package io.github.rebrowser.ui;

import android.app.Activity;
import android.os.Bundle;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/**
 * Native download WINDOW SHELL over an already-computed TaskSnapshot page. Filtering is display
 * only (it keeps original rows/revision) and paging is a bounded REQUEST; the window never captures
 * a response, starts a transfer, publishes a file or sends an installer/open Intent. "已发布" is
 * only the native handoff offer combined with an observed end+commit, never inferred from 100%.
 */
public final class DownloadsActivity extends Activity {
    public enum PageDirection { PREVIOUS, NEXT }
    /** Native-provided availability; absent facts never become "available". */
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
    /** Display filter over the CURRENT page; conditions come from TaskSnapshot/offered facts. */
    public enum Filter {
        ALL { public boolean matches(DownloadsAdapter.Row row){return true;} },
        OCCUPIED {
            public boolean matches(DownloadsAdapter.Row row){
                return Boolean.TRUE.equals(row.workStillOwned()); // not derivable from phase/cancel
            }
        },
        PUBLISHED {
            public boolean matches(DownloadsAdapter.Row row){
                return Boolean.TRUE.equals(row.publicationReported()); // offered handoff != publication
            }
        },
        PENDING_APPROVAL {
            public boolean matches(DownloadsAdapter.Row row){
                return DownloadsAdapter.Rows.canRequestApproval(row);
            }
        };
        public abstract boolean matches(DownloadsAdapter.Row row);
        /** Bounded display-only projection preserving each row and the page revision. */
        public static List<DownloadsAdapter.Row> apply(List<DownloadsAdapter.Row> rows,Filter filter){
            if(rows==null || rows.size()>DownloadsAdapter.MAX_ROWS || filter==null)throw new IllegalArgumentException("Invalid download page/filter");
            List<DownloadsAdapter.Row> matched=new ArrayList<>();
            for(DownloadsAdapter.Row row:rows){if(row==null)throw new IllegalArgumentException("Missing download row");if(filter.matches(row))matched.add(row);}
            return matched;
        }
    }

    public interface Actions {
        void approveDownload(Object requestToken,long expectedRevision);
        void approveRow(DownloadsAdapter.Row row,long expectedRevision);
        void requestCancel(DownloadsAdapter.Row row,long expectedRevision);
        void requestHandoff(DownloadsAdapter.Row row,long expectedRevision);
        void requestPage(PageRequest request);
    }
    /** A pending native approval request; the token is opaque and is not a file grant. */
    public static final class ApprovalRequest {
        private final Object token;private final String displayName;private final long reportedBytes;
        public ApprovalRequest(Object token,String displayName,long reportedBytes){
            if(token==null || displayName==null || displayName.isEmpty() || displayName.length()>256)throw new IllegalArgumentException("Invalid approval request");
            if(reportedBytes< -1 || reportedBytes>1L<<40)throw new IllegalArgumentException("Invalid reported size");
            this.token=token;this.displayName=io.github.rebrowser.core.DomainResult.text(displayName,256,false);this.reportedBytes=reportedBytes;
        }
        public Object token(){return token;}
        public String displayName(){return displayName;}
        /** -1 = unknown; never coerced to zero or a fake 100%. */
        public long reportedBytes(){return reportedBytes;}
    }

    private TextView status;
    private ListView list;
    private DownloadsAdapter adapter;
    private BrowserToolbar.Binding binding;
    private Actions actions;
    private Object pageFrame;
    private long pageRevision;
    private List<DownloadsAdapter.Row> allRows=new ArrayList<>();
    private Filter filter=Filter.ALL;
    private PageNav pageNav=new PageNav(null,null);
    private PageDirection pendingPage;
    private Button approvalButton,previousPageButton,nextPageButton;
    private ApprovalRequest approvalRequest;
    private Actions approvalActions;
    private Object approvalFrame;
    private final java.util.Set<AttemptKey> rowAttempts=new java.util.HashSet<>();

    private static final class AttemptKey {
        final long revision;final Object itemRef;final DownloadsAdapter.RowAction action;
        AttemptKey(long revision,Object itemRef,DownloadsAdapter.RowAction action){this.revision=revision;this.itemRef=itemRef;this.action=action;}
        @Override public boolean equals(Object other){if(!(other instanceof AttemptKey))return false;AttemptKey key=(AttemptKey)other;return revision==key.revision && itemRef==key.itemRef && action==key.action;}
        @Override public int hashCode(){return Long.hashCode(revision)*31+action.hashCode();}
    }

    @Override protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        TextView header=new TextView(this);
        header.setText("下载");
        header.setTextSize(20);
        root.addView(header,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT));
        LinearLayout filters=new LinearLayout(this);
        filters.setOrientation(LinearLayout.HORIZONTAL);
        for(Filter option:Filter.values()){
            Button button=new Button(this);
            button.setText(filterLabel(option));
            button.setMinimumHeight(dp(48));
            button.setContentDescription("筛选下载：" + filterLabel(option));
            button.setOnClickListener(v->selectFilter(option));
            filters.addView(button,new LinearLayout.LayoutParams(0,dp(48),1));
        }
        root.addView(filters);
        status=new TextView(this);
        status.setText("未连接原生下载控制器");
        root.addView(status,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT));
        LinearLayout paging=new LinearLayout(this);previousPageButton=new Button(this);nextPageButton=new Button(this);previousPageButton.setText("上一页");nextPageButton.setText("下一页");previousPageButton.setMinimumHeight(dp(48));nextPageButton.setMinimumHeight(dp(48));previousPageButton.setOnClickListener(v->{if(actions!=null)requestPage(PageDirection.PREVIOUS,actions);});nextPageButton.setOnClickListener(v->{if(actions!=null)requestPage(PageDirection.NEXT,actions);});paging.addView(previousPageButton);paging.addView(nextPageButton);root.addView(paging);
        approvalButton=new Button(this);
        approvalButton.setText("批准此项下载");
        approvalButton.setMinimumHeight(dp(48));
        approvalButton.setEnabled(false);
        approvalButton.setOnClickListener(v->submitApproval());
        root.addView(approvalButton,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT));
        list=new ListView(this);
        adapter=new DownloadsAdapter(this);
        list.setAdapter(adapter);
        root.addView(list,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,0,1));
        setContentView(root);
    }

    /** Called ONLY by the native download owner with a current binding and one page. */
    public void showPage(BrowserToolbar.Binding binding,long pageRevision,List<DownloadsAdapter.Row> rows,Actions actions){
        showPage(binding,pageRevision,rows,new PageNav(null,null),actions);
    }

    public void showPage(BrowserToolbar.Binding binding,long pageRevision,List<DownloadsAdapter.Row> rows,PageNav nav,Actions actions){
        BrowserToolbar.main();
        if(binding==null || nav==null || actions==null)throw new IllegalArgumentException("Missing native download binding");
        binding.check();
        DownloadsAdapter.Rows.validate(rows,pageRevision);
        clearApproval(); // A new page invalidates the previous exact approval presentation.
        this.binding=binding;this.actions=actions;this.pageFrame=new Object();
        this.pageRevision=pageRevision;this.pageNav=nav;this.allRows=new ArrayList<>(rows);
        this.pendingPage=null;
        rowAttempts.clear();
        renderPage();
    }

    private void selectFilter(Filter next){
        BrowserToolbar.main();
        if(next==null)throw new IllegalArgumentException("Missing download filter");
        filter=next;
        if(binding!=null)renderPage();
    }

    private void renderPage(){
        if(binding==null || pageFrame==null)return;
        try{binding.check();}catch(SecurityException|IllegalStateException stale){showUnavailable("来源或窗口已失效，需重新读取");return;}
        List<DownloadsAdapter.Row> visible=Filter.apply(allRows,filter);
        List<DownloadsAdapter.Row> projected=new ArrayList<>(visible.size());
        for(DownloadsAdapter.Row row:visible)projected.add(reduceAttempted(row));
        final Actions exactOwner=actions;final Object exactFrame=pageFrame;final BrowserToolbar.Binding exactBinding=binding;
        adapter.bind(binding,pageRevision,projected,new DownloadsAdapter.Actions(){
            public void requestApproval(DownloadsAdapter.Row row,long revision){
                Actions owner=exactOwner;if(owner==null || pageFrame!=exactFrame || binding!=exactBinding || actions!=owner)return;
                if(!rowAttempts.add(new AttemptKey(revision,row.itemRef(),DownloadsAdapter.RowAction.APPROVE)))return;
                owner.approveRow(row,revision);
            }
            public void requestCancel(DownloadsAdapter.Row row,long revision){
                Actions owner=exactOwner;if(owner==null || pageFrame!=exactFrame || binding!=exactBinding || actions!=owner)return;
                if(!rowAttempts.add(new AttemptKey(revision,row.itemRef(),DownloadsAdapter.RowAction.CANCEL)))return;
                owner.requestCancel(row,revision);
            }
            public void requestHandoff(DownloadsAdapter.Row row,long revision){
                Actions owner=exactOwner;if(owner==null || pageFrame!=exactFrame || binding!=exactBinding || actions!=owner)return;
                if(!rowAttempts.add(new AttemptKey(revision,row.itemRef(),DownloadsAdapter.RowAction.HANDOFF)))return;
                owner.requestHandoff(row,revision);
            }
        });
        status.setText(pageStatus(visible.size()));
        previousPageButton.setEnabled(pendingPage==null && pageNav.available(PageDirection.PREVIOUS));nextPageButton.setEnabled(pendingPage==null && pageNav.available(PageDirection.NEXT));
    }

    /** Rows already attempted in this page keep their token but lose the attempted offered action. */
    private DownloadsAdapter.Row reduceAttempted(DownloadsAdapter.Row row){java.util.EnumSet<DownloadsAdapter.RowAction> suppressed=java.util.EnumSet.noneOf(DownloadsAdapter.RowAction.class);for(DownloadsAdapter.RowAction action:DownloadsAdapter.RowAction.values())if(rowAttempts.contains(new AttemptKey(pageRevision,row.itemRef(),action)))suppressed.add(action);return suppressed.isEmpty()?row:row.suppressUiActions(suppressed);}

    private String pageStatus(int visibleRows){
        StringBuilder text=new StringBuilder();
        text.append("显示 ").append(visibleRows).append(" / ").append(allRows.size()).append(" 行");
        text.append(" · 筛选：").append(filterLabel(filter));
        if(pageNav.previousAvailable()==null && pageNav.nextAvailable()==null)text.append(" · 翻页状态未知");
        else text.append(" · 上一页").append(pageNav.available(PageDirection.PREVIOUS)?"可用":"不可用")
                .append("，下一页").append(pageNav.available(PageDirection.NEXT)?"可用":"不可用");
        if(pendingPage!=null)text.append(" · 已请求翻页，等待原生结果");
        text.append(" · 状态以原生任务轴为准");
        return text.toString();
    }

    /** A separate explicit control; clicking a download list row cannot approve anything. */
    public void showApprovalRequest(ApprovalRequest request,Actions actions){
        BrowserToolbar.main();
        BrowserToolbar.Binding captured=binding;
        if(captured==null || actions==null || actions!=this.actions)throw new IllegalStateException("Download window is not connected");
        captured.check();
        if(request==null)throw new IllegalArgumentException("Missing approval request");
        clearApproval();
        approvalRequest=request;
        approvalActions=actions;
        approvalFrame=new Object();
        approvalButton.setEnabled(true);
        status.setText("待批准：" + request.displayName() + (request.reportedBytes()<0?"（大小未知）":"（" + request.reportedBytes() + " 字节）") + " · 请使用独立按钮确认");
    }

    /** One submission per exact displayed request, even if the native outcome is unknown. */
    private void submitApproval(){
        BrowserToolbar.main();
        final BrowserToolbar.Binding captured=binding;
        final ApprovalRequest exactRequest=approvalRequest;
        final Actions exactActions=approvalActions;
        final Object exactFrame=approvalFrame;
        final long exactRevision=pageRevision;final Object exactPageFrame=pageFrame;
        if(captured==null || exactRequest==null || exactActions==null || exactFrame==null)return;
        clearApproval();
        BrowserToolbar.IntentOutcome outcome=BrowserToolbar.invokeNativeIntent(()->{
            captured.check();
            if(binding!=captured || pageRevision!=exactRevision || pageFrame!=exactPageFrame || actions!=exactActions)return;
            exactActions.approveDownload(exactRequest.token(),exactRevision);
        });
        if(outcome==BrowserToolbar.IntentOutcome.CALLBACK_UNKNOWN && binding==captured && pageFrame==exactPageFrame)
            status.setText("批准请求结果未知，请从原生下载任务重新读取，不自动重试");
    }

    private void clearApproval(){
        approvalRequest=null;approvalActions=null;approvalFrame=null;
        if(approvalButton!=null)approvalButton.setEnabled(false);
    }

    /** One paging request per page revision; unknown outcome keeps paging blocked. */
    public void requestPage(PageDirection direction,Actions actions){
        BrowserToolbar.main();
        BrowserToolbar.Binding captured=binding;
        if(captured==null || actions==null || this.actions!=actions || pageFrame==null)
            throw new IllegalStateException("Download window is not connected");
        if(direction==null)throw new IllegalArgumentException("Missing page direction");
        if(pendingPage!=null || !pageNav.available(direction))return;
        final Object expectedFrame=pageFrame;final long expectedRevision=pageRevision;
        pendingPage=direction;previousPageButton.setEnabled(false);nextPageButton.setEnabled(false);
        status.setText(pageStatus(Filter.apply(allRows,filter).size()));
        BrowserToolbar.IntentOutcome outcome=BrowserToolbar.invokeNativeIntent(()->{
            captured.check();
            if(pageFrame!=expectedFrame || binding!=captured || this.actions!=actions || pageRevision!=expectedRevision)return;
            actions.requestPage(new PageRequest(direction,expectedRevision));
        });
        if(outcome==BrowserToolbar.IntentOutcome.CALLBACK_UNKNOWN && pageFrame==expectedFrame)
            status.setText("翻页请求结果未知，请重新读取下载页；不会自动重试");
    }

    public void showUnavailable(String reason){
        BrowserToolbar.main();
        clearApproval();adapter.unbind();binding=null;actions=null;pageFrame=null;allRows=new ArrayList<>();pendingPage=null;rowAttempts.clear();
        previousPageButton.setEnabled(false);nextPageButton.setEnabled(false);status.setText(reason==null || reason.isEmpty()?"未连接原生下载控制器":"下载不可用：" + BrowserToolbar.boundedStatusReason(reason));
    }

    @Override protected void onDestroy(){
        clearApproval();
        if(adapter!=null)adapter.retire();
        binding=null;actions=null;pageFrame=null;rowAttempts.clear();allRows.clear();if(list!=null)list.setAdapter(null);BrowserToolbar.clearOwnedViewTree(findViewById(android.R.id.content));
        super.onDestroy();
    }

    public static String filterLabel(Filter filter){
        if(filter==null)throw new IllegalArgumentException("Missing download filter");
        switch(filter){case ALL:return "全部";case OCCUPIED:return "仍占用";case PUBLISHED:return "已发布（原生提供）";default:return "待批准";}
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
