package io.github.reoutlook;

import static org.junit.Assert.*;
import android.content.Context;
import android.os.Looper;
import android.webkit.ValueCallback;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.regex.Pattern;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class) @Config(sdk=31)
public final class ReBrowserStorageEstimateTest {
    private Context context;
    private static final String MUSIC = ReBrowserApplications.MUSIC, MAIL = ReBrowserApplications.OUTLOOK;
    @Before public void setup() { context = RuntimeEnvironment.getApplication(); }
    private static final class Page implements ReBrowserStorageEstimate.Driver {
        String url = ReBrowserMusicPolicy.HOME, token;
        int evaluations;
        boolean hold;
        ValueCallback<String> callback;
        final ArrayDeque<String> statuses = new ArrayDeque<>();
        @Override public String url() { return url; }
        @Override public void evaluate(String script, ValueCallback<String> reply) {
            evaluations++; callback = reply;
            try {
                java.util.regex.Matcher m = Pattern.compile("token=(\"[^\"]+\")").matcher(script);
                assertTrue(m.find()); token = (String) new JSONTokener(m.group(1)).nextValue();
                if (!hold) complete(statuses.isEmpty() ? "ok" : statuses.remove());
            } catch (Exception e) { throw new AssertionError(e); }
        }
        void complete(String status) throws Exception {
            callback.onReceiveValue(JSONObject.quote(new JSONObject().put("token", token).put("status", status)
                    .put("usage", 4096).put("caches", 1024).toString()));
        }
    }
    @Test public void missingPageIsUnavailableWithoutCreatingProfilesOrTouchingData() throws Exception {
        ArrayList<JSONObject> replies = new ArrayList<>();
        ReBrowserStorageEstimate.request(context, MUSIC, replies::add);
        assertEquals("unavailable", replies.get(0).getString("status"));
        assertTrue(replies.get(0).isNull("cacheStorageBytes"));
        assertFalse(context.getDatabasePath(MailDatabase.DATABASE_NAME).exists());
        assertFalse(ReBrowserSubapplicationGate.blocked(context, MUSIC));
    }
    @Test public void originsAreFixedHttpsAndNeverAccountProbeDocuments() {
        assertEquals("https://music.163.com", ReBrowserStorageEstimate.origin(MUSIC, ReBrowserMusicPolicy.HOME));
        assertEquals("https://y.music.163.com", ReBrowserStorageEstimate.origin(MUSIC, ReBrowserMusicPolicy.MOBILE_LOGIN));
        assertNull(ReBrowserStorageEstimate.origin(MUSIC, ReBrowserMusicPolicy.ACCOUNT));
        assertNull(ReBrowserStorageEstimate.origin(MUSIC, "https://music.163.com.evil.test/st/webplayer"));
        assertNull(ReBrowserStorageEstimate.origin(MUSIC, "https://user@music.163.com/st/webplayer"));
        assertNull(ReBrowserStorageEstimate.origin(MUSIC, "https://music.163.com:8080/st/webplayer"));
        assertNull(ReBrowserStorageEstimate.origin(MAIL, "https://evil.test/?outlook.office.com"));
        assertNull(ReBrowserStorageEstimate.origin(MAIL, "https://outlook.office.com\\@evil.test/"));
        assertNull(ReBrowserStorageEstimate.origin(MAIL, "file:///outlook.office.com"));
        assertEquals("https://outlook.office.com", ReBrowserStorageEstimate.origin(MAIL, "https://outlook.office.com/mail/?not-exported=1"));
    }
    @Test public void byteValidationDistinguishesZeroFromUnsupportedAndRejectsUnsafeInputs() {
        assertEquals(Long.valueOf(0), ReBrowserStorageEstimate.bytes(0));
        assertEquals(Long.valueOf(4096), ReBrowserStorageEstimate.bytes(4096d));
        for (Object v : new Object[]{JSONObject.NULL, "0", -1, 1.5, Double.NaN, Double.POSITIVE_INFINITY, 9007199254740992d})
            assertNull(ReBrowserStorageEstimate.bytes(v));
    }
    @Test public void existingPageYieldsOnlyApproximateCurrentOriginNumbers() throws Exception {
        Page page = new Page(); page.statuses.add("pending"); page.statuses.add("ok");
        ReBrowserStorageEstimate.register(context, MUSIC, page);
        ArrayList<JSONObject> replies = new ArrayList<>(); ReBrowserStorageEstimate.request(context, MUSIC, replies::add);
        assertTrue(replies.isEmpty()); Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(220));
        JSONObject result = replies.get(0);
        assertEquals(1024, result.getLong("cacheStorageBytes")); assertEquals(4096, result.getLong("siteStorageBytes"));
        assertEquals("https://music.163.com", result.getString("origin")); assertFalse(result.getBoolean("includesHttpCache"));
        assertEquals("current-origin-only", result.getString("scope")); assertTrue(result.getBoolean("approximate"));
        assertEquals(2, page.evaluations);
    }
    @Test public void unsupportedEstimateDoesNotBlockNativeOperationsOrOtherTarget() throws Exception {
        Page page = new Page(); page.statuses.add("unsupported"); ReBrowserStorageEstimate.register(context, MUSIC, page);
        ReBrowserStorageEstimate.request(context, MUSIC, value -> assertEquals("unsupported", value.optString("status")));
        assertTrue(ReBrowserStorageEstimate.snapshot(context, MUSIC).isNull("cacheStorageBytes"));
        try (ReBrowserSubapplicationGate.Lease lease = ReBrowserSubapplicationGate.acquire(context, MUSIC)) { assertNotNull(lease); }
        context.getSharedPreferences("rb_music_resume_netease_v1", 0).edit().putString("fixture", "keep").commit();
        ReBrowserSubapplicationStorage.Backend backend = new ReBrowserSubapplicationStorage.Backend() {
            public boolean supported(boolean data) { return true; }
            public void clear(Context c, String id, boolean data, Runnable done) { done.run(); }
        };
        ArrayList<String> messages = new ArrayList<>(); ReBrowserSubapplicationStorage.clear(context, MUSIC, false, backend, messages::add);
        assertEquals(1, messages.size()); assertTrue(context.getSharedPreferences("rb_music_resume_netease_v1", 0).contains("fixture"));
        assertFalse(ReBrowserSubapplicationGate.blocked(context, MAIL)); assertFalse(ReBrowserSubapplicationGate.blocked(context, MUSIC));
    }
    @Test public void timeoutDoesNotReleaseActualEvaluationSlotOrAcceptLateResult() throws Exception {
        Page page = new Page(); page.hold = true; ReBrowserStorageEstimate.register(context, MUSIC, page);
        ArrayList<JSONObject> replies = new ArrayList<>(); ReBrowserStorageEstimate.request(context, MUSIC, replies::add);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ReBrowserStorageEstimate.TIMEOUT_MS + 1));
        assertEquals(1, replies.size()); assertEquals("unavailable", replies.get(0).getString("status"));
        ReBrowserStorageEstimate.request(context, MUSIC, value -> assertEquals("busy", value.optString("status")));
        assertEquals(1, page.evaluations); page.complete("ok");
        assertEquals("unavailable", ReBrowserStorageEstimate.snapshot(context, MUSIC).getString("status"));
        page.hold = false; ReBrowserStorageEstimate.request(context, MUSIC, replies::add);
        assertEquals(2, page.evaluations); assertEquals("ok", replies.get(1).getString("status"));
    }
    @Test public void navigationAndResetInvalidateSamplesAndPendingResults() throws Exception {
        Page page = new Page(); page.hold = true; ReBrowserStorageEstimate.register(context, MUSIC, page);
        ArrayList<JSONObject> replies = new ArrayList<>(); ReBrowserStorageEstimate.request(context, MUSIC, replies::add);
        ReBrowserStorageEstimate.invalidate(context, MUSIC); page.complete("ok");
        assertEquals("unavailable", ReBrowserStorageEstimate.snapshot(context, MUSIC).getString("status")); assertEquals(1, replies.size());
        page.hold = false; ReBrowserStorageEstimate.request(context, MUSIC, replies::add);
        assertEquals("ok", ReBrowserStorageEstimate.snapshot(context, MUSIC).getString("status"));
        ReBrowserStorageEstimate.invalidate(context, MUSIC);
        assertTrue(ReBrowserStorageEstimate.snapshot(context, MUSIC).isNull("siteStorageBytes"));
    }
    @Test public void activeCleanupOrQuarantineDoesNotRunJavascript() {
        Page page = new Page(); ReBrowserStorageEstimate.register(context, MUSIC, page);
        assertTrue(ReBrowserSubapplicationGate.begin(context, MUSIC, true));
        try { ReBrowserStorageEstimate.request(context, MUSIC, value -> assertEquals("unavailable", value.optString("status"))); assertEquals(0, page.evaluations); }
        finally { ReBrowserSubapplicationGate.end(context, MUSIC, true); }
    }
    @Test public void unrelatedOriginDoesNotExecuteAndTargetReplacementDropsOldCallback() throws Exception {
        Page old = new Page(); old.hold = true; ReBrowserStorageEstimate.register(context, MUSIC, old);
        ReBrowserStorageEstimate.request(context, MUSIC, value -> {});
        Page replacement = new Page(); replacement.url = "https://evil.test/"; ReBrowserStorageEstimate.register(context, MUSIC, replacement);
        old.complete("ok"); ReBrowserStorageEstimate.request(context, MUSIC, value -> {});
        assertEquals(0, replacement.evaluations); assertEquals("unavailable", ReBrowserStorageEstimate.snapshot(context, MUSIC).getString("status"));
    }
}
