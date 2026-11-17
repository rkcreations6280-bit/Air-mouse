package com.my.airmouse.bt;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHidDevice;
import android.bluetooth.BluetoothHidDeviceAppSdpSettings;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import com.my.airmouse.R;
import com.my.airmouse.data.DeviceRepository;
import com.my.airmouse.data.PreferencesManager;

import com.my.airmouse.data.SavedDevice;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Makes the phone a real Bluetooth HID mouse / keyboard (BluetoothHidDevice, Android 9+).
 * This class is the single source of truth for the connection state. It never reports
 * CONNECTED unless the system has told us a host is connected.
 * All methods must be called from the main thread.
 */
@SuppressLint("MissingPermission")
public final class BluetoothHidController implements ReportSender {

    public interface Listener {
        void onStateChanged(ConnectionState state);
    }

    private static final long INIT_TIMEOUT_MS = 8000;
    private static final long CONNECT_TIMEOUT_MS = 20000;
    private static final long DISCOVERABLE_MS = 120000;
    private static final long RECONNECT_DELAY_MS = 2500;
    private static final int MAX_RECONNECTS = 3;

    private static BluetoothHidController instance;

    public static synchronized BluetoothHidController get(Context c) {
        if (instance == null) instance = new BluetoothHidController(c.getApplicationContext());
        return instance;
    }

    public final MouseReportManager mouse = new MouseReportManager(this);
    public final KeyboardReportManager keyboard = new KeyboardReportManager(this);
    public final ConsumerReportManager consumer = new ConsumerReportManager(this);

    private final Context app;
    private final BluetoothAdapter adapter;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final DeviceRepository repo;
    private final PreferencesManager prefs;
    private final Object reconnectToken = new Object();

    private BluetoothHidDevice hid;
    private BluetoothDevice host;
    private BluetoothDevice pending;
    private boolean proxyRequested;
    private boolean registered;
    private boolean registering;
    private boolean userDisconnect;
    private boolean discoverable;
    private int reconnectAttempts;
    private ConnectionState state = ConnectionState.DISCONNECTED;
    private int errorRes;

    // app lifecycle: connect when the app opens, shut everything down when it closes (saves battery)
    private static final long BACKGROUND_GRACE_MS = 6000;
    private static final long KEEP_AFTER_CONNECT_MS = 30000;
    private boolean foreground;
    private boolean autoConnectPending;
    private long lastConnectedAt;

    // keyboard and media reports are sent one at a time with a small gap, hosts can miss
    // a press and release that arrive back to back
    private static final long SEND_GAP_MS = 7;
    private final ArrayDeque<Report> queue = new ArrayDeque<>();
    private boolean draining;

    private static final class Report {
        final int id;
        final byte[] data;

        Report(int id, byte[] data) {
            this.id = id;
            this.data = data;
        }
    }

    private BluetoothHidController(Context app) {
        this.app = app;
        BluetoothManager bm = (BluetoothManager) app.getSystemService(Context.BLUETOOTH_SERVICE);
        this.adapter = bm != null ? bm.getAdapter() : null;
        this.repo = DeviceRepository.get(app);
        this.prefs = PreferencesManager.get(app);
        app.registerReceiver(btReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
    }

    // ------------------------------------------------------------ public state

    public void addListener(Listener l) { listeners.add(l); }

    public void removeListener(Listener l) { listeners.remove(l); }

    public ConnectionState getState() { return state; }

    /** String resource describing why the state is FAILED, or 0. */
    public int getErrorRes() { return errorRes; }

    public boolean isConnected() { return state == ConnectionState.CONNECTED && host != null; }

    public boolean hasBluetooth() { return adapter != null; }

    public boolean isBluetoothEnabled() { return adapter != null && adapter.isEnabled(); }

    public boolean isRegistered() { return registered; }

    public boolean hasConnectPermission() {
        return Build.VERSION.SDK_INT < 31
                || app.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED;
    }

    public String getHostName() {
        if (host == null) return "";
        String n = hasConnectPermission() ? host.getName() : null;
        return n != null ? n : host.getAddress();
    }

    public String getHostAddress() { return host != null ? host.getAddress() : null; }

    /** The name other devices see when they search for this phone. */
    public String getLocalName() {
        String n = null;
        if (adapter != null && hasConnectPermission()) n = adapter.getName();
        return n != null ? n : Build.MODEL;
    }

    public List<BluetoothDevice> getPairedDevices() {
        List<BluetoothDevice> out = new ArrayList<>();
        if (adapter != null && hasConnectPermission()) out.addAll(adapter.getBondedDevices());
        return out;
    }

    // ------------------------------------------------------------ lifecycle

    /** Starts the HID profile and registers the descriptor. Safe to call repeatedly. */
    public void start() {
        if (adapter == null || !adapter.isEnabled() || !hasConnectPermission()) return;
        if (hid != null) {
            if (!registered) register();
            return;
        }
        if (proxyRequested) return;
        setState(ConnectionState.INITIALIZING, 0);
        try {
            proxyRequested = adapter.getProfileProxy(app, serviceListener, BluetoothProfile.HID_DEVICE);
        } catch (SecurityException e) {
            fail(R.string.err_permission, true);
            return;
        }
        if (!proxyRequested) {
            fail(R.string.err_hid_unavailable, true);
            return;
        }
        handler.removeCallbacks(initTimeout);
        handler.postDelayed(initTimeout, INIT_TIMEOUT_MS);
    }

    private void register() {
        if (registering) return;
        registering = true;
        BluetoothHidDeviceAppSdpSettings sdp = new BluetoothHidDeviceAppSdpSettings(
                "Air mouse", "Phone as mouse and keyboard", "Air mouse",
                BluetoothHidDevice.SUBCLASS1_COMBO, HidDescriptor.DESCRIPTOR);
        boolean ok;
        try {
            ok = hid.registerApp(sdp, null, null, app.getMainExecutor(), callback);
        } catch (SecurityException e) {
            fail(R.string.err_permission, true);
            return;
        }
        if (!ok) fail(R.string.err_register, true);
    }

    private void teardown() {
        handler.removeCallbacks(initTimeout);
        if (hid != null && adapter != null) {
            try {
                adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, hid);
            } catch (RuntimeException ignored) {
                // already gone
            }
        }
        hid = null;
        host = null;
        pending = null;
        proxyRequested = false;
        registered = false;
        registering = false;
        queue.clear();
        draining = false;
        mouse.resetState();
        keyboard.resetState();
    }

    // ------------------------------------------------------------ actions

    /** Connects to a computer. Starts the HID profile first if needed. */
    public boolean connect(BluetoothDevice device) {
        if (adapter == null || !adapter.isEnabled() || !hasConnectPermission()) return false;
        userDisconnect = false;
        reconnectAttempts = 0;
        handler.removeCallbacksAndMessages(reconnectToken);
        if (hid == null || !registered) {
            pending = device;
            start();
            return true;
        }
        return doConnect(device);
    }

    /** Connects to a previously saved computer by its address. */
    public boolean connectSaved(String address) {
        if (adapter == null) return false;
        try {
            return connect(adapter.getRemoteDevice(address));
        } catch (IllegalArgumentException e) {
            fail(R.string.err_connect_failed, false);
            return false;
        }
    }

    private boolean doConnect(BluetoothDevice device) {
        setState(ConnectionState.CONNECTING, 0);
        armConnectTimeout();
        boolean ok;
        try {
            ok = hid.connect(device);
        } catch (SecurityException e) {
            fail(R.string.err_permission, false);
            return false;
        }
        if (!ok) {
            fail(R.string.err_connect_failed, false);
            return false;
        }
        return true;
    }

    /** Disconnects, or cancels a connection attempt in progress. */
    public void disconnect() {
        handler.removeCallbacksAndMessages(reconnectToken);
        handler.removeCallbacks(connectTimeout);
        pending = null;
        if (hid != null && host != null) {
            userDisconnect = true;
            hid.disconnect(host);
        } else {
            setState(idleState(), 0);
        }
    }

    /** Tell the controller the phone is (or is no longer) visible to other devices. */
    public void setDiscoverable(boolean on) {
        discoverable = on;
        handler.removeCallbacks(discoverableEnd);
        if (on) {
            handler.postDelayed(discoverableEnd, DISCOVERABLE_MS);
            if (state == ConnectionState.DISCONNECTED || state == ConnectionState.FAILED) {
                setState(ConnectionState.DISCOVERABLE, 0);
            }
        } else if (state == ConnectionState.DISCOVERABLE) {
            setState(ConnectionState.DISCONNECTED, 0);
        }
    }

    // ------------------------------------------------------------ sending

    @Override
    public boolean send(int reportId, byte[] data) {
        if (hid == null || host == null || state != ConnectionState.CONNECTED) return false;
        if (reportId == HidDescriptor.REPORT_MOUSE) return sendNow(reportId, data);
        queue.add(new Report(reportId, data));
        if (!draining) {
            draining = true;
            handler.post(drain);
        }
        return true;
    }

    private boolean sendNow(int reportId, byte[] data) {
        BluetoothHidDevice h = hid;
        BluetoothDevice d = host;
        if (h == null || d == null || state != ConnectionState.CONNECTED) return false;
        try {
            return h.sendReport(d, reportId, data);
        } catch (SecurityException e) {
            return false;
        }
    }

    private final Runnable drain = new Runnable() {
        @Override
        public void run() {
            Report r = queue.poll();
            if (r == null) {
                draining = false;
                return;
            }
            sendNow(r.id, r.data);
            handler.postDelayed(this, SEND_GAP_MS);
        }
    };

    // ------------------------------------------------------------ app lifecycle

    /** The app came to the front: start the HID service and connect to the last computer straight away. */
    public void onAppForeground() {
        foreground = true;
        handler.removeCallbacks(shutdownRunnable);
        if (adapter == null || !adapter.isEnabled() || !hasConnectPermission()) return;
        if (hid == null || !registered) {
            autoConnectPending = prefs.isAutoConnect();
            start();
            return;
        }
        maybeAutoConnect();
    }

    /** The app left the screen. Everything is shut down after a short grace period. */
    public void onAppBackground() {
        foreground = false;
        handler.removeCallbacks(shutdownRunnable);
        handler.postDelayed(shutdownRunnable, BACKGROUND_GRACE_MS);
    }

    private final Runnable shutdownRunnable = new Runnable() {
        @Override
        public void run() {
            if (foreground) return;
            boolean busy = discoverable
                    || state == ConnectionState.CONNECTING
                    || state == ConnectionState.RECONNECTING
                    || System.currentTimeMillis() - lastConnectedAt < KEEP_AFTER_CONNECT_MS;
            if (busy) {
                handler.postDelayed(this, 10000);
                return;
            }
            shutdownNow();
        }
    };

    /** Disconnects and releases the Bluetooth HID profile so the phone uses no battery for it. */
    public void shutdownNow() {
        handler.removeCallbacksAndMessages(reconnectToken);
        handler.removeCallbacks(connectTimeout);
        handler.removeCallbacks(initTimeout);
        handler.removeCallbacks(discoverableEnd);
        discoverable = false;
        autoConnectPending = false;
        userDisconnect = false;
        reconnectAttempts = 0;
        if (hid != null) {
            try {
                if (host != null) hid.disconnect(host);
                hid.unregisterApp();
            } catch (SecurityException | IllegalStateException ignored) {
                // closing anyway
            }
        }
        teardown();
        setState(ConnectionState.DISCONNECTED, 0);
    }

    private void maybeAutoConnect() {
        if (!prefs.isAutoConnect()) return;
        if (state == ConnectionState.CONNECTED || state == ConnectionState.CONNECTING
                || state == ConnectionState.RECONNECTING) return;
        SavedDevice last = repo.getLast();
        if (last != null) connectSaved(last.address);
    }

    // ------------------------------------------------------------ internals

    private ConnectionState idleState() {
        return discoverable ? ConnectionState.DISCOVERABLE : ConnectionState.DISCONNECTED;
    }

    private void setState(ConnectionState s, int err) {
        state = s;
        errorRes = err;
        handler.post(new Runnable() {
            @Override
            public void run() {
                for (Listener l : listeners) l.onStateChanged(state);
            }
        });
    }

    private void fail(int messageRes, boolean tearDown) {
        handler.removeCallbacks(connectTimeout);
        handler.removeCallbacks(initTimeout);
        if (tearDown) teardown();
        setState(ConnectionState.FAILED, messageRes);
    }

    private void armConnectTimeout() {
        handler.removeCallbacks(connectTimeout);
        handler.postDelayed(connectTimeout, CONNECT_TIMEOUT_MS);
    }

    private final Runnable initTimeout = new Runnable() {
        @Override
        public void run() {
            if (!registered) {
                fail(hid != null ? R.string.err_register : R.string.err_hid_unavailable, true);
            }
        }
    };

    private final Runnable connectTimeout = new Runnable() {
        @Override
        public void run() {
            if (state == ConnectionState.CONNECTING || state == ConnectionState.RECONNECTING) {
                fail(R.string.err_timeout, false);
            }
        }
    };

    private final Runnable discoverableEnd = new Runnable() {
        @Override
        public void run() {
            discoverable = false;
            if (state == ConnectionState.DISCOVERABLE) setState(ConnectionState.DISCONNECTED, 0);
        }
    };

    private final BluetoothProfile.ServiceListener serviceListener = new BluetoothProfile.ServiceListener() {
        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            if (profile != BluetoothProfile.HID_DEVICE) return;
            hid = (BluetoothHidDevice) proxy;
            register();
        }

        @Override
        public void onServiceDisconnected(int profile) {
            if (profile != BluetoothProfile.HID_DEVICE) return;
            boolean wasUp = state == ConnectionState.CONNECTED;
            hid = null;
            host = null;
            proxyRequested = false;
            registered = false;
            mouse.resetState();
            keyboard.resetState();
            if (adapter != null && adapter.isEnabled()) {
                setState(wasUp ? ConnectionState.FAILED : ConnectionState.DISCONNECTED,
                        wasUp ? R.string.err_unexpected_disconnect : 0);
            }
        }
    };

    private final BluetoothHidDevice.Callback callback = new BluetoothHidDevice.Callback() {
        @Override
        public void onAppStatusChanged(BluetoothDevice pluggedDevice, boolean isRegistered) {
            if (hid == null) return; // late callback from a profile we already closed
            registering = false;
            registered = isRegistered;
            if (isRegistered) {
                handler.removeCallbacks(initTimeout);
                if (state == ConnectionState.INITIALIZING) setState(idleState(), 0);
                if (pending != null) {
                    BluetoothDevice d = pending;
                    pending = null;
                    autoConnectPending = false;
                    doConnect(d);
                } else if (autoConnectPending) {
                    autoConnectPending = false;
                    maybeAutoConnect();
                }
            } else if (state == ConnectionState.INITIALIZING) {
                fail(R.string.err_register, true);
            }
        }

        @Override
        public void onConnectionStateChanged(BluetoothDevice device, int newState) {
            if (hid == null) return;
            switch (newState) {
                case BluetoothProfile.STATE_CONNECTING:
                    host = device;
                    setState(state == ConnectionState.RECONNECTING
                            ? ConnectionState.RECONNECTING : ConnectionState.CONNECTING, 0);
                    armConnectTimeout();
                    break;
                case BluetoothProfile.STATE_CONNECTED:
                    host = device;
                    pending = null;
                    reconnectAttempts = 0;
                    handler.removeCallbacks(connectTimeout);
                    handler.removeCallbacksAndMessages(reconnectToken);
                    discoverable = false;
                    lastConnectedAt = System.currentTimeMillis();
                    handler.removeCallbacks(discoverableEnd);
                    String name = hasConnectPermission() ? device.getName() : null;
                    repo.save(name != null ? name : device.getAddress(), device.getAddress());
                    setState(ConnectionState.CONNECTED, 0);
                    break;
                case BluetoothProfile.STATE_DISCONNECTED:
                    handleDisconnected(device);
                    break;
                default:
                    break; // DISCONNECTING: wait for DISCONNECTED
            }
        }
    };

    private void handleDisconnected(BluetoothDevice device) {
        if (host != null && device != null && !device.equals(host)) return;
        boolean wasConnected = state == ConnectionState.CONNECTED;
        BluetoothDevice last = host != null ? host : device;
        host = null;
        queue.clear();
        mouse.resetState();
        keyboard.resetState();
        handler.removeCallbacks(connectTimeout);

        if (userDisconnect) {
            userDisconnect = false;
            setState(idleState(), 0);
            return;
        }
        if ((wasConnected || state == ConnectionState.RECONNECTING)
                && prefs.isAutoReconnect() && last != null && reconnectAttempts < MAX_RECONNECTS) {
            reconnectAttempts++;
            setState(ConnectionState.RECONNECTING, 0);
            scheduleReconnect(last);
            return;
        }
        if (wasConnected) {
            fail(R.string.err_unexpected_disconnect, false);
        } else if (state == ConnectionState.CONNECTING || state == ConnectionState.RECONNECTING) {
            fail(R.string.err_connect_failed, false);
        } else if (state != ConnectionState.FAILED) {
            setState(idleState(), 0);
        }
    }

    private void scheduleReconnect(final BluetoothDevice device) {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (state != ConnectionState.RECONNECTING || hid == null) return;
                armConnectTimeout();
                boolean ok;
                try {
                    ok = hid.connect(device);
                } catch (SecurityException e) {
                    ok = false;
                }
                if (!ok) fail(R.string.err_connect_failed, false);
            }
        }, reconnectToken, RECONNECT_DELAY_MS);
    }

    private final BroadcastReceiver btReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int s = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
            if (s == BluetoothAdapter.STATE_TURNING_OFF || s == BluetoothAdapter.STATE_OFF) {
                handler.removeCallbacksAndMessages(reconnectToken);
                handler.removeCallbacks(connectTimeout);
                discoverable = false;
                handler.removeCallbacks(discoverableEnd);
                teardown();
                setState(ConnectionState.DISCONNECTED, 0);
            } else if (s == BluetoothAdapter.STATE_ON) {
                setState(ConnectionState.DISCONNECTED, 0);
                if (foreground) onAppForeground();
            }
        }
    };
}
