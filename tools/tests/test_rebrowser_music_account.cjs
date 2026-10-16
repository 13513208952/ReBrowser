'use strict';
// Fixed response-reader fixture only; no network, real Profile or account access.
const assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs');
const source=fs.readFileSync('app/src/full/assets/rebrowser_netease_account.js','utf8');
let checks=0;
function read(text,{origin='https://music.163.com',path='/api/w/nuser/account/get',frame=false,state='complete'}={}){
 const document={readyState:state,body:{textContent:text}};
 Object.defineProperty(document,'cookie',{get(){throw new Error('Cookie access forbidden');}});
 const context=vm.createContext({location:{origin,pathname:path},document});context.window=context;context.top=frame?{}:context;
 const out=JSON.parse(vm.runInContext(source,context));assert.deepEqual(Object.keys(out),['auth']);checks++;return out.auth;
}
for(const id of [1,42,Number.MAX_SAFE_INTEGER,'1','4242'])assert.equal(read(JSON.stringify({code:200,profile:{userId:id,privateToken:'FIXTURE_NOT_FOR_OUTPUT'},account:{id:99}})),'yes');
for(const id of [0,-1,1.5,Number.MAX_SAFE_INTEGER+1,'0','01','-1','1.2','NaN',true,null,{},[]])assert.equal(read(JSON.stringify({code:200,profile:{userId:id}})),'unknown');
for(const data of [{code:200,profile:null},{code:301},{code:401}])assert.equal(read(JSON.stringify(data)),'no');
for(const text of ['',null,'not-json','<html>denied</html>','null','[]','{}',JSON.stringify({code:500}),JSON.stringify({code:'200',profile:{userId:42}}),' '.repeat(65537)])assert.equal(read(text),'unknown');
const valid=JSON.stringify({code:200,profile:{userId:42}});
for(const options of [{origin:'http://music.163.com'},{origin:'https://evil.invalid'},{origin:'https://music.163.com.evil.invalid'},{path:'/st/webplayer'},{path:'/api/w/nuser/account/get/'},{frame:true},{state:'loading'},{state:'interactive'}])assert.equal(read(valid,options),'pending');
assert.equal(read(valid),'yes');assert.equal(read(JSON.stringify({code:200,profile:null})),'no'); // no retained positive decision
console.log(`Music light-account reader: ${checks} bounded/tri-state/origin/frame/privacy fixtures passed; not device or network acceptance.`);
