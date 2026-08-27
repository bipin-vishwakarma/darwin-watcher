package com.darwin.watcher;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {
    public static final String DEFAULT_ACTIONS = "wait 3\n";
    public static final int SLOT_MORNING = 1;
    public static final int SLOT_EVENING = 2;

    private static final String NAME = "darwin_watcher";
    private static final String ACTIONS = "actions_text";
    private static final String CURRENT_PROFILE = "current_profile";
    private static final String PROFILES = "profiles";
    private static final String TARGET_PACKAGE = "target_package";
    private static final String TARGET_LABEL = "target_label";
    private static final String MORNING_ENABLED = "morning_enabled";
    private static final String MORNING_HOUR = "morning_hour";
    private static final String MORNING_MINUTE = "morning_minute";
    private static final String EVENING_ENABLED = "evening_enabled";
    private static final String EVENING_HOUR = "evening_hour";
    private static final String EVENING_MINUTE = "evening_minute";
    private static final String LAST_STATUS = "last_run_status";
    private static final String TELEGRAM_ENABLED = "telegram_enabled";
    private static final String TELEGRAM_TOKEN = "telegram_token";
    private static final String TELEGRAM_CHAT = "telegram_chat";
    private static final String PENDING_SCHEDULED_RUN = "pending_scheduled_run";

    private Prefs() { }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public static String cleanPkg(String pkg) {
        if (pkg == null || pkg.trim().length() == 0) return "global";
        return pkg.trim().replace('.', '_').replace('-', '_');
    }

    public static String currentProfile(Context context) {
        String pkg = cleanPkg(targetPackage(context));
        return prefs(context).getString(CURRENT_PROFILE + "_" + pkg, prefs(context).getString(CURRENT_PROFILE, "Default"));
    }

    public static void setCurrentProfile(Context context, String value) {
        String pkg = cleanPkg(targetPackage(context));
        String profile = cleanProfile(value);
        SharedPreferences.Editor edit = prefs(context).edit();
        edit.putString(CURRENT_PROFILE + "_" + pkg, profile);
        edit.putString(CURRENT_PROFILE, profile);
        String profiles = profiles(context);
        if (!hasProfile(profiles, profile)) {
            String newProfiles = profiles.length() == 0 ? profile : profiles + ", " + profile;
            edit.putString(PROFILES + "_" + pkg, newProfiles);
            edit.putString(PROFILES, newProfiles);
        }
        edit.apply();
    }

    public static String profiles(Context context) {
        String pkg = cleanPkg(targetPackage(context));
        return prefs(context).getString(PROFILES + "_" + pkg, prefs(context).getString(PROFILES, "Default"));
    }

    /** Calculator package per OEM, most specific first, then a generic fallback. */
    private static final String[] CALCULATOR_CANDIDATES = {
        "com.miui.calculator",                    // Xiaomi / MIUI / HyperOS
        "com.sec.android.app.popupcalculator",    // Samsung / One UI
        "com.google.android.calculator",          // Pixel / AOSP builds with Google apps
        "com.android.calculator2"                 // stock AOSP
    };

    /**
     * Default target when nothing has been chosen yet. Resolves a calculator that is
     * actually installed rather than assuming MIUI's, which does not exist outside
     * Xiaomi and left a fresh profile pointing at a missing package.
     */
    public static String defaultTargetPackage(Context context) {
        if (context != null) {
            android.content.pm.PackageManager pm = context.getPackageManager();
            for (int i = 0; i < CALCULATOR_CANDIDATES.length; i++) {
                try {
                    if (pm.getLaunchIntentForPackage(CALCULATOR_CANDIDATES[i]) != null) {
                        return CALCULATOR_CANDIDATES[i];
                    }
                } catch (Exception ignored) { }
            }
        }
        return CALCULATOR_CANDIDATES[0];
    }

    public static String targetPackage(Context context) {
        return prefs(context).getString(TARGET_PACKAGE, defaultTargetPackage(context));
    }

    public static String targetLabel(Context context) {
        return prefs(context).getString(TARGET_LABEL, "Calculator");
    }

    public static void setTargetApp(Context context, String packageName, String label) {
        prefs(context).edit()
                .putString(TARGET_PACKAGE, packageName == null ? "" : packageName.trim())
                .putString(TARGET_LABEL, label == null || label.trim().length() == 0 ? "Selected app" : label.trim())
                .apply();
    }

    public static String actions(Context context) {
        String key = profileActionsKey(context);
        String legacyKey = ACTIONS + "_" + cleanProfile(currentProfile(context)).replace(' ', '_');
        return prefs(context).getString(key, prefs(context).getString(legacyKey, prefs(context).getString(ACTIONS, DEFAULT_ACTIONS)));
    }

    public static void setActions(Context context, String value) {
        prefs(context).edit().putString(profileActionsKey(context), value).apply();
    }

    private static String profileActionsKey(Context context) {
        String pkg = cleanPkg(targetPackage(context));
        return ACTIONS + "_" + pkg + "_" + cleanProfile(currentProfile(context)).replace(' ', '_');
    }

    private static String cleanProfile(String value) {
        String trimmed = value == null ? "" : value.replace(',', ' ').trim();
        if (trimmed.length() == 0) return "Default";
        if (trimmed.length() > 24) return trimmed.substring(0, 24);
        return trimmed;
    }

    private static boolean hasProfile(String profiles, String profile) {
        String[] parts = profiles.split(",");
        for (int i = 0; i < parts.length; i++) {
            if (profile.equals(parts[i].trim())) return true;
        }
        return false;
    }

    public static boolean scheduleEnabled(Context context, int slot) {
        if (slot == SLOT_EVENING) return prefs(context).getBoolean(EVENING_ENABLED, false);
        return prefs(context).getBoolean(MORNING_ENABLED, false);
    }

    public static int scheduleHour(Context context, int slot) {
        if (slot == SLOT_EVENING) return prefs(context).getInt(EVENING_HOUR, 18);
        return prefs(context).getInt(MORNING_HOUR, 9);
    }

    public static int scheduleMinute(Context context, int slot) {
        if (slot == SLOT_EVENING) return prefs(context).getInt(EVENING_MINUTE, 5);
        return prefs(context).getInt(MORNING_MINUTE, 5);
    }

    public static void setSchedule(Context context, int slot, boolean enabled, int hour, int minute) {
        String enabledKey = slot == SLOT_EVENING ? EVENING_ENABLED : MORNING_ENABLED;
        String hourKey = slot == SLOT_EVENING ? EVENING_HOUR : MORNING_HOUR;
        String minuteKey = slot == SLOT_EVENING ? EVENING_MINUTE : MORNING_MINUTE;
        prefs(context).edit()
                .putBoolean(enabledKey, enabled)
                .putInt(hourKey, hour)
                .putInt(minuteKey, minute)
                .apply();
    }

    public static String lastStatus(Context context) {
        return prefs(context).getString(LAST_STATUS, "Not run yet");
    }

    public static void setLastStatus(Context context, String value) {
        prefs(context).edit().putString(LAST_STATUS, value).apply();
    }

    public static void setPendingScheduledRun(Context context) {
        prefs(context).edit().putBoolean(PENDING_SCHEDULED_RUN, true).apply();
    }

    public static boolean consumePendingScheduledRun(Context context) {
        boolean pending = prefs(context).getBoolean(PENDING_SCHEDULED_RUN, false);
        if (pending) prefs(context).edit().putBoolean(PENDING_SCHEDULED_RUN, false).apply();
        return pending;
    }

    public static final String KEY_SCHEDULES = "custom_schedules_json";

    public static final class ScheduleItem {
        public String id;
        public String name;
        public int hour;
        public int minute;
        public boolean enabled;
        public int randomWindowMinutes; // 0 for exact, 2, 5, 10, 15, 30
        public String days; // 7-char mask: "1111100" (Mon..Sun) or presets
        public String profile;
        public String targetPackage;
        public String targetLabel;

        public ScheduleItem(String id, String name, int hour, int minute, boolean enabled, int randomWindowMinutes, String days, String profile, String targetPackage, String targetLabel) {
            this.id = id;
            this.name = name;
            this.hour = hour;
            this.minute = minute;
            this.enabled = enabled;
            this.randomWindowMinutes = randomWindowMinutes;
            this.days = normalizeDaysMask(days);
            this.profile = profile == null || profile.trim().length() == 0 ? "Default" : profile.trim();
            this.targetPackage = targetPackage == null ? "" : targetPackage.trim();
            this.targetLabel = targetLabel == null ? "" : targetLabel.trim();
        }

        public static String normalizeDaysMask(String d) {
            if (d == null || d.trim().length() == 0) return "1111100";
            String upper = d.trim().toUpperCase();
            if ("ALL".equals(upper) || "EVERYDAY".equals(upper) || "EVERY DAY".equals(upper)) return "1111111";
            if ("WEEKENDS".equals(upper) || "WEEKEND".equals(upper)) return "0000011";
            if ("WEEKDAYS".equals(upper) || "WEEKDAY".equals(upper)) return "1111100";
            if (upper.length() == 7) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < 7; i++) {
                    sb.append(upper.charAt(i) == '1' ? '1' : '0');
                }
                return sb.toString();
            }
            return "1111100";
        }

        public boolean isDayIndexActive(int index) {
            if (index < 0 || index >= 7) return false;
            return days.charAt(index) == '1';
        }

        public void toggleDayIndex(int index) {
            if (index < 0 || index >= 7) return;
            char[] chars = days.toCharArray();
            chars[index] = chars[index] == '1' ? '0' : '1';
            this.days = new String(chars);
        }

        public boolean isCalendarDayActive(int calDayOfWeek) {
            // Calendar.SUNDAY = 1, MONDAY = 2, TUESDAY = 3, WEDNESDAY = 4, THURSDAY = 5, FRIDAY = 6, SATURDAY = 7
            int idx = calDayOfWeek == java.util.Calendar.SUNDAY ? 6 : calDayOfWeek - 2;
            return isDayIndexActive(idx);
        }

        public String get12HourTime() {
            int h = hour % 12;
            if (h == 0) h = 12;
            return String.format("%02d:%02d", h, minute);
        }

        public String getAmPm() {
            return hour >= 12 ? "PM" : "AM";
        }

        public String daysBadge() {
            if ("1111111".equals(days)) return "Every Day (Mon-Sun)";
            if ("1111100".equals(days)) return "Weekdays (Mon-Fri)";
            if ("0000011".equals(days)) return "Weekends (Sat-Sun)";
            if ("0000000".equals(days)) return "No Days Selected";
            return daysShort();
        }

        public String daysShort() {
            if ("1111111".equals(days)) return "Every Day";
            if ("1111100".equals(days)) return "Mon - Fri";
            if ("0000011".equals(days)) return "Sat, Sun";
            String[] names = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 7; i++) {
                if (isDayIndexActive(i)) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(names[i]);
                }
            }
            return sb.length() > 0 ? sb.toString() : "None";
        }

        public String toleranceText() {
            if (randomWindowMinutes <= 0) return "Exact (0m)";
            return "±" + randomWindowMinutes + " min";
        }

        public String windowPreview() {
            if (randomWindowMinutes <= 0) {
                return String.format("%s %s (Exact)", get12HourTime(), getAmPm());
            }
            int startMin = (hour * 60 + minute - randomWindowMinutes + 1440) % 1440;
            int endMin = (hour * 60 + minute + randomWindowMinutes) % 1440;

            int sH = (startMin / 60) % 12;
            if (sH == 0) sH = 12;
            String sAmPm = (startMin / 60) >= 12 ? "PM" : "AM";

            int eH = (endMin / 60) % 12;
            if (eH == 0) eH = 12;
            String eAmPm = (endMin / 60) >= 12 ? "PM" : "AM";

            return String.format("%02d:%02d %s - %02d:%02d %s", sH, startMin % 60, sAmPm, eH, endMin % 60, eAmPm);
        }

        public static int cycleTolerance(int current) {
            if (current <= 0) return 2;
            if (current == 2) return 5;
            if (current == 5) return 10;
            if (current == 10) return 15;
            if (current == 15) return 30;
            return 0;
        }

        public org.json.JSONObject toJson() {
            org.json.JSONObject obj = new org.json.JSONObject();
            try {
                obj.put("id", id);
                obj.put("name", name);
                obj.put("hour", hour);
                obj.put("minute", minute);
                obj.put("enabled", enabled);
                obj.put("randomWindowMinutes", randomWindowMinutes);
                obj.put("days", days);
                obj.put("profile", profile);
                obj.put("targetPackage", targetPackage);
                obj.put("targetLabel", targetLabel);
            } catch (Exception ignored) { }
            return obj;
        }

        public static ScheduleItem fromJson(org.json.JSONObject obj) {
            if (obj == null) return null;
            return new ScheduleItem(
                obj.optString("id", "sched_" + System.currentTimeMillis()),
                obj.optString("name", "Daily Schedule"),
                obj.optInt("hour", 9),
                obj.optInt("minute", 0),
                obj.optBoolean("enabled", false),
                obj.optInt("randomWindowMinutes", 5),
                obj.optString("days", "1111100"),
                obj.optString("profile", "Default"),
                obj.optString("targetPackage", ""),
                obj.optString("targetLabel", "")
            );
        }
    }

    private static final String CRASH_REPORT = "pending_crash_report";

    /**
     * Set by the crash handler just before the process dies, read once by the restarted
     * process so the owner is told. Written with commit() - a dying process may not
     * survive long enough for an async apply() to reach disk.
     */
    public static String pendingCrashReport(Context context) {
        return prefs(context).getString(CRASH_REPORT, "");
    }

    public static void setPendingCrashReport(Context context, String value) {
        prefs(context).edit().putString(CRASH_REPORT, value == null ? "" : value).commit();
    }

    public static void clearPendingCrashReport(Context context) {
        prefs(context).edit().remove(CRASH_REPORT).apply();
    }

    private static final String SCHEDULE_LAST_RUN = "schedule_last_run_";

    /** Local calendar date as yyyy-MM-dd. Used to enforce one run per schedule per day. */
    public static String dateStamp(java.util.Calendar cal) {
        if (cal == null) return "";
        return String.format(java.util.Locale.US, "%04d-%02d-%02d",
                cal.get(java.util.Calendar.YEAR),
                cal.get(java.util.Calendar.MONTH) + 1,
                cal.get(java.util.Calendar.DAY_OF_MONTH));
    }

    public static String todayStamp() {
        return dateStamp(java.util.Calendar.getInstance());
    }

    public static String scheduleLastRunDate(Context context, String id) {
        if (id == null) return "";
        return prefs(context).getString(SCHEDULE_LAST_RUN + id, "");
    }

    /**
     * Stamped immediately before a scheduled run is dispatched. commit() rather than
     * apply() on purpose: if the process dies mid-run, an async write could be lost and
     * the schedule would be free to fire again the same day.
     */
    public static void setScheduleLastRunDate(Context context, String id, String date) {
        if (id == null) return;
        prefs(context).edit().putString(SCHEDULE_LAST_RUN + id, date == null ? "" : date).commit();
    }

    public static void clearScheduleLastRunDate(Context context, String id) {
        if (id == null) return;
        prefs(context).edit().remove(SCHEDULE_LAST_RUN + id).apply();
    }

    public static boolean scheduleAlreadyRanToday(Context context, String id) {
        return id != null && todayStamp().equals(scheduleLastRunDate(context, id));
    }

    public static java.util.ArrayList<ScheduleItem> getSchedules(Context context) {
        java.util.ArrayList<ScheduleItem> list = new java.util.ArrayList<ScheduleItem>();
        String json = prefs(context).getString(KEY_SCHEDULES, "");
        if (json.length() == 0) {
            // Default initial schedules
            list.add(new ScheduleItem("sched_morning", "Morning Check-In", 9, 5, false, 5, "1111100", currentProfile(context), targetPackage(context), targetLabel(context)));
            list.add(new ScheduleItem("sched_evening", "Evening Punch-Out", 18, 5, false, 5, "1111100", currentProfile(context), targetPackage(context), targetLabel(context)));
            saveSchedules(context, list);
            return list;
        }
        try {
            org.json.JSONArray array = new org.json.JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                ScheduleItem item = ScheduleItem.fromJson(array.optJSONObject(i));
                if (item != null) list.add(item);
            }
        } catch (Exception ignored) { }
        return list;
    }

    public static void saveSchedules(Context context, java.util.ArrayList<ScheduleItem> list) {
        org.json.JSONArray array = new org.json.JSONArray();
        if (list != null) {
            for (int i = 0; i < list.size(); i++) {
                array.put(list.get(i).toJson());
            }
        }
        prefs(context).edit().putString(KEY_SCHEDULES, array.toString()).apply();
    }

    public static void saveSchedule(Context context, ScheduleItem item) {
        if (item == null) return;
        // An explicit edit means "re-arm as specified" - forget that it already ran today,
        // otherwise moving a schedule to a later time on the same day would silently
        // defer it to tomorrow. Only ever called from the UI, never from the fire path.
        clearScheduleLastRunDate(context, item.id);
        java.util.ArrayList<ScheduleItem> list = getSchedules(context);
        boolean updated = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(item.id)) {
                list.set(i, item);
                updated = true;
                break;
            }
        }
        if (!updated) {
            list.add(item);
        }
        saveSchedules(context, list);
    }

    public static void deleteSchedule(Context context, String id) {
        if (id == null) return;
        java.util.ArrayList<ScheduleItem> list = getSchedules(context);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(id)) {
                list.remove(i);
                break;
            }
        }
        saveSchedules(context, list);
    }

    public static ScheduleItem getScheduleById(Context context, String id) {
        if (id == null) return null;
        java.util.ArrayList<ScheduleItem> list = getSchedules(context);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(id)) return list.get(i);
        }
        return null;
    }

    public static boolean telegramEnabled(Context context) {
        return prefs(context).getBoolean(TELEGRAM_ENABLED, false);
    }

    public static String telegramToken(Context context) {
        return prefs(context).getString(TELEGRAM_TOKEN, "");
    }

    public static String telegramChat(Context context) {
        return prefs(context).getString(TELEGRAM_CHAT, "");
    }

    public static boolean hasTelegramCredentials(Context context) {
        return telegramToken(context).trim().length() > 0 && telegramChat(context).trim().length() > 0;
    }

    public static boolean telegramConfigured(Context context) {
        return telegramEnabled(context) && hasTelegramCredentials(context);
    }

    public static void setTelegram(Context context, boolean enabled, String token, String chat) {
        prefs(context).edit()
                .putBoolean(TELEGRAM_ENABLED, enabled)
                .putString(TELEGRAM_TOKEN, token == null ? "" : token.trim())
                .putString(TELEGRAM_CHAT, chat == null ? "" : chat.trim())
                .apply();
    }

    public static final String TELEGRAM_REMOTE_ENABLED = "telegram_remote_enabled";
    public static final String TELEGRAM_LAST_UPDATE_ID = "telegram_last_update_id";

    public static boolean telegramRemoteEnabled(Context context) {
        return prefs(context).getBoolean(TELEGRAM_REMOTE_ENABLED, true);
    }

    public static void setTelegramRemoteEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(TELEGRAM_REMOTE_ENABLED, enabled).apply();
    }

    public static long telegramLastUpdateId(Context context) {
        return prefs(context).getLong(TELEGRAM_LAST_UPDATE_ID, 0);
    }

    public static void setTelegramLastUpdateId(Context context, long id) {
        prefs(context).edit().putLong(TELEGRAM_LAST_UPDATE_ID, id).apply();
    }
}
