/* Top-level fixed Outlook SUMMARY-only entry. No login/body/MIME/attachment/write operations.
 * No automatic observations, window relay, reconnect or account admission from page claims. */
(() => {
  "use strict";
  if (window !== window.top || location.protocol !== "https:" || !["outlook.live.com", "outlook.office.com", "outlook.office365.com"].includes(location.hostname) || location.port && location.port !== "443") return;
  const transport = globalThis.__rbBuiltinProtocolV1, readOnly = globalThis.__rbOutlookObservationV1;
  if (!transport || !readOnly) return;
  transport.open("rebrowser_outlook", "outlook.observe", () => readOnly.snapshot());
})();
