package io.github.rebrowser.ui;

import static org.junit.Assert.*;
import org.junit.Test;

/** Pure row/latch model of the history adapter; no Android view or database is executed. */
public final class HistoryAdapterD03Test {
    private static HistoryAdapter.Row row(Object token,String title){
        return new HistoryAdapter.Row(token,title,"https://example/","时间");
    }
    @Test public void defaultOfferDoesNotFabricateRetainOrDelete(){
        HistoryAdapter.Row row=row(new Object(),"标题");
        assertTrue(row.offers(HistoryAdapter.Offered.OPEN));
        assertFalse(row.offers(HistoryAdapter.Offered.RETAIN));
        assertFalse(row.offers(HistoryAdapter.Offered.REMOVE));
        java.util.Set<HistoryAdapter.Offered> offered=java.util.EnumSet.of(HistoryAdapter.Offered.OPEN,HistoryAdapter.Offered.REMOVE);
        HistoryAdapter.Row explicit=new HistoryAdapter.Row(new Object(),"t","u","v",offered,"原因");
        assertEquals(HistoryAdapter.ActionState.AVAILABLE,HistoryAdapter.Rows.actionState(explicit,HistoryAdapter.Offered.REMOVE,false));
        assertEquals(HistoryAdapter.ActionState.NOT_OFFERED,HistoryAdapter.Rows.actionState(explicit,HistoryAdapter.Offered.RETAIN,false));
        assertEquals("原因",HistoryAdapter.Rows.actionReason(explicit,HistoryAdapter.Offered.RETAIN,HistoryAdapter.ActionState.NOT_OFFERED));
        assertThrows(UnsupportedOperationException.class,()->explicit.offered().add(HistoryAdapter.Offered.RETAIN));
    }
    @Test public void retainAndRemoveAreOneShotPerFrameWhileOpenStaysAvailable(){
        HistoryAdapter.State state=new HistoryAdapter.State();
        Object token=new Object();
        Object frame=state.show(3,java.util.Arrays.asList(new HistoryAdapter.Row(token,"标题","u","v",java.util.EnumSet.of(HistoryAdapter.Offered.OPEN,HistoryAdapter.Offered.REMOVE),"")));
        assertTrue(state.beginAttempt(frame,3,token,HistoryAdapter.Offered.REMOVE));
        assertFalse(state.beginAttempt(frame,3,token,HistoryAdapter.Offered.REMOVE));
        assertTrue(state.wasAttempted(frame,3,token,HistoryAdapter.Offered.REMOVE));
        HistoryAdapter.Row offeredRow=new HistoryAdapter.Row(token,"标题","u","v",java.util.EnumSet.of(HistoryAdapter.Offered.OPEN,HistoryAdapter.Offered.REMOVE),"原因");
        assertEquals(HistoryAdapter.ActionState.ATTEMPTED,HistoryAdapter.Rows.actionState(offeredRow,HistoryAdapter.Offered.REMOVE,true));
        assertEquals(HistoryAdapter.ActionState.AVAILABLE,HistoryAdapter.Rows.actionState(row(token,"标题"),HistoryAdapter.Offered.OPEN,true));
        assertFalse(state.beginAttempt(frame,4,token,HistoryAdapter.Offered.REMOVE));
        Object next=state.show(4,java.util.Arrays.asList(new HistoryAdapter.Row(token,"标题","u","v",java.util.EnumSet.of(HistoryAdapter.Offered.OPEN,HistoryAdapter.Offered.REMOVE),"")));
        assertFalse(state.wasAttempted(next,4,token,HistoryAdapter.Offered.REMOVE));
        assertTrue(state.beginAttempt(next,4,token,HistoryAdapter.Offered.REMOVE));
    }
    @Test public void latchIsBoundedAndClearedOnHideOrRetire(){
        HistoryAdapter.State state=new HistoryAdapter.State();
        Object frame=state.show(1,java.util.Arrays.asList(row("a","A")));
        for(int i=0;i<HistoryAdapter.MAX_ROWS*3;i++)assertFalse(state.beginAttempt(frame,1,new Object(),HistoryAdapter.Offered.REMOVE)); // unknown targets cannot consume even a UI attempt
        assertFalse(state.beginAttempt(frame,1,new Object(),HistoryAdapter.Offered.REMOVE)); // bounded latch memory
        state.hide();
        assertFalse(state.wasAttempted(frame,1,"a",HistoryAdapter.Offered.REMOVE));
        state.retire();
        assertThrows(IllegalStateException.class,()->state.show(2,java.util.Arrays.asList(row("b","B"))));
    }
}
