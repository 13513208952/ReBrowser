package io.github.rebrowser.authority;

import io.github.rebrowser.bootstrap.ComponentGate;
import io.github.rebrowser.control.ControlEnvelope;
import io.github.rebrowser.crypto.CryptoEncoding;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.function.LongSupplier;

/** Actual P256 possession challenge for this native target and eligible compiled owner.
 * It does not generate/read installation private keys, enroll devices, bypass revocation or
 * authorize a command. Current device-enable/actor/scope/grant state remains a policy gate. */
public final class TargetPossessionProof {
    public static final class Challenge {
        private final TargetPossessionProof issuer;private final AdminProofVerifier.KeyProof principal;private final DeviceWhitelist.Device device;private final byte[] message;private final long deadline;private boolean spent;
        private Challenge(TargetPossessionProof issuer,AdminProofVerifier.KeyProof principal,DeviceWhitelist.Device device,byte[] message,long deadline){this.issuer=issuer;this.principal=principal;this.device=device;this.message=message;this.deadline=deadline;}
        public byte[] signingBytes(){return message.clone();}
        /** Existing installation-key signer only; no raw arbitrary bytes sign endpoint. */
        public void requireCurrentTarget(byte[] actualKeySha256,byte[] actualEpoch){synchronized(issuer){if(spent || !issuer.pending.contains(this) || issuer.now()>=deadline || actualKeySha256==null || actualEpoch==null || !MessageDigest.isEqual(issuer.target,actualKeySha256) || !MessageDigest.isEqual(issuer.source.dataEpoch(),actualEpoch))throw denied();issuer.source.requireCurrent();}}
    }
    public static final class Receipt {
        private final Challenge challenge;private boolean consumed;
        private Receipt(Challenge challenge){this.challenge=challenge;}
    }
    private final DeviceWhitelist whitelist;private final ComponentGate.DataEpochPin source;private final byte[] target;private final LongSupplier clock;private final SecureRandom random=new SecureRandom();private final Set<Challenge> pending=Collections.newSetFromMap(new IdentityHashMap<Challenge,Boolean>());private long last=-1;private boolean closed;
    public TargetPossessionProof(DeviceWhitelist whitelist,ComponentGate.DataEpochPin source,byte[] nativeTargetSpkiSha256,LongSupplier elapsedRealtime){if(whitelist==null || source==null || nativeTargetSpkiSha256==null || nativeTargetSpkiSha256.length!=32 || elapsedRealtime==null)throw invalid();source.requireCurrent();this.whitelist=whitelist;this.source=source;target=nativeTargetSpkiSha256.clone();clock=elapsedRealtime;}
    public synchronized Challenge issue(AdminProofVerifier.KeyProof principal){
        long now=now();source.requireCurrent();if(principal==null || !MessageDigest.isEqual(target,principal.request().frame().bytes(ControlEnvelope.Field.TARGET_INSTALL_SPKI_SHA256)) || !MessageDigest.isEqual(source.dataEpoch(),principal.request().frame().bytes(ControlEnvelope.Field.DATA_EPOCH)))throw denied();
        Iterator<Challenge> old=pending.iterator();while(old.hasNext())if(now>=old.next().deadline)old.remove();if(pending.size()>=16 || now>Long.MAX_VALUE-30_000)throw denied();
        DeviceWhitelist.Device device=whitelist.eligible(principal,hex(target));long deadline=Math.min(now+30_000,principal.request().frame().unsigned63(ControlEnvelope.Field.DEADLINE_ELAPSED_MS));if(deadline<=now)throw denied();
        byte[] magic="ReBrowser.Target.Possession.1\0".getBytes(StandardCharsets.US_ASCII),nonce=new byte[32];random.nextBytes(nonce);
        byte[] frameHash=CryptoEncoding.sha256(principal.request().frame().signedBytes());ByteBuffer body=ByteBuffer.allocate(magic.length+32+16+32+32+8);body.put(magic).put(target).put(source.dataEpoch()).put(frameHash).put(nonce).putLong(deadline);
        Challenge c=new Challenge(this,principal,device,body.array(),deadline);source.requireCurrent();pending.add(c);return c;
    }
    /** Spend BEFORE crypto/native handoff. Any invalid/unknown signature requires a fresh challenge. */
    public synchronized Receipt verify(Challenge c,byte[] signature)throws GeneralSecurityException{
        if(c==null || c.issuer!=this || c.spent || !pending.remove(c))throw denied();c.spent=true;
        if(now()>=c.deadline)throw denied();source.requireCurrent();CryptoEncoding.validateP256DerSignature(signature);
        Signature v=Signature.getInstance("SHA256withECDSA");v.initVerify(c.device.key);v.update(c.message);if(!v.verify(signature))throw new GeneralSecurityException("Invalid target possession proof");source.requireCurrent();return new Receipt(c);
    }
    public synchronized void consume(Receipt receipt,AdminProofVerifier.KeyProof exactRequest){
        if(receipt==null || receipt.challenge.issuer!=this || receipt.consumed)throw denied();receipt.consumed=true;Challenge c=receipt.challenge;
        if(c.principal!=exactRequest || now()>=c.deadline)throw denied();source.requireCurrent();
    }
    public synchronized void close(){closed=true;pending.clear();}
    private long now(){long n;try{n=clock.getAsLong();}catch(RuntimeException e){closed=true;pending.clear();throw denied();}if(closed || n<0 || n<last){closed=true;pending.clear();throw denied();}last=n;return n;}
    private static String hex(byte[] b){StringBuilder s=new StringBuilder();for(byte v:b)s.append(Character.forDigit((v&255)>>>4,16)).append(Character.forDigit(v&15,16));return s.toString();}
    private static SecurityException denied(){return new SecurityException("Target possession unavailable or spent");}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid native possession context");}
}
