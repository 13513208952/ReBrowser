package io.github.rebrowser.ui;

import static org.junit.Assert.*;
import org.junit.Test;

/** Approval request display model; starting a transfer remains with the native owner. */
public final class DownloadsActivityD03Test {
    @Test public void approvalRequestKeepsUnknownSizeUnknown(){
        DownloadsActivity.ApprovalRequest request=new DownloadsActivity.ApprovalRequest(new Object(),"文件.zip",-1);
        assertEquals(-1,request.reportedBytes());
        assertThrows(IllegalArgumentException.class,()->new DownloadsActivity.ApprovalRequest(null,"a",1));
        assertThrows(IllegalArgumentException.class,()->new DownloadsActivity.ApprovalRequest(new Object(),"",1));
        assertThrows(IllegalArgumentException.class,()->new DownloadsActivity.ApprovalRequest(new Object(),"a",-2));
        assertThrows(IllegalArgumentException.class,()->new DownloadsActivity.ApprovalRequest(new Object(),"a",(1L<<40)+1));
    }
    @Test public void displayNameLengthIsBounded(){
        assertThrows(IllegalArgumentException.class,()->new DownloadsActivity.ApprovalRequest(new Object(),new String(new char[257]).replace('\0','n'),1));
        assertEquals("a",new DownloadsActivity.ApprovalRequest(new Object(),"a",0).displayName());
    }

    private static io.github.rebrowser.core.TaskSnapshot snapshot(io.github.rebrowser.core.TaskSnapshot.Phase phase,io.github.rebrowser.core.TaskSnapshot.Work work,io.github.rebrowser.core.TaskSnapshot.Effect effect,io.github.rebrowser.core.TaskSnapshot.Durability durability,boolean current){
        return io.github.rebrowser.core.TaskSnapshot.fromNativeEvidence(phase,work,effect,durability,io.github.rebrowser.core.TaskSnapshot.ClientWait.WAITING,false,current);
    }
    @Test public void filtersSeparateOccupiedPublishedAndPendingRows(){
        io.github.rebrowser.core.TaskSnapshot running=snapshot(io.github.rebrowser.core.TaskSnapshot.Phase.WORK_STARTED,io.github.rebrowser.core.TaskSnapshot.Work.RUNNING,io.github.rebrowser.core.TaskSnapshot.Effect.NOT_DISPATCHED,io.github.rebrowser.core.TaskSnapshot.Durability.NOT_COMMITTED,true);
        io.github.rebrowser.core.TaskSnapshot published=snapshot(io.github.rebrowser.core.TaskSnapshot.Phase.OUTCOME_OBSERVED,io.github.rebrowser.core.TaskSnapshot.Work.ENDED,io.github.rebrowser.core.TaskSnapshot.Effect.OBSERVED_SUCCESS,io.github.rebrowser.core.TaskSnapshot.Durability.COMMITTED,true);
        io.github.rebrowser.core.TaskSnapshot reserved=snapshot(io.github.rebrowser.core.TaskSnapshot.Phase.RESERVED,io.github.rebrowser.core.TaskSnapshot.Work.NOT_STARTED,io.github.rebrowser.core.TaskSnapshot.Effect.NOT_DISPATCHED,io.github.rebrowser.core.TaskSnapshot.Durability.NOT_COMMITTED,true);
        DownloadsAdapter.Row runningRow=new DownloadsAdapter.Row(new Object(),"a",100,10,running,true,false,null,Boolean.TRUE);
        DownloadsAdapter.Row publishedRow=new DownloadsAdapter.Row(new Object(),"b",100,100,published,false,true,Boolean.TRUE,Boolean.FALSE);
        DownloadsAdapter.Row reservedRow=new DownloadsAdapter.Row(new Object(),"c",-1,0,reserved,true,false);
        java.util.List<DownloadsAdapter.Row> page=java.util.Arrays.asList(runningRow,publishedRow,reservedRow);
        assertEquals(java.util.Arrays.asList(runningRow),DownloadsActivity.Filter.apply(page,DownloadsActivity.Filter.OCCUPIED));
        assertEquals(java.util.Arrays.asList(publishedRow),DownloadsActivity.Filter.apply(page,DownloadsActivity.Filter.PUBLISHED));
        assertEquals(java.util.Arrays.asList(reservedRow),DownloadsActivity.Filter.apply(page,DownloadsActivity.Filter.PENDING_APPROVAL));
        assertEquals(3,DownloadsActivity.Filter.apply(page,DownloadsActivity.Filter.ALL).size());
        assertEquals("待批准",DownloadsActivity.filterLabel(DownloadsActivity.Filter.PENDING_APPROVAL));
    }
    @Test public void pageNavAndRequestAreBoundedIntents(){
        DownloadsActivity.PageNav nav=new DownloadsActivity.PageNav(Boolean.TRUE,null);
        assertTrue(nav.available(DownloadsActivity.PageDirection.PREVIOUS));
        assertFalse(nav.available(DownloadsActivity.PageDirection.NEXT));
        DownloadsActivity.PageRequest request=new DownloadsActivity.PageRequest(DownloadsActivity.PageDirection.PREVIOUS,4);
        assertEquals(4,request.expectedRevision());
        assertFalse(request.isApplied());
        assertThrows(IllegalArgumentException.class,()->new DownloadsActivity.PageRequest(null,1));
    }
}
