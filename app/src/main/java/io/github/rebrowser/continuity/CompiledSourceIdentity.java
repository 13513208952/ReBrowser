package io.github.rebrowser.continuity;

import io.github.rebrowser.authority.CompiledPrincipals;

/**
 * Immutable public Root identity from THIS APK's compiled table, not caller enrollment.
 * Missing formal Root is explicitly UNCONFIGURED, never a default/test/trust-on-first-use
 * key. Root equality is only one continuity leg: no signer acceptance, owner consent,
 * current policy digest, last-good write, source/data unlock or private-key custody here.
 */
public final class CompiledSourceIdentity {
    /** Must match standalone APK version; stale running code must not accept another installed version. */
    public static final long APPLICATION_VERSION_CODE=2_000_000L;
    public enum RootRelation { UNCONFIGURED, MATCH, DIFFERENT }
    private final String root;
    private CompiledSourceIdentity(String nativeCompiledRoot){
        if(nativeCompiledRoot!=null && !fingerprint(nativeCompiledRoot))throw new IllegalArgumentException("Invalid compiled Root fingerprint");root=nativeCompiledRoot;
    }
    public static CompiledSourceIdentity current(){return new CompiledSourceIdentity(CompiledPrincipals.production().rootSpkiSha256());}
    public boolean configured(){return root!=null;}
    public String requireRootSpkiSha256(){if(root==null)throw new IllegalStateException("Formal compiled source Root unconfigured");return root;}
    /** Native baseline metadata comparison only; signer/ledger provenance checked elsewhere. */
    public RootRelation compareBaselineRoot(LastGoodSourceStore.SourceEpoch nativeBaseline){
        if(nativeBaseline==null)throw new IllegalArgumentException("Missing native source baseline");
        return !configured()?RootRelation.UNCONFIGURED:rootMatchesLastGood(nativeBaseline.rootSha256())?RootRelation.MATCH:RootRelation.DIFFERENT;
    }
    public boolean rootMatchesLastGood(String nativeLastGoodRoot){
        if(!fingerprint(nativeLastGoodRoot))throw new IllegalArgumentException("Invalid native last-good Root fingerprint");
        return root!=null && root.equals(nativeLastGoodRoot);
    }
    private static boolean fingerprint(String value){return value!=null && value.matches("[0-9a-f]{64}");}
}
