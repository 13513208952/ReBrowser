package io.github.rebrowser.continuity;

/**
 * Owned native four-click/verification reducer. No IO, business unlock or erasure here.
 * Context is native METADATA, not source authority. Production construction/verification
 * is through OwnerAuthentication, never caller booleans, administrator keys or IPC DTOs.
 * Native bootstrap must supply a side-effect-free current-source/epoch/ledger guard.
 * This limited final consent STILL needs current fences, bounded actual-work quiescence,
 * exact owned erasure and fresh epoch commit in the separate native erasure backend.
 */
public final class ContinuityCoordinator {
    public enum Stage { CONFIRMING, WAITING_OWNER, WAITING_TICKET, CONSENT_AVAILABLE, CONSENT_CONSUMED, RETIRED }
    public interface CurrentSourceGuard { void checkCurrent(Context expectedNativeContext); }
    interface Clock { long elapsedMs(); }
    public static final class Context {
        private final LastGoodSourceStore.View baseline;
        private final String candidateSigner,candidateRoot;
        private Context(LastGoodSourceStore.View baseline,String signer,String root){this.baseline=baseline;candidateSigner=signer;candidateRoot=root;}
        /** Trusted native bootstrap metadata ONLY, never decode candidate/source from Intent/control args. */
        public static Context fromNativeMetadata(LastGoodSourceStore.View baseline,String candidateSigner,String candidateRoot){
            if(baseline==null || baseline.kind()!=LastGoodSourceStore.Kind.CURRENT)throw invalid();
            LastGoodSourceStore.Record record=baseline.record();LastGoodSourceStore.Phase phase=record.phase();
            if(phase!=LastGoodSourceStore.Phase.STABLE && phase!=LastGoodSourceStore.Phase.PREPARED_OLD_DATA_PRESERVED)throw invalid();
            new LastGoodSourceStore.SourceEpoch(candidateSigner,candidateRoot,new byte[16]); // strict syntax only, no authority
            if(record.lastGood().signingSha256().equals(candidateSigner) && record.lastGood().rootSha256().equals(candidateRoot))throw invalid();
            if(phase==LastGoodSourceStore.Phase.PREPARED_OLD_DATA_PRESERVED && (!record.pendingSource().signingSha256().equals(candidateSigner) || !record.pendingSource().rootSha256().equals(candidateRoot)))throw invalid();
            return new Context(baseline,candidateSigner,candidateRoot);
        }
        public LastGoodSourceStore.View nativeBaselineView(){return baseline;}
        public byte[] oldEpoch(){return baseline.record().lastGood().dataEpoch();}
        public String candidateSignerSha256(){return candidateSigner;}
        public String candidateRootSha256(){return candidateRoot;}
    }
    public static final class View {
        private final long revision;
        private final Stage stage;
        private final ContinuityState.Confirmation step;
        private final boolean nativeOwnerWorkPending;
        private View(long revision,Stage stage,ContinuityState.Confirmation step,boolean pending){this.revision=revision;this.stage=stage;this.step=step;nativeOwnerWorkPending=pending;}
        public long revision(){return revision;}
        public Stage stage(){return stage;}
        public ContinuityState.Confirmation step(){return step;}
        public boolean hasPendingNativeOwnerWork(){return nativeOwnerWorkPending;}
        // Deliberately no trusted/unlocked/erased flag.
    }
    /** Opaque, private-issued native attempt; only the real owner-auth bridge completes it in production. */
    public static final class OwnerAttempt {
        private final ContinuityCoordinator issuer;
        private final ContinuityState.Confirmation step;
        private boolean workPending,terminal;
        private OwnerAttempt(ContinuityCoordinator issuer,ContinuityState.Confirmation step){this.issuer=issuer;this.step=step;}
        public Context context(){return issuer.context;}
        public ContinuityState.Confirmation step(){return step;}
        boolean windowMatches(Object window){issuer.owner();return issuer.window==window;}
        void checkBeforeNativeBegin(){issuer.liveAttempt(this,Stage.WAITING_OWNER);if(terminal || workPending)throw stale();issuer.current();}
        void nativeWorkMayStart(){checkBeforeNativeBegin();workPending=true;issuer.bump();}
        /** Only bridge's positive Binding.started==false/WAIT_FOREGROUND observation can call this. */
        void nativeNoSubmissionYet(){issuer.owner();if(terminal)return;workPending=false;if(issuer.retired && issuer.pending==this)issuer.pending=null;}
        void nativeSubmissionUnknown(){issuer.owner();/* retain workPending; no alternate attempt/timeout settlement */}
        /** First actual native terminal result wins, even after retirement/source revocation. */
        void nativeTerminal(boolean hasPrivateTicket){
            issuer.owner();if(terminal)return;terminal=true;workPending=false;
            if(issuer.pending!=this)return;
            if(issuer.retired || !hasPrivateTicket){issuer.retire();issuer.pending=null;return;}
            issuer.stage=Stage.WAITING_TICKET;issuer.bump();
        }
        void checkBeforeNativeTicketConsume(){issuer.liveAttempt(this,Stage.WAITING_TICKET);if(!terminal || workPending)throw stale();issuer.current();}
        /** PACKAGE-PRIVATE native bridge hook, NOT a client success/Boolean API. Tests use synthetic signals explicitly. */
        void verifiedAfterNativeTicketConsume(){
            checkBeforeNativeTicketConsume();long now=issuer.now();issuer.pending=null;
            if(step==ContinuityState.Confirmation.FORCE_2){issuer.step=ContinuityState.Confirmation.FORCE_3;issuer.stage=Stage.CONFIRMING;}
            else if(step==ContinuityState.Confirmation.FORCE_3){issuer.consent=new Consent(issuer,now);issuer.stage=Stage.CONSENT_AVAILABLE;}
            else throw stale();
            issuer.bump();
        }
        void retireFlow(){issuer.retire();}
        boolean isFlowRetired(){issuer.owner();return issuer.retired;}
    }
    /** Final limited native owner-consent capability, not a BusinessPermit/erasure receipt. */
    public static final class Consent {
        private final ContinuityCoordinator issuer;
        private final long issued;
        private boolean consumed;
        private Consent(ContinuityCoordinator issuer,long issued){this.issuer=issuer;this.issued=issued;}
    }
    private final Context context;
    private final Object window;
    private final CurrentSourceGuard currentSource;
    private final Clock clock;
    private final Thread thread=Thread.currentThread();
    private long revision,lastTime;
    private boolean retired,checking;
    private Stage stage=Stage.CONFIRMING;
    private ContinuityState.Confirmation step=ContinuityState.Confirmation.TRUST_SOURCE;
    private OwnerAttempt pending;
    private Consent consent;
    // Production factory lives in OwnerAuthentication so actual owned-window/clock/Root binding is native.
    ContinuityCoordinator(Context context,Object nativeWindow,CurrentSourceGuard guard,Clock clock){
        if(context==null || nativeWindow==null || guard==null || clock==null)throw invalid();this.context=context;window=nativeWindow;currentSource=guard;this.clock=clock;
        lastTime=clock.elapsedMs();if(lastTime<0)throw invalid();
    }
    public View view(){owner();return new View(revision,stage,step,pending!=null && pending.workPending);}
    public void confirmOrdinary(long expectedRevision,ContinuityState.Confirmation expectedStep){
        confirming(expectedRevision,expectedStep);if(step.requiresSeparateAndroidOwnerVerification())throw stale();current();step=step.next();bump();
    }
    public OwnerAttempt beginOwnerConfirmation(long expectedRevision,ContinuityState.Confirmation expectedStep){
        confirming(expectedRevision,expectedStep);if(!step.requiresSeparateAndroidOwnerVerification())throw stale();current();pending=new OwnerAttempt(this,step);stage=Stage.WAITING_OWNER;bump();return pending;
    }
    public Consent availableConsent(){owner();current();if(stage!=Stage.CONSENT_AVAILABLE || consent==null)throw stale();checkConsentAge(consent);return consent;}
    /** Native erasure coordinator only: spend before external handoff, never replay an unknown outcome. */
    Context consumeConsentForNativeErasure(Consent expected){
        owner();current();if(expected==null || expected.issuer!=this || consent!=expected || expected.consumed || stage!=Stage.CONSENT_AVAILABLE)throw stale();checkConsentAge(expected);
        expected.consumed=true;consent=null;stage=Stage.CONSENT_CONSUMED;bump();return context;
    }
    /** Revoke eligibility immediately; old native OS work remains pending until its real terminal callback. */
    public void retire(){owner();if(retired)return;retired=true;stage=Stage.RETIRED;consent=null;if(pending!=null && !pending.workPending)pending=null;if(revision<Long.MAX_VALUE)revision++;}
    private void confirming(long expectedRevision,ContinuityState.Confirmation expectedStep){owner();if(retired || checking || stage!=Stage.CONFIRMING || pending!=null || expectedRevision!=revision || expectedStep!=step)throw stale();}
    private void liveAttempt(OwnerAttempt attempt,Stage expected){owner();if(retired || checking || pending!=attempt || stage!=expected)throw stale();}
    private void current(){
        owner();if(retired || checking)throw stale();now();long before=revision;checking=true;
        try{currentSource.checkCurrent(context);}catch(RuntimeException revoked){retire();throw new SecurityException("Native continuity context no longer current",revoked);}finally{checking=false;}
        if(retired || revision!=before)throw stale();now();
    }
    private long now(){
        long value;try{value=clock.elapsedMs();}catch(RuntimeException unavailable){retire();throw new SecurityException("Native continuity clock unavailable",unavailable);}
        if(value<lastTime || value<0){retire();throw new SecurityException("Native continuity clock rolled back");}lastTime=value;return value;
    }
    private void checkConsentAge(Consent value){if(now()-value.issued>30_000){retire();throw new SecurityException("Native owner consent expired");}}
    private void bump(){if(revision==Long.MAX_VALUE){retire();throw stale();}revision++;}
    private void owner(){if(Thread.currentThread()!=thread)throw new IllegalStateException("Native continuity owner thread required");}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid native continuity context");}
    private static IllegalStateException stale(){return new IllegalStateException("Stale or invalid native continuity step");}
}
