package io.github.rebrowser.browser;

import io.github.rebrowser.browser.BrowserModels.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Bounded native retained-roster binary format. Parse results are UNVERIFIED display metadata,
 * never restored Ref/Actor/document/partition/permission or latest-snapshot authority. The outer
 * PrivateRecordStore supplies separate AEAD/source binding. No cookies/Popup ancestry/SessionState.
 */
public final class BrowserCatalogCodec {
    public static final int MAX_BYTES=4*1024*1024,VERSION=1;
    private static final long MAGIC=0x5242434154303100L; // RBCAT01\0
    private static final int NONE=65535,HEADER=22,TITLE_BYTES=640,URL_BYTES=24576;
    public static final class Tab {
        private final String id,title,displayUrl;private final long generation;
        private Tab(String id,String title,String url,long generation){this.id=id;this.title=title;displayUrl=url;this.generation=generation;}
        public String id(){return id;}public String title(){return title;}
        public String displayUrl(){return displayUrl;}
        /** Stored metadata counter ONLY, NOT actual current document identity. */
        public long metadataGeneration(){return generation;}
    }
    public static final class Workspace {
        private final String id,title;private final Level level;private final Placement placement;
        private final long revision,tabsRevision;private final int selectedTab;private final List<Tab> tabs;
        private Workspace(String id,String title,Level level,Placement placement,long revision,long tabsRevision,int selectedTab,List<Tab> tabs){this.id=id;this.title=title;this.level=level;this.placement=placement;this.revision=revision;this.tabsRevision=tabsRevision;this.selectedTab=selectedTab;this.tabs=Collections.unmodifiableList(tabs);}
        public String id(){return id;}public String title(){return title;}public Level level(){return level;}public Placement placement(){return placement;}
        public long metadataRevision(){return revision;}public long tabsMetadataRevision(){return tabsRevision;}
        public List<Tab> tabs(){return tabs;}public int selectedTabIndex(){return selectedTab;}
    }
    public static final class UnverifiedCatalog {
        private final long revision;private final int selected;private final List<Workspace> workspaces;private final int tabCount;
        private UnverifiedCatalog(long revision,int selected,List<Workspace> workspaces,int tabCount){this.revision=revision;this.selected=selected;this.workspaces=Collections.unmodifiableList(workspaces);this.tabCount=tabCount;}
        public long metadataRevision(){return revision;}public int selectedWorkspaceIndex(){return selected;}
        public List<Workspace> workspaces(){return workspaces;}public int tabCount(){return tabCount;}
    }
    private BrowserCatalogCodec(){}
    public static byte[] encode(RetainedCapture capture){
        if(capture==null)throw bad();capture.requireCurrentSource();List<RetainedGroup> groups=capture.groups();
        if(groups.size()>WorkspaceCatalog.MAX_WORKSPACES || capture.revision()<0)throw bad();
        int size=HEADER,selected=-1,primary=0;Set<String> workspaceIds=new HashSet<>(),tabIds=new HashSet<>();
        for(int i=0;i<groups.size();i++){
            RetainedGroup group=groups.get(i);WorkspaceView w=group.workspace();TabsView tabs=group.tabs();
            if(!workspaceIds.add(w.ref().id()) || w.revision()<1 || tabs.revision()<1 || tabs.tabs().isEmpty() || tabs.tabs().size()>TabCatalog.MAX_TABS_PER_WORKSPACE)throw bad();
            if(w.level()==Level.PRIMARY){if(++primary>WorkspaceCatalog.MAX_PRIMARY || w.placement()!=Placement.ACTIVE)throw bad();}
            else if(w.level()!=Level.SECONDARY)throw bad();
            if(capture.selectedRetainedWorkspace()==w.ref()){if(w.placement()!=Placement.ACTIVE)throw bad();selected=i;}
            idBytes(w.ref().id(),"ws_");int tabSelection=-1;
            size=add(size,40+utf8(w.title(),TITLE_BYTES,true).length);
            for(int j=0;j<tabs.tabs().size();j++){
                TabView tab=tabs.tabs().get(j);if(tab.ref().workspace()!=w.ref() || !tabIds.add(tab.ref().id()) || tab.documentGeneration()<1)throw bad();
                idBytes(tab.ref().id(),"tab_");if(tab.ref()==tabs.selected())tabSelection=j;
                size=add(size,28+utf8(tab.title(),TITLE_BYTES,true).length+utf8(tab.displayUrl(),URL_BYTES,false).length);
            }
            if(tabSelection<0)throw bad();
        }
        if(capture.selectedRetainedWorkspace()!=null && selected<0)throw bad();capture.requireCurrentSource();
        byte[] result=new byte[size];ByteBuffer out=ByteBuffer.wrap(result).order(ByteOrder.BIG_ENDIAN);
        try{
            out.putLong(MAGIC).putShort((short)VERSION).putShort((short)groups.size()).putLong(capture.revision()).putShort((short)(selected<0?NONE:selected));
            for(RetainedGroup group:groups){
                WorkspaceView w=group.workspace();TabsView tabs=group.tabs();int selectedTab=-1;for(int j=0;j<tabs.tabs().size();j++)if(tabs.tabs().get(j).ref()==tabs.selected())selectedTab=j;
                out.put(idBytes(w.ref().id(),"ws_")).put((byte)(w.level()==Level.PRIMARY?3:2)).put((byte)(w.placement()==Placement.ACTIVE?1:2)).putLong(w.revision()).putLong(tabs.revision());
                putText(out,w.title(),TITLE_BYTES,true);out.putShort((short)tabs.tabs().size()).putShort((short)selectedTab);
                for(TabView tab:tabs.tabs()){out.put(idBytes(tab.ref().id(),"tab_")).putLong(tab.documentGeneration());putText(out,tab.title(),TITLE_BYTES,true);putText(out,tab.displayUrl(),URL_BYTES,false);}
            }
            if(out.hasRemaining())throw bad();capture.requireCurrentSource();return result;
        }catch(RuntimeException|Error failure){Arrays.fill(result,(byte)0);throw failure;}
    }
    /** Does not mutate caller position; accepts exact bytes only, no tail/truncation/repair. */
    public static UnverifiedCatalog decodeUnverified(ByteBuffer bytes){
        if(bytes==null || bytes.remaining()<HEADER || bytes.remaining()>MAX_BYTES)throw bad();ByteBuffer in=bytes.asReadOnlyBuffer().order(ByteOrder.BIG_ENDIAN);
        try{
            if(in.getLong()!=MAGIC || unsigned(in.getShort())!=VERSION)throw bad();int count=unsigned(in.getShort());long revision=in.getLong();int selected=unsigned(in.getShort());
            if(count>WorkspaceCatalog.MAX_WORKSPACES || revision<0 || (selected!=NONE && selected>=count))throw bad();
            List<Workspace> groups=new ArrayList<>();Set<String> wsIds=new HashSet<>(),tabIds=new HashSet<>();int primary=0,totalTabs=0;
            for(int i=0;i<count;i++){
                String id=readId(in,"ws_");int level=in.get()&255,placement=in.get()&255;long wsRevision=in.getLong(),tabsRevision=in.getLong();
                if(!wsIds.add(id) || (level!=2 && level!=3) || (placement!=1 && placement!=2) || wsRevision<1 || tabsRevision<1 || (level==3 && placement!=1))throw bad();
                if(level==3 && ++primary>WorkspaceCatalog.MAX_PRIMARY)throw bad();String title=readText(in,TITLE_BYTES,true);int n=unsigned(in.getShort()),tabSelection=unsigned(in.getShort());
                if(n<1 || n>TabCatalog.MAX_TABS_PER_WORKSPACE || tabSelection>=n)throw bad();List<Tab> tabs=new ArrayList<>();
                for(int j=0;j<n;j++){
                    String tabId=readId(in,"tab_");long generation=in.getLong();if(!tabIds.add(tabId) || generation<1)throw bad();
                    String tabTitle=readText(in,TITLE_BYTES,true),url=readText(in,URL_BYTES,false);tabs.add(new Tab(tabId,tabTitle,url,generation));
                }
                totalTabs+=n;groups.add(new Workspace(id,title,level==3?Level.PRIMARY:Level.SECONDARY,placement==1?Placement.ACTIVE:Placement.SHELVED,wsRevision,tabsRevision,tabSelection,tabs));
            }
            if(in.hasRemaining() || (selected!=NONE && groups.get(selected).placement()!=Placement.ACTIVE))throw bad();
            return new UnverifiedCatalog(revision,selected==NONE?-1:selected,groups,totalTabs);
        }catch(java.nio.BufferUnderflowException malformed){throw bad();}
    }
    private static void putText(ByteBuffer out,String text,int max,boolean title){byte[] bytes=utf8(text,max,title);out.putShort((short)bytes.length).put(bytes);}
    private static String readText(ByteBuffer in,int maximum,boolean title){
        int length=unsigned(in.getShort());if(length>maximum || length>in.remaining())throw bad();ByteBuffer slice=in.slice();slice.limit(length);String result;
        try{result=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(slice).toString();}catch(CharacterCodingException invalid){throw bad();}
        in.position(in.position()+length);validateText(result,title);return result;
    }
    private static byte[] utf8(String text,int maximum,boolean title){
        validateText(text,title);try{ByteBuffer b=StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(text));if(b.remaining()>maximum)throw bad();byte[] bytes=new byte[b.remaining()];b.get(bytes);return bytes;}catch(CharacterCodingException invalid){throw bad();}
    }
    private static void validateText(String text,boolean title){
        if(text==null || text.length()>(title?320:8192))throw bad();
        if(title){if(!BrowserModels.title(text,"").equals(text) || text.isEmpty())throw bad();}else BrowserModels.displayUrl(text);
    }
    private static byte[] idBytes(String id,String prefix){
        if(id==null || !id.startsWith(prefix) || id.length()!=prefix.length()+32)throw bad();byte[] bytes=new byte[16];
        for(int i=0;i<16;i++){int hi=hex(id.charAt(prefix.length()+2*i)),lo=hex(id.charAt(prefix.length()+2*i+1));bytes[i]=(byte)((hi<<4)|lo);}return bytes;
    }
    private static String readId(ByteBuffer in,String prefix){StringBuilder id=new StringBuilder(prefix);for(int i=0;i<16;i++){int b=in.get()&255;id.append("0123456789abcdef".charAt(b>>>4)).append("0123456789abcdef".charAt(b&15));}return id.toString();}
    private static int hex(char c){int x="0123456789abcdef".indexOf(c);if(x<0)throw bad();return x;}
    private static int unsigned(short n){return n&65535;}
    private static int add(int size,int more){if(more<0 || more>MAX_BYTES-size)throw bad();return size+more;}
    private static IllegalArgumentException bad(){return new IllegalArgumentException("Invalid retained browser catalog metadata");}
}
