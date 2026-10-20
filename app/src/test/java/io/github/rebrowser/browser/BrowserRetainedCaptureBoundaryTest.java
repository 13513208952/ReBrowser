package io.github.rebrowser.browser;
import static org.junit.Assert.*;
import io.github.rebrowser.bootstrap.*;
import io.github.rebrowser.engine.PartitionCatalog;
import io.github.rebrowser.browser.BrowserModels.*;
import java.lang.reflect.*;
import java.util.*;
import org.junit.Test;
/** Private synthetic domain allocation; actual capture method/metadata contexts, NO SDK factory. */
public final class BrowserRetainedCaptureBoundaryTest {
 private static final class F {
  final BrowserCatalogCodecTest.F f=new BrowserCatalogCodecTest.F();final PartitionCatalog partitions=new PartitionCatalog(f.gate,new byte[16],64);final Map<WorkspaceRef,PartitionCatalog.Lease> contexts=new HashMap<>();final BrowserCoordinator domain;
  F()throws Exception{Class<?> c=Class.forName("sun.misc.Unsafe");Field singleton=c.getDeclaredField("theUnsafe");singleton.setAccessible(true);Object unsafe=singleton.get(null);domain=(BrowserCoordinator)c.getMethod("allocateInstance",Class.class).invoke(unsafe,BrowserCoordinator.class);put("thread",Thread.currentThread());put("gate",f.gate);put("sourceLifetime",f.gate.newResourcePin());put("workspaces",f.workspaces);put("tabs",f.tabs);put("partitions",partitions);put("contexts",contexts);}
  void put(String field,Object value)throws Exception{Field p=BrowserCoordinator.class.getDeclaredField(field);p.setAccessible(true);p.set(domain,value);}
  RetainedCapture capture(){return domain.captureRetained(f.workspaces.snapshot(f.permit).revision(),f.permit);}
 }
 @Test public void privateContextCannotBecomeRetainedCatalogByLevelMetadata()throws Exception{F f=new F();WorkspaceRef w=f.f.add(Level.SECONDARY);f.contexts.put(w,f.partitions.createPrivateBrowser(w,f.f.permit));assertThrows(SecurityException.class,f::capture);}
 @Test public void privateTemporaryWorkspaceIsExcludedNotPersisted()throws Exception{F f=new F();WorkspaceRef w=f.f.add(Level.TEMPORARY);f.contexts.put(w,f.partitions.createPrivateBrowser(w,f.f.permit));assertTrue(f.capture().groups().isEmpty());}
 @Test public void contextOwnerMismatchCannotRetargetAnotherWorkspace()throws Exception{F f=new F();WorkspaceRef w=f.f.add(Level.SECONDARY),other=f.f.add(Level.TEMPORARY);f.contexts.put(w,f.partitions.createPrivateBrowser(other,f.f.permit));assertThrows(IllegalArgumentException.class,f::capture);}
 @Test public void metadataWithoutAContextDoesNotClaimCookieRestoration()throws Exception{F f=new F();f.f.add(Level.SECONDARY);assertEquals(1,f.capture().groups().size());assertTrue(f.contexts.isEmpty());}
 @Test public void sourceLockDeniesCaptureWithoutDeletingCatalog()throws Exception{F f=new F();f.f.add(Level.PRIMARY);f.f.source.set(ComponentGate.SourceView.untrusted(ComponentGate.SourceState.LOCKED));assertThrows(SecurityException.class,()->f.domain.captureRetained(2,f.f.permit));}
}
