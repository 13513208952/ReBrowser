package io.github.reoutlook;

import static org.junit.Assert.*;
import org.junit.Test;

public final class ReBrowserMusicPolicyTest {
    @Test public void officialSecureNavigationAndLoginStayInsideProvider() {
        for (String url : new String[]{ReBrowserMusicPolicy.HOME, "https://music.163.com/login?target=webplayer",
                "https://MUSIC.163.COM:443/st/webplayer#/playlist", "https://music.163.com/m/login"})
            assertTrue(url, ReBrowserMusicPolicy.allowsNavigation(url));
    }
    @Test public void credentialsImpostorsPortsAndExternalSchemesAreDenied() {
        for (String url : new String[]{null,"http://music.163.com/st/webplayer","https://music.163.com.evil.invalid/",
                "https://evil.invalid/?target=https://music.163.com", "https://user:secret@music.163.com/",
                "https://music.163.com:444/", "https://music.163.com./", "file:///data/local/music",
                "content://music.163.com/", "javascript:alert(1)", "intent://music.163.com/", "not a url",
                "https://music.163.com/" + "x".repeat(8192)}) assertFalse(String.valueOf(url), ReBrowserMusicPolicy.allowsNavigation(url));
    }
    @Test public void layoutAppliesOnlyToExactPlayerPath() {
        assertTrue(ReBrowserMusicPolicy.playerDocument(ReBrowserMusicPolicy.HOME + "?source=entry#home"));
        assertFalse(ReBrowserMusicPolicy.playerDocument("https://music.163.com/st/webplayer/"));
        assertFalse(ReBrowserMusicPolicy.playerDocument("https://music.163.com/st/%77ebplayer"));
        assertFalse(ReBrowserMusicPolicy.playerDocument("https://music.163.com/login"));
    }
    @Test public void loginRedirectIsLimitedToTheOfficialMobileEntry() {
        assertTrue(ReBrowserMusicPolicy.allowsNavigation("https://y.music.163.com/m/login"));
        assertTrue(ReBrowserMusicPolicy.loginDocument(ReBrowserMusicPolicy.MOBILE_LOGIN));
        assertTrue(ReBrowserMusicPolicy.loginDocument(ReBrowserMusicPolicy.DESKTOP_LOGIN));
        assertFalse(ReBrowserMusicPolicy.loginDocument(ReBrowserMusicPolicy.HOME));
        assertFalse(ReBrowserMusicPolicy.playerDocument(ReBrowserMusicPolicy.MOBILE_LOGIN));
        for (String url : new String[]{"https://y.music.163.com/", "https://y.music.163.com/m/login/", "https://y.music.163.com/m/%6cogin",
                "https://y.music.163.com/st/webplayer", "http://y.music.163.com/m/login", "https://y.music.163.com:444/m/login",
                "https://user:secret@y.music.163.com/m/login", "https://y.music.163.com.evil.invalid/m/login"})
            assertFalse(url,ReBrowserMusicPolicy.allowsNavigation(url));
    }
    @Test public void mobileSuccessUsesTheOriginalAutomaticRedirectParameter() throws Exception {
        java.net.URI u=new java.net.URI(ReBrowserMusicPolicy.MOBILE_LOGIN);
        assertEquals("y.music.163.com",u.getHost());assertEquals("/m/login",u.getPath());
        assertEquals("redirect_url="+ReBrowserMusicPolicy.HOME,java.net.URLDecoder.decode(u.getRawQuery(),java.nio.charset.StandardCharsets.UTF_8));
        assertFalse(u.getRawQuery().contains("targetUrl"));
    }
    @Test public void fixedProfileNeverAliasesExistingBrowserOrMail() {
        assertNotEquals("Default", ReBrowserMusicPolicy.PROFILE);
        assertFalse(ReBrowserMusicPolicy.PROFILE.startsWith("rebrowser_workspace_"));
        assertEquals("rebrowser_music_netease_v1", ReBrowserMusicPolicy.PROFILE);
    }
}
