package io.github.rebrowser.ui;

import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.*;

/** Browser-global URL favorites only. Secondary whole-environment bookmarks belong to the
 * workspace catalog, NOT folders in this list. This view never owns a repository or grants
 * navigation: the native owner re-resolves exact rows/current source on its shared IO lane. */
public final class BookmarkView extends ScrollView {
    public static final int MAX_ROWS=500,MAX_TITLE_CHARS=640,MAX_URL_CHARS=8192;
    public interface Actions {void open(Row row,long revision);void add(long revision);void edit(Row row,long revision);void remove(Row row,long revision);}
    public static final class Row {
        private final Object itemRef;private final String title,url;
        public Row(Object itemRef,String title,String url){if(itemRef==null)throw bad();this.itemRef=itemRef;this.title=text(title,MAX_TITLE_CHARS);this.url=text(url,MAX_URL_CHARS);}
        public Object itemRef(){return itemRef;}public String title(){return title;}public String displayUrl(){return url;}
    }
    public static final class Rows {
        public static void validate(List<Row> rows,long revision){if(rows==null || rows.size()>MAX_ROWS || revision<1)throw bad();Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());for(Row row:rows)if(row==null || !seen.add(row.itemRef))throw bad();}
        public static String label(Row row){if(row==null)throw bad();return row.title;}
        public static String description(Row row){if(row==null)throw bad();return row.url;}
    }
    /** Original page identity never silently becomes a newer favorite or browser tab. */
    public static final class State {
        private final Thread owner=Thread.currentThread();private List<Row> rows=Collections.emptyList();private long revision;private Object frame;private boolean retired;
        public Object show(long revision,List<Row> rows){check();if(retired)throw new IllegalStateException("Favorites view retired");Rows.validate(rows,revision);this.rows=Collections.unmodifiableList(new ArrayList<>(rows));this.revision=revision;return frame=new Object();}
        public void hide(){check();rows=Collections.emptyList();frame=null;}
        public void retire(){check();retired=true;hide();}
        public Object frame(){check();return frame;}public long revision(){check();return revision;}public int rowCount(){check();return rows.size();}public List<Row> rows(){check();return rows;}
        public Row rowAt(int position){check();return position<0 || position>=rows.size()?null:rows.get(position);}
        public boolean current(Object expected,long revision){check();return !retired && expected!=null && frame==expected && this.revision==revision;}
        public Row require(Object expected,long revision,Object item){check();if(!current(expected,revision))throw new IllegalStateException("Stale favorites page");for(Row row:rows)if(row.itemRef==item)return row;throw bad();}
        private void check(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Favorites UI thread required");}
    }
    private final LinearLayout root,rows;private final TextView count;private final Button add;private final State state=new State();private final List<View> controls=new ArrayList<>();private BrowserToolbar.Binding binding;private Actions actions;private boolean pending;
    public BookmarkView(Context context){super(context);setFillViewport(true);setSaveEnabled(false);root=new LinearLayout(context);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(14),dp(10),dp(14),dp(14));root.setBackgroundColor(0xfff7f8fc);LinearLayout header=new LinearLayout(context);header.setGravity(android.view.Gravity.CENTER_VERTICAL);count=new TextView(context);count.setTextSize(15);header.addView(count,new LinearLayout.LayoutParams(0,dp(48),1));add=button("＋");add.setContentDescription("收藏当前网页");header.addView(add,new LinearLayout.LayoutParams(dp(52),dp(48)));root.addView(header);rows=new LinearLayout(context);rows.setOrientation(LinearLayout.VERTICAL);root.addView(rows);addView(root,new LayoutParams(-1,-2));}
    public void bind(BrowserToolbar.Binding binding,long revision,List<Row> page,boolean canAddCurrent,Actions actions){BrowserToolbar.main();if(binding==null || actions==null)throw bad();binding.check();state.show(revision,page);this.binding=binding;this.actions=actions;pending=false;Object frame=state.frame();controls.clear();BrowserToolbar.clearOwnedViewTree(rows);rows.removeAllViews();count.setText(page.size()+" 个收藏网址");add.setEnabled(canAddCurrent);controls.add(add);add.setOnClickListener(v->dispatch(frame,revision,null,0));
        if(page.isEmpty()){TextView empty=new TextView(getContext());empty.setText("还没有收藏网址\n点击右上角＋收藏当前网页");empty.setPadding(dp(10),dp(24),dp(10),dp(24));rows.addView(empty);}
        for(Row row:page){LinearLayout line=new LinearLayout(getContext());line.setOrientation(LinearLayout.HORIZONTAL);line.setGravity(android.view.Gravity.CENTER_VERTICAL);line.setPadding(dp(10),dp(8),0,dp(8));LinearLayout labels=new LinearLayout(getContext());labels.setOrientation(LinearLayout.VERTICAL);TextView title=new TextView(getContext());title.setText(row.title);title.setTextSize(15);title.setMaxLines(2);TextView url=new TextView(getContext());url.setText(row.url);url.setTextSize(12);url.setMaxLines(1);url.setEllipsize(android.text.TextUtils.TruncateAt.END);labels.addView(title);labels.addView(url);labels.setMinimumHeight(dp(52));labels.setContentDescription("打开收藏："+row.title);labels.setOnClickListener(v->dispatch(frame,revision,row.itemRef,1));line.addView(labels,new LinearLayout.LayoutParams(0,-2,1));controls.add(labels);Button edit=button("编辑");edit.setContentDescription("编辑收藏："+row.title);edit.setOnClickListener(v->dispatch(frame,revision,row.itemRef,2));line.addView(edit,new LinearLayout.LayoutParams(dp(64),dp(52)));controls.add(edit);Button remove=button("删除");remove.setContentDescription("删除收藏："+row.title);remove.setOnClickListener(v->dispatch(frame,revision,row.itemRef,3));line.addView(remove,new LinearLayout.LayoutParams(dp(64),dp(52)));controls.add(remove);rows.addView(line,new LinearLayout.LayoutParams(-1,-2));}
    }
    private void dispatch(Object frame,long revision,Object item,int action){BrowserToolbar.Binding exact=binding;Actions callback=actions;if(exact==null || callback==null || pending || !state.current(frame,revision))return;try{exact.check();Row row=item==null?null:state.require(frame,revision,item);pending=true;for(View control:controls)control.setEnabled(false);BrowserToolbar.IntentOutcome outcome=BrowserToolbar.invokeNativeIntent(()->{exact.check();if(!state.current(frame,revision) || exact!=binding || callback!=actions)return;if(action==0)callback.add(revision);else if(action==1)callback.open(row,revision);else if(action==2)callback.edit(row,revision);else callback.remove(row,revision);});if(outcome==BrowserToolbar.IntentOutcome.CALLBACK_UNKNOWN)setContentDescription("收藏操作结果未知；请重新读取，不自动重试");}catch(RuntimeException stale){setContentDescription("收藏窗口已改变；未重试");}}
    public void unbind(){BrowserToolbar.main();binding=null;actions=null;pending=false;state.hide();controls.clear();add.setOnClickListener(null);add.setEnabled(false);count.setText("");BrowserToolbar.clearOwnedViewTree(rows);rows.removeAllViews();setContentDescription(null);}
    public void showUnavailable(String reason){unbind();count.setText(BrowserToolbar.boundedStatusReason(reason));}
    public void retire(){unbind();state.retire();}
    @Override protected void onDetachedFromWindow(){unbind();super.onDetachedFromWindow();}
    private Button button(String text){Button button=new Button(getContext());button.setText(text);button.setMinimumHeight(dp(48));return button;}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private static String text(String value,int maximum){if(value==null || value.isEmpty())throw bad();return io.github.rebrowser.core.DomainResult.text(value,maximum,false);}
    private static IllegalArgumentException bad(){return new IllegalArgumentException("Invalid native URL favorites list");}
}
