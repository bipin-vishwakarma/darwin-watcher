# Work log

## 2026-08-26
- Fixed main-thread crash on fire-and-forget taps: `GestureCallback.onCompleted/onCancelled` called `done.call()` with no null check; live view, `/tap` and `/swipe` all pass `done = null`. Gesture callbacks run on the main thread, so the NPE wedged the main Looper and killed every pending `postDelayed`, including the post-tap screenshot. Confirmed in logcat: `AndroidRuntime NPE ... GestureCallback.onCompleted:1259` + `CrashShield intercepted uncaught exception in thread main`.
- Fixed live-view ANR: `takeScreenshot()` used `getMainExecutor()`, so crop/scale/grid-draw/JPEG-compress ran on the UI thread every refresh. Rendering moved to a single-thread executor; run-screenshot path still on main (touches WindowManager overlays).
- Fixed black live frames: a screenshot with the display off succeeds and returns solid black (7904 bytes vs ~180KB). `LiveView.ensureScreenOn()` added - session wake lock plus `DeviceUtils.wakeUpScreen()` when not interactive; `/live` waits 1.6s after waking before frame 1; every capture re-checks and retries within the existing budget.
- Live view reworked from a flat tap grid into a zoom selector: grid/numbers/crosshair drawn onto the frame, numbers zoom, `TAP` hits the crosshair, `Out`/`Whole` navigate. Grid 3x4 -> 4x6 (180x267px per cell on 720x1600), so aiming costs one zoom plus TAP.
- Frame delivery guaranteed: capture retries 4x/900ms, two settle frames per action (1.4s and 4.2s), failed `editMessageMedia` falls back to posting a new message and adopts its id, and an all-attempts-failed case reports in chat instead of going silent.
- Refactored capture to one implementation: `captureBitmap(BitmapReady)` returns the raw bitmap, callers own presentation. Replaced `captureFrame(maxWidth, quality)`, which could not crop or draw.
- Verified `/live` is transport-independent: phone holds 3 ESTABLISHED HTTPS connections to `149.154.166.110:443` (Telegram); user commands `/live`, `cb_lv_c:5`, `cb_lv_tap` arrived over the internet with no ADB involvement.
- Recorded device connectivity facts: `gsm.sim.state = ABSENT,ABSENT` (no SIM, no mobile data - Wi-Fi is the only internet path); `wifi_sleep_policy=2`; power saving off.
- Confirmed Darwinbox is screenshot-capable (no FLAG_SECURE), ruling it out as a cause.
- Installed/verified scrcpy 4.1 (was already present via winget, not on Git Bash PATH). Added desktop shortcuts `Phone Mirror` (USB) and `Phone Mirror (WiFi)` -> `phone-mirror.ps1`, which auto-detects the phone IP over USB, re-enables `adb tcpip 5555`, caches the address and reconnects. Verified from a fully cold state (no adb server, cable unplugged).
- Schedules confirmed live: Morning check-in 08:10 +/-5 and evening check-out 18:15 +/-2 (user changed 17:35 -> 18:15), both Mon-Sat, profile `Darwin`, both armed.
- Open: `current_profile` is `test` (leftover from testing). Scheduled runs are unaffected - `AlarmReceiver` sets the profile from the schedule - but a manual `/run` would run `test` actions. User was asked; not changed.
- Open: Samsung `Never sleeping apps` still not added for Darwin Watcher. UI-only, no ADB equivalent. Without it Freecess can freeze the listener after hours idle and `/live` gets no reply.
- Open: no SIM in the phone, so Telegram commands fail wherever there is no Wi-Fi. Hardware, not code.
- Open: committed `build/darwin-watcher-debug.apk` deliberately not rebuilt - `build.ps1` signs with a generated keystore, so a local build would force existing installs to uninstall.

## 2026-08-25
- Fixed duplicate schedule runs: `Runner.scheduleItem()` re-armed the same day because it rebuilt `when` as today and rolled a fresh jitter, advancing the day only `if (triggerTime <= now)`. Fire at 08:05, re-roll to 08:15, 08:15 > 08:05, second run. On a toggle target (Darwinbox check-in/out) run two undid run one.
- Added `Prefs.scheduleLastRunDate` / `setScheduleLastRunDate` / `clearScheduleLastRunDate` / `scheduleAlreadyRanToday` / `dateStamp` / `todayStamp`; stamp written with `commit()` so a mid-run crash cannot license a repeat.
- `AlarmReceiver` skips a scheduled run already run today, re-arms, and does not touch target app or active profile on a skipped run.
- `Runner.scheduleItem()` never arms a trigger on a date already recorded as run; also closes the `BOOT_COMPLETED` re-registration path.
- `Prefs.saveSchedule()` clears the stamp, so editing a schedule to a later time the same day still arms today.
- Verified on device: fire at 12:45 with nominal 12:49 +/-5 re-armed to `2026-08-26 12:53:44` (tomorrow). Duplicate `scheduleId` broadcast produced no run (24s activity trace stayed `(none)`). `isTest` broadcast still runs.
- Rebuilt, uninstalled, reinstalled, restored prefs from backup; `install.ps1` all-PASS; both `Darwin` schedules re-armed (17:35:56 today, 08:10:47 tomorrow); token, chat, 3 profiles, 2 action sets intact.
- Re-checked Darwinbox bounds: dashboard identical to 2026-08-17, `checkInShortcut [32,432][688,570]` unchanged, so the production profile is unaffected. Attendance section migrated to `FlutterEmbeddingActivity`, making the `test` profile's second tap a no-op (documented, left as-is).
- Archived Samsung port plan to `tasks/plan-samsung-port.md` / `tasks/todo-samsung-port.md`.
- Open: `/live` Telegram live view (Tasks 3-5) not started.
- Open: device-aware naming + MIUI dead-ends (Task 6) not started.
- Open: stray no-op alarm for the removed `sched_verify` schedule fires once at 2026-08-26 12:53 and does nothing (`getScheduleById` returns null); clears on reboot.

## 2026-08-17
- Ported to Samsung SM-M055F (Galaxy M05), One UI 8.0, Android 16 / API 36, 720x1600.
- Measured real Darwinbox node bounds on 720x1600; recorded in `tasks/coords.md`.
- `test` profile taps corrected: `357,665` → `360,730` (was already valid), `416,1073` → `360,1136` (was a dead tap on a non-clickable stat cell, 23px above the Attendance view button).
- Confirmed coords were authored on this device, not MIUI leftovers. A 1.5x rescale of tap 1 would land at `238,443`, inside `checkInShortcut [32,432][688,570]` — the real Check Out button.
- `install.ps1` rewritten: OEM branch on `ro.product.manufacturer`, per-command PASS/FAIL/SKIP, 7-check end-state verification, non-zero exit on failure, signature-mismatch and missing-APK guards, `am start` instead of `monkey`.
- `install.ps1` fix: all adb calls routed through `Adb-Raw`/`Adb-Shell` so `-s $DeviceAddress` is applied to `pm grant`/`appops`/`settings`, not just install and launch.
- `install.ps1` fix: accessibility service is now appended to `enabled_accessibility_services` instead of replacing the list. Previous behaviour wiped every other enabled accessibility service (Tasker/AutoInput, Nova, TalkBack).
- `install.ps1` fix: removed `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` appop — no such appop exists ("Unknown operation string"); the doze whitelist is the mechanism.
- `install.ps1` fix: `cmd package set-standby-bucket` → `am set-standby-bucket`, and only applied when bucket > 10, since the doze whitelist already yields bucket 5 (EXEMPTED) and forcing `active` (10) is a downgrade.
- MIUI appops `10008/10021/10022` gated to Xiaomi only; documented that they do not exist on One UI.
- Verified on device: `WRITE_SECURE_SETTINGS granted=true`, accessibility bound with `capabilities=161`, `TelegramRemoteService` foreground with `types=0x00000000`, doze whitelisted, standby bucket 5, `SYSTEM_ALERT_WINDOW allow`, `USE_EXACT_ALARM granted=true`.
- `SCHEDULE_EXACT_ALARM` is not `pm grant`-able on Android 16 ("not a changeable permission type"); `USE_EXACT_ALARM` is install-granted and is what the scheduler relies on.
- End-to-end test run passed twice (screen-on and screen-off/Dozing), triggered via `am broadcast -n com.darwin.watcher/.AlarmReceiver --ez isTest true`. Activity trace: Splash 2s → Dashboard 4s → AttendanceHome 8s → MonthlyAttendance 12s → launcher 18s → screen OFF 20s. Zero `WaitForTarget` retries. `last_run_status` = `Telegram screenshot sent!`.
- Documented: `targetSdkVersion` must stay 33 — at 34+ `TelegramRemoteService` needs `foregroundServiceType`, which the manifest does not declare.
- Documented: `build.ps1` keystore differs from the committed APK signer (`d8d94a0b…` vs `b1770bfb…`), so local builds require uninstall, which wipes prefs.
- Added `tasks/plan.md`, `tasks/todo.md`, `tasks/coords.md`. Updated `README.md` with Samsung/One UI provisioning, prerequisites, wireless-ADB pairing, and verification commands.
- Toolchain installed on the dev machine: Android cmdline-tools, `platform-tools`, `build-tools;33.0.2`, `platforms;android-33-ext5`; `ANDROID_HOME` corrected from nonexistent `C:\Android\Sdk`.
- Open: **device prefs left in a test state** — both `Darwin` schedules (08:10, 17:30) are `enabled:false` and a throwaway `BOOT TEST` schedule (13:34, profile `test`) is `enabled:true`. Must be reverted.
- Open: boot-persistence test (Task 10) unverified — device returned from reboot `unauthorized` and was then disconnected.
- Open: endurance test under Freecess (Task 7) not run.
- Open: Telegram remote command matrix (Task 8) not run — requires the owner's Telegram chat.
- Open: Tasks 5-6 (device-aware model name, MIUI-only UI dead-ends, hardcoded `com.miui.calculator` default) not written — blocked on a decision about uninstall/reinstall.
- Open: unconfirmed whether the run screenshot actually arrived in Telegram; app reported success.
