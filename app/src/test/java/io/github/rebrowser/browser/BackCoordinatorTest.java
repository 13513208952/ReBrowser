package io.github.rebrowser.browser;

import static org.junit.Assert.*;
import io.github.rebrowser.bootstrap.ComponentGate;
import io.github.rebrowser.bootstrap.ProcessRole;
import io.github.rebrowser.browser.BackCoordinator.*;
import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.Test;

/** Actual production Back reducer with synthetic native owned-layer callbacks, not Android UI. */
public final class BackCoordinatorTest {
    private static final class Window implements NativeWindow {
        final EnumSet<Layer> present=EnumSet.noneOf(Layer.class);
        Layer selected;
        Reporter reporter;
        int dispatched;
        Runnable duringQuery;
        Layer queryTrigger;
        Consumer<Reporter> effect=r->{};
        public boolean present(Layer layer){if(duringQuery!=null && (queryTrigger==null || queryTrigger==layer)){Runnable action=duringQuery;duringQuery=null;action.run();}return present.contains(layer);}
        public void perform(Layer layer,Reporter reporter){selected=layer;this.reporter=reporter;dispatched++;effect.accept(reporter);}
    }
    private static final class Fixture {
        final AtomicReference<ComponentGate.SourceState> source=new AtomicReference<>(ComponentGate.SourceState.TRUSTED);
        final ComponentGate gate=new ComponentGate(ProcessRole.MAIN,source::get);
        final ComponentGate.BusinessPermit permit=gate.requireBusiness();
        final Window window=new Window();
        final BackCoordinator back=new BackCoordinator(gate,window);
        Receipt request(){return back.request(back.presentationRevision(),permit);}
    }
    @Test public void nativePriorityChoosesExactlyOneLayer(){
        for(Layer expected:Layer.values()){
            Fixture f=new Fixture();for(Layer layer:Layer.values())if(layer.ordinal()>=expected.ordinal())f.window.present.add(layer);
            Receipt result=f.request();assertEquals(expected,result.layer());assertEquals(1,f.window.dispatched);assertEquals(Status.PENDING,result.status());
        }
    }
    @Test public void emptyWindowDoesNotDispatchOrInventConsumption(){
        Fixture f=new Fixture();long revision=f.back.presentationRevision();Receipt r=f.request();assertEquals(Status.NO_OP,r.status());assertNull(r.layer());assertEquals(0,f.window.dispatched);assertEquals(revision,f.back.presentationRevision());assertFalse(f.back.hasPendingLayer());
    }
    @Test public void pendingAndPresentationRotationNeverFallThroughOrClearActualPending(){
        Fixture f=new Fixture();f.window.present.addAll(EnumSet.of(Layer.IME,Layer.HISTORY));Receipt pending=f.request();long old=pending.reservedPresentationRevision();
        assertThrows(IllegalStateException.class,f::request);f.back.presentationChanged();assertTrue(f.back.hasPendingLayer());assertThrows(IllegalStateException.class,()->f.back.request(old,f.permit));
        assertTrue(f.window.reporter.reportConsumed());assertFalse(f.back.hasPendingLayer());assertFalse(f.window.reporter.snapshot().currentForPublication());assertEquals(Status.PENDING,pending.status());assertEquals(1,f.window.dispatched);
    }
    @Test public void sourceLockStillAllowsOwnedCancellationButNeverHistoryOrWorkspaceReopen(){
        Fixture f=new Fixture();f.source.set(ComponentGate.SourceState.LOCKED);f.window.present.addAll(EnumSet.of(Layer.HISTORY,Layer.WORKSPACE,Layer.WINDOW_CLOSE));
        assertEquals(Layer.WINDOW_CLOSE,f.back.request(f.back.presentationRevision(),null).layer());
        Fixture warning=new Fixture();warning.source.set(ComponentGate.SourceState.UNCONFIGURED);warning.window.present.addAll(EnumSet.of(Layer.SOURCE_WARNING_CANCEL,Layer.HISTORY));assertEquals(Layer.SOURCE_WARNING_CANCEL,warning.back.request(1,null).layer());
    }
    @Test public void businessLayerRequiresPermitFromThisGate(){
        Fixture f=new Fixture(),foreign=new Fixture();f.window.present.add(Layer.HISTORY);
        assertEquals(Status.NO_OP,f.back.request(1,foreign.permit).status());assertEquals(0,f.window.dispatched);assertEquals(Layer.HISTORY,f.request().layer());
    }
    @Test public void thrownDispatchIsUnknownAndRemainsOwnedUntilRealCallback(){
        Fixture f=new Fixture();f.window.present.addAll(EnumSet.of(Layer.MODAL,Layer.HISTORY));f.window.effect=r->{throw new IllegalStateException("synthetic lost reply");};
        Receipt unknown=f.request();assertEquals(Status.UNKNOWN,unknown.status());assertTrue(f.back.hasPendingLayer());assertEquals(1,f.window.dispatched);assertThrows(IllegalStateException.class,f::request);
        assertTrue(f.window.reporter.reportNoOp());assertEquals(Status.NO_OP,f.window.reporter.snapshot().status());assertFalse(f.back.hasPendingLayer());assertEquals(1,f.window.dispatched);
    }
    @Test public void firstNativeResultWinsEvenIfDispatchReturnLaterThrows(){
        Fixture f=new Fixture();f.window.present.add(Layer.FULLSCREEN);f.window.effect=r->{assertTrue(r.reportConsumed());throw new IllegalStateException("synthetic post-result failure");};
        assertEquals(Status.CONSUMED,f.request().status());assertFalse(f.window.reporter.reportFailed());assertFalse(f.window.reporter.reportNoOp());assertFalse(f.back.hasPendingLayer());
    }
    @Test public void noReentrantRequestBeforeOrAfterSynchronousResult(){
        Fixture f=new Fixture();f.window.present.addAll(EnumSet.of(Layer.SELECTION,Layer.HISTORY));f.window.effect=r->{assertThrows(IllegalStateException.class,f::request);r.reportConsumed();assertThrows(IllegalStateException.class,f::request);};
        assertEquals(Status.CONSUMED,f.request().status());assertEquals(1,f.window.dispatched);
    }
    @Test public void changedOrRetiredDuringSelectionCannotDispatchWrongLayer(){
        Fixture changed=new Fixture();changed.window.present.add(Layer.IME);changed.window.duringQuery=changed.back::presentationChanged;assertThrows(IllegalStateException.class,changed::request);assertEquals(0,changed.window.dispatched);
        Fixture retired=new Fixture();retired.window.present.add(Layer.IME);retired.window.duringQuery=retired.back::retireWindowOwner;assertThrows(IllegalStateException.class,retired::request);assertEquals(0,retired.window.dispatched);
        Fixture locked=new Fixture();locked.window.present.add(Layer.HISTORY);locked.window.queryTrigger=Layer.HISTORY;locked.window.duringQuery=()->locked.source.set(ComponentGate.SourceState.LOCKED);assertThrows(SecurityException.class,locked::request);assertEquals(0,locked.window.dispatched);
    }
    @Test public void retiredWindowStillAcceptsOriginalSettlementWithoutNewPublication(){
        Fixture f=new Fixture();f.window.present.add(Layer.POPUP);f.request();f.back.retireWindowOwner();assertTrue(f.back.hasPendingLayer());assertThrows(IllegalStateException.class,f::request);
        assertTrue(f.window.reporter.reportConsumed());assertFalse(f.window.reporter.snapshot().currentForPublication());assertFalse(f.back.hasPendingLayer());
    }
    @Test public void wrongThreadCannotReportActualNativeLayerResult()throws Exception{
        Fixture f=new Fixture();f.window.present.add(Layer.IME);f.request();ExecutorService pool=Executors.newSingleThreadExecutor();
        try{assertTrue(pool.submit(()->{try{f.window.reporter.reportConsumed();return false;}catch(IllegalStateException expected){return true;}}).get(2,TimeUnit.SECONDS));}
        finally{pool.shutdownNow();assertTrue(pool.awaitTermination(2,TimeUnit.SECONDS));}
        assertTrue(f.back.hasPendingLayer());assertTrue(f.window.reporter.reportConsumed());
    }
    @Test public void revisionExhaustionFailsBeforeDispatch()throws Exception{
        Fixture f=new Fixture();f.window.present.add(Layer.IME);Field revision=BackCoordinator.class.getDeclaredField("revision");revision.setAccessible(true);revision.setLong(f.back,Long.MAX_VALUE);
        assertThrows(IllegalStateException.class,f::request);assertEquals(0,f.window.dispatched);assertFalse(f.back.hasPendingLayer());
    }
}
