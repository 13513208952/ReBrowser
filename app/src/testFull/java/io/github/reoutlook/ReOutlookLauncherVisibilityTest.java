package io.github.reoutlook;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class ReOutlookLauncherVisibilityTest {
    private Context context;
    private PackageManager packages;
    private ReOutlookLauncherVisibility visibility;

    @Before
    public void setup() {
        context = RuntimeEnvironment.getApplication();
        packages = context.getPackageManager();
        visibility = new ReOutlookLauncherVisibility(context);
    }

    private ComponentName launcher() {
        return new ComponentName(context.getPackageName(),
                context.getPackageName() + ".ReOutlookLauncher");
    }

    @Test
    public void launcherDefaultsHiddenAndTargetsMail() throws Exception {
        assertFalse(visibility.visible());
        assertEquals(MainActivity.class.getName(), packages.getActivityInfo(launcher(), PackageManager.GET_DISABLED_COMPONENTS).targetActivity);
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                packages.getComponentEnabledSetting(launcher()));
    }

    @Test
    public void hidingOnlyDisablesLauncherAlias() throws Exception {
        visibility.setVisible(true);
        visibility.setVisible(false);
        assertFalse(visibility.visible());
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                packages.getComponentEnabledSetting(launcher()));
        JSONObject status = visibility.snapshot();
        assertFalse(status.getBoolean("outlookLauncherVisible"));
        assertTrue(status.getBoolean("outlookActivityEnabled"));
        assertTrue(status.getBoolean("rebrowserLauncherEnabled"));
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                packages.getComponentEnabledSetting(new ComponentName(context, MainActivity.class)));
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                packages.getComponentEnabledSetting(new ComponentName(context, ReBrowserLauncherActivity.class)));
    }

    @Test
    public void hiddenChoiceIsNotResetByNewControllerAndCanBeReversed() {
        visibility.setVisible(false);
        ReOutlookLauncherVisibility recreated = new ReOutlookLauncherVisibility(context);
        assertFalse(recreated.visible());
        recreated.setVisible(true);
        assertTrue(visibility.visible());
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                packages.getComponentEnabledSetting(launcher()));
        recreated.setVisible(true);
        assertTrue(recreated.visible());
    }

    @Test
    public void mailModeRoutingDoesNotUseTheDisabledAlias() {
        visibility.setVisible(false);
        ModeRouter.openOutlook(context);
        Intent started = shadowOf(RuntimeEnvironment.getApplication()).getNextStartedActivity();
        // This JVM has no provider: routing stays native rather than crashing a WebView.
        assertEquals(new ComponentName(context, ReBrowserSettingsActivity.class), started.getComponent());
        assertNotNull(packages.resolveActivity(started, 0));
    }

    @Test
    public void settingsToggleReflectsPersistedStateAfterRecreation() {
        try (ActivityController<ReBrowserSettingsActivity> settings =
                Robolectric.buildActivity(ReBrowserSettingsActivity.class).setup()) {
            Switch toggle = launcherSwitch(settings.get().findViewById(android.R.id.content));
            assertNotNull(toggle);
            assertFalse(toggle.isChecked());
            toggle.setChecked(true);
            toggle.setChecked(false);
            assertFalse(visibility.visible());
            settings.recreate();
            toggle = launcherSwitch(settings.get().findViewById(android.R.id.content));
            assertNotNull(toggle);
            assertFalse(toggle.isChecked());
            toggle.setChecked(true);
            assertTrue(visibility.visible());
        }
    }

    @Test
    public void administratorSnapshotIsFixedAndReadOnly() throws Exception {
        visibility.setVisible(false);
        JSONObject status = visibility.snapshot();
        assertEquals(4, status.length());
        assertFalse(status.getBoolean("administratorCanSet"));
        assertFalse(status.has("url"));
        assertFalse(visibility.visible());
    }

    private static Switch launcherSwitch(View view) {
        if (view instanceof Switch && "显示 ReOutlook 桌面入口".contentEquals(
                view.getContentDescription() == null ? "" : view.getContentDescription())) {
            return (Switch) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                Switch result = launcherSwitch(group.getChildAt(i));
                if (result != null) return result;
            }
        }
        return null;
    }
}
