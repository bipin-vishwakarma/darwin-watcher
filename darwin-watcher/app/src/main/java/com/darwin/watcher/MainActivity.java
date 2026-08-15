package com.darwin.watcher;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.app.TimePickerDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import java.util.ArrayList;

public class MainActivity extends Activity implements View.OnClickListener, AdapterView.OnItemSelectedListener, DialogInterface.OnClickListener, TelegramNotifier.Callback {
    public static final String EXTRA_TAB = "tab";
    public static final String EXTRA_RUN_NOW = "runNow";

    public static final int TAB_HOME = 0;
    public static final int TAB_SCHEDULES = 1;
    public static final int TAB_PROFILES = 2;
    public static final int TAB_SETTINGS = 3;

    private static boolean bootShown = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private View splash;
    private TextView splashDiagnostic;
    private LinearLayout content;
    private TextView status;
    private Button homeTab;
    private Button schedulesTab;
    private Button profilesTab;
    private Button settingsTab;
    private Button runButton;
    private Button configure;
    private Button testTelegramButton;
    private Button saveSettingsButton;
    private Button saveProfileButton;
    private Button testAlarmButton;
    private EditText profile;
    private Spinner profiles;
    private boolean bindingProfiles;
    private String profilesCache;
    private CheckBox telegramEnabled;
    private CheckBox telegramRemoteEnabled;
    private EditText telegramToken;
    private EditText telegramChat;
    private ArrayList<AppItem> appChoices;
    private int currentTab = TAB_HOME;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ensureTelegramRemoteService();
        boolean isRunNow = getIntent() != null && getIntent().getBooleanExtra(EXTRA_RUN_NOW, false);
        handleRunIntent(getIntent());
        currentTab = getIntent() != null ? getIntent().getIntExtra(EXTRA_TAB, TAB_HOME) : TAB_HOME;

        if (!bootShown && !isRunNow) {
            bootShown = true;
            showBootSplash();
        } else {
            showMain();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && intent.hasExtra(EXTRA_TAB)) {
            currentTab = intent.getIntExtra(EXTRA_TAB, TAB_HOME);
            renderTab();
        }
        handleRunIntent(intent);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (splash != null) splash.animate().cancel();
        super.onDestroy();
    }

    private void handleRunIntent(Intent intent) {
        if (intent == null || !intent.getBooleanExtra(EXTRA_RUN_NOW, false)) return;
        if (!Prefs.consumePendingScheduledRun(this)) return;
        wakeForScheduledRun();
        handler.postDelayed(new RunAutomation(this), 1600);
    }

    private void wakeForScheduledRun() {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null) {
                PowerManager.WakeLock wl = pm.newWakeLock(
                    PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE,
                    "DarwinWatcher:MainActivityWake"
                );
                wl.acquire(10000);
            }
        } catch (Exception ignored) { }

        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else if (window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        }
        if (window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        if (Build.VERSION.SDK_INT >= 26) {
            KeyguardManager keyguard = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
            if (keyguard != null) keyguard.requestDismissKeyguard(this, null);
        }
    }

    private void ensureTelegramRemoteService() {
        if (Prefs.telegramRemoteEnabled(this) && Prefs.hasTelegramCredentials(this)) {
            TelegramRemoteService.start(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        ensureTelegramRemoteService();
        if (splash == null) {
            renderTab();
        }
    }

    // ==========================================
    // 1. BOOT SEQUENCE & DEVELOPER CREDITS
    // ==========================================

    private void showBootSplash() {
        LinearLayout root = column();
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(28), dp(28), dp(28), dp(28));
        root.setBackgroundColor(0xFF090D16);

        TextView logoBadge = new TextView(this);
        logoBadge.setText("DW");
        logoBadge.setTextSize(26);
        logoBadge.setTextColor(0xFFFFFFFF);
        logoBadge.setTypeface(null, Typeface.BOLD);
        logoBadge.setGravity(Gravity.CENTER);
        GradientDrawable logoBg = new GradientDrawable();
        logoBg.setColor(0xFF2563EB);
        logoBg.setCornerRadius(dp(18));
        logoBadge.setBackground(logoBg);
        root.addView(logoBadge, margins(dp(68), dp(68), 0, 0, 0, 16));

        TextView title = text("DARWIN WATCHER", 24, 0xFFFFFFFF);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, Typeface.BOLD);
        root.addView(title, margins(-1, -2, 0, 0, 0, 4));

        TextView subtitle = text("Autonomous Navigation & Workflow Engine", 13, 0xFF94A3B8);
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle, margins(-1, -2, 0, 0, 0, 24));

        // Diagnostics Step Box
        LinearLayout diagBox = new LinearLayout(this);
        diagBox.setOrientation(LinearLayout.VERTICAL);
        diagBox.setGravity(Gravity.CENTER);
        diagBox.setPadding(dp(20), dp(12), dp(20), dp(12));
        GradientDrawable diagBg = new GradientDrawable();
        diagBg.setColor(0xFF131D31);
        diagBg.setCornerRadius(dp(12));
        diagBg.setStroke(dp(1), 0xFF1E293B);
        diagBox.setBackground(diagBg);

        splashDiagnostic = text("● Starting Automation Core...", 12, 0xFF38BDF8);
        splashDiagnostic.setTypeface(null, Typeface.BOLD);
        splashDiagnostic.setGravity(Gravity.CENTER);
        diagBox.addView(splashDiagnostic);
        root.addView(diagBox, margins(-2, -2, 0, 0, 0, 36));

        // Developer Credit
        TextView devCredit = text("Developed by Bipin Vishwakarma", 12, 0xFF64748B);
        devCredit.setGravity(Gravity.CENTER);
        root.addView(devCredit, margins(-1, -2, 0, 0, 0, 0));

        splash = root;
        setContentView(root);

        handler.postDelayed(new BootStep(this, 1), 400);
        handler.postDelayed(new BootStep(this, 2), 850);
        handler.postDelayed(new BootStep(this, 3), 1300);
        handler.postDelayed(new BootStep(this, 4), 1650);
    }

    private void updateBootStep(int step) {
        if (splashDiagnostic == null) return;
        if (step == 1) {
            splashDiagnostic.setText("● Verifying System Permissions & Shield...");
            splashDiagnostic.setTextColor(0xFF38BDF8);
        } else if (step == 2) {
            splashDiagnostic.setText("● Checking Automation & Telegram Bridge...");
            splashDiagnostic.setTextColor(0xFF38BDF8);
        } else if (step == 3) {
            splashDiagnostic.setText("✓ All Systems Operational");
            splashDiagnostic.setTextColor(0xFF22C55E);
        } else if (step == 4) {
            if (splash != null) {
                splash.animate().alpha(0f).setDuration(250).withEndAction(new ShowMain(this)).start();
            } else {
                showMain();
            }
        }
    }

    // ==========================================
    // 2. MAIN APPLICATION SHELL & TABS
    // ==========================================

    private void showMain() {
        LinearLayout mainContainer = column();
        mainContainer.setBackgroundColor(0xFFF8FAFC);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout root = column();
        root.setPadding(dp(16), dp(16), dp(16), dp(16));
        root.setBackgroundColor(0xFFF8FAFC);
        scroll.addView(root);

        // Header Title
        TextView title = text("Darwin Watcher", 26, 0xFF0F172A);
        title.setTypeface(null, Typeface.BOLD);
        root.addView(title);

        TextView sub = text("Personal Navigation & Screen Automation Engine", 13, 0xFF64748B);
        root.addView(sub, margins(-1, -2, 0, 2, 0, 14));

        // Navigation Tabs Bar
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        homeTab = tabButton("Dashboard", "tabHome");
        schedulesTab = tabButton("Schedules", "tabSchedules");
        profilesTab = tabButton("Profiles", "tabProfiles");
        settingsTab = tabButton("Settings", "tabSettings");

        tabs.addView(homeTab, weightParams());
        tabs.addView(schedulesTab, weightParams());
        tabs.addView(profilesTab, weightParams());
        tabs.addView(settingsTab, weightParams());
        root.addView(tabs, margins(-1, dp(44), 0, 0, 0, 16));

        content = column();
        root.addView(content);

        // Scrollable content area takes all remaining vertical space
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f
        );
        mainContainer.addView(scroll, scrollParams);

        // Sticky Fixed Footer Bar Pinned at Bottom
        LinearLayout footerBar = new LinearLayout(this);
        footerBar.setOrientation(LinearLayout.VERTICAL);
        footerBar.setGravity(Gravity.CENTER);
        footerBar.setBackgroundColor(0xFFFFFFFF);
        footerBar.setPadding(0, dp(10), 0, dp(12));

        View divider = new View(this);
        divider.setBackgroundColor(0xFFE2E8F0);
        footerBar.addView(divider, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));

        TextView stickyFooter = text("Developed by Bipin Vishwakarma", 12, 0xFF64748B);
        stickyFooter.setTypeface(null, Typeface.BOLD);
        stickyFooter.setGravity(Gravity.CENTER);
        footerBar.addView(stickyFooter, margins(-1, -2, 0, 8, 0, 0));

        mainContainer.addView(footerBar, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        setContentView(mainContainer);
        renderTab();
    }

    private void renderTab() {
        if (content == null) return;
        clearVisibleFields();
        content.removeAllViews();
        updateTabs();

        if (currentTab == TAB_SCHEDULES) {
            renderSchedulesTab();
        } else if (currentTab == TAB_PROFILES) {
            renderProfilesTab();
        } else if (currentTab == TAB_SETTINGS) {
            renderSettingsTab();
        } else {
            renderDashboardTab();
        }
        refresh();
    }

    // ==========================================
    // 3. DASHBOARD TAB & PERMISSION SHIELD
    // ==========================================

    private void renderDashboardTab() {
        // Permission Shield Diagnostic Card
        renderPermissionShield();

        // Target App Hero Card
        LinearLayout appCard = card();
        TextView appHeader = section("Target Application");
        appCard.addView(appHeader);

        TextView appDetails = text("App: " + Prefs.targetLabel(this) + "\nPackage: " + Prefs.targetPackage(this), 13, 0xFF475569);
        appCard.addView(appDetails, margins(-1, -2, 0, 6, 0, 10));

        Button switchApp = secondaryButton("Switch Target Application");
        switchApp.setTag("chooseApp");
        switchApp.setOnClickListener(this);
        appCard.addView(switchApp);
        content.addView(appCard, margins(-1, -2, 0, 0, 0, 14));

        // Quick Actions Card
        LinearLayout actionsCard = card();
        TextView actHeader = section("Automation Control");
        actionsCard.addView(actHeader, margins(-1, -2, 0, 0, 0, 10));

        runButton = primaryButton("Run Automation Now");
        runButton.setTag("run");
        runButton.setOnClickListener(this);
        actionsCard.addView(runButton, margins(-1, dp(50), 0, 0, 0, 10));

        configure = secondaryButton("Configure Tap Path Overlay");
        configure.setTag("configure");
        configure.setOnClickListener(this);
        actionsCard.addView(configure);
        content.addView(actionsCard, margins(-1, -2, 0, 0, 0, 14));

        // Live Diagnostic Log
        status = text("", 12, 0xFF334155);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        status.setPadding(dp(14), dp(14), dp(14), dp(14));
        GradientDrawable statusBg = new GradientDrawable();
        statusBg.setColor(0xFFFFFFFF);
        statusBg.setCornerRadius(dp(12));
        statusBg.setStroke(dp(1), 0xFFE2E8F0);
        status.setBackground(statusBg);
        content.addView(status, margins(-1, -2, 0, 0, 0, 16));
    }

    private void renderPermissionShield() {
        boolean pAccess = WatcherAccessibilityService.isRunning();
        boolean pOverlay = Settings.canDrawOverlays(this);
        boolean pNotif = Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        boolean pAlarms = true;
        if (Build.VERSION.SDK_INT >= 31) {
            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            if (am != null) pAlarms = am.canScheduleExactAlarms();
        }
        boolean pBattery = true;
        if (Build.VERSION.SDK_INT >= 23) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null) pBattery = pm.isIgnoringBatteryOptimizations(getPackageName());
        }

        int grantedCount = (pAccess ? 1 : 0) + (pOverlay ? 1 : 0) + (pNotif ? 1 : 0) + (pAlarms ? 1 : 0) + (pBattery ? 1 : 0);
        boolean allGranted = grantedCount == 5;

        LinearLayout shieldCard = new LinearLayout(this);
        shieldCard.setOrientation(LinearLayout.VERTICAL);
        shieldCard.setPadding(dp(14), dp(14), dp(14), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(12));

        if (allGranted) {
            bg.setColor(0xFFF0FDF4);
            bg.setStroke(dp(1), 0xFF86EFAC);
            shieldCard.setBackground(bg);

            TextView shieldTitle = text("✓ System Shield Active · All Permissions Granted", 14, 0xFF166534);
            shieldTitle.setTypeface(null, Typeface.BOLD);
            shieldCard.addView(shieldTitle);

            TextView shieldSub = text("Accessibility, Overlay, Notifications & Alarm engines are 100% active.", 12, 0xFF15803D);
            shieldCard.addView(shieldSub, margins(-1, -2, 0, 4, 0, 0));
        } else {
            bg.setColor(0xFFFFFBEB);
            bg.setStroke(dp(1), 0xFFFCD34D);
            shieldCard.setBackground(bg);

            TextView shieldTitle = text("⚠️ System Permissions Required (" + grantedCount + "/5 Granted)", 14, 0xFF92400E);
            shieldTitle.setTypeface(null, Typeface.BOLD);
            shieldCard.addView(shieldTitle);

            TextView shieldSub = text("Grant the missing permissions below to unlock automated taps & overlays:", 12, 0xFFB45309);
            shieldCard.addView(shieldSub, margins(-1, -2, 0, 4, 0, 10));

            if (!pAccess) addPermissionGrantRow(shieldCard, "Accessibility Engine", "Required for automated taps", "grantAccess");
            if (!pOverlay) addPermissionGrantRow(shieldCard, "Display Over Other Apps", "Required for tap recorder", "grantOverlay");
            if (!pNotif) addPermissionGrantRow(shieldCard, "System Notifications", "Required for status alerts", "grantNotif");
            if (!pAlarms) addPermissionGrantRow(shieldCard, "Exact Alarms", "Required for scheduled runs", "grantAlarm");
            if (!pBattery) addPermissionGrantRow(shieldCard, "Battery Unrestricted", "Prevents background killing", "grantBattery");
        }

        content.addView(shieldCard, margins(-1, -2, 0, 0, 0, 14));
    }

    private void addPermissionGrantRow(LinearLayout parent, String title, String desc, String tag) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));

        LinearLayout textCol = column();
        TextView t = text(title, 13, 0xFF78350F);
        t.setTypeface(null, Typeface.BOLD);
        textCol.addView(t);
        TextView d = text(desc, 11, 0xFF92400E);
        textCol.addView(d);
        row.addView(textCol, weightParams());

        Button grant = new Button(this);
        grant.setText("Grant");
        grant.setTextColor(0xFFFFFFFF);
        grant.setTextSize(12);
        grant.setTypeface(null, Typeface.BOLD);
        grant.setAllCaps(false);
        grant.setTag(tag);
        grant.setOnClickListener(this);
        GradientDrawable gbg = new GradientDrawable();
        gbg.setColor(0xFFD97706);
        gbg.setCornerRadius(dp(6));
        grant.setBackground(gbg);
        grant.setPadding(dp(12), dp(4), dp(12), dp(4));
        row.addView(grant, margins(-2, dp(36), 0, 0, 0, 0));

        parent.addView(row);
    }

    // ==========================================
    // 4. SCHEDULES TAB (MULTI-SCHEDULE + RANDOM JITTER)
    // ==========================================

    private void renderSchedulesTab() {
        LinearLayout headerCard = card();
        TextView secTitle = section("Automated Schedules");
        headerCard.addView(secTitle);
        TextView sub = text("Configure automated schedules with custom day dots, 12-hour AM/PM times, app & profile selection, and natural jitter windows.", 13, 0xFF64748B);
        headerCard.addView(sub, margins(-1, -2, 0, 4, 0, 12));

        Button addSched = primaryButton("+ Add New Schedule");
        addSched.setTag("addSchedule");
        addSched.setOnClickListener(this);
        headerCard.addView(addSched);
        content.addView(headerCard, margins(-1, -2, 0, 0, 0, 14));

        ArrayList<Prefs.ScheduleItem> list = Prefs.getSchedules(this);
        if (list.isEmpty()) {
            TextView empty = text("No schedules configured yet. Tap above to add one.", 13, 0xFF64748B);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(20), dp(30), dp(20), dp(30));
            content.addView(empty);
        } else {
            for (int i = 0; i < list.size(); i++) {
                Prefs.ScheduleItem item = list.get(i);
                renderScheduleCard(item);
            }
        }
    }

    private LinearLayout createDayDotsRow(final Prefs.ScheduleItem item, final boolean interactive) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        String[] labels = {"M", "T", "W", "T", "F", "S", "S"};
        for (int i = 0; i < 7; i++) {
            final int dayIndex = i;
            boolean active = item.isDayIndexActive(dayIndex);

            Button dot = new Button(this);
            dot.setText(labels[dayIndex]);
            dot.setTextSize(11);
            dot.setTypeface(null, Typeface.BOLD);
            dot.setPadding(0, 0, 0, 0);

            GradientDrawable dotBg = new GradientDrawable();
            dotBg.setShape(GradientDrawable.OVAL);
            if (active) {
                dotBg.setColor(0xFF2563EB);
                dot.setTextColor(0xFFFFFFFF);
            } else {
                dotBg.setColor(0xFFF1F5F9);
                dotBg.setStroke(dp(1), 0xFFCBD5E1);
                dot.setTextColor(0xFF64748B);
            }
            dot.setBackground(dotBg);

            if (interactive) {
                dot.setTag("toggleDay_" + item.id + "_" + dayIndex);
                dot.setOnClickListener(this);
            }

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(34), dp(34));
            lp.setMargins(0, 0, dp(6), 0);
            row.addView(dot, lp);
        }
        return row;
    }

    private void renderScheduleCard(final Prefs.ScheduleItem item) {
        LinearLayout c = card();

        // Top Row: Name & Enabled Switch
        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView nameText = text(item.name, 16, 0xFF0F172A);
        nameText.setTypeface(null, Typeface.BOLD);
        topRow.addView(nameText, weightParams());

        Button toggle = new Button(this);
        toggle.setText(item.enabled ? "Active" : "Disabled");
        toggle.setTextColor(item.enabled ? 0xFFFFFFFF : 0xFF64748B);
        toggle.setTextSize(11);
        toggle.setTypeface(null, Typeface.BOLD);
        toggle.setAllCaps(false);
        GradientDrawable tbg = new GradientDrawable();
        tbg.setColor(item.enabled ? 0xFF16A34A : 0xFFE2E8F0);
        tbg.setCornerRadius(dp(8));
        toggle.setBackground(tbg);
        toggle.setPadding(dp(12), dp(4), dp(12), dp(4));
        toggle.setTag("toggleSched_" + item.id);
        toggle.setOnClickListener(this);
        topRow.addView(toggle, margins(-2, dp(32), 0, 0, 0, 0));

        c.addView(topRow, margins(-1, -2, 0, 0, 0, 8));

        // Time Row: 12-Hour Big Time + AM/PM Pill + Tolerance Badge
        LinearLayout timeRow = new LinearLayout(this);
        timeRow.setOrientation(LinearLayout.HORIZONTAL);
        timeRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView timeText = new TextView(this);
        timeText.setText(item.get12HourTime());
        timeText.setTextSize(26);
        timeText.setTextColor(0xFF0F172A);
        timeText.setTypeface(null, Typeface.BOLD);
        timeRow.addView(timeText);

        TextView amPmPill = text(item.getAmPm(), 12, 0xFF2563EB);
        amPmPill.setTypeface(null, Typeface.BOLD);
        amPmPill.setPadding(dp(8), dp(3), dp(8), dp(3));
        GradientDrawable apBg = new GradientDrawable();
        apBg.setColor(0xFFEFF6FF);
        apBg.setCornerRadius(dp(6));
        amPmPill.setBackground(apBg);
        timeRow.addView(amPmPill, margins(-2, -2, 6, 0, 8, 0));

        TextView tolBadge = text("🎲 " + item.toleranceText(), 11, item.randomWindowMinutes > 0 ? 0xFF1D4ED8 : 0xFF475569);
        tolBadge.setPadding(dp(8), dp(4), dp(8), dp(4));
        GradientDrawable tbg2 = new GradientDrawable();
        tbg2.setColor(item.randomWindowMinutes > 0 ? 0xFFEFF6FF : 0xFFF1F5F9);
        tbg2.setCornerRadius(dp(6));
        tolBadge.setBackground(tbg2);
        timeRow.addView(tolBadge);

        c.addView(timeRow, margins(-1, -2, 0, 0, 0, 10));

        // 7-Day Dots Row (Interactive!)
        c.addView(createDayDotsRow(item, true), margins(-1, -2, 0, 0, 0, 8));

        // Target App & Profile Info + Window Preview
        String appName = item.targetLabel.length() > 0 ? item.targetLabel : Prefs.targetLabel(this);
        String profileName = item.profile.length() > 0 ? item.profile : Prefs.currentProfile(this);
        String windowText = "Window: " + item.windowPreview() + "\nApp: " + appName + "  ·  Profile: " + profileName;
        TextView summary = text(windowText, 12, 0xFF64748B);
        c.addView(summary, margins(-1, -2, 0, 0, 0, 10));

        // Action Buttons Row (Customize Modal, Tolerance Stepper, Delete)
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);

        Button customizeBtn = chipButton("⚙️ Customize", "editSched_" + item.id, true);
        btnRow.addView(customizeBtn, weightMargins(1.4f, 0, 0, 4, 0));

        Button tolBtn = chipButton("±" + (item.randomWindowMinutes > 0 ? item.randomWindowMinutes + "m" : "0m") + " ▾", "cycleTolerance_" + item.id, false);
        btnRow.addView(tolBtn, weightMargins(1.0f, 0, 0, 4, 0));

        Button deleteBtn = chipButton("Delete", "deleteSched_" + item.id, false);
        deleteBtn.setTextColor(0xFFDC2626);
        btnRow.addView(deleteBtn, weightMargins(1.0f, 0, 0, 0, 0));

        c.addView(btnRow);
        content.addView(c, margins(-1, -2, 0, 0, 0, 12));
    }

    public void showScheduleConfigDialog(final Prefs.ScheduleItem item, final boolean isNew) {
        ScrollView sv = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(16), dp(20), dp(16));
        sv.addView(layout);

        // Schedule Name
        TextView nameLabel = text("Schedule Name", 12, 0xFF475569);
        nameLabel.setTypeface(null, Typeface.BOLD);
        layout.addView(nameLabel, margins(-1, -2, 0, 0, 0, 4));

        final EditText nameInput = new EditText(this);
        nameInput.setText(item.name);
        nameInput.setSingleLine(true);
        nameInput.setHint("e.g. Morning Check-In");
        layout.addView(nameInput, margins(-1, dp(44), 0, 0, 0, 14));

        // Time Picker Button (12-Hour AM/PM)
        TextView timeLabel = text("Execution Time (12-Hour AM/PM)", 12, 0xFF475569);
        timeLabel.setTypeface(null, Typeface.BOLD);
        layout.addView(timeLabel, margins(-1, -2, 0, 0, 0, 4));

        final Button timeBtn = new Button(this);
        timeBtn.setText("⏰  " + item.get12HourTime() + " " + item.getAmPm());
        timeBtn.setTextSize(16);
        timeBtn.setTypeface(null, Typeface.BOLD);
        timeBtn.setTextColor(0xFF2563EB);
        GradientDrawable timeBtnBg = new GradientDrawable();
        timeBtnBg.setColor(0xFFEFF6FF);
        timeBtnBg.setCornerRadius(dp(8));
        timeBtnBg.setStroke(dp(1), 0xFFBFDBFE);
        timeBtn.setBackground(timeBtnBg);
        timeBtn.setPadding(dp(12), dp(10), dp(12), dp(10));
        layout.addView(timeBtn, margins(-1, -2, 0, 0, 0, 14));

        // Active Days Section
        TextView daysLabel = text("Active Days of Week", 12, 0xFF475569);
        daysLabel.setTypeface(null, Typeface.BOLD);
        layout.addView(daysLabel, margins(-1, -2, 0, 0, 0, 4));

        final LinearLayout dotsContainer = new LinearLayout(this);
        dotsContainer.setOrientation(LinearLayout.HORIZONTAL);
        dotsContainer.setGravity(Gravity.CENTER_VERTICAL);
        layout.addView(dotsContainer, margins(-1, -2, 0, 0, 0, 8));

        final Button[] dayDots = new Button[7];
        final String[] dayLetters = {"M", "T", "W", "T", "F", "S", "S"};
        for (int i = 0; i < 7; i++) {
            Button dot = new Button(this);
            dot.setText(dayLetters[i]);
            dot.setTextSize(11);
            dot.setTypeface(null, Typeface.BOLD);
            dot.setPadding(0, 0, 0, 0);
            dayDots[i] = dot;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(34), dp(34));
            lp.setMargins(0, 0, dp(6), 0);
            dotsContainer.addView(dot, lp);
        }
        updateDialogDayDots(item, dayDots);

        // Day Presets Row
        LinearLayout presetsRow = new LinearLayout(this);
        presetsRow.setOrientation(LinearLayout.HORIZONTAL);
        Button presetWk = chipButton("Weekdays", "presetWk", false);
        Button presetAll = chipButton("All Days", "presetAll", false);
        Button presetWknd = chipButton("Weekends", "presetWknd", false);
        presetsRow.addView(presetWk, weightMargins(1, 0, 0, 4, 0));
        presetsRow.addView(presetAll, weightMargins(1, 0, 0, 4, 0));
        presetsRow.addView(presetWknd, weightMargins(1, 0, 0, 0, 0));
        layout.addView(presetsRow, margins(-1, -2, 0, 0, 0, 14));

        // Tolerance Stepper Section
        TextView tolLabel = text("Natural Time Tolerance Window", 12, 0xFF475569);
        tolLabel.setTypeface(null, Typeface.BOLD);
        layout.addView(tolLabel, margins(-1, -2, 0, 0, 0, 4));

        LinearLayout tolRow = new LinearLayout(this);
        tolRow.setOrientation(LinearLayout.HORIZONTAL);
        final Button[] tolBtns = new Button[6];
        final int[] tolVals = {0, 2, 5, 10, 15, 30};
        final String[] tolTitles = {"Exact", "±2m", "±5m", "±10m", "±15m", "±30m"};
        for (int i = 0; i < 6; i++) {
            tolBtns[i] = chipButton(tolTitles[i], "tolVal_" + tolVals[i], item.randomWindowMinutes == tolVals[i]);
            tolRow.addView(tolBtns[i], weightMargins(1, 0, 0, i < 5 ? 4 : 0, 0));
        }
        layout.addView(tolRow, margins(-1, -2, 0, 0, 0, 14));

        // Target Application Dropdown
        TextView appLabel = text("Target Application", 12, 0xFF475569);
        appLabel.setTypeface(null, Typeface.BOLD);
        layout.addView(appLabel, margins(-1, -2, 0, 0, 0, 4));

        final Spinner appSpinner = new Spinner(this);
        final ArrayList<String> appLabels = new ArrayList<String>();
        final ArrayList<String> appPackages = new ArrayList<String>();

        appLabels.add("Calculator (com.miui.calculator)");
        appPackages.add("com.miui.calculator");

        appLabels.add("Darwinbox (com.darwinbox.darwinbox)");
        appPackages.add("com.darwinbox.darwinbox");

        String currPkg = Prefs.targetPackage(this);
        String currLbl = Prefs.targetLabel(this);
        if (!"com.miui.calculator".equals(currPkg) && !"com.darwinbox.darwinbox".equals(currPkg) && currPkg.length() > 0) {
            appLabels.add(currLbl + " (" + currPkg + ")");
            appPackages.add(currPkg);
        }

        ArrayAdapter<String> appAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, appLabels);
        appAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        appSpinner.setAdapter(appAdapter);

        int selectedAppIdx = 0;
        String matchPkg = item.targetPackage.length() > 0 ? item.targetPackage : currPkg;
        for (int i = 0; i < appPackages.size(); i++) {
            if (appPackages.get(i).equals(matchPkg)) {
                selectedAppIdx = i;
                break;
            }
        }
        appSpinner.setSelection(selectedAppIdx);
        layout.addView(appSpinner, margins(-1, dp(44), 0, 0, 0, 14));

        // Profile Dropdown
        TextView profLabel = text("Script Profile", 12, 0xFF475569);
        profLabel.setTypeface(null, Typeface.BOLD);
        layout.addView(profLabel, margins(-1, -2, 0, 0, 0, 4));

        final Spinner profSpinner = new Spinner(this);
        final String[] pNames = profileNames();
        ArrayAdapter<String> profAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, pNames);
        profAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        profSpinner.setAdapter(profAdapter);

        int selectedProfIdx = 0;
        String matchProf = item.profile.length() > 0 ? item.profile : Prefs.currentProfile(this);
        for (int i = 0; i < pNames.length; i++) {
            if (pNames[i].equals(matchProf)) {
                selectedProfIdx = i;
                break;
            }
        }
        profSpinner.setSelection(selectedProfIdx);
        layout.addView(profSpinner, margins(-1, dp(44), 0, 0, 0, 14));

        // Build AlertDialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(isNew ? "Create Schedule" : "Customize Schedule");
        builder.setView(sv);
        builder.setPositiveButton("Save", new SaveScheduleClickListener(this, item, nameInput, appSpinner, appPackages, profSpinner, pNames));
        builder.setNegativeButton("Cancel", null);

        final AlertDialog dialog = builder.create();

        // Wire dialog listeners
        timeBtn.setOnClickListener(new DialogTimePickerClickListener(this, item, timeBtn));

        for (int i = 0; i < 7; i++) {
            final int dayIndex = i;
            dayDots[i].setOnClickListener(new DialogDayDotClickListener(item, dayIndex, dayDots, this));
        }

        presetWk.setOnClickListener(new DialogPresetClickListener(item, "WEEKDAYS", dayDots, this));
        presetAll.setOnClickListener(new DialogPresetClickListener(item, "ALL", dayDots, this));
        presetWknd.setOnClickListener(new DialogPresetClickListener(item, "WEEKENDS", dayDots, this));

        for (int i = 0; i < 6; i++) {
            final int val = tolVals[i];
            tolBtns[i].setOnClickListener(new DialogToleranceClickListener(item, val, tolBtns, tolVals, this));
        }

        dialog.show();
    }

    public void updateDialogDayDots(Prefs.ScheduleItem item, Button[] dayDots) {
        for (int i = 0; i < 7; i++) {
            boolean active = item.isDayIndexActive(i);
            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.OVAL);
            if (active) {
                gd.setColor(0xFF2563EB);
                dayDots[i].setTextColor(0xFFFFFFFF);
            } else {
                gd.setColor(0xFFF1F5F9);
                gd.setStroke(dp(1), 0xFFCBD5E1);
                dayDots[i].setTextColor(0xFF64748B);
            }
            dayDots[i].setBackground(gd);
        }
    }

    // ==========================================
    // 5. PROFILES & PATH OVERLAY TAB
    // ==========================================

    private void renderProfilesTab() {
        LinearLayout profileCard = card();
        profileCard.addView(section("Active Profile"));

        profiles = new Spinner(this);
        profiles.setContentDescription("Saved profiles");
        profiles.setOnItemSelectedListener(this);
        profileCard.addView(profiles, margins(-1, dp(48), 0, 6, 0, 10));

        TextView hint = text("Type below to create or rename profile:", 12, 0xFF64748B);
        profileCard.addView(hint, margins(-1, -2, 0, 0, 0, 4));

        profile = new EditText(this);
        profile.setHint("Profile Name");
        profile.setSingleLine(true);
        profile.setText(Prefs.currentProfile(this));
        profileCard.addView(profile, margins(-1, dp(48), 0, 0, 0, 10));

        saveProfileButton = primaryButton("Save / Create Profile");
        saveProfileButton.setTag("saveProfile");
        saveProfileButton.setOnClickListener(this);
        profileCard.addView(saveProfileButton);
        content.addView(profileCard, margins(-1, -2, 0, 0, 0, 14));

        // Action Sequence / Tap Path Card
        LinearLayout pathCard = card();
        pathCard.addView(section("Tap Path Configuration"));

        TextView pathSub = text("Record taps, gestures, and delays visually on top of your target application.", 13, 0xFF64748B);
        pathCard.addView(pathSub, margins(-1, -2, 0, 4, 0, 10));

        configure = primaryButton("Configure Tap Path Overlay");
        configure.setTag("configure");
        configure.setOnClickListener(this);
        pathCard.addView(configure);
        content.addView(pathCard, margins(-1, -2, 0, 0, 0, 14));
    }

    // ==========================================
    // 6. SETTINGS & SYSTEM BRIDGES TAB
    // ==========================================

    private void renderSettingsTab() {
        // Telegram Alerts & Remote Control Card
        LinearLayout tgCard = card();
        tgCard.addView(section("Telegram Notification Bridge"));

        telegramEnabled = new CheckBox(this);
        telegramEnabled.setText("Send screenshot & status on completion");
        telegramEnabled.setTextSize(13);
        telegramEnabled.setChecked(Prefs.telegramEnabled(this));
        tgCard.addView(telegramEnabled, margins(-1, -2, 0, 6, 0, 4));

        telegramRemoteEnabled = new CheckBox(this);
        telegramRemoteEnabled.setText("Enable Two-Way Remote Bot Listener");
        telegramRemoteEnabled.setTextSize(13);
        telegramRemoteEnabled.setChecked(Prefs.telegramRemoteEnabled(this));
        tgCard.addView(telegramRemoteEnabled, margins(-1, -2, 0, 0, 0, 6));

        telegramToken = new EditText(this);
        telegramToken.setHint(tokenHint());
        telegramToken.setSingleLine(true);
        telegramToken.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        tgCard.addView(telegramToken, margins(-1, dp(48), 0, 0, 0, 8));

        telegramChat = new EditText(this);
        telegramChat.setHint("Telegram Chat ID (e.g. 1605474113)");
        telegramChat.setSingleLine(true);
        telegramChat.setText(Prefs.telegramChat(this));
        tgCard.addView(telegramChat, margins(-1, dp(48), 0, 0, 0, 10));

        LinearLayout tgBtns = new LinearLayout(this);
        tgBtns.setOrientation(LinearLayout.HORIZONTAL);

        testTelegramButton = secondaryButton("Test Notification");
        testTelegramButton.setTag("testTelegram");
        testTelegramButton.setOnClickListener(this);
        tgBtns.addView(testTelegramButton, weightParams());

        saveSettingsButton = primaryButton("Save Bridge");
        saveSettingsButton.setTag("saveSettings");
        saveSettingsButton.setOnClickListener(this);
        tgBtns.addView(saveSettingsButton, weightParams());

        tgCard.addView(tgBtns, margins(-1, -2, 0, 0, 0, 12));

        // Remote Bot Status Banner & Commands Cheat Sheet (Visible ONLY when Remote Listener is Enabled!)
        boolean isRemoteRunning = TelegramRemoteService.isRunning();
        LinearLayout botStatusPill = new LinearLayout(this);
        botStatusPill.setOrientation(LinearLayout.VERTICAL);
        botStatusPill.setPadding(dp(12), dp(10), dp(12), dp(10));
        android.graphics.drawable.GradientDrawable bspBg = new android.graphics.drawable.GradientDrawable();
        bspBg.setColor(isRemoteRunning ? 0xFFF0FDF4 : 0xFFF8FAFC);
        bspBg.setCornerRadius(dp(8));
        bspBg.setStroke(dp(1), isRemoteRunning ? 0xFF86EFAC : 0xFFE2E8F0);
        botStatusPill.setBackground(bspBg);

        TextView botStatusTitle = text(isRemoteRunning ? "🟢 Remote Bot Listener: Active" : "⚪ Remote Bot Listener: Inactive", 12, isRemoteRunning ? 0xFF166534 : 0xFF64748B);
        botStatusTitle.setTypeface(null, Typeface.BOLD);
        botStatusPill.addView(botStatusTitle);

        TextView botCommands = text(
            "Send commands in Telegram:\n" +
            "• /run - Trigger automation from anywhere\n" +
            "• /sleep - Lock device & sleep screen\n" +
            "• /status - Live battery & device health\n" +
            "• /schedules - List active daily schedules\n" +
            "• /screenshot - Capture & send screen photo\n" +
            "• /ping - Check bot connectivity",
            12, 0xFF475569
        );
        botStatusPill.addView(botCommands, margins(-1, -2, 0, 4, 0, 0));
        botStatusPill.setVisibility(telegramRemoteEnabled.isChecked() ? View.VISIBLE : View.GONE);
        telegramRemoteEnabled.setOnCheckedChangeListener(new RemoteCheckChangeListener(botStatusPill));
        tgCard.addView(botStatusPill);

        content.addView(tgCard, margins(-1, -2, 0, 0, 0, 14));

        // System Settings Shortcuts
        LinearLayout sysCard = card();
        sysCard.addView(section("System & Device Diagnostics"));

        Button access = secondaryButton("Accessibility Engine Settings");
        access.setTag("access");
        access.setOnClickListener(this);
        sysCard.addView(access, margins(-1, dp(46), 0, 6, 0, 8));

        Button batteryOpt = secondaryButton("Battery Restrictions Settings");
        batteryOpt.setTag("batteryOpt");
        batteryOpt.setOnClickListener(this);
        sysCard.addView(batteryOpt, margins(-1, dp(46), 0, 0, 0, 8));

        Button miuiGuide = secondaryButton("🛡️ Xiaomi / MIUI 24/7 Keep-Alive Guide");
        miuiGuide.setTag("miuiGuide");
        miuiGuide.setOnClickListener(this);
        sysCard.addView(miuiGuide, margins(-1, dp(46), 0, 0, 0, 8));

        testAlarmButton = secondaryButton("Test Alarm Wake (15 Seconds)");
        testAlarmButton.setTag("testAlarm");
        testAlarmButton.setOnClickListener(this);
        sysCard.addView(testAlarmButton);
        content.addView(sysCard, margins(-1, -2, 0, 0, 0, 14));

        // Touch Physics Info Card
        LinearLayout physicsCard = card();
        physicsCard.addView(section("Touch Physics Engine"));
        TextView physicsText = text(
            "• Tap Duration: 130ms (Exceeds Flutter 100ms kPressTimeout)\n" +
            "• Natural Timing Jitter: ±150ms per wait step\n" +
            "• Dynamic Island Status: Stably anchored in notch zone\n" +
            "• Tapjacking Protection: Zero body window obstruction",
            12, 0xFF475569
        );
        physicsCard.addView(physicsText, margins(-1, -2, 0, 6, 0, 0));
        content.addView(physicsCard, margins(-1, -2, 0, 0, 0, 14));
    }

    // ==========================================
    // 7. CLICK & ACTION DISPATCHER
    // ==========================================

    @Override
    public void onClick(View view) {
        Object tag = view.getTag();
        if (tag == null) return;
        String t = tag.toString();

        if ("tabHome".equals(t)) {
            currentTab = TAB_HOME;
            renderTab();
        } else if ("tabSchedules".equals(t)) {
            currentTab = TAB_SCHEDULES;
            renderTab();
        } else if ("tabProfiles".equals(t)) {
            currentTab = TAB_PROFILES;
            renderTab();
        } else if ("tabSettings".equals(t)) {
            currentTab = TAB_SETTINGS;
            renderTab();
        } else if ("grantAccess".equals(t) || "access".equals(t)) {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } else if ("grantOverlay".equals(t)) {
            if (Build.VERSION.SDK_INT >= 23) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        } else if ("grantNotif".equals(t)) {
            requestNotifications();
        } else if ("grantAlarm".equals(t)) {
            if (Build.VERSION.SDK_INT >= 31) {
                Intent settings = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                startActivity(settings);
            }
        } else if ("grantBattery".equals(t) || "batteryOpt".equals(t)) {
            try {
                if (Build.VERSION.SDK_INT >= 23) {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                } else {
                    Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                    startActivity(intent);
                }
            } catch (Exception e) {
                try {
                    Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                    startActivity(intent);
                } catch (Exception ignored) { }
            }
        } else if ("chooseApp".equals(t)) {
            showAppChooser();
        } else if ("configure".equals(t)) {
            requestNotifications();
            setButtonLoading(configure, "● Opening Overlay...");
            startPulseAnimation(configure);
            configureOverlay();
            handler.postDelayed(new ResetButtonState(configure, "Configure Tap Path Overlay", 0xFF2563EB, 0xFFFFFFFF), 2500);
        } else if ("saveProfile".equals(t)) {
            saveVisible(true);
            restoreButtonSuccess(saveProfileButton, "✓ Profile Saved!", "Save / Create Profile", 0xFF2563EB, 0xFFFFFFFF);
        } else if ("saveSettings".equals(t)) {
            saveVisible(true);
            restoreButtonSuccess(saveSettingsButton, "✓ Bridge Saved!", "Save Bridge", 0xFF2563EB, 0xFFFFFFFF);
        } else if ("testTelegram".equals(t)) {
            requestNotifications();
            saveVisible(false);
            setButtonLoading(testTelegramButton, "● Connecting & Sending...");
            startPulseAnimation(testTelegramButton);
            TelegramNotifier.sendTest(this, this);
        } else if ("run".equals(t)) {
            requestNotifications();
            saveVisible(false);
            setButtonLoading(runButton, "● Launching Engine & App...");
            startPulseAnimation(runButton);
            Runner.run(this);
            handler.postDelayed(new ResetButtonState(runButton, "Run Automation Now", 0xFF2563EB, 0xFFFFFFFF), 4000);
            refresh();
        } else if ("addSchedule".equals(t)) {
            String id = "sched_" + System.currentTimeMillis();
            Prefs.ScheduleItem newItem = new Prefs.ScheduleItem(
                id, "Custom Schedule", 9, 0, true, 5, "1111100",
                Prefs.currentProfile(this),
                Prefs.targetPackage(this),
                Prefs.targetLabel(this)
            );
            showScheduleConfigDialog(newItem, true);
        } else if (t.startsWith("editSched_")) {
            String id = t.substring("editSched_".length());
            Prefs.ScheduleItem item = Prefs.getScheduleById(this, id);
            if (item != null) {
                showScheduleConfigDialog(item, false);
            }
        } else if (t.startsWith("toggleDay_")) {
            String rest = t.substring("toggleDay_".length());
            int lastUnderscore = rest.lastIndexOf('_');
            if (lastUnderscore > 0) {
                String id = rest.substring(0, lastUnderscore);
                int dayIdx = Integer.parseInt(rest.substring(lastUnderscore + 1));
                Prefs.ScheduleItem item = Prefs.getScheduleById(this, id);
                if (item != null) {
                    item.toggleDayIndex(dayIdx);
                    Prefs.saveSchedule(this, item);
                    if (item.enabled) Runner.scheduleItem(this, item);
                    renderTab();
                }
            }
        } else if (t.startsWith("toggleSched_")) {
            String id = t.substring("toggleSched_".length());
            Prefs.ScheduleItem item = Prefs.getScheduleById(this, id);
            if (item != null) {
                item.enabled = !item.enabled;
                Prefs.saveSchedule(this, item);
                if (item.enabled) {
                    Runner.scheduleItem(this, item);
                } else {
                    Runner.cancelScheduleItem(this, item.id);
                }
                renderTab();
            }
        } else if (t.startsWith("editTime_")) {
            String id = t.substring("editTime_".length());
            Prefs.ScheduleItem item = Prefs.getScheduleById(this, id);
            if (item != null) {
                new TimePickerDialog(this, new ScheduleTimeSetListener(this, item.id), item.hour, item.minute, false).show();
            }
        } else if (t.startsWith("cycleTolerance_") || t.startsWith("toggleJitter_")) {
            String id = t.startsWith("cycleTolerance_") ? t.substring("cycleTolerance_".length()) : t.substring("toggleJitter_".length());
            Prefs.ScheduleItem item = Prefs.getScheduleById(this, id);
            if (item != null) {
                item.randomWindowMinutes = Prefs.ScheduleItem.cycleTolerance(item.randomWindowMinutes);
                Prefs.saveSchedule(this, item);
                if (item.enabled) Runner.scheduleItem(this, item);
                renderTab();
            }
        } else if (t.startsWith("deleteSched_")) {
            String id = t.substring("deleteSched_".length());
            Runner.cancelScheduleItem(this, id);
            Prefs.deleteSchedule(this, id);
            renderTab();
        } else if ("testAlarm".equals(t)) {
            requestNotifications();
            saveVisible(false);
            setButtonLoading(testAlarmButton, "⏱️ Arming Wake Alarm (15s)...");
            startPulseAnimation(testAlarmButton);
            Runner.scheduleTestRunIn(this, 15);
            Toast.makeText(this, "Alarm set for 15s! Lock your phone screen now.", Toast.LENGTH_LONG).show();
            handler.postDelayed(new ResetButtonState(testAlarmButton, "Test Alarm Wake (15 Seconds)", 0xFFFFFFFF, 0xFF0F172A), 3000);
            refresh();
        } else if ("miuiGuide".equals(t)) {
            showMiuiKeepAliveDialog();
        } else if ("openAutostart".equals(t)) {
            openMiuiAutostart();
        } else if ("openAppInfo".equals(t)) {
            openAppDetailsSettings();
        }
    }

    private void showMiuiKeepAliveDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LinearLayout root = column();
        root.setPadding(dp(20), dp(16), dp(20), dp(16));

        TextView title = text("🛡️ Xiaomi / MIUI 24/7 Keep-Alive", 16, 0xFF0F172A);
        title.setTypeface(null, Typeface.BOLD);
        root.addView(title, margins(-1, -2, 0, 0, 0, 8));

        TextView info = text(
            "MIUI & HyperOS aggressively shut down Accessibility services when idle. Configure these 3 quick settings for 100% reliability:\n\n" +
            "1. Auto-Start ➔ Turn ON\n" +
            "2. Battery Saver ➔ Set to 'No restrictions'\n" +
            "3. Recent Apps ➔ Lock Darwin Watcher with Padlock 🔒",
            13, 0xFF334155
        );
        root.addView(info, margins(-1, -2, 0, 0, 0, 14));

        Button btnAutoStart = primaryButton("1. Open Auto-Start Settings");
        btnAutoStart.setTag("openAutostart");
        btnAutoStart.setOnClickListener(this);
        root.addView(btnAutoStart, margins(-1, dp(44), 0, 0, 0, 8));

        Button btnAppInfo = secondaryButton("2. Open Battery / App Info");
        btnAppInfo.setTag("openAppInfo");
        btnAppInfo.setOnClickListener(this);
        root.addView(btnAppInfo, margins(-1, dp(44), 0, 0, 0, 12));

        builder.setView(root);
        builder.setPositiveButton("Done", null);
        builder.show();
    }

    private void openMiuiAutostart() {
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"));
            startActivity(intent);
        } catch (Exception e1) {
            try {
                Intent intent = new Intent();
                intent.setComponent(new ComponentName("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity"));
                startActivity(intent);
            } catch (Exception e2) {
                openAppDetailsSettings();
            }
        }
    }

    private void openAppDetailsSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception ignored) { }
    }



    public void onScheduleTimeSet(String scheduleId, int hourOfDay, int minute) {
        Prefs.ScheduleItem item = Prefs.getScheduleById(this, scheduleId);
        if (item != null) {
            item.hour = hourOfDay;
            item.minute = minute;
            Prefs.saveSchedule(this, item);
            if (item.enabled) {
                Runner.scheduleItem(this, item);
            }
            renderTab();
        }
    }

    @Override
    public void onResult(boolean success, String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        if (testTelegramButton != null) {
            if (success) {
                restoreButtonSuccess(testTelegramButton, "✓ Notification Sent!", "Test Notification", 0xFFFFFFFF, 0xFF0F172A);
            } else {
                restoreButtonError(testTelegramButton, "✕ Send Failed", "Test Notification", 0xFFFFFFFF, 0xFF0F172A);
            }
        }
        refresh();
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (bindingProfiles) return;
        String typed = profile == null ? "" : profile.getText().toString().trim();
        if (profile != null && profile.hasFocus() && typed.length() > 0 && !Prefs.currentProfile(this).equals(typed)) return;
        String[] names = profileNames();
        if (position >= 0 && position < names.length && !Prefs.currentProfile(this).equals(names[position])) {
            Prefs.setCurrentProfile(this, names[position]);
            if (profile != null) profile.setText(names[position]);
            refresh();
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) { }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (appChoices == null || which < 0 || which >= appChoices.size()) return;
        AppItem item = appChoices.get(which);
        Prefs.setTargetApp(this, item.packageName, item.label);
        profilesCache = null;
        if (profile != null) profile.setText(Prefs.currentProfile(this));
        renderTab();
    }

    private void showAppChooser() {
        appChoices = loadApps();
        if (appChoices.isEmpty()) {
            Toast.makeText(this, "No launchable apps found", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels = new String[appChoices.size()];
        for (int i = 0; i < appChoices.size(); i++) labels[i] = appChoices.get(i).label;
        new AlertDialog.Builder(this).setTitle("Choose target app").setItems(labels, this).show();
    }

    private ArrayList<AppItem> loadApps() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        ArrayList<AppItem> result = new ArrayList<AppItem>();
        java.util.List<ResolveInfo> apps = getPackageManager().queryIntentActivities(intent, 0);
        for (int i = 0; i < apps.size(); i++) {
            ResolveInfo info = apps.get(i);
            if (info.activityInfo == null || info.activityInfo.packageName == null) continue;
            String packageName = info.activityInfo.packageName;
            if (getPackageName().equals(packageName)) continue;
            CharSequence rawLabel = info.loadLabel(getPackageManager());
            String label = rawLabel == null ? packageName : rawLabel.toString();
            result.add(new AppItem(label, packageName));
        }
        sortApps(result);
        return result;
    }

    private void sortApps(ArrayList<AppItem> apps) {
        for (int i = 1; i < apps.size(); i++) {
            AppItem item = apps.get(i);
            int j = i - 1;
            while (j >= 0 && apps.get(j).label.compareToIgnoreCase(item.label) > 0) {
                apps.set(j + 1, apps.get(j));
                j--;
            }
            apps.set(j + 1, item);
        }
    }

    private void saveVisible(boolean showToast) {
        String token = telegramToken == null ? Prefs.telegramToken(this) : telegramToken.getText().toString().trim();
        if (token.length() == 0) token = Prefs.telegramToken(this);
        boolean sendTelegram = telegramEnabled == null ? Prefs.telegramEnabled(this) : telegramEnabled.isChecked();
        String chat = telegramChat == null ? Prefs.telegramChat(this) : telegramChat.getText().toString();
        if (profile != null) {
            Prefs.setCurrentProfile(this, profile.getText().toString());
            profile.setText(Prefs.currentProfile(this));
        }
        Prefs.setTelegram(this, sendTelegram, token, chat);
        if (telegramRemoteEnabled != null) {
            Prefs.setTelegramRemoteEnabled(this, telegramRemoteEnabled.isChecked());
        }
        if (Prefs.telegramRemoteEnabled(this) && Prefs.hasTelegramCredentials(this)) {
            TelegramRemoteService.start(this);
        } else {
            TelegramRemoteService.stop(this);
        }
        if (telegramToken != null) {
            telegramToken.setText("");
            telegramToken.setHint(tokenHint());
        }
        if (telegramChat != null) telegramChat.setText(Prefs.telegramChat(this));
        profilesCache = null;
        refresh();
        if (showToast) Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
    }

    private void configureOverlay() {
        WatcherAccessibilityService service = WatcherAccessibilityService.current();
        if (service == null) {
            Toast.makeText(this, "Enable Accessibility first", Toast.LENGTH_SHORT).show();
            refresh();
            return;
        }
        if (!Runner.openTarget(this)) {
            refresh();
            return;
        }
        service.requestOverlay();
        refresh();
    }

    @Override
    public void onBackPressed() {
        if (currentTab != TAB_HOME) {
            currentTab = TAB_HOME;
            renderTab();
            return;
        }
        super.onBackPressed();
    }

    private void refresh() {
        if (content == null) return;
        updateTabs();
        if (profiles != null) refreshProfiles();
        boolean installed = Runner.isTargetInstalled(this);
        boolean accessibility = WatcherAccessibilityService.current() != null;
        boolean overlay = Settings.canDrawOverlays(this);
        boolean ready = installed && accessibility && overlay;

        if (status != null) {
            String appStatus = installed ? "Installed & Ready" : "Not Found (Select App)";
            String accessStatus = accessibility ? "Active & Connected" : "Disconnected (Grant Above)";
            String overlayStatus = overlay ? "Active" : "Missing (Grant Above)";
            String telegramStatus = Prefs.telegramConfigured(this) ? "Connected (Chat: " + Prefs.telegramChat(this) + ")" : "Disabled / Not Configured";
            String actionsStr = Prefs.actions(this).trim();
            int actionCount = actionsStr.length() == 0 ? 0 : actionsStr.split("\n").length;

            String devModel = DeviceUtils.getDeviceModelName();
            String devOS = DeviceUtils.getShortOS();
            String remoteStatus = TelegramRemoteService.isRunning() ? "Active & Listening" : "Inactive";

            status.setText(
                "SYSTEM & DEVICE OVERVIEW\n" +
                "  Device Model: " + devModel + " (" + devOS + ")\n" +
                "  Target App: " + Prefs.targetLabel(this) + " [" + appStatus + "]\n" +
                "  Accessibility Engine: " + accessStatus + "\n" +
                "  Overlay Permission: " + overlayStatus + "\n" +
                "  Telegram Alerts: " + telegramStatus + "\n" +
                "  Remote Bot Listener: " + remoteStatus + "\n\n" +
                "PROFILE & SCRIPT\n" +
                "  Active Profile: " + Prefs.currentProfile(this) + " (" + actionCount + " action steps)\n\n" +
                "LAST EXECUTION\n" +
                "  " + Prefs.lastStatus(this)
            );
        }

        if (runButton != null) {
            runButton.setEnabled(ready);
            runButton.setAlpha(ready ? 1.0f : 0.45f);
            if (!ready) runButton.setText("Grant Permissions to Run");
            else runButton.setText("Run Automation Now");
        }

        if (configure != null) {
            configure.setEnabled(ready);
            configure.setAlpha(ready ? 1.0f : 0.45f);
            if (!ready) configure.setText("Configure Tap Path (Permissions Needed)");
            else configure.setText("Configure Tap Path Overlay");
        }
    }

    private void refreshProfiles() {
        String list = Prefs.profiles(this);
        if (!list.equals(profilesCache) || profiles.getAdapter() == null) {
            String[] names = profileNames();
            ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, names);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            bindingProfiles = true;
            profiles.setAdapter(adapter);
            bindingProfiles = false;
            profilesCache = list;
        }
        selectCurrentProfile();
    }

    private void selectCurrentProfile() {
        String[] names = profileNames();
        bindingProfiles = true;
        for (int i = 0; i < names.length; i++) {
            if (Prefs.currentProfile(this).equals(names[i])) {
                profiles.setSelection(i);
                break;
            }
        }
        bindingProfiles = false;
    }

    private String[] profileNames() {
        String[] raw = Prefs.profiles(this).split(",");
        for (int i = 0; i < raw.length; i++) {
            raw[i] = raw[i].trim();
        }
        return raw;
    }

    private void requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, 7);
        }
    }

    private String tokenHint() {
        return Prefs.telegramToken(this).trim().length() > 0 ? "Token configured - type to replace" : "Telegram Bot Token";
    }

    private void clearVisibleFields() {
        status = null;
        runButton = null;
        configure = null;
        profile = null;
        profiles = null;
        telegramEnabled = null;
        telegramRemoteEnabled = null;
        telegramToken = null;
        telegramChat = null;
        testTelegramButton = null;
        saveSettingsButton = null;
        saveProfileButton = null;
        testAlarmButton = null;
    }

    private void updateTabs() {
        setTabStyle(homeTab, currentTab == TAB_HOME);
        setTabStyle(schedulesTab, currentTab == TAB_SCHEDULES);
        setTabStyle(profilesTab, currentTab == TAB_PROFILES);
        setTabStyle(settingsTab, currentTab == TAB_SETTINGS);
    }

    private void setTabStyle(Button button, boolean selected) {
        if (button == null) return;
        button.setTextColor(selected ? 0xFFFFFFFF : 0xFF475569);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(selected ? 0xFF2563EB : 0xFFFFFFFF);
        gd.setCornerRadius(dp(8));
        gd.setStroke(dp(1), selected ? 0xFF2563EB : 0xFFCBD5E1);
        button.setBackground(gd);
    }

    private Button tabButton(String label, String tag) {
        Button button = secondaryButton(label);
        button.setTag(tag);
        button.setOnClickListener(this);
        button.setTextSize(12);
        return button;
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFFFFFFF);
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), 0xFFE2E8F0);
        card.setBackground(bg);
        return card;
    }

    private TextView section(String value) {
        TextView view = text(value, 15, 0xFF0F172A);
        view.setTypeface(null, Typeface.BOLD);
        return view;
    }

    private TextView text(String value, int sp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        return view;
    }

    private Button primaryButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(0xFFFFFFFF);
        button.setTextSize(14);
        button.setTypeface(null, Typeface.BOLD);
        button.setAllCaps(false);
        button.setMinHeight(dp(48));
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(0xFF2563EB);
        gd.setCornerRadius(dp(8));
        button.setBackground(gd);
        return button;
    }

    private Button secondaryButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(0xFF0F172A);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setMinHeight(dp(44));
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(0xFFFFFFFF);
        gd.setCornerRadius(dp(8));
        gd.setStroke(dp(1), 0xFFCBD5E1);
        button.setBackground(gd);
        return button;
    }

    private Button chipButton(String label, String tag, boolean highlight) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(highlight ? 0xFFFFFFFF : 0xFF334155);
        button.setTextSize(11);
        button.setTypeface(null, Typeface.BOLD);
        button.setAllCaps(false);
        button.setSingleLine(true);
        button.setTag(tag);
        button.setOnClickListener(this);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(highlight ? 0xFF2563EB : 0xFFF1F5F9);
        gd.setCornerRadius(dp(6));
        gd.setStroke(dp(1), highlight ? 0xFF2563EB : 0xFFCBD5E1);
        button.setBackground(gd);
        button.setPadding(dp(4), dp(2), dp(4), dp(2));
        return button;
    }

    private LinearLayout.LayoutParams weightMargins(float weight, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(34), weight);
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return params;
    }

    private LinearLayout.LayoutParams margins(int width, int height, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return params;
    }

    private LinearLayout.LayoutParams weightParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -1, 1);
        params.setMargins(dp(2), 0, dp(2), 0);
        return params;
    }

    private LinearLayout.LayoutParams weightParamsWrap() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        params.setMargins(dp(2), 0, dp(2), 0);
        return params;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void setButtonLoading(final Button btn, String loadingText) {
        if (btn == null) return;
        btn.setEnabled(false);
        btn.setText(loadingText);
        btn.animate().alpha(0.7f).scaleX(0.97f).scaleY(0.97f).setDuration(200).start();
    }

    private void startPulseAnimation(final Button btn) {
        if (btn == null) return;
        handler.postDelayed(new PulseAnimation(btn, true), 200);
    }

    private void restoreButtonSuccess(final Button btn, String successText, final String defaultText, final int defaultBgColor, final int defaultTextColor) {
        if (btn == null) return;
        btn.setText(successText);
        btn.animate().alpha(1.0f).scaleX(1.0f).scaleY(1.0f).setDuration(200).start();
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(0xFF16A34A);
        gd.setCornerRadius(dp(8));
        btn.setBackground(gd);
        btn.setTextColor(0xFFFFFFFF);

        handler.postDelayed(new ResetButtonState(btn, defaultText, defaultBgColor, defaultTextColor), 2000);
    }

    private void restoreButtonError(final Button btn, String errorText, final String defaultText, final int defaultBgColor, final int defaultTextColor) {
        if (btn == null) return;
        btn.setText(errorText);
        btn.animate().alpha(1.0f).scaleX(1.0f).scaleY(1.0f).setDuration(200).start();
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(0xFFDC2626);
        gd.setCornerRadius(dp(8));
        btn.setBackground(gd);
        btn.setTextColor(0xFFFFFFFF);

        handler.postDelayed(new ResetButtonState(btn, defaultText, defaultBgColor, defaultTextColor), 2500);
    }

    private static final class ResetButtonState implements Runnable {
        private final Button button;
        private final String text;
        private final int bgColor;
        private final int textColor;

        ResetButtonState(Button button, String text, int bgColor, int textColor) {
            this.button = button;
            this.text = text;
            this.bgColor = bgColor;
            this.textColor = textColor;
        }

        @Override
        public void run() {
            if (button != null) {
                button.setEnabled(true);
                button.setText(text);
                button.setTextColor(textColor);
                button.setAlpha(1.0f);
                button.setScaleX(1.0f);
                button.setScaleY(1.0f);
                GradientDrawable gd = new GradientDrawable();
                gd.setColor(bgColor);
                gd.setCornerRadius(dpFrom(button, 8));
                if (bgColor == 0xFFFFFFFF || bgColor == 0xFFF1F5F9) {
                    gd.setStroke(dpFrom(button, 1), 0xFFCBD5E1);
                }
                button.setBackground(gd);
            }
        }

        private static int dpFrom(View v, int val) {
            return (int) (val * v.getResources().getDisplayMetrics().density + 0.5f);
        }
    }

    private static final class PulseAnimation implements Runnable {
        private final Button button;
        private final boolean forward;

        PulseAnimation(Button button, boolean forward) {
            this.button = button;
            this.forward = forward;
        }

        @Override
        public void run() {
            if (button != null && !button.isEnabled()) {
                float targetAlpha = forward ? 0.55f : 0.95f;
                button.animate().alpha(targetAlpha).setDuration(350)
                    .withEndAction(new PulseAnimation(button, !forward)).start();
            }
        }
    }

    private static final class AppItem {
        final String label;
        final String packageName;

        AppItem(String label, String packageName) {
            this.label = label;
            this.packageName = packageName;
        }
    }

    private static final class BootStep implements Runnable {
        private final MainActivity activity;
        private final int step;

        BootStep(MainActivity activity, int step) {
            this.activity = activity;
            this.step = step;
        }

        @Override
        public void run() {
            activity.updateBootStep(step);
        }
    }

    private static final class ShowMain implements Runnable {
        private final MainActivity activity;

        ShowMain(MainActivity activity) {
            this.activity = activity;
        }

        @Override
        public void run() {
            activity.splash = null;
            activity.showMain();
        }
    }

    private static final class RunAutomation implements Runnable {
        private final MainActivity activity;

        RunAutomation(MainActivity activity) {
            this.activity = activity;
        }

        @Override
        public void run() {
            Runner.run(activity);
        }
    }

    private static final class ScheduleTimeSetListener implements TimePickerDialog.OnTimeSetListener {
        private final MainActivity activity;
        private final String scheduleId;

        ScheduleTimeSetListener(MainActivity activity, String scheduleId) {
            this.activity = activity;
            this.scheduleId = scheduleId;
        }

        @Override
        public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
            activity.onScheduleTimeSet(scheduleId, hourOfDay, minute);
        }
    }

    private static final class RemoteCheckChangeListener implements CompoundButton.OnCheckedChangeListener {
        private final View targetView;

        RemoteCheckChangeListener(View targetView) {
            this.targetView = targetView;
        }

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (targetView != null) {
                targetView.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            }
        }
    }

    private static final class DialogTimePickerClickListener implements View.OnClickListener {
        private final MainActivity activity;
        private final Prefs.ScheduleItem item;
        private final Button timeBtn;

        DialogTimePickerClickListener(MainActivity activity, Prefs.ScheduleItem item, Button timeBtn) {
            this.activity = activity;
            this.item = item;
            this.timeBtn = timeBtn;
        }

        @Override
        public void onClick(View v) {
            new TimePickerDialog(activity, new DialogTimeSetListener(item, timeBtn), item.hour, item.minute, false).show();
        }
    }

    private static final class DialogTimeSetListener implements TimePickerDialog.OnTimeSetListener {
        private final Prefs.ScheduleItem item;
        private final Button timeBtn;

        DialogTimeSetListener(Prefs.ScheduleItem item, Button timeBtn) {
            this.item = item;
            this.timeBtn = timeBtn;
        }

        @Override
        public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
            item.hour = hourOfDay;
            item.minute = minute;
            timeBtn.setText("⏰  " + item.get12HourTime() + " " + item.getAmPm());
        }
    }

    private static final class DialogDayDotClickListener implements View.OnClickListener {
        private final Prefs.ScheduleItem item;
        private final int dayIndex;
        private final Button[] dayDots;
        private final MainActivity activity;

        DialogDayDotClickListener(Prefs.ScheduleItem item, int dayIndex, Button[] dayDots, MainActivity activity) {
            this.item = item;
            this.dayIndex = dayIndex;
            this.dayDots = dayDots;
            this.activity = activity;
        }

        @Override
        public void onClick(View v) {
            item.toggleDayIndex(dayIndex);
            activity.updateDialogDayDots(item, dayDots);
        }
    }

    private static final class DialogPresetClickListener implements View.OnClickListener {
        private final Prefs.ScheduleItem item;
        private final String preset;
        private final Button[] dayDots;
        private final MainActivity activity;

        DialogPresetClickListener(Prefs.ScheduleItem item, String preset, Button[] dayDots, MainActivity activity) {
            this.item = item;
            this.preset = preset;
            this.dayDots = dayDots;
            this.activity = activity;
        }

        @Override
        public void onClick(View v) {
            item.days = Prefs.ScheduleItem.normalizeDaysMask(preset);
            activity.updateDialogDayDots(item, dayDots);
        }
    }

    private static final class DialogToleranceClickListener implements View.OnClickListener {
        private final Prefs.ScheduleItem item;
        private final int value;
        private final Button[] tolBtns;
        private final int[] tolVals;
        private final MainActivity activity;

        DialogToleranceClickListener(Prefs.ScheduleItem item, int value, Button[] tolBtns, int[] tolVals, MainActivity activity) {
            this.item = item;
            this.value = value;
            this.tolBtns = tolBtns;
            this.tolVals = tolVals;
            this.activity = activity;
        }

        @Override
        public void onClick(View v) {
            item.randomWindowMinutes = value;
            for (int i = 0; i < tolBtns.length; i++) {
                boolean active = tolVals[i] == value;
                GradientDrawable gd = new GradientDrawable();
                gd.setColor(active ? 0xFF2563EB : 0xFFF1F5F9);
                gd.setCornerRadius(activity.dp(6));
                gd.setStroke(activity.dp(1), active ? 0xFF2563EB : 0xFFCBD5E1);
                tolBtns[i].setBackground(gd);
                tolBtns[i].setTextColor(active ? 0xFFFFFFFF : 0xFF334155);
            }
        }
    }

    private static final class SaveScheduleClickListener implements DialogInterface.OnClickListener {
        private final MainActivity activity;
        private final Prefs.ScheduleItem item;
        private final EditText nameInput;
        private final Spinner appSpinner;
        private final ArrayList<String> appPackages;
        private final Spinner profSpinner;
        private final String[] pNames;

        SaveScheduleClickListener(MainActivity activity, Prefs.ScheduleItem item, EditText nameInput, Spinner appSpinner, ArrayList<String> appPackages, Spinner profSpinner, String[] pNames) {
            this.activity = activity;
            this.item = item;
            this.nameInput = nameInput;
            this.appSpinner = appSpinner;
            this.appPackages = appPackages;
            this.profSpinner = profSpinner;
            this.pNames = pNames;
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            String name = nameInput.getText().toString().trim();
            if (name.length() > 0) item.name = name;

            int appIdx = appSpinner.getSelectedItemPosition();
            if (appIdx >= 0 && appIdx < appPackages.size()) {
                item.targetPackage = appPackages.get(appIdx);
                item.targetLabel = "com.darwinbox.darwinbox".equals(item.targetPackage) ? "Darwinbox" : "Calculator";
            }

            int profIdx = profSpinner.getSelectedItemPosition();
            if (profIdx >= 0 && profIdx < pNames.length) {
                item.profile = pNames[profIdx];
            }

            Prefs.saveSchedule(activity, item);
            if (item.enabled) {
                Runner.scheduleItem(activity, item);
            }
            activity.renderTab();
        }
    }
}
