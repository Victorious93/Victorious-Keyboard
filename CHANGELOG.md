# Changelog

Format based on [Keep a Changelog](https://keepachangelog.com/). Versions are pre-release until Phase 10.

## [Unreleased]

### Changed
- Removed user-visible upstream branding: theme names, "FlorisBoard" wording in strings (translated copies removed,
  English fallback), the button that opened upstream's add-on store, the upstream feedback link, an Albanian app-name
  override. Identifiers and licence credit intentionally unchanged (`docs/branding/README.md`).

### Fixed
- `PayloadCipher.decrypt` rejects non-canonical base64url (an altered last character could decode to identical bytes
  and still authenticate); tamper tests are now deterministic.

### Added
- Logo: hooded-figure mark with neon face and red laptop "V", as adaptive-icon vectors (stable cyan, debug amber,
  monochrome), master SVGs and a generator in `docs/branding/`.
- Phase 4 core: terminal key mapper (Ctrl/Alt/Shift + key → real `KeyEvent`), Ctrl/Alt sticky/lock/hold state machine,
  developer row and language-profile models (pure Kotlin, 37 tests). `KeyboardManager` now handles Ctrl/Alt/Esc/Tab key
  codes; inert unless a layout provides such a key (none does yet). No UI.
- Project documentation set (`AUDIT.md`, `ARCHITECTURE.md`, `LICENSES.md`, `BUILD.md`, `ROADMAP.md`, `CLAUDE.md`, `PRIVACY.md`, `SECURITY.md`).
- CI workflow: build debug APK and run JVM unit tests.
- Gradle dependency verification (SHA-256), regenerated and proven from a cold Gradle home.
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
