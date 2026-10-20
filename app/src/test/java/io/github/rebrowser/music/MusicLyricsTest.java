package io.github.rebrowser.music;

import static org.junit.Assert.*;
import org.junit.Test;

/** Pure bounded parsing; no playback, page script or network is involved. */
public final class MusicLyricsTest {
    @Test public void lrcLinesAreSortedAndMetadataIgnored(){
        MusicLyrics.Lyrics lyrics=MusicLyrics.parseLrc("[ar:artist]\n[00:01.50]second\n[00:00.10]first\nplain text");
        assertEquals(MusicLyrics.Status.PARSED,lyrics.status());
        assertEquals(2,lyrics.lines().size());
        assertEquals("first",lyrics.lines().get(0).text());
        assertEquals(100,lyrics.lines().get(0).timeMs());
        assertEquals("second",lyrics.lines().get(1).text());
        assertEquals(1500,lyrics.lines().get(1).timeMs());
    }
    @Test public void multipleStampsShareBodyAndFractionUsesTwoDigits(){
        MusicLyrics.Lyrics lyrics=MusicLyrics.parseLrc("[00:01.05][01:02.7]same");
        assertEquals(2,lyrics.lines().size());
        assertEquals(1050,lyrics.lines().get(0).timeMs());
        assertEquals(62700,lyrics.lines().get(1).timeMs());
    }
    @Test public void threeFractionalDigitsPreserveRealMilliseconds(){
        MusicLyrics.Lyrics lyrics=MusicLyrics.parseLrc("[00:01.005]five ms\n[00:01.05]fifty ms\n[00:01.5]five hundred ms");
        assertEquals(1005,lyrics.lines().get(0).timeMs());
        assertEquals(1050,lyrics.lines().get(1).timeMs());
        assertEquals(1500,lyrics.lines().get(2).timeMs());
    }
    @Test public void unsortedWordTimingsAreStablySortedBeforePositionMapping(){
        java.util.List<MusicLyrics.Word> words=MusicLyrics.parseWords("2000\t2400\tsecond\n1000\t1400\tfirst\n1000\t1500\ttied");
        assertEquals("first",words.get(0).text());
        assertEquals("tied",words.get(1).text());
        MusicLyrics.Lyrics lyrics=MusicLyrics.parse("",java.util.Arrays.asList(
                new MusicLyrics.Word(2000,2400,"second"),
                new MusicLyrics.Word(1000,1400,"first"),
                new MusicLyrics.Word(1000,1500,"tied")));
        assertEquals("first",lyrics.words().get(0).text());
        assertEquals("tied",lyrics.words().get(1).text());
        assertEquals(1,lyrics.wordIndexAt(1500));
        assertEquals(2,lyrics.wordIndexAt(2300));
        assertTrue(lyrics.overlappingWords());
        assertThrows(IllegalArgumentException.class,()->MusicLyrics.parse("",java.util.Arrays.asList((MusicLyrics.Word)null)));
    }
    @Test public void dotRequiresAtLeastOneFractionDigit(){
        assertEquals(1,MusicLyrics.parseLrc("[00:01.]x\n[00:02.0]ok").lines().size());
        assertEquals("ok",MusicLyrics.parseLrc("[00:01.]x\n[00:02.0]ok").lines().get(0).text());
        assertTrue(MusicLyrics.parseLrc("[00:01.]x").lines().isEmpty());
        assertEquals(1500,MusicLyrics.parseLrc("[00:01.5]x").lines().get(0).timeMs());
        assertEquals(1005,MusicLyrics.parseLrc("[00:01.005]x").lines().get(0).timeMs());
    }
    @Test public void invalidStampsAreIgnoredRatherThanGuessed(){
        MusicLyrics.Lyrics lyrics=MusicLyrics.parseLrc("[00:99.00]bad\n[abc]bad\n[00:00.00]ok");
        assertEquals(1,lyrics.lines().size());
        assertEquals("ok",lyrics.lines().get(0).text());
        assertEquals(MusicLyrics.Status.EMPTY,MusicLyrics.parseLrc(null).status());
    }
    @Test public void wordTimingsAreBoundedAndOverlapReported(){
        java.util.List<MusicLyrics.Word> words=MusicLyrics.parseWords("0\t500\ta\n400\t900\tb");
        assertEquals(2,words.size());
        assertTrue(MusicLyrics.parse("",words).overlappingWords());
        assertThrows(IllegalArgumentException.class,()->MusicLyrics.parseWords("500\t400\tb"));
        assertThrows(IllegalArgumentException.class,()->MusicLyrics.parseWords("0\t10"));
    }
    @Test public void positionMappingNeverExtrapolates(){
        MusicLyrics.Lyrics lyrics=MusicLyrics.parse("[00:00.00]a\n[00:02.00]b",MusicLyrics.parseWords("0\t1000\tx\n2000\t3000\ty"));
        assertEquals(0,lyrics.lineIndexAt(0));
        assertEquals(0,lyrics.lineIndexAt(1999));
        assertEquals(1,lyrics.lineIndexAt(2000));
        assertEquals(0,lyrics.wordIndexAt(1500));
        assertThrows(IllegalArgumentException.class,()->lyrics.lineIndexAt(-1));
    }
}
