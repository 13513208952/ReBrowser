package io.github.rebrowser.download;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Looper;
import io.github.rebrowser.bootstrap.ComponentGate;
import io.github.rebrowser.core.TaskCoordinator;
import io.github.rebrowser.data.NativeRecordCodec;
import io.github.rebrowser.ui.BrowserToolbar;

/** Explicit native-user OPEN of an exact selected save target after matching complete copied
 * bytes and actual provider close. Ticket is private-issued, current read grant is repeated;
 * no URI/path from IPC/download metadata, background auto-open, APK bypass or launch=end claim.
 * Provider write/close is NOT fsync/durable publication or trusted file safety. APK MIME must
 * use the separate native source/archive/system-installer pipeline. Not an administrator effect
 * handler until genuine current registered-target/Actor admission is installed. */
public final class DownloadHandoff {
    public enum Observation { CHOOSER_LAUNCH_CALL_RETURNED, SUBMISSION_UNKNOWN }
    private final Application application;private final ComponentGate.DataEpochPin source;
    public DownloadHandoff(Application actualOwnApplication,ComponentGate actualGate,ComponentGate.BusinessPermit permit){if(actualOwnApplication==null || actualGate==null)throw new IllegalArgumentException("Native file handoff owner required");application=actualOwnApplication;source=actualGate.captureDataEpoch(permit);}
    public Observation openExplicitSavedFile(TaskCoordinator.Owner actualNativeTask,Activity originalOwnedWindow,BrowserToolbar.Binding originalNativeFrame,SaveTargetRegistry.NativeSavedRead actualReadTicket,String nativeMime){
        if(Looper.myLooper()!=Looper.getMainLooper())throw new IllegalStateException("Native file open UI owner required");source.requireCurrent();if(actualNativeTask==null || actualReadTicket==null || originalOwnedWindow==null || originalOwnedWindow.getApplication()!=application || originalNativeFrame==null || originalOwnedWindow.isFinishing() || originalOwnedWindow.isDestroyed() || !originalOwnedWindow.hasWindowFocus())throw new SecurityException("Native original file-open window unavailable");NativeRecordCodec.validateText(nativeMime,128,false);if(!nativeMime.matches("[a-z0-9!#$&^_.+~-]+/[a-z0-9!#$&^_.+~-]+") || "application/vnd.android.package-archive".equals(nativeMime))throw new IllegalArgumentException("Native APK/source pipeline or supported MIME required");originalNativeFrame.check();actualNativeTask.requireNativeDataSource(source);actualNativeTask.claimCommittedNativeRoute(actualReadTicket);Uri uri=actualReadTicket.spendForExplicitNativeOpen(actualNativeTask);Intent view=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,nativeMime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);view.setClipData(ClipData.newRawUri("Saved file",uri));Intent chooser=Intent.createChooser(view,"打开已选择保存的文件");originalNativeFrame.check();source.requireCurrent();actualNativeTask.requireCurrentCommittedNativeRoute(actualReadTicket);try{originalOwnedWindow.startActivity(chooser);return Observation.CHOOSER_LAUNCH_CALL_RETURNED;}catch(RuntimeException unknown){return Observation.SUBMISSION_UNKNOWN;}
    }
}
