package io.github.rebrowser.authority;

import io.github.rebrowser.control.ControlDecoder;
import io.github.rebrowser.control.ControlEnvelope;
import io.github.rebrowser.crypto.CryptoEncoding;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.Signature;

/**
 * Source-neutral key-possession check against immutable compiled identities.
 * NOT effective authority/admission: disable/delegation, target/source/epoch/policy,
 * fresh stored challenge/deadline/nonce/grant/action schema must still be checked.
 * No caller package, APK signature or private-key extractability is consulted.
 */
public final class AdminProofVerifier {
    private final CompiledPrincipals compiled;
    public AdminProofVerifier(CompiledPrincipals compiled) {
        if (compiled == null) throw new IllegalArgumentException("Compiled policy required");
        this.compiled = compiled;
    }
    public static final class KeyProof {
        private final CompiledPrincipals.Entry principal;
        private final ControlDecoder.UnverifiedProof request;
        private KeyProof(CompiledPrincipals.Entry principal, ControlDecoder.UnverifiedProof request) {
            this.principal = principal; this.request = request;
        }
        public CompiledPrincipals.Entry compiledPrincipal() { return principal; }
        public ControlDecoder.UnverifiedProof request() { return request; }
    }
    public KeyProof verifyCompiledKey(ControlDecoder.UnverifiedProof request) throws GeneralSecurityException {
        if (request == null) throw invalid();
        // Low-delegation certificate validation is a distinct path, never silently direct.
        if (request.delegation().length != 0 || !MessageDigest.isEqual(
                request.frame().bytes(ControlEnvelope.Field.DELEGATION_SHA256), new byte[32])) throw invalid();
        CompiledPrincipals.Entry principal = compiled.find(request.frame().ascii(ControlEnvelope.Field.PRINCIPAL_KEY_ID));
        if (principal == null) throw invalid();
        try {
            byte[] signature = request.signature();
            CryptoEncoding.validateP256DerSignature(signature);
            Signature verifier = Signature.getInstance("SHA256withECDSA");
            verifier.initVerify(principal.publicKey());
            verifier.update(request.frame().signedBytes());
            if (!verifier.verify(signature)) throw invalid();
            return new KeyProof(principal, request);
        } catch (GeneralSecurityException | IllegalArgumentException failure) {
            // No raw args, key identity, signatures or scopes in external diagnostics.
            throw invalid();
        }
    }
    private static GeneralSecurityException invalid() { return new GeneralSecurityException("Invalid administrator proof"); }
}
