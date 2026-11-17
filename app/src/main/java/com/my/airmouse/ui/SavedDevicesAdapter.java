package com.my.airmouse.ui;

import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.my.airmouse.R;
import com.my.airmouse.data.SavedDevice;

import java.util.ArrayList;
import java.util.List;

/** List of previously connected computers. */
public final class SavedDevicesAdapter extends RecyclerView.Adapter<SavedDevicesAdapter.Holder> {

    public interface Listener {
        void onConnect(SavedDevice device);

        void onRemove(SavedDevice device);
    }

    private final List<SavedDevice> items = new ArrayList<>();
    private final Listener listener;
    private String connectedAddress;

    public SavedDevicesAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<SavedDevice> list, String connectedAddress) {
        items.clear();
        items.addAll(list);
        this.connectedAddress = connectedAddress;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_saved_device, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        final SavedDevice d = items.get(position);
        boolean connected = d.address.equals(connectedAddress);
        h.name.setText(d.name.isEmpty() ? d.address : d.name);
        if (connected) {
            h.info.setText(R.string.connected_now);
            h.connect.setVisibility(View.GONE);
        } else {
            CharSequence ago = DateUtils.getRelativeTimeSpanString(d.lastConnected,
                    System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
            h.info.setText(h.itemView.getContext().getString(R.string.last_connected, ago));
            h.connect.setVisibility(View.VISIBLE);
        }
        h.connect.setOnClickListener(v -> listener.onConnect(d));
        h.remove.setOnClickListener(v -> listener.onRemove(d));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView name, info;
        final MaterialButton connect;
        final ImageButton remove;

        Holder(View v) {
            super(v);
            name = v.findViewById(R.id.saved_name);
            info = v.findViewById(R.id.saved_info);
            connect = v.findViewById(R.id.saved_connect);
            remove = v.findViewById(R.id.saved_remove);
        }
    }
}
