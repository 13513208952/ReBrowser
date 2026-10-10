package io.github.reoutlook;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** Compatibility route for existing internal callers; permissions now live in a peer settings page. */
public final class ReBrowserSitePermissionsActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        startActivity(new Intent(this, ReBrowserSettingsActivity.class)
                .putExtra(ReBrowserSettings.EXTRA_SECTION, ReBrowserSettings.ISOLATION));
        finish();
    }
}
