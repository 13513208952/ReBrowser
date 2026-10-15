package io.github.reoutlook;

/** Bounded public-CDN artwork read: no Cookie, redirect, arbitrary host, URL logging or disk cache. */
final class ReBrowserMusicArtwork {
    private ReBrowserMusicArtwork() {}
    static android.graphics.Bitmap load(String value) {
        value = ReBrowserMusicSnapshot.artwork(value);
        if (value.isEmpty()) return null;
        java.net.HttpURLConnection c = null;
        try {
            c = (java.net.HttpURLConnection) new java.net.URL(value).openConnection();
            c.setRequestProperty("Cookie", ""); c.setRequestProperty("Authorization", "");
            c.setInstanceFollowRedirects(false); c.setConnectTimeout(8000); c.setReadTimeout(8000);
            if (c.getResponseCode() != 200 || c.getContentLengthLong() > 1_048_576) return null;
            String type = c.getContentType();
            if (type == null || !type.toLowerCase(java.util.Locale.ROOT).startsWith("image/")) return null;
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            try (java.io.InputStream in = c.getInputStream()) {
                byte[] block = new byte[4096]; int n;
                while ((n = in.read(block)) != -1) { if (out.size() + n > 1_048_576) return null; out.write(block, 0, n); }
            }
            byte[] bytes = out.toByteArray();
            android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
            bounds.inJustDecodeBounds = true; android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth > 8192 || bounds.outHeight > 8192
                    || (long) bounds.outWidth * bounds.outHeight > 16_777_216) return null;
            android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
            while (Math.max(bounds.outWidth, bounds.outHeight) / Math.max(1, options.inSampleSize) > 512)
                options.inSampleSize = Math.max(1, options.inSampleSize) * 2;
            return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        } catch (Exception unavailable) { return null; }
        finally { if (c != null) c.disconnect(); }
    }
}
