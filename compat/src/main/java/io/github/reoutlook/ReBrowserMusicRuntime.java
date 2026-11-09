package io.github.reoutlook;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;

import java.util.Set;

import org.json.JSONObject;

/** One service-owned Gecko WebPlayer. All methods run on the main thread. */
final class ReBrowserMusicRuntime {
    interface Listener {
        void changed(ReBrowserMusicSnapshot snapshot);
        void stageChanged();
        void loading();
        void failure(String reason);
        default void lyricsInvalidated() {}
        default void seekResult(String result) {}
        default void seekObserved(int completed, int failed) {}
        default void engineObserved(boolean known, boolean paused, boolean seeking, int ready,
                int network, int error, boolean advancing, String play) {}
    }
    private final Context application;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Listener listener;
    final ReBrowserMusicLifecycle lifecycle = new ReBrowserMusicLifecycle();
    private final ReBrowserMusicAccountCheck accountCheck = new ReBrowserMusicAccountCheck();
    private Runnable accountTimeout, initializationTimeout;
    private final ReBrowserMusicInitialization initialization = new ReBrowserMusicInitialization();
    private final ReBrowserMusicGeckoPage web;
    private int document;
    private final ReBrowserMusicEvaluation polling = new ReBrowserMusicEvaluation();
    private final ReBrowserMusicEvaluation commanding = new ReBrowserMusicEvaluation();
    private final ReBrowserMusicEvaluation backing = new ReBrowserMusicEvaluation();
    private final ReBrowserMusicEvaluation lyricReading = new ReBrowserMusicEvaluation();
    private String queuedCommand, queuedTrack;
    private long queuedPosition;
    private boolean disposed, adapted = true, ready, loginVisible, providerFailureReported;
    private final ReBrowserMusicPreparation coldPause = new ReBrowserMusicPreparation();
    private ReBrowserMusicSnapshot snapshot = ReBrowserMusicSnapshot.empty();
    private final Runnable tick = this::poll;

    ReBrowserMusicRuntime(Context owner, Listener listener) throws Exception {
        application = owner.getApplicationContext();
        this.listener = listener;
        web = new ReBrowserMusicGeckoPage(owner, new ReBrowserMusicGeckoPage.Listener() {
            @Override public void pageStarted(String url) { onPageStarted(url); }
            @Override public void pageFinished(String url) { onPageFinished(url); }
            @Override public void mainFrameError(String url) { onMainFrameError(url); }
            @Override public void bridgeReady() {
                if (player()) { web.configure(lifecycle.desktop(), adapted, true); wakePoll(); }
            }
        });
        ReBrowserStorageEstimate.register(application, ReBrowserApplications.MUSIC,
                web.storageDriver());
        load();
        handler.post(tick);
    }

    private void onPageStarted(String url) {
        document++; resetEvaluations(); ready = false; coldPause.reset(); loginVisible = false;
        if (!ReBrowserMusicPolicy.allowsNavigation(url)) { web.stopLoading(); return; }
        String expected = accountCheck.request();
        if (expected != null && !expected.equals(url)) {
            web.stopLoading(); failAccount(expected); return;
        }
        accountCheck.started(url);
        if (lifecycle.stage() == ReBrowserMusicLifecycle.Stage.LOGIN
                && ReBrowserMusicPolicy.playerDocument(url)) {
            lifecycle.recheck(); accountCheck.restart(); web.stopLoading();
            handler.post(() -> { if (!disposed) { load(); listener.stageChanged(); } }); return;
        }
        if (ReBrowserMusicPolicy.loginDocument(url)
                && lifecycle.stage() != ReBrowserMusicLifecycle.Stage.LOGIN) {
            lifecycle.account(false); snapshot = ReBrowserMusicSnapshot.empty();
            listener.changed(snapshot); web.stopLoading();
            handler.post(() -> { if (!disposed) load(); });
        }
        listener.stageChanged();
    }

    private void onPageFinished(String url) {
        if ((url != null && url.equals(web.url()) && accountCheck.finished(url))
                || ReBrowserMusicPolicy.playerDocument(url)) wakePoll();
    }

    private void onMainFrameError(String url) {
        if (accountCheck.active(url)) failAccount(url);
        else listener.failure("音乐网页连接失败，请检查网络后重新进入。");
    }

    void attach(android.app.Activity activity, ViewGroup host) {
        if (!disposed) web.attach(activity, host);
    }
    void detach(android.app.Activity activity) { web.detach(activity); }
    boolean ready() { return ready && !disposed; }
    boolean player() { return lifecycle.player(); }
    boolean loginVisible() { return !disposed && lifecycle.presentLogin(web.url(), loginVisible); }
    ReBrowserMusicSnapshot snapshot() { return snapshot; }
    void window(boolean tablet, boolean full, boolean landscape) {
        boolean reload = lifecycle.window(tablet, full, landscape);
        adapted = !ReBrowserMusicPolicy.originalLayout(tablet, full, landscape);
        if (!disposed) web.configure(lifecycle.desktop(), adapted, player());
        if (reload) { load(); listener.stageChanged(); }
        else if (!disposed && player() && ReBrowserMusicPolicy.playerDocument(web.url())) {
            web.setPortrait(adapted);
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
        else if (ReBrowserMusicPolicy.playerDocument(web.url())) web.recheck();
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
        web.stopLoading();
        web.configure(lifecycle.desktop(), adapted, player());
        web.load(probe != null ? probe : checking ? ReBrowserMusicPolicy.HOME
                : lifecycle.destination());
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
        boolean loginDocument = ReBrowserMusicPolicy.loginDocument(web.url());
        final String expectedAccount = accountCheck.request();
        boolean accountDocument = accountCheck.active(web.url());
        if (accountDocument && !accountCheck.readable(web.url())) return;
        if (!accountDocument && !loginDocument
                && !ReBrowserMusicPolicy.playerDocument(web.url())) return;
        final int epoch = document, operation = polling.begin();
        if (operation == 0) return;
        Runnable timeout = () -> {
            if (!disposed && document == epoch && polling.timeout(operation)) {
                if (accountDocument) fallbackAccount(expectedAccount);
                else listener.failure("音乐网页暂未响应，已停止追加检查请求；退出重进会保留登录且保持暂停。");
            }
        };
        handler.postDelayed(timeout, 2500);
        web.probe(accountDocument ? "music-account"
                : loginDocument ? "music-login" : "music-state", value -> {
            if (disposed || document != epoch || !polling.finish(operation)) return;
            handler.removeCallbacks(timeout);
            if (initialization.expired()) return;
            if (accountDocument && !accountCheck.readable(web.url())) return;
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
        if (disposed || !player() || !ReBrowserMusicPolicy.playerDocument(web.url())) {
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
        web.mediaCommand(kind, position, expectedTrack, result -> {
            if (disposed || epoch != document || !commanding.finish(operation)) return;
            if ("seek".equals(kind)) listener.seekResult(ReBrowserMusicSeekDiagnostics.javascriptResult(result));
            if (!"\"submitted\"".equals(result)) {
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
                || !ReBrowserMusicPolicy.playerDocument(web.url())) return false;
        final int epoch = document, operation = lyricReading.begin();
        if (operation == 0) return false;
        java.util.concurrent.atomic.AtomicBoolean done = new java.util.concurrent.atomic.AtomicBoolean();
        Runnable timeout = () -> {
            if (!disposed && document == epoch && lyricReading.timeout(operation) && done.compareAndSet(false, true))
                result.accept(ReBrowserMusicLyrics.state(expected, "timeout"));
        };
        handler.postDelayed(timeout, 2500);
        web.lyrics(expected, value -> {
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
        web.back(value -> {
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
        ReBrowserStorageEstimate.unregister(application, ReBrowserApplications.MUSIC,
                web.storageDriver());
        disposed = true; ready = false; document++; resetEvaluations(); lifecycle.stop();
        handler.removeCallbacksAndMessages(null);
        web.stopLoading(); web.close();
        // Never delete the Profile or call global pauseTimers().
    }
}
