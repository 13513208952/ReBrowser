package io.github.rebrowser.ui;

import static org.junit.Assert.*;
import io.github.rebrowser.core.TaskSnapshot;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

/** Actual bounded TaskSnapshot display model: no stream, file, publication or installer action. */
public final class DownloadsAdapterTest {
    private static TaskSnapshot snap(TaskSnapshot.Phase phase, TaskSnapshot.Work work, TaskSnapshot.Effect effect,
                                     TaskSnapshot.Durability durability, TaskSnapshot.ClientWait wait,
                                     boolean cancellationRequested, boolean currentForPublication) {
        return TaskSnapshot.fromNativeEvidence(phase, work, effect, durability, wait, cancellationRequested, currentForPublication);
    }
    private static TaskSnapshot running() {
        return snap(TaskSnapshot.Phase.DISPATCH_INTENT, TaskSnapshot.Work.RUNNING, TaskSnapshot.Effect.UNKNOWN,
                TaskSnapshot.Durability.COMMITTED, TaskSnapshot.ClientWait.WAITING, false, true);
    }
    private static DownloadsAdapter.Row row(TaskSnapshot snapshot, boolean handoffOffered) {
        return new DownloadsAdapter.Row(new Object(), "文件.zip", 1_048_576, 524_288, snapshot, handoffOffered);
    }
    private static List<DownloadsAdapter.Row> page(int count) {
        List<DownloadsAdapter.Row> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) rows.add(new DownloadsAdapter.Row(new Object(), "文件" + i + ".zip", 1024, 512, running(), false));
        return rows;
    }
    /** Every one of the 14 legal (phase, work, effect) combinations must be displayable. */
    @Test public void allFourteenLegalTaskSnapshotCombinationsAreDisplayedWithoutFakeCompletion(){
        TaskSnapshot.Phase[] phases = {TaskSnapshot.Phase.RESERVED, TaskSnapshot.Phase.WORK_STARTED,
                TaskSnapshot.Phase.DISPATCH_INTENT, TaskSnapshot.Phase.OUTCOME_OBSERVED,
                TaskSnapshot.Phase.ENDED_BEFORE_DISPATCH, TaskSnapshot.Phase.OUTCOME_UNKNOWN,
                TaskSnapshot.Phase.OWNER_LOST_BEFORE_DISPATCH};
        List<DownloadsAdapter.Row> rows = new ArrayList<>();
        for (TaskSnapshot.Phase phase : phases) {
            for (TaskSnapshot.Work work : TaskSnapshot.Work.values()) {
                for (TaskSnapshot.Effect effect : TaskSnapshot.Effect.values()) {
                    TaskSnapshot snapshot;
                    try { snapshot = snap(phase, work, effect, TaskSnapshot.Durability.UNKNOWN, TaskSnapshot.ClientWait.WAITING, false, false); }
                    catch (IllegalArgumentException invalidCombination) { continue; }
                    DownloadsAdapter.Row row = new DownloadsAdapter.Row(new Object(), "组合", 1000, 500, snapshot, true);
                    rows.add(row);
                }
            }
        }
        assertEquals(14, rows.size());
        DownloadsAdapter.Rows.validate(rows, 1);
        for (DownloadsAdapter.Row row : rows) {
            String text = DownloadsAdapter.Rows.statusText(row) + " " + DownloadsAdapter.Rows.detail(row);
            assertFalse(text.isEmpty());
            for (String claim : new String[]{"已完成", "已发布", "可打开", "安装完成", "下载完成", "已保存到"}) {
                assertFalse("false completion claim: " + claim + " in " + text, text.contains(claim));
            }
        }
    }
    @Test public void observedEffectWhileWorkUnsettledIsNotFinishedOrHandoffable(){
        TaskSnapshot runningSuccess = snap(TaskSnapshot.Phase.OUTCOME_OBSERVED, TaskSnapshot.Work.RUNNING,
                TaskSnapshot.Effect.OBSERVED_SUCCESS, TaskSnapshot.Durability.COMMITTED, TaskSnapshot.ClientWait.WAITING, false, true);
        DownloadsAdapter.Row row = row(runningSuccess, true);
        assertEquals(DownloadsAdapter.Terminal.EFFECT_OBSERVED_WORK_UNSETTLED, DownloadsAdapter.Rows.terminal(row));
        assertFalse(DownloadsAdapter.Rows.canRequestHandoff(row));
        assertTrue(DownloadsAdapter.Rows.statusText(row).contains("工作结束未确认"));
    }
    @Test public void endedButUnknownEffectStaysUnknownAndCannotHandoff(){
        TaskSnapshot endedUnknown = snap(TaskSnapshot.Phase.OUTCOME_UNKNOWN, TaskSnapshot.Work.ENDED,
                TaskSnapshot.Effect.UNKNOWN, TaskSnapshot.Durability.COMMITTED, TaskSnapshot.ClientWait.WAITING, false, true);
        DownloadsAdapter.Row row = row(endedUnknown, true);
        assertEquals(DownloadsAdapter.Terminal.WORK_RUNNING_OR_UNKNOWN, DownloadsAdapter.Rows.terminal(row));
        assertFalse(DownloadsAdapter.Rows.canRequestHandoff(row));
        assertTrue(DownloadsAdapter.Rows.statusText(row).contains("状态未知"));
    }
    @Test public void unknownDurabilityBlocksHandoffButDoesNotRewriteWorkEnd(){
        TaskSnapshot successUnknownDurability = snap(TaskSnapshot.Phase.OUTCOME_OBSERVED, TaskSnapshot.Work.ENDED,
                TaskSnapshot.Effect.OBSERVED_SUCCESS, TaskSnapshot.Durability.UNKNOWN, TaskSnapshot.ClientWait.WAITING, false, true);
        DownloadsAdapter.Row row = row(successUnknownDurability, true);
        assertTrue(row.snapshot().actualWorkEnded());
        assertFalse(DownloadsAdapter.Rows.canRequestHandoff(row));
        assertTrue(DownloadsAdapter.Rows.statusText(row).contains("持久化未知"));
    }
    @Test public void handoffIntentNeedsAllAxesAndStillIsNotPublication(){
        TaskSnapshot committed = snap(TaskSnapshot.Phase.OUTCOME_OBSERVED, TaskSnapshot.Work.ENDED,
                TaskSnapshot.Effect.OBSERVED_SUCCESS, TaskSnapshot.Durability.COMMITTED, TaskSnapshot.ClientWait.WAITING, false, true);
        assertTrue(DownloadsAdapter.Rows.canRequestHandoff(row(committed, true)));
        assertFalse(DownloadsAdapter.Rows.canRequestHandoff(row(committed, false)));
        TaskSnapshot stale = snap(TaskSnapshot.Phase.OUTCOME_OBSERVED, TaskSnapshot.Work.ENDED,
                TaskSnapshot.Effect.OBSERVED_SUCCESS, TaskSnapshot.Durability.COMMITTED, TaskSnapshot.ClientWait.WAITING, false, false);
        assertFalse(DownloadsAdapter.Rows.canRequestHandoff(row(stale, true)));
        TaskSnapshot cancelled = snap(TaskSnapshot.Phase.OUTCOME_OBSERVED, TaskSnapshot.Work.ENDED,
                TaskSnapshot.Effect.OBSERVED_SUCCESS, TaskSnapshot.Durability.COMMITTED, TaskSnapshot.ClientWait.WAITING, true, true);
        assertFalse(DownloadsAdapter.Rows.canRequestHandoff(row(cancelled, true)));
        String text = DownloadsAdapter.Rows.statusText(row(committed, true));
        assertFalse(text.contains("已发布"));
        assertFalse(text.contains("可打开"));
    }
    @Test public void clientDeadlineAndCancellationRequestAreSeparateAxes(){
        TaskSnapshot deadline = snap(TaskSnapshot.Phase.DISPATCH_INTENT, TaskSnapshot.Work.RUNNING,
                TaskSnapshot.Effect.UNKNOWN, TaskSnapshot.Durability.NOT_COMMITTED, TaskSnapshot.ClientWait.DEADLINE_EXCEEDED, false, false);
        DownloadsAdapter.Row waited = row(deadline, false);
        assertTrue(waited.snapshot().work() == TaskSnapshot.Work.RUNNING);
        assertTrue(DownloadsAdapter.Rows.statusText(waited).contains("等待超时，不代表任务结束"));
        assertTrue(DownloadsAdapter.Rows.canRequestCancel(waited));
        TaskSnapshot requested = snap(TaskSnapshot.Phase.DISPATCH_INTENT, TaskSnapshot.Work.RUNNING,
                TaskSnapshot.Effect.UNKNOWN, TaskSnapshot.Durability.NOT_COMMITTED, TaskSnapshot.ClientWait.WAITING, true, false);
        DownloadsAdapter.Row cancelling = row(requested, false);
        assertFalse(DownloadsAdapter.Rows.canRequestCancel(cancelling));
        assertTrue(DownloadsAdapter.Rows.statusText(cancelling).contains("结束未确认"));
        assertTrue(cancelling.snapshot().work() == TaskSnapshot.Work.RUNNING);
    }
    @Test public void unknownTotalNeverShowsFakePercentOrOneHundred(){
        DownloadsAdapter.Row unknownTotal = new DownloadsAdapter.Row(new Object(), "部分文件", DownloadsAdapter.UNKNOWN_BYTES, 10_000, running(), false);
        assertEquals(-1, DownloadsAdapter.Rows.shownPercent(unknownTotal));
        assertEquals("未知", DownloadsAdapter.Rows.sizeText(unknownTotal.totalBytes()));
        assertTrue(DownloadsAdapter.Rows.detail(unknownTotal).contains("大小未知"));
        DownloadsAdapter.Row unknownReceived = new DownloadsAdapter.Row(new Object(), "部分文件", 10_000, DownloadsAdapter.UNKNOWN_BYTES, running(), false);
        assertEquals(-1, DownloadsAdapter.Rows.shownPercent(unknownReceived));
        DownloadsAdapter.Row empty = new DownloadsAdapter.Row(new Object(), "空文件", 0, 0, running(), false);
        assertEquals(0, DownloadsAdapter.Rows.shownPercent(empty));
    }
    @Test public void fullKnownPercentDoesNotMeanCompletionOrHandoff(){
        TaskSnapshot runningAtSize = running();
        DownloadsAdapter.Row full = new DownloadsAdapter.Row(new Object(), "满进度但仍运行", 4096, 4096, runningAtSize, true);
        assertEquals(100, DownloadsAdapter.Rows.shownPercent(full));
        assertFalse(DownloadsAdapter.Rows.canRequestHandoff(full));
        assertFalse(full.snapshot().actualWorkEnded());
    }
    @Test public void explicitApprovalIsOnlyCurrentReservedUserIntent(){
        TaskSnapshot reserved=snap(TaskSnapshot.Phase.RESERVED,TaskSnapshot.Work.NOT_STARTED,TaskSnapshot.Effect.NOT_DISPATCHED,TaskSnapshot.Durability.COMMITTED,TaskSnapshot.ClientWait.WAITING,false,true);
        assertTrue(DownloadsAdapter.Rows.canRequestApproval(new DownloadsAdapter.Row(new Object(),"待批准",-1,-1,reserved,true,false)));
        assertFalse(DownloadsAdapter.Rows.canRequestApproval(new DownloadsAdapter.Row(new Object(),"未提供",-1,-1,reserved,false,false)));
        assertFalse(DownloadsAdapter.Rows.canRequestApproval(new DownloadsAdapter.Row(new Object(),"已运行",-1,-1,running(),true,false)));
        assertFalse(DownloadsAdapter.Rows.canRequestApproval(new DownloadsAdapter.Row(new Object(),"旧行",-1,-1,reserved.withoutPublication(),true,false)));
    }
    @Test public void cancellationRequestCannotEraseAlreadyObservedActualEnd(){
        TaskSnapshot ended=snap(TaskSnapshot.Phase.OUTCOME_UNKNOWN,TaskSnapshot.Work.ENDED,TaskSnapshot.Effect.UNKNOWN,TaskSnapshot.Durability.UNKNOWN,TaskSnapshot.ClientWait.DETACHED,true,true);
        String status=DownloadsAdapter.Rows.statusText(row(ended,false));assertTrue(status.contains("工作已结束"));assertFalse(status.contains("结束未确认"));assertFalse(DownloadsAdapter.Rows.canRequestHandoff(row(ended,true)));
    }
    @Test public void byteTextIsExactAndUnknownStaysUnknown(){
        assertEquals("未知", DownloadsAdapter.Rows.sizeText(-1));
        assertEquals("0 B", DownloadsAdapter.Rows.sizeText(0));
        assertEquals("1023 B", DownloadsAdapter.Rows.sizeText(1023));
        assertEquals("1.0 KiB", DownloadsAdapter.Rows.sizeText(1024));
        assertEquals("1.5 MiB", DownloadsAdapter.Rows.sizeText(1572864));
        assertEquals("1.0 GiB", DownloadsAdapter.Rows.sizeText(1073741824));
    }
    @Test public void invalidSizesNamesAndSnapshotsAreRejected(){
        Object token = new Object();
        assertThrows(IllegalArgumentException.class, () -> new DownloadsAdapter.Row(null, "n", 1, 1, running(), false));
        assertThrows(IllegalArgumentException.class, () -> new DownloadsAdapter.Row(token, "n", 1, 1, null, false));
        assertThrows(IllegalArgumentException.class, () -> new DownloadsAdapter.Row(token, "", 1, 1, running(), false));
        assertThrows(IllegalArgumentException.class, () -> new DownloadsAdapter.Row(token, "x".repeat(257), 1, 1, running(), false));
        assertThrows(IllegalArgumentException.class, () -> new DownloadsAdapter.Row(token, "bad\nname", 1, 1, running(), false));
        assertThrows(IllegalArgumentException.class, () -> new DownloadsAdapter.Row(token, "n", 100, 200, running(), false));
        assertThrows(IllegalArgumentException.class, () -> new DownloadsAdapter.Row(token, "n", -2, 0, running(), false));
        assertThrows(IllegalArgumentException.class, () -> new DownloadsAdapter.Row(token, "n", DownloadsAdapter.MAX_BYTES + 1, 0, running(), false));
        assertThrows(IllegalArgumentException.class, () -> new DownloadsAdapter.Row(token, "n", 100, DownloadsAdapter.MAX_BYTES + 1, running(), false));
    }
    @Test public void pageIsBoundedAndDefensivelyCopiedWithStableFrameLifecycle(){
        DownloadsAdapter.State state = new DownloadsAdapter.State();
        assertNotNull(state.show(1, page(100)));
        assertEquals(100, state.rowCount());
        assertThrows(IllegalArgumentException.class, () -> state.show(2, page(101)));
        assertThrows(IllegalArgumentException.class, () -> state.show(0, page(1)));
        List<DownloadsAdapter.Row> input = page(2);
        DownloadsAdapter.Row first = input.get(0);
        state.show(3, input);
        input.clear();
        assertEquals(2, state.rowCount());
        assertSame(first, state.require(state.frame(), 3, first.itemRef()));
        Object oldFrame = state.frame();
        state.show(4, page(1));
        assertFalse(state.current(oldFrame, 3));
        assertThrows(IllegalStateException.class, () -> state.require(oldFrame, 3, first.itemRef()));
        assertThrows(UnsupportedOperationException.class, () -> state.rows().clear());
    }
    @Test public void duplicateTokensHideAndRetireBehaveLikeOtherBoundedLists(){
        DownloadsAdapter.State state = new DownloadsAdapter.State();
        Object token = new Object();
        DownloadsAdapter.Row one = new DownloadsAdapter.Row(token, "a", 1, 0, running(), false);
        DownloadsAdapter.Row two = new DownloadsAdapter.Row(token, "b", 1, 0, running(), false);
        assertThrows(IllegalArgumentException.class, () -> state.show(1, Arrays.asList(one, two)));
        state.show(2, Arrays.asList(one));
        state.hide();
        assertEquals(0, state.rowCount());
        assertNull(state.rowAt(0));
        state.show(3, Arrays.asList(one));
        state.retire();
        state.retire();
        assertEquals(0, state.rowCount());
        assertThrows(IllegalStateException.class, () -> state.show(4, Arrays.asList(one)));
    }
    @Test public void foreignThreadCannotReadOrShowPage() throws Exception {
        DownloadsAdapter.State state = new DownloadsAdapter.State();
        state.show(1, page(1));
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            assertTrue(pool.submit(() -> { try { state.rows(); return false; } catch (IllegalStateException expected) { return true; } }).get(3, TimeUnit.SECONDS));
        } finally {
            pool.shutdown();
            assertTrue(pool.awaitTermination(3, TimeUnit.SECONDS));
        }
        assertEquals(1, state.rowCount());
    }
    @Test public void adapterExposesNoTransferPublishOpenOrDeleteOperation(){
        for (Method method : DownloadsAdapter.class.getDeclaredMethods()) {
            String name = method.getName().toLowerCase();
            for (String forbidden : new String[]{"load", "publish", "openfile", "install", "delete", "startdownload", "writestream", "save"}) {
                assertFalse("unexpected operation " + method.getName(), name.contains(forbidden));
            }
        }
        assertEquals(3, DownloadsAdapter.Actions.class.getDeclaredMethods().length);
    }
}
