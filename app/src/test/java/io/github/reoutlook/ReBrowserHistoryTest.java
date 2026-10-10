package io.github.reoutlook;

import static org.junit.Assert.*;

import android.content.Context;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class ReBrowserHistoryTest {
    private Context context;
    private ReBrowserHistory history;
    private ReBrowserWorkspaceController controller;
    private final AtomicLong now = new AtomicLong(10_000_000L);

    @Before public void setup() {
        context = RuntimeEnvironment.getApplication();
        context.deleteDatabase("rebrowser_history.db");
        context.getSharedPreferences("rebrowser_workspace_store_v1", Context.MODE_PRIVATE).edit().clear().commit();
        history = new ReBrowserHistory(context, now::get);
        history.setPaused(false);
        controller = new ReBrowserWorkspaceController(new ReBrowserStore(context), history);
        controller.initialize("https://example.com/");
    }

    @After public void finish() { history.setPaused(false); history.close(); }

    private ReBrowserStore.Workspace source() { return controller.activeWorkspace(); }
    private long visit(String path) {
        return history.record(source(), source().activeTab(), "https://example.com/" + path, path);
    }
    private long count() throws Exception { return history.snapshot().getLong("recordCount"); }
    private long ttl() { return ReBrowserHistory.RETENTION_MS; }

    @Test public void temporaryExpiresExactlyAtItsOwnDeadline() throws Exception {
        long id = visit("one"); now.addAndGet(ttl() - 1);
        assertNotNull(history.find(id)); now.incrementAndGet(); assertNull(history.find(id));
        assertEquals(0, count());
    }

    @Test public void secondaryAndPrimaryAreLongLived() throws Exception {
        assertTrue(controller.lockAsSecondary(source())); long one = visit("one");
        assertTrue(controller.promoteToPrimary(source(), false)); long two = visit("two");
        now.addAndGet(ttl() * 100);
        assertNull(history.find(one).expiresAt); assertNull(history.find(two).expiresAt);
        assertEquals(2, count());
    }

    @Test public void closingAChildNeverExpiresItsHistory() {
        assertTrue(controller.lockAsSecondary(source()));
        ReBrowserStore.Tab child = controller.addTab(source(), "https://example.com/child");
        long id = history.record(source(), child, child.url, "child");
        controller.closeTab(source(), child, "https://example.com/");
        now.addAndGet(ttl() * 100);
        assertNotNull(history.find(id)); assertNull(history.find(id).expiresAt);
    }

    @Test public void shelvingStartsDeadlineAtWholeWorkspaceClosure() {
        assertTrue(controller.lockAsSecondary(source())); long id = visit("old");
        now.addAndGet(ttl() * 100); long closedAt = now.get();
        assertTrue(controller.shelfSecondary(source()));
        assertEquals(Long.valueOf(closedAt + ttl()), history.find(id).expiresAt);
        now.addAndGet(ttl()); assertNull(history.find(id));
    }

    @Test public void deletingShelvedSourceDoesNotRenewDeadline() {
        ReBrowserStore.Workspace workspace = source();
        controller.lockAsSecondary(workspace); long id = visit("old"); controller.shelfSecondary(workspace);
        long deadline = history.find(id).expiresAt;
        now.addAndGet(ttl() / 2); controller.deleteShelvedSecondary(workspace);
        assertEquals(Long.valueOf(deadline), history.find(id).expiresAt);
    }

    @Test public void restoringUnexpiredSourceMakesRecordsLongLivedAgain() {
        ReBrowserStore.Workspace workspace = source(); controller.lockAsSecondary(workspace);
        long id = visit("old"); controller.shelfSecondary(workspace); now.addAndGet(ttl() / 2);
        assertTrue(controller.restoreSecondary(workspace)); assertNull(history.find(id).expiresAt);
        now.addAndGet(ttl() * 100); assertNotNull(history.find(id));
    }

    @Test public void restoringCannotResurrectExpiredHistory() throws Exception {
        ReBrowserStore.Workspace workspace = source(); controller.lockAsSecondary(workspace);
        visit("old"); controller.shelfSecondary(workspace); now.addAndGet(ttl());
        assertTrue(controller.restoreSecondary(workspace)); assertEquals(0, count());
    }

    @Test public void promotionPreservesOnlyStillValidTemporaryHistory() throws Exception {
        long expired = visit("expired"); now.addAndGet(ttl()); long live = visit("live");
        controller.lockAsSecondary(source()); assertNull(history.find(expired));
        assertNull(history.find(live).expiresAt); assertEquals(1, count());
    }

    @Test public void unlockGivesOldDurableRecordsThirtyMinuteGrace() {
        controller.lockAsSecondary(source()); long id = visit("old"); now.addAndGet(ttl() * 100);
        controller.unlockToTemporary(source()); assertEquals(Long.valueOf(now.get() + ttl()), history.find(id).expiresAt);
        now.addAndGet(ttl()); assertNull(history.find(id));
    }

    @Test public void temporaryClosureDoesNotExtendVisitDeadline() {
        long id = visit("one"); long deadline = history.find(id).expiresAt;
        now.addAndGet(ttl() / 2); controller.removeTemporary(source());
        assertEquals(Long.valueOf(deadline), history.find(id).expiresAt);
    }

    @Test public void maintainPersistsAndDisablingRenewsOnlyTimedRecords() throws Exception {
        ReBrowserStore.Workspace persistent = controller.createTemporary("https://example.com/persistent");
        controller.lockAsSecondary(persistent);
        long durable = history.record(persistent, persistent.activeTab(), persistent.activeTab().url, "persistent");
        long temporary = visit("one"); history.setMaintaining(true); history.close();
        history = new ReBrowserHistory(context, now::get); assertTrue(history.maintaining());
        now.addAndGet(ttl() * 10); assertNotNull(history.find(temporary)); assertEquals(0, history.clearTimed());
        history.setMaintaining(false);
        assertEquals(Long.valueOf(now.get() + ttl()), history.find(temporary).expiresAt);
        assertNull(history.find(durable).expiresAt);
        now.addAndGet(ttl()); assertNull(history.find(temporary)); assertEquals(1, count());
    }

    @Test public void closingDuringMaintainIsTimedAfterMaintainEnds() {
        controller.lockAsSecondary(source()); long id = visit("one"); history.setMaintaining(true);
        controller.shelfSecondary(source()); now.addAndGet(ttl() * 10); assertNotNull(history.find(id));
        history.setMaintaining(false); assertEquals(Long.valueOf(now.get() + ttl()), history.find(id).expiresAt);
    }

    @Test public void enablingMaintainDoesNotRecoverAlreadyExpiredRows() throws Exception {
        visit("expired"); now.addAndGet(ttl()); history.setMaintaining(true); assertEquals(0, count());
    }

    @Test public void settingMaintainOffAgainDoesNotRenewDeadlines() {
        long id = visit("one"); history.setMaintaining(true); history.setMaintaining(false);
        long deadline = history.find(id).expiresAt; now.addAndGet(1000); history.setMaintaining(false);
        assertEquals(Long.valueOf(deadline), history.find(id).expiresAt);
    }

    @Test public void clearTimedLeavesDurableAndWorksWhilePaused() throws Exception {
        visit("temporary"); ReBrowserStore.Workspace persistent = controller.createTemporary("https://example.com/persistent");
        controller.lockAsSecondary(persistent);
        history.record(persistent, persistent.activeTab(), persistent.activeTab().url, "persistent");
        history.setPaused(true); assertEquals(1, history.clearTimed()); assertEquals(1, count());
    }

    @Test public void pauseDoesNotFreezeExpiryAndSurvivesControllerRecreation() throws Exception {
        visit("old"); history.setPaused(true); assertEquals(0, visit("not-recorded"));
        try (ReBrowserHistory second = new ReBrowserHistory(context, now::get)) {
            assertTrue(second.paused());
        }
        now.addAndGet(ttl()); assertEquals(0, count());
    }

    @Test public void resumeSnapshotIncludesEveryOpenPageButNotShelvedSources() throws Exception {
        ReBrowserStore.Workspace other = controller.createTemporary("https://other.example/");
        controller.addTab(other, "https://other.example/two");
        ReBrowserStore.Workspace shelved = controller.createTemporary("https://shelved.example/");
        controller.lockAsSecondary(shelved); controller.shelfSecondary(shelved);
        history.setPaused(true);
        assertEquals(0, history.syncCurrent(controller.activeWorkspaces(), tab -> null, tab -> null));
        history.setPaused(false);
        AtomicLong lookups = new AtomicLong();
        assertEquals(3, history.syncCurrent(controller.activeWorkspaces(), tab -> {
            lookups.incrementAndGet(); return tab == other.activeTab() ? "https://other.example/current" : null;
        }, tab -> tab == other.activeTab() ? "Current title" : null));
        assertEquals(3, lookups.get()); assertEquals(3, count());
        assertTrue(history.entries(0, 10).stream().anyMatch(e -> e.title.equals("Current title")));
        assertFalse(history.entries(0, 10).stream().anyMatch(e -> e.workspaceId.equals(shelved.id)));
    }

    @Test public void startupSynchronizesBackgroundPagesOnlyOncePerProcess() throws Exception {
        ReBrowserStore.Tab visible = source().activeTab();
        controller.addTab(source(), "https://example.com/background");
        controller.createTemporary("https://other.example/background");
        assertEquals(2, history.syncStartup(controller.activeWorkspaces(), visible));
        assertEquals(2, count());
        assertFalse(history.entries(0, 10).stream().anyMatch(e -> e.tabId.equals(visible.id)));
        try (ReBrowserHistory recreated = new ReBrowserHistory(context, now::get)) {
            assertEquals(0, recreated.syncStartup(controller.activeWorkspaces(), visible));
        }
    }

    @Test public void lateTitleUpdatesNeverRecreateDeletedHistoryOrRetitlePreviousPage() {
        long id = visit("one"); now.incrementAndGet();
        history.updateTitle(source().activeTab(), "https://example.com/one", "Actual title");
        assertEquals("Actual title", history.find(id).title);
        assertEquals(10_000_000L, history.find(id).visitedAt);
        history.updateTitle(source().activeTab(), "https://example.com/other", "Wrong title");
        assertEquals("Actual title", history.find(id).title);
        history.delete(id); history.updateTitle(source().activeTab(), "https://example.com/one", "Late title");
        assertNull(history.find(id));
    }

    @Test public void paginationAndRepeatedVisitsDoNotDiscardPermanentRows() throws Exception {
        controller.lockAsSecondary(source());
        for (int i = 0; i < 125; i++) visit("same");
        assertEquals(125, count());
        List<ReBrowserHistory.Entry> first = history.entries(0, 50);
        assertEquals(50, first.size()); assertEquals(50, history.entries(50, 50).size());
        assertEquals(25, history.entries(100, 50).size()); assertTrue(first.get(0).id > first.get(1).id);
    }

    @Test public void reconcileStartsMissingSourceDeadlineOnceAndNeverRevivesExpiredRows() {
        controller.lockAsSecondary(source()); long id = visit("one");
        history.reconcile(List.of()); long deadline = history.find(id).expiresAt;
        now.addAndGet(ttl() / 2); history.reconcile(List.of());
        assertEquals(Long.valueOf(deadline), history.find(id).expiresAt);
        now.addAndGet(ttl()); history.reconcile(controller.activeWorkspaces()); assertNull(history.find(id));
    }

    @Test public void URLsAreWebOnlyAndSnapshotDoesNotExposeBrowsingData() throws Exception {
        assertNull(ReBrowserHistory.cleanUrl("about:blank"));
        assertNull(ReBrowserHistory.cleanUrl("javascript:alert(1)"));
        assertNull(ReBrowserHistory.cleanUrl("file:///data/private"));
        assertNull(ReBrowserHistory.cleanUrl("https:///missing-host"));
        String url = "https://user:pass@example.com/page?token=local-only#section";
        long id = history.record(source(), source().activeTab(), url, "Private title");
        assertEquals("https://example.com/page?token=local-only#section", history.find(id).url);
        JSONObject status = history.snapshot(); String serialized = status.toString();
        assertFalse(serialized.contains("example.com")); assertFalse(serialized.contains("token"));
        assertFalse(serialized.contains("Private title")); assertFalse(status.getBoolean("administratorCanSet"));
        assertEquals(1800, status.getLong("retentionSeconds"));
    }
}
