package io.github.reoutlook;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

/** Music is a coexistence application: its required icon and enable state are one choice. */
final class ReBrowserMusicEntrance {
    private final PackageManager packages;
    private final ComponentName alias;
    ReBrowserMusicEntrance(Context context) {
        packages = context.getPackageManager();
        alias = new ComponentName(context.getPackageName(), context.getPackageName() + ".ReBrowserMusicLauncher");
    }
    boolean enabled() {
        if (!BuildConfig.FULL_SUITE) return false;
        int state = packages.getComponentEnabledSetting(alias);
        if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) return true;
        if (state != PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) return false;
        try { return packages.getActivityInfo(alias, PackageManager.GET_DISABLED_COMPONENTS).enabled; }
        catch (PackageManager.NameNotFoundException unavailable) { return false; }
    }
    void setEnabled(boolean enabled) {
        if (!BuildConfig.FULL_SUITE) throw new IllegalStateException("application-not-included");
        packages.setComponentEnabledSetting(alias, enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                : PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        if (!enabled) ReBrowserMusicActivity.stopEnabledRuntime();
    }
}
