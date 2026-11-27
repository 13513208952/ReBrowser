package io.github.reoutlook;

import android.app.Activity;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSession.PromptDelegate.BasePrompt;
import org.mozilla.geckoview.GeckoSession.PromptDelegate.FilePrompt;
import org.mozilla.geckoview.GeckoSession.PromptDelegate.PromptInstanceDelegate;
import org.mozilla.geckoview.GeckoSession.PromptDelegate.PromptResponse;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Bridges Android's document grants to the local file paths Gecko's file prompt needs. */
final class ReBrowserGeckoFileChooser {
    private static final int MAX_SELECTION_FILES = 32;
    private static final int MAX_CACHED_FILES = 128;
    private static final long MAX_CACHED_BYTES = 512L * 1024 * 1024;
    private static final int MAX_NAME_LENGTH = 80; // At most 240 UTF-8 bytes.
    private static final String[] NAME_COLUMN = {OpenableColumns.DISPLAY_NAME};

    private static final class Request {
        final GeckoSession owner;
        final FilePrompt prompt;
        final GeckoResult<PromptResponse> result = new GeckoResult<>();
        volatile boolean cancelled;

        Request(GeckoSession owner, FilePrompt prompt) {
            this.owner = owner;
            this.prompt = prompt;
        }
    }

    private static final class StagedFiles {
        final File directory;
        final Uri[] uris;
        final long bytes;

        StagedFiles(File directory, Uri[] uris, long bytes) {
            this.directory = directory;
            this.uris = uris;
            this.bytes = bytes;
        }
    }

    private static final class PageFiles {
        final List<StagedFiles> batches = new ArrayList<>();
        int batchesBeforeNavigation;
    }

    private final Activity activity;
    private final int requestCode;
    private final Runnable onLaunchFailure;
    // Serializes deletion with new copies and lets the thread retire when idle.
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(0, 1, 15, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(), task -> new Thread(task, "gecko-file-cache"));

    // UI thread only: the worker sees only immutable inputs and Request.cancelled.
    private final Map<GeckoSession, PageFiles> filesBySession = new IdentityHashMap<>();
    private Request active;
    private boolean pickerInFlight;
    private boolean copying;
    private boolean closed;
    private int cachedFiles;
    private long cachedBytes;

    ReBrowserGeckoFileChooser(Activity activity, int requestCode) {
        this(activity, requestCode, null);
    }

    ReBrowserGeckoFileChooser(Activity activity, int requestCode, Runnable onLaunchFailure) {
        this.activity = activity;
        this.requestCode = requestCode;
        this.onLaunchFailure = onLaunchFailure;
    }

    GeckoResult<PromptResponse> onFilePrompt(GeckoSession owner, FilePrompt prompt) {
        // Folder mode needs Gecko's separate tree-URI traversal and consent prompt.
        if (closed || active != null || pickerInFlight || copying
                || (prompt.type != FilePrompt.Type.SINGLE
                        && prompt.type != FilePrompt.Type.MULTIPLE)) {
            return GeckoResult.fromValue(prompt.dismiss());
        }
        Request request = new Request(owner, prompt);
        active = request;
        prompt.setDelegate(new PromptInstanceDelegate() {
            @Override public void onPromptDismiss(BasePrompt ignored) {
                if (active == request) cancel();
            }
        });
        pickerInFlight = true;
        try {
            activity.startActivityForResult(pickerIntent(prompt), requestCode);
        } catch (RuntimeException unavailable) {
            pickerInFlight = false;
            dismiss(request);
            if (onLaunchFailure != null) onLaunchFailure.run();
        }
        return request.result;
    }

    boolean pending() {
        return active != null;
    }

    boolean onActivityResult(int code, int resultCode, Intent data) {
        if (code != requestCode || !pickerInFlight) return false;
        pickerInFlight = false;
        Request request = active;
        if (request == null) return true; // Drain a cancelled Android launch before reuse.
        if (resultCode != Activity.RESULT_OK || data == null) {
            dismiss(request);
            return true;
        }
        int limit = request.prompt.type == FilePrompt.Type.SINGLE ? 1 : MAX_SELECTION_FILES;
        List<Uri> selection;
        try {
            selection = selectedUris(data, limit);
        } catch (RuntimeException invalidResult) {
            selection = Collections.emptyList();
        }
        if (selection.isEmpty() || selection.size() > MAX_CACHED_FILES - cachedFiles) {
            dismiss(request);
        } else {
            copy(request, selection, MAX_CACHED_BYTES - cachedBytes);
        }
        return true;
    }

    void cancel() {
        if (active == null) return;
        dismiss(active);
        if (pickerInFlight) {
            // Don't reuse this request code before any late result is consumed.
            try { activity.finishActivity(requestCode); }
            catch (RuntimeException ignored) { /* Ignore the next result regardless. */ }
        }
    }

    void cancel(GeckoSession owner) {
        if (active != null && active.owner == owner) cancel();
        PageFiles files = filesBySession.remove(owner);
        if (files != null) {
            for (StagedFiles batch : files.batches) release(batch);
        }
    }

    void onPageStart(GeckoSession owner) {
        if (active != null && active.owner == owner) cancel();
        PageFiles files = filesBySession.get(owner);
        if (files != null) files.batchesBeforeNavigation = files.batches.size();
    }

    void onPageStop(GeckoSession owner, boolean success) {
        PageFiles files = filesBySession.get(owner);
        if (files == null || !success) return;
        // A form navigation can still be reading its staged attachments at onPageStart.
        for (int i = 0; i < files.batchesBeforeNavigation; i++) release(files.batches.get(i));
        files.batches.subList(0, files.batchesBeforeNavigation).clear();
        files.batchesBeforeNavigation = 0;
        if (files.batches.isEmpty()) filesBySession.remove(owner);
    }

    void close() {
        if (closed) return;
        closed = true;
        cancel();
        for (PageFiles files : filesBySession.values()) {
            for (StagedFiles batch : files.batches) release(batch);
        }
        filesBySession.clear();
        if (!copying) worker.shutdown();
    }

    private void dismiss(Request request) {
        active = null;
        request.cancelled = true;
        request.prompt.setDelegate(null);
        if (!request.prompt.isComplete()) request.result.complete(request.prompt.dismiss());
    }

    private void copy(Request request, List<Uri> selection, long byteBudget) {
        copying = true;
        worker.execute(() -> {
            StagedFiles staged = stage(request, selection, byteBudget);
            activity.runOnUiThread(() -> finishCopy(request, staged));
        });
    }

    private void finishCopy(Request request, StagedFiles staged) {
        copying = false;
        if (active != request || !request.owner.isOpen() || request.prompt.isComplete()) {
            if (active == request) dismiss(request);
            if (staged != null) worker.execute(() -> deleteBatch(staged.directory));
            if (closed) worker.shutdown();
            return;
        }
        if (staged == null) {
            dismiss(request);
            return;
        }
        PromptResponse response;
        try {
            response = request.prompt.confirm(activity, staged.uris);
        } catch (RuntimeException invalidFile) {
            dismiss(request);
            worker.execute(() -> deleteBatch(staged.directory));
            return;
        }
        active = null;
        request.prompt.setDelegate(null);
        cachedFiles += staged.uris.length;
        cachedBytes += staged.bytes;
        filesBySession.computeIfAbsent(request.owner, ignored -> new PageFiles())
                .batches.add(staged);
        request.result.complete(response);
    }

    private StagedFiles stage(Request request, List<Uri> selection, long byteBudget) {
        File directory = null;
        StagedFiles staged = null;
        try {
            if (request.cancelled) return null;
            ContentResolver resolver = activity.getContentResolver();
            directory = Files.createTempDirectory(activity.getCacheDir().toPath(),
                    "gecko-upload-").toFile();
            byte[] buffer = new byte[64 * 1024];
            Uri[] files = new Uri[selection.size()];
            long size = 0;
            for (int i = 0; i < selection.size(); i++) {
                if (request.cancelled) return null;
                Uri uri = selection.get(i);
                // Each attachment gets its own directory, so duplicate names are preserved.
                File slot = new File(directory, Integer.toString(i));
                if (!slot.mkdir()) return null;
                File file = new File(slot, fileName(resolver, uri));
                if (request.cancelled) return null;
                try (InputStream input = resolver.openInputStream(uri)) {
                    if (input == null) return null;
                    try (FileOutputStream output = new FileOutputStream(file)) {
                        int read;
                        while ((read = input.read(buffer)) != -1) {
                            if (request.cancelled || read > byteBudget - size) return null;
                            output.write(buffer, 0, read);
                            size += read;
                        }
                    }
                }
                files[i] = Uri.fromFile(file);
            }
            if (!request.cancelled) staged = new StagedFiles(directory, files, size);
            return staged;
        } catch (IOException | RuntimeException unreadable) {
            // Treat provider errors and disk exhaustion as a cancelled selection, not partial input.
            return null;
        } finally {
            if (staged == null && directory != null) deleteBatch(directory);
        }
    }

    private void release(StagedFiles batch) {
        cachedFiles -= batch.uris.length;
        cachedBytes -= batch.bytes;
        worker.execute(() -> deleteBatch(batch.directory));
    }

    private static void deleteBatch(File directory) {
        File[] slots = directory.listFiles();
        if (slots != null) {
            for (File slot : slots) {
                File[] files = slot.listFiles();
                if (files != null) for (File file : files) file.delete();
                slot.delete();
            }
        }
        directory.delete();
    }

    private static List<Uri> selectedUris(Intent data, int limit) {
        ClipData clip = data.getClipData();
        int count = clip == null ? 0 : clip.getItemCount();
        if (count > limit) return Collections.emptyList();
        if (count == 0) {
            Uri uri = data.getData();
            return isContent(uri) ? Collections.singletonList(uri) : Collections.emptyList();
        }
        List<Uri> uris = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Uri uri = clip.getItemAt(i).getUri();
            if (!isContent(uri)) return Collections.emptyList();
            uris.add(uri);
        }
        return uris;
    }

    private static boolean isContent(Uri uri) {
        return uri != null && ContentResolver.SCHEME_CONTENT.equals(uri.getScheme());
    }

    private static Intent pickerIntent(FilePrompt prompt) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE);
        Set<String> types = new LinkedHashSet<>();
        if (prompt.mimeTypes != null) {
            for (String raw : prompt.mimeTypes) {
                if (raw == null) continue;
                String type = raw.trim().toLowerCase(Locale.ROOT);
                if (type.startsWith(".")) {
                    type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(type.substring(1));
                }
                if (type == null) continue;
                int slash = type.indexOf('/');
                if (slash > 0 && slash < type.length() - 1) types.add(type);
            }
        }
        if (types.isEmpty() || types.contains("*/*")) {
            intent.setType("*/*");
        } else if (types.size() == 1) {
            intent.setType(types.iterator().next());
        } else {
            String first = types.iterator().next();
            String prefix = first.substring(0, first.indexOf('/') + 1);
            String common = prefix + '*';
            for (String type : types) {
                if (!type.startsWith(prefix)) {
                    common = "*/*";
                    break;
                }
            }
            intent.setType(common).putExtra(Intent.EXTRA_MIME_TYPES,
                    types.toArray(new String[types.size()]));
        }
        if (prompt.type == FilePrompt.Type.MULTIPLE) {
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        }
        return intent;
    }

    private static String fileName(ContentResolver resolver, Uri uri) {
        String name = null;
        try (Cursor cursor = resolver.query(uri, NAME_COLUMN, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) name = cursor.getString(0);
        } catch (RuntimeException ignored) {
            // Metadata is optional even when the provider can open the file.
        }
        if (name == null || name.trim().isEmpty()) {
            String extension = null;
            try {
                extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(resolver.getType(uri));
            } catch (RuntimeException ignored) { /* No MIME metadata. */ }
            name = extension == null ? "attachment" : "attachment." + extension;
        }
        int dot = name.lastIndexOf('.');
        if (name.length() > MAX_NAME_LENGTH && dot > 0 && name.length() - dot <= 16) {
            int keep = MAX_NAME_LENGTH - (name.length() - dot);
            if (Character.isHighSurrogate(name.charAt(keep - 1))) keep--;
            name = name.substring(0, keep) + name.substring(dot);
        }
        int limit = Math.min(name.length(), MAX_NAME_LENGTH);
        StringBuilder safe = new StringBuilder(limit);
        for (int i = 0; i < limit;) {
            int cp = name.codePointAt(i);
            i += Character.charCount(cp);
            if (i > limit) break;
            int type = Character.getType(cp);
            if (Character.isISOControl(cp) || type == Character.FORMAT
                    || type == Character.SURROGATE || "\\/:*?\"<>|".indexOf(cp) >= 0) {
                safe.append('_');
            } else {
                safe.appendCodePoint(cp);
            }
        }
        String result = safe.toString().trim();
        return result.isEmpty() || ".".equals(result) || "..".equals(result)
                ? "attachment" : result;
    }
}
