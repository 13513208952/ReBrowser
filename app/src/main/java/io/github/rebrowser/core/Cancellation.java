package io.github.rebrowser.core;

import java.util.concurrent.atomic.AtomicReference;

/**
 * One task's monotonic request/end state, independent of watcher/document leases.
 * No stop dispatch, executor, slot release, domain result or persistence is performed.
 * Requesters cannot report completion through their view. The trusted execution owner
 * must retain the Reporter until the WHOLE work chain (including native writers) ends;
 * an interrupt/Future.cancel/port close/timeout is never enough to report actual end.
 * This object does not authenticate reporters; never accept one or its snapshot from IPC.
 */
public final class Cancellation {
    public enum RequestOutcome { REQUESTED, ALREADY_REQUESTED, ALREADY_ENDED }
    public enum EndReason { UNDERLYING_RETURNED, UNDERLYING_FAILED, STOP_CONFIRMED }

    public interface Requester {
        RequestOutcome requestCancellation();
        Snapshot snapshot();
    }

    public interface Reporter {
        /**
         * First trusted actual-end record wins. This is execution evidence only,
         * NOT proof an external effect was prevented, storage committed, or a
         * page's entire partition is quiescent. Returns false for later reports.
         */
        boolean reportActualEnd(EndReason reason);
        Snapshot snapshot();
    }

    public static final class Snapshot {
        private final boolean requested;
        private final EndReason end;
        private Snapshot(boolean requested, EndReason end) { this.requested = requested; this.end = end; }
        public boolean cancellationRequested() { return requested; }
        public boolean workEnded() { return end != null; }
        /** Null until trusted actual execution-chain end has been recorded. */
        public EndReason endReason() { return end; }
    }

    private final AtomicReference<Snapshot> state = new AtomicReference<>(new Snapshot(false, null));
    private final Requester requester = new Requester() {
        @Override public RequestOutcome requestCancellation() {
            while (true) {
                Snapshot before = state.get();
                if (before.workEnded()) return RequestOutcome.ALREADY_ENDED;
                if (before.cancellationRequested()) return RequestOutcome.ALREADY_REQUESTED;
                if (state.compareAndSet(before, new Snapshot(true, null))) return RequestOutcome.REQUESTED;
            }
        }
        @Override public Snapshot snapshot() { return state.get(); }
    };
    private final Reporter reporter = new Reporter() {
        @Override public boolean reportActualEnd(EndReason reason) {
            if (reason == null) throw new IllegalArgumentException("Actual end reason required");
            while (true) {
                Snapshot before = state.get();
                if (before.workEnded()) return false;
                Snapshot after = new Snapshot(before.cancellationRequested(), reason);
                if (state.compareAndSet(before, after)) return true;
            }
        }
        @Override public Snapshot snapshot() { return state.get(); }
    };

    private Cancellation() {}

    /** Keep this owner root private to the trusted native task coordinator. */
    public static Cancellation create() { return new Cancellation(); }
    public Requester requester() { return requester; }
    public Reporter reporter() { return reporter; }
}
