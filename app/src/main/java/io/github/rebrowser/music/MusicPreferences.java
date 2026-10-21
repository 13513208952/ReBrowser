package io.github.rebrowser.music;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only music preferences PROJECTION and bounded change INTENTS. Nothing is written, no v1
 * value is migrated, no account/source is loaded and a stored resume intent never authorizes
 * playback. A {@link Draft} merges several typed field edits into ONE immutable change bound to
 * the original revision; no-op, conflicting and unknown inputs are explicit.
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
        boolean sameAs(Values other){
            return source==other.source && entry==other.entry
                    && java.util.Objects.equals(resumeIntent,other.resumeIntent)
                    && resumePositionMs==other.resumePositionMs
                    && java.util.Objects.equals(lastQueueToken,other.lastQueueToken);
        }
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

    /** One axis may be edited at most once per draft; a second different value is a conflict. */
    public enum Axis { SOURCE, ENTRY, RESUME, QUEUE }

    public static final class Draft {
        private final Thread owner=Thread.currentThread();
        private final Snapshot base;
        private final Map<Axis,String> claimed=new EnumMap<>(Axis.class);
        private Source source;private Entry entry;private Boolean resumeIntent;private Long resumePosition;private String queueToken;private boolean conflicted;
        public Draft(Snapshot base){if(base==null)throw new IllegalArgumentException("Missing preferences base");this.base=base;this.source=base.values().source;this.entry=base.values().entry;this.resumeIntent=base.values().resumeIntent;this.resumePosition=base.values().resumePositionMs;this.queueToken=base.values().lastQueueToken;}
        public Snapshot base(){check();return base;}
        public String setSource(Source value){
            check();
            if(value==null || value==Source.UNKNOWN)return "未知音源不能保存";
            String conflict=claim(Axis.SOURCE,String.valueOf(value));if(conflict!=null)return conflict;
            source=value;return null;
        }
        public String setEntry(Entry value){
            check();
            if(value==null || value==Entry.UNKNOWN)return "未知入口不能保存";
            String conflict=claim(Axis.ENTRY,String.valueOf(value));if(conflict!=null)return conflict;
            entry=value;return null;
        }
        public String setResume(Boolean intent,long positionMs){
            check();
            if(positionMs<0 || positionMs>MAX_POSITION_MS)return "恢复位置超出范围";
            String conflict=claim(Axis.RESUME,intent+":"+positionMs);if(conflict!=null)return conflict;
            resumeIntent=intent;resumePosition=positionMs;return null;
        }
        public String clearQueue(){
            check();
            String conflict=claim(Axis.QUEUE,"");if(conflict!=null)return conflict;
            queueToken=null;return null;
        }
        private String claim(Axis axis,String value){
            String previous=claimed.get(axis);
            if(previous!=null && !previous.equals(value)){conflicted=true;return "同一项存在冲突修改";}
            claimed.put(axis,value);return null;
        }
        public boolean isDirty(){check();return !merge().values().sameAs(base.values());}
        /** Merge every edited axis into ONE change; a no-op returns the base values/revision. */
        public Change merge(){
            check();if(conflicted)throw new IllegalStateException("Conflicting preference changes cannot form a batch");
            Values next=new Values(source,entry,resumeIntent,resumePosition==null?0:resumePosition,queueToken);
            return new Change(next,base.revision());
        }
        private void check(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Preferences draft owner thread required");}
    }

    public static Snapshot snapshot(Values values,long revision){return new Snapshot(values,revision);}

    public static Change changeSource(Snapshot current,Source source){
        Draft draft=new Draft(current);String problem=draft.setSource(source);
        if(problem!=null)throw new IllegalArgumentException(problem);
        return draft.merge();
    }
    public static Change changeEntry(Snapshot current,Entry entry){
        Draft draft=new Draft(current);String problem=draft.setEntry(entry);
        if(problem!=null)throw new IllegalArgumentException(problem);
        return draft.merge();
    }
    public static Change changeResume(Snapshot current,boolean resumeIntent,long positionMs){
        Draft draft=new Draft(current);String problem=draft.setResume(resumeIntent,positionMs);
        if(problem!=null)throw new IllegalArgumentException(problem);
        return draft.merge();
    }
    public static Change clearQueue(Snapshot current){
        Draft draft=new Draft(current);String problem=draft.clearQueue();
        if(problem!=null)throw new IllegalArgumentException(problem);
        return draft.merge();
    }

    /** Bounded DISPLAY projection shape checks only, NOT persisted schema validation/repair. */
    public static List<String> rejectedFields(Map<String,String> raw){
        if(raw==null || raw.size()>16)throw new IllegalArgumentException("Invalid projection field count");
        List<String> rejected=new ArrayList<>();
        for(Map.Entry<String,String> entry:raw.entrySet()){
            if(entry.getKey()==null || !entry.getKey().matches("[A-Za-z0-9_-]{1,64}"))throw new IllegalArgumentException("Invalid projection field label");
            String value=entry.getValue();
            if(value==null || value.length()>256)rejected.add(entry.getKey());
        }
        return Collections.unmodifiableList(rejected);
    }
    private MusicPreferences(){}
}
