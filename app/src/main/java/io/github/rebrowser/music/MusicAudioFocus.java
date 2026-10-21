package io.github.rebrowser.music;

import android.app.Application;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import io.github.rebrowser.bootstrap.ComponentGate;

/** Actual native audio-focus ownership, not playback/login/rights authorization. Focus GAIN
 * never starts/resumes media. Pause/abandon spend before framework call; unknown retains ownership.
 * No AudioManager request occurs at construction; actual focus/device qualification unperformed. */
public final class MusicAudioFocus {
    public enum State { REQUESTING, GAIN_REPORTED, LOST, ABANDONED, OUTCOME_UNKNOWN }
    public interface Listener {void observed(State state);}
    public static final class Lease {
        private final MusicAudioFocus issuer;private AudioFocusRequest request;private State state=State.REQUESTING;private boolean requestReturned,abandonAttempted;private Integer lastNativeChange;
        private Lease(MusicAudioFocus issuer){this.issuer=issuer;}
        public State reportedState(){issuer.main();return state;}
    }
    private final Application application;private final ComponentGate.DataEpochPin source;private Lease active;
    public MusicAudioFocus(Application nativeOwnApplication,ComponentGate.DataEpochPin nativeSource){if(nativeOwnApplication==null || nativeSource==null)throw new IllegalArgumentException("Native audio-focus owner required");application=nativeOwnApplication;source=nativeSource;main();}
    public Lease requestFromAuthorizedNativeOwner(Listener listener){
        main();source.requireCurrent();if(active!=null || listener==null)throw new IllegalStateException("Native audio focus still owned");AudioManager manager=application.getSystemService(AudioManager.class);if(manager==null)throw new IllegalStateException("Native audio manager unavailable");final Lease lease=new Lease(this);active=lease;
        try{lease.request=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()).setAcceptsDelayedFocusGain(false).setWillPauseWhenDucked(true).setOnAudioFocusChangeListener(change->{
                main();if(active!=lease || lease.abandonAttempted)return;lease.lastNativeChange=change;
                if(change==AudioManager.AUDIOFOCUS_GAIN && lease.requestReturned)lease.state=State.GAIN_REPORTED;else if(change!=AudioManager.AUDIOFOCUS_GAIN)lease.state=State.LOST;
                try{listener.observed(lease.state);}catch(RuntimeException ignored){/* no private native errors */}
            },new Handler(Looper.getMainLooper())).build();
            int result=manager.requestAudioFocus(lease.request);lease.requestReturned=true;source.requireCurrent();lease.state=result==AudioManager.AUDIOFOCUS_REQUEST_GRANTED && (lease.lastNativeChange==null || lease.lastNativeChange==AudioManager.AUDIOFOCUS_GAIN)?State.GAIN_REPORTED:State.LOST;return lease;
        }catch(RuntimeException unknown){lease.state=State.OUTCOME_UNKNOWN;throw unknown;} // No second request or fake release.
    }
    public void requireCurrentOwnedGain(Lease lease){main();source.requireCurrent();own(lease);if(!lease.requestReturned || lease.abandonAttempted || lease.state!=State.GAIN_REPORTED)throw new SecurityException("Native audio focus not currently owned");}
    /** Does NOT prove player/network/native work has ended, even if framework abandonment succeeds. */
    public void abandon(Lease lease){main();own(lease);if(lease.abandonAttempted)throw new IllegalStateException("Native focus abandon already attempted");lease.abandonAttempted=true;lease.state=State.LOST;
        try{AudioManager manager=application.getSystemService(AudioManager.class);if(manager==null || lease.request==null || manager.abandonAudioFocusRequest(lease.request)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED){lease.state=State.OUTCOME_UNKNOWN;return;}lease.state=State.ABANDONED;active=null;}
        catch(RuntimeException unknown){lease.state=State.OUTCOME_UNKNOWN;throw unknown;}
    }
    /** Failed/unknown request may not have returned a lease to its native owner. */
    public void abandonOwnedAttempt(){main();if(active!=null)abandon(active);}
    public boolean hasOwnedFocusAttempt(){main();return active!=null;}
    private void own(Lease lease){if(lease==null || lease.issuer!=this || active!=lease)throw new SecurityException("Foreign audio-focus lease");}
    private void main(){if(Looper.myLooper()!=Looper.getMainLooper())throw new IllegalStateException("Native audio-focus thread required");}
}
