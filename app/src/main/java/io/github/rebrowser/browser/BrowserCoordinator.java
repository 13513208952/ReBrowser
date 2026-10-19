package io.github.rebrowser.browser;

import io.github.rebrowser.bootstrap.ComponentGate;
import io.github.rebrowser.browser.BrowserModels.*;
import io.github.rebrowser.engine.GeckoSessionOwner;
import io.github.rebrowser.engine.PartitionCatalog;
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
    private final ComponentGate gate;
    private final WorkspaceCatalog workspaces;
    private final TabCatalog tabs;
    private final PartitionCatalog partitions;
    private final GeckoSessionOwner sessions;
    private final Thread thread=Thread.currentThread();
    private final Map<WorkspaceRef,PartitionCatalog.Lease> contexts=new HashMap<>();
    private final Map<TabRef,GeckoSessionOwner.SessionRef> live=new HashMap<>();
    private final Set<TabRef> requiresRestore=new HashSet<>();
    private boolean engineMutation;
    public BrowserCoordinator(ComponentGate gate,WorkspaceCatalog workspaces,TabCatalog tabs,PartitionCatalog partitions,GeckoSessionOwner sessions){
        if(gate==null || workspaces==null || tabs==null || partitions==null || sessions==null)throw BrowserModels.invalid();
        workspaces.requireGate(gate);tabs.requireCatalog(workspaces);partitions.requireGate(gate);sessions.requireBindings(gate,partitions);
        this.gate=gate;this.workspaces=workspaces;this.tabs=tabs;this.partitions=partitions;this.sessions=sessions;
    }
    public WorkspaceRef createTemporary(long expectedWorkspacesRevision,ComponentGate.BusinessPermit permit){
        access(permit);WorkspaceRef ref=workspaces.createTemporary(expectedWorkspacesRevision,permit);
        // No Gecko creation, network load, context clearing or "saved" claim on metadata creation.
        tabs.initializeWorkspace(ref,permit);return ref;
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
        if(context==null){context=partitions.create(PartitionCatalog.Domain.BROWSER,workspace,permit);contexts.put(workspace,context);}
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
    public TabCatalog.DocumentLease navigateSelected(WorkspaceRef expectedWorkspace,TabRef expectedTab,long expectedTabsRevision,SearchEngines.Target target,ComponentGate.BusinessPermit permit){
        access(permit);if(target==null || workspaces.snapshot(permit).selected()!=expectedWorkspace)throw BrowserModels.invalid();
        TabsView group=tabs.snapshot(expectedWorkspace,permit);if(group.selected()!=expectedTab || group.revision()!=expectedTabsRevision)throw new IllegalStateException("Stale navigation target");
        GeckoSessionOwner.SessionRef ref=requireSelectedSession(permit);
        TabCatalog.DocumentLease lease=tabs.beginDocument(expectedTab,expectedTabsRevision,permit);engineMutation=true;
        try{sessions.requireOpen(ref,permit).loadUri(target.url());return lease;}finally{engineMutation=false;}
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
    private void access(ComponentGate.BusinessPermit permit){ownerThread();if(engineMutation)throw new IllegalStateException("Reentrant browser engine mutation forbidden");gate.checkCurrent(permit);}
    private void ownerThread(){if(Thread.currentThread()!=thread)throw new IllegalStateException("Browser coordinator owner thread required");}
}
