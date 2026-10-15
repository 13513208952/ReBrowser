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

def lifetime_patches(core):
    """Reviewed Android-only lifecycle fixes; keep the approved visual reference immutable."""
    def patch(old, new):
        nonlocal core
        if core.count(old) != 1:
            raise SystemExit('Approved layout patch anchor is not unique: ' + old[:80])
        core = core.replace(old, new)
    patch("let open=false,disposed=false,lastRoute='',lastPage=null,provider=null;",
          "let open=false,disposed=false,lastRoute='';")
    patch('function attach(){if(disposed)return;', '''const markerClasses=['rb-local-rail','rb-recent-hide','rb-recent-page','rb-sidebar-hide','rb-meta-column','rb-meta-row','rb-meta-shell','rb-song-detail'];
function restoreCover(n,v){if(v.value)n.style.setProperty('--rb-cover-size',v.value,v.priority);else n.style.removeProperty('--rb-cover-size');}
function prune(){
 for(const n of marked)if(!n.isConnected){n.classList.remove(...markerClasses);marked.delete(n);}
 for(const [n,v] of coverStyles)if(!n.isConnected){restoreCover(n,v);coverStyles.delete(n);}
}
function attach(){if(disposed)return;prune();''')
    patch(next(line for line in core.splitlines() if line.startswith('function route()')),
          "function route(){return window.__rbNeteaseProviderV1?.route()||location.hash||location.pathname;}")
    patch(next(line for line in core.splitlines() if line.startswith('function handleBack()')),
          "function handleBack(){if(open){toggle(false);return true;}return window.__rbNeteaseProviderV1?.back()==='handled';}")
    patch("let scheduled=false;const observer=new MutationObserver(()=>{if(!scheduled){scheduled=true;requestAnimationFrame(()=>{scheduled=false;attach();});}});",
          "let frame=0;const observer=new MutationObserver(()=>{if(!disposed&&!frame)frame=requestAnimationFrame(()=>{frame=0;attach();});});")
    # Share only the provider's fixed route/Back facade. No second Webpack registration/closure.
    patch(next(line for line in core.splitlines() if line.startswith('if(Array.isArray(window.webpackJsonp))')), '')
    patch('providerAvailable:!!provider', 'providerAvailable:!!window.__rbNeteaseProviderV1')
    patch(next(line for line in core.splitlines() if line.startswith('function cleanup()')), '''function cleanup(){
 if(disposed)return;disposed=true;observer.disconnect();clearInterval(timer);if(frame)cancelAnimationFrame(frame);frame=0;
 for(const b of bindings)b[0].removeEventListener(b[1],b[2],b[3]);bindings.length=0;
 for(const n of nodes)n.remove();nodes.length=0;style.remove();
 for(const n of marked)n.classList.remove(...markerClasses);marked.clear();
 for(const [n,v] of coverStyles)restoreCover(n,v);coverStyles.clear();
 if(!createdViewport){if(oldViewport===null)viewport.removeAttribute('content');else viewport.setAttribute('content',oldViewport);}
 document.documentElement.classList.remove('rb-music-mobile','rb-music-drawer-open');side.inert=oldInert;
 delete window.__rbNeteasePortraitLayoutV1;
}''')
    return core

def generate():
    data = REFERENCE.read_bytes()
    if hashlib.sha256(data).hexdigest() != EXPECTED:
        raise SystemExit('Reference changed: explicitly review and update its approved digest first')
    core = data.decode().replace('window.__rbMusicSidebarDemo?.cleanup();', '')
    core = core.replace('__rbMusicMobileDemo', '__rbNeteasePortraitLayoutV1')
    core = core.replace("listen(document,'keydown',e=>{if(e.key==='Escape'&&handleBack()){e.preventDefault();e.stopPropagation();}},true);", '')
    core = lifetime_patches(core)
    core = core.replace('window.__rbNeteasePortraitLayoutV1={toggle,status,cleanup,handleBack};', '''
function backResult(){
 if(open){toggle(false);return 'handled';}
 const result=window.__rbNeteaseProviderV1?.back();
 return ['handled','root','layer'].includes(result)?result:'layer';
}
window.__rbNeteasePortraitLayoutV1={toggle,status,cleanup,handleBack,backResult};''')
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
window.__rbMusicLayoutHostV1=Object.freeze({setPortrait,cleanup,back:()=>window.__rbNeteasePortraitLayoutV1?.backResult()||window.__rbNeteaseProviderV1?.back()||'unavailable'});
setPortrait(portrait);
})();
'''.replace('CORE', core)

if __name__ == '__main__':
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(generate(), encoding='utf-8')
