package io.github.reoutlook;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

/** Owner-only policy controls embedded in the isolation settings page; never creates site grants. */
@SuppressLint({"SetTextI18n", "ViewConstructor"})
final class ReBrowserSitePermissionsView extends LinearLayout {
    private final ReBrowserSitePermissions permissions;
    private LinearLayout grantList;

    ReBrowserSitePermissionsView(Context context, ReBrowserSitePermissions permissions) {
        super(context);
        this.permissions = permissions;
        setOrientation(VERTICAL);
        refresh();
    }

    void refresh() {
        removeAllViews();
        addView(text("启用表示网站可以提出申请，不代表自动授权。授权按总标签页 Profile 与 HTTPS 网站分别隔离。", 13, 0xff5d646f));
        addView(section("需要网站确认"));
        addView(permissionSwitch("剪贴板", "默认开启；每站允许 5 分钟或 15 天", ReBrowserSitePermissions.CLIPBOARD));
        addView(permissionSwitch("模糊定位", "默认开启；每站允许 5 分钟或 15 天", ReBrowserSitePermissions.APPROXIMATE_LOCATION));
        addView(permissionSwitch("麦克风", "默认关闭；每站仅本次或允许 3 小时", ReBrowserSitePermissions.MICROPHONE));
        addView(permissionSwitch("摄像头", "默认关闭；每站仅本次或允许 3 小时", ReBrowserSitePermissions.CAMERA));
        addView(permissionSwitch("高精度定位", "默认关闭；每站仅本次或允许 3 小时", ReBrowserSitePermissions.PRECISE_LOCATION));
        addView(section("永久禁止"));
        addView(fixedPolicy("网站通知", "永久禁止，不提供申请入口"));
        addView(fixedPolicy("后台推送", "永久禁止，不允许网页后台保活"));
        addView(fixedPolicy("网页运动传感器", "永久禁止陀螺仪、加速度计和方向传感器"));
        addView(section("应用后台策略"));
        LinearLayout background = row("允许后台运行", "关闭后进入后台将销毁网页运行时；系统 DownloadManager 下载仍可继续");
        Switch toggle = new Switch(getContext());
        toggle.setContentDescription("允许后台运行");
        toggle.setChecked(permissions.backgroundRuntimeEnabled());
        toggle.setOnCheckedChangeListener((button, enabled) -> permissions.setBackgroundRuntimeEnabled(enabled));
        background.addView(toggle, new LayoutParams(dp(58), dp(52))); addView(background);
        addView(section("有效的网站授权"));
        grantList = new LinearLayout(getContext()); grantList.setOrientation(VERTICAL); addView(grantList);
        refreshGrants();
    }

    private LinearLayout permissionSwitch(String title, String subtitle, String permission) {
        LinearLayout row = row(title, subtitle);
        Switch toggle = new Switch(getContext());
        toggle.setContentDescription(title);
        toggle.setChecked(permissions.capabilityEnabled(permission));
        toggle.setOnCheckedChangeListener((button, enabled) -> {
            permissions.setCapabilityEnabled(permission, enabled);
            refreshGrants();
            if (!enabled) Toast.makeText(getContext(), "已立即禁用并清除“" + title + "”的网站授权", Toast.LENGTH_SHORT).show();
        });
        row.addView(toggle, new LayoutParams(dp(58), dp(52)));
        return row;
    }

    private LinearLayout fixedPolicy(String title, String subtitle) {
        LinearLayout row = row(title, subtitle);
        TextView denied = text("已禁止", 13, 0xff963137); denied.setGravity(Gravity.CENTER);
        row.addView(denied, new LayoutParams(dp(64), dp(52))); return row;
    }

    private void refreshGrants() {
        grantList.removeAllViews();
        List<ReBrowserSitePermissions.Grant> grants = permissions.grants(System.currentTimeMillis());
        if (grants.isEmpty()) {
            grantList.addView(text("目前没有有效的跨请求授权。单次授权不会保存。", 13, 0xff656b75));
            return;
        }
        for (ReBrowserSitePermissions.Grant grant : grants) {
            String profile = grant.profileName.substring(Math.max(0, grant.profileName.length() - 8));
            String expiry = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(grant.expiresAt));
            LinearLayout row = row(permissionLabel(grant.permission) + " · " + grant.origin,
                    "Profile …" + profile + " · 到期 " + expiry);
            Button revoke = new Button(getContext()); revoke.setText("撤销");
            revoke.setOnClickListener(v -> { permissions.revoke(grant.profileName, grant.origin, grant.permission); refreshGrants(); });
            row.addView(revoke, new LayoutParams(dp(72), dp(48))); grantList.addView(row);
        }
        Button clear = new Button(getContext()); clear.setText("清除全部网站授权");
        clear.setOnClickListener(v -> { permissions.clearAll(); refreshGrants(); });
        grantList.addView(clear, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
    }

    private LinearLayout row(String title, String subtitle) {
        LinearLayout row = new LinearLayout(getContext()); row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(7), 0, dp(7));
        LinearLayout labels = new LinearLayout(getContext()); labels.setOrientation(VERTICAL);
        labels.addView(text(title, 16, 0xff232831)); labels.addView(text(subtitle, 12, 0xff656b75));
        row.addView(labels, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private TextView section(String value) {
        TextView title = text(value, 16, 0xff5b46b4); title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(0, dp(18), 0, dp(8)); return title;
    }
    private TextView text(String value, int size, int color) {
        TextView view = new TextView(getContext()); view.setText(value); view.setTextSize(size); view.setTextColor(color); return view;
    }
    private static String permissionLabel(String permission) {
        switch (permission) {
            case ReBrowserSitePermissions.CAMERA: return "摄像头";
            case ReBrowserSitePermissions.MICROPHONE: return "麦克风";
            case ReBrowserSitePermissions.PRECISE_LOCATION: return "高精度定位";
            case ReBrowserSitePermissions.APPROXIMATE_LOCATION: return "模糊定位";
            default: return "剪贴板";
        }
    }
    private int dp(int value) { return ReBrowserUi.dp(getContext(), value); }
}
