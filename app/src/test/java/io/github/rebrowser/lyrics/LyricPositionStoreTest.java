package io.github.rebrowser.lyrics;

import static org.junit.Assert.*;
import org.junit.Test;

/** Pure position record; no preference/file write or v1 position read. */
public final class LyricPositionStoreTest {
    @Test public void clampKeepsWholePanelOnScreen(){
        LyricPositionStore.Record record=LyricPositionStore.clamp(5000,-20,1080,2400,360,200,3);
        assertEquals(720,record.x());
        assertEquals(0,record.y());
        assertEquals(3,record.revision());
    }
    @Test public void restoreRequiresExactDisplayAndPanelMatch(){
        LyricPositionStore.Record record=LyricPositionStore.clamp(100,200,1080,2400,360,200,1);
        LyricPositionStore.Restore restore=LyricPositionStore.restore(record,1080,2400,360,200);
        assertTrue(restore.hasPosition());
        assertEquals(100,restore.x());
        assertEquals(200,restore.y());
        assertEquals(LyricPositionStore.RestoreKind.UNKNOWN,LyricPositionStore.restoreExact(record,720,1600,360,200).kind());
        assertEquals(LyricPositionStore.RestoreKind.UNKNOWN,LyricPositionStore.restore(null,1080,2400,360,200).kind());
        assertThrows(IllegalStateException.class,()->LyricPositionStore.restore(null,1080,2400,360,200).x());
    }
    @Test public void invalidRecordsAreRejectedNotRepaired(){
        assertThrows(IllegalArgumentException.class,()->new LyricPositionStore.Record(0,0,1080,2400,360,200,0));
        assertThrows(IllegalArgumentException.class,()->new LyricPositionStore.Record(0,0,0,2400,360,200,1));
        assertThrows(IllegalArgumentException.class,()->new LyricPositionStore.Record(0,0,1080,2400,2000,200,1));
        assertThrows(IllegalArgumentException.class,()->LyricPositionStore.clamp(0,0,0,0,360,200,1));
    }
    @Test public void saveIntentIsLabelledAsNotWritten(){
        String label=LyricPositionStore.saveIntentLabel(LyricPositionStore.clamp(10,20,1080,2400,360,200,2));
        assertTrue(label.contains("未写入"));
        assertThrows(IllegalArgumentException.class,()->LyricPositionStore.saveIntentLabel(null));
    }
}
