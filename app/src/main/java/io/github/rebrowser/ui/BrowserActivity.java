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
import io.github.rebrowser.browser.BookmarkStore;
import io.github.rebrowser.browser.ExternalNavigation;
import io.github.rebrowser.data.PrivateRecordStore;
import io.github.rebrowser.data.OwnedDataCatalog;
import io.github.rebrowser.engine.GeckoSessionOwner;
import io.github.rebrowser.engine.SessionDisplayBinding;
import io.github.rebrowser.engine.SessionPresentation;
import io.github.rebrowser.engine.PromptDelegate;
import io.github.rebrowser.entrypoints.AdminControlActivity;
import io.github.rebrowser.settings.SettingsRepository;
import io.github.rebrowser.settings.SettingsRepository.WindowOrientation;
import java.lang.ref.WeakReference;
import org.mozilla.geckoview.GeckoView;

/** Actual native browser window, not an alternate browser/backend. Business views ONLY after
 * independent source acceptance. No Intent/Bundle/test key creates a source permit or URL load.
 * The current native factory supports temporary environments; persistence/Popup/permissions
 * remain explicitly pending. Native business journeys have separately scoped runtime evidence;
 * neither a URI/Intent nor general compilation is full product/source/administrator admission.
 */
public final class BrowserActivity extends Activity {
    private static WeakReference<BrowserActivity> nativeMainWindow=new WeakReference<>(null);
    private StartupCoordinator startup;private ComponentGate.BusinessPermit permit;
    private WindowPresentation presentation;private NativeDialogs dialogs;private BackCoordinator nativeBack;private BackCoordinator.Reporter closingWindow,pendingHistoryBack;
    private OnBackInvokedCallback systemBack;private boolean foreground,renderQueued,browserComposed,browserCompositionAttempted;
    private final Handler main=new Handler(Looper.getMainLooper());
    private BrowserCoordinator browser;private BrowserCoordinator.WindowSubscription watch;private StartupCoordinator.EntrySubscription entryWatch;
    private BrowserToolbar toolbar;private NewTabView newTab;private GeckoView gecko;private SessionDisplayBinding display;private GeckoSessionOwner.SessionRef displayedRef;
    private FrameLayout root,content,pageSurface,overview;private TextView overviewTitle,lockedStatus,sourceWarning,sessionWarning;private WorkspacePanel workspacePanel;private TabPanel tabPanel;
    private PromptDelegate nativePrompt;private PromptDelegate.Window promptWindow;private BrowserToolbar.Binding promptBinding;
    private BackCoordinator.Reporter pendingFullscreenBack;private GeckoSessionOwner.SessionRef fullscreenBackSession;private long fullscreenBackSequence;
    private boolean childOverview,favoritesOverview;private BookmarkView favoritesPanel;private FavoriteData favoriteData;private FavoritePage favoritesPageAtOpen;private final java.util.concurrent.atomic.AtomicBoolean favoriteBusy=new java.util.concurrent.atomic.AtomicBoolean();
    private BrowserToolbar.Binding binding,newTabBinding;private TabRef boundTab;private Object windowFrame;
    private LoadingProgressView progress;private LoadingProgressView.LoadRef progressRef;private SessionPresentation.LoadObservation progressObservation;private GeckoSessionOwner.SessionRef progressSession;
    private PopupWindow menu;private MenuAction pendingMenuAction;
    private static final class MenuAction {final PopupWindow popup;final BrowserToolbar.Binding binding;final Runnable action;boolean dismissed,queued;MenuAction(PopupWindow popup,BrowserToolbar.Binding binding,Runnable action){this.popup=popup;this.binding=binding;this.action=action;}}
    private AlertDialog editor;private SettingsRepository.Snapshot preferences;private Object preferencesRequest;
    private final java.util.concurrent.atomic.AtomicBoolean directionBusy=new java.util.concurrent.atomic.AtomicBoolean();
    private long exitArmedAt=-1,backLocationSequence;private GeckoSessionOwner.SessionRef backSession;
    private PendingHistoryNavigation pendingHistoryNavigation;private ExternalState externalState;private WorkspaceRef initialExternalWorkspace;
    /** Native nonconfiguration holder of untrusted web DATA/duplicate-attempt state only.
     * Not a source/Actor/window capability and never reconstructed from Intent extras/Bundle. */
    private static final class ExternalState {final StartupCoordinator owner;ExternalNavigation.PendingWebTarget pending;ExternalState(StartupCoordinator owner,ExternalNavigation.PendingWebTarget pending){this.owner=owner;this.pending=pending;}}
    /** In-process native target only: neither an Intent/row token nor serialized selection
     * can retarget a history click to whichever browser tab happens to be current later. */
    static final class HistoryTarget {
        private final WeakReference<BrowserActivity> window;private final TabRef original;private final ComponentGate.DataEpochPin source;
        private HistoryTarget(BrowserActivity window,TabRef original,ComponentGate.DataEpochPin source){this.window=new WeakReference<>(window);this.original=original;this.source=source;}
        private BrowserActivity requireOriginal(){BrowserToolbar.main();source.requireCurrent();BrowserActivity current=window.get();if(current==null || current.isDestroyed() || current.isFinishing() || current.browser==null || current.browser.captureSelection(current.permit).tab()!=original)throw new SecurityException("Original history target retired/changed");return current;}
        void stage(HistoryActivity.OpenReceipt receipt){BrowserActivity current=requireOriginal();if(receipt==null || current.pendingHistoryNavigation!=null)throw new SecurityException("Native history handoff already pending");receipt.requireCurrent();current.pendingHistoryNavigation=new PendingHistoryNavigation(this,receipt);}
    }
    private static final class PendingHistoryNavigation {final HistoryTarget target;final HistoryActivity.OpenReceipt receipt;PendingHistoryNavigation(HistoryTarget target,HistoryActivity.OpenReceipt receipt){this.target=target;this.receipt=receipt;}}
    static HistoryTarget captureHistoryTarget(){BrowserToolbar.main();BrowserActivity current=nativeMainWindow.get();if(current==null || !current.browserComposed || current.browser==null || current.isDestroyed() || current.isFinishing())return null;try{current.startup.gate().checkCurrent(current.permit);return new HistoryTarget(current,current.browser.captureSelection(current.permit).tab(),current.startup.gate().captureDataEpoch(current.permit));}catch(RuntimeException unavailable){return null;}}
    private void deliverHistoryNavigation(){PendingHistoryNavigation pending=pendingHistoryNavigation;if(pending==null)return;pendingHistoryNavigation=null;try{if(pending.target.requireOriginal()!=this)throw new SecurityException("Foreign history window");requireWindow();navigate(pending.receipt.consume());}catch(RuntimeException stale){toast("原标签、来源或历史记录有效期已改变，未打开网页");}}

    @Override public void onCreate(Bundle state){
        super.onCreate(state);if(!(getApplication() instanceof ReBrowserApplication)){finish();return;}
        startup=((ReBrowserApplication)getApplication()).primaryStartup();if(!startup.gate().mayShowNativeShell()){finish();return;}
        Object retained=getLastNonConfigurationInstance();if(retained instanceof ExternalState && ((ExternalState)retained).owner==startup){externalState=(ExternalState)retained;ExternalNavigation.takeNativeIncoming(getIntent());}else externalState=new ExternalState(startup,ExternalNavigation.takeNativeIncoming(getIntent()));setIntent(new Intent(this,BrowserActivity.class).setAction(Intent.ACTION_MAIN));
        BrowserActivity existing=nativeMainWindow.get();
        if(existing!=null && existing!=this && !existing.isFinishing() && !existing.isDestroyed() && !existing.isChangingConfigurations()){
            if(externalState.pending!=null && !externalState.pending.attempted())existing.offerExternalData(externalState.pending);
            // Same native main window, as the inherited launcher/reorder route. Do not compose
            // a second Gecko display while settings/history is above the original Activity.
            // Mark this duplicate finishing BEFORE asking Android to reorder: otherwise
            // REORDER_TO_FRONT can resolve this just-created top instance instead of the
            // original Main below Settings, then our finish strands the queued URL there.
            finish();try{existing.startActivity(new Intent(existing,BrowserActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_SINGLE_TOP));}catch(RuntimeException unknown){/* original URL stays queued; no alternate window/backend or automatic retry */}return;
        }
        nativeMainWindow=new WeakReference<>(this);
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
        Button initialize=new Button(this);initialize.setText("首次初始化（先核对空白状态）");initialize.setOnClickListener(v->{try{startup.requestFirstSetup(this);initialize.setEnabled(false);updateLockedStatus();}catch(RuntimeException unavailable){toast("当前来源不允许首次初始化；未重试或覆盖数据");}});shell.addView(initialize);
        Button explanation=new Button(this);explanation.setText("当前开发状态");explanation.setOnClickListener(v->information("主浏览器原生窗口已接到同一浏览器域，但首次来源部署、实际设备运行、持久环境恢复和完整权限尚需闭合。本窗口不会用公钥配置、首次点击或调试参数伪造业务解锁。"));shell.addView(explanation);
        Button admin=new Button(this);admin.setText("管理员检查（不执行命令）");admin.setOnClickListener(v->startActivity(new Intent(this,AdminControlActivity.class)));shell.addView(admin);
        setContentView(shell);presentation.attachNativeContent(shell);updateLockedStatus();
    }
    private void updateLockedStatus(){if(lockedStatus!=null)lockedStatus.setText("来源状态："+startup.gate().sourceState().name()+"\n配置管理员公钥不等于来源接受。业务保持锁定。\n"+startup.firstSetupExplanation());}
    private void refreshEntry(){
        if(!foreground || isFinishing() || isDestroyed())return;
        if(startup.gate().sourceState()!=ComponentGate.SourceState.TRUSTED){if(browserComposed)lockCurrentWindow();updateLockedStatus();return;}
        try{
            permit=startup.gate().requireBusiness();browser=startup.temporaryBrowserDomain(permit);
            if(!browserComposed){if(browserCompositionAttempted)throw new IllegalStateException("Native composition outcome unavailable; no automatic alternate view");browserCompositionAttempted=true;composeBrowser();}
            if(watch==null){WeakReference<BrowserActivity> weak=new WeakReference<>(this);watch=browser.observeNativeWindow(()->{BrowserActivity window=weak.get();if(window!=null)window.scheduleRender();},permit);}
            readPreferences();renderBrowser();dispatchExternalTarget();if(favoriteData==null && !favoriteBusy.get())favoriteWork(FavoriteIntent.read());
        }catch(RuntimeException unavailable){if(!browserComposed)updateLockedStatus();else toast("浏览器窗口暂不可用；未重建会话或绕过来源检查");}
    }
    private void composeBrowser(){
        presentation.close();presentation=new WindowPresentation(this);
        root=new FrameLayout(this);root.setBackgroundColor(0xffffffff);LinearLayout column=new LinearLayout(this);column.setOrientation(LinearLayout.VERTICAL);root.addView(column,new FrameLayout.LayoutParams(-1,-1));
        toolbar=new BrowserToolbar(this);column.addView(toolbar,new LinearLayout.LayoutParams(-1,-2));content=new FrameLayout(this);column.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        pageSurface=new FrameLayout(this);content.addView(pageSurface,new FrameLayout.LayoutParams(-1,-1));gecko=new GeckoView(this);pageSurface.addView(gecko,new FrameLayout.LayoutParams(-1,-1));gecko.setVisibility(View.GONE);display=browser.sessionDisplayAccess(permit).bind(gecko);
        newTab=new NewTabView(this);pageSurface.addView(newTab,new FrameLayout.LayoutParams(-1,-1));newTab.shareNativeBackdrop(root,photo->{if(toolbar!=null)toolbar.appearance(photo);if(presentation!=null && !isFinishing() && !isDestroyed())presentation.photoForeground(photo);});
        sessionWarning=new TextView(this);sessionWarning.setGravity(Gravity.CENTER);sessionWarning.setTextSize(16);sessionWarning.setBackgroundColor(0xffffffff);sessionWarning.setPadding(dp(24),dp(24),dp(24),dp(24));sessionWarning.setText("当前网页会话已退出或尚待显式恢复。\n未自动重开、换路重发或伪报恢复。\n点此打开总标签概览，可切换或关闭此子页。");sessionWarning.setOnClickListener(v->{try{showWorkspaces();}catch(RuntimeException stale){lockCurrentWindow();}});sessionWarning.setVisibility(View.GONE);pageSurface.addView(sessionWarning,new FrameLayout.LayoutParams(-1,-1));
        overview=new FrameLayout(this);overview.setBackgroundColor(0xfff7f8fc);LinearLayout panels=new LinearLayout(this);panels.setOrientation(LinearLayout.VERTICAL);overview.addView(panels,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);TextView back=new TextView(this);back.setText("‹");back.setTextSize(32);back.setGravity(Gravity.CENTER);back.setContentDescription("返回上一级概览或网页");back.setOnClickListener(v->backOverview());heading.addView(back,new LinearLayout.LayoutParams(dp(48),dp(52)));overviewTitle=new TextView(this);overviewTitle.setTextSize(19);heading.addView(overviewTitle,new LinearLayout.LayoutParams(0,-2,1));panels.addView(heading,new LinearLayout.LayoutParams(-1,dp(56)));
        workspacePanel=new WorkspacePanel(this);tabPanel=new TabPanel(this);FrameLayout panelBody=new FrameLayout(this);panelBody.addView(workspacePanel,new FrameLayout.LayoutParams(-1,-1));panelBody.addView(tabPanel,new FrameLayout.LayoutParams(-1,-1));favoritesPanel=new BookmarkView(this);favoritesPanel.setVisibility(View.GONE);panelBody.addView(favoritesPanel,new FrameLayout.LayoutParams(-1,-1));panels.addView(panelBody,new LinearLayout.LayoutParams(-1,0,1));content.addView(overview,new FrameLayout.LayoutParams(-1,-1));overview.setVisibility(View.GONE);
        sourceWarning=new TextView(this);sourceWarning.setGravity(Gravity.CENTER);sourceWarning.setTextSize(16);sourceWarning.setTextColor(0xff31353d);sourceWarning.setBackgroundColor(0xffffffff);sourceWarning.setText("来源或窗口许可已失效，网页与私有内容已隐藏。\n需完成对应来源恢复后才能继续，不会自动重建或覆盖数据。");sourceWarning.setPadding(dp(24),dp(24),dp(24),dp(24));sourceWarning.setVisibility(View.GONE);content.addView(sourceWarning,new FrameLayout.LayoutParams(-1,-1));
        progress=new LoadingProgressView(this);FrameLayout.LayoutParams line=new FrameLayout.LayoutParams(-1,dp(3),Gravity.TOP);line.topMargin=dp(62);root.addView(progress,line);progress.setElevation(dp(8));
        setContentView(root);presentation.attachNativeContent(root);browserComposed=true;lockedStatus=null;
        if(browser.workspaces(permit).active().isEmpty()){WorkspaceRef fresh=browser.createTemporary(browser.workspaces(permit).revision(),permit);if(externalState!=null && externalState.pending!=null && !externalState.pending.attempted())initialExternalWorkspace=fresh;}
    }
    @Override protected void onNewIntent(Intent incoming){super.onNewIntent(incoming);boolean web=incoming!=null && (Intent.ACTION_VIEW.equals(incoming.getAction()) || ExternalNavigation.ACTION_OPEN_WEB.equals(incoming.getAction()));ExternalNavigation.PendingWebTarget data=ExternalNavigation.takeNativeIncoming(incoming);setIntent(new Intent(this,BrowserActivity.class).setAction(Intent.ACTION_MAIN));if(data!=null){offerExternalData(data);if(foreground)refreshEntry();}else if(web)toast("仅支持有效的HTTP(S)外部网页链接");}
    @Override public Object onRetainNonConfigurationInstance(){return externalState;}
    private void offerExternalData(ExternalNavigation.PendingWebTarget data){if(data==null || data.attempted())return;if(externalState==null)externalState=new ExternalState(startup,null);if(externalState.pending!=null && !externalState.pending.attempted()){if(externalState.pending!=data)toast("已有外部网页请求等待来源或窗口；未替换原请求");return;}externalState.pending=data;}
    private void dispatchExternalTarget(){if(externalState==null || externalState.pending==null || externalState.pending.attempted())return;requireWindow();SearchEngines.Target target=externalState.pending.claimNativeDispatchAttempt();try{WorkspaceRef fresh=initialExternalWorkspace;initialExternalWorkspace=null;if(fresh==null || browser.workspaces(permit).selected()!=fresh)createTemporary(browser.workspaces(permit).revision());navigate(target);}catch(RuntimeException unknown){initialExternalWorkspace=null;toast("外部网页尝试未确认；未改用原登录环境或自动重试");}}
    private void scheduleRender(){
        if(renderQueued || !foreground || !browserComposed)return;renderQueued=true;WeakReference<BrowserActivity> weak=new WeakReference<>(this);
        if(!main.post(()->{BrowserActivity window=weak.get();if(window==null)return;window.renderQueued=false;if(window.foreground)window.renderBrowser();}))renderQueued=false;
    }
    private void readPreferences(){
        Object request=new Object();preferencesRequest=request;WeakReference<BrowserActivity> weak=new WeakReference<>(this);Handler handler=main;StartupCoordinator owner=startup;ComponentGate.BusinessPermit current=permit;
        try{startup.executeBrowserDataIo(current,()->{
            SettingsRepository.Snapshot result=null;try{SettingsRepository repository=owner.openExistingSettingsOnIo(current);try(OwnedDataCatalog.View<SettingsRepository.Snapshot> view=repository.read(current)){result=view.value(current);}owner.gate().checkCurrent(current);}catch(Exception unavailable){/* missing settings is NOT a default/seed */}
            SettingsRepository.Snapshot observed=result;handler.post(()->{BrowserActivity window=weak.get();if(window==null || !window.foreground || window.preferencesRequest!=request || window.isDestroyed() || window.isFinishing())return;try{owner.gate().checkCurrent(current);window.preferences=observed;if(observed==null)window.toast("设置记录尚未就绪，未写入默认配置");else window.applyObservedDirection();}catch(RuntimeException stale){window.lockCurrentWindow();}});
        });}catch(RuntimeException unavailable){toast("设置读取未能提交，未自动重试");}
    }
    private void applyObservedDirection(){
        if(!foreground || preferences==null || binding==null || presentation==null || !hasWindowFocus())return;
        try{binding.check();presentation.applyObservedDirection(binding,preferences.windowOrientation(),preferences.deviceType());}
        catch(SecurityException lost){lockCurrentWindow();}
        catch(RuntimeException unavailable){toast("当前窗口方向请求未确认；未重试或更改系统旋转设置");}
    }
    private static boolean sameSettings(SettingsRepository.Snapshot a,SettingsRepository.Snapshot b){
        byte[] first=SettingsRepository.CODEC.encode(a),second=SettingsRepository.CODEC.encode(b);
        try{return java.util.Arrays.equals(first,second);}finally{java.util.Arrays.fill(first,(byte)0);java.util.Arrays.fill(second,(byte)0);}
    }
    private void requestDirection(WindowOrientation mode){
        requireWindow();if(mode==null || preferences==null || !presentation.manualDirectionAllowed(preferences.deviceType()) || !directionBusy.compareAndSet(false,true))return;
        BrowserToolbar.Binding original=binding;SettingsRepository.Snapshot base=preferences;
        ComponentGate.DataEpochPin source=startup.gate().captureDataEpoch(permit);SettingsRepository.Snapshot intended=base.withWindowOrientation(mode);
        WeakReference<BrowserActivity> weak=new WeakReference<>(this);WeakReference<BrowserToolbar.Binding> weakBinding=new WeakReference<>(original);
        StartupCoordinator owner=startup;ComponentGate.BusinessPermit current=permit;Handler handler=main;
        java.util.concurrent.atomic.AtomicBoolean busy=directionBusy;
        try{owner.executeBrowserDataIo(current,()->{
            SettingsRepository.Snapshot verified=null;String failure=null;
            try{
                source.requireCurrent();SettingsRepository repository=owner.openExistingSettingsOnIo(current);long written;
                try(OwnedDataCatalog.View<SettingsRepository.Snapshot> view=repository.read(current)){
                    if(view.kind()!=PrivateRecordStore.Kind.CURRENT || !sameSettings(base,view.value(current)))throw new java.io.IOException("Original settings changed");
                    source.requireCurrent();written=repository.compareAndWriteWindowOrientation(view,intended,current);
                }
                // One decoded-root credit: close original before independent exact readback.
                try(OwnedDataCatalog.View<SettingsRepository.Snapshot> view=repository.read(current)){
                    if(view.kind()!=PrivateRecordStore.Kind.CURRENT || view.recordRevision()!=written || !sameSettings(intended,view.value(current)))throw new java.io.IOException("Direction readback unavailable");
                    verified=view.value(current);source.requireCurrent();
                }
            }catch(Exception unknown){verified=null;failure="方向保存结果未确认或原配置已改变；未重试或重置";}
            finally{busy.set(false);}
            SettingsRepository.Snapshot actual=verified;String message=failure;
            handler.post(()->{BrowserActivity window=weak.get();BrowserToolbar.Binding target=weakBinding.get();
                if(window==null || target==null || !window.foreground || window.isFinishing() || window.isDestroyed() || window.binding!=target)return;
                try{source.requireCurrent();target.check();if(actual==null){window.toast(message);window.readPreferences();return;}
                    window.preferences=actual;window.presentation.applyNativeDirectionIntent(target,actual.windowOrientation(),actual.deviceType());
                    window.toast("方向偏好已写入并回读；已请求当前窗口调整");
                }catch(RuntimeException unavailable){window.toast("原窗口或方向请求已失效；未改用其他窗口或系统设置");}
            });
        });}catch(java.util.concurrent.RejectedExecutionException unsubmitted){busy.set(false);toast("方向任务未获队列接纳；未保存或重试");}
        catch(RuntimeException unknownSubmission){toast("方向任务提交结果未知；未自动重试或释放工作状态");}
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
                detachNativePrompt();detachDisplay();gecko.setVisibility(View.GONE);newTab.setVisibility(View.VISIBLE);
                if(newTabBinding!=binding){BrowserToolbar.Binding exact=binding;newTab.bindNative(startup,permit,exact,target->{exact.check();navigate(target);});newTabBinding=exact;}progress.unbind();progressRef=null;progressObservation=null;progressSession=null;
            }else{
                if(newTabBinding!=null){newTab.unbind();newTabBinding=null;}newTab.setVisibility(View.GONE);gecko.setVisibility(View.VISIBLE);
                GeckoSessionOwner.SessionRef session=browser.existingSelectedSession(browser.captureSelection(permit),permit);
                if(session==null){sessionWarning.setOnClickListener(v->{try{showWorkspaces();}catch(RuntimeException stale){lockCurrentWindow();}});sessionWarning.setText("当前网页会话已退出或尚待显式恢复。\n未自动重开或伪报恢复。\n点此打开总标签概览，可切换或关闭此子页。");detachDisplay();gecko.setVisibility(View.GONE);sessionWarning.setVisibility(View.VISIBLE);progress.unbind();progressRef=null;progressObservation=null;progressSession=null;}
                else{display.attach(session,permit);displayedRef=session;display.updateVisibility(true,hasWindowFocus(),false,permit);bindNativePrompt(session);BrowserToolbar.Binding selectionWindow=binding;display.bindNativeSelection(this,startup.gate().captureDataEpoch(permit),()->{selectionWindow.check();if(displayedRef!=session || !gecko.isShown() || overview.getVisibility()==View.VISIBLE || sourceWarning.getVisibility()==View.VISIBLE || menu!=null || editor!=null || nativePrompt!=null && nativePrompt.hasPending())throw new SecurityException("Original visible selection window unavailable");},permit);if(frame!=null && frame.reportedLoadError()){sessionWarning.setOnClickListener(null);gecko.setVisibility(View.GONE);sessionWarning.setText("网页加载失败；会话仍保留。\n可输入新地址或从菜单重新加载。\n未改用其他会话、忽略证书或自动重发。");sessionWarning.setVisibility(View.VISIBLE);}}
                if(session!=null && frame!=null && frame.reportedLoad()!=null){if(progressObservation!=frame.reportedLoad() || progressSession!=session){progressObservation=frame.reportedLoad();progressSession=session;progressRef=progress.beginSessionReportedLoad(binding,session,frame.reportedLoad());}if(frame.loadingObserved())progress.report(progressRef,frame.reportedProgress());else progress.pageStopped(progressRef);}
            }
            toolbar.bind(binding,toolbarActions,url,frame!=null && frame.reportedCanGoBack(),blank,snapshot.workspaces().active().size());
            if(overview.getVisibility()==View.VISIBLE)renderOverview(snapshot);
            boolean nativeFullscreen=frame!=null && !frame.retired() && frame.reportedFullScreen() && !blank && sessionWarning.getVisibility()!=View.VISIBLE;
            boolean showFullscreen=nativeFullscreen && overview.getVisibility()!=View.VISIBLE && menu==null && editor==null && (dialogs==null || !dialogs.hasPending()) && (nativePrompt==null || !nativePrompt.hasPending());
            if(showFullscreen){toolbar.setVisibility(View.GONE);progress.setAlpha(0f);if(!presentation.isOwnedFullscreen())presentation.enterOwnedFullscreen(binding);}else restoreFullscreenChrome();
            if(pendingFullscreenBack!=null){SessionPresentation.Frame original=browser.observeOwnedSession(fullscreenBackSession,permit);if(SessionPresentation.nativeFullscreenExitObserved(fullscreenBackSequence,original)){pendingFullscreenBack.reportConsumed();pendingFullscreenBack=null;fullscreenBackSession=null;}else if(browser.ownedSessionEnded(fullscreenBackSession,permit)){pendingFullscreenBack.reportFailed();pendingFullscreenBack=null;fullscreenBackSession=null;}}
            if(pendingHistoryBack!=null){SessionPresentation.Frame original=browser.observeOwnedSession(backSession,permit);if(original!=null && original.reportedLocationSequence()>backLocationSequence){pendingHistoryBack.reportConsumed();pendingHistoryBack=null;backSession=null;}else if(browser.ownedSessionEnded(backSession,permit)){pendingHistoryBack.reportFailed();pendingHistoryBack=null;backSession=null;}}
        }catch(SecurityException locked){lockCurrentWindow();}catch(RuntimeException unavailable){toast("当前窗口状态未确认，未重开会话或重发导航");}
    }
    private void restoreFullscreenChrome(){if(presentation!=null)presentation.leaveOwnedFullscreen();if(toolbar!=null)toolbar.setVisibility(View.VISIBLE);if(progress!=null)progress.setAlpha(1f);}
    private void lockCurrentWindow(){pendingMenuAction=null;restoreFullscreenChrome();favoriteData=null;favoritesPageAtOpen=null;if(favoritesPanel!=null)favoritesPanel.unbind();favoritesOverview=false;if(sourceWarning!=null)sourceWarning.setVisibility(View.VISIBLE);if(gecko!=null)gecko.setVisibility(View.GONE);if(overview!=null)overview.setVisibility(View.GONE);if(watch!=null){watch.close();watch=null;}try{detachDisplay();}catch(RuntimeException unknown){/* retain the actual display owner; no fake close/end */}retireBinding();preferences=null;preferencesRequest=null;if(menu!=null)menu.dismiss();toast("来源或窗口许可已失效，浏览操作已停止");}
    private void bindNativePrompt(GeckoSessionOwner.SessionRef session){PromptDelegate delegate=browser.ownedPromptDelegate(session,permit);if(delegate==nativePrompt && promptBinding==binding)return;detachNativePrompt();if(delegate==null)return;BrowserToolbar.Binding exact=binding;PromptDelegate.Window actualWindow=new PromptDelegate.Window(){public Activity requireCurrentPromptWindow(){exact.check();if(displayedRef!=session || gecko==null || !gecko.isShown() || overview.getVisibility()==View.VISIBLE || sourceWarning.getVisibility()==View.VISIBLE || menu!=null || editor!=null || dialogs!=null && dialogs.hasPending())throw new SecurityException("Original visible native prompt window unavailable");return BrowserActivity.this;}public void presentationChanged(){nativeBack.presentationChanged();scheduleRender();}};delegate.attachNativeWindow(actualWindow);nativePrompt=delegate;promptWindow=actualWindow;promptBinding=exact;}
    private void detachNativePrompt(){PromptDelegate old=nativePrompt;PromptDelegate.Window original=promptWindow;nativePrompt=null;promptWindow=null;promptBinding=null;if(old!=null)old.detachNativeWindow(original);}
    private void retireBinding(){detachNativePrompt();windowFrame=null;if(binding!=null)binding.retire();binding=null;newTabBinding=null;boundTab=null;if(newTab!=null)newTab.unbind();if(toolbar!=null)toolbar.unbind();if(progress!=null)progress.unbind();progressRef=null;progressObservation=null;progressSession=null;}
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
    private void showWorkspaces(){requireWindow();favoritesOverview=false;favoritesPanel.unbind();favoritesPanel.setVisibility(View.GONE);childOverview=false;showOverviewSurface();renderOverview(browser.windowSnapshot(permit));nativeBack.presentationChanged();}
    private void showChildren(){requireWindow();favoritesOverview=false;favoritesPanel.unbind();favoritesPanel.setVisibility(View.GONE);childOverview=true;showOverviewSurface();renderOverview(browser.windowSnapshot(permit));nativeBack.presentationChanged();}
    private void showOverviewSurface(){if(display!=null)display.fenceNativeSelection();overview.setVisibility(View.VISIBLE);pageSurface.setVisibility(View.INVISIBLE);}
    private void hideOverview(){if(pageSurface!=null)pageSurface.setVisibility(View.VISIBLE);favoritesOverview=false;favoritesPageAtOpen=null;if(favoritesPanel!=null){favoritesPanel.unbind();favoritesPanel.setVisibility(View.GONE);}if(overview!=null)overview.setVisibility(View.GONE);if(workspacePanel!=null)workspacePanel.unbind();if(tabPanel!=null)tabPanel.unbind();}
    private void backOverview(){if(favoritesOverview){hideOverview();scheduleRender();return;}if(childOverview){childOverview=false;renderOverview(browser.windowSnapshot(permit));}else hideOverview();scheduleRender();}
    private void renderOverview(BrowserCoordinator.WindowSnapshot snapshot){
        if(favoritesOverview){overviewTitle.setText("收藏");workspacePanel.setVisibility(View.GONE);tabPanel.setVisibility(View.GONE);favoritesPanel.setVisibility(View.VISIBLE);return;}
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
    private static final class FavoriteData {final BookmarkStore.Snapshot snapshot;final long revision;final ComponentGate.DataEpochPin source;FavoriteData(BookmarkStore.Snapshot snapshot,long revision,ComponentGate.DataEpochPin source){this.snapshot=snapshot;this.revision=revision;this.source=source;}}
    private static final class FavoritePage {final String url,title;final GeckoSessionOwner.SessionRef session;final long location;FavoritePage(String url,String title,GeckoSessionOwner.SessionRef session,long location){this.url=url;this.title=title;this.session=session;this.location=location;}}
    private static final class FavoriteIntent {static final int READ=0,ADD=1,EDIT=2,REMOVE=3,OPEN=4;final int kind;final long revision;final String id,original,title,url;private FavoriteIntent(int kind,long revision,String id,String original,String title,String url){this.kind=kind;this.revision=revision;this.id=id;this.original=original;this.title=title;this.url=url;}static FavoriteIntent read(){return new FavoriteIntent(READ,0,null,null,null,null);}}
    private FavoritePage favoritePage(){try{Selection selection=browser.captureSelection(permit);SessionPresentation.Frame frame=browser.selectedPresentation(selection,permit);if(frame==null || frame.reportedLoadError() || browser.selectedRequestedTarget(selection,permit)!=null)return null;String url=SearchEngines.requireWebUrl(frame.reportedLocation()).url();String title=frame.reportedTitle();if(title==null || title.trim().isEmpty())title=url;if(title.length()>640){int end=640;if(Character.isHighSurrogate(title.charAt(end-1)))end--;title=title.substring(0,end);}return new FavoritePage(url,title,browser.existingSelectedSession(selection,permit),frame.reportedLocationSequence());}catch(RuntimeException unavailable){return null;}}
    private void requireFavoritePage(FavoritePage original){requireWindow();FavoritePage current=favoritePage();if(original==null || current==null || original.session!=current.session || original.location!=current.location || !original.url.equals(current.url))throw new SecurityException("Original favorite page changed");}
    private static FavoriteData readFavoriteData(StartupCoordinator owner,ComponentGate.BusinessPermit permit)throws Exception{BookmarkStore repository=owner.openExistingBookmarkStoreOnIo(permit);try(OwnedDataCatalog.View<BookmarkStore.Snapshot> view=repository.read(permit)){if(view.kind()!=PrivateRecordStore.Kind.CURRENT)throw new java.io.IOException("Existing favorites unavailable");return new FavoriteData(view.value(permit),view.recordRevision(),owner.gate().captureDataEpoch(permit));}}
    /** Same metadata lane, exact canonical row/version before effects, weak UI recipient and
     * no missing-record seed, alternate store, Activity capture or unknown-write retry. */
    private void favoriteWork(FavoriteIntent intent){
        requireWindow();if(intent==null || !favoriteBusy.compareAndSet(false,true)){toast("收藏操作仍在进行；未重复提交");return;}StartupCoordinator owner=startup;ComponentGate.BusinessPermit currentPermit=permit;ComponentGate.DataEpochPin source=owner.gate().captureDataEpoch(permit);java.util.concurrent.atomic.AtomicBoolean busy=favoriteBusy;WeakReference<BrowserActivity> weak=new WeakReference<>(this);Handler handler=main;WeakReference<BrowserToolbar.Binding> weakBinding=new WeakReference<>(binding);
        try{owner.executeBrowserDataIo(currentPermit,()->{FavoriteData result=null;SearchEngines.Target open=null;String failure=null;boolean changed=false;try{source.requireCurrent();BookmarkStore repository=owner.openExistingBookmarkStoreOnIo(currentPermit);BookmarkStore.Snapshot intended=null;long written=-1;
            try(OwnedDataCatalog.View<BookmarkStore.Snapshot> view=repository.read(currentPermit)){if(view.kind()!=PrivateRecordStore.Kind.CURRENT || intent.revision>0 && view.recordRevision()!=intent.revision)throw new java.io.IOException("Original favorites revision changed");BookmarkStore.Snapshot before=view.value(currentPermit);if(intent.kind==FavoriteIntent.ADD)intended=before.add(intent.title,intent.url);else if(intent.kind==FavoriteIntent.EDIT)intended=before.editExact(intent.id,intent.original,intent.title,intent.url);else if(intent.kind==FavoriteIntent.REMOVE)intended=before.removeExact(intent.id,intent.original);else if(intent.kind==FavoriteIntent.OPEN){BookmarkStore.Node node=before.require(intent.id);if(!node.url().equals(intent.original))throw new SecurityException("Favorite retargeted");open=SearchEngines.requireWebUrl(node.url());}if(intended!=null && intended!=before){source.requireCurrent();written=repository.compareAndWrite(view,intended,currentPermit);}}
            result=readFavoriteData(owner,currentPermit);if(intent.kind==FavoriteIntent.OPEN && (result.revision!=intent.revision || !result.snapshot.require(intent.id).url().equals(intent.original)))throw new java.io.IOException("Favorite changed during open");if(written>=0){byte[] expected=BookmarkStore.CODEC.encode(intended),actual=BookmarkStore.CODEC.encode(result.snapshot);try{if(result.revision!=written || !java.util.Arrays.equals(expected,actual))throw new java.io.IOException("Favorites readback mismatch");changed=true;}finally{java.util.Arrays.fill(expected,(byte)0);java.util.Arrays.fill(actual,(byte)0);}}source.requireCurrent();
        }catch(Exception unknown){failure="收藏读取、写入或原条目未能确认；没有自动重试、重置或宣称已保存";}finally{busy.set(false);}FavoriteData observed=result;SearchEngines.Target target=open;String error=failure;boolean committed=changed;handler.post(()->{BrowserActivity window=weak.get();if(window==null || !window.foreground || window.isFinishing() || window.isDestroyed())return;try{source.requireCurrent();if(error!=null){if(window.favoritesOverview)window.favoritesPanel.showUnavailable(error);window.toast(error);return;}window.favoriteData=observed;if(intent.kind==FavoriteIntent.OPEN){BrowserToolbar.Binding original=weakBinding.get();if(original==null || window.binding!=original)throw new SecurityException("Original favorites navigation window retired");original.check();window.navigate(target);return;}if(window.favoritesOverview)window.renderFavorites();if(intent.kind!=FavoriteIntent.READ && window.binding==weakBinding.get())window.toast(committed?(intent.kind==FavoriteIntent.REMOVE?"已取消收藏":"收藏已写入并回读确认"):"当前网页已收藏");}catch(RuntimeException stale){window.toast("收藏来源或原窗口已改变；未改用其他目标");}});
        });}catch(java.util.concurrent.RejectedExecutionException unsubmitted){busy.set(false);toast("收藏任务未获队列接纳；未提交写入");}catch(RuntimeException unknownSubmission){toast("收藏提交结果未知；保留原操作，不重复提交");}
    }
    private void showFavorites(){requireWindow();favoritesPanel.showUnavailable("正在读取实际收藏…");favoritesOverview=true;childOverview=false;favoritesPageAtOpen=favoritePage();showOverviewSurface();renderOverview(browser.windowSnapshot(permit));if(!favoriteBusy.get())favoriteWork(FavoriteIntent.read());nativeBack.presentationChanged();}
    private void renderFavorites(){if(!favoritesOverview || favoriteData==null || binding==null)return;try{favoriteData.source.requireCurrent();binding.check();}catch(RuntimeException stale){favoriteData=null;favoritesPanel.showUnavailable("原收藏来源或窗口已改变；未恢复旧内容");return;}java.util.List<BookmarkView.Row> rows=new java.util.ArrayList<>();for(BookmarkStore.Node node:favoriteData.snapshot.nodes())rows.add(new BookmarkView.Row(node,node.title(),node.url()));FavoriteData original=favoriteData;favoritesPanel.bind(binding,original.revision,rows,favoritesPageAtOpen!=null,new BookmarkView.Actions(){
        public void open(BookmarkView.Row row,long revision){BookmarkStore.Node node=(BookmarkStore.Node)row.itemRef();favoriteWork(new FavoriteIntent(FavoriteIntent.OPEN,revision,node.id(),node.url(),null,null));}
        public void add(long revision){try{requireFavoritePage(favoritesPageAtOpen);favoriteWork(new FavoriteIntent(FavoriteIntent.ADD,revision,null,null,favoritesPageAtOpen.title,favoritesPageAtOpen.url));}catch(RuntimeException changed){favoritesPageAtOpen=null;toast("原网页已改变，未收藏其他页面");renderFavorites();}}
        public void edit(BookmarkView.Row row,long revision){editFavorite((BookmarkStore.Node)row.itemRef(),revision);}
        public void remove(BookmarkView.Row row,long revision){BookmarkStore.Node node=(BookmarkStore.Node)row.itemRef();if(editor!=null)return;BrowserToolbar.Binding exact=binding;AlertDialog dialog=new AlertDialog.Builder(BrowserActivity.this).setTitle("删除收藏？").setMessage(node.title()+"\n"+node.url()).setNegativeButton("取消",null).setPositiveButton("删除",(d,w)->{try{exact.check();favoriteWork(new FavoriteIntent(FavoriteIntent.REMOVE,revision,node.id(),node.url(),null,null));}catch(RuntimeException stale){toast("收藏窗口已改变，未删除");}}).create();editor=dialog;dialog.setOnDismissListener(d->{if(editor==dialog)editor=null;if(!favoriteBusy.get() && favoritesOverview)renderFavorites();});dialog.show();}
    });}
    private void editFavorite(BookmarkStore.Node node,long revision){requireWindow();if(editor!=null)return;BrowserToolbar.Binding exact=binding;LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(dp(18),0,dp(18),0);EditText title=new EditText(this),url=new EditText(this);for(EditText input:new EditText[]{title,url}){input.setSaveEnabled(false);input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);input.setSingleLine(true);form.addView(input);}TextView validation=new TextView(this);validation.setTextColor(0xffb3261e);validation.setVisibility(View.GONE);form.addView(validation);title.setHint("收藏标题");title.setContentDescription("收藏标题");url.setHint("收藏网址");url.setContentDescription("收藏网址");url.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);title.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(640)});url.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(8192)});title.setText(node.title());url.setText(node.url());AlertDialog dialog=new AlertDialog.Builder(this).setTitle("编辑收藏").setView(form).setNegativeButton("取消",null).setPositiveButton("保存",null).create();editor=dialog;dialog.setOnDismissListener(d->{title.setText("");url.setText("");if(editor==dialog)editor=null;if(!favoriteBusy.get() && favoritesOverview)renderFavorites();});dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{exact.check();String target=SearchEngines.requireWebUrl(url.getText().toString()).url(),label=title.getText().toString();favoriteWork(new FavoriteIntent(FavoriteIntent.EDIT,revision,node.id(),node.url(),label,target));dialog.dismiss();}catch(IllegalArgumentException invalid){validation.setText("网址不可用，未提交");validation.setVisibility(View.VISIBLE);}catch(RuntimeException changed){toast("收藏窗口已改变，未提交");}}));dialog.show();}
    private void toggleFavorite(FavoritePage page,FavoriteData data){requireFavoritePage(page);if(data==null)throw new SecurityException("Favorites projection unavailable");data.source.requireCurrent();BookmarkStore.Node existing=data.snapshot.findByUrl(page.url);favoriteWork(existing==null?new FavoriteIntent(FavoriteIntent.ADD,data.revision,null,null,page.title,page.url):new FavoriteIntent(FavoriteIntent.REMOVE,data.revision,existing.id(),existing.url(),null,null));}
    private void showBrowserMenu(){
        requireWindow();if(pendingMenuAction!=null || menu!=null && menu.isShowing())return;SessionPresentation.Frame frame=browser.selectedPresentation(browser.captureSelection(permit),permit);BrowserToolbar.Binding exact=binding;
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(8),dp(8),dp(8),dp(8));GradientDrawable bg=new GradientDrawable();bg.setColor(0xffffffff);bg.setCornerRadius(dp(20));body.setBackground(bg);
        FavoritePage favoritePage=favoritePage();FavoriteData favorites=favoriteData;BookmarkStore.Node saved=null;boolean favoriteReady=false;try{if(favoritePage!=null && favorites!=null){favorites.source.requireCurrent();saved=favorites.snapshot.findByUrl(favoritePage.url);favoriteReady=true;}}catch(RuntimeException stale){/* unread is not a default empty favorites set */}
        LinearLayout shortcuts=new LinearLayout(this);body.addView(shortcuts,new LinearLayout.LayoutParams(-1,dp(52)));
        menuIcon(shortcuts,R.drawable.ic_rb_back,"网页后退",frame!=null && frame.reportedCanGoBack(),()->ordinaryBack());menuIcon(shortcuts,R.drawable.ic_rb_forward,"网页前进",frame!=null && frame.reportedCanGoForward(),()->forward());menuIcon(shortcuts,saved==null?R.drawable.ic_rb_star_outline:R.drawable.ic_rb_star_filled,saved==null?"收藏当前网页":"取消收藏当前网页",favoriteReady,()->toggleFavorite(favoritePage,favorites));boolean observedManual=false,observedLandscape=false,directionKnown=true;
        try{observedManual=presentation.manualDirectionAllowed(preferences==null?null:preferences.deviceType());observedLandscape=presentation.landscapeObserved();}catch(RuntimeException unavailable){directionKnown=false;observedManual=false;}
        final boolean manualDirection=observedManual,landscape=observedLandscape;
        ImageButton direction=menuIcon(shortcuts,landscape?R.drawable.ic_rb_orientation_portrait:R.drawable.ic_rb_orientation_landscape,
                !directionKnown?"方向信息未能读取":!manualDirection?"平板方向跟随系统；小窗跟随窗口布局":landscape?"切换并锁定竖屏；长按取消方向锁定":"切换并锁定横屏；长按取消方向锁定",
                manualDirection && preferences!=null && !directionBusy.get(),()->requestDirection(landscape?WindowOrientation.PORTRAIT:WindowOrientation.LANDSCAPE));
        direction.setOnLongClickListener(v->{if(!manualDirection || preferences==null || directionBusy.get())return true;try{queueMenuAction(exact,()->requestDirection(WindowOrientation.FOLLOW_SYSTEM));}catch(RuntimeException unknown){toast("方向菜单交接未确认；未重试");}return true;});menuIcon(shortcuts,R.drawable.ic_rb_refresh,"刷新当前页",true,()->reloadPage());
        menuItem(body,"新建总标签页",()->createTemporary(browser.workspaces(permit).revision()));menuItem(body,"当前总标签页的子标签",()->showChildren());menuItem(body,"收藏夹",()->showFavorites());menuItem(body,"历史记录",()->startActivity(new Intent(this,HistoryActivity.class)));menuItem(body,"设置",()->startActivity(new Intent(this,SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_SINGLE_TOP)));
        TextView pending=new TextView(this);pending.setText("当前演示路径：临时环境；完整权限、持久恢复与下载仍待接通");pending.setTextSize(12);pending.setTextColor(0xff6b6472);pending.setPadding(dp(12),dp(8),dp(12),dp(8));body.addView(pending);
        ScrollView menuScroll=new ScrollView(this);menuScroll.setContentDescription("ReBrowser 菜单项目");menuScroll.addView(body,new ScrollView.LayoutParams(-1,-2));
        PopupWindow popup=new PopupWindow(menuScroll,dp(300),-2,true);popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);menu=popup;popup.setBackgroundDrawable(bg);popup.setElevation(dp(10));popup.setOutsideTouchable(true);popup.setOnDismissListener(()->{if(menu==popup)menu=null;MenuAction handoff=pendingMenuAction;if(handoff!=null && handoff.popup==popup){handoff.dismissed=true;releaseMenuAction();}scheduleRender();});exact.check();popup.showAsDropDown(toolbar.menuAnchor(),0,0,Gravity.END);
    }
    private ImageButton menuIcon(LinearLayout row,int drawable,String description,boolean enabled,Runnable intent){ImageButton b=new ImageButton(this);b.setImageResource(drawable);b.setContentDescription(description);GradientDrawable circle=new GradientDrawable();circle.setColor(0xfff1f2f6);circle.setShape(GradientDrawable.OVAL);b.setBackground(circle);b.setPadding(dp(12),dp(12),dp(12),dp(12));b.setEnabled(enabled);b.setAlpha(enabled?1f:.35f);BrowserToolbar.Binding exact=binding;b.setOnClickListener(v->{try{queueMenuAction(exact,intent);}catch(RuntimeException unknown){toast("操作未确认，未自动重试");}});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(48),1);p.setMargins(dp(2),0,dp(2),0);row.addView(b,p);return b;}
    private void menuItem(LinearLayout body,String title,Runnable intent){TextView item=new TextView(this);item.setText(title);item.setTextSize(15);item.setPadding(dp(16),dp(13),dp(16),dp(13));item.setMinimumHeight(dp(48));BrowserToolbar.Binding exact=binding;item.setOnClickListener(v->{try{queueMenuAction(exact,intent);}catch(RuntimeException unknown){toast("操作未确认，未自动重试");}});body.addView(item,new LinearLayout.LayoutParams(-1,-2));}
    /** Original owned menu action waits for actual popup dismissal AND main-window focus.
     * Dismiss()/SDK return alone cannot make a synchronous navigation prompt eligible. */
    private void queueMenuAction(BrowserToolbar.Binding original,Runnable action){
        original.check();if(original!=binding || pendingMenuAction!=null || menu==null)throw new SecurityException("Original native menu unavailable");
        pendingMenuAction=new MenuAction(menu,original,action);dismissMenu();
    }
    private void releaseMenuAction(){
        MenuAction pending=pendingMenuAction;
        if(pending==null || pending.queued || !pending.dismissed || !hasWindowFocus())return;
        if(!foreground || binding!=pending.binding || isFinishing() || isDestroyed()){pendingMenuAction=null;return;}
        pending.queued=true;WeakReference<BrowserActivity> weak=new WeakReference<>(this);
        if(!main.post(()->{BrowserActivity window=weak.get();if(window!=null)window.dispatchMenuAction();}))pendingMenuAction=null;
    }
    private void dispatchMenuAction(){
        MenuAction pending=pendingMenuAction;if(pending==null || !pending.queued)return;
        pending.queued=false;
        if(!foreground || binding!=pending.binding || isFinishing() || isDestroyed()){pendingMenuAction=null;return;}
        if(!pending.dismissed || !hasWindowFocus())return;
        pendingMenuAction=null; // One dispatch attempt, before any native effect; no positive retry.
        try{pending.binding.check();pending.action.run();}catch(RuntimeException unknown){toast("菜单操作未确认，未自动重试");}
    }
    private void dismissMenu(){PopupWindow current=menu;if(current!=null)current.dismiss();}
    private boolean backLayerPresent(BackCoordinator.Layer layer){
        if(layer==BackCoordinator.Layer.IME)return toolbar!=null && toolbar.hasInputFocus() || newTab!=null && newTab.hasInputFocus();
        if(layer==BackCoordinator.Layer.MODAL)return menu!=null && menu.isShowing() || editor!=null && editor.isShowing() || dialogs!=null && dialogs.hasPending() || nativePrompt!=null && nativePrompt.hasPending();
        if(layer==BackCoordinator.Layer.FULLSCREEN){if(presentation!=null && presentation.isOwnedFullscreen())return true;if(browser==null || binding==null)return false;SessionPresentation.Frame frame=browser.selectedPresentation(browser.captureSelection(permit),permit);return frame!=null && frame.reportedFullScreen();}
        if(layer==BackCoordinator.Layer.SELECTION)return display!=null && display.hasNativeSelection();
        if(layer==BackCoordinator.Layer.PANEL)return overview!=null && overview.getVisibility()==View.VISIBLE;
        if(layer==BackCoordinator.Layer.HISTORY){if(browser==null || binding==null)return false;SessionPresentation.Frame frame=browser.selectedPresentation(browser.captureSelection(permit),permit);return frame!=null && frame.reportedCanGoBack();}
        if(layer==BackCoordinator.Layer.WORKSPACE){if(browser==null || binding==null || preferences==null || newTab.getVisibility()==View.VISIBLE)return false;SessionPresentation.Frame frame=browser.selectedPresentation(browser.captureSelection(permit),permit);return frame!=null && frame.reportedLocation()!=null && !frame.reportedLocation().equals(preferences.homepage());}
        return layer==BackCoordinator.Layer.WINDOW_CLOSE && !isFinishing();
    }
    private void performBackLayer(BackCoordinator.Layer layer,BackCoordinator.Reporter reporter){
        if(layer==BackCoordinator.Layer.IME){boolean cleared=toolbar!=null && toolbar.dismissInput();if(newTab!=null)cleared=newTab.dismissInput()||cleared;if(cleared)reporter.reportConsumed();else reporter.reportNoOp();}
        else if(layer==BackCoordinator.Layer.MODAL){if(menu!=null){dismissMenu();reporter.reportConsumed();}else if(editor!=null){editor.dismiss();reporter.reportConsumed();}else if(nativePrompt!=null && nativePrompt.hasPending()){if(!nativePrompt.requestOwnedCancel(reporter::reportConsumed))reporter.reportNoOp();}else if(dialogs==null || !dialogs.requestOwnedCancel(reporter::reportConsumed))reporter.reportNoOp();}
        else if(layer==BackCoordinator.Layer.FULLSCREEN){requireWindow();Selection original=browser.captureSelection(permit);SessionPresentation.Frame frame=browser.selectedPresentation(original,permit);if(frame==null || !frame.reportedFullScreen()){restoreFullscreenChrome();reporter.reportConsumed();}else{fullscreenBackSession=browser.existingSelectedSession(original,permit);fullscreenBackSequence=frame.reportedFullScreenSequence();pendingFullscreenBack=reporter;browser.exitOwnedFullscreen(fullscreenBackSession,permit);scheduleRender();}}
        else if(layer==BackCoordinator.Layer.SELECTION){if(display==null || !display.requestNativeSelectionCancel(reporter::reportConsumed))reporter.reportNoOp();}
        else if(layer==BackCoordinator.Layer.PANEL){backOverview();reporter.reportConsumed();}
        else if(layer==BackCoordinator.Layer.HISTORY || layer==BackCoordinator.Layer.WORKSPACE){
            requireWindow();Selection selected=browser.captureSelection(permit);SessionPresentation.Frame frame=browser.selectedPresentation(selected,permit);backLocationSequence=frame==null?0:frame.reportedLocationSequence();backSession=browser.requireSelectedSession(permit);pendingHistoryBack=reporter;
            if(layer==BackCoordinator.Layer.HISTORY)browser.traverseSelected(selected,true,permit);else browser.navigateSelected(selected,preferences.homeTarget(),permit);scheduleRender(); // actual new location completes the layer, NOT Java return
        }else if(layer==BackCoordinator.Layer.WINDOW_CLOSE){long now=SystemClock.elapsedRealtime();if(browserComposed && (exitArmedAt<0 || now-exitArmedAt>2000)){exitArmedAt=now;toast("再按一次返回退出");reporter.reportConsumed();}else{closingWindow=reporter;finish();}}
        else reporter.reportNoOp();
    }
    @Override public void onBackPressed(){ownedBack();}
    private void ownedBack(){pendingMenuAction=null;if(nativeBack==null || nativeBack.hasPendingLayer())return;try{nativeBack.request(nativeBack.presentationRevision(),binding==null?null:permit);}catch(RuntimeException stale){toast("返回层状态已改变，请重新操作");}}
    @Override protected void onResume(){super.onResume();if(isFinishing())return;foreground=true;if(entryWatch==null){WeakReference<BrowserActivity> weak=new WeakReference<>(this);try{entryWatch=startup.observeNativeEntry(()->{BrowserActivity window=weak.get();if(window!=null && window.foreground)window.refreshEntry();});}catch(IllegalStateException occupied){toast("另一个原生主窗口仍持有显示，请返回原窗口");return;}}refreshEntry();deliverHistoryNavigation();}
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(foreground && display!=null && display.isAttached() && binding!=null)try{display.updateVisibility(true,focused,false,permit);}catch(RuntimeException stale){lockCurrentWindow();}if(foreground && focused && nativePrompt!=null)nativePrompt.onNativeWindowFocused();if(foreground && focused){releaseMenuAction();applyObservedDirection();}}
    @Override protected void onStop(){foreground=false;pendingMenuAction=null;restoreFullscreenChrome();favoriteData=null;favoritesPageAtOpen=null;if(favoritesOverview && overview!=null)hideOverview();if(favoritesPanel!=null){favoritesPanel.unbind();favoritesPanel.setVisibility(View.GONE);}favoritesOverview=false;preferences=null;preferencesRequest=null;if(entryWatch!=null){entryWatch.close();entryWatch=null;}if(watch!=null){watch.close();watch=null;}if(menu!=null)menu.dismiss();if(editor!=null)editor.dismiss();if(nativeBack!=null)nativeBack.presentationChanged();if(dialogs!=null)dialogs.cancelOwned();detachDisplay();retireBinding();super.onStop();}
    @Override protected void onDestroy(){
        if(nativeMainWindow.get()==this)nativeMainWindow.clear();pendingHistoryNavigation=null;favoriteData=null;favoritesPageAtOpen=null;if(favoritesPanel!=null)favoritesPanel.retire();
        if(Build.VERSION.SDK_INT>=33 && systemBack!=null)getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(systemBack);
        if(entryWatch!=null){entryWatch.close();entryWatch=null;}if(watch!=null){watch.close();watch=null;}detachDisplay();retireBinding();if(nativeBack!=null)nativeBack.retireWindowOwner();if(dialogs!=null){dialogs.retireWindow();dialogs=null;}if(presentation!=null){presentation.close();presentation=null;}
        if(closingWindow!=null){closingWindow.reportConsumed();closingWindow=null;}BrowserToolbar.clearOwnedViewTree(findViewById(android.R.id.content));preferences=null;preferencesRequest=null;super.onDestroy();
    }
    private void information(String text){try{if(dialogs!=null && !isFinishing() && !isDestroyed())dialogs.showInformation(text);}catch(RuntimeException unknown){toast("说明窗口状态未知");}}
    private void toast(String text){if(!isDestroyed())Toast.makeText(this,text,Toast.LENGTH_SHORT).show();}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
