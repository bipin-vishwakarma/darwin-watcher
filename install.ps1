param (
    [string]$DeviceAddress = ""
)

$Sdk = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } elseif ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "$env:LOCALAPPDATA\Android\Sdk" }
$Adb = Join-Path $Sdk 'platform-tools\adb.exe'
$Apk = Join-Path $PSScriptRoot 'build\darwin-watcher-debug.apk'

if (-not (Test-Path $Adb)) {
    $Adb = "adb"
}

if ($DeviceAddress -ne "") {
    Write-Host "[*] Connecting to $DeviceAddress..." -ForegroundColor Cyan
    & $Adb connect $DeviceAddress
    $targetDevice = "-s $DeviceAddress"
} else {
    $targetDevice = ""
}

Write-Host "[*] Installing $Apk..." -ForegroundColor Cyan
if ($DeviceAddress -ne "") {
    & $Adb -s $DeviceAddress install -r $Apk
} else {
    & $Adb install -r $Apk
}

Write-Host "[*] Locking 24/7 Permissions & Whitelisting OS Battery Optimization..." -ForegroundColor Yellow

$pkg = "com.darwin.watcher"
$service = "$pkg/$pkg.WatcherAccessibilityService"

# Grant sensitive secure settings and location permissions for WiFi identification
& $Adb shell pm grant $pkg android.permission.WRITE_SECURE_SETTINGS 2>$null
& $Adb shell pm grant $pkg android.permission.POST_NOTIFICATIONS 2>$null
& $Adb shell pm grant $pkg android.permission.SCHEDULE_EXACT_ALARM 2>$null
& $Adb shell pm grant $pkg android.permission.ACCESS_FINE_LOCATION 2>$null
& $Adb shell pm grant $pkg android.permission.ACCESS_COARSE_LOCATION 2>$null
& $Adb shell pm grant $pkg android.permission.NEARBY_WIFI_DEVICES 2>$null

# Whitelist from Android Doze & Battery Optimization
& $Adb shell dumpsys deviceidle whitelist +$pkg 2>$null
& $Adb shell cmd appops set $pkg REQUEST_IGNORE_BATTERY_OPTIMIZATIONS allow 2>$null
& $Adb shell cmd appops set $pkg RUN_IN_BACKGROUND allow 2>$null
& $Adb shell cmd appops set $pkg RUN_ANY_IN_BACKGROUND allow 2>$null
& $Adb shell cmd appops set $pkg AUTO_REVOKE_PERMISSIONS_IF_UNUSED ignore 2>$null
& $Adb shell cmd appops set $pkg SYSTEM_ALERT_WINDOW allow 2>$null

# MIUI / Xiaomi & HyperOS Keep-Alive AppOps (10008 = Auto-start, 10021 = Show on Lock Screen, 10022 = Background Popups)
& $Adb shell cmd appops set $pkg 10008 allow 2>$null
& $Adb shell cmd appops set $pkg 10021 allow 2>$null
& $Adb shell cmd appops set $pkg 10022 allow 2>$null

# Lock Accessibility Service into Android Secure Settings
& $Adb shell settings put secure accessibility_enabled 1 2>$null
& $Adb shell settings put secure enabled_accessibility_services $service 2>$null

Write-Host "[*] 24/7 Lock & Self-Healing Shield provisioned successfully!" -ForegroundColor Green

Write-Host "[*] Launching Darwin Watcher..." -ForegroundColor Green
if ($DeviceAddress -ne "") {
    & $Adb -s $DeviceAddress shell monkey -p $pkg 1
} else {
    & $Adb shell monkey -p $pkg 1
}
