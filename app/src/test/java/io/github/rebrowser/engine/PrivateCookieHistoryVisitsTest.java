package io.github.rebrowser.engine;
import static org.junit.Assert.*;
import java.util.*;
import org.junit.Test;
/** HOST production metadata reducer, not actual Gecko callback/document/source evidence. */
public final class PrivateCookieHistoryVisitsTest {
 private static final String A="https://example.test/a",B="https://example.test/b";
 private final List<String> visits=new ArrayList<>();
 private final GeckoHistoryDelegate.PrivateSessionVisits tracker=new GeckoHistoryDelegate.PrivateSessionVisits(visits::add);
 private void loaded(String uri){tracker.start(uri);tracker.location(uri);tracker.stop(true);}
 @Test public void requiresNewNativeLocationAndSuccessfulStop(){tracker.start(A);tracker.stop(true);assertTrue(visits.isEmpty());tracker.location(A);assertEquals(Arrays.asList(A),visits);}
 @Test public void locationBeforeStopWaitsForNativeSuccess(){tracker.start(A);tracker.location(A);assertTrue(visits.isEmpty());tracker.stop(true);assertEquals(Arrays.asList(A),visits);}
 @Test public void failedLoadNeverRecordedEvenWithNativeLocation(){tracker.start(A);tracker.location(A);tracker.stop(false);tracker.location(A);assertTrue(visits.isEmpty());}
 @Test public void redirectSourceIsNotEmitted(){tracker.start(A);tracker.location(A);tracker.location(B);tracker.stop(true);assertEquals(Arrays.asList(B),visits);}
 @Test public void duplicateStateAndStopDoNotCreateVisits(){loaded(A);tracker.location(A);tracker.stop(true);assertEquals(Arrays.asList(A),visits);}
 @Test public void actualReloadCanRecordSameUriWithoutAnotherLocation(){loaded(A);tracker.start(A);tracker.stop(true);assertEquals(Arrays.asList(A,A),visits);}
 @Test public void backAndForwardAreActualVisits(){loaded(A);loaded(B);loaded(A);loaded(B);assertEquals(Arrays.asList(A,B,A,B),visits);}
 @Test public void nativeFragmentLocationCanRecordWithoutAnotherLoad(){loaded(A);tracker.location(A+"#fragment");tracker.location(A+"#fragment");assertEquals(Arrays.asList(A,A+"#fragment"),visits);}
 @Test public void oldLocationCannotBorrowNewDifferentLoadSuccess(){loaded(A);tracker.start(B);tracker.stop(true);assertEquals(Arrays.asList(A),visits);tracker.location(B);assertEquals(Arrays.asList(A,B),visits);}
 @Test public void nativeBlankUnsupportedOrCredentialUrisCannotBecomeVisits(){for(String uri:new String[]{"about:blank","file:///private","javascript:alert(1)","https://user:pass@example.test/"})loaded(uri);assertTrue(visits.isEmpty());}
 @Test public void retirementDropsAndPermanentlyRetiresPrivateProjection(){loaded(A);tracker.clear();tracker.start(B);tracker.location(B);tracker.stop(true);assertEquals(Arrays.asList(A),visits);}
 @Test public void actualContentPaintRecordsViewedPageBeforeFullLoadEnds(){tracker.start(A);tracker.location(A);tracker.painted();assertEquals(Arrays.asList(A),visits);}
 @Test public void interruptedAfterPaintStillWasAnActualVisit(){tracker.start(A);tracker.location(A);tracker.painted();tracker.stop(false);assertEquals(Arrays.asList(A),visits);}
 @Test public void redirectBeforePaintRecordsOnlyFinalVisibleDestination(){tracker.start(A);tracker.location(A);tracker.location(B);tracker.painted();tracker.stop(true);assertEquals(Arrays.asList(B),visits);}
 @Test public void nativeErrorBeforeAnyPaintDoesNotBecomeSuccessfulVisit(){tracker.start(A);tracker.location(A);tracker.error();tracker.painted();tracker.stop(true);assertTrue(visits.isEmpty());}
 @Test public void paintStillNeedsActualWebLocation(){tracker.start(A);tracker.painted();assertTrue(visits.isEmpty());tracker.location(A);assertEquals(Arrays.asList(A),visits);}
 @Test public void repeatedPaintAndStopDoNotDuplicateOneVisit(){tracker.start(A);tracker.location(A);tracker.painted();tracker.painted();tracker.stop(true);tracker.painted();assertEquals(Arrays.asList(A),visits);}
 @Test public void bootstrapPaintCannotRecordARequestedWebUrl(){tracker.start("about:blank");tracker.location("about:blank");tracker.painted();tracker.location(A);assertTrue(visits.isEmpty());}
 @Test public void retirementRejectsLatePaint(){loaded(A);tracker.clear();tracker.painted();assertEquals(Arrays.asList(A),visits);}
 @Test public void successfulBootstrapBlankIsNotSuccessfulWebLoad(){loaded("about:blank");tracker.location(A);assertTrue(visits.isEmpty());}
}
