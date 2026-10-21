package io.github.rebrowser.control;

import android.os.Looper;
import io.github.rebrowser.bootstrap.ComponentGate;
import io.github.rebrowser.browser.*;
import io.github.rebrowser.settings.SettingsRepository;
import io.github.rebrowser.download.*;
import io.github.rebrowser.crypto.PrivateRecordSealer;
import io.github.rebrowser.core.*;
import io.github.rebrowser.data.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Native FIXED browser-metadata objects only. No arbitrary paths/URIs/epoch/key aliases, mail
 * accounts/credentials, raw other-owner whitelists, generic DB SQL or deferred subapp cleanup.
 * Mandatory genuine current target/level/high-risk-owner adapter has NO production installation;
 * current four ordinary control actions cannot silently become these private-data actions.
 * Inspect/export/explicit whole-record replacement are real bounded AEAD/IO/CAS operations,
 * not actor authority minted from DTO/phase/AEAD. No invented reset defaults or active-work stop.
 */
public final class PrivateDataController {
    public enum Operation { INSPECT_METADATA, EXPORT_METADATA, REPLACE_EXPLICIT_SNAPSHOT }
    public enum Family {
        BOOKMARKS(PrivateRecordSealer.Purpose.BOOKMARKS,"RB-BOOKMARKS-001"),HISTORY(PrivateRecordSealer.Purpose.HISTORY,"RB-HISTORY-00001"),SETTINGS(PrivateRecordSealer.Purpose.SETTINGS,"RB-SETTINGS-0001"),DOWNLOADS(PrivateRecordSealer.Purpose.DOWNLOADS,"RB-DOWNLOADS-001");
        final PrivateRecordSealer.Purpose purpose;final byte[] scope;Family(PrivateRecordSealer.Purpose purpose,String scope){this.purpose=purpose;this.scope=scope.getBytes(java.nio.charset.StandardCharsets.US_ASCII);}
        long revision(ByteBuffer input){switch(this){case BOOKMARKS:return BookmarkStore.CODEC.decode(input).metadataRevision();case HISTORY:return HistoryStore.CODEC.decode(input).metadataRevision();case SETTINGS:return SettingsRepository.CODEC.decode(input).metadataRevision();case DOWNLOADS:return DownloadStore.CODEC.decode(input).metadataRevision();default:throw new AssertionError();}}
        byte[] canonical(ByteBuffer input){switch(this){case BOOKMARKS:return BookmarkStore.CODEC.encode(BookmarkStore.CODEC.decode(input));case HISTORY:return HistoryStore.CODEC.encode(HistoryStore.CODEC.decode(input));case SETTINGS:return SettingsRepository.CODEC.encode(SettingsRepository.CODEC.decode(input));case DOWNLOADS:return DownloadStore.CODEC.encode(DownloadStore.CODEC.decode(input));default:throw new AssertionError();}}
    }
    /** Read-only current checks, including native owner grant/registered target and required
     * reset-owner fencing. Never consumes a ticket repeatedly or calls storage under its lock. */
    public interface CurrentPrivateDataAuthority {void requireCurrentExact(Operation operation,Family nativeFamily,TaskCoordinator.Owner actualNativeTask,Inspection originalNativeRead,SaveTargetRegistry.Target actualSelectedDestination);}
    public static final class Inspection {
        private final PrivateDataController issuer;private final Family family;private final PrivateRecordStore.ReadStamp stamp;private final long metadataRevision;private final int decodedBytes;private final AtomicBoolean replacementAttempted=new AtomicBoolean();
        private Inspection(PrivateDataController issuer,Family family,PrivateRecordStore.ReadStamp stamp,long revision,int bytes){this.issuer=issuer;this.family=family;this.stamp=stamp;metadataRevision=revision;decodedBytes=bytes;}
        public PrivateRecordStore.Kind recordKind(){issuer.source.requireCurrent();return stamp.kind();}public long recordedEnvelopeRevision(){issuer.source.requireCurrent();return stamp.revision();}public int decodedByteCount(){issuer.source.requireCurrent();return decodedBytes;}
    }
    public static final class ExportObservation {private final long bytes;private ExportObservation(long bytes){this.bytes=bytes;}public long bytesSentToSelectedProvider(){return bytes;}public String assurance(){return "PROVIDER_CLOSE_RETURNED_NOT_DURABILITY_OR_NATIVE_WORK_END";}}
    private final ComponentGate gate;private final ComponentGate.BusinessPermit permit;private final ComponentGate.DataEpochPin source;private final PrivateRecordStore records;private final SaveTargetRegistry saves;private final CurrentPrivateDataAuthority authority;private final AtomicBoolean busy=new AtomicBoolean();
    public PrivateDataController(ComponentGate actualGate,ComponentGate.BusinessPermit permit,PrivateRecordStore canonicalNativeRecords,SaveTargetRegistry actualSaveOwner,CurrentPrivateDataAuthority realNativeCurrentAuthority){if(actualGate==null || canonicalNativeRecords==null || actualSaveOwner==null || realNativeCurrentAuthority==null)throw new IllegalArgumentException("Native private-data owners/authority required");gate=actualGate;this.permit=permit;source=gate.captureDataEpoch(permit);records=canonicalNativeRecords;saves=actualSaveOwner;authority=realNativeCurrentAuthority;bindings();}
    public Inspection inspectFixedMetadata(TaskCoordinator.Owner admittedNativeTask,Family exactFamily)throws Exception{io();begin();try{Object route=claim(admittedNativeTask,Operation.INSPECT_METADATA,exactFamily,null,null);guard(admittedNativeTask,Operation.INSPECT_METADATA,exactFamily,null,null,route);try(PrivateRecordStore.ReadRecord record=records.read(reference(exactFamily),permit)){long revision=-1;int bytes=0;if(record.stamp().kind()==PrivateRecordStore.Kind.CURRENT){long[] metadata=record.readOwnedPlaintext(b->new long[]{exactFamily.revision(b),b.remaining()});revision=metadata[0];bytes=(int)metadata[1];}guard(admittedNativeTask,Operation.INSPECT_METADATA,exactFamily,null,null,route);return new Inspection(this,exactFamily,record.stamp(),revision,bytes);}}finally{busy.set(false);}}
    public ExportObservation exportOriginalMetadata(TaskCoordinator.Owner admittedNativeTask,Inspection originalNativeRead,SaveTargetRegistry.Target actualSelectedDestination)throws Exception{
        io();begin();try{own(originalNativeRead);if(actualSelectedDestination==null || originalNativeRead.stamp.kind()!=PrivateRecordStore.Kind.CURRENT)throw new SecurityException("Native current private snapshot/destination required");Family family=originalNativeRead.family;Object route=claim(admittedNativeTask,Operation.EXPORT_METADATA,family,originalNativeRead,actualSelectedDestination);records.requireCurrentStamp(originalNativeRead.stamp,permit);
            try(PrivateRecordStore.ReadRecord record=records.read(reference(family),permit)){if(record.stamp().kind()!=PrivateRecordStore.Kind.CURRENT || record.stamp().revision()!=originalNativeRead.stamp.revision())throw new IOException("Native selected private metadata changed");records.requireCurrentStamp(originalNativeRead.stamp,permit);Long count=record.readOwnedPlaintext(plain->{byte[] scratch=new byte[32768];long written=0;try{guard(admittedNativeTask,Operation.EXPORT_METADATA,family,originalNativeRead,actualSelectedDestination,route);try(SaveTargetRegistry.OwnedOutput output=saves.openOnce(actualSelectedDestination,plain.remaining())){while(plain.hasRemaining()){guard(admittedNativeTask,Operation.EXPORT_METADATA,family,originalNativeRead,actualSelectedDestination,route);int n=Math.min(scratch.length,plain.remaining());plain.get(scratch,0,n);output.write(scratch,0,n);Arrays.fill(scratch,0,n,(byte)0);written+=n;}guard(admittedNativeTask,Operation.EXPORT_METADATA,family,originalNativeRead,actualSelectedDestination,route);output.flush();}return written;}finally{Arrays.fill(scratch,(byte)0);}});records.requireCurrentStamp(originalNativeRead.stamp,permit);guard(admittedNativeTask,Operation.EXPORT_METADATA,family,originalNativeRead,actualSelectedDestination,route);return new ExportObservation(count);}
        }finally{busy.set(false);} // Public prefixes may exist on ANY failure; no automatic replay or publication claim.
    }
    /** Replaces one CURRENT fixed record with exact native complete canonical bytes and strictly
     * next logical metadata revision. Does not reset to guessed defaults, delete files/keys,
     * stop tasks/media or clear other data. Native authoritative reset owner must fence its live
     * domain writers/recipients before authorizing this; no adapter is installed in production. */
    public long replaceWithExplicitNativeSnapshot(TaskCoordinator.Owner admittedNativeTask,Inspection originalNativeRead,byte[] nativeCompleteIntendedBytes)throws Exception{
        io();begin();byte[] owned=null,canonical=null;try{own(originalNativeRead);Family family=originalNativeRead.family;if(originalNativeRead.stamp.kind()!=PrivateRecordStore.Kind.CURRENT || originalNativeRead.metadataRevision<0 || originalNativeRead.metadataRevision==Long.MAX_VALUE || nativeCompleteIntendedBytes==null || nativeCompleteIntendedBytes.length>family.purpose.maximumPlaintextBytes() || !originalNativeRead.replacementAttempted.compareAndSet(false,true))throw new SecurityException("Native exact replacement unavailable or spent");Object route=claim(admittedNativeTask,Operation.REPLACE_EXPLICIT_SNAPSHOT,family,originalNativeRead,null);owned=nativeCompleteIntendedBytes.clone();long intendedRevision=family.revision(ByteBuffer.wrap(owned));if(intendedRevision!=originalNativeRead.metadataRevision+1)throw new IllegalArgumentException("Native replacement logical revision mismatch");canonical=family.canonical(ByteBuffer.wrap(owned));if(!Arrays.equals(canonical,owned))throw new IllegalArgumentException("Noncanonical native intended snapshot");PrivateRecordStore.NativeWriteFence current=()->guard(admittedNativeTask,Operation.REPLACE_EXPLICIT_SNAPSHOT,family,originalNativeRead,null,route);current.requireCurrent();long revision=records.writeAfterNativeAuthorization(originalNativeRead.stamp,owned,permit,current).revision();current.requireCurrent();return revision;
        }finally{if(canonical!=null)Arrays.fill(canonical,(byte)0);if(owned!=null)Arrays.fill(owned,(byte)0);busy.set(false);}
    }
    private Object claim(TaskCoordinator.Owner task,Operation op,Family family,Inspection expected,SaveTargetRegistry.Target target){if(task==null || family==null)throw new IllegalArgumentException("Actual native private-data task/family required");bindings();task.requireNativeDataSource(source);authority.requireCurrentExact(op,family,task,expected,target);Object route=new Object();task.claimCommittedNativeRoute(route);return route;}
    private void guard(TaskCoordinator.Owner task,Operation op,Family family,Inspection expected,SaveTargetRegistry.Target target,Object route){bindings();task.requireNativeDataSource(source);task.requireCurrentCommittedNativeRoute(route);authority.requireCurrentExact(op,family,task,expected,target);bindings();}
    private void own(Inspection view){bindings();if(view==null || view.issuer!=this)throw new SecurityException("Foreign private-data read");}
    private PrivateRecordStore.Reference reference(Family family){return records.reference(family.purpose,family.scope,permit);}
    private void bindings(){source.requireBindings(gate,permit);records.requireBindings(gate,source.dataEpoch(),permit);saves.requireBindings(gate,permit);}
    private void begin(){if(!busy.compareAndSet(false,true))throw new IllegalStateException("Native private-data IO still owned");}
    private static void io(){if(Looper.myLooper()==Looper.getMainLooper())throw new IllegalStateException("Native private-data bounded IO required");}
}
