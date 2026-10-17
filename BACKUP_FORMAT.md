# Clipboard backup format (version 2)

Implemented in `ime/clipboard/backup/` (pure Kotlin, JVM-tested). Reading a backup treats **every byte as hostile**.

## Variants

| Variant | Extension | Importable | Contains |
|---|---|---|---|
| Password ZIP | `.zip` | yes | `manifest.json` + `content.vkbk` (the encrypted container around a plain ZIP) |
| ZIP | `.zip` | yes | `manifest.json` + `entries.json` |
| Encrypted container | `.vkbk` | yes | the password container around a plain ZIP |
| JSON | `.json` | yes | one document: `{ "manifest": …, "entries": […] }` |
| Markdown | `.md` | **no** | human-readable archive, no integrity data |
| Plain text | `.txt` | **no** | entry texts separated by a dashed line |

Only **text** entries are exported. Images, videos, and URI clips are not preserved (their data isn't safely restorable)
and are counted in `skippedUnsupported`. The older backup screen still leaves out entries flagged sensitive by Android,
or that look like credentials, unless the user asks (`skippedSensitive`). The ZIP screen exports the text of the
clipboard the user picked (Regular, Vault, or both) and does not apply that second filter.

A Regular-only ZIP may be the plain ZIP, with an optional password. **Any export whose scope is Vault or Both must be
the password ZIP**, even when the vault is empty. A locked vault is not read, so it cannot be exported or imported.
Item text for a password ZIP is only inside `content.vkbk`. The outer `manifest.json` has counts and a hash, not the
clips. There is no way to recover a lost password.

## `manifest`

| Field | Meaning |
|---|---|
| `format` | `"victorious-keyboard-clipboard"` (anything else → "not a backup") |
| `formatVersion` | integer, currently `2`. `1` is still read |
| `createdAtMs` | export time, epoch ms |
| `appVersion` | app version that wrote it |
| `itemCount` | number of entries in the file (checked against the real count) |
| `supportedEntryTypes` | `["text"]` |
| `entriesSha256` | hex SHA-256 of `entries.json`, or of `content.vkbk` when `passwordProtected` is true. **Must be absent** in the JSON variant |
| `skippedUnsupported`, `skippedSensitive` | counts of what was left out |
| `attachments` | reserved, always `[]` (`{path, sha256, mime, entryId}` when used). Images are still not stored |
| `regularCount`, `vaultCount` | how many rows belong to each clipboard. On version 2 and newer their sum equals `itemCount` and each is checked against the rows. Version 1 files omit them |
| `passwordProtected` | true only on the outer manifest of a password ZIP |

## Entry

`id` (stable UUID-like string, `[A-Za-z0-9_-]{1,64}`), `type` (`"text"`), `text`, `createdAtMs`, `editedAtMs?`,
`pinned`, `favorite`, `tags[]`, `category`, `contentHash` (hex SHA-256 of the UTF-8 `text`, verified on read),
`lane` (`"regular"` or `"vault"`). An unknown `lane` is rejected. A missing `lane` (every version 1 file) is the
regular clipboard, so an old file cannot place rows into the vault and a new file cannot be misread by an older app
that would ignore an unknown field and import vault text into regular history.

Unknown JSON fields are ignored; an unknown `category` reads as `GENERAL`; entries with an unknown `type` are counted
and skipped. No passwords, keys or key hints are ever written.

If a vault row uses the same id as a regular row in one file, the vault id is renamed with a `-v` suffix. The regular
id is left alone. The vault file itself stores id, text, created time, pin, and reason. Favourite, tags, category, and
`editedAtMs` travel in the ZIP for both lanes, and the regular history keeps them; a vault import keeps the previous
reason for an existing id and marks a new row `IMPORTED`.

## ZIP layout

A plain ZIP has `manifest.json` and `entries.json`. When the user has custom suggestions, it also has
`suggestions.json`. That member is not part of `entriesSha256`. It is omitted when the list is empty, so older ZIPs
stay valid. A password ZIP has `manifest.json` and `content.vkbk` (the
encrypted container below; its plaintext is a plain ZIP, including `suggestions.json` when present). A file that contains both `entries.json` and `content.vkbk`
is rejected. Other members are ignored and never extracted (nothing is ever written to disk during import). Member
names containing `..`, a leading `/`, `\`, `:` or NUL are rejected. Timestamps inside the ZIP are fixed so identical
plaintext input gives identical bytes. The password ZIP is not byte-identical across runs, because the container uses
a random salt and nonce.

`entriesSha256` is checked on the original member bytes **before** the version 1 → 2 migration adds `lane`.

## Encrypted container

All integers big-endian. The whole 40-byte header is AEAD associated data.

```
offset size field
0      4    magic "VKBK"
4      1    container version = 1
5      1    KDF  = 1  (PBKDF2-HMAC-SHA256)
6      1    cipher = 1 (AES-256-GCM)
7      1    reserved = 0
8      4    PBKDF2 iterations (unsigned)
12     16   salt   (random per file)
28     12   nonce  (random per file)
40     …    ciphertext || 16-byte GCM tag   (plaintext = the ZIP above)
```

* Key: PBKDF2-HMAC-SHA256, 256-bit, from the password normalised to Unicode NFC. Export uses **600 000** iterations.
* On read, the iteration count must be within **100 000 – 5 000 000**, so a crafted header can't make the app spin.
* Minimum export password length: 8. A forgotten password cannot be recovered.
* Wrong password and tampering are reported identically ("wrong password, or the file was modified").
* Only JCE primitives are used; no custom cryptography. Argon2 would resist GPUs better but needs a third-party/native
  library; revisit in Phase 9.
* Limitation: the password is held as a `String` by the UI text field and can't be wiped from memory.

## Snippet export (reuses this container)

`SnippetEncryptedBackup` (`ime/snippets/`) writes the snippets JSON (`format: "victorious-snippets"`, see `SNIPPETS.md`)
inside the same container, with the same KDF, cipher, header and limits. The container carries no marker for its
payload, so a reader must check what it decrypted: the snippet reader rejects anything that is not a snippets file, and
the clipboard reader rejects anything that is not a ZIP of the clipboard format. File read is capped at the plain limit
(8 MiB) plus 56 bytes of container overhead before any key is derived. No format change; container version stays 1.

## Limits

File ≤ 96 MiB; uncompressed ≤ 96 MiB; ≤ 16 ZIP members; ≤ 5 000 entries; ≤ 1 000 000 chars per entry; ≤ 64 tags per
entry. (A history of 500 × 50 000 chars is about 25 MB.) Whole files are read into memory — acceptable at these sizes;
revisit for low-memory devices in Phase 9.

## Reading and errors

`BackupReader.read` returns a parsed backup or throws `BackupException` (`NotABackup`, `UnsupportedVersion`,
`Malformed`, `Corrupt`, `TooLarge`, `PasswordRequired`, `WrongPasswordOrTampered`, `WeakPassword`,
`EncryptionRequired`, `VaultLocked`). Messages never contain clip text.

## Versioning and migrations

`formatVersion` > the app's version → "created by a newer version, update the app". Versions ≥ 1 and ≤ current are
read through `BackupMigrations` (an ordered chain of pure JSON transformations). The one step today is v1 → v2: add
`lane: "regular"` where it is missing. A format change must add a step, a test fixture of the old format, and a note
here. Old files must stay importable.

## Import semantics (`ImportPlanner`, pure)

The plan is computed before anything changes and shown to the user.

* **Replace** — deletes the entire current history (pinned too), then imports. Always needs confirmation if there is
  anything to delete.
* **Merge** — keeps current entries; a backup entry matching an existing one by id or identical text is a duplicate:
  tags are unioned, pin/favourite OR-ed, a `GENERAL` category is filled in; if the *same id* has *different text*, the
  more recently modified version wins (counted as a conflict).
* **Skip duplicates** — current entries untouched; only missing ones are added.
* **500 cap** — after the mode step, if over the limit the *oldest unpinned* entries of the combined set go. Existing
  pinned entries are never removed; if only pinned remain over the limit, *incoming* pinned entries are dropped. An old
  backup merged into a full history therefore drops incoming entries rather than deleting existing ones. Every removed
  or dropped entry is listed in the plan; any removal/drop makes `requiresConfirmation` true.
* Consent (`Confirmation`) is tied to the exact entries previewed; if the history changed so that more would be
  deleted, the user is asked again.
* Applying is one transaction per store (`deleteIds` + `upserts`). Regular and vault are planned separately, so a
  replace of a regular-only file does not empty the vault, and a vault row is never inserted into regular history.
  The vault transaction runs first. If it fails, regular history is unchanged. If the regular transaction then fails,
  the vault file has already been updated.
* Import asks for a password when the file is a `.vkbk` or a password ZIP. The user can merge, replace, or skip
  duplicates. Merge and skip de-duplicate by id or identical text inside that clipboard.
