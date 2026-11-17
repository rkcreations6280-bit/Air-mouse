package com.my.airmouse.ui;

import android.annotation.SuppressLint;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.my.airmouse.R;
import com.my.airmouse.bt.BluetoothHidController;
import com.my.airmouse.data.PreferencesManager;
import com.my.airmouse.data.ShortcutManager;
import com.my.airmouse.input.KeyCodes;
import com.my.airmouse.input.KeyMap;

import java.util.ArrayList;
import java.util.List;

/**
 * Full computer keyboard: key down / key up, modifier latching, key combinations,
 * navigation keys, shortcuts, and a text box that uses the phone keyboard.
 * Modifier keys: tap = hold for the next key, long press = lock, tap again = release.
 */
public class KeyboardFragment extends Fragment {

    private static final int LATCH_NONE = 0, LATCH_ONCE = 1, LATCH_LOCK = 2;

    private BluetoothHidController controller;
    private PreferencesManager prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private LinearLayout keysPanel, navPanel, shortcutsPanel;
    private View textPanel;

    private final SparseIntArray latch = new SparseIntArray();
    private final SparseArray<List<TextView>> modifierViews = new SparseArray<>();
    private PreferencesManager.Os builtFor;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_keyboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        controller = BluetoothHidController.get(requireContext());
        prefs = PreferencesManager.get(requireContext());

        keysPanel = v.findViewById(R.id.kbd_keys);
        navPanel = v.findViewById(R.id.kbd_nav);
        shortcutsPanel = v.findViewById(R.id.kbd_shortcuts);
        textPanel = v.findViewById(R.id.kbd_text);

        MaterialButtonToggleGroup seg = v.findViewById(R.id.kbd_segments);
        seg.check(R.id.seg_keys);
        seg.addOnButtonCheckedListener((group, id, checked) -> {
            if (checked) showPanel(id);
        });
        showPanel(R.id.seg_keys);

        TextInputEditText input = v.findViewById(R.id.kbd_input);
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override public void afterTextChanged(Editable s) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                for (int i = 0; i < before; i++) controller.keyboard.tap(0, KeyCodes.BACKSPACE);
                for (int i = start; i < start + count && i < s.length(); i++) {
                    int[] k = KeyMap.lookup(s.charAt(i));
                    if (k != null) controller.keyboard.tap(k[0], k[1]);
                }
            }
        });
        buildAll();
    }

    private void showPanel(int segId) {
        keysPanel.setVisibility(segId == R.id.seg_keys ? View.VISIBLE : View.GONE);
        navPanel.setVisibility(segId == R.id.seg_nav ? View.VISIBLE : View.GONE);
        shortcutsPanel.setVisibility(segId == R.id.seg_shortcuts ? View.VISIBLE : View.GONE);
        textPanel.setVisibility(segId == R.id.seg_text ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onStart() {
        super.onStart();
        // The OS or shortcut bindings may have changed in Settings.
        buildAll();
    }

    @Override
    public void onStop() {
        super.onStop();
        releaseAll();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (hidden) releaseAll();
        else {
            buildAll();
        }
    }

    private void releaseAll() {
        handler.removeCallbacksAndMessages(null);
        controller.keyboard.releaseAll();
        latch.clear();
        for (int i = 0; i < modifierViews.size(); i++) {
            for (TextView t : modifierViews.valueAt(i)) paintModifier(t, LATCH_NONE);
        }
    }

    // ------------------------------------------------------------ building

    private void buildAll() {
        PreferencesManager.Os os = prefs.getOsOrDefault();
        buildShortcuts(os); // always: bindings may have changed
        if (builtFor == os && keysPanel.getChildCount() > 0) return;
        builtFor = os;
        buildKeys(os);
        buildNav();
    }

    private static final class KeyDef {
        final String label;
        final int code;
        final int mod;
        final float weight;

        KeyDef(String label, int code, int mod, float weight) {
            this.label = label;
            this.code = code;
            this.mod = mod;
            this.weight = weight;
        }
    }

    private static KeyDef k(String label, int code, float w) { return new KeyDef(label, code, 0, w); }

    private static KeyDef m(String label, int mask, float w) { return new KeyDef(label, 0, mask, w); }

    /** A key that types this character (without shift). */
    private static KeyDef c(char ch) {
        int[] map = KeyMap.lookup(Character.toLowerCase(ch));
        return new KeyDef(String.valueOf(Character.toUpperCase(ch)), map != null ? map[1] : 0, 0, 1f);
    }

    private void buildKeys(PreferencesManager.Os os) {
        keysPanel.removeAllViews();
        modifierViews.clear();
        latch.clear();
        boolean mac = os == PreferencesManager.Os.MAC;

        List<KeyDef> r0 = new ArrayList<>();
        r0.add(k("Esc", KeyCodes.ESC, 1.3f));
        for (int i = 0; i < 12; i++) r0.add(k("F" + (i + 1), KeyCodes.F1 + i, 1f));
        addRow(r0, 34, 10);

        List<KeyDef> r1 = new ArrayList<>();
        for (char ch : "`1234567890-=".toCharArray()) r1.add(c(ch));
        r1.add(k("⌫", KeyCodes.BACKSPACE, 2f));
        addRow(r1, 44, 13);

        List<KeyDef> r2 = new ArrayList<>();
        r2.add(k("Tab", KeyCodes.TAB, 1.5f));
        for (char ch : "QWERTYUIOP[]".toCharArray()) r2.add(c(ch));
        r2.add(c('\\'));
        addRow(r2, 44, 13);

        List<KeyDef> r3 = new ArrayList<>();
        r3.add(k("Caps", KeyCodes.CAPS_LOCK, 1.8f));
        for (char ch : "ASDFGHJKL;'".toCharArray()) r3.add(c(ch));
        r3.add(k("Enter", KeyCodes.ENTER, 2.2f));
        addRow(r3, 44, 13);

        List<KeyDef> r4 = new ArrayList<>();
        r4.add(m("Shift", KeyCodes.MOD_SHIFT, 2.3f));
        for (char ch : "ZXCVBNM,./".toCharArray()) r4.add(c(ch));
        r4.add(m("Shift", KeyCodes.MOD_SHIFT, 2.7f));
        addRow(r4, 44, 13);

        List<KeyDef> r5 = new ArrayList<>();
        r5.add(m("Ctrl", KeyCodes.MOD_CTRL, 1.4f));
        r5.add(mac ? m("Opt", KeyCodes.MOD_ALT, 1.4f) : m("Win", KeyCodes.MOD_GUI, 1.4f));
        r5.add(mac ? m("Cmd", KeyCodes.MOD_GUI, 1.4f) : m("Alt", KeyCodes.MOD_ALT, 1.4f));
        r5.add(k("Space", KeyCodes.SPACE, 6f));
        r5.add(mac ? m("Cmd", KeyCodes.MOD_GUI, 1.4f) : m("Alt", KeyCodes.MOD_ALT, 1.4f));
        r5.add(mac ? m("Opt", KeyCodes.MOD_ALT, 1.4f) : m("Ctrl", KeyCodes.MOD_CTRL, 1.4f));
        addRow(r5, 44, 12);
    }

    private void buildNav() {
        navPanel.removeAllViews();
        List<KeyDef> a = new ArrayList<>();
        a.add(k("Insert", KeyCodes.INSERT, 1f));
        a.add(k("Home", KeyCodes.HOME, 1f));
        a.add(k("PgUp", KeyCodes.PAGE_UP, 1f));
        addRowTo(navPanel, a, 54, 14);
        List<KeyDef> b = new ArrayList<>();
        b.add(k("Delete", KeyCodes.DELETE, 1f));
        b.add(k("End", KeyCodes.END, 1f));
        b.add(k("PgDn", KeyCodes.PAGE_DOWN, 1f));
        addRowTo(navPanel, b, 54, 14);
        List<KeyDef> c = new ArrayList<>();
        c.add(k("PrtSc", KeyCodes.PRINT_SCREEN, 1f));
        c.add(k("Esc", KeyCodes.ESC, 1f));
        c.add(k("Tab", KeyCodes.TAB, 1f));
        addRowTo(navPanel, c, 54, 14);

        List<KeyDef> up = new ArrayList<>();
        up.add(new KeyDef("", 0, 0, 1f)); // spacer
        up.add(k("↑", KeyCodes.UP, 1f));
        up.add(new KeyDef("", 0, 0, 1f));
        addRowTo(navPanel, up, 60, 20);
        List<KeyDef> dn = new ArrayList<>();
        dn.add(k("←", KeyCodes.LEFT, 1f));
        dn.add(k("↓", KeyCodes.DOWN, 1f));
        dn.add(k("→", KeyCodes.RIGHT, 1f));
        addRowTo(navPanel, dn, 60, 20);
    }

    private void buildShortcuts(PreferencesManager.Os os) {
        shortcutsPanel.removeAllViews();
        final ShortcutManager sm = ShortcutManager.get(requireContext());
        List<ShortcutManager.Shortcut> all = sm.getAll(os);
        for (int i = 0; i < all.size(); i += 2) {
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int j = i; j < Math.min(i + 2, all.size()); j++) {
                final ShortcutManager.Shortcut s = all.get(j);
                MaterialButton b = (MaterialButton) getLayoutInflater()
                        .inflate(R.layout.item_shortcut_button, row, false);
                b.setText(getString(s.labelRes) + "\n" + ShortcutManager.describe(os, s.mods, s.key));
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) b.getLayoutParams();
                lp.setMargins(dp(3), dp(3), dp(3), dp(3));
                b.setLayoutParams(lp);
                b.setOnClickListener(x -> {
                    haptic(x);
                    sm.perform(controller.keyboard, s);
                });
                row.addView(b);
            }
            shortcutsPanel.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    private void addRow(List<KeyDef> defs, int heightDp, float textSp) {
        addRowTo(keysPanel, defs, heightDp, textSp);
    }

    private void addRowTo(LinearLayout parent, List<KeyDef> defs, int heightDp, float textSp) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (KeyDef d : defs) row.addView(makeKey(d, heightDp, textSp));
        parent.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    @SuppressLint("ClickableViewAccessibility")
    private TextView makeKey(KeyDef d, int heightDp, float textSp) {
        TextView t = new TextView(requireContext());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(heightDp), d.weight);
        int mg = dp(2);
        lp.setMargins(mg / 2 + 1, mg / 2 + 1, mg / 2 + 1, mg / 2 + 1);
        t.setLayoutParams(lp);
        t.setGravity(Gravity.CENTER);
        t.setText(d.label);
        t.setTextSize(textSp);
        t.setSingleLine(true);
        t.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_text_primary));
        if (d.label.isEmpty()) return t; // spacer
        t.setBackgroundResource(R.drawable.bg_key);
        if (d.mod != 0) {
            List<TextView> list = modifierViews.get(d.mod);
            if (list == null) {
                list = new ArrayList<>();
                modifierViews.put(d.mod, list);
            }
            list.add(t);
            t.setOnTouchListener(new ModifierTouch(d.mod));
        } else {
            t.setOnTouchListener(new KeyTouch(d.code));
        }
        return t;
    }

    // ------------------------------------------------------------ key handling

    private final class KeyTouch implements View.OnTouchListener {
        private final int code;

        KeyTouch(int code) { this.code = code; }

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.setPressed(true);
                    if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(true);
                    haptic(v);
                    // Held key: the computer does its own repeat. With repeat off, send one press only.
                    if (prefs.isKeyRepeat()) controller.keyboard.keyDown(code);
                    else controller.keyboard.tap(0, code);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.setPressed(false);
                    if (prefs.isKeyRepeat()) controller.keyboard.keyUp(code);
                    releaseOneShotModifiers();
                    return true;
                default:
                    return true;
            }
        }
    }

    private final class ModifierTouch implements View.OnTouchListener {
        private final int mask;
        private boolean locked;
        private final Runnable longPress = new Runnable() {
            @Override
            public void run() {
                locked = true;
                setLatch(mask, LATCH_LOCK);
            }
        };

        ModifierTouch(int mask) { this.mask = mask; }

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.setPressed(true);
                    if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(true);
                    haptic(v);
                    locked = false;
                    handler.postDelayed(longPress, 450);
                    return true;
                case MotionEvent.ACTION_UP:
                    handler.removeCallbacks(longPress);
                    v.setPressed(false);
                    if (!locked) setLatch(mask, latch.get(mask) == LATCH_NONE ? LATCH_ONCE : LATCH_NONE);
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    handler.removeCallbacks(longPress);
                    v.setPressed(false);
                    return true;
                default:
                    return true;
            }
        }
    }

    private void setLatch(int mask, int state) {
        latch.put(mask, state);
        if (state != LATCH_NONE) controller.keyboard.modifierDown(mask);
        else controller.keyboard.modifierUp(mask);
        List<TextView> views = modifierViews.get(mask);
        if (views != null) for (TextView t : views) paintModifier(t, state);
    }

    private void paintModifier(TextView t, int state) {
        t.setSelected(state != LATCH_NONE);
        t.setTypeface(null, state == LATCH_LOCK ? Typeface.BOLD : Typeface.NORMAL);
        t.setAlpha(1f);
    }

    /** After a normal key, one-shot modifiers (Ctrl, Shift...) are released again. */
    private void releaseOneShotModifiers() {
        for (int i = latch.size() - 1; i >= 0; i--) {
            if (latch.valueAt(i) == LATCH_ONCE) setLatch(latch.keyAt(i), LATCH_NONE);
        }
    }

    private void haptic(View v) {
        if (prefs.isHaptic()) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
