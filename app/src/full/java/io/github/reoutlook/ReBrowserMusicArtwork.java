package io.github.reoutlook;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Bounded public-CDN artwork read: no Cookie, redirect, arbitrary host, URL logging or disk cache. */
final class ReBrowserMusicArtwork {
    private ReBrowserMusicArtwork() {}
    interface ConnectionFactory { HttpURLConnection open(URL url) throws java.io.IOException; }
    static final class Request {
        private final ScheduledThreadPoolExecutor deadlines;
        private final ConnectionFactory connections;
        private final long budgetMillis;
        private final AtomicReference<HttpURLConnection> connection = new AtomicReference<>();
        private volatile boolean cancelled;
        private volatile ScheduledFuture<?> deadline;
        Request(ScheduledThreadPoolExecutor deadlines) {
            this(deadlines, url -> (HttpURLConnection) url.openConnection(), 20_000);
        }
        // Package-private deterministic I/O seam. The same CDN validation still precedes opening.
        Request(ScheduledThreadPoolExecutor deadlines, ConnectionFactory connections, long budgetMillis) {
            this.deadlines = deadlines; this.connections = connections; this.budgetMillis = budgetMillis;
        }
        void cancel() {
            cancelled = true;
            ScheduledFuture<?> timeout = deadline;
            if (timeout != null) timeout.cancel(false);
            HttpURLConnection active = connection.getAndSet(null);
            // Never perform potentially blocking socket teardown on the Activity/main thread.
            if (active != null) deadlines.execute(active::disconnect);
        }
        private boolean stopped() { return cancelled || Thread.currentThread().isInterrupted(); }
        android.graphics.Bitmap load(String value) {
            value = ReBrowserMusicSnapshot.artwork(value);
            if (value.isEmpty() || stopped()) return null;
            HttpURLConnection c = null;
            try {
                deadline = deadlines.schedule(this::cancel, budgetMillis, TimeUnit.MILLISECONDS);
                if (stopped()) return null;
                c = connections.open(new URL(value)); connection.set(c);
                if (stopped()) return null;
                c.setRequestProperty("Cookie", ""); c.setRequestProperty("Authorization", "");
                c.setInstanceFollowRedirects(false); c.setConnectTimeout(8000); c.setReadTimeout(8000);
                if (c.getResponseCode() != 200 || c.getContentLengthLong() > 1_048_576 || stopped()) return null;
                String type = c.getContentType();
                if (type == null || !type.toLowerCase(java.util.Locale.ROOT).startsWith("image/")) return null;
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                try (java.io.InputStream in = c.getInputStream()) {
                    byte[] block = new byte[4096]; int n;
                    while (!stopped() && (n = in.read(block)) != -1) {
                        if (stopped() || out.size() + n > 1_048_576) return null;
                        out.write(block, 0, n);
                    }
                }
                if (stopped()) return null;
                byte[] bytes = out.toByteArray();
                android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
                bounds.inJustDecodeBounds = true; android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth > 8192 || bounds.outHeight > 8192
                        || (long) bounds.outWidth * bounds.outHeight > 16_777_216 || stopped()) return null;
                android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
                while (Math.max(bounds.outWidth, bounds.outHeight) / Math.max(1, options.inSampleSize) > 512)
                    options.inSampleSize = Math.max(1, options.inSampleSize) * 2;
                android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
                if (stopped()) { if (bitmap != null) bitmap.recycle(); return null; }
                return bitmap;
            } catch (Exception unavailable) { return null; }
            finally {
                ScheduledFuture<?> timeout = deadline;
                if (timeout != null) timeout.cancel(false);
                // A cancellation has already handed its connection to the deadline worker.
                if (c != null && connection.compareAndSet(c, null)) c.disconnect();
            }
        }
    }
}
