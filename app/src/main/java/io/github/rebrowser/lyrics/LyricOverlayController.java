package io.github.rebrowser.lyrics;

/**
 * Owner-thread reducer for the lyric overlay MODE and user intents. It never touches
 * WindowManager/Settings/permission prompts and never claims that a system overlay or touch
 * passthrough actually took effect. Only the real window owner may confirm application; until
 * then {@link #passthroughEffective()} stays false.
 */
public final class LyricOverlayController {
    public enum Mode { CLOSED, VISIBLE_UNLOCKED, VISIBLE_LOCKED }
    public enum Event { SHOW, DRAG_START, DRAG_MOVE, DRAG_END, LOCK, UNLOCK, CLOSE, PERMISSION_REVOKED, WINDOW_DETACHED }
    public static final int MAX_COORD = 32_768;

    public static final class State {
        private final long generation;private final Mode mode;private final boolean dragActive;private final int x,y;
        private State(long generation,Mode mode,boolean dragActive,int x,int y){this.generation=generation;this.mode=mode;this.dragActive=dragActive;this.x=x;this.y=y;}
        public long generation(){return generation;}
        public Mode mode(){return mode;}
        public boolean dragActive(){return dragActive;}
        public int x(){return x;}
        public int y(){return y;}
    }

    private final Thread owner=Thread.currentThread();
    private long generation=1;
    private Mode mode=Mode.CLOSED;private Mode confirmed=Mode.CLOSED;
    private boolean dragActive,retired;private int x,y;
    private long windowGeneration;

    /** User/native intent only. Returns the new immutable state or throws for illegal transitions. */
    public State apply(Event event,int nextX,int nextY){
        check();if(event==null)throw new IllegalArgumentException("Missing overlay event");
        if(retired)throw new IllegalStateException("Retired lyric overlay controller");
        if(generation==Long.MAX_VALUE){retire();throw new IllegalStateException("Overlay generation exhausted");}
        if(nextX< -MAX_COORD || nextX>MAX_COORD || nextY< -MAX_COORD || nextY>MAX_COORD)throw new IllegalArgumentException("Overlay coordinate out of bounds");
        switch(event){
            case SHOW:
                if(mode==Mode.CLOSED){mode=Mode.VISIBLE_UNLOCKED;advance();}
                break;
            case DRAG_START:
                requireVisible();if(mode!=Mode.VISIBLE_UNLOCKED)throw new IllegalStateException("Locked overlay cannot drag");dragActive=true;advance();break;
            case DRAG_MOVE:
                if(!dragActive)throw new IllegalStateException("Drag move without drag start");
                x=nextX;y=nextY;advance();break;
            case DRAG_END:
                if(!dragActive)throw new IllegalStateException("Drag end without drag start");
                dragActive=false;x=nextX;y=nextY;advance();break;
            case LOCK:
                requireVisible();mode=Mode.VISIBLE_LOCKED;dragActive=false;advance();break;
            case UNLOCK:
                requireVisible();mode=Mode.VISIBLE_UNLOCKED;advance();break;
            case CLOSE:
            case PERMISSION_REVOKED:
            case WINDOW_DETACHED:
                mode=Mode.CLOSED;dragActive=false;confirmed=Mode.CLOSED;windowGeneration=0;advance();break;
            default:throw new IllegalArgumentException("Unsupported overlay event");
        }
        return state();
    }
    public State state(){check();return new State(generation,mode,dragActive,x,y);}

    /** Called ONLY by the actual native window owner after addView/updateViewLayout succeeded. */
    void confirmWindowApplied(Mode applied,long appliedGeneration){
        check();if(retired)return;
        if(applied==null)throw new IllegalArgumentException("Missing applied mode");
        if(mode==Mode.CLOSED && applied!=Mode.CLOSED)return; // late confirmation after close is ignored
        if(appliedGeneration!=generation || applied!=mode)return;
        confirmed=applied;windowGeneration=appliedGeneration;
    }
    /** True only when the real window owner confirmed the locked mode for the CURRENT generation. */
    public boolean passthroughEffective(){check();return mode==Mode.VISIBLE_LOCKED && confirmed==Mode.VISIBLE_LOCKED && windowGeneration==generation;}
    public boolean visible(){check();return mode!=Mode.CLOSED;}
    public void retire(){check();retired=true;mode=Mode.CLOSED;confirmed=Mode.CLOSED;dragActive=false;windowGeneration=0;}

    private void requireVisible(){if(mode==Mode.CLOSED)throw new IllegalStateException("Lyric overlay is closed");}
    private void advance(){if(generation==Long.MAX_VALUE)throw new IllegalStateException("Overlay generation exhausted");generation++;}
    private void check(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Lyric overlay owner thread required");}
}
