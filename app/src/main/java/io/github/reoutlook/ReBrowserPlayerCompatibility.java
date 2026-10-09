package io.github.reoutlook;

import android.net.Uri;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Runtime player compatibility policy, independent of website capability grants. */
final class ReBrowserPlayerCompatibility {
    static final Set<String> YOUTUBE_ORIGINS = Set.of(
            "https://m.youtube.com", "https://www.youtube.com");
    private final boolean defaultEnabled;
    private final Map<String, Boolean> profileOverrides = new HashMap<>();

    /** Regular browsing uses the narrow patch without a separate user-facing switch. */
    ReBrowserPlayerCompatibility() {
        this(true);
    }

    /** Allows a future browsing mode to choose a different default without changing the patch. */
    ReBrowserPlayerCompatibility(boolean defaultEnabled) {
        this.defaultEnabled = defaultEnabled;
    }

    static String supportedOrigin(String url) {
        if (url == null || url.length() > 8192) return "";
        try {
            Uri uri = Uri.parse(url);
            if (uri.getUserInfo() != null) return "";
            String origin = ReBrowserSitePermissions.normalizeOrigin(url);
            return YOUTUBE_ORIGINS.contains(origin) ? origin : "";
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    boolean enabled(String profileName, String url) {
        return ReBrowserStore.isOwnedProfile(profileName) && !supportedOrigin(url).isEmpty()
                && profileOverrides.getOrDefault(profileName, defaultEnabled);
    }

    boolean setEnabled(String profileName, boolean enabled) {
        if (!ReBrowserStore.isOwnedProfile(profileName)) return false;
        if (enabled == defaultEnabled) {
            profileOverrides.remove(profileName);
        } else {
            if (!profileOverrides.containsKey(profileName)
                    && profileOverrides.size() >= ReBrowserStore.MAX_WORKSPACES) return false;
            profileOverrides.put(profileName, enabled);
        }
        return true;
    }

    Set<String> originsForProfile(String profileName) {
        if (!ReBrowserStore.isOwnedProfile(profileName)
                || !profileOverrides.getOrDefault(profileName, defaultEnabled)) return Set.of();
        return YOUTUBE_ORIGINS;
    }

    void clearForProfile(String profileName) {
        profileOverrides.remove(profileName);
    }
}
