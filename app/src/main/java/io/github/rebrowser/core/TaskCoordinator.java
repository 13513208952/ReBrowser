package io.github.rebrowser.core;

import io.github.rebrowser.bootstrap.ComponentGate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Bounded PRE-ADMITTED native jobs/actual registered workset, not command authority.
 * Real TaskJournal start commits before native preparatory body; dispatch intent commits
 * before caller's external handoff. No route dispatch, automatic retry or client success.
 * Java body return != native chain end. All domain work, Java invocation, already-owned
 * stop requests and synchronous journal finalization must end before memory credit returns.
 * This registry alone never proves untracked Gecko/system/account work is quiescent.
 */
public final class TaskCoordinator implements AutoCloseable {
    public enum Lane { AUTH, EFFECT, READ, SAFETY }
    public enum Phase { QUEUED, STARTING, BODY_ENTERED, WAITING_NATIVE_END, FINALIZING, SETTLED }
    public enum Persistence { NOT_ATTEMPTED, COMMITTED, BLOCKED_OR_UNKNOWN }
    public interface CurrentContext { void checkCurrent(String nativeReservedJob); }
    public interface NativeBody { void run(Owner actualOwner)throws Exception; }
    public interface NativeStopper { void requestOwnedStop()throws Exception; }
    interface JournalBackend {
        Object start(String job,ComponentGate.BusinessPermit permit);
        void dispatch(Object ticket,ComponentGate.BusinessPermit permit);
        void observe(Object ticket,TaskSnapshot.Effect effect,ComponentGate.BusinessPermit permit);
        void end(Object ticket,ComponentGate.BusinessPermit permit);
        void endQueued(String job,ComponentGate.BusinessPermit permit);
    }
    private static final class NativeJournal implements JournalBackend {
        final TaskJournal journal;
        NativeJournal(TaskJournal journal){if(journal==null)throw invalid();this.journal=journal;}
        public Object start(String job,ComponentGate.BusinessPermit p){return journal.startWorkAfterAuthorization(job,p);}
        public void dispatch(Object t,ComponentGate.BusinessPermit p){journal.commitDispatchIntentAfterAuthorization((TaskJournal.ExecutionTicket)t,p);}
        public void observe(Object t,TaskSnapshot.Effect e,ComponentGate.BusinessPermit p){journal.observe((TaskJournal.ExecutionTicket)t,e,p);}
        public void end(Object t,ComponentGate.BusinessPermit p){journal.actualWorkEnded((TaskJournal.ExecutionTicket)t,p);}
        public void endQueued(String job,ComponentGate.BusinessPermit p){journal.endQueuedWithoutStartingAfterAuthorization(job,p);}
    }
    public static final class Snapshot {
        private final Phase phase;
        private final boolean invocationReturned,nativeEnd,stopReturned,unknown;
        private final Persistence persistence;
        private final Cancellation.Snapshot cancellation;
        private Snapshot(Entry e){phase=e.phase;invocationReturned=e.invocationReturned;nativeEnd=e.nativeEnd!=null;stopReturned=!e.stopScheduled || e.stopReturned;unknown=e.unknown;persistence=e.persistence;cancellation=e.cancellation.requester().snapshot();}
        public Phase phase(){return phase;}
        public boolean javaInvocationReturned(){return invocationReturned;}
        public boolean nativeDomainChainEnded(){return nativeEnd;}
        public boolean ownedStopReturned(){return stopReturned;}
        public boolean routeOrLifecycleOutcomeUnknown(){return unknown;}
        public Persistence journalFinalization(){return persistence;}
        public Cancellation.Snapshot cancellation(){return cancellation;}
    }
    /** Client gets only this view, never Owner/Reporter/TaskJournal ticket/NativeBody. */
    public static final class Client {
        private final TaskCoordinator issuer;private final Entry entry;
        private Client(TaskCoordinator issuer,Entry entry){this.issuer=issuer;this.entry=entry;}
        public String jobRef(){return entry.job;}
        public Cancellation.RequestOutcome requestCancellation(){return issuer.cancel(entry);}
        public Snapshot snapshot(){synchronized(issuer.monitor){return new Snapshot(entry);}}
    }
    /** Native execution-chain owner capability, not serializable/decodable from control input. */
    public static final class Owner {
        private final TaskCoordinator issuer;private final Entry entry;
        private Owner(TaskCoordinator issuer,Entry entry){this.issuer=issuer;this.entry=entry;}
        public Cancellation.Requester cancellation(){return entry.cancellation.requester();}
        /** Exactly one attempted route; if this throws caller MUST NOT dispatch or try another route. */
        public void commitBeforeExternalDispatch(){issuer.commitDispatch(entry);}
        public void observeOutcome(TaskSnapshot.Effect effect){issuer.observe(entry,effect);}
        public void installOwnedStopper(NativeStopper stopper){issuer.installStopper(entry,stopper);}
        /** Domain/native writers REALLY ended. Does not itself return lane credit or publish effects. */
        public boolean reportActualDomainChainEnd(Cancellation.EndReason reason){
            if(reason==null)throw invalid();synchronized(issuer.monitor){if(entry.nativeEnd!=null)return false;if(!entry.bodyEntered)throw new IllegalStateException("Native domain body has not started");entry.nativeEnd=reason;issuer.maybeFinalize(entry);return true;}
        }
        /** Current native source/scope only, NOT effect success/durability or private-result viewer authorization. */
        public boolean mayPublishCurrentResult(){try{issuer.currentWork(entry);return true;}catch(RuntimeException stale){return false;}}
    }
    public static final class Submission {
        private final Client client;private final Owner owner;
        private Submission(Client client,Owner owner){this.client=client;this.owner=owner;}
        public Client client(){return client;}
        /** Native dispatcher/domain retains this root; NEVER return it through IPC. */
        public Owner nativeOwner(){return owner;}
    }
    /** THIS registry only; epoch-erasure code must separately prove complete domain coverage. */
    public static final class RegisteredSetFence {
        private final TaskCoordinator issuer;
        private RegisteredSetFence(TaskCoordinator issuer){this.issuer=issuer;}
    }
    private static final class Entry {
        final String job;final Lane lane;final ComponentGate.BusinessPermit permit;NativeBody body;
        final Cancellation cancellation=Cancellation.create();
        Phase phase=Phase.QUEUED;Persistence persistence=Persistence.NOT_ATTEMPTED;
        Object ticket;boolean bodyEntered,invocationReturned,dispatchAttempted,dispatchCommitted,unknown,finalizerScheduled,settled;
        int nativeCalls;
        NativeStopper stopper;boolean stopScheduled,stopReturned;
        Cancellation.EndReason nativeEnd;
        Owner owner;
        Entry(String job,Lane lane,ComponentGate.BusinessPermit permit,NativeBody body){this.job=job;this.lane=lane;this.permit=permit;this.body=body;}
    }
    private final Object monitor=new Object();
    private final ThreadLocal<Boolean> ownedThread=new ThreadLocal<>();
    private final ComponentGate gate;private final JournalBackend journal;private final CurrentContext context;
    private final int perLaneCapacity;
    private final Map<String,Entry> live=new LinkedHashMap<>();
    private final EnumMap<Lane,Integer> counts=new EnumMap<>(Lane.class);
    private final EnumMap<Lane,ThreadPoolExecutor> executors=new EnumMap<>(Lane.class);
    private boolean accepting=true,executorsClosed;
    public TaskCoordinator(ComponentGate gate,TaskJournal nativeJournal,CurrentContext nativeContext,ResourceBudgets.Limits nativeLimits){this(gate,nativeJournal,nativeContext,requireLimits(nativeLimits));}
    private static int requireLimits(ResourceBudgets.Limits limits){if(limits==null)throw invalid();return limits.maxJobsPerNativeLane();}
    public TaskCoordinator(ComponentGate gate,TaskJournal nativeJournal,CurrentContext nativeContext,int perLaneCapacity){this(gate,new NativeJournal(nativeJournal),nativeContext,perLaneCapacity);}
    /** PRIVATE synthetic journal injection is for reflected HOST tests, not production enrollment. */
    private TaskCoordinator(ComponentGate gate,JournalBackend journal,CurrentContext context,int capacity){
        if(gate==null || !gate.mayShowNativeShell() || journal==null || context==null || capacity<1 || capacity>32)throw invalid();
        this.gate=gate;this.journal=journal;this.context=context;perLaneCapacity=capacity;
        for(final Lane lane:Lane.values()){
            int queue=lane==Lane.SAFETY?6*capacity+1:capacity;counts.put(lane,0);
            ThreadFactory threads=new ThreadFactory(){@Override public Thread newThread(final Runnable r){Thread t=new Thread(new Runnable(){@Override public void run(){ownedThread.set(Boolean.TRUE);try{r.run();}finally{ownedThread.remove();}}},"ReBrowser-v2-"+lane.name().toLowerCase(java.util.Locale.ROOT));t.setDaemon(true);return t;}};
            ThreadPoolExecutor executor=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(queue),threads,new ThreadPoolExecutor.AbortPolicy());executor.allowCoreThreadTimeOut(true);executors.put(lane,executor);
        }
    }
    /** Job already exists in paired request/task ledger. This method does NOT authenticate/grant it. */
    public Submission submitNativeReserved(String job,Lane lane,ComponentGate.BusinessPermit permit,NativeBody body){
        if(job==null || !job.matches("j_[0-9a-f]{32}") || lane==null || lane==Lane.SAFETY || body==null)throw invalid();
        gate.checkCurrent(permit);context.checkCurrent(job);
        Entry e=new Entry(job,lane,permit,body);e.owner=new Owner(this,e);
        synchronized(monitor){
            gate.checkCurrent(permit);if(!accepting)throw new IllegalStateException("Native registered workset fenced");
            if(live.containsKey(job))throw new IllegalStateException("Original native job already owned");
            if(counts.get(lane)>=perLaneCapacity)throw new RejectedExecutionException("Native lane live-work budget full");
            live.put(job,e);counts.put(lane,counts.get(lane)+1);
        }
        try{executors.get(lane).execute(new Runnable(){@Override public void run(){invoke(e);}});}
        catch(RejectedExecutionException neverQueued){synchronized(monitor){e.unknown=true;e.nativeEnd=Cancellation.EndReason.STOP_CONFIRMED;e.invocationReturned=true;maybeFinalize(e);}}
        return new Submission(new Client(this,e),e.owner);
    }
    private void invoke(Entry e){
        try{
            synchronized(monitor){e.phase=Phase.STARTING;}
            if(e.cancellation.requester().snapshot().cancellationRequested()){synchronized(monitor){e.nativeEnd=Cancellation.EndReason.STOP_CONFIRMED;}return;}
            currentWork(e);Object ticket=journal.start(e.job,e.permit);if(ticket==null)throw new IllegalStateException("Native journal start returned no owner");
            synchronized(monitor){e.ticket=ticket;}currentWork(e);
            if(e.cancellation.requester().snapshot().cancellationRequested()){synchronized(monitor){e.nativeEnd=Cancellation.EndReason.STOP_CONFIRMED;}return;}
            synchronized(monitor){if(!accepting || e.cancellation.requester().snapshot().cancellationRequested()){e.nativeEnd=Cancellation.EndReason.STOP_CONFIRMED;return;}e.bodyEntered=true;e.phase=Phase.BODY_ENTERED;}
            e.body.run(e.owner); // returning is not completion of an asynchronous native chain
        }catch(Exception unknown){
            synchronized(monitor){e.unknown=true;if(!e.bodyEntered && e.nativeEnd==null)e.nativeEnd=Cancellation.EndReason.UNDERLYING_FAILED;}
        }finally{
            synchronized(monitor){e.invocationReturned=true;if(!e.finalizerScheduled)e.phase=Phase.WAITING_NATIVE_END;maybeFinalize(e);}
        }
    }
    private void current(Entry e){gate.checkCurrent(e.permit);context.checkCurrent(e.job);gate.checkCurrent(e.permit);}
    private void currentWork(Entry e){
        synchronized(monitor){if(!accepting)throw new SecurityException("Native task set fenced");}current(e);
        synchronized(monitor){if(!accepting)throw new SecurityException("Native task set fenced");}
    }
    private void beforeNewEffect(Entry e){currentWork(e);synchronized(monitor){if(e.nativeEnd!=null || e.settled)throw new SecurityException("Native execution chain already ended");}if(e.cancellation.requester().snapshot().cancellationRequested())throw new SecurityException("Native cancellation requested before dispatch");}
    private void beginNativeCall(Entry e,boolean dispatch){
        synchronized(monitor){if(!e.bodyEntered || e.nativeEnd!=null || e.settled)throw new IllegalStateException("Native execution owner no longer running");if(dispatch){if(e.dispatchAttempted)throw new IllegalStateException("Native dispatch already attempted");e.dispatchAttempted=true;}e.nativeCalls++;}
    }
    private void endNativeCall(Entry e){synchronized(monitor){e.nativeCalls--;maybeFinalize(e);}}
    private void commitDispatch(Entry e){
        beginNativeCall(e,true);try{beforeNewEffect(e);journal.dispatch(e.ticket,e.permit);beforeNewEffect(e);synchronized(monitor){e.dispatchCommitted=true;}}catch(RuntimeException unknown){synchronized(monitor){e.unknown=true;}throw unknown;}finally{endNativeCall(e);}
    }
    private void observe(Entry e,TaskSnapshot.Effect effect){
        if(effect!=TaskSnapshot.Effect.OBSERVED_SUCCESS && effect!=TaskSnapshot.Effect.OBSERVED_FAILURE)throw invalid();
        synchronized(monitor){if(!e.dispatchCommitted)throw new IllegalStateException("No current committed native dispatch intent");}
        beginNativeCall(e,false);try{currentWork(e);journal.observe(e.ticket,effect,e.permit);}catch(RuntimeException unknown){synchronized(monitor){e.unknown=true;}throw unknown;}finally{endNativeCall(e);}
    }
    private Cancellation.RequestOutcome cancel(Entry e){
        Cancellation.RequestOutcome outcome=e.cancellation.requester().requestCancellation();synchronized(monitor){scheduleStop(e);}return outcome;
    }
    private void installStopper(Entry e,NativeStopper stopper){
        if(stopper==null)throw invalid();synchronized(monitor){if(!e.bodyEntered || e.stopper!=null || e.nativeEnd!=null || e.settled)throw new IllegalStateException("Owned native stopper already fixed/ended");e.stopper=stopper;scheduleStop(e);}
    }
    private void scheduleStop(Entry e){
        if(e.stopper==null || e.stopScheduled || e.nativeEnd!=null || e.settled || !e.cancellation.requester().snapshot().cancellationRequested())return;
        e.stopScheduled=true;
        executors.get(Lane.SAFETY).execute(new Runnable(){@Override public void run(){
            try{boolean needed;synchronized(monitor){needed=e.nativeEnd==null;}if(needed)e.stopper.requestOwnedStop();}
            catch(Exception unknown){synchronized(monitor){e.unknown=true;}}
            finally{synchronized(monitor){e.stopReturned=true;maybeFinalize(e);}}
        }});
    }
    private void maybeFinalize(Entry e){
        // Called only under short metadata monitor; all DB/blocking work is in the owned safety executor.
        if(e.finalizerScheduled || e.settled || e.nativeEnd==null || !e.invocationReturned || e.nativeCalls!=0 || (e.stopScheduled && !e.stopReturned))return;
        e.finalizerScheduled=true;e.phase=Phase.FINALIZING;
        executors.get(Lane.SAFETY).execute(new Runnable(){@Override public void run(){finalizeOwned(e);}});
    }
    private void finalizeOwned(Entry e){
        Persistence result;
        try{current(e);if(e.ticket==null)journal.endQueued(e.job,e.permit);else journal.end(e.ticket,e.permit);result=Persistence.COMMITTED;}
        catch(Exception blockedOrUnknown){result=Persistence.BLOCKED_OR_UNKNOWN;}
        synchronized(monitor){
            e.persistence=result;e.cancellation.reporter().reportActualEnd(e.nativeEnd);e.settled=true;e.phase=Phase.SETTLED;e.body=null;e.stopper=null;e.ticket=null;
            live.remove(e.job);counts.put(e.lane,counts.get(e.lane)-1);monitor.notifyAll();shutdownIfDrained();
        }
    }
    public int registeredOutstanding(){synchronized(monitor){return live.size();}}
    /** Permanently fence new positive jobs. Old native owners/stop/finalization still run. */
    public RegisteredSetFence fenceToOwnedSafetyOnly(){
        ArrayList<Entry> previous;synchronized(monitor){accepting=false;previous=new ArrayList<>(live.values());shutdownIfDrained();}
        for(Entry e:previous)cancel(e);return new RegisteredSetFence(this);
    }
    public boolean isThisRegisteredSetSettled(RegisteredSetFence fence){
        if(fence==null || fence.issuer!=this)throw invalid();synchronized(monitor){return !accepting && live.isEmpty();}
    }
    @Override public void close(){fenceToOwnedSafetyOnly();}
    private void shutdownIfDrained(){if(!accepting && live.isEmpty() && !executorsClosed){executorsClosed=true;for(ThreadPoolExecutor executor:executors.values())executor.shutdown();}}
    /** Wait for ONLY owned Java executors, never interrupt/kill them or infer remote-effect success. */
    public boolean awaitOwnedExecutorExit(long timeout,TimeUnit unit)throws InterruptedException{
        if(timeout<0 || unit==null)throw invalid();if(Boolean.TRUE.equals(ownedThread.get()))throw new IllegalStateException("Owned native worker cannot await itself");long duration=unit.toNanos(timeout),remaining=duration,start=System.nanoTime();
        for(ThreadPoolExecutor executor:executors.values()){if(!executor.awaitTermination(Math.max(0,remaining),TimeUnit.NANOSECONDS))return false;remaining=duration-(System.nanoTime()-start);}return true;
    }
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid native task workset binding");}
}
