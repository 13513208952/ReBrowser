package io.github.rebrowser.music;

import android.media.MediaMetadata;
import android.media.session.PlaybackState;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Pure projection of OFFICIAL player facts onto Android media-session display values. Explicitly
 * OFFERED actions, REPORTED position/duration/speed and the separate display ESTIMATE stay
 * distinct; unknown duration removes SEEK, UNKNOWN playback emits no PLAY/PAUSE, and an explicit
 * empty projection lets the native owner clear stale metadata/actions. No MediaSession is created,
 * no callback is installed and no audio focus is requested.
 */
public final class MusicMediaSession {
    public enum Transport { NONE, STOPPED, PAUSED, PLAYING, BUFFERING, ERROR }
    public enum Action { PLAY, PAUSE, SEEK, SKIP_NEXT, SKIP_PREVIOUS, STOP }

    public static final class Projection {
        private final Transport transport;private final String title,artist,album;
        private final long reportedPositionMs,reportedDurationMs;private final float reportedSpeed;private final boolean speedKnown;
        private final Long estimatedPositionMs;private final Set<Action> offered;
        private Projection(Transport transport,String title,String artist,String album,
                           long positionMs,long durationMs,float speed,boolean speedKnown,Long estimatedPositionMs,Set<Action> offered){
            this.transport=transport;this.title=title;this.artist=artist;this.album=album;
            this.reportedPositionMs=positionMs;this.reportedDurationMs=durationMs;this.reportedSpeed=speed;this.speedKnown=speedKnown;
            this.estimatedPositionMs=estimatedPositionMs;this.offered=Collections.unmodifiableSet(offered);
        }
        public Transport transport(){return transport;}
        public String title(){return title;}
        public String artist(){return artist;}
        public String album(){return album;}
        /** The actual reported position; never replaced by the estimate. */
        public long reportedPositionMs(){return reportedPositionMs;}
        public long positionMs(){return reportedPositionMs;}
        public long durationMs(){return reportedDurationMs;}
        /** Display fallback 0 when no speed observation was supplied, NOT a reported zero rate. */
        public float speed(){return reportedSpeed;}
        /** -1 = unknown; never replaced with a fabricated duration. */
        public long reportedDurationMs(){return reportedDurationMs;}
        public float reportedSpeed(){return reportedSpeed;}
        public boolean reportedSpeedKnown(){return speedKnown;}
        /** Display-only estimate, or null when not usable. */
        public Long estimatedPositionMs(){return estimatedPositionMs;}
        public Set<Action> offeredActions(){return offered;}
        public Set<Action> actions(){return offered;}
        public boolean seekable(){return offered.contains(Action.SEEK) && reportedDurationMs>0;}
        public static Projection empty(){
            return new Projection(Transport.NONE,"","","",-1,-1,0f,false,null,EnumSet.noneOf(Action.class));
        }
    }

    /**
     * Project explicit owner offers plus reported facts and an optional display estimate. Missing
     * facts never create an action: the caller must supply every offered action.
     */
    public static Projection project(MusicState observed,Set<Action> offeredActions,MusicState.Estimate estimate){return project(observed,offeredActions,estimate,null);}
    public static Projection project(MusicState observed,Set<Action> offeredActions,MusicState.Estimate estimate,Float nativeReportedRate){
        if(observed==null)throw new IllegalArgumentException("Missing music observation");
        if(offeredActions==null)throw new IllegalArgumentException("Missing offered actions; none are inferred");
        Transport transport;
        switch(observed.playback()){
            case PLAYING:transport=Transport.PLAYING;break;
            case PAUSED:transport=Transport.PAUSED;break;
            case BUFFERING:transport=Transport.BUFFERING;break;
            case IDLE:case ENDED:transport=Transport.STOPPED;break;
            default:transport=Transport.NONE;break;
        }
        EnumSet<Action> actions=EnumSet.noneOf(Action.class);
        actions.addAll(offeredActions);
        if(observed.durationMs()<=0)actions.remove(Action.SEEK);
        if(transport==Transport.NONE){actions.remove(Action.PLAY);actions.remove(Action.PAUSE);}
        if(nativeReportedRate!=null && (!Float.isFinite(nativeReportedRate) || nativeReportedRate<0 || nativeReportedRate>16))throw new IllegalArgumentException("Invalid reported playback rate");
        float speed=nativeReportedRate==null?0f:nativeReportedRate; // unknown is a display fallback, NOT 1x
        Long displayEstimate=estimate!=null && estimate.belongsTo(observed) && estimate.usable()?estimate.positionMs():null;
        return new Projection(transport,observed.title(),observed.artist(),"",
                observed.positionMs(),observed.hasReportedDuration()?observed.durationMs():-1,
                speed,nativeReportedRate!=null,displayEstimate,actions);
    }

    public static Projection project(MusicState observed,Set<Action> offered){return project(observed,offered,null);}
    /** Legacy API: only two explicitly supplied SKIP offers are safe to project.
     * A reported PLAYING/PAUSED label is NOT an action offer, and duration is NOT seek permission. */
    public static Projection project(MusicState observed,boolean ownerOffersNext,boolean ownerOffersPrevious){
        if(observed==null)throw new IllegalArgumentException("Missing music observation");
        EnumSet<Action> offered=EnumSet.noneOf(Action.class);
        if(ownerOffersNext)offered.add(Action.SKIP_NEXT);
        if(ownerOffersPrevious)offered.add(Action.SKIP_PREVIOUS);
        return project(observed,offered,null);
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
        for(Action action:projection.offeredActions()){
            switch(action){
                case PLAY:actions|=PlaybackState.ACTION_PLAY;break;
                case PAUSE:actions|=PlaybackState.ACTION_PAUSE;break;
                case SEEK:actions|=PlaybackState.ACTION_SEEK_TO;break;
                case SKIP_NEXT:actions|=PlaybackState.ACTION_SKIP_TO_NEXT;break;
                case SKIP_PREVIOUS:actions|=PlaybackState.ACTION_SKIP_TO_PREVIOUS;break;
                case STOP:actions|=PlaybackState.ACTION_STOP;break;
            }
        }
        return new PlaybackState.Builder().setState(state,projection.reportedPositionMs(),projection.reportedSpeed()).setActions(actions).build();
    }

    public static MediaMetadata metadata(Projection projection){
        if(projection==null)throw new IllegalArgumentException("Missing projection");
        MediaMetadata.Builder builder=new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE,projection.title().isEmpty()?null:projection.title())
                .putString(MediaMetadata.METADATA_KEY_ARTIST,projection.artist().isEmpty()?null:projection.artist());
        if(projection.reportedDurationMs()>0)builder.putLong(MediaMetadata.METADATA_KEY_DURATION,projection.reportedDurationMs());
        return builder.build();
    }

    private MusicMediaSession(){}
}
