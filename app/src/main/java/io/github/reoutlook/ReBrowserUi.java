package io.github.reoutlook;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

/** Stateless view and dimension helpers shared by ReBrowser UI components. */
final class ReBrowserUi {
    private ReBrowserUi() {}

    static final int SETTINGS_BACKGROUND = 0xffede8f0;
    static final int SETTINGS_ACCENT = 0xff6047ad;
    static final int SETTINGS_SURFACE = 0xfffffaff;

    static LinearLayout settingsGroup(LinearLayout parent) {
        Context context = parent.getContext();
        LinearLayout group = new LinearLayout(context);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setBackground(roundedBackground(SETTINGS_SURFACE, dp(context, 14)));
        group.setClipToOutline(true);
        parent.addView(group, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return group;
    }

    static void settingsDivider(LinearLayout group) {
        View line = new View(group.getContext());
        line.setBackgroundColor(0xffebe5ee);
        group.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
    }

    static void tintSettingsSwitch(Switch toggle) {
        toggle.setThumbTintList(new ColorStateList(
                new int[][] {{android.R.attr.state_checked}, {}},
                new int[] {0xffffffff, 0xff746a7e}));
        toggle.setTrackTintList(new ColorStateList(
                new int[][] {{android.R.attr.state_checked}, {}},
                new int[] {SETTINGS_ACCENT, 0xffb9adbf}));
    }

    static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable roundedBackground(int color, int radiusPixels) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(radiusPixels);
        return background;
    }

    static int appDrawerWidth(Context context) {
        return Math.min(dp(context, 320),
                Math.round(context.getResources().getDisplayMetrics().widthPixels * 0.84f));
    }

    static void styleAppDrawer(View drawer) {
        Context context = drawer.getContext();
        drawer.setElevation(dp(context, 18));
        drawer.setPadding(dp(context, 20), dp(context, 24), dp(context, 16), dp(context, 18));
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xfffafbfd);
        int corner = dp(context, 22);
        background.setCornerRadii(new float[]{0, 0, corner, corner, corner, corner, 0, 0});
        drawer.setBackground(background);
    }

    static void addAppDrawerHeading(LinearLayout drawer, CharSequence title) {
        Context context = drawer.getContext();
        TextView brand = new TextView(context);
        brand.setText(title);
        brand.setTextColor(0xff0f548c);
        brand.setTextSize(27);
        brand.setTypeface(null, android.graphics.Typeface.BOLD);
        drawer.addView(brand, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 46)));
    }

    static FrameLayout.LayoutParams matchMatch() {
        return new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    static FrameLayout.LayoutParams matchWrap() {
        return new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
