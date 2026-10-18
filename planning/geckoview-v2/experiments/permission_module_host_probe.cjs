'use strict';
// Execute exact pinned Mozilla modules with ONLY synthetic Services/actors.
// HOST component exercise, NOT Android/Gecko permission-manager/runtime validation.
const fs = require('node:fs');
const vm = require('node:vm');
const crypto = require('node:crypto');
const assert = require('node:assert/strict');
const path = require('node:path');
const [permissionFile, storageFile] = process.argv.slice(2);
assert(permissionFile && storageFile, 'supply private pinned module paths; never download');
const sources = [permissionFile, storageFile].map(p => ({file:p, bytes:fs.readFileSync(p)}));
const digest = bytes => crypto.createHash('sha256').update(bytes).digest('hex');
const expected = JSON.parse(fs.readFileSync(path.join(__dirname, '../evidence/gecko-permission-api-and-lifetime.json')))
  .sources.find(s => s.upstreamPath.includes('GeckoViewPermission.sys.mjs')).sha256;
assert.equal(digest(sources[0].bytes), expected);
assert.equal(digest(sources[1].bytes), '427027c14b47c1904ef75240962b225468e38155d6e34ae86b8f1ef06737913f');
let checks = 0;
const eq = (a,b) => { assert.deepEqual(a,b);checks++; };
function harness() {
  const rows = new Map(), writes = [], calls = [], principals = new Map();
  const key = (p,type) => JSON.stringify([p.URI.displaySpec,p.originAttributes.geckoViewSessionContextId ?? null,p.privateBrowsingId,type]);
  const makePrincipal = (uri,context='ctx-a',privateId=0) => ({URI:{displaySpec:uri},origin:new URL(uri).origin,originAttributes:{geckoViewSessionContextId:context},privateBrowsingId:privateId});
  const serialize = p => { const k=JSON.stringify(p);principals.set(k,p);return k; };
  const perms = {ALLOW_ACTION:1,DENY_ACTION:2,PROMPT_ACTION:3,EXPIRE_NEVER:0,
    addFromPrincipal(p,type,value,expire) { const row={principal:p,type,capability:value,expire};rows.set(key(p,type),row);writes.push(row); },
    addFromPrincipalAndPersistInPrivateBrowsing(p,type,value) { calls.push('permanent-private');this.addFromPrincipal(p,type,value,'persist-private'); },
    getAllForPrincipal(p) { return [...rows.values()].filter(r=>key(r.principal,'')===key(p,'')); },
    testPermissionFromPrincipal(p,type) { return rows.get(key(p,type))?.capability ?? 3; },
    get all() { return [...rows.values()]; }
  };
  const context = vm.createContext({
    Services:{perms,uuid:{generateUUID:()=>'{synthetic-id}'},prefs:{getBoolPref:()=>true},
      io:{newURI:uri=>({displaySpec:uri}),createExposableURI:uri=>uri},
      scriptSecurityManager:{createContentPrincipal:(uri,attrs)=>makePrincipal(uri.displaySpec,attrs.geckoViewSessionContextId,attrs.privateBrowsingId)}},
    Ci:{nsIArray:{},nsIContentPermissionType:{},nsIClearDataService:new Proxy({}, {get:()=>0}),nsIPermissionManager:{EXPIRE_SESSION:2,EXPIRE_NEVER:0}},
    Components:{ID:x=>x},console:{error:()=>{}},
    GeckoViewUtils:{initLogging:()=>({debug:()=>{},warn:()=>{}})},
    ChromeUtils:{generateQI:()=>()=>{},defineESModuleGetters(target) {
      target.E10SUtils={serializePrincipal:serialize,deserializePrincipal:s=>{assert(principals.has(s));return principals.get(s);}};
      target.EventDispatcher={instance:{registerListener:()=>{}}};
      target.PrincipalsCollector=class { async getAllPrincipals(){throw Error('no clearing paths allowed');} };
    }},
  }, {codeGeneration:{strings:false,wasm:false}});
  const transform = s => s.replace('import { GeckoViewUtils } from "resource://gre/modules/GeckoViewUtils.sys.mjs";', '')
    .replace('export class GeckoViewPermission', 'class GeckoViewPermission')
    .replace('export const GeckoViewStorageController', 'const GeckoViewStorageController');
  vm.runInContext(`(()=>{${transform(sources[0].bytes.toString('utf8'))}\nglobalThis.PermissionSubject=GeckoViewPermission;})()`,context,{timeout:2000});
  vm.runInContext(`(()=>{${transform(sources[1].bytes.toString('utf8'))}\nglobalThis.StorageSubject=GeckoViewStorageController;})()`,context,{timeout:2000});
  return {subject:new context.PermissionSubject(),storage:context.StorageSubject,makePrincipal,serialize,perms,writes,calls};
}
function request(h,type,{value=1,os=true,gesture=true,third=false,privateId=0,deferred=null,count=1}={}) {
  const top=h.makePrincipal('https://top.synthetic.invalid/','ctx-a',privateId);
  const principal=third?h.makePrincipal('https://frame.synthetic.invalid/','ctx-a',privateId):top;
  const observed={allow:0,cancel:0,shown:0,bundle:null,appPermissions:[]};
  const actor={async getContentPermission(bundle) { observed.bundle=bundle;return deferred?deferred:value; },
    async getAppPermissions(names) { observed.appPermissions.push([...names]);return os; }};
  const req={types:{QueryInterface:()=>({length:count,queryElementAt:()=>({type,capability:3})})},
    principal,topLevelPrincipal:top,hasValidTransientUserGestureActivation:gesture,
    window:{browsingContext:{currentWindowGlobal:{getActor:()=>actor}}},
    allow:()=>observed.allow++,cancel:()=>observed.cancel++,notifyShown:()=>observed.shown++};
  return {req,observed,top,principal};
}
(async()=>{
  {
    const h=harness(), r=request(h,'geolocation',{third:true});await h.subject.prompt(r.req);
    eq(r.observed.allow,1);eq(r.observed.cancel,0);eq(r.observed.appPermissions,[['android.permission.ACCESS_FINE_LOCATION']]);
    eq(h.writes.length,1);eq(h.writes[0].expire,0);eq(h.writes[0].principal,r.top);
    eq(r.observed.bundle.thirdPartyOrigin,r.principal.origin);
    eq(Object.hasOwn(r.observed.bundle,'deadline'),false);
  }
  {
    const h=harness(), r=request(h,'geolocation',{os:false});await h.subject.prompt(r.req);
    eq(r.observed.allow,0);eq(r.observed.cancel,1);eq(h.writes[0].capability,2);
  }
  {
    const h=harness(),r=request(h,'desktop-notification',{gesture:false});await h.subject.prompt(r.req);
    eq(r.observed.cancel,1);eq(r.observed.bundle,null);eq(h.writes.length,0);
  }
  {
    const h=harness(),r=request(h,'storage-access',{third:true});await h.subject.prompt(r.req);
    eq(r.observed.allow,1);eq(h.writes.length,0);eq(r.observed.bundle.uri,r.principal.URI.displaySpec);
  }
  {
    let resolve;const deferred=new Promise(r=>{resolve=r;});const h=harness(),r=request(h,'geolocation',{deferred});
    const done=h.subject.prompt(r.req);assert(r.observed.bundle);checks++;
    const id=r.observed.bundle.requestId;
    h.subject.onEvent('GeckoView:ContentPermissionShown',{requestId:id});
    h.subject.onEvent('GeckoView:ContentPermissionShown',{requestId:id});eq(r.observed.shown,1);
    resolve(1);await done;
    h.subject.onEvent('GeckoView:ContentPermissionShown',{requestId:id});eq(r.observed.shown,1);
  }
  {
    const h=harness(),r=request(h,'geolocation',{count:2});await h.subject.prompt(r.req);
    eq(r.observed.cancel,1);eq(r.observed.bundle,null);
  }
  {
    const h=harness(),normal=h.makePrincipal('https://top.synthetic.invalid/','ctx-a'),other=h.makePrincipal('https://top.synthetic.invalid/','ctx-b'),pb=h.makePrincipal('https://top.synthetic.invalid/','ctx-a',1);
    let callbacks=0;const cb={onSuccess:()=>callbacks++};
    for(const p of [normal,other,pb]) h.storage.onEvent('GeckoView:SetPermission',{principal:h.serialize(p),perm:'geolocation',newValue:1,privateMode:p.privateBrowsingId!==0,allowPermanentPrivateBrowsing:false},cb);
    eq(callbacks,0);eq(h.writes.map(x=>x.expire),[0,0,2]);
    let result;h.storage.onEvent('GeckoView:GetPermissionsByURI',{uri:normal.URI.displaySpec,contextId:'ctx-a',privateBrowsingId:0},{onSuccess:r=>{result=r;}});
    eq(result.permissions.length,1);eq(result.permissions[0].contextId,'ctx-a');eq(result.permissions[0].privateMode,false);
    h.storage.onEvent('GeckoView:SetPermission',{principal:h.serialize(normal),perm:'storage-access',thirdPartyOrigin:'https://frame.synthetic.invalid',newValue:1,privateMode:false,allowPermanentPrivateBrowsing:false},cb);
    eq(h.writes.at(-1).type,'3rdPartyFrameStorage^https://frame.synthetic.invalid');
    // Java has already filtered forbidden tracking-to-PROMPT cases; direct module call is not public API proof.
  }
  console.log(JSON.stringify({scope:'HOST executes pinned Mozilla module bodies with synthetic actors/Services; no Android/real Gecko permission-manager claim',checks,
    sourceModuleSha256:sources.map(x=>digest(x.bytes)),ordinaryPermissionWritesExpireNeverObserved:true,scopedSyntheticReadbackObserved:true,
    setPermissionDispatchCompletionCallbackObserved:false,notifyShownCountOnceObserved:true,realPermissionOrClipboardLocationAccess:false},null,2));
})().catch(error=>{console.error(error);process.exitCode=1;});
