package io.github.rebrowser.authority;

import io.github.rebrowser.crypto.CryptoEncoding;
import java.security.GeneralSecurityException;
import java.security.interfaces.ECPublicKey;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable APK-compiled principal identities, never populated from IPC/preferences.
 * Formal key custody/provisioning has not happened: current APK table is EMPTY and
 * all administrator proofs fail closed. Do not embed the public scalar1 test key here.
 * Adding high identities requires changing this compiled source in a signed APK update.
 */
public final class CompiledPrincipals {
    public enum Role { LOW, HIGH, HIGHEST, ROOT }
    private static final Entry[] APK_ENTRIES = {};
    private final Map<String, Entry> entries;
    private final Entry root;

    public static final class Entry {
        private final String keyId;
        private final Role role;
        private final int level;
        private final ECPublicKey key;
        private final String fingerprint;
        // Package-private for compiled policy declarations and same-package HOST tests.
        Entry(String keyId, Role role, int level, byte[] canonicalSpki) throws GeneralSecurityException {
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
        ECPublicKey publicKey() { return key; }
    }

    public static CompiledPrincipals production() { return new CompiledPrincipals(APK_ENTRIES); }
    CompiledPrincipals(Entry... declarations) {
        if (declarations == null) throw invalid();
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
    public int size() { return entries.size(); }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid compiled principal policy"); }
}
