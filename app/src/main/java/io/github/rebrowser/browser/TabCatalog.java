package io.github.rebrowser.browser;

import io.github.rebrowser.bootstrap.ComponentGate;
import io.github.rebrowser.browser.BrowserModels.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Owner-thread native tab selection/Popup ancestry and document-generation metadata.
 * Pure metadata only: close/retire returns refs to the coordinator, not a Gecko close,
 * context deletion, persisted checkpoint, task settlement or permission revocation.
 */
public final class TabCatalog {
    public static final int MAX_TABS_PER_WORKSPACE=50;
    private final WorkspaceCatalog workspaces;
    private final Map<WorkspaceRef,Group> groups=new LinkedHashMap<>();
    private static final class Tab {
        final TabRef ref;
        TabRef parent;
        long parentGeneration,generation=1;
        String title="新子标签页",url="";
        Tab(TabRef ref){this.ref=ref;}
        TabView view(){return new TabView(ref,parent,title,url,generation);}
    }
    private static final class Group {
        final WorkspaceRef workspace;
        final Map<TabRef,Tab> tabs=new LinkedHashMap<>();
        TabRef selected;
        long revision=1;
        Group(WorkspaceRef workspace){this.workspace=workspace;}
    }
    /** Native METADATA generation/dispatch intent, NOT actual Gecko document identity.
     * NEVER reacquire the latest lease for an old callback lacking native document evidence. */
    public static final class DocumentLease {
        private final TabCatalog issuer;
        private final TabRef tab;
        private final long generation;
        private DocumentLease(TabCatalog issuer,Tab tab){this.issuer=issuer;this.tab=tab.ref;generation=tab.generation;}
        public TabRef tab(){return tab;}
        public long generation(){return generation;}
    }
    public static final class CloseResult {
        private final TabRef closed,selected;
        private final boolean replacedLast;
        private CloseResult(TabRef closed,TabRef selected,boolean replacedLast){this.closed=closed;this.selected=selected;this.replacedLast=replacedLast;}
        public TabRef closed(){return closed;}
        public TabRef selected(){return selected;}
        public boolean replacedLast(){return replacedLast;}
    }
    public TabCatalog(WorkspaceCatalog workspaces){if(workspaces==null)throw BrowserModels.invalid();this.workspaces=workspaces;}
    void requireCatalog(WorkspaceCatalog expected){if(workspaces!=expected)throw BrowserModels.invalid();}
    public TabRef initializeWorkspace(WorkspaceRef workspace,ComponentGate.BusinessPermit permit){
        workspaces.requireActive(workspace,permit);if(groups.containsKey(workspace))throw new IllegalStateException("Tab group already exists");
        if(groups.size()>=WorkspaceCatalog.MAX_WORKSPACES)throw new IllegalStateException("Tab group limit reached");
        Group group=new Group(workspace);Tab first=new Tab(BrowserModels.tabRef(this,workspace));group.tabs.put(first.ref,first);group.selected=first.ref;groups.put(workspace,group);return first.ref;
    }
    public TabRef addTab(WorkspaceRef workspace,long expectedTabsRevision,ComponentGate.BusinessPermit permit){
        Group group=active(workspace,expectedTabsRevision,permit);return add(group,null);
    }
    /** onNewSession uses this exact current native source, not a sender-claimed tab ID. */
    public TabRef addPopup(DocumentLease source,long expectedTabsRevision,ComponentGate.BusinessPermit permit){
        if(source==null || source.issuer!=this)throw BrowserModels.invalid();workspaces.requireSelected(source.tab.workspace(),permit);
        Group group=active(source.tab.workspace(),expectedTabsRevision,permit);Tab parent=tab(group,source.tab);
        if(group.selected!=source.tab || parent.generation!=source.generation)throw stale();return add(group,parent);
    }
    private TabRef add(Group group,Tab parent){
        if(group.tabs.size()>=MAX_TABS_PER_WORKSPACE)throw new IllegalStateException("Tab limit reached");
        long next=BrowserModels.next(group.revision);Tab tab=new Tab(BrowserModels.tabRef(this,group.workspace));
        for(Tab known:group.tabs.values())if(known.ref.id().equals(tab.ref.id()))throw new IllegalStateException("Tab identity collision");
        if(parent!=null){tab.parent=parent.ref;tab.parentGeneration=parent.generation;}
        group.tabs.put(tab.ref,tab);group.selected=tab.ref;group.revision=next;return tab.ref;
    }
    public void select(TabRef ref,long expectedTabsRevision,ComponentGate.BusinessPermit permit){
        Group group=active(workspace(ref),expectedTabsRevision,permit);tab(group,ref);if(group.selected!=ref){long next=BrowserModels.next(group.revision);group.selected=ref;group.revision=next;}
    }
    public DocumentLease currentDocument(TabRef ref,ComponentGate.BusinessPermit permit){
        workspaces.requireActive(workspace(ref),permit);return new DocumentLease(this,tab(group(ref.workspace()),ref));
    }
    /** Retire prior metadata generation before dispatch; NOT successful new-document establishment. */
    public DocumentLease beginDocument(TabRef ref,long expectedTabsRevision,ComponentGate.BusinessPermit permit){
        Group group=active(workspace(ref),expectedTabsRevision,permit);Tab tab=tab(group,ref);
        long generation=BrowserModels.next(tab.generation),revision=BrowserModels.next(group.revision);
        tab.generation=generation;clearParent(group,ref);group.revision=revision;return new DocumentLease(this,tab);
    }
    /** Native session display observations ONLY. This does NOT issue/acquire a document lease,
     * advance document generation, authorize website actions or prove a committed document.
     * The browser owner must bind the original actual session to this issued TabRef. */
    boolean updateSessionReportedMetadata(TabRef ref,String title,String displayUrl,ComponentGate.BusinessPermit permit){
        workspaces.access(permit);workspace(ref);if(!workspaces.registered(ref.workspace(),permit) || workspaces.workspace(ref.workspace(),permit).placement()!=Placement.ACTIVE)return false;
        Group group=groups.get(ref.workspace());Tab tab=group==null?null:group.tabs.get(ref);if(tab==null)return false;
        String nextTitle=title==null?tab.title:BrowserModels.title(title,tab.title),nextUrl=displayUrl==null?tab.url:BrowserModels.displayUrl(displayUrl);
        if(nextTitle.equals(tab.title) && nextUrl.equals(tab.url))return false;
        long next=BrowserModels.next(group.revision);tab.title=nextTitle;tab.url=nextUrl;group.revision=next;return true;
    }
    public boolean updateMetadata(DocumentLease lease,String title,String displayUrl,ComponentGate.BusinessPermit permit){
        workspaces.access(permit);if(lease==null || lease.issuer!=this)throw BrowserModels.invalid();
        if(!workspaces.registered(lease.tab.workspace(),permit))return false;
        if(workspaces.workspace(lease.tab.workspace(),permit).placement()!=Placement.ACTIVE)return false;
        Group group=groups.get(lease.tab.workspace());Tab tab=group==null?null:group.tabs.get(lease.tab);
        if(tab==null || tab.generation!=lease.generation)return false;
        String nextTitle=title==null?tab.title:BrowserModels.title(title,tab.title),nextUrl=displayUrl==null?tab.url:BrowserModels.displayUrl(displayUrl);
        if(!nextTitle.equals(tab.title) || !nextUrl.equals(tab.url)){long next=BrowserModels.next(group.revision);tab.title=nextTitle;tab.url=nextUrl;group.revision=next;}
        return true;
    }
    public CloseResult close(TabRef ref,long expectedTabsRevision,ComponentGate.BusinessPermit permit){
        Group group=active(workspace(ref),expectedTabsRevision,permit);Tab closing=tab(group,ref);long next=BrowserModels.next(group.revision);
        if(group.tabs.size()==1){
            Tab replacement=new Tab(BrowserModels.tabRef(this,group.workspace));group.tabs.clear();group.tabs.put(replacement.ref,replacement);group.selected=replacement.ref;group.revision=next;
            return new CloseResult(ref,replacement.ref,true);
        }
        List<TabRef> ordered=new ArrayList<>(group.tabs.keySet());int index=ordered.indexOf(ref);Tab parent=group.tabs.get(closing.parent);
        boolean validParent=parent!=null && parent.generation==closing.parentGeneration;
        group.tabs.remove(ref);clearParent(group,ref);
        if(group.selected==ref){ordered.remove(ref);group.selected=validParent?parent.ref:ordered.get(Math.min(index,ordered.size()-1));}
        group.revision=next;return new CloseResult(ref,group.selected,false);
    }
    /** Metadata retirement only. Coordinator still owns actual session/worker close receipts. */
    public List<TabRef> retireWorkspace(WorkspaceRef workspace,ComponentGate.BusinessPermit permit){
        workspaces.registered(workspace,permit);Group group=groups.remove(workspace);
        return group==null?Collections.<TabRef>emptyList():BrowserModels.immutable(new ArrayList<>(group.tabs.keySet()));
    }
    /** Exact current selection snapshot. Cannot be reconstructed from a sender's opaque IDs. */
    public Selection captureSelection(ComponentGate.BusinessPermit permit){
        WorkspacesView all=workspaces.snapshot(permit);if(all.selected()==null)throw new IllegalStateException("No selected workspace");
        TabsView group=snapshot(all.selected(),permit);Selection result=BrowserModels.selection(this,all,group);requireSelection(result,permit);return result;
    }
    public void requireSelection(Selection selection,ComponentGate.BusinessPermit permit){
        workspaces.access(permit);if(selection==null || !selection.issuedBy(this))throw BrowserModels.invalid();
        WorkspacesView all=workspaces.snapshot(permit);if(all.revision()!=selection.workspacesRevision() || all.selected()!=selection.workspace())throw stale();
        Group group=active(selection.workspace(),selection.tabsRevision(),permit);if(group.selected!=selection.tab())throw stale();tab(group,selection.tab());
    }
    public TabsView snapshot(WorkspaceRef workspace,ComponentGate.BusinessPermit permit){
        workspaces.access(permit);if(!workspaces.registered(workspace,permit))throw BrowserModels.invalid();Group group=group(workspace);List<TabView> tabs=new ArrayList<>();for(Tab t:group.tabs.values())tabs.add(t.view());return new TabsView(workspace,tabs,group.selected,group.revision);
    }
    /** Owner-thread coherent bounded retained roster. Does NOT capture Gecko state or permit restore. */
    public RetainedCapture captureRetained(long expectedWorkspacesRevision,ComponentGate.BusinessPermit permit){
        workspaces.requireCapturedRevision(expectedWorkspacesRevision,permit);
        ComponentGate.DataEpochPin epoch=workspaces.captureDataEpoch(permit);WorkspacesView all=workspaces.snapshot(permit);
        List<WorkspaceView> ordered=new ArrayList<>(all.active());ordered.addAll(all.shelved());List<RetainedGroup> retained=new ArrayList<>();WorkspaceRef selected=null;
        for(WorkspaceView workspace:ordered)if(workspace.wantsRetention()){
            TabsView current=snapshot(workspace.ref(),permit);if(current.tabs().isEmpty() || current.selected()==null)throw new IllegalStateException("Incomplete retained tab roster");
            retained.add(BrowserModels.retainedGroup(workspace,current));if(all.selected()==workspace.ref())selected=workspace.ref();
        }
        workspaces.requireCapturedRevision(expectedWorkspacesRevision,permit);
        for(RetainedGroup captured:retained)if(snapshot(captured.workspace().ref(),permit).revision()!=captured.tabs().revision())throw stale();
        epoch.requireCurrent();return BrowserModels.retainedCapture(epoch,all.revision(),selected,retained);
    }
    private Group active(WorkspaceRef workspace,long expected,ComponentGate.BusinessPermit permit){workspaces.requireActive(workspace,permit);Group group=group(workspace);if(group.revision!=expected)throw stale();return group;}
    private Group group(WorkspaceRef workspace){Group group=groups.get(workspace);if(group==null)throw BrowserModels.invalid();return group;}
    private Tab tab(Group group,TabRef ref){if(ref==null || !ref.issuedBy(this) || ref.workspace()!=group.workspace)throw BrowserModels.invalid();Tab tab=group.tabs.get(ref);if(tab==null)throw BrowserModels.invalid();return tab;}
    private WorkspaceRef workspace(TabRef ref){if(ref==null || !ref.issuedBy(this))throw BrowserModels.invalid();return ref.workspace();}
    private static void clearParent(Group group,TabRef source){for(Tab t:group.tabs.values())if(t.parent==source){t.parent=null;t.parentGeneration=0;}}
    private static IllegalStateException stale(){return new IllegalStateException("Stale browser tab/document selection");}
}
