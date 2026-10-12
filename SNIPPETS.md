# Snippets and text expansion (Phase 5)

Status: **pure-Kotlin core JVM-tested; Android shell (store, expansion on the space key, settings screen with
import/export) compiled but never run or rendered on a device.** Expansion is off by default.

## Using it (as built, unverified on a device)

* Settings → Keyboard → Developer mode → **Snippets**: add, edit, delete, search, import and export. Snippets live in
  `noBackupFilesDir/snippets/snippets.json` (not in Android auto-backup) and load off the main thread at app start.
* Same group: **Expand snippets while typing** (off by default), **Expansion prefix** (`;` `:` `/` `\` `;;` `::`),
  **Case-sensitive abbreviations**.
* With expansion on, typing `;sig` and then **space** replaces `;sig` with the rendered snippet and keeps the space,
  unless the snippet contains `{{cursor}}`: then the space is consumed and the caret is placed at `{{cursor}}`.
* Never in password-like fields, never while text is selected, and a rendering that is too large (> 20 000 characters)
  is not inserted (the typed text is left as it is).
* `{{date}}`, `{{time}}` and `{{datetime}}` follow the date and time format chosen in Advanced developer settings; an
  explicit pattern such as `{{date:yyyy}}` always wins. A pattern that cannot be used is inserted as the text you wrote.
* "Keep the typed space after expanding" (default on) can be switched off.
* `{{clipboard}}` uses the current clipboard text only if it is plain text; `{{selection}}` is always empty in
  expansion (there is no snippet picker yet that could supply a selection).

## What exists (`dev.patrickgold.florisboard.ime.snippets`)

| Piece | Purpose |
|---|---|
| `Snippet`, `SnippetLimits`, `SnippetValidator` | The data model and its limits (500 snippets, title 80, body 10 000 chars, abbreviation 2–32 chars without whitespace, 10 tags) |
| `SnippetTemplate` | The template language and renderer (below) |
| `ExpansionSettings`, `ExpansionMatcher` | Opt-in text expansion: decides whether a typed boundary character completes `<prefix><abbreviation>` |
| `SnippetLibrary` | Immutable collection: add/update/remove, unique abbreviations (case-insensitive), search. Never drops anything silently |
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

* `\{{` writes a literal `{{`. There is no other escape.
* Unknown, malformed, unterminated or over-long placeholders (including `{{exec:…}}`, `{{shell:…}}`, `{{env:…}}`) stay
  **literal text**; the validator reports them as warnings.
* Substituted clipboard/selection text is **never re-scanned**, so clipboard content cannot inject placeholders.
* A rendering over 20 000 characters is refused (`TooLarge`), not truncated, so a huge clipboard cannot flood a field.
* Date patterns that are invalid, longer than 32 characters or need a zone stay literal instead of failing.
* The caret position is returned as "code points from the end", ready for the shell to move left.

## Expansion rules

* **Off by default** (`ExpansionSettings.enabled = false`). The prefix is user-configurable (1–3 characters, default `;`).
* `;sig` + space/newline/tab expands; the prefix must start the word, so ordinary words never expand.
* **Never** in a sensitive (password-like) field; the shell must pass that flag.
* Disabled snippets and snippets without an abbreviation never expand. Case sensitivity is configurable.
* No recursion or chaining: the decision uses only the text before the caret, and an inserted body is not matched again.

## Storage and privacy (must hold when the shell is written)

* The store directory **must** be under `noBackupFilesDir` (CLAUDE.md invariant 7); snippets are personal text.
* Nothing in the core logs or reports snippet content. The shell must not either.
* Exports are **plain, unencrypted JSON** (they contain whatever the user wrote). Encryption can reuse the clipboard
  backup container later; it is not done.
* `{{clipboard}}` and `{{selection}}` must be passed as `null` in sensitive fields.

## Failure behaviour

* A corrupt, oversized (> 8 MiB) or wrong-format file is reported and **never overwritten silently**: `load()` leaves it
  alone; the next `save()` keeps it as `snippets.json.corrupt` and never replaces the last good `.bak` with it.
* Import never removes anything: `SKIP_EXISTING` leaves existing snippets untouched, `OVERWRITE_BY_ID` only replaces
  the same id, clashing abbreviations and a full library are skipped and reported.
* Invalid entries in a file are left out and reported, never "repaired".

## Not done

* A way to insert a snippet from the keyboard itself (a picker/panel): today snippets only insert by abbreviation.
  `{{selection}}` needs that picker.
* Expansion on Enter or Tab (only the space key is hooked; the core supports more boundaries).
* Expansion is **off in Termux mode by default** (Advanced developer settings → Expand snippets in Termux mode).
* Further per-mode defaults, snippet variables beyond the six above, folders.
* Encrypted export. Translations.
* Device validation of any of it (expansion timing against the IME's own composing text is the main unknown).
