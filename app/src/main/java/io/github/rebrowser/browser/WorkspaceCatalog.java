package io.github.rebrowser.browser;

import io.github.rebrowser.bootstrap.ComponentGate;
import io.github.rebrowser.browser.BrowserModels.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Serialized native workspace metadata, not Gecko/Profile storage or a persistence receipt.
 * Primary cannot close directly, secondary shelves, temporary removes. Promotion keeps the
 * same native identity (and hence the same separately bound partition), never copies cookies.
 * Native coordinator must fence/close actual sessions/tasks before logical removal/shelving.
 * This file neither deletes data nor releases actual work slots; BrowserStore is separate.
 */
public final class WorkspaceCatalog {
    public static final int MAX_WORKSPACES=64,MAX_PRIMARY=5;
    private final ComponentGate gate;
    private final ComponentGate.ResourcePin sourceLifetime;
    private final Thread thread=Thread.currentThread();
    private final Map<WorkspaceRef,Entry> entries=new LinkedHashMap<>();
    private WorkspaceRef selected;
    private long revision;
    private static final class Entry {
        final WorkspaceRef ref;
        String title="临时总标签页";
        Level level=Level.TEMPORARY;
        Placement placement=Placement.ACTIVE;
        long revision=1;
        Entry(WorkspaceRef ref){this.ref=ref;}
        WorkspaceView view(){return new WorkspaceView(ref,title,level,placement,revision);}
    }
    public WorkspaceCatalog(ComponentGate gate){if(gate==null)throw BrowserModels.invalid();this.gate=gate;sourceLifetime=gate.newResourcePin();}
    public WorkspaceRef createTemporary(long expectedCatalogRevision,ComponentGate.BusinessPermit permit){
        access(permit);if(expectedCatalogRevision!=revision)throw stale();if(entries.size()>=MAX_WORKSPACES)throw new IllegalStateException("Workspace limit reached");
        long next=BrowserModels.next(revision);WorkspaceRef ref=BrowserModels.workspaceRef(this);
        for(Entry e:entries.values())if(e.ref.id().equals(ref.id()))throw new IllegalStateException("Workspace identity collision");
        entries.put(ref,new Entry(ref));selected=ref;revision=next;return ref;
    }
    public void select(WorkspaceRef ref,long expectedCatalogRevision,ComponentGate.BusinessPermit permit){
        access(permit);if(expectedCatalogRevision!=revision)throw stale();Entry e=entry(ref);if(e.placement!=Placement.ACTIVE)throw BrowserModels.invalid();
        if(selected!=ref){long next=BrowserModels.next(revision);selected=ref;revision=next;}
    }
    public void rename(WorkspaceRef ref,long expectedWorkspaceRevision,String title,ComponentGate.BusinessPermit permit){
        access(permit);Entry e=current(ref,expectedWorkspaceRevision);String clean=BrowserModels.title(title,"总标签页");
        if(!clean.equals(e.title)){advance(e);e.title=clean;}
    }
    public void changeLevel(WorkspaceRef ref,long expectedWorkspaceRevision,Level level,ComponentGate.BusinessPermit permit){
        access(permit);Entry e=current(ref,expectedWorkspaceRevision);if(level==null || e.placement!=Placement.ACTIVE)throw BrowserModels.invalid();
        if(e.level==level)return;
        if(e.level==Level.PRIMARY && level!=Level.SECONDARY)throw new IllegalStateException("Demote primary to secondary first");
        if(level==Level.PRIMARY){int count=0;for(Entry candidate:entries.values())if(candidate.level==Level.PRIMARY)count++;if(count>=MAX_PRIMARY)throw new IllegalStateException("Primary workspace limit reached");}
        advance(e);e.level=level;
    }
    public void shelfSecondary(WorkspaceRef ref,long expectedWorkspaceRevision,ComponentGate.BusinessPermit permit){
        access(permit);Entry e=current(ref,expectedWorkspaceRevision);if(e.level!=Level.SECONDARY || e.placement!=Placement.ACTIVE)throw BrowserModels.invalid();
        advance(e);e.placement=Placement.SHELVED;if(selected==ref)selected=fallback();
    }
    public void restoreSecondary(WorkspaceRef ref,long expectedWorkspaceRevision,ComponentGate.BusinessPermit permit){
        access(permit);Entry e=current(ref,expectedWorkspaceRevision);if(e.level!=Level.SECONDARY || e.placement!=Placement.SHELVED)throw BrowserModels.invalid();
        advance(e);e.placement=Placement.ACTIVE;selected=ref;
    }
    /** Metadata only: source-native coordinator owns real close/retirement and old work settlement. */
    public WorkspaceView remove(WorkspaceRef ref,long expectedWorkspaceRevision,ComponentGate.BusinessPermit permit){
        access(permit);Entry e=current(ref,expectedWorkspaceRevision);
        if(e.level==Level.PRIMARY || (e.level==Level.SECONDARY && e.placement!=Placement.SHELVED))throw new IllegalStateException("Primary pins; active secondary must shelf first");
        long next=BrowserModels.next(revision);WorkspaceView removed=e.view();entries.remove(ref);if(selected==ref)selected=fallback();revision=next;return removed;
    }
    public WorkspaceView workspace(WorkspaceRef ref,ComponentGate.BusinessPermit permit){access(permit);return entry(ref).view();}
    public WorkspacesView snapshot(ComponentGate.BusinessPermit permit){
        access(permit);List<WorkspaceView> active=new ArrayList<>(),shelved=new ArrayList<>();
        for(Entry e:entries.values())(e.placement==Placement.ACTIVE?active:shelved).add(e.view());
        return new WorkspacesView(active,shelved,selected,revision);
    }
    /** Source-owned retained metadata capture; no proposed epoch or fresh-install inference. */
    ComponentGate.DataEpochPin captureDataEpoch(ComponentGate.BusinessPermit permit){access(permit);ComponentGate.DataEpochPin result=gate.captureDataEpoch(permit);access(permit);return result;}
    void requireCapturedRevision(long expected,ComponentGate.BusinessPermit permit){access(permit);if(revision!=expected)throw stale();}
    void requireGate(ComponentGate expected){if(gate!=expected || Thread.currentThread()!=thread)throw BrowserModels.invalid();}
    void requireActive(WorkspaceRef ref,ComponentGate.BusinessPermit permit){access(permit);if(entry(ref).placement!=Placement.ACTIVE)throw BrowserModels.invalid();}
    boolean registered(WorkspaceRef ref,ComponentGate.BusinessPermit permit){access(permit);if(ref==null || !ref.issuedBy(this))throw BrowserModels.invalid();return entries.containsKey(ref);}
    void requireSelected(WorkspaceRef ref,ComponentGate.BusinessPermit permit){requireActive(ref,permit);if(selected!=ref)throw stale();}
    void access(ComponentGate.BusinessPermit permit){if(Thread.currentThread()!=thread)throw new IllegalStateException("Browser owner thread required");sourceLifetime.checkOrBind(permit);}
    private Entry current(WorkspaceRef ref,long expected){Entry e=entry(ref);if(e.revision!=expected)throw stale();return e;}
    private Entry entry(WorkspaceRef ref){if(ref==null || !ref.issuedBy(this))throw BrowserModels.invalid();Entry e=entries.get(ref);if(e==null)throw BrowserModels.invalid();return e;}
    private void advance(Entry e){long global=BrowserModels.next(revision),local=BrowserModels.next(e.revision);revision=global;e.revision=local;}
    private WorkspaceRef fallback(){WorkspaceRef first=null;for(Entry e:entries.values())if(e.placement==Placement.ACTIVE){if(first==null)first=e.ref;if(e.level==Level.PRIMARY)return e.ref;}return first;}
    private static IllegalStateException stale(){return new IllegalStateException("Stale browser workspace revision/selection");}
}
