package io.github.reoutlook;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class) @Config(sdk=31)
public final class ReBrowserMusicEntranceTest {
    private Context context;
    @Before public void setup() { context = RuntimeEnvironment.getApplication(); }
    private boolean containsWebView(View root) {
        if (root instanceof WebView) return true;
        if (root instanceof ViewGroup) for (int i=0;i<((ViewGroup)root).getChildCount();i++)
            if (containsWebView(((ViewGroup)root).getChildAt(i))) return true;
        return false;
    }
    @Test public void freshDisabledAndOwnerChoiceControlsRequiredIcon() {
        ReBrowserMusicEntrance entrance = new ReBrowserMusicEntrance(context);
        assertFalse(entrance.enabled());
        entrance.setEnabled(true); assertTrue(new ReBrowserMusicEntrance(context).enabled());
        entrance.setEnabled(false); assertFalse(entrance.enabled());
        assertFalse(new ReOutlookLauncherVisibility(context).visible());
    }
    @Test public void musicHasPrivateDistinctSingleTaskAndDisabledExportedAlias() throws Exception {
        PackageManager pm=context.getPackageManager();
        android.content.pm.ActivityInfo music=pm.getActivityInfo(new ComponentName(context,ReBrowserMusicActivity.class),0);
        assertFalse(music.exported); assertEquals("io.github.reoutlook.music",music.taskAffinity);
        assertEquals(android.content.pm.ActivityInfo.LAUNCH_SINGLE_TASK,music.launchMode);
        android.content.pm.ActivityInfo alias=pm.getActivityInfo(new ComponentName(context.getPackageName(),
                context.getPackageName()+".ReBrowserMusicLauncher"),PackageManager.GET_DISABLED_COMPONENTS);
        assertTrue(alias.exported);assertFalse(alias.enabled);
    }
    @Test public void disabledRouteDoesNotCreateRuntimeAndUsesNativeManagement() {
        ModeRouter.openMusic(context);
        Intent intent=shadowOf(RuntimeEnvironment.getApplication()).getNextStartedActivity();
        assertEquals(ReBrowserSettingsActivity.class.getName(),intent.getComponent().getClassName());
        assertEquals("applications",intent.getStringExtra(ReBrowserSettings.EXTRA_SECTION));
    }
    @Test public void enabledButUnsupportedProviderNeverBorrowsDefaultProfile() {
        new ReBrowserMusicEntrance(context).setEnabled(true);
        try(var activity=Robolectric.buildActivity(ReBrowserMusicActivity.class).setup()) {
            assertFalse(containsWebView(activity.get().findViewById(android.R.id.content)));
        }
    }
    @Test public void settingsKeepTheirNativeOnlyBoundaryAndMusicStatusIsRedacted() throws Exception {
        try(var activity=Robolectric.buildActivity(ReBrowserSettingsActivity.class).setup()) {
            assertFalse(containsWebView(activity.get().findViewById(android.R.id.content)));
        }
        org.json.JSONObject snapshot=ReBrowserApplications.snapshot(context);
        org.json.JSONObject music=snapshot.getJSONArray("applications").getJSONObject(1);
        assertEquals("music",music.getString("id"));assertFalse(music.getBoolean("enabled"));
        assertTrue(music.getBoolean("coexistence"));assertFalse(music.getBoolean("backgroundPlaybackVerified"));
        assertFalse(snapshot.toString().contains(ReBrowserMusicPolicy.PROFILE));
        assertFalse(ReBrowserApplications.validMode("music")); // No new administrator authority in this increment.
    }
}
