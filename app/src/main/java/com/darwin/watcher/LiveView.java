package com.darwin.watcher;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;

/**
 * Telegram "live view": one chat message whose photo is replaced on every refresh,
 * plus an inline keyboard for driving the phone.
 *
 * Deliberately not video. AccessibilityService.takeScreenshot is rate-limited to about
 * one call per second and each frame is a fresh multipart upload, so the honest ceiling
 * is a few seconds per frame. For real-time mirroring use scrcpy over ADB.
 */
public final class LiveView {
    private static final String TAG = "LiveView";

    /** Grid used for coarse tapping. Row-major, buttons labelled 1..GRID_ROWS*GRID_COLS. */
    private static final int GRID_COLS = 3;
    private static final int GRID_ROWS = 4;

    /** Frames are downscaled before upload - full 720x1600 JPEGs make refresh crawl. */
    private static final int FRAME_WIDTH = 480;
    private static final int FRAME_QUALITY = 60;

    /** Floor on auto-refresh. Below this the screenshot API and Telegram both throttle. */
    private static final long AUTO_INTERVAL_MS = 3000L;
    private static final int MAX_FRAMES = 100;
    private static final long MAX_SESSION_MS = 5 * 60 * 1000L;

    private static final Handler HANDLER = new Handler(Looper.getMainLooper());

    private static volatile boolean active = false;
    private static volatile boolean auto = false;
    private static volatile int messageId = 0;
    private static volatile int frames = 0;
    private static volatile int consecutiveErrors = 0;
    private static volatile long startedAt = 0L;
    private static Runnable ticker = null;

    private LiveView() { }

    public static boolean isActive() {
        return active;
    }

    public static void start(Context context) {
        if (active) {
            capture(context, "Live view already running");
            return;
        }
        active = true;
        auto = false;
        messageId = 0;
        frames = 0;
        consecutiveErrors = 0;
        startedAt = System.currentTimeMillis();
        capture(context, "Live view started");
    }

    public static void stop(Context context, String why) {
        boolean wasActive = active;
        active = false;
        auto = false;
        cancelTicker();
        messageId = 0;
        if (wasActive && context != null && why != null) {
            TelegramNotifier.sendText(context, "⏹ Live view stopped - " + why + ".", null);
        }
    }

    /** Returns true if the callback belonged to live view. */
    public static boolean handleCallback(Context context, String data, String queryId) {
        if (data == null || !data.startsWith("cb_lv_")) return false;

        if ("cb_lv_stop".equals(data)) {
            TelegramNotifier.answerCallbackQuery(context, queryId, "Live view stopped");
            stop(context, "closed");
            return true;
        }
        if ("cb_lv_refresh".equals(data)) {
            TelegramNotifier.answerCallbackQuery(context, queryId, "Refreshing");
            capture(context, null);
            return true;
        }
        if ("cb_lv_auto".equals(data)) {
            auto = !auto;
            TelegramNotifier.answerCallbackQuery(context, queryId, auto ? "Auto-refresh on" : "Auto-refresh paused");
            if (auto) scheduleTick(context); else cancelTicker();
            capture(context, null);
            return true;
        }

        WatcherAccessibilityService s = WatcherAccessibilityService.current();
        if (s == null) {
            TelegramNotifier.answerCallbackQuery(context, queryId, "Accessibility service not active");
            return true;
        }

        if ("cb_lv_home".equals(data)) {
            TelegramNotifier.answerCallbackQuery(context, queryId, "Home");
            s.triggerHome();
        } else if ("cb_lv_back".equals(data)) {
            TelegramNotifier.answerCallbackQuery(context, queryId, "Back");
            s.triggerBack();
        } else if ("cb_lv_recents".equals(data)) {
            TelegramNotifier.answerCallbackQuery(context, queryId, "Recents");
            s.triggerRecents();
        } else if (data.startsWith("cb_lv_t:")) {
            String[] rc = data.substring(8).split(":");
            try {
                int row = Integer.parseInt(rc[0]);
                int col = Integer.parseInt(rc[1]);
                int[] xy = cellCentre(s, row, col);
                s.tap(xy[0], xy[1], null);
                TelegramNotifier.answerCallbackQuery(context, queryId, "Tap " + xy[0] + "," + xy[1]);
            } catch (Exception e) {
                TelegramNotifier.answerCallbackQuery(context, queryId, "Bad grid cell");
                return true;
            }
        } else {
            TelegramNotifier.answerCallbackQuery(context, queryId, "Unknown live action");
            return true;
        }

        // Give the UI a moment to settle, then show the result without a second tap.
        HANDLER.postDelayed(new DelayedCapture(context), 900);
        return true;
    }

    private static int[] cellCentre(WatcherAccessibilityService s, int row, int col) {
        DisplayMetrics m = s.getResources().getDisplayMetrics();
        int w = m.widthPixels > 0 ? m.widthPixels : 1080;
        int h = m.heightPixels > 0 ? m.heightPixels : 2400;
        int x = (int) ((col + 0.5f) * w / GRID_COLS);
        int y = (int) ((row + 0.5f) * h / GRID_ROWS);
        return new int[]{x, y};
    }

    private static void capture(Context context, String note) {
        if (!active) return;
        WatcherAccessibilityService s = WatcherAccessibilityService.current();
        if (s == null) {
            TelegramNotifier.sendText(context, "⚠️ Live view needs the Accessibility service. Enable it and try `/live` again.", null);
            active = false;
            return;
        }
        s.captureFrame(FRAME_WIDTH, FRAME_QUALITY, new FrameHandler(context.getApplicationContext(), note));
    }

    private static final class FrameHandler implements WatcherAccessibilityService.FrameReady {
        private final Context context;
        private final String note;

        FrameHandler(Context context, String note) {
            this.context = context;
            this.note = note;
        }

        @Override
        public void onFrame(byte[] jpeg) {
            if (!active) return;
            if (jpeg == null) {
                consecutiveErrors++;
                if (consecutiveErrors >= 3) stop(context, "screen capture kept failing");
                return;
            }
            frames++;
            TelegramNotifier.sendOrEditFrame(context, messageId, jpeg, caption(context, note),
                    keyboard(), new ResultHandler(context));
        }
    }

    private static final class ResultHandler implements TelegramNotifier.MessageCallback {
        private final Context context;

        ResultHandler(Context context) {
            this.context = context;
        }

        @Override
        public void onMessage(boolean ok, int id, String error) {
            if (!ok) {
                consecutiveErrors++;
                Log.w(TAG, "frame delivery failed: " + error);
                // "message is not modified" just means the screen did not change; harmless.
                if (error != null && error.indexOf("not modified") >= 0) {
                    consecutiveErrors = 0;
                    return;
                }
                if (consecutiveErrors >= 3) stop(context, "Telegram kept rejecting updates");
                return;
            }
            consecutiveErrors = 0;
            if (id > 0) messageId = id;
        }
    }

    private static final class DelayedCapture implements Runnable {
        private final Context context;

        DelayedCapture(Context context) {
            this.context = context;
        }

        @Override
        public void run() {
            capture(context, null);
        }
    }

    private static final class Ticker implements Runnable {
        private final Context context;

        Ticker(Context context) {
            this.context = context;
        }

        @Override
        public void run() {
            if (!active || !auto) return;
            if (frames >= MAX_FRAMES) {
                stop(context, "frame limit reached (" + MAX_FRAMES + ")");
                return;
            }
            if (System.currentTimeMillis() - startedAt > MAX_SESSION_MS) {
                stop(context, "session timed out after " + (MAX_SESSION_MS / 60000) + " min");
                return;
            }
            capture(context, null);
            scheduleTick(context);
        }
    }

    private static void scheduleTick(Context context) {
        cancelTicker();
        ticker = new Ticker(context.getApplicationContext());
        HANDLER.postDelayed(ticker, AUTO_INTERVAL_MS);
    }

    private static void cancelTicker() {
        if (ticker != null) {
            HANDLER.removeCallbacks(ticker);
            ticker = null;
        }
    }

    private static String caption(Context context, String note) {
        StringBuilder sb = new StringBuilder();
        if (note != null && note.length() > 0) sb.append(note).append("\n");
        sb.append("🖥 ").append(DeviceUtils.getDeviceModelName());
        sb.append("  ·  frame ").append(frames);
        sb.append(auto ? "  ·  ▶ auto" : "  ·  ⏸ manual");
        sb.append("\n👤 profile: ").append(Prefs.currentProfile(context));
        String fg = WatcherAccessibilityService.foregroundPackageName();
        if (fg != null && fg.length() > 0) sb.append("\n📱 foreground: ").append(fg);
        sb.append("\n🕒 ").append(new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
                .format(new java.util.Date()));
        sb.append("\nGrid taps are coarse - use /tap <x> <y> for precision.");
        return sb.toString();
    }

    private static String keyboard() {
        StringBuilder sb = new StringBuilder("{\"inline_keyboard\":[");
        int n = 1;
        for (int r = 0; r < GRID_ROWS; r++) {
            sb.append(r == 0 ? "[" : ",[");
            for (int c = 0; c < GRID_COLS; c++) {
                if (c > 0) sb.append(",");
                sb.append("{\"text\":\"").append(n++)
                  .append("\",\"callback_data\":\"cb_lv_t:").append(r).append(":").append(c).append("\"}");
            }
            sb.append("]");
        }
        sb.append(",[{\"text\":\"◀ Back\",\"callback_data\":\"cb_lv_back\"}")
          .append(",{\"text\":\"⬤ Home\",\"callback_data\":\"cb_lv_home\"}")
          .append(",{\"text\":\"❐ Recents\",\"callback_data\":\"cb_lv_recents\"}]");
        sb.append(",[{\"text\":\"🔄 Refresh\",\"callback_data\":\"cb_lv_refresh\"}")
          .append(",{\"text\":\"").append(auto ? "⏸ Pause" : "▶️ Auto")
          .append("\",\"callback_data\":\"cb_lv_auto\"}")
          .append(",{\"text\":\"⏹ Stop\",\"callback_data\":\"cb_lv_stop\"}]");
        sb.append("]}");
        return sb.toString();
    }
}
