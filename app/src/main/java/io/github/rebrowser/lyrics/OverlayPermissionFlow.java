package io.github.rebrowser.lyrics;

/**
 * Read-only overlay-permission OBSERVATION state machine. It never starts an Activity, never
 * requests a permission and never mints owner consent. Returning from system settings always
 * requires a fresh observation before any capability is shown as granted.
 */
public final class OverlayPermissionFlow {
    public enum Observed { UNKNOWN, ALLOWED, DENIED }
    public enum Phase { UNKNOWN, DENIED, ALLOWED, RECHECK_REQUIRED }

    public static final class State {
        private final Phase phase;private final Observed observation;private final long revision;
        private State(Phase phase,Observed observation,long revision){this.phase=phase;this.observation=observation;this.revision=revision;}
        public Phase phase(){return phase;}
        public Observed observation(){return observation;}
        public long revision(){return revision;}
        /** Only a fresh ALLOWED observation may be presented as available. */
        public boolean reportedAllowedForDisplay(){return phase==Phase.ALLOWED && observation==Observed.ALLOWED;}
        public String label(){
            switch(phase){case ALLOWED:return "系统允许显示在其他应用上层";case DENIED:return "系统未允许显示在其他应用上层";case RECHECK_REQUIRED:return "已返回设置，需重新读取系统状态";default:return "系统浮层权限未知";}
        }
    }

    private final Thread owner=Thread.currentThread();
    private long revision;private Phase phase=Phase.UNKNOWN;private Observed observation=Observed.UNKNOWN;private boolean retired;
    /** Fresh own-app observation: a Boolean wrapper because the platform read may be unavailable. */
    public State observe(Boolean canDrawOverlays){
        check();if(retired)throw new IllegalStateException("Retired overlay permission flow");
        observation=canDrawOverlays==null?Observed.UNKNOWN:canDrawOverlays?Observed.ALLOWED:Observed.DENIED;
        phase=observation==Observed.ALLOWED?Phase.ALLOWED:observation==Observed.DENIED?Phase.DENIED:Phase.UNKNOWN;
        advance();return state();
    }
    /** The user left/returned from system settings: prior grant is no longer current. */
    public State returnedFromSettings(){
        check();if(retired)throw new IllegalStateException("Retired overlay permission flow");
        phase=Phase.RECHECK_REQUIRED;advance();return state();
    }
    public State state(){check();return new State(phase,observation,revision);}
    public void retire(){check();retired=true;phase=Phase.UNKNOWN;observation=Observed.UNKNOWN;}
    private void advance(){if(revision==Long.MAX_VALUE)throw new IllegalStateException("Overlay permission revision exhausted");revision++;}
    private void check(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Overlay permission owner thread required");}
}
