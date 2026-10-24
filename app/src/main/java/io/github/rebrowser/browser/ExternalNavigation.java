package io.github.rebrowser.browser;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import io.github.rebrowser.ui.BrowserActivity;

/**
 * Narrow Android untrusted web-link boundary, NOT administrator input or source consent.
 * Rebuild from HTTP(S) data only, never forward caller extras/grants/component/selector/
 * ClipData/categories. Private action is NOT authentication: consume revalidates it too.
 * Receiver must wait for source/epoch gate and allocate a NEW isolated temporary workspace;
 * never infer a target account/tab/window or a trusted intent from the calling package.
 * Native BrowserActivity consumes only bounded web DATA after the separate source/epoch gate;
 * every dispatch creates a NEW isolated temporary workspace. No caller identity/admin extras.
 */
public final class ExternalNavigation {
    public static final String ACTION_OPEN_WEB="io.github.rebrowser.action.OPEN_EXTERNAL_WEB";
    public static final int MAX_EXTERNAL_URL_CHARS=8192;
    /** Untrusted URL data and local duplicate-dispatch latch ONLY, not source/Actor/window/grant
     * authority. Native owner must separately check actual source and create an isolated target. */
    public static final class PendingWebTarget {
        private final Thread owner=Thread.currentThread();private final SearchEngines.Target target;private boolean attempted;
        public PendingWebTarget(String raw){if(raw==null || raw.length()>MAX_EXTERNAL_URL_CHARS)throw new IllegalArgumentException("External URL unavailable");target=SearchEngines.requireWebUrl(raw);}
        public boolean attempted(){check();return attempted;}
        public SearchEngines.Target claimNativeDispatchAttempt(){check();if(attempted)throw new IllegalStateException("External URL dispatch already attempted");attempted=true;return target;}
        private void check(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Native external-data owner thread required");}
    }
    private ExternalNavigation(){}
    /** Reads ONLY action/data fields, never caller extras, grants, component or ClipData. The
     * returned object remains untrusted web data even when a sender forges our private action. */
    public static PendingWebTarget takeNativeIncoming(Intent incoming){if(incoming==null)return null;String action=incoming.getAction();if(!Intent.ACTION_VIEW.equals(action) && !ACTION_OPEN_WEB.equals(action))return null;String raw=incoming.getDataString();clearIncoming(incoming);try{return new PendingWebTarget(raw);}catch(IllegalArgumentException invalid){return null;}}
    private static void clearIncoming(Intent incoming){incoming.setAction(null);incoming.setData(null);incoming.setSelector(null);incoming.setClipData(null);incoming.replaceExtras((Bundle)null);incoming.setFlags(0);}
    /** Null means rejected. No Intent extras are read or unparcelled. */
    public static Intent sanitize(Context context,Intent incoming){
        if(context==null || incoming==null || !Intent.ACTION_VIEW.equals(incoming.getAction()))return null;
        final SearchEngines.Target target;
        try{String raw=incoming.getDataString();if(raw==null || raw.length()>MAX_EXTERNAL_URL_CHARS)return null;target=SearchEngines.requireWebUrl(raw);}catch(IllegalArgumentException invalid){return null;}
        return new Intent(context,BrowserActivity.class).setAction(ACTION_OPEN_WEB).setData(Uri.parse(target.url()))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
    }
    /** One-shot extraction; even a manually forged private action is only untrusted web data. */
    public static SearchEngines.Target consume(Intent intent){
        if(intent==null || !ACTION_OPEN_WEB.equals(intent.getAction()))return null;
        String raw=intent.getDataString();
        clearIncoming(intent);
        try{if(raw==null || raw.length()>MAX_EXTERNAL_URL_CHARS)return null;return SearchEngines.requireWebUrl(raw);}catch(IllegalArgumentException invalid){return null;}
    }
}
