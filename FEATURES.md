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

Details, mapping table and limits: `TERMINAL.md`. **None of this is visible to a user yet**: no layout contains a
Ctrl/Alt/Esc/Tab key and no UI shows the developer rows, so the keyboard behaves exactly like upstream until the
Phase 4 UI exists.

| Feature | Status |
|---|---|
| Key + Ctrl/Alt/Shift → real `KeyEvent` (never literal "Ctrl+C" text), special keys, F1–F12 | ✅ pure core, JVM-tested |
| Sticky / double-tap-locked / held Ctrl and Alt state machine | ✅ pure core, JVM-tested |
| Developer symbol rows for 7 language profiles, terminal row model | ✅ models, JVM-tested · ⬜ no UI |
| `KeyboardManager` handles `CTRL/ALT/CTRL_LOCK/ALT_LOCK/ESCAPE/TAB` and sends typed characters as key events while a modifier is active | 🟦 compiled, **not run** |
| Developer row / terminal mode UI, language-profile picker, per-app auto profile | ⬜ |
| Ctrl/Alt applied to arrow, Home/End, Delete keys that already exist on layouts | ⬜ (existing arrow handling ignores Ctrl/Alt) |
| Bracket pairing as automatic behaviour, indentation helpers | ⬜ (pair *keys* exist as models; nothing auto-closes) |
| Termux / SSH / vim / tmux behaviour | ⬜ **device-test only; not verified** |
| Command library, snippets, text expansion | ⬜ Phase 5

## Prediction and voice (Phase 6)

| Feature | Status |
|---|---|
| Glide typing | 🔹 upstream classifier exists; needs a dictionary; unverified |
| Word suggestions, autocorrect, next-word prediction, personal dictionary | ⬜ upstream `suggest()` returns nothing and `spell()` is a test stub |
| Voice typing | ⬜ |
| GIF integration | ⬜ |

## AI workspace (Phase 7)

Prompt library, templates, local transformations, optional provider adapters: ⬜ **not started.** The app has **no
`INTERNET` permission**.

## Quality and release

| Item | Status |
|---|---|
| Debug APK builds; 394 JVM unit tests pass | ✅ (`TESTING.md`) |
| CI workflow (build + unit tests + debug APK artifact) | ✅ written; **never run** (needs GitHub Actions) |
| Dependency checksum verification | ✅ |
| Lint | ⬜ fails on 52 inherited errors (`AUDIT.md` §8) |
| Instrumented / device tests, performance numbers | ⬜ |
| Signed release, release notes, store listing | ⬜ (no signing material exists; none is committed) |
