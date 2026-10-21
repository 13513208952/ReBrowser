package io.github.rebrowser.music;

import static org.junit.Assert.*;
import org.junit.Test;

/** Projection math only; building/posting a notification requires Android. */
public final class MusicNotificationD03Test {
    @Test public void secondsConvertToMillisecondsAndUnknownStaysUnknown(){
        MusicNotification.Projection projection=new MusicNotification.Projection("标题","歌手",true,90,-1);
        assertEquals(90_000,projection.positionMillis());
        assertEquals(-1,projection.durationMillis());
        assertTrue(projection.unknownDuration());
        assertEquals(90_000,projection.shownProgressMillis());
    }
    @Test public void positionCannotExceedReportedDurationAndBoundsHold(){
        assertEquals(1000,new MusicNotification.Projection("t","a",false,1,1).positionMillis());
        assertThrows(IllegalArgumentException.class,()->new MusicNotification.Projection("t","a",false,2,1));
        assertThrows(IllegalArgumentException.class,()->new MusicNotification.Projection("t","a",false,-1,1));
        assertThrows(IllegalArgumentException.class,()->new MusicNotification.Projection("t","a",false,MusicNotification.MAX_POSITION_SECONDS+1,-1));
    }
    @Test public void actionLabelsAreChineseAndUnknownRejected(){
        assertEquals("播放",MusicNotification.label(MusicNotification.ActionKind.PLAY));
        assertEquals("暂停",MusicNotification.label(MusicNotification.ActionKind.PAUSE));
        assertThrows(IllegalArgumentException.class,()->MusicNotification.label(null));
    }
    @Test public void notificationTextIsBounded(){
        assertThrows(IllegalArgumentException.class,()->new MusicNotification.Projection(new String(new char[129]).replace('\0','t'),"a",false,0,-1));
        assertThrows(IllegalArgumentException.class,()->new MusicNotification.Projection("bad\nline","a",false,0,-1));
    }

    @Test public void snapshotBindsOneFrameAndIsConsumedOnce(){
        MusicNotification.Projection projection=new MusicNotification.Projection("标题","歌手",true,10,100);
        MusicNotification.Snapshot snapshot=MusicNotification.Snapshot.of(projection,java.util.Collections.<MusicNotification.Action>emptyList());
        assertFalse(snapshot.consumed());
        assertTrue(snapshot.take());
        assertTrue(snapshot.consumed());
        assertFalse(snapshot.take());
        assertTrue(MusicNotification.Snapshot.empty().isEmpty());
        assertTrue(MusicNotification.Snapshot.empty().projection().title().isEmpty());
    }
    @Test public void actionListBoundsAreCheckedBeforeAnyAndroidCall(){
        MusicNotification.Projection projection=new MusicNotification.Projection("标题","歌手",false,0,-1);
        assertThrows(IllegalArgumentException.class,()->MusicNotification.Snapshot.of(projection,java.util.Collections.nCopies(5,(MusicNotification.Action)null)));
        assertThrows(IllegalArgumentException.class,()->MusicNotification.Snapshot.of(projection,java.util.Arrays.asList(null,null)));
    }
}
