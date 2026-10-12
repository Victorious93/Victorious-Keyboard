# Testing

## CI (GitHub Actions)

| Run | Result |
|---|---|
| 1-4 (PR and push, 2026-10-12) | failed at dependency verification (plugin-marker checksums, see `BUILD.md`), nothing compiled |
| 5 (`workflow_dispatch`, branch `ccr-08db99f8-cdmjq6`, commit `c92c878`) | **build + unit tests passed**; job red only at "Upload unit test reports": artifact storage quota hit; APK artifact skipped |

Not yet seen: a fully green run including the APK artifact, lint (not a gate), instrumented tests (none run anywhere).

## What exists and has been run

| Layer | Tooling | Status |
|---|---|---|
| JVM unit tests | kotest + JUnit platform, `./gradlew testDebugUnitTest` | **Run**: 714 tests, 0 failures, 0 errors (`:app` 497 + `:lib:snygg` 106 via `testDebugUnitTest`, plus `:lib:kotlin` 111 via `:lib:kotlin:test`, which `testDebugUnitTest` does not run; counted from the `TEST-*.xml` result files). 176 were measured on the unmodified baseline (`AUDIT.md` §6, `:app` + `:lib:snygg` only), so 436 are ours (336 below + 9 `curlyFormat` regression tests + 57 snippet picker/insertion/preview/caret/quick-action/UI-mode tests + 34 dictionary pack reader/parser/planner/asset tests): clipboard core (history policy, search, classifier, sensitive-data detector, backup crypto/format/hostile files, import planner, 500-entry round trip), `PayloadCipher`, backup library, password-field detection, Phase 4 terminal/developer core and key resolver (49), Phase 5 snippets core (55), operating modes (12), advanced developer settings (36) |
| Compile / package | `./gradlew assembleDebug` | **Run**, success |
| Release (minified) build | `assembleRelease` | see `AUDIT.md`/phase report for the latest result |
| Lint | `./gradlew lintDebug` | **Run, fails on pre-existing upstream code** (52 errors, none in new code at time of writing; `AUDIT.md` §8). Not yet a CI gate |
| Instrumented tests (`androidTest`) | one upstream example test | **Not run** (no device/emulator) |
| Manual device matrix | below | **Not run** |

### Kotest and Gradle's `--tests` filter

`./gradlew testDebugUnitTest --tests '*SomeTest'` does **not** work with this kotest version: every excluded spec
fails with `IllegalStateException: Could not find spec TestDescriptor` (`initializationError`). Run the whole suite.

### Dependency verification, cold checkout

`gradle/verification-metadata.xml` was regenerated from an empty Gradle home (`clean assembleDebug
testDebugUnitTest`, then `assembleRelease`, then `testReleaseUnitTest`, each with `--write-verification-metadata
sha256`) and then proven: with a *second* empty Gradle home, `./gradlew --no-daemon assembleDebug
testDebugUnitTest` (no write flag, the CI command) succeeded. Both runs fetched through a Maven Central mirror, so
the checksums are trust-on-first-use; the first GitHub Actions run (direct Central) is the independent check.

### Latest debug APK (built locally, not installed anywhere)

`app/build/outputs/apk/debug/app-debug.apk`: 31,411,921 bytes (built 2026-10-12 from the dictionary-pack working tree before
commit; it contains the five `assets/ime/dictionary-packs/*.zip`). The hash changes with every rebuild: recompute it with
`sha256sum`. Package, version and permissions were last checked with `aapt2 dump badging` on an earlier build
(`com.victorious.keyboard.debug`; VIBRATE, POST_NOTIFICATIONS; no `INTERNET`), not re-checked on this one. The APK has never
been installed or run. (An intermediate local build was 34 MB with no code explanation; the size is not stable between
local debug builds.)

## Environment limits

The reference environment has no Android device and no `/dev/kvm`, so an emulator is not usable. Therefore **no
claim is made** that the keyboard installs, is selectable, types, or behaves in any app. CI will eventually run an
emulator job (Phase 10).

## Manual device checklist (to run on real hardware before any release)

Install the debug APK → enable in *Settings → System → Keyboard* → select via the input picker, then:

- [ ] Type in: a notes app, a messaging app, a browser URL bar and web form, a search field, a multiline editor.
- [ ] Password field: no suggestions, nothing enters clipboard history, no expansion.
- [ ] Rotate to landscape and back mid-typing; switch apps; lock/unlock.
- [ ] Shift / caps lock, long-press alternates, key repeat, haptics, number row.
- [ ] Developer row (enable in Settings → Keyboard → Developer mode): it appears above the keyboard, the keyboard is not
  clipped, rotate and switch fields; scroll the row (must not type); Ctrl/Alt tap → pressed look, double tap → locked
  (CAPITALS), tap → off; hold an arrow (repeats); switch profile and check the symbols; turn the pref off (row gone).
- [ ] Termux: Esc, Tab, Ctrl+C / D / L / Z / A / E / R / W / U / K, Ctrl+\\, Alt+key, arrows with Ctrl, Home/End, a locked
  Ctrl across several keys, a lock must not survive an app switch (`TERMINAL.md`).
- [ ] Snippet picker (add 3 snippets in Settings → Keyboard → Developer mode → Snippets, one with a tag, one with
  `{{cursor}}`, one with `{{selection}}`): the "Snippets" quick action is present (after an update from an older build it
  is in the overflow panel); the panel opens at the same height as the text view (also with the developer row on, and from
  the overflow panel), shows the list and the tag chips, scrolls without inserting; tap inserts at the caret and returns
  to the keyboard; with text selected, `{{selection}}` wraps it and the selection is replaced; `{{cursor}}` ends up in the
  right place also before an emoji; a very long clipboard in `{{clipboard}}` is refused with a message; password field:
  `{{clipboard}}`/`{{selection}}` come out empty; panel shows "Locked" on the lock screen; after a reboot, before the
  first unlock, nothing is read; in Termux the text arrives and the caret lands correctly; rotate with the panel open.
- [ ] Dictionary packs (Settings → Typing → Dictionary, internal user dictionary on): the "Dictionary packs" screen lists 5
  packs with word counts; add one → toast with the number, the words appear in *Manage internal user dictionary*; add it
  again → 0 added; edit one word's frequency, then Remove → that word stays, the others go; with the internal dictionary
  off → "Turn on the internal user dictionary first"; open a non-zip file → "not a valid dictionary pack". Note what the
  spell checker flags before and after (the only place the words have an effect today).
- [ ] Clipboard: copy 3 items elsewhere → they appear; pin, search, delete; restart process → still there (Phase 3).
- [ ] Low-memory device and Android 8 / 12 / 14 / 15+ samples.
