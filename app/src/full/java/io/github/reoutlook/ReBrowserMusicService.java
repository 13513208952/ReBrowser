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
    private final ReBrowserMusicSeekDiagnostics seekDiagnostics = new ReBrowserMusicSeekDiagnostics();
    private ReBrowserMusicLyricOverlay lyricOverlay;
    private ReBrowserMusicLyrics lyrics = ReBrowserMusicLyrics.state("", "loading");
    private String lyricTrack = "";
    private long lyricGeneration, lastLyricRead;
    private boolean lyricBusy, lyricsPermissionPending;
    private MediaSession session;
    private PowerManager.WakeLock wake;
    private boolean foreground, stopped;
    private String notificationKey = "", metadataKey = "", failure = "";
    private long lastSave, preparingUntil;
    private android.graphics.Bitmap artwork;
    private String artworkKey = "";
    private final ReBrowserMusicLatestTask images = new ReBrowserMusicLatestTask();
    private final java.util.concurrent.ScheduledThreadPoolExecutor imageDeadlines = artworkDeadlines();
    private ReBrowserMusicArtwork.Request artworkRequest;
    private static java.util.concurrent.ScheduledThreadPoolExecutor artworkDeadlines() {
        java.util.concurrent.ScheduledThreadPoolExecutor executor = new java.util.concurrent.ScheduledThreadPoolExecutor(
                1, runnable -> new Thread(runnable, "ReBrowserMusicArtworkDeadline"));
        executor.setRemoveOnCancelPolicy(true);
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        return executor;
    }
    private ReBrowserMusicSnapshot snapshot = ReBrowserMusicSnapshot.empty();
    private android.content.BroadcastReceiver noisy;
    private int musicTask = -1, missingTaskChecks;
    private final android.os.Handler tasks = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable taskWatch = new Runnable() {
        @Override public void run() {
            if (stopped) return;
            if (musicTask >= 0) {
                try {
                    missingTaskChecks = currentTaskPresent() ? 0 : missingTaskChecks + 1;
                    if (missingTaskChecks >= 2) { shutdown(); stopSelf(); return; }
                } catch (RuntimeException unavailable) { missingTaskChecks = 0; }
            }
            tasks.postDelayed(this, 3000);
        }
    };
    void claimTask(int taskId) { if (taskId >= 0) { musicTask = taskId; missingTaskChecks = 0; } }
    boolean closed() { return stopped; }
    void releaseTask(int taskId) {
        if (taskId < 0 || taskId != musicTask) return;
        shutdown(); stopSelf();
    }
    private boolean currentTaskPresent() {
        if (musicTask < 0) return false;
        for (android.app.ActivityManager.AppTask task : getSystemService(android.app.ActivityManager.class).getAppTasks())
            if (task.getTaskInfo().taskId == musicTask) return true;
        return false;
    }

    private ReBrowserSubapplicationGate.Lease storageLease;
    static void stopEnabledRuntime() {
        ReBrowserMusicService s = live.get();
        if (s != null) { s.shutdown(); s.stopSelf(); }
    }
    @Override public void onCreate() {
        super.onCreate(); live = new WeakReference<>(this);
        if (!BuildConfig.FULL_SUITE || !new ReBrowserMusicEntrance(this).enabled() || !ReBrowserRuntime.inspect().browserAvailable()) {
            stopped = true; stopSelf(); return;
        }
        try { storageLease = ReBrowserSubapplicationGate.acquire(this, ReBrowserApplications.MUSIC); }
        catch (IllegalStateException cleanup) { stopped = true; stopSelf(); return; }
        NotificationManager notifications = getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(new NotificationChannel(CHANNEL, "音乐播放", NotificationManager.IMPORTANCE_LOW));
        session = new MediaSession(this, "ReBrowserMusic");
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setRatingType(Rating.RATING_HEART);
        session.setPlaybackToLocal(new android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA).setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build());
        session.setSessionActivity(PendingIntent.getActivity(this, 0, new Intent(this, ReBrowserMusicActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        lyricOverlay = new ReBrowserMusicLyricOverlay(this, () -> {
            if (!stopped) {
                lyricGeneration++; lyricBusy = false; notificationKey = ""; changed(snapshot); notifyObservers();
                Toast.makeText(this, "系统未允许继续显示桌面歌词，已关闭；位置保留。", Toast.LENGTH_LONG).show();
            }
        });
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { control("resume", 0); }
            @Override public void onPause() { control("pause", 0); }
            @Override public void onSkipToPrevious() { control("previous", 0); }
            @Override public void onSkipToNext() { control("next", 0); }
            @Override public void onSeekTo(long position) { control("seek", position); }
            @Override public void onStop() { if (!stopped) { shutdown(); ReBrowserMusicActivity.stopEnabledRuntime(); stopSelf(); } }
            @Override public void onCustomAction(String action, android.os.Bundle extras) {
                if ("toggle-like".equals(action)) control("like", snapshot.liked ? 0 : 1);
                else if ("cycle-lyrics".equals(action)) cycleDesktopLyrics();
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
                else updateDesktopLyrics();
            }
        };
        android.content.IntentFilter filter = new android.content.IntentFilter(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        filter.addAction(Intent.ACTION_SCREEN_OFF); filter.addAction(Intent.ACTION_SCREEN_ON); filter.addAction(Intent.ACTION_USER_PRESENT);
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
    boolean takeLyricsPermissionRequest() {
        if (!lyricsPermissionPending || stopped || runtime == null || !runtime.ready()) return false;
        lyricsPermissionPending = false; return true;
    }
    ReBrowserMusicLyricWindow.Mode lyricsMode() {
        return lyricOverlay == null ? ReBrowserMusicLyricWindow.Mode.OFF : lyricOverlay.state.mode();
    }
    private String lyricsActionLabel() {
        String extra = lyricsMode() == ReBrowserMusicLyricWindow.Mode.OFF && lyricOverlay != null && !lyricOverlay.allowed() ? "（需授权）" : "";
        return "桌面歌词：" + lyricsMode().label() + extra + "；点按" + lyricsMode().next().label();
    }
    void cycleDesktopLyrics() {
        if (stopped || runtime == null || !runtime.ready() || lyricOverlay == null) return;
        if (!lyricOverlay.cycle()) {
            lyricsPermissionPending = true; notifyObservers();
            Toast.makeText(this, "桌面歌词需你主动授权：请点媒体卡片回到音乐界面。", Toast.LENGTH_LONG).show();
            return;
        }
        if (lyricsMode() == ReBrowserMusicLyricWindow.Mode.OFF) { lyricGeneration++; lyricBusy = false; }
        notificationKey = ""; changed(snapshot); notifyObservers();
    }
    @Override public void lyricsInvalidated() {
        lyricGeneration++; lyricBusy = false; lastLyricRead = 0; lyricTrack = "";
        lyrics = ReBrowserMusicLyrics.state("", "loading");
        if (lyricOverlay != null) lyricOverlay.render("", false);
    }
    private void updateDesktopLyrics() {
        if (stopped || lyricOverlay == null) return;
        if (!snapshot.track.equals(lyricTrack)) {
            lyricGeneration++; lyricBusy = false; lastLyricRead = 0; lyricTrack = snapshot.track;
            lyrics = ReBrowserMusicLyrics.state(lyricTrack, "loading");
        }
        boolean ready = snapshot.ready && runtime != null && runtime.ready();
        lyricOverlay.render(lyrics.textAt(snapshot.position), ready);
        if (!ready || !lyricOverlay.showing() || lyricsMode() == ReBrowserMusicLyricWindow.Mode.OFF || lyricBusy || !lyrics.pending()) return;
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - lastLyricRead < 750) return;
        lastLyricRead = now; lyricBusy = true;
        final long generation = lyricGeneration; final String expected = lyricTrack;
        if (!runtime.readLyrics(expected, value -> {
            if (stopped || generation != lyricGeneration || !expected.equals(snapshot.track)) return;
            lyricBusy = false; lyrics = value;
            if (lyricOverlay != null) lyricOverlay.render(lyrics.textAt(snapshot.position), snapshot.ready && runtime != null && runtime.ready());
        })) lyricBusy = false;
    }
    @Override public void seekResult(String result) { seekDiagnostics.result(result); }
    @Override public void seekObserved(int completed, int failed) { seekDiagnostics.observed(completed, failed); }
    @Override public void engineObserved(boolean known, boolean paused, boolean seeking, int ready, int network, int error, boolean advancing, String play) {
        seekDiagnostics.engine(known, paused, seeking, ready, network, error, advancing, play);
    }
    void control(String kind, long position) {
        if ("seek".equals(kind)) seekDiagnostics.received();
        if (stopped || runtime == null || !runtime.ready()) {
            if ("seek".equals(kind)) seekDiagnostics.result("runtime-not-ready");
            return;
        }
        if ("lyrics".equals(kind)) { cycleDesktopLyrics(); return; }
        if ("seek".equals(kind) && !snapshot.canSeek) { seekDiagnostics.result("seek-not-supported"); return; }
        if (("like".equals(kind) && !snapshot.canLike)
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
        updateDesktopLyrics();
        session.setActive(snapshot.ready);
        long actions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_STOP;
        if (snapshot.canSkip) actions |= PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_SKIP_TO_NEXT;
        if (snapshot.canSeek) actions |= PlaybackState.ACTION_SEEK_TO;
        if (snapshot.canLike) actions |= PlaybackState.ACTION_SET_RATING;
        PlaybackState.Builder state = new PlaybackState.Builder().setActions(actions).setState(
                snapshot.ready ? (snapshot.playing ? PlaybackState.STATE_PLAYING : snapshot.buffering ? PlaybackState.STATE_BUFFERING : PlaybackState.STATE_PAUSED)
                        : PlaybackState.STATE_NONE, snapshot.position, snapshot.playing ? snapshot.speed : 0f);
        if (snapshot.canLike) state.addCustomAction("toggle-like", snapshot.liked ? "取消喜欢" : "喜欢",
                ReBrowserMusicSystemIcons.like(Build.VERSION.SDK_INT, snapshot.liked));
        if (snapshot.ready) state.addCustomAction("cycle-lyrics", lyricsActionLabel(), ReBrowserMusicSystemIcons.lyrics(lyricsMode()));
        session.setPlaybackState(state.build());
        String metadata = snapshot.track + "\n" + snapshot.title + "\n" + snapshot.artist + "\n" + snapshot.album + "\n" + snapshot.duration + snapshot.liked;
        String imageKey = snapshot.track + "\n" + snapshot.cover;
        if (!imageKey.equals(artworkKey)) {
            artworkKey = imageKey; artwork = null;
            if (artworkRequest != null) artworkRequest.cancel();
            artworkRequest = null; images.cancel();
            if (!snapshot.cover.isEmpty()) {
                String url = snapshot.cover;
                ReBrowserMusicArtwork.Request request = artworkRequest = new ReBrowserMusicArtwork.Request(imageDeadlines);
                images.replace(() -> {
                    android.graphics.Bitmap bitmap = request.load(url);
                    tasks.post(() -> {
                        if (stopped || request != artworkRequest || !imageKey.equals(artworkKey)) { if (bitmap != null) bitmap.recycle(); return; }
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
        String key = metadata + snapshot.playing + snapshot.buffering + snapshot.liked + snapshot.canLike + snapshot.ready + lyricsMode();
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
        if (snapshot.canLike) b.addAction(ReBrowserMusicSystemIcons.like(Build.VERSION.SDK_INT, snapshot.liked),
                snapshot.liked ? "取消喜欢" : "喜欢", action("like", 4));
        if (snapshot.ready) b.addAction(ReBrowserMusicSystemIcons.lyrics(lyricsMode()), lyricsActionLabel(), action("lyrics", 5));
        b.setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken()).setShowActionsInCompactView(0, 1, 2));
        return b.build();
    }
    static void notificationControl(String command, String session) {
        ReBrowserMusicService s = live.get();
        if (s != null && !s.stopped && s.notificationSession.equals(session)) s.control(command, "like".equals(command) && !s.snapshot.liked ? 1 : 0);
        // Stale notifications never start/revive a stopped service or task.
    }
    @Override public void stageChanged() { notifyObservers(); }
    @Override public void loading() { failure = ""; notifyObservers(); }
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
                // Removal of an old task can arrive after a replacement has claimed this service.
                boolean replacementPresent = false;
                try { replacementPresent = currentTaskPresent(); } catch (RuntimeException unavailable) { /* Stop safely. */ }
                if (!replacementPresent) { shutdown(); stopSelf(); }
            }
        }
        super.onTaskRemoved(root);
    }
    void shutdown() {
        if (stopped) return;
        stopped = true; preparingUntil = 0; tasks.removeCallbacksAndMessages(null);
        lyricsPermissionPending = false; lyricsInvalidated();
        if (lyricOverlay != null) { lyricOverlay.close(); lyricOverlay = null; }
        if (artworkRequest != null) artworkRequest.cancel(); artworkRequest = null;
        images.close(); imageDeadlines.shutdown();
        artwork = null;
        if (runtime != null) { runtime.close(); runtime = null; }
        if (wake != null && wake.isHeld()) wake.release();
        if (session != null) { session.setActive(false); session.release(); session = null; }
        stopForeground(STOP_FOREGROUND_REMOVE); foreground = false;
        getSystemService(NotificationManager.class).cancel(NOTIFICATION);
        if (storageLease != null) { storageLease.close(); storageLease = null; }
        notifyObservers();
    }
    @Override protected void dump(java.io.FileDescriptor fd, java.io.PrintWriter writer, String[] args) {
        // Android's DUMP-protected service path only; fixed lifecycle/overlay geometry, no task IDs/account/media/URLs.
        boolean taskPresent = false;
        try { taskPresent = currentTaskPresent(); }
        catch (RuntimeException unavailable) { /* Report presence only when established. */ }
        writer.println("music stopped=" + stopped + " runtime=" + (runtime != null)
                + " ready=" + (runtime != null && runtime.ready()) + " taskClaimed=" + (musicTask >= 0)
                + " taskPresent=" + taskPresent + " stage=" + (runtime == null ? "none" : runtime.lifecycle.stage()));
        if (lyricOverlay != null) lyricOverlay.dump(writer); // Fixed mode/geometry only, never lyric text.
        seekDiagnostics.dump(writer); // Fixed result names and counters, no position/account/song identity.
    }
    @Override public void onDestroy() {
        shutdown(); if (noisy != null) unregisterReceiver(noisy);
        if (live.get() == this) live.clear(); observers.clear(); super.onDestroy();
    }
}
