package io.github.reoutlook;

public final class ReBrowserApplication extends android.app.Application {
    @Override public void onCreate() {
        super.onCreate();
        // GeckoRuntime is created lazily by the browser controller after native UI exists.
    }
}
