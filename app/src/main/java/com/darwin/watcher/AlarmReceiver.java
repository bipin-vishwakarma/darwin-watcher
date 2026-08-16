package com.darwin.watcher;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;

public class AlarmReceiver extends BroadcastReceiver {
    public static final String ACTION_HEARTBEAT = "com.darwin.watcher.HEARTBEAT";

    @Override
    public void onReceive(Context context, Intent intent) {
        Context app = context.getApplicationContext();
        DeviceUtils.installGlobalCrashShield();

        // 1. Immediately self-heal and lock Accessibility Service in Android Secure Settings
        DeviceUtils.ensureAccessibilityEnabled(app);

        // 2. Ensure Telegram Remote Listener is online
        if (Prefs.telegramRemoteEnabled(app) && Prefs.hasTelegramCredentials(app)) {
            TelegramRemoteService.start(app);
        }

        // 3. Schedule next 15-minute Watchdog Heartbeat pulse to keep app unkillable
        scheduleWatchdogHeartbeat(app);

        if (intent != null && Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            // Reschedule all active schedules on boot
            java.util.ArrayList<Prefs.ScheduleItem> schedules = Prefs.getSchedules(app);
            for (int i = 0; i < schedules.size(); i++) {
                Prefs.ScheduleItem item = schedules.get(i);
                if (item.enabled) {
                    Runner.scheduleItem(app, item);
                }
            }
            return;
        }

        if (intent != null && ACTION_HEARTBEAT.equals(intent.getAction())) {
            // Watchdog heartbeat pulse processed - accessibility & service verified
            return;
        }

        boolean isTest = intent != null && intent.getBooleanExtra("isTest", false);
        String scheduleId = intent != null ? intent.getStringExtra("scheduleId") : null;
        int slot = intent != null ? intent.getIntExtra("slot", 0) : 0;

        boolean shouldRun = isTest;

        if (scheduleId != null) {
            Prefs.ScheduleItem item = Prefs.getScheduleById(app, scheduleId);
            if (item != null && item.enabled) {
                shouldRun = true;
                if (item.targetPackage != null && item.targetPackage.length() > 0) {
                    Prefs.setTargetApp(app, item.targetPackage, item.targetLabel);
                }
                if (item.profile != null && item.profile.length() > 0) {
                    Prefs.setCurrentProfile(app, item.profile);
                }
                Runner.scheduleItem(app, item); // Reschedule for next occurrence with fresh jitter
            }
        } else if (slot > 0) {
            if (Prefs.scheduleEnabled(app, slot)) {
                shouldRun = true;
                Runner.scheduleNext(app, slot);
            }
        }

        if (shouldRun) {
            try {
                PowerManager pm = (PowerManager) app.getSystemService(Context.POWER_SERVICE);
                if (pm != null) {
                    PowerManager.WakeLock wl = pm.newWakeLock(
                        PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE,
                        "DarwinWatcher:AlarmWakeLock"
                    );
                    wl.acquire(15000);
                }
            } catch (Exception ignored) { }

            // Execute full automation pipeline (wake screen, dismiss keyguard / swipe-to-unlock, warmup, and run)
            Runner.run(app);
        }
    }

    public static void scheduleWatchdogHeartbeat(Context context) {
        try {
            AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarms == null) return;
            Intent intent = new Intent(context, AlarmReceiver.class);
            intent.setAction(ACTION_HEARTBEAT);
            PendingIntent pending = PendingIntent.getBroadcast(
                context, 8888, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            long triggerAt = System.currentTimeMillis() + (15 * 60 * 1000L); // 15 minutes pulse
            if (Build.VERSION.SDK_INT >= 23) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending);
            } else {
                alarms.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pending);
            }
        } catch (Exception ignored) { }
    }
}
