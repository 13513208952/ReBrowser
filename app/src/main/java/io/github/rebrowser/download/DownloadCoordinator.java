package io.github.rebrowser.download;

import android.app.*;
import android.os.Looper;
import io.github.rebrowser.bootstrap.ComponentGate;
import io.github.rebrowser.core.*;
import io.github.rebrowser.engine.GeckoSessionOwner;
import io.github.rebrowser.ui.BrowserToolbar;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.mozilla.geckoview.*;

/** Native approved ACTUAL WebResponse body -> bounded PRIVATE memory stage -> exact catalog
 * CAS -> one actual selected provider writer. No URL fetch/refetch, arbitrary paths, automatic
 * save/open/install, checkpoint/cookie access, progress-as-success or native-work-end inference.
 * Deliberately <=8MiB known-length transfers until a qualified private disk-stage owner exists.
 * Actual SDK/selection adapters are NOT installed: native producer must retain body ownership
 * until approval/rejected-close, and task current context must bind the ORIGINAL window/target.
 * Provider may publish partial bytes on any failure; close return != durable publication.
 */
public final class DownloadCoordinator {
    public static final int MAX_MEMORY_TRANSFER_BYTES=8*1024*1024;
    public static final class ApprovedTransfer {
        private final DownloadCoordinator issuer;private final TaskCoordinator.Submission task;private final InputStream body;private final String url,name,mime;private final int expected;private final AtomicBoolean attempted=new AtomicBoolean();private volatile boolean retired;
        private ApprovedTransfer(DownloadCoordinator issuer,TaskCoordinator.Submission task,InputStream body,String url,String name,String mime,int expected){this.issuer=issuer;this.task=task;this.body=body;this.url=url;this.name=name;this.mime=mime;this.expected=expected;}
        public void retirePublication(){retired=true;} // Must be called by native original-window/document lifecycle; no fake IO end.
    }
    public static final class TransferObservation {
        private final ResponseStreamWorker.CopiedStage copy;private final SaveTargetRegistry.NativeSavedRead read;private final String id;
        private TransferObservation(ResponseStreamWorker.CopiedStage copy,SaveTargetRegistry.NativeSavedRead read,String id){this.copy=copy;this.read=read;this.id=id;}
        public long actualCopiedBytes(){return copy.byteCount();}public String metadataRowId(){return id;}
        /** User must explicitly choose OPEN; this ticket cannot auto-install or restore after death. */
        public SaveTargetRegistry.NativeSavedRead nativeExplicitOpenTicket(){return read;}
        public String assurance(){return "EXACT_PRIVATE_COPY_AND_PROVIDER_CLOSE_RETURNED_NOT_DURABLE_PUBLICATION_OR_WORK_END";}
    }
    private static final class PrivateStage extends OutputStream {
        private byte[] bytes;private int used;private boolean closed;
        PrivateStage(int length){bytes=new byte[length];}
        @Override public void write(int b)throws IOException{throw new IOException("Bulk native response copy required");}
        @Override public void write(byte[] b,int offset,int n)throws IOException{if(closed || n<0 || n>bytes.length-used)throw new IOException("Private native download stage full/closed");System.arraycopy(b,offset,bytes,used,n);used+=n;}
        @Override public void close(){closed=true;} // This PRIVATE stage stays owned until exact final scrub, not this logical writer close.
        void scrub(){if(bytes!=null){Arrays.fill(bytes,(byte)0);bytes=null;}}
    }
    private final Application application;private final ComponentGate gate;private final ComponentGate.BusinessPermit permit;private final ComponentGate.DataEpochPin source;private final DownloadStore catalog;private final SaveTargetRegistry saves;private final ResourceBudgets.ByteBudget retained=new ResourceBudgets.ByteBudget(MAX_MEMORY_TRANSFER_BYTES,1);private final IdentityHashMap<WebResponse,ApprovedTransfer> originals=new IdentityHashMap<>();private final Set<InputStream> originalBodies=Collections.newSetFromMap(new IdentityHashMap<InputStream,Boolean>());
    public DownloadCoordinator(Application actualApplication,ComponentGate gate,ComponentGate.BusinessPermit permit,DownloadStore canonicalCatalog,SaveTargetRegistry canonicalSaveTargets){if(actualApplication==null || gate==null || canonicalCatalog==null || canonicalSaveTargets==null)throw new IllegalArgumentException("Native download owners required");application=actualApplication;this.gate=gate;this.permit=permit;source=gate.captureDataEpoch(permit);catalog=canonicalCatalog;saves=canonicalSaveTargets;checkOwners();}
    /** Native UI only, actual private session owner + native original frame, not page metadata.
     * This claims already durably committed task route BEFORE handing it to the native IO lane. */
    public ApprovedTransfer approveActualResponseFromNativeWindow(Activity actualOwnedWindow,BrowserToolbar.Binding originalFrame,GeckoSessionOwner actualSessions,GeckoSessionOwner.SessionRef exactSession,GeckoSession actualSdkSession,WebResponse actualResponse,TaskCoordinator.Submission nativeTask,String nativeDisplayName,String nativeMime,long nativeDeclaredBytes){
        if(Looper.myLooper()!=Looper.getMainLooper())throw new IllegalStateException("Native download consent window required");checkOwners();if(actualOwnedWindow==null || actualOwnedWindow.getApplication()!=application || actualOwnedWindow.isFinishing() || actualOwnedWindow.isDestroyed() || !actualOwnedWindow.hasWindowFocus() || originalFrame==null || actualSessions==null || actualResponse==null || actualResponse.body==null || nativeTask==null || nativeDeclaredBytes<0 || nativeDeclaredBytes>MAX_MEMORY_TRANSFER_BYTES || originals.size()>=16 || originals.containsKey(actualResponse) || originalBodies.contains(actualResponse.body))throw new SecurityException("Native response/consent unavailable");originalFrame.check();if(actualSessions.requireOpen(exactSession,permit)!=actualSdkSession)throw new SecurityException("Wrong native response session");nativeTask.nativeOwner().requireNativeDataSource(source);
        // Validates display metadata WITHOUT treating the URI as permission or fetching it again.
        DownloadStore.validateNativeDisplayMetadata(nativeDisplayName,nativeMime,actualResponse.uri);
        ApprovedTransfer approved=new ApprovedTransfer(this,nativeTask,actualResponse.body,actualResponse.uri,nativeDisplayName,nativeMime,(int)nativeDeclaredBytes);nativeTask.nativeOwner().claimCommittedNativeRoute(approved);originals.put(actualResponse,approved);originalBodies.add(actualResponse.body);return approved;
    }
    /** Caller supplies real original selected Target. Takes/closes original body ONCE, including
     * source/cancel/read/EOF/write/close failures. It never settles the native task automatically. */
    public TransferObservation copyAndSaveOnOwnedIo(ApprovedTransfer approved,SaveTargetRegistry.Target exactSelectedTarget)throws Exception{
        io();own(approved);if(!approved.attempted.compareAndSet(false,true))throw new SecurityException("Native response transfer already attempted");ResourceBudgets.Allocation credit=null;PrivateStage stage=null;
        try(InputStream body=new FilterInputStream(approved.body){private boolean closed;@Override public void close()throws IOException{if(closed)return;closed=true;super.close();}}){check(approved);credit=retained.tryReserve(approved.expected);if(credit==null)throw new IOException("Native download private stage budget owned");stage=new PrivateStage(approved.expected);DownloadStore.Entry row=catalog.nativeRegisteredMetadata(approved.task,approved.name,approved.mime,approved.url,permit);
            try(DownloadStore.ReadView view=catalog.read(permit)){DownloadStore.Snapshot current=view.unverifiedMetadata(permit);catalog.compareAndWriteCompleteIntendedMetadata(view,current.upsertNativeObservation(row),permit);} // Missing/unfinished never auto-seeded.
            ResponseStreamWorker.CopiedStage copied=ResponseStreamWorker.copyOwned(body,stage,approved.expected,approved.expected,approved.task.nativeOwner().cancellation());check(approved);DownloadStore.Entry observed=catalog.observeNativePrivateCopyReturn(row,approved.task,copied,permit);
            try(DownloadStore.ReadView view=catalog.read(permit)){catalog.compareAndWriteCompleteIntendedMetadata(view,view.unverifiedMetadata(permit).upsertNativeObservation(observed),permit);}check(approved);
            // Public provider is NOT passed to private-stage copyOwned. Every external write is
            // an independent possible partial publication, not reversible by deleting a temp file.
            try(SaveTargetRegistry.OwnedOutput out=saves.openOnce(exactSelectedTarget,approved.expected)){for(int at=0;at<approved.expected;){check(approved);int n=Math.min(ResponseStreamWorker.BUFFER_BYTES,approved.expected-at);out.write(stage.bytes,at,n);at+=n;}check(approved);out.flush();}check(approved);SaveTargetRegistry.NativeSavedRead read=saves.nativeReadAfterExactCopyClose(exactSelectedTarget,copied);return new TransferObservation(copied,read,row.id());
        }finally{if(stage!=null)stage.scrub();if(credit!=null)credit.releaseAfterOwnedAllocationFreed();}
    }
    /** Actual native safety IO for approved but unconsumed bodies, allowed after source retirement. */
    public void closeRejectedOriginalOnIo(ApprovedTransfer approved)throws IOException{io();own(approved);approved.retired=true;if(!approved.attempted.compareAndSet(false,true))throw new SecurityException("Original body already handed to native IO");ResponseStreamWorker.discardOwned(approved.body);}
    private void check(ApprovedTransfer approved){own(approved);if(approved.retired)throw new SecurityException("Native original download scope retired");checkOwners();approved.task.nativeOwner().requireNativeDataSource(source);approved.task.nativeOwner().requireCurrentCommittedNativeRoute(approved);if(approved.task.nativeOwner().cancellation().snapshot().cancellationRequested())throw new SecurityException("Native download cancellation requested");}
    private void own(ApprovedTransfer approved){if(approved==null || approved.issuer!=this)throw new SecurityException("Foreign native response transfer");}
    private void checkOwners(){source.requireBindings(gate,permit);catalog.requireBindings(gate,permit);saves.requireBindings(gate,permit);}
    private static void io(){if(Looper.myLooper()==Looper.getMainLooper())throw new IllegalStateException("Native download bounded IO required");}
}
