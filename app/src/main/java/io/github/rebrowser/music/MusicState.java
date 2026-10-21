package io.github.rebrowser.music;

/**
 * Immutable READ-ONLY observation of the official player for display. It deliberately has no
 * mayPlay()/isAuthorized()/ownerGrant()/automatic-login or audio-focus-positive decision: a
 * reported account/playing flag is not fresh authentication, playback permission or work end.
 *
 * A DISPLAY-ONLY position estimate exists for smooth progress, but it never becomes the reported
 * value, never authorizes control and is explicitly stale/unknown when the clock or generation
 * cannot be trusted.
 */
public final class MusicState {
    public enum Playback { UNKNOWN, IDLE, PAUSED, PLAYING, BUFFERING, ENDED }
    public enum Account { UNKNOWN, REPORTED_SIGNED_IN, REPORTED_SIGNED_OUT }
    public enum Focus { UNOBSERVED, NONE, TRANSIENT_LOSS, PERMANENT_LOSS, GAIN_REPORTED }
    public static final int MAX_TITLE_CHARS = 512;
    public static final long MAX_POSITION_MS = 24L * 60 * 60 * 1000;
    /** An estimate may not be extrapolated for longer than this from the last observation. */
    public static final long MAX_ESTIMATE_MS = 6L * 60 * 60 * 1000;

    /** Pure display projection; UNKNOWN means "do not display a moving position". */
    public static final class Estimate {
        private final MusicState issuer;
        private final long positionMs;private final boolean known;private final boolean stale;private final boolean clamped;
        private Estimate(MusicState issuer,long positionMs,boolean known,boolean stale,boolean clamped){this.issuer=issuer;this.positionMs=positionMs;this.known=known;this.stale=stale;this.clamped=clamped;}
        public long positionMs(){return positionMs;}
        public boolean known(){return known;}
        /** The observation was too old or the supplied clock went backwards. */
        public boolean stale(){return stale;}
        public boolean clamped(){return clamped;}
        public boolean usable(){return known && !stale;}
        public boolean belongsTo(MusicState observed){return issuer==observed;}
    }

    private final Account account;private final Playback playback;private final Focus focus;
    private final String title,artist,resourceToken;private final long positionMs,durationMs,generation,observedAtMs;
    public MusicState(Account account,Playback playback,Focus focus,String title,String artist,String resourceToken,
                      long positionMs,long durationMs,long generation,long observedAtMs){
        if(account==null || playback==null || focus==null)throw new IllegalArgumentException("Missing observation axis");
        if(generation<1 || generation==Long.MAX_VALUE)throw new IllegalArgumentException("Invalid intent generation");
        if(observedAtMs<0)throw new IllegalArgumentException("Negative observation time");
        if(positionMs<0 || positionMs>MAX_POSITION_MS || durationMs< -1 || durationMs>MAX_POSITION_MS)throw new IllegalArgumentException("Invalid position/duration");
        if(durationMs>=0 && positionMs>durationMs)throw new IllegalArgumentException("Position exceeds reported duration");
        this.account=account;this.playback=playback;this.focus=focus;
        this.title=text(title,MAX_TITLE_CHARS);this.artist=text(artist,MAX_TITLE_CHARS);
        this.resourceToken=resourceToken==null?"":MusicQueue.opaqueToken(resourceToken);
        this.positionMs=positionMs;this.durationMs=durationMs;this.generation=generation;this.observedAtMs=observedAtMs;
    }
    public Account account(){return account;}
    public Playback playback(){return playback;}
    public Focus focus(){return focus;}
    public String title(){return title;}
    public String artist(){return artist;}
    public String resourceToken(){return resourceToken;}
    /** The REPORTED position; the display estimate is a separate value. */
    public long positionMs(){return positionMs;}
    /** -1 means the official player did not report a duration. */
    public long durationMs(){return durationMs;}
    public long intentGeneration(){return generation;}
    public long observedAtMs(){return observedAtMs;}
    public boolean hasReportedDuration(){return durationMs>=0;}
    /** Reported playback label; UNKNOWN is never rendered as stopped/finished. */
    public String playbackLabel(){
        switch(playback){case PLAYING:return "播放中（官网报告）";case PAUSED:return "已暂停（官网报告）";case BUFFERING:return "缓冲中（官网报告）";case ENDED:return "已结束（官网报告）";case IDLE:return "空闲（官网报告）";default:return "播放状态未知";}
    }
    /** Reported account label; never claims fresh authentication. */
    public String accountLabel(){switch(account){case REPORTED_SIGNED_IN:return "已登录（官网报告）";case REPORTED_SIGNED_OUT:return "未登录（官网报告）";default:return "账号未知";}}
    public MusicState withGeneration(long next){
        if(next<=generation)throw new IllegalArgumentException("Generation must advance");
        return new MusicState(account,playback,focus,title,artist,resourceToken.isEmpty()?null:resourceToken,positionMs,durationMs,next,observedAtMs);
    }
    /** New observation at the SAME intent generation; does not authorize any action. */
    public MusicState observe(Playback nextPlayback,long nextPositionMs,long nextDurationMs,long atMs){
        if(atMs<observedAtMs)throw new IllegalArgumentException("Observation time moved backwards");
        return new MusicState(account,nextPlayback,focus,title,artist,resourceToken.isEmpty()?null:resourceToken,nextPositionMs,nextDurationMs,generation,atMs);
    }

    /**
     * Display-only estimate for the SAME generation. Requires a reported PLAYING observation with
     * a known observation time; a backwards clock, an exhausted/stale interval or another
     * generation yields an UNKNOWN/STALE estimate instead of a moving value.
     */
    public Estimate estimatePosition(long forGeneration,long nowElapsedMs){
        return estimatePosition(forGeneration,nowElapsedMs,null); // rate was not reported: never assume 1x
    }
    public Estimate estimatePosition(long forGeneration,long nowElapsedMs,Double nativeReportedRate){
        if(nativeReportedRate!=null && (!Double.isFinite(nativeReportedRate) || nativeReportedRate<0 || nativeReportedRate>16))throw new IllegalArgumentException("Invalid reported rate");
        if(nowElapsedMs<0)throw new IllegalArgumentException("Negative elapsed time");
        if(forGeneration!=generation)return new Estimate(this,positionMs,false,true,false);
        if(playback!=Playback.PLAYING || nativeReportedRate==null)return new Estimate(this,positionMs,false,false,false);
        if(nowElapsedMs<observedAtMs)return new Estimate(this,positionMs,false,true,false);
        long delta=nowElapsedMs-observedAtMs;
        if(delta>MAX_ESTIMATE_MS)return new Estimate(this,positionMs,false,true,false);
        long estimated=positionMs+(long)(delta*nativeReportedRate);
        boolean clamped=false;
        if(durationMs>=0 && estimated>durationMs){estimated=durationMs;clamped=true;}
        if(estimated>MAX_POSITION_MS){estimated=MAX_POSITION_MS;clamped=true;}
        return new Estimate(this,estimated,true,false,clamped);
    }

    static String text(String value,int max){
        if(value==null)return "";
        if(value.length()>max)throw new IllegalArgumentException("Unbounded music text");
        for(int i=0;i<value.length();i++){char c=value.charAt(i);if(Character.isISOControl(c))throw new IllegalArgumentException("Control in music text");if(Character.isHighSurrogate(c)){if(++i>=value.length() || !Character.isLowSurrogate(value.charAt(i)))throw new IllegalArgumentException("Broken UTF16");}else if(Character.isLowSurrogate(c))throw new IllegalArgumentException("Broken UTF16");}
        return value;
    }
}
