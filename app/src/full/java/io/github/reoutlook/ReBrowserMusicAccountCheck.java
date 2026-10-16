package io.github.reoutlook;

/** Per-check nonce and one safe fallback. No credentials or account data leave the WebView. */
final class ReBrowserMusicAccountCheck {
    static final long TIMEOUT_MS = 3000;
    private String request;
    private boolean fallback, loaded, failed;
    String begin(boolean checking) {
        loaded = failed = false;
        request = checking && !fallback ? ReBrowserMusicPolicy.ACCOUNT
                + "?_rb=" + java.util.UUID.randomUUID().toString().replace("-", "") : null;
        return request;
    }
    boolean active(String url) { return !fallback && request != null && request.equals(url); }
    boolean readable(String url) { return loaded && !failed && active(url); }
    void started(String url) { if (active(url)) loaded = false; }
    void fail(String url) { if (active(url)) { failed = true; loaded = false; } }
    boolean finished(String url) { if (failed || !active(url)) return false; loaded = true; return true; }
    String request() { return request; }
    boolean fallback(String expected) {
        if (!active(expected)) return false;
        fallback = true; return true;
    }
    void restart() { request = null; fallback = loaded = failed = false; }
}
