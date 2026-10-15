package io.github.reoutlook;

/** Private immutable-notification PendingIntent receiver; never starts a runtime. */
public final class ReBrowserMusicControlReceiver extends android.content.BroadcastReceiver {
    @Override public void onReceive(android.content.Context context, android.content.Intent intent) {
        if (intent != null) ReBrowserMusicService.notificationControl(intent.getAction(), intent.getStringExtra("session"));
    }
}
