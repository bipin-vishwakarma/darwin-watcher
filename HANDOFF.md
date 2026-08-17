# Handoff

## Goal

Make Darwin Watcher work on a **Samsung SM-M055F (Galaxy M05), One UI 8.0, Android 16 / API 36, 720x1600** exactly as it did on the Xiaomi Mi 11X (MIUI, Android 13, 1080x2400).
All debugging uses the **`test` profile only** — the `Darwin` profile performs a real Darwinbox attendance punch.

## ⚠️ Do this first — device is in a test state

The phone's prefs were deliberately altered for testing and **not yet reverted**. The owner's attendance automation is currently disabled.

| Schedule | Time | Should be | Currently |
|---|---|---|---|
| Morning - check in | 08:10, Mon–Sat, ±5m, profile `Darwin` | `enabled:true` | **`enabled:false`** |
| evening - check out | 17:30, Mon–Sat, ±5m, profile `Darwin` | `enabled:true` | **`enabled:false`** |
| BOOT TEST | 13:34, all days, exact, profile `test` | **should not exist** | `enabled:true` |

Also changed from original: `telegram_enabled` was `false`, now `true` (screenshot-on-completion). Ask the owner whether to keep it on.

Fix via the app UI (Schedules tab: re-enable the two, delete BOOT TEST), or restore prefs wholesale from the backup — path in "Backups" below. After fixing, confirm both alarms are armed:

```powershell
adb shell "dumpsys alarm | grep -B1 -A3 'Alarm{.*com.darwin.watcher'"
```

Expect three armed alarms: the two `.AlarmReceiver` schedules plus `HEARTBEAT`.

## Decisions already made — don't relitigate

- **`targetSdkVersion` stays 33.** At 34+ Android requires `android:foregroundServiceType` + a `FOREGROUND_SERVICE_*` permission; `TelegramRemoteService` declares neither, so the 24/7 listener would fail to start. It currently runs fine on Android 16 with `types=0x00000000` precisely because of the targetSdk-33 exemption.
- **Never rescale tap coordinates arithmetically.** Measure them with `uiautomator dump`. On this device a 1.5x rescale of tap 1 lands at `238,443`, inside `checkInShortcut [32,432][688,570]` — the real Check Out button. Full measured map and procedure: `tasks/coords.md`.
- **The `test` profile's targets are correct as of now**: `tap 360 730` (Attendance tile) → `tap 360 1136` (Attendance view button → read-only `MonthlyAttendanceActivity`). Both verified harmless — no attendance state is written.
- **`install.ps1` appends to `enabled_accessibility_services`, never replaces.** Replacing wipes every other enabled accessibility service on the device. Matches `DeviceUtils.ensureAccessibilityEnabled()`.
- **Do not force the standby bucket to `active`.** The doze whitelist already yields bucket 5 (EXEMPTED); `active` is 10, i.e. worse.
- **Use `am start`, never `monkey -p <pkg> 1`**, to launch anything. Monkey injects a pseudo-random event that can press Back and silently background the app.
- **Trigger test runs through the alarm path**, not the UI button: `am broadcast -n com.darwin.watcher/.AlarmReceiver --ez isTest true`. It exercises the real scheduled-run code and uses the active profile.
- **Verify gestures through the app**, not `adb shell input tap`. `input tap` bypasses the `dispatchGesture` path that actually needs proving. (`input tap` is fine for *exploring* the UI.)

## Current state

### Works — verified on device
- APK installed, `com.darwin.watcher`, uid 10266.
- `WRITE_SECURE_SETTINGS` `granted=true`; `USE_EXACT_ALARM` `granted=true`.
- Accessibility service bound, `capabilities=161` (retrieve-window-content + perform-gestures + take-screenshot), `Crashed services:{}`.
- `TelegramRemoteService` foreground, id 1002.
- Doze whitelisted; standby bucket 5; `SYSTEM_ALERT_WINDOW allow`.
- **End-to-end run passes**, twice — once screen-on, once from `Dozing`/screen-off. Trace: `Splash` 2s → `Dashboard` 4s → `AttendanceHome` 8s → `MonthlyAttendance` 12s → launcher 18s → screen OFF 20s. Zero `WaitForTarget` retries. `last_run_status` = `Telegram screenshot sent!`.
- `install.ps1` reports all-PASS with 7/7 end-state checks, and is idempotent.

### Broken / not fixed
- **Tasks 5-6 not written.** `DeviceUtils.getDeviceModelName()` reports `Samsung SM-M055F` instead of `Galaxy M05`; the "Xiaomi / MIUI 24/7 Keep-Alive Guide" button (`MainActivity.java:968-971`) and `openMiuiAutostart()` (`:1186-1189`) target `com.miui.securitycenter` and are dead taps on Samsung; `Prefs.targetPackage()` (`Prefs.java:66`) defaults to `com.miui.calculator`, which is not installed here (Samsung's is `com.sec.android.app.popupcalculator`).
  Blocked on a decision, see "Next action".

### Untested
- **Boot persistence.** Reboot was performed but the device came back `unauthorized` (USB-debugging dialog) and was then disconnected, so `BOOT_COMPLETED` re-registration was never observed. The `BOOT TEST` schedule may have fired unobserved — check `last_run_status` and alarm history before re-running.
- **Endurance under Freecess.** One UI logs `FreecessHandler: freeze <pkg> result : 2` every ~6s against both this app and Darwinbox. Not yet proven the listener survives a ≥30 min screen-off idle window. Suspect if it fails: the wake lock is acquired with a 10-minute timeout (`TelegramRemoteService.java:87`) while the watchdog heartbeat is 15 minutes, so the lock lapses before the heartbeat lands.
- **Telegram remote command matrix.** Needs the owner's Telegram chat; cannot be driven from ADB.
- **Whether the run screenshot actually arrived in Telegram** — the app reported success, unconfirmed by eye.
- **Keyguard swipe-unlock.** `locksettings get-disabled` = `true`, i.e. no PIN/pattern on this phone, so `isDeviceLocked()` is always false and the unlock gesture never ran. Needs retesting if a secure lock is ever set.

## Next action

1. **Revert the device prefs** (see the warning block at the top). Highest priority — the owner's attendance automation is off.
2. Ask the owner: land Tasks 5-6 or skip them? Landing them requires a rebuild, and `build.ps1`'s generated keystore (`d8d94a0b…`) does not match the committed APK's signer (`b1770bfb…`), so `install -r` fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. The only paths are:
   - uninstall → install local build → restore prefs from backup (wipes then restores schedules, profiles, Telegram token), or
   - skip Tasks 5-6 and keep the working committed APK, or
   - obtain the original keystore from the repo owner so signatures match.
3. Then the untested items above, in order: boot persistence → endurance → Telegram matrix.

Task-by-task detail with acceptance criteria: `tasks/plan.md`. Live checklist: `tasks/todo.md`.

## Backups

A pre-change copy of the device prefs is in the session scratchpad:

```
C:\Rtmp\claude\O--UPES-11--People-Bipin-Darwin-Watcher\5872b0e4-856b-4307-9e41-3067b5806883\scratchpad\prefs-backup-task1.xml
```

**This file contains the live Telegram bot token — never commit it.** If the scratchpad is gone, the prefs are only on the device.

Restore procedure (the app must be stopped first, or its in-memory `SharedPreferences` will overwrite the file):

```powershell
adb shell am force-stop com.darwin.watcher
adb push prefs-backup-task1.xml /data/local/tmp/dw.xml
adb shell chmod 644 /data/local/tmp/dw.xml
adb shell "run-as com.darwin.watcher cp /data/local/tmp/dw.xml /data/data/com.darwin.watcher/shared_prefs/darwin_watcher.xml"
adb shell rm /data/local/tmp/dw.xml
adb shell am start -n com.darwin.watcher/.MainActivity
```

## Commands

```powershell
# Build (see README for the keystore/signature caveat)
powershell -ExecutionPolicy Bypass -File .\build.ps1

# Install + provision (USB)
powershell -ExecutionPolicy Bypass -File .\install.ps1

# Install + provision (wireless; needs a prior `adb pair`)
powershell -ExecutionPolicy Bypass -File .\install.ps1 10.6.1.155:41701
```

```powershell
# Health check
adb shell "dumpsys activity services com.darwin.watcher | grep -E 'ServiceRecord|isForeground'"
adb shell "dumpsys alarm | grep -B1 -A3 'Alarm{.*com.darwin.watcher'"
adb shell "am get-standby-bucket com.darwin.watcher"
adb shell settings get secure enabled_accessibility_services
adb shell "run-as com.darwin.watcher cat /data/data/com.darwin.watcher/shared_prefs/darwin_watcher.xml"
```

```powershell
# Trigger a run with the active profile, then watch where it goes
adb shell "am broadcast -n com.darwin.watcher/.AlarmReceiver --ez isTest true"
adb shell "dumpsys activity activities | grep topResumedActivity"
```

```powershell
# Re-measure coordinates on a new device
adb shell wm size; adb shell wm density
adb shell am start -n com.darwinbox.darwinbox/com.darwinbox.splashscreen.ui.SplashScreenActivity
adb shell uiautomator dump /sdcard/ui.xml; adb pull /sdcard/ui.xml
adb shell screencap -p /sdcard/s.png; adb pull /sdcard/s.png
```

Note: capture screenshots with `screencap` to a file then `adb pull`. Piping `adb exec-out screencap -p > file.png` through PowerShell corrupts the binary (adds a BOM).

## Environment

- Repo: `O:\UPES\11. People\Bipin\Darwin Watcher\darwin-watcher`, remote `github.com/bipin-vishwakarma/darwin-watcher`
- Android SDK: `C:\Users\Mrinal Singh\AppData\Local\Android\Sdk` (`ANDROID_HOME` set to this; it previously pointed at a nonexistent `C:\Android\Sdk`)
- Installed SDK packages: `platform-tools`, `build-tools;33.0.2`, `platforms;android-33-ext5`
- JDK 22 (`javac --release 8` warns about obsolete target; harmless)
- Test device: `R9ZY40E319D` over USB. Wireless debugging was never paired — `adb connect` alone reports `offline`.
