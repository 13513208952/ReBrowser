package io.github.rebrowser.music;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Read-only music preferences PROJECTION and bounded change INTENTS. Nothing is written, no v1
 * value is migrated, no account/source is loaded and a stored resume intent never authorizes
 * playback. Unknown or corrupt fields stay unknown instead of becoming a default.
 */
public final class MusicPreferences {
    public static final int MAX_TOKEN_CHARS = 64;
    public static final long MAX_POSITION_MS = 24L * 60 * 60 * 1000;
    public enum Source { UNKNOWN, OFFICIAL_WEB }
    public enum Entry { UNKNOWN, MUSIC_HOME, LAST_QUEUE, SEARCH }

    public static final class Values {
        private final Source source;private final Entry entry;private final Boolean resumeIntent;
        private final long resumePositionMs;private final String lastQueueToken;
        public Values(Source source,Entry entry,Boolean resumeIntent,long resumePositionMs,String lastQueueToken){
            if(source==null || entry==null)throw new IllegalArgumentException("Unknown preference axes must be explicit");
            if(resumePositionMs<0 || resumePositionMs>MAX_POSITION_MS)throw new IllegalArgumentException("Invalid resume position");
            if(lastQueueToken!=null && (lastQueueToken.isEmpty() || lastQueueToken.length()>MAX_TOKEN_CHARS))throw new IllegalArgumentException("Invalid queue token");
            if(lastQueueToken!=null)MusicQueue.opaqueToken(lastQueueToken);
            this.source=source;this.entry=entry;this.resumeIntent=resumeIntent;this.resumePositionMs=resumePositionMs;this.lastQueueToken=lastQueueToken;
        }
        public Source source(){return source;}
        public Entry entry(){return entry;}
        /** null = not observed; true only means the owner previously expressed intent. */
        public Boolean resumeIntent(){return resumeIntent;}
        public long resumePositionMs(){return resumePositionMs;}
        public String lastQueueToken(){return lastQueueToken;}
    }

    public static final class Snapshot {
        private final Values values;private final long revision;
        public Snapshot(Values values,long revision){
            if(values==null || revision<1)throw new IllegalArgumentException("Invalid preferences snapshot");
            this.values=values;this.revision=revision;
        }
        public Values values(){return values;}
        public long revision(){return revision;}
        /** Unknown remains unknown; no fallback default is invented for display. */
        public String sourceLabel(){switch(values.source){case OFFICIAL_WEB:return "官方网页音乐";default:return "音源未知";}}
        public String entryLabel(){switch(values.entry){case MUSIC_HOME:return "音乐首页";case LAST_QUEUE:return "上次队列";case SEARCH:return "搜索";default:return "入口未知";}}
        public String resumeLabel(){
            if(values.resumeIntent==null)return "恢复意图未读取";
            return values.resumeIntent?"已记录恢复意图（仍需明确起播）":"不自动恢复";
        }
    }

    public static final class Change {
        private final Values next;private final long expectedRevision;
        public Change(Values next,long expectedRevision){
            if(next==null || expectedRevision<1)throw new IllegalArgumentException("Invalid preference change");
            this.next=next;this.expectedRevision=expectedRevision;
        }
        public Values values(){return next;}
        public long expectedRevision(){return expectedRevision;}
        /** Intent only: the native preference owner decides success and persistence. */
        public boolean isPersisted(){return false;}
    }

    public static Snapshot snapshot(Values values,long revision){return new Snapshot(values,revision);}

    public static Change changeSource(Snapshot current,Source source){
        Snapshot base=require(current);
        if(source==Source.UNKNOWN)throw new IllegalArgumentException("Cannot persist unknown source");
        return new Change(new Values(source,base.values.entry,base.values.resumeIntent,base.values.resumePositionMs,base.values.lastQueueToken),base.revision);
    }
    public static Change changeEntry(Snapshot current,Entry entry){
        Snapshot base=require(current);
        if(entry==Entry.UNKNOWN)throw new IllegalArgumentException("Cannot persist unknown entry");
        return new Change(new Values(base.values.source,entry,base.values.resumeIntent,base.values.resumePositionMs,base.values.lastQueueToken),base.revision);
    }
    public static Change changeResume(Snapshot current,boolean resumeIntent,long positionMs){
        Snapshot base=require(current);
        return new Change(new Values(base.values.source,base.values.entry,resumeIntent,positionMs,base.values.lastQueueToken),base.revision);
    }
    public static Change clearQueue(Snapshot current){
        Snapshot base=require(current);
        return new Change(new Values(base.values.source,base.values.entry,base.values.resumeIntent,base.values.resumePositionMs,null),base.revision);
    }
    /** Capture expected revision ONLY. The native repository must compare it; DTO is not freshness proof. */
    private static Snapshot require(Snapshot current){if(current==null)throw new IllegalArgumentException("Missing preferences snapshot");return current;}

    /** Shape/size checks of a DISPLAY projection only, NOT persisted schema/enum validation or repair. */
    public static List<String> rejectedFields(java.util.Map<String,String> raw){
        if(raw==null || raw.size()>16)throw new IllegalArgumentException("Missing raw preferences");
        List<String> rejected=new ArrayList<>();
        for(java.util.Map.Entry<String,String> entry:raw.entrySet()){
            String key=entry.getKey();if(key==null || !key.matches("[A-Za-z0-9_-]{1,64}"))throw new IllegalArgumentException("Invalid projection field label");
            String value=entry.getValue();
            if(value==null || value.length()>256)rejected.add(entry.getKey());
        }
        return Collections.unmodifiableList(rejected);
    }
    private MusicPreferences(){}
}
