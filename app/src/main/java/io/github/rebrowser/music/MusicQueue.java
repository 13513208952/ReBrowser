package io.github.rebrowser.music;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Bounded immutable view of the OFFICIAL web player queue / like state. It never extracts a
 * stream URL, never changes the official queue and never claims that a like or account binding
 * succeeded. Resource tokens are opaque handoff values, not account or action authority.
 */
public final class MusicQueue {
    public static final int MAX_ENTRIES = 500;
    public static final int MAX_TOKEN_CHARS = 64;
    public static final int MAX_TITLE_CHARS = 512;
    public enum Like { UNKNOWN, REPORTED_LIKED, REPORTED_NOT_LIKED }
    public enum Action { PLAY_ENTRY, REMOVE_FROM_QUEUE, TOGGLE_LIKE }

    public static final class Track {
        private final String token,title,artist,album;private final long durationMs;private final Like liked;
        public Track(String token,String title,String artist,String album,long durationMs,Like liked){
            this.token=opaqueToken(token);this.title=text(title,MAX_TITLE_CHARS);this.artist=text(artist,MAX_TITLE_CHARS);
            this.album=text(album,MAX_TITLE_CHARS);
            if(durationMs< -1 || durationMs>24L*60*60*1000)throw new IllegalArgumentException("Invalid reported duration");
            if(liked==null)throw new IllegalArgumentException("Unknown like state must be explicit");
            this.durationMs=durationMs;this.liked=liked;
        }
        public String token(){return token;}
        public String title(){return title;}
        public String artist(){return artist;}
        public String album(){return album;}
        public long durationMs(){return durationMs;}
        public Like liked(){return liked;}
    }
    public static final class Intent {
        private final Action action;private final String token;private final long expectedRevision;
        Intent(Action action,String token,long expectedRevision){this.action=action;this.token=token;this.expectedRevision=expectedRevision;}
        public Action action(){return action;}
        public String token(){return token;}
        public long expectedRevision(){return expectedRevision;}
        /** Requested only; the official player response remains the sole outcome evidence. */
        public boolean isOutcome(){return false;}
    }

    private final List<Track> tracks;private final long revision;private final String sourceLabel;
    public MusicQueue(List<Track> tracks,long revision,String sourceLabel){
        if(tracks==null || tracks.size()>MAX_ENTRIES || revision<1)throw new IllegalArgumentException("Invalid queue page");
        Set<String> tokens=new HashSet<>();
        List<Track> copy=new ArrayList<>();
        for(Track track:tracks){if(track==null)throw new IllegalArgumentException("Missing queue entry");if(!tokens.add(track.token))throw new IllegalArgumentException("Duplicate queue token");copy.add(track);}
        this.tracks=Collections.unmodifiableList(copy);this.revision=revision;this.sourceLabel=text(sourceLabel,64);
    }
    /** Explicitly empty placeholder; shows "not read" rather than a fabricated queue. */
    public static MusicQueue empty(){return new MusicQueue(Collections.<Track>emptyList(),1,"未读取");}
    public List<Track> tracks(){return tracks;}
    public long revision(){return revision;}
    public String sourceLabel(){return sourceLabel;}
    public Intent play(int index){return intent(index,Action.PLAY_ENTRY);}
    public Intent remove(int index){return intent(index,Action.REMOVE_FROM_QUEUE);}
    public Intent toggleLike(int index){return intent(index,Action.TOGGLE_LIKE);}
    private Intent intent(int index,Action action){Track track=require(index);return new Intent(action,track.token,revision);}
    public Track require(int index){if(index<0 || index>=tracks.size())throw new IllegalArgumentException("Queue index out of range");return tracks.get(index);}
    /** Opaque tokens are bounded ASCII identifiers; URLs are rejected rather than mined. */
    static String opaqueToken(String value){
        if(value==null || value.isEmpty() || value.length()>MAX_TOKEN_CHARS)throw new IllegalArgumentException("Invalid queue token");
        for(int i=0;i<value.length();i++){char c=value.charAt(i);boolean ok=(c>='a'&&c<='z')||(c>='A'&&c<='Z')||(c>='0'&&c<='9')||c=='_'||c=='-';if(!ok)throw new IllegalArgumentException("Queue token is not an opaque native id");}
        return value;
    }
    private static String text(String value,int max){if(value==null || value.length()>max)throw new IllegalArgumentException("Unbounded queue text");for(int i=0;i<value.length();i++){char c=value.charAt(i);if(Character.isISOControl(c))throw new IllegalArgumentException("Control in queue text");if(Character.isHighSurrogate(c)){if(++i>=value.length() || !Character.isLowSurrogate(value.charAt(i)))throw new IllegalArgumentException("Broken UTF16");}else if(Character.isLowSurrogate(c))throw new IllegalArgumentException("Broken UTF16");}return value;}
}
