# Terminal and developer input (Phase 4)

Status: **core logic JVM-tested; wiring compiled but never run; no UI; nothing verified in Termux/SSH.** See
`FEATURES.md` for the row-by-row status.

## What upstream did before (verified by reading the code)

`KeyCode.CTRL/ALT/ESCAPE/TAB` exist and `AbstractEditorInstance.sendDownUpKeyEvent(keyCode, metaState)` can send real
key events with Ctrl/Alt/Shift. But `KeyboardManager` had **no handler** for `CTRL`, `ALT`, `ESCAPE` or `TAB` (a Ctrl
tap fell into the "unknown key" branch) and typed characters always went through `commitChar` with no meta state.
So there was no Ctrl+letter path at all; only the editor's own Ctrl+A/Z/Shift+Z for select-all/undo/redo.

## Design

| Piece | Package | Pure Kotlin |
|---|---|---|
| `TerminalKeyMapper` — (key, `Modifiers`) → `TerminalAction.Key(keyCode, metaState)` or `Text` | `ime.terminal` | yes |
| `ModifierStateMachine` — Ctrl/Alt: tap = next key only, quick second tap = locked, tap on locked = off, hold + key = chord (does not arm afterwards), lock key, `reset()` | `ime.terminal` | yes, time injected |
| `AndroidKeys` — copy of the `KeyEvent` constants used; `AndroidKeyConstantsTest` compares every value with `android.view.KeyEvent` | `ime.terminal` | yes |
| `DeveloperRows`, `LanguageProfile`, `DevKey` — terminal row and per-language symbol rows (Shell, Python, JS/TS, Kotlin/Java, JSON, YAML, Markdown) | `ime.developer` | yes |
| Hook in `KeyboardManager` (`terminalModifiers`, `sendTerminalChar`, `sendTerminalSpecial`) | `ime.keyboard` | no (thin) |

## Mapping rules

* With no Ctrl/Alt active, typed characters take the **unchanged upstream text path**.
* With Ctrl and/or Alt active, a character that has a key is sent as a down+up `KeyEvent` with
  `META_CTRL_ON|META_CTRL_LEFT_ON` / `META_ALT_ON|META_ALT_LEFT_ON` (same flags as the editor's `meta()`), never as the
  text "Ctrl+C". Uppercase letters and shifted symbols add `META_SHIFT_*`.
* Table: `a–z`, `0–9`, space, and US-layout punctuation (`` ` - = [ ] \ ; ' , . / `` plus their shifted forms
  `~ _ + { } | : " < > ?` and `!@#$%^&*()`). **Assumption:** US QWERTY positions (e.g. `@` = Shift+2).
* Anything without an entry (non-ASCII, emoji, control characters, multi-character strings) is committed as plain text
  and the modifier does not apply to it. One-shot modifiers are only spent when a key event was actually sent.
* `Esc`, `Tab` (+Shift = Shift+Tab), Enter, Backspace, Delete, arrows, Home/End, PgUp/PgDn, Insert, F1–F12 map to their
  `KeyEvent` codes with the active meta state.
* One-shot modifiers are spent after one key; locked ones persist; **everything is reset when the focused editor
  changes** (`activeInfoFlow`), so a lock cannot leak into another app. (Best effort: a `StateFlow` does not re-emit an
  identical value, so re-focusing the very same field does not reset.)

## Privacy

The hook never logs or stores what is typed; it only forwards key codes. Modifiers armed in a password field produce key
events, not captured text; the clipboard and capture guards from Phase 3 are unaffected.

## Not done / not verified

* **No UI.** No layout contains a Ctrl/Alt/Esc/Tab key and nothing displays the developer rows, so a user cannot reach
  any of this yet. The next step is the row UI plus an armed/locked indicator (needs observable state on top of the
  state machine).
* Ctrl/Alt do not yet apply to the existing arrow/Home/End/Delete keys (upstream's `handleArrow` only knows Shift).
* How Termux, ConnectBot, SSH clients, vim or tmux interpret these events has **not** been tested. Device checklist
  (`TESTING.md`): Esc, Tab, Ctrl+C/D/L/Z/A/E/R/W/U/K, Alt+key, arrows, a locked Ctrl across several keys, a lock
  surviving an app switch (it should not).
* Bracket pairs are explicit keys (`()`, `{}`, …) in the row models; nothing auto-closes brackets.
