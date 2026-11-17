package com.my.airmouse.bt;

import com.my.airmouse.R;

/** Every state the Bluetooth HID connection can be in. One source of truth for the whole UI. */
public enum ConnectionState {
    DISCONNECTED(R.string.state_disconnected, R.color.color_text_secondary, false),
    INITIALIZING(R.string.state_initializing, R.color.color_warning, true),
    DISCOVERABLE(R.string.state_discoverable, R.color.primary, true),
    CONNECTING(R.string.state_connecting, R.color.color_warning, true),
    CONNECTED(R.string.state_connected, R.color.color_success, false),
    RECONNECTING(R.string.state_reconnecting, R.color.color_warning, true),
    FAILED(R.string.state_failed, R.color.color_error, false);

    public final int labelRes;
    public final int colorRes;
    /** True while something is in progress (show a progress indicator / pulse). */
    public final boolean busy;

    ConnectionState(int labelRes, int colorRes, boolean busy) {
        this.labelRes = labelRes;
        this.colorRes = colorRes;
        this.busy = busy;
    }
}
