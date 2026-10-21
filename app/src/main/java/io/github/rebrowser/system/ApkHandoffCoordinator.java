package io.github.rebrowser.system;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.Looper;
import io.github.rebrowser.bootstrap.ComponentGate;
import io.github.rebrowser.core.TaskCoordinator;
import io.github.rebrowser.crypto.CryptoEncoding;
import io.github.rebrowser.ui.BrowserToolbar;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Native UI/admin dispatcher shared two-lane APK handoff core. Explicit actual native window
 * capture -> committed IO route/archive signer metadata/source pin -> SAME private route/window
 * on UI -> one actual explicit SYSTEM handler call. No arbitrary APK URI/path/package from IPC,
 * automatic install, fallback/chooser retry or Java return interpreted as installed/native end.
 * PackageManager archive signer metadata is NOT independently qualified APK signature validation.
 * Expected signer must come from genuinely admitted native artifact/source policy, never parsed
 * caller metadata. Real current registered-target/owner authority belongs to native dispatcher.
 * Provider/installer manifest activation and actual Android/OS qualification remain held. */
public final class ApkHandoffCoordinator {
    public enum LaunchObservation { LAUNCH_CALL_RETURNED, SUBMISSION_UNKNOWN }
    public static final class WindowTicket {
        private final ApkHandoffCoordinator issuer;private final WeakReference<Activity> window;private final BrowserToolbar.Binding binding;private final AtomicBoolean prepared=new AtomicBoolean();
        private WindowTicket(ApkHandoffCoordinator issuer,Activity window,BrowserToolbar.Binding binding){this.issuer=issuer;this.window=new WeakReference<>(window);this.binding=binding;}
    }
    public static final class Prepared {
        private final ApkHandoffCoordinator issuer;private final WindowTicket window;private final TaskCoordinator.Owner task;private final Intent intent;private final ApkStagingStore.ReadLease lease;private final String packageName;private boolean launchAttempted;
        private Prepared(ApkHandoffCoordinator issuer,WindowTicket window,TaskCoordinator.Owner task,Intent intent,ApkStagingStore.ReadLease lease,String packageName){this.lease=lease;this.issuer=issuer;this.window=window;this.task=task;this.intent=intent;this.packageName=packageName;}
        public String reportedArchivePackageName(){issuer.source.requireCurrent();return packageName;} // Archive metadata only, no trusted publisher/installed claim.
    }
    private final Application application;private final ComponentGate.DataEpochPin source;private final ApkStagingStore stages;
    public ApkHandoffCoordinator(Application actualApplication,ComponentGate actualGate,ComponentGate.BusinessPermit permit,ApkStagingStore canonicalNativeStages){if(actualApplication==null || actualGate==null || canonicalNativeStages==null)throw new IllegalArgumentException("Native APK source/stage owner required");application=actualApplication;source=actualGate.captureDataEpoch(permit);stages=canonicalNativeStages;stages.requireBindings(actualGate,permit);}
    /** Native explicit-user WINDOW binding only; no Activity Intent/Bundle may supply this token. */
    public WindowTicket captureExplicitNativeWindow(Activity actualOwnedWindow,BrowserToolbar.Binding originalWindowBinding){main();source.requireCurrent();if(actualOwnedWindow==null || actualOwnedWindow.getApplication()!=application || actualOwnedWindow.isFinishing() || actualOwnedWindow.isDestroyed() || !actualOwnedWindow.hasWindowFocus() || originalWindowBinding==null)throw new SecurityException("Native APK window unavailable");originalWindowBinding.check();source.requireCurrent();return new WindowTicket(this,actualOwnedWindow,originalWindowBinding);}
    public Prepared prepareAfterNativeAuthorizationOnIo(WindowTicket originalWindow,TaskCoordinator.Owner registeredNativeHandoffTask,ApkStagingStore.Stage actualStage,byte[] admittedNativeExpectedSignerSha256)throws IOException{
        if(Looper.myLooper()==Looper.getMainLooper())throw new IllegalStateException("APK preparation requires bounded native IO");source.requireCurrent();if(originalWindow==null || originalWindow.issuer!=this || registeredNativeHandoffTask==null || actualStage==null || admittedNativeExpectedSignerSha256==null || admittedNativeExpectedSignerSha256.length!=32 || !originalWindow.prepared.compareAndSet(false,true))throw new SecurityException("Native APK preparation unavailable or spent");byte[] expected=admittedNativeExpectedSignerSha256.clone();registeredNativeHandoffTask.requireNativeDataSource(source);registeredNativeHandoffTask.claimCommittedNativeRoute(originalWindow);registeredNativeHandoffTask.requireCurrentCommittedNativeRoute(originalWindow);PackageManager pm=application.getPackageManager();
        stages.requireOwnedStageForNativePreparation(actualStage);PackageInfo archive=pm.getPackageArchiveInfo(actualStage.nativeInspectionPath(),PackageManager.GET_SIGNING_CERTIFICATES);if(archive==null || archive.packageName==null || !archive.packageName.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+") || archive.packageName.length()>255 || archive.signingInfo==null || archive.signingInfo.hasMultipleSigners())throw new IOException("Unsupported native APK archive metadata");android.content.pm.Signature[] signers=archive.signingInfo.getApkContentsSigners();if(signers==null || signers.length!=1)throw new IOException("Native archive signer metadata unavailable");byte[] certificate=signers[0].toByteArray();if(certificate.length>65536 || !MessageDigest.isEqual(CryptoEncoding.sha256(certificate),expected))throw new SecurityException("Native APK source signer metadata mismatch");registeredNativeHandoffTask.requireCurrentCommittedNativeRoute(originalWindow);source.requireCurrent();
        ApkStagingStore.ReadLease lease=stages.spendForNativeHandoff(actualStage);Uri uri=new Uri.Builder().scheme("content").authority(application.getPackageName()+".apkstage").appendPath(lease.token).build();Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,ApkStagingProvider.MIME).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);intent.setClipData(ClipData.newRawUri("APK",uri));
        List<ResolveInfo> candidates=pm.queryIntentActivities(intent,PackageManager.MATCH_DEFAULT_ONLY|PackageManager.MATCH_SYSTEM_ONLY);if(candidates==null || candidates.size()>32)throw new IOException("Native system APK handler unavailable");ComponentName target=null;for(ResolveInfo r:candidates){ActivityInfo a=r==null?null:r.activityInfo;if(a==null || !a.enabled || !a.exported || a.applicationInfo==null || !a.applicationInfo.enabled || (a.applicationInfo.flags&(ApplicationInfo.FLAG_SYSTEM|ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))==0)continue;ComponentName component=new ComponentName(a.packageName,a.name);if(target!=null && !target.equals(component))throw new IOException("Multiple native system APK handlers require explicit native selection");target=component;}if(target==null)throw new IOException("Native system APK handler unavailable");intent.setComponent(target);registeredNativeHandoffTask.requireCurrentCommittedNativeRoute(originalWindow);source.requireCurrent();return new Prepared(this,originalWindow,registeredNativeHandoffTask,intent,lease,archive.packageName);
    }
    /** Invoke from actual native UI after prepared-result publication, not a parsed message/URI.
     * Source/route/window checks are repeated on UI; no synchronous DB/file preparation here. */
    public LaunchObservation launchOnceFromOriginalNativeWindow(Prepared prepared){main();if(prepared==null || prepared.issuer!=this || prepared.launchAttempted)throw new SecurityException("Native APK handoff unavailable or spent");prepared.launchAttempted=true;source.requireCurrent();prepared.task.requireCurrentCommittedNativeRoute(prepared.window);Activity window=prepared.window.window.get();if(window==null || window.getApplication()!=application || window.isFinishing() || window.isDestroyed() || !window.hasWindowFocus())throw new SecurityException("Original APK native window retired");prepared.window.binding.check();stages.requireUnexpiredHandoffLease(prepared.lease);source.requireCurrent();prepared.task.requireCurrentCommittedNativeRoute(prepared.window);try{window.startActivity(new Intent(prepared.intent));return LaunchObservation.LAUNCH_CALL_RETURNED;}catch(RuntimeException unknown){return LaunchObservation.SUBMISSION_UNKNOWN;}}
    private static void main(){if(Looper.myLooper()!=Looper.getMainLooper())throw new IllegalStateException("Native APK window owner required");}
}
