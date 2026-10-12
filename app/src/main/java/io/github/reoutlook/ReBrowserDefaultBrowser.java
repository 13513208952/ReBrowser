package io.github.reoutlook;

import android.app.Activity;
import android.app.role.RoleManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.Settings;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

/** Manual navigation to system-owned default-app selection, never a role assignment. */
final class ReBrowserDefaultBrowser {
    private ReBrowserDefaultBrowser() {}

    static Intent settingsIntent() {
        return new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS);
    }

    static Intent systemIntent(Context context, Intent intent) {
        PackageManager pm = context.getPackageManager();
        int flags = PackageManager.MATCH_DEFAULT_ONLY | PackageManager.MATCH_SYSTEM_ONLY;
        ResolveInfo preferred = pm.resolveActivity(intent, flags);
        java.util.List<ResolveInfo> candidates = pm.queryIntentActivities(intent, flags);
        // Do not explicitly launch the synthetic ResolverActivity or a third-party chooser.
        if (systemHandler(preferred)) {
            for (ResolveInfo candidate : candidates) {
                if (systemHandler(candidate)
                        && preferred.activityInfo.packageName.equals(candidate.activityInfo.packageName)
                        && preferred.activityInfo.name.equals(candidate.activityInfo.name))
                    return explicit(intent, candidate);
            }
        }
        for (ResolveInfo candidate : candidates) {
            if (systemHandler(candidate)) return explicit(intent, candidate);
        }
        throw new ActivityNotFoundException("No system settings handler");
    }

    private static boolean systemHandler(ResolveInfo info) {
        return info != null && info.activityInfo != null && info.activityInfo.exported
                && info.activityInfo.enabled && info.activityInfo.applicationInfo != null
                && (info.activityInfo.applicationInfo.flags
                    & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
    }

    private static Intent explicit(Intent intent, ResolveInfo info) {
        return new Intent(intent).setComponent(new ComponentName(
                info.activityInfo.packageName, info.activityInfo.name));
    }

    static void openSettings(Activity activity) {
        try { activity.startActivity(systemIntent(activity, settingsIntent())); return; }
        catch (ActivityNotFoundException error) {
            // Preserve OEM compatibility without hard-coding the primary Settings package.
        }
        try {
            Intent oem = settingsIntent().setPackage("com.android.settings");
            activity.startActivity(systemIntent(activity, oem));
        } catch (ActivityNotFoundException error) {
            try { activity.startActivity(systemIntent(activity, new Intent(Settings.ACTION_SETTINGS))); }
            catch (ActivityNotFoundException unavailable) {
                Toast.makeText(activity, "系统未提供默认应用设置入口", Toast.LENGTH_SHORT).show();
            }
        }
    }

    static JSONObject snapshot(Context context) throws JSONException {
        RoleManager roles = context.getSystemService(RoleManager.class);
        return new JSONObject().put("registered", true)
                .put("roleAvailable", roles != null && roles.isRoleAvailable(RoleManager.ROLE_BROWSER))
                .put("roleHeld", roles != null && roles.isRoleHeld(RoleManager.ROLE_BROWSER))
                .put("administratorCanSet", false);
    }
}
