package io.github.rebrowser.system;

import io.github.rebrowser.settings.SettingsRepository.DeviceType;
import io.github.rebrowser.system.DeviceWindowState.DeviceKind;
import org.junit.Test;
import static org.junit.Assert.*;

/** Pure native-reported physical-DPI arithmetic and presentation correction, not hardware
 * attestation, an Android display, administrator identity, device enrollment or UA authority. */
public final class DevicePresentationClassificationTest {
    @Test public void sevenInchBoundaryUsesNativePixelAndPhysicalDpiNotDpWidth(){
        assertEquals(DeviceKind.TABLET,DeviceWindowState.classifyDevice(DeviceType.AUTO,4200,5600,1000,1000).automatic());
        assertEquals(DeviceKind.PHONE,DeviceWindowState.classifyDevice(DeviceType.AUTO,4200,5600,1000.1f,1000.1f).automatic());
    }
    @Test public void samePixelsDifferentPhysicalDpiCanBePhoneOrTablet(){
        assertEquals(DeviceKind.PHONE,DeviceWindowState.classifyDevice(null,1080,2400,420,420).effective());
        assertEquals(DeviceKind.TABLET,DeviceWindowState.classifyDevice(null,1080,2400,200,200).effective());
    }
    @Test public void manualCorrectionDoesNotRewritePhysicalObservation(){
        DeviceWindowState.DeviceClassification forced=DeviceWindowState.classifyDevice(DeviceType.TABLET,1080,2400,420,420);
        assertEquals(DeviceKind.PHONE,forced.automatic());assertEquals(DeviceKind.TABLET,forced.effective());
        assertEquals("manual",forced.source());assertTrue(forced.label().contains("手动纠正"));
        assertEquals(DeviceKind.PHONE,DeviceWindowState.classifyDevice(DeviceType.PHONE,1600,2560,200,200).effective());
    }
    @Test public void unavailableFactsStayUnknownEvenWithLabelledPhoneFallback(){
        DeviceWindowState.DeviceClassification unknown=DeviceWindowState.classifyDevice(null,0,0,Float.NaN,Float.NaN);
        assertEquals(DeviceKind.UNKNOWN,unknown.automatic());assertFalse(unknown.physicalKnown());assertTrue(Double.isNaN(unknown.diagonalInches()));
        assertEquals(DeviceKind.PHONE,unknown.effective());assertTrue(unknown.phoneFallback());assertEquals("phone-fallback",unknown.source());
        assertTrue(unknown.label().contains("未读取"));assertTrue(unknown.label().contains("回退"));
    }
    @Test public void badDpiNeverBecomesFalsePhysicalTabletEvidence(){
        for(float bad:new float[]{0,-1,Float.NaN,Float.POSITIVE_INFINITY}){
            DeviceWindowState.DeviceClassification result=DeviceWindowState.classifyDevice(DeviceType.AUTO,1080,2400,bad,420);
            assertEquals(DeviceKind.UNKNOWN,result.automatic());assertFalse(result.physicalKnown());
        }
        assertEquals(DeviceKind.UNKNOWN,DeviceWindowState.classifyDevice(null,65537,2400,420,420).automatic());
    }
    @Test public void manualChoiceRemainsAvailableWithoutClaimingKnownPhysicalFacts(){
        DeviceWindowState.DeviceClassification manual=DeviceWindowState.classifyDevice(DeviceType.TABLET,0,0,0,0);
        assertEquals(DeviceKind.UNKNOWN,manual.automatic());assertFalse(manual.physicalKnown());assertTrue(manual.tablet());assertFalse(manual.phoneFallback());assertEquals("manual",manual.source());
    }
    @Test public void swappingBothNativeAxesKeepsDiagonalAndInputsRemainImmutable(){
        DeviceWindowState.DeviceClassification first=DeviceWindowState.classifyDevice(DeviceType.AUTO,1600,2400,300,350),second=DeviceWindowState.classifyDevice(DeviceType.AUTO,2400,1600,350,300);
        assertEquals(first.diagonalInches(),second.diagonalInches(),0.000001);assertEquals(first.automatic(),second.automatic());assertEquals(DeviceType.AUTO,first.choice());
    }
}
