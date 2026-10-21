package io.github.rebrowser.ui;

import static org.junit.Assert.*;
import io.github.rebrowser.core.TaskSnapshot;
import org.junit.Test;

/** Pure row/latch/indeterminate model of the download adapter; no transfer is started. */
public final class DownloadsAdapterD03Test {
    private static TaskSnapshot running(long totalKnown){
        return TaskSnapshot.fromNativeEvidence(TaskSnapshot.Phase.WORK_STARTED,TaskSnapshot.Work.RUNNING,TaskSnapshot.Effect.NOT_DISPATCHED,TaskSnapshot.Durability.NOT_COMMITTED,TaskSnapshot.ClientWait.WAITING,false,true);
    }
    private static DownloadsAdapter.Row row(Object token,TaskSnapshot snapshot,long total){
        return new DownloadsAdapter.Row(token,"f.zip",total,total<0?0:1,snapshot,true,false);
    }
    @Test public void unknownTotalShowsIndeterminateWithoutFakeCompletion(){
        DownloadsAdapter.Row unknown=row(new Object(),running(-1),-1);
        assertTrue(DownloadsAdapter.Rows.indeterminate(unknown));
        assertEquals(-1,DownloadsAdapter.Rows.shownPercent(unknown));
        assertFalse(DownloadsAdapter.Rows.statusText(unknown).contains("已发布"));
        DownloadsAdapter.Row known=row(new Object(),running(100),100);
        assertFalse(DownloadsAdapter.Rows.indeterminate(known));
    }
    @Test public void approveAndCancelAreOneShotPerFrameAndRow(){
        DownloadsAdapter.State state=new DownloadsAdapter.State();
        Object token=new Object();
        Object frame=state.show(2,java.util.Arrays.asList(row(token,running(100),100)));
        assertFalse(state.beginAttempt(frame,2,token,DownloadsAdapter.RowAction.APPROVE)); // RUNNING cannot approve RESERVED work
        assertTrue(state.beginAttempt(frame,2,token,DownloadsAdapter.RowAction.CANCEL));
        assertFalse(state.beginAttempt(frame,2,token,DownloadsAdapter.RowAction.CANCEL));
        assertFalse(state.beginAttempt(frame,3,token,DownloadsAdapter.RowAction.CANCEL));
        Object next=state.show(3,java.util.Arrays.asList(row(token,running(100),100)));
        assertTrue(state.beginAttempt(next,3,token,DownloadsAdapter.RowAction.CANCEL));
    }
    @Test public void latchClearsOnHideAndPageValidationStaysStrict(){
        DownloadsAdapter.State state=new DownloadsAdapter.State();
        Object token=new Object();
        Object frame=state.show(1,java.util.Arrays.asList(row(token,running(1),1)));
        assertFalse(state.beginAttempt(frame,1,token,DownloadsAdapter.RowAction.HANDOFF)); // RUNNING is not published/openable
        assertTrue(state.beginAttempt(frame,1,token,DownloadsAdapter.RowAction.CANCEL));
        state.hide();
        assertFalse(state.wasAttempted(frame,1,token,DownloadsAdapter.RowAction.HANDOFF));
        assertThrows(IllegalArgumentException.class,()->state.show(1,java.util.Arrays.asList(row(token,running(1),1),row(token,running(1),1))));
        assertThrows(IllegalArgumentException.class,()->state.show(0,java.util.Arrays.asList(row(new Object(),running(1),1))));
    }
}
