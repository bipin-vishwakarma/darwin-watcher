# Handoff

## Goal

Keep Darwin Watcher working on a **Samsung SM-M055F (Galaxy M05), One UI 8, Android 16, 720x1600**,
driving `com.darwinbox.darwinbox` on a schedule, with a usable Telegram `/live` remote view.

## Safety constraint — read before running anything

The **`Darwin` profile performs a real Darwinbox attendance punch.** Test with the **`test`** profile only.
Never tap inside `id/checkInShortcut` `[32,432][688,570]` — that is the live Check In/Out control.

`AlarmReceiver` sets the active profile *from the firing schedule*, so a scheduled run always uses
`Darwin` regardless of what is currently selected.

## Decisions already made — don't relitigate

- **`targetSdkVersion` stays 33.** At 34+ Android requires `android:foregroundServiceType` plus a
  `FOREGROUND_SERVICE_*` permission; `TelegramRemoteService` declares neither, so the 24/7 listener
  would fail to start. It runs on Android 16 today only because of the targetSdk-33 exemption.
- **Never rescale tap coordinates arithmetically — measure them.** A 1.5x rescale of the old tap 1
  lands at `238,443`, inside `checkInShortcut`, i.e. a real attendance punch. See `tasks/coords.md`.
- **`/live` is a refreshing still, not video.** `takeScreenshot` is rate-limited to ~1/s and every
  frame is a fresh upload. Auto-refresh floors at 3s; sessions self-stop at 120 frames / 5 min.
  For real-time control use scrcpy.
- **The `/live` grid zooms, it does not tap.** A grid coarse enough for an inline keyboard cannot hit
  a button. Numbers zoom; `✥ TAP` hits the crosshair. Grid and numbers are drawn onto the frame.
- **`install.ps1` appends to `enabled_accessibility_services`, never replaces** — replacing wipes every
  other enabled accessibility service (Tasker/AutoInput, Nova, TalkBack).
- **Do not force the standby bucket to `active`.** The doze whitelist already yields bucket 5
  (EXEMPTED); `active` is 10, i.e. worse.
- **Use `am start`, never `monkey -p <pkg> 1`** — monkey injects a random event that can press Back.
- **Do not rebuild the committed APK.** `build.ps1` signs with a generated keystore, so committing a
  local build forces every existing install to uninstall and lose its config. Bipin must rebuild with
  the original key.

## Current state

### Works — verified on device
- Schedules fire **exactly once per day**. Morning check-in 08:10 ±5, evening check-out 18:15 ±2,
  both Mon–Sat, profile `Darwin`, both armed.
- `/live` works over the internet with no ADB, no USB, no shared network. Proven: the phone holds
  ESTABLISHED HTTPS connections to `149.154.166.110:443` (Telegram) and user commands arrive in logcat.
- `/live` wakes the screen, so it works with the phone idle and face-down.
- Every `/live` action delivers a frame or an explicit error — capture retries, two settle frames,
  new-message fallback if an edit fails.
- Provisioning: `install.ps1` reports all-PASS with 7 end-state checks, and is idempotent.
- scrcpy 4.1 mirrors at 720x1600 over USB and over Wi-Fi.

### Broken / not done
- **Samsung "Never sleeping apps" has never been set** for Darwin Watcher. UI-only, no ADB equivalent.
  Without it, Freecess can freeze the listener after hours idle and `/live` gets no reply. **This is the
  most likely cause of any future "it stopped working overnight" report.**
- `current_profile` is `test`, left from testing. Scheduled runs unaffected; a manual `/run` would run
  the harmless `test` actions rather than checking out.

### Untested
- Endurance: the listener surviving a multi-hour idle period unplugged.
- Boot persistence after a reboot (`BOOT_COMPLETED` re-registration observed only indirectly).
- The `test` profile's second tap is a **no-op** — Darwinbox moved Attendance to
  `FlutterEmbeddingActivity`, so `360,1136` no longer hits anything. Deliberate; the profile still
  exercises cold start, gestures, screenshot, close and auto-sleep.

## Hard constraint: no SIM

`gsm.sim.state = ABSENT,ABSENT`. The phone has **no mobile data**; Wi-Fi is its only internet path.
Telegram commands work from anywhere in the world **provided the phone is on some Wi-Fi with internet**.
Out of Wi-Fi range, nothing can work. This is hardware, not code.

## Exact next action

1. On the phone: Settings → Battery → Background usage limits → **Never sleeping apps** → add
   **Darwin Watcher** (and the target app). This is the only outstanding correctness item.
2. Leave the phone unplugged and idle for 2+ hours, then send `/live` from Telegram. That is the one
   scenario never verified.
3. Ask the user whether `current_profile` should go back to `Darwin`.

## Commands

```powershell
# Build (see README for the keystore/signature caveat)
powershell -ExecutionPolicy Bypass -File .\build.ps1

# Install onto a device already running a locally built APK
adb install -r build\darwin-watcher-debug.apk

# Provision (USB, or wireless after `adb pair`)
powershell -ExecutionPolicy Bypass -File .\install.ps1
```

```powershell
# Health
adb shell "dumpsys activity services com.darwin.watcher | grep -E 'ServiceRecord|isForeground'"
adb shell "dumpsys alarm | grep -A3 'Alarm{.*com.darwin.watcher'"
adb shell "am get-standby-bucket com.darwin.watcher"          # 5 = EXEMPTED (best)
adb shell "run-as com.darwin.watcher cat /data/data/com.darwin.watcher/shared_prefs/darwin_watcher.xml"

# Is the Telegram listener actually connected? (the definitive check)
adb shell "ss -tn | grep 149.154"                              # ESTAB = talking to Telegram
adb logcat -d | grep TelegramRemoteService                     # incoming commands

# Trigger a run through the real alarm path (uses the ACTIVE profile - check it first)
adb shell "am broadcast -n com.darwin.watcher/.AlarmReceiver --ez isTest true"
adb shell "dumpsys activity activities | grep topResumedActivity"
```

```powershell
# Mirroring
scrcpy -s R9ZY40E319D
# Wireless: use the "Phone Mirror (WiFi)" desktop shortcut -> phone-mirror.ps1
# adb tcpip mode dies on reboot; plug in USB and run the shortcut once to restore it.
```

Screenshots: use `adb shell screencap -p /sdcard/x.png` then `adb pull`. Piping
`adb exec-out screencap -p > file.png` through PowerShell corrupts the binary.
**~7904 bytes means the screen was off** (solid black), not that capture failed.

## Debugging notes that cost real time

- **Gesture callbacks run on the main thread.** A null `Done` used to throw there and wedge the main
  Looper, silently killing every pending `postDelayed`. CrashShield hides it — grep logcat for
  `CrashShield intercepted uncaught exception in thread main`.
- **A screenshot with the display off returns solid black, it does not fail.** Always check screen
  state before blaming capture.
- **`takeScreenshot` fails during window transitions**, i.e. right after a tap that opens an app.
  Retry rather than treating it as fatal.
- PowerShell 5.1: never `$ErrorActionPreference='Stop'` around native commands (adb writes normal
  messages to stderr), and `$matches` is clobbered by the next `-match`.
- Git Bash mangles device paths (`/data/local/tmp` → `C:/Program Files/Git/...`). Use PowerShell for
  `adb push`/`pull`, or set `MSYS_NO_PATHCONV=1` — but then local paths need Windows form.

## Environment

- Repo `O:\UPES\11. People\Bipin\Darwin Watcher\darwin-watcher`, branch `samsung-oneui-port`,
  remote `github.com/bipin-vishwakarma/darwin-watcher`, open PR #1.
- SDK `C:\Users\Mrinal Singh\AppData\Local\Android\Sdk` (`platform-tools`, `build-tools;33.0.2`,
  `platforms;android-33-ext5`). JDK 22.
- Device `R9ZY40E319D`; Wi-Fi ADB at `10.6.1.155:5555` on UPESNET.
- Prefs backups (contain the live bot token — **never commit**):
  `C:\Rtmp\claude\O--UPES-11--People-Bipin-Darwin-Watcher\5872b0e4-856b-4307-9e41-3067b5806883\scratchpad\`
