package io.github.reoutlook;

/** Only an explicit fixed native playback command can relax the engine gesture gate. */
final class ReBrowserMusicPlaybackAuthorization {
    private ReBrowserMusicPlaybackAuthorization() {}
    static boolean requiresGesture(String command, boolean current) {
        if ("pause".equals(command)) return true;
        if ("resume".equals(command) || "previous".equals(command) || "next".equals(command)) return false;
        return current; // Seeking, rating, navigation and window changes do not authorize playback.
    }
}
