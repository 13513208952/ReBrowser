package io.github.rebrowser.outlook;

import static org.junit.Assert.*;
import org.junit.Test;

/** Plain-text metadata page; no HTML, attachment bytes or database access. */
public final class OfflineMailActivityD03Test {
    @Test public void mailRowBoundsAndAttachmentFlag(){
        OfflineMailActivity.MailRow row=new OfflineMailActivity.MailRow(new Object(),"主题","发件人","2026-01-01","预览",true);
        assertTrue(row.attachmentPresent());
        assertEquals("主题",row.subject());
        assertThrows(IllegalArgumentException.class,()->new OfflineMailActivity.MailRow(null,"s","a","d","p",false));
        assertThrows(IllegalArgumentException.class,()->new OfflineMailActivity.MailRow(new Object(),new String(new char[513]).replace('\0','x'),"a","d","p",false));
    }
    @Test public void pageIsBoundedAndTokenUnique(){
        OfflineMailActivity.MailRow row=new OfflineMailActivity.MailRow(new Object(),"s","a","d","p",false);
        OfflineMailActivity.Page page=new OfflineMailActivity.Page(java.util.Arrays.asList(row),2);
        assertThrows(UnsupportedOperationException.class,()->page.rows().clear());
        assertThrows(IllegalArgumentException.class,()->new OfflineMailActivity.Page(java.util.Arrays.asList(row,row),2));
        assertThrows(IllegalArgumentException.class,()->new OfflineMailActivity.Page(java.util.Arrays.asList(row),0));
    }

    @Test public void queryNormalisesAndBoundsLiteralAndPage(){
        OfflineMailActivity.Query query=new OfflineMailActivity.Query("  发票  ",2);
        assertEquals("发票",query.literal());
        assertEquals(2,query.pageNumber());
        assertEquals("query-page-2",query.toString()); // diagnostics never echo private query literal
        assertThrows(IllegalArgumentException.class,()->new OfflineMailActivity.Query(new String(new char[65]).replace('\0','x'),0));
        assertThrows(IllegalArgumentException.class,()->new OfflineMailActivity.Query("",-1));
        OfflineMailActivity.PageNav nav=new OfflineMailActivity.PageNav(null,Boolean.TRUE);
        assertTrue(nav.nextAvailable());
        assertNull(nav.previousAvailable());
        assertThrows(IllegalArgumentException.class,()->new OfflineMailActivity.Page(java.util.Collections.emptyList(),0));
    }
}
