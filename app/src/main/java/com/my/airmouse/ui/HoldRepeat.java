package com.my.airmouse.ui;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;

/** Runs an action on touch down, then repeats it while the finger stays down. */
public final class HoldRepeat {

    private static final long FIRST_DELAY_MS = 350;
    private static final long REPEAT_MS = 80;

    private HoldRepeat() {}

    @SuppressLint("ClickableViewAccessibility")
    public static void bind(final View v, final Runnable action) {
        final Handler handler = new Handler(Looper.getMainLooper());
        final Runnable repeat = new Runnable() {
            @Override
            public void run() {
                action.run();
                handler.postDelayed(this, REPEAT_MS);
            }
        };
        v.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        view.setPressed(true);
                        action.run();
                        handler.postDelayed(repeat, FIRST_DELAY_MS);
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        view.setPressed(false);
                        handler.removeCallbacks(repeat);
                        return true;
                    default:
                        return true;
                }
            }
        });
    }
}
