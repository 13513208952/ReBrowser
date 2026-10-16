package io.github.reoutlook;

import org.junit.Test;
import static org.junit.Assert.*;

public final class ReBrowserMusicPreparationTest {
    @Test public void noReadinessBeforeRequestAndAcknowledgement() {
        ReBrowserMusicPreparation p = new ReBrowserMusicPreparation();
        assertFalse(p.acknowledge(true)); assertFalse(p.acknowledged());
        assertFalse(p.paused(false, false));
        assertTrue(p.request()); assertFalse(p.acknowledged());
        assertFalse(p.paused(false, false));
    }
    @Test public void oneRequestAndOneSuccessfulWakePerDocument() {
        ReBrowserMusicPreparation p = new ReBrowserMusicPreparation();
        assertTrue(p.request());
        for (int i=0;i<10_000;i++) assertFalse(p.request());
        assertTrue(p.acknowledge(true)); assertFalse(p.acknowledge(true));
        assertTrue(p.paused(false, false));
    }
    @Test public void failedSubmissionStaysCoveredWithoutAutomaticRequestStorm() {
        ReBrowserMusicPreparation p = new ReBrowserMusicPreparation();
        assertTrue(p.request()); assertFalse(p.acknowledge(false));
        assertFalse(p.acknowledged()); assertFalse(p.request()); assertFalse(p.paused(false,false));
        p.reset(); assertTrue(p.request()); assertTrue(p.acknowledge(true));
    }
    @Test public void freshStateMustAlsoBePausedAndNotBuffering() {
        ReBrowserMusicPreparation p = new ReBrowserMusicPreparation();
        p.request(); p.acknowledge(true);
        assertFalse(p.paused(true,false)); assertFalse(p.paused(false,true));
        assertFalse(p.paused(true,true)); assertTrue(p.paused(false,false));
    }
    @Test public void documentResetRevokesAcknowledgement() {
        ReBrowserMusicPreparation p = new ReBrowserMusicPreparation();
        p.request(); p.acknowledge(true); p.reset();
        assertFalse(p.acknowledged()); assertFalse(p.paused(false,false));
        assertFalse(p.acknowledge(true)); assertTrue(p.request());
    }
    @Test public void normalPlaybackDoesNotRequireAnotherColdPause() {
        ReBrowserMusicPreparation p = new ReBrowserMusicPreparation();
        p.request(); p.acknowledge(true);
        assertFalse(p.paused(true,false)); // A fresh startup would remain covered.
        assertTrue(p.acknowledged()); assertFalse(p.request()); // Do not re-pause a prepared runtime.
    }
}
