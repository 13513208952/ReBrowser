#!/usr/bin/env python3
"""Tiny synthetic ZIP fixtures; not installable APKs, no device access."""
from pathlib import Path
import struct
import tempfile
import unittest
import zipfile
from verify_abi_apks import ABIS, verify


class AbiPackageTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='rbv-abi-')
        self.addCleanup(self.temp.cleanup)
        self.apk = Path(self.temp.name) / 'synthetic.apk'

    def fixture(self, abis, machine=None, aligned=True, compressed=False, engine=True):
        with zipfile.ZipFile(self.apk, 'w') as z:
            z.writestr('AndroidManifest.xml', b'synthetic manifest shape only')
            z.writestr('classes.dex', b'synthetic dex shape only')
            for abi in abis:
                data = bytearray(64);data[:4] = b'\x7fELF';data[4] = ABIS[abi][0];data[5] = 1
                struct.pack_into('<H', data, 18, machine if machine is not None else ABIS[abi][1])
                name = f'lib/{abi}/' + ('libxul.so' if engine else 'other.so')
                info = zipfile.ZipInfo(name)
                info.compress_type = zipfile.ZIP_DEFLATED if compressed else zipfile.ZIP_STORED
                if aligned:
                    pad = (-(z.fp.tell() + 30 + len(name.encode()) + 4)) % 16384
                    info.extra = struct.pack('<HH', 0xCAFE, pad) + bytes(pad)
                z.writestr(info, data)

    def test_three_separate_shapes(self):
        for abi in ABIS:
            self.fixture([abi]);self.assertTrue(verify(self.apk, abi)['onlyExpectedAbi'])

    def test_universal_refused(self):
        self.fixture(list(ABIS))
        with self.assertRaises(AssertionError): verify(self.apk, 'arm64-v8a')

    def test_wrong_target_refused(self):
        self.fixture(['x86_64'])
        with self.assertRaises(AssertionError): verify(self.apk, 'arm64-v8a')

    def test_mislabelled_elf_refused(self):
        self.fixture(['arm64-v8a'], machine=62)
        with self.assertRaises(AssertionError): verify(self.apk, 'arm64-v8a')

    def test_unaligned_native_refused(self):
        self.fixture(['arm64-v8a'], aligned=False)
        with self.assertRaises(AssertionError): verify(self.apk, 'arm64-v8a')

    def test_compressed_native_refused(self):
        self.fixture(['arm64-v8a'], compressed=True)
        with self.assertRaises(AssertionError): verify(self.apk, 'arm64-v8a')

    def test_missing_engine_refused(self):
        self.fixture(['arm64-v8a'], engine=False)
        with self.assertRaises(AssertionError): verify(self.apk, 'arm64-v8a')


if __name__ == '__main__':
    unittest.main()
