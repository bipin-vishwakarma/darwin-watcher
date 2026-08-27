param (
    [string]$DeviceAddress = ""
)

$Sdk = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } elseif ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "$env:LOCALAPPDATA\Android\Sdk" }
$Adb = Join-Path $Sdk 'platform-tools\adb.exe'
$Apk = Join-Path $PSScriptRoot 'build\darwin-watcher-debug.apk'

if (-not (Test-Path $Adb)) {
    $Adb = "adb"
}

$pkg = "com.darwin.watcher"
$service = "$pkg/$pkg.WatcherAccessibilityService"
$script:Failures = 0

# Every adb invocation goes through here so -s <device> is never forgotten.
# Device-side "2>&1" (not PowerShell-side) keeps stderr in the captured string
# without tripping PowerShell 5.1's NativeCommandError wrapping.
function Adb-Raw {
    param([string[]]$AdbArgs)
    $full = @()
    if ($DeviceAddress -ne "") { $full += @('-s', $DeviceAddress) }
    $full += $AdbArgs
    return ((& $Adb @full | Out-String).Trim())
}

function Adb-Shell {
    param([string]$Command)
    return (Adb-Raw @('shell', "$Command 2>&1"))
}

function Get-Prop {
    param([string]$Name)
    return (Adb-Shell "getprop $Name")
}

# Runs a provisioning command and reports what actually happened instead of
# swallowing it. Android CLI tools mostly stay silent on success, so treat
# recognisable error text as failure and anything else as applied.
function Step {
    param(
        [string]$Label,
        [string]$Command,
        [switch]$Optional
    )
    $out = Adb-Shell $Command
    $bad = $out -match 'Error|Exception|Failure|Unknown|No such|not found|Bad |Killed|Permission Denial|java\.lang'
    # Android throws full Java stack traces at us; keep the first line's worth.
    $brief = ($out -replace '\s+', ' ')
    if ($brief.Length -gt 160) { $brief = $brief.Substring(0, 160) + ' ...' }
    if ($bad) {
        if ($Optional) {
            Write-Host ("  SKIP  {0}" -f $Label) -ForegroundColor DarkGray
            if ($brief) { Write-Host ("          {0}" -f $brief) -ForegroundColor DarkGray }
        } else {
            Write-Host ("  FAIL  {0}" -f $Label) -ForegroundColor Red
            if ($brief) { Write-Host ("          {0}" -f $brief) -ForegroundColor Red }
            $script:Failures++
        }
    } else {
        Write-Host ("  PASS  {0}" -f $Label) -ForegroundColor Green
        if ($brief) { Write-Host ("          {0}" -f $brief) -ForegroundColor DarkGray }
    }
}

# End-state assertion. Provisioning output lies; this checks what is actually true.
function Verify {
    param(
        [string]$Label,
        [string]$Command,
        [string]$Expect
    )
    $out = Adb-Shell $Command
    if ($out -match $Expect) {
        Write-Host ("  OK    {0}" -f $Label) -ForegroundColor Green
        Write-Host ("          {0}" -f ($out -replace '\s+', ' ')) -ForegroundColor DarkGray
    } else {
        Write-Host ("  BAD   {0}" -f $Label) -ForegroundColor Red
        Write-Host ("          expected /{0}/, got: {1}" -f $Expect, ($out -replace '\s+', ' ')) -ForegroundColor Red
        $script:Failures++
    }
}

# ---------------------------------------------------------------- connect

if ($DeviceAddress -ne "") {
    Write-Host "[*] Connecting to $DeviceAddress..." -ForegroundColor Cyan
    & $Adb connect $DeviceAddress
}

$serial = Adb-Raw @('get-serialno')
if (-not $serial -or $serial -match 'error|unknown') {
    Write-Host "[!] No device. Attach over USB, or pair wireless debugging first:" -ForegroundColor Red
    Write-Host "      adb pair <ip>:<pairing-port>   (6-digit code from the phone)" -ForegroundColor Yellow
    Write-Host "    Then re-run with the address shown under 'IP address & Port'." -ForegroundColor Yellow
    exit 1
}

$manufacturer = Get-Prop 'ro.product.manufacturer'
$model = Get-Prop 'ro.product.model'
$release = Get-Prop 'ro.build.version.release'
$sdkInt = [int](Get-Prop 'ro.build.version.sdk')
$oneui = Get-Prop 'ro.build.version.oneui'

$isSamsung = $manufacturer -match '(?i)samsung'
$isXiaomi = $manufacturer -match '(?i)xiaomi|redmi|poco'

$oemLabel = if ($isSamsung) { if ($oneui) { "Samsung One UI $oneui" } else { "Samsung" } } elseif ($isXiaomi) { "Xiaomi / MIUI" } else { "Generic Android" }

Write-Host ""
Write-Host "[*] Device : $manufacturer $model ($serial)" -ForegroundColor Cyan
Write-Host "[*] OS     : Android $release (API $sdkInt) - $oemLabel" -ForegroundColor Cyan
Write-Host ""

# ---------------------------------------------------------------- install

if (-not (Test-Path $Apk)) {
    Write-Host "[!] APK missing: $Apk" -ForegroundColor Red
    Write-Host "    Build it first: powershell -ExecutionPolicy Bypass -File .\build.ps1" -ForegroundColor Yellow
    exit 1
}

Write-Host "[*] Installing $Apk..." -ForegroundColor Cyan
$installOut = Adb-Raw @('install', '-r', $Apk)
Write-Host ("    {0}" -f ($installOut -replace '\s+', ' '))

if ($installOut -match 'INSTALL_FAILED_UPDATE_INCOMPATIBLE|signatures do not match') {
    Write-Host "[!] Signature mismatch - the installed copy was signed with a different key." -ForegroundColor Red
    Write-Host "    A locally built APK uses your own debug keystore, not the committed one." -ForegroundColor Yellow
    Write-Host "    Uninstall first (this wipes schedules, profiles and Telegram settings):" -ForegroundColor Yellow
    Write-Host "      adb uninstall $pkg" -ForegroundColor Yellow
    exit 1
}
if ($installOut -notmatch 'Success') {
    Write-Host "[!] Install did not report Success - stopping before provisioning." -ForegroundColor Red
    exit 1
}

# ---------------------------------------------------------------- permissions

Write-Host ""
Write-Host "[*] Granting permissions..." -ForegroundColor Yellow
Step 'WRITE_SECURE_SETTINGS (self-healing accessibility)' "pm grant $pkg android.permission.WRITE_SECURE_SETTINGS"
Step 'POST_NOTIFICATIONS'      "pm grant $pkg android.permission.POST_NOTIFICATIONS"
Step 'ACCESS_FINE_LOCATION'    "pm grant $pkg android.permission.ACCESS_FINE_LOCATION"
Step 'ACCESS_COARSE_LOCATION'  "pm grant $pkg android.permission.ACCESS_COARSE_LOCATION"
Step 'NEARBY_WIFI_DEVICES'     "pm grant $pkg android.permission.NEARBY_WIFI_DEVICES"
# SCHEDULE_EXACT_ALARM is not grantable via pm on all builds; the manifest also
# declares USE_EXACT_ALARM, which is granted at install time and covers us.
Step 'SCHEDULE_EXACT_ALARM'    "pm grant $pkg android.permission.SCHEDULE_EXACT_ALARM" -Optional

Write-Host ""
Write-Host "[*] Battery / background exemptions..." -ForegroundColor Yellow
# The doze whitelist IS the battery-optimisation exemption, and it also lifts the
# app to standby bucket 5 (EXEMPTED). There is no
# REQUEST_IGNORE_BATTERY_OPTIMIZATIONS appop - the old script called one and the
# error was swallowed by 2>$null, so it never did anything.
Step 'Doze + battery optimisation whitelist' "dumpsys deviceidle whitelist +$pkg"
Step 'RUN_IN_BACKGROUND'                     "cmd appops set $pkg RUN_IN_BACKGROUND allow"
Step 'RUN_ANY_IN_BACKGROUND'                 "cmd appops set $pkg RUN_ANY_IN_BACKGROUND allow"
Step 'AUTO_REVOKE_PERMISSIONS_IF_UNUSED'     "cmd appops set $pkg AUTO_REVOKE_PERMISSIONS_IF_UNUSED ignore"
Step 'SYSTEM_ALERT_WINDOW (run watermark)'   "cmd appops set $pkg SYSTEM_ALERT_WINDOW allow"

# Buckets: 5 EXEMPTED (best) < 10 ACTIVE < 20 WORKING_SET < 30 < 40 < 45 RESTRICTED.
# Only nudge to ACTIVE if we are worse than that - forcing it when the whitelist
# already granted EXEMPTED would be a downgrade. Note: "am", not "cmd package".
$bucket = Adb-Shell "am get-standby-bucket $pkg"
if ($bucket -match '^\d+$' -and [int]$bucket -le 10) {
    Write-Host ("  PASS  App standby bucket already {0} (<=10, no change needed)" -f $bucket) -ForegroundColor Green
} else {
    Step 'App standby bucket -> active' "am set-standby-bucket $pkg active" -Optional
}

# ---------------------------------------------------------------- OEM branch

if ($isXiaomi) {
    Write-Host ""
    Write-Host "[*] Xiaomi / MIUI / HyperOS keep-alive appops..." -ForegroundColor Yellow
    Step 'MIUI 10008 Auto-start'          "cmd appops set $pkg 10008 allow"
    Step 'MIUI 10021 Show on lock screen' "cmd appops set $pkg 10021 allow"
    Step 'MIUI 10022 Background popups'   "cmd appops set $pkg 10022 allow"
}
elseif ($isSamsung) {
    Write-Host ""
    Write-Host "[*] Samsung / One UI keep-alive..." -ForegroundColor Yellow
    # One UI has no equivalent of the MIUI autostart appops. What ADB can reach is
    # the AOSP power-exemption surface; Freecess and "sleeping apps" are UI-only.
    Step 'SYSTEM_EXEMPT_FROM_POWER_RESTRICTIONS' "cmd appops set $pkg SYSTEM_EXEMPT_FROM_POWER_RESTRICTIONS allow" -Optional
    Write-Host "  NOTE  Samsung Freecess app-freezing cannot be disabled over ADB." -ForegroundColor DarkGray
    Write-Host "        The manual checklist below is required, not optional." -ForegroundColor DarkGray
}
else {
    Write-Host ""
    Write-Host "[*] No OEM-specific keep-alive for '$manufacturer' - AOSP exemptions only." -ForegroundColor DarkGray
}

# ---------------------------------------------------------------- accessibility

Write-Host ""
Write-Host "[*] Locking accessibility into Secure Settings..." -ForegroundColor Yellow

# APPEND, never replace. A bare "settings put" here wipes every other enabled
# accessibility service on the device (TalkBack, Tasker/AutoInput, launchers).
# Mirrors DeviceUtils.ensureAccessibilityEnabled(), which appends with ':'.
$currentA11y = Adb-Shell "settings get secure enabled_accessibility_services"
if ($currentA11y -eq 'null' -or $currentA11y -eq '') {
    $newA11y = $service
} elseif ($currentA11y.Split(':') -contains $service) {
    $newA11y = $currentA11y
} else {
    $newA11y = $currentA11y + ':' + $service
    Write-Host ("  KEEP  preserving already-enabled services: {0}" -f $currentA11y) -ForegroundColor DarkGray
}

if ($newA11y -eq $currentA11y) {
    Write-Host "  PASS  enabled_accessibility_services (already present, unchanged)" -ForegroundColor Green
} else {
    Step 'enabled_accessibility_services' "settings put secure enabled_accessibility_services $newA11y"
}
Step 'accessibility_enabled'          "settings put secure accessibility_enabled 1"

# ---------------------------------------------------------------- verify

Write-Host ""
Write-Host "[*] Verifying end state (this is what actually matters)..." -ForegroundColor Cyan
Verify 'WRITE_SECURE_SETTINGS granted' "dumpsys package $pkg | grep WRITE_SECURE_SETTINGS" 'granted=true'
Verify 'Accessibility service enabled' "settings get secure enabled_accessibility_services" ([regex]::Escape($service))
Verify 'Accessibility master switch'   "settings get secure accessibility_enabled" '^1$'
Verify 'Doze whitelist contains app'   "dumpsys deviceidle whitelist | grep $pkg" ([regex]::Escape($pkg))
Verify 'Standby bucket exempt/active'  "am get-standby-bucket $pkg" '^(5|10)$'
Verify 'Overlay appop allowed'         "cmd appops get $pkg SYSTEM_ALERT_WINDOW" 'allow'
# SCHEDULE_EXACT_ALARM is not pm-grantable, but USE_EXACT_ALARM is granted at
# install and is what actually lets the scheduler fire exact alarms.
Verify 'USE_EXACT_ALARM held'          "dumpsys package $pkg | grep USE_EXACT_ALARM" 'USE_EXACT_ALARM'

# ---------------------------------------------------------------- summary

Write-Host ""
if ($script:Failures -eq 0) {
    Write-Host "[+] Provisioning complete - all checks passed." -ForegroundColor Green
} else {
    Write-Host ("[!] Provisioning finished with {0} problem(s) above." -f $script:Failures) -ForegroundColor Red
}

if ($isSamsung) {
    Write-Host ""
    Write-Host "=== REQUIRED manual steps on Samsung (no ADB equivalent) ===" -ForegroundColor Yellow
    Write-Host "  1. Settings > Battery > Background usage limits" -ForegroundColor Yellow
    Write-Host "       - Never sleeping apps  -> ADD Darwin Watcher" -ForegroundColor Yellow
    Write-Host "       - Put unused apps to sleep -> OFF" -ForegroundColor Yellow
    Write-Host "  2. Settings > Battery > Optimise battery usage" -ForegroundColor Yellow
    Write-Host "       - Darwin Watcher -> not optimised" -ForegroundColor Yellow
    Write-Host "  3. Developer options > USB debugging (Security settings) -> ON" -ForegroundColor Yellow
    Write-Host "       (needed for WRITE_SECURE_SETTINGS after any factory reset)" -ForegroundColor Yellow
    Write-Host "  Also add the TARGET app to Never sleeping apps - Freecess freezes it too." -ForegroundColor Yellow
}
elseif ($isXiaomi) {
    Write-Host ""
    Write-Host "=== REQUIRED manual steps on MIUI ===" -ForegroundColor Yellow
    Write-Host "  1. Security > Permissions > Autostart -> enable Darwin Watcher" -ForegroundColor Yellow
    Write-Host "  2. Recents > lock the Darwin Watcher card" -ForegroundColor Yellow
    Write-Host "  3. Battery saver -> No restrictions" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "[*] Launching Darwin Watcher..." -ForegroundColor Green
# am start, not "monkey": monkey injects a pseudo-random event after launching,
# which can press Back and drop the app straight out of the foreground.
Adb-Shell "am start -n $pkg/.MainActivity" | Out-Null

if ($script:Failures -ne 0) { exit 1 }
