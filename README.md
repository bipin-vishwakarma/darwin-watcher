# Darwin & Darwin Watcher 🤖📱

> **Autonomous Android Navigation, Screen Automation & Two-Way Telegram Remote Control Engine**

Darwin is a complete, production-grade Android automation suite comprising:
1. **`darwin`**: A safe-by-default Python agent powered by **LangGraph 1.2** for goal validation, device inspection, LLM vision planning, and ADB execution.
2. **`darwin-watcher`**: A high-performance native Android application running directly on-device with Accessibility gestures, automatic screen wake & swipe unlocking, scheduled task execution with time tolerance, and a **Two-Way Telegram Remote Bot** (no external VPS required).

---

## 🌟 Key Highlights & Features

### 📲 On-Device Engine (`darwin-watcher`)
- **Zero VPS / Server Needed**: Runs fully autonomous on Android with a low-power background Telegram bot poller.
- **Lock Screen Auto-Unlock**: Wakes the display and automatically dispatches a calibrated upward swipe gesture to unlock without touching the phone.
- **Strict Keyguard Protection**: Keyguard status checks guarantee zero phantom taps on lock screens.
- **Clean App Cold Starts**: Always restarts target apps from scratch (`FLAG_ACTIVITY_CLEAR_TASK`) before executing actions.
- **4-Second Warmup Buffer**: Displays a live Dynamic Island countdown (`App ready · Resting (4s)...`) to allow splash screens and networks to settle.
- **Auto-Sleep Upon Completion**: Captures a clean screenshot, delivers it to Telegram, closes the app, and automatically locks the device and sleeps the display.
- **Flexible Scheduling Tab**:
  - 7-day interactive day-of-week selector (`Mon` to `Sun`) with quick presets (*Weekdays*, *Every Day*, *Weekends*).
  - 12-Hour AM/PM format support.
  - Natural time tolerance stepper (Exact, $\pm 2\text{m}$, $\pm 5\text{m}$, $\pm 10\text{m}$, $\pm 15\text{m}$, $\pm 30\text{m}$) for human-like scheduling.
  - Per-schedule app and profile bindings that persist across reboots.
- **Xiaomi / MIUI 24/7 Keep-Alive Shield**:
  - Full accessibility configuration (`canRetrieveWindowContent="true"`, `FLAG_RETRIEVE_INTERACTIVE_WINDOWS`).
  - 1-tap in-app shortcuts to configure MIUI Auto-Start and Battery Saver (*No restrictions*).
- **Dynamic Island Watermark**: Non-intrusive notch HUD showing real-time step progress with zero tapjacking interference.

### 🐍 Python LangGraph Agent (`darwin`)
- Safe-by-default execution pipeline (`validate -> inspect -> plan -> approve -> execute -> verify`).
- Support for deterministic JSON plans or plain-English goals via LLM.
- Enforced security policies rejecting dangerous package launches, shell injections, and sensitive data leakage.

---

## 🤖 Telegram Remote Control Center

Send commands directly to your private Telegram bot from anywhere in the world:

| Command | Action |
| :--- | :--- |
| **`/run`** | Wakes device, auto-unlocks screen, opens target app fresh, warms up for 4s, executes taps, takes screenshot, delivers photo, and locks phone back to sleep |
| **`/sleep`** or **`/lock`** | Remotely locks the phone and puts screen to sleep immediately |
| **`/status`** | Real-time battery percentage, charging state, target app, active profile, and system engine health |
| **`/schedules`** | Lists all configured automated schedules with active days and tolerance windows |
| **`/screenshot`** | Captures a live screen photo and sends it to Telegram |
| **`/ping`** | Checks bot connectivity and returns device model (*e.g., Xiaomi Mi 11X*) |

---

## 🚀 Quickstart Guide

### 1. Building & Installing `darwin-watcher` (Android APK)

Prerequisites:
- Android SDK installed (`build-tools`, `platforms;android-33`)
- PowerShell

```powershell
# Navigate to the Android watcher directory
cd darwin-watcher

# Build the debug APK
powershell -ExecutionPolicy Bypass -File .\build.ps1

# Install onto your connected Android device via ADB
adb install -r .\build\darwin-watcher-debug.apk
```

### 2. Setting Up Telegram Remote Bridge
1. Message `@BotFather` on Telegram to create your bot and obtain your `BOT_TOKEN`.
2. Get your `CHAT_ID` (e.g. from `@userinfobot`).
3. Open **Darwin Watcher** on your phone $\to$ Navigate to the **Settings** tab:
   - Check **"Send screenshot & status on completion"**
   - Check **"Enable Two-Way Remote Bot Listener"**
   - Enter your `Bot Token` and `Chat ID`
   - Tap **Save Bridge**
4. Send `/ping` or `/run` in your Telegram chat to test!

---

### 3. Setting Up the Python CLI (`darwin`)

```powershell
# Create virtual environment
py -3.13 -m venv .venv
.venv\Scripts\Activate.ps1

# Install dependencies
python -m pip install -e ".[dev]"

# Verify ADB connection
adb devices

# Run preview demo
darwin demo --package com.android.settings

# Run deterministic action plan
darwin run examples/basic_plan.json --execute
```

---

## 📂 Repository Structure

```text
.
├── darwin-watcher/                  # Native Android Automation Engine
│   ├── app/src/main/
│   │   ├── java/com/darwin/watcher/
│   │   │   ├── MainActivity.java                # Multi-tab Dashboard, Schedules & Settings UI
│   │   │   ├── WatcherAccessibilityService.java # Accessibility touch engine, lock/unlock & screenshot
│   │   │   ├── Runner.java                      # Action execution loop, warmup countdown & auto-sleep
│   │   │   ├── TelegramRemoteService.java       # Two-way foreground bot poller & command dispatcher
│   │   │   ├── TelegramNotifier.java            # Telegram Bot HTTP client (photo/text)
│   │   │   ├── WakeUnlockActivity.java          # Keyguard dismissal & screen turn-on activity
│   │   │   ├── AlarmReceiver.java               # Exact alarm scheduler with reboot resilience
│   │   │   ├── Prefs.java                       # Persistent schedules, profiles & configuration
│   │   │   └── DeviceUtils.java                 # Hardware model & system inspector
│   │   └── AndroidManifest.xml                  # App permissions & system services
│   ├── build.ps1                                # Native build & packaging pipeline
│   └── build/darwin-watcher-debug.apk           # Compiled ready-to-use APK
├── src/darwin/                      # Python LangGraph Automation Agent
│   ├── __main__.py                  # CLI entry point
│   ├── cli.py                       # Argument parsing & execution flows
│   ├── graph.py                     # LangGraph state machine & orchestration
│   ├── planner.py                   # Deterministic & smart LLM plan generator
│   └── adb.py                       # ADB adapter & device controller
├── tests/                           # Python unit and integration test suite
├── examples/                        # Sample automation plan JSONs
├── LICENSE                          # MIT License
└── README.md                        # Documentation & setup guide
```

---

## 🛡️ Privacy & Security Notice

Darwin and Darwin Watcher are designed for personal productivity and automated testing on devices and accounts you own. The engine does **not** bypass biometric authentication or PINs on encrypted screens without authorization. Keep your bot tokens private and out of public repositories.

---

## 👨‍💻 Author

**Bipin Vishwakarma**
- GitHub: [@bipin-vishwakarma](https://github.com/bipin-vishwakarma)
