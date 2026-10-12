# Testing

## What exists and has been run

| Layer | Tooling | Status |
|---|---|---|
| JVM unit tests | kotest + JUnit platform, `./gradlew testDebugUnitTest` | **Run**: 394 tests, 0 failures, 0 errors, 0 skipped (`:app` 288 + `:lib:snygg` 106; counted from `**/build/test-results/testDebugUnitTest/TEST-*.xml`). 176 were measured on the unmodified baseline (`AUDIT.md` §6), so 218 are ours: clipboard core (history policy, search, classifier, sensitive-data detector, backup crypto/format/hostile files, import planner, 500-entry round trip), `PayloadCipher`, backup library, password-field detection, Phase 4 terminal/developer core (37) |
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

`app/build/outputs/apk/debug/app-debug.apk`: 31,077,571 bytes, sha256
`5bf28e6e24d95435f4a181ec7760066afd43a661943dc7971fc5e4bff9bf7197`. `aapt2 dump badging`: package
`com.victorious.keyboard.debug`, versionCode 120, versionName `0.1.0-debug+f9968b4`, label "Victorious Keyboard Debug".
Permissions: `VIBRATE`, `POST_NOTIFICATIONS` and the app-internal `...DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`; no
`INTERNET`. This only shows what the manifest declares; the APK has never been installed or run.

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
- [ ] Termux: Esc, Tab, Ctrl+C / D / L / Z / A / E / R / W / U / K, arrows (Phase 4; needs the Phase 4 UI to expose a Ctrl key, see `TERMINAL.md`).
- [ ] Clipboard: copy 3 items elsewhere → they appear; pin, search, delete; restart process → still there (Phase 3).
- [ ] Low-memory device and Android 8 / 12 / 14 / 15+ samples.
