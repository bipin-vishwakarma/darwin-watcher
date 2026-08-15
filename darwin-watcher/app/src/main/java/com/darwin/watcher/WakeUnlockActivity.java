package com.darwin.watcher;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.view.View;
import android.view.WindowManager;

public class WakeUnlockActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Black background for smooth visual transition
        View bgView = new View(this);
        bgView.setBackgroundColor(Color.BLACK);
        setContentView(bgView);

        // Turn screen on and show above lock screen across all Android/MIUI versions
        getWindow().addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            try {
                setShowWhenLocked(true);
                setTurnScreenOn(true);
                KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
                if (km != null) {
                    km.requestDismissKeyguard(this, null);
                }
            } catch (Exception ignored) { }
        }

        // Acquire wake lock
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                PowerManager.WakeLock wl = pm.newWakeLock(
                    PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE,
                    "darwin:wake_unlock_activity"
                );
                wl.acquire(15000);
            }
        } catch (Exception ignored) { }

        // Launch target app
        final String targetPkg = getIntent() != null ? getIntent().getStringExtra("target_package") : null;
        if (targetPkg != null && targetPkg.trim().length() > 0) {
            Intent launch = getPackageManager().getLaunchIntentForPackage(targetPkg);
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(launch);
            }
        }

        // Finish after 1.5 seconds once target app has launched into foreground
        new Handler(Looper.getMainLooper()).postDelayed(new FinishRunnable(this), 1500);
    }

    private static final class FinishRunnable implements Runnable {
        private final Activity activity;

        FinishRunnable(Activity activity) {
            this.activity = activity;
        }

        @Override
        public void run() {
            if (activity != null && !activity.isFinishing()) {
                activity.finish();
            }
        }
    }
}
