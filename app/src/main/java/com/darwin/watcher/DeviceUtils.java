package com.darwin.watcher;

import android.os.Build;

public final class DeviceUtils {
    private DeviceUtils() { }

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

    private static String capitalize(String str) {
        if (str == null || str.length() == 0) return "";
        char first = str.charAt(0);
        if (Character.isUpperCase(first)) return str;
        return Character.toUpperCase(first) + str.substring(1);
    }
}
