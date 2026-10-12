# Developer keyboard and operating modes

Status: **modes core JVM-tested; settings, auto-detect and developer row compiled but never run or rendered on a
device.** See `TERMINAL.md` for key mapping, `SNIPPETS.md` for snippets, `docs/ARCHITECTURE_ASSESSMENT.md` for the plan.

## The six modes in the brief, and what exists

| Mode | Status | What it does today |
|---|---|---|
| Standard | **available** | The normal keyboard. The developer row follows its own preference (off by default). Unchanged from upstream behaviour |
| Developer | **available** | Developer row always shown, with your chosen symbol profile and terminal-keys setting |
| Termux / terminal | **available** | Developer row always shown with terminal keys and Shell symbols. It is a *preset of existing features*, not a Termux integration |
| Claude Code | planned | Nothing yet (needs the command palette). Not selectable |
| Kali Linux | planned | Nothing yet (needs the command catalog). Not selectable |
| NetHunter AI | planned | Nothing yet (needs the AI assistant and a secure execution design). Not selectable |

Planned modes are deliberately **not offered**: a mode that does nothing would be a placeholder toggle. A stored value
for a mode that is not available falls back to Standard.

## How it is built

* `ime.mode.OperatingMode`: the enum, its availability, and `ModePresets` that turn a mode plus the user's
  developer-row preferences into the effective row (visible, profile, terminal keys). Pure Kotlin.
* `ime.mode.ModeSuggester` / `ActiveMode`: pick the mode for the current app. Pure Kotlin.
* `KeyboardManager.activeMode` (a `StateFlow`) combines the default-mode preference, the auto-detect preference and the
  current editor's package name. Switching is a state change: the keyboard is not restarted and typed text is untouched.
* One IME, one set of preferences: `modes__default`, `modes__auto_detect` (new) next to the existing `devrow__*` keys.
  The enum names are persisted, so they must never be renamed (a test guards this).

Settings: Settings → Keyboard → **Developer mode**: *Keyboard mode*, *Switch mode in terminal apps*, *Developer row*,
*Symbols for*, *Terminal keys*.

## Automatic mode for terminal apps (off by default)

When enabled, typing in Termux (`com.termux`) or ConnectBot (`org.connectbot`) uses Termux mode; leaving the app returns
to your default mode. It uses only `EditorInfo.packageName`, which Android passes to every input method for the field
being edited. **No accessibility service, no usage access, no monitoring.** Matching is exact (no prefixes), and only
packages whose identity is certain are built in. User-defined app-to-mode mappings exist in the core (`overrides`) but
have no UI yet.

## Advanced settings

Settings → Keyboard → Developer mode → **Advanced developer settings** (compiled, never run or rendered on a device):

| Group | Options |
|---|---|
| Developer row | Row size (Compact 80 % / Normal / Large 125 % of the Smartbar height); navigation keys on/off (arrows, Home, End, PgUp, PgDn) separately from Esc/Tab/Ctrl/Alt; language symbols on/off; **your own symbol keys**; whether they are added to or replace the language symbols |
| Timing and feedback | Hold delay before repeating (150-800 ms, default 350); repeat interval (20-200 ms, default 60); double-tap time to lock Ctrl/Alt (200-800 ms, default 400); vibration on developer keys |
| Snippets | Date format for `{{date}}` (ISO, DD/MM/YYYY, MM/DD/YYYY, DD.MM.YYYY); time format for `{{time}}` (24 h, 24 h with seconds, 12 h); keep the typed space after expanding; expand in Termux mode (off by default) |
| Keyboard modes | **Your own app-to-mode mappings** used by automatic mode switching |
| Reset | Puts everything on this screen back to its default; the basic Developer mode settings are untouched |

Free-text settings are validated live with the same pure parsers the keyboard uses, and are untrusted input:

* **Own symbol keys:** whitespace-separated tokens, 1-8 code points each, at most 40 keys, no control characters,
  duplicates dropped, anything else listed as "not accepted" instead of being truncated or guessed. `()` `[]` `{}` `<>`
  and quote pairs place the caret between the halves. Each key only inserts its text.
* **App-to-mode mappings:** one `package=MODE` per line (`#` comments), MODE one of the selectable modes, package names
  must look like Android package names, at most 50, bad lines listed and ignored.
* Stored timing values are clamped on use, so a hand-edited preference cannot make the row unusable.
* Termux mode stays a fixed preset (row, terminal keys, navigation keys, Shell symbols); your own keys are still added.

## Not done

* Quick switching between modes from the keyboard itself (today the default mode is changed in Settings).
* Per-mode autocorrect/prediction off, per-mode snippet-expansion defaults, bracket auto-pairing, indentation helpers.
* The three planned modes and everything they need.
* Device validation of all of it.
