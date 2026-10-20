package io.github.rebrowser.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;
import io.github.rebrowser.ReBrowserApplication;
import io.github.rebrowser.bootstrap.ComponentGate;
import io.github.rebrowser.bootstrap.StartupCoordinator;
import io.github.rebrowser.bootstrap.BrowserStoreCoordinator;
import io.github.rebrowser.browser.BackCoordinator;
import io.github.rebrowser.engine.ViewportPolicy;
import io.github.rebrowser.entrypoints.AdminControlActivity;

/**
 * Actual native entry-window lifecycle. Locked startup composes ONLY minimal shell views;
 * no DB/profile/session/URL restore or disabled business controls masquerading as a browser.
 * Native toolbar/panels need verified source epoch/domain/finite-permission factory before use.
 */
public final class BrowserActivity extends Activity {
    private StartupCoordinator startup;private TextView status,geometry;private Button inspectRetained;
    private BrowserStoreCoordinator catalogInspector;private BrowserStoreCoordinator.Callback catalogCallback;private Object catalogFrame;private boolean catalogAttempted;
    private WindowPresentation presentation;private NativeDialogs dialogs;private OnBackInvokedCallback back;private BackCoordinator nativeBack;private BackCoordinator.Reporter closingWindow;
    @Override public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        if(!(getApplication() instanceof ReBrowserApplication)){finish();return;}
        startup=((ReBrowserApplication)getApplication()).primaryStartup();if(!startup.gate().mayShowNativeShell()){finish();return;}
        presentation=new WindowPresentation(this);dialogs=new NativeDialogs(this);
        nativeBack=new BackCoordinator(startup.gate(),new BackCoordinator.NativeWindow(){
            @Override public boolean present(BackCoordinator.Layer layer){if(layer==BackCoordinator.Layer.MODAL)return dialogs!=null && dialogs.hasPending();if(layer==BackCoordinator.Layer.FULLSCREEN)return presentation!=null && presentation.isOwnedFullscreen();return layer==BackCoordinator.Layer.WINDOW_CLOSE && !isFinishing();}
            @Override public void perform(BackCoordinator.Layer layer,BackCoordinator.Reporter reporter){
                if(layer==BackCoordinator.Layer.MODAL){if(dialogs==null || !dialogs.requestOwnedCancel(reporter::reportConsumed))reporter.reportNoOp();}
                else if(layer==BackCoordinator.Layer.FULLSCREEN){presentation.leaveOwnedFullscreen();reporter.reportConsumed();}
                else if(layer==BackCoordinator.Layer.WINDOW_CLOSE){closingWindow=reporter;finish();} // actual onDestroy reports this owned-window end
                else reporter.reportNoOp();
            }
        });
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);int pad=Math.round(16*getResources().getDisplayMetrics().density);root.setPadding(pad,pad,pad,pad);
        TextView title=new TextView(this);title.setText("ReBrowser");title.setTextSize(24);root.addView(title);
        status=new TextView(this);status.setTextSize(16);root.addView(status);
        geometry=new TextView(this);root.addView(geometry);
        Button inspect=button("重新读取本窗口状态");inspect.setOnClickListener(v->showNativeState());root.addView(inspect);
        inspectRetained=button("检查保留列表元数据（不恢复）");inspectRetained.setOnClickListener(v->inspectRetainedCatalog());root.addView(inspectRetained);
        Button info=button("开发状态说明");info.setOnClickListener(v->{try{dialogs.showInformation("原生工具栏、双层标签面板和进度组件已开始实现。来源连续性、完整主窗口接线与实际网页运行仍未完成；本页不会解锁、恢复旧数据或自动打开链接。");}catch(RuntimeException unknown){status.setText("原生说明窗口状态未知；不会自动重复打开。");}});root.addView(info);
        Button admin=button("管理员检查（不执行命令）");admin.setOnClickListener(v->{if(!isFinishing() && !isDestroyed() && !dialogs.hasPending())startActivity(new Intent(this,AdminControlActivity.class));});root.addView(admin);
        setContentView(root);presentation.attachNativeContent(root);showNativeState();
        if(Build.VERSION.SDK_INT>=33){back=this::ownedBack;getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT,back);}
        // Intent.data/extras/savedInstanceState never become a load, restore, grant or source decision.
    }
    @Override protected void onResume(){super.onResume();if(status!=null)showNativeState();}
    @Override protected void onStop(){catalogFrame=null;catalogCallback=null;if(catalogInspector!=null)catalogInspector.retireRecipient();if(nativeBack!=null)nativeBack.presentationChanged();if(dialogs!=null)dialogs.cancelOwned();super.onStop();}
    @Override protected void onDestroy(){
        if(Build.VERSION.SDK_INT>=33 && back!=null){getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(back);back=null;}
        if(nativeBack!=null)nativeBack.retireWindowOwner();
        if(dialogs!=null){dialogs.retireWindow();dialogs=null;}
        if(presentation!=null){presentation.close();presentation=null;}
        if(closingWindow!=null){closingWindow.reportConsumed();closingWindow=null;}nativeBack=null;
        BrowserToolbar.clearOwnedViewTree(findViewById(android.R.id.content));catalogCallback=null;catalogFrame=null;catalogInspector=null;inspectRetained=null;
        status=null;geometry=null;super.onDestroy();
    }
    @Override public void onBackPressed(){ownedBack();}
    private void ownedBack(){
        if(nativeBack==null || nativeBack.hasPendingLayer())return;
        try{nativeBack.request(nativeBack.presentationRevision(),null);}catch(IllegalStateException stale){/* no alternate layer/global fallback */}
    }
    private void showNativeState(){
        ComponentGate.SourceState source=startup.gate().sourceState();
        if(inspectRetained!=null)inspectRetained.setEnabled(!catalogAttempted && source==ComponentGate.SourceState.TRUSTED);
        status.setText(source==ComponentGate.SourceState.UNCONFIGURED?"正式来源及管理员根尚未配置，网页业务保持锁定。":"网页来源闸门："+source.name()+"；完整原生浏览器接线尚未就绪。");
        try{ViewportPolicy.Snapshot view=ViewportPolicy.inspectOwnWindow(this);geometry.setText("本窗口 "+view.widthDp()+" × "+view.heightDp()+" dp · "+view.widthClass().name()+(view.narrowWindowOnLargeDisplay()?" · 小窗":""));}catch(RuntimeException unavailable){geometry.setText("当前窗口几何信息不可用");}
    }
    private void inspectRetainedCatalog(){
        if(catalogAttempted || isFinishing() || isDestroyed())return;
        final ComponentGate.BusinessPermit permit;
        try{permit=startup.gate().requireBusiness();}catch(RuntimeException locked){showNativeState();return;}
        catalogAttempted=true;inspectRetained.setEnabled(false);Object frame=new Object();catalogFrame=frame;
        try{
            catalogInspector=startup.browserCatalogInspector(permit);
            catalogCallback=report->{if(catalogFrame!=frame || isFinishing() || isDestroyed() || status==null)return;
                switch(report.status()){
                    case AUTHENTICATED_METADATA_NOT_LATEST:status.setText("已认证保留元数据："+report.workspaceCount()+"个总标签页 / "+report.tabCount()+"个子标签页；未证明最新，未恢复会话。");break;
                    case MISSING_NOT_FRESH:status.setText("保留列表记录缺失，不等于首次安装；未创建记录。");break;
                    case UNFINISHED_WRITE:status.setText("存在未完成的保留列表写入；未自动修复或恢复。");break;
                    default:status.setText("保留元数据读取不可用；未恢复数据或重试。");
                }
                catalogCallback=null;
            };
            status.setText("正在独立读取保留元数据；不会恢复会话或打开网页。");catalogInspector.inspectOnce(catalogCallback,permit);
        }catch(RuntimeException unknown){catalogCallback=null;catalogFrame=null;if(status!=null)status.setText("保留元数据检查状态未知；不会自动重试。");}
    }
    private Button button(String text){Button button=new Button(this);button.setText(text);button.setMinimumHeight(Math.round(48*getResources().getDisplayMetrics().density));return button;}
}
