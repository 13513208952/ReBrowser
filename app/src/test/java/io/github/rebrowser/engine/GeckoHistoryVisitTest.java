package io.github.rebrowser.engine;
import org.junit.Test;
import static org.junit.Assert.*;
import org.mozilla.geckoview.GeckoSession;
/** Actual production flag/URL classification, no Gecko callback/device/network execution. */
public final class GeckoHistoryVisitTest {
 @Test public void ordinaryTopLevelAndRedirectDestinationsAreVisits(){int top=GeckoSession.HistoryDelegate.VISIT_TOP_LEVEL;assertTrue(GeckoHistoryDelegate.acceptsVisit("https://example.test/",top));assertTrue(GeckoHistoryDelegate.acceptsVisit("https://example.test/",top|GeckoSession.HistoryDelegate.VISIT_REDIRECT_TEMPORARY));assertTrue(GeckoHistoryDelegate.acceptsVisit("https://example.test/",top|GeckoSession.HistoryDelegate.VISIT_REDIRECT_PERMANENT));}
 @Test public void frameRedirectSourcesAndFailedLoadsNeverBecomeVisits(){int top=GeckoSession.HistoryDelegate.VISIT_TOP_LEVEL;assertFalse(GeckoHistoryDelegate.acceptsVisit("https://example.test/",0));for(int flag:new int[]{GeckoSession.HistoryDelegate.VISIT_REDIRECT_SOURCE,GeckoSession.HistoryDelegate.VISIT_REDIRECT_SOURCE_PERMANENT,GeckoSession.HistoryDelegate.VISIT_UNRECOVERABLE_ERROR,1<<20})assertFalse(GeckoHistoryDelegate.acceptsVisit("https://example.test/",top|flag));}
 @Test public void blankFileScriptMalformedOrCredentialUrlCannotEnterHistory(){for(String url:new String[]{"about:blank","file:///private","javascript:alert(1)","https://user:secret@example.test/","https://example.test/\n",null})assertFalse(GeckoHistoryDelegate.acceptsVisit(url,GeckoSession.HistoryDelegate.VISIT_TOP_LEVEL));}
 @Test public void queryAndFragmentRemainPrivateHistoryDataNotStrippedOrigin(){assertTrue(GeckoHistoryDelegate.acceptsVisit("https://example.test/path?q=中文#part",GeckoSession.HistoryDelegate.VISIT_TOP_LEVEL));}
}
