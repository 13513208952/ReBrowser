package io.github.reoutlook;

/** Runs the one-time entrance migration before ordinary application screens. */
public final class ReBrowserApplication extends android.app.Application {
    @Override public void onCreate() {
        super.onCreate();
        try {
            if (BuildConfig.FULL_SUITE) new ReOutlookLauncherVisibility(this).migrateInstallation();
            else if (!getSharedPreferences("rebrowser_applications_v1", MODE_PRIVATE).getBoolean("entranceDefaultsMigrated", false)) {
                // Fresh minimal -> full adds mail for the first time, not a legacy visible mail alias.
                if (!getSharedPreferences("rebrowser_applications_v1", MODE_PRIVATE).edit()
                        .putBoolean("entranceDefaultsMigrated", true).commit()) throw new IllegalStateException("entrance-migration-save-failed");
            }
        }
        catch (RuntimeException error) {
            android.util.Log.w("ReBrowser", "Entrance migration unavailable: " + error.getClass().getSimpleName());
        }
    }
}
