package com.my.airmouse.input;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.my.airmouse.R;
import com.my.airmouse.data.PreferencesManager;

/**
 * Large touch surface with a dotted pattern background.
 * All gesture logic lives in {@link GestureController}.
 */
public class TouchpadView extends View implements GestureController.Callback {

    public interface TouchListener {
        void onTouchChanged(boolean down);
    }

    private GestureController controller;
    private TouchListener touchListener;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float radius;
    private final float cornerRadius;
    private float tx, ty;
    private float alpha; // 0..1 of the touch feedback circle
    private ValueAnimator fade;

    public TouchpadView(Context c, AttributeSet a) {
        super(c, a);
        float d = c.getResources().getDisplayMetrics().density;
        radius = 30f * d;
        cornerRadius = 24f * d;
        paint.setColor(ContextCompat.getColor(c, R.color.primary));
        dotPaint.setShader(makeDotShader(c, d));
    }

    /** A small tile with one dot in the middle, repeated over the whole surface. */
    private static Shader makeDotShader(Context c, float density) {
        int size = Math.round(20f * density);
        Bitmap tile = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(tile);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(ContextCompat.getColor(c, R.color.pad_dot));
        canvas.drawCircle(size / 2f, size / 2f, 1.5f * density, p);
        return new BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
    }

    public void setController(GestureController gc) {
        controller = gc;
        gc.setCallback(this);
    }

    public void setTouchListener(TouchListener l) { touchListener = l; }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (controller == null) return true;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (fade != null) fade.cancel();
                alpha = 1f;
                tx = e.getX();
                ty = e.getY();
                invalidate();
                break;
            case MotionEvent.ACTION_MOVE:
                tx = e.getX();
                ty = e.getY();
                invalidate();
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                fadeOut();
                break;
            default:
                break;
        }
        controller.onTouch(e);
        return true;
    }

    private void fadeOut() {
        if (fade != null) fade.cancel();
        fade = ValueAnimator.ofFloat(alpha, 0f);
        fade.setDuration(220);
        fade.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                alpha = (Float) a.getAnimatedValue();
                invalidate();
            }
        });
        fade.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // dotted pattern, inside the border of the rounded background
        float inset = 1.5f * getResources().getDisplayMetrics().density;
        rect.set(inset, inset, getWidth() - inset, getHeight() - inset);
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, dotPaint);

        if (alpha > 0f) {
            paint.setAlpha((int) (alpha * 70));
            canvas.drawCircle(tx, ty, radius, paint);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (fade != null) fade.cancel();
        super.onDetachedFromWindow();
    }

    // ---- GestureController.Callback ----
    @Override
    public void onTouchChanged(boolean down) {
        if (touchListener != null) touchListener.onTouchChanged(down);
    }

    @Override
    public void onClickFeedback() {
        if (PreferencesManager.get(getContext()).isHaptic()) {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
    }
}
