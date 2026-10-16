package io.github.reoutlook;

import static org.junit.Assert.*;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class) @Config(sdk=31)
public final class ReBrowserMusicServiceLifetimeTest {
    private static java.lang.reflect.Field field(Class<?> type,String name) throws Exception {
        java.lang.reflect.Field f=type.getDeclaredField(name);f.setAccessible(true);return f;
    }
    @Test public void oldActivityCannotShutDownReplacementTaskOwner() throws Exception {
        var controller=Robolectric.buildService(ReBrowserMusicService.class).create();
        try {
            ReBrowserMusicService service=controller.get();
            // Model a live service without creating a Profile/WebView in this lifecycle fixture.
            field(ReBrowserMusicService.class,"stopped").setBoolean(service,false);
            service.claimTask(100);service.claimTask(101);service.releaseTask(100);
            assertFalse(service.closed());
            service.releaseTask(101);assertTrue(service.closed());
        } finally { controller.destroy(); }
    }
    @Test public void unrelatedTaskRemovalDoesNotStopMusic() throws Exception {
        var controller=Robolectric.buildService(ReBrowserMusicService.class).create();
        try {
            ReBrowserMusicService service=controller.get();field(ReBrowserMusicService.class,"stopped").setBoolean(service,false);
            service.onTaskRemoved(new Intent().setClassName("io.github.reoutlook","io.github.reoutlook.MainActivity"));
            assertFalse(service.closed());service.shutdown();
        } finally { controller.destroy(); }
    }
    @Test public void closedBoundServiceIsRejectedBeforeClaimingReplacementTask() throws Exception {
        new ReBrowserMusicEntrance(RuntimeEnvironment.getApplication()).setEnabled(true);
        var serviceController=Robolectric.buildService(ReBrowserMusicService.class).create();
        try(var activityController=Robolectric.buildActivity(ReBrowserMusicActivity.class).setup()) {
            ReBrowserMusicService service=serviceController.get();assertTrue(service.closed());
            ReBrowserMusicActivity activity=activityController.get();
            ServiceConnection connection=(ServiceConnection)field(ReBrowserMusicActivity.class,"connection").get(activity);
            connection.onServiceConnected(new ComponentName(activity,ReBrowserMusicService.class),service.onBind(null));
            assertEquals(-1,field(ReBrowserMusicService.class,"musicTask").getInt(service));
            assertNull(field(ReBrowserMusicActivity.class,"service").get(activity));
            assertEquals(1,field(ReBrowserMusicActivity.class,"reconnectAttempts").getInt(activity));
        } finally { serviceController.destroy(); }
    }
}
