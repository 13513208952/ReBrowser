package io.github.rebrowser.music;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Bounded immutable view of the OFFICIAL web player queue / like state. It never extracts a
 * stream URL, never changes the official queue and never claims that a like or account binding
 * succeeded. Resource tokens are opaque handoff values, not account or action authority.
 * Windows/filters are LOCAL display projections: they keep the full-queue revision and the
 * original Track token so no intent is ever addressed by a filtered index.
 */
public final class MusicQueue {
    public static final int MAX_ENTRIES = 500;
    public static final int MAX_WINDOW = 50;
    public static final int MAX_TOKEN_CHARS = 64;
    public static final int MAX_TITLE_CHARS = 512;
    public static final int MAX_FILTER_CHARS = 64;
    public enum Like { UNKNOWN, REPORTED_LIKED, REPORTED_NOT_LIKED }
    public enum Action { PLAY_ENTRY, REMOVE_FROM_QUEUE, TOGGLE_LIKE }
    /** Explicit three-state availability; AVAILABLE is the only state with rows to show. */
    public enum ViewState { UNREAD, EMPTY, AVAILABLE, TRUNCATED }

    public static final class Track {
        private final String token,title,artist,album;private final long durationMs;private final Like liked;private final Set<Action> nativeOffered;
        public Track(String token,String title,String artist,String album,long durationMs,Like liked){this(token,title,artist,album,durationMs,liked,java.util.Collections.<Action>emptySet());}
        public Track(String token,String title,String artist,String album,long durationMs,Like liked,Set<Action> nativeOffered){
            this.token=opaqueToken(token);this.title=text(title,MAX_TITLE_CHARS);this.artist=text(artist,MAX_TITLE_CHARS);
            this.album=text(album,MAX_TITLE_CHARS);
            if(durationMs< -1 || durationMs>24L*60*60*1000)throw new IllegalArgumentException("Invalid reported duration");
            if(liked==null)throw new IllegalArgumentException("Unknown like state must be explicit");
            this.durationMs=durationMs;this.liked=liked;if(nativeOffered==null)throw new IllegalArgumentException("Missing offered queue actions");java.util.EnumSet<Action> copy=java.util.EnumSet.noneOf(Action.class);copy.addAll(nativeOffered);this.nativeOffered=Collections.unmodifiableSet(copy);
        }
        public boolean offers(Action action){return nativeOffered.contains(action);}
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
    /** One bounded display page over the supplied queue; indices are display-only. */
    public static final class Window {
        private final List<Track> tracks;private final int originalStart;private final long revision;
        private final String filter;private final boolean more;private final ViewState state;
        private Window(List<Track> tracks,int originalStart,long revision,String filter,boolean more,ViewState state){
            this.tracks=Collections.unmodifiableList(tracks);this.originalStart=originalStart;this.revision=revision;this.filter=filter;this.more=more;this.state=state;
        }
        public List<Track> tracks(){return tracks;}
        public int originalStart(){return originalStart;}
        public long revision(){return revision;}
        public String filter(){return filter;}
        public boolean hasMore(){return more;}
        public ViewState state(){return state;}
        public Intent play(int position){return intent(position,Action.PLAY_ENTRY);}
        public Intent remove(int position){return intent(position,Action.REMOVE_FROM_QUEUE);}
        public Intent toggleLike(int position){return intent(position,Action.TOGGLE_LIKE);}
        private Intent intent(int position,Action action){
            if(position<0 || position>=tracks.size())throw new IllegalArgumentException("Queue window position out of range");
            return new Intent(action,tracks.get(position).token(),revision);
        }
    }

    private final List<Track> tracks;private final long revision;private final String sourceLabel;private final ViewState state;
    public MusicQueue(List<Track> tracks,long revision,String sourceLabel){this(tracks,revision,sourceLabel,false);}
    public MusicQueue(List<Track> tracks,long revision,String sourceLabel,boolean nativeSubsetTruncated){
        if(tracks==null || tracks.size()>MAX_ENTRIES || revision<1)throw new IllegalArgumentException("Invalid queue page");
        Set<String> tokens=new HashSet<>();
        List<Track> copy=new ArrayList<>();
        for(Track track:tracks){if(track==null)throw new IllegalArgumentException("Missing queue entry");if(!tokens.add(track.token))throw new IllegalArgumentException("Duplicate queue token");copy.add(track);}
        this.tracks=Collections.unmodifiableList(copy);this.revision=revision;this.sourceLabel=text(sourceLabel,64);
        this.state=nativeSubsetTruncated?ViewState.TRUNCATED:tracks.isEmpty()?ViewState.EMPTY:ViewState.AVAILABLE;
    }
    private MusicQueue(long revision,ViewState state){
        this.tracks=Collections.emptyList();this.revision=revision;this.sourceLabel=state==ViewState.UNREAD?"未读取":"已提供空队列";this.state=state;
    }
    /** Explicit "not read" queue: distinct from a genuinely empty supplied queue. */
    public static MusicQueue unread(){return new MusicQueue(1,ViewState.UNREAD);}
    /** Explicitly empty supplied queue; shows "not read" differently, never a fabricated queue. */
    public static MusicQueue empty(){return new MusicQueue(1,ViewState.EMPTY);}
    public List<Track> tracks(){return tracks;}
    public long revision(){return revision;}
    public String sourceLabel(){return sourceLabel;}
    public ViewState state(){return state;}
    public Intent play(int index){return intent(index,Action.PLAY_ENTRY);}
    public Intent remove(int index){return intent(index,Action.REMOVE_FROM_QUEUE);}
    public Intent toggleLike(int index){return intent(index,Action.TOGGLE_LIKE);}
    private Intent intent(int index,Action action){Track track=require(index);return new Intent(action,track.token,revision);}
    public Track require(int index){if(index<0 || index>=tracks.size())throw new IllegalArgumentException("Queue index out of range");return tracks.get(index);}

    /**
     * Bounded literal filter + window over the CURRENT queue. The filter never reorders the queue
     * and intents keep each track's original token, not the filtered position.
     */
    public Window window(int start,int count,String literal){
        if(state==ViewState.UNREAD)throw new IllegalStateException("Queue not read");
        if(start<0 || count<1 || count>MAX_WINDOW)throw new IllegalArgumentException("Invalid queue window");
        List<Track> matched=new ArrayList<>();
        if(literal!=null)text(literal,MAX_FILTER_CHARS);
        String needle=literal==null?"":literal.trim().toLowerCase(Locale.ROOT);
        if(needle.length()>MAX_FILTER_CHARS)throw new IllegalArgumentException("Queue filter too long");
        for(Track track:tracks){
            if(needle.isEmpty() || track.title().toLowerCase(Locale.ROOT).contains(needle)
                    || track.artist().toLowerCase(Locale.ROOT).contains(needle)
                    || track.album().toLowerCase(Locale.ROOT).contains(needle))matched.add(track);
        }
        if(start>matched.size())throw new IllegalArgumentException("Queue window start out of range");
        int end=Math.min(matched.size(),start+count);
        return new Window(new ArrayList<>(matched.subList(start,end)),start,revision,literal==null?"":literal,end<matched.size() || state==ViewState.TRUNCATED,state);
    }
    /** Whole-queue truncated display (native supplied a bounded subset and says so). */
    public Window windowAll(String literal){
        try{return window(0,MAX_WINDOW,literal);}catch(IllegalStateException unread){return new Window(Collections.<Track>emptyList(),0,revision,"",false,ViewState.UNREAD);}
    }

    /** Opaque tokens are bounded ASCII identifiers; URLs are rejected rather than mined. */
    static String opaqueToken(String value){
        if(value==null || value.isEmpty() || value.length()>MAX_TOKEN_CHARS)throw new IllegalArgumentException("Invalid queue token");
        for(int i=0;i<value.length();i++){char c=value.charAt(i);boolean ok=(c>='a'&&c<='z')||(c>='A'&&c<='Z')||(c>='0'&&c<='9')||c=='_'||c=='-';if(!ok)throw new IllegalArgumentException("Queue token is not an opaque native id");}
        return value;
    }
    private static String text(String value,int max){if(value==null || value.length()>max)throw new IllegalArgumentException("Unbounded queue text");for(int i=0;i<value.length();i++){char c=value.charAt(i);if(Character.isISOControl(c))throw new IllegalArgumentException("Control in queue text");if(Character.isHighSurrogate(c)){if(++i>=value.length() || !Character.isLowSurrogate(value.charAt(i)))throw new IllegalArgumentException("Broken UTF16");}else if(Character.isLowSurrogate(c))throw new IllegalArgumentException("Broken UTF16");}return value;}
}
