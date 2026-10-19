package io.github.rebrowser.continuity;

import static org.junit.Assert.*;
import io.github.rebrowser.crypto.CryptoEncoding;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

/** Metadata reduction with PUBLIC toy octets, NOT real certificates/Android signing attestation. */
public final class SigningIdentityTest {
    private static byte[] bytes(String value){return value.getBytes(StandardCharsets.UTF_8);}
    @Test public void currentSignerAndSystemLineageAreSeparateFromAnyRootOrUnlock()throws Exception{
        byte[] a=bytes("public-toy-A"),b=bytes("public-toy-B");SigningIdentity.Identity id=SigningIdentity.fromSystemVerifiedCertificates(b,new byte[][]{a,b});
        assertEquals(CryptoEncoding.sha256Hex(b),id.currentCertificateSha256());assertTrue(id.acceptsLastGoodSigner(CryptoEncoding.sha256Hex(a)));assertTrue(id.acceptsLastGoodSigner(CryptoEncoding.sha256Hex(b)));assertFalse(id.acceptsLastGoodSigner(CryptoEncoding.sha256Hex(bytes("unknown"))));
    }
    @Test public void returnedHistoryAndInputMutationsCannotChangeFingerprint()throws Exception{
        byte[] current=bytes("public-toy-A");SigningIdentity.Identity id=SigningIdentity.fromSystemVerifiedCertificates(current,new byte[][]{current,current});String before=id.currentCertificateSha256();current[0]^=1;
        assertEquals(before,id.currentCertificateSha256());assertEquals(1,id.certificateHistorySha256().size());assertThrows(UnsupportedOperationException.class,()->id.certificateHistorySha256().clear());
    }
    @Test public void missingMismatchedOrOversizedNativeEvidenceNeverFallsBackToTrust(){
        byte[] a=bytes("A"),b=bytes("B");
        assertThrows(SigningIdentity.Unavailable.class,()->SigningIdentity.fromSystemVerifiedCertificates(a,null));assertThrows(SigningIdentity.Unavailable.class,()->SigningIdentity.fromSystemVerifiedCertificates(a,new byte[0][]));
        assertThrows(SigningIdentity.Unavailable.class,()->SigningIdentity.fromSystemVerifiedCertificates(a,new byte[][]{b}));assertThrows(SigningIdentity.Unavailable.class,()->SigningIdentity.fromSystemVerifiedCertificates(a,new byte[][]{null}));
        assertThrows(SigningIdentity.Unavailable.class,()->SigningIdentity.fromSystemVerifiedCertificates(new byte[0],new byte[][]{a}));assertThrows(SigningIdentity.Unavailable.class,()->SigningIdentity.fromSystemVerifiedCertificates(new byte[65537],new byte[][]{a}));
        assertThrows(SigningIdentity.Unavailable.class,()->SigningIdentity.fromSystemVerifiedCertificates(a,new byte[257][]));
    }
    @Test public void nativeAnchorMustBeExactLowercaseSha256NotAnyCallerLabel()throws Exception{
        byte[] a=bytes("A");SigningIdentity.Identity id=SigningIdentity.fromSystemVerifiedCertificates(a,new byte[][]{a});
        for(String value:new String[]{null,"","root-key","AA","../private",CryptoEncoding.sha256Hex(a).toUpperCase(java.util.Locale.ROOT)})assertThrows(IllegalArgumentException.class,()->id.acceptsLastGoodSigner(value));
    }
}
