package io.github.reoutlook;

import android.content.Context;
import org.json.JSONObject;

/** Native diagnostics remain reachable without creating any browser runtime or mail WebView. */
@androidx.annotation.OptIn(markerClass = androidx.webkit.WebViewCompat.ExperimentalSaveState.class)
final class ReBrowserAdminEnvironment {
    private ReBrowserAdminEnvironment() {}
    static JSONObject snapshot(Context context) throws Exception {
        ReBrowserRuntime.Status runtime = ReBrowserRuntime.inspect();
        ReBrowserDownloads downloads = new ReBrowserDownloads(context);
        JSONObject history;
        try (ReBrowserHistory store = new ReBrowserHistory(context)) { history = store.snapshot(); }
        return new JSONObject().put("protocolVersion", ReBrowserAdminProtocol.VERSION)
                .put("multiProfile", runtime.multiProfile)
                .put("deleteBrowsingData", runtime.provider && ReBrowserRuntime.feature(androidx.webkit.WebViewFeature.DELETE_BROWSING_DATA))
                .put("saveState", runtime.provider && ReBrowserRuntime.feature(androidx.webkit.WebViewFeature.SAVE_STATE))
                .put("history", history)
                .put("websitePermissions", ReBrowserSettings.websitePolicy(new ReBrowserSitePermissions(context)))
                .put("downloads", new JSONObject().put("downloadsEnabled", downloads.downloadsEnabled())
                        .put("scope", "ReBrowser-named-profiles-only").put("rateWindowSeconds", 300)
                        .put("ordinaryRequestsPerSite", 2).put("siteDefinition", "profileName+topLevelOrigin")
                        .put("maxPendingGlobal", ReBrowserDownloadController.MAX_PENDING_GLOBAL)
                        .put("maxPendingPerSite", ReBrowserDownloadController.MAX_PENDING_PER_SITE)
                        .put("maxHistory", ReBrowserDownloads.MAX_RECORDS)
                        .put("maxBlobBytes", ReBrowserDownloads.MAX_BLOB_BYTES)
                        .put("maxDataBytes", ReBrowserDownloads.MAX_DATA_BYTES)
                        .put("automaticOpen", false).put("automaticInstall", false).put("rawCookieDisclosure", false)
                        .put("policyEffect", "new-and-pending-requests"))
                .put("operations", ReBrowserAdminProtocol.supportedOperations())
                .put("administratorRoots", ReBrowserAdminKeys.capabilities())
                .put("ownerCredentialMaximumLevel", 2).put("rootKeyMaximumLevel", 3)
                .put("maxWorkspaces", ReBrowserStore.MAX_WORKSPACES)
                .put("maxPrimaryWorkspaces", ReBrowserStore.MAX_PRIMARY_WORKSPACES)
                .put("maxTabsPerWorkspace", ReBrowserStore.MAX_TABS_PER_WORKSPACE)
                .put("workspaceRuntimeLoaded", false).put("scope", "native-environment")
                .put("device", ReBrowserDevice.inspect(context).snapshot())
                .put("systemRotation", ReBrowserDevice.systemRotation(context))
                .put("browserWindowStatusAvailable", false)
                .put("runtime", ReBrowserRuntime.inspect().snapshot())
                .put("applications", ReBrowserApplications.snapshot(context))
                .put("settings", ReBrowserSettings.snapshot(context))
                .put("launcherVisibility", new ReOutlookLauncherVisibility(context).snapshot());
    }
}
