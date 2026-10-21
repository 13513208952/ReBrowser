package io.github.rebrowser.permissions;

import android.app.Application;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Process;
import io.github.rebrowser.bootstrap.ComponentGate;
import io.github.rebrowser.system.PlatformResultRouter;
import java.io.*;
import java.util.*;

/** Read only an exact user-selected native picker receipt with CURRENT real OS URI access.
 * No raw caller URI/path, assumed persistable grant, alternate provider or automatic persistence.
 * Input/provider calls may block: close/cancel/Java return is NOT whole OS/provider quiescence. */
public final class FilePickerCoordinator {
    public static final class OwnedInput extends InputStream {
        private final InputStream input;private final ComponentGate.DataEpochPin source;private final long maximum;private long count;private boolean closed,ended,failed;
        private OwnedInput(InputStream input,ComponentGate.DataEpochPin source,long maximum){this.input=input;this.source=source;this.maximum=maximum;}
        @Override public synchronized int read()throws IOException{byte[] one=new byte[1];int n=read(one,0,1);return n<0?-1:one[0]&255;}
        @Override public synchronized int read(byte[] b,int offset,int length)throws IOException{
            if(b==null || offset<0 || length<0 || offset>b.length-length)throw new IndexOutOfBoundsException();if(length==0)return 0;if(closed || failed)throw new IOException("Selected input unavailable");if(ended)return -1;source.requireCurrent();
            int ask=(int)Math.min(Math.min((long)length,32768L),maximum-count+1); // One sentinel byte may establish oversize; it is never returned/published.
            int n;try{n=input.read(b,offset,ask);}catch(IOException|RuntimeException unknown){failed=true;throw unknown;}
            if(n< -1 || n>ask || n==0){failed=true;throw new IOException("Selected provider made invalid progress");}try{source.requireCurrent();}catch(RuntimeException stale){if(n>0)java.util.Arrays.fill(b,offset,offset+n,(byte)0);failed=true;throw stale;}if(n<0){ended=true;return -1;}if(n>maximum-count){java.util.Arrays.fill(b,offset,offset+n,(byte)0);failed=true;throw new IOException("Selected input exceeds native limit");}count+=n;return n;
        }
        public synchronized long bytesReturned(){return count;}public synchronized boolean actualEofObserved(){return ended;}
        @Override public synchronized void close()throws IOException{if(closed)return;closed=true;input.close();} // Spend before provider close, no retry on unknown.
    }
    private final Application application;private final ComponentGate.DataEpochPin source;private final Set<PlatformResultRouter.Selection> spent=Collections.newSetFromMap(new IdentityHashMap<PlatformResultRouter.Selection,Boolean>());
    public FilePickerCoordinator(Application actualOwnApplication,ComponentGate.DataEpochPin nativeSource){if(actualOwnApplication==null || nativeSource==null)throw new IllegalArgumentException("Native selected-file owner required");application=actualOwnApplication;source=nativeSource;source.requireCurrent();}
    public synchronized OwnedInput openRead(PlatformResultRouter.Selection actualSelection,long nativeMaximumBytes)throws IOException{
        source.requireCurrent();if(actualSelection==null || nativeMaximumBytes<0 || nativeMaximumBytes>64L*1024*1024 || spent.size()>=64 || !spent.add(actualSelection))throw new SecurityException("Selected file receipt unavailable or spent");
        if(actualSelection.isSaveSelection())throw new SecurityException("Save receipt is not a read selection");Uri uri=actualSelection.requireCurrentUri();if(application.checkUriPermission(uri,Process.myPid(),Process.myUid(),Intent.FLAG_GRANT_READ_URI_PERMISSION)!=PackageManager.PERMISSION_GRANTED)throw new SecurityException("Actual selected URI permission unavailable");
        InputStream opened=null;try{opened=application.getContentResolver().openInputStream(uri);if(opened==null)throw new IOException("Selected provider returned no input");source.requireCurrent();actualSelection.requireCurrentUri();OwnedInput result=new OwnedInput(opened,source,nativeMaximumBytes);opened=null;return result;}
        finally{if(opened!=null)opened.close();}
    }
}
