package io.github.reoutlook;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.SystemClock;
import android.util.Log;
import android.view.ViewConfiguration;
import android.webkit.ValueCallback;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.WebExtension;
import org.mozilla.geckoview.WebRequestError;
import org.mozilla.geckoview.WebResponse;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** GeckoView implementation behind the v1.0.5 browser UI and workspace model. */
final class ReBrowserWebController implements ReBrowserPageDownloadBridge {
    private static ReBrowserWebController bridgeServiceOwner;

    interface Listener {
        void onPageStarted(ReBrowserStore.Tab tab, String url, boolean visible);
        void onVisitedHistory(ReBrowserStore.Tab tab, String url, boolean reload);
        void onPageFinished(ReBrowserStore.Tab tab, String url, String title, boolean visible);
        void onMainFrameError(ReBrowserStore.Tab tab, int errorCode,
                String description, boolean visible);
        void onProgressChanged(ReBrowserStore.Tab tab, int progress, boolean visible);
        void onLoadingStopped(ReBrowserStore.Tab tab, boolean visible);
        void onTitleReceived(ReBrowserStore.Tab tab, String title, boolean visible);
        void onExternalNavigationBlocked(String message);
        void onExternalNavigationRequested(Uri uri, String scheme);
        PopupTarget onCreatePopup(ReBrowserStore.Tab sourceTab);
        void onPopupCreated(ReBrowserStore.Tab tab);
        void onPopupCreationFailed(ReBrowserStore.Tab tab, Throwable error);
        void onCloseRequested(ReBrowserStore.Tab tab);
        void onDownloadRequested(DownloadRequest request);
        void onFileChooserUnavailable();
        void onSitePermissionRequested(ReBrowserPermissionRequest request);
    }

    static final class PopupTarget {
        final String workspaceId, profileName;
        final ReBrowserStore.Tab tab;
        PopupTarget(String workspaceId, String profileName, ReBrowserStore.Tab tab) {
            this.workspaceId = workspaceId;
            this.profileName = profileName;
            this.tab = tab;
        }
    }

    static final class DownloadRequest {
        final String workspaceId, tabId, profileName, sourceUrl, sourceOrigin, url;
        final String requestMethod, userAgent, contentDisposition, mimeType;
        final long contentLength;
        DownloadRequest(String workspaceId, String tabId, String profileName,
                String sourceUrl, String sourceOrigin, String url, String requestMethod,
                String userAgent, String contentDisposition, String mimeType,
                long contentLength) {
            this.workspaceId = workspaceId;
            this.tabId = tabId;
            this.profileName = profileName;
            this.sourceUrl = sourceUrl;
            this.sourceOrigin = sourceOrigin;
            this.url = url;
            this.requestMethod = requestMethod;
            this.userAgent = userAgent;
            this.contentDisposition = contentDisposition;
            this.mimeType = mimeType;
            this.contentLength = contentLength;
        }
    }

    static final class PageState {
        final ReBrowserStore.Tab tab;
        final String url, title;
        PageState(ReBrowserStore.Tab tab, String url, String title) {
            this.tab = tab;
            this.url = url;
            this.title = title;
        }
    }

    private static final class PageBinding {
        final String workspaceId, profileName;
        final ReBrowserStore.Tab tab;
        final GeckoSession session;
        final GeckoView view;
        final long generation;
        final ReBrowserClipboardGesture clipboardGesture = new ReBrowserClipboardGesture();
        final Map<String, Long> permissionPromptBlockedUntil = new HashMap<>();
        final Map<String, ValueCallback<JSONObject>> bridgeCallbacks = new HashMap<>();
        String url, title, pendingInitialUrl;
        long navigationGeneration;
        long nextBridgeRequest = 1L;
        boolean loading, canGoBack, canGoForward;
        int progress;
        WebExtension.Port bridgePort;
        String lastMainFrameError = "";

        PageBinding(String workspaceId, String profileName, ReBrowserStore.Tab tab,
                GeckoSession session, GeckoView view, long generation) {
            this.workspaceId = workspaceId;
            this.profileName = profileName;
            this.tab = tab;
            this.session = session;
            this.view = view;
            this.generation = generation;
            this.url = tab.url;
            this.title = tab.title;
        }
    }

    private final Activity activity;
    private final ReBrowserPreferences preferences;
    private final ReBrowserSitePermissions sitePermissions;
    private final ReBrowserFullscreenController fullscreenController;
    private final Listener listener;
    private final int fileChooserRequestCode;
    private final GeckoRuntime runtime;
    private final Map<String, PageBinding> pagesByTabId = new HashMap<>();
    private final Map<GeckoSession, PageBinding> pagesBySession = new IdentityHashMap<>();
    private final List<ReBrowserPermissionRequest> pendingPermissionRequests = new ArrayList<>();
    private final ReBrowserPlayerCompatibility playerCompatibility =
            new ReBrowserPlayerCompatibility();
    private final Map<String, BlobChunk> completedBlobChunks = new HashMap<>();
    private FrameLayout container;
    private PageBinding visiblePage;
    private GeckoSession.PromptDelegate.FilePrompt pendingFilePrompt;
    private GeckoResult<GeckoSession.PromptDelegate.PromptResponse> pendingFileResult;
    private PageBinding pendingFilePage;
    private WebExtension bridgeExtension;
    private WebExtension.Port bridgeServicePort;
    private final Map<String, ValueCallback<JSONObject>> bridgeServiceCallbacks =
            new HashMap<>();
    private long nextBridgeServiceRequest = 1L;
    private boolean bridgeInstallationFailed;
    private boolean foreground = true;
    private long nextGeneration = 1L;

    ReBrowserWebController(Activity activity, ReBrowserPreferences preferences,
            ReBrowserSitePermissions sitePermissions,
            ReBrowserFullscreenController fullscreenController, Listener listener,
            int fileChooserRequestCode) {
        this.activity = activity;
        this.preferences = preferences;
        this.sitePermissions = sitePermissions;
        this.fullscreenController = fullscreenController;
        this.listener = listener;
        this.fileChooserRequestCode = fileChooserRequestCode;
        runtime = ReBrowserGeckoRuntime.get(activity);
        runtime.getWebExtensionController().ensureBuiltIn(
                "resource://android/assets/extensions/rebrowser/",
                "engine-bridge@rebrowser.local").accept(extension ->
                        activity.runOnUiThread(() -> onBridgeInstalled(extension)), error ->
                        activity.runOnUiThread(() -> onBridgeInstallationFailed(error)));
    }

    private void onBridgeInstalled(WebExtension extension) {
        bridgeExtension = extension;
        // Gecko routes native ports through either the per-session or extension
        // delegate depending on where connectNative was called. Register both;
        // attachBridgePort still requires an owned top-level content-script session.
        ReBrowserWebController previousServiceOwner = bridgeServiceOwner;
        bridgeServiceOwner = this;
        extension.setMessageDelegate(new WebExtension.MessageDelegate() {
            @Override public void onConnect(WebExtension.Port port) {
                WebExtension.MessageSender sender = port == null ? null : port.sender;
                if (isBridgeSender(sender) && sender.session == null
                        && sender.environmentType
                        == WebExtension.MessageSender.ENV_TYPE_EXTENSION) {
                    attachBridgeServicePort(port);
                    return;
                }
                PageBinding page = sender == null ? null : pagesBySession.get(sender.session);
                attachBridgePort(page, port);
            }
        }, "browser");
        if (previousServiceOwner != null && previousServiceOwner != this) {
            previousServiceOwner.releaseBridgeService();
        }
        for (PageBinding page : new ArrayList<>(pagesByTabId.values())) {
            bindBridge(page);
            if (page.pendingInitialUrl != null) {
                String url = page.pendingInitialUrl;
                page.pendingInitialUrl = null;
                page.session.loadUri(url);
            }
        }
    }

    private void onBridgeInstallationFailed(Throwable error) {
        bridgeInstallationFailed = true;
        for (PageBinding page : new ArrayList<>(pagesByTabId.values())) {
            page.pendingInitialUrl = null;
            page.lastMainFrameError = "engine-bridge-unavailable";
            listener.onMainFrameError(page.tab, -1,
                    "Gecko engine bridge unavailable", isVisible(page));
        }
    }

    void attachContainer(FrameLayout container) { this.container = container; }
    boolean hasPages() { return !pagesByTabId.isEmpty(); }

    @Override public boolean profileExists(String profileName) {
        return ReBrowserStore.isOwnedProfile(profileName);
    }

    boolean deleteProfile(String profileName) {
        if (!ReBrowserStore.isOwnedProfile(profileName)) return false;
        for (PageBinding page : pagesByTabId.values()) {
            if (profileName.equals(page.profileName)) return false;
        }
        runtime.getStorageController().clearDataForSessionContext(profileName);
        return true;
    }

    void showTab(String workspaceId, String profileName, ReBrowserStore.Tab tab) {
        PageBinding page = pagesByTabId.get(tab.id);
        if (page == null) page = createPage(workspaceId, profileName, tab);
        showPage(page, true);
    }

    private PageBinding createPage(String workspaceId, String profileName,
            ReBrowserStore.Tab tab) {
        if (!ReBrowserStore.isOwnedProfile(profileName)) {
            throw new IllegalArgumentException("拒绝使用非 ReBrowser Profile");
        }
        GeckoSessionSettings settings = new GeckoSessionSettings.Builder()
                .contextId(profileName)
                .allowJavascript(preferences.javascriptEnabled())
                .userAgentMode(preferences.desktopModeEnabled()
                        ? GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
                        : GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
                .viewportMode(preferences.desktopModeEnabled()
                        ? GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
                        : GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
                .suspendMediaWhenInactive(true)
                .build();
        if (!profileName.equals(settings.getContextId())) {
            throw new IllegalStateException("Gecko context isolation unavailable");
        }
        GeckoSession session = new GeckoSession(settings);
        GeckoView view = new GeckoView(activity);
        PageBinding page = new PageBinding(
                workspaceId, profileName, tab, session, view, nextGeneration++);
        installDelegates(page);
        installClipboardInputPolicy(page);
        session.open(runtime);
        pagesByTabId.put(tab.id, page);
        pagesBySession.put(session, page);
        if (bridgeExtension != null) bindBridge(page);
        return page;
    }

    private void showPage(PageBinding page, boolean loadInitialUrl) {
        if (container == null) throw new IllegalStateException("web-container-unavailable");
        if (visiblePage != null && visiblePage != page) {
            visiblePage.session.setFocused(false);
            visiblePage.session.setActive(false);
            visiblePage.session.flushSessionState();
            visiblePage.view.releaseSession();
            cancelPermissionRequestsForTab(visiblePage.tab.id, "tab-hidden");
        }
        container.removeAllViews();
        if (page.view.getParent() instanceof android.view.ViewGroup) {
            ((android.view.ViewGroup) page.view.getParent()).removeView(page.view);
        }
        if (page.view.getSession() == null) page.view.setSession(page.session);
        container.addView(page.view, ReBrowserUi.matchMatch());
        visiblePage = page;
        page.session.setActive(foreground);
        page.session.setFocused(foreground);
        if (loadInitialUrl && (page.url == null || page.url.isBlank()
                || ReBrowserNewTabView.isBlank(page.url))) {
            page.url = page.tab.url;
        }
        if (loadInitialUrl && page.session.isOpen() && !ReBrowserNewTabView.isBlank(page.url)
                && !page.loading && page.progress == 0) {
            if (bridgeInstallationFailed) {
                throw new IllegalStateException("gecko-engine-bridge-unavailable");
            }
            if (bridgeExtension == null) page.pendingInitialUrl = page.url;
            else page.session.loadUri(page.url);
        }
    }

    void resumeTab(String tabId) {
        PageBinding page = pagesByTabId.get(tabId);
        if (page != null) showPage(page, false);
    }

    void showNativePage(android.view.View view) {
        if (container == null || view.getParent() == container) return;
        if (visiblePage != null) {
            visiblePage.session.setFocused(false);
            visiblePage.session.setActive(false);
            visiblePage.view.releaseSession();
            cancelPermissionRequestsForTab(visiblePage.tab.id, "native-page-shown");
            visiblePage = null;
        }
        container.removeAllViews();
        if (view.getParent() instanceof android.view.ViewGroup) {
            ((android.view.ViewGroup) view.getParent()).removeView(view);
        }
        container.addView(view, ReBrowserUi.matchMatch());
    }

    private void bindBridge(PageBinding page) {
        WebExtension extension = bridgeExtension;
        if (extension == null || !page.session.isOpen()) return;
        page.session.getWebExtensionController().setMessageDelegate(extension,
                new WebExtension.MessageDelegate() {
                    @Override public void onConnect(WebExtension.Port port) {
                        attachBridgePort(page, port);
                    }
                }, "browser");
    }

    private void attachBridgePort(PageBinding expectedPage, WebExtension.Port port) {
        WebExtension.MessageSender sender = port == null ? null : port.sender;
        PageBinding senderPage = sender == null ? null : pagesBySession.get(sender.session);
        PageBinding page = expectedPage == null ? senderPage : expectedPage;
        if (page == null || !isBridgeSender(sender) || senderPage != page
                || sender.environmentType
                != WebExtension.MessageSender.ENV_TYPE_CONTENT_SCRIPT
                || !sender.isTopLevel()) {
            Log.w("ReBrowserGecko", "Rejected engine bridge port: sender="
                    + (sender != null) + ", session=" + (senderPage != null)
                    + ", expected=" + (expectedPage == null || expectedPage == senderPage)
                    + ", environment=" + (sender == null ? -1 : sender.environmentType)
                    + ", top=" + (sender != null && sender.isTopLevel()));
            if (port != null) port.disconnect();
            return;
        }
        if (page.bridgePort != null && page.bridgePort != port) {
            page.bridgePort.disconnect();
        }
        page.bridgePort = port;
        Log.i("ReBrowserGecko", "Engine bridge connected for tab");
        port.setDelegate(new WebExtension.PortDelegate() {
            @Override public void onPortMessage(Object message, WebExtension.Port source) {
                if (source != page.bridgePort) return;
                JSONObject value = jsonObject(message);
                String type = value.optString("type");
                if ("command-result".equals(type)) {
                    ValueCallback<JSONObject> callback = page.bridgeCallbacks.remove(
                            value.optString("id"));
                    if (callback != null) callback.onReceiveValue(value);
                } else if ("permission".equals(type)) {
                    handleBridgePermissionMessage(page, value.optString("payload"));
                }
            }

            @Override public void onDisconnect(WebExtension.Port source) {
                if (source != page.bridgePort) return;
                page.bridgePort = null;
                for (ValueCallback<JSONObject> callback
                        : new ArrayList<>(page.bridgeCallbacks.values())) {
                    callback.onReceiveValue(null);
                }
                page.bridgeCallbacks.clear();
            }
        });
    }

    private boolean isBridgeSender(WebExtension.MessageSender sender) {
        return sender != null && sender.webExtension != null && bridgeExtension != null
                && bridgeExtension.id.equals(sender.webExtension.id);
    }

    private void releaseBridgeService() {
        if (bridgeServicePort != null) bridgeServicePort.disconnect();
        bridgeServicePort = null;
        for (ValueCallback<JSONObject> callback
                : new ArrayList<>(bridgeServiceCallbacks.values())) {
            callback.onReceiveValue(null);
        }
        bridgeServiceCallbacks.clear();
    }

    private void attachBridgeServicePort(WebExtension.Port port) {
        if (bridgeServicePort != null && bridgeServicePort != port) {
            bridgeServicePort.disconnect();
        }
        bridgeServicePort = port;
        Log.i("ReBrowserGecko", "Engine bridge service connected");
        port.setDelegate(new WebExtension.PortDelegate() {
            @Override public void onPortMessage(Object message, WebExtension.Port source) {
                if (source != bridgeServicePort) return;
                JSONObject value = jsonObject(message);
                if (!"command-result".equals(value.optString("type"))) return;
                ValueCallback<JSONObject> callback = bridgeServiceCallbacks.remove(
                        value.optString("id"));
                if (callback != null) callback.onReceiveValue(value);
            }

            @Override public void onDisconnect(WebExtension.Port source) {
                if (source != bridgeServicePort) return;
                bridgeServicePort = null;
                for (ValueCallback<JSONObject> callback
                        : new ArrayList<>(bridgeServiceCallbacks.values())) {
                    callback.onReceiveValue(null);
                }
                bridgeServiceCallbacks.clear();
            }
        });
    }

    private static JSONObject jsonObject(Object value) {
        if (value instanceof JSONObject) return (JSONObject) value;
        if (value instanceof Map) return new JSONObject((Map<?, ?>) value);
        try { return new JSONObject(String.valueOf(value)); }
        catch (Exception ignored) { return new JSONObject(); }
    }

    private void sendBridgeCommand(PageBinding page, String operation, JSONObject arguments,
            ValueCallback<JSONObject> callback) {
        if (!isVisible(page) || page.bridgePort == null) {
            Log.w("ReBrowserGecko", "Engine bridge command unavailable: " + operation
                    + ", visible=" + isVisible(page)
                    + ", connected=" + (page.bridgePort != null));
            callback.onReceiveValue(null);
            return;
        }
        String id = Long.toString(page.nextBridgeRequest++);
        JSONObject message = new JSONObject();
        try {
            message.put("type", "command").put("id", id).put("operation", operation);
            if (arguments != null) {
                java.util.Iterator<String> keys = arguments.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    message.put(key, arguments.opt(key));
                }
            }
            page.bridgeCallbacks.put(id, callback);
            page.bridgePort.postMessage(message);
        } catch (Exception error) {
            page.bridgeCallbacks.remove(id);
            callback.onReceiveValue(null);
        }
    }

    private void sendBridgeServiceCommand(String operation, JSONObject arguments,
            ValueCallback<JSONObject> callback) {
        if (bridgeServicePort == null) {
            callback.onReceiveValue(null);
            return;
        }
        String id = Long.toString(nextBridgeServiceRequest++);
        JSONObject message = new JSONObject();
        try {
            message.put("type", "command").put("id", id).put("operation", operation);
            if (arguments != null) {
                java.util.Iterator<String> keys = arguments.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    message.put(key, arguments.opt(key));
                }
            }
            bridgeServiceCallbacks.put(id, callback);
            bridgeServicePort.postMessage(message);
        } catch (Exception error) {
            bridgeServiceCallbacks.remove(id);
            callback.onReceiveValue(null);
        }
    }

    private void handleBridgePermissionMessage(PageBinding page, String payload) {
        String id = "";
        try {
            if (payload == null || payload.length() > 220_000 || !isVisible(page)) {
                sendPermissionReply(page, id, false, null, "page-not-eligible");
                return;
            }
            JSONObject request = new JSONObject(payload);
            id = request.optString("id", "");
            if (!id.matches("[0-9]{1,12}")) {
                sendPermissionReply(page, "", false, null, "invalid-request-id");
                return;
            }
            String origin = safeOrigin(page.url);
            if (origin.isEmpty()) {
                sendPermissionReply(page, id, false, null, "page-not-eligible");
                return;
            }
            String operation = request.optString("operation");
            if ("clipboard-read".equals(operation)
                    || "clipboard-write".equals(operation)) {
                handleClipboardRequest(page, origin, id, operation, request);
                return;
            }
            sendPermissionReply(page, id, false, null, "unsupported-operation");
        } catch (Exception error) {
            sendPermissionReply(page, id, false, null, "invalid-request");
        }
    }

    private void handleClipboardRequest(PageBinding page, String origin, String id,
            String operation, JSONObject request) {
        if (!clipboardPageEligible(page)) {
            page.clipboardGesture.invalidate();
            sendPermissionReply(page, id, false, null, "page-not-eligible");
            return;
        }
        if (!page.clipboardGesture.consume()) {
            sendPermissionReply(page, id, false, null,
                    "clipboard-user-gesture-required");
            return;
        }
        long navigation = page.navigationGeneration;
        long inputContext = page.clipboardGesture.contextGeneration();
        requestPermissions(page, origin, List.of(ReBrowserSitePermissions.CLIPBOARD),
                List.of(), () -> {
                    if (!clipboardPageEligible(page)
                            || page.navigationGeneration != navigation
                            || page.clipboardGesture.contextGeneration() != inputContext
                            || !origin.equals(safeOrigin(page.url))) {
                        sendPermissionReply(page, id, false, null, "page-not-eligible");
                        return;
                    }
                    ClipboardManager clipboard = (ClipboardManager)
                            activity.getSystemService(Activity.CLIPBOARD_SERVICE);
                    if ("clipboard-write".equals(operation)) {
                        String text = request.optString("text", "");
                        if (text.length() > 200_000) {
                            sendPermissionReply(page, id, false, null,
                                    "clipboard-text-too-large");
                            return;
                        }
                        clipboard.setPrimaryClip(ClipData.newPlainText("网页剪贴板", text));
                        sendPermissionReply(page, id, true, JSONObject.NULL, "");
                    } else {
                        ClipData clip = clipboard.getPrimaryClip();
                        CharSequence text = clip == null || clip.getItemCount() == 0 ? ""
                                : clip.getItemAt(0).coerceToText(activity);
                        String value = text == null ? "" : text.toString();
                        if (value.length() > 200_000) value = value.substring(0, 200_000);
                        sendPermissionReply(page, id, true, value, "");
                    }
                }, () -> sendPermissionReply(page, id, false, null, "clipboard-denied"));
    }

    private void sendPermissionReply(PageBinding page, String id, boolean ok,
            Object value, String error) {
        if (page.bridgePort == null) return;
        try {
            String payload = new JSONObject().put("id", id).put("ok", ok)
                    .put("value", value == null ? JSONObject.NULL : value)
                    .put("message", error == null ? "" : error).toString();
            page.bridgePort.postMessage(new JSONObject()
                    .put("type", "permission-result").put("payload", payload));
        } catch (Exception ignored) {
            // Navigation and port teardown invalidate outstanding replies.
        }
    }

    private void installClipboardInputPolicy(PageBinding page) {
        int slop = ViewConfiguration.get(activity).getScaledTouchSlop();
        page.view.setOnTouchListener((view, event) -> {
            if (!clipboardPageEligible(page) || event.getX() < 0 || event.getY() < 0
                    || event.getX() >= view.getWidth() || event.getY() >= view.getHeight()) {
                page.clipboardGesture.invalidate();
            } else page.clipboardGesture.touch(event, slop);
            return false;
        });
    }

    private boolean clipboardPageEligible(PageBinding page) {
        return foreground && isVisible(page) && page.view.isShown()
                && activity.hasWindowFocus();
    }

    void applyPreferencesToAll() {
        for (PageBinding page : pagesByTabId.values()) {
            page.session.getSettings().setAllowJavascript(preferences.javascriptEnabled());
            page.session.getSettings().setUserAgentMode(preferences.desktopModeEnabled()
                    ? GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
                    : GeckoSessionSettings.USER_AGENT_MODE_MOBILE);
            page.session.getSettings().setViewportMode(preferences.desktopModeEnabled()
                    ? GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
                    : GeckoSessionSettings.VIEWPORT_MODE_MOBILE);
        }
    }

    boolean youtubePlayerCompatibilityEnabled(String profileName, String url) {
        return playerCompatibility.enabled(profileName, url);
    }
    boolean setYoutubePlayerCompatibilityEnabled(String profileName, boolean enabled) {
        return playerCompatibility.setEnabled(profileName, enabled);
    }
    void clearYoutubePlayerCompatibility(String profileName) {
        playerCompatibility.clearForProfile(profileName);
    }

    void load(String tabId, String url) {
        PageBinding page = requirePage(tabId);
        page.progress = 0;
        page.session.loadUri(url);
    }
    void stop(String tabId) { requirePage(tabId).session.stop(); }
    void reload(String tabId) { requirePage(tabId).session.reload(); }
    boolean canGoBack(String tabId) {
        PageBinding page = pagesByTabId.get(tabId);
        return page != null && page.canGoBack;
    }
    boolean canGoForward(String tabId) {
        PageBinding page = pagesByTabId.get(tabId);
        return page != null && page.canGoForward;
    }
    void goBack(String tabId) { requirePage(tabId).session.goBack(); }
    boolean goBackWithinPopup(String tabId) {
        PageBinding page = pagesByTabId.get(tabId);
        if (page == null || !page.canGoBack) return false;
        page.session.goBack();
        return true;
    }
    void goForward(String tabId) { requirePage(tabId).session.goForward(); }
    String currentUrl(String tabId) {
        PageBinding page = pagesByTabId.get(tabId);
        return page == null ? null : page.url;
    }
    String currentTitle(String tabId) {
        PageBinding page = pagesByTabId.get(tabId);
        return page == null ? null : page.title;
    }
    boolean isLoading(String tabId) {
        PageBinding page = pagesByTabId.get(tabId);
        return page != null && page.loading;
    }
    int loadProgress(String tabId) {
        PageBinding page = pagesByTabId.get(tabId);
        return page == null ? 0 : page.progress;
    }
    String lastMainFrameError(String tabId) {
        PageBinding page = pagesByTabId.get(tabId);
        return page == null ? "" : page.lastMainFrameError;
    }
    List<PageState> pageStates() {
        List<PageState> states = new ArrayList<>();
        for (PageBinding page : pagesByTabId.values()) {
            states.add(new PageState(page.tab, page.url, page.title));
        }
        return states;
    }

    void removeTab(String tabId) {
        PageBinding page = pagesByTabId.remove(tabId);
        if (page != null) destroyPage(page);
    }

    void destroyAll() {
        for (PageBinding page : new ArrayList<>(pagesByTabId.values())) destroyPage(page);
        pagesByTabId.clear();
        pagesBySession.clear();
        visiblePage = null;
        releaseBridgeService();
        if (container != null) container.removeAllViews();
        cancelAllPermissionRequests("pages-destroyed");
    }

    private void destroyPage(PageBinding page) {
        if (pendingFilePage == page) cancelPendingFileChooser();
        pagesByTabId.remove(page.tab.id);
        pagesBySession.remove(page.session);
        if (page.view.getSession() != null) page.view.releaseSession();
        if (page.bridgePort != null) page.bridgePort.disconnect();
        page.bridgePort = null;
        for (ValueCallback<JSONObject> callback
                : new ArrayList<>(page.bridgeCallbacks.values())) {
            callback.onReceiveValue(null);
        }
        page.bridgeCallbacks.clear();
        completedBlobChunks.keySet().removeIf(key -> key.startsWith(page.tab.id + ":"));
        page.session.setFocused(false);
        page.session.setActive(false);
        page.session.close();
        if (page.view.getParent() instanceof android.view.ViewGroup) {
            ((android.view.ViewGroup) page.view.getParent()).removeView(page.view);
        }
        cancelPermissionRequestsForTab(page.tab.id, "page-destroyed");
    }

    void onResume() {
        foreground = true;
        if (visiblePage != null) {
            visiblePage.session.setActive(true);
            visiblePage.session.setFocused(true);
        }
    }
    void pauseForAndroidPermissionDialog() {
        if (visiblePage != null) visiblePage.session.setFocused(false);
    }
    void onPause() {
        foreground = false;
        for (PageBinding page : pagesByTabId.values()) page.clipboardGesture.invalidate();
        if (visiblePage != null) {
            visiblePage.session.setFocused(false);
            visiblePage.session.setActive(false);
            visiblePage.session.flushSessionState();
        }
        cancelAllPermissionRequests("application-backgrounded");
    }
    void onWindowFocusChanged(boolean focused) {
        if (!focused) {
            for (PageBinding page : pagesByTabId.values()) {
                page.clipboardGesture.invalidate();
            }
        }
        if (visiblePage != null && foreground) visiblePage.session.setFocused(focused);
    }

    void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != fileChooserRequestCode || pendingFilePrompt == null) return;
        GeckoSession.PromptDelegate.FilePrompt prompt = pendingFilePrompt;
        GeckoResult<GeckoSession.PromptDelegate.PromptResponse> result = pendingFileResult;
        pendingFilePrompt = null;
        pendingFileResult = null;
        pendingFilePage = null;
        if (resultCode != Activity.RESULT_OK || data == null) {
            result.complete(prompt.dismiss());
            return;
        }
        List<Uri> uris = new ArrayList<>();
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) {
                Uri uri = clip.getItemAt(i).getUri();
                if (uri != null) uris.add(uri);
            }
        } else if (data.getData() != null) uris.add(data.getData());
        if (uris.isEmpty()) result.complete(prompt.dismiss());
        else result.complete(prompt.confirm(activity, uris.toArray(new Uri[0])));
    }
    boolean hasPendingFileChooser() { return pendingFilePrompt != null; }
    void cancelPendingFileChooser() {
        if (pendingFilePrompt != null && pendingFileResult != null) {
            pendingFileResult.complete(pendingFilePrompt.dismiss());
        }
        pendingFilePrompt = null;
        pendingFileResult = null;
        pendingFilePage = null;
    }

    @Override public String cookieForHttpDownload(String tabId, String profileName,
            String sourceOrigin, String requestUrl) {
        throw new IllegalStateException("gecko-cookie-query-is-asynchronous");
    }

    @Override public void cookieForHttpDownload(String tabId, String profileName,
            String sourceOrigin, String requestUrl, ValueCallback<String> callback) {
        PageBinding page;
        try {
            page = requireMatchingPage(tabId, profileName, sourceOrigin);
        } catch (RuntimeException error) {
            callback.onReceiveValue(null);
            return;
        }
        long navigation = page.navigationGeneration;
        ValueCallback<JSONObject> resultCallback = result -> {
            if (pagesByTabId.get(tabId) != page
                    || page.navigationGeneration != navigation
                    || !sourceOrigin.equals(safeOrigin(page.url))) {
                callback.onReceiveValue(null);
                return;
            }
            if (result == null || !result.optBoolean("ok")) {
                Log.w("ReBrowserGecko", "Cookie bridge failed: "
                        + (result == null ? "no-result" : result.optString("error")));
                callback.onReceiveValue(null);
            } else callback.onReceiveValue(result.optString("value", ""));
        };
        if (bridgeServicePort != null) {
            sendBridgeServiceCommand("cookies", jsonArgs(
                    "url", requestUrl,
                    "storeId", "firefox-container-" + profileName), resultCallback);
        } else {
            sendBridgeCommand(page, "cookies", jsonArgs("url", requestUrl), resultCallback);
        }
    }

    @Override public BlobHandle beginBlobTransfer(String tabId, String profileName,
            String origin, String blobUrl, String transferId, ValueCallback<Boolean> callback) {
        PageBinding page = requireMatchingPage(tabId, profileName, origin);
        String key = "rebrowser-" + transferId;
        BlobHandle handle = new BlobHandle(tabId, profileName, origin, key,
                page.generation, page.navigationGeneration);
        sendBridgeCommand(page, "blob-begin",
                jsonArgs("key", key, "url", blobUrl), result -> callback.onReceiveValue(
                        result != null && result.optBoolean("ok")));
        return handle;
    }

    @Override public void pollBlobMetadata(BlobHandle handle,
            ValueCallback<BlobMetadata> callback) {
        PageBinding page = requireBlobPage(handle);
        sendBridgeCommand(page, "blob-metadata", jsonArgs("key", handle.key), result -> {
            if (result == null || !result.optBoolean("ok")) {
                callback.onReceiveValue(new BlobMetadata("error",
                        result == null ? "engine-bridge-unavailable"
                                : result.optString("error", "blob-metadata-failed"), 0L, ""));
                return;
            }
            JSONObject value = result.optJSONObject("value");
            if (value == null) {
                callback.onReceiveValue(new BlobMetadata(
                        "error", "blob-metadata-invalid", 0L, ""));
                return;
            }
            callback.onReceiveValue(new BlobMetadata(value.optString("status", "error"),
                    value.optString("error", ""), value.optLong("size", 0L),
                    value.optString("type", "")));
        });
    }

    @Override public void requestBlobChunk(BlobHandle handle, long offset, long end,
            ValueCallback<Boolean> callback) {
        PageBinding page = requireBlobPage(handle);
        sendBridgeCommand(page, "blob-chunk", jsonArgs("key", handle.key,
                "offset", offset, "end", end), result -> {
            if (result == null || !result.optBoolean("ok")) {
                callback.onReceiveValue(false);
                return;
            }
            JSONObject value = result.optJSONObject("value");
            if (value == null) {
                callback.onReceiveValue(false);
                return;
            }
            completedBlobChunks.put(blobResultKey(handle), new BlobChunk(
                    value.optString("status", "error"), value.optString("error", ""),
                    value.optString("data", "")));
            callback.onReceiveValue(true);
        });
    }

    @Override public void pollBlobChunk(BlobHandle handle,
            ValueCallback<BlobChunk> callback) {
        requireBlobPage(handle);
        BlobChunk chunk = completedBlobChunks.remove(blobResultKey(handle));
        callback.onReceiveValue(chunk == null
                ? new BlobChunk("loading", "", "") : chunk);
    }

    @Override public void endBlobTransfer(BlobHandle handle) {
        completedBlobChunks.remove(blobResultKey(handle));
        PageBinding page = pagesByTabId.get(handle.tabId);
        if (page == null || page.generation != handle.pageGeneration
                || page.navigationGeneration != handle.navigationGeneration) return;
        sendBridgeCommand(page, "blob-end", jsonArgs("key", handle.key), ignored -> {});
    }

    private PageBinding requireBlobPage(BlobHandle handle) {
        PageBinding page = requireMatchingPage(
                handle.tabId, handle.profileName, handle.origin);
        if (page.generation != handle.pageGeneration) {
            throw new IllegalStateException("blob-source-page-replaced");
        }
        if (page.navigationGeneration != handle.navigationGeneration) {
            throw new IllegalStateException("blob-source-page-changed");
        }
        return page;
    }

    private PageBinding requireMatchingPage(String tabId, String profileName, String origin) {
        PageBinding page = requirePage(tabId);
        if (!page.profileName.equals(profileName)) {
            throw new IllegalStateException("profile-mismatch");
        }
        if (!origin.equals(safeOrigin(page.url))) {
            throw new IllegalStateException("origin-changed");
        }
        return page;
    }

    private static String blobResultKey(BlobHandle handle) {
        return handle.tabId + ":" + handle.key;
    }

    private void installDelegates(PageBinding page) {
        page.session.setNavigationDelegate(new GeckoSession.NavigationDelegate() {
            @Override public GeckoResult<AllowOrDeny> onLoadRequest(GeckoSession session,
                    LoadRequest request) {
                Uri uri = safeUri(request.uri);
                String scheme = uri == null ? "" : lower(uri.getScheme());
                if ("https".equals(scheme) || "http".equals(scheme)
                        || "about".equals(scheme)) return GeckoResult.allow();
                if ("tel".equals(scheme) || "mailto".equals(scheme)
                        || "sms".equals(scheme) || "geo".equals(scheme)) {
                    listener.onExternalNavigationRequested(uri, scheme);
                } else listener.onExternalNavigationBlocked("已阻止不受支持的外部链接");
                return GeckoResult.deny();
            }

            @Override public void onLocationChange(GeckoSession session, String url,
                    List<GeckoSession.PermissionDelegate.ContentPermission> permissions,
                    Boolean hasUserGesture) {
                if (!owns(page, session) || url == null) return;
                page.url = url;
            }

            @Override public void onCanGoBack(GeckoSession session, boolean value) {
                if (owns(page, session)) page.canGoBack = value;
            }
            @Override public void onCanGoForward(GeckoSession session, boolean value) {
                if (owns(page, session)) page.canGoForward = value;
            }

            @Override public GeckoResult<GeckoSession> onNewSession(GeckoSession session,
                    String url) {
                if (!isVisible(page)) return GeckoResult.fromValue(null);
                PopupTarget target = listener.onCreatePopup(page.tab);
                if (target == null) return GeckoResult.fromValue(null);
                try {
                    PageBinding popup = createPage(target.workspaceId, target.profileName,
                            target.tab);
                    activity.runOnUiThread(() -> {
                        showPage(popup, false);
                        listener.onPopupCreated(target.tab);
                    });
                    return GeckoResult.fromValue(popup.session);
                } catch (Throwable error) {
                    listener.onPopupCreationFailed(target.tab, error);
                    return GeckoResult.fromValue(null);
                }
            }

            @Override public GeckoResult<String> onLoadError(GeckoSession session,
                    String url, WebRequestError error) {
                if (owns(page, session)) {
                    page.loading = false;
                    page.lastMainFrameError = error == null ? "unknown"
                            : Integer.toString(error.code);
                    listener.onMainFrameError(page.tab,
                            error == null ? -1 : error.code,
                            error == null ? "unknown" : error.getMessage(), isVisible(page));
                }
                return null;
            }
        });

        page.session.setProgressDelegate(new GeckoSession.ProgressDelegate() {
            @Override public void onPageStart(GeckoSession session, String url) {
                if (!owns(page, session)) return;
                page.url = url;
                page.navigationGeneration++;
                page.clipboardGesture.invalidate();
                for (ValueCallback<JSONObject> callback
                        : new ArrayList<>(page.bridgeCallbacks.values())) {
                    callback.onReceiveValue(null);
                }
                page.bridgeCallbacks.clear();
                completedBlobChunks.keySet().removeIf(
                        key -> key.startsWith(page.tab.id + ":"));
                page.loading = true;
                page.progress = 5;
                page.lastMainFrameError = "";
                listener.onPageStarted(page.tab, url, isVisible(page));
            }
            @Override public void onProgressChange(GeckoSession session, int progress) {
                if (!owns(page, session)) return;
                page.progress = progress;
                listener.onProgressChanged(page.tab, progress, isVisible(page));
            }
            @Override public void onPageStop(GeckoSession session, boolean success) {
                if (!owns(page, session)) return;
                page.loading = false;
                page.progress = success ? 100 : page.progress;
                if (success) {
                    listener.onVisitedHistory(page.tab, page.url, false);
                    listener.onPageFinished(page.tab, page.url, page.title, isVisible(page));
                } else listener.onLoadingStopped(page.tab, isVisible(page));
            }
        });

        page.session.setContentDelegate(new GeckoSession.ContentDelegate() {
            @Override public void onTitleChange(GeckoSession session, String title) {
                if (!owns(page, session)) return;
                page.title = title;
                listener.onTitleReceived(page.tab, title, isVisible(page));
            }
            @Override public void onCloseRequest(GeckoSession session) {
                if (owns(page, session)) listener.onCloseRequested(page.tab);
            }
            @Override public void onFullScreen(GeckoSession session, boolean fullScreen) {
                if (isVisible(page)) fullscreenController.setDocumentFullscreen(fullScreen);
            }
            @Override public void onExternalResponse(GeckoSession session,
                    WebResponse response) {
                if (!owns(page, session) || response == null || response.uri == null) return;
                String disposition = header(response.headers, "content-disposition");
                String mime = header(response.headers, "content-type");
                long length = parseLong(header(response.headers, "content-length"));
                listener.onDownloadRequested(new DownloadRequest(page.workspaceId, page.tab.id,
                        page.profileName, page.url, safeOrigin(page.url), response.uri, "GET",
                        GeckoSession.getDefaultUserAgent(), disposition, mime, length));
            }
        });

        page.session.setPromptDelegate(new GeckoSession.PromptDelegate() {
            @Override public GeckoResult<PromptResponse> onAlertPrompt(GeckoSession session,
                    AlertPrompt prompt) {
                if (!isVisible(page)) return GeckoResult.fromValue(prompt.dismiss());
                GeckoResult<PromptResponse> result = new GeckoResult<>();
                new AlertDialog.Builder(activity).setTitle(prompt.title)
                        .setMessage(prompt.message)
                        .setPositiveButton("确定", (dialog, which) ->
                                result.complete(prompt.dismiss()))
                        .setOnCancelListener(dialog -> result.complete(prompt.dismiss()))
                        .show();
                return result;
            }

            @Override public GeckoResult<PromptResponse> onButtonPrompt(GeckoSession session,
                    ButtonPrompt prompt) {
                if (!isVisible(page)) return GeckoResult.fromValue(prompt.dismiss());
                GeckoResult<PromptResponse> result = new GeckoResult<>();
                new AlertDialog.Builder(activity).setTitle(prompt.title)
                        .setMessage(prompt.message)
                        .setPositiveButton("确定", (dialog, which) ->
                                result.complete(prompt.confirm(0)))
                        .setNegativeButton("取消", (dialog, which) ->
                                result.complete(prompt.confirm(2)))
                        .setOnCancelListener(dialog -> result.complete(prompt.dismiss()))
                        .show();
                return result;
            }

            @Override public GeckoResult<PromptResponse> onTextPrompt(GeckoSession session,
                    TextPrompt prompt) {
                if (!isVisible(page)) return GeckoResult.fromValue(prompt.dismiss());
                GeckoResult<PromptResponse> result = new GeckoResult<>();
                EditText input = new EditText(activity);
                input.setSingleLine(true);
                input.setText(prompt.defaultValue == null ? "" : prompt.defaultValue);
                new AlertDialog.Builder(activity).setTitle(prompt.title)
                        .setMessage(prompt.message).setView(input)
                        .setPositiveButton("确定", (dialog, which) -> result.complete(
                                prompt.confirm(input.getText().toString())))
                        .setNegativeButton("取消", (dialog, which) ->
                                result.complete(prompt.dismiss()))
                        .setOnCancelListener(dialog -> result.complete(prompt.dismiss()))
                        .show();
                return result;
            }

            @Override public GeckoResult<PromptResponse> onBeforeUnloadPrompt(
                    GeckoSession session, BeforeUnloadPrompt prompt) {
                if (!isVisible(page)) return GeckoResult.fromValue(
                        prompt.confirm(AllowOrDeny.DENY));
                GeckoResult<PromptResponse> result = new GeckoResult<>();
                new AlertDialog.Builder(activity).setTitle("离开此页面？")
                        .setMessage("网页中尚未保存的内容可能会丢失。")
                        .setPositiveButton("离开", (dialog, which) -> result.complete(
                                prompt.confirm(AllowOrDeny.ALLOW)))
                        .setNegativeButton("留下", (dialog, which) -> result.complete(
                                prompt.confirm(AllowOrDeny.DENY)))
                        .setOnCancelListener(dialog -> result.complete(
                                prompt.confirm(AllowOrDeny.DENY))).show();
                return result;
            }

            @Override public GeckoResult<PromptResponse> onRepostConfirmPrompt(
                    GeckoSession session, RepostConfirmPrompt prompt) {
                if (!isVisible(page)) return GeckoResult.fromValue(
                        prompt.confirm(AllowOrDeny.DENY));
                GeckoResult<PromptResponse> result = new GeckoResult<>();
                new AlertDialog.Builder(activity).setTitle("重新提交表单？")
                        .setMessage("刷新会再次发送此前提交的数据。")
                        .setPositiveButton("重新提交", (dialog, which) -> result.complete(
                                prompt.confirm(AllowOrDeny.ALLOW)))
                        .setNegativeButton("取消", (dialog, which) -> result.complete(
                                prompt.confirm(AllowOrDeny.DENY)))
                        .setOnCancelListener(dialog -> result.complete(
                                prompt.confirm(AllowOrDeny.DENY))).show();
                return result;
            }

            @Override public GeckoResult<PromptResponse> onPopupPrompt(GeckoSession session,
                    PopupPrompt prompt) {
                // User-triggered window.open is already represented by onNewSession.
                return GeckoResult.fromValue(prompt.confirm(isVisible(page)
                        ? AllowOrDeny.ALLOW : AllowOrDeny.DENY));
            }

            @Override public GeckoResult<PromptResponse> onAuthPrompt(GeckoSession session,
                    AuthPrompt prompt) {
                if (!isVisible(page)) return GeckoResult.fromValue(prompt.dismiss());
                GeckoResult<PromptResponse> result = new GeckoResult<>();
                LinearLayout fields = new LinearLayout(activity);
                fields.setOrientation(LinearLayout.VERTICAL);
                EditText username = new EditText(activity);
                username.setHint("用户名");
                username.setSingleLine(true);
                EditText password = new EditText(activity);
                password.setHint("密码");
                password.setSingleLine(true);
                password.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                        | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
                if (prompt.authOptions != null) {
                    username.setText(prompt.authOptions.username);
                    password.setText(prompt.authOptions.password);
                }
                fields.addView(username);
                fields.addView(password);
                new AlertDialog.Builder(activity).setTitle(prompt.title)
                        .setMessage(prompt.message).setView(fields)
                        .setPositiveButton("登录", (dialog, which) -> result.complete(
                                prompt.confirm(username.getText().toString(),
                                        password.getText().toString())))
                        .setNegativeButton("取消", (dialog, which) ->
                                result.complete(prompt.dismiss()))
                        .setOnCancelListener(dialog -> result.complete(prompt.dismiss()))
                        .show();
                return result;
            }

            @Override public GeckoResult<PromptResponse> onFilePrompt(GeckoSession session,
                    FilePrompt prompt) {
                if (!isVisible(page) || pendingFilePrompt != null) {
                    return GeckoResult.fromValue(prompt.dismiss());
                }
                pendingFilePrompt = prompt;
                GeckoResult<PromptResponse> result = new GeckoResult<>();
                pendingFileResult = result;
                pendingFilePage = page;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType(prompt.mimeTypes == null || prompt.mimeTypes.length == 0
                                ? "*/*" : prompt.mimeTypes[0]);
                if (prompt.mimeTypes != null && prompt.mimeTypes.length > 1) {
                    intent.putExtra(Intent.EXTRA_MIME_TYPES, prompt.mimeTypes);
                }
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                try {
                    activity.startActivityForResult(intent, fileChooserRequestCode);
                } catch (RuntimeException error) {
                    cancelPendingFileChooser();
                    listener.onFileChooserUnavailable();
                }
                // Completion is delivered asynchronously by onActivityResult.
                return result;
            }
        });

        page.session.setPermissionDelegate(new GeckoSession.PermissionDelegate() {
            @Override public GeckoResult<Integer> onContentPermissionRequest(
                    GeckoSession session, ContentPermission permission) {
                if (!isVisible(page) || permission == null
                        || permission.permission != PERMISSION_GEOLOCATION) {
                    return GeckoResult.fromValue(ContentPermission.VALUE_DENY);
                }
                GeckoResult<Integer> result = new GeckoResult<>();
                requestSemanticPermission(page, permission.uri,
                        ReBrowserSitePermissions.APPROXIMATE_LOCATION,
                        List.of(Manifest.permission.ACCESS_COARSE_LOCATION),
                        () -> result.complete(ContentPermission.VALUE_ALLOW),
                        () -> result.complete(ContentPermission.VALUE_DENY));
                return result;
            }

            @Override public void onMediaPermissionRequest(GeckoSession session, String uri,
                    MediaSource[] video, MediaSource[] audio, MediaCallback callback) {
                if (!isVisible(page)) { callback.reject(); return; }
                MediaSource selectedVideo = video == null || video.length == 0 ? null : video[0];
                MediaSource selectedAudio = audio == null || audio.length == 0 ? null : audio[0];
                List<String> semantic = new ArrayList<>();
                List<String> androidPermissions = new ArrayList<>();
                if (selectedVideo != null) {
                    semantic.add(ReBrowserSitePermissions.CAMERA);
                    androidPermissions.add(Manifest.permission.CAMERA);
                }
                if (selectedAudio != null) {
                    semantic.add(ReBrowserSitePermissions.MICROPHONE);
                    androidPermissions.add(Manifest.permission.RECORD_AUDIO);
                }
                if (semantic.isEmpty()) { callback.reject(); return; }
                requestPermissions(page, uri, semantic, androidPermissions,
                        () -> callback.grant(selectedVideo, selectedAudio), callback::reject);
            }

            @Override public void onAndroidPermissionsRequest(GeckoSession session,
                    String[] permissions, Callback callback) {
                // Runtime permission decisions are owned by the semantic requests above.
                if (permissions == null || permissions.length == 0) callback.grant();
                else callback.reject();
            }
        });
    }

    private void requestSemanticPermission(PageBinding page, String origin, String permission,
            List<String> androidPermissions, Runnable approved, Runnable denied) {
        requestPermissions(page, origin, List.of(permission), androidPermissions,
                approved, denied);
    }

    private void requestPermissions(PageBinding page, String origin, List<String> permissions,
            List<String> androidPermissions, Runnable approved, Runnable denied) {
        for (String permission : permissions) {
            if (!sitePermissions.capabilityEnabled(permission)
                    || page.permissionPromptBlockedUntil.getOrDefault(permission, 0L)
                    > SystemClock.elapsedRealtime()) {
                denied.run();
                return;
            }
        }
        long now = System.currentTimeMillis();
        boolean existing = true;
        for (String permission : permissions) {
            existing &= sitePermissions.hasGrant(page.profileName, origin, permission, now);
        }
        if (existing && androidPermissionsGranted(androidPermissions)) {
            approved.run();
            return;
        }
        boolean sensitive = permissions.contains(ReBrowserSitePermissions.CAMERA)
                || permissions.contains(ReBrowserSitePermissions.MICROPHONE)
                || permissions.contains(ReBrowserSitePermissions.PRECISE_LOCATION);
        ReBrowserPermissionRequest[] holder = new ReBrowserPermissionRequest[1];
        ReBrowserPermissionRequest request = new ReBrowserPermissionRequest(page.profileName,
                page.tab.id, safeOrigin(origin), permissions,
                sensitive ? 0L : ReBrowserSitePermissions.FIVE_MINUTES_MS,
                sensitive ? ReBrowserSitePermissions.THREE_HOURS_MS
                        : ReBrowserSitePermissions.FIFTEEN_DAYS_MS,
                sensitive ? "仅本次" : "允许 5 分钟",
                sensitive ? "允许 3 小时" : "允许 15 天", androidPermissions,
                new ReBrowserPermissionRequest.Responder() {
                    @Override public void approve(long durationMs) {
                        pendingPermissionRequests.remove(holder[0]);
                        if (!foreground || !isVisible(page)
                                || !androidPermissionsGranted(androidPermissions)) {
                            denied.run();
                            return;
                        }
                        if (durationMs > 0L) {
                            long grantedAt = System.currentTimeMillis();
                            for (String permission : permissions) {
                                if (!sitePermissions.grant(page.profileName, origin, permission,
                                        durationMs, grantedAt)) {
                                    denied.run();
                                    return;
                                }
                            }
                        }
                        approved.run();
                    }
                    @Override public void deny(String reason) {
                        pendingPermissionRequests.remove(holder[0]);
                        if ("user-denied".equals(reason) || "user-dismissed".equals(reason)) {
                            long until = SystemClock.elapsedRealtime() + 30_000L;
                            for (String permission : permissions) {
                                page.permissionPromptBlockedUntil.put(permission, until);
                            }
                        }
                        denied.run();
                    }
                });
        holder[0] = request;
        pendingPermissionRequests.add(request);
        listener.onSitePermissionRequested(request);
    }

    private boolean androidPermissionsGranted(List<String> permissions) {
        for (String permission : permissions) {
            if (activity.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void cancelPermissionRequestsForTab(String tabId, String reason) {
        for (ReBrowserPermissionRequest request :
                new ArrayList<>(pendingPermissionRequests)) {
            if (tabId.equals(request.tabId)) request.deny(reason);
        }
    }
    private void cancelAllPermissionRequests(String reason) {
        for (ReBrowserPermissionRequest request :
                new ArrayList<>(pendingPermissionRequests)) request.deny(reason);
    }

    private PageBinding requirePage(String tabId) {
        PageBinding page = pagesByTabId.get(tabId);
        if (page == null) throw new IllegalStateException("page-unavailable");
        return page;
    }
    private boolean owns(PageBinding page, GeckoSession session) {
        return pagesBySession.get(session) == page;
    }
    private boolean isVisible(PageBinding page) {
        return foreground && visiblePage == page && page.view.getSession() == page.session;
    }
    private static Uri safeUri(String value) {
        try { return value == null ? null : Uri.parse(value); }
        catch (RuntimeException ignored) { return null; }
    }
    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
    private static String header(Map<String, String> headers, String name) {
        if (headers == null) return "";
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (name.equalsIgnoreCase(entry.getKey())) return entry.getValue();
        }
        return "";
    }
    private static long parseLong(String value) {
        try { return Long.parseLong(value); }
        catch (RuntimeException ignored) { return -1L; }
    }
    private static JSONObject jsonArgs(Object... values) {
        JSONObject result = new JSONObject();
        try {
            for (int index = 0; index + 1 < values.length; index += 2) {
                result.put(String.valueOf(values[index]), values[index + 1]);
            }
        } catch (Exception ignored) {
            return new JSONObject();
        }
        return result;
    }
    static String safeOrigin(String value) {
        try {
            Uri uri = Uri.parse(value);
            String scheme = lower(uri.getScheme());
            String host = uri.getHost();
            if (!("https".equals(scheme) || "http".equals(scheme))
                    || host == null || host.isBlank()) return "";
            int port = uri.getPort();
            return scheme + "://" + host.toLowerCase(Locale.ROOT)
                    + (port < 0 ? "" : ":" + port);
        } catch (RuntimeException ignored) { return ""; }
    }
}
