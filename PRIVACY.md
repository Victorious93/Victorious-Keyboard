# Privacy

Applies to the code in this repository. Statements are checked against the source; items not yet built are labelled
**planned** and must not be read as current behaviour. Last reviewed: Phase 1.

## What the app does with your data today

* **Network:** the app declares **no `INTERNET` permission** (see `app/src/main/AndroidManifest.xml`), so it cannot
  open network connections itself. It can ask Android to open a link in your browser (for example "Privacy policy",
  "Project repository", or the upstream extension-store page); that is a browser action you trigger.
* **Analytics / telemetry / ads:** none.
* **Typed text:** processed in memory to insert text and (once implemented) to produce suggestions. It is not
  uploaded anywhere. The keyboard does not record keystrokes to a log.
* **Personal dictionary:** words you add are stored in a local database (`floris_user_dictionary`).
* **Clipboard history (inherited from FlorisBoard):** if enabled, items you copy are stored in an app-private
  database on the device. **At this baseline the database is not encrypted** (encryption is **planned**, Phase 3).
  Items Android marks as *sensitive* are masked in the UI. History can be disabled or cleared in the settings.
* **Crash reports:** if the app crashes it shows a local crash screen containing a stack trace and device info. Nothing
  is sent automatically; you choose whether to copy it into an issue. Check it for private text before sharing.
* **Android auto-backup:** the app allows Android's backup service to include only: settings (`jetpref_datastore`),
  the `files/ime` folder (keyboard/theme extensions), and the personal-dictionary database. Clipboard history and
  clipboard images (stored in `noBackupFilesDir`) are **not** included. If you do not want settings or your personal
  dictionary in a device/cloud backup, disable backup for the app in Android settings.
* **In-app backup/restore (inherited):** creates a ZIP of settings and extensions that you save where you choose.

## Planned (not implemented yet)

* Encrypted clipboard storage; encrypted, password-protected backup files; never storing text from password fields.
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
