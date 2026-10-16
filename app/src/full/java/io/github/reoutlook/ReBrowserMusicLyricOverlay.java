package io.github.reoutlook;

import android.app.KeyguardManager;
import android.content.ComponentCallbacks;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.input.InputManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/** One small native window, never a fullscreen touch interceptor. No audio/JS/permission grants. */
final class ReBrowserMusicLyricOverlay implements AutoCloseable {
    private final Context owner;
    private final SharedPreferences positions;
    final ReBrowserMusicLyricWindow state;
    private final Runnable lost;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Context visual;
    private WindowManager windows;
    private LyricView root;
    private static final class LyricView extends LinearLayout {
        LyricView(Context context) { super(context); }
        @Override public boolean performClick() { return super.performClick(); }
    }
    private TextView lyric;
    private WindowManager.LayoutParams params;
    private boolean attached, scheduled, closed, lostPosted, runtimeReady, configurationRegistered;
    private String applied = "", text = "";
    private int availableWidth, availableHeight, margin, insetsHash;
    private boolean tablet;
    private float downX, downY;
    private ReBrowserMusicLyricWindow.Placement placement, dragStart;
    private final Runnable reflow = () -> { scheduled = false; reflow(); };
    private final ComponentCallbacks configuration = new ComponentCallbacks() {
        @Override public void onConfigurationChanged(Configuration value) { dragStart = null; schedule(); }
        @Override public void onLowMemory() { /* One bounded lyric line only. */ }
    };
    ReBrowserMusicLyricOverlay(Context context, Runnable lost) {
        owner = context; this.lost = lost;
        positions = context.getSharedPreferences("rb_music_lyric_position_v1", Context.MODE_PRIVATE);
        state = new ReBrowserMusicLyricWindow(read("x", .5f), read("y", .25f));
    }
    private float read(String key, float fallback) {
        try { return positions.getFloat(key, fallback); } catch (ClassCastException invalid) { return fallback; }
    }
    boolean allowed() { return Settings.canDrawOverlays(owner); }
    boolean showing() { return attached; }
    static int windowFlags(ReBrowserMusicLyricWindow.Mode mode) {
        return WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | (mode == ReBrowserMusicLyricWindow.Mode.LOCKED ? WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE : 0);
    }
    static int background(ReBrowserMusicLyricWindow.Mode mode) {
        return mode == ReBrowserMusicLyricWindow.Mode.LOCKED ? android.graphics.Color.TRANSPARENT : 0xC0202020;
    }
    boolean cycle() {
        if (closed || !state.cycle(allowed())) return false;
        dragStart = null; save(); render(text, runtimeReady); return true;
    }
    void render(String value, boolean ready) {
        if (closed) return;
        runtimeReady = ready;
        text = value == null ? "" : value.substring(0, Math.min(value.length(), 256));
        reflow();
        if (attached && !text.contentEquals(lyric.getText())) lyric.setText(text);
    }
    private int dp(int value) { return Math.round(value * owner.getResources().getDisplayMetrics().density); }
    private void schedule() {
        if (!closed && !scheduled) { scheduled = true; main.post(reflow); }
    }
    @android.annotation.SuppressLint("RtlHardcoded") // Persisted x is measured from physical LEFT, also in RTL locales.
    private void create() {
        if (visual != null) return;
        visual = owner;
        if (Build.VERSION.SDK_INT >= 30) {
            Display display = owner.getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY);
            if (display == null) throw new IllegalStateException("no-default-display");
            visual = owner.createDisplayContext(display).createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null);
        }
        windows = visual.getSystemService(WindowManager.class);
        visual.registerComponentCallbacks(configuration); configurationRegistered = true;
        root = new LyricView(visual); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(10), dp(6), dp(10), dp(6));
        root.setBackgroundColor(background(state.mode()));
        lyric = new TextView(visual); lyric.setTextSize(20); lyric.setTextColor(0xFFFFFFFF); lyric.setMaxLines(3);
        lyric.setEllipsize(TextUtils.TruncateAt.END); lyric.setGravity(Gravity.CENTER); lyric.setShadowLayer(dp(2), 0, 0, 0xFF000000);
        root.addView(lyric, new LinearLayout.LayoutParams(-1, 0, 1));
        root.setOnTouchListener(this::touch);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int hash = insets.hashCode();
            if (insetsHash != hash) { insetsHash = hash; schedule(); }
            return insets;
        });
        params = new WindowManager.LayoutParams(1, 1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT;
        if (Build.VERSION.SDK_INT >= 30) {
            params.setFitInsetsTypes(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            params.setFitInsetsIgnoringVisibility(true);
        }
    }
    @SuppressWarnings("deprecation") private void reflow() {
        if (closed) return;
        if (state.mode() == ReBrowserMusicLyricWindow.Mode.OFF || !runtimeReady) { hide(); return; }
        if (!allowed()) { disable(); return; }
        if (!owner.getSystemService(PowerManager.class).isInteractive()
                || owner.getSystemService(KeyguardManager.class).isKeyguardLocked()) { hide(); return; }
        try {
            create(); int width, height;
            if (Build.VERSION.SDK_INT >= 30) {
                var metrics = windows.getCurrentWindowMetrics(); var bounds = metrics.getBounds();
                var safe = metrics.getWindowInsets().getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                var ime = metrics.getWindowInsets().getInsets(WindowInsets.Type.ime());
                width = bounds.width() - safe.left - safe.right;
                height = bounds.height() - safe.top - Math.max(safe.bottom, ime.bottom);
            } else {
                DisplayMetrics metrics = new DisplayMetrics(); windows.getDefaultDisplay().getRealMetrics(metrics);
                width = metrics.widthPixels; height = metrics.heightPixels;
                WindowInsets insets = root.getRootWindowInsets();
                if (insets != null) {
                    int left = insets.getStableInsetLeft(), right = insets.getStableInsetRight(), top = insets.getStableInsetTop(), bottom = insets.getStableInsetBottom();
                    var cutout = insets.getDisplayCutout();
                    if (cutout != null) { left = Math.max(left, cutout.getSafeInsetLeft()); right = Math.max(right, cutout.getSafeInsetRight()); top = Math.max(top, cutout.getSafeInsetTop()); bottom = Math.max(bottom, cutout.getSafeInsetBottom()); }
                    width -= left + right; height -= top + Math.max(bottom, insets.getSystemWindowInsetBottom());
                }
            }
            margin = Math.max(0, Math.min(dp(8), Math.min(width, height) / 4));
            availableWidth = Math.max(0, width - margin * 2); availableHeight = Math.max(0, height - margin * 2);
            if (availableWidth < dp(40) || availableHeight < dp(24)) { hide(); return; }
            tablet = ReBrowserDevice.inspect(visual).tablet();
            placement = state.place(availableWidth, availableHeight, tablet ? dp(560) : availableWidth, dp(112), tablet);
            params.width = placement.width(); params.height = placement.height(); params.x = margin + placement.x(); params.y = margin + placement.y();
            boolean locked = state.mode() == ReBrowserMusicLyricWindow.Mode.LOCKED;
            root.setBackgroundColor(background(state.mode()));
            params.flags = windowFlags(state.mode());
            // Android 12+ can BLOCK touches behind an opaque untrusted NOT_TOUCHABLE window.
            // A View's transparent background is NOT enough: limit the WINDOW alpha itself.
            float maximum = Build.VERSION.SDK_INT >= 31 ? owner.getSystemService(InputManager.class).getMaximumObscuringOpacityForTouch() : 1;
            params.alpha = locked ? ReBrowserMusicLyricWindow.lockedAlpha(maximum) : 1;
            String key = params.x + ":" + params.y + ":" + params.width + ":" + params.height + ":" + params.flags + ":" + params.alpha;
            if (!attached) { windows.addView(root, params); attached = true; applied = key; root.requestApplyInsets(); }
            else if (!key.equals(applied)) { windows.updateViewLayout(root, params); applied = key; }
        } catch (RuntimeException restricted) { disable(); }
    }
    private boolean touch(View view, MotionEvent event) {
        if (closed || state.mode() != ReBrowserMusicLyricWindow.Mode.UNLOCKED) return false;
        if (event.getPointerCount() != 1 || (event.getFlags() & (MotionEvent.FLAG_WINDOW_IS_OBSCURED | MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED)) != 0) {
            dragStart = null; save(); return true;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN -> { downX = event.getRawX(); downY = event.getRawY(); dragStart = placement; return true; }
            case MotionEvent.ACTION_MOVE -> {
                if (dragStart == null) return true;
                state.drag(dragStart, event.getRawX() - downX, event.getRawY() - downY, availableWidth, availableHeight, tablet);
                reflow(); return true;
            }
            case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { dragStart = null; save(); if (event.getActionMasked() == MotionEvent.ACTION_UP) view.performClick(); return true; }
            default -> { return true; }
        }
    }
    private void save() { positions.edit().putFloat("x", state.xFraction()).putFloat("y", state.yFraction()).apply(); }
    private void hide() {
        dragStart = null;
        if (attached) { try { windows.removeViewImmediate(root); } catch (RuntimeException ignored) { /* Already removed. */ } attached = false; applied = ""; }
    }
    private void disable() {
        boolean active = state.mode() != ReBrowserMusicLyricWindow.Mode.OFF;
        state.off(); save(); releaseVisual();
        if (active && !lostPosted && !closed) { lostPosted = true; main.post(() -> { lostPosted = false; if (!closed) lost.run(); }); }
    }
    void dump(java.io.PrintWriter writer) {
        int[] location = new int[2];
        if (attached && root != null) root.getLocationOnScreen(location);
        writer.println("lyrics mode=" + state.mode() + " attached=" + attached + " tablet=" + tablet
                + " touchable=" + (attached && (params.flags & WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) == 0)
                + " alpha=" + (attached ? params.alpha : 0)
                + " x=" + location[0] + " y=" + location[1]
                + " width=" + (attached ? root.getWidth() : 0) + " height=" + (attached ? root.getHeight() : 0));
    }
    private void releaseVisual() {
        hide();
        if (visual != null && configurationRegistered) visual.unregisterComponentCallbacks(configuration);
        configurationRegistered = false;
        root = null; lyric = null; visual = null; windows = null; params = null;
    }
    @Override public void close() {
        if (closed) return;
        save(); state.off(); closed = true; main.removeCallbacksAndMessages(null); releaseVisual(); text = "";
    }
}
