# Phase 0 Audit

Date: 2026-10-11. Everything below was observed in this repository or in the build sandbox. Statements that are
inferences are marked *(inference)*.

## 1. Starting state

The repository contained one commit (`Initial commit`) with a one-line README. No code, no upstream history.

## 2. Upstream base: decision

Victorious Keyboard is a **modified FlorisBoard distribution** (not a clean-room implementation).

| Candidate | Upstream commit | Result in this sandbox | Verdict |
|---|---|---|---|
| `main` tip | `fe1241f4921b3eae923571ff7a3e113a7e10d677` (2026-09-30, "v0.6.0-alpha02 + 103 commits") | `assembleDebug` OK (4m59s), 70 unit tests pass, 7 skipped | Rejected |
| `v0.5.2` (last stable) | `2e82060251897226c0739b9f52d1d051b02305fb` | Build **fails**: requires `jetpref:20251119T222500Z-SNAPSHOT` from `central.sonatype.com` snapshots; host denied by the sandbox's egress policy | Rejected |
| **`v0.6.0-alpha02`** | **`0ea0481c40616b0fd6eb4cd09c57521181b9a4b6`** (2026-01-23) | `assembleDebug` OK (4m53s), **176 unit tests pass, 0 fail, 7 skipped** | **Chosen** |

Why not `main`:

* From `v0.5.2` to `main`: 208 commits, 455 files, +37 742 / −31 894 lines. The input stack was rewritten
  (commit messages: "Remove EditorInstance and KeyboardManager", "Remove old InputEventDispatcher", "It compiles
  again!", "Convert :lib:snygg to cmp module").
* At `main` the legacy `TextKeyboardLayout` composable (where glide typing is wired in) has no call site; the live
  renderer is `ime/keyboard3/ui/ImeKeyboardBox`. Upstream's own roadmap lists glide typing as "to be reimplemented".
  *(inference: glide typing is currently non-functional at `main`.)*
* `main` has fewer passing unit tests (70) than `alpha02` (176) because the rewrite removed tests.
* The only validation possible here is compile + JVM tests; there is no emulator (see §5). An unreleased rewrite
  that cannot be run on a device is the wrong base for "prioritise keyboard stability".

Why not `v0.5.2`: it depends on a SNAPSHOT artifact. Snapshots are not reproducible, can be purged upstream, and the
snapshot host is blocked here. `alpha02` uses only released versions (jetpref `0.3.0-rc01`).

Consequence: upstream `main` can no longer be merged mechanically (the engine it replaces is the one we build on).
This is effectively a **permanent hard fork** from `alpha02`. Upstream fixes must be cherry-picked by hand.

## 3. Upstream facts relevant to requirements

Architecture (alpha02): single Android app module `:app` (~223 Kotlin files) + libraries `:lib:android`,
`:lib:color`, `:lib:compose`, `:lib:kotlin`, `:lib:snygg` (theme engine), `:lib:native` (Rust+CMake JNI, currently a
`dummyAdd` placeholder but built into the APK as `libfl_native.so`). Details in `ARCHITECTURE.md`.

| Requirement area | What upstream alpha02 actually has | Gap |
|---|---|---|
| Word suggestions / autocorrect | `LatinLanguageProvider.suggest()` returns `emptyList()`; `spell()` is a hard-coded test stub (`"typo"` → typo). Bundled `ime/dict/data.json` is a test dictionary. | **Not implemented.** Phase 6 is real work, not a toggle. |
| Glide typing | `StatisticalGlideTypingClassifier` + `GlideTypingManager` exist and are wired to `TextKeyboardLayout`. | Needs a dictionary to be useful; runtime unverified here. |
| Clipboard history | Room DB (`clipboard_history`, version 4): text/image/video items, `isPinned`, `is_sensitive`, `is_remote_device`; size limit pref; auto-clean; `ClipboardMediaProvider` for images. | No search, tags, categories, favourites vs pins, editing, multi-select export, backup/import, encryption. Default limit is a user pref, not a hard 500 cap. |
| Clipboard data safety | `Room.databaseBuilder(...).fallbackToDestructiveMigration()` on both clipboard DBs. | **A schema mismatch silently wipes history.** Violates "never silently destroy pinned data". Must be replaced by explicit migrations. |
| Clipboard at-rest protection | Plain SQLite, plain text. | Not encrypted. |
| Sensitive clips | Reads `ClipDescription.EXTRA_IS_SENSITIVE` (API 33+) and masks the item. | Good base; password-field capture must still be tested. |
| Terminal keys | `KeyCode` has `ESCAPE`, `TAB`, `CTRL`, `CTRL_LOCK`, `ALT`, `ALT_LOCK`, arrows, page/line moves; predefined `TextKeyData.CTRL/ALT`. `EditorInstance` sends real `KeyEvent`s with `META_CTRL_ON` for select-all/undo/redo. | No evidence yet of a general Ctrl+<letter> path or sticky-modifier state machine. **To be verified/implemented in Phase 4**, then tested in Termux on a device. |
| Snippets / AI / developer rows / profiles | None. | Greenfield. |
| Themes | Snygg theme engine, theme extensions, time-based switching. | Presets required by the brief are new content. |
| One-handed / floating | `ime/onehanded` exists. | Floating mode is not present at alpha02 (upstream lists it under 0.7+). |
| Voice | Not present. | Greenfield (Android `SpeechRecognizer`/`RecognizerIntent`). |
| Permissions / privacy surface | Manifest requests only `VIBRATE` and `POST_NOTIFICATIONS`. **No `INTERNET`.** Content-provider authorities use `${applicationId}`, so changing the application id is safe. | Keep it that way until an explicit, opt-in AI adapter (P7) needs network. |
| Tests | 176 JVM tests (kotest/JUnit5 platform) incl. window, quick-action, extension code. | No clipboard, IME-editor, or instrumented coverage. `androidTest` has one example test. |
| Toolchain | AGP 8.12.3, Kotlin 2.3.0, Gradle 9.2.0, compile/target SDK 36, min SDK 26, NDK 26.1.10909125, CMake 4.0.2, Rust 1.83.0, JDK 17 in upstream CI. | We built with JDK 21 successfully. |

## 4. Legal / policy findings

* Upstream is **Apache-2.0**; modification and redistribution are permitted if notices are preserved (see
  `LICENSES.md`).
* Upstream ships `AI_POLICY.md`: it forbids AI-generated code or communications **in contributions to FlorisBoard**.
  It governs upstream contributions, not an Apache-2.0 fork. Practical consequences: (1) code written in this fork
  with AI assistance **must not be submitted upstream**; (2) the policy file is kept for reference at
  `docs/upstream/AI_POLICY.md` and is *not* adopted as this project's policy. The owner should be aware of this when
  deciding how to publish the fork.
* Project name/branding: FlorisBoard's name and icon are the upstream project's; the application id, label and icon
  are being replaced in Phase 1 (`LICENSES.md` §3).
* No Gboard source, assets or models are used or needed.

## 5. Build environment (sandbox) and its limits

Verified: JDK 21.0.12, Gradle wrapper (downloaded, 9.2.0 for alpha02 / 9.7.1 for `main`), Android SDK cmdline-tools
`14742923` (checksum matched upstream's pinned value), platform 36, build-tools 35.0.0, NDK 26.1.10909125,
CMake 4.0.2, Rust via preinstalled rustup/cargo.

Environment problems and handling (none are project defects):

1. **HTTP 429 from Maven Central** (`repo.maven.apache.org`, `repo1.maven.org`, and `plugins.gradle.org` redirects)
   for the sandbox's shared IP. Worked around **outside the repository** with a user-level Gradle init script that
   prefers Google's GCS mirror of Maven Central. The project's repository configuration is unchanged. Because a
   mirror is involved, Phase 1 adds Gradle dependency verification (checksums) so mirror-vs-origin differences cannot go
   unnoticed.
2. **`central.sonatype.com` snapshots denied** by egress policy → `v0.5.2` is unbuildable here (see §2). Not worked around.
3. **No `/dev/kvm`** → no hardware-accelerated emulator. **No Android device is attached.** Therefore *nothing in
   this project has been installed or run on Android in this session.* Compile-time and JVM tests only. Device
   validation steps are tracked in `TESTING.md` as "not run".

## 6. Baseline results (unmodified upstream `v0.6.0-alpha02`)

| Check | Result |
|---|---|
| `./gradlew assembleDebug` | **Success**, 4m53s |
| APK | `app-debug.apk`, 37 368 386 bytes, sha256 `51094ae4491567757c9af1c65f83f7e2bf870de8100998cbccc2d67c14b6f78a`, package `dev.patrickgold.florisboard.debug`, minSdk 26, target/compile 36, ABIs arm64-v8a/armeabi-v7a/x86/x86_64 (inspected with `aapt2 dump badging`) |
| `./gradlew testDebugUnitTest` | **Success**: 176 passed, 0 failed, 7 skipped |
| Install / run on device | **Not run** (no device or emulator) |
| Lint, instrumented tests, benchmarks | **Not run** |

## 7. Risks register

| # | Risk | Impact | Mitigation / owner phase |
|---|---|---|---|
| R1 | Hard fork from an alpha tag; upstream can't be merged | Ongoing maintenance cost | Accept; document; cherry-pick security fixes (P1) |
| R2 | No device validation possible in this environment | IME bugs invisible until run on hardware | Maximise JVM-testable logic (pure Kotlin cores); document manual test matrix (`TESTING.md`); CI emulator job in P10 |
| R3 | `fallbackToDestructiveMigration` on clipboard DB | Silent data loss | Replace with explicit migrations + migration tests (P3) |
| R4 | Suggestions/autocorrect absent upstream | Feature gap vs Gboard | Real implementation in P6; no fake toggles |
| R5 | `FlorisApplication.onCreate` calls JNI `dummyAdd(3,4)` (debug scaffolding in `:lib:native`) after a swallowed `loadLibrary` failure. `UnsatisfiedLinkError` is an `Error`, not an `Exception`, so a load failure would crash the app process at startup. The module also forces Rust + NDK + CMake onto every build and ships `libfl_native.so` in 4 ABIs. | IME startup crash path; build complexity; APK size | **Remove `:lib:native` and the call in Phase 1** (nothing else uses it: verified by grep) |
| R6 | Rename of Kotlin package would touch ~250 files | Churn, merge cost | Change `applicationId` only; keep code namespace `dev.patrickgold.florisboard` (documented in `ARCHITECTURE.md`) |
| R7 | Terminal modifier behaviour is unverified | Ctrl+C might not reach Termux correctly | Pure-Kotlin key-event mapping with tests (P4) + device test checklist |
| R8 | Dependency integrity via mirror | Supply-chain | Gradle dependency verification (P1) |
