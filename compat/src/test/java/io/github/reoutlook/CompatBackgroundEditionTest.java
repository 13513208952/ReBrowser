package io.github.reoutlook;

import static org.junit.Assert.*;

import android.app.Application;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class)
public final class CompatBackgroundEditionTest {
    @Test public void geckoDefaultsToPhotographicFullSuite() {
        assertTrue(BuildConfig.FULL_SUITE);
        assertEquals(R.raw.rb_new_tab_background, ReBrowserBackgroundAsset.resourceId());
        ReBrowserPreferences preferences = new ReBrowserPreferences(RuntimeEnvironment.getApplication());
        assertEquals(ReBrowserPreferences.BACKGROUND_EXCLUSIVE, preferences.newTabBackground());
        assertTrue(preferences.exclusiveBackgroundEnabled());
    }

    @Test public void packagedMasterIsTheWebpPhoto() throws Exception {
        try (var input = RuntimeEnvironment.getApplication().getResources()
                .openRawResource(ReBrowserBackgroundAsset.resourceId())) {
            byte[] header = input.readNBytes(12);
            assertEquals("RIFF", new String(header, 0, 4, java.nio.charset.StandardCharsets.US_ASCII));
            assertEquals("WEBP", new String(header, 8, 4, java.nio.charset.StandardCharsets.US_ASCII));
        }
    }

    @Test public void explicitOwnerWhiteChoiceRemainsReversible() {
        ReBrowserPreferences preferences = new ReBrowserPreferences(RuntimeEnvironment.getApplication());
        preferences.setNewTabBackground(ReBrowserPreferences.BACKGROUND_WHITE);
        assertFalse(preferences.exclusiveBackgroundEnabled());
        preferences.setNewTabBackground(ReBrowserPreferences.BACKGROUND_EXCLUSIVE);
        assertTrue(preferences.exclusiveBackgroundEnabled());
    }
}
