package io.github.reoutlook;

import static org.junit.Assert.*;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.view.View;

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
public final class ReBrowserDeviceTest {
    private ReBrowserPreferences preferences;

    @Before public void setup() {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("rebrowser_preferences_v1", Context.MODE_PRIVATE).edit().clear().commit();
        preferences = new ReBrowserPreferences(context);
    }

    @Test public void physicalSizeNotRawResolutionDeterminesAutomaticType() {
        assertFalse(ReBrowserDevice.classify("auto", 1440, 3200, 520, 520).tablet());
        ReBrowserDevice.Status tablet = ReBrowserDevice.classify("auto", 1800, 2880, 309, 309);
        assertTrue(tablet.tablet());
        assertEquals(11, tablet.diagonalInches, .05);
        assertEquals("physical-dpi", tablet.source);
        assertEquals(tablet.effective, ReBrowserDevice.classify("auto", 2880, 1800, 309, 309).effective);
    }

    @Test public void unavailableDpiAlwaysDefaultsToPhoneButManualTabletStillWorks() {
        for (float dpi : new float[]{0, -1, Float.NaN, Float.POSITIVE_INFINITY}) {
            ReBrowserDevice.Status automatic = ReBrowserDevice.classify("auto", 10000, 20000, dpi, 160);
            assertFalse(automatic.tablet());assertEquals("phone-fallback", automatic.source);
            assertTrue(ReBrowserDevice.classify("tablet", 10000, 20000, dpi, 160).tablet());
            assertFalse(ReBrowserDevice.classify("auto", 10000, 20000, 160, dpi).tablet());
        }
        assertFalse(ReBrowserDevice.classify("auto", 0, 0, 160, 160).tablet());
        assertTrue(ReBrowserDevice.classify("tablet", 0, 0, 0, 0).tablet());
    }

    @Test public void manualPhoneOverridesLargePhysicalScreenAndOnlyThreeValuesPersist() {
        assertFalse(ReBrowserDevice.classify("phone", 1800, 2880, 309, 309).tablet());
        assertEquals("auto", preferences.deviceType());
        preferences.setDeviceType("tablet");preferences.setDeviceType("sensor");
        assertEquals("tablet", new ReBrowserPreferences(RuntimeEnvironment.getApplication()).deviceType());
        preferences.setDeviceType(null);assertEquals("tablet", preferences.deviceType());
        preferences.setDeviceType("phone");assertEquals("phone", preferences.deviceType());
        preferences.setDeviceType("auto");assertEquals("auto", preferences.deviceType());
    }

    @Test public void wholeTabletWindowUsesFullUserNotUnrestrictedSensors() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_USER,
                ReBrowserFullscreenController.windowOrientation(true, true, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
        assertNotEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR,
                ReBrowserFullscreenController.windowOrientation(true, true, ActivityInfo.SCREEN_ORIENTATION_SENSOR));
        for (int phone : new int[]{ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED}) {
            assertEquals(phone, ReBrowserFullscreenController.windowOrientation(false, true, phone));
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
                    ReBrowserFullscreenController.windowOrientation(true, false, phone));
        }
    }

    public static final class WindowActivity extends Activity {
        boolean multi;
        boolean pip;
        @Override public boolean isInMultiWindowMode() { return multi; }
        @Override public boolean isInPictureInPictureMode() { return pip; }
    }

    @Test public void windowTransitionsAndManualOverrideReapplyWithoutErasingPhonePreferences() {
        preferences.setDeviceType("tablet");preferences.setGlobalOrientation("portrait");
        try (ActivityController<WindowActivity> c = Robolectric.buildActivity(WindowActivity.class).setup()) {
            WindowActivity a=c.get();
            ReBrowserFullscreenController controller=new ReBrowserFullscreenController(a,preferences);
            controller.applyGlobalOrientationPreference();
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_USER,a.getRequestedOrientation());
            a.multi=true;controller.onConfigurationChanged();
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,a.getRequestedOrientation());
            a.multi=false;a.pip=true;controller.onConfigurationChanged();
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,a.getRequestedOrientation());
            a.pip=false;controller.onWindowFocusChanged(true);
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_USER,a.getRequestedOrientation());
            assertEquals("portrait",preferences.globalOrientation());
            preferences.setDeviceType("phone");controller.applyGlobalOrientationPreference();
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,a.getRequestedOrientation());
        }
    }

    @Test public void tabletVideoRemainsFullUserAndPhoneVideoBehaviorIsPreserved() {
        try (ActivityController<WindowActivity> c = Robolectric.buildActivity(WindowActivity.class).setup()) {
            WindowActivity a=c.get();
            ReBrowserFullscreenController controller=new ReBrowserFullscreenController(a,preferences);
            preferences.setDeviceType("tablet");preferences.setVideoOrientationOverrideEnabled(true);
            preferences.setVideoOrientation("portrait");controller.show(new View(a),()->{});
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_USER,a.getRequestedOrientation());
            controller.hide();preferences.setDeviceType("phone");
            controller.show(new View(a),()->{});
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,a.getRequestedOrientation());
            controller.hide();
        }
    }

    @Test public void diagnosticSnapshotHandlesMissingDpiWithoutExportingInvalidNumbers() throws Exception {
        org.json.JSONObject value=ReBrowserDevice.classify("tablet",0,0,Float.NaN,Float.POSITIVE_INFINITY).snapshot();
        assertEquals("tablet",value.getString("effectiveType"));
        assertEquals("phone",value.getString("automaticType"));
        assertTrue(value.isNull("xdpi"));assertTrue(value.isNull("ydpi"));assertTrue(value.isNull("diagonalInches"));
        android.provider.Settings.System.putInt(RuntimeEnvironment.getApplication().getContentResolver(),
                android.provider.Settings.System.ACCELEROMETER_ROTATION,0);
        assertFalse(ReBrowserDevice.systemRotation(RuntimeEnvironment.getApplication()).getBoolean("autoRotateEnabled"));
    }

    @Test @Config(sdk=29) public void androidTenMetadataPathAndManualFallbackRemainNative() {
        preferences.setDeviceType("tablet");
        assertTrue(ReBrowserDevice.inspect(RuntimeEnvironment.getApplication()).tablet());
        preferences.setDeviceType("phone");
        assertFalse(ReBrowserDevice.inspect(RuntimeEnvironment.getApplication()).tablet());
    }
}
