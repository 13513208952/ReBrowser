#!/usr/bin/env python3
"""Bounded, read-only Gecko APK ABI/ELF/16KiB ZIP-alignment check; no build/install.
Does not replace APK signature, resource, R8, Android runtime or release checks.
"""
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import struct
import zipfile

ABIS = {'armeabi-v7a': (1, 40), 'arm64-v8a': (2, 183), 'x86_64': (2, 62)}


def verify(apk, expected):
    assert expected in ABIS
    apk = Path(apk)
    with zipfile.ZipFile(apk) as z, apk.open('rb') as raw:
        names = z.namelist()
        assert len(names) == len(set(names)), 'duplicate ZIP entries'
        assert 'AndroidManifest.xml' in names and 'classes.dex' in names, 'not standalone APK shape'
        for name in names:
            p = PurePosixPath(name)
            assert not p.is_absolute() and '..' not in p.parts and '\\' not in name
        libs = [i for i in z.infolist() if i.filename.startswith('lib/') and not i.is_dir()]
        observed = {i.filename.split('/')[1] for i in libs}
        assert observed == {expected}, f'expected ONLY {expected}, got {observed}'
        assert f'lib/{expected}/libxul.so' in names, 'missing Gecko native engine'
        for info in libs:
            assert len(info.filename.split('/')) == 3 and info.filename.endswith('.so')
            with z.open(info) as lib:
                header = lib.read(64)
            assert len(header) >= 20 and header[:4] == b'\x7fELF', info.filename
            assert header[5] == 1, 'unexpected ELF byte order'
            assert (header[4], struct.unpack_from('<H', header, 18)[0]) == ABIS[expected], 'ELF architecture mismatch'
            # Directly read local header: central-directory extra length can differ.
            assert info.compress_type == zipfile.ZIP_STORED, 'native library unexpectedly compressed'
            raw.seek(info.header_offset)
            local = raw.read(30)
            assert local[:4] == b'PK\x03\x04'
            filename_bytes, extra_bytes = struct.unpack_from('<HH', local, 26)
            assert (info.header_offset + 30 + filename_bytes + extra_bytes) % 16384 == 0, 'native ZIP offset not 16KiB-aligned'
    digest = hashlib.sha256()
    with apk.open('rb') as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b''):
            digest.update(chunk)
    return {'abi': expected, 'nativeLibraries': len(libs), 'bytes': apk.stat().st_size,
            'sha256': digest.hexdigest(), 'onlyExpectedAbi': True, 'nativeElfMatchesAbi': True,
            'nativeZipAlignment16KiB': True,
            'scope': 'static package check, not ELF segment alignment/signature/runtime qualification'}


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--expected', required=True, choices=sorted(ABIS))
    p.add_argument('apk', type=Path)
    args = p.parse_args()
    print(json.dumps(verify(args.apk, args.expected), indent=2))


if __name__ == '__main__':
    main()
