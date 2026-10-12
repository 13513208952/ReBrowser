package io.github.reoutlook;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

/** Public browser entry, separate from the launcher and private browser/admin Activity. */
public final class ReBrowserLinkActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (state == null) {
            Intent browser = ReBrowserExternalNavigation.browserIntent(this, getIntent());
            if (browser != null) startActivity(browser);
            else Toast.makeText(this, "仅支持有效的 HTTP(S) 网页链接", Toast.LENGTH_SHORT).show();
        }
        finish();
    }
}
