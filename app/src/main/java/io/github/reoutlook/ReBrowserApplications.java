package io.github.reoutlook;

import android.content.Context;
import android.content.Intent;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Fixed build-time applications; providers are mutually exclusive modes inside music. */
final class ReBrowserApplications {
    static final String OUTLOOK = "outlook", MUSIC = "music";
    static final String EXTRA_APPLICATION = "applicationId";
    private ReBrowserApplications() {}
    static boolean validApplication(Object value) {
        return BuildConfig.FULL_SUITE && (OUTLOOK.equals(value) || MUSIC.equals(value));
    }
    static String name(String id) {
        if (OUTLOOK.equals(id)) return "ReOutlook";
        if (MUSIC.equals(id)) return "ReMusic";
        throw new IllegalArgumentException("Unsupported fixed application");
    }
    static Intent applicationSettingsIntent(Context context, String id) {
        if (!validApplication(id)) throw new IllegalArgumentException("Unsupported fixed application");
        return new Intent().setClassName(context, context.getPackageName() + ".ReBrowserSubapplicationSettingsActivity")
                .putExtra(EXTRA_APPLICATION, id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    }
    static boolean validMode(Object value) { return "browser".equals(value) || (BuildConfig.FULL_SUITE && OUTLOOK.equals(value)); }
    static Intent settingsIntent(Context context, String section) {
        String page = ReBrowserSettings.section(section);
        if (!BuildConfig.FULL_SUITE && ReBrowserSettings.APPLICATIONS.equals(page)) page = ReBrowserSettings.GENERAL;
        return new Intent(context, ReBrowserSettingsActivity.class).putExtra(ReBrowserSettings.EXTRA_SECTION, page)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    }
    static void openManagement(Context context) { context.startActivity(settingsIntent(context, ReBrowserSettings.APPLICATIONS)); }
    static JSONObject snapshot(Context context) throws JSONException {
        ReBrowserRuntime.Status runtime = ReBrowserRuntime.inspect();
        JSONArray applications = new JSONArray();
        if (BuildConfig.FULL_SUITE) {
            applications.put(new JSONObject().put("id", OUTLOOK).put("name", "ReOutlook")
                    .put("available", runtime.provider).put("requiresMultiProfile", false).put("settingsAvailable", true)
                    .put("launcherVisible", new ReOutlookLauncherVisibility(context).visible()).put("freshInstallLauncherVisible", false));
            applications.put(new JSONObject().put("id", MUSIC).put("name", "音乐")
                    .put("provider", new ReBrowserMusicSources(context).selected()).put("available", runtime.browserAvailable())
                    .put("requiresMultiProfile", true).put("settingsAvailable", true).put("enabled", new ReBrowserMusicEntrance(context).enabled())
                    .put("launcherVisible", new ReBrowserMusicEntrance(context).enabled()).put("freshInstallLauncherVisible", false)
                    .put("coexistence", true).put("singleActiveProvider", true).put("backgroundPlaybackVerified", false));
        }
        return new JSONObject().put("mainName", "ReBrowser").put("samePackageModes", true)
                .put("suite", BuildConfig.FULL_SUITE ? "full" : "minimal").put("applicationManagementAvailable", BuildConfig.FULL_SUITE)
                .put("runtime", runtime.snapshot()).put("administratorCanSetEntrances", false).put("applications", applications);
    }
}
