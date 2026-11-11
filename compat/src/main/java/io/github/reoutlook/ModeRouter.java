package io.github.reoutlook;

import android.content.Context;
import android.content.Intent;

/** Routes the Gecko build to its migrated browser implementation. */
final class ModeRouter {
    private ModeRouter() {}

    static void openReBrowser(Context context) {
        if (!ReBrowserRuntime.inspect().browserAvailable()) {
            ReBrowserApplications.openManagement(context);
            return;
        }
        context.startActivity(new Intent(context, ReBrowserActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP));
    }

    static void openOutlook(Context context) {
        context.startActivity(new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP));
    }

    static void openMusic(Context context) {
        if (!new ReBrowserMusicEntrance(context).enabled()) {
            ReBrowserApplications.openManagement(context);
            return;
        }
        context.startActivity(new Intent(context, ReBrowserMusicActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP));
    }
}
