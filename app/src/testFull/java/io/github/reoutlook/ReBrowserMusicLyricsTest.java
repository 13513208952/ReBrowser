package io.github.reoutlook;

import static org.junit.Assert.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class) @Config(sdk=31)
public final class ReBrowserMusicLyricsTest {
    private static JSONObject line(Object time, Object text) throws Exception { return new JSONObject().put("time",time).put("text",text); }
    private static String value(String track,String status,JSONObject... lines) throws Exception {
        return JSONObject.quote(new JSONObject().put("track",track).put("status",status).put("lines",new JSONArray(lines)).toString());
    }
    @Test public void actualMillisecondsPauseSeekAndDuplicateTimesUseLatestObservedPosition() throws Exception {
        var lyric=ReBrowserMusicLyrics.parse(value("42","ready",line(1000,"A"),line(2000,"B"),line(2000,"C"),line(3000,"")),"42");
        assertEquals("",lyric.textAt(999));assertEquals("A",lyric.textAt(1000));assertEquals("C",lyric.textAt(2500));
        assertEquals("A",lyric.textAt(1500));assertEquals("",lyric.textAt(4000)); // Seek backwards; empty line clears.
        assertEquals("C",lyric.textAt(2500));assertEquals("C",lyric.textAt(2500)); // No synthetic clock while paused.
    }
    @Test public void offsetIsNotAppliedAgainAndLyricsArePlainText() throws Exception {
        var lyric=ReBrowserMusicLyrics.parse(value("42","ready",line(500,"<b>Fixture</b>\nText")),"42");
        assertEquals("",lyric.textAt(499));assertEquals("<b>Fixture</b> Text",lyric.textAt(500));
    }
    @Test public void statesRemainDistinctAndNeverReplaceMetadataWithLyrics() throws Exception {
        for(String status:new String[]{"loading","none","untimed","too-large","timeout","unavailable"}) {
            var lyric=ReBrowserMusicLyrics.parse(value("42",status),"42");assertEquals(status,lyric.status);
            assertEquals("",lyric.textAt(1000));assertFalse(lyric.message(1000).isEmpty());
        }
        assertTrue(ReBrowserMusicLyrics.state("42","loading").pending());assertFalse(ReBrowserMusicLyrics.state("42","none").pending());
    }
    @Test public void mismatchedSongAndUnknownStatusAreRejected() throws Exception {
        rejects(value("41","none"));rejects(value("42","invented"));
    }
    @Test public void malformedEnvelopesAreRejected() throws Exception {
        for(String raw:new String[]{"null","{}","42","\"not-json\"","x".repeat(1_048_577)})rejects(raw);
    }
    @Test public void lineCountAndTextBoundsAreEnforced() throws Exception {
        rejects(value("42","ready"));rejects(value("42","ready",line(0,"x".repeat(257))));
        JSONObject[] many=new JSONObject[513];java.util.Arrays.fill(many,line(0,"x"));rejects(value("42","ready",many));
        JSONObject[] maximum=new JSONObject[512];java.util.Arrays.fill(maximum,line(0,"x".repeat(256)));
        assertEquals(256,ReBrowserMusicLyrics.parse(value("42","ready",maximum),"42").textAt(0).length());
    }
    @Test public void untrustedTimesTypesAndOrderFailClosed() throws Exception {
        for(Object time:new Object[]{-1,86400001,1.5,"1000",JSONObject.NULL})rejects(value("42","ready",line(time,"x")));
        rejects(value("42","ready",line(2000,"A"),line(1000,"B")));rejects(value("42","ready",line(0,123)));
    }
    @Test public void emptyAndMissingTimestampsAreNotInvented() throws Exception {
        assertEquals("♪",ReBrowserMusicLyrics.parse(value("42","ready",line(10,"")),"42").message(20));
        rejects(value("42","ready",new JSONObject().put("text","fixture")));
    }
    private static void rejects(String value) throws Exception {
        try { ReBrowserMusicLyrics.parse(value,"42");fail("accepted invalid lyric"); } catch(IllegalArgumentException|org.json.JSONException expected) { /* fail closed */ }
    }
}
