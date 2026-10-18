package io.github.rebrowser.outlook;

import io.github.rebrowser.crypto.ObjectAead;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.function.BooleanSupplier;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Exact original-byte codec/hash preparation and verification into PRIVATE staging.
 * Never publishes, closes caller streams, authenticates AEAD, or commits DB refs/cursors.
 * A later failure can leave staged prefixes: abandon them. Successful ZLIB decode still
 * needs full enclosing AEAD/source/account/fence/transaction checks before publication.
 */
public final class MailObjectCodec {
    private static final int CHUNK = ObjectAead.FRAME_BYTES;
    private static final long MAX_PAYLOAD = 0xffff_ffffL * CHUNK;
    public enum Level { BALANCED(6), DENSE(9); private final int value; Level(int value) { this.value = value; } }
    public enum Selection { FRAMED_SAVING, NO_MEANINGFUL_SAVING, CANDIDATE_BUDGET }

    public static final class Limits {
        private final long original, packed;
        public Limits(long maxOriginalBytes, long maxPackedBytes) {
            if (maxOriginalBytes < 0 || maxPackedBytes < 0
                    || maxOriginalBytes > MAX_PAYLOAD || maxPackedBytes > MAX_PAYLOAD) throw argument();
            original = maxOriginalBytes; packed = maxPackedBytes;
        }
    }

    public static final class Prepared {
        private final ObjectAead.Codec codec;
        private final long original, packed, candidateBytes;
        private final byte[] digest;
        private final boolean completeCandidate;
        private final Selection selection;
        private Prepared(ObjectAead.Codec codec, long original, long packed, byte[] digest,
                long candidateBytes, boolean completeCandidate, Selection selection) {
            this.codec = codec; this.original = original; this.packed = packed; this.digest = digest;
            this.candidateBytes = candidateBytes; this.completeCandidate = completeCandidate; this.selection = selection;
        }
        public ObjectAead.Codec codec() { return codec; }
        public long originalBytes() { return original; }
        public long packedBytes() { return packed; }
        public byte[] originalSha256() { return digest.clone(); }
        public long candidateBytesWritten() { return candidateBytes; }
        public boolean candidateComplete() { return completeCandidate; }
        public Selection selection() { return selection; }
    }

    /**
     * Raw and candidate outputs must be distinct, native-owned private stages. Both can
     * hold data; return value selects one, it does NOT publish or delete the other.
     * Candidate budget exhaustion falls back to RAW if RAW fits the admitted packed cap.
     */
    public static Prepared prepare(InputStream original, OutputStream rawStage, OutputStream candidateStage,
            Limits limits, Level level, BooleanSupplier cancelled) throws IOException {
        if (original == null || rawStage == null || candidateStage == null || rawStage == candidateStage
                || limits == null || level == null || cancelled == null) throw argument();
        MessageDigest digest = sha();
        byte[] input = new byte[CHUNK];
        Candidate candidate = new Candidate(candidateStage, limits.packed, level, cancelled);
        long total = 0;
        try {
            while (true) {
                int n = readSome(original, input, input.length, cancelled);
                if (n < 0) break;
                if (n > limits.original - total) throw resource();
                digest.update(input, 0, n);
                candidate.feed(input, n); // fully consumes input before next source read
                check(cancelled); rawStage.write(input, 0, n);
                total += n;
            }
            candidate.finish(); check(cancelled);
            long rawWire = framed(total), packedWire = framed(candidate.written);
            long minimum = Math.max(32, rawWire / 20 + (rawWire % 20 == 0 ? 0 : 1));
            boolean usePacked = candidate.complete && rawWire - packedWire >= minimum;
            if (!usePacked && total > limits.packed) throw resource();
            Selection reason = usePacked ? Selection.FRAMED_SAVING
                    : candidate.complete ? Selection.NO_MEANINGFUL_SAVING : Selection.CANDIDATE_BUDGET;
            return new Prepared(usePacked ? ObjectAead.Codec.ZLIB : ObjectAead.Codec.RAW, total,
                    usePacked ? candidate.written : total, digest.digest(), candidate.written, candidate.complete, reason);
        } finally { candidate.close(); Arrays.fill(input, (byte) 0); }
    }

    /** Expected lengths/digest must be from the native catalog/authenticated object chain. */
    public static void decode(ObjectAead.Codec codec, long originalBytes, long packedBytes, byte[] originalSha256,
            Limits limits, InputStream packed, OutputStream privateOriginalStage, BooleanSupplier cancelled) throws IOException {
        if (codec == null || originalSha256 == null || originalSha256.length != 32 || limits == null
                || packed == null || privateOriginalStage == null || cancelled == null
                || originalBytes < 0 || packedBytes < 0) throw argument();
        if (originalBytes > limits.original || packedBytes > limits.packed) throw resource();
        byte[] expectedDigest = originalSha256.clone(), input = new byte[CHUNK], output = new byte[CHUNK];
        MessageDigest digest = sha();
        long read = 0, produced = 0;
        Inflater inflater = codec == ObjectAead.Codec.ZLIB ? new Inflater() : null;
        try {
            if (codec == ObjectAead.Codec.RAW) {
                if (originalBytes != packedBytes) throw invalid();
                while (read < packedBytes) {
                    int n = readSome(packed, input, (int) Math.min(input.length, packedBytes - read), cancelled);
                    if (n < 0) throw invalid();
                    digest.update(input, 0, n); check(cancelled); privateOriginalStage.write(input, 0, n);
                    read += n; produced += n;
                }
            } else {
                while (!inflater.finished()) {
                    check(cancelled);
                    if (inflater.needsInput()) {
                        if (read == packedBytes) throw invalid();
                        int n = readSome(packed, input, (int) Math.min(input.length, packedBytes - read), cancelled);
                        if (n < 0) throw invalid();
                        read += n; inflater.setInput(input, 0, n);
                    }
                    final int n;
                    try { n = inflater.inflate(output); }
                    catch (DataFormatException bad) { throw invalid(); }
                    if (n > 0) {
                        if (n > originalBytes - produced) throw invalid();
                        digest.update(output, 0, n); check(cancelled); privateOriginalStage.write(output, 0, n);
                        produced += n;
                    } else if (inflater.needsDictionary() || (!inflater.needsInput() && !inflater.finished())) {
                        throw invalid();
                    }
                }
                if (read != packedBytes || inflater.getRemaining() != 0) throw invalid();
            }
            check(cancelled);
            if (packed.read() != -1 || produced != originalBytes
                    || !MessageDigest.isEqual(digest.digest(), expectedDigest)) throw invalid();
            check(cancelled);
        } finally {
            if (inflater != null) inflater.end();
            Arrays.fill(input, (byte) 0); Arrays.fill(output, (byte) 0);
        }
    }

    private static final class Candidate {
        private final OutputStream stage;
        private final long max;
        private final BooleanSupplier cancelled;
        private Deflater deflater;
        private final byte[] output = new byte[CHUNK];
        private long written;
        private boolean complete;
        Candidate(OutputStream stage, long max, Level level, BooleanSupplier cancelled) {
            this.stage = stage; this.max = max; this.cancelled = cancelled;
            deflater = new Deflater(level.value); // zlib wrapper, not gzip/raw-deflate
        }
        void feed(byte[] input, int n) throws IOException {
            if (deflater == null) return;
            deflater.setInput(input, 0, n);
            while (deflater != null && !deflater.needsInput()) emit();
        }
        void finish() throws IOException {
            if (deflater == null) return;
            deflater.finish();
            while (deflater != null && !deflater.finished()) {
                long before = written;
                emit();
                if (deflater != null && written == before && !deflater.finished()) throw invalid();
            }
            complete = deflater != null && deflater.finished();
        }
        void emit() throws IOException {
            check(cancelled);
            int n = deflater.deflate(output);
            if (n == 0) {
                if (!deflater.needsInput() && !deflater.finished()) throw invalid();
                return;
            }
            if (n > max - written) { close(); return; }
            check(cancelled); stage.write(output, 0, n); written += n;
        }
        void close() {
            if (deflater != null) { deflater.end(); deflater = null; }
            Arrays.fill(output, (byte) 0);
        }
    }

    private static int readSome(InputStream stream, byte[] buffer, int max, BooleanSupplier cancelled) throws IOException {
        check(cancelled);
        int n = stream.read(buffer, 0, max);
        if (n > max || n < -1) throw invalid();
        if (n == 0) {
            check(cancelled); int single = stream.read();
            if (single < 0) return -1;
            buffer[0] = (byte) single; return 1;
        }
        return n;
    }
    private static long framed(long size) { return ObjectAead.HEADER_BYTES + size + 20 * Math.max(1, (size + CHUNK - 1) / CHUNK); }
    private static MessageDigest sha() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException unavailable) { throw new IllegalStateException("SHA-256 unavailable", unavailable); }
    }
    private static void check(BooleanSupplier cancelled) throws InterruptedIOException {
        if (cancelled.getAsBoolean()) throw new InterruptedIOException("Mail codec operation cancelled");
    }
    private static IllegalArgumentException argument() { return new IllegalArgumentException("Invalid mail codec input"); }
    private static IOException invalid() { return new IOException("Invalid mail object encoding"); }
    private static IOException resource() { return new IOException("Mail object resource limit"); }
    private MailObjectCodec() {}
}
