package io.github.rebrowser.outlook;

import static org.junit.Assert.*;
import org.junit.Test;

/** Observation labels only; reported login is never fresh authentication. */
public final class OutlookActivityTest {
    @Test public void labelsDistinguishReportedStateFromUnknown(){
        assertTrue(OutlookActivity.accountLabel(OutlookActivity.AccountObservation.REPORTED_SIGNED_IN).contains("非fresh认证"));
        assertEquals("账号状态未知",OutlookActivity.accountLabel(OutlookActivity.AccountObservation.UNKNOWN));
        assertThrows(IllegalArgumentException.class,()->OutlookActivity.accountLabel(null));
    }
    @Test public void officialEntriesRemainTheOnlyFixedChoices(){
        assertEquals(2,OfficialMailEntry.fixedEntries().size());
        assertEquals(OfficialMailEntry.Kind.PERSONAL,OfficialMailEntry.fixedEntries().get(0).kind());
    }
}
