# Testing

## What exists and has been run

| Layer | Tooling | Status |
|---|---|---|
| JVM unit tests | kotest + JUnit platform, `./gradlew testDebugUnitTest` | **Run**: all pass. 176 inherited from upstream (app + libs) plus 134 new clipboard-core tests (history policy, search, classifier, sensitive-data detector, backup crypto/format/hostile files, import planner, 500-entry round trip). Numbers: `AUDIT.md` §6 and the phase report |
| Compile / package | `./gradlew assembleDebug` | **Run**, success |
| Release (minified) build | `assembleRelease` | see `AUDIT.md`/phase report for the latest result |
| Lint | `./gradlew lintDebug` | **Run, fails on pre-existing upstream code** (52 errors, none in new code at time of writing; `AUDIT.md` §8). Not yet a CI gate |
| Instrumented tests (`androidTest`) | one upstream example test | **Not run** (no device/emulator) |
| Manual device matrix | below | **Not run** |

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
- [ ] Termux: Esc, Tab, Ctrl+C / D / L / Z / A / E / R / W / U / K, arrows (Phase 4).
- [ ] Clipboard: copy 3 items elsewhere → they appear; pin, search, delete; restart process → still there (Phase 3).
- [ ] Low-memory device and Android 8 / 12 / 14 / 15+ samples.
