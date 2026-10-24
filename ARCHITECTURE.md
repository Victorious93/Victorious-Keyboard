# Architecture

Describes the code **as it is** (upstream FlorisBoard `v0.6.0-alpha02` + our changes) and the decisions for where new
functionality goes. Facts here were read from the source; see `AUDIT.md` for the evidence trail.

## 1. Module layout (actual)

```
:app            Android application; namespace com.victorious.keyboard (≈223 Kotlin files)
:lib:android    Android helpers (version checks, content resolver, system settings, …)
:lib:color      Color model
:lib:compose    Shared Compose UI pieces
:lib:kotlin     Pure-Kotlin utilities (no Android)
:lib:snygg      Theme/style engine ("Snygg") used to style the IME and the app
:lib:native     Rust+CMake JNI placeholder (dummyAdd) — to be removed in Phase 1 (AUDIT R5)
```

### Decision: no new Gradle modules for now

The brief proposes `core-*`/`feature-*` modules and says to introduce modules only where they pay for themselves.
Today they would not: upstream's IME, clipboard, NLP, theme and preferences code is tightly coupled through
`FlorisApplication` lazies and `FlorisImeService`, and splitting it would be a large risky refactor with no way to
run the keyboard here to catch regressions.

Instead, **new functionality is added as packages in `:app` with a pure-Kotlin core that has no Android imports**,
so it is unit-testable on the JVM (the only test surface available in the sandbox):

```
com.victorious.keyboard.ime.clipboard.history   (P3) history policy, search, categorisation   [pure Kotlin]
com.victorious.keyboard.ime.clipboard.backup    (P3) manifest, JSON/ZIP codecs, crypto, import planner [pure Kotlin + JCE]
com.victorious.keyboard.ime.terminal            (P4) key-sequence mapping, modifier state machine [pure Kotlin]
com.victorious.keyboard.ime.developer           (P4) extra-key rows, profiles
com.victorious.keyboard.ime.snippets            (P5) templates, expansion engine
com.victorious.keyboard.ime.ai                  (P7) prompt library, local transforms, provider adapters
```

A package is promoted to a Gradle module only when (a) it has no dependency on `:app` internals and (b) the build-time
or isolation benefit is demonstrated. The AI provider layer (P7) is the most likely first candidate because it must be
provably isolated from key handling.

## 2. Runtime structure (actual)

* `FlorisApplication` — process entry. Holds `lazy` singletons: `ClipboardManager`, `EditorInstance`,
  `ExtensionManager`, `GlideTypingManager`, `KeyboardManager`, `NlpManager`, `SubtypeManager`, `ThemeManager`,
  `CacheManager`. Preferences are a Jetpref `PreferenceStore` (`AppPrefs`) loaded asynchronously
  (`preferenceStoreLoaded`).
* `FlorisImeService : LifecycleInputMethodService` — the `InputMethodService`. Renders Compose UI
  (`ime/text/TextInputLayout`, `ime/smartbar`, `ime/clipboard/ClipboardInputLayout`, `ime/media`).
* `ime/editor/EditorInstance` — the only code that talks to `InputConnection`: commit/compose/delete, selection,
  context-menu actions, and `sendDownUpKeyEvent` (real `KeyEvent`s with `META_*` state).
* `ime/keyboard/KeyboardManager` (≈1000 lines) — keyboard state (`KeyboardState`), layout resolution
  (`LayoutManager`, `ComputingEvaluator`), and handling of key presses; `ime/input/InputEventDispatcher` handles
  repeat/long-press timing.
* `ime/text/key/KeyCode` — key code space (positive = Unicode, negative = special: `CTRL=-1`, `ALT=-3`, arrows,
  `ESCAPE=27`, `TAB=9`, …); layouts are JSON under `assets/ime/keyboard/org.florisboard.layouts`, loaded as
  extensions (`ime/extension`, `lib/ext`).
* `ime/nlp/NlpManager` — pluggable `NlpProvider`s (`latin/LatinLanguageProvider`, `han/…`). Latin suggestions come from
  `ime/nlp/prediction` (bundled packs, optional on-device learning). `spell()` is still the upstream test stub.
* `ime/text/gestures` — swipe actions and glide typing (`StatisticalGlideTypingClassifier`).
* `ime/clipboard` — `ClipboardManager` (listens to the system clipboard, owns history), `provider/ClipboardDatabase`
  (Room, plain SQLite), `provider/ClipboardMediaProvider` (a `ContentProvider` for stored images).
* `ime/theme` + `:lib:snygg` — theme extensions and style resolution.
* `app/` — the settings app (Compose + Navigation, `app/Routes.kt`, `app/settings/**`), setup wizard, extension
  manager UI, dev tools.

## 3. Principles for new code (binding)

1. **The keyboard works with everything optional switched off.** Optional features are lazily constructed and every
   call from the hot path into them is wrapped so a failure degrades to "feature unavailable", never a crash.
2. **No blocking I/O on the IME main thread.** Database, crypto, file and network work run on `Dispatchers.IO` /
   `Default`; the UI observes `Flow`/`StateFlow`.
3. **Pure core, thin shell.** Logic that can be pure Kotlin is pure Kotlin and tested on the JVM; Android classes
   (`InputConnection`, `ClipboardManager`, SAF URIs) stay in small adapters.
4. **AI/network code is isolated.** Nothing in `ime/ai` is reachable from key handling, text capture or clipboard
   monitoring. The manifest declares `INTERNET` for one opt-in feature only, the pack update check (`ime/packs`,
   one class, `UrlConnectionTransport`, opens the connection; `NetworkUseTest` pins that). A provider adapter (P7) would
   need its own owner-approved change and a visible declaration (see `AI_INTEGRATION.md`).
5. **Sensitive data stays explicit.** No logging of clipboard/typed content (not even in debug), no analytics, no
   background network.
6. **Schema changes are migrations.** No `fallbackToDestructiveMigration()` on any user-data database.

## 4. Decisions log

| # | Decision | Reason |
|---|---|---|
| D1 | Base = FlorisBoard `v0.6.0-alpha02`, merged with history | `AUDIT.md` §2 |
| D2 | Kotlin namespace is `com.victorious.keyboard`. `applicationId` stays `com.victorious.keyboard` (debug `.debug`, bench `.bench`) | Don approved the rename after pull request #55. Preference keys, the dictionary file, extension ids, and fleet action strings stay. The IME, spell checker, screenshot helper, and launcher alias keep their previous component names |
| D3 | Remove `:lib:native` | `AUDIT.md` R5 |
| D4 | No new Gradle modules yet | §1 |
| D5 | Crypto for backups: JCE only — `AES-256-GCM` with a key from `PBKDF2WithHmacSHA256`, per-file random salt and nonce | No new dependency; widely reviewed primitives; available on API 26. (Argon2 is stronger against GPUs but needs a native/3rd-party lib; revisit in P9) |
| D6 | At-rest encryption of the clipboard DB: Android Keystore-wrapped key, not a user password | Must work without prompting on every keyboard start; trade-offs in `SECURITY.md` (P3) |
