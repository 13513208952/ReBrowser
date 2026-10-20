package io.github.rebrowser.system;

import static org.junit.Assert.*;
import org.junit.Test;

/** Role observation + display request only; no resolver/Intent/default change. */
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
        assertFalse(recommendation.isDispatched());
        DefaultBrowserFlow.Observation notHeld=flow.observe(Boolean.FALSE);
        assertFalse(notHeld.roleHeld());
    }
    @Test public void staleOrMissingObservationRejectsRecommendation(){
        DefaultBrowserFlow flow=new DefaultBrowserFlow();
        assertThrows(IllegalStateException.class,()->flow.current());
        assertThrows(IllegalStateException.class,()->flow.recommendation(DefaultBrowserFlow.RequestKind.OPEN_SYSTEM_DEFAULT_APPS_SETTINGS));
        flow.observe(Boolean.TRUE);
        assertThrows(IllegalArgumentException.class,()->flow.recommendation(null));
    }
}
