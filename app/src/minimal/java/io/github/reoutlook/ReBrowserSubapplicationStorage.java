package io.github.reoutlook;

import android.content.Context;
import org.json.JSONObject;

/** Minimal builds do not contain subapplication storage or cleanup implementations. */
final class ReBrowserSubapplicationStorage {
    private ReBrowserSubapplicationStorage() {}
    static JSONObject snapshot(Context context, String id) {
        throw new IllegalArgumentException("Subapplications unavailable in minimal suite");
    }
}
