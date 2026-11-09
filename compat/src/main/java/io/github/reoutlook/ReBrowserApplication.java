package io.github.reoutlook;

/** Gecko stays lazy; native ReVideo's auxiliary WebView has its own profile. */
public final class ReBrowserApplication extends android.app.Application {
    @Override public void onCreate() {
        super.onCreate();
        if (android.app.Application.getProcessName().endsWith(":revideo")) {
            android.webkit.WebView.setDataDirectorySuffix("revideo");
        }
        // GeckoRuntime is created lazily by the browser controller after native UI exists.
    }
}
