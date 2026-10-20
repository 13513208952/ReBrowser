package io.github.rebrowser.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import io.github.rebrowser.core.TaskSnapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Bounded download-list DISPLAY over existing TaskSnapshot axes. It never starts/停止 a
 * transfer, never touches a stream or file, never publishes and never opens a file or the
 * system installer: clicks return the exact captured row token, page revision and a user
 * INTENT only. Waiting timeout, cancellation request, Java return or an observed effect are
 * kept distinct from actual work end, persistence and publication. Progress never implies
 * completion or that the file is openable.
 */
public final class DownloadsAdapter extends BaseAdapter {
    public static final int MAX_ROWS = 100;
    public static final int MAX_NAME_CHARS = 256;
    public static final long MAX_BYTES = 1L << 40;
    public static final long UNKNOWN_BYTES = -1;
    private static final int CANCEL = 0, HANDOFF = 1, APPROVE = 2;

    /** Faithful projection vocabulary; NOT a new task protocol and NOT a publication state. */
    public enum Terminal {
        NOT_DISPATCHED, WORK_RUNNING_OR_UNKNOWN, EFFECT_OBSERVED_WORK_UNSETTLED,
        FINISHED_OBSERVED_SUCCESS, FINISHED_OBSERVED_FAILURE, ENDED_BEFORE_DISPATCH, OWNER_LOST_BEFORE_DISPATCH
    }

    public interface Actions {
        void requestApproval(Row row,long expectedRevision);
        void requestCancel(Row row, long expectedRevision);
        void requestHandoff(Row row, long expectedRevision);
    }

    /** Immutable display row. Unknown sizes stay UNKNOWN_BYTES and never become zero. */
    public static final class Row {
        private final Object itemRef;
        private final String displayName;
        private final long totalBytes, receivedBytes;
        private final TaskSnapshot snapshot;
        private final boolean nativeApprovalOffered,nativeHandoffOffered;
        public Row(Object itemRef, String displayName, long totalBytes, long receivedBytes,
                   TaskSnapshot snapshot, boolean nativeHandoffOffered) {
            this(itemRef,displayName,totalBytes,receivedBytes,snapshot,false,nativeHandoffOffered);
        }
        public Row(Object itemRef,String displayName,long totalBytes,long receivedBytes,TaskSnapshot snapshot,boolean nativeApprovalOffered,boolean nativeHandoffOffered){
            if (itemRef == null || snapshot == null) throw invalid();
            this.itemRef = itemRef;
            this.displayName = text(displayName, MAX_NAME_CHARS);
            if (totalBytes < UNKNOWN_BYTES || totalBytes > MAX_BYTES) throw invalid();
            if (receivedBytes < UNKNOWN_BYTES || receivedBytes > MAX_BYTES) throw invalid();
            if (totalBytes >= 0 && receivedBytes >= 0 && receivedBytes > totalBytes) throw invalid();
            this.totalBytes = totalBytes;
            this.receivedBytes = receivedBytes;
            this.snapshot = snapshot;
            this.nativeApprovalOffered=nativeApprovalOffered;this.nativeHandoffOffered = nativeHandoffOffered;
        }
        public Object itemRef() { return itemRef; }
        public String displayName() { return displayName; }
        public long totalBytes() { return totalBytes; }
        public long receivedBytes() { return receivedBytes; }
        public TaskSnapshot snapshot() { return snapshot; }
        /** A prompt offered by the native caller; still NOT publication or file authorization. */
        public boolean nativeApprovalOffered(){return nativeApprovalOffered;}
        public boolean nativeHandoffOffered() { return nativeHandoffOffered; }
    }

    public static final class Rows {
        public static void validate(List<Row> rows, long pageRevision) {
            if (pageRevision < 1) throw new IllegalArgumentException("Invalid native download page revision");
            if (rows == null || rows.size() > MAX_ROWS) throw new IllegalArgumentException("Invalid native download page size");
            Map<Object, Row> seen = new IdentityHashMap<>();
            for (Row row : rows) {
                if (row == null) throw new IllegalArgumentException("Missing native download row");
                if (seen.put(row.itemRef(), row) != null) throw new IllegalArgumentException("Duplicate native download row token");
                terminal(row);
            }
        }
        public static Terminal terminal(Row row) {
            if (row == null) throw invalid();
            TaskSnapshot snapshot = row.snapshot;
            if (snapshot.phase() == TaskSnapshot.Phase.ENDED_BEFORE_DISPATCH) return Terminal.ENDED_BEFORE_DISPATCH;
            if (snapshot.phase() == TaskSnapshot.Phase.OWNER_LOST_BEFORE_DISPATCH) return Terminal.OWNER_LOST_BEFORE_DISPATCH;
            if (snapshot.effect() == TaskSnapshot.Effect.NOT_DISPATCHED) return Terminal.NOT_DISPATCHED;
            if (snapshot.work() == TaskSnapshot.Work.ENDED && snapshot.effect() == TaskSnapshot.Effect.OBSERVED_SUCCESS) return Terminal.FINISHED_OBSERVED_SUCCESS;
            if (snapshot.work() == TaskSnapshot.Work.ENDED && snapshot.effect() == TaskSnapshot.Effect.OBSERVED_FAILURE) return Terminal.FINISHED_OBSERVED_FAILURE;
            if (snapshot.effect() == TaskSnapshot.Effect.OBSERVED_SUCCESS || snapshot.effect() == TaskSnapshot.Effect.OBSERVED_FAILURE) return Terminal.EFFECT_OBSERVED_WORK_UNSETTLED;
            return Terminal.WORK_RUNNING_OR_UNKNOWN;
        }
        /** Human byte text; UNKNOWN stays "未知" and is never guessed as zero/complete. */
        public static String sizeText(long bytes) {
            if (bytes < 0) return "未知";
            if (bytes < 1024) return bytes + " B";
            if (bytes < 1024L * 1024) return oneDecimal(bytes, 1024) + " KiB";
            if (bytes < 1024L * 1024 * 1024) return oneDecimal(bytes, 1024L * 1024) + " MiB";
            return oneDecimal(bytes, 1024L * 1024 * 1024) + " GiB";
        }
        /** -1 means unknown; a full 100 never means the transfer completed or the file is ready. */
        public static int shownPercent(Row row) {
            if (row == null) throw invalid();
            if (row.totalBytes < 0 || row.receivedBytes < 0) return -1;
            if (row.totalBytes == 0) return 0;
            long percent = row.receivedBytes * 100 / row.totalBytes;
            return (int) Math.min(100, Math.max(0, percent));
        }
        public static boolean canRequestApproval(Row row){if(row==null)throw invalid();return row.nativeApprovalOffered && row.snapshot.phase()==TaskSnapshot.Phase.RESERVED && row.snapshot.work()==TaskSnapshot.Work.NOT_STARTED && row.snapshot.currentForPublication() && !row.snapshot.cancellationRequested();}
        public static boolean canRequestCancel(Row row) {
            if (row == null) throw invalid();
            return row.snapshot.work() == TaskSnapshot.Work.RUNNING && !row.snapshot.cancellationRequested();
        }
        /** Intent gate only: success effect + actual end + committed record + current + native prompt. */
        public static boolean canRequestHandoff(Row row) {
            if (row == null) throw invalid();
            return row.nativeHandoffOffered
                    && terminal(row) == Terminal.FINISHED_OBSERVED_SUCCESS
                    && row.snapshot.durability() == TaskSnapshot.Durability.COMMITTED
                    && row.snapshot.currentForPublication()
                    && !row.snapshot.cancellationRequested();
        }
        /** Truthful multi-axis text; deliberately contains no "completed/published/openable" claim. */
        public static String statusText(Row row) {
            TaskSnapshot snapshot = row.snapshot;
            List<String> parts = new ArrayList<>();
            switch (terminal(row)) {
                case NOT_DISPATCHED: parts.add("尚未发起"); break;
                case WORK_RUNNING_OR_UNKNOWN: parts.add(snapshot.work() == TaskSnapshot.Work.RUNNING ? "进行中" : "状态未知"); break;
                case EFFECT_OBSERVED_WORK_UNSETTLED: parts.add("已观察到结果，工作结束未确认"); break;
                case FINISHED_OBSERVED_SUCCESS: parts.add("已收到成功结果"); break;
                case FINISHED_OBSERVED_FAILURE: parts.add("已收到失败结果"); break;
                case ENDED_BEFORE_DISPATCH: parts.add("未发起即结束"); break;
                case OWNER_LOST_BEFORE_DISPATCH: parts.add("执行者丢失，未发起"); break;
                default: parts.add("状态未知"); break;
            }
            if(snapshot.work()==TaskSnapshot.Work.ENDED)parts.add("工作已结束");
            else if(snapshot.work()==TaskSnapshot.Work.UNKNOWN)parts.add("工作结束未知");
            if (snapshot.durability() == TaskSnapshot.Durability.COMMITTED) parts.add("记录已持久化");
            else if (snapshot.durability() == TaskSnapshot.Durability.UNKNOWN) parts.add("持久化未知");
            else parts.add("记录未持久化");
            if (snapshot.clientWait() == TaskSnapshot.ClientWait.DEADLINE_EXCEEDED) parts.add("等待超时，不代表任务结束");
            else if (snapshot.clientWait() == TaskSnapshot.ClientWait.DETACHED) parts.add("界面已分离");
            if (snapshot.cancellationRequested()) parts.add(snapshot.work()==TaskSnapshot.Work.ENDED?"已请求取消":"已请求取消，结束未确认");
            if (!snapshot.currentForPublication()) parts.add("记录已被更新的结果取代");
            return String.join(" · ", parts);
        }
        public static String detail(Row row) {
            if (row == null) throw invalid();
            int percent = shownPercent(row);
            String size = sizeText(row.receivedBytes) + " / " + sizeText(row.totalBytes);
            String progress = percent < 0 ? "大小未知" : percent + "%";
            return size + " · " + progress + " · " + statusText(row);
        }
        private static String oneDecimal(long bytes, long unit) {
            long tenths = bytes * 10 / unit;
            return (tenths / 10) + "." + (tenths % 10);
        }
    }

    /** Owner-thread page/frame reducer; identical lifecycle to other bounded native lists. */
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
            if (!current(expectedFrame, expectedRevision)) throw new IllegalStateException("Stale native download frame");
            for (Row row : rows) if (row.itemRef() == itemRef) return row;
            throw new IllegalArgumentException("Unknown native download row token");
        }
        private void check() { if (Thread.currentThread() != owner) throw new IllegalStateException("Download adapter owner thread required"); }
    }

    private final Context context;
    private final State state = new State();
    private BrowserToolbar.Binding binding;
    private Actions actions;
    private String outcomeText;
    private final Object holderIssuer=new Object();private final Map<Holder,Boolean> holders=new WeakHashMap<>();

    public DownloadsAdapter(Context context) {
        BrowserToolbar.main();
        if (context == null) throw invalid();
        Context application = context.getApplicationContext();
        this.context = application == null ? context : application;
    }

    public void bind(BrowserToolbar.Binding binding, long pageRevision, List<Row> rows, Actions actions) {
        BrowserToolbar.main();
        if (binding == null || actions == null) throw invalid();
        binding.check();
        state.show(pageRevision, rows);clearHolders();
        this.binding = binding;
        this.actions = actions;
        outcomeText = null;
        notifyDataSetChanged();
    }

    public void unbind() {
        BrowserToolbar.main();
        binding = null;
        actions = null;
        outcomeText = null;
        state.hide();clearHolders();
        notifyDataSetChanged();
    }

    public void retire() {
        BrowserToolbar.main();
        binding = null;
        actions = null;
        state.retire();outcomeText=null;clearHolders();
        notifyDataSetChanged();
    }

    public String lastOutcomeText() { return outcomeText; }

    @Override public int getCount() { return state.rowCount(); }
    @Override public Row getItem(int position) {
        Row row = state.rowAt(position);
        if (row == null) throw new IndexOutOfBoundsException("Invalid native download position");
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
        if(!holders.containsKey(holder) && holders.size()>=MAX_ROWS*2)throw new IllegalStateException("Native download view budget reached");holders.put(holder,Boolean.TRUE);
        Row row = state.rowAt(position);
        if (row == null) {
            clear(holder);
            return holder.root;
        }
        bindRow(holder, row);
        return holder.root;
    }

    private Holder create() {
        if(holders.size()>=MAX_ROWS*2)throw new IllegalStateException("Native download view budget reached");
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setMinimumHeight(dp(48));
        TextView name = new TextView(context);
        name.setTextSize(16);
        TextView detail = new TextView(context);
        detail.setTextSize(13);
        LinearLayout options = new LinearLayout(context);
        options.setOrientation(LinearLayout.HORIZONTAL);
        Button approve=button("批准下载");options.addView(approve,new LinearLayout.LayoutParams(0,dp(48),1));
        Button cancel = button("取消请求");
        Button handoff = button("交接文件");
        options.addView(cancel, new LinearLayout.LayoutParams(0, dp(48), 1));
        options.addView(handoff, new LinearLayout.LayoutParams(0, dp(48), 1));
        root.addView(name, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(detail, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(options, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        Holder holder = new Holder(holderIssuer,root, name, detail, approve,cancel, handoff);
        root.setTag(holder);
        return holder;
    }

    private void bindRow(Holder holder, Row row) {
        Object frame = state.frame();
        long revision = state.revision();
        holder.name.setText(row.displayName());
        holder.detail.setText(Rows.detail(row));
        holder.approve.setEnabled(Rows.canRequestApproval(row));holder.approve.setContentDescription("请求批准下载："+row.displayName());holder.approve.setOnClickListener(v->dispatch(frame,revision,row,APPROVE));
        holder.cancel.setEnabled(Rows.canRequestCancel(row));
        holder.handoff.setEnabled(Rows.canRequestHandoff(row));
        holder.cancel.setContentDescription("请求取消下载：" + row.displayName());
        holder.handoff.setContentDescription("交接下载文件给系统：" + row.displayName());
        holder.cancel.setOnClickListener(v -> dispatch(frame, revision, row, CANCEL));
        holder.handoff.setOnClickListener(v -> dispatch(frame, revision, row, HANDOFF));
    }

    private void clear(Holder holder) {
        holder.name.setText("");
        holder.detail.setText("");
        holder.name.setContentDescription(null);
        holder.detail.setContentDescription(null);
        holder.cancel.setContentDescription(null);
        holder.handoff.setContentDescription(null);
        holder.approve.setOnClickListener(null);holder.approve.setContentDescription(null);holder.approve.setEnabled(false);
        holder.cancel.setOnClickListener(null);
        holder.handoff.setOnClickListener(null);
        holder.cancel.setEnabled(false);
        holder.handoff.setEnabled(false);
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
        if(action==APPROVE?!Rows.canRequestApproval(row):action==CANCEL?!Rows.canRequestCancel(row):!Rows.canRequestHandoff(row))return;
        BrowserToolbar.IntentOutcome result=BrowserToolbar.invokeNativeIntent(()->{if(action==APPROVE)callback.requestApproval(row,expectedRevision);else if(action==CANCEL)callback.requestCancel(row,expectedRevision);else callback.requestHandoff(row,expectedRevision);});
        if(result==BrowserToolbar.IntentOutcome.CALLBACK_UNKNOWN && binding==capturedBinding && actions==callback && state.current(expectedFrame,expectedRevision))outcomeText="操作结果未知，请重新读取下载状态";
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
        final Object issuer;final LinearLayout root;
        final TextView name, detail;
        final Button approve,cancel, handoff;
        Holder(Object issuer,LinearLayout root, TextView name, TextView detail,Button approve, Button cancel, Button handoff) {
            this.issuer=issuer;this.root = root; this.name = name; this.detail = detail;
            this.approve=approve;this.cancel = cancel; this.handoff = handoff;
        }
    }

    private static String text(String value, int maximum) {
        if (value == null || value.isEmpty() || value.length() > maximum) throw invalid();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isISOControl(c)) throw invalid();
            if (Character.isHighSurrogate(c)) {
                if (++i >= value.length() || !Character.isLowSurrogate(value.charAt(i))) throw invalid();
            } else if (Character.isLowSurrogate(c)) throw invalid();
        }
        return value;
    }

    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid native download row/page"); }
}
