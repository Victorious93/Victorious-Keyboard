# Droidcommand-AI fleet link

Victorious Keyboard stays a normal keyboard when the hub is not there. This link is optional. The app does not request `INTERNET`.

Nothing in this document has been run on a device. The hub's real package name, service action, AIDL, and signature permission are not invented here. The constants are placeholders (`todo.confirm.with.hub` and the two names under it) because a `<queries>` package must be a Java package name. The source is marked `TODO-confirm-with-hub`.

## Contract

`lib/fleet` is the module that can be swapped for the hub's own binder.

| Piece | Where | Status |
|---|---|---|
| Package name, service action, signature permission | `FleetHubContract` | `TODO-confirm-with-hub` |
| Binder methods `askHub`, `runCommand`, `getSnippets`, `ping` | `IFleetHub.aidl` | Provisional. Replace this file when the hub publishes AIDL |
| Kotlin port of those four methods | `FleetHub` | What the keyboard and the unit tests call |
| Android adapter | `AidlFleetHub` | The only class that touches the generated stub |

`app/src/main/AndroidManifest.xml` requests the permission and adds a `<queries>` package plus a `<queries>` intent action. Those three `android:name` values must stay identical to `FleetHubContract`. A unit test counts them. The keyboard does not declare the permission and does not set `protectionLevel`. The hub owns the signature permission. If the signatures differ, the bind fails and the keyboard keeps working.

`ACTION_LIST_COMMANDS` (`list-commands`) and `ACTION_ASK` (`ask`) are also marked `TODO-confirm-with-hub`. They are actions passed to `askHub`, not the service intent action.

## When features show

| State | Strip commands | Ask hub and snippets | Status dot |
|---|---|---|---|
| Switch off, or hub package absent | Hidden | Hidden | Hidden |
| Package installed, `ping()` false or bind failed | Hidden | Hidden | Shown, labeled "Hub off" |
| `ping()` true | `/build`, `/status`, `/note`, plus advertised names | Shown under Terminal keys | Shown, labeled "Hub" |

Settings → Fleet link is the switch (`fleet__enabled`, default on). The default does not connect to anything until `FleetHubContract` names a package that is actually installed.

Advertised command names use `[a-z][a-z0-9_-]{0,31}`. Names already used by a built-in or custom slash command are dropped, so the hub cannot replace `/date` or `/shell`. `/note hello` keeps `hello` as the argument only while those hub names are connected. A space still ends `/date`.

## What is sent

`FleetPolicy` decides.

* Password fields: nothing. The slash matcher returns before it reads the field, and Ask hub refuses.
* Slash commands: the words typed after the command. Clipboard text and vault items are not attached.
* Ask hub: the current selection, and only because the user tapped the button. A vault selection is sent on that tap. An automatic path cannot send vault text (`explicitVaultPick` is false).
* Snippets: the keyboard calls `getSnippets()` with no user text. Tapping a snippet inserts its text. If the snippet has a command name and no text, `runCommand` asks the hub for a string and that string is inserted.

Each send and each reply is capped at 8,000 characters. Binder calls run on a daemon thread named `fleet-hub`, with a two-second wait. A timeout, a dead hub, or a bad payload leaves the keyboard usable. The text is not logged.

## Snippets and commands documents

Provisional JSON, also `TODO-confirm-with-hub`. A document longer than 64 KB, or one the parser cannot read, becomes an empty list. Built-in `/build`, `/status`, and `/note` still show after a bad command list if `ping()` succeeded.

```json
[{"name":"deploy","description":"Deploy"}]
```

```json
[{"id":"adb1","label":"devices","kind":"adb","text":"adb devices"}]
```

`kind` is `adb`, `fastboot`, `termux`, or anything else (shown as other). At most 40 snippets and 40 advertised commands are kept.

## Tests

`lib/fleet` unit tests use `FakeFleetHub`. They cover a missing hub, a failed ping, advertised commands, a shadowed `/date`, hostile JSON, password and vault refusal, and snippet text that is inserted with `execute == false`. The app test checks that `/note hello` joins the strip only when hub commands are passed in, and that the manifest names the placeholder three times and does not request `INTERNET`.
