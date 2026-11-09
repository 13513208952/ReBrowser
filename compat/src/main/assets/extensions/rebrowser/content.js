"use strict";

(() => {
  if (window.top !== window || globalThis.__reBrowserGeckoBridgeContent) return;
  globalThis.__reBrowserGeckoBridgeContent = true;

  const port = browser.runtime.connectNative("browser");
  const blobs = new Map();
  const REQUEST_EVENT = "__rebrowser_engine_request_v1";
  const RESPONSE_EVENT = "__rebrowser_engine_response_v1";
  const OUTLOOK_REQUEST_EVENT = "__reoutlook_native_request_v1";
  const OUTLOOK_RESPONSE_EVENT = "__reoutlook_native_response_v1";

  const respond = (id, ok, value, error) => {
    try {
      port.postMessage({
        type: "command-result",
        id: String(id || ""),
        ok: Boolean(ok),
        value: value == null ? null : value,
        error: String(error || "").slice(0, 200)
      });
    } catch (_) {}
  };

  port.onMessage.addListener(async message => {
    if (!message || typeof message !== "object") return;
    if (message.type === "permission-result" || message.type === "outlook-result") {
      try {
        document.dispatchEvent(new CustomEvent(
          message.type === "permission-result" ? RESPONSE_EVENT : OUTLOOK_RESPONSE_EVENT,
          {detail: String(message.payload || "")}
        ));
      } catch (_) {}
      return;
    }
    if (message.type !== "command") return;
    const id = String(message.id || "");
    try {
      if (message.operation === "cookies") {
        const result = await browser.runtime.sendMessage({
          operation: "cookies", url: String(message.url || "")
        });
        respond(id, Boolean(result && result.ok), result && result.value,
          result && result.error);
        return;
      }
      if (String(message.operation || "").startsWith("music-")) {
        if (typeof globalThis.__reMusicFixedCommand !== "function") {
          respond(id, false, null, "music-policy-unavailable");
        } else {
          const value = await globalThis.__reMusicFixedCommand(message.operation, message);
          respond(id, value !== null && value !== undefined, value,
            value === null || value === undefined ? "music-command-unavailable" : "");
        }
        return;
      }
      if (message.operation === "outlook-current") {
        if (typeof globalThis.__reOutlookCurrentMessage !== "function") {
          respond(id, false, null, "outlook-policy-unavailable");
        } else {
          respond(id, true, globalThis.__reOutlookCurrentMessage(), "");
        }
        return;
      }
      if (message.operation === "outlook-scroll") {
        if (typeof globalThis.__reOutlookScrollMailList !== "function") {
          respond(id, false, null, "outlook-policy-unavailable");
        } else {
          respond(id, true, globalThis.__reOutlookScrollMailList(), "");
        }
        return;
      }
      if (message.operation === "blob-begin") {
        const key = String(message.key || "");
        const state = {status: "loading", error: "", blob: null, size: 0, type: ""};
        blobs.set(key, state);
        fetch(String(message.url || ""), {credentials: "include"})
          .then(response => {
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            return response.blob();
          })
          .then(blob => {
            if (blobs.get(key) !== state) return;
            state.blob = blob;
            state.size = blob.size;
            state.type = blob.type || "";
            state.status = "ready";
          })
          .catch(error => {
            if (blobs.get(key) !== state) return;
            state.error = String(error).slice(0, 160);
            state.status = "error";
          });
        respond(id, true, true, "");
        return;
      }
      if (message.operation === "blob-metadata") {
        const state = blobs.get(String(message.key || ""));
        respond(id, Boolean(state), state ? {
          status: state.status, error: state.error,
          size: state.size || 0, type: state.type || ""
        } : null, state ? "" : "missing-blob-state");
        return;
      }
      if (message.operation === "blob-chunk") {
        const state = blobs.get(String(message.key || ""));
        if (!state || !state.blob || state.status !== "ready") {
          respond(id, false, null, "blob-not-ready");
          return;
        }
        const offset = Math.max(0, Number(message.offset) || 0);
        const end = Math.max(offset, Number(message.end) || offset);
        const bytes = new Uint8Array(await state.blob.slice(offset, end).arrayBuffer());
        let binary = "";
        for (let index = 0; index < bytes.length; index += 32768) {
          binary += String.fromCharCode.apply(null, bytes.subarray(index, index + 32768));
        }
        respond(id, true, {status: "ready", error: "", data: btoa(binary)}, "");
        return;
      }
      if (message.operation === "blob-end") {
        blobs.delete(String(message.key || ""));
        respond(id, true, true, "");
        return;
      }
      respond(id, false, null, "unsupported-command");
    } catch (error) {
      respond(id, false, null, String(error).slice(0, 200));
    }
  });

  document.addEventListener(REQUEST_EVENT, event => {
    const payload = event && typeof event.detail === "string" ? event.detail : "";
    if (!payload || payload.length > 220000) return;
    try { port.postMessage({type: "permission", payload}); } catch (_) {}
  });
  document.addEventListener(OUTLOOK_REQUEST_EVENT, event => {
    const payload = event && typeof event.detail === "string" ? event.detail : "";
    if (!payload || payload.length > 1800000) return;
    try { port.postMessage({type: "outlook", payload}); } catch (_) {}
  });

  const pagePolicy = `(() => {
    if (globalThis.__reBrowserPermissionPolicyInstalled) return;
    globalThis.__reBrowserPermissionPolicyInstalled = true;
    const requestEvent = ${JSON.stringify(REQUEST_EVENT)};
    const responseEvent = ${JSON.stringify(RESPONSE_EVENT)};
    const denied = message => new DOMException(message || 'Permission denied', 'NotAllowedError');
    const pending = new Map();
    let sequence = 0;
    const bridge = {
      onmessage: null,
      postMessage(payload) {
        document.dispatchEvent(new CustomEvent(requestEvent, {detail: String(payload || '')}));
      }
    };
    document.addEventListener(responseEvent, event => {
      if (bridge.onmessage) bridge.onmessage({data: String(event.detail || '')});
    });
    const send = (operation, value = {}) => new Promise((resolve, reject) => {
      const id = String(++sequence);
      pending.set(id, {resolve, reject});
      bridge.postMessage(JSON.stringify({id, operation, ...value}));
    });
    bridge.onmessage = event => {
      try {
        const value = JSON.parse(String(event.data || '{}'));
        const callback = pending.get(String(value.id || ''));
        if (!callback) return;
        pending.delete(String(value.id));
        if (value.ok) callback.resolve(value.value);
        else callback.reject(denied(String(value.message || 'Permission denied')));
      } catch (_) {}
    };
    const requireGesture = () => {
      if (!navigator.userActivation || navigator.userActivation.isActive !== true) {
        return Promise.reject(denied('Clipboard requires a user gesture'));
      }
      return null;
    };
    const clipboard = Object.freeze({
      readText: () => requireGesture() || send('clipboard-read').then(value => String(value || '')),
      writeText: value => requireGesture() || send('clipboard-write', {text: String(value ?? '')}).then(() => undefined),
      read: () => Promise.reject(new DOMException('Rich clipboard is unsupported', 'NotSupportedError')),
      write: () => Promise.reject(new DOMException('Rich clipboard is unsupported', 'NotSupportedError'))
    });
    try {
      Object.defineProperty(Object.getPrototypeOf(navigator), 'clipboard', {
        get: () => clipboard, configurable: false, enumerable: true
      });
      Object.defineProperty(navigator, 'clipboard', {
        value: clipboard, configurable: false, enumerable: true, writable: false
      });
    } catch (_) {}
    for (const name of ['Accelerometer', 'Gyroscope', 'Magnetometer',
      'LinearAccelerationSensor', 'GravitySensor', 'AbsoluteOrientationSensor',
      'RelativeOrientationSensor', 'AmbientLightSensor', 'DeviceMotionEvent',
      'DeviceOrientationEvent', 'Notification', 'PushManager']) {
      try { Object.defineProperty(globalThis, name, {value: undefined, configurable: false}); }
      catch (_) {}
    }
  })();`;

  try {
    // Gecko's wrapped page global is the document-start equivalent of WebView's
    // DOCUMENT_START_SCRIPT. The source is fixed inside this built-in extension.
    window.wrappedJSObject.eval(pagePolicy);
  } catch (_) {
    // Fail closed: no privileged object is exported if the page realm rejects injection.
  }
})();
