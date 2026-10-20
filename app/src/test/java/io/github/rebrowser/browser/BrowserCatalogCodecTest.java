package io.github.rebrowser.browser;
import static org.junit.Assert.*;
import io.github.rebrowser.bootstrap.*;
import io.github.rebrowser.browser.BrowserModels.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
/** Actual source-bound metadata capture/format. Synthetic source, NO Android/Gecko/restore. */
public final class BrowserCatalogCodecTest {
 static final class F {
  final AtomicReference<ComponentGate.SourceView> source=new AtomicReference<>(ComponentGate.SourceView.acceptedNative(new Object(),new byte[16]));
  final ComponentGate gate=ComponentGate.forNativeSource(ProcessRole.MAIN,source::get);final ComponentGate.BusinessPermit permit=gate.requireBusiness();
  final WorkspaceCatalog workspaces=new WorkspaceCatalog(gate);final TabCatalog tabs=new TabCatalog(workspaces);
  WorkspaceRef add(Level level){WorkspaceRef w=workspaces.createTemporary(workspaces.snapshot(permit).revision(),permit);tabs.initializeWorkspace(w,permit);if(level!=Level.TEMPORARY)workspaces.changeLevel(w,workspaces.workspace(w,permit).revision(),level,permit);return w;}
  RetainedCapture capture(){return tabs.captureRetained(workspaces.snapshot(permit).revision(),permit);}
  byte[] bytes(){return BrowserCatalogCodec.encode(capture());}
 }
 static BrowserCatalogCodec.UnverifiedCatalog decode(byte[] b){return BrowserCatalogCodec.decodeUnverified(ByteBuffer.wrap(b));}
 @Test public void emptyCanonicalFrameHasNoGuessedWorkspace(){F f=new F();byte[] b=f.bytes();assertEquals(22,b.length);assertEquals(-1,decode(b).selectedWorkspaceIndex());assertEquals(0,decode(b).metadataRevision());}
 @Test public void temporaryWorkspaceNeverPersisted(){F f=new F();f.add(Level.TEMPORARY);assertEquals(0,decode(f.bytes()).workspaces().size());}
 @Test public void retainedNativeIdsAndDisplayMetadataRoundTrip(){F f=new F();WorkspaceRef w=f.add(Level.PRIMARY);TabRef tab=f.tabs.snapshot(w,f.permit).selected();TabCatalog.DocumentLease lease=f.tabs.currentDocument(tab,f.permit);f.tabs.updateMetadata(lease,"标题 😀","https://example.invalid/展示",f.permit);BrowserCatalogCodec.UnverifiedCatalog c=decode(f.bytes());assertEquals(w.id(),c.workspaces().get(0).id());assertEquals(tab.id(),c.workspaces().get(0).tabs().get(0).id());assertEquals("标题 😀",c.workspaces().get(0).tabs().get(0).title());assertEquals(1,c.tabCount());}
 @Test public void shelfPlacementAndSelectionRemainSeparate(){F f=new F();WorkspaceRef w=f.add(Level.SECONDARY);f.workspaces.shelfSecondary(w,f.workspaces.workspace(w,f.permit).revision(),f.permit);BrowserCatalogCodec.UnverifiedCatalog c=decode(f.bytes());assertEquals(Placement.SHELVED,c.workspaces().get(0).placement());assertEquals(-1,c.selectedWorkspaceIndex());}
 @Test public void selectedTemporaryDoesNotSelectSomeRetainedWorkspace(){F f=new F();f.add(Level.SECONDARY);f.add(Level.TEMPORARY);assertEquals(-1,decode(f.bytes()).selectedWorkspaceIndex());}
 @Test public void captureFrozenDespiteLaterLiveMutations(){F f=new F();WorkspaceRef w=f.add(Level.PRIMARY);RetainedCapture old=f.capture();f.workspaces.rename(w,f.workspaces.workspace(w,f.permit).revision(),"new",f.permit);assertEquals("临时总标签页",decode(BrowserCatalogCodec.encode(old)).workspaces().get(0).title());}
 @Test public void oldCatalogRevisionCannotCaptureNewRoster(){F f=new F();long before=f.workspaces.snapshot(f.permit).revision();f.add(Level.PRIMARY);assertThrows(IllegalStateException.class,()->f.tabs.captureRetained(before,f.permit));}
 @Test public void sourceLockRevokesCapturedSerialization(){F f=new F();RetainedCapture c=f.capture();f.source.set(ComponentGate.SourceView.untrusted(ComponentGate.SourceState.LOCKED));assertThrows(SecurityException.class,()->BrowserCatalogCodec.encode(c));}
 @Test public void newSameEpochIncarnationDoesNotRetargetOldCapture(){F f=new F();RetainedCapture c=f.capture();f.source.set(ComponentGate.SourceView.acceptedNative(new Object(),new byte[16]));ComponentGate.BusinessPermit next=f.gate.requireBusiness();assertThrows(SecurityException.class,()->c.requireBindings(f.gate,next));}
 @Test public void stateOnlyTrustedGateCannotCaptureAnEpoch(){ComponentGate g=new ComponentGate(ProcessRole.MAIN,()->ComponentGate.SourceState.TRUSTED);ComponentGate.BusinessPermit p=g.requireBusiness();assertThrows(SecurityException.class,()->new TabCatalog(new WorkspaceCatalog(g)).captureRetained(0,p));}
 @Test public void captureImmutableCollections(){F f=new F();f.add(Level.SECONDARY);RetainedCapture c=f.capture();assertThrows(UnsupportedOperationException.class,()->c.groups().clear());assertThrows(UnsupportedOperationException.class,()->decode(f.bytes()).workspaces().clear());}
 @Test public void noPopupAncestryOrDocumentAuthorityInWire(){F f=new F();WorkspaceRef w=f.add(Level.SECONDARY);TabRef parent=f.tabs.snapshot(w,f.permit).selected();f.tabs.addPopup(f.tabs.currentDocument(parent,f.permit),f.tabs.snapshot(w,f.permit).revision(),f.permit);BrowserCatalogCodec.UnverifiedCatalog c=decode(f.bytes());assertEquals(2,c.tabCount());for(java.lang.reflect.Method m:BrowserCatalogCodec.Tab.class.getMethods())assertFalse(m.getName().equals("popupParent"));}
 @Test public void exactTailAndEveryTruncationReject(){F f=new F();f.add(Level.PRIMARY);byte[] b=f.bytes();for(int n=0;n<b.length;n++){byte[] shortFrame=Arrays.copyOf(b,n);assertThrows(IllegalArgumentException.class,()->decode(shortFrame));}assertThrows(IllegalArgumentException.class,()->decode(Arrays.copyOf(b,b.length+1)));}
 @Test public void badMagicVersionAndWorkspaceCountReject(){F f=new F();byte[] b=f.bytes();for(int pos:new int[]{0,9,10}){byte[] bad=b.clone();bad[pos]=(byte)255;assertThrows(IllegalArgumentException.class,()->decode(bad));}}
 @Test public void negativeMetadataRevisionRejects(){F f=new F();byte[] b=f.bytes();ByteBuffer.wrap(b).putLong(12,-1);assertThrows(IllegalArgumentException.class,()->decode(b));}
 @Test public void unknownOrTemporaryStoredLevelRejects(){F f=new F();f.add(Level.PRIMARY);byte[] b=f.bytes();b[38]=1;assertThrows(IllegalArgumentException.class,()->decode(b));}
 @Test public void primaryCannotBeStoredShelved(){F f=new F();f.add(Level.PRIMARY);byte[] b=f.bytes();b[39]=2;assertThrows(IllegalArgumentException.class,()->decode(b));}
 @Test public void selectedWorkspaceIndexMustExist(){F f=new F();byte[] b=f.bytes();ByteBuffer.wrap(b).putShort(20,(short)0);assertThrows(IllegalArgumentException.class,()->decode(b));}
 @Test public void readOnlyInputPositionIsNotConsumed(){F f=new F();byte[] b=f.bytes();ByteBuffer in=ByteBuffer.wrap(b).asReadOnlyBuffer();decode(b);BrowserCatalogCodec.decodeUnverified(in);assertEquals(0,in.position());}
 @Test public void invalidUtf8NeverRepaired(){F f=new F();f.add(Level.PRIMARY);byte[] b=f.bytes();b[58]=(byte)0xc0;b[59]=(byte)0xaf;assertThrows(IllegalArgumentException.class,()->decode(b));}
 @Test public void outputBudgetCheckedBeforeHugeFinalAllocation(){F f=new F();String url=new String(new char[8192]).replace('\0','x');for(int i=0;i<12;i++){WorkspaceRef w=f.add(Level.SECONDARY);for(int j=1;j<50;j++)f.tabs.addTab(w,f.tabs.snapshot(w,f.permit).revision(),f.permit);for(TabView t:f.tabs.snapshot(w,f.permit).tabs())f.tabs.updateMetadata(f.tabs.currentDocument(t.ref(),f.permit),null,url,f.permit);}assertThrows(IllegalArgumentException.class,f::bytes);}
 @Test public void uninitializedRetainedGroupDoesNotInventTab(){F f=new F();WorkspaceRef w=f.workspaces.createTemporary(0,f.permit);f.workspaces.changeLevel(w,1,Level.SECONDARY,f.permit);assertThrows(IllegalArgumentException.class,f::capture);}
}
