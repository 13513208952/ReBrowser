'use strict';
// Fixed lyric reader only: fake official API/parser/SDK. No network/account/real lyrics/audio.
const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const source=fs.readFileSync('app/src/full/assets/rebrowser_netease_provider.js','utf8');
const flush=async()=>{for(let i=0;i<12;i++)await Promise.resolve();};
async function realm({player=true,logged=true,cloud=false,missing=false}={}){
 let now=100000,resolve,reject,calls=0,params,actions=0;
 class Audio{play(){throw Error('Reader must not play');}}
 const store={playing:{playId:'fixture-play',resourceTrackId:'123',resourceIsCloudSong:cloud}};
 const helper={getStore:()=>store,getDispatch:()=>()=>actions++};
 const api={Ah:()=>Promise.resolve(logged?{code:200,profile:{userId:42}}:{code:200})};
 if(!missing)api.Rf=p=>{calls++;params=p;return new Promise((r,j)=>{resolve=r;reject=j;});};
 const sdk={AudioPlayer:{subscribePlayStatus:()=>Promise.resolve(true)}};
 const req=n=>({14:{a:helper},15:api,4:sdk,519:{a:data=>data.normalized}}[n]);req.m={};req.c={};
 const c=vm.createContext({URL,DOMException,HTMLMediaElement:Audio,Promise,setTimeout,clearTimeout,Date:{now:()=>now},
  location:{origin:'https://music.163.com',pathname:'/st/webplayer'},
  document:{scripts:[{src:'https://s5.music.126.net/static_public/v/hybrid/app.4c9df986.js'}],addEventListener(){},removeEventListener(){},querySelectorAll:()=>[]},
  webpackJsonp:[]});c.window=c;c.top=c;
 c.webpackJsonp.push=e=>e[1].rb_provider_v1({}, {},req);
 vm.runInContext(source.replace('__PLAYER_ALLOWED__',String(player)),c);
 const facade=c.__rbNeteaseProviderV1;facade.state();await flush();facade.state();await flush();
 return {facade,store,resolve:x=>resolve(x),reject:()=>reject(Error('Fixture network failure')),calls:()=>calls,params:()=>params,actions:()=>actions,advance:n=>now+=n};
}
const data=lines=>({code:200,lrc:{lyric:'[00:01.00]Fixture'},normalized:{lyricLines:lines}});
(async()=>{
 for(const opts of [{player:false},{logged:false}]){
  const r=await realm(opts);assert.equal(r.facade.lyrics('123').status,'not-ready');assert.equal(r.calls(),0);r.facade.cleanup();
 }
 for(const opts of [{cloud:true},{missing:true}]){
  const r=await realm(opts);assert.equal(r.facade.lyrics('123').status,'unavailable');assert.equal(r.calls(),0);r.facade.cleanup();
 }
 {
  const r=await realm();assert.equal(r.facade.lyrics('124').status,'track-changed');
  assert.equal(r.facade.lyrics('123').status,'loading');await flush();assert.equal(r.calls(),1);
  assert.equal(JSON.stringify(r.params()),JSON.stringify({id:'123',lv:-1,tv:-1,rv:-1,yv:-1}));
  for(let i=0;i<1000;i++)assert.equal(r.facade.lyrics('123').status,'loading');assert.equal(r.calls(),1);
  r.resolve(data([{time:2.25,lyric:'Later'},{time:-0.5,lyric:'Offset\nfixture'},{lyric:'Untimed credit'},{time:1,lyric:'First'}]));await flush();
  const result=r.facade.lyrics('123');assert.equal(result.status,'ready');
  assert.equal(JSON.stringify(result.lines),JSON.stringify([{time:0,text:'Offset fixture'},{time:1000,text:'First'},{time:2250,text:'Later'}]));
  assert.equal(r.calls(),1);assert.equal(r.actions(),0);r.facade.cleanup();assert.equal(r.facade.lyrics('123').status,'not-ready');
 }
 {
  const r=await realm();r.facade.lyrics('123');await flush();r.advance(8000);
  for(let i=0;i<1000;i++)assert.equal(r.facade.lyrics('123').status,'timeout');assert.equal(r.calls(),1);
  r.store.playing.resourceTrackId='456';r.store.playing.playId='new-play';
  assert.equal(r.facade.lyrics('456').status,'timeout');assert.equal(r.calls(),1); // No second outstanding request.
  r.resolve(data([{time:1,lyric:'Stale response'}]));await flush();assert.equal(r.facade.lyrics('456').status,'loading');await flush();assert.equal(r.calls(),2);
  r.resolve(data([{time:2,lyric:'New response'}]));await flush();assert.equal(r.facade.lyrics('456').lines[0].text,'New response');r.facade.cleanup();
 }
 {
  const r=await realm();r.facade.lyrics('123');await flush();r.store.playing.playId='replacement-play';
  r.resolve(data([{time:0,lyric:'Old generation'}]));await flush();
  assert.equal(r.facade.lyrics('123').status,'loading');await flush();assert.equal(r.calls(),2);
  r.resolve(data([{time:0,lyric:'Replacement generation'}]));await flush();
  assert.equal(r.facade.lyrics('123').lines[0].text,'Replacement generation');r.facade.cleanup();
 }
 {
  const r=await realm();r.facade.lyrics('123');await flush();r.advance(8000);
  r.resolve(data([{time:0,lyric:'Late'}]));await flush();
  for(let i=0;i<1000;i++)assert.equal(r.facade.lyrics('123').status,'timeout');assert.equal(r.calls(),1);r.facade.cleanup();
 }
 {
  const r=await realm();r.facade.lyrics('123');await flush();r.reject();await flush();
  assert.equal(r.facade.lyrics('123').status,'unavailable');assert.equal(r.calls(),1);r.facade.cleanup();
 }
 for(const [payload,status] of [
  [{code:500},'unavailable'],
  [{code:200},'none'],
  [{...data([{time:0,lyric:'Instrumental'}]),normalized:{displayType:'pure',lyricLines:[]}},'none'],
  [data([{lyric:'No timestamp'}]),'untimed'],
  [data(Array.from({length:513},()=>({time:1,lyric:'Bound'}))),'too-large'],
  [data([{time:0,lyric:'x'.repeat(257)}]),'too-large'],
  [{...data([]),lrc:{lyric:'x'.repeat(262145)}},'too-large'],
  [{...data([]),lrc:{lyric:42}},'unavailable']
 ]){
  const r=await realm();r.facade.lyrics('123');await flush();r.resolve(payload);await flush();assert.equal(r.facade.lyrics('123').status,status);assert.equal(r.calls(),1);r.facade.cleanup();
 }
 {
  const r=await realm();r.facade.lyrics('123');await flush();r.facade.cleanup();r.resolve(data([{time:0,lyric:'Disposed'}]));await flush();assert.equal(r.facade.lyrics('123').status,'not-ready');
 }
 console.log('Music lyrics: 18 fake API/parser scenarios; bounded reader, offset units, stale/late/disposed responses and no playback actions. Not real Profile/lyrics acceptance.');
})().catch(e=>{console.error(e);process.exitCode=1;});
