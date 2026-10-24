package io.github.rebrowser.crypto;

import android.app.Application;
import android.os.Looper;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import io.github.rebrowser.bootstrap.StartupCoordinator;
import io.github.rebrowser.continuity.SourceBootstrap;
import java.security.spec.ECGenParameterSpec;
import io.github.rebrowser.authority.CompiledPrincipals;
import io.github.rebrowser.authority.TargetPossessionProof;
import io.github.rebrowser.bootstrap.ComponentGate;
import io.github.rebrowser.bootstrap.ProcessRole;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.util.*;

/** Per-v2-installation Android Keystore P256 key. Existing reads never create/repair.
 * Explicit first-setup creation is cold/native-consent-only. No import, alias enumeration,
 * deletion, caller alias/raw sign endpoint or enrollment from package/serial.
 * FormalRoot unconfigured fails BEFORE Keystore IO. Actual key custody/enrollment unqualified. */
public final class InstallKeyStore {
    private static final String ALIAS="io.github.rebrowser.install.v1";
    private final PrivateKey key;private final ECPublicKey publicKey;private final ComponentGate.DataEpochPin source;private final Set<TargetPossessionProof.Challenge> spent=Collections.newSetFromMap(new IdentityHashMap<TargetPossessionProof.Challenge,Boolean>());
    private final Set<io.github.rebrowser.authority.EnrollmentProposal.Challenge> enrollmentSpent=Collections.newSetFromMap(new IdentityHashMap<io.github.rebrowser.authority.EnrollmentProposal.Challenge,Boolean>());
    private InstallKeyStore(PrivateKey key,ECPublicKey publicKey,ComponentGate.DataEpochPin source){this.key=key;this.publicKey=publicKey;this.source=source;}
    public static InstallKeyStore openExisting(Application ownApplication,ComponentGate.DataEpochPin nativeSource)throws GeneralSecurityException{
        if(ownApplication==null || nativeSource==null)throw new IllegalArgumentException("Native installation key context required");if(Looper.myLooper()==Looper.getMainLooper())throw new IllegalStateException("Installation key IO lane required");nativeSource.requireCurrent();if(!CompiledPrincipals.production().formalRootConfigured())throw new GeneralSecurityException("Formal installation key domain unconfigured");
        if(ProcessRole.classifyOwnApplication(ownApplication.getPackageName(),Application.getProcessName()).startupRoute()!=ProcessRole.StartupRoute.MAIN_NATIVE_OWNER)throw new SecurityException("Native own key process required");
        try{KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);java.security.cert.Certificate certificate=store.getCertificate(ALIAS);java.security.Key key=store.getKey(ALIAS,null);if(certificate==null || !(key instanceof PrivateKey))throw new GeneralSecurityException("Existing installation key unavailable");ECPublicKey publicKey=CryptoEncoding.p256PublicKey(certificate.getPublicKey().getEncoded());if(!"EC".equals(key.getAlgorithm()))throw new GeneralSecurityException("Unsupported installation key");nativeSource.requireCurrent();return new InstallKeyStore((PrivateKey)key,publicKey,nativeSource);}catch(java.io.IOException unavailable){throw new GeneralSecurityException("Installation key provider unavailable");}
    }
    /** Read-only first-setup inventory, not absence-as-fresh proof. */
    public static void requireAbsentForFirstSetup(StartupCoordinator.FirstSetupFence fence)throws GeneralSecurityException {
        if(fence==null)throw new SecurityException("Native first-setup fence required");if(Looper.myLooper()==Looper.getMainLooper())throw new IllegalStateException("Initial key inventory IO required");fence.requireStillCold(fence.nativeApplication());
        try{KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);if(store.containsAlias(ALIAS))throw new GeneralSecurityException("Installation key already exists; no automatic replacement");}catch(java.io.IOException unavailable){throw new GeneralSecurityException("Installation key inventory unavailable",unavailable);}
    }
    /** Actual native first-setup only. The generated public key is NOT compiled enrollment. */
    public static byte[] createForFirstSetup(SourceBootstrap.FreshConsent consent)throws GeneralSecurityException {
        if(consent==null)throw new SecurityException("Native first-setup consent required");if(Looper.myLooper()==Looper.getMainLooper())throw new IllegalStateException("Initial key creation IO required");consent.requireCurrent();consent.beginInstallationKeyAttempt();
        try{KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);if(store.containsAlias(ALIAS))throw new GeneralSecurityException("Installation alias already present");
            KeyPairGenerator generator=KeyPairGenerator.getInstance("EC","AndroidKeyStore");generator.initialize(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_SIGN|KeyProperties.PURPOSE_VERIFY).setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1")).setDigests(KeyProperties.DIGEST_SHA256).setUserAuthenticationRequired(false).build());KeyPair generated=generator.generateKeyPair();
            java.security.cert.Certificate certificate=store.getCertificate(ALIAS);if(certificate==null)throw new GeneralSecurityException("New installation certificate unavailable");ECPublicKey publicKey=CryptoEncoding.p256PublicKey(certificate.getPublicKey().getEncoded());if(!Arrays.equals(publicKey.getEncoded(),generated.getPublic().getEncoded()))throw new GeneralSecurityException("Installation key changed during creation");
            byte[] domain="RB-FIRST-INSTALL-KEY-V1\0".getBytes(java.nio.charset.StandardCharsets.US_ASCII);byte[] message=java.nio.ByteBuffer.allocate(domain.length+16).put(domain).put(consent.dataEpoch()).array();Signature signer=Signature.getInstance("SHA256withECDSA");signer.initSign((PrivateKey)store.getKey(ALIAS,null));signer.update(message);byte[] signature=signer.sign();Signature verifier=Signature.getInstance("SHA256withECDSA");verifier.initVerify(publicKey);verifier.update(message);if(!verifier.verify(signature))throw new GeneralSecurityException("New installation key possession unavailable");consent.requireCurrent();return publicKey.getEncoded().clone();
        }catch(java.io.IOException unavailable){throw new GeneralSecurityException("Initial installation provider unavailable",unavailable);}
    }
    public byte[] publicSpki(){source.requireCurrent();return publicKey.getEncoded().clone();}
    /** Actual fresh native TargetPossessionProof message only, one attempted signature per receipt.
     * This does not admit the actor, whitelist entry, owner enrollment or any external effect. */
    public synchronized byte[] proveExistingKey(TargetPossessionProof.Challenge actualNativeChallenge)throws GeneralSecurityException{
        if(Looper.myLooper()==Looper.getMainLooper())throw new IllegalStateException("Installation signing IO lane required");source.requireCurrent();if(actualNativeChallenge==null || spent.size()+enrollmentSpent.size()>=64 || !spent.add(actualNativeChallenge))throw new SecurityException("Native installation proof unavailable or spent");actualNativeChallenge.requireCurrentTarget(CryptoEncoding.sha256(publicKey.getEncoded()),source.dataEpoch());byte[] message=actualNativeChallenge.signingBytes();try{Signature signer=Signature.getInstance("SHA256withECDSA");signer.initSign(key);signer.update(message);byte[] signature=signer.sign();CryptoEncoding.validateP256DerSignature(signature);actualNativeChallenge.requireCurrentTarget(CryptoEncoding.sha256(publicKey.getEncoded()),source.dataEpoch());source.requireCurrent();return signature;}finally{Arrays.fill(message,(byte)0);}
    }
    /** Separate domain-tagged native DRAFT challenge only, never arbitrary signing/enrollment. */
    public synchronized byte[] proveNativeEnrollmentDraft(io.github.rebrowser.authority.EnrollmentProposal.Challenge challenge)throws GeneralSecurityException{
        if(Looper.myLooper()==Looper.getMainLooper())throw new IllegalStateException("Installation signing IO lane required");source.requireCurrent();if(challenge==null || spent.size()+enrollmentSpent.size()>=64 || !enrollmentSpent.add(challenge))throw new SecurityException("Native draft proof unavailable or spent");byte[] fingerprint=CryptoEncoding.sha256(publicKey.getEncoded()),epoch=source.dataEpoch();challenge.requireCurrentTarget(fingerprint,epoch);byte[] message=challenge.signingBytes();try{Signature signer=Signature.getInstance("SHA256withECDSA");signer.initSign(key);signer.update(message);byte[] signature=signer.sign();CryptoEncoding.validateP256DerSignature(signature);challenge.requireCurrentTarget(fingerprint,epoch);source.requireCurrent();return signature;}finally{Arrays.fill(message,(byte)0);}
    }
}
