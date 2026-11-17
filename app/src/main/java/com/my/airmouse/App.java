package com.my.airmouse;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import com.my.airmouse.bt.BluetoothHidController;
import com.my.airmouse.data.PreferencesManager;

/**
 * Counts visible activities. When the first one appears the Bluetooth connection is started and
 * the last computer is connected right away. When the last one goes away the connection is shut
 * down (after a short grace period) so the phone does not waste battery.
 */
public class App extends Application {

    private int started;

    @Override
    public void onCreate() {
        super.onCreate();
        PreferencesManager.get(this).applyTheme();

        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityStarted(Activity activity) {
                started++;
                if (started == 1) BluetoothHidController.get(App.this).onAppForeground();
            }

            @Override
            public void onActivityStopped(Activity activity) {
                started = Math.max(0, started - 1);
                if (started == 0) BluetoothHidController.get(App.this).onAppBackground();
            }

            @Override public void onActivityCreated(Activity a, Bundle b) {}

            @Override public void onActivityResumed(Activity a) {}

            @Override public void onActivityPaused(Activity a) {}

            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}

            @Override public void onActivityDestroyed(Activity a) {}
        });
    }
}
