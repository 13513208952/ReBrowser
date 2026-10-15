package io.github.reoutlook;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.MediaMetadata;
import android.media.Rating;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.widget.Toast;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/** No exported command surface or sticky restart. Only the music task owns this runtime. */
public final class ReBrowserMusicService extends Service implements ReBrowserMusicRuntime.Listener {
    private static final String CHANNEL = "rebrowser_music_playback_v1";
    private static final int NOTIFICATION = 163;
    private static WeakReference<ReBrowserMusicService> live = new WeakReference<>(null);
    final class LocalBinder extends Binder { ReBrowserMusicService service() { return ReBrowserMusicService.this; } }
    private final LocalBinder binder = new LocalBinder();
    private final String notificationSession = java.util.UUID.randomUUID().toString();
    private final List<Runnable> observers = new ArrayList<>();
    private ReBrowserMusicRuntime runtime;
    private MediaSession session;
    private PowerManager.WakeLock wake;
    private boolean foreground, stopped;
    private String notificationKey = "", metadataKey = "", failure = "";
    private long lastSave, preparingUntil;
    private android.graphics.Bitmap artwork;
    private String artworkKey = "";
    private final java.util.concurrent.ExecutorService images = java.util.concurrent.Executors.newSingleThreadExecutor();
    private java.util.concurrent.Future<?> imageTask;
    private ReBrowserMusicSnapshot snapshot = ReBrowserMusicSnapshot.empty();
    private android.content.BroadcastReceiver noisy;
    private int musicTask = -1, missingTaskChecks;
    private final android.os.Handler tasks = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable taskWatch = new Runnable() {
        @Override public void run() {
            if (stopped) return;
            if (musicTask >= 0) {
                try {
                    boolean exists = false;
                    for (android.app.ActivityManager.AppTask task : getSystemService(android.app.ActivityManager.class).getAppTasks())
                        if (task.getTaskInfo().taskId == musicTask) { exists = true; break; }
                    missingTaskChecks = exists ? 0 : missingTaskChecks + 1;
                    if (missingTaskChecks >= 2) { shutdown(); stopSelf(); return; }
                } catch (RuntimeException unavailable) { missingTaskChecks = 0; }
            }
            tasks.postDelayed(this, 3000);
        }
    };
    void claimTask(int taskId) { if (taskId >= 0) { musicTask = taskId; missingTaskChecks = 0; } }

    static void stopEnabledRuntime() {
        ReBrowserMusicService s = live.get();
        if (s != null) { s.shutdown(); s.stopSelf(); }
    }
    @Override public void onCreate() {
        super.onCreate(); live = new WeakReference<>(this);
        if (!BuildConfig.FULL_SUITE || !new ReBrowserMusicEntrance(this).enabled() || !ReBrowserRuntime.inspect().browserAvailable()) {
            stopped = true; stopSelf(); return;
        }
        NotificationManager notifications = getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(new NotificationChannel(CHANNEL, "音乐播放", NotificationManager.IMPORTANCE_LOW));
        session = new MediaSession(this, "ReBrowserMusic");
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setRatingType(Rating.RATING_HEART);
        session.setPlaybackToLocal(new android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA).setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build());
        session.setSessionActivity(PendingIntent.getActivity(this, 0, new Intent(this, ReBrowserMusicActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { control("resume", 0); }
            @Override public void onPause() { control("pause", 0); }
            @Override public void onSkipToPrevious() { control("previous", 0); }
            @Override public void onSkipToNext() { control("next", 0); }
            @Override public void onSeekTo(long position) { control("seek", position); }
            @Override public void onStop() { if (!stopped) { shutdown(); ReBrowserMusicActivity.stopEnabledRuntime(); stopSelf(); } }
            @Override public void onCustomAction(String action, android.os.Bundle extras) {
                if ("toggle-like".equals(action)) control("like", snapshot.liked ? 0 : 1);
            }
            @Override public void onSetRating(Rating rating) {
                if (rating != null && rating.isRated() && rating.getRatingStyle() == Rating.RATING_HEART
                        && snapshot.canLike && rating.hasHeart() != snapshot.liked) control("like", rating.hasHeart() ? 1 : 0);
            }
        });
        wake = getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ReBrowser:music");
        wake.setReferenceCounted(false);
        noisy = new android.content.BroadcastReceiver() {
            @Override public void onReceive(android.content.Context c, Intent i) {
                if (android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(i.getAction())) control("pause", 0);
            }
        };
        android.content.IntentFilter filter = new android.content.IntentFilter(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(noisy, filter, RECEIVER_NOT_EXPORTED);
        else registerReceiver(noisy, filter);
        try { runtime = new ReBrowserMusicRuntime(this, this); tasks.postDelayed(taskWatch, 3000); }
        catch (Exception | LinkageError error) { failure("音乐运行时初始化失败，未借用其他登录环境。"); }
    }
    @Override public IBinder onBind(Intent intent) { return binder; }
    @Override public int onStartCommand(Intent intent, int flags, int startId) { return START_NOT_STICKY; }
    ReBrowserMusicRuntime runtime() { return runtime; }
    String failure() { return failure; }
    void observe(Runnable observer) { if (!observers.contains(observer)) observers.add(observer); observer.run(); }
    void unobserve(Runnable observer) { observers.remove(observer); }
    private void notifyObservers() { for (Runnable r : new ArrayList<>(observers)) r.run(); }
    void control(String kind, long position) {
        if (stopped || runtime == null || !runtime.ready()) return;
        if (("like".equals(kind) && !snapshot.canLike) || ("seek".equals(kind) && !snapshot.canSeek)
                || (("previous".equals(kind) || "next".equals(kind)) && !snapshot.canSkip)) return;
        if ("resume".equals(kind) || "previous".equals(kind) || "next".equals(kind)) {
            // A media-button user action needs an FGS before WebView requests focus on API 35+.
            try {
                if (!foreground) { startForeground(NOTIFICATION, notification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK); foreground = true; }
                preparingUntil = android.os.SystemClock.elapsedRealtime() + 10_000;
                wake.acquire(15_000);
            } catch (RuntimeException restricted) { failure("系统暂不允许恢复后台播放，请返回音乐界面。"); return; }
        } else if ("pause".equals(kind)) preparingUntil = 0;
        runtime.command(kind, position, snapshot.track);
    }
    @Override public void changed(ReBrowserMusicSnapshot observed) {
        if (stopped || session == null) return;
        if (snapshot.playing && observed.buffering && !observed.playing)
            preparingUntil = android.os.SystemClock.elapsedRealtime() + 15_000;
        if (!observed.playing && !observed.buffering) preparingUntil = 0;
        snapshot = observed;
        session.setActive(snapshot.ready);
        long actions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_STOP;
        if (snapshot.canSkip) actions |= PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_SKIP_TO_NEXT;
        if (snapshot.canSeek) actions |= PlaybackState.ACTION_SEEK_TO;
        if (snapshot.canLike) actions |= PlaybackState.ACTION_SET_RATING;
        PlaybackState.Builder state = new PlaybackState.Builder().setActions(actions).setState(
                snapshot.ready ? (snapshot.playing ? PlaybackState.STATE_PLAYING : snapshot.buffering ? PlaybackState.STATE_BUFFERING : PlaybackState.STATE_PAUSED)
                        : PlaybackState.STATE_NONE, snapshot.position, snapshot.playing ? snapshot.speed : 0f);
        if (snapshot.canLike) state.addCustomAction("toggle-like", snapshot.liked ? "取消喜欢" : "喜欢",
                android.R.drawable.btn_star_big_on);
        session.setPlaybackState(state.build());
        String metadata = snapshot.track + "\n" + snapshot.title + "\n" + snapshot.artist + "\n" + snapshot.album + "\n" + snapshot.duration + snapshot.liked;
        String imageKey = snapshot.track + "\n" + snapshot.cover;
        if (!imageKey.equals(artworkKey)) {
            artworkKey = imageKey; artwork = null;
            if (imageTask != null) imageTask.cancel(true);
            if (!snapshot.cover.isEmpty()) {
                String url = snapshot.cover;
                imageTask = images.submit(() -> {
                    android.graphics.Bitmap bitmap = ReBrowserMusicArtwork.load(url);
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        if (stopped || !imageKey.equals(artworkKey)) { if (bitmap != null) bitmap.recycle(); return; }
                        artwork = bitmap; metadataKey = ""; notificationKey = ""; changed(snapshot);
                    });
                });
            }
            metadataKey = "";
        }
        if (!metadata.equals(metadataKey)) {
            metadataKey = metadata;
            session.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_MEDIA_ID, snapshot.track)
                    .putString(MediaMetadata.METADATA_KEY_TITLE, snapshot.title).putString(MediaMetadata.METADATA_KEY_ARTIST, snapshot.artist)
                    .putString(MediaMetadata.METADATA_KEY_ALBUM, snapshot.album).putLong(MediaMetadata.METADATA_KEY_DURATION, snapshot.duration)
                    .putBitmap(MediaMetadata.METADATA_KEY_ART, artwork).putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, artwork)
                    .putRating(MediaMetadata.METADATA_KEY_USER_RATING, Rating.newHeartRating(snapshot.liked)).build());
        }
        String key = metadata + snapshot.playing + snapshot.buffering + snapshot.liked + snapshot.canLike + snapshot.ready;
        try {
            if (snapshot.playing || android.os.SystemClock.elapsedRealtime() < preparingUntil) {
                Notification notification = notification();
                if (!foreground) {
                    startForeground(NOTIFICATION, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
                    foreground = true;
                } else if (!key.equals(notificationKey)) getSystemService(NotificationManager.class).notify(NOTIFICATION, notification);
                // Bounded lease renewed from live state; not proof against Doze or renderer freezing.
                wake.acquire(30_000);
            } else {
                if (wake.isHeld()) wake.release();
                if (foreground) { stopForeground(STOP_FOREGROUND_DETACH); foreground = false; }
                if (snapshot.ready && !key.equals(notificationKey)) getSystemService(NotificationManager.class).notify(NOTIFICATION, notification());
                if (!snapshot.ready) getSystemService(NotificationManager.class).cancel(NOTIFICATION);
            }
            notificationKey = key;
        } catch (RuntimeException restricted) {
            if (runtime != null) runtime.command("pause", 0, snapshot.track);
            failure("系统暂不允许开启音乐前台播放，请返回音乐界面重试。");
        }
        if (snapshot.ready && (android.os.SystemClock.elapsedRealtime() - lastSave >= 10_000 || !snapshot.playing)) {
            lastSave = android.os.SystemClock.elapsedRealtime();
            getSharedPreferences("rb_music_resume_netease_v1", MODE_PRIVATE).edit()
                    .putString("track", snapshot.track).putLong("position", snapshot.position)
                    .putLong("duration", snapshot.duration).putString("title", snapshot.title)
                    .putString("artist", snapshot.artist).putBoolean("playing", false).apply();
            // Official Profile storage owns queue/resource restoration. Never persist a media URL.
        }
    }
    private PendingIntent action(String command, int request) {
        return PendingIntent.getBroadcast(this, request, new Intent(this, ReBrowserMusicControlReceiver.class)
                .setAction(command).setData(android.net.Uri.parse("rebrowser-music-control:" + notificationSession + ":" + command))
                .putExtra("session", notificationSession), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    private Notification notification() {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, ReBrowserMusicActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_rbmusic_launcher)
                .setContentTitle(snapshot.title.isEmpty() ? "音乐" : snapshot.title).setContentText(snapshot.artist)
                .setContentIntent(open).setLargeIcon(artwork).setVisibility(Notification.VISIBILITY_PUBLIC).setOnlyAlertOnce(true)
                .setOngoing(snapshot.playing || snapshot.buffering).setCategory(Notification.CATEGORY_TRANSPORT)
                .addAction(android.R.drawable.ic_media_previous, "上一首", action("previous", 1))
                .addAction(snapshot.playing || snapshot.buffering ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                        snapshot.playing || snapshot.buffering ? "暂停" : "继续", action(snapshot.playing || snapshot.buffering ? "pause" : "resume", 2))
                .addAction(android.R.drawable.ic_media_next, "下一首", action("next", 3));
        if (snapshot.canLike) b.addAction(android.R.drawable.btn_star_big_on,
                snapshot.liked ? "取消喜欢" : "喜欢", action("like", 4));
        b.setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken()).setShowActionsInCompactView(0, 1, 2));
        return b.build();
    }
    static void notificationControl(String command, String session) {
        ReBrowserMusicService s = live.get();
        if (s != null && !s.stopped && s.notificationSession.equals(session)) s.control(command, "like".equals(command) && !s.snapshot.liked ? 1 : 0);
        // Stale notifications never start/revive a stopped service or task.
    }
    @Override public void stageChanged() { notifyObservers(); }
    @Override public void failure(String reason) {
        failure = reason;
        if (runtime != null && !runtime.ready()) changed(ReBrowserMusicSnapshot.empty());
        notifyObservers();
        Toast.makeText(this, reason, Toast.LENGTH_SHORT).show();
    }
    @Override public void onTaskRemoved(Intent root) {
        if (root != null && root.getComponent() != null) {
            String c = root.getComponent().getClassName();
            if (c.equals(getPackageName() + ".ReBrowserMusicActivity") || c.equals(getPackageName() + ".ReBrowserMusicLauncher")) {
                shutdown(); stopSelf();
            }
        }
        super.onTaskRemoved(root);
    }
    void shutdown() {
        if (stopped) return;
        stopped = true; preparingUntil = 0; tasks.removeCallbacksAndMessages(null);
        if (imageTask != null) imageTask.cancel(true); images.shutdownNow();
        if (runtime != null) { runtime.close(); runtime = null; }
        if (wake != null && wake.isHeld()) wake.release();
        if (session != null) { session.setActive(false); session.release(); session = null; }
        stopForeground(STOP_FOREGROUND_REMOVE); foreground = false;
        getSystemService(NotificationManager.class).cancel(NOTIFICATION);
        notifyObservers();
    }
    @Override public void onDestroy() {
        shutdown(); if (noisy != null) unregisterReceiver(noisy);
        if (live.get() == this) live.clear(); observers.clear(); super.onDestroy();
    }
}
