package com.darwin.watcher;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.StatFs;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Locale;

public final class TelegramRemoteService extends Service {
    private static final String TAG = "TelegramRemoteService";
    private static volatile boolean running = false;
    private static volatile Thread workerThread = null;
    private PowerManager.WakeLock wakeLock = null;
    private BatteryMonitorReceiver batteryReceiver = null;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static boolean isRunning() {
        return running;
    }

    public static void start(Context context) {
        if (!running) {
            Intent intent = new Intent(context, TelegramRemoteService.class);
            try {
                if (Build.VERSION.SDK_INT >= 26) {
                    context.startForegroundService(intent);
                } else {
                    context.startService(intent);
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to startService: " + e.getMessage());
            }
        }
    }

    public static void stop(Context context) {
        if (running) {
            running = false;
            Intent intent = new Intent(context, TelegramRemoteService.class);
            try {
                context.stopService(intent);
            } catch (Exception ignored) { }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        DeviceUtils.installGlobalCrashShield(this);
        DeviceUtils.ensureAccessibilityEnabled(this);
        Log.i(TAG, "TelegramRemoteService started (24/7 Keep-Alive & Command Hub active).");

        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DarwinWatcher:TelegramListenerWakeLock");
                wakeLock.setReferenceCounted(false);
                wakeLock.acquire(10 * 60 * 1000L);
            }
        } catch (Exception ignored) { }

        // Register power & battery monitor receiver
        try {
            batteryReceiver = new BatteryMonitorReceiver();
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_POWER_CONNECTED);
            filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
            filter.addAction(Intent.ACTION_BATTERY_LOW);
            filter.addAction(Intent.ACTION_BATTERY_CHANGED);
            registerReceiver(batteryReceiver, filter);
        } catch (Exception ignored) { }

        if (Build.VERSION.SDK_INT >= 26) {
            try {
                android.app.NotificationManager nm = (android.app.NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null) {
                    android.app.NotificationChannel chan = new android.app.NotificationChannel(
                        "darwin_bot_listener",
                        "Telegram Remote & Keep-Alive Guard",
                        android.app.NotificationManager.IMPORTANCE_LOW
                    );
                    chan.setDescription("Ensures 24/7 automation listening and accessibility persistence");
                    nm.createNotificationChannel(chan);
                }
                android.app.Notification.Builder nb = new android.app.Notification.Builder(this, "darwin_bot_listener");
                nb.setSmallIcon(R.drawable.ic_launcher_foreground)
                  .setContentTitle("Darwin Watcher · 24/7 Command Hub")
                  .setContentText("🟢 Listening for Telegram remote commands & buttons")
                  .setOngoing(true);
                startForeground(1002, nb.build());
            } catch (Exception ignored) { }
        }

        reportPendingCrash();
        startPoller();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        running = true;
        DeviceUtils.ensureAccessibilityEnabled(this);
        if (workerThread == null || !workerThread.isAlive()) {
            startPoller();
        }
        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        if (Prefs.telegramRemoteEnabled(this) && Prefs.hasTelegramCredentials(this)) {
            Intent restartServiceIntent = new Intent(getApplicationContext(), TelegramRemoteService.class);
            restartServiceIntent.setPackage(getPackageName());
            PendingIntent restartPendingIntent = PendingIntent.getService(
                getApplicationContext(), 1003, restartServiceIntent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
            );
            AlarmManager alarmService = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (alarmService != null) {
                alarmService.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 1000, restartPendingIntent);
            }
        }
    }

    @Override
    public void onDestroy() {
        RingtoneFinder.stop(this);

        if (batteryReceiver != null) {
            try { unregisterReceiver(batteryReceiver); } catch (Exception ignored) { }
            batteryReceiver = null;
        }

        if (wakeLock != null && wakeLock.isHeld()) {
            try { wakeLock.release(); } catch (Exception ignored) { }
        }

        if (running && Prefs.telegramRemoteEnabled(this) && Prefs.hasTelegramCredentials(this)) {
            Intent restartIntent = new Intent(getApplicationContext(), TelegramRemoteService.class);
            restartIntent.setPackage(getPackageName());
            PendingIntent pi = PendingIntent.getService(
                getApplicationContext(), 1004, restartIntent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
            );
            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (am != null) {
                am.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 1000, pi);
            }
        }

        running = false;
        if (workerThread != null) {
            workerThread.interrupt();
            workerThread = null;
        }
        Log.i(TAG, "TelegramRemoteService stopped.");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    /**
     * Reports a crash from the RESTARTED process, not the dying one - delivery must not
     * depend on a crashing thread outliving its own exception. Sent once, then cleared.
     */
    private void reportPendingCrash() {
        try {
            String rec = Prefs.pendingCrashReport(this);
            if (rec == null || rec.length() == 0) return;
            Prefs.clearPendingCrashReport(this);

            String[] p = rec.split(java.util.regex.Pattern.quote("|"), -1);
            String thread = p.length > 0 ? p[0] : "?";
            String type   = p.length > 1 ? p[1] : "?";
            String msg    = p.length > 2 ? p[2] : "";
            String when   = p.length > 3 ? p[3] : "";

            StringBuilder sb = new StringBuilder();
            sb.append("⚠️ Darwin Watcher restarted after a crash\n\n");
            sb.append("🧵 Thread   ").append(thread).append("\n");
            sb.append("💥 Error    ").append(type).append("\n");
            if (msg.length() > 0) sb.append("📝 Detail   ").append(msg).append("\n");
            if (when.length() > 0) sb.append("🕒 When     ").append(when).append("\n");
            sb.append("\nSchedules and the listener are back up. No action needed.");
            TelegramNotifier.sendText(this, sb.toString(), null);
        } catch (Throwable t) {
            Log.w(TAG, "could not report pending crash", t);
        }
    }

    private void startPoller() {
        if (workerThread != null && workerThread.isAlive()) return;
        workerThread = new Thread(new PollerRunnable(getApplicationContext(), mainHandler, wakeLock));
        workerThread.setName("TelegramBotPoller");
        workerThread.start();
    }

    private static final class PollerRunnable implements Runnable {
        private final Context context;
        private final Handler mainHandler;
        private final PowerManager.WakeLock wakeLock;

        PollerRunnable(Context context, Handler mainHandler, PowerManager.WakeLock wakeLock) {
            this.context = context;
            this.mainHandler = mainHandler;
            this.wakeLock = wakeLock;
        }

        @Override
        public void run() {
            Log.i(TAG, "Telegram Bot poller thread active.");
            int heartbeatCounter = 0;

            while (running) {
                try {
                    heartbeatCounter++;
                    if (heartbeatCounter % 3 == 0) {
                        DeviceUtils.ensureAccessibilityEnabled(context);
                        if (wakeLock != null && !wakeLock.isHeld()) {
                            try { wakeLock.acquire(10 * 60 * 1000L); } catch (Exception ignored) { }
                        }
                    }

                    if (!Prefs.telegramRemoteEnabled(context) || !Prefs.hasTelegramCredentials(context)) {
                        try { Thread.sleep(5000); } catch (InterruptedException ignored) { }
                        continue;
                    }

                    String token = Prefs.telegramToken(context).trim();
                    String cleanToken = token.startsWith("bot") ? token.substring(3) : token;
                    String authorizedChat = Prefs.telegramChat(context).trim();
                    long offset = Prefs.telegramLastUpdateId(context);

                    HttpURLConnection conn = null;
                    try {
                        String urlStr = "https://api.telegram.org/bot" + cleanToken + "/getUpdates?offset=" + (offset + 1) + "&timeout=25";
                        URL url = new URL(urlStr);
                        conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("GET");
                        conn.setConnectTimeout(30000);
                        conn.setReadTimeout(35000);

                        int code = conn.getResponseCode();
                        if (code >= 200 && code < 300) {
                            String body = readStream(conn.getInputStream());
                            JSONObject json = new JSONObject(body);
                            if (json.optBoolean("ok", false)) {
                                JSONArray results = json.optJSONArray("result");
                                if (results != null) {
                                    for (int i = 0; i < results.length(); i++) {
                                        JSONObject update = results.optJSONObject(i);
                                        if (update == null) continue;

                                        long updateId = update.optLong("update_id", offset);
                                        if (updateId > offset) {
                                            offset = updateId;
                                            Prefs.setTelegramLastUpdateId(context, updateId);
                                        }

                                        // 1. Check for Callback Queries (Button Clicks)
                                        if (update.has("callback_query")) {
                                            JSONObject cb = update.optJSONObject("callback_query");
                                            if (cb != null) {
                                                String cbId = cb.optString("id");
                                                String cbData = cb.optString("data");
                                                JSONObject from = cb.optJSONObject("from");
                                                long fromId = from != null ? from.optLong("id", 0) : 0;
                                                JSONObject msgObj = cb.optJSONObject("message");
                                                JSONObject chatObj = msgObj != null ? msgObj.optJSONObject("chat") : null;
                                                long chatId = chatObj != null ? chatObj.optLong("id", 0) : fromId;

                                                if (String.valueOf(chatId).equals(authorizedChat) || String.valueOf(fromId).equals(authorizedChat)) {
                                                    handleCallbackQuery(cbId, cbData);
                                                }
                                            }
                                            continue;
                                        }

                                        // 2. Check for Standard Text Messages
                                        JSONObject message = update.optJSONObject("message");
                                        if (message == null) continue;

                                        JSONObject chat = message.optJSONObject("chat");
                                        if (chat == null) continue;

                                        long chatId = chat.optLong("id", 0);
                                        String text = message.optString("text", "").trim();

                                        if (!String.valueOf(chatId).equals(authorizedChat)) {
                                            Log.w(TAG, "Ignored message from unauthorized chat_id: " + chatId);
                                            continue;
                                        }

                                        if (text.length() > 0) {
                                            handleTextCommand(text);
                                        }
                                    }
                                }
                            }
                        } else {
                            try { Thread.sleep(4000); } catch (InterruptedException ignored) { }
                        }
                    } catch (Exception e) {
                        if (running) {
                            try { Thread.sleep(4000); } catch (InterruptedException ignored) { }
                        }
                    } finally {
                        if (conn != null) {
                            try { conn.disconnect(); } catch (Exception ignored) { }
                        }
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "Poller loop error", t);
                    try { Thread.sleep(3000); } catch (InterruptedException ignored) { }
                }
            }
        }

        private void handleCallbackQuery(String queryId, String data) {
            Log.i(TAG, "Processing Callback Query: " + data);
            DeviceUtils.ensureAccessibilityEnabled(context);

            // Live view owns every cb_lv_* action and refreshes its own frame.
            if (LiveView.handleCallback(context, data, queryId)) return;

            if ("cb_run".equals(data)) {
                TelegramNotifier.answerCallbackQuery(context, queryId, "Executing automation...");
                triggerRemoteRun();
            } else if ("cb_wake".equals(data)) {
                TelegramNotifier.answerCallbackQuery(context, queryId, "Waking up screen...");
                DeviceUtils.wakeUpScreen(context);
                TelegramNotifier.sendText(context, "☀️ *Screen turned on & unlocked.*", getMainMenuKeyboard(), null);
            } else if ("cb_screenshot".equals(data)) {
                TelegramNotifier.answerCallbackQuery(context, queryId, "Capturing screenshot...");
                handleScreenshot();
            } else if ("cb_status".equals(data)) {
                TelegramNotifier.answerCallbackQuery(context, queryId, "Fetching status...");
                sendStatusReport();
            } else if ("cb_lock".equals(data)) {
                TelegramNotifier.answerCallbackQuery(context, queryId, "Locking phone...");
                handleLock();
            } else if ("cb_schedules".equals(data)) {
                TelegramNotifier.answerCallbackQuery(context, queryId, "Loading schedules...");
                sendSchedulesReport();
            } else if ("cb_profiles".equals(data)) {
                TelegramNotifier.answerCallbackQuery(context, queryId, "Loading profiles...");
                sendProfilesMenu();
            } else if (data != null && data.startsWith("cb_setprof:")) {
                String profName = data.substring("cb_setprof:".length()).trim();
                Prefs.setCurrentProfile(context, profName);
                TelegramNotifier.answerCallbackQuery(context, queryId, "Active profile: " + profName);
                TelegramNotifier.sendText(context, "✅ *Active Profile Switched:* `" + profName + "`\nAutomation will now run with this profile.", getMainMenuKeyboard(), null);
            } else if ("cb_ring".equals(data)) {
                TelegramNotifier.answerCallbackQuery(context, queryId, "Playing loud siren!");
                RingtoneFinder.start(context);
                String stopKeyboard = "{\"inline_keyboard\":[[{\"text\":\"🔇 Stop Siren Alarm\",\"callback_data\":\"cb_stopring\"}],[{\"text\":\"« Main Menu\",\"callback_data\":\"cb_menu\"}]]}";
                TelegramNotifier.sendText(context, "🚨 *SIREN ACTIVATED* at maximum alarm volume to locate your device!\n\nTap below to silence it.", stopKeyboard, null);
            } else if ("cb_stopring".equals(data)) {
                TelegramNotifier.answerCallbackQuery(context, queryId, "Siren silenced.");
                RingtoneFinder.stop(context);
                TelegramNotifier.sendText(context, "🔇 Siren has been turned off.", getMainMenuKeyboard(), null);
            } else if ("cb_net".equals(data)) {
                TelegramNotifier.answerCallbackQuery(context, queryId, "Fetching device info...");
                sendNetworkAndDeviceInfo();
            } else if ("cb_home".equals(data)) {
                TelegramNotifier.answerCallbackQuery(context, queryId, "Home pressed");
                WatcherAccessibilityService s = WatcherAccessibilityService.current();
                if (s != null) s.triggerHome();
            } else if ("cb_back".equals(data)) {
                TelegramNotifier.answerCallbackQuery(context, queryId, "Back pressed");
                WatcherAccessibilityService s = WatcherAccessibilityService.current();
                if (s != null) s.triggerBack();
            } else if ("cb_menu".equals(data)) {
                TelegramNotifier.answerCallbackQuery(context, queryId, "Main Menu");
                sendMainMenu();
            } else if ("cb_live".equals(data)) {
                TelegramNotifier.answerCallbackQuery(context, queryId, "Starting live view");
                LiveView.start(context);
            } else {
                TelegramNotifier.answerCallbackQuery(context, queryId, "Command received");
            }
        }

        private void handleTextCommand(String raw) {
            String[] parts = raw.split("\\s+");
            String cmd = parts[0].toLowerCase(Locale.ROOT);
            Log.i(TAG, "Received Telegram command: " + cmd);

            DeviceUtils.ensureAccessibilityEnabled(context);

            if ("/start".equals(cmd) || "/menu".equals(cmd) || "/help".equals(cmd)) {
                sendMainMenu();
            } else if ("/ping".equals(cmd)) {
                String devName = DeviceUtils.getDeviceModelName();
                String osName = DeviceUtils.getShortOS();
                TelegramNotifier.sendText(context, "🏓 *Pong!* Darwin Watcher is online & active on *" + devName + "* (" + osName + ").", getMainMenuKeyboard(), null);
            } else if ("/live".equals(cmd)) {
                if (parts.length > 1 && "stop".equalsIgnoreCase(parts[1])) {
                    LiveView.stop(context, "closed");
                } else {
                    LiveView.start(context);
                }
            } else if ("/status".equals(cmd)) {
                sendStatusReport();
            } else if ("/schedules".equals(cmd)) {
                sendSchedulesReport();
            } else if ("/profiles".equals(cmd) || "/profile".equals(cmd)) {
                if (parts.length > 1) {
                    String targetProf = raw.substring(raw.indexOf(' ') + 1).trim();
                    Prefs.setCurrentProfile(context, targetProf);
                    TelegramNotifier.sendText(context, "✅ *Active Profile Switched:* `" + targetProf + "`", getMainMenuKeyboard(), null);
                } else {
                    sendProfilesMenu();
                }
            } else if ("/wake".equals(cmd) || "/wakeup".equals(cmd) || "/screenon".equals(cmd)) {
                DeviceUtils.wakeUpScreen(context);
                TelegramNotifier.sendText(context, "☀️ *Screen turned on & unlocked.*", getMainMenuKeyboard(), null);
            } else if ("/screenshot".equals(cmd) || "/screen".equals(cmd) || "/shot".equals(cmd)) {
                handleScreenshot();
            } else if ("/sleep".equals(cmd) || "/lock".equals(cmd) || "/screenoff".equals(cmd)) {
                handleLock();
            } else if ("/run".equals(cmd)) {
                if (parts.length >= 3 && "in".equalsIgnoreCase(parts[1])) {
                    handleDelayedRun(parts[2]);
                } else {
                    triggerRemoteRun();
                }
            } else if ("/ring".equals(cmd) || "/find".equals(cmd) || "/alarm".equals(cmd)) {
                RingtoneFinder.start(context);
                String stopKeyboard = "{\"inline_keyboard\":[[{\"text\":\"🔇 Stop Siren Alarm\",\"callback_data\":\"cb_stopring\"}]]}";
                TelegramNotifier.sendText(context, "🚨 *SIREN ACTIVATED* at maximum alarm volume to locate your device!\n\nType `/stopring` or tap below to silence.", stopKeyboard, null);
            } else if ("/stopring".equals(cmd) || "/silence".equals(cmd)) {
                RingtoneFinder.stop(context);
                TelegramNotifier.sendText(context, "🔇 Siren turned off.", getMainMenuKeyboard(), null);
            } else if ("/net".equals(cmd) || "/ip".equals(cmd) || "/info".equals(cmd)) {
                sendNetworkAndDeviceInfo();
            } else if ("/home".equals(cmd)) {
                WatcherAccessibilityService s = WatcherAccessibilityService.current();
                if (s != null) {
                    s.triggerHome();
                    TelegramNotifier.sendText(context, "🏠 Navigation: *Home* key pressed.", null);
                }
            } else if ("/back".equals(cmd)) {
                WatcherAccessibilityService s = WatcherAccessibilityService.current();
                if (s != null) {
                    s.triggerBack();
                    TelegramNotifier.sendText(context, "🔙 Navigation: *Back* key pressed.", null);
                }
            } else if ("/recents".equals(cmd)) {
                WatcherAccessibilityService s = WatcherAccessibilityService.current();
                if (s != null) {
                    s.triggerRecents();
                    TelegramNotifier.sendText(context, "📑 Navigation: *Recents* opened.", null);
                }
            } else if ("/notifications".equals(cmd) || "/notif".equals(cmd)) {
                WatcherAccessibilityService s = WatcherAccessibilityService.current();
                if (s != null) {
                    s.triggerNotifications();
                    TelegramNotifier.sendText(context, "🔔 Navigation: *Notification Shade* pulled down.", null);
                }
            } else if ("/tap".equals(cmd)) {
                if (parts.length >= 3) {
                    try {
                        int x = Integer.parseInt(parts[1]);
                        int y = Integer.parseInt(parts[2]);
                        WatcherAccessibilityService s = WatcherAccessibilityService.current();
                        if (s != null) {
                            s.tap(x, y, null);
                            TelegramNotifier.sendText(context, "👉 *Remote Tap* executed at `(" + x + ", " + y + ")`.", null);
                        } else {
                            TelegramNotifier.sendText(context, "⚠️ Accessibility service not active.", null);
                        }
                    } catch (Exception e) {
                        TelegramNotifier.sendText(context, "Usage: `/tap <x> <y>` (e.g. `/tap 540 1200`)", null);
                    }
                } else {
                    TelegramNotifier.sendText(context, "Usage: `/tap <x> <y>` (e.g. `/tap 540 1200`)", null);
                }
            } else if ("/swipe".equals(cmd)) {
                if (parts.length >= 5) {
                    try {
                        int x1 = Integer.parseInt(parts[1]);
                        int y1 = Integer.parseInt(parts[2]);
                        int x2 = Integer.parseInt(parts[3]);
                        int y2 = Integer.parseInt(parts[4]);
                        int dur = parts.length >= 6 ? Integer.parseInt(parts[5]) : 300;
                        WatcherAccessibilityService s = WatcherAccessibilityService.current();
                        if (s != null) {
                            s.swipe(x1, y1, x2, y2, dur, null);
                            TelegramNotifier.sendText(context, "👆 *Remote Swipe* executed: `(" + x1 + "," + y1 + ") ➔ (" + x2 + "," + y2 + ")`", null);
                        } else {
                            TelegramNotifier.sendText(context, "⚠️ Accessibility service not active.", null);
                        }
                    } catch (Exception e) {
                        TelegramNotifier.sendText(context, "Usage: `/swipe <x1> <y1> <x2> <y2> [duration_ms]`", null);
                    }
                } else {
                    TelegramNotifier.sendText(context, "Usage: `/swipe <x1> <y1> <x2> <y2> [duration_ms]`", null);
                }
            } else if ("/text".equals(cmd)) {
                if (parts.length > 1) {
                    String input = raw.substring(raw.indexOf(' ') + 1);
                    WatcherAccessibilityService s = WatcherAccessibilityService.current();
                    if (s != null) {
                        boolean ok = s.enterText(input);
                        TelegramNotifier.sendText(context, ok ? "⌨️ *Typed:* \"" + input + "\"" : "⚠️ No active focused input field found.", null);
                    }
                } else {
                    TelegramNotifier.sendText(context, "Usage: `/text <message to type>`", null);
                }
            }
        }

        private void sendMainMenu() {
            String devName = DeviceUtils.getDeviceModelName();
            String activeProf = Prefs.currentProfile(context);
            String targetApp = Prefs.targetLabel(context);

            String text = "🤖 *DARWIN WATCHER CONTROL CENTER*\n" +
                "━━━━━━━━━━━━━━━━━━━━\n" +
                "• *Device:* `" + devName + "`\n" +
                "• *Target App:* *" + targetApp + "*\n" +
                "• *Active Profile:* `" + activeProf + "`\n" +
                "• *System Shield:* 🟢 24/7 Active\n\n" +
                "Tap a button below or type `/help` for gesture commands (`/tap`, `/swipe`, `/text`).";

            TelegramNotifier.sendText(context, text, getMainMenuKeyboard(), null);
        }

        private void handleScreenshot() {
            WatcherAccessibilityService service = WatcherAccessibilityService.current();
            if (service == null) {
                DeviceUtils.ensureAccessibilityEnabled(context);
                service = WatcherAccessibilityService.current();
            }
            if (service != null) {
                service.captureScreenshotAndSend(context, null);
            } else {
                TelegramNotifier.sendText(context, "⚠️ Accessibility service re-connecting. Retrying screenshot in 1s...", null);
                mainHandler.postDelayed(new DelayedScreenshot(context), 1000);
            }
        }

        private void handleLock() {
            WatcherAccessibilityService service = WatcherAccessibilityService.current();
            if (service == null) {
                DeviceUtils.ensureAccessibilityEnabled(context);
                service = WatcherAccessibilityService.current();
            }
            if (service != null) {
                boolean locked = service.lockDevice();
                if (locked) {
                    TelegramNotifier.sendText(context, "🔒 *Device locked* and screen put to sleep.", getMainMenuKeyboard(), null);
                } else {
                    TelegramNotifier.sendText(context, "⚠️ Unable to lock device via system accessibility.", null);
                }
            } else {
                TelegramNotifier.sendText(context, "⚠️ Accessibility service re-enabled.", null);
            }
        }

        private void handleDelayedRun(String delayStr) {
            long delayMs = 0;
            String clean = delayStr.toLowerCase(Locale.ROOT).trim();
            try {
                if (clean.endsWith("m")) {
                    int mins = Integer.parseInt(clean.substring(0, clean.length() - 1));
                    delayMs = mins * 60 * 1000L;
                } else if (clean.endsWith("s")) {
                    int secs = Integer.parseInt(clean.substring(0, clean.length() - 1));
                    delayMs = secs * 1000L;
                } else if (clean.endsWith("h")) {
                    int hours = Integer.parseInt(clean.substring(0, clean.length() - 1));
                    delayMs = hours * 3600 * 1000L;
                } else {
                    int mins = Integer.parseInt(clean);
                    delayMs = mins * 60 * 1000L;
                }
            } catch (Exception e) {
                TelegramNotifier.sendText(context, "⚠️ Invalid delay format. Examples: `/run in 10m`, `/run in 30s`", null);
                return;
            }

            if (delayMs <= 0) {
                triggerRemoteRun();
                return;
            }

            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (am != null) {
                Intent intent = new Intent(context, AlarmReceiver.class);
                intent.putExtra("isTest", true);
                PendingIntent pi = PendingIntent.getBroadcast(
                    context, 9991, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );
                long targetTime = System.currentTimeMillis() + delayMs;
                if (Build.VERSION.SDK_INT >= 23) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetTime, pi);
                } else {
                    am.setExact(AlarmManager.RTC_WAKEUP, targetTime, pi);
                }
                long minsLeft = delayMs / 60000;
                long secsLeft = (delayMs % 60000) / 1000;
                TelegramNotifier.sendText(context, "⏰ *Delayed Run Scheduled!* Automation will trigger in *" + (minsLeft > 0 ? minsLeft + "m " : "") + secsLeft + "s*.", getMainMenuKeyboard(), null);
            }
        }

        private void sendStatusReport() {
            int batteryPct = -1;
            String chargingStatus = "🔋 Battery";
            try {
                IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                Intent bStatus = context.registerReceiver(null, ifilter);
                if (bStatus != null) {
                    int level = bStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                    int scale = bStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                    if (level >= 0 && scale > 0) {
                        batteryPct = (int) ((level / (float) scale) * 100);
                    }
                    int plugged = bStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
                    chargingStatus = plugged > 0 ? "⚡ Charging" : "🔋 Discharging";
                }
            } catch (Exception ignored) { }

            boolean accessActive = WatcherAccessibilityService.isRunning();
            String targetLabel = Prefs.targetLabel(context);
            String targetPkg = Prefs.targetPackage(context);
            String profile = Prefs.currentProfile(context);
            String devInfo = DeviceUtils.getDeviceFullInfo();

            String report = "📱 *Darwin Watcher Status Report*\n\n" +
                "• *Device:* " + devInfo + "\n" +
                "• *Target App:* *" + targetLabel + "* (`" + targetPkg + "`)\n" +
                "• *Active Profile:* `" + profile + "`\n" +
                "• *Accessibility Engine:* " + (accessActive ? "✅ Active & Connected" : "🔄 Self-Healing / Auto-Locked") + "\n" +
                "• *Battery:* " + (batteryPct >= 0 ? batteryPct + "%" : "Unknown") + " (" + chargingStatus + ")\n" +
                "• *Remote Listener:* 🟢 Online & Polling (24/7 Unkillable Shield)";

            TelegramNotifier.sendText(context, report, getMainMenuKeyboard(), null);
        }

        private void sendSchedulesReport() {
            ArrayList<Prefs.ScheduleItem> list = Prefs.getSchedules(context);
            StringBuilder sb = new StringBuilder();
            sb.append("⏰ *Configured Schedules (").append(list.size()).append(")*\n\n");
            if (list.isEmpty()) {
                sb.append("No daily schedules configured.");
            } else {
                for (int i = 0; i < list.size(); i++) {
                    Prefs.ScheduleItem item = list.get(i);
                    String time = String.format(Locale.getDefault(), "%02d:%02d", item.hour, item.minute);
                    sb.append("• *").append(item.name).append("*: `").append(time).append("` (").append(item.daysShort()).append(")\n");
                    sb.append("  ↳ Tolerance: ").append(item.toleranceText()).append(" [").append(item.windowPreview()).append("]\n");
                    sb.append("  ↳ State: ").append(item.enabled ? "Active ✅" : "Disabled ⚪");
                    sb.append("\n\n");
                }
            }
            TelegramNotifier.sendText(context, sb.toString().trim(), getMainMenuKeyboard(), null);
        }

        private void sendProfilesMenu() {
            String raw = Prefs.profiles(context);
            String current = Prefs.currentProfile(context);
            String[] parts = raw.split(",");
            StringBuilder sb = new StringBuilder("{\"inline_keyboard\":[");
            boolean first = true;
            for (String p : parts) {
                String name = p.trim();
                if (name.isEmpty()) continue;
                if (!first) sb.append(",");
                first = false;
                String prefix = name.equalsIgnoreCase(current) ? "● " : "○ ";
                sb.append("[{\"text\":\"").append(prefix).append(name).append("\",\"callback_data\":\"cb_setprof:").append(name).append("\"}]");
            }
            if (!first) sb.append(",");
            sb.append("[{\"text\":\"« Back to Main Menu\",\"callback_data\":\"cb_menu\"}]");
            sb.append("]}");

            String msg = "🔄 *Select Profile to Activate:*\nCurrently active: *" + current + "*";
            TelegramNotifier.sendText(context, msg, sb.toString(), null);
        }

        private void sendNetworkAndDeviceInfo() {
            String netStatus = DeviceUtils.getNetworkStatus(context);

            String storageInfo = "Unknown";
            try {
                StatFs stat = new StatFs(Environment.getDataDirectory().getPath());
                long freeBytes = stat.getAvailableBytes();
                long totalBytes = stat.getTotalBytes();
                double freeGb = freeBytes / (1024.0 * 1024.0 * 1024.0);
                double totalGb = totalBytes / (1024.0 * 1024.0 * 1024.0);
                storageInfo = String.format(Locale.getDefault(), "%.1f GB free of %.1f GB", freeGb, totalGb);
            } catch (Exception ignored) { }

            String ramInfo = "Unknown";
            try {
                ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                if (am != null) {
                    ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                    am.getMemoryInfo(mi);
                    double availGb = mi.availMem / (1024.0 * 1024.0 * 1024.0);
                    double totalGb = mi.totalMem / (1024.0 * 1024.0 * 1024.0);
                    ramInfo = String.format(Locale.getDefault(), "%.1f GB free of %.1f GB", availGb, totalGb);
                }
            } catch (Exception ignored) { }

            String text = "🌐 *Device & Network Information*\n\n" +
                "• *Device:* " + DeviceUtils.getDeviceFullInfo() + "\n" +
                "• *Network:* " + netStatus + "\n" +
                "• *Internal Storage:* `" + storageInfo + "`\n" +
                "• *RAM Available:* `" + ramInfo + "`\n" +
                "• *Android Version:* " + DeviceUtils.getShortOS();

            TelegramNotifier.sendText(context, text, getMainMenuKeyboard(), null);
        }

        private void triggerRemoteRun() {
            String label = Prefs.targetLabel(context);
            TelegramNotifier.sendText(context, "🚀 Executing automation sequence on *" + label + "*...", null);

            try {
                PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                if (pm != null) {
                    PowerManager.WakeLock wl = pm.newWakeLock(
                        PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE,
                        "darwin:remote_run_wake"
                    );
                    wl.acquire(15000);
                }
            } catch (Exception ignored) { }

            mainHandler.post(new ExecuteRun(context));
        }

        private static String getMainMenuKeyboard() {
            return "{\"inline_keyboard\":[" +
                "[{\"text\":\"🚀 Run Now\",\"callback_data\":\"cb_run\"},{\"text\":\"📸 Screenshot\",\"callback_data\":\"cb_screenshot\"}]," +
                "[{\"text\":\"☀️ Wake Screen\",\"callback_data\":\"cb_wake\"},{\"text\":\"🔒 Lock Screen\",\"callback_data\":\"cb_lock\"}]," +
                "[{\"text\":\"📊 Status\",\"callback_data\":\"cb_status\"},{\"text\":\"🔄 Profiles\",\"callback_data\":\"cb_profiles\"}]," +
                "[{\"text\":\"⏰ Schedules\",\"callback_data\":\"cb_schedules\"},{\"text\":\"🔊 Find Phone\",\"callback_data\":\"cb_ring\"}]," +
                "[{\"text\":\"🌐 Network & Info\",\"callback_data\":\"cb_net\"},{\"text\":\"🏠 Home\",\"callback_data\":\"cb_home\"}]," +
                "[{\"text\":\"🖥 Live View & Control\",\"callback_data\":\"cb_live\"}]" +
            "]}";
        }

        private static String readStream(InputStream is) {
            if (is == null) return "";
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();
                return sb.toString();
            } catch (Exception e) {
                return "";
            }
        }
    }

    private static final class ExecuteRun implements Runnable {
        private final Context context;

        ExecuteRun(Context context) {
            this.context = context;
        }

        @Override
        public void run() {
            Runner.run(context);
        }
    }

    private static final class DelayedScreenshot implements Runnable {
        private final Context context;

        DelayedScreenshot(Context context) {
            this.context = context;
        }

        @Override
        public void run() {
            WatcherAccessibilityService s = WatcherAccessibilityService.current();
            if (s != null) {
                s.captureScreenshotAndSend(context, null);
            }
        }
    }

    private static final class BatteryMonitorReceiver extends BroadcastReceiver {
        private static long lastLowBatteryAlert = 0;

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) return;
            String action = intent.getAction();

            if (Intent.ACTION_POWER_CONNECTED.equals(action)) {
                TelegramNotifier.sendText(context, "⚡ *Charger Connected!* Device is now charging.", null);
            } else if (Intent.ACTION_POWER_DISCONNECTED.equals(action)) {
                TelegramNotifier.sendText(context, "🔌 *Charger Disconnected!* Device running on battery power.", null);
            } else if (Intent.ACTION_BATTERY_LOW.equals(action) || Intent.ACTION_BATTERY_CHANGED.equals(action)) {
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                if (level >= 0 && scale > 0) {
                    int pct = (int) ((level / (float) scale) * 100);
                    if (pct <= 15 && (System.currentTimeMillis() - lastLowBatteryAlert > 30 * 60 * 1000L)) {
                        lastLowBatteryAlert = System.currentTimeMillis();
                        TelegramNotifier.sendText(context, "⚠️ *Low Battery Alert!* Phone is at *" + pct + "%*. Please plug in charger to ensure uninterrupted 24/7 automation.", null);
                    }
                }
            }
        }
    }
}
