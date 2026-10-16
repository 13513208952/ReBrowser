package io.github.reoutlook;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/** One bounded, non-persistent lyric document; times already include the official offset. */
final class ReBrowserMusicLyrics {
    private static final Set<String> STATUSES = Set.of("loading", "ready", "none", "untimed", "too-large", "timeout", "unavailable");
    final String track, status;
    private final List<Line> lines;
    private record Line(long time, String text) {}
    private ReBrowserMusicLyrics(String track, String status, List<Line> lines) {
        this.track = track; this.status = status; this.lines = List.copyOf(lines);
    }
    static ReBrowserMusicLyrics state(String track, String status) {
        return new ReBrowserMusicLyrics(track, STATUSES.contains(status) ? status : "unavailable", List.of());
    }
    static ReBrowserMusicLyrics parse(String value, String expected) throws Exception {
        if (value == null || value.length() > 1_048_576 || expected == null || expected.length() > 128)
            throw new IllegalArgumentException("lyric-envelope");
        Object decoded = new org.json.JSONTokener(value).nextValue();
        if (!(decoded instanceof String) || ((String) decoded).length() > 262144) throw new IllegalArgumentException("lyric-envelope");
        JSONObject o = new JSONObject((String) decoded);
        if (!expected.equals(o.opt("track"))) throw new IllegalArgumentException("lyric-track");
        Object status = o.opt("status");
        if (!(status instanceof String) || !STATUSES.contains(status)) throw new IllegalArgumentException("lyric-status");
        if (!"ready".equals(status)) return state(expected, (String) status);
        JSONArray source = o.optJSONArray("lines");
        if (source == null || source.length() == 0 || source.length() > 512) throw new IllegalArgumentException("lyric-lines");
        ArrayList<Line> lines = new ArrayList<>(source.length()); long last = -1;
        for (int i = 0; i < source.length(); i++) {
            JSONObject line = source.getJSONObject(i); Object time = line.opt("time"), text = line.opt("text");
            if (!(time instanceof Number) || !(text instanceof String) || ((String) text).length() > 256)
                throw new IllegalArgumentException("lyric-line");
            double t = ((Number) time).doubleValue();
            if (!Double.isFinite(t) || t < 0 || t > 86400000 || t != Math.rint(t) || t < last)
                throw new IllegalArgumentException("lyric-time");
            last = (long) t;
            lines.add(new Line(last, ((String) text).replaceAll("[\\p{Cntrl}]", " ")));
        }
        return new ReBrowserMusicLyrics(expected, "ready", lines);
    }
    boolean pending() { return "loading".equals(status); }
    String textAt(long position) {
        int low = 0, high = lines.size();
        while (low < high) { int mid = (low + high) >>> 1; if (lines.get(mid).time <= position) low = mid + 1; else high = mid; }
        return low == 0 ? "" : lines.get(low - 1).text;
    }
    String message(long position) {
        return switch (status) {
            case "ready" -> textAt(position).isEmpty() ? "♪" : textAt(position);
            case "loading" -> "正在读取官方歌词…";
            case "none" -> "暂无歌词";
            case "untimed" -> "暂无同步歌词";
            case "too-large" -> "歌词超出显示范围";
            case "timeout" -> "歌词读取超时";
            default -> "当前歌词暂不可用";
        };
    }
}
