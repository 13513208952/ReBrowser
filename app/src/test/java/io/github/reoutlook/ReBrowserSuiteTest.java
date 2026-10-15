package io.github.reoutlook;

import static org.junit.Assert.*;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public final class ReBrowserSuiteTest {
    @Test public void identityAndDisjointVersionRangesAreFixed() {
        assertEquals("io.github.reoutlook",BuildConfig.APPLICATION_ID);
        assertEquals(BuildConfig.FULL_SUITE,BuildConfig.VERSION_CODE >= 1_000_000);
        assertTrue(BuildConfig.VERSION_CODE > 19);
        if(BuildConfig.FULL_SUITE)assertTrue(BuildConfig.FLAVOR.toLowerCase(java.util.Locale.ROOT).contains("background"));
    }
    @Test public void applicationRegistryMatchesBuildNotRuntimeAvailability() throws Exception {
        Context c=RuntimeEnvironment.getApplication();
        org.json.JSONObject s=ReBrowserApplications.snapshot(c);
        assertEquals(BuildConfig.FULL_SUITE?2:0,s.getJSONArray("applications").length());
        assertEquals(BuildConfig.FULL_SUITE,ReBrowserApplications.validMode("outlook"));
        assertFalse(ReBrowserApplications.validMode("music"));
        if(!BuildConfig.FULL_SUITE)assertEquals("general",ReBrowserApplications.settingsIntent(c,"applications")
                .getStringExtra(ReBrowserSettings.EXTRA_SECTION));
    }
    @Test public void minimalManifestHasNoChildComponents() throws Exception {
        Context c=RuntimeEnvironment.getApplication();PackageManager pm=c.getPackageManager();
        for(String name:new String[]{"MainActivity","ArchiveActivity","MaintenanceActivity","ReBrowserMusicActivity","ReBrowserMusicLauncher"}) {
            ComponentName component=new ComponentName(c.getPackageName(),c.getPackageName()+"."+name);
            if(BuildConfig.FULL_SUITE)assertNotNull(pm.getActivityInfo(component,PackageManager.GET_DISABLED_COMPONENTS));
            else assertThrows(PackageManager.NameNotFoundException.class,()->pm.getActivityInfo(component,PackageManager.GET_DISABLED_COMPONENTS));
        }
        ComponentName service=new ComponentName(c.getPackageName(),c.getPackageName()+".ReBrowserMusicService");
        if(BuildConfig.FULL_SUITE)assertFalse(pm.getServiceInfo(service,0).exported);
        else assertThrows(PackageManager.NameNotFoundException.class,()->pm.getServiceInfo(service,0));
    }
    @Test public void sourcesAreFixedAndIndependentOfEnableSwitch() {
        Context c=RuntimeEnvironment.getApplication();ReBrowserMusicSources s=new ReBrowserMusicSources(c);
        assertEquals("netease",s.selected());assertFalse(s.select("https://arbitrary.invalid"));
        assertEquals(BuildConfig.FULL_SUITE,s.select("netease"));
    }
}
