'use strict';
// Offline lifetime/backpressure regressions, not WebView/PSS or actual audio acceptance.
const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const {realm}=require('./music_memory_fixture.cjs');
const layout=fs.readFileSync(process.env.REBROWSER_TEST_LAYOUT_SOURCE||'app/src/full/assets/rebrowser_netease_layout.js','utf8');
const provider=fs.readFileSync(process.env.REBROWSER_TEST_PROVIDER_SOURCE||'app/src/full/assets/rebrowser_netease_provider.js','utf8');
const runLayout=(r,s=layout)=>vm.runInContext(s.replace('__INITIAL_PORTRAIT__','true'),r.c);
const runProvider=r=>vm.runInContext(provider.replace('__PLAYER_ALLOWED__','true'),r.c);
const flush=async()=>{for(let i=0;i<5;i++)await Promise.resolve();};
(async()=>{
 {
  const r=realm();const anchor='function status(){';assert.equal(layout.split(anchor).length,2);
  const side=r.c.document.querySelector('.Container_c1udi5wp'),sideReads=[];
  side.style.setProperty('transition','fixture .8s','important');
  side.getBoundingClientRect=()=>{sideReads.push(side.style.getPropertyValue('transition'));return {x:0,width:200,height:100};};
  runLayout(r,layout.replace(anchor,'window.__testRetained=()=>({marked:marked.size,cover:coverStyles.size,detached:[...marked].filter(n=>!n.isConnected).length});'+anchor));
  assert.ok(sideReads.length>0);assert.ok(sideReads.every(value=>value==='none'));
  assert.equal(side.style.getPropertyValue('transition'),'fixture .8s');assert.equal(side.style.getPropertyPriority('transition'),'important');
  for(let i=0;i<1000;i++)r.tick();
  assert.equal(r.c.__testRetained().marked,2);assert.equal(r.c.__testRetained().cover,1);
  const retired=r.current();
  for(let i=0;i<1000;i++){r.generation();r.tick();}
  assert.equal(r.c.__testRetained().marked,2);assert.equal(r.c.__testRetained().cover,1);assert.equal(r.c.__testRetained().detached,0);
  assert.equal(retired.group.style.getPropertyValue('--rb-cover-size'),'');assert.equal(retired.page.classes.size,0);
  const head=r.c.document.head;
  assert.equal(head.children.filter(n=>n.id==='rb-music-controls-style').length,1);
  assert.equal(head.children.filter(n=>n.id==='rb-music-expanded-style').length,1);
  const host=r.c.__rbMusicLayoutHostV1;host.setPortrait(false);
  assert.equal(head.children.filter(n=>n.id==='rb-music-controls-style').length,0);
  assert.equal(head.children.filter(n=>n.id==='rb-music-expanded-style').length,0);
  assert.equal(r.c.__testRetained().marked,0);assert.equal(r.c.__testRetained().cover,0);assert.equal(r.intervals.size,0);assert.equal(r.frames.size,0);
  for(let i=0;i<1000;i++){host.setPortrait(true);host.setPortrait(false);}
  assert.equal(r.c.webpackJsonp.length,0);assert.equal(r.chunks.size,0);assert.equal(r.intervals.size,0);
  host.cleanup();host.cleanup();assert.equal(r.timers.size,0);
 }
 {
  const r=realm();runProvider(r);
  for(let i=0;i<4800;i++){r.step();r.c.__rbNeteaseProviderV1.state();await flush();}
  assert.equal(r.c.__rbNeteaseProviderV1.state().ready,true);assert.equal(r.registrations(),1);assert.equal(r.subscriptions(),4);
  assert.equal(r.c.webpackJsonp.length,0);assert.equal(r.chunks.size,1);
  assert.ok(!Object.keys(r.req.m).some(k=>k.startsWith('rb_')));assert.ok(!Object.keys(r.req.c).some(k=>k.startsWith('rb_')));
  r.c.__rbNeteaseProviderV1.cleanup();assert.equal(r.timers.size,0);
 }
 {
  const r=realm({healthy:false});runProvider(r);
  for(let i=0;i<4800;i++){r.step();r.c.__rbNeteaseProviderV1.state();}
  assert.equal(r.c.__rbNeteaseProviderV1.state().unavailable,true);assert.equal(r.registrations(),40);
  assert.equal(r.chunks.size,1);assert.equal(r.c.webpackJsonp.length,0);assert.equal(r.subscriptions(),0);
  r.c.__rbNeteaseProviderV1.recheck();assert.equal(r.registrations(),41);
  r.c.__rbNeteaseProviderV1.cleanup();
 }
 {
  const r=realm({queued:true});runProvider(r);r.c.__rbNeteaseProviderV1.state();
  for(let i=0;i<20;i++){r.step();r.c.__rbNeteaseProviderV1.state();}
  assert.equal(r.c.webpackJsonp.length,1);r.step(30000);assert.equal(r.c.__rbNeteaseProviderV1.state().unavailable,true);
  assert.equal(r.c.webpackJsonp.length,0);r.activate();assert.equal(r.registrations(),0);
 }
 {
  const r=realm({queued:true});runProvider(r);r.c.__rbNeteaseProviderV1.state();
  assert.equal(r.c.webpackJsonp.length,1);r.activate();r.c.__rbNeteaseProviderV1.state();await flush();r.c.__rbNeteaseProviderV1.state();await flush();
  assert.equal(r.c.__rbNeteaseProviderV1.state().ready,true);assert.equal(r.c.webpackJsonp.length,0);
 }
 {
  const r=realm({queued:true});runProvider(r);r.c.__rbNeteaseProviderV1.state();r.c.__rbNeteaseProviderV1.cleanup();
  assert.equal(r.c.webpackJsonp.length,0);r.activate();assert.equal(r.registrations(),0);
 }
 // Missing bootstrap used to skip the deadline entirely because no registration had begun.
 for(const missing of ['webpack','bundle']){
  const r=realm();const array=r.c.webpackJsonp;
  if(missing==='webpack')delete r.c.webpackJsonp;else r.c.document.scripts=[];
  runProvider(r);r.c.__rbNeteaseProviderV1.state();
  r.step(30000);assert.equal(r.c.__rbNeteaseProviderV1.state().unavailable,true);
  assert.equal(r.registrations(),0);
  if(missing==='webpack')r.c.webpackJsonp=array;else r.c.document.scripts=[{src:'https://s5.music.126.net/static_public/v/hybrid/app.4c9df986.js'}];
  assert.equal(r.c.__rbNeteaseProviderV1.state().unavailable,true); // No uncontrolled automatic restart.
  r.c.__rbNeteaseProviderV1.recheck();r.c.__rbNeteaseProviderV1.state();await flush();r.c.__rbNeteaseProviderV1.state();await flush();
  assert.equal(r.c.__rbNeteaseProviderV1.state().ready,true);r.c.__rbNeteaseProviderV1.cleanup();
 }
 if(typeof global.gc==='function'){
  function retired(){const r=realm();runLayout(r);const weak=r.weakPage();r.c.__rbMusicLayoutHostV1.setPortrait(false);r.generation();return {r,weak};}
  const fixture=retired();for(let i=0;i<5;i++){await new Promise(resolve=>setImmediate(resolve));global.gc();}
  assert.equal(fixture.weak.deref(),undefined);
 }else throw new Error('Run memory regressions with node --expose-gc');
 console.log('Music memory: 9 offline scenarios passed (1,000 page replacements/layout cycles; 4,800 polls; bounded faults; old DOM GC). Not real WebView/PSS.');
})().catch(e=>{console.error(e);process.exitCode=1;});
