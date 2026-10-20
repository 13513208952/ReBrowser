package io.github.rebrowser.system;

/**
 * Pure ADVICE matrix for foreground-service / notification prerequisites. It executes nothing:
 * no service start, no notification, no permission request. Missing native facts stay UNKNOWN and
 * can never be treated as ALLOWED. Hiding the UI is not evidence that the whole app or media work
 * has stopped.
 */
public final class ForegroundPolicy {
    public enum Lifecycle { NOT_STARTED, CREATED, STARTED, RESUMED, PAUSED, STOPPED, DESTROYED }
    public enum NotificationPermission { UNKNOWN, GRANTED, DENIED }
    public enum ServiceKind { MEDIA_PLAYBACK, DATA_SYNC, SHORT_OPERATION }
    public enum Advice { ALLOWED, BLOCKED, UNKNOWN }
    public enum Reason { NONE, APP_DESTROYED, NOTIFICATION_PERMISSION_DENIED, NOTIFICATION_PERMISSION_UNKNOWN, UNSUPPORTED_SDK, SHORT_OPERATION_NEEDS_APP_FOREGROUND, BACKGROUND_START_NOT_PROVEN, SERVICE_NOT_STARTED }

    public static final class Decision {
        private final Advice advice;private final Reason reason;
        private Decision(Advice advice,Reason reason){this.advice=advice;this.reason=reason;}
        public Advice advice(){return advice;}
        public Reason reason(){return reason;}
        /** A recommendation only; the real service/notification owner performs and observes. */
        public boolean isExecution(){return false;}
        public String label(){
            switch(advice){case ALLOWED:return "可尝试原生前台服务";case BLOCKED:return "原生前置条件不满足";default:return "前置条件未知，不启动";}
        }
    }

    /**
     * This is only startup advice: POST_NOTIFICATIONS denial is NOT a foreground-service
     * launch veto on Android 13+. The service still needs its manifest type/permissions,
     * a valid notification, and an OS-allowed foreground-start context.
     */
    public static Decision mediaPlaybackAdvice(int sdkInt,Lifecycle lifecycle,NotificationPermission permission){
        return advise(sdkInt,lifecycle,permission,ServiceKind.MEDIA_PLAYBACK,true);
    }
    public static Decision dataSyncAdvice(int sdkInt,Lifecycle lifecycle,NotificationPermission permission){
        return advise(sdkInt,lifecycle,permission,ServiceKind.DATA_SYNC,true);
    }
    public static Decision shortOperationAdvice(int sdkInt,Lifecycle lifecycle,NotificationPermission permission,boolean appCurrentlyForeground){
        return advise(sdkInt,lifecycle,permission,ServiceKind.SHORT_OPERATION,appCurrentlyForeground);
    }
    private static Decision advise(int sdkInt,Lifecycle lifecycle,NotificationPermission permission,ServiceKind kind,boolean shortOperationForeground){
        if(kind==null || lifecycle==null || permission==null)throw new IllegalArgumentException("Missing foreground facts");
        if(sdkInt<30)return new Decision(Advice.BLOCKED,Reason.UNSUPPORTED_SDK);
        if(lifecycle==Lifecycle.DESTROYED)return new Decision(Advice.BLOCKED,Reason.APP_DESTROYED);
        if(lifecycle==Lifecycle.NOT_STARTED)return new Decision(Advice.BLOCKED,Reason.SERVICE_NOT_STARTED);
        if(kind==ServiceKind.SHORT_OPERATION && !shortOperationForeground)
            return new Decision(Advice.BLOCKED,Reason.SHORT_OPERATION_NEEDS_APP_FOREGROUND);
        // A visible Activity can attempt startup. Notification permission does not decide it.
        if(lifecycle==Lifecycle.STARTED || lifecycle==Lifecycle.RESUMED)
            return new Decision(Advice.ALLOWED,Reason.NONE);
        // CREATED/PAUSED/STOPPED give no proof of the Android 12+ background exception.
        return new Decision(Advice.UNKNOWN,Reason.BACKGROUND_START_NOT_PROVEN);
    }
    private ForegroundPolicy(){}
}
