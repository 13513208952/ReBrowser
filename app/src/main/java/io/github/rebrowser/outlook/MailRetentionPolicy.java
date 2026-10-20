package io.github.rebrowser.outlook;

/**
 * Conservative retention CLASSIFICATION only. It never deletes, rewrites references, changes
 * counters or turns a boolean into a deletion permit: only an explicitly rebuildable derivative
 * whose unique original is still observed available may become a reclaim CANDIDATE, and the
 * native provenance/transaction owner must still confirm it.
 */
public final class MailRetentionPolicy {
    public enum ContentClass { UNIQUE_ORIGINAL, DERIVED_REBUILDABLE, DERIVED_UNKNOWN }
    public enum Decision { KEEP, RECLAIM_CANDIDATE, UNKNOWN_KEEP }

    /** Native observation input; null means the fact was not observed (never assumed). */
    public static final class Observation {
        private final Boolean uniqueOriginalAvailable;
        private final Boolean dependentsPresent;
        private final Boolean referencedByPendingWork;
        public Observation(Boolean uniqueOriginalAvailable,Boolean dependentsPresent,Boolean referencedByPendingWork){
            this.uniqueOriginalAvailable=uniqueOriginalAvailable;
            this.dependentsPresent=dependentsPresent;
            this.referencedByPendingWork=referencedByPendingWork;
        }
        public Boolean uniqueOriginalAvailable(){return uniqueOriginalAvailable;}
        public Boolean dependentsPresent(){return dependentsPresent;}
        public Boolean referencedByPendingWork(){return referencedByPendingWork;}
    }

    public static Decision classify(ContentClass contentClass,Observation observation){
        if(contentClass==null || observation==null)throw new IllegalArgumentException("Retention classification input required");
        if(contentClass==ContentClass.UNIQUE_ORIGINAL)return Decision.KEEP;
        if(contentClass==ContentClass.DERIVED_UNKNOWN)return Decision.UNKNOWN_KEEP;
        if(Boolean.TRUE.equals(observation.referencedByPendingWork))return Decision.KEEP;
        if(Boolean.TRUE.equals(observation.dependentsPresent))return Decision.KEEP;
        // Unknown references or unfinished work must never be reinterpreted as absent.
        if(observation.referencedByPendingWork==null || observation.dependentsPresent==null)return Decision.UNKNOWN_KEEP;
        if(Boolean.TRUE.equals(observation.uniqueOriginalAvailable))return Decision.RECLAIM_CANDIDATE;
        return Decision.UNKNOWN_KEEP; // missing provenance/dependent facts are never "safe to reclaim"
    }

    /** Display text for the native UI; never an executed action or completion receipt. */
    public static String label(Decision decision){
        if(decision==null)throw new IllegalArgumentException("Missing retention decision");
        switch(decision){case KEEP:return "保留";case RECLAIM_CANDIDATE:return "可回收候选（待原生事务确认）";default:return "来源不明，保留";}
    }

    private MailRetentionPolicy(){}
}
