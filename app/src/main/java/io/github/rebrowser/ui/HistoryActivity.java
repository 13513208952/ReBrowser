package io.github.rebrowser.ui;

import android.app.Activity;
import android.os.Bundle;
import android.view.ViewGroup.LayoutParams;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Native history WINDOW SHELL over ONE already-queried page. It filters only the rows in hand,
 * never queries the database, and turns paging into a bounded REQUEST carrying the original page
 * revision. A busy/unknown page request blocks repeated paging until the native owner supplies a
 * new page; filter changes never re-token rows and never turn display into a history transaction.
 */
public final class HistoryActivity extends Activity {
    public enum Period { DAYS_7(7), DAYS_30(30), DAYS_90(90), ALL(0);
        public final int days; Period(int days){this.days=days;}
        public static Period fromDays(int days){for(Period p:values())if(p.days==days)return p;throw new IllegalArgumentException("Unsupported history period");}
    }
    public enum PageDirection { PREVIOUS, NEXT }
    /** Native-provided availability for this page; absent means unknown, never "available". */
    public static final class PageNav {
        private final Boolean previousAvailable,nextAvailable;
        public PageNav(Boolean previousAvailable,Boolean nextAvailable){this.previousAvailable=previousAvailable;this.nextAvailable=nextAvailable;}
        public Boolean previousAvailable(){return previousAvailable;}
        public Boolean nextAvailable(){return nextAvailable;}
        public boolean available(PageDirection direction){
            Boolean value=direction==PageDirection.PREVIOUS?previousAvailable:nextAvailable;
            return Boolean.TRUE.equals(value);
        }
    }
    /** User INTENT only; the native history owner re-reads and performs with current authority. */
    public static final class QueryIntent {
        private final Period period;private final boolean pauseWrites;private final Object expectedPageToken;
        public QueryIntent(Period period,boolean pauseWrites,Object expectedPageToken){
            if(period==null)throw new IllegalArgumentException("Missing history period");
            this.period=period;this.pauseWrites=pauseWrites;this.expectedPageToken=expectedPageToken;
        }
        public Period period(){return period;}
        public boolean pauseWrites(){return pauseWrites;}
        public Object expectedPageToken(){return expectedPageToken;}
        public boolean isApplied(){return false;}
    }
    /** Paging request bound to the page revision it was made against. */
    public static final class PageRequest {
        private final PageDirection direction;private final long expectedRevision;
        public PageRequest(PageDirection direction,long expectedRevision){
            if(direction==null || expectedRevision<1)throw new IllegalArgumentException("Invalid page request");
            this.direction=direction;this.expectedRevision=expectedRevision;
        }
        public PageDirection direction(){return direction;}
        public long expectedRevision(){return expectedRevision;}
        public boolean isApplied(){return false;}
    }

    public interface Actions {
        void open(HistoryAdapter.Row row,long expectedRevision);
        void retain(HistoryAdapter.Row row,long expectedRevision);
        void remove(HistoryAdapter.Row row,long expectedRevision);
        void changeQuery(QueryIntent intent);
        void requestPage(PageRequest request);
    }

    /** Bounded literal filter over the CURRENT page only (title + display text, case-folded). */
    public static final class Filter {
        public static final int MAX_FILTER_CHARS = 64;
        public static List<HistoryAdapter.Row> apply(List<HistoryAdapter.Row> page,String literal){
            if(page==null || page.size()>HistoryAdapter.MAX_ROWS)throw new IllegalArgumentException("Invalid history page");
            HistoryAdapter.Rows.validate(page,1);
            if(literal==null || literal.isEmpty())return new ArrayList<>(page);
            io.github.rebrowser.core.DomainResult.text(literal,MAX_FILTER_CHARS,false);
            String needle=literal.trim().toLowerCase(Locale.ROOT);
            if(needle.isEmpty())return new ArrayList<>(page);
            if(literal.length()>MAX_FILTER_CHARS)throw new IllegalArgumentException("History filter too long");
            List<HistoryAdapter.Row> matched=new ArrayList<>();
            for(HistoryAdapter.Row row:page){
                if(row.title().toLowerCase(Locale.ROOT).contains(needle)
                        || row.displayUrl().toLowerCase(Locale.ROOT).contains(needle))matched.add(row);
            }
            return matched;
        }
    }

    private TextView status;private android.widget.Button previousPageButton,nextPageButton;
    private EditText filterInput;
    private ListView list;
    private HistoryAdapter adapter;
    private BrowserToolbar.Binding binding;
    private Actions actions;
    private Object pageFrame;
    private long pageRevision;
    private List<HistoryAdapter.Row> allRows=new ArrayList<>();
    private PageNav pageNav=new PageNav(null,null);
    private PageDirection pendingPage;
    private String filterText="";
    private final java.util.Set<AttemptKey> attempts=new java.util.HashSet<>();

    private static final class AttemptKey {
        final long revision;final Object itemRef;final HistoryAdapter.Offered action;
        AttemptKey(long revision,Object itemRef,HistoryAdapter.Offered action){this.revision=revision;this.itemRef=itemRef;this.action=action;}
        @Override public boolean equals(Object other){if(!(other instanceof AttemptKey))return false;AttemptKey key=(AttemptKey)other;return revision==key.revision && itemRef==key.itemRef && action==key.action;}
        @Override public int hashCode(){return Long.hashCode(revision)*31+action.hashCode();}
    }

    @Override protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        TextView header=new TextView(this);
        header.setText("历史记录");
        header.setTextSize(20);
        root.addView(header,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT));
        filterInput=new EditText(this);filterInput.setSaveEnabled(false);filterInput.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO);
        filterInput.setSingleLine(true);
        filterInput.setHint("筛选本页历史（标题或显示地址）");
        filterInput.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(Filter.MAX_FILTER_CHARS)});
        root.addView(filterInput,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT));
        android.widget.Button apply=new android.widget.Button(this);
        apply.setText("筛选本页");
        apply.setMinimumHeight(dp(48));
        apply.setOnClickListener(v->applyFilter());
        root.addView(apply,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(48)));
        android.widget.Button clear=new android.widget.Button(this);clear.setText("清除筛选");clear.setMinimumHeight(dp(48));clear.setOnClickListener(v->clearFilter());root.addView(clear);
        LinearLayout paging=new LinearLayout(this);previousPageButton=new android.widget.Button(this);nextPageButton=new android.widget.Button(this);previousPageButton.setText("上一页");nextPageButton.setText("下一页");previousPageButton.setMinimumHeight(dp(48));nextPageButton.setMinimumHeight(dp(48));previousPageButton.setOnClickListener(v->{if(actions!=null)requestPage(PageDirection.PREVIOUS,actions);});nextPageButton.setOnClickListener(v->{if(actions!=null)requestPage(PageDirection.NEXT,actions);});paging.addView(previousPageButton);paging.addView(nextPageButton);root.addView(paging);
        status=new TextView(this);
        status.setText("未连接原生历史窗口");
        root.addView(status,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT));
        list=new ListView(this);
        adapter=new HistoryAdapter(this);
        list.setAdapter(adapter);
        root.addView(list,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,0,1));
        setContentView(root);
        // Intent extras are deliberately ignored: they are not history authority.
    }

    /** Called ONLY by the owning native window with a current binding and one queried page. */
    public void showPage(BrowserToolbar.Binding binding,long pageRevision,List<HistoryAdapter.Row> rows,Actions actions){
        showPage(binding,pageRevision,rows,new PageNav(null,null),actions);
    }

    public void showPage(BrowserToolbar.Binding binding,long pageRevision,List<HistoryAdapter.Row> rows,PageNav nav,Actions actions){
        BrowserToolbar.main();
        if(binding==null || nav==null || actions==null)throw new IllegalArgumentException("Missing native history binding");
        binding.check();
        HistoryAdapter.Rows.validate(rows,pageRevision);
        this.binding=binding;this.actions=actions;this.pageFrame=new Object();
        this.pageRevision=pageRevision;this.pageNav=nav;this.allRows=new ArrayList<>(rows);
        this.pendingPage=null;filterText="";filterInput.setText("");
        attempts.clear(); // a new native page owns new tokens
        renderPage();
    }

    /** Local display filter; never queries, never re-tokens and never changes the page revision. */
    public void applyFilter(){
        BrowserToolbar.main();
        if(binding==null || pageFrame==null)return;
        filterText=filterInput.getText()==null?"":filterInput.getText().toString();
        renderPage();
    }

    public void clearFilter(){
        BrowserToolbar.main();
        filterText="";
        if(filterInput!=null)filterInput.setText("");
        if(binding!=null)renderPage();
    }

    private void renderPage(){
        if(binding==null || pageFrame==null)return;
        try{binding.check();}catch(SecurityException|IllegalStateException stale){showUnavailable("来源或窗口已失效，需重新读取");return;}
        List<HistoryAdapter.Row> visible;
        try{visible=Filter.apply(allRows,filterText);}catch(RuntimeException invalid){status.setText("筛选条件无效，已保留原页");return;}
        List<HistoryAdapter.Row> projected=new ArrayList<>(visible.size());
        for(HistoryAdapter.Row row:visible)projected.add(reduceAttempted(row));
        final Actions exactOwner=actions;final Object exactFrame=pageFrame;final BrowserToolbar.Binding exactBinding=binding;
        adapter.bind(binding,pageRevision,projected,new HistoryAdapter.Actions(){
            public void open(HistoryAdapter.Row row,long revision){if(exactOwner!=null && actions==exactOwner && pageFrame==exactFrame && binding==exactBinding)exactOwner.open(row,revision);}
            public void retain(HistoryAdapter.Row row,long revision){
                Actions owner=exactOwner;if(owner==null || actions!=owner || pageFrame!=exactFrame || binding!=exactBinding)return;
                if(!attempts.add(new AttemptKey(revision,row.itemRef(),HistoryAdapter.Offered.RETAIN)))return;
                owner.retain(row,revision);
            }
            public void remove(HistoryAdapter.Row row,long revision){
                Actions owner=exactOwner;if(owner==null || actions!=owner || pageFrame!=exactFrame || binding!=exactBinding)return;
                if(!attempts.add(new AttemptKey(revision,row.itemRef(),HistoryAdapter.Offered.REMOVE)))return;
                owner.remove(row,revision);
            }
        });
        status.setText(pageStatus(visible.size()));
        previousPageButton.setEnabled(pendingPage==null && pageNav.available(PageDirection.PREVIOUS));nextPageButton.setEnabled(pendingPage==null && pageNav.available(PageDirection.NEXT));
        if(filterInput!=null && !filterText.isEmpty() && !filterText.equals(filterInput.getText().toString()))filterInput.setText(filterText);
    }

    /** A row already attempted in this page keeps its token but loses that offered action. */
    private HistoryAdapter.Row reduceAttempted(HistoryAdapter.Row row){
        java.util.EnumSet<HistoryAdapter.Offered> offered=java.util.EnumSet.noneOf(HistoryAdapter.Offered.class);
        offered.addAll(row.offered());
        for(HistoryAdapter.Offered action:new HistoryAdapter.Offered[]{HistoryAdapter.Offered.RETAIN,HistoryAdapter.Offered.REMOVE})
            if(attempts.contains(new AttemptKey(pageRevision,row.itemRef(),action)))offered.remove(action);
        if(offered.equals(row.offered()))return row;
        return new HistoryAdapter.Row(row.itemRef(),row.title(),row.displayUrl(),row.visitedText(),offered,
                "本页已请求过该操作，等待原生重新读取");
    }

    private String pageStatus(int visibleRows){
        StringBuilder text=new StringBuilder();
        text.append("显示 ").append(visibleRows).append(" / ").append(allRows.size()).append(" 行");
        if(!filterText.trim().isEmpty())text.append(" · 本页筛选");
        if(pageNav.previousAvailable()==null && pageNav.nextAvailable()==null)text.append(" · 翻页状态未知");
        else text.append(" · 上一页").append(pageNav.available(PageDirection.PREVIOUS)?"可用":"不可用")
                .append("，下一页").append(pageNav.available(PageDirection.NEXT)?"可用":"不可用");
        if(pendingPage!=null)text.append(" · 已请求").append(pendingPage==PageDirection.NEXT?"下一页":"上一页").append("，等待原生结果");
        text.append(" · 操作需原生窗口确认");
        return text.toString();
    }

    public void showUnavailable(String reason){
        BrowserToolbar.main();
        adapter.unbind();binding=null;actions=null;pageFrame=null;allRows=new ArrayList<>();pendingPage=null;attempts.clear();filterText="";filterInput.setText("");previousPageButton.setEnabled(false);nextPageButton.setEnabled(false);
        status.setText(reason==null || reason.isEmpty()?"未连接原生历史窗口":"历史不可用：" + boundedReason(reason));
        list.setAdapter(adapter);
    }

    /** One paging request per page revision; unknown outcome keeps paging blocked. */
    public void requestPage(PageDirection direction,Actions actions){
        BrowserToolbar.main();
        BrowserToolbar.Binding captured=binding;
        if(captured==null || actions==null || this.actions!=actions || pageFrame==null)
            throw new IllegalStateException("History window is not connected");
        if(direction==null)throw new IllegalArgumentException("Missing page direction");
        if(pendingPage!=null || !pageNav.available(direction))return;
        final Object expectedFrame=pageFrame;final long expectedRevision=pageRevision;
        pendingPage=direction;previousPageButton.setEnabled(false);nextPageButton.setEnabled(false);
        status.setText(pageStatus(Filter.apply(allRows,filterText).size()));
        BrowserToolbar.IntentOutcome outcome=BrowserToolbar.invokeNativeIntent(()->{
            captured.check();
            if(pageFrame!=expectedFrame || binding!=captured || this.actions!=actions || pageRevision!=expectedRevision)return;
            actions.requestPage(new PageRequest(direction,expectedRevision));
        });
        if(outcome==BrowserToolbar.IntentOutcome.CALLBACK_UNKNOWN && pageFrame==expectedFrame)
            status.setText("翻页请求结果未知，请重新读取历史；不会自动重试");
    }

    /** Emits a query INTENT; the owner supplies a new page later. */
    public void requestQuery(Period period,boolean pauseWrites,Actions actions){
        BrowserToolbar.main();
        BrowserToolbar.Binding captured=binding;
        if(captured==null || actions==null || this.actions!=actions || pageFrame==null)
            throw new IllegalStateException("History window is not connected");
        final Object expectedFrame=pageFrame;
        BrowserToolbar.IntentOutcome outcome=BrowserToolbar.invokeNativeIntent(()->{
            captured.check();
            if(binding!=captured || this.actions!=actions || pageFrame!=expectedFrame)return;
            actions.changeQuery(new QueryIntent(period,pauseWrites,expectedFrame));
        });
        if(outcome==BrowserToolbar.IntentOutcome.CALLBACK_UNKNOWN && pageFrame==expectedFrame)
            status.setText("历史查询结果未知，请重新读取");
    }

    @Override protected void onDestroy(){
        if(adapter!=null)adapter.retire();
        binding=null;actions=null;pageFrame=null;allRows=new ArrayList<>();attempts.clear();if(list!=null)list.setAdapter(null);BrowserToolbar.clearOwnedViewTree(findViewById(android.R.id.content));
        super.onDestroy();
    }

    /** Bounded display text for an empty/error explanation; never private URL/account data. */
    static String boundedReason(String value){
        if(value==null)return "";
        return BrowserToolbar.boundedStatusReason(value);
    }
    static List<HistoryAdapter.Row> emptyPage(){return java.util.Collections.emptyList();}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
