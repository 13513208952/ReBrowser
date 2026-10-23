package io.github.rebrowser.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.ImageDecoder;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import io.github.rebrowser.R;
import io.github.rebrowser.bootstrap.ComponentGate;
import io.github.rebrowser.bootstrap.StartupCoordinator;
import io.github.rebrowser.browser.HistoryStore;
import io.github.rebrowser.browser.SearchEngines;
import io.github.rebrowser.data.OwnedDataCatalog;
import io.github.rebrowser.settings.PersonalizationPolicy;
import io.github.rebrowser.settings.SettingsRepository;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Approved native new-tab surface, not a webpage and not a favorites manager. One ACTUAL
 * configured engine, its packaged brand artwork, the common search/address resolver, and at
 * most eight distinct real-history URLs. Private data never enters website DOM/JS.
 * bindNative reads existing canonical settings/history off the UI thread. Missing settings do
 * not guess Bing/fresh-install defaults; optional unavailable history just hides the recent row.
 * The parent still owns the browser window, toolbar appearance, navigation and global Back.
 */
public final class NewTabView extends FrameLayout {
    public static final int MAX_SHORTCUTS=8,MAX_TITLE_CHARS=640,MAX_HOST_CHARS=320,MAX_QUERY_CHARS=8192;
    private static final int MAX_BACKGROUND_PIXELS=4*1024*1024;
    /** Native intent only. This callback must use the SAME actual browser window/environment's
     * shared navigation service. A callback return is not a navigation/effect receipt. */
    public interface Navigation { void navigate(SearchEngines.Target target); }

    private static final class RecentRef {
        final String id,url;
        RecentRef(HistoryStore.Entry entry){id=entry.id();url=entry.url();}
    }
    /** Display-only; arbitrary caller tokens cannot be resolved as native history by this view. */
    public static final class Shortcut {
        private final Object token;private final String title,host;
        public Shortcut(Object token,String title,String hostLabel){
            if(token==null)throw invalid();this.token=token;this.title=text(title,MAX_TITLE_CHARS);host=text(hostLabel,MAX_HOST_CHARS);
            for(int i=0;i<host.length();i++)if(Character.isWhitespace(host.charAt(i)) || Character.isSpaceChar(host.charAt(i)))throw invalid();
        }
        public Object token(){return token;}public String title(){return title;}public String hostLabel(){return host;}
        public String badge(){return new String(Character.toChars(host.codePointAt(0))).toUpperCase(Locale.ROOT);}
    }
    public static final class Query {
        public static String require(String raw){if(raw==null || raw.length()>MAX_QUERY_CHARS || raw.trim().isEmpty())throw new IllegalArgumentException("请输入搜索内容或网址");return text(raw,MAX_QUERY_CHARS);}
    }
    /** Per-render explicit-intent guard, NOT task/IO ownership. Unknown handoff is never reset
     * just because a callback returned/threw. A new native binding creates a new guard. */
    public static final class Submission {
        private int state;
        public boolean begin(){if(state!=0)return false;state=1;return true;}
        public void preparationFailed(){if(state!=1)throw new IllegalStateException("No pending preparation");state=0;}
        public void spendBeforeHandoff(){if(state!=1)throw new IllegalStateException("No pending preparation");state=2;}
        public boolean handedOff(){return state==2;}
    }
    public static final class Model {
        private final SettingsRepository.Snapshot settings;private final List<Shortcut> shortcuts;
        public Model(SettingsRepository.Snapshot settings,List<Shortcut> shortcuts){
            if(settings==null || shortcuts==null || shortcuts.size()>MAX_SHORTCUTS)throw invalid();
            Set<Object> tokens=Collections.newSetFromMap(new java.util.IdentityHashMap<>());List<Shortcut> copy=new ArrayList<>();
            for(Shortcut s:shortcuts){if(s==null || !tokens.add(s.token))throw invalid();copy.add(s);}
            this.settings=settings;this.shortcuts=settings.newTabRecentEnabled()?Collections.unmodifiableList(copy):Collections.emptyList();
        }
        public static Model fromNative(SettingsRepository.Snapshot settings,HistoryStore.Snapshot history,long now){
            if(settings==null || now<0)throw invalid();List<Shortcut> rows=new ArrayList<>();
            if(settings.newTabRecentEnabled() && history!=null)for(HistoryStore.Entry e:history.recentEntries(now)){
                String host=URI.create(e.url()).getRawAuthority();if(host.startsWith("www."))host=host.substring(4);
                rows.add(new Shortcut(new RecentRef(e),e.title().isEmpty()?host:e.title(),host));
            }
            return new Model(settings,rows);
        }
        public List<Shortcut> shortcuts(){return shortcuts;}public boolean empty(){return shortcuts.isEmpty();}
        public SearchEngines.Provider provider(){return settings.searchProvider();}
        public boolean photo(){return settings.newTabBackground()==SettingsRepository.NewTabBackground.EXCLUSIVE;}
        /** The inherited signature has THREE relevant preferences. Unrelated app enablement
         * or a metadata revision alone must not interrupt typing or invent a new confirmation. */
        public boolean sameSettings(SettingsRepository.Snapshot current){return current!=null && provider()==current.searchProvider() && settings.newTabBackground()==current.newTabBackground() && settings.newTabRecentEnabled()==current.newTabRecentEnabled();}
        /** Pure live-row check. Runtime calls this on a fresh, source-bound canonical store read.
         * A removed/expired/replaced record cannot be reopened from the displayed cached URL. */
        public static SearchEngines.Target resolveRecent(Shortcut original,HistoryStore.Snapshot current,long now){
            if(original==null || !(original.token instanceof RecentRef) || current==null)throw invalid();RecentRef ref=(RecentRef)original.token;
            HistoryStore.Entry live=current.findVisible(ref.id,now);if(live==null || !live.url().equals(ref.url))throw new IllegalStateException("这条访问记录已删除或到期");
            return SearchEngines.requireWebUrl(live.url());
        }
        public static List<SearchEngines.Provider> providers(){return Collections.unmodifiableList(java.util.Arrays.asList(SearchEngines.Provider.BING,SearchEngines.Provider.BAIDU,SearchEngines.Provider.DUCKDUCKGO,SearchEngines.Provider.GOOGLE));}
        public static String providerLabel(SearchEngines.Provider p){if(p==null)throw invalid();switch(p){case BING:return "必应";case BAIDU:return "百度";case DUCKDUCKGO:return "DuckDuckGo";case GOOGLE:return "Google";default:throw invalid();}}
        public int logoResource(){switch(provider()){case BING:return photo()?R.drawable.rb_brand_bing_logo_photo:R.drawable.rb_brand_bing_logo;case BAIDU:return R.drawable.rb_brand_baidu_logo;case DUCKDUCKGO:return photo()?R.drawable.rb_brand_duckduckgo_logo_photo:R.drawable.rb_brand_duckduckgo_logo;case GOOGLE:return photo()?R.drawable.rb_brand_google_logo_light_photo:R.drawable.rb_brand_google_logo_photo;default:throw invalid();}}
        public int iconResource(){switch(provider()){case BING:return R.drawable.rb_brand_bing_icon;case BAIDU:return R.drawable.rb_brand_baidu_icon;case DUCKDUCKGO:return R.drawable.rb_brand_duckduckgo_icon;case GOOGLE:return R.drawable.rb_brand_google_icon;default:throw invalid();}}
    }

    private final LinearLayout body,recent,searchBox;
    private final FrameLayout logoPanel,logoSlot;
    private final ImageView logo,engine;
    private final TextView registered;
    private final EditText input;
    private final ImageButton submit;
    private final Handler main=new Handler(Looper.getMainLooper());
    private BrowserToolbar.Binding binding;private NativeOwner owner;private Navigation navigation;
    private Model model;private Object frame;private Submission submission=new Submission();private boolean loading;
    private int stableWidth,stableHeight;private long artworkRevision;private boolean decoding;private int decodedWidth,decodedHeight;
    public interface PhotoAppearance {void changed(boolean photo);}
    private WeakReference<View> backdropRoot=new WeakReference<>(null);private PhotoAppearance photoAppearance;
    /** Actual native ANCESTOR only: approved photograph spans the toolbar/system-inset root,
     * not merely a220dp block or the scrolling content. No URI or window authority is granted. */
    public void shareNativeBackdrop(View ownAncestor,PhotoAppearance appearance){
        BrowserToolbar.main();if(ownAncestor==null || appearance==null || ownAncestor==this)throw invalid();android.view.ViewParent parent=getParent();int depth=0;while(parent!=null && parent!=ownAncestor && ++depth<=64)parent=parent.getParent();if(parent!=ownAncestor)throw invalid();backdropRoot=new WeakReference<>(ownAncestor);photoAppearance=appearance;setBackgroundColor(0x00000000);applyBackdrop(false,null);
    }
    private void applyBackdrop(boolean photo,Drawable pixels){View root=backdropRoot.get();View target=root==null?this:root;if(pixels==null)target.setBackgroundColor(photo?0xff302653:0xffffffff);else target.setBackground(pixels);if(root!=null)setBackgroundColor(0x00000000);if(photoAppearance!=null)photoAppearance.changed(photo);}

    public NewTabView(Context context){
        super(context);BrowserToolbar.main();setBackgroundColor(0xffffffff);setSaveEnabled(false);
        ScrollView scroll=new ScrollView(context);scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.setVerticalScrollBarEnabled(false);addView(scroll,new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT));
        FrameLayout content=new FrameLayout(context);content.setFocusableInTouchMode(true);scroll.addView(content,new ScrollView.LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT));
        body=new LinearLayout(context);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(0,dp(44),0,dp(28));
        LayoutParams bodyParams=new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT,Gravity.TOP|Gravity.CENTER_HORIZONTAL);bodyParams.setMargins(dp(18),0,dp(18),0);content.addView(body,bodyParams);
        logoSlot=new FrameLayout(context);logoPanel=new FrameLayout(context);
        logo=new ImageView(context);logo.setScaleType(ImageView.ScaleType.FIT_CENTER);logoPanel.addView(logo,new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT));
        registered=label("®",9,0xff333333);registered.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);LayoutParams mark=new LayoutParams(dp(12),dp(16),Gravity.END|Gravity.TOP);mark.topMargin=dp(12);logoPanel.addView(registered,mark);
        logoSlot.addView(logoPanel,new LayoutParams(dp(240),dp(84),Gravity.CENTER));body.addView(logoSlot,new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,dp(84)));logoSlot.setVisibility(INVISIBLE);
        searchBox=new LinearLayout(context);searchBox.setGravity(Gravity.CENTER_VERTICAL);searchBox.setPadding(dp(16),0,dp(8),0);searchBox.setBackground(surface(0xfff0f1f5,32,false));
        engine=new ImageView(context);engine.setScaleType(ImageView.ScaleType.FIT_CENTER);searchBox.addView(engine,new LinearLayout.LayoutParams(dp(28),dp(28)));
        input=new EditText(context);input.setSingleLine(true);input.setSaveEnabled(false);input.setImportantForAutofill(IMPORTANT_FOR_AUTOFILL_NO);input.setTextSize(16);input.setTextColor(0xff282633);input.setHintTextColor(0xff686472);input.setHint("搜索或输入网址");input.setContentDescription("新标签页搜索或输入网址");input.setBackgroundColor(0x00000000);input.setPadding(dp(12),0,dp(4),0);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);input.setImeOptions(EditorInfo.IME_ACTION_GO);input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MAX_QUERY_CHARS)});
        input.setOnEditorActionListener((v,action,event)->{if(action!=EditorInfo.IME_ACTION_GO && action!=EditorInfo.IME_ACTION_SEARCH && !(event!=null && event.getKeyCode()==KeyEvent.KEYCODE_ENTER && event.getAction()==KeyEvent.ACTION_UP))return false;submitInput();return true;});searchBox.addView(input,new LinearLayout.LayoutParams(0,dp(62),1));
        submit=new ImageButton(context);submit.setImageResource(R.drawable.ic_rb_submit);submit.setContentDescription("提交搜索或网址");submit.setBackgroundResource(android.R.drawable.list_selector_background);submit.setPadding(dp(12),dp(12),dp(12),dp(12));submit.setOnClickListener(v->submitInput());searchBox.addView(submit,new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout.LayoutParams searchParams=new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT);searchParams.setMargins(0,dp(18),0,dp(20));body.addView(searchBox,searchParams);
        recent=new LinearLayout(context);recent.setOrientation(LinearLayout.VERTICAL);recent.setPadding(dp(14),dp(12),dp(10),dp(10));body.addView(recent,new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT));recent.setVisibility(GONE);
        controls(false);content.requestFocus(); // never summon the IME merely by opening a blank workspace
    }

    /** The only runtime binding path. No UI-supplied model, URL extra, favorite catalog or
     * serialized token can substitute for canonical existing native settings/history reads. */
    public void bindNative(StartupCoordinator startup,ComponentGate.BusinessPermit permit,BrowserToolbar.Binding nativeWindow,Navigation navigate){
        BrowserToolbar.main();if(startup==null || nativeWindow==null || navigate==null)throw invalid();nativeWindow.check();startup.gate().checkCurrent(permit);unbind();
        binding=nativeWindow;navigation=navigate;frame=new Object();owner=new NativeOwner(startup,permit,new WeakReference<>(this));loadNative(null);
    }
    /** Explicit refresh, e.g. after returning from settings/history. Preserve the local draft,
     * but retire all old row callbacks. No reload of webpages or restoration of shelved contexts. */
    public void refreshNative(){BrowserToolbar.main();if(current())loadNative(null);}
    private boolean current(){if(binding==null || owner==null || frame==null)return false;try{binding.check();return true;}catch(RuntimeException stale){unbind();return false;}}
    private void loadNative(String reason){
        if(!current())return;Object expected=frame=new Object();loading=true;controls(false);clearRecent();owner.read(expected,reason);
    }
    private void render(Model page){
        boolean backgroundChanged=model==null || model.photo()!=page.photo();
        frame=new Object();loading=false;model=page;submission=new Submission();String name=Model.providerLabel(page.provider());logo.setImageResource(page.logoResource());logo.setContentDescription("默认搜索引擎："+name);engine.setImageResource(page.iconResource());engine.setContentDescription(name+" 搜索");logoSlot.setVisibility(VISIBLE);
        registered.setVisibility(page.provider()==SearchEngines.Provider.DUCKDUCKGO?VISIBLE:GONE);boolean pad=page.photo() && page.provider()!=SearchEngines.Provider.GOOGLE && page.provider()!=SearchEngines.Provider.BAIDU;logoPanel.setPadding(pad?dp(12):0,pad?dp(8):0,pad?dp(12):0,pad?dp(8):0);
        searchBox.setBackground(surface(page.photo()?0xffffffff:0xfff0f1f5,32,false));searchBox.setElevation(page.photo()?dp(2):0);recent.setBackground(surface(page.photo()?0xfffaf7ff:0xffffffff,24,!page.photo()));
        clearRecent();if(!page.empty()){
            HorizontalScrollView row=new HorizontalScrollView(getContext());row.setHorizontalScrollBarEnabled(false);LinearLayout items=new LinearLayout(getContext());
            final Object renderedFrame=frame;for(Shortcut s:page.shortcuts()){
                LinearLayout item=new LinearLayout(getContext());item.setOrientation(LinearLayout.VERTICAL);item.setGravity(Gravity.CENTER_HORIZONTAL);item.setPadding(dp(4),dp(8),dp(4),dp(6));item.setMinimumHeight(dp(106));
                TextView badge=label(s.badge(),22,0xff66557d);badge.setGravity(Gravity.CENTER);badge.setBackground(surface(0xffeee8f4,26,false));badge.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);item.addView(badge,new LinearLayout.LayoutParams(dp(50),dp(50)));
                TextView title=label(s.title(),12,0xff514858);title.setMaxLines(2);title.setEllipsize(TextUtils.TruncateAt.END);title.setGravity(Gravity.CENTER);title.setPadding(0,dp(6),0,0);item.addView(title,new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT));
                item.setContentDescription(s.title()+"，"+s.hostLabel());item.setBackgroundResource(android.R.drawable.list_selector_background);item.setOnClickListener(v->{if(frame==renderedFrame)openRecent(s);});items.addView(item,new LinearLayout.LayoutParams(dp(82),LayoutParams.WRAP_CONTENT));
            }
            row.addView(items,new HorizontalScrollView.LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT));recent.addView(row,new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT));recent.setVisibility(VISIBLE);
        }
        input.setError(null);controls(true);layoutBody();
        // Refreshing recent visits or changing only the default engine must not discard an
        // unchanged approved photo, flash the fallback color, or decode it again on return.
        if(backgroundChanged){artworkRevision++;decodedWidth=decodedHeight=0;applyBackdrop(page.photo(),null);}
        requestBackground();
    }
    private void submitInput(){
        if(!current() || loading || model==null)return;final String raw;try{raw=Query.require(input.getText().toString());}catch(IllegalArgumentException bad){input.setError(bad.getMessage());return;}
        if(!submission.begin())return;controls(false);Object expected=frame;Model displayed=model;owner.prepare(expected,displayed,raw,null);
    }
    private void openRecent(Shortcut shortcut){if(!current() || loading || model==null || !submission.begin())return;controls(false);owner.prepare(frame,model,null,shortcut);}
    private void handoff(SearchEngines.Target target){
        if(!current())return;submission.spendBeforeHandoff();dismissInput();Navigation exact=navigation;Object expected=frame;
        BrowserToolbar.IntentOutcome outcome=BrowserToolbar.invokeNativeIntent(()->{binding.check();if(expected==frame && exact==navigation)exact.navigate(target);});
        if(outcome==BrowserToolbar.IntentOutcome.CALLBACK_UNKNOWN && frame==expected)input.setError("导航请求结果未知，请核对当前页面");
    }
    private void preparationFailed(String reason){if(!current())return;submission.preparationFailed();controls(true);input.setError(reason);}
    /** Global Back can dismiss the actual local input before webpage/environment handling. */
    public boolean hasInputFocus(){BrowserToolbar.main();return input.hasFocus();}
    public boolean dismissInput(){BrowserToolbar.main();if(!input.hasFocus())return false;input.clearFocus();InputMethodManager ime=(InputMethodManager)getContext().getSystemService(Context.INPUT_METHOD_SERVICE);if(ime!=null)ime.hideSoftInputFromWindow(input.getWindowToken(),0);return true;}
    private void clearRecent(){BrowserToolbar.clearOwnedViewTree(recent);recent.removeAllViews();recent.setVisibility(GONE);}
    public void unbind(){
        BrowserToolbar.main();frame=null;binding=null;navigation=null;if(owner!=null)owner.retire();owner=null;model=null;loading=false;submission=new Submission();artworkRevision++;decodedWidth=decodedHeight=0;
        input.setText("");input.setError(null);input.clearFocus();logo.setImageDrawable(null);engine.setImageDrawable(null);logoSlot.setVisibility(INVISIBLE);clearRecent();controls(false);applyBackdrop(false,null);
        // Retire recipients only. Already running IO/decode finishes on its real worker; detach
        // does not cancel streams, release a running-worker slot, or recycle a displayed bitmap.
    }
    @Override protected void onDetachedFromWindow(){unbind();super.onDetachedFromWindow();}
    private void controls(boolean enabled){input.setEnabled(enabled);submit.setEnabled(enabled);recent.setEnabled(enabled);}
    private TextView label(String value,int size,int color){TextView t=new TextView(getContext());t.setSaveEnabled(false);t.setText(value);t.setTextSize(size);t.setTextColor(color);return t;}
    private GradientDrawable surface(int color,int radius,boolean border){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));if(border)d.setStroke(dp(1),0xffe8e5ed);return d;}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private void layoutBody(){
        if(stableWidth<1 || stableHeight<1)return;LayoutParams p=(LayoutParams)body.getLayoutParams();p.width=Math.min(Math.max(1,(getWidth()>0?getWidth():stableWidth)-dp(36)),dp(720));body.setLayoutParams(p);body.setPadding(0,dp(stableWidth>stableHeight?12:44),0,dp(28));
        LayoutParams l=(LayoutParams)logoPanel.getLayoutParams();l.width=Math.min(p.width,dp(model!=null && model.provider()==SearchEngines.Provider.DUCKDUCKGO?290:240));logoPanel.setLayoutParams(l);
    }
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);updateViewport(w,h);}
    @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom){super.onLayout(changed,left,top,right,bottom);updateViewport(getWidth(),getHeight());}
    @Override public WindowInsets onApplyWindowInsets(WindowInsets insets){WindowInsets result=super.onApplyWindowInsets(insets);updateViewport(getWidth(),getHeight());return result;}
    private void updateViewport(int w,int h){
        View geometry=backdropRoot.get();if(geometry!=null && geometry.getWidth()>0 && geometry.getHeight()>0){w=geometry.getWidth();h=geometry.getHeight();}else geometry=this;
        if(w<1 || h<1)return;WindowInsets insets=getRootWindowInsets();boolean ime=insets!=null && insets.isVisible(WindowInsets.Type.ime());
        int nextH=(ime || insets==null && stableWidth==w && stableHeight>h) && stableWidth==w && stableHeight>0?stableHeight:h;
        // Prefer this ACTUAL Activity's window, not application/display metrics. This remains
        // stable even if onSizeChanged arrives before the IME-visible inset notification, yet
        // follows real split-window resize. No keyboard dimensions decide device/orientation.
        Context context=getContext();for(int n=0;n<8 && context instanceof android.content.ContextWrapper && !(context instanceof android.app.Activity);n++)context=((android.content.ContextWrapper)context).getBaseContext();
        if(context instanceof android.app.Activity)try{
            android.view.WindowMetrics metrics=((android.app.Activity)context).getWindowManager().getCurrentWindowMetrics();int[] location=new int[2];geometry.getLocationInWindow(location);
            android.graphics.Insets bars=metrics.getWindowInsets().getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            nextH=PersonalizationPolicy.unoccludedViewportHeight(h,metrics.getBounds().height(),location[1],bars.bottom);
        }catch(RuntimeException unavailable){/* keep only observed own-content geometry above */}
        if(stableWidth!=w || stableHeight!=nextH){stableWidth=w;stableHeight=nextH;artworkRevision++;layoutBody();requestBackground();}
    }
    private void requestBackground(){
        if(model==null || !model.photo() || stableWidth<1 || stableHeight<1 || decoding || decodedWidth==stableWidth && decodedHeight==stableHeight || !current())return;
        final int w=stableWidth,h=stableHeight;final long revision=artworkRevision;final android.content.res.Resources resources=getContext().getApplicationContext().getResources();WeakReference<NewTabView> weak=new WeakReference<>(this);Handler handler=main;decoding=true;
        try{ART.execute(()->{
            Bitmap pixels=null;try{PersonalizationPolicy.Crop crop=PersonalizationPolicy.centerTopCrop(3324,2493,w,h,MAX_BACKGROUND_PIXELS);
                pixels=ImageDecoder.decodeBitmap(ImageDecoder.createSource(resources,R.raw.rb_new_tab_background),(decoder,info,source)->{
                    if(info.getSize().getWidth()!=3324 || info.getSize().getHeight()!=2493)throw new IllegalArgumentException("Unexpected packaged master");
                    double scale=Math.min((double)crop.outputWidth()/crop.cropWidth(),(double)crop.outputHeight()/crop.cropHeight());int sw=Math.max(1,(int)Math.floor(3324*scale)),sh=Math.max(1,(int)Math.floor(2493*scale));
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);decoder.setTargetColorSpace(android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.SRGB));decoder.setTargetSize(sw,sh);int cw=Math.min(sw,crop.outputWidth()),ch=Math.min(sh,crop.outputHeight());decoder.setCrop(new Rect((sw-cw)/2,0,(sw+cw)/2,ch));
                });
                if((long)pixels.getWidth()*pixels.getHeight()>MAX_BACKGROUND_PIXELS || pixels.getAllocationByteCount()>4L*MAX_BACKGROUND_PIXELS)throw new IllegalArgumentException("Background output exceeds pixel/allocation limit");
            }catch(java.io.IOException|RuntimeException|OutOfMemoryError unavailable){if(pixels!=null)pixels.recycle();pixels=null;}
            final Bitmap result=pixels;handler.post(()->{try{
                NewTabView view=weak.get();if(view==null){if(result!=null)result.recycle();return;}view.decoding=false;
                if(view.artworkRevision!=revision || view.model==null || !view.model.photo()){if(result!=null)result.recycle();view.requestBackground();return;}
                if(result!=null && view.current()){view.decodedWidth=w;view.decodedHeight=h;view.applyBackdrop(true,new TopArtwork(result,h));}else if(result!=null)result.recycle();
            }finally{resumeDeferredArtwork();}});
        });}catch(java.util.concurrent.RejectedExecutionException busy){decoding=false;deferArtwork();}
    }
    // Main-thread-only weak waiters. A known rejected fixed-public-image request can wait for
    // REAL decode completion, rather than leaving rapid tab switches permanently on a solid
    // fallback. No additional decode thread, queued bitmap, timer, synchronous work or URI path.
    private static final java.util.ArrayDeque<WeakReference<NewTabView>> DEFERRED_ART=new java.util.ArrayDeque<>();
    private void deferArtwork(){
        for(java.util.Iterator<WeakReference<NewTabView>> i=DEFERRED_ART.iterator();i.hasNext();){NewTabView v=i.next().get();if(v==null)i.remove();else if(v==this)return;}
        if(DEFERRED_ART.size()==64)DEFERRED_ART.removeFirst();DEFERRED_ART.addLast(new WeakReference<>(this));
    }
    private static void resumeDeferredArtwork(){
        int pending=DEFERRED_ART.size();for(int n=0;n<pending;n++){
            NewTabView v=DEFERRED_ART.removeFirst().get();if(v==null || v.model==null || !v.model.photo() || v.decoding || v.decodedWidth==v.stableWidth && v.decodedHeight==v.stableHeight)continue;
            v.requestBackground();return; // at most one new attempt after an actual completion
        }
    }
    /** Cached top crop keeps its unoccluded height while the IME clips the native viewport. */
    private static final class TopArtwork extends Drawable {
        private final Bitmap pixels;private final Paint paint=new Paint(Paint.FILTER_BITMAP_FLAG);private final int height;
        TopArtwork(Bitmap pixels,int height){this.pixels=pixels;this.height=height;}
        @Override public void draw(Canvas c){Rect b=getBounds();c.drawBitmap(pixels,null,new Rect(b.left,b.top,b.right,b.top+height),paint);}
        @Override public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}
        @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
    private interface UiResult {void accept(NewTabView view);}
    private static final ThreadPoolExecutor ART=worker("ReBrowser-new-tab-art");
    private static ThreadPoolExecutor worker(String name){ThreadPoolExecutor e=new ThreadPoolExecutor(1,1,15,TimeUnit.SECONDS,new ArrayBlockingQueue<>(1),r->{Thread t=new Thread(r,name);t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());e.allowCoreThreadTimeOut(true);return e;}
    private static final class NativeOwner {
        final StartupCoordinator startup;final ComponentGate.BusinessPermit permit;final ComponentGate.DataEpochPin pin;final WeakReference<NewTabView> view;final Handler main=new Handler(Looper.getMainLooper());volatile boolean retired;
        NativeOwner(StartupCoordinator startup,ComponentGate.BusinessPermit permit,WeakReference<NewTabView> view){this.startup=startup;this.permit=permit;this.view=view;pin=startup.gate().captureDataEpoch(permit);}
        void retire(){retired=true;}
        void check(){if(retired)throw new IllegalStateException("Retired new-tab recipient");pin.requireCurrent();startup.gate().checkCurrent(permit);}
        SettingsRepository.Snapshot settings()throws java.io.IOException,java.security.GeneralSecurityException{
            check();SettingsRepository repository=startup.openExistingSettingsOnIo(permit);try(OwnedDataCatalog.View<SettingsRepository.Snapshot> s=repository.read(permit)){SettingsRepository.Snapshot value=s.value(permit);check();return value;}
        }
        Model page(SettingsRepository.Snapshot settings)throws java.io.IOException,java.security.GeneralSecurityException{
            HistoryStore.Snapshot history=null;if(settings.newTabRecentEnabled())try{history=startup.openExistingHistoryStoreOnIo(permit).readCurrent(System.currentTimeMillis(),permit);}catch(java.io.IOException|java.security.GeneralSecurityException|RuntimeException optionalHistoryUnavailable){check();}
            check();return Model.fromNative(settings,history,System.currentTimeMillis());
        }
        void post(Object expected,UiResult callback){main.post(()->{
            NewTabView v=view.get();if(v==null || retired || v.owner!=this || v.frame!=expected || !v.current())return;
            try{check();}catch(RuntimeException stale){v.unbind();return;}
            // A presentation/callback exception is NOT a changed source or an effect receipt.
            try{callback.accept(v);}catch(RuntimeException unknown){v.controls(false);v.input.setError("原生界面状态未知，请核对当前页面");}
        });}
        void read(Object expected,String reason){
            try{startup.executeBrowserDataIo(permit,()->{try{check();Model page=page(settings());post(expected,v->{v.render(page);if(reason!=null)v.input.setError(reason);});}catch(java.io.IOException|java.security.GeneralSecurityException|RuntimeException unavailable){post(expected,v->{v.controls(false);v.input.setError("新标签页设置暂不可用，未创建或覆盖数据");});}});}catch(java.util.concurrent.RejectedExecutionException busy){post(expected,v->v.input.setError("原生读取繁忙，请稍后刷新"));}catch(RuntimeException unavailable){post(expected,NewTabView::unbind);}
        }
        void prepare(Object expected,Model displayed,String raw,Shortcut recent){
            try{startup.executeBrowserDataIo(permit,()->{try{check();SettingsRepository.Snapshot current=settings();
                if(!displayed.sameSettings(current)){Model changed=page(current);post(expected,v->{v.render(changed);v.input.setError("新标签页设置已更新，请再次提交");});return;}
                SearchEngines.Target target;Long expires=null;if(recent==null)target=SearchEngines.resolveInput(Query.require(raw),current.searchProvider());else{
                    if(!current.newTabRecentEnabled())throw new IllegalStateException("Recent entries disabled");HistoryStore.Snapshot history=startup.openExistingHistoryStoreOnIo(permit).readCurrent(System.currentTimeMillis(),permit);long now=System.currentTimeMillis();target=Model.resolveRecent(recent,history,now);
                    if(!history.maintaining())expires=history.findVisible(((RecentRef)recent.token).id,now).expiresUtcMs();
                }
                Long exactExpiry=expires;check();post(expected,v->{
                    if(exactExpiry!=null && System.currentTimeMillis()>=exactExpiry){v.preparationFailed("这条访问记录已到期");v.loadNative("这条访问记录已到期");return;}
                    v.handoff(target);
                });
            }catch(java.io.IOException|java.security.GeneralSecurityException|RuntimeException unavailable){post(expected,v->{v.preparationFailed(recent==null?"输入或读取暂不可用，未请求导航":"这条访问记录已删除、到期或暂不可读");if(recent!=null)v.loadNative("这条访问记录已删除、到期或暂不可读");});}});}catch(java.util.concurrent.RejectedExecutionException busy){post(expected,v->v.preparationFailed("原生读取繁忙，请稍后再试"));}catch(RuntimeException unavailable){post(expected,NewTabView::unbind);}
        }
    }
    private static String text(String value,int max){if(value==null || value.isEmpty() || value.length()>max)throw invalid();for(int i=0;i<value.length();i++){char c=value.charAt(i);if(Character.isISOControl(c))throw invalid();if(Character.isHighSurrogate(c)){if(++i>=value.length() || !Character.isLowSurrogate(value.charAt(i)))throw invalid();}else if(Character.isLowSurrogate(c))throw invalid();}return value;}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid native new-tab presentation");}
}
