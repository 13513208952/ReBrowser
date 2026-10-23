package io.github.rebrowser.browser;

import io.github.rebrowser.bootstrap.ComponentGate;
import io.github.rebrowser.data.NativeRecordCodec;
import io.github.rebrowser.data.OwnedDataCatalog;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.*;

/** Browser history, NOT website state or Gecko's back/forward list. Ordinary TEMPORARY
 * workspaces DO have 30-minute history. Only the explicit process recording switch suppresses
 * new visits. Source protection still precedes IO; no Cookie/Profile data is read or deleted.
 * RBHIST02 corrects the unshipped RBHIST01 model; no guessed conversion/default/repair of old
 * bytes. The bounded metadata backend reports capacity failure, NEVER evicts retained history.
 */
public final class HistoryStore {
    public static final int MAX_ENTRIES=5000,MAX_BYTES=4*1024*1024,MAX_CURRENT_PAGES=64*50;
    public static final long RETENTION_MS=30L*60*1000;

    /** Native catalog metadata, not an authorization token. Scope is a WHOLE workspace. */
    public static final class Source {
        private final String workspaceId,title;private final BrowserModels.Level level;private final boolean open;
        public Source(String workspaceId,String title,BrowserModels.Level level,boolean open){id(workspaceId,"ws_");if(level==null)throw bad();this.workspaceId=workspaceId;this.title=NativeRecordCodec.validateText(title,640,true);this.level=level;this.open=open;}
        public String workspaceId(){return workspaceId;}public String title(){return title;}
        public boolean longLived(){return open && level!=BrowserModels.Level.TEMPORARY;}
        public boolean open(){return open;}
        public static Source from(BrowserModels.WorkspaceView actualCatalogView){if(actualCatalogView==null)throw bad();return new Source(actualCatalogView.ref().id(),actualCatalogView.title(),actualCatalogView.level(),actualCatalogView.placement()==BrowserModels.Placement.ACTIVE);}
    }
    /** Current native page metadata for a genuine visit or explicit recording-resume sync.
     * Caller must bind it to the real browser owner/event, not accept it from a website/IPC. */
    public static final class Page {
        private final Source source;private final String tabId,title,url;
        public Page(Source source,String tabId,String title,String url){if(source==null || !source.open())throw bad();id(tabId,"tab_");this.source=source;this.tabId=tabId;this.title=NativeRecordCodec.validateText(title,640,true);this.url=webUrl(url);}
        public Source source(){return source;}public String tabId(){return tabId;}public String title(){return title;}public String url(){return url;}
    }
    public static final class Entry {
        private final String id,workspaceId,tabId,sourceTitle,title,url;private final long visitedUtc,ordinal;private final Long expiresUtc;
        public Entry(String id,String workspaceId,String tabId,String sourceTitle,String title,String url,long visitedUtc,Long expiresUtc,long ordinal){
            HistoryStore.id(id,"hv_");HistoryStore.id(workspaceId,"ws_");HistoryStore.id(tabId,"tab_");time(visitedUtc);if(expiresUtc!=null)time(expiresUtc);if(ordinal<1)throw bad();
            this.id=id;this.workspaceId=workspaceId;this.tabId=tabId;this.sourceTitle=NativeRecordCodec.validateText(sourceTitle,640,true);this.title=NativeRecordCodec.validateText(title,640,true);this.url=webUrl(url);this.visitedUtc=visitedUtc;this.expiresUtc=expiresUtc;this.ordinal=ordinal;
        }
        public String id(){return id;}public String workspaceId(){return workspaceId;}public String tabId(){return tabId;}public String sourceTitle(){return sourceTitle;}
        public String title(){return title;}public String url(){return url;}public long visitedUtcMs(){return visitedUtc;}public Long expiresUtcMs(){return expiresUtc;}public long ordinal(){return ordinal;}
        public boolean timed(){return expiresUtc!=null;}
        private Entry expiry(Long expiry,String sourceName){return new Entry(id,workspaceId,tabId,sourceName,title,url,visitedUtc,expiry,ordinal);}
    }
    private static final Comparator<Entry> ORDER=(a,b)->{int byTime=Long.compare(b.visitedUtc,a.visitedUtc);return byTime!=0?byTime:Long.compare(b.ordinal,a.ordinal);};
    public static final class Snapshot {
        private final long revision;private final boolean maintaining;private final List<Entry> entries;
        public Snapshot(long metadataRevision,boolean maintaining,List<Entry> input){
            if(metadataRevision<0 || input==null || input.size()>MAX_ENTRIES)throw bad();revision=metadataRevision;this.maintaining=maintaining;
            List<Entry> copy=new ArrayList<>(input);Set<String> ids=new HashSet<>();Set<Long> ordinals=new HashSet<>();long bytes=21;
            for(Entry e:copy){if(e==null || !ids.add(e.id) || !ordinals.add(e.ordinal))throw bad();bytes+=2*6+e.id.length()+e.workspaceId.length()+e.tabId.length()+8*2+1+(e.timed()?8:0)+utfBytes(e.sourceTitle)+utfBytes(e.title)+utfBytes(e.url);if(bytes>MAX_BYTES)throw bad();}
            copy.sort(ORDER);entries=Collections.unmodifiableList(copy);
        }
        public long metadataRevision(){return revision;}public boolean maintaining(){return maintaining;}public List<Entry> entries(){return entries;}
        public Snapshot expire(long now){time(now);if(maintaining)return this;List<Entry> live=new ArrayList<>();for(Entry e:entries)if(e.expiresUtc==null || e.expiresUtc>now)live.add(e);return live.size()==entries.size()?this:new Snapshot(next(revision),false,live);}
        public Snapshot withMaintaining(boolean value,long now){
            time(now);if(value==maintaining)return expire(now);Snapshot before=expire(now);List<Entry> changed=new ArrayList<>();for(Entry e:before.entries)changed.add(!value && e.timed()?e.expiry(deadline(now),e.sourceTitle):e);return new Snapshot(next(before.revision),value,changed);
        }
        /** One action, not a switch; the DATA LAYER also rejects it while maintaining. */
        public Snapshot clearTimed(){if(maintaining)throw new IllegalStateException("维持记录期间不能清理有时限记录");List<Entry> live=new ArrayList<>();for(Entry e:entries)if(!e.timed())live.add(e);return live.size()==entries.size()?this:new Snapshot(next(revision),false,live);}
        public Snapshot addObservedVisit(Page page,long now){
            if(page==null)throw bad();time(now);Snapshot before=expire(now);if(before.entries.size()>=MAX_ENTRIES)throw new IllegalStateException("历史记录容量已满，未删除已有记录");long ordinal=0;Set<String> ids=new HashSet<>();for(Entry e:before.entries){ordinal=Math.max(ordinal,e.ordinal);ids.add(e.id);}ordinal=next(ordinal);
            String id=null;for(int n=0;n<16;n++){String candidate=randomId();if(!ids.contains(candidate)){id=candidate;break;}}if(id==null)throw bad();List<Entry> copy=new ArrayList<>(before.entries);Source s=page.source;copy.add(new Entry(id,s.workspaceId,page.tabId,s.title,page.title,page.url,now,s.longLived()?null:deadline(now),ordinal));return new Snapshot(next(before.revision),maintaining,copy);
        }
        /** Call at actual workspace promotion/demotion/shelf/restore/removal time. Closing a tab
         * alone never calls this. Timed records are not renewed by closing/deleting again. */
        public Snapshot workspaceChanged(Source source,long now){
            if(source==null)throw bad();time(now);Snapshot before=expire(now);List<Entry> changed=new ArrayList<>();boolean any=false;
            for(Entry e:before.entries){Entry after=e;if(e.workspaceId.equals(source.workspaceId)){Long expiry=source.longLived()?null:e.expiresUtc==null?deadline(now):e.expiresUtc;after=e.expiry(expiry,source.title);any|=!Objects.equals(expiry,e.expiresUtc) || !source.title.equals(e.sourceTitle);}changed.add(after);}return any?new Snapshot(next(before.revision),maintaining,changed):before;
        }
        /** Startup reconciliation: missing formerly durable sources get ONE deadline, not a new
         * deadline on every launch. Only actual open durable sources become long-lived again. */
        public Snapshot reconcileSources(List<Source> actualSources,long now){
            if(actualSources==null || actualSources.size()>64)throw bad();time(now);Map<String,Source> sources=new HashMap<>();for(Source s:actualSources)if(s==null || sources.put(s.workspaceId,s)!=null)throw bad();Snapshot before=expire(now);List<Entry> changed=new ArrayList<>();boolean any=false;
            for(Entry e:before.entries){Source s=sources.get(e.workspaceId);Long expiry=s!=null && s.longLived()?null:e.expiresUtc==null?deadline(now):e.expiresUtc;String name=s==null?e.sourceTitle:s.title;any|=!Objects.equals(expiry,e.expiresUtc) || !name.equals(e.sourceTitle);changed.add(e.expiry(expiry,name));}return any?new Snapshot(next(before.revision),maintaining,changed):before;
        }
        public Snapshot remove(String id){List<Entry> changed=new ArrayList<>();boolean found=false;for(Entry e:entries)if(e.id.equals(id))found=true;else changed.add(e);if(!found)throw bad();return new Snapshot(next(revision),maintaining,changed);}
        /** Exact original visit + URL only: a late title never creates another visit. */
        public Snapshot updateTitle(String id,String expectedUrl,String title){String clean=NativeRecordCodec.validateText(title,640,true);webUrl(expectedUrl);List<Entry> changed=new ArrayList<>();boolean any=false;for(Entry e:entries){if(e.id.equals(id) && e.url.equals(expectedUrl)){changed.add(new Entry(e.id,e.workspaceId,e.tabId,e.sourceTitle,clean,e.url,e.visitedUtc,e.expiresUtc,e.ordinal));any|=!clean.equals(e.title);}else changed.add(e);}return any?new Snapshot(next(revision),maintaining,changed):this;}
        public Entry findVisible(String id,long now){time(now);for(Entry e:entries)if(e.id.equals(id) && (maintaining || e.expiresUtc==null || e.expiresUtc>now))return e;return null;}
        public List<Entry> query(String literal,int offset,int limit){NativeRecordCodec.validateText(literal,256,true);if(offset<0 || offset>MAX_ENTRIES || limit<1 || limit>100)throw bad();List<Entry> page=new ArrayList<>();int hits=0;for(Entry e:entries)if(e.title.contains(literal) || e.url.contains(literal)){if(hits++>=offset && page.size()<limit)page.add(e);}return Collections.unmodifiableList(page);}
        /** Latest eight DISTINCT FULL URLs, not favorites or a mutated/deduplicated history. */
        public List<Entry> recentEntries(long now){time(now);List<Entry> result=new ArrayList<>();Set<String> urls=new HashSet<>();for(Entry e:entries)if((maintaining || !e.timed() || e.expiresUtc>now) && urls.add(e.url)){result.add(e);if(result.size()==8)break;}return Collections.unmodifiableList(result);}
    }

    /** One canonical PROCESS lifetime recording switch. NEVER serialized with the history. */
    public static final class RecordingSession {
        private boolean paused;private long revision;
        public synchronized boolean paused(){return paused;}
        public synchronized long revision(){return revision;}
        public synchronized Change setPaused(boolean value){boolean changed=value!=paused;if(changed){revision=next(revision);paused=value;}return new Change(this,revision,changed && !value,changed);}
        public synchronized boolean acceptsVisit(){return !paused;}
        /** Capture at the native visit event, BEFORE queueing IO. An intermediate paused visit
         * cannot become recordable merely because the user resumes before the worker runs. */
        public synchronized long captureVisitRevision(){return paused?-1:revision;}
        public synchronized boolean acceptsCapturedVisit(long observedRevision){return observedRevision>=0 && !paused && observedRevision==revision;}
        /** Resume is one-shot even if a subsequent IO write has an unknown outcome. */
        private synchronized void consumeResume(Change change){if(change==null || change.issuer!=this || !change.resume || change.revision!=revision || change.spent || paused)throw new IllegalStateException("历史记录恢复请求已过期或已使用");change.spent=true;}
    }
    public static final class Change {
        private final RecordingSession issuer;private final long revision;private final boolean resume,changed;private boolean spent;
        private Change(RecordingSession issuer,long revision,boolean resume,boolean changed){this.issuer=issuer;this.revision=revision;this.resume=resume;this.changed=changed;}
        public boolean changed(){return changed;}public boolean needsCurrentPageSync(){return resume;}
    }
    public static final NativeRecordCodec.Codec<Snapshot> CODEC=new NativeRecordCodec.Codec<Snapshot>(){
        public Snapshot decode(ByteBuffer input){NativeRecordCodec.Reader r=new NativeRecordCodec.Reader(input,"RBHIST02",MAX_BYTES);long revision=r.nonnegative();boolean maintain=r.bool();int count=r.u16();if(count>MAX_ENTRIES)throw bad();List<Entry> entries=new ArrayList<>();Entry previous=null;for(int i=0;i<count;i++){String id=r.text(35,false),ws=r.text(35,false),tab=r.text(36,false);long visited=r.nonnegative(),ordinal=r.nonnegative();Long expires=r.bool()?r.nonnegative():null;String source=r.text(640,true),title=r.text(640,true),url=r.text(8192,false);Entry e=new Entry(id,ws,tab,source,title,url,visited,expires,ordinal);if(!e.url.equals(url) || previous!=null && ORDER.compare(previous,e)>=0)throw bad();entries.add(e);previous=e;}r.end();return new Snapshot(revision,maintain,entries);}
        public byte[] encode(Snapshot s){if(s==null)throw bad();NativeRecordCodec.Writer w=new NativeRecordCodec.Writer("RBHIST02",MAX_BYTES).nonnegative(s.revision).bool(s.maintaining).u16(s.entries.size());for(Entry e:s.entries){w.text(e.id,35,false).text(e.workspaceId,35,false).text(e.tabId,36,false).nonnegative(e.visitedUtc).nonnegative(e.ordinal).bool(e.timed());if(e.timed())w.nonnegative(e.expiresUtc);w.text(e.sourceTitle,640,true).text(e.title,640,true).text(e.url,8192,false);}return w.finish();}
    };
    private final OwnedDataCatalog records;private final ComponentGate.DataEpochPin source;private final RecordingSession recording=new RecordingSession();
    public HistoryStore(OwnedDataCatalog records,ComponentGate.BusinessPermit permit){if(records==null)throw bad();this.records=records;source=records.captureDataEpoch(permit);}
    public boolean paused(){source.requireCurrent();return recording.paused();}
    /** UI-thread safe, no filesystem access. Resume's actual page synchronization runs on IO. */
    public Change setPaused(boolean value){source.requireCurrent();return recording.setPaused(value);}
    public long recordingRevision(){source.requireCurrent();return recording.revision();}
    public long captureVisitRecordingRevision(){source.requireCurrent();return recording.captureVisitRevision();}
    public OwnedDataCatalog.View<Snapshot> read(ComponentGate.BusinessPermit permit)throws IOException,GeneralSecurityException{return records.read(OwnedDataCatalog.Family.HISTORY,CODEC,permit);}
    public long compareAndWrite(OwnedDataCatalog.View<Snapshot> expected,Snapshot intended,ComponentGate.BusinessPermit permit)throws IOException,GeneralSecurityException{records.requireFamily(expected,OwnedDataCatalog.Family.HISTORY,CODEC);if(intended==null || expected.kind()==io.github.rebrowser.data.PrivateRecordStore.Kind.CURRENT && intended.revision<=expected.value(permit).revision)throw bad();return records.compareAndWrite(expected,intended,permit);}
    private interface Edit {Snapshot apply(Snapshot before);}
    private synchronized Snapshot edit(Edit edit,ComponentGate.BusinessPermit permit)throws IOException,GeneralSecurityException{source.requireCurrent();try(OwnedDataCatalog.View<Snapshot> view=read(permit)){Snapshot before=view.value(permit),after=edit.apply(before);if(after!=before)compareAndWrite(view,after,permit);source.requireCurrent();return after;}}
    /** Read-time expiry uses actual AEAD/CAS IO. Missing/corrupt metadata is not a fresh install. */
    public Snapshot readCurrent(long now,ComponentGate.BusinessPermit permit)throws IOException,GeneralSecurityException{return edit(s->s.expire(now),permit);}
    public Snapshot setMaintaining(boolean value,long now,ComponentGate.BusinessPermit permit)throws IOException,GeneralSecurityException{return edit(s->s.withMaintaining(value,now),permit);}
    public Snapshot clearTimed(ComponentGate.BusinessPermit permit)throws IOException,GeneralSecurityException{return edit(Snapshot::clearTimed,permit);}
    public Snapshot workspaceChanged(Source nativeSource,long now,ComponentGate.BusinessPermit permit)throws IOException,GeneralSecurityException{return edit(s->s.workspaceChanged(nativeSource,now),permit);}
    public Snapshot reconcileSources(List<Source> nativeSources,long now,ComponentGate.BusinessPermit permit)throws IOException,GeneralSecurityException{return edit(s->s.reconcileSources(nativeSources,now),permit);}
    public Snapshot remove(String id,long now,ComponentGate.BusinessPermit permit)throws IOException,GeneralSecurityException{return edit(s->{Snapshot live=s.expire(now);if(live.findVisible(id,now)==null)throw new IllegalStateException("记录已到期或删除");return live.remove(id);},permit);}
    public Snapshot updateNativeVisitTitle(String originalVisitId,String expectedUrl,String title,ComponentGate.BusinessPermit permit)throws IOException,GeneralSecurityException{return edit(s->recording.acceptsVisit()?s.updateTitle(originalVisitId,expectedUrl,title):s,permit);}
    public Snapshot removeExact(String id,String expectedUrl,long now,ComponentGate.BusinessPermit permit)throws IOException,GeneralSecurityException{return edit(s->{Snapshot live=s.expire(now);Entry entry=live.findVisible(id,now);if(entry==null || !entry.url.equals(expectedUrl))throw new IllegalStateException("原历史记录已改变或到期");return live.remove(id);},permit);}
    public Snapshot recordVisit(Page nativePage,long now,ComponentGate.BusinessPermit permit)throws IOException,GeneralSecurityException{return recordVisit(nativePage,now,captureVisitRecordingRevision(),permit);}
    /** The observed generation is scheduling metadata, NOT website/admin/source authority. */
    public Snapshot recordVisit(Page nativePage,long now,long observedRecordingRevision,ComponentGate.BusinessPermit permit)throws IOException,GeneralSecurityException{return edit(s->recording.acceptsCapturedVisit(observedRecordingRevision)?s.addObservedVisit(nativePage,now):s.expire(now),permit);}
    public Snapshot resumeCurrentPages(Change change,List<Page> actualOpenPages,long now,ComponentGate.BusinessPermit permit)throws IOException,GeneralSecurityException{
        source.requireCurrent();if(actualOpenPages==null || actualOpenPages.size()>MAX_CURRENT_PAGES)throw bad();Set<String> ids=new HashSet<>();for(Page p:actualOpenPages)if(p==null || !ids.add(p.source.workspaceId+":"+p.tabId))throw bad();recording.consumeResume(change);
        return edit(s->{Snapshot after=s.expire(now);for(Page p:actualOpenPages){if(!recording.acceptsVisit())break;after=after.addObservedVisit(p,now);}return after;},permit);
    }
    private static void id(String id,String prefix){if(id==null || !id.matches(prefix+"[0-9a-f]{32}"))throw bad();}
    private static void time(long value){if(value<0)throw bad();}
    private static long deadline(long now){time(now);if(now>Long.MAX_VALUE-RETENTION_MS)throw bad();return now+RETENTION_MS;}
    private static String webUrl(String url){NativeRecordCodec.validateText(url,8192,false);String result=SearchEngines.requireWebUrl(url).url();NativeRecordCodec.validateText(result,8192,false);return result;}
    private static int utfBytes(String s){return s.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;}
    private static long next(long r){if(r==Long.MAX_VALUE)throw bad();return r+1;}
    private static String randomId(){byte[] b=new byte[16];new SecureRandom().nextBytes(b);StringBuilder s=new StringBuilder("hv_");for(byte v:b)s.append(Character.forDigit((v&255)>>>4,16)).append(Character.forDigit(v&15,16));return s.toString();}
    private static IllegalArgumentException bad(){return NativeRecordCodec.bad();}
}
