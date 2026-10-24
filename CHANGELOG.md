# Changelog

Format based on [Keep a Changelog](https://keepachangelog.com/). Versions are pre-release until Phase 10.

## [Unreleased]

### Added
- Pack update check (roadmap item 10), the app's first and only network use. Settings → Tools → Pack updates has a switch that starts off and a *Check now* button. A check makes one HTTPS request for this project's `packs/index.json` on GitHub and lists each pack as new, update available or up to date; tapping one opens its page in the browser. It sends no identifiers, installed-pack list or text, never follows a redirect, caps the reply at 256 KiB, treats it as untrusted, downloads no pack, installs nothing and never runs in the background. The app now declares `INTERNET` (for this only) and blocks cleartext traffic. `CLAUDE.md` invariant 3, `PRIVACY.md`, `README.md` and the manual changed with it; `NetworkUseTest` fails if another network use or background trigger appears. Pure parts JVM-tested; the screen and connection class are compiled, never run on a device, and no real request has been made.
- `docs/RELEASES.md`: release policy (real versions get a release; debug builds share one rolling `debug-latest` pre-release; pack files use separate tags) and the commands to remove the old per-commit debug releases. Policy only; no workflow was added and nothing was deleted.
- Pack catalog format (roadmap item 9). `docs/PACKS.md` describes the pack types, ids, the `packs/index.json` catalog, its limits, how to publish a pack and the trust model; `LANGUAGEPACKS.md` now points to it. `packs/index.json` is an empty, valid catalog. A strict pure-Kotlin reader (`PackCatalogReader`) and a hash check (`PackHash`) are JVM-tested, and a test validates the repository's own catalog. The keyboard does not read the catalog or fetch anything yet, and nothing is published. Not run on a device.
- Get more buttons (roadmap item 8). The theme, keyboard-extension, language-pack, dictionary-pack and fleet screens open this project's page for that kind of add-on in the browser. All pack links are in `ProjectUrls` and point into the new `packs/` folder of this repository, whose pages say nothing is published yet. New local extensions default their homepage and issue tracker to the project's pages. A test fails on any project link that leaves the owner's repository. No network access from the keyboard, no behaviour change except where those taps go. Not rendered or run on a device.
- Local AI quick actions. The AI tab adds Shorter, Explain this command, and Generate command from a description. They use the on-device model, show the reply before Insert, and do not send the text off the phone. Explain and Generate reply with one sentence or one command and do not run it. Rewrite and Fix grammar stay as they are. Not run on a device.
- Precision cursor and selection. Drag on the space bar moves the caret one character at a time, with a haptic tick per step. Hold the space bar and drag to select from that caret. A selection toolbar offers word, line, all, cut, copy, paste, Home, End, and jump by word. A hold without a drag still runs the space-bar long-press action. Offline. Not run on a device.
- Per-app profiles and command history. Termux, NetHunter Terminal, JuiceSSH, and common code editors turn autocorrect off and the terminal key row on while you are in that app. A saved profile can also switch the layout, the theme, and suggestions, and the previous layout and theme are put back when you leave. Command history is off by default. In a terminal app it keeps the last 30 committed lines, skips password fields and incognito, and recalls them from the History tile or the older/newer keys. The lines stay in `noBackupFilesDir` and are not logged. Not run on a device.
- Text Tools tile on the Code tab. It transforms the selection, or the clipboard when nothing is selected, and shows a preview before Replace, Insert, or Copy. Transforms cover Base64, URL, and hex encode and decode, JSON pretty and minify, MD5, SHA-1, SHA-256, letter case, camelCase, snake_case, kebab-case, trim, sort, and dedupe lines, ROT13, URL, email, and IP extract, JWT header and payload (no signature check), UNIX time to and from a date, and shell, JSON, and regex escapes. Nothing leaves the phone and the text is not logged. Not run on a device.
- Code namespace is `com.victorious.keyboard`. The application id stays `com.victorious.keyboard` (debug `.debug`, bench `.bench`). The keyboard, spell checker, screenshot helper, and launcher icon keep their previous component names, so an update does not turn them off or make you pick the keyboard again. Preference keys, the user dictionary, extension ids, and fleet action strings are unchanged. Not run on a device.
- Remote-control envelope v1 on the hub bridge (`BRIDGE_PROTOCOL_MAJOR` 1, `BRIDGE_PROTOCOL_MINOR` 1). Operations are `capabilities.get`, `remote.command`, `macro.step`, `macro.run`, `node.status.get`, `config.export`, and `config.import`. `node.status.get` reports node health only and never includes typed text, clipboard contents, or Vault data. `config.export` is an action with `remoteAllowed` false and `requiresConfirm` false. `config.import` is an action with `remoteAllowed` false and `requiresConfirm` true. A remote caller gets `capability_off` and no settings are copied. The local backup is preferences only: themes, layouts, and keyboard settings, including the toolbar layout. Clipboard history, typed text, the Vault, and snippet bodies are not included. Import supports dry-run and the conflict modes skip, overwrite, and merge-by-id. A dry run writes nothing. Any other import waits for an on-device confirmation and then forces external control off. Callers are still authorized only by UID, package, and certificate. External control stays off until Hub status is on. The new operations never type into the focused app and never read the field. They can report status, switch Standard / Developer / Termux, switch a bundled theme, and send media keys. Volume on this envelope adjusts the system music stream. Exact seek, `media.state`, and a player `media.volume` level return `not_paired` here; those belong to the File Manager bridge. A destructive flag on a command or a macro returns `confirmation_required` and shows an on-device notice; it is never run. `node.status.get` ignores that flag. Expired requests and duplicate nonces are refused. The older `keyboard.input.text` and `keyboard.input.key` operations are unchanged. Not run on a device.
- Media controls. The tools panel Quick tab has a Media tile, and the terminal key row can add Play/Pause, Previous, Next, Rewind, Fast-forward, Volume down, and Volume up. Those buttons send media keys (`AudioManager.dispatchMediaKeyEvent`, down then up) or adjust the music volume. Volume keys stay on the system music stream. Player quieter and Player louder, shown only while File Manager is connected, send `media.volume` and change that player's own volume. An idle player may answer `no_session` or `NO_SESSION`; both fall back to the media key. If File Manager's external control is off, the reply is `external_control_disabled` and the panel says to turn that switch on. They do not type into the focused app. Exact seek through Victorious File Manager is off until File Manager seek is on. The debug package `ai.victorious.filemanager.debug` is pinned to its published certificate. The release package `ai.victorious.filemanager` has no certificate yet, so it uses the trust prompt and is never auto-trusted. Each package is discovered by its own bridge action, and a missing install is ignored. The tile can then show the track title from `media.state`. The title is not logged. No network access. Not run on a device.
- User manual. `docs/MANUAL.md` is written in everyday language and bundled in the app. Settings → Help renders that copy offline. The old spell-checker and theme-editor help links open the same screen. Picture slots under `docs/manual/img/` are TODO placeholders. Header art is `docs/manual/header.svg`.
- Custom terminal key row. Settings → Developer & terminal lists the keys (Esc, Tab, Ctrl, Alt, arrows, `|`, `~`, `/`, and the rest). Move them, remove them, or add them back. A macro key sends a saved sequence: Ctrl plus a letter, or a command followed by Enter. The command is inserted, not run, and it is not logged. Leave the order blank to keep the usual row. No network access.
- Clipboard tile search. On the regular clipboard tab, Search takes what you type (the keys do not go to the app while it is on), and Pinned and Favorites narrow the list. Clear resets them. Vault stays on its own tab. The query is not logged. No network access.
- Snippet variables. Inserting a snippet expands exact `{date}`, `{time}`, `{clipboard}` and `{cursor}` tokens, in addition to the existing `{{date}}` form. `{cursor}` places the caret. `{date:pattern}`, `{datetime}`, `{selection}` and other braces such as `{ return; }` stay as written. A value taken from the clipboard is not scanned again. No network access.

### Fixed
- Typing `word.word`, a file name, a domain, an email, a URL, or a version such as `1.2.3` no longer inserts a dictionary word after the period. A dotted token was one edit from a real word (`word.` → `words`, `file.t` → `filet`, `com.` → `come`) and the next non-letter committed it. The period is not its own word, so that commit was inserted with no space. Those tokens are not corrected. A period glued to a letter or digit does not auto-space or auto-capitalize; `word. ` still does. A period after an ordinary typo can still correct that word. Not run on a device.
- Hub bridge: an allowed Droidcommand app was always refused. The service read the caller's process id after Messenger had already posted the message, so it saw itself. It now uses the id Messenger stamped on the message. A missing stamp is refused.
- Fleet commands: a late reply from a provider could be applied to the next command. Replies are matched to the request id, and a reply with a missing or older id is ignored.
- Clipboard tile: an image or video clip with a missing or non-numeric uri crashed the keyboard. Those clips now show a short message. A video thumbnail no longer throws when width or height metadata is missing, and the metadata reader is released.
- Short and long toasts no longer block on `runBlocking`, which could deadlock a caller that already held the main thread. The same function names stay, and they are no longer marked deprecated.

### Changed
- Built-in keyboard, language, and theme extensions use Victorious names, maintainer Victorious93, and links to https://github.com/Victorious93/Victorious-Keyboard. Their ids are `com.victorious.keyboard.*` (for example `com.victorious.keyboard.themes`). A saved theme, layout, or language that still says `org.florisboard.*` loads the same pack. The Chinese shape pack points at that repository for more input methods. About and the Apache-2.0 licence still credit FlorisBoard. Not run on a device.
- No tappable link points at FlorisBoard any more. The About screen's "Based on FlorisBoard" row stays as a credit (the original licence is kept in the app) and now opens the in-app licence screen instead of the upstream repository. Theme stylesheets name `https://victorious93.github.io/Victorious-Keyboard/schemas/snygg/v2/stylesheet` as their schema; stylesheets that name the old `schemas.florisboard.org` URL (imported or community themes) still load and are rewritten to the new one on export. The URL is an identifier only and is never fetched. The bundled `extension.json` maintainer lists no longer carry personal email addresses (so no mailto chips); the original authors stay credited by name, and "Victorious Keyboard Contributors" is added. Built-in extension ids are now `com.victorious.keyboard.*`, and the previous `org.florisboard.*` ids still resolve. Covered by JVM tests; not run on a device.

### Added
- AI control through the Hub bridge (roadmap item 6). An allowed Droidcommand hub app can type text and press keys (Enter, Tab, arrows, Ctrl plus a letter) through `KeyboardBridgeService`: new operations `keyboard.input.text` and `keyboard.input.key`. Same switch (off by default, Settings → Tools → Fleet link → Hub status) and same signing-certificate allowlist as before. It cannot read the field and cannot run toolbar buttons. Refused in password-like fields, with no active field, for bad or oversize requests (text 2,000 characters, request 16,000, key repeat 20). Nothing typed is logged or returned. `BridgeInput` is pure Kotlin and JVM-tested; the Android shell is compiled and not run on a device.
- Customizable tools panel. Long-press the tools button, or open Settings → Smartbar → Tools panel. Show or hide each tile, reorder by dragging or with Earlier and Later, and pin up to four tools as small icons on the suggestion strip next to the tools button. Pinning nothing leaves the strip as suggestions and the tools button. Tile size and column count, and reset, are on the same screen. A slash command or a fleet action can be a Quick-tab tile or a pin: a slash pin inserts the rendered command, and a fleet pin asks that app. The layout is saved under `noBackupFilesDir` and travels in the clipboard ZIP as `toolbar.json` (shell history is left out). A file from before this layout has no pins. Not run on a device.
- Debug and benchmark builds sign with a fixed debug keystore (`signing/debug.keystore`). A newer debug APK installs over an older one signed with that key. A phone that already has a debug build signed with a different key needs one uninstall before this APK will install. Release signing is unchanged.
- Hub status bridge. The keyboard hosts `KeyboardBridgeService` for action `com.victorious.keyboard.action.BRIDGE`. The Droidcommand hub can ask `keyboard.capabilities.get` and `keyboard.status` over Messenger JSON. External control is off until Settings → Fleet link → Hub status is on. Callers are checked by UID, package, and signing certificate, and only `ai.droidcommand.app` and `ai.droidcommand.app.debug` are allowed. Typed text and keystrokes are never sent. No signature permission and no AIDL.
- Optional Droidcommand-AI fleet link. The keyboard stays standalone and does not request `INTERNET`. It discovers providers with `PackageManager.queryIntentServices` for `com.victorious.keyboard.action.COMMAND_PROVIDER` and talks to them with Messenger JSON (`commands.list`, `command.run`). An app signed with the keyboard release certificate is on until switched off. Every other app needs a one-time approval in Settings → Fleet link, pinned to that app's certificate SHA-256. There is no app list and no signature permission. Connected apps advertise a name, icon, slash commands, snippets, and actions. Clashing slash names are namespaced (`/edl:status`). When a trusted app is connected, the tabbed tools panel gains a Fleet tab: one section per app, with its icon and a status dot, an Ask tile, advertised actions, and snippets. Settings → Tools → Fleet link is the switch. Package add, remove, and replace rescan. Password fields are never sent. Vault text is sent only from an explicit Ask or action tap. Provider text is inserted and never executed. Not run on a device.
- Screenshot tile on the tools panel Quick tab. Tapping it hides the keyboard, then a small accessibility helper takes one screenshot and saves it to Pictures/Victorious Keyboard. A dialog offers Copy image. The helper requests no event types and cannot read window content. If it is off, the tile opens a setup screen that explains why and links to Accessibility settings. Android 10 and below get a message that the feature needs Android 11. Settings → Tools → Screenshot shows or hides the tile. Nothing is sent off the device, and the picture is not logged. Not run on a device.
- Password-protected snippet export. Snippets → Export asks whether to protect the file (on by default); the encrypted `snippets.vkbk` uses the clipboard backup container (PBKDF2-HMAC-SHA256, AES-256-GCM). Import detects an encrypted file and asks for the password. 20 JVM tests cover the core; the dialogs are compiled, not run on a device.
- Snippet expansion per keyboard mode (Snippets → Expansion → Per keyboard mode). Standard, Developer and Termux each choose *Space, plus Enter and Tab if on*, *Space only*, or *Off*. Defaults: Standard and Developer follow the switches; Termux is Off. The master switch still turns expansion off everywhere. The old *Expand snippets in Termux mode* switch is migrated to the Termux rule (on → follow the switches, off → Off), so nothing changes for existing users. Not run on a device.
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
- The keyboard decides snippet expansion for the active mode through one pure function, `ModeExpansionPolicy.triggers`, instead of an inline Termux check. The unused `ExpansionAttempt.blockedInTerminalMode` field is gone. Advanced developer settings now link to the per-mode rules instead of holding the Termux switch.
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
- Suggestion chips no longer leave wide gaps between words in the classic suggestion layout: each chip now fills its cell. Found from a screenshot; the fix is a layout change that has not been rendered here.
- Slash commands: `/ai`, `/shell`, and `/snip` still keep a path (`/shell ls /usr/bin`), and a later command (`/ai hello /date`) starts that command instead of being swallowed. A path on its own (`cd /usr/bin`) no longer replaces the suggestion strip with an empty command list. Case conversion keeps a decomposed accent attached to its letter. Not run on a device.
- Snippet expansion while typing (Space, and Enter or Tab when on) could not match any snippet. Since the Enter and Tab change (`f514726`, 2026-10-13), the keyboard built the expansion request without the snippet library, and the request's list defaulted to empty. The keyboard now passes the library, and the field is required, so leaving it out no longer compiles. Found by code review; not run on a device.
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
