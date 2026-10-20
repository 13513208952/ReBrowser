package io.github.rebrowser.subapps;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Fixed built-in subapplication identity/display directory ONLY.
 * Browser is not a subapplication. This directory contains NO enablement state, NO
 * persisted preference, NO account/login fact and NO component opener: every Route value
 * is a descriptive local hint and must not start an Activity, service or network work.
 */
public final class SubapplicationRegistry {
    /** Display-only local route hint; resolving it must NEVER start a component. */
    public enum Route { OUTLOOK_NATIVE_ENTRY, MUSIC_NATIVE_ENTRY }
    public static final int MAX_TEXT_CHARS = 160;
    public static final int MAX_LOCAL_ID_CHARS = 32;

    /** Immutable directory entry. Existence here is NOT "enabled", "configured" or "signed in". */
    public static final class Descriptor {
        private final String localId, displayName, description;
        private final Route route;
        private Descriptor(String localId, String displayName, String description, Route route) {
            this.localId = localId; this.displayName = displayName; this.description = description; this.route = route;
        }
        public String localId() { return localId; }
        public String displayName() { return displayName; }
        public String description() { return description; }
        public Route route() { return route; }
    }

    private static final List<Descriptor> ALL = build();
    private SubapplicationRegistry() {}

    /** Unmodifiable ordered directory: Outlook first, music second. */
    public static List<Descriptor> all() { return ALL; }

    /** Exact, case-sensitive lookup. Null/blank/long/unknown identifiers never select a default. */
    public static Descriptor require(String localId) {
        if (localId == null || localId.isEmpty() || localId.length() > MAX_LOCAL_ID_CHARS) throw invalid();
        text(localId, MAX_LOCAL_ID_CHARS);
        for (Descriptor descriptor : ALL) if (descriptor.localId.equals(localId)) return descriptor;
        throw new IllegalArgumentException("Unknown native subapplication id");
    }

    private static List<Descriptor> build() {
        List<Descriptor> entries = new ArrayList<>();
        entries.add(new Descriptor("outlook", "Outlook", "官方邮箱与离线邮件入口", Route.OUTLOOK_NATIVE_ENTRY));
        entries.add(new Descriptor("music", "音乐", "官方网站音乐播放器入口", Route.MUSIC_NATIVE_ENTRY));
        if (entries.size() != 2) throw new IllegalStateException("Built-in subapplication directory corrupted");
        Set<String> ids = new HashSet<>();
        for (Descriptor descriptor : entries) {
            text(descriptor.localId, MAX_LOCAL_ID_CHARS);
            text(descriptor.displayName, MAX_LOCAL_ID_CHARS);
            text(descriptor.description, MAX_TEXT_CHARS);
            if (!ids.add(descriptor.localId)) throw new IllegalStateException("Duplicate native subapplication id");
        }
        return Collections.unmodifiableList(entries);
    }

    /** Bounded single-line UTF-16 text; no control characters, no unpaired surrogates. */
    private static void text(String value, int maximum) {
        if (value == null || value.isEmpty() || value.length() > maximum) throw invalid();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isISOControl(c)) throw invalid();
            if (Character.isHighSurrogate(c)) {
                if (++i >= value.length() || !Character.isLowSurrogate(value.charAt(i))) throw invalid();
            } else if (Character.isLowSurrogate(c)) throw invalid();
        }
    }

    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid native subapplication directory input"); }
}
