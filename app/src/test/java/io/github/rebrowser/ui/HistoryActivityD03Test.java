package io.github.rebrowser.ui;

import static org.junit.Assert.*;
import org.junit.Test;

/** Intent/period model only; no DB query, deletion or pause is performed. */
public final class HistoryActivityD03Test {
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

    @Test public void filterCoversCurrentPageOnlyAndKeepsOriginalTokens(){
        HistoryAdapter.Row first=new HistoryAdapter.Row("t1","Alpha","https://a.example/","今天");
        HistoryAdapter.Row second=new HistoryAdapter.Row("t2","Beta","https://b.example/","昨天");
        java.util.List<HistoryAdapter.Row> page=java.util.Arrays.asList(first,second);
        java.util.List<HistoryAdapter.Row> filtered=HistoryActivity.Filter.apply(page,"alp");
        assertEquals(1,filtered.size());
        assertSame(first,filtered.get(0));
        assertEquals("t1",filtered.get(0).itemRef());
        assertEquals(2,HistoryActivity.Filter.apply(page,"").size());
        assertEquals(1,HistoryActivity.Filter.apply(page,"b.example").size());
        assertThrows(IllegalArgumentException.class,()->HistoryActivity.Filter.apply(page,new String(new char[65]).replace('\0','x')));
    }
    @Test public void pageNavNeedsExplicitTrueAndRequestKeepsRevision(){
        HistoryActivity.PageNav unknown=new HistoryActivity.PageNav(null,null);
        assertFalse(unknown.available(HistoryActivity.PageDirection.NEXT));
        HistoryActivity.PageNav nav=new HistoryActivity.PageNav(Boolean.FALSE,Boolean.TRUE);
        assertFalse(nav.available(HistoryActivity.PageDirection.PREVIOUS));
        assertTrue(nav.available(HistoryActivity.PageDirection.NEXT));
        HistoryActivity.PageRequest request=new HistoryActivity.PageRequest(HistoryActivity.PageDirection.NEXT,7);
        assertEquals(HistoryActivity.PageDirection.NEXT,request.direction());
        assertEquals(7,request.expectedRevision());
        assertFalse(request.isApplied());
        assertThrows(IllegalArgumentException.class,()->new HistoryActivity.PageRequest(null,1));
    }
}
