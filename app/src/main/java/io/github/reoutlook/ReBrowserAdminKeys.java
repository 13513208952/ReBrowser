package io.github.reoutlook;

import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;

/** Immutable public root for ReBrowser administrator control. */
final class ReBrowserAdminKeys {
    static final String REBROWSER_ROOT_ID = "rebrowser-root-v2";
    private static final String REBROWSER_EC_X509_BASE64 =
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAENOeoTzrWMF2moFGhvb3F0lFIhjUggnNq1Yg2LNbflpED22iWs2wRSHUNatwXTkOo3KGJvDcJqTAEaHKaefmkSA==";
    private static final String REBROWSER_FINGERPRINT =
            "d3e730cab1ad80e5319f7966a269ea94d8a8126788a3bc0a956785b81f97cd55";

    private ReBrowserAdminKeys() {}

    static String verify(String keyId, String challenge, String signatureBase64) throws Exception {
        if (!REBROWSER_ROOT_ID.equals(keyId)) {
            throw new SecurityException("Unknown or unavailable administrator key");
        }
        byte[] encodedPublic = Base64.decode(REBROWSER_EC_X509_BASE64, Base64.DEFAULT);
        Signature verifier = Signature.getInstance("SHA256withECDSA");
        verifier.initVerify(KeyFactory.getInstance("EC")
                .generatePublic(new X509EncodedKeySpec(encodedPublic)));
        verifier.update(challenge.getBytes(StandardCharsets.UTF_8));
        byte[] signature;
        try {
            signature = Base64.decode(signatureBase64, Base64.DEFAULT);
        } catch (IllegalArgumentException error) {
            throw new SecurityException("Malformed administrator signature", error);
        }
        if (!verifier.verify(signature)) {
            throw new SecurityException("Invalid administrator signature");
        }
        return REBROWSER_ROOT_ID;
    }

    static JSONArray capabilities() throws Exception {
        JSONArray keys = new JSONArray();
        keys.put(keyValue(REBROWSER_ROOT_ID, "ECDSA-P256/SHA-256",
                REBROWSER_FINGERPRINT, "rebrowser:root"));
        return keys;
    }

    private static JSONObject keyValue(
            String id,
            String algorithm,
            String fingerprint,
            String scope
    ) throws Exception {
        JSONObject value = new JSONObject();
        value.put("keyId", id);
        value.put("algorithm", algorithm);
        value.put("fingerprintSha256", fingerprint);
        value.put("scope", scope);
        value.put("runtimeRevocable", false);
        return value;
    }
}
