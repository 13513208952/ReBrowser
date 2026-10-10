package io.github.reoutlook;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Looper;
import android.view.View;

import java.lang.reflect.Field;
import java.time.Duration;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ReBrowserLoadingBarTest {
    private ReBrowserLoadingBar bar;

    @Before
    public void setup() {
        bar = new ReBrowserLoadingBar(RuntimeEnvironment.getApplication());
        bar.layout(0, 0, 200, 4);
    }

    private void attach() {
        Robolectric.buildActivity(Activity.class).setup().get().setContentView(bar);
        shadowOf(Looper.getMainLooper()).idle();
        bar.layout(0, 0, 200, 4);
    }

    private Object state(String name) throws Exception {
        Field field = ReBrowserLoadingBar.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(bar);
    }

    @Test
    public void startsWithVisibleShortSegment() throws Exception {
        assertEquals(View.INVISIBLE, bar.getVisibility());
        bar.start("one");
        assertEquals(View.VISIBLE, bar.getVisibility());
        assertEquals(0.05f, (float) state("displayedProgress"), 0.001f);
        assertEquals(1f, bar.getAlpha(), 0f);
    }

    @Test
    public void progressNeverRegressesAndOtherTabCannotFinishIt() throws Exception {
        bar.start("one");
        bar.update("one", 70);
        bar.update("one", 20);
        bar.finish("two");
        assertEquals(70, state("targetProgress"));
        assertEquals(false, state("completed"));
        bar.finish("one");
        assertEquals(100, state("targetProgress"));
        assertEquals(true, state("completed"));
        bar.update("one", 50);
        assertEquals(100, state("targetProgress"));
    }

    @Test
    public void changingTabRestoresItsOwnProgress() throws Exception {
        bar.start("one");
        bar.update("one", 80);
        bar.bind("two", true, 30);
        assertEquals("two", state("pageId"));
        assertEquals(30, state("targetProgress"));
        bar.bind("three", false, 100);
        assertEquals(View.INVISIBLE, bar.getVisibility());
    }

    @Test
    public void hidingStopsPendingAnimations() throws Exception {
        attach();
        bar.start("one");
        bar.finish("one");
        bar.hideImmediately();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        assertEquals(View.INVISIBLE, bar.getVisibility());
        assertEquals(0f, (float) state("displayedProgress"), 0f);
        assertEquals(1f, bar.getAlpha(), 0f);
    }

    @Test
    public void nextNavigationCannotBeHiddenByPreviousCompletion() throws Exception {
        attach();
        bar.start("one");
        bar.finish("one");
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(230));
        bar.start("two");
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        assertEquals(View.VISIBLE, bar.getVisibility());
        assertEquals("two", state("pageId"));
        assertEquals(1f, bar.getAlpha(), 0f);
    }

    @Test
    public void solidPaletteAndEmptyTrackDrawCorrectly() {
        bar.setPalette(Color.MAGENTA);
        bar.start("one");
        Bitmap image = Bitmap.createBitmap(200, 4, Bitmap.Config.ARGB_8888);
        bar.draw(new Canvas(image));
        assertEquals(Color.MAGENTA, image.getPixel(4, 2));
        assertEquals(Color.TRANSPARENT, image.getPixel(100, 2));
    }

    @Test
    public void completionReachesEndAndFadesOut() throws Exception {
        attach();
        bar.start("one");
        bar.finish("one");
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        assertEquals(1f, (float) state("displayedProgress"), 0.001f);
        assertEquals(View.INVISIBLE, bar.getVisibility());
        assertEquals(0f, bar.getAlpha(), 0.001f);
    }

    @Test
    public void defaultGradientIsFixedAcrossFullWidth() {
        attach();
        bar.start("one");
        bar.update("one", 80);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(350));
        Bitmap image = Bitmap.createBitmap(200, 4, Bitmap.Config.ARGB_8888);
        bar.draw(new Canvas(image));
        int left = image.getPixel(4, 2);
        int right = image.getPixel(150, 2);
        assertTrue(Color.red(left) > Color.blue(left));
        assertTrue(Color.blue(right) > Color.red(right));
        assertEquals(Color.TRANSPARENT, image.getPixel(180, 2));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsEmptyPalette() {
        bar.setPalette();
    }
}
