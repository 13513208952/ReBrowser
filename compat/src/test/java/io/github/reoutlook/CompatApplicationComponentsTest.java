package io.github.reoutlook;

import static org.junit.Assert.*;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** The installation ID intentionally differs from the manifest's Java namespace. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class)
public final class CompatApplicationComponentsTest {
    @Test public void outlookAliasUsesManifestNamespace() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        assertEquals("io.github.rebrowser.compat", context.getPackageName());
        ReOutlookLauncherVisibility visibility = new ReOutlookLauncherVisibility(context);
        assertFalse(visibility.visible());
        visibility.setVisible(true);
        assertTrue(visibility.visible());
        assertTrue(visibility.snapshot().getBoolean("outlookActivityEnabled"));
        ComponentName alias = new ComponentName(context.getPackageName(),
                "io.github.reoutlook.ReOutlookLauncher");
        assertEquals(MainActivity.class.getName(), context.getPackageManager()
                .getActivityInfo(alias, PackageManager.GET_DISABLED_COMPONENTS).targetActivity);
    }

    @Test public void musicEnableControlsDeclaredAlias() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        ReBrowserMusicEntrance entrance = new ReBrowserMusicEntrance(context);
        assertFalse(entrance.enabled());
        entrance.setEnabled(true);
        assertTrue(new ReBrowserMusicEntrance(context).enabled());
        ComponentName alias = new ComponentName(context.getPackageName(),
                "io.github.reoutlook.ReBrowserMusicLauncher");
        assertEquals(ReBrowserMusicActivity.class.getName(), context.getPackageManager()
                .getActivityInfo(alias, PackageManager.GET_DISABLED_COMPONENTS).targetActivity);
    }

    @Test public void subapplicationSettingsIntentResolvesInsideCompat() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        Intent intent = ReBrowserApplications.applicationSettingsIntent(context,
                ReBrowserApplications.MUSIC);
        assertEquals(new ComponentName(context, ReBrowserSubapplicationSettingsActivity.class),
                intent.getComponent());
        assertNotNull(context.getPackageManager().getActivityInfo(intent.getComponent(), 0));
    }
}
