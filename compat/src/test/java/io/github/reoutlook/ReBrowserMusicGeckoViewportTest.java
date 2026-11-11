package io.github.reoutlook;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.mozilla.geckoview.GeckoSessionSettings;

public final class ReBrowserMusicGeckoViewportTest {
    @Test public void desktopPlayerUaDoesNotShrinkAdaptedWindows() {
        assertEquals(GeckoSessionSettings.VIEWPORT_MODE_MOBILE,
                ReBrowserMusicGeckoPage.viewportMode(true, true, true));
    }

    @Test public void originalTabletPlayerKeepsDesktopViewport() {
        assertEquals(GeckoSessionSettings.VIEWPORT_MODE_DESKTOP,
                ReBrowserMusicGeckoPage.viewportMode(true, false, true));
    }

    @Test public void mobileLoginUsesMobileViewport() {
        assertEquals(GeckoSessionSettings.VIEWPORT_MODE_MOBILE,
                ReBrowserMusicGeckoPage.viewportMode(false, true, false));
    }

    @Test public void explicitDesktopLoginRetainsWideViewport() {
        assertEquals(GeckoSessionSettings.VIEWPORT_MODE_DESKTOP,
                ReBrowserMusicGeckoPage.viewportMode(true, false, false));
    }

    @Test public void physicalWindowPolicyOnlyExemptsFullLandscapeTablets() {
        for (boolean tablet : new boolean[] {false, true}) {
            for (boolean full : new boolean[] {false, true}) {
                for (boolean landscape : new boolean[] {false, true}) {
                    boolean original = tablet && full && landscape;
                    boolean adapted = !ReBrowserMusicPolicy.originalLayout(tablet, full, landscape);
                    assertEquals(original ? GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
                                    : GeckoSessionSettings.VIEWPORT_MODE_MOBILE,
                            ReBrowserMusicGeckoPage.viewportMode(true, adapted, true));
                }
            }
        }
    }
}
