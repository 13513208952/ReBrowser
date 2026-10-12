package io.github.reoutlook;

/** Runs the one-time entrance migration before ordinary application screens. */
public final class ReBrowserApplication extends android.app.Application {
    @Override public void onCreate() {
        super.onCreate();
        try { new ReOutlookLauncherVisibility(this).migrateInstallation(); }
        catch (RuntimeException error) {
            android.util.Log.w("ReBrowser", "Entrance migration unavailable: " + error.getClass().getSimpleName());
        }
    }
}
