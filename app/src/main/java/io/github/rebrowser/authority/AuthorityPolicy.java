package io.github.rebrowser.authority;

import io.github.rebrowser.bootstrap.ComponentGate;
import io.github.rebrowser.control.*;
import io.github.rebrowser.core.ActorContext;
import java.security.MessageDigest;
import java.util.function.LongSupplier;

/** Current compiled-identity WINDOW reservation policy. Not low-chain admission, business data,
 * whitelist bypass, an effect dispatcher or a replacement for Android/site permission.
 * A signature/verified historical ledger alone cannot mint ActorContext: the native current head,
 * source/epoch, exact parsed request, registered target and live SPENT challenge fence all gate it.
 * No production adapter is installed until a real durable current-policy owner exists. */
public final class AuthorityPolicy {
    public interface NativeCurrent {
        /** Nonblocking read of native-owned verified cache. No IPC DTOs, slow IO or mutation. */
        DisableLedger.Snapshot currentDisableHead();
        /** Verify exact snapshot remains the native durable/current head; no latest-from-AEAD claim. */
        void requireCurrentHead(DisableLedger.Snapshot exact);
        /** Native registered WINDOW ownership/revision/scope, not merely a matching string. */
        void requireCurrentTarget(ActionSchemas.ParsedAction exactAction);
    }
    public interface Use<T>{T run(ActorContext actor)throws Exception;}
    /** Private-issued ephemeral capability. Every access rechecks the live reservation fence. */
    public static final class Checked {
        private final AuthorityPolicy issuer;private final ControlChallengeStore.CommitFence fence;private final ActionSchemas.ParsedAction action;private final Thread thread=Thread.currentThread();private boolean active=true;
        private Checked(AuthorityPolicy issuer,ControlChallengeStore.CommitFence fence,ActionSchemas.ParsedAction action){this.issuer=issuer;this.fence=fence;this.action=action;}
        public void requireCurrent(){if(!active || Thread.currentThread()!=thread)throw denied();fence.checkCurrentUnchecked();issuer.check(fence.proof(),action);}
        public AdminProofVerifier.KeyProof proof(){requireCurrent();return fence.proof();}
        public ActionSchemas.ParsedAction action(){requireCurrent();return action;}
    }
    private final CompiledPrincipals compiled;private final ComponentGate.DataEpochPin source;private final byte[] target,policy;private final NativeCurrent current;private final LongSupplier clock;private long last=-1;private boolean checking,closed,using;
    public AuthorityPolicy(CompiledPrincipals compiled,ComponentGate.DataEpochPin source,byte[] nativeTargetInstallSpkiSha256,NativeCurrent nativeCurrent,LongSupplier elapsedRealtime){
        if(compiled==null || source==null || nativeTargetInstallSpkiSha256==null || nativeTargetInstallSpkiSha256.length!=32 || nativeCurrent==null || elapsedRealtime==null)throw new IllegalArgumentException("Native current-policy binding required");source.requireCurrent();this.compiled=compiled;this.source=source;target=nativeTargetInstallSpkiSha256.clone();policy=ActionRegistry.policySha256(compiled);current=nativeCurrent;clock=elapsedRealtime;
    }
    /** Safe repeatable read-check used before nonce spend and INSIDE the native commit fence. */
    public synchronized void check(AdminProofVerifier.KeyProof proof,ActionSchemas.ParsedAction action){
        if(checking){closed=true;throw denied();}checking=true;
        try{source.requireCurrent();if(closed || !compiled.formalRootConfigured() || proof==null || !proof.belongsTo(compiled) || action==null || action.unverifiedRequest()!=proof.request())throw denied();
            ControlEnvelope f=proof.request().frame();ActionRegistry.Rule rule=ActionRegistry.require(f.ascii(ControlEnvelope.Field.ACTION_ID));
            if(rule.kind()!=action.kind() || rule.needsOwnerGrant() || !f.ascii(ControlEnvelope.Field.SCOPE_REF).equals(action.windowRef()) || !MessageDigest.isEqual(target,f.bytes(ControlEnvelope.Field.TARGET_INSTALL_SPKI_SHA256)) || !MessageDigest.isEqual(source.dataEpoch(),f.bytes(ControlEnvelope.Field.DATA_EPOCH)) || !MessageDigest.isEqual(policy,f.bytes(ControlEnvelope.Field.COMPILED_POLICY_SHA256)))throw denied();
            if(now()>=f.unsigned63(ControlEnvelope.Field.DEADLINE_ELAPSED_MS))throw denied();
            DisableLedger.Snapshot head=current.currentDisableHead();if(head==null || !head.belongsTo(compiled) || !head.matchesNativeBinding(target,source.dataEpoch()) || head.revision()!=f.unsigned63(ControlEnvelope.Field.DISABLE_REVISION) || head.disabled(proof.compiledPrincipal().keyId()) || !AuthorityScale.meetsThreshold(proof.compiledPrincipal().level(),rule.requiredLevel()))throw denied();
            current.requireCurrentHead(head);current.requireCurrentTarget(action);current.requireCurrentHead(head);source.requireCurrent();if(now()>=f.unsigned63(ControlEnvelope.Field.DEADLINE_ELAPSED_MS))throw denied();
        }catch(RuntimeException unavailable){throw denied();}finally{checking=false;}
    }
    /** Callback lifetime only. No Actor can be retained as authority beyond this exact reservation. */
    public synchronized <T>T withReservationActor(ControlChallengeStore.CommitFence fence,ActionSchemas.ParsedAction action,Use<T> use)throws Exception{
        if(using || fence==null || use==null)throw denied();using=true;Checked checked=null;
        try{fence.checkCurrent();check(fence.proof(),action);checked=new Checked(this,fence,action);ActorContext actor=ActorContext.forReservation(checked);T result=use.run(actor);checked.requireCurrent();return result;}
        finally{if(checked!=null)checked.active=false;using=false;}
    }
    private long now(){long value;try{value=clock.getAsLong();}catch(RuntimeException unavailable){closed=true;throw denied();}if(value<0 || value<last){closed=true;throw denied();}last=value;return value;}
    public synchronized void close(){closed=true;}
    private static SecurityException denied(){return new SecurityException("Current administrator authority unavailable");}
}
