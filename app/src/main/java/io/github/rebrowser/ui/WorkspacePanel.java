package io.github.rebrowser.ui;

import android.content.Context;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import io.github.rebrowser.browser.BrowserModels.*;

/** Bounded native dual-level workspace overview. No Gecko open/remove/erasure in a view. */
public final class WorkspacePanel extends ScrollView {
    public interface Actions {
        void createTemporary(long expectedCatalogRevision);
        void select(WorkspaceRef ref,long expectedCatalogRevision);
        void restore(WorkspaceRef ref,long expectedWorkspaceRevision);
        void edit(WorkspaceRef ref,long expectedWorkspaceRevision);
        void closeOrShelf(WorkspaceRef ref,long expectedWorkspaceRevision);
    }
    public static final class Rows {
        public static void validate(WorkspacesView view){if(view==null || view.active().size()+view.shelved().size()>64)throw new IllegalArgumentException("Invalid native workspace rows");}
        public static String description(WorkspaceView workspace){if(workspace==null)throw new IllegalArgumentException("Missing native workspace");String level=workspace.level()==Level.PRIMARY?"主级":workspace.level()==Level.SECONDARY?"次级":"临时";return workspace.title()+" · "+level+(workspace.placement()==Placement.SHELVED?" · 已收起":"");}
        public static boolean closeEnabled(WorkspaceView workspace){return workspace.level()!=Level.PRIMARY;}
    }
    private final LinearLayout rows;private Object frame;
    public WorkspacePanel(Context context){super(context);BrowserToolbar.main();setFillViewport(true);rows=new LinearLayout(context);rows.setOrientation(LinearLayout.VERTICAL);addView(rows,new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT));}
    public void bind(BrowserToolbar.Binding binding,WorkspacesView snapshot,Actions actions){
        BrowserToolbar.main();if(binding==null || actions==null)throw new IllegalArgumentException("Missing native workspace binding");binding.check();Rows.validate(snapshot);unbind();final Object current=new Object();frame=current;
        Button create=button("新建临时总标签页");create.setOnClickListener(v->run(current,binding,()->actions.createTemporary(snapshot.revision())));rows.addView(create);
        for(WorkspaceView workspace:snapshot.active())addRow(current,binding,snapshot,workspace,actions,false);
        if(!snapshot.shelved().isEmpty()){TextView title=new TextView(getContext());title.setText("已收起的次级总标签页");title.setTextSize(16);rows.addView(title);for(WorkspaceView workspace:snapshot.shelved())addRow(current,binding,snapshot,workspace,actions,true);}
    }
    private void addRow(Object current,BrowserToolbar.Binding binding,WorkspacesView snapshot,WorkspaceView workspace,Actions actions,boolean shelved){
        LinearLayout row=new LinearLayout(getContext());row.setOrientation(LinearLayout.VERTICAL);
        Button select=button(Rows.description(workspace));select.setSelected(snapshot.selected()==workspace.ref());select.setOnClickListener(v->run(current,binding,()->{if(shelved)actions.restore(workspace.ref(),workspace.revision());else actions.select(workspace.ref(),snapshot.revision());}));row.addView(select);
        LinearLayout options=new LinearLayout(getContext());Button edit=button("名称/级别");edit.setOnClickListener(v->run(current,binding,()->actions.edit(workspace.ref(),workspace.revision())));options.addView(edit,new LinearLayout.LayoutParams(0,dp(48),1));
        Button close=button(workspace.level()==Level.SECONDARY && !shelved?"收起":"移除");close.setEnabled(Rows.closeEnabled(workspace));close.setOnClickListener(v->run(current,binding,()->actions.closeOrShelf(workspace.ref(),workspace.revision())));options.addView(close,new LinearLayout.LayoutParams(0,dp(48),1));row.addView(options);rows.addView(row);
    }
    private void run(Object expected,BrowserToolbar.Binding binding,Runnable action){if(frame!=expected)return;try{binding.check();if(frame==expected)action.run();}catch(SecurityException stale){unbind();}catch(RuntimeException unknown){setContentDescription("操作结果未知，请重新读取总标签状态");}}
    public void unbind(){BrowserToolbar.main();frame=null;rows.removeAllViews();setContentDescription(null);}
    @Override protected void onDetachedFromWindow(){unbind();super.onDetachedFromWindow();}
    private Button button(String label){Button button=new Button(getContext());button.setText(label);button.setMinimumHeight(dp(48));return button;}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
