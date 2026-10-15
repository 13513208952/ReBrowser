package io.github.reoutlook;

import static org.junit.Assert.*;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public final class ReBrowserMusicSnapshotTest {
    private ReBrowserMusicSnapshot parse(JSONObject o) throws Exception {return ReBrowserMusicSnapshot.parse(JSONObject.quote(o.toString()));}
    @Test public void pendingCannotAdvertisePlaying() throws Exception {
        assertFalse(parse(new JSONObject().put("playing",true)).playing);
        assertFalse(parse(new JSONObject().put("canLike",true)).canLike);
        assertFalse(parse(new JSONObject().put("buffering",true)).buffering);
        ReBrowserMusicSnapshot waiting=parse(new JSONObject().put("ready",true).put("buffering",true).put("speed",100));
        assertTrue(waiting.buffering);assertFalse(waiting.playing);assertEquals(1f,waiting.speed,0f);
        assertEquals(1.25f,parse(new JSONObject().put("speed",1.25)).speed,0f);
    }
    @Test public void metadataIsBoundedAndPositionsValidated() throws Exception {
        ReBrowserMusicSnapshot s=parse(new JSONObject().put("ready",true).put("title","x".repeat(500))
                .put("artist","local\nfixture").put("position",-1).put("duration",99_999_999).put("canSeek",true));
        assertEquals(240,s.title.length());assertEquals("local fixture",s.artist);assertEquals(0,s.position);assertFalse(s.canSeek);
    }
    @Test public void artworkIsHttpsWhitelistedWithoutUserInfo() {
        assertEquals("https://p1.music.126.net/image.jpg",ReBrowserMusicSnapshot.artwork("https://p1.music.126.net/image.jpg"));
        assertEquals("https://p1.music.126.net/image.jpg",ReBrowserMusicSnapshot.artwork("http://p1.music.126.net/image.jpg"));
        assertEquals("https://p1.music.126.net/image%20fixture.jpg?param=512y512",ReBrowserMusicSnapshot.artwork("http://p1.music.126.net:80/image%20fixture.jpg?param=512y512"));
        for(String u:new String[]{"http://p1.music.126.net:8080/x","http://p1.music.126.net:443/x","http://user@p1.music.126.net/x","https://p1.music.126.net.evil.invalid/x",
                "https://user@p1.music.126.net/x","file:///private","https://p1.music.126.net:444/x"})
            assertEquals("",ReBrowserMusicSnapshot.artwork(u));
    }
    @Test public void rawOrOversizedStateRejected() {
        assertThrows(Exception.class,()->ReBrowserMusicSnapshot.parse("{}"));
        assertThrows(Exception.class,()->ReBrowserMusicSnapshot.parse("x".repeat(8193)));
    }
}
