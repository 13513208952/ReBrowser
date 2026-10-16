package io.github.reoutlook;

/** One cold pause per document; submission is acknowledged before a fresh paused-state read. */
final class ReBrowserMusicPreparation {
    private boolean requested, acknowledged;
    boolean request() {
        if (requested) return false;
        requested = true; return true;
    }
    boolean acknowledge(boolean submitted) {
        if (!requested || acknowledged || !submitted) return false;
        acknowledged = true; return true;
    }
    boolean acknowledged() { return acknowledged; }
    boolean paused(boolean playing, boolean buffering) { return acknowledged && !playing && !buffering; }
    void reset() { requested = acknowledged = false; }
}
