package io.github.rebrowser.validation;

import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;

/** Compile-only API fixture. Never requests OS permissions, reads clipboard, or grants a site. */
final class GeckoPermissionApiSmoke implements GeckoSession.PermissionDelegate, GeckoSession.SelectionActionDelegate {
    String lastScope;
    void attach(GeckoSession session) {
        session.setPermissionDelegate(this);
        session.setSelectionActionDelegate(this);
    }
    @Override public void onAndroidPermissionsRequest(GeckoSession session, String[] permissions, Callback callback) {
        // No invocation is performed by this compile-only fixture.
        callback.reject();
    }
    @Override public GeckoResult<Integer> onContentPermissionRequest(GeckoSession session, ContentPermission permission) {
        lastScope = permission.uri + ":" + permission.contextId + ":" + permission.privateMode + ":" + permission.thirdPartyOrigin + ":" + permission.permission;
        return GeckoResult.fromValue(ContentPermission.VALUE_DENY);
    }
    @Override public void onMediaPermissionRequest(GeckoSession session, String uri, MediaSource[] video, MediaSource[] audio, MediaCallback callback) {
        callback.reject();
    }
    @Override public GeckoResult<AllowOrDeny> onShowClipboardPermissionRequest(GeckoSession session, ClipboardPermission permission) {
        lastScope = permission.uri + ":" + permission.type;
        return GeckoResult.deny();
    }
    @Override public void onDismissClipboardPermissionRequest(GeckoSession session) { lastScope = null; }
    GeckoResult<java.util.List<ContentPermission>> scopedRead(GeckoRuntime runtime, String uri, String contextId, boolean privateMode) {
        // Signature/overload check only; no call is executed.
        return runtime.getStorageController().getPermissions(uri, contextId, privateMode);
    }
    void resetExistingPermission(GeckoRuntime runtime, ContentPermission original) {
        // VOID dispatch, not revocation completion or proof that active use stopped.
        runtime.getStorageController().setPermission(original, ContentPermission.VALUE_PROMPT);
    }
    void afterUiActuallyShown(ContentPermission original) { original.notifyShown(); }
}
