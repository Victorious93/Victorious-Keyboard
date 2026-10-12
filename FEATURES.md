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

Developer rows, symbol rows, profiles, bracket pairing, indentation helpers, terminal mode and Ctrl/Alt key
events, command library, snippets and text expansion: ⬜ **not started.** Upstream defines key codes for Ctrl/Alt/Esc/
Tab/arrows but has no verified Ctrl+letter path (`AUDIT.md`).

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
| Debug APK builds; 300+ JVM unit tests pass | ✅ (`TESTING.md` for exact numbers) |
| CI workflow (build + unit tests + debug APK artifact) | ✅ written; **never run** (needs GitHub Actions) |
| Dependency checksum verification | ✅ |
| Lint | ⬜ fails on 52 inherited errors (`AUDIT.md` §8) |
| Instrumented / device tests, performance numbers | ⬜ |
| Signed release, release notes, store listing | ⬜ (no signing material exists; none is committed) |
