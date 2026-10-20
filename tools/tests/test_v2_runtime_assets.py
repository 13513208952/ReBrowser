#!/usr/bin/env python3
"""Source/static checks only. No actual Gecko CSS/CSP/site/rendering qualification."""
import hashlib
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
ASSETS=ROOT/'app/src/main/assets'
class RuntimeAssetTests(unittest.TestCase):
 def test_reader_csp_is_deny_by_default(self):
  html=(ASSETS/'offline-mail/reader.html').read_text()
  for token in ["default-src 'none'", "img-src 'none'", "connect-src 'none'", "frame-src 'none'", "object-src 'none'", "base-uri 'none'", "form-action 'none'", "script-src 'self'", "style-src 'self'"]:
   self.assertIn(token,html)
  self.assertIn('src="reader.js"',html)
  self.assertNotIn('http://',html);self.assertNotIn('https://',html)
 def test_reader_has_no_html_or_privileged_channel(self):
  js=(ASSETS/'offline-mail/reader.js').read_text()
  for token in ['innerHTML','outerHTML','insertAdjacentHTML','fetch(', 'XMLHttpRequest', 'postMessage(', 'eval(', 'window.location', 'window.open', 'localStorage', 'sessionStorage']:
   self.assertNotIn(token,js)
 def test_phone_contains_exact_existing_approved_css_bodies(self):
  phone=(ASSETS/'bridge/styles/music-phone.css').read_bytes()
  for name in ['netease_home_card.css','netease_expanded_portrait.css']:
   self.assertIn((ROOT/'tools/music'/name).read_bytes(),phone)
  self.assertEqual('974ba8c6a8d287f41f46a32de8fdd57b1aaf409a99ddf877861d78751fbb56f4',hashlib.sha256((ROOT/'docs/MUSIC_SUBAPPLICATION_OWNER_VISION_VERBATIM.md').read_bytes()).hexdigest())
  self.assertEqual('4ee8ce6f44c9fd26f009babfe996746c86c440e384890b8b808c270a9e13e0ec',hashlib.sha256((ROOT/'tools/music/netease_portrait_reference.js').read_bytes()).hexdigest())
 def test_two_unapproved_rearrangements_not_admitted(self):
  for name in ['music-window.css','music-desktop.css']:self.assertFalse((ASSETS/'bridge/styles'/name).exists())
 def test_new_assets_not_globally_packaged_or_activities_enabled(self):
  build=(ROOT/'rebrowser/app/build.gradle').read_text()
  self.assertNotIn('assets.srcDirs',build);self.assertNotIn('assets.srcDir',build)
  manifest=(ROOT/'rebrowser/app/src/main/AndroidManifest.xml').read_text()
  for name in ['MusicActivity','ApkHandoffActivity','OfflineMailActivity','SettingsActivity']:self.assertNotIn(name,manifest)
if __name__=='__main__':unittest.main()
