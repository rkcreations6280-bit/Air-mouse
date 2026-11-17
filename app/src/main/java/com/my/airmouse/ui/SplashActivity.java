package com.my.airmouse.ui;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import com.my.airmouse.R;
import com.my.airmouse.data.PreferencesManager;

/** Branded start screen. Routes to onboarding, OS selection or the main screen. */
public class SplashActivity extends BaseActivity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable go = new Runnable() {
        @Override
        public void run() {
            route();
        }
    };
    private ObjectAnimator[] rings = new ObjectAnimator[2];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        applyInsets(findViewById(R.id.splash_root), true);

        pulse(findViewById(R.id.ring1), 0, 0);
        pulse(findViewById(R.id.ring2), 1, 500);

        View logo = findViewById(R.id.splash_logo);
        logo.setScaleX(0.6f);
        logo.setScaleY(0.6f);
        logo.setAlpha(0f);
        logo.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(500)
                .setInterpolator(new DecelerateInterpolator()).start();

        handler.postDelayed(go, 700);
    }

    private void pulse(View ring, int slot, long delay) {
        ObjectAnimator a = ObjectAnimator.ofPropertyValuesHolder(ring,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 0.7f, 1.6f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.7f, 1.6f),
                PropertyValuesHolder.ofFloat(View.ALPHA, 0.8f, 0f));
        a.setDuration(1400);
        a.setStartDelay(delay);
        a.setRepeatCount(ObjectAnimator.INFINITE);
        a.start();
        rings[slot] = a;
    }

    private void route() {
        PreferencesManager p = PreferencesManager.get(this);
        Class<?> next;
        if (!p.isOnboardingDone()) next = OnboardingActivity.class;
        else if (p.getOs() == null) next = OsSelectActivity.class;
        else next = MainActivity.class;
        startActivity(new Intent(this, next));
        finish();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(go);
        for (ObjectAnimator a : rings) if (a != null) a.cancel();
        super.onDestroy();
    }
}
