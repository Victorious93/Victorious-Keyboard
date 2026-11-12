#!/usr/bin/env python3
"""Exercise the production shell helper against a temporary ConfigFS/UDC model.

This validates sequencing/protocol/rollback, not a real kernel, USB HAL or SELinux.
No root or hardware is used, and all path substitutions are in a temporary copy.
"""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

HELPER = Path(__file__).resolve().parents[2] / 'app/src/main/assets/usb/keyboard.sh'


class UsbHelperTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.base = self.root / 'gadgets'
        self.original = self.base / 'phone'
        self.original.mkdir(parents=True)
        for name, value in {'UDC': 'test.udc', 'idVendor': '0x1234', 'idProduct': '0x5678',
                            'keep_me': 'unchanged'}.items():
            (self.original / name).write_text(value)
        self.udc = self.root / 'udc/test.udc'
        self.udc.mkdir(parents=True)
        (self.udc / 'state').write_text('configured')
        (self.root / 'dev').mkdir()
        bin_dir = self.root / 'bin'
        bin_dir.mkdir()
        self.shim(bin_dir, 'id', '#!/bin/sh\necho 0\n')
        self.shim(bin_dir, 'mkdir', '''#!/usr/bin/env python3
import os, pathlib, sys
p = pathlib.Path(sys.argv[1])
if p.name == 'hid.keyboard' and os.environ.get('VK_TEST_FAIL') == 'hid': sys.exit(1)
p.mkdir()
if p.name == 'victorious_keyboard':
    for group in ['configs', 'functions', 'strings']: (p/group).mkdir()
    (p/'UDC').touch()
if p.name == 'hid.keyboard':
    (pathlib.Path(os.environ['VK_TEST_ROOT'])/'dev/hidg0').symlink_to('/dev/null')
''')
        self.shim(bin_dir, 'rmdir', '''#!/usr/bin/env python3
import pathlib, sys
p = pathlib.Path(sys.argv[1])
# ConfigFS attribute files and default groups disappear with their owning item.
for child in p.iterdir():
    if child.is_file() and not child.is_symlink(): child.unlink()
    elif child.name in ['functions', 'configs', 'strings'] and child.is_dir(): child.rmdir()
p.rmdir()
''')
        self.env = dict(os.environ, PATH=str(bin_dir) + ':' + os.environ['PATH'], VK_TEST_ROOT=str(self.root))
        self.script = HELPER.read_text().replace('/config/usb_gadget', str(self.base))
        self.script = self.script.replace('/sys/kernel/config/usb_gadget', str(self.base))
        self.script = self.script.replace('/sys/class/udc', str(self.root / 'udc'))
        self.script = self.script.replace('/dev/hidg', str(self.root / 'dev/hidg'))

    @staticmethod
    def shim(directory, name, text):
        p = directory / name
        p.write_text(text)
        p.chmod(0o755)

    def start(self):
        p = subprocess.Popen(['sh', '-c', self.script], env=self.env,
                             stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True)
        self.addCleanup(lambda: p.poll() is None and p.kill())
        self.addCleanup(p.stdout.close)
        self.addCleanup(lambda: not p.stdin.closed and p.stdin.close())
        return p

    def activate(self):
        p = self.start()
        self.assertEqual(p.stdout.readline().strip(), 'VK_AVAILABLE')
        p.stdin.write('START\n')
        p.stdin.flush()
        self.assertEqual(p.stdout.readline().strip(), 'VK_READY')
        self.assertEqual((self.original / 'UDC').read_text().strip(), '')
        self.assertEqual((self.base / 'victorious_keyboard/UDC').read_text(), 'test.udc')
        return p

    def stop_and_check(self, p):
        p.stdin.close()
        remaining = p.stdout.read()
        p.wait(timeout=8)
        self.assertIn('VK_STOPPED', remaining)
        self.assertEqual((self.original / 'UDC').read_text(), 'test.udc')
        self.assertEqual((self.original / 'keep_me').read_text(), 'unchanged')
        self.assertFalse((self.base / 'victorious_keyboard').exists())
        return remaining

    def test_keyboard_mouse_reports_and_eof_restore_original(self):
        p = self.activate()
        for report in [r'\001\000\000\004\000\000\000\000\000',
                       r'\002\000\177\201\001']:
            p.stdin.write(report + '\n')
            p.stdin.flush()
            self.assertEqual(p.stdout.readline().strip(), 'VK_OK')
        self.stop_and_check(p)

    def test_exact_bytes_reach_the_hid_node(self):
        # Point the discovered node at a regular file so the written bytes can be compared. A report's
        # escapes must become the raw bytes, including values above 0x7f (negative mouse movement).
        node = self.root / 'dev/hidg0'
        p = self.activate()
        node.unlink()
        node.write_bytes(b'')
        cases = {
            r'\001\002\000\004\000\000\000\000\000': bytes([1, 2, 0, 4, 0, 0, 0, 0, 0]),
            r'\002\005\177\201\377': bytes([2, 5, 127, 129, 255]),
        }
        for report, expected in cases.items():
            p.stdin.write(report + '\n')
            p.stdin.flush()
            self.assertEqual(p.stdout.readline().strip(), 'VK_OK')
            self.assertEqual(node.read_bytes(), expected)
        p.stdin.close()
        p.stdout.read()
        p.wait(timeout=8)

    def test_invalid_bytes_cannot_be_executed_and_restore_usb(self):
        p = self.activate()
        marker = self.root / 'injected'
        p.stdin.write(f'$(touch {marker})\n')
        p.stdin.flush()
        self.assertIn('VK_ERROR:PROTOCOL', self.stop_and_check(p))
        self.assertFalse(marker.exists())

    def test_wrong_report_id_or_length_fails_and_restores(self):
        for report in [r'\003\000\000\000\000', r'\001\000\000\000\000',
                       r'\002\000\000\000\000\000\000\000\000',
                       '0' * 36]:
            with self.subTest(report=report):
                p = self.activate()
                p.stdin.write(report + '\n')
                p.stdin.flush()
                self.assertIn('VK_ERROR:PROTOCOL', self.stop_and_check(p))
                (self.root / 'dev/hidg0').unlink()

    def test_canceled_root_confirmation_does_not_change_usb(self):
        p = self.start()
        self.assertEqual(p.stdout.readline().strip(), 'VK_AVAILABLE')
        p.stdin.close()
        p.wait(timeout=3)
        self.assertEqual((self.original / 'UDC').read_text(), 'test.udc')
        self.assertFalse((self.base / 'victorious_keyboard').exists())

    def test_unsupported_kernel_is_detected_before_original_is_unbound(self):
        self.env['VK_TEST_FAIL'] = 'hid'
        p = self.start()
        self.assertEqual(p.stdout.readline().strip(), 'VK_AVAILABLE')
        p.stdin.write('START\n')
        p.stdin.flush()
        output = self.stop_and_check(p)
        self.assertIn('VK_ERROR:HID_UNSUPPORTED', output)

    def test_host_disconnect_rejects_send_and_restores(self):
        p = self.activate()
        (self.udc / 'state').write_text('not attached')
        p.stdin.write(r'\002\000\000\000\000' + '\n')
        p.stdin.flush()
        self.assertIn('VK_ERROR:HOST_NOT_CONFIGURED', self.stop_and_check(p))

    def test_descriptor_input_lengths_match_keyboard_and_mouse_reports(self):
        p = self.activate()
        descriptor = (self.base / 'victorious_keyboard/functions/hid.keyboard/report_desc').read_bytes()
        size = count = report_id = 0
        bits = {}
        offset = 0
        while offset < len(descriptor):
            prefix = descriptor[offset]
            length = [0, 1, 2, 4][prefix & 3]
            data = int.from_bytes(descriptor[offset+1:offset+1+length], 'little')
            if prefix & 0xfc == 0x74: size = data
            if prefix & 0xfc == 0x94: count = data
            if prefix & 0xfc == 0x84: report_id = data
            if prefix & 0xfc == 0x80: bits[report_id] = bits.get(report_id, 0) + size * count
            offset += length + 1
        self.assertEqual(bits, {1: 64, 2: 32})
        self.assertEqual((self.base / 'victorious_keyboard/functions/hid.keyboard/report_length').read_text(), '9')
        self.stop_and_check(p)

    def test_term_signal_restores_original(self):
        p = self.activate()
        p.terminate()
        self.stop_and_check(p)

    def test_write_timeout_still_restores_usb(self):
        self.shim(self.root / 'bin', 'timeout', '#!/bin/sh\n[ "$1" != 2 ] || exit 124\nexec /usr/bin/timeout "$@"\n')
        p = self.activate()
        p.stdin.write(r'\002\000\000\000\000' + '\n')
        p.stdin.flush()
        self.assertIn('VK_ERROR:WRITE_FAILED', self.stop_and_check(p))

    def test_existing_gadget_is_never_removed(self):
        g = self.base / 'victorious_keyboard'
        g.mkdir()
        (g / 'keep_me').write_text('unchanged')
        p = self.start()
        self.assertEqual(p.stdout.readline().strip(), 'VK_AVAILABLE')
        p.stdin.write('START\n')
        p.stdin.flush()
        p.stdin.close()
        self.assertIn('VK_ERROR:STALE_GADGET', p.stdout.read())
        p.wait(timeout=3)
        self.assertEqual((g / 'keep_me').read_text(), 'unchanged')
        self.assertEqual((self.original / 'UDC').read_text(), 'test.udc')


if __name__ == '__main__':
    unittest.main()
