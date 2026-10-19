package io.github.rebrowser.engine;

import android.app.Application;
import android.os.Looper;
import io.github.rebrowser.bootstrap.ComponentGate;
import io.github.rebrowser.bootstrap.ProcessRole;
import org.mozilla.geckoview.GeckoRuntime;

/** Application-process lifetime Gecko owner; native UI/background transitions never shut it down. */
public final class GeckoRuntimeOwner {
    private static GeckoRuntimeOwner primary;
    private final Application application;
    private final ComponentGate gate;
    private GeckoRuntime runtime;
    private boolean creationAttempted;
    private GeckoRuntimeOwner(Application application, ComponentGate gate) {
        this.application = application; this.gate = gate;
    }
    public static synchronized GeckoRuntimeOwner forMainProcess(Application application, ComponentGate gate) {
        mainThread();
        if (application == null || gate == null || ProcessRole.classify(application.getPackageName(),
                Application.getProcessName()) != ProcessRole.MAIN) throw new IllegalStateException("Not the primary application process");
        if (primary == null) primary = new GeckoRuntimeOwner(application, gate);
        else if (primary.application != application || primary.gate != gate) throw new IllegalStateException("Primary runtime owner already exists");
        return primary;
    }
    public GeckoRuntime requireRuntime(ComponentGate.BusinessPermit permit) {
        mainThread();gate.checkCurrent(permit);
        if (runtime == null) {
            if (creationAttempted) throw new IllegalStateException("Gecko initialization already attempted; no automatic second runtime");
            creationAttempted = true;
            runtime = GeckoRuntime.create(application);
        }
        return runtime;
    }
    public boolean hasCreatedRuntime() { mainThread(); return runtime != null; }
    private static void mainThread() {
        if (Looper.myLooper() != Looper.getMainLooper()) throw new IllegalStateException("Main thread required");
    }
}
