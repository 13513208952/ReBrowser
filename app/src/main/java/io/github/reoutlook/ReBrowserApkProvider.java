package io.github.reoutlook;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/** Private, read-granted, single-file APK cache; never exposes browser, mail or arbitrary paths. */
public final class ReBrowserApkProvider extends ContentProvider {
    static final String AUTHORITY = "io.github.reoutlook.apk-handoff";
    static final String APK_TYPE = "application/vnd.android.package-archive";
    static final long RETENTION_MS = 24L * 60 * 60 * 1000;

    static File directory(Context context) { return new File(context.getCacheDir(), "apk-handoff"); }
    static Uri uri(File file) { return new Uri.Builder().scheme("content").authority(AUTHORITY).appendPath(file.getName()).build(); }

    static File file(Context context, Uri uri) throws FileNotFoundException {
        if (uri == null || !"content".equals(uri.getScheme()) || !AUTHORITY.equals(uri.getAuthority())
                || uri.getPathSegments().size() != 1 || uri.getQuery() != null || uri.getFragment() != null
                || !uri.getLastPathSegment().matches("[a-f0-9]{32}\\.apk")) throw new FileNotFoundException("Invalid handoff URI");
        File file = new File(directory(context), uri.getLastPathSegment());
        try {
            if (!file.getCanonicalFile().getParentFile().equals(directory(context).getCanonicalFile())
                    || !file.isFile() || System.currentTimeMillis() - file.lastModified() >= RETENTION_MS)
                throw new FileNotFoundException("APK handoff expired or unavailable");
        } catch (IOException error) { throw new FileNotFoundException("APK handoff unavailable"); }
        return file;
    }

    static void cleanup(Context context) {
        File[] files = directory(context).listFiles();
        if (files == null) return;
        long cutoff = System.currentTimeMillis() - RETENTION_MS;
        for (File file : files) if (file.isFile() && file.lastModified() <= cutoff) file.delete();
    }

    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return APK_TYPE; }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Read-only APK handoff");
        return ParcelFileDescriptor.open(file(getContext(), uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        if (selection != null || selectionArgs != null || sortOrder != null) throw new IllegalArgumentException("No query expressions");
        try {
            File file = file(getContext(), uri);
            String[] columns = projection == null ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
            if (columns.length > 2) throw new IllegalArgumentException("Unsupported projection");
            MatrixCursor cursor = new MatrixCursor(columns); Object[] values = new Object[columns.length];
            for (int i=0; i<columns.length; i++) {
                if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) values[i] = "package.apk";
                else if (OpenableColumns.SIZE.equals(columns[i])) values[i] = file.length();
                else throw new IllegalArgumentException("Unsupported column");
            }
            cursor.addRow(values); return cursor;
        } catch (FileNotFoundException error) { return null; }
    }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException("Read-only"); }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { throw new UnsupportedOperationException("Read-only"); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { throw new UnsupportedOperationException("Read-only"); }
}
