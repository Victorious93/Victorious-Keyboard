# CLAUDE.md

## Purpose
Victorious Keyboard: Android IME (Kotlin, Compose) = modified FlorisBoard `v0.6.0-alpha02` + clipboard manager,
developer/terminal modes, snippets, offline suggestions, opt-in AI tools. Read `AUDIT.md` and `ARCHITECTURE.md` first.

## Current phase
**Phases 0–1 done; Phase 3 implemented but not device-validated; Phase 4 core JVM-tested, wiring + developer-row UI compiled but never rendered/run; Phase 5 core (snippets) JVM-tested, Android shell (store/expansion/settings screen) compiled but never run; operating modes (Standard/Developer/Termux) core tested, wiring compiled but never run.**
Status table: `ROADMAP.md`. Update it with every phase. Phase 4 details/limits: `TERMINAL.md`.

## Verified environment
JDK 21, Gradle wrapper 9.2.0, Android SDK platform 36 / build-tools 35.0.0 (NDK/CMake/Rust are no longer needed:
`:lib:native` was removed). Details and the Maven-Central 429 workaround: `BUILD.md`.
**No Android device or emulator is available** (no KVM): only compile + JVM tests can be run. Never claim install or
runtime verification.

## Build / test
```sh
./gradlew assembleDebug            # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest        # JVM tests (kotest + JUnit platform)
```
Long builds: run in the background with `--no-parallel --max-workers=2`, log to a file, wait for it.

## Architecture boundaries
* Single `:app` module + `:lib:*`. New features = packages under `dev.patrickgold.florisboard.ime.*` with a
  **pure-Kotlin core** (no `android.*` imports) and a thin Android shell. See `ARCHITECTURE.md` §1, §3.
* Kotlin namespace stays `dev.patrickgold.florisboard`; product identity is `applicationId` + resources.
* Optional features must never crash the IME and never block the main thread.

## Security invariants (do not weaken)
1. No logging of clipboard, typed, or snippet/prompt content — not even at debug level.
2. Nothing is captured from password / sensitive fields. Honour `EXTRA_IS_SENSITIVE`.
3. No network access unless the user explicitly enabled a provider; no `INTERNET` permission before P7.
4. No `fallbackToDestructiveMigration()` on user-data databases. Pinned/saved data is never deleted to satisfy a cap
   without explicit user confirmation.
5. Backups: only JCE primitives (AES-256-GCM, PBKDF2-HMAC-SHA256), random salt+nonce per file, versioned header, no
   keys or passwords in metadata. Imports are untrusted input: bounded sizes, no path traversal, atomic apply.
6. No secrets, keystores or signing material in Git.
7. Any new persistent store (clipboard vault, snippets, prompts, provider credentials, key files) lives in
   `noBackupFilesDir` or is explicitly excluded in `res/xml/backup_rules.xml`; Android auto-backup must not carry it.
8. Version codes stay ≥ 120 (in-app restore rejects < 64) and only increase.

## Conventions
* Match surrounding code; Apache-2.0 header on new files (`Copyright (C) 2026 Victorious Keyboard Contributors`).
* Don't remove upstream copyright lines. Prefer additive changes to upstream files.
* Tests: kotest/JUnit5 under `app/src/test`. Put logic in pure Kotlin so it is testable.
* Docs are updated in the same commit as the behaviour they describe. Don't document unimplemented features as done.

## Definition of done (per feature)
Implemented (not a stub) · unit-tested incl. failure paths · builds · docs updated · privacy invariants hold ·
honest status in `ROADMAP.md` (device validation "not run" unless actually run).

## Known blockers
* No device/emulator → IME runtime behaviour (Termux modifiers, password fields, rotation, latency) unvalidated.
* `central.sonatype.com` snapshots are denied by egress policy; do not depend on SNAPSHOT artifacts anyway.
* Upstream licence provenance of emoji/Han data unconfirmed (`LICENSES.md`).

## Next milestone
Needs a device first: validate Phase 3 and the Phase 4 wiring on hardware (checklist in `TESTING.md`). Without a
device the next work is the in-keyboard snippet picker and then Termux integration (verify the current `RUN_COMMAND` contract from Termux's documentation first); see `SNIPPETS.md`, `docs/ARCHITECTURE_ASSESSMENT.md`.

Gotcha: run the whole unit-test suite; kotest breaks under Gradle's `--tests` filter (see `TESTING.md`).

## Upstream
`upstream` remote = `https://github.com/florisboard/florisboard.git`; baseline `0ea0481c40616b0fd6eb4cd09c57521181b9a4b6`
(tag `v0.6.0-alpha02`). Upstream's AI policy forbids AI-written contributions *to upstream*; do not prepare patches for it.
