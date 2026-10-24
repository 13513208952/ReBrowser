package io.github.rebrowser.crypto;

import android.app.Application;
import android.os.Looper;
import android.security.keystore.KeyInfo;
import android.security.keystore.KeyGenParameterSpec;
import io.github.rebrowser.continuity.SourceBootstrap;
import javax.crypto.KeyGenerator;
import android.security.keystore.KeyProperties;
import io.github.rebrowser.bootstrap.ComponentGate;
import io.github.rebrowser.bootstrap.ProcessRole;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Existing epoch-owned reads NEVER generate, enumerate, delete or repair keys. Explicit
 * native first-setup creation is separate, requires actual consent/cold inventory, and is
 * not enrollment, credential authentication, a fallback or missing-key repair.
 * Private records use fresh random DEKs, independently wrapped with purpose/epoch/scope/revision
 * AAD. Ordinary calls are synchronous IO-owner work, not actual whole-domain quiescence.
 */
public final class DataKeyVault {
    public static final int ENVELOPE_OVERHEAD=160;
    private static final long MAGIC=0x5242564c54310000L; // RBVLT1 NUL NUL
    private static final int PREFIX_BYTES=72;
    private final SecretKey wrappingKey;private final ComponentGate gate;private final ComponentGate.ResourcePin lifetime;private final byte[] epoch;private final boolean nativePlatform;
    private DataKeyVault(SecretKey wrappingKey,ComponentGate gate,byte[] epoch,boolean nativePlatform){if(wrappingKey==null || gate==null || epoch==null || epoch.length!=16)throw invalid();this.wrappingKey=wrappingKey;this.gate=gate;this.epoch=epoch.clone();this.nativePlatform=nativePlatform;lifetime=gate.newResourcePin();}
    public static DataKeyVault openExisting(Application application,ComponentGate gate,byte[] nativeEpoch,ComponentGate.BusinessPermit permit)throws GeneralSecurityException {
        if(application==null || gate==null || nativeEpoch==null || nativeEpoch.length!=16)throw invalid();byte[] epoch=nativeEpoch.clone();
        ioThread();if(ProcessRole.classifyOwnApplication(application.getPackageName(),Application.getProcessName()).startupRoute()!=ProcessRole.StartupRoute.MAIN_NATIVE_OWNER)throw new SecurityException("Primary native data owner required");
        gate.requireDataEpoch(permit,epoch);ComponentGate.ResourcePin pin=gate.newResourcePin();pin.checkEpochOrBind(permit,epoch);
        try{
            KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);java.security.Key key=store.getKey(alias(epoch),null);
            if(!(key instanceof SecretKey))throw new GeneralSecurityException("Existing epoch wrapping key unavailable");
            SecretKey secret=(SecretKey)key;KeyInfo info=(KeyInfo)SecretKeyFactory.getInstance("AES","AndroidKeyStore").getKeySpec(secret,KeyInfo.class);
            if((info.getPurposes()&(KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT))!=(KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT) || info.getKeySize()!=256 || info.isUserAuthenticationRequired() || !contains(info.getBlockModes(),"GCM") || !contains(info.getEncryptionPaddings(),"NoPadding"))throw new GeneralSecurityException("Unsupported existing wrapping-key policy");
            pin.checkEpochOrBind(permit,epoch);DataKeyVault result=new DataKeyVault(secret,gate,epoch,true);result.check(permit);return result;
        }catch(java.io.IOException unavailable){throw new GeneralSecurityException("Native key provider unavailable",unavailable);}
    }
    public void requireBindings(ComponentGate expectedGate,byte[] nativeExpectedEpoch,ComponentGate.BusinessPermit permit){if(gate!=expectedGate || !Arrays.equals(epoch,nativeExpectedEpoch))throw invalid();check(permit);}
    public byte[] seal(PrivateRecordSealer.Binding binding,byte[] plaintext,ComponentGate.BusinessPermit permit)throws GeneralSecurityException {
        checkBinding(binding,permit);byte[] sealed=sealEnvelope(wrappingKey,binding,plaintext);checkBinding(binding,permit);return sealed;
    }
    /** Pure cryptographic primitive; possessing a key is NOT a source/creation permission. */
    static byte[] sealEnvelope(SecretKey wrappingKey,PrivateRecordSealer.Binding binding,byte[] plaintext)throws GeneralSecurityException {
        if(binding==null || plaintext==null || plaintext.length>binding.purpose().maximumPlaintextBytes())throw invalid();
        byte[] dek=new byte[32];new SecureRandom().nextBytes(dek);
        try{
            Cipher wrapper=Cipher.getInstance("AES/GCM/NoPadding");wrapper.init(Cipher.ENCRYPT_MODE,wrappingKey);wrapper.updateAAD(binding.wrappingAad());byte[] wrapped=wrapper.doFinal(dek),nonce=wrapper.getIV();
            if(wrapped.length!=48 || nonce==null || nonce.length!=12)throw new GeneralSecurityException("Unsupported wrapping-key AEAD framing");
            byte[] record=PrivateRecordSealer.seal(dek,binding,plaintext);
            return ByteBuffer.allocate(PREFIX_BYTES+record.length).putLong(MAGIC).putInt(record.length).put(nonce).put(wrapped).put(record).array();
        }finally{Arrays.fill(dek,(byte)0);} // best effort only; provider/JVM copies are NOT absolute memory erasure
    }
    public byte[] open(PrivateRecordSealer.Binding binding,byte[] envelope,ComponentGate.BusinessPermit permit)throws GeneralSecurityException {
        checkBinding(binding,permit);byte[] plaintext=openEnvelope(wrappingKey,binding,envelope);try{checkBinding(binding,permit);byte[] result=plaintext;plaintext=null;return result;}finally{if(plaintext!=null)Arrays.fill(plaintext,(byte)0);}
    }
    static byte[] openEnvelope(SecretKey wrappingKey,PrivateRecordSealer.Binding binding,byte[] envelope)throws GeneralSecurityException {
        if(binding==null || envelope==null || envelope.length<ENVELOPE_OVERHEAD || envelope.length>ENVELOPE_OVERHEAD+binding.purpose().maximumPlaintextBytes())throw invalid();
        ByteBuffer input=ByteBuffer.wrap(envelope);if(input.getLong()!=MAGIC)throw new GeneralSecurityException("Private vault format mismatch");int recordLength=input.getInt();
        if(recordLength!=envelope.length-PREFIX_BYTES)throw new GeneralSecurityException("Private vault length mismatch");byte[] nonce=new byte[12],wrapped=new byte[48];input.get(nonce).get(wrapped);
        Cipher wrapper=Cipher.getInstance("AES/GCM/NoPadding");wrapper.init(Cipher.DECRYPT_MODE,wrappingKey,new GCMParameterSpec(128,nonce));wrapper.updateAAD(binding.wrappingAad());byte[] dek=wrapper.doFinal(wrapped),plaintext=null;
        try{if(dek.length!=32)throw new GeneralSecurityException("Invalid record key size");plaintext=PrivateRecordSealer.open(dek,binding,Arrays.copyOfRange(envelope,PREFIX_BYTES,envelope.length));byte[] result=plaintext;plaintext=null;return result;}
        finally{Arrays.fill(dek,(byte)0);if(plaintext!=null)Arrays.fill(plaintext,(byte)0);}
    }
    /** New-key operation is separate from existing-key reads, and requires the private native
     * first-setup consent. Unknown generation is NEVER retried/replaced/deleted here. */
    public static InitialKey createForFirstSetup(SourceBootstrap.FreshConsent consent)throws GeneralSecurityException {
        if(consent==null)throw invalid();ioThread();consent.requireCurrent();consent.beginWrappingKeyAttempt();
        try{byte[] epoch=consent.dataEpoch();KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);String name=alias(epoch);if(store.containsAlias(name))throw new GeneralSecurityException("First-setup wrapping alias already present");
            KeyGenerator generator=KeyGenerator.getInstance("AES","AndroidKeyStore");generator.init(new KeyGenParameterSpec.Builder(name,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).setUserAuthenticationRequired(false).build());generator.generateKey();
            SecretKey key=(SecretKey)store.getKey(name,null);if(key==null)throw new GeneralSecurityException("New wrapping key unavailable");KeyInfo info=(KeyInfo)SecretKeyFactory.getInstance("AES","AndroidKeyStore").getKeySpec(key,KeyInfo.class);if(info.getKeySize()!=256 || info.isUserAuthenticationRequired() || (info.getPurposes()&(KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT))!=(KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT) || !contains(info.getBlockModes(),"GCM") || !contains(info.getEncryptionPaddings(),"NoPadding"))throw new GeneralSecurityException("Unexpected new wrapping-key policy");consent.requireCurrent();return new InitialKey(consent,key,epoch);
        }catch(java.io.IOException unavailable){throw new GeneralSecurityException("Initial wrapping provider unavailable",unavailable);}
    }
    public static final class InitialKey {
        private final SourceBootstrap.FreshConsent consent;private final SecretKey key;private final byte[] epoch;
        private InitialKey(SourceBootstrap.FreshConsent consent,SecretKey key,byte[] epoch){this.consent=consent;this.key=key;this.epoch=epoch.clone();}
        /** Fixed first-setup owner supplies the whole intended record; never an existing-data read. */
        public byte[] sealInitial(PrivateRecordSealer.Binding binding,byte[] plaintext)throws GeneralSecurityException {
            consent.requireCurrent();if(binding==null || !Arrays.equals(epoch,binding.dataEpoch()) || binding.revision()!=1)throw invalid();
            byte[] sealed=sealEnvelope(key,binding,plaintext),verified=null;
            try{verified=openEnvelope(key,binding,sealed);if(!java.security.MessageDigest.isEqual(plaintext,verified))throw new GeneralSecurityException("Initial vault verification failed");consent.requireCurrent();return sealed;}finally{if(verified!=null)Arrays.fill(verified,(byte)0);}
        }
    }
    /** Unverified revision candidate only, NOT latest/durable/authority or an authenticated result. */
    public static long unverifiedRevision(byte[] envelope){if(envelope==null || envelope.length<ENVELOPE_OVERHEAD || envelope.length>ENVELOPE_OVERHEAD+4*1024*1024)throw invalid();long revision=ByteBuffer.wrap(envelope,PREFIX_BYTES+48,8).getLong();if(revision<1)throw invalid();return revision;}
    private void checkBinding(PrivateRecordSealer.Binding b,ComponentGate.BusinessPermit permit){if(b==null || !Arrays.equals(epoch,b.dataEpoch()))throw invalid();check(permit);}
    private void check(ComponentGate.BusinessPermit permit){if(nativePlatform)ioThread();lifetime.checkEpochOrBind(permit,epoch);}
    private static void ioThread(){if(Looper.myLooper()==Looper.getMainLooper())throw new IllegalStateException("Native key work requires an owned IO lane");}
    private static String alias(byte[] epoch){StringBuilder out=new StringBuilder("io.github.rebrowser.datawrap.v1.");for(byte b:epoch)out.append("0123456789abcdef".charAt((b&255)>>>4)).append("0123456789abcdef".charAt(b&15));return out.toString();}
    private static boolean contains(String[] values,String expected){for(String value:values)if(expected.equals(value))return true;return false;}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid native epoch vault input");}
}
