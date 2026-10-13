<div align="center">

<img src="docs/branding/logo.png" alt="Victorious Keyboard logo" width="128">

# Victorious Keyboard

**An open-source Android keyboard for everyday typing, developers and terminal users, with a clipboard manager,
snippets and offline tools.**

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Kotlin 2.3](https://img.shields.io/badge/Kotlin-2.3-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack_Compose-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/compose)
![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B_(API_26)-3DDC84?logo=android&logoColor=white)
[![Based on FlorisBoard](https://img.shields.io/badge/based_on-FlorisBoard_v0.6.0--alpha02-green)](https://github.com/florisboard/florisboard)
![Status: early development](https://img.shields.io/badge/status-early_development-orange)

[Overview](#overview) · [Features](#features) · [Status](#status) · [Tech stack](#tech-stack) · [Build](#build-and-install) ·
[Structure](#project-structure) · [Contributing](#contributing) · [Licence](#licence)

</div>

> [!WARNING]
> **Early development.** The core logic of the Victorious additions is unit-tested on the JVM. The Android screens and
> wiring compile, but **none of the additions has been run on an Android device yet.** Do not use this as your only
> keyboard. Details: [Status](#status).

## Table of contents

* [Overview](#overview)
* [Features](#features)
* [Status](#status)
* [APK at a glance](#apk-at-a-glance)
* [Tech stack](#tech-stack)
* [Build and install](#build-and-install)
* [Project structure](#project-structure)
* [Logo and launcher icons](#logo-and-launcher-icons)
* [Documentation](#documentation)
* [Contributing](#contributing)
* [Licence](#licence)
* [Acknowledgements](#acknowledgements)

## Overview

Victorious Keyboard is a modified distribution of [FlorisBoard](https://github.com/florisboard/florisboard)
(Apache-2.0, based on upstream tag `v0.6.0-alpha02`). It keeps FlorisBoard's typing engine, layouts and themes and adds:

* **Clipboard manager**: a 500-item history with search, pinning, filters and encrypted storage.
* **Developer row and operating modes**: Esc, Tab, Ctrl, Alt and arrow keys, with a Termux preset and optional
  auto-detection for Termux and ConnectBot.
* **Snippets**: reusable text with templates (`{{selection}}`, `{{clipboard}}`, date and time), an in-keyboard picker,
  and text expansion on the space key (Enter and Tab can be turned on separately).
* **Dictionary packs**: five bundled word lists for technical vocabulary, installed and removed on your choice.

It is not affiliated with or endorsed by the FlorisBoard project. The repository contains upstream's full Git history
(the first commit is Patrick Goldinger's initial FlorisBoard commit from April 2020), so per-file authorship is
preserved. The design goal is that nothing leaves the device: there is no
analytics, no ads and no `INTERNET` permission.

## Features

Status key: ✅ **tested**, unit tests on the JVM · 🟦 **built, not run** on a device · 🔹 **inherited** from FlorisBoard
· ⬜ **planned**

### Typing (inherited)

| Feature | Status |
|---|---|
| QWERTY, numeric and symbol layouts, emoji, long-press, key repeat, haptics, sounds, themes, one-handed mode, number row, shift and caps lock | 🔹 |
| Physical keyboard support | 🔹 (incomplete upstream) |
| Glide typing | 🔹 classifier present; needs a dictionary; unverified |
| Floating or split keyboard | ⬜ |

### Clipboard manager

| Feature | Status |
|---|---|
| 500-item history; pinned items are never evicted | ✅ policy · 🟦 wired into the manager |
| Encrypted at rest, AES-256-GCM, key wrapped by the Android Keystore | ✅ cipher · 🟦 Keystore and Room storage |
| Search, filters, tags, favourites, editing, multi-select, share | ✅ logic · 🟦 screens |
| Text that looks like a password, API key or card number is not saved by default | ✅ |
| Copy and Cut in the keyboard refuse to work in password fields | ✅ detection · 🟦 call site |
| Export and import as JSON, ZIP, encrypted archive, Markdown or text, with preview and merge, replace or skip | ✅ logic · 🟦 file-picker screens |
| Images and URI clips in backups | ⛔ cannot be restored safely; skipped and counted |

Limits: Android does not give keyboards the source app of a copied item. Details: [CLIPBOARD.md](CLIPBOARD.md).

### Developer and terminal

| Feature | Status |
|---|---|
| Developer row (off by default): Esc, Tab, Ctrl, Alt, arrows, Home, End, PgUp, PgDn, and language symbol keys | ✅ key mapping · 🟦 row UI |
| Keys send real key events, never literal text such as `Ctrl+C`; F1–F12 supported | ✅ mapping table |
| Ctrl and Alt: tap for one key, double-tap to lock, or hold | ✅ state machine · 🟦 keyboard wiring |
| Operating modes: Standard, Developer, Termux; optional auto-detect of Termux and ConnectBot | ✅ core · 🟦 settings and wiring |
| Advanced developer settings: row size, repeat and lock timing, vibration, own symbol keys, app-to-mode mapping | ✅ parsers · 🟦 screen |
| Termux, SSH, vim and tmux behaviour | ⬜ not verified; device testing required |
| Claude Code, Kali and NetHunter modes | ⬜ not selectable until their features exist |

Details and limits: [TERMINAL.md](TERMINAL.md) and [docs/DEVELOPER_KEYBOARD.md](docs/DEVELOPER_KEYBOARD.md).

### Snippets

| Feature | Status |
|---|---|
| Template language: `{{selection}}`, `{{cursor}}`, `{{clipboard}}`, `{{date}}`, `{{time}}`, `{{datetime}}` with optional formats | ✅ renderer |
| Expansion on the space key: opt-in, never in password fields | ✅ rules · 🟦 wiring |
| Expansion on Enter and Tab: each off until you turn it on; a key that matches nothing still does its normal action | ✅ rules · 🟦 wiring |
| Library of up to 500 snippets with unique abbreviations; versioned JSON import and export (non-destructive importer) | ✅ logic · 🟦 settings screen |
| In-keyboard picker: quick action, panel, insert at cursor | ✅ insertion rules · 🟦 panel |
| Typed search in the picker, encrypted export | ⬜ |

Shell and environment templates (`{{shell:…}}`, `{{exec:…}}`, `{{env:…}}`) are not implemented; they stay literal text.
Details: [SNIPPETS.md](SNIPPETS.md).

### Dictionary packs

Five bundled word lists (Kali tools, NetHunter, AI terms, slash commands, Termux), with a strict zip reader and a
non-destructive install and remove screen. The lists were written from memory and are not yet verified against
official sources. Words reach the system spell checker only: **there are no word suggestions yet.**
Details: [DICTIONARY_PACKS.md](DICTIONARY_PACKS.md).

### Planned

* Word suggestions, autocorrect, next-word prediction and a personal dictionary (upstream's `suggest()` returns nothing)
* Victorious theme presets and a responsive layout pass (Phase 8)
* AI workspace: prompt library, local transforms and optional provider adapters. Off by default; the app gets network
  access only after you enable a provider (Phase 7, not started)
* Voice typing through Android's own recogniser (not started)

## Status

Status per phase. Full detail: [ROADMAP.md](ROADMAP.md) and [FEATURES.md](FEATURES.md).

| Phase | Scope | Status |
|---|---|---|
| 0 | Audit and baseline | ✅ |
| 1 | Project foundation: application ID, build variants, dependency verification, CI | ✅ builds; unit tests pass |
| 2 | Stable keyboard engine | ⬜ needs a device |
| 3 | Advanced clipboard | 🟡 core tested; storage and UI compiled, not run |
| 4 | Developer and terminal modes | 🟡 core tested; wiring and row UI compiled, not run |
| 5 | Snippets and productivity | 🟡 core tested; Android shell compiled, not run |
| 6 | Prediction and gesture typing | ⬜ |
| 7 | AI workspace | ⬜ |
| 8 | Themes and responsive UI | ⬜ |
| 9 | Security and performance | ⬜ |
| 10 | Release engineering | ⬜ no signed release exists |

No screenshots are included yet, because the app has not been run on a device.

## APK at a glance

| | |
|---|---|
| Application ID | `com.victorious.keyboard` (debug builds: `com.victorious.keyboard.debug`, labelled "Victorious Keyboard Debug") |
| Version | `0.1.0-alpha01`, version code `120` |
| Android | 8.0 and newer (`minSdk` 26); compiled and targeted at API 36 |
| Permissions | `VIBRATE` (haptic key feedback), `POST_NOTIFICATIONS` (requested in the setup flow) |
| Internet | **None.** The app declares no `INTERNET` permission |
| Analytics, ads, telemetry | None |
| Where data lives | On the device only. Clipboard history and snippets are kept out of Android auto-backup |
| Languages | English and the inherited translations; some strings fall back to English after rebranding |
| Distribution | No release, store listing or signed APK exists yet. You build it yourself (below) |

Privacy statement, item by item: [PRIVACY.md](PRIVACY.md).

## Tech stack

| Area | What is used |
|---|---|
| Language | Kotlin 2.3 (JVM target 11), with KSP and the Kotlin Compose and serialization plugins |
| UI | Jetpack Compose (BOM 2025.12.01), `androidx.navigation`, AboutLibraries; settings via JetPref (`patrickgold-jetpref`) |
| Keyboard theming | `:lib:snygg`, upstream's own styling engine, and `:lib:color` |
| Storage and data | AndroidX Room (clipboard database), kotlinx-serialization JSON, JCE cryptography (AES-256-GCM) for the clipboard vault and encrypted backups |
| Build | Gradle (wrapper 9.2.0), Android Gradle Plugin 8.12.3, version catalog in `gradle/libs.versions.toml`, dependency verification |
| Tests | JUnit platform with kotest; logic is kept in pure Kotlin so it runs on the JVM |
| CI | GitHub Actions (`.github/workflows/android.yml`, JDK 21) |

Versions are taken from `gradle/libs.versions.toml` and `gradle.properties`.

## Build and install

Requirements: JDK 21 (used by this repository's CI workflow and in local verification), and the Android SDK packages
`platforms;android-36` and `build-tools;35.0.0`. Full steps, verification notes and known environment issues are in [BUILD.md](BUILD.md).

```sh
git clone https://github.com/Victorious93/Victorious-Keyboard.git
cd Victorious-Keyboard
echo "sdk.dir=$ANDROID_HOME" > local.properties   # git-ignored; point this at your SDK

./gradlew assembleDebug                          # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest :lib:kotlin:test     # JVM unit tests
```

To try it on a phone, install the debug APK with `adb install app/build/outputs/apk/debug/app-debug.apk`, then enable
Victorious Keyboard in Android's *Language & input* settings and select it as the active keyboard. Nothing in this
repository has been installed or run on a device.

## Project structure

```text
app/                         The Android application (single module)
  src/main/kotlin/dev/patrickgold/florisboard/
    FlorisImeService.kt      The input method service
    app/                     Settings and setup screens
    ime/                     Keyboard features, one package each:
      clipboard/             Clipboard history, encrypted vault, backup and restore
      developer/, terminal/  Developer row, key mapping, Ctrl/Alt modifier state machine
      mode/                  Operating modes (Standard, Developer, Termux)
      snippets/              Snippet templates, library, expansion and picker
      dictionary/            Dictionaries and dictionary packs
      keyboard/, text/, ...  Inherited FlorisBoard typing engine
  src/test/                  JVM unit tests
lib/                         android, color, compose, kotlin, snygg (inherited helper libraries)
dictionary-packs/            Sources of the five bundled word lists
tools/, utils/               Pack builder and upstream utility scripts
docs/                        Branding assets, developer-keyboard notes, upstream documents
```

The Kotlin namespace stays `dev.patrickgold.florisboard` on purpose, to keep upstream merges manageable; the product
identity is the application ID and resources. New features follow the rule *pure-Kotlin core plus a thin Android
shell*, described in [ARCHITECTURE.md](ARCHITECTURE.md).

## Logo and launcher icons

The logo is in [`docs/branding/logo.png`](docs/branding/logo.png). The launcher icons are generated from it by
[`docs/branding/make_icons.py`](docs/branding/make_icons.py) (Pillow required), which writes the adaptive-icon
foreground and monochrome layers for every density, plus a 512 px Play Store icon. Background colour: `#11151C`.
The logo is 117 px, so the larger icon outputs are upscaled; see [docs/branding/README.md](docs/branding/README.md).

## Documentation

| Document | What it covers |
|---|---|
| [FEATURES.md](FEATURES.md) | Every feature with its status in one table |
| [ROADMAP.md](ROADMAP.md) | Phases, the work queue, and the honest status of each phase |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Code structure: pure-Kotlin cores and thin Android shells |
| [BUILD.md](BUILD.md) | Building, dependency verification, environment issues |
| [TESTING.md](TESTING.md) | Test counts, what is and is not verified, and the device checklist |
| [AUDIT.md](AUDIT.md) | What was inspected, why this upstream base, baseline results, risks |
| [CLIPBOARD.md](CLIPBOARD.md) · [BACKUP_FORMAT.md](BACKUP_FORMAT.md) | Clipboard design, limits and the encrypted backup format |
| [SNIPPETS.md](SNIPPETS.md) · [DICTIONARY_PACKS.md](DICTIONARY_PACKS.md) | Snippet template language and dictionary packs |
| [TERMINAL.md](TERMINAL.md) · [docs/DEVELOPER_KEYBOARD.md](docs/DEVELOPER_KEYBOARD.md) | Developer row, operating modes and their limits |
| [PRIVACY.md](PRIVACY.md) · [SECURITY.md](SECURITY.md) | What the app does with data, and how to report a vulnerability |
| [CHANGELOG.md](CHANGELOG.md) | Notable changes |
| [CONTRIBUTING.md](CONTRIBUTING.md) · [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) | How to contribute |
| [LICENSES.md](LICENSES.md) · [docs/upstream/](docs/upstream/) | Licences and attribution; upstream's documents, kept verbatim |

## Contributing

Issues and pull requests are welcome. This is a security-sensitive app, so correctness and honesty about what was tested
matter more than speed. Read [CONTRIBUTING.md](CONTRIBUTING.md) first; the short version:

* One logical change per pull request, with the build green (`./gradlew assembleDebug testDebugUnitTest`).
* Put logic in pure Kotlin and unit-test it, including failure paths.
* Never log or transmit typed text, clipboard contents or snippets.
* Say in the PR what you tested and what you could not (for example, no device).

Please follow the [Code of Conduct](CODE_OF_CONDUCT.md). Report vulnerabilities as described in [SECURITY.md](SECURITY.md).

## Licence

Victorious Keyboard is licensed under the [Apache License 2.0](LICENSE); see also [LICENSES.md](LICENSES.md).
Upstream copyright notices are preserved, and the app's About screen credits FlorisBoard.

## Acknowledgements

* **[FlorisBoard](https://github.com/florisboard/florisboard)** by Patrick Goldinger and The FlorisBoard Contributors
  (Apache-2.0). This project is built on its `v0.6.0-alpha02` release (commit `0ea0481c`). The typing engine, layouts,
  themes, Snygg styling engine and settings framework are theirs. The FlorisBoard name and icon belong to upstream.
* The authors of the libraries listed in [LICENSES.md](LICENSES.md) and in the app's About screen, including AndroidX,
  Jetpack Compose, Kotlin, kotest and JetPref.
