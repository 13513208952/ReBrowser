package io.github.rebrowser.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import android.graphics.drawable.GradientDrawable;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;
import io.github.rebrowser.R;
import io.github.rebrowser.ReBrowserApplication;
import io.github.rebrowser.bootstrap.ComponentGate;
import io.github.rebrowser.bootstrap.StartupCoordinator;
import io.github.rebrowser.browser.BackCoordinator;
import io.github.rebrowser.browser.BrowserCoordinator;
import io.github.rebrowser.browser.BrowserModels.*;
import io.github.rebrowser.browser.SearchEngines;
import io.github.rebrowser.data.OwnedDataCatalog;
import io.github.rebrowser.engine.GeckoSessionOwner;
import io.github.rebrowser.engine.SessionDisplayBinding;
import io.github.rebrowser.engine.SessionPresentation;
import io.github.rebrowser.entrypoints.AdminControlActivity;
import io.github.rebrowser.settings.SettingsRepository;
import java.lang.ref.WeakReference;
import org.mozilla.geckoview.GeckoView;

/** Actual native browser window, not an alternate browser/backend. Business views ONLY after
 * independent source acceptance. No Intent/Bundle/test key creates a source permit or URL load.
 * The current native factory supports temporary environments; persistence/Popup/permissions
 * remain explicitly pending. This source wiring is not an installed/demo/runtime qualification.
 */
public final class BrowserActivity extends Activity {
    private StartupCoordinator startup;private ComponentGate.BusinessPermit permit;
    private WindowPresentation presentation;private NativeDialogs dialogs;private BackCoordinator nativeBack;private BackCoordinator.Reporter closingWindow,pendingHistoryBack;
    private OnBackInvokedCallback systemBack;private boolean foreground,renderQueued,browserComposed,browserCompositionAttempted;
    private final Handler main=new Handler(Looper.getMainLooper());
    private BrowserCoordinator browser;private BrowserCoordinator.WindowSubscription watch;private StartupCoordinator.EntrySubscription entryWatch;
    private BrowserToolbar toolbar;private NewTabView newTab;private GeckoView gecko;private SessionDisplayBinding display;private GeckoSessionOwner.SessionRef displayedRef;
    private FrameLayout root,content,overview;private TextView overviewTitle,lockedStatus,sourceWarning,sessionWarning;private WorkspacePanel workspacePanel;private TabPanel tabPanel;
    private boolean childOverview;private BrowserToolbar.Binding binding,newTabBinding;private TabRef boundTab;private Object windowFrame;
    private LoadingProgressView progress;private LoadingProgressView.LoadRef progressRef;private SessionPresentation.LoadObservation progressObservation;private GeckoSessionOwner.SessionRef progressSession;
    private PopupWindow menu;private AlertDialog editor;private SettingsRepository.Snapshot preferences;private Object preferencesRequest;
    private long exitArmedAt=-1,backLocationSequence;private GeckoSessionOwner.SessionRef backSession;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);if(!(getApplication() instanceof ReBrowserApplication)){finish();return;}
        startup=((ReBrowserApplication)getApplication()).primaryStartup();if(!startup.gate().mayShowNativeShell()){finish();return;}
        presentation=new WindowPresentation(this);dialogs=new NativeDialogs(this);
        nativeBack=new BackCoordinator(startup.gate(),new BackCoordinator.NativeWindow(){
            public boolean present(BackCoordinator.Layer layer){return backLayerPresent(layer);}
            public void perform(BackCoordinator.Layer layer,BackCoordinator.Reporter reporter){performBackLayer(layer,reporter);}
        });
        showLockedShell();
        if(Build.VERSION.SDK_INT>=33){systemBack=this::ownedBack;getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT,systemBack);}
        // External URL intents and savedInstanceState need their own native target routing; they
        // are NOT silently treated as a current browser load/source acceptance in this window.
    }
    private void showLockedShell(){
        LinearLayout shell=new LinearLayout(this);shell.setOrientation(LinearLayout.VERTICAL);int pad=dp(16);shell.setPadding(pad,pad,pad,pad);shell.setBackgroundColor(0xffffffff);
        TextView title=new TextView(this);title.setText("ReBrowser");title.setTextSize(24);shell.addView(title);
        lockedStatus=new TextView(this);shell.addView(lockedStatus);
        Button refresh=new Button(this);refresh.setText("重新读取来源状态");refresh.setOnClickListener(v->refreshEntry());shell.addView(refresh);
        Button explanation=new Button(this);explanation.setText("当前开发状态");explanation.setOnClickListener(v->information("主浏览器原生窗口已接到同一浏览器域，但首次来源部署、实际设备运行、持久环境恢复和完整权限尚需闭合。本窗口不会用公钥配置、首次点击或调试参数伪造业务解锁。"));shell.addView(explanation);
        Button admin=new Button(this);admin.setText("管理员检查（不执行命令）");admin.setOnClickListener(v->startActivity(new Intent(this,AdminControlActivity.class)));shell.addView(admin);
        setContentView(shell);presentation.attachNativeContent(shell);updateLockedStatus();
    }
    private void updateLockedStatus(){if(lockedStatus!=null)lockedStatus.setText("来源状态："+startup.gate().sourceState().name()+"\n配置管理员公钥不等于来源接受。业务保持锁定，未读取或初始化私有记录。");}
    private void refreshEntry(){
        if(!foreground || isFinishing() || isDestroyed())return;
        if(startup.gate().sourceState()!=ComponentGate.SourceState.TRUSTED){if(browserComposed)lockCurrentWindow();updateLockedStatus();return;}
        try{
            permit=startup.gate().requireBusiness();browser=startup.temporaryBrowserDomain(permit);
            if(!browserComposed){if(browserCompositionAttempted)throw new IllegalStateException("Native composition outcome unavailable; no automatic alternate view");browserCompositionAttempted=true;composeBrowser();}
            if(watch==null){WeakReference<BrowserActivity> weak=new WeakReference<>(this);watch=browser.observeNativeWindow(()->{BrowserActivity window=weak.get();if(window!=null)window.scheduleRender();},permit);}
            readPreferences();renderBrowser();
        }catch(RuntimeException unavailable){if(!browserComposed)updateLockedStatus();else toast("浏览器窗口暂不可用；未重建会话或绕过来源检查");}
    }
    private void composeBrowser(){
        presentation.close();presentation=new WindowPresentation(this);
        root=new FrameLayout(this);root.setBackgroundColor(0xffffffff);LinearLayout column=new LinearLayout(this);column.setOrientation(LinearLayout.VERTICAL);root.addView(column,new FrameLayout.LayoutParams(-1,-1));
        toolbar=new BrowserToolbar(this);column.addView(toolbar,new LinearLayout.LayoutParams(-1,-2));content=new FrameLayout(this);column.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        gecko=new GeckoView(this);content.addView(gecko,new FrameLayout.LayoutParams(-1,-1));gecko.setVisibility(View.GONE);display=browser.sessionDisplayAccess(permit).bind(gecko);
        newTab=new NewTabView(this);content.addView(newTab,new FrameLayout.LayoutParams(-1,-1));newTab.shareNativeBackdrop(root,photo->{if(toolbar!=null)toolbar.appearance(photo);if(presentation!=null && !isFinishing() && !isDestroyed())presentation.photoForeground(photo);});
        sessionWarning=new TextView(this);sessionWarning.setGravity(Gravity.CENTER);sessionWarning.setTextSize(16);sessionWarning.setBackgroundColor(0xffffffff);sessionWarning.setPadding(dp(24),dp(24),dp(24),dp(24));sessionWarning.setText("当前网页会话已退出或尚待显式恢复。\n未自动重开、换路重发或伪报恢复。\n点此打开总标签概览，可切换或关闭此子页。");sessionWarning.setOnClickListener(v->{try{showWorkspaces();}catch(RuntimeException stale){lockCurrentWindow();}});sessionWarning.setVisibility(View.GONE);content.addView(sessionWarning,new FrameLayout.LayoutParams(-1,-1));
        overview=new FrameLayout(this);overview.setBackgroundColor(0xfff7f8fc);LinearLayout panels=new LinearLayout(this);panels.setOrientation(LinearLayout.VERTICAL);overview.addView(panels,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);TextView back=new TextView(this);back.setText("‹");back.setTextSize(32);back.setGravity(Gravity.CENTER);back.setContentDescription("返回上一级概览或网页");back.setOnClickListener(v->backOverview());heading.addView(back,new LinearLayout.LayoutParams(dp(48),dp(52)));overviewTitle=new TextView(this);overviewTitle.setTextSize(19);heading.addView(overviewTitle,new LinearLayout.LayoutParams(0,-2,1));panels.addView(heading,new LinearLayout.LayoutParams(-1,dp(56)));
        workspacePanel=new WorkspacePanel(this);tabPanel=new TabPanel(this);FrameLayout panelBody=new FrameLayout(this);panelBody.addView(workspacePanel,new FrameLayout.LayoutParams(-1,-1));panelBody.addView(tabPanel,new FrameLayout.LayoutParams(-1,-1));panels.addView(panelBody,new LinearLayout.LayoutParams(-1,0,1));content.addView(overview,new FrameLayout.LayoutParams(-1,-1));overview.setVisibility(View.GONE);
        sourceWarning=new TextView(this);sourceWarning.setGravity(Gravity.CENTER);sourceWarning.setTextSize(16);sourceWarning.setTextColor(0xff31353d);sourceWarning.setBackgroundColor(0xffffffff);sourceWarning.setText("来源或窗口许可已失效，网页与私有内容已隐藏。\n需完成对应来源恢复后才能继续，不会自动重建或覆盖数据。");sourceWarning.setPadding(dp(24),dp(24),dp(24),dp(24));sourceWarning.setVisibility(View.GONE);content.addView(sourceWarning,new FrameLayout.LayoutParams(-1,-1));
        progress=new LoadingProgressView(this);FrameLayout.LayoutParams line=new FrameLayout.LayoutParams(-1,dp(3),Gravity.TOP);line.topMargin=dp(62);root.addView(progress,line);progress.setElevation(dp(8));
        setContentView(root);presentation.attachNativeContent(root);browserComposed=true;lockedStatus=null;
        if(browser.workspaces(permit).active().isEmpty())browser.createTemporary(browser.workspaces(permit).revision(),permit);
    }
    private void scheduleRender(){
        if(renderQueued || !foreground || !browserComposed)return;renderQueued=true;WeakReference<BrowserActivity> weak=new WeakReference<>(this);
        if(!main.post(()->{BrowserActivity window=weak.get();if(window==null)return;window.renderQueued=false;if(window.foreground)window.renderBrowser();}))renderQueued=false;
    }
    private void readPreferences(){
        Object request=new Object();preferencesRequest=request;WeakReference<BrowserActivity> weak=new WeakReference<>(this);Handler handler=main;StartupCoordinator owner=startup;ComponentGate.BusinessPermit current=permit;
        try{startup.executeBrowserDataIo(current,()->{
            SettingsRepository.Snapshot result=null;try{SettingsRepository repository=owner.openExistingSettingsOnIo(current);try(OwnedDataCatalog.View<SettingsRepository.Snapshot> view=repository.read(current)){result=view.value(current);}owner.gate().checkCurrent(current);}catch(Exception unavailable){/* missing settings is NOT a default/seed */}
            SettingsRepository.Snapshot observed=result;handler.post(()->{BrowserActivity window=weak.get();if(window==null || !window.foreground || window.preferencesRequest!=request || window.isDestroyed() || window.isFinishing())return;try{owner.gate().checkCurrent(current);window.preferences=observed;if(observed==null)window.toast("设置记录尚未就绪，未写入默认配置");}catch(RuntimeException stale){window.lockCurrentWindow();}});
        });}catch(RuntimeException unavailable){toast("设置读取未能提交，未自动重试");}
    }
    private void renderBrowser(){
        if(!foreground || !browserComposed)return;
        try{
            startup.gate().checkCurrent(permit);sourceWarning.setVisibility(View.GONE);sessionWarning.setVisibility(View.GONE);BrowserCoordinator.WindowSnapshot snapshot=browser.windowSnapshot(permit);
            if(snapshot.workspaces().active().isEmpty()){browser.createTemporary(snapshot.workspaces().revision(),permit);snapshot=browser.windowSnapshot(permit);}
            TabsView tabs=snapshot.selectedWorkspaceTabs();if(tabs==null)throw new IllegalStateException("No selected native environment");TabRef selected=tabs.selected();
            if(binding==null || boundTab!=selected){nativeBack.presentationChanged();detachDisplay();retireBinding();boundTab=selected;Object exact=windowFrame=new Object();binding=new BrowserToolbar.Binding(startup.gate(),permit,()->{if(!foreground || isFinishing() || isDestroyed() || windowFrame!=exact || browser.captureSelection(permit).tab()!=selected)throw new SecurityException("Retired native browser window/selection");});}
            Selection selection=browser.captureSelection(permit);SessionPresentation.Frame frame=browser.selectedPresentation(selection,permit);SearchEngines.Target requested=browser.selectedRequestedTarget(selection,permit);
            TabView current=null;for(TabView tab:tabs.tabs())if(tab.ref()==selected)current=tab;if(current==null)throw new IllegalStateException("Missing selected child");
            String url=requested!=null?requested.url():frame!=null && frame.reportedLocation()!=null?frame.reportedLocation():current.displayUrl();boolean blank=url==null || url.isEmpty() || "about:blank".equalsIgnoreCase(url);
            if(blank){
                detachDisplay();gecko.setVisibility(View.GONE);newTab.setVisibility(View.VISIBLE);
                if(newTabBinding!=binding){BrowserToolbar.Binding exact=binding;newTab.bindNative(startup,permit,exact,target->{exact.check();navigate(target);});newTabBinding=exact;}progress.unbind();progressRef=null;progressObservation=null;progressSession=null;
            }else{
                if(newTabBinding!=null){newTab.unbind();newTabBinding=null;}newTab.setVisibility(View.GONE);gecko.setVisibility(View.VISIBLE);
                GeckoSessionOwner.SessionRef session=browser.existingSelectedSession(browser.captureSelection(permit),permit);
                if(session==null){detachDisplay();gecko.setVisibility(View.GONE);sessionWarning.setVisibility(View.VISIBLE);progress.unbind();progressRef=null;progressObservation=null;progressSession=null;}
                else{display.attach(session,permit);displayedRef=session;display.updateVisibility(true,hasWindowFocus(),false,permit);}
                if(session!=null && frame!=null && frame.reportedLoad()!=null){if(progressObservation!=frame.reportedLoad() || progressSession!=session){progressObservation=frame.reportedLoad();progressSession=session;progressRef=progress.beginSessionReportedLoad(binding,session,frame.reportedLoad());}if(frame.loadingObserved())progress.report(progressRef,frame.reportedProgress());else progress.pageStopped(progressRef);}
            }
            toolbar.bind(binding,toolbarActions,url,frame!=null && frame.reportedCanGoBack(),blank,snapshot.workspaces().active().size());
            if(overview.getVisibility()==View.VISIBLE)renderOverview(snapshot);
            if(pendingHistoryBack!=null){SessionPresentation.Frame original=browser.observeOwnedSession(backSession,permit);if(original!=null && original.reportedLocationSequence()>backLocationSequence){pendingHistoryBack.reportConsumed();pendingHistoryBack=null;backSession=null;}else if(browser.ownedSessionEnded(backSession,permit)){pendingHistoryBack.reportFailed();pendingHistoryBack=null;backSession=null;}}
        }catch(SecurityException locked){lockCurrentWindow();}catch(RuntimeException unavailable){toast("当前窗口状态未确认，未重开会话或重发导航");}
    }
    private void lockCurrentWindow(){if(sourceWarning!=null)sourceWarning.setVisibility(View.VISIBLE);if(gecko!=null)gecko.setVisibility(View.GONE);if(overview!=null)overview.setVisibility(View.GONE);if(watch!=null){watch.close();watch=null;}try{detachDisplay();}catch(RuntimeException unknown){/* retain the actual display owner; no fake close/end */}retireBinding();preferences=null;preferencesRequest=null;if(menu!=null)menu.dismiss();toast("来源或窗口许可已失效，浏览操作已停止");}
    private void retireBinding(){windowFrame=null;if(binding!=null)binding.retire();binding=null;newTabBinding=null;boundTab=null;if(newTab!=null)newTab.unbind();if(toolbar!=null)toolbar.unbind();if(progress!=null)progress.unbind();progressRef=null;progressObservation=null;progressSession=null;}
    private void detachDisplay(){if(display!=null && display.isAttached()){display.hideOwnedSession();display.detach();}displayedRef=null;}
    private void requireWindow(){if(binding==null || !foreground)throw new SecurityException("Native window unavailable");binding.check();}
    private void navigate(SearchEngines.Target target){
        requireWindow();Selection original=browser.captureSelection(permit);exitArmedAt=-1;
        try{browser.navigateSelected(original,target,permit);}catch(RuntimeException unknown){toast("导航请求结果未确认，未使用其他路径补发");}finally{hideOverview();scheduleRender();}
    }
    private final BrowserToolbar.Actions toolbarActions=new BrowserToolbar.Actions(){
        public void submit(String input){requireWindow();if(preferences==null){try{navigate("about:blank".equals(input)?SearchEngines.newTab():SearchEngines.requireWebUrl(input));}catch(IllegalArgumentException unknown){toast("默认搜索设置尚未读取；未猜测搜索引擎");}return;}navigate(SearchEngines.resolveInput(input,preferences.searchProvider()));}
        public void home(){requireWindow();if(preferences==null){toast("主页设置尚未读取");return;}navigate(preferences.homeTarget());}
        public void addChild(){createChild();}public void back(){ordinaryBack();}public void reload(){reloadPage();}public void workspaces(){showWorkspaces();}public void tabs(){showChildren();}public void menu(){showBrowserMenu();}
    };
    private void createTemporary(long expected){requireWindow();detachDisplay();retireBinding();browser.createTemporary(expected,permit);hideOverview();renderBrowser();}
    private void createChild(){requireWindow();if(preferences==null){toast("主页设置尚未读取，未猜测新子页地址");return;}TabsView group=browser.windowSnapshot(permit).selectedWorkspaceTabs();SearchEngines.Target home=preferences.homeTarget();detachDisplay();retireBinding();browser.addTab(group.workspace(),group.revision(),permit);try{browser.navigateSelected(browser.captureSelection(permit),home,permit);}finally{hideOverview();renderBrowser();}}
    private void ordinaryBack(){requireWindow();SessionPresentation.Frame frame=browser.selectedPresentation(browser.captureSelection(permit),permit);if(frame==null || !frame.reportedCanGoBack())return;browser.traverseSelected(browser.captureSelection(permit),true,permit);scheduleRender();}
    private void forward(){requireWindow();SessionPresentation.Frame frame=browser.selectedPresentation(browser.captureSelection(permit),permit);if(frame==null || !frame.reportedCanGoForward())return;browser.traverseSelected(browser.captureSelection(permit),false,permit);scheduleRender();}
    private void reloadPage(){requireWindow();if(newTab.getVisibility()==View.VISIBLE){newTab.refreshNative();readPreferences();return;}browser.reloadSelected(browser.captureSelection(permit),permit);scheduleRender();}
    private void showWorkspaces(){requireWindow();childOverview=false;overview.setVisibility(View.VISIBLE);renderOverview(browser.windowSnapshot(permit));nativeBack.presentationChanged();}
    private void showChildren(){requireWindow();childOverview=true;overview.setVisibility(View.VISIBLE);renderOverview(browser.windowSnapshot(permit));nativeBack.presentationChanged();}
    private void hideOverview(){if(overview!=null)overview.setVisibility(View.GONE);if(workspacePanel!=null)workspacePanel.unbind();if(tabPanel!=null)tabPanel.unbind();}
    private void backOverview(){if(childOverview){childOverview=false;renderOverview(browser.windowSnapshot(permit));}else hideOverview();}
    private void renderOverview(BrowserCoordinator.WindowSnapshot snapshot){
        overviewTitle.setText(childOverview?"子标签页":"总标签页");workspacePanel.setVisibility(childOverview?View.GONE:View.VISIBLE);tabPanel.setVisibility(childOverview?View.VISIBLE:View.GONE);
        if(childOverview)tabPanel.bind(binding,snapshot.selectedWorkspaceTabs(),new TabPanel.Actions(){
            public void create(WorkspaceRef ws,long rev){createChild();}
            public void select(TabRef tab,long rev){requireWindow();detachDisplay();retireBinding();browser.selectTab(tab,rev,permit);hideOverview();renderBrowser();}
            public void close(TabRef tab,long rev){requireWindow();if(boundTab==tab){detachDisplay();retireBinding();}browser.closeTab(tab,rev,permit);renderBrowser();}
        });else workspacePanel.bind(binding,snapshot.workspaces(),new WorkspacePanel.Actions(){
            public void createTemporary(long rev){BrowserActivity.this.createTemporary(rev);}
            public void select(WorkspaceRef ws,long rev){requireWindow();detachDisplay();retireBinding();browser.selectWorkspace(ws,rev,permit);childOverview=true;renderBrowser();}
            public void restore(WorkspaceRef ws,long rev){information("持久环境的真实恢复尚未在当前原生工厂接通，未假装恢复。");}
            public void edit(WorkspaceRef ws,long rev){editWorkspaceName(ws,rev);}
            public void closeOrShelf(WorkspaceRef ws,long rev){requireWindow();WorkspaceView target=null;for(WorkspaceView row:browser.workspaces(permit).active())if(row.ref()==ws)target=row;if(target==null || target.level()!=Level.TEMPORARY){information("当前阶段只接通临时环境；副环境收起与主级保护将随持久恢复一起闭合。");return;}if(boundTab!=null && boundTab.workspace()==ws){detachDisplay();retireBinding();}browser.removeWorkspace(ws,rev,permit);renderBrowser();}
        });
    }
    private void editWorkspaceName(WorkspaceRef ws,long revision){
        requireWindow();if(editor!=null && editor.isShowing())return;EditText input=new EditText(this);input.setSingleLine(true);input.setHint("总标签页名称");input.setSaveEnabled(false);BrowserToolbar.Binding exact=binding;
        editor=new AlertDialog.Builder(this).setTitle("修改名称").setView(input).setNegativeButton("取消",null).setPositiveButton("保存",(d,w)->{try{exact.check();browser.renameWorkspace(ws,revision,input.getText().toString(),permit);scheduleRender();}catch(RuntimeException stale){toast("名称修改未确认，请重新读取");}}).create();editor.setOnDismissListener(d->{input.setText("");editor=null;});editor.show();
    }
    private void showBrowserMenu(){
        requireWindow();if(menu!=null && menu.isShowing())return;SessionPresentation.Frame frame=browser.selectedPresentation(browser.captureSelection(permit),permit);BrowserToolbar.Binding exact=binding;
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(8),dp(8),dp(8),dp(8));GradientDrawable bg=new GradientDrawable();bg.setColor(0xffffffff);bg.setCornerRadius(dp(20));body.setBackground(bg);
        LinearLayout shortcuts=new LinearLayout(this);body.addView(shortcuts,new LinearLayout.LayoutParams(-1,dp(52)));
        menuIcon(shortcuts,R.drawable.ic_rb_back,"网页后退",frame!=null && frame.reportedCanGoBack(),()->ordinaryBack());menuIcon(shortcuts,R.drawable.ic_rb_forward,"网页前进",frame!=null && frame.reportedCanGoForward(),()->forward());menuIcon(shortcuts,R.drawable.ic_rb_star_outline,"URL收藏尚待接通",false,()->{});menuIcon(shortcuts,R.drawable.ic_rb_orientation_portrait,"全局方向设置尚待接通",false,()->{});menuIcon(shortcuts,R.drawable.ic_rb_refresh,"刷新当前页",true,()->reloadPage());
        menuItem(body,"新建总标签页",()->createTemporary(browser.workspaces(permit).revision()));menuItem(body,"当前总标签页的子标签",()->showChildren());menuItem(body,"历史记录",()->startActivity(new Intent(this,HistoryActivity.class)));
        TextView pending=new TextView(this);pending.setText("当前演示路径：临时环境；完整设置、收藏、持久恢复仍待接通");pending.setTextSize(12);pending.setTextColor(0xff6b6472);pending.setPadding(dp(12),dp(8),dp(12),dp(8));body.addView(pending);
        PopupWindow popup=new PopupWindow(body,dp(300),-2,true);menu=popup;popup.setBackgroundDrawable(bg);popup.setElevation(dp(10));popup.setOutsideTouchable(true);popup.setOnDismissListener(()->{if(menu==popup)menu=null;});exact.check();popup.showAsDropDown(toolbar.menuAnchor(),0,0,Gravity.END);
    }
    private void menuIcon(LinearLayout row,int drawable,String description,boolean enabled,Runnable intent){ImageButton b=new ImageButton(this);b.setImageResource(drawable);b.setContentDescription(description);GradientDrawable circle=new GradientDrawable();circle.setColor(0xfff1f2f6);circle.setShape(GradientDrawable.OVAL);b.setBackground(circle);b.setPadding(dp(12),dp(12),dp(12),dp(12));b.setEnabled(enabled);b.setAlpha(enabled?1f:.35f);BrowserToolbar.Binding exact=binding;b.setOnClickListener(v->{try{exact.check();dismissMenu();intent.run();}catch(RuntimeException unknown){toast("操作未确认，未自动重试");}});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(48),1);p.setMargins(dp(2),0,dp(2),0);row.addView(b,p);}
    private void menuItem(LinearLayout body,String title,Runnable intent){TextView item=new TextView(this);item.setText(title);item.setTextSize(15);item.setPadding(dp(16),dp(13),dp(16),dp(13));item.setMinimumHeight(dp(48));BrowserToolbar.Binding exact=binding;item.setOnClickListener(v->{try{exact.check();dismissMenu();intent.run();}catch(RuntimeException unknown){toast("操作未确认，未自动重试");}});body.addView(item,new LinearLayout.LayoutParams(-1,-2));}
    private void dismissMenu(){PopupWindow current=menu;if(current!=null)current.dismiss();}
    private boolean backLayerPresent(BackCoordinator.Layer layer){
        if(layer==BackCoordinator.Layer.IME)return toolbar!=null && toolbar.hasInputFocus() || newTab!=null && newTab.hasInputFocus();
        if(layer==BackCoordinator.Layer.MODAL)return menu!=null && menu.isShowing() || editor!=null && editor.isShowing() || dialogs!=null && dialogs.hasPending();
        if(layer==BackCoordinator.Layer.FULLSCREEN)return presentation!=null && presentation.isOwnedFullscreen();
        if(layer==BackCoordinator.Layer.PANEL)return overview!=null && overview.getVisibility()==View.VISIBLE;
        if(layer==BackCoordinator.Layer.HISTORY){if(browser==null || binding==null)return false;SessionPresentation.Frame frame=browser.selectedPresentation(browser.captureSelection(permit),permit);return frame!=null && frame.reportedCanGoBack();}
        if(layer==BackCoordinator.Layer.WORKSPACE){if(browser==null || binding==null || preferences==null || newTab.getVisibility()==View.VISIBLE)return false;SessionPresentation.Frame frame=browser.selectedPresentation(browser.captureSelection(permit),permit);return frame!=null && frame.reportedLocation()!=null && !frame.reportedLocation().equals(preferences.homepage());}
        return layer==BackCoordinator.Layer.WINDOW_CLOSE && !isFinishing();
    }
    private void performBackLayer(BackCoordinator.Layer layer,BackCoordinator.Reporter reporter){
        if(layer==BackCoordinator.Layer.IME){boolean cleared=toolbar!=null && toolbar.dismissInput();if(newTab!=null)cleared=newTab.dismissInput()||cleared;if(cleared)reporter.reportConsumed();else reporter.reportNoOp();}
        else if(layer==BackCoordinator.Layer.MODAL){if(menu!=null){dismissMenu();reporter.reportConsumed();}else if(editor!=null){editor.dismiss();reporter.reportConsumed();}else if(dialogs==null || !dialogs.requestOwnedCancel(reporter::reportConsumed))reporter.reportNoOp();}
        else if(layer==BackCoordinator.Layer.FULLSCREEN){presentation.leaveOwnedFullscreen();reporter.reportConsumed();}
        else if(layer==BackCoordinator.Layer.PANEL){backOverview();reporter.reportConsumed();}
        else if(layer==BackCoordinator.Layer.HISTORY || layer==BackCoordinator.Layer.WORKSPACE){
            requireWindow();Selection selected=browser.captureSelection(permit);SessionPresentation.Frame frame=browser.selectedPresentation(selected,permit);backLocationSequence=frame==null?0:frame.reportedLocationSequence();backSession=browser.requireSelectedSession(permit);pendingHistoryBack=reporter;
            if(layer==BackCoordinator.Layer.HISTORY)browser.traverseSelected(selected,true,permit);else browser.navigateSelected(selected,preferences.homeTarget(),permit);scheduleRender(); // actual new location completes the layer, NOT Java return
        }else if(layer==BackCoordinator.Layer.WINDOW_CLOSE){long now=SystemClock.elapsedRealtime();if(browserComposed && (exitArmedAt<0 || now-exitArmedAt>2000)){exitArmedAt=now;toast("再按一次返回退出");reporter.reportConsumed();}else{closingWindow=reporter;finish();}}
        else reporter.reportNoOp();
    }
    @Override public void onBackPressed(){ownedBack();}
    private void ownedBack(){if(nativeBack==null || nativeBack.hasPendingLayer())return;try{nativeBack.request(nativeBack.presentationRevision(),binding==null?null:permit);}catch(RuntimeException stale){toast("返回层状态已改变，请重新操作");}}
    @Override protected void onResume(){super.onResume();foreground=true;if(entryWatch==null){WeakReference<BrowserActivity> weak=new WeakReference<>(this);try{entryWatch=startup.observeNativeEntry(()->{BrowserActivity window=weak.get();if(window!=null && window.foreground)window.refreshEntry();});}catch(IllegalStateException occupied){toast("另一个原生主窗口仍持有显示，请返回原窗口");return;}}refreshEntry();}
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(foreground && display!=null && display.isAttached() && binding!=null)try{display.updateVisibility(true,focused,false,permit);}catch(RuntimeException stale){lockCurrentWindow();}}
    @Override protected void onStop(){foreground=false;preferences=null;preferencesRequest=null;if(entryWatch!=null){entryWatch.close();entryWatch=null;}if(watch!=null){watch.close();watch=null;}if(menu!=null)menu.dismiss();if(editor!=null)editor.dismiss();if(nativeBack!=null)nativeBack.presentationChanged();if(dialogs!=null)dialogs.cancelOwned();detachDisplay();retireBinding();super.onStop();}
    @Override protected void onDestroy(){
        if(Build.VERSION.SDK_INT>=33 && systemBack!=null)getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(systemBack);
        if(entryWatch!=null){entryWatch.close();entryWatch=null;}if(watch!=null){watch.close();watch=null;}detachDisplay();retireBinding();if(nativeBack!=null)nativeBack.retireWindowOwner();if(dialogs!=null){dialogs.retireWindow();dialogs=null;}if(presentation!=null){presentation.close();presentation=null;}
        if(closingWindow!=null){closingWindow.reportConsumed();closingWindow=null;}BrowserToolbar.clearOwnedViewTree(findViewById(android.R.id.content));preferences=null;preferencesRequest=null;super.onDestroy();
    }
    private void information(String text){try{if(dialogs!=null && !isFinishing() && !isDestroyed())dialogs.showInformation(text);}catch(RuntimeException unknown){toast("说明窗口状态未知");}}
    private void toast(String text){if(!isDestroyed())Toast.makeText(this,text,Toast.LENGTH_SHORT).show();}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
