package io.github.reoutlook;

import static org.junit.Assert.*;

import android.app.Application;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class)
public final class OutlookGeckoBridgeAssetsTest {
    private String asset(String name) throws Exception {
        try (var input = RuntimeEnvironment.getApplication().getAssets()
                .open("extensions/rebrowser/" + name)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test public void mainWorldUsesExactSharedCaptureWithoutEval() throws Exception {
        String script = asset("outlook-page.js");
        assertTrue(script.endsWith(CaptureScript.OWA_SERVICE_RESPONSES));
        assertTrue(script.contains("reOutlookBridge"));
        assertFalse(script.contains(".eval("));
        assertFalse(script.contains("wrappedJSObject"));
        assertFalse(script.contains("browser.runtime"));
    }

    @Test public void isolatedCommandsUseExactDomAdaptersWithoutPageEval() throws Exception {
        String script = asset("outlook-content.js");
        assertTrue(script.contains(CaptureScript.CURRENT_MESSAGE));
        assertTrue(script.contains(CaptureScript.SCROLL_MAIL_LIST));
        assertFalse(script.contains(".eval("));
        assertFalse(script.contains("wrappedJSObject"));
        assertFalse(script.contains("OWA_SERVICE_RESPONSES"));
    }

    @Test public void manifestSeparatesPageWorldAndPrivilegedPort() throws Exception {
        JSONArray scripts = new JSONObject(asset("manifest.json")).getJSONArray("content_scripts");
        JSONObject page = null, isolated = null;
        for (int i = 0; i < scripts.length(); i++) {
            JSONObject value = scripts.getJSONObject(i);
            String file = value.getJSONArray("js").getString(0);
            if ("outlook-page.js".equals(file)) page = value;
            if ("outlook-content.js".equals(file)) isolated = value;
        }
        assertNotNull(page);
        assertNotNull(isolated);
        assertEquals("MAIN", page.getString("world"));
        assertEquals("ISOLATED", isolated.getString("world"));
        assertEquals("document_start", page.getString("run_at"));
        assertEquals("document_start", isolated.getString("run_at"));
        assertFalse(page.getBoolean("all_frames"));
        assertEquals(isolated.getJSONArray("matches").toString(),
                page.getJSONArray("matches").toString());
        assertFalse(page.getJSONArray("matches").toString().contains("login.live.com"));
    }
}
