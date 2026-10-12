package io.github.reoutlook;

import android.content.pm.PackageInfo;
import android.webkit.WebView;
import androidx.webkit.WebViewFeature;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Capability checks never create a workspace or borrow Outlook's Default Profile. */
final class ReBrowserRuntime {
    private ReBrowserRuntime() {}

    static final class Status {
        final boolean provider, multiProfile, messageBridge, documentScript;
        final String packageName, version;
        Status(boolean provider, boolean multi, boolean bridge, boolean script, String name, String version) {
            this.provider = provider; multiProfile = multi; messageBridge = bridge; documentScript = script;
            packageName = name; this.version = version;
        }
        boolean browserAvailable() { return provider && multiProfile && messageBridge && documentScript; }
        JSONObject snapshot() throws JSONException {
            JSONArray missing = new JSONArray();
            if (!provider) missing.put("WEBVIEW_PROVIDER");
            if (!multiProfile) missing.put("MULTI_PROFILE");
            if (!messageBridge) missing.put("WEB_MESSAGE_LISTENER");
            if (!documentScript) missing.put("DOCUMENT_START_SCRIPT");
            return new JSONObject().put("webViewPresent", provider).put("browserAvailable", browserAvailable())
                    .put("outlookAvailable", provider).put("outlookRequiresMultiProfile", false)
                    .put("multiProfile", multiProfile).put("messageBridge", messageBridge)
                    .put("documentScript", documentScript).put("missingFeatures", missing)
                    .put("webViewPackage", packageName).put("webViewVersion", version)
                    .put("nativeApplicationManagementAvailable", true);
        }
    }

    static Status inspect() {
        PackageInfo info;
        try { info = WebView.getCurrentWebViewPackage(); }
        catch (RuntimeException | LinkageError error) { info = null; }
        if (info == null) return new Status(false, false, false, false, "", "");
        return new Status(true, feature(WebViewFeature.MULTI_PROFILE),
                feature(WebViewFeature.WEB_MESSAGE_LISTENER), feature(WebViewFeature.DOCUMENT_START_SCRIPT),
                info.packageName, info.versionName == null ? "" : info.versionName);
    }

    static boolean feature(String name) {
        try { return WebViewFeature.isFeatureSupported(name); }
        catch (RuntimeException | LinkageError error) { return false; }
    }
}
