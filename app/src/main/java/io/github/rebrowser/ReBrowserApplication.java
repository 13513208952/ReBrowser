package io.github.rebrowser;

import android.app.Application;
import io.github.rebrowser.bootstrap.ProcessRole;
import io.github.rebrowser.bootstrap.StartupCoordinator;

/** New v2 application owner; the legacy io.github.reoutlook application is unrelated. */
public final class ReBrowserApplication extends Application {
    private volatile StartupCoordinator startup;
    private volatile ProcessRole role=ProcessRole.UNKNOWN;
    private boolean applicationCreateObserved;
    @Override public synchronized void onCreate() {
        if(applicationCreateObserved)return;applicationCreateObserved=true;
        super.onCreate();
        try{role=ProcessRole.classifyOwnApplication(getPackageName(),Application.getProcessName());}catch(RuntimeException unavailable){role=ProcessRole.UNKNOWN;}
        if(role.startupRoute()==ProcessRole.StartupRoute.MAIN_NATIVE_OWNER){startup=StartupCoordinator.forMainApplication(this);startup.beginNativeSourceInspection();}
        // Gecko manages its own child services. Never create runtime/DB/control server here.
    }
    /** Local routing metadata only; never administrator identity or source acceptance. */
    public ProcessRole processRole(){return role;}
    public StartupCoordinator primaryStartup() {
        if (role.startupRoute()!=ProcessRole.StartupRoute.MAIN_NATIVE_OWNER || startup == null) throw new IllegalStateException("Primary process startup unavailable");
        return startup;
    }
}
