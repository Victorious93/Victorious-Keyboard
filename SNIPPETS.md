# Snippets and text expansion (Phase 5)

Status: **pure-Kotlin core JVM-tested; Android shell (store, expansion on space and opt-in Enter/Tab, settings screen with
import/export, in-keyboard picker) compiled but never run or rendered on a device.** Expansion is off by default.

## Using it (as built, unverified on a device)

* Settings → Tools → **Snippets** (a Home entry since the UI pass): add, edit, delete, search, import and export.
  Snippets live in `noBackupFilesDir/snippets/snippets.json` (not in Android auto-backup) and load off the main thread at
  app start. The tools panel's Clipboard tab also opens the in-keyboard picker.
* Snippets → **Expansion**: **Expand snippets while typing** (off by default), **Expansion prefix** (`;` `:` `/` `\`
  `;;` `::`), **Case-sensitive abbreviations**, the Enter and Tab switches below, and **Per keyboard mode**.
* **Per keyboard mode** (Standard, Developer, Termux), each one of: *Space, plus Enter and Tab if on* (follow the
  switches), *Space only*, or *Off*. Defaults: Standard and Developer follow the switches, Termux is Off. A mode rule
  can narrow expansion but never turn it on while the master switch is off. Planned modes use Standard's rule. The
  decision is one pure function, `ModeExpansionPolicy.triggers` (JVM-tested); the keyboard calls it before it reads the
  field. In a terminal, Enter runs the line, so *Space only* is the safe way to turn expansion on in Termux mode: with
  *Follow* and Enter expansion on, the expanded text is sent as soon as you press Enter. The group stays visible but
  greyed out while expansion is off, so the link from Advanced developer settings always lands on it.
* Scope of the Termux rule (code reading, not run on a device): a `TYPE_NULL` field gives the keyboard no text before
  the caret (`EditorContent.Unspecified`), so no abbreviation can match there whatever the rule says. Termux's terminal
  view is believed to declare `TYPE_NULL` (not re-checked against Termux's source), so the rule mainly matters while
  Termux mode is active in an ordinary text field, for example when it was chosen by hand.
* With expansion on, typing `;sig` and then **space** replaces `;sig` with the rendered snippet and keeps the space,
  unless the snippet contains `{{cursor}}`: then the space is consumed and the caret is placed at `{{cursor}}`.
* **Enter** and **Tab** do the same only if you turn on "Also expand on Enter" / "Also expand on Tab" (both off by
  default, under the expansion switch). If nothing matches, Enter and Tab do their normal action and the typed text
  stays. The same is true when the snippet would be empty or too large, in a password-like field, while text is
  selected, or while Shift, Ctrl or Alt is held. "Keep Enter after expanding" and "Keep Tab after expanding" (Advanced
  developer settings, both on by default) re-send that key after a successful expansion; a `{{cursor}}` snippet never does.
* Never in password-like fields, never while text is selected, and a rendering that is too large (> 20 000 characters)
  is not inserted (the typed text is left as it is).
* `{{date}}`, `{{time}}` and `{{datetime}}` follow the date and time format chosen in Advanced developer settings; an
  explicit pattern such as `{{date:yyyy}}` always wins. A pattern that cannot be used is inserted as the text you wrote.
* "Keep the typed space after expanding" (default on) can be switched off.
* `{{clipboard}}` uses the current clipboard text only if it is plain text **and the platform did not mark the clip
  sensitive** (`EXTRA_IS_SENSITIVE`, e.g. a password manager); it is read only if the snippet uses it.
  `{{selection}}` is always empty in abbreviation expansion (expansion never runs with a selection); it works when the
  snippet is inserted from the picker (below).
* An expansion whose result is empty or too large is not applied; what you typed stays as it is.

## The snippet picker (in the keyboard)

* A smartbar quick action **Snippets** (new installs: in the default arrangement; existing installs: appended once to the
  overflow panel, and left where you put it if you already hid or moved it) opens a panel that lists your **enabled**
  snippets (title, abbreviation hint, one-line preview) sorted by title. Tags you used become filter chips. There is no
  typed search in the panel (it would need a text field inside the keyboard).
* Tap a snippet: it is rendered and committed at the caret, **replacing the current selection** like typing would, then the
  keyboard returns to the text view. `{{selection}}` is the text that was selected when you tapped. A `{{cursor}}` moves the
  caret with plain left-arrow presses (counted per user-perceived character; if more than 500 would be needed, the caret is
  left at the end instead).
* In password-like fields `{{clipboard}}` and `{{selection}}` are never read or inserted (empty); the rest of the snippet
  is inserted. Nothing is shown while the device is locked, and the store is not read before the first unlock.
* Failures say so with a short message: too large (> 20 000 characters), nothing to insert (renders empty right now),
  or no input connection. The panel stays open in those cases. Titles, bodies, clipboard and selection are never logged.
* Uses the clipboard panel's theme elements, so existing themes style it.

## What exists (`dev.patrickgold.florisboard.ime.snippets`)

| Piece | Purpose |
|---|---|
| `Snippet`, `SnippetLimits`, `SnippetValidator` | The data model and its limits (500 snippets, title 80, body 10 000 chars, abbreviation 2–32 chars without whitespace, 10 tags) |
| `SnippetTemplate` | The template language and renderer (below); `usesPlaceholder` tells the shell whether the clipboard or selection is needed at all |
| `SnippetInsertion`, `InsertionRequest`, `InsertionPlan`, `CaretPlacement` | Turns a snippet into text to insert for **both** expansion and the picker, and applies the sensitive-field rule in one place (clipboard and selection are dropped); counts the left-arrow presses for `{{cursor}}` per grapheme cluster with a cap |
| `SnippetPickerModel`, `SnippetPreview` | The picker list (enabled only, optional tag, sorted) and the one-line preview (whitespace collapsed, bidi controls dropped, cut on a code point) |
| `ExpansionSettings`, `ExpansionMatcher` | Opt-in text expansion: decides whether a typed boundary character completes `<prefix><abbreviation>` |
| `ExpansionTrigger`, `ExpansionTriggers`, `ExpansionAction` | Per-key opt-in for space, Enter and Tab, and the rule that a non-match (or a render that cannot be inserted) leaves the key alone |
| `SnippetLibrary` | Immutable collection: add/update/remove, unique abbreviations (case-insensitive), search. Never drops anything silently |
| `SnippetEncryptedBackup` | Password-encrypted export/import: the clipboard `BackupCrypto` container around `SnippetBackup`'s JSON; size-bounded before key derivation, typed errors (`WRONG_PASSWORD`, `WEAK_PASSWORD`, `ENCRYPTED`) |
| `SnippetBackup`, `SnippetImporter` | Versioned JSON export/import (`format: "victorious-snippets"`, `version: 1`) and a non-destructive merge |
| `SnippetFileStore` | One JSON file with atomic save and automatic `.bak` / `.corrupt` safety copies |

## Template language

Inert by design: **no placeholder can run a command, read a file, call a URL or loop.**

| Placeholder | Result |
|---|---|
| `{{date}}`, `{{date:PATTERN}}` | local date (`yyyy-MM-dd`) or a `java.time` pattern |
| `{{time}}`, `{{time:PATTERN}}` | local time (`HH:mm`) or a pattern |
| `{{datetime}}`, `{{datetime:PATTERN}}` | `yyyy-MM-dd HH:mm` or a pattern |
| `{{clipboard}}` | current clipboard text (empty if none) |
| `{{selection}}` | selected text (empty if none) |
| `{{cursor}}` | where the caret ends up (first wins, the rest are removed) |
| `{date}`, `{time}`, `{clipboard}`, `{cursor}` | the same results, as exact tokens with no pattern |

* `\{{` writes a literal `{{`. A backslash before a single-brace token (`\{date}`) writes that token unchanged.
* `{date:PATTERN}`, `{datetime}`, `{selection}`, and any other braces (`{ return; }`) stay literal. Only the four exact single-brace tokens above expand.
* Unknown, malformed, unterminated or over-long placeholders (including `{{exec:…}}`, `{{shell:…}}`, `{{env:…}}`) stay
  **literal text**; the validator reports them as warnings.
* Substituted clipboard/selection text is **never re-scanned**, so clipboard content cannot inject placeholders.
* A rendering over 20 000 characters is refused (`TooLarge`), not truncated, so a huge clipboard cannot flood a field.
* Date patterns that are invalid, longer than 32 characters or need a zone stay literal instead of failing.
* The caret position is returned as "code points from the end", ready for the shell to move left.

## Expansion rules

* **Off by default** (`ExpansionSettings.enabled = false`). The prefix is user-configurable (1–3 characters, default `;`).
* `;sig` + an opted-in boundary expands. Space is on whenever expansion is on. Newline (Enter) and tab are each off until
  the user opts in. The prefix must start the word, so ordinary words never expand.
* A boundary that is not opted in, a sensitive field, a selection, a held Shift/Ctrl/Alt on Enter or Tab, or no matching
  snippet produces no expansion. The shell then performs the real key. A render that is empty or too large does the same,
  so the abbreviation is not deleted and Enter/Tab are not swallowed.
* **Never** in a sensitive (password-like) field; the shell must pass that flag.
* Disabled snippets and snippets without an abbreviation never expand. Case sensitivity is configurable.
* No recursion or chaining: the decision uses only the text before the caret, and an inserted body is not matched again.

## Storage and privacy (must hold when the shell is written)

* The store directory **must** be under `noBackupFilesDir` (CLAUDE.md invariant 7); snippets are personal text.
* Nothing in the core logs or reports snippet content. The shell must not either.
* Exports are JSON that contains whatever the user wrote. **Export** asks whether to protect the file with a password
  (the switch is on by default; switching it off writes plain JSON). The encrypted file is the clipboard backup
  container (`BackupCrypto`, see `BACKUP_FORMAT.md`: PBKDF2-HMAC-SHA256 600 000 iterations, AES-256-GCM, random salt and
  nonce per file, authenticated header) around the same JSON, saved as `snippets.vkbk`. Minimum password length 8; a lost
  password cannot be recovered. Import detects an encrypted file by its magic bytes and asks for the password; a wrong
  password and a modified file give the same message. A clipboard `.vkbk` opened here is refused after decryption
  (not a snippets file). The container does not say what is inside it, so the two kinds of file look alike until opened.
  Limitation: the password is held as a `String` by the text field and cannot be wiped from memory.
* `{{clipboard}}` and `{{selection}}` must be passed as `null` in sensitive fields (`SnippetInsertion.plan` enforces it for
  both callers; a test per path).
* Before the first unlock after a reboot (Direct Boot) the credential-encrypted storage is unavailable: `SnippetManager`
  loads nothing until the user is unlocked and **refuses every save until a real load happened**, so an empty in-memory
  library can never replace the stored file.

## Failure behaviour

* A corrupt, oversized (> 8 MiB) or wrong-format file is reported and **never overwritten silently**: `load()` leaves it
  alone; the next `save()` keeps it as `snippets.json.corrupt` and never replaces the last good `.bak` with it.
* Import never removes anything: `SKIP_EXISTING` leaves existing snippets untouched, `OVERWRITE_BY_ID` only replaces
  the same id, clashing abbreviations and a full library are skipped and reported.
* Invalid entries in a file are left out and reported, never "repaired".

## Not done

* Typed search inside the picker; folders; recently used ordering.
* A password-like field with selected text and a snippet that uses `{{selection}}`: the selection is replaced like any
  commit would, but its text is (deliberately) not read.
* Per-mode settings beyond expansion (for example a different prefix or Enter/Tab keep-switches per mode), snippet
  variables beyond the six above, folders.
* Translations. Password strength beyond the 8-character minimum. A separate file extension that tells snippet and clipboard containers apart.
* Device validation of any of it (expansion timing against the IME's own composing text is the main unknown; for the
  picker: panel height and look, the left-arrow caret placement in real editors and in Termux, and `{{selection}}` in
  editors that report no selection). See the checklist in `TESTING.md`.
