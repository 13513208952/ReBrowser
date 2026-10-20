package io.github.rebrowser.crypto;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Bounded private metadata AEAD, distinct from mail RBMOBJ1. No storage, trust or key provisioning. */
public final class PrivateRecordSealer {
    public static final int HEADER_BYTES=72,TAG_BYTES=16;
    private static final long MAGIC=0x5242505249563100L; // RBPRIV1 NUL
    private static final SecureRandom RANDOM=new SecureRandom();
    public enum Purpose {
        BROWSER_CATALOG(KeyPurpose.RecordKind.BROWSER_CATALOG),GECKO_CHECKPOINT(KeyPurpose.RecordKind.GECKO_CHECKPOINT);
        private final int code,maximum;
        Purpose(KeyPurpose.RecordKind kind){this.code=kind.code();this.maximum=(int)kind.maxBytes();}
        public int code(){return code;}public int maximumPlaintextBytes(){return maximum;}
    }
    /** Native expectation, NOT authority. Never copy attacker header fields to establish it. */
    public static final class Binding {
        private final Purpose purpose;private final byte[] epoch,scope;private final long revision;
        public Binding(Purpose purpose,byte[] nativeEpoch,byte[] nativeScope,long nativeRevision){if(purpose==null || nativeRevision<1)throw invalid();this.purpose=purpose;epoch=fixed(nativeEpoch,16);scope=fixed(nativeScope,16);revision=nativeRevision;}
        public Purpose purpose(){return purpose;}public long revision(){return revision;}
        public byte[] dataEpoch(){return epoch.clone();}public byte[] nativeScope(){return scope.clone();}
        /** Fixed domain-separated context for the independent wrapping-key AEAD. */
        public byte[] wrappingAad(){return ByteBuffer.allocate(58).putLong(0x5242575241503100L).putShort((short)purpose.code).put(epoch).put(scope).putLong(revision).putLong(1).array();}
    }
    private PrivateRecordSealer(){}
    public static byte[] seal(byte[] nativeDek,Binding expected,byte[] plaintext)throws GeneralSecurityException {
        if(expected==null || plaintext==null || plaintext.length>expected.purpose.maximum)throw invalid();
        byte[] key=fixed(nativeDek,32),nonce=new byte[12];RANDOM.nextBytes(nonce);
        byte[] header=header(expected,plaintext.length,nonce);
        try{Cipher cipher=cipher(Cipher.ENCRYPT_MODE,key,nonce,header);byte[] encrypted=cipher.doFinal(plaintext);byte[] result=new byte[HEADER_BYTES+encrypted.length];System.arraycopy(header,0,result,0,HEADER_BYTES);System.arraycopy(encrypted,0,result,HEADER_BYTES,encrypted.length);return result;}
        finally{Arrays.fill(key,(byte)0);}
    }
    public static byte[] open(byte[] nativeDek,Binding expected,byte[] framed)throws GeneralSecurityException {
        if(expected==null || framed==null || framed.length<HEADER_BYTES+TAG_BYTES || framed.length>HEADER_BYTES+TAG_BYTES+expected.purpose.maximum)throw invalid();
        ByteBuffer input=ByteBuffer.wrap(framed);long magic=input.getLong();int version=input.getShort()&65535,purpose=input.getShort()&65535,keyVersion=input.getInt();byte[] epoch=new byte[16],scope=new byte[16];input.get(epoch).get(scope);long revision=input.getLong();int length=input.getInt();byte[] nonce=new byte[12];input.get(nonce);
        if(magic!=MAGIC || version!=1 || purpose!=expected.purpose.code || keyVersion!=1 || !MessageDigest.isEqual(epoch,expected.epoch) || !MessageDigest.isEqual(scope,expected.scope) || revision!=expected.revision || length<0 || length>expected.purpose.maximum || framed.length!=HEADER_BYTES+TAG_BYTES+length)throw new GeneralSecurityException("Private record binding or length mismatch");
        byte[] key=fixed(nativeDek,32);
        try{return cipher(Cipher.DECRYPT_MODE,key,nonce,Arrays.copyOf(framed,HEADER_BYTES)).doFinal(framed,HEADER_BYTES,framed.length-HEADER_BYTES);}
        finally{Arrays.fill(key,(byte)0);}
    }
    /** Untrusted candidate used ONLY to construct a native-bound verification expectation. */
    public static long unverifiedRevision(byte[] framed){if(framed==null || framed.length<HEADER_BYTES+TAG_BYTES)throw invalid();long revision=ByteBuffer.wrap(framed,48,8).getLong();if(revision<1)throw invalid();return revision;}
    private static byte[] header(Binding b,int length,byte[] nonce){return ByteBuffer.allocate(HEADER_BYTES).putLong(MAGIC).putShort((short)1).putShort((short)b.purpose.code).putInt(1).put(b.epoch).put(b.scope).putLong(b.revision).putInt(length).put(nonce).array();}
    private static Cipher cipher(int mode,byte[] key,byte[] nonce,byte[] aad)throws GeneralSecurityException{Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(mode,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));cipher.updateAAD(aad);return cipher;}
    private static byte[] fixed(byte[] input,int length){if(input==null || input.length!=length)throw invalid();return input.clone();}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid bounded private record input");}
}
