package io.github.reoutlook;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

/** Virtualized history list; pagination never discards long-lived database records. */
@SuppressLint({"SetTextI18n", "ViewConstructor"}) // Dependency-injected view, never inflated from XML.
final class ReBrowserHistoryView extends LinearLayout {
    private static final int PAGE_SIZE = 50;
    private final ReBrowserHistory history;
    private final Consumer<String> open;
    private final List<ReBrowserHistory.Entry> entries = new ArrayList<>();
    private final ImageButton maintain, clear, pause;
    private final TextView subtitle;
    private final Button more;
    private final ListView list;
    private boolean holding;
    private final HistoryAdapter adapter = new HistoryAdapter();

    ReBrowserHistoryView(Context context, ReBrowserHistory history, Runnable back,
            Consumer<String> open, Consumer<Boolean> pauseChanged) {
        super(context);
        this.history = history;
        this.open = open;
        setOrientation(VERTICAL);
        setBackgroundColor(Color.rgb(247, 248, 252));
        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(8), dp(8), dp(8), dp(8));
        TextView backButton = new TextView(context);
        backButton.setText("‹"); backButton.setTextSize(32); backButton.setGravity(Gravity.CENTER);
        backButton.setContentDescription("返回网页"); backButton.setOnClickListener(view -> back.run());
        header.addView(backButton, new LayoutParams(dp(44), dp(48)));
        LinearLayout labels = new LinearLayout(context); labels.setOrientation(VERTICAL);
        TextView title = new TextView(context); title.setText("历史记录"); title.setTextSize(19);
        title.setTextColor(Color.rgb(31, 36, 45)); title.setTypeface(null, android.graphics.Typeface.BOLD);
        labels.addView(title);
        subtitle = new TextView(context); subtitle.setTextSize(11); subtitle.setTextColor(Color.rgb(102, 107, 121));
        labels.addView(subtitle);
        header.addView(labels, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        maintain = iconButton(); clear = iconButton(); pause = iconButton();
        header.addView(maintain); header.addView(clear); header.addView(pause);
        addView(header, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(72)));
        maintain.setOnClickListener(view -> {
            try { history.setMaintaining(!history.maintaining()); refresh(); }
            catch (RuntimeException error) { failure(); }
        });
        clear.setOnClickListener(view -> {
            try {
                int count = history.clearTimed(); refresh();
                Toast.makeText(context, "已清理 " + count + " 条有时限记录", Toast.LENGTH_SHORT).show();
            } catch (RuntimeException error) { failure(); }
        });
        pause.setOnClickListener(view -> {
            boolean value = !history.paused();
            history.setPaused(value);
            pauseChanged.accept(value);
            refresh();
        });
        list = new ListView(context);
        list.setDivider(new android.graphics.drawable.ColorDrawable(Color.rgb(247, 248, 252)));
        list.setDividerHeight(dp(6));
        list.setClipToPadding(false); list.setPadding(dp(12), 0, dp(12), dp(16));
        more = new Button(context); more.setText("加载更早记录"); more.setOnClickListener(view -> loadMore());
        list.addFooterView(more, null, false);
        list.setAdapter(adapter);
        addView(list, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        refresh();
    }

    void refresh() {
        int position = list.getFirstVisiblePosition();
        try {
            boolean hold = history.maintaining();
            boolean paused = history.paused();
            maintain.setImageDrawable(new ReBrowserHistoryIcon(ReBrowserHistoryIcon.MAINTAIN, hold));
            pause.setImageDrawable(new ReBrowserHistoryIcon(ReBrowserHistoryIcon.PAUSE, paused));
            clear.setImageDrawable(new ReBrowserHistoryIcon(ReBrowserHistoryIcon.CLEAR_TIMED, false));
            maintain.setBackground(ReBrowserUi.roundedBackground(hold ? Color.rgb(255, 235, 238) : Color.WHITE, dp(24)));
            pause.setBackground(ReBrowserUi.roundedBackground(paused ? Color.rgb(235, 225, 255) : Color.WHITE, dp(24)));
            clear.setEnabled(!hold); clear.setAlpha(hold ? 0.35f : 1f);
            maintain.setContentDescription(hold ? "结束维持记录，重新开始30分钟期限" : "开始维持记录");
            pause.setContentDescription(paused ? "恢复历史记录，并同步所有当前网页" : "暂停历史记录");
            clear.setContentDescription(hold ? "清理有时限记录，维持记录期间禁用" : "清理所有30分钟有时限记录");
            maintain.setSelected(hold); pause.setSelected(paused);
            List<ReBrowserHistory.Entry> latest = history.entries(0, PAGE_SIZE);
            long count = history.snapshot().getLong("recordCount");
            holding = hold;
            entries.clear(); entries.addAll(latest);
            subtitle.setText(count + " 条 · " + (paused ? "暂停记录" : hold ? "维持中" : "最新在前"));
            more.setVisibility(entries.size() < count ? VISIBLE : GONE);
            adapter.notifyDataSetChanged();
            list.setSelection(Math.min(position, Math.max(0, entries.size() - 1)));
        } catch (Exception error) {
            subtitle.setText("暂时无法读取历史记录");
            more.setVisibility(GONE);
        }
    }

    private void loadMore() {
        try {
            entries.addAll(history.entries(entries.size(), PAGE_SIZE));
            more.setVisibility(entries.size() < history.snapshot().getLong("recordCount") ? VISIBLE : GONE);
            adapter.notifyDataSetChanged();
        } catch (Exception error) { failure(); }
    }

    private ImageButton iconButton() {
        ImageButton button = new ImageButton(getContext());
        button.setPadding(dp(10), dp(10), dp(10), dp(10));
        button.setBackground(ReBrowserUi.roundedBackground(Color.WHITE, dp(24)));
        LayoutParams params = new LayoutParams(dp(44), dp(44)); params.setMargins(dp(3), 0, dp(3), 0);
        button.setLayoutParams(params);
        return button;
    }

    private void openEntry(ReBrowserHistory.Entry entry) {
        try {
            ReBrowserHistory.Entry current = history.find(entry.id);
            if (current == null) { refresh(); Toast.makeText(getContext(), "记录已过期或删除", Toast.LENGTH_SHORT).show(); }
            else open.accept(current.url);
        } catch (RuntimeException error) { failure(); }
    }

    private void deleteEntry(ReBrowserHistory.Entry entry) {
        new AlertDialog.Builder(getContext()).setTitle("删除历史记录？")
                .setMessage(entry.title)
                .setPositiveButton("删除", (dialog, which) -> {
                    try { history.delete(entry.id); refresh(); }
                    catch (RuntimeException error) { failure(); }
                }).setNegativeButton("取消", null).show();
    }

    private void failure() { Toast.makeText(getContext(), "历史记录操作失败，请重试", Toast.LENGTH_SHORT).show(); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private final class HistoryAdapter extends BaseAdapter {
        @Override public int getCount() { return entries.isEmpty() ? 1 : entries.size(); }
        @Override public Object getItem(int position) { return entries.isEmpty() ? null : entries.get(position); }
        @Override public long getItemId(int position) { return entries.isEmpty() ? -1 : entries.get(position).id; }
        @Override public int getViewTypeCount() { return 2; }
        @Override public int getItemViewType(int position) { return entries.isEmpty() ? 0 : 1; }
        @Override public View getView(int position, View convertView, ViewGroup parent) {
            if (entries.isEmpty()) {
                TextView empty = new TextView(getContext()); empty.setText("还没有历史记录");
                empty.setTextSize(16); empty.setGravity(Gravity.CENTER); empty.setPadding(0, dp(72), 0, dp(72));
                empty.setTextColor(Color.rgb(112, 115, 130)); return empty;
            }
            ReBrowserHistory.Entry entry = entries.get(position);
            LinearLayout row;
            if (convertView instanceof LinearLayout) row = (LinearLayout) convertView;
            else {
                row = new LinearLayout(getContext()); row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(12), dp(10), dp(6), dp(10));
                row.setBackground(ReBrowserUi.roundedBackground(Color.WHITE, dp(10)));
                LinearLayout labels = new LinearLayout(getContext()); labels.setOrientation(VERTICAL);
                for (int i = 0; i < 3; i++) {
                    TextView text = new TextView(getContext()); text.setSingleLine(true); text.setTextSize(i == 0 ? 15 : 11);
                    text.setTextColor(i == 0 ? Color.rgb(32, 37, 46) : Color.rgb(105, 110, 124));
                    labels.addView(text);
                }
                row.addView(labels, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                TextView delete = new TextView(getContext()); delete.setText("删除"); delete.setTextSize(12);
                delete.setGravity(Gravity.CENTER); delete.setTextColor(Color.rgb(127, 85, 155));
                row.addView(delete, new LayoutParams(dp(48), dp(52)));
            }
            LinearLayout labels = (LinearLayout) row.getChildAt(0);
            ((TextView) labels.getChildAt(0)).setText(entry.title);
            ((TextView) labels.getChildAt(1)).setText(entry.url);
            ((TextView) labels.getChildAt(2)).setText(DateFormat.getDateTimeInstance(
                    DateFormat.SHORT, DateFormat.SHORT).format(new Date(entry.visitedAt))
                    + " · " + entry.sourceTitle + (holding ? " · 维持中"
                            : entry.expiresAt == null ? " · 长期" : " · 30分钟"));
            labels.setOnClickListener(view -> openEntry(entry));
            row.getChildAt(1).setContentDescription("删除历史记录 " + entry.title);
            row.getChildAt(1).setOnClickListener(view -> deleteEntry(entry));
            return row;
        }
    }
}
