package io.github.rebrowser.browser;

import io.github.rebrowser.bootstrap.ComponentGate;

/**
 * One native owned window, one Back request, ONE chosen layer. Never inject global BACK,
 * synthesize positive source/owner consent, or fall through after timeout/unknown result.
 * Native layer owner supplies actual completion on its owner thread. This does not settle
 * a domain task/remote effect, release IO slots, or establish partition quiescence.
 * UI and administrator caller authorization/owned window resolution precedes this class.
 */
public final class BackCoordinator {
    public enum Layer {
        IME, MODAL, SOURCE_WARNING_CANCEL, FULLSCREEN, SELECTION, PANEL, POPUP, HISTORY, WORKSPACE, WINDOW_CLOSE
    }
    public enum Status { PENDING, CONSUMED, NO_OP, FAILED, UNKNOWN }
    public interface NativeWindow {
        /** Actual native owned layers only, never a caller-supplied availability DTO. No side effects. */
        boolean present(Layer layer);
        /** Cancel/leave only. Report real layer completion; never start another layer here. */
        void perform(Layer layer,Reporter reporter);
    }
    public static final class Receipt {
        private final Layer layer;
        private final long reservedRevision;
        private final Status status;
        private final boolean currentForPublication;
        private Receipt(Layer layer,long revision,Status status,boolean current){this.layer=layer;reservedRevision=revision;this.status=status;currentForPublication=current;}
        public Layer layer(){return layer;}
        public long reservedPresentationRevision(){return reservedRevision;}
        public Status status(){return status;}
        public boolean currentForPublication(){return currentForPublication;}
    }
    /** Execution-owner callback capability, not serializable/reconstructible from a job/view ID. */
    public static final class Reporter {
        private final BackCoordinator issuer;
        private final Attempt attempt;
        private Reporter(BackCoordinator issuer,Attempt attempt){this.issuer=issuer;this.attempt=attempt;}
        public boolean reportConsumed(){return issuer.settle(attempt,Status.CONSUMED);}
        public boolean reportNoOp(){return issuer.settle(attempt,Status.NO_OP);}
        public boolean reportFailed(){return issuer.settle(attempt,Status.FAILED);}
        public Receipt snapshot(){issuer.ownerThread();return issuer.view(attempt);}
    }
    private static final class Attempt {
        final Layer layer;
        final long revision;
        Status status=Status.PENDING;
        boolean ended;
        Attempt(Layer layer,long revision){this.layer=layer;this.revision=revision;}
    }
    private final ComponentGate gate;
    private final NativeWindow window;
    private final Thread thread=Thread.currentThread();
    private long revision=1;
    private Attempt pending;
    private boolean requesting,retired;
    public BackCoordinator(ComponentGate gate,NativeWindow actualOwnedWindow){
        if(gate==null || actualOwnedWindow==null || !gate.mayShowNativeShell())throw BrowserModels.invalid();this.gate=gate;window=actualOwnedWindow;
    }
    public long presentationRevision(){ownerThread();return revision;}
    /** Native modal/navigation/view changes fence late UI results, NOT actual work settlement. */
    public void presentationChanged(){ownerThread();revision=BrowserModels.next(revision);}
    public void retireWindowOwner(){ownerThread();long next=BrowserModels.next(revision);retired=true;revision=next;}
    public boolean hasPendingLayer(){ownerThread();return pending!=null;}
    /** Null permit still allows minimal owned UI cancellation, NEVER history/workspace reopening. */
    public Receipt request(long expectedPresentationRevision,ComponentGate.BusinessPermit permit){
        ownerThread();if(retired || requesting || pending!=null || expectedPresentationRevision!=revision)throw new IllegalStateException("Stale, pending or retired native Back request");
        requesting=true;
        try{
            Layer chosen=null;
            for(Layer layer:Layer.values()){
                if(businessLayer(layer) && !businessAllowed(permit))continue;
                if(window.present(layer)){chosen=layer;break;}
            }
            if(retired || expectedPresentationRevision!=revision)throw new IllegalStateException("Native Back presentation changed during selection");
            if(chosen==null)return new Receipt(null,revision,Status.NO_OP,true);
            if(businessLayer(chosen))gate.checkCurrent(permit);
            long reserved=BrowserModels.next(revision);Attempt attempt=new Attempt(chosen,reserved);Reporter reporter=new Reporter(this,attempt);revision=reserved;pending=attempt;
            try{window.perform(chosen,reporter);}catch(RuntimeException unknown){if(!attempt.ended)attempt.status=Status.UNKNOWN;}
            return view(attempt); // even UNKNOWN never invokes a second layer or frees pending
        }finally{requesting=false;}
    }
    private boolean settle(Attempt attempt,Status result){
        ownerThread();if(attempt.ended)return false;if(pending!=attempt)throw new IllegalStateException("Wrong native Back owner");
        attempt.status=result;attempt.ended=true;pending=null;return true;
    }
    private Receipt view(Attempt attempt){return new Receipt(attempt.layer,attempt.revision,attempt.status,!retired && revision==attempt.revision);}
    private boolean businessAllowed(ComponentGate.BusinessPermit permit){try{gate.checkCurrent(permit);return true;}catch(SecurityException locked){return false;}}
    private static boolean businessLayer(Layer layer){return layer==Layer.HISTORY || layer==Layer.WORKSPACE;}
    private void ownerThread(){if(Thread.currentThread()!=thread)throw new IllegalStateException("Native Back owner thread required");}
}
