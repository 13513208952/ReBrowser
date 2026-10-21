package io.github.rebrowser.outlook;

import android.os.Looper;
import io.github.rebrowser.bootstrap.ComponentGate;
import io.github.rebrowser.core.*;
import io.github.rebrowser.download.SaveTargetRegistry;
import java.io.*;
import java.util.Arrays;

/** High-risk native selected OBJECT export, <=8MiB verified RAW. Actual native scope/target/
 * owner/current principal policy must be checked by mandatory authoritative adapter EVERY step;
 * no default or caller boolean, proof parser/page account claim/job completion as authority.
 * No production adapter installed: formal current-head/target/high-risk owner admission remains
 * held. When genuinely admitted: full private AEAD/SHA/EOF verification precedes opening public
 * provider. Any public prefix/close failure is UNKNOWN, never rollback/retry/success inference.
 */
public final class MailExportCoordinator {
    public interface CurrentExportAuthority {void requireExactCurrentExport(MailDatabase.ObjectRecord nativeObject,TaskCoordinator.Owner nativeTask,SaveTargetRegistry.Target actualSelectedDestination);}
    /** Trusted existing native account-purpose key owner; returns an OWNED 32-byte copy, no key
     * generation, arbitrary aliases or raw key/args from IPC. Caller scrubs it even on failure. */
    public interface ExistingPurposeKeyProvider {byte[] openExistingNativeKeyCopy(MailDatabase.ObjectRecord exactAccountObject)throws Exception;}
    public static final class ExportObservation {
        private final byte[] object;private final long bytes;
        private ExportObservation(byte[] object,long bytes){this.object=object;this.bytes=bytes;}
        public byte[] nativeObjectId(){return object.clone();}public long bytesSentToProvider(){return bytes;}
        public String assurance(){return "VERIFIED_RAW_PRIVATE_OBJECT_AND_PROVIDER_CLOSE_RETURNED_NOT_DURABILITY_OR_NATIVE_WORK_END";}
    }
    private static final class PrivateStage extends OutputStream {
        private byte[] bytes;private int size;PrivateStage(int length){bytes=new byte[length];}
        @Override public void write(int b)throws IOException{throw new IOException("Bulk private object staging required");}
        @Override public void write(byte[] b,int off,int n)throws IOException{if(n<0 || n>bytes.length-size)throw new IOException("Native private export stage overflow");System.arraycopy(b,off,bytes,size,n);size+=n;}
        void scrub(){if(bytes!=null){Arrays.fill(bytes,(byte)0);bytes=null;}}
    }
    public static final int MAX_EXPORT_BYTES=8*1024*1024;
    private final MailDatabase database;private final MailObjectStore objects;private final SaveTargetRegistry saves;private final ComponentGate.DataEpochPin source;private final CurrentExportAuthority authority;private final ExistingPurposeKeyProvider keys;private final ResourceBudgets.ByteBudget memory=new ResourceBudgets.ByteBudget(MAX_EXPORT_BYTES,1);
    public MailExportCoordinator(MailDatabase canonicalNativeAccountDatabase,MailObjectStore canonicalNativeAccountObjects,SaveTargetRegistry exactNativeSaveOwner,ComponentGate actualGate,ComponentGate.BusinessPermit permit,CurrentExportAuthority realCurrentExportAuthority,ExistingPurposeKeyProvider actualExistingNativeKeyProvider){if(canonicalNativeAccountDatabase==null || canonicalNativeAccountObjects==null || exactNativeSaveOwner==null || actualGate==null || realCurrentExportAuthority==null || actualExistingNativeKeyProvider==null)throw new IllegalArgumentException("Real native account/export/key owners required");database=canonicalNativeAccountDatabase;objects=canonicalNativeAccountObjects;saves=exactNativeSaveOwner;source=actualGate.captureDataEpoch(permit);saves.requireBindings(actualGate,permit);authority=realCurrentExportAuthority;keys=actualExistingNativeKeyProvider;}
    public ExportObservation exportExactRawObjectToSelectedTarget(TaskCoordinator.Owner admittedNativeTask,MailDatabase.ObjectRecord exactNativeObject,SaveTargetRegistry.Target actualSelectedDestination)throws Exception{
        io();if(admittedNativeTask==null || exactNativeObject==null || actualSelectedDestination==null)throw new IllegalArgumentException("Native exact export scope required");source.requireCurrent();database.requireNativeTaskSource(admittedNativeTask);database.requireOwnedObjectRecord(exactNativeObject);authority.requireExactCurrentExport(exactNativeObject,admittedNativeTask,actualSelectedDestination);long declared=exactNativeObject.originalByteCount();if(declared>MAX_EXPORT_BYTES)throw new IOException("Native private export memory limit exceeded");final Object route=new Object();admittedNativeTask.claimCommittedNativeRoute(route);
        MailDatabase.NativeFence fence=()->{source.requireCurrent();database.requireNativeTaskSource(admittedNativeTask);database.requireOwnedObjectRecord(exactNativeObject);admittedNativeTask.requireCurrentCommittedNativeRoute(route);authority.requireExactCurrentExport(exactNativeObject,admittedNativeTask,actualSelectedDestination);source.requireCurrent();};fence.requireCurrent();database.requireCurrentScope(fence);ResourceBudgets.Allocation credit=memory.tryReserve(declared);if(credit==null)throw new IOException("Native private export stage still owned");PrivateStage stage=null;byte[] key=null;
        try{stage=new PrivateStage((int)declared);key=keys.openExistingNativeKeyCopy(exactNativeObject);if(key==null || key.length!=32)throw new IOException("Existing native account-purpose key unavailable");fence.requireCurrent();objects.readVerifiedRawIntoPrivateStage(exactNativeObject,key,stage,()->admittedNativeTask.cancellation().snapshot().cancellationRequested(),fence);if(stage.size!=declared)throw new IOException("Native authenticated object length changed");fence.requireCurrent();
            try(SaveTargetRegistry.OwnedOutput output=saves.openOnce(actualSelectedDestination,declared)){for(int at=0;at<stage.size;){fence.requireCurrent();int n=Math.min(32768,stage.size-at);output.write(stage.bytes,at,n);at+=n;}fence.requireCurrent();output.flush();}fence.requireCurrent();return new ExportObservation(exactNativeObject.nativeObjectId(),declared);
        }finally{if(key!=null)Arrays.fill(key,(byte)0);if(stage!=null)stage.scrub();credit.releaseAfterOwnedAllocationFreed();} // Actual IO close/scrub, no automatic task/grant release or alleged remote/end receipt.
    }
    private static void io(){if(Looper.myLooper()==Looper.getMainLooper())throw new IllegalStateException("Native private export bounded IO required");}
}
