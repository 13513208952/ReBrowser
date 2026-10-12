package io.github.reoutlook;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Preserve old default-visible aliases even before the first post-upgrade desktop launch. */
public final class ReBrowserUpgradeReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            try { new ReOutlookLauncherVisibility(context).migrateInstallation(); }
            catch (RuntimeException error) {
                android.util.Log.w("ReBrowser", "Entrance migration deferred: " + error.getClass().getSimpleName());
            }
        }
    }
}
