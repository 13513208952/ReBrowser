package io.github.reoutlook;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.function.Function;

/** Private browsing history, separate from mail/Profile data. No background expiry service. */
final class ReBrowserHistory implements AutoCloseable {
    static final long RETENTION_MS = 30 * 60 * 1000L;
    private static final String DATABASE = "rebrowser_history.db";
    private static final String[] ENTRY_COLUMNS = {"id", "workspace_id", "tab_id", "title", "url",
            "source_title", "visited_at", "expires_at"};
    // Process-local, not Activity-local: backgrounding or recreation must not resume recording.
    private static boolean paused;
    private static boolean startupPending = true;
    private final Database helper;
    private final LongSupplier clock;
    private final Map<String, Long> currentVisits = new HashMap<>();
    private static String lastError = "";

    static final class Entry {
        final long id, visitedAt;
        final String workspaceId, tabId, title, url, sourceTitle;
        final Long expiresAt;

        Entry(Cursor row) {
            id = row.getLong(0);
            workspaceId = row.getString(1);
            tabId = row.getString(2);
            title = row.getString(3);
            url = row.getString(4);
            sourceTitle = row.getString(5);
            visitedAt = row.getLong(6);
            expiresAt = row.isNull(7) ? null : row.getLong(7);
        }
    }

    ReBrowserHistory(Context context) {
        this(context, System::currentTimeMillis);
    }

    ReBrowserHistory(Context context, LongSupplier clock) {
        helper = new Database(context.getApplicationContext());
        this.clock = clock;
    }

    boolean paused() { return paused; }

    boolean setPaused(boolean value) {
        boolean changed = paused != value;
        paused = value;
        currentVisits.clear();
        return changed;
    }

    boolean maintaining() {
        return maintaining(helper.getReadableDatabase());
    }

    void setMaintaining(boolean value) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            if (maintaining(db) != value) {
                long now = clock.getAsLong();
                if (value) cleanup(db, now); // Never resurrect already-expired history.
                else {
                    ContentValues expiry = new ContentValues();
                    expiry.put("expires_at", now + RETENTION_MS);
                    db.update("visits", expiry, "expires_at IS NOT NULL", null);
                }
                ContentValues setting = new ContentValues();
                setting.put("name", "maintain");
                setting.put("value", value ? 1 : 0);
                db.insertWithOnConflict("settings", null, setting, SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    long record(ReBrowserStore.Workspace workspace, ReBrowserStore.Tab tab,
            String url, String title) {
        if (paused || workspace == null || tab == null || !workspace.tabs.contains(tab)) return 0;
        String cleanUrl = cleanUrl(url);
        if (cleanUrl == null) return 0;
        try {
            SQLiteDatabase db = helper.getWritableDatabase();
            long now = clock.getAsLong();
            if (!db.inTransaction()) cleanup(db, now);
            ContentValues values = new ContentValues();
            values.put("workspace_id", workspace.id);
            values.put("tab_id", tab.id);
            values.put("url", cleanUrl);
            values.put("title", cleanTitle(title, cleanUrl));
            values.put("source_title", cleanTitle(workspace.title, "总标签页"));
            values.put("visited_at", now);
            if (workspace.level == ReBrowserStore.Level.TEMPORARY) {
                values.put("expires_at", now + RETENTION_MS);
            } else values.putNull("expires_at");
            long id = db.insertOrThrow("visits", null, values);
            currentVisits.put(tab.id, id);
            lastError = "";
            return id;
        } catch (SQLiteException error) {
            lastError = error.getClass().getSimpleName();
            return 0; // A full/unavailable history database must not break browsing.
        }
    }

    int syncCurrent(List<ReBrowserStore.Workspace> openWorkspaces,
            Function<ReBrowserStore.Tab, String> urls, Function<ReBrowserStore.Tab, String> titles) {
        if (paused) return 0;
        try {
            SQLiteDatabase db = helper.getWritableDatabase();
            db.beginTransaction();
            int count = 0;
            try {
                cleanup(db, clock.getAsLong());
                for (ReBrowserStore.Workspace workspace : openWorkspaces) {
                    for (ReBrowserStore.Tab tab : workspace.tabs) {
                        String url = urls.apply(tab);
                        String title = titles.apply(tab);
                        if (record(workspace, tab, url == null ? tab.url : url,
                                title == null || title.isBlank() ? tab.title : title) > 0) count++;
                    }
                }
                db.setTransactionSuccessful();
            } finally { db.endTransaction(); }
            return count;
        } catch (SQLiteException error) {
            currentVisits.clear();
            lastError = error.getClass().getSimpleName();
            return 0;
        }
    }

    int syncStartup(List<ReBrowserStore.Workspace> openWorkspaces, ReBrowserStore.Tab visibleTab) {
        if (!startupPending || paused) return 0;
        startupPending = false;
        // The visible page's native commit will record it; snapshot only unloaded background tabs.
        List<ReBrowserStore.Workspace> background = new ArrayList<>();
        for (ReBrowserStore.Workspace workspace : openWorkspaces) {
            ReBrowserStore.Workspace copy = new ReBrowserStore.Workspace(workspace.id, workspace.title, workspace.level);
            for (ReBrowserStore.Tab tab : workspace.tabs) if (tab != visibleTab) copy.tabs.add(tab);
            background.add(copy);
        }
        return syncCurrent(background, tab -> tab.url, tab -> tab.title);
    }

    void navigationStarted(String tabId) { currentVisits.remove(tabId); }

    void updateTitle(ReBrowserStore.Tab tab, String url, String title) {
        if (paused || tab == null || title == null || title.isBlank()) return;
        Long id = currentVisits.get(tab.id);
        String cleanUrl = cleanUrl(url);
        if (id == null || cleanUrl == null) return;
        try {
            ContentValues values = new ContentValues();
            values.put("title", cleanTitle(title, cleanUrl));
            helper.getWritableDatabase().update("visits", values, "id=? AND url=?",
                    new String[]{Long.toString(id), cleanUrl});
        } catch (SQLiteException error) { lastError = error.getClass().getSimpleName(); }
    }

    void workspaceChanged(ReBrowserStore.Workspace workspace, boolean open) {
        if (!open) for (ReBrowserStore.Tab tab : workspace.tabs) currentVisits.remove(tab.id);
        try {
            SQLiteDatabase db = helper.getWritableDatabase();
            db.beginTransaction();
            try {
                long now = clock.getAsLong();
                cleanup(db, now);
                ContentValues values = new ContentValues();
                values.put("source_title", cleanTitle(workspace.title, "总标签页"));
                if (open && workspace.level != ReBrowserStore.Level.TEMPORARY) {
                    values.putNull("expires_at");
                    db.update("visits", values, "workspace_id=?", new String[]{workspace.id});
                } else {
                    values.put("expires_at", now + RETENTION_MS);
                    // Closing temporary or deleting an already-shelved source does not renew TTL.
                    db.update("visits", values, "workspace_id=? AND expires_at IS NULL",
                            new String[]{workspace.id});
                }
                db.setTransactionSuccessful();
            } finally { db.endTransaction(); }
        } catch (SQLiteException error) { lastError = error.getClass().getSimpleName(); }
    }

    void reconcile(List<ReBrowserStore.Workspace> openWorkspaces) {
        try {
            SQLiteDatabase db = helper.getWritableDatabase();
            db.beginTransaction();
            try {
                long now = clock.getAsLong();
                cleanup(db, now);
                List<String> durable = new ArrayList<>();
                for (ReBrowserStore.Workspace workspace : openWorkspaces) {
                    if (workspace.level != ReBrowserStore.Level.TEMPORARY) durable.add(workspace.id);
                }
                ContentValues closed = new ContentValues();
                closed.put("expires_at", now + RETENTION_MS);
                String placeholders = String.join(",", java.util.Collections.nCopies(durable.size(), "?"));
                db.update("visits", closed, "expires_at IS NULL" + (durable.isEmpty() ? ""
                        : " AND workspace_id NOT IN (" + placeholders + ")"), durable.toArray(new String[0]));
                ContentValues unlimited = new ContentValues();
                unlimited.putNull("expires_at");
                for (String id : durable) db.update("visits", unlimited,
                        "workspace_id=?", new String[]{id});
                db.setTransactionSuccessful();
            } finally { db.endTransaction(); }
        } catch (SQLiteException error) { lastError = error.getClass().getSimpleName(); }
    }

    List<Entry> entries(int offset, int limit) {
        SQLiteDatabase db = helper.getWritableDatabase();
        cleanup(db, clock.getAsLong());
        List<Entry> entries = new ArrayList<>();
        try (Cursor rows = db.query("visits", ENTRY_COLUMNS, null, null, null, null,
                "visited_at DESC, id DESC", Math.max(0, offset) + "," + Math.max(1, Math.min(100, limit)))) {
            while (rows.moveToNext()) entries.add(new Entry(rows));
        }
        return entries;
    }

    Entry find(long id) {
        SQLiteDatabase db = helper.getWritableDatabase();
        cleanup(db, clock.getAsLong());
        try (Cursor rows = db.query("visits", ENTRY_COLUMNS, "id=?", new String[]{Long.toString(id)},
                null, null, null)) {
            return rows.moveToFirst() ? new Entry(rows) : null;
        }
    }

    void delete(long id) {
        helper.getWritableDatabase().delete("visits", "id=?", new String[]{Long.toString(id)});
        currentVisits.values().removeIf(value -> value == id);
    }

    int clearTimed() {
        SQLiteDatabase db = helper.getWritableDatabase();
        if (maintaining(db)) return 0;
        return db.delete("visits", "expires_at IS NOT NULL", null);
    }

    JSONObject snapshot() throws JSONException {
        SQLiteDatabase db = helper.getWritableDatabase();
        cleanup(db, clock.getAsLong());
        boolean hold = maintaining(db);
        try (Cursor row = db.rawQuery("SELECT COUNT(*), COUNT(expires_at), MIN(expires_at) FROM visits", null)) {
            row.moveToFirst();
            return new JSONObject().put("maintaining", hold).put("paused", paused)
                    .put("recordCount", row.getLong(0)).put("timedRecordCount", row.getLong(1))
                    .put("longLivedRecordCount", row.getLong(0) - row.getLong(1))
                    .put("retentionSeconds", RETENTION_MS / 1000)
                    .put("nextExpiryAt", hold || row.isNull(2) ? 0 : row.getLong(2))
                    .put("lastStorageError", lastError).put("administratorCanSet", false);
        }
    }

    private static boolean maintaining(SQLiteDatabase db) {
        try (Cursor row = db.rawQuery("SELECT value FROM settings WHERE name='maintain'", null)) {
            return row.moveToFirst() && row.getInt(0) == 1;
        }
    }

    private static void cleanup(SQLiteDatabase db, long now) {
        if (!maintaining(db)) db.delete("visits", "expires_at IS NOT NULL AND expires_at<=?",
                new String[]{Long.toString(now)});
    }

    static String cleanUrl(String value) {
        if (value == null || value.length() > 8192) return null;
        String clean = value.trim();
        Uri uri = Uri.parse(clean);
        if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getHost().isBlank()) return null;
        String authority = uri.getEncodedAuthority();
        if (authority != null && authority.contains("@")) {
            clean = uri.buildUpon().encodedAuthority(authority.substring(authority.lastIndexOf('@') + 1))
                    .build().toString();
        }
        return clean;
    }

    private static String cleanTitle(String title, String fallback) {
        String clean = title == null ? "" : title.replaceAll("\\s+", " ").trim();
        if (clean.isEmpty()) clean = fallback;
        return clean.substring(0, Math.min(clean.length(), 160));
    }

    @Override public void close() { helper.close(); }

    private static final class Database extends SQLiteOpenHelper {
        Database(Context context) { super(context, DATABASE, null, 1); }
        @Override public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE visits (id INTEGER PRIMARY KEY AUTOINCREMENT, workspace_id TEXT NOT NULL,"
                    + " tab_id TEXT NOT NULL, title TEXT NOT NULL, url TEXT NOT NULL, source_title TEXT NOT NULL,"
                    + " visited_at INTEGER NOT NULL, expires_at INTEGER)");
            db.execSQL("CREATE INDEX visits_order ON visits(visited_at DESC, id DESC)");
            db.execSQL("CREATE INDEX visits_source ON visits(workspace_id)");
            db.execSQL("CREATE INDEX visits_expiry ON visits(expires_at)");
            db.execSQL("CREATE TABLE settings (name TEXT PRIMARY KEY, value INTEGER NOT NULL)");
        }
        @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            throw new SQLiteException("Unsupported history schema upgrade");
        }
    }
}
