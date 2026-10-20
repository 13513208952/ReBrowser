package io.github.rebrowser.system;

import static org.junit.Assert.*;
import io.github.rebrowser.engine.ViewportPolicy;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.Test;

/** Actual read-only own-window combination policy; no Activity/Window or system mutation. */
public final class DeviceWindowStateTest {
    private static DeviceWindowState.Snapshot phone() {
        return DeviceWindowState.observed(1080, 2400, 0, 120, 1920, 3, false, 0, false);
    }
    @Test public void geometryReusesViewportPolicyTaxonomy(){
        DeviceWindowState.Snapshot phone = phone();
        assertEquals(360, phone.geometry().widthDp());
        assertEquals(760, phone.geometry().heightDp());
        assertEquals(ViewportPolicy.WidthClass.COMPACT, phone.widthClass());
        assertTrue(phone.portraitShape());
        DeviceWindowState.Snapshot tablet = DeviceWindowState.observed(2560, 1600, 0, 0, 2560, 2, false, 90, false);
        assertEquals(ViewportPolicy.WidthClass.EXPANDED, tablet.widthClass());
        assertFalse(tablet.portraitShape());
    }
    @Test public void actualAndroidSurfaceConstantsAreNotDegrees(){
        assertEquals(DeviceWindowState.Rotation.ROTATION_0,DeviceWindowState.observedPlatformRotation(android.view.Surface.ROTATION_0));
        assertEquals(DeviceWindowState.Rotation.ROTATION_90,DeviceWindowState.observedPlatformRotation(android.view.Surface.ROTATION_90));
        assertEquals(DeviceWindowState.Rotation.ROTATION_180,DeviceWindowState.observedPlatformRotation(android.view.Surface.ROTATION_180));
        assertEquals(DeviceWindowState.Rotation.ROTATION_270,DeviceWindowState.observedPlatformRotation(android.view.Surface.ROTATION_270));
        assertEquals(DeviceWindowState.Rotation.UNKNOWN,DeviceWindowState.observedPlatformRotation(null));
        assertThrows(IllegalArgumentException.class,()->DeviceWindowState.observedPlatformRotation(90));
    }
    @Test public void fourKnownRotationsMapExactly(){
        assertEquals(DeviceWindowState.Rotation.ROTATION_0, DeviceWindowState.observedWindowFacts(0, null, null).rotation());
        assertEquals(DeviceWindowState.Rotation.ROTATION_90, DeviceWindowState.observedWindowFacts(90, null, null).rotation());
        assertEquals(DeviceWindowState.Rotation.ROTATION_180, DeviceWindowState.observedWindowFacts(180, null, null).rotation());
        assertEquals(DeviceWindowState.Rotation.ROTATION_270, DeviceWindowState.observedWindowFacts(270, null, null).rotation());
        assertEquals(DeviceWindowState.Rotation.ROTATION_270, phoneRotationOf(270));
    }
    private static DeviceWindowState.Rotation phoneRotationOf(int degrees) {
        return DeviceWindowState.observed(1080, 2400, 0, 0, 1080, 3, false, degrees, null).rotation();
    }
    @Test public void missingRotationIsUnknownNotZero(){
        DeviceWindowState.Facts facts = DeviceWindowState.observedWindowFacts(null, null, null);
        assertEquals(DeviceWindowState.Rotation.UNKNOWN, facts.rotation());
        assertFalse(facts.rotationKnown());
        assertNull(facts.multiWindow());
        assertNull(facts.pictureInPicture());
    }
    @Test public void invalidRotationDegreesAreRejectedRatherThanGuessed(){
        assertThrows(IllegalArgumentException.class, () -> DeviceWindowState.observedWindowFacts(45, null, null));
        assertThrows(IllegalArgumentException.class, () -> DeviceWindowState.observedWindowFacts(-90, null, null));
        assertThrows(IllegalArgumentException.class, () -> DeviceWindowState.observedWindowFacts(360, null, null));
        assertThrows(IllegalArgumentException.class, () -> DeviceWindowState.observed(1080, 2400, 0, 0, 1080, 3, false, 45, null));
    }
    @Test public void unknownMultiWindowAndPipStayUnknownInsteadOfFalse(){
        DeviceWindowState.Facts facts = DeviceWindowState.observedWindowFacts(90, null, null);
        assertNull(facts.multiWindow());
        assertNull(facts.pictureInPicture());
        assertFalse(facts.multiWindowKnown());
        assertFalse(facts.pictureInPictureKnown());
        assertNotEquals(Boolean.FALSE, facts.multiWindow());
        assertNotEquals(Boolean.FALSE, facts.pictureInPicture());
    }
    @Test public void pipAndMultiWindowAreIndependentReportedFacts(){
        DeviceWindowState.Facts pipOnly = DeviceWindowState.observedWindowFacts(0, Boolean.FALSE, Boolean.TRUE);
        assertEquals(Boolean.FALSE, pipOnly.multiWindow());
        assertEquals(Boolean.TRUE, pipOnly.pictureInPicture());
        DeviceWindowState.Facts splitOnly = DeviceWindowState.observedWindowFacts(0, Boolean.TRUE, Boolean.FALSE);
        assertEquals(Boolean.TRUE, splitOnly.multiWindow());
        assertEquals(Boolean.FALSE, splitOnly.pictureInPicture());
        DeviceWindowState.Snapshot snapshot = DeviceWindowState.observed(1000, 1600, 0, 0, 2560, 2, true, 0, Boolean.TRUE);
        assertEquals(Boolean.TRUE, snapshot.multiWindow());
        assertEquals(Boolean.TRUE, snapshot.pictureInPicture());
        assertTrue(snapshot.geometry().narrowWindowOnLargeDisplay());
    }
    @Test public void invalidGeometryFailsInsteadOfInventingWindowFacts(){
        assertThrows(IllegalArgumentException.class, () -> DeviceWindowState.observed(0, 100, 0, 0, 100, 1, false, 0, null));
        assertThrows(IllegalArgumentException.class, () -> DeviceWindowState.observed(100, 100, 100, 0, 100, 1, false, 0, null));
        assertThrows(IllegalArgumentException.class, () -> DeviceWindowState.observed(100, 100, 0, 0, 100, Float.NaN, false, 0, null));
        assertThrows(IllegalArgumentException.class, () -> DeviceWindowState.observed(100, 100, 0, 0, 50, 1, false, 0, null));
    }
    @Test public void snapshotIsImmutableAndRetainsNoActivityOrWindow(){
        DeviceWindowState.Snapshot snapshot = phone();
        assertEquals(DeviceWindowState.Rotation.ROTATION_0, snapshot.rotation());
        assertEquals(Boolean.FALSE, snapshot.multiWindow());
        assertEquals(Boolean.FALSE, snapshot.pictureInPicture());
        for (Field field : DeviceWindowState.Snapshot.class.getDeclaredFields()) {
            String type = field.getType().getName();
            assertFalse(type.startsWith("android."));
            assertFalse(type.equals("android.app.Activity"));
            assertFalse(type.equals("android.view.Window"));
        }
        for (Method method : DeviceWindowState.Snapshot.class.getDeclaredMethods()) {
            assertEquals(0, method.getParameterCount());
            assertFalse(method.getName().startsWith("set"));
        }
    }
    @Test public void stateExposesNoGlobalSettingOrDeviceIdentityGate(){
        for (Method method : DeviceWindowState.class.getDeclaredMethods()) {
            String name = method.getName().toLowerCase();
            for (String forbidden : new String[]{"set", "rotate", "orientation", "brightness", "volume", "density",
                    "serial", "androidid", "brand", "model", "lock", "role", "timeout"}) {
                assertFalse("unexpected global/device control " + method.getName(), name.contains(forbidden));
            }
        }
        for (Field field : DeviceWindowState.class.getDeclaredFields()) {
            assertFalse(field.getType().getName().startsWith("android."));
        }
    }
    @Test public void rotationNamesDoNotClaimPortraitShape(){
        DeviceWindowState.Snapshot landscapeDeviceInPortraitWindow = DeviceWindowState.observed(1080, 2400, 0, 0, 1080, 3, false, 90, false);
        assertEquals(DeviceWindowState.Rotation.ROTATION_90, landscapeDeviceInPortraitWindow.rotation());
        assertTrue(landscapeDeviceInPortraitWindow.portraitShape());
    }
}
