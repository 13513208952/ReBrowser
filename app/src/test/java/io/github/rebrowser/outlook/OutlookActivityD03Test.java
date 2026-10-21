package io.github.rebrowser.outlook;

import static org.junit.Assert.*;
import org.junit.Test;

/** Observation labels only; reported login is never fresh authentication. */
public final class OutlookActivityD03Test {
    @Test public void labelsDistinguishReportedStateFromUnknown(){
        assertTrue(OutlookActivity.accountLabel(OutlookActivity.AccountObservation.REPORTED_SIGNED_IN).contains("非fresh认证"));
        assertEquals("账号状态未知",OutlookActivity.accountLabel(OutlookActivity.AccountObservation.UNKNOWN));
        assertThrows(IllegalArgumentException.class,()->OutlookActivity.accountLabel(null));
    }
    @Test public void officialEntriesRemainTheOnlyFixedChoices(){
        assertEquals(2,OfficialMailEntry.fixedEntries().size());
        assertEquals(OfficialMailEntry.Kind.PERSONAL,OfficialMailEntry.fixedEntries().get(0).kind());
    }

    @Test public void pageAndAccountRowsAreBoundedAndExplicit(){
        OutlookActivity.AccountRow row=new OutlookActivity.AccountRow(new Object(),"工作账号",OutlookActivity.AccountObservation.REPORTED_SIGNED_OUT);
        OutlookActivity.Page page=new OutlookActivity.Page(java.util.Arrays.asList(row),null,Boolean.FALSE,3);
        assertEquals(1,page.accounts().size());
        assertEquals(Boolean.FALSE,page.offlineOffered());
        assertThrows(IllegalArgumentException.class,()->new OutlookActivity.Page(java.util.Arrays.asList(row,row),null,null,1));
        assertThrows(IllegalArgumentException.class,()->new OutlookActivity.AccountRow(null,"x",OutlookActivity.AccountObservation.UNKNOWN));
        assertTrue(OutlookActivity.accountLabel(OutlookActivity.AccountObservation.REPORTED_SIGNED_IN).contains("非fresh认证"));
    }
}
