// The app permits only its foreground Rain Classroom sessions.
globalThis.rainClassActivation ??= (async () => {
  if (window !== window.top) return false;
  try {
    return (await browser.runtime.sendNativeMessage('rainclass', { type: 'activate' })) === true;
  } catch {
    return false;
  }
})();
