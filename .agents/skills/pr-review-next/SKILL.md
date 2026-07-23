---
name: pr-review-next
description: Produces a shortlist of AnkiDroid PRs to review.
---

Usage: `/pr-review-next`

# AnkiDroid: pick the next PR to review

Produce a ranked shortlist of open PRs which need review, recommend the best next one, then stop.
The reviewer is expected to select one and run `/pr-full-review` on it. This skill does not run
the review itself.

This skill distills the queue-selection half of the
[Code-review guide (wiki)](https://github.com/ankidroid/Anki-Android/wiki/Code-review-guide): the
search for reviewable PRs and the labels that prioritize them.

**Never act on GitHub.** Only use read-only `gh` queries. Do not comment, review, approve, request
changes, label, assign, or merge. Output the shortlist in this conversation.

Re-fetch the queue fresh on every run; labels, CI, and timestamps change frequently.

## 1. Fetch the queue

Resolve the current reviewer login first, to exclude self-authored PRs:

```bash
gh api user --jq .login
```

Then list the open PRs, applying the wiki's filter via `--search`:

```bash
gh pr list --repo ankidroid/Anki-Android --state open --limit 100 \
  --search 'draft:false -author:@me -label:"Has Conflicts" -label:"Needs Author Reply" -label:"Blocked by dependency"' \
  --json number,title,url,labels,reviewDecision,isDraft,updatedAt,author,additions,deletions
```

### Filters

- `draft:false`: drafts are work in progress; skip unless explicitly asked.
- `-author:@me`: don't review your own PRs.
- `-label:"Has Conflicts"`: needs a rebase before it's worth reviewing.
- `-label:"Needs Author Reply"`: the ball is in the author's court.
- `-label:"Blocked by dependency"`: can't land until its dependency does.

This result differs from the wiki: Dependabot PRs are not excluded, just ranked last (see below).

### Post-filter: drop PRs you've approved

GitHub search has no "approved-by" qualifier, so do this after fetching. For each candidate,
determine your **effective** review decision.

`COMMENTED` reviews do **not** change approval state: once you `APPROVED`, a later follow-up comment
leaves the approval standing (only a subsequent `CHANGES_REQUESTED`, or a dismissal, revokes it). So
do not just take your *last* review. A trailing `COMMENTED` masks an earlier `APPROVED` and
let an already-approved PR slip back into the queue. Compute the decision from the last review that
is `APPROVED` or `CHANGES_REQUESTED`, ignoring `COMMENTED`:

```bash
gh api repos/ankidroid/Anki-Android/pulls/<number>/reviews \
  --jq 'map(select(.user.login == "<your-login>" and (.state == "APPROVED" or .state == "CHANGES_REQUESTED"))) | last | "\(.state) \(.submitted_at)"'
```

Run it only over the candidates that survived the search, not the whole repo. Exclude the PR if the
effective decision is `APPROVED`.

For the ranking step you also need your *latest* review of any kind (a follow-up `COMMENTED` is what
triggers the "author responded since my comment" adjustment), so capture that separately and retain
its `submitted_at`:

```bash
gh api repos/ankidroid/Anki-Android/pulls/<number>/reviews \
  --jq 'map(select(.user.login == "<your-login>")) | last | "\(.state) \(.submitted_at)"'
```

## 2. Recover stale `Needs Author Reply` PRs

`Needs Author Reply` is excluded from the main queue. 
But **PR authors cannot remove this label, only reviewers can.** So it goes stale: the
author replies or pushes a fix, and the PR silently stays out of the queue until a reviewer
notices and flips the label back to `Needs Review`.

Catching these is the **highest-priority** output of this skill: a stale label means a contributor
is waiting on a response the queue is actively hiding. Surface them first, in their own block,
flagged as needing the label corrected to `Needs Review` (see the 'Output' section).

Fetch the bucket, using the same blockers as the original query.

```bash
gh pr list --repo ankidroid/Anki-Android --state open --limit 100 \
  --search 'draft:false -author:@me label:"Needs Author Reply" -label:"Has Conflicts" -label:"Blocked by dependency"' \
  --json number,title,url,author,updatedAt,labels
```

A review is **stale** when the author's most recent activity is *newer* than the last time the label
was added. Compute both timestamps per PR and compare.

Merge paginated pages locally: `--jq` runs once per page (so `max` is per-page, not global) and
`--slurp` is rejected alongside `--jq`. Pipe raw pages into `jq -s 'add'` instead:

```bash
n=<number>; author=<pr-author-login>   # .author.login from the bucket fetch

# When the label was LAST added (a reviewer may have added - removed - re-added it; take the max):
gh api repos/ankidroid/Anki-Android/issues/$n/timeline --paginate \
  | jq -s 'add | [.[] | select(.event=="labeled" and .label.name=="Needs Author Reply") | .created_at] | max // "none"' -r

# Author's most recent activity: take the max across all three sources:
gh pr view $n --repo ankidroid/Anki-Android --json commits --jq '.commits[-1].committedDate'  # latest commit
gh api repos/ankidroid/Anki-Android/issues/$n/comments --paginate \
  | jq -s "add | [.[] | select(.user.login==\"$author\") | .created_at] | max // \"none\"" -r  # conversation comments
gh api repos/ankidroid/Anki-Android/pulls/$n/comments  --paginate \
  | jq -s "add | [.[] | select(.user.login==\"$author\") | .created_at] | max // \"none\"" -r  # inline comments / replies
```

- A review is stale if author activity is **after** the last label-add, so list it in the stale block in the output.
- If nothing post-dates the label then the label is accurate and no further action is needed.

Pitfalls:
- Always compare against the **most recent** `labeled` event (`max`), not the first.
- A reply that predates the label but a *commit* that post-dates it (or vice-versa) still counts:
  the test is `max(commit, conversation, inline) > lastLabeled`.

**Never act on GitHub**: this skill only reports the stale PRs; a reviewer flips the label by hand.

## 3. Rank the survivors

Sort into tiers. Within a tier, oldest `updatedAt` first, so nothing starves.

0. **Stale `Needs Author Reply`**: always first, above everything. These aren't a normal
   review. The action is to correct the label to `Needs Review` so the PR re-enters the queue.
   Report them in their own block above the ranked shortlist, so they enter the queue on the next 
   cycle.
1. `Review High Priority` and `Queued for Cherry Pick to Stable Branch`: always first, on par with
   each other.
2. `Needs Second Approval` (one approval, likely easy to merge) and `Needs Review`
   (awaiting first review). Nudge `New Contributor` PRs up within this tier.
3. Everything else still passing the filter.
4. Dependabot PRs last, unless you are `david-allison` or `mikehardy`, where they should be ranked  
   as `Needs Second Approval`.

Adjustments:

- **Prioritize PRs you've reviewed once the author responds.** Applies when your latest review is
  `COMMENTED` or `CHANGES_REQUESTED`. Find the author's most recent activity on the PR and compare 
  it to your review's `submitted_at`:

  ```bash
  author=<pr-author-login>   # .author.login from the queue fetch
  gh pr view <number> --repo ankidroid/Anki-Android --json commits --jq '.commits[-1].committedDate' # latest commit
  gh api repos/ankidroid/Anki-Android/issues/<number>/comments --jq "[.[] | select(.user.login==\"$author\") | .created_at] | max // empty"  # comments on the PR (conversation tab)
  gh api repos/ankidroid/Anki-Android/pulls/<number>/comments  --jq "[.[] | select(.user.login==\"$author\") | .created_at] | max // empty"  # inline review comments and replies
  ```

    - If the author has acted and a re-review is expected: prioritize the PR within its tier.
    - If nothing post-dates it, leave it ranked low and note "awaiting author".
- Smaller diffs break a tie when two PRs are otherwise equal, but never override the tier order.

## 4. Gather per-candidate context

For the top 7 after ranking, collect the context for one table row each:

- `#<number>`, title, and `url`.
- Author login, tagged `(New Contributor)` if the label is present.
- Routing labels (`Needs Review`, `Needs Second Approval`, `Review High Priority`, etc...).
- Age waiting: how long since `updatedAt`.
- Size: `+<additions>/-<deletions>`, excluding generated and lock files (`package-lock.json`,
  `yarn.lock`, `pnpm-lock.yaml`, `Cargo.lock`, `Gemfile.lock`, `gradle.lockfile`, `*.lock`). Sum
  the per-file diff rather than the top-level `additions`/`deletions`:

  ```bash
  gh pr view <number> --repo ankidroid/Anki-Android --json files --jq '
    [.files[] | select(.path | test("(^|/)(package-lock\\.json|yarn\\.lock|pnpm-lock\\.yaml|Cargo\\.lock|Gemfile\\.lock|gradle\\.lockfile)$|\\.lock$") | not)]
    | "+\([.[].additions] | add // 0)/-\([.[].deletions] | add // 0)"'
  ```

  Flag `(large)` on the filtered count, and note when lock-file churn was excluded (e.g.
  `+40/-3 (excl. lockfiles)`).

For the 3 recommended picks (see step 5), check CI. If red, don't review deeply, either re-run
flaky checks or tell the author to fix the checks.

```bash
gh pr checks <number> --repo ankidroid/Anki-Android
```

Report CI status, but don't filter the queue on it.

## 5. Output, then stop

If any stale `Needs Author Reply` PRs were found, print them **first**, in their own block, before
the ranked shortlist. This is the highest-priority correction:

```
## Stale `Needs Author Reply` - relabel to `Needs Review`
- #<number> <title> - <author> - author last acted <when>, label added <when> (<delta> stale)
  https://github.com/ankidroid/Anki-Android/pull/<number>
```

For each, state the action plainly ("author replied; a reviewer should remove `Needs Author Reply`
so it re-enters the queue"). **Do not** flip the label yourself.

List **only** the stale PRs which require action.

**If no stale PRs were found, emit nothing for this concern**. Go straight to the shortlist,
this block appears only when there is an actual relabel to perform.

Then print the ranked top 7 as a table, one row per PR, with the context from step 4 and a short
reason for its rank. Keep all reasoning and caveats in the `Reason` column. Link the PR number to
its URL.

| # | PR                                                             | Author     | Labels                  | Waiting | Size     | Reason                                                                      |
|---|----------------------------------------------------------------|------------|-------------------------|---------|----------|-----------------------------------------------------------------------------|
| 1 | [#21147](https://github.com/ankidroid/Anki-Android/pull/21147) | criticalAY | `Needs Second Approval` | ~6d     | +337/-22 | One approval already; author responded to my comments → re-review expected. |
| … |                                                                |            |                         |         |          |                                                                             |

Tag the author `(New Contributor)` where the label is present, and flag size with `(large)` /
`(excl. lockfiles)` per step 4.

Print **only** the table, do not make footnotes regarding PRs which require no action. 
Any caveat that matters belongs in that PR's `Reason` column, not in prose after the table.

Then end with this closing block: the 3 highest-ranked picks and nothing after it, so the last
thing on screen is usable. For each pick, on consecutive lines:

1. The PR URL as a plain link, then on the same line a one-line summary including the author (tag
   `(New Contributor)` if the label is present).
2. The review status and the action it implies, derived from the labels and your prior review
   state (post-filter):
   - `Needs Review`, no prior review by you: First review.
   - `Needs Review`, you previously `COMMENTED` or `CHANGES_REQUESTED`: Follow-up review; check
     your earlier feedback was addressed.
   - `Needs Second Approval`, you haven't approved: Second review.
   - `New Contributor` present: prefix with "New contributor".
   - Add one merge-time flag if relevant, e.g. `Strings` means a strings-sync at merge.
3. The text to type into Claude to start the review: `/pr-full-review <number>`

Separate the 3 picks with a blank line. Order them best-first; the reviewer takes the top one and
keeps the other two as fallbacks if it's blocked.

Example ending:

```
https://github.com/ankidroid/Anki-Android/pull/21212 - david-allison: pins `updateDaemonJvm` defaults to JetBrains 21.
First review - build-config follow-on to #20382.
/pr-full-review 21212

https://github.com/ankidroid/Anki-Android/pull/20934 - Galal-20: prevents IllegalArgumentException in Card Browser.
First review - oldest `Needs Review`, small bug-fix.
/pr-full-review 20934

https://github.com/ankidroid/Anki-Android/pull/21186 - ShaanNarendran (New Contributor): adds bottom sheet for "more".
New contributor, first review.
/pr-full-review 21186
```

**Empty queue:** if nothing survives the filter, say the review queue is clear.

```bash
gh pr list --repo ankidroid/Anki-Android --state open \
  --search 'draft:false -author:@me label:"Has Conflicts"'        # may have been rebased
```
