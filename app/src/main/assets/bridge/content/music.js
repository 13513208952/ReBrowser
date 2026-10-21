/* Top-level fixed music observation entry. No page messages/commands or automatic observation.
 * Background precheck and native READY are routing only; Native COMMAND drives bounded read. */
(() => {
  "use strict";
  if (window !== window.top || location.protocol !== "https:" || location.hostname !== "music.163.com" || location.port && location.port !== "443") return;
  const transport = globalThis.__rbBuiltinProtocolV1, readOnly = globalThis.__rbMusicObservationV1;
  if (!transport || !readOnly) return;
  transport.open("rebrowser_music", "music.observe", () => readOnly.snapshot());
})();
