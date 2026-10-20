# Testing

## CI (GitHub Actions)

| Run | Result |
|---|---|
| 1-4 (PR and push, 2026-10-12) | failed at dependency verification (plugin-marker checksums, see `BUILD.md`), nothing compiled |
| 5 (`workflow_dispatch`, branch `ccr-08db99f8-cdmjq6`, commit `c92c878`) | **build + unit tests passed**; job red only at "Upload unit test reports": artifact storage quota hit; APK artifact skipped |
| PR #10 (`38353170157`) and its merge to `main` (`38357539442`) | **build + unit tests passed**; job red only at "Upload debug APK": "Artifact storage quota has been hit". No review comments |
| Merge of PR #11 to `main` (`38368541837`) | **fully green**, including both artifact uploads |
| PR #29, UI pass (`38668379528`, commit `029e087`) | **green**: `assembleDebug`, `testDebugUnitTest`, `:lib:kotlin:test` and `lintDebug` (`main` had been red at `lintDebug` since PRs #23/#25/#26) |
| PR #30, per-mode snippet expansion (`38875748244` attempt 2, commit `c7efaf2`) | **green**: `assembleDebug` (including the llama.cpp native build), `testDebugUnitTest`, `:lib:kotlin:test`, `lintDebug` and both uploads. Every earlier attempt on this PR (`38835325290`, `38837129992`, and attempt 1 of `38875748244`, 2026-10-16 01:28–08:44Z) failed in 2–3 s with no runner assigned (`runner_id: 0`), as did the push of PR #31's merge to `main` (`38853745308`): an account-level Actions block that the owner fixed; no code ran in those |
| Merge of PR #30 to `main` (commit `29af3d8`) | **green**: manual run `38961232420` (`workflow_dispatch`, 2026-10-16 20:19–20:41Z) passed every step, including `assembleDebug`, unit tests, lint and both uploads. The push run `38960866613` was cancelled after four minutes because the manual run took over the same `ci-refs/heads/main` concurrency group (`cancel-in-progress: true`), not because anything failed |
| PR #34, encrypted snippet export (`38992117081`, commit `5deed1e`) | **green**: job `build` passed (01:44–02:05Z): `assembleDebug` (including the llama.cpp native build), `testDebugUnitTest`, `:lib:kotlin:test`, `lintDebug`. The merge to `main` (`6a28240`): push run `39049962089` (16:33–16:55Z) **green**. |
| PR #40, toolbar row and chip gap fix (`39281537487`, commit `5326404`) | **green**: job `build` passed (15:55–16:07Z): `assembleDebug`, `testDebugUnitTest`, `:lib:kotlin:test`, `lintDebug`. The push run for the merge to `main` (`e6a856f`, run `39286596523`) was still queued when this was written; its result is not recorded here. |

PR #29's run was the first on GitHub to execute `lintDebug` and `:lib:kotlin:test` as well. Instrumented tests are still not run anywhere.

## What exists and has been run

| Layer | Tooling | Status |
|---|---|---|
| JVM unit tests | kotest + JUnit platform, `./gradlew testDebugUnitTest` | **Run (2026-10-20, File Manager contract on `a09f9a5e`)**: 1031 tests, 0 failures, 0 errors (`:app` 766 + `:lib:fleet` 45 + `:lib:snygg` 109 + `:lib:kotlin` 111), from `./gradlew --no-daemon --no-parallel --max-workers=2 clean assembleDebug testDebugUnitTest :lib:kotlin:test lintDebug` (EXIT 0 in 4m 40s). Lint: 0 errors, 226 warnings, 2 hints. `MediaControlTest` is 13. Not run on a device. **Earlier run (2026-10-20, File Manager pin on `20c3e688`)**: 1004 tests, 0 failures, 0 errors (`:app` 739 + `:lib:fleet` 45 + `:lib:snygg` 109 + `:lib:kotlin` 111) in 4m 46s. `MediaControlTest` was 11. **Earlier run (2026-10-20, media keys on `09794401`)**: 1003 tests, 0 failures, 0 errors (`:app` 738 + `:lib:fleet` 45 + `:lib:snygg` 109 + `:lib:kotlin` 111) in 4m 48s. Adds `MediaControlTest` (10) and `ManualCopyTest` (1). **Earlier run (2026-10-20, toolbar layout on `8c84280c`, before the testing-note commit)**: 1004 tests, 0 failures, 0 errors (`:app` 739 + `:lib:fleet` 45 + `:lib:snygg` 109 + `:lib:kotlin` 111) in 4m 45s. **Earlier run (2026-10-19, AI control through the Hub bridge on `main` at `0b10e49`)**: 974 tests, 0 failures, 0 errors (`:app` 715 + `:lib:fleet` 42 + `:lib:snygg` 106 + `:lib:kotlin` 111, from `testDebugUnitTest`, `:lib:kotlin:test` and `:lib:fleet:testDebugUnitTest`, counted from the result XMLs); adds 37 (`BridgeInputTest` 34, `BridgeConfigTest` +3). `assembleDebug` and lint were not run here (llama.cpp download blocked); GitHub CI run `39299472483` on `ac4b7b9` ran the full `build` job (`assembleDebug`, unit tests, `:lib:kotlin:test`, `lintDebug`) and passed. Not run on a device. **Earlier run (2026-10-19, toolbar row on `aec5919`)**: 929 tests, 0 failures, 0 errors (`:app` 712 + `:lib:snygg` 106 + `:lib:kotlin` 111, from `testDebugUnitTest` and `:lib:kotlin:test`; `:lib:fleet` is not part of those commands); adds 18 (`PinnedToolbarTest`). **Earlier run (2026-10-17, fleet branch with main after PR #36, including the screenshot tile and snippet export)**: 916 tests, 0 failures, 0 errors (`:app` 691 + `:lib:fleet` 8 + `:lib:snygg` 106 + `:lib:kotlin` 111). `:lib:fleet` was up to date. Not run on a device. The screenshot tree on `6a282401`, before fleet, was 906 (`:app` 689 + `:lib:snygg` 106 + `:lib:kotlin` 111). The screenshot tile alone, on `6e72d207`, was 886 (`:app` 669 + `:lib:snygg` 106 + `:lib:kotlin` 111) and added 7 (`ScreenshotPolicyTest` 6, `ScreenshotConfigTest` 1). Encrypted snippet export on that same base was 899 (`:app` 682) and added 20 (`SnippetEncryptedBackupTest`). **Earlier run (2026-10-16, the code of `main` after PR #32, `b3ca800`)**: 879 tests, 0 failures, 0 errors (`:app` 662 + `:lib:snygg` 106 + `:lib:kotlin` 111); PR #32 adds 3 to the per-mode snippet run below. **Run (2026-10-16, per-mode snippet expansion merged with `main` at `70e8f5d`; the same tree is `main` after PR #30, `29af3d8`)**: 876 tests, 0 failures, 0 errors (`:app` 659 + `:lib:snygg` 106 via `testDebugUnitTest`, plus `:lib:kotlin` 111 via `:lib:kotlin:test`, which `testDebugUnitTest` does not run; counted from the `TEST-*.xml` result files). This phase adds 16 (`ModeExpansionTest` 10, `SnippetModeMigrationTest` 5, which loads a stored preference file through jetpref and `AppPrefs.migrate`, and one more in `ExpansionActionTest`). The slash-anchor fix on `70e8f5df` measured `:app` 646 + `:lib:kotlin` 111 + `:lib:snygg` 106 before that snippet work was merged. Before either, PR #31 (bug-fix pass, run on `9cc6955`) measured `:app` 643 + `:lib:kotlin` 111, 0 failures, and added 6 (`TextTransformsUnicodeTest` 2, `ToolTileFitTest` 2, `StripSlashTest` +2). The UI pass (PR #29) measured 854 (`:app` 637), adding 31 to the 823 measured at `a6356e5` (`TerminalRowPlanTest` 13, `ToolsCatalogTest` 11, `PanelPaletteTest` 7). The older breakdown that follows dates from 730 total and was not re-derived for the PRs in between. 176 were measured on the unmodified baseline (`AUDIT.md` §6, `:app` + `:lib:snygg` only), so 452 are ours (336 below + 9 `curlyFormat` regression tests + 57 snippet picker/insertion/preview/caret/quick-action/UI-mode tests + 34 dictionary pack reader/parser/planner/asset tests + 14 Enter/Tab expansion tests + 2 stored-locale tag tests): clipboard core (history policy, search, classifier, sensitive-data detector, backup crypto/format/hostile files, import planner, 500-entry round trip), `PayloadCipher`, backup library, password-field detection, Phase 4 terminal/developer core and key resolver (49), Phase 5 snippets core (55), operating modes (12), advanced developer settings (36), Enter/Tab expansion decision (14) |
| Compile / package | `./gradlew assembleDebug` | Last local success before the llama.cpp step. On 2026-10-15 the sandbox could compile all Kotlin but not package: the native step downloads `ggml-org/llama.cpp` `b11624` as a GitHub tarball, which the session's egress policy refused (HTTP 403). CI downloads it directly |
| Release (minified) build | `assembleRelease` | see `AUDIT.md`/phase report for the latest result |
| Lint | `./gradlew lintDebug` | **Run (2026-10-20, File Manager contract on `a09f9a5e`): 0 errors, 226 warnings, 2 hints** (`app/build/reports/lint-results-debug.txt`). **Earlier run (2026-10-20, File Manager pin on `20c3e688`): 0 errors, 227 warnings, 2 hints** (`app/build/reports/lint-results-debug.txt`). **Earlier run (2026-10-17, fleet branch with main after PR #36): 0 errors, 225 warnings, 2 hints** (`app/build/reports/lint-results-debug.txt`). The screenshot tile run the same day was also 0 errors, 225 warnings, 2 hints, after marking `canTakeScreenshot` as API 30 (`tools:targetApi`). The first pass had 1 `NewApi` error (`Bitmap.wrapHardwareBuffer`, API 29) and that call is now limited to API 30. **Encrypted snippet export (PR #34, `6e72d20`): 0 errors, 225 warnings.** `main` at `a6356e5` had 3 `NewApi` errors from earlier PRs (`InputStream.readNBytes` in `AiFiles`, `URLEncoder`/`URLDecoder` with a `Charset` in `TextTransforms`; all API 33, minSdk 26); the UI pass fixes them. The task is a CI gate. Warnings do not fail the build |
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
- [ ] Terminal key row (Settings → Developer & terminal → Show terminal key row): Auto shows it in Termux and in Developer
  mode, not in a notes app; Always and Never do what they say; it appears above the keyboard, the keyboard is not
  clipped, rotate and switch fields; scroll the row (must not type); Ctrl/Alt tap → pressed look, double tap → locked
  (CAPITALS), tap → off; hold an arrow (repeats); switch profile and check the symbols; turn *Terminal keys* off (Esc/Tab/
  Ctrl/Alt gone) and Advanced → *Navigation keys* off (arrows gone).
- [ ] Tools panel (tools button left of the suggestions): five tabs, tab switch keeps the panel height, the app behind the
  keyboard does not move when it opens (also in Termux with the row showing); every tile has an icon and a readable
  label in Victorious Black, Neon Light and a Floris day theme, and after picking Keyboard colors presets; Incognito,
  Learn words, Key row and Bottom gap show their state; Terminal tab scrolls to its last row; a saved command from
  Settings → Smartbar → Tools panel appears under "My commands" and is inserted, not run; hiding and reordering a button
  in that screen shows on the keyboard; the Tune button opens that screen; TalkBack reads each tile's label and state.
- [ ] AI panel and clip editor in a light theme: light background, dark readable text, visible selection.
- [ ] Snippet expansion per mode (Snippets → Expansion → Per keyboard mode, master switch and Enter on): in Standard,
  `;sig` + Enter expands; set Developer to *Space only* and switch Default mode to Developer: Enter no longer expands,
  Space does; in Termux (default *Off*) nothing expands; set Termux to *Space only*: Space expands, Enter sends the line
  unchanged; turn the master switch off: nothing expands in any mode. Upgrade from a build where *Expand snippets in
  Termux mode* was on: the Termux row shows *Space, plus Enter and Tab if on*.
- [ ] Settings Home: all groups and summaries fit without truncation on a small phone; Developer & terminal and
  Snippets → Expansion open and change the same settings the keyboard uses.
- [ ] Termux: Esc, Tab, Ctrl+C / D / L / Z / A / E / R / W / U / K, Ctrl+\\, Alt+key, arrows with Ctrl, Home/End, a locked
  Ctrl across several keys, a lock must not survive an app switch (`TERMINAL.md`).
- [ ] Snippet picker (add 3 snippets in Settings → Tools → Snippets, one with a tag, one with
  `{{cursor}}`, one with `{{selection}}`): the "Snippets" quick action is present (after an update from an older build it
  is in the overflow panel); the panel opens at the same height as the text view (also with the developer row on, and from
  the overflow panel), shows the list and the tag chips, scrolls without inserting; tap inserts at the caret and returns
  to the keyboard; with text selected, `{{selection}}` wraps it and the selection is replaced; `{{cursor}}` ends up in the
  right place also before an emoji; a very long clipboard in `{{clipboard}}` is refused with a message; password field:
  `{{clipboard}}`/`{{selection}}` come out empty; panel shows "Locked" on the lock screen; after a reboot, before the
  first unlock, nothing is read; in Termux the text arrives and the caret lands correctly; rotate with the panel open.
- [ ] Snippet expansion on Enter and Tab (turn on "Expand snippets while typing", then "Also expand on Enter" and
  "Also expand on Tab"): `;sig` then Enter inserts the snippet and still sends Enter (newline, or Search/Send); `;sig`
  then Tab inserts and still sends Tab; Enter or Tab with no matching abbreviation does the normal action and leaves
  the typed text; the same in a password field (no expansion); Shift+Enter, Ctrl+Tab and a text selection are not
  expanded; a snippet with `{{cursor}}` does not also send Enter/Tab; turn "Keep Enter after expanding" off and Enter
  expands without sending.
- [ ] Dictionary packs (Settings → Typing → Dictionary, internal user dictionary on): the "Dictionary packs" screen lists 5
  packs with word counts; add one → toast with the number, the words appear in *Manage internal user dictionary*; add it
  again → 0 added; edit one word's frequency, then Remove → that word stays, the others go; with the internal dictionary
  off → "Turn on the internal user dictionary first"; open a non-zip file → "not a valid dictionary pack". Note what the
  spell checker flags before and after (the only place the words have an effect today).
- [ ] Clipboard: copy 3 items elsewhere → they appear; pin, search, delete; restart process → still there (Phase 3).
- [ ] Low-memory device and Android 8 / 12 / 14 / 15+ samples.
