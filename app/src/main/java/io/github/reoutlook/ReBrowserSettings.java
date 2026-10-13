package io.github.reoutlook;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Fixed settings sections and a bounded, non-secret administrator snapshot. */
final class ReBrowserSettings {
    static final String GENERAL = "general";
    static final String ISOLATION = "isolation";
    static final String APPLICATIONS = "applications";
    static final String EXTRA_SECTION = "settingsSection";
    static final String[] SECTIONS = {GENERAL, ISOLATION, APPLICATIONS};
    private ReBrowserSettings() {}

    static boolean validSection(Object value) {
        return GENERAL.equals(value) || ISOLATION.equals(value) || APPLICATIONS.equals(value);
    }

    static String section(Object value) { return validSection(value) ? (String) value : GENERAL; }

    static JSONObject snapshot(Context context) throws JSONException {
        ReBrowserPreferences p = new ReBrowserPreferences(context);
        boolean photoAvailable = ReBrowserBackgroundAsset.resourceId() != 0;
        JSONArray backgrounds = new JSONArray().put(ReBrowserPreferences.BACKGROUND_WHITE);
        if (photoAvailable) backgrounds.put(ReBrowserPreferences.BACKGROUND_EXCLUSIVE);
        return new JSONObject()
                .put("sections", new JSONArray(SECTIONS))
                .put("scope", "browser-global-preferences; website-state-in-named-profiles")
                .put("general", new JSONObject()
                        .put("homeCustomized", !ReBrowserStore.HOME_URL.equals(p.homeUrl()))
                        .put("searchEngine", p.searchEngine())
                        .put("defaultBrowser", ReBrowserDefaultBrowser.snapshot(context))
                        .put("desktopMode", p.desktopModeEnabled())
                        .put("device", ReBrowserDevice.inspect(context).snapshot())
                        .put("globalOrientation", p.globalOrientation())
                        .put("videoOrientationOverride", p.videoOrientationOverrideEnabled())
                        .put("videoOrientation", p.videoOrientation())
                        .put("launcherVisibility", new ReOutlookLauncherVisibility(context).snapshot()))
                // Protocol v2 compatibility key: retained bounded values, not a settings page.
                .put("personalization", new JSONObject()
                        .put("newTabBackground", p.newTabBackground())
                        .put("effectiveNewTabBackground", p.exclusiveBackgroundEnabled()
                                ? ReBrowserPreferences.BACKGROUND_EXCLUSIVE : ReBrowserPreferences.BACKGROUND_WHITE)
                        .put("photoAvailable", photoAvailable)
                        .put("availableBackgrounds", backgrounds)
                        .put("newTabRecent", p.newTabRecentEnabled()))
                .put("applications", ReBrowserApplications.snapshot(context))
                .put("isolation", new JSONObject()
                        .put("javascript", p.javascriptEnabled())
                        .put("thirdPartyCookies", p.thirdPartyCookiesEnabled())
                        .put("forcePrimaryPromotion", p.forcePrimaryPromotionEnabled())
                        .put("fullIncognitoImplemented", false)
                        .put("websitePermissions", websitePolicy(new ReBrowserSitePermissions(context))));
    }

    static JSONObject websitePolicy(ReBrowserSitePermissions permissions) throws JSONException {
        return new JSONObject()
                .put("clipboard", permissions.capabilityEnabled(ReBrowserSitePermissions.CLIPBOARD))
                .put("approximateLocation", permissions.capabilityEnabled(ReBrowserSitePermissions.APPROXIMATE_LOCATION))
                .put("preciseLocation", permissions.capabilityEnabled(ReBrowserSitePermissions.PRECISE_LOCATION))
                .put("camera", permissions.capabilityEnabled(ReBrowserSitePermissions.CAMERA))
                .put("microphone", permissions.capabilityEnabled(ReBrowserSitePermissions.MICROPHONE))
                .put("notifications", false).put("backgroundPush", false).put("webSensors", false)
                .put("backgroundRuntime", permissions.backgroundRuntimeEnabled())
                .put("administratorCanGrant", false).put("scope", "profileName+topLevelOrigin");
    }
}
