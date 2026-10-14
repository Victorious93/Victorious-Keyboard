# Feature status

The single place to see what exists. **No row is "device-validated"**: the reference environment has no Android
device or emulator, so nothing has been installed or run on Android. "Inherited" = comes with upstream FlorisBoard
`v0.6.0-alpha02` and was not changed or re-verified by this project.

Legend: ✅ implemented and JVM-tested · 🟦 implemented, compiled, **not run** · 🔹 inherited · ⬜ not started · ⛔ not
possible as specified (reason given).

## Core keyboard

| Feature | Status | Note |
|---|---|---|
| QWERTY/numeric/symbol layouts, emoji, long-press, key repeat, haptics, sounds, themes, one-handed, number row, shift/caps | 🔹 | upstream engine |
| Physical keyboard support | 🔹 | upstream lists it as incomplete |
| Floating keyboard | ⬜ | not in upstream at this version |
| Split keyboard / tablet layouts | ⬜ | |
| Required theme presets (Victorious Dark, AMOLED, Terminal Green, …) | ⬜ | Phase 8 |

## Clipboard (Phase 3) — see `CLIPBOARD.md`

| Feature | Status |
|---|---|
| 500-entry cap, pinned never evicted, duplicate modes | ✅ policy · 🟦 wired into the manager |
| Encrypted storage at rest (AES-256-GCM, Keystore-wrapped key) | ✅ cipher · 🟦 Keystore/Room |
| Search (in the Clipboard manager screen), filters, tags, favourites, categories, editing, multi-select, share | ✅ logic · 🟦 UI |
| Search typed inside the keyboard panel | ⬜ (see CLIPBOARD.md) |
| Credential detection (not saved by default) | ✅ |
| Password-field guard on Copy/Cut | ✅ detection · 🟦 call site |
| Export JSON / ZIP / encrypted / Markdown / text; import with preview, merge/replace/skip, consent, atomic apply | ✅ logic · 🟦 SAF screens |
| Backup library (user-chosen files, minimal metadata, availability check, delete) | ✅ model · 🟦 UI |
| Images / URI clips in backups | ⛔ not safely restorable; skipped and counted |
| Source-app metadata | ⛔ Android does not provide it to keyboards |

## Developer / terminal / productivity (Phases 4–5)

Details, mapping table and limits: `TERMINAL.md`. The developer row is **off by default** (Settings → Keyboard →
Developer mode); with it off the keyboard behaves like upstream. The UI has not been seen on a screen.

| Feature | Status |
|---|---|
| Key + Ctrl/Alt/Shift → real `KeyEvent` (never literal "Ctrl+C" text), special keys, F1–F12 | ✅ pure core, JVM-tested |
| Sticky / double-tap-locked / held Ctrl and Alt state machine | ✅ pure core, JVM-tested |
| Developer symbol rows for 7 language profiles, terminal row model | ✅ models, JVM-tested · ⬜ no UI |
| `KeyboardManager` handles `CTRL/ALT/CTRL_LOCK/ALT_LOCK/ESCAPE/TAB` and sends typed characters as key events while a modifier is active | 🟦 compiled, **not run** |
| Developer row UI (Esc/Tab/Ctrl/Alt/arrows/Home/End/PgUp/PgDn + profile symbols), armed/locked indicator, settings toggle + profile picker | 🟦 compiled, **never rendered or run**; off by default |
| Advanced developer settings: row size, navigation/symbol toggles, own symbol keys, key repeat and Ctrl/Alt lock timing, vibration, snippet date/time formats, own app-to-mode mappings, reset | ✅ parsers/rows JVM-tested · 🟦 screen and wiring compiled, **never run or rendered** |
| Per-app automatic profile (language symbols per app) | ⬜ |
| Ctrl/Alt applied to the keyboard's arrow and line-start/end keys | 🟦 compiled, not run (Delete/Backspace not covered) |
| Bracket pairing as automatic behaviour, indentation helpers | ⬜ (pair *keys* exist as models; nothing auto-closes) |
| Termux / SSH / vim / tmux behaviour | ⬜ **device-test only; not verified** |
| Operating modes: Standard / Developer / Termux (preset of the developer row), default-mode setting, opt-in auto-detect of Termux and ConnectBot from `EditorInfo.packageName` | ✅ core, JVM-tested · 🟦 settings and wiring compiled, **never run** (`docs/DEVELOPER_KEYBOARD.md`) |
| Claude Code, Kali and NetHunter AI modes | ⬜ planned, deliberately not selectable until their features exist |
| Snippet template language, opt-in expansion rules, library, JSON import/export, safe file store | ✅ pure core, JVM-tested (`SNIPPETS.md`) |
| Snippets settings screen (add/edit/delete/search/import/export), expansion on the space key (opt-in), store in `noBackupFilesDir` | 🟦 compiled, **never run or rendered** |
| Expansion on Enter and Tab, each opt-in and off by default; a non-match still sends the real key; never in password fields | ✅ rules JVM-tested · 🟦 wiring compiled, **never run** |
| In-keyboard snippet picker (smartbar quick action, panel with tag chips, insert at the caret, `{{selection}}`, `{{cursor}}`) | ✅ pure parts JVM-tested (insertion rules, caret placement, list model, preview, migration of the quick action) · 🟦 panel and wiring compiled, **never run or rendered** |
| Typed search in the picker, encrypted export | ⬜ |
| Dictionary packs: 5 bundled word lists (Kali tools, NetHunter, AI terms, slash commands, Termux), strict zip reader, non-destructive install/remove, install screen (`DICTIONARY_PACKS.md`) | ✅ pure parts JVM-tested · 🟦 screen and database wiring compiled, **never run**; lists written from memory, **not verified online**; prediction reads the bundled zips, and installing a pack still only feeds the system spell checker |
| Command library | ⬜

## Prediction and voice (Phase 6)

| Feature | Status |
|---|---|
| Glide typing | 🔹 upstream classifier exists; needs a dictionary; unverified |
| Word suggestions, autocorrect, next-word prediction | 🟡 pure predictor JVM-tested; suggestion-bar wiring and Typing settings compiled, **never run**. `spell()` is still a test stub. Next-word without learning is a frequency ranking (`DICTIONARY_PACKS.md`) |
| Learn from my typing (off by default) and Clear learned words | 🟡 store and gate JVM-tested; file under `noBackupFilesDir/prediction/`; **never run** on a device |
| Voice typing | ⬜ |
| GIF integration | ⬜ |

## AI workspace (Phase 7)

Prompt library, templates, local transformations, optional provider adapters: ⬜ **not started.** The app has **no
`INTERNET` permission**.

## Quality and release

| Item | Status |
|---|---|
| Debug APK builds; 730 JVM unit tests pass | ✅ (`TESTING.md`) |
| CI workflow (build + unit tests + lint + debug APK artifact) | ✅ build, unit tests, `:lib:kotlin:test`, and `lintDebug` are the gate. Artifact upload is non-fatal: a full GitHub storage quota no longer fails the job |
| Dependency checksum verification | ✅ |
| Lint | ✅ the errors in `AUDIT.md` §8 are fixed and `lintDebug` is a CI gate. Warnings do not fail the build |
| Instrumented / device tests, performance numbers | ⬜ |
| Signed release, release notes, store listing | ⬜ (no signing material exists; none is committed) |
