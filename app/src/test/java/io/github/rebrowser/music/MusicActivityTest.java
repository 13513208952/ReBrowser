package io.github.rebrowser.music;

import static org.junit.Assert.*;
import org.junit.Test;

/** Display arithmetic for the window; no playback is started by these methods. */
public final class MusicActivityTest {
    @Test public void progressAndSeekAreClampedAndBounded(){
        assertEquals(0,MusicActivity.Model.progressPermille(-1,1000));
        assertEquals(500,MusicActivity.Model.progressPermille(500,1000));
        assertEquals(1000,MusicActivity.Model.progressPermille(5000,1000));
        assertEquals(0,MusicActivity.Model.progressPermille(100,0));
        assertEquals(500,MusicActivity.Model.seekTarget(1000,500));
        assertThrows(IllegalArgumentException.class,()->MusicActivity.Model.seekTarget(1000,1001));
        assertThrows(IllegalArgumentException.class,()->MusicActivity.Model.seekTarget(0,1));
    }
    @Test public void seekIsOnlyEnabledForReportedPlayingWithDuration(){
        MusicState playing=new MusicState(MusicState.Account.UNKNOWN,MusicState.Playback.PLAYING,MusicState.Focus.NONE,"t","a",null,10,100,1,0);
        MusicState paused=new MusicState(MusicState.Account.UNKNOWN,MusicState.Playback.PAUSED,MusicState.Focus.NONE,"t","a",null,10,100,1,0);
        MusicState unknownDuration=new MusicState(MusicState.Account.UNKNOWN,MusicState.Playback.PLAYING,MusicState.Focus.NONE,"t","a",null,10,-1,1,0);
        assertTrue(MusicActivity.Model.seekEnabled(playing));
        assertFalse(MusicActivity.Model.seekEnabled(paused));
        assertFalse(MusicActivity.Model.seekEnabled(unknownDuration));
        assertFalse(MusicActivity.Model.seekEnabled(null));
    }
}
