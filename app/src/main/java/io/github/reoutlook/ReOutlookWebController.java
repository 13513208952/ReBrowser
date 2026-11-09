package io.github.reoutlook;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.webkit.JavaScriptReplyProxy;
import androidx.webkit.WebMessageCompat;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import java.util.Set;

/** WebView adapter behind the engine-neutral ReOutlook activity. */
final class ReOutlookWebController {
    interface Listener {
        void onPageStarted();
        void onPageFinished();
        void onProgressChanged(int progress);
        void onMainFrameError();
        void onUserTouch();
        void onStructuredMessage(String payload, ValueCallback<String> reply);
    }

    private final Activity activity;
    private final Listener listener;
    private final int fileChooserRequestCode;
    private final WebView webView;
    private final ReBrowserStorageEstimate.Driver storageDriver =
            new ReBrowserStorageEstimate.Driver() {
                @Override public String url() { return ReOutlookWebController.this.url(); }
                @Override public void evaluate(String script, ValueCallback<String> callback) {
                    ReOutlookWebController.this.evaluate(script, callback);
                }
            };
    private ValueCallback<Uri[]> pendingFileChooser;
    private boolean structuredCaptureEnabled;
    private boolean destroyed;

    ReOutlookWebController(Activity activity, Listener listener, int fileChooserRequestCode) {
        this.activity = activity;
        this.listener = listener;
        this.fileChooserRequestCode = fileChooserRequestCode;
        webView = new WebView(activity);
        configure();
    }

    View view() { return webView; }
    String url() { return webView.getUrl(); }
    boolean canGoBack() { return webView.canGoBack(); }
    void goBack() { webView.goBack(); }
    void loadUrl(String url) { webView.loadUrl(url); }
    void reload() { webView.reload(); }
    void evaluate(String script, ValueCallback<String> callback) {
        webView.evaluateJavascript(script, callback);
    }
    boolean structuredCaptureEnabled() { return structuredCaptureEnabled; }
    boolean restoreState(Bundle state) { return state != null && webView.restoreState(state) != null; }
    void saveState(Bundle state) { webView.saveState(state); }
    void onResume() { webView.onResume(); }
    void onPause() { CookieManager.getInstance().flush(); webView.onPause(); }
    void onWindowFocusChanged(boolean ignored) { }

    ReBrowserStorageEstimate.Driver storageDriver() { return storageDriver; }

    boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != fileChooserRequestCode || pendingFileChooser == null) return false;
        Uri[] result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
        pendingFileChooser.onReceiveValue(result);
        pendingFileChooser = null;
        return true;
    }

    void destroy() {
        if (destroyed) return;
        destroyed = true;
        if (pendingFileChooser != null) pendingFileChooser.onReceiveValue(null);
        pendingFileChooser = null;
        webView.stopLoading();
        webView.loadUrl("about:blank");
        webView.clearHistory();
        webView.removeAllViews();
        webView.destroy();
    }

    private void configure() {
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setGeolocationEnabled(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSafeBrowsingEnabled(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setMediaPlaybackRequiresUserGesture(true);

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, true);

        installStructuredCapture();
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView view, String url, Bitmap favicon) {
                if (destroyed) return;
                ReBrowserStorageEstimate.invalidate(activity, ReBrowserApplications.OUTLOOK);
                listener.onPageStarted();
            }

            @Override public void onPageFinished(WebView view, String url) {
                if (!destroyed) listener.onPageFinished();
            }

            @Override public boolean shouldOverrideUrlLoading(WebView view,
                    WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                    return false;
                }
                try {
                    activity.startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (ActivityNotFoundException error) {
                    Toast.makeText(activity, "没有能够打开此链接的应用", Toast.LENGTH_SHORT).show();
                }
                return true;
            }

            @Override public void onReceivedError(WebView view, WebResourceRequest request,
                    WebResourceError error) {
                if (request.isForMainFrame() && !destroyed) listener.onMainFrameError();
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onPermissionRequest(PermissionRequest request) { request.deny(); }
            @Override public void onGeolocationPermissionsShowPrompt(String origin,
                    GeolocationPermissions.Callback callback) {
                callback.invoke(origin, false, false);
            }
            @Override public void onProgressChanged(WebView view, int progress) {
                if (!destroyed) listener.onProgressChanged(progress);
            }
            @Override public boolean onShowFileChooser(WebView view,
                    ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (pendingFileChooser != null) pendingFileChooser.onReceiveValue(null);
                pendingFileChooser = callback;
                try {
                    activity.startActivityForResult(params.createIntent(), fileChooserRequestCode);
                    return true;
                } catch (ActivityNotFoundException error) {
                    pendingFileChooser = null;
                    Toast.makeText(activity, "没有可用的文件选择器", Toast.LENGTH_SHORT).show();
                    return false;
                }
            }
        });
        webView.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) listener.onUserTouch();
            return false;
        });
    }

    private void installStructuredCapture() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
                || !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            structuredCaptureEnabled = false;
            return;
        }
        Set<String> origins = Set.of(
                "https://outlook.office.com",
                "https://outlook.office365.com",
                "https://outlook.live.com");
        WebViewCompat.addWebMessageListener(webView, "reOutlookBridge", origins,
                (view, message, sourceOrigin, isMainFrame, replyProxy) -> {
                    if (!isMainFrame || !MainActivity.isOutlookMailOrigin(
                            sourceOrigin.toString())) return;
                    forwardStructuredMessage(message, replyProxy);
                });
        WebViewCompat.addDocumentStartJavaScript(
                webView, CaptureScript.OWA_SERVICE_RESPONSES, origins);
        structuredCaptureEnabled = true;
    }

    private void forwardStructuredMessage(WebMessageCompat message,
            JavaScriptReplyProxy replyProxy) {
        String payload = message.getData();
        listener.onStructuredMessage(payload, value -> {
            try { replyProxy.postMessage(value); }
            catch (RuntimeException ignored) { /* Navigation invalidated the page port. */ }
        });
    }
}
