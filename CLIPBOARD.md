# Clipboard manager

Status: **implemented and unit-tested at the logic level; compiled but not run on any Android device.** Read
"What is verified" before relying on anything here.

## Behaviour

* **Off by default.** History is only recorded after the user enables it (*Settings → Clipboard → Enable clipboard
  history*). That switch is also the pause control. Disabling it stops recording immediately; existing entries stay
  until deleted.
* **Sources.** Text copied anywhere (via the system clipboard listener, subject to Android's clipboard rules), and the
  keyboard's own Copy/Cut keys. The keyboard never reads typed text into history, and its Copy/Cut keys **refuse in
  password fields** (text, visible, web and numeric passwords).
* **Android limits (honest list).** Apps cannot read the clipboard in the background; the keyboard sees clips while it
  is the active input method. The source app of a clip is not provided by Android to keyboards, so none is stored.
  Android 13+ marks sensitive clips; those are masked and can be auto-removed after a delay. No root, no Accessibility
  Service, no workarounds.

## Retention rules (enforced by `HistoryPolicy`, pure Kotlin, unit-tested)

1. At most **500** entries, pinned ones included. The user can lower the limit (5–500), never raise it. The old
   "limit on/off" switch no longer exists; the cap is always on.
2. When full, the **oldest unpinned** entry is evicted first.
3. **Pinned entries are never evicted automatically.** If the history is full of pinned entries, the new clip is **not
   saved** and the user is told; nothing is deleted.
4. Lowering the limit deletes nothing at that moment; surplus entries go as new clips arrive.
5. Clips longer than 50 000 characters (configurable ≤ 200 000 in code) are rejected with a notice, never truncated.
6. Duplicates: *move existing to top* (default; keeps pin/favourite/tags), *ignore*, or *keep both*.
7. Anything that must be kept permanently belongs in the separate **snippet collection** (Phase 5), which is outside
   the cap. Backups are also outside the cap.

## Data per entry

Text, UUID, timestamps (created, edited), pinned, favourite, tags (≤ 16, normalised), category, sensitive flag.
Character count is derived. Images and videos (upstream feature) remain in history and count toward the cap, but are
**not** exported: backup format v1 is text-only, and URI-backed clips can't be restored faithfully.

## Local categorisation

`ClipClassifier` guesses URL / JSON / shell command / Markdown / code / AI prompt / general with simple rules. It runs
on the device, sends nothing anywhere, is a convenience (the user can change it), and is not a security control.

## Sensitive data

* Clips Android flags as sensitive are masked in the UI and excluded from exports by default.
* `SensitiveDetector` recognises private-key headers, AWS key ids, JWTs, common API-token formats, password/secret
  assignments with secret-looking values, and Luhn-valid card numbers. Matching text is **not saved** unless
  *Save passwords and keys* is switched on. It is pattern matching: it misses secrets that look like ordinary text and
  can flag harmless text. It reports only a reason, never the matched text.
* Nothing from this feature is logged.

## Encryption at rest

* Each text value is stored as `vkenc1:` + base64url(nonce ‖ AES-256-GCM ciphertext ‖ tag), the row's UUID bound as
  authenticated data (a ciphertext moved to another row fails).
* The 256-bit data key is random, kept only **wrapped** by a non-exportable **Android Keystore** AES key, in
  `noBackupFilesDir` — outside Android auto-backup, so it can't reach a device where the Keystore key doesn't exist.
* If the key is unavailable, the app **refuses to store** new clips (it never falls back to plaintext) and hides the
  undecryptable rows, counting them against the cap. They are not deleted automatically; *Reset clipboard storage*
  (explicit confirmation) is the recovery path.
* What this protects: the database file read without access to the Keystore key. What it does not: malware running as
  the app, a rooted device, or someone using your unlocked phone. The unwrapped key is cached in process memory.
* Metadata (timestamps, pin/favourite flags, category) is not encrypted.
* Database: Room schema v5 with explicit migration; no destructive fallback. If Room can't migrate an old file it is
  renamed aside (`*.unmigratable-<time>`), not deleted.

## Search, filters, editing

Full-text search is done in memory (texts are encrypted, so SQL can't search them): all terms must match,
case-insensitive, in text or tags; debounced and run off the main thread. Filters: pinned, favourites, category,
tag. The **Clipboard manager** screen (*Settings → Clipboard → Clipboard manager*) offers search, multi-select with
bulk pin/unpin/tag/delete/export, an editor (text, tags, category, favourite, pin, copy, share, delete), and the
key-loss banner. The keyboard's clipboard panel keeps upstream's grid with pin/favourite/delete/paste.

**Known gap:** typed search *inside the keyboard panel* is not implemented — the IME has no internal text input
for its own UI, and building one means redirecting the core typing path, which is too risky without device testing.
Use the manager screen, or the filter chips in the panel.

## Backup and restore

See `BACKUP_FORMAT.md`. Summary: JSON, ZIP, password-encrypted ZIP (AES-256-GCM, PBKDF2-HMAC-SHA256, 600 000
iterations), plus one-way Markdown/text exports. Restore shows an exact preview (adds / merges / skips / removals /
discards), requires explicit consent when anything would be deleted or discarded, applies in one database
transaction, and never trusts the file. The general *Backup & Restore* page no longer exports clipboard **text**
(its archive is unencrypted).

## What is verified (and what is not)

| Criterion | Evidence |
|---|---|
| History never exceeds 500; pinned never evicted | `HistoryPolicyTest` incl. a 6 000-operation randomised invariant test — **JVM only** |
| Search, filters | `ClipSearchTest` incl. 500 × 50 000-char entries — JVM |
| Encrypted export re-imports; merge/replace/skip behave predictably | `BackupRoundTripTest`, `ImportPlannerTest` (randomised invariants), `ClipboardBackupServiceTest` — JVM |
| Malformed / hostile files cannot damage history | `BackupFormatTest`, `BackupCryptoTest`: traversal names, ZIP bomb, duplicate members, wrong/forged hashes, tampering of every byte, bad versions, size limits; failed apply leaves history unchanged (fake transactional repo) |
| Password-field handling | `InputAttributesPasswordTest` (field detection). The guard in Copy/Cut is a 1-line check; **not exercised on a device** |
| Encryption of stored rows | `PayloadCipherTest` (cipher). **Keystore provider and Room paths not run on a device** |
| History survives restarts | Room persistence — **not verified** |
| UI responsive with 500 long entries | Designed for it (lazy list, off-main search/decryption, bounded previews); **not measured** |
| Room 4→5 migration | Compile-time validated by Room; **instrumented migration test not written/run** |
| Settings screens, SAF pickers | Compiled; **never run** |
