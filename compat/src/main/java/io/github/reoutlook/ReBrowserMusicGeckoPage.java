package io.github.reoutlook;

import android.app.Activity;
import android.content.Context;
import android.content.MutableContextWrapper;
import android.net.Uri;
import android.view.ViewGroup;
import android.webkit.ValueCallback;

import org.json.JSONObject;
import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.WebExtension;
import org.mozilla.geckoview.WebRequestError;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Fixed-command Gecko page used by the service-owned music runtime. */
final class ReBrowserMusicGeckoPage {
    private static final String EXTENSION_ID = "engine-bridge@rebrowser.local";
    interface Listener {
        void pageStarted(String url);
        void pageFinished(String url);
        void mainFrameError(String url);
        void bridgeReady();
    }

    private final Context application;
    private final MutableContextWrapper context;
    private final Listener listener;
    private final GeckoSession session;
    private final GeckoView view;
    private final Map<String, ValueCallback<JSONObject>> callbacks = new HashMap<>();
    private final ReBrowserStorageEstimate.Driver storageDriver =
            new ReBrowserStorageEstimate.Driver() {
                @Override public String url() { return ReBrowserMusicGeckoPage.this.url; }
                @Override public void evaluate(String script, ValueCallback<String> callback) {
                    callback.onReceiveValue("null");
                }
            };
    private WebExtension extension;
    private WebExtension.Port port;
    private String url = "";
    private String pendingUrl;
    private long nextRequest = 1;
    private boolean canGoBack;
    private boolean destroyed;
    private boolean playerAllowed;
    private boolean portrait;

    ReBrowserMusicGeckoPage(Context owner, Listener listener) {
        application = owner.getApplicationContext();
        context = new MutableContextWrapper(application);
        this.listener = listener;
        session = new GeckoSession(new GeckoSessionSettings.Builder()
                .contextId(ReBrowserMusicPolicy.PROFILE)
                .allowJavascript(true)
                .suspendMediaWhenInactive(false)
                .build());
        view = new GeckoView(context);
        installDelegates();
        session.open(ReBrowserGeckoRuntime.get(application));
        ReBrowserGeckoRuntime.get(application).getWebExtensionController().ensureBuiltIn(
                "resource://android/assets/extensions/rebrowser/", EXTENSION_ID)
                .accept(value -> new android.os.Handler(android.os.Looper.getMainLooper())
                                .post(() -> onExtension(value)),
                        error -> new android.os.Handler(android.os.Looper.getMainLooper())
                                .post(() -> listener.mainFrameError(url)));
    }

    String url() { return url; }
    boolean canGoBack() { return canGoBack; }
    ReBrowserStorageEstimate.Driver storageDriver() { return storageDriver; }

    void attach(Activity activity, ViewGroup host) {
        if (destroyed) return;
        context.setBaseContext(activity);
        if (view.getParent() instanceof ViewGroup) {
            ((ViewGroup) view.getParent()).removeView(view);
        }
        if (view.getSession() == null) view.setSession(session);
        host.addView(view, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        session.setActive(true);
        session.setFocused(activity.hasWindowFocus());
    }

    void detach(Activity activity) {
        if (view.getParent() instanceof ViewGroup) {
            ((ViewGroup) view.getParent()).removeView(view);
        }
        if (view.getSession() != null) view.releaseSession();
        session.setFocused(false);
        context.setBaseContext(application);
        // The service owns playback; detaching UI must not suspend media.
        session.setActive(true);
    }

    void configure(boolean desktop, boolean adapted, boolean player) {
        playerAllowed = player;
        portrait = adapted;
        session.getSettings().setUserAgentMode(desktop
                ? GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
                : GeckoSessionSettings.USER_AGENT_MODE_MOBILE);
        session.getSettings().setUserAgentOverride(
                desktop ? ReBrowserMusicPolicy.DESKTOP_UA : null);
        // Desktop UA selects the real WebPlayer, not its display geometry.
        // Match WebView's !player || !adapted wide-viewport policy independently.
        session.getSettings().setViewportMode(viewportMode(desktop, adapted, player));
        if (port != null && ReBrowserMusicPolicy.playerDocument(url)) activatePolicy();
    }

    static int viewportMode(boolean desktop, boolean adapted, boolean player) {
        return desktop && (!player || !adapted)
                ? GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
                : GeckoSessionSettings.VIEWPORT_MODE_MOBILE;
    }

    void load(String value) {
        if (destroyed) return;
        if (extension == null) pendingUrl = value;
        else session.loadUri(value);
    }

    void stopLoading() { session.stop(); }
    void clearHistory() { /* Gecko has no mutable public history API; back() is policy-bounded. */ }
    void goBack() { if (canGoBack) session.goBack(); }

    void probe(String operation, ValueCallback<String> callback) {
        command(operation, null, callback);
    }

    void mediaCommand(String kind, long position, String track, ValueCallback<String> callback) {
        JSONObject args = new JSONObject();
        try { args.put("kind", kind).put("position", position).put("track", track); }
        catch (Exception ignored) {}
        command("music-command", args, callback);
    }

    void lyrics(String track, ValueCallback<String> callback) {
        JSONObject args = new JSONObject();
        try { args.put("track", track); } catch (Exception ignored) {}
        command("music-lyrics", args, callback);
    }

    void back(ValueCallback<String> callback) { command("music-back", null, callback); }
    void recheck() { command("music-recheck", null, ignored -> {}); }
    void setPortrait(boolean value) {
        portrait = value;
        JSONObject args = new JSONObject();
        try { args.put("value", value); } catch (Exception ignored) {}
        command("music-portrait", args, ignored -> {});
    }

    void close() {
        if (destroyed) return;
        destroyed = true;
        cancelCallbacks();
        if (port != null) port.disconnect();
        port = null;
        if (view.getParent() instanceof ViewGroup) {
            ((ViewGroup) view.getParent()).removeView(view);
        }
        if (view.getSession() != null) view.releaseSession();
        session.close();
        context.setBaseContext(application);
    }

    private void onExtension(WebExtension value) {
        if (destroyed) return;
        extension = value;
        session.getWebExtensionController().setMessageDelegate(value,
                new WebExtension.MessageDelegate() {
                    @Override public void onConnect(WebExtension.Port candidate) {
                        attachPort(candidate);
                    }
                }, "browser");
        if (pendingUrl != null) {
            String valueUrl = pendingUrl;
            pendingUrl = null;
            session.loadUri(valueUrl);
        }
    }

    private void attachPort(WebExtension.Port candidate) {
        WebExtension.MessageSender sender = candidate == null ? null : candidate.sender;
        if (sender == null || sender.session != session || sender.webExtension == null
                || extension == null || !EXTENSION_ID.equals(sender.webExtension.id)
                || sender.environmentType
                != WebExtension.MessageSender.ENV_TYPE_CONTENT_SCRIPT
                || !sender.isTopLevel()) {
            if (candidate != null) candidate.disconnect();
            return;
        }
        if (port != null && port != candidate) port.disconnect();
        port = candidate;
        candidate.setDelegate(new WebExtension.PortDelegate() {
            @Override public void onPortMessage(Object message, WebExtension.Port source) {
                if (source != port) return;
                JSONObject value = jsonObject(message);
                if (!"command-result".equals(value.optString("type"))) return;
                ValueCallback<JSONObject> callback = callbacks.remove(value.optString("id"));
                if (callback != null) callback.onReceiveValue(value);
            }
            @Override public void onDisconnect(WebExtension.Port source) {
                if (source != port) return;
                port = null;
                cancelCallbacks();
            }
        });
        if (ReBrowserMusicPolicy.playerDocument(url)) activatePolicy();
        listener.bridgeReady();
    }

    private void activatePolicy() {
        if (playerAllowed) command("music-activate", null, ignored -> {});
        setPortrait(portrait);
    }

    private void command(String operation, JSONObject arguments,
            ValueCallback<String> callback) {
        commandRaw(operation, arguments, result -> {
            if (callback == null) return;
            if (result == null || !result.optBoolean("ok")) {
                callback.onReceiveValue("null");
                return;
            }
            Object value = result.opt("value");
            if (value == null || value == JSONObject.NULL) callback.onReceiveValue("null");
            else if (value instanceof String) callback.onReceiveValue(JSONObject.quote((String) value));
            else callback.onReceiveValue(String.valueOf(value));
        });
    }

    private void commandRaw(String operation, JSONObject arguments,
            ValueCallback<JSONObject> callback) {
        String pageOrigin = origin(url);
        if (destroyed || port == null || !("https://music.163.com".equals(pageOrigin)
                || "https://y.music.163.com".equals(pageOrigin))) {
            callback.onReceiveValue(null);
            return;
        }
        String id = Long.toString(nextRequest++);
        try {
            JSONObject message = new JSONObject().put("type", "command")
                    .put("id", id).put("operation", operation);
            if (arguments != null) {
                java.util.Iterator<String> keys = arguments.keys();
                while (keys.hasNext()) {
                    String key = keys.next(); message.put(key, arguments.opt(key));
                }
            }
            callbacks.put(id, callback);
            port.postMessage(message);
        } catch (Exception error) {
            callbacks.remove(id);
            callback.onReceiveValue(null);
        }
    }

    private void cancelCallbacks() {
        for (ValueCallback<JSONObject> callback : new ArrayList<>(callbacks.values())) {
            callback.onReceiveValue(null);
        }
        callbacks.clear();
    }

    private void installDelegates() {
        session.setNavigationDelegate(new GeckoSession.NavigationDelegate() {
            @Override public GeckoResult<AllowOrDeny> onLoadRequest(GeckoSession session,
                    LoadRequest request) {
                return ReBrowserMusicPolicy.allowsNavigation(request.uri)
                        ? GeckoResult.allow() : GeckoResult.deny();
            }
            @Override public void onLocationChange(GeckoSession session, String value,
                    List<GeckoSession.PermissionDelegate.ContentPermission> permissions,
                    Boolean hasUserGesture) {
                if (value != null) url = value;
            }
            @Override public void onCanGoBack(GeckoSession session, boolean value) {
                canGoBack = value;
            }
            @Override public GeckoResult<String> onLoadError(GeckoSession session,
                    String value, WebRequestError error) {
                listener.mainFrameError(value); return null;
            }
        });
        session.setProgressDelegate(new GeckoSession.ProgressDelegate() {
            @Override public void onPageStart(GeckoSession session, String value) {
                url = value == null ? "" : value;
                cancelCallbacks();
                ReBrowserStorageEstimate.invalidate(application, ReBrowserApplications.MUSIC);
                listener.pageStarted(url);
            }
            @Override public void onPageStop(GeckoSession session, boolean success) {
                if (success) listener.pageFinished(url);
            }
        });
        session.setPermissionDelegate(new GeckoSession.PermissionDelegate() {
            @Override public GeckoResult<Integer> onContentPermissionRequest(
                    GeckoSession session, ContentPermission permission) {
                return GeckoResult.fromValue(ContentPermission.VALUE_DENY);
            }
            @Override public void onMediaPermissionRequest(GeckoSession session, String uri,
                    MediaSource[] video, MediaSource[] audio, MediaCallback callback) {
                callback.reject();
            }
        });
    }

    private static String origin(String value) {
        try {
            Uri uri = Uri.parse(value); return uri.getScheme() + "://" + uri.getHost();
        } catch (RuntimeException error) { return ""; }
    }

    private static JSONObject jsonObject(Object value) {
        if (value instanceof JSONObject) return (JSONObject) value;
        if (value instanceof Map) return new JSONObject((Map<?, ?>) value);
        try { return new JSONObject(String.valueOf(value)); }
        catch (Exception ignored) { return new JSONObject(); }
    }
}
