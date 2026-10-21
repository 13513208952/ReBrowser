package io.github.rebrowser.control;

import io.github.rebrowser.authority.*;
import io.github.rebrowser.bootstrap.ComponentGate;
import io.github.rebrowser.core.TaskJournal;

/** Source-neutral control RESERVATION coordinator. Concrete dispatch of native/OS effects is NOT
 * installed: a returned job is not success, a handler receipt or permission to skip later current
 * policy checks. Root/policy/ledger/target adapters must genuinely exist before construction/use.
 * No startup/provider/Activity activation and no alternate USB/package/broadcast fallback route. */
public final class ControlDispatcher {
    private final AdminProofVerifier keys;private final ControlChallengeStore challenges;private final AuthorityPolicy policy;private final IdempotencyStore requests;private final TaskJournal tasks;private final AdminAudit audit;
    public ControlDispatcher(AdminProofVerifier keys,ControlChallengeStore challenges,AuthorityPolicy policy,IdempotencyStore nativeRequestLedger,TaskJournal nativeTaskJournal,AdminAudit audit){
        if(keys==null || challenges==null || policy==null || nativeRequestLedger==null || nativeTaskJournal==null || audit==null)throw new IllegalArgumentException("Native control owners required");this.keys=keys;this.challenges=challenges;this.policy=policy;requests=nativeRequestLedger;tasks=nativeTaskJournal;this.audit=audit;
    }
    public IdempotencyStore.Reservation reserve(byte[] untrustedEnvelope,ComponentGate.BusinessPermit nativeCurrentSource)throws Exception{
        ControlDecoder.UnverifiedProof request=ControlDecoder.decode(untrustedEnvelope);
        AdminProofVerifier.KeyProof key=keys.verifyCompiledKey(request); // no low/direct silent fallback
        final ActionSchemas.ParsedAction action=ActionSchemas.parse(request);ActionRegistry.require(action.kind().actionId());
        final boolean[] attempted={false};
        try{
            IdempotencyStore.Reservation result=challenges.admit(key,new ControlChallengeStore.Admission<IdempotencyStore.Reservation>(){
                @Override public void authorize(AdminProofVerifier.KeyProof exact){if(exact!=key)throw new SecurityException("Wrong control proof");policy.check(exact,action);}
                @Override public IdempotencyStore.Reservation reserveJob(ControlChallengeStore.CommitFence fence)throws Exception{
                    attempted[0]=true;
                    return policy.withReservationActor(fence,action,actor->requests.reserveAfterAuthorization(fence,actor,nativeCurrentSource,tasks));
                }
            });
            // Audit trouble after possible commit never permits a second route/automatic retry.
            audit.observe(action.kind(),AdminAudit.Outcome.RESERVATION_RETURNED);return result;
        }catch(Exception failure){try{audit.observe(action.kind(),attempted[0]?AdminAudit.Outcome.RESERVATION_OUTCOME_UNKNOWN:AdminAudit.Outcome.REJECTED_BEFORE_RESERVATION);}catch(RuntimeException ignored){/* no private external diagnostics */}throw failure;}
    }
}
