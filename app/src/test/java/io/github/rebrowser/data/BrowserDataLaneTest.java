package io.github.rebrowser.data;

import static org.junit.Assert.*;
import io.github.rebrowser.bootstrap.StartupCoordinator;
import io.github.rebrowser.browser.HistoryStore;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

/** ACTUAL production executor factory + HOST private FS/JCA/catalog single-root credit.
 * Reflection reaches only the private Java queue factory, NEVER constructs a trusted Startup,
 * source/key/Android owner. Does not qualify Handler, Android IO or lifecycle execution. */
public final class BrowserDataLaneTest {
    static ThreadPoolExecutor lane()throws Exception{Method m=StartupCoordinator.class.getDeclaredMethod("newBrowserDataExecutor");m.setAccessible(true);return (ThreadPoolExecutor)m.invoke(null);}
    static void seedHistory(BrowserMetadataStoreTest.F f)throws Exception{try(OwnedDataCatalog.View<HistoryStore.Snapshot> v=f.history.read(f.permit)){f.history.compareAndWrite(v,new HistoryStore.Snapshot(0,false,Collections.emptyList()),f.permit);}}
    @Test public void settingsAndHistoryUseTheSameActualWorkerWithoutCompetingForDecodedRoot()throws Exception{
        try(BrowserMetadataStoreTest.F f=new BrowserMetadataStoreTest.F()){
            f.seedSettings(false);seedHistory(f);ThreadPoolExecutor e=lane();CountDownLatch opened=new CountDownLatch(1),release=new CountDownLatch(1),finished=new CountDownLatch(2);AtomicReference<Throwable> failure=new AtomicReference<>();AtomicReference<Thread> first=new AtomicReference<>(),second=new AtomicReference<>();
            try{
                e.execute(()->{try(OwnedDataCatalog.View<?> view=f.settings.read(f.permit)){first.set(Thread.currentThread());assertEquals(PrivateRecordStore.Kind.CURRENT,view.kind());opened.countDown();assertTrue(release.await(5,TimeUnit.SECONDS));}catch(Throwable t){failure.compareAndSet(null,t);}finally{finished.countDown();}});
                assertTrue(opened.await(5,TimeUnit.SECONDS));e.execute(()->{try{second.set(Thread.currentThread());assertTrue(f.history.readCurrent(1,f.permit).entries().isEmpty());}catch(Throwable t){failure.compareAndSet(null,t);}finally{finished.countDown();}});
                assertEquals(1,e.getQueue().size());assertNull(second.get());release.countDown();assertTrue(finished.await(5,TimeUnit.SECONDS));assertNull(failure.get());assertSame(first.get(),second.get());assertFalse(f.records.hasOwnedIoInFlight());
            }finally{release.countDown();e.shutdown();assertTrue(e.awaitTermination(5,TimeUnit.SECONDS));}
        }
    }
    @Test public void boundedQueueDoesNotTreatSubmissionAsRunningWorkCompletion()throws Exception{
        ThreadPoolExecutor e=lane();CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1),allFinished=new CountDownLatch(65);List<Integer> order=Collections.synchronizedList(new ArrayList<>());AtomicReference<Throwable> failure=new AtomicReference<>();
        try{
            e.execute(()->{started.countDown();try{assertTrue(release.await(5,TimeUnit.SECONDS));order.add(-1);}catch(Throwable t){failure.set(t);}finally{allFinished.countDown();}});assertTrue(started.await(5,TimeUnit.SECONDS));
            // This tests the ACTUAL bounded FIFO and completion, not a fake Activity callback
            // as work-end. Actual Android window retirement is explicitly not exercised.
            for(int n=0;n<64;n++){final int index=n;e.execute(()->{order.add(index);allFinished.countDown();});}
            assertEquals(1,e.getMaximumPoolSize());assertEquals(64,e.getQueue().size());assertThrows(RejectedExecutionException.class,()->e.execute(()->fail("Rejected job ran")));assertTrue(order.isEmpty());
            release.countDown();assertTrue(allFinished.await(5,TimeUnit.SECONDS));assertNull(failure.get());assertEquals(65,order.size());assertEquals(Integer.valueOf(-1),order.get(0));for(int n=0;n<64;n++)assertEquals(Integer.valueOf(n),order.get(n+1));
        }finally{release.countDown();e.shutdown();assertTrue(e.awaitTermination(5,TimeUnit.SECONDS));}
    }
    @Test public void failedActualDecodeReleasesOnlyItsCreditBeforeNextFamilyRead()throws Exception{
        try(BrowserMetadataStoreTest.F f=new BrowserMetadataStoreTest.F()){
            seedHistory(f);try(PrivateRecordStore.ReadRecord r=f.records.read(f.ref(OwnedDataCatalog.Family.SETTINGS),f.permit)){f.records.write(r.stamp(),new byte[]{1},f.permit);}byte[] original=java.nio.file.Files.readAllBytes(f.root.resolve(f.ref(OwnedDataCatalog.Family.SETTINGS).opaqueRef()));
            ThreadPoolExecutor e=lane();CountDownLatch done=new CountDownLatch(2);AtomicReference<Throwable> failure=new AtomicReference<>();
            try{
                e.execute(()->{try{assertThrows(IllegalArgumentException.class,()->f.settings.read(f.permit));}catch(Throwable t){failure.compareAndSet(null,t);}finally{done.countDown();}});
                e.execute(()->{try{assertTrue(f.history.readCurrent(1,f.permit).entries().isEmpty());}catch(Throwable t){failure.compareAndSet(null,t);}finally{done.countDown();}});
                assertTrue(done.await(5,TimeUnit.SECONDS));assertNull(failure.get());assertArrayEquals(original,java.nio.file.Files.readAllBytes(f.root.resolve(f.ref(OwnedDataCatalog.Family.SETTINGS).opaqueRef())));assertFalse(f.records.hasOwnedIoInFlight());
            }finally{e.shutdown();assertTrue(e.awaitTermination(5,TimeUnit.SECONDS));}
        }
    }
}
