(() => {
'use strict';
// Read only the real HTTPS response document. No fetch, Cookie API, identity or token output.
if(window.top!==window || location.origin!=='https://music.163.com'
 || location.pathname!=='/api/w/nuser/account/get' || document.readyState!=='complete')
 return JSON.stringify({auth:'pending'});
try {
 const text=document.body?.textContent;
 if(typeof text!=='string'||text.length>65536)return JSON.stringify({auth:'unknown'});
 const response=JSON.parse(text),id=response?.profile?.userId;
 const positive=(typeof id==='string'&&/^[1-9][0-9]*$/.test(id))
  ||(typeof id==='number'&&Number.isSafeInteger(id)&&id>0);
 const auth=response?.code===200&&positive?'yes'
  :response?.code===301||response?.code===401||(response?.code===200&&response.profile===null)?'no':'unknown';
 return JSON.stringify({auth});
}catch{return JSON.stringify({auth:'unknown'});}
})();
