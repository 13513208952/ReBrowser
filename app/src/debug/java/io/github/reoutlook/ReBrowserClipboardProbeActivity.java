package io.github.reoutlook;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Debug-only, DUMP-protected, fixed offline HTTPS fixture; never uses user Profiles/grants. */
public final class ReBrowserClipboardProbeActivity extends Activity {
    static final String URL="https://clipboard-probe.invalid/test";
    final String source="rb-source-"+UUID.randomUUID();
    final String written="rb-written-"+UUID.randomUUID();
    private final String profile="rebrowser_workspace_"+UUID.randomUUID().toString().replace("-","");
    private final String preferencePrefix="rb_clip_probe_"+UUID.randomUUID()+"_";
    static final String CLEANUP_PREFERENCES="rb_clip_probe_cleanup_v1";
    private final Set<String> preferenceNames=new HashSet<>();
    WebView webView;
    private ReBrowserWebController controller;
    private ClipboardManager clipboard;
    private ClipData previousClip;
    private boolean clipboardPrepared;
    volatile boolean cleaned;
    volatile boolean ready;
    volatile String failure="";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        try {
            cleanupDeferredProfiles(this);
            Set<String> pending=new HashSet<>(getSharedPreferences(CLEANUP_PREFERENCES,MODE_PRIVATE).getStringSet("profiles",Set.of()));
            pending.add(profile);getSharedPreferences(CLEANUP_PREFERENCES,MODE_PRIVATE).edit().putStringSet("profiles",pending).commit();
            Context isolated=new ContextWrapper(this) {
                @Override public Context getApplicationContext() {return this;}
                @Override public SharedPreferences getSharedPreferences(String name,int mode) {
                    String own=preferencePrefix+name;preferenceNames.add(own);
                    return super.getSharedPreferences(own,mode);
                }
            };
            ReBrowserPreferences prefs=new ReBrowserPreferences(isolated);
            ReBrowserSitePermissions permissions=new ReBrowserSitePermissions(isolated);
            if(!permissions.grant(profile,"https://clipboard-probe.invalid",ReBrowserSitePermissions.CLIPBOARD,
                    ReBrowserSitePermissions.FIFTEEN_DAYS_MS,System.currentTimeMillis())) throw new IllegalStateException("fixture-grant-failed");
            FrameLayout container=new FrameLayout(this);setContentView(container);
            controller=new ReBrowserWebController(this,prefs,permissions,new ReBrowserFullscreenController(this,prefs),new Listener(),7419);
            controller.attachContainer(container);
            controller.showTab("probe",profile,new ReBrowserStore.Tab("probe-tab","probe","about:blank"));
            webView=(WebView)container.getChildAt(0);
            WebViewClient original=webView.getWebViewClient();
            webView.setWebViewClient(new WebViewClient() {
                @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request) {
                    String body=URL.equals(request.getUrl().toString())?html():"";
                    return new WebResourceResponse("text/html","UTF-8",new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
                }
                @Override public boolean shouldOverrideKeyEvent(WebView view,android.view.KeyEvent event) {
                    android.util.Log.i("RBClipProbe","native-key action="+event.getAction()+" focused="+view.hasFocus());
                    return original.shouldOverrideKeyEvent(view,event);
                }
                @Override public void onPageStarted(WebView view,String url,Bitmap icon) {ready=false;original.onPageStarted(view,url,icon);}
                @Override public void onPageFinished(WebView view,String url) {original.onPageFinished(view,url);if(URL.equals(url))ready=true;}
                @Override public void doUpdateVisitedHistory(WebView view,String url,boolean reload) {original.doUpdateVisitedHistory(view,url,reload);}
            });
            webView.loadUrl(URL);
        } catch(Throwable error) {failure="fixture-initialization:"+error.getClass().getSimpleName();}
    }
    private String html() {
        return """
          <!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">
          <meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'">
          <style>body{font:22px sans-serif;padding:24px}button{display:block;width:90%;height:70px;margin:16px 0}</style>
          <h3>受控剪贴板安全测试</h3><button id="read">读取一次</button><button id="write">写入一次</button>
          <button id="batch">读取两次</button><button id="prime">仅触摸，不访问</button>
          <script>
          window.results=[];let seq=0;const pending=new Map();
          reBrowserPermissionBridge.onmessage=e=>{const r=JSON.parse(e.data);const done=pending.get(r.id);if(!done)return;
            pending.delete(r.id);const safe={ok:r.ok,message:r.message||'',matches:r.value===SOURCE};results.push(safe);done(safe)};
          window.direct=op=>new Promise(resolve=>{const id=String(++seq);pending.set(id,resolve);
            reBrowserPermissionBridge.postMessage(JSON.stringify({id,operation:op,text:WRITTEN,userGesture:true,userActivation:true}))});
          read.onclick=()=>direct('clipboard-read');write.onclick=()=>direct('clipboard-write');
          batch.onclick=async()=>{await direct('clipboard-read');await direct('clipboard-read')};
          </script>
          """.replace("SOURCE",org.json.JSONObject.quote(source)).replace("WRITTEN",org.json.JSONObject.quote(written));
    }
    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if(controller!=null)controller.onWindowFocusChanged(focused);
        if(focused&&!clipboardPrepared&&failure.isEmpty()) {
            clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            previousClip=clipboard.getPrimaryClip();
            clipboard.setPrimaryClip(ClipData.newPlainText("ReBrowser security probe",source));clipboardPrepared=true;
        }
    }
    @Override public boolean dispatchTouchEvent(android.view.MotionEvent event) {
        boolean handled=super.dispatchTouchEvent(event);
        if(event.getActionMasked()==android.view.MotionEvent.ACTION_DOWN
                || event.getActionMasked()==android.view.MotionEvent.ACTION_UP) {
            android.util.Log.i("RBClipProbe","action="+event.getActionMasked()+" flags="+event.getFlags()+" focus="+hasWindowFocus());
        }
        return handled;
    }
    boolean prepared() {return clipboardPrepared;}
    @Override public void onPause() {if(controller!=null)controller.onPause();super.onPause();}
    @Override public void onResume() {super.onResume();if(controller!=null)controller.onResume();}
    void cleanupProbe() {
        if(cleaned)return;
        // Restore while this fixture still owns the foreground clipboard access.
        if(clipboardPrepared) {
            ClipData current=clipboard.getPrimaryClip();
            CharSequence value=current!=null&&current.getItemCount()>0?current.getItemAt(0).getText():null;
            if(source.contentEquals(value==null?"":value)||written.contentEquals(value==null?"":value)) {
                if(previousClip==null)clipboard.clearPrimaryClip();else clipboard.setPrimaryClip(previousClip);
            }
            previousClip=null;
        }
        if(controller!=null)controller.destroyAll();
        webView=null;controller=null;
        cleanupDeferredProfiles(this);
        for(String name:preferenceNames)deleteSharedPreferences(name);
        cleaned=true;
    }
    static int cleanupDeferredProfiles(Context context) {
        SharedPreferences prefs=context.getSharedPreferences(CLEANUP_PREFERENCES,MODE_PRIVATE);
        Set<String> remaining=new HashSet<>(prefs.getStringSet("profiles",Set.of()));
        for(String own:new HashSet<>(remaining)) {
            if(!ReBrowserStore.isOwnedProfile(own))continue;
            try {androidx.webkit.ProfileStore.getInstance().deleteProfile(own);remaining.remove(own);}
            catch(IllegalStateException inUse) { /* Chromium may retain the profile until this process exits. */ }
        }
        prefs.edit().putStringSet("profiles",remaining).commit();return remaining.size();
    }
    @Override public void onDestroy() {cleanupProbe();super.onDestroy();}
    private final class Listener implements ReBrowserWebController.Listener {
        public void onPageStarted(ReBrowserStore.Tab tab,String url,boolean visible) {}
        public void onVisitedHistory(ReBrowserStore.Tab tab,String url,boolean reload) {}
        public void onPageFinished(ReBrowserStore.Tab tab,String url,String title,boolean visible) {}
        public void onMainFrameError(ReBrowserStore.Tab tab,int code,String description,boolean visible) {failure="fixture-page-error";}
        public void onProgressChanged(ReBrowserStore.Tab tab,int progress,boolean visible) {}
        public void onLoadingStopped(ReBrowserStore.Tab tab,boolean visible) {}
        public void onTitleReceived(ReBrowserStore.Tab tab,String title,boolean visible) {}
        public void onExternalNavigationBlocked(String message) {}
        public void onExternalNavigationRequested(Uri uri,String scheme) {failure="fixture-external-navigation";}
        public ReBrowserWebController.PopupTarget onCreatePopup(ReBrowserStore.Tab tab) {return null;}
        public void onPopupCreated(ReBrowserStore.Tab tab) {}
        public void onPopupCreationFailed(ReBrowserStore.Tab tab,Throwable error) {}
        public void onCloseRequested(ReBrowserStore.Tab tab) {}
        public void onDownloadRequested(ReBrowserWebController.DownloadRequest request) {failure="fixture-download";}
        public void onFileChooserUnavailable() {}
        public void onSitePermissionRequested(ReBrowserPermissionRequest request) {request.deny("unexpected-fixture-prompt");failure="fixture-prompt";}
    }
}
