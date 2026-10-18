package io.github.rebrowser.crypto;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECFieldFp;
import java.security.spec.ECParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;

/**
 * Bounded canonical proof/key encodings. No private keys or key generation.
 * Format/curve validity is NOT signature verification, principal resolution or authority.
 * Raw control frames/args must retain their original bytes through verification.
 */
public final class CryptoEncoding {
    public static final int MAX_BINARY_BYTES = 65_536;
    private static final BigInteger P = new BigInteger(
            "ffffffff00000001000000000000000000000000ffffffffffffffffffffffff", 16);
    private static final BigInteger A = P.subtract(BigInteger.valueOf(3));
    private static final BigInteger B = new BigInteger(
            "5ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53b0f63bce3c3e27d2604b", 16);
    private static final BigInteger N = new BigInteger(
            "ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551", 16);
    private static final BigInteger GX = new BigInteger(
            "6b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296", 16);
    private static final BigInteger GY = new BigInteger(
            "4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5", 16);
    private static final byte[] SPKI_PREFIX = {
        0x30,0x59,0x30,0x13,0x06,0x07,0x2a,(byte)0x86,0x48,(byte)0xce,0x3d,0x02,0x01,
        0x06,0x08,0x2a,(byte)0x86,0x48,(byte)0xce,0x3d,0x03,0x01,0x07,0x03,0x42,0x00,0x04
    };

    private CryptoEncoding() {}

    /** Snapshot and bound binary inputs before processing them. */
    private static byte[] snapshot(byte[] value, int max) {
        if (value == null || value.length > max) throw invalid();
        return value.clone();
    }

    public static String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(snapshot(value, MAX_BINARY_BYTES));
    }

    /** Caller supplies its own stricter field limit, never an unbounded decode. */
    public static byte[] decodeBase64Url(String text, int maxDecodedBytes) {
        if (text == null || maxDecodedBytes < 0 || maxDecodedBytes > MAX_BINARY_BYTES) throw invalid();
        int maxEncoded = (maxDecodedBytes / 3) * 4
                + (maxDecodedBytes % 3 == 0 ? 0 : maxDecodedBytes % 3 + 1);
        if (text.length() > maxEncoded || text.length() % 4 == 1) throw invalid();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9') || c == '-' || c == '_')) throw invalid();
        }
        final byte[] decoded;
        try { decoded = Base64.getUrlDecoder().decode(text); }
        catch (IllegalArgumentException error) { throw invalid(); }
        if (decoded.length > maxDecodedBytes
                || !Base64.getUrlEncoder().withoutPadding().encodeToString(decoded).equals(text)) {
            throw invalid();
        }
        return decoded;
    }

    /** Strict P256 named-curve DER SPKI with one canonical uncompressed-point form. */
    public static ECPublicKey p256PublicKey(byte[] encoded) throws GeneralSecurityException {
        byte[] value = snapshot(encoded, 91);
        if (value.length != 91) throw invalid();
        for (int i = 0; i < SPKI_PREFIX.length; i++) {
            if (value[i] != SPKI_PREFIX[i]) throw invalid();
        }
        BigInteger x = new BigInteger(1, Arrays.copyOfRange(value, 27, 59));
        BigInteger y = new BigInteger(1, Arrays.copyOfRange(value, 59, 91));
        if (x.compareTo(P) >= 0 || y.compareTo(P) >= 0
                || !y.multiply(y).mod(P).equals(x.multiply(x).multiply(x).add(A.multiply(x)).add(B).mod(P))) {
            throw invalid();
        }
        Object parsed = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(value));
        if (!(parsed instanceof ECPublicKey)) throw invalid();
        ECPublicKey key = (ECPublicKey) parsed;
        ECParameterSpec params = key.getParams();
        if (params == null || !(params.getCurve().getField() instanceof ECFieldFp)
                || !P.equals(((ECFieldFp) params.getCurve().getField()).getP())
                || !A.equals(params.getCurve().getA()) || !B.equals(params.getCurve().getB())
                || !N.equals(params.getOrder()) || params.getCofactor() != 1
                || !GX.equals(params.getGenerator().getAffineX())
                || !GY.equals(params.getGenerator().getAffineY())
                || !x.equals(key.getW().getAffineX()) || !y.equals(key.getW().getAffineY())
                || !Arrays.equals(value, key.getEncoded())) throw invalid();
        return key;
    }

    /** Reject ASN1 aliases/zero/out-of-range scalars; do NOT enforce low-S. */
    public static void validateP256DerSignature(byte[] encoded) {
        byte[] value = snapshot(encoded, 72);
        if (value.length < 8 || value[0] != 0x30 || (value[1] & 255) != value.length - 2) {
            throw invalid();
        }
        int at = 2;
        for (int i = 0; i < 2; i++) {
            if (value.length - at < 2 || value[at++] != 2) throw invalid();
            int length = value[at++] & 255;
            if (length < 1 || length > 33 || length > value.length - at
                    || value[at] < 0
                    || (length > 1 && value[at] == 0 && value[at + 1] >= 0)) throw invalid();
            BigInteger scalar = new BigInteger(Arrays.copyOfRange(value, at, at + length));
            if (scalar.signum() <= 0 || scalar.compareTo(N) >= 0) throw invalid();
            at += length;
        }
        if (at != value.length) throw invalid();
    }

    /** Hash bounded metadata/proof bytes. Whole large mail files need streaming elsewhere. */
    public static byte[] sha256(byte[] encoded) {
        byte[] value = snapshot(encoded, MAX_BINARY_BYTES);
        try { return MessageDigest.getInstance("SHA-256").digest(value); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable", impossible); }
    }

    public static String sha256Hex(byte[] encoded) {
        byte[] digest = sha256(encoded);
        char[] chars = new char[digest.length * 2];
        final String alphabet = "0123456789abcdef";
        for (int i = 0; i < digest.length; i++) {
            chars[2 * i] = alphabet.charAt((digest[i] & 255) >>> 4);
            chars[2 * i + 1] = alphabet.charAt(digest[i] & 15);
        }
        return new String(chars);
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid crypto encoding");
    }
}
