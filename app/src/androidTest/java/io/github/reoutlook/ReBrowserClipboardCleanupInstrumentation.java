package io.github.reoutlook;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;

/** Deletes only fixture-owned profile markers, before creating any WebView in a fresh process. */
public final class ReBrowserClipboardCleanupInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args) {super.onCreate(args);start();}
    @Override public void onStart() {
        Bundle result=new Bundle();int[] remaining={-1};
        try {
            runOnMainSync(()->remaining[0]=ReBrowserClipboardProbeActivity.cleanupDeferredProfiles(getTargetContext()));
            result.putInt("remainingFixtureProfiles",remaining[0]);result.putString("status",remaining[0]==0?"passed":"deferred");
            finish(remaining[0]==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
        } catch(Throwable error) {
            result.putString("status","failed");result.putString("error",error.getClass().getSimpleName());finish(Activity.RESULT_CANCELED,result);
        }
    }
}
