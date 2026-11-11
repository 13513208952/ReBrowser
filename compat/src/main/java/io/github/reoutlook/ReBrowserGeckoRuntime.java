package io.github.reoutlook;

import android.content.Context;

import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoRuntimeSettings;

/** Single process-wide Gecko runtime shared by browser, Outlook, and music controllers. */
final class ReBrowserGeckoRuntime {
    private static GeckoRuntime runtime;
    private ReBrowserGeckoRuntime() {}

    static synchronized GeckoRuntime get(Context context) {
        if (runtime == null) {
            GeckoRuntimeSettings settings = new GeckoRuntimeSettings.Builder()
                    .extensionsWebAPIEnabled(true)
                    .build();
            runtime = GeckoRuntime.create(context.getApplicationContext(), settings);
        }
        return runtime;
    }
}
