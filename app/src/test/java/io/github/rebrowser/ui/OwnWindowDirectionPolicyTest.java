package io.github.rebrowser.ui;

import android.content.pm.ActivityInfo;
import io.github.rebrowser.settings.SettingsRepository.WindowOrientation;
import org.junit.Test;
import static org.junit.Assert.*;

/** Own-window requested-mode arithmetic only; no Android sensor, rotation, window or OS write. */
public final class OwnWindowDirectionPolicyTest {
    @Test public void fullPhoneLegacyUsesInheritedPortraitWithoutStoredPreference(){
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,WindowPresentation.requestedDirectionFor(false,true,null));
    }
    @Test public void fullPhoneUsesFixedPortraitAndBothSensorLandscapeDirections(){
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,WindowPresentation.requestedDirectionFor(false,true,WindowOrientation.PORTRAIT));
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,WindowPresentation.requestedDirectionFor(false,true,WindowOrientation.LANDSCAPE));
    }
    @Test public void explicitUnlockIsUnspecifiedNotForcedSensorOrSystemSetting(){
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,WindowPresentation.requestedDirectionFor(false,true,WindowOrientation.FOLLOW_SYSTEM));
    }
    @Test public void fullTabletAlwaysUsesFullUserWhichDoesNotOverrideOsRotationLock(){
        for(WindowOrientation mode:WindowOrientation.values())assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_USER,WindowPresentation.requestedDirectionFor(true,true,mode));
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_USER,WindowPresentation.requestedDirectionFor(true,true,null));
    }
    @Test public void smallOrPipWindowNeverKeepsPhoneOrTabletLock(){
        for(boolean tablet:new boolean[]{false,true}){
            for(WindowOrientation mode:WindowOrientation.values())assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,WindowPresentation.requestedDirectionFor(tablet,false,mode));
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,WindowPresentation.requestedDirectionFor(tablet,false,null));
        }
    }
}
