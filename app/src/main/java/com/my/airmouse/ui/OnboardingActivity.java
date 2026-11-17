package com.my.airmouse.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.button.MaterialButton;
import com.my.airmouse.R;

/** Four intro pages with page dots, Skip and Next / Get started. */
public class OnboardingActivity extends BaseActivity {

    private static final int[] ICONS = {R.drawable.ic_mouse, R.drawable.ic_touchpad,
            R.drawable.ic_keyboard, R.drawable.ic_bluetooth};
    private static final int[] TITLES = {R.string.onb1_title, R.string.onb2_title,
            R.string.onb3_title, R.string.onb4_title};
    private static final int[] TEXTS = {R.string.onb1_text, R.string.onb2_text,
            R.string.onb3_text, R.string.onb4_text};

    private ViewPager2 pager;
    private LinearLayout dots;
    private MaterialButton next;
    private View skip;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);
        applyInsets(findViewById(R.id.onb_root), true);

        pager = findViewById(R.id.onb_pager);
        dots = findViewById(R.id.onb_dots);
        next = findViewById(R.id.onb_next);
        skip = findViewById(R.id.onb_skip);

        pager.setAdapter(new PageAdapter());
        buildDots();
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateUi(position);
            }
        });
        updateUi(0);

        skip.setOnClickListener(v -> finishOnboarding());
        next.setOnClickListener(v -> {
            int p = pager.getCurrentItem();
            if (p < ICONS.length - 1) pager.setCurrentItem(p + 1, true);
            else finishOnboarding();
        });
    }

    private void finishOnboarding() {
        startActivity(new Intent(this, OsSelectActivity.class));
        finish();
    }

    private void buildDots() {
        dots.removeAllViews();
        int size = dp(8);
        for (int i = 0; i < ICONS.length; i++) {
            View d = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(dp(4), 0, dp(4), 0);
            d.setLayoutParams(lp);
            d.setBackgroundResource(R.drawable.bg_dot_shape);
            dots.addView(d);
        }
    }

    private void updateUi(int position) {
        for (int i = 0; i < dots.getChildCount(); i++) {
            View d = dots.getChildAt(i);
            boolean on = i == position;
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) d.getLayoutParams();
            lp.width = on ? dp(24) : dp(8);
            d.setLayoutParams(lp);
            d.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(this, on ? R.color.primary : R.color.color_outline)));
        }
        boolean last = position == ICONS.length - 1;
        next.setText(last ? R.string.get_started : R.string.next);
        skip.setVisibility(last ? View.INVISIBLE : View.VISIBLE);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private static final class PageHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView title, text;

        PageHolder(View v) {
            super(v);
            icon = v.findViewById(R.id.page_icon);
            title = v.findViewById(R.id.page_title);
            text = v.findViewById(R.id.page_text);
        }
    }

    private static final class PageAdapter extends RecyclerView.Adapter<PageHolder> {
        @NonNull
        @Override
        public PageHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new PageHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_onboarding_page, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull PageHolder h, int position) {
            h.icon.setImageResource(ICONS[position]);
            h.title.setText(TITLES[position]);
            h.text.setText(TEXTS[position]);
        }

        @Override
        public int getItemCount() {
            return ICONS.length;
        }
    }
}
