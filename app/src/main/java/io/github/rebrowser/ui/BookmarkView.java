package io.github.rebrowser.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bounded native bookmark/folder presentation. It owns NO store, saves/deletes nothing,
 * shows no dialog and performs NO navigation: open/add/edit/remove are user INTENTS with the
 * exact captured row token and page revision. displayUrl is text only, never a load target.
 */
public final class BookmarkView extends ScrollView {
    public static final int MAX_ROWS = 100;
    public static final int MAX_TITLE_CHARS = 4096;
    public static final int MAX_URL_CHARS = 8192;

    public enum Kind { FOLDER, BOOKMARK }

    public interface Actions {
        void open(Row row, long expectedRevision);
        void add(boolean folder, long expectedRevision);
        void edit(Row row, long expectedRevision);
        void remove(Row row, long expectedRevision);
    }

    /** Immutable row. FOLDER keeps an empty displayUrl; BOOKMARK requires display text. */
    public static final class Row {
        private final Object itemRef;
        private final String title, displayUrl;
        private final Kind kind;
        public Row(Object itemRef, String title, String displayUrl, Kind kind) {
            if (itemRef == null || kind == null) throw invalid();
            this.itemRef = itemRef;
            this.title = text(title, MAX_TITLE_CHARS, false);
            this.displayUrl = text(displayUrl == null ? "" : displayUrl, MAX_URL_CHARS, true);
            if (kind == Kind.FOLDER && !this.displayUrl.isEmpty()) throw invalid();
            if (kind == Kind.BOOKMARK && this.displayUrl.isEmpty()) throw invalid();
            this.kind = kind;
        }
        public Object itemRef() { return itemRef; }
        public String title() { return title; }
        public String displayUrl() { return displayUrl; }
        public Kind kind() { return kind; }
    }

    public static final class Rows {
        public static void validate(List<Row> rows, long pageRevision) {
            if (pageRevision < 1) throw new IllegalArgumentException("Invalid native bookmark page revision");
            if (rows == null || rows.size() > MAX_ROWS) throw new IllegalArgumentException("Invalid native bookmark page size");
            Map<Object, Row> seen = new IdentityHashMap<>();
            for (Row row : rows) {
                if (row == null) throw new IllegalArgumentException("Missing native bookmark row");
                if (seen.put(row.itemRef(), row) != null) throw new IllegalArgumentException("Duplicate native bookmark row token");
            }
        }
        public static String label(Row row) {
            if (row == null) throw invalid();
            return (row.kind == Kind.FOLDER ? "文件夹 · " : "书签 · ") + row.title;
        }
        public static String description(Row row) {
            if (row == null) throw invalid();
            return row.kind == Kind.FOLDER ? "文件夹，可展开" : row.displayUrl;
        }
    }

    /** Owner-thread page/frame reducer; captured frames never retarget a newer page. */
    public static final class State {
        private final Thread owner = Thread.currentThread();
        private long revision;
        private List<Row> rows = Collections.emptyList();
        private Object frame;
        private boolean retired;
        public Object show(long pageRevision, List<Row> next) {
            check();
            if (retired) throw new IllegalStateException("Retired native list cannot show a new page");
            Rows.validate(next, pageRevision);
            revision = pageRevision;
            rows = Collections.unmodifiableList(new ArrayList<>(next));
            frame = new Object();
            return frame;
        }
        public void hide() { check(); rows = Collections.emptyList(); frame = null; }
        public void retire() { check(); if (retired) return; retired = true; rows = Collections.emptyList(); frame = null; }
        public Object frame() { check(); return frame; }
        public long revision() { check(); return revision; }
        public int rowCount() { check(); return retired ? 0 : rows.size(); }
        public boolean current(Object expectedFrame, long expectedRevision) {
            check();
            return !retired && expectedFrame != null && expectedFrame == frame && expectedRevision == revision;
        }
        public List<Row> rows() { check(); return rows; }
        public Row rowAt(int position) {
            check();
            if (retired || position < 0 || position >= rows.size()) return null;
            return rows.get(position);
        }
        public Row require(Object expectedFrame, long expectedRevision, Object itemRef) {
            check();
            if (!current(expectedFrame, expectedRevision)) throw new IllegalStateException("Stale native bookmark frame");
            for (Row row : rows) if (row.itemRef() == itemRef) return row;
            throw new IllegalArgumentException("Unknown native bookmark row token");
        }
        private void check() { if (Thread.currentThread() != owner) throw new IllegalStateException("Bookmark view owner thread required"); }
    }

    private final LinearLayout rows;
    private final State state = new State();
    private BrowserToolbar.Binding binding;
    private Actions actions;
    private Object frame;

    public BookmarkView(Context context) {
        super(context);
        BrowserToolbar.main();
        rows = new LinearLayout(context);
        rows.setOrientation(LinearLayout.VERTICAL);
        addView(rows, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    public void bind(BrowserToolbar.Binding binding, long pageRevision, List<Row> page, Actions actions) {
        BrowserToolbar.main();
        if (binding == null || actions == null) throw invalid();
        binding.check();
        state.show(pageRevision, page);
        unbindRows();
        this.binding = binding;
        this.actions = actions;
        final Object current = state.frame();
        final long revision = pageRevision;
        frame = current;
        Button newBookmark = button("新建书签");
        newBookmark.setOnClickListener(v -> runAdd(current, revision, false));
        Button newFolder = button("新建文件夹");
        newFolder.setOnClickListener(v -> runAdd(current, revision, true));
        rows.addView(newBookmark);
        rows.addView(newFolder);
        for (Row row : state.rows()) {
            LinearLayout line = new LinearLayout(getContext());
            line.setOrientation(LinearLayout.HORIZONTAL);
            Button open = button(Rows.label(row));
            open.setMinimumWidth(dp(160));
            open.setContentDescription(row.kind() == Kind.FOLDER ? "展开文件夹：" + row.title() : "打开书签：" + row.title());
            open.setOnClickListener(v -> run(current, revision, row.itemRef(), (callback, exact) -> callback.open(exact, revision)));
            line.addView(open, new LinearLayout.LayoutParams(0, dp(48), 1));
            Button edit = button("编辑");
            edit.setContentDescription("编辑：" + row.title());
            edit.setOnClickListener(v -> run(current, revision, row.itemRef(), (callback, exact) -> callback.edit(exact, revision)));
            line.addView(edit, new LinearLayout.LayoutParams(dp(72), dp(48)));
            Button remove = button("移除");
            remove.setContentDescription("移除：" + row.title());
            remove.setOnClickListener(v -> run(current, revision, row.itemRef(), (callback, exact) -> callback.remove(exact, revision)));
            line.addView(remove, new LinearLayout.LayoutParams(dp(72), dp(48)));
            rows.addView(line);
        }
    }

    /** Drops every private text/row/listener; the page must be read again before reuse. */
    public void unbind() {
        BrowserToolbar.main();
        binding = null;
        actions = null;
        state.hide();
        unbindRows();
    }

    public void retire() {
        BrowserToolbar.main();
        binding = null;
        actions = null;
        state.retire();
        unbindRows();
    }

    private void unbindRows() {
        frame = null;
        for(int i=0;i<rows.getChildCount();i++)clearOwnedView(rows.getChildAt(i));
        rows.removeAllViews();
        setContentDescription(null);
    }

    private static void clearOwnedView(View view){view.setOnClickListener(null);view.setContentDescription(null);if(view instanceof TextView)((TextView)view).setText("");if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)clearOwnedView(group.getChildAt(i));}}
    private void runAdd(Object expected, long expectedRevision, boolean folder) {
        BrowserToolbar.Binding captured = binding;
        Actions callback = actions;
        if (captured == null || callback == null || frame != expected) return;
        try {
            captured.check();
            if (frame != expected || captured != binding || callback != actions) return;
            if (!state.current(expected, expectedRevision)) return;
            BrowserToolbar.IntentOutcome outcome=BrowserToolbar.invokeNativeIntent(()->callback.add(folder,expectedRevision));
            if(outcome==BrowserToolbar.IntentOutcome.CALLBACK_UNKNOWN && binding==captured && actions==callback && frame==expected)setContentDescription("操作结果未知，请重新读取收藏状态");
        } catch (SecurityException staleSource) {
            unbind();
        } catch (IllegalStateException | IllegalArgumentException staleFrame) {
            // Page gone: no action and no retry.
        } catch (RuntimeException unknown) {
            setContentDescription("操作结果未知，请重新读取收藏状态");
        }
    }

    private void run(Object expected, long expectedRevision, Object itemRef, java.util.function.BiConsumer<Actions, Row> action) {
        BrowserToolbar.Binding captured = binding;
        Actions callback = actions;
        if (captured == null || callback == null || frame != expected) return;
        try {
            captured.check();
            if (frame != expected || captured != binding || callback != actions) return;
            Row row = state.require(expected, expectedRevision, itemRef);
            BrowserToolbar.IntentOutcome outcome=BrowserToolbar.invokeNativeIntent(()->action.accept(callback,row));
            if(outcome==BrowserToolbar.IntentOutcome.CALLBACK_UNKNOWN && binding==captured && actions==callback && frame==expected)setContentDescription("操作结果未知，请重新读取收藏状态");
        } catch (SecurityException staleSource) {
            unbind();
        } catch (IllegalStateException | IllegalArgumentException staleFrame) {
            // Row/frame gone: no action, no retry and no newest-row fallback.
        } catch (RuntimeException unknown) {
            setContentDescription("操作结果未知，请重新读取收藏状态");
        }
    }

    @Override protected void onDetachedFromWindow() {
        unbind();
        super.onDetachedFromWindow();
    }

    private Button button(String label) {
        Button button = new Button(getContext());
        button.setText(label);
        button.setMinimumHeight(dp(48));
        return button;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private static String text(String value, int maximum, boolean allowEmpty) {
        if (value == null || value.length() > maximum || (!allowEmpty && value.isEmpty())) throw invalid();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isISOControl(c)) throw invalid();
            if (Character.isHighSurrogate(c)) {
                if (++i >= value.length() || !Character.isLowSurrogate(value.charAt(i))) throw invalid();
            } else if (Character.isLowSurrogate(c)) throw invalid();
        }
        return value;
    }

    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid native bookmark row/page"); }
}
