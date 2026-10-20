package io.github.rebrowser.ui;

import static org.junit.Assert.*;
import org.junit.Test;

/** Intent/period model only; no DB query, deletion or pause is performed. */
public final class HistoryActivityTest {
    @Test public void periodsAreFixedAndUnknownDaysRejected(){
        assertEquals(HistoryActivity.Period.DAYS_30,HistoryActivity.Period.fromDays(30));
        assertEquals(HistoryActivity.Period.ALL,HistoryActivity.Period.fromDays(0));
        assertThrows(IllegalArgumentException.class,()->HistoryActivity.Period.fromDays(1));
        assertThrows(IllegalArgumentException.class,()->HistoryActivity.Period.fromDays(-7));
    }
    @Test public void queryIntentIsNotAnAppliedState(){
        HistoryActivity.QueryIntent intent=new HistoryActivity.QueryIntent(HistoryActivity.Period.DAYS_7,true,"page");
        assertEquals(HistoryActivity.Period.DAYS_7,intent.period());
        assertTrue(intent.pauseWrites());
        assertEquals("page",intent.expectedPageToken());
        assertFalse(intent.isApplied());
        assertThrows(IllegalArgumentException.class,()->new HistoryActivity.QueryIntent(null,false,"p"));
    }
    @Test public void reasonTextIsBoundedAndSuggestiveOnly(){
        assertEquals("",HistoryActivity.boundedReason(null));
        assertEquals("未连接",HistoryActivity.boundedReason("未连接"));
        assertThrows(IllegalArgumentException.class,()->HistoryActivity.boundedReason(new String(new char[201]).replace('\0','x')));
        assertTrue(HistoryActivity.emptyPage().isEmpty());
    }
}
