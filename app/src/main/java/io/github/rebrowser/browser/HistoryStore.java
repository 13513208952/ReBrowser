package io.github.rebrowser.browser;

import io.github.rebrowser.bootstrap.ComponentGate;
import io.github.rebrowser.data.NativeRecordCodec;
import io.github.rebrowser.data.OwnedDataCatalog;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.*;

/** Bounded explicit native history metadata persistence. Native producer must exclude actual
 * private/temporary sessions and verify a real visit BEFORE supplying data. A URL/DTO/time below
 * proves neither a committed Gecko document nor an authorized user action. This store does NOT
 * subscribe to Gecko, automatically write/expire, report visited CSS or infer "loaded". */
public final class HistoryStore {
    public static final int MAX_ENTRIES=5000,MAX_BYTES=4*1024*1024;
    public static final class Entry {
        private final String id,title,url;private final long visitedUtc;private final boolean retained;
        public Entry(String id,String title,String url,long visitedUtc,boolean retained){if(id==null || !id.matches("hv_[0-9a-f]{32}") || visitedUtc<0)throw bad();this.id=id;this.title=NativeRecordCodec.validateText(title,640,true);NativeRecordCodec.validateText(url,8192,false);this.url=SearchEngines.requireWebUrl(url).url();NativeRecordCodec.validateText(this.url,8192,false);this.visitedUtc=visitedUtc;this.retained=retained;}
        public String id(){return id;}public String title(){return title;}public String url(){return url;}public long visitedUtcMs(){return visitedUtc;}public boolean retained(){return retained;}
    }
    private static final Comparator<Entry> ORDER=new Comparator<Entry>(){public int compare(Entry a,Entry b){int time=Long.compare(b.visitedUtc,a.visitedUtc);return time!=0?time:a.id.compareTo(b.id);}};
    public static final class Snapshot {
        private final long revision;private final boolean paused;private final List<Entry> entries;
        public Snapshot(long metadataRevision,boolean pauseWrites,List<Entry> input){if(metadataRevision<0 || input==null || input.size()>MAX_ENTRIES)throw bad();revision=metadataRevision;paused=pauseWrites;List<Entry> copy=new ArrayList<>(input);Set<String> ids=new HashSet<>();long bytes=21;for(Entry e:copy){if(e==null || !ids.add(e.id))throw bad();bytes+=37+8+1+2+utfBytes(e.title)+2+utfBytes(e.url);if(bytes>MAX_BYTES)throw bad();}Collections.sort(copy,ORDER);entries=Collections.unmodifiableList(copy);}
        public long metadataRevision(){return revision;}public boolean pauseWrites(){return paused;}public List<Entry> entries(){return entries;}
        public Snapshot withPaused(boolean value){return new Snapshot(next(revision),value,entries);}
        /** Explicit native observation data only; no document/actor/session receipt minted. */
        public Snapshot addObservedVisit(String title,String url,long nativeUtc){if(paused)throw new IllegalStateException("Native history writes paused");if(entries.size()>=MAX_ENTRIES)throw bad();String id=null;Set<String> ids=new HashSet<>();for(Entry e:entries)ids.add(e.id);for(int attempt=0;attempt<16;attempt++){String candidate=randomId();if(!ids.contains(candidate)){id=candidate;break;}}if(id==null)throw bad();List<Entry> copy=new ArrayList<>(entries);copy.add(new Entry(id,title,url,nativeUtc,false));return new Snapshot(next(revision),paused,copy);}
        public Snapshot retain(String id,boolean retain){boolean found=false;List<Entry> copy=new ArrayList<>();for(Entry e:entries){if(e.id.equals(id)){copy.add(new Entry(e.id,e.title,e.url,e.visitedUtc,retain));found=true;}else copy.add(e);}if(!found)throw bad();return new Snapshot(next(revision),paused,copy);}
        public Snapshot remove(String id){boolean found=false;List<Entry> copy=new ArrayList<>();for(Entry e:entries)if(e.id.equals(id))found=true;else copy.add(e);if(!found)throw bad();return new Snapshot(next(revision),paused,copy);}
        /** Called only for an explicit reconciled native retention policy; pinned entries survive. */
        public Snapshot pruneBefore(long nativeCutoffUtc){if(nativeCutoffUtc<0)throw bad();List<Entry> copy=new ArrayList<>();for(Entry e:entries)if(e.retained || e.visitedUtc>=nativeCutoffUtc)copy.add(e);return new Snapshot(next(revision),paused,copy);}
        public List<Entry> query(String literal,int offset,int limit){NativeRecordCodec.validateText(literal,256,true);if(offset<0 || offset>MAX_ENTRIES || limit<1 || limit>100)throw bad();List<Entry> page=new ArrayList<>();int hits=0;for(Entry e:entries)if(e.title.contains(literal) || e.url.contains(literal)){if(hits++>=offset && page.size()<limit)page.add(e);}return Collections.unmodifiableList(page);}
    }
    public static final NativeRecordCodec.Codec<Snapshot> CODEC=new NativeRecordCodec.Codec<Snapshot>(){
        public Snapshot decode(ByteBuffer input){NativeRecordCodec.Reader r=new NativeRecordCodec.Reader(input,"RBHIST01",MAX_BYTES);long revision=r.nonnegative();boolean paused=r.bool();int count=r.u16();if(count>MAX_ENTRIES)throw bad();List<Entry> entries=new ArrayList<>();Entry previous=null;for(int i=0;i<count;i++){String id=r.text(35,false);long time=r.nonnegative();boolean retained=r.bool();String title=r.text(640,true),url=r.text(8192,false);Entry e=new Entry(id,title,url,time,retained);if(!e.url.equals(url) || previous!=null && ORDER.compare(previous,e)>=0)throw bad();entries.add(e);previous=e;}r.end();return new Snapshot(revision,paused,entries);}
        public byte[] encode(Snapshot s){if(s==null)throw bad();NativeRecordCodec.Writer w=new NativeRecordCodec.Writer("RBHIST01",MAX_BYTES).nonnegative(s.revision).bool(s.paused).u16(s.entries.size());for(Entry e:s.entries)w.text(e.id,35,false).nonnegative(e.visitedUtc).bool(e.retained).text(e.title,640,true).text(e.url,8192,false);return w.finish();}
    };
    private final OwnedDataCatalog records;public HistoryStore(OwnedDataCatalog records){if(records==null)throw bad();this.records=records;}
    public OwnedDataCatalog.View<Snapshot> read(ComponentGate.BusinessPermit permit)throws IOException,GeneralSecurityException{return records.read(OwnedDataCatalog.Family.HISTORY,CODEC,permit);}
    public long compareAndWrite(OwnedDataCatalog.View<Snapshot> expected,Snapshot intended,ComponentGate.BusinessPermit permit)throws IOException,GeneralSecurityException{records.requireFamily(expected,OwnedDataCatalog.Family.HISTORY,CODEC);if(intended==null || expected.kind()==io.github.rebrowser.data.PrivateRecordStore.Kind.CURRENT && intended.revision<=expected.value(permit).revision)throw bad();return records.compareAndWrite(expected,intended,permit);}
    private static int utfBytes(String s){return s.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;}
    private static long next(long r){if(r==Long.MAX_VALUE)throw bad();return r+1;}
    private static String randomId(){byte[] b=new byte[16];new SecureRandom().nextBytes(b);StringBuilder s=new StringBuilder("hv_");for(byte v:b)s.append(Character.forDigit((v&255)>>>4,16)).append(Character.forDigit(v&15,16));return s.toString();}
    private static IllegalArgumentException bad(){return NativeRecordCodec.bad();}
}
