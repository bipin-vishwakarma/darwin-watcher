package com.darwin.watcher;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

public final class DeviceUtils {
    private static final String TAG = "DeviceUtils";
    private static boolean crashShieldInstalled = false;

    private DeviceUtils() { }

    /**
     * Records uncaught exceptions and, on the main thread, lets the process DIE.
     *
     * The previous version captured the default handler and never called it. That kept
     * the process alive after a fatal main-thread exception - but Looper.loop() has
     * already unwound by then, so the main thread is dead for good. Everything that runs
     * on it stops silently: AlarmReceiver (scheduled runs, the 15-minute watchdog,
     * accessibility self-heal), every Handler.postDelayed, the takeScreenshot callback
     * and all of Runner. Meanwhile the Telegram poller has its own thread and keeps
     * answering /status and /net, so the app looks perfectly healthy while attendance
     * quietly stops being punched. Verified with `adb shell am crash`: FATAL EXCEPTION
     * on main, and the pid was unchanged afterwards.
     *
     * Dying is the recoverable option. The service is START_STICKY and alarms live in
     * AlarmManager, not in the process, so Android brings us straight back.
     */
    public static void installGlobalCrashShield(Context context) {
        if (crashShieldInstalled) return;
        crashShieldInstalled = true;
        Context app = context != null ? context.getApplicationContext() : null;
        Thread.setDefaultUncaughtExceptionHandler(
                new CrashHandler(app, Thread.getDefaultUncaughtExceptionHandler()));
    }

    private static final class CrashHandler implements Thread.UncaughtExceptionHandler {
        private final Context app;
        private final Thread.UncaughtExceptionHandler defaultHandler;

        CrashHandler(Context app, Thread.UncaughtExceptionHandler defaultHandler) {
            this.app = app;
            this.defaultHandler = defaultHandler;
        }

        @Override
        public void uncaughtException(Thread t, Throwable e) {
            boolean isMain = t == android.os.Looper.getMainLooper().getThread();
            Log.e(TAG, "Uncaught exception in thread " + t.getName() + " (main=" + isMain + ")", e);

            // Persisted with commit(): a dying process may not outlive an async apply().
            try {
                if (app != null) {
                    String msg = e.getMessage() == null ? "" : e.getMessage();
                    if (msg.length() > 160) msg = msg.substring(0, 160);
                    Prefs.setPendingCrashReport(app,
                            t.getName() + "|" + e.getClass().getSimpleName() + "|" + msg
                            + "|" + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                                    java.util.Locale.US).format(new java.util.Date()));
                }
            } catch (Throwable ignored) { }

            // A background thread dying is survivable - the poller is restarted by the
            // 15-minute watchdog, and killing the process over it would be worse.
            if (!isMain) return;

            try {
                if (defaultHandler != null) {
                    defaultHandler.uncaughtException(t, e);
                    return;
                }
            } catch (Throwable ignored) { }
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(10);
        }
    }

    public static void wakeUpScreen(Context context) {
        if (context == null) return;
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                PowerManager.WakeLock wl = pm.newWakeLock(
                    PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE,
                    "DarwinWatcher:RemoteWakeScreen"
                );
                wl.acquire(15000);
            }
        } catch (Exception ignored) { }

        try {
            Intent intent = new Intent(context, WakeUnlockActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            context.startActivity(intent);
        } catch (Exception ignored) { }

        try {
            WatcherAccessibilityService service = WatcherAccessibilityService.current();
            if (service != null) {
                service.unlockDevice();
            }
        } catch (Exception ignored) { }
    }

    public static boolean ensureAccessibilityEnabled(Context context) {
        if (context == null) return false;
        try {
            String myService = context.getPackageName() + "/" + WatcherAccessibilityService.class.getName();
            String current = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            boolean changed = false;

            if (current == null || !current.contains(myService)) {
                String updated = (current == null || current.trim().isEmpty()) ? myService : current + ":" + myService;
                Settings.Secure.putString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, updated);
                changed = true;
            }

            int enabled = Settings.Secure.getInt(context.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 0);
            if (enabled != 1) {
                Settings.Secure.putInt(context.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 1);
                changed = true;
            }

            if (changed) {
                Log.i(TAG, "🛡️ Self-healed & locked Accessibility Service into Secure Settings.");
            }
            return true;
        } catch (Exception e) {
            // Fails gracefully if WRITE_SECURE_SETTINGS not yet granted via ADB
            return false;
        }
    }

    public static boolean isSamsung() {
        String m = Build.MANUFACTURER != null ? Build.MANUFACTURER : "";
        return m.toLowerCase().contains("samsung");
    }

    public static boolean isXiaomi() {
        String m = (Build.MANUFACTURER != null ? Build.MANUFACTURER : "") + " "
                 + (Build.BRAND != null ? Build.BRAND : "");
        m = m.toLowerCase();
        return m.contains("xiaomi") || m.contains("redmi") || m.contains("poco");
    }

    public static String getDeviceModelName() {
        String manufacturer = Build.MANUFACTURER != null ? Build.MANUFACTURER.trim() : "";
        String model = Build.MODEL != null ? Build.MODEL.trim() : "";
        String marketName = Build.DEVICE != null ? Build.DEVICE.trim() : "";

        // Samsung reports raw sales codes (SM-M055F) rather than the name on the box.
        // Checked before the startsWith() shortcut below, which would otherwise pass
        // "Samsung SM-M055F" straight through.
        if (isSamsung()) {
            String galaxy = galaxyName(model);
            if (galaxy != null) return "Samsung " + galaxy;
        }

        if (model.toLowerCase().startsWith(manufacturer.toLowerCase())) {
            return capitalize(model);
        }

        // Special mapping for common flagship codenames if model is a raw codename
        if ("M2012K11AI".equalsIgnoreCase(model) || "aliothin".equalsIgnoreCase(marketName)) {
            return "Xiaomi Mi 11X";
        }
        if ("M2012K11AC".equalsIgnoreCase(model) || "alioth".equalsIgnoreCase(marketName)) {
            return "Redmi K40 / POCO F3";
        }

        if (manufacturer.length() > 0 && model.length() > 0) {
            return capitalize(manufacturer) + " " + model;
        } else if (model.length() > 0) {
            return model;
        } else {
            return "Android Device";
        }
    }

    public static String getDeviceFullInfo() {
        return getDeviceModelName() + " (Android " + Build.VERSION.RELEASE + ", API " + Build.VERSION.SDK_INT + ")";
    }

    public static String getShortOS() {
        return "Android " + Build.VERSION.RELEASE;
    }

    public static String getNetworkStatus(Context context) {
        if (context == null) return "Unknown";
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return "No Network Service";

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network activeNet = cm.getActiveNetwork();
                if (activeNet == null) return "❌ Disconnected (Offline)";
                NetworkCapabilities caps = cm.getNetworkCapabilities(activeNet);
                if (caps == null) return "❌ Disconnected (Offline)";

                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    String ssid = null;
                    int rssi = -999;
                    int linkSpeed = -1;

                    // 1. Try NetworkCapabilities TransportInfo (Android 10+)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        try {
                            android.net.TransportInfo tInfo = caps.getTransportInfo();
                            if (tInfo instanceof WifiInfo) {
                                WifiInfo wi = (WifiInfo) tInfo;
                                ssid = wi.getSSID();
                                rssi = wi.getRssi();
                                linkSpeed = wi.getLinkSpeed();
                            }
                        } catch (Exception ignored) { }
                    }

                    // 2. Try WifiManager
                    if (ssid == null || ssid.isEmpty() || "<unknown ssid>".equalsIgnoreCase(ssid)) {
                        try {
                            WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                            if (wm != null) {
                                WifiInfo wi = wm.getConnectionInfo();
                                if (wi != null) {
                                    if (ssid == null || "<unknown ssid>".equalsIgnoreCase(ssid)) {
                                        ssid = wi.getSSID();
                                    }
                                    if (rssi == -999) rssi = wi.getRssi();
                                    if (linkSpeed == -1) linkSpeed = wi.getLinkSpeed();
                                }
                            }
                        } catch (Exception ignored) { }
                    }

                    if (ssid != null) {
                        ssid = ssid.replace("\"", "").trim();
                    }

                    String ip = getLocalIpAddress("wlan0");
                    if (ip == null || ip.isEmpty()) {
                        ip = getAnyLocalIpAddress();
                    }

                    StringBuilder sb = new StringBuilder();
                    if (ssid != null && !ssid.isEmpty() && !"<unknown ssid>".equalsIgnoreCase(ssid) && !"0x".equalsIgnoreCase(ssid)) {
                        sb.append("📶 `").append(ssid).append("`");
                    } else {
                        sb.append("📶 Connected to Wi-Fi");
                    }

                    if (ip != null && !ip.isEmpty()) {
                        sb.append(" (`").append(ip).append("`)");
                    }

                    if (linkSpeed > 0) {
                        sb.append("\n  ↳ Speed: *").append(linkSpeed).append(" Mbps*");
                    }
                    if (rssi != -999 && rssi != 0) {
                        int signalPct = WifiManager.calculateSignalLevel(rssi, 100);
                        sb.append(" · Signal: *").append(signalPct).append("%* (").append(rssi).append(" dBm)");
                    }
                    return sb.toString();
                } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    String ip = getAnyLocalIpAddress();
                    return "📶 4G/5G Cellular Mobile Data" + (ip != null ? " (`" + ip + "`)" : "");
                } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                    String ip = getAnyLocalIpAddress();
                    return "🔌 Ethernet" + (ip != null ? " (`" + ip + "`)" : "");
                }
            }
        } catch (Exception ignored) { }
        return "Connected";
    }

    public static String getLocalIpAddress(String ifaceName) {
        try {
            java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface iface = interfaces.nextElement();
                if (iface.getName().equalsIgnoreCase(ifaceName) || iface.getDisplayName().equalsIgnoreCase(ifaceName)) {
                    java.util.Enumeration<java.net.InetAddress> addresses = iface.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        java.net.InetAddress addr = addresses.nextElement();
                        if (!addr.isLoopbackAddress() && addr instanceof java.net.Inet4Address) {
                            return addr.getHostAddress();
                        }
                    }
                }
            }
        } catch (Exception ignored) { }
        return null;
    }

    public static String getAnyLocalIpAddress() {
        try {
            java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface iface = interfaces.nextElement();
                java.util.Enumeration<java.net.InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    java.net.InetAddress addr = addresses.nextElement();
                    if (!addr.isLoopbackAddress() && addr instanceof java.net.Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) { }
        return null;
    }

    /**
     * Maps a Samsung sales code to its retail name. Only the prefix carries the series,
     * so this stays short instead of enumerating every SKU: SM-M055F -> Galaxy M05.
     * Returns null when the code is not recognised, so the caller can fall back.
     */
    private static String galaxyName(String model) {
        if (model == null) return null;
        String m = model.toUpperCase().trim();
        if (!m.startsWith("SM-")) return null;
        String code = m.substring(3);
        if (code.length() < 2) return null;

        char series = code.charAt(0);
        StringBuilder digits = new StringBuilder();
        for (int i = 1; i < code.length() && Character.isDigit(code.charAt(i)); i++) {
            digits.append(code.charAt(i));
        }
        if (digits.length() == 0) return null;

        // Only the M and A series map arithmetically: the padded number minus its
        // trailing variant digit is the retail number (M055 -> M05, A546 -> A54).
        // S, N and F do NOT follow this - SM-S911B is the Galaxy S23, not "S91", and
        // SM-F946B is the Z Fold5 - so they fall through to the raw sales code rather
        // than being confidently wrong.
        if (series != 'M' && series != 'A') return null;

        String num = digits.toString();
        if (num.length() < 3) return null;
        num = num.substring(0, num.length() - 1);
        return "Galaxy " + series + num;
    }

    private static String capitalize(String str) {
        if (str == null || str.length() == 0) return "";
        char first = str.charAt(0);
        if (Character.isUpperCase(first)) return str;
        return Character.toUpperCase(first) + str.substring(1);
    }
}
