package io.github.reoutlook;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** No settings entry, automatic install, or arbitrary browser/admin Intent forwarding. */
public final class ReBrowserRenamedApkActivity extends Activity {
    private static final ThreadPoolExecutor COPIES = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(1), task -> { Thread t = new Thread(task, "renamed-apk"); t.setDaemon(true); return t; });
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final AtomicReference<InputStream> reading = new AtomicReference<>();
    private Future<?> work;
    private volatile File prepared;
    private boolean handedOff;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) { finish(); return; }
        Toast.makeText(this, "正在将 .1 安装包交给系统安装器", Toast.LENGTH_SHORT).show();
        Intent incoming = getIntent();
        try { work = COPIES.submit(() -> prepare(incoming)); }
        catch (java.util.concurrent.RejectedExecutionException busy) {
            Toast.makeText(this, "已有安装包正在准备，请稍后重试", Toast.LENGTH_SHORT).show(); finish();
        }
    }

    private void prepare(Intent incoming) {
        File staged = null;
        try {
            Uri source = ReBrowserRenamedApk.source(this, incoming);
            try (InputStream input = getContentResolver().openInputStream(source)) {
                if (input == null) throw new IOException("无法读取所选文件");
                reading.set(input);
                if (cancelled.get()) throw new IOException("已取消");
                staged = ReBrowserRenamedApk.copy(this, input);
            } finally { reading.set(null); }
            if (cancelled.get()) throw new IOException("已取消");
            if (!ReBrowserRenamedApk.isApk(this, staged)) throw new IOException("所选 .1 文件不是有效的 APK 安装包");
            prepared = staged;
            if (cancelled.get()) throw new IOException("已取消");
            runOnUiThread(this::handoff);
        } catch (Exception error) {
            if (staged != null) staged.delete();
            if (!cancelled.get()) runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                String message = error instanceof IOException ? error.getMessage() : "无法读取或验证安装包，请检查文件读取授权";
                Toast.makeText(this, message, Toast.LENGTH_LONG).show(); finish();
            });
        }
    }

    private void handoff() {
        if (cancelled.get() || isFinishing() || isDestroyed()) return;
        try {
            startActivity(ReBrowserRenamedApk.installerIntent(this, prepared));
            handedOff = true;
        } catch (Exception error) {
            if (prepared != null) prepared.delete();
            Toast.makeText(this, "无法打开系统安装器，未执行安装", Toast.LENGTH_LONG).show();
        }
        finish();
    }

    @Override protected void onDestroy() {
        cancelled.set(true);
        if (work != null) work.cancel(true);
        InputStream input = reading.getAndSet(null);
        if (input != null) try { input.close(); } catch (IOException ignored) {}
        if (!handedOff && prepared != null) prepared.delete();
        super.onDestroy();
    }
}
