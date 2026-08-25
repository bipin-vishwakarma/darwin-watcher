package com.darwin.watcher;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

public final class Runner {
    private static final String CHANNEL = "darwin_watcher";

    private Runner() { }

    public static void run(Context context) {
        final Context app = context.getApplicationContext();
        DeviceUtils.installGlobalCrashShield();
        DeviceUtils.ensureAccessibilityEnabled(app);

        WatcherAccessibilityService service = WatcherAccessibilityService.current();
        if (service == null) {
            new Handler(Looper.getMainLooper()).postDelayed(new DelayedRetryRunner(app), 800);
            return;
        }

        ArrayList<Action> actions;
        try {
            actions = parse(Prefs.actions(app));
        } catch (IllegalArgumentException exc) {
            status(app, exc.getMessage());
            notify(app, "Darwin Watcher blocked", exc.getMessage());
            return;
        }

        String packageName = Prefs.targetPackage(app);
        String label = Prefs.targetLabel(app);
        service.showRunningWatermark("Starting " + label);
        status(app, "Starting " + label);
        if (!openTarget(app)) {
            service.showRunningWatermark("Stopped: app not installed");
            new Handler(Looper.getMainLooper()).postDelayed(new HideWatermark(service), 1200);
            return;
        }
        service.showRunningWatermark("Opening " + label);
        status(app, "Waiting for " + label + " to open");
        notify(app, "Darwin Watcher", "Started " + label + " navigation");
        new Handler(Looper.getMainLooper()).postDelayed(new WaitForTarget(app, service, actions, packageName, label, 0), 400);
    }

    public static boolean isTargetInstalled(Context context) {
        return context.getPackageManager().getLaunchIntentForPackage(Prefs.targetPackage(context)) != null;
    }

    public static boolean openTarget(Context context) {
        String packageName = Prefs.targetPackage(context);
        String label = Prefs.targetLabel(context);

        // 1. Wake screen if asleep
        try {
            android.os.PowerManager pm = (android.os.PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                android.os.PowerManager.WakeLock wl = pm.newWakeLock(
                    android.os.PowerManager.FULL_WAKE_LOCK | android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP | android.os.PowerManager.ON_AFTER_RELEASE,
                    "darwin:runner_wake_screen"
                );
                wl.acquire(15000);
            }
        } catch (Exception ignored) { }

        // 2. Clear stale foreground package in accessibility service
        WatcherAccessibilityService service = WatcherAccessibilityService.current();
        if (service != null) {
            service.resetForegroundPackage();
        }

        // 3. Kill background processes for fresh start
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                am.killBackgroundProcesses(packageName);
            }
        } catch (Exception ignored) { }

        // 4. Check if target is installed
        Intent launch = context.getPackageManager().getLaunchIntentForPackage(packageName);
        if (launch == null) {
            status(context, label + " is not installed");
            return false;
        }

        // 5. Launch WakeUnlockActivity to turn screen on, dismiss keyguard, and forward to target
        try {
            Intent unlockIntent = new Intent(context, WakeUnlockActivity.class);
            unlockIntent.putExtra("target_package", packageName);
            unlockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            context.startActivity(unlockIntent);
        } catch (Exception ignored) { }

        // 6. Direct launch from AccessibilityService (exempt from background activity start limits)
        if (service != null) {
            service.launchTarget(packageName);
        } else {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(launch);
        }

        status(context, "Opened " + label);
        return true;
    }

    public static void scheduleItem(Context context, Prefs.ScheduleItem item) {
        if (item == null || !item.enabled) return;
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) {
            status(context, "Alarm service unavailable");
            return;
        }

        Calendar when = Calendar.getInstance();
        when.set(Calendar.HOUR_OF_DAY, item.hour);
        when.set(Calendar.MINUTE, item.minute);
        when.set(Calendar.SECOND, 0);
        when.set(Calendar.MILLISECOND, 0);

        long jitterMillis = 0;
        if (item.randomWindowMinutes > 0) {
            int rangeSec = item.randomWindowMinutes * 60;
            int randomOffsetSec = (int) ((Math.random() * (rangeSec * 2)) - rangeSec);
            jitterMillis = randomOffsetSec * 1000L;
        }

        long now = System.currentTimeMillis();
        long triggerTime = when.getTimeInMillis() + jitterMillis;
        if (triggerTime <= now) {
            when.add(Calendar.DAY_OF_MONTH, 1);
            triggerTime = when.getTimeInMillis() + jitterMillis;
        }

        // Never re-arm on a date this schedule already ran.
        //
        // Re-arming happens immediately after a fire, and rolls a FRESH jitter. Fire at
        // 08:05 (jitter -5), re-roll to +5, and the new trigger is 08:15 - still ahead of
        // now, so the guard above does not advance the day and the schedule runs a second
        // time. With a toggle-style target (Darwinbox check-in/out) the second run undoes
        // the first. The same hazard exists on BOOT_COMPLETED, which re-arms everything
        // with no memory of what already ran today.
        if (Prefs.dateStamp(when).equals(Prefs.scheduleLastRunDate(context, item.id))) {
            when.add(Calendar.DAY_OF_MONTH, 1);
            triggerTime = when.getTimeInMillis() + jitterMillis;
        }

        // Advance calendar until day of week matches item.days
        int attempts = 0;
        while (!item.isCalendarDayActive(when.get(Calendar.DAY_OF_WEEK)) && attempts < 14) {
            when.add(Calendar.DAY_OF_MONTH, 1);
            triggerTime = when.getTimeInMillis() + jitterMillis;
            attempts++;
        }
        if (attempts >= 14) {
            status(context, "No active days for " + item.name);
            return;
        }

        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.putExtra("scheduleId", item.id);
        int reqCode = Math.abs(item.id.hashCode() % 100000);
        PendingIntent pending = PendingIntent.getBroadcast(context, reqCode, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (Build.VERSION.SDK_INT >= 31 && !alarms.canScheduleExactAlarms()) {
            status(context, "Allow exact alarms for schedules");
            Intent settings = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
            settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(settings);
            return;
        }

        try {
            if (Build.VERSION.SDK_INT >= 23) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pending);
            } else {
                alarms.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pending);
            }
            status(context, "Scheduled " + item.name + " (" + item.daysShort() + ", " + item.toleranceText() + ")");
        } catch (SecurityException exc) {
            alarms.set(AlarmManager.RTC_WAKEUP, triggerTime, pending);
            status(context, "Scheduled inexact: " + item.name);
        }
    }

    public static void cancelScheduleItem(Context context, String id) {
        if (id == null) return;
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) return;
        Intent intent = new Intent(context, AlarmReceiver.class);
        int reqCode = Math.abs(id.hashCode() % 100000);
        PendingIntent pending = PendingIntent.getBroadcast(context, reqCode, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        alarms.cancel(pending);
    }

    public static void scheduleNext(Context context, int slot) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) {
            status(context, "Alarm service unavailable");
            return;
        }

        Calendar when = Calendar.getInstance();
        when.set(Calendar.HOUR_OF_DAY, Prefs.scheduleHour(context, slot));
        when.set(Calendar.MINUTE, Prefs.scheduleMinute(context, slot));
        when.set(Calendar.SECOND, 0);
        when.set(Calendar.MILLISECOND, 0);
        if (when.getTimeInMillis() <= System.currentTimeMillis()) {
            when.add(Calendar.DAY_OF_MONTH, 1);
        }

        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.putExtra("slot", slot);
        PendingIntent pending = PendingIntent.getBroadcast(context, slot, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (Build.VERSION.SDK_INT >= 31 && !alarms.canScheduleExactAlarms()) {
            status(context, "Allow exact alarms for schedules");
            Intent settings = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
            settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(settings);
            return;
        }

        try {
            if (Build.VERSION.SDK_INT >= 23) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when.getTimeInMillis(), pending);
            } else {
                alarms.setExact(AlarmManager.RTC_WAKEUP, when.getTimeInMillis(), pending);
            }
            status(context, "Scheduled next run");
        } catch (SecurityException exc) {
            alarms.set(AlarmManager.RTC_WAKEUP, when.getTimeInMillis(), pending);
            status(context, "Scheduled inexact run");
        }
    }

    public static void scheduleTestRunIn(Context context, int seconds) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) {
            status(context, "Alarm service unavailable");
            return;
        }

        long triggerAtMillis = System.currentTimeMillis() + (seconds * 1000L);
        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.putExtra("slot", Prefs.SLOT_MORNING);
        intent.putExtra("isTest", true);
        PendingIntent pending = PendingIntent.getBroadcast(context, 999, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (Build.VERSION.SDK_INT >= 31 && !alarms.canScheduleExactAlarms()) {
            status(context, "Allow exact alarms for schedules");
            Intent settings = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
            settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(settings);
            return;
        }

        try {
            if (Build.VERSION.SDK_INT >= 23) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending);
            } else {
                alarms.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending);
            }
            status(context, "Test alarm set for " + seconds + "s! Lock phone now.");
        } catch (SecurityException exc) {
            alarms.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending);
            status(context, "Test alarm set (inexact). Lock phone now.");
        }
    }

    public static void cancelSchedule(Context context, int slot) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.putExtra("slot", slot);
        PendingIntent pending = PendingIntent.getBroadcast(context, slot, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (alarms != null) {
            alarms.cancel(pending);
        }
    }

    private static ArrayList<Action> parse(String text) {
        String lower = text.toLowerCase(Locale.US);
        String[] risky = {"checkin", "check-in", "check in", "checkout", "check-out", "check out", "clockin", "clock-in", "clock in", "clockout", "clock-out", "clock out", "punchin", "punch-in", "punch in", "punchout", "punch-out", "punch out", "attendance", "shift", "approval", "approve", "login", "log in", "signin", "sign in", "credential", "credentials", "password", "passcode", "pin", "otp", "mfa", "2fa", "auth", "authenticate", "verification", "verify", "token", "secret"};
        for (int i = 0; i < risky.length; i++) {
            if (lower.indexOf(risky[i]) >= 0) {
                throw new IllegalArgumentException("Blocked risky word: " + risky[i]);
            }
        }

        ArrayList<Action> actions = new ArrayList<Action>();
        String[] lines = text.split("\\r?\\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.length() == 0) continue;
            if (actions.size() >= 20) throw new IllegalArgumentException("Max 20 actions allowed");

            String[] p = line.split("\\s+");
            try {
                if ("wait".equals(p[0]) && p.length == 2) {
                    int ms = (int) (Float.parseFloat(p[1]) * 1000);
                    if (ms < 1000 || ms > 50000) throw new NumberFormatException();
                    actions.add(new Action("wait", 0, 0, 0, 0, ms));
                } else if ("tap".equals(p[0]) && p.length == 3) {
                    actions.add(new Action("tap", coord(p[1]), coord(p[2]), 0, 0, 0));
                } else if ("swipe".equals(p[0]) && p.length == 6) {
                    int duration = Integer.parseInt(p[5]);
                    if (duration < 50 || duration > 3000) throw new NumberFormatException();
                    actions.add(new Action("swipe", coord(p[1]), coord(p[2]), coord(p[3]), coord(p[4]), duration));
                } else {
                    throw new IllegalArgumentException("Invalid action line " + (i + 1));
                }
            } catch (NumberFormatException exc) {
                throw new IllegalArgumentException("Invalid number on line " + (i + 1));
            }
        }
        if (actions.size() == 0) throw new IllegalArgumentException("Add at least one action");
        return actions;
    }

    private static int coord(String value) {
        int n = Integer.parseInt(value);
        if (n < 0 || n > 10000) throw new NumberFormatException();
        return n;
    }

    private static void status(Context context, String value) {
        Prefs.setLastStatus(context, value);
    }

    private static void notify(Context context, String title, String text) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "Darwin Watcher", NotificationManager.IMPORTANCE_DEFAULT);
            manager.createNotificationChannel(channel);
        }
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(context, CHANNEL) : new Notification.Builder(context);
        builder.setSmallIcon(R.drawable.ic_launcher_foreground).setContentTitle(title).setContentText(text).setAutoCancel(true);
        manager.notify(42, builder.build());
    }

    private static final class WaitForTarget implements Runnable {
        private final Context context;
        private final WatcherAccessibilityService service;
        private final ArrayList<Action> actions;
        private final String packageName;
        private final String label;
        private final int tries;

        WaitForTarget(Context context, WatcherAccessibilityService service, ArrayList<Action> actions, String packageName, String label, int tries) {
            this.context = context;
            this.service = service;
            this.actions = actions;
            this.packageName = packageName;
            this.label = label;
            this.tries = tries;
        }

        @Override
        public void run() {
            if (service.isDeviceLocked()) {
                service.showRunningWatermark("Unlocking device (" + (tries + 1) + "/25)");
                status(context, "Unlocking device...");
                service.unlockDevice();
                Runner.openTarget(context);
                new Handler(Looper.getMainLooper()).postDelayed(new WaitForTarget(context, service, actions, packageName, label, tries + 1), 600);
                return;
            }

            if (service.isTargetActive(packageName)) {
                service.showRunningWatermark("App ready · Resting (4s)...");
                status(context, "App ready · Resting (4s)...");
                new Handler(Looper.getMainLooper()).postDelayed(new WarmupCountdown(context, service, actions, packageName, label, 4), 1000);
                return;
            }
            if (tries == 2 || tries == 5 || tries == 8) {
                Runner.openTarget(context);
            }
            service.showRunningWatermark("Waiting for " + label + " " + (tries + 1) + "/25");
            if (tries >= 25) {
                service.showRunningWatermark("Stopped: app did not open");
                status(context, "Stopped: " + label + " did not open");
                Runner.notify(context, "Darwin Watcher stopped", label + " did not open");
                new Handler(Looper.getMainLooper()).postDelayed(new HideWatermark(service), 1200);
                return;
            }
            new Handler(Looper.getMainLooper()).postDelayed(new WaitForTarget(context, service, actions, packageName, label, tries + 1), 500);
        }
    }

    private static final class WarmupCountdown implements Runnable {
        private final Context context;
        private final WatcherAccessibilityService service;
        private final ArrayList<Action> actions;
        private final String packageName;
        private final String label;
        private final int secondsLeft;

        WarmupCountdown(Context context, WatcherAccessibilityService service, ArrayList<Action> actions, String packageName, String label, int secondsLeft) {
            this.context = context;
            this.service = service;
            this.actions = actions;
            this.packageName = packageName;
            this.label = label;
            this.secondsLeft = secondsLeft;
        }

        @Override
        public void run() {
            if (service.isDeviceLocked() || !service.isTargetActive(packageName)) {
                new Handler(Looper.getMainLooper()).postDelayed(new WaitForTarget(context, service, actions, packageName, label, 0), 500);
                return;
            }

            if (secondsLeft <= 0) {
                service.showRunningWatermark("Ready · Starting Step 1/" + actions.size());
                status(context, "Ready · Starting actions");
                new Handler(Looper.getMainLooper()).postDelayed(new Step(context, service, actions, packageName, label, 0), 500);
                return;
            }

            service.showRunningWatermark("App ready · Resting (" + secondsLeft + "s)...");
            status(context, "App ready · Resting (" + secondsLeft + "s)");
            new Handler(Looper.getMainLooper()).postDelayed(new WarmupCountdown(context, service, actions, packageName, label, secondsLeft - 1), 1000);
        }
    }

    private static final class Step implements Runnable, WatcherAccessibilityService.Done {
        private final Context context;
        private final WatcherAccessibilityService service;
        private final ArrayList<Action> actions;
        private final String packageName;
        private final String label;
        private final int index;

        Step(Context context, WatcherAccessibilityService service, ArrayList<Action> actions, String packageName, String label, int index) {
            this.context = context;
            this.service = service;
            this.actions = actions;
            this.packageName = packageName;
            this.label = label;
            this.index = index;
        }

        @Override
        public void run() {
            if (index >= actions.size()) {
                // Hide watermark so screenshot is 100% clean
                service.hideRunningWatermark();
                status(context, "Task done");
                Runner.notify(context, "Darwin Watcher", "Actions finished");
                new Handler(Looper.getMainLooper()).postDelayed(new ScreenshotDelayRunner(context, service), 700);
                return;
            }

            Action a = actions.get(index);
            int step = index + 1;
            if ("wait".equals(a.type)) {
                // Add natural timing jitter (+- 150ms)
                int jitter = (int) ((Math.random() * 300) - 150);
                int waitDuration = Math.max(700, a.duration + jitter);
                service.showRunningWatermark("WAIT " + (a.duration / 1000) + "s · Step " + step + "/" + actions.size());
                status(context, "Step " + step + "/" + actions.size() + " · Wait " + (a.duration / 1000) + "s");
                new Handler(Looper.getMainLooper()).postDelayed(new Step(context, service, actions, packageName, label, index + 1), waitDuration);
            } else if ("tap".equals(a.type)) {
                if (service.isDeviceLocked() || !service.isTargetActive(packageName)) {
                    call(false, label + " is not active");
                    return;
                }
                service.hideRunningWatermark(); // Ensure zero overlays on screen to prevent tapjacking touch filtering
                status(context, "Step " + step + "/" + actions.size() + " · Tap (" + a.x1 + ", " + a.y1 + ")");
                service.tap(a.x1, a.y1, this);
            } else if ("swipe".equals(a.type)) {
                if (service.isDeviceLocked() || !service.isTargetActive(packageName)) {
                    call(false, label + " is not active");
                    return;
                }
                service.hideRunningWatermark(); // Ensure zero overlays on screen
                status(context, "Step " + step + "/" + actions.size() + " · Swipe");
                service.swipe(a.x1, a.y1, a.x2, a.y2, a.duration, this);
            }
        }

        @Override
        public void call(boolean ok, String message) {
            if (!ok) {
                service.showRunningWatermark("STOPPED · " + message);
                status(context, "Stopped: " + message);
                Runner.notify(context, "Darwin Watcher stopped", message);
                new Handler(Looper.getMainLooper()).postDelayed(new HideWatermark(service), 1500);
                return;
            }
            service.showRunningWatermark("TAP #" + (index + 1) + " DONE · Step " + (index + 1) + "/" + actions.size());
            int postTapDelay = 220 + (int) (Math.random() * 120); // 220ms - 340ms natural recovery between steps
            new Handler(Looper.getMainLooper()).postDelayed(new Step(context, service, actions, packageName, label, index + 1), postTapDelay);
        }
    }

    private static final class FinishAutomation implements WatcherAccessibilityService.ScreenshotDone {
        private final Context context;
        private final WatcherAccessibilityService service;

        FinishAutomation(Context context, WatcherAccessibilityService service) {
            this.context = context;
            this.service = service;
        }

        @Override
        public void onFinished() {
            service.showRunningWatermark("DELIVERED · Sent to Telegram");
            service.closeTarget();
            new Handler(Looper.getMainLooper()).postDelayed(new HideWatermarkAndSleep(service), 1800);
        }
    }

    private static final class ScreenshotDelayRunner implements Runnable {
        private final Context context;
        private final WatcherAccessibilityService service;

        ScreenshotDelayRunner(Context context, WatcherAccessibilityService service) {
            this.context = context;
            this.service = service;
        }

        @Override
        public void run() {
            service.showRunningWatermark("CAPTURING SCREENSHOT · Finalizing");
            service.captureScreenshotAndSend(context, new FinishAutomation(context, service));
        }
    }

    private static final class HideWatermarkAndSleep implements Runnable {
        private final WatcherAccessibilityService service;

        HideWatermarkAndSleep(WatcherAccessibilityService service) {
            this.service = service;
        }

        @Override
        public void run() {
            service.hideRunningWatermark();
            // Automatically put phone back to sleep and lock screen
            service.lockDevice();
        }
    }

    private static final class HideWatermark implements Runnable {
        private final WatcherAccessibilityService service;

        HideWatermark(WatcherAccessibilityService service) {
            this.service = service;
        }

        @Override
        public void run() {
            service.hideRunningWatermark();
        }
    }

    private static final class Action {
        final String type;
        final int x1;
        final int y1;
        final int x2;
        final int y2;
        final int duration;
        Action(String type, int x1, int y1, int x2, int y2, int duration) {
            this.type = type;
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
            this.duration = duration;
        }
    }

    private static final class DelayedRetryRunner implements Runnable {
        private final Context context;

        DelayedRetryRunner(Context context) {
            this.context = context;
        }

        @Override
        public void run() {
            WatcherAccessibilityService s = WatcherAccessibilityService.current();
            if (s != null) {
                Runner.run(context);
            } else {
                status(context, "Enable Darwin Watcher Accessibility first");
                Runner.notify(context, "Darwin Watcher", "Accessibility is not enabled");
            }
        }
    }
}
