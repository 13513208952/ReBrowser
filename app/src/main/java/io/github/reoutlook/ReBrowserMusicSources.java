package io.github.reoutlook;

import android.content.Context;

/** Build-time whitelist, not arbitrary provider URLs or downloadable plugins. */
final class ReBrowserMusicSources {
    static final String NETEASE = "netease";
    private final android.content.SharedPreferences state;
    ReBrowserMusicSources(Context context) { state = context.getSharedPreferences("rb_music_sources_v1", Context.MODE_PRIVATE); }
    String selected() { return NETEASE; } // Only the first provider is implemented in this build.
    boolean select(String provider) {
        if (!BuildConfig.FULL_SUITE || !NETEASE.equals(provider)) return false;
        if (provider.equals(state.getString("selected", NETEASE))) return true;
        ReBrowserMusicActivity.stopEnabledRuntime();
        return state.edit().putString("selected", provider).commit();
    }
}
