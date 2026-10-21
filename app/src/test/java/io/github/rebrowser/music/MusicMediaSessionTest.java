package io.github.rebrowser.music;

import static org.junit.Assert.*;
import org.junit.Test;

/** Read-only projection; no MediaSession is created and no transport action is executed. */
public final class MusicMediaSessionTest {
    private static MusicState state(MusicState.Playback playback,long duration){return new MusicState(MusicState.Account.REPORTED_SIGNED_IN,playback,MusicState.Focus.NONE,"歌","人",null,10,duration,1,0);}
    @Test public void playingProjectionOffersPauseAndBoundedSeek(){
        MusicMediaSession.Projection projection=MusicMediaSession.project(state(MusicState.Playback.PLAYING,100),java.util.EnumSet.of(MusicMediaSession.Action.PAUSE,MusicMediaSession.Action.SEEK,MusicMediaSession.Action.SKIP_NEXT));
        assertEquals(MusicMediaSession.Transport.PLAYING,projection.transport());
        assertTrue(projection.actions().contains(MusicMediaSession.Action.PAUSE));
        assertTrue(projection.actions().contains(MusicMediaSession.Action.SKIP_NEXT));
        assertFalse(projection.actions().contains(MusicMediaSession.Action.SKIP_PREVIOUS));
        assertTrue(projection.actions().contains(MusicMediaSession.Action.SEEK));
        assertEquals(0f,projection.speed(),0f);assertFalse(projection.reportedSpeedKnown()); // PLAYING is not a rate observation
    }
    @Test public void unknownPlaybackYieldsNoTransportAction(){
        MusicMediaSession.Projection projection=MusicMediaSession.project(state(MusicState.Playback.UNKNOWN,-1),true,true);
        assertEquals(MusicMediaSession.Transport.NONE,projection.transport());
        assertFalse(projection.actions().contains(MusicMediaSession.Action.PLAY));assertFalse(projection.actions().contains(MusicMediaSession.Action.PAUSE));assertTrue(projection.actions().contains(MusicMediaSession.Action.SKIP_NEXT));
        assertFalse(projection.seekable());
        assertEquals(-1,projection.durationMs());
    }
    @Test public void pausedAndUnknownDurationDoNotFabricateSeek(){
        MusicMediaSession.Projection projection=MusicMediaSession.project(state(MusicState.Playback.PAUSED,-1),false,false);
        assertFalse(projection.actions().contains(MusicMediaSession.Action.PLAY)); // no native feature observation, cannot invent PLAY
        assertFalse(projection.actions().contains(MusicMediaSession.Action.SEEK));
        assertEquals(0f,projection.speed(),0f);
        assertThrows(IllegalArgumentException.class,()->MusicMediaSession.project(null,false,false));
    }
}
