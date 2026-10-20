package io.github.rebrowser.ui;

import static org.junit.Assert.*;
import org.junit.Test;

/** Approval request display model; starting a transfer remains with the native owner. */
public final class DownloadsActivityTest {
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
}
