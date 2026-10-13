package io.github.reoutlook;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** Browser-global settings. Website state remains inside each workspace's named Profile. */
@SuppressLint("SetTextI18n")
public final class ReBrowserSettingsActivity extends Activity {
    private ReBrowserPreferences preferences;
    private ReOutlookLauncherVisibility launcherVisibility;
    private Switch outlookLauncherSwitch;
    private Button outlookOpenButton;
    private TextView runtimeMessage;
    private EditText homeUrl;
    private Spinner searchEngine;
    private Spinner deviceType;
    private TextView deviceStatus;
    private LinearLayout videoOverrideRow;
    private LinearLayout videoOrientationSelector;
    private TextView videoLandscape;
    private TextView videoAuto;
    private TextView videoPortrait;
    private final ScrollView[] pages = new ScrollView[ReBrowserSettings.SECTIONS.length];
    private final TextView[] pageButtons = new TextView[ReBrowserSettings.SECTIONS.length];
    private final LinearLayout[] pageContents = new LinearLayout[ReBrowserSettings.SECTIONS.length];
    private String selectedSection = ReBrowserSettings.GENERAL;
    private String initialHome;
    private int initialSearch;
    private ReBrowserSitePermissionsView sitePermissionsView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        preferences = new ReBrowserPreferences(this);
        launcherVisibility = new ReOutlookLauncherVisibility(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(247, 248, 252));
        WindowStyling.apply(this, root);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(0, insets.getSystemWindowInsetTop(),
                    0, insets.getSystemWindowInsetBottom());
            return insets;
        });

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(22), dp(18), dp(22), dp(8));
        root.addView(header, matchWrap());

        TextView title = new TextView(this);
        title.setText("ReBrowser 设置");
        title.setTextSize(26);
        title.setTextColor(Color.rgb(31, 36, 45));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        header.addView(title, matchWrap());

        TextView scope = new TextView(this);
        scope.setText("这些设置由同一应用内的所有 ReBrowser 总标签页共享；网站登录状态仍按总标签页 Profile 隔离。");
        scope.setTextSize(13);
        scope.setTextColor(Color.rgb(93, 100, 111));
        LinearLayout.LayoutParams scopeParams = matchWrap();
        scopeParams.setMargins(0, dp(8), 0, dp(14));
        header.addView(scope, scopeParams);
        LinearLayout navigation = new LinearLayout(this);
        navigation.setPadding(dp(3), dp(3), dp(3), dp(3));
        navigation.setBackground(roundedBackground(0xffe7e9ef, dp(14)));
        String[] sectionLabels = {"常规", "隔离与权限", "应用管理"};
        for (int i = 0; i < pages.length; i++) {
            final String section = ReBrowserSettings.SECTIONS[i];
            TextView button = new TextView(this);
            button.setText(sectionLabels[i]); button.setTextSize(14); button.setGravity(Gravity.CENTER);
            button.setContentDescription(sectionLabels[i] + "设置");
            button.setOnClickListener(v -> showSection(section));
            navigation.addView(button, new LinearLayout.LayoutParams(0, dp(48), 1));
            pageButtons[i] = button;
        }
        header.addView(navigation, matchWrap());
        FrameLayout pageHost = new FrameLayout(this);
        root.addView(pageHost, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        for (int i = 0; i < pages.length; i++) {
            ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
            scroll.setTag(ReBrowserSettings.SECTIONS[i]);
            LinearLayout panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL);
            panel.setPadding(dp(22), 0, dp(22), dp(20));
            scroll.addView(panel, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            pageHost.addView(scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            pages[i] = scroll; pageContents[i] = panel;
        }
        LinearLayout content = pageContents[0];
        LinearLayout isolation = pageContents[1];
        LinearLayout applications = pageContents[2];
        runtimeMessage = fieldLabel("");
        applications.addView(runtimeMessage);
        applications.addView(sectionTitle("子软件"));
        LinearLayout applicationRow = new LinearLayout(this);
        applicationRow.setGravity(Gravity.CENTER_VERTICAL);
        applicationRow.setPadding(dp(8), dp(12), dp(8), dp(12));
        applicationRow.setBackground(roundedBackground(Color.WHITE, dp(14)));
        LinearLayout name = new LinearLayout(this);
        name.setOrientation(LinearLayout.VERTICAL);
        name.addView(fieldLabel("ReOutlook"));
        name.addView(fieldLabel("邮件模式 · 与本应用共用安装包"));
        applicationRow.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        outlookLauncherSwitch = new Switch(this);
        outlookLauncherSwitch.setContentDescription("显示 ReOutlook 桌面入口");
        outlookLauncherSwitch.setChecked(launcherVisibility.visible());
        outlookLauncherSwitch.setOnCheckedChangeListener(this::onOutlookLauncherChanged);
        applicationRow.addView(outlookLauncherSwitch);
        outlookOpenButton = new Button(this);
        outlookOpenButton.setText("进入");
        outlookOpenButton.setContentDescription("进入 ReOutlook 模式");
        outlookOpenButton.setOnClickListener(v -> ModeRouter.openOutlook(this));
        applicationRow.addView(outlookOpenButton);
        applications.addView(applicationRow);
        applications.addView(fieldLabel("开关仅控制桌面入口；隐藏时仍可点击进入。新安装默认隐藏，升级保留已有选择。"));

        content.addView(sectionTitle("启动与搜索"));
        content.addView(fieldLabel("主页网址"));
        homeUrl = new EditText(this);
        homeUrl.setSingleLine(true);
        initialHome = preferences.homeUrl();
        homeUrl.setText(savedInstanceState == null ? initialHome : savedInstanceState.getString("homeDraft", initialHome));
        homeUrl.setHint("https://cn.bing.com/");
        content.addView(homeUrl, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

        content.addView(fieldLabel("默认搜索引擎"));
        searchEngine = new Spinner(this);
        String[] labels = {"Bing", "百度", "DuckDuckGo", "Google"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, labels);
        searchEngine.setAdapter(adapter);
        String currentSearch = preferences.searchEngine();
        initialSearch = searchPosition(currentSearch);
        searchEngine.setSelection(savedInstanceState == null ? initialSearch : savedInstanceState.getInt("searchDraft", initialSearch));
        content.addView(searchEngine, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

        isolation.addView(sectionTitle("网页行为"));
        isolation.addView(settingSwitch(
                "JavaScript",
                "关闭后部分网站将无法正常工作",
                preferences.javascriptEnabled(),
                preferences::setJavascriptEnabled));
        isolation.addView(settingSwitch(
                "允许第三方 Cookie",
                "按总标签页 Profile 分别保存；不会与 ReOutlook 共用",
                preferences.thirdPartyCookiesEnabled(),
                preferences::setThirdPartyCookiesEnabled));
        content.addView(sectionTitle("网页显示"));
        content.addView(settingSwitch(
                "桌面版网站",
                "新建或重新载入的页面使用桌面布局",
                preferences.desktopModeEnabled(),
                preferences::setDesktopModeEnabled));
        isolation.addView(sectionTitle("总标签页生命周期"));
        isolation.addView(settingSwitch(
                "允许强行提升为主标签页",
                "开启后，临时总标签页可跳过副级别直接提升为主；其他生命周期规则不变",
                preferences.forcePrimaryPromotionEnabled(),
                preferences::setForcePrimaryPromotionEnabled));
        isolation.addView(sectionTitle("网站权限管理"));
        sitePermissionsView = new ReBrowserSitePermissionsView(this, new ReBrowserSitePermissions(this));
        isolation.addView(sitePermissionsView, matchWrap());

        content.addView(sectionTitle("设备与方向"));
        content.addView(fieldLabel("设备类型"));
        deviceType = new Spinner(this);
        deviceType.setContentDescription("设备类型：自动、手机或平板");
        deviceType.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"自动识别", "手机", "平板"}));
        deviceType.setSelection(devicePosition(preferences.deviceType()));
        deviceType.setOnItemSelectedListener(deviceListener);
        content.addView(deviceType, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        deviceStatus = fieldLabel("");
        content.addView(deviceStatus);
        content.addView(fieldLabel("自动按屏幕物理 DPI 计算对角线，7 英寸及以上识别为平板；可手动纠正。"
                + "平板完整窗口遵守系统方向锁定，允许四个朝向；分屏、小窗跟随窗口。设置界面固定竖屏。"));
        refreshDeviceStatus();

        content.addView(sectionTitle("视频播放器"));
        videoOverrideRow = settingSwitch(
                "覆写视频播放器界面方向",
                "仅手机生效；平板完整窗口始终遵守系统四方向策略",
                preferences.videoOrientationOverrideEnabled(),
                enabled -> {
                    preferences.setVideoOrientationOverrideEnabled(enabled);
                    updateVideoOrientationSelector();
                });
        content.addView(videoOverrideRow);
        videoOrientationSelector = createVideoOrientationSelector();
        content.addView(videoOrientationSelector, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
        updateVideoOrientationSelector();

        content.addView(sectionTitle("系统默认浏览器"));
        Button defaultBrowser = new Button(this);
        defaultBrowser.setText("选择默认浏览器");
        defaultBrowser.setContentDescription("选择默认浏览器，打开 Android 系统默认应用设置");
        defaultBrowser.setOnClickListener(v -> ReBrowserDefaultBrowser.openSettings(this));
        content.addView(defaultBrowser, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));

        Button save = new Button(this);
        save.setText("保存并返回");
        save.setOnClickListener(view -> {
            saveSettings(true);
            finish();
        });
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        saveParams.setMargins(dp(22), dp(8), dp(22), dp(12));
        root.addView(save, saveParams);
        showSection(savedInstanceState == null ? getIntent().getStringExtra(ReBrowserSettings.EXTRA_SECTION)
                : savedInstanceState.getString(ReBrowserSettings.EXTRA_SECTION));
        if (savedInstanceState != null) for (int i = 0; i < pages.length; i++) {
            final ScrollView page = pages[i];
            final int position = savedInstanceState.getInt("scroll_" + ReBrowserSettings.SECTIONS[i], 0);
            page.post(() -> page.scrollTo(0, position));
        }
        setContentView(root);
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        // Reordering an existing native page must honor the requested section
        // without clearing its drafts or finishing a browser activity above it.
        showSection(intent.getStringExtra(ReBrowserSettings.EXTRA_SECTION));
    }

    @Override
    protected void onResume() {
        super.onResume();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        if (deviceType != null) {
            deviceType.setOnItemSelectedListener(null);
            deviceType.setSelection(devicePosition(preferences.deviceType()));
            deviceType.setOnItemSelectedListener(deviceListener);
            refreshDeviceStatus();
        }
        if (homeUrl != null && homeUrl.getText().toString().equals(initialHome)) {
            initialHome = preferences.homeUrl();
            homeUrl.setText(initialHome);
        }
        if (searchEngine != null && searchEngine.getSelectedItemPosition() == initialSearch) {
            initialSearch = searchPosition(preferences.searchEngine());
            searchEngine.setSelection(initialSearch);
        }
        ReBrowserRuntime.Status runtime = ReBrowserRuntime.inspect();
        if (runtimeMessage != null) runtimeMessage.setText(runtime.browserAvailable()
                ? "ReBrowser 浏览功能可用。子软件是同一应用内的独立模式。"
                : runtime.provider ? "当前 WebView 不满足 ReBrowser 隔离浏览要求；仍可进入 ReOutlook。此页面不启动浏览器工作区。"
                : "当前没有可用 WebView；原生应用管理仍可使用，安装或启用 WebView 后再进入网页模式。");
        if (outlookOpenButton != null) outlookOpenButton.setEnabled(runtime.provider);
        if (sitePermissionsView != null && ReBrowserSettings.ISOLATION.equals(selectedSection)) sitePermissionsView.refresh();
        // PackageManager is the single source of truth, including after process restarts.
        if (outlookLauncherSwitch != null) {
            outlookLauncherSwitch.setOnCheckedChangeListener(null);
            outlookLauncherSwitch.setChecked(launcherVisibility.visible());
            outlookLauncherSwitch.setOnCheckedChangeListener(this::onOutlookLauncherChanged);
        }
    }

    private void showSection(String value) {
        selectedSection = ReBrowserSettings.section(value);
        for (int i = 0; i < pages.length; i++) {
            boolean selected = selectedSection.equals(ReBrowserSettings.SECTIONS[i]);
            pages[i].setVisibility(selected ? View.VISIBLE : View.GONE);
            pageButtons[i].setSelected(selected);
            pageButtons[i].setTextColor(selected ? Color.WHITE : 0xff373b44);
            pageButtons[i].setBackground(roundedBackground(selected ? 0xff5b46b4 : Color.TRANSPARENT, dp(11)));
        }
        if (sitePermissionsView != null && ReBrowserSettings.ISOLATION.equals(selectedSection)) sitePermissionsView.refresh();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString(ReBrowserSettings.EXTRA_SECTION, selectedSection);
        state.putString("homeDraft", homeUrl.getText().toString());
        state.putInt("searchDraft", searchEngine.getSelectedItemPosition());
        for (int i = 0; i < pages.length; i++) state.putInt("scroll_" + ReBrowserSettings.SECTIONS[i], pages[i].getScrollY());
        super.onSaveInstanceState(state);
    }

    private void onOutlookLauncherChanged(CompoundButton button, boolean visible) {
        try {
            launcherVisibility.setVisible(visible);
        } catch (RuntimeException error) {
            button.setOnCheckedChangeListener(null);
            button.setChecked(!visible);
            button.setOnCheckedChangeListener(this::onOutlookLauncherChanged);
            Toast.makeText(this, "桌面入口更新失败，请重试", Toast.LENGTH_SHORT).show();
        }
    }

    private TextView sectionTitle(String text) {
        TextView title = new TextView(this);
        title.setText(text);
        title.setTextSize(16);
        title.setTextColor(Color.rgb(91, 70, 180));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(0, dp(18), 0, dp(8));
        return title;
    }

    private TextView fieldLabel(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(13);
        label.setTextColor(Color.rgb(82, 88, 98));
        label.setPadding(0, dp(8), 0, 0);
        return label;
    }

    private LinearLayout createVideoOrientationSelector() {
        LinearLayout selector = new LinearLayout(this);
        selector.setOrientation(LinearLayout.HORIZONTAL);
        selector.setGravity(Gravity.CENTER);
        selector.setPadding(dp(3), dp(3), dp(3), dp(3));
        selector.setBackground(roundedBackground(Color.rgb(231, 233, 239), dp(14)));
        videoLandscape = videoOrientationChoice(
                "横屏锁定", ReBrowserPreferences.ORIENTATION_LANDSCAPE);
        videoAuto = videoOrientationChoice(
                "自动旋转", ReBrowserPreferences.VIDEO_ORIENTATION_AUTO);
        videoPortrait = videoOrientationChoice(
                "竖屏锁定", ReBrowserPreferences.ORIENTATION_PORTRAIT);
        selector.addView(videoLandscape);
        selector.addView(videoAuto);
        selector.addView(videoPortrait);
        return selector;
    }

    private TextView videoOrientationChoice(String label, String value) {
        TextView choice = new TextView(this);
        choice.setText(label);
        choice.setTextSize(14);
        choice.setGravity(Gravity.CENTER);
        choice.setOnClickListener(view -> {
            preferences.setVideoOrientation(value);
            updateVideoOrientationSelector();
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(44), 1);
        params.setMargins(dp(2), 0, dp(2), 0);
        choice.setLayoutParams(params);
        return choice;
    }

    private final AdapterView.OnItemSelectedListener deviceListener = new AdapterView.OnItemSelectedListener() {
        @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            String value = position == 1 ? ReBrowserDevice.PHONE
                    : position == 2 ? ReBrowserDevice.TABLET : ReBrowserDevice.AUTO;
            if (!value.equals(preferences.deviceType())) preferences.setDeviceType(value);
            refreshDeviceStatus();
        }
        @Override public void onNothingSelected(AdapterView<?> parent) {}
    };

    private static int devicePosition(String value) {
        return ReBrowserDevice.PHONE.equals(value) ? 1 : ReBrowserDevice.TABLET.equals(value) ? 2 : 0;
    }

    private void refreshDeviceStatus() {
        if (deviceStatus == null) return;
        ReBrowserDevice.Status status = ReBrowserDevice.inspect(this);
        String size = Double.isFinite(status.diagonalInches)
                ? String.format(java.util.Locale.ROOT, "（约 %.1f 英寸）", status.diagonalInches)
                : "（屏幕尺寸或物理 DPI 不可用，默认手机）";
        deviceStatus.setText("自动识别：" + (ReBrowserDevice.TABLET.equals(status.automatic) ? "平板" : "手机")
                + size + "；当前按" + (status.tablet() ? "平板" : "手机") + "处理");
        updateVideoOrientationSelector();
    }

    private void updateVideoOrientationSelector() {
        if (videoOrientationSelector == null) return;
        boolean phone = !ReBrowserDevice.inspect(this).tablet();
        if (videoOverrideRow != null) {
            videoOverrideRow.setAlpha(phone ? 1f : 0.45f);
            videoOverrideRow.getChildAt(1).setEnabled(phone);
        }
        boolean enabled = phone && preferences.videoOrientationOverrideEnabled();
        String selected = preferences.videoOrientation();
        videoOrientationSelector.setAlpha(enabled ? 1f : 0.45f);
        styleVideoOrientationChoice(videoLandscape, enabled,
                ReBrowserPreferences.ORIENTATION_LANDSCAPE.equals(selected));
        styleVideoOrientationChoice(videoAuto, enabled,
                ReBrowserPreferences.VIDEO_ORIENTATION_AUTO.equals(selected));
        styleVideoOrientationChoice(videoPortrait, enabled,
                ReBrowserPreferences.ORIENTATION_PORTRAIT.equals(selected));
    }

    private void styleVideoOrientationChoice(TextView choice, boolean enabled, boolean selected) {
        if (choice == null) return;
        choice.setEnabled(enabled);
        choice.setTextColor(selected && enabled ? Color.WHITE : Color.rgb(55, 59, 68));
        choice.setBackground(roundedBackground(
                selected && enabled ? Color.rgb(91, 70, 180) : Color.TRANSPARENT,
                dp(11)));
        choice.setContentDescription(choice.getText()
                + (selected ? "，已选择" : "")
                + (enabled ? "" : "，当前设备未启用方向覆写"));
    }

    private GradientDrawable roundedBackground(int color, int radius) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(radius);
        return background;
    }

    private LinearLayout settingSwitch(
            String title,
            String subtitle,
            boolean checked,
            java.util.function.Consumer<Boolean> listener
    ) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(8), 0, dp(8));
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(16);
        titleView.setTextColor(Color.rgb(35, 40, 49));
        labels.addView(titleView);
        TextView subtitleView = new TextView(this);
        subtitleView.setText(subtitle);
        subtitleView.setTextSize(12);
        subtitleView.setTextColor(Color.rgb(101, 107, 117));
        labels.addView(subtitleView);
        row.addView(labels, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Switch toggle = new Switch(this);
        toggle.setContentDescription(title);
        toggle.setChecked(checked);
        toggle.setOnCheckedChangeListener((button, enabled) -> listener.accept(enabled));
        row.addView(toggle, new LinearLayout.LayoutParams(dp(58), dp(52)));
        row.setMinimumHeight(dp(72));
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    @Override
    protected void onPause() {
        saveSettings(false);
        super.onPause();
    }

    private static int searchPosition(String engine) {
        return ReBrowserPreferences.SEARCH_BAIDU.equals(engine) ? 1
                : ReBrowserPreferences.SEARCH_DUCKDUCKGO.equals(engine) ? 2
                : ReBrowserPreferences.SEARCH_GOOGLE.equals(engine) ? 3 : 0;
    }

    private void saveSettings(boolean notify) {
        if (homeUrl == null || searchEngine == null) return;
        String draft = homeUrl.getText().toString();
        if (!draft.equals(initialHome)) {
            preferences.setHomeUrl(draft);
            initialHome = draft;
        }
        int selected = searchEngine.getSelectedItemPosition();
        if (selected != initialSearch) {
            preferences.setSearchEngine(selected == 1 ? ReBrowserPreferences.SEARCH_BAIDU
                    : selected == 2 ? ReBrowserPreferences.SEARCH_DUCKDUCKGO
                    : selected == 3 ? ReBrowserPreferences.SEARCH_GOOGLE : ReBrowserPreferences.SEARCH_BING);
            initialSearch = selected;
        }
        if (notify) Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
