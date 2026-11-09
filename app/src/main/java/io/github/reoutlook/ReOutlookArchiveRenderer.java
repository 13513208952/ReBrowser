package io.github.reoutlook;

import android.content.Context;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** Network-disabled WebView renderer for an already sanitized local mail body. */
final class ReOutlookArchiveRenderer {
    private final WebView view;

    ReOutlookArchiveRenderer(Context context, String bodyHtml) {
        view = new WebView(context);
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(false);
        settings.setBlockNetworkLoads(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        view.setBackgroundColor(android.graphics.Color.WHITE);
        view.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView web,
                    WebResourceRequest request) { return true; }
        });
        view.loadDataWithBaseURL("about:blank", document(bodyHtml),
                "text/html", "UTF-8", null);
    }

    View view() { return view; }
    void destroy() { view.stopLoading(); view.destroy(); }

    private static String document(String bodyHtml) {
        return "<!doctype html><html><head><meta name=\"viewport\" "
                + "content=\"width=device-width,initial-scale=1\"><style>"
                + "body{font-family:sans-serif;color:#242424;margin:16px;overflow-wrap:anywhere}"
                + "img{max-width:100%;height:auto}pre{white-space:pre-wrap}a{color:#0f6cbd}"
                + "</style></head><body>" + bodyHtml + "</body></html>";
    }
}
