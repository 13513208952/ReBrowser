package io.github.rebrowser.crypto;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Stream RBMOBJ1 authenticated PACKED bytes into native, private, UNPUBLISHED staging.
 * No compression, key provisioning, catalog transaction, stream close/flush or publication.
 * Earlier verified frames may already be staged if a later frame fails: discard the stage.
 * ZLIB success authenticates packed bytes ONLY; bounded inflate/original digest verification
 * must still succeed before any read/export/DB-reference success. No source/owner authority
 * can be inferred from this helper. Expected metadata must come from the native catalog,
 * never from copying an untrusted received header.
 */
public final class ObjectAead {
    public static final int HEADER_BYTES = 124;
    public static final int FRAME_BYTES = 32_768;
    private static final long U32_MAX = 0xffff_ffffL;
    private static final long MAX_PACKED = U32_MAX * FRAME_BYTES;
    private static final byte[] MAGIC = "RBMOBJ1\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] KEY_DOMAIN = "RBMOBJ1-KEY\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] AAD_DOMAIN = "RBMOBJ1-AAD\0".getBytes(StandardCharsets.US_ASCII);
    private static final SecureRandom OBJECT_IDS = new SecureRandom();

    public enum Codec { RAW(0), ZLIB(1); private final int code; Codec(int code) { this.code = code; } }
    public enum Purpose { BODY(1), ATTACHMENT(2), SEARCH_TEXT(3); private final int code; Purpose(int code) { this.code = code; } }

    public static final class Limits {
        private final long original;
        private final long packed;
        public Limits(long maxOriginalBytes, long maxPackedBytes) {
            if (maxOriginalBytes < 0 || maxPackedBytes < 0 || maxPackedBytes > MAX_PACKED) throw argument();
            original = maxOriginalBytes; packed = maxPackedBytes;
        }
    }

    public static final class Metadata {
        private final Codec codec;
        private final Purpose purpose;
        private final byte[] epoch, account, object, digest, header;
        private final long originalLength, packedLength, frames;
        private Metadata(Limits limits, Codec codec, Purpose purpose, long keyVersion,
                byte[] epoch, byte[] account, byte[] object, long originalLength,
                long packedLength, byte[] originalSha256) {
            if (limits == null || codec == null || purpose == null || keyVersion < 1 || keyVersion > U32_MAX
                    || originalLength < 0 || originalLength > limits.original
                    || packedLength < 0 || packedLength > limits.packed
                    || (codec == Codec.RAW && originalLength != packedLength)) throw argument();
            this.codec = codec; this.purpose = purpose;
            this.epoch = fixed(epoch, 16); this.account = fixed(account, 16);
            this.object = fixed(object, 16); this.digest = fixed(originalSha256, 32);
            this.originalLength = originalLength; this.packedLength = packedLength;
            frames = Math.max(1, (packedLength + FRAME_BYTES - 1) / FRAME_BYTES);
            header = ByteBuffer.allocate(HEADER_BYTES).put(MAGIC).putShort((short) 1).putShort((short) 1)
                    .putShort((short) codec.code).putShort((short) purpose.code).putInt((int) keyVersion)
                    .put(this.epoch).put(this.account).put(this.object).putLong(originalLength).putLong(packedLength)
                    .putInt(FRAME_BYTES).putInt((int) frames).put(this.digest).array();
        }
        public byte[] objectId() { return object.clone(); }
        public byte[] headerBytes() { return header.clone(); }
        public long originalBytes() { return originalLength; }
        public long packedBytes() { return packedLength; }
        public long framedBytes() { return HEADER_BYTES + packedLength + frames * 20; }
        public Codec codec() { return codec; }
    }

    /** Native catalog expectation for READ, not a handle authorizing re-encryption. */
    public static Metadata catalogMetadata(Limits limits, Codec codec, Purpose purpose, long keyVersion,
            byte[] epoch, byte[] account, byte[] objectId, long originalLength, long packedLength,
            byte[] originalSha256) {
        return new Metadata(limits, codec, purpose, keyVersion, epoch, account, objectId,
                originalLength, packedLength, originalSha256);
    }

    public static final class WritePlan {
        private final Metadata metadata;
        private final AtomicBoolean used = new AtomicBoolean();
        private WritePlan(Metadata metadata) { this.metadata = metadata; }
        public Metadata metadata() { return metadata; }
    }

    /** Fresh random128-bit representation ID. Native catalog must still reserve uniqueness. */
    public static WritePlan freshWrite(Limits limits, Codec codec, Purpose purpose, long keyVersion,
            byte[] epoch, byte[] account, long originalLength, long packedLength, byte[] originalSha256) {
        byte[] objectId = new byte[16]; OBJECT_IDS.nextBytes(objectId);
        return new WritePlan(new Metadata(limits, codec, purpose, keyVersion, epoch, account,
                objectId, originalLength, packedLength, originalSha256));
    }

    /** Consume once even on failure/cancel. Retries/recompression MUST create a fresh plan. */
    public static void seal(WritePlan plan, byte[] accountPurposeDek, InputStream preparedPacked,
            OutputStream privateStage, BooleanSupplier cancelled) throws IOException, GeneralSecurityException {
        if (plan == null || !plan.used.compareAndSet(false, true)) throw argument();
        if (preparedPacked == null || privateStage == null || cancelled == null) throw argument();
        byte[] dek = fixed(accountPurposeDek, 32), key = null;
        Metadata m = plan.metadata;
        try {
            checkCancelled(cancelled);
            key = objectKey(dek, m);
            MessageDigest rawDigest = m.codec == Codec.RAW ? MessageDigest.getInstance("SHA-256") : null;
            privateStage.write(m.header.clone());
            byte[] plain = new byte[FRAME_BYTES];
            long offset = 0;
            try {
                for (long index = 0; index < m.frames; index++) {
                    int size = (int) Math.min(FRAME_BYTES, m.packedLength - offset);
                    readFully(preparedPacked, plain, size, cancelled);
                    if (rawDigest != null) rawDigest.update(plain, 0, size);
                    byte[] encrypted = crypt(Cipher.ENCRYPT_MODE, key, m.header, index, size, plain, size);
                    checkCancelled(cancelled);
                    privateStage.write(ByteBuffer.allocate(4).putInt(encrypted.length).array());
                    privateStage.write(encrypted);
                    offset += size;
                }
                requireEof(preparedPacked, cancelled);
                if (rawDigest != null && !MessageDigest.isEqual(rawDigest.digest(), m.digest)) throw invalid();
            } finally { Arrays.fill(plain, (byte) 0); }
        } finally { Arrays.fill(dek, (byte) 0); if (key != null) Arrays.fill(key, (byte) 0); }
    }

    /**
     * Authenticate each frame before writing to PRIVATE staging. On failure discard all
     * staging, including verified prefix. Returned metadata is framing success only:
     * ZLIB callers still must inflate/check original length+digest and recheck commit gate.
     */
    public static Metadata open(Metadata expected, byte[] accountPurposeDek, InputStream encryptedObject,
            OutputStream privatePackedStage, BooleanSupplier cancelled) throws IOException, GeneralSecurityException {
        if (expected == null || encryptedObject == null || privatePackedStage == null || cancelled == null) throw argument();
        byte[] dek = fixed(accountPurposeDek, 32), key = null;
        try {
            byte[] header = new byte[HEADER_BYTES]; readFully(encryptedObject, header, header.length, cancelled);
            if (!MessageDigest.isEqual(header, expected.header)) throw invalid();
            key = objectKey(dek, expected);
            MessageDigest rawDigest = expected.codec == Codec.RAW ? MessageDigest.getInstance("SHA-256") : null;
            byte[] length = new byte[4];
            long offset = 0;
            for (long index = 0; index < expected.frames; index++) {
                int size = (int) Math.min(FRAME_BYTES, expected.packedLength - offset);
                readFully(encryptedObject, length, 4, cancelled);
                if (ByteBuffer.wrap(length).getInt() != size + 16) throw invalid();
                byte[] cipher = new byte[size + 16]; readFully(encryptedObject, cipher, cipher.length, cancelled);
                byte[] verified = crypt(Cipher.DECRYPT_MODE, key, header, index, size, cipher, cipher.length);
                try {
                    if (verified.length != size) throw invalid();
                    if (rawDigest != null) rawDigest.update(verified);
                    checkCancelled(cancelled);
                    privatePackedStage.write(verified);
                } finally { Arrays.fill(verified, (byte) 0); }
                offset += size;
            }
            requireEof(encryptedObject, cancelled);
            if (rawDigest != null && !MessageDigest.isEqual(rawDigest.digest(), expected.digest)) throw invalid();
            return expected;
        } finally { Arrays.fill(dek, (byte) 0); if (key != null) Arrays.fill(key, (byte) 0); }
    }

    private static byte[] objectKey(byte[] dek, Metadata metadata) throws GeneralSecurityException {
        Mac extract = Mac.getInstance("HmacSHA256");
        extract.init(new SecretKeySpec(metadata.epoch, "HmacSHA256"));
        byte[] prk = extract.doFinal(dek);
        try {
            byte[] infoAndCounter = ByteBuffer.allocate(KEY_DOMAIN.length + 16 + 16 + 5)
                    .put(KEY_DOMAIN).put(metadata.account).put(metadata.object)
                    .putShort((short) metadata.purpose.code).putShort((short) 1).put((byte) 1).array();
            Mac expand = Mac.getInstance("HmacSHA256"); expand.init(new SecretKeySpec(prk, "HmacSHA256"));
            return expand.doFinal(infoAndCounter);
        } finally { Arrays.fill(prk, (byte) 0); }
    }

    private static byte[] crypt(int mode, byte[] key, byte[] header, long index, int plainSize,
            byte[] value, int size) throws GeneralSecurityException {
        byte[] nonce = ByteBuffer.allocate(12).putLong(0).putInt((int) index).array();
        byte[] aad = ByteBuffer.allocate(AAD_DOMAIN.length + HEADER_BYTES + 8)
                .put(AAD_DOMAIN).put(header).putInt((int) index).putInt(plainSize).array();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        cipher.updateAAD(aad);
        return cipher.doFinal(value, 0, size);
    }

    private static void readFully(InputStream stream, byte[] buffer, int size, BooleanSupplier cancelled) throws IOException {
        int offset = 0;
        while (offset < size) {
            checkCancelled(cancelled);
            int n = stream.read(buffer, offset, size - offset);
            if (n < 0 || n > size - offset) throw invalid();
            if (n == 0) {
                checkCancelled(cancelled);
                int single = stream.read(); if (single < 0) throw invalid();
                buffer[offset++] = (byte) single;
            } else { offset += n; }
        }
    }

    private static void requireEof(InputStream stream, BooleanSupplier cancelled) throws IOException {
        checkCancelled(cancelled); if (stream.read() != -1) throw invalid();
    }
    private static void checkCancelled(BooleanSupplier cancelled) throws InterruptedIOException {
        if (cancelled.getAsBoolean()) throw new InterruptedIOException("Object operation cancelled");
    }
    private static byte[] fixed(byte[] value, int size) {
        if (value == null || value.length != size) throw argument(); return value.clone();
    }
    private static IllegalArgumentException argument() { return new IllegalArgumentException("Invalid object crypto input"); }
    private static IOException invalid() { return new IOException("Invalid encrypted object"); }
    private ObjectAead() {}
}
