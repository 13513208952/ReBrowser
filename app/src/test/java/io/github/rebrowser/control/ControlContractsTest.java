package io.github.rebrowser.control;

import static org.junit.Assert.*;

import io.github.rebrowser.control.ControlEnvelope.Field;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.EnumMap;
import org.junit.Test;

/** Pure Java production-frame tests. No challenge/JSON/authorization/Android coverage claimed. */
public final class ControlContractsTest {
    private static final int HEADER = "ReBrowser.Control.Proof\0".length() + 2;
    private static final int[] MAX = {32,128,128,32,16,32,32,8,16,32,8,16,128,256,128,32,32768};
    private static final int[] EXACT = {0,0,0,32,16,32,32,8,16,32,8,16,0,0,0,32,0};

    private static byte[] utf8(String text) { return text.getBytes(StandardCharsets.UTF_8); }

    private static EnumMap<Field, byte[]> fields() {
        EnumMap<Field, byte[]> result = new EnumMap<>(Field.class);
        for (Field f : Field.values()) {
            int i = f.ordinal();
            byte[] b = new byte[EXACT[i] == 0 ? 1 : EXACT[i]];
            Arrays.fill(b, (byte) (i + 1));
            result.put(f, b);
        }
        result.put(Field.PROTOCOL, utf8("rebrowser.control/1"));
        result.put(Field.APPLICATION_ID, utf8("io.github.rebrowser"));
        result.put(Field.PRINCIPAL_KEY_ID, utf8("public-dummy-principal"));
        result.put(Field.DISABLE_REVISION, ByteBuffer.allocate(8).putLong(3).array());
        result.put(Field.DEADLINE_ELAPSED_MS, ByteBuffer.allocate(8).putLong(30000).array());
        result.put(Field.ACTION_ID, utf8("mail.query"));
        result.put(Field.SCOPE_REF, utf8("account_synthetic"));
        result.put(Field.OWNER_GRANT_REF, new byte[0]);
        result.put(Field.DELEGATION_SHA256, new byte[32]);
        result.put(Field.ARGS_BYTES, utf8("{\"query\":\"合成\",\"limit\":2}"));
        return result;
    }

    private static void reject(Runnable operation) {
        try { operation.run(); fail("must reject invalid input"); }
        catch (IllegalArgumentException expected) {
            assertEquals("Invalid control proof frame", expected.getMessage());
        }
    }

    private static int prefixOffset(byte[] frame, Field field) {
        ByteBuffer b = ByteBuffer.wrap(frame);
        b.position(HEADER);
        for (int i = 0; i < field.ordinal(); i++) { int n=b.getInt();b.position(b.position()+n); }
        return b.position();
    }

    @Test public void goldenFrameMatchesIndependentR14PythonAndJcaBytes() throws Exception {
        ControlEnvelope proof = ControlEnvelope.fromFields(fields());
        byte[] frame = proof.signedBytes();
        assertEquals(433, frame.length);
        StringBuilder hex = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(frame)) hex.append(String.format("%02x", b & 255));
        assertEquals("59095d82727035d2f0eaa0da1ba606f74fefd38352306b4278ce3e0fb0c4d001", hex.toString());
        ControlEnvelope decoded = ControlEnvelope.decode(frame);
        for (Field f : Field.values()) assertArrayEquals(fields().get(f), decoded.bytes(f));
        assertArrayEquals(frame, decoded.signedBytes());
        assertEquals(3L, decoded.unsigned63(Field.DISABLE_REVISION));
        assertEquals(30000L, decoded.unsigned63(Field.DEADLINE_ELAPSED_MS));
    }

    @Test public void historicalPublicScalarOneSignaturesStillVerify() throws Exception {
        // Public R14 fixture only; no real key material or new keys generated.
        String spki = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEaxfR8uEsQkf4vOblY6RA8ncDfYEt6zOg9KE5RdiYwpZP40Li_hp_m47n60p8D54WK84zV2sxXs7LtkBoN79R9Q";
        String[] signatures = {
            "MEYCIQCU0ZY6UBOMmUrOwly4Z3xr6yk6H_DlhL3JyDLQiLw7uwIhAP1d0grGvaikMFcw0IIjzYz83WVELc0ZC5QAqRidveH9",
            "MEUCIQCU0ZY6UBOMmUrOwly4Z3xr6yk6H_DlhL3JyDLQiLw7uwIgAqIt9DlCV1zPqM8vfdwycsAJlWl5SoV5X7khql6lQ1Q"
        };
        PublicKey key = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(Base64.getUrlDecoder().decode(spki)));
        for (String text : signatures) {
            Signature verifier = Signature.getInstance("SHA256withECDSA");verifier.initVerify(key);
            verifier.update(ControlEnvelope.fromFields(fields()).signedBytes());
            assertTrue(verifier.verify(Base64.getUrlDecoder().decode(text)));
        }
    }

    @Test public void rejectsEveryTruncationAndTrailingData() {
        byte[] frame=ControlEnvelope.fromFields(fields()).signedBytes();
        for (int n=0;n<frame.length;n++) { byte[] shortFrame=Arrays.copyOf(frame,n);reject(()->ControlEnvelope.decode(shortFrame)); }
        reject(()->ControlEnvelope.decode(Arrays.copyOf(frame,frame.length+1)));
        reject(()->ControlEnvelope.decode(new byte[ControlEnvelope.MAX_FRAME_BYTES+1]));
        reject(()->ControlEnvelope.decode(null));
    }

    @Test public void rejectsWrongMagicAndVersion() {
        byte[] good=ControlEnvelope.fromFields(fields()).signedBytes();
        for (int i=0;i<HEADER;i++) { byte[] bad=good.clone();bad[i]^=1;reject(()->ControlEnvelope.decode(bad)); }
    }

    @Test public void rejectsUnsignedOverflowAndBoundsBeforeFieldAllocation() {
        byte[] good=ControlEnvelope.fromFields(fields()).signedBytes();
        for (Field f:Field.values()) {
            for (int n:new int[]{-1,Integer.MIN_VALUE,Integer.MAX_VALUE,MAX[f.ordinal()]+1}) {
                byte[] bad=good.clone();ByteBuffer.wrap(bad).putInt(prefixOffset(bad,f),n);
                reject(()->ControlEnvelope.decode(bad));
            }
            EnumMap<Field,byte[]> map=fields();map.put(f,new byte[MAX[f.ordinal()]+1]);
            reject(()->ControlEnvelope.fromFields(map));
        }
    }

    @Test public void requiresAllSeventeenFieldsAndExactBinaryLengths() {
        reject(()->ControlEnvelope.fromFields(null));
        for (Field f:Field.values()) {
            EnumMap<Field,byte[]> missing=fields();missing.remove(f);reject(()->ControlEnvelope.fromFields(missing));
            EnumMap<Field,byte[]> nil=fields();nil.put(f,null);reject(()->ControlEnvelope.fromFields(nil));
            if(f!=Field.OWNER_GRANT_REF) { EnumMap<Field,byte[]> empty=fields();empty.put(f,new byte[0]);reject(()->ControlEnvelope.fromFields(empty)); }
            int exact=EXACT[f.ordinal()];
            if(exact!=0) {
                EnumMap<Field,byte[]> shortValue=fields();shortValue.put(f,new byte[exact-1]);reject(()->ControlEnvelope.fromFields(shortValue));
            }
        }
    }

    @Test public void acceptsOnlyVisibleAsciiWithoutSemanticCoercion() {
        for(Field f:new Field[]{Field.PRINCIPAL_KEY_ID,Field.ACTION_ID,Field.SCOPE_REF,Field.OWNER_GRANT_REF}) {
            for(int value=0;value<256;value++) {
                EnumMap<Field,byte[]> map=fields();map.put(f,new byte[]{(byte)value});
                if(value>=33 && value<=126) assertEquals(Character.toString((char)value),ControlEnvelope.fromFields(map).ascii(f));
                else reject(()->ControlEnvelope.fromFields(map));
            }
        }
        EnumMap<Field,byte[]> wrongProtocol=fields();wrongProtocol.put(Field.PROTOCOL,utf8("rebrowser.control/2"));reject(()->ControlEnvelope.fromFields(wrongProtocol));
        EnumMap<Field,byte[]> wrongApp=fields();wrongApp.put(Field.APPLICATION_ID,utf8("io.github.reoutlook"));reject(()->ControlEnvelope.fromFields(wrongApp));
    }

    @Test public void unsigned63HasNoSignedOrFloatingPointPath() {
        for(Field f:new Field[]{Field.DISABLE_REVISION,Field.DEADLINE_ELAPSED_MS}) {
            for(long n:new long[]{0,1,Long.MAX_VALUE}) {
                EnumMap<Field,byte[]> map=fields();map.put(f,ByteBuffer.allocate(8).putLong(n).array());
                assertEquals(n,ControlEnvelope.fromFields(map).unsigned63(f));
            }
            for(long n:new long[]{-1,Long.MIN_VALUE}) {
                EnumMap<Field,byte[]> map=fields();map.put(f,ByteBuffer.allocate(8).putLong(n).array());reject(()->ControlEnvelope.fromFields(map));
            }
        }
    }

    @Test public void strictUtf8RejectsOverlongSurrogateTruncatedAndOutOfRange() {
        byte[][] invalid={{(byte)0xc0,(byte)0xaf},{(byte)0x80},{(byte)0xed,(byte)0xa0,(byte)0x80},
            {(byte)0xf4,(byte)0x90,(byte)0x80,(byte)0x80},{(byte)0xe2,(byte)0x82}, {(byte)0xff}};
        for(byte[] bad:invalid) { EnumMap<Field,byte[]> map=fields();map.put(Field.ARGS_BYTES,bad);reject(()->ControlEnvelope.fromFields(map)); }
        byte[] good=ControlEnvelope.fromFields(fields()).signedBytes();
        int at=prefixOffset(good,Field.ARGS_BYTES)+4;good[at]=(byte)0xff;
        reject(()->ControlEnvelope.decode(good));
        EnumMap<Field,byte[]> unicode=fields();unicode.put(Field.ARGS_BYTES,utf8("{\"q\":\"合成😀\"}"));
        assertArrayEquals(unicode.get(Field.ARGS_BYTES),ControlEnvelope.fromFields(unicode).bytes(Field.ARGS_BYTES));
    }

    @Test public void rawUtf8IsNotReserializedOrMistakenForValidatedJson() {
        for(String raw:new String[]{" { \"q\" : 1 } ","{\"q\":1,\"q\":2}","not JSON"}) {
            EnumMap<Field,byte[]> map=fields();map.put(Field.ARGS_BYTES,utf8(raw));
            assertArrayEquals(utf8(raw),ControlEnvelope.decode(ControlEnvelope.fromFields(map).signedBytes()).bytes(Field.ARGS_BYTES));
        }
        // Duplicate-key and non-JSON text above MUST be rejected by the separate DTO decoder.
        // Framing success never implies those later checks ran or authority was established.
    }

    @Test public void callerInputAndAllOutputCopiesAreIndependent() {
        EnumMap<Field,byte[]> map=fields();ControlEnvelope created=ControlEnvelope.fromFields(map);
        byte[] original=created.signedBytes();Arrays.fill(map.get(Field.ARGS_BYTES),(byte)'!');map.clear();
        assertArrayEquals(original,created.signedBytes());
        byte[] input=original.clone();ControlEnvelope decoded=ControlEnvelope.decode(input);Arrays.fill(input,(byte)0);
        byte[] output=decoded.signedBytes();Arrays.fill(output,(byte)0);
        byte[] args=decoded.bytes(Field.ARGS_BYTES);Arrays.fill(args,(byte)0);
        assertArrayEquals(original,decoded.signedBytes());assertArrayEquals(fields().get(Field.ARGS_BYTES),decoded.bytes(Field.ARGS_BYTES));
    }

    @Test public void maximumArgsAndTypedAccessorsRemainBounded() {
        EnumMap<Field,byte[]> map=fields();byte[] args=new byte[ControlEnvelope.MAX_ARGS_BYTES];Arrays.fill(args,(byte)' ');map.put(Field.ARGS_BYTES,args);
        ControlEnvelope proof=ControlEnvelope.fromFields(map);assertTrue(proof.signedBytes().length<=ControlEnvelope.MAX_FRAME_BYTES);
        assertArrayEquals(args,ControlEnvelope.decode(proof.signedBytes()).bytes(Field.ARGS_BYTES));
        reject(()->proof.ascii(Field.ARGS_BYTES));reject(()->proof.ascii(null));reject(()->proof.bytes(null));
        reject(()->proof.unsigned63(Field.ARGS_BYTES));reject(()->proof.unsigned63(null));
    }
}
