# Darwin Watcher 🤖📱

<p align="center">
  <img src="assets/splash.png" alt="Darwin Watcher Splash" width="220"/>
  <br>
  <b>Autonomous Android Touch Automation, Intelligent Task Scheduler & Two-Way Telegram Remote Control Hub</b>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android_13%2B-brightgreen.svg" alt="Platform"/>
  <img src="https://img.shields.io/badge/Architecture-Native_Java_|_Accessibility-blue.svg" alt="Architecture"/>
  <img src="https://img.shields.io/badge/Remote_Control-Telegram_Bot_API-0088cc.svg" alt="Telegram"/>
  <img src="https://img.shields.io/badge/Keep_Alive-24%2F7_Self--Healing-red.svg" alt="24/7 Shield"/>
  <img src="https://img.shields.io/badge/License-MIT-orange.svg" alt="License"/>
</p>

---

## 📌 Overview

**Darwin Watcher** is an on-device personal automation engine and remote management hub for Android. It enables automated touch navigation, background screen wake-up, keyguard swipe-unlocking, multi-schedule automation with natural human-like jitter, 24/7 unkillable background persistence with self-healing accessibility, and an **Interactive Two-Way Telegram Remote Bot** that requires **zero external servers or VPS**.

---

## 📸 Interface & Live Screenshots

<p align="center">
  <img src="assets/dashboard.png" width="30%" alt="Dashboard Tab"/>
  <img src="assets/schedules.png" width="30%" alt="Schedules Tab"/>
  <img src="assets/settings.png" width="30%" alt="Settings Tab"/>
</p>

<p align="center">
  <em>Figure 1: Dashboard with System Shield · Figure 2: Schedules with Day Dots & Tolerance · Figure 3: Telegram Bridge & Keep-Alive</em>
</p>

<br>

<p align="center">
  <img src="assets/schedule_modal.png" width="32%" alt="Schedule Editor Modal"/>
  &nbsp;&nbsp;&nbsp;&nbsp;
  <img src="assets/calculator_result.png" width="32%" alt="Live Automation Execution"/>
</p>

<p align="center">
  <em>Figure 4: Interactive Schedule Editor · Figure 5: Live Execution with Clean Auto-Sleep</em>
</p>

---

## ✨ Core Features & Capabilities

### 1. 🤖 Two-Way Telegram Command Hub & Inline Buttons
Runs a lightweight, battery-optimized foreground listener on the device with **Interactive Telegram Inline Keyboard Buttons**:

```text
┌───────────────────────────────────────────────┐
│ 🤖 DARWIN WATCHER CONTROL CENTER              │
│ • Device: Xiaomi Mi 11X (Android 13)         │
│ • Target App: Calculator                      │
│ • Active Profile: Default                     │
│ • System Shield: 🟢 24/7 Active               │
├───────────────────────┬───────────────────────┤
│ 🚀 Run Now            │ 📸 Screenshot         │
├───────────────────────┼───────────────────────┤
│ ☀️ Wake Screen        │ 🔒 Lock Screen        │
├───────────────────────┼───────────────────────┤
│ 📊 Status             │ 🔄 Profiles           │
├───────────────────────┼───────────────────────┤
│ ⏰ Schedules          │ 🔊 Find Phone (Siren) │
├───────────────────────┼───────────────────────┤
│ 🌐 Network & Info     │ 🏠 Home               │
└───────────────────────┴───────────────────────┘
```

#### 🕹️ Supported Commands & Features:
- **Interactive Inline Buttons**: Zero typing required — execute tasks, capture screenshots, and switch profiles with 1 tap.
- **`/live`** & **`/live stop`**: **Live view & control** — posts one screenshot message that is *replaced* on each refresh (never floods the chat), with a 4x6 zoom grid drawn on the frame, Back/Home/Recents, manual Refresh and an Auto toggle. See the caveat below.
- **`/run`** & **`/run in <time>`**: Execute automation immediately or set a delayed one-off timer (*e.g., `/run in 10m` or `/run in 45s`*).
- **`/wake`** & **`/sleep`**: Remotely wake display & dismiss keyguard or put device to sleep.
- **`/profiles`** or **`/profile <name>`**: Interactively switch active automation profile.
- **`/tap <x> <y>`**: Execute direct touch taps at specific coordinates from anywhere in the world.
- **`/swipe <x1> <y1> <x2> <y2> [duration]`**: Perform smooth swipe gestures remotely.
- **`/text <content>`**: Type text directly into the focused input box.
- **`/home`**, **`/back`**, **`/recents`**, **`/notifications`**: Remote navigation key controls.
- **`/ring`** & **`/stopring`**: Loud siren alarm finder at 100% volume to locate misplaced phone (bypasses DND/Silent).
- **`/net`** or **`/ip`**: Dynamic hardware telemetry — live connected Wi-Fi SSID, local IPv4, link speed, RSSI dBm signal, available RAM, and free internal storage.
- **⚡ Autonomous Power Alerts**: Real-time push notifications when charger is connected, disconnected, or battery drops below 15%.

---

### 2. 🛡️ 24/7 Keep-Alive & Self-Healing Accessibility Shield
- **Auto-Healing Accessibility Engine**: Powered by `WRITE_SECURE_SETTINGS`. If MIUI/Android ever unbinds accessibility, the background daemon **automatically rewrites Android Secure Settings and re-enables itself with zero manual intervention**.
- **CPU WakeLock Shield**: Continuous `PARTIAL_WAKE_LOCK` prevents CPU deep-sleep freeze on Xiaomi/Samsung/Pixel devices.
- **15-Minute Watchdog Heartbeat**: Recurring `setExactAndAllowWhileIdle` pulse continuously verifies listener & accessibility health.
- **Doze & Battery Optimization Bypass**: Whitelisted via `dumpsys deviceidle whitelist` + `AppOps` permissions (`10008` Auto-start, `10021` Lockscreen, `10022` Background popups).
- **Process CrashShield**: Global uncaught exception handler prevents background runtime hiccups from terminating the process.

---

### 3. ⏰ Flexible Scheduling Engine
- **7-Day Selector**: Interactive weekday dots (`Mon` to `Sun`) with quick presets (*Weekdays*, *Every Day*, *Weekends*).
- **12-Hour AM/PM Format**: Clean time pickers with exact alarm dispatching.
- **Natural Time Tolerance Adjuster**: Random jitter ($\pm 0\text{m}$, $\pm 2\text{m}$, $\pm 5\text{m}$, $\pm 10\text{m}$, $\pm 15\text{m}$, $\pm 30\text{m}$) for human-like execution windows.
- **Per-Schedule Binding**: Assign specific target apps and profiles to each schedule.
- **Boot Persistence**: Automatically re-registers exact alarms upon phone reboot.

---

### 4. 🔒 Locked Phone Safety & Cold-Start Reliability
- **Lock Screen Protection**: Keyguard status checks guarantee zero phantom taps on lock screens.
- **Auto Screen Wake & Swipe Unlock**: Wakes display and dispatches calibrated swipe gesture to unlock.
- **Fresh Cold Starts**: Relaunches target apps fresh from the beginning with `FLAG_ACTIVITY_CLEAR_TASK`.
- **4-Second Warmup Buffer**: Live countdown overlay (`App ready · Resting (4s)...`) allows layouts to settle before execution.
- **Auto-Sleep on Completion**: Closes the target app and locks the device after sending the screenshot.

---

## 🛠️ Quickstart: Build & 1-Click Provision

### Prerequisites
- Android SDK: `platform-tools`, `build-tools;33.0.2`, `platforms;android-33-ext5`
  (`build.ps1` reads `build-tools\33.0.2` and `platforms\android-33-ext5\android.jar` — these exact versions)
- JDK with `javac`, `java`, `keytool` on PATH (JDK 22 works; `--release 8` only emits obsolete-target warnings)
- `ANDROID_SDK_ROOT` or `ANDROID_HOME` pointing at the SDK
- PowerShell (Windows)

### 1. Build the Debug APK
```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1
```

`build.ps1` signs with a debug keystore it generates at `$env:TEMP\darwin-watcher-debug.keystore`. That key is **not** the one the committed APK was signed with, so a locally built APK cannot `install -r` over a copy installed from `build/darwin-watcher-debug.apk`. You get `INSTALL_FAILED_UPDATE_INCOMPATIBLE` and must `adb uninstall com.darwin.watcher` first — **which wipes schedules, profiles and Telegram settings.** Back up first:

```powershell
adb shell "run-as com.darwin.watcher cat /data/data/com.darwin.watcher/shared_prefs/darwin_watcher.xml" > prefs-backup.xml
```

### 2. Automated Install & 24/7 Provisioning (USB or Wireless ADB)
```powershell
# For USB connected phone:
powershell -ExecutionPolicy Bypass -File .\install.ps1

# For Wireless ADB connected phone:
powershell -ExecutionPolicy Bypass -File .\install.ps1 192.168.1.10:43387
```

`install.ps1` installs the APK, grants `WRITE_SECURE_SETTINGS`, whitelists doze/battery optimisation, applies OEM-specific keep-alive settings, and appends the accessibility service to Android Secure Settings. It reports **PASS/FAIL/SKIP per command** and finishes with a 7-check verification of actual end state, then exits non-zero if anything failed. It is idempotent — re-running a healthy device changes nothing.

Wireless ADB on Android 11+ needs a one-time pairing before `install.ps1 <ip>:<port>` will work:
```powershell
adb pair <ip>:<pairing-port>   # 6-digit code from Wireless debugging > Pair device with pairing code
```
The pairing port differs from the connect port shown under "IP address & Port". `adb connect` to an unpaired port reports the device as `offline`.

---

## 📱 Per-device notes

Automation coordinates are **screen-resolution specific**. Re-measure them per device — never rescale by hand. See `tasks/coords.md` for the measured map of the current device and the re-calibration procedure.

| Device | Screen | Status |
|---|---|---|
| Xiaomi Mi 11X (MIUI, Android 13) | 1080x2400 | original target |
| Samsung SM-M055F / Galaxy M05 (One UI 8, Android 16 / API 36) | 720x1600 | verified working |

### Samsung / One UI

`install.ps1` handles what ADB can reach. These have **no ADB equivalent** and are required:

1. Settings → Battery → Background usage limits → **Never sleeping apps** → add Darwin Watcher **and the target app**
2. Same screen → **Put unused apps to sleep** → OFF
3. Settings → Battery → **Optimise battery usage** → Darwin Watcher → not optimised
4. Developer options → **USB debugging (Security settings)** → ON (needed for `WRITE_SECURE_SETTINGS` after a factory reset)

One UI runs **Freecess**, which repeatedly tries to freeze both this app and the target app (visible as `FreecessHandler: freeze <pkg> result : 2` in logcat). It cannot be disabled over ADB — step 1 above is the mitigation.

The MIUI keep-alive AppOps (`10008` autostart, `10021` lock screen, `10022` background popups) **do not exist on One UI** and are skipped automatically.

### Android 14+ / targetSdk warning

`targetSdkVersion` is **33** and must stay there. Raising it to 34+ makes Android require `android:foregroundServiceType` plus a matching `FOREGROUND_SERVICE_*` permission, neither of which `TelegramRemoteService` declares — the 24/7 listener would fail to start. Verified running on Android 16 at targetSdk 33 (`types=0x00000000`).

`android:persistent="true"` in the manifest is silently ignored for non-system apps. It contributes nothing to the keep-alive.

### Useful verification commands

```powershell
adb shell "dumpsys activity services com.darwin.watcher | grep -E 'ServiceRecord|isForeground'"
adb shell "dumpsys alarm | grep -B1 -A3 'Alarm{.*com.darwin.watcher'"
adb shell "am get-standby-bucket com.darwin.watcher"   # 5 = EXEMPTED (best), 10 = ACTIVE
adb shell settings get secure enabled_accessibility_services
adb shell "run-as com.darwin.watcher cat /data/data/com.darwin.watcher/shared_prefs/darwin_watcher.xml"
```

Trigger a run through the real alarm path (uses the currently active profile):
```powershell
adb shell "am broadcast -n com.darwin.watcher/.AlarmReceiver --ez isTest true"
```

Do **not** launch the target with `monkey -p <pkg> 1` — monkey injects one pseudo-random event after launching, which can press Back and silently drop the app out of the foreground. Use `am start -n <pkg>/<activity>`.

### Two behaviours that surprise people

- **A firing schedule rewrites the active profile.** `AlarmReceiver` calls `Prefs.setTargetApp` and `Prefs.setCurrentProfile` from the schedule's own fields, so after any scheduled run the active profile is that schedule's profile — not whatever you had selected. Check the active profile before a manual run.
- **Action text is keyword-filtered.** `Runner.parse()` rejects the whole profile if the text contains any of ~30 words (`checkin`, `attendance`, `login`, `otp`, `password`, …). Keep profiles to bare `tap` / `wait` / `swipe` lines — a descriptive comment mentioning one of those words will throw `Blocked risky word`.

---

## ⚙️ Telegram Bot Setup

1. Message **`@BotFather`** on Telegram to create your bot and copy your `BOT_TOKEN`.
2. Get your `CHAT_ID` (using `@userinfobot` or similar).
3. Open **Darwin Watcher** $\to$ Navigate to the **Settings** tab:
   - Turn **ON** `Send screenshot & status on completion`
   - Turn **ON** `Enable Two-Way Remote Bot Listener`
   - Paste your `Bot Token` and `Chat ID`
   - Tap **Save Bridge**
4. Send `/menu` in your Telegram chat to launch the remote control center!

---

## 📂 Project Architecture

```text
.
├── app/src/main/
│   ├── java/com/darwin/watcher/
│   │   ├── MainActivity.java                # Multi-tab Dashboard, Schedules & Settings UI
│   │   ├── WatcherAccessibilityService.java # Accessibility touch engine, lock/unlock, gestures & screenshot
│   │   ├── Runner.java                      # Action execution loop, warmup countdown & auto-sleep
│   │   ├── TelegramRemoteService.java       # Two-way foreground bot poller, callback queries & command hub
│   │   ├── TelegramNotifier.java            # Multi-part Telegram client with Markdown & inline keyboards
│   │   ├── WakeUnlockActivity.java          # Keyguard dismissal & screen turn-on activity
│   │   ├── AlarmReceiver.java               # Exact alarm scheduler with 15-min watchdog heartbeat
│   │   ├── DarwinDeviceAdminReceiver.java   # Universal Device Administrator protection
│   │   ├── RingtoneFinder.java              # 100% volume loud alarm siren finder
│   │   ├── Prefs.java                       # Persistent schedules, profiles & configuration
│   │   └── DeviceUtils.java                 # Dynamic hardware telemetry, network resolver & self-healing engine
│   ├── res/                                 # Strings, styles, drawables, device admin & accessibility config
│   └── AndroidManifest.xml                  # App permissions, receivers & system services
├── assets/                                  # UI Screenshots and documentation media
├── build/                                   # Compiled ready-to-use APK
├── build.ps1                                # Native build & packaging pipeline
├── install.ps1                              # 1-click install & 24/7 permission provisioning script
├── LICENSE                                  # MIT License
└── README.md                                # Project documentation
```

---

## 👨‍💻 Author

**Bipin Vishwakarma**
- GitHub: [@bipin-vishwakarma](https://github.com/bipin-vishwakarma)

---

## 🖥 Live view (`/live`) — what it is and isn't

`/live` is a **refreshing still image, not video.** Two hard limits make real-time
mirroring impossible from inside the app:

- `AccessibilityService.takeScreenshot` is rate-limited by the platform to roughly one
  call per second.
- Every frame is a fresh multipart upload to Telegram, which applies its own per-chat
  edit rate limits.

The honest ceiling is a frame every few seconds. Auto-refresh is floored at **3s**, and a
session stops itself after **100 frames or 5 minutes** so a forgotten live view cannot
drain the battery.

**Want true real-time mirroring with full mouse and keyboard control?** Use
[scrcpy](https://github.com/Genymobile/scrcpy) over ADB — 30-60fps, no app changes,
and it needs nothing from this project:

```bash
scrcpy -s <device-serial>
```

That needs a PC that can reach the phone. `/live` is for when all you have is your phone.

### Controls

The grid is a **zoom selector, not a tapper**. A grid coarse enough to fit an inline
keyboard cannot hit a button: on 720x1600 a 4x6 grid is still 180x267px per cell. So
numbers **zoom** into that region and redraw; the **✥ TAP** button hits the red crosshair
at the centre of whatever is currently shown. One zoom reaches ~45x67px, which is
button-sized. **🔍 Out** steps back a level, **⛶ Whole** returns to the full screen.

The grid, its numbers and the crosshair are drawn **onto the frame itself** — numbers
living only in the keyboard leave you guessing which part of the screen each one means.

Cell geometry comes from `getDisplayMetrics()`, so it adapts to any screen. For anything
precise, read the coordinates off the live frame and use `/tap <x> <y>`.

### What `/live` needs (and does not need)

`/live` runs over the **Telegram Bot API**, exactly like `/run` and `/status`. The phone
holds an outbound HTTPS long-poll to `api.telegram.org`. Therefore:

- It does **not** need ADB, USB, a PC, or you to be on the same network as the phone.
- It **does** need the phone to have working internet. That is the only requirement.

The screen is woken automatically. A screenshot taken while the display is off does not
fail — it returns a **solid black frame** (~7.9KB versus ~180KB for a real screen), which
is why live view previously appeared to "stop working" whenever the phone had been idle.
`LiveView.ensureScreenOn()` now holds a session wake lock and calls
`DeviceUtils.wakeUpScreen()` when the display is off, the same path `Runner` uses.

### Privacy

Live frames are uploaded to your Telegram chat. Whatever is on screen goes with them.
Live view never starts on its own — it is only ever started by `/live` or the menu
button, and it stops itself.
tail -5 README.md

---

## 📶 Connectivity facts for this device (SM-M055F)

`gsm.sim.state = ABSENT,ABSENT` — **there is no SIM in this phone**, so it has no mobile
data. Its only route to the internet is Wi-Fi.

| Situation | Telegram commands (`/live`, `/run`, …) |
|---|---|
| Phone on any Wi-Fi with internet, you anywhere in the world | works |
| Phone out of Wi-Fi range | cannot work — no path to Telegram |

Moving the phone somewhere new means joining it to that Wi-Fi once. You never need to be
on the same network as the phone.

`wifi_sleep_policy=2` and power saving off, so Wi-Fi stays associated while idle.

## 🖥 scrcpy (real-time mirroring — needs a PC that can reach the phone)

Installed via `winget install Genymobile.scrcpy`. Two desktop shortcuts:

- **Phone Mirror** — phone on USB
- **Phone Mirror (WiFi)** — runs `phone-mirror.ps1`: remembers the last working address,
  and if the phone is unreachable but plugged in, auto-detects its current IP, re-enables
  wireless ADB, saves it and starts

Wireless ADB (`adb tcpip 5555`) **switches off whenever the phone reboots**. Recovery:
plug in USB, run the Wi-Fi shortcut once, unplug.

Two PowerShell gotchas that broke that script and are worth remembering:

- Do not use `$ErrorActionPreference = 'Stop'` around native commands. PowerShell 5.1
  wraps a program's stderr in error records, and `adb` writes ordinary messages such as
  `no devices found` to stderr — which aborts the script mid-way.
- `$matches` is overwritten by the *next* `-match`. Capture `$matches[1]` into a variable
  before comparing anything else.
