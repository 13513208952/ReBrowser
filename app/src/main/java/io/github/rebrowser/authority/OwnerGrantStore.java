package io.github.rebrowser.authority;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.CancellationSignal;
import android.os.Looper;
import android.os.SystemClock;
import io.github.rebrowser.bootstrap.ComponentGate;
import io.github.rebrowser.control.ActionRegistry;
import io.github.rebrowser.control.ActionSchemas;
import io.github.rebrowser.control.ControlEnvelope;
import io.github.rebrowser.crypto.CryptoEncoding;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;

/** Business grants OWN their local consent + current OS authentication. No caller boolean,
 * continuity FORCE ticket, old prompt result or whitelist acts as consent. Unactivated component:
 * constructing it opens no dialog, authenticates nobody and creates no keys or grants.
 * Actual Android callback/foreground/cancellation qualification remains a separate test gate. */
public final class OwnerGrantStore {
    public interface Listener {void completed(Grant grant);void unavailable();}
    public static final class Grant {
        private final OwnerGrantStore issuer;private final String ref,principal,scope,action;private final byte[] target,epoch;private final long revision,deadline;private final boolean noLock;
        private Grant(OwnerGrantStore issuer,String ref,AdminProofVerifier.KeyProof proof,long revision,long deadline,boolean noLock){this.noLock=noLock;this.issuer=issuer;this.ref=ref;this.revision=revision;this.deadline=deadline;principal=proof.compiledPrincipal().spkiSha256();ControlEnvelope f=proof.request().frame();scope=f.ascii(ControlEnvelope.Field.SCOPE_REF);action=f.ascii(ControlEnvelope.Field.ACTION_ID);target=f.bytes(ControlEnvelope.Field.TARGET_INSTALL_SPKI_SHA256);epoch=f.bytes(ControlEnvelope.Field.DATA_EPOCH);}
        public String reference(){return ref;}public long revision(){return revision;}
    }
    private static final class Attempt {
        final AdminProofVerifier.KeyProof proof;final Listener listener;final long started;
        boolean consent,mayHaveSubmitted,terminal,notified,osEnded,authenticated,noLock;CancellationSignal cancel;AlertDialog dialog;
        Attempt(AdminProofVerifier.KeyProof proof,Listener listener,long started){this.proof=proof;this.listener=listener;this.started=started;}
    }
    private final AuthorityPolicy policy;private final CompiledPrincipals compiled;private final ComponentGate.DataEpochPin source;private final Activity activity;private final Map<String,Grant> grants=new LinkedHashMap<>();private final SecureRandom random=new SecureRandom();private long revision,lastTime=-1;private boolean closed;private Attempt active;
    public OwnerGrantStore(Activity nativeOwnedActivity,CompiledPrincipals compiled,ComponentGate.DataEpochPin source,AuthorityPolicy nativePolicy){if(nativeOwnedActivity==null || compiled==null || source==null || nativePolicy==null)throw invalid();activity=nativeOwnedActivity;this.compiled=compiled;this.source=source;policy=nativePolicy;main();}
    public void request(AdminProofVerifier.KeyProof proof,Listener listener){
        main();check();if(listener==null || proof==null || !proof.belongsTo(compiled) || !compiled.formalRootConfigured() || active!=null)throw denied();
        ActionRegistry.require(proof.request().frame().ascii(ControlEnvelope.Field.ACTION_ID));policy.check(proof,ActionSchemas.parse(proof.request()));if(!MessageDigest.isEqual(source.dataEpoch(),proof.request().frame().bytes(ControlEnvelope.Field.DATA_EPOCH)))throw denied();
        prune();if(grants.size()>=32 || revision==Long.MAX_VALUE)throw denied();final Attempt a=new Attempt(proof,listener,now());active=a;
        try{a.dialog=new AlertDialog.Builder(activity).setTitle("ReBrowser 本机授权").setMessage("动作："+proof.request().frame().ascii(ControlEnvelope.Field.ACTION_ID)+"\n范围："+proof.request().frame().ascii(ControlEnvelope.Field.SCOPE_REF)+"\n目标SHA256（base64url）："+CryptoEncoding.base64Url(proof.request().frame().bytes(ControlEnvelope.Field.TARGET_INSTALL_SPKI_SHA256))+"\n管理员密钥："+proof.compiledPrincipal().spkiSha256().substring(0,12)+"\n最长五分钟；不改变身份，不代替 Android 或网站许可。")
            .setPositiveButton("授权",(dialog,which)->beginOs(a)).setNegativeButton("取消",(dialog,which)->cancelConsent(a)).setOnCancelListener(dialog->cancelConsent(a)).create();a.dialog.show();}
        catch(RuntimeException unknown){closed=true;grants.clear();notifyUnavailable(a);} // Actual dialog work may exist; retain exact attempt, no retry.
    }
    private void beginOs(final Attempt a){
        main();if(active!=a || a.terminal || a.consent)return;a.consent=true;
        try{check();if(now()-a.started>=30_000)throw denied();KeyguardManager lock=activity.getSystemService(KeyguardManager.class);if(lock==null)throw denied();
            if(!lock.isDeviceSecure()){a.noLock=true;recordPositive(a);return;} // Actual positive consent + current OS NO_LOCK only.
            a.cancel=new CancellationSignal();BiometricPrompt prompt=new BiometricPrompt.Builder(activity).setTitle("ReBrowser 本机授权验证").setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG|BiometricManager.Authenticators.DEVICE_CREDENTIAL).build();
            a.mayHaveSubmitted=true; // BEFORE crossing OS boundary, not inferred from Java return.
            prompt.authenticate(a.cancel,activity.getMainExecutor(),new BiometricPrompt.AuthenticationCallback(){
                @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result){
                    boolean known=false;try{known=result!=null && (result.getAuthenticationType()==BiometricPrompt.AUTHENTICATION_RESULT_TYPE_BIOMETRIC || result.getAuthenticationType()==BiometricPrompt.AUTHENTICATION_RESULT_TYPE_DEVICE_CREDENTIAL);}catch(RuntimeException unavailable){}
                    if(known)recordPositive(a);else terminalFailure(a);
                }
                @Override public void onAuthenticationError(int code,CharSequence ignored){terminalFailure(a);}
            });
        }catch(RuntimeException unavailable){if(a.terminal)return;if(a.mayHaveSubmitted){closed=true;grants.clear();notifyUnavailable(a);}else terminalFailure(a);}
    }
    private void cancelConsent(Attempt a){if(!a.consent)terminalFailure(a);}
    private void recordPositive(Attempt a){main();if(active!=a || a.terminal)return;a.osEnded=true;a.authenticated=true;tryCompleteFromForeground();}
    /** Call from the OWN native Activity after the actual OS dialog returns focus; never a bool result. */
    public void tryCompleteFromForeground(){main();Attempt a=active;if(a==null || !a.osEnded || !a.authenticated || !activity.hasWindowFocus())return;terminalSuccess(a);}
    private void terminalSuccess(Attempt a){
        main();if(active!=a || a.terminal)return;Grant grant=null;
        try{check();policy.check(a.proof,ActionSchemas.parse(a.proof.request()));prune();long time=now();if(!a.osEnded || !a.authenticated || !a.consent || time-a.started>=30_000 || grants.size()>=32 || revision==Long.MAX_VALUE || time>Long.MAX_VALUE-300_000)throw denied();
            String ref=null;for(int tries=0;tries<16;tries++){byte[] id=new byte[16];random.nextBytes(id);StringBuilder s=new StringBuilder("og_");for(byte b:id)s.append(Character.forDigit((b&255)>>>4,16)).append(Character.forDigit(b&15,16));if(!grants.containsKey(s.toString())){ref=s.toString();break;}}if(ref==null)throw denied();
            if(a.noLock){KeyguardManager lock=activity.getSystemService(KeyguardManager.class);if(lock==null || lock.isDeviceSecure())throw denied();}grant=new Grant(this,ref,a.proof,++revision,time+300_000,a.noLock);grants.put(ref,grant);
        }catch(RuntimeException stale){/* terminal OS event is real, but no positive grant */}
        a.terminal=true;active=null;a.dialog=null;a.cancel=null;if(grant==null)notifyUnavailable(a);else if(!a.notified){a.notified=true;a.listener.completed(grant);}
    }
    private void terminalFailure(Attempt a){main();if(active!=a || a.terminal)return;a.terminal=true;active=null;a.dialog=null;a.cancel=null;notifyUnavailable(a);}
    private void notifyUnavailable(Attempt a){if(!a.notified){a.notified=true;a.listener.unavailable();}}
    /** Exact five-minute binding; not an identity-level increase or generic future consent. */
    public void checkCurrent(Grant grant,AdminProofVerifier.KeyProof proof){main();check();prune();if(proof!=null)policy.check(proof,ActionSchemas.parse(proof.request()));if(grant==null || proof==null || grant.issuer!=this || grants.get(grant.ref)!=grant || !proof.belongsTo(compiled))throw denied();if(grant.noLock){KeyguardManager lock=activity.getSystemService(KeyguardManager.class);if(lock==null || lock.isDeviceSecure())throw denied();}ControlEnvelope f=proof.request().frame();if(!grant.ref.equals(f.ascii(ControlEnvelope.Field.OWNER_GRANT_REF)) || !grant.principal.equals(proof.compiledPrincipal().spkiSha256()) || !grant.action.equals(f.ascii(ControlEnvelope.Field.ACTION_ID)) || !grant.scope.equals(f.ascii(ControlEnvelope.Field.SCOPE_REF)) || !MessageDigest.isEqual(grant.target,f.bytes(ControlEnvelope.Field.TARGET_INSTALL_SPKI_SHA256)) || !MessageDigest.isEqual(grant.epoch,f.bytes(ControlEnvelope.Field.DATA_EPOCH)))throw denied();}
    public void revoke(Grant exact){main();if(exact==null || exact.issuer!=this)throw denied();grants.remove(exact.ref,exact);}
    public void close(){main();closed=true;grants.clear();Attempt a=active;if(a!=null){if(a.dialog!=null)a.dialog.dismiss();if(a.cancel!=null)a.cancel.cancel();}} // Requesting cancel is NOT actual prompt end.
    private void prune(){long time=now();Iterator<Grant> i=grants.values().iterator();while(i.hasNext())if(time>=i.next().deadline)i.remove();}
    private long now(){long time=SystemClock.elapsedRealtime();if(time<0 || time<lastTime){closed=true;grants.clear();throw denied();}lastTime=time;return time;}
    private void check(){main();source.requireCurrent();if(closed || activity.isFinishing() || activity.isDestroyed() || !activity.hasWindowFocus())throw denied();now();}
    private static void main(){if(Looper.myLooper()!=Looper.getMainLooper())throw new IllegalStateException("Native owner UI thread required");}
    private static SecurityException denied(){return new SecurityException("Owner grant unavailable or stale");}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid native owner grant binding");}
}
