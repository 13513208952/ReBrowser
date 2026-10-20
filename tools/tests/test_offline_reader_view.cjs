#!/usr/bin/env node
'use strict';
// Actual runtime reader.js in an explicitly SYNTHETIC bounded DOM, NOT Gecko/CSP enforcement.
const fs=require('fs'),vm=require('vm'),assert=require('assert'),path=require('path');
let checks=0;function ok(v){checks++;assert(v);}function throws(fn){checks++;assert.throws(fn);}
class N {
 constructor(tag,text=''){this.tag=tag;this.children=[];this.value=text;this.hidden=false;this.attrs={};}
 get firstChild(){return this.children[0]||null;}
 appendChild(n){if(n.tag==='#fragment'){for(const c of [...n.children])this.appendChild(c);n.children=[];}else this.children.push(n);return n;}
 removeChild(n){const at=this.children.indexOf(n);assert(at>=0);this.children.splice(at,1);return n;}
 set textContent(v){this.children=[];this.value=String(v);}get textContent(){return this.value+this.children.map(c=>c.textContent).join('');}
 setAttribute(k,v){this.attrs[k]=String(v);}
}
const nodes={};for(const id of ['subject','meta','body','attachments','status'])nodes[id]=new N(id);
const doc={getElementById:id=>nodes[id],createElement:tag=>new N(tag),createTextNode:text=>new N('#text',text),createDocumentFragment:()=>new N('#fragment')};
const ctx=vm.createContext({document:doc,window:{}});const file=path.resolve(__dirname,'../../app/src/main/assets/offline-mail/reader.js');
vm.runInContext(fs.readFileSync(file,'utf8'),ctx,{filename:file,timeout:2000});
const run=code=>vm.runInContext(code,ctx,{timeout:2000});const render=o=>run('window.ReBrowserReader.render('+JSON.stringify(o)+')');
ok(nodes.body.textContent==='');
render({subject:'<img src=x onerror=bad()>',meta:'m',paragraphs:['<script>bad()</script>','a & b'],attachments:['<x>.txt']});
ok(nodes.subject.textContent.includes('<img'));ok(nodes.body.textContent.includes('<script>'));ok(nodes.body.children.every(n=>n.tag==='p'));ok(nodes.attachments.textContent.includes('<x>.txt'));
throws(()=>render({subject:'secret',paragraphs:['good','x'.repeat(20001)]}));ok(nodes.body.textContent==='');ok(nodes.subject.textContent==='离线邮件');
throws(()=>render({paragraphs:['x'.repeat(20000),...Array(60).fill('x'.repeat(20000))]}));ok(nodes.body.textContent==='');
throws(()=>render({paragraphs:['\ud800']}));throws(()=>render({paragraphs:[],extra:true}));
render({subject:'sensitive title',meta:'private account',paragraphs:['aaaa '.repeat(100)]});
const count=n=>1+n.children.reduce((a,c)=>a+count(c),0);
for(let i=0;i<30;i++)run('window.ReBrowserReader.highlight("aa")');ok(count(nodes.body)<2000);ok(nodes.body.textContent==='aaaa '.repeat(100));
throws(()=>run('window.ReBrowserReader.highlight("")'));
run('window.ReBrowserReader.showFailure("failed")');ok(nodes.subject.textContent==='离线邮件');ok(nodes.meta.textContent==='');ok(nodes.body.textContent==='');ok(nodes.attachments.hidden);
throws(()=>run('window.ReBrowserReader.render({paragraphs:new Array(3)})'));
console.log(JSON.stringify({syntheticDomAssertionsPassed:checks,runtimeJavaScriptActuallyExecuted:true,actualGeckoRenderingOrCspEnforcement:false,sourceFile:'app/src/main/assets/offline-mail/reader.js'}));
