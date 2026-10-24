"""Actual approved filled-star resource provenance; not native UI/IO execution."""
import hashlib
import json
from pathlib import Path
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[2]

class FavoritesResourceTests(unittest.TestCase):
    def test_approved_filled_star_is_exact_and_consumed(self):
        path = 'rebrowser/app/src/main/res/drawable/ic_rb_star_filled.xml'
        record = json.loads((ROOT / 'planning/geckoview-v2/records/native-browser-chrome-resource-provenance.json').read_text())
        entries = [r for r in record['runtimeResources'] if r['runtimePath'] == path]
        self.assertEqual(len(entries), 1)
        raw = (ROOT / path).read_bytes()
        approved = subprocess.check_output(['git', 'show', 'v1.0.4:' + entries[0]['sourcePath']], cwd=ROOT)
        self.assertEqual(raw, approved)
        self.assertEqual(hashlib.sha256(raw).hexdigest(), entries[0]['sha256'])
        self.assertIn('R.drawable.ic_rb_star_filled', (ROOT / 'app/src/main/java/io/github/rebrowser/ui/BrowserActivity.java').read_text())
