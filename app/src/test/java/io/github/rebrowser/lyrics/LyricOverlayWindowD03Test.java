package io.github.rebrowser.lyrics;

import static org.junit.Assert.*;
import org.junit.Test;

/** Pure drag arithmetic; the real system-window placement remains with the native owner. */
public final class LyricOverlayWindowD03Test {
    @Test public void dragClampsInsideOwnDisplay(){
        LyricOverlayWindow.DragModel model=new LyricOverlayWindow.DragModel(1080,2400,360,200);
        model.start(1,0,0,100,100);
        model.move(1,400,300);
        assertEquals(300,model.x());
        assertEquals(200,model.y());
        model.move(1,20_000,20_000);
        assertEquals(720,model.x());
        assertEquals(2200,model.y());
        model.end();
        assertFalse(model.active());
    }
    @Test public void multiPointerCancelsWithoutApplyingPartialMove(){
        LyricOverlayWindow.DragModel model=new LyricOverlayWindow.DragModel(1080,2400,360,200);
        model.start(1,0,0,10,10);
        model.move(1,50,60);
        model.move(2,80,90);
        assertTrue(model.cancelled());
        assertEquals(40,model.x());
        assertEquals(50,model.y());
        assertFalse(model.active());
    }
    @Test public void abortingMultiTouchIsIdempotentAndCannotReviveDrag(){
        LyricOverlayWindow.DragModel model=new LyricOverlayWindow.DragModel(1080,2400,360,200);
        model.start(1,0,0,10,10);
        model.move(2,80,90);
        model.cancel(); // synthetic ACTION_POINTER_UP/ACTION_CANCEL after already cancelled
        assertTrue(model.cancelled());
        assertFalse(model.active());
        assertThrows(IllegalStateException.class,()->model.end()); // UI must NOT call end
        model.start(1,0,0,15,15);
        assertTrue(model.active());
        assertFalse(model.cancelled());
        model.cancel();model.cancel();
        assertFalse(model.active());
    }
    @Test public void invalidGeometryAndUnstartedMoveRejected(){
        assertThrows(IllegalArgumentException.class,()->new LyricOverlayWindow.DragModel(100,100,200,50));
        assertThrows(IllegalArgumentException.class,()->new LyricOverlayWindow.DragModel(0,100,10,10));
        LyricOverlayWindow.DragModel model=new LyricOverlayWindow.DragModel(1080,2400,360,200);
        assertThrows(IllegalStateException.class,()->model.move(1,1,1));
        assertThrows(IllegalStateException.class,()->model.end());
        assertThrows(IllegalArgumentException.class,()->model.start(2,0,0,0,0));
    }
    @Test public void displayProjectionBoundsTextAndLabelsMode(){
        LyricOverlayWindow.Display display=new LyricOverlayWindow.Display("当前","下一句",LyricOverlayController.Mode.VISIBLE_LOCKED);
        assertEquals("当前",display.currentLine());
        assertTrue(display.modeLabel().contains("已锁定"));
        assertThrows(IllegalArgumentException.class,()->new LyricOverlayWindow.Display(new String(new char[513]).replace('\0','x'),"n",LyricOverlayController.Mode.VISIBLE_UNLOCKED));
        assertThrows(IllegalArgumentException.class,()->new LyricOverlayWindow.Display("c","n",null));
    }

    @Test public void foreignPointerCancelsAndCapturedPointerEnds(){
        LyricOverlayWindow.DragModel model=new LyricOverlayWindow.DragModel(1080,2400,360,200);
        model.start(7,1,0,0,10,10);
        assertEquals(7,model.pointerId());
        model.movePointer(8,1,50,60);
        assertTrue(model.cancelled());
        assertFalse(model.active());
        LyricOverlayWindow.DragModel second=new LyricOverlayWindow.DragModel(1080,2400,360,200);
        second.start(3,1,0,0,10,10);
        second.movePointer(3,1,60,70);
        assertEquals(50,second.x());
        second.endPointer(3);
        assertFalse(second.active());
    }
}
