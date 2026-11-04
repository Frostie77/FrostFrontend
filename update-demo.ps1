# Update, build, and install FrostFrontend Demo on a connected Android device.
# Updates only io.github.frostfrontend.demo. Never uninstalls, clears app data,
# or changes the released Arcane app.
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

function Require-Success([string]$step) {
    if ($LASTEXITCODE -ne 0) {
        throw "$step failed (exit code $LASTEXITCODE). Demo APK not installed."
    }
}

$javaPath = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot'
if (-not (Test-Path (Join-Path $javaPath 'bin\java.exe'))) {
    throw "JDK 17 not found at $javaPath. Edit this script for your installed JDK."
}
$env:JAVA_HOME = $javaPath
$env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android\Sdk'

$gradle = Join-Path $HOME 'Downloads\Arcane-Armor-Collector-Android\.bootstrap\gradle-8.11.1\bin\gradle.bat'
$adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
$apk = Join-Path $PSScriptRoot 'demo\build\outputs\apk\debug\demo-debug.apk'

foreach ($file in @($gradle, $adb)) {
    if (-not (Test-Path $file)) { throw "Required tool not found: $file" }
}

Write-Host '[1/3] Pulling latest FrostFrontend source...'
& git pull --ff-only
Require-Success 'Git pull'

Write-Host '[2/3] Building FrostFrontend Demo...'
& $gradle :demo:assembleDebug
Require-Success 'Gradle build'

if (-not (Test-Path $apk)) { throw "Built APK missing: $apk" }
Write-Host '[3/3] Updating FrostFrontend Demo on your connected device...'
& $adb install -r $apk
Require-Success 'ADB install'

Write-Host 'PASS: FrostFrontend Demo updated in place. Its saved settings were not cleared.'
