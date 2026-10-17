# fleet-keyboard-api

Small standalone binder contract for Droidcommand-AI apps that want a tile inside Victorious Keyboard. The keyboard does not ship a list of apps. It discovers every installed package that exports this service, holds the shared signature permission, and is signed with the keyboard's certificate.

Copy this module into a fleet app, or depend on the AAR. Implement `com.victorious.fleet.IFleetKeyboardProvider`. EDL-Commander, Pentest-Swarm-AI, VictorLink64, VictorSuite, and Rootforge-OS are examples of apps that can implement it. None of those names are required, and the keyboard does not look for them.

The service action and permission below are provisional.

TODO-confirm-with-hub

## Service

```xml
<uses-permission android:name="com.victorious.fleet.PERMISSION"/>

<service
    android:name=".FleetKeyboardService"
    android:exported="true"
    android:permission="com.victorious.fleet.PERMISSION">
    <intent-filter>
        <action android:name="com.victorious.fleet.KEYBOARD_PROVIDER"/>
    </intent-filter>
</service>
```

One app signed with the same certificate defines the permission:

```xml
<permission
    android:name="com.victorious.fleet.PERMISSION"
    android:protectionLevel="signature"/>
```

Sign the fleet app with the same certificate as Victorious Keyboard. The keyboard rejects any other signer, and it rejects a package that has not been granted the permission.

## Methods

| Method | What the keyboard does with it |
|---|---|
| `ping()` | Connected when this returns true |
| `getProviderName()` | Section title and status row |
| `getNamespace()` | Short token such as `edl`, used only when two apps advertise the same slash name |
| `getIcon()` | Optional PNG, at most 48 KB. Empty is fine |
| `getSlashCommands()` | JSON array of `{name, description}` |
| `getSnippets()` | JSON array of `{id, label, kind, text, command, args}` |
| `getActions()` | JSON array of `{name, label}` |
| `ask(action, text)` | Inserts the returned string. `action` is empty for the Ask button, or an advertised action name |
| `runCommand(name, args)` | Used only when a snippet has no `text`. The returned string is inserted |

`kind` is `adb`, `fastboot`, `termux`, or anything else. Slash names match `[a-z][a-z0-9_-]{0,31}`. A namespace matches `[a-z][a-z0-9]{0,15}`.

When two connected apps advertise `status`, the strip shows `/edl:status` and `/swarm:status` from their namespaces. A name only one app advertises stays `/status`, unless the keyboard already uses that name.

## Privacy

The keyboard does not request Internet. It does not send password fields. Vault text is sent only when the user taps Ask or an action for that app. Slash commands send the words typed after the command, not the clipboard. Do not expect the keyboard to execute `runCommand` or any string you return. Bind calls are off the keyboard's main thread. The keyboard does not log your payloads.
