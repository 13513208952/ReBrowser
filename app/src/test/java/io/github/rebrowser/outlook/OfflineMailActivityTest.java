package io.github.rebrowser.outlook;

import static org.junit.Assert.*;
import org.junit.Test;

/** Plain-text metadata page; no HTML, attachment bytes or database access. */
public final class OfflineMailActivityTest {
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
}
