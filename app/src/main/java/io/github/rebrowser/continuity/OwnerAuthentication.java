package io.github.rebrowser.continuity;

import android.app.Activity;
import android.app.KeyguardManager;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.CancellationSignal;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import java.security.SecureRandom;

/**
 * Activity-owned fresh Android owner verification for FORCE_2 and FORCE_3 separately.
 * No caller bool/Intent result, Root key, whitelist, previous-step success or biometric
 * error can create a ticket. Only positive current OS no-lock status exempts the prompt.
 * No passwords/biometric templates, business data, last-good writes, erasure or unlock.
 * Actual Android authentication remains an independently authorized runtime test gate.
 */
public final class OwnerAuthentication {
    public enum Evidence { SYSTEM_AUTHENTICATED, OS_CONFIRMED_NO_LOCK }
    public enum Outcome { VERIFIED, NO_LOCK_CONFIGURED, CANCELLED, FAILED, UNAVAILABLE, SUBMISSION_UNKNOWN }
    public enum Start { WAIT_FOREGROUND, TERMINAL_DELIVERED, PROMPT_SUBMITTED, SUBMISSION_UNKNOWN }
    public interface Callback {void result(Result result);}
    // Internal platform boundary; NO public injection/alternate authenticator constructor.
    // HOST tests reflect the private constructor with explicit synthetic platform signals.
    enum SecurityState { NO_LOCK, SECURE, UNKNOWN }
    enum AuthKind { BIOMETRIC, DEVICE_CREDENTIAL, UNKNOWN }
    enum NativeError { CANCELLED, FAILED }
    interface NativeCancel { void cancel(); }
    interface NativeListener { void success(AuthKind kind); void error(NativeError error); }
    interface NativePrompt { void authenticate(NativeCancel cancellation,NativeListener listener); }
    interface Platform {
        void requireOwnerThread();long elapsedMs();boolean foregroundReady();SecurityState securityState();
        NativeCancel newCancellation();NativePrompt buildPrompt(ContinuityState.Confirmation step);
    }
    private static final class AndroidCancel implements NativeCancel {
        final CancellationSignal signal=new CancellationSignal();
        @Override public void cancel(){signal.cancel();}
    }
    private static final class AndroidPlatform implements Platform {
        private final Activity activity;
        AndroidPlatform(Activity activity){if(activity==null)throw invalid();this.activity=activity;}
        @Override public void requireOwnerThread(){if(Looper.myLooper()!=Looper.getMainLooper())throw new IllegalStateException("Android owner UI thread required");}
        @Override public long elapsedMs(){return SystemClock.elapsedRealtime();}
        @Override public boolean foregroundReady(){
            try{KeyguardManager keyguard=activity.getSystemService(KeyguardManager.class);PowerManager power=activity.getSystemService(PowerManager.class);
                return !activity.isFinishing() && !activity.isDestroyed() && activity.hasWindowFocus() && keyguard!=null && !keyguard.isKeyguardLocked() && !keyguard.isDeviceLocked() && power!=null && power.isInteractive();
            }catch(RuntimeException unavailable){return false;}
        }
        @Override public SecurityState securityState(){KeyguardManager keyguard=activity.getSystemService(KeyguardManager.class);return keyguard==null?SecurityState.UNKNOWN:keyguard.isDeviceSecure()?SecurityState.SECURE:SecurityState.NO_LOCK;}
        @Override public NativeCancel newCancellation(){return new AndroidCancel();}
        @Override public NativePrompt buildPrompt(ContinuityState.Confirmation step){
            final BiometricPrompt prompt=new BiometricPrompt.Builder(activity).setTitle("ReBrowser 来源验证")
                .setSubtitle(step==ContinuityState.Confirmation.FORCE_2?"第二次强行使用确认":"第三次强行使用确认")
                .setDescription("验证只用于当前来源和数据代次，不是数据已删除或业务已解锁的回执。")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG|BiometricManager.Authenticators.DEVICE_CREDENTIAL).build();
            return new NativePrompt(){@Override public void authenticate(NativeCancel cancellation,final NativeListener listener){
                if(!(cancellation instanceof AndroidCancel))throw invalid();
                prompt.authenticate(((AndroidCancel)cancellation).signal,activity.getMainExecutor(),new BiometricPrompt.AuthenticationCallback(){
                    @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result){
                        AuthKind kind=AuthKind.UNKNOWN;
                        if(result!=null)try{int type=result.getAuthenticationType();kind=type==BiometricPrompt.AUTHENTICATION_RESULT_TYPE_BIOMETRIC?AuthKind.BIOMETRIC:type==BiometricPrompt.AUTHENTICATION_RESULT_TYPE_DEVICE_CREDENTIAL?AuthKind.DEVICE_CREDENTIAL:AuthKind.UNKNOWN;}
                        catch(RuntimeException unavailableType){/* actual terminal event still maps to failed, never stuck/pseudo-success */}
                        listener.success(kind);
                    }
                    @Override public void onAuthenticationError(int code,CharSequence ignoredSystemText){listener.error(code==BiometricPrompt.BIOMETRIC_ERROR_CANCELED || code==BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED?NativeError.CANCELLED:NativeError.FAILED);}
                    // Failed biometric attempt is not terminal; system UI owns further attempts.
                });
            }};
        }
    }
    public static final class Binding {
        private final byte[] epoch;
        private final String signer,root,challenge;
        private final ContinuityState.Confirmation step;
        private boolean started,valid=true;
        /** Native current source/epoch only, NEVER decode this binding from administrator input. */
        public Binding(byte[] nativeOldEpoch,String nativeCandidateSigner,String nativeCandidateRoot,ContinuityState.Confirmation step){
            if(nativeOldEpoch==null || nativeOldEpoch.length!=16 || !fingerprint(nativeCandidateSigner) || !fingerprint(nativeCandidateRoot)
                    || (step!=ContinuityState.Confirmation.FORCE_2 && step!=ContinuityState.Confirmation.FORCE_3))throw invalid();
            epoch=nativeOldEpoch.clone();signer=nativeCandidateSigner;root=nativeCandidateRoot;this.step=step;challenge=randomChallenge();
        }
        public byte[] oldEpoch(){return epoch.clone();}
        public String candidateSignerSha256(){return signer;}
        public String candidateRootSha256(){return root;}
        public String challengeRef(){return challenge;}
        public ContinuityState.Confirmation confirmation(){return step;}
    }
    public static final class Result {
        private final Outcome outcome;
        private final Ticket ticket;
        private Result(Outcome outcome,Ticket ticket){this.outcome=outcome;this.ticket=ticket;}
        public Outcome outcome(){return outcome;}
        public Ticket ticket(){if(ticket==null)throw new IllegalStateException("No Android owner verification ticket");return ticket;}
        /** UNKNOWN means cancellation requested, not proof the OS prompt has ended. */
        public boolean hasTicket(){return ticket!=null;}
    }
    /** Private native capability, single use, same Binding/window and <=30 seconds old. */
    public static final class Ticket {
        private final OwnerAuthentication issuer;
        private final Binding binding;
        private final Evidence evidence;
        private final long issued;
        private boolean consumed;
        private Ticket(OwnerAuthentication issuer,Binding binding,Evidence evidence){this.issuer=issuer;this.binding=binding;this.evidence=evidence;issued=issuer.platform.elapsedMs();if(issued<0)throw new IllegalStateException("Owner verification clock unavailable");}
        /** Wait for own window foreground after OS dialog dismiss; never treat callback as consent to delete. */
        public Evidence consume(Binding expectedCurrentNativeBinding){
            issuer.mainThread();if(consumed)throw new SecurityException("Owner verification already consumed");consumed=true;
            long now=issuer.platform.elapsedMs();
            if(expectedCurrentNativeBinding!=binding || !binding.valid || issuer.closed || now<issued || now-issued>30_000 || !issuer.foregroundReady())throw new SecurityException("Stale or wrong owner verification binding");
            if(evidence==Evidence.OS_CONFIRMED_NO_LOCK && issuer.platform.securityState()!=SecurityState.NO_LOCK)throw new SecurityException("Screen-lock state changed");
            return evidence; // caller still rechecks actual signer/Root/epoch/flow step before irreversible action
        }
    }
    private static final class Attempt {
        final Binding binding;
        final Callback callback;
        final NativeCancel signal;
        boolean cancellationRequested,terminal,submitted;
        Attempt(Binding binding,Callback callback,NativeCancel signal){this.binding=binding;this.callback=callback;this.signal=signal;}
    }
    private final Activity activity;
    private final Platform platform;
    private final Object nativeWindow=new Object();
    private ContinuityCoordinator.OwnerAttempt continuityAttempt;
    private Binding continuityBinding;
    private Ticket continuityTicket;
    private ContinuityListener continuityListener;
    public interface ContinuityListener { void stateChanged(); }
    private Attempt active;
    private boolean closed,starting;
    public OwnerAuthentication(Activity actualOwnedActivity){this(actualOwnedActivity,new AndroidPlatform(actualOwnedActivity));}
    private OwnerAuthentication(Activity activity,Platform platform){if(platform==null)throw invalid();platform.requireOwnerThread();this.activity=activity;this.platform=platform;}
    private OwnerAuthentication(Platform syntheticPlatform){this(null,syntheticPlatform);}
    /** Native source UI factory; metadata/context must come from bootstrap, NEVER caller extras. */
    public ContinuityCoordinator newContinuityFlow(ContinuityCoordinator.Context context,ContinuityCoordinator.CurrentSourceGuard nativeGuard)throws SigningIdentity.Unavailable{
        mainThread();if(closed || active!=null || continuityAttempt!=null)throw new IllegalStateException("Owner verification already owned");
        if(context==null || nativeGuard==null)throw invalid();CompiledSourceIdentity compiled=CompiledSourceIdentity.current();
        if(!compiled.configured() || !compiled.requireRootSpkiSha256().equals(context.candidateRootSha256()))throw new SecurityException("Formal source Root unavailable or changed");
        if(activity==null)throw new SecurityException("Actual Android source inspection required");
        SigningIdentity.Identity actual=SigningIdentity.inspectOwn(activity.getApplication());
        if(!actual.currentCertificateSha256().equals(context.candidateSignerSha256()))throw new SecurityException("Own installed signer changed");
        LastGoodSourceStore.SourceEpoch old=context.nativeBaselineView().record().lastGood();
        if(compiled.rootMatchesLastGood(old.rootSha256()) && actual.acceptsLastGoodSigner(old.signingSha256()))throw new SecurityException("Correct source requires native rescue, not destructive warnings");
        nativeGuard.checkCurrent(context);
        return new ContinuityCoordinator(context,nativeWindow,nativeGuard,new ContinuityCoordinator.Clock(){@Override public long elapsedMs(){return platform.elapsedMs();}});
    }
    /** Reserve before real OS call. WAIT_FOREGROUND keeps this same unstarted attempt/binding. */
    public Start beginContinuityVerification(ContinuityCoordinator.OwnerAttempt expected,ContinuityListener listener){
        mainThread();if(closed || expected==null || listener==null || !expected.windowMatches(nativeWindow))throw invalid();
        try{expected.checkBeforeNativeBegin();}
        catch(RuntimeException stale){
            // A positively unstarted binding owns no OS request. Never apply this to unknown/submitted work.
            if(continuityAttempt==expected && !continuityBinding.started){continuityBinding.valid=false;expected.nativeTerminal(false);clearContinuitySlot();}
            throw stale;
        }
        if(continuityAttempt==null){
            if(active!=null)throw new IllegalStateException("Owner verification already pending");
            continuityAttempt=expected;ContinuityCoordinator.Context context=expected.context();
            continuityBinding=new Binding(context.oldEpoch(),context.candidateSignerSha256(),context.candidateRootSha256(),expected.step());continuityListener=listener;
        }else if(continuityAttempt!=expected || continuityListener!=listener)throw new IllegalStateException("Different native owner attempt or observer");
        if(!foregroundReady())return Start.WAIT_FOREGROUND;
        Binding original=continuityBinding;expected.nativeWorkMayStart();
        try{
            Start started=begin(original,result->receiveContinuityResult(expected,original,result));
            if(started==Start.WAIT_FOREGROUND && !original.started)expected.nativeNoSubmissionYet();
            if(started==Start.SUBMISSION_UNKNOWN)expected.nativeSubmissionUnknown();return started;
        }catch(RuntimeException failure){
            if(active!=null && active.binding==original)expected.nativeSubmissionUnknown();
            else{expected.nativeTerminal(false);if(continuityAttempt==expected && continuityTicket==null)clearContinuitySlot();}
            throw failure;
        }
    }
    /** Call after own foreground returns; a raw OS callback is not consent to delete. */
    public boolean tryConsumeContinuityVerification(){
        mainThread();if(continuityAttempt==null || continuityTicket==null)return false;
        ContinuityCoordinator.OwnerAttempt expected=continuityAttempt;Binding binding=continuityBinding;ContinuityListener listener=continuityListener;
        // A held private ticket already has an observed terminal OS result; retirement may discard it without new foreground.
        if(closed || expected.isFlowRetired()){binding.valid=false;clearContinuitySlot();return false;}
        if(!foregroundReady())return false;
        try{
            if(closed || !expected.windowMatches(nativeWindow))throw new SecurityException("Owner window changed");
            expected.checkBeforeNativeTicketConsume();
            if(continuityTicket.issuer!=this || continuityTicket.binding!=binding)throw new SecurityException("Wrong native verification ticket");
            continuityTicket.consume(binding);expected.verifiedAfterNativeTicketConsume();binding.valid=false;clearContinuitySlot();
        }catch(RuntimeException stale){
            expected.retireFlow();binding.valid=false;clearContinuitySlot();
            try{listener.stateChanged();}catch(RuntimeException publish){stale.addSuppressed(publish);}throw stale;
        }
        listener.stateChanged();return true;
    }
    public boolean hasPendingContinuityVerification(){mainThread();return continuityAttempt!=null;}
    private void receiveContinuityResult(ContinuityCoordinator.OwnerAttempt expected,Binding binding,Result result){
        mainThread();
        if(continuityAttempt!=expected || continuityBinding!=binding){expected.retireFlow();if(result.outcome!=Outcome.SUBMISSION_UNKNOWN)expected.nativeTerminal(false);return;}
        ContinuityListener listener=continuityListener;
        if(result.outcome==Outcome.SUBMISSION_UNKNOWN){expected.nativeSubmissionUnknown();listener.stateChanged();return;}
        if(result.hasTicket() && !expected.isFlowRetired()){continuityTicket=result.ticket();expected.nativeTerminal(true);}
        else{expected.nativeTerminal(false);binding.valid=false;clearContinuitySlot();}
        listener.stateChanged(); // thrown publication does not retry/release a still-owned ticket or OS request
    }
    private void clearContinuitySlot(){continuityAttempt=null;continuityBinding=null;continuityTicket=null;continuityListener=null;}
    public Start begin(Binding binding,Callback callback){
        mainThread();if(closed || starting || active!=null || (continuityAttempt!=null && binding!=continuityBinding))throw new IllegalStateException("Owner authentication unavailable or already pending");
        if(binding==null || callback==null || binding.started || !binding.valid)throw invalid();
        if(!foregroundReady())return Start.WAIT_FOREGROUND;
        NativeCancel cancellation=platform.newCancellation();if(cancellation==null)throw new IllegalStateException("Native cancellation unavailable");
        starting=true;binding.started=true;Attempt attempt=new Attempt(binding,callback,cancellation);active=attempt;
        try{
            SecurityState security=platform.securityState();
            if(security==null || security==SecurityState.UNKNOWN){finish(attempt,Outcome.UNAVAILABLE,null);return Start.TERMINAL_DELIVERED;}
            if(security==SecurityState.NO_LOCK){
                finish(attempt,Outcome.NO_LOCK_CONFIGURED,new Ticket(this,binding,Evidence.OS_CONFIRMED_NO_LOCK));return Start.TERMINAL_DELIVERED;
            }
            final NativePrompt prompt;
            try{prompt=platform.buildPrompt(binding.step);if(prompt==null)throw new IllegalStateException("Native prompt unavailable");}
            catch(RuntimeException beforeSubmission){finish(attempt,Outcome.UNAVAILABLE,null);return Start.TERMINAL_DELIVERED;}
            attempt.submitted=true;
            try{
                prompt.authenticate(attempt.signal,new NativeListener(){
                    @Override public void success(AuthKind kind){
                        mainThread();if(active!=attempt || attempt.terminal)return;
                        if(kind!=AuthKind.BIOMETRIC && kind!=AuthKind.DEVICE_CREDENTIAL){finish(attempt,Outcome.FAILED,null);return;}
                        final Ticket ticket;
                        try{ticket=new Ticket(OwnerAuthentication.this,attempt.binding,Evidence.SYSTEM_AUTHENTICATED);}
                        catch(RuntimeException unavailableClock){finish(attempt,Outcome.FAILED,null);return;} // actual terminal callback still settles owner
                        finish(attempt,Outcome.VERIFIED,ticket);
                    }
                    @Override public void error(NativeError error){mainThread();finish(attempt,attempt.cancellationRequested || error==NativeError.CANCELLED?Outcome.CANCELLED:Outcome.FAILED,null);}
                });
                return active==attempt?Start.PROMPT_SUBMITTED:Start.TERMINAL_DELIVERED;
            }catch(RuntimeException unknownSubmission){
                if(active!=attempt)return Start.TERMINAL_DELIVERED;
                attempt.cancellationRequested=true;attempt.signal.cancel();
                // Keep actual pending ownership until a real terminal OS callback, not a timeout.
                if(active==attempt)callback.result(new Result(Outcome.SUBMISSION_UNKNOWN,null));
                return active==attempt?Start.SUBMISSION_UNKNOWN:Start.TERMINAL_DELIVERED;
            }
        }catch(RuntimeException unavailable){
            if(active==attempt){
                if(attempt.submitted)return Start.SUBMISSION_UNKNOWN; // no invented terminal receipt if cancel/callback itself threw
                finish(attempt,Outcome.UNAVAILABLE,null);return Start.TERMINAL_DELIVERED;
            }
            throw unavailable;
        }finally{starting=false;}
    }
    /** Stops eligibility immediately, but cancellation request is not actual OS completion. */
    public void cancel(){
        mainThread();
        if(continuityAttempt!=null){continuityAttempt.retireFlow();continuityBinding.valid=false;if(active==null || active.binding!=continuityBinding)clearContinuitySlot();}
        if(active!=null){active.binding.valid=false;active.cancellationRequested=true;active.signal.cancel();}
    }
    public void invalidate(Binding binding){
        mainThread();if(binding==null)throw invalid();binding.valid=false;
        if(binding==continuityBinding && continuityAttempt!=null){continuityAttempt.retireFlow();if(active==null || active.binding!=binding)clearContinuitySlot();}
        if(active!=null && active.binding==binding)cancel();
    }
    public void close(){mainThread();closed=true;cancel();}
    public boolean hasPendingOsRequest(){mainThread();return active!=null;}
    private void finish(Attempt attempt,Outcome outcome,Ticket ticket){
        mainThread();if(active!=attempt || attempt.terminal)return;
        if(closed || attempt.cancellationRequested || !attempt.binding.valid){outcome=Outcome.CANCELLED;ticket=null;}
        attempt.terminal=true;active=null;attempt.callback.result(new Result(outcome,ticket));
    }
    private boolean foregroundReady(){try{return platform.foregroundReady();}catch(RuntimeException unavailable){return false;}}
    private void mainThread(){platform.requireOwnerThread();}
    private static boolean fingerprint(String value){return value!=null && value.matches("[0-9a-f]{64}");}
    private static String randomChallenge(){byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);StringBuilder b=new StringBuilder(64);for(byte value:bytes){b.append("0123456789abcdef".charAt((value&255)>>>4));b.append("0123456789abcdef".charAt(value&15));}return b.toString();}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid native owner authentication binding");}
}
