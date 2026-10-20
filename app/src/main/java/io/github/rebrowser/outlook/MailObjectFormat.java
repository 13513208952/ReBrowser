package io.github.rebrowser.outlook;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

/**
 * Pure RBMOBJ1 fixed-header/framing codec: 124-byte big-endian header, 32768-byte plaintext
 * frames, 20 bytes per framed record (u32 length + 16-byte tag). This class does NOT encrypt,
 * decrypt, compress, write or mint object IDs, and a received header is never treated as native
 * catalog expectation. Authentication stays in ObjectAead with the native key catalog.
 */
public final class MailObjectFormat {
    public static final int HEADER_BYTES = 124;
    public static final int FRAME_PLAIN_BYTES = 32_768;
    public static final int FRAME_OVERHEAD_BYTES = 20;
    public static final long U32_MAX = 0xffff_ffffL;
    public static final long MAX_PACKED_BYTES = U32_MAX * FRAME_PLAIN_BYTES;
    public static final int VERSION = 1, FLAGS_AEAD = 1;
    private static final byte[] MAGIC = "RBMOBJ1\0".getBytes(StandardCharsets.US_ASCII);
    public enum Codec { RAW(0), ZLIB(1); public final int code; Codec(int code){this.code=code;} public static Codec fromCode(int value){for(Codec c:values())if(c.code==value)return c;throw invalid();} }
    public enum Purpose { BODY(1), ATTACHMENT(2), SEARCH_TEXT(3); public final int code; Purpose(int code){this.code=code;} public static Purpose fromCode(int value){for(Purpose p:values())if(p.code==value)return p;throw invalid();} }

    public static final class Header {
        private final Codec codec;private final Purpose purpose;private final long keyVersion;
        private final byte[] epoch,account,objectId,originalSha256;
        private final long originalLength,packedLength;
        private Header(Codec codec,Purpose purpose,long keyVersion,byte[] epoch,byte[] account,byte[] objectId,
                       long originalLength,long packedLength,byte[] originalSha256){
            if(codec==null || purpose==null)throw invalid();
            if(keyVersion<1 || keyVersion>U32_MAX)throw invalid();
            if(originalLength<0 || packedLength<0 || packedLength>MAX_PACKED_BYTES)throw invalid();
            if(codec==Codec.RAW && originalLength!=packedLength)throw invalid();
            // Structural codec only: compressed-stream/original-length consistency is verified by MailObjectCodec, not guessed here.
            this.codec=codec;this.purpose=purpose;this.keyVersion=keyVersion;
            this.epoch=fixed(epoch,16);this.account=fixed(account,16);this.objectId=fixed(objectId,16);
            this.originalSha256=fixed(originalSha256,32);
            this.originalLength=originalLength;this.packedLength=packedLength;
        }
        public Codec codec(){return codec;}
        public Purpose purpose(){return purpose;}
        public long keyVersion(){return keyVersion;}
        public byte[] dataEpoch(){return epoch.clone();}
        public byte[] accountId(){return account.clone();}
        public byte[] objectId(){return objectId.clone();}
        public byte[] originalSha256(){return originalSha256.clone();}
        public long originalBytes(){return originalLength;}
        public long packedBytes(){return packedLength;}
        public long frameCount(){return frames(packedLength);}
        public long storedBytes(){return HEADER_BYTES+packedLength+frameCount()*FRAME_OVERHEAD_BYTES;}
    }

    public static Header header(Codec codec,Purpose purpose,long keyVersion,byte[] epoch,byte[] account,byte[] objectId,
                                long originalLength,long packedLength,byte[] originalSha256){
        return new Header(codec,purpose,keyVersion,epoch,account,objectId,originalLength,packedLength,originalSha256);
    }

    public static byte[] encode(Header header){
        if(header==null)throw invalid();
        ByteBuffer out=ByteBuffer.allocate(HEADER_BYTES);
        out.put(MAGIC).putShort((short)VERSION).putShort((short)FLAGS_AEAD)
                .putShort((short)header.codec.code).putShort((short)header.purpose.code)
                .putInt((int)header.keyVersion).put(header.epoch).put(header.account).put(header.objectId)
                .putLong(header.originalLength).putLong(header.packedLength)
                .putInt(FRAME_PLAIN_BYTES).putInt((int)header.frameCount()).put(header.originalSha256);
        return out.array();
    }

    public static Header decode(byte[] value){
        if(value==null || value.length!=HEADER_BYTES)throw invalid();
        ByteBuffer in=ByteBuffer.wrap(value);
        byte[] magic=new byte[8];in.get(magic);
        if(!Arrays.equals(magic,MAGIC))throw invalid();
        int version=in.getShort()&0xffff,flags=in.getShort()&0xffff;
        int codec=in.getShort()&0xffff,purpose=in.getShort()&0xffff;
        long keyVersion=in.getInt()&U32_MAX;
        byte[] epoch=array(in,16),account=array(in,16),objectId=array(in,16);
        long original=in.getLong(),packed=in.getLong();
        long framePlain=in.getInt()&U32_MAX,frameCount=in.getInt()&U32_MAX;
        byte[] digest=array(in,32);
        if(version!=VERSION || flags!=FLAGS_AEAD)throw new IllegalArgumentException("Unsupported RBMOBJ1 version/flags");
        if(framePlain!=FRAME_PLAIN_BYTES)throw new IllegalArgumentException("Unexpected frame size");
        if(frameCount!=frames(packed))throw new IllegalArgumentException("Frame count mismatch");
        return new Header(Codec.fromCode(codec),Purpose.fromCode(purpose),keyVersion,epoch,account,objectId,original,packed,digest);
    }

    public static long frames(long packedLength){
        if(packedLength<0 || packedLength>MAX_PACKED_BYTES)throw invalid();
        long frames=packedLength==0?1:(packedLength+FRAME_PLAIN_BYTES-1)/FRAME_PLAIN_BYTES;
        if(frames>U32_MAX)throw invalid();
        return frames;
    }

    /** Strict stored-length expectation; never an allocation command. */
    public static long requireStoredBytes(Header header,long observedBytes){
        if(header==null || observedBytes<0)throw invalid();
        if(header.storedBytes()!=observedBytes)throw new IllegalArgumentException("Stored object length mismatch");
        return observedBytes;
    }

    /** Compare exact immutable identity fields; a received header cannot substitute catalog data. */
    public static boolean matchesCatalog(Header received,Header catalog){
        if(received==null || catalog==null)return false;
        return received.codec==catalog.codec && received.purpose==catalog.purpose
                && received.keyVersion==catalog.keyVersion && received.originalLength==catalog.originalLength
                && received.packedLength==catalog.packedLength
                && Arrays.equals(received.epoch,catalog.epoch) && Arrays.equals(received.account,catalog.account)
                && Arrays.equals(received.objectId,catalog.objectId)
                && MessageDigest.isEqual(received.originalSha256,catalog.originalSha256);
    }

    private static byte[] array(ByteBuffer in,int length){byte[] value=new byte[length];in.get(value);return value;}
    private static byte[] fixed(byte[] value,int length){if(value==null || value.length!=length)throw invalid();return value.clone();}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid RBMOBJ1 header/framing");}
    private MailObjectFormat(){}
}
