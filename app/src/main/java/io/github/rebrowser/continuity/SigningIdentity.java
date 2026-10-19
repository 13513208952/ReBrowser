package io.github.rebrowser.continuity;

import android.app.Application;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Process;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Read THIS installed application's Android-verified single signer/rotation history.
 * This is NOT caller-package authentication, APK-content integrity, Root identity,
 * last-good persistence, private-key custody, or permission to unlock any old data.
 * Continuity must independently compare the last-good Root and complete owner flow.
 */
public final class SigningIdentity {
    private static final int MAX_CERT_BYTES=65_536,MAX_LINEAGE=256;
    public static final class Unavailable extends Exception {
        private Unavailable(){super("Own installed signing identity unavailable");}
    }
    public static final class Identity {
        private final String current;
        private final Set<String> history;
        private Identity(String current,Set<String> history){this.current=current;this.history=Collections.unmodifiableSet(new LinkedHashSet<>(history));}
        public String currentCertificateSha256(){return current;}
        /** Public certificate fingerprints only; Android attestation is from inspectOwn, not this DTO. */
        public Set<String> certificateHistorySha256(){return history;}
        /** Signer leg only. A different Root is NEVER legitimized by certificate rotation. */
        public boolean acceptsLastGoodSigner(String nativeLastGoodAnchor){
            if(nativeLastGoodAnchor==null || !nativeLastGoodAnchor.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Invalid native certificate anchor");
            return history.contains(nativeLastGoodAnchor);
        }
    }
    private SigningIdentity(){}
    public static Identity inspectOwn(Application nativeApplication)throws Unavailable{
        if(nativeApplication==null)throw new Unavailable();
        try{
            String packageName=nativeApplication.getPackageName();
            if(!"io.github.rebrowser".equals(packageName) && !"io.github.rebrowser.development".equals(packageName))throw new Unavailable();
            PackageManager manager=nativeApplication.getPackageManager();
            PackageInfo info=manager.getPackageInfo(packageName,PackageManager.GET_SIGNING_CERTIFICATES);
            if(info==null || !packageName.equals(info.packageName) || info.applicationInfo==null || info.applicationInfo.uid!=Process.myUid())throw new Unavailable();
            SigningInfo signing=info.signingInfo;if(signing==null || signing.hasMultipleSigners())throw new Unavailable();
            Signature[] current=signing.getApkContentsSigners(),history=signing.getSigningCertificateHistory();
            if(current==null || current.length!=1 || current[0]==null || history==null || history.length==0 || history.length>MAX_LINEAGE)throw new Unavailable();
            byte[][] bytes=new byte[history.length][];
            for(int i=0;i<history.length;i++){if(history[i]==null)throw new Unavailable();bytes[i]=history[i].toByteArray();if(bytes[i].length==0 || bytes[i].length>MAX_CERT_BYTES)throw new Unavailable();}
            return fromSystemVerifiedCertificates(current[0].toByteArray(),bytes);
        }catch(PackageManager.NameNotFoundException|RuntimeException failure){throw new Unavailable();}
    }
    /** Package-private metadata reduction only; NEVER expose as an IPC certificate-enrollment API. */
    static Identity fromSystemVerifiedCertificates(byte[] current,byte[][] history)throws Unavailable{
        if(history==null || history.length==0 || history.length>MAX_LINEAGE)throw new Unavailable();
        String fingerprint=hash(current);Set<String> fingerprints=new LinkedHashSet<>();
        for(byte[] certificate:history)fingerprints.add(hash(certificate));
        if(!fingerprints.contains(fingerprint))throw new Unavailable();
        return new Identity(fingerprint,fingerprints);
    }
    private static String hash(byte[] bytes)throws Unavailable{
        if(bytes==null || bytes.length==0 || bytes.length>MAX_CERT_BYTES)throw new Unavailable();
        try{byte[] digest=MessageDigest.getInstance("SHA-256").digest(bytes);StringBuilder value=new StringBuilder(64);for(byte b:digest){value.append("0123456789abcdef".charAt((b&255)>>>4));value.append("0123456789abcdef".charAt(b&15));}return value.toString();}
        catch(NoSuchAlgorithmException failure){throw new Unavailable();}
    }
}
