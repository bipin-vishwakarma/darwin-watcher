package com.darwin.watcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;

import java.util.ArrayList;

/**
 * Telegram live view: one chat message whose photo is replaced on every refresh, with
 * an inline keyboard for driving the phone.
 *
 * A grid coarse enough to fit an inline keyboard cannot tap accurately - on 720x1600 a
 * 4x6 grid is 180x267px per cell, still bigger than most buttons. So the grid is a ZOOM
 * selector, not a tapper: picking a cell narrows the view to that region and redraws, and
 * an explicit TAP button hits the crosshair at the centre of whatever is shown. One zoom
 * reaches about 45x67px, which is button-sized; a second reaches ~11x11px.
 *
 * The grid is drawn onto the frame itself. Numbers that live only in the keyboard leave
 * the reader guessing which part of the screen each one means.
 *
 * Deliberately not video: takeScreenshot is rate-limited to about 1/s and every frame is
 * a fresh upload. For real-time mirroring with a real pointer, use scrcpy over ADB.
 */
public final class LiveView {
    private static final String TAG = "LiveView";

    /**
     * 4x6 rather than 3x4. On 720x1600 that puts each cell at 180x267px - about one app
     * icon - so a single zoom reaches ~45x67px and you can aim in two actions instead of
     * three. Coarser grids look tidier and cost an extra round trip every time.
     */
    private static final int GRID_COLS = 4;
    private static final int GRID_ROWS = 6;

    /** Frames are rendered here, never on the main thread - see FrameHandler.onBitmap. */
    private static final java.util.concurrent.ExecutorService RENDER =
            java.util.concurrent.Executors.newSingleThreadExecutor();

    /** Width of the frame sent to Telegram. Wide enough to read, small enough to upload. */
    private static final int OUT_WIDTH = 560;
    private static final int JPEG_QUALITY = 70;

    /** Stop offering to zoom once a cell is about this small - just tap it. */
    private static final int MIN_CELL_PX = 24;

    private static final long AUTO_INTERVAL_MS = 3000L;
    private static final int MAX_FRAMES = 120;
    private static final long MAX_SESSION_MS = 5 * 60 * 1000L;

    private static final Handler HANDLER = new Handler(Looper.getMainLooper());

    private static volatile boolean active = false;
    private static volatile boolean auto = false;
    private static volatile int messageId = 0;
    private static volatile int frames = 0;
    private static volatile int consecutiveErrors = 0;
    private static volatile long startedAt = 0L;
    private static Runnable ticker = null;

    /** Region of the screen currently shown, in device pixels. */
    private static Rect view = null;
    private static final ArrayList<Rect> zoomStack = new ArrayList<Rect>();

    private LiveView() { }

    public static boolean isActive() {
        return active;
    }

    public static void start(Context context) {
        if (!active) {
            active = true;
            auto = false;
            messageId = 0;
            frames = 0;
            consecutiveErrors = 0;
            startedAt = System.currentTimeMillis();
        }
        resetView();
        capture(context);
    }

    public static void stop(Context context, String why) {
        boolean wasActive = active;
        active = false;
        auto = false;
        cancelTicker();
        messageId = 0;
        view = null;
        zoomStack.clear();
        if (wasActive && context != null && why != null) {
            TelegramNotifier.sendText(context, "⏹ Live view stopped - " + why + ".", null);
        }
    }

    private static void resetView() {
        view = fullScreen();
        zoomStack.clear();
    }

    private static Rect fullScreen() {
        WatcherAccessibilityService s = WatcherAccessibilityService.current();
        int w = 1080, h = 2400;
        if (s != null) {
            DisplayMetrics m = s.getResources().getDisplayMetrics();
            if (m.widthPixels > 0) w = m.widthPixels;
            if (m.heightPixels > 0) h = m.heightPixels;
        }
        return new Rect(0, 0, w, h);
    }

    /** Returns true if the callback belonged to live view. */
    public static boolean handleCallback(Context context, String data, String queryId) {
        if (data == null || !data.startsWith("cb_lv_")) return false;
        if (view == null) resetView();

        if ("cb_lv_stop".equals(data)) {
            TelegramNotifier.answerCallbackQuery(context, queryId, "Live view stopped");
            stop(context, "closed");
            return true;
        }
        if ("cb_lv_refresh".equals(data)) {
            TelegramNotifier.answerCallbackQuery(context, queryId, "Refreshing");
            capture(context);
            return true;
        }
        if ("cb_lv_auto".equals(data)) {
            auto = !auto;
            TelegramNotifier.answerCallbackQuery(context, queryId, auto ? "Auto-refresh on" : "Auto-refresh paused");
            if (auto) scheduleTick(context); else cancelTicker();
            capture(context);
            return true;
        }
        if ("cb_lv_out".equals(data)) {
            if (zoomStack.isEmpty()) {
                TelegramNotifier.answerCallbackQuery(context, queryId, "Already showing the whole screen");
            } else {
                view = zoomStack.remove(zoomStack.size() - 1);
                TelegramNotifier.answerCallbackQuery(context, queryId, "Zoomed out");
            }
            capture(context);
            return true;
        }
        if ("cb_lv_reset".equals(data)) {
            resetView();
            TelegramNotifier.answerCallbackQuery(context, queryId, "Whole screen");
            capture(context);
            return true;
        }

        WatcherAccessibilityService s = WatcherAccessibilityService.current();
        if (s == null) {
            TelegramNotifier.answerCallbackQuery(context, queryId, "Accessibility service not active");
            return true;
        }

        if (data.startsWith("cb_lv_c:")) {
            // Zoom into a cell rather than tapping it - a full-screen cell is far too
            // coarse to hit anything deliberately.
            try {
                int n = Integer.parseInt(data.substring(8));
                Rect cell = cellRect(view, n / GRID_COLS, n % GRID_COLS);
                if (cell.width() <= MIN_CELL_PX || cell.height() <= MIN_CELL_PX) {
                    tapCentre(context, s, cell, queryId);
                    return true;
                }
                zoomStack.add(new Rect(view));
                view = cell;
                TelegramNotifier.answerCallbackQuery(context, queryId,
                        "Zoom " + cell.width() + "x" + cell.height() + "px");
                capture(context);
            } catch (Exception e) {
                TelegramNotifier.answerCallbackQuery(context, queryId, "Bad cell");
            }
            return true;
        }

        if ("cb_lv_tap".equals(data)) {
            tapCentre(context, s, view, queryId);
            return true;
        }
        if ("cb_lv_home".equals(data)) {
            TelegramNotifier.answerCallbackQuery(context, queryId, "Home");
            s.triggerHome();
            resetView();
        } else if ("cb_lv_back".equals(data)) {
            TelegramNotifier.answerCallbackQuery(context, queryId, "Back");
            s.triggerBack();
            resetView();
        } else if ("cb_lv_recents".equals(data)) {
            TelegramNotifier.answerCallbackQuery(context, queryId, "Recents");
            s.triggerRecents();
            resetView();
        } else {
            TelegramNotifier.answerCallbackQuery(context, queryId, "Unknown live action");
            return true;
        }

        HANDLER.postDelayed(new DelayedCapture(context), 900);
        return true;
    }

    private static void tapCentre(Context context, WatcherAccessibilityService s, Rect r, String queryId) {
        int x = r.centerX();
        int y = r.centerY();
        s.tap(x, y, null);
        TelegramNotifier.answerCallbackQuery(context, queryId, "Tapped " + x + "," + y);
        // Come back to the whole screen: after acting, the next thing you want to see
        // is where you ended up, not the patch you were aiming at.
        resetView();
        HANDLER.postDelayed(new DelayedCapture(context), 1100);
    }

    private static Rect cellRect(Rect r, int row, int col) {
        float cw = r.width() / (float) GRID_COLS;
        float ch = r.height() / (float) GRID_ROWS;
        int l = (int) (r.left + col * cw);
        int t = (int) (r.top + row * ch);
        return new Rect(l, t, (int) (l + cw), (int) (t + ch));
    }

    private static void capture(Context context) {
        if (!active) return;
        WatcherAccessibilityService s = WatcherAccessibilityService.current();
        if (s == null) {
            TelegramNotifier.sendText(context, "⚠️ Live view needs the Accessibility service. Enable it and send `/live` again.", null);
            active = false;
            return;
        }
        s.captureBitmap(new FrameHandler(context.getApplicationContext()));
    }

    private static final class FrameHandler implements WatcherAccessibilityService.BitmapReady {
        private final Context context;

        FrameHandler(Context context) {
            this.context = context;
        }

        @Override
        public void onBitmap(Bitmap full) {
            // Runs on the main thread: takeScreenshot() is handed getMainExecutor()
            // because the end-of-run path touches WindowManager overlays. Cropping,
            // drawing and JPEG-compressing a frame here would block the UI thread on
            // every refresh and ANR the app, so hand off immediately and do nothing
            // expensive on this thread.
            if (!active) {
                if (full != null) full.recycle();
                return;
            }
            if (full == null) {
                consecutiveErrors++;
                if (consecutiveErrors >= 3) stop(context, "screen capture kept failing");
                return;
            }
            RENDER.execute(new RenderTask(context, full));
        }
    }

    private static final class RenderTask implements Runnable {
        private final Context context;
        private final Bitmap full;

        RenderTask(Context context, Bitmap full) {
            this.context = context;
            this.full = full;
        }

        @Override
        public void run() {
            byte[] jpeg = null;
            try {
                jpeg = render(full);
            } catch (Throwable t) {
                Log.e(TAG, "frame render failed", t);
            } finally {
                full.recycle();
            }
            if (!active) return;
            if (jpeg == null) {
                consecutiveErrors++;
                if (consecutiveErrors >= 3) stop(context, "frame rendering kept failing");
                return;
            }
            frames++;
            TelegramNotifier.sendOrEditFrame(context, messageId, jpeg, caption(context),
                    keyboard(), new ResultHandler(context));
        }
    }

    /** Crop to the current view, scale up, then draw the grid, numbers and crosshair. */
    private static byte[] render(Bitmap full) {
        Rect r = view != null ? view : fullScreen();
        Rect src = new Rect(
                Math.max(0, r.left), Math.max(0, r.top),
                Math.min(full.getWidth(), r.right), Math.min(full.getHeight(), r.bottom));
        if (src.width() <= 0 || src.height() <= 0) return null;

        int outW = OUT_WIDTH;
        int outH = Math.max(1, (int) ((long) src.height() * outW / src.width()));
        Bitmap frame = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(frame);
        c.drawBitmap(full, src, new Rect(0, 0, outW, outH), null);

        float cw = outW / (float) GRID_COLS;
        float ch = outH / (float) GRID_ROWS;

        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setColor(Color.argb(200, 255, 214, 10));
        line.setStrokeWidth(Math.max(2f, outW / 260f));
        for (int i = 1; i < GRID_COLS; i++) c.drawLine(i * cw, 0, i * cw, outH, line);
        for (int i = 1; i < GRID_ROWS; i++) c.drawLine(0, i * ch, outW, i * ch, line);

        float textSize = Math.min(cw, ch) * 0.34f;
        Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
        halo.setColor(Color.argb(150, 0, 0, 0));
        Paint num = new Paint(Paint.ANTI_ALIAS_FLAG);
        num.setColor(Color.WHITE);
        num.setTextSize(textSize);
        num.setTextAlign(Paint.Align.CENTER);
        num.setFakeBoldText(true);

        int n = 1;
        for (int row = 0; row < GRID_ROWS; row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                float cx = (col + 0.5f) * cw;
                float cy = (row + 0.5f) * ch;
                c.drawCircle(cx, cy, textSize * 0.72f, halo);
                c.drawText(String.valueOf(n++), cx, cy + textSize * 0.36f, num);
            }
        }

        // Crosshair marks exactly where the TAP button will land.
        Paint cross = new Paint(Paint.ANTI_ALIAS_FLAG);
        cross.setColor(Color.rgb(255, 45, 45));
        cross.setStrokeWidth(Math.max(2f, outW / 200f));
        cross.setStyle(Paint.Style.STROKE);
        float mx = outW / 2f, my = outH / 2f;
        float arm = Math.min(cw, ch) * 0.30f;
        c.drawCircle(mx, my, arm * 0.55f, cross);
        c.drawLine(mx - arm, my, mx + arm, my, cross);
        c.drawLine(mx, my - arm, mx, my + arm, cross);

        byte[] out = WatcherAccessibilityService.compressJpeg(frame, JPEG_QUALITY);
        frame.recycle();
        return out;
    }

    private static final class ResultHandler implements TelegramNotifier.MessageCallback {
        private final Context context;

        ResultHandler(Context context) {
            this.context = context;
        }

        @Override
        public void onMessage(boolean ok, int id, String error) {
            if (!ok) {
                // "message is not modified" only means the screen did not change.
                if (error != null && error.indexOf("not modified") >= 0) {
                    consecutiveErrors = 0;
                    return;
                }
                consecutiveErrors++;
                Log.w(TAG, "frame delivery failed: " + error);
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
            capture(context);
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
            capture(context);
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

    private static String caption(Context context) {
        Rect r = view != null ? view : fullScreen();
        Rect fs = fullScreen();
        boolean whole = r.width() >= fs.width() && r.height() >= fs.height();

        StringBuilder sb = new StringBuilder();
        sb.append("1-12 zooms into that box · ✥ TAP hits the red crosshair\n");
        if (whole) {
            sb.append("📐 whole screen ").append(r.width()).append("×").append(r.height());
        } else {
            sb.append("🔍 zoom ").append(r.width()).append("×").append(r.height())
              .append("px at (").append(r.centerX()).append(",").append(r.centerY()).append(")");
        }
        sb.append("  ·  frame ").append(frames).append(auto ? "  ·  ▶ auto" : "  ·  ⏸ manual");
        String fg = WatcherAccessibilityService.foregroundPackageName();
        if (fg != null && fg.length() > 0) sb.append("\n📱 ").append(fg);
        sb.append("  ·  🕒 ").append(new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
                .format(new java.util.Date()));
        return sb.toString();
    }

    private static String keyboard() {
        StringBuilder sb = new StringBuilder("{\"inline_keyboard\":[");
        int n = 0;
        for (int r = 0; r < GRID_ROWS; r++) {
            sb.append(r == 0 ? "[" : ",[");
            for (int c = 0; c < GRID_COLS; c++) {
                if (c > 0) sb.append(",");
                sb.append("{\"text\":\"").append(n + 1)
                  .append("\",\"callback_data\":\"cb_lv_c:").append(n).append("\"}");
                n++;
            }
            sb.append("]");
        }
        sb.append(",[{\"text\":\"✥ TAP\",\"callback_data\":\"cb_lv_tap\"}")
          .append(",{\"text\":\"🔍 Out\",\"callback_data\":\"cb_lv_out\"}")
          .append(",{\"text\":\"⛶ Whole\",\"callback_data\":\"cb_lv_reset\"}]");
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
