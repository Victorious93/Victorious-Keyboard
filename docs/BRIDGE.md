# Hub bridge

Victorious Keyboard hosts a small service for the Droidcommand hub. It answers status questions and, for an allowed caller with the switch on, types text and presses keys in the focused field. It does not use a signature permission or AIDL. It never reads the field's contents and never forwards what the user typed. Nothing here has been run on a device.

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

Operations: `keyboard.capabilities.get`, `keyboard.status`, `keyboard.input.text` and `keyboard.input.key`.

Remote-control envelope v1 uses this same service and the same allowlist. Its operations are `capabilities.get`, `remote.command`, `macro.step`, `macro.run`, `node.status.get`, `config.export`, and `config.import`. Those operations never type into the focused app. `node.status.get` reports node health only. `config.export` and `config.import` are advertised with `remoteAllowed` false, so a remote caller cannot dump or restore settings. The contract is in [FLEET.md](FLEET.md). The four operations above are unchanged.

## Input operations

Both need the same allowlist and the same switch as the status operations (below). There is no second switch. They are the only ways to change the editor; there is no operation that reads it and none that runs a toolbar button.

```json
{"protocol_major":1,"op":"keyboard.input.text","text":"ls -la\n"}
{"protocol_major":1,"op":"keyboard.input.key","key":"enter"}
{"protocol_major":1,"op":"keyboard.input.key","key":"c","ctrl":true}
{"protocol_major":1,"op":"keyboard.input.key","key":"left","ctrl":true,"count":3}
```

| Field | Rule |
|---|---|
| `text` | 1 to 2,000 characters. Printable characters plus `\n` and `\t`. NUL, other control characters and unpaired surrogates are refused. Inserted at the caret as typed text |
| `key` | `enter`, `tab`, `up`, `down`, `left`, `right` (case-insensitive), or one letter `a`..`z` only together with `"ctrl":true`. A bare letter is text, not a key |
| `ctrl` | optional boolean, default false. The only modifier. `alt`, `shift` and `meta` are refused |
| `count` | optional, 1 to 20, default 1 |
| `id` | optional, up to 64 characters, accepted and ignored (never echoed) |

A whole request is at most 16,000 characters. A request with an unknown field, a wrong type (a quoted number, a number for `text`) or another `protocol_major` is refused rather than interpreted. The reply is the usual envelope with `ok` and `error`; it never repeats the text or the key.

| `error` | Meaning |
|---|---|
| `unauthorized_caller` | caller not on the allowlist (checked first, switch on or off) |
| `external_control_disabled` | allowed caller, switch off |
| `request_too_large` | request over 16,000 characters, or `text` over 2,000 |
| `invalid_request` | bad JSON, unknown field, wrong type, missing field, key not allowed, `count` out of range |
| `unsupported_op` | unknown `op` |
| `keyboard_unavailable` | the keyboard service is not running |
| `no_active_field` | no input connection is attached |
| `sensitive_field` | the focused field is password-like (text, visible, web or numeric password). Nothing is sent. `EXTRA_IS_SENSITIVE` belongs to clipboard items and has no editor counterpart; the field test is the same `isPasswordLike()` the clipboard and snippet expansion use |
| `input_failed` | Android refused the edit, or the keyboard threw. Nothing is retried |

Ctrl combinations go to the app as key events with an explicit Ctrl meta state; the on-screen Ctrl or Shift the user may hold does not leak in. Text and keys are acted on the main thread, one request at a time, in the order they arrive. Not done: rate limiting (a caller that is allowed and enabled can send many small requests), a visible indicator while the bridge types, and acting only while a particular app is in front.

## Security

External control is off by default. Settings → Tools → Fleet link → Hub status is the switch.

The service reads the UID Messenger stamped on the message while the binder call was still running (`Message.sendingUid`). The handler runs later on the main looper, so reading `Binder.getCallingUid()` there would see this process and refuse every hub. A message with no stamp is refused. It then reads the packages for that UID and the SHA-256 of each package's signing certificate. A caller is allowed only when every package on that UID is `ai.droidcommand.app` or `ai.droidcommand.app.debug`, and each of those packages has a signing certificate.

| Caller | Switch | Error |
|---|---|---|
| Anyone else | either | `unauthorized_caller` |
| An allowed hub package | off | `external_control_disabled` |
| An allowed hub package | on | no error; `ok` is true for the two operations |

A reply never contains the editor contents.
