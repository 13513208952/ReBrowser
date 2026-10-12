package io.github.reoutlook;

import android.content.Context;
import android.content.Intent;

/** Routes both desktop entries and in-app switches to the requested application mode. */
final class ModeRouter {
    private ModeRouter() {}

    static void openOutlook(Context context) {
        if (ReBrowserRuntime.inspect().provider) start(context, MainActivity.class);
        else ReBrowserApplications.openManagement(context);
    }

    static void openReBrowser(Context context) {
        if (ReBrowserRuntime.inspect().browserAvailable()) start(context, ReBrowserActivity.class);
        else ReBrowserApplications.openManagement(context);
    }

    private static void start(Context context, Class<?> activityClass) {
        Intent intent = new Intent(context, activityClass)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        context.startActivity(intent);
    }
}
