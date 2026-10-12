# Contributing

Thanks for helping. This is a security-sensitive app (it sees everything you type), so the bar is "correct and
honest" over "fast".

## Ground rules

1. Read `ARCHITECTURE.md` and `CLAUDE.md` (security invariants apply to humans too).
2. One logical change per PR. Keep the build green: `./gradlew assembleDebug testDebugUnitTest`.
3. Put logic in pure Kotlin and unit-test it, including failure paths. Android glue should stay thin.
4. Never log or transmit typed text, clipboard contents, snippets, prompts or credentials.
5. Don't add dependencies lightly: permissive licence (Apache/MIT/BSD), maintained, listed in `LICENSES.md`, and
   regenerate `gradle/verification-metadata.xml` (see `BUILD.md`).
6. State in the PR what you tested and what you could **not** test (e.g. no device). Don't claim more.
7. Update docs in the same PR; `ROADMAP.md` status must stay truthful.

## Upstream (FlorisBoard)

Code here derives from FlorisBoard (Apache-2.0). Fixes may be ported from upstream with attribution. Upstream's AI
policy (`docs/upstream/AI_POLICY.md`) applies to contributions *to upstream*; do not send patches from this fork
upstream unless you wrote them yourself and comply with that policy.

## Licence

By contributing you agree your contribution is licensed under Apache-2.0 (see `LICENSE`).
