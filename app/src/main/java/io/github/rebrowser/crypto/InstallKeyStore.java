package io.github.rebrowser.crypto;

import android.app.Application;
import android.os.Looper;
import io.github.rebrowser.authority.CompiledPrincipals;
import io.github.rebrowser.authority.TargetPossessionProof;
import io.github.rebrowser.bootstrap.ComponentGate;
import io.github.rebrowser.bootstrap.ProcessRole;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.util.*;

/** Existing ONLY per-v2-installation Android Keystore P256 key. No key generation, import,
 * alias enumeration/deletion, caller alias/raw sign endpoint or enrollment from package/serial.
 * FormalRoot unconfigured fails BEFORE Keystore IO. Actual key custody/enrollment unqualified. */
public final class InstallKeyStore {
    private static final String ALIAS="io.github.rebrowser.install.v1";
    private final PrivateKey key;private final ECPublicKey publicKey;private final ComponentGate.DataEpochPin source;private final Set<TargetPossessionProof.Challenge> spent=Collections.newSetFromMap(new IdentityHashMap<TargetPossessionProof.Challenge,Boolean>());
    private InstallKeyStore(PrivateKey key,ECPublicKey publicKey,ComponentGate.DataEpochPin source){this.key=key;this.publicKey=publicKey;this.source=source;}
    public static InstallKeyStore openExisting(Application ownApplication,ComponentGate.DataEpochPin nativeSource)throws GeneralSecurityException{
        if(ownApplication==null || nativeSource==null)throw new IllegalArgumentException("Native installation key context required");if(Looper.myLooper()==Looper.getMainLooper())throw new IllegalStateException("Installation key IO lane required");nativeSource.requireCurrent();if(!CompiledPrincipals.production().formalRootConfigured())throw new GeneralSecurityException("Formal installation key domain unconfigured");
        if(ProcessRole.classifyOwnApplication(ownApplication.getPackageName(),Application.getProcessName()).startupRoute()!=ProcessRole.StartupRoute.MAIN_NATIVE_OWNER)throw new SecurityException("Native own key process required");
        try{KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);java.security.cert.Certificate certificate=store.getCertificate(ALIAS);java.security.Key key=store.getKey(ALIAS,null);if(certificate==null || !(key instanceof PrivateKey))throw new GeneralSecurityException("Existing installation key unavailable");ECPublicKey publicKey=CryptoEncoding.p256PublicKey(certificate.getPublicKey().getEncoded());if(!"EC".equals(key.getAlgorithm()))throw new GeneralSecurityException("Unsupported installation key");nativeSource.requireCurrent();return new InstallKeyStore((PrivateKey)key,publicKey,nativeSource);}catch(java.io.IOException unavailable){throw new GeneralSecurityException("Installation key provider unavailable");}
    }
    public byte[] publicSpki(){source.requireCurrent();return publicKey.getEncoded().clone();}
    /** Actual fresh native TargetPossessionProof message only, one attempted signature per receipt.
     * This does not admit the actor, whitelist entry, owner enrollment or any external effect. */
    public synchronized byte[] proveExistingKey(TargetPossessionProof.Challenge actualNativeChallenge)throws GeneralSecurityException{
        if(Looper.myLooper()==Looper.getMainLooper())throw new IllegalStateException("Installation signing IO lane required");source.requireCurrent();if(actualNativeChallenge==null || spent.size()>=64 || !spent.add(actualNativeChallenge))throw new SecurityException("Native installation proof unavailable or spent");actualNativeChallenge.requireCurrentTarget(CryptoEncoding.sha256(publicKey.getEncoded()),source.dataEpoch());byte[] message=actualNativeChallenge.signingBytes();try{Signature signer=Signature.getInstance("SHA256withECDSA");signer.initSign(key);signer.update(message);byte[] signature=signer.sign();CryptoEncoding.validateP256DerSignature(signature);actualNativeChallenge.requireCurrentTarget(CryptoEncoding.sha256(publicKey.getEncoded()),source.dataEpoch());source.requireCurrent();return signature;}finally{Arrays.fill(message,(byte)0);}
    }
}
