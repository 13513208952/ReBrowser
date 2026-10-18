package io.github.rebrowser.control;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Immutable version-1 signed-frame bytes, not the outer JSON transport envelope.
 *
 * <p>This class validates framing, field bounds, visible ASCII and strict UTF-8 only.
 * It does NOT validate the args JSON DTO, signature/key, authority, challenge deadline,
 * target installation, source/data gate or replay state. A parsed frame is NOT admission.
 * The dispatcher must perform all those checks before using any requested action.
 * No reserialization of the raw args bytes is permitted for signature verification.
 */
public final class ControlEnvelope {
    public static final int MAX_FRAME_BYTES = 35_000;
    public static final int MAX_ARGS_BYTES = 32_768;
    private static final byte[] MAGIC =
            "ReBrowser.Control.Proof\0".getBytes(StandardCharsets.US_ASCII);
    private static final int VERSION = 1;

    /** Wire order is fixed; identifiers/semantics are validated by higher layers. */
    public enum Field {
        PROTOCOL(32, 0, true),
        APPLICATION_ID(128, 0, true),
        PRINCIPAL_KEY_ID(128, 0, true),
        TARGET_INSTALL_SPKI_SHA256(32, 32, false),
        DATA_EPOCH(16, 16, false),
        SERVER_INSTANCE_NONCE(32, 32, false),
        COMPILED_POLICY_SHA256(32, 32, false),
        DISABLE_REVISION(8, 8, false),
        CHALLENGE_ID(16, 16, false),
        NONCE(32, 32, false),
        DEADLINE_ELAPSED_MS(8, 8, false),
        REQUEST_ID(16, 16, false),
        ACTION_ID(128, 0, true),
        SCOPE_REF(256, 0, true),
        OWNER_GRANT_REF(128, 0, true),
        DELEGATION_SHA256(32, 32, false),
        ARGS_BYTES(MAX_ARGS_BYTES, 0, false);

        private final int max;
        private final int exact;
        private final boolean ascii;

        Field(int max, int exact, boolean ascii) {
            this.max = max;
            this.exact = exact;
            this.ascii = ascii;
        }
    }

    private final byte[][] fields;
    private final byte[] signedBytes;

    private ControlEnvelope(byte[][] fields, byte[] signedBytes) {
        // Both arrays are newly owned snapshots, never exposed to callers.
        this.fields = fields;
        this.signedBytes = signedBytes;
    }

    /**
     * Build from all 17 fields; only OWNER_GRANT_REF may be empty.
     * The input map must not be modified concurrently. All byte arrays are copied
     * before value validation; caller mutations after return cannot change this frame.
     */
    public static ControlEnvelope fromFields(Map<Field, byte[]> input) {
        Field[] order = Field.values();
        if (input == null || input.size() != order.length) throw invalid();
        byte[][] fields = new byte[order.length][];
        int size = MAGIC.length + 2 + 4 * order.length;
        for (Field field : order) {
            byte[] value = input.get(field);
            if (value == null) throw invalid();
            validateLength(field, value.length);
            byte[] snapshot = value.clone();
            validateValue(field, snapshot);
            fields[field.ordinal()] = snapshot;
            size += snapshot.length; // each bounded; sum cannot overflow int
        }
        if (size > MAX_FRAME_BYTES) throw invalid();
        ByteBuffer frame = ByteBuffer.allocate(size);
        frame.put(MAGIC).putShort((short) VERSION);
        for (byte[] value : fields) frame.putInt(value.length).put(value);
        return new ControlEnvelope(fields, frame.array());
    }

    /** Reject negative/oversize U32 lengths before any per-field allocation. */
    public static ControlEnvelope decode(byte[] input) {
        if (input == null || input.length > MAX_FRAME_BYTES
                || input.length < MAGIC.length + 2) throw invalid();
        byte[] snapshot = input.clone();
        ByteBuffer frame = ByteBuffer.wrap(snapshot);
        for (byte expected : MAGIC) if (frame.get() != expected) throw invalid();
        if ((frame.getShort() & 0xffff) != VERSION) throw invalid();
        Field[] order = Field.values();
        byte[][] fields = new byte[order.length][];
        for (Field field : order) {
            if (frame.remaining() < 4) throw invalid();
            int length = frame.getInt();
            // Values >= 2^31 are negative here and rejected, never cast/allocated.
            validateLength(field, length);
            if (length > frame.remaining()) throw invalid();
            byte[] value = new byte[length];
            frame.get(value);
            validateValue(field, value);
            fields[field.ordinal()] = value;
        }
        if (frame.hasRemaining()) throw invalid();
        return new ControlEnvelope(fields, snapshot);
    }

    /** Exact immutable-source bytes to feed a signature verifier, returned as a copy. */
    public byte[] signedBytes() {
        return signedBytes.clone();
    }

    public byte[] bytes(Field field) {
        if (field == null) throw invalid();
        return fields[field.ordinal()].clone();
    }

    /** Only fields explicitly defined as visible ASCII may use this accessor. */
    public String ascii(Field field) {
        if (field == null || !field.ascii) throw invalid();
        return new String(fields[field.ordinal()], StandardCharsets.US_ASCII);
    }

    /** Only the two U63BE fields may use this accessor; no decimal/coercion path. */
    public long unsigned63(Field field) {
        if (field != Field.DISABLE_REVISION && field != Field.DEADLINE_ELAPSED_MS) {
            throw invalid();
        }
        return ByteBuffer.wrap(fields[field.ordinal()]).getLong();
    }

    private static void validateLength(Field field, int length) {
        if (length < 0 || length > field.max
                || (length == 0 && field != Field.OWNER_GRANT_REF)
                || (field.exact != 0 && length != field.exact)) throw invalid();
    }

    private static void validateValue(Field field, byte[] value) {
        if (field.ascii) {
            for (byte b : value) {
                int unsigned = b & 0xff;
                if (unsigned < 0x21 || unsigned > 0x7e) throw invalid();
            }
        }
        if (field == Field.PROTOCOL
                && !"rebrowser.control/1".equals(new String(value, StandardCharsets.US_ASCII))) {
            throw invalid();
        }
        if (field == Field.APPLICATION_ID
                && !"io.github.rebrowser".equals(new String(value, StandardCharsets.US_ASCII))) {
            throw invalid();
        }
        if ((field == Field.DISABLE_REVISION || field == Field.DEADLINE_ELAPSED_MS)
                && value[0] < 0) throw invalid();
        if (field == Field.ARGS_BYTES) {
            try {
                StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(value));
            } catch (CharacterCodingException error) {
                throw invalid();
            }
        }
    }

    private static IllegalArgumentException invalid() {
        // Never include private args, principal IDs or raw wire bytes in diagnostics.
        return new IllegalArgumentException("Invalid control proof frame");
    }
}
