# USB keyboard and mouse

By Victorious — Victorious Framework

## Use

Open **Settings → Tools → USB keyboard and mouse**. Connect a USB **data** cable to your PC,
select the **US English** input layout on the PC, and tap **Start USB keyboard**. Confirm the
USB change and grant root in your root manager. The helper checks ConfigFS, an active USB
controller and kernel HID support. It refuses ambiguous controllers or another active instance.

The PC sees a standard USB HID keyboard and relative mouse. No desktop application, driver
installation from this project, network connection, ADB authorization or cloud account is used.
USB HID support is described by the [Linux kernel documentation](https://docs.kernel.org/usb/gadget_hid.html).
Firmware/BIOS compatibility is **not claimed**: this is a report-ID HID device, not a boot keyboard.

- **Typing:** use your phone keyboard in the draft field, then tap **Send text**. Only the draft
  is sent, up to 256 characters. US ASCII letters, punctuation, numbers, tabs and newlines work;
  emoji/accented characters are rejected before any part of that draft is sent. Newlines send
  Enter on the PC. Changing the PC layout changes what those key usages type.
- **Special keys:** tap the key buttons for Enter, Backspace, Tab, Escape, arrows, navigation,
  F1–F12. Ctrl, Shift, Alt and Super apply once to the next key or shortcut. **Shortcuts:** select
  modifiers, enter one letter, digit or unshifted punctuation key and tap Send shortcut (for example
  Ctrl+Shift+T); **Tap modifiers** presses the selected modifiers alone (Super opens a start menu on
  most desktops). Shifted symbols are rejected as shortcut keys. Draft text uses its own case. Keys
  and modifiers are released after every stroke.
- **Mouse:** slide on the touchpad to move the pointer, tap for left click, long-press for right
  click, or use the Left/Right/Middle click buttons. Each click includes a button release.
  Slide the scroll strip or use Scroll up/down. Pointer motion is coalesced and bounded.
  Drag-and-drop, pinch, multi-touch and two-finger scrolling are not implemented.
- **Stop:** tap Stop, leave the screen, rotate the phone (the screen is recreated and the session
  ends cleanly; start again afterwards), background the app or lock the phone. Pending input is
  invalidated immediately, drafts cleared, keyboard and mouse released, the temporary gadget
  removed, and the original gadget rebound. A pending root prompt cannot silently start a later
  session after leaving: the helper requires a second START handshake from the still-active screen.

## Requirements and limitations

Android 8+ as for the rest of the app, working `su`, ConfigFS with `hid` function support,
an accessible USB Device Controller, and the Android shell's `timeout` command. Root alone
is insufficient. Vendor USB HALs and SELinux policy may prevent switching even with a supported
kernel. This code does not disable SELinux, change USB properties, patch kernels, change node
ownership/permissions, or install boot-time services.

File transfer, USB tethering, ADB and other USB functions disconnect for the session. Starting
creates a **separate temporary gadget**; the original descriptors and configuration are preserved.
The temporary gadget reuses the phone's existing VID/PID, with HID report IDs 1 (keyboard) and
2 (mouse). HID-node discovery uses the newly created character device, not an assumed `/dev/hidg0`.
Setup, write and protocol failures run the same cleanup path. HID writes are timeout-bounded;
EOF and TERM also trigger cleanup. Sudden power loss, SIGKILL of the privileged helper and USB HAL
races can still prevent confirmed restoration. If restoration is unconfirmed, disconnect the cable
and reboot the phone; the screen reports this explicitly and never claims success.

Cable removal is noticed when the next key or click is sent (the helper checks that the PC has
configured the device before every write); an idle session stays open until then or until Stop.
A key or click that was already being sent when the cable was pulled may not have been delivered.

USB input has no delivery acknowledgement from the PC application. If a cable is removed mid-draft,
some earlier keys may have reached the PC. There is no automatic resend. Inspect the PC before retrying.
A helper acknowledgement means a kernel write succeeded, not that a particular application received it.

## Privacy

Off by default; no saved enable switch, remote bridge entry point, startup receiver, new permission,
network use or telemetry. Only explicit input on this screen is sent over the USB cable to the attached
PC. The app does not capture normal IME typing, other apps, passwords or clipboard contents for USB.
The draft is memory-only, cleared after submission/backgrounding, and not included in backups.
Neither typed text nor reports are logged. The privileged protocol accepts fixed-size octal HID reports,
never shell commands or user-supplied paths.

## Report formats

Checked by decoding the helper's descriptor bytes item by item (and by a test that sums the declared input bits
per report ID), and against the HID Usage Tables as known to the author. Not checked against a live host.

| Report | ID | Bytes on the wire | Layout |
|---|---|---|---|
| Keyboard | 1 | 9 | ID, modifier bits (LCtrl=1, LShift=2, LAlt=4, LGUI=8), reserved 0, six key usages (0x00–0x65) |
| Mouse | 2 | 5 | ID, buttons (left=1, right=2, middle=4), signed X, signed Y, signed wheel; each −127..127 |

The helper writes each report to the discovered `/dev/hidg*` node as a `printf` **format** made only of
validated `\ddd` octal escapes (POSIX format escapes; `%b` needs `\0ddd` and is not portable to every
Android `sh`). The host-side tests capture the real bytes, including values above 0x7f. The usages follow
the USB HID Usage Tables (keyboard page 0x07); modifiers are usages 0xE0–0xE7. Wheel +1 is scroll up.
No LED output is used: `no_out_endpoint` is set where the kernel offers it, otherwise host output reports
are drained.

## Validation

- Eleven host-side tests run the actual shell helper against a temporary ConfigFS/UDC model:
  keyboard/mouse reports, descriptor lengths, EOF/TERM rollback, canceled startup, unsupported HID,
  malformed/wrong-sized reports, command-injection input, host disconnect, write timeout and the exact
  bytes written to the HID node.
- Kotlin tests cover US key usage mapping, modifiers/releases, whole-draft rejection, report encoding,
  relative mouse bounds/click release, and real-subprocess handshake/restoration errors.
- Core Kotlin sources compile independently against the Gradle distribution's Kotlin 2.2.20 compiler
  and bundled coroutines. The project itself pins Kotlin 2.3.0.
- Android UI compilation, the full JVM suite, packaging and lint are CI gates. See TESTING.md for the
  current run results and the GitHub write-access blocker. **No physical phone, PC USB link or rooted kernel has been validated here.**

Before calling hardware support verified, test on the intended phone: typing/case/punctuation, every
modifier and navigation key, pointer motion/clicks/scroll, cable removal mid-send, root refusal,
background/lock/rotation/force-stop, USB HAL contention and restoration of file transfer/ADB.
