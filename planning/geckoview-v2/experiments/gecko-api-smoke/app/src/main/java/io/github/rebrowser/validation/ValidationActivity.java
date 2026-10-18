package io.github.rebrowser.validation;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import org.json.JSONObject;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.MediaSession;
import org.mozilla.geckoview.WebExtension;

/** Auxiliary validation APK only; no product identity, signing or data access. */
public final class ValidationActivity extends Activity {
    private static GeckoRuntime runtime;
    private GeckoSession session;
    private GeckoView view;
    private int observations;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        Button start = new Button(this);
        start.setText("Disposable validation: manual start, no automatic playback/login");
        start.setOnClickListener(v -> startSyntheticSession());
        setContentView(start);
    }
    private void startSyntheticSession() {
        if (runtime == null) runtime = GeckoRuntime.create(getApplicationContext());
        if (session != null && session.isOpen()) return;
        session = new GeckoSession(new GeckoSessionSettings.Builder().contextId("synthetic-validation-a").build());
        session.setMediaSessionDelegate(new MediaSession.Delegate() {
            @Override public void onActivated(GeckoSession s, MediaSession media) { observations++; }
            @Override public void onFeatures(GeckoSession s, MediaSession media, long features) { observations++; }
            @Override public void onPositionState(GeckoSession s, MediaSession media, MediaSession.PositionState value) { observations++; }
            @Override public void onAudioSessionTypeChanged(GeckoSession s, MediaSession media, String type) { observations++; }
        });
        session.setProgressDelegate(new GeckoSession.ProgressDelegate() {
            @Override public void onSessionStateChange(GeckoSession s, GeckoSession.SessionState value) {
                // Deliberately no form/history serialization or export in this probe.
                observations++;
            }
        });
        runtime.getWebExtensionController().ensureBuiltIn("resource://android/assets/validation_bridge/", "validation@rebrowser.invalid").accept(extension -> {
            WebExtension.MessageDelegate messages = new WebExtension.MessageDelegate() {
                @Override public GeckoResult<Object> onMessage(String nativeApp, Object message, WebExtension.MessageSender sender) {
                    boolean hasBoundSession = sender.session == session;
                    boolean top = sender.isTopLevel();
                    observations += hasBoundSession && top ? 1 : 0;
                    return GeckoResult.fromValue(null);
                }
                @Override public void onConnect(WebExtension.Port port) {
                    port.setDelegate(new WebExtension.PortDelegate() {
                        @Override public void onPortMessage(Object message, WebExtension.Port actual) { observations++; }
                        @Override public void onDisconnect(WebExtension.Port actual) { observations++; }
                    });
                    try { port.postMessage(new JSONObject().put("kind", "READY")); }
                    catch (org.json.JSONException impossible) { throw new IllegalStateException(impossible); }
                }
            };
            extension.setMessageDelegate(messages, "validation");
            session.getWebExtensionController().setMessageDelegate(extension, messages, "validation");
            session.open(runtime);
            view = new GeckoView(this);
            view.setSession(session);
            setContentView(view);
            session.loadUri("about:blank");
        }, error -> observations++);
    }
    @Override protected void onDestroy() {
        if (view != null) view.releaseSession();
        if (session != null) { session.setActive(false); session.close(); }
        super.onDestroy();
    }
}
