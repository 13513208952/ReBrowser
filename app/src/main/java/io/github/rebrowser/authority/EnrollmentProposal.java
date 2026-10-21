package io.github.rebrowser.authority;

import io.github.rebrowser.bootstrap.ComponentGate;
import io.github.rebrowser.control.ControlEnvelope;
import io.github.rebrowser.crypto.CryptoEncoding;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.util.*;
import java.util.function.LongSupplier;

/** Native compile-list DRAFT with actual fresh target P256 possession. Highest prepares ONLY
 * its own list; Root may prepare Root/highest lists without enumerating other devices. KeyProof
 * is deliberately NOT current enablement/Actor authority. No actual owner consent collected,
 * key creation, runtime whitelist import, enrollment receipt or automatic compilation/update.
 * Offline maintainer MUST separately validate current authority, exact owner OS consent,
 * fingerprint match and formal signing/provisioning before any APK compiled-list change. */
public final class EnrollmentProposal implements AutoCloseable {
    public static final class Challenge {
        private final EnrollmentProposal issuer;private final ECPublicKey target;private final String owner,preparer;private final byte[] bytes;private final long deadline;private boolean spent;
        private Challenge(EnrollmentProposal issuer,ECPublicKey target,String owner,String preparer,byte[] bytes,long deadline){this.issuer=issuer;this.target=target;this.owner=owner;this.preparer=preparer;this.bytes=bytes;this.deadline=deadline;}
        public byte[] signingBytes(){return bytes.clone();}
        /** Existing native installation signer must check BEFORE and AFTER actual JCA call. */
        public void requireCurrentTarget(byte[] actualInstallKeySha256,byte[] actualDataEpoch){synchronized(issuer){long now=issuer.now();if(spent || !issuer.pending.contains(this) || now>=deadline || actualInstallKeySha256==null || actualDataEpoch==null || !MessageDigest.isEqual(CryptoEncoding.sha256(target.getEncoded()),actualInstallKeySha256) || !MessageDigest.isEqual(issuer.source.dataEpoch(),actualDataEpoch))throw denied();issuer.source.requireCurrent();}}
    }
    public static final class Draft {
        private final byte[] encoded;private final String owner,preparer,targetFingerprint;
        private Draft(Challenge c,byte[] actualSignature){owner=c.owner;preparer=c.preparer;targetFingerprint=CryptoEncoding.sha256Hex(c.target.getEncoded());try{ByteArrayOutputStream b=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(b);out.write("ReBrowser.Enrollment.NOT_OWNER_APPROVED.DRAFT.1\0".getBytes(StandardCharsets.US_ASCII));out.writeInt(c.bytes.length);out.write(c.bytes);out.writeInt(actualSignature.length);out.write(actualSignature);encoded=b.toByteArray();}catch(IOException impossible){throw new AssertionError(impossible);}}
        public byte[] portableDraftBytes(){return encoded.clone();}public String listOwnerKeyId(){return owner;}public String preparerKeyId(){return preparer;}public String proposedInstallSpkiSha256(){return targetFingerprint;}
        public String assurance(){return "TARGET_POSSESSION_ONLY_NO_OWNER_CONSENT_NO_CURRENT_AUTHORITY_NO_ENROLLMENT";}
    }
    private static final byte[] DOMAIN="ReBrowser.Enrollment.Draft.TargetPossession.1\0".getBytes(StandardCharsets.US_ASCII);
    private final CompiledPrincipals compiled;private final ComponentGate.DataEpochPin source;private final LongSupplier elapsed;private final Set<Challenge> pending=Collections.newSetFromMap(new IdentityHashMap<Challenge,Boolean>());private final SecureRandom random=new SecureRandom();private long last=-1;private int issued;private boolean closed;
    public EnrollmentProposal(CompiledPrincipals actualCompiledPolicy,ComponentGate.DataEpochPin nativeSource,LongSupplier nativeElapsedClock){if(actualCompiledPolicy==null || nativeSource==null || nativeElapsedClock==null)throw invalid();compiled=actualCompiledPolicy;source=nativeSource;elapsed=nativeElapsedClock;source.requireCurrent();}
    public synchronized Challenge prepareForNativeReview(AdminProofVerifier.KeyProof preparerKeyProofOnly,String exactListOwnerKeyId,byte[] proposedTargetCanonicalSpki)throws GeneralSecurityException{
        long now=now();prune(now);if(!compiled.formalRootConfigured() || preparerKeyProofOnly==null || !preparerKeyProofOnly.belongsTo(compiled) || issued>=64 || pending.size()>=8 || now>Long.MAX_VALUE-30_000)throw denied();CompiledPrincipals.Entry preparer=preparerKeyProofOnly.compiledPrincipal(),owner=compiled.find(exactListOwnerKeyId);
        if(owner==null || owner.role()!=CompiledPrincipals.Role.ROOT && owner.role()!=CompiledPrincipals.Role.HIGHEST || preparer.role()!=CompiledPrincipals.Role.ROOT && (preparer.role()!=CompiledPrincipals.Role.HIGHEST || preparer!=owner))throw denied();
        if(!MessageDigest.isEqual(source.dataEpoch(),preparerKeyProofOnly.request().frame().bytes(ControlEnvelope.Field.DATA_EPOCH)))throw denied();ECPublicKey target=CryptoEncoding.p256PublicKey(proposedTargetCanonicalSpki);if(!MessageDigest.isEqual(CryptoEncoding.sha256(target.getEncoded()),preparerKeyProofOnly.request().frame().bytes(ControlEnvelope.Field.TARGET_INSTALL_SPKI_SHA256)) || compiled.containsFingerprint(CryptoEncoding.sha256Hex(target.getEncoded())))throw denied();byte[] nonce=new byte[32];random.nextBytes(nonce);long deadline=now+30_000;
        try{ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);out.write(DOMAIN);out.write(compiled.identityPolicySha256());out.write(source.dataEpoch());out.writeUTF(preparer.keyId());out.writeUTF(owner.keyId());out.write(target.getEncoded());out.write(nonce);out.writeLong(deadline);Challenge c=new Challenge(this,target,owner.keyId(),preparer.keyId(),bytes.toByteArray(),deadline);source.requireCurrent();issued++;pending.add(c);return c;}catch(IOException impossible){throw new AssertionError(impossible);}
    }
    /** Spend before clock/DER/signature processing. Signature is cryptography, NOT owner approval. */
    public synchronized Draft verifyTargetPossessionForDraft(Challenge c,byte[] signature)throws GeneralSecurityException{
        if(c==null || c.issuer!=this || c.spent || !pending.remove(c))throw denied();c.spent=true;long now=now();if(now>=c.deadline)throw denied();if(signature==null || signature.length>72)throw invalid();byte[] original=signature.clone();CryptoEncoding.validateP256DerSignature(original);Signature verifier=Signature.getInstance("SHA256withECDSA");verifier.initVerify(c.target);verifier.update(c.bytes);if(!verifier.verify(original))throw denied();source.requireCurrent();return new Draft(c,original);
    }
    private void prune(long now){Iterator<Challenge> i=pending.iterator();while(i.hasNext()){Challenge c=i.next();if(now>=c.deadline){c.spent=true;i.remove();}}}
    private long now(){source.requireCurrent();long n;try{n=elapsed.getAsLong();}catch(RuntimeException unknown){close();throw denied();}if(closed || n<0 || n<last){close();throw denied();}last=n;return n;}
    @Override public synchronized void close(){closed=true;for(Challenge c:pending)c.spent=true;pending.clear();}
    private static SecurityException denied(){return new SecurityException("Native enrollment DRAFT unavailable");}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid native enrollment draft");}
}
