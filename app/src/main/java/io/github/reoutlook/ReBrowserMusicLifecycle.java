package io.github.reoutlook;

/** Task lifetime is not Activity visibility. A new runtime never inherits playing=true. */
final class ReBrowserMusicLifecycle {
    enum Stage { CHECKING, LOGIN, PLAYER, STOPPED }
    private Stage stage = Stage.CHECKING;
    private boolean desktopLogin;
    private boolean desktopAllowed;
    private long generation = 1;

    Stage stage() { return stage; }
    long generation() { return generation; }
    boolean desktop() { return stage == Stage.PLAYER || (stage == Stage.LOGIN && desktopLogin); }
    boolean player() { return stage == Stage.PLAYER; }
    String destination() {
        return stage == Stage.LOGIN ? (desktopLogin ? ReBrowserMusicPolicy.DESKTOP_LOGIN : ReBrowserMusicPolicy.MOBILE_LOGIN)
                : ReBrowserMusicPolicy.HOME;
    }
    boolean presentLogin(String url, boolean verified) {
        return stage == Stage.LOGIN && verified && ReBrowserMusicPolicy.loginDocument(url);
    }
    boolean recheck() {
        if (stage != Stage.LOGIN) return false;
        stage = Stage.CHECKING; desktopLogin = false; generation++; return true;
    }
    boolean desktopLoginAllowed() { return desktopAllowed && stage == Stage.LOGIN; }
    boolean window(boolean tablet, boolean full, boolean landscape) {
        desktopAllowed = ReBrowserMusicPolicy.desktopLoginAllowed(tablet, full, landscape);
        if (!desktopAllowed && desktopLogin && stage != Stage.PLAYER) {
            desktopLogin = false; generation++; return true;
        }
        return false;
    }
    boolean loginUa(boolean desktop) {
        if (stage != Stage.LOGIN || (desktop && !desktopAllowed)) return false;
        if (desktopLogin == desktop) return false;
        desktopLogin = desktop; generation++; return true;
    }
    boolean account(boolean loggedIn) {
        if (stage == Stage.STOPPED) return false;
        if (loggedIn) {
            if (stage == Stage.PLAYER) return false;
            stage = Stage.PLAYER; desktopLogin = false; generation++; return true;
        }
        if (stage == Stage.LOGIN) return false;
        stage = Stage.LOGIN; desktopLogin = false; generation++; return true;
    }
    void stop() { stage = Stage.STOPPED; desktopLogin = false; generation++; }
}
