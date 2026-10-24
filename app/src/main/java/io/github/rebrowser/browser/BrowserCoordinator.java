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
    /** Native request display only, not page success or document authority. Remains unknown
     * on thrown SDK handoff; never reload another URL/session as a fallback. */
    private static final class RequestedNavigation {final SearchEngines.Target target;final long locationSequence;RequestedNavigation(SearchEngines.Target target,long sequence){this.target=target;locationSequence=sequence;}}
    private final Map<TabRef,RequestedNavigation> requested=new HashMap<>();
    private final Set<TabRef> requiresRestore=new HashSet<>();
    private boolean engineMutation;
    private boolean temporaryNativeOnly;
    public interface NativeWindowObserver {void changed();}
    private java.lang.ref.WeakReference<NativeWindowObserver> windowObserver=new java.lang.ref.WeakReference<>(null);private Object observerToken;
    /** Window owns this subscription and its listener. The process browser holds ONLY a weak
     * reference, so a retired Activity cannot be retained by a session/delegate callback. */
    public final class WindowSubscription implements AutoCloseable {
        private final Object token;private NativeWindowObserver listener;
        private WindowSubscription(Object token,NativeWindowObserver listener){this.token=token;this.listener=listener;}
        public void close(){ownerThread();if(observerToken==token){windowObserver.clear();observerToken=null;}listener=null;}
    }
    public WindowSubscription observeNativeWindow(NativeWindowObserver listener,ComponentGate.BusinessPermit permit){access(permit);if(listener==null || windowObserver.get()!=null)throw new IllegalStateException("Native browser window already observed");Object token=new Object();observerToken=token;windowObserver=new java.lang.ref.WeakReference<>(listener);return new WindowSubscription(token,listener);}
    private void windowChanged(){NativeWindowObserver listener=windowObserver.get();if(listener!=null)try{listener.changed();}catch(RuntimeException unavailable){/* UI publication is not a navigation outcome */}}
    private void sessionReported(TabRef tab,SessionPresentation.Frame frame){
        ownerThread();if(!engineMutation && frame!=null && !frame.retired() && live.containsKey(tab))try{ComponentGate.BusinessPermit permit=gate.requireBusiness();sourceLifetime.checkOrBind(permit);tabs.updateSessionReportedMetadata(tab,frame.reportedTitle(),frame.reportedLocation(),permit);}catch(RuntimeException stale){/* no reassignment to a newer tab/document */}
        windowChanged(); // native window observer coalesces onto its owner event loop
    }
    public interface HistorySink {void visited(HistoryStore.Page page);void sourceChanged(HistoryStore.Source source);void titleChanged(String workspaceId,String tabId,String url,String title);}
    private HistorySink history;
    public void installHistorySink(HistorySink nativeSink,ComponentGate.BusinessPermit permit){access(permit);if(history!=null || nativeSink==null)throw new IllegalStateException("Canonical history already connected");history=nativeSink;}
    private void historyVisit(TabRef tab,String url){
        ownerThread();if(history==null)return;ComponentGate.BusinessPermit permit=gate.requireBusiness();sourceLifetime.checkOrBind(permit);
        WorkspaceView workspace=workspaces.workspace(tab.workspace(),permit);boolean found=false;for(TabView row:tabs.snapshot(tab.workspace(),permit).tabs())if(row.ref()==tab)found=true;
        if(found && workspace.placement()==Placement.ACTIVE)history.visited(new HistoryStore.Page(HistoryStore.Source.from(workspace),tab.id(),"",url));
    }
    private void historyTitle(TabRef tab,String url,String title){ownerThread();if(history==null || url==null)return;ComponentGate.BusinessPermit permit=gate.requireBusiness();sourceLifetime.checkOrBind(permit);workspaces.requireActive(tab.workspace(),permit);history.titleChanged(tab.workspace().id(),tab.id(),url,BrowserModels.title(title,url));}
    private void historySource(WorkspaceRef ref,ComponentGate.BusinessPermit permit){if(history!=null)history.sourceChanged(HistoryStore.Source.from(workspaces.workspace(ref,permit)));}
    /** Real native graph, but deliberately no persistent/secondary/primary restoration path. */
    public static BrowserCoordinator createTemporaryNative(ComponentGate gate,GeckoRuntimeOwner runtime,byte[] acceptedEpoch,int capacity,ComponentGate.BusinessPermit permit){
        gate.requireDataEpoch(permit,acceptedEpoch);WorkspaceCatalog workspaces=new WorkspaceCatalog(gate);TabCatalog tabs=new TabCatalog(workspaces);
        PartitionCatalog partitions=new PartitionCatalog(gate,acceptedEpoch,WorkspaceCatalog.MAX_WORKSPACES);
        final BrowserCoordinator[] nativeOwner=new BrowserCoordinator[1];
        GeckoSessionOwner sessions=BrowserSessionHost.createTemporaryOwner(gate,runtime,partitions,capacity,new BrowserSessionHost.BrowserHistory(){public void visited(TabRef tab,String url){if(nativeOwner[0]!=null)nativeOwner[0].historyVisit(tab,url);}public void titleChanged(TabRef tab,String url,String title){if(nativeOwner[0]!=null)nativeOwner[0].historyTitle(tab,url,title);}public void sessionReported(TabRef tab,SessionPresentation.Frame frame){if(nativeOwner[0]!=null)nativeOwner[0].sessionReported(tab,frame);}});
        BrowserCoordinator domain=new BrowserCoordinator(gate,workspaces,tabs,partitions,sessions);nativeOwner[0]=domain;domain.temporaryNativeOnly=true;domain.access(permit);gate.requireDataEpoch(permit,acceptedEpoch);return domain;
    }
    public SessionDisplayAccess sessionDisplayAccess(ComponentGate.BusinessPermit permit){access(permit);return new SessionDisplayAccess(sessions);}
    /** Native display owner only; never IPC. Does not open or claim a View by itself. */
    public static final class SessionDisplayAccess {
        private final GeckoSessionOwner sessions;private SessionDisplayAccess(GeckoSessionOwner sessions){this.sessions=sessions;}
        public io.github.rebrowser.engine.SessionDisplayBinding bind(org.mozilla.geckoview.GeckoView view){return new io.github.rebrowser.engine.SessionDisplayBinding(view,sessions);}
    }
    /** Explicit metadata persistence intent only, NOT automatic autosave or authenticated-current
     * restore. Even a retained label must never serialize a live private-mode workspace. */
    public RetainedCapture captureRetained(long expectedWorkspacesRevision,ComponentGate.BusinessPermit permit){
        access(permit);RetainedCapture capture=tabs.captureRetained(expectedWorkspacesRevision,permit);
        for(RetainedGroup group:capture.groups()){
            PartitionCatalog.Lease context=contexts.get(group.workspace().ref());
            if(context!=null){partitions.requireOwner(context,group.workspace().ref(),permit);if(context.retention()==PartitionCatalog.Retention.PRIVATE_SESSION)throw new SecurityException("Private session metadata cannot become retained storage");}
        }
        capture.requireBindings(gate,permit);access(permit);return capture;
    }
    public Selection captureSelection(ComponentGate.BusinessPermit permit){access(permit);return tabs.captureSelection(permit);}
    /** Original native session observation for an outstanding UI Back, even if the user has
     * selected another tab. Never resolve "current" again to complete the old operation. */
    public SessionPresentation.Frame observeOwnedSession(GeckoSessionOwner.SessionRef ref,ComponentGate.BusinessPermit permit){access(permit);if(ref==null || !live.containsValue(ref) || sessions.state(ref)!=GeckoSessionOwner.State.OPEN_REQUESTED)return null;try{return sessions.presentation(ref,permit);}catch(IllegalStateException unavailable){return null;}}
    public io.github.rebrowser.engine.PromptDelegate ownedPromptDelegate(GeckoSessionOwner.SessionRef ref,ComponentGate.BusinessPermit permit){access(permit);if(ref==null || !live.containsValue(ref))throw new SecurityException("Unknown prompt session");return sessions.promptDelegate(ref,permit);}
    /** Original actual session, never resolve current again for an outstanding fullscreen Back.
     * SDK return is dispatch only; its new native fullScreen(false) observation settles exit. */
    public void exitOwnedFullscreen(GeckoSessionOwner.SessionRef ref,ComponentGate.BusinessPermit permit){access(permit);if(ref==null || !live.containsValue(ref))throw new SecurityException("Unknown fullscreen session");sessions.requireOpen(ref,permit).exitFullScreen();}
    public boolean ownedSessionEnded(GeckoSessionOwner.SessionRef ref,ComponentGate.BusinessPermit permit){access(permit);return ref!=null && sessions.state(ref)==GeckoSessionOwner.State.CLOSED;}
    public GeckoSessionOwner.SessionRef existingSelectedSession(Selection selection,ComponentGate.BusinessPermit permit){access(permit);tabs.requireSelection(selection,permit);GeckoSessionOwner.SessionRef ref=live.get(selection.tab());if(ref==null || sessions.state(ref)!=GeckoSessionOwner.State.OPEN_REQUESTED)return null;try{sessions.requireOpen(ref,permit);return ref;}catch(IllegalStateException closed){return null;}}
    public SessionPresentation.Frame selectedPresentation(Selection selection,ComponentGate.BusinessPermit permit){access(permit);tabs.requireSelection(selection,permit);GeckoSessionOwner.SessionRef ref=live.get(selection.tab());if(ref==null || sessions.state(ref)!=GeckoSessionOwner.State.OPEN_REQUESTED)return null;try{return sessions.presentation(ref,permit);}catch(IllegalStateException retired){return null;}}
    public SearchEngines.Target selectedRequestedTarget(Selection selection,ComponentGate.BusinessPermit permit){access(permit);tabs.requireSelection(selection,permit);RequestedNavigation pending=requested.get(selection.tab());if(pending==null)return null;GeckoSessionOwner.SessionRef ref=live.get(selection.tab());if(ref!=null && sessions.state(ref)==GeckoSessionOwner.State.OPEN_REQUESTED && selectedPresentation(selection,permit)!=null && nativeLocationSettlesRequest(pending.target,pending.locationSequence,sessions.presentation(ref,permit))){requested.remove(selection.tab());return null;}return pending.target;}
    /** Fresh Gecko opens may report bootstrap about:blank AFTER an explicit web request.
     * It cannot erase that requested display target or pretend the request succeeded. */
    static boolean nativeLocationSettlesRequest(SearchEngines.Target target,long before,SessionPresentation.Frame frame){return target!=null && frame!=null && frame.reportedLocationSequence()>before && frame.reportedLocation()!=null && (!"about:blank".equalsIgnoreCase(frame.reportedLocation()) || target.kind()==SearchEngines.Kind.NEW_TAB);}
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
    public void renameWorkspace(WorkspaceRef ref,long expectedWorkspaceRevision,String nativeTitle,ComponentGate.BusinessPermit permit){access(permit);workspaces.rename(ref,expectedWorkspaceRevision,nativeTitle,permit);historySource(ref,permit);}
    public void changeWorkspaceLevel(WorkspaceRef ref,long expectedWorkspaceRevision,Level level,ComponentGate.BusinessPermit permit){access(permit);if(temporaryNativeOnly && level!=Level.TEMPORARY)throw new UnsupportedOperationException("Persistent browser factory/restore not implemented");workspaces.changeLevel(ref,expectedWorkspaceRevision,level,permit);historySource(ref,permit);}
    /** Metadata becomes active; closed sessions still require explicit validated restore. */
    public void restoreSecondary(WorkspaceRef ref,long expectedWorkspaceRevision,ComponentGate.BusinessPermit permit){access(permit);workspaces.restoreSecondary(ref,expectedWorkspaceRevision,permit);historySource(ref,permit);}
    public WindowSnapshot windowSnapshot(ComponentGate.BusinessPermit permit){
        access(permit);
        // Reconcile observations that arrived synchronously during an engine mutation. These
        // are display values only; never reacquire currentDocument() to invent authority.
        for(Map.Entry<TabRef,GeckoSessionOwner.SessionRef> entry:live.entrySet()){if(sessions.state(entry.getValue())!=GeckoSessionOwner.State.OPEN_REQUESTED)continue;try{SessionPresentation.Frame frame=sessions.presentation(entry.getValue(),permit);if(!frame.retired())tabs.updateSessionReportedMetadata(entry.getKey(),frame.reportedTitle(),frame.reportedLocation(),permit);}catch(IllegalStateException unavailable){/* preserve original metadata/owner; a dead session must not strand all other tabs */}}
        WorkspacesView view=workspaces.snapshot(permit);TabsView selected=view.selected()==null?null:tabs.snapshot(view.selected(),permit);
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
        long observedLocation=sessions.presentation(ref,permit).reportedLocationSequence();
        TabCatalog.DocumentLease lease=tabs.beginDocument(selection.tab(),selection.tabsRevision(),permit);requested.put(selection.tab(),new RequestedNavigation(target,observedLocation));engineMutation=true;
        try{sessions.requireOpen(ref,permit).loadUri(target.url());return lease;}finally{engineMutation=false;}
    }
    /** Reload the exact selected existing native session, one attempt, no alternate route. */
    public void reloadSelected(Selection selection,ComponentGate.BusinessPermit permit){access(permit);tabs.requireSelection(selection,permit);GeckoSessionOwner.SessionRef ref=live.get(selection.tab());if(ref==null)throw new IllegalStateException("No current session");RequestedNavigation failed=requested.get(selection.tab());if(failed!=null && sessions.presentation(ref,permit).reportedLoadError()){navigateSelected(selection,failed.target,permit);return;}tabs.beginDocument(selection.tab(),selection.tabsRevision(),permit);engineMutation=true;try{sessions.requireOpen(ref,permit).reload();}finally{engineMutation=false;windowChanged();}}
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
        sessions.close(ref);live.remove(tab);requested.remove(tab);requiresRestore.add(tab);
    }
    public void shelfSecondary(WorkspaceRef workspace,long expectedWorkspaceRevision,ComponentGate.BusinessPermit permit){
        access(permit);WorkspaceView current=workspaces.workspace(workspace,permit);
        if(current.revision()!=expectedWorkspaceRevision || current.level()!=Level.SECONDARY || current.placement()!=Placement.ACTIVE)throw BrowserModels.invalid();
        engineMutation=true;
        try{for(TabView tab:tabs.snapshot(workspace,permit).tabs())closeOwnedSessionInternal(tab.ref());workspaces.shelfSecondary(workspace,expectedWorkspaceRevision,permit);historySource(workspace,permit);}
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
            if(history!=null)history.sourceChanged(new HistoryStore.Source(current.ref().id(),current.title(),current.level(),false));
        }finally{engineMutation=false;}
    }
    /** Capture actual open native catalog pages without loading/reopening anything. Used once
     * when the owner ends history pause, not a replay of the intermediate paused navigation. */
    public java.util.List<HistoryStore.Page> captureOpenHistoryPages(ComponentGate.BusinessPermit permit){
        access(permit);java.util.List<HistoryStore.Page> result=new java.util.ArrayList<>();
        for(WorkspaceView workspace:workspaces.snapshot(permit).active())for(TabView tab:tabs.snapshot(workspace.ref(),permit).tabs()){
            String url=tab.displayUrl(),title=tab.title();GeckoSessionOwner.SessionRef ref=live.get(tab.ref());
            if(ref!=null){SessionPresentation.Frame frame=sessions.presentation(ref,permit);if(!frame.retired()){if(frame.reportedLocation()!=null)url=frame.reportedLocation();if(frame.reportedTitle()!=null)title=frame.reportedTitle();}}
            if(url==null || "about:blank".equalsIgnoreCase(url))continue;
            try{result.add(new HistoryStore.Page(HistoryStore.Source.from(workspace),tab.ref().id(),BrowserModels.title(title,url),url));}catch(IllegalArgumentException unsupported){/* no blank/file/script URL history */}
        }
        access(permit);return java.util.Collections.unmodifiableList(result);
    }
    public WorkspacesView workspaces(ComponentGate.BusinessPermit permit){access(permit);return workspaces.snapshot(permit);}
    public TabsView tabs(WorkspaceRef workspace,ComponentGate.BusinessPermit permit){access(permit);return tabs.snapshot(workspace,permit);}
    private void access(ComponentGate.BusinessPermit permit){ownerThread();if(engineMutation)throw new IllegalStateException("Reentrant browser engine mutation forbidden");sourceLifetime.checkOrBind(permit);}
    private void ownerThread(){if(Thread.currentThread()!=thread)throw new IllegalStateException("Browser coordinator owner thread required");}
}
