package com.my.airmouse.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.my.airmouse.R;
import com.my.airmouse.data.PreferencesManager;
import com.my.airmouse.data.ShortcutManager;
import com.my.airmouse.input.KeyCodes;

import java.util.List;

/** Shows every shortcut for the chosen OS and lets the user assign a new key combination. */
public class KeyBindingActivity extends BaseActivity {

    private PreferencesManager prefs;
    private ShortcutManager shortcuts;
    private LinearLayout list;
    private TextView osLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_key_binding);
        applyInsets(findViewById(R.id.bind_root), true);

        prefs = PreferencesManager.get(this);
        shortcuts = ShortcutManager.get(this);
        list = findViewById(R.id.bind_list);
        osLabel = findViewById(R.id.bind_os);

        findViewById(R.id.bind_back).setOnClickListener(v -> finish());
        findViewById(R.id.bind_reset).setOnClickListener(v ->
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.restore_defaults)
                        .setMessage(R.string.restore_defaults_message)
                        .setPositiveButton(R.string.restore, (d, w) -> {
                            shortcuts.resetDefaults(prefs.getOsOrDefault());
                            refresh();
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .show());
        refresh();
    }

    private void refresh() {
        final PreferencesManager.Os os = prefs.getOsOrDefault();
        osLabel.setText(getString(R.string.bindings_for, getString(
                os == PreferencesManager.Os.MAC ? R.string.os_mac : R.string.os_windows)));
        list.removeAllViews();
        List<ShortcutManager.Shortcut> all = shortcuts.getAll(os);
        for (final ShortcutManager.Shortcut s : all) {
            View row = getLayoutInflater().inflate(R.layout.item_binding_row, list, false);
            ((TextView) row.findViewById(R.id.bind_name)).setText(s.labelRes);
            ((TextView) row.findViewById(R.id.bind_keys)).setText(ShortcutManager.describe(os, s.mods, s.key));
            row.setOnClickListener(v -> edit(os, s));
            list.addView(row);
        }
    }

    private void edit(final PreferencesManager.Os os, final ShortcutManager.Shortcut s) {
        final boolean mac = os == PreferencesManager.Os.MAC;
        int pad = Math.round(20 * getResources().getDisplayMetrics().density);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad / 2, pad, 0);

        final CheckBox ctrl = check(mac ? "Control" : "Ctrl", (s.mods & KeyCodes.MOD_CTRL) != 0);
        final CheckBox alt = check(mac ? "Option" : "Alt", (s.mods & KeyCodes.MOD_ALT) != 0);
        final CheckBox shift = check("Shift", (s.mods & KeyCodes.MOD_SHIFT) != 0);
        final CheckBox gui = check(mac ? "Cmd" : "Win", (s.mods & KeyCodes.MOD_GUI) != 0);
        box.addView(ctrl);
        box.addView(alt);
        box.addView(shift);
        box.addView(gui);

        final Spinner key = new Spinner(this);
        key.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, KeyCodes.NAMES));
        key.setSelection(KeyCodes.indexOf(s.key));
        box.addView(key);

        new MaterialAlertDialogBuilder(this)
                .setTitle(s.labelRes)
                .setView(box)
                .setPositiveButton(R.string.save, (d, w) -> {
                    int mods = (ctrl.isChecked() ? KeyCodes.MOD_CTRL : 0)
                            | (alt.isChecked() ? KeyCodes.MOD_ALT : 0)
                            | (shift.isChecked() ? KeyCodes.MOD_SHIFT : 0)
                            | (gui.isChecked() ? KeyCodes.MOD_GUI : 0);
                    shortcuts.setBinding(os, s.id, mods, KeyCodes.CODES[key.getSelectedItemPosition()]);
                    refresh();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private CheckBox check(String label, boolean on) {
        CheckBox c = new CheckBox(this);
        c.setText(label);
        c.setChecked(on);
        return c;
    }
}
