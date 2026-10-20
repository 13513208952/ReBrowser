package io.github.rebrowser.bootstrap;

import android.app.Application;
import android.os.Looper;
import io.github.rebrowser.authority.AdminProofVerifier;
import io.github.rebrowser.authority.CompiledPrincipals;
import io.github.rebrowser.engine.GeckoRuntimeOwner;
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
    public static final class EntryStatus {
        private final ComponentGate.SourceState source;private final boolean runtimeOwnerCreated;
        private EntryStatus(ComponentGate.SourceState source,boolean runtimeOwnerCreated){this.source=source;this.runtimeOwnerCreated=runtimeOwnerCreated;}
        public ComponentGate.SourceState source(){return source;}
        public ProcessRole.StartupRoute route(){return ProcessRole.StartupRoute.MAIN_NATIVE_OWNER;}
        public boolean hasRuntimeOwner(){return runtimeOwnerCreated;}
        public boolean sourceAcceptanceDependencyPending(){return source!=ComponentGate.SourceState.TRUSTED;}
    }
    private final Application application;
    private final ComponentGate gate;
    private final CompiledPrincipals principals;
    private final AdminProofVerifier proofVerifier;
    private final CompiledSourceIdentity compiledSource;
    private volatile GeckoRuntimeOwner runtimeOwner;
    private final ComponentGate.ResourcePin businessLifetime;

    private StartupCoordinator(Application application, ProcessRole role) {
        if (application == null || role != ProcessRole.MAIN) throw new IllegalStateException("Primary startup only");
        this.application = application;
        principals = CompiledPrincipals.production();
        proofVerifier = new AdminProofVerifier(principals);
        compiledSource = CompiledSourceIdentity.current();
        // Formal source/Root provisioning and ContinuityCoordinator integration are not
        // complete. NEVER replace this with a debug/first-click/key-proof unlock shortcut.
        gate = ComponentGate.forNativeSource(role, () -> ComponentGate.SourceView.untrusted(compiledSource.configured()
                ? ComponentGate.SourceState.LOCKED : ComponentGate.SourceState.UNCONFIGURED));
        businessLifetime=gate.newResourcePin(); // real accepted incarnation must come from continuity, never this shell
    }
    public ComponentGate gate() { return gate; }
    /** Minimal native entry metadata only; does not initialize/inspect business/Gecko state. */
    public EntryStatus inspectNativeEntry(){return new EntryStatus(gate.sourceState(),runtimeOwner!=null);}
    public AdminProofVerifier proofVerifier() { return proofVerifier; }
    public boolean hasCompiledAdministratorPolicy() { return principals.formalRootConfigured(); }
    public CompiledSourceIdentity compiledSourceIdentity() { return compiledSource; }
    /** Minimal native own-APK metadata only; no business DB/runtime or automatic trust write. */
    public SigningIdentity.Identity inspectOwnSigner() throws SigningIdentity.Unavailable { return SigningIdentity.inspectOwn(application); }
    public GeckoRuntimeOwner browserRuntimeOwner(ComponentGate.BusinessPermit permit) {
        businessLifetime.checkOrBind(permit);
        if (runtimeOwner == null) runtimeOwner = GeckoRuntimeOwner.forMainProcess(application, gate);
        return runtimeOwner;
    }
    public boolean hasCreatedBrowserRuntime() { return runtimeOwner != null && runtimeOwner.hasCreatedRuntime(); }
}
