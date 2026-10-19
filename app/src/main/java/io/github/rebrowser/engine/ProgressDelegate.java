package io.github.rebrowser.engine;

import io.github.rebrowser.bootstrap.ComponentGate;
import java.util.concurrent.atomic.AtomicReference;
import org.mozilla.geckoview.GeckoSession;

/**
 * Direct Gecko progress/security/checkpoint observations for ONE registered native session.
 * Local observation order is NOT a document ID, login/permission proof, job receipt or
 * actual network/work quiescence. PageStop(success) never marks a domain task complete.
 * No sensitive state serialization/logging here; native checkpoint receiver must enforce
 * byte limits/encryption and current session/epoch/fence again before durable publication.
 */
public final class ProgressDelegate implements GeckoSession.ProgressDelegate {
    public enum Event { REPORTED_START, REPORTED_PROGRESS, REPORTED_STOP }
    public static final class Loading {
        private final long sequence;
        private final Event event;
        private final String startUrl;
        private final int progress;
        private final Boolean reportedSuccess;
        private Loading(long sequence,Event event,String startUrl,int progress,Boolean success){this.sequence=sequence;this.event=event;this.startUrl=startUrl;this.progress=progress;reportedSuccess=success;}
        public long observationSequence(){return sequence;}
        public Event event(){return event;}
        /** Only present for START, display-only; no fabricated URL correlation on later SDK events. */
        public String reportedStartUrl(){return startUrl;}
        public int reportedProgress(){return progress;}
        public Boolean reportedStopSuccess(){return reportedSuccess;}
    }
    public static final class Security {
        private final long sequence;
        private final boolean secure,exception;
        private final int mode,passive,active;
        private Security(long sequence,GeckoSession.ProgressDelegate.SecurityInformation info){this.sequence=sequence;secure=info.isSecure;exception=info.isException;mode=info.securityMode;passive=info.mixedModePassive;active=info.mixedModeActive;}
        public long observationSequence(){return sequence;}
        public boolean reportedSecure(){return secure;}
        public boolean reportedException(){return exception;}
        public int securityMode(){return mode;}
        public int passiveMixedContent(){return passive;}
        public int activeMixedContent(){return active;}
    }
    public interface NativeObserver {
        /** Native UI/service observer only. Never capture Activity strongly in a process session delegate. */
        void loading(Loading observation);
        void security(Security observation);
        /** false means rejected WITHOUT retaining/starting work. true requires actual-owner end ack. */
        boolean checkpoint(SensitiveCheckpoint offer);
    }
    /** Native worker capability; never ordinary logs/export/IPC/UI, and never an authenticated account. */
    public static final class SensitiveCheckpoint {
        private final ProgressDelegate issuer;
        private final long sequence;
        private final AtomicReference<GeckoSession.SessionState> state;
        private boolean acknowledged;
        private SensitiveCheckpoint(ProgressDelegate issuer,long sequence,GeckoSession.SessionState state){this.issuer=issuer;this.sequence=sequence;this.state=new AtomicReference<>(new GeckoSession.SessionState(state));}
        public long observationSequence(){return sequence;}
        public PartitionCatalog.Lease partition(){return issuer.partition;}
        public Object nativeOwnerToken(){return issuer.nativeOwner;}
        /** One transfer to the exact owned worker. Worker enforces bounds; do NOT serialize on UI. */
        public GeckoSession.SessionState takeForNativeWorker(){GeckoSession.SessionState value=state.getAndSet(null);if(value==null)throw new IllegalStateException("Checkpoint already transferred/retired");return value;}
        /** Marshal back to native UI only AFTER the whole capture/write chain actually ends. */
        public void acknowledgeActualCaptureEnd(){
            GeckoSessionOwner.mainThread();if(acknowledged)return;acknowledged=true;state.set(null);if(issuer.pending==this)issuer.pending=null;
        }
    }
    private final GeckoSession bound;
    private final ComponentGate gate;
    private final PartitionCatalog partitions;
    private final PartitionCatalog.Lease partition;
    private final Object nativeOwner;
    private final NativeObserver observer;
    private SensitiveCheckpoint pending;
    private long sequence;
    private int progress;
    private boolean invalidated;
    public ProgressDelegate(GeckoSession actualSession,ComponentGate gate,PartitionCatalog partitions,PartitionCatalog.Lease partition,Object nativeOwnerToken,NativeObserver observer){
        GeckoSessionOwner.mainThread();if(actualSession==null || gate==null || partitions==null || partition==null || nativeOwnerToken==null || observer==null)throw new IllegalArgumentException("Native progress binding required");
        partitions.requireGate(gate);bound=actualSession;this.gate=gate;this.partitions=partitions;this.partition=partition;nativeOwner=nativeOwnerToken;this.observer=observer;
    }
    @Override public void onPageStart(GeckoSession actual,String url){
        if(!eligible(actual))return;long next=next();if(invalidated)return;progress=0;
        // Do not retain oversized/invalid display strings or infer actual principal from them.
        String display=displayUrl(url);deliver(new Loading(next,Event.REPORTED_START,display,progress,null));
    }
    @Override public void onProgressChange(GeckoSession actual,int value){
        if(!eligible(actual) || value<0 || value>100)return;long next=next();if(invalidated)return;progress=Math.max(progress,value);deliver(new Loading(next,Event.REPORTED_PROGRESS,null,progress,null));
    }
    @Override public void onPageStop(GeckoSession actual,boolean success){
        if(!eligible(actual))return;long next=next();if(invalidated)return;deliver(new Loading(next,Event.REPORTED_STOP,null,progress,success));
    }
    @Override public void onSecurityChange(GeckoSession actual,GeckoSession.ProgressDelegate.SecurityInformation info){
        if(!eligible(actual) || info==null)return;long next=next();if(invalidated)return;
        try{observer.security(new Security(next,info));}catch(RuntimeException ignoredObserverFailure){/* no private certificate/origin/error text in logs */}
    }
    @Override public void onSessionStateChange(GeckoSession actual,GeckoSession.SessionState state){
        if(!eligible(actual) || state==null || pending!=null)return;long next=next();if(invalidated)return;
        SensitiveCheckpoint offer=new SensitiveCheckpoint(this,next,state);pending=offer;
        if(!eligible(actual)){offer.acknowledgeActualCaptureEnd();return;} // never submitted, no worker owns it
        try{if(!observer.checkpoint(offer))offer.acknowledgeActualCaptureEnd();}
        catch(RuntimeException unknownReceiverOutcome){/* keep the exact offer/credit until its native worker acknowledges end; no blind retry */}
    }
    /** Fences new publication only; a retained native writer still owns its old end acknowledgement. */
    public void invalidate(){GeckoSessionOwner.mainThread();invalidated=true;}
    public boolean hasOutstandingCheckpoint(){GeckoSessionOwner.mainThread();return pending!=null;}
    private boolean eligible(GeckoSession actual){
        GeckoSessionOwner.mainThread();if(invalidated || actual!=bound || !bound.isOpen())return false;
        try{ComponentGate.BusinessPermit permit=gate.requireBusiness();partitions.requireActive(partition,permit);return true;}catch(SecurityException|IllegalArgumentException stale){return false;}
    }
    private long next(){if(sequence==Long.MAX_VALUE){invalidated=true;return sequence;}return ++sequence;}
    private void deliver(Loading event){try{observer.loading(event);}catch(RuntimeException ignoredObserverFailure){/* observation is not task settlement */}}
    private static String displayUrl(String url){
        if(url==null || url.length()>8192)return null;
        for(int i=0;i<url.length();i++){char c=url.charAt(i);if(Character.isISOControl(c))return null;if(Character.isHighSurrogate(c)){if(++i>=url.length() || !Character.isLowSurrogate(url.charAt(i)))return null;}else if(Character.isLowSurrogate(c))return null;}return url;
    }
}
