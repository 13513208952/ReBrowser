package io.github.rebrowser.system;
import static org.junit.Assert.*;
import org.junit.Test;
/** Display metadata is deliberately not an actual APK verifier receipt. No Activity/installer run. */
public final class ApkHandoffActivityTest {
 private static final String SHA=new String(new char[64]).replace('\0','a');
 @Test public void displayDescriptorHasBoundsButCannotVerifyApk(){ApkHandoffActivity.DisplayDescriptor d=new ApkHandoffActivity.DisplayDescriptor("ReBrowser.apk","2.0.0",1024,SHA);assertEquals(1024,d.byteCount());assertThrows(IllegalArgumentException.class,()->new ApkHandoffActivity.DisplayDescriptor("a","1",0,SHA));assertThrows(IllegalArgumentException.class,()->new ApkHandoffActivity.DisplayDescriptor("a","1",1,"ABC"));assertThrows(IllegalArgumentException.class,()->new ApkHandoffActivity.DisplayDescriptor("bad\nname","1",1,SHA));}
 @Test public void labelsNeverClaimVerificationOrInstallationSuccess(){assertFalse(ApkHandoffActivity.stateLabel(ApkHandoffActivity.HandoffState.REPORTED_READY).contains("已验证"));assertTrue(ApkHandoffActivity.stateLabel(ApkHandoffActivity.HandoffState.HANDED_OFF_TO_SYSTEM).contains("结果未确认"));}
}
