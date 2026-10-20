package io.github.rebrowser.music;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.os.Build;

/**
 * Builds a media notification VIEW only. It creates no channel, posts nothing, starts no
 * foreground service and fabricates no PendingIntent: actions must be exact intentionally-supplied
 * intents, and missing ones are omitted. Seconds from the official player are converted to
 * milliseconds with bounded long arithmetic; unknown duration stays unknown.
 */
public final class MusicNotification {
    public static final int MAX_TEXT_CHARS = 128;
    public static final long MAX_POSITION_SECONDS = 24L * 60 * 60;
    public static final String DEFAULT_CHANNEL_ID = "rebrowser.music.playback";
    public enum ActionKind { PLAY, PAUSE, NEXT, PREVIOUS }

    /** Exact supplied action; this class never constructs its intent or PendingIntent. */
    public static final class Action {
        private final ActionKind kind;private final PendingIntent intent;
        public Action(ActionKind kind,PendingIntent exactNativeIntent){
            if(kind==null || exactNativeIntent==null)throw new IllegalArgumentException("Action requires an exact native intent");
            this.kind=kind;this.intent=exactNativeIntent;
        }
        public ActionKind kind(){return kind;}
        public PendingIntent intent(){return intent;}
    }
    public static final class Projection {
        private final String title,artist;private final long positionSeconds,durationSeconds;
        private final boolean playing;private final boolean unknownDuration;
        public Projection(String title,String artist,boolean playing,long positionSeconds,long durationSeconds){
            this.title=text(title,MAX_TEXT_CHARS);this.artist=text(artist,MAX_TEXT_CHARS);this.playing=playing;
            if(positionSeconds<0 || positionSeconds>MAX_POSITION_SECONDS)throw new IllegalArgumentException("Invalid position");
            if(durationSeconds< -1 || durationSeconds>MAX_POSITION_SECONDS)throw new IllegalArgumentException("Invalid duration");
            if(durationSeconds>=0 && positionSeconds>durationSeconds)throw new IllegalArgumentException("Position exceeds duration");
            this.positionSeconds=positionSeconds;this.durationSeconds=durationSeconds;this.unknownDuration=durationSeconds<0;
        }
        public String title(){return title;}
        public String artist(){return artist;}
        public boolean playing(){return playing;}
        public long positionSeconds(){return positionSeconds;}
        public long durationSeconds(){return durationSeconds;}
        public boolean unknownDuration(){return unknownDuration;}
        /** Seconds to milliseconds; -1 remains "unknown" instead of a fake zero. */
        public long positionMillis(){return positionSeconds*1000L;}
        public long durationMillis(){return unknownDuration?-1:durationSeconds*1000L;}
        public long shownProgressMillis(){return positionMillis();}
    }

    private MusicNotification(){}

    /**
     * Construct (not post) a notification. API26+ requires a channel that the OWNER must have
     * created; an absent channel is reported through the platform at post time, not faked here.
     */
    public static Notification build(Context context,String channelId,int smallIconResourceId,Projection projection,Action[] suppliedActions){
        if(context==null || projection==null)throw new IllegalArgumentException("Missing notification context/projection");
        if(smallIconResourceId==0)throw new IllegalArgumentException("A supplied small icon is required");
        String channel=channelId==null || channelId.isEmpty()?DEFAULT_CHANNEL_ID:channelId;
        Notification.Builder builder=Build.VERSION.SDK_INT>=26?new Notification.Builder(context,channel):new Notification.Builder(context);
        builder.setSmallIcon(smallIconResourceId)
                .setContentTitle(projection.title().isEmpty()?"音乐":projection.title())
                .setContentText(projection.artist())
                .setOnlyAlertOnce(true)
                .setOngoing(projection.playing())
                .setShowWhen(false);
        int count=suppliedActions==null?0:suppliedActions.length;
        if(count>ActionKind.values().length)throw new IllegalArgumentException("Too many native actions");
        java.util.Set<ActionKind> seen=java.util.EnumSet.noneOf(ActionKind.class);
        for(int i=0;i<count;i++){
            Action action=suppliedActions[i];
            if(action==null || !seen.add(action.kind()))throw new IllegalArgumentException("Null or duplicate notification action");
            builder.addAction(new Notification.Action.Builder(0,label(action.kind()),action.intent()).build());
        }
        // Progress is deliberately omitted: confirmed playback end is not established here.
        return builder.build();
    }

    public static String label(ActionKind kind){
        if(kind==null)throw new IllegalArgumentException("Missing action kind");
        switch(kind){case PLAY:return "播放";case PAUSE:return "暂停";case NEXT:return "下一首";case PREVIOUS:return "上一首";default:throw new IllegalArgumentException("Unsupported action");}
    }

    private static String text(String value,int max){
        if(value==null)return "";
        if(value.length()>max)throw new IllegalArgumentException("Unbounded notification text");
        for(int i=0;i<value.length();i++){char c=value.charAt(i);if(Character.isISOControl(c))throw new IllegalArgumentException("Control in notification text");if(Character.isHighSurrogate(c)){if(++i>=value.length() || !Character.isLowSurrogate(value.charAt(i)))throw new IllegalArgumentException("Broken UTF16");}else if(Character.isLowSurrogate(c))throw new IllegalArgumentException("Broken UTF16");}
        return value;
    }
}
