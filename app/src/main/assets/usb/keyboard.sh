# Copyright (C) 2026 Victorious Keyboard Contributors
# Licensed under the Apache License, Version 2.0. See LICENSE.
# Run by su -c. stdin is a bounded octal report protocol, never evaluated as commands.
# No chmod/chown, SELinux changes, property changes, network, or persistent boot configuration.
exec 2>/dev/null
fail() { echo "VK_ERROR:$1"; exit 1; }
[ "$(id -u)" = 0 ] || fail ROOT_REQUIRED
command -v timeout >/dev/null || fail TIMEOUT_REQUIRED
base=/config/usb_gadget
[ -d "$base" ] || base=/sys/kernel/config/usb_gadget
[ -d "$base" ] || fail CONFIGFS_MISSING
[ -d /sys/class/udc ] || fail UDC_MISSING
old=
udc=
for path in "$base"/*; do
    [ -f "$path/UDC" ] || continue
    bound=$(cat "$path/UDC")
    [ -n "$bound" ] || continue
    [ -z "$old" ] || fail MULTIPLE_GADGETS
    old=$path
    udc=$bound
done
[ -n "$old" ] || fail NO_ACTIVE_GADGET
[ -d "/sys/class/udc/$udc" ] || fail UDC_MISSING
# Refuse unknown names before embedding the value in a sysfs path.
case "$udc" in *[!a-zA-Z0-9._:-]*) fail UDC_MISSING;; esac
# Wait for app confirmation after root approval. Closing stdin before START never changes USB.
echo VK_AVAILABLE
IFS= read -r command || exit 0
[ "$command" = START ] || exit 0
g="$base/victorious_keyboard"
[ ! -e "$g" ] || fail STALE_GADGET
owned=0
switched=0
node=
reader=
cleanup() {
    trap - EXIT HUP INT TERM
    ok=1
    if [ -n "$node" ]; then
        timeout 1 sh -c 'printf "\001\000\000\000\000\000\000\000\000" > "$1"' sh "$node"
        timeout 1 sh -c 'printf "\002\000\000\000\000" > "$1"' sh "$node"
    fi
    [ -z "$reader" ] || { kill "$reader"; wait "$reader"; }
    if [ "$switched" = 1 ] && [ -f "$g/UDC" ] && [ -n "$(cat "$g/UDC")" ]; then
        printf '\n' > "$g/UDC" || ok=0
    fi
    if [ "$owned" = 1 ]; then
        [ ! -L "$g/configs/c.1/hid.keyboard" ] || rm "$g/configs/c.1/hid.keyboard" || ok=0
        [ ! -d "$g/functions/hid.keyboard" ] || rmdir "$g/functions/hid.keyboard" || ok=0
        [ ! -d "$g/configs/c.1/strings/0x409" ] || rmdir "$g/configs/c.1/strings/0x409" || ok=0
        [ ! -d "$g/configs/c.1" ] || rmdir "$g/configs/c.1" || ok=0
        [ ! -d "$g/strings/0x409" ] || rmdir "$g/strings/0x409" || ok=0
        rmdir "$g" || ok=0
    fi
    if [ "$switched" = 1 ]; then
        # Only restore if another USB manager has not already rebound the original.
        current=$(cat "$old/UDC")
        if [ -z "$current" ]; then
            printf '%s' "$udc" > "$old/UDC" || ok=0
        elif [ "$current" != "$udc" ]; then
            ok=0
        fi
    fi
    if [ "$ok" = 1 ]; then echo VK_STOPPED; else echo VK_RESTORE_FAILED; fi
}
trap cleanup EXIT
trap 'exit 0' HUP INT TERM
mkdir "$g" || fail GADGET_CREATE
owned=1
# Reuse this phone's existing VID/PID rather than inventing a vendor identity.
cat "$old/idVendor" > "$g/idVendor" || fail DESCRIPTOR
cat "$old/idProduct" > "$g/idProduct" || fail DESCRIPTOR
printf '0x0200' > "$g/bcdUSB" || fail DESCRIPTOR
mkdir "$g/strings/0x409" || fail DESCRIPTOR
printf 'Victorious Keyboard' > "$g/strings/0x409/product" || fail DESCRIPTOR
mkdir "$g/configs/c.1" || fail DESCRIPTOR
printf '250' > "$g/configs/c.1/MaxPower" || fail DESCRIPTOR
before=
for n in /dev/hidg*; do [ ! -c "$n" ] || before="$before $n "; done
# This creation is the actual kernel HID capability test; performed before disconnecting normal USB.
mkdir "$g/functions/hid.keyboard" || fail HID_UNSUPPORTED
printf '0' > "$g/functions/hid.keyboard/protocol" || fail DESCRIPTOR
printf '0' > "$g/functions/hid.keyboard/subclass" || fail DESCRIPTOR
printf '9' > "$g/functions/hid.keyboard/report_length" || fail DESCRIPTOR
printf '\005\001\011\006\241\001\205\001\005\007\031\340\051\347\025\000\045\001\165\001\225\010\201\002\225\001\165\010\201\003\225\005\165\001\005\010\031\001\051\005\221\002\225\001\165\003\221\003\225\006\165\010\025\000\045\145\005\007\031\000\051\145\201\000\300\005\001\011\002\241\001\205\002\011\001\241\000\005\011\031\001\051\003\025\000\045\001\225\003\165\001\201\002\225\001\165\005\201\003\005\001\011\060\011\061\011\070\025\201\045\177\165\010\225\003\201\006\300\300' > "$g/functions/hid.keyboard/report_desc" || fail DESCRIPTOR
# Avoid host LED OUT endpoint backpressure where supported; drain reports on older kernels too.
[ ! -e "$g/functions/hid.keyboard/no_out_endpoint" ] || printf '1' > "$g/functions/hid.keyboard/no_out_endpoint" || fail DESCRIPTOR
ln -s "$g/functions/hid.keyboard" "$g/configs/c.1/hid.keyboard" || fail DESCRIPTOR
# Mark first so every failure from here attempts restoration.
switched=1
printf '\n' > "$old/UDC" || fail USB_BUSY
printf '%s' "$udc" > "$g/UDC" || fail USB_BIND
attempt=0
while [ "$attempt" -lt 20 ]; do
    node=
    for n in /dev/hidg*; do
        [ -c "$n" ] || continue
        case "$before" in *" $n "*) continue;; esac
        [ -z "$node" ] || fail AMBIGUOUS_NODE
        node=$n
    done
    [ -z "$node" ] || break
    sleep 0.1
    attempt=$((attempt + 1))
done
[ -n "$node" ] || fail HID_NODE_MISSING
cat "$node" >/dev/null &
reader=$!
echo VK_READY
while IFS= read -r report; do
    [ "$report" != STOP ] || break
    case "${#report}" in 20|36) ;; *) fail PROTOCOL;; esac
    case "$report" in *[!\\0-7]*) fail PROTOCOL;; esac
    # Character filtering alone is insufficient: enforce byte boundaries and report IDs.
    remaining=$report
    count=0
    while [ -n "$remaining" ]; do
        case "$remaining" in \\[0-3][0-7][0-7]*) ;; *) fail PROTOCOL;; esac
        remaining=${remaining#????}
        count=$((count + 1))
    done
    case "$report" in
        \\001*) [ "$count" = 9 ] || fail PROTOCOL;;
        \\002*) [ "$count" = 5 ] || fail PROTOCOL;;
        *) fail PROTOCOL;;
    esac
    [ "$(cat "/sys/class/udc/$udc/state")" = configured ] || fail HOST_NOT_CONFIGURED
    # Only $report's fixed octal bytes enter printf. No command interpolation, no log of data.
    # The report becomes the printf FORMAT: validated above to contain only backslash-octal escapes (no %).
    # POSIX format escapes (\ddd) are portable to mksh/toybox/dash; %b needs \0ddd and is not.
    timeout 2 sh -c 'printf "$1" > "$2"' sh "$report" "$node" || fail WRITE_FAILED
    echo VK_OK
done
