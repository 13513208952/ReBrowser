package io.github.reoutlook;

import static org.junit.Assert.*;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import java.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowSystemClock;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public final class ReBrowserClipboardGestureTest {
    static MotionEvent event(long down, int action, float x, float y, int source, int flags, int count, int buttons) {
        MotionEvent.PointerProperties[] properties=new MotionEvent.PointerProperties[count];
        MotionEvent.PointerCoords[] coords=new MotionEvent.PointerCoords[count];
        for(int i=0;i<count;i++) {
            properties[i]=new MotionEvent.PointerProperties();properties[i].id=i;
            properties[i].toolType=source==InputDevice.SOURCE_MOUSE?MotionEvent.TOOL_TYPE_MOUSE:MotionEvent.TOOL_TYPE_FINGER;
            coords[i]=new MotionEvent.PointerCoords();coords[i].x=x;coords[i].y=y;coords[i].pressure=1;
        }
        return MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,count,properties,coords,0,buttons,1,1,0,0,source,flags);
    }
    static void touch(ReBrowserClipboardGesture gate,long down,int action,float x,int flags,int count) {
        MotionEvent e=event(down,action,x,20,InputDevice.SOURCE_TOUCHSCREEN,flags,count,0);
        try {gate.touch(e,8);} finally {e.recycle();}
    }
    static void tap(ReBrowserClipboardGesture gate) {
        long now=SystemClock.uptimeMillis();touch(gate,now,MotionEvent.ACTION_DOWN,20,0,1);
        touch(gate,now,MotionEvent.ACTION_UP,20,0,1);
    }
    @Test public void noInputOrTouchDownCannotSupplyCreditAndTapIsSingleUse() {
        ReBrowserClipboardGesture gate=new ReBrowserClipboardGesture();assertFalse(gate.consume());
        long now=SystemClock.uptimeMillis();touch(gate,now,MotionEvent.ACTION_DOWN,20,0,1);assertFalse(gate.consume());
        touch(gate,now,MotionEvent.ACTION_UP,20,0,1);assertTrue(gate.consume());assertFalse(gate.consume());
    }
    @Test public void scrollingCancellationAndMultiplePointersCannotSupplyCredit() {
        for(int mode=0;mode<3;mode++) {
            ReBrowserClipboardGesture gate=new ReBrowserClipboardGesture();long now=SystemClock.uptimeMillis();
            touch(gate,now,MotionEvent.ACTION_DOWN,20,0,1);
            touch(gate,now,mode==0?MotionEvent.ACTION_MOVE:mode==1?MotionEvent.ACTION_CANCEL:MotionEvent.ACTION_POINTER_DOWN,
                    mode==0?60:20,0,mode==2?2:1);
            touch(gate,now,MotionEvent.ACTION_UP,20,0,1);assertFalse(gate.consume());
        }
    }
    @Test public void obscuredOrNonFiniteInputAndUnsupportedSourcesFailClosed() {
        for(int flag:new int[]{MotionEvent.FLAG_WINDOW_IS_OBSCURED,MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED}) {
            ReBrowserClipboardGesture gate=new ReBrowserClipboardGesture();long now=SystemClock.uptimeMillis();
            touch(gate,now,MotionEvent.ACTION_DOWN,20,flag,1);touch(gate,now,MotionEvent.ACTION_UP,20,0,1);assertFalse(gate.consume());
        }
        ReBrowserClipboardGesture gate=new ReBrowserClipboardGesture();long now=SystemClock.uptimeMillis();
        touch(gate,now,MotionEvent.ACTION_DOWN,Float.NaN,0,1);touch(gate,now,MotionEvent.ACTION_UP,20,0,1);assertFalse(gate.consume());
        MotionEvent e=event(now,MotionEvent.ACTION_DOWN,20,20,0,0,1,0);gate.touch(e,8);e.recycle();assertFalse(gate.consume());
    }
    @Test public void expiryAndContextInvalidationCannotBeReplayed() {
        ReBrowserClipboardGesture gate=new ReBrowserClipboardGesture();tap(gate);
        ShadowSystemClock.advanceBy(Duration.ofMillis(ReBrowserClipboardGesture.WINDOW_MS+1));assertFalse(gate.consume());
        tap(gate);long epoch=gate.contextGeneration();gate.invalidate();assertTrue(gate.contextGeneration()>epoch);assertFalse(gate.consume());
    }
    @Test public void primaryMousePressDoesNotCreateAnotherCreditOnRelease() {
        ReBrowserClipboardGesture gate=new ReBrowserClipboardGesture();long now=SystemClock.uptimeMillis();
        MotionEvent down=event(now,MotionEvent.ACTION_DOWN,20,20,InputDevice.SOURCE_MOUSE,0,1,MotionEvent.BUTTON_PRIMARY);
        gate.touch(down,8);down.recycle();assertTrue(gate.consume());
        MotionEvent up=event(now,MotionEvent.ACTION_UP,20,20,InputDevice.SOURCE_MOUSE,0,1,0);
        gate.touch(up,8);up.recycle();assertFalse(gate.consume());
    }
    @Test public void keyboardActivationIsSingleUseAndRepeatsOrModifiersDoNotRefill() {
        ReBrowserClipboardGesture gate=new ReBrowserClipboardGesture();long now=SystemClock.uptimeMillis();
        gate.key(new KeyEvent(now,now,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_ENTER,0));assertTrue(gate.consume());
        gate.key(new KeyEvent(now,now,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_ENTER,1));assertFalse(gate.consume());
        gate.key(new KeyEvent(now,now,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_SHIFT_LEFT,0));assertFalse(gate.consume());
        gate.key(new KeyEvent(now,now,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_ESCAPE,0));assertFalse(gate.consume());
        gate.key(new KeyEvent(now,now,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_HOME,0));assertFalse(gate.consume());
    }
}
