package io.github.rebrowser.engine;

import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;

/**
 * Explicit conservative temporary-browser boundary. No OS prompt, persistent grant,
 * media capture or synthetic positive decision. Finite native grants are NOT implemented.
 * This denies NEW requests; it does not revoke old native grants or stop existing sensors.
 */
public final class PermissionDelegate implements GeckoSession.PermissionDelegate {
    @Override public void onAndroidPermissionsRequest(GeckoSession session,String[] permissions,Callback callback){if(callback!=null)callback.reject();}
    @Override public GeckoResult<Integer> onContentPermissionRequest(GeckoSession session,ContentPermission permission){return GeckoResult.fromValue(ContentPermission.VALUE_DENY);}
    @Override public void onMediaPermissionRequest(GeckoSession session,String uri,MediaSource[] video,MediaSource[] audio,MediaCallback callback){if(callback!=null)callback.reject();}
}
