# Changelog

Format based on [Keep a Changelog](https://keepachangelog.com/). Versions are pre-release until Phase 10.

## [Unreleased]

### Added
- Optional Droidcommand-AI fleet link for every app that implements `fleet-keyboard-api`, not a fixed hub. The keyboard stays standalone and does not request `INTERNET`. It discovers providers with `PackageManager.queryIntentServices` for `com.victorious.fleet.KEYBOARD_PROVIDER` and accepts one only when it holds `com.victorious.fleet.PERMISSION` and matches this keyboard's signing certificate. Both names are placeholders marked `TODO-confirm-with-hub`. There is no app list. Connected apps advertise a name, icon, slash commands, snippets, and actions. Clashing slash names are namespaced (`/edl:status`). When a trusted app is connected, the tabbed tools panel gains a Fleet tab: one section per app, with its icon and a status dot, an Ask tile, advertised actions, and snippets. Settings → Tools → Fleet link is the switch. Package add, remove, and replace rescan. Password fields are never sent. Vault text is sent only from an explicit Ask or action tap. Provider text is inserted and never executed. Not run on a device.
- UI pass: the tools panel has five tabs (Clipboard, AI, Terminal, Code, Quick) of icon tiles with translatable labels; Incognito, Learn words, Key row and Bottom gap show their state. It uses the keyboard theme's tile and chip elements, so every theme and Keyboard colors apply. It keeps the window height (keys plus the terminal key row). A Tune button opens Settings → Smartbar → Tools panel, which now hides and reorders buttons per tab and lists your own commands on the Terminal tab (inserted as text, never run). Snippets, select all, undo and redo are reachable from the Clipboard tab. Not rendered or run on a device.
- Settings → Developer & terminal (operating mode, auto-detect, terminal key row, terminal keys, symbols, advanced settings) and Snippets → Expansion screens. Not rendered.
- Suggestion strip shows words while typing (swipe for more, primary suggestion highlighted) and a tools button that opens a panel in place of the keys: Clipboard, Clip editor, Vault, AI, Terminal keys, Code, and Privacy. The page-tab row is no longer on the strip. The Esc/Tab/Ctrl/Alt/arrow row is Auto (terminal apps and `TYPE_NULL`), Always, or Never. Not run on a device.
- My suggestions. Settings → Typing → Suggestions → My suggestions, or long-press a strip word or selected text. Optional shortcut. Editable and deletable. Included in the clipboard ZIP as `suggestions.json` when the list is not empty. Stored in `noBackupFilesDir`. Password fields are skipped. Not run on a device.
- Slash commands. Typing `/` at the start of a word lists matches; tapping one expands it. Built-ins include `/date`, `/time`, `/clip`, `/snip`, `/ai`, `/fix`, `/rewrite`, `/sum`, `/translate`, `/shell` (inserted, never executed), `/edit`, and `/vault`. Custom commands support `{date}` `{time}` `{clipboard}` `{selection}` `{cursor}`. Settings → Slash commands can add, edit, delete, reorder, and disable them. Off in password fields. Not run on a device.
- On-device Local AI. Settings → Local AI imports a user-supplied `.gguf` (Storage Access Framework, private copy, size warning, list, delete, select). Parameters: context size, threads, temperature, max tokens, and a system prompt stored in `noBackupFilesDir/ai/`. llama.cpp `b11624` is linked through JNI for `arm64-v8a` and `x86_64`. No model is bundled and no `INTERNET` permission is added. The AI toolbar page rewrites, fixes grammar, continues, summarizes, translates, drafts one shell command, or asks a free-form question. Sources are the selection, field, clipboard, or clip editor; vault text is included only when chosen. Suggestions in the strip are off by default and follow the learning toggle and no-personalized-learning flag. Inference is off the main thread, streams, and can be cancelled. The model unloads on memory pressure and after idle. Not run on a device.
- Clipboard ZIP export and import (Settings → Tools → Clipboard). Choose Regular, Vault, or both. A password is required when the vault is included; that ZIP stores the existing AES-256-GCM container and does not put item text in the manifest. Import can merge or replace, and vault rows stay in the vault. Images are still skipped. Not run on a device.
- Two clipboards. The keyboard panel has Clipboard and Vault tabs. Password-field copies and clips Android marks sensitive go to the Vault. Opt-in detection can also send card numbers, IBANs, API keys, private keys, and one-time codes there. Vault items use a separate Keystore key, stay out of suggestions, stay masked until revealed, lock with the device credential (on by default), expire (default 24 hours; Never is allowed; pins stay), and are left out of backups unless an encrypted export is opted into. Not run on a device.
- Keyboard colors (Settings → Look). Six colors, a hue/saturation wheel and hex field, six presets, a live preview, and reset. Saved in preferences and painted over Victorious Neon and Neon Light, including popups, suggestion pills, the clipboard strip, the developer row, and the emoji panel. Unreadable text is adjusted. Not run on a device.
- Word prediction in the suggestion bar: current-word completions, a next-word guess, and a one-edit autocorrect from
  the bundled dictionary packs. *Word prediction* is on by default. *Learn from my typing* is off by default; when on,
  words stay in `noBackupFilesDir/prediction/learned-words.json` and are never read from password, incognito, or
  no-personalized-learning fields. *Clear learned words* asks before deleting them. Not run on a device. `spell()` is
  still a stub, and next-word without learning ranks dictionary frequency only.

### Changed
- Settings Home: Look, Typing, Tools (Clipboard, Snippets, Slash commands, Fleet link, Local AI, Emoji), Developer, Data (back up, restore), About; every entry has a one-line summary. The developer and snippet-expansion settings left the Keyboard screen. Not rendered.
- Terminal key row: Auto also shows it in Developer or Termux mode. One pure decision (`TerminalRowPolicy.plan`) now drives both the row and the height that full-height panels reserve for it.
- The AI panel and the clip editor take their colors from the keyboard theme (window, key and pressed-key colors) instead of fixed near-black values; unreadable text is replaced with a readable ink. Not rendered.
- The tools panel's ZIP button opens the clipboard ZIP screen instead of the settings home.
- Look: Victorious Black is the default day and night keyboard theme (near-black background, charcoal keys, light legends, about 5 dp corners). Victorious Neon and Neon Light stay selectable, and the color picker still has a Neon preset. Not run on a device.
- Victorious Neon and Neon Light remain selectable. Their keys use an amber edge and a soft shadow; the suggestion bar, key popup, clipboard panel, snippet picker, emoji panel and developer row use navy `#010619`, amber `#FFC107` and hot pink. The older FlorisBoard themes stay in the theme list. Settings defaults to the dark Material scheme (light and AMOLED remain); a chosen accent color still recolors the app. Letter keys scale down slightly while pressed.
- Logo: `docs/branding/logo.png` is the approved 720×720 neon mark (hooded figure at a keyboard). Launcher foregrounds,
  monochrome layers and `docs/branding/play_store_icon_512.png` are regenerated from it. The adaptive-icon background is
  the logo's navy, `#010619`. The neon stays inside the 33dp safe zone. The README header shows the 720px file at 240px,
  so it is not enlarged.
- Logo and launcher icons: the project logo (`docs/branding/logo.png`) is the master. `docs/branding/make_icons.py` generates
  the adaptive-icon foreground and monochrome layers for every density and the 512 px Play Store icon. The earlier generated
  vector mark and its generator (`make_logo.py`, SVG masters) were removed; they remain in git history.
- README rewritten as the project homepage: feature status per item, phase status, APK facts, build and install steps, and a
  documentation index. It no longer describes Phase 3–5 features as planned.
- CI: dependency-verification checksums for five Gradle plugin marker POMs now match what the Gradle Plugin Portal serves
  (Central's copy kept as `also-trust`); CI run 5 passed build + unit tests on GitHub. Test-report upload and debug APK
  upload are both non-fatal, so a full GitHub artifact-storage quota no longer fails the job after a good build.
- About screen: "Based on FlorisBoard" credit with licence and baseline version, linking to upstream.
- Removed user-visible upstream branding: theme names, "FlorisBoard" wording in strings (translated copies removed,
  English fallback), the button that opened upstream's add-on store, the upstream feedback link, an Albanian app-name
  override. Identifiers and licence credit intentionally unchanged (`docs/branding/README.md`).

### Fixed
- Slash commands: `/ai`, `/shell`, and `/snip` still keep a path (`/shell ls /usr/bin`), and a later command (`/ai hello /date`) starts that command instead of being swallowed. A path on its own (`cd /usr/bin`) no longer replaces the suggestion strip with an empty command list. Case conversion keeps a decomposed accent attached to its letter. Not run on a device.
- The *Terminal keys* and *Navigation keys* switches had no effect on the drawn row (it always showed both), and *Terminal keys* could not be reached because it depended on the retired *Developer row* switch.
- In a terminal app with default settings the row was drawn but no height was reserved for it, so full-height panels (snippet picker) opened one row shorter and the window changed size.
- Android 8–12 crashes found by lint (`NewApi`, a CI gate that was failing on `main`): importing a GGUF read its header with `InputStream.readNBytes` (API 33), and the URL encode/decode tools used the `Charset` overloads of `URLEncoder`/`URLDecoder` (API 33). Both now use calls available on API 26.
- Settings → Smartbar → Toolbar configured a strip toolbar that was no longer drawn anywhere; it now configures the tools panel. The unused `AdvancedToolbar` was removed.
- Docked keyboard keys no longer sit in the home-gesture strip. The bottom padding is the largest of the navigation-bar, mandatory system-gesture, tappable-element, and IME caption-bar insets, plus a Bottom gap setting (Settings → Look → Keyboard, 0–48 dp, 16 dp on gesture navigation and 4 dp with buttons). The gap uses the keyboard background and shows in the keyboard preview. Not run on a device.
- "Search for Updates" on Add-ons & Extensions opens this project's GitHub releases page. Product links (repository, issues, privacy, changelog, spell-checker and theme-editor notes) live in `ProjectUrls` and no longer send people to the FlorisBoard add-on store or wiki. The About screen still links to the upstream FlorisBoard repository as attribution.
- Suggestion chips: the neon pill's vertical margin and padding were larger than the fixed smartbar row, so Compose clipped the lower half of each word. The chip now uses a short vertical margin, does not clip the glyphs, and shrinks the type size so the line box fits at font scales 0.85×–1.3×. Long words ellipsize. The same spacing is what the color-picker overlay paints over. The developer row scrolls outside its background clip, so the Home key is no longer cut off at the screen edge.
- Typing "hope" and then space was replaced with "rope". The AI dictionary pack stores RoPE, which is one edit from hope, and the correction never checked the English word list. A word in that list, the user dictionary, or learned words is no longer replaced. Backspace immediately after a replacement restores the typed word. Restoring the same word twice adds it to the user dictionary. Settings → Typing → Autocorrect is Off, Mild, or Normal (Normal is the default). Mild only replaces an unknown word when the missed key is a neighbor, so h is not treated as next to r. Not run on a device.
- `lintDebug` errors that had failed the build since the Phase 1 baseline (and one added with the snippets screen):
  literal percents in the "above 50%" validation hint are escaped, plural labels use `%d` so locales whose "one"
  form covers more than one number keep the quantity, the physical-keyboard screen reads `LocalConfiguration`,
  the snippet editor resolves errors during composition, and the second `EmojiCompat.reset` is suppressed the same
  way as the first. `lintDebug` now runs in CI. Warnings are unchanged and do not fail the build.
- User-dictionary language list: Room drops null elements of `List<T?>`, so words stored for all languages never
  appeared as their own language. The query now returns a row with a nullable tag. The system dictionary cursor is
  closed when it is empty.
- Benchmark build type no longer warns that it is replacing `floris_app_name` copied from release.
- Snippet store in Direct Boot: before the first unlock after a reboot the storage is unavailable and a missing file
  looked like "no snippets"; opening a snippet screen/panel then latched an empty library, and a later save would have
  replaced the real file (a `.bak` copy remained). `SnippetManager` now loads nothing until the user is unlocked and
  refuses to save before a real load.
- Snippet expansion now goes through the same pure insertion rules as the picker: a result that is empty or too large is no
  longer applied (before, an empty result could delete the typed abbreviation), `{{clipboard}}` is read only if the snippet
  uses it, and `{{cursor}}` placement counts user-perceived characters instead of code points (emoji with skin tone,
  flags, combining accents no longer overshoot) and is skipped for very long tails.
- `curlyFormat` (the `{name}` string formatter behind `stringRes(id, "x" to value)`) looped until out of memory when a
  value contained its own placeholder. Reachable through text we do not control, e.g. the `{error}` argument built from
  an exception message while importing a backup file, or the delete-confirmation name. Values are now inserted once and
  never re-scanned (this also stops one argument injecting another's placeholder). Reproduced before the fix (OOM),
  9 regression tests after.
- `PayloadCipher.decrypt` rejects non-canonical base64url (an altered last character could decode to identical bytes
  and still authenticate); tamper tests are now deterministic.
- Code tab case conversion (camelCase, snake_case, kebab-case, PascalCase) treated every non-ASCII letter as a word separator and dropped it (`café au lait` became `caf_au_lait`). It now keeps Unicode letters and digits.
- Slash commands: a slash followed by a space (`1 / 2`) no longer takes over the suggestion strip, and a later slash inside `/ai`, `/shell` or `/snip` text (`/shell ls /usr/bin`) no longer re-anchors the command on that slash.
- Tools panel: a tab whose buttons are all hidden now says so (tap to open the toolbar settings) instead of a blank panel, and tiles under 64 dp show one label line so a second line is not clipped in short (landscape) panels. Not rendered.

### Added
- Snippet expansion on Enter and Tab. Each key is its own switch, off by default (Settings → Keyboard → Developer mode,
  under "Expand snippets while typing"). A press that matches no snippet still sends the real Enter or Tab, including in
  password fields, while text is selected, and while Shift, Ctrl or Alt is held. "Keep Enter after expanding" and
  "Keep Tab after expanding" (Advanced developer settings, on by default) re-send the key after a successful expansion;
  a snippet with `{{cursor}}` does not. Pure decision JVM-tested; keyboard wiring compiled, never run on a device
  (`SNIPPETS.md`).
- Dictionary packs: five bundled word lists (Kali Linux tools, Kali NetHunter, AI/LLM vocabulary, slash commands, Termux
  and shell commands) as small zips, a strict bounded zip reader, non-destructive install/remove and a "Dictionary packs"
  screen under Settings → Typing → Dictionary. Words go to the internal user dictionary and today only affect the system
  spell checker; no suggestions exist yet. The lists were written from memory and are not verified online. Pure parts
  JVM-tested; screen compiled, never run (`DICTIONARY_PACKS.md`).
- Snippet picker in the keyboard: a "Snippets" smartbar quick action opens a panel with your enabled snippets (tag chips to
  narrow), tap to insert at the caret (replaces a selection); `{{selection}}` now works from there. Clipboard and selection
  are never read in password-like fields, clips the platform marked sensitive are not substituted, nothing is shown while
  the device is locked. The caret goes to `{{cursor}}` per user-perceived character, capped at 500 presses. Existing
  installs get the quick action appended once to the overflow panel. Pure parts JVM-tested; panel compiled, never run or
  rendered on a device (`SNIPPETS.md`).
- Advanced developer settings screen: developer row size, separate navigation/symbol key toggles, your own symbol keys
  (validated, never executed), key-repeat and Ctrl/Alt lock timing, vibration, snippet date/time formats and
  keep-space option, expansion in Termux mode (off by default), your own app-to-mode mappings, reset to defaults.
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
