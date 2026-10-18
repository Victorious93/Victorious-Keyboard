# Hub status bridge

Victorious Keyboard hosts a small status service for the Droidcommand hub. It does not use a signature permission or AIDL. It does not read or forward typed text or keystrokes. Nothing here has been run on a device.

## Service

| Piece | Value |
|---|---|
| Class | `com.victorious.keyboard.fleet.KeyboardBridgeService` |
| Intent action | `com.victorious.keyboard.action.BRIDGE` |
| Exported | yes, with no permission attribute |
| Transport | `Messenger` |
| Request | `what = 1`, Bundle key `request`, JSON |
| Reply | `what = 2`, Bundle key `response`, JSON |

The envelope on every reply is:

| Field | Value |
|---|---|
| `protocol_major` | `1` |
| `live` | true only when an allowed caller is enabled and the keyboard service is up |
| `mode` | `live` or `stub` |
| `dca_required` | `false` |
| `fleet_class` | `external_node` |

Operations: `keyboard.capabilities.get` and `keyboard.status`.

## Security

External control is off by default. Settings → Tools → Fleet link → Hub status is the switch.

The service reads `Binder.getCallingUid()`, then the packages for that UID, then the SHA-256 of each package's signing certificate. A caller is allowed only when every package on that UID is `ai.droidcommand.app` or `ai.droidcommand.app.debug`, and each of those packages has a signing certificate.

| Caller | Switch | Error |
|---|---|---|
| Anyone else | either | `unauthorized_caller` |
| An allowed hub package | off | `external_control_disabled` |
| An allowed hub package | on | no error; `ok` is true for the two operations |

A reply never contains the editor contents.
