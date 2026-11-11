package io.github.reoutlook;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** GeckoView runtime capability report for the migrated application. */
final class ReBrowserRuntime {
    private static final String ENGINE_VERSION = "149.0.20260403140140";
    private ReBrowserRuntime() {}

    static final class Status {
        final boolean provider, multiProfile, messageBridge, documentScript;
        final String packageName, version;

        Status(boolean provider, boolean multi, boolean bridge, boolean script,
                String name, String version) {
            this.provider = provider;
            multiProfile = multi;
            messageBridge = bridge;
            documentScript = script;
            packageName = name;
            this.version = version;
        }

        boolean browserAvailable() {
            return provider && multiProfile;
        }

        JSONObject snapshot() throws JSONException {
            JSONArray missing = new JSONArray();
            if (!provider) missing.put("GECKOVIEW_RUNTIME");
            if (!multiProfile) missing.put("SESSION_CONTEXT");
            return new JSONObject()
                    .put("engine", "geckoview")
                    .put("webViewPresent", false)
                    .put("browserAvailable", browserAvailable())
                    .put("outlookAvailable", provider)
                    .put("outlookRequiresMultiProfile", false)
                    .put("musicAvailable", browserAvailable())
                    .put("musicRequiresMultiProfile", true)
                    .put("multiProfile", multiProfile)
                    .put("messageBridge", messageBridge)
                    .put("documentScript", documentScript)
                    .put("missingFeatures", missing)
                    .put("webViewPackage", "")
                    .put("webViewVersion", "")
                    .put("geckoViewPackage", packageName)
                    .put("geckoViewVersion", version)
                    .put("nativeApplicationManagementAvailable", true);
        }
    }

    static Status inspect() {
        try {
            Class.forName("org.mozilla.geckoview.GeckoRuntime", false,
                    ReBrowserRuntime.class.getClassLoader());
            return new Status(true, true, true, true,
                    "org.mozilla.geckoview", ENGINE_VERSION);
        } catch (LinkageError | ClassNotFoundException error) {
            return new Status(false, false, false, false, "", "");
        }
    }

    static boolean supportsDeleteBrowsingData() { return true; }
    static boolean supportsSaveState() { return true; }
    static String engineDisplayName() { return "GeckoView"; }
    static boolean usesSystemWebView() { return false; }
}
