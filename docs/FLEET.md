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

## Media keys and File Manager seek

Play, pause, previous, next, rewind, and fast-forward go out as media keys. Volume down and volume up adjust the music stream and show the system volume UI. That path needs no pairing and talks to whichever player is listening. `media.volume` is a different control: it sets the player's own volume, and only the Player quieter and Player louder buttons send it. The buttons do not type into the focused app, and they do not read the field, the clipboard, or the vault.

| Call | Value |
|---|---|
| Transport | `AudioManager.dispatchMediaKeyEvent`, action down then action up |
| Play/pause | `KEYCODE_MEDIA_PLAY_PAUSE` (85) |
| Next | `KEYCODE_MEDIA_NEXT` (87) |
| Previous | `KEYCODE_MEDIA_PREVIOUS` (88) |
| Rewind | `KEYCODE_MEDIA_REWIND` (89) |
| Fast-forward | `KEYCODE_MEDIA_FAST_FORWARD` (90) |
| Volume keys | `AudioManager.adjustStreamVolume(STREAM_MUSIC, ADJUST_LOWER or ADJUST_RAISE, FLAG_SHOW_UI)` |
| Player volume | `media.volume` with `level` 0 through 100, only from Player quieter / Player louder |

The Media tile is on the tools panel Quick tab. The same buttons can be added to the terminal key row. A blank key order leaves that row as it is.

Exact seek is optional. It is off until the user turns on File Manager seek, and off until Victorious File Manager enables its own side. The keyboard then uses the same Messenger JSON style as the fleet clients.

| Piece | Value |
|---|---|
| Packages | `ai.victorious.filemanager` (release), `ai.victorious.filemanager.debug` |
| Release certificate | none yet. The trust prompt pins whatever is installed. Never auto-trusted |
| Debug certificate | `8C:1F:E3:C9:E5:34:F2:71:05:C0:68:8F:45:28:D7:7E:47:11:AE:0A:79:DD:08:D1:E8:59:30:B9:E9:48:85:F2` |
| Release action | `ai.victorious.filemanager.action.BRIDGE` |
| Debug action | `ai.victorious.filemanager.debug.action.BRIDGE` |
| Visibility | `<queries>` names those actions, not the packages. The app does not have to be installed |
| Request | `what = 1`, Bundle key `request` |
| Reply | `what = 2`, Bundle key `response` |
| Body | `protocol_major` 1, `op`, `id` |

Commands: `media.play`, `media.pause`, `media.toggle`, `media.next`, `media.prev`, `media.seek` (`positionMs` ≥ 0), `media.seekBy` (`deltaMs`), `media.volume` (`level` 0 through 100), `media.state`.

`media.state` returns `playing`, `positionMs`, `durationMs`, `title`, and `source`. The Media tile shows `title` while this bridge is connected. The title is not logged. `error: "no_session"` means nothing is playing. `NO_SESSION` is accepted as the same idle error. `external_control_disabled` means File Manager's own external-control switch is off. The Media panel says so, and a press shows the same hint. The bridge does not run until that switch is on.

The debug package is auto-trusted only when its certificate is that published fingerprint and File Manager seek is on. A different debug certificate needs the trust prompt. The release package has no published fingerprint, so it is never auto-trusted: Settings → Tools → Fleet link shows Approve, and that approval is pinned to the certificate on the phone. A later certificate needs a new approval. The keyboard discovers either package by querying its bridge action. If neither service is installed, the query is empty and every button stays a media key. The older `ai.victorious.victorfiles` name is not accepted.

Volume keys stay on `adjustStreamVolume` even when the bridge is on. `media.volume` is the player's own absolute level and is not used for those keys. Rewind and fast-forward on the bridge are `media.seekBy` of −10 000 ms and +10 000 ms. `no_session` or `NO_SESSION`, or a reply the keyboard cannot read, falls back to the matching media key. A seek with no key to fall back to reports that nothing is playing. `external_control_disabled` falls back to the media key when there is one, and it never changes the music stream for a player-volume press.

```json
{"protocol_major":1,"op":"media.toggle","id":"m1"}
```

```json
{"protocol_major":1,"op":"media.seekBy","id":"m2","deltaMs":-10000}
```

```json
{"playing":true,"positionMs":12000,"durationMs":180000,"title":"Track","source":"file"}
```

```json
{"ok":false,"error":"no_session"}
```
