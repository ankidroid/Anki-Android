## Project
SmartCards is a fork of AnkiDroid (`upstream` remote), repurposed as a language-learning
flashcard app: multi-modal cards (audio-only prompts, images, example sentences, a collapsible
explainer with meaning/context/synonyms), share-sheet capture of foreign words, and an eventual
AI backend that auto-generates translations and card content. This is a solo, deliberately
AI-driven project — it does not use AnkiDroid's upstream `AI_POLICY.md` contributor restrictions.

## Refactoring Scope
- Constrain scope tightly: do not modify unrelated files, themes, or settings 'while you're in there'.

## Verification
- For bug fixes, write the failing regression test FIRST and confirm it fails before applying the fix.

## Git workflow
- Never commit or push directly to `main`. Always work on a branch and open a PR.
- Never merge a PR yourself (`gh pr merge`) — only the repo owner merges, after review.
- Before opening a PR, run the checks in `.github/workflows/README.md#quality-checks`
  (`./gradlew lintAll ktLintCheck lint-rules:test`, `./gradlew jacocoUnitTestReport`).
- Before considering a PR ready for human review, run it through the `pr-full-review` skill
  (`.agents/skills/pr-full-review`) against the current branch.
