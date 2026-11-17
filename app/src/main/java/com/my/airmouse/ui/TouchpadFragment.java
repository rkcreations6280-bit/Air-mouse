package com.my.airmouse.ui;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.my.airmouse.R;
import com.my.airmouse.bt.BluetoothHidController;
import com.my.airmouse.bt.MouseReportManager;
import com.my.airmouse.data.PreferencesManager;
import com.my.airmouse.input.GestureController;
import com.my.airmouse.input.TouchpadView;

import java.util.Locale;

/** Big touchpad plus left / right / scroll / speed controls. */
public class TouchpadFragment extends Fragment {

    private BluetoothHidController controller;
    private PreferencesManager prefs;
    private GestureController gesture;

    private TextView tvSpeed;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_touchpad, container, false);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        controller = BluetoothHidController.get(requireContext());
        prefs = PreferencesManager.get(requireContext());

        tvSpeed = v.findViewById(R.id.tv_speed);

        TouchpadView pad = v.findViewById(R.id.pad);
        gesture = new GestureController(requireContext(), controller.mouse, prefs);
        pad.setController(gesture);

        bindHold(v.findViewById(R.id.btn_left), MouseReportManager.LEFT);
        bindHold(v.findViewById(R.id.btn_right), MouseReportManager.RIGHT);

        HoldRepeat.bind(v.findViewById(R.id.btn_scroll_up), () -> controller.mouse.scroll(1, 0));
        HoldRepeat.bind(v.findViewById(R.id.btn_scroll_down), () -> controller.mouse.scroll(-1, 0));

        v.findViewById(R.id.btn_speed_down).setOnClickListener(x -> changeSpeed(-0.25f));
        v.findViewById(R.id.btn_speed_up).setOnClickListener(x -> changeSpeed(0.25f));
        showSpeed();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void bindHold(View b, final int mask) {
        b.setOnTouchListener((view, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    view.setPressed(true);
                    if (prefs.isHaptic()) view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
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

    private void changeSpeed(float delta) {
        float v = Math.max(0.5f, Math.min(4f, prefs.getPointerSensitivity() + delta));
        prefs.setPointerSensitivity(v);
        showSpeed();
    }

    private void showSpeed() {
        tvSpeed.setText(getString(R.string.speed_value, String.format(Locale.US, "%.2f", prefs.getPointerSensitivity())));
    }

    @Override
    public void onStart() {
        super.onStart();
        showSpeed();
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
        else showSpeed();
    }

    private void releaseAll() {
        if (gesture != null) gesture.cancel();
        controller.mouse.releaseAll();
    }
}
