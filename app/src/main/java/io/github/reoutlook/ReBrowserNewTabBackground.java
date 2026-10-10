package io.github.reoutlook;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.ImageDecoder;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** One bounded master, decoded off the UI thread for the current window's center/top crop. */
final class ReBrowserNewTabBackground extends Drawable implements AutoCloseable {
    private static final ExecutorService DECODER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ReBrowser background");
        thread.setDaemon(true);
        return thread;
    });
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final int windowHeight;
    private Future<?> pending;
    private Bitmap bitmap;
    private boolean closed;

    ReBrowserNewTabBackground(Activity activity) {
        Point window = windowSize(activity);
        windowHeight = window.y;
        int resource = ReBrowserBackgroundAsset.resourceId();
        if (resource == 0) return;
        Handler main = new Handler(Looper.getMainLooper());
        pending = DECODER.submit(() -> {
            Bitmap decoded;
            try {
                decoded = ImageDecoder.decodeBitmap(ImageDecoder.createSource(activity.getResources(), resource),
                        (decoder, info, source) -> {
                            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                            Rect crop = crop(info.getSize().getWidth(), info.getSize().getHeight(), window.x, window.y);
                            double scale = Math.min(1d, Math.max((double) window.x / crop.width(),
                                    (double) window.y / crop.height()));
                            int w = Math.max(1, (int) Math.ceil(info.getSize().getWidth() * scale));
                            int h = Math.max(1, (int) Math.ceil(info.getSize().getHeight() * scale));
                            decoder.setTargetSize(w, h);
                            decoder.setCrop(crop(w, h, window.x, window.y));
                        });
            } catch (IOException | RuntimeException error) {
                return; // A missing/failed optional image must never stop browsing.
            }
            main.post(() -> {
                if (closed) decoded.recycle();
                else { bitmap = decoded; invalidateSelf(); }
            });
        });
    }

    static Rect crop(int sourceWidth, int sourceHeight, int targetWidth, int targetHeight) {
        if (sourceWidth <= 0 || sourceHeight <= 0 || targetWidth <= 0 || targetHeight <= 0) {
            throw new IllegalArgumentException("invalid-background-size");
        }
        double aspect = (double) targetWidth / targetHeight;
        int w = sourceWidth, h = sourceHeight;
        if ((double) sourceWidth / sourceHeight > aspect) {
            w = Math.max(1, Math.min(sourceWidth, (int) Math.round(sourceHeight * aspect)));
        } else h = Math.max(1, Math.min(sourceHeight, (int) Math.round(sourceWidth / aspect)));
        int left = (sourceWidth - w) / 2;
        return new Rect(left, 0, left + w, h);
    }

    private static Point windowSize(Activity activity) {
        if (Build.VERSION.SDK_INT >= 30) {
            Rect bounds = activity.getWindowManager().getCurrentWindowMetrics().getBounds();
            return new Point(Math.max(1, bounds.width()), Math.max(1, bounds.height()));
        }
        Point size = new Point();
        activity.getWindowManager().getDefaultDisplay().getSize(size);
        size.x = Math.max(1, size.x); size.y = Math.max(1, size.y);
        return size;
    }

    @Override public void draw(Canvas canvas) {
        if (bitmap == null || bitmap.isRecycled()) {
            canvas.drawColor(Color.rgb(33, 25, 63));
            return;
        }
        Rect bounds = getBounds();
        // Keep the image stable when adjustResize shrinks content for the keyboard.
        Rect destination = new Rect(bounds.left, bounds.top, bounds.right,
                bounds.top + Math.max(bounds.height(), windowHeight));
        canvas.drawBitmap(bitmap, null, destination, paint);
    }
    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.OPAQUE; }
    @Override public void close() {
        closed = true;
        if (pending != null) pending.cancel(false);
        // Do not recycle an already displayed bitmap while RenderThread may still reference it.
        bitmap = null;
        setCallback(null);
    }
}
