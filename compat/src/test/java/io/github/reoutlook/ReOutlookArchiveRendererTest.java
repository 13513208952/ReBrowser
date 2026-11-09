package io.github.reoutlook;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class)
public final class ReOutlookArchiveRendererTest {
    @Test public void mailCssAndScriptsNeverAppearAsBodyText() {
        String html = "<html><head><style>.header{color:red}</style></head><body>"
                + "<!-- @font-face {font-family:'Mail Sans'} -->"
                + "<style type='text/css'>.rps_cc12 {display:block}</style>"
                + "<script>neverRenderSecret()</script>"
                + "<p>Readable <b>offline</b> mail.</p></body></html>";
        String text = ReOutlookArchiveRenderer.readableBody(html).toString();
        assertTrue(text.contains("Readable offline mail."));
        assertFalse(text.contains("@font-face"));
        assertFalse(text.contains(".rps_cc12"));
        assertFalse(text.contains("neverRenderSecret"));
    }

    @Test public void emptyBodyDoesNotCrash() {
        assertTrue(ReOutlookArchiveRenderer.readableBody(null).toString().isEmpty());
    }
}
