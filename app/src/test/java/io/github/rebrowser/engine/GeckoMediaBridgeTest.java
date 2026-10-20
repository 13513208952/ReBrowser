package io.github.rebrowser.engine;

import static org.junit.Assert.*;
import org.junit.Test;

/** Observation DTO only; no SDK session, callback or control API is exercised. */
public final class GeckoMediaBridgeTest {
    @Test public void observationKeepsSecondsAndMissingDuration(){
        GeckoMediaBridge.Observation observation=new GeckoMediaBridge.Observation(GeckoMediaBridge.Transport.ACTIVE,"标题","歌手","专辑",12.5,-1,1,7);
        assertEquals(GeckoMediaBridge.Transport.ACTIVE,observation.transport());
        assertEquals(12.5,observation.positionSeconds(),0);
        assertEquals(-1,observation.durationSeconds(),0);
        assertEquals(12_500,observation.positionMillis());
        assertEquals(7,observation.sequence());
    }
    @Test public void unknownPositionDoesNotBecomeZeroMilliseconds(){
        GeckoMediaBridge.Observation observation=new GeckoMediaBridge.Observation(GeckoMediaBridge.Transport.PAUSE_REPORTED,"","","",-1,-1,0,1);
        assertEquals(-1,observation.positionMillis());
        assertEquals(GeckoMediaBridge.Transport.PAUSE_REPORTED,observation.transport());
    }
}
