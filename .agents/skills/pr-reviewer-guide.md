---
name: pr-reviewer-guide
description: AnkiDroid Code Review Guide — the rules, standards, and checklists every reviewer must apply. Reference this document when reviewing any PR in this repo.
---

# AnkiDroid Code Review Guide

> Code reviewing is subjective. These guidelines are guidelines — use your best judgment.
> This guide is for members of the AnkiDroid Reviewer team.

## Attitude

- **BE KIND.** Critical feedback reads harsher than intended. Acknowledge what is good before listing issues. Express gratitude for contributions.
- Use `nit:` prefix for non-blocking suggestions. If you're unsure whether to block, default to the implementer's choice.
- When a submitter may not fully understand their own PR code, request additional documentation — future maintainers will need it.

## New Contributors

- Relax standards for PRs from contributors labelled "New Contributor". The goal is a positive first impression that encourages continued participation.
- If reasonable effort exists, a reviewer may rebase and take over finishing a PR — but must inform the submitter and accept responsibility for any further changes needed to merge.

## Finding PRs to Review

Useful GitHub label queries (combine as needed):

| Purpose | Query |
|---|---|
| High priority | `label:"Review High Priority"` |
| Needs first review | `label:"Needs Review"` |
| Needs second approval | `label:"Needs Second Approval"` |
| Needs reviewer reply | `label:"Needs reviewer reply"` |
| Ready to merge | `label:"Pending Merge"` |
| Exclude my own PRs | `-author:@me` |
| Exclude conflicts | `-label:"Has Conflicts"` |
| Exclude awaiting author | `-label:"Needs Author Reply"` |
| Exclude drafts | `draft:false` |

Recommended: check out the PR locally with `gh pr checkout <num> --force`.

## Before Reviewing (Gate Checks)

Request changes immediately — do not proceed — if either of these is true:

1. **PR template incomplete** — the submitter has not filled in the required sections.
2. **CI failing** — rerun once in case of a flake; if it still fails, block and request a fix.

## Things to Look For

Work through each of these:

- [ ] Does the codebase **improve** after this change? (Not just "not worse".)
- [ ] Do you **understand** the entire change? If not, ask.
- [ ] Is the PR **linked to an issue**?
- [ ] Are **names** (variables, methods, classes) comprehensible to a future reader?
- [ ] Is **documentation** added or updated where the behaviour changes?
- [ ] Does **each commit**:
  - compile on its own?
  - address exactly one thing?
  - have a clear, descriptive commit message?
- [ ] Are **edge cases and exception paths** handled correctly?
- [ ] For **regression fixes**: is there a test, or a `@NeedsTest` annotation with a comment explaining why a test wasn't added?
- [ ] For **significant new features**: is there a test, or a `@NeedsTest` annotation?

## Requesting Changes

- Non-blocking suggestions: prefix with `nit:` — do not block the PR for these.
- Missing test evidence (screenshots, logs): **block** the PR.
- Uncertain about an approach: default to the implementer's choice; leave it as a `nit`.
- Large changes: require justification that they are a meaningful, lasting improvement.
- Oversized PRs: request splitting where feasible — especially for refactoring commits that could be cherry-picked independently.
- For complex or multi-line changes: prefer inline GitHub suggestions or a patch with documentation links over vague prose comments.

## Before Merging Checklist

- [ ] **Strings sync**: if the PR has the `Strings` label, perform string synchronisation before merging.
- [ ] **Licenses wiki**: if new dependencies are added, ensure the [Licenses wiki](https://github.com/ankidroid/Anki-Android/wiki/Licensing) is updated (submitter's responsibility; verify it was done).
- [ ] **Squash-merge**: if the `squash-merge` label is set, squash and force-push before merging.
- [ ] **No merge commits**: verify the commit history contains no merge commits.
- [ ] **Stable branch**: for bugfixes, add the label `Queued for Cherry Pick to Stable Branch`.

## Skipping the Second Approval Requirement

Two approvals are the default. The second approval may be skipped for:

- Emergency / hotfix changes
- Automated refactorings (e.g. lint auto-fix, tool-generated rename)
- Test-only changes
- Changes that are obviously correct (trivial one-liners, typo fixes)

## See Also

- [Google Code Review Standards](https://google.github.io/eng-practices/review/) — broader best-practice reference
- `CONTRIBUTING.md` in this repo — contributor-facing companion guide
