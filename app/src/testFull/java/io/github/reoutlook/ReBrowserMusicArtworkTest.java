package io.github.reoutlook;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.*;

/** All connections are fake. Does not read the network or test actual Bitmap decoding. */
public class ReBrowserMusicArtworkTest {
    private static ScheduledThreadPoolExecutor deadlines() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
        executor.setRemoveOnCancelPolicy(true);
        return executor;
    }
    private static class Connection extends HttpURLConnection {
        final CountDownLatch entered = new CountDownLatch(1), disconnected = new CountDownLatch(1);
        Connection(URL url) { super(url); }
        @Override public void connect() {}
        @Override public boolean usingProxy() { return false; }
        @Override public void disconnect() { disconnected.countDown(); }
        @Override public int getResponseCode() throws IOException { return 403; }
    }
    @Test public void totalDeadlineDisconnectsBlockedReadEvenWithoutNewTrack() throws Exception {
        ScheduledThreadPoolExecutor deadlines = deadlines(); ExecutorService worker = Executors.newSingleThreadExecutor();
        Connection fake = new Connection(new URL("https://p1.music.126.net/fixture")) {
            @Override public int getResponseCode() throws IOException {
                entered.countDown();
                try { if (!disconnected.await(2, TimeUnit.SECONDS)) throw new IOException("not disconnected"); }
                catch (InterruptedException e) { throw new IOException(e); }
                throw new IOException("cancelled");
            }
        };
        try {
            ReBrowserMusicArtwork.Request request = new ReBrowserMusicArtwork.Request(deadlines, url -> fake, 100);
            Future<?> result = worker.submit(() -> request.load("https://p1.music.126.net/fixture"));
            assertTrue(fake.entered.await(1, TimeUnit.SECONDS)); assertNull(result.get(2, TimeUnit.SECONDS));
            assertEquals(0, fake.disconnected.getCount()); assertEquals(0, deadlines.getQueue().size());
        } finally { worker.shutdownNow(); deadlines.shutdownNow(); }
    }
    @Test public void invalidHostsNeverReachConnectionFactoryAndLegacyCdnUsesHttps() {
        ScheduledThreadPoolExecutor deadlines = deadlines(); AtomicInteger opened = new AtomicInteger();
        try {
            for (String url : new String[]{"https://evil.invalid/image", "http://evil.invalid/", "https://p1.music.126.net.evil.invalid/x", "https://user@p1.music.126.net/x"}) {
                ReBrowserMusicArtwork.Request request = new ReBrowserMusicArtwork.Request(deadlines, value -> { opened.incrementAndGet(); return new Connection(value); }, 2000);
                assertNull(request.load(url));
            }
            assertEquals(0, opened.get());
            ReBrowserMusicArtwork.Request request = new ReBrowserMusicArtwork.Request(deadlines, value -> {
                assertEquals("https", value.getProtocol()); assertEquals("p1.music.126.net", value.getHost());
                opened.incrementAndGet(); return new Connection(value);
            }, 2000);
            assertNull(request.load("http://p1.music.126.net/fixture")); assertEquals(1, opened.get());
            assertEquals(0, deadlines.getQueue().size());
        } finally { deadlines.shutdownNow(); }
    }
    @Test public void unknownLengthImageStillHasByteLimit() {
        ScheduledThreadPoolExecutor deadlines = deadlines(); AtomicInteger read = new AtomicInteger();
        try {
            ReBrowserMusicArtwork.Request request = new ReBrowserMusicArtwork.Request(deadlines, value -> new Connection(value) {
                @Override public int getResponseCode() { return 200; }
                @Override public long getContentLengthLong() { return -1; }
                @Override public String getContentType() { return "image/png"; }
                @Override public InputStream getInputStream() { return new InputStream() {
                    @Override public int read() { read.incrementAndGet(); return 0; }
                    @Override public int read(byte[] bytes) { read.addAndGet(bytes.length); return bytes.length; }
                }; }
            }, 2000);
            assertNull(request.load("https://p1.music.126.net/fixture")); assertEquals(1_052_672, read.get());
        } finally { deadlines.shutdownNow(); }
    }
    @Test public void cancelledRequestNeverOpensConnection() {
        ScheduledThreadPoolExecutor deadlines = deadlines();
        try {
            ReBrowserMusicArtwork.Request request = new ReBrowserMusicArtwork.Request(deadlines, value -> { throw new AssertionError("cancelled request opened connection"); }, 2000);
            request.cancel(); request.cancel(); assertNull(request.load("https://p1.music.126.net/fixture"));
            assertEquals(0, deadlines.getQueue().size());
        } finally { deadlines.shutdownNow(); }
    }
}
