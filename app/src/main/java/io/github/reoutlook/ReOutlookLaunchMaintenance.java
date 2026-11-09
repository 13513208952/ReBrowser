package io.github.reoutlook;

import android.content.Context;

/** WebView-suite launch housekeeping kept outside the engine-neutral Outlook activity. */
final class ReOutlookLaunchMaintenance {
    private ReOutlookLaunchMaintenance() {}

    static void prepare(Context context) {
        MaintenanceAuthorizer.ensureEntryPointsEnabled(context);
        MaintenanceAuthorizer.clearPendingChallenge(context);
        ReBrowserAdminAuthorizer.clearPendingChallenge(context);
    }

    static void onNewIntent(Context context) {
        MaintenanceAuthorizer.clearPendingChallenge(context);
        ReBrowserAdminAuthorizer.clearPendingChallenge(context);
    }
}
