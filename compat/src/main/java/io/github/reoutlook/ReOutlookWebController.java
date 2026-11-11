package io.github.reoutlook;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.content.SharedPreferences;
import android.webkit.ValueCallback;
import android.view.View;

import org.json.JSONObject;
import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.WebExtension;
import org.mozilla.geckoview.WebRequestError;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** GeckoView adapter behind the shared ReOutlook activity and archive model. */
final class ReOutlookWebController {
    private static final String CONTEXT_ID = "rebrowser_outlook_default";
    private static final String EXTENSION_ID = "engine-bridge@rebrowser.local";
    private static final String STATE_PREFS = "outlook_gecko_session_state";
    private static final String STATE_KEY = "session_state_v1";
    private static final int MAX_STATE_LENGTH = 2_000_000;

    interface Listener {
        void onPageStarted();
        void onPageFinished();
        void onProgressChanged(int progress);
        void onMainFrameError();
        void onUserTouch();
        void onStructuredMessage(String payload, ValueCallback<String> reply);
    }

    private final Activity activity;
    private final Listener listener;
    private final int fileChooserRequestCode;
    private final GeckoRuntime runtime;
    private GeckoSession session;
    private GeckoSession.SessionState lastState;
    private GeckoSession.SessionState pendingState;
    private final SharedPreferences statePrefs;
    private final GeckoView view;
    private final Map<String, ValueCallback<JSONObject>> callbacks = new HashMap<>();
    private final ReBrowserStorageEstimate.Driver storageDriver =
            new ReBrowserStorageEstimate.Driver() {
                @Override public String url() { return ReOutlookWebController.this.url; }
                @Override public void evaluate(String script, ValueCallback<String> callback) {
                    // Storage estimates are optional and deliberately unavailable until a
                    // dedicated fixed Gecko command is added; no arbitrary script channel exists.
                    callback.onReceiveValue("null");
                }
            };
    private WebExtension extension;
    private WebExtension.Port port;
    private GeckoSession.PromptDelegate.FilePrompt pendingFilePrompt;
    private GeckoResult<GeckoSession.PromptDelegate.PromptResponse> pendingFileResult;
    private String pendingUrl;
    private String url = "";
    private long navigationGeneration;
    private long nextRequest = 1L;
    private boolean canGoBack;
    private boolean foreground;
    private boolean destroyed;
    private boolean contentLost;

    ReOutlookWebController(Activity activity, Listener listener, int fileChooserRequestCode) {
        this.activity = activity;
        this.listener = listener;
        this.fileChooserRequestCode = fileChooserRequestCode;
        runtime = ReBrowserGeckoRuntime.get(activity);
        statePrefs = activity.getSharedPreferences(STATE_PREFS, Activity.MODE_PRIVATE);
        session = newSession();
        view = new GeckoView(activity);
        view.setOnTouchListener((ignored, event) -> {
            if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
                listener.onUserTouch();
            }
            return false;
        });
        session.open(runtime);
        view.setSession(session);
        runtime.getWebExtensionController().ensureBuiltIn(
                "resource://android/assets/extensions/rebrowser/", EXTENSION_ID)
                .accept(value -> activity.runOnUiThread(() -> onExtension(value)),
                        error -> activity.runOnUiThread(listener::onMainFrameError));
    }

    View view() { return view; }
    String url() { return url; }
    boolean canGoBack() { return canGoBack; }
    void goBack() { if (canGoBack) session.goBack(); }
    void reload() {
        if (destroyed) return;
        if (contentLost) recoverSession();
        else session.reload();
    }
    boolean structuredCaptureEnabled() { return true; }
    boolean restoreState(Bundle ignored) {
        // Gecko delivers state asynchronously; flushSessionState() does not return a snapshot.
        // Keep the last completed snapshot in private app storage, not in an IPC Bundle.
        try {
            String saved = statePrefs.getString(STATE_KEY, null);
            if (saved == null || saved.length() > MAX_STATE_LENGTH) return false;
            GeckoSession.SessionState restored = GeckoSession.SessionState.fromString(saved);
            if (restored == null || restored.size() == 0) return false;
            pendingState = restored;
            lastState = restored;
            return true;
        } catch (RuntimeException invalidState) {
            statePrefs.edit().remove(STATE_KEY).apply();
            return false;
        }
    }
    void saveState(Bundle ignored) { if (!destroyed && !contentLost) session.flushSessionState(); }
    ReBrowserStorageEstimate.Driver storageDriver() { return storageDriver; }

    void loadUrl(String value) {
        if (destroyed) return;
        if (extension == null) pendingUrl = value;
        else session.loadUri(value);
    }

    void evaluate(String script, ValueCallback<String> callback) {
        String operation;
        if (CaptureScript.CURRENT_MESSAGE.equals(script)) operation = "outlook-current";
        else if (CaptureScript.SCROLL_MAIL_LIST.equals(script)) operation = "outlook-scroll";
        else {
            callback.onReceiveValue("null");
            return;
        }
        sendCommand(operation, result -> {
            if (result == null || !result.optBoolean("ok")) {
                callback.onReceiveValue("null");
                return;
            }
            Object value = result.opt("value");
            callback.onReceiveValue(value == null || value == JSONObject.NULL
                    ? "null" : JSONObject.quote(String.valueOf(value)));
        });
    }

    void onResume() {
        foreground = true;
        if (contentLost) recoverSession();
        session.setActive(true);
        session.setFocused(activity.hasWindowFocus());
    }

    void onPause() {
        foreground = false;
        session.setFocused(false);
        session.setActive(false);
        if (!contentLost) session.flushSessionState();
    }

    void onWindowFocusChanged(boolean focused) {
        if (!destroyed && foreground) session.setFocused(focused);
    }

    boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != fileChooserRequestCode || pendingFilePrompt == null) return false;
        GeckoSession.PromptDelegate.FilePrompt prompt = pendingFilePrompt;
        GeckoResult<GeckoSession.PromptDelegate.PromptResponse> result = pendingFileResult;
        pendingFilePrompt = null;
        pendingFileResult = null;
        if (resultCode != Activity.RESULT_OK || data == null) {
            result.complete(prompt.dismiss());
            return true;
        }
        List<Uri> uris = new ArrayList<>();
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int index = 0; index < clip.getItemCount(); index++) {
                Uri item = clip.getItemAt(index).getUri();
                if (item != null) uris.add(item);
            }
        } else if (data.getData() != null) uris.add(data.getData());
        result.complete(uris.isEmpty() ? prompt.dismiss()
                : prompt.confirm(activity, uris.toArray(new Uri[0])));
        return true;
    }

    void destroy() {
        if (destroyed) return;
        destroyed = true;
        cancelCallbacks();
        if (pendingFilePrompt != null && pendingFileResult != null) {
            pendingFileResult.complete(pendingFilePrompt.dismiss());
        }
        pendingFilePrompt = null;
        pendingFileResult = null;
        if (port != null) port.disconnect();
        port = null;
        if (view.getSession() != null) view.releaseSession();
        session.close();
    }

    private GeckoSession newSession() {
        GeckoSession created = new GeckoSession(new GeckoSessionSettings.Builder()
                .contextId(CONTEXT_ID)
                .allowJavascript(true)
                .suspendMediaWhenInactive(true)
                .build());
        installDelegates(created);
        return created;
    }

    private void onContentLost(GeckoSession lost, String reason) {
        if (destroyed || lost != session || contentLost) return;
        contentLost = true;
        navigationGeneration++;
        cancelCallbacks();
        if (port != null) port.disconnect();
        port = null;
        if (BuildConfig.DEBUG) android.util.Log.w("ReOutlookSession", reason);
        if (foreground) recoverSession();
    }

    private void recoverSession() {
        if (destroyed || !contentLost) return;
        GeckoSession old = session;
        // A killed content process cannot be repaired by reloading the same Session.
        if (view.getSession() != null) view.releaseSession();
        old.close();
        session = newSession();
        session.open(runtime);
        view.setSession(session);
        if (extension != null) registerExtensionDelegate();
        session.setActive(foreground);
        session.setFocused(foreground && activity.hasWindowFocus());
        contentLost = false;
        pendingState = lastState;
        String fallback = MainActivity.isOutlookMailOrigin(url) ? url : "https://outlook.office.com/mail/";
        if (extension == null) pendingUrl = fallback;
        else restoreOrLoad(fallback);
        if (BuildConfig.DEBUG) android.util.Log.i("ReOutlookSession", "session-recreated");
    }

    private void restoreOrLoad(String fallback) {
        GeckoSession.SessionState restored = pendingState;
        pendingState = null;
        if (restored != null && restored.size() > 0) {
            try {
                session.restoreState(restored);
                if (BuildConfig.DEBUG) android.util.Log.i("ReOutlookSession", "state-restored");
                return;
            } catch (RuntimeException invalidState) {
                lastState = null;
                statePrefs.edit().remove(STATE_KEY).apply();
            }
        }
        session.loadUri(fallback);
        if (BuildConfig.DEBUG) android.util.Log.i("ReOutlookSession", "fallback-loaded");
    }

    private void onExtension(WebExtension value) {
        if (destroyed) return;
        extension = value;
        registerExtensionDelegate();
        if (pendingUrl != null) {
            String valueUrl = pendingUrl;
            pendingUrl = null;
            restoreOrLoad(valueUrl);
        } else if (pendingState != null) {
            restoreOrLoad("https://outlook.office.com/mail/");
        }
    }

    private void registerExtensionDelegate() {
        session.getWebExtensionController().setMessageDelegate(extension,
                new WebExtension.MessageDelegate() {
                    @Override public void onConnect(WebExtension.Port candidate) {
                        attachPort(candidate);
                    }
                }, "browser");
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
        if (BuildConfig.DEBUG) android.util.Log.d("ReOutlookCapture", "session-port-connected");
        candidate.setDelegate(new WebExtension.PortDelegate() {
            @Override public void onPortMessage(Object message, WebExtension.Port source) {
                if (source != port || destroyed) return;
                JSONObject value = jsonObject(message);
                String type = value.optString("type");
                if ("command-result".equals(type)) {
                    ValueCallback<JSONObject> callback = callbacks.remove(value.optString("id"));
                    if (callback != null) callback.onReceiveValue(value);
                } else if ("outlook".equals(type)) {
                    handleStructuredMessage(value.optString("payload"));
                }
            }
            @Override public void onDisconnect(WebExtension.Port source) {
                if (source != port) return;
                port = null;
                cancelCallbacks();
            }
        });
    }

    private void handleStructuredMessage(String payload) {
        if (!foreground || !MainActivity.isOutlookMailOrigin(url)
                || payload == null || payload.length() > 1_800_000) return;
        if (BuildConfig.DEBUG) {
            try {
                String type = new JSONObject(payload).optString("type");
                if (java.util.Set.of("account", "mail", "candidates", "conversationComplete",
                        "conversationDiscard").contains(type)) {
                    android.util.Log.d("ReOutlookCapture", "structured-event=" + type);
                }
            } catch (Exception ignored) { /* Never log page payloads or account identifiers. */ }
        }
        long generation = navigationGeneration;
        WebExtension.Port source = port;
        listener.onStructuredMessage(payload, reply -> {
            if (destroyed || !foreground || port != source
                    || navigationGeneration != generation
                    || !MainActivity.isOutlookMailOrigin(url)) return;
            try {
                source.postMessage(new JSONObject().put("type", "outlook-result")
                        .put("payload", reply == null ? "" : reply));
            } catch (Exception ignored) {
                // Navigation invalidated the fixed page bridge.
            }
        });
    }

    private void sendCommand(String operation, ValueCallback<JSONObject> callback) {
        if (!foreground || port == null || !MainActivity.isOutlookMailOrigin(url)) {
            callback.onReceiveValue(null);
            return;
        }
        String id = Long.toString(nextRequest++);
        try {
            callbacks.put(id, callback);
            port.postMessage(new JSONObject().put("type", "command")
                    .put("id", id).put("operation", operation));
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

    private void installDelegates(GeckoSession session) {
        session.setContentDelegate(new GeckoSession.ContentDelegate() {
            @Override public void onKill(GeckoSession lost) { onContentLost(lost, "content-killed"); }
            @Override public void onCrash(GeckoSession lost) { onContentLost(lost, "content-crashed"); }
        });
        session.setNavigationDelegate(new GeckoSession.NavigationDelegate() {
            @Override public GeckoResult<AllowOrDeny> onLoadRequest(GeckoSession session,
                    LoadRequest request) {
                Uri uri = Uri.parse(request.uri == null ? "" : request.uri);
                String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
                if ("https".equals(scheme) || "http".equals(scheme)
                        || "about".equals(scheme)) {
                    if (request.target == TARGET_WINDOW_NEW && request.uri != null) {
                        activity.runOnUiThread(() -> session.loadUri(request.uri));
                        return GeckoResult.deny();
                    }
                    return GeckoResult.allow();
                }
                try { activity.startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
                catch (RuntimeException ignored) { /* Unsupported external scheme. */ }
                return GeckoResult.deny();
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
                    String url, WebRequestError error) {
                listener.onMainFrameError();
                return null;
            }
        });
        session.setProgressDelegate(new GeckoSession.ProgressDelegate() {
            @Override public void onPageStart(GeckoSession session, String value) {
                url = value == null ? "" : value;
                navigationGeneration++;
                cancelCallbacks();
                ReBrowserStorageEstimate.invalidate(activity, ReBrowserApplications.OUTLOOK);
                listener.onPageStarted();
            }
            @Override public void onProgressChange(GeckoSession session, int progress) {
                listener.onProgressChanged(progress);
            }
            @Override public void onPageStop(GeckoSession session, boolean success) {
                if (success) listener.onPageFinished();
            }
            @Override public void onSessionStateChange(GeckoSession source,
                    GeckoSession.SessionState state) {
                if (destroyed || source != ReOutlookWebController.this.session || state == null
                        || state.size() == 0) return;
                try {
                    String serialized = state.toString();
                    if (serialized == null || serialized.length() > MAX_STATE_LENGTH) return;
                    lastState = new GeckoSession.SessionState(state);
                    statePrefs.edit().putString(STATE_KEY, serialized).apply();
                } catch (RuntimeException invalidState) {
                    // Never replace a valid snapshot with an incomplete or invalid one.
                }
            }
        });
        session.setPromptDelegate(new GeckoSession.PromptDelegate() {
            @Override public GeckoResult<PromptResponse> onFilePrompt(GeckoSession session,
                    FilePrompt prompt) {
                if (!foreground || pendingFilePrompt != null) {
                    return GeckoResult.fromValue(prompt.dismiss());
                }
                pendingFilePrompt = prompt;
                GeckoResult<PromptResponse> result = new GeckoResult<>();
                pendingFileResult = result;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType(prompt.mimeTypes == null || prompt.mimeTypes.length == 0
                                ? "*/*" : prompt.mimeTypes[0]);
                if (prompt.mimeTypes != null && prompt.mimeTypes.length > 1) {
                    intent.putExtra(Intent.EXTRA_MIME_TYPES, prompt.mimeTypes);
                }
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                try { activity.startActivityForResult(intent, fileChooserRequestCode); }
                catch (RuntimeException error) {
                    pendingFilePrompt = null;
                    pendingFileResult = null;
                    result.complete(prompt.dismiss());
                }
                return result;
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

    private static JSONObject jsonObject(Object value) {
        if (value instanceof JSONObject) return (JSONObject) value;
        if (value instanceof Map) return new JSONObject((Map<?, ?>) value);
        try { return new JSONObject(String.valueOf(value)); }
        catch (Exception ignored) { return new JSONObject(); }
    }
}
