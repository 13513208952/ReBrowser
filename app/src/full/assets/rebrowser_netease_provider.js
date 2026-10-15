(() => {
'use strict';
if (window.top !== window || location.origin !== 'https://music.163.com' || location.pathname !== '/st/webplayer') return;
if (window.__rbNeteaseProviderV1) return;
// Fixed version-specific facade, not an arbitrary module/action/network bridge.
const playerDocument = __PLAYER_ALLOWED__;
let helper=null, api=null, sdk=null, disposed=false, hooked=false;
let auth='pending', accountBusy=false, lastAccount=0, accountFailures=0, accountGeneration=0, accountTimer=0;
let connectionPacket=null, connectionArray=null, connectionAttempts=0, connectionStarted=null, connectionFailed=false, rootRoute='';
const connectionId='rb_provider_v1';
function releaseConnection(req){
 if(connectionPacket&&connectionArray){const index=connectionArray.indexOf(connectionPacket);if(index>=0)connectionArray.splice(index,1);}
 connectionPacket=null;connectionArray=null;
 if(req){delete req.m[connectionId];delete req.c[connectionId];}
}
let allowPlay=false, audioState=0, eventPlayId='', current=0, currentKnown=false, subscriptions=0, lowPaused=false, endedAt=0;
const nativePlay=HTMLMediaElement.prototype.play;
Object.defineProperty(HTMLMediaElement.prototype,'play',{configurable:true,writable:true,value:function(){
 if(!disposed && playerDocument && auth==='yes' && allowPlay) return nativePlay.call(this);
 return Promise.reject(new DOMException('Music starts paused; explicit playback required','NotAllowedError'));
}});
function trusted(e){
 if(!e.isTrusted||!playerDocument||auth!=='yes')return;
 const target=e.target;
 if(target?.closest?.('input,textarea,[contenteditable="true"]'))return;
 const button=target?.closest?.('button,[role="button"],.play-all');
 const label=button?(button.getAttribute('aria-label')||button.getAttribute('title')||button.querySelector('.cmd-icon[title]')?.getAttribute('title')||button.querySelector('svg title')?.textContent||button.textContent||'').trim():'';
 const playIntent=button?.id==='btn_pc_minibar_play'||button?.classList?.contains('play-all')||/^(播放|继续播放|上一首|下一首)/.test(label);
 const rowDoubleClick=e.type==='dblclick'&&target?.closest?.('.StyledTableViewer_sv4xczm .tr');
 const keyPlay=e.type==='keydown'&&!e.repeat&&[' ','MediaPlayPause','MediaPlay'].includes(e.key);
 if(playIntent||rowDoubleClick||keyPlay){
  let playing=false;try{playing=helper?.getStore()?.playing?.playingState===2;}catch{}
  allowPlay=!/^暂停/.test(label)&&!(keyPlay&&e.key!=='MediaPlay'&&playing);
 }
}
document.addEventListener('pointerup',trusted,true);
document.addEventListener('dblclick',trusted,true);
document.addEventListener('keydown',trusted,true);
function known(){return Array.from(document.scripts||[]).some(s=>{try{const u=new URL(s.src);return u.protocol==='https:'&&u.hostname==='s5.music.126.net'&&u.pathname.endsWith('/hybrid/app.4c9df986.js');}catch{return false;}});}
function connect(){
 if(disposed||helper||connectionFailed)return;
 if(connectionStarted!==null&&(Date.now()-connectionStarted>=30000||connectionAttempts>=40)){
  releaseConnection();connectionFailed=true;return;
 }
 if(connectionPacket||!known()||!Array.isArray(window.webpackJsonp))return;
 if(connectionStarted===null)connectionStarted=Date.now();
 connectionAttempts++;
 const entry={};entry[connectionId]=(module,exports,req)=>{try{
  if(disposed||connectionFailed)return;
  const h=req(14).a,a=req(15);let s;try{s=req(4);}catch{}
  if(h&&typeof h.getStore==='function'&&typeof h.getDispatch==='function'&&typeof a.Ah==='function'
    &&(!playerDocument||typeof s?.AudioPlayer?.subscribePlayStatus==='function')){
   helper=h;api=a;sdk=s?.AudioPlayer;rootRoute=route();
  }
 }catch{}finally{releaseConnection(req);}};
 connectionArray=window.webpackJsonp;connectionPacket=[[connectionId],entry,[[connectionId]]];
 try{connectionArray.push(connectionPacket);}catch{releaseConnection();connectionFailed=true;}
}
function positive(v){return (typeof v==='string'&&/^[1-9][0-9]*$/.test(v))||(typeof v==='number'&&Number.isSafeInteger(v)&&v>0);}
function account(){
 if(!api||accountBusy||disposed||Date.now()-lastAccount<(auth==='yes'?60000:5000))return;
 // Bounded pending/login checks; manual recheck restarts this budget.
 if(auth!=='yes'&&accountFailures>=60)return;
 accountBusy=true;lastAccount=Date.now();const epoch=++accountGeneration;
 const timeout=accountTimer=setTimeout(()=>{if(accountGeneration===epoch){accountTimer=0;accountGeneration++;accountBusy=false;accountFailures++;}},8000);
 Promise.resolve(api.Ah({})).then(s=>{
  if(disposed||epoch!==accountGeneration)return;
  if(s?.code===200&&positive(s?.profile?.userId)){auth='yes';accountFailures=0;}
  else if(s?.code===200||s?.code===301||s?.code===401){auth='no';allowPlay=false;accountFailures++;}
  else accountFailures++;
 }).catch(()=>{if(epoch===accountGeneration)accountFailures++;}).finally(()=>{clearTimeout(timeout);if(accountTimer===timeout)accountTimer=0;if(epoch===accountGeneration)accountBusy=false;});
}
function subscribe(){
 if(hooked||typeof sdk?.subscribePlayStatus!=='function'||!playerDocument||auth!=='yes')return;
 hooked=true;
 for(const type of ['playstate','playprogress','end','seek']){
  Promise.resolve(sdk.subscribePlayStatus({type,callback:e=>{
   if(disposed||typeof e?.playId!=='string')return;
   let p;try{p=helper.getStore()?.playing;}catch{return;}
   if(!p||e.playId!==p.playId)return;
   if(eventPlayId!==e.playId){eventPlayId=e.playId;current=0;currentKnown=false;audioState=0;}
   if(type==='playstate'){audioState=e.state===1?1:0;lowPaused=e.state===2;}
   if(type==='end'){audioState=0;lowPaused=false;endedAt=Date.now();}
   if(type==='seek'&&e.code!==0)return;
   if(type==='playprogress'||type==='seek'){const n=Number(type==='seek'?e.position:e.current);if(Number.isFinite(n)&&n>=0&&n<=86400){current=n;currentKnown=true;}}
  }})).then(ok=>{if(ok===true)subscriptions++;}).catch(()=>{});
 }
}
function likeButton(){
 const buttons=Array.from(document.querySelectorAll('footer.Container_col456s button'));
 return buttons.find(b=>!b.disabled&&b.getAttribute('aria-disabled')!=='true'&&[
  b.getAttribute('aria-label'),b.getAttribute('title'),b.querySelector('.cmd-icon[title]')?.getAttribute('title'),
  b.querySelector('svg title')?.textContent
 ].some(label=>/^(取消喜欢|喜欢|喜欢音乐|取消喜欢音乐)(?:$|[（(])/.test(label||''))&&b.getClientRects().length>0)||null;
}
function state(){
 connect();account();subscribe();
 let s,p;try{s=helper?.getStore();p=s?.playing;}catch{}
 const ready=playerDocument&&auth==='yes'&&!!p&&subscriptions===4;
 if(ready&&!rootRoute)rootRoute=route();
 const id=String(p?.resourceTrackId||'');
 const same=eventPlayId!==''&&eventPlayId===p?.playId;
 const start=Number(p?.freeTrialInfo?.start)||0;
 const duration=Number(p?.resourceDuration)||0;
 const liked=!!s?.['async:hostResource']?.likeTracksMap?.[p?.onlineResourceId];
 // The official restore resource is authoritative before the engine has loaded audio,
 // including a paused seek. A pause callback alone must not turn restored progress into zero.
 const position=p?.restoreResource?Number(p.restoreResource.current||0)+start:(same&&currentKnown?current+start:0);
 return {auth,ready,unavailable:connectionFailed,
  buffering:ready&&allowPlay&&(!same||!lowPaused)&&(!same||audioState!==1)&&(p?.playingState===2||p?.isLoadingFirst||Date.now()-endedAt<3000),playing:ready&&same&&audioState===1&&p?.playingState===2,
  track:id.slice(0,128),title:String(p?.resourceName||'').slice(0,240),
  artist:(Array.isArray(p?.resourceArtists)?p.resourceArtists.slice(0,16).map(a=>String(a?.name||'')).join(' / '):'').slice(0,240),
  album:String(p?.curTrack?.album?.name||'').slice(0,240),cover:String(p?.resourceCoverUrl||'').slice(0,2048),
  position:Math.round(position*1000),duration:Math.round(duration*1000),speed:Number(p?.playingSpeed)||1,
  canSeek:ready&&duration>0,canSkip:ready&&Array.isArray(s?.playingList?.curPlayingList)&&s.playingList.curPlayingList.length>0,
  canLike:ready&&!!likeButton(),liked};
}
function command(kind,value,expected){
 if(disposed||!playerDocument||auth!=='yes'||!helper)return 'not-ready';
 const s=helper.getStore(),p=s?.playing;if(!p)return 'not-ready';
 if(expected&&String(p.resourceTrackId||'')!==expected)return 'track-changed';
 const dispatch=helper.getDispatch();
 switch(kind){
  case 'pause':allowPlay=false;dispatch({type:'playing/pause',payload:{}});return 'submitted';
  case 'resume':allowPlay=true;lowPaused=false;dispatch({type:'playing/resume',payload:{}});return 'submitted';
  case 'previous':case 'next':
   if(!Array.isArray(s?.playingList?.curPlayingList)||!s.playingList.curPlayingList.length)return 'unsupported';
   allowPlay=true;dispatch({type:'playingList/jump2Track',payload:{flag:kind==='next'?1:-1,type:'call'}});return 'submitted';
  case 'seek':{
   if(typeof value!=='number'||!Number.isFinite(value)||value<0)return 'invalid-position';
   const seconds=value/1000,start=Number(p.freeTrialInfo?.start)||0,end=Number(p.freeTrialInfo?.end)||Number(p.resourceDuration);
   if(!Number.isFinite(end)||seconds<start||seconds>end)return 'outside-playable-range';
   dispatch({type:'playing/setPlayingPosition',payload:{duration:seconds-start}});return 'submitted';
  }
  case 'like':{
   if(value!==0&&value!==1)return 'invalid-like-state';
   const button=likeButton();if(!button)return 'unsupported';
   const actual=!!s?.['async:hostResource']?.likeTracksMap?.[p.onlineResourceId];
   if(actual!==(value===1))button.click();
   return 'submitted';
  }
  default:return 'unsupported';
 }
}
// Only fixed official navigation is exposed; never return the store/history/dispatch objects.
function route(){try{const path=helper?.getHistory?.()?.location?.pathname;return typeof path==='string'?path.slice(0,512):'';}catch{return '';}}
function visible(node){
 if(!node?.getClientRects?.().length||node.closest?.('[hidden],[aria-hidden="true"],[inert]'))return false;
 const rect=node.getBoundingClientRect?.();
 if(rect&&(rect.width<=0||rect.height<=0||rect.right<=0||rect.bottom<=0||rect.left>=window.innerWidth||rect.top>=window.innerHeight))return false;
 for(let parent=node;parent;parent=parent.parentElement){
  const css=getComputedStyle(parent);
  if(css.display==='none'||css.visibility==='hidden'||css.visibility==='collapse'||css.opacity==='0')return false;
 }
 return true;
}
function closeButton(button){
 if(!visible(button)||button.disabled||button.getAttribute('aria-disabled')==='true')return false;
 button.click();return true;
}
function back(){
 if(disposed||!playerDocument||!helper)return 'layer';
 try{
  // Never confirm an operation or mutate React props. Use only original close controls.
  const dialogs=Array.from(document.querySelectorAll('[role="dialog"],.cmd-modal,.cmd-sidesheet')).filter(visible);
  if(dialogs.length){
   const top=dialogs.map((node,index)=>({node,index,z:Number(getComputedStyle(node).zIndex)||0})).sort((a,b)=>a.z-b.z||a.index-b.index).at(-1).node;
   return closeButton(top.querySelector('button.cmd-modal-close,button.cmd-sidesheet-close'))?'handled':'layer';
  }
  const input=document.querySelector('.SearchWrapper_s14tpfr6 input');
  const search=visible(document.querySelector('.SearchResults_s92u6ho'));
  const popover=Array.from(document.querySelectorAll('.cmd-popover')).some(visible);
  if(search||popover){
   input?.blur();
   // Known UI module 3 listens for window mousedown; blur alone only changes focus styling.
   // This takes the official outside-click path and never clears the query or arms playback.
   document.body.dispatchEvent(new MouseEvent('mousedown',{bubbles:true,cancelable:true,view:window}));
   return 'handled';
  }
  if(input&&document.activeElement===input){input.blur();return 'handled';}
  const page=helper.getStore()?.['page:vinylPage'];
  if(page?.pageState&&page.pageState!=='closed'){
   return closeButton(document.querySelector('.Container_c1m2d5ze .close button'))?'handled':'layer';
  }
  const history=helper.getHistory?.();
  if(Number.isSafeInteger(history?.index)&&history.index>=0){
   if(history.index===0)return 'root';
   if(typeof history.goBack!=='function')return 'layer';
   history.goBack();return 'handled';
  }
  if(rootRoute&&route()!==rootRoute&&typeof history?.goBack==='function'){history.goBack();return 'handled';}
  return 'root';
 }catch{return 'layer';}
}
function cleanup(){
 if(disposed)return;disposed=true;allowPlay=false;accountGeneration++;clearTimeout(accountTimer);accountTimer=0;accountBusy=false;
 releaseConnection();helper=null;api=null;sdk=null;rootRoute='';
 for(const type of ['pointerup','dblclick','keydown'])document.removeEventListener(type,trusted,true);
}
window.__rbNeteaseProviderV1=Object.freeze({state,command,route,back,recheck:()=>{
 if(disposed)return;
 releaseConnection();connectionAttempts=0;connectionStarted=null;connectionFailed=false;accountFailures=0;lastAccount=0;
 connect();account();
},cleanup});
})();
