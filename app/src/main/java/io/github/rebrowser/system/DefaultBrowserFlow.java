package io.github.rebrowser.system;

/**
 * Read-only default-browser ROLE observation and a display-only recommendation request. It does
 * not set the role, synthesize a resolver, send an Intent or open a third-party page; the actual
 * authorized handoff stays with the native owner.
 */
public final class DefaultBrowserFlow {
    public enum Role { UNKNOWN, HELD, NOT_HELD }
    public enum RequestKind { OPEN_SYSTEM_DEFAULT_APPS_SETTINGS }

    public static final class Observation {
        private final Role role;private final long revision;
        public Observation(Role role,long revision){
            if(role==null || revision<1)throw new IllegalArgumentException("Invalid role observation");
            this.role=role;this.revision=revision;
        }
        public Role role(){return role;}
        public long revision(){return revision;}
        public boolean roleHeld(){return role==Role.HELD;}
        public String label(){
            switch(role){case HELD:return "当前是默认浏览器";case NOT_HELD:return "当前不是默认浏览器";default:return "默认浏览器状态未知";}
        }
    }
    public static final class Recommendation {
        private final RequestKind kind;private final long expectedRevision;
        private Recommendation(RequestKind kind,long expectedRevision){this.kind=kind;this.expectedRevision=expectedRevision;}
        public RequestKind kind(){return kind;}
        public long expectedRevision(){return expectedRevision;}
        /** A request description only: no system state changed and no Intent dispatched. */
        public boolean isDispatched(){return false;}
    }
    private final Thread owner=Thread.currentThread();
    private Observation current;private long revision;private boolean retired;
    public Observation observe(Boolean roleHeld){
        check();if(retired)throw new IllegalStateException("Retired default-browser flow");
        Role role=roleHeld==null?Role.UNKNOWN:roleHeld?Role.HELD:Role.NOT_HELD;
        if(revision==Long.MAX_VALUE)throw new IllegalStateException("Role revision exhausted");
        revision++;current=new Observation(role,revision);return current;
    }
    /** Held role may still open the recommendation page; not-held shows the same entry. */
    public Observation current(){check();if(current==null)throw new IllegalStateException("Role not observed");return current;}
    public Recommendation recommendation(RequestKind kind){
        check();Observation observed=current();
        if(kind==null)throw new IllegalArgumentException("Missing recommendation kind");
        return new Recommendation(kind,observed.revision());
    }
    public void retire(){check();retired=true;current=null;}
    private void check(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Default-browser owner thread required");}
}
