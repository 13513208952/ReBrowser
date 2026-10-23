package io.github.rebrowser.authority;

import io.github.rebrowser.crypto.CryptoEncoding;
import java.security.GeneralSecurityException;
import java.security.interfaces.ECPublicKey;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable APK-compiled principal identities, never populated from IPC/preferences.
 * Initial formal Root public key was generated under explicit owner authorization. Private
 * PKCS8 material/passphrase remain outside this repository; offline custody/backup handoff is
 * still pending. This public table does NOT establish current authority, owner consent, device
 * enrollment, trusted installation/source or business unlock. Never embed a private/test key.
 * Adding high identities requires changing this compiled source in a signed APK update.
 */
public final class CompiledPrincipals {
    public enum Role { LOW, HIGH, HIGHEST, ROOT }
    private static final Entry[] APK_ENTRIES = initialRootDeclarations();
    private static Entry[] initialRootDeclarations(){
        try{
            Entry root=new Entry("rebrowser-root-2897220b1306695b",Role.ROOT,AuthorityScale.ROOT,
                CryptoEncoding.decodeBase64Url("MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEQ33HLWXWc9Q72gzId2jhtp_gl8sA414eN8bvavgpW2zi-df7gwZ99FppR6OOakYvK3VvA4quBq6vj3ybEY79RQ",91),
                AuthorityScale.HIGH_BOUNDARY-1,java.util.Arrays.asList("host.openDefaultBrowserFlow","host.openPermissionSettings","host.openInstaller","host.window.back"));
            if(!root.spkiSha256().equals("2897220b1306695b955d7d8d91c9d34f23d342e9ae5260b68619363b02fd2f11"))throw invalid();
            return new Entry[]{root};
        }catch(GeneralSecurityException|IllegalArgumentException invalid){throw new ExceptionInInitializerError(invalid);}
    }
    private final Map<String, Entry> entries;
    private final Entry root;

    public static final class Entry {
        private final String keyId;
        private final Role role;
        private final int level;
        private final ECPublicKey key;
        private final String fingerprint;
        private final int maxLowDelegation;private final java.util.Set<String> delegationActions;
        // Package-private for compiled policy declarations and same-package HOST tests.
        Entry(String keyId, Role role, int level, byte[] canonicalSpki) throws GeneralSecurityException {this(keyId,role,level,canonicalSpki,-1,java.util.Collections.<String>emptyList());}
        Entry(String keyId,Role role,int level,byte[] canonicalSpki,int maxLowDelegation,java.util.Collection<String> delegationActions)throws GeneralSecurityException {
            if(maxLowDelegation < -1 || maxLowDelegation>Math.min(level,4) || delegationActions==null || delegationActions.size()>32)throw invalid();
            java.util.TreeSet<String> scope=new java.util.TreeSet<>();for(String action:delegationActions){io.github.rebrowser.control.ActionRegistry.require(action);if(!scope.add(action))throw invalid();}
            if((maxLowDelegation==-1)!=scope.isEmpty())throw invalid();this.maxLowDelegation=maxLowDelegation;this.delegationActions=java.util.Collections.unmodifiableSet(scope);
            if (keyId == null || keyId.length() == 0 || keyId.length() > 128 || role == null) throw invalid();
            for (int i = 0; i < keyId.length(); i++) if (keyId.charAt(i) < 33 || keyId.charAt(i) > 126) throw invalid();
            AuthorityScale.requireLevel(level);
            if ((role == Role.ROOT && level != AuthorityScale.ROOT)
                    || (role == Role.HIGHEST && level != AuthorityScale.HIGHEST)
                    || (role == Role.HIGH && (level < AuthorityScale.HIGH_BOUNDARY || level >= AuthorityScale.HIGHEST))
                    || (role == Role.LOW && level >= AuthorityScale.HIGH_BOUNDARY)) throw invalid();
            this.key = CryptoEncoding.p256PublicKey(canonicalSpki);
            this.fingerprint = CryptoEncoding.sha256Hex(key.getEncoded());
            this.keyId = keyId; this.role = role; this.level = level;
        }
        public String keyId() { return keyId; }
        public Role role() { return role; }
        public int level() { return level; }
        public String spkiSha256() { return fingerprint; }
        public int maxLowDelegation(){return maxLowDelegation;}
        public boolean permitsLowDelegationAction(String action){return delegationActions.contains(action);}
        ECPublicKey publicKey() { return key; }
    }

    public static CompiledPrincipals production() { return new CompiledPrincipals(APK_ENTRIES); }
    CompiledPrincipals(Entry... declarations) {
        if (declarations == null || declarations.length>128) throw invalid();
        Map<String, Entry> byId = new LinkedHashMap<>();Map<String, Entry> byKey = new LinkedHashMap<>();
        Entry rootEntry = null;
        for (Entry entry : declarations) {
            if (entry == null || byId.put(entry.keyId, entry) != null || byKey.put(entry.fingerprint, entry) != null) throw invalid();
            if (entry.role == Role.ROOT) { if (rootEntry != null) throw invalid(); rootEntry = entry; }
        }
        entries = Collections.unmodifiableMap(byId); root = rootEntry;
    }
    public Entry find(String keyId) { return entries.get(keyId); }
    public boolean formalRootConfigured() { return root != null; }
    /** Public APK metadata only; no runtime Root enrollment or private-key custody claim. */
    public String rootSpkiSha256() { return root == null ? null : root.fingerprint; }
    public int size() { return entries.size(); }
    public boolean containsFingerprint(String fingerprint){for(Entry entry:entries.values())if(entry.fingerprint.equals(fingerprint))return true;return false;}
    /** Canonical identity commitment, independent of declaration iteration order. */
    public byte[] identityPolicySha256() {
        try {
            java.io.ByteArrayOutputStream b=new java.io.ByteArrayOutputStream();java.io.DataOutputStream o=new java.io.DataOutputStream(b);
            o.write("ReBrowser.Compiled.Identities.1\0".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            java.util.TreeMap<String,Entry> ordered=new java.util.TreeMap<>(entries);o.writeInt(ordered.size());
            for(Entry e:ordered.values()){o.writeUTF(e.keyId);o.writeUTF(e.role.name());o.writeInt(e.level);o.writeInt(e.maxLowDelegation);o.writeInt(e.delegationActions.size());for(String action:e.delegationActions)o.writeUTF(action);byte[] key=e.key.getEncoded();o.writeInt(key.length);o.write(key);}
            return CryptoEncoding.sha256(b.toByteArray());
        }catch(java.io.IOException impossible){throw new AssertionError(impossible);}
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid compiled principal policy"); }
}
