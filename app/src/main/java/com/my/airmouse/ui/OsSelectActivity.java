package com.my.airmouse.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.google.android.material.card.MaterialCardView;
import com.my.airmouse.R;
import com.my.airmouse.data.PreferencesManager;

/** Choose Windows or macOS. The choice drives key labels and shortcut mappings. */
public class OsSelectActivity extends BaseActivity {

    private PreferencesManager prefs;
    private PreferencesManager.Os selected;
    private MaterialCardView winCard, macCard;
    private View winCheck, macCheck;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_os_select);
        applyInsets(findViewById(R.id.os_root), true);

        prefs = PreferencesManager.get(this);
        selected = prefs.getOsOrDefault();

        winCard = findViewById(R.id.card_windows);
        macCard = findViewById(R.id.card_mac);
        winCheck = findViewById(R.id.check_windows);
        macCheck = findViewById(R.id.check_mac);

        winCard.setOnClickListener(v -> select(PreferencesManager.Os.WINDOWS));
        macCard.setOnClickListener(v -> select(PreferencesManager.Os.MAC));
        findViewById(R.id.os_continue).setOnClickListener(v -> {
            prefs.setOs(selected);
            prefs.setOnboardingDone(true);
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
        select(selected);
    }

    private void select(PreferencesManager.Os os) {
        selected = os;
        style(winCard, winCheck, os == PreferencesManager.Os.WINDOWS);
        style(macCard, macCheck, os == PreferencesManager.Os.MAC);
    }

    private void style(MaterialCardView card, View check, boolean on) {
        card.setStrokeWidth(Math.round((on ? 2 : 1) * getResources().getDisplayMetrics().density));
        card.setStrokeColor(ContextCompat.getColor(this, on ? R.color.primary : R.color.color_outline));
        check.setVisibility(on ? View.VISIBLE : View.INVISIBLE);
    }
}
