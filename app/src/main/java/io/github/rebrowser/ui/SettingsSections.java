package io.github.rebrowser.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Static READ-ONLY native settings directory. It owns NO repository, NO persisted setting
 * key, NO default value and NO policy: a missing real configuration stays "未读取" and
 * cannot be acted on. Rows never carry a Runnable, Activity or domain permission.
 */
public final class SettingsSections {
    public enum SectionId { GENERAL, APPEARANCE, SEARCH, SUBAPPLICATIONS, SITE_PERMISSIONS, SOURCE_SAFETY, ABOUT }
    /** Descriptive navigation hint for the owning native window; never an admin action or setting key. */
    public enum Route {
        PREFERENCES_GENERAL, PREFERENCES_STARTUP, APPEARANCE, SEARCH,
        SUBAPPLICATIONS, SITE_PERMISSIONS, SOURCE_SAFETY, ABOUT
    }
    public static final int MAX_TITLE_CHARS = 32;
    public static final int MAX_SECTIONS = 7;
    public static final int MAX_ROWS = 16;

    public static final class Row {
        private final String id, title;
        private final Route route;
        private Row(String id, String title, Route route) { this.id = id; this.title = title; this.route = route; }
        public String id() { return id; }
        public String title() { return title; }
        public Route route() { return route; }
    }

    public static final class Section {
        private final SectionId id;
        private final String title;
        private final List<Row> rows;
        private Section(SectionId id, String title, List<Row> rows) {
            this.id = id; this.title = title; this.rows = Collections.unmodifiableList(new ArrayList<>(rows));
        }
        public SectionId id() { return id; }
        public String title() { return title; }
        public List<Row> rows() { return rows; }
    }

    private static final List<Section> ALL = build();
    private SettingsSections() {}

    public static List<Section> sections() { return ALL; }

    public static Section require(SectionId id) {
        if (id == null) throw invalid();
        for (Section section : ALL) if (section.id == id) return section;
        throw new IllegalArgumentException("Unknown native settings section");
    }

    /** Display of a caller-supplied READ-ONLY observation; never a write, default or authorization. */
    public static String observedValueText(Boolean observed) {
        return observed == null ? "未读取" : observed ? "已开启" : "已关闭";
    }

    /** Unknown configuration is not actionable; callers must read the real value first. */
    public static boolean actionable(Boolean observed) { return observed != null; }

    private static List<Section> build() {
        List<Section> sections = new ArrayList<>();
        sections.add(section(SectionId.GENERAL, "通用",
                row("ui.row.general.preferences", "通用设置", Route.PREFERENCES_GENERAL),
                row("ui.row.general.startup", "启动与主页", Route.PREFERENCES_STARTUP)));
        sections.add(section(SectionId.APPEARANCE, "外观",
                row("ui.row.appearance.background", "背景与外观", Route.APPEARANCE)));
        sections.add(section(SectionId.SEARCH, "搜索",
                row("ui.row.search.engine", "搜索引擎", Route.SEARCH)));
        sections.add(section(SectionId.SUBAPPLICATIONS, "子应用",
                row("ui.row.subapplications.enabled", "子应用启用", Route.SUBAPPLICATIONS)));
        sections.add(section(SectionId.SITE_PERMISSIONS, "网站权限",
                row("ui.row.permissions.sites", "网站权限", Route.SITE_PERMISSIONS)));
        sections.add(section(SectionId.SOURCE_SAFETY, "来源安全",
                row("ui.row.source.safety", "来源与会话安全", Route.SOURCE_SAFETY)));
        sections.add(section(SectionId.ABOUT, "关于",
                row("ui.row.about.version", "关于 ReBrowser", Route.ABOUT)));
        if (sections.size() != MAX_SECTIONS) throw new IllegalStateException("Native settings directory corrupted");
        validate(sections);
        return Collections.unmodifiableList(sections);
    }

    private static Section section(SectionId id, String title, Row... rows) {
        text(title, MAX_TITLE_CHARS);
        return new Section(id, title, java.util.Arrays.asList(rows));
    }

    private static Row row(String id, String title, Route route) {
        if (id == null || !id.startsWith("ui.") || route == null) throw new IllegalStateException("Invalid native settings row identifier");
        text(id, 64);
        text(title, MAX_TITLE_CHARS);
        return new Row(id, title, route);
    }

    private static void validate(List<Section> sections) {
        Set<SectionId> sectionIds = new HashSet<>();
        Set<String> rowIds = new HashSet<>();
        int rows = 0;
        for (Section section : sections) {
            if (!sectionIds.add(section.id)) throw new IllegalStateException("Duplicate native settings section");
            if (section.rows.isEmpty()) throw new IllegalStateException("Empty native settings section");
            for (Row row : section.rows) {
                if (!rowIds.add(row.id)) throw new IllegalStateException("Duplicate native settings row");
                rows++;
            }
        }
        if (rows > MAX_ROWS) throw new IllegalStateException("Native settings row limit reached");
    }

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

    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid native settings directory input"); }
}
