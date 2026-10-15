package io.github.reoutlook;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public class ReBrowserMusicLatestTaskTest {
    @Test public void blockedWorkerKeepsOnlyLatestReplacement() throws Exception {
        ReBrowserMusicLatestTask tasks = new ReBrowserMusicLatestTask();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1), done = new CountDownLatch(1);
        AtomicInteger observed = new AtomicInteger();
        try {
            tasks.replace(() -> {
                entered.countDown();
                while (release.getCount() != 0) {
                    try { release.await(); } catch (InterruptedException ignored) { /* uninterruptible I/O model */ }
                }
            });
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            for (int i = 1; i <= 4096; i++) {
                final int value = i;
                tasks.replace(() -> { observed.set(value); done.countDown(); });
                assertTrue(tasks.queued() <= 1);
            }
            assertEquals(1, tasks.queued()); assertEquals(0, observed.get());
            release.countDown(); assertTrue(done.await(2, TimeUnit.SECONDS)); assertEquals(4096, observed.get());
        } finally { release.countDown(); tasks.close(); }
    }
    @Test public void cancelAndCloseDiscardQueuedTask() throws Exception {
        ReBrowserMusicLatestTask tasks = new ReBrowserMusicLatestTask();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger ran = new AtomicInteger();
        try {
            tasks.replace(() -> { entered.countDown(); while (release.getCount() != 0) { try { release.await(); } catch (InterruptedException ignored) {} } });
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            tasks.replace(ran::incrementAndGet); tasks.cancel(); assertEquals(0, tasks.queued());
            tasks.close(); tasks.close(); tasks.replace(ran::incrementAndGet);
            release.countDown(); assertEquals(0, ran.get());
        } finally { release.countDown(); tasks.close(); }
    }
}
