package io.github.reoutlook;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

/** Byte-for-byte staging of an explicitly opened .1 file, then only a system installation prompt. */
final class ReBrowserRenamedApk {
    static final long MAX_BYTES = 1024L * 1024 * 1024;
    static final long SPACE_RESERVE = 16L * 1024 * 1024;
    private ReBrowserRenamedApk() {}

    static Uri source(Context context, Intent intent) throws IOException {
        if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction()) || intent.getData() == null)
            throw new IOException("请选择需要打开的 .1 安装包");
        Uri uri = intent.getData();
        if ("content".equals(uri.getScheme())) {
            String authority = uri.getAuthority();
            if (authority == null || authority.substring(authority.lastIndexOf('@')+1).startsWith(context.getPackageName()+"."))
                throw new IOException("不接受应用自身的私有数据来源");
        } else if ("file".equals(uri.getScheme())) {
            if (uri.getPath() == null || (uri.getAuthority() != null && !uri.getAuthority().isEmpty()))
                throw new IOException("无效文件地址");
            File file = new File(uri.getPath()).getCanonicalFile();
            File shared = Environment.getExternalStorageDirectory().getCanonicalFile();
            if (!file.getPath().startsWith(shared.getPath()+File.separator))
                throw new IOException("仅接受共享存储中的文件，不读取应用私有路径");
        } else throw new IOException("仅支持获得读取权限的本地文件");
        String name = uri.getLastPathSegment();
        if ("content".equals(uri.getScheme())) try (Cursor cursor = context.getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (column >= 0 && !cursor.isNull(column)) name = cursor.getString(column);
            }
        }
        if (name == null || name.length() > 1024 || !name.endsWith(".1")) throw new IOException("仅支持 .1 后缀的 APK 文件");
        return uri;
    }

    static File copy(Context context, InputStream input) throws IOException { return copy(context, input, MAX_BYTES); }

    // Do not request eviction of unrelated browser/app caches to make room for an installer.
    @SuppressLint("UsableSpace")
    static File copy(Context context, InputStream input, long limit) throws IOException {
        ReBrowserApkProvider.cleanup(context);
        File directory = ReBrowserApkProvider.directory(context);
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("无法准备安装包缓存");
        long existing = 0;
        File[] files = directory.listFiles();
        if (files != null) for (File file : files) if (file.isFile()) existing += file.length();
        if (existing >= 2 * MAX_BYTES) throw new IOException("安装中转缓存已满，请稍后重试");
        File output = new File(directory, UUID.randomUUID().toString().replace("-", "")+".apk");
        boolean complete = false;
        try (FileOutputStream writer = new FileOutputStream(output)) {
            byte[] buffer = new byte[32768]; long total = 0;
            for (int read; (read = input.read(buffer)) != -1;) {
                if (Thread.currentThread().isInterrupted()) throw new IOException("已取消");
                if (read == 0) continue;
                total += read;
                if (total > limit || total + existing > 2 * MAX_BYTES) throw new IOException("安装包超过中转大小上限（1 GiB）");
                if (directory.getUsableSpace() < SPACE_RESERVE + read) throw new IOException("剩余空间不足");
                writer.write(buffer, 0, read);
            }
            if (total == 0) throw new IOException("文件为空");
            complete = true;
        } finally { if (!complete) output.delete(); }
        return output;
    }

    static boolean isApk(Context context, File file) {
        return context.getPackageManager().getPackageArchiveInfo(file.getPath(), 0) != null;
    }

    @SuppressWarnings("deprecation")
    static Intent installerIntent(Context context, File file) throws IOException {
        Uri uri = ReBrowserApkProvider.uri(file);
        PackageManager pm = context.getPackageManager();
        for (String action : new String[]{Intent.ACTION_INSTALL_PACKAGE, Intent.ACTION_VIEW}) {
            Intent install = new Intent(action).setDataAndType(uri, ReBrowserApkProvider.APK_TYPE);
            List<ResolveInfo> candidates = pm.queryIntentActivities(install, PackageManager.MATCH_DEFAULT_ONLY | PackageManager.MATCH_SYSTEM_ONLY);
            for (ResolveInfo candidate : candidates) {
                if (!systemInstaller(candidate)) continue;
                install.setComponent(new ComponentName(candidate.activityInfo.packageName, candidate.activityInfo.name));
                install.setClipData(ClipData.newRawUri("APK", uri));
                return install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            }
        }
        throw new IOException("未找到可处理 APK 的系统安装器");
    }

    static boolean systemInstaller(ResolveInfo candidate) {
        if (candidate == null || candidate.activityInfo == null || !candidate.activityInfo.exported
                || !candidate.activityInfo.enabled || candidate.activityInfo.applicationInfo == null
                || (candidate.activityInfo.applicationInfo.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) == 0)
            return false;
        String name = candidate.activityInfo.packageName;
        // A system app store advertising APKs is not necessarily the OS confirmation installer.
        return name != null && java.util.Set.of("com.android.permissioncontroller", "com.google.android.permissioncontroller",
                "com.android.packageinstaller", "com.google.android.packageinstaller", "com.miui.packageinstaller",
                "com.miui.global.packageinstaller", "com.oplus.packageinstaller", "com.samsung.android.packageinstaller").contains(name);
    }
}
