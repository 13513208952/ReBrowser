package io.github.reoutlook;

import java.net.URI;

/** Fixed provider identity. No browser workspace or Outlook Default Profile is borrowed. */
final class ReBrowserMusicPolicy {
    static final String HOME = "https://music.163.com/st/webplayer";
    // Same read-only account endpoint used by the pinned official module 15 export Ah.
    static final String ACCOUNT = "https://music.163.com/api/w/nuser/account/get";
    // Official mobile entry confirmed by the owner, with the site's ordinary return target.
    static final String MOBILE_LOGIN = "https://y.music.163.com/m/login?redirect_url=https%3A%2F%2Fmusic.163.com%2Fst%2Fwebplayer";
    // Same URL produced by the official WebPlayer's module 121 login helper for desktop login.
    static final String DESKTOP_LOGIN = "https://music.163.com/login?targetUrl=https%3A%2F%2Fmusic.163.com%2Fst%2Fwebplayer";
    static final String PROFILE = "rebrowser_music_netease_v1";
    static final String DESKTOP_UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/156.0.0.0 Safari/537.36";
    private ReBrowserMusicPolicy() {}

    static String mobileUserAgent(String defaultWebViewUa) {
        // Ordinary browser mobile UA, including on a physical tablet. Keep actual engine/device
        // fields, but do not advertise an embedded native app that supplies provider bridges.
        String ua = defaultWebViewUa.replace("; wv", "").replace(" Version/4.0", "");
        if (!ua.contains(" Mobile ")) ua = ua.replace(" Safari/", " Mobile Safari/");
        return ua;
    }

    static boolean originalLayout(boolean tablet, boolean fullWindow, boolean landscape) {
        return tablet && fullWindow && landscape;
    }

    static boolean desktopLoginAllowed(boolean tablet, boolean fullWindow, boolean landscape) {
        return originalLayout(tablet, fullWindow, landscape);
    }

    static boolean allowsNavigation(String value) {
        if (value == null || value.length() > 8192) return false;
        try {
            URI url = new URI(value);
            return "https".equalsIgnoreCase(url.getScheme()) && url.getRawUserInfo() == null
                    && (url.getPort() == -1 || url.getPort() == 443)
                    && ("music.163.com".equalsIgnoreCase(url.getHost())
                        || ("y.music.163.com".equalsIgnoreCase(url.getHost()) && "/m/login".equals(url.getRawPath())));
        } catch (Exception invalid) { return false; }
    }

    static boolean loginDocument(String value) {
        if (!allowsNavigation(value)) return false;
        try {
            URI u = new URI(value);
            return ("music.163.com".equalsIgnoreCase(u.getHost()) && "/login".equals(u.getRawPath()))
                    || ("y.music.163.com".equalsIgnoreCase(u.getHost()) && "/m/login".equals(u.getRawPath()));
        } catch (Exception invalid) { return false; }
    }

    static boolean playerDocument(String value) {
        if (!allowsNavigation(value)) return false;
        try { return "/st/webplayer".equals(new URI(value).getRawPath()); }
        catch (Exception invalid) { return false; }
    }
}
