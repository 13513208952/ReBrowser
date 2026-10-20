package io.github.rebrowser.lyrics;
import static org.junit.Assert.*;
import org.junit.Test;
/** Reported booleans are display ONLY, never a system flow/owner authorization capability. */
public final class OverlayPermissionFlowTest {
 @Test public void unknownIsNotReportedAllowed(){OverlayPermissionFlow f=new OverlayPermissionFlow();assertFalse(f.observe(null).reportedAllowedForDisplay());assertEquals("系统浮层权限未知",f.state().label());}
 @Test public void returningRequiresRecheck(){OverlayPermissionFlow f=new OverlayPermissionFlow();assertTrue(f.observe(true).reportedAllowedForDisplay());assertFalse(f.returnedFromSettings().reportedAllowedForDisplay());assertTrue(f.observe(true).reportedAllowedForDisplay());}
 @Test public void noBooleanOwnerTicketCanCreateSystemFlow(){for(java.lang.reflect.Method m:OverlayPermissionFlow.class.getDeclaredMethods())assertFalse(m.getName().equals("mayOfferSystemFlow"));}
 @Test public void retirementDropsPositiveObservation(){OverlayPermissionFlow f=new OverlayPermissionFlow();f.observe(true);f.retire();assertFalse(f.state().reportedAllowedForDisplay());assertThrows(IllegalStateException.class,()->f.observe(true));}
}
