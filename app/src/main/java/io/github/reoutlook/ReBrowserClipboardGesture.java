package io.github.reoutlook;

import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

/** Native input credit, never created from JavaScript or persisted with a site grant. */
final class ReBrowserClipboardGesture {
    static final long WINDOW_MS = 5_000L;
    private long armedAt = -1L;
    private boolean tracking;
    private boolean mouse;
    private int pointerId;
    private long downTime;
    private float downX;
    private float downY;
    private long contextGeneration;

    long contextGeneration() { return contextGeneration; }

    void invalidate() {
        contextGeneration++;
        resetInput();
    }

    private void resetInput() {
        armedAt = -1L;
        tracking = false;
    }

    void touch(MotionEvent event, int touchSlop) {
        if ((event.getFlags() & (MotionEvent.FLAG_WINDOW_IS_OBSCURED
                | MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED)) != 0
                || event.getPointerCount() != 1
                || !Float.isFinite(event.getX()) || !Float.isFinite(event.getY())
                || !(event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN)
                        || event.isFromSource(InputDevice.SOURCE_MOUSE)
                        || event.isFromSource(InputDevice.SOURCE_STYLUS))) {
            resetInput();
            return;
        }
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            resetInput();
            tracking = true;
            mouse = event.isFromSource(InputDevice.SOURCE_MOUSE);
            pointerId = event.getPointerId(0);
            downTime = event.getDownTime();
            downX = event.getX();
            downY = event.getY();
            // Match mouse press activation without granting a second credit on release.
            if (mouse && (event.getButtonState() & MotionEvent.BUTTON_PRIMARY) != 0) {
                arm(event.getEventTime());
            } else if (mouse) {
                tracking = false;
            }
        } else if (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP) {
            if (!tracking || pointerId != event.getPointerId(0) || downTime != event.getDownTime()
                    || Math.hypot(event.getX() - downX, event.getY() - downY) > touchSlop) {
                resetInput();
                return;
            }
            if (action == MotionEvent.ACTION_UP) {
                tracking = false;
                if (!mouse) arm(event.getEventTime());
            }
        } else {
            resetInput();
        }
    }

    void key(KeyEvent event) {
        if (event.isCanceled()) {
            resetInput();
            return;
        }
        if (event.getAction() != KeyEvent.ACTION_DOWN || event.getRepeatCount() != 0
                || event.getKeyCode() == KeyEvent.KEYCODE_UNKNOWN
                || event.getKeyCode() == KeyEvent.KEYCODE_ESCAPE
                || KeyEvent.isModifierKey(event.getKeyCode()) || event.isSystem()) return;
        arm(event.getEventTime());
    }

    private void arm(long eventTime) {
        long now = SystemClock.uptimeMillis();
        armedAt = eventTime >= 0 && eventTime <= now && now - eventTime <= WINDOW_MS
                ? eventTime : -1L;
    }

    boolean consume() {
        long now = SystemClock.uptimeMillis();
        boolean valid = armedAt >= 0 && now >= armedAt && now - armedAt <= WINDOW_MS;
        armedAt = -1L;
        return valid;
    }
}
