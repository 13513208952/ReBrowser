package io.github.rebrowser.browser;

import static org.junit.Assert.*;
import io.github.rebrowser.bootstrap.ComponentGate;
import io.github.rebrowser.bootstrap.ProcessRole;
import io.github.rebrowser.browser.BrowserModels.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

/** Actual catalog classes only: no Android/Gecko/Profile/durability or site assertions. */
public final class BrowserCatalogTest {
    private static final class Fixture {
        final AtomicReference<ComponentGate.SourceState> source=new AtomicReference<>(ComponentGate.SourceState.TRUSTED);
        final ComponentGate gate=new ComponentGate(ProcessRole.MAIN,source::get);
        final ComponentGate.BusinessPermit permit=gate.requireBusiness();
        final WorkspaceCatalog workspaces=new WorkspaceCatalog(gate);
        final TabCatalog tabs=new TabCatalog(workspaces);
        WorkspaceRef create(){WorkspaceRef ref=workspaces.createTemporary(workspaces.snapshot(permit).revision(),permit);tabs.initializeWorkspace(ref,permit);return ref;}
        WorkspaceView view(WorkspaceRef w){return workspaces.workspace(w,permit);}
        TabsView tabs(WorkspaceRef w){return tabs.snapshot(w,permit);}
        void level(WorkspaceRef w,Level l){workspaces.changeLevel(w,view(w).revision(),l,permit);}
    }
    @Test public void firstTemporaryAndDefensiveSnapshotsAreNotPersistence(){
        Fixture f=new Fixture();WorkspaceRef w=f.create();WorkspacesView old=f.workspaces.snapshot(f.permit);
        assertSame(w,old.selected());assertEquals(Level.TEMPORARY,old.active().get(0).level());assertFalse(old.active().get(0).wantsRetention());assertEquals(1,f.tabs(w).tabs().size());
        assertThrows(UnsupportedOperationException.class,()->old.active().clear());assertThrows(UnsupportedOperationException.class,()->f.tabs(w).tabs().clear());
        f.workspaces.rename(w,f.view(w).revision(),"renamed",f.permit);assertEquals("临时总标签页",old.active().get(0).title());assertEquals("renamed",f.view(w).title());
    }
    @Test public void retentionTransitionsKeepIdentityAndFivePrimaryLimit(){
        Fixture f=new Fixture();WorkspaceRef first=f.create();f.level(first,Level.SECONDARY);f.level(first,Level.PRIMARY);assertSame(first,f.view(first).ref());assertTrue(f.view(first).wantsRetention());
        for(int i=1;i<5;i++)f.level(f.create(),Level.PRIMARY);
        WorkspaceRef extra=f.create();assertThrows(IllegalStateException.class,()->f.level(extra,Level.PRIMARY));assertEquals(Level.TEMPORARY,f.view(extra).level());
        assertThrows(IllegalStateException.class,()->f.level(first,Level.TEMPORARY));f.level(first,Level.SECONDARY);f.level(first,Level.TEMPORARY);assertFalse(f.view(first).wantsRetention());
    }
    @Test public void pinnedPrimaryCannotCloseAndActiveSecondaryMustShelf(){
        Fixture f=new Fixture();WorkspaceRef w=f.create();f.level(w,Level.PRIMARY);
        assertThrows(IllegalStateException.class,()->f.workspaces.remove(w,f.view(w).revision(),f.permit));f.level(w,Level.SECONDARY);
        assertThrows(IllegalStateException.class,()->f.workspaces.remove(w,f.view(w).revision(),f.permit));f.workspaces.shelfSecondary(w,f.view(w).revision(),f.permit);
        f.tabs.retireWorkspace(w,f.permit);f.workspaces.remove(w,f.view(w).revision(),f.permit);assertEquals(0,f.workspaces.snapshot(f.permit).shelved().size());
    }
    @Test public void shelfRestoreAndPrimaryFallbackSelection(){
        Fixture f=new Fixture();WorkspaceRef primary=f.create();f.level(primary,Level.PRIMARY);WorkspaceRef secondary=f.create();f.level(secondary,Level.SECONDARY);
        f.workspaces.shelfSecondary(secondary,f.view(secondary).revision(),f.permit);assertSame(primary,f.workspaces.snapshot(f.permit).selected());assertEquals(1,f.workspaces.snapshot(f.permit).shelved().size());
        f.workspaces.restoreSecondary(secondary,f.view(secondary).revision(),f.permit);assertSame(secondary,f.workspaces.snapshot(f.permit).selected());
    }
    @Test public void allShelvedStillCountsAgainstTotalAndDoesNotInventSixtyFifth(){
        Fixture f=new Fixture();for(int i=0;i<64;i++){WorkspaceRef w=f.create();f.level(w,Level.SECONDARY);f.workspaces.shelfSecondary(w,f.view(w).revision(),f.permit);}
        WorkspacesView view=f.workspaces.snapshot(f.permit);assertNull(view.selected());assertEquals(64,view.shelved().size());assertThrows(IllegalStateException.class,()->f.workspaces.createTemporary(view.revision(),f.permit));
    }
    @Test public void staleWorkspaceMutationsAndForeignHandlesReject(){
        Fixture f=new Fixture();WorkspaceRef w=f.create();long old=f.view(w).revision();f.workspaces.rename(w,old,"new",f.permit);
        assertThrows(IllegalStateException.class,()->f.workspaces.rename(w,old,"stale",f.permit));assertEquals("new",f.view(w).title());
        Fixture other=new Fixture();WorkspaceRef foreign=other.create();assertThrows(IllegalArgumentException.class,()->f.workspaces.workspace(foreign,f.permit));
        assertThrows(SecurityException.class,()->f.workspaces.snapshot(other.permit));
    }
    @Test public void sourceClosureRejectsReadsAndWritesWithoutErasingMetadata(){
        Fixture f=new Fixture();WorkspaceRef w=f.create();f.source.set(ComponentGate.SourceState.LOCKED);
        assertThrows(SecurityException.class,()->f.workspaces.snapshot(f.permit));assertThrows(SecurityException.class,()->f.tabs(w));
        f.source.set(ComponentGate.SourceState.TRUSTED);assertSame(w,f.view(w).ref());
    }
    @Test public void actualWrongThreadCannotMutateCatalog()throws Exception{
        Fixture f=new Fixture();WorkspaceRef w=f.create();ExecutorService pool=Executors.newSingleThreadExecutor();
        try{assertTrue(pool.submit(()->{try{f.tabs.currentDocument(f.tabs(w).selected(),f.permit);return false;}catch(IllegalStateException expected){return true;}}).get(2,TimeUnit.SECONDS));}finally{pool.shutdownNow();assertTrue(pool.awaitTermination(2,TimeUnit.SECONDS));}
        assertEquals(1,f.tabs(w).tabs().size());
    }
    @Test public void tabLimitAndStaleTabRevisionDoNotSilentlyCloseAnother(){
        Fixture f=new Fixture();WorkspaceRef w=f.create();long old=f.tabs(w).revision();f.tabs.addTab(w,old,f.permit);
        assertThrows(IllegalStateException.class,()->f.tabs.close(f.tabs(w).selected(),old,f.permit));
        while(f.tabs(w).tabs().size()<50)f.tabs.addTab(w,f.tabs(w).revision(),f.permit);
        assertThrows(IllegalStateException.class,()->f.tabs.addTab(w,f.tabs(w).revision(),f.permit));assertEquals(50,f.tabs(w).tabs().size());
    }
    @Test public void popupRequiresCurrentNativeParentAndExactWorkspaceSelection(){
        Fixture f=new Fixture();WorkspaceRef w=f.create();TabRef parent=f.tabs(w).selected();TabCatalog.DocumentLease lease=f.tabs.currentDocument(parent,f.permit);
        WorkspaceRef other=f.create();assertThrows(IllegalStateException.class,()->f.tabs.addPopup(lease,f.tabs(w).revision(),f.permit));
        f.workspaces.select(w,f.workspaces.snapshot(f.permit).revision(),f.permit);TabRef popup=f.tabs.addPopup(lease,f.tabs(w).revision(),f.permit);assertSame(parent,f.tabs(w).tabs().get(1).popupParent());
        assertThrows(IllegalStateException.class,()->f.tabs.addPopup(lease,f.tabs(w).revision(),f.permit));
        f.tabs.beginDocument(popup,f.tabs(w).revision(),f.permit);assertSame(parent,f.tabs(w).tabs().get(1).popupParent());
        f.tabs.close(popup,f.tabs(w).revision(),f.permit);assertSame(parent,f.tabs(w).selected());assertNotSame(other,w);
    }
    @Test public void closingNestedPopupReturnsOpenerAndDeletedParentsCannotLinger(){
        Fixture f=new Fixture();WorkspaceRef w=f.create();TabRef root=f.tabs(w).selected();TabRef popup=f.tabs.addPopup(f.tabs.currentDocument(root,f.permit),f.tabs(w).revision(),f.permit);
        TabRef child=f.tabs.addPopup(f.tabs.currentDocument(popup,f.permit),f.tabs(w).revision(),f.permit);f.tabs.close(child,f.tabs(w).revision(),f.permit);assertSame(popup,f.tabs(w).selected());
        f.tabs.close(root,f.tabs(w).revision(),f.permit);assertNull(f.tabs(w).tabs().get(0).popupParent());
    }
    @Test public void lastCloseReplacesHandleAndOldDocumentCannotUpdateReplacement(){
        Fixture f=new Fixture();WorkspaceRef w=f.create();TabRef old=f.tabs(w).selected();TabCatalog.DocumentLease lease=f.tabs.currentDocument(old,f.permit);
        TabCatalog.CloseResult closed=f.tabs.close(old,f.tabs(w).revision(),f.permit);assertTrue(closed.replacedLast());assertNotSame(old,closed.selected());assertEquals(1,f.tabs(w).tabs().size());
        assertFalse(f.tabs.updateMetadata(lease,"late","https://late.invalid/",f.permit));
    }
    @Test public void staleDocumentShelvedOrRemovedCannotWriteCurrentMetadata(){
        Fixture f=new Fixture();WorkspaceRef w=f.create();TabRef tab=f.tabs(w).selected();TabCatalog.DocumentLease old=f.tabs.currentDocument(tab,f.permit),current=f.tabs.beginDocument(tab,f.tabs(w).revision(),f.permit);
        assertFalse(f.tabs.updateMetadata(old,"late",null,f.permit));assertTrue(f.tabs.updateMetadata(current,"current","https://example.invalid/",f.permit));
        f.level(w,Level.SECONDARY);f.workspaces.shelfSecondary(w,f.view(w).revision(),f.permit);assertFalse(f.tabs.updateMetadata(current,"hidden",null,f.permit));
        f.tabs.retireWorkspace(w,f.permit);f.workspaces.remove(w,f.view(w).revision(),f.permit);assertFalse(f.tabs.updateMetadata(current,"removed",null,f.permit));
        Fixture foreign=new Fixture();WorkspaceRef fw=foreign.create();assertThrows(IllegalArgumentException.class,()->f.tabs.updateMetadata(foreign.tabs.currentDocument(foreign.tabs(fw).selected(),foreign.permit),"bad",null,f.permit));
    }
    @Test public void unicodeBoundsAndMetadataFailureAreAtomic(){
        Fixture f=new Fixture();WorkspaceRef w=f.create();StringBuilder emoji=new StringBuilder();for(int i=0;i<200;i++)emoji.append("\ud83d\ude03");f.workspaces.rename(w,f.view(w).revision(),emoji.toString(),f.permit);String title=f.view(w).title();assertEquals(160,title.codePointCount(0,title.length()));
        TabRef tab=f.tabs(w).selected();TabCatalog.DocumentLease lease=f.tabs.currentDocument(tab,f.permit);long before=f.tabs(w).revision();
        assertThrows(IllegalArgumentException.class,()->f.tabs.updateMetadata(lease,"new","https://bad.invalid/\n",f.permit));assertEquals(before,f.tabs(w).revision());assertEquals("新子标签页",f.tabs(w).tabs().get(0).title());
        assertThrows(IllegalArgumentException.class,()->f.workspaces.rename(w,f.view(w).revision(),"\ud800",f.permit));
    }
}
