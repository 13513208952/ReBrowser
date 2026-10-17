package io.github.reoutlook;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;
import androidx.webkit.Profile;
import androidx.webkit.ProfileStore;
import androidx.webkit.WebStorageCompat;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import org.json.JSONObject;
import java.io.File;
import java.util.function.Consumer;

/** Fixed-target cleanup. Never walks WebView internals, application roots or unrelated Profiles. */
final class ReBrowserSubapplicationStorage {
    private ReBrowserSubapplicationStorage() {}
    static String profileName(String id) {
        if (ReBrowserApplications.OUTLOOK.equals(id)) return Profile.DEFAULT_PROFILE_NAME;
        if (ReBrowserApplications.MUSIC.equals(id)) return ReBrowserMusicPolicy.PROFILE;
        throw new IllegalArgumentException("Unsupported fixed application");
    }
    private static String[] preferences(String id) {
        if (ReBrowserApplications.OUTLOOK.equals(id)) return new String[]{"account_preferences", "ui_preferences"};
        if (ReBrowserApplications.MUSIC.equals(id)) return new String[]{"rb_music_sources_v1", "rb_music_resume_netease_v1", "rb_music_lyric_position_v1"};
        throw new IllegalArgumentException("Unsupported fixed application");
    }
    static long nativeBytes(Context context, String id) {
        profileName(id);
        long bytes = 0;
        if (ReBrowserApplications.OUTLOOK.equals(id)) {
            File database = context.getDatabasePath(MailDatabase.DATABASE_NAME);
            for (String suffix : new String[]{"", "-wal", "-shm", "-journal"}) bytes += new File(database.getPath() + suffix).length();
        }
        for (String name : preferences(id)) bytes += new File(context.getApplicationInfo().dataDir, "shared_prefs/" + name + ".xml").length();
        return bytes;
    }
    static JSONObject snapshot(Context context, String id) throws Exception {
        if (!ReBrowserApplications.validApplication(id)) throw new IllegalArgumentException("Unsupported fixed application");
        JSONObject value = new JSONObject().put("applicationId", id).put("name", ReBrowserApplications.name(id))
                .put("nativeDataBytes", nativeBytes(context, id)).put("webCacheBytes", JSONObject.NULL)
                .put("webDataBytes", JSONObject.NULL).put("completeSizeAvailable", false)
                .put("cacheClearSupported", supported(false)).put("dataClearSupported", supported(true))
                .put("cleanupRunning", ReBrowserSubapplicationGate.running(context, id))
                .put("dataCleanupPending", ReBrowserSubapplicationGate.pendingData(context, id))
                .put("activeResources", ReBrowserSubapplicationGate.users(context, id))
                .put("scope", "fixed-subapplication-only").put("administratorCanClear", false);
        try { value.put("webEstimate", ReBrowserStorageEstimate.snapshot(context, id)); }
        catch (Exception unavailable) { value.put("webEstimate", new JSONObject().put("status", "unavailable")); }
        return value;
    }
    static boolean supported(boolean data) {
        return ReBrowserRuntime.inspect().provider && ReBrowserRuntime.feature(WebViewFeature.MULTI_PROFILE)
                && (!data || ReBrowserRuntime.feature(WebViewFeature.DELETE_BROWSING_DATA));
    }
    interface Backend {
        boolean supported(boolean data);
        void clear(Context context, String id, boolean data, Runnable complete);
    }
    private static final Backend WEB = new Backend() {
        @Override public boolean supported(boolean data) { return ReBrowserSubapplicationStorage.supported(data); }
        @Override public void clear(Context context, String id, boolean data, Runnable complete) {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                ProfileStore profiles = ProfileStore.getInstance();
                Profile profile = ReBrowserApplications.OUTLOOK.equals(id)
                        ? profiles.getOrCreateProfile(Profile.DEFAULT_PROFILE_NAME) : profiles.getProfile(profileName(id));
                if (profile == null) { complete.run(); return; } // Never create a missing music Profile just to clear it.
                if (!profileName(id).equals(profile.getName())) throw new IllegalStateException("profile-binding-mismatch");
                if (data) {
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA)) {
                        profile.getGeolocationPermissions().clearAll();
                        WebStorageCompat.deleteBrowsingData(profile.getWebStorage(), context.getMainExecutor(), complete);
                    } else throw new UnsupportedOperationException("profile-data-deletion-required");
                } else {
                    // Cache removal is scoped to this WebView's BrowserContext, not cookies/storage.
                    // There is no disk-cache completion callback: report submission, not completion.
                    WebView temporary = new WebView(context);
                    try {
                        WebViewCompat.setProfile(temporary, profileName(id));
                        if (!profileName(id).equals(WebViewCompat.getProfile(temporary).getName()))
                            throw new IllegalStateException("profile-binding-mismatch");
                        temporary.clearCache(true);
                    } finally { temporary.destroy(); }
                    complete.run();
                }
            } else throw new UnsupportedOperationException("multi-profile-required");
        }
    };
    static void clear(Context context, String id, boolean data, Consumer<String> reply) {
        clear(context, id, data, WEB, reply);
    }
    static void clear(Context context, String id, boolean data, Backend backend, Consumer<String> reply) {
        if (Looper.myLooper() != Looper.getMainLooper()) throw new IllegalStateException("UI thread required");
        if (!ReBrowserApplications.validApplication(id)) throw new IllegalArgumentException("Unsupported fixed application");
        Context app = context.getApplicationContext();
        if (!backend.supported(data)) { reply.accept("当前环境暂不支持此项清理。"); return; }
        boolean recovering = ReBrowserSubapplicationGate.pendingData(app, id);
        if (!ReBrowserSubapplicationGate.begin(app, id, data)) { reply.accept("该子应用仍在清理，请等待；未重复执行。"); return; }
        ReBrowserStorageEstimate.invalidate(app, id);
        try {
            if (ReBrowserApplications.MUSIC.equals(id)) ReBrowserMusicActivity.stopEnabledRuntime();
            else ReBrowserSubapplicationGate.closeMailScreens(app);
        } catch (RuntimeException shutdown) {
            ReBrowserSubapplicationGate.end(app, id, !recovering);
            reply.accept("子应用未能停止，本次未提交清理；请退出后重试。"); return;
        }
        new Drain(app, id, data, recovering, backend, reply).run();
    }
    private static final class Drain implements Runnable {
        final Context app;
        final String id;
        final boolean data, recovering;
        final Backend backend;
        final Consumer<String> reply;
        final Handler handler = new Handler(Looper.getMainLooper());
        int attempts;
        boolean finished;
        Drain(Context app, String id, boolean data, boolean recovering, Backend backend, Consumer<String> reply) {
            this.app = app; this.id = id; this.data = data; this.recovering = recovering; this.backend = backend; this.reply = reply;
        }
        @Override public void run() {
            if (ReBrowserSubapplicationGate.users(app, id) != 0) {
                if (attempts++ < 120) { handler.postDelayed(this, 25); return; }
                // No deletion was submitted: do not quarantine a still-active protected export.
                finish(!recovering, "子应用写入或邮件导出仍在运行，本次未清理，请结束后重试。"); return;
            }
            try {
                backend.clear(app, id, data, () -> {
                    if (finished) return;
                    try {
                        if (data) clearNativeData(app, id);
                        finish(true, data ? "已清除全部本机数据。" : "缓存清理请求已提交。");
                    } catch (RuntimeException failure) { finish(false, "清理尚未完成，请在本页重试清除数据。"); }
                });
            } catch (RuntimeException failure) { finish(!data, "清理未完成；没有改为全应用清理，请重试。"); }
            // Never unlock after an arbitrary timeout once async data deletion has started.
        }
        void finish(boolean safeToReopen, String message) {
            if (finished) return; finished = true;
            try { ReBrowserSubapplicationGate.end(app, id, safeToReopen); }
            catch (RuntimeException journal) { message = "清理状态未保存，请重试；子应用仍保持受保护状态。"; }
            reply.accept(message);
        }
    }
    static void clearNativeData(Context context, String id) {
        if (!ReBrowserSubapplicationGate.running(context, id) || !ReBrowserSubapplicationGate.pendingData(context, id)
                || ReBrowserSubapplicationGate.users(context, id) != 0)
            throw new IllegalStateException("cleanup-fence-required");
        if (ReBrowserApplications.OUTLOOK.equals(id)) {
            File file = context.getDatabasePath(MailDatabase.DATABASE_NAME);
            boolean exists = false;
            for (String suffix : new String[]{"", "-wal", "-shm", "-journal"}) exists |= new File(file.getPath() + suffix).exists();
            if (exists && !context.deleteDatabase(MailDatabase.DATABASE_NAME)) throw new IllegalStateException("mail-database-delete-failed");
        }
        for (String name : preferences(id))
            if (!context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit())
                throw new IllegalStateException("application-preferences-clear-failed");
    }
}
