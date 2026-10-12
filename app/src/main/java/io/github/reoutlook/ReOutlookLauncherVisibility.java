package io.github.reoutlook;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

import org.json.JSONException;
import org.json.JSONObject;

/** Controls only the mail launcher alias; PackageManager persists the choice across restarts. */
final class ReOutlookLauncherVisibility {
    private final Context context;
    private final PackageManager packages;
    private final ComponentName launcher;

    ReOutlookLauncherVisibility(Context context) {
        this.context = context.getApplicationContext();
        packages = this.context.getPackageManager();
        launcher = new ComponentName(this.context.getPackageName(),
                this.context.getPackageName() + ".ReOutlookLauncher");
    }

    void migrateInstallation() {
        boolean upgrade = false;
        try {
            android.content.pm.PackageInfo info = packages.getPackageInfo(context.getPackageName(), 0);
            upgrade = info.firstInstallTime < info.lastUpdateTime;
        } catch (PackageManager.NameNotFoundException ignored) {}
        migrateInstallation(upgrade);
    }

    synchronized void migrateInstallation(boolean upgrade) {
        android.content.SharedPreferences state = context.getSharedPreferences("rebrowser_applications_v1", Context.MODE_PRIVATE);
        if (state.getBoolean("entranceDefaultsMigrated", false)) return;
        int existing = packages.getComponentEnabledSetting(launcher);
        // Explicit old owner choices remain untouched. Old manifest DEFAULT meant visible.
        if (upgrade && existing == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) {
            packages.setComponentEnabledSetting(launcher, PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP);
        }
        if (!state.edit().putBoolean("entranceDefaultsMigrated", true).commit())
            throw new IllegalStateException("entrance-migration-save-failed");
    }

    boolean visible() {
        return enabled(launcher);
    }

    void setVisible(boolean visible) {
        if (visible() == visible) return;
        packages.setComponentEnabledSetting(launcher,
                visible ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }

    JSONObject snapshot() throws JSONException {
        return new JSONObject()
                .put("outlookLauncherVisible", visible())
                .put("outlookActivityEnabled", enabled(new ComponentName(context, MainActivity.class)))
                .put("rebrowserLauncherEnabled", enabled(new ComponentName(
                        context, ReBrowserLauncherActivity.class)))
                .put("administratorCanSet", false);
    }

    private boolean enabled(ComponentName component) {
        int state = packages.getComponentEnabledSetting(component);
        if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) return true;
        if (state != PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) return false;
        try {
            return packages.getActivityInfo(component,
                    PackageManager.GET_DISABLED_COMPONENTS).enabled;
        } catch (PackageManager.NameNotFoundException error) {
            throw new IllegalStateException("launcher-component-missing", error);
        }
    }
}
