package io.github.rebrowser.engine;

import android.app.Application;
import android.os.Looper;
import java.util.Arrays;
import io.github.rebrowser.bootstrap.ComponentGate;
import io.github.rebrowser.bootstrap.ProcessRole;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoRuntimeSettings;

/** Application-process lifetime Gecko owner; native UI/background transitions never shut it down. */
public final class GeckoRuntimeOwner {
    private static GeckoRuntimeOwner primary;
    private final Application application;
    private final ComponentGate gate;
    private final ComponentGate.ResourcePin sourceLifetime;
    private final byte[] epoch;
    private GeckoRuntime runtime;
    private boolean creationAttempted;
    private io.github.rebrowser.bridge.BuiltinConnectorManager connectors;
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
            GeckoRuntime candidate=GeckoRuntime.create(application, sourceBoundSettings());
            GeckoRuntimeSettings observed=candidate.getSettings();
            if(!"".equals(observed.getConfigFilePath()) || observed.getRemoteDebuggingEnabled() || observed.getConsoleOutputEnabled())throw new SecurityException("Native runtime startup policy mismatch");
            runtime=candidate; // publish only after native readback; a failed attempt stays spent
        }
        sourceLifetime.checkEpochOrBind(permit,epoch); // source may change during native runtime creation; never publish old-context runtime
        return runtime;
    }
    /** Null configuration path lets a debuggable Android app read external
     * /data/local/tmp/$PACKAGE-geckoview-config.yaml and merge process args/preferences/extras.
     * That file is NOT source-authorized. Empty explicitly disables this pinned SDK path;
     * no Intent/debug config may substitute a profile, enable devtools or turn on console logs.
     * This is startup policy only, NOT persistent-profile provisioning or a new source grant. */
    static GeckoRuntimeSettings sourceBoundSettings(){return new GeckoRuntimeSettings.Builder().configFilePath("").remoteDebuggingEnabled(false).consoleOutput(false).build();}
    /** Lazy native extension owner, not extension installation/asset existence or page READY. */
    public io.github.rebrowser.bridge.BuiltinConnectorManager builtinConnectors(ComponentGate.BusinessPermit permit){mainThread();sourceLifetime.checkEpochOrBind(permit,epoch);if(connectors==null)connectors=new io.github.rebrowser.bridge.BuiltinConnectorManager(application,this,gate,permit);return connectors;}
    public void requireNativeBindings(Application expectedApplication,ComponentGate expectedGate,ComponentGate.DataEpochPin expectedSource){mainThread();if(expectedApplication!=application || expectedGate!=gate || expectedSource==null || !Arrays.equals(epoch,expectedSource.dataEpoch()))throw new SecurityException("Foreign native connector runtime binding");sourceLifetime.requireCurrent();}
    public boolean hasCreatedRuntime() { mainThread(); return runtime != null; }
    private static void mainThread() {
        if (Looper.myLooper() != Looper.getMainLooper()) throw new IllegalStateException("Main thread required");
    }
}
