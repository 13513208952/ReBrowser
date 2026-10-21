package io.github.rebrowser.download;

import io.github.rebrowser.bootstrap.ComponentGate;
import io.github.rebrowser.core.ResourceBudgets;
import io.github.rebrowser.core.TaskCoordinator;
import io.github.rebrowser.crypto.PrivateRecordSealer;
import io.github.rebrowser.data.*;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.security.*;
import java.util.*;

/** Fixed p6 encrypted native download-METADATA catalog. Actual registered task/owned copy
 * observations can produce new rows; decoded metadata never restores a stream/save grant/file/
 * work-end/publication receipt. No URI permission serialization, auto defaults/retry or startup
 * IO. Explicit authorized intended WHOLE snapshot + exact native read-stamp CAS before writes;
 * missing is not fresh/empty, AEAD is not latest. Caller owns domain authorization/reconciliation.
 */
public final class DownloadStore {
    public enum CopyObservation { QUEUED_METADATA(1), PRIVATE_COPY_CALL_RETURNED(2), OUTCOME_UNKNOWN(3);final int code;CopyObservation(int code){this.code=code;}static CopyObservation decode(int code){for(CopyObservation v:values())if(v.code==code)return v;throw bad();}}
    public static final int MAX_ROWS=256,MAX_BYTES=4*1024*1024;
    private static final byte[] SCOPE="RB-DOWNLOADS-001".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    public static final class Entry {
        private final String id,job,name,mime,url,digest;private final long copiedBytes;private final CopyObservation observation;
        private Entry(String id,String job,String name,String mime,String url,CopyObservation observation,long bytes,String digest){if(id==null || !id.matches("dl_[0-9a-f]{32}") || observation==null || bytes<0 || bytes>ResponseStreamWorker.MAX_COPY_BYTES || digest==null || !digest.isEmpty() && !digest.matches("[0-9a-f]{64}") || observation==CopyObservation.PRIVATE_COPY_CALL_RETURNED && digest.isEmpty())throw bad();this.id=id;this.job=ascii(job,128);this.name=name(name);this.mime=mime(mime);this.url=url(url);this.observation=observation;copiedBytes=bytes;this.digest=digest;}
        public String id(){return id;}public String nativeJobRefMetadata(){return job;}public String displayName(){return name;}public String mimeType(){return mime;}public String sourceUrlMetadata(){return url;}public CopyObservation recordedCopyObservation(){return observation;}public long recordedCopiedBytes(){return copiedBytes;}public String recordedOriginalSha256(){return digest;}
    }
    public static final class Snapshot {
        private final long revision;private final List<Entry> entries;
        /** Metadata only. Empty supplied intentionally is NOT evidence of a fresh install. */
        public Snapshot(long nativeMetadataRevision,List<Entry> completeIntendedRows){if(nativeMetadataRevision<0 || completeIntendedRows==null || completeIntendedRows.size()>MAX_ROWS)throw bad();TreeMap<String,Entry> sorted=new TreeMap<>();Set<String> jobs=new HashSet<>();for(Entry e:completeIntendedRows)if(e==null || sorted.put(e.id,e)!=null || !jobs.add(e.job))throw bad();revision=nativeMetadataRevision;entries=Collections.unmodifiableList(new ArrayList<>(sorted.values()));}
        public long metadataRevision(){return revision;}public List<Entry> entries(){return entries;}
        public Snapshot upsertNativeObservation(Entry actualNativeObservation){if(actualNativeObservation==null || revision==Long.MAX_VALUE)throw bad();List<Entry> rows=new ArrayList<>(entries);boolean replaced=false;for(int i=0;i<rows.size();i++)if(rows.get(i).id.equals(actualNativeObservation.id)){rows.set(i,actualNativeObservation);replaced=true;break;}if(!replaced)rows.add(actualNativeObservation);return new Snapshot(revision+1,rows);}
    }
    public static final class ReadView implements AutoCloseable {
        private final DownloadStore issuer;private final PrivateRecordStore.ReadStamp stamp;private Snapshot snapshot;private ResourceBudgets.Allocation credit;private boolean closed,writeAttempted;
        private ReadView(DownloadStore issuer,PrivateRecordStore.ReadStamp stamp,Snapshot snapshot,ResourceBudgets.Allocation credit){this.issuer=issuer;this.stamp=stamp;this.snapshot=snapshot;this.credit=credit;}
        public synchronized PrivateRecordStore.Kind kind(){open();return stamp.kind();}
        public synchronized Snapshot unverifiedMetadata(ComponentGate.BusinessPermit permit){open();issuer.check(permit);if(snapshot==null)throw new IllegalStateException("No authenticated existing download catalog");return snapshot;}
        private synchronized PrivateRecordStore.ReadStamp claim(DownloadStore expected,Snapshot intended){open();if(issuer!=expected || writeAttempted || stamp.kind()==PrivateRecordStore.Kind.UNFINISHED_WRITE)throw bad();writeAttempted=true;if(snapshot!=null && (snapshot.revision==Long.MAX_VALUE || intended.revision!=snapshot.revision+1))throw bad();return stamp;}
        private void open(){if(closed)throw new IllegalStateException("Native download metadata read closed");}
        @Override public synchronized void close(){if(closed)return;closed=true;snapshot=null;if(credit!=null){credit.releaseAfterOwnedAllocationFreed();credit=null;}} // External retained metadata copies remain caller-owned.
    }
    public static final NativeRecordCodec.Codec<Snapshot> CODEC=new NativeRecordCodec.Codec<Snapshot>(){
        public byte[] encode(Snapshot value){if(value==null)throw bad();NativeRecordCodec.Writer w=new NativeRecordCodec.Writer("RBDLST01",MAX_BYTES);w.nonnegative(value.revision).u16(value.entries.size());for(Entry e:value.entries)w.text(e.id,35,false).text(e.job,128,false).text(e.name,512,false).text(e.mime,128,false).text(e.url,8192,false).u8(e.observation.code).nonnegative(e.copiedBytes).text(e.digest,64,true);return w.finish();}
        public Snapshot decode(ByteBuffer bytes){NativeRecordCodec.Reader r=new NativeRecordCodec.Reader(bytes,"RBDLST01",MAX_BYTES);long revision=r.nonnegative();int n=r.u16();if(n>MAX_ROWS)throw bad();List<Entry> rows=new ArrayList<>();String previous=null;for(int i=0;i<n;i++){Entry e=new Entry(r.text(35,false),r.text(128,false),r.text(512,false),r.text(128,false),r.text(8192,false),CopyObservation.decode(r.u8()),r.nonnegative(),r.text(64,true));if(previous!=null && previous.compareTo(e.id)>=0)throw bad();previous=e.id;rows.add(e);}r.end();return new Snapshot(revision,rows);}
    };
    private final PrivateRecordStore records;private final PrivateRecordStore.Reference reference;private final ComponentGate gate;private final ComponentGate.DataEpochPin source;private final ResourceBudgets.ByteBudget held=new ResourceBudgets.ByteBudget(16L*1024*1024,1);
    private DownloadStore(PrivateRecordStore records,PrivateRecordStore.Reference ref,ComponentGate gate,ComponentGate.DataEpochPin source){this.records=records;reference=ref;this.gate=gate;this.source=source;}
    public static DownloadStore openExisting(PrivateRecordStore records,ComponentGate gate,ComponentGate.BusinessPermit permit){if(records==null || gate==null)throw bad();ComponentGate.DataEpochPin source=gate.captureDataEpoch(permit);records.requireBindings(gate,source.dataEpoch(),permit);return new DownloadStore(records,records.reference(PrivateRecordSealer.Purpose.DOWNLOADS,SCOPE,permit),gate,source);}
    public ReadView read(ComponentGate.BusinessPermit permit)throws IOException,GeneralSecurityException{check(permit);ResourceBudgets.Allocation credit=held.tryReserve(16L*1024*1024);if(credit==null)throw new IllegalStateException("Prior decoded download catalog still owned");try(PrivateRecordStore.ReadRecord record=records.read(reference,permit)){PrivateRecordStore.ReadStamp stamp=record.stamp();Snapshot metadata=stamp.kind()==PrivateRecordStore.Kind.CURRENT?record.readOwnedPlaintext(CODEC::decode):null;check(permit);ReadView result=new ReadView(this,stamp,metadata,credit);credit=null;return result;}finally{if(credit!=null)credit.releaseAfterOwnedAllocationFreed();}}
    public long compareAndWriteCompleteIntendedMetadata(ReadView expected,Snapshot intended,ComponentGate.BusinessPermit permit)throws IOException,GeneralSecurityException{check(permit);if(expected==null || intended==null)throw bad();PrivateRecordStore.ReadStamp stamp=expected.claim(this,intended);byte[] bytes=CODEC.encode(intended);try{check(permit);return records.write(stamp,bytes,permit).revision();}finally{Arrays.fill(bytes,(byte)0);}}
    public Entry nativeRegisteredMetadata(TaskCoordinator.Submission actualNativeTask,String nativeDisplayName,String nativeMime,String actualResponseSourceUrl,ComponentGate.BusinessPermit permit){check(permit);if(actualNativeTask==null || !actualNativeTask.nativeOwner().mayPublishCurrentResult())throw bad();actualNativeTask.nativeOwner().requireNativeDataSource(source);byte[] id=new byte[16];new SecureRandom().nextBytes(id);return new Entry("dl_"+hex(id),actualNativeTask.client().jobRef(),nativeDisplayName,nativeMime,actualResponseSourceUrl,CopyObservation.QUEUED_METADATA,0,"");}
    public Entry observeNativePrivateCopyReturn(Entry exactOriginal,TaskCoordinator.Submission exactNativeTask,ResponseStreamWorker.CopiedStage actualCopiedStage,ComponentGate.BusinessPermit permit){check(permit);if(exactOriginal==null || exactNativeTask==null || actualCopiedStage==null || !exactOriginal.job.equals(exactNativeTask.client().jobRef()) || !exactNativeTask.nativeOwner().mayPublishCurrentResult())throw bad();exactNativeTask.nativeOwner().requireNativeDataSource(source);return new Entry(exactOriginal.id,exactOriginal.job,exactOriginal.name,exactOriginal.mime,exactOriginal.url,CopyObservation.PRIVATE_COPY_CALL_RETURNED,actualCopiedStage.byteCount(),hex(actualCopiedStage.originalSha256()));}
    public void requireBindings(ComponentGate expected,ComponentGate.BusinessPermit permit){if(expected!=gate)throw bad();check(permit);}
    private void check(ComponentGate.BusinessPermit permit){source.requireBindings(gate,permit);records.requireBindings(gate,source.dataEpoch(),permit);}
    public static void validateNativeDisplayMetadata(String name,String mime,String sourceUrl){name(name);mime(mime);url(sourceUrl);} // Shape only, never download/source authority.
    private static String name(String s){text(s,512,false);if(s.indexOf('/')>=0 || s.indexOf('\\')>=0 || s.equals(".") || s.equals(".."))throw bad();return s;}
    private static String mime(String s){ascii(s,128);if(!s.matches("[a-z0-9!#$&^_.+~-]+/[a-z0-9!#$&^_.+~-]+"))throw bad();return s;}
    private static String text(String s,int bytes,boolean empty){return NativeRecordCodec.validateText(s,bytes,empty);}private static String ascii(String s,int bytes){text(s,bytes,false);for(int i=0;i<s.length();i++)if(s.charAt(i)<33 || s.charAt(i)>126)throw bad();return s;}
    private static String url(String s){text(s,8192,false);try{URI uri=new URI(s);if((!"https".equals(uri.getScheme()) && !"http".equals(uri.getScheme())) || uri.getHost()==null || uri.getRawUserInfo()!=null)throw bad();return s;}catch(java.net.URISyntaxException invalid){throw bad();}}
    private static String hex(byte[] b){StringBuilder s=new StringBuilder();for(byte v:b)s.append(Character.forDigit((v&255)>>>4,16)).append(Character.forDigit(v&15,16));return s.toString();}
    private static IllegalArgumentException bad(){return new IllegalArgumentException("Invalid bounded native download metadata");}
}
