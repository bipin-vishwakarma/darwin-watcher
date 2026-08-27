# Survive 5 days unattended

Root cause of the repeated breakage: `DeviceUtils.installGlobalCrashShield()` captures
Android's default crash handler and never calls it. A main-thread exception kills the
main thread but keeps the process alive — the Telegram poller (own thread) keeps
replying while alarms, the watchdog, accessibility self-heal, automation runs and every
live-view capture are silently dead.

Full detail in [plan.md](plan.md). Previous plans: [plan-liveview.md](plan-liveview.md),
[plan-samsung-port.md](plan-samsung-port.md).

**Safety: every test uses the `test` profile, and the profile is asserted before any run.**

---

## Phase 0 — Baseline
- [x] **Task 1** — Snapshot a known-good state
  - [x] prefs backed up to scratchpad (not the repo — holds the bot token)
  - [x] working APK pulled off the device as a rollback artefact
  - [x] armed alarms + next-fire times recorded
  - [x] rollback commit sha recorded

## Phase 1 — Stop silent death (root fix)
- [x] **Task 2** — A crash restarts the app instead of half-killing it
  - [x] `installGlobalCrashShield(Context)` takes a context
  - [x] main-thread crash: log, persist crash record with `commit()`, chain to defaultHandler (fallback `killProcess` + `System.exit(10)`)
  - [x] background-thread crash: log + persist, do **not** kill
  - [x] record holds thread, exception class, message, timestamp
  - [x] verified: pid **changes**, alarms survive, service returns on its own

- [x] **Task 3** — Tell the owner it restarted
  - [x] one Telegram line on next startup naming thread + exception
  - [x] sent from the restarted process, then record cleared
  - [x] never repeats on a normal start; send failure never blocks startup

- [x] **Task 4** — Detect a wedged main thread from the poller thread
  - [x] poller pings the main Handler every ~5 min and records when it runs
  - [x] ~3 min past due → persist record + kill so it restarts
  - [x] startup grace period; no extra main-thread work beyond an empty Runnable

### Checkpoint 1 — revert point
- [ ] main-thread crash → new pid + alarms intact + one Telegram alert
- [ ] live view still works exactly as it does today

## Phase 2 — Remove the frame race
- [x] **Task 5** — One frame in flight at a time
  - [x] in-flight guard; an overlapping frame replaces the waiting one (latest-wins,
        one slot) instead of queueing — the newest frame is always the most settled
  - [ ] no `canceled by new edit message request` across ten actions *(verified in Task 8)*
  - [ ] still exactly one chat message per session *(verified in Task 8)*

## Phase 3 — On-device hardening (in person, today)
- [x] **Task 6** — Samsung settings ADB cannot reach *(mostly done via ADB instead)*
  - [x] ~~Never sleeping apps~~ — unnecessary. Both apps are doze-whitelisted and in
        standby bucket **5 (EXEMPTED)**, which outranks the Never-sleeping-apps list.
        Darwin Watcher does not appear in the Samsung picker *because* it is already
        exempt; Darwinbox was added by ADB on 2026-08-27:
        `dumpsys deviceidle whitelist +com.darwinbox.darwinbox`
  - [x] ~~Put unused apps to sleep~~ — moot, both apps are EXEMPTED
  - [x] Optimise battery usage → Darwin Watcher → Not optimised (`user,com.darwin.watcher` in whitelist)
  - [x] Auto restart at set times — `auto_restart_days` unset; a reboot is survivable anyway
        (BOOT_COMPLETED reschedules, service is START_STICKY)
  - [x] Wi-Fi never sleeps (`wifi_sleep_policy=2`)
  - [x] screen lock left disabled
  - [ ] **phone left on the charger** — the one thing still on you
  - [x] accessibility still enabled (WatcherAccessibilityService bound)

- [ ] **Task 7** — Close wireless ADB (**must be last**)
  - [ ] `adb -s <serial> usb`; `adb connect 10.6.1.155:5555` refuses
  - [ ] re-enable procedure recorded in HANDOFF.md

## Phase 4 — Prove it before departure
- [ ] **Task 8** — Full pre-departure verification
  - [ ] `/live`: frame, zoom, ✥ TAP, frame returns after tap
  - [ ] `/status`, `/net`, `/menu` reply
  - [x] active profile is **`Darwin`** so emergency `/run` punches for real
  - [x] both schedules armed after the reinstall: 2026-08-27 18:16:41, 2026-08-28 08:14:38
        (plus the 15-min heartbeat), all held by AlarmManager
  - [x] crash-restart proven once — `am crash` at 11:31, pid 15148 -> 17200, alarms intact
  - [x] ~~one `test`-profile run~~ — skipped deliberately. Today's real 18:16 check-out
        is the same code path with a real outcome and is observed in person; a test run
        would touch the Darwinbox UI for no extra information.
  - [ ] today's 18:15 check-out observed firing

### Checkpoint 2 — departure gate
- [ ] Task 8 all green; phone charging, on UPESNET, lock off; wireless ADB closed last

## Phase 5 — Away runbook
- [x] **Task 9** — Write it into HANDOFF.md
  - [x] healthy day = two screenshots (~08:10, ~18:15)
  - [x] no screenshot → `/status` then `/live`
  - [x] missed punch → `/run` (profile is `Darwin`, so it punches for real)
  - [x] total silence → Wi-Fi, power or Freecess; needs someone physically present
  - [x] a crash now sends a restart alert — informative, not alarming
  - [x] device state as-left table, and how to re-enable wireless ADB
