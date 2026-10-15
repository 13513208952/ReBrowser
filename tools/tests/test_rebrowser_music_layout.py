"""Source-generation consistency; no browser, service or Profile access."""
import importlib.util
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('music_layout_generator', ROOT / 'tools/music/build_android_layout.py')
generator = importlib.util.module_from_spec(spec)
spec.loader.exec_module(generator)

class MusicLayoutGenerationTest(unittest.TestCase):
    def test_android_asset_matches_approved_generator(self):
        self.assertEqual(generator.generate(), generator.OUTPUT.read_text(encoding='utf-8'))
    def test_memory_patch_preserves_approved_visual_css(self):
        reference = generator.REFERENCE.read_text(encoding='utf-8')
        asset = generator.generate()
        css = lambda text: text.split('style.textContent=`', 1)[1].split('`;', 1)[0]
        self.assertEqual(css(reference), css(asset))
        self.assertNotIn('webpackJsonp.push', asset)
        self.assertIn('marked.clear()', asset)
        self.assertIn('coverStyles.clear()', asset)

    def test_native_host_is_fixed_and_does_not_dispatch_playback(self):
        asset = generator.generate()
        self.assertIn('window.top!==window', asset)
        self.assertIn("u.pathname.endsWith('/hybrid/app.4c9df986.js')", asset)
        self.assertEqual(asset.count('__INITIAL_PORTRAIT__'), 1)
        self.assertNotIn("type:'playing/", asset)
        self.assertNotIn("listen(document,'keydown'", asset)

if __name__ == '__main__':
    unittest.main()
