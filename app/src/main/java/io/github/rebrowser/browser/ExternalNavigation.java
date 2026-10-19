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
 * EntryRouter/BrowserActivity link acceptance is intentionally not wired until those gates.
 */
public final class ExternalNavigation {
    public static final String ACTION_OPEN_WEB="io.github.rebrowser.action.OPEN_EXTERNAL_WEB";
    private ExternalNavigation(){}
    /** Null means rejected. No Intent extras are read or unparcelled. */
    public static Intent sanitize(Context context,Intent incoming){
        if(context==null || incoming==null || !Intent.ACTION_VIEW.equals(incoming.getAction()))return null;
        final SearchEngines.Target target;
        try{target=SearchEngines.requireWebUrl(incoming.getDataString());}catch(IllegalArgumentException invalid){return null;}
        return new Intent(context,BrowserActivity.class).setAction(ACTION_OPEN_WEB).setData(Uri.parse(target.url()))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
    }
    /** One-shot extraction; even a manually forged private action is only untrusted web data. */
    public static SearchEngines.Target consume(Intent intent){
        if(intent==null || !ACTION_OPEN_WEB.equals(intent.getAction()))return null;
        String raw=intent.getDataString();
        intent.setAction(null);intent.setData(null);intent.setSelector(null);intent.setClipData(null);
        intent.replaceExtras((Bundle)null);intent.setFlags(0);
        try{return SearchEngines.requireWebUrl(raw);}catch(IllegalArgumentException invalid){return null;}
    }
}
