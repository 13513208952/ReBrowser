package io.github.rebrowser.core;

import static org.junit.Assert.*;
import io.github.rebrowser.bootstrap.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import org.junit.Test;

/** Actual owned HOST threads/workset with PRIVATE synthetic journal. No Android SQLite/effects. */
public final class TaskCoordinatorTest {
    private static final class Journal implements TaskCoordinator.JournalBackend {
        final AtomicInteger starts=new AtomicInteger(),dispatches=new AtomicInteger(),observations=new AtomicInteger(),ends=new AtomicInteger(),queuedEnds=new AtomicInteger();
        final List<String> order=Collections.synchronizedList(new ArrayList<>());
        final Set<String> startedJobs=ConcurrentHashMap.newKeySet();
        volatile boolean failStart,failDispatch,failEnd,skipStartChecks,skipDispatchChecks,skipEndChecks;
        volatile TaskCoordinator.CommitFence lastStartFence,lastDispatchFence;
        private void check(TaskCoordinator.CommitFence f,boolean skip){if(!skip)f.checkCurrent();}
        volatile CountDownLatch startEntered,startRelease,dispatchEntered,dispatchRelease,endEntered,endRelease;
        public Object start(String job,ComponentGate.BusinessPermit p,TaskCoordinator.CommitFence f){lastStartFence=f;check(f,skipStartChecks);starts.incrementAndGet();order.add("start");signal(startEntered);waitUninterruptibly(startRelease);check(f,skipStartChecks);if(!startedJobs.add(job))throw new IllegalStateException("synthetic original durable task no longer RESERVED");if(failStart)throw new IllegalStateException("synthetic start commit unknown");return new Object();}
        public void dispatch(Object t,ComponentGate.BusinessPermit p,TaskCoordinator.CommitFence f){lastDispatchFence=f;assertNotNull(t);check(f,skipDispatchChecks);dispatches.incrementAndGet();order.add("dispatch");signal(dispatchEntered);waitUninterruptibly(dispatchRelease);check(f,skipDispatchChecks);if(failDispatch)throw new IllegalStateException("synthetic dispatch commit unknown");}
        public void observe(Object t,TaskSnapshot.Effect effect,ComponentGate.BusinessPermit p,TaskCoordinator.CommitFence f){assertNotNull(t);f.checkCurrent();observations.incrementAndGet();order.add("observe");f.checkCurrent();}
        public void end(Object t,ComponentGate.BusinessPermit p,TaskCoordinator.CommitFence f){assertNotNull(t);check(f,skipEndChecks);ends.incrementAndGet();signal(endEntered);waitUninterruptibly(endRelease);check(f,skipEndChecks);if(failEnd)throw new IllegalStateException("synthetic end commit unknown");}
        public void endQueued(String job,ComponentGate.BusinessPermit p,TaskCoordinator.CommitFence f){check(f,skipEndChecks);check(f,skipEndChecks);queuedEnds.incrementAndGet();if(!startedJobs.add(job))throw new IllegalStateException("synthetic task no longer queued");if(failEnd)throw new IllegalStateException("synthetic queued end unknown");}
    }
    private static void signal(CountDownLatch latch){if(latch!=null)latch.countDown();}
    private static void waitUninterruptibly(CountDownLatch latch){if(latch==null)return;while(true)try{latch.await();return;}catch(InterruptedException ignored){}}
    private static void reached(CountDownLatch latch)throws InterruptedException{assertTrue("owned thread reached stage",latch.await(3,TimeUnit.SECONDS));}
    private static void until(BooleanSupplier ready)throws InterruptedException{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(!ready.getAsBoolean() && System.nanoTime()<end)Thread.sleep(1);assertTrue("bounded owned state reached",ready.getAsBoolean());}
    private static String job(int n){return "j_"+String.format(Locale.ROOT,"%032x",n);}
    private static final class Fixture implements AutoCloseable {
        final AtomicReference<ComponentGate.SourceState> state=new AtomicReference<>(ComponentGate.SourceState.TRUSTED);
        final ComponentGate gate=new ComponentGate(ProcessRole.MAIN,state::get);final Journal journal=new Journal();final AtomicBoolean current=new AtomicBoolean(true);final AtomicReference<Runnable> currentHook=new AtomicReference<>();
        final TaskCoordinator tasks;final List<TaskCoordinator.Submission> submitted=new ArrayList<>();int next;
        Fixture(int capacity)throws Exception{
            Constructor<TaskCoordinator> ctor=TaskCoordinator.class.getDeclaredConstructor(ComponentGate.class,TaskCoordinator.JournalBackend.class,TaskCoordinator.CurrentContext.class,int.class);assertTrue(Modifier.isPrivate(ctor.getModifiers()));ctor.setAccessible(true);tasks=ctor.newInstance(gate,journal,(TaskCoordinator.CurrentContext)j->{if(!current.get())throw new SecurityException("synthetic epoch/policy revoked");Runnable hook=currentHook.get();if(hook!=null)hook.run();},capacity);
        }
        TaskCoordinator.Submission submit(TaskCoordinator.Lane lane,TaskCoordinator.NativeBody body){TaskCoordinator.Submission s=tasks.submitNativeReserved(job(++next),lane,gate.requireBusiness(),body);submitted.add(s);return s;}
        @Override public void close()throws Exception{
            signal(journal.startRelease);signal(journal.dispatchRelease);signal(journal.endRelease);
            for(TaskCoordinator.Submission s:submitted)try{s.nativeOwner().reportActualDomainChainEnd(Cancellation.EndReason.UNDERLYING_FAILED);}catch(IllegalStateException notStarted){}
            tasks.close();assertTrue("only owned coordinator executors exit",tasks.awaitOwnedExecutorExit(4,TimeUnit.SECONDS));
        }
    }
    private static void settled(TaskCoordinator.Submission s)throws InterruptedException{until(()->s.client().snapshot().phase()==TaskCoordinator.Phase.SETTLED);}
    @Test public void concreteStartCommitPrecedesBodyAndDispatchAndEnd()throws Exception{
        try(Fixture f=new Fixture(2)){
            TaskCoordinator.Submission s=f.submit(TaskCoordinator.Lane.EFFECT,o->{f.journal.order.add("body");o.commitBeforeExternalDispatch();f.journal.order.add("handoff");o.observeOutcome(TaskSnapshot.Effect.OBSERVED_SUCCESS);o.reportActualDomainChainEnd(Cancellation.EndReason.UNDERLYING_RETURNED);});settled(s);assertEquals(Arrays.asList("start","body","dispatch","handoff","observe"),f.journal.order);assertEquals(1,f.journal.ends.get());assertEquals(TaskCoordinator.Persistence.COMMITTED,s.client().snapshot().journalFinalization());assertTrue(s.client().snapshot().cancellation().workEnded());
        }
    }
    @Test public void javaReturnDoesNotFreeAsyncNativeWorkOrLaneBudget()throws Exception{
        try(Fixture f=new Fixture(1)){
            CountDownLatch body=new CountDownLatch(1);TaskCoordinator.Submission s=f.submit(TaskCoordinator.Lane.READ,o->body.countDown());reached(body);until(()->s.client().snapshot().javaInvocationReturned());assertFalse(s.client().snapshot().cancellation().workEnded());assertEquals(1,f.tasks.registeredOutstanding());assertThrows(RejectedExecutionException.class,()->f.submit(TaskCoordinator.Lane.READ,o->{}));s.nativeOwner().reportActualDomainChainEnd(Cancellation.EndReason.UNDERLYING_RETURNED);settled(s);assertEquals(0,f.tasks.registeredOutstanding());
        }
    }
    @Test public void nativeEndBeforeInterruptIgnoringJavaReturnStillHoldsCredit()throws Exception{
        try(Fixture f=new Fixture(1)){
            CountDownLatch body=new CountDownLatch(1),release=new CountDownLatch(1);AtomicReference<Thread> thread=new AtomicReference<>();TaskCoordinator.Submission s=f.submit(TaskCoordinator.Lane.EFFECT,o->{thread.set(Thread.currentThread());body.countDown();waitUninterruptibly(release);});
            try{reached(body);s.client().requestCancellation();thread.get().interrupt();assertTrue(s.nativeOwner().reportActualDomainChainEnd(Cancellation.EndReason.STOP_CONFIRMED));assertFalse(s.client().snapshot().javaInvocationReturned());assertFalse(s.client().snapshot().cancellation().workEnded());assertEquals(1,f.tasks.registeredOutstanding());}finally{release.countDown();}settled(s);
        }
    }
    @Test public void journalFinalizerMustActuallyReturnBeforeMemoryEndAndSlotRelease()throws Exception{
        try(Fixture f=new Fixture(1)){
            f.journal.endEntered=new CountDownLatch(1);f.journal.endRelease=new CountDownLatch(1);TaskCoordinator.Submission s=f.submit(TaskCoordinator.Lane.READ,o->o.reportActualDomainChainEnd(Cancellation.EndReason.UNDERLYING_RETURNED));
            try{reached(f.journal.endEntered);assertTrue(s.client().snapshot().javaInvocationReturned());assertTrue(s.client().snapshot().nativeDomainChainEnded());assertFalse(s.client().snapshot().cancellation().workEnded());assertThrows(RejectedExecutionException.class,()->f.submit(TaskCoordinator.Lane.READ,o->{}));}finally{f.journal.endRelease.countDown();}settled(s);
        }
    }
    @Test public void sourceLockCannotPreventOriginalOwnerMemorySettlement()throws Exception{
        try(Fixture f=new Fixture(1)){
            CountDownLatch body=new CountDownLatch(1);TaskCoordinator.Submission s=f.submit(TaskCoordinator.Lane.EFFECT,o->body.countDown());reached(body);until(()->s.client().snapshot().javaInvocationReturned());f.state.set(ComponentGate.SourceState.LOCKED);s.nativeOwner().reportActualDomainChainEnd(Cancellation.EndReason.UNDERLYING_RETURNED);settled(s);assertEquals(TaskCoordinator.Persistence.BLOCKED_OR_UNKNOWN,s.client().snapshot().journalFinalization());assertEquals(0,f.journal.ends.get());assertTrue(s.client().snapshot().cancellation().workEnded());assertFalse(s.nativeOwner().mayPublishCurrentResult());
        }
    }
    @Test public void endCommitUnknownStillEndsMemoryButNeverClaimsCommittedPersistence()throws Exception{
        try(Fixture f=new Fixture(1)){
            f.journal.failEnd=true;TaskCoordinator.Submission s=f.submit(TaskCoordinator.Lane.READ,o->o.reportActualDomainChainEnd(Cancellation.EndReason.UNDERLYING_FAILED));settled(s);assertEquals(TaskCoordinator.Persistence.BLOCKED_OR_UNKNOWN,s.client().snapshot().journalFinalization());assertTrue(s.client().snapshot().cancellation().workEnded());assertEquals(1,f.journal.ends.get());
        }
    }
    @Test public void bodyExceptionRetainsUnknownUntilOriginalNativeOwnerReportsEnd()throws Exception{
        try(Fixture f=new Fixture(1)){
            TaskCoordinator.Submission s=f.submit(TaskCoordinator.Lane.EFFECT,o->{throw new IllegalStateException("synthetic route unknown");});until(()->s.client().snapshot().javaInvocationReturned());assertTrue(s.client().snapshot().routeOrLifecycleOutcomeUnknown());assertFalse(s.client().snapshot().cancellation().workEnded());assertEquals(1,f.tasks.registeredOutstanding());s.nativeOwner().reportActualDomainChainEnd(Cancellation.EndReason.UNDERLYING_FAILED);settled(s);
        }
    }
    @Test public void startCommitUnknownNeverEntersBodyOrAutomaticallyRetries()throws Exception{
        try(Fixture f=new Fixture(1)){
            f.journal.failStart=true;AtomicInteger body=new AtomicInteger();TaskCoordinator.Submission s=f.submit(TaskCoordinator.Lane.READ,o->body.incrementAndGet());settled(s);assertEquals(0,body.get());assertEquals(1,f.journal.starts.get());assertEquals(0,f.journal.dispatches.get());assertTrue(s.client().snapshot().routeOrLifecycleOutcomeUnknown());assertEquals(Cancellation.EndReason.UNDERLYING_FAILED,s.client().snapshot().cancellation().endReason());
        }
    }
    @Test public void cancelQueuedJobNeverStartsPreparatoryBodyAndEndsReservation()throws Exception{
        try(Fixture f=new Fixture(2)){
            CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);TaskCoordinator.Submission first=f.submit(TaskCoordinator.Lane.READ,o->{entered.countDown();waitUninterruptibly(release);o.reportActualDomainChainEnd(Cancellation.EndReason.UNDERLYING_RETURNED);});reached(entered);AtomicInteger body=new AtomicInteger();TaskCoordinator.Submission queued=f.submit(TaskCoordinator.Lane.READ,o->body.incrementAndGet());
            try{queued.client().requestCancellation();assertFalse(queued.client().snapshot().cancellation().workEnded());assertThrows(IllegalStateException.class,()->queued.nativeOwner().reportActualDomainChainEnd(Cancellation.EndReason.STOP_CONFIRMED));}finally{release.countDown();}settled(first);settled(queued);assertEquals(0,body.get());assertEquals(1,f.journal.starts.get());assertEquals(1,f.journal.queuedEnds.get());
        }
    }
    @Test public void distinctLanesProceedWhileEffectThreadIsBlocked()throws Exception{
        try(Fixture f=new Fixture(1)){
            CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);TaskCoordinator.Submission effect=f.submit(TaskCoordinator.Lane.EFFECT,o->{entered.countDown();waitUninterruptibly(release);o.reportActualDomainChainEnd(Cancellation.EndReason.UNDERLYING_RETURNED);});
            try{reached(entered);TaskCoordinator.Submission read=f.submit(TaskCoordinator.Lane.READ,o->o.reportActualDomainChainEnd(Cancellation.EndReason.UNDERLYING_RETURNED));TaskCoordinator.Submission auth=f.submit(TaskCoordinator.Lane.AUTH,o->o.reportActualDomainChainEnd(Cancellation.EndReason.UNDERLYING_RETURNED));settled(read);settled(auth);assertFalse(effect.client().snapshot().cancellation().workEnded());}finally{release.countDown();}settled(effect);
        }
    }
    @Test public void reservedSafetyStopRunsWithoutEffectLaneCreditAndDoesNotEndDomain()throws Exception{
        try(Fixture f=new Fixture(1)){
            CountDownLatch installed=new CountDownLatch(1),stopped=new CountDownLatch(1);TaskCoordinator.Submission s=f.submit(TaskCoordinator.Lane.EFFECT,o->{o.installOwnedStopper(stopped::countDown);installed.countDown();});reached(installed);until(()->s.client().snapshot().javaInvocationReturned());s.client().requestCancellation();reached(stopped);assertFalse(s.client().snapshot().cancellation().workEnded());assertEquals(1,f.tasks.registeredOutstanding());s.nativeOwner().reportActualDomainChainEnd(Cancellation.EndReason.STOP_CONFIRMED);settled(s);
        }
    }
    @Test public void runningOwnedStopMustReturnBeforeFinalization()throws Exception{
        try(Fixture f=new Fixture(1)){
            CountDownLatch installed=new CountDownLatch(1),stopping=new CountDownLatch(1),release=new CountDownLatch(1);TaskCoordinator.Submission s=f.submit(TaskCoordinator.Lane.READ,o->{o.installOwnedStopper(()->{stopping.countDown();waitUninterruptibly(release);});installed.countDown();});reached(installed);until(()->s.client().snapshot().javaInvocationReturned());s.client().requestCancellation();
            try{reached(stopping);s.nativeOwner().reportActualDomainChainEnd(Cancellation.EndReason.STOP_CONFIRMED);assertFalse(s.client().snapshot().ownedStopReturned());assertEquals(0,f.journal.ends.get());assertFalse(s.client().snapshot().cancellation().workEnded());}finally{release.countDown();}settled(s);
        }
    }
    @Test public void cancellationBeforeStopperRegistrationStillDispatchesOneOwnedStop()throws Exception{
        try(Fixture f=new Fixture(1)){
            CountDownLatch entered=new CountDownLatch(1),register=new CountDownLatch(1),stopped=new CountDownLatch(1);AtomicInteger calls=new AtomicInteger();TaskCoordinator.Submission s=f.submit(TaskCoordinator.Lane.READ,o->{entered.countDown();waitUninterruptibly(register);o.installOwnedStopper(()->{calls.incrementAndGet();stopped.countDown();});});reached(entered);s.client().requestCancellation();register.countDown();reached(stopped);s.client().requestCancellation();assertEquals(1,calls.get());until(()->s.client().snapshot().javaInvocationReturned());s.nativeOwner().reportActualDomainChainEnd(Cancellation.EndReason.STOP_CONFIRMED);settled(s);
        }
    }
    @Test public void stopExceptionNeverInventsNativeDomainEnd()throws Exception{
        try(Fixture f=new Fixture(1)){
            CountDownLatch installed=new CountDownLatch(1);TaskCoordinator.Submission s=f.submit(TaskCoordinator.Lane.READ,o->{o.installOwnedStopper(()->{throw new IllegalStateException("synthetic stop outcome unknown");});installed.countDown();});reached(installed);s.client().requestCancellation();until(()->s.client().snapshot().ownedStopReturned() && s.client().snapshot().routeOrLifecycleOutcomeUnknown());assertFalse(s.client().snapshot().cancellation().workEnded());s.nativeOwner().reportActualDomainChainEnd(Cancellation.EndReason.UNDERLYING_RETURNED);settled(s);
        }
    }
    @Test public void fenceStopsNewJobsAndRetainsOldUnknownWorkNotGlobalQuiescence()throws Exception{
        try(Fixture f=new Fixture(1)){
            CountDownLatch body=new CountDownLatch(1);TaskCoordinator.Submission s=f.submit(TaskCoordinator.Lane.EFFECT,o->body.countDown());reached(body);until(()->s.client().snapshot().javaInvocationReturned());TaskCoordinator.RegisteredSetFence fence=f.tasks.fenceToOwnedSafetyOnly();assertFalse(f.tasks.isThisRegisteredSetSettled(fence));assertTrue(s.client().snapshot().cancellation().cancellationRequested());assertFalse(s.nativeOwner().mayPublishCurrentResult());assertThrows(IllegalStateException.class,()->f.submit(TaskCoordinator.Lane.READ,o->{}));s.nativeOwner().reportActualDomainChainEnd(Cancellation.EndReason.UNDERLYING_RETURNED);settled(s);assertTrue(f.tasks.isThisRegisteredSetSettled(fence));
        }
    }
    @Test public void fenceDuringStartJournalIoPreventsDomainBodyEntry()throws Exception{
        try(Fixture f=new Fixture(1)){
            f.journal.startEntered=new CountDownLatch(1);f.journal.startRelease=new CountDownLatch(1);AtomicInteger body=new AtomicInteger();TaskCoordinator.Submission s=f.submit(TaskCoordinator.Lane.EFFECT,o->body.incrementAndGet());reached(f.journal.startEntered);TaskCoordinator.RegisteredSetFence fence=f.tasks.fenceToOwnedSafetyOnly();f.journal.startRelease.countDown();settled(s);assertEquals(0,body.get());assertTrue(f.tasks.isThisRegisteredSetSettled(fence));
        }
    }
    @Test public void dispatchCommitRechecksCurrentPolicyAfterBlockingNativeIo()throws Exception{
        try(Fixture f=new Fixture(1)){
            f.journal.dispatchEntered=new CountDownLatch(1);f.journal.dispatchRelease=new CountDownLatch(1);AtomicInteger handoff=new AtomicInteger();TaskCoordinator.Submission s=f.submit(TaskCoordinator.Lane.EFFECT,o->{try{o.commitBeforeExternalDispatch();handoff.incrementAndGet();}finally{o.reportActualDomainChainEnd(Cancellation.EndReason.UNDERLYING_FAILED);}});reached(f.journal.dispatchEntered);f.current.set(false);f.journal.dispatchRelease.countDown();settled(s);assertEquals(0,handoff.get());assertEquals(1,f.journal.dispatches.get());assertEquals(TaskCoordinator.Persistence.BLOCKED_OR_UNKNOWN,s.client().snapshot().journalFinalization());
        }
    }
    @Test public void unknownDispatchCommitIsSpentAndCannotUseAlternateRouteOrObservation()throws Exception{
        try(Fixture f=new Fixture(1)){
            f.journal.failDispatch=true;TaskCoordinator.Submission s=f.submit(TaskCoordinator.Lane.EFFECT,o->{assertThrows(IllegalStateException.class,o::commitBeforeExternalDispatch);assertThrows(IllegalStateException.class,o::commitBeforeExternalDispatch);assertThrows(IllegalStateException.class,()->o.observeOutcome(TaskSnapshot.Effect.OBSERVED_SUCCESS));o.reportActualDomainChainEnd(Cancellation.EndReason.UNDERLYING_FAILED);});settled(s);assertEquals(1,f.journal.dispatches.get());assertEquals(0,f.journal.observations.get());
        }
    }
    @Test public void cancellationDuringIntentIoPreventsExternalHandoffAfterReturn()throws Exception{
        try(Fixture f=new Fixture(1)){
            f.journal.dispatchEntered=new CountDownLatch(1);f.journal.dispatchRelease=new CountDownLatch(1);AtomicInteger handoff=new AtomicInteger();TaskCoordinator.Submission s=f.submit(TaskCoordinator.Lane.EFFECT,o->{try{o.commitBeforeExternalDispatch();handoff.incrementAndGet();}finally{o.reportActualDomainChainEnd(Cancellation.EndReason.UNDERLYING_FAILED);}});reached(f.journal.dispatchEntered);s.client().requestCancellation();f.journal.dispatchRelease.countDown();settled(s);assertEquals(0,handoff.get());assertEquals(1,f.journal.dispatches.get());
        }
    }
    @Test public void completedOriginalJobCannotRunAgainAfterMemoryEntryRemoval()throws Exception{
        try(Fixture f=new Fixture(1)){
            TaskCoordinator.Submission original=f.submit(TaskCoordinator.Lane.READ,o->o.reportActualDomainChainEnd(Cancellation.EndReason.UNDERLYING_RETURNED));settled(original);AtomicInteger replay=new AtomicInteger();TaskCoordinator.Submission retry=f.tasks.submitNativeReserved(original.client().jobRef(),TaskCoordinator.Lane.READ,f.gate.requireBusiness(),o->replay.incrementAndGet());f.submitted.add(retry);settled(retry);assertEquals(0,replay.get());assertEquals(TaskCoordinator.Persistence.BLOCKED_OR_UNKNOWN,retry.client().snapshot().journalFinalization());assertTrue(retry.client().snapshot().routeOrLifecycleOutcomeUnknown());
        }
    }
    @Test public void observedSuccessIsNotActualNativeChainEnd()throws Exception{
        try(Fixture f=new Fixture(1)){
            CountDownLatch observed=new CountDownLatch(1);TaskCoordinator.Submission s=f.submit(TaskCoordinator.Lane.EFFECT,o->{o.commitBeforeExternalDispatch();o.observeOutcome(TaskSnapshot.Effect.OBSERVED_SUCCESS);observed.countDown();});reached(observed);until(()->s.client().snapshot().javaInvocationReturned());assertFalse(s.client().snapshot().cancellation().workEnded());s.nativeOwner().reportActualDomainChainEnd(Cancellation.EndReason.UNDERLYING_RETURNED);settled(s);
        }
    }
    @Test public void inFlightNativeDispatchCallMustReturnBeforeOwnerEndCanFinalize()throws Exception{
        try(Fixture f=new Fixture(1)){
            CountDownLatch body=new CountDownLatch(1);TaskCoordinator.Submission s=f.submit(TaskCoordinator.Lane.EFFECT,o->body.countDown());reached(body);until(()->s.client().snapshot().javaInvocationReturned());f.journal.dispatchEntered=new CountDownLatch(1);f.journal.dispatchRelease=new CountDownLatch(1);AtomicBoolean handoff=new AtomicBoolean(),blocked=new AtomicBoolean();Thread nativeCallback=new Thread(()->{try{s.nativeOwner().commitBeforeExternalDispatch();handoff.set(true);}catch(SecurityException ended){blocked.set(true);}},"native-dispatch-fixture");nativeCallback.start();
            try{reached(f.journal.dispatchEntered);s.nativeOwner().reportActualDomainChainEnd(Cancellation.EndReason.UNDERLYING_RETURNED);assertFalse(s.client().snapshot().cancellation().workEnded());assertEquals(0,f.journal.ends.get());}finally{f.journal.dispatchRelease.countDown();nativeCallback.join(3000);}assertFalse(nativeCallback.isAlive());assertFalse(handoff.get());assertTrue(blocked.get());settled(s);
        }
    }
    @Test public void duplicateLiveJobCannotReplaceOwnerOrRunSecondBody()throws Exception{
        try(Fixture f=new Fixture(1)){
            CountDownLatch body=new CountDownLatch(1);TaskCoordinator.Submission s=f.submit(TaskCoordinator.Lane.READ,o->body.countDown());reached(body);assertThrows(IllegalStateException.class,()->f.tasks.submitNativeReserved(s.client().jobRef(),TaskCoordinator.Lane.EFFECT,f.gate.requireBusiness(),o->{}));s.nativeOwner().reportActualDomainChainEnd(Cancellation.EndReason.UNDERLYING_RETURNED);settled(s);assertEquals(1,f.journal.starts.get());
        }
    }
    @Test public void foreignFenceAndInvalidOrPositiveSafetySubmissionsReject()throws Exception{
        try(Fixture f=new Fixture(1);Fixture other=new Fixture(1)){
            TaskCoordinator.RegisteredSetFence foreign=other.tasks.fenceToOwnedSafetyOnly();assertThrows(IllegalArgumentException.class,()->f.tasks.isThisRegisteredSetSettled(foreign));assertThrows(IllegalArgumentException.class,()->f.tasks.submitNativeReserved(job(9),TaskCoordinator.Lane.SAFETY,f.gate.requireBusiness(),o->{}));assertThrows(IllegalArgumentException.class,()->f.tasks.submitNativeReserved("untrusted-path",TaskCoordinator.Lane.READ,f.gate.requireBusiness(),o->{}));assertEquals(0,f.journal.starts.get());
        }
    }
    @Test public void lockedSourceOrForeignPermitCannotRegisterAnyPositiveWork()throws Exception{
        try(Fixture f=new Fixture(1);Fixture other=new Fixture(1)){
            assertThrows(SecurityException.class,()->f.tasks.submitNativeReserved(job(8),TaskCoordinator.Lane.READ,other.gate.requireBusiness(),o->{}));ComponentGate.BusinessPermit permit=f.gate.requireBusiness();f.state.set(ComponentGate.SourceState.LOCKED);assertThrows(SecurityException.class,()->f.tasks.submitNativeReserved(job(9),TaskCoordinator.Lane.AUTH,permit,o->{}));assertEquals(0,f.tasks.registeredOutstanding());assertEquals(0,f.journal.starts.get());
        }
    }
    @Test public void firstActualNativeEndWinsAndClientHasNoCompletionCapability()throws Exception{
        try(Fixture f=new Fixture(1)){
            CountDownLatch body=new CountDownLatch(1);TaskCoordinator.Submission s=f.submit(TaskCoordinator.Lane.READ,o->body.countDown());reached(body);assertTrue(s.nativeOwner().reportActualDomainChainEnd(Cancellation.EndReason.UNDERLYING_RETURNED));assertFalse(s.nativeOwner().reportActualDomainChainEnd(Cancellation.EndReason.STOP_CONFIRMED));settled(s);assertEquals(Cancellation.EndReason.UNDERLYING_RETURNED,s.client().snapshot().cancellation().endReason());assertEquals(Cancellation.RequestOutcome.ALREADY_ENDED,s.client().requestCancellation());for(Method method:TaskCoordinator.Client.class.getMethods())assertFalse(method.getName().contains("reportActual"));
        }
    }
    @Test public void closeNeverKillsOrPretendsBlockedOwnedWorkerEnded()throws Exception{
        try(Fixture f=new Fixture(1)){
            CountDownLatch body=new CountDownLatch(1),release=new CountDownLatch(1);TaskCoordinator.Submission s=f.submit(TaskCoordinator.Lane.READ,o->{body.countDown();waitUninterruptibly(release);o.reportActualDomainChainEnd(Cancellation.EndReason.UNDERLYING_RETURNED);});
            try{reached(body);f.tasks.close();assertFalse(f.tasks.awaitOwnedExecutorExit(1,TimeUnit.MILLISECONDS));assertFalse(s.client().snapshot().cancellation().workEnded());}finally{release.countDown();}settled(s);assertTrue(f.tasks.awaitOwnedExecutorExit(3,TimeUnit.SECONDS));
        }
    }
    @Test public void ownedWorkerCannotDeadlockAwaitingItsOwnExecutorExit()throws Exception{
        try(Fixture f=new Fixture(1)){
            TaskCoordinator.Submission s=f.submit(TaskCoordinator.Lane.READ,o->{assertThrows(IllegalStateException.class,()->f.tasks.awaitOwnedExecutorExit(2,TimeUnit.SECONDS));o.reportActualDomainChainEnd(Cancellation.EndReason.UNDERLYING_RETURNED);});settled(s);
        }
    }
    @Test public void revokedStartFenceAfterSyntheticWriterWaitPreventsPreparationAndCommit()throws Exception{
        try(Fixture f=new Fixture(1)){
            f.journal.startEntered=new CountDownLatch(1);f.journal.startRelease=new CountDownLatch(1);AtomicInteger bodies=new AtomicInteger();TaskCoordinator.Submission s=f.submit(TaskCoordinator.Lane.READ,o->bodies.incrementAndGet());reached(f.journal.startEntered);f.current.set(false);f.journal.startRelease.countDown();settled(s);assertEquals(0,bodies.get());assertTrue(f.journal.startedJobs.isEmpty());assertEquals(TaskCoordinator.Persistence.BLOCKED_OR_UNKNOWN,s.client().snapshot().journalFinalization());
        }
    }
    @Test public void retiredFenceCannotBeReusedEvenByOriginalThreadInBody()throws Exception{
        try(Fixture f=new Fixture(1)){
            TaskCoordinator.Submission s=f.submit(TaskCoordinator.Lane.READ,o->{assertThrows(SecurityException.class,f.journal.lastStartFence::checkCurrent);o.reportActualDomainChainEnd(Cancellation.EndReason.UNDERLYING_RETURNED);});settled(s);assertThrows(SecurityException.class,f.journal.lastStartFence::checkCurrent);
        }
    }
    @Test public void activeFenceRejectsForeignThreadAndCannotEscapeDao()throws Exception{
        try(Fixture f=new Fixture(1)){
            f.journal.dispatchEntered=new CountDownLatch(1);f.journal.dispatchRelease=new CountDownLatch(1);TaskCoordinator.Submission s=f.submit(TaskCoordinator.Lane.EFFECT,o->{o.commitBeforeExternalDispatch();o.reportActualDomainChainEnd(Cancellation.EndReason.UNDERLYING_RETURNED);});reached(f.journal.dispatchEntered);try{assertThrows(SecurityException.class,f.journal.lastDispatchFence::checkCurrent);}finally{f.journal.dispatchRelease.countDown();}settled(s);assertThrows(SecurityException.class,f.journal.lastDispatchFence::checkCurrent);
        }
    }
    @Test public void backendSkippingStartFenceCannotEnterNativeBody()throws Exception{
        try(Fixture f=new Fixture(1)){
            f.journal.skipStartChecks=true;AtomicInteger body=new AtomicInteger();TaskCoordinator.Submission s=f.submit(TaskCoordinator.Lane.READ,o->body.incrementAndGet());settled(s);assertEquals(0,body.get());assertTrue(s.client().snapshot().routeOrLifecycleOutcomeUnknown());assertEquals(TaskCoordinator.Persistence.BLOCKED_OR_UNKNOWN,s.client().snapshot().journalFinalization());
        }
    }
    @Test public void skippedIntentFenceNeverHandsOffOrAllowsAlternateDispatch()throws Exception{
        try(Fixture f=new Fixture(1)){
            f.journal.skipDispatchChecks=true;AtomicInteger handoff=new AtomicInteger();TaskCoordinator.Submission s=f.submit(TaskCoordinator.Lane.EFFECT,o->{assertThrows(SecurityException.class,o::commitBeforeExternalDispatch);assertThrows(IllegalStateException.class,o::commitBeforeExternalDispatch);o.reportActualDomainChainEnd(Cancellation.EndReason.UNDERLYING_FAILED);});settled(s);assertEquals(0,handoff.get());assertEquals(1,f.journal.dispatches.get());
        }
    }
    @Test public void skippedSettlementFencesCannotClaimCommittedButStillSettleMemory()throws Exception{
        try(Fixture f=new Fixture(1)){
            f.journal.skipEndChecks=true;TaskCoordinator.Submission s=f.submit(TaskCoordinator.Lane.READ,o->o.reportActualDomainChainEnd(Cancellation.EndReason.UNDERLYING_RETURNED));settled(s);assertEquals(TaskCoordinator.Persistence.BLOCKED_OR_UNKNOWN,s.client().snapshot().journalFinalization());assertTrue(s.client().snapshot().cancellation().workEnded());
        }
    }
    @Test public void reentrantNativeFenceIsRejectedWithoutRecursiveAuthorityOrBody()throws Exception{
        try(Fixture f=new Fixture(1)){
            f.currentHook.set(()->{TaskCoordinator.CommitFence active=f.journal.lastStartFence;if(active!=null)active.checkCurrent();});AtomicInteger body=new AtomicInteger();TaskCoordinator.Submission s=f.submit(TaskCoordinator.Lane.READ,o->body.incrementAndGet());settled(s);assertEquals(0,body.get());assertEquals(0,f.journal.starts.get());assertTrue(s.client().snapshot().routeOrLifecycleOutcomeUnknown());assertTrue(s.client().snapshot().cancellation().workEnded());
        }
    }
    @Test public void journalTopLevelDataOperationsRequireNativeFenceNotOnlySourcePermit(){
        Set<String> required=new HashSet<>(Arrays.asList("initialize","startWorkAfterAuthorization","commitDispatchIntentAfterAuthorization","observe","markOutcomeUnknown","actualWorkEnded","endQueuedWithoutStartingAfterAuthorization","reconcileLostProcess","queryAfterAuthorization"));
        for(Method method:TaskJournal.class.getDeclaredMethods())if(Modifier.isPublic(method.getModifiers()) && required.contains(method.getName())){assertEquals(TaskJournal.NativeFence.class,method.getParameterTypes()[method.getParameterCount()-1]);required.remove(method.getName());}assertTrue(required.isEmpty());
        for(Constructor<?> constructor:TaskCoordinator.CommitFence.class.getDeclaredConstructors())assertFalse(Modifier.isPublic(constructor.getModifiers()));
    }
    @Test public void oldCoordinatorCannotAcceptFreshPermitAfterSourceReturnsTrusted()throws Exception{
        try(Fixture f=new Fixture(2)){
            CountDownLatch body=new CountDownLatch(1);TaskCoordinator.Submission original=f.submit(TaskCoordinator.Lane.READ,o->body.countDown());reached(body);until(()->original.client().snapshot().javaInvocationReturned());f.state.set(ComponentGate.SourceState.LOCKED);f.gate.sourceState();f.state.set(ComponentGate.SourceState.TRUSTED);ComponentGate.BusinessPermit fresh=f.gate.requireBusiness();assertThrows(SecurityException.class,()->f.tasks.submitNativeReserved(job(99),TaskCoordinator.Lane.AUTH,fresh,o->{}));assertFalse(original.nativeOwner().mayPublishCurrentResult());original.nativeOwner().reportActualDomainChainEnd(Cancellation.EndReason.UNDERLYING_RETURNED);settled(original);assertTrue(original.client().snapshot().cancellation().workEnded());assertEquals(TaskCoordinator.Persistence.BLOCKED_OR_UNKNOWN,original.client().snapshot().journalFinalization());assertEquals(0,f.journal.ends.get());
        }
    }
    @Test public void databaseOwnersRequireNativeOpeningPermitNotOnlyRawHandle(){
        for(Class<?> owner:new Class<?>[]{TaskJournal.class,io.github.rebrowser.control.IdempotencyStore.class})for(Constructor<?> constructor:owner.getConstructors())assertEquals(ComponentGate.BusinessPermit.class,constructor.getParameterTypes()[constructor.getParameterCount()-1]);
    }
    @Test public void startupOwnerCannotBeConstructedByCallerSuppliedMainRole(){
        assertEquals(0,io.github.rebrowser.bootstrap.StartupCoordinator.class.getConstructors().length);
    }
    @Test public void publicConstructorOnlyAcceptsRealNativeJournalNotFakeBackend(){
        for(Constructor<?> constructor:TaskCoordinator.class.getConstructors())for(Class<?> parameter:constructor.getParameterTypes())assertNotEquals(TaskCoordinator.JournalBackend.class,parameter);
    }
}
