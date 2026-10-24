package io.github.rebrowser.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.*;
import android.view.Gravity;
import android.graphics.*;
import android.graphics.drawable.*;
import io.github.rebrowser.ReBrowserApplication;
import io.github.rebrowser.bootstrap.*;
import io.github.rebrowser.browser.HistoryStore;
import java.util.*;
import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;

/** History keeps the existing three independent controls and paged native list. Maintenance
 * suspends expiry, clear removes timed records, the mask pauses recording for this PROCESS.
 * Actual private IO runs on one bounded worker; window teardown never claims an in-flight
 * transaction was cancelled. No URI/Intent supplies history authority, and no missing store is
 * silently initialized. A connected browser supplies navigation; this window never reopens an
 * old Profile simply because the owner selected a history row.
 */
public final class HistoryActivity extends Activity {
    public enum Control { MAINTAIN, CLEAR_TIMED, PAUSE_RECORDING }
    public static final class Controls {
        private final Boolean maintaining,paused;private final boolean pending;
        public Controls(Boolean maintaining,Boolean paused,boolean pending){this.maintaining=maintaining;this.paused=paused;this.pending=pending;}
        public Boolean maintaining(){return maintaining;}public Boolean paused(){return paused;}
        public boolean enabled(Control control){if(control==null)throw new IllegalArgumentException();return !pending && maintaining!=null && paused!=null && (control!=Control.CLEAR_TIMED || !maintaining);}
        public String label(Control c){if(c==Control.MAINTAIN)return Boolean.TRUE.equals(maintaining)?"结束维持记录，重新开始30分钟期限":"开始维持记录";if(c==Control.CLEAR_TIMED)return "立即清理所有有时限记录";return Boolean.TRUE.equals(paused)?"恢复历史记录，并同步当前打开网页":"暂停历史记录";}
    }
    public static final class ControlIntent {
        private final Control control;private final boolean enabled;private final long revision;
        public ControlIntent(Control control,boolean enabled,long expectedRevision){if(control==null || expectedRevision<1)throw new IllegalArgumentException("Invalid history control");this.control=control;this.enabled=enabled;revision=expectedRevision;}
        public Control control(){return control;}public boolean enabled(){return enabled;}public long expectedRevision(){return revision;}
    }
    // Query descriptors remain internal compatibility vocabulary, not replacement visible UI or
    // retention policies. Seven/thirty/ninety DAYS are NOT the history's thirty-MINUTE expiry.
    public enum Period { DAYS_7(7),DAYS_30(30),DAYS_90(90),ALL(0);public final int days;Period(int d){days=d;}public static Period fromDays(int d){for(Period p:values())if(p.days==d)return p;throw new IllegalArgumentException("Unsupported period");}}
    public enum PageDirection { PREVIOUS,NEXT }
    public static final class PageNav {private final Boolean previous,next;public PageNav(Boolean p,Boolean n){previous=p;next=n;}public Boolean previousAvailable(){return previous;}public Boolean nextAvailable(){return next;}public boolean available(PageDirection d){return Boolean.TRUE.equals(d==PageDirection.PREVIOUS?previous:next);}}
    public static final class PageRequest {private final PageDirection direction;private final long revision;public PageRequest(PageDirection d,long r){if(d==null || r<1)throw new IllegalArgumentException();direction=d;revision=r;}public PageDirection direction(){return direction;}public long expectedRevision(){return revision;}public boolean isApplied(){return false;}}
    public static final class QueryIntent {private final Period period;private final boolean pause;private final Object token;public QueryIntent(Period p,boolean pause,Object t){if(p==null)throw new IllegalArgumentException();period=p;this.pause=pause;token=t;}public Period period(){return period;}public boolean pauseWrites(){return pause;}public Object expectedPageToken(){return token;}public boolean isApplied(){return false;}}
    public interface Actions {
        void open(HistoryAdapter.Row row,long expectedRevision);
        void retain(HistoryAdapter.Row row,long expectedRevision);
        void remove(HistoryAdapter.Row row,long expectedRevision);
        void changeQuery(QueryIntent intent);
        void requestPage(PageRequest request);
        default void control(ControlIntent intent){throw new UnsupportedOperationException("History controls not connected");}
    }
    public static final class Filter {
        public static final int MAX_FILTER_CHARS=64;
        public static List<HistoryAdapter.Row> apply(List<HistoryAdapter.Row> rows,String literal){HistoryAdapter.Rows.validate(rows,1);if(literal==null || literal.isEmpty())return new ArrayList<>(rows);io.github.rebrowser.core.DomainResult.text(literal,MAX_FILTER_CHARS,false);String n=literal.trim().toLowerCase(Locale.ROOT);List<HistoryAdapter.Row> out=new ArrayList<>();for(HistoryAdapter.Row r:rows)if(r.title().toLowerCase(Locale.ROOT).contains(n) || r.displayUrl().toLowerCase(Locale.ROOT).contains(n))out.add(r);return out;}
    }
    private TextView status;private ImageButton maintain,clear,pause;private Button more;
    private ListView list;private HistoryAdapter adapter;private BrowserToolbar.Binding binding;private Actions actions;
    private Controls controls=new Controls(null,null,false);private PageNav pageNav=new PageNav(null,null);
    private Object frame;private long pageRevision;private boolean actionPending;private int nativeLimit=50;
    private NativeIo nativeIo;private long nativeRequest;private boolean resumed;private WindowPresentation presentation;private BrowserActivity.HistoryTarget nativeTarget;private StartupCoordinator.EntrySubscription sourceWatch;
    /** Only the actual canonical IO lookup below can construct this private-process receipt.
     * It is a normal URL-navigation handoff, NOT a document/administrator/data-read grant. */
    static final class OpenReceipt {
        private final ComponentGate.DataEpochPin source;private final io.github.rebrowser.browser.SearchEngines.Target target;private final Long expires;private final long issued;private final AtomicBoolean spent=new AtomicBoolean();
        private OpenReceipt(ComponentGate.DataEpochPin source,HistoryStore.Entry entry,boolean maintaining,long issued){this.source=source;target=io.github.rebrowser.browser.SearchEngines.requireWebUrl(entry.url());expires=maintaining?null:entry.expiresUtcMs();this.issued=issued;}
        void requireCurrent(){requireAt(android.os.SystemClock.elapsedRealtime(),System.currentTimeMillis());}
        private void requireAt(long elapsed,long utc){source.requireCurrent();if(spent.get() || issued<0 || utc<0 || elapsed<issued || elapsed-issued>30_000 || expires!=null && utc>=expires)throw new SecurityException("Native history handoff expired/spent");}
        io.github.rebrowser.browser.SearchEngines.Target consume(){return consumeAt(android.os.SystemClock.elapsedRealtime(),System.currentTimeMillis());}
        private io.github.rebrowser.browser.SearchEngines.Target consumeAt(long elapsed,long utc){requireAt(elapsed,utc);if(!spent.compareAndSet(false,true))throw new SecurityException("History handoff already spent");source.requireCurrent();return target;}
    }
    static HistoryStore.Entry requireOpenEntry(HistoryStore.Snapshot current,String id,String expectedUrl,long now){if(current==null)throw new SecurityException("Native history not available");HistoryStore.Entry entry=current.findVisible(id,now);if(entry==null || !entry.url().equals(expectedUrl))throw new SecurityException("Original history record deleted/expired/changed");return entry;}

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xfff7f8fc);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(8),dp(8),dp(8),dp(8));
        TextView back=new TextView(this);back.setText("‹");back.setTextSize(32);back.setGravity(Gravity.CENTER);back.setContentDescription("返回网页");back.setOnClickListener(v->finish());header.addView(back,new LinearLayout.LayoutParams(dp(44),dp(48)));
        LinearLayout labels=new LinearLayout(this);labels.setOrientation(LinearLayout.VERTICAL);TextView title=new TextView(this);title.setText("历史记录");title.setTextSize(19);title.setTypeface(null,Typeface.BOLD);labels.addView(title);status=new TextView(this);status.setTextSize(11);status.setText("正在读取历史记录…");labels.addView(status);header.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        maintain=icon(header,Control.MAINTAIN);clear=icon(header,Control.CLEAR_TIMED);pause=icon(header,Control.PAUSE_RECORDING);root.addView(header,new LinearLayout.LayoutParams(-1,dp(72)));
        list=new ListView(this);adapter=new HistoryAdapter(this);more=new Button(this);more.setText("加载更早记录");more.setOnClickListener(v->{if(actions!=null)requestPage(PageDirection.NEXT,actions);});list.addFooterView(more,null,false);list.setAdapter(adapter);root.addView(list,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);presentation=new WindowPresentation(this);presentation.attachNativeContent(root);renderControls();
        if(getApplication() instanceof ReBrowserApplication){StartupCoordinator startup=((ReBrowserApplication)getApplication()).primaryStartup();try{ComponentGate.BusinessPermit permit=startup.gate().requireBusiness();nativeIo=new NativeIo(startup,permit,this);nativeTarget=BrowserActivity.captureHistoryTarget();loadNative(null);}catch(RuntimeException unavailable){showUnavailable("来源未就绪，未读取历史数据");}}
        else showUnavailable("未连接原生历史窗口");
    }
    private ImageButton icon(LinearLayout parent,Control control){ImageButton b=new ImageButton(this);b.setPadding(dp(10),dp(10),dp(10),dp(10));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(44),dp(44));p.setMargins(dp(3),0,dp(3),0);parent.addView(b,p);b.setOnClickListener(v->requestControl(control));return b;}
    private void renderControls(){if(maintain==null)return;for(Control c:Control.values()){ImageButton b=c==Control.MAINTAIN?maintain:c==Control.CLEAR_TIMED?clear:pause;boolean active=c==Control.MAINTAIN?Boolean.TRUE.equals(controls.maintaining):c==Control.PAUSE_RECORDING && Boolean.TRUE.equals(controls.paused);b.setImageDrawable(new ControlIcon(c,active));GradientDrawable bg=new GradientDrawable();bg.setColor(active?(c==Control.MAINTAIN?0xffffebee:0xffebe1ff):Color.WHITE);bg.setCornerRadius(dp(24));b.setBackground(bg);b.setEnabled(binding!=null && actions!=null && !actionPending && controls.enabled(c));b.setAlpha(b.isEnabled()?1f:.35f);b.setSelected(active);b.setContentDescription(controls.label(c));}if(more!=null)more.setEnabled(!actionPending && pageNav.available(PageDirection.NEXT));}
    public void requestControl(Control control){BrowserToolbar.main();if(binding==null || actions==null || control==null || actionPending || !controls.enabled(control))return;binding.check();final Object expected=frame;final Actions callback=actions;boolean value=control==Control.MAINTAIN?!Boolean.TRUE.equals(controls.maintaining):control==Control.PAUSE_RECORDING?!Boolean.TRUE.equals(controls.paused):false;actionPending=true;renderControls();BrowserToolbar.IntentOutcome result=BrowserToolbar.invokeNativeIntent(()->{binding.check();if(frame==expected && callback==actions)callback.control(new ControlIntent(control,value,pageRevision));});if(result==BrowserToolbar.IntentOutcome.CALLBACK_UNKNOWN && frame==expected)status.setText("历史操作结果未知，请重新读取；不会自动重试");}
    public void showPage(BrowserToolbar.Binding b,long revision,List<HistoryAdapter.Row> rows,Actions a){showPage(b,revision,rows,new PageNav(null,null),a);}
    public void showPage(BrowserToolbar.Binding b,long revision,List<HistoryAdapter.Row> rows,PageNav nav,Actions a){showPage(b,revision,rows,nav,new Controls(null,null,false),a);}
    public void showPage(BrowserToolbar.Binding b,long revision,List<HistoryAdapter.Row> rows,PageNav nav,Controls state,Actions a){
        BrowserToolbar.main();if(b==null || nav==null || state==null || a==null)throw new IllegalArgumentException();b.check();HistoryAdapter.Rows.validate(rows,revision);binding=b;actions=a;pageRevision=revision;pageNav=nav;controls=state;frame=new Object();actionPending=false;final Object expected=frame;
        adapter.bind(b,revision,rows,new HistoryAdapter.Actions(){public void open(HistoryAdapter.Row row,long r){if(frame==expected && actions==a)a.open(row,r);}public void retain(HistoryAdapter.Row row,long r){/* no per-record pin replaces workspace retention */}public void remove(HistoryAdapter.Row row,long r){if(frame!=expected || actions!=a || actionPending)return;new AlertDialog.Builder(HistoryActivity.this).setTitle("删除历史记录？").setMessage(row.title()).setOnCancelListener(d->{if(frame==expected && binding==b && actions==a){if(nativeIo!=null)loadNative(null);else showPage(b,revision,rows,nav,state,a);}}).setNegativeButton("取消",(d,w)->{if(frame==expected && binding==b && actions==a){if(nativeIo!=null)loadNative(null);else showPage(b,revision,rows,nav,state,a);}}).setPositiveButton("删除",(d,w)->{if(frame==expected && binding==b && actions==a){b.check();a.remove(row,r);}}).show();}});
        status.setText(rows.size()+" 条已显示 · "+(Boolean.TRUE.equals(state.paused)?"暂停记录":Boolean.TRUE.equals(state.maintaining)?"维持中":"最新在前"));more.setVisibility(nav.available(PageDirection.NEXT)?android.view.View.VISIBLE:android.view.View.GONE);renderControls();
    }
    public void requestPage(PageDirection d,Actions a){BrowserToolbar.main();if(binding==null || a==null || a!=actions || frame==null)throw new IllegalStateException("History disconnected");if(d==null)throw new IllegalArgumentException();if(actionPending || !pageNav.available(d))return;binding.check();actionPending=true;renderControls();BrowserToolbar.IntentOutcome outcome=BrowserToolbar.invokeNativeIntent(()->a.requestPage(new PageRequest(d,pageRevision)));if(outcome==BrowserToolbar.IntentOutcome.CALLBACK_UNKNOWN)status.setText("读取结果未知，不会自动重试");}
    public void requestQuery(Period period,boolean pauseWrites,Actions a){BrowserToolbar.main();if(binding==null || a!=actions)throw new IllegalStateException();binding.check();a.changeQuery(new QueryIntent(period,pauseWrites,frame));}
    public void showUnavailable(String reason){BrowserToolbar.main();if(adapter!=null)adapter.unbind();if(binding!=null)binding.retire();binding=null;actions=null;frame=null;controls=new Controls(null,null,false);actionPending=false;pageNav=new PageNav(null,null);if(status!=null)status.setText(reason==null?"历史不可用":boundedReason(reason));if(more!=null)more.setVisibility(android.view.View.GONE);renderControls();}

    /** Actual native IO, not an injected boolean completion backend. Uses the startup's SAME
     * bounded browser-data lane as capture/new-tab/settings; one pending operation per window,
     * weak recipient, source checks before/after each fixed private operation. */
    private static final class NativeIo {
        final StartupCoordinator startup;final ComponentGate.BusinessPermit permit;final WeakReference<HistoryActivity> window;final android.os.Handler main=new android.os.Handler(android.os.Looper.getMainLooper());final AtomicBoolean busy=new AtomicBoolean();
        volatile boolean closed;
        NativeIo(StartupCoordinator startup,ComponentGate.BusinessPermit permit,HistoryActivity window){this.startup=startup;this.permit=permit;this.window=new WeakReference<>(window);}
        void submit(long request,int limit,Mutation mutation){if(closed || !busy.compareAndSet(false,true))throw new IllegalStateException("历史IO尚未结束");try{startup.executeBrowserDataIo(permit,()->{HistoryStore store=null;List<HistoryStore.Entry> page=null;boolean hold=false,paused=false,hasMore=false;String failure=null;try{if(closed)return;startup.gate().checkCurrent(permit);store=startup.openExistingHistoryStoreOnIo(permit);long now=System.currentTimeMillis();if(mutation!=null)mutation.run(store,now,permit);HistoryStore.Snapshot snapshot=store.readCurrent(now,permit);page=new ArrayList<>(snapshot.entries().subList(0,Math.min(limit,snapshot.entries().size())));hold=snapshot.maintaining();paused=store.paused();hasMore=snapshot.entries().size()>page.size();startup.gate().checkCurrent(permit);}catch(Exception unavailable){failure="历史读取或操作未确认完成，请重新打开；未自动创建或重试";}finally{busy.set(false);}final HistoryStore resultStore=store;final List<HistoryStore.Entry> rows=page;final boolean maintain=hold,pause=paused,more=hasMore;final String error=failure;main.post(()->{HistoryActivity current=window.get();if(!closed && current!=null && !current.isDestroyed() && !current.isFinishing() && current.resumed){if(current.nativeRequest==request)current.nativeResult(resultStore,rows,maintain,pause,more,error);else if(current.binding==null && !busy.get())current.loadNative(null);}});});}catch(RuntimeException failure){busy.set(false);throw failure;}}
        void open(long request,HistoryStore.Entry original){
            if(closed || original==null || !busy.compareAndSet(false,true))throw new IllegalStateException("Native history operation already pending");
            try{startup.executeBrowserDataIo(permit,()->{
                OpenReceipt resolved=null;
                try{if(closed)return;startup.gate().checkCurrent(permit);HistoryStore store=startup.openExistingHistoryStoreOnIo(permit);long now=System.currentTimeMillis();HistoryStore.Snapshot current=store.readCurrent(now,permit);HistoryStore.Entry actual=requireOpenEntry(current,original.id(),original.url(),System.currentTimeMillis());resolved=new OpenReceipt(startup.gate().captureDataEpoch(permit),actual,current.maintaining(),android.os.SystemClock.elapsedRealtime());resolved.requireCurrent();}
                catch(Exception unavailable){/* no fallback URL/default/session or retry */}
                finally{busy.set(false);}
                final OpenReceipt receipt=resolved;main.post(()->{HistoryActivity current=window.get();if(!closed && current!=null && current.resumed && !current.isFinishing() && !current.isDestroyed() && current.nativeRequest==request){if(receipt==null){current.showUnavailable("原历史记录已改变、到期或未能核对；未打开网页");return;}try{if(current.binding==null || current.nativeTarget==null)throw new SecurityException("Original native target missing");current.binding.check();receipt.requireCurrent();current.nativeTarget.stage(receipt);current.finish();}catch(RuntimeException retired){current.showUnavailable("原浏览器标签或来源已改变；未改用其他目标打开");}}});
            });}catch(java.util.concurrent.RejectedExecutionException knownUnsubmitted){busy.set(false);throw knownUnsubmitted;}
        }
        void close(){closed=true;window.clear();} // shared process IO is NOT shutdown by one window; old work still owns its slot
    }
    private interface Mutation {void run(HistoryStore store,long now,ComponentGate.BusinessPermit permit)throws Exception;}
    private void loadNative(Mutation mutation){if(nativeIo==null || nativeIo.closed)return;long request=++nativeRequest;actionPending=true;renderControls();try{nativeIo.submit(request,nativeLimit,mutation);}catch(RuntimeException unavailable){showUnavailable("历史IO忙碌或不可用，未重复请求");}}
    private void nativeResult(HistoryStore store,List<HistoryStore.Entry> entries,boolean hold,boolean paused,boolean hasMore,String failure){
        if(failure!=null || entries==null){showUnavailable(failure);return;}if(binding!=null)binding.retire();BrowserToolbar.Binding current=new BrowserToolbar.Binding(nativeIo.startup.gate(),nativeIo.permit,()->{if(!resumed || isFinishing() || isDestroyed())throw new SecurityException("History window retired");});List<HistoryAdapter.Row> rows=new ArrayList<>();for(HistoryStore.Entry e:entries){String detail=java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.SHORT).format(new Date(e.visitedUtcMs()))+" · "+e.sourceTitle()+" · "+(hold?"维持中":e.timed()?"30分钟":"长期");String title=e.title().isEmpty()?e.url():e.title();rows.add(new HistoryAdapter.Row(e,title,e.url(),detail,nativeTarget==null?EnumSet.of(HistoryAdapter.Offered.REMOVE):EnumSet.of(HistoryAdapter.Offered.OPEN,HistoryAdapter.Offered.REMOVE),"原浏览器窗口不可用"));}
        showPage(current,Math.max(1,nativeRequest),rows,new PageNav(false,hasMore),new Controls(hold,paused,false),new Actions(){
            public void open(HistoryAdapter.Row row,long revision){if(actionPending || binding==null || nativeTarget==null || revision!=pageRevision || !(row.itemRef() instanceof HistoryStore.Entry))return;binding.check();actionPending=true;renderControls();long request=++nativeRequest;try{nativeIo.open(request,(HistoryStore.Entry)row.itemRef());}catch(RuntimeException unavailable){showUnavailable("历史打开未能确认提交；不自动重试");}}
            public void retain(HistoryAdapter.Row row,long revision){throw new UnsupportedOperationException("Retention follows workspace");}
            public void remove(HistoryAdapter.Row row,long revision){if(!(row.itemRef() instanceof HistoryStore.Entry))throw new SecurityException();HistoryStore.Entry e=(HistoryStore.Entry)row.itemRef();loadNative((owner,now,p)->owner.removeExact(e.id(),e.url(),now,p));}
            public void changeQuery(QueryIntent ignored){throw new UnsupportedOperationException("Not a visible history policy");}
            public void requestPage(PageRequest request){nativeLimit=Math.min(HistoryAdapter.MAX_ROWS,nativeLimit+50);loadNative(null);}
            public void control(ControlIntent intent){if(intent.control==Control.PAUSE_RECORDING){List<HistoryStore.Page> pages=intent.enabled?Collections.emptyList():nativeIo.startup.captureOpenHistoryPages(nativeIo.permit);HistoryStore.Change change=store.setPaused(intent.enabled);loadNative(change.needsCurrentPageSync()?(owner,now,p)->owner.resumeCurrentPages(change,pages,now,p):null);}else if(intent.control==Control.MAINTAIN)loadNative((owner,now,p)->owner.setMaintaining(intent.enabled,now,p));else loadNative((owner,now,p)->owner.clearTimed(p));}
        });
        if(nativeIo.startup.historyCaptureIncomplete())status.append(" · 部分历史更新未成功");
    }
    @Override protected void onResume(){super.onResume();resumed=true;if(nativeIo!=null){WeakReference<HistoryActivity> weak=new WeakReference<>(this);if(sourceWatch==null)sourceWatch=nativeIo.startup.observeNativeEntry(()->{HistoryActivity current=weak.get();if(current!=null && current.resumed)try{current.nativeIo.startup.gate().checkCurrent(current.nativeIo.permit);}catch(RuntimeException lost){++current.nativeRequest;current.showUnavailable("来源已改变，历史展示及操作已停止");}});if(!nativeIo.busy.get() && binding==null)loadNative(null);}}
    @Override protected void onStop(){resumed=false;if(sourceWatch!=null){sourceWatch.close();sourceWatch=null;}++nativeRequest;if(binding!=null)binding.retire();showUnavailable("返回后重新读取历史记录");super.onStop();}
    @Override protected void onDestroy(){if(presentation!=null){presentation.close();presentation=null;}if(nativeIo!=null)nativeIo.close();nativeTarget=null;if(adapter!=null)adapter.retire();binding=null;actions=null;frame=null;if(list!=null)list.setAdapter(null);BrowserToolbar.clearOwnedViewTree(findViewById(android.R.id.content));super.onDestroy();}
    static String boundedReason(String value){return value==null?"":BrowserToolbar.boundedStatusReason(value);}
    static List<HistoryAdapter.Row> emptyPage(){return Collections.emptyList();}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    /** Same deterministic triangle/square, 30-arrow and mask vocabulary as the existing UI. */
    private static final class ControlIcon extends Drawable {
        final Control control;final boolean active;private int alpha=255;final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);ControlIcon(Control c,boolean a){control=c;active=a;}
        @Override public void draw(Canvas c){int save=c.save();c.translate(getBounds().left,getBounds().top);c.scale(getBounds().width()/32f,getBounds().height()/32f);p.setColor(active?(control==Control.MAINTAIN?0xffdc3c4b:0xff7350c8):0xff9fa4b0);p.setAlpha(alpha);p.setStyle(Paint.Style.FILL);Path path=new Path();if(control==Control.MAINTAIN){if(active)c.drawRoundRect(9,9,23,23,2,2,p);else{path.moveTo(11,7);path.lineTo(25,16);path.lineTo(11,25);path.close();c.drawPath(path,p);}}else if(control==Control.CLEAR_TIMED){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.8f);c.drawArc(4,4,28,28,35,290,false,p);p.setStyle(Paint.Style.FILL);path.moveTo(23,3);path.lineTo(30,9);path.lineTo(22,11);path.close();c.drawPath(path,p);p.setTextSize(10);p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.DEFAULT_BOLD);c.drawText("30",16,20,p);}else{path.moveTo(3,10);path.cubicTo(9,6,12,8,16,10);path.cubicTo(20,8,23,6,29,10);path.lineTo(27,21);path.cubicTo(22,27,19,23,16,19);path.cubicTo(13,23,10,27,5,21);path.close();c.drawPath(path,p);p.setColor(Color.WHITE);c.drawOval(7,13,13,17,p);c.drawOval(19,13,25,17,p);}c.restoreToCount(save);}
        public void setAlpha(int a){alpha=a;invalidateSelf();}public void setColorFilter(ColorFilter f){p.setColorFilter(f);invalidateSelf();}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
}
