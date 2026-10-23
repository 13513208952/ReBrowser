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
import io.github.rebrowser.browser.BrowserStore;
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
    private BrowserStore browserStore;private boolean browserStoreAttempted;private io.github.rebrowser.download.DownloadStore downloadStore;private boolean downloadStoreAttempted;
    private io.github.rebrowser.data.OwnedDataCatalog browserData;private boolean browserDataAttempted;
    private io.github.rebrowser.browser.BookmarkStore bookmarks;private volatile io.github.rebrowser.browser.HistoryStore history;
    private volatile boolean historyCaptureIncomplete;
    private io.github.rebrowser.settings.SettingsRepository settings;private io.github.rebrowser.subapps.SubapplicationCoordinator subapplications;
    private BrowserStoreCoordinator browserInspector;private boolean browserInspectorAttempted;
    private io.github.rebrowser.music.MusicSessionOwner musicSessions;private boolean musicSessionsAttempted;private io.github.rebrowser.music.MusicCoordinator musicDomain;private boolean musicDomainAttempted;
    private io.github.rebrowser.outlook.OutlookSessionOwner outlookSessions;private boolean outlookSessionsAttempted;
    private PrivateRecordStore privateRecords;private boolean privateRecordsAttempted;private final Object privateRecordOpening=new Object();
    private final ThreadPoolExecutor browserDataIo=newBrowserDataExecutor();
    /** Settings/history share ONE decoded-root credit. Their actual native producers/readers
     * must therefore use one lane, rather than fail randomly on independent UI worker threads.
     * No thread/read/key/directory is started merely by constructing this executor. */
    private static ThreadPoolExecutor newBrowserDataExecutor(){ThreadPoolExecutor e=new ThreadPoolExecutor(1,1,15,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(64),r->{Thread t=new Thread(r,"ReBrowser-browser-data");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());e.allowCoreThreadTimeOut(true);return e;}
    /** Admission only. Each native job MUST recheck its exact source on execution and keep its
     * own finally/close handling. No wrapper skips that cleanup on a later source revocation.
     * Window retirement never shuts down or frees a running shared job. No main-thread IO. */
    public void executeBrowserDataIo(ComponentGate.BusinessPermit permit,Runnable nativeJob){if(nativeJob==null)throw new IllegalArgumentException("Missing native browser data job");gate.checkCurrent(permit);browserDataIo.execute(nativeJob);}
    private io.github.rebrowser.system.ApkStagingStore apkStages;private boolean apkOpeningAttempted;private final Object apkOpening=new Object();
    private final ComponentGate.ResourcePin businessLifetime;
    private final AtomicReference<ComponentGate.SourceView> sourceView=new AtomicReference<>(ComponentGate.SourceView.untrusted(ComponentGate.SourceState.INITIALIZING));
    private final Handler main=new Handler(Looper.getMainLooper());
    private volatile SourceBootstrap.AcceptedContext acceptedSource;private volatile boolean sourceWasEverAccepted,coldContinuityFenceAttempted;
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
            try{Path directory=application.getDataDir().toPath().resolve("no_backup").resolve(LastGoodSourceStore.BOOTSTRAP_DIRECTORY);result=SourceBootstrap.inspectOwn(application,new LastGoodSourceStore(directory));}
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
            try{SourceBootstrap.AcceptedContext accepted=inspection.takeAccepted(application);sourceWasEverAccepted=true;acceptedSource=accepted;sourceView.set(ComponentGate.SourceView.acceptedNative(accepted,accepted.dataEpoch()));return;}
            catch(RuntimeException stale){inspectionStatus=SourceBootstrap.Status.UNAVAILABLE;}
        }
        sourceView.set(ComponentGate.SourceView.untrusted(inspectionStatus==SourceBootstrap.Status.UNCONFIGURED?ComponentGate.SourceState.UNCONFIGURED:ComponentGate.SourceState.LOCKED));
    }
    /** Native continuity MUST call/observe this BEFORE any ledger mutation or accepted source cutover.
     * Revoke-only; no public trust setter, no retry/new incarnation/runtime or actual-work release. */
    public void fenceNativeSourceBeforeContinuityMutation(){ownerThread();sourceView.set(ComponentGate.SourceView.untrusted(ComponentGate.SourceState.LOCKED));acceptedSource=null;inspectionStatus=SourceBootstrap.Status.RECOVERY_REQUIRED;publication.revoke();}
    /** Cold-only native safety capability. NOT callback/metadata-as-quiescence: it is issuable
     * ONLY before this actual main owner has EVER accepted a business epoch or opened owners.
     * Active/warm processes must use a separately qualified actual-stop path (not implemented). */
    public static final class ColdContinuityFence {
        private final StartupCoordinator issuer;private ColdContinuityFence(StartupCoordinator issuer){this.issuer=issuer;}
        public void requireStillCold(Application exactNativeApplication){if(exactNativeApplication!=issuer.application || !issuer.coldContinuityFenceAttempted || issuer.sourceWasEverAccepted || issuer.acceptedSource!=null || issuer.inspectionIoPending || issuer.inspectionStatus!=SourceBootstrap.Status.RECOVERY_REQUIRED || issuer.gate.sourceState()!=ComponentGate.SourceState.LOCKED || issuer.runtimeOwner!=null || issuer.privateRecords!=null || issuer.apkStages!=null || issuer.musicSessions!=null || issuer.outlookSessions!=null)throw new SecurityException("Native cold continuity safety no longer holds");}
    }
    public ColdContinuityFence fenceNeverActivatedMismatchForNativeRecovery(){ownerThread();if(coldContinuityFenceAttempted || sourceWasEverAccepted || inspectionStatus!=SourceBootstrap.Status.SOURCE_MISMATCH || inspectionIoPending || publication.hasPendingHandoff() || acceptedSource!=null || runtimeOwner!=null || privateRecords!=null || apkStages!=null || musicSessions!=null || outlookSessions!=null)throw new SecurityException("Active/unknown native work cannot use cold erasure path");coldContinuityFenceAttempted=true;fenceNativeSourceBeforeContinuityMutation();return new ColdContinuityFence(this);}
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
        browserDomain=BrowserCoordinator.createTemporaryNative(gate,browserRuntimeOwner(permit),epoch,8,permit);
        browserDomain.installHistorySink(new NativeHistorySink(permit),permit);gate.checkCurrent(permit);return browserDomain;
    }
    /** Non-secret capture availability, not success/empty-history or a storage-completion proof. */
    public boolean historyCaptureIncomplete(){return historyCaptureIncomplete;}
    private interface NativeHistoryWrite {void write(io.github.rebrowser.browser.HistoryStore store)throws Exception;}
    /** Own application IO, never a caller-supplied history backend or Activity reference. Native
     * SDK visits and catalog lifecycle changes share one bounded ordered queue. Rejection/failure
     * is reported, not silently called a recorded visit; it never breaks page navigation. */
    private final class NativeHistorySink implements BrowserCoordinator.HistorySink {
        private final ComponentGate.BusinessPermit permit;
        private final java.util.LinkedHashMap<String,io.github.rebrowser.browser.HistoryStore.Entry> latest=new java.util.LinkedHashMap<>();
        private long recordingRevision=-1;
        NativeHistorySink(ComponentGate.BusinessPermit permit){this.permit=permit;}
        private long observedRecording(){try{io.github.rebrowser.browser.HistoryStore known=history;return known==null?0:known.captureVisitRecordingRevision();}catch(RuntimeException sourceUnavailable){historyCaptureIncomplete=true;return -1;}}
        private void submit(NativeHistoryWrite work){try{gate.checkCurrent(permit);executeBrowserDataIo(permit,()->{try{gate.checkCurrent(permit);io.github.rebrowser.browser.HistoryStore store=openExistingHistoryStoreOnIo(permit);long revision=store.recordingRevision();if(revision!=recordingRevision){latest.clear();recordingRevision=revision;}work.write(store);gate.checkCurrent(permit);}catch(Exception unavailable){historyCaptureIncomplete=true;}});}catch(RuntimeException unavailable){historyCaptureIncomplete=true;}}
        public void visited(io.github.rebrowser.browser.HistoryStore.Page page){long observedRecording=observedRecording();if(observedRecording<0)return;long now=System.currentTimeMillis();submit(store->{if(store.paused() || store.recordingRevision()!=observedRecording)return;io.github.rebrowser.browser.HistoryStore.Snapshot saved=store.recordVisit(page,now,observedRecording,permit);if(store.recordingRevision()!=observedRecording)return;io.github.rebrowser.browser.HistoryStore.Entry found=null;for(io.github.rebrowser.browser.HistoryStore.Entry e:saved.entries())if(e.tabId().equals(page.tabId()) && e.workspaceId().equals(page.source().workspaceId()) && e.url().equals(page.url()) && e.visitedUtcMs()==now && (found==null || e.ordinal()>found.ordinal()))found=e;if(found!=null){String key=found.workspaceId()+":"+found.tabId();if(!latest.containsKey(key) && latest.size()>=io.github.rebrowser.browser.HistoryStore.MAX_CURRENT_PAGES)latest.remove(latest.keySet().iterator().next());latest.put(key,found);}});}
        public void sourceChanged(io.github.rebrowser.browser.HistoryStore.Source source){long now=System.currentTimeMillis();submit(store->{store.workspaceChanged(source,now,permit);if(!source.open())latest.values().removeIf(e->e.workspaceId().equals(source.workspaceId()));});}
        public void titleChanged(String workspaceId,String tabId,String url,String title){long observedRecording=observedRecording();if(observedRecording<0)return;submit(store->{if(store.paused() || store.recordingRevision()!=observedRecording)return;io.github.rebrowser.browser.HistoryStore.Entry original=latest.get(workspaceId+":"+tabId);if(original!=null && original.url().equals(url))store.updateNativeVisitTitle(original.id(),url,title,permit);});}
    }
    /** Canonical UI/service shared built-in session owners. Construction does not open/load/play. */
    public io.github.rebrowser.music.MusicSessionOwner musicSessionOwner(ComponentGate.BusinessPermit permit){ownerThread();requireNativeDataEpoch(permit);if(musicSessions!=null)return musicSessions;if(musicSessionsAttempted)throw new IllegalStateException("Native music owner already attempted");musicSessionsAttempted=true;musicSessions=new io.github.rebrowser.music.MusicSessionOwner(gate,browserRuntimeOwner(permit),permit);gate.checkCurrent(permit);return musicSessions;}
    public io.github.rebrowser.music.MusicCoordinator musicCoordinator(ComponentGate.BusinessPermit permit){ownerThread();requireNativeDataEpoch(permit);if(musicDomain!=null)return musicDomain;if(musicDomainAttempted)throw new IllegalStateException("Native music domain already attempted");musicDomainAttempted=true;musicDomain=new io.github.rebrowser.music.MusicCoordinator(application,gate,permit,browserRuntimeOwner(permit),musicSessionOwner(permit));gate.checkCurrent(permit);return musicDomain;}
    public io.github.rebrowser.outlook.OutlookSessionOwner outlookSessionOwner(ComponentGate.BusinessPermit permit){ownerThread();requireNativeDataEpoch(permit);if(outlookSessions!=null)return outlookSessions;if(outlookSessionsAttempted)throw new IllegalStateException("Native Outlook owner already attempted");outlookSessionsAttempted=true;outlookSessions=new io.github.rebrowser.outlook.OutlookSessionOwner(gate,browserRuntimeOwner(permit),permit);gate.checkCurrent(permit);return outlookSessions;}
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
    /** Existing encrypted download metadata, shared native IO owner; never auto-seeds or restores grants. */
    public io.github.rebrowser.download.DownloadStore openExistingDownloadsOnIo(ComponentGate.BusinessPermit permit)throws java.io.IOException,java.security.GeneralSecurityException{if(Looper.myLooper()==Looper.getMainLooper())throw new IllegalStateException("Native download records require owned IO");requireNativeDataEpoch(permit);synchronized(privateRecordOpening){if(downloadStore!=null){downloadStore.requireBindings(gate,permit);return downloadStore;}if(downloadStoreAttempted)throw new IllegalStateException("Native download records opening already attempted");downloadStoreAttempted=true;io.github.rebrowser.download.DownloadStore opened=io.github.rebrowser.download.DownloadStore.openExisting(openExistingPrivateRecordsOnIo(permit),gate,permit);gate.checkCurrent(permit);downloadStore=opened;return opened;}}
    /** Native task IO only; does not create missing private epoch/stage directories. */
    public io.github.rebrowser.system.ApkStagingStore openExistingApkStagesOnIo(ComponentGate.BusinessPermit permit)throws java.io.IOException{if(Looper.myLooper()==Looper.getMainLooper())throw new IllegalStateException("Native APK stage opening requires owned IO");byte[] epoch=requireNativeDataEpoch(permit);synchronized(apkOpening){if(apkStages!=null){apkStages.requireBindings(gate,permit);return apkStages;}if(apkOpeningAttempted)throw new IllegalStateException("Native APK stage opening already attempted");apkOpeningAttempted=true;io.github.rebrowser.system.ApkStagingStore opened=io.github.rebrowser.system.ApkStagingStore.openExisting(application,gate.captureDataEpoch(permit));gate.requireDataEpoch(permit,epoch);apkStages=opened;return opened;}}
    /** Provider reads only an already explicitly opened native stage owner. No lazy IO/creation. */
    public io.github.rebrowser.system.ApkStagingStore existingApkStagesForNativeProvider(){synchronized(apkOpening){ComponentGate.BusinessPermit permit=gate.requireBusiness();if(apkStages==null)throw new SecurityException("Native APK stage owner not active");apkStages.requireBindings(gate,permit);return apkStages;}}
    /** Main-process accepted source only; canonical existing retained metadata owner on owned IO.
     * Missing/corrupt catalog is not fresh-install proof; no automatic write or restore occurs. */
    public BrowserStore openExistingBrowserStoreOnIo(ComponentGate.BusinessPermit permit)throws java.io.IOException,java.security.GeneralSecurityException {
        if(Looper.myLooper()==Looper.getMainLooper())throw new IllegalStateException("Browser catalog opening requires an owned IO lane");
        byte[] epoch=requireNativeDataEpoch(permit);
        synchronized(privateRecordOpening){
            requireNativeDataEpoch(permit);if(browserStore!=null){browserStore.requireBindings(gate,permit);return browserStore;}
            if(browserStoreAttempted)throw new IllegalStateException("Native browser catalog opening already attempted");browserStoreAttempted=true;
            BrowserStore opened=BrowserStore.openExisting(openExistingPrivateRecordsOnIo(permit),gate,permit);gate.requireDataEpoch(permit,epoch);browserStore=opened;return opened;
        }
    }
    /** Canonical existing browser-metadata owner on the SAME private-record IO lane. No read,
     * seed/default values, callbacks, autosave or directory/key creation at construction. */
    public io.github.rebrowser.data.OwnedDataCatalog openExistingBrowserDataOnIo(ComponentGate.BusinessPermit permit)throws java.io.IOException,java.security.GeneralSecurityException{
        if(Looper.myLooper()==Looper.getMainLooper())throw new IllegalStateException("Browser metadata requires an owned IO lane");
        byte[] epoch=requireNativeDataEpoch(permit);synchronized(privateRecordOpening){
            requireNativeDataEpoch(permit);if(browserData!=null){browserData.requireBindings(gate,permit);return browserData;}
            if(browserDataAttempted)throw new IllegalStateException("Native browser metadata owner already attempted");browserDataAttempted=true;
            io.github.rebrowser.data.OwnedDataCatalog opened=io.github.rebrowser.data.OwnedDataCatalog.openExisting(openExistingPrivateRecordsOnIo(permit),gate,permit);gate.requireDataEpoch(permit,epoch);browserData=opened;return opened;
        }
    }
    public io.github.rebrowser.browser.BookmarkStore openExistingBookmarkStoreOnIo(ComponentGate.BusinessPermit permit)throws java.io.IOException,java.security.GeneralSecurityException{
        synchronized(privateRecordOpening){io.github.rebrowser.data.OwnedDataCatalog owner=openExistingBrowserDataOnIo(permit);if(bookmarks==null)bookmarks=new io.github.rebrowser.browser.BookmarkStore(owner);return bookmarks;}
    }
    /** Native current open-page metadata only, for explicit resume-recording synchronization.
     * Never creates a browser/session or restores shelved environments just to inspect history. */
    public java.util.List<io.github.rebrowser.browser.HistoryStore.Page> captureOpenHistoryPages(ComponentGate.BusinessPermit permit){ownerThread();gate.checkCurrent(permit);return browserDomain==null?java.util.Collections.emptyList():browserDomain.captureOpenHistoryPages(permit);}
    public io.github.rebrowser.browser.HistoryStore openExistingHistoryStoreOnIo(ComponentGate.BusinessPermit permit)throws java.io.IOException,java.security.GeneralSecurityException{
        synchronized(privateRecordOpening){io.github.rebrowser.data.OwnedDataCatalog owner=openExistingBrowserDataOnIo(permit);if(history==null)history=new io.github.rebrowser.browser.HistoryStore(owner,permit);return history;}
    }
    public io.github.rebrowser.settings.SettingsRepository openExistingSettingsOnIo(ComponentGate.BusinessPermit permit)throws java.io.IOException,java.security.GeneralSecurityException{
        synchronized(privateRecordOpening){io.github.rebrowser.data.OwnedDataCatalog owner=openExistingBrowserDataOnIo(permit);if(settings==null)settings=new io.github.rebrowser.settings.SettingsRepository(owner);return settings;}
    }
    public io.github.rebrowser.subapps.SubapplicationCoordinator subapplicationDomainOnIo(ComponentGate.BusinessPermit permit)throws java.io.IOException,java.security.GeneralSecurityException{
        synchronized(privateRecordOpening){io.github.rebrowser.settings.SettingsRepository owner=openExistingSettingsOnIo(permit);if(subapplications==null)subapplications=new io.github.rebrowser.subapps.SubapplicationCoordinator(owner);return subapplications;}
    }
    /** Lazy explicit native inspection only. Does not open Gecko or read any business record here. */
    public BrowserStoreCoordinator browserCatalogInspector(ComponentGate.BusinessPermit permit){
        ownerThread();requireNativeDataEpoch(permit);if(browserInspector!=null)return browserInspector;
        if(browserInspectorAttempted)throw new IllegalStateException("Native catalog inspector construction already attempted");browserInspectorAttempted=true;
        browserInspector=new BrowserStoreCoordinator(this,permit);gate.checkCurrent(permit);return browserInspector;
    }
    public boolean hasCreatedBrowserRuntime() { return runtimeOwner != null && runtimeOwner.hasCreatedRuntime(); }
}
