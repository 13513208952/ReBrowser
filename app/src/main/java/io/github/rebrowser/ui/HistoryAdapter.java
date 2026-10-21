package io.github.rebrowser.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Bounded ONE-PAGE native history list. This adapter NEVER queries the database, never
 * loads a URL and never deletes a record: clicks only return the exact captured row token,
 * page revision and user intent to the owning window, which must re-authorize and re-read.
 * A Row token is a process-local UI handoff, NOT an account/grant/文件 capability.
 * Retain/remove are ONE-SHOT per exact frame+row: after an attempt the button stays disabled
 * until the native owner supplies a new page, even when the callback outcome is unknown.
 * OPEN has a separate policy: navigation may be requested again (the owner re-authorizes).
 */
public final class HistoryAdapter extends BaseAdapter {
    public static final int MAX_ROWS = 100;
    public static final int MAX_TITLE_CHARS = 4096;
    public static final int MAX_URL_CHARS = 8192;
    public static final int MAX_VISITED_TEXT_CHARS = 64;
    private static final int OPEN = 0, RETAIN = 1, REMOVE = 2;
    /** Display-only capability reported by the native owner; it is not a grant to delete. */
    public enum Offered { OPEN, RETAIN, REMOVE }
    /** UI gate state: only AVAILABLE may be clicked; ATTEMPTED waits for a fresh native page. */
    public enum ActionState { AVAILABLE, NOT_OFFERED, ATTEMPTED, UNAVAILABLE }

    public interface Actions {
        void open(Row row, long expectedRevision);
        void retain(Row row, long expectedRevision);
        void remove(Row row, long expectedRevision);
    }

    /** Immutable display row. displayUrl is display text only, never a load target. */
    public static final class Row {
        private final Object itemRef;
        private final String title, displayUrl, visitedText, unavailableReason;
        private final Set<Offered> offered;
        public Row(Object itemRef, String title, String displayUrl, String visitedText) {
            this(itemRef, title, displayUrl, visitedText, EnumSet.of(Offered.OPEN), "原生未提供保留或删除操作");
        }
        /** A missing/empty offered set never enables open/retain/remove by default. */
        public Row(Object itemRef, String title, String displayUrl, String visitedText, Set<Offered> offered, String unavailableReason) {
            if (itemRef == null) throw Checks.invalid();
            this.itemRef = itemRef;
            this.title = Checks.text(title, MAX_TITLE_CHARS);
            this.displayUrl = Checks.text(displayUrl, MAX_URL_CHARS);
            this.visitedText = Checks.text(visitedText, MAX_VISITED_TEXT_CHARS);
            if (offered == null) throw Checks.invalid();
            EnumSet<Offered> copy = EnumSet.noneOf(Offered.class);
            copy.addAll(offered);
            this.offered = Collections.unmodifiableSet(copy);
            this.unavailableReason = unavailableReason == null ? "未提供可用原因" : text(unavailableReason, 96);
        }
        public Object itemRef() { return itemRef; }
        public String title() { return title; }
        public String displayUrl() { return displayUrl; }
        /** Pre-formatted display text supplied by the query owner; never parsed as authority. */
        public String visitedText() { return visitedText; }
        public Set<Offered> offered(){return offered;}
        public boolean offers(Offered action){return offered.contains(action);}
        public String unavailableReason(){return unavailableReason;}
    }

    /** Nested pure checks so Rows/State/Row never resolve the Android outer class at HOST runtime. */
    static final class Checks {
        static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid native list row/page");}
        static String text(String value,int maximum){
            if(value==null || value.length()>maximum)throw Checks.invalid();
            for(int i=0;i<value.length();i++){char c=value.charAt(i);if(Character.isISOControl(c))throw Checks.invalid();if(Character.isHighSurrogate(c)){if(++i>=value.length() || !Character.isLowSurrogate(value.charAt(i)))throw Checks.invalid();}else if(Character.isLowSurrogate(c))throw Checks.invalid();}
            return value;
        }
    }
    public static final class Rows {
        public static void validate(List<Row> rows, long pageRevision) {
            if (pageRevision < 1) throw new IllegalArgumentException("Invalid native history page revision");
            if (rows == null || rows.size() > MAX_ROWS) throw new IllegalArgumentException("Invalid native history page size");
            Map<Object, Row> seen = new IdentityHashMap<>();
            for (Row row : rows) {
                if (row == null) throw new IllegalArgumentException("Missing native history row");
                if (seen.put(row.itemRef(), row) != null) throw new IllegalArgumentException("Duplicate native history row token");
            }
        }
        public static String label(Row row) { if (row == null) throw Checks.invalid(); return row.title(); }
        public static ActionState actionState(Row row, Offered action, boolean attempted) {
            if (row == null || action == null) throw Checks.invalid();
            if (!row.offers(action)) return ActionState.NOT_OFFERED;
            if (attempted && action != Offered.OPEN) return ActionState.ATTEMPTED;
            return ActionState.AVAILABLE;
        }
        public static String actionReason(Row row, Offered action, ActionState state) {
            if (row == null || action == null || state == null) throw Checks.invalid();
            switch (state) {
                case AVAILABLE: return actionLabel(action) + "：“" + row.title() + "”";
                case NOT_OFFERED: return row.unavailableReason();
                case ATTEMPTED: return "已请求“" + row.title() + "”，等待原生重新读取";
                default: return "当前不可用";
            }
        }
        public static String actionLabel(Offered action) {
            switch (action) { case OPEN: return "打开历史记录"; case RETAIN: return "保留历史记录"; default: return "删除历史记录"; }
        }
        public static String detail(Row row) {
            if (row == null) throw Checks.invalid();
            if (row.displayUrl().isEmpty()) return row.visitedText();
            if (row.visitedText().isEmpty()) return row.displayUrl();
            return row.displayUrl() + " · " + row.visitedText();
        }
    }

    /** Owner-thread page/frame reducer. Captured frames never mutate a newer page. */
    public static final class State {
        private final Thread owner = Thread.currentThread();
        private long revision;
        private List<Row> rows = Collections.emptyList();
        private Object frame;
        private boolean retired;
        private final List<Attempt> attempts = new ArrayList<>();
        private static final int MAX_ATTEMPTS = MAX_ROWS * 3;
        private static final class Attempt {
            final Object frame, itemRef; final long revision; final Offered action;
            Attempt(Object frame,long revision,Object itemRef,Offered action){this.frame=frame;this.revision=revision;this.itemRef=itemRef;this.action=action;}
        }
        public Object show(long pageRevision, List<Row> next) {
            check();
            if (retired) throw new IllegalStateException("Retired native list cannot show a new page");
            Rows.validate(next, pageRevision);
            revision = pageRevision;
            rows = Collections.unmodifiableList(new ArrayList<>(next));
            frame = new Object();
            attempts.clear(); // a fresh native page re-enables genuinely re-offered actions
            return frame;
        }
        public void hide() { check(); rows = Collections.emptyList(); frame = null; attempts.clear(); }
        public void retire() { check(); if (retired) return; retired = true; rows = Collections.emptyList(); frame = null; attempts.clear(); }
        /** Consume the single UI attempt BEFORE invoking the owner; unknown outcome stays spent. */
        public boolean beginAttempt(Object expectedFrame, long expectedRevision, Object itemRef, Offered action) {
            check();
            if (!current(expectedFrame, expectedRevision) || action == null) return false;
            Row row=null;for(Row candidate:rows)if(candidate.itemRef()==itemRef){row=candidate;break;}
            if(row==null || !row.offers(action))return false;
            if (wasAttempted(expectedFrame, expectedRevision, itemRef, action)) return false;
            if (attempts.size() >= MAX_ATTEMPTS) return false; // bounded latch memory
            attempts.add(new Attempt(expectedFrame, expectedRevision, itemRef, action));
            return true;
        }
        public boolean wasAttempted(Object expectedFrame, long expectedRevision, Object itemRef, Offered action) {
            check();
            for (Attempt attempt : attempts)
                if (attempt.frame == expectedFrame && attempt.revision == expectedRevision
                        && attempt.itemRef == itemRef && attempt.action == action) return true;
            return false;
        }
        public Object frame() { check(); return frame; }
        public long revision() { check(); return revision; }
        public int rowCount() { check(); return retired ? 0 : rows.size(); }
        public boolean current(Object expectedFrame, long expectedRevision) {
            check();
            return !retired && expectedFrame != null && expectedFrame == frame && expectedRevision == revision;
        }
        /** Immutable page copy; empty after hide/retire. */
        public List<Row> rows() { check(); return rows; }
        public Row rowAt(int position) {
            check();
            if (retired || position < 0 || position >= rows.size()) return null;
            return rows.get(position);
        }
        /** Exact identity token for THIS frame/revision; never re-selects a newest row by value. */
        public Row require(Object expectedFrame, long expectedRevision, Object itemRef) {
            check();
            if (!current(expectedFrame, expectedRevision)) throw new IllegalStateException("Stale native history frame");
            for (Row row : rows) if (row.itemRef() == itemRef) return row;
            throw new IllegalArgumentException("Unknown native history row token");
        }
        private void check() { if (Thread.currentThread() != owner) throw new IllegalStateException("History adapter owner thread required"); }
    }

    private final Context context;
    private final State state = new State();
    private BrowserToolbar.Binding binding;
    private Actions actions;
    private String outcomeText;
    private final Object holderIssuer=new Object();
    private final Map<Holder,Boolean> holders=new WeakHashMap<>();

    public HistoryAdapter(Context context) {
        BrowserToolbar.main();
        if (context == null) throw Checks.invalid();
        Context application = context.getApplicationContext();
        this.context = application == null ? context : application;
    }

    public void bind(BrowserToolbar.Binding binding, long pageRevision, List<Row> rows, Actions actions) {
        BrowserToolbar.main();
        if (binding == null || actions == null) throw Checks.invalid();
        binding.check();
        state.show(pageRevision, rows);
        clearHolders();
        this.binding = binding;
        this.actions = actions;
        outcomeText = null;
        notifyDataSetChanged();
    }

    /** UI owner calls this on detach/rebind; it drops the page and every private text. */
    public void unbind() {
        BrowserToolbar.main();
        binding = null;
        actions = null;
        outcomeText = null;
        state.hide();
        clearHolders();
        notifyDataSetChanged();
    }

    public void retire() {
        BrowserToolbar.main();
        binding = null;
        actions = null;
        state.retire();
        outcomeText=null;clearHolders();
        notifyDataSetChanged();
    }

    public String lastOutcomeText() { return outcomeText; }

    @Override public int getCount() { return state.rowCount(); }
    @Override public Row getItem(int position) {
        Row row = state.rowAt(position);
        if (row == null) throw new IndexOutOfBoundsException("Invalid native history position");
        return row;
    }
    @Override public long getItemId(int position) { return position; }
    @Override public boolean hasStableIds() { return false; }

    @Override public View getView(int position, View recycled, ViewGroup parent) {
        BrowserToolbar.main();
        Holder holder;
        if (recycled instanceof LinearLayout && recycled.getTag() instanceof Holder && ((Holder)recycled.getTag()).issuer==holderIssuer) {
            holder = (Holder) recycled.getTag();
        } else {
            holder = create();
        }
        if(!holders.containsKey(holder) && holders.size()>=MAX_ROWS*2)throw new IllegalStateException("Native history view budget reached");
        holders.put(holder,Boolean.TRUE);
        Row row = state.rowAt(position);
        if (row == null) {
            clear(holder);
            return holder.root;
        }
        bindRow(holder, row);
        return holder.root;
    }

    private Holder create() {
        if(holders.size()>=MAX_ROWS*2)throw new IllegalStateException("Native history view budget reached");
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setMinimumHeight(dp(48));
        TextView title = new TextView(context);
        title.setTextSize(16);
        TextView detail = new TextView(context);
        detail.setTextSize(13);
        LinearLayout options = new LinearLayout(context);
        options.setOrientation(LinearLayout.HORIZONTAL);
        Button open = button("打开");
        Button retain = button("保留");
        Button remove = button("删除");
        options.addView(open, new LinearLayout.LayoutParams(0, dp(48), 1));
        options.addView(retain, new LinearLayout.LayoutParams(0, dp(48), 1));
        options.addView(remove, new LinearLayout.LayoutParams(0, dp(48), 1));
        root.addView(title, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(detail, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(options, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        Holder holder = new Holder(holderIssuer,root, title, detail, open, retain, remove);
        root.setTag(holder);
        return holder;
    }

    private void bindRow(Holder holder, Row row) {
        Object frame = state.frame();
        long revision = state.revision();
        holder.title.setText(Rows.label(row));
        holder.detail.setText(Rows.detail(row));
        bindAction(holder.open, row, Offered.OPEN, frame, revision);
        bindAction(holder.retain, row, Offered.RETAIN, frame, revision);
        bindAction(holder.remove, row, Offered.REMOVE, frame, revision);
    }
    private void bindAction(Button button, Row row, Offered action, Object frame, long revision) {
        ActionState actionState = Rows.actionState(row, action, state.wasAttempted(frame, revision, row.itemRef(), action));
        button.setEnabled(actionState == ActionState.AVAILABLE);
        button.setContentDescription(Rows.actionReason(row, action, actionState));
        if (actionState != ActionState.AVAILABLE) { button.setOnClickListener(null); return; }
        int kind = action == Offered.OPEN ? OPEN : action == Offered.RETAIN ? RETAIN : REMOVE;
        button.setOnClickListener(v -> dispatch(frame, revision, row, kind));
    }

    private void clear(Holder holder) {
        holder.title.setText("");
        holder.detail.setText("");
        holder.title.setContentDescription(null);
        holder.detail.setContentDescription(null);
        holder.open.setContentDescription(null);
        holder.retain.setContentDescription(null);
        holder.remove.setContentDescription(null);
        holder.open.setOnClickListener(null);
        holder.retain.setOnClickListener(null);
        holder.remove.setOnClickListener(null);
        holder.open.setEnabled(false);
        holder.retain.setEnabled(false);
        holder.remove.setEnabled(false);
    }

    private void clearHolders(){for(Holder holder:new ArrayList<>(holders.keySet()))if(holder!=null)clear(holder);holders.clear();}

    private void dispatch(Object expectedFrame, long expectedRevision, Row captured, int action) {
        BrowserToolbar.Binding capturedBinding = binding;
        Actions callback = actions;
        if (capturedBinding == null || callback == null) return;
        final Row row;
        try{capturedBinding.check();if(capturedBinding!=binding || callback!=actions)return;row=state.require(expectedFrame,expectedRevision,captured.itemRef());}
        catch(SecurityException staleSource){if(binding==capturedBinding)unbind();return;}
        catch(IllegalStateException|IllegalArgumentException staleFrameOrRow){return;}
        Offered offeredAction=action==OPEN?Offered.OPEN:action==RETAIN?Offered.RETAIN:Offered.REMOVE;
        if(!row.offers(offeredAction))return;
        if(action==RETAIN&&!state.beginAttempt(expectedFrame,expectedRevision,row.itemRef(),Offered.RETAIN))return;
        if(action==REMOVE&&!state.beginAttempt(expectedFrame,expectedRevision,row.itemRef(),Offered.REMOVE))return;
        notifyDataSetChanged();
        BrowserToolbar.IntentOutcome result=BrowserToolbar.invokeNativeIntent(()->{if(action==OPEN)callback.open(row,expectedRevision);else if(action==RETAIN)callback.retain(row,expectedRevision);else callback.remove(row,expectedRevision);});
        if(result==BrowserToolbar.IntentOutcome.CALLBACK_UNKNOWN && binding==capturedBinding && actions==callback && state.current(expectedFrame,expectedRevision)){outcomeText="操作结果未知，请重新读取历史";notifyDataSetChanged();}
    }

    private Button button(String label) {
        Button button = new Button(context);
        button.setText(label);
        button.setMinimumHeight(dp(48));
        button.setMinimumWidth(dp(48));
        return button;
    }

    private int dp(int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }

    private static final class Holder {
        final Object issuer;
        final LinearLayout root;
        final TextView title, detail;
        final Button open, retain, remove;
        Holder(Object issuer,LinearLayout root, TextView title, TextView detail, Button open, Button retain, Button remove) {
            this.issuer=issuer;this.root = root; this.title = title; this.detail = detail;
            this.open = open; this.retain = retain; this.remove = remove;
        }
    }

    private static String text(String value, int maximum) {
        if (value == null || value.length() > maximum) throw Checks.invalid();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isISOControl(c)) throw Checks.invalid();
            if (Character.isHighSurrogate(c)) {
                if (++i >= value.length() || !Character.isLowSurrogate(value.charAt(i))) throw Checks.invalid();
            } else if (Character.isLowSurrogate(c)) throw Checks.invalid();
        }
        return value;
    }

    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid native history row/page"); }
}
