# Changelog

Format based on [Keep a Changelog](https://keepachangelog.com/). Versions are pre-release until Phase 10.

## [Unreleased]

### Added
- Project documentation set (`AUDIT.md`, `ARCHITECTURE.md`, `LICENSES.md`, `BUILD.md`, `ROADMAP.md`, `CLAUDE.md`, `PRIVACY.md`, `SECURITY.md`).
- CI workflow: build debug APK and run JVM unit tests.
- Gradle dependency verification (SHA-256).
- Clipboard core (pure Kotlin, unit-tested): 500-entry retention policy that never evicts pinned data, search,
  local categoriser, credential detector, backup formats (JSON / ZIP / password-encrypted with AES-256-GCM +
  PBKDF2-HMAC-SHA256), hostile-file-safe reader, and an import planner with replace/merge/skip modes and exact
  cap preview. 

- Clipboard manager: AES-256-GCM encrypted storage (Keystore-wrapped key), Room v5, favourites/tags/categories,
  Clipboard manager and Backup/restore screens (search, bulk actions, editor, preview-and-confirm restore,
  backup library), `CLIPBOARD.md`, `BACKUP_FORMAT.md`, `FEATURES.md`.
- Keyboard clipboard panel: favourite action.

### Changed
- Clipboard history is capped at 500 entries (was a toggleable 5–100); pinned entries are never evicted; changing the
  limit deletes nothing immediately; credential-looking text is not saved by default.
- Clipboard database no longer uses destructive migration fallback.
- The general Backup page no longer exports clipboard text (unencrypted archive).
- Forked from FlorisBoard `v0.6.0-alpha02` (`0ea0481c`). Application id is now `com.victorious.keyboard`; product name,
  launcher icon, in-app links and issue/PR templates replaced. Backups from either `com.victorious.keyboard*` or
  upstream `dev.patrickgold.florisboard*` can be restored.

### Fixed
- Keyboard Copy/Cut keys no longer read from password fields.
- Clipboard grid no longer lays out or pattern-matches whole 50k-character clips.

### Removed
- `:lib:native` Rust/JNI placeholder (debug scaffolding that could crash startup if the library failed to load).
- Upstream's `beta` build type, F-Droid/fastlane metadata, funding links, Crowdin config/workflow.
