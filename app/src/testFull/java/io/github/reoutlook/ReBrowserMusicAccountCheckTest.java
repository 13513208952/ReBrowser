package io.github.reoutlook;

import org.junit.Test;
import static org.junit.Assert.*;

public final class ReBrowserMusicAccountCheckTest {
    @Test public void startsOnlyInCheckingWithFixedOfficialFreshUrl() {
        ReBrowserMusicAccountCheck c=new ReBrowserMusicAccountCheck();
        assertNull(c.begin(false));assertFalse(c.active(null));assertFalse(c.finished(null));
        String url=c.begin(true);
        assertTrue(url.matches("https://music\\.163\\.com/api/w/nuser/account/get\\?_rb=[0-9a-f]{32}"));
        assertTrue(ReBrowserMusicPolicy.allowsNavigation(url));assertFalse(ReBrowserMusicPolicy.playerDocument(url));
        assertFalse(ReBrowserMusicPolicy.loginDocument(url));assertFalse(c.readable(url));
    }
    @Test public void onlyCompletedCurrentDocumentCanBeRead() {
        ReBrowserMusicAccountCheck c=new ReBrowserMusicAccountCheck();String url=c.begin(true);
        assertFalse(c.finished(ReBrowserMusicPolicy.HOME));assertFalse(c.readable(url));
        assertTrue(c.finished(url));assertTrue(c.readable(url));
        c.started(url);assertFalse(c.readable(url));
    }
    @Test public void nonceFencesOldCompletionsAndTimeouts() {
        ReBrowserMusicAccountCheck c=new ReBrowserMusicAccountCheck();String old=c.begin(true),now=c.begin(true);
        assertNotEquals(old,now);assertFalse(c.finished(old));assertFalse(c.fallback(old));
        assertTrue(c.active(now));assertFalse(c.readable(now));
    }
    @Test public void fallbackIsOncePerCheckAndDoesNotClaimLogout() {
        ReBrowserMusicAccountCheck c=new ReBrowserMusicAccountCheck();String url=c.begin(true);
        assertTrue(c.fallback(url));assertFalse(c.fallback(url));assertFalse(c.finished(url));
        assertNull(c.begin(true));assertNull(c.begin(true));
        ReBrowserMusicLifecycle lifecycle=new ReBrowserMusicLifecycle();assertFalse(lifecycle.player());
        assertEquals(ReBrowserMusicLifecycle.Stage.CHECKING,lifecycle.stage());
    }
    @Test public void errorsFenceFinishedAndAlreadyQueuedReadsBeforeNavigation() {
        ReBrowserMusicAccountCheck c=new ReBrowserMusicAccountCheck();String url=c.begin(true);
        c.finished(url);assertTrue(c.readable(url));c.fail(url);
        assertFalse(c.readable(url));assertFalse(c.finished(url));assertTrue(c.fallback(url));
    }
    @Test public void unrelatedErrorsDoNotInvalidateCurrentCheck() {
        ReBrowserMusicAccountCheck c=new ReBrowserMusicAccountCheck();String url=c.begin(true);
        c.fail(ReBrowserMusicPolicy.HOME);assertTrue(c.finished(url));assertTrue(c.readable(url));
    }
    @Test public void successfulStageChangeInvalidatesOldAccountRequests() {
        ReBrowserMusicAccountCheck c=new ReBrowserMusicAccountCheck();String url=c.begin(true);c.finished(url);
        assertNull(c.begin(false));assertFalse(c.active(url));assertFalse(c.readable(url));assertFalse(c.fallback(url));
    }
    @Test public void explicitNewCheckGetsAFreshFallbackBudget() {
        ReBrowserMusicAccountCheck c=new ReBrowserMusicAccountCheck();String old=c.begin(true);c.fail(old);c.fallback(old);
        c.restart();String now=c.begin(true);assertNotEquals(old,now);assertFalse(c.fallback(old));
        assertTrue(c.finished(now));assertTrue(c.readable(now));assertTrue(c.fallback(now));
    }
}
