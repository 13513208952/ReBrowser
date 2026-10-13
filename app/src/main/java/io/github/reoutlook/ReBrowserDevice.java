package io.github.reoutlook;

import android.app.Activity;
import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import org.json.JSONException;
import org.json.JSONObject;

/** Native device classification; never inferred from UA or the current split-window width. */
final class ReBrowserDevice {
    static final String AUTO = "auto";
    static final String PHONE = "phone";
    static final String TABLET = "tablet";
    static final double TABLET_DIAGONAL_INCHES = 7.0;

    private ReBrowserDevice() {}

    static boolean validType(Object value) {
        return AUTO.equals(value) || PHONE.equals(value) || TABLET.equals(value);
    }

    @SuppressWarnings("deprecation")
    static Status inspect(Context context) {
        DisplayMetrics metrics = new DisplayMetrics();
        metrics.setTo(context.getResources().getDisplayMetrics());
        int width = 0;
        int height = 0;
        WindowManager manager = context.getSystemService(WindowManager.class);
        if (manager != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // Maximum bounds avoid reclassifying a tablet as a phone in split screen.
                    Rect bounds = manager.getMaximumWindowMetrics().getBounds();
                    width = bounds.width();
                    height = bounds.height();
                } else {
                    manager.getDefaultDisplay().getRealMetrics(metrics);
                    width = metrics.widthPixels;
                    height = metrics.heightPixels;
                }
            } catch (RuntimeException unavailable) {
                // Non-visual native diagnostic contexts may not expose WindowMetrics.
                // Device choice remains available even when screen metadata is unavailable.
            }
        }
        return classify(new ReBrowserPreferences(context).deviceType(), width, height,
                metrics.xdpi, metrics.ydpi);
    }

    static Status classify(String choice, int width, int height,
            float xdpi, float ydpi) {
        double diagonal = Double.NaN;
        if (width > 0 && height > 0 && xdpi > 0 && ydpi > 0
                && Float.isFinite(xdpi) && Float.isFinite(ydpi)) {
            diagonal = Math.hypot(width / (double) xdpi, height / (double) ydpi);
        }
        boolean physical = Double.isFinite(diagonal) && diagonal > 0;
        boolean large = physical && diagonal >= TABLET_DIAGONAL_INCHES;
        String automatic = large ? TABLET : PHONE;
        String preference = validType(choice) ? choice : AUTO;
        String effective = AUTO.equals(preference) ? automatic : preference;
        return new Status(preference, automatic, effective,
                !AUTO.equals(preference) ? "manual" : physical ? "physical-dpi" : "phone-fallback",
                diagonal, width, height, xdpi, ydpi);
    }

    static JSONObject systemRotation(Context context) throws JSONException {
        Object auto = JSONObject.NULL;
        Object rotation = JSONObject.NULL;
        try {
            auto = Settings.System.getInt(context.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION) != 0;
        } catch (Settings.SettingNotFoundException | SecurityException unavailable) { /* read-only, unknown */ }
        try {
            int value = Settings.System.getInt(context.getContentResolver(), Settings.System.USER_ROTATION);
            if (value >= 0 && value <= 3) rotation = value;
        } catch (Settings.SettingNotFoundException | SecurityException unavailable) { /* read-only, unknown */ }
        return new JSONObject().put("scope", "system-settings-read-only")
                .put("autoRotateEnabled", auto).put("userRotation", rotation);
    }

    static boolean fullWindow(Activity activity) {
        // "Fullscreen" here means a normal whole-app window, not immersive video mode.
        return !activity.isInMultiWindowMode() && !activity.isInPictureInPictureMode();
    }

    static final class Status {
        final String preference;
        final String automatic;
        final String effective;
        final String source;
        final double diagonalInches;
        final int widthPixels;
        final int heightPixels;
        final float xdpi;
        final float ydpi;

        Status(String preference, String automatic, String effective,
                String source, double diagonalInches, int widthPixels, int heightPixels, float xdpi, float ydpi) {
            this.preference = preference;
            this.automatic = automatic;
            this.effective = effective;
            this.source = source;
            this.diagonalInches = diagonalInches;
            this.widthPixels = widthPixels;
            this.heightPixels = heightPixels;
            this.xdpi = xdpi;
            this.ydpi = ydpi;
        }

        boolean tablet() { return TABLET.equals(effective); }

        JSONObject snapshot() throws JSONException {
            return new JSONObject().put("choice", preference).put("automaticType", automatic)
                    .put("effectiveType", effective).put("source", source)
                    .put("diagonalInches", Double.isFinite(diagonalInches)
                            ? Math.round(diagonalInches * 100) / 100.0 : JSONObject.NULL)
                    .put("tabletThresholdInches", TABLET_DIAGONAL_INCHES)
                    .put("screenWidthPixels", Math.max(0, widthPixels))
                    .put("screenHeightPixels", Math.max(0, heightPixels))
                    .put("xdpi", xdpi > 0 && Float.isFinite(xdpi) ? xdpi : JSONObject.NULL)
                    .put("ydpi", ydpi > 0 && Float.isFinite(ydpi) ? ydpi : JSONObject.NULL);
        }
    }
}
