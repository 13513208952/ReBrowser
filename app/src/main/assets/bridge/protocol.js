/* Fixed built-in ISOLATED-world protocol. Never injects MAIN/eval, observes controls from DOM,
 * reads cookies/storage/credentials or accepts window.postMessage/page commands. Native READY
 * tokens bind routing only; RESULT is a page claim, never account/effect/native-work completion. */
(() => {
  "use strict";
  const NAME = "__rbBuiltinProtocolV1", PROTOCOL = "rebrowser.connector/1";
  if (Object.prototype.hasOwnProperty.call(globalThis, NAME)) return;
  const BIND = ["dataEpoch", "partitionRef", "partitionGeneration", "sessionRef", "sessionGeneration", "documentLease", "portLeaseToken"];
  function exact(value, keys) { return value !== null && typeof value === "object" && !Array.isArray(value) && Object.keys(value).length === keys.length && keys.every(k => Object.prototype.hasOwnProperty.call(value, k)); }
  const ascii = (s, max) => typeof s === "string" && s.length > 0 && s.length <= max && /^[\x21-\x7e]+$/.test(s);
  const integer = n => Number.isSafeInteger(n) && n >= 0;
  const hex = (s, length) => typeof s === "string" && s.length === length && /^[0-9a-f]+$/.test(s);
  function binding(b) { return exact(b, BIND) && hex(b.dataEpoch, 32) && hex(b.documentLease, 64) && hex(b.portLeaseToken, 64) && ascii(b.partitionRef, 64) && ascii(b.sessionRef, 64) && integer(b.partitionGeneration) && integer(b.sessionGeneration); }
  function same(a, b) { return binding(b) && BIND.every(k => a[k] === b[k]); }
  function boundedJson(root) {
    let nodes = 0;
    function visit(v, depth) {
      if (++nodes > 4096 || depth > 12) throw Error("bounded protocol");
      if (typeof v === "string") { if (v.length > 8192) throw Error("bounded string"); for (let i = 0; i < v.length; i++) { const n = v.charCodeAt(i); if (n >= 0xd800 && n <= 0xdbff) { const tail = v.charCodeAt(++i); if (!(tail >= 0xdc00 && tail <= 0xdfff)) throw Error("UTF16"); } else if (n >= 0xdc00 && n <= 0xdfff) throw Error("UTF16"); } return; }
      if (typeof v === "number") { if (!Number.isFinite(v) || Math.abs(v) > Number.MAX_SAFE_INTEGER) throw Error("number"); return; }
      if (v === null || typeof v === "boolean") return;
      if (Array.isArray(v)) { if (v.length > 256) throw Error("array"); v.forEach(x => visit(x, depth + 1)); return; }
      if (typeof v !== "object" || Object.getPrototypeOf(v) !== Object.prototype) throw Error("shape");
      const keys = Object.keys(v); if (keys.length > 64) throw Error("fields"); keys.forEach(k => { if (!ascii(k, 128)) throw Error("field"); visit(v[k], depth + 1); });
    }
    visit(root, 0); const text = JSON.stringify(root); if (text.length > 65536 || new TextEncoder().encode(text).length > 65536) throw Error("envelope"); return text;
  }
  /* Bounded retained display text, no textContent/innerHTML/full-subtree serialization. DOM/SDK
   * engine allocations preceding JS access are separate, not claimed as whole-process bounds. */
  function displayText(node, maximumBytes) {
    if (!node) return "";
    const walker = document.createTreeWalker(node, NodeFilter.SHOW_TEXT), encoder = new TextEncoder();
    let result = "", used = 0, visited = 0;
    for (let text = walker.nextNode(); text && ++visited <= 128; text = walker.nextNode()) {
      const data = text.data;
      for (let i = 0; i < data.length;) {
        let cp = data.codePointAt(i); i += cp > 65535 ? 2 : 1;
        if (cp >= 0xd800 && cp <= 0xdfff) throw Error("DOM UTF16");
        if (cp < 32 || cp === 127) cp = 32;
        const ch = String.fromCodePoint(cp), n = encoder.encode(ch).length;
        if (used + n > maximumBytes) return result.trim(); result += ch; used += n;
      }
    }
    return result.trim();
  }
  let opened = false;
  function open(nativeApp, action, observe) {
    if (opened || window !== window.top || typeof observe !== "function" || !["rebrowser_music", "rebrowser_outlook"].includes(nativeApp) || action !== (nativeApp === "rebrowser_music" ? "music.observe" : "outlook.observe")) throw Error("native route");
    opened = true; // One attempt per actual content-script lifetime; unknown never reconnects.
    const random = new Uint8Array(32); crypto.getRandomValues(random); const hint = Array.from(random, n => n.toString(16).padStart(2, "0")).join("");
    let port = null, ready = null, retired = false, sending = false; const requests = new Set();
    function retire() { if (retired) return; retired = true; ready = null; if (port) { try { port.disconnect(); } catch (_) { /* unknown, no second route */ } } }
    function post(value) { if (retired || !port || sending) throw Error("native port unavailable"); const text = boundedJson(value); sending = true; try { port.postMessage(text); } finally { sending = false; } }
    function receive(message) {
      try {
        if (retired || sending || !message || typeof message !== "object" || message.protocol !== PROTOCOL) throw Error("native envelope");
        if (!ready) {
          if (!exact(message, ["protocol", "kind", "binding", "limits", "supportedActions"]) || message.kind !== "READY" || !binding(message.binding) || !exact(message.limits, ["chunkBytes", "creditBytes"]) || message.limits.chunkBytes !== 32768 || message.limits.creditBytes !== 65536 || !Array.isArray(message.supportedActions) || message.supportedActions.length !== 1 || message.supportedActions[0] !== action) throw Error("native READY");
          ready = Object.freeze({...message.binding}); return;
        }
        if (!same(ready, message.binding) || typeof message.requestId !== "string" || !/^r_[0-9a-f]{32}$/.test(message.requestId)) throw Error("native binding");
        if (message.kind === "CANCEL") {
          if (!exact(message, ["protocol", "kind", "binding", "requestId"]) || !requests.has(message.requestId)) throw Error("native cancel");
          post({protocol: PROTOCOL, kind: "CANCEL_STATUS", binding: ready, requestId: message.requestId, cancelState: "OBSERVATION_ALREADY_RETURNED"}); return;
        }
        if (!exact(message, ["protocol", "kind", "binding", "requestId", "action", "args"]) || message.kind !== "COMMAND" || message.action !== action || !exact(message.args, []) || requests.has(message.requestId) || requests.size >= 32) throw Error("native command");
        requests.add(message.requestId); // Spend before acceptance/post/DOM callback; failures cannot replay.
        post({protocol: PROTOCOL, kind: "ACCEPTED", binding: ready, requestId: message.requestId});
        const result = observe(); // Fixed synchronous read-only DOM function, never arbitrary page code.
        post({protocol: PROTOCOL, kind: "RESULT", binding: ready, requestId: message.requestId, outcome: "observed", evidenceKind: "page-observation", result});
      } catch (_) { retire(); }
    }
    globalThis.addEventListener("pagehide", retire, {once: true});
    try {
      const checked = browser.runtime.sendMessage({kind: "rebrowser.route.precheck.1", nativeApp, documentHint: hint});
      Promise.resolve(checked).then(response => {
        if (retired) return;
        if (!exact(response, ["precheckOnly", "version", "documentHint"]) || response.precheckOnly !== true || response.version !== "1.0.0" || response.documentHint !== hint) { retire(); return; }
        try { port = browser.runtime.connectNative(nativeApp); port.onMessage.addListener(receive); port.onDisconnect.addListener(retire); post({protocol: PROTOCOL, kind: "HELLO", connectorVersion: "1.0.0", documentInstanceHint: hint}); } catch (_) { retire(); }
      }, retire);
    } catch (_) { retire(); }
  }
  Object.defineProperty(globalThis, NAME, {value: Object.freeze({open, displayText}), configurable: false, writable: false});
})();
