package io.github.reoutlook;

/** Android 16 custom media-action artwork; keep older devices' existing rating/icon path intact. */
final class ReBrowserMusicSystemIcons {
    private ReBrowserMusicSystemIcons() {}
    static int lyrics(ReBrowserMusicLyricWindow.Mode mode) {
        return switch (mode) {
            case OFF -> R.drawable.ic_rb_music_lyrics_off;
            case UNLOCKED -> R.drawable.ic_rb_music_lyrics_unlocked;
            case LOCKED -> R.drawable.ic_rb_music_lyrics_locked;
        };
    }
    static int like(int sdk, boolean liked) {
        if (sdk < 36) return android.R.drawable.btn_star_big_on;
        return liked ? R.drawable.ic_rb_music_heart_filled : R.drawable.ic_rb_music_heart_outline;
    }
}
