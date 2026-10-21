/* Filename is historical tree vocabulary: loaded in the extension ISOLATED world, NOT MAIN.
 * Fixed read-only DOM/media-property observations only. No webpack/private player APIs, writes,
 * login, credentials, network, arbitrary selectors, play/pause/seek or native playback claims. */
(() => {
  "use strict";
  const protocol = globalThis.__rbBuiltinProtocolV1;
  if (!protocol || Object.prototype.hasOwnProperty.call(globalThis, "__rbMusicObservationV1")) return;
  function millis(value) {const ms = Math.round(value * 1000); return Number.isFinite(value) && value >= 0 && Number.isSafeInteger(ms) && ms <= 86400000 ? ms : null;}
  function snapshot() {
    const media = document.querySelector("audio"), title = document.querySelector(".m-playbar .name"), artist = document.querySelector(".m-playbar .by");
    let playback = "UNKNOWN", positionMs = null, durationMs = null, rate = null;
    if (media) {
      positionMs = millis(media.currentTime); durationMs = millis(media.duration);
      if (positionMs !== null && durationMs !== null && positionMs > durationMs) positionMs = null;
      if (Number.isFinite(media.playbackRate) && media.playbackRate >= 0 && media.playbackRate <= 16) rate = media.playbackRate;
      if (media.ended === true) playback = "ENDED";
      else if (media.paused === true) playback = "PAUSED";
      else if (media.paused === false && Number.isInteger(media.readyState)) playback = media.readyState >= 3 ? "PLAYING" : "BUFFERING";
    }
    // An avatar is merely a spoofable page report; native authentication never consumes this.
    const account = document.querySelector(".m-tophead .head") ? "REPORTED_SIGNED_IN" : "UNKNOWN";
    return {account, playback, title: protocol.displayText(title, 512), artist: protocol.displayText(artist, 512), positionMs, durationMs, rate};
  }
  Object.defineProperty(globalThis, "__rbMusicObservationV1", {value: Object.freeze({snapshot}), writable: false, configurable: false});
})();
