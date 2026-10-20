package io.github.rebrowser.bootstrap;

import android.app.Application;
import android.os.Looper;
import android.os.Handler;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import io.github.rebrowser.continuity.LastGoodSourceStore;
import io.github.rebrowser.continuity.SourceBootstrap;
import io.github.rebrowser.authority.AdminProofVerifier;
import io.github.rebrowser.authority.CompiledPrincipals;
import io.github.rebrowser.engine.GeckoRuntimeOwner;
import io.github.rebrowser.browser.BrowserCoordinator;
import io.github.rebrowser.data.PrivateRecordStore;
import java.util.concurrent.atomic.AtomicInteger;
import io.github.rebrowser.continuity.CompiledSourceIdentity;
import io.github.rebrowser.continuity.SigningIdentity;

/** Native shell -> continuity gate -> on-demand Gecko. No DB/profile restore at Application startup. */
public final class StartupCoordinator {
    private static StartupCoordinator primary;private static Application ownerApplication;private static boolean ownerCreationAttempted;
    /** One actual process owner; reentry/construction failure never creates an alternate owner. */
    public static synchronized StartupCoordinator forMainApplication(Application application){
        if(Looper.myLooper()!=Looper.getMainLooper())throw new IllegalStateException("Primary startup owner thread required");
        if(application==null || ProcessRole.classifyOwnApplication(application.getPackageName(),Application.getProcessName()).startupRoute()!=ProcessRole.StartupRoute.MAIN_NATIVE_OWNER)throw new IllegalStateException("Actual primary application required");
        if(ownerApplication!=null && ownerApplication!=application)throw new IllegalStateException("Primary application owner mismatch");
        if(primary!=null)return primary;if(ownerCreationAttempted)throw new IllegalStateException("Primary startup already attempted");
        ownerApplication=application;ownerCreationAttempted=true;primary=new StartupCoordinator(application,ProcessRole.MAIN);return primary;
    }
    /** Owner-thread handoff model only, not source acceptance/actual IO-end evidence. */
    public static final class PublicationGate {
        public static final class Ticket {private final PublicationGate issuer;private boolean ended;private final AtomicInteger submission=new AtomicInteger();private Ticket(PublicationGate issuer){this.issuer=issuer;}}
        private final Thread thread=Thread.currentThread();private Ticket pending;private boolean started,revoked;
        public Ticket begin(){check();if(started || revoked)throw new IllegalStateException("Native source inspection already attempted");started=true;pending=new Ticket(this);return pending;}
        public boolean finish(Ticket ticket){check();if(ticket==null || ticket.issuer!=this)throw new SecurityException("Foreign source publication ticket");if(ticket.ended)return false;if(pending!=ticket)throw new SecurityException("Stale source publication ticket");ticket.ended=true;pending=null;int submission=ticket.submission.getAndSet(3);return !revoked && submission==1;}
        /** Native IO records positive Handler.post RETURN, not merely an attempted submission. */
        public boolean confirmSubmission(Ticket ticket){own(ticket);return ticket.submission.compareAndSet(0,1);}
        public void rejectSubmission(Ticket ticket){own(ticket);ticket.submission.compareAndSet(0,2);ticket.submission.compareAndSet(1,2);}
        private void own(Ticket ticket){if(ticket==null || ticket.issuer!=this)throw new SecurityException("Foreign source submission ticket");}
        public void revoke(){check();revoked=true;}
        public boolean hasPendingHandoff(){check();return pending!=null;}
        private void check(){if(Thread.currentThread()!=thread)throw new IllegalStateException("Native source publication owner thread required");}
    }
    public static final class EntryStatus {
        private final ComponentGate.SourceState source;private final boolean runtimeOwnerCreated;private final boolean inspectionIoPending;private final SourceBootstrap.Status inspection;
        private EntryStatus(ComponentGate.SourceState source,boolean runtimeOwnerCreated,boolean pending,SourceBootstrap.Status inspection){this.source=source;this.runtimeOwnerCreated=runtimeOwnerCreated;inspectionIoPending=pending;this.inspection=inspection;}
        public ComponentGate.SourceState source(){return source;}
        public ProcessRole.StartupRoute route(){return ProcessRole.StartupRoute.MAIN_NATIVE_OWNER;}
        public boolean hasRuntimeOwner(){return runtimeOwnerCreated;}
        public boolean sourceInspectionIoPending(){return inspectionIoPending;}
        public SourceBootstrap.Status sourceInspectionStatus(){return inspection;}
        public boolean sourceAcceptanceDependencyPending(){return source!=ComponentGate.SourceState.TRUSTED;}
    }
    private final Application application;
    private final ComponentGate gate;
    private final CompiledPrincipals principals;
    private final AdminProofVerifier proofVerifier;
    private final CompiledSourceIdentity compiledSource;
    private volatile GeckoRuntimeOwner runtimeOwner;
    private BrowserCoordinator browserDomain;private boolean browserDomainAttempted;
    private PrivateRecordStore privateRecords;private boolean privateRecordsAttempted;private final Object privateRecordOpening=new Object();
    private final ComponentGate.ResourcePin businessLifetime;
    private final AtomicReference<ComponentGate.SourceView> sourceView=new AtomicReference<>(ComponentGate.SourceView.untrusted(ComponentGate.SourceState.INITIALIZING));
    private final Handler main=new Handler(Looper.getMainLooper());
    private volatile SourceBootstrap.AcceptedContext acceptedSource;
    private volatile SourceBootstrap.Status inspectionStatus=SourceBootstrap.Status.UNAVAILABLE;
    private volatile boolean inspectionIoPending;
    private boolean inspectionStarted;private final PublicationGate publication=new PublicationGate();

    private StartupCoordinator(Application application, ProcessRole role) {
        if (application == null || role != ProcessRole.MAIN) throw new IllegalStateException("Primary startup only");
        this.application = application;
        principals = CompiledPrincipals.production();
        proofVerifier = new AdminProofVerifier(principals);
        compiledSource = CompiledSourceIdentity.current();
        // Formal source/Root provisioning and ContinuityCoordinator integration are not
        // complete. NEVER replace this with a debug/first-click/key-proof unlock shortcut.
        sourceView.set(ComponentGate.SourceView.untrusted(compiledSource.configured()?ComponentGate.SourceState.INITIALIZING:ComponentGate.SourceState.UNCONFIGURED));
        gate = ComponentGate.forNativeSource(role,sourceView::get);
        businessLifetime=gate.newResourcePin();
    }
    public ComponentGate gate() { return gate; }
    /** Minimal native entry metadata only; does not initialize/inspect business/Gecko state. */
    public EntryStatus inspectNativeEntry(){return new EntryStatus(gate.sourceState(),runtimeOwner!=null,inspectionIoPending,inspectionStatus);}
    /** One bounded metadata/PM IO owner; no retry, marker creation or old business data open. */
    public void beginNativeSourceInspection(){
        ownerThread();if(inspectionStarted)return;inspectionStarted=true;
        if(!compiledSource.configured()){inspectionStatus=SourceBootstrap.Status.UNCONFIGURED;sourceView.set(ComponentGate.SourceView.untrusted(ComponentGate.SourceState.UNCONFIGURED));return;}
        final PublicationGate.Ticket ticket=publication.begin();inspectionIoPending=true;
        ThreadPoolExecutor io;
        try{io=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(1),r->{Thread t=new Thread(r,"ReBrowser-source-bootstrap");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());}
        catch(RuntimeException knownUnsubmitted){inspectionIoPending=false;publication.finish(ticket);failInspectionHandoff();return;}
        try{io.execute(()->{
            SourceBootstrap.Inspection result=null;
            try{Path directory=application.getNoBackupFilesDir().toPath().resolve(LastGoodSourceStore.BOOTSTRAP_DIRECTORY);result=SourceBootstrap.inspectOwn(application,new LastGoodSourceStore(directory));}
            catch(Exception unavailable){/* missing/unsafe bootstrap is not a fresh installation */}
            finally{inspectionIoPending=false;if(result==null){if(inspectionStatus!=SourceBootstrap.Status.RECOVERY_REQUIRED)inspectionStatus=SourceBootstrap.Status.UNAVAILABLE;sourceView.set(ComponentGate.SourceView.untrusted(ComponentGate.SourceState.LOCKED));}}
            final SourceBootstrap.Inspection finished=result;
            boolean confirmed=false;
            try{if(main.post(()->publishNativeInspection(ticket,finished)))confirmed=publication.confirmSubmission(ticket);}
            catch(RuntimeException unknownSubmission){/* never retry an uncertain post */}
            finally{if(!confirmed){publication.rejectSubmission(ticket);failInspectionHandoff();}}
        });}catch(RejectedExecutionException knownUnsubmitted){inspectionIoPending=false;publication.finish(ticket);failInspectionHandoff();}
        catch(RuntimeException unknownSubmission){failInspectionHandoff();}
        finally{io.shutdown();} // orderly only, NEVER interruption/timeout as actual IO completion
    }
    private void failInspectionHandoff(){if(inspectionStatus!=SourceBootstrap.Status.RECOVERY_REQUIRED)inspectionStatus=SourceBootstrap.Status.UNAVAILABLE;sourceView.set(ComponentGate.SourceView.untrusted(ComponentGate.SourceState.LOCKED));}
    private void publishNativeInspection(PublicationGate.Ticket ticket,SourceBootstrap.Inspection inspection){
        ownerThread();boolean pending=publication.hasPendingHandoff();if(!publication.finish(ticket)){if(pending)failInspectionHandoff();return;} // early/unknown submission cannot publish; duplicate completion cannot change a settled source
        inspectionStatus=inspection==null?SourceBootstrap.Status.UNAVAILABLE:inspection.status();
        if(inspection!=null && inspection.status()==SourceBootstrap.Status.ACCEPTED_STABLE){
            try{SourceBootstrap.AcceptedContext accepted=inspection.takeAccepted(application);acceptedSource=accepted;sourceView.set(ComponentGate.SourceView.acceptedNative(accepted,accepted.dataEpoch()));return;}
            catch(RuntimeException stale){inspectionStatus=SourceBootstrap.Status.UNAVAILABLE;}
        }
        sourceView.set(ComponentGate.SourceView.untrusted(inspectionStatus==SourceBootstrap.Status.UNCONFIGURED?ComponentGate.SourceState.UNCONFIGURED:ComponentGate.SourceState.LOCKED));
    }
    /** Native continuity MUST call/observe this BEFORE any ledger mutation or accepted source cutover.
     * Revoke-only; no public trust setter, no retry/new incarnation/runtime or actual-work release. */
    public void fenceNativeSourceBeforeContinuityMutation(){ownerThread();sourceView.set(ComponentGate.SourceView.untrusted(ComponentGate.SourceState.LOCKED));acceptedSource=null;inspectionStatus=SourceBootstrap.Status.RECOVERY_REQUIRED;publication.revoke();}
    public byte[] requireNativeDataEpoch(ComponentGate.BusinessPermit permit){
        businessLifetime.checkOrBind(permit);SourceBootstrap.AcceptedContext accepted=acceptedSource;if(accepted==null)throw new SecurityException("Native accepted source unavailable");byte[] epoch=accepted.dataEpoch();gate.requireDataEpoch(permit,epoch);return epoch;
    }
    private static void ownerThread(){if(Looper.myLooper()!=Looper.getMainLooper())throw new IllegalStateException("Native source owner thread required");}
    public AdminProofVerifier proofVerifier() { return proofVerifier; }
    public boolean hasCompiledAdministratorPolicy() { return principals.formalRootConfigured(); }
    public CompiledSourceIdentity compiledSourceIdentity() { return compiledSource; }
    /** Minimal native own-APK metadata only; no business DB/runtime or automatic trust write. */
    public SigningIdentity.Identity inspectOwnSigner() throws SigningIdentity.Unavailable { return SigningIdentity.inspectOwn(application); }
    public GeckoRuntimeOwner browserRuntimeOwner(ComponentGate.BusinessPermit permit) {
        businessLifetime.checkOrBind(permit);
        if (runtimeOwner == null) runtimeOwner = GeckoRuntimeOwner.forMainProcess(application,gate,requireNativeDataEpoch(permit),permit);
        return runtimeOwner;
    }
    /** Lazy actual native temporary-domain graph. Does not open a session or load a URL. */
    public BrowserCoordinator temporaryBrowserDomain(ComponentGate.BusinessPermit permit){
        ownerThread();byte[] epoch=requireNativeDataEpoch(permit);
        if(browserDomain!=null)return browserDomain;if(browserDomainAttempted)throw new IllegalStateException("Browser domain construction already attempted");browserDomainAttempted=true;
        browserDomain=BrowserCoordinator.createTemporaryNative(gate,browserRuntimeOwner(permit),epoch,8,permit);gate.checkCurrent(permit);return browserDomain;
    }
    /** Explicit owned IO call only. Existing directory/key required; never source/fresh-install seed.
     * Failure consumes opening attempt; an uncertain provider result cannot create another owner. */
    public PrivateRecordStore openExistingPrivateRecordsOnIo(ComponentGate.BusinessPermit permit)throws java.io.IOException,java.security.GeneralSecurityException {
        if(Looper.myLooper()==Looper.getMainLooper())throw new IllegalStateException("Private record opening requires an owned IO lane");
        byte[] epoch=requireNativeDataEpoch(permit);
        synchronized(privateRecordOpening){
            requireNativeDataEpoch(permit);
            if(privateRecords!=null){privateRecords.requireBindings(gate,epoch,permit);return privateRecords;}
            if(privateRecordsAttempted)throw new IllegalStateException("Native private record opening already attempted");privateRecordsAttempted=true;
            PrivateRecordStore opened=PrivateRecordStore.openExisting(application,gate,epoch,permit);gate.requireDataEpoch(permit,epoch);privateRecords=opened;return opened;
        }
    }
    public boolean hasCreatedBrowserRuntime() { return runtimeOwner != null && runtimeOwner.hasCreatedRuntime(); }
}
