package com.my.airmouse.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.my.airmouse.R;
import com.my.airmouse.data.PreferencesManager;

/** Step by step connection tutorial, separate for Windows and macOS. */
public class GuideActivity extends BaseActivity {

    private LinearLayout steps;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_guide);
        applyInsets(findViewById(R.id.guide_root), true);

        steps = findViewById(R.id.guide_steps);
        findViewById(R.id.guide_back).setOnClickListener(v -> finish());

        MaterialButtonToggleGroup toggle = findViewById(R.id.guide_toggle);
        boolean mac = PreferencesManager.get(this).getOsOrDefault() == PreferencesManager.Os.MAC;
        toggle.check(mac ? R.id.guide_mac : R.id.guide_windows);
        toggle.addOnButtonCheckedListener((group, id, checked) -> {
            if (checked) show(id == R.id.guide_mac);
        });
        show(mac);
    }

    private void show(boolean mac) {
        steps.removeAllViews();
        String[] list = getResources().getStringArray(mac ? R.array.guide_steps_mac : R.array.guide_steps_windows);
        for (int i = 0; i < list.length; i++) {
            View row = getLayoutInflater().inflate(R.layout.item_guide_step, steps, false);
            ((TextView) row.findViewById(R.id.step_number)).setText(String.valueOf(i + 1));
            ((TextView) row.findViewById(R.id.step_text)).setText(list[i]);
            steps.addView(row);
        }
    }
}
