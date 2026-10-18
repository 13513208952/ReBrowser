// Auxiliary fixture only: no accounts, media, page exports or cleanup.
const native = browser.runtime.connectNative('validation_background');
native.postMessage({kind: 'BACKGROUND_HELLO'});
browser.runtime.onMessage.addListener(async (message, sender) => {
  if (!message || message.kind !== 'MAIN_CHECK' || !sender.tab || sender.frameId !== 0) return undefined;
  try {
    const result = await browser.scripting.executeScript({
      target: {tabId: sender.tab.id, frameIds: [0]}, world: 'MAIN',
      func: () => ({marker: window.rbvMainMarker})
    });
    return {marker: result[0] && result[0].result && result[0].result.marker};
  } catch (error) { return {error: String(error)}; }
});
