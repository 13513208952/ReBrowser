package io.github.reoutlook;

import android.content.Context;
import android.content.Intent;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Fixed modes, not downloadable plugins or independent installed packages. */
final class ReBrowserApplications {
    static final String OUTLOOK = "outlook";
    private ReBrowserApplications() {}
    static boolean validMode(Object value) { return OUTLOOK.equals(value) || "browser".equals(value); }

    static Intent settingsIntent(Context context, String section) {
        // Never strand owner UI inside the transient administrator task.
        return new Intent(context, ReBrowserSettingsActivity.class)
                .putExtra(ReBrowserSettings.EXTRA_SECTION, ReBrowserSettings.section(section))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    }

    static void openManagement(Context context) {
        context.startActivity(settingsIntent(context, ReBrowserSettings.APPLICATIONS));
    }

    static JSONObject snapshot(Context context) throws JSONException {
        ReBrowserRuntime.Status runtime = ReBrowserRuntime.inspect();
        return new JSONObject().put("mainName", "ReBrowser").put("samePackageModes", true)
                .put("runtime", runtime.snapshot()).put("administratorCanSetEntrances", false)
                .put("applications", new JSONArray().put(new JSONObject()
                        .put("id", OUTLOOK).put("name", "ReOutlook")
                        .put("available", runtime.provider).put("requiresMultiProfile", false)
                        .put("launcherVisible", new ReOutlookLauncherVisibility(context).visible())
                        .put("freshInstallLauncherVisible", false)));
    }
}
