package io.github.rebrowser.music;

import static org.junit.Assert.*;
import org.junit.Test;

/** Immutable display queue; tokens are opaque ids, never URLs or playback grants. */
public final class MusicQueueTest {
    private static MusicQueue.Track track(String token){return new MusicQueue.Track(token,"标题","歌手","专辑",180_000,MusicQueue.Like.UNKNOWN);}
    @Test public void queueIsImmutableAndRejectsDuplicateOpaqueTokens(){
        MusicQueue queue=new MusicQueue(java.util.Arrays.asList(track("a"),track("b")),3,"官方页面");
        assertEquals(2,queue.tracks().size());
        assertThrows(UnsupportedOperationException.class,()->queue.tracks().clear());
        assertThrows(IllegalArgumentException.class,()->new MusicQueue(java.util.Arrays.asList(track("a"),track("a")),1,"x"));
        assertThrows(IllegalArgumentException.class,()->track("https://music.example/song"));
        assertThrows(IllegalArgumentException.class,()->track("bad token"));
    }
    @Test public void intentsCaptureExactTokenAndRevisionWithoutClaimingOutcome(){
        MusicQueue queue=new MusicQueue(java.util.Arrays.asList(track("abc")),7,"官方页面");
        MusicQueue.Intent play=queue.play(0);
        assertEquals(MusicQueue.Action.PLAY_ENTRY,play.action());
        assertEquals("abc",play.token());
        assertEquals(7,play.expectedRevision());
        assertFalse(play.isOutcome());
        assertThrows(IllegalArgumentException.class,()->queue.play(1));
        assertThrows(IllegalArgumentException.class,()->new MusicQueue(java.util.Collections.emptyList(),0,"x"));
    }
    @Test public void unknownLikeStateAndDurationsStayExplicit(){
        MusicQueue.Track track=new MusicQueue.Track("a","标题","歌手","专辑",-1,MusicQueue.Like.UNKNOWN);
        assertEquals(-1,track.durationMs());
        assertEquals(MusicQueue.Like.UNKNOWN,track.liked());
        assertThrows(IllegalArgumentException.class,()->new MusicQueue.Track("a","t","a","b",1,null));
        assertEquals(0,MusicQueue.empty().tracks().size());
    }
}
