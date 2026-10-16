package io.github.reoutlook;

import static org.junit.Assert.*;
import android.content.Context;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowSettings;

/** Native preference/permission lifetime only; no claim of real OEM touch passthrough. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=31)
public final class ReBrowserMusicLyricOverlayTest {
    @Test public void ungrantedPermissionNeverChangesModeOrCreatesWindow() {
        ShadowSettings.setCanDrawOverlays(false);
        try(var overlay=new ReBrowserMusicLyricOverlay(RuntimeEnvironment.getApplication(),()->fail("no failure callback expected"))) {
            assertFalse(overlay.allowed());assertFalse(overlay.cycle());assertFalse(overlay.showing());
            assertEquals(ReBrowserMusicLyricWindow.Mode.OFF,overlay.state.mode());
        }
    }
    @Test public void lockingClosingAndNewRuntimePreservePositionOnlyNotEnabledState() {
        ShadowSettings.setCanDrawOverlays(true);Context c=RuntimeEnvironment.getApplication();
        var overlay=new ReBrowserMusicLyricOverlay(c,()->{});assertTrue(overlay.cycle());
        var p=overlay.state.place(1200,800,560,112,true);
        p=overlay.state.drag(p,100,170,1200,800,true);overlay.cycle();overlay.cycle();overlay.close();
        try(var reopened=new ReBrowserMusicLyricOverlay(c,()->{})) {
            assertEquals(ReBrowserMusicLyricWindow.Mode.OFF,reopened.state.mode());
            assertEquals(p,reopened.state.place(1200,800,560,112,true));assertFalse(reopened.showing());
        }
    }
    @Test public void permissionGrantAloneDoesNotEnableOverlayAndCloseIsIdempotent() {
        ShadowSettings.setCanDrawOverlays(false);var overlay=new ReBrowserMusicLyricOverlay(RuntimeEnvironment.getApplication(),()->{});
        ShadowSettings.setCanDrawOverlays(true);assertEquals(ReBrowserMusicLyricWindow.Mode.OFF,overlay.state.mode());
        overlay.render("bounded fixture",false);assertFalse(overlay.showing());overlay.close();overlay.close();assertFalse(overlay.cycle());
    }
    @Test public void lockedNativeWindowIsNotTouchableFocusableOrAllowedToWakeTheScreen() {
        for(var mode:ReBrowserMusicLyricWindow.Mode.values()) {
            int flags=ReBrowserMusicLyricOverlay.windowFlags(mode);
            assertTrue((flags&android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)!=0);
            assertEquals(mode==ReBrowserMusicLyricWindow.Mode.LOCKED,(flags&android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)!=0);
            assertEquals(0,flags&android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            assertEquals(0,flags&android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
            assertEquals(0,flags&android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        }
    }
    @Test public void lockedBackgroundIsFullyTransparentWithoutBreakingTouchFlags() {
        assertEquals(0,android.graphics.Color.alpha(ReBrowserMusicLyricOverlay.background(ReBrowserMusicLyricWindow.Mode.LOCKED)));
        assertTrue(android.graphics.Color.alpha(ReBrowserMusicLyricOverlay.background(ReBrowserMusicLyricWindow.Mode.UNLOCKED))>0);
        assertTrue((ReBrowserMusicLyricOverlay.windowFlags(ReBrowserMusicLyricWindow.Mode.LOCKED)
                &android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)!=0);
        assertTrue(ReBrowserMusicLyricWindow.lockedAlpha(.8f)<=.8f);
    }
    @Test @Config(sdk=29) public void contentContainsOnlyTheLyricViewAndNoInstructionLabel() throws Exception {
        try(var overlay=new ReBrowserMusicLyricOverlay(RuntimeEnvironment.getApplication(),()->{})) {
            overlay.state.cycle(true);overlay.state.cycle(true);
            var create=ReBrowserMusicLyricOverlay.class.getDeclaredMethod("create");create.setAccessible(true);create.invoke(overlay);
            var field=ReBrowserMusicLyricOverlay.class.getDeclaredField("root");field.setAccessible(true);
            android.view.ViewGroup root=(android.view.ViewGroup)field.get(overlay);
            assertEquals(1,root.getChildCount());assertTrue(root.getChildAt(0) instanceof android.widget.TextView);
            assertEquals("",((android.widget.TextView)root.getChildAt(0)).getText().toString());
            assertEquals(0,((android.graphics.drawable.ColorDrawable)root.getBackground()).getAlpha());
        }
    }
    @Test public void corruptPreferencesCannotBecomeUnboundedWindowCoordinates() {
        Context c=RuntimeEnvironment.getApplication();c.getSharedPreferences("rb_music_lyric_position_v1",Context.MODE_PRIVATE).edit()
                .putString("x","not-a-float").putFloat("y",Float.NaN).commit();
        try(var overlay=new ReBrowserMusicLyricOverlay(c,()->{})) {
            var p=overlay.state.place(1200,800,560,112,true);assertEquals(320,p.x());assertEquals(172,p.y());
        }
    }
}
