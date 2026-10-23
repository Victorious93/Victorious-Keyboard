# Roadmap

Status legend: ✅ done and verified as stated · 🟡 partially done · ⬜ not started · ⛔ blocked.
"Verified" means compiled and JVM-tested in the reference environment. **Nothing has been run on an Android device
yet** (no device/emulator available; see `AUDIT.md` §5), so no phase is marked "device-validated".

Upstream's own roadmap is preserved at `docs/upstream/ROADMAP.md`.

## Phase queue

Started by the owner's words "continue with phase implementation" (procedure in `CLAUDE.md`, "Working method"). One
pull request per item. Items are listed in the order they should be built; the owner may reorder them.

| # | Item | Status | Notes |
|---|---|---|---|
| 1 | Snippets: expansion on Enter and Tab (the core already supports more boundaries, `ExpansionSettings.boundaries`) | done | JVM-tested (`ExpansionAction`). Each key is its own opt-in (both off by default). Never in sensitive fields. A non-match does not swallow Enter/Tab. Keyboard wiring compiled, **not run on a device**. That wiring never passed the snippet library, so typed expansion (Space too) matched nothing until PR #30 fixed it; found by code review. |
| Toolbar | Customizable tools panel and suggestion-strip pins (owner request, 2026-10-19, outside the numbered queue). The tools button opens the panel. Long-press it, or Settings → Smartbar → Tools panel, to hide or reorder tiles, pick tile size and columns, reset, and pin up to four tools on the suggestion strip. Nothing pinned leaves the strip as suggestions and the tools button. Slash commands and fleet actions can be tiles or pins. The layout is in the clipboard ZIP as `toolbar.json`. The earlier default row of eight buttons above the suggestions is no longer drawn. | strip pins in the toolbar-layout change (not merged); the panel and the chip-gap fix shipped in PR #40 | JVM-tested. Settings, the strip, and the panel compiled, **never rendered or run on a device**. |
| Screenshot | One-tap screenshot tile (owner request, 2026-10-17, outside the numbered queue). A narrow accessibility helper takes the picture after the keyboard hides, and saves it to Pictures/Victorious Keyboard. | implemented | Policy JVM-tested. Settings → Tools shows or hides the tile. No `INTERNET`. Compiled, **not run on a device**. |
| UI | UI pass (owner request, 2026-10-15, outside the numbered queue): tabbed tools panel on theme elements, one terminal-row decision, theme-following AI and clip-editor panels, Settings Home regrouped with Developer & terminal and Snippets → Expansion screens | done (PR #29, merged 2026-10-15) | Pure parts JVM-tested (31 tests). Compiled; **never rendered or run on a device**. `assembleDebug` could not finish in the sandbox (llama.cpp tarball blocked by egress policy); GitHub CI run `38668379528` on `029e087` passed the full job (APK, unit tests, lint). Next UI steps, not started: a JVM render loop (Paparazzi or Roborazzi) to see Compose screens without a device, responsive/tablet layout, translations of the new strings. |
| 2 | Snippets: per-mode defaults (e.g. expansion on/off per operating mode) | done (PR #30, merged 2026-10-16) | CI green (`assembleDebug`, unit tests, `:lib:kotlin:test`, `lintDebug`). `ModeExpansionPolicy` (JVM-tested): per mode Follow / Space only / Off; defaults Standard and Developer Follow, Termux Off; the master switch always wins. The old "Expand snippets in Termux mode" switch migrates to the Termux rule without changing behaviour (JVM-tested by loading a stored preference file through jetpref). Also fixes typed expansion never matching (the keyboard passed no snippet library since item 1). Settings: Snippets → Expansion → Per keyboard mode. Keyboard wiring compiled, **not run on a device**. Per-mode prefix or keep-switches are not done. |
| 3 | Snippets: encrypted export, reusing the clipboard backup container | done (PR #34, merged 2026-10-17) | CI green on the PR head (`assembleDebug`, unit tests, lint). `SnippetEncryptedBackup` (JVM-tested, 20 tests): wraps `BackupCrypto` around the snippets JSON; no new cryptography, container version unchanged. Export and import dialogs in Snippets compiled, **not run on a device**. |
| 4 | Dictionary packs: device validation and a decision on the word lists (verify against official sources) | blocked on owner | The lists were written from memory; verification needs network access the sandbox does not have. |
| 5 | Termux `RUN_COMMAND` integration | blocked on owner | Not started. Do not begin unless the owner explicitly asks (see `CLAUDE.md`). |
| Remote | Frozen fleet remote-control envelope v1 (owner request, 2026-10-20, outside the numbered queue). `capabilities.get`, `remote.command`, `macro.step`, `macro.run`, `node.status.get`, `config.export`, and `config.import` on the existing bridge. | implemented (PR #55) | Local gate on `4916cf06` EXIT 0 in 5m 4s: 1014 tests, 0 failures (`:app` 730, `:lib:fleet` 64, `:lib:snygg` 109, `:lib:kotlin` 111), lint 0 errors, 227 warnings, 2 hints. `ConfigBackupTest` is 4. `RemoteEnvelopeTest` is 15. `node.status.get` returns node health only (`EXTERNAL_NODE`, application id, `node_health` ok or down). It is a read, remote-allowed, and not confirmation-gated. `config.export` is an action, not remote-allowed, and not confirmation-gated. `config.import` is an action, not remote-allowed, and confirmation-gated. A remote caller gets `capability_off`. The local backup is themes, layouts, and keyboard settings, including the toolbar layout. It does not include typed text, clipboard history, Vault data, or snippet bodies. Dry-run writes nothing. A confirmed import uses skip, overwrite, or merge-by-id and forces external control off. Android shell compiled, **not run on a device**. Exact media seek is `not_paired` here; the media-keys pull request is separate. |
| Local AI actions | Shorter, explain a command, generate a command (owner request, 2026-10-20). | implemented | On-device only, on the selected text, preview before insert. Rewrite and Fix grammar were already present. Offline. JVM-tested. The panel is compiled, **not run on a device**. |
| Cursor | Precision cursor and selection (owner request, 2026-10-20). | implemented | Space-bar drag moves the caret. Long-press then drag selects. Selection toolbar: word, line, all, cut, copy, paste, Home, End, jump by word. Haptic tick per step. Offline. JVM-tested. The toolbar is compiled, **not run on a device**. |
| Profiles | Per-app profiles and command history (owner request, 2026-10-20). | implemented | Presets for Termux, NetHunter Terminal, JuiceSSH, and code editors: autocorrect off, terminal row on. Layout and theme overrides restore the previous values. Command history is opt-in, off by default, last 30 lines, terminal apps only, excluded from password fields and incognito, clearable. Offline. JVM-tested. The panels are compiled, **not run on a device**. |
| Text Tools | Text Tools tile (owner request, 2026-10-20). Preview, then Replace, Insert, or Copy. Selection, or the clipboard when nothing is selected. | implemented | Extends `TextTransforms`. Offline. JVM-tested. The panel is compiled, **not run on a device**. |
| Namespace | Rename the code namespace from `dev.patrickgold.florisboard` to `com.victorious.keyboard` (Don approved 2026-10-20). | done | `applicationId` stays `com.victorious.keyboard`, `.debug`, and `.bench`. The IME service, spell checker, screenshot helper, and launcher alias keep the previous component names, so an update does not turn them off. Preference keys, the user dictionary file, extension ids, and fleet action strings are unchanged. JNI symbols match the moved `LlamaNative` class. Apache-2.0 FlorisBoard attribution stays. Not run on a device. |
| 6 | AI control of the keyboard through the Hub bridge (owner request, 2026-10-19) | done (PR #44, merged 2026-10-19) | CI green on the PR head `ac4b7b9` and on the merge-ready head `279fa9d` (`build` job: `assembleDebug`, unit tests, `:lib:kotlin:test`, `lintDebug`; run `39299472483`). `BridgeInput` (`:lib:fleet`, pure Kotlin, JVM-tested incl. wrong caller, switch off, oversize, bad input, sensitive field): `keyboard.input.text` and `keyboard.input.key`; text and Enter, Tab, arrows, Ctrl+letter only. Same switch and certificate allowlist as `docs/BRIDGE.md`; no reading, no toolbar buttons; refused in password-like fields. Android shell (`ExternalInputPort`, service wiring) compiled, **not run on a device**. No rate limit or on-screen indicator yet. |
| 7 | Music buttons: play/pause, next, previous, rewind, fast-forward, volume (owner request, 2026-10-19; media-keys task 2026-10-20) | implemented (PR #54) | Local gate on `20c3e688` EXIT 0 in 4m 46s: 1004 tests, 0 failures (`:app` 739, `:lib:fleet` 45, `:lib:snygg` 109, `:lib:kotlin` 111), lint 0 errors, 227 warnings, 2 hints. `MediaControl` is pure Kotlin and JVM-tested with a fake sink: media-key down then up, music-stream volume, File Manager bridge only when seek is on and that certificate is approved, `NO_SESSION` falls back to the key. Packages are `ai.victorious.filemanager` and `.debug` (the earlier `ai.victorious.victorfiles` name is not the bridge and is not required). Debug is pinned to SHA-256 `8C:1F:E3:C9:E5:34:F2:71:05:C0:68:8F:45:28:D7:7E:47:11:AE:0A:79:DD:08:D1:E8:59:30:B9:E9:48:85:F2`. Release has no certificate yet, so it uses the trust prompt and is never auto-trusted. Each package is discovered by its own bridge action. The Media tile, the optional terminal-row keys, and the approval row are compiled, **not run on a device**. |
| 8 | Packs 1: one source of truth for links (owner request, 2026-10-19: own redirects, add-ons, language and dictionary packs, plugins) | implemented (PR pending) | **Done in this PR:** `ProjectUrls` has seven pack links (`PACKS`, `EXTENSIONS`, `THEMES`, `LAYOUTS`, `LANGUAGE_PACKS`, `DICTIONARY_PACKS`, `COMMAND_PROVIDERS`), all inside this repository's `packs/` folder. The theme, keyboard-extension, language-pack, dictionary-pack and fleet screens have a Get more button that opens the browser; new local extensions default their homepage and issue tracker to the project's. `ProjectUrlsTest` fails on any link whose host is not the owner's repository (the Unicode table link is the one named exception) and on a pack link whose page is missing. `packs/*.md` are stub pages saying nothing is published yet; item 9 builds the real catalog. Compiled, **not rendered or run on a device**. Original scope: `ProjectUrls` gains the URLs for extensions, themes, layouts, language packs, dictionary packs and command providers; each list screen gets a Get more button; extension homepage and issue tracker default to them; a test fails on any link whose host is not the owner's. No network, no behaviour change except where taps go. |
| 9 | Packs 2: the catalog and the formats | implemented (PR pending) | `packs/index.json` (empty: nothing is published) and `docs/PACKS.md`, which replaces the `LANGUAGEPACKS.md` stub. Format `victorious-pack-catalog` v1: types theme, layout, language-pack, dictionary-pack (url, sha256, sizeBytes) and command-provider (packageName, certSha256); own ids `com.victorious.*`, built-ins stay `org.florisboard.*`. `PackCatalogReader` (pure Kotlin, JVM-tested incl. failure paths): size, entry count, format/version, per-entry checks (https release-asset URL on this repository only, lower-case SHA-256, size bounds, duplicate ids, no control characters); a bad entry is dropped and counted, never repaired. A test validates the repository's own `index.json` and that the docs name the format and every type. Nothing downloads or installs from it: no `INTERNET` yet (item 10). The catalog is unsigned (item 14). The language-pack file format is specified with item 12. Not run on a device. |
| 10 | Packs 3: opt-in update check (needs `INTERNET`) | queued | **Owner approved relaxing security invariant 3 for this feature on 2026-10-19**; `CLAUDE.md` and `PRIVACY.md` change in this PR, not before. Off by default; a manual Check now button, no background polling; one fixed HTTPS host allowlist for the owner's catalog; size cap and timeouts; no cookies, identifiers, typed text, clipboard or snippet content ever sent; no new dependencies (`HttpURLConnection`); cleartext traffic blocked. The index is untrusted input. A pack is still imported only when the user taps. |
| 11 | Packs 4: dictionary pack import | queued | Import a dictionary pack zip with the existing limits and format, so the owner's own packs install like the five bundled ones; catalog entries link to them. |
| 12 | Packs 5: language packs | queued | Format spec and import path for language packs. The existing language pack code has to be read first; only its asset folder has been looked at so far. |
| 13 | Packs 6: plugins (extension zips and command-provider apps) | queued | The catalog lists provider apps with package name and signing-certificate SHA-256. Listing never trusts a provider: trust stays one certificate-pinned approval per app, as in the fleet protocol. Dynamic code loading is not planned. |
| 14 | Packs 7 (optional): catalog signing | queued | A signing key pinned in the app, checked on the index, because a remote index plus `INTERNET` is a supply-chain risk. Decide after item 10 is on a device. |
| 15 | Vault 1: password manager core (owner request, 2026-10-19; owner delegated the design) | queued | Pure Kotlin, JVM-tested, no new dependencies. Entries: title, username, password, app package or URL, notes, optional TOTP secret, tags, timestamps. Encrypted file in `noBackupFilesDir`: the existing `BackupCrypto` container (AES-256-GCM, PBKDF2-HMAC-SHA256 600 000 iterations, random salt and nonce, authenticated header) around versioned JSON, unlocked by a master password the app never stores; auto-lock timeout; bounded sizes. Password and passphrase generator on `SecureRandom`; TOTP on JCE HMAC. Never logged. The vault never captures anything typed: entries come from the user, the generator or an import. |
| 16 | Vault 2: KeePass import and export | queued | Import KeePass CSV and KeePass 2.x XML exports (untrusted input: size and entry caps, no entity expansion, preview and dedupe before anything is written, the plaintext file is the user's to delete and the app says so). Export to KeePass-style CSV, and to the encrypted container. **Direct `.kdbx` import is not planned:** [Likely] current KDBX files default to Argon2, which is not in the JDK, and invariant 5 allows only JCE primitives; adding an Argon2 library is the owner's call. |
| 17 | Vault 3: in-keyboard panel | queued | A Passwords button for the toolbar and tools panel; search; the entry for the current app is listed first (package name from the editor); tap inserts username, password or the current TOTP code with `commitText`, never through the clipboard; settings screens that show secrets are `FLAG_SECURE`. **Clarification of invariant 2, proposed by this item and open to the owner's veto in its PR:** the vault may insert the user's own entry into a password field after an explicit tap; capturing from password fields stays forbidden. The invariant text is edited in this PR, not before. |
| 18 | Email aliases 1: generator on the owner's own domain | queued | Offline, no new permission. The owner sets one domain with a catch-all forward (for example Cloudflare Email Routing); the keyboard generates random addresses on it (`word-word-1234@domain`) from `SecureRandom`, remembers a label per alias, and offers one in email fields. Without a domain set, the feature says so and does nothing. No tracker stripping: that needs a mail service. |
| 19 | Email aliases 2 (optional): a provider's API | queued | addy.io or SimpleLogin through the user's own API key, stored encrypted. Needs `INTERNET` and its own opt-in and host allowlist; the update-check approval (item 10) does not cover it. DuckDuckGo's Email Protection has no public API [Likely], so it cannot be integrated. |
| 20 | Vault 4 (optional): quick unlock, Autofill service, `.kdbx` | queued | Unlock with the device lock through a Keystore-wrapped key, as the clipboard Vault does; evaluate an Android Autofill service (app and domain matching is stronger than a keyboard can do); decide on an Argon2 library for `.kdbx`. Each is the owner's call. |

Completed phases of this queue are recorded in `CHANGELOG.md` and in the status tables above.

| Phase | Name | Status |
|---|---|---|
| 0 | Repository audit and baseline | ✅ (docs below; baseline built + 176 tests pass) |
| 1 | Project foundation | ✅ (builds, JVM tests, dependency verification proven from a cold Gradle home through a mirror; CI green through build + unit tests on GitHub, including a run that stored the APK; artifact upload stays non-fatal if the storage quota is full; `lintDebug` is a gate after the inherited lint errors were fixed — see `AUDIT.md` §8) |
| 2 | Stable keyboard engine | ⬜ (needs device; engine is upstream's) |
| 3 | Advanced clipboard | 🟡 core ✅ JVM-tested, including regular/vault routing, detection, expiry, and export exclusion. Storage/UI 🟦 compiled, **not run on a device**; the vault credential prompt is not run |
| 4 | Developer and terminal modes | 🟡 pure core ✅ JVM-tested; wiring and developer-row UI 🟦 compiled, **never run or rendered** (see `TERMINAL.md`) |
| 5 | Snippets and productivity | 🟡 pure core ✅ JVM-tested; Android shell (store, expansion on space and opt-in Enter/Tab, settings screen, import/export, in-keyboard picker panel) 🟦 compiled, **never run or rendered** (see `SNIPPETS.md`) |
| 6 | Predictive and gesture typing | 🟡 word prediction (current word, next word, one-edit autocorrect) is JVM-tested and wired into the suggestion bar; learning is off by default and the file is not backed up. Gesture typing is still not done. Nothing has been run on a device (`DICTIONARY_PACKS.md`) |
| 7 | AI workspace | 🟡 on-device GGUF import and llama.cpp JNI: pure rules JVM-tested, settings and toolbar compiled, **not run on a device**. No `INTERNET` permission. No model is bundled. Network providers are not started. |
| 8 | Themes and responsive UI | 🟡 default keyboard theme is Victorious Black (day and night); Victorious Neon and Neon Light stay selectable. Settings use a navy/amber/pink Material scheme, dark by default. Keyboard colors overlay those themes and are JVM-tested; the screen is compiled and not run on a device. UI pass (merged, PR #29): the tools panel uses theme elements and the AI panel and clip editor follow the keyboard theme, so light themes and Keyboard colors reach them; Settings Home regrouped. Never rendered. Responsive/tablet layout not started |
| 9 | Security and performance | ⬜ |
| 10 | Release engineering | ⬜ |

## Phase 0 — Audit and baseline ✅

* ✅ Upstream inspected; three candidate bases built/evaluated; `v0.6.0-alpha02` chosen (`AUDIT.md`).
* ✅ Licence review (`LICENSES.md`); AI-policy implication recorded.
* ✅ Baseline: `assembleDebug` succeeds, 176 unit tests pass.
* ✅ Docs: `AUDIT.md`, `ARCHITECTURE.md`, `LICENSES.md`, `BUILD.md`, `ROADMAP.md`, `CLAUDE.md`, `README.md`.
* Open: emoji/Han data licence provenance (`LICENSES.md` §6).

## Phase 1 — Foundation

* application id `com.victorious.keyboard`; user-visible name/branding/icon; remove `:lib:native`
* build variants (`debug`, `release`; drop `beta`/`benchmark` unless needed); version scheme
* Gradle dependency verification
* CI: PR validation (compile, unit tests, lint), debug APK artifact
* issue/PR templates adapted from upstream; `CONTRIBUTING.md`, `CHANGELOG.md`
* exit: compiles, debug APK produced, CI config in place

## Phase 2 — Stable engine

Engine is upstream's. Work here is verification: registration/selection, typing, password fields, rotation, app
switching. Requires a device; until then it is a documented manual checklist (`TESTING.md`).

## Phase 3 — Advanced clipboard (highest priority)

Pure-Kotlin core first (policy, search, backup codecs, crypto, import planner), then Room schema v5 with explicit
migration, then UI. Requirements and acceptance criteria are those in the project brief; design in `CLIPBOARD.md` and
`BACKUP_FORMAT.md` (written with the code).

## Phases 4–10

As in the project brief. Notable decisions already taken:

* P4: modifier→`KeyEvent` mapping is a pure function with a test table; Termux/SSH behaviour is device-test-only.
* P6: suggestions are a pure predictor over the bundled packs, with learning off unless the user opts in. Gesture typing is still not done. `spell()` is still a stub.
* P7: no `INTERNET` permission until a provider adapter is enabled by the user. The on-device GGUF path does not add that permission. The user imports a `.gguf`; llama.cpp `b11624` runs locally. Network providers are not started.
* P10: no production signing material in the repo; debug builds are never published as releases.

## Phase 3 — status detail

Done and JVM-tested: retention policy, search, categoriser, credential detector, backup formats (JSON/ZIP/encrypted),
hostile-file-safe reader, import planner (replace/merge/skip, exact cap preview), export/restore service with
consent and rollback semantics, backup library model, AES-GCM value cipher, password-field detection.

Compiled but **never run**: Android Keystore key provider, Room v5 + `ClipboardStore`, `ClipboardManager` rewrite,
Clipboard manager screen, Backup/restore screen with SAF pickers, keyboard panel favourite action.

Open before Phase 3 can be called complete:
* Run it on a device: persistence across restarts, Keystore behaviour, password-field copy guard, SAF export/import,
  responsiveness with 500 long entries (measure; set thresholds).
* Instrumented Room migration test (4→5).
* Typed search inside the keyboard clipboard panel (needs an IME-internal text input; deliberately not attempted
  blind).
* Image/URI clips in backups (v1 is text-only by design).

## Phase 4 — status detail

Done and JVM-tested: key+modifier → `KeyEvent` mapper (US-layout table, constants checked against
`android.view.KeyEvent`), Ctrl/Alt sticky/lock/hold state machine, developer row and language-profile models.

Compiled but **never run**: `KeyboardManager` handling of Ctrl/Alt/Esc/Tab key codes and the key-event path for typed
characters.

Also compiled, never run: Advanced developer settings screen, developer row composable, settings group, observable Ctrl/Alt state, Ctrl/Alt on the
keyboard's arrow keys.

Open: seeing the row on a real screen (sizing, window height, theming, scroll-vs-tap), all Termux/SSH behaviour on a
device, per-app automatic profile, translations.

## Phase 5 — status detail

Done and JVM-tested: template language and renderer, expansion matcher (opt-in, never in sensitive fields, no
recursion), snippet library with validation and unique abbreviations, versioned JSON import/export with a
non-destructive importer, atomic file store with `.bak`/`.corrupt` safety copies. Details and limits: `SNIPPETS.md`.

Android shell compiled, never run: `SnippetManager` (store under `noBackupFilesDir`), expansion on the space key and, each
opt-in, on Enter and Tab, Snippets settings screen with import/export, in-keyboard picker (quick action, panel,
`{{selection}}`), per-mode expansion rules (`ModeExpansionPolicy`, JVM-tested; settings under Snippets → Expansion).
Open: encrypted export, typed search in the picker, device validation (including Enter/Tab expansion and the per-mode
rules, which are compiled and JVM-tested but not run on a device).

## Mapping to the "VictorKeys master brief"

A longer brief (written for "VictorKeys"; the owner confirmed Victorious Keyboard is the correct project) uses different
phase numbers. Where its topics land in this repository's plan:

| Master brief phase | Here |
|---|---|
| 1 Architecture / mode manager | not started; `ime.terminal`, `ime.developer` and the developer row are the first pieces |
| 2 Termux keyboard (special keys, symbol row, shell snippets, `RUN_COMMAND`) | special keys/symbol row = our Phase 4 (done, unrun); snippets/templates = Phase 5 (core done); `RUN_COMMAND` integration not started |
| 3 Claude Code keyboard, 4 Kali catalog, 5 NetHunter, 6 AI assistant, 7 terminal companion | not started (our Phase 7+); the template engine here is a prerequisite for prompt/command templates |
| 8 Developer functions (pairing, indentation, snippets) | pair *keys* exist; auto-pairing/indentation not started; snippets = Phase 5 |
| 9 Clipboard and workflows | clipboard = Phase 3 (done, unrun); workflows not started |
| 10 Knowledge / token efficiency, 11 UI, 12–14 tests, docs, CI | partly covered by existing docs/CI; the rest not started |

Nothing from the brief beyond what is listed in `FEATURES.md` exists in this repository. The owner confirmed that this
repository (Victorious Keyboard) is the target; the "VictorKeys" name in the brief is not a rename.
