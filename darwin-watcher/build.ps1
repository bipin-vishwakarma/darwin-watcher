$ErrorActionPreference = 'Stop'

function Invoke-Native {
    & $args[0] @($args[1..($args.Count - 1)])
    if ($LASTEXITCODE -ne 0) { throw "Command failed: $($args[0])" }
}

$Sdk = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } elseif ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "$env:LOCALAPPDATA\Android\Sdk" }
$BuildTools = Join-Path $Sdk 'build-tools\33.0.2'
$PlatformJar = Join-Path $Sdk 'platforms\android-33-ext5\android.jar'
$Root = $PSScriptRoot
$Build = Join-Path $Root 'build'
$Manifest = Join-Path $Root 'app\src\main\AndroidManifest.xml'
$Res = Join-Path $Root 'app\src\main\res'
$JavaRoot = Join-Path $Root 'app\src\main\java\com\darwin\watcher'
$Package = 'com.darwin.watcher'
$ApkUnsigned = Join-Path $Build 'app-unsigned.apk'
$ApkAligned = Join-Path $Build 'app-aligned.apk'
$ApkDebug = Join-Path $Build 'darwin-watcher-debug.apk'

Remove-Item -Recurse -Force $Build -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path "$Build\compiled", "$Build\gen", "$Build\classes", "$Build\dex" | Out-Null

Invoke-Native "$BuildTools\aapt2.exe" compile --dir $Res -o "$Build\compiled"
$flats = Get-ChildItem "$Build\compiled" -Filter '*.flat' | ForEach-Object { $_.FullName }
Invoke-Native "$BuildTools\aapt2.exe" link -o $ApkUnsigned -I $PlatformJar --manifest $Manifest --java "$Build\gen" --min-sdk-version 23 --target-sdk-version 33 --auto-add-overlay @flats

$rJava = Join-Path $Build 'gen\com\darwin\watcher\R.java'
$javaFiles = Get-ChildItem $JavaRoot -Filter '*.java' | ForEach-Object { $_.FullName }
Invoke-Native javac --release 8 -classpath $PlatformJar -d "$Build\classes" $rJava @javaFiles
$classFiles = Get-ChildItem "$Build\classes" -Filter '*.class' -Recurse | ForEach-Object { $_.FullName }
Invoke-Native java -cp "$BuildTools\lib\d8.jar" com.android.tools.r8.D8 --min-api 23 --lib $PlatformJar --output "$Build\dex" @classFiles

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::Open($ApkUnsigned, 'Update')
try {
    [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, (Join-Path $Build 'dex\classes.dex'), 'classes.dex') | Out-Null
} finally {
    $zip.Dispose()
}

$Keystore = Join-Path $env:TEMP 'darwin-watcher-debug.keystore'
if (-not (Test-Path $Keystore)) {
    Invoke-Native keytool -genkeypair -v -keystore $Keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Android Debug,O=Android,C=US'
}
Invoke-Native "$BuildTools\zipalign.exe" -f 4 $ApkUnsigned $ApkAligned
Invoke-Native "$BuildTools\apksigner.bat" sign --ks $Keystore --ks-pass pass:android --key-pass pass:android --out $ApkDebug $ApkAligned
Invoke-Native "$BuildTools\apksigner.bat" verify $ApkDebug

Write-Output "Built $ApkDebug"
Write-Output "Install: adb install -r `"$ApkDebug`""
Write-Output "Launch:  adb shell monkey -p $Package 1"
