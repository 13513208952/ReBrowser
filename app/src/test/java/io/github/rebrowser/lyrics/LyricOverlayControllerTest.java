package io.github.rebrowser.lyrics;

import static org.junit.Assert.*;
import org.junit.Test;

/** Owner-thread mode reducer; window application is confirmed only by the native owner. */
public final class LyricOverlayControllerTest {
    @Test public void showLockAndConfirmGatePassthrough(){
        LyricOverlayController controller=new LyricOverlayController();
        assertEquals(LyricOverlayController.Mode.CLOSED,controller.state().mode());
        LyricOverlayController.State shown=controller.apply(LyricOverlayController.Event.SHOW,0,0);
        assertEquals(LyricOverlayController.Mode.VISIBLE_UNLOCKED,shown.mode());
        assertFalse(controller.passthroughEffective());
        controller.apply(LyricOverlayController.Event.LOCK,0,0);
        assertFalse(controller.passthroughEffective());
        long generation=controller.state().generation();
        controller.confirmWindowApplied(LyricOverlayController.Mode.VISIBLE_LOCKED,generation-1);
        assertFalse(controller.passthroughEffective()); // wrong generation
        controller.confirmWindowApplied(LyricOverlayController.Mode.VISIBLE_LOCKED,generation);
        assertTrue(controller.passthroughEffective());
    }
    @Test public void dragRequiresStartAndIsLostOnLockAndClose(){
        LyricOverlayController controller=new LyricOverlayController();
        controller.apply(LyricOverlayController.Event.SHOW,0,0);
        assertThrows(IllegalStateException.class,()->controller.apply(LyricOverlayController.Event.DRAG_MOVE,1,1));
        controller.apply(LyricOverlayController.Event.DRAG_START,5,6);
        LyricOverlayController.State moved=controller.apply(LyricOverlayController.Event.DRAG_MOVE,9,10);
        assertEquals(9,moved.x());assertEquals(10,moved.y());
        controller.apply(LyricOverlayController.Event.LOCK,-1,-1);
        assertFalse(controller.state().dragActive());
        controller.apply(LyricOverlayController.Event.CLOSE,0,0);
        assertEquals(LyricOverlayController.Mode.CLOSED,controller.state().mode());
        assertFalse(controller.passthroughEffective());
    }
    @Test public void lateConfirmationAfterCloseIsIgnored(){
        LyricOverlayController controller=new LyricOverlayController();
        controller.apply(LyricOverlayController.Event.SHOW,0,0);
        controller.apply(LyricOverlayController.Event.CLOSE,0,0);
        controller.confirmWindowApplied(LyricOverlayController.Mode.VISIBLE_LOCKED,1);
        assertFalse(controller.passthroughEffective());
        assertFalse(controller.visible());
    }
    @Test public void invalidCoordinatesAndRetirementRejectFurtherEvents(){
        LyricOverlayController controller=new LyricOverlayController();
        assertThrows(IllegalArgumentException.class,()->controller.apply(LyricOverlayController.Event.SHOW,100_000,0));
        controller.apply(LyricOverlayController.Event.SHOW,0,0);
        controller.retire();
        assertThrows(IllegalStateException.class,()->controller.apply(LyricOverlayController.Event.SHOW,0,0));
    }
}
