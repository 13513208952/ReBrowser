#!/usr/bin/env python3
"""Resource/source-shape guards only; actual Android rendering/action checks are separate."""
import hashlib
import json
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
A = '{http://schemas.android.com/apk/res/android}'

class NativeHomeButtonResources(unittest.TestCase):
    def test_native_authorship_does_not_claim_legacy_byte_identity(self):
        record = json.loads((ROOT / 'planning/geckoview-v2/records/native-home-button-resource.json').read_text())
        item = record['runtimeResources'][0]
        self.assertEqual(hashlib.sha256((ROOT / item['runtimePath']).read_bytes()).hexdigest(), item['sha256'])
        self.assertIn('not imported v1', record['authorship'])
        self.assertEqual(record['preserved']['touchWidthDp'], 46)
        self.assertEqual(record['preserved']['touchHeightDp'], 50)

    def test_optical_geometry_is_density_independent_and_round(self):
        vector = ET.parse(ROOT / 'rebrowser/app/src/main/res/drawable/ic_rb_home.xml').getroot()
        self.assertEqual(vector.tag, 'vector')
        self.assertEqual(vector.get(A + 'width'), '24dp')
        self.assertEqual(vector.get(A + 'height'), '24dp')
        path = vector.find('path')
        self.assertEqual(path.get(A + 'strokeLineCap'), 'round')
        self.assertEqual(path.get(A + 'strokeLineJoin'), 'round')
        self.assertEqual(path.get(A + 'fillColor'), '#00000000')
        self.assertEqual(path.get(A + 'strokeWidth'), '1.8')

    def test_runtime_consumer_retains_action_accessibility_and_touch_area(self):
        source = (ROOT / 'app/src/main/java/io/github/rebrowser/ui/BrowserToolbar.java').read_text()
        self.assertIn('private final ImageButton home;', source)
        self.assertIn('setImageResource(R.drawable.ic_rb_home)', source)
        self.assertIn('home,new LayoutParams(dp(46),dp(50))', source)
        self.assertIn('home.setOnClickListener(v->dispatch(6))', source)
        self.assertIn('打开当前子标签页的主页', source)
        self.assertNotIn('home=icon("⌂"', source)

    def test_normal_and_photo_tint_and_feedback_are_native_only(self):
        source = (ROOT / 'app/src/main/java/io/github/rebrowser/ui/BrowserToolbar.java').read_text()
        self.assertIn('home.setImageTintList(', source)
        self.assertIn('selectableItemBackgroundBorderless', source)
        self.assertIn('boolean light=photo && nativeNewTab', source)
        main = (ROOT / 'app/src/main/java/io/github/rebrowser/ui/BrowserActivity.java').read_text()
        self.assertIn('navigate(preferences.homeTarget())', main)

if __name__ == '__main__':
    unittest.main()
