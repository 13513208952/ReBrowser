package io.github.rebrowser.music;

import android.media.MediaMetadata;
import android.media.session.PlaybackState;
import java.util.EnumSet;
import java.util.Set;

/**
 * Pure projection of the OFFICIAL player observation onto Android media-session display values,
 * plus construction of PlaybackState/MediaMetadata. It creates no MediaSession, installs no
 * callback, acquires no audio focus and performs no transport action: available actions describe
 * what the OWNER may offer, never that playback is playing or authorized.
 */
public final class MusicMediaSession {
    public enum Transport { NONE, STOPPED, PAUSED, PLAYING, BUFFERING, ERROR }
    public enum Action { PLAY, PAUSE, SEEK, SKIP_NEXT, SKIP_PREVIOUS, STOP }

    public static final class Projection {
        private final Transport transport;private final String title,artist,album;
        private final long positionMs,durationMs;private final boolean seekable;private final Set<Action> actions;
        private Projection(Transport transport,String title,String artist,String album,long positionMs,long durationMs,boolean seekable,Set<Action> actions){
            this.transport=transport;this.title=title;this.artist=artist;this.album=album;
            this.positionMs=positionMs;this.durationMs=durationMs;this.seekable=seekable;this.actions=java.util.Collections.unmodifiableSet(actions);
        }
        public Transport transport(){return transport;}
        public String title(){return title;}
        public String artist(){return artist;}
        public String album(){return album;}
        public long positionMs(){return positionMs;}
        /** -1 = unknown; never replaced with a fabricated duration. */
        public long durationMs(){return durationMs;}
        public boolean seekable(){return seekable;}
        public Set<Action> actions(){return actions;}
        public float speed(){switch(transport){case PLAYING:return 1f;case PAUSED:return 0f;default:return 0f;}}
    }

    /** Map the read-only observation; UNKNOWN yields no transport action at all. */
    public static Projection project(MusicState observed,boolean ownerOffersNext,boolean ownerOffersPrevious){
        Set<Action> offered=EnumSet.noneOf(Action.class);if(ownerOffersNext)offered.add(Action.SKIP_NEXT);if(ownerOffersPrevious)offered.add(Action.SKIP_PREVIOUS);return project(observed,offered);
    }
    /** Caller-supplied native supported-action observation, never inferred from playing/duration. */
    public static Projection project(MusicState observed,Set<Action> nativeOfferedActions){
        if(nativeOfferedActions==null || nativeOfferedActions.size()>Action.values().length)throw new IllegalArgumentException("Invalid native action observation");
        for(Action action:nativeOfferedActions)if(action==null)throw new IllegalArgumentException("Null action observation");
        if(observed==null)throw new IllegalArgumentException("Missing music observation");
        Transport transport;
        switch(observed.playback()){
            case PLAYING:transport=Transport.PLAYING;break;
            case PAUSED:transport=Transport.PAUSED;break;
            case BUFFERING:transport=Transport.BUFFERING;break;
            case IDLE:case ENDED:transport=Transport.STOPPED;break;
            default:transport=Transport.NONE;break;
        }
        Set<Action> actions=EnumSet.noneOf(Action.class);
        if(transport!=Transport.NONE){
            actions.addAll(nativeOfferedActions);
            if(!observed.hasReportedDuration() || observed.durationMs()<=0)actions.remove(Action.SEEK);
        }
        return new Projection(transport,observed.title(),observed.artist(),"",
                observed.positionMs(),observed.hasReportedDuration()?observed.durationMs():-1,
                actions.contains(Action.SEEK),actions);
    }

    /** Build DISPLAY state only; no session is created and no callback is installed. */
    public static PlaybackState playbackState(Projection projection){
        if(projection==null)throw new IllegalArgumentException("Missing projection");
        int state;
        switch(projection.transport()){
            case PLAYING:state=PlaybackState.STATE_PLAYING;break;
            case PAUSED:state=PlaybackState.STATE_PAUSED;break;
            case BUFFERING:state=PlaybackState.STATE_BUFFERING;break;
            case STOPPED:state=PlaybackState.STATE_STOPPED;break;
            case ERROR:state=PlaybackState.STATE_ERROR;break;
            default:state=PlaybackState.STATE_NONE;break;
        }
        long actions=0;
        for(Action action:projection.actions()){
            switch(action){
                case PLAY:actions|=PlaybackState.ACTION_PLAY;break;
                case PAUSE:actions|=PlaybackState.ACTION_PAUSE;break;
                case SEEK:actions|=PlaybackState.ACTION_SEEK_TO;break;
                case SKIP_NEXT:actions|=PlaybackState.ACTION_SKIP_TO_NEXT;break;
                case SKIP_PREVIOUS:actions|=PlaybackState.ACTION_SKIP_TO_PREVIOUS;break;
                case STOP:actions|=PlaybackState.ACTION_STOP;break;
            }
        }
        return new PlaybackState.Builder().setState(state,projection.positionMs(),projection.speed()).setActions(actions).build();
    }

    public static MediaMetadata metadata(Projection projection){
        if(projection==null)throw new IllegalArgumentException("Missing projection");
        MediaMetadata.Builder builder=new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE,projection.title().isEmpty()?null:projection.title())
                .putString(MediaMetadata.METADATA_KEY_ARTIST,projection.artist().isEmpty()?null:projection.artist());
        if(projection.durationMs()>=0)builder.putLong(MediaMetadata.METADATA_KEY_DURATION,projection.durationMs());
        return builder.build();
    }

    private MusicMediaSession(){}
}
