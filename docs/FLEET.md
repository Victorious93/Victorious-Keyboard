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

Play, pause, previous, next, rewind, and fast-forward go out as media keys. Volume down and volume up adjust the music stream and show the system volume UI. That path needs no pairing and talks to whichever player is listening. The buttons do not type into the focused app, and they do not read the field, the clipboard, or the vault.

| Call | Value |
|---|---|
| Transport | `AudioManager.dispatchMediaKeyEvent`, action down then action up |
| Play/pause | `KEYCODE_MEDIA_PLAY_PAUSE` (85) |
| Next | `KEYCODE_MEDIA_NEXT` (87) |
| Previous | `KEYCODE_MEDIA_PREVIOUS` (88) |
| Rewind | `KEYCODE_MEDIA_REWIND` (89) |
| Fast-forward | `KEYCODE_MEDIA_FAST_FORWARD` (90) |
| Volume | `AudioManager.adjustStreamVolume(STREAM_MUSIC, ADJUST_LOWER or ADJUST_RAISE, FLAG_SHOW_UI)` |

The Media tile is on the tools panel Quick tab. The same buttons can be added to the terminal key row. A blank key order leaves that row as it is.

Exact seek is optional. It is off until the user turns on File Manager seek, and off until Victorious File Manager enables its own side. The keyboard then uses the same Messenger JSON style as the fleet clients.

| Piece | Value |
|---|---|
| Packages | `ai.victorious.filemanager`, and `ai.victorious.filemanager.debug` |
| Action | `ai.victorious.filemanager.action.BRIDGE` |
| Visibility | `<queries>` names that action, not the package |
| Request | `what = 1`, Bundle key `request` |
| Reply | `what = 2`, Bundle key `response` |
| Body | `protocol_major` 1, `op`, `id` |

Commands: `media.play`, `media.pause`, `media.toggle`, `media.next`, `media.prev`, `media.seek` (`positionMs` ≥ 0), `media.seekBy` (`deltaMs`), `media.volume` (`level` 0 through 100), `media.state`.

`media.state` returns `playing`, `positionMs`, `durationMs`, `title`, and `source`. The Media tile shows `title` while this bridge is connected. The title is not logged. `error: "NO_SESSION"` means nothing is playing.

The keyboard does not auto-trust File Manager. `FleetReleaseCert.SHA256` is empty, so an empty release fingerprint never matches. Settings → Tools → Fleet link pins the certificate of the installed package. A different certificate needs a new approval. The bridge is used only when that pin is active and File Manager seek is on. Otherwise every button is a media key.

Volume buttons stay on `adjustStreamVolume` even when the bridge is on, because `media.volume` is an absolute level. Rewind and fast-forward on the bridge are `media.seekBy` of −10 000 ms and +10 000 ms. `NO_SESSION`, or a reply the keyboard cannot read, falls back to the matching media key. A seek with no key to fall back to reports that nothing is playing.

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
{"ok":false,"error":"NO_SESSION"}
```
