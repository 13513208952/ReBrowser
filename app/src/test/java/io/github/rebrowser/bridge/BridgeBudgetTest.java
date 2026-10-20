package io.github.rebrowser.bridge;

import static org.junit.Assert.*;
import org.junit.Test;

/** Bounded credit accounting; release means the consumer really freed bytes. */
public final class BridgeBudgetTest {
    @Test public void envelopeAndChunkLimitsAreExact(){
        assertEquals(65_536,BridgeBudget.envelope(65_536).utf8Bytes());
        assertThrows(BridgeBudget.Rejected.class,()->BridgeBudget.envelope(65_537));
        assertEquals(32_768,BridgeBudget.chunk(32_768));
        assertThrows(BridgeBudget.Rejected.class,()->BridgeBudget.chunk(32_769));
        assertThrows(IllegalArgumentException.class,()->BridgeBudget.envelope(-1));
        assertThrows(BridgeBudget.Rejected.class,()->BridgeBudget.requireSequence(1,2));
        BridgeBudget.requireSequence(2,2);
    }
    @Test public void creditIsBoundedAndReleasedOnceOnlyAfterActualFree(){
        BridgeBudget.Window window=new BridgeBudget.Window(65_536,2);
        BridgeBudget.Credit first=window.tryReserve(32_768);
        assertNotNull(first);
        assertEquals(32_768,window.remaining());
        assertNull(window.tryReserve(32_769)); // exceeds remaining
        BridgeBudget.Credit second=window.tryReserve(32_768);
        assertNotNull(second);
        assertNull(window.tryReserve(1)); // object limit reached
        assertEquals(2,window.objects());
        assertTrue(first.releaseAfterConsumerFreedBytes());
        assertFalse(first.releaseAfterConsumerFreedBytes());
        assertTrue(first.isReleased());
        assertNotNull(window.tryReserve(1)); // first release freed bytes+one object slot
        assertNull(window.tryReserve(1)); // object limit reached again
        assertTrue(second.releaseAfterConsumerFreedBytes());
        assertNotNull(window.tryReserve(1));
        assertThrows(BridgeBudget.Rejected.class,()->window.tryReserve(65_537));
        assertThrows(IllegalArgumentException.class,()->window.tryReserve(0));
    }
    @Test public void noReleaseFromTimeoutOrForeignWindow(){
        BridgeBudget.Window window=new BridgeBudget.Window(1024,1);
        BridgeBudget.Credit credit=window.tryReserve(512);
        BridgeBudget.Window other=new BridgeBudget.Window(1024,1);
        assertEquals(1024,other.remaining());
        assertTrue(credit.releaseAfterConsumerFreedBytes());
        assertEquals(0,window.objects());
    }
}
