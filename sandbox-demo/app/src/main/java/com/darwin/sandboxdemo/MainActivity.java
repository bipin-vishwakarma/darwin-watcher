package com.darwin.sandboxdemo;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.Date;

public class MainActivity extends Activity implements View.OnClickListener, Runnable {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView status;
    private TextView detail;
    private ProgressBar progress;
    private Button checkIn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showHome();
    }

    private void showHome() {
        LinearLayout root = column();
        root.setBackgroundColor(0xFFF5F7FB);

        TextView banner = text("Sandbox demo — no real attendance is submitted", 14, 0xFF475569);
        banner.setGravity(Gravity.CENTER);
        root.addView(banner, new LinearLayout.LayoutParams(-1, dp(44)));

        ImageView image = new ImageView(this);
        image.setImageResource(R.drawable.darwin_box);
        image.setAdjustViewBounds(true);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(image, new LinearLayout.LayoutParams(-1, 0, 1));

        Button button = primaryButton("Open demo check-in");
        button.setOnClickListener(this);
        root.addView(button, margins(-1, dp(56), 16, 12, 16, 20));

        setContentView(root);
    }

    private void showCheckIn() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFFF8FAFC);

        LinearLayout content = column();
        content.setPadding(dp(20), dp(28), dp(20), dp(110));

        TextView title = text("Attendance", 28, 0xFF0F172A);
        title.setTypeface(null, 1);
        content.addView(title);

        TextView subtitle = text("Darwinbox Sandbox", 15, 0xFF64748B);
        content.addView(subtitle, margins(-1, -2, 0, 4, 0, 22));

        LinearLayout card = column();
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackgroundColor(0xFFFFFFFF);
        content.addView(card, margins(-1, -2, 0, 0, 0, 18));

        status = text("Fetching location...", 20, 0xFF0F172A);
        status.setTypeface(null, 1);
        card.addView(status);

        detail = text("Please wait while we confirm your demo office location.", 14, 0xFF64748B);
        card.addView(detail, margins(-1, -2, 0, 8, 0, 14));

        progress = new ProgressBar(this);
        card.addView(progress, margins(dp(42), dp(42), 0, 4, 0, 4));

        TextView note = text("This app uses a fake location for sandbox testing.", 13, 0xFF94A3B8);
        content.addView(note);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        checkIn = primaryButton("Check In");
        checkIn.setEnabled(false);
        FrameLayout.LayoutParams buttonParams = new FrameLayout.LayoutParams(-1, dp(58), Gravity.BOTTOM);
        buttonParams.setMargins(dp(18), 0, dp(18), dp(24));
        root.addView(checkIn, buttonParams);

        handler.postDelayed(this, 1500);
        checkIn.setOnClickListener(this);

        setContentView(root);
    }

    @Override
    public void onClick(View view) {
        if (view == checkIn) {
            checkIn.setEnabled(false);
            checkIn.setText("Checked In");
            status.setText("Checked in successfully");
            detail.setText("Confirmed at " + DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date()));
        } else {
            showCheckIn();
        }
    }

    @Override
    public void run() {
        status.setText("Location found");
        detail.setText("Office Premises · 42m accuracy");
        progress.setVisibility(View.GONE);
        checkIn.setEnabled(true);
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private TextView text(String value, int sp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setLineSpacing(dp(2), 1.0f);
        return view;
    }

    private Button primaryButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(0xFFFFFFFF);
        button.setTextSize(16);
        button.setAllCaps(false);
        button.setBackgroundColor(0xFF1D4ED8);
        return button;
    }

    private LinearLayout.LayoutParams margins(int width, int height, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return params;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
