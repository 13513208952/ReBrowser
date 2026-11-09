"use strict";

async function queryCookies(url, storeId) {
  try {
    const details = {url};
    if (typeof storeId === "string" && storeId) details.storeId = storeId;
    const cookies = await browser.cookies.getAll(details);
    return {
      ok: true,
      value: cookies.map(cookie => `${cookie.name}=${cookie.value}`).join("; ")
    };
  } catch (error) {
    return {ok: false, error: String(error).slice(0, 160)};
  }
}

browser.runtime.onMessage.addListener(async (message, sender) => {
  if (!message || message.operation !== "cookies" || typeof message.url !== "string") {
    return {ok: false, error: "unsupported-background-operation"};
  }
  const storeId = sender && sender.tab ? sender.tab.cookieStoreId : "";
  return queryCookies(message.url, storeId);
});

// A background-owned port remains available while an attachment navigation has
// no content document. Android supplies only an owned Gecko context identifier.
function connectServicePort() {
  const port = browser.runtime.connectNative("browser");
  port.onMessage.addListener(async message => {
    if (!message || message.type !== "command" || message.operation !== "cookies"
        || typeof message.url !== "string" || typeof message.storeId !== "string") {
      port.postMessage({
        type: "command-result",
        id: message && message.id ? String(message.id) : "",
        ok: false,
        error: "unsupported-service-operation"
      });
      return;
    }
    const result = await queryCookies(message.url, message.storeId);
    port.postMessage({type: "command-result", id: String(message.id), ...result});
  });
  port.onDisconnect.addListener(() => setTimeout(connectServicePort, 0));
}
connectServicePort();
