package io.github.rebrowser.engine;

import static org.junit.Assert.*;
import org.junit.Test;

/** URL-free observation summary only; no history store or fabricated visited answer. */
public final class GeckoHistoryDelegateTest {
    @Test public void unconnectedBackendIsReportedExplicitly(){
        GeckoHistoryDelegate.Observation observation=new GeckoHistoryDelegate.Observation(GeckoHistoryDelegate.Status.UNCONNECTED,0,-1,false,1);
        assertEquals(GeckoHistoryDelegate.Status.UNCONNECTED,observation.status());
        assertEquals(0,observation.entryCount());
        assertEquals(-1,observation.currentIndex());
    }
    @Test public void observedSummaryCarriesNoUrlField(){
        GeckoHistoryDelegate.Observation observation=new GeckoHistoryDelegate.Observation(GeckoHistoryDelegate.Status.OBSERVED,3,2,true,4);
        assertEquals(3,observation.entryCount());
        assertEquals(2,observation.currentIndex());
        assertTrue(observation.titleTopLevelOnly());
        for(java.lang.reflect.Field field:GeckoHistoryDelegate.Observation.class.getDeclaredFields()){
            assertEquals(false,field.getType()==String.class);
        }
    }
}
