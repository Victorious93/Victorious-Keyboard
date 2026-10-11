# Roadmap

Status legend: ✅ done and verified as stated · 🟡 partially done · ⬜ not started · ⛔ blocked.
"Verified" means compiled and JVM-tested in the reference environment. **Nothing has been run on an Android device
yet** (no device/emulator available; see `AUDIT.md` §5), so no phase is marked "device-validated".

Upstream's own roadmap is preserved at `docs/upstream/ROADMAP.md`.

| Phase | Name | Status |
|---|---|---|
| 0 | Repository audit and baseline | ✅ (docs below; baseline built + 176 tests pass) |
| 1 | Project foundation | ⬜ |
| 2 | Stable keyboard engine | ⬜ (needs device; engine is upstream's) |
| 3 | Advanced clipboard | ⬜ |
| 4 | Developer and terminal modes | ⬜ |
| 5 | Snippets and productivity | ⬜ |
| 6 | Predictive and gesture typing | ⬜ |
| 7 | AI workspace | ⬜ |
| 8 | Themes and responsive UI | ⬜ |
| 9 | Security and performance | ⬜ |
| 10 | Release engineering | ⬜ |

## Phase 0 — Audit and baseline ✅

* ✅ Upstream inspected; three candidate bases built/evaluated; `v0.6.0-alpha02` chosen (`AUDIT.md`).
* ✅ Licence review (`LICENSES.md`); AI-policy implication recorded.
* ✅ Baseline: `assembleDebug` succeeds, 176 unit tests pass.
* ✅ Docs: `AUDIT.md`, `ARCHITECTURE.md`, `LICENSES.md`, `BUILD.md`, `ROADMAP.md`, `CLAUDE.md`, `README.md`.
* Open: emoji/Han data licence provenance (`LICENSES.md` §6).

## Phase 1 — Foundation

* application id `com.victorious.keyboard`; user-visible name/branding/icon; remove `:lib:native`
* build variants (`debug`, `release`; drop `beta`/`benchmark` unless needed); version scheme
* Gradle dependency verification
* CI: PR validation (compile, unit tests, lint), debug APK artifact
* issue/PR templates adapted from upstream; `CONTRIBUTING.md`, `CHANGELOG.md`
* exit: compiles, debug APK produced, CI config in place

## Phase 2 — Stable engine

Engine is upstream's. Work here is verification: registration/selection, typing, password fields, rotation, app
switching. Requires a device; until then it is a documented manual checklist (`TESTING.md`).

## Phase 3 — Advanced clipboard (highest priority)

Pure-Kotlin core first (policy, search, backup codecs, crypto, import planner), then Room schema v5 with explicit
migration, then UI. Requirements and acceptance criteria are those in the project brief; design in `CLIPBOARD.md` and
`BACKUP_FORMAT.md` (written with the code).

## Phases 4–10

As in the project brief. Notable decisions already taken:

* P4: modifier→`KeyEvent` mapping is a pure function with a test table; Termux/SSH behaviour is device-test-only.
* P6: suggestions/autocorrect do not exist upstream; they are built here or not shipped. No placeholder toggles.
* P7: no `INTERNET` permission until a provider adapter is enabled by the user.
* P10: no production signing material in the repo; debug builds are never published as releases.
