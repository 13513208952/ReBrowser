package io.github.rebrowser.browser;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Native browser handles and immutable views. IDs/views are NOT administrator authority. */
public final class BrowserModels {
    public enum Level { TEMPORARY, SECONDARY, PRIMARY }
    public enum Placement { ACTIVE, SHELVED }
    public static final class WorkspaceRef {
        private final Object issuer;
        private final String id;
        private WorkspaceRef(Object issuer){this.issuer=issuer;id=randomId("ws_");}
        public String id(){return id;}
        boolean issuedBy(Object owner){return issuer==owner;}
    }
    public static final class TabRef {
        private final Object issuer;
        private final WorkspaceRef workspace;
        private final String id;
        private TabRef(Object issuer,WorkspaceRef workspace){this.issuer=issuer;this.workspace=workspace;id=randomId("tab_");}
        public String id(){return id;}
        public WorkspaceRef workspace(){return workspace;}
        boolean issuedBy(Object owner){return issuer==owner;}
    }
    public static final class WorkspaceView {
        private final WorkspaceRef ref;
        private final String title;
        private final Level level;
        private final Placement placement;
        private final long revision;
        WorkspaceView(WorkspaceRef ref,String title,Level level,Placement placement,long revision){this.ref=ref;this.title=title;this.level=level;this.placement=placement;this.revision=revision;}
        public WorkspaceRef ref(){return ref;}
        public String title(){return title;}
        public Level level(){return level;}
        public Placement placement(){return placement;}
        public long revision(){return revision;}
        /** Retention INTENT only. BrowserStore/epoch transaction must commit it separately. */
        public boolean wantsRetention(){return level!=Level.TEMPORARY;}
    }
    public static final class WorkspacesView {
        private final List<WorkspaceView> active,shelved;
        private final WorkspaceRef selected;
        private final long revision;
        WorkspacesView(List<WorkspaceView> active,List<WorkspaceView> shelved,WorkspaceRef selected,long revision){this.active=immutable(active);this.shelved=immutable(shelved);this.selected=selected;this.revision=revision;}
        public List<WorkspaceView> active(){return active;}
        public List<WorkspaceView> shelved(){return shelved;}
        public WorkspaceRef selected(){return selected;}
        public long revision(){return revision;}
    }
    public static final class TabView {
        private final TabRef ref,parent;
        private final String title,displayUrl;
        private final long generation;
        TabView(TabRef ref,TabRef parent,String title,String displayUrl,long generation){this.ref=ref;this.parent=parent;this.title=title;this.displayUrl=displayUrl;this.generation=generation;}
        public TabRef ref(){return ref;}
        public TabRef popupParent(){return parent;}
        public String title(){return title;}
        /** Display observation ONLY, never use as an already-authorized load target. */
        public String displayUrl(){return displayUrl;}
        public long documentGeneration(){return generation;}
    }
    public static final class TabsView {
        private final WorkspaceRef workspace;
        private final List<TabView> tabs;
        private final TabRef selected;
        private final long revision;
        TabsView(WorkspaceRef workspace,List<TabView> tabs,TabRef selected,long revision){this.workspace=workspace;this.tabs=immutable(tabs);this.selected=selected;this.revision=revision;}
        public WorkspaceRef workspace(){return workspace;}
        public List<TabView> tabs(){return tabs;}
        public TabRef selected(){return selected;}
        public long revision(){return revision;}
    }
    private BrowserModels(){}
    static WorkspaceRef workspaceRef(Object issuer){return new WorkspaceRef(issuer);}
    static TabRef tabRef(Object issuer,WorkspaceRef workspace){return new TabRef(issuer,workspace);}
    static <T> List<T> immutable(List<T> values){return Collections.unmodifiableList(new ArrayList<>(values));}
    static long next(long before){if(before==Long.MAX_VALUE)throw new IllegalStateException("Browser revision exhausted");return before+1;}
    static String title(String value,String fallback){
        if(value==null)return fallback;validateText(value,4096);StringBuilder out=new StringBuilder();boolean space=false;int count=0;
        for(int offset=0;offset<value.length() && count<160;){int cp=value.codePointAt(offset);offset+=Character.charCount(cp);
            if(Character.isWhitespace(cp)||Character.isISOControl(cp)){space=out.length()>0;continue;}
            if(space){if(count==159)break;out.append(' ');count++;space=false;}
            out.appendCodePoint(cp);count++;
        }
        return out.length()==0?fallback:out.toString();
    }
    static String displayUrl(String value){if(value==null)return "";validateText(value,8192);for(int i=0;i<value.length();i++)if(Character.isISOControl(value.charAt(i)))throw invalid();return value;}
    private static void validateText(String value,int limit){
        if(value.length()>limit)throw invalid();
        for(int i=0;i<value.length();i++){char c=value.charAt(i);if(Character.isHighSurrogate(c)){if(++i>=value.length() || !Character.isLowSurrogate(value.charAt(i)))throw invalid();}else if(Character.isLowSurrogate(c))throw invalid();}
    }
    private static String randomId(String prefix){byte[] bytes=new byte[16];new SecureRandom().nextBytes(bytes);StringBuilder b=new StringBuilder(prefix);for(byte v:bytes){b.append("0123456789abcdef".charAt((v&255)>>>4));b.append("0123456789abcdef".charAt(v&15));}return b.toString();}
    static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid native browser input");}
}
