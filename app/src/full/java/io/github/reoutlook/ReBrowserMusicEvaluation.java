package io.github.reoutlook;

/** A timeout reports failure, but never pretends an outstanding WebView callback was cancelled. */
final class ReBrowserMusicEvaluation {
    private int sequence;
    private boolean pending, expired;
    int begin() {
        if (pending) return 0;
        pending = true; expired = false;
        if (++sequence == 0) sequence++;
        return sequence;
    }
    boolean finish(int token) {
        if (!pending || token != sequence) return false;
        pending = false; expired = false; return true;
    }
    boolean timeout(int token) {
        if (!pending || token != sequence || expired) return false;
        expired = true; return true;
    }
    boolean expired() { return expired; }
    void reset() { pending = false; expired = false; sequence++; }
}
