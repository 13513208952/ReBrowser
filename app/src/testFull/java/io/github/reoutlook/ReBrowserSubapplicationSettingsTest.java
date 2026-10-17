package io.github.reoutlook;

import static org.junit.Assert.*;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import java.time.Duration;
import java.util.ArrayList;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

@RunWith(RobolectricTestRunner.class) @Config(sdk=31)
public final class ReBrowserSubapplicationSettingsTest {
    private Context context;
    private static final String MAIL = ReBrowserApplications.OUTLOOK, MUSIC = ReBrowserApplications.MUSIC;
    @Before public void setup() {
        context = RuntimeEnvironment.getApplication();
        Shadows.shadowOf((android.app.Application) context).grantPermissions(android.Manifest.permission.HIDE_OVERLAY_WINDOWS);
    }
    private void put(String file) { assertTrue(context.getSharedPreferences(file, 0).edit().putString("fixture", "keep").commit()); }
    private boolean has(String file) { return context.getSharedPreferences(file, 0).contains("fixture"); }
    private static final class FakeWeb implements ReBrowserSubapplicationStorage.Backend {
        boolean support = true, fail;
        int calls;
        String target;
        boolean data;
        Runnable pending;
        @Override public boolean supported(boolean data) { return support; }
        @Override public void clear(Context context, String id, boolean data, Runnable done) {
            calls++; target = id; this.data = data;
            if (fail) throw new IllegalStateException("fixture-failure");
            pending = done;
        }
    }
    private String labels(View view) {
        String value = view instanceof android.widget.TextView ? ((android.widget.TextView) view).getText().toString() : "";
        if (view.getContentDescription() != null) value += view.getContentDescription();
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) value += labels(((ViewGroup) view).getChildAt(i));
        return value;
    }
    @Test public void twoGearRoutesAreFixedAndOutsideAdminTask() {
        for (String id : new String[]{MAIL, MUSIC}) {
            Intent intent = ReBrowserApplications.applicationSettingsIntent(context, id);
            assertEquals("io.github.reoutlook.ReBrowserSubapplicationSettingsActivity", intent.getComponent().getClassName());
            assertEquals(id, intent.getStringExtra(ReBrowserApplications.EXTRA_APPLICATION));
            assertEquals(0, intent.getFlags() & Intent.FLAG_ACTIVITY_CLEAR_TOP);
        }
        assertThrows(IllegalArgumentException.class, () -> ReBrowserApplications.applicationSettingsIntent(context, "Default"));
    }
    @Test public void musicSourceIsInChildNotPeerPageAndMailDoesNotHaveIt() {
        for (String id : new String[]{MAIL, MUSIC}) {
            try (ActivityController<ReBrowserSubapplicationSettingsActivity> c = Robolectric.buildActivity(
                    ReBrowserSubapplicationSettingsActivity.class, ReBrowserApplications.applicationSettingsIntent(context, id)).setup()) {
                String text = labels(c.get().findViewById(android.R.id.content));
                assertEquals(MUSIC.equals(id), text.contains("音乐源工作模式"));
                assertTrue(text.contains("清除缓存")); assertTrue(text.contains("清除全部数据"));
                assertTrue(text.contains("缓存估算")); assertTrue(text.contains("不可撤销"));
                assertFalse(context.getDatabasePath(MailDatabase.DATABASE_NAME).exists());
                assertFalse(ReBrowserSubapplicationGate.blocked(context, id));
            }
        }
    }
    private android.widget.Button button(View view, String text) {
        if (view instanceof android.widget.Button && text.contentEquals(((android.widget.Button) view).getText())) return (android.widget.Button) view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            android.widget.Button found = button(((ViewGroup) view).getChildAt(i), text); if (found != null) return found;
        }
        return null;
    }
    private void tap(android.widget.Button target, int flags) {
        android.view.MotionEvent.PointerProperties p = new android.view.MotionEvent.PointerProperties(); p.id = 0; p.toolType = android.view.MotionEvent.TOOL_TYPE_FINGER;
        android.view.MotionEvent.PointerCoords c = new android.view.MotionEvent.PointerCoords(); c.x = 10; c.y = 10; c.pressure = 1;
        long now = android.os.SystemClock.uptimeMillis();
        for (int action : new int[]{android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP}) {
            android.view.MotionEvent e = android.view.MotionEvent.obtain(now, now + 1, action, 1,
                    new android.view.MotionEvent.PointerProperties[]{p}, new android.view.MotionEvent.PointerCoords[]{c},
                    0, 0, 1, 1, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, flags);
            try { target.dispatchTouchEvent(e); } finally { e.recycle(); }
        }
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    }
    @Test public void cancellationAndUnsupportedConfirmationDoNotDeleteData() {
        put("account_preferences");
        try (ActivityController<ReBrowserSubapplicationSettingsActivity> c = Robolectric.buildActivity(
                ReBrowserSubapplicationSettingsActivity.class, ReBrowserApplications.applicationSettingsIntent(context, MAIL)).setup()) {
            android.widget.Button clear = button(c.get().findViewById(android.R.id.content), "清除全部数据");
            assertFalse(clear.isEnabled());
            // Exercise the dialog in the unsupported-provider fixture; production has no feature override.
            clear.setEnabled(true); clear.performClick();
            android.app.AlertDialog dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
            assertNotNull(dialog);
            String warning = ((android.widget.TextView) dialog.findViewById(android.R.id.message)).getText().toString();
            assertTrue(warning.contains("不可撤销")); assertTrue(warning.contains("全部本机数据"));
            assertTrue(warning.length() < 50); assertFalse(warning.contains("WebView"));
            android.widget.Button confirm = dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE);
            java.util.concurrent.atomic.AtomicInteger clicked = new java.util.concurrent.atomic.AtomicInteger();
            confirm.setOnClickListener(v -> clicked.incrementAndGet()); confirm.layout(0, 0, 200, 100);
            tap(confirm, android.view.MotionEvent.FLAG_WINDOW_IS_OBSCURED);
            tap(confirm, android.view.MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED);
            assertEquals(0, clicked.get()); tap(confirm, 0); assertEquals(1, clicked.get());
            dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).performClick();
            assertTrue(has("account_preferences")); assertFalse(ReBrowserSubapplicationGate.blocked(context, MAIL));
            clear.setEnabled(true); clear.performClick();
            dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
            assertTrue(has("account_preferences")); assertFalse(ReBrowserSubapplicationGate.blocked(context, MAIL));
        }
    }
    @Test public void unknownApplicationCannotOpenSettings() {
        try (ActivityController<ReBrowserSubapplicationSettingsActivity> c = Robolectric.buildActivity(
                ReBrowserSubapplicationSettingsActivity.class, new Intent().putExtra(ReBrowserApplications.EXTRA_APPLICATION, "file:///private")).setup()) {
            assertTrue(c.get().isFinishing());
        }
    }
    @Test public void admissionAndDrainAreTargetScopedAndLeaseCloseIsIdempotent() {
        ReBrowserSubapplicationGate.Lease lease = ReBrowserSubapplicationGate.acquire(context, MAIL);
        assertEquals(1, ReBrowserSubapplicationGate.users(context, MAIL));
        assertTrue(ReBrowserSubapplicationGate.begin(context, MAIL, true));
        assertThrows(IllegalStateException.class, () -> ReBrowserSubapplicationGate.acquire(context, MAIL));
        try (ReBrowserSubapplicationGate.Lease other = ReBrowserSubapplicationGate.acquire(context, MUSIC)) {
            assertEquals(1, ReBrowserSubapplicationGate.users(context, MUSIC));
        }
        lease.close(); lease.close(); assertEquals(0, ReBrowserSubapplicationGate.users(context, MAIL));
        ReBrowserSubapplicationGate.end(context, MAIL, true);
        assertFalse(ReBrowserSubapplicationGate.blocked(context, MAIL));
    }
    @Test public void cacheRequestPreservesNativeDataAndDoesNotClaimCompletion() {
        put("account_preferences"); put("ui_preferences");
        try (MailDatabase db = new MailDatabase(context)) { db.getWritableDatabase(); }
        FakeWeb web = new FakeWeb(); ArrayList<String> results = new ArrayList<>();
        ReBrowserSubapplicationStorage.clear(context, MAIL, false, web, results::add);
        assertEquals(MAIL, web.target); assertFalse(web.data); assertTrue(results.isEmpty());
        web.pending.run();
        assertTrue(context.getDatabasePath(MailDatabase.DATABASE_NAME).exists());
        assertTrue(has("account_preferences")); assertTrue(has("ui_preferences"));
        assertTrue(results.get(0).contains("已提交")); assertFalse(ReBrowserSubapplicationGate.blocked(context, MAIL));
    }
    @Test public void mailDataIsDeletedOnlyAfterWebCompletionAndOtherDataSurvives() {
        new ReOutlookLauncherVisibility(context).setVisible(true);
        put("account_preferences"); put("ui_preferences"); put("rb_music_resume_netease_v1");
        put("rebrowser_preferences"); put("rebrowser_admin_state_v1");
        try (MailDatabase db = new MailDatabase(context)) { db.getWritableDatabase(); }
        FakeWeb web = new FakeWeb();
        ReBrowserSubapplicationStorage.clear(context, MAIL, true, web, ignored -> {});
        assertTrue(has("account_preferences")); assertTrue(context.getDatabasePath(MailDatabase.DATABASE_NAME).exists());
        assertThrows(IllegalStateException.class, () -> new MailDatabase(context));
        web.pending.run();
        assertFalse(has("account_preferences")); assertFalse(has("ui_preferences"));
        assertFalse(context.getDatabasePath(MailDatabase.DATABASE_NAME).exists());
        assertTrue(has("rb_music_resume_netease_v1")); assertTrue(has("rebrowser_preferences")); assertTrue(has("rebrowser_admin_state_v1"));
        assertFalse(ReBrowserSubapplicationGate.blocked(context, MAIL));
        assertTrue(new ReOutlookLauncherVisibility(context).visible());
    }
    @Test public void musicResetIsLimitedToThreePreferencesAndItsProfile() {
        new ReBrowserMusicEntrance(context).setEnabled(true);
        for (String file : new String[]{"rb_music_sources_v1", "rb_music_resume_netease_v1", "rb_music_lyric_position_v1", "account_preferences", "rebrowser_applications_v1"}) put(file);
        FakeWeb web = new FakeWeb();
        ReBrowserSubapplicationStorage.clear(context, MUSIC, true, web, ignored -> {});
        assertEquals(MUSIC, web.target); assertTrue(web.data); web.pending.run();
        assertFalse(has("rb_music_sources_v1")); assertFalse(has("rb_music_resume_netease_v1")); assertFalse(has("rb_music_lyric_position_v1"));
        assertTrue(has("account_preferences")); assertTrue(has("rebrowser_applications_v1"));
        assertTrue(new ReBrowserMusicEntrance(context).enabled());
        assertEquals("Default", ReBrowserSubapplicationStorage.profileName(MAIL));
        assertEquals(ReBrowserMusicPolicy.PROFILE, ReBrowserSubapplicationStorage.profileName(MUSIC));
    }
    @Test public void unsupportedProviderNeverBeginsOrClosesData() {
        put("account_preferences"); FakeWeb web = new FakeWeb(); web.support = false;
        ReBrowserSubapplicationStorage.clear(context, MAIL, true, web, ignored -> {});
        assertEquals(0, web.calls); assertTrue(has("account_preferences")); assertFalse(ReBrowserSubapplicationGate.blocked(context, MAIL));
    }
    @Test public void acceptedMailWritesMustDrainAndProtectedExportIsNotForceClosed() {
        try (MailDatabase exporter = new MailDatabase(context)) {
            exporter.getWritableDatabase(); FakeWeb web = new FakeWeb();
            ReBrowserSubapplicationStorage.clear(context, MAIL, true, web, ignored -> {});
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofSeconds(4));
            assertEquals(0, web.calls); assertTrue(context.getDatabasePath(MailDatabase.DATABASE_NAME).exists());
            assertFalse(ReBrowserSubapplicationGate.blocked(context, MAIL));
        }
    }
    @Test public void partialResetRemainsQuarantinedAndCanBeRetried() {
        put("account_preferences"); FakeWeb fail = new FakeWeb(); fail.fail = true;
        ReBrowserSubapplicationStorage.clear(context, MAIL, true, fail, ignored -> {});
        assertTrue(ReBrowserSubapplicationGate.pendingData(context, MAIL));
        assertFalse(ReBrowserSubapplicationGate.running(context, MAIL));
        assertThrows(IllegalStateException.class, () -> new MailDatabase(context));
        FakeWeb retry = new FakeWeb(); ReBrowserSubapplicationStorage.clear(context, MAIL, true, retry, ignored -> {});
        retry.pending.run(); assertFalse(ReBrowserSubapplicationGate.blocked(context, MAIL));
    }
    @Test public void asyncResetNeverUnlocksOnTimeoutAndRejectsDuplicateRequests() {
        FakeWeb web = new FakeWeb(); ReBrowserSubapplicationStorage.clear(context, MUSIC, true, web, ignored -> {});
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMinutes(1));
        assertTrue(ReBrowserSubapplicationGate.blocked(context, MUSIC));
        ReBrowserSubapplicationStorage.clear(context, MUSIC, true, web, ignored -> {});
        assertEquals(1, web.calls); web.pending.run(); web.pending.run();
        assertFalse(ReBrowserSubapplicationGate.blocked(context, MUSIC));
    }
    @Test public void pendingIntentSurvivesProcessStateRecreation() {
        assertTrue(ReBrowserSubapplicationGate.begin(context, MUSIC, true)); ReBrowserSubapplicationGate.end(context, MUSIC, false);
        ReflectionHelpers.setStaticField(ReBrowserSubapplicationGate.class, "state", null);
        assertTrue(ReBrowserSubapplicationGate.blocked(context, MUSIC));
        assertThrows(IllegalStateException.class, () -> ReBrowserSubapplicationGate.acquire(context, MUSIC));
    }
    @Test public void closedMailHelperCannotReopenAfterReset() {
        MailDatabase database = new MailDatabase(context); database.getWritableDatabase(); database.close(); database.close();
        assertThrows(IllegalStateException.class, database::getWritableDatabase);
        assertThrows(IllegalStateException.class, database::getReadableDatabase);
        assertEquals(0, ReBrowserSubapplicationGate.users(context, MAIL));
    }
    @Test public void nativeDeletionNeedsDrainFenceAndCannotTakePaths() {
        assertThrows(IllegalStateException.class, () -> ReBrowserSubapplicationStorage.clearNativeData(context, MAIL));
        assertTrue(ReBrowserSubapplicationGate.begin(context, MAIL, false));
        assertThrows(IllegalStateException.class, () -> ReBrowserSubapplicationStorage.clearNativeData(context, MAIL));
        ReBrowserSubapplicationGate.end(context, MAIL, true);
        assertThrows(IllegalArgumentException.class, () -> ReBrowserSubapplicationStorage.profileName("../Default"));
    }
    @Test public void nativeSummaryDoesNotPretendUnknownWebBytesAreZeroOrExportContents() throws Exception {
        JSONObject info = ReBrowserSubapplicationStorage.snapshot(context, MAIL);
        assertTrue(info.isNull("webCacheBytes")); assertTrue(info.isNull("webDataBytes")); assertFalse(info.getBoolean("completeSizeAvailable"));
        assertFalse(info.getBoolean("administratorCanClear")); assertFalse(info.toString().contains("mail_cache.db"));
        assertFalse(info.toString().contains("account_preferences")); assertFalse(info.toString().contains("cookie"));
        assertFalse(context.getDatabasePath(MailDatabase.DATABASE_NAME).exists());
    }
    @Test public void malformedCleanupJournalIsQuarantinedAndExplicitResetCanRepairIt() {
        assertTrue(context.getSharedPreferences("rb_subapplication_cleanup_v1", 0).edit().putString(MUSIC, "malformed").commit());
        ReflectionHelpers.setStaticField(ReBrowserSubapplicationGate.class, "state", null);
        assertTrue(ReBrowserSubapplicationGate.blocked(context, MUSIC));
        FakeWeb web = new FakeWeb(); ReBrowserSubapplicationStorage.clear(context, MUSIC, true, web, ignored -> {});
        web.pending.run(); assertFalse(ReBrowserSubapplicationGate.blocked(context, MUSIC));
    }
    @Test public void failedMailHelperConstructionDoesNotLeakItsDrainLease() {
        assertTrue(context.getSharedPreferences("account_preferences", 0).edit().putBoolean("active_account_key", true).commit());
        assertThrows(ClassCastException.class, () -> new MailDatabase(context));
        assertEquals(0, ReBrowserSubapplicationGate.users(context, MAIL));
    }
    @Test public void administratorRoutesAreFixedReadOnlyLevelOneAndAudited() throws Exception {
        for (String operation : new String[]{"GET_APPLICATION_STORAGE", "OPEN_APPLICATION_SETTINGS"}) {
            for (String id : new String[]{MAIL, MUSIC}) {
                JSONObject request = new JSONObject().put("operation", operation).put("applicationId", id);
                ReBrowserAdminProtocol.prepareExternalRequest(request); assertEquals(1, request.getInt("authorizationLevel"));
                ReBrowserAdminProtocol.recordStatus(context, request, "completed", "fixture", "fixture", null, null);
                assertEquals(id, ReBrowserAdminProtocol.audit(context).getJSONObject(0).getString("applicationId"));
            }
            for (Object target : new Object[]{"Default", "browser", "../outlook", "file:///private", true, JSONObject.NULL})
                assertThrows(SecurityException.class, () -> ReBrowserAdminProtocol.prepareExternalRequest(new JSONObject().put("operation", operation).put("applicationId", target)));
            assertThrows(SecurityException.class, () -> ReBrowserAdminProtocol.prepareExternalRequest(new JSONObject().put("operation", operation)));
        }
        for (String operation : new String[]{"CLEAR_APPLICATION_DATA", "CLEAR_APPLICATION_CACHE", "SET_APPLICATION_ENABLED"})
            assertThrows(SecurityException.class, () -> ReBrowserAdminProtocol.prepareExternalRequest(new JSONObject().put("operation", operation).put("applicationId", MUSIC)));
    }
}
