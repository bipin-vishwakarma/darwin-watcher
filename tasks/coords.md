# Coordinate calibration — Samsung SM-M055F (Galaxy M05)

Measured 2026-08-17 from live `uiautomator dump` on the device. All values are physical pixels.

## Device reference

| Property | Value |
|---|---|
| Model | Samsung SM-M055F (Galaxy M05) |
| OS | One UI 8.0, Android 16 (API 36) |
| `wm size` | **720 x 1600** |
| `wm density` | 300 physical, **320 override** (override is what layouts use) |
| Prior device | Xiaomi Mi 11X, 1080 x 2400 (exactly 1.5x larger) |
| Target app | `com.darwinbox.darwinbox` |

## Screen 1 — `com.darwinbox.core.dashboard.ui.DashboardActivity`

Dashboard tiles are a uniform 3-column grid of `android.view.ViewGroup`, 240px wide:

| Tile | Bounds | Center |
|---|---|---|
| Task Box | `[0,596][240,864]` | (120,730) |
| **Attendance** | `[240,596][480,864]` | **(360,730)** |
| Leave | `[480,596][720,864]` | (600,730) |
| Reimbursement | `[0,874][240,1179]` | (120,1026) |
| Performance | `[240,874][480,1179]` | (360,1026) |
| Feedback | `[480,874][720,1179]` | (600,1026) |

**Danger zone — do not tap during testing:**

| Element | Bounds | Center |
|---|---|---|
| `id/checkInShortcut` (real Check In / Check Out) | `[32,432][688,570]` | (360,501) |

## Screen 2 — `com.darwinbox.attendance.ui.AttendanceHomeActivity`

Reached by tapping the Attendance tile. This screen is inside a `ScrollView`, so bounds below are valid **at scroll-top only**.

| Element | Bounds | Center | Clickable |
|---|---|---|---|
| Navigate up (back) | `[0,59][112,171]` | (56,115) | yes |
| **Check In** tab | `[360,171][720,267]` | (540,219) | yes — **punch path, avoid** |
| **Attendance view** | `[180,1096][540,1176]` | **(360,1136)** | yes — opens read-only log |
| Floating action button | `[576,1360][688,1472]` | (632,1416) | yes |

Tapping **Attendance view** opens `com.darwinbox.attendance.ui.MonthlyAttendanceActivity`, a read-only monthly log. Verified harmless — no attendance state is written.

## Verdict on the existing `test` profile

| Existing action | Lands in | Verdict |
|---|---|---|
| `tap 357 665` | `[240,596][480,864]` = Attendance tile | ✅ **correct** — inside the tile, 65px above center |
| `tap 416 1073` | `[360,854][688,1078]` = "Avg. Late By" stat cell, `clickable=false` | ❌ **dead tap** — 23px above the Attendance view button (`y` starts 1096) |

The coordinates were **not** MIUI leftovers. Tap 1 is correct for 720x1600, so these were authored on this device; tap 2 is simply a near-miss by 23px.

### The 1.5x rescale would have been dangerous

Had we assumed the coords were Mi 11X values and divided by 1.5:

- `357,665` → `238,443`
- `238,443` falls inside `checkInShortcut` `[32,432][688,570]` — **the real Check Out button**

A blind rescale would have punched attendance. Measure, never assume.

## Adopted `test` profile

Using tile/button **centers** rather than the original near-miss offsets, so small layout shifts stay tolerable:

```
tap 360 730
wait 2
wait 2
tap 360 1136
wait 2
wait 2
```

Expected activity trace: `DashboardActivity` → `AttendanceHomeActivity` → `MonthlyAttendanceActivity`.

## Notes for re-calibration on any future device

1. `adb shell wm size` and `wm density` first — record both densities.
2. `adb shell am start -n com.darwinbox.darwinbox/com.darwinbox.splashscreen.ui.SplashScreenActivity`
   Do **not** use `monkey -p <pkg> 1` — it injects one pseudo-random event after launch, which can press Back and silently take the app out of foreground.
3. `adb shell uiautomator dump`, pull it, and read `clickable='true'` node bounds. Tap the geometric center.
4. Confirm every candidate target by tapping it once and checking `topResumedActivity` before putting it in a profile.
5. Screen 2 scrolls — re-dump at scroll-top if the button y-values look shifted.
