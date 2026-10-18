package io.github.rebrowser.core;

import static org.junit.Assert.*;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

/** Cancellation primitive HOST tests only; no Gecko/IPC/task ownership/drain claim. */
public final class CoreContractsTest {
    @Test public void initialStateHasNoRequestAndNoEnd() {
        Cancellation c=Cancellation.create();Cancellation.Snapshot before=c.requester().snapshot();
        assertFalse(before.cancellationRequested());assertFalse(before.workEnded());assertNull(before.endReason());
        assertSame(before,c.reporter().snapshot());
    }

    @Test public void requestIsIdempotentAndDoesNotStopWork() {
        Cancellation c=Cancellation.create();Cancellation.Snapshot before=c.requester().snapshot();
        assertEquals(Cancellation.RequestOutcome.REQUESTED,c.requester().requestCancellation());
        for(int i=0;i<1000;i++)assertEquals(Cancellation.RequestOutcome.ALREADY_REQUESTED,c.requester().requestCancellation());
        assertTrue(c.requester().snapshot().cancellationRequested());assertFalse(c.requester().snapshot().workEnded());
        assertFalse(before.cancellationRequested());assertFalse(before.workEnded());
    }

    @Test public void naturalReturnAfterRequestIsNotConfirmedCancellation() {
        Cancellation c=Cancellation.create();c.requester().requestCancellation();
        Cancellation.Snapshot waiting=c.requester().snapshot();
        assertTrue(c.reporter().reportActualEnd(Cancellation.EndReason.UNDERLYING_RETURNED));
        assertTrue(c.requester().snapshot().workEnded());assertTrue(c.requester().snapshot().cancellationRequested());
        assertEquals(Cancellation.EndReason.UNDERLYING_RETURNED,c.requester().snapshot().endReason());
        assertFalse(waiting.workEnded());assertNull(waiting.endReason());
    }

    @Test public void endFirstDoesNotInventRequestOrRewriteTerminalRecord() {
        for(Cancellation.EndReason reason:Cancellation.EndReason.values()) {
            Cancellation c=Cancellation.create();assertTrue(c.reporter().reportActualEnd(reason));
            Cancellation.Snapshot ended=c.reporter().snapshot();
            assertEquals(Cancellation.RequestOutcome.ALREADY_ENDED,c.requester().requestCancellation());
            assertFalse(c.requester().snapshot().cancellationRequested());
            for(Cancellation.EndReason later:Cancellation.EndReason.values())assertFalse(c.reporter().reportActualEnd(later));
            assertSame(ended,c.reporter().snapshot());assertEquals(reason,ended.endReason());
        }
    }

    @Test public void nullCannotBecomeActualEndEvidence() {
        Cancellation c=Cancellation.create();
        try{c.reporter().reportActualEnd(null);fail();}catch(IllegalArgumentException expected){}
        assertFalse(c.reporter().snapshot().workEnded());
    }

    @Test public void requesterInterfaceCannotReportActualEndAndThereIsNoReset() {
        for(Method method:Cancellation.Requester.class.getMethods())assertFalse(method.getName().contains("report"));
        for(Method method:Cancellation.class.getMethods())assertFalse(method.getName().toLowerCase().contains("reset"));
        assertSame(Cancellation.create().getClass(),Cancellation.class);
    }

    @Test public void jobsDoNotShareCancellationOrEndState() {
        Cancellation a=Cancellation.create(),b=Cancellation.create();a.requester().requestCancellation();
        assertFalse(b.requester().snapshot().cancellationRequested());
        a.reporter().reportActualEnd(Cancellation.EndReason.STOP_CONFIRMED);
        assertFalse(b.reporter().snapshot().workEnded());
    }

    @Test public void requestAndConflictingEndRacesAreLinearizable() throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(4);
        try {
            for(int round=0;round<256;round++) {
                Cancellation c=Cancellation.create();CountDownLatch start=new CountDownLatch(1);
                List<Future<Cancellation.RequestOutcome>> requests=new ArrayList<>();
                for(int i=0;i<2;i++)requests.add(pool.submit(()->{assertTrue(start.await(2,TimeUnit.SECONDS));return c.requester().requestCancellation();}));
                Future<Boolean> returned=pool.submit(()->{assertTrue(start.await(2,TimeUnit.SECONDS));return c.reporter().reportActualEnd(Cancellation.EndReason.UNDERLYING_RETURNED);});
                Future<Boolean> stopped=pool.submit(()->{assertTrue(start.await(2,TimeUnit.SECONDS));return c.reporter().reportActualEnd(Cancellation.EndReason.STOP_CONFIRMED);});
                start.countDown();int firstRequests=0;
                for(Future<Cancellation.RequestOutcome> f:requests)if(f.get(2,TimeUnit.SECONDS)==Cancellation.RequestOutcome.REQUESTED)firstRequests++;
                assertTrue(firstRequests<=1);assertTrue(returned.get(2,TimeUnit.SECONDS)^stopped.get(2,TimeUnit.SECONDS));
                Cancellation.Snapshot end=c.requester().snapshot();assertTrue(end.workEnded());
                assertEquals(firstRequests==1,end.cancellationRequested());
                assertEquals(Cancellation.RequestOutcome.ALREADY_ENDED,c.requester().requestCancellation());
            }
        } finally {pool.shutdownNow();assertTrue(pool.awaitTermination(3,TimeUnit.SECONDS));}
    }

    @Test public void futureCancelledAndDoneCanStillHaveActualWorkerRunning() throws Exception {
        Cancellation c=Cancellation.create();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),interrupted=new CountDownLatch(1),ended=new CountDownLatch(1);
        AtomicReference<Throwable> failure=new AtomicReference<>();ExecutorService worker=Executors.newSingleThreadExecutor();
        try {
            Future<?> future=worker.submit(()->{
                entered.countDown();
                try {
                    while(release.getCount()!=0) {
                        try {if(!release.await(3,TimeUnit.SECONDS))throw new AssertionError("bounded synthetic worker expired");}
                        catch(InterruptedException ignored){interrupted.countDown();} // deliberately keeps working
                    }
                } catch(Throwable error){failure.set(error);}
                finally {c.reporter().reportActualEnd(failure.get()==null?Cancellation.EndReason.UNDERLYING_RETURNED:Cancellation.EndReason.UNDERLYING_FAILED);ended.countDown();}
            });
            assertTrue(entered.await(2,TimeUnit.SECONDS));assertEquals(Cancellation.RequestOutcome.REQUESTED,c.requester().requestCancellation());
            assertTrue(future.cancel(true));assertTrue(future.isCancelled());assertTrue(future.isDone());
            assertTrue(interrupted.await(2,TimeUnit.SECONDS));assertFalse(c.requester().snapshot().workEnded());
            release.countDown();assertTrue(ended.await(2,TimeUnit.SECONDS));assertNull(failure.get());
            assertEquals(Cancellation.EndReason.UNDERLYING_RETURNED,c.requester().snapshot().endReason());
        } finally {release.countDown();worker.shutdownNow();assertTrue(worker.awaitTermination(3,TimeUnit.SECONDS));}
    }
}
