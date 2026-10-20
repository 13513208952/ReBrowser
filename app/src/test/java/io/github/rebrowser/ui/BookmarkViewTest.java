package io.github.rebrowser.ui;

import static org.junit.Assert.*;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

/** Actual bounded bookmark/folder presentation model: no store, dialog, navigation or delete. */
public final class BookmarkViewTest {
    private static BookmarkView.Row bookmark(Object token, String title, String url) {
        return new BookmarkView.Row(token, title, url, BookmarkView.Kind.BOOKMARK);
    }
    private static BookmarkView.Row folder(Object token, String title) {
        return new BookmarkView.Row(token, title, "", BookmarkView.Kind.FOLDER);
    }
    private static List<BookmarkView.Row> page(int count) {
        List<BookmarkView.Row> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) rows.add(bookmark(new Object(), "收藏" + i, "https://example.test/" + i));
        return rows;
    }
    @Test public void folderAndBookmarkKindsAreLabelledDistinctly(){
        BookmarkView.Row folder = folder(new Object(), "工作");
        BookmarkView.Row bookmark = bookmark(new Object(), "示例", "https://example.test/");
        assertEquals("文件夹 · 工作", BookmarkView.Rows.label(folder));
        assertEquals("书签 · 示例", BookmarkView.Rows.label(bookmark));
        assertEquals("文件夹，可展开", BookmarkView.Rows.description(folder));
        assertEquals("https://example.test/", BookmarkView.Rows.description(bookmark));
        assertNotEquals(BookmarkView.Rows.label(folder), BookmarkView.Rows.label(bookmark));
    }
    @Test public void kindSpecificUrlRulesAndBoundsAreEnforced(){
        Object token = new Object();
        assertThrows(IllegalArgumentException.class, () -> new BookmarkView.Row(token, "文件夹", "https://example.test/", BookmarkView.Kind.FOLDER));
        assertThrows(IllegalArgumentException.class, () -> new BookmarkView.Row(token, "书签", "", BookmarkView.Kind.BOOKMARK));
        assertThrows(IllegalArgumentException.class, () -> new BookmarkView.Row(token, "书签", null, BookmarkView.Kind.BOOKMARK));
        assertThrows(IllegalArgumentException.class, () -> new BookmarkView.Row(null, "t", "u", BookmarkView.Kind.BOOKMARK));
        assertThrows(IllegalArgumentException.class, () -> new BookmarkView.Row(token, "t", "u", null));
        assertThrows(IllegalArgumentException.class, () -> new BookmarkView.Row(token, "", "u", BookmarkView.Kind.BOOKMARK));
        assertThrows(IllegalArgumentException.class, () -> new BookmarkView.Row(token, "x".repeat(4097), "u", BookmarkView.Kind.BOOKMARK));
        assertThrows(IllegalArgumentException.class, () -> new BookmarkView.Row(token, "t", "u".repeat(8193), BookmarkView.Kind.BOOKMARK));
        for (String broken : new String[]{"\ud800", "\udc00", "line\nbreak", "nul\u0000"}) {
            assertThrows(IllegalArgumentException.class, () -> new BookmarkView.Row(token, broken, "u", BookmarkView.Kind.BOOKMARK));
            assertThrows(IllegalArgumentException.class, () -> new BookmarkView.Row(token, "t", broken, BookmarkView.Kind.BOOKMARK));
        }
        assertEquals("标题😀", new BookmarkView.Row(token, "标题😀", "https://example.test/", BookmarkView.Kind.BOOKMARK).title());
    }
    @Test public void pageIsBoundedToOneHundredRowsWithPositiveRevision(){
        BookmarkView.State state = new BookmarkView.State();
        assertNotNull(state.show(1, page(100)));
        assertEquals(100, state.rowCount());
        assertThrows(IllegalArgumentException.class, () -> state.show(2, page(101)));
        assertThrows(IllegalArgumentException.class, () -> state.show(3, null));
        assertThrows(IllegalArgumentException.class, () -> state.show(0, page(1)));
        assertEquals(100, state.rowCount());
    }
    @Test public void duplicateTokensAndNullRowsAreRejectedButDistinctRowsAreAllowed(){
        BookmarkView.State state = new BookmarkView.State();
        Object token = new Object();
        assertThrows(IllegalArgumentException.class, () -> state.show(1, Arrays.asList(folder(token, "a"), folder(token, "b"))));
        assertThrows(IllegalArgumentException.class, () -> state.show(1, Arrays.asList(folder(new Object(), "a"), null)));
        state.show(2, Arrays.asList(folder(new Object(), "a"), bookmark(new Object(), "b", "https://example.test/")));
        assertEquals(2, state.rowCount());
    }
    @Test public void pageIsDefensivelyCopiedImmutableAndStillCurrentAfterShow(){
        BookmarkView.State state = new BookmarkView.State();
        List<BookmarkView.Row> input = page(2);
        BookmarkView.Row first = input.get(0);
        Object frame = state.show(5, input);
        input.clear();
        assertEquals(2, state.rowCount());
        assertSame(first, state.rows().get(0));
        assertSame(first, state.require(frame, 5, first.itemRef()));
        assertThrows(UnsupportedOperationException.class, () -> state.rows().clear());
    }
    @Test public void rebindInvalidatesOldFrameAndNeverRetargetsLatestRow(){
        BookmarkView.State state = new BookmarkView.State();
        BookmarkView.Row old = folder(new Object(), "旧");
        state.show(1, Arrays.asList(old));
        Object oldFrame = state.frame();
        BookmarkView.Row latest = bookmark(new Object(), "新", "https://example.test/");
        state.show(2, Arrays.asList(latest));
        assertFalse(state.current(oldFrame, 1));
        assertThrows(IllegalStateException.class, () -> state.require(oldFrame, 1, old.itemRef()));
        assertSame(latest, state.require(state.frame(), 2, latest.itemRef()));
    }
    @Test public void revisionMismatchUnknownTokenAndOutOfRangeAreNotGuessed(){
        BookmarkView.State state = new BookmarkView.State();
        BookmarkView.Row row = folder(new Object(), "工作");
        state.show(3, Arrays.asList(row));
        Object frame = state.frame();
        assertFalse(state.current(frame, 4));
        assertThrows(IllegalStateException.class, () -> state.require(frame, 4, row.itemRef()));
        assertThrows(IllegalArgumentException.class, () -> state.require(frame, 3, new Object()));
        assertNull(state.rowAt(-1));
        assertNull(state.rowAt(1));
        assertNotNull(state.rowAt(0));
    }
    @Test public void hideDropsPrivateRowsAndFrameWithoutRetiring(){
        BookmarkView.State state = new BookmarkView.State();
        state.show(1, page(2));
        Object frame = state.frame();
        state.hide();
        assertEquals(0, state.rowCount());
        assertTrue(state.rows().isEmpty());
        assertFalse(state.current(frame, 1));
        state.show(2, page(1));
        assertEquals(1, state.rowCount());
    }
    @Test public void retireIsOneWayAndCannotRevivePage(){
        BookmarkView.State state = new BookmarkView.State();
        state.show(1, page(1));
        state.retire();
        state.retire();
        assertEquals(0, state.rowCount());
        assertThrows(IllegalStateException.class, () -> state.show(2, page(1)));
    }
    @Test public void foreignThreadCannotReadOrMutatePage() throws Exception {
        BookmarkView.State state = new BookmarkView.State();
        state.show(1, page(1));
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            assertTrue(pool.submit(() -> { try { state.rows(); return false; } catch (IllegalStateException expected) { return true; } }).get(3, TimeUnit.SECONDS));
            assertTrue(pool.submit(() -> { try { state.show(2, page(1)); return false; } catch (IllegalStateException expected) { return true; } }).get(3, TimeUnit.SECONDS));
        } finally {
            pool.shutdown();
            assertTrue(pool.awaitTermination(3, TimeUnit.SECONDS));
        }
        assertEquals(1, state.rowCount());
    }
    @Test public void intentsCannotMutateTheDisplayedPageByThemselves(){
        BookmarkView.State state = new BookmarkView.State();
        List<BookmarkView.Row> rows = page(2);
        state.show(1, rows);
        long revision = state.revision();
        BookmarkView.Row exact = state.require(state.frame(), revision, rows.get(0).itemRef());
        assertEquals(rows.get(0), exact);
        // Read-only model: resolving the token and revision never edits/removes a row.
        assertEquals(revision, state.revision());
        assertEquals(2, state.rowCount());
        assertSame(rows.get(0), state.rowAt(0));
    }
    @Test public void viewAndActionsExposeNoSaveDeleteDialogOrNavigationOperation(){
        for (Method method : BookmarkView.class.getDeclaredMethods()) {
            String name = method.getName().toLowerCase();
            for (String forbidden : new String[]{"save", "delete", "persist", "dialog", "load", "confirm", "authorize", "trust"}) {
                assertFalse("unexpected operation " + method.getName(), name.contains(forbidden));
            }
        }
        assertEquals(4, BookmarkView.Actions.class.getDeclaredMethods().length);
        for (Method method : BookmarkView.Actions.class.getDeclaredMethods()) {
            assertTrue(Modifier.isAbstract(method.getModifiers()));
            assertEquals(2, method.getParameterCount());
        }
        assertEquals(2, BookmarkView.Kind.values().length);
    }
}
