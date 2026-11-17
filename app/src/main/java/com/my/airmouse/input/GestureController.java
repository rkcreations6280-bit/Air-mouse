package com.my.airmouse.input;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import com.my.airmouse.bt.MouseReportManager;
import com.my.airmouse.data.PreferencesManager;

/**
 * Turns touch events into mouse actions.
 * One finger: move. Tap: left click. Tap again quickly: double click.
 * Tap then touch and move (within a short time): drag with the left button held.
 * Two fingers: scroll (vertical and horizontal). Two finger tap: right click.
 */
public final class GestureController {

    public interface Callback {
        void onTouchChanged(boolean down);

        /** A click happened (for haptic feedback). */
        void onClickFeedback();
    }

    private static final long TAP_MS = 250;
    private static final long DOUBLE_TAP_WINDOW_MS = 280;

    private final MouseReportManager mouse;
    private final PreferencesManager prefs;
    private final float slop;
    private final float scrollStepBase;

    private Callback callback;
    private boolean airMode;

    private float lastX, lastY, lastAvgX, lastAvgY;
    private float totalMove;
    private float remX, remY, accX, accY;
    private long downTime, lastTapUp;
    private boolean multi, dragCandidate, dragging;

    public GestureController(Context c, MouseReportManager mouse, PreferencesManager prefs) {
        this.mouse = mouse;
        this.prefs = prefs;
        this.slop = ViewConfiguration.get(c).getScaledTouchSlop();
        this.scrollStepBase = 24f * c.getResources().getDisplayMetrics().density;
    }

    public void setCallback(Callback cb) { callback = cb; }

    /** In air mode the finger does not move the pointer (the gyroscope does). Taps and scrolling still work. */
    public void setAirMode(boolean air) { airMode = air; }

    public void onTouch(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = e.getX();
                lastY = e.getY();
                totalMove = 0;
                remX = remY = accX = accY = 0;
                multi = false;
                dragging = false;
                downTime = e.getEventTime();
                dragCandidate = prefs.isTapDrag() && lastTapUp > 0
                        && downTime - lastTapUp < DOUBLE_TAP_WINDOW_MS;
                if (callback != null) callback.onTouchChanged(true);
                break;

            case MotionEvent.ACTION_POINTER_DOWN:
                if (e.getPointerCount() == 2) {
                    multi = true;
                    dragCandidate = false;
                    lastAvgX = avgX(e);
                    lastAvgY = avgY(e);
                    accX = accY = 0;
                }
                break;

            case MotionEvent.ACTION_POINTER_UP:
                if (e.getPointerCount() == 2) {
                    int keep = e.getActionIndex() == 0 ? 1 : 0;
                    lastX = e.getX(keep);
                    lastY = e.getY(keep);
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (e.getPointerCount() >= 2) scroll(e);
                else if (!multi) singleFinger(e);
                break;

            case MotionEvent.ACTION_UP:
                finish(e);
                break;

            case MotionEvent.ACTION_CANCEL:
                cancel();
                if (callback != null) callback.onTouchChanged(false);
                break;
            default:
                break;
        }
    }

    /** Releases anything held by a gesture. Call when the screen is left. */
    public void cancel() {
        if (dragging) {
            mouse.release(MouseReportManager.LEFT);
            dragging = false;
        }
        dragCandidate = false;
        lastTapUp = 0;
    }

    private void scroll(MotionEvent e) {
        float ax = avgX(e), ay = avgY(e);
        float dx = ax - lastAvgX, dy = ay - lastAvgY;
        lastAvgX = ax;
        lastAvgY = ay;
        totalMove += Math.abs(dx) + Math.abs(dy);
        float step = scrollStepBase / Math.max(0.1f, prefs.getScrollSpeed());
        accX += dx;
        accY += dy;
        int sy = (int) (accY / step);
        int sx = (int) (accX / step);
        if (sy != 0 || sx != 0) {
            accY -= sy * step;
            accX -= sx * step;
            int sign = prefs.isNaturalScroll() ? 1 : -1;
            mouse.scroll(sy * sign, -sx * sign);
        }
    }

    private void singleFinger(MotionEvent e) {
        float dx = e.getX() - lastX, dy = e.getY() - lastY;
        lastX = e.getX();
        lastY = e.getY();
        totalMove += Math.abs(dx) + Math.abs(dy);
        if (dragCandidate && !dragging && totalMove > slop) {
            dragging = true;
            mouse.press(MouseReportManager.LEFT);
        }
        if (!airMode) {
            float speed = prefs.getPointerSensitivity()
                    * (1f + Math.min(1.5f, (Math.abs(dx) + Math.abs(dy)) / 25f));
            remX += dx * speed;
            remY += dy * speed;
            int ix = (int) remX, iy = (int) remY;
            remX -= ix;
            remY -= iy;
            if (ix != 0 || iy != 0) mouse.move(ix, iy);
        }
    }

    private void finish(MotionEvent e) {
        if (callback != null) callback.onTouchChanged(false);
        if (dragging) {
            mouse.release(MouseReportManager.LEFT);
            dragging = false;
            lastTapUp = 0;
        } else if (totalMove < slop && e.getEventTime() - downTime < TAP_MS) {
            if (multi) {
                mouse.click(MouseReportManager.RIGHT);
                lastTapUp = 0;
                feedback();
            } else if (prefs.isTapToClick()) {
                mouse.click(MouseReportManager.LEFT);
                lastTapUp = e.getEventTime();
                feedback();
            }
        } else {
            lastTapUp = 0;
        }
        dragCandidate = false;
    }

    private void feedback() {
        if (callback != null) callback.onClickFeedback();
    }

    private static float avgX(MotionEvent e) {
        float sum = 0;
        for (int i = 0; i < e.getPointerCount(); i++) sum += e.getX(i);
        return sum / e.getPointerCount();
    }

    private static float avgY(MotionEvent e) {
        float sum = 0;
        for (int i = 0; i < e.getPointerCount(); i++) sum += e.getY(i);
        return sum / e.getPointerCount();
    }
}
