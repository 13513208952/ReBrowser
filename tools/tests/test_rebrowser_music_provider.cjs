'use strict';
// Fake official facade/audio only: no network, Profile, account or real playback access.
const assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs');
const source=fs.readFileSync('app/src/full/assets/rebrowser_netease_provider.js','utf8');
function realm(player=true,logged=true){
 const calls=[],callbacks={},listeners={};let realPlays=0,likeClicks=0;
 const icon={getAttribute:n=>n==='title'?'喜欢(Ctrl+L)':''};
 const like={disabled:false,getAttribute:()=>'',querySelector:s=>s==='.cmd-icon[title]'?icon:null,getClientRects:()=>[{}],
  click:()=>{likeClicks++;store['async:hostResource'].likeTracksMap['local-song']=!store['async:hostResource'].likeTracksMap['local-song'];}};
 class Audio{play(){realPlays++;return Promise.resolve();}}
 const store={playing:{playId:'local-song',resourceTrackId:'local-song',onlineResourceId:'local-song',playingState:1,
  resourceName:'Owned fixture',resourceDuration:300,resourceArtists:[{name:'Fixture artist'}],freeTrialInfo:{start:30,end:60}},
  playingList:{curPlayingList:[{}]},'async:hostResource':{likeTracksMap:{}}};
 const history={location:{pathname:'/home'},goBack:()=>calls.push({type:'fixture-navigation-back'})};
 const helper={getStore:()=>store,getDispatch:()=>a=>calls.push(a),getHistory:()=>history};
 const api={Ah:()=>Promise.resolve(logged?{code:200,profile:{userId:42}}:{code:200,profile:null})};
 const sdk={AudioPlayer:{subscribePlayStatus:({type,callback})=>{callbacks[type]=callback;return Promise.resolve(true);}}};
 const req=n=>({14:{a:helper},15:api,4:sdk}[n]);req.m={};req.c={};
 const context=vm.createContext({URL,DOMException,HTMLMediaElement:Audio,Date,Promise,console,setTimeout,clearTimeout,
  location:{origin:'https://music.163.com',pathname:'/st/webplayer'},
  document:{scripts:[{src:'https://s5.music.126.net/static_public/v/hybrid/app.4c9df986.js'}],
   addEventListener:(t,f)=>listeners[t]=f,removeEventListener(){},querySelector:()=>null,querySelectorAll:()=>[like]},
  webpackJsonp:{},});
 context.window=context;context.top=context;
 context.webpackJsonp=[];context.webpackJsonp.push=entry=>{const id=Object.keys(entry[1])[0];entry[1][id]({}, {}, req);};
 vm.runInContext(source.replace('__PLAYER_ALLOWED__',String(player)),context);
 return {api:context.__rbNeteaseProviderV1,Audio,calls,callbacks,listeners,store,like,history,context,document:context.document,likeClicks:()=>likeClicks,plays:()=>realPlays};
}
async function ready(r){r.api.state();await Promise.resolve();await Promise.resolve();r.api.state();await Promise.resolve();await Promise.resolve();return r.api.state();}
(async()=>{
 const cold=realm();assert.equal((await ready(cold)).ready,true);
 await assert.rejects(new cold.Audio().play(),{name:'NotAllowedError'});assert.equal(cold.plays(),0);
 cold.listeners.pointerup({isTrusted:false});await assert.rejects(new cold.Audio().play());
 cold.listeners.pointerup({isTrusted:true});await assert.rejects(new cold.Audio().play()); // unrelated tap does not arm playback
 const playButton={id:'btn_pc_minibar_play',getAttribute:()=>'',querySelector:()=>null,textContent:''};
 const playTarget={closest:s=>s.startsWith('input')?null:playButton};
 cold.listeners.pointerup({isTrusted:true,target:playTarget});await new cold.Audio().play();assert.equal(cold.plays(),1);
 cold.store.playing.playingState=2;assert.equal(cold.api.state().playing,false);
 const pauseButton={...playButton,querySelector:s=>s==='.cmd-icon[title]'?{getAttribute:()=> '暂停'}:null};
 cold.listeners.pointerup({isTrusted:true,target:{closest:s=>s.startsWith('input')?null:pauseButton}});
 await assert.rejects(new cold.Audio().play(),{name:'NotAllowedError'});
 cold.listeners.keydown({type:'keydown',isTrusted:true,key:'MediaPlay'});await new cold.Audio().play();
 cold.listeners.keydown({type:'keydown',isTrusted:true,key:'MediaPlayPause'});await assert.rejects(new cold.Audio().play(),{name:'NotAllowedError'});
 assert.equal(cold.api.command('resume',0,'local-song'),'submitted');
 cold.callbacks.playstate({playId:'wrong',state:1});assert.equal(cold.api.state().playing,false);
 cold.callbacks.playstate({playId:'local-song',state:1});assert.equal(cold.api.state().playing,true);
 cold.callbacks.playprogress({playId:'local-song',current:'12.25'});assert.equal(cold.api.state().position,42250);
 assert.equal(cold.api.command('seek',29999,'local-song'),'outside-playable-range');
 assert.equal(cold.api.command('seek',45000,'local-song'),'submitted');assert.equal(cold.calls.at(-1).payload.duration,15);
 cold.callbacks.seek({playId:'local-song',code:1,position:15});assert.equal(cold.api.state().position,42250);
 cold.callbacks.seek({playId:'local-song',code:0,position:15});assert.equal(cold.api.state().position,45000);
 cold.callbacks.end({playId:'local-song'});assert.equal(cold.api.state().playing,false);assert.equal(cold.api.state().buffering,true);
 cold.callbacks.playstate({playId:'local-song',state:2});assert.equal(cold.api.state().buffering,false);
 assert.equal(cold.api.command('next',0,'stale-song'),'track-changed');
 assert.equal(cold.api.command('arbitrary',0,''),'unsupported');
 assert.equal(cold.api.state().canLike,true);
 assert.equal(cold.api.command('like',1,'local-song'),'submitted');assert.equal(cold.likeClicks(),1);assert.equal(cold.api.state().liked,true);
 assert.equal(cold.api.command('like',1,'local-song'),'submitted');assert.equal(cold.likeClicks(),1); // desired state is idempotent
 assert.equal(cold.api.command('like',0,'local-song'),'submitted');assert.equal(cold.likeClicks(),2);assert.equal(cold.api.state().liked,false);
 cold.like.disabled=true;assert.equal(cold.api.command('like',1,''),'unsupported');
 assert.equal(cold.api.command('pause',0,''),'submitted');await assert.rejects(new cold.Audio().play());
 cold.api.cleanup();assert.equal(cold.api.command('resume',0,''),'not-ready');
 const restored=realm();await ready(restored);restored.store.playing.playId='';restored.store.playing.restoreResource={current:232};
 assert.equal(restored.api.state().position,262000);
 restored.store.playing.restoreResource.current=155;assert.equal(restored.api.state().position,185000);
 restored.store.playing.playId='local-song';restored.callbacks.playstate({playId:'local-song',state:2});assert.equal(restored.api.state().position,185000);
 const login=realm(false,true);assert.equal((await ready(login)).auth,'yes');assert.equal(login.api.state().ready,false);
 assert.equal(login.api.command('resume',0,''),'not-ready');await assert.rejects(new login.Audio().play());
 const guest=realm(true,false);assert.equal((await ready(guest)).auth,'no');assert.equal(guest.api.state().ready,false);
 assert.equal(guest.api.command('resume',0,''),'not-ready');
 const s=guest.api.state();assert.equal('userId' in s,false);assert.equal('profile' in s,false);
 const navigation=realm();await ready(navigation);
 const selected=new Map();let dialogs=[],popovers=[],outside=0,closed=0;
 navigation.context.innerWidth=600;navigation.context.innerHeight=900;
 navigation.context.getComputedStyle=n=>n.css||{display:'block',visibility:'visible',zIndex:'0'};
 navigation.context.MouseEvent=class{constructor(type,options){this.type=type;Object.assign(this,options);}};
 navigation.document.body={dispatchEvent:e=>{assert.equal(e.type,'mousedown');outside++;}};
 navigation.document.querySelector=s=>selected.get(s)||null;
 navigation.document.querySelectorAll=s=>s==='[role="dialog"],.cmd-modal,.cmd-sidesheet'?dialogs:s==='.cmd-popover'?popovers:[];
 const close={getClientRects:()=>[{}],getAttribute:()=>null,click:()=>closed++};
 assert.equal(navigation.api.back(),'root');
 const input={value:'fixture query',blur:()=>{navigation.document.activeElement=null;}};
 selected.set('.SearchWrapper_s14tpfr6 input',input);navigation.document.activeElement=input;
 assert.equal(navigation.api.back(),'handled');assert.equal(outside,0);
 selected.set('.SearchResults_s92u6ho',{getClientRects:()=>[{}]});
 assert.equal(navigation.api.back(),'handled');assert.equal(outside,1);assert.equal(input.value,'fixture query');
 selected.delete('.SearchResults_s92u6ho');
 // Retained offscreen / transparent dialog DOM must not intercept Back.
 dialogs=[{getClientRects:()=>[{}],getBoundingClientRect:()=>({width:10,height:10,left:800,right:810,top:0,bottom:10}),querySelector:()=>close}];
 assert.equal(navigation.api.back(),'root');
 dialogs=[{getClientRects:()=>[{}],parentElement:{css:{opacity:'0'}},querySelector:()=>close}];
 assert.equal(navigation.api.back(),'root');
 dialogs=[{getClientRects:()=>[{}],closest:()=>({}),querySelector:()=>close}];
 assert.equal(navigation.api.back(),'root');
 dialogs=[{getClientRects:()=>[{}],querySelector:()=>close}];
 assert.equal(navigation.api.back(),'handled');assert.equal(closed,1);
 close.disabled=true;assert.equal(navigation.api.back(),'layer');close.disabled=false;
 dialogs=[{getClientRects:()=>[{}],querySelector:()=>null}];assert.equal(navigation.api.back(),'layer');dialogs=[];
 navigation.store['page:vinylPage']={pageState:'opened'};
 assert.equal(navigation.api.back(),'layer');selected.set('.Container_c1m2d5ze .close button',close);
 assert.equal(navigation.api.back(),'handled');assert.equal(closed,2);navigation.store['page:vinylPage'].pageState='closed';
 navigation.history.location.pathname='/searchResults/fixture';assert.equal(navigation.api.back(),'handled');
 assert.equal(navigation.calls.at(-1).type,'fixture-navigation-back');
 await assert.rejects(new navigation.Audio().play(),{name:'NotAllowedError'});
 navigation.api.cleanup();assert.equal(navigation.api.back(),'layer');
 console.log('Music provider: cold-pause/login gate, actual-state correlation, bounded controls, trial coordinates and fixed layered Back passed (fake SDK/DOM/audio).');
})().catch(e=>{console.error(e);process.exitCode=1;});
