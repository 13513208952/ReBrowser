package io.github.rebrowser.system;

import static org.junit.Assert.*;
import org.junit.Test;

/** Role/latch models only; no actual Android role observation or Intent/default change. */
public final class DefaultBrowserFlowTest {
    @Test public void unknownRoleIsNotReportedAsHeldOrNot(){
        DefaultBrowserFlow flow=new DefaultBrowserFlow();
        DefaultBrowserFlow.Observation observation=flow.observe(null);
        assertEquals(DefaultBrowserFlow.Role.UNKNOWN,observation.role());
        assertEquals("默认浏览器状态未知",observation.label());
    }
    @Test public void heldRoleStillOffersRecommendationWithoutChangingIt(){
        DefaultBrowserFlow flow=new DefaultBrowserFlow();
        flow.observe(Boolean.TRUE);
        DefaultBrowserFlow.Recommendation recommendation=flow.recommendation(DefaultBrowserFlow.RequestKind.OPEN_SYSTEM_DEFAULT_APPS_SETTINGS);
        assertEquals(1,recommendation.expectedRevision());
        assertFalse(recommendation.dispatchAttempted());
        DefaultBrowserFlow.Observation notHeld=flow.observe(Boolean.FALSE);
        assertFalse(notHeld.roleHeld());
    }
    @Test public void oneAttemptDoesNotAssignRoleOrBecomeReusable(){
        DefaultBrowserFlow flow=new DefaultBrowserFlow();flow.observe(Boolean.FALSE);
        DefaultBrowserFlow.Recommendation request=flow.recommendation(DefaultBrowserFlow.RequestKind.OPEN_SYSTEM_DEFAULT_APPS_SETTINGS);
        flow.claimAttempt(request);assertTrue(request.dispatchAttempted());assertFalse(flow.current().roleHeld());
        assertThrows(SecurityException.class,()->flow.claimAttempt(request));
    }
    @Test public void requestCannotBeSpentByOtherFlowAfterNewObservationOrRetirement(){
        DefaultBrowserFlow flow=new DefaultBrowserFlow(),other=new DefaultBrowserFlow();flow.observe(Boolean.FALSE);other.observe(Boolean.FALSE);
        DefaultBrowserFlow.Recommendation request=flow.recommendation(DefaultBrowserFlow.RequestKind.OPEN_SYSTEM_DEFAULT_APPS_SETTINGS);
        assertThrows(SecurityException.class,()->other.claimAttempt(request));assertFalse(request.dispatchAttempted());
        flow.observe(null);assertThrows(SecurityException.class,()->flow.claimAttempt(request));assertFalse(request.dispatchAttempted());
        DefaultBrowserFlow.Recommendation fresh=flow.recommendation(DefaultBrowserFlow.RequestKind.OPEN_SYSTEM_DEFAULT_APPS_SETTINGS);
        flow.retire();assertThrows(SecurityException.class,()->flow.claimAttempt(fresh));
    }
    @Test public void latchReadAndAttemptRequireOriginalThread()throws Exception{
        DefaultBrowserFlow flow=new DefaultBrowserFlow();flow.observe(Boolean.FALSE);
        DefaultBrowserFlow.Recommendation request=flow.recommendation(DefaultBrowserFlow.RequestKind.OPEN_SYSTEM_DEFAULT_APPS_SETTINGS);
        java.util.concurrent.atomic.AtomicReference<Throwable> failure=new java.util.concurrent.atomic.AtomicReference<>();
        Thread t=new Thread(()->{try{flow.claimAttempt(request);}catch(Throwable rejected){failure.set(rejected);}});t.start();t.join();
        assertTrue(failure.get() instanceof IllegalStateException);assertFalse(request.dispatchAttempted());
    }
    @Test public void staleOrMissingObservationRejectsRecommendation(){
        DefaultBrowserFlow flow=new DefaultBrowserFlow();
        assertThrows(IllegalStateException.class,()->flow.current());
        assertThrows(IllegalStateException.class,()->flow.recommendation(DefaultBrowserFlow.RequestKind.OPEN_SYSTEM_DEFAULT_APPS_SETTINGS));
        flow.observe(Boolean.TRUE);
        assertThrows(IllegalArgumentException.class,()->flow.recommendation(null));
    }
}
