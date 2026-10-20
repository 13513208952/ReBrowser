package io.github.rebrowser.system;

import static org.junit.Assert.*;
import org.junit.Test;

/** Advisory launch matrix: notification denial cannot be mistaken for foreground-service veto. */
public final class ForegroundPolicyTest {
    @Test public void grantedPermissionWithLiveLifecycleIsOnlyAnAdvice(){
        ForegroundPolicy.Decision decision=ForegroundPolicy.mediaPlaybackAdvice(33,ForegroundPolicy.Lifecycle.RESUMED,ForegroundPolicy.NotificationPermission.GRANTED);
        assertEquals(ForegroundPolicy.Advice.ALLOWED,decision.advice());
        assertFalse(decision.isExecution());
    }
    @Test public void notificationPermissionDoesNotGateForegroundServiceStartup(){
        assertEquals(ForegroundPolicy.Advice.ALLOWED,ForegroundPolicy.dataSyncAdvice(33,ForegroundPolicy.Lifecycle.STARTED,ForegroundPolicy.NotificationPermission.UNKNOWN).advice());
        assertEquals(ForegroundPolicy.Advice.ALLOWED,ForegroundPolicy.dataSyncAdvice(33,ForegroundPolicy.Lifecycle.STARTED,ForegroundPolicy.NotificationPermission.DENIED).advice());
        assertEquals(ForegroundPolicy.Advice.ALLOWED,ForegroundPolicy.mediaPlaybackAdvice(33,ForegroundPolicy.Lifecycle.RESUMED,ForegroundPolicy.NotificationPermission.DENIED).advice());
        assertEquals(ForegroundPolicy.Advice.UNKNOWN,ForegroundPolicy.mediaPlaybackAdvice(33,ForegroundPolicy.Lifecycle.STOPPED,ForegroundPolicy.NotificationPermission.GRANTED).advice());
        assertEquals(ForegroundPolicy.Reason.BACKGROUND_START_NOT_PROVEN,ForegroundPolicy.mediaPlaybackAdvice(33,ForegroundPolicy.Lifecycle.PAUSED,ForegroundPolicy.NotificationPermission.GRANTED).reason());
    }
    @Test public void destroyedOrUnsupportedSdkBlocksRegardlessOfPermission(){
        assertEquals(ForegroundPolicy.Advice.BLOCKED,ForegroundPolicy.mediaPlaybackAdvice(29,ForegroundPolicy.Lifecycle.RESUMED,ForegroundPolicy.NotificationPermission.GRANTED).advice());
        assertEquals(ForegroundPolicy.Advice.BLOCKED,ForegroundPolicy.mediaPlaybackAdvice(33,ForegroundPolicy.Lifecycle.DESTROYED,ForegroundPolicy.NotificationPermission.GRANTED).advice());
    }
    @Test public void shortOperationRequiresForegroundAndKnownPermission(){
        assertEquals(ForegroundPolicy.Advice.BLOCKED,ForegroundPolicy.shortOperationAdvice(33,ForegroundPolicy.Lifecycle.STARTED,ForegroundPolicy.NotificationPermission.GRANTED,false).advice());
        assertEquals(ForegroundPolicy.Advice.ALLOWED,ForegroundPolicy.shortOperationAdvice(33,ForegroundPolicy.Lifecycle.RESUMED,ForegroundPolicy.NotificationPermission.UNKNOWN,true).advice());
        assertEquals(ForegroundPolicy.Advice.BLOCKED,ForegroundPolicy.mediaPlaybackAdvice(33,ForegroundPolicy.Lifecycle.NOT_STARTED,ForegroundPolicy.NotificationPermission.GRANTED).advice());
        assertThrows(IllegalArgumentException.class,()->ForegroundPolicy.mediaPlaybackAdvice(33,null,ForegroundPolicy.NotificationPermission.GRANTED));
    }
}
