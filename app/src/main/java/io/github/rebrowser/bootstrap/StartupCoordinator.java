package io.github.rebrowser.bootstrap;

import android.app.Application;
import io.github.rebrowser.authority.AdminProofVerifier;
import io.github.rebrowser.authority.CompiledPrincipals;
import io.github.rebrowser.engine.GeckoRuntimeOwner;

/** Native shell -> continuity gate -> on-demand Gecko. No DB/profile restore at Application startup. */
public final class StartupCoordinator {
    private final Application application;
    private final ComponentGate gate;
    private final CompiledPrincipals principals;
    private final AdminProofVerifier proofVerifier;
    private GeckoRuntimeOwner runtimeOwner;

    public StartupCoordinator(Application application, ProcessRole role) {
        if (application == null || role != ProcessRole.MAIN) throw new IllegalStateException("Primary startup only");
        this.application = application;
        principals = CompiledPrincipals.production();
        proofVerifier = new AdminProofVerifier(principals);
        // Formal source/Root provisioning and ContinuityCoordinator integration are not
        // complete. NEVER replace this with a debug/first-click/key-proof unlock shortcut.
        gate = new ComponentGate(role, () -> principals.formalRootConfigured()
                ? ComponentGate.SourceState.LOCKED : ComponentGate.SourceState.UNCONFIGURED);
    }
    public ComponentGate gate() { return gate; }
    public AdminProofVerifier proofVerifier() { return proofVerifier; }
    public boolean hasCompiledAdministratorPolicy() { return principals.formalRootConfigured(); }
    public GeckoRuntimeOwner browserRuntimeOwner(ComponentGate.BusinessPermit permit) {
        gate.checkCurrent(permit);
        if (runtimeOwner == null) runtimeOwner = GeckoRuntimeOwner.forMainProcess(application, gate);
        return runtimeOwner;
    }
    public boolean hasCreatedBrowserRuntime() { return runtimeOwner != null && runtimeOwner.hasCreatedRuntime(); }
}
