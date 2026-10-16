package io.github.reoutlook;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
import android.app.AlertDialog;
import android.content.Intent;
import android.provider.Settings;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowSettings;

/** Prompt entry on a native unsupported-provider fixture; never creates a Profile/WebView. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=31)
public final class ReBrowserMusicLyricPermissionTest {
    private static AlertDialog prompt(ReBrowserMusicActivity activity) throws Exception {
        var method=ReBrowserMusicActivity.class.getDeclaredMethod("explainLyricsPermission");method.setAccessible(true);method.invoke(activity);
        var field=ReBrowserMusicActivity.class.getDeclaredField("lyricsPermissionDialog");field.setAccessible(true);return (AlertDialog)field.get(activity);
    }
    @Test public void promptAndCancelNeverOpenSettingsOrGrantPermission() throws Exception {
        var app=RuntimeEnvironment.getApplication();new ReBrowserMusicEntrance(app).setEnabled(true);ShadowSettings.setCanDrawOverlays(false);
        try(var c=Robolectric.buildActivity(ReBrowserMusicActivity.class).setup()) {
            var dialog=prompt(c.get());assertTrue(dialog.isShowing());assertNull(shadowOf(app).getNextStartedActivity());
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();shadowOf(android.os.Looper.getMainLooper()).idle();
            assertNull(shadowOf(app).getNextStartedActivity());assertFalse(Settings.canDrawOverlays(app));
        }
    }
    @Test public void onlyExplicitPositiveClickOpensFixedPackageSettingsAndDoesNotGrantIt() throws Exception {
        var app=RuntimeEnvironment.getApplication();new ReBrowserMusicEntrance(app).setEnabled(true);ShadowSettings.setCanDrawOverlays(false);
        try(var c=Robolectric.buildActivity(ReBrowserMusicActivity.class).setup()) {
            var dialog=prompt(c.get());assertNull(shadowOf(app).getNextStartedActivity());
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();shadowOf(android.os.Looper.getMainLooper()).idle();Intent intent=shadowOf(app).getNextStartedActivity();
            assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,intent.getAction());assertEquals("package:io.github.reoutlook",intent.getDataString());
            assertFalse(Settings.canDrawOverlays(app));
        }
    }
    @Test public void permissionButtonsRejectObscuredTouchesAndActivityDestroyDismissesPrompt() throws Exception {
        var app=RuntimeEnvironment.getApplication();new ReBrowserMusicEntrance(app).setEnabled(true);
        AlertDialog dialog;
        try(var c=Robolectric.buildActivity(ReBrowserMusicActivity.class).setup()) {
            dialog=prompt(c.get());assertTrue(dialog.getButton(AlertDialog.BUTTON_POSITIVE).getFilterTouchesWhenObscured());
            assertTrue(dialog.getButton(AlertDialog.BUTTON_NEGATIVE).getFilterTouchesWhenObscured());
        }
        assertFalse(dialog.isShowing());
    }
}
