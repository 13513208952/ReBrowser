package io.github.reoutlook;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;

/** Orientation and Gecko document-fullscreen owner. */
final class ReBrowserFullscreenController {
    private final Activity activity;
    private final ReBrowserPreferences preferences;
    private View styledRoot;
    private View fullscreenContent;
    private View[] browserChrome = new View[0];
    private int[] chromeVisibility = new int[0];
    private int[] chromeAccessibility = new int[0];
    private int contentTopMargin;
    private boolean fullscreenLayoutApplied;
    private boolean fullscreen;
    private int systemUiBeforeFullscreen;

    ReBrowserFullscreenController(Activity activity, ReBrowserPreferences preferences) {
        this.activity = activity;
        this.preferences = preferences;
    }

    void attachBrowserLayout(View root, View content, View... chrome) {
        styledRoot = root;
        fullscreenContent = content;
        browserChrome = chrome;
        chromeVisibility = new int[chrome.length];
        chromeAccessibility = new int[chrome.length];
    }
    boolean isFullscreen() { return fullscreen; }
    boolean isLandscape() {
        return activity.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
    }
    boolean isTablet() { return ReBrowserDevice.inspect(activity).tablet(); }

    String toggleGlobalOrientationLock() {
        boolean landscape = isLandscape();
        String mode = landscape ? ReBrowserPreferences.ORIENTATION_PORTRAIT
                : ReBrowserPreferences.ORIENTATION_LANDSCAPE;
        preferences.setGlobalOrientation(mode);
        applyGlobalOrientationPreference();
        return mode;
    }

    void clearGlobalOrientationLock() {
        preferences.setGlobalOrientation(ReBrowserPreferences.ORIENTATION_UNLOCKED);
        applyGlobalOrientationPreference();
    }

    void applyGlobalOrientationPreference() {
        String mode = preferences.globalOrientation();
        int phone = ReBrowserPreferences.ORIENTATION_LANDSCAPE.equals(mode)
                ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                : ReBrowserPreferences.ORIENTATION_UNLOCKED.equals(mode)
                ? ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        if (fullscreen) phone = effectiveVideoOrientation();
        int requested = !ReBrowserDevice.fullWindow(activity)
                ? ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                : isTablet() ? ActivityInfo.SCREEN_ORIENTATION_FULL_USER : phone;
        if (activity.getRequestedOrientation() != requested) {
            activity.setRequestedOrientation(requested);
        }
    }

    org.json.JSONObject windowSnapshot() throws org.json.JSONException {
        return new org.json.JSONObject().put("scope", "browser-activity-window")
                .put("multiWindow", activity.isInMultiWindowMode())
                .put("pictureInPicture", activity.isInPictureInPictureMode())
                .put("fullWindow", ReBrowserDevice.fullWindow(activity))
                .put("requestedOrientation", activity.getRequestedOrientation())
                .put("configurationOrientation",
                        activity.getResources().getConfiguration().orientation)
                .put("fullscreenVideo", fullscreen)
                .put("tabletFourDirections", isTablet() && ReBrowserDevice.fullWindow(activity));
    }

    void setDocumentFullscreen(boolean enabled) {
        if (fullscreen == enabled) return;
        if (enabled) {
            systemUiBeforeFullscreen = activity.getWindow().getDecorView()
                    .getSystemUiVisibility();
        }
        fullscreen = enabled;
        setFullscreenLayout(enabled);
        setFullscreenSystemUi(enabled);
        applyGlobalOrientationPreference();
    }

    boolean hideIfVisible() {
        if (!fullscreen) return false;
        hide();
        return true;
    }

    void hide() { setDocumentFullscreen(false); }

    void onConfigurationChanged() {
        applyGlobalOrientationPreference();
        if (styledRoot != null) styledRoot.requestApplyInsets();
    }

    void onWindowFocusChanged(boolean hasFocus) {
        if (!hasFocus) return;
        applyGlobalOrientationPreference();
        if (fullscreen) setFullscreenSystemUi(true);
    }

    private int effectiveVideoOrientation() {
        String mode = preferences.videoOrientationOverrideEnabled()
                ? preferences.videoOrientation()
                : ReBrowserPreferences.ORIENTATION_UNLOCKED.equals(preferences.globalOrientation())
                ? ReBrowserPreferences.VIDEO_ORIENTATION_AUTO
                : ReBrowserPreferences.ORIENTATION_LANDSCAPE;
        if (ReBrowserPreferences.VIDEO_ORIENTATION_AUTO.equals(mode)) {
            return ActivityInfo.SCREEN_ORIENTATION_SENSOR;
        }
        return ReBrowserPreferences.ORIENTATION_PORTRAIT.equals(mode)
                ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                : ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
    }

    private void setFullscreenLayout(boolean enabled) {
        if (enabled) {
            if (fullscreenLayoutApplied || fullscreenContent == null) return;
            ViewGroup.LayoutParams rawParams = fullscreenContent.getLayoutParams();
            if (rawParams instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) rawParams;
                contentTopMargin = params.topMargin;
                params.topMargin = 0;
                fullscreenContent.setLayoutParams(params);
            }
            for (int index = 0; index < browserChrome.length; index++) {
                View chrome = browserChrome[index];
                chromeVisibility[index] = chrome.getVisibility();
                chromeAccessibility[index] = chrome.getImportantForAccessibility();
                chrome.setVisibility(View.GONE);
                chrome.setImportantForAccessibility(
                        View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            }
            fullscreenLayoutApplied = true;
            fullscreenContent.requestLayout();
            return;
        }
        if (!fullscreenLayoutApplied) return;
        ViewGroup.LayoutParams rawParams = fullscreenContent.getLayoutParams();
        if (rawParams instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) rawParams;
            params.topMargin = contentTopMargin;
            fullscreenContent.setLayoutParams(params);
        }
        for (int index = 0; index < browserChrome.length; index++) {
            View chrome = browserChrome[index];
            chrome.setVisibility(chromeVisibility[index]);
            chrome.setImportantForAccessibility(chromeAccessibility[index]);
        }
        fullscreenLayoutApplied = false;
        fullscreenContent.requestLayout();
    }

    private void setFullscreenSystemUi(boolean enabled) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = activity.getWindow().getInsetsController();
            if (controller != null) {
                if (enabled) {
                    controller.setSystemBarsBehavior(
                            WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                    controller.hide(WindowInsets.Type.systemBars());
                } else controller.show(WindowInsets.Type.systemBars());
            }
        } else if (enabled) {
            activity.getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        } else {
            activity.getWindow().getDecorView().setSystemUiVisibility(systemUiBeforeFullscreen);
        }
        if (!enabled && styledRoot != null) WindowStyling.apply(activity, styledRoot);
    }
}
