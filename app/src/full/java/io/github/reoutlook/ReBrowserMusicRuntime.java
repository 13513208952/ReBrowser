package io.github.reoutlook;

import android.content.Context;
import android.content.MutableContextWrapper;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import androidx.webkit.ScriptHandler;
import androidx.webkit.WebViewCompat;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.json.JSONObject;

/** One service-owned WebPlayer. All methods and WebView operations run on the main thread. */
final class ReBrowserMusicRuntime {
    interface Listener {
        void changed(ReBrowserMusicSnapshot snapshot);
        void stageChanged();
        void loading();
        void failure(String reason);
        default void lyricsInvalidated() {}
        default void seekResult(String result) {}
        default void seekObserved(int completed, int failed) {}
        default void engineObserved(boolean known, boolean paused, boolean seeking, int ready, int network, int error, boolean advancing, String play) {}
    }
    private final Context application;
    private final MutableContextWrapper context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Listener listener;
    final ReBrowserMusicLifecycle lifecycle = new ReBrowserMusicLifecycle();
    private final String providerSource, layoutSource, accountSource;
    private final ReBrowserMusicAccountCheck accountCheck = new ReBrowserMusicAccountCheck();
    private Runnable accountTimeout, initializationTimeout;
    private final ReBrowserMusicInitialization initialization = new ReBrowserMusicInitialization();
    private final WebView web;
    private ScriptHandler providerScript, layoutScript;
    private int document;
    private final ReBrowserMusicEvaluation polling = new ReBrowserMusicEvaluation();
    private final ReBrowserMusicEvaluation commanding = new ReBrowserMusicEvaluation();
    private final ReBrowserMusicEvaluation backing = new ReBrowserMusicEvaluation();
    private final ReBrowserMusicEvaluation lyricReading = new ReBrowserMusicEvaluation();
    private String queuedCommand, queuedTrack;
    private long queuedPosition;
    private boolean disposed, adapted = true, ready, loginVisible, providerFailureReported;
    private final ReBrowserMusicPreparation coldPause = new ReBrowserMusicPreparation();
    private static final String LOGIN_PROBE = "JSON.stringify({loginVisible:(()=>{"
            + "const e=document.querySelector('.u-login-page-wrapper,#login-wrapper .n-log2,#login-wrapper iframe');"
            + "return !!e&&e.getClientRects().length>0&&getComputedStyle(e).visibility!=='hidden';})()})";
    private static final String STATE_PROBE = "JSON.stringify((()=>{"
            + "const s=window.__rbNeteaseProviderV1?.state()||{auth:'pending'};"
            + "s.layoutReady=window.__rbMusicLayoutHostV1?.ready()===true;return s;})())";
    private ReBrowserMusicSnapshot snapshot = ReBrowserMusicSnapshot.empty();
    private final Runnable tick = this::poll;

    ReBrowserMusicRuntime(Context owner, Listener listener) throws Exception {
        application = owner.getApplicationContext(); context = new MutableContextWrapper(application);
        this.listener = listener;
        providerSource = resource(application, "rebrowser_netease_provider.js");
        layoutSource = resource(application, "rebrowser_netease_layout.js");
        accountSource = resource(application, "rebrowser_netease_account.js");
        web = new WebView(context);
        try {
        WebViewCompat.setProfile(web, ReBrowserMusicPolicy.PROFILE);
        android.webkit.WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true);
        WebViewCompat.getProfile(web).getCookieManager().setAcceptThirdPartyCookies(web,
                new ReBrowserPreferences(application).thirdPartyCookiesEnabled());
        settings.setAllowFileAccess(false); settings.setAllowContentAccess(false);
        settings.setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSafeBrowsingEnabled(true); settings.setGeolocationEnabled(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false); settings.setSupportMultipleWindows(false);
        WebViewCompat.addDocumentStartJavaScript(web, ReBrowserPermissionScript.DOCUMENT_START, Set.of("*"));
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, android.webkit.WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                String url = request.getUrl().toString(), expected = accountCheck.request();
                if (expected != null && !expected.equals(url)) {
                    failAccount(expected); return true;
                }
                return !ReBrowserMusicPolicy.allowsNavigation(url);
            }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
                ReBrowserStorageEstimate.navigating(application, ReBrowserApplications.MUSIC, view);
                document++; resetEvaluations(); ready = false; coldPause.reset(); loginVisible = false;
                view.getSettings().setMediaPlaybackRequiresUserGesture(true);
                if (!ReBrowserMusicPolicy.allowsNavigation(url)) { view.stopLoading(); return; }
                String expected = accountCheck.request();
                if (expected != null && !expected.equals(url)) {
                    view.stopLoading(); failAccount(expected); return;
                }
                accountCheck.started(url);
                // Only the official login callback returning HOME triggers this; never interrupt login input.
                if (lifecycle.stage() == ReBrowserMusicLifecycle.Stage.LOGIN && ReBrowserMusicPolicy.playerDocument(url)) {
                    lifecycle.recheck(); accountCheck.restart(); view.stopLoading();
                    handler.post(() -> { if (!disposed) { load(); listener.stageChanged(); } }); return;
                }
                if (ReBrowserMusicPolicy.loginDocument(url) && lifecycle.stage() != ReBrowserMusicLifecycle.Stage.LOGIN) {
                    lifecycle.account(false); snapshot = ReBrowserMusicSnapshot.empty(); listener.changed(snapshot);
                    // An unsolicited login redirect must use the default official mobile entry.
                    view.stopLoading(); handler.post(() -> { if (!disposed) load(); });
                }
                listener.stageChanged();
            }
            @Override public void onPageFinished(WebView view, String url) {
                if ((url != null && url.equals(view.getUrl()) && accountCheck.finished(url))
                        || ReBrowserMusicPolicy.playerDocument(url)) wakePoll();
            }
            @Override public void onReceivedError(WebView view, android.webkit.WebResourceRequest request,
                    android.webkit.WebResourceError error) {
                if (request.isForMainFrame()) {
                    failAccount(request.getUrl().toString());
                }
            }
            @Override public void onReceivedHttpError(WebView view, android.webkit.WebResourceRequest request,
                    android.webkit.WebResourceResponse response) {
                if (request.isForMainFrame()) {
                    failAccount(request.getUrl().toString());
                }
            }
            @Override public void onReceivedSslError(WebView view, android.webkit.SslErrorHandler h,
                    android.net.http.SslError error) {
                h.cancel(); failAccount(error.getUrl());
            }
            @Override public boolean onRenderProcessGone(WebView view, android.webkit.RenderProcessGoneDetail detail) {
                close(); listener.failure("音乐网页进程已结束，请重新进入；不会自动恢复播放。"); return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onPermissionRequest(android.webkit.PermissionRequest request) { request.deny(); }
            @Override public void onGeolocationPermissionsShowPrompt(String origin,
                    android.webkit.GeolocationPermissions.Callback callback) { callback.invoke(origin, false, false); }
            @Override public boolean onShowFileChooser(WebView view,
                    android.webkit.ValueCallback<android.net.Uri[]> callback, FileChooserParams params) {
                callback.onReceiveValue(null); return true;
            }
        });
        web.setDownloadListener((url, agent, disposition, type, length) -> {
            if (accountCheck.active(url)) failAccount(url);
            else listener.failure("音乐模式不支持下载");
        });
        ReBrowserStorageEstimate.register(application, ReBrowserApplications.MUSIC, web);
        load();
        handler.post(tick);
        } catch (RuntimeException | LinkageError failure) {
            ReBrowserStorageEstimate.unregister(application, ReBrowserApplications.MUSIC, web);
            web.destroy(); throw failure;
        }
    }
    static String resource(Context context, String name) throws Exception {
        try (java.io.InputStream input = context.getAssets().open(name)) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096]; int n;
            while ((n = input.read(buffer)) != -1) {
                if (out.size() + n > 65_536) throw new IllegalStateException("music-resource-too-large");
                out.write(buffer, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
    void attach(android.app.Activity activity, ViewGroup host) {
        if (disposed) return;
        context.setBaseContext(activity);
        if (web.getParent() instanceof ViewGroup) ((ViewGroup) web.getParent()).removeView(web);
        host.addView(web, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }
    void detach(android.app.Activity activity) {
        if (context.getBaseContext() != activity) return;
        if (web.getParent() instanceof ViewGroup) ((ViewGroup) web.getParent()).removeView(web);
        context.setBaseContext(application);
    }
    boolean ready() { return ready && !disposed; }
    boolean player() { return lifecycle.player(); }
    boolean loginVisible() { return !disposed && lifecycle.presentLogin(web.getUrl(), loginVisible); }
    ReBrowserMusicSnapshot snapshot() { return snapshot; }
    void window(boolean tablet, boolean full, boolean landscape) {
        boolean reload = lifecycle.window(tablet, full, landscape);
        adapted = !ReBrowserMusicPolicy.originalLayout(tablet, full, landscape);
        if (!disposed) {
            web.getSettings().setUseWideViewPort(!player() || !adapted);
            web.getSettings().setLoadWithOverviewMode(lifecycle.desktop() && !adapted);
        }
        if (reload) { load(); listener.stageChanged(); }
        else if (!disposed) {
            scripts();
            if (player() && ReBrowserMusicPolicy.playerDocument(web.getUrl())) web.evaluateJavascript(
                    "window.__rbMusicLayoutHostV1?.setPortrait(" + adapted + ");", null);
        }
    }
    void loginUa(boolean desktop) {
        if (lifecycle.loginUa(desktop)) { load(); listener.stageChanged(); }
    }
    void checkLogin() {
        if (disposed) return;
        // Explicit retry never infers authentication from login DOM or stored credentials.
        if (lifecycle.recheck() || lifecycle.stage() == ReBrowserMusicLifecycle.Stage.CHECKING) {
            accountCheck.restart(); load(); listener.stageChanged();
        }
        else if (player() && !ready) { load(); listener.stageChanged(); }
        else if (ReBrowserMusicPolicy.playerDocument(web.getUrl())) {
            web.evaluateJavascript("window.__rbNeteaseProviderV1?.recheck();", null);
        }
    }
    private void scripts() {
        if (providerScript != null) providerScript.remove();
        if (layoutScript != null) { layoutScript.remove(); layoutScript = null; }
        providerScript = WebViewCompat.addDocumentStartJavaScript(web,
                providerSource.replace("__PLAYER_ALLOWED__", Boolean.toString(player())), Set.of("https://music.163.com"));
        if (player()) layoutScript = WebViewCompat.addDocumentStartJavaScript(web,
                layoutSource.replace("__INITIAL_PORTRAIT__", Boolean.toString(adapted)), Set.of("https://music.163.com"));
    }
    private void resetEvaluations() {
        polling.reset(); commanding.reset(); backing.reset(); lyricReading.reset(); queuedCommand = queuedTrack = null;
        providerFailureReported = false; listener.lyricsInvalidated(); listener.seekObserved(0, 0);
        listener.engineObserved(false, true, false, -1, -1, -1, false, "none");
    }
    private void load() {
        if (disposed) return;
        document++; resetEvaluations(); ready = false; coldPause.reset(); loginVisible = false;
        if (accountTimeout != null) handler.removeCallbacks(accountTimeout);
        accountTimeout = null;
        if (initializationTimeout != null) handler.removeCallbacks(initializationTimeout);
        long initializationToken = initialization.start();
        initializationTimeout = () -> {
            if (!disposed && initialization.expire(initializationToken, ready,
                    lifecycle.stage() == ReBrowserMusicLifecycle.Stage.LOGIN)) {
                handler.removeCallbacks(tick);
                listener.failure("音乐初始化暂未完成，已停止追加检查；点“重新检查”重新载入，登录会保留。");
            }
        };
        handler.postDelayed(initializationTimeout, ReBrowserMusicInitialization.TIMEOUT_MS);
        boolean checking = lifecycle.stage() == ReBrowserMusicLifecycle.Stage.CHECKING;
        String probe = accountCheck.begin(checking);
        web.stopLoading(); scripts();
        web.getSettings().setMediaPlaybackRequiresUserGesture(true);
        web.getSettings().setUserAgentString(lifecycle.desktop() ? ReBrowserMusicPolicy.DESKTOP_UA
                : ReBrowserMusicPolicy.mobileUserAgent(android.webkit.WebSettings.getDefaultUserAgent(application)));
        web.getSettings().setUseWideViewPort(!player() || !adapted);
        web.getSettings().setLoadWithOverviewMode(lifecycle.desktop() && !adapted);
        web.getSettings().setCacheMode(probe != null ? android.webkit.WebSettings.LOAD_NO_CACHE
                : android.webkit.WebSettings.LOAD_DEFAULT);
        web.loadUrl(probe != null ? probe : checking ? ReBrowserMusicPolicy.HOME : lifecycle.destination());
        if (probe != null) {
            accountTimeout = () -> fallbackAccount(probe);
            handler.postDelayed(accountTimeout, ReBrowserMusicAccountCheck.TIMEOUT_MS);
        }
        listener.loading();
    }
    private void failAccount(String expected) {
        if (!accountCheck.active(expected)) return;
        // Reject late reads immediately, before the deferred navigation runs.
        accountCheck.fail(expected); handler.post(() -> fallbackAccount(expected));
    }
    private void fallbackAccount(String expected) {
        if (disposed || lifecycle.stage() != ReBrowserMusicLifecycle.Stage.CHECKING || !accountCheck.fallback(expected)) return;
        // One old-path retry in this check, without exporting credentials or guessing login failure.
        load(); listener.stageChanged();
    }
    private void wakePoll() { wakePoll(0); }
    private void wakePoll(long delay) {
        if (disposed || initialization.expired()) return;
        // A hint advances only the next fixed read; it never releases an outstanding callback slot.
        handler.removeCallbacks(tick); handler.postDelayed(tick, delay);
    }
    private void poll() {
        if (disposed || initialization.expired()) return;
        handler.postDelayed(tick, 750);
        boolean loginDocument = ReBrowserMusicPolicy.loginDocument(web.getUrl());
        final String expectedAccount = accountCheck.request();
        boolean accountDocument = accountCheck.active(web.getUrl());
        if (accountDocument && !accountCheck.readable(web.getUrl())) return;
        if (!accountDocument && !loginDocument && !ReBrowserMusicPolicy.playerDocument(web.getUrl())) return;
        final int epoch = document, operation = polling.begin();
        if (operation == 0) return;
        Runnable timeout = () -> {
            if (!disposed && document == epoch && polling.timeout(operation)) {
                if (accountDocument) fallbackAccount(expectedAccount);
                else listener.failure("音乐网页暂未响应，已停止追加检查请求；退出重进会保留登录且保持暂停。");
            }
        };
        handler.postDelayed(timeout, 2500);
        web.evaluateJavascript(accountDocument ? accountSource : loginDocument ? LOGIN_PROBE : STATE_PROBE, value -> {
            if (disposed || document != epoch || !polling.finish(operation)) return;
            handler.removeCallbacks(timeout);
            if (initialization.expired()) return;
            if (accountDocument && !accountCheck.readable(web.getUrl())) return;
            try {
                Object decoded = new org.json.JSONTokener(value).nextValue();
                if (!(decoded instanceof String) || ((String) decoded).length() > 8192) return;
                JSONObject state = new JSONObject((String) decoded);
                if (loginDocument) {
                    loginVisible = state.optBoolean("loginVisible", false);
                    listener.stageChanged(); return;
                }
                if (state.optBoolean("unavailable") && !providerFailureReported) {
                    providerFailureReported = true;
                    listener.failure("官方播放器暂未能初始化，已停止重试；检查网络后可重新检查。");
                }
                String auth = state.optString("auth");
                if (accountDocument && !"yes".equals(auth) && !"no".equals(auth)) {
                    fallbackAccount(expectedAccount); return;
                }
                if ("yes".equals(auth) || "no".equals(auth)) {
                    boolean changed = lifecycle.account("yes".equals(auth));
                    if (changed || "no".equals(auth)) {
                        snapshot = ReBrowserMusicSnapshot.empty(); listener.changed(snapshot);
                        load(); listener.stageChanged(); return;
                    }
                }
                ReBrowserMusicSnapshot observed = ReBrowserMusicSnapshot.parse(value);
                if (player() && observed.ready) {
                    listener.seekObserved(state.optInt("seekCompleted", 0), state.optInt("seekFailed", 0));
                    JSONObject engine = state.optJSONObject("engine");
                    if (engine != null) listener.engineObserved(engine.optBoolean("known"), engine.optBoolean("paused"),
                            engine.optBoolean("seeking"), engine.optInt("readyState", -1), engine.optInt("networkState", -1),
                            engine.optInt("errorCode", -1), engine.optBoolean("advancing"), engine.optString("playResult", "other"));
                    if (!coldPause.acknowledged()) {
                        if (coldPause.request()) {
                            web.clearHistory(); // UA/login transitions are not music-page Back destinations.
                            command("pause", 0, "");
                        }
                        return;
                    }
                    if (!ready && !coldPause.paused(observed.playing, observed.buffering)) return;
                    if (!ready && adapted && !state.optBoolean("layoutReady")) {
                        wakePoll(100); return;
                    }
                    ready = true; snapshot = observed; listener.changed(snapshot); listener.stageChanged();
                } else if (!player()) listener.stageChanged();
            } catch (Exception invalid) {
                if (accountDocument) fallbackAccount(expectedAccount);
                // Untrusted/unavailable state is never a login confirmation.
            }
        });
    }
    void command(String kind, long position, String expectedTrack) {
        if (disposed || !player() || !ReBrowserMusicPolicy.playerDocument(web.getUrl())) {
            if ("seek".equals(kind)) listener.seekResult("wrong-document");
            return;
        }
        if (!Set.of("pause", "resume", "previous", "next", "seek", "like").contains(kind)) return;
        if (polling.expired()) { if ("seek".equals(kind)) listener.seekResult("page-unresponsive"); return; }
        final int epoch = document, operation = commanding.begin();
        if (operation == 0) {
            if ("seek".equals(kind)) listener.seekResult("queued");
            queuedCommand = kind; queuedPosition = position; queuedTrack = expectedTrack; return;
        }
        // evaluateJavascript does not carry a Chromium user gesture. A real native media
        // command therefore authorizes this player document explicitly; the document-start
        // play guard still rejects cold/unapproved play, and navigation/pause revokes this.
        boolean previousGesture = web.getSettings().getMediaPlaybackRequiresUserGesture();
        web.getSettings().setMediaPlaybackRequiresUserGesture(
                ReBrowserMusicPlaybackAuthorization.requiresGesture(kind, previousGesture));
        String prepareLayout = "pause".equals(kind) && !coldPause.acknowledged()
                ? "window.__rbMusicLayoutHostV1?.prepare();" : "";
        web.evaluateJavascript(prepareLayout + "window.__rbNeteaseProviderV1?.command(" + JSONObject.quote(kind) + ","
                + position + "," + JSONObject.quote(expectedTrack) + ")", result -> {
            if (disposed || epoch != document || !commanding.finish(operation)) return;
            if ("seek".equals(kind)) listener.seekResult(ReBrowserMusicSeekDiagnostics.javascriptResult(result));
            if (!"\"submitted\"".equals(result)) {
                if (!"pause".equals(kind)) web.getSettings().setMediaPlaybackRequiresUserGesture(previousGesture);
                listener.failure("seek".equals(kind) && "\"outside-playable-range\"".equals(result)
                        ? "该位置超出网易云官方允许播放／试听的范围。" : "网页暂不能执行此媒体操作");
            }
            // Advance a genuine read after submission, not an optimistic/fabricated position.
            if ("seek".equals(kind) && "\"submitted\"".equals(result)) wakePoll();
            if ("pause".equals(kind) && coldPause.acknowledge("\"submitted\"".equals(result))) wakePoll();
            if (queuedCommand != null) {
                String next = queuedCommand, track = queuedTrack; long value = queuedPosition;
                queuedCommand = queuedTrack = null; command(next, value, track);
            }
        });
    }
    boolean readLyrics(String expected, java.util.function.Consumer<ReBrowserMusicLyrics> result) {
        if (disposed || !ready() || polling.expired() || !player()
                || !ReBrowserMusicPolicy.playerDocument(web.getUrl())) return false;
        final int epoch = document, operation = lyricReading.begin();
        if (operation == 0) return false;
        java.util.concurrent.atomic.AtomicBoolean done = new java.util.concurrent.atomic.AtomicBoolean();
        Runnable timeout = () -> {
            if (!disposed && document == epoch && lyricReading.timeout(operation) && done.compareAndSet(false, true))
                result.accept(ReBrowserMusicLyrics.state(expected, "timeout"));
        };
        handler.postDelayed(timeout, 2500);
        web.evaluateJavascript("JSON.stringify(window.__rbNeteaseProviderV1?.lyrics(" + JSONObject.quote(expected) + "))", value -> {
            if (disposed || epoch != document || !lyricReading.finish(operation)) return;
            handler.removeCallbacks(timeout);
            if (!done.compareAndSet(false, true)) return;
            ReBrowserMusicLyrics parsed;
            try { parsed = ReBrowserMusicLyrics.parse(value, expected); }
            catch (Exception invalid) { parsed = ReBrowserMusicLyrics.state(expected, "unavailable"); }
            result.accept(parsed);
        });
        return true;
    }
    void back(java.util.function.Consumer<String> result) {
        if (disposed || initialization.expired()) { result.accept("root"); return; }
        final int epoch = document, operation = backing.begin();
        if (operation == 0 || polling.expired()) {
            if (operation != 0) backing.finish(operation);
            result.accept("unresponsive"); return;
        }
        java.util.concurrent.atomic.AtomicBoolean done = new java.util.concurrent.atomic.AtomicBoolean();
        handler.postDelayed(() -> { if (done.compareAndSet(false, true)) result.accept(disposed || epoch != document ? "handled" : "unresponsive"); }, 1500);
        web.evaluateJavascript("window.__rbMusicLayoutHostV1?.back()||'unavailable'", value -> {
            if (epoch == document) backing.finish(operation);
            if (!done.compareAndSet(false, true)) return;
            if (disposed || epoch != document) { result.accept("handled"); return; }
            if ("\"handled\"".equals(value)) { result.accept("handled"); return; }
            if ("\"layer\"".equals(value)) { result.accept("layer"); return; }
            if (web.canGoBack()) { web.goBack(); result.accept("handled"); }
            else result.accept("root");
        });
    }
    void close() {
        if (disposed) return;
        ReBrowserStorageEstimate.unregister(application, ReBrowserApplications.MUSIC, web);
        disposed = true; ready = false; document++; resetEvaluations(); lifecycle.stop(); handler.removeCallbacksAndMessages(null);
        if (providerScript != null) providerScript.remove();
        if (layoutScript != null) layoutScript.remove();
        if (web.getParent() instanceof ViewGroup) ((ViewGroup) web.getParent()).removeView(web);
        web.stopLoading(); web.destroy(); context.setBaseContext(application);
        // Never delete the Profile or call global pauseTimers().
    }
}
