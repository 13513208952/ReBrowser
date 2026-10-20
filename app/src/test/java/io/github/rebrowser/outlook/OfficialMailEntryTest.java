package io.github.rebrowser.outlook;

import static org.junit.Assert.*;
import org.junit.Test;

/** Fixed official entry directory; no probing or account-domain guessing. */
public final class OfficialMailEntryTest {
    @Test public void onlyTwoFixedOfficialEntries(){
        assertEquals(2,OfficialMailEntry.fixedEntries().size());
        assertEquals("https://outlook.live.com/mail/",OfficialMailEntry.require(OfficialMailEntry.Kind.PERSONAL).url());
        assertEquals("https://outlook.office.com/mail/",OfficialMailEntry.require(OfficialMailEntry.Kind.WORK_SCHOOL).url());
    }
    @Test public void recentAppearsOnlyFromAnExactOfficialChoice(){
        assertEquals(2,OfficialMailEntry.entriesWithRecent(null).size());
        java.util.List<OfficialMailEntry.Choice> withRecent=OfficialMailEntry.entriesWithRecent(OfficialMailEntry.require(OfficialMailEntry.Kind.PERSONAL));
        assertEquals(3,withRecent.size());
        assertEquals(OfficialMailEntry.Kind.RECENT_SUCCESS,withRecent.get(0).kind());
        assertEquals(OfficialMailEntry.PERSONAL_MAIL,withRecent.get(0).url());
        assertThrows(IllegalArgumentException.class,()->OfficialMailEntry.entriesWithRecent(withRecent.get(0)));
    }
    @Test public void arbitraryOrLookalikeUrlsAreRejected(){
        assertThrows(IllegalArgumentException.class,()->OfficialMailEntry.fromOfficialUrl("https://outlook.live.com/mail"));
        assertThrows(IllegalArgumentException.class,()->OfficialMailEntry.fromOfficialUrl("https://outlook.live.com.evil.example/mail/"));
        assertEquals(OfficialMailEntry.Kind.WORK_SCHOOL,OfficialMailEntry.fromOfficialUrl(OfficialMailEntry.WORK_SCHOOL_MAIL).kind());
        assertThrows(IllegalArgumentException.class,()->OfficialMailEntry.require(OfficialMailEntry.Kind.RECENT_SUCCESS));
        assertThrows(IllegalArgumentException.class,()->OfficialMailEntry.require(null));
    }
}
