package io.github.reoutlook;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

final class ReBrowserRainSettings {
    private static final String KEY = "api_key";
    private static final String PLAIN = "plain_answers";
    private final SharedPreferences preferences;
    private final FrameLayout controls;
    private final View page;
    private final View scrim;
    private final ScrollView drawer;
    private final TextView ball;
    private final EditText apiKey;

    ReBrowserRainSettings(Activity activity, FrameLayout root, View page, Runnable displayChanged,
                          Runnable refresh, Runnable home) {
        this.page = page;
        preferences = activity.getSharedPreferences("rebrowser_rain_settings_v1", Context.MODE_PRIVATE);
        controls = new FrameLayout(activity);
        root.addView(controls, ReBrowserUi.matchMatch());
        scrim = new View(activity);
        scrim.setBackgroundColor(0x52000000);
        scrim.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        scrim.setOnClickListener(view -> close());
        controls.addView(scrim, ReBrowserUi.matchMatch());

        drawer = new ScrollView(activity);
        drawer.setFocusableInTouchMode(true);
        drawer.setClickable(true);
        drawer.setAccessibilityPaneTitle("雨课堂 Web 设置");
        ReBrowserUi.styleAppDrawer(drawer);
        controls.addView(drawer, new FrameLayout.LayoutParams(ReBrowserUi.appDrawerWidth(activity),
                FrameLayout.LayoutParams.MATCH_PARENT, Gravity.START));
        LinearLayout fields = new LinearLayout(activity);
        fields.setOrientation(LinearLayout.VERTICAL);
        drawer.addView(fields, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        ReBrowserUi.addAppDrawerHeading(fields, "雨课堂 Web");

        TextView keyLabel = new TextView(activity);
        keyLabel.setText("DeepSeek API 密钥：");
        keyLabel.setTextColor(0xff23272b);
        keyLabel.setTextSize(15);
        keyLabel.setTypeface(null, Typeface.BOLD);
        fields.addView(keyLabel);
        apiKey = new EditText(activity);
        apiKey.setId(View.generateViewId());
        keyLabel.setLabelFor(apiKey.getId());
        apiKey.setSingleLine(true);
        apiKey.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        apiKey.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        apiKey.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        apiKey.setTextSize(15);
        apiKey.setTextColor(0xff23272b);
        apiKey.setPadding(dp(14), 0, dp(14), 0);
        apiKey.setBackground(ReBrowserUi.roundedBackground(0xffe5f2fc, dp(12)));
        apiKey.setOnEditorActionListener((view, action, event) -> action == EditorInfo.IME_ACTION_DONE && close());
        fields.addView(apiKey, fieldLayout());

        Switch plain = new Switch(activity);
        plain.setText("去除 LaTeX（纯文本模式）回复");
        plain.setTextColor(0xff23272b);
        plain.setTextSize(15);
        plain.setPadding(dp(14), 0, dp(12), 0);
        plain.setBackground(ReBrowserUi.roundedBackground(0xfff1f5f8, dp(12)));
        plain.setChecked(plainAnswers());
        plain.setOnCheckedChangeListener((button, checked) -> {
            preferences.edit().putBoolean(PLAIN, checked).apply();
            displayChanged.run();
        });
        fields.addView(plain, fieldLayout());

        fields.addView(action("刷新当前界面", refresh), fieldLayout());
        fields.addView(action("回到雨课堂首页", home), fieldLayout());

        ball = new TextView(activity);
        ball.setText("雨");
        ball.setContentDescription("打开雨课堂设置");
        ball.setTextSize(17);
        ball.setTextColor(Color.WHITE);
        ball.setGravity(Gravity.CENTER);
        ball.setTypeface(null, Typeface.BOLD);
        ball.setElevation(dp(8));
        ball.setAlpha(0.68f);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(0xff0f6cbd);
        ball.setBackground(circle);
        ball.setOnClickListener(view -> open());
        FrameLayout.LayoutParams position = new FrameLayout.LayoutParams(dp(48), dp(48),
                Gravity.END | Gravity.CENTER_VERTICAL);
        position.setMarginEnd(dp(12));
        controls.addView(ball, position);
        drawer.setVisibility(View.GONE);
        scrim.setVisibility(View.GONE);
    }

    String apiKey() { return preferences.getString(KEY, ""); }
    boolean plainAnswers() { return preferences.getBoolean(PLAIN, false); }

    void open() {
        if (controls.getVisibility() != View.VISIBLE || drawer.getVisibility() == View.VISIBLE) return;
        apiKey.setText(apiKey());
        drawer.setVisibility(View.VISIBLE);
        drawer.requestFocus();
        int width = drawer.getLayoutParams().width;
        drawer.setTranslationX(drawer.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL ? width : -width);
        drawer.animate().translationX(0).setDuration(220).start();
        scrim.setVisibility(View.VISIBLE);
        ball.setVisibility(View.GONE);
        page.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
    }

    boolean close() {
        if (drawer.getVisibility() != View.VISIBLE) return false;
        saveEdits();
        InputMethodManager ime = controls.getContext().getSystemService(InputMethodManager.class);
        if (ime != null && apiKey.hasFocus()) ime.hideSoftInputFromWindow(apiKey.getWindowToken(), 0);
        apiKey.setText("");
        drawer.animate().cancel();
        drawer.setVisibility(View.GONE);
        scrim.setVisibility(View.GONE);
        ball.setVisibility(View.VISIBLE);
        page.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        page.requestFocus();
        return true;
    }

    void saveEdits() {
        if (drawer.getVisibility() != View.VISIBLE) return;
        String key = apiKey.getText().toString().trim();
        if (key.equals(apiKey())) return;
        if (key.isEmpty()) preferences.edit().remove(KEY).apply();
        else preferences.edit().putString(KEY, key).apply();
    }

    void setFullscreen(boolean fullscreen) {
        if (fullscreen) close();
        controls.setVisibility(fullscreen ? View.GONE : View.VISIBLE);
    }

    private TextView action(String label, Runnable action) {
        TextView button = new TextView(controls.getContext());
        button.setText(label);
        button.setTextColor(0xff23272b);
        button.setTextSize(15);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(14), 0, dp(14), 0);
        button.setBackground(ReBrowserUi.roundedBackground(0xffe5f2fc, dp(12)));
        button.setOnClickListener(view -> {
            close();
            action.run();
        });
        return button;
    }

    private LinearLayout.LayoutParams fieldLayout() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        params.topMargin = dp(10);
        return params;
    }

    private int dp(int value) { return ReBrowserUi.dp(controls.getContext(), value); }
}
