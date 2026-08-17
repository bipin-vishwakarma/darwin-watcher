# Port Darwin Watcher from MIUI to Samsung One UI 8 (Android 16)

## Context

Darwin Watcher was built, calibrated, and provisioned for a **Xiaomi Mi 11X (MIUI, Android 13, 1080x2400)**. It now needs to run identically on a **Samsung SM-M055F / Galaxy M05 (One UI 8.0, Android 16 / API 36, 720x1600)**.

The app is already installed and, contrary to expectation, most of the hard runtime plumbing already works on Android 16. The real gaps are narrower than a full port: **tap coordinates that were calibrated for a 1.5x larger screen**, **provisioning and UI affordances that only speak MIUI**, and **an unverified end-to-end run**.

Outcome: the `test` profile drives Darwinbox correctly on this phone, the 24/7 keep-alive survives One UI's Freecess app-freezing, and `install.ps1` provisions a Samsung device as completely as it did a Xiaomi one.

---

## Already verified working on this device — do not redo

Confirmed live over ADB this session:

| Item | State |
|---|---|
| APK install | `com.darwin.watcher`, uid 10266, pid 21846 |
| `WRITE_SECURE_SETTINGS` | `granted=true` |
| Accessibility service | bound; `accessibility_enabled=1`, service in `enabled_accessibility_services` |
| `TelegramRemoteService` | **running as foreground** (`isForeground=true`, id 1002, `types=0x0`) |
| Doze whitelist | `user,com.darwin.watcher,10266` |
| App standby bucket | `5` = EXEMPTED (best tier) |
| `SYSTEM_ALERT_WINDOW` | `allow` |
| Target app | `com.darwinbox.darwinbox` installed, launches `…SplashScreenActivity` |
| `test` profile | exists and is the **active** profile for Darwinbox |

The foreground service starting cleanly on Android 16 is load-bearing luck: `targetSdkVersion` is **33**, which exempts it from the API-34+ rule requiring `android:foregroundServiceType` plus a matching `FOREGROUND_SERVICE_*` permission. `TelegramRemoteService` declares no type (`AndroidManifest.xml:87-89`). **Do not raise `targetSdkVersion` in this work** — it would break the service. Noted for a future task, not this one.

`android:persistent="true"` (`AndroidManifest.xml:39`) is silently ignored for non-system apps. Harmless; not part of the keep-alive story despite appearances.

---

## Root problems to fix

1. **Coordinates.** `test` profile is `tap 357 665` / `tap 416 1073`. Screen is 720x1600, Mi 11X was 1080x2400 — exactly 1.5x. Both numbers are *in range* for either screen, so prefs alone can't tell us if they're stale. Must be settled empirically.
2. **`install.ps1` is MIUI-only.** Its three keep-alive appops (`10008` autostart, `10021` lockscreen, `10022` background popups) do not exist on One UI and failed silently. Worse, every `pm grant`/`appops`/`settings` call uses bare `& $Adb` with no `-s $DeviceAddress` (`install.ps1:34-56`) while install and launch do use it — wrong device gets provisioned when two are attached. All errors are swallowed by `2>$null`, so the script printed "provisioned successfully!" while doing nothing.
3. **Samsung Freecess actively freezes apps.** Logcat this session: `FreecessHandler: freeze com.darwinbox.darwinbox(10314) result : 2`, repeatedly. If it freezes the *target*, cold-start is unreliable; if it freezes the watcher, the listener dies.
4. **Hardcoded MIUI package.** `Prefs.targetPackage()` defaults to `com.miui.calculator` (`Prefs.java:66`), and `MainActivity.java:736-744` hardcodes it in the app-picker. Samsung's is `com.sec.android.app.popupcalculator`. Current prefs point at Darwinbox so this isn't blocking today, but any profile reset lands on a nonexistent app.
5. **MIUI-only UI dead-ends.** The "Xiaomi / MIUI 24/7 Keep-Alive Guide" button (`MainActivity.java:968-971`) and `openMiuiAutostart()` (`MainActivity.java:1186-1189`) target `com.miui.securitycenter`, absent on Samsung — the button does nothing. `DeviceUtils.getDeviceModelName()` (`DeviceUtils.java:100-105`) maps only Xiaomi codenames; this phone reports the raw "Samsung SM-M055F".
6. **No end-to-end proof.** No automated test suite exists in this repo — "tests" here means a device verification matrix, defined in Phase 5.

---

## Hard safety constraint

The `Darwin` profile performs the **real Darwinbox attendance punch**. Every step below uses the **`test` profile only**.

`AlarmReceiver.java:57-62` is the trap: a firing schedule calls `Prefs.setTargetApp` *and* `Prefs.setCurrentProfile(app, item.profile)`, so a scheduled run silently switches the active profile to `Darwin` and punches attendance. Both live schedules (08:10 check-in, 17:30 check-out, Mon–Sat, ±5min) match today. **Task 1 disables them first** and Task 11 restores them.

Before any run that dispatches taps, re-assert `current_profile_com_darwinbox_darwinbox = test`.

Also: `Runner.parse()` (`Runner.java:283-288`) rejects action text containing any of ~30 risky words (`checkin`, `attendance`, `login`, `otp`, …). Keep profile action text to bare `tap`/`wait`/`swipe` lines — no descriptive comments, they will throw.

---

## Phase 0 — Safety net and baseline

### Task 1: Disarm production schedules, arm test instrumentation
**Description:** Back up device prefs, disable both `Darwin` schedules, enable Telegram screenshot-on-completion, confirm `test` is active.

**Acceptance criteria:**
- [ ] `darwin_watcher.xml` copied to a local backup file before any change
- [ ] Both schedules show `"enabled":false`; no pending Darwin alarms remain
- [ ] `telegram_enabled=true`, `current_profile_com_darwinbox_darwinbox=test`

**Verification:** re-dump prefs via `run-as com.darwin.watcher cat …/shared_prefs/darwin_watcher.xml`; `adb shell dumpsys alarm | grep darwin` shows only the 15-min heartbeat (req code 8888), no schedule alarms.

**Dependencies:** None. **Scope:** XS (device state only, no repo files).

Do this through the **app UI** (Schedules tab toggles, Settings tab toggle), not by editing the prefs XML — editing prefs behind a running process gets overwritten on next `apply()`.

### Checkpoint 0
- [ ] Prefs backup exists locally
- [ ] Zero schedule alarms armed; heartbeat still armed
- [ ] Confirmed with the user before dispatching the first tap

---

## Phase 1 — Coordinate truth (the actual blocker)

### Task 2: Derive real Darwinbox tap targets on this screen
**Description:** Launch Darwinbox, dump the UI hierarchy, read actual node bounds for the two elements the `test` profile means to hit, and compare against 357,665 / 416,1073.

**Acceptance criteria:**
- [ ] `uiautomator dump` captured for the Darwinbox screen(s) the test profile traverses
- [ ] Written mapping: intended element → actual bounds → center px on 720x1600
- [ ] Explicit verdict per tap: correct as-is, or replace with X,Y
- [ ] `wm size` and both densities (300 physical / 320 override) recorded alongside, so the numbers are reproducible

**Verification:** overlay the two existing coords onto the dumped bounds; a coord inside the intended node's rect passes, outside fails. Capture `adb exec-out screencap` for the record.

**Dependencies:** Task 1. **Scope:** S (no repo files; produces `tasks/coords.md`).

Prediction to test, not assume: 1.5x downscale gives 238,443 and 277,715. Do not apply it blindly — density differs (300 vs 320 override), so the Darwinbox layout may not scale linearly.

### Task 3: Fix the test profile and prove one clean end-to-end run
**Description:** If Task 2 says the coords miss, update the `test` profile actions in the app UI. Then run via Run Now and verify every stage.

**Acceptance criteria:**
- [ ] Both taps land inside their intended Darwinbox elements
- [ ] Full pipeline observed: warmup countdown → each step → clean screenshot → auto-sleep/lock
- [ ] Screenshot arrives in Telegram
- [ ] `last_run_status` ends at `Task done` (not `Stopped: …`)

**Verification:** run with `adb logcat -s WatcherService DeviceUtils TelegramRemoteService` streaming; compare the delivered screenshot against the expected post-tap Darwinbox screen.

**Dependencies:** Task 2. **Scope:** S.

Verify through the **app's own accessibility gestures** (Run Now / `/run`), never `adb shell input tap` — `input tap` bypasses the exact `dispatchGesture` path that must be proven, and Samsung applies different touch filtering to injected vs accessibility events.

### Checkpoint 1
- [ ] One clean end-to-end `test` run, screenshot as evidence
- [ ] Coordinate decision recorded in `tasks/coords.md`
- [ ] **Review with user before proceeding** — this is the point where "does it work at all" is settled

---

## Phase 2 — Samsung provisioning parity

### Task 4: Make `install.ps1` device-aware and honest
**Description:** Branch on `ro.product.manufacturer`, run only applicable keep-alive commands, and report per-command PASS/FAIL instead of swallowing errors.

**Acceptance criteria:**
- [ ] Reads `ro.product.manufacturer` / `ro.build.version.sdk` and picks a Samsung or Xiaomi branch
- [ ] Every `pm grant` / `appops` / `settings` call passes `-s $DeviceAddress` when one was supplied (fixes the wrong-device bug)
- [ ] Per-command PASS/FAIL printed; `2>$null` blanket suppression removed
- [ ] Samsung branch adds `cmd package set-standby-bucket com.darwin.watcher active` and best-effort `cmd appops set … SYSTEM_EXEMPT_FROM_POWER_RESTRICTIONS allow` (API 34+; report FAIL without aborting)
- [ ] MIUI appops `10008/10021/10022` run only on Xiaomi
- [ ] Prints the Samsung manual checklist that ADB genuinely cannot cover (below)
- [ ] Re-running on this phone reports all-PASS and changes no working state

**Verification:** run the script, confirm every line PASSes; re-check `am get-standby-bucket` (expect exempted/active), doze whitelist, and `dumpsys package … granted=true` afterwards.

**Dependencies:** None (parallel with Phase 1). **Files:** `install.ps1`. **Scope:** S.

The Samsung manual checklist — no reliable ADB equivalent exists, so the script must print it rather than pretend:
- Settings → Battery → Background usage limits → **Never sleeping apps** → add Darwin Watcher
- Same screen → **Put unused apps to sleep**: OFF
- Settings → Battery → **Optimise battery usage** → Darwin Watcher: not optimised
- Developer options → **USB debugging (Security settings)**: ON (required for `pm grant WRITE_SECURE_SETTINGS` after a factory reset)

### Task 5: Device-aware model naming and OEM detection
**Description:** Add `isSamsung()` / `isXiaomi()` helpers to `DeviceUtils` and map this phone's codename to its market name.

**Acceptance criteria:**
- [ ] `SM-M055F` renders as `Samsung Galaxy M05`, not `Samsung SM-M055F`
- [ ] Existing Xiaomi mappings (`M2012K11AI`, `alioth`) still resolve unchanged
- [ ] OEM helpers exported for `MainActivity` to consume in Task 6

**Verification:** `/status` and `/net` over Telegram show the corrected name; Dashboard header matches.

**Dependencies:** None. **Files:** `DeviceUtils.java`. **Scope:** XS.

### Task 6: Replace MIUI-only UI dead-ends
**Description:** Make the keep-alive guide and autostart button branch by OEM, and stop hardcoding `com.miui.calculator`.

**Acceptance criteria:**
- [ ] On Samsung the guide shows the One UI checklist from Task 4; on Xiaomi it shows today's MIUI text verbatim
- [ ] The autostart button opens a screen that **exists** on Samsung (Device Care / battery settings), with a graceful fallback to app-details when the intent won't resolve — never a dead tap
- [ ] `Prefs.targetPackage()` default resolves an installed calculator at runtime instead of returning a hardcoded MIUI package; falls back safely when none is found
- [ ] App-picker lists the calculator actually present on the device
- [ ] Existing prefs pointing at Darwinbox are untouched by the default change

**Verification:** tap both buttons on-device, confirm the target screen opens; clear-data on a scratch profile and confirm the default target resolves to `com.sec.android.app.popupcalculator`, not a missing package.

**Dependencies:** Task 5. **Files:** `MainActivity.java`, `Prefs.java`. **Scope:** M.

Reuse what exists: `MainActivity` already guards overlay state via `Settings.canDrawOverlays` (`:393`, `:1363`) — follow that same try/fallback shape for the new intents rather than inventing a pattern.

### Checkpoint 2
- [ ] `build.ps1` succeeds; `apksigner verify` passes
- [ ] Reinstall keeps accessibility bound and the FGS running (`types=0x0`, still allowed at targetSdk 33)
- [ ] Task 3's end-to-end run still passes after the rebuild

---

## Phase 3 — Keep-alive endurance under Freecess

### Task 7: Prove the 24/7 shield survives One UI
**Description:** Confirm Freecess does not freeze `com.darwin.watcher`, and that the 15-minute watchdog heartbeat actually fires on this OS.

**Acceptance criteria:**
- [ ] Heartbeat observed firing at least twice while the device sits idle and screen-off (spans ≥30 min)
- [ ] No `FreecessHandler: freeze com.darwin.watcher` in logcat across the idle window
- [ ] `TelegramRemoteService` still `isForeground=true` afterwards; PARTIAL_WAKE_LOCK still held
- [ ] A Telegram command answers immediately after the idle window with no warm-up delay
- [ ] Self-heal proven: clear `enabled_accessibility_services`, confirm `DeviceUtils.ensureAccessibilityEnabled` rewrites it on the next heartbeat

**Verification:** `dumpsys alarm | grep darwin` before/after to see the heartbeat re-arm; `dumpsys activity services` for FGS state; grep the idle-window logcat for `Freecess`.

**Dependencies:** Checkpoint 2. **Scope:** S (device only).

The wake lock is acquired with a **10-minute timeout** (`TelegramRemoteService.java:87`) and, from the code read, is not visibly re-acquired on a timer — the 15-min heartbeat lands *after* it lapses. If the idle test shows the listener going deaf, that gap is the first suspect. Diagnose during this task; fix only if the test fails, and treat the fix as its own task.

---

## Phase 4 — Full functional matrix

### Task 8: Telegram remote command suite
**Description:** Exercise every remote command, `test` profile only.

**Acceptance criteria:** each responds correctly — `/menu` (inline keyboard renders), `/status`, `/net` (SSID + IPv4 + RSSI + RAM + storage), `/wake`, `/sleep`, `/home`, `/back`, `/recents`, `/notifications`, `/screenshot`, `/tap`, `/swipe`, `/ring` + `/stopring`, `/profiles`, `/run`, `/run in 60s`.
- [ ] Every command verified; failures logged with the exact reply received
- [ ] `/profiles` never leaves `Darwin` selected — reselect `test` if switched
- [ ] `/ring` reaches full volume and `/stopring` actually silences it

**Verification:** run each from the Telegram chat, capture replies; cross-check `/net` values against `adb shell dumpsys wifi` and `df`.

**Dependencies:** Checkpoint 2. **Scope:** S.

### Task 9: Locked-phone cold start
**Description:** The scenario the whole app exists for — screen off, keyguard on, remote `/run`.

**Acceptance criteria:**
- [ ] Screen wakes, keyguard dismissed via the swipe gesture (`WatcherAccessibilityService:161-191`, already resolution-relative — verify, don't change)
- [ ] Darwinbox cold-starts fresh, 4s warmup overlay counts down
- [ ] Taps land; **no phantom taps on the lock screen**
- [ ] Clean screenshot (no watermark) delivered, then app closed and device re-locked

**Verification:** `adb exec-out screencap` at each stage plus the delivered Telegram screenshot; confirm `input` events only after keyguard clears.

**Dependencies:** Task 3, Task 8. **Scope:** S.

If Freecess froze Darwinbox (seen this session), cold start may need a retry — `Runner.WaitForTarget` already re-opens at tries 2/5/8 with a 25-try ceiling (`Runner.java:377-388`). Record how many tries it actually took; that number is the regression baseline.

### Task 10: Boot persistence
**Description:** Reboot and confirm everything self-restores.

**Acceptance criteria:**
- [ ] After reboot: accessibility still enabled, FGS running, heartbeat re-armed
- [ ] Schedule alarms re-register from `BOOT_COMPLETED` (`AlarmReceiver.java:30-40`)
- [ ] A Telegram command works without opening the app first

**Verification:** `adb reboot`, wait for boot, then `dumpsys alarm`/`dumpsys activity services`/`settings get secure enabled_accessibility_services`, and one Telegram round-trip.

**Dependencies:** Task 7. **Scope:** XS.

Task 10 needs at least one schedule armed to prove re-registration. Use a **throwaway `test`-profile schedule** a few minutes out — do not re-enable the `Darwin` ones for this.

### Checkpoint 3
- [ ] Full matrix result table written to `tasks/test-results.md`, each row pass/fail with evidence
- [ ] Any failure either fixed or explicitly logged as a known limitation

---

## Phase 5 — Restore and hand back

### Task 11: Re-arm production and finalize
**Acceptance criteria:**
- [ ] Throwaway test schedule deleted
- [ ] Both `Darwin` schedules re-enabled with original times/days/tolerance, matching the Task 1 backup
- [ ] `dumpsys alarm` shows both armed at the right next-fire times
- [ ] Active profile deliberately set to whichever the user wants day-to-day (ask — `Darwin` for real punches, `test` if they want it idle)
- [ ] Final APK rebuilt and installed; `git status` reviewed and changes committed
- [ ] `README.md` gains a short Samsung/One UI provisioning section

**Verification:** diff live prefs against the Task 1 backup — only intended fields differ.

**Dependencies:** Checkpoint 3. **Scope:** S.

---

## Risks

| Risk | Impact | Mitigation |
|---|---|---|
| Accidentally running `Darwin` profile → false attendance punch in a real HR system | **High** | Schedules disabled in Task 1; assert `current_profile=test` before every run; never call `/profile Darwin` |
| Coords assumed rather than measured | High | Task 2 gates Task 3; verdict must cite dumped node bounds |
| Freecess freezes watcher or target | Med | Task 7 idle test; standby bucket + doze already exempt; manual "never sleeping apps" in checklist |
| Rebuild breaks the FGS by bumping targetSdk | Med | `targetSdkVersion` stays 33 this whole plan; Checkpoint 2 re-verifies `isForeground=true` |
| Re-signing breaks `install -r` | Med | Local debug keystore differs from the committed APK's — uninstall/reinstall if signature mismatch appears |
| `2>$null` masks a provisioning failure again | Med | Task 4 replaces it with explicit PASS/FAIL |
| Screenshot rate limit (1/sec) on repeated `/screenshot` | Low | Space calls ≥1s in Task 8 |
| Editing prefs XML under a live process | Low | All config changes go through the app UI |

## Note, outside scope

The Telegram bot token sits in plaintext in `shared_prefs/darwin_watcher.xml`, readable by ADB/backup tooling. Not part of this port and I'm not changing it — but if that phone is ever shared or handed on, rotate the token via `@BotFather`.

## Verification summary

- **Build:** `powershell -ExecutionPolicy Bypass -File .\build.ps1` — aapt2 → javac → d8 → zipalign → `apksigner verify`
- **Provision:** `powershell -ExecutionPolicy Bypass -File .\install.ps1` — expect all-PASS on Samsung
- **Runtime state:** `dumpsys activity services com.darwin.watcher`, `dumpsys alarm | grep darwin`, `am get-standby-bucket`, `settings get secure enabled_accessibility_services`
- **Behavioral:** the Phase 4 matrix, `test` profile only, evidence in `tasks/test-results.md`
- **No automated suite exists** in this repo, and this plan does not add one — device verification is the test bed. Say so plainly in the results file.

First action on approval: create `tasks/plan.md` (this document) and `tasks/todo.md` (the checklist), since plan mode blocked writing them.
