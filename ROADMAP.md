# Roadmap

Status legend: ✅ done and verified as stated · 🟡 partially done · ⬜ not started · ⛔ blocked.
"Verified" means compiled and JVM-tested in the reference environment. **Nothing has been run on an Android device
yet** (no device/emulator available; see `AUDIT.md` §5), so no phase is marked "device-validated".

Upstream's own roadmap is preserved at `docs/upstream/ROADMAP.md`.

## Phase queue

Started by the owner's words "continue with phase implementation" (procedure in `CLAUDE.md`, "Working method"). One
pull request per item. Items are listed in the order they should be built; the owner may reorder them.

| # | Item | Status | Notes |
|---|---|---|---|
| 1 | Snippets: expansion on Enter and Tab (the core already supports more boundaries, `ExpansionSettings.boundaries`) | done | JVM-tested (`ExpansionAction`). Each key is its own opt-in (both off by default). Never in sensitive fields. A non-match does not swallow Enter/Tab. Keyboard wiring compiled, **not run on a device**. |
| 2 | Snippets: per-mode defaults (e.g. expansion on/off per operating mode) | `next` | Builds on `OperatingMode` and the Advanced developer settings. |
| 3 | Snippets: encrypted export, reusing the clipboard backup container | queued | Must follow `BACKUP_FORMAT.md` (JCE primitives only, versioned header, no keys in metadata). |
| 4 | Dictionary packs: device validation and a decision on the word lists (verify against official sources) | blocked on owner | The lists were written from memory; verification needs network access the sandbox does not have. |
| 5 | Termux `RUN_COMMAND` integration | blocked on owner | Not started. Do not begin unless the owner explicitly asks (see `CLAUDE.md`). |

Completed phases of this queue are recorded in `CHANGELOG.md` and in the status tables above.

| Phase | Name | Status |
|---|---|---|
| 0 | Repository audit and baseline | ✅ (docs below; baseline built + 176 tests pass) |
| 1 | Project foundation | ✅ (builds, JVM tests, dependency verification proven from a cold Gradle home through a mirror; CI green through build + unit tests on GitHub, including a run that stored the APK; artifact upload stays non-fatal if the storage quota is full; `lintDebug` is a gate after the inherited lint errors were fixed — see `AUDIT.md` §8) |
| 2 | Stable keyboard engine | ⬜ (needs device; engine is upstream's) |
| 3 | Advanced clipboard | 🟡 core ✅ JVM-tested; storage/UI 🟦 compiled, **not run on a device**; see open items |
| 4 | Developer and terminal modes | 🟡 pure core ✅ JVM-tested; wiring and developer-row UI 🟦 compiled, **never run or rendered** (see `TERMINAL.md`) |
| 5 | Snippets and productivity | 🟡 pure core ✅ JVM-tested; Android shell (store, expansion on space and opt-in Enter/Tab, settings screen, import/export, in-keyboard picker panel) 🟦 compiled, **never run or rendered** (see `SNIPPETS.md`) |
| 6 | Predictive and gesture typing | 🟡 word prediction (current word, next word, one-edit autocorrect) is JVM-tested and wired into the suggestion bar; learning is off by default and the file is not backed up. Gesture typing is still not done. Nothing has been run on a device (`DICTIONARY_PACKS.md`) |
| 7 | AI workspace | ⬜ |
| 8 | Themes and responsive UI | 🟡 default keyboard theme is Victorious Neon (light variant too); settings use a navy/amber/pink Material scheme, dark by default. Keyboard colors overlay Neon (presets, picker, persistence) and are JVM-tested; the screen is compiled and not run on a device. Older themes stay selectable. Responsive/tablet layout not started |
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
* P6: suggestions are a pure predictor over the bundled packs, with learning off unless the user opts in. Gesture typing is still not done. `spell()` is still a stub.
* P7: no `INTERNET` permission until a provider adapter is enabled by the user.
* P10: no production signing material in the repo; debug builds are never published as releases.

## Phase 3 — status detail

Done and JVM-tested: retention policy, search, categoriser, credential detector, backup formats (JSON/ZIP/encrypted),
hostile-file-safe reader, import planner (replace/merge/skip, exact cap preview), export/restore service with
consent and rollback semantics, backup library model, AES-GCM value cipher, password-field detection.

Compiled but **never run**: Android Keystore key provider, Room v5 + `ClipboardStore`, `ClipboardManager` rewrite,
Clipboard manager screen, Backup/restore screen with SAF pickers, keyboard panel favourite action.

Open before Phase 3 can be called complete:
* Run it on a device: persistence across restarts, Keystore behaviour, password-field copy guard, SAF export/import,
  responsiveness with 500 long entries (measure; set thresholds).
* Instrumented Room migration test (4→5).
* Typed search inside the keyboard clipboard panel (needs an IME-internal text input; deliberately not attempted
  blind).
* Image/URI clips in backups (v1 is text-only by design).

## Phase 4 — status detail

Done and JVM-tested: key+modifier → `KeyEvent` mapper (US-layout table, constants checked against
`android.view.KeyEvent`), Ctrl/Alt sticky/lock/hold state machine, developer row and language-profile models.

Compiled but **never run**: `KeyboardManager` handling of Ctrl/Alt/Esc/Tab key codes and the key-event path for typed
characters.

Also compiled, never run: Advanced developer settings screen, developer row composable, settings group, observable Ctrl/Alt state, Ctrl/Alt on the
keyboard's arrow keys.

Open: seeing the row on a real screen (sizing, window height, theming, scroll-vs-tap), all Termux/SSH behaviour on a
device, per-app automatic profile, translations.

## Phase 5 — status detail

Done and JVM-tested: template language and renderer, expansion matcher (opt-in, never in sensitive fields, no
recursion), snippet library with validation and unique abbreviations, versioned JSON import/export with a
non-destructive importer, atomic file store with `.bak`/`.corrupt` safety copies. Details and limits: `SNIPPETS.md`.

Android shell compiled, never run: `SnippetManager` (store under `noBackupFilesDir`), expansion on the space key and, each
opt-in, on Enter and Tab, Snippets settings screen with import/export, in-keyboard picker (quick action, panel,
`{{selection}}`). Open: per-mode defaults, encrypted export, typed search in the picker, device validation (including
Enter/Tab expansion, which is compiled and JVM-tested but not run on a device).

## Mapping to the "VictorKeys master brief"

A longer brief (written for "VictorKeys"; the owner confirmed Victorious Keyboard is the correct project) uses different
phase numbers. Where its topics land in this repository's plan:

| Master brief phase | Here |
|---|---|
| 1 Architecture / mode manager | not started; `ime.terminal`, `ime.developer` and the developer row are the first pieces |
| 2 Termux keyboard (special keys, symbol row, shell snippets, `RUN_COMMAND`) | special keys/symbol row = our Phase 4 (done, unrun); snippets/templates = Phase 5 (core done); `RUN_COMMAND` integration not started |
| 3 Claude Code keyboard, 4 Kali catalog, 5 NetHunter, 6 AI assistant, 7 terminal companion | not started (our Phase 7+); the template engine here is a prerequisite for prompt/command templates |
| 8 Developer functions (pairing, indentation, snippets) | pair *keys* exist; auto-pairing/indentation not started; snippets = Phase 5 |
| 9 Clipboard and workflows | clipboard = Phase 3 (done, unrun); workflows not started |
| 10 Knowledge / token efficiency, 11 UI, 12–14 tests, docs, CI | partly covered by existing docs/CI; the rest not started |

Nothing from the brief beyond what is listed in `FEATURES.md` exists in this repository. The owner confirmed that this
repository (Victorious Keyboard) is the target; the "VictorKeys" name in the brief is not a rename.
