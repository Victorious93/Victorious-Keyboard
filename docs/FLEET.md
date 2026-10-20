# Droidcommand-AI fleet link

Victorious Keyboard stays a normal keyboard when no command provider is connected. This link is optional. The app does not request `INTERNET`. Nothing here has been run on a device.

The keyboard owns this protocol. Any of Don's fleet apps can implement it. There is no shared signature permission and no AIDL. The keyboard does not contain a list of fleet apps.

## Discovery

| Piece | Value |
|---|---|
| Intent action | `com.victorious.keyboard.action.COMMAND_PROVIDER` |
| Lookup | `PackageManager.queryIntentServices`, plus a `<queries>` entry for that action |
| Binding | Explicit `ComponentName`, `Context.bindService` |
| Transport | `Messenger` |
| Request | `what = 1`, Bundle key `request`, JSON string |
| Reply | `what = 2`, Bundle key `response`, JSON string |
| Cap | 16 providers. That cap is a limit, not an allowlist |

The keyboard skips its own package. Package add, remove, and replace rescan. A provider that disappears is unbound. That failure does not stop the keyboard.

## Messages

`protocol_major` is `1`. A reply with another major version, `ok: false`, or a body the parser cannot read is ignored. Bodies longer than 64 KB are ignored. Nothing in a request or a reply is logged.

`commands.list` asks for slash names, labels, and snippets. It sends no typed text.

```json
{"protocol_major":1,"op":"commands.list","id":"1"}
```

```json
{"protocol_major":1,"op":"commands.list","id":"1","ok":true,"title":"EDL","namespace":"edl",
 "commands":[{"name":"status","label":"Status"}],
 "snippets":[{"name":"devices","label":"devices","text":"adb devices"}]}
```

`command.run` asks for text to insert. The keyboard inserts that text and does not execute it.

```json
{"protocol_major":1,"op":"command.run","id":"2","name":"status","argument":"hello"}
```

```json
{"protocol_major":1,"op":"command.run","id":"2","ok":true,"text":"inserted text"}
```

Slash names match `[a-z][a-z0-9_-]{0,31}`. `namespace` matches `[a-z][a-z0-9]{0,15}`. When it is missing, the keyboard slugs the provider title, then the package suffix. A second app that wants the same token gets a numeric suffix (`edl`, then `edl2`).

When two connected apps advertise `status`, the strip shows `/edl:status` and `/swarm:status`. A name only one app advertises stays `/status`, unless the keyboard already uses that name. The words after a qualified name are the argument. The provider receives the raw name, not the `edl:` prefix.

## Trust

Settings → Tools → Fleet link lists every discovered app with its name and the SHA-256 of its first signing certificate.

| Certificate | Before the user acts | After |
|---|---|---|
| Same as the keyboard **release** key | On | The switch can turn it off, and back on, without a new approval |
| Anything else | Off, with an Approve button | One approval pins that certificate. The switch can then turn it off and on |
| Certificate changed | Treated as a new app | The old approval does not carry over |

A debug build of the keyboard is not the release key. Auto-trust uses `FleetReleaseCert.SHA256` when that constant is set. Until release signing exists the constant is empty, so a debug build auto-trusts nobody. A release build (`com.victorious.keyboard`, no `.debug` suffix) uses its own signing certificate as the release certificate.

The master switch still hides every fleet feature. Each provider has its own switch on top of that.

## What is sent

`FleetPolicy` decides.

* Password fields: nothing. The slash matcher returns before it reads the field, and Ask refuses.
* `commands.list`: no typed text, no clipboard, no vault.
* Slash commands: the words typed after the command, and only because the user tapped that command. Clipboard text and vault items are not attached.
* Ask and a command tile: the current selection, and only because the user tapped that tile. A vault selection is sent on that tap.
* Snippets with text: inserted locally. A snippet with no text calls `command.run` and inserts the reply.

Each send and each reply is capped at 8,000 characters. Calls run on a daemon thread named `fleet-hub`, with a two-second wait. A timeout or a dead provider leaves the keyboard usable. One request is in flight at a time. A reply is used only when its `id` is the id of that request, so a late reply cannot fill in the next command. A reply with no `id` is ignored.

## Provider sketch

```xml
<service android:name=".CommandProvider" android:exported="true">
    <intent-filter>
        <action android:name="com.victorious.keyboard.action.COMMAND_PROVIDER"/>
    </intent-filter>
</service>
```

`onBind` returns a `Messenger` binder. Handle `what == 1`, read `request`, and reply to `msg.replyTo` with `what == 2` and `response`. Do not require a signature permission.

## Hub status bridge

The keyboard also hosts a separate status service for the Droidcommand hub. That service is off until the user turns on Hub status. It answers `keyboard.capabilities.get` and `keyboard.status` only to `ai.droidcommand.app` and `ai.droidcommand.app.debug`. It does not forward typed text. See [BRIDGE.md](BRIDGE.md).

## Remote-control envelope v1

`BRIDGE_PROTOCOL_MAJOR` is 1 and `BRIDGE_PROTOCOL_MINOR` is 1. The service and the action are the same as the hub bridge: `KeyboardBridgeService`, `com.victorious.keyboard.action.BRIDGE`, Messenger `what = 1` / `what = 2`. External control is off until Hub status is on.

The caller is authorized only by UID, then package, then certificate. The allowlist is still `ai.droidcommand.app` and `ai.droidcommand.app.debug`. Fields inside the JSON are never an identity. A missing UID is `unauthorized`. The switch off is `external_control_disabled`.

Top-level fields are flat: `op`, `requestId`, `targetNodeRef`, `actionRef`, `params`, `nonce`, `timestampMs`, `ttlMs`, `destructive`, `trigger`. Optional `protocol_major` and `protocol_minor` must be 1 when they are present. `targetNodeRef` and `trigger` are informational. `ttlMs` defaults to 120000 and cannot be above 300000.

`op` is one of `capabilities.get`, `remote.command`, `macro.step`, `macro.run`.

`macro.step` adds `macroId`, `stepIndex`, and `onError` (`abort` or `continue`). `macro.run` adds `macroId`, `steps`, and `dryRun`. A step object is `actionRef`, `params`, and optional `destructive` and `onError`. `dryRun` checks the steps and does not perform them and does not show a prompt.

The nonce is 128 bits, 32 hex digits, dashes allowed. A request is expired when `now > timestampMs + ttlMs`, or when the timestamp is more than 120000 ms away from now. A nonce is remembered for the authenticated package for 10 minutes. A repeat in that window is `replay_detected`, including a repeat of an expired request. Two packages do not share a nonce.

`capabilities.get` returns, for each action, `name`, `kind` (`read`, `action`, or `destructive`), `remoteAllowed`, and `requiresConfirm`.

`remote.command` may use:

| actionRef | Effect |
|---|---|
| `keyboard.status` | read: whether the keyboard is up, the mode name, the day theme id |
| `keyboard.mode` | `params.mode` of `standard`, `developer`, or `termux` |
| `keyboard.theme` | `params.theme` of `org.florisboard.themes:` plus a component id |
| `media.play`, `media.pause`, `media.toggle` | media key play/pause, down then up |
| `media.next`, `media.prev`, `media.rewind`, `media.fast_forward` | the matching media key, down then up |
| `media.volume` with `direction` `up` or `down` | music-stream volume, system UI |
| `media.seek`, `media.seekBy`, `media.state`, `media.volume` with `level` | `not_paired` in this build. Exact seek is the File Manager bridge, which is a separate change |

`keyboard.input.text` and `keyboard.input.key` as an `actionRef` are `unsupported_op`. The new envelope never calls the input port. Those two names still work as the old operations, with their old replies.

`destructive: true`, or a capability whose kind is `destructive`, returns `confirmation_required`. The keyboard shows an on-device notice and does not run the action. Nothing in this catalog is auto-approved. A dry run reports the same error and does not show the notice.

Other errors: `invalid_parameters`, `not_found`, `internal`, `capability_off`.

```json
{"op":"remote.command","requestId":"r1","actionRef":"media.next","params":{},
 "nonce":"00112233445566778899aabbccddeeff","timestampMs":1700000000000,"ttlMs":120000}
```

```json
{"op":"macro.run","requestId":"r2","macroId":"m1","dryRun":true,"nonce":"aabbccddeeff00112233445566778899",
 "timestampMs":1700000000000,"steps":[{"actionRef":"keyboard.mode","params":{"mode":"developer"},"onError":"abort"}]}
```
