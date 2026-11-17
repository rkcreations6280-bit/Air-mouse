package com.my.airmouse.ui;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.my.airmouse.R;

/** Shows one of the five tab fragments and keeps the others alive (hidden) so they keep their state. */
public final class NavigationManager {

    private static final String[] TAGS = {"devices", "touchpad", "keyboard", "modes", "settings"};

    private final FragmentManager fm;
    private final int containerId;

    public NavigationManager(FragmentManager fm, int containerId) {
        this.fm = fm;
        this.containerId = containerId;
    }

    public void navigate(int menuItemId) {
        String tag = tagFor(menuItemId);
        FragmentTransaction tx = fm.beginTransaction().setReorderingAllowed(true);
        for (String t : TAGS) {
            Fragment f = fm.findFragmentByTag(t);
            if (f != null && !t.equals(tag)) tx.hide(f);
        }
        Fragment target = fm.findFragmentByTag(tag);
        if (target == null) tx.add(containerId, create(tag), tag);
        else tx.show(target);
        tx.commit();
    }

    private static String tagFor(int id) {
        if (id == R.id.nav_touchpad) return TAGS[1];
        if (id == R.id.nav_keyboard) return TAGS[2];
        if (id == R.id.nav_modes) return TAGS[3];
        if (id == R.id.nav_settings) return TAGS[4];
        return TAGS[0];
    }

    private static Fragment create(String tag) {
        switch (tag) {
            case "touchpad": return new TouchpadFragment();
            case "keyboard": return new KeyboardFragment();
            case "modes": return new ModesFragment();
            case "settings": return new SettingsFragment();
            default: return new DevicesFragment();
        }
    }
}
