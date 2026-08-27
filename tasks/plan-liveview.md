# Fix duplicate schedule runs + add Telegram live view

## Context

Two requests and one bug, discovered while investigating them.

1. **Duplicate schedule runs (critical).** The 08:10 check-in fires twice, and because Darwinbox's check-in control is a toggle, the second run checks the user straight back out. Root cause found and confirmed in code — silently corrupts real attendance records. **This outranks everything else here.**
2. **Live screen view + control over Telegram.** Remote control already exists (`/tap`, `/swipe`, `/text`, `/home`, `/back`, `/screenshot`); what's missing is seeing the screen while doing it.
3. **Accidental touch protection — already resolved by the user**, no work needed. Details under "Resolved / out of scope".

Since installing any of this requires an uninstall + restore anyway (signature mismatch), the leftover Java fixes from the Samsung port are folded into the same build so the cost is paid once.

---

## The duplicate-run bug

**Where:** `Runner.scheduleItem()`, `Runner.java:131-162`, specifically the rollover guard at `:146-149`.

```java
Calendar when = Calendar.getInstance();      // <- TODAY at item.hour:item.minute
...
int randomOffsetSec = (int)((Math.random() * (rangeSec*2)) - rangeSec);   // fresh jitter every call
long triggerTime = when.getTimeInMillis() + jitterMillis;
if (triggerTime <= now) {                    // <- only rolls forward if ALREADY past
    when.add(Calendar.DAY_OF_MONTH, 1);
}
```

**Sequence:**
1. Alarm fires at 08:05 (jitter −5 on a 08:10 ±5 schedule).
2. `AlarmReceiver.java:63` calls `Runner.scheduleItem(app, item)` to re-arm.
3. `when` is rebuilt as **today** 08:10; a **new** jitter is rolled, say +5 → 08:15.
4. `08:15 <= 08:05` is false, so the day is never advanced.
5. Alarm re-arms for **today 08:15** → the schedule runs a second time.

Triggered whenever the new jitter lands later than the elapsed old one — about a coin flip at ±5 min, and it can chain until a roll finally lands in the past.

**Why it matters:** `id/checkInShortcut` on the Darwinbox dashboard is a single toggle (`[32,432][688,570]`, labelled "Check Out" once checked in). Run number two hits the same control and reverses run number one.

**Second path to the same fault:** `AlarmReceiver.java:30-40` re-arms every enabled schedule on `BOOT_COMPLETED`, with no memory of what already ran. A reboot at 08:07 re-arms today 08:1x and fires again.

**Chosen fix — per-schedule `lastRunDate`.** Record the date (`yyyy-MM-dd`) a schedule last actually ran; refuse to run or re-arm for a date already used. This covers the re-arm cascade, the reboot path, and any duplicate alarm delivery from the OS, in one mechanism.

Rejected: passing an `afterFire` boolean into `scheduleItem` to force tomorrow. Fewer lines, but it fixes only path one — the reboot and duplicate-delivery paths stay open.

---

## Phase 0 — Safety net

### Task 1: Snapshot device state before any install
**Description:** Capture prefs and current alarm state so the uninstall is reversible.

**Acceptance criteria:**
- [ ] `shared_prefs/darwin_watcher.xml` pulled to the session scratchpad (**never the repo** — it holds the live bot token)
- [ ] Current armed alarms and their next-fire times recorded, so post-restore state can be diffed
- [ ] Both `Darwin` schedules confirmed `enabled:true` before the change

**Verification:** backup file is non-empty and contains `telegram_token`, both schedule entries, and all profile action strings.

**Dependencies:** None. **Scope:** XS.

---

## Phase 1 — Stop the double run (highest priority)

### Task 2: A schedule runs at most once per active day
**Description:** Add `lastRunDate` per schedule and enforce it at both the run gate and the re-arm.

**Files:** `Prefs.java`, `AlarmReceiver.java`, `Runner.java`

**Acceptance criteria:**
- [ ] `Prefs` gains `scheduleLastRunDate(context, id)` / `setScheduleLastRunDate(context, id, date)`, keyed per schedule id, stored as `yyyy-MM-dd` in device-local time
- [ ] `AlarmReceiver` skips the run when `lastRunDate == today`, but still re-arms the next occurrence and still runs the heartbeat/self-heal steps
- [ ] `lastRunDate` is stamped at dispatch, before `Runner.run()`, so a crash mid-run cannot cause a repeat
- [ ] `Runner.scheduleItem()` never arms a trigger falling on a date already recorded as run — advance a day *before* the existing day-of-week loop at `Runner.java:152-157`
- [ ] Manual paths stay unaffected: `isTest` broadcasts and the UI Run Now button bypass the date gate entirely
- [ ] Saving a schedule from the UI for later the same day still arms today (only the fired/boot paths force forward)
- [ ] Jitter still applies to the next day's occurrence

**Verification:**
- Create a throwaway `test`-profile schedule with a ±5 min window, timed 1–2 min out, and let it fire. Observe **exactly one** run.
- Immediately read `dumpsys alarm | grep -B1 -A3 'Alarm{.*com.darwin.watcher'`, convert `origWhen` to a date, and confirm the re-armed occurrence is **tomorrow**, not later today. This is the exact assertion that fails on today's build.
- Reboot within the schedule's jitter window and confirm no run fires and the alarm still points at tomorrow.
- Fire `am broadcast -n com.darwin.watcher/.AlarmReceiver --ez isTest true` twice in a row and confirm both run — the gate must not block manual runs.

**Dependencies:** None. **Scope:** S (3 files, small edits).

### Checkpoint 1
- [ ] Exactly-once proven by observation, with the re-armed alarm dated tomorrow
- [ ] Manual runs still work
- [ ] **This alone is worth shipping** — if anything later goes wrong, this fix must survive

---

## Phase 2 — Telegram live view

Each task below is independently useful and independently verifiable; ship them in order.

### Task 3: `/live` sends a frame that refreshes in place
**Description:** New live mode that posts one screenshot and updates that same message instead of flooding the chat.

**Files:** `WatcherAccessibilityService.java`, `TelegramNotifier.java`, `TelegramRemoteService.java`

**Acceptance criteria:**
- [ ] `WatcherAccessibilityService` exposes a capture that hands back `byte[]` instead of sending directly. Extract it from the existing `ScreenshotCallbackHandler.onSuccess` (`:1275-1290`) — it already produces JPEG bytes at quality 85 — and make `captureScreenshotAndSend` call the same path so there is one capture implementation, not two
- [ ] Frames are downscaled and re-compressed for live use (a full 720x1600 JPEG per refresh is needlessly slow over mobile data); full quality stays for the end-of-run screenshot
- [ ] `TelegramNotifier.sendPhoto` parses `result.message_id` out of the API response and hands it back — currently the response is discarded
- [ ] New `TelegramNotifier.editMessageMedia(context, messageId, bytes, caption, replyMarkupJson)` following the existing multipart pattern in `PhotoSender` (`:255-360`)
- [ ] `/live` posts the first frame and stores its `message_id` in live-session state
- [ ] A **🔄 Refresh** inline button re-captures and edits the same message
- [ ] Caption carries useful state: active profile, foreground package, timestamp
- [ ] `/live` while already live re-uses the existing session rather than starting a second one

**Verification:** send `/live`, confirm a single chat message appears and Refresh updates that message in place with no new messages. Cross-check the frame against `adb exec-out screencap`.

**Dependencies:** Task 1. **Scope:** M.

### Task 4: Control the phone from the live view
**Description:** Inline keyboard so the screen can be driven without typing coordinates.

**Files:** `TelegramRemoteService.java`

**Acceptance criteria:**
- [ ] Navigation row wired to existing helpers: `triggerBack()`, `triggerHome()`, `triggerRecents()` (`WatcherAccessibilityService.java:200-213`)
- [ ] A coarse tap grid (3 columns x 4 rows over the screen) whose buttons dispatch `service.tap(x, y, null)` at each cell centre, computed from `getResources().getDisplayMetrics()` so it works on any resolution — do not hardcode 720x1600
- [ ] Every control action auto-refreshes the frame afterwards, so the result is visible without a second tap
- [ ] Precise taps still available via the existing `/tap x y`, using the live image to read coordinates
- [ ] Keyboard JSON built following the existing pattern at `TelegramRemoteService.java:728-734`
- [ ] Callback handlers answer the callback query so Telegram stops showing a spinner — reuse `TelegramNotifier.answerCallbackQuery` (`:86`)

**Verification:** from the chat, drive Darwinbox: Home → tap the Attendance cell → confirm the refreshed frame shows `AttendanceHomeActivity`. Confirm `dumpsys activity activities` agrees.

**Dependencies:** Task 3. **Scope:** M.

### Task 5: Auto-refresh with a hard stop
**Description:** Hands-free refresh, bounded so a forgotten session cannot drain the battery.

**Files:** `TelegramRemoteService.java`

**Acceptance criteria:**
- [ ] **▶️ Auto / ⏸ Pause** toggle; refresh interval no faster than **3 s** (the accessibility screenshot API is rate-limited to ~1/s, and Telegram edits are rate-limited per chat)
- [ ] Session auto-stops after a cap (~5 min or ~100 frames) and says so in the chat
- [ ] **⏹ Stop** button and `/live stop` both end it immediately
- [ ] Auto-refresh stops on its own if the Telegram edit fails repeatedly, rather than looping on errors
- [ ] Live mode never runs concurrently with an automation run — a `Runner` run takes precedence and pauses live refresh
- [ ] Runs on the existing poller thread pattern; no new always-on thread left behind after stop

**Verification:** start auto, leave it 6 minutes, confirm it self-stops with a message and no further edits. Confirm no lingering thread via `dumpsys activity services com.darwin.watcher`.

**Dependencies:** Task 4. **Scope:** S.

### Checkpoint 2
- [ ] `/live` usable end-to-end from the phone with no PC
- [ ] Chat contains one live message, not a flood
- [ ] No battery/thread leak after stop

---

## Phase 3 — Folded-in Samsung port leftovers

These were deferred last session solely because they needed a rebuild. The rebuild is happening anyway.

### Task 6: Device-aware naming and no MIUI dead-ends
**Description:** Carry over the unfinished Tasks 5-6 from `tasks/plan-samsung-port.md`.

**Files:** `DeviceUtils.java`, `MainActivity.java`, `Prefs.java`

**Acceptance criteria:**
- [ ] `SM-M055F` renders as `Samsung Galaxy M05`; existing Xiaomi codename mappings (`DeviceUtils.java:100-105`) unchanged
- [ ] `isSamsung()` / `isXiaomi()` helpers on `DeviceUtils`
- [ ] The "Xiaomi / MIUI 24/7 Keep-Alive Guide" button (`MainActivity.java:968-971`) shows the One UI checklist on Samsung; `openMiuiAutostart()` (`:1186-1189`) opens a screen that exists on Samsung, with a fallback to app-details so it is never a dead tap
- [ ] `Prefs.targetPackage()` (`Prefs.java:66`) resolves an installed calculator at runtime instead of hardcoding `com.miui.calculator`; existing Darwinbox prefs untouched

**Verification:** tap both buttons on-device and confirm a real screen opens; confirm the dashboard header reads `Galaxy M05`.

**Dependencies:** None. **Scope:** M.

---

## Phase 4 — Build, install, restore

### Task 7: Rebuild and reinstall without losing configuration
**Description:** The uninstall is unavoidable — `build.ps1` signs with a generated keystore (`d8d94a0b…`) that does not match the installed APK's signer (`b1770bfb…`), so `install -r` is refused.

**Acceptance criteria:**
- [ ] `build.ps1` succeeds; `apksigner verify` passes; `targetSdkVersion` still **33** (raising it breaks `TelegramRemoteService`, which declares no `foregroundServiceType`)
- [ ] Fresh prefs snapshot taken immediately before uninstall, in addition to Task 1's
- [ ] `adb uninstall com.darwin.watcher`, install the new APK, restore prefs via the `run-as` path, then run `install.ps1` for provisioning
- [ ] Post-restore diff against the backup shows only intended differences
- [ ] Both `Darwin` schedules re-armed at the correct next-fire times; profiles, Telegram token and chat id all present
- [ ] Accessibility bound, `TelegramRemoteService` foreground, doze whitelist and standby bucket restored (`install.ps1` reports all-PASS)

**Verification:** `install.ps1` all-PASS; `dumpsys alarm` shows both schedules plus heartbeat; one `isTest` broadcast completes end-to-end with a screenshot delivered.

**Dependencies:** Tasks 2, 5, 6. **Scope:** S (device work, no repo files).

### Checkpoint 3
- [ ] Nothing lost — user re-enters no configuration
- [ ] Duplicate-run fix verified on the installed build, not just the dev machine
- [ ] Both features exercised once on-device

---

## Resolved / out of scope

**Accidental touch protection — fixed by the user in Settings, no code needed.** For the record, this is Samsung pocket mode: `settings system screen_off_pocket=1` (also `proximity_sensor=1`), driven by `com.samsung.android.gesture.PocketProximityManager` against the `SIP3510_Proximity` sensor. Disabling it does not weaken touch — the feature exists to *block* touch when the sensor is covered, so switching it off makes automation more reliable, at the cost of possible pocket-dials.

**Vibration to clear an obstruction — not built, because it would not work.** A phone's vibration motor produces roughly a millimetre of buzz; it will not shake an object off the screen or move the phone out from under something. The honest version of the idea is vibration as an *alert* ("come clear the phone"), which is only worth adding if pocket mode is ever re-enabled. Flagged here rather than silently dropped — say so if you want it anyway.

**Real-time video (MediaProjection + local HTTP server).** Rejected for now: the accessibility screenshot path caps at ~1 fps, so genuine video means MediaProjection, which needs a consent dialog every session, plus a hand-written HTTP server since the build vendors no libraries. Large effort for a capability `scrcpy` already provides whenever a PC is available.

---

## Risks

| Risk | Impact | Mitigation |
|---|---|---|
| Uninstall loses schedules, profiles, bot token | **High** | Two snapshots (Task 1 and Task 7); restore path already proven repeatedly; post-restore diff against backup |
| Duplicate-run fix wrong → schedule stops firing entirely | **High** | Verify with a throwaway schedule *and* confirm the re-armed alarm is dated tomorrow; never test on the `Darwin` profile |
| `/live` streams whatever is on screen into a Telegram chat | Med | Document it; live mode never auto-starts; it is user-initiated and self-stopping |
| Telegram edit rate limits during auto-refresh | Med | Floor the interval at 3 s; stop after repeated edit failures |
| Battery drain from a forgotten live session | Med | Hard cap on frames/duration with a chat notice |
| Raising `targetSdk` during the rebuild breaks the 24/7 listener | Med | Stays at 33; Checkpoint 3 re-verifies `isForeground=true` |
| Testing accidentally runs the `Darwin` profile and punches attendance | **High** | All testing on the `test` profile; assert active profile before any run; `checkInShortcut` is at `[32,432][688,570]` — never tap there |

## Verification summary

```powershell
# Build and install
powershell -ExecutionPolicy Bypass -File .\build.ps1
adb uninstall com.darwin.watcher
adb install -r .\build\darwin-watcher-debug.apk
powershell -ExecutionPolicy Bypass -File .\install.ps1

# The assertion that fails on today's build: next occurrence must be TOMORROW
adb shell "dumpsys alarm | grep -B1 -A3 'Alarm{.*com.darwin.watcher'"

# Manual run must still work despite the once-per-day gate
adb shell "am broadcast -n com.darwin.watcher/.AlarmReceiver --ez isTest true"
adb shell "dumpsys activity activities | grep topResumedActivity"
```

No automated test suite exists in this repo and this plan does not add one; device verification is the test bed. Results go in `tasks/test-results.md`.

**Note on plan files:** `tasks/plan.md` and `tasks/todo.md` currently hold the Samsung port plan (committed in PR #1). The port plan will be archived to `tasks/plan-samsung-port.md` — with its still-open items carried into Phase 3 above — so nothing is lost when the new plan takes those filenames.
