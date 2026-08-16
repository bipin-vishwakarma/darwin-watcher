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

    public static void installGlobalCrashShield() {
        if (crashShieldInstalled) return;
        crashShieldInstalled = true;
        final Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                Log.e(TAG, "⚡ CrashShield intercepted uncaught exception in thread " + t.getName(), e);
                // Do not let the app process terminate silently - keep services alive
            }
        });
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

    public static String getDeviceModelName() {
        String manufacturer = Build.MANUFACTURER != null ? Build.MANUFACTURER.trim() : "";
        String model = Build.MODEL != null ? Build.MODEL.trim() : "";
        String marketName = Build.DEVICE != null ? Build.DEVICE.trim() : "";

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

    private static String capitalize(String str) {
        if (str == null || str.length() == 0) return "";
        char first = str.charAt(0);
        if (Character.isUpperCase(first)) return str;
        return Character.toUpperCase(first) + str.substring(1);
    }
}
