package io.github.rebrowser.music;

import static org.junit.Assert.*;
import org.junit.Test;

/** Observation projection: reported facts only, with no permission decision methods. */
public final class MusicStateD03Test {
    private static MusicState state(){return new MusicState(MusicState.Account.REPORTED_SIGNED_IN,MusicState.Playback.PAUSED,MusicState.Focus.NONE,"歌","人","tok",1000,2000,2,50);}
    @Test public void stateValidatesAxesAndPosition(){
        MusicState state=state();
        assertEquals(MusicState.Playback.PAUSED,state.playback());
        assertEquals(1000,state.positionMs());
        assertTrue(state.hasReportedDuration());
        assertThrows(IllegalArgumentException.class,()->new MusicState(MusicState.Account.UNKNOWN,MusicState.Playback.PLAYING,MusicState.Focus.NONE,"t","a",null,3000,2000,1,0));
        assertThrows(IllegalArgumentException.class,()->new MusicState(MusicState.Account.UNKNOWN,MusicState.Playback.PLAYING,MusicState.Focus.NONE,"t","a",null,-1,2000,1,0));
        assertThrows(IllegalArgumentException.class,()->new MusicState(MusicState.Account.UNKNOWN,MusicState.Playback.PLAYING,MusicState.Focus.NONE,"t","a",null,0,2000,0,0));
    }
    @Test public void labelsNeverUpgradeReportedStateToAuthority(){
        assertEquals("播放状态未知",new MusicState(MusicState.Account.UNKNOWN,MusicState.Playback.UNKNOWN,MusicState.Focus.UNOBSERVED,"","",null,0,-1,1,0).playbackLabel());
        assertTrue(state().accountLabel().contains("官网报告"));
    }
    @Test public void generationMustAdvanceAndObservationKeepsIt(){
        MusicState state=state();
        assertEquals(3,state.withGeneration(3).intentGeneration());
        assertThrows(IllegalArgumentException.class,()->state.withGeneration(2));
        MusicState observed=state.observe(MusicState.Playback.PLAYING,1500,2000,60);
        assertEquals(2,observed.intentGeneration());
        assertEquals(MusicState.Playback.PLAYING,observed.playback());
    }
    @Test public void noPositiveAuthorizationMethodsExist(){
        for(java.lang.reflect.Method method:MusicState.class.getDeclaredMethods()){
            String name=method.getName().toLowerCase(java.util.Locale.ROOT);
            for(String forbidden:new String[]{"mayplay","isauthorized","ownergrant","grant","login","resumeplay"})assertFalse(name.contains(forbidden));
        }
    }

    @Test public void estimateRequiresPlayingKnownClockAndSameGeneration(){
        MusicState playing=new MusicState(MusicState.Account.UNKNOWN,MusicState.Playback.PLAYING,MusicState.Focus.NONE,"t","a",null,1000,5000,2,100);
        MusicState.Estimate estimate=playing.estimatePosition(2,600,1d);
        assertTrue(estimate.usable());
        assertEquals(1500,estimate.positionMs());
        assertTrue(playing.estimatePosition(2,100,1d).usable());
        assertEquals(1000,playing.estimatePosition(2,100,1d).positionMs());
        assertTrue(playing.estimatePosition(2,99,1d).stale());
        assertTrue(playing.estimatePosition(3,600).stale());
        assertEquals(5000,playing.estimatePosition(2,9000,1d).positionMs());
        assertTrue(playing.estimatePosition(2,9000,1d).clamped());
        MusicState paused=new MusicState(MusicState.Account.UNKNOWN,MusicState.Playback.PAUSED,MusicState.Focus.NONE,"t","a",null,1000,5000,2,100);
        assertFalse(paused.estimatePosition(2,600).known());
        MusicState unknown=new MusicState(MusicState.Account.UNKNOWN,MusicState.Playback.UNKNOWN,MusicState.Focus.NONE,"","",null,0,-1,1,0);
        assertEquals(MusicState.Playback.UNKNOWN,unknown.playback());
        assertFalse(unknown.estimatePosition(1,600).known());
        assertThrows(IllegalArgumentException.class,()->playing.estimatePosition(2,-1));
    }
}
