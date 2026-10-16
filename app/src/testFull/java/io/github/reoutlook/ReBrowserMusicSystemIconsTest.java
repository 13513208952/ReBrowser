package io.github.reoutlook;

import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class) @Config(sdk=31)
public final class ReBrowserMusicSystemIconsTest {
    @Test public void preAndroid16KeepsItsExistingIconAndRatingPath() {
        for(int sdk:new int[]{29,30,33,34,35}) for(boolean liked:new boolean[]{false,true})
            assertEquals(android.R.drawable.btn_star_big_on,ReBrowserMusicSystemIcons.like(sdk,liked));
    }
    @Test public void android16CustomActionHasDistinctOutlinedAndFilledHearts() {
        assertEquals(R.drawable.ic_rb_music_heart_outline,ReBrowserMusicSystemIcons.like(36,false));
        assertEquals(R.drawable.ic_rb_music_heart_filled,ReBrowserMusicSystemIcons.like(36,true));
        assertNotEquals(ReBrowserMusicSystemIcons.like(36,false),ReBrowserMusicSystemIcons.like(36,true));
        assertNotEquals(android.R.drawable.btn_star_big_on,ReBrowserMusicSystemIcons.like(36,false));
    }
    @Test public void allLyricModesHaveDistinctPackagedArtwork() {
        java.util.Set<Integer> icons = new java.util.HashSet<>();
        for (var mode : ReBrowserMusicLyricWindow.Mode.values()) {
            int icon = ReBrowserMusicSystemIcons.lyrics(mode); assertTrue(icons.add(icon));
            assertNotNull(RuntimeEnvironment.getApplication().getDrawable(icon));
        }
    }
    @Test public void bothVectorsArePackagedAndInflate() {
        assertNotNull(RuntimeEnvironment.getApplication().getDrawable(ReBrowserMusicSystemIcons.like(36,false)));
        assertNotNull(RuntimeEnvironment.getApplication().getDrawable(ReBrowserMusicSystemIcons.like(36,true)));
    }
}
