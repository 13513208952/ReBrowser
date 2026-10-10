package io.github.reoutlook;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.function.Consumer;

/** Native local page. History data is never injected into any website's DOM or JS bridge. */
@SuppressLint({"SetTextI18n", "ViewConstructor"})
final class ReBrowserNewTabView extends ScrollView {
    private final ReBrowserHistory history;
    private final ReBrowserPreferences preferences;
    private final Consumer<String> navigate;
    private final LinearLayout body;
    private final LinearLayout recent;
    private final FrameLayout logoPanel;
    private final int logoWidth;
    private final EditText input;
    private final String signature;

    ReBrowserNewTabView(Context context, ReBrowserPreferences preferences, ReBrowserHistory history,
            Consumer<String> navigate) {
        super(context);
        this.history = history; this.preferences = preferences; this.navigate = navigate;
        signature = signature(preferences);
        setFillViewport(true);
        setClipToPadding(false);
        setVerticalScrollBarEnabled(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        FrameLayout frame = new FrameLayout(context);
        addView(frame, new ScrollView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, dp(44), 0, dp(28));
        FrameLayout.LayoutParams bodyParams = new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL);
        bodyParams.setMargins(dp(18), 0, dp(18), 0);
        frame.addView(body, bodyParams);

        boolean photo = preferences.exclusiveBackgroundEnabled();
        FrameLayout logoSlot = new FrameLayout(context);
        logoPanel = new FrameLayout(context);
        String selectedEngine = preferences.searchEngine();
        boolean google = ReBrowserPreferences.SEARCH_GOOGLE.equals(selectedEngine);
        if (photo && !google && !ReBrowserPreferences.SEARCH_BAIDU.equals(selectedEngine)) {
            // Retain the existing logo spacing, but no opaque backing or recoloring.
            logoPanel.setPadding(dp(12), dp(8), dp(12), dp(8));
        }
        ImageView wordmark = new ImageView(context);
        wordmark.setImageResource(ReBrowserSearchEngineAssets.logo(selectedEngine, photo));
        wordmark.setScaleType(ImageView.ScaleType.FIT_CENTER);
        wordmark.setContentDescription("默认搜索引擎：" + preferences.searchEngineName());
        logoPanel.addView(wordmark, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        if (ReBrowserPreferences.SEARCH_DUCKDUCKGO.equals(selectedEngine)) {
            // The brand guide requests a registered mark for prominent external placements.
            TextView registered = label("®", 9, 0xff333333);
            registered.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            FrameLayout.LayoutParams mark = new FrameLayout.LayoutParams(dp(12), dp(16), Gravity.END | Gravity.TOP);
            mark.topMargin = dp(12);
            logoPanel.addView(registered, mark);
        }
        logoWidth = ReBrowserPreferences.SEARCH_DUCKDUCKGO.equals(selectedEngine) ? 290 : 240;
        logoSlot.addView(logoPanel, new FrameLayout.LayoutParams(dp(logoWidth), dp(84), Gravity.CENTER));
        body.addView(logoSlot, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(84)));

        LinearLayout search = new LinearLayout(context);
        search.setGravity(Gravity.CENTER_VERTICAL);
        search.setPadding(dp(16), 0, dp(8), 0);
        search.setBackground(surface(photo ? Color.WHITE : 0xfff0f1f5, 32, false));
        search.setElevation(photo ? dp(2) : 0);
        ImageView engine = new ImageView(context);
        engine.setImageResource(ReBrowserSearchEngineAssets.icon(preferences.searchEngine()));
        engine.setScaleType(ImageView.ScaleType.FIT_CENTER);
        engine.setContentDescription(preferences.searchEngineName() + " 搜索");
        search.addView(engine, new LinearLayout.LayoutParams(dp(28), dp(28)));
        input = new EditText(context);
        input.setSingleLine(true);
        input.setTextSize(16);
        input.setTextColor(0xff282633);
        input.setHintTextColor(0xff686472);
        input.setHint("搜索或输入网址");
        input.setContentDescription("新标签页搜索或输入网址");
        input.setBackgroundColor(Color.TRANSPARENT);
        input.setPadding(dp(12), 0, dp(4), 0);
        input.setImeOptions(EditorInfo.IME_ACTION_GO);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setOnEditorActionListener((v, action, event) -> {
            if (action == EditorInfo.IME_ACTION_GO || action == EditorInfo.IME_ACTION_SEARCH
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                        && event.getAction() == KeyEvent.ACTION_DOWN)) {
                submit(); return true;
            }
            return false;
        });
        search.addView(input, new LinearLayout.LayoutParams(0, dp(62), 1));
        ImageButton submit = new ImageButton(context);
        submit.setImageResource(R.drawable.ic_rb_submit);
        submit.setContentDescription("提交搜索或网址");
        submit.setBackgroundResource(android.R.drawable.list_selector_background);
        submit.setPadding(dp(12), dp(12), dp(12), dp(12));
        submit.setOnClickListener(v -> submit());
        search.addView(submit, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout.LayoutParams searchParams = fullWidth();
        searchParams.setMargins(0, dp(18), 0, dp(20));
        body.addView(search, searchParams);

        recent = new LinearLayout(context);
        recent.setOrientation(LinearLayout.VERTICAL);
        recent.setPadding(dp(14), dp(12), dp(10), dp(10));
        recent.setBackground(surface(photo ? 0xfffaf7ff : Color.WHITE, 24, !photo));
        body.addView(recent, fullWidth());
        refreshRecent();

        // The page must not automatically focus the input and summon the keyboard.
        frame.setFocusableInTouchMode(true);
        frame.requestFocus();
    }

    static boolean isBlank(String url) { return "about:blank".equalsIgnoreCase(url); }
    static String signature(ReBrowserPreferences preferences) {
        return preferences.searchEngine() + ":" + preferences.exclusiveBackgroundEnabled()
                + ":" + preferences.newTabRecentEnabled();
    }
    boolean matchesPreferences() { return signature.equals(signature(preferences)); }

    void refreshRecent() {
        recent.removeAllViews();
        recent.setVisibility(GONE);
        if (!preferences.newTabRecentEnabled()) return;
        try {
            java.util.List<ReBrowserHistory.Entry> entries = history.recentEntries(8);
            if (entries.isEmpty()) return;
            recent.setVisibility(VISIBLE);
            HorizontalScrollView scroll = new HorizontalScrollView(getContext());
            scroll.setHorizontalScrollBarEnabled(false);
            LinearLayout items = new LinearLayout(getContext());
            for (ReBrowserHistory.Entry entry : entries) items.addView(shortcut(entry));
            scroll.addView(items, new HorizontalScrollView.LayoutParams(LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT));
            recent.addView(scroll, fullWidth());
        } catch (android.database.sqlite.SQLiteException error) {
            recent.setVisibility(GONE); // Optional shortcuts must not replace the simple frame with notices.
        }
    }

    private View shortcut(ReBrowserHistory.Entry entry) {
        LinearLayout item = new LinearLayout(getContext());
        item.setOrientation(LinearLayout.VERTICAL); item.setGravity(Gravity.CENTER_HORIZONTAL);
        item.setPadding(dp(4), dp(8), dp(4), dp(6));
        item.setLayoutParams(new LinearLayout.LayoutParams(dp(82), LayoutParams.WRAP_CONTENT));
        item.setMinimumHeight(dp(106));
        String host = Uri.parse(entry.url).getHost();
        if (host == null) host = "网页";
        if (host.startsWith("www.")) host = host.substring(4);
        if (host.isEmpty()) host = "网页";
        TextView badge = label(host.substring(0, 1).toUpperCase(java.util.Locale.ROOT), 22, 0xff66557d);
        badge.setGravity(Gravity.CENTER); badge.setBackground(surface(0xffeee8f4, 26, false));
        badge.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        item.addView(badge, new LinearLayout.LayoutParams(dp(50), dp(50)));
        TextView title = label(entry.title, 12, 0xff514858);
        title.setMaxLines(2); title.setEllipsize(TextUtils.TruncateAt.END);
        title.setGravity(Gravity.CENTER); title.setPadding(0, dp(6), 0, 0);
        item.addView(title, fullWidth());
        item.setContentDescription(entry.title + "，" + host);
        item.setBackgroundResource(android.R.drawable.list_selector_background);
        item.setOnClickListener(v -> {
            try {
                ReBrowserHistory.Entry live = history.find(entry.id);
                if (live != null) navigate.accept(live.url);
                else {
                    Toast.makeText(getContext(), "这条访问记录已删除或到期", Toast.LENGTH_SHORT).show();
                    refreshRecent();
                }
            } catch (android.database.sqlite.SQLiteException error) { refreshRecent(); }
        });
        return item;
    }

    private void submit() {
        String value = input.getText().toString().trim();
        if (value.isEmpty()) return;
        dismissInput(); navigate.accept(value);
    }
    boolean dismissInput() {
        if (!input.hasFocus()) return false;
        input.clearFocus();
        InputMethodManager keyboard = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(input.getWindowToken(), 0);
        return true;
    }
    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) body.getLayoutParams();
        params.width = Math.min(Math.max(1, w - dp(36)), dp(720));
        body.setLayoutParams(params);
        FrameLayout.LayoutParams logoParams = (FrameLayout.LayoutParams) logoPanel.getLayoutParams();
        logoParams.width = Math.min(params.width, dp(logoWidth));
        logoPanel.setLayoutParams(logoParams);
        body.setPadding(0, dp(w > h ? 12 : 44), 0, dp(28));
    }
    private TextView label(String text, int size, int color) {
        TextView label = new TextView(getContext()); label.setText(text);
        label.setTextSize(size); label.setTextColor(color); return label;
    }
    private GradientDrawable surface(int color, int radius, boolean border) {
        GradientDrawable shape = ReBrowserUi.roundedBackground(color, dp(radius));
        if (border) shape.setStroke(dp(1), 0xffe8e5ed);
        return shape;
    }
    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
    private int dp(int value) { return ReBrowserUi.dp(getContext(), value); }
}
