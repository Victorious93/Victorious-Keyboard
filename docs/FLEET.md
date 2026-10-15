# Droidcommand-AI fleet link

Victorious Keyboard stays a normal keyboard when no trusted fleet app is installed. This link is optional. The app does not request `INTERNET`.

Nothing in this document has been run on a device. The shared service action and signature permission are placeholders, marked `TODO-confirm-with-hub`. The keyboard does not contain a list of fleet apps.

## Contract

`fleet-keyboard-api` is a small standalone module other apps can copy or depend on. Its README names apps that can implement the binder. Those names are not used by discovery.

| Piece | Where | Status |
|---|---|---|
| Service action `com.victorious.fleet.KEYBOARD_PROVIDER` | `FleetApiContract.SERVICE_ACTION` | `TODO-confirm-with-hub` |
| Signature permission `com.victorious.fleet.PERMISSION` | `FleetApiContract.PERMISSION` | `TODO-confirm-with-hub` |
| Binder | `IFleetKeyboardProvider.aidl` | Shared. Providers implement it |
| Kotlin port | `FleetProvider` | What the keyboard and the unit tests call |
| Android adapter | `AidlFleetProvider` | The only class that touches the generated stub |

`app/src/main/AndroidManifest.xml` requests the permission and adds a `<queries>` intent for the service action. It does not declare the permission and it does not list packages. A unit test checks both names, the `TODO-confirm-with-hub` marker, the absence of a `<package>` query, and the absence of `INTERNET`.

The keyboard asks `PackageManager.queryIntentServices` for that action. It skips itself. It keeps a service only when `checkPermission` grants the shared permission and `apkContentsSigners` is the same set of certificates as the keyboard (API 28+ `GET_SIGNING_CERTIFICATES`; older `GET_SIGNATURES`). It binds an explicit `ComponentName`. At most 16 providers are kept. That cap is a limit, not an allowlist.

Package add, remove, and replace rescan. Opening the tools panel and changing fields also rescan. A dead binder marks that one app disconnected and leaves the others in place.

## When features show

| State | Strip commands | Ask, actions, snippets | Status row |
|---|---|---|---|
| Switch off, or no trusted provider | Hidden | Hidden | Hidden |
| Trusted app installed, `ping()` false or bind failed | Hidden for that app | Hidden for that app | That app, grey dot |
| `ping()` true | Advertised names, namespaced on a clash | A section per connected app under Terminal keys | That app, green dot |

Settings → Fleet link is the switch (`fleet__enabled`, default on). The default does not connect to anything until some same-signed app exports the service and holds the permission. If nobody has defined the permission yet, `checkPermission` is denied and every fleet feature stays hidden.

Slash names match `[a-z][a-z0-9_-]{0,31}`. A namespace matches `[a-z][a-z0-9]{0,15}`, taken from `getNamespace()`, otherwise a slug of the display name, otherwise the package suffix. A second app that wants the same token gets a numeric suffix (`edl`, then `edl2`).

When two connected apps advertise `status`, the strip shows `/edl:status` and `/swarm:status`. A name only one app advertises stays `/status`, unless the keyboard already uses that name (`/date`, a custom command, and the other built-ins). Typing the raw name still lists the namespaced copies. The words after a qualified name are the argument. The provider receives the raw name, not the `edl:` prefix.

## What is sent

`FleetPolicy` decides.

* Password fields: nothing. The slash matcher returns before it reads the field, and Ask refuses.
* Slash commands: the words typed after the command, sent only to the app that advertised it. Clipboard text and vault items are not attached.
* Ask and advertised actions: the current selection, and only because the user tapped that app's button. A vault selection is sent on that tap. An automatic path cannot send vault text (`explicitVaultPick` is false).
* Snippets: the keyboard calls `getSnippets()` with no user text. Tapping a snippet inserts its text. If the snippet has a command name and no text, `runCommand` asks that provider for a string and that string is inserted.

Each send and each reply is capped at 8,000 characters. Binder calls run on a daemon thread named `fleet-hub`, with a two-second wait. A timeout, a dead provider, or a bad payload leaves the keyboard usable. The text is not logged. Returned strings are inserted and never executed.

## Provider documents

Provisional JSON, described in `fleet-keyboard-api/README.md`. A document longer than 64 KB, or one the parser cannot read, becomes an empty list. An icon must be a PNG of at most 48 KB. Anything else is dropped and the row has no icon.

```json
[{"name":"status","description":"Status"}]
```

```json
[{"name":"flash","label":"Flash"}]
```

```json
[{"id":"adb1","label":"devices","kind":"adb","text":"adb devices"}]
```

`kind` is `adb`, `fastboot`, `termux`, or anything else (shown as other). At most 40 slash commands are shown in total, and at most 40 snippets and 40 actions per app.

## Tests

`lib/fleet` unit tests use two `FakeFleetProvider` instances. They cover a missing provider, a switched-off link, a failed ping beside a live app, `/edl:status` plus `/swarm:status`, a unique name left unqualified, a reserved `/date` that gets a namespace, a repeated namespace token, permission and certificate rejection, hostile JSON, a non-PNG icon, password and vault refusal, and snippet text that is inserted with `execute == false`. The app test checks the strip for a shared name and checks that the manifest and client sources call `queryIntentServices` without naming fleet apps or requesting `INTERNET`.
