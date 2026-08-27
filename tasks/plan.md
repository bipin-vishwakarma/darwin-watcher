# Make Darwin Watcher survive 5 days unattended

## Context — why this app keeps breaking

There is one root cause behind almost every failure this week, and it is in
`DeviceUtils.installGlobalCrashShield()`:

```java
final Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
    public void uncaughtException(Thread t, Throwable e) {
        Log.e(TAG, "⚡ CrashShield intercepted uncaught exception in thread " + t.getName(), e);
        // Do not let the app process terminate silently - keep services alive
    }
});
```

`defaultHandler` is captured and **never called**. When something throws on the main
thread, Android's `Looper.loop()` has already unwound — so the **main thread dies**, but
the process stays alive because the handler suppresses the kill.

What is left is a process that looks perfectly healthy and is half dead:

| Runs on | Survives a main-thread crash? |
|---|---|
| Telegram poller (`workerThread`) | **yes** — `/status`, `/net`, `/menu` keep replying |
| `AlarmReceiver.onReceive` (scheduled runs, 15-min watchdog, accessibility self-heal) | **no** — BroadcastReceivers run on main |
| `Handler.postDelayed` (every live-view capture and settle frame) | **no** |
| `takeScreenshot` callback (`getMainExecutor()`) | **no** |
| `Runner` automation | **no** |

So the bot answers you cheerfully while **attendance silently stops being punched**.
That is exactly the shape of every "it worked, then it just stopped" report.

It also explains this morning precisely: `cb_live` was received and processed by the
poller thread nine times, the wake lock was acquired — and nothing else happened, with no
error anywhere, because everything downstream needed the main thread. Reinstalling fixed
it because that restarted the process.

The individual bugs found this week (`GestureCallback` NPE on a null callback, rendering
on the UI thread, black frames from a sleeping display) were each *a* trigger. CrashShield
is what turned every trigger into a permanent, invisible outage.

**Goal:** the phone runs unattended for 5 days and punches attendance at 08:10 and 18:15
each working day, and any failure becomes visible instead of silent.

---

## Verified device facts (2026-08-27)

| Fact | Value | Relevance |
|---|---|---|
| Power | `AC powered: true`, level 68% | Must stay on the charger. Unplugged it drains ~13%/night |
| Uptime | 9 days 21 h | No spontaneous reboots; stable |
| Doze whitelist | `user,com.darwin.watcher,10316` | Exempt |
| Standby bucket | 5 (EXEMPTED) | Best tier |
| `RUN_ANY_IN_BACKGROUND` | allow | OK |
| Service restart policy | `START_STICKY` | **Android will restart the process if we let it die** |
| Alarms | held by AlarmManager, not the process | Survive process death |
| SIM | `ABSENT,ABSENT` | No mobile data — Wi-Fi is the only path to Telegram |
| Screen lock | disabled | No keyguard to defeat; keep it that way |

`START_STICKY` plus system-held alarms is what makes the fix below safe: dying is
recoverable, staying half-alive is not.

---

## Phase 0 — Baseline

### Task 1: Snapshot a known-good state
**Description:** Live view works right now. Capture exactly that state so any later step is revertible.

**Acceptance criteria:**
- [ ] `shared_prefs/darwin_watcher.xml` pulled to the scratchpad (**never the repo** — holds the bot token)
- [ ] Current APK pulled off the device (`adb shell pm path` → `adb pull`) as a rollback artefact
- [ ] Armed alarms and their next-fire times recorded
- [ ] `git rev-parse HEAD` recorded as the rollback commit

**Verification:** backup contains `telegram_token`, both schedules, all three profiles.

**Dependencies:** None. **Scope:** XS.

---

## Phase 1 — Stop silent death (the root fix)

### Task 2: A crash restarts the app instead of half-killing it
**Files:** `DeviceUtils.java`, plus the five `installGlobalCrashShield()` call sites.

**Description:** Make the handler log, record what happened, then **let the process die** so
`START_STICKY` and the pending alarms bring it back clean.

**Acceptance criteria:**
- [ ] `installGlobalCrashShield(Context)` takes a context (all five call sites already have one)
- [ ] On a **main-thread** exception: log, persist a crash record to prefs with `commit()`,
      then chain to the captured `defaultHandler` (falling back to
      `Process.killProcess(myPid())` + `System.exit(10)` if it is null)
- [ ] On a **background-thread** exception: log and persist, but do **not** kill the process —
      a dead poller thread is recoverable, a dead main thread is not
- [ ] The crash record holds thread name, exception class and message, and a timestamp
- [ ] Nothing is swallowed silently ever again

**Verification:**
- Force a main-thread crash (temporary debug broadcast, removed before shipping), confirm
  the process pid **changes** and the service comes back on its own.
- `dumpsys alarm` still shows both schedules armed after the restart.
- Confirm `pidof com.darwin.watcher` differs before/after.

**Dependencies:** Task 1. **Scope:** S.

### Task 3: Tell the owner it restarted
**Files:** `TelegramRemoteService.java`, `Prefs.java`

**Description:** On startup, if a crash record exists, send one Telegram line and clear it.

**Acceptance criteria:**
- [ ] Message names the thread and exception, e.g. `⚠️ Darwin Watcher restarted after a crash in main: NullPointerException`
- [ ] Sent once, then the record is cleared — no repeat on every service start
- [ ] Sent from the restarted process, **not** from the dying one, so delivery does not depend on a crashing thread surviving long enough
- [ ] Failure to send never blocks startup

**Verification:** trigger a crash, confirm exactly one Telegram message after the restart, and none on a subsequent normal start.

**Dependencies:** Task 2. **Scope:** XS.

### Task 4: Detect a wedged main thread from the thread that survives
**Files:** `TelegramRemoteService.java`

**Description:** The existing 15-min watchdog runs inside `AlarmReceiver.onReceive` — on the
main thread — so it cannot detect its own death. The poller thread is the one component
proven to survive, so the liveness check belongs there.

**Acceptance criteria:**
- [ ] Poller posts a no-op to the main `Handler` roughly every 5 minutes and records when it runs
- [ ] If the main thread has not run a ping for **~3 minutes past due**, treat it as dead:
      persist a crash record ("main thread unresponsive") and kill the process so it restarts
- [ ] Grace period after startup so a slow boot is not mistaken for death
- [ ] Adds no work to the main thread beyond an empty Runnable

**Verification:** with a deliberately wedged main thread, confirm the watchdog kills and
restarts within ~8 minutes and a Telegram alert follows.

**Dependencies:** Task 2. **Scope:** S.

### Checkpoint 1
- [ ] A main-thread crash now produces: new pid, alarms intact, one Telegram alert
- [ ] Live view still works exactly as it does today
- [ ] **This is the revert point** — if anything later misbehaves, roll back to here

---

## Phase 2 — Remove the frame race

### Task 5: One frame in flight at a time
**Files:** `LiveView.java`

**Description:** Today's log shows `HTTP 400: Bad Request: canceled by new edit message
request` — the 1.4 s and 4.2 s settle frames overlapping on the same message. The fallback
then posts a *new* message, which is why the message id jumped 310 → 311 and duplicates appear.

**Acceptance criteria:**
- [ ] A single in-flight guard; a frame arriving while a send is active is dropped, not queued
- [ ] The later settle frame still lands, so the final image is still the settled screen
- [ ] No `canceled by new edit message request` in logcat across ten actions
- [ ] Live view still ends with exactly one chat message per session

**Verification:** run ten mixed actions (zoom, TAP, Home, Back), grep logcat for HTTP 400, count messages in the chat.

**Dependencies:** Checkpoint 1. **Scope:** XS.

---

## Phase 3 — On-device hardening (must be done today, in person)

### Task 6: Samsung settings that ADB cannot reach
**Description:** These are UI-only. **This is the last chance to do them.**

**Acceptance criteria:**
- [ ] Settings → Battery → Background usage limits → **Never sleeping apps** → add **Darwin Watcher** *and* **Darwinbox**
- [ ] Same screen → **Put unused apps to sleep** → OFF
- [ ] Settings → Battery → **Optimise battery usage** → Darwin Watcher → **Not optimised**
- [ ] Device care → **Auto restart at set times** → OFF (a surprise reboot is survivable but pointless risk)
- [ ] Wi-Fi → UPESNET → **Auto reconnect** ON
- [ ] Screen lock left **disabled** (no keyguard to defeat)
- [ ] Phone left **on the charger** — non-negotiable; unplugged it loses ~13%/night
- [ ] Accessibility → Darwin Watcher still enabled

**Verification:** re-read `dumpsys deviceidle whitelist`, `am get-standby-bucket`, and
`settings get secure enabled_accessibility_services` afterwards; screenshot the Never
sleeping apps list as evidence.

**Dependencies:** None — can run in parallel with Phase 1. **Scope:** S (manual).

### Task 7: Close wireless ADB
**Acceptance criteria:**
- [ ] `adb -s <serial> usb` (or reboot) so port 5555 is no longer listening
- [ ] `adb connect 10.6.1.155:5555` refuses afterwards
- [ ] Re-enable procedure recorded in `HANDOFF.md`: plug in USB, run the **Phone Mirror (WiFi)** shortcut once

**Dependencies:** must be **last** — it removes my ability to verify anything else. **Scope:** XS.

---

## Phase 4 — Prove it before you leave

### Task 8: Full pre-departure verification
**Acceptance criteria:**
- [ ] `/live` from Telegram: frame arrives, zoom works, ✥ TAP works, frame returns after the tap
- [ ] `/status`, `/net`, `/menu` all reply
- [ ] One **`test`-profile** run end-to-end, screenshot delivered
- [ ] Active profile then set to **`Darwin`**, so an emergency `/run` from Telegram performs a real punch
- [ ] Both schedules armed: 08:10 ±5 and 18:15 ±2, Mon–Sat, profile `Darwin`
- [ ] Crash-restart path proven once (Task 2 verification), pid changed, alarms survived
- [ ] Watch today's **18:15 check-out actually fire** and its screenshot arrive — the last run observable in person

**Verification:** `dumpsys alarm` next-fire dates; the 18:15 screenshot in the chat.

**Dependencies:** Tasks 2–6. **Scope:** S.

### Checkpoint 2 — departure gate
- [ ] Every item in Task 8 green
- [ ] Phone on charger, on UPESNET, screen lock off
- [ ] Wireless ADB closed (Task 7 last)

---

## Phase 5 — What to do from 500 km away

### Task 9: Write the away runbook into `HANDOFF.md`
**Acceptance criteria:**
- [ ] **Healthy day** = two screenshots, ~08:10 and ~18:15
- [ ] **No screenshot?** send `/status` (is the listener alive?), then `/live` (is the main thread alive?). `/status` replying while `/live` does nothing is the classic half-dead signature — but with Task 2 shipped that should now self-restart instead
- [ ] **Missed a punch?** send `/run` — with the profile on `Darwin` this performs the real punch, late but recorded
- [ ] **Total silence?** the phone has lost Wi-Fi, lost power, or Freecess froze it. Nothing is fixable remotely without ADB; someone physically present must unplug/replug or reopen the app
- [ ] Note that a crash now sends `⚠️ Darwin Watcher restarted after a crash…` — receiving one is informative, not alarming

**Dependencies:** Checkpoint 2. **Scope:** XS.

---

## Risks

| Risk | Impact | Mitigation |
|---|---|---|
| A new build breaks today's working state | **High** | Task 1 keeps the working APK + prefs; `install -r` works in place; Checkpoint 1 is an explicit revert point |
| Darwinbox updates and moves `checkInShortcut` | **High** | Cannot be prevented remotely. The daily screenshot makes it visible; `/live` + `/tap` is the manual workaround |
| Phone unplugged or power cut | **High** | Task 6 leaves it charging; nothing else can be done remotely |
| Wi-Fi drops / UPESNET outage | **High** | Auto-reconnect on. No SIM means no fallback path — accepted limitation |
| Freecess freezes the app despite exemptions | Med | Task 6 Never-sleeping-apps is the real mitigation; watchdog restart covers the rest |
| Killing the process on crash causes a restart loop | Med | Only main-thread crashes kill; background ones just log. If a loop appeared it would be visible as repeated Telegram alerts |
| Emergency `/run` fires the wrong profile | Med | Profile set to `Darwin` deliberately and stated in the runbook, so `/run` means "punch attendance" |
| Testing accidentally punches real attendance | **High** | Every test uses the `test` profile, and **the profile is asserted before any run** — I broke this rule once today and will gate it in the script |

## Verification summary

```powershell
# health
adb shell "pidof com.darwin.watcher"
adb shell "dumpsys activity services com.darwin.watcher | grep -E 'ServiceRecord|isForeground'"
adb shell "dumpsys alarm | grep -A3 'Alarm{.*com.darwin.watcher'"
adb shell "ss -tn | grep 149.154"          # ESTAB = talking to Telegram

# live view instrumentation added today
adb logcat -d | grep LiveView
# expect: start() -> capture() -> got bitmap 720x1600 -> sending frame N -> frame delivered

# crash-restart proof: pid must CHANGE and alarms must survive
adb shell "pidof com.darwin.watcher"
```

No automated test suite exists; device verification is the test bed. Results go in
`tasks/test-results.md`.

**Ordering constraint:** Task 7 (closing wireless ADB) must be the final action — after it
I can no longer see or fix anything on that phone.
