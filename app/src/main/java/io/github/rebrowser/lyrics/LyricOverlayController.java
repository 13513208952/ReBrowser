package io.github.rebrowser.lyrics;

/**
 * Owner-thread reducer for the lyric overlay MODE, drag gestures and window-application
 * observations. It never touches WindowManager/Settings/permission prompts and never claims that
 * a system overlay, removal or touch passthrough actually took effect: only the real window owner
 * may confirm application/removal for the CURRENT requested mode+generation. A failed/unknown
 * application never becomes "removed".
 */
public final class LyricOverlayController {
    public enum Mode { CLOSED, VISIBLE_UNLOCKED, VISIBLE_LOCKED }
    public enum Event { SHOW, DRAG_START, DRAG_MOVE, DRAG_END, DRAG_CANCEL, LOCK, UNLOCK, HIDE, CLOSE, PERMISSION_REVOKED, WINDOW_DETACHED }
    public static final int MAX_COORD = 32_768;
    public static final int MAX_GESTURES = 64;

    public static final class State {
        private final long generation;private final Mode mode;private final boolean dragActive;private final int x,y;
        private State(long generation,Mode mode,boolean dragActive,int x,int y){this.generation=generation;this.mode=mode;this.dragActive=dragActive;this.x=x;this.y=y;}
        public long generation(){return generation;}
        public Mode mode(){return mode;}
        public boolean dragActive(){return dragActive;}
        public int x(){return x;}
        public int y(){return y;}
    }
    /** Opaque per-gesture token; a token from an older generation cannot move the new window. */
    public static final class Gesture {
        private final long generation;private boolean ended;
        private Gesture(long generation){this.generation=generation;}
        public long generation(){return generation;}
        public boolean ended(){return ended;}
    }

    private final Thread owner=Thread.currentThread();
    private long generation=1;
    private Mode mode=Mode.CLOSED;private Mode confirmed=Mode.CLOSED;private long confirmedGeneration;
    private boolean dragActive,retired;private int x,y;
    private Gesture gesture;
    // Only the current gesture is owned; ended tokens do not accumulate or exhaust lifetime use.

    /** User/native intent only. Returns the new immutable state or rejects illegal transitions. */
    public State apply(Event event,int nextX,int nextY){
        check();if(event==null)throw new IllegalArgumentException("Missing overlay event");
        if(retired)throw new IllegalStateException("Retired lyric overlay controller");
        requireCoords(nextX,nextY);
        switch(event){
            case SHOW:
                if(mode==Mode.CLOSED)transition(Mode.VISIBLE_UNLOCKED,false,nextX,nextY);
                break;
            case DRAG_START:
                requireVisible();beginGesture(nextX,nextY);break;
            case DRAG_MOVE:
                if(!dragActive)throw new IllegalStateException("Drag move without drag start");
                transition(mode,true,nextX,nextY);break;
            case DRAG_END:
                if(!dragActive || gesture==null)throw new IllegalStateException("Drag end without drag start");
                endGesture(gesture,nextX,nextY,false);break;
            case DRAG_CANCEL:
                if(gesture!=null)cancelGesture(gesture);break;
            case LOCK:
                requireVisible();transition(Mode.VISIBLE_LOCKED,false,nextX,nextY);break;
            case UNLOCK:
                requireVisible();transition(Mode.VISIBLE_UNLOCKED,false,nextX,nextY);break;
            case HIDE:
                transition(Mode.CLOSED,false,x,y);break;
            case CLOSE:
            case PERMISSION_REVOKED:
            case WINDOW_DETACHED:
                transition(Mode.CLOSED,false,x,y);confirmed=Mode.CLOSED;confirmedGeneration=0;break;
            default:throw new IllegalArgumentException("Unsupported overlay event");
        }
        return state();
    }
    public State state(){check();return new State(generation,mode,dragActive,x,y);}
    public Gesture beginGesture(int nextX,int nextY){
        check();requireVisible();if(retired || mode!=Mode.VISIBLE_UNLOCKED)throw new IllegalStateException("Unlocked current overlay required");requireCoords(nextX,nextY);
        if(gesture!=null && !gesture.ended)throw new IllegalStateException("Gesture already active");
        if(generation==Long.MAX_VALUE)throw new IllegalStateException("Overlay generation exhausted");
        generation++;
        if(confirmedGeneration!=generation)confirmed=Mode.CLOSED;
        Gesture created=new Gesture(generation);
        gesture=created;dragActive=true;x=nextX;y=nextY;
        return created;
    }
    /** Moves only the CURRENT gesture token at the CURRENT generation. */
    public State moveGesture(Gesture token,int nextX,int nextY){
        check();requireCoords(nextX,nextY);
        if(!currentGesture(token))throw new IllegalStateException("Stale overlay gesture");
        transition(mode,true,nextX,nextY);return state();
    }
    public State endGesture(Gesture token,int nextX,int nextY,boolean cancelled){
        check();requireCoords(nextX,nextY);
        if(!currentGesture(token))throw new IllegalStateException("Stale overlay gesture");
        if(cancelled)return cancelGesture(token);
        transition(mode,false,nextX,nextY);
        token.ended=true;gesture=null;dragActive=false;
        return state();
    }
    public State cancelGesture(Gesture token){
        check();
        if(!currentGesture(token))return state();
        transition(mode,false,x,y);
        token.ended=true;gesture=null;dragActive=false;
        return state();
    }

    /** ONLY the actual native window owner, after it applied the CURRENT requested mode. */
    void confirmWindowApplied(Mode applied,long appliedGeneration){
        check();if(retired)return;
        if(applied==null)throw new IllegalArgumentException("Missing applied mode");
        if(appliedGeneration!=generation || mode==Mode.CLOSED && applied!=Mode.CLOSED)return;
        if(applied!=mode)return; // a stale/other mode cannot confirm the current request
        confirmed=applied;confirmedGeneration=appliedGeneration;
    }
    /** Application reported a failure for a generation: no removal/passthrough is inferred. */
    void confirmWindowApplyFailed(long failedGeneration){
        check();if(retired || failedGeneration!=generation)return;
        confirmed=Mode.CLOSED;confirmedGeneration=failedGeneration;
    }
    /** ONLY the native owner, after it actually observed window removal for this generation. */
    void confirmWindowRemoved(long removedGeneration){
        check();if(retired || removedGeneration!=generation)return;
        confirmed=Mode.CLOSED;confirmedGeneration=removedGeneration;
    }
    /** True only when the real owner confirmed LOCKED for the CURRENT generation. */
    public boolean passthroughEffective(){
        check();
        return mode==Mode.VISIBLE_LOCKED && confirmed==Mode.VISIBLE_LOCKED && confirmedGeneration==generation;
    }
    public boolean visible(){check();return mode!=Mode.CLOSED;}
    public void retire(){check();retired=true;mode=Mode.CLOSED;confirmed=Mode.CLOSED;dragActive=false;confirmedGeneration=0;if(gesture!=null){gesture.ended=true;gesture=null;}}

    private boolean currentGesture(Gesture token){return !retired && token!=null && token==gesture && !token.ended && token.generation==generation && dragActive;}
    private void requireVisible(){if(mode==Mode.CLOSED)throw new IllegalStateException("Lyric overlay is closed");}
    private void transition(Mode nextMode,boolean nextDrag,int nextX,int nextY){
        boolean modeChange=nextMode!=mode;
        if(modeChange){
            if(generation==Long.MAX_VALUE)throw new IllegalStateException("Overlay generation exhausted");
            generation++;
            // A mode change invalidates any gesture bound to the previous window request.
            if(gesture!=null){gesture.ended=true;gesture=null;nextDrag=false;}
            if(confirmedGeneration!=generation)confirmed=Mode.CLOSED;
        }
        mode=nextMode;dragActive=nextDrag;x=nextX;y=nextY;
    }
    private static void requireCoords(int x,int y){if(x< -MAX_COORD || x>MAX_COORD || y< -MAX_COORD || y>MAX_COORD)throw new IllegalArgumentException("Overlay coordinate out of bounds");}
    private void check(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Lyric overlay owner thread required");}
}
