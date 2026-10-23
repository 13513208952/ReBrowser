package io.github.rebrowser.ui;

import static org.junit.Assert.*;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

/** Actual bounded page/frame reducer only: no Android View, database query or navigation. */
public final class HistoryAdapterTest {
    private static HistoryAdapter.Row row(Object token, String title) {
        return new HistoryAdapter.Row(token, title, "https://example.test/" + title, "2026-10-20 12:00");
    }
    private static List<HistoryAdapter.Row> page(int count) {
        List<HistoryAdapter.Row> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) rows.add(row(new Object(), "记录" + i));
        return rows;
    }
    @Test public void growingVirtualizedListIsBoundedWithoutHundredRowTruncation(){
        HistoryAdapter.State state = new HistoryAdapter.State();
        assertNotNull(state.show(1, page(HistoryAdapter.MAX_ROWS)));
        assertEquals(HistoryAdapter.MAX_ROWS, state.rowCount());
        assertThrows(IllegalArgumentException.class, () -> state.show(2, page(HistoryAdapter.MAX_ROWS+1)));
        assertThrows(IllegalArgumentException.class, () -> state.show(3, null));
        assertThrows(IllegalArgumentException.class, () -> state.show(0, page(1)));
        assertEquals(HistoryAdapter.MAX_ROWS, state.rowCount());
    }
    @Test public void shownPageIsDefensivelyCopiedAndImmutable(){
        HistoryAdapter.State state = new HistoryAdapter.State();
        List<HistoryAdapter.Row> input = page(2);
        HistoryAdapter.Row first = input.get(0);
        state.show(7, input);
        input.clear();
        assertEquals(2, state.rowCount());
        assertEquals(first, state.rows().get(0));
        assertThrows(UnsupportedOperationException.class, () -> state.rows().clear());
        assertSame(first, state.require(state.frame(), 7, first.itemRef()));
    }
    @Test public void duplicateRowTokenIsRejectedButDistinctEqualRowsAreAllowed(){
        HistoryAdapter.State state = new HistoryAdapter.State();
        Object token = new Object();
        assertThrows(IllegalArgumentException.class, () -> state.show(1, Arrays.asList(row(token, "a"), row(token, "b"))));
        assertThrows(IllegalArgumentException.class, () -> state.show(1, Arrays.asList(row(new Object(), "a"), null)));
        state.show(2, Arrays.asList(row(new Object(), "a"), row(new Object(), "b")));
        assertEquals(2, state.rowCount());
    }
    @Test public void titleUrlAndVisitedTextAreBoundedAndUtf16Strict(){
        Object token = new Object();
        assertEquals("", new HistoryAdapter.Row(token, "", "", "").title());
        assertThrows(IllegalArgumentException.class, () -> new HistoryAdapter.Row(null, "t", "", ""));
        assertThrows(IllegalArgumentException.class, () -> new HistoryAdapter.Row(token, null, "", ""));
        assertThrows(IllegalArgumentException.class, () -> new HistoryAdapter.Row(token, "x".repeat(4097), "", ""));
        assertThrows(IllegalArgumentException.class, () -> new HistoryAdapter.Row(token, "t", "u".repeat(8193), ""));
        assertThrows(IllegalArgumentException.class, () -> new HistoryAdapter.Row(token, "t", "", "v".repeat(HistoryAdapter.MAX_VISITED_TEXT_CHARS+1)));
        for (String broken : new String[]{"\ud800", "\udc00", "line\nbreak", "nul\u0000char"}) {
            assertThrows(IllegalArgumentException.class, () -> new HistoryAdapter.Row(token, broken, "", ""));
            assertThrows(IllegalArgumentException.class, () -> new HistoryAdapter.Row(token, "t", broken, ""));
            assertThrows(IllegalArgumentException.class, () -> new HistoryAdapter.Row(token, "t", "", broken));
        }
        assertEquals("标题😀", new HistoryAdapter.Row(token, "标题😀", "", "").title());
    }
    @Test public void newPageInvalidatesCapturedFrameAndNeverRetargetsNewestRow(){
        HistoryAdapter.State state = new HistoryAdapter.State();
        List<HistoryAdapter.Row> firstPage = page(1);
        state.show(3, firstPage);
        Object firstFrame = state.frame();
        Object firstToken = firstPage.get(0).itemRef();
        List<HistoryAdapter.Row> secondPage = page(1);
        state.show(4, secondPage);
        assertFalse(state.current(firstFrame, 3));
        assertThrows(IllegalStateException.class, () -> state.require(firstFrame, 3, firstToken));
        assertSame(secondPage.get(0), state.require(state.frame(), 4, secondPage.get(0).itemRef()));
    }
    @Test public void revisionMismatchAndUnknownTokenCannotSelectAnotherRow(){
        HistoryAdapter.State state = new HistoryAdapter.State();
        List<HistoryAdapter.Row> rows = page(2);
        state.show(5, rows);
        Object frame = state.frame();
        assertFalse(state.current(frame, 6));
        assertThrows(IllegalStateException.class, () -> state.require(frame, 6, rows.get(0).itemRef()));
        assertThrows(IllegalArgumentException.class, () -> state.require(frame, 5, new Object()));
    }
    @Test public void hideDropsPrivateRowsAndFrameWithoutBecomingRetired(){
        HistoryAdapter.State state = new HistoryAdapter.State();
        state.show(1, page(3));
        Object frame = state.frame();
        state.hide();
        assertEquals(0, state.rowCount());
        assertTrue(state.rows().isEmpty());
        assertNull(state.rowAt(0));
        assertFalse(state.current(frame, 1));
        state.show(2, page(1));
        assertEquals(1, state.rowCount());
    }
    @Test public void retireIsOneWayAndCannotReviveOldOrNewPage(){
        HistoryAdapter.State state = new HistoryAdapter.State();
        state.show(1, page(2));
        state.retire();
        state.retire();
        assertEquals(0, state.rowCount());
        assertTrue(state.rows().isEmpty());
        assertThrows(IllegalStateException.class, () -> state.show(2, page(1)));
    }
    @Test public void outOfRangePositionsReturnNullInsteadOfGuessing(){
        HistoryAdapter.State state = new HistoryAdapter.State();
        state.show(1, page(1));
        assertNull(state.rowAt(-1));
        assertNull(state.rowAt(1));
        assertNotNull(state.rowAt(0));
    }
    @Test public void foreignThreadCannotReadOrMutatePage() throws Exception {
        HistoryAdapter.State state = new HistoryAdapter.State();
        state.show(1, page(1));
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            assertTrue(pool.submit(() -> {
                try { state.rows(); return false; } catch (IllegalStateException expected) { return true; }
            }).get(3, TimeUnit.SECONDS));
            assertTrue(pool.submit(() -> {
                try { state.show(2, page(1)); return false; } catch (IllegalStateException expected) { return true; }
            }).get(3, TimeUnit.SECONDS));
        } finally {
            pool.shutdown();
            assertTrue(pool.awaitTermination(3, TimeUnit.SECONDS));
        }
        assertEquals(1, state.rowCount());
    }
    @Test public void detailUsesDisplayTextOnlyAndLabelIsTitle(){
        HistoryAdapter.Row full = new HistoryAdapter.Row(new Object(), "标题", "https://example.test/", "12:00");
        assertEquals("标题", HistoryAdapter.Rows.label(full));
        assertEquals("https://example.test/ · 12:00", HistoryAdapter.Rows.detail(full));
        assertEquals("12:00", HistoryAdapter.Rows.detail(new HistoryAdapter.Row(new Object(), "t", "", "12:00")));
        assertEquals("https://example.test/", HistoryAdapter.Rows.detail(new HistoryAdapter.Row(new Object(), "t", "https://example.test/", "")));
        assertEquals("", HistoryAdapter.Rows.detail(new HistoryAdapter.Row(new Object(), "t", "", "")));
        assertThrows(IllegalArgumentException.class, () -> HistoryAdapter.Rows.validate(null, 1));
    }
    @Test public void adapterExposesNoQueryDeleteOrNavigationOperation(){
        for (Method method : HistoryAdapter.class.getDeclaredMethods()) {
            String name = method.getName().toLowerCase();
            for (String forbidden : new String[]{"load", "delete", "insert", "query", "openurl", "save", "persist"}) {
                assertFalse("unexpected operation " + method.getName(), name.contains(forbidden));
            }
        }
        for (Method method : HistoryAdapter.Actions.class.getDeclaredMethods()) {
            assertTrue(java.lang.reflect.Modifier.isAbstract(method.getModifiers()));
            assertEquals(2, method.getParameterCount());
        }
    }
}
