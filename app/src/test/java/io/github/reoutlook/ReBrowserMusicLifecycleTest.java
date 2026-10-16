package io.github.reoutlook;

import static org.junit.Assert.*;
import org.junit.Test;

public final class ReBrowserMusicLifecycleTest {
    @Test public void originalLayoutNeedsAllThreeConditions() {
        for (boolean tablet : new boolean[]{false,true}) for (boolean full : new boolean[]{false,true})
            for (boolean landscape : new boolean[]{false,true})
                assertEquals(tablet && full && landscape, ReBrowserMusicPolicy.originalLayout(tablet,full,landscape));
    }
    @Test public void ordinaryMobileUaKeepsActualEngineAndForcesMobileOnTablet() {
        String phone = ReBrowserMusicPolicy.mobileUserAgent("Mozilla/5.0 (Linux; Android 15; Device; wv) AppleWebKit/537.36 Version/4.0 Chrome/156.0.0.0 Mobile Safari/537.36");
        assertFalse(phone.contains("wv")); assertFalse(phone.contains("Version/4.0")); assertTrue(phone.contains("Chrome/156.0.0.0"));
        String tablet = ReBrowserMusicPolicy.mobileUserAgent("Mozilla/5.0 (Linux; Android 15; Tablet) AppleWebKit/537.36 Chrome/156.0.0.0 Safari/537.36");
        assertTrue(tablet.contains(" Mobile Safari/")); assertTrue(tablet.contains("Tablet"));
    }
    @Test public void coldRuntimeUsesPhoneUaAndDoesNotAssumeLogin() {
        ReBrowserMusicLifecycle s = new ReBrowserMusicLifecycle();
        assertEquals(ReBrowserMusicLifecycle.Stage.CHECKING,s.stage()); assertFalse(s.desktop()); assertFalse(s.player());
    }
    @Test public void eligibleTabletStillDefaultsToPhoneLogin() {
        ReBrowserMusicLifecycle s=new ReBrowserMusicLifecycle();s.window(true,true,true);s.account(false);
        assertTrue(s.desktopLoginAllowed()); assertFalse(s.desktop());
        assertTrue(s.loginUa(true)); assertTrue(s.desktop());
    }
    @Test public void leavingExceptionForcesPhoneLoginButNotPlayerUa() {
        ReBrowserMusicLifecycle s=new ReBrowserMusicLifecycle();s.window(true,true,true);s.account(false);s.loginUa(true);
        assertTrue(s.window(true,false,true));assertFalse(s.desktop());assertFalse(s.loginUa(true));
        s.account(true);assertTrue(s.desktop());assertFalse(s.window(false,true,false));assertTrue(s.desktop());
    }
    @Test public void onlyConfirmedAccountEntersPlayerAndExpiryReturnsToPhone() {
        ReBrowserMusicLifecycle s=new ReBrowserMusicLifecycle();assertTrue(s.account(true));assertTrue(s.player());
        assertFalse(s.account(true));assertTrue(s.account(false));assertFalse(s.player());assertFalse(s.desktop());
    }
    @Test public void guestPlayerIsNeverPresentedAsLoginEvenWithAPositiveDomFlag() {
        ReBrowserMusicLifecycle s=new ReBrowserMusicLifecycle();
        assertEquals(ReBrowserMusicPolicy.ACCOUNT,s.destination());
        assertFalse(s.presentLogin(ReBrowserMusicPolicy.MOBILE_LOGIN,true));
        s.account(false);assertEquals(ReBrowserMusicPolicy.MOBILE_LOGIN,s.destination());
        assertFalse(s.presentLogin(ReBrowserMusicPolicy.HOME,true));
        assertFalse(s.presentLogin(ReBrowserMusicPolicy.MOBILE_LOGIN,false));
        assertTrue(s.presentLogin(ReBrowserMusicPolicy.MOBILE_LOGIN,true));
        s.account(true);assertEquals(ReBrowserMusicPolicy.HOME,s.destination());
        assertFalse(s.presentLogin(ReBrowserMusicPolicy.MOBILE_LOGIN,true));
    }
    @Test public void desktopLoginDestinationRequiresExplicitEligibleSelection() {
        ReBrowserMusicLifecycle s=new ReBrowserMusicLifecycle();s.account(false);
        assertFalse(s.loginUa(true));assertEquals(ReBrowserMusicPolicy.MOBILE_LOGIN,s.destination());
        s.window(true,true,true);assertEquals(ReBrowserMusicPolicy.MOBILE_LOGIN,s.destination());
        s.loginUa(true);assertEquals(ReBrowserMusicPolicy.DESKTOP_LOGIN,s.destination());
        s.window(true,false,true);assertEquals(ReBrowserMusicPolicy.MOBILE_LOGIN,s.destination());
    }
    @Test public void successfulReturnIsAutomaticallyVerifiedWithoutAManualRecheck() {
        ReBrowserMusicLifecycle s=new ReBrowserMusicLifecycle();s.account(false);
        // The official callback returns HOME; native recheck selects the lightweight account document.
        assertTrue(s.recheck()); assertEquals(ReBrowserMusicPolicy.ACCOUNT,s.destination()); assertFalse(s.player());
        assertTrue(s.account(true));assertTrue(s.player());assertTrue(s.desktop());assertFalse(s.recheck());
        s.account(false);assertTrue(s.recheck());assertEquals(ReBrowserMusicLifecycle.Stage.CHECKING,s.stage());
        assertEquals(ReBrowserMusicPolicy.ACCOUNT,s.destination());assertFalse(s.desktop());
    }
    @Test public void stopCannotBeRevivedAndNewInstanceStartsChecking() {
        ReBrowserMusicLifecycle s=new ReBrowserMusicLifecycle();s.account(true);s.stop();
        assertFalse(s.account(true));assertEquals(ReBrowserMusicLifecycle.Stage.STOPPED,s.stage());
        assertFalse(new ReBrowserMusicLifecycle().player());
    }
}
