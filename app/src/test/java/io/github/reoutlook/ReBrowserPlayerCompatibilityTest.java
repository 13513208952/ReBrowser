package io.github.reoutlook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Set;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class ReBrowserPlayerCompatibilityTest {
    private static final String PROFILE_A = "rebrowser_workspace_" + "a".repeat(32);
    private static final String PROFILE_B = "rebrowser_workspace_" + "b".repeat(32);
    private static final String MOBILE = "https://m.youtube.com/watch?v=test";

    @Test
    public void exactHttpsOriginsOnly() {
        assertEquals("https://m.youtube.com", ReBrowserPlayerCompatibility.supportedOrigin(MOBILE));
        assertEquals("https://www.youtube.com", ReBrowserPlayerCompatibility.supportedOrigin(
                "https://WWW.YOUTUBE.COM:443/watch?v=test"));
        for (String url : Set.of("http://m.youtube.com/", "https://m.youtube.com:444/",
                "https://youtube.com.evil.test/", "https://m.youtube.com@evil.test/",
                "https://user@m.youtube.com/", "https://evil.m.youtube.com/",
                "https://www.youtube-nocookie.com/embed/test", "about:blank")) {
            assertEquals(url, "", ReBrowserPlayerCompatibility.supportedOrigin(url));
        }
    }

    @Test
    public void regularBrowsingDefaultsOnAndCanBeDisabledPerProfile() {
        ReBrowserPlayerCompatibility patch = new ReBrowserPlayerCompatibility();
        assertTrue(patch.enabled(PROFILE_A, MOBILE));
        assertTrue(patch.enabled(PROFILE_A, "https://www.youtube.com/"));
        assertEquals(ReBrowserPlayerCompatibility.YOUTUBE_ORIGINS, patch.originsForProfile(PROFILE_A));
        assertTrue(patch.setEnabled(PROFILE_A, false));
        assertFalse(patch.enabled(PROFILE_A, MOBILE));
        assertFalse(patch.enabled(PROFILE_A, "https://www.youtube.com/"));
        assertTrue(patch.originsForProfile(PROFILE_A).isEmpty());
        assertTrue(patch.enabled(PROFILE_B, MOBILE));
        assertTrue(patch.setEnabled(PROFILE_A, true));
        assertTrue(patch.enabled(PROFILE_A, MOBILE));
    }

    @Test
    public void alternativeModeCanDefaultOffWithoutChangingTheOriginBoundary() {
        ReBrowserPlayerCompatibility patch = new ReBrowserPlayerCompatibility(false);
        assertFalse(patch.enabled(PROFILE_A, MOBILE));
        assertTrue(patch.originsForProfile(PROFILE_A).isEmpty());
        assertTrue(patch.setEnabled(PROFILE_A, true));
        assertTrue(patch.enabled(PROFILE_A, MOBILE));
        assertFalse(patch.enabled(PROFILE_B, MOBILE));
        assertFalse(patch.enabled(PROFILE_A, "https://example.com/"));
        assertTrue(patch.setEnabled(PROFILE_A, false));
        assertFalse(patch.enabled(PROFILE_A, MOBILE));
    }

    @Test
    public void defaultProfileAndUnsupportedSitesCannotBeEnabled() {
        ReBrowserPlayerCompatibility patch = new ReBrowserPlayerCompatibility();
        assertFalse(patch.setEnabled("Default", true));
        assertFalse(patch.enabled("Default", MOBILE));
        assertFalse(patch.enabled(PROFILE_A, "https://example.com/"));
        assertTrue(patch.originsForProfile("Default").isEmpty());
    }

    @Test
    public void profileCleanupAndNewRuntimeRestoreTheModeDefault() {
        ReBrowserPlayerCompatibility patch = new ReBrowserPlayerCompatibility();
        patch.setEnabled(PROFILE_A, false);
        patch.setEnabled(PROFILE_B, false);
        patch.clearForProfile(PROFILE_A);
        assertTrue(patch.enabled(PROFILE_A, MOBILE));
        assertFalse(patch.enabled(PROFILE_B, MOBILE));
        assertTrue(new ReBrowserPlayerCompatibility().enabled(PROFILE_B, MOBILE));

        ReBrowserPlayerCompatibility offByDefault = new ReBrowserPlayerCompatibility(false);
        offByDefault.setEnabled(PROFILE_A, true);
        offByDefault.clearForProfile(PROFILE_A);
        assertFalse(offByDefault.enabled(PROFILE_A, MOBILE));
    }

    @Test
    public void scriptGenerationDoesNotExpandTheAllowlist() throws Exception {
        String script = ReBrowserPermissionScript.documentStart(Set.of(
                "https://m.youtube.com", "https://www.youtube.com", "https://evil.test"));
        String marker = "const youtubePlayerOrigins = ";
        int start = script.indexOf(marker) + marker.length();
        org.json.JSONArray origins = new org.json.JSONArray(
                script.substring(start, script.indexOf(';', start)));
        assertEquals(2, origins.length());
        assertEquals("https://m.youtube.com", origins.getString(0));
        assertEquals("https://www.youtube.com", origins.getString(1));
        assertFalse(script.contains("https://evil.test"));
        assertTrue(ReBrowserPermissionScript.documentStart(Set.of()).contains(
                "const youtubePlayerOrigins = [];"));
    }
}
