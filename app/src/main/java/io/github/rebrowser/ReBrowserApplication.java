package io.github.rebrowser;

import android.app.Application;
import io.github.rebrowser.bootstrap.ProcessRole;
import io.github.rebrowser.bootstrap.StartupCoordinator;

/** New v2 application owner; the legacy io.github.reoutlook application is unrelated. */
public final class ReBrowserApplication extends Application {
    private StartupCoordinator startup;
    @Override public void onCreate() {
        super.onCreate();
        ProcessRole role = ProcessRole.classify(getPackageName(), Application.getProcessName());
        if (role == ProcessRole.MAIN) startup = new StartupCoordinator(this, role);
        // Gecko manages its own child services. Never create runtime/DB/control server here.
    }
    public StartupCoordinator primaryStartup() {
        if (startup == null) throw new IllegalStateException("Primary process startup unavailable");
        return startup;
    }
}
