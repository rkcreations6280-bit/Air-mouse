package com.my.airmouse.ui;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import com.my.airmouse.AppLinks;
import com.my.airmouse.R;
import com.my.airmouse.bt.BluetoothHidController;
import com.my.airmouse.data.DeviceRepository;
import com.my.airmouse.data.PreferencesManager;

import java.util.Locale;

/** Structured settings: General, Touchpad, Keyboard, Bluetooth, Other. */
public class SettingsFragment extends Fragment {

    private PreferencesManager prefs;
    private LinearLayout box;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        prefs = PreferencesManager.get(requireContext());
        box = v.findViewById(R.id.settings_box);
        build();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) build();
    }

    @Override
    public void onStart() {
        super.onStart();
        build();
    }

    private void build() {
        if (box == null) return;
        box.removeAllViews();

        header(R.string.settings_general);
        valueRow(R.string.setting_theme, themeName(), x -> pickTheme());
        valueRow(R.string.setting_orientation, orientationName(), x -> pickOrientation());
        valueRow(R.string.setting_language, getString(R.string.language_system), x -> openLanguage());
        switchRow(R.string.setting_haptic, 0, prefs.isHaptic(), prefs::setHaptic);

        header(R.string.settings_touchpad);
        sliderRow(R.string.setting_sensitivity, 0.5f, 4f, 0.1f, prefs.getPointerSensitivity(), prefs::setPointerSensitivity);
        sliderRow(R.string.setting_scroll_speed, 0.3f, 3f, 0.1f, prefs.getScrollSpeed(), prefs::setScrollSpeed);
        switchRow(R.string.setting_tap_click, R.string.setting_tap_click_desc, prefs.isTapToClick(), prefs::setTapToClick);
        switchRow(R.string.setting_tap_drag, R.string.setting_tap_drag_desc, prefs.isTapDrag(), prefs::setTapDrag);
        switchRow(R.string.setting_natural, R.string.setting_natural_desc, prefs.isNaturalScroll(), prefs::setNaturalScroll);

        header(R.string.settings_keyboard);
        valueRow(R.string.setting_layout, getString(osName()), x -> pickOs());
        switchRow(R.string.setting_key_repeat, R.string.setting_key_repeat_desc, prefs.isKeyRepeat(), prefs::setKeyRepeat);
        valueRow(R.string.setting_shortcuts, getString(R.string.edit), x ->
                startActivity(new Intent(requireContext(), KeyBindingActivity.class)));

        header(R.string.settings_bluetooth);
        BluetoothHidController c = BluetoothHidController.get(requireContext());
        valueRow(R.string.setting_status, getString(c.getState().labelRes), x ->
                ((MainActivity) requireActivity()).navigateTo(R.id.nav_devices));
        switchRow(R.string.setting_auto_connect, R.string.setting_auto_connect_desc, prefs.isAutoConnect(), prefs::setAutoConnect);
        switchRow(R.string.setting_reconnect, R.string.setting_reconnect_desc, prefs.isAutoReconnect(), prefs::setAutoReconnect);
        int saved = DeviceRepository.get(requireContext()).list().size();
        valueRow(R.string.setting_saved_devices, getString(R.string.saved_count, saved), x ->
                ((MainActivity) requireActivity()).navigateTo(R.id.nav_devices));

        header(R.string.settings_other);
        valueRow(R.string.setting_guide, "", x -> startActivity(new Intent(requireContext(), GuideActivity.class)));
        valueRow(R.string.setting_support, "", x -> contactSupport());
        valueRow(R.string.setting_privacy, "", x -> open(Uri.parse(AppLinks.PRIVACY_URL)));
        valueRow(R.string.setting_rate, "", x -> rate());
        valueRow(R.string.setting_share, "", x -> share());
    }

    // ------------------------------------------------------------ row builders

    private void header(int titleRes) {
        TextView t = (TextView) getLayoutInflater().inflate(R.layout.item_setting_header, box, false);
        t.setText(titleRes);
        box.addView(t);
    }

    private void valueRow(int titleRes, String value, View.OnClickListener click) {
        View row = getLayoutInflater().inflate(R.layout.item_setting_value, box, false);
        ((TextView) row.findViewById(R.id.set_title)).setText(titleRes);
        TextView val = row.findViewById(R.id.set_value);
        val.setText(value);
        val.setVisibility(value.isEmpty() ? View.GONE : View.VISIBLE);
        row.setOnClickListener(click);
        box.addView(row);
    }

    private interface BoolSetter { void set(boolean v); }

    private interface FloatSetter { void set(float v); }

    private void switchRow(int titleRes, int descRes, boolean checked, final BoolSetter setter) {
        View row = getLayoutInflater().inflate(R.layout.item_setting_switch, box, false);
        ((TextView) row.findViewById(R.id.set_title)).setText(titleRes);
        TextView desc = row.findViewById(R.id.set_desc);
        if (descRes != 0) desc.setText(descRes);
        else desc.setVisibility(View.GONE);
        final MaterialSwitch sw = row.findViewById(R.id.set_switch);
        sw.setChecked(checked);
        sw.setOnCheckedChangeListener((b, isChecked) -> setter.set(isChecked));
        row.setOnClickListener(x -> sw.toggle());
        box.addView(row);
    }

    private void sliderRow(int titleRes, float from, float to, float step, float value, final FloatSetter setter) {
        View row = getLayoutInflater().inflate(R.layout.item_setting_slider, box, false);
        ((TextView) row.findViewById(R.id.set_title)).setText(titleRes);
        final TextView val = row.findViewById(R.id.set_value);
        Slider s = row.findViewById(R.id.set_slider);
        s.setValueFrom(from);
        s.setValueTo(to);
        s.setStepSize(step);
        float clamped = Math.max(from, Math.min(to, value));
        float snapped = from + Math.round((clamped - from) / step) * step;
        s.setValue(Math.max(from, Math.min(to, snapped)));
        val.setText(String.format(Locale.US, "%.1fx", s.getValue()));
        s.addOnChangeListener((slider, v, fromUser) -> {
            val.setText(String.format(Locale.US, "%.1fx", v));
            if (fromUser) setter.set(v);
        });
        box.addView(row);
    }

    // ------------------------------------------------------------ pickers

    private String themeName() {
        switch (prefs.getTheme()) {
            case PreferencesManager.THEME_LIGHT: return getString(R.string.theme_light);
            case PreferencesManager.THEME_DARK: return getString(R.string.theme_dark);
            default: return getString(R.string.theme_system);
        }
    }

    private String orientationName() {
        switch (prefs.getOrientation()) {
            case PreferencesManager.ORIENTATION_PORTRAIT: return getString(R.string.orientation_portrait);
            case PreferencesManager.ORIENTATION_LANDSCAPE: return getString(R.string.orientation_landscape);
            default: return getString(R.string.orientation_auto);
        }
    }

    private int osName() {
        return prefs.getOsOrDefault() == PreferencesManager.Os.MAC ? R.string.os_mac : R.string.os_windows;
    }

    private void pickTheme() {
        String[] items = {getString(R.string.theme_system), getString(R.string.theme_light), getString(R.string.theme_dark)};
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.setting_theme)
                .setSingleChoiceItems(items, prefs.getTheme(), (d, which) -> {
                    prefs.setTheme(which);
                    prefs.applyTheme();
                    d.dismiss();
                })
                .show();
    }

    private void pickOrientation() {
        String[] items = {getString(R.string.orientation_auto), getString(R.string.orientation_portrait),
                getString(R.string.orientation_landscape)};
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.setting_orientation)
                .setSingleChoiceItems(items, prefs.getOrientation(), (d, which) -> {
                    prefs.setOrientation(which);
                    ((MainActivity) requireActivity()).applyOrientation();
                    d.dismiss();
                    build();
                })
                .show();
    }

    private void pickOs() {
        String[] items = {getString(R.string.os_windows), getString(R.string.os_mac)};
        int checked = prefs.getOsOrDefault() == PreferencesManager.Os.MAC ? 1 : 0;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.setting_layout)
                .setSingleChoiceItems(items, checked, (d, which) -> {
                    prefs.setOs(which == 1 ? PreferencesManager.Os.MAC : PreferencesManager.Os.WINDOWS);
                    d.dismiss();
                    build();
                })
                .show();
    }

    // ------------------------------------------------------------ actions

    private void openLanguage() {
        if (Build.VERSION.SDK_INT >= 33) {
            Intent i = new Intent(Settings.ACTION_APP_LOCALE_SETTINGS,
                    Uri.parse("package:" + requireContext().getPackageName()));
            try {
                startActivity(i);
                return;
            } catch (ActivityNotFoundException ignored) {
                // fall through to the message
            }
        }
        Toast.makeText(requireContext(), R.string.language_info, Toast.LENGTH_LONG).show();
    }

    private void open(Uri uri) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(requireContext(), R.string.no_app_found, Toast.LENGTH_SHORT).show();
        }
    }

    private void contactSupport() {
        Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + AppLinks.SUPPORT_EMAIL));
        i.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name));
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(requireContext(), R.string.no_app_found, Toast.LENGTH_SHORT).show();
        }
    }

    private void rate() {
        String pkg = requireContext().getPackageName();
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg)));
        } catch (ActivityNotFoundException e) {
            open(Uri.parse("https://play.google.com/store/apps/details?id=" + pkg));
        }
    }

    private void share() {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, getString(R.string.share_text,
                "https://play.google.com/store/apps/details?id=" + requireContext().getPackageName()));
        startActivity(Intent.createChooser(i, getString(R.string.setting_share)));
    }
}
