package io.github.reoutlook;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Downloads a long-pressed image and publishes it to the shared gallery. */
final class ReBrowserImageSaver {
    private static final String USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64; rv:149.0) Gecko/20100101 Firefox/149.0";

    private ReBrowserImageSaver() {}

    static String save(Context context, String source, String base) throws Exception {
        byte[] data = download(source, base);
        String extension = extension(source);
        String name = "ReBrowser-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
                .format(new Date()) + extension;
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
        values.put(MediaStore.Images.Media.MIME_TYPE, mime(extension));
        values.put(MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/ReBrowser");
        values.put(MediaStore.Images.Media.IS_PENDING, 1);
        ContentResolver resolver = context.getContentResolver();
        Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new Exception("gallery-insert-failed");
        try (OutputStream output = resolver.openOutputStream(uri)) {
            if (output == null) throw new Exception("gallery-open-failed");
            output.write(data);
        }
        values.clear();
        values.put(MediaStore.Images.Media.IS_PENDING, 0);
        resolver.update(uri, values, null, null);
        return name;
    }

    private static byte[] download(String source, String base) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(source).openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(20_000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        if (base != null) connection.setRequestProperty("Referer", base);
        int status = connection.getResponseCode();
        if (status != HttpURLConnection.HTTP_OK) throw new Exception("http-" + status);
        try (InputStream input = connection.getInputStream();
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[8192];
            for (int read; (read = input.read(chunk)) > 0; ) buffer.write(chunk, 0, read);
            return buffer.toByteArray();
        } finally {
            connection.disconnect();
        }
    }

    private static String extension(String source) {
        String path = Uri.parse(source).getLastPathSegment();
        if (path != null) {
            int dot = path.lastIndexOf('.');
            if (dot >= 0 && dot < path.length() - 1) {
                String value = path.substring(dot + 1).toLowerCase(Locale.ROOT);
                if (value.matches("jpe?g|png|gif|webp|bmp|heic|heif")) return "." + value;
            }
        }
        return ".jpg";
    }

    private static String mime(String extension) {
        switch (extension) {
            case ".png": return "image/png";
            case ".gif": return "image/gif";
            case ".webp": return "image/webp";
            case ".bmp": return "image/bmp";
            case ".heic": return "image/heic";
            case ".heif": return "image/heif";
            default: return "image/jpeg";
        }
    }
}
