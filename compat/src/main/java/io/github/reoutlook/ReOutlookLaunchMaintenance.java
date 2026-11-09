package io.github.reoutlook;

import android.content.Context;

/** Gecko alpha has no exported ReOutlook maintenance entry point. */
final class ReOutlookLaunchMaintenance {
    private ReOutlookLaunchMaintenance() {}
    static void prepare(Context context) {}
    static void onNewIntent(Context context) {}
}
