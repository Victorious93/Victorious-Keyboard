# Architecture and gap assessment against the master brief

Written 2026-10-12. Scope: this repository (`Victorious93/Victorious-Keyboard`, confirmed by the owner as the target of
the "VictorKeys master brief"). Everything marked **verified** was read from code, built, or queried this session;
**not verified** means nothing here has run on an Android device.

## 1. What the repository is

A modified distribution of FlorisBoard `v0.6.0-alpha02` (Apache-2.0, upstream credit kept, see `LICENSES.md` and the
About screen). Kotlin + Compose, single `:app` module plus `:lib:*`, Kotlin namespace `dev.patrickgold.florisboard`,
application id `com.victorious.keyboard`. New features live in `ime.*` packages with a pure-Kotlin core and a thin
Android shell (`CLAUDE.md`).

## 2. Existing components relevant to the brief

| Capability | State | Evidence |
|---|---|---|
| IME service, key dispatch, editor access (`FlorisImeService`, `KeyboardManager`, `EditorInstance`) | inherited | `sendDownUpKeyEvent(code, metaState)` exists; no Ctrl/Alt handling existed before Phase 4 |
| Layout JSON, themes (Snygg), preferences (jetpref) | inherited | settings screens, enum display entries |
| Clipboard manager: 500 cap, pinning, search, encrypted store, backup | implemented, unit-tested, **not run on device** | `CLIPBOARD.md`, `BACKUP_FORMAT.md` |
| Terminal key mapper, Ctrl/Alt state machine, developer row UI, symbol rows for 7 languages | implemented, unit-tested, **UI never rendered** | `TERMINAL.md` |
| Snippets: template engine, expansion rules, library, import/export, file store | pure core, unit-tested, **not wired, no UI** | `SNIPPETS.md` |
| Suggestions / autocorrect / NLP | inherited, status **not verified**; upstream's dictionary support is minimal at this version | `AUDIT.md` |
| Mode system | 3 of 6 modes exist as presets (see §5, `docs/DEVELOPER_KEYBOARD.md`); Claude Code / Kali / NetHunter AI do not | code |
| Termux `RUN_COMMAND` integration, command execution, AI providers, command catalog, audit log | **do not exist** | grep |

## 3. Security posture (verified from `AndroidManifest.xml`)

* Permissions: `VIBRATE`, `POST_NOTIFICATIONS`. **No `INTERNET`**, no storage, no accessibility, no Termux permission.
* Exported components: the input-method service (guarded by `BIND_INPUT_METHOD`), the spell-checker service (guarded by
  `BIND_TEXT_SERVICE`), the launcher alias, the extension-import activity (VIEW/SEND filters) and a share-target
  activity. All content providers are `exported="false"`.
* `allowBackup="true"` with `backup_rules.xml` including `jetpref_datastore`, `ime/` files and the user-dictionary
  database. **Consequence for the brief:** provider settings, API keys, snippets, execution audit logs and any
  pairing secrets must **not** go into the preference datastore or `files/ime`; use `noBackupFilesDir` and/or the
  Android Keystore (CLAUDE.md invariant 7).
* Any future component that accepts command requests (companion, receiver, service) must be non-exported or
  permission-protected with caller validation. None exists today.

## 4. CI status (verified, important)

The GitHub Actions workflow has run **four times and failed every time** (runs 1-4: PR and push events, 2026-10-12),
always at dependency verification, before compiling anything. Root cause found and fixed in `c92c878` (plugin-marker
checksums recorded through a mirror differed from what the Gradle Plugin Portal serves; details in `BUILD.md`).
**Run 5 (after the fix) passed the build and unit tests on GitHub's runner**; it was red only at the artifact upload
("storage quota has been hit", a GitHub account limit), so no CI-built APK exists yet. Local runs and run 5 now agree.

## 5. Gap assessment by brief phase

Dependency order is the order that avoids rework, not the brief's numbering.

| Brief phase | Gap | Depends on | Next concrete step |
|---|---|---|---|
| 1 Architecture, mode manager | **started**: `OperatingMode` (Standard/Developer/Termux available; Claude Code/Kali/NetHunter AI planned, not selectable), presets, opt-in app detection (`docs/DEVELOPER_KEYBOARD.md`); missing: quick switch from the keyboard, per-mode autocorrect off | prefs, developer row | quick mode switch; per-mode settings |
| 2 Termux keyboard | special keys + symbol row done; missing: Insert/Del keys in the row, multi-line paste protection, shell escaping helper, `RUN_COMMAND` | snippets shell, modes | snippet shell; then verify the current `RUN_COMMAND` contract from Termux's documentation before writing any code |
| 3 Claude Code keyboard | no slash-command palette; no catalog | snippet/template engine (done), modes | provider-adapter interface + user-editable command list (no hardcoded "complete" list) |
| 4 Kali knowledge system | nothing | importer design, licensing review of Kali docs | catalog schema + importer from package metadata; no bundled third-party manuals |
| 5 NetHunter integration | nothing | terminal execution component (phase 7) | capability-detection model with explicit "why unavailable" |
| 6 AI assistant | nothing; no `INTERNET` permission by design | secure storage, execution policy | provider interface; permission added only with the opt-in feature |
| 7 Companion / secure execution | nothing | pairing + caller-identity design | threat model document before any code |
| 8 Developer functions | pair keys exist as models; no auto-pairing, indentation, per-mode autocorrect off | modes | per-mode settings in the mode manager |
| 9 Clipboard / workflows | clipboard done (unrun); workflows missing | snippets, execution policy | workflows reuse the same authorization path as single commands |
| 10 Local knowledge | nothing; Room is already a dependency | catalog, snippets | decide after measuring |
| 11 UI | developer row exists; palette/AI/approval UIs missing | everything above | per feature |
| 12 Testing | 461 local JVM tests; no instrumented or device tests | device access | owner runs `TESTING.md` checklist |
| 13 Docs | partly in place | | keep updating per change |
| 14 CI / release | CI exists but was red since the first run | | prove green; debug APK artifact |

## 6. Risks and blockers

1. **No device or emulator** here: key events into Termux, IME window sizing with the extra row, scroll-vs-tap, Keystore
   behaviour, Room migration and clipboard guards are all unvalidated.
2. **Termux integration is unverified.** `RUN_COMMAND` requires a Termux-side opt-in and a runtime permission; both
   must be confirmed against the current Termux documentation before implementation (not done).
3. **Execution and root features** (phases 5-7) need a written threat model and the approval flow before code. They are
   not started and will not be started without that design.
4. **Catalog licensing:** do not bundle third-party man pages or Kali documentation without checking licences; generate
   from package metadata and link to official docs.
5. **Upstream drift:** the base is an alpha tag; upstream's input engine is being rewritten, so merging upstream later
   will be costly.
6. **Product name (decided):** the owner confirmed the product and repository are **Victorious Keyboard**. The brief's
   "VictorKeys" name is not a rename request; app name, applicationId and docs stay as they are. (`Victorious93/VictorKeys`
   is a separate public fork and is not part of this project.)
