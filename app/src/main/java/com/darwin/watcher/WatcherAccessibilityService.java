package com.darwin.watcher;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.PowerManager;
import android.text.Html;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;

public class WatcherAccessibilityService extends AccessibilityService implements View.OnClickListener, View.OnTouchListener {
    private static WatcherAccessibilityService instance;

    private WindowManager windows;
    private View panel;
    private View minimized;
    private View capture;
    private View watermark;
    private PathOverlayView pathOverlay;
    private Button togglePathBtn;
    private TextView recorded;
    private int currentDelaySec = 2;
    private TextView delayValueText;
    private TargetReticleView captureTarget;
    private TextView captureHint;
    private WindowManager.LayoutParams panelParams;
    private int dragStartX;
    private int dragStartY;
    private int panelSavedX = -1;
    private int panelSavedY = -1;
    private int captureStartX;
    private int captureStartY;
    private int captureX;
    private int captureY;
    private float touchStartX;
    private float touchStartY;
    private CharSequence foregroundPackage;
    private boolean pendingOverlay;

    public interface Done {
        void call(boolean ok, String message);
    }

    public static WatcherAccessibilityService current() {
        return instance;
    }

    public static boolean isRunning() {
        return instance != null;
    }

    @Override
    protected void onServiceConnected() {
        instance = this;
        windows = (WindowManager) getSystemService(WINDOW_SERVICE);
        try {
            android.accessibilityservice.AccessibilityServiceInfo info = getServiceInfo();
            if (info == null) info = new android.accessibilityservice.AccessibilityServiceInfo();
            info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED | AccessibilityEvent.TYPE_WINDOWS_CHANGED;
            info.feedbackType = android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_GENERIC;
            info.flags = android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS |
                         android.accessibilityservice.AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS |
                         android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
            info.notificationTimeout = 100;
            setServiceInfo(info);
        } catch (Exception ignored) { }
    }

    @Override
    public void onDestroy() {
        hideOverlay();
        hideRunningWatermark();
        if (instance == this) {
            instance = null;
        }
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event != null && event.getPackageName() != null) {
            CharSequence packageName = event.getPackageName();
            if (getPackageName().contentEquals(packageName)) {
                return;
            }
            foregroundPackage = packageName;
            if (isTargetActive(Prefs.targetPackage(this))) {
                if (pendingOverlay) {
                    pendingOverlay = false;
                    showOverlay();
                }
            }
        }
    }

    @Override
    public boolean onUnbind(Intent intent) {
        hideOverlay();
        hideRunningWatermark();
        if (instance == this) {
            instance = null;
        }
        return super.onUnbind(intent);
    }

    @Override
    public void onInterrupt() { }

    public void resetForegroundPackage() {
        foregroundPackage = null;
    }

    /** Last non-self package seen in the foreground, or null. Used for live-view captions. */
    public static String foregroundPackageName() {
        WatcherAccessibilityService s = instance;
        if (s == null || s.foregroundPackage == null) return null;
        return s.foregroundPackage.toString();
    }

    public boolean launchTarget(String packageName) {
        if (packageName == null) return false;
        try {
            Intent launch = getPackageManager().getLaunchIntentForPackage(packageName);
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(launch);
                return true;
            }
        } catch (Exception ignored) { }
        return false;
    }

    public boolean isDeviceLocked() {
        try {
            KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            if (km != null && km.isKeyguardLocked()) {
                return true;
            }
        } catch (Exception ignored) { }
        return false;
    }

    public void unlockDevice() {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                PowerManager.WakeLock wl = pm.newWakeLock(
                    PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE,
                    "darwin:unlock_gesture_wake"
                );
                wl.acquire(10000);
            }
        } catch (Exception ignored) { }

        try {
            android.graphics.Path path = new android.graphics.Path();
            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            int screenHeight = getResources().getDisplayMetrics().heightPixels;
            if (screenWidth <= 0) screenWidth = 1080;
            if (screenHeight <= 0) screenHeight = 2400;

            float centerX = screenWidth / 2f;
            float startY = screenHeight * 0.85f;
            float endY = screenHeight * 0.15f;

            path.moveTo(centerX, startY);
            path.lineTo(centerX, endY);

            GestureDescription.Builder gb = new GestureDescription.Builder();
            gb.addStroke(new GestureDescription.StrokeDescription(path, 0, 200));
            dispatchGesture(gb.build(), null, null);
        } catch (Exception ignored) { }
    }

    public boolean lockDevice() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
        }
        return false;
    }

    public boolean triggerHome() {
        return performGlobalAction(GLOBAL_ACTION_HOME);
    }

    public boolean triggerBack() {
        return performGlobalAction(GLOBAL_ACTION_BACK);
    }

    public boolean triggerRecents() {
        return performGlobalAction(GLOBAL_ACTION_RECENTS);
    }

    public boolean triggerNotifications() {
        return performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);
    }

    public boolean enterText(String text) {
        if (text == null) return false;
        try {
            android.view.accessibility.AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                android.view.accessibility.AccessibilityNodeInfo focused = root.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT);
                if (focused != null) {
                    android.os.Bundle args = new android.os.Bundle();
                    args.putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
                    return focused.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT, args);
                }
            }
        } catch (Exception ignored) { }
        return false;
    }

    public boolean isTargetActive(String packageName) {
        if (packageName == null) return false;
        if (isDeviceLocked()) {
            return false; // Screen is locked - target app is NOT active
        }
        try {
            android.view.accessibility.AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                CharSequence rootPkg = root.getPackageName();
                if (rootPkg != null && !getPackageName().contentEquals(rootPkg)) {
                    foregroundPackage = rootPkg;
                    return packageName.equals(rootPkg.toString());
                }
            }
        } catch (Exception ignored) { }
        return foregroundPackage != null && packageName.equals(foregroundPackage.toString());
    }

    public void requestOverlay() {
        pendingOverlay = true;
        Prefs.setLastStatus(this, "Waiting for " + Prefs.targetLabel(this) + " to open");
        if (isTargetActive(Prefs.targetPackage(this))) {
            pendingOverlay = false;
            showOverlay();
        }
    }

    public void showOverlay() {
        if (!isTargetActive(Prefs.targetPackage(this))) {
            pendingOverlay = true;
            Prefs.setLastStatus(this, "Waiting for " + Prefs.targetLabel(this) + " to open");
            return;
        }
        if (windows == null) windows = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (windows == null || panel != null) return;
        hideMinimized();

        showPathOverlay();

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable boxBg = new GradientDrawable();
        boxBg.setColor(0xF60B1120); // Deep frosted dark glass
        boxBg.setCornerRadius(dp(14));
        boxBg.setStroke(dp(1), 0xFF334155);
        box.setBackground(boxBg);

        // Header with Drag Handle & App Chip
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titleChip = new LinearLayout(this);
        titleChip.setOrientation(LinearLayout.VERTICAL);
        titleChip.setOnTouchListener(this);

        TextView dragHandle = new TextView(this);
        dragHandle.setText("••••");
        dragHandle.setTextColor(0xFF64748B);
        dragHandle.setTextSize(10);
        dragHandle.setGravity(Gravity.CENTER_HORIZONTAL);
        titleChip.addView(dragHandle);

        TextView title = new TextView(this);
        title.setText(Html.fromHtml("<b><font color='#38BDF8'>" + Prefs.targetLabel(this) + "</font></b> <font color='#94A3B8'>· " + Prefs.currentProfile(this) + "</font>"));
        title.setTextSize(12);
        title.setTextColor(Color.WHITE);
        titleChip.addView(title);
        header.addView(titleChip, weighted());

        Button minimize = smallButton("−", "minimize");
        header.addView(minimize);

        Button close = smallButton("✕", "close");
        header.addView(close);
        box.addView(header);

        box.addView(space(6));

        // Primary Row: Record Tap & Show/Hide Path
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        Button tap = primaryButton("Record Tap", "tap");
        row1.addView(tap, weighted());

        togglePathBtn = button(pathOverlay != null && pathOverlay.isShowPath() ? "Hide Path" : "Show Path", "togglePath");
        row1.addView(togglePathBtn, weighted());
        box.addView(row1);

        box.addView(space(6));

        // Quick Delay Presets Row
        LinearLayout presetsRow = new LinearLayout(this);
        presetsRow.setOrientation(LinearLayout.HORIZONTAL);
        presetsRow.addView(chipButton("1s", "preset1"), weighted());
        presetsRow.addView(chipButton("2s", "preset2"), weighted());
        presetsRow.addView(chipButton("5s", "preset5"), weighted());
        presetsRow.addView(chipButton("10s", "preset10"), weighted());
        box.addView(presetsRow);

        box.addView(space(6));

        // Delay Stepper Row
        LinearLayout delayRow = new LinearLayout(this);
        delayRow.setOrientation(LinearLayout.HORIZONTAL);
        delayRow.setGravity(Gravity.CENTER_VERTICAL);

        Button decBtn = smallButton("−", "decDelay");
        delayRow.addView(decBtn);

        delayValueText = new TextView(this);
        delayValueText.setText(currentDelaySec + "s");
        delayValueText.setTextColor(0xFF38BDF8);
        delayValueText.setTextSize(14);
        delayValueText.setTypeface(null, Typeface.BOLD);
        delayValueText.setGravity(Gravity.CENTER);
        delayRow.addView(delayValueText, weighted());

        Button incBtn = smallButton("+", "incDelay");
        delayRow.addView(incBtn);

        Button addDelay = button("+ Add Delay", "addDelay");
        LinearLayout.LayoutParams addDelayParams = new LinearLayout.LayoutParams(0, dp(42), 1.5f);
        addDelayParams.leftMargin = dp(6);
        delayRow.addView(addDelay, addDelayParams);
        box.addView(delayRow);

        box.addView(space(6));

        // Action Buttons Row: Undo, Clear, Save
        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        Button undo = button("Undo", "undo");
        row2.addView(undo, weighted());

        Button clear = button("Clear", "clear");
        row2.addView(clear, weighted());

        Button save = successButton("Save & Exit", "save");
        row2.addView(save, weighted());
        box.addView(row2);

        box.addView(space(8));

        // Actions Timeline Card
        LinearLayout timelineCard = new LinearLayout(this);
        timelineCard.setOrientation(LinearLayout.VERTICAL);
        timelineCard.setPadding(dp(10), dp(8), dp(10), dp(8));
        GradientDrawable timeBg = new GradientDrawable();
        timeBg.setColor(0xFF030712); // Deep dark
        timeBg.setCornerRadius(dp(8));
        timeBg.setStroke(dp(1), 0xFF1F2937);
        timelineCard.setBackground(timeBg);

        TextView timeHeader = new TextView(this);
        timeHeader.setText(Html.fromHtml("<font color='#64748B'><b>ACTION TIMELINE</b></font>"));
        timeHeader.setTextSize(10);
        timeHeader.setPadding(0, 0, 0, dp(4));
        timelineCard.addView(timeHeader);

        recorded = new TextView(this);
        recorded.setText(formatActionsTimeline());
        recorded.setTextColor(0xFFE2E8F0);
        recorded.setTextSize(11);
        timelineCard.addView(recorded);

        box.addView(timelineCard);

        panelParams = params(dp(270), WindowManager.LayoutParams.WRAP_CONTENT);
        panelParams.gravity = Gravity.TOP | Gravity.END;
        panelParams.x = panelSavedX >= 0 ? panelSavedX : dp(8);
        panelParams.y = panelSavedY >= 0 ? panelSavedY : dp(80);
        panel = box;
        windows.addView(panel, panelParams);
    }

    private void showPathOverlay() {
        if (windows == null || pathOverlay != null) return;
        pathOverlay = new PathOverlayView(this);
        WindowManager.LayoutParams pathParams = params(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        pathParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        windows.addView(pathOverlay, pathParams);
        updatePathPoints();
    }

    private void hidePathOverlay() {
        if (windows != null && pathOverlay != null) {
            try {
                windows.removeView(pathOverlay);
            } catch (IllegalArgumentException ignored) { }
            pathOverlay = null;
        }
    }

    private void updatePathPoints() {
        if (pathOverlay == null) return;
        ArrayList<TapPoint> points = new ArrayList<>();
        String[] lines = Prefs.actions(this).split("\\r?\\n");
        int tapIndex = 1;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.startsWith("tap ")) {
                String[] p = line.split("\\s+");
                if (p.length >= 3) {
                    try {
                        int x = Integer.parseInt(p[1]);
                        int y = Integer.parseInt(p[2]);
                        points.add(new TapPoint(x, y, tapIndex++));
                    } catch (NumberFormatException ignored) { }
                }
            }
        }
        pathOverlay.setPoints(points);
    }

    public void hideOverlay() {
        pendingOverlay = false;
        hideCapture();
        hideMinimized();
        hidePathOverlay();
        if (windows != null && panel != null) {
            try {
                windows.removeView(panel);
            } catch (IllegalArgumentException ignored) { }
            panel = null;
            panelParams = null;
        }
    }

    public void showRunningWatermark(String text) {
        if (windows == null) windows = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (windows == null) return;
        CharSequence content = Html.fromHtml("<font color='#22C55E'>●</font> <font color='#94A3B8'><b>WATCHER</b></font> <font color='#475569'>|</font> <font color='#F8FAFC'><b>" + text + "</b></font>");
        if (watermark instanceof TextView) {
            final TextView tv = (TextView) watermark;
            tv.animate().alpha(0.3f).scaleX(0.96f).scaleY(0.96f).setDuration(80).withEndAction(new TextCrossFade(tv, content)).start();
            return;
        }

        TextView view = new TextView(this);
        view.setText(content);
        view.setTextColor(0xFFF8FAFC);
        view.setTextSize(11);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(22), dp(8), dp(22), dp(8));
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF8000000); // Jet black Dynamic Island
        bg.setCornerRadius(dp(22));
        bg.setStroke(dp(1), 0xFF1E293B);
        view.setBackground(bg);

        // Initial state for smooth entrance animation
        view.setAlpha(0f);
        view.setScaleX(0.85f);
        view.setScaleY(0.85f);

        WindowManager.LayoutParams p = params(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT);
        p.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        p.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        p.y = dp(8);
        watermark = view;
        windows.addView(watermark, p);

        // Smooth spring entrance
        view.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
    }

    public void closeTarget() {
        performGlobalAction(GLOBAL_ACTION_HOME);
    }

    public void hideRunningWatermark() {
        if (windows != null && watermark != null) {
            final View target = watermark;
            watermark = null;
            target.animate().alpha(0f).scaleX(0.85f).scaleY(0.85f).setDuration(180).withEndAction(new RemoveWatermarkView(windows, target)).start();
        }
    }

    private static final class TextCrossFade implements Runnable {
        private final TextView view;
        private final CharSequence text;

        TextCrossFade(TextView view, CharSequence text) {
            this.view = view;
            this.text = text;
        }

        @Override
        public void run() {
            if (view != null) {
                view.setText(text);
                view.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(130).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
            }
        }
    }

    private static final class RemoveWatermarkView implements Runnable {
        private final WindowManager wm;
        private final View view;

        RemoveWatermarkView(WindowManager wm, View view) {
            this.wm = wm;
            this.view = view;
        }

        @Override
        public void run() {
            if (wm != null && view != null) {
                try {
                    wm.removeView(view);
                } catch (IllegalArgumentException ignored) { }
            }
        }
    }

    @Override
    public void onClick(View view) {
        Object tag = view.getTag();
        if ("tap".equals(tag)) {
            showCapture();
        } else if ("addTap".equals(tag)) {
            addCapturedTap();
        } else if ("addDelay".equals(tag)) {
            addDelay();
        } else if ("decDelay".equals(tag)) {
            if (currentDelaySec > 1) {
                currentDelaySec--;
                updateDelayDisplay();
            }
        } else if ("incDelay".equals(tag)) {
            if (currentDelaySec < 60) {
                currentDelaySec++;
                updateDelayDisplay();
            }
        } else if ("preset1".equals(tag)) {
            currentDelaySec = 1;
            updateDelayDisplay();
        } else if ("preset2".equals(tag)) {
            currentDelaySec = 2;
            updateDelayDisplay();
        } else if ("preset5".equals(tag)) {
            currentDelaySec = 5;
            updateDelayDisplay();
        } else if ("preset10".equals(tag)) {
            currentDelaySec = 10;
            updateDelayDisplay();
        } else if ("undo".equals(tag)) {
            undoLast();
        } else if ("clear".equals(tag)) {
            clearAll();
        } else if ("togglePath".equals(tag)) {
            togglePath();
        } else if ("profiles".equals(tag)) {
            openWatcher(MainActivity.TAB_PROFILES);
        } else if ("save".equals(tag)) {
            openWatcher(MainActivity.TAB_HOME);
        } else if ("minimize".equals(tag)) {
            minimizeOverlay();
        } else if ("restore".equals(tag)) {
            restoreOverlay();
        } else if ("close".equals(tag)) {
            hideOverlay();
        } else if ("cancelCapture".equals(tag)) {
            hideCapture();
        }
    }

    private void togglePath() {
        if (pathOverlay != null) {
            boolean next = !pathOverlay.isShowPath();
            pathOverlay.setShowPath(next);
            if (togglePathBtn != null) {
                togglePathBtn.setText(next ? "Hide Path" : "Show Path");
            }
        }
    }

    private void clearAll() {
        Prefs.setActions(this, "");
        if (recorded != null) recorded.setText(formatActionsTimeline());
        updatePathPoints();
    }

    @Override
    public boolean onTouch(View view, MotionEvent event) {
        if (view == captureTarget) {
            return moveCaptureTarget(view, event);
        }

        if (view == capture) {
            if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
                captureX = (int) event.getX();
                captureY = (int) event.getY();
                if (captureTarget != null) {
                    FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) captureTarget.getLayoutParams();
                    int size = dp(64);
                    params.leftMargin = captureX - (size / 2);
                    params.topMargin = captureY - (size / 2);
                    captureTarget.setLayoutParams(params);
                }
                if (captureHint != null) {
                    captureHint.setText("Target Tap #" + (tapCount() + 1) + " · (" + captureX + ", " + captureY + ")");
                }
                return true;
            }
            return true;
        }

        if (panelParams == null || windows == null || panel == null) return true;
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            dragStartX = panelParams.x;
            dragStartY = panelParams.y;
            touchStartX = event.getRawX();
            touchStartY = event.getRawY();
            return true;
        }
        if (event.getAction() == MotionEvent.ACTION_MOVE) {
            panelParams.x = dragStartX - (int) (event.getRawX() - touchStartX);
            panelParams.y = dragStartY + (int) (event.getRawY() - touchStartY);
            panelSavedX = panelParams.x;
            panelSavedY = panelParams.y;
            windows.updateViewLayout(panel, panelParams);
            return true;
        }
        return true;
    }

    public void tap(int x, int y, Done done) {
        Path path = new Path();
        path.moveTo(x, y);
        path.lineTo(x, y);
        gesture(path, 0, 130, done);
    }

    public void swipe(int x1, int y1, int x2, int y2, int duration, Done done) {
        Path path = new Path();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        gesture(path, 0, duration, done);
    }

    private void showCapture() {
        if (windows == null || capture != null) return;

        if (panel != null) {
            panel.setVisibility(View.GONE);
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0x2A000000);

        final int size = dp(64);
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        captureX = metrics.widthPixels / 2;
        captureY = metrics.heightPixels / 2;

        captureTarget = new TargetReticleView(this);
        captureTarget.setTapNumber(tapCount() + 1);
        captureTarget.setContentDescription("Drag tap target");
        captureTarget.setOnTouchListener(this);
        FrameLayout.LayoutParams targetParams = new FrameLayout.LayoutParams(size, size);
        targetParams.leftMargin = captureX - (size / 2);
        targetParams.topMargin = captureY - (size / 2);
        root.addView(captureTarget, targetParams);
        root.setOnTouchListener(this);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable ctrlBg = new GradientDrawable();
        ctrlBg.setColor(0xEE0F172A);
        ctrlBg.setCornerRadii(new float[]{dp(16), dp(16), dp(16), dp(16), 0, 0, 0, 0});
        ctrlBg.setStroke(dp(1), 0xFF334155);
        controls.setBackground(ctrlBg);

        captureHint = label("Target Tap #" + (tapCount() + 1) + " · (" + captureX + ", " + captureY + ")");
        captureHint.setTextSize(14);
        captureHint.setTypeface(null, Typeface.BOLD);
        captureHint.setPadding(0, dp(4), 0, dp(6));
        controls.addView(captureHint);

        TextView captureSubHint = new TextView(this);
        captureSubHint.setText("Tap anywhere on screen or drag the crosshair to set target location.");
        captureSubHint.setTextSize(11);
        captureSubHint.setTextColor(0xFF94A3B8);
        captureSubHint.setPadding(0, 0, 0, dp(8));
        controls.addView(captureSubHint);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button add = primaryButton("✓ Add Tap #" + (tapCount() + 1), "addTap");
        row.addView(add, weighted());

        Button cancel = button("✕ Cancel", "cancelCapture");
        LinearLayout.LayoutParams cancelParams = weighted();
        cancelParams.leftMargin = dp(8);
        row.addView(cancel, cancelParams);
        controls.addView(row);

        FrameLayout.LayoutParams controlsParams = new FrameLayout.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        root.addView(controls, controlsParams);

        capture = root;
        windows.addView(capture, params(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT));
    }

    private boolean moveCaptureTarget(View view, MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) view.getLayoutParams();
            captureStartX = params.leftMargin;
            captureStartY = params.topMargin;
            touchStartX = event.getRawX();
            touchStartY = event.getRawY();
            return true;
        }
        if (event.getAction() == MotionEvent.ACTION_MOVE) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) view.getLayoutParams();
            params.leftMargin = captureStartX + (int) (event.getRawX() - touchStartX);
            params.topMargin = captureStartY + (int) (event.getRawY() - touchStartY);
            view.setLayoutParams(params);
            int size = view.getWidth() > 0 ? view.getWidth() : dp(64);
            captureX = params.leftMargin + (size / 2);
            captureY = params.topMargin + (size / 2);
            if (captureHint != null) {
                captureHint.setText("Target Tap #" + (tapCount() + 1) + " · (" + captureX + ", " + captureY + ")");
            }
            return true;
        }
        return true;
    }

    private void addCapturedTap() {
        append("tap " + captureX + " " + captureY);
        hideCapture();
    }

    private void hideCapture() {
        if (windows != null && capture != null) {
            try {
                windows.removeView(capture);
            } catch (IllegalArgumentException ignored) { }
            capture = null;
            captureTarget = null;
            captureHint = null;
        }
        if (panel != null) {
            panel.setVisibility(View.VISIBLE);
        }
    }

    private void openWatcher(int tab) {
        hideOverlay();
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(MainActivity.EXTRA_TAB, tab);
        startActivity(intent);
    }

    private void addDelay() {
        append("wait " + currentDelaySec);
    }

    private void updateDelayDisplay() {
        if (delayValueText != null) {
            delayValueText.setText(currentDelaySec + "s");
        }
    }

    private void minimizeOverlay() {
        hideCapture();
        if (windows != null && panel != null) {
            if (panelParams != null) {
                panelSavedX = panelParams.x;
                panelSavedY = panelParams.y;
            }
            try {
                windows.removeView(panel);
            } catch (IllegalArgumentException ignored) { }
            panel = null;
            panelParams = null;
        }
        showMinimized();
    }

    private void restoreOverlay() {
        hideMinimized();
        showOverlay();
    }

    private void showMinimized() {
        if (windows == null || minimized != null) return;
        Button button = smallButton("DW", "restore");
        minimized = button;
        WindowManager.LayoutParams params = params(dp(64), dp(48));
        params.gravity = Gravity.TOP | Gravity.END;
        params.x = panelSavedX >= 0 ? panelSavedX : dp(8);
        params.y = panelSavedY >= 0 ? panelSavedY : dp(80);
        windows.addView(minimized, params);
    }

    private void hideMinimized() {
        if (windows != null && minimized != null) {
            try {
                windows.removeView(minimized);
            } catch (IllegalArgumentException ignored) { }
            minimized = null;
        }
    }

    private void append(String line) {
        String current = Prefs.actions(this).trim();
        String next = current.length() == 0 ? line : current + "\n" + line;
        Prefs.setActions(this, next + "\n");
        if (recorded != null) recorded.setText(formatActionsTimeline());
        updatePathPoints();
    }

    private void undoLast() {
        String current = Prefs.actions(this).trim();
        if (current.length() == 0) {
            Prefs.setActions(this, "");
            if (recorded != null) recorded.setText(formatActionsTimeline());
            updatePathPoints();
            return;
        }
        String[] lines = current.split("\\r?\\n");
        StringBuilder next = new StringBuilder();
        for (int i = 0; i < lines.length - 1; i++) {
            String line = lines[i].trim();
            if (line.length() > 0) next.append(line).append("\n");
        }
        Prefs.setActions(this, next.toString());
        if (recorded != null) recorded.setText(formatActionsTimeline());
        updatePathPoints();
    }

    private GradientDrawable circle() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(0x77EF4444);
        drawable.setStroke(dp(3), 0xFFFFD166);
        return drawable;
    }

    private int tapCount() {
        String[] lines = Prefs.actions(this).split("\\r?\\n");
        int count = 0;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].trim().startsWith("tap ")) count++;
        }
        return count;
    }

    private CharSequence formatActionsTimeline() {
        String actions = Prefs.actions(this).trim();
        if (actions.length() == 0) {
            return Html.fromHtml("<font color='#64748B'><i>No actions recorded yet.<br>Tap 'Record Tap' or '+ Add Delay'.</i></font>");
        }
        String[] lines = actions.split("\\r?\\n");
        StringBuilder sb = new StringBuilder();
        int maxToShow = Math.max(0, lines.length - 6);
        if (maxToShow > 0) {
            sb.append("<font color='#64748B'>• • • (").append(maxToShow).append(" earlier steps)</font><br>");
        }
        for (int i = maxToShow; i < lines.length; i++) {
            String l = lines[i].trim();
            if (l.length() == 0) continue;
            int currentStep = i + 1;
            if (l.startsWith("tap ")) {
                String[] parts = l.split("\\s+");
                String coord = parts.length >= 3 ? "(" + parts[1] + ", " + parts[2] + ")" : l;
                sb.append("<b><font color='#38BDF8'>[").append(currentStep).append("] TAP</font></b> ")
                  .append("<font color='#F8FAFC'>").append(coord).append("</font><br>");
            } else if (l.startsWith("wait ")) {
                String sec = l.substring(5).trim();
                sb.append("<b><font color='#F59E0B'>[").append(currentStep).append("] DELAY</font></b> ")
                  .append("<font color='#F8FAFC'>").append(sec).append("s</font><br>");
            } else if (l.startsWith("swipe ")) {
                sb.append("<b><font color='#A855F7'>[").append(currentStep).append("] SWIPE</font></b> ")
                  .append("<font color='#F8FAFC'>").append(l.substring(6)).append("</font><br>");
            } else {
                sb.append("<b><font color='#94A3B8'>[").append(currentStep).append("]</font></b> ")
                  .append("<font color='#F8FAFC'>").append(l).append("</font><br>");
            }
        }
        return Html.fromHtml(sb.toString().trim());
    }

    private String shortActions() {
        return Prefs.actions(this).trim();
    }

    private Button button(String text, String tag) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(13);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setMinHeight(dp(42));
        button.setMinimumHeight(dp(42));
        button.setPadding(dp(8), 0, dp(8), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF1E293B);
        bg.setCornerRadius(dp(8));
        bg.setStroke(dp(1), 0xFF334155);
        button.setBackground(bg);
        button.setTag(tag);
        button.setOnClickListener(this);
        return button;
    }

    private Button primaryButton(String text, String tag) {
        Button button = button(text, tag);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF2563EB); // Royal Blue
        bg.setCornerRadius(dp(8));
        button.setBackground(bg);
        return button;
    }

    private Button successButton(String text, String tag) {
        Button button = button(text, tag);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF059669); // Emerald Green
        bg.setCornerRadius(dp(8));
        button.setBackground(bg);
        return button;
    }

    private View space(int h) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, dp(h)));
        return v;
    }

    private Button chipButton(String text, String tag) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(11);
        button.setTextColor(0xFFE2E8F0);
        button.setAllCaps(false);
        button.setMinHeight(dp(30));
        button.setMinimumHeight(dp(30));
        button.setPadding(dp(4), 0, dp(4), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF1E293B);
        bg.setCornerRadius(dp(6));
        bg.setStroke(dp(1), 0xFF334155);
        button.setBackground(bg);
        button.setTag(tag);
        button.setOnClickListener(this);
        return button;
    }

    private Button smallButton(String text, String tag) {
        Button button = button(text, tag);
        button.setTextSize(16);
        button.setMinWidth(dp(40));
        button.setMinimumWidth(dp(40));
        return button;
    }

    private TextView label(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.WHITE);
        view.setTextSize(12);
        view.setPadding(0, dp(3), dp(4), dp(3));
        return view;
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, WindowManager.LayoutParams.WRAP_CONTENT, 1);
    }

    private WindowManager.LayoutParams params(int width, int height) {
        return new WindowManager.LayoutParams(width, height, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void gesture(Path path, int start, int duration, final Done done) {
        if (Build.VERSION.SDK_INT < 24) {
            done.call(false, "Gestures need Android 7+");
            return;
        }
        GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(path, start, duration);
        GestureDescription gesture = new GestureDescription.Builder().addStroke(stroke).build();
        if (!dispatchGesture(gesture, new GestureCallback(done), null)) {
            done.call(false, "Gesture dispatch failed");
        }
    }

    public static final class TapPoint {
        public final int x;
        public final int y;
        public final int index;

        public TapPoint(int x, int y, int index) {
            this.x = x;
            this.y = y;
            this.index = index;
        }
    }

    public static class PathOverlayView extends View {
        private final ArrayList<TapPoint> points = new ArrayList<>();
        private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean showPath = true;

        public PathOverlayView(Context context) {
            super(context);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);

            linePaint.setColor(0xFF22C55E); // Bright emerald/neon green
            linePaint.setStrokeWidth(dp(3.5f));
            linePaint.setStyle(Paint.Style.STROKE);
            linePaint.setStrokeCap(Paint.Cap.ROUND);

            circlePaint.setColor(0xDD0F172A); // Slate fill
            circlePaint.setStyle(Paint.Style.FILL);

            borderPaint.setColor(0xFFFFD166); // Amber / Gold accent
            borderPaint.setStrokeWidth(dp(3f));
            borderPaint.setStyle(Paint.Style.STROKE);

            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(dp(16));
            textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            textPaint.setTextAlign(Paint.Align.CENTER);

            arrowPaint.setColor(0xFF22C55E);
            arrowPaint.setStyle(Paint.Style.FILL);
        }

        private float dp(float v) {
            return v * getResources().getDisplayMetrics().density;
        }

        public void setPoints(ArrayList<TapPoint> newPoints) {
            points.clear();
            if (newPoints != null) {
                points.addAll(newPoints);
            }
            invalidate();
        }

        public void setShowPath(boolean show) {
            this.showPath = show;
            invalidate();
        }

        public boolean isShowPath() {
            return showPath;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (!showPath || points.isEmpty()) return;

            // Draw connecting lines with arrows
            for (int i = 0; i < points.size() - 1; i++) {
                TapPoint p1 = points.get(i);
                TapPoint p2 = points.get(i + 1);
                canvas.drawLine(p1.x, p1.y, p2.x, p2.y, linePaint);

                float midX = (p1.x + p2.x) / 2f;
                float midY = (p1.y + p2.y) / 2f;
                float angle = (float) Math.toDegrees(Math.atan2(p2.y - p1.y, p2.x - p1.x));
                drawArrow(canvas, midX, midY, angle);
            }

            // Draw precision targeting reticle at each tap point
            for (int i = 0; i < points.size(); i++) {
                TapPoint p = points.get(i);
                float px = p.x;
                float py = p.y;

                // Outer circle ring
                canvas.drawCircle(px, py, dp(14f), borderPaint);

                // Crosshairs
                float gap = dp(3.5f);
                float ext = dp(18f);
                canvas.drawLine(px - ext, py, px - gap, py, linePaint);
                canvas.drawLine(px + gap, py, px + ext, py, linePaint);
                canvas.drawLine(px, py - ext, px, py - gap, linePaint);
                canvas.drawLine(px, py + gap, px, py + ext, linePaint);

                // Laser center dot
                canvas.drawCircle(px, py, dp(3f), arrowPaint);

                // Offset numbered badge
                float badgeX = px + dp(16f);
                float badgeY = py - dp(16f);
                float badgeR = dp(10f);
                canvas.drawCircle(badgeX, badgeY, badgeR, circlePaint);
                canvas.drawCircle(badgeX, badgeY, badgeR, borderPaint);

                String text = String.valueOf(p.index);
                Paint.FontMetrics fm = textPaint.getFontMetrics();
                float textY = badgeY - (fm.ascent + fm.descent) / 2f;
                canvas.drawText(text, badgeX, textY, textPaint);
            }
        }

        private void drawArrow(Canvas canvas, float x, float y, float angle) {
            float size = dp(10);
            canvas.save();
            canvas.translate(x, y);
            canvas.rotate(angle);
            Path arrow = new Path();
            arrow.moveTo(size, 0);
            arrow.lineTo(-size, -size * 0.7f);
            arrow.lineTo(-size * 0.4f, 0);
            arrow.lineTo(-size, size * 0.7f);
            arrow.close();
            canvas.drawPath(arrow, arrowPaint);
            canvas.restore();
        }
    }

    public static class TargetReticleView extends View {
        private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint crossPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint badgeBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint badgeBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int tapNumber = 1;

        public TargetReticleView(Context context) {
            super(context);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            ringPaint.setColor(0xFF22C55E);
            ringPaint.setStyle(Paint.Style.STROKE);
            ringPaint.setStrokeWidth(dp(2f));

            crossPaint.setColor(0xFF22C55E);
            crossPaint.setStyle(Paint.Style.STROKE);
            crossPaint.setStrokeWidth(dp(1.5f));

            dotPaint.setColor(0xFFEF4444);
            dotPaint.setStyle(Paint.Style.FILL);

            badgeBgPaint.setColor(0xEE0F172A);
            badgeBgPaint.setStyle(Paint.Style.FILL);

            badgeBorderPaint.setColor(0xFFFFD166);
            badgeBorderPaint.setStyle(Paint.Style.STROKE);
            badgeBorderPaint.setStrokeWidth(dp(1.5f));

            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(dp(11));
            textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            textPaint.setTextAlign(Paint.Align.CENTER);
        }

        private float dp(float v) {
            return v * getResources().getDisplayMetrics().density;
        }

        public void setTapNumber(int number) {
            this.tapNumber = number;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float r = dp(16f);

            // Outer ring
            canvas.drawCircle(cx, cy, r, ringPaint);

            // Crosshairs
            float gap = dp(4f);
            float ext = dp(24f);
            canvas.drawLine(cx - ext, cy, cx - gap, cy, crossPaint);
            canvas.drawLine(cx + gap, cy, cx + ext, cy, crossPaint);
            canvas.drawLine(cx, cy - ext, cx, cy - gap, crossPaint);
            canvas.drawLine(cx, cy + gap, cx, cy + ext, crossPaint);

            // Center laser dot
            canvas.drawCircle(cx, cy, dp(3f), dotPaint);

            // Floating badge offset
            float badgeX = cx + dp(18f);
            float badgeY = cy - dp(18f);
            float badgeR = dp(10f);
            canvas.drawCircle(badgeX, badgeY, badgeR, badgeBgPaint);
            canvas.drawCircle(badgeX, badgeY, badgeR, badgeBorderPaint);

            Paint.FontMetrics fm = textPaint.getFontMetrics();
            float textY = badgeY - (fm.ascent + fm.descent) / 2f;
            canvas.drawText(String.valueOf(tapNumber), badgeX, textY, textPaint);
        }
    }

    private static final class GestureCallback extends GestureResultCallback {
        private final Done done;

        GestureCallback(Done done) {
            this.done = done;
        }

        @Override
        public void onCompleted(GestureDescription gestureDescription) {
            done.call(true, "OK");
        }

        @Override
        public void onCancelled(GestureDescription gestureDescription) {
            done.call(false, "Gesture cancelled");
        }
    }

    public interface ScreenshotDone {
        void onFinished();
    }

    /** Receives the raw screen bitmap, or null if the capture failed. */
    public interface BitmapReady {
        void onBitmap(Bitmap bmp);
    }

    /**
     * The single screenshot implementation. Hands back the raw bitmap and lets callers
     * own presentation - the run screenshot wants full resolution, the live view wants
     * a cropped, grid-annotated, downscaled frame.
     *
     * The platform rate-limits AccessibilityService.takeScreenshot to roughly one call
     * per second, so anything refreshing on a timer must stay above that.
     */
    public void captureBitmap(BitmapReady cb) {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), new BitmapCallbackHandler(cb));
                return;
            } catch (Throwable t) {
                android.util.Log.e("WatcherService", "takeScreenshot call failed", t);
            }
        }
        if (cb != null) cb.onBitmap(null);
    }

    public static byte[] compressJpeg(Bitmap bmp, int quality) {
        if (bmp == null) return null;
        try {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.JPEG, quality, stream);
            return stream.toByteArray();
        } catch (Throwable t) {
            return null;
        }
    }

    private static final class BitmapCallbackHandler implements AccessibilityService.TakeScreenshotCallback {
        private final BitmapReady cb;

        BitmapCallbackHandler(BitmapReady cb) {
            this.cb = cb;
        }

        @Override
        public void onSuccess(AccessibilityService.ScreenshotResult result) {
            Bitmap out = null;
            try {
                if (Build.VERSION.SDK_INT >= 30 && result != null) {
                    Bitmap hw = Bitmap.wrapHardwareBuffer(result.getHardwareBuffer(), result.getColorSpace());
                    if (hw != null) {
                        out = hw.copy(Bitmap.Config.ARGB_8888, false);
                        hw.recycle();
                        result.getHardwareBuffer().close();
                    }
                }
            } catch (Throwable t) {
                android.util.Log.e("WatcherService", "Screenshot processing error", t);
            }
            if (cb != null) cb.onBitmap(out);
        }

        @Override
        public void onFailure(int errorCode) {
            android.util.Log.e("WatcherService", "Screenshot failed: code " + errorCode);
            if (cb != null) cb.onBitmap(null);
        }
    }

    /** Sends the end-of-run screenshot at full resolution, then reports completion. */
    private static final class SendRunScreenshot implements BitmapReady {
        private final Context context;
        private final ScreenshotDone done;

        SendRunScreenshot(Context context, ScreenshotDone done) {
            this.context = context;
            this.done = done;
        }

        @Override
        public void onBitmap(Bitmap bmp) {
            byte[] jpeg = compressJpeg(bmp, 85);
            if (bmp != null) bmp.recycle();
            if (jpeg != null) {
                TelegramNotifier.sendPhoto(context, jpeg,
                        "✅ Darwin Watcher: Task finished on " + Prefs.targetLabel(context) + "!", null);
            } else {
                TelegramNotifier.sendDone(context);
            }
            if (done != null) done.onFinished();
        }
    }

    public void captureScreenshotAndSend(final Context context, final ScreenshotDone done) {
        captureBitmap(new SendRunScreenshot(context, done));
    }
}
