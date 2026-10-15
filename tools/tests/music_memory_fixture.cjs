'use strict';
// Minimal DOM + Webpack-4 parent-array semantics. No official bundle/network/account is loaded.
const vm=require('node:vm');
class Node {
 constructor(){this.isConnected=false;this.inert=false;this.children=[];this.attrs={};this.props=new Map();this.classes=new Set();
  this.classList={add:(...s)=>s.forEach(x=>this.classes.add(x)),remove:(...s)=>s.forEach(x=>this.classes.delete(x)),toggle:(s,v)=>v?this.classes.add(s):this.classes.delete(s)};
  this.style={setProperty:(k,v,p='')=>this.props.set(k,{v,p}),getPropertyValue:k=>this.props.get(k)?.v||'',getPropertyPriority:k=>this.props.get(k)?.p||'',removeProperty:k=>this.props.delete(k)};}
 append(n){n.parentElement=this;n.isConnected=true;this.children.push(n);}prepend(n){this.append(n);}
 setAttribute(k,v){this.attrs[k]=v;}getAttribute(k){return this.attrs[k]??null;}removeAttribute(k){delete this.attrs[k];}
 addEventListener(){}removeEventListener(){}remove(){this.isConnected=false;if(this.parentElement)this.parentElement.children=this.parentElement.children.filter(n=>n!==this);}
 querySelector(){return null;}querySelectorAll(){return [];}contains(){return false;}focus(){}
 getBoundingClientRect(){return {x:0,width:200,height:100};}
}
function realm({healthy=true,queued=false}={}){
 let clock=100000,sequence=0,subscriptions=0,attempts=0,registrations=0;const timers=new Map(),intervals=new Map(),frames=new Map(),chunks=new Set();
 const side=new Node(),main=new Node(),header=new Node(),root=new Node(),body=new Node(),head=new Node(),html=new Node();
 for(const n of [side,main,header,root,body,head,html])n.isConnected=true;
 let current;
 function generation(){if(current)for(const n of Object.values(current))n.isConnected=false;
  const page=new Node(),tabs=new Node(),group=new Node(),detail=new Node(),cover=new Node(),tabMarker=new Node();
  for(const n of [page,tabs,group,detail,cover,tabMarker])n.isConnected=true;
  tabs.parentElement=page;detail.parentElement=group;
  detail.querySelector=s=>s==='.StyledCover_szv5e0'?cover:null;
  group.querySelector=s=>s===':scope>.TabsCss_tquhgfk'?tabMarker:null;
  current={page,tabs,group,detail,cover,tabMarker};
 }
 generation();
 const store={playing:{playId:'fixture',resourceTrackId:'fixture',onlineResourceId:'fixture',playingState:1,resourceDuration:300,resourceArtists:[]},playingList:{curPlayingList:[{}]},'async:hostResource':{likeTracksMap:{}}};
 const helper=healthy?{getStore:()=>store,getDispatch:()=>()=>{},getHistory:()=>({location:{pathname:'/home'}})}:{};
 const definitions={14:m=>{attempts++;m.exports={a:helper};},15:m=>m.exports={Ah:()=>Promise.resolve({code:200,profile:{userId:42}})},4:m=>m.exports={AudioPlayer:{subscribePlayStatus:()=>{subscriptions++;return Promise.resolve(true);}}}};
 function req(id){if(req.c[id])return req.c[id].exports;const m={exports:{}};req.c[id]=m;req.m[id](m,m.exports,req);return m.exports;}req.c={};req.m={...definitions};
 class FakeAudio{play(){return Promise.resolve();}}
 const c=vm.createContext({URL,DOMException,Promise,console,HTMLMediaElement:FakeAudio,Date:class extends Date{static now(){return clock;}},innerWidth:600,
  CSS:{supports:()=>true},location:{origin:'https://music.163.com',pathname:'/st/webplayer',hash:''},MutationObserver:class{observe(){}disconnect(){}},getComputedStyle:()=>({display:'block',flexDirection:'column'}),
  requestAnimationFrame:f=>{frames.set(++sequence,f);return sequence;},cancelAnimationFrame:i=>frames.delete(i),
  setInterval:f=>{intervals.set(++sequence,f);return sequence;},clearInterval:i=>intervals.delete(i),setTimeout:f=>{timers.set(++sequence,f);return sequence;},clearTimeout:i=>timers.delete(i),
  document:{body,head,documentElement:html,scripts:[{src:'https://s5.music.126.net/static_public/v/hybrid/app.4c9df986.js'}],
   createElement:()=>new Node(),getElementById:s=>s==='root'?root:null,
   querySelector:s=>({'.Container_c1udi5wp':side,'.RightContainer_r8p6pvv':main,'.HeaderRow_h1n2o69r':header}[s]||null),
   querySelectorAll:s=>s==='.btn_pc_recently_play_subtab'?[current.tabs]:s==='.StyledCommon_s1pv4hmr'?[current.detail]:[],addEventListener(){},removeEventListener(){}}});
 c.window=c;c.top=c;c.webpackJsonp=[];
 function register(packet,parent=true){registrations++;for(const id of packet[0])chunks.add(id);Object.assign(req.m,packet[1]);if(parent)Array.prototype.push.call(c.webpackJsonp,packet);for(const entry of packet[2]||[])req(entry[0]);}
 function activate(){const waiting=c.webpackJsonp.slice();c.webpackJsonp.push=p=>register(p);for(const p of waiting)register(p,false);}
 if(!queued)activate();
 return {c,timers,intervals,frames,chunks,req,generation,activate,current:()=>current,subscriptions:()=>subscriptions,weakPage:()=>new WeakRef(current.page),
  tick(){clock+=750;for(const f of [...intervals.values()])f();},step(ms=750){clock+=ms;},attempts:()=>attempts,registrations:()=>registrations};
}
module.exports={realm};
