package com.my.airmouse.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

/** All app settings in one place (SharedPreferences). */
public final class PreferencesManager {

    public enum Os { WINDOWS, MAC }

    public static final int THEME_SYSTEM = 0, THEME_LIGHT = 1, THEME_DARK = 2;
    public static final int ORIENTATION_AUTO = 0, ORIENTATION_PORTRAIT = 1, ORIENTATION_LANDSCAPE = 2;

    private static final String FILE = "airmouse_prefs";
    private static PreferencesManager instance;

    public static synchronized PreferencesManager get(Context c) {
        if (instance == null) instance = new PreferencesManager(c.getApplicationContext());
        return instance;
    }

    private final SharedPreferences sp;

    private PreferencesManager(Context c) {
        sp = c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    // ---- first run ----
    public boolean isOnboardingDone() { return sp.getBoolean("onboarding_done", false); }

    public void setOnboardingDone(boolean v) { sp.edit().putBoolean("onboarding_done", v).apply(); }

    /** Null until the user has picked an operating system. */
    public Os getOs() {
        String s = sp.getString("os", null);
        if (s == null) return null;
        try {
            return Os.valueOf(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public Os getOsOrDefault() {
        Os os = getOs();
        return os != null ? os : Os.WINDOWS;
    }

    public void setOs(Os os) { sp.edit().putString("os", os.name()).apply(); }

    // ---- general ----
    public int getTheme() { return sp.getInt("theme", THEME_SYSTEM); }

    public void setTheme(int v) { sp.edit().putInt("theme", v).apply(); }

    public void applyTheme() {
        int mode;
        switch (getTheme()) {
            case THEME_LIGHT: mode = AppCompatDelegate.MODE_NIGHT_NO; break;
            case THEME_DARK: mode = AppCompatDelegate.MODE_NIGHT_YES; break;
            default: mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM; break;
        }
        AppCompatDelegate.setDefaultNightMode(mode);
    }

    public int getOrientation() { return sp.getInt("orientation", ORIENTATION_AUTO); }

    public void setOrientation(int v) { sp.edit().putInt("orientation", v).apply(); }

    public boolean isHaptic() { return sp.getBoolean("haptic", true); }

    public void setHaptic(boolean v) { sp.edit().putBoolean("haptic", v).apply(); }

    // ---- touchpad ----
    public float getPointerSensitivity() { return sp.getFloat("pointer_sensitivity", 1.5f); }

    public void setPointerSensitivity(float v) { sp.edit().putFloat("pointer_sensitivity", v).apply(); }

    public float getScrollSpeed() { return sp.getFloat("scroll_speed", 1.0f); }

    public void setScrollSpeed(float v) { sp.edit().putFloat("scroll_speed", v).apply(); }

    public boolean isTapToClick() { return sp.getBoolean("tap_to_click", true); }

    public void setTapToClick(boolean v) { sp.edit().putBoolean("tap_to_click", v).apply(); }

    public boolean isTapDrag() { return sp.getBoolean("tap_drag", true); }

    public void setTapDrag(boolean v) { sp.edit().putBoolean("tap_drag", v).apply(); }

    public boolean isNaturalScroll() { return sp.getBoolean("natural_scroll", true); }

    public void setNaturalScroll(boolean v) { sp.edit().putBoolean("natural_scroll", v).apply(); }

    // ---- keyboard ----
    public boolean isKeyRepeat() { return sp.getBoolean("key_repeat", true); }

    public void setKeyRepeat(boolean v) { sp.edit().putBoolean("key_repeat", v).apply(); }

    // ---- bluetooth ----
    public boolean isAutoReconnect() { return sp.getBoolean("auto_reconnect", true); }

    public void setAutoReconnect(boolean v) { sp.edit().putBoolean("auto_reconnect", v).apply(); }

    /** Connect to the last computer as soon as the app opens. */
    public boolean isAutoConnect() { return sp.getBoolean("auto_connect", true); }

    public void setAutoConnect(boolean v) { sp.edit().putBoolean("auto_connect", v).apply(); }

    // ---- modes ----
    public int getMode() { return sp.getInt("mode", 0); }

    public void setMode(int v) { sp.edit().putInt("mode", v).apply(); }

    // ---- raw storage for other classes (saved devices, shortcuts) ----
    public String getRaw(String key) { return sp.getString(key, null); }

    public void putRaw(String key, String value) {
        if (value == null) sp.edit().remove(key).apply();
        else sp.edit().putString(key, value).apply();
    }
}
