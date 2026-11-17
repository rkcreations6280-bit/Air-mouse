package com.my.airmouse.ui;

import android.annotation.SuppressLint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.card.MaterialCardView;
import com.my.airmouse.R;
import com.my.airmouse.bt.BluetoothHidController;
import com.my.airmouse.bt.ConsumerReportManager;
import com.my.airmouse.bt.MouseReportManager;
import com.my.airmouse.data.PreferencesManager;
import com.my.airmouse.input.GestureController;
import com.my.airmouse.input.KeyCodes;
import com.my.airmouse.input.TouchpadView;

/**
 * Extra control modes: Mouse (air / gyroscope), Keyboard, Touchpad, Multimedia, Navigation.
 * Keyboard and Touchpad just open their own tab.
 */
public class ModesFragment extends Fragment
        implements SensorEventListener {

    private static final int MODE_MOUSE = 0, MODE_KEYBOARD = 1, MODE_TOUCHPAD = 2, MODE_MEDIA = 3, MODE_NAV = 4;
    private static final int[] NAMES = {R.string.mode_mouse, R.string.mode_keyboard, R.string.mode_touchpad,
            R.string.mode_media, R.string.mode_nav};
    private static final int[] DESCS = {R.string.mode_mouse_desc, R.string.mode_keyboard_desc,
            R.string.mode_touchpad_desc, R.string.mode_media_desc, R.string.mode_nav_desc};
    private static final int[] ICONS = {R.drawable.ic_mouse, R.drawable.ic_keyboard, R.drawable.ic_touchpad,
            R.drawable.ic_play, R.drawable.ic_modes};

    private BluetoothHidController controller;
    private PreferencesManager prefs;
    private GestureController gesture;
    private SensorManager sensors;
    private Sensor gyro;

    private final MaterialCardView[] cards = new MaterialCardView[5];
    private final View[] checks = new View[5];
    private View panelAir, panelMedia, panelNav;
    private TextView tvNoGyro;
    private int mode;
    private boolean padDown;
    private float remX, remY;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_modes, container, false);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        controller = BluetoothHidController.get(requireContext());
        prefs = PreferencesManager.get(requireContext());
        sensors = (SensorManager) requireContext().getSystemService(android.content.Context.SENSOR_SERVICE);
        gyro = sensors.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

        panelAir = v.findViewById(R.id.panel_air);
        panelMedia = v.findViewById(R.id.panel_media);
        panelNav = v.findViewById(R.id.panel_nav);
        tvNoGyro = v.findViewById(R.id.air_no_gyro);

        // mode cards
        LinearLayout list = v.findViewById(R.id.modes_list);
        for (int i = 0; i < 5; i++) {
            final int index = i;
            View item = getLayoutInflater().inflate(R.layout.item_mode_card, list, false);
            ((ImageView) item.findViewById(R.id.mode_icon)).setImageResource(ICONS[i]);
            ((TextView) item.findViewById(R.id.mode_name)).setText(NAMES[i]);
            ((TextView) item.findViewById(R.id.mode_desc)).setText(DESCS[i]);
            cards[i] = (MaterialCardView) item;
            checks[i] = item.findViewById(R.id.mode_check);
            item.setOnClickListener(x -> selectMode(index, true));
            list.addView(item);
        }

        // air mouse panel
        TouchpadView airPad = v.findViewById(R.id.air_pad);
        gesture = new GestureController(requireContext(), controller.mouse, prefs);
        gesture.setAirMode(true);
        airPad.setController(gesture);
        airPad.setTouchListener(down -> {
            padDown = down;
            remX = remY = 0;
        });
        bindHold(v.findViewById(R.id.air_left), MouseReportManager.LEFT);
        bindHold(v.findViewById(R.id.air_right), MouseReportManager.RIGHT);

        // multimedia panel
        v.findViewById(R.id.media_prev).setOnClickListener(x -> media(x, ConsumerReportManager.PREVIOUS));
        v.findViewById(R.id.media_play).setOnClickListener(x -> media(x, ConsumerReportManager.PLAY_PAUSE));
        v.findViewById(R.id.media_next).setOnClickListener(x -> media(x, ConsumerReportManager.NEXT));
        v.findViewById(R.id.media_vol_down).setOnClickListener(x -> media(x, ConsumerReportManager.VOLUME_DOWN));
        v.findViewById(R.id.media_mute).setOnClickListener(x -> media(x, ConsumerReportManager.MUTE));
        v.findViewById(R.id.media_vol_up).setOnClickListener(x -> media(x, ConsumerReportManager.VOLUME_UP));

        // navigation panel (holding repeats)
        nav(v, R.id.nav_key_up, KeyCodes.UP);
        nav(v, R.id.nav_key_down, KeyCodes.DOWN);
        nav(v, R.id.nav_key_left, KeyCodes.LEFT);
        nav(v, R.id.nav_key_right, KeyCodes.RIGHT);
        nav(v, R.id.nav_key_enter, KeyCodes.ENTER);
        nav(v, R.id.nav_key_esc, KeyCodes.ESC);
        nav(v, R.id.nav_key_tab, KeyCodes.TAB);
        nav(v, R.id.nav_key_pgup, KeyCodes.PAGE_UP);
        nav(v, R.id.nav_key_pgdn, KeyCodes.PAGE_DOWN);

        mode = prefs.getMode();
        selectMode(mode, false);
    }

    private void media(View v, int usage) {
        if (prefs.isHaptic()) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        controller.consumer.tap(usage);
    }

    private void nav(View root, int id, final int code) {
        HoldRepeat.bind(root.findViewById(id), () -> controller.keyboard.tap(0, code));
    }

    @SuppressLint("ClickableViewAccessibility")
    private void bindHold(View b, final int mask) {
        b.setOnTouchListener((view, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    view.setPressed(true);
                    if (prefs.isHaptic()) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    controller.mouse.press(mask);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.setPressed(false);
                    controller.mouse.release(mask);
                    return true;
                default:
                    return true;
            }
        });
    }

    private void selectMode(int index, boolean userAction) {
        mode = index;
        prefs.setMode(index);
        for (int i = 0; i < 5; i++) {
            boolean on = i == index;
            cards[i].setStrokeWidth(Math.round((on ? 2 : 1) * getResources().getDisplayMetrics().density));
            cards[i].setStrokeColor(ContextCompat.getColor(requireContext(), on ? R.color.primary : R.color.color_outline));
            checks[i].setVisibility(on ? View.VISIBLE : View.INVISIBLE);
        }
        panelAir.setVisibility(index == MODE_MOUSE ? View.VISIBLE : View.GONE);
        panelMedia.setVisibility(index == MODE_MEDIA ? View.VISIBLE : View.GONE);
        panelNav.setVisibility(index == MODE_NAV ? View.VISIBLE : View.GONE);
        tvNoGyro.setVisibility(index == MODE_MOUSE && gyro == null ? View.VISIBLE : View.GONE);
        updateSensor();
        if (userAction && index == MODE_KEYBOARD) {
            ((MainActivity) requireActivity()).navigateTo(R.id.nav_keyboard);
        } else if (userAction && index == MODE_TOUCHPAD) {
            ((MainActivity) requireActivity()).navigateTo(R.id.nav_touchpad);
        }
    }

    // ------------------------------------------------------------ gyroscope

    private void updateSensor() {
        sensors.unregisterListener(this);
        if (mode == MODE_MOUSE && gyro != null && isResumed() && !isHidden()) {
            sensors.registerListener(this, gyro, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    /** Phone held flat like a remote: turn = left / right, tilt = up / down. Moves only while a finger is on the pad. */
    @Override
    public void onSensorChanged(SensorEvent e) {
        if (!padDown) return;
        float k = 14f * prefs.getPointerSensitivity();
        remX += -e.values[2] * k;
        remY += -e.values[0] * k;
        int ix = (int) remX, iy = (int) remY;
        remX -= ix;
        remY -= iy;
        if (ix != 0 || iy != 0) controller.mouse.move(ix, iy);
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    // ------------------------------------------------------------ lifecycle

    @Override
    public void onResume() {
        super.onResume();
        updateSensor();
    }

    @Override
    public void onPause() {
        super.onPause();
        sensors.unregisterListener(this);
        releaseAll();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (hidden) {
            sensors.unregisterListener(this);
            releaseAll();
        } else {
            updateSensor();
        }
    }

    private void releaseAll() {
        padDown = false;
        if (gesture != null) gesture.cancel();
        controller.mouse.releaseAll();
    }
}
