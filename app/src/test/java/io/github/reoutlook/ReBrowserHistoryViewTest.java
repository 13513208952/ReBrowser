package io.github.reoutlook;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ReBrowserHistoryViewTest {
    private ReBrowserHistory history;
    private ReBrowserHistoryView view;
    private ReBrowserStore.Workspace workspace;
    private final AtomicLong now = new AtomicLong(10_000_000);
    private final List<Boolean> pauses = new ArrayList<>();
    private final List<String> opened = new ArrayList<>();

    @Before public void setup() {
        RuntimeEnvironment.getApplication().deleteDatabase("rebrowser_history.db");
        history = new ReBrowserHistory(RuntimeEnvironment.getApplication(), now::get);
        history.setPaused(false);
        workspace = new ReBrowserStore.Workspace("a".repeat(32), "Test", ReBrowserStore.Level.TEMPORARY);
        workspace.tabs.add(new ReBrowserStore.Tab("b".repeat(32), "Current", "https://example.com/current"));
        workspace.activeTabId = workspace.tabs.get(0).id;
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        view = new ReBrowserHistoryView(activity, history, () -> {}, opened::add, paused -> {
            pauses.add(paused);
            if (!paused) history.syncCurrent(List.of(workspace), tab -> null, tab -> null);
        });
        activity.setContentView(view);
    }
    @After public void finish() { history.setPaused(false); history.close(); }

    private ImageButton button(int type) {
        LinearLayout header = (LinearLayout) view.getChildAt(0);
        return (ImageButton) header.getChildAt(2 + type);
    }
    private LinearLayout row() {
        ListView list = (ListView) view.getChildAt(1);
        return (LinearLayout) list.getAdapter().getView(0, null, list);
    }
    private long record() {
        return history.record(workspace, workspace.activeTab(), workspace.activeTab().url, "Current");
    }

    @Test public void maintainAndPauseHaveDistinctStatesAndClearIsDisabledDuringMaintain() {
        record(); view.refresh();
        assertFalse(button(0).isSelected()); assertFalse(button(2).isSelected()); assertTrue(button(1).isEnabled());
        button(0).performClick(); assertTrue(history.maintaining()); assertTrue(button(0).isSelected());
        assertFalse(button(1).isEnabled()); button(1).performClick(); assertEquals(1, history.entries(0, 10).size());
        button(0).performClick(); assertFalse(history.maintaining()); assertTrue(button(1).isEnabled());
        button(2).performClick(); assertTrue(history.paused()); assertTrue(button(2).isSelected());
        button(2).performClick(); assertFalse(history.paused()); assertFalse(button(2).isSelected());
        assertEquals(List.of(true, false), pauses); assertEquals(2, history.entries(0, 10).size());
    }

    @Test public void clearWorksWhilePausedAndDoesNotDeleteDurableHistory() {
        record(); workspace.level = ReBrowserStore.Level.SECONDARY;
        history.record(workspace, workspace.activeTab(), "https://example.com/durable", "Durable");
        view.refresh(); button(2).performClick(); button(1).performClick();
        assertEquals(1, history.entries(0, 10).size()); assertNull(history.entries(0, 10).get(0).expiresAt);
    }

    @Test public void individualDeletionIsAllowedWhileMaintaining() {
        long id = record(); view.refresh(); button(0).performClick(); row().getChildAt(1).performClick();
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog(); assertNotNull(dialog);
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        assertNull(history.find(id));
    }

    @Test public void openingOnlyPassesUrlAndCannotOpenAnExpiredStaleRow() {
        record(); view.refresh(); View labels = row().getChildAt(0);
        labels.performClick(); assertEquals(List.of("https://example.com/current"), opened);
        opened.clear(); now.addAndGet(ReBrowserHistory.RETENTION_MS);
        labels.performClick(); assertTrue(opened.isEmpty());
    }
}
