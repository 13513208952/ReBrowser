package io.github.rebrowser.system;

import static org.junit.Assert.*;
import org.junit.Test;

/** Advice only: no cache is freed, no chain is cancelled and media is preserved. */
public final class MemoryPressurePolicyTest {
    @Test public void criticalPressureSuggestsOnlyRebuildableCache(){
        MemoryPressurePolicy.Advice advice=MemoryPressurePolicy.advise(false,false,false,true,false,false,false,false);
        assertTrue(advice.underPressure());
        assertEquals(MemoryPressurePolicy.Candidate.REBUILDABLE_IMAGE_CACHE,advice.reclaimCandidate());
        assertTrue(advice.preserveOwnedForegroundAndMediaWork());
        assertFalse(advice.allowNewBackgroundAllocations());
    }
    @Test public void ownedMediaSuppressesReclaimCandidate(){
        MemoryPressurePolicy.Advice advice=MemoryPressurePolicy.advise(false,false,true,false,false,false,true,false);
        assertTrue(advice.underPressure());
        assertEquals(MemoryPressurePolicy.Candidate.NONE,advice.reclaimCandidate());
        assertTrue(advice.preserveOwnedForegroundAndMediaWork());
    }
    @Test public void uiHiddenAloneIsNotPressureAndNotStopProof(){
        MemoryPressurePolicy.Advice advice=MemoryPressurePolicy.advise(false,false,false,false,false,true,false,false);
        assertFalse(advice.underPressure());
        assertTrue(advice.preserveOwnedForegroundAndMediaWork());
    }
    @Test public void releaseRequiresNoUniqueContentAndNoRunningChain(){
        assertFalse(MemoryPressurePolicy.mayRelease(MemoryPressurePolicy.Candidate.REBUILDABLE_IMAGE_CACHE,true,false));
        assertFalse(MemoryPressurePolicy.mayRelease(MemoryPressurePolicy.Candidate.REBUILDABLE_IMAGE_CACHE,false,true));
        assertTrue(MemoryPressurePolicy.mayRelease(MemoryPressurePolicy.Candidate.REBUILDABLE_IMAGE_CACHE,false,false));
        assertFalse(MemoryPressurePolicy.mayRelease(MemoryPressurePolicy.Candidate.NONE,false,false));
        assertThrows(IllegalArgumentException.class,()->MemoryPressurePolicy.mayRelease(null,false,false));
    }
}
