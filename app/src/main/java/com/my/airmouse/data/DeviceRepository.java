package com.my.airmouse.data;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Stores previously connected computers (name, address, last connection time). */
public final class DeviceRepository {

    private static final String KEY = "saved_devices";
    private static DeviceRepository instance;

    public static synchronized DeviceRepository get(Context c) {
        if (instance == null) instance = new DeviceRepository(c.getApplicationContext());
        return instance;
    }

    private final PreferencesManager prefs;

    private DeviceRepository(Context c) {
        prefs = PreferencesManager.get(c);
    }

    /** Newest connection first. */
    public List<SavedDevice> list() {
        List<SavedDevice> out = new ArrayList<>();
        String raw = prefs.getRaw(KEY);
        if (raw != null) {
            try {
                JSONArray arr = new JSONArray(raw);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    out.add(new SavedDevice(o.optString("name", ""), o.getString("address"), o.optLong("last", 0)));
                }
            } catch (JSONException e) {
                // corrupt data: start again with an empty list
            }
        }
        Collections.sort(out, new Comparator<SavedDevice>() {
            @Override
            public int compare(SavedDevice a, SavedDevice b) {
                return Long.compare(b.lastConnected, a.lastConnected);
            }
        });
        return out;
    }

    public SavedDevice getLast() {
        List<SavedDevice> all = list();
        return all.isEmpty() ? null : all.get(0);
    }

    /** Adds the device, or refreshes its name and time if already saved. */
    public void save(String name, String address) {
        List<SavedDevice> all = list();
        List<SavedDevice> next = new ArrayList<>();
        for (SavedDevice d : all) if (!d.address.equals(address)) next.add(d);
        next.add(new SavedDevice(name, address, System.currentTimeMillis()));
        write(next);
    }

    public void remove(String address) {
        List<SavedDevice> next = new ArrayList<>();
        for (SavedDevice d : list()) if (!d.address.equals(address)) next.add(d);
        write(next);
    }

    private void write(List<SavedDevice> devices) {
        JSONArray arr = new JSONArray();
        try {
            for (SavedDevice d : devices) {
                JSONObject o = new JSONObject();
                o.put("name", d.name);
                o.put("address", d.address);
                o.put("last", d.lastConnected);
                arr.put(o);
            }
        } catch (JSONException e) {
            return;
        }
        prefs.putRaw(KEY, arr.toString());
    }
}
