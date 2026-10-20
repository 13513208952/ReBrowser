package io.github.rebrowser.bootstrap;

import android.os.Handler;
import android.os.Looper;
import io.github.rebrowser.browser.BrowserCatalogCodec;
import io.github.rebrowser.browser.BrowserStore;
import io.github.rebrowser.data.PrivateRecordStore;
import java.lang.ref.WeakReference;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Single-attempt bounded native retained-catalog INSPECTION. No save/restore/repair/session.
 * One canonical instance belongs to actual StartupCoordinator. The worker reads/decrypts/parses
 * off UI, closes all owned plaintext/metadata roots, and posts only a small non-sensitive report.
 * A post attempt/timeout/window-close is not actual IO end or a successful publication receipt.
 */
public final class BrowserStoreCoordinator {
    public enum Status { AUTHENTICATED_METADATA_NOT_LATEST, MISSING_NOT_FRESH, UNFINISHED_WRITE, UNAVAILABLE }
    public interface Callback {void observed(Report report);}
    public static final class Report {
        private final Status status;private final long recordRevision;private final int workspaces,tabs;
        private Report(Status status,long revision,int workspaces,int tabs){this.status=status;recordRevision=revision;this.workspaces=workspaces;this.tabs=tabs;}
        public Status status(){return status;}public long recordRevision(){return recordRevision;}
        public int workspaceCount(){return workspaces;}public int tabCount(){return tabs;}
    }
    private final StartupCoordinator startup;private final ComponentGate gate;private final ComponentGate.DataEpochPin source;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final StartupCoordinator.PublicationGate publication=new StartupCoordinator.PublicationGate();
    private boolean started;private volatile boolean ioOwned;private volatile boolean publicationUnknown;
    BrowserStoreCoordinator(StartupCoordinator startup,ComponentGate.BusinessPermit permit){
        owner();if(startup==null)throw new IllegalArgumentException("Actual startup required");this.startup=startup;gate=startup.gate();source=gate.captureDataEpoch(permit);
    }
    public void inspectOnce(Callback callback,ComponentGate.BusinessPermit permit){
        owner();if(callback==null)throw new IllegalArgumentException("Native callback required");source.requireBindings(gate,permit);
        if(started)throw new IllegalStateException("Catalog inspection already attempted");started=true;
        StartupCoordinator.PublicationGate.Ticket ticket=publication.begin();WeakReference<Callback> recipient=new WeakReference<>(callback);ThreadPoolExecutor io;
        try{io=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(1),r->{Thread t=new Thread(r,"ReBrowser-catalog-inspection");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());}
        catch(RuntimeException knownUnsubmitted){publication.finish(ticket);publicationUnknown=true;throw knownUnsubmitted;}
        ioOwned=true;
        try{io.execute(()->{
            Report result=new Report(Status.UNAVAILABLE,0,0,0);
            try{
                source.requireBindings(gate,permit);BrowserStore store=startup.openExistingBrowserStoreOnIo(permit);
                try(BrowserStore.ReadView read=store.read(permit)){
                    PrivateRecordStore.Kind kind=read.kind();
                    if(kind==PrivateRecordStore.Kind.CURRENT){BrowserCatalogCodec.UnverifiedCatalog parsed=read.metadata(permit);result=new Report(Status.AUTHENTICATED_METADATA_NOT_LATEST,read.recordRevision(),parsed.workspaces().size(),parsed.tabCount());}
                    else result=new Report(kind==PrivateRecordStore.Kind.MISSING?Status.MISSING_NOT_FRESH:Status.UNFINISHED_WRITE,0,0,0);
                }
                source.requireBindings(gate,permit);
            }catch(Exception unavailable){result=new Report(Status.UNAVAILABLE,0,0,0);}
            catch(Error actualFailure){publicationUnknown=true;publication.rejectSubmission(ticket);throw actualFailure;}
            finally{ioOwned=false;} // owned read/close returned, NOT whole executor or Handler publication end
            final Report finished=result;boolean confirmed=false;
            try{if(main.post(()->deliver(ticket,recipient,finished,permit)))confirmed=publication.confirmSubmission(ticket);}
            catch(RuntimeException unknownSubmission){/* do not retry/alternate route */}
            finally{if(!confirmed){publication.rejectSubmission(ticket);publicationUnknown=true;}}
        });}catch(java.util.concurrent.RejectedExecutionException knownUnsubmitted){ioOwned=false;publicationUnknown=true;publication.rejectSubmission(ticket);throw knownUnsubmitted;}
        catch(RuntimeException unknownSubmission){publicationUnknown=true;publication.rejectSubmission(ticket);throw unknownSubmission;}
        catch(Error unknownSubmission){publicationUnknown=true;publication.rejectSubmission(ticket);throw unknownSubmission;}
        finally{io.shutdown();} // no interruption/Future.cancel/forced termination as an end receipt
    }
    private void deliver(StartupCoordinator.PublicationGate.Ticket ticket,WeakReference<Callback> recipient,Report report,ComponentGate.BusinessPermit permit){
        owner();if(!publication.finish(ticket)){publicationUnknown=true;return;}
        try{source.requireBindings(gate,permit);}catch(RuntimeException stale){return;}
        Callback callback=recipient.get();if(callback!=null)try{callback.observed(report);}catch(RuntimeException unknownUi){/* no private values/error text logged */}
    }
    public void retireRecipient(){owner();publication.revoke();} // does NOT cancel IO or free its credit
    public boolean hasOwnedReadIo(){return ioOwned;}
    public boolean publicationOutcomeUnknown(){return publicationUnknown;}
    private static void owner(){if(Looper.myLooper()!=Looper.getMainLooper())throw new IllegalStateException("Catalog presentation owner thread required");}
}
