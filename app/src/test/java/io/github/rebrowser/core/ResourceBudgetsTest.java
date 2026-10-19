package io.github.rebrowser.core;

import static org.junit.Assert.*;
import android.content.ComponentCallbacks2;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

/** Actual native counters/limits with SYNTHETIC observations; no Android service call. */
public final class ResourceBudgetsTest {
    @Test public void lowUnknownAndLowRamObservationsStayConservative(){
        for(int memory:new int[]{Integer.MIN_VALUE,-1,0,16,128,191})assertEquals(ResourceBudgets.Tier.CONSERVATIVE,ResourceBudgets.observedLimits(memory,false).tier());
        assertEquals(ResourceBudgets.Tier.CONSERVATIVE,ResourceBudgets.observedLimits(1024,true).tier());
        assertEquals(ResourceBudgets.Tier.NORMAL,ResourceBudgets.observedLimits(192,false).tier());
    }
    @Test public void immutableConservativeLimitsAreStricterWithoutKillingOwners(){
        ResourceBudgets.Limits small=ResourceBudgets.observedLimits(128,false),normal=ResourceBudgets.observedLimits(512,false);
        assertEquals(1,small.maxJobsPerNativeLane());assertEquals(4,normal.maxJobsPerNativeLane());assertEquals(1,normal.maxCompressionWorkers());assertTrue(small.rebuildableMemoryBytes()<normal.rebuildableMemoryBytes());assertTrue(small.maxDecodedArtworkPixels()<normal.maxDecodedArtworkPixels());assertTrue(small.maxRetainedRebuildableObjects()<normal.maxRetainedRebuildableObjects());assertEquals(32768,ResourceBudgets.STREAM_CHUNK_BYTES);assertEquals(65536,ResourceBudgets.MAX_CONTROL_BYTES);
    }
    @Test public void admissionAtomicallyBoundsBytesAndCount(){
        ResourceBudgets.ByteBudget b=new ResourceBudgets.ByteBudget(10,2);ResourceBudgets.Allocation a=b.tryReserve(6),other=b.tryReserve(4);assertNotNull(a);assertNotNull(other);assertNull(b.tryReserve(1));assertEquals(10,b.usage().bytes());assertEquals(2,b.usage().objects());assertTrue(a.releaseAfterOwnedAllocationFreed());assertNotNull(b.tryReserve(1));assertNull(b.tryReserve(1));assertEquals(5,b.usage().bytes());
    }
    @Test public void oversizedAndOverflowingInputsCannotConsumeCredit(){
        ResourceBudgets.ByteBudget b=new ResourceBudgets.ByteBudget(1L<<40,2);assertNull(b.tryReserve(Long.MAX_VALUE));assertNull(b.tryReserve((1L<<40)+1));assertEquals(0,b.usage().bytes());assertEquals(0,b.usage().objects());assertThrows(IllegalArgumentException.class,()->b.tryReserve(0));assertThrows(IllegalArgumentException.class,()->b.tryReserve(-1));assertThrows(IllegalArgumentException.class,()->new ResourceBudgets.ByteBudget(Long.MAX_VALUE,1));assertThrows(IllegalArgumentException.class,()->new ResourceBudgets.ByteBudget(1,4097));
    }
    @Test public void exactNativeTokenOneShotReleaseDoesNotAffectOtherBudgets(){
        ResourceBudgets.ByteBudget a=new ResourceBudgets.ByteBudget(7,1),b=new ResourceBudgets.ByteBudget(7,1);ResourceBudgets.Allocation token=a.tryReserve(7);b.tryReserve(7);assertEquals(7,token.reservedBytes());assertTrue(token.releaseAfterOwnedAllocationFreed());assertFalse(token.releaseAfterOwnedAllocationFreed());assertEquals(0,a.usage().bytes());assertEquals(7,b.usage().bytes());
    }
    @Test public void cancellationAndTimeoutDoNotAutomaticallyFreeRealAllocation(){
        ResourceBudgets.ByteBudget b=new ResourceBudgets.ByteBudget(20,1);ResourceBudgets.Allocation token=b.tryReserve(20);Cancellation task=Cancellation.create();task.requester().requestCancellation();assertNull(b.tryReserve(1));task.reporter().reportActualEnd(Cancellation.EndReason.STOP_CONFIRMED);assertNull(b.tryReserve(1));assertTrue(token.releaseAfterOwnedAllocationFreed());assertNotNull(b.tryReserve(1));
    }
    @Test public void concurrentAdmissionCannotOverdrawOrInventZeroSizeObjects()throws Exception{
        ResourceBudgets.ByteBudget b=new ResourceBudgets.ByteBudget(17,3);ExecutorService threads=Executors.newFixedThreadPool(4);List<Future<ResourceBudgets.Allocation>> attempts=new ArrayList<>();CountDownLatch start=new CountDownLatch(1);
        try{for(int i=0;i<32;i++)attempts.add(threads.submit(()->{start.await();return b.tryReserve(5);}));start.countDown();List<ResourceBudgets.Allocation> accepted=new ArrayList<>();for(Future<ResourceBudgets.Allocation> f:attempts){ResourceBudgets.Allocation a=f.get(3,TimeUnit.SECONDS);if(a!=null)accepted.add(a);}assertEquals(3,accepted.size());assertEquals(15,b.usage().bytes());assertEquals(3,b.usage().objects());for(ResourceBudgets.Allocation a:accepted)a.releaseAfterOwnedAllocationFreed();assertEquals(0,b.usage().bytes());assertEquals(0,b.usage().objects());}finally{start.countDown();threads.shutdown();assertTrue(threads.awaitTermination(3,TimeUnit.SECONDS));}
    }
    @Test public void concurrentReleaseHasSingleWinner()throws Exception{
        ResourceBudgets.ByteBudget b=new ResourceBudgets.ByteBudget(10,1);ResourceBudgets.Allocation a=b.tryReserve(10);ExecutorService threads=Executors.newFixedThreadPool(4);AtomicInteger wins=new AtomicInteger();List<Future<?>> pending=new ArrayList<>();
        try{for(int i=0;i<32;i++)pending.add(threads.submit(()->{if(a.releaseAfterOwnedAllocationFreed())wins.incrementAndGet();}));for(Future<?> f:pending)f.get(3,TimeUnit.SECONDS);assertEquals(1,wins.get());assertEquals(0,b.usage().bytes());assertEquals(0,b.usage().objects());}finally{threads.shutdown();assertTrue(threads.awaitTermination(3,TimeUnit.SECONDS));}
    }
    @Test public void trimAdviceNeverAuthorizesStoppingForegroundOrMediaOrPersistentCleanup(){
        assertFalse(ResourceBudgets.trimAdvice(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN,false).dropOnlyRebuildableMemoryCaches());
        for(int level:new int[]{ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE,ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,ComponentCallbacks2.TRIM_MEMORY_COMPLETE}){ResourceBudgets.TrimAdvice plan=ResourceBudgets.trimAdvice(level,false);assertTrue(plan.dropOnlyRebuildableMemoryCaches());assertFalse(plan.allowNewBackgroundAllocations());assertTrue(plan.preserveOwnedForegroundAndMediaWork());}
        assertTrue(ResourceBudgets.trimAdvice(0,true).dropOnlyRebuildableMemoryCaches());assertTrue(ResourceBudgets.trimAdvice(0,false).allowNewBackgroundAllocations());
    }
    @Test public void limitsIssueIndependentOwnedCountersNotGlobalSharedAuthority(){
        ResourceBudgets.Limits limits=ResourceBudgets.observedLimits(128,false);ResourceBudgets.ByteBudget first=limits.newOwnedMemoryBudget(),second=limits.newOwnedMemoryBudget();ResourceBudgets.Allocation a=first.tryReserve(limits.rebuildableMemoryBytes());assertNotNull(a);assertNull(first.tryReserve(1));assertNotNull(second.tryReserve(1));assertEquals(1,second.usage().bytes());
    }
}
