package io.github.rebrowser.music;

import io.github.rebrowser.bootstrap.ComponentGate;
import io.github.rebrowser.ui.BrowserToolbar;
import java.util.function.LongSupplier;

/** Native explicit playback INTENT latch, not account/rights/focus/Actor authorization. Cold
 * start, restore, service rebind, page playing flags and focus GAIN cannot create an intent.
 * A native dispatcher must separately validate official rights/current account/owned focus and
 * durable task admission before any real player command. No actual player is invoked here. */
public final class MusicPlaybackAuthorization {
    public static final class Intent {
        private final MusicPlaybackAuthorization issuer;private final BrowserToolbar.Binding window;private final long generation,issued;private boolean spent;
        private Intent(MusicPlaybackAuthorization issuer,BrowserToolbar.Binding window,long generation,long issued){this.issuer=issuer;this.window=window;this.generation=generation;this.issued=issued;}
        public long nativeIntentGeneration(){return generation;}
    }
    private final ComponentGate.DataEpochPin source;private final LongSupplier clock;private final Thread thread=Thread.currentThread();private long generation=1,last=-1;private Intent pending;private boolean desiredPlaying,closed;
    public MusicPlaybackAuthorization(ComponentGate.DataEpochPin nativeSource,LongSupplier nativeElapsedRealtime){if(nativeSource==null || nativeElapsedRealtime==null)throw new IllegalArgumentException("Native playback intent owner required");source=nativeSource;clock=nativeElapsedRealtime;source.requireCurrent();}
    /** Call ONLY from the actual native UI action adapter, not JS/IPC booleans or restored state. */
    public Intent explicitNativePlayRequest(BrowserToolbar.Binding exactOwnedWindow){current();if(exactOwnedWindow==null)throw denied();exactOwnedWindow.check();advance();desiredPlaying=true;pending=new Intent(this,exactOwnedWindow,generation,now());return pending;}
    /** Pause/stop/focus loss invalidates pending starts even while source is locked. */
    public void invalidateForPauseOrFocusLoss(){owner();advance();desiredPlaying=false;pending=null;}
    public void beginSingleNativeAttempt(Intent exactIntent){current();if(exactIntent==null || exactIntent.issuer!=this || pending!=exactIntent || exactIntent.spent || !desiredPlaying || exactIntent.generation!=generation)throw denied();exactIntent.spent=true;exactIntent.window.check();long time=now();if(time-exactIntent.issued>=30_000)throw denied();source.requireCurrent();} // Spend before any possible native player handoff.
    public boolean hasUnspentExplicitIntent(){current();return desiredPlaying && pending!=null && !pending.spent && now()-pending.issued<30_000;}
    public void close(){owner();closed=true;invalidateForPauseOrFocusLoss();}
    private void advance(){if(generation==Long.MAX_VALUE){closed=true;throw denied();}generation++;}
    private void current(){owner();source.requireCurrent();if(closed)throw denied();now();}
    private long now(){long n;try{n=clock.getAsLong();}catch(RuntimeException unavailable){closed=true;pending=null;throw denied();}if(n<0 || n<last){closed=true;pending=null;throw denied();}last=n;return n;}
    private void owner(){if(Thread.currentThread()!=thread)throw new IllegalStateException("Native music intent owner thread required");}
    private static SecurityException denied(){return new SecurityException("Explicit native playback intent unavailable or spent");}
}
