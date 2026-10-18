package io.github.rebrowser.crypto;

import static org.junit.Assert.*;

import io.github.rebrowser.control.ControlEnvelope;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;
import java.util.Base64;
import java.util.Random;
import org.junit.Test;

/** Public scalar1 vectors and malformed inputs only. No real key generation or Android claim. */
public final class CryptoContractsTest {
    private static final String SPKI = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEaxfR8uEsQkf4vOblY6RA8ncDfYEt6zOg9KE5RdiYwpZP40Li_hp_m47n60p8D54WK84zV2sxXs7LtkBoN79R9Q";
    private static final String[] SIGNATURES = {
        "MEYCIQCU0ZY6UBOMmUrOwly4Z3xr6yk6H_DlhL3JyDLQiLw7uwIhAP1d0grGvaikMFcw0IIjzYz83WVELc0ZC5QAqRidveH9",
        "MEUCIQCU0ZY6UBOMmUrOwly4Z3xr6yk6H_DlhL3JyDLQiLw7uwIgAqIt9DlCV1zPqM8vfdwycsAJlWl5SoV5X7khql6lQ1Q"
    };
    private static final BigInteger N = new BigInteger("ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551",16);
    private interface Checked { void run() throws Exception; }
    private static byte[] publicSpki() { return Base64.getUrlDecoder().decode(SPKI); }
    private static void reject(Checked operation) throws Exception {
        try { operation.run();fail("must reject noncanonical encoding"); }
        catch(IllegalArgumentException expected) { assertEquals("Invalid crypto encoding",expected.getMessage()); }
    }
    private static byte[] der(BigInteger r, BigInteger s) {
        byte[] a=r.toByteArray(),b=s.toByteArray();ByteArrayOutputStream out=new ByteArrayOutputStream();
        out.write(0x30);out.write(4+a.length+b.length);out.write(2);out.write(a.length);out.write(a,0,a.length);
        out.write(2);out.write(b.length);out.write(b,0,b.length);return out.toByteArray();
    }

    @Test public void base64RoundTripsEveryShortSizeAndMaximum() throws Exception {
        for(int length=0;length<=513;length++) {
            byte[] raw=new byte[length];for(int i=0;i<length;i++)raw[i]=(byte)(i*29+length);
            String encoded=CryptoEncoding.base64Url(raw);
            assertArrayEquals(raw,CryptoEncoding.decodeBase64Url(encoded,length));
            if(length!=0)reject(()->CryptoEncoding.decodeBase64Url(encoded,raw.length-1));
        }
        byte[] maximum=new byte[CryptoEncoding.MAX_BINARY_BYTES];Arrays.fill(maximum,(byte)0xff);
        assertArrayEquals(maximum,CryptoEncoding.decodeBase64Url(CryptoEncoding.base64Url(maximum),maximum.length));
        reject(()->CryptoEncoding.base64Url(new byte[CryptoEncoding.MAX_BINARY_BYTES+1]));
    }

    @Test public void base64RejectsAlphabetPaddingWhitespaceAndPadBits() throws Exception {
        for(String bad:new String[]{"A","AB","AAB","AA=","AA=="," AA","AA\n","AA\r","AA\t","A+","A/","合成","\ud800"}) {
            reject(()->CryptoEncoding.decodeBase64Url(bad,100));
        }
        reject(()->CryptoEncoding.decodeBase64Url(null,10));reject(()->CryptoEncoding.base64Url(null));
        reject(()->CryptoEncoding.decodeBase64Url("",-1));
        reject(()->CryptoEncoding.decodeBase64Url("",CryptoEncoding.MAX_BINARY_BYTES+1));
        assertArrayEquals(new byte[0],CryptoEncoding.decodeBase64Url("",0));
    }

    @Test public void spkiCanonicalP256PublicPointAndCopies() throws Exception {
        byte[] encoded=publicSpki();ECPublicKey key=CryptoEncoding.p256PublicKey(encoded);
        assertArrayEquals(encoded,key.getEncoded());assertEquals(256,key.getParams().getCurve().getField().getFieldSize());
        Arrays.fill(encoded,(byte)0);assertArrayEquals(publicSpki(),key.getEncoded());
        // Public negative generator point is also valid, but is a DIFFERENT public key.
        BigInteger p=new BigInteger("ffffffff00000001000000000000000000000000ffffffffffffffffffffffff",16);
        byte[] negative=publicSpki();byte[] y=p.subtract(key.getW().getAffineY()).toByteArray();
        Arrays.fill(negative,59,91,(byte)0);int offset=Math.max(0,y.length-32);
        System.arraycopy(y,offset,negative,91-(y.length-offset),y.length-offset);
        ECPublicKey other=CryptoEncoding.p256PublicKey(negative);
        assertNotEquals(key.getW(),other.getW());assertNotEquals(CryptoEncoding.sha256Hex(publicSpki()),CryptoEncoding.sha256Hex(negative));
    }

    @Test public void spkiRejectsAliasesWrongOidsAndAllTruncations() throws Exception {
        byte[] good=publicSpki();
        for(int n=0;n<good.length;n++) { byte[] shortKey=Arrays.copyOf(good,n);reject(()->CryptoEncoding.p256PublicKey(shortKey)); }
        reject(()->CryptoEncoding.p256PublicKey(Arrays.copyOf(good,good.length+1)));
        reject(()->CryptoEncoding.p256PublicKey(null));
        for(int i=0;i<27;i++) { byte[] bad=good.clone();bad[i]^=1;reject(()->CryptoEncoding.p256PublicKey(bad)); }
        byte[] compressed=good.clone();compressed[26]=2;reject(()->CryptoEncoding.p256PublicKey(compressed));
    }

    @Test public void spkiRejectsOutOfFieldInfinityAndOffCurve() throws Exception {
        byte[] good=publicSpki();byte[] offCurve=good.clone();offCurve[90]^=1;reject(()->CryptoEncoding.p256PublicKey(offCurve));
        byte[] zero=good.clone();Arrays.fill(zero,27,91,(byte)0);reject(()->CryptoEncoding.p256PublicKey(zero));
        byte[] p=new BigInteger("ffffffff00000001000000000000000000000000ffffffffffffffffffffffff",16).toByteArray();
        for(int at:new int[]{27,59}) { byte[] bad=good.clone();System.arraycopy(p,p.length-32,bad,at,32);reject(()->CryptoEncoding.p256PublicKey(bad)); }
    }

    @Test public void derAcceptsMinimalBoundaryScalarsIncludingHighS() {
        BigInteger[] values={BigInteger.ONE,BigInteger.valueOf(127),BigInteger.valueOf(128),BigInteger.valueOf(255),BigInteger.valueOf(256),N.subtract(BigInteger.ONE)};
        for(BigInteger r:values)for(BigInteger s:values)CryptoEncoding.validateP256DerSignature(der(r,s));
        assertEquals(8,der(BigInteger.ONE,BigInteger.ONE).length);
        assertEquals(72,der(N.subtract(BigInteger.ONE),N.subtract(BigInteger.ONE)).length);
    }

    @Test public void derRejectsZeroNegativeOversizeAndOrderScalars() throws Exception {
        for(BigInteger scalar:new BigInteger[]{BigInteger.ZERO,BigInteger.valueOf(-1),N,N.add(BigInteger.ONE),BigInteger.ONE.shiftLeft(264)}) {
            reject(()->CryptoEncoding.validateP256DerSignature(der(scalar,BigInteger.ONE)));
            reject(()->CryptoEncoding.validateP256DerSignature(der(BigInteger.ONE,scalar)));
        }
        reject(()->CryptoEncoding.validateP256DerSignature(null));
        reject(()->CryptoEncoding.validateP256DerSignature(new byte[73]));
    }

    @Test public void derRejectsBerAliasesSuffixAndMalformedTags() throws Exception {
        byte[][] invalid={
            {0x30,7,2,2,0,1,2,1,1}, // unnecessary INTEGER sign padding
            {0x30,6,2,1,(byte)0x80,2,1,1}, // negative INTEGER
            {0x30,6,3,1,1,2,1,1}, // wrong tag
            {0x30,(byte)0x81,6,2,1,1,2,1,1}, // BER long length, not DER
            {0x30,6,2,0,1,2,1,1}, {0x30,6,2,33,1,2,1,1}
        };
        for(byte[] bad:invalid)reject(()->CryptoEncoding.validateP256DerSignature(bad));
        for(String text:SIGNATURES) {
            byte[] good=Base64.getUrlDecoder().decode(text);
            for(int n=0;n<good.length;n++) { byte[] shortSig=Arrays.copyOf(good,n);reject(()->CryptoEncoding.validateP256DerSignature(shortSig)); }
            reject(()->CryptoEncoding.validateP256DerSignature(Arrays.copyOf(good,good.length+1)));
        }
    }

    @Test public void productionFrameBytesAndBothHistoricSignaturesWorkTogether() throws Exception {
        String frameB64="UmVCcm93c2VyLkNvbnRyb2wuUHJvb2YAAAEAAAATcmVicm93c2VyLmNvbnRyb2wvMQAAABNpby5naXRodWIucmVicm93c2VyAAAAFnB1YmxpYy1kdW1teS1wcmluY2lwYWwAAAAgBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQAAAAQBQUFBQUFBQUFBQUFBQUFBQAAACAGBgYGBgYGBgYGBgYGBgYGBgYGBgYGBgYGBgYGBgYGBgAAACAHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwAAAAgAAAAAAAAAAwAAABAJCQkJCQkJCQkJCQkJCQkJAAAAIAoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKAAAACAAAAAAAAHUwAAAAEAwMDAwMDAwMDAwMDAwMDAwAAAAKbWFpbC5xdWVyeQAAABFhY2NvdW50X3N5bnRoZXRpYwAAAAAAAAAgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAceyJxdWVyeSI6IuWQiOaIkCIsImxpbWl0IjoyfQ";
        ControlEnvelope envelope=ControlEnvelope.decode(CryptoEncoding.decodeBase64Url(frameB64,ControlEnvelope.MAX_FRAME_BYTES));
        assertEquals("59095d82727035d2f0eaa0da1ba606f74fefd38352306b4278ce3e0fb0c4d001",CryptoEncoding.sha256Hex(envelope.signedBytes()));
        ECPublicKey key=CryptoEncoding.p256PublicKey(CryptoEncoding.decodeBase64Url(SPKI,91));
        for(String text:SIGNATURES) {
            byte[] signature=CryptoEncoding.decodeBase64Url(text,72);CryptoEncoding.validateP256DerSignature(signature);
            Signature verifier=Signature.getInstance("SHA256withECDSA");verifier.initVerify(key);verifier.update(envelope.signedBytes());assertTrue(verifier.verify(signature));
            byte[] changed=envelope.signedBytes();changed[changed.length-1]^=1;
            verifier.initVerify(key);verifier.update(changed);assertFalse(verifier.verify(signature));
        }
    }

    @Test public void seededDerMutationsCannotBypassJcaSyntaxOrArrayBounds() throws Exception {
        Random random=new Random(0x44455231L);byte[] valid=Base64.getUrlDecoder().decode(SIGNATURES[0]);
        ECPublicKey key=CryptoEncoding.p256PublicKey(publicSpki());
        for(int i=0;i<2000;i++) {
            byte[] candidate=i%2==0?valid.clone():new byte[random.nextInt(100)];
            if(i%2==0)candidate[random.nextInt(candidate.length)]^=(byte)(1+random.nextInt(255));
            else random.nextBytes(candidate);
            try { CryptoEncoding.validateP256DerSignature(candidate); }
            catch(IllegalArgumentException expected) { assertEquals("Invalid crypto encoding",expected.getMessage());continue; }
            Signature verifier=Signature.getInstance("SHA256withECDSA");verifier.initVerify(key);
            verifier.update(new byte[]{1,2,3});
            assertFalse(verifier.verify(candidate)); // syntax valid, but never an admission proof
        }
    }

    @Test public void sha256KnownDigestsAndBoundedIndependentCopies() throws Exception {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",CryptoEncoding.sha256Hex(new byte[0]));
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",CryptoEncoding.sha256Hex(new byte[]{97,98,99}));
        byte[] digest=CryptoEncoding.sha256(new byte[0]);Arrays.fill(digest,(byte)0);assertFalse(Arrays.equals(digest,CryptoEncoding.sha256(new byte[0])));
        reject(()->CryptoEncoding.sha256(null));reject(()->CryptoEncoding.sha256(new byte[CryptoEncoding.MAX_BINARY_BYTES+1]));
    }
}
