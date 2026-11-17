package com.my.airmouse.data;

import android.content.Context;

import com.my.airmouse.R;
import com.my.airmouse.bt.KeyboardReportManager;
import com.my.airmouse.input.KeyCodes;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Keyboard shortcuts per operating system, with user overrides stored locally. */
public final class ShortcutManager {

    public static final class Shortcut {
        public final String id;
        public final int labelRes;
        public final int mods;
        public final int key;

        Shortcut(String id, int labelRes, int mods, int key) {
            this.id = id;
            this.labelRes = labelRes;
            this.mods = mods;
            this.key = key;
        }
    }

    private static final int C = KeyCodes.MOD_CTRL, S = KeyCodes.MOD_SHIFT, A = KeyCodes.MOD_ALT, G = KeyCodes.MOD_GUI;
    private static final int KEY_A = 0x04, KEY_C = 0x06, KEY_D = 0x07, KEY_F = 0x09, KEY_L = 0x0F, KEY_Q = 0x14,
            KEY_S = 0x16, KEY_T = 0x17, KEY_V = 0x19, KEY_W = 0x1A, KEY_X = 0x1B, KEY_Y = 0x1C, KEY_Z = 0x1D,
            KEY_4 = 0x21, KEY_F4 = 0x3D, KEY_F11 = 0x44;

    private static ShortcutManager instance;

    public static synchronized ShortcutManager get(Context c) {
        if (instance == null) instance = new ShortcutManager(c.getApplicationContext());
        return instance;
    }

    private final PreferencesManager prefs;

    private ShortcutManager(Context c) {
        prefs = PreferencesManager.get(c);
    }

    private static String storeKey(PreferencesManager.Os os) {
        return "shortcuts_" + os.name();
    }

    private static List<Shortcut> defaults(PreferencesManager.Os os) {
        List<Shortcut> l = new ArrayList<>();
        if (os == PreferencesManager.Os.MAC) {
            l.add(new Shortcut("copy", R.string.sc_copy, G, KEY_C));
            l.add(new Shortcut("paste", R.string.sc_paste, G, KEY_V));
            l.add(new Shortcut("cut", R.string.sc_cut, G, KEY_X));
            l.add(new Shortcut("undo", R.string.sc_undo, G, KEY_Z));
            l.add(new Shortcut("redo", R.string.sc_redo, G | S, KEY_Z));
            l.add(new Shortcut("select_all", R.string.sc_select_all, G, KEY_A));
            l.add(new Shortcut("save", R.string.sc_save, G, KEY_S));
            l.add(new Shortcut("find", R.string.sc_find, G, KEY_F));
            l.add(new Shortcut("new_tab", R.string.sc_new_tab, G, KEY_T));
            l.add(new Shortcut("close_tab", R.string.sc_close_tab, G, KEY_W));
            l.add(new Shortcut("switch_app", R.string.sc_switch_app, G, KeyCodes.TAB));
            l.add(new Shortcut("show_desktop", R.string.sc_show_desktop, 0, KEY_F11));
            l.add(new Shortcut("lock", R.string.sc_lock, C | G, KEY_Q));
            l.add(new Shortcut("screenshot", R.string.sc_screenshot, G | S, KEY_4));
            l.add(new Shortcut("task_view", R.string.sc_task_view, C, KeyCodes.UP));
            l.add(new Shortcut("close_window", R.string.sc_quit_app, G, KEY_Q));
        } else {
            l.add(new Shortcut("copy", R.string.sc_copy, C, KEY_C));
            l.add(new Shortcut("paste", R.string.sc_paste, C, KEY_V));
            l.add(new Shortcut("cut", R.string.sc_cut, C, KEY_X));
            l.add(new Shortcut("undo", R.string.sc_undo, C, KEY_Z));
            l.add(new Shortcut("redo", R.string.sc_redo, C, KEY_Y));
            l.add(new Shortcut("select_all", R.string.sc_select_all, C, KEY_A));
            l.add(new Shortcut("save", R.string.sc_save, C, KEY_S));
            l.add(new Shortcut("find", R.string.sc_find, C, KEY_F));
            l.add(new Shortcut("new_tab", R.string.sc_new_tab, C, KEY_T));
            l.add(new Shortcut("close_tab", R.string.sc_close_tab, C, KEY_W));
            l.add(new Shortcut("switch_app", R.string.sc_switch_app, A, KeyCodes.TAB));
            l.add(new Shortcut("show_desktop", R.string.sc_show_desktop, G, KEY_D));
            l.add(new Shortcut("lock", R.string.sc_lock, G, KEY_L));
            l.add(new Shortcut("screenshot", R.string.sc_screenshot, G | S, KEY_S));
            l.add(new Shortcut("task_view", R.string.sc_task_view, G, KeyCodes.TAB));
            l.add(new Shortcut("close_window", R.string.sc_close_window, A, KEY_F4));
        }
        return l;
    }

    /** All shortcuts for this OS, with the user's changes applied. */
    public List<Shortcut> getAll(PreferencesManager.Os os) {
        List<Shortcut> base = defaults(os);
        JSONObject overrides = readOverrides(os);
        List<Shortcut> out = new ArrayList<>();
        for (Shortcut s : base) {
            JSONArray o = overrides.optJSONArray(s.id);
            if (o != null && o.length() == 2) {
                out.add(new Shortcut(s.id, s.labelRes, o.optInt(0), o.optInt(1)));
            } else {
                out.add(s);
            }
        }
        return out;
    }

    public void setBinding(PreferencesManager.Os os, String id, int mods, int key) {
        JSONObject overrides = readOverrides(os);
        try {
            JSONArray a = new JSONArray();
            a.put(mods);
            a.put(key);
            overrides.put(id, a);
        } catch (JSONException e) {
            return;
        }
        prefs.putRaw(storeKey(os), overrides.toString());
    }

    public void resetDefaults(PreferencesManager.Os os) {
        prefs.putRaw(storeKey(os), null);
    }

    private JSONObject readOverrides(PreferencesManager.Os os) {
        String raw = prefs.getRaw(storeKey(os));
        if (raw != null) {
            try {
                return new JSONObject(raw);
            } catch (JSONException e) {
                // fall through to empty
            }
        }
        return new JSONObject();
    }

    public void perform(KeyboardReportManager keyboard, Shortcut s) {
        keyboard.tap(s.mods, s.key);
    }

    /** Human readable, for example "Ctrl + Shift + S" (Cmd / Option names on macOS). */
    public static String describe(PreferencesManager.Os os, int mods, int key) {
        boolean mac = os == PreferencesManager.Os.MAC;
        StringBuilder sb = new StringBuilder();
        if ((mods & C) != 0) sb.append(mac ? "Control" : "Ctrl").append(" + ");
        if ((mods & A) != 0) sb.append(mac ? "Option" : "Alt").append(" + ");
        if ((mods & S) != 0) sb.append("Shift + ");
        if ((mods & G) != 0) sb.append(mac ? "Cmd" : "Win").append(" + ");
        sb.append(KeyCodes.nameOf(key));
        return sb.toString();
    }
}
