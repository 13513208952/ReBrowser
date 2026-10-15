package io.github.reoutlook;

import java.net.URI;
import org.json.JSONObject;

/** Bounded external metadata, never a source of privileges, URLs or administrator authority. */
final class ReBrowserMusicSnapshot {
    final boolean ready, playing, buffering, canSeek, canSkip, canLike, liked;
    final String track, title, artist, album, cover;
    final long position, duration;
    final float speed;
    private ReBrowserMusicSnapshot(JSONObject o) throws Exception {
        ready = o.optBoolean("ready", false);
        playing = ready && o.optBoolean("playing", false);
        buffering = ready && !playing && o.optBoolean("buffering", false);
        track = text(o, "track", 128); title = text(o, "title", 240);
        artist = text(o, "artist", 240); album = text(o, "album", 240);
        position = millis(o, "position"); duration = millis(o, "duration");
        Object rate = o.opt("speed"); double r = rate instanceof Number ? ((Number)rate).doubleValue() : 1;
        speed = Double.isFinite(r) && r >= 0.25 && r <= 4 ? (float)r : 1;
        cover = artwork(text(o, "cover", 2048));
        canSeek = ready && duration > 0 && o.optBoolean("canSeek", false);
        canSkip = ready && o.optBoolean("canSkip", false);
        canLike = ready && o.optBoolean("canLike", false); liked = o.optBoolean("liked", false);
    }
    static ReBrowserMusicSnapshot empty() {
        try { return new ReBrowserMusicSnapshot(new JSONObject()); } catch (Exception e) { throw new AssertionError(e); }
    }
    static ReBrowserMusicSnapshot parse(String value) throws Exception {
        if (value == null || value.length() > 8192) throw new IllegalArgumentException("invalid-music-state");
        Object decoded = new org.json.JSONTokener(value).nextValue();
        if (!(decoded instanceof String)) throw new IllegalArgumentException("invalid-music-state");
        return new ReBrowserMusicSnapshot(new JSONObject((String) decoded));
    }
    private static String text(JSONObject o, String key, int limit) {
        Object value = o.opt(key);
        if (!(value instanceof String)) return "";
        String s = ((String) value).replaceAll("[\\p{Cntrl}]", " ");
        return s.substring(0, Math.min(limit, s.length()));
    }
    private static long millis(JSONObject o, String key) {
        Object value = o.opt(key);
        if (!(value instanceof Number)) return 0;
        double d = ((Number) value).doubleValue();
        return Double.isFinite(d) && d >= 0 && d <= 86_400_000 ? (long) d : 0;
    }
    static String artwork(String value) {
        try {
            URI u = new URI(value);
            String host = u.getHost();
            if (u.getRawUserInfo() != null || host == null || !host.matches("p[0-9]+\\.music\\.126\\.net")) return "";
            if ("https".equals(u.getScheme()) && (u.getPort() == -1 || u.getPort() == 443)) return value;
            // Some official album DTOs still contain HTTP artwork. Upgrade only this fixed CDN;
            // never open a cleartext connection or follow a redirect to obtain the image.
            if ("http".equals(u.getScheme()) && (u.getPort() == -1 || u.getPort() == 80))
                return "https://" + host + (u.getRawPath() == null ? "" : u.getRawPath())
                        + (u.getRawQuery() == null ? "" : "?" + u.getRawQuery());
            return "";
        } catch (Exception invalid) { return ""; }
    }
}
