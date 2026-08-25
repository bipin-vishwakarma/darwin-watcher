# Duplicate schedule runs + Telegram live view

Device: Samsung SM-M055F (Galaxy M05), One UI 8.0, Android 16 / API 36, 720x1600. Serial `R9ZY40E319D`.
Target app: `com.darwinbox.darwinbox`.

**Safety rule: all testing on the `test` profile.** The `Darwin` profile punches real attendance.
Never tap `[32,432][688,570]` (`id/checkInShortcut`) during testing.

Full detail in [plan.md](plan.md). Archived Samsung port plan: [plan-samsung-port.md](plan-samsung-port.md).

---

## Phase 0 — Safety net

- [x] **Task 1** — Snapshot device state before any install
  - [x] `shared_prefs/darwin_watcher.xml` pulled to scratchpad (**not** the repo — holds the live bot token)
  - [x] Armed alarms + next-fire times recorded for post-restore diff
  - [x] Both `Darwin` schedules confirmed `enabled:true` beforehand

---

## Phase 1 — Stop the double run (highest priority)

- [x] **Task 2** — A schedule runs at most once per active day
  - [x] `Prefs.scheduleLastRunDate` / `setScheduleLastRunDate`, per schedule id, `yyyy-MM-dd` local
  - [x] `AlarmReceiver` skips the run when already run today; still re-arms + heartbeat + self-heal
  - [x] `lastRunDate` stamped at dispatch, before `Runner.run()`
  - [x] `Runner.scheduleItem()` never arms a date already recorded as run (advance before the day-of-week loop at `Runner.java:152-157`)
  - [x] `isTest` broadcasts and UI Run Now bypass the gate entirely
  - [x] Saving a schedule from the UI for later today still arms today
  - [x] Jitter still applied to the next day's occurrence

  Root cause: `Runner.java:146-149`. On re-arm, `when` is rebuilt as **today**, a fresh jitter is rolled, and the day only advances `if (triggerTime <= now)`. Fires at 08:05, re-rolls to 08:15, 08:15 > 08:05 → second run today. Second path: `AlarmReceiver.java:30-40` re-arms all schedules on `BOOT_COMPLETED` with no memory of what ran.

### Checkpoint 1
- [x] Exactly one run observed from a throwaway `test` schedule
- [x] Re-armed alarm dated **tomorrow** (convert `origWhen` from `dumpsys alarm`) — this is the assertion that fails on today's build
- [~] Reboot inside the jitter window produces no run — *not tested directly*; the duplicate-`scheduleId` broadcast exercises the same `scheduleAlreadyRanToday` gate the boot path hits
- [x] Two back-to-back `isTest` broadcasts both run

---

## Phase 2 — Telegram live view

- [ ] **Task 3** — `/live` sends a frame that refreshes in place
  - [ ] Extract byte-returning capture from `ScreenshotCallbackHandler.onSuccess` (`:1275-1290`); `captureScreenshotAndSend` reuses it — one implementation, not two
  - [ ] Live frames downscaled/re-compressed; end-of-run screenshot stays full quality
  - [ ] `sendPhoto` parses and returns `result.message_id` (currently discarded)
  - [ ] New `editMessageMedia`, following the `PhotoSender` multipart pattern (`:255-360`)
  - [ ] `/live` posts first frame, stores `message_id`
  - [ ] 🔄 Refresh button edits the same message
  - [ ] Caption shows active profile, foreground package, timestamp
  - [ ] `/live` while live re-uses the session

- [ ] **Task 4** — Control the phone from the live view
  - [ ] Nav row wired to `triggerBack()` / `triggerHome()` / `triggerRecents()` (`:200-213`)
  - [ ] 3x4 tap grid, cell centres computed from `getDisplayMetrics()` — no hardcoded 720x1600
  - [ ] Every control action auto-refreshes the frame
  - [ ] `/tap x y` still available for precision
  - [ ] Keyboard JSON follows the pattern at `TelegramRemoteService.java:728-734`
  - [ ] Callbacks answered via `answerCallbackQuery` (`:86`) so no spinner

- [ ] **Task 5** — Auto-refresh with a hard stop
  - [ ] ▶️ Auto / ⏸ Pause toggle, interval floor **3 s**
  - [ ] Auto-stop after ~5 min or ~100 frames, announced in chat
  - [ ] ⏹ Stop button and `/live stop` both end immediately
  - [ ] Stops itself after repeated edit failures
  - [ ] Never runs concurrently with a `Runner` run
  - [ ] No thread left behind after stop

### Checkpoint 2
- [ ] `/live` usable end-to-end from the phone, no PC
- [ ] One live message in chat, not a flood
- [ ] No battery/thread leak after stop

---

## Phase 3 — Folded-in Samsung port leftovers

- [ ] **Task 6** — Device-aware naming and no MIUI dead-ends
  - [ ] `SM-M055F` → `Samsung Galaxy M05`; Xiaomi mappings (`DeviceUtils.java:100-105`) unchanged
  - [ ] `isSamsung()` / `isXiaomi()` helpers
  - [ ] MIUI keep-alive button (`MainActivity.java:968-971`) shows One UI checklist on Samsung
  - [ ] `openMiuiAutostart()` (`:1186-1189`) opens a real Samsung screen, fallback to app-details — never a dead tap
  - [ ] `Prefs.targetPackage()` (`Prefs.java:66`) resolves an installed calculator at runtime; Darwinbox prefs untouched

---

## Phase 4 — Build, install, restore

- [ ] **Task 7** — Rebuild and reinstall without losing configuration
  - [ ] `build.ps1` succeeds, `apksigner verify` passes, `targetSdkVersion` still **33**
  - [ ] Fresh prefs snapshot immediately before uninstall
  - [ ] `adb uninstall` → install new APK → restore prefs via `run-as` → `install.ps1`
  - [ ] Post-restore diff shows only intended differences
  - [ ] Both `Darwin` schedules re-armed at correct times; profiles + token + chat id present
  - [ ] `install.ps1` all-PASS

  Unavoidable because `build.ps1`'s generated keystore (`d8d94a0b…`) ≠ installed APK signer (`b1770bfb…`).

### Checkpoint 3
- [ ] Nothing lost — user re-enters no configuration
- [ ] Duplicate-run fix verified on the installed build
- [ ] Both features exercised on-device
- [ ] Results written to `tasks/test-results.md`

---

## Out of scope (decided)

- **Accidental touch protection** — user disabled it in Settings. Samsung pocket mode: `settings system screen_off_pocket=1`, `com.samsung.android.gesture.PocketProximityManager`, sensor `SIP3510_Proximity`. Disabling does not weaken touch; the feature exists to *block* touch.
- **Vibration to clear an obstruction** — not built. A vibration motor moves the phone ~1mm; it cannot shake an object off the screen. Vibrate-as-alert is available on request.
- **Real-time video (MediaProjection + HTTP server)** — accessibility capture caps at ~1 fps; real video needs MediaProjection (consent dialog every session) plus a hand-written HTTP server. `scrcpy` covers this whenever a PC is available.
