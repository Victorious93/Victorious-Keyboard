# Privacy

Applies to the code in this repository. Statements are checked against the source; items not yet built are labelled
**planned** and must not be read as current behaviour. Last reviewed: Phase 3.

## What the app does with your data today

* **Network:** the app declares **no `INTERNET` permission** (see `app/src/main/AndroidManifest.xml`), so it cannot
  open network connections itself. It can ask Android to open a link in your browser (for example "Privacy policy",
  "Project repository", or the upstream extension-store page); that is a browser action you trigger.
* **Analytics / telemetry / ads:** none.
* **Typed text:** processed in memory to insert text and (once implemented) to produce suggestions. It is not
  uploaded anywhere. The keyboard does not record keystrokes to a log.
* **Personal dictionary:** words you add are stored in a local database (`floris_user_dictionary`).
* **Clipboard history:** off until you turn it on (*Settings → Clipboard*); that switch is also the pause control.
  When on, copied text is stored on the device only, **encrypted at rest** (AES-256-GCM; the key is wrapped by the
  Android Keystore and kept outside Android backup). Text that looks like a password, API key or card number is **not
  saved** unless you allow it; the keyboard's Copy/Cut keys refuse to work in password fields. Items Android marks
  sensitive are masked. History can be paused, searched, edited, deleted or cleared at any time. See `CLIPBOARD.md`
  for exactly what this does and does not protect against. *(Implemented and unit-tested; not yet verified on a
  device.)*
* **Clipboard backups:** only when you ask. You choose the destination with the system file picker; encrypted backups
  need a password you set, which is never stored. Nothing is uploaded by the app.
* **Crash reports:** if the app crashes it shows a local crash screen containing a stack trace and device info. Nothing
  is sent automatically; you choose whether to copy it into an issue. Check it for private text before sharing.
* **Android auto-backup:** the app allows Android's backup service to include only: settings (`jetpref_datastore`),
  the `files/ime` folder (keyboard/theme extensions), and the personal-dictionary database. Clipboard history and
  clipboard images (stored in `noBackupFilesDir`) are **not** included. If you do not want settings or your personal
  dictionary in a device/cloud backup, disable backup for the app in Android settings.
* **In-app backup/restore (inherited):** creates an unencrypted ZIP of settings, extensions and (optionally)
  clipboard images/videos that you save where you choose. Clipboard **text** is deliberately excluded from this
  archive because it is not encrypted.

## Planned (not implemented yet)

* Optional AI integrations: off by default, no `INTERNET` permission until you enable a provider, nothing sent without
  an explicit action each time, credentials in Android Keystore-backed storage.
* Voice typing through Android's speech recogniser only when you tap the microphone. Which service processes audio
  depends on your device's recogniser, not on this app.

## Things this app will not do

No keylogging beyond what is needed to type, no background uploads, no use of Accessibility Services or root to read
other apps, no sale or sharing of data.

## Upstream

This project is derived from FlorisBoard. Its privacy policy (<https://florisboard.org/legal/privacy/>) describes the
upstream project, not this one.
