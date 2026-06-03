---
name: pr-full-review
description: Full AnkiDroid PR review workflow. Reviews a PR across three axes in parallel — Standards (does the code follow AnkiDroid's guidelines?), Spec (does it implement what the issue asked?), and Tests (is coverage adequate?). Use when asked to review a PR, a branch, or work-in-progress changes.
---

# AnkiDroid Full PR Review

Three-axis review of a pull request:

- **Standards** — does the code conform to AnkiDroid's review guide and project conventions?
- **Spec** — does the code faithfully implement what the linked issue requested?
- **Tests** — is test coverage sufficient? Are regressions guarded?

All three axes run as **parallel sub-agents** so they don't pollute each other's context. This skill aggregates their findings.

## Process

### 1. Identify the PR

Accept a PR number, branch name, or commit range from the user. If none is given, ask: "Which PR or branch should I review?" Do not proceed without it.

If given a PR number, fetch its metadata:

```
gh pr view <num> --json title,body,labels,author,baseRefName,headRefName,commits
gh pr diff <num>
```

Note: the author's GitHub username; whether they carry a "New Contributor" label or have few prior merged PRs; the base branch (usually `main`).

If given a branch or commit range, compute the diff:

```
git diff <base>...HEAD
git log <base>..HEAD --oneline
```

### 2. Gate checks — run before sub-agents

These are hard blockers. If either fails, stop and request changes; do not continue to the full review.

1. **PR template**: scan the PR body for unfilled template sections (lines containing `<!-- … -->` placeholders or empty required fields). If the template is not complete → request changes.
2. **CI status**: `gh pr checks <num>`. If any required check is failing (excluding known flakes), stop → request a CI fix.

### 3. Identify the spec

Look for the issue the PR closes, in this order:

1. `Closes #N` / `Fixes #N` / `Resolves #N` in the PR body or commit messages — fetch with `gh issue view N`.
2. A `#N` reference anywhere in the PR body.
3. If none found, note "no linked issue" in the Spec section and skip the Spec sub-agent.

### 4. Identify standards sources

Read `.agents/skills/pr-reviewer-guide.md` in this repo — that is the primary standards document.

Also note (but do not re-check what tooling already enforces):
- `CONTRIBUTING.md`
- `CLAUDE.md`
- Any `lint`, `ktlint`, or `detekt` config files

### 5. Spawn three sub-agents in parallel

Send a single message with three `Agent` tool calls. Use `general-purpose` for all three.

---

**Standards sub-agent prompt:**

```
You are reviewing a PR against AnkiDroid's coding standards.

PR diff:
<paste full diff>

Commits:
<paste git log --oneline>

PR author: <username>. New contributor: <yes/no>.

Standards document: read `.agents/skills/pr-reviewer-guide.md` in this repo.

Tasks:
1. Check every item in the "Things to Look For" checklist.
2. Check every item in the "Before Reviewing" gate checks (assume CI already passed).
3. Note any violations of commit quality (atomic, compiles, clear message).
4. Note any naming or documentation issues.
5. For new contributors: flag which concerns you would relax vs. still enforce.

For each finding: state the rule (cite the guide section), the file/line, and whether it is a hard block or a nit.
Under 400 words. Lead with blockers.
```

---

**Spec sub-agent prompt:**

```
You are reviewing whether a PR implements what was asked for.

PR diff:
<paste full diff>

Commits:
<paste git log --oneline>

Issue / spec:
<paste gh issue view output, or "no linked issue">

Tasks:
1. List requirements from the issue that are missing or only partially implemented.
2. List behaviour in the diff that was NOT asked for (scope creep).
3. Flag any requirements that appear implemented but look incorrect.

Quote the issue text for each finding. If there is no linked issue, say so and skip.
Under 300 words.
```

---

**Tests sub-agent prompt:**

```
You are reviewing test coverage for a PR.

PR diff:
<paste full diff>

Tasks:
1. For every bug fix: is there a regression test? If not, is there a @NeedsTest annotation with a justification comment?
2. For every significant new feature: is there a test or @NeedsTest?
3. Are existing tests broken or removed without replacement?
4. Do tests test behaviour (not just call coverage)?

For each finding: state the file/line, and whether it is a hard block (missing test on a regression fix with no @NeedsTest) or a nit.
Under 300 words. Lead with blockers.
```

---

### 6. Aggregate and report

Present findings under three headings:

```
## Standards
<sub-agent output>

## Spec
<sub-agent output>

## Tests
<sub-agent output>
```

Then add:

```
## Summary

- Standards: N finding(s) — X blocker(s), Y nit(s)
- Spec: N finding(s) [or: no linked issue]
- Tests: N finding(s) — X blocker(s), Y nit(s)

Worst issue: <single most important thing to fix, if any>

Merge readiness: READY / NEEDS CHANGES / BLOCKED
```

If the PR is from a new contributor and all blockers are minor, note that standards enforcement should be relaxed and suggest the reviewer consider rebasing to help complete the PR (per the guide).

## Why three axes

A PR can pass two axes and fail the third:

- Correct implementation, adequate tests, but messy commit history → Standards fail only.
- Clean code, clean commits, but implements the wrong thing → Spec fail only.
- Well-written feature with no tests on a regression → Tests fail only.

Reporting axes separately prevents one axis from masking another.
