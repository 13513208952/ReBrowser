package io.github.rebrowser.ui;

import android.content.Context;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import io.github.rebrowser.browser.BrowserModels.*;

/** Native bounded child-tab overview, retains exact workspace/tab/revision for every action. */
public final class TabPanel extends ScrollView {
    public interface Actions {void create(WorkspaceRef workspace,long expectedTabsRevision);void select(TabRef tab,long expectedTabsRevision);void close(TabRef tab,long expectedTabsRevision);}
    public static final class Rows {
        public static void validate(TabsView group){if(group==null || group.tabs().isEmpty() || group.tabs().size()>50)throw new IllegalArgumentException("Invalid native child-tab rows");for(TabView tab:group.tabs())if(tab.ref().workspace()!=group.workspace())throw new IllegalArgumentException("Wrong native tab workspace");}
        public static String label(TabView tab){if(tab==null)throw new IllegalArgumentException("Missing native child tab");return (tab.popupParent()==null?"":"弹出页 · ")+tab.title();}
    }
    private final LinearLayout rows;private Object frame;
    public TabPanel(Context context){super(context);BrowserToolbar.main();rows=new LinearLayout(context);rows.setOrientation(LinearLayout.VERTICAL);addView(rows,new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT));}
    public void bind(BrowserToolbar.Binding binding,TabsView snapshot,Actions actions){
        BrowserToolbar.main();if(binding==null || actions==null)throw new IllegalArgumentException("Missing native tab binding");binding.check();Rows.validate(snapshot);unbind();final Object current=new Object();frame=current;
        Button create=button("新建子标签页");create.setOnClickListener(v->run(current,binding,()->actions.create(snapshot.workspace(),snapshot.revision())));rows.addView(create);
        for(TabView tab:snapshot.tabs()){
            LinearLayout row=new LinearLayout(getContext());Button select=button(Rows.label(tab));select.setSelected(snapshot.selected()==tab.ref());select.setOnClickListener(v->run(current,binding,()->actions.select(tab.ref(),snapshot.revision())));row.addView(select,new LinearLayout.LayoutParams(0,dp(48),1));
            Button close=button("关闭");close.setContentDescription("关闭子标签页："+tab.title());close.setOnClickListener(v->run(current,binding,()->actions.close(tab.ref(),snapshot.revision())));row.addView(close,new LinearLayout.LayoutParams(dp(72),dp(48)));rows.addView(row);
        }
    }
    private void run(Object expected,BrowserToolbar.Binding binding,Runnable action){if(frame!=expected)return;try{binding.check();if(frame==expected)action.run();}catch(SecurityException stale){unbind();}catch(RuntimeException unknown){setContentDescription("操作结果未知，请重新读取子标签状态");}}
    public void unbind(){BrowserToolbar.main();frame=null;rows.removeAllViews();setContentDescription(null);}
    @Override protected void onDetachedFromWindow(){unbind();super.onDetachedFromWindow();}
    private Button button(String text){Button button=new Button(getContext());button.setText(text);button.setMinimumHeight(dp(48));return button;}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
