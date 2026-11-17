package com.my.airmouse.ui;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.my.airmouse.R;
import com.my.airmouse.bt.BluetoothHidController;
import com.my.airmouse.bt.ConnectionState;
import com.my.airmouse.data.DeviceRepository;
import com.my.airmouse.data.PreferencesManager;
import com.my.airmouse.data.SavedDevice;

/**
 * Hosts the always visible header, the five tabs (Touchpad, Keyboard, Modes, Devices, Settings)
 * and the "No Device Connected" popup. Also owns the connect flow (permission, Bluetooth on,
 * discoverable) so every screen can start it.
 */
public class MainActivity extends BaseActivity implements BluetoothHidController.Listener {

    private BluetoothHidController controller;
    private DeviceRepository repo;
    private PreferencesManager prefs;
    private NavigationManager navigation;
    private BottomNavigationView bottomNav;
    private int currentTab = R.id.nav_touchpad;

    private TextView headerStatus;
    private ImageView headerPillIcon;
    private View headerBt;
    private View modal;
    private TextView modalTitle, modalText;
    private LinearProgressIndicator modalProgress;
    private View modalButton, modalDevices;

    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) controller.onAppForeground();
                updateUi();
            });

    private final ActivityResultLauncher<Intent> enableLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK) startConnectFlow();
                else updateUi();
            });

    private final ActivityResultLauncher<Intent> discoverableLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != Activity.RESULT_CANCELED) {
                    controller.setDiscoverable(true);
                    showConnectSheet();
                }
                updateUi();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        applyInsets(findViewById(R.id.main_root), false);

        controller = BluetoothHidController.get(this);
        repo = DeviceRepository.get(this);
        prefs = PreferencesManager.get(this);

        // header
        headerStatus = findViewById(R.id.header_status);
        headerPillIcon = findViewById(R.id.header_pill_icon);
        headerBt = findViewById(R.id.header_bt);
        findViewById(R.id.header_pill).setOnClickListener(v -> navigateTo(R.id.nav_devices));
        headerBt.setOnClickListener(v -> toggleConnection());
        findViewById(R.id.header_theme).setOnClickListener(v -> showThemeDialog());
        findViewById(R.id.header_help).setOnClickListener(v ->
                startActivity(new Intent(this, GuideActivity.class)));

        // popup
        modal = findViewById(R.id.modal_overlay);
        modalTitle = findViewById(R.id.modal_title);
        modalText = findViewById(R.id.modal_text);
        modalProgress = findViewById(R.id.modal_progress);
        modalButton = findViewById(R.id.modal_button);
        modalDevices = findViewById(R.id.modal_devices);
        modalButton.setOnClickListener(v -> {
            if (controller.getState() == ConnectionState.DISCOVERABLE) showConnectSheet();
            else startConnectFlow();
        });
        modalDevices.setOnClickListener(v -> navigateTo(R.id.nav_devices));

        // tabs
        navigation = new NavigationManager(getSupportFragmentManager(), R.id.container);
        bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setOnItemSelectedListener(item -> {
            currentTab = item.getItemId();
            navigation.navigate(currentTab);
            updateUi();
            return true;
        });
        if (savedInstanceState == null) bottomNav.setSelectedItemId(R.id.nav_touchpad);
        else currentTab = bottomNav.getSelectedItemId();

        // Bluetooth permission is asked once, right at the start, so later launches connect instantly.
        if (!controller.hasConnectPermission()) {
            permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        controller.addListener(this);
        updateUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyOrientation();
        updateUi();
    }

    @Override
    protected void onStop() {
        super.onStop();
        controller.removeListener(this);
    }

    @Override
    public void onStateChanged(ConnectionState state) {
        updateUi();
    }

    // ------------------------------------------------------------ header and popup

    private void updateUi() {
        if (controller == null || headerStatus == null) return;
        ConnectionState s = controller.getState();
        boolean connected = s == ConnectionState.CONNECTED;

        // status pill
        if (connected) headerStatus.setText(controller.getHostName());
        else if (s == ConnectionState.INITIALIZING || s == ConnectionState.CONNECTING
                || s == ConnectionState.RECONNECTING || s == ConnectionState.DISCOVERABLE) {
            headerStatus.setText(s.labelRes);
        } else headerStatus.setText(R.string.not_connected);
        headerPillIcon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(this,
                connected ? R.color.primary : R.color.color_text_secondary)));
        headerBt.setAlpha(controller.isBluetoothEnabled() ? 1f : 0.45f);

        // popup, only on screens that need a connection
        boolean needsConnection = currentTab == R.id.nav_touchpad || currentTab == R.id.nav_keyboard
                || currentTab == R.id.nav_modes;
        modal.setVisibility(!connected && needsConnection ? View.VISIBLE : View.GONE);
        if (modal.getVisibility() != View.VISIBLE) return;

        boolean working = s == ConnectionState.INITIALIZING || s == ConnectionState.CONNECTING
                || s == ConnectionState.RECONNECTING;
        modalProgress.setVisibility(working ? View.VISIBLE : View.GONE);
        modalButton.setVisibility(working ? View.GONE : View.VISIBLE);
        modalDevices.setVisibility(working ? View.GONE : View.VISIBLE);
        TextView button = (TextView) modalButton;
        switch (s) {
            case INITIALIZING:
            case CONNECTING:
            case RECONNECTING:
                modalTitle.setText(s.labelRes);
                modalText.setText(s == ConnectionState.INITIALIZING ? R.string.desc_initializing
                        : s == ConnectionState.CONNECTING ? R.string.desc_connecting : R.string.desc_reconnecting);
                break;
            case DISCOVERABLE:
                modalTitle.setText(s.labelRes);
                modalText.setText(getString(R.string.desc_discoverable, controller.getLocalName()));
                button.setText(R.string.show_instructions);
                break;
            case FAILED:
                modalTitle.setText(s.labelRes);
                modalText.setText(controller.getErrorRes() != 0 ? controller.getErrorRes()
                        : R.string.err_connect_failed);
                button.setText(R.string.try_again);
                break;
            default:
                modalTitle.setText(R.string.no_device_connected);
                modalText.setText(R.string.no_device_connected_text);
                button.setText(R.string.connect_device);
                break;
        }
    }

    /** Header Bluetooth button: disconnect when connected, otherwise connect (last computer, or the full flow). */
    private void toggleConnection() {
        ConnectionState s = controller.getState();
        if (s == ConnectionState.CONNECTED || s == ConnectionState.CONNECTING
                || s == ConnectionState.RECONNECTING || s == ConnectionState.INITIALIZING) {
            controller.disconnect();
            return;
        }
        SavedDevice last = repo.getLast();
        if (last != null && controller.hasBluetooth() && controller.hasConnectPermission()
                && controller.isBluetoothEnabled()) {
            controller.connectSaved(last.address);
        } else {
            startConnectFlow();
        }
    }

    private void showThemeDialog() {
        String[] items = {getString(R.string.theme_system), getString(R.string.theme_light),
                getString(R.string.theme_dark)};
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.setting_theme)
                .setSingleChoiceItems(items, prefs.getTheme(), (d, which) -> {
                    prefs.setTheme(which);
                    prefs.applyTheme();
                    d.dismiss();
                })
                .show();
    }

    // ------------------------------------------------------------ connect flow

    /** Permission, Bluetooth on, visible to other devices, then the pairing instructions. */
    public void startConnectFlow() {
        if (!controller.hasBluetooth()) {
            updateUi();
            return;
        }
        if (!controller.hasConnectPermission()) {
            permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT);
            return;
        }
        if (!controller.isBluetoothEnabled()) {
            enableLauncher.launch(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
            return;
        }
        controller.start();
        Intent i = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        i.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120);
        discoverableLauncher.launch(i);
    }

    public void showConnectSheet() {
        final BottomSheetDialog sheet = new BottomSheetDialog(this);
        View v = getLayoutInflater().inflate(R.layout.sheet_connect, null);
        ((TextView) v.findViewById(R.id.sheet_steps)).setText(
                getString(R.string.sheet_steps, controller.getLocalName()));
        v.findViewById(R.id.sheet_open_settings).setOnClickListener(x ->
                startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));
        v.findViewById(R.id.sheet_done).setOnClickListener(x -> sheet.dismiss());
        sheet.setContentView(v);
        sheet.show();
    }

    // ------------------------------------------------------------ misc

    /** Called from settings too. */
    public void applyOrientation() {
        int o;
        switch (prefs.getOrientation()) {
            case PreferencesManager.ORIENTATION_PORTRAIT: o = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT; break;
            case PreferencesManager.ORIENTATION_LANDSCAPE: o = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE; break;
            default: o = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED; break;
        }
        setRequestedOrientation(o);
    }

    /** Switch to a tab, e.g. R.id.nav_devices. */
    public void navigateTo(int menuItemId) {
        bottomNav.setSelectedItemId(menuItemId);
    }
}
