package io.github.rebrowser.bootstrap;

import java.util.Arrays;

/**
 * Native entry/source lifetime gate, NOT administrator/owner/scope authorization.
 * Native context provider is a read-only nonblocking snapshot of accepted continuity.
 * Snapshot metadata never establishes acceptance by itself; no IPC/debug unlock setter.
 */
public final class ComponentGate {
    public enum SourceState { INITIALIZING, UNCONFIGURED, LOCKED, TRUSTED }
    public interface SourceEvaluator { SourceState current(); }
    public interface BoundSourceEvaluator { SourceView current(); }
    /** Native continuity supplies a FRESH opaque incarnation for each accepted source/epoch.
     * Never reuse a context object after a cutover, even when returning to same source/data.
     * This DTO is not a receipt/authority to accept a source. No old-data access here.
     */
    public static final class SourceView {
        private final SourceState state;private final Object incarnation;private final byte[] epoch;
        private SourceView(SourceState state,Object incarnation,byte[] epoch){if(state==null || ((state==SourceState.TRUSTED)!=(incarnation!=null)))throw new IllegalArgumentException("Invalid native source snapshot");this.state=state;this.incarnation=incarnation;this.epoch=epoch==null?null:epoch.clone();}
        public static SourceView untrusted(SourceState state){if(state==SourceState.TRUSTED)throw new IllegalArgumentException("Native source incarnation required");return new SourceView(state,null,null);}
        public static SourceView acceptedNative(Object nativeAcceptedIncarnation){if(nativeAcceptedIncarnation==null)throw new IllegalArgumentException("Native source incarnation required");return new SourceView(SourceState.TRUSTED,nativeAcceptedIncarnation,null);}
        /** Actual accepted native epoch, not caller-provided enrollment or authority. */
        public static SourceView acceptedNative(Object nativeAcceptedIncarnation,byte[] nativeEpoch){if(nativeAcceptedIncarnation==null || nativeEpoch==null || nativeEpoch.length!=16)throw new IllegalArgumentException("Native accepted epoch required");return new SourceView(SourceState.TRUSTED,nativeAcceptedIncarnation,nativeEpoch);}
    }
    private final ProcessRole role;private final BoundSourceEvaluator source;
    private SourceState observed;private Object incarnation;private byte[] observedEpoch;private long revision;private boolean evaluating,poisoned;
    /** State-only routing for locked shells/explicit synthetic fixtures; cannot represent an
     * unobserved epoch change. PRODUCTION acceptance must use forNativeSource + fresh incarnation.
     */
    public ComponentGate(ProcessRole role,SourceEvaluator source){
        if(role==null || source==null)throw new IllegalArgumentException("Native source decision required");this.role=role;
        final Object stateOnlyContext=new Object();this.source=new BoundSourceEvaluator(){@Override public SourceView current(){SourceState state=source.current();return state==null?null:state==SourceState.TRUSTED?SourceView.acceptedNative(stateOnlyContext):SourceView.untrusted(state);}};
    }
    private ComponentGate(ProcessRole role,BoundSourceEvaluator source,boolean bound){if(role==null || source==null)throw new IllegalArgumentException("Native source decision required");this.role=role;this.source=source;}
    public static ComponentGate forNativeSource(ProcessRole role,BoundSourceEvaluator nativeAcceptedSource){return new ComponentGate(role,nativeAcceptedSource,true);}
    public boolean mayShowNativeShell(){return role==ProcessRole.MAIN;}
    /** No provider IO/DB/authority mutation allowed while this short snapshot lock is held. */
    private synchronized SourceState capture(){
        if(role!=ProcessRole.MAIN)return SourceState.LOCKED;
        if(evaluating){poisoned=true;return SourceState.LOCKED;}
        SourceView view=null;evaluating=true;
        try{view=source.current();}catch(RuntimeException unavailable){/* reject, do not log source-private data */}finally{evaluating=false;}
        SourceState next=view==null || poisoned?SourceState.LOCKED:view.state;Object context=next==SourceState.TRUSTED?view.incarnation:null;
        byte[] epoch=next==SourceState.TRUSTED?view.epoch:null;
        if(next!=observed || context!=incarnation || !Arrays.equals(epoch,observedEpoch)){if(revision==Long.MAX_VALUE){poisoned=true;next=SourceState.LOCKED;context=null;epoch=null;}else revision++;observed=next;incarnation=context;observedEpoch=epoch==null?null:epoch.clone();}
        return next;
    }
    public SourceState sourceState(){return capture();}
    public static final class BusinessPermit {
        private final ComponentGate issuer;private final Object incarnation;private final long revision;
        private BusinessPermit(ComponentGate issuer,Object incarnation,long revision){this.issuer=issuer;this.incarnation=incarnation;this.revision=revision;}
    }
    public synchronized BusinessPermit requireBusiness(){if(capture()!=SourceState.TRUSTED)throw locked();return new BusinessPermit(this,incarnation,revision);}
    public synchronized void checkCurrent(BusinessPermit permit){if(capture()!=SourceState.TRUSTED || !matches(permit))throw locked();}
    public synchronized void checkSameContext(BusinessPermit pinned,BusinessPermit current){if(capture()!=SourceState.TRUSTED || !matches(pinned) || !matches(current))throw locked();}
    public synchronized void requireDataEpoch(BusinessPermit permit,byte[] nativeExpectedEpoch){if(capture()!=SourceState.TRUSTED || !matches(permit) || nativeExpectedEpoch==null || nativeExpectedEpoch.length!=16 || observedEpoch==null || !Arrays.equals(observedEpoch,nativeExpectedEpoch))throw locked();}
    private boolean matches(BusinessPermit permit){return permit!=null && permit.issuer==this && permit.incarnation==incarnation && permit.revision==revision;}
    /** Sticky native resource lifetime. A new valid permit never retargets an old DB/catalog/runtime. */
    public static final class ResourcePin {
        private final ComponentGate issuer;private BusinessPermit anchor;
        private ResourcePin(ComponentGate issuer){this.issuer=issuer;}
        public synchronized void checkOrBind(BusinessPermit current){issuer.checkCurrent(current);if(anchor==null)anchor=current;else issuer.checkSameContext(anchor,current);}
        public synchronized void checkEpochOrBind(BusinessPermit current,byte[] nativeExpectedEpoch){issuer.requireDataEpoch(current,nativeExpectedEpoch);checkOrBind(current);}
        public synchronized void requireCurrent(){if(anchor==null)throw locked();issuer.checkCurrent(anchor);}
    }
    public ResourcePin newResourcePin(){return new ResourcePin(this);}
    private static SecurityException locked(){return new SecurityException("Business source/data context locked or retired");}
}
