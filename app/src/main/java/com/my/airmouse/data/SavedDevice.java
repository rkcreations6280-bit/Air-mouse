package com.my.airmouse.data;

/** A computer the phone has connected to before. */
public final class SavedDevice {
    public final String name;
    public final String address;
    public final long lastConnected;

    public SavedDevice(String name, String address, long lastConnected) {
        this.name = name;
        this.address = address;
        this.lastConnected = lastConnected;
    }
}
