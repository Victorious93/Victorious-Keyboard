<!-- victorious-fleet-readme-refresh:start -->
<p align="center">
  <img src="assets/logo.png" alt="Victorious-Keyboard logo" width="200">
</p>

# Victorious-Keyboard

Custom Android keyboard with integrated media controls for the fleet.

**Package id:** `com.victorious.keyboard`

**Status:** CI green / merging *(from [fleet-manifest ROADMAP](https://github.com/Victorious93/fleet-manifest/blob/main/ROADMAP.md))*

## Install / build

Gradle Android app (`app/build.gradle.kts`). Typical: `./gradlew :app:assembleDebug`.

_Steps above are taken from this repo’s build files / existing docs; anything not exercised in this refresh is unlabeled as verified on a device._

## Victorious fleet

- Monorepo: [https://github.com/Victorious93/victorious-fleet](https://github.com/Victorious93/victorious-fleet)
- Fleet site: [https://victorious-framework.pages.dev](https://victorious-framework.pages.dev)

<!-- victorious-fleet-readme-refresh:end -->

<div align="center">

<img src="docs/branding/logo.png" alt="Victorious Keyboard logo" width="128">

# Victorious Keyboard

**An open-source Android keyboard for everyday typing, developers, and terminal users — with a clipboard manager, snippets, and offline tools.**

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Kotlin 2.3](https://img.shields.io/badge/Kotlin-2.3-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack_Compose-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/compose)
![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B_(API_26)-3DDC84?logo=android&logoColor=white)
[![Based on FlorisBoard](https://img.shields.io/badge/based_on-FlorisBoard_v0.6.0--alpha02-green)](https://github.com/florisboard/florisboard)
![Status: early development](https://img.shields.io/badge/status-early_development-orange)

[Overview](#overview) · [Features](#features) · [Screenshots](#screenshots) · [Status](#status) · [Build](#build-and-install) · [Contributing](#contributing) · [Licence](#licence)

</div>

> [!WARNING]
> **Early development.** The core logic of the Victorious additions is unit-tested on the JVM. The Android screens and
> wiring compile, but **none of the additions has been run on an Android device yet.** Do not use this as your only
> keyboard. Details: [Status](#status).

## Overview

Victorious Keyboard is a modified distribution of [FlorisBoard](https://github.com/florisboard/florisboard)
(Apache-2.0, based on upstream tag `v0.6.0-alpha02`). It keeps FlorisBoard's typing engine, layouts and themes and adds:

* **Clipboard manager** — up to 500 items with search, pinning, filters, and AES-256-GCM encrypted storage (Android Keystore–wrapped key).
* **Developer row and operating modes** — Esc, Tab, Ctrl, Alt and arrows; Standard / Developer / Termux modes with optional auto-detect for Termux and ConnectBot.
* **Snippets** — reusable text with templates (`{{selection}}`, `{{clipboard}}`, date/time), an in-keyboard picker, and optional expansion on Space (Enter and Tab can be turned on separately).
* **Dictionary packs** — five bundled word lists for technical vocabulary (install/remove on your choice).

It is not affiliated with or endorsed by the FlorisBoard project. The repository retains upstream's full Git history so per-file authorship is preserved. Design goal: **nothing leaves the device** — no analytics, no ads, and no `INTERNET` permission.

## Features

Status key: ✅ **tested** (JVM unit tests) · 🟦 **built, not run** on a device · 🔹 **inherited** from FlorisBoard · ⬜ **planned**

### Typing (inherited)

QWERTY, numeric and symbol layouts, emoji, long-press, key repeat, haptics, sounds, themes, one-handed mode, number row, shift/caps — 🔹. Physical keyboard support is incomplete upstream. Glide typing has a classifier but needs a dictionary and is unverified. Floating/split keyboard is planned.

### Clipboard manager

| Feature | Status |
|---|---|
| 500-item history; pinned items never evicted | ✅ policy · 🟦 wired |
| Encrypted at rest (AES-256-GCM, Keystore-wrapped) | ✅ cipher · 🟦 Room/Keystore |
| Search, filters, tags, favourites, edit, multi-select, share | ✅ logic · 🟦 screens |
| Password / API-key / card-looking text not saved by default | ✅ |
| Copy/Cut refused in password fields | ✅ detection · 🟦 call site |
| Export/import: JSON, ZIP, encrypted archive, Markdown, text | ✅ logic · 🟦 file pickers |
| Images/URI clips in backups | ⛔ skipped (cannot restore safely) |

Details: [CLIPBOARD.md](CLIPBOARD.md), [BACKUP_FORMAT.md](BACKUP_FORMAT.md).

### Developer and terminal

Developer row (off by default): Esc, Tab, Ctrl, Alt, arrows, Home/End/PgUp/PgDn. Keys send real key events (not literal `Ctrl+C` text); F1–F12 supported. Ctrl/Alt: tap, double-tap lock, or hold. Modes: Standard, Developer, Termux; optional auto-detect for Termux and ConnectBot. Termux/SSH/vim/tmux behaviour and Claude Code / Kali / NetHunter modes are **not verified** on device yet.

Details: [TERMINAL.md](TERMINAL.md), [docs/DEVELOPER_KEYBOARD.md](docs/DEVELOPER_KEYBOARD.md).

### Snippets

Template language: `{{selection}}`, `{{cursor}}`, `{{clipboard}}`, `{{date}}`, `{{time}}`, `{{datetime}}` (optional formats) — ✅ renderer. Space expansion is opt-in and never in password fields (✅ rules · 🟦 wiring). Enter and Tab expansion is each off until you turn it on; a key that matches nothing still does its normal action (✅ rules · 🟦 wiring). Library of up to 500 snippets with unique abbreviations and versioned JSON import/export (✅ logic · 🟦 settings screen). In-keyboard picker: quick action, panel, insert at the cursor (✅ insertion rules · 🟦 panel). Typed search in the picker and encrypted export are ⬜. Shell/exec/env templates are **not** implemented (placeholders stay literal).

Details: [SNIPPETS.md](SNIPPETS.md).

### Dictionary packs

Five bundled word lists: Kali tools, NetHunter, AI terms, slash commands, Termux. Strict zip reader; non-destructive install/remove. Lists were written from memory and are not yet verified against official sources. Words reach the system spell checker only — **word suggestions are not implemented yet.**

Details: [DICTIONARY_PACKS.md](DICTIONARY_PACKS.md).

### Planned (not started or incomplete)

* Word suggestions, autocorrect, next-word prediction, personal dictionary
* Victorious theme presets and responsive layout (Phase 8)
* AI workspace: prompt library, local transforms, optional provider adapters (Phase 7; network only after you enable a provider)
* Voice typing via Android's recogniser

## Screenshots

> Placeholders — the app has not been run on a device yet, so no real screenshots are available.

| Keyboard (default) | Developer row | Clipboard history | Snippets picker | Settings |
|---|---|---|---|---|
| *TODO* | *TODO* | *TODO* | *TODO* | *TODO* |

Suggested captures once a device build is available: default QWERTY; developer row with Ctrl locked; clipboard history with a pinned item and search; snippet picker inserting a template; dictionary-packs install screen; About / privacy screen showing no Internet permission.

## Status

| Phase | Scope | Status |
|---|---|---|
| 0 | Audit and baseline | ✅ |
| 1 | Project foundation, variants, CI | ✅ builds; unit tests pass |
| 2 | Stable keyboard engine | ⬜ needs a device |
| 3 | Advanced clipboard | 🟡 core tested; storage/UI compiled, not run |
| 4 | Developer and terminal modes | 🟡 core tested; wiring/row UI compiled, not run |
| 5 | Snippets and productivity | 🟡 core tested; Android shell compiled, not run |
| 6–10 | Prediction, AI workspace, themes, security/perf, release | ⬜ |

Full detail: [ROADMAP.md](ROADMAP.md), [FEATURES.md](FEATURES.md).

## APK at a glance

| | |
|---|---|
| Application ID | `com.victorious.keyboard` (debug: `com.victorious.keyboard.debug`) |
| Version | `0.1.0-alpha01` (version code `120`) |
| Android | 8.0+ (`minSdk` 26); compile/target API 36 |
| Permissions | `VIBRATE`, `POST_NOTIFICATIONS` (setup flow) |
| Internet | **None** — no `INTERNET` permission |
| Analytics / ads / telemetry | None |
| Data | On-device only; clipboard and snippets excluded from Android auto-backup |
| Distribution | No signed release yet — build from source |

Privacy: [PRIVACY.md](PRIVACY.md).

## Requirements

* **JDK 21** (matches CI)
* Android SDK: `platforms;android-36`, `build-tools;35.0.0`
* Optional: connected device or emulator for sideload testing

## Build and install

Full steps and environment notes: [BUILD.md](BUILD.md).

```sh
git clone https://github.com/Victorious93/Victorious-Keyboard.git
cd Victorious-Keyboard
echo "sdk.dir=$ANDROID_HOME" > local.properties

./gradlew assembleDebug                          # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest :lib:kotlin:test     # JVM unit tests
```

Install with `adb install app/build/outputs/apk/debug/app-debug.apk`, then enable Victorious Keyboard under Android *Language & input* and select it as the active IME. Nothing in this repository has been installed or run on a device.

## Project structure

```text
app/                         Android application
  src/main/kotlin/dev/patrickgold/florisboard/
    FlorisImeService.kt      Input method service
    ime/clipboard/           Clipboard history, vault, backup/restore
    ime/developer/, terminal/ Developer row, key mapping, modifiers
    ime/mode/                Standard / Developer / Termux modes
    ime/snippets/            Templates, library, expansion, picker
    ime/dictionary/          Dictionaries and packs
lib/                         android, color, compose, kotlin, snygg
dictionary-packs/            Sources for the five bundled word lists
tools/, utils/               Pack builder and upstream utilities
docs/                        Branding, developer-keyboard notes, upstream docs
```

Kotlin package stays `dev.patrickgold.florisboard` to ease upstream merges; product identity is the application ID and resources. New features follow *pure-Kotlin core + thin Android shell* — see [ARCHITECTURE.md](ARCHITECTURE.md).

## Documentation

| Document | Covers |
|---|---|
| [FEATURES.md](FEATURES.md) · [ROADMAP.md](ROADMAP.md) | Feature status and phases |
| [ARCHITECTURE.md](ARCHITECTURE.md) · [BUILD.md](BUILD.md) · [TESTING.md](TESTING.md) | Structure, build, tests |
| [CLIPBOARD.md](CLIPBOARD.md) · [SNIPPETS.md](SNIPPETS.md) · [DICTIONARY_PACKS.md](DICTIONARY_PACKS.md) · [TERMINAL.md](TERMINAL.md) | Feature design docs |
| [PRIVACY.md](PRIVACY.md) · [SECURITY.md](SECURITY.md) | Data handling and vulnerability reports |
| [CONTRIBUTING.md](CONTRIBUTING.md) · [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) | Contribution rules |
| [LICENSES.md](LICENSES.md) · [docs/upstream/](docs/upstream/) | Licences and upstream attribution |

## Contributing

Issues and pull requests are welcome. Correctness and honesty about what was tested matter more than speed. Read [CONTRIBUTING.md](CONTRIBUTING.md) first:

* One logical change per PR; keep `./gradlew assembleDebug testDebugUnitTest` green.
* Put logic in pure Kotlin and unit-test it, including failure paths.
* Never log or transmit typed text, clipboard contents, or snippets.
* State in the PR what you tested and what you could not (e.g. no device).

Follow the [Code of Conduct](CODE_OF_CONDUCT.md). Report vulnerabilities via [SECURITY.md](SECURITY.md).

## Licence

Licensed under the [Apache License 2.0](LICENSE); see also [LICENSES.md](LICENSES.md). Upstream copyright notices are preserved; the About screen credits FlorisBoard.

## Acknowledgements

* **[FlorisBoard](https://github.com/florisboard/florisboard)** by Patrick Goldinger and The FlorisBoard Contributors (Apache-2.0), base `v0.6.0-alpha02` (commit `0ea0481c`). Typing engine, layouts, themes, Snygg, and settings framework are theirs.
* Libraries listed in [LICENSES.md](LICENSES.md) and the in-app About screen (AndroidX, Compose, Kotlin, kotest, JetPref, and others).
