package io.github.rebrowser.bridge;

import io.github.rebrowser.data.NativeRecordCodec;
import io.github.rebrowser.music.MusicState;
import java.util.Map;

/** Bounded READ-ONLY page observation consumer. Even on an exact native port, page-reported
 * signed-in/playing/rate fields are DISPLAY claims, not account binding, playback authorization,
 * audio-focus ownership, official effect receipts or actual native-work end. No JS eval/player API. */
public final class MusicConnector {
    public static final String ACTION="music.observe";
    public static final class Observation {
        private final MusicState.Account account;private final MusicState.Playback playback;private final String title,artist;private final Long position,duration;private final Double rate;private final long nativeTime;
        private Observation(MusicState.Account account,MusicState.Playback playback,String title,String artist,Long position,Long duration,Double rate,long time){this.account=account;this.playback=playback;this.title=title;this.artist=artist;this.position=position;this.duration=duration;this.rate=rate;nativeTime=time;}
        public MusicState.Account reportedAccount(){return account;}public MusicState.Playback reportedPlayback(){return playback;}public String title(){return title;}public String artist(){return artist;}public Long reportedPositionMs(){return position;}public Long reportedDurationMs(){return duration;}public Double reportedRate(){return rate;}public long nativeObservedAtMs(){return nativeTime;}
    }
    private final DocumentLease lease;private long last=-1;
    public MusicConnector(DocumentLease nativeRoutingLease){if(nativeRoutingLease==null)throw bad();lease=nativeRoutingLease;}
    public Observation observe(DocumentLease.Received received,long nativeElapsedMs){
        if(received==null || nativeElapsedMs<0 || nativeElapsedMs<last)throw bad();received.requireLease(lease);BridgeMessages.Value payload=observationPayload(received.message(),"music-observation");Map<String,BridgeMessages.Value> f=object(payload,"account","playback","title","artist","positionMs","durationMs","rate");
        MusicState.Account account;MusicState.Playback playback;try{account=MusicState.Account.valueOf(f.get("account").string());playback=MusicState.Playback.valueOf(f.get("playback").string());}catch(IllegalArgumentException invalid){throw bad();}
        Long position=nullableMillis(f.get("positionMs")),duration=nullableMillis(f.get("durationMs"));if(position!=null && duration!=null && position>duration)throw bad();Double rate=null;if(f.get("rate").type()!=BridgeMessages.ValueType.NULL){rate=f.get("rate").asDouble();if(!Double.isFinite(rate) || rate<0 || rate>16)throw bad();}
        String title=text(f.get("title"),512),artist=text(f.get("artist"),512);received.requireLease(lease);last=nativeElapsedMs;return new Observation(account,playback,title,artist,position,duration,rate,nativeElapsedMs);
    }
    static BridgeMessages.Value observationPayload(BridgeMessages.Message m,String event){if(m.kind()==BridgeMessages.Kind.EVENT){if(!event.equals(m.event().eventType()))throw bad();return m.event().payload();}if(m.kind()==BridgeMessages.Kind.RESULT){BridgeMessages.Result result=m.result();if(!"observed".equals(result.outcome()) || !"page-observation".equals(result.evidenceKind()))throw bad();return result.result();}throw bad();}
    static Map<String,BridgeMessages.Value> object(BridgeMessages.Value value,String...fields){Map<String,BridgeMessages.Value> map=value.object();if(map.size()!=fields.length)throw bad();for(String field:fields)if(!map.containsKey(field))throw bad();return map;}
    static String text(BridgeMessages.Value v,int maximumUtf8){return NativeRecordCodec.validateText(v.string(),maximumUtf8,true);}
    static Long nullableMillis(BridgeMessages.Value v){if(v.type()==BridgeMessages.ValueType.NULL)return null;long n=v.asLong();if(n<0 || n>MusicState.MAX_POSITION_MS)throw bad();return n;}
    static IllegalArgumentException bad(){return new IllegalArgumentException("Invalid bounded connector observation");}
}
