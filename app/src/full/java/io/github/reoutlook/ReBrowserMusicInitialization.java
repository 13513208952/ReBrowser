package io.github.reoutlook;

/** Initialization has a finite budget; expiry never cancels or frees a real JS callback slot. */
final class ReBrowserMusicInitialization {
    static final long TIMEOUT_MS = 30_000;
    private long generation;
    private boolean expired;
    long start() { expired = false; return ++generation; }
    boolean expire(long token, boolean ready, boolean login) {
        if (token != generation || ready || login || expired) return false;
        expired = true; return true;
    }
    boolean expired() { return expired; }
}
