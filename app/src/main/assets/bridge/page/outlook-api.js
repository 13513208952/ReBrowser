/* Isolated-world bounded visible SUMMARY extractor, not an official authenticated API adapter.
 * Selectors may cease matching: returns UNKNOWN/empty or omits incomplete rows, never invents
 * server dates/IDs, downloads MIME/body/attachments, reads inputs/cookies, signs in or mutates mail.
 * Page IDs/account reports remain unverified and cannot enter native MailAccountCatalog/SQL. */
(() => {
  "use strict";
  const protocol = globalThis.__rbBuiltinProtocolV1;
  if (!protocol || Object.prototype.hasOwnProperty.call(globalThis, "__rbOutlookObservationV1")) return;
  function snapshot() {
    const messages = [], seen = new Set();let shellReported = false;
    const root = document.documentElement;
    if (root) {
      const walk = document.createTreeWalker(root, NodeFilter.SHOW_ELEMENT);let visited = 0;
      for (let row = walk.nextNode(); row && ++visited <= 4096 && messages.length < 100; row = walk.nextNode()) {
        if (!row.matches('[role="option"][data-itemid]')) continue;
        shellReported = true;
        const id = row.getAttribute("data-itemid"), subject = row.querySelector('[data-testid="MessageSubject"]'), sender = row.querySelector('[data-testid="MessageSender"]'), time = row.querySelector("time[datetime]");
        if (typeof id !== "string" || !id.length || id.length > 256 || new TextEncoder().encode(id).length > 256 || seen.has(id) || !subject || !sender || !time) continue;
        const date = time.getAttribute("datetime"); if (typeof date !== "string" || date.length > 64) continue;
        const receivedUtcMs = Date.parse(date);if (!Number.isSafeInteger(receivedUtcMs) || receivedUtcMs < 0) continue;
        seen.add(id);messages.push({id, subject: protocol.displayText(subject, 512), sender: protocol.displayText(sender, 256), receivedUtcMs, hasAttachment: row.querySelector('[data-testid="attachmentIcon"]') !== null});
      }
    }
    return {account: shellReported ? "REPORTED_SIGNED_IN" : "UNKNOWN", accountHint: "", messages};
  }
  Object.defineProperty(globalThis, "__rbOutlookObservationV1", {value: Object.freeze({snapshot}), configurable: false, writable: false});
})();
