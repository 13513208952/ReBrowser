package io.github.reoutlook;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.WeakHashMap;

/** Target-scoped admission/drain fence. A failed/interrupted data reset remains closed on restart. */
final class ReBrowserSubapplicationGate {
    private static State state;
    private ReBrowserSubapplicationGate() {}
    private static final class State {
        final Context application;
        final SharedPreferences journal;
        final int[] users = new int[2];
        final boolean[] running = new boolean[2];
        final boolean[] quarantine = new boolean[2];
        final WeakHashMap<Activity, String> activities = new WeakHashMap<>();
        State(Context context) {
            application = context.getApplicationContext();
            journal = application.getSharedPreferences("rb_subapplication_cleanup_v1", Context.MODE_PRIVATE);
            quarantine[0] = pending(ReBrowserApplications.OUTLOOK);
            quarantine[1] = pending(ReBrowserApplications.MUSIC);
        }
        private boolean pending(String id) {
            try { return journal.getBoolean(id, false); }
            catch (ClassCastException malformed) { return true; } // Recover only through confirmed data cleanup.
        }
    }
    private static synchronized State state(Context context) {
        Context application = context.getApplicationContext();
        if (state == null || state.application != application) state = new State(application);
        return state;
    }
    private static int index(String id) {
        if (ReBrowserApplications.OUTLOOK.equals(id)) return 0;
        if (ReBrowserApplications.MUSIC.equals(id)) return 1;
        throw new IllegalArgumentException("Unsupported fixed application");
    }
    static final class Lease implements AutoCloseable {
        private final State owner;
        private final int index;
        private boolean closed;
        private Lease(State owner, int index) { this.owner = owner; this.index = index; }
        @Override public void close() {
            synchronized (ReBrowserSubapplicationGate.class) {
                if (!closed) { closed = true; owner.users[index]--; }
            }
        }
    }
    static synchronized Lease acquire(Context context, String id) {
        State s = state(context); int i = index(id);
        if (s.running[i] || s.quarantine[i]) throw new IllegalStateException("application-cleanup-pending");
        s.users[i]++; return new Lease(s, i);
    }
    static synchronized boolean blocked(Context context, String id) {
        State s = state(context); int i = index(id); return s.running[i] || s.quarantine[i];
    }
    static synchronized boolean running(Context context, String id) { return state(context).running[index(id)]; }
    static synchronized boolean pendingData(Context context, String id) { return state(context).quarantine[index(id)]; }
    static synchronized int users(Context context, String id) { return state(context).users[index(id)]; }
    static synchronized boolean begin(Context context, String id, boolean data) {
        State s = state(context); int i = index(id);
        if (s.running[i] || (!data && s.quarantine[i])) return false;
        // Persist intent before quiescing, so process death cannot silently reopen a half-reset account.
        if (data) {
            s.quarantine[i] = true;
            if (!s.journal.edit().putBoolean(id, true).commit()) throw new IllegalStateException("cleanup-journal-save-failed");
        }
        s.running[i] = true; return true;
    }
    static synchronized void end(Context context, String id, boolean completed) {
        State s = state(context); int i = index(id);
        s.running[i] = false;
        if (completed) {
            if (!s.journal.edit().remove(id).commit()) {
                s.quarantine[i] = true; throw new IllegalStateException("cleanup-journal-save-failed");
            }
            s.quarantine[i] = false;
        }
    }
    static synchronized void register(Activity activity, String id) { index(id); state(activity).activities.put(activity, id); }
    static synchronized void unregister(Activity activity) { state(activity).activities.remove(activity); }
    /** UI thread only. Never finish the protected administrator/mail-export screens. */
    static void closeMailScreens(Context context) {
        ArrayList<Activity> close = new ArrayList<>();
        synchronized (ReBrowserSubapplicationGate.class) {
            for (java.util.Map.Entry<Activity, String> e : state(context).activities.entrySet())
                if (ReBrowserApplications.OUTLOOK.equals(e.getValue())) close.add(e.getKey());
        }
        for (Activity activity : close) if (!activity.isFinishing()) activity.finish();
    }
}
