package io.github.reoutlook;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
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
    private Switch musicEnabledSwitch;
    private Button musicOpenButton;
    private TextView runtimeMessage;
    private EditText homeUrl;
    private Spinner searchEngine;
    private Spinner deviceType;
    private TextView deviceStatus;
    private LinearLayout tabletSiteRows;
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
        root.setBackgroundColor(ReBrowserUi.SETTINGS_BACKGROUND);
        WindowStyling.apply(this, root);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(0, insets.getSystemWindowInsetTop(),
                    0, insets.getSystemWindowInsetBottom());
            return insets;
        });

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(16), dp(14), dp(16), dp(8));
        root.addView(header, matchWrap());

        TextView title = new TextView(this);
        title.setText("ReBrowser 设置");
        title.setTextSize(22);
        title.setTextColor(0xff242129);
        title.setTypeface(android.graphics.Typeface.create("sans-serif-medium", 0));
        header.addView(title, matchWrap());

        LinearLayout navigation = new LinearLayout(this);
        navigation.setPadding(dp(2), dp(2), dp(2), dp(2));
        navigation.setBackground(roundedBackground(0xffe3dce9, dp(14)));
        LinearLayout.LayoutParams navigationParams = matchWrap();
        navigationParams.topMargin = dp(8);
        String[] sectionLabels = {"常规", "隔离与权限", "应用管理"};
        for (int i = 0; i < pages.length; i++) {
            final String section = ReBrowserSettings.SECTIONS[i];
            TextView button = new TextView(this);
            button.setText(sectionLabels[i]); button.setTextSize(14); button.setGravity(Gravity.CENTER);
            button.setContentDescription(sectionLabels[i] + "设置");
            button.setEnabled(BuildConfig.FULL_SUITE || !ReBrowserSettings.APPLICATIONS.equals(section));
            button.setOnClickListener(v -> showSection(section));
            navigation.addView(button, new LinearLayout.LayoutParams(0, dp(44), 1));
            pageButtons[i] = button;
        }
        header.addView(navigation, navigationParams);
        FrameLayout pageHost = new FrameLayout(this);
        root.addView(pageHost, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        for (int i = 0; i < pages.length; i++) {
            ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
            scroll.setTag(ReBrowserSettings.SECTIONS[i]);
            LinearLayout panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL);
            panel.setPadding(dp(16), 0, dp(16), dp(20));
            scroll.addView(panel, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            pageHost.addView(scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            pages[i] = scroll; pageContents[i] = panel;
        }
        LinearLayout content = pageContents[0];
        LinearLayout isolation = pageContents[1];
        LinearLayout applications = pageContents[2];
        runtimeMessage = fieldLabel("");
        applications.addView(runtimeMessage);
        if (BuildConfig.FULL_SUITE) {
        applications.addView(sectionTitle("子软件"));
        LinearLayout appGroup = ReBrowserUi.settingsGroup(applications);
        LinearLayout applicationRow = new LinearLayout(this);
        applicationRow.setGravity(Gravity.CENTER_VERTICAL);
        applicationRow.setPadding(dp(12), dp(8), dp(10), dp(8));
        LinearLayout name = new LinearLayout(this);
        name.setOrientation(LinearLayout.VERTICAL);
        name.addView(applicationName("ReOutlook"));
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
        applicationRow.addView(applicationSettingsButton(ReBrowserApplications.OUTLOOK));
        styleButton(outlookOpenButton, 0xffeae0f6, 0xff463174, 18);
        ReBrowserUi.tintSettingsSwitch(outlookLauncherSwitch);
        appGroup.addView(applicationRow, matchWrap());
        ReBrowserUi.settingsDivider(appGroup);
        LinearLayout musicRow = new LinearLayout(this);
        musicRow.setGravity(Gravity.CENTER_VERTICAL);
        musicRow.setPadding(dp(12), dp(8), dp(10), dp(8));
        LinearLayout musicName = new LinearLayout(this); musicName.setOrientation(LinearLayout.VERTICAL);
        musicName.addView(applicationName("音乐"));
        musicName.addView(fieldLabel("网易云 · 共存型 · 独立任务与登录环境"));
        musicRow.addView(musicName, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        musicEnabledSwitch = new Switch(this);
        musicEnabledSwitch.setContentDescription("启用音乐及其桌面入口");
        musicEnabledSwitch.setChecked(new ReBrowserMusicEntrance(this).enabled());
        musicEnabledSwitch.setOnCheckedChangeListener(this::onMusicEnabledChanged);
        musicRow.addView(musicEnabledSwitch);
        musicOpenButton = new Button(this); musicOpenButton.setText("进入");
        musicOpenButton.setContentDescription("进入音乐");
        musicOpenButton.setOnClickListener(v -> ModeRouter.openMusic(this)); musicRow.addView(musicOpenButton);
        musicRow.addView(applicationSettingsButton(ReBrowserApplications.MUSIC));
        styleButton(musicOpenButton, 0xffeae0f6, 0xff463174, 18);
        ReBrowserUi.tintSettingsSwitch(musicEnabledSwitch);
        appGroup.addView(musicRow, matchWrap());
        ReBrowserUi.settingsDivider(appGroup);
        LinearLayout videoRow = new LinearLayout(this);
        videoRow.setGravity(Gravity.CENTER_VERTICAL);
        videoRow.setPadding(dp(12), dp(8), dp(10), dp(8));
        LinearLayout videoName = new LinearLayout(this);
        videoName.setOrientation(LinearLayout.VERTICAL);
        videoName.addView(applicationName("ReVideo"));
        videoName.addView(fieldLabel("BiliClient · 快捷入口"));
        videoRow.addView(videoName, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button videoOpenButton = new Button(this);
        videoOpenButton.setText("进入");
        videoOpenButton.setContentDescription("进入 ReVideo");
        videoOpenButton.setOnClickListener(v ->
                startActivity(new Intent(this, ReVideoShortcutActivity.class)));
        styleButton(videoOpenButton, 0xffeae0f6, 0xff463174, 18);
        videoRow.addView(videoOpenButton);
        appGroup.addView(videoRow, matchWrap());
        if (!ReBrowserRuntime.usesSystemWebView()) {
            tabletSiteRows = new LinearLayout(this);
            tabletSiteRows.setOrientation(LinearLayout.VERTICAL);
            for (ReBrowserTabletApp site : ReBrowserTabletApp.values()) addTabletSiteRow(site);
            appGroup.addView(tabletSiteRows, matchWrap());
        }
        }

        content.addView(sectionTitle("启动与搜索"));
        LinearLayout searchGroup = ReBrowserUi.settingsGroup(content);
        homeUrl = new EditText(this);
        homeUrl.setSingleLine(true);
        initialHome = preferences.homeUrl();
        homeUrl.setText(savedInstanceState == null ? initialHome : savedInstanceState.getString("homeDraft", initialHome));
        homeUrl.setHint("https://cn.bing.com/");
        homeUrl.setTextSize(14);
        homeUrl.setBackgroundColor(Color.TRANSPARENT);
        addField(searchGroup, "主页网址", homeUrl);
        ReBrowserUi.settingsDivider(searchGroup);
        searchEngine = new Spinner(this);
        String[] labels = {"Bing", "百度", "DuckDuckGo", "Google"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, labels);
        searchEngine.setAdapter(adapter);
        String currentSearch = preferences.searchEngine();
        initialSearch = searchPosition(currentSearch);
        searchEngine.setSelection(savedInstanceState == null ? initialSearch : savedInstanceState.getInt("searchDraft", initialSearch));
        addField(searchGroup, "默认搜索引擎", searchEngine);

        isolation.addView(sectionTitle("网页行为"));
        LinearLayout webGroup = ReBrowserUi.settingsGroup(isolation);
        webGroup.addView(settingSwitch(
                "JavaScript",
                "关闭后部分网站将无法正常工作",
                preferences.javascriptEnabled(),
                preferences::setJavascriptEnabled));
        ReBrowserUi.settingsDivider(webGroup);
        webGroup.addView(settingSwitch(
                "允许第三方 Cookie",
                "按总标签页 Profile 分别保存；不会与 ReOutlook 共用",
                preferences.thirdPartyCookiesEnabled(),
                preferences::setThirdPartyCookiesEnabled));
        content.addView(sectionTitle("网页显示"));
        ReBrowserUi.settingsGroup(content).addView(settingSwitch(
                "桌面版网站",
                "新建或重新载入的页面使用桌面布局",
                preferences.desktopModeEnabled(),
                preferences::setDesktopModeEnabled));
        isolation.addView(sectionTitle("总标签页生命周期"));
        ReBrowserUi.settingsGroup(isolation).addView(settingSwitch(
                "允许强行提升为主标签页",
                "开启后，临时总标签页可跳过副级别直接提升为主；其他生命周期规则不变",
                preferences.forcePrimaryPromotionEnabled(),
                preferences::setForcePrimaryPromotionEnabled));
        isolation.addView(sectionTitle("网站权限管理"));
        sitePermissionsView = new ReBrowserSitePermissionsView(this, new ReBrowserSitePermissions(this));
        isolation.addView(sitePermissionsView, matchWrap());

        content.addView(sectionTitle("设备与方向"));
        LinearLayout deviceGroup = ReBrowserUi.settingsGroup(content);
        deviceType = new Spinner(this);
        deviceType.setContentDescription("设备类型：自动、手机或平板");
        deviceType.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"自动识别", "手机", "平板"}));
        deviceType.setSelection(devicePosition(preferences.deviceType()));
        deviceType.setOnItemSelectedListener(deviceListener);
        addField(deviceGroup, "设备类型", deviceType);
        ReBrowserUi.settingsDivider(deviceGroup);
        deviceStatus = groupDescription("");
        deviceGroup.addView(deviceStatus);
        ReBrowserUi.settingsDivider(deviceGroup);
        deviceGroup.addView(groupDescription("自动按屏幕物理 DPI 计算对角线，7 英寸及以上识别为平板；可手动纠正。"));
        refreshDeviceStatus();

        content.addView(sectionTitle("视频播放器"));
        LinearLayout videoGroup = ReBrowserUi.settingsGroup(content);
        videoOverrideRow = settingSwitch(
                "覆写视频播放器界面方向",
                "仅手机生效；平板完整窗口始终遵守系统四方向策略",
                preferences.videoOrientationOverrideEnabled(),
                enabled -> {
                    preferences.setVideoOrientationOverrideEnabled(enabled);
                    updateVideoOrientationSelector();
                });
        videoGroup.addView(videoOverrideRow);
        videoOrientationSelector = createVideoOrientationSelector();
        LinearLayout.LayoutParams videoParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        videoParams.setMargins(dp(10), 0, dp(10), dp(8));
        videoGroup.addView(videoOrientationSelector, videoParams);
        updateVideoOrientationSelector();

        content.addView(sectionTitle("系统默认浏览器"));
        Button defaultBrowser = new Button(this);
        defaultBrowser.setText("选择默认浏览器");
        defaultBrowser.setContentDescription("选择默认浏览器，打开 Android 系统默认应用设置");
        defaultBrowser.setOnClickListener(v -> ReBrowserDefaultBrowser.openSettings(this));
        defaultBrowser.setAllCaps(false);
        defaultBrowser.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        styleButton(defaultBrowser, ReBrowserUi.SETTINGS_SURFACE, ReBrowserUi.SETTINGS_ACCENT, 14);
        content.addView(defaultBrowser, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));

        Button save = new Button(this);
        save.setText("保存并返回");
        save.setOnClickListener(view -> {
            saveSettings(true);
            finish();
        });
        styleButton(save, ReBrowserUi.SETTINGS_ACCENT, Color.WHITE, 24);
        LinearLayout footer = new LinearLayout(this);
        footer.setGravity(Gravity.END);
        footer.setPadding(dp(16), dp(8), dp(16), dp(12));
        ReBrowserUi.settingsDivider(root);
        footer.addView(save, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)));
        root.addView(footer, matchWrap());
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
                ? "ReBrowser 浏览功能可用，当前内核："
                        + ReBrowserRuntime.engineDisplayName() + "。"
                : runtime.provider ? "当前" + ReBrowserRuntime.engineDisplayName()
                        + "不满足 ReBrowser 隔离浏览要求；此页面不启动浏览器工作区。"
                : "当前没有可用的" + ReBrowserRuntime.engineDisplayName()
                        + "；原生设置仍可使用，恢复内核后再进入网页模式。");
        if (outlookOpenButton != null) outlookOpenButton.setEnabled(runtime.provider);
        refreshMusicEntrance();
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
        if (!BuildConfig.FULL_SUITE && ReBrowserSettings.APPLICATIONS.equals(selectedSection)) selectedSection = ReBrowserSettings.GENERAL;
        for (int i = 0; i < pages.length; i++) {
            boolean selected = selectedSection.equals(ReBrowserSettings.SECTIONS[i]);
            pages[i].setVisibility(selected ? View.VISIBLE : View.GONE);
            pageButtons[i].setSelected(selected);
            pageButtons[i].setTextColor(selected ? 0xff382365 : 0xff373b44);
            pageButtons[i].setBackground(roundedBackground(selected ? 0xffd8c9f6 : Color.TRANSPARENT, dp(11)));
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

    private void onMusicEnabledChanged(CompoundButton button, boolean enabled) {
        try { new ReBrowserMusicEntrance(this).setEnabled(enabled); }
        catch (RuntimeException error) {
            Toast.makeText(this, "音乐入口更新失败，请重试", Toast.LENGTH_SHORT).show();
        }
        refreshMusicEntrance();
    }

    private void refreshMusicEntrance() {
        if (!BuildConfig.FULL_SUITE) return;
        boolean enabled = new ReBrowserMusicEntrance(this).enabled();
        if (musicEnabledSwitch != null) {
            musicEnabledSwitch.setOnCheckedChangeListener(null);
            musicEnabledSwitch.setChecked(enabled);
            musicEnabledSwitch.setOnCheckedChangeListener(this::onMusicEnabledChanged);
        }
        if (musicOpenButton != null) musicOpenButton.setEnabled(enabled && ReBrowserRuntime.inspect().browserAvailable());
    }

    private void addTabletSiteRow(ReBrowserTabletApp site) {
        ReBrowserUi.settingsDivider(tabletSiteRows);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(8), dp(10), dp(8));
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(applicationName(site.title));
        labels.addView(fieldLabel(site.summary));
        row.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Switch launcher = new Switch(this);
        launcher.setContentDescription("显示 " + site.title + " 桌面图标");
        launcher.setChecked(site.launcherPreferred(this));
        launcher.setOnCheckedChangeListener((button, checked) -> site.setLauncherPreferred(this, checked));
        ReBrowserUi.tintSettingsSwitch(launcher);
        row.addView(launcher);
        Button open = new Button(this);
        open.setText("进入");
        open.setContentDescription("进入 " + site.title);
        open.setEnabled(ReBrowserRuntime.inspect().browserAvailable());
        open.setOnClickListener(view -> site.open(this));
        styleButton(open, 0xffeae0f6, 0xff463174, 18);
        row.addView(open);
        tabletSiteRows.addView(row, matchWrap());
    }

    private android.widget.ImageButton applicationSettingsButton(String id) {
        android.widget.ImageButton button = new android.widget.ImageButton(this);
        button.setImageResource(R.drawable.ic_rb_settings);
        button.setImageTintList(android.content.res.ColorStateList.valueOf(Color.rgb(91, 70, 180)));
        button.setBackground(roundedBackground(Color.WHITE, dp(24)));
        button.setPadding(dp(12), dp(12), dp(12), dp(12));
        button.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
        button.setContentDescription(ReBrowserApplications.name(id) + " 独立设置");
        button.setFilterTouchesWhenObscured(true);
        button.setOnClickListener(v -> startActivity(ReBrowserApplications.applicationSettingsIntent(this, id)));
        return button;
    }

    private TextView sectionTitle(String text) {
        TextView title = new TextView(this);
        title.setText(text);
        title.setTextSize(13);
        title.setTextColor(ReBrowserUi.SETTINGS_ACCENT);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(dp(4), dp(12), dp(4), dp(6));
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

    private void addField(LinearLayout group, String label, View control) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), 0, dp(10), 0);
        TextView name = fieldLabel(label);
        name.setTextSize(14);
        name.setTextColor(0xff242129);
        name.setPadding(0, 0, dp(8), 0);
        if (control instanceof EditText) ((EditText) control).setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        if (control.getId() == View.NO_ID) control.setId(View.generateViewId());
        name.setLabelFor(control.getId());
        row.addView(name, new LinearLayout.LayoutParams(dp(108), ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(control, new LinearLayout.LayoutParams(0, dp(50), 1));
        group.addView(row, matchWrap());
    }

    private TextView applicationName(String text) {
        TextView label = fieldLabel(text);
        label.setTextSize(14);
        label.setTextColor(0xff242129);
        label.setPadding(0, 0, 0, 0);
        label.setTypeface(android.graphics.Typeface.create("sans-serif-medium", 0));
        return label;
    }

    private TextView groupDescription(String text) {
        TextView label = fieldLabel(text);
        label.setPadding(dp(12), dp(8), dp(12), dp(8));
        return label;
    }

    private void styleButton(Button button, int fill, int text, int radius) {
        button.setTextColor(new android.content.res.ColorStateList(
                new int[][] {{-android.R.attr.state_enabled}, {}},
                new int[] {0xff857b91, text}));
        button.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x246047ad),
                roundedBackground(fill, dp(radius)), null));
        button.setPadding(dp(14), 0, dp(14), 0);
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
            if (!value.equals(preferences.deviceType())) {
                preferences.setDeviceType(value);
                ReBrowserTabletApp.deviceTypeChanged(ReBrowserSettingsActivity.this);
            }
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
        if (tabletSiteRows != null) {
            tabletSiteRows.setVisibility(status.allowsTabletSites() ? View.VISIBLE : View.GONE);
        }
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
        row.setPadding(dp(12), dp(6), dp(10), dp(6));
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(14);
        titleView.setTypeface(android.graphics.Typeface.create("sans-serif-medium", 0));
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
        ReBrowserUi.tintSettingsSwitch(toggle);
        row.addView(toggle, new LinearLayout.LayoutParams(dp(48), dp(48)));
        row.setMinimumHeight(dp(54));
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
