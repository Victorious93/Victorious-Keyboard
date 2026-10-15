# Terminal and developer input (Phase 4)

Status: **core logic JVM-tested; wiring and UI compiled but never run on a device; nothing verified in Termux/SSH.** See
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

## Developer row (UI)

Settings → **Developer & terminal**: *Show terminal key row* (Auto / Always / Never, default Auto), *Terminal keys*
(on/off), *Symbols for* (Shell, Python, JS/TS, Kotlin/Java, JSON, YAML, Markdown). Auto shows the row in known terminal
apps, in `TYPE_NULL` fields, and while Developer or Termux mode is active; in other apps the keyboard is unchanged. The
old *Developer row* switch (`devrow__enabled`) is no longer read. `TerminalRowPolicy.plan` (pure, JVM-tested) is the
single decision for whether the row shows and what it holds; the row and the height that full-height panels reserve for
it both use it. Termux mode forces terminal keys, navigation keys and Shell symbols; other modes use your switches.

When shown, one horizontally scrollable row sits between the Smartbar and the keyboard:
`Esc Tab Ctrl Alt ← ↓ ↑ → Home End PgUp PgDn` then the profile's symbols. It reuses the Smartbar action-key theme
elements, so existing themes style it.

* **Ctrl / Alt:** tap = applies to the next key only (drawn pressed); quick second tap = locked (drawn pressed *and*
  label in CAPITALS, announced as "locked" by TalkBack); tap again = off; hold + tap another key = chord.
* **Keys fire on release**, so scrolling the row never types. **Arrows / PgUp / PgDn repeat** while held (350 ms, then
  every 60 ms). A press that turns into a scroll is cancelled and sends nothing.
* A symbol key under Ctrl/Alt is sent as a key event when it has one (Ctrl+`\` works from the row); pair keys
  (`()`, `{}`, `""`, …) insert both halves and move the cursor between them.
* Ctrl/Alt now also apply to the keyboard's own arrow and line-start/line-end keys.
* The row is hidden while the Smartbar's actions overflow panel, the tools panel, the AI panel or the clip editor is
  open. The tools panel takes over the row's height, so the window does not change size when it opens.
* Advanced developer settings add: row size, separate navigation-key and symbol toggles, your own symbol keys, hold and
  repeat timing, the Ctrl/Alt lock window and vibration (`docs/DEVELOPER_KEYBOARD.md`).

| Piece | Where |
|---|---|
| Decision logic (`DeveloperKeyResolver`), rows, observable `ModifierSnapshot` | `ime.developer`, `ime.terminal` (pure, tested) |
| `DeveloperRowBar` composable | `ime.developer.ui` (shell) |
| `performDeveloperKey`, `terminalModifierState` | `KeyboardManager` |
| Preferences `devrow__enabled`, `devrow__profile`, `devrow__terminal_keys` | `AppPrefs.devrow` |

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

* **The UI has never been rendered.** It compiles and its logic is unit-tested, but I have not seen it on a screen:
  key sizes, the row's height against the keyboard window, scroll-versus-tap behaviour, theming in dark/light and the
  Ctrl "locked" look are all unverified. The extra row makes the keyboard taller; whether the IME window and insets
  adapt correctly is untested.
* Ctrl/Alt do not apply to Delete/Backspace keys (upstream handles those separately).
* The row is English-only and the profile names are not translated.
* How Termux, ConnectBot, SSH clients, vim or tmux interpret these events has **not** been tested. Device checklist
  (`TESTING.md`): Esc, Tab, Ctrl+C/D/L/Z/A/E/R/W/U/K, Alt+key, arrows, a locked Ctrl across several keys, a lock
  surviving an app switch (it should not).
* Bracket pairs are explicit keys (`()`, `{}`, …) in the row models; nothing auto-closes brackets.
