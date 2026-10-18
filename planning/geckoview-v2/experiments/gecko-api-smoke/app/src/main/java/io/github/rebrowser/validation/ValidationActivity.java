package io.github.rebrowser.validation;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.WebExtension;

/** Auxiliary synthetic phone fixture only. No media/auth/clear/old-app access. */
public final class ValidationActivity extends Activity {
    private static GeckoRuntime runtime;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final JSONObject results = new JSONObject();
    private GeckoSession a, b;
    private GeckoView view;
    private TextView status;
    private LocalFixtureServer fixture;
    private boolean running, finished, flushRequested;
    private String stage = "IDLE";
    private int contentMessages;
    private void result(String key, Object value) {
        try { results.put(key, value); results.put("stage", stage); }
        catch (Exception ignored) { return; }
        String text = results.toString();
        Log.i("RBV", text);
        if (status != null) status.setText(text);
        try {
            File root = getExternalFilesDir(null);
            if (root != null) try (FileOutputStream out = new FileOutputStream(new File(root, "synthetic-results.json"))) {
                out.write(text.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) { }
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        status = new TextView(this);status.setText("Synthetic local-only validation. No login/playback/cleanup.");root.addView(status);
        Button start = new Button(this);start.setText("Run synthetic validation");start.setOnClickListener(v -> startSynthetic());root.addView(start);
        view = new GeckoView(this);root.addView(view, new LinearLayout.LayoutParams(-1, 0, 1));setContentView(root);
        result("validationOnly", true);
        if (getIntent().getBooleanExtra("RUN_SYNTHETIC", false)) handler.post(this::startSynthetic);
    }
    private GeckoSession newSession(String context) {
        GeckoSession s = new GeckoSession(new GeckoSessionSettings.Builder().contextId(context).build());
        s.setContentDelegate(new GeckoSession.ContentDelegate() {
            @Override public void onCrash(GeckoSession session) { result("nativeCrash", true); }
            @Override public void onKill(GeckoSession session) { result("nativeKill", true); }
        });
        s.setProgressDelegate(new GeckoSession.ProgressDelegate() {
            @Override public void onSessionStateChange(GeckoSession session, GeckoSession.SessionState value) {
                if (flushRequested && session == a) result("stateCallbackAfterFlush", true);
            }
            @Override public void onPageStop(GeckoSession session, boolean success) { result("lastLoadSuccess", success); }
        });
        return s;
    }
    private void display(GeckoSession s) {
        GeckoSession previous = view.releaseSession();
        if (previous != null) previous.setActive(false);
        view.setSession(s);s.setActive(true);
    }
    private void startSynthetic() {
        if (running) return;running = true;stage = "BOOT";
        try {
            fixture = new LocalFixtureServer();
            result("loopbackOnly", true);
            if (runtime == null) runtime = GeckoRuntime.create(getApplicationContext());
            result("nativeRuntimeCreated", true);
            String run = Long.toHexString(System.nanoTime());
            a = newSession("synthetic-a-" + run);b = newSession("synthetic-b-" + run);
            runtime.getWebExtensionController().ensureBuiltIn("resource://android/assets/validation_bridge/", "validation@rebrowser.invalid").accept(extension -> {
                result("builtinInstalled", true);
                WebExtension.MessageDelegate background = new WebExtension.MessageDelegate() {
                    @Override public void onConnect(WebExtension.Port port) {
                        result("backgroundSenderHasNoSession", port.sender.session == null);
                        port.setDelegate(new WebExtension.PortDelegate() {
                            @Override public void onPortMessage(Object message, WebExtension.Port actual) {
                                if (message instanceof JSONObject && "BACKGROUND_HELLO".equals(((JSONObject)message).optString("kind"))) result("backgroundNativeMessage", true);
                            }
                        });
                    }
                };
                WebExtension.MessageDelegate content = new WebExtension.MessageDelegate() {
                    @Override public void onConnect(WebExtension.Port port) {
                        boolean sourceOk = (port.sender.session == a || port.sender.session == b) && port.sender.isTopLevel() && fixture.isFixture(port.sender.url);
                        if (!sourceOk) { result("badNativeSender", true);port.disconnect();return; }
                        final String token = "synthetic-lease-" + (++contentMessages);
                        port.setDelegate(new WebExtension.PortDelegate() {
                            @Override public void onPortMessage(Object message, WebExtension.Port actual) {
                                if (!(message instanceof JSONObject)) return;
                                JSONObject data = (JSONObject)message;
                                if (!token.equals(data.optString("lease"))) { result("leaseMismatch", true);return; }
                                if ("FIXTURE_RESULT".equals(data.optString("kind"))) acceptFixture(actual.sender.session, data);
                            }
                        });
                        try { port.postMessage(new JSONObject().put("kind", "READY").put("lease", token)); }
                        catch (Exception failure) { result("messageError", failure.getClass().getSimpleName()); }
                    }
                };
                extension.setMessageDelegate(background, "validation_background");
                a.getWebExtensionController().setMessageDelegate(extension, content, "validation_content");
                b.getWebExtensionController().setMessageDelegate(extension, content, "validation_content");
                a.open(runtime);b.open(runtime);
                stage = "A-SEED";display(a);a.loadUri(fixture.url("a-seed"));
            }, error -> result("builtinInstallError", error.getClass().getSimpleName()));
        } catch (Exception error) { result("startupError", error.getClass().getSimpleName()); }
        handler.postDelayed(() -> { if (!finished) result("timeoutWithoutCompletion", true); }, 30000);
    }
    private void acceptFixture(GeckoSession source, JSONObject data) {
        result("topLevelContentMessage", true);
        result("mainWorldResult", data.optString("mainMarker"));
        if (data.has("mainError")) result("mainWorldError", data.optString("mainError"));
        String reported = data.optString("case");
        if (stage.equals("A-SEED") && reported.equals("A-SEED") && source == a) {
            result("aSeedCookie", data.optString("cookie").contains("rbv=A"));
            result("aSeedLocal", "A".equals(data.optString("local")));
            stage = "B-CHECK";display(b);b.loadUri(fixture.url("b-check"));
        } else if (stage.equals("B-CHECK") && reported.equals("B-CHECK") && source == b) {
            result("bBeforeCookieEmpty", data.optString("beforeCookie").isEmpty());
            result("bBeforeLocalEmpty", data.isNull("beforeLocal") || data.optString("beforeLocal").isEmpty());
            result("bCookie", data.optString("cookie").contains("rbv=B"));
            stage = "A-READ";display(a);a.loadUri(fixture.url("a-read"));
        } else if (stage.equals("A-READ") && reported.equals("A-READ") && source == a) {
            result("aCookieSurvivesB", data.optString("cookie").contains("rbv=A"));
            result("aLocalSurvivesB", "A".equals(data.optString("local")));
            GeckoSession released = view.releaseSession();
            result("releaseDoesNotClose", released == a && a.isOpen());
            view.setSession(a);a.setActive(true);
            result("reattachSameSession", view.getSession() == a);
            flushRequested = true;a.flushSessionState();
            handler.postDelayed(() -> {
                finished = true;stage = "COMPLETE";
                boolean ok = results.optBoolean("nativeRuntimeCreated") && results.optBoolean("builtinInstalled") && results.optBoolean("topLevelContentMessage") && results.optBoolean("bBeforeCookieEmpty") && results.optBoolean("bBeforeLocalEmpty") && results.optBoolean("aCookieSurvivesB") && results.optBoolean("aLocalSurvivesB") && results.optBoolean("releaseDoesNotClose") && "MAIN_SYNTHETIC_READY".equals(results.optString("mainWorldResult"));
                result("syntheticPass", ok);
            }, 1500);
        }
    }
    @Override protected void onDestroy() {
        if (view != null) view.releaseSession();
        if (a != null) a.close();if (b != null) b.close();
        if (fixture != null) try { fixture.close(); } catch (Exception ignored) { }
        super.onDestroy();
    }
}
