#!/usr/bin/env python3
"""Generate a fixed Android document-start layout host from the approved reference.
No live browser, Profile, service requests or credentials are read.
"""
from pathlib import Path
import hashlib

ROOT = Path(__file__).resolve().parents[2]
REFERENCE = ROOT / 'tools/music/netease_portrait_reference.js'
OUTPUT = ROOT / 'app/src/full/assets/rebrowser_netease_layout.js'
EXPECTED = '4ee8ce6f44c9fd26f009babfe996746c86c440e384890b8b808c270a9e13e0ec'

def generate():
    data = REFERENCE.read_bytes()
    if hashlib.sha256(data).hexdigest() != EXPECTED:
        raise SystemExit('Reference changed: explicitly review and update its approved digest first')
    core = data.decode().replace('window.__rbMusicSidebarDemo?.cleanup();', '')
    core = core.replace('__rbMusicMobileDemo', '__rbNeteasePortraitLayoutV1')
    core = core.replace("listen(document,'keydown',e=>{if(e.key==='Escape'&&handleBack()){e.preventDefault();e.stopPropagation();}},true);", '')
    # Full-player/modal Back remains deliberately fail-closed until its close controls are tested.
    core = core.replace('window.__rbNeteasePortraitLayoutV1={toggle,status,cleanup,handleBack};', '''
function backResult(){
try{const page=provider?.getStore?.()?.['page:vinylPage'];
if(!open&&page?.pageState==='opened')return 'layer';
if(!open&&Array.from(document.querySelectorAll('[role="dialog"]')).some(x=>x.getBoundingClientRect().width>0))return 'layer';
return handleBack()?'handled':'root';}catch{return 'layer';}}
window.__rbNeteasePortraitLayoutV1={toggle,status,cleanup,backResult};''')
    return '''(() => {
'use strict';
if(window.top!==window || location.origin!=='https://music.163.com' || location.pathname!=='/st/webplayer')return;
if(window.__rbMusicLayoutHostV1)return;
if(typeof CSS==='undefined'||!CSS.supports('display','grid')||!CSS.supports('selector(:has(*))'))return;
let portrait=__INITIAL_PORTRAIT__, timer=0, attempts=0, disposed=false;
const install=()=>{CORE};
function attempt(){
  timer=0;
  if(disposed||!portrait||window.__rbNeteasePortraitLayoutV1)return;
  const knownBundle=Array.from(document.scripts||[]).some(s=>{try{const u=new URL(s.src);return u.protocol==='https:'&&u.hostname==='s5.music.126.net'&&u.pathname.endsWith('/hybrid/app.4c9df986.js');}catch{return false;}});
  if(knownBundle&&document.body&&document.getElementById('root')&&document.querySelector('.Container_c1udi5wp')&&document.querySelector('.RightContainer_r8p6pvv')){install();return;}
  if(++attempts<120)timer=setTimeout(attempt,250);
}
function setPortrait(value){
  if(disposed)return;
  portrait=value===true;clearTimeout(timer);timer=0;
  if(!portrait){window.__rbNeteasePortraitLayoutV1?.cleanup();return;}
  attempts=0;attempt();
}
function cleanup(){disposed=true;clearTimeout(timer);window.__rbNeteasePortraitLayoutV1?.cleanup();delete window.__rbMusicLayoutHostV1;}
window.__rbMusicLayoutHostV1=Object.freeze({setPortrait,cleanup,back:()=>window.__rbNeteasePortraitLayoutV1?.backResult()||'unavailable'});
setPortrait(portrait);
})();
'''.replace('CORE', core)

if __name__ == '__main__':
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(generate(), encoding='utf-8')
