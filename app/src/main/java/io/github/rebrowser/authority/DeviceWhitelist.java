package io.github.rebrowser.authority;

import io.github.rebrowser.crypto.CryptoEncoding;
import java.security.GeneralSecurityException;
import java.security.interfaces.ECPublicKey;
import java.util.*;

/** Immutable APK whitelist ownership only. No device names/serials/account metadata, mutable
 * enrollment, exemption boolean or raw cross-owner enumeration. Membership is NOT possession,
 * enabled policy, owner consent or action admission. Production intentionally has zero devices. */
public final class DeviceWhitelist {
    static final class Device {
        final String owner,fingerprint;final ECPublicKey key;
        Device(String owner,byte[] spki)throws GeneralSecurityException{if(owner==null)throw invalid();this.owner=owner;key=CryptoEncoding.p256PublicKey(spki);fingerprint=CryptoEncoding.sha256Hex(key.getEncoded());}
    }
    private final CompiledPrincipals compiled;private final List<Device> devices;
    public static DeviceWhitelist production(CompiledPrincipals compiled){return new DeviceWhitelist(compiled,new Device[0]);}
    // Compiled declarations + same-package tests only; never import from IPC/preferences.
    DeviceWhitelist(CompiledPrincipals compiled,Device... declarations){
        if(compiled==null || declarations==null || declarations.length>1024)throw invalid();this.compiled=compiled;List<Device> accepted=new ArrayList<>();Set<String> pairs=new HashSet<>();
        for(Device d:declarations){CompiledPrincipals.Entry owner=d==null?null:compiled.find(d.owner);if(owner==null || (owner.role()!=CompiledPrincipals.Role.ROOT && owner.role()!=CompiledPrincipals.Role.HIGHEST) || !pairs.add(d.owner+":"+d.fingerprint))throw invalid();accepted.add(d);}devices=Collections.unmodifiableList(accepted);
    }
    /** Returns only requested eligible key; no nearest-owner fallback, no label matching. */
    Device eligible(AdminProofVerifier.KeyProof key,String exactFingerprint){
        if(key==null || !key.belongsTo(compiled) || !compiled.formalRootConfigured() || exactFingerprint==null || !exactFingerprint.matches("[0-9a-f]{64}"))throw denied();
        CompiledPrincipals.Entry principal=key.compiledPrincipal();if(principal.role()!=CompiledPrincipals.Role.ROOT && principal.role()!=CompiledPrincipals.Role.HIGHEST)throw denied();
        for(Device device:devices)if(device.fingerprint.equals(exactFingerprint) && (principal.role()==CompiledPrincipals.Role.ROOT || device.owner.equals(principal.keyId())))return device;
        throw denied();
    }
    public int compiledDeviceEntryCount(){return devices.size();} // Public APK metadata, no identities.
    private static SecurityException denied(){return new SecurityException("Device exemption unavailable");}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid compiled device ownership");}
}
