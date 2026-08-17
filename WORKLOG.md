# Work log

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
