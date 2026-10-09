package io.github.reoutlook;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class ReBrowserPopupHistoryTest {
    @Test
    public void initialBlankDocumentIsNotABackDestination() {
        assertFalse(ReBrowserWebController.hasPopupBackHistory(List.of(), -1));
        assertFalse(ReBrowserWebController.hasPopupBackHistory(List.of("about:blank"), 0));
        assertFalse(ReBrowserWebController.hasPopupBackHistory(
                List.of("https://example.com/"), 0));
        assertFalse(ReBrowserWebController.hasPopupBackHistory(
                List.of("about:blank", "https://example.com/"), 1));
        assertFalse(ReBrowserWebController.hasPopupBackHistory(
                Arrays.asList(null, "", "about:blank", "https://example.com/"), 3));
    }

    @Test
    public void realPageHistoryPrecedesReturningToTheSourceTab() {
        List<String> urls = List.of("about:blank", "https://example.com/", "https://example.com/next");
        assertTrue(ReBrowserWebController.hasPopupBackHistory(urls, 2));
        assertFalse(ReBrowserWebController.hasPopupBackHistory(urls, 1));
        assertFalse(ReBrowserWebController.hasPopupBackHistory(urls, 3));
        assertTrue(ReBrowserWebController.hasPopupBackHistory(
                List.of("https://example.com/", "https://cn.bing.com/"), 1));
    }

    @Test
    public void laterIntentionalBlankVisitsRemainPartOfPageHistory() {
        List<String> urls = List.of("about:blank", "https://example.com/", "about:blank",
                "https://example.com/next");
        assertTrue(ReBrowserWebController.hasPopupBackHistory(urls, 3));
        assertTrue(ReBrowserWebController.hasPopupBackHistory(urls, 2));
    }
}
