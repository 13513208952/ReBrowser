// Synthetic connection only. No cookies, site account, storage erasure or audio.
const port = browser.runtime.connectNative('validation');
port.onMessage.addListener(message => {
  if (message && message.kind === 'READY') port.postMessage({kind: 'SYNTHETIC_RESULT', value: 7});
});
port.postMessage({kind: 'HELLO', protocol: 'validation-only/1'});
