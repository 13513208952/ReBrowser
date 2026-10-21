package io.github.rebrowser.system;

import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import io.github.rebrowser.ReBrowserApplication;
import java.io.*;
import java.util.*;

/** Sole random single-APK read route, intended exported=false + explicit URI read grant.
 * Not manifest-registered yet. Canonical native stage MUST already exist; no filesystem/key/
 * runtime startup from external IPC. URI/expiry/grant/source/owned file/key/size checks, bounded
 * opens, no directory traversal, business-data paths, writes or administrator identity by UID.
 * Existing OS descriptors may outlive URI revocation/expiry; neither proves installer work end. */
public final class ApkStagingProvider extends ContentProvider {
    static final String MIME="application/vnd.android.package-archive";
    @Override public boolean onCreate(){return true;}
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{if(!"r".equals(mode))throw new FileNotFoundException("Read-only native APK route");try{String token=validatedToken(uri);requireReadGrant(uri);return store().openGrantedNativeToken(token);}catch(IOException|RuntimeException rejected){throw new FileNotFoundException("Native APK source unavailable");}}
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order){if(selection!=null || args!=null || order!=null)throw new IllegalArgumentException("No native APK queries");String token=validatedToken(uri);requireReadGrant(uri);String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection.clone();if(columns.length<1 || columns.length>2 || new HashSet<>(Arrays.asList(columns)).size()!=columns.length)throw new IllegalArgumentException("Invalid native APK projection");for(String c:columns)if(!OpenableColumns.DISPLAY_NAME.equals(c) && !OpenableColumns.SIZE.equals(c))throw new IllegalArgumentException("Invalid native APK projection");try{ApkStagingStore.ReadLease lease=store().resolveNativeReadToken(token);Object[] values=new Object[columns.length];for(int i=0;i<columns.length;i++)values[i]=OpenableColumns.DISPLAY_NAME.equals(columns[i])?"download.apk":lease.stage.byteCount();MatrixCursor result=new MatrixCursor(columns,1);result.addRow(values);return result;}catch(IOException rejected){throw new SecurityException("Native APK source unavailable");}}
    @Override public String getType(Uri uri){validatedToken(uri);return MIME;} // Public fixed MIME, no payload/size/name/lease enumeration.
    private String validatedToken(Uri uri){if(getContext()==null || uri==null || !"content".equals(uri.getScheme()) || !(getContext().getPackageName()+".apkstage").equals(uri.getAuthority()) || uri.getQuery()!=null || uri.getFragment()!=null || uri.toString().length()>256)throw new SecurityException("Invalid native APK URI");List<String> parts=uri.getPathSegments();if(parts.size()!=1 || !parts.get(0).matches("[0-9a-f]{64}") || !("/"+parts.get(0)).equals(uri.getEncodedPath()))throw new SecurityException("Invalid native APK URI");return parts.get(0);}
    private void requireReadGrant(Uri uri){if(Binder.getCallingUid()!=android.os.Process.myUid() && getContext().checkUriPermission(uri,Binder.getCallingPid(),Binder.getCallingUid(),Intent.FLAG_GRANT_READ_URI_PERMISSION)!=PackageManager.PERMISSION_GRANTED)throw new SecurityException("Native APK URI grant required");}
    private ApkStagingStore store(){if(!(getContext().getApplicationContext() instanceof ReBrowserApplication))throw new SecurityException("Native application unavailable");return ((ReBrowserApplication)getContext().getApplicationContext()).primaryStartup().existingApkStagesForNativeProvider();}
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException("Read-only native APK provider");}
    @Override public int update(Uri uri,ContentValues values,String selection,String[] args){throw new UnsupportedOperationException("Read-only native APK provider");}
    @Override public int delete(Uri uri,String selection,String[] args){throw new UnsupportedOperationException("No native APK cleanup via URI");}
    @Override public Bundle call(String method,String arg,Bundle extras){throw new UnsupportedOperationException("No native APK commands");}
}
