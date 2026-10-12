# Clipboard backup format (version 1)

Implemented in `ime/clipboard/backup/` (pure Kotlin, JVM-tested). Reading a backup treats **every byte as hostile**.

## Variants

| Variant | Extension | Importable | Contains |
|---|---|---|---|
| Encrypted ZIP | `.vkbk` | yes | password-protected ZIP (below) |
| ZIP | `.zip` | yes | `manifest.json` + `entries.json` |
| JSON | `.json` | yes | one document: `{ "manifest": …, "entries": […] }` |
| Markdown | `.md` | **no** | human-readable archive, no integrity data |
| Plain text | `.txt` | **no** | entry texts separated by a dashed line |

Only **text** entries are exported. Images/videos/URI clips are not preserved (their data isn't safely restorable) and
are counted in `skippedUnsupported`. Entries flagged sensitive by Android or that look like credentials are left out
unless the user asks, and are counted in `skippedSensitive`.

## `manifest`

| Field | Meaning |
|---|---|
| `format` | `"victorious-keyboard-clipboard"` (anything else → "not a backup") |
| `formatVersion` | integer, currently `1` |
| `createdAtMs` | export time, epoch ms |
| `appVersion` | app version that wrote it |
| `itemCount` | number of entries in the file (checked against the real count) |
| `supportedEntryTypes` | `["text"]` |
| `entriesSha256` | hex SHA-256 of `entries.json` bytes (ZIP/encrypted only; **must be absent** in the JSON variant) |
| `skippedUnsupported`, `skippedSensitive` | counts of what was left out |
| `attachments` | reserved, always `[]` in v1 (`{path, sha256, mime, entryId}` when used) |

## Entry

`id` (stable UUID-like string, `[A-Za-z0-9_-]{1,64}`), `type` (`"text"`), `text`, `createdAtMs`, `editedAtMs?`,
`pinned`, `favorite`, `tags[]`, `category`, `contentHash` (hex SHA-256 of the UTF-8 `text`, verified on read).
Unknown JSON fields are ignored; an unknown `category` reads as `GENERAL`; entries with an unknown `type` are counted
and skipped. No passwords, keys or key hints are ever written.

## ZIP layout

Exactly two members are read: `manifest.json`, `entries.json`. Other members are ignored and never extracted
(nothing is ever written to disk during import). Member names containing `..`, a leading `/`, `\`, `:` or NUL are
rejected. Timestamps inside the ZIP are fixed so identical input gives identical bytes.

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

## Limits

File ≤ 96 MiB; uncompressed ≤ 96 MiB; ≤ 16 ZIP members; ≤ 5 000 entries; ≤ 1 000 000 chars per entry; ≤ 64 tags per
entry. (A history of 500 × 50 000 chars is about 25 MB.) Whole files are read into memory — acceptable at these sizes;
revisit for low-memory devices in Phase 9.

## Reading and errors

`BackupReader.read` returns a parsed backup or throws `BackupException` (`NotABackup`, `UnsupportedVersion`,
`Malformed`, `Corrupt`, `TooLarge`, `PasswordRequired`, `WrongPasswordOrTampered`, `WeakPassword`). Messages never
contain clip text.

## Versioning and migrations

`formatVersion` > the app's version → "created by a newer version, update the app". Versions ≥ 1 and ≤ current are
read through `BackupMigrations` (an ordered chain of pure JSON transformations; empty for v1). A format change must
add a step, a test fixture of the old format, and a note here. Old files must stay importable.

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
* Applying is one database transaction (`deleteIds` + `upserts`). If it fails, nothing changed.
