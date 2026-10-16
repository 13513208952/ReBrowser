package io.github.reoutlook;

import java.util.Set;

/** DUMP-only bounded counters/status. Never store requested position, song identity or page strings. */
final class ReBrowserMusicSeekDiagnostics {
    private static final Set<String> RESULTS = Set.of("runtime-not-ready", "seek-not-supported", "wrong-document",
            "page-unresponsive", "queued", "submitted", "track-changed", "not-ready", "outside-playable-range",
            "invalid-position", "unsupported", "unknown");
    private static final Set<String> PLAY_RESULTS = Set.of("none", "requested", "resolved", "guarded", "NotAllowedError", "AbortError", "NotSupportedError", "other");
    private int received, submitted, queued, rejected, acknowledged, failed;
    private String last = "none", playResult = "none";
    private boolean engineKnown, enginePaused, engineSeeking, engineAdvancing;
    private int engineReady = -1, engineNetwork = -1, engineError = -1;
    void received() { received = increment(received); }
    void result(String status) {
        last = status != null && RESULTS.contains(status) ? status : "unknown";
        switch (last) {
            case "submitted" -> submitted = increment(submitted);
            case "queued" -> queued = increment(queued);
            default -> rejected = increment(rejected);
        }
    }
    static String javascriptResult(String raw) {
        if (raw != null && raw.length() <= 80)
            for (String known : RESULTS) if (("\"" + known + "\"").equals(raw)) return known;
        return "unknown";
    }
    void observed(int success, int failure) {
        acknowledged = bounded(success); failed = bounded(failure);
    }
    void engine(boolean known, boolean paused, boolean seeking, int ready, int network, int error, boolean advancing, String play) {
        engineKnown = known; enginePaused = paused; engineSeeking = seeking; engineAdvancing = advancing;
        engineReady = ready >= 0 && ready <= 4 ? ready : -1;
        engineNetwork = network >= 0 && network <= 3 ? network : -1;
        engineError = error >= 0 && error <= 4 ? error : -1;
        playResult = play != null && PLAY_RESULTS.contains(play) ? play : "other";
    }
    private static int increment(int value) { return Math.min(1_000_000, value + 1); }
    private static int bounded(int value) { return Math.max(0, Math.min(1_000_000, value)); }
    void dump(java.io.PrintWriter writer) {
        writer.println("seek received=" + received + " submitted=" + submitted + " queued=" + queued
                + " rejected=" + rejected + " last=" + last + " sdkCompleted=" + acknowledged + " sdkFailed=" + failed);
        writer.println("engine known=" + engineKnown + " paused=" + enginePaused + " seeking=" + engineSeeking
                + " ready=" + engineReady + " network=" + engineNetwork + " error=" + engineError
                + " advancing=" + engineAdvancing + " play=" + playResult);
    }
}
