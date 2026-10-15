package io.github.reoutlook;

import org.junit.Test;
import static org.junit.Assert.*;

public class ReBrowserMusicPlaybackAuthorizationTest {
    @Test public void onlyFixedExplicitNativePlaybackAuthorizesAndPauseRevokes() {
        for (String command : new String[]{"resume", "previous", "next"})
            assertFalse(ReBrowserMusicPlaybackAuthorization.requiresGesture(command, true));
        assertTrue(ReBrowserMusicPlaybackAuthorization.requiresGesture("pause", false));
    }
    @Test public void nonPlaybackOperationsPreserveExistingGate() {
        for (String command : new String[]{"seek", "like", "back", "window", "recheck", "play", "RESUME", "", null}) {
            assertTrue(ReBrowserMusicPlaybackAuthorization.requiresGesture(command, true));
            assertFalse(ReBrowserMusicPlaybackAuthorization.requiresGesture(command, false));
        }
    }
}
