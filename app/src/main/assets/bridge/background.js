/* Extension-local routing PRECHECK only, not native document/port/account/Actor authority.
 * No native background port, remote network, arbitrary scripts, cookies/storage or page relay.
 * Full native broker checks remain mandatory even if these hints are forged/replayed. */
(() => {
  "use strict";
  const ID = "rebrowser-builtin@io.github.rebrowser", VERSION = "1.0.0", routes = new Map();
  const hosts = Object.freeze({rebrowser_music: ["music.163.com"], rebrowser_outlook: ["outlook.live.com", "outlook.office.com", "outlook.office365.com"]});
  function precheck(request, sender) {
    if (!request || typeof request !== "object" || Object.keys(request).length !== 3 || request.kind !== "rebrowser.route.precheck.1" || !Object.prototype.hasOwnProperty.call(hosts, request.nativeApp) || typeof request.documentHint !== "string" || !/^[0-9a-f]{64}$/.test(request.documentHint)) return undefined;
    if (browser.runtime.id !== ID || browser.runtime.getManifest().version !== VERSION || !sender || sender.id !== ID || sender.frameId !== 0 || !sender.tab || !Number.isSafeInteger(sender.tab.id) || sender.tab.id < 0 || typeof sender.url !== "string" || sender.url.length > 8192) return undefined;
    let url; try { url = new URL(sender.url); } catch (_) { return undefined; }
    if (url.protocol !== "https:" || url.username || url.password || url.port || !hosts[request.nativeApp].includes(url.hostname)) return undefined;
    const previous = routes.get(sender.tab.id);
    if (previous && previous.documentHint === request.documentHint || !previous && routes.size >= 64) return undefined;
    routes.set(sender.tab.id, Object.freeze({documentHint: request.documentHint, nativeApp: request.nativeApp}));
    return Promise.resolve({precheckOnly: true, version: VERSION, documentHint: request.documentHint});
  }
  browser.runtime.onMessage.addListener(precheck);
  browser.tabs.onRemoved.addListener(tabId => {routes.delete(tabId);});
  // These events retire ONLY extension-local hints. They never release native request/stream work.
  browser.tabs.onUpdated.addListener((tabId, change) => {if (change.status === "loading") routes.delete(tabId);});
})();
