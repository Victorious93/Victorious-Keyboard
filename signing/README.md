# Debug signing

`debug.keystore` is a dedicated debug-only key for Victorious Keyboard. Debug builds and the benchmark build type sign with it. Release signing does not use this file.

| Field | Value |
|---|---|
| File | `signing/debug.keystore` |
| Type | PKCS12 |
| Alias | `victorious-debug` |
| Store password | `android` |
| Key password | `android` |
| Certificate SHA-256 | `47:1E:AD:6B:3F:96:7E:F3:C8:63:EF:49:08:74:8B:3B:E3:63:F9:1D:75:0B:FD:B1:E9:28:05:BC:F1:6B:AF:4F` |

The password is the well-known Android debug password. This key is not secret, and it is not used to decide which fleet apps are trusted.

CI and a local `assembleDebug` both produce an APK signed with this certificate, so a newer debug APK installs over an older one signed with the same key.

Phones that already have a Victorious Keyboard debug build signed with a different key need one uninstall before this APK will install. Android rejects an update when the signing certificate changes.
