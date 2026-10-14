<div align="center">

<img src="docs/branding/logo.png" alt="Victorious Keyboard logo" width="240" height="240">

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
(Apache-2.0, based on upstream tag `v0.6.0-alpha02`). It keeps FlorisBoard's typing engine and layouts. The default look is Victorious Neon: navy `#010619`, amber `#FFC107`, and hot pink, with rounder keys, a gap between them, and a soft glow. A light variant is the day theme. The older FlorisBoard themes stay selectable. Settings are grouped under Look, Typing, Tools, and About. It adds:

* **Keyboard colors** — Settings → Look → Keyboard colors. Background, key, key text, accent, highlight, and suggestion pills, with presets and a reset. The choice is saved and painted over Victorious Neon.
* **Word prediction** — current word, next word, and a close correction from the bundled dictionaries. *Learn from my typing* is off until you turn it on.
* **Clipboard manager** — up to 500 items with search, pinning (pinned items are kept), filters, and AES-256-GCM encrypted storage (Android Keystore–wrapped key).
* **Developer row and operating modes** — off until you enable it. Esc, Tab, Ctrl, Alt and arrows; Standard / Developer / Termux modes with optional auto-detect for Termux and ConnectBot.
* **Snippets** — reusable text with templates (`{{selection}}`, `{{clipboard}}`, date/time), an in-keyboard picker, and optional expansion on Space (Enter and Tab can be turned on separately).
* **Dictionary packs** — five bundled word lists for technical vocabulary (install/remove on your choice).

It is not affiliated with or endorsed by the FlorisBoard project. The repository retains upstream's full Git history so per-file authorship is preserved. Design goal: **nothing leaves the device** — no analytics, no ads, and no `INTERNET` permission.

## Features

Status key: ✅ **tested** (JVM unit tests) · 🟦 **built, not run** on a device · 🔹 **inherited** from FlorisBoard · ⬜ **planned**

### Look

Settings → Look holds Languages, Theme, Keyboard colors, and Keyboard.

Victorious Neon is the default night theme and Neon Light is the day theme. Keys are rounded, spaced, and edged in the accent color. The suggestion bar, key popup, clipboard strip, developer row, and emoji panel use the same palette. Letter keys scale down slightly while held. This has been compiled and drawn from the stylesheet. It has not been shown on a device.

Keyboard colors (Settings → Look → Keyboard colors) let you recolor that Neon theme without the Snygg theme editor. You can set the background, key color, key text, accent and glow, highlight (space bar and emphasis), and suggestion pills. A hue and saturation wheel plus a hex field edits one color at a time, and the keyboard preview on the screen updates immediately. Presets: Neon, Cyber blue, Matrix, Purple, Red, and Mono. Reset to default restores Neon. Text that would be hard to read is replaced with a dark or light ink. The choice is stored in preferences and applied again on the next start, including popups, the clipboard strip, the developer row, and the emoji panel. It applies while Victorious Neon or Neon Light is the selected keyboard theme. Another theme is left alone. The screen is compiled and not yet run on a device.

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

The developer row is off until you turn it on (Settings → Keyboard → Developer mode). It adds Esc, Tab, Ctrl, Alt, arrows, Home/End/PgUp/PgDn. Keys send real key events (not literal `Ctrl+C` text); F1–F12 supported. Ctrl/Alt: tap, double-tap lock, or hold. Modes: Standard, Developer, Termux; optional auto-detect for Termux and ConnectBot. Termux/SSH/vim/tmux behaviour and Claude Code / Kali / NetHunter modes are **not verified** on device yet. The row itself is compiled and has not been rendered on a device.

Details: [TERMINAL.md](TERMINAL.md), [docs/DEVELOPER_KEYBOARD.md](docs/DEVELOPER_KEYBOARD.md).

### Snippets

Template language: `{{selection}}`, `{{cursor}}`, `{{clipboard}}`, `{{date}}`, `{{time}}`, `{{datetime}}` (optional formats) — ✅ renderer. Space expansion is opt-in and never in password fields (✅ rules · 🟦 wiring). Enter and Tab expansion is each off until you turn it on; a key that matches nothing still does its normal action (✅ rules · 🟦 wiring). Library of up to 500 snippets with unique abbreviations and versioned JSON import/export (✅ logic · 🟦 settings screen). In-keyboard picker: quick action, panel, insert at the cursor (✅ insertion rules · 🟦 panel). Typed search in the picker and encrypted export are ⬜. Shell/exec/env templates are **not** implemented (placeholders stay literal).

Details: [SNIPPETS.md](SNIPPETS.md).

### Dictionary packs

Five bundled word lists: Kali tools, NetHunter, AI terms, slash commands, Termux. Strict zip reader; non-destructive install/remove. Lists were written from memory and are not yet verified against official sources.

Word prediction is on by default and reads those lists in the suggestion bar: the word you are typing, a next-word guess, and a one-edit correction that can commit on space. *Learn from my typing* is a separate switch and **off by default**. While it is off, nothing you type is stored and only the static lists are used. While it is on, new words and the word before them stay in `noBackupFilesDir/prediction/learned-words.json` on this device. Passwords, incognito, and fields that disallow personalized learning are not stored. *Clear learned words* asks before it deletes that file. Next-word without learning ranks dictionary frequency, because the packs have no sentence pairs. `spell()` is still a stub. Gesture typing is not done. The suggestion wiring has not been run on a device.

Details: [DICTIONARY_PACKS.md](DICTIONARY_PACKS.md).

### Planned (not started or incomplete)

* Gesture typing (word prediction itself is in: current word, next word, and a close correction; learning is opt-in)
* Further theme presets beyond the color-picker set (a separate AMOLED keyboard theme, Terminal Green) and a responsive/tablet layout (Phase 8). Victorious Neon, Neon Light, and Keyboard colors are already in.
* AI workspace: prompt library, local transforms, optional provider adapters (Phase 7; network only after you enable a provider)
* Voice typing via Android's recogniser

## Screenshots

These pictures are drawn from the shipped theme colors and the keyboard-colors screen layout. They are not photographs of the app on a device. The glow is a little stronger than the drop shadow the keyboard draws.

| Floris Night and Victorious Neon | Neon | Neon Light |
|---|---|---|
| <img src="docs/screenshots/keyboard-before-after.png" alt="Floris Night beside Victorious Neon" width="420"> | <img src="docs/screenshots/keyboard-neon.png" alt="Victorious Neon keyboard" width="280"> | <img src="docs/screenshots/keyboard-neon-light.png" alt="Victorious Neon light keyboard" width="280"> |

| Settings, before and after | Keyboard colors | Color wheel |
|---|---|---|
| <img src="docs/screenshots/settings-before-after.png" alt="Settings home, previous green list and grouped navy and light schemes" width="420"> | <img src="docs/screenshots/keyboard-colors.png" alt="Keyboard colors screen with preview, presets, and color rows" width="280"> | <img src="docs/screenshots/keyboard-color-wheel.png" alt="Hue and saturation wheel with a hex field" width="280"> |

| Cyber blue | Matrix | Purple |
|---|---|---|
| <img src="docs/screenshots/keyboard-preset-cyber.png" alt="Cyber blue keyboard preset" width="280"> | <img src="docs/screenshots/keyboard-preset-matrix.png" alt="Matrix green keyboard preset" width="280"> | <img src="docs/screenshots/keyboard-preset-purple.png" alt="Purple keyboard preset" width="280"> |

Still waiting on a device: the developer row with Ctrl locked, clipboard history with a pinned item, and the snippet picker.

## Status

| Phase | Scope | Status |
|---|---|---|
| 0 | Audit and baseline | ✅ |
| 1 | Project foundation, variants, CI | ✅ builds; unit tests pass |
| 2 | Stable keyboard engine | ⬜ needs a device |
| 3 | Advanced clipboard | 🟡 core tested; storage/UI compiled, not run |
| 4 | Developer and terminal modes | 🟡 core tested; wiring/row UI compiled, not run |
| 5 | Snippets and productivity | 🟡 core tested; Android shell compiled, not run |
| 6 | Word prediction | 🟡 predictor tested; suggestion wiring compiled, not run. Gesture typing not started |
| 7, 9–10 | AI workspace, security/perf, release | ⬜ |
| 8 | Themes | 🟡 Neon default and keyboard colors compiled, not run on a device; responsive layout not started |

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
| Data | On-device only; clipboard, snippets, and learned words stay out of Android auto-backup |
| Distribution | Debug pre-release on GitHub Releases. Signed with the Android debug key, not a store build |

Privacy: [PRIVACY.md](PRIVACY.md).

## Requirements

* **JDK 21** (matches CI)
* Android SDK: `platforms;android-36`, `build-tools;35.0.0`
* Optional: connected device or emulator for sideload testing

## Download

The newest debug build is the latest pre-release on [GitHub Releases](https://github.com/Victorious93/Victorious-Keyboard/releases). It is signed with the Android debug key. It includes Victorious Neon, word prediction, and keyboard colors. It has not been installed or exercised on a device from this repository. Install it with `adb install`, then enable Victorious Keyboard under Android *Language & input*.

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
