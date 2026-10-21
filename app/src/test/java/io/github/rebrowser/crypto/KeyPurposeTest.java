package io.github.rebrowser.crypto;

import static org.junit.Assert.*;
import org.junit.Test;

/** Vocabulary only: no key operation can be performed through these enums. */
public final class KeyPurposeTest {
    @Test public void fixedDomainsExistWithoutDuplicates(){
        assertEquals(7,KeyPurpose.Domain.values().length);
        assertEquals(KeyPurpose.Domain.DATA_WRAP,KeyPurpose.Domain.valueOf("DATA_WRAP"));
    }
    @Test public void recordKindsHaveFixedCodesAndBudgets(){
        assertEquals(1,KeyPurpose.RecordKind.BROWSER_CATALOG.code());
        assertEquals(2,KeyPurpose.RecordKind.GECKO_CHECKPOINT.code());
        assertEquals(4L*1024*1024,KeyPurpose.RecordKind.BROWSER_CATALOG.maxBytes());
        assertEquals(2L*1024*1024,KeyPurpose.RecordKind.GECKO_CHECKPOINT.maxBytes());
        assertEquals(KeyPurpose.RecordKind.BROWSER_CATALOG,KeyPurpose.RecordKind.fromCode(1));
        assertEquals(KeyPurpose.RecordKind.BOOKMARKS,KeyPurpose.RecordKind.fromCode(3));
        assertEquals(KeyPurpose.RecordKind.HISTORY,KeyPurpose.RecordKind.fromCode(4));
        assertEquals(KeyPurpose.RecordKind.SETTINGS,KeyPurpose.RecordKind.fromCode(5));
        assertEquals(KeyPurpose.RecordKind.DOWNLOADS,KeyPurpose.RecordKind.fromCode(6));
        assertThrows(IllegalArgumentException.class,()->KeyPurpose.RecordKind.fromCode(7));
        assertThrows(IllegalArgumentException.class,()->KeyPurpose.RecordKind.fromCode(0));
    }
    @Test public void budgetCheckNeverAllocatesOrAuthorizes(){
        assertSame(KeyPurpose.RecordKind.GECKO_CHECKPOINT,KeyPurpose.requireWithin(KeyPurpose.RecordKind.GECKO_CHECKPOINT,2L*1024*1024));
        assertThrows(IllegalArgumentException.class,()->KeyPurpose.requireWithin(KeyPurpose.RecordKind.GECKO_CHECKPOINT,2L*1024*1024+1));
        assertThrows(IllegalArgumentException.class,()->KeyPurpose.requireWithin(KeyPurpose.RecordKind.BROWSER_CATALOG,-1));
        assertThrows(IllegalArgumentException.class,()->KeyPurpose.requireWithin(null,1));
    }
}
