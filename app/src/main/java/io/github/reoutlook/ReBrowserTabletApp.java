package io.github.reoutlook;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

/** User-owned launcher choices for the independently tasked, Gecko-only tablet apps. */
enum ReBrowserTabletApp {
    CHATGPT("chatgpt", "ChatGPT", "常规网站 · 平板横屏", "ChatGpt"),
    RAINCLASS("rainclass", "雨课堂 Web", "桌面网站 · 平板横屏", "RainClass");

    final String title;
    final String summary;
    private final String key;
    private final String activity;
    private final String launcher;

    ReBrowserTabletApp(String key, String title, String summary, String activity) {
        this.key = key;
        this.title = title;
        this.summary = summary;
        this.activity = "io.github.reoutlook.ReBrowserTabletSiteActivity$" + activity;
        this.launcher = "io.github.reoutlook.ReBrowser" + activity + "Launcher";
    }

    boolean launcherPreferred(Context context) {
        return preferences(context).getBoolean(key, false);
    }

    void setLauncherPreferred(Context context, boolean visible) {
        if (!eligible(context)) return;
        preferences(context).edit().putBoolean(key, visible).apply();
        updateLauncher(context, visible);
    }

    void open(Context context) {
        if (!eligible(context) || !ReBrowserRuntime.inspect().browserAvailable()) return;
        context.startActivity(new Intent().setComponent(component(context))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private void stop(Context context) {
        ActivityManager manager = context.getSystemService(ActivityManager.class);
        if (manager == null) return;
        ComponentName component = component(context);
        ComponentName alias = new ComponentName(context.getPackageName(), launcher);
        for (ActivityManager.AppTask task : manager.getAppTasks()) {
            ComponentName base = task.getTaskInfo().baseActivity;
            if (component.equals(base) || alias.equals(base)) task.finishAndRemoveTask();
        }
    }

    static void deviceTypeChanged(Context context) {
        if (ReBrowserRuntime.usesSystemWebView()) return;
        boolean tablet = ReBrowserDevice.inspect(context).allowsTabletSites();
        for (ReBrowserTabletApp site : values()) {
            site.updateLauncher(context, tablet && site.launcherPreferred(context));
            if (!tablet) site.stop(context);
        }
    }

    private void updateLauncher(Context context, boolean visible) {
        PackageManager packages = context.getPackageManager();
        ComponentName alias = new ComponentName(context.getPackageName(), launcher);
        int current = packages.getComponentEnabledSetting(alias);
        if (visible != (current == PackageManager.COMPONENT_ENABLED_STATE_ENABLED)) {
            packages.setComponentEnabledSetting(alias, visible ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    : PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP);
        }
    }

    private ComponentName component(Context context) {
        return new ComponentName(context.getPackageName(), activity);
    }

    private static boolean eligible(Context context) {
        return !ReBrowserRuntime.usesSystemWebView() && ReBrowserDevice.inspect(context).allowsTabletSites();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("rebrowser_tablet_launchers_v1", Context.MODE_PRIVATE);
    }
}
