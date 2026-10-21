package io.github.rebrowser.core;
import static org.junit.Assert.*;
import java.lang.reflect.*;
import java.util.concurrent.*;
import org.junit.Test;
/** Actual private native work owners/real owned threads; fixture's journal is synthetic, not SQLite. */
public class TaskCommittedRouteTest {
 static class F implements AutoCloseable {
  final Object fixture;final Method submit;
  F()throws Exception{Class<?> c=Class.forName("io.github.rebrowser.core.TaskCoordinatorTest$Fixture");Constructor<?> ctor=c.getDeclaredConstructor(int.class);ctor.setAccessible(true);fixture=ctor.newInstance(1);submit=c.getDeclaredMethod("submit",TaskCoordinator.Lane.class,TaskCoordinator.NativeBody.class);submit.setAccessible(true);}
  TaskCoordinator.Submission start(boolean commit)throws Exception{CountDownLatch ready=new CountDownLatch(1);TaskCoordinator.Submission s=(TaskCoordinator.Submission)submit.invoke(fixture,TaskCoordinator.Lane.READ,(TaskCoordinator.NativeBody)o->{if(commit)o.commitBeforeExternalDispatch();ready.countDown();});assertTrue(ready.await(3,TimeUnit.SECONDS));return s;}
  public void close()throws Exception{((AutoCloseable)fixture).close();}
 }
 @Test public void realPrivateOwnerCanClaimExactlyOneCommittedRoute()throws Exception{try(F f=new F()){TaskCoordinator.Submission s=f.start(true);Object route=new Object();s.nativeOwner().claimCommittedNativeRoute(route);assertThrows(SecurityException.class,()->s.nativeOwner().claimCommittedNativeRoute(route));assertThrows(SecurityException.class,()->s.nativeOwner().claimCommittedNativeRoute(new Object()));}}
 @Test public void routeCannotBypassDurableDispatchIntent()throws Exception{try(F f=new F()){TaskCoordinator.Submission s=f.start(false);assertThrows(SecurityException.class,()->s.nativeOwner().claimCommittedNativeRoute(new Object()));}}
 @Test public void cancellationFencesEvenAlreadyCommittedButUnhandedRoute()throws Exception{try(F f=new F()){TaskCoordinator.Submission s=f.start(true);s.client().requestCancellation();assertThrows(SecurityException.class,()->s.nativeOwner().claimCommittedNativeRoute(new Object()));}}
 @Test public void javaBodyReturnOrReportedOutcomeIsNotNativeChainEnd()throws Exception{try(F f=new F()){TaskCoordinator.Submission s=f.start(true);assertFalse(s.nativeOwner().hasReportedActualDomainEnd());s.nativeOwner().observeOutcome(TaskSnapshot.Effect.OBSERVED_SUCCESS);assertFalse(s.nativeOwner().hasReportedActualDomainEnd());s.nativeOwner().reportActualDomainChainEnd(Cancellation.EndReason.UNDERLYING_RETURNED);assertTrue(s.nativeOwner().hasReportedActualDomainEnd());assertThrows(SecurityException.class,()->s.nativeOwner().claimCommittedNativeRoute(new Object()));}}
 @Test public void nullRouteDoesNotBecomeAlternateDispatchPermission()throws Exception{try(F f=new F()){TaskCoordinator.Submission s=f.start(true);assertThrows(IllegalArgumentException.class,()->s.nativeOwner().claimCommittedNativeRoute(null));s.nativeOwner().claimCommittedNativeRoute(new Object());}}
}
