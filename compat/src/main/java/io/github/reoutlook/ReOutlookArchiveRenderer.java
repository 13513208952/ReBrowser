package io.github.reoutlook;

import android.content.Context;
import android.graphics.Color;
import android.text.Html;
import android.text.Spanned;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.regex.Pattern;

/** Native, network-free renderer for the sanitized offline archive in the Gecko build. */
final class ReOutlookArchiveRenderer {
    // Html.fromHtml does not treat email CSS/comments as browser HTML: it prints
    // some of them verbatim. Strip non-content blocks before native rendering.
    private static final Pattern NON_CONTENT = Pattern.compile(
            "<!--[\\s\\S]*?-->|<(?:head|style|script|noscript)\\b[^>]*>"
                    + "[\\s\\S]*?</(?:head|style|script|noscript)\\s*>",
            Pattern.CASE_INSENSITIVE);
    private final ScrollView view;

    ReOutlookArchiveRenderer(Context context, String bodyHtml) {
        view = new ScrollView(context);
        view.setBackgroundColor(Color.WHITE);
        TextView body = new TextView(context);
        int padding = Math.round(16 * context.getResources().getDisplayMetrics().density);
        body.setPadding(padding, padding, padding, padding);
        body.setTextColor(Color.rgb(36, 36, 36));
        body.setTextSize(15);
        body.setText(readableBody(bodyHtml));
        // Link spans remain visible but are not made clickable; the archive is offline-only.
        body.setMovementMethod(null);
        view.addView(body);
    }

    static Spanned readableBody(String bodyHtml) {
        String html = NON_CONTENT.matcher(bodyHtml == null ? "" : bodyHtml).replaceAll("");
        return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY);
    }

    View view() { return view; }
    void destroy() { view.removeAllViews(); }
}
