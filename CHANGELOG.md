# Changelog

Format based on [Keep a Changelog](https://keepachangelog.com/). Versions are pre-release until Phase 10.

## [Unreleased]

### Changed
- CI: dependency-verification checksums for five Gradle plugin marker POMs now match what the Gradle Plugin Portal serves
  (Central's copy kept as `also-trust`); CI run 5 passed build + unit tests on GitHub. Test-report upload is non-fatal.
- About screen: "Based on FlorisBoard" credit with licence and baseline version, linking to upstream.
- Removed user-visible upstream branding: theme names, "FlorisBoard" wording in strings (translated copies removed,
  English fallback), the button that opened upstream's add-on store, the upstream feedback link, an Albanian app-name
  override. Identifiers and licence credit intentionally unchanged (`docs/branding/README.md`).

### Fixed
- `PayloadCipher.decrypt` rejects non-canonical base64url (an altered last character could decode to identical bytes
  and still authenticate); tamper tests are now deterministic.

### Added
- Snippets: Android shell for the Phase 5 core: store in `noBackupFilesDir`, opt-in expansion on the space key, Settings →
  Keyboard → Developer mode → Snippets screen (add/edit/delete/search, import/export through the system file picker).
  Compiled and unit-tested (pure parts); never run on a device.
- Operating modes (Standard, Developer, Termux) with a default-mode setting and opt-in automatic Termux mode in
  Termux/ConnectBot (uses only the package name Android gives every keyboard). Claude Code / Kali / NetHunter AI modes
  are planned and not selectable. See `docs/DEVELOPER_KEYBOARD.md`.
- `docs/ARCHITECTURE_ASSESSMENT.md`: architecture and gap assessment against the master brief.
- Phase 5 core (pure Kotlin, tested): snippet template language (`{{date}} {{time}} {{clipboard}} {{selection}}
  {{cursor}}`, inert by design), opt-in expansion rules, snippet library, versioned JSON import/export, atomic file
  store with safety copies. Not wired into the keyboard; no UI. See `SNIPPETS.md`.
- Developer row (Settings → Keyboard → Developer mode, off by default): Esc/Tab/Ctrl/Alt/arrows/Home/End/PgUp/PgDn plus
  per-language symbol rows, sticky/locked Ctrl and Alt with an indicator, arrow key repeat, Ctrl/Alt on the keyboard's
  arrow keys. Compiled and logic-tested; never rendered on a device.
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
