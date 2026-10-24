package io.github.rebrowser.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Static READ-ONLY native settings directory. It owns NO repository, NO persisted setting key,
 * NO default value and NO policy: a missing real configuration stays "未读取" and cannot be acted
 * on. Rows carry a display/EDIT KIND and a bounded fixed option list, never a Runnable, Activity,
 * arbitrary URL or domain permission. These option values are UI vocabulary, not storage keys.
 */
public final class SettingsSections {
    public enum SectionId { GENERAL, ISOLATION_PERMISSIONS, APPLICATIONS }
    /** Descriptive navigation hint for the owning native window; never an admin action or setting key. */
    public enum Route {
        PREFERENCES_GENERAL, PREFERENCES_STARTUP, APPEARANCE, SEARCH,
        SUBAPPLICATIONS, SITE_PERMISSIONS, SOURCE_SAFETY, DEFAULT_BROWSER, ABOUT
    }
    /** Editing/display kind offered to the window; storage semantics remain with the owner. */
    public enum ValueKind { READ_ONLY, BOOLEAN, FIXED_OPTION, BOUNDED_TEXT }
    public static final int MAX_TITLE_CHARS = 32;
    public static final int MAX_SECTIONS = 3;
    public static final int MAX_ROWS = 16;
    public static final int MAX_OPTIONS = 8;
    public static final int MAX_OPTION_VALUE_CHARS = 32;
    public static final int MAX_OPTION_LABEL_CHARS = 32;
    public static final int MAX_TEXT_CHARS = 8192;
    public static final String UNKNOWN_TEXT = "未读取";

    /** Bounded fixed option; value is an opaque UI token, label is display text. */
    public static final class Option {
        private final String value,label;
        public Option(String value,String label){
            this.value=token(value);this.label=text(label,MAX_OPTION_LABEL_CHARS);
        }
        public String value(){return value;}
        public String label(){return label;}
    }

    public static final class Row {
        private final String id, title;
        private final Route route;
        private final ValueKind kind;
        private final List<Option> options;
        private final int maxTextChars;
        private Row(String id, String title, Route route, ValueKind kind, List<Option> options, int maxTextChars) {
            this.id = id; this.title = title; this.route = route; this.kind = kind;
            this.options = Collections.unmodifiableList(new ArrayList<>(options));
            this.maxTextChars = maxTextChars;
        }
        public String id() { return id; }
        public String title() { return title; }
        public Route route() { return route; }
        public ValueKind kind(){return kind;}
        public List<Option> options(){return options;}
        public int maxTextChars(){return maxTextChars;}
        /** Unknown fixed-option value must be reported, never silently replaced by the first option. */
        public Option findOption(String value){
            if(value==null)return null;
            for(Option option:options)if(option.value.equals(value))return option;
            return null;
        }
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

    public static Row requireRow(String rowId){
        if(rowId==null)throw invalid();
        for(Section section:ALL)for(Row row:section.rows())if(row.id.equals(rowId))return row;
        throw new IllegalArgumentException("Unknown native settings row");
    }

    /** The four fixed search providers offered by the approved new-tab/search UI. */
    public static List<Option> searchProviderOptions(){
        return Collections.unmodifiableList(java.util.Arrays.asList(
                new Option("bing","必应"), new Option("baidu","百度"),
                new Option("duckduckgo","DuckDuckGo"), new Option("google","Google")));
    }

    /** Display of a caller-supplied READ-ONLY observation; never a write, default or authorization. */
    public static String observedValueText(Boolean observed) {
        return observed == null ? UNKNOWN_TEXT : observed ? "已开启" : "已关闭";
    }
    public static String observedOptionText(String observedValue,List<Option> options){
        if(observedValue==null)return UNKNOWN_TEXT;
        for(Option option:options)if(option.value.equals(observedValue))return option.label;
        return UNKNOWN_TEXT + "（不支持的选项）";
    }
    public static String observedTextValue(String observed,int maxChars){
        if(observed==null)return UNKNOWN_TEXT;
        return text(observed,Math.min(maxChars,MAX_TEXT_CHARS));
    }

    /** Unknown configuration is not actionable; callers must read the real value first. */
    public static boolean actionable(Boolean observed) { return observed != null; }

    private static List<Section> build() {
        List<Section> sections = new ArrayList<>();
        sections.add(section(SectionId.GENERAL, "常规",
                row("ui.row.general.homepage", "主页网址", Route.PREFERENCES_STARTUP,ValueKind.BOUNDED_TEXT,Collections.<Option>emptyList(),8192),
                optionRow("ui.row.search.engine", "搜索引擎", Route.SEARCH, searchProviderOptions()),
                optionRow("ui.row.appearance.background", "新标签页背景", Route.APPEARANCE,java.util.Arrays.asList(new Option("exclusive","专属背景"),new Option("white","纯白"))),
                booleanRow("ui.row.general.recent", "显示最近访问", Route.PREFERENCES_GENERAL),
                optionRow("ui.row.general.device-type", "设备类型", Route.PREFERENCES_GENERAL,java.util.Arrays.asList(new Option("auto","自动识别"),new Option("phone","手机"),new Option("tablet","平板"))),
                readOnlyRow("ui.row.general.default-browser", "默认浏览器", Route.DEFAULT_BROWSER),
                readOnlyRow("ui.row.about.version", "关于 ReBrowser", Route.ABOUT)));
        sections.add(section(SectionId.ISOLATION_PERMISSIONS, "隔离与权限",
                readOnlyRow("ui.row.permissions.sites", "网站权限与隔离状态", Route.SITE_PERMISSIONS),
                readOnlyRow("ui.row.source.safety", "来源与会话安全", Route.SOURCE_SAFETY)));
        sections.add(section(SectionId.APPLICATIONS, "应用管理",
                readOnlyRow("ui.row.subapplications.enabled", "子应用启用与运行状态", Route.SUBAPPLICATIONS)));
        if (sections.size() != MAX_SECTIONS) throw new IllegalStateException("Native settings directory corrupted");
        validate(sections);
        return Collections.unmodifiableList(sections);
    }

    private static Section section(SectionId id, String title, Row... rows) {
        text(title, MAX_TITLE_CHARS);
        return new Section(id, title, java.util.Arrays.asList(rows));
    }
    private static Row readOnlyRow(String id,String title,Route route){return row(id,title,route,ValueKind.READ_ONLY,Collections.<Option>emptyList(),0);}
    private static Row booleanRow(String id,String title,Route route){return row(id,title,route,ValueKind.BOOLEAN,Collections.<Option>emptyList(),0);}
    private static Row optionRow(String id,String title,Route route,List<Option> options){return row(id,title,route,ValueKind.FIXED_OPTION,options,0);}
    private static Row row(String id, String title, Route route,ValueKind kind,List<Option> options,int maxTextChars) {
        if (id == null || !id.startsWith("ui.") || route == null || kind == null) throw new IllegalStateException("Invalid native settings row identifier");
        text(id, 64);
        text(title, MAX_TITLE_CHARS);
        if(options.size()>MAX_OPTIONS)throw new IllegalStateException("Too many native settings options");
        if(kind==ValueKind.FIXED_OPTION && options.isEmpty())throw new IllegalStateException("Fixed option row requires options");
        if(kind!=ValueKind.FIXED_OPTION && !options.isEmpty())throw new IllegalStateException("Unexpected native settings options");
        if(kind==ValueKind.BOUNDED_TEXT && (maxTextChars<1 || maxTextChars>MAX_TEXT_CHARS))throw new IllegalStateException("Invalid text bound");
        Set<String> values=new HashSet<>();
        for(Option option:options)if(!values.add(option.value))throw new IllegalStateException("Duplicate native settings option");
        return new Row(id, title, route,kind,options,maxTextChars);
    }

    private static void validate(List<Section> sections) {
        Set<SectionId> sectionIds = new HashSet<>();
        Set<String> rowIds = new HashSet<>();
        int rows = 0;
        for (Section section : sections) {
            if (!sectionIds.add(section.id)) throw new IllegalStateException("Duplicate native settings section");
            if (section.rows.isEmpty()) throw new IllegalStateException("Empty native settings section");
            for (Row row : section.rows()) {
                if (!rowIds.add(row.id)) throw new IllegalStateException("Duplicate native settings row");
                rows++;
            }
        }

        if (rows > MAX_ROWS) throw new IllegalStateException("Native settings row limit reached");
    }

    static String text(String value, int maximum) {
        if (value == null || value.length() > maximum) throw invalid();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isISOControl(c)) throw invalid();
            if (Character.isHighSurrogate(c)) {
                if (++i >= value.length() || !Character.isLowSurrogate(value.charAt(i))) throw invalid();
            } else if (Character.isLowSurrogate(c)) throw invalid();
        }
        return value;
    }
    static String token(String value){
        if(value==null || value.isEmpty() || value.length()>MAX_OPTION_VALUE_CHARS)throw invalid();
        for(int i=0;i<value.length();i++){char c=value.charAt(i);boolean ok=(c>='a'&&c<='z')||(c>='A'&&c<='Z')||(c>='0'&&c<='9')||c=='_'||c=='-';
            if(!ok)throw invalid();}
        return value.toLowerCase(Locale.ROOT);
    }

    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid native settings directory input"); }
}
