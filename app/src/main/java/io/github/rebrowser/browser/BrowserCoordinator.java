package io.github.rebrowser.browser;

import io.github.rebrowser.bootstrap.ComponentGate;
import io.github.rebrowser.browser.BrowserModels.*;
import io.github.rebrowser.engine.GeckoSessionOwner;
import io.github.rebrowser.engine.PartitionCatalog;
import io.github.rebrowser.engine.GeckoRuntimeOwner;
import io.github.rebrowser.engine.BrowserSessionHost;
import io.github.rebrowser.engine.SessionPresentation;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Shared native browser domain entry (UI/admin authorization happens before entry).
 * Catalogs are process-local; persistent restore/navigation/permission/Popup delegates
 * are NOT silently emulated. Sessions are lazy, same workspace shares one issued context.
 * Source closure never prevents stopping already-owned browser sessions. Neither closing
 * a window nor removing metadata settles underlying writers/tasks or erases context data.
 */
public final class BrowserCoordinator {
    /** Coherent owner-thread metadata for native views; no Session/Actor/DB receipt authority. */
    public static final class WindowSnapshot {
        private final WorkspacesView workspaces;private final TabsView tabs;
        private WindowSnapshot(WorkspacesView workspaces,TabsView tabs){this.workspaces=workspaces;this.tabs=tabs;}
        public WorkspacesView workspaces(){return workspaces;}
        public TabsView selectedWorkspaceTabs(){return tabs;}
    }
    private final ComponentGate gate;
    private final ComponentGate.ResourcePin sourceLifetime;
    private final WorkspaceCatalog workspaces;
    private final TabCatalog tabs;
    private final PartitionCatalog partitions;
    private final GeckoSessionOwner sessions;
    private final Thread thread=Thread.currentThread();
    private final Map<WorkspaceRef,PartitionCatalog.Lease> contexts=new HashMap<>();
    private final Map<TabRef,GeckoSessionOwner.SessionRef> live=new HashMap<>();
    private final Set<TabRef> requiresRestore=new HashSet<>();
    private boolean engineMutation;
    private boolean temporaryNativeOnly;
    /** Real native graph, but deliberately no persistent/secondary/primary restoration path. */
    public static BrowserCoordinator createTemporaryNative(ComponentGate gate,GeckoRuntimeOwner runtime,byte[] acceptedEpoch,int capacity,ComponentGate.BusinessPermit permit){
        gate.requireDataEpoch(permit,acceptedEpoch);WorkspaceCatalog workspaces=new WorkspaceCatalog(gate);TabCatalog tabs=new TabCatalog(workspaces);
        PartitionCatalog partitions=new PartitionCatalog(gate,acceptedEpoch,WorkspaceCatalog.MAX_WORKSPACES);
        GeckoSessionOwner sessions=BrowserSessionHost.createTemporaryOwner(gate,runtime,partitions,capacity);
        BrowserCoordinator domain=new BrowserCoordinator(gate,workspaces,tabs,partitions,sessions);domain.temporaryNativeOnly=true;domain.access(permit);gate.requireDataEpoch(permit,acceptedEpoch);return domain;
    }
    public SessionDisplayAccess sessionDisplayAccess(ComponentGate.BusinessPermit permit){access(permit);return new SessionDisplayAccess(sessions);}
    /** Native display owner only; never IPC. Does not open or claim a View by itself. */
    public static final class SessionDisplayAccess {
        private final GeckoSessionOwner sessions;private SessionDisplayAccess(GeckoSessionOwner sessions){this.sessions=sessions;}
        public io.github.rebrowser.engine.SessionDisplayBinding bind(org.mozilla.geckoview.GeckoView view){return new io.github.rebrowser.engine.SessionDisplayBinding(view,sessions);}
    }
    public Selection captureSelection(ComponentGate.BusinessPermit permit){access(permit);return tabs.captureSelection(permit);}
    public SessionPresentation.Frame selectedPresentation(Selection selection,ComponentGate.BusinessPermit permit){access(permit);tabs.requireSelection(selection,permit);GeckoSessionOwner.SessionRef ref=live.get(selection.tab());return ref==null?null:sessions.presentation(ref,permit);}
    public BrowserCoordinator(ComponentGate gate,WorkspaceCatalog workspaces,TabCatalog tabs,PartitionCatalog partitions,GeckoSessionOwner sessions){
        if(gate==null || workspaces==null || tabs==null || partitions==null || sessions==null)throw BrowserModels.invalid();
        workspaces.requireGate(gate);tabs.requireCatalog(workspaces);partitions.requireGate(gate);sessions.requireBindings(gate,partitions);
        this.gate=gate;sourceLifetime=gate.newResourcePin();this.workspaces=workspaces;this.tabs=tabs;this.partitions=partitions;this.sessions=sessions;
    }
    public WorkspaceRef createTemporary(long expectedWorkspacesRevision,ComponentGate.BusinessPermit permit){
        access(permit);WorkspaceRef ref=workspaces.createTemporary(expectedWorkspacesRevision,permit);
        // No Gecko creation, network load, context clearing or "saved" claim on metadata creation.
        tabs.initializeWorkspace(ref,permit);return ref;
    }
    /** Native UI/admin shared services: caller authorization precedes these domain mutations. */
    public void renameWorkspace(WorkspaceRef ref,long expectedWorkspaceRevision,String nativeTitle,ComponentGate.BusinessPermit permit){access(permit);workspaces.rename(ref,expectedWorkspaceRevision,nativeTitle,permit);}
    public void changeWorkspaceLevel(WorkspaceRef ref,long expectedWorkspaceRevision,Level level,ComponentGate.BusinessPermit permit){access(permit);if(temporaryNativeOnly && level!=Level.TEMPORARY)throw new UnsupportedOperationException("Persistent browser factory/restore not implemented");workspaces.changeLevel(ref,expectedWorkspaceRevision,level,permit);}
    /** Metadata becomes active; closed sessions still require explicit validated restore. */
    public void restoreSecondary(WorkspaceRef ref,long expectedWorkspaceRevision,ComponentGate.BusinessPermit permit){access(permit);workspaces.restoreSecondary(ref,expectedWorkspaceRevision,permit);}
    public WindowSnapshot windowSnapshot(ComponentGate.BusinessPermit permit){
        access(permit);WorkspacesView view=workspaces.snapshot(permit);TabsView selected=view.selected()==null?null:tabs.snapshot(view.selected(),permit);
        WorkspacesView after=workspaces.snapshot(permit);if(after.revision()!=view.revision() || after.selected()!=view.selected())throw new IllegalStateException("Browser window snapshot changed during capture");gate.checkCurrent(permit);return new WindowSnapshot(view,selected);
    }
    public TabRef addTab(WorkspaceRef workspace,long expectedTabsRevision,ComponentGate.BusinessPermit permit){access(permit);return tabs.addTab(workspace,expectedTabsRevision,permit);}
    public void selectWorkspace(WorkspaceRef workspace,long expectedWorkspacesRevision,ComponentGate.BusinessPermit permit){access(permit);workspaces.select(workspace,expectedWorkspacesRevision,permit);}
    public void selectTab(TabRef tab,long expectedTabsRevision,ComponentGate.BusinessPermit permit){access(permit);tabs.select(tab,expectedTabsRevision,permit);}
    /** Caller attaches through SessionDisplayBinding. OPEN_REQUESTED is not page-ready. */
    public GeckoSessionOwner.SessionRef requireSelectedSession(ComponentGate.BusinessPermit permit){
        access(permit);WorkspaceRef workspace=workspaces.snapshot(permit).selected();if(workspace==null)throw new IllegalStateException("No active browser workspace");
        TabRef tab=tabs.snapshot(workspace,permit).selected();GeckoSessionOwner.SessionRef existing=live.get(tab);
        if(existing!=null){sessions.requireOpen(existing,permit);return existing;}
        if(requiresRestore.contains(tab))throw new IllegalStateException("Explicit validated checkpoint restore required; no blind reopen");
        PartitionCatalog.Lease context=contexts.get(workspace);
        if(context==null){context=temporaryNativeOnly?partitions.createPrivateBrowser(workspace,permit):partitions.create(PartitionCatalog.Domain.BROWSER,workspace,permit);contexts.put(workspace,context);}
        engineMutation=true;
        try{GeckoSessionOwner.SessionRef opened=sessions.open(context,tab,permit);live.put(tab,opened);return opened;}
        finally{engineMutation=false;}
    }
    /**
     * Native explicit UI/current-authorized administrator only. Caller must commit its job
     * dispatch intent before entry. Return is a native dispatch attempt, NOT page success;
     * no retry or generation rollback after a thrown/unknown SDK result. Delegates must
     * bind actual document evidence, never acquire a fresh lease for an old event.
     */
    public TabCatalog.DocumentLease navigateSelected(Selection selection,SearchEngines.Target target,ComponentGate.BusinessPermit permit){
        access(permit);if(target==null)throw BrowserModels.invalid();tabs.requireSelection(selection,permit);
        GeckoSessionOwner.SessionRef ref=requireSelectedSession(permit);tabs.requireSelection(selection,permit);
        TabCatalog.DocumentLease lease=tabs.beginDocument(selection.tab(),selection.tabsRevision(),permit);engineMutation=true;
        try{sessions.requireOpen(ref,permit).loadUri(target.url());return lease;}finally{engineMutation=false;}
    }
    /** Exact current native history handoff, no global BACK or inferred navigation success. */
    public void traverseSelected(Selection selection,boolean backwards,ComponentGate.BusinessPermit permit){
        access(permit);tabs.requireSelection(selection,permit);GeckoSessionOwner.SessionRef ref=live.get(selection.tab());if(ref==null)throw new IllegalStateException("No current session");
        SessionPresentation.Frame observed=sessions.presentation(ref,permit);if(backwards?!observed.reportedCanGoBack():!observed.reportedCanGoForward())throw new IllegalStateException("History capability not observed");
        tabs.requireSelection(selection,permit);tabs.beginDocument(selection.tab(),selection.tabsRevision(),permit);engineMutation=true;
        try{org.mozilla.geckoview.GeckoSession actual=sessions.requireOpen(ref,permit);if(backwards)actual.goBack();else actual.goForward();}finally{engineMutation=false;}
    }
    public TabCatalog.CloseResult closeTab(TabRef tab,long expectedTabsRevision,ComponentGate.BusinessPermit permit){
        access(permit);TabsView view=tabs.snapshot(tab.workspace(),permit);if(view.revision()!=expectedTabsRevision)throw new IllegalStateException("Stale tab revision");
        engineMutation=true;
        try{closeOwnedSessionInternal(tab);TabCatalog.CloseResult result=tabs.close(tab,expectedTabsRevision,permit);requiresRestore.remove(tab);return result;}
        finally{engineMutation=false;}
    }
    /** Detach display first. No new source permit/site login is needed to stop owned work. */
    public void closeOwnedSession(TabRef tab){
        ownerThread();if(engineMutation)throw new IllegalStateException("Reentrant browser engine mutation forbidden");engineMutation=true;
        try{closeOwnedSessionInternal(tab);}finally{engineMutation=false;}
    }
    private void closeOwnedSessionInternal(TabRef tab){
        GeckoSessionOwner.SessionRef ref=live.get(tab);if(ref==null)return;
        sessions.close(ref);live.remove(tab);requiresRestore.add(tab);
    }
    public void shelfSecondary(WorkspaceRef workspace,long expectedWorkspaceRevision,ComponentGate.BusinessPermit permit){
        access(permit);WorkspaceView current=workspaces.workspace(workspace,permit);
        if(current.revision()!=expectedWorkspaceRevision || current.level()!=Level.SECONDARY || current.placement()!=Placement.ACTIVE)throw BrowserModels.invalid();
        engineMutation=true;
        try{for(TabView tab:tabs.snapshot(workspace,permit).tabs())closeOwnedSessionInternal(tab.ref());workspaces.shelfSecondary(workspace,expectedWorkspaceRevision,permit);}
        finally{engineMutation=false;}
    }
    public void removeWorkspace(WorkspaceRef workspace,long expectedWorkspaceRevision,ComponentGate.BusinessPermit permit){
        access(permit);WorkspaceView current=workspaces.workspace(workspace,permit);
        if(current.revision()!=expectedWorkspaceRevision || current.level()==Level.PRIMARY || (current.level()==Level.SECONDARY && current.placement()!=Placement.SHELVED))throw BrowserModels.invalid();
        TabsView group=tabs.snapshot(workspace,permit);engineMutation=true;
        try{
            for(TabView tab:group.tabs())closeOwnedSessionInternal(tab.ref());
            PartitionCatalog.Lease context=contexts.get(workspace);
            if(context!=null){partitions.fence(context,permit);partitions.retire(context,permit);} // retirement, NOT erasure/quiescence
            tabs.retireWorkspace(workspace,permit);workspaces.remove(workspace,expectedWorkspaceRevision,permit);contexts.remove(workspace);
            for(TabView tab:group.tabs())requiresRestore.remove(tab.ref());
        }finally{engineMutation=false;}
    }
    public WorkspacesView workspaces(ComponentGate.BusinessPermit permit){access(permit);return workspaces.snapshot(permit);}
    public TabsView tabs(WorkspaceRef workspace,ComponentGate.BusinessPermit permit){access(permit);return tabs.snapshot(workspace,permit);}
    private void access(ComponentGate.BusinessPermit permit){ownerThread();if(engineMutation)throw new IllegalStateException("Reentrant browser engine mutation forbidden");sourceLifetime.checkOrBind(permit);}
    private void ownerThread(){if(Thread.currentThread()!=thread)throw new IllegalStateException("Browser coordinator owner thread required");}
}
