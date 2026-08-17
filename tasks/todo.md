# Darwin Watcher — MIUI → Samsung One UI 8 port

Device: Samsung SM-M055F (Galaxy M05), One UI 8.0, Android 16 / API 36, 720x1600, density 300/320 override.
Serial: `R9ZY40E319D` (USB). Target app: `com.darwinbox.darwinbox`.

**Safety rule for every task: `test` profile only.** The `Darwin` profile punches real attendance.

Full detail in [plan.md](plan.md).

---

## Phase 0 — Safety net

- [x] **Task 1** — Disarm production schedules, arm test instrumentation
  - [x] Back up `shared_prefs/darwin_watcher.xml` locally (scratchpad, **not** the repo — contains the bot token)
  - [x] Both `Darwin` schedules (08:10, 17:30) set `enabled:false`
  - [x] `telegram_enabled=true` (screenshot on completion)
  - [x] `current_profile_com_darwinbox_darwinbox=test` confirmed
  - [x] `dumpsys alarm` shows heartbeat (#9) only, no schedule alarms

  Alarms that were armed and are now cancelled: check-out **2026-08-17 17:29:02**, check-in **2026-08-18 08:11:26**.
  ⚠️ Today's auto check-out will not happen while disabled — user was warned; Darwinbox shows 17 Aug `10:02:32 → N.A.`.

### Checkpoint 0
- [x] Prefs backup exists
- [x] Zero schedule alarms armed, heartbeat still armed

---

## Phase 1 — Coordinate truth (the blocker)

- [x] **Task 2** — Derive real Darwinbox tap targets on 720x1600
  - [x] `uiautomator dump` for both screens the test profile traverses
  - [x] Mapping written to [coords.md](coords.md): element → bounds → center px
  - [x] Verdict: `tap 357 665` **correct**; `tap 416 1073` **dead tap**, 23px above the target
  - [x] Screen size + densities recorded (720x1600, density 300/320 override)

  Coords were **not** MIUI leftovers — tap 1 is correct for this screen, tap 2 was a 23px near-miss.
  A blind 1.5x rescale would have put tap 1 at `238,443`, **inside `checkInShortcut` `[32,432][688,570]`** — a real check-out punch. Measuring first was load-bearing.

- [x] **Task 3** — Fix test profile, prove one clean end-to-end run
  - [x] Profile now `tap 360 730` / `tap 360 1136` (measured centers)
  - [x] Taps land inside intended elements — proven by activity trace
  - [x] Warmup → steps → clean screenshot → auto-sleep observed
  - [x] Screenshot delivered (`last_run_status` = `Telegram screenshot sent!`) — *awaiting user confirmation it arrived in chat*
  - [x] No `Stopped:` status, no errors in logcat

  Triggered via the real alarm path (`am broadcast -n com.darwin.watcher/.AlarmReceiver --ez isTest true`), not the UI button.
  Activity trace: `Splash` 2s → `Dashboard` 4s → `AttendanceHome` 8s → `MonthlyAttendance` 12s → `NovaLauncher` 18s → screen OFF 20s.
  Cold-start baseline: target reached in ~4s, zero `WaitForTarget` retries.

### Checkpoint 1
- [x] One clean end-to-end run, activity trace + status as evidence
- [ ] **Review with user** — settles "does it work at all"

---

## Phase 2 — Samsung provisioning parity

- [x] **Task 4** — `install.ps1` device-aware and honest
  - [x] Branches on `ro.product.manufacturer` (samsung / xiaomi / generic)
  - [x] All adb calls routed through `Adb-Raw`/`Adb-Shell` so `-s $DeviceAddress` is never dropped
  - [x] Per-command PASS/FAIL/SKIP; `2>$null` suppression removed, stack traces truncated to 160 chars
  - [x] Samsung: best-effort `SYSTEM_EXEMPT_FROM_POWER_RESTRICTIONS`, bucket nudge only when needed
  - [x] MIUI appops `10008/10021/10022` gated to Xiaomi only
  - [x] Prints OEM-specific manual checklist
  - [x] 7-check end-state `Verify` section — asserts reality, not command output
  - [x] Idempotent: re-run all-PASS, "already present, unchanged", bucket untouched
  - [x] Aborts on signature mismatch / missing APK with actionable guidance

  **Three latent defects the old script was hiding behind `2>$null`:**
  1. `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` — not an appop at all (*"Unknown operation string"*). Never worked. Removed; the doze whitelist is the actual mechanism.
  2. `cmd package set-standby-bucket` — *"Unknown command"*. Correct form is `am set-standby-bucket`. Also: whitelist already yields bucket **5 (EXEMPTED)**, so forcing `active` (10) would have been a **downgrade** — now only nudges when bucket > 10.
  3. `settings put secure enabled_accessibility_services $service` **replaced** the whole list, wiping every other enabled accessibility service (Tasker/AutoInput, Nova, TalkBack). Now appends with `:`, matching `DeviceUtils.ensureAccessibilityEnabled()`.

  `SCHEDULE_EXACT_ALARM` is not pm-grantable on Android 16 (*"not a changeable permission type"*) — correctly SKIPped. `USE_EXACT_ALARM` is install-granted and verified, which is what actually matters.

- [ ] **Task 5** — Device-aware model naming (`DeviceUtils.java`)
  - [ ] `SM-M055F` → `Samsung Galaxy M05`
  - [ ] Xiaomi mappings unchanged
  - [ ] `isSamsung()` / `isXiaomi()` helpers exported

- [ ] **Task 6** — Replace MIUI-only UI dead-ends (`MainActivity.java`, `Prefs.java`)
  - [ ] Keep-alive guide branches by OEM
  - [ ] Autostart button opens a screen that exists on Samsung, with fallback
  - [ ] `Prefs.targetPackage()` default resolves an installed calculator at runtime
  - [ ] App-picker lists the calculator actually present
  - [ ] Existing Darwinbox prefs untouched

### Checkpoint 2
- [ ] `build.ps1` succeeds, `apksigner verify` passes
- [ ] Reinstall keeps accessibility bound and FGS running
- [ ] Task 3 run still passes after rebuild

---

## Phase 3 — Keep-alive endurance

- [ ] **Task 7** — Prove the 24/7 shield survives One UI Freecess
  - [ ] Heartbeat fires ≥2x across a ≥30 min screen-off idle window
  - [ ] No `Freecess … freeze com.darwin.watcher` in logcat
  - [ ] FGS still `isForeground=true`, wake lock still held
  - [ ] Telegram command answers instantly after idle
  - [ ] Self-heal proven: clear `enabled_accessibility_services`, heartbeat restores it

---

## Phase 4 — Functional matrix

- [ ] **Task 8** — Telegram remote command suite (test profile only)
  - [ ] `/menu` `/status` `/net` `/wake` `/sleep` `/home` `/back` `/recents` `/notifications`
  - [ ] `/screenshot` `/tap` `/swipe` `/ring` `/stopring` `/profiles` `/run` `/run in 60s`
  - [ ] `/profiles` never left on `Darwin`
  - [ ] Space `/screenshot` calls ≥1s (rate limit)

- [ ] **Task 9** — Locked-phone cold start
  - [ ] Screen wakes, keyguard dismissed by swipe gesture
  - [ ] Darwinbox cold-starts, 4s warmup counts down
  - [ ] No phantom taps on lock screen
  - [ ] Clean screenshot delivered, app closed, device re-locked
  - [ ] Retry count recorded as regression baseline

- [ ] **Task 10** — Boot persistence
  - [ ] Throwaway `test`-profile schedule armed a few minutes out
  - [ ] After reboot: accessibility on, FGS running, heartbeat re-armed
  - [ ] Schedule alarm re-registered from `BOOT_COMPLETED`
  - [ ] Telegram command works without opening the app

### Checkpoint 3
- [ ] `tasks/test-results.md` written, each row pass/fail with evidence
- [ ] Failures fixed or logged as known limitations

---

## Phase 5 — Restore

- [ ] **Task 11** — Re-arm production and finalize
  - [ ] Throwaway test schedule deleted
  - [ ] Both `Darwin` schedules re-enabled, matching the Task 1 backup
  - [ ] `dumpsys alarm` shows both armed at correct next-fire times
  - [ ] Day-to-day active profile set per user's choice (ask)
  - [ ] Final APK rebuilt + installed, `git status` reviewed, committed
  - [ ] `README.md` gains a Samsung / One UI provisioning section
