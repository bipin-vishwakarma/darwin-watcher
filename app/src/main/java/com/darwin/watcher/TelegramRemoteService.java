package com.darwin.watcher;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

public final class TelegramRemoteService extends Service {
    private static final String TAG = "TelegramRemoteService";
    private static volatile boolean running = false;
    private static volatile Thread workerThread = null;

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
        Log.i(TAG, "TelegramRemoteService started.");

        if (Build.VERSION.SDK_INT >= 26) {
            try {
                android.app.NotificationManager nm = (android.app.NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null) {
                    android.app.NotificationChannel chan = new android.app.NotificationChannel("darwin_bot_listener", "Telegram Remote Listener", android.app.NotificationManager.IMPORTANCE_LOW);
                    chan.setDescription("Listens for remote Telegram commands");
                    nm.createNotificationChannel(chan);
                }
                android.app.Notification.Builder nb = new android.app.Notification.Builder(this, "darwin_bot_listener");
                nb.setSmallIcon(R.drawable.ic_launcher_foreground)
                  .setContentTitle("Darwin Watcher Remote")
                  .setContentText("🟢 Listening for Telegram commands")
                  .setOngoing(true);
                startForeground(1002, nb.build());
            } catch (Exception ignored) { }
        }

        startPoller();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        running = true;
        if (workerThread == null || !workerThread.isAlive()) {
            startPoller();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
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

    private void startPoller() {
        if (workerThread != null && workerThread.isAlive()) return;
        workerThread = new Thread(new PollerRunnable(getApplicationContext(), mainHandler));
        workerThread.setName("TelegramBotPoller");
        workerThread.start();
    }

    private static final class PollerRunnable implements Runnable {
        private final Context context;
        private final Handler mainHandler;

        PollerRunnable(Context context, Handler mainHandler) {
            this.context = context;
            this.mainHandler = mainHandler;
        }

        @Override
        public void run() {
            Log.i(TAG, "Telegram Bot poller thread active.");
            while (running) {
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

                                    JSONObject message = update.optJSONObject("message");
                                    if (message == null) continue;

                                    JSONObject chat = message.optJSONObject("chat");
                                    if (chat == null) continue;

                                    long chatId = chat.optLong("id", 0);
                                    String text = message.optString("text", "").trim();

                                    // Security gate: only accept messages from authorized chat
                                    if (!String.valueOf(chatId).equals(authorizedChat)) {
                                        Log.w(TAG, "Ignored message from unauthorized chat_id: " + chatId);
                                        continue;
                                    }

                                    if (text.length() > 0) {
                                        handleCommand(text);
                                    }
                                }
                            }
                        }
                    } else {
                        Log.w(TAG, "getUpdates returned HTTP " + code);
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
            }
        }

        private void handleCommand(String raw) {
            String cmd = raw.split("\\s+")[0].toLowerCase();
            Log.i(TAG, "Received Telegram command: " + cmd);

            if ("/ping".equals(cmd)) {
                String devName = DeviceUtils.getDeviceModelName();
                String osName = DeviceUtils.getShortOS();
                TelegramNotifier.sendText(context, "🏓 Pong! Darwin Watcher is online & listening on " + devName + " (" + osName + ").", null);
            } else if ("/help".equals(cmd) || "/start".equals(cmd)) {
                String devName = DeviceUtils.getDeviceModelName();
                String help = "🤖 *Darwin Watcher Remote Center*\n" +
                    "Device: " + devName + "\n\n" +
                    "• /run - Execute automation task right now\n" +
                    "• /sleep - Lock device & turn screen off\n" +
                    "• /status - Live device & automation health\n" +
                    "• /schedules - List active daily schedules\n" +
                    "• /screenshot - Capture & send screen photo\n" +
                    "• /ping - Check bot connectivity";
                TelegramNotifier.sendText(context, help, null);
            } else if ("/status".equals(cmd)) {
                sendStatusReport();
            } else if ("/schedules".equals(cmd)) {
                sendSchedulesReport();
            } else if ("/screenshot".equals(cmd)) {
                WatcherAccessibilityService service = WatcherAccessibilityService.current();
                if (service != null) {
                    service.captureScreenshotAndSend(context, null);
                } else {
                    TelegramNotifier.sendText(context, "⚠️ Accessibility service is not running. Please open the app.", null);
                }
            } else if ("/sleep".equals(cmd) || "/lock".equals(cmd) || "/screenoff".equals(cmd)) {
                WatcherAccessibilityService service = WatcherAccessibilityService.current();
                if (service != null) {
                    boolean locked = service.lockDevice();
                    if (locked) {
                        TelegramNotifier.sendText(context, "🔒 Device locked and screen put to sleep.", null);
                    } else {
                        TelegramNotifier.sendText(context, "⚠️ Unable to lock device via system accessibility.", null);
                    }
                } else {
                    TelegramNotifier.sendText(context, "⚠️ Accessibility service is not running. Please open the app.", null);
                }
            } else if ("/run".equals(cmd)) {
                triggerRemoteRun();
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
                "• *Target App:* " + targetLabel + " (`" + targetPkg + "`)\n" +
                "• *Active Profile:* " + profile + "\n" +
                "• *Accessibility Engine:* " + (accessActive ? "✅ Active & Connected" : "❌ Disconnected") + "\n" +
                "• *Battery:* " + (batteryPct >= 0 ? batteryPct + "%" : "Unknown") + " (" + chargingStatus + ")\n" +
                "• *Remote Listener:* 🟢 Online & Polling";

            TelegramNotifier.sendText(context, report, null);
        }

        private void sendSchedulesReport() {
            ArrayList<Prefs.ScheduleItem> list = Prefs.getSchedules(context);
            StringBuilder sb = new StringBuilder();
            sb.append("⏰ *Configured Schedules (").append(list.size()).append(")*\n\n");
            if (list.isEmpty()) {
                sb.append("No schedules configured.");
            } else {
                for (int i = 0; i < list.size(); i++) {
                    Prefs.ScheduleItem item = list.get(i);
                    String time = String.format("%02d:%02d", item.hour, item.minute);
                    sb.append("• *").append(item.name).append("*: `").append(time).append("` (").append(item.daysShort()).append(")\n");
                    sb.append("  ↳ Tolerance: ").append(item.toleranceText()).append(" [").append(item.windowPreview()).append("]\n");
                    sb.append("  ↳ State: ").append(item.enabled ? "Active ✅" : "Disabled ⚪");
                    sb.append("\n\n");
                }
            }
            TelegramNotifier.sendText(context, sb.toString().trim(), null);
        }

        private void triggerRemoteRun() {
            String label = Prefs.targetLabel(context);
            TelegramNotifier.sendText(context, "🚀 Executing automation on *" + label + "* now...", null);

            // Wake up the screen if asleep
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
}
