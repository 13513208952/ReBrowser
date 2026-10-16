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

    def test_both_player_states_use_the_same_square_id_rule_and_owned_cleanup(self):
        asset = generator.generate()
        self.assertIn("const controlsStyle=own('style','rb-music-controls-style')", asset)
        self.assertIn('.DefaultBarWrapper_d1fdcdsp .btns>#btn_pc_minibar_play{', asset)
        self.assertIn('width:40px!important;height:40px!important', asset)
        self.assertIn('min-width:40px!important;max-width:40px!important', asset)
        self.assertIn('min-height:40px!important;max-height:40px!important', asset)
        self.assertIn('for(const n of nodes)n.remove();nodes.length=0;', asset)

    def test_expanded_styles_are_separately_owned_and_fully_scoped(self):
        import re
        css = (ROOT / 'tools/music/netease_expanded_portrait.css').read_text()
        asset = generator.generate()
        self.assertIn("const expandedStyle=own('style','rb-music-expanded-style')", asset)
        self.assertIn(css, asset)
        plain = re.sub(r'/\*.*?\*/', '', css, flags=re.S)
        for selector in re.findall(r'([^{}]+)\{', plain):
            if selector.strip().startswith('@media'):
                continue
            for part in selector.split(','):
                self.assertTrue(part.strip().startswith('.rb-music-mobile '), part)
        self.assertNotRegex(plain, r'\b(?:transform|zoom|touch-action)\s*:')

    def test_home_card_styles_are_owned_and_removed_with_adaptation(self):
        import re
        css = (ROOT / 'tools/music/netease_home_card.css').read_text()
        asset = generator.generate()
        self.assertIn("const homeStyle=own('style','rb-music-home-style')", asset)
        self.assertIn(css, asset)
        self.assertIn('for(const n of nodes)n.remove();nodes.length=0;', asset)
        plain = re.sub(r'/\*.*?\*/', '', css, flags=re.S)
        for selector in re.findall(r'([^{}]+)\{', plain):
            self.assertTrue(selector.strip().startswith('.rb-music-mobile '), selector)
        self.assertNotRegex(plain, r'\b(?:transform|zoom|touch-action)\s*:')

    def test_home_card_stretches_and_only_hides_middle_entries_without_dom_changes(self):
        css = (ROOT / 'tools/music/netease_home_card.css').read_text()
        self.assertIn('align-items:stretch!important', css)
        self.assertIn('height:auto!important;min-height:0!important;align-self:stretch', css)
        self.assertIn('grid-template-columns:repeat(2,minmax(0,1fr))', css)
        self.assertIn('.card-rail>.cards {', css)
        self.assertIn('.cards:has(>:nth-child(4))>:not(:nth-child(1)):not(:nth-child(4))', css)
        self.assertNotIn('.card-rail:has(', css)  # Rail children are pages, not four entry tiles.
        self.assertIn('flex:0 0 100%!important', css)  # Preserve original page width/transform.
        self.assertIn('height:0!important;padding-top:100%', css)
        self.assertIn('max-height:none!important', css)
        self.assertNotIn('removeChild', generator.generate())

    def test_expanded_grid_keeps_official_title_cover_lyrics_and_controls(self):
        css = (ROOT / 'tools/music/netease_expanded_portrait.css').read_text()
        self.assertIn('grid-template-columns:minmax(0,1fr)', css)
        self.assertIn('grid-template-rows:minmax(88px,max-content) clamp(100px,20vh,200px) minmax(0,1fr)', css)
        self.assertIn('padding:8px 18px 146px', css)
        self.assertIn('margin-left:52px!important;min-height:40px', css)
        self.assertIn('grid-template-columns:repeat(2,minmax(0,1fr))', css)
        self.assertIn('.VinylPageLayoutContainer_vwagppq>.display', css)
        self.assertIn('display:contents!important', css)
        for selector in ['.TrackTitleContainer_t1s72en4', '.TrackDisplayContainer_t11eiop9',
                         '.VinylPageBarWrapper_v1xybaiu', '.LyricDefaultPageContainer_lnd8jru']:
            self.assertIn(selector, css)
        self.assertIn('min-height:44px!important;max-height:44px!important', css)
        self.assertIn('white-space:nowrap!important', css)

    def test_short_adapted_windows_reserve_lyrics_without_scaling(self):
        css = (ROOT / 'tools/music/netease_expanded_portrait.css').read_text()
        self.assertIn('@media (max-height:760px)', css)
        self.assertIn('grid-template-rows:minmax(88px,max-content) minmax(0,1fr)', css)
        self.assertIn('.VinylPageLayoutContainer_vwagppq>.vinyl {display:none!important;}', css)
        self.assertNotIn('scale(', css)

    def test_classic_window_midpoint_contract_rejects_the_old_negative_anchor(self):
        # Arithmetic fixtures for the pinned official withOffset formula, NOT a CSS engine.
        # The official scroll target is innerHeight/2 - lyricRect.top. The old stacked
        # desktop-sized cover put that target above the lyric viewport on this phone.
        height = 793
        old_top = 56 + 96 + height * .28 + 20 + 52
        self.assertLess(height / 2 - old_top, 0)
        for height in [480, 600, 760, 761, 793, 900, 1200]:
            for title in [88, 110, 124]:  # Normal, alias, two-line title + alias.
                cover = 0 if height <= 760 else min(200, max(100, height * .20))
                gaps = 6 if height <= 760 else 16
                top = 8 + title + cover + gaps + 52
                target = height / 2 - top
                lyric_height = height - 146 - top
                self.assertGreaterEqual(target, 20, (height, title, target))
                self.assertLessEqual(target + 88, lyric_height, (height, title, target, lyric_height))

    def test_heart_contours_share_optical_bounds_not_rating_state(self):
        import re
        import xml.etree.ElementTree as xml
        ns = '{http://schemas.android.com/apk/res/android}'
        paths = []
        for name in ['outline', 'filled']:
            vector = xml.parse(ROOT / f'app/src/full/res/drawable/ic_rb_music_heart_{name}.xml').getroot()
            path = vector.find('path')
            paths.append(path.attrib[ns + 'pathData'])
            self.assertEqual('1.7', path.attrib[ns + 'strokeWidth'])
            self.assertEqual('round', path.attrib[ns + 'strokeLineJoin'])
            self.assertEqual('@android:color/transparent' if name == 'outline' else '#FFFFFFFF',
                             path.attrib[ns + 'fillColor'])
        self.assertEqual(paths[0], paths[1])
        coords = [float(n) for n in re.findall(r'\d+(?:\.\d+)?', paths[0])]
        self.assertAlmostEqual(24, min(coords[0::2]) + max(coords[0::2]))
        self.assertGreaterEqual(min(coords[0::2]) - .85, 2)
        self.assertLessEqual(max(coords[0::2]) + .85, 22)

    def test_native_host_is_fixed_and_does_not_dispatch_playback(self):
        asset = generator.generate()
        self.assertIn('window.top!==window', asset)
        self.assertIn("u.pathname.endsWith('/hybrid/app.4c9df986.js')", asset)
        self.assertEqual(asset.count('__INITIAL_PORTRAIT__'), 1)
        self.assertNotIn("type:'playing/", asset)
        self.assertNotIn("listen(document,'keydown'", asset)

if __name__ == '__main__':
    unittest.main()
