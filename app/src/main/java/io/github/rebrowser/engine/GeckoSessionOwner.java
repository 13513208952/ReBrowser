package io.github.rebrowser.engine;

import android.os.Looper;
import io.github.rebrowser.bootstrap.ComponentGate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;

/**
 * Strong native session ownership, explicit issued context and fail-closed admission.
 * Regular session creation only; Popup document validation/checkpoint restore are separate.
 * No Activity/View retained here (display ownership uses plain tokens). SDK open is async:
 * OPEN_REQUESTED is NOT page ready, authenticated, restored, or actual work settled.
 */
public final class GeckoSessionOwner {
    public enum State { ALLOCATED, OPEN_REQUESTED, CLOSING, CLOSE_FAILED, CLOSED }
    public interface Initializer {
        /** Native source/epoch/finite-permission reconciliation; throwing prevents runtime creation. */
        void reconcileBeforeOpen(PartitionCatalog.Lease partition,ComponentGate.BusinessPermit permit);
        /** Install delegates BEFORE open with the exact already-registered owner, not a post-open tab lookup.
         * No queued loads/effects or Activity capture. Ref is allocated, NOT an open/ready capability. */
        void installDelegates(GeckoSession session,SessionRef allocatedOwner,PartitionCatalog.Lease partition,Object nativeOwnerToken);
        /** Synchronous/idempotent positive-publication fencing, NOT writer or JNI quiescence. */
        default void retireDelegates(GeckoSession session,SessionRef owner){}
        default SessionPresentation.Frame presentation(SessionRef owner){throw new UnsupportedOperationException("Session presentation unavailable");}
    }
    public static final class SessionRef {
        private final GeckoSessionOwner issuer;
        private final GeckoSession session;
        private final PartitionCatalog.Lease partition;
        private Object displayToken;
        private State state=State.ALLOCATED;
        private SessionRef(GeckoSessionOwner issuer,GeckoSession session,PartitionCatalog.Lease partition){this.issuer=issuer;this.session=session;this.partition=partition;}
        public PartitionCatalog.Lease partition(){return partition;}
    }
    private final ComponentGate gate;
    private final GeckoRuntimeOwner runtime;
    private final PartitionCatalog partitions;
    private final Initializer initializer;
    private final int capacity;
    private final Map<SessionRef,SessionRef> sessions=new LinkedHashMap<>();
    private boolean opening,unknownRejectedResponseClose;
    void observeUnknownRejectedBodyClose(SessionRef ref){mainThread();own(ref);unknownRejectedResponseClose=true;}
    /** Sticky even after retiring a callback/ref; no positive native-tail/quiescence receipt. */
    public boolean hasUnknownRejectedResponseClose(){mainThread();return unknownRejectedResponseClose;}
    public GeckoSessionOwner(ComponentGate gate,GeckoRuntimeOwner runtime,PartitionCatalog partitions,Initializer initializer,int capacity){
        mainThread();if(gate==null || runtime==null || partitions==null || initializer==null || capacity<1 || capacity>256)throw invalid();
        partitions.requireGate(gate);
        this.gate=gate;this.runtime=runtime;this.partitions=partitions;this.initializer=initializer;this.capacity=capacity;
    }
    public void requireBindings(ComponentGate expectedGate,PartitionCatalog expectedPartitions){mainThread();if(gate!=expectedGate || partitions!=expectedPartitions)throw invalid();}
    public SessionRef open(PartitionCatalog.Lease partition,Object nativeOwnerToken,ComponentGate.BusinessPermit permit){
        mainThread();if(nativeOwnerToken==null)throw invalid();if(opening)throw new IllegalStateException("Reentrant session creation forbidden");opening=true;
        try{return openInternal(partition,nativeOwnerToken,permit);}finally{opening=false;}
    }
    private SessionRef openInternal(PartitionCatalog.Lease partition,Object nativeOwnerToken,ComponentGate.BusinessPermit permit){
        gate.checkCurrent(permit);partitions.requireActive(partition,permit);
        if(sessions.size()>=capacity)throw new IllegalStateException("Live session limit reached");
        initializer.reconcileBeforeOpen(partition,permit);gate.checkCurrent(permit);partitions.requireActive(partition,permit);
        GeckoSession session=new GeckoSession(new GeckoSessionSettings.Builder().contextId(partitions.activeContextId(partition,permit)).usePrivateMode(partition.retention()==PartitionCatalog.Retention.PRIVATE_SESSION).build());
        SessionRef ref=new SessionRef(this,session,partition);sessions.put(ref,ref);
        try{
            initializer.installDelegates(session,ref,partition,nativeOwnerToken);gate.checkCurrent(permit);partitions.requireActive(partition,permit);
            if(ref.state!=State.ALLOCATED || !sessions.containsKey(ref))throw new IllegalStateException("Allocated session retired during delegate installation");
            session.open(runtime.requireRuntime(permit));session.setFocused(false);session.setActive(false);
            if(ref.state!=State.ALLOCATED || !sessions.containsKey(ref) || !session.isOpen())throw new IllegalStateException("Session ended during open request");
            ref.state=State.OPEN_REQUESTED;return ref;
        }catch(RuntimeException failure){
            try{initializer.retireDelegates(session,ref);}catch(RuntimeException cleanup){failure.addSuppressed(cleanup);}
            try{if(session.isOpen())session.close();}catch(RuntimeException cleanup){failure.addSuppressed(cleanup);}
            if(session.isOpen()){ref.state=State.CLOSE_FAILED;sessions.put(ref,ref);}else{sessions.remove(ref);ref.state=State.CLOSED;}
            throw failure; // no automatic second route/runtime/session attempt
        }
    }
    /** Trusted native delegates/domain only. This accessor does NOT authorize any load/effect. */
    public GeckoSession requireOpen(SessionRef ref,ComponentGate.BusinessPermit permit){
        mainThread();gate.checkCurrent(permit);known(ref);partitions.requireActive(ref.partition,permit);
        if(ref.state!=State.OPEN_REQUESTED || !ref.session.isOpen())throw new IllegalStateException("Session is not open");return ref.session;
    }
    public void show(SessionRef ref,boolean focused,ComponentGate.BusinessPermit permit){GeckoSession session=requireOpen(ref,permit);session.setActive(true);session.setFocused(focused);}
    /** Safe stop of already-owned presentation must not require fresh site login/source unlock. */
    public void hide(SessionRef ref){mainThread();own(ref);if(ref.session.isOpen()){ref.session.setFocused(false);ref.session.setActive(false);}}
    /** Close the exact owned window only. Not IO settlement/context erasure/runtime shutdown. */
    public void close(SessionRef ref){
        mainThread();own(ref);if(ref.displayToken!=null)throw new IllegalStateException("Release owned display before closing session");
        if(ref.state==State.CLOSED)return;
        if(opening || ref.state==State.CLOSING)throw new IllegalStateException("Reentrant session close forbidden");
        ref.state=State.CLOSING;
        try{initializer.retireDelegates(ref.session,ref);ref.session.close();if(ref.session.isOpen())throw new IllegalStateException("Session close did not close window");}
        catch(RuntimeException failure){ref.state=State.CLOSE_FAILED;throw failure;}
        ref.state=State.CLOSED;sessions.remove(ref);
    }
    /** SDK onCrash/onKill says window closed; never re-open or silently restore here. */
    public void observeEngineClosed(SessionRef ref){mainThread();own(ref);if(ref.session.isOpen())throw new IllegalStateException("Engine window still open");initializer.retireDelegates(ref.session,ref);ref.state=State.CLOSED;sessions.remove(ref);}
    public SessionPresentation.Frame presentation(SessionRef ref,ComponentGate.BusinessPermit permit){requireOpen(ref,permit);SessionPresentation.Frame frame=initializer.presentation(ref);requireOpen(ref,permit);return frame;}
    /** Native safety sweep only; no IPC publication, no whole-work/erasure/quiescence claim. */
    public static final class StopSweep {
        private final int closedWindows,stillTracked;
        private StopSweep(int closedWindows,int stillTracked){this.closedWindows=closedWindows;this.stillTracked=stillTracked;}
        public int closedWindows(){return closedWindows;}
        public int stillTracked(){return stillTracked;}
    }
    /** Source lock must not strand a failed-open handle that was never returned to its caller. */
    public StopSweep closeUndisplayedForDomain(PartitionCatalog.Domain domain){
        mainThread();if(domain==null)throw invalid();int closed=0,remaining=0;
        for(SessionRef ref:new ArrayList<>(sessions.keySet()))if(ref.partition.domain()==domain){
            if(ref.displayToken!=null){remaining++;continue;}
            try{close(ref);closed++;}catch(RuntimeException failure){remaining++;} // retain exact failed owner; no false all-stopped result
        }
        return new StopSweep(closed,remaining);
    }
    public State state(SessionRef ref){mainThread();own(ref);return ref.state;}
    public List<SessionRef> sessions(ComponentGate.BusinessPermit permit){mainThread();gate.checkCurrent(permit);return Collections.unmodifiableList(new ArrayList<>(sessions.keySet()));}
    GeckoSession claimDisplay(SessionRef ref,Object token,ComponentGate.BusinessPermit permit){
        GeckoSession session=requireOpen(ref,permit);if(token==null || ref.displayToken!=null)throw new IllegalStateException("Session already has display owner");ref.displayToken=token;return session;
    }
    void releaseDisplay(SessionRef ref,Object token){mainThread();own(ref);if(ref.displayToken!=token)throw new IllegalStateException("Wrong display owner");ref.displayToken=null;}
    GeckoSession cleanupSession(SessionRef ref){mainThread();own(ref);return ref.session;}
    private void known(SessionRef ref){own(ref);if(!sessions.containsKey(ref))throw invalid();}
    private void own(SessionRef ref){if(ref==null || ref.issuer!=this)throw invalid();}
    static void mainThread(){if(Looper.myLooper()!=Looper.getMainLooper())throw new IllegalStateException("Gecko UI thread required");}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid native Gecko session owner/handle");}
}
