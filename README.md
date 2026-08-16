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
- Android SDK (`build-tools`, `platforms;android-33`)
- PowerShell (Windows) or ADB

### 1. Build the Debug APK
```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1
```

### 2. Automated Install & 24/7 Provisioning (USB or Wireless ADB)
```powershell
# For USB connected phone:
powershell -ExecutionPolicy Bypass -File .\install.ps1

# For Wireless ADB connected phone:
powershell -ExecutionPolicy Bypass -File .\install.ps1 192.168.1.10:43387
```
*The `install.ps1` script automatically installs the APK, grants `WRITE_SECURE_SETTINGS`, whitelists battery optimization, configures auto-start AppOps, and locks accessibility in Android Secure Settings.*

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
