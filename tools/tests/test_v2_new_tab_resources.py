#!/usr/bin/env python3
"""Offline SOURCE artwork/provenance checks, not Android rendering or brand authorization."""
import hashlib
import json
from pathlib import Path
import struct
import subprocess
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
MANIFEST = ROOT / 'planning/geckoview-v2/records/native-new-tab-resource-provenance.json'


def webp_dimensions(data):
    if data[:4] != b'RIFF' or data[8:12] != b'WEBP' or struct.unpack('<I', data[4:8])[0] + 8 != len(data):
        raise ValueError('Not a complete WebP container')
    offset = 12
    while offset + 8 <= len(data):
        kind = data[offset:offset + 4]
        size = struct.unpack('<I', data[offset + 4:offset + 8])[0]
        body = data[offset + 8:offset + 8 + size]
        if len(body) != size:
            raise ValueError('Truncated WebP chunk')
        if kind == b'VP8X' and len(body) >= 10:
            return int.from_bytes(body[4:7], 'little') + 1, int.from_bytes(body[7:10], 'little') + 1
        if kind == b'VP8 ' and len(body) >= 10 and body[3:6] == b'\x9d\x01\x2a':
            return struct.unpack('<H', body[6:8])[0] & 16383, struct.unpack('<H', body[8:10])[0] & 16383
        if kind == b'VP8L' and len(body) >= 5 and body[0] == 47:
            packed = int.from_bytes(body[1:5], 'little')
            return (packed & 16383) + 1, ((packed >> 14) & 16383) + 1
        offset += 8 + size + (size & 1)
    raise ValueError('No WebP canvas')


class NativeNewTabResourcesTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.provenance = json.loads(MANIFEST.read_text(encoding='utf-8'))
        cls.records = cls.provenance['runtimeResources']

    def test_source_tag_is_the_actual_protected_reference(self):
        commit = subprocess.check_output(['git', 'rev-parse', 'v1.0.4^{commit}'], cwd=ROOT, text=True).strip()
        self.assertEqual(commit, self.provenance['sourceCommit'])
        self.assertEqual('v1.0.4', self.provenance['sourceTag'])

    def test_all_admitted_bytes_match_manifest_and_actual_tag(self):
        self.assertEqual(13, len(self.records))
        for item in self.records:
            with self.subTest(path=item['runtimePath']):
                data = (ROOT / item['runtimePath']).read_bytes()
                original = subprocess.check_output(['git', 'show', 'v1.0.4:' + item['sourcePath']], cwd=ROOT)
                self.assertEqual(original, data)
                self.assertEqual(item['bytes'], len(data))
                self.assertEqual(item['sha256'], hashlib.sha256(data).hexdigest())

    def test_only_exact_native_resources_are_admitted(self):
        self.assertEqual(len(self.records), len({item['runtimePath'] for item in self.records}))
        for item in self.records:
            self.assertTrue(item['runtimePath'].startswith('rebrowser/app/src/main/res/'))
            self.assertIn(Path(item['runtimePath']).suffix, ('.webp', '.xml'))
            self.assertTrue(item['sourcePath'].startswith(('app/src/main/res/', 'app/src/background/res/')))
        gradle = (ROOT / 'rebrowser/app/build.gradle').read_text()
        self.assertNotIn("main.res.setSrcDirs(['../../app", gradle)
        self.assertNotIn("main.assets.setSrcDirs(['../../app", gradle)

    def test_background_is_the_packaged_master_not_private_source(self):
        data = (ROOT / 'rebrowser/app/src/main/res/raw/rb_new_tab_background.webp').read_bytes()
        self.assertEqual(834672, len(data))
        self.assertEqual((3324, 2493), webp_dimensions(data))
        self.assertLess(len(data), 1024 * 1024)

    def test_logos_and_icons_are_bounded_actual_rasters(self):
        for item in self.records:
            if '/drawable-nodpi/' in item['runtimePath']:
                with self.subTest(path=item['runtimePath']):
                    width, height = webp_dimensions((ROOT / item['runtimePath']).read_bytes())
                    self.assertTrue(0 < width <= 1024 and 0 < height <= 1024)

    def test_photo_aliases_preserve_approved_adjustments_not_new_edits(self):
        mapping = {Path(item['runtimePath']).name: item['sourcePath'] for item in self.records}
        for name in ('bing_logo', 'duckduckgo_logo', 'google_logo', 'google_logo_light'):
            self.assertEqual('app/src/background/res/drawable-nodpi/rb_brand_' + name + '.webp', mapping['rb_brand_' + name + '_photo.webp'])

    def test_every_admitted_resource_has_an_actual_new_tab_consumer(self):
        source = (ROOT / 'app/src/main/java/io/github/rebrowser/ui/NewTabView.java').read_text()
        for item in self.records:
            kind = 'raw' if '/raw/' in item['runtimePath'] else 'drawable'
            self.assertIn('R.' + kind + '.' + Path(item['runtimePath']).stem, source)

    def test_submit_vector_is_the_existing_explicit_button_not_text_branding(self):
        tree = ET.fromstring((ROOT / 'rebrowser/app/src/main/res/drawable/ic_rb_submit.xml').read_bytes())
        self.assertEqual('vector', tree.tag)
        self.assertTrue(tree.findall('path'))


if __name__ == '__main__':
    unittest.main()
