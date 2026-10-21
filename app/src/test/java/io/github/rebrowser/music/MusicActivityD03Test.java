package io.github.rebrowser.music;

import static org.junit.Assert.*;
import org.junit.Test;

/** Display arithmetic for the window; no playback is started by these methods. */
public final class MusicActivityD03Test {
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
        assertFalse(MusicActivity.Model.seekEnabled(playing)); // playback is not permission
        assertTrue(MusicActivity.Model.seekEnabled(playing,true));
        assertTrue(MusicActivity.Model.seekEnabled(paused,true)); // native may offer paused seek
        assertFalse(MusicActivity.Model.seekEnabled(paused,false));
        assertFalse(MusicActivity.Model.seekEnabled(unknownDuration,true));
        assertFalse(MusicActivity.Model.seekEnabled(null));
    }

    @Test public void lyricTextHighlightsOnlyInsideAWordAndNeverKeepsGapWord(){
        MusicLyrics.Lyrics lyrics=MusicLyrics.parse("[00:00.00]line",MusicLyrics.parseWords("100\t200\t甲\n400\t500\t乙"));
        assertTrue(MusicActivity.Model.lyricText(lyrics,150).contains("未映射原行）：甲")); // timing text is absent from original line: no guessed glyph span
        assertFalse(MusicActivity.Model.lyricText(lyrics,300).contains("当前时间词"));
        assertTrue(MusicActivity.Model.lyricText(lyrics,450).contains("未映射原行）：乙"));
        assertFalse(MusicActivity.Model.lyricText(lyrics,600).contains("当前时间词"));
        assertEquals("歌词未读取",MusicActivity.Model.lyricText(null,0));
    }
}
