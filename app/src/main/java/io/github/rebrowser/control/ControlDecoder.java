package io.github.rebrowser.control;

import io.github.rebrowser.crypto.CryptoEncoding;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Strict bounded transport/JSON syntax decoder. Successful decoding is NOT admission.
 * ActionSchemas must still reject unknown action/args fields and invalid typed values;
 * key/delegation verification, target/source/epoch/policy/grant/challenge/replay checks
 * and atomic admission must all run before any domain action. Never reserialize args
 * to reconstruct signed bytes. No JSONObject coercion or signature verification here.
 */
public final class ControlDecoder {
    public static final int MAX_WIRE_BYTES = 65_536;
    public static final int MAX_CONTAINER_DEPTH = 16;
    public static final int MAX_VALUE_NODES = 4_096;
    public static final int MAX_NUMBER_TOKEN_CHARS = 128;
    private static final String PROTOCOL = "rebrowser.control/1";

    private ControlDecoder() {}

    /** Immutable typed syntax tree. All schema/range/authority checks remain external. */
    public static final class Value {
        public enum Kind { OBJECT, ARRAY, STRING, NUMBER, BOOLEAN, NULL }
        private final Kind kind;
        private final Object data;

        private Value(Kind kind, Object data) { this.kind = kind; this.data = data; }
        public Kind kind() { return kind; }
        private void require(Kind expected) { if (kind != expected) throw invalid(); }
        @SuppressWarnings("unchecked")
        public Map<String, Value> object() {
            require(Kind.OBJECT); return (Map<String, Value>) data;
        }
        @SuppressWarnings("unchecked")
        public List<Value> array() {
            require(Kind.ARRAY); return (List<Value>) data;
        }
        public String string() { require(Kind.STRING); return (String) data; }
        /** Original token only: never a Double, parsed integer, or coerced string. */
        public String numberToken() { require(Kind.NUMBER); return (String) data; }
        public boolean bool() { require(Kind.BOOLEAN); return (Boolean) data; }
    }

    /** No authority-bearing type or execution callback is returned by this parser. */
    public static final class UnverifiedProof {
        private final ControlEnvelope frame;
        private final byte[] signature;
        private final byte[] delegation;
        private final Value args;
        private UnverifiedProof(ControlEnvelope frame, byte[] signature, byte[] delegation, Value args) {
            this.frame = frame; this.signature = signature; this.delegation = delegation; this.args = args;
        }
        public ControlEnvelope frame() { return frame; }
        public byte[] signature() { return signature.clone(); }
        /** Empty means direct compiled identity, not a validated delegated identity. */
        public byte[] delegation() { return delegation.clone(); }
        public Value args() { return args; }
    }

    public static UnverifiedProof decode(byte[] wire) {
        try {
            Map<String, Value> object = parse(wire, MAX_WIRE_BYTES).object();
            for (String name : object.keySet()) {
                if (!name.equals("protocol") && !name.equals("frameB64")
                        && !name.equals("signatureB64") && !name.equals("delegationB64")) throw invalid();
            }
            if (!object.containsKey("protocol") || !object.containsKey("frameB64")
                    || !object.containsKey("signatureB64")) throw invalid();
            if (!PROTOCOL.equals(object.get("protocol").string())) throw invalid();
            ControlEnvelope frame = ControlEnvelope.decode(CryptoEncoding.decodeBase64Url(
                    object.get("frameB64").string(), ControlEnvelope.MAX_FRAME_BYTES));
            byte[] signature = CryptoEncoding.decodeBase64Url(object.get("signatureB64").string(), 72);
            CryptoEncoding.validateP256DerSignature(signature);
            byte[] delegation = new byte[0];
            byte[] signedDigest = frame.bytes(ControlEnvelope.Field.DELEGATION_SHA256);
            boolean direct = MessageDigest.isEqual(signedDigest, new byte[32]);
            if (object.containsKey("delegationB64")) {
                delegation = CryptoEncoding.decodeBase64Url(object.get("delegationB64").string(), 8_192);
                if (direct || delegation.length == 0
                        || !MessageDigest.isEqual(signedDigest, CryptoEncoding.sha256(delegation))) throw invalid();
            } else if (!direct) throw invalid();
            Value args = parse(frame.bytes(ControlEnvelope.Field.ARGS_BYTES), ControlEnvelope.MAX_ARGS_BYTES);
            args.object(); // exactly one DTO object, not a scalar/array/null
            return new UnverifiedProof(frame, signature, delegation, args);
        } catch (IllegalArgumentException error) {
            // Do not propagate decoder messages containing any input-specific context.
            throw invalid();
        }
    }

    private static Value parse(byte[] bytes, int maxBytes) {
        if (bytes == null || bytes.length > maxBytes) throw invalid();
        final String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes.clone())).toString();
        } catch (CharacterCodingException error) { throw invalid(); }
        Parser parser = new Parser(text);
        Value result = parser.value(0);
        parser.space();
        if (parser.at != text.length()) throw invalid();
        return result;
    }

    private static final class Parser {
        private final String text;
        private int at;
        private int nodes;
        Parser(String text) { this.text = text; }
        char peek() { return at < text.length() ? text.charAt(at) : '\0'; }
        boolean take(char c) {
            if (at < text.length() && text.charAt(at) == c) { at++; return true; }
            return false;
        }
        void expect(char c) { if (!take(c)) throw invalid(); }
        void space() {
            while (peek() == ' ' || peek() == '\t' || peek() == '\n' || peek() == '\r') at++;
        }
        static boolean digit(char c) { return c >= '0' && c <= '9'; }
        Value value(int depth) {
            space();
            if (++nodes > MAX_VALUE_NODES) throw invalid();
            char c = peek();
            if (c == '{' || c == '[') {
                if (depth >= MAX_CONTAINER_DEPTH) throw invalid();
                return c == '{' ? object(depth + 1) : array(depth + 1);
            }
            if (c == '"') return new Value(Value.Kind.STRING, string());
            if (c == '-' || digit(c)) return new Value(Value.Kind.NUMBER, number());
            if (text.startsWith("true", at)) { at += 4; return new Value(Value.Kind.BOOLEAN, true); }
            if (text.startsWith("false", at)) { at += 5; return new Value(Value.Kind.BOOLEAN, false); }
            if (text.startsWith("null", at)) { at += 4; return new Value(Value.Kind.NULL, null); }
            throw invalid();
        }
        Value object(int depth) {
            expect('{'); space();
            Map<String, Value> values = new LinkedHashMap<>();
            if (!take('}')) {
                do {
                    space();
                    String key = string();
                    if (values.containsKey(key)) throw invalid();
                    space(); expect(':');
                    values.put(key, value(depth));
                    space();
                    if (take('}')) return new Value(Value.Kind.OBJECT, Collections.unmodifiableMap(values));
                    expect(',');
                } while (true);
            }
            return new Value(Value.Kind.OBJECT, Collections.unmodifiableMap(values));
        }
        Value array(int depth) {
            expect('['); space();
            List<Value> values = new ArrayList<>();
            if (!take(']')) {
                do {
                    values.add(value(depth)); space();
                    if (take(']')) return new Value(Value.Kind.ARRAY, Collections.unmodifiableList(values));
                    expect(',');
                } while (true);
            }
            return new Value(Value.Kind.ARRAY, Collections.unmodifiableList(values));
        }
        String string() {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (at < text.length()) {
                char c = text.charAt(at++);
                if (c == '"') return out.toString();
                if (c < 0x20) throw invalid();
                if (c == '\\') {
                    if (at == text.length()) throw invalid();
                    char escaped = text.charAt(at++);
                    switch (escaped) {
                        case '"': case '\\': case '/': out.append(escaped); break;
                        case 'b': out.append('\b'); break;
                        case 'f': out.append('\f'); break;
                        case 'n': out.append('\n'); break;
                        case 'r': out.append('\r'); break;
                        case 't': out.append('\t'); break;
                        case 'u':
                            char unit = hexUnit();
                            if (Character.isHighSurrogate(unit)) {
                                expect('\\'); expect('u');
                                char low = hexUnit();
                                if (!Character.isLowSurrogate(low)) throw invalid();
                                out.append(unit).append(low);
                            } else {
                                if (Character.isLowSurrogate(unit)) throw invalid();
                                out.append(unit);
                            }
                            break;
                        default: throw invalid();
                    }
                } else if (Character.isHighSurrogate(c)) {
                    if (at == text.length() || !Character.isLowSurrogate(text.charAt(at))) throw invalid();
                    out.append(c).append(text.charAt(at++));
                } else {
                    if (Character.isLowSurrogate(c)) throw invalid();
                    out.append(c);
                }
            }
            throw invalid();
        }
        char hexUnit() {
            if (text.length() - at < 4) throw invalid();
            int result = 0;
            for (int i = 0; i < 4; i++) {
                char c = text.charAt(at++);
                int n = c >= '0' && c <= '9' ? c - '0'
                        : c >= 'a' && c <= 'f' ? c - 'a' + 10
                        : c >= 'A' && c <= 'F' ? c - 'A' + 10 : -1;
                if (n < 0) throw invalid();
                result = result * 16 + n;
            }
            return (char) result;
        }
        String number() {
            int start = at;
            take('-');
            if (take('0')) {
                if (digit(peek())) throw invalid();
            } else {
                if (peek() < '1' || peek() > '9') throw invalid();
                while (digit(peek())) at++;
            }
            if (take('.')) digits();
            if (take('e') || take('E')) {
                if (!take('+')) take('-');
                digits();
            }
            if (at - start > MAX_NUMBER_TOKEN_CHARS) throw invalid();
            return text.substring(start, at);
        }
        void digits() {
            if (!digit(peek())) throw invalid();
            while (digit(peek())) at++;
        }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid control proof envelope");
    }
}
