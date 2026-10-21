package io.github.rebrowser.download;

import android.app.Application;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Process;
import io.github.rebrowser.bootstrap.ComponentGate;
import io.github.rebrowser.system.PlatformResultRouter;
import java.io.*;
import java.util.*;

/** Exact native ACTION_CREATE_DOCUMENT results only. No arbitrary URI/path string and no app
 * private directory target. A URI grant is not content safety, full save success or installation.
 * One writer attempt per receipt, spent BEFORE ContentResolver; unknown cannot switch route. */
public final class SaveTargetRegistry {
    public static final class Target {
        private final SaveTargetRegistry issuer;private final PlatformResultRouter.Selection selection;private boolean attempted,readIssued;private OwnedOutput ownedOutput;
        private Target(SaveTargetRegistry issuer,PlatformResultRouter.Selection selection){this.issuer=issuer;this.selection=selection;}
    }
    /** Exact selected-provider read ticket, NOT durable publication/content-safety/installation. */
    public static final class NativeSavedRead {
        private final SaveTargetRegistry issuer;private final Target target;private boolean attempted;
        private NativeSavedRead(SaveTargetRegistry issuer,Target target){this.issuer=issuer;this.target=target;}
        public synchronized Uri spendForExplicitNativeOpen(io.github.rebrowser.core.TaskCoordinator.Owner nativeTask){issuer.source.requireCurrent();if(nativeTask==null || attempted)throw denied();nativeTask.requireNativeDataSource(issuer.source);attempted=true;Uri uri=target.selection.requireCurrentUri();if(issuer.application.checkUriPermission(uri,Process.myPid(),Process.myUid(),Intent.FLAG_GRANT_READ_URI_PERMISSION)!=PackageManager.PERMISSION_GRANTED)throw denied();issuer.source.requireCurrent();return uri;}
    }
    public static final class OwnedOutput extends OutputStream {
        private final OutputStream output;private final ComponentGate.DataEpochPin source;private final long maximum;private long written;private boolean closed,failed,closeReturned;private final java.security.MessageDigest digest=sha256();private byte[] finalDigest;
        private OwnedOutput(OutputStream output,ComponentGate.DataEpochPin source,long maximum){this.output=output;this.source=source;this.maximum=maximum;}
        @Override public synchronized void write(int b)throws IOException{write(new byte[]{(byte)b},0,1);}
        @Override public synchronized void write(byte[] b,int offset,int length)throws IOException{if(b==null || offset<0 || length<0 || offset>b.length-length)throw new IndexOutOfBoundsException();if(closed || failed || length>maximum-written)throw new IOException("Selected save target unavailable or full");source.requireCurrent();try{output.write(b,offset,length);digest.update(b,offset,length);written+=length;source.requireCurrent();}catch(IOException|RuntimeException unknown){failed=true;throw unknown;}}
        @Override public synchronized void flush()throws IOException{if(closed || failed)throw new IOException("Selected save target unavailable");source.requireCurrent();try{output.flush();source.requireCurrent();}catch(IOException|RuntimeException unknown){failed=true;throw unknown;}}
        public synchronized long bytesWritten(){return written;}
        @Override public synchronized void close()throws IOException{if(closed)return;closed=true;try{output.close();finalDigest=digest.digest();closeReturned=true;}catch(IOException|RuntimeException unknown){failed=true;throw unknown;}}
        private synchronized void requireExactCopyClose(ResponseStreamWorker.CopiedStage expected){if(expected==null || !closeReturned || failed || written!=expected.byteCount() || !java.security.MessageDigest.isEqual(finalDigest,expected.originalSha256()))throw denied();} // Provider close/flush != fsync/publication/whole-work-end.
    }
    private final Application application;private final ComponentGate.DataEpochPin source;private final IdentityHashMap<PlatformResultRouter.Selection,Target> targets=new IdentityHashMap<>();
    public SaveTargetRegistry(Application nativeOwnApplication,ComponentGate.DataEpochPin nativeSource){if(nativeOwnApplication==null || nativeSource==null)throw new IllegalArgumentException("Native save target owner required");application=nativeOwnApplication;source=nativeSource;source.requireCurrent();}
    public synchronized Target registerSelected(PlatformResultRouter.Selection actualSelection){source.requireCurrent();if(actualSelection==null || !actualSelection.isSaveSelection() || targets.containsKey(actualSelection) || targets.size()>=64)throw denied();Uri uri=actualSelection.requireCurrentUri();if(application.checkUriPermission(uri,Process.myPid(),Process.myUid(),Intent.FLAG_GRANT_WRITE_URI_PERMISSION)!=PackageManager.PERMISSION_GRANTED)throw denied();Target target=new Target(this,actualSelection);targets.put(actualSelection,target);return target;}
    public synchronized OwnedOutput openOnce(Target target,long nativeMaximumBytes)throws IOException{source.requireCurrent();if(target==null || target.issuer!=this || targets.get(target.selection)!=target || target.attempted || nativeMaximumBytes<0 || nativeMaximumBytes>64L*1024*1024)throw denied();target.attempted=true;Uri uri=target.selection.requireCurrentUri();if(application.checkUriPermission(uri,Process.myPid(),Process.myUid(),Intent.FLAG_GRANT_WRITE_URI_PERMISSION)!=PackageManager.PERMISSION_GRANTED)throw denied();OutputStream output=null;try{output=application.getContentResolver().openOutputStream(uri,"w");if(output==null)throw new IOException("Selected provider returned no output");source.requireCurrent();OwnedOutput result=new OwnedOutput(output,source,nativeMaximumBytes);output=null;target.ownedOutput=result;return result;}finally{if(output!=null)output.close();}}
    public void requireBindings(ComponentGate expectedGate,ComponentGate.BusinessPermit permit){source.requireBindings(expectedGate,permit);}
    /** Only exact complete owned-stage bytes whose provider close ACTUALLY returned; no fsync/publication claim. */
    public synchronized NativeSavedRead nativeReadAfterExactCopyClose(Target target,ResponseStreamWorker.CopiedStage actualPrivateCopiedStage){source.requireCurrent();if(target==null || target.issuer!=this || targets.get(target.selection)!=target || target.readIssued || target.ownedOutput==null)throw denied();target.readIssued=true;target.ownedOutput.requireExactCopyClose(actualPrivateCopiedStage);target.selection.requireCurrentUri();return new NativeSavedRead(this,target);}
    private static java.security.MessageDigest sha256(){try{return java.security.MessageDigest.getInstance("SHA-256");}catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
    private static SecurityException denied(){return new SecurityException("Selected save target unavailable or spent");}
}
