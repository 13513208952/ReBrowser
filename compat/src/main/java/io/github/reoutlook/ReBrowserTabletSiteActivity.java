package io.github.reoutlook;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;
import org.json.JSONException;
import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoRuntimeSettings;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.WebExtension;
import org.mozilla.geckoview.WebExtensionController;

import java.time.LocalTime;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Gecko-only tablet web apps. Each app has its own persistent session context. */
public abstract class ReBrowserTabletSiteActivity extends Activity {
    private static final String RAIN_EXTENSION = "rainclass-foreground-playback@local";
    private static final String RAIN_NATIVE_APP = "rainclass";
    private static final String RAIN_KEY_REQUEST = "apiKey";
    // GeckoView 149 requires <all_urls> for tabs.captureTab, even on a single scoped tab.
    private static final String CAPTURE_ORIGIN = "<all_urls>";
    private static final String[] CAPTURE_ORIGINS = {CAPTURE_ORIGIN};
    private static final String[] NO_PERMISSIONS = {};
    private static final int FILE_REQUEST = 6101;

    public static final class ChatGpt extends ReBrowserTabletSiteActivity {
        @Override ReBrowserTabletApp app() { return ReBrowserTabletApp.CHATGPT; }
    }

    public static final class RainClass extends ReBrowserTabletSiteActivity {
        @Override ReBrowserTabletApp app() { return ReBrowserTabletApp.RAINCLASS; }
    }

    private final ArrayDeque<GeckoSession> pages = new ArrayDeque<>();
    private final Set<GeckoSession> backHistory = new HashSet<>();
    private GeckoRuntime runtime;
    private GeckoView view;
    private GeckoSession mainPage;
    private ReBrowserGeckoFileChooser chooser;
    private WebExtension extension;
    private ReBrowserRainSettings rainSettings;
    private final Set<WebExtension.Port> displayPorts = new HashSet<>();
    private boolean resumed;
    private boolean fullScreen;
    private ImageView chatGptRefresh;
    private final Runnable themeTick = new Runnable() {
        @Override public void run() {
            applyChatGptTheme();
            if (resumed && view != null) view.postDelayed(this, 60_000);
        }
    };

    abstract ReBrowserTabletApp app();

    private boolean rain() { return app() == ReBrowserTabletApp.RAINCLASS; }

    @Override protected final void onCreate(Bundle state) {
        super.onCreate(state);
        if (!available()) { finish(); return; }
        runtime = ReBrowserGeckoRuntime.get(this);
        chooser = new ReBrowserGeckoFileChooser(this, FILE_REQUEST);
        SystemBackDispatcher.register(this, this::back);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);
        WindowStyling.apply(this, root);
        view = new GeckoView(this);
        root.addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (rain()) {
            runtime.getSettings().setPreferredColorScheme(GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM);
            rainSettings = new ReBrowserRainSettings(this, root, view, this::rainDisplayChanged,
                    this::reloadPage, this::openRainHome);
        } else {
            addChatGptRefresh(root);
        }
        setContentView(root);
        if (!rain()) applyChatGptTheme();

        mainPage = newPage();
        mainPage.open(runtime);
        push(mainPage);
        if (!rain()) mainPage.loadUri("https://chatgpt.com/");
        else {
            extensions().ensureBuiltIn("resource://android/assets/extensions/rainclass/", RAIN_EXTENSION)
                    .accept(value -> runOnUiThread(() -> prepareRain(value)),
                            error -> runOnUiThread(() -> setupRain(null)));
        }
    }

    private boolean available() {
        return ReBrowserDevice.inspect(this).allowsTabletSites() && ReBrowserRuntime.inspect().browserAvailable();
    }

    private WebExtensionController extensions() { return runtime.getWebExtensionController(); }

    private void prepareRain(WebExtension value) {
        if (isFinishing() || isDestroyed()) return;
        if (value == null || !value.isBuiltIn || !RAIN_EXTENSION.equals(value.id)) {
            setupRain(null);
        } else if (hasCaptureOrigin(value)) {
            setupRain(value);
        } else {
            // The app owns the built-in extension's permission policy; do not prompt from the page.
            extensions().addOptionalPermissions(value.id, NO_PERMISSIONS, CAPTURE_ORIGINS, NO_PERMISSIONS)
                    .accept(granted -> runOnUiThread(() -> setupRain(hasCaptureOrigin(granted) ? granted : null)),
                            error -> runOnUiThread(() -> setupRain(null)));
        }
    }

    private static boolean hasCaptureOrigin(WebExtension extension) {
        return extension != null && (hasCaptureOrigin(extension.metaData.requiredOrigins)
                || hasCaptureOrigin(extension.metaData.grantedOptionalOrigins));
    }

    private static boolean hasCaptureOrigin(String[] origins) {
        if (origins == null) return false;
        for (String origin : origins) if (CAPTURE_ORIGIN.equals(origin)) return true;
        return false;
    }

    private void setupRain(WebExtension value) {
        if (isFinishing() || isDestroyed()) return;
        if (value == null) {
            Toast.makeText(this, "雨课堂插件无法启动，仍可使用网站。", Toast.LENGTH_LONG).show();
        } else {
            extension = value;
            bindRainPage(mainPage);
            // Only the built-in background script gets the key, never a content script.
            extension.setMessageDelegate(new WebExtension.MessageDelegate() {
                @Override public GeckoResult<Object> onMessage(String nativeApp, Object message,
                        WebExtension.MessageSender sender) {
                    boolean allowed = RAIN_NATIVE_APP.equals(nativeApp) && sender.session == null
                            && sender.environmentType == WebExtension.MessageSender.ENV_TYPE_EXTENSION
                            && RAIN_EXTENSION.equals(sender.webExtension.id) && resumed && hasWindowFocus()
                            && RAIN_KEY_REQUEST.equals(operation(message));
                    return GeckoResult.fromValue(allowed ? rainSettings.apiKey() : "");
                }
            }, RAIN_NATIVE_APP);
        }
        // The official entry displays QR login when signed out, and redirects signed-in users to /v2/web/index.
        mainPage.loadUri("https://www.yuketang.cn/web");
    }

    private void bindRainPage(GeckoSession page) {
        page.getWebExtensionController().setMessageDelegate(extension, new WebExtension.MessageDelegate() {
            @Override public GeckoResult<Object> onMessage(String nativeApp, Object message,
                    WebExtension.MessageSender sender) {
                if (!RAIN_NATIVE_APP.equals(nativeApp) || !ownsRainContent(page, sender)) {
                    return GeckoResult.fromValue(false);
                }
                String operation = operation(message);
                if ("activate".equals(operation)) return GeckoResult.fromValue(true);
                if ("settings".equals(operation) && resumed && hasWindowFocus() && pages.peek() == page) {
                    rainSettings.open();
                    return GeckoResult.fromValue(true);
                }
                return GeckoResult.fromValue(false);
            }

            @Override public void onConnect(WebExtension.Port port) {
                if (!ownsRainContent(page, port.sender)) {
                    port.disconnect();
                    return;
                }
                port.setDelegate(new WebExtension.PortDelegate() {
                    @Override public void onPortMessage(Object message, WebExtension.Port sender) {
                        if ("display".equals(operation(message))) sender.postMessage(rainDisplay());
                    }

                    @Override public void onDisconnect(WebExtension.Port disconnected) {
                        displayPorts.remove(disconnected);
                    }
                });
                displayPorts.add(port);
            }
        }, RAIN_NATIVE_APP);
    }


    private void reloadPage() {
        GeckoSession page = pages.peek();
        if (page != null) page.reload();
    }

    private void openRainHome() {
        GeckoSession page = pages.peek();
        if (page != null) page.loadUri("https://www.yuketang.cn/web");
    }

    private void addChatGptRefresh(FrameLayout root) {
        ImageView refresh = new ImageView(this);
        refresh.setImageResource(R.drawable.ic_rb_refresh);
        refresh.setContentDescription("刷新当前界面");
        refresh.setBackground(null);
        refresh.setPadding(dp(10), dp(10), dp(10), dp(10));
        refresh.setAlpha(0.82f);
        refresh.setOnClickListener(click -> reloadPage());
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(44), dp(44),
                Gravity.END | Gravity.BOTTOM);
        params.setMarginEnd(dp(14));
        params.bottomMargin = dp(14);
        root.addView(refresh, params);
        chatGptRefresh = refresh;
    }

    private void applyChatGptTheme() {
        if (rain() || runtime == null) return;
        boolean dark = darkHours();
        runtime.getSettings().setPreferredColorScheme(dark
                ? GeckoRuntimeSettings.COLOR_SCHEME_DARK : GeckoRuntimeSettings.COLOR_SCHEME_LIGHT);
        if (chatGptRefresh != null) {
            chatGptRefresh.setImageTintList(ColorStateList.valueOf(dark ? 0xFFE8EAED : 0xFF3C4043));
        }
    }

    private static boolean darkHours() {
        LocalTime now = LocalTime.now();
        return now.isBefore(LocalTime.of(6, 30)) || !now.isBefore(LocalTime.of(18, 0));
    }

    private void offerImageSave(String source, String base, int x, int y) {
        TextView item = new TextView(this);
        item.setText("保存到相册");
        item.setTextSize(15);
        item.setTextColor(0xff23272b);
        item.setPadding(dp(18), dp(12), dp(18), dp(12));
        item.setMinWidth(dp(168));
        android.util.TypedValue ripple = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);
        item.setBackgroundResource(ripple.resourceId);
        PopupWindow popup = new PopupWindow(item, ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        GradientDrawable card = new GradientDrawable();
        card.setColor(0xffffffff);
        card.setCornerRadius(dp(10));
        card.setStroke(dp(1), 0x1f000000);
        popup.setBackgroundDrawable(card);
        popup.setElevation(dp(8));
        popup.setOutsideTouchable(true);
        item.setOnClickListener(clicked -> {
            popup.dismiss();
            saveImage(source, base);
        });
        // Gecko reports page coordinates in CSS pixels, relative to the page viewport.
        float density = getResources().getDisplayMetrics().density;
        int[] origin = new int[2];
        view.getLocationOnScreen(origin);
        int left = origin[0] + Math.round(x * density);
        int top = origin[1] + Math.round(y * density);
        left = Math.max(dp(8), Math.min(left, origin[0] + view.getWidth() - dp(190)));
        top = Math.max(dp(8), Math.min(top, origin[1] + view.getHeight() - dp(80)));
        popup.showAtLocation(view, Gravity.NO_GRAVITY, left, top);
    }

    private void saveImage(String source, String base) {
        new Thread(() -> {
            try {
                String name = ReBrowserImageSaver.save(this, source, base);
                runOnUiThread(() -> Toast.makeText(this, "已保存到相册：" + name, Toast.LENGTH_SHORT).show());
            } catch (Exception error) {
                runOnUiThread(() -> Toast.makeText(this, "保存图片失败", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private int dp(int value) { return ReBrowserUi.dp(this, value); }

    private static boolean ownsRainContent(GeckoSession page, WebExtension.MessageSender sender) {
        return sender.session == page && sender.isTopLevel() && RAIN_EXTENSION.equals(sender.webExtension.id)
                && sender.environmentType == WebExtension.MessageSender.ENV_TYPE_CONTENT_SCRIPT
                && rainPluginPage(sender.url);
    }

    private static String operation(Object message) {
        return message instanceof JSONObject payload && payload.length() == 1
                ? payload.optString("type") : "";
    }

    private JSONObject rainDisplay() {
        try {
            return new JSONObject().put("plainAnswers", rainSettings.plainAnswers());
        } catch (JSONException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private void rainDisplayChanged() {
        if (displayPorts.isEmpty()) return;
        JSONObject display = rainDisplay();
        for (WebExtension.Port port : displayPorts) port.postMessage(display);
    }

    private GeckoSession newPage() {
        boolean desktop = rain();
        GeckoSession page = new GeckoSession(new GeckoSessionSettings.Builder()
                .contextId(rain() ? "rebrowser_rainclass_v1" : "rebrowser_chatgpt_v1")
                .userAgentMode(desktop ? GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
                        : GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
                .viewportMode(desktop ? GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
                        : GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
                .suspendMediaWhenInactive(true).build());
        page.setNavigationDelegate(new GeckoSession.NavigationDelegate() {
            @Override public GeckoResult<AllowOrDeny> onLoadRequest(GeckoSession session, LoadRequest request) {
                boolean allowed = "about:blank".equals(request.uri) || allowedSite(rain(), request.uri);
                return GeckoResult.fromValue(allowed ? AllowOrDeny.ALLOW : AllowOrDeny.DENY);
            }

            @Override public GeckoResult<GeckoSession> onNewSession(GeckoSession session, String uri) {
                if (!resumed || isFinishing() || pages.peek() != session || pages.size() >= 4
                        || (!allowedSite(rain(), uri) && !"about:blank".equals(uri))) return null;
                GeckoSession popup = newPage();
                if (extension != null) bindRainPage(popup);
                view.post(() -> attachNewPage(popup));
                return GeckoResult.fromValue(popup);
            }

            @Override public void onCanGoBack(GeckoSession session, boolean value) {
                if (value) backHistory.add(session);
                else backHistory.remove(session);
            }
        });
        page.setProgressDelegate(new GeckoSession.ProgressDelegate() {
            @Override public void onPageStart(GeckoSession session, String url) { chooser.onPageStart(session); }
            @Override public void onPageStop(GeckoSession session, boolean success) {
                chooser.onPageStop(session, success);
            }
        });
        page.setContentDelegate(new GeckoSession.ContentDelegate() {
            @Override public void onCloseRequest(GeckoSession session) {
                if (pages.peek() == session && pages.size() > 1) pop();
            }

            @Override public void onFullScreen(GeckoSession session, boolean enabled) {
                if (pages.peek() == session) showFullScreen(enabled);
            }

            @Override public void onContextMenu(GeckoSession session, int x, int y,
                    GeckoSession.ContentDelegate.ContextElement element) {
                if (rain() && element != null
                        && element.type == GeckoSession.ContentDelegate.ContextElement.TYPE_IMAGE
                        && element.srcUri != null && element.srcUri.startsWith("https://")) {
                    offerImageSave(element.srcUri, element.baseUri, x, y);
                }
            }
        });
        page.setPromptDelegate(new GeckoSession.PromptDelegate() {
            @Override public GeckoResult<PromptResponse> onFilePrompt(GeckoSession session, FilePrompt prompt) {
                if (!resumed || pages.peek() != session) return GeckoResult.fromValue(prompt.dismiss());
                return chooser.onFilePrompt(session, prompt);
            }
        });
        return page;
    }

    private static boolean allowedSite(boolean rain, String value) {
        if (ReBrowserExternalNavigation.httpUrl(value) == null) return false;
        Uri url = Uri.parse(value);
        if (!"https".equalsIgnoreCase(url.getScheme()) || (url.getPort() != -1 && url.getPort() != 443)) return false;
        String host = url.getHost().toLowerCase(Locale.ROOT);
        if (rain) return host.equals("yuketang.cn") || host.endsWith(".yuketang.cn")
                || host.equals("examination.xuetangx.com") || host.equals("open.weixin.qq.com");
        return host.equals("chatgpt.com") || host.endsWith(".chatgpt.com")
                || host.equals("auth.openai.com") || host.equals("auth0.openai.com")
                || host.equals("accounts.google.com") || host.equals("login.microsoftonline.com")
                || host.equals("login.live.com")
                || host.equals("appleid.apple.com");
    }

    private static boolean rainPluginPage(String value) {
        if (!allowedSite(true, value)) return false;
        Uri url = Uri.parse(value);
        String path = url.getPath();
        if (path == null) return false;
        if ("www.yuketang.cn".equalsIgnoreCase(url.getHost())) {
            return path.startsWith("/ai-workspace/lms-graph/") || path.startsWith("/v2/web/exam/")
                    || path.startsWith("/v2/web/iframe-quiz/") || path.startsWith("/v2/web/studentQuiz_web/")
                    || path.startsWith("/v2/web/quizResult/");
        }
        return "examination.xuetangx.com".equalsIgnoreCase(url.getHost())
                && (path.startsWith("/exam/") || path.startsWith("/result/"));
    }

    private void showFullScreen(boolean enabled) {
        fullScreen = enabled;
        if (rainSettings != null) rainSettings.setFullscreen(enabled);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController insets = getWindow().getInsetsController();
            if (insets != null) {
                if (enabled) {
                    insets.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                    insets.hide(WindowInsets.Type.systemBars());
                } else insets.show(WindowInsets.Type.systemBars());
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(enabled
                    ? View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    : View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                            | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
    }

    private void attachNewPage(GeckoSession session) {
        if (isFinishing() || isDestroyed()) { session.close(); return; }
        push(session);
    }

    private void push(GeckoSession page) {
        GeckoSession previous = pages.peek();
        if (previous != null) {
            if (fullScreen) {
                previous.exitFullScreen();
                showFullScreen(false);
            }
            setActive(previous, false);
            view.releaseSession();
        }
        pages.push(page);
        attach(page);
    }

    private void pop() {
        GeckoSession page = pages.pop();
        if (fullScreen) showFullScreen(false);
        backHistory.remove(page);
        setActive(page, false);
        view.releaseSession();
        chooser.cancel(page);
        page.close();
        attach(pages.element());
    }

    private void attach(GeckoSession page) {
        view.setSession(page);
        view.requestFocus();
        setActive(page, resumed);
    }

    private void setActive(GeckoSession page, boolean active) {
        if (page == null) return;
        page.setActive(active);
        page.setFocused(active && hasWindowFocus());
        if (rain()) extensions().setTabActive(page, active && hasWindowFocus());
    }

    private void back() {
        if (rainSettings != null && rainSettings.close()) return;
        else if (fullScreen && pages.peek() != null) pages.element().exitFullScreen();
        else if (backHistory.contains(pages.peek())) pages.element().goBack();
        else if (pages.size() > 1) pop();
        else finish();
    }

    @Override public final void onBackPressed() { back(); }

    @Override protected final void onResume() {
        super.onResume();
        if (!available()) { finish(); return; }
        resumed = true;
        setActive(pages.peek(), true);
        if (!rain()) {
            applyChatGptTheme();
            view.removeCallbacks(themeTick);
            view.postDelayed(themeTick, 60_000);
        }
    }

    @Override protected final void onPause() {
        resumed = false;
        if (view != null) view.removeCallbacks(themeTick);
        if (rainSettings != null) rainSettings.saveEdits();
        setActive(pages.peek(), false);
        super.onPause();
    }

    @Override public final void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        if (!available()) finish();
    }

    @Override public final void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        setActive(pages.peek(), resumed);
    }

    @Override protected final void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (chooser != null) chooser.onActivityResult(code, result, data);
    }

    @Override protected final void onDestroy() {
        if (extension != null) extension.setMessageDelegate(null, RAIN_NATIVE_APP);
        displayPorts.clear();
        if (chooser != null) chooser.close();
        if (view != null && view.getSession() != null) view.releaseSession();
        while (!pages.isEmpty()) pages.pop().close();
        super.onDestroy();
    }
}
