package io.github.rebrowser.engine;

import android.app.Application;
import android.os.Looper;
import java.util.Arrays;
import io.github.rebrowser.bootstrap.ComponentGate;
import io.github.rebrowser.bootstrap.ProcessRole;
import org.mozilla.geckoview.GeckoRuntime;

/** Application-process lifetime Gecko owner; native UI/background transitions never shut it down. */
public final class GeckoRuntimeOwner {
    private static GeckoRuntimeOwner primary;
    private final Application application;
    private final ComponentGate gate;
    private final ComponentGate.ResourcePin sourceLifetime;
    private final byte[] epoch;
    private GeckoRuntime runtime;
    private boolean creationAttempted;
    private GeckoRuntimeOwner(Application application, ComponentGate gate,byte[] nativeEpoch,ComponentGate.BusinessPermit permit) {
        this.application = application; this.gate = gate;epoch=nativeEpoch.clone();sourceLifetime=gate.newResourcePin();sourceLifetime.checkEpochOrBind(permit,epoch);
    }
    public static synchronized GeckoRuntimeOwner forMainProcess(Application application, ComponentGate gate,byte[] nativeEpoch,ComponentGate.BusinessPermit permit) {
        mainThread();
        if (application == null || gate == null || ProcessRole.classifyOwnApplication(application.getPackageName(),
                Application.getProcessName()) != ProcessRole.MAIN) throw new IllegalStateException("Not the primary application process");
        if(nativeEpoch==null || nativeEpoch.length!=16)throw new IllegalArgumentException("Native runtime epoch required");byte[] accepted=nativeEpoch.clone();gate.requireDataEpoch(permit,accepted);
        if (primary == null) primary = new GeckoRuntimeOwner(application, gate,accepted,permit);
        else if (primary.application != application || primary.gate != gate || !Arrays.equals(primary.epoch,accepted)) throw new IllegalStateException("Primary runtime owner already exists");
        return primary;
    }
    public GeckoRuntime requireRuntime(ComponentGate.BusinessPermit permit) {
        mainThread();sourceLifetime.checkEpochOrBind(permit,epoch);
        if (runtime == null) {
            if (creationAttempted) throw new IllegalStateException("Gecko initialization already attempted; no automatic second runtime");
            creationAttempted = true;
            runtime = GeckoRuntime.create(application);
        }
        sourceLifetime.checkEpochOrBind(permit,epoch); // source may change during native runtime creation; never publish old-context runtime
        return runtime;
    }
    public boolean hasCreatedRuntime() { mainThread(); return runtime != null; }
    private static void mainThread() {
        if (Looper.myLooper() != Looper.getMainLooper()) throw new IllegalStateException("Main thread required");
    }
}
