package io.github.rebrowser.system;

/**
 * Bounded advice about which OWN rebuildable caches may be released under memory pressure. It
 * releases nothing, closes nothing and never cancels/ends any chain. Unique content, unfinished
 * work, foreground UI and active media are never candidates, and hiding the UI is not proof that
 * service-owned music has stopped.
 */
public final class MemoryPressurePolicy {
    public enum Candidate { NONE, REBUILDABLE_IMAGE_CACHE, REBUILDABLE_SESSION_STATE, REBUILDABLE_HISTORY_PAGE }

    public static final class Advice {
        private final boolean pressure;private final Candidate candidate;private final boolean allowNewBackgroundAllocations;
        private Advice(boolean pressure,Candidate candidate,boolean allow){this.pressure=pressure;this.candidate=candidate;allowNewBackgroundAllocations=allow;}
        public boolean underPressure(){return pressure;}
        public Candidate reclaimCandidate(){return candidate;}
        public boolean allowNewBackgroundAllocations(){return allowNewBackgroundAllocations;}
        /** Always true: a trim advice never grants permission to stop owned foreground/media work. */
        public boolean preserveOwnedForegroundAndMediaWork(){return true;}
    }

    /**
     * @param runningModerate/runningLow/runningCritical/background/UIHidden real platform trim facts
     * @param mediaActivelyOwnedByOwnService native fact, not inferred from UI visibility
     * @param rebuildableCandidateExplicitlyReleasedByOwner true only after the owner actually freed it
     */
    public static Advice advise(boolean lowMemory,boolean runningModerate,boolean runningLow,boolean runningCritical,
                                boolean background,boolean uiHidden,boolean mediaActivelyOwnedByOwnService,
                                boolean rebuildableCandidateExplicitlyReleasedByOwner){
        boolean pressure=lowMemory || runningModerate || runningLow || runningCritical || background;
        Candidate candidate=Candidate.NONE;
        if(pressure && !mediaActivelyOwnedByOwnService && runningCritical) candidate=Candidate.REBUILDABLE_IMAGE_CACHE;
        else if(pressure && !mediaActivelyOwnedByOwnService) candidate=Candidate.REBUILDABLE_SESSION_STATE;
        // UI_HIDDEN alone is reported for transparency but never turns into pressure by itself.
        return new Advice(pressure,candidate,!pressure);
    }

    /** Decision for a specific cache class; unknown/quarantined content is always preserved. */
    public static boolean mayRelease(Candidate candidate,boolean containsUniqueOrUnfinishedContent,boolean chainStillRunning){
        if(candidate==null)throw new IllegalArgumentException("Missing cache candidate");
        if(containsUniqueOrUnfinishedContent || chainStillRunning)return false;
        return candidate!=Candidate.NONE;
    }
    private MemoryPressurePolicy(){}
}
