package io.github.rebrowser.browser;

import io.github.rebrowser.bootstrap.ComponentGate;
import io.github.rebrowser.core.ResourceBudgets;
import io.github.rebrowser.crypto.PrivateRecordSealer;
import io.github.rebrowser.data.PrivateRecordStore;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;

/** Existing native epoch -> fixed encrypted retained browser metadata record. No key/directory
 * provisioning, automatic seed/autosave, import, session restore or cookie/permission persistence.
 * AEAD + exact disk CAS is NOT latest/anti-rollback proof. Explicit native domain authorization
 * and a coherent intended roster precede compareAndWrite; UI/admin dispatch is outside this class.
 */
public final class BrowserStore {
    private static final byte[] SCOPE={82,66,45,66,82,79,87,83,69,82,45,67,65,84,45,49}; // RB-BROWSER-CAT-1
    private final PrivateRecordStore records;private final PrivateRecordStore.Reference reference;
    private final ComponentGate gate;private final ComponentGate.DataEpochPin source;
    // Per-instance owned decoded root, NOT caller copies/process-global/provider peak memory proof.
    private final ResourceBudgets.ByteBudget decodedBudget=new ResourceBudgets.ByteBudget(16L*1024*1024,1);
    private BrowserStore(PrivateRecordStore records,ComponentGate gate,ComponentGate.DataEpochPin source,PrivateRecordStore.Reference reference){this.records=records;this.gate=gate;this.source=source;this.reference=reference;}
    public static BrowserStore openExisting(PrivateRecordStore records,ComponentGate gate,ComponentGate.BusinessPermit permit){
        if(records==null || gate==null)throw bad();ComponentGate.DataEpochPin source=gate.captureDataEpoch(permit);
        records.requireBindings(gate,source.dataEpoch(),permit);PrivateRecordStore.Reference ref=records.reference(PrivateRecordSealer.Purpose.BROWSER_CATALOG,SCOPE,permit);
        source.requireBindings(gate,permit);return new BrowserStore(records,gate,source,ref);
    }
    /** Authenticated envelope content, still NOT latest/current-memory/native-ref restore authority.
     * Close drops THIS owned metadata root/credit; external copies/references stay caller-owned. */
    public static final class ReadView implements AutoCloseable {
        private final BrowserStore issuer;private final PrivateRecordStore.ReadStamp stamp;
        private BrowserCatalogCodec.UnverifiedCatalog catalog;private ResourceBudgets.Allocation credit;
        private boolean closed,writeAttempted;
        private ReadView(BrowserStore issuer,PrivateRecordStore.ReadStamp stamp,BrowserCatalogCodec.UnverifiedCatalog catalog,ResourceBudgets.Allocation credit){this.issuer=issuer;this.stamp=stamp;this.catalog=catalog;this.credit=credit;}
        public synchronized PrivateRecordStore.Kind kind(){requireOpen();return stamp.kind();}
        /** AEAD record revision; distinct from process-local catalog metadata revision. */
        public synchronized long recordRevision(){requireOpen();return stamp.revision();}
        public synchronized BrowserCatalogCodec.UnverifiedCatalog metadata(ComponentGate.BusinessPermit permit){requireOpen();issuer.check(permit);if(catalog==null)throw new IllegalStateException("No authenticated catalog metadata");return catalog;}
        private synchronized PrivateRecordStore.ReadStamp claimWrite(BrowserStore expected){requireOpen();if(issuer!=expected || writeAttempted || stamp.kind()==PrivateRecordStore.Kind.UNFINISHED_WRITE)throw bad();writeAttempted=true;return stamp;}
        private void requireOpen(){if(closed)throw new IllegalStateException("Native catalog read view closed");}
        @Override public synchronized void close(){if(closed)return;closed=true;catalog=null;if(credit!=null){credit.releaseAfterOwnedAllocationFreed();credit=null;}}
    }
    public ReadView read(ComponentGate.BusinessPermit permit)throws IOException,GeneralSecurityException {
        check(permit);ResourceBudgets.Allocation credit=decodedBudget.tryReserve(16L*1024*1024);if(credit==null)throw new IllegalStateException("Previous decoded catalog root still owned");
        ReadView result=null;
        try(PrivateRecordStore.ReadRecord record=records.read(reference,permit)){
            PrivateRecordStore.ReadStamp stamp=record.stamp();BrowserCatalogCodec.UnverifiedCatalog catalog=null;
            if(stamp.kind()==PrivateRecordStore.Kind.CURRENT)catalog=record.readOwnedPlaintext(BrowserCatalogCodec::decodeUnverified);
            check(permit);result=new ReadView(this,stamp,catalog,credit);credit=null;return result;
        }catch(IOException|GeneralSecurityException|RuntimeException|Error failure){if(result!=null)result.close();throw failure;}
        finally{if(credit!=null)credit.releaseAfterOwnedAllocationFreed();}
    }
    /** One attempted native replacement of the exact previously read record. No alternate route,
     * weak write, retry, restore-on-missing or assumption that the captured roster was loaded from disk.
     * Caller MUST reconcile intended live roster vs original read before requesting replacement. */
    public long compareAndWrite(ReadView expected,BrowserModels.RetainedCapture capture,ComponentGate.BusinessPermit permit)throws IOException,GeneralSecurityException {
        check(permit);if(expected==null || capture==null)throw bad();capture.requireBindings(gate,permit);
        if(!Arrays.equals(capture.dataEpoch(),source.dataEpoch()))throw bad();
        PrivateRecordStore.ReadStamp stamp=expected.claimWrite(this);byte[] encoded=null;
        try{encoded=BrowserCatalogCodec.encode(capture);check(permit);capture.requireBindings(gate,permit);return records.write(stamp,encoded,permit).revision();}
        finally{if(encoded!=null)Arrays.fill(encoded,(byte)0);}
    }
    public void requireBindings(ComponentGate expected,ComponentGate.BusinessPermit permit){if(expected!=gate)throw bad();check(permit);}
    private void check(ComponentGate.BusinessPermit permit){source.requireBindings(gate,permit);records.requireBindings(gate,source.dataEpoch(),permit);}
    private static IllegalArgumentException bad(){return new IllegalArgumentException("Invalid native browser catalog owner/read/capture");}
}
