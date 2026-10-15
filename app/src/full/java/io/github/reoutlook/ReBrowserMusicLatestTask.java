package io.github.reoutlook;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** One running task plus at most one latest replacement, even if old I/O ignores interrupt. */
final class ReBrowserMusicLatestTask implements AutoCloseable {
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1), runnable -> new Thread(runnable, "ReBrowserMusicArtwork"));
    private FutureTask<Void> latest;
    private boolean closed;
    synchronized void replace(Runnable task) {
        if (closed) return;
        cancel();
        latest = new FutureTask<>(task, null);
        worker.execute(latest);
    }
    synchronized void cancel() {
        if (latest != null) latest.cancel(true);
        worker.getQueue().clear();
        latest = null;
    }
    synchronized int queued() { return worker.getQueue().size(); }
    @Override public synchronized void close() {
        if (closed) return;
        closed = true; cancel(); worker.shutdownNow();
    }
}
