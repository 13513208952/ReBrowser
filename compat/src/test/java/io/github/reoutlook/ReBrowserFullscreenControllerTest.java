package io.github.reoutlook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class)
public final class ReBrowserFullscreenControllerTest {
    @Test public void documentFullscreenOccupiesWholeWindowAndRestoresBrowserChrome() {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("rebrowser_preferences_v1", Context.MODE_PRIVATE)
                .edit().clear().commit();
        ReBrowserPreferences preferences = new ReBrowserPreferences(context);

        try (ActivityController<Activity> owner = Robolectric.buildActivity(Activity.class).setup()) {
            Activity activity = owner.get();
            FrameLayout root = new FrameLayout(activity);
            FrameLayout content = new FrameLayout(activity);
            FrameLayout.LayoutParams contentParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
            contentParams.topMargin = 66;
            root.addView(content, contentParams);

            View toolbar = new View(activity);
            toolbar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            root.addView(toolbar);
            View progress = new View(activity);
            progress.setVisibility(View.INVISIBLE);
            progress.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            root.addView(progress);
            activity.setContentView(root);

            ReBrowserFullscreenController controller =
                    new ReBrowserFullscreenController(activity, preferences);
            controller.attachBrowserLayout(root, content, toolbar, progress);
            controller.setDocumentFullscreen(true);

            assertTrue(controller.isFullscreen());
            assertEquals(0, ((FrameLayout.LayoutParams) content.getLayoutParams()).topMargin);
            assertEquals(View.GONE, toolbar.getVisibility());
            assertEquals(View.GONE, progress.getVisibility());
            assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
                    toolbar.getImportantForAccessibility());
            assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
                    progress.getImportantForAccessibility());

            controller.setDocumentFullscreen(false);

            assertFalse(controller.isFullscreen());
            assertEquals(66, ((FrameLayout.LayoutParams) content.getLayoutParams()).topMargin);
            assertEquals(View.VISIBLE, toolbar.getVisibility());
            assertEquals(View.INVISIBLE, progress.getVisibility());
            assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_YES,
                    toolbar.getImportantForAccessibility());
            assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO,
                    progress.getImportantForAccessibility());
        }
    }
}
