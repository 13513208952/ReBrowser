// Isolated-world content connector, restricted to app-loopback fixture origin.
const port = browser.runtime.connectNative('validation_content');
let done = false;
port.onMessage.addListener(async message => {
  if (done || !message || message.kind !== 'READY') return;
  done = true;
  const main = await browser.runtime.sendMessage({kind: 'MAIN_CHECK'}).catch(error => ({error: String(error)}));
  const root = document.documentElement;
  port.postMessage({
    kind: 'FIXTURE_RESULT', lease: message.lease,
    case: root.getAttribute('data-rbv-case') || '',
    beforeCookie: root.getAttribute('data-rbv-before-cookie') || '',
    beforeLocal: root.getAttribute('data-rbv-before-local') || '',
    cookie: document.cookie, local: localStorage.getItem('rbv'),
    mainMarker: main && main.marker || '', ...(main && main.error ? {mainError: main.error} : {})
  });
});
