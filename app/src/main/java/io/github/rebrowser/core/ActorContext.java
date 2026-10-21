package io.github.rebrowser.core;

import io.github.rebrowser.authority.AdminProofVerifier;
import io.github.rebrowser.authority.AuthorityPolicy;
import io.github.rebrowser.control.ActionSchemas;
import io.github.rebrowser.control.ControlEnvelope;
import java.security.MessageDigest;

/** Actual current ADMINISTRATOR RESERVATION slice, not a caller-selectable identity enum.
 * Issued only from a private live AuthorityPolicy.Checked during a SPENT challenge reservation.
 * No setters, public DTO constructor, persisted/IPC reconstruction or effect-dispatch capability.
 * User/system-media actors remain separate future native issuers, never this admin proof path. */
public final class ActorContext {
    private final AuthorityPolicy.Checked authority;
    private ActorContext(AuthorityPolicy.Checked authority){this.authority=authority;}
    public static ActorContext forReservation(AuthorityPolicy.Checked authority){if(authority==null)throw denied();authority.requireCurrent();return new ActorContext(authority);}
    public void requireCurrentReservation(){authority.requireCurrent();}
    public String principalFingerprint(){return authority.proof().compiledPrincipal().spkiSha256();}
    public int effectiveOrdinaryLevel(){return authority.proof().compiledPrincipal().level();}
    public ActionSchemas.ParsedAction exactAction(){return authority.action();}
    /** An Actor for another proof/target/epoch/action cannot authorize this ledger binding. */
    public void requireExactRequest(AdminProofVerifier.KeyProof exact){
        if(exact==null || authority.proof()!=exact)throw denied();requireCurrentReservation();
    }
    public void requireNativeBinding(byte[] nativeTarget,byte[] nativeEpoch){
        ControlEnvelope f=authority.proof().request().frame();if(nativeTarget==null || nativeEpoch==null || !MessageDigest.isEqual(nativeTarget,f.bytes(ControlEnvelope.Field.TARGET_INSTALL_SPKI_SHA256)) || !MessageDigest.isEqual(nativeEpoch,f.bytes(ControlEnvelope.Field.DATA_EPOCH)))throw denied();requireCurrentReservation();
    }
    private static SecurityException denied(){return new SecurityException("Wrong or retired native reservation actor");}
}
