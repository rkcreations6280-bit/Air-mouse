package com.my.airmouse.ui;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.radiobutton.MaterialRadioButton;
import com.my.airmouse.R;
import com.my.airmouse.bt.BluetoothHidController;
import com.my.airmouse.bt.ConnectionState;
import com.my.airmouse.data.DeviceRepository;
import com.my.airmouse.data.PreferencesManager;
import com.my.airmouse.data.SavedDevice;

import java.util.ArrayList;
import java.util.List;

/** Devices tab: "Devices List" (current and recent computers) and "OS Profile". */
public class DevicesFragment extends Fragment implements BluetoothHidController.Listener {

    private BluetoothHidController controller;
    private DeviceRepository repo;
    private PreferencesManager prefs;
    private SavedDevicesAdapter adapter;

    private View panelList, panelOs, banner, check, currentIcon, rowConnected, btnConnect;
    private TextView tvName, tvStatus, tvDesc, tvBanner, tvNoSaved, btnDisconnect;
    private LinearProgressIndicator progress;
    private MaterialCardView cardWindows, cardMac;
    private MaterialRadioButton rbWindows, rbMac;
    private ObjectAnimator pulse;
    private ConnectionState lastState;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_devices, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        controller = BluetoothHidController.get(requireContext());
        repo = DeviceRepository.get(requireContext());
        prefs = PreferencesManager.get(requireContext());

        panelList = v.findViewById(R.id.panel_devices_list);
        panelOs = v.findViewById(R.id.panel_os_profile);
        banner = v.findViewById(R.id.banner_card);
        check = v.findViewById(R.id.iv_check);
        currentIcon = v.findViewById(R.id.current_icon);
        rowConnected = v.findViewById(R.id.row_connected);
        btnConnect = v.findViewById(R.id.btn_connect);
        btnDisconnect = v.findViewById(R.id.btn_disconnect);
        tvName = v.findViewById(R.id.tv_current_name);
        tvStatus = v.findViewById(R.id.tv_current_status);
        tvDesc = v.findViewById(R.id.tv_state_desc);
        tvBanner = v.findViewById(R.id.tv_banner);
        tvNoSaved = v.findViewById(R.id.tv_no_saved);
        progress = v.findViewById(R.id.progress);
        cardWindows = v.findViewById(R.id.os_card_windows);
        cardMac = v.findViewById(R.id.os_card_mac);
        rbWindows = v.findViewById(R.id.rb_windows);
        rbMac = v.findViewById(R.id.rb_mac);

        MaterialButtonToggleGroup seg = v.findViewById(R.id.devices_segments);
        seg.check(R.id.seg_devices_list);
        seg.addOnButtonCheckedListener((group, id, checked) -> {
            if (checked) showSegment(id == R.id.seg_os_profile);
        });
        showSegment(false);

        adapter = new SavedDevicesAdapter(new SavedDevicesAdapter.Listener() {
            @Override
            public void onConnect(SavedDevice d) {
                connectSaved(d);
            }

            @Override
            public void onRemove(final SavedDevice d) {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.remove_device_title)
                        .setMessage(getString(R.string.remove_device_message, d.name))
                        .setPositiveButton(R.string.remove, (dialog, which) -> {
                            repo.remove(d.address);
                            render();
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .show();
            }
        });
        RecyclerView rv = v.findViewById(R.id.rv_saved);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(adapter);
        rv.setNestedScrollingEnabled(false);

        btnConnect.setOnClickListener(x -> {
            if (controller.getState() == ConnectionState.DISCOVERABLE) main().showConnectSheet();
            else main().startConnectFlow();
        });
        btnDisconnect.setOnClickListener(x -> controller.disconnect());
        v.findViewById(R.id.btn_open_touchpad).setOnClickListener(x -> main().navigateTo(R.id.nav_touchpad));
        v.findViewById(R.id.btn_banner_fix).setOnClickListener(x -> main().startConnectFlow());

        cardWindows.setOnClickListener(x -> selectOs(PreferencesManager.Os.WINDOWS));
        cardMac.setOnClickListener(x -> selectOs(PreferencesManager.Os.MAC));
    }

    private MainActivity main() {
        return (MainActivity) requireActivity();
    }

    private void showSegment(boolean osProfile) {
        panelList.setVisibility(osProfile ? View.GONE : View.VISIBLE);
        panelOs.setVisibility(osProfile ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onStart() {
        super.onStart();
        controller.addListener(this);
        render();
        renderOs();
    }

    @Override
    public void onStop() {
        super.onStop();
        controller.removeListener(this);
        stopPulse();
    }

    @Override
    public void onStateChanged(ConnectionState state) {
        render();
    }

    private void connectSaved(SavedDevice d) {
        if (!controller.hasBluetooth() || !controller.hasConnectPermission() || !controller.isBluetoothEnabled()) {
            main().startConnectFlow();
            return;
        }
        controller.connectSaved(d.address);
    }

    // ------------------------------------------------------------ OS profile

    private void selectOs(PreferencesManager.Os os) {
        prefs.setOs(os);
        renderOs();
    }

    private void renderOs() {
        if (getView() == null) return;
        boolean mac = prefs.getOsOrDefault() == PreferencesManager.Os.MAC;
        styleOs(cardWindows, rbWindows, !mac);
        styleOs(cardMac, rbMac, mac);
    }

    private void styleOs(MaterialCardView card, MaterialRadioButton rb, boolean on) {
        rb.setChecked(on);
        card.setStrokeWidth(Math.round((on ? 2 : 1) * getResources().getDisplayMetrics().density));
        card.setStrokeColor(ContextCompat.getColor(requireContext(), on ? R.color.primary : R.color.color_outline));
    }

    // ------------------------------------------------------------ devices list

    private void render() {
        if (getView() == null) return;
        ConnectionState s = controller.getState();
        boolean connected = s == ConnectionState.CONNECTED;
        boolean working = s == ConnectionState.INITIALIZING || s == ConnectionState.CONNECTING
                || s == ConnectionState.RECONNECTING;

        // current device card
        tvName.setText(connected ? controller.getHostName() : getString(R.string.no_device_connected));
        tvStatus.setText(s.labelRes);
        tvStatus.setTextColor(ContextCompat.getColor(requireContext(), s.colorRes));
        tvDesc.setText(descriptionFor(s));
        check.setVisibility(connected ? View.VISIBLE : View.GONE);
        progress.setVisibility(working ? View.VISIBLE : View.GONE);

        btnConnect.setVisibility(connected || working ? View.GONE : View.VISIBLE);
        ((TextView) btnConnect).setText(s == ConnectionState.DISCOVERABLE ? R.string.show_instructions
                : s == ConnectionState.FAILED ? R.string.try_again : R.string.connect_device);
        rowConnected.setVisibility(connected || working ? View.VISIBLE : View.GONE);
        btnDisconnect.setText(connected ? R.string.disconnect : R.string.cancel);
        getView().findViewById(R.id.btn_open_touchpad).setVisibility(connected ? View.VISIBLE : View.GONE);

        if (s.busy) startPulse();
        else stopPulse();
        if (connected && lastState != ConnectionState.CONNECTED) bounce();
        lastState = s;

        // banner for problems
        int msg = 0;
        if (!controller.hasBluetooth()) msg = R.string.err_no_bluetooth;
        else if (!controller.isBluetoothEnabled()) msg = R.string.banner_bt_off;
        else if (!controller.hasConnectPermission()) msg = R.string.err_permission;
        else if (s == ConnectionState.FAILED && controller.getErrorRes() != 0) msg = controller.getErrorRes();
        banner.setVisibility(msg != 0 ? View.VISIBLE : View.GONE);
        if (msg != 0) tvBanner.setText(msg);
        getView().findViewById(R.id.btn_banner_fix).setVisibility(
                controller.hasBluetooth() && (!controller.isBluetoothEnabled() || !controller.hasConnectPermission())
                        ? View.VISIBLE : View.GONE);

        // recent devices (the current one is shown above)
        List<SavedDevice> recent = new ArrayList<>();
        String current = connected ? controller.getHostAddress() : null;
        for (SavedDevice d : repo.list()) {
            if (current == null || !d.address.equals(current)) recent.add(d);
        }
        adapter.submit(recent, null);
        tvNoSaved.setVisibility(recent.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private String descriptionFor(ConnectionState s) {
        switch (s) {
            case INITIALIZING: return getString(R.string.desc_initializing);
            case DISCOVERABLE: return getString(R.string.desc_discoverable, controller.getLocalName());
            case CONNECTING: return getString(R.string.desc_connecting);
            case CONNECTED: return getString(R.string.desc_connected_info);
            case RECONNECTING: return getString(R.string.desc_reconnecting);
            case FAILED:
                return controller.getErrorRes() != 0 ? getString(controller.getErrorRes())
                        : getString(R.string.err_connect_failed);
            default: return getString(R.string.desc_disconnected);
        }
    }

    private void startPulse() {
        if (pulse != null && pulse.isRunning()) return;
        pulse = ObjectAnimator.ofPropertyValuesHolder(currentIcon,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.1f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.1f));
        pulse.setDuration(700);
        pulse.setRepeatCount(ObjectAnimator.INFINITE);
        pulse.setRepeatMode(ObjectAnimator.REVERSE);
        pulse.start();
    }

    private void stopPulse() {
        if (pulse != null) {
            pulse.cancel();
            pulse = null;
        }
        if (currentIcon != null) {
            currentIcon.setScaleX(1f);
            currentIcon.setScaleY(1f);
        }
    }

    private void bounce() {
        check.setScaleX(0.3f);
        check.setScaleY(0.3f);
        check.animate().scaleX(1f).scaleY(1f).setDuration(260).start();
    }
}
