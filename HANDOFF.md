# Handoff

Written 2026-08-27. If the date above is more than a week old, verify before trusting.

## Goal

Punch attendance in Darwinbox on a Samsung SM-M055F twice a working day (~08:14 check-in,
~18:16 check-out) with no human present, controlled and observed over Telegram.
It must run unattended from **27 Aug to 1 Sep 2026** while the owner is 500 km away.

## Safety constraints — read before running anything

- **A firing schedule rewrites the active profile.** `AlarmReceiver` calls
  `Prefs.setTargetApp` and `Prefs.setCurrentProfile` from the schedule's own fields. After
  any scheduled run the active profile is that schedule's profile, not what you selected.
  **Assert the active profile before any manual run.** This was violated once and fired a
  real attendance control:
  ```powershell
  adb shell "run-as com.darwin.watcher cat shared_prefs/darwin_watcher.xml" | Select-String current_profile
  ```
- **Never tap `[32,432][688,570]`** (`id/checkInShortcut`) while testing — that is the real
  punch.
- The prefs file contains the **live Telegram bot token**. Back it up to a scratchpad, never
  into the repo.
- `am crash com.darwin.watcher` is safe: it injects a synthetic crash and runs no
  automation. `am broadcast ... --ez isTest true` is **not** safe — it drives the target app
  using the active profile.

## Decisions already made — don't relitigate

- **A main-thread crash kills the process on purpose.** `START_STICKY` plus system-held
  alarms bring it back clean. Suppressing the kill is what caused a week of silent
  outages; do not "fix" the handler by swallowing exceptions again.
- **Background-thread crashes do not kill the process.** A dead poller thread is
  recoverable; a dead main thread is not.
- `targetSdkVersion` stays at **33**. Raising it to 34+ requires
  `android:foregroundServiceType` plus a matching permission, and the 24/7 listener would
  fail to start.
- **`/live` is a refreshing still image, not video.** `takeScreenshot` is platform
  rate-limited to ~1/s and each frame is a fresh multipart upload. Auto-refresh floors at
  3s. Do not attempt real-time mirroring from inside the app — use scrcpy from a PC that
  can reach the phone.
- **Samsung's "Never sleeping apps" is not used.** Doze-whitelisting via ADB is stronger
  and puts the app in standby bucket 5. An app already whitelisted does not appear in the
  picker at all.
- No test framework exists. **The device is the test bed.** Every fix is proven with a
  RED run and a GREEN run on hardware.
- Alerts are crash/restart only. No routine heartbeat spam to Telegram.

## Current state

### Works — verified on device 2026-08-27

| | Evidence |
|---|---|
| Crash → clean restart | `am crash` at 11:31, pid 15148 → 17200, alarms intact |
| Wedged main thread → restart | `Main thread unresponsive for 252s`, pid 13797 → 14980 |
| One Telegram alert per crash | received, formatted, record cleared |
| `/live` frames, zoom, TAP, Back, Home, Refresh | 11 frames sent, 11 delivered |
| One frame in flight | 5 coalesced, zero `canceled by new edit message request`, single messageId 318 |
| `/status`, `/net`, `/menu` | reply |
| Both apps doze-exempt | `com.darwin.watcher` + `com.darwinbox.darwinbox`, both bucket 5 |
| Schedules armed | 2026-08-27 18:16:41, 2026-08-28 08:14:38, plus 15-min heartbeat |
| Telegram reachable | `ESTAB … 149.154.166.110:443` |

### Not done

- **Task 7 — wireless ADB is still open** on `10.6.1.155:5555`. Deliberate: it must be the
  last action, and today's 18:16 check-out had not been observed yet.

### Untested

- Today's **18:16 check-out** firing unattended — the last run observable in person.
- Behaviour across a phone reboot in this build (`BOOT_COMPLETED` reschedules and the
  service is `START_STICKY`, so it should hold, but it has not been exercised since the
  crash-handler change).
- Whether a restart loop is possible if something crashes on every startup. Only
  main-thread crashes kill, and repeated Telegram alerts would make it visible.

## Exact next action

1. Watch today's **18:16:41** check-out fire and confirm its screenshot reaches Telegram.
2. Then close wireless ADB — **last action, nothing is remotely fixable afterwards**:
   ```powershell
   adb -s R9ZY40E319D usb
   adb connect 10.6.1.155:5555   # must now refuse
   ```
3. Leave the phone **on the charger**, on UPESNET, screen lock disabled.

## Commands

```powershell
# Build (PowerShell 5.1: do NOT pipe with 2>&1 — javac's stderr warning becomes a
# terminating error under the script's $ErrorActionPreference='Stop')
.\build.ps1

# Install onto a device already running a locally built APK
adb install -r "build\darwin-watcher-debug.apk"
adb shell "monkey -p com.darwin.watcher 1"

# Provision (USB, or wireless after `adb pair`)
.\install.ps1

# Health
adb shell "pidof com.darwin.watcher"
adb shell "dumpsys activity services com.darwin.watcher | grep -E 'ServiceRecord|isForeground'"
adb shell "dumpsys alarm | grep -A6 'walarm.*com.darwin.watcher' | grep origWhen"
adb shell "am get-standby-bucket com.darwin.watcher"     # 5 = EXEMPTED
adb shell "dumpsys deviceidle whitelist | grep -i darwin"

# Is the Telegram listener actually connected? (the definitive check)
adb shell "ss -tn | grep 149.154"                        # ESTAB = talking to Telegram

# Prove the crash-restart path (safe, runs no automation) — the pid MUST change
adb shell "pidof com.darwin.watcher"; adb shell "am crash com.darwin.watcher"; adb shell "pidof com.darwin.watcher"

# Logcat: this device floods the buffer (HeatmapThread, Light) and app lines rotate out
# within ~30s. Capture to a file with tag filters instead of `logcat -d | grep`.
adb logcat -c
adb logcat LiveView:V DarwinWatcher:V TelegramNotifier:V AndroidRuntime:E "*:S" > liveview.log

# Doze-exempt the target app (also moves it to standby bucket 5;
# `am set-standby-bucket <pkg> 5` throws — bucket 5 is only reachable this way)
adb shell "dumpsys deviceidle whitelist +com.darwinbox.darwinbox"

# Re-enable wireless ADB after returning: plug in USB, then run the
# "Phone Mirror (WiFi)" desktop shortcut once -> phone-mirror.ps1
```

Device: **SM-M055F**, serial `R9ZY40E319D`, wireless `10.6.1.155:5555`.

---

# Away runbook — 27 Aug to 1 Sep 2026

The phone is at UPES on the charger, on UPESNET, with **no SIM** (`gsm.sim.state =
ABSENT,ABSENT`) — Wi-Fi is the only path to Telegram. Wireless ADB is closed, so nothing
on the device is fixable remotely. Everything below is done from the Telegram chat.

## What a healthy day looks like

Two screenshots, roughly **08:14** and **18:16**, Monday to Saturday. Nothing else.

## If a screenshot does not arrive

| Step | Command | What it means |
|---|---|---|
| 1 | `/status` | Replies → the listener is alive and on Wi-Fi. No reply → see "Total silence". |
| 2 | `/live` | A frame arrives → the app is healthy; the miss was Darwinbox-side. |
| 3 | `/run` | Performs the **real punch** (active profile is `Darwin`). Late, but recorded. |

`/status` replying while `/live` does nothing was the classic half-dead signature — the
poller thread surviving a dead main thread. That should no longer happen: a main-thread
crash kills the process, and a main thread that wedges without crashing is killed by the
poller's watchdog within ~4 min. Either way `START_STICKY` and the system-held alarms
bring it back.

## Alerts you may receive

```
⚠ Darwin Watcher restarted after a crash
  Thread   main
  Error    <exception>
  When     <timestamp>
  Schedules and the listener are back up. No action needed.
```

Informative, not alarming — the safety net did its job. One message per crash. Only worry
if several arrive in a short window, which would mean a restart loop.

## Total silence

`/status` not replying means the phone lost Wi-Fi, lost power, or Samsung froze the app
despite the exemptions. None are fixable remotely. Someone physically present must
unplug/replug the charger or open Darwin Watcher once from the launcher.

## Device state as left (verified 2026-08-27)

| | |
|---|---|
| Doze whitelist | `com.darwin.watcher`, `com.darwinbox.darwinbox` |
| Standby buckets | both **5 (EXEMPTED)** |
| `RUN_ANY_IN_BACKGROUND` | allow |
| Wi-Fi sleep policy | 2 (never sleeps) |
| Auto restart at set times | unset |
| Screen lock | disabled |
| Active profile | **`Darwin`** — `/run` punches for real |
| Service restart policy | `START_STICKY` |
| Alarms | held by AlarmManager; survive process death and reboot |
| Battery | on AC, 80% |
| Accessibility | `com.darwin.watcher/…WatcherAccessibilityService` (the only entry; Tasker/AutoInput are not installed on this phone) |
