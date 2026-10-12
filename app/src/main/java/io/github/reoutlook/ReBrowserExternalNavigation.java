package io.github.reoutlook;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

/** Untrusted Android web links cross a narrow boundary, never carrying administrator extras. */
final class ReBrowserExternalNavigation {
    static final String ACTION_OPEN_LINK = "io.github.reoutlook.action.OPEN_BROWSER_LINK";
    static final String EXTRA_URL = "externalHttpUrl";
    private ReBrowserExternalNavigation() {}

    static String httpUrl(String value) {
        if (value == null || value.isEmpty() || value.length() > 8192) return null;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isWhitespace(c) || Character.isISOControl(c) || c == '\\') return null;
        }
        Uri uri = Uri.parse(value);
        String scheme = uri.getScheme();
        if (!("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))
                || uri.getHost() == null || uri.getHost().isEmpty()
                || uri.getEncodedAuthority() == null || uri.getEncodedAuthority().contains("@")) return null;
        return value;
    }

    static Intent browserIntent(Context context, Intent incoming) {
        if (incoming == null || !Intent.ACTION_VIEW.equals(incoming.getAction()) || incoming.getData() == null) return null;
        String url = httpUrl(incoming.getDataString());
        if (url == null) return null;
        return new Intent(context, ReBrowserActivity.class).setAction(ACTION_OPEN_LINK)
                .putExtra(EXTRA_URL, url)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    }

    static String consume(Intent intent) {
        if (intent == null || !ACTION_OPEN_LINK.equals(intent.getAction())) return null;
        String value = intent.getStringExtra(EXTRA_URL);
        intent.setAction(null); intent.removeExtra(EXTRA_URL);
        return httpUrl(value);
    }
}
