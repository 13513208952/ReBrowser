'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const source=fs.readFileSync('app/src/full/assets/rebrowser_netease_layout.js','utf8');
function fixture({origin='https://music.163.com',path='/st/webplayer',frame=false,css=true,portrait=true}={}){
  const timers=new Map();let id=0;
  const window={};window.top=frame?{}:window;
  const context=vm.createContext({window,location:{origin,pathname:path},CSS:{supports:()=>css},URL,
    document:{scripts:[],body:null,querySelector:()=>null,getElementById:()=>null},
    setTimeout:fn=>{timers.set(++id,fn);return id;},clearTimeout:n=>timers.delete(n)});
  vm.runInContext(source.replace('__INITIAL_PORTRAIT__',String(portrait)),context);
  return {window,timers,pump(){let n=0;while(timers.size){const [id,fn]=timers.entries().next().value;timers.delete(id);fn();assert.ok(++n<150);}return n;}};
}
for(const options of [{origin:'https://evil.invalid'},{path:'/login'},{frame:true},{css:false}]){
  const f=fixture(options);assert.equal(f.window.__rbMusicLayoutHostV1,undefined);assert.equal(f.timers.size,0);
}
{
  const f=fixture();assert.ok(Object.isFrozen(f.window.__rbMusicLayoutHostV1));
  assert.equal(f.window.__rbMusicLayoutHostV1.back(),'unavailable');
  assert.equal(f.pump(),119);assert.equal(f.timers.size,0);
}
{
  const f=fixture({portrait:false});assert.equal(f.timers.size,0);
  f.window.__rbMusicLayoutHostV1.setPortrait(true);assert.equal(f.timers.size,1);
  f.window.__rbMusicLayoutHostV1.setPortrait(false);assert.equal(f.timers.size,0);
  let removed=0;f.window.__rbNeteasePortraitLayoutV1={cleanup:()=>removed++,backResult:()=> 'handled'};
  assert.equal(f.window.__rbMusicLayoutHostV1.back(),'handled');
  f.window.__rbMusicLayoutHostV1.cleanup();assert.equal(removed,1);
  assert.equal(f.window.__rbMusicLayoutHostV1,undefined);assert.equal(f.timers.size,0);
}
assert.ok(!source.includes("listen(document,'keydown'"));
assert.ok(!source.includes("type:'playing/switchResumeOrPause'"));
console.log('Music layout host: 6 lifecycle/guard scenarios passed (fake DOM, not WebView/playback).');
