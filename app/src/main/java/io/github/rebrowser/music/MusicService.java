package io.github.rebrowser.music;

import android.app.Service;
import android.content.Intent;
import android.os.*;
import io.github.rebrowser.ReBrowserApplication;
import io.github.rebrowser.bootstrap.StartupCoordinator;

/** Native bound-service lifetime -> SAME canonical UI/domain owner, never a second player or
 * private Gecko runtime. Not manifest-registered or a qualified foreground playback service yet:
 * actual playback/rights/native end + mediaPlayback FGS admission/notification must close first.
 * No start-command extras, automatic play/login/page loads, sticky resurrection or fake ongoing
 * notification. Local UID check is native in-process transport scope, NOT administrator identity. */
public final class MusicService extends Service {
    public final class NativeBinder extends Binder {
        private NativeBinder(){}
        /** Native UI owner-thread only; creates lazy canonical owners but no actual Gecko/page. */
        public MusicCoordinator sharedNativeDomain(){requireLocal();if(retired || !(getApplication() instanceof ReBrowserApplication))throw new SecurityException("Native music service unavailable");StartupCoordinator startup=((ReBrowserApplication)getApplication()).primaryStartup();return startup.musicCoordinator(startup.gate().requireBusiness());}
        public boolean serviceRetired(){requireLocal();return retired;}
    }
    private final NativeBinder binder=new NativeBinder();private boolean retired;
    @Override public IBinder onBind(Intent ignored){if(retired)return null;return binder;}
    @Override public int onStartCommand(Intent ignored,int flags,int startId){stopSelf(startId);return START_NOT_STICKY;} // No external/start-intent playback command route.
    @Override public void onDestroy(){retired=true;super.onDestroy();} // Service stop does NOT destroy canonical player/focus/task owners or infer their end.
    private static void requireLocal(){if(Binder.getCallingUid()!=android.os.Process.myUid() || Looper.myLooper()!=Looper.getMainLooper())throw new SecurityException("Native local music UI owner required");}
}
