package io.github.reoutlook;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.lang.ref.WeakReference;

/** Music task UI. Visibility changes do not stop or recreate the service-owned WebPlayer. */
public final class ReBrowserMusicActivity extends Activity {
    private static WeakReference<ReBrowserMusicActivity> live = new WeakReference<>(null);
    private ReBrowserMusicService service;
    private boolean bound, backPending, resumed;
    private FrameLayout host;
    private LinearLayout loginBar;
    private TextView message;
    private Button loginUa;
    private AlertDialog exitDialog, lyricsPermissionDialog;
    private final Runnable refresh = this::refresh;
    private final android.os.Handler reconnect = new android.os.Handler(android.os.Looper.getMainLooper());
    private int reconnectAttempts;
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            if (isFinishing()) return;
            service = ((ReBrowserMusicService.LocalBinder) binder).service();
            if (service.closed()) { retryConnection(); return; }
            reconnectAttempts = 0;
            service.claimTask(getTaskId());
            if (service.runtime() != null) {
                service.runtime().attach(ReBrowserMusicActivity.this, host);
                window();
            }
            service.observe(refresh);
        }
        @Override public void onServiceDisconnected(ComponentName name) { service = null; refresh(); }
    };
    static void stopEnabledRuntime() {
        ReBrowserMusicActivity a = live.get();
        ReBrowserMusicService.stopEnabledRuntime();
        if (a != null) a.finishAndRemoveTask();
    }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); live = new WeakReference<>(this);
        if (ReBrowserSubapplicationGate.blocked(this, ReBrowserApplications.MUSIC)) {
            startActivity(ReBrowserApplications.applicationSettingsIntent(this, ReBrowserApplications.MUSIC));
            finish(); return;
        }
        if (!new ReBrowserMusicEntrance(this).enabled()) { ReBrowserApplications.openManagement(this); finish(); return; }
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        WindowStyling.apply(this, root);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets safe = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            } else view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        loginBar = new LinearLayout(this); loginBar.setOrientation(LinearLayout.VERTICAL);
        message = new TextView(this); message.setText("正在检查网易云官方登录状态…"); loginBar.addView(message);
        LinearLayout actions = new LinearLayout(this);
        Button check = new Button(this); check.setText("重新检查（异常时）");
        check.setOnClickListener(v -> {
            if (service != null && service.runtime() != null) service.runtime().checkLogin();
            else { reconnectAttempts = 0; retryConnection(); }
        });
        actions.addView(check);
        loginUa = new Button(this); loginUa.setText("电脑 UA 扫码登录");
        loginUa.setOnClickListener(v -> { if (service != null && service.runtime() != null) {
            service.runtime().loginUa(!service.runtime().lifecycle.desktop()); refresh();
        }});
        actions.addView(loginUa); loginBar.addView(actions); root.addView(loginBar);
        host = new FrameLayout(this); root.addView(host, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root); SystemBackDispatcher.register(this, this::handleBack);
        if (!ReBrowserRuntime.inspect().browserAvailable()) { message.setText("当前 WebView 不满足音乐隔离运行要求。"); return; }
        if (Build.VERSION.SDK_INT >= 31) getWindow().setHideOverlayWindows(true);
        connect();
    }
    private void connect() {
        if (isFinishing() || isDestroyed() || bound) return;
        Intent intent = new Intent(this, ReBrowserMusicService.class);
        try { startService(intent); bound = bindService(intent, connection, BIND_AUTO_CREATE); }
        catch (RuntimeException failure) { message.setText("音乐服务暂不可用，未借用其他登录环境。"); }
    }
    private void retryConnection() {
        if (isFinishing() || isDestroyed()) return;
        if (service != null) service.unobserve(refresh);
        service = null;
        if (bound) { unbindService(connection); bound = false; }
        stopService(new Intent(this, ReBrowserMusicService.class));
        reconnect.removeCallbacksAndMessages(null);
        if (reconnectAttempts++ < 3) {
            message.setText("正在重新连接已停止的音乐服务…");
            reconnect.postDelayed(this::connect, 250);
        } else message.setText("音乐服务尚未重新连接，请点“重新检查”；登录会保留。");
    }
    private void window() {
        if (service == null || service.runtime() == null) return;
        service.runtime().window(ReBrowserDevice.inspect(this).tablet(), ReBrowserDevice.fullWindow(this),
                getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE);
    }
    private void refresh() {
        if (host == null) return;
        ReBrowserMusicRuntime runtime = service == null ? null : service.runtime();
        boolean player = runtime != null && runtime.player(), ready = runtime != null && runtime.ready();
        loginBar.setVisibility(player && ready ? View.GONE : View.VISIBLE);
        // Only official login UI is shown before authentication; business UI stays covered until ready.
        host.setVisibility(runtime != null && (runtime.loginVisible() || ready) ? View.VISIBLE : View.INVISIBLE);
        loginUa.setVisibility(runtime != null && runtime.lifecycle.desktopLoginAllowed() ? View.VISIBLE : View.GONE);
        if (runtime != null) {
            loginUa.setText(runtime.lifecycle.desktop() ? "切回手机 UA 登录" : "电脑 UA 扫码登录");
            message.setText(player ? "登录已确认，正在准备暂停状态的 WebPlayer…"
                    : runtime.lifecycle.stage() == ReBrowserMusicLifecycle.Stage.CHECKING ? "正在检查网易云官方登录状态…"
                    : !runtime.loginVisible() ? "正在载入网易云原版登录页…"
                    : runtime.lifecycle.desktop() ? "请使用网易云原版电脑界面扫码，或切回手机登录。"
                    : "请在网易云原版手机页面完成登录");
        }
        if (service != null && !service.failure().isEmpty()) message.setText(service.failure());
        if (resumed && service != null && (lyricsPermissionDialog == null || !lyricsPermissionDialog.isShowing())
                && service.takeLyricsPermissionRequest()) explainLyricsPermission();
    }
    private void explainLyricsPermission() {
        if (isFinishing() || isDestroyed()) return;
        lyricsPermissionDialog = new AlertDialog.Builder(this).setTitle("桌面歌词需要悬浮窗权限")
                .setMessage("仅用于显示歌词。通知中的同一个歌词按钮依次切换：关闭 → 可拖动 → 锁定穿透 → 关闭。"
                        + "手机上下拖动，平板可上下左右拖动；关闭后保留位置。锁定时不接收触摸。\n\n"
                        + "是否前往系统权限页面？授权完全由你决定；返回后再次点击歌词按钮才会开启。")
                .setNegativeButton("暂不开启", null)
                .setPositiveButton("前往系统授权", (dialog, which) -> {
                    try { startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            android.net.Uri.parse("package:" + getPackageName()))); }
                    catch (RuntimeException unavailable) { android.widget.Toast.makeText(this, "系统未提供该权限页面，未授予或开启悬浮窗。", android.widget.Toast.LENGTH_LONG).show(); }
                }).create();
        lyricsPermissionDialog.show();
        lyricsPermissionDialog.getButton(AlertDialog.BUTTON_POSITIVE).setFilterTouchesWhenObscured(true);
        lyricsPermissionDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setFilterTouchesWhenObscured(true);
    }
    private void applyOrientation() {
        int request = ReBrowserDevice.inspect(this).tablet() && ReBrowserDevice.fullWindow(this)
                ? android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_USER : android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
        if (getRequestedOrientation() != request) setRequestedOrientation(request);
    }
    @Override protected void onResume() {
        super.onResume(); resumed = true;
        if (!new ReBrowserMusicEntrance(this).enabled()) { stopEnabledRuntime(); return; }
        applyOrientation(); window(); refresh();
    }
    @Override protected void onPause() { resumed = false; super.onPause(); }
    @Override public void onConfigurationChanged(Configuration configuration) { super.onConfigurationChanged(configuration); window(); refresh(); }
    @Override public void onMultiWindowModeChanged(boolean multi, Configuration configuration) { super.onMultiWindowModeChanged(multi, configuration); applyOrientation(); window(); refresh(); }
    @Override public void onPictureInPictureModeChanged(boolean pip, Configuration configuration) { super.onPictureInPictureModeChanged(pip, configuration); applyOrientation(); window(); refresh(); }
    @SuppressWarnings("deprecation") @Override public void onBackPressed() { handleBack(); }
    private void handleBack() {
        WindowInsets insets = getWindow().getDecorView().getRootWindowInsets();
        boolean ime = insets != null && (Build.VERSION.SDK_INT >= 30 ? insets.isVisible(WindowInsets.Type.ime())
                : insets.getSystemWindowInsetBottom() > insets.getStableInsetBottom());
        if (ime) {
            android.view.inputmethod.InputMethodManager manager = getSystemService(android.view.inputmethod.InputMethodManager.class);
            if (manager != null) manager.hideSoftInputFromWindow(host.getWindowToken(), 0); return;
        }
        if (backPending) return;
        if (service == null || service.runtime() == null) { finish(); return; }
        backPending = true;
        service.runtime().back(result -> {
            backPending = false; if (isFinishing() || "handled".equals(result)) return;
            if ("layer".equals(result)) { android.widget.Toast.makeText(this, "请先关闭网页播放或列表面板", android.widget.Toast.LENGTH_SHORT).show(); return; }
            if (exitDialog != null && exitDialog.isShowing()) return;
            exitDialog = new AlertDialog.Builder(this).setTitle("退出音乐？")
                    .setMessage("停止运行与播放，保留登录及恢复信息。再次启动保持暂停。")
                    .setNegativeButton("取消", null).setPositiveButton("退出", (d, w) -> stopEnabledRuntime()).show();
        });
    }
    @Override protected void onDestroy() {
        if (exitDialog != null) exitDialog.dismiss();
        if (lyricsPermissionDialog != null) lyricsPermissionDialog.dismiss();
        reconnect.removeCallbacksAndMessages(null);
        if (service != null) {
            service.unobserve(refresh);
            if (service.runtime() != null) service.runtime().detach(this);
            if (isFinishing()) service.releaseTask(getTaskId());
        }
        if (bound) unbindService(connection);
        if (live.get() == this) live.clear(); super.onDestroy();
    }
}
