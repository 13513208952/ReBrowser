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

@RunWith(RobolectricTestRunner.class) @Config(sdk=31)
public final class ReBrowserApplicationsTest {
    private Context context;
    @Before public void setup() { context=RuntimeEnvironment.getApplication(); }
    private View tagged(String section, ReBrowserSettingsActivity activity) {
        return activity.findViewById(android.R.id.content).findViewWithTag(section);
    }
    private boolean hasLauncher(View view) {
        if(view instanceof Switch && "显示 ReOutlook 桌面入口".contentEquals(view.getContentDescription()))return true;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)
            if(hasLauncher(((ViewGroup)view).getChildAt(i)))return true;
        return false;
    }
    @Test public void mainNameAndFreshEntranceAreCorrect() throws Exception {
        assertEquals("ReBrowser",context.getString(R.string.app_name));
        assertFalse(new ReOutlookLauncherVisibility(context).visible());
        assertTrue(context.getPackageManager().getActivityInfo(new ComponentName(context,MainActivity.class),0).enabled);
    }
    @Test public void applicationsIsRightmostPeerAndOwnsTheSwitch() {
        assertArrayEquals(new String[]{"general","isolation","applications"},ReBrowserSettings.SECTIONS);
        try(ActivityController<ReBrowserSettingsActivity> c=Robolectric.buildActivity(ReBrowserSettingsActivity.class,
                new Intent().putExtra(ReBrowserSettings.EXTRA_SECTION,"applications")).setup()) {
            assertEquals(View.VISIBLE,tagged("applications",c.get()).getVisibility());
            assertTrue(hasLauncher(tagged("applications",c.get())));
            assertFalse(hasLauncher(tagged("general",c.get())));
            c.recreate(); assertEquals(View.VISIBLE,tagged("applications",c.get()).getVisibility());
        }
    }
    private String labels(View view) {
        StringBuilder result = new StringBuilder();
        if (view instanceof android.widget.TextView) result.append(((android.widget.TextView) view).getText()).append('\n');
        if (view.getContentDescription() != null) result.append(view.getContentDescription()).append('\n');
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
            result.append(labels(((ViewGroup) view).getChildAt(i)));
        return result.toString();
    }
    @Test public void musicManagementRetainsControlsButNotObsoleteExplanation() {
        try (ActivityController<ReBrowserSettingsActivity> c = Robolectric.buildActivity(ReBrowserSettingsActivity.class,
                new Intent().putExtra(ReBrowserSettings.EXTRA_SECTION, "applications")).setup()) {
            String text = labels(tagged("applications", c.get()));
            assertTrue(text.contains("启用音乐及其桌面入口")); assertTrue(text.contains("进入音乐"));
            assertTrue(text.contains("音乐源工作模式"));
            assertFalse(text.contains("音乐需先启用")); assertFalse(text.contains("尚未实机验收"));
            assertFalse(text.contains("后台／系统控制已接入首轮源码"));
        }
    }
    @Test public void android12ProviderWithoutProfilesCanStillUseMail() throws Exception {
        ReBrowserRuntime.Status status=new ReBrowserRuntime.Status(true,false,false,false,"huawei.provider","version");
        assertFalse(status.browserAvailable());assertTrue(status.provider);
        JSONObject info=status.snapshot();assertTrue(info.getBoolean("outlookAvailable"));
        assertFalse(info.getBoolean("outlookRequiresMultiProfile"));assertTrue(info.getBoolean("nativeApplicationManagementAvailable"));
    }
    @Test public void missingAnyIsolationFeatureNeverEnablesBrowserFallback() {
        for(int missing=0;missing<3;missing++) {
            assertFalse(new ReBrowserRuntime.Status(true,missing!=0,missing!=1,missing!=2,"provider","v").browserAvailable());
        }
        assertTrue(new ReBrowserRuntime.Status(true,true,true,true,"provider","v").browserAvailable());
    }
    @Test public void nativeSettingsAreLaunchedOutsideTheAdministratorTask() {
        for(String section:ReBrowserSettings.SECTIONS) {
            Intent intent=ReBrowserApplications.settingsIntent(context,section);
            assertEquals(ReBrowserSettingsActivity.class.getName(),intent.getComponent().getClassName());
            assertEquals(section,intent.getStringExtra(ReBrowserSettings.EXTRA_SECTION));
            assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP,intent.getFlags());
            assertEquals(0,intent.getFlags() & Intent.FLAG_ACTIVITY_CLEAR_TOP);
        }
        assertEquals("general",ReBrowserApplications.settingsIntent(context,"arbitrary").getStringExtra(ReBrowserSettings.EXTRA_SECTION));
    }
    @Test public void providerAbsentStillOpensNativeManagementNotBrowserOrMail() {
        ModeRouter.openReBrowser(context);
        Intent intent=shadowOf(RuntimeEnvironment.getApplication()).getNextStartedActivity();
        assertEquals(ReBrowserSettingsActivity.class.getName(),intent.getComponent().getClassName());
        assertEquals("applications",intent.getStringExtra(ReBrowserSettings.EXTRA_SECTION));
    }
    @Test public void directUnsupportedBrowserEntryHasSafeLifecycle() {
        try(ActivityController<ReBrowserActivity> c=Robolectric.buildActivity(ReBrowserActivity.class).setup()) {
            assertTrue(c.get().isFinishing());
            assertEquals(ReBrowserSettingsActivity.class.getName(),shadowOf(c.get()).getNextStartedActivity().getComponent().getClassName());
        }
    }
    @Test public void directMailAliasWithoutProviderHasSafeNativeFallback() {
        try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup()) {
            assertTrue(c.get().isFinishing());
            assertEquals(ReBrowserSettingsActivity.class.getName(),shadowOf(c.get()).getNextStartedActivity().getComponent().getClassName());
        }
    }
    @Test public void migrationPreservesLegacyDefaultAndExplicitOwnerChoices() {
        PackageManager pm=context.getPackageManager();
        ComponentName alias=new ComponentName(context.getPackageName(),context.getPackageName()+".ReOutlookLauncher");
        for(int state:new int[]{PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,PackageManager.COMPONENT_ENABLED_STATE_ENABLED,PackageManager.COMPONENT_ENABLED_STATE_DISABLED}) {
            context.getSharedPreferences("rebrowser_applications_v1",Context.MODE_PRIVATE).edit().clear().commit();
            pm.setComponentEnabledSetting(alias,state,PackageManager.DONT_KILL_APP);
            ReOutlookLauncherVisibility controller=new ReOutlookLauncherVisibility(context);
            controller.migrateInstallation(true);
            assertEquals(state!=PackageManager.COMPONENT_ENABLED_STATE_DISABLED,controller.visible());
            controller.setVisible(false);controller.migrateInstallation(true);assertFalse(controller.visible());
        }
    }
    @Test public void freshMigrationDoesNotEnableAnEntranceOnLaterUpgrade() {
        context.getSharedPreferences("rebrowser_applications_v1",Context.MODE_PRIVATE).edit().clear().commit();
        ReOutlookLauncherVisibility controller=new ReOutlookLauncherVisibility(context);
        controller.migrateInstallation(false);controller.migrateInstallation(true);assertFalse(controller.visible());
    }
    @Test public void nativeStatusWorksWithNoBrowserRuntimeAndDoesNotExportMailOrCookie() throws Exception {
        JSONObject info=ReBrowserAdminEnvironment.snapshot(context);
        assertEquals("native-environment",info.getString("scope"));
        assertFalse(info.getBoolean("workspaceRuntimeLoaded"));assertTrue(info.has("applications"));
        assertFalse(info.toString().contains("Cookie="));assertFalse(info.has("mail"));
    }
    @Test public void administratorModesAreFixedLevelOneAndCannotEnableEntrances() throws Exception {
        for(String operation:new String[]{"GET_APPLICATION_STATUS","GET_RUNTIME_STATUS","OPEN_APPLICATION_MANAGEMENT"}) {
            JSONObject r=new JSONObject().put("operation",operation);ReBrowserAdminProtocol.prepareExternalRequest(r);
            assertEquals(1,r.getInt("authorizationLevel"));
        }
        for(Object mode:new Object[]{"outlook","browser"}) {
            JSONObject r=new JSONObject().put("operation","OPEN_MODE").put("mode",mode);
            ReBrowserAdminProtocol.prepareExternalRequest(r);assertEquals(1,r.getInt("authorizationLevel"));
        }
        for(Object mode:new Object[]{"com.android.settings",true,JSONObject.NULL,"file:///data/local/tmp/app.apk"})
            assertThrows(SecurityException.class,()->ReBrowserAdminProtocol.prepareExternalRequest(new JSONObject().put("operation","OPEN_MODE").put("mode",mode)));
        assertThrows(SecurityException.class,()->ReBrowserAdminProtocol.prepareExternalRequest(new JSONObject().put("operation","SET_APPLICATION_ENABLED")));
    }
}
