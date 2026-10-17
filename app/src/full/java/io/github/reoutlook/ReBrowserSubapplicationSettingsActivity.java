package io.github.reoutlook;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import org.json.JSONObject;
import java.lang.ref.WeakReference;

/** Native per-subapplication settings, reachable even when its runtime is disabled/unavailable. */
@SuppressLint("SetTextI18n")
public final class ReBrowserSubapplicationSettingsActivity extends Activity {
    private String id;
    private TextView storage, result;
    private Button cache, data;
    private Spinner source;
    private AlertDialog confirmation;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean resumed;
    private final Runnable poll = this::refresh;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (Build.VERSION.SDK_INT >= 31) getWindow().setHideOverlayWindows(true);
        SystemBackDispatcher.register(this, this::finish);
        showApplication();
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent);
        if (confirmation != null) confirmation.dismiss();
        handler.removeCallbacks(poll); showApplication();
    }
    private void showApplication() {
        id = getIntent().getStringExtra(ReBrowserApplications.EXTRA_APPLICATION);
        if (!ReBrowserApplications.validApplication(id)) { finish(); return; }
        source = null;
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(247, 248, 252));
        WindowStyling.apply(this, root);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        Button back = new Button(this); back.setText("‹"); back.setContentDescription("返回应用管理"); back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(56), dp(56)));
        TextView title = label(ReBrowserApplications.name(id) + " 设置", 23); title.setTypeface(null, android.graphics.Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1)); root.addView(header);
        ScrollView scroll = new ScrollView(this); LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL); panel.setPadding(dp(22), dp(10), dp(22), dp(24)); scroll.addView(panel);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        if (ReBrowserApplications.MUSIC.equals(id)) {
            panel.addView(label("音乐源", 17)); source = new Spinner(this); source.setContentDescription("音乐源工作模式"); protect(source);
            source.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"网易云"}));
            source.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long itemId) {
                    ReBrowserMusicSources choices = new ReBrowserMusicSources(ReBrowserSubapplicationSettingsActivity.this);
                    if (!ReBrowserSubapplicationGate.blocked(ReBrowserSubapplicationSettingsActivity.this, id)
                            && !ReBrowserMusicSources.NETEASE.equals(choices.selected()))
                        choices.select(ReBrowserMusicSources.NETEASE);
                }
                @Override public void onNothingSelected(AdapterView<?> parent) {}
            });
            panel.addView(source, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        }
        panel.addView(label("存储", 17)); storage = label("正在读取本机存储摘要…", 14); panel.addView(storage);
        panel.addView(button("刷新存储状态", this::refresh));
        cache = button("清除缓存", () -> confirm(false)); panel.addView(cache);
        data = button("清除数据", () -> confirm(true)); panel.addView(data);
        result = label("缓存不主动清除Cookie或持久化站点数据；清数据会清除此子应用的本机资料及网页登录状态。", 14); panel.addView(result);
        setContentView(root); refresh();
    }
    private void refresh() {
        if (storage == null || isFinishing()) return;
        handler.removeCallbacks(poll);
        try {
            JSONObject info = ReBrowserSubapplicationStorage.snapshot(this, id);
            boolean busy = info.getBoolean("cleanupRunning"), pending = info.getBoolean("dataCleanupPending");
            storage.setText("本机资料：" + Formatter.formatFileSize(this, info.getLong("nativeDataBytes"))
                    + "（不含网页存储）\n网页缓存／站点数据：由WebView管理，未提供独立容量统计接口"
                    + (busy ? "\n正在清理，此子应用暂不可进入。" : pending ? "\n上次数据清理未完成，请确认继续清除数据。" : ""));
            cache.setEnabled(!busy && !pending && info.getBoolean("cacheClearSupported"));
            data.setEnabled(!busy && info.getBoolean("dataClearSupported"));
            if (source != null) source.setEnabled(!busy && !pending);
            if (busy && resumed) handler.postDelayed(poll, 500);
        } catch (Exception error) {
            storage.setText("暂时无法读取存储摘要，未清理任何内容。"); cache.setEnabled(false); data.setEnabled(false);
        }
    }
    private void confirm(boolean clearData) {
        if (confirmation != null && confirmation.isShowing()) return;
        final String target = id;
        String effects = clearData
                ? "将关闭此子应用，清除其网页登录、站点数据、本机资料和独立设置。"
                    + (ReBrowserApplications.OUTLOOK.equals(id) ? "本机离线邮件也会删除。" : "播放恢复信息及歌词位置也会重置。")
                : "将关闭此子应用并请求清理其网页资源缓存，不主动删除Cookie、持久化站点存储或本机资料。关闭页面会丢失未保存内容及临时会话。";
        confirmation = new AlertDialog.Builder(this).setTitle("清除" + ReBrowserApplications.name(id) + (clearData ? "数据？" : "缓存？"))
                .setMessage(effects + "\n\n不影响其他浏览环境或子应用；官网账号、云端内容、应用启用／桌面入口及系统授权保持不变。")
                .setNegativeButton("取消", null).setPositiveButton("确认清除", (dialog, which) -> {
                    if (!target.equals(id)) return;
                    result.setText("正在停止对应子应用并等待其写入结束…");
                    WeakReference<ReBrowserSubapplicationSettingsActivity> owner = new WeakReference<>(this);
                    try {
                        ReBrowserSubapplicationStorage.clear(getApplicationContext(), target, clearData, message -> {
                            ReBrowserSubapplicationSettingsActivity activity = owner.get();
                            if (activity != null && !activity.isDestroyed() && target.equals(activity.id)) {
                                activity.result.setText(message); activity.refresh();
                            }
                        });
                    } catch (RuntimeException failure) { result.setText("清理未能开始，未改为全应用清理。"); }
                    refresh();
                }).create();
        confirmation.show();
        protect(confirmation.getButton(AlertDialog.BUTTON_POSITIVE));
        protect(confirmation.getButton(AlertDialog.BUTTON_NEGATIVE));
    }
    private TextView label(String text, int size) {
        TextView view = new TextView(this); view.setText(text); view.setTextSize(size);
        view.setTextColor(Color.rgb(54, 60, 72)); view.setPadding(0, dp(10), 0, dp(12)); return view;
    }
    private Button button(String text, Runnable action) {
        Button button = new Button(this); button.setText(text); protect(button);
        button.setOnClickListener(v -> action.run()); return button;
    }
    @SuppressLint("ClickableViewAccessibility") // Clean events use the widget's normal accessible click handling.
    private void protect(View view) {
        view.setFilterTouchesWhenObscured(true);
        view.setOnTouchListener((target, event) -> {
            boolean obscured = (event.getFlags() & (android.view.MotionEvent.FLAG_WINDOW_IS_OBSCURED
                    | android.view.MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED)) != 0;
            if (obscured) { target.setPressed(false); target.cancelLongPress(); }
            return obscured;
        });
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    @Override protected void onResume() { super.onResume(); resumed = true; refresh(); }
    @Override protected void onPause() { resumed = false; handler.removeCallbacks(poll); super.onPause(); }
    @Override protected void onDestroy() {
        handler.removeCallbacks(poll); if (confirmation != null) confirmation.dismiss(); super.onDestroy();
    }
}
