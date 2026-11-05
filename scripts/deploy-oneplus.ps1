param(
    [switch]$Build,
    [switch]$RunTests
)

$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
$adb = 'D:\Leslie\Tools\platform-tools\adb.exe'
$javaHome = 'D:\Leslie\Tools\root-pkg110\statusbar\jdk\jdk-17.0.20+8'
$serial = '92f73a16'
$package = 'com.leslie.teachtrack'
$apk = Join-Path $project 'app\build\outputs\apk\debug\app-debug.apk'

if (-not (Test-Path $adb)) { throw "ADB not found: $adb" }
if (-not (Test-Path $apk) -or $Build) {
    $env:JAVA_HOME = $javaHome
    Push-Location $project
    try { & .\gradlew.bat :app:assembleDebug --no-daemon; if ($LASTEXITCODE -ne 0) { throw 'Debug build failed' } }
    finally { Pop-Location }
}

$state = (& $adb -s $serial get-state 2>$null).Trim()
if ($state -ne 'device') { throw "OnePlus $serial is not available; deployment was not attempted." }

# ADB install is the silent USB-debug deployment path: no package-installer UI and no user click.
& $adb -s $serial install -r -d $apk
if ($LASTEXITCODE -ne 0) { throw 'Silent install failed' }
& $adb -s $serial shell am force-stop $package
& $adb -s $serial shell monkey -p $package 1 | Out-Host
if ($LASTEXITCODE -ne 0) { throw 'App launch failed' }

if ($RunTests) {
    Push-Location $project
    try {
        $env:JAVA_HOME = $javaHome
        & .\gradlew.bat :app:connectedDebugAndroidTest --no-daemon
        if ($LASTEXITCODE -ne 0) { throw 'OnePlus instrumentation failed' }
    }
    finally { Pop-Location }
}

Write-Output "Silent deployment complete: $package -> OnePlus/$serial"
