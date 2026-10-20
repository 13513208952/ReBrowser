package io.github.rebrowser.ui;

import android.app.Activity;
import android.os.Bundle;
import android.view.ViewGroup.LayoutParams;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.util.Collections;
import java.util.List;

/**
 * Native history WINDOW SHELL. It displays ONE already-queried page through HistoryAdapter and
 * reports period/open/retain/pause/delete INTENT. It never queries or clears a history store,
 * never trusts Intent extras as account/actor/epoch authority and never claims a deletion or
 * pause succeeded. Without a native page/binding the window stays explicitly unconnected.
 */
public final class HistoryActivity extends Activity {
    public enum Period { DAYS_7(7), DAYS_30(30), DAYS_90(90), ALL(0);
        public final int days; Period(int days){this.days=days;}
        public static Period fromDays(int days){for(Period p:values())if(p.days==days)return p;throw new IllegalArgumentException("Unsupported history period");}
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

    public interface Actions {
        void open(HistoryAdapter.Row row,long expectedRevision);
        void retain(HistoryAdapter.Row row,long expectedRevision);
        void remove(HistoryAdapter.Row row,long expectedRevision);
        void changeQuery(QueryIntent intent);
    }

    private TextView header,status;
    private ListView list;
    private HistoryAdapter adapter;
    private BrowserToolbar.Binding binding;
    private Actions actions;
    private Object pageFrame;

    @Override protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        header=new TextView(this);
        header.setText("历史记录");
        header.setTextSize(20);
        root.addView(header,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT));
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
        BrowserToolbar.main();
        if(binding==null || actions==null)throw new IllegalArgumentException("Missing native history binding");
        binding.check();
        this.binding=binding;this.actions=actions;this.pageFrame=new Object();
        adapter.bind(binding,pageRevision,rows,new HistoryAdapter.Actions(){
            public void open(HistoryAdapter.Row row,long revision){actions.open(row,revision);}
            public void retain(HistoryAdapter.Row row,long revision){actions.retain(row,revision);}
            public void remove(HistoryAdapter.Row row,long revision){actions.remove(row,revision);}
        });
        status.setText(rows.isEmpty()?"没有可显示的历史记录（未读取其他来源）":"显示一页历史记录 · 操作需原生窗口确认");
    }

    public void showUnavailable(String reason){
        BrowserToolbar.main();
        adapter.unbind();binding=null;actions=null;pageFrame=null;
        status.setText(reason==null || reason.isEmpty()?"未连接原生历史窗口":"历史不可用：" + BrowserToolbar.boundedStatusReason(reason));
        list.setAdapter(adapter);
    }

    /** Emits a query INTENT; the owner supplies a new page later. */
    public void requestQuery(Period period,boolean pauseWrites,Actions actions){
        BrowserToolbar.main();
        BrowserToolbar.Binding captured=binding;
        final Object expectedFrame=pageFrame;
        if(captured==null || actions==null || this.actions!=actions || expectedFrame==null)
            throw new IllegalStateException("History window is not connected");
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
        binding=null;actions=null;pageFrame=null;if(list!=null)list.setAdapter(null);BrowserToolbar.clearOwnedViewTree(header);BrowserToolbar.clearOwnedViewTree(status);
        super.onDestroy();
    }

    /** Bounded display text for an empty/error explanation; never private URL/account data. */
    static String boundedReason(String value){
        if(value==null)return "";
        if(value.length()>200)throw new IllegalArgumentException("Unbounded status text");
        return value;
    }
    static List<HistoryAdapter.Row> emptyPage(){return Collections.emptyList();}
}
