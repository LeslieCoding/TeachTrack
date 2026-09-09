param([int]$IntervalMinutes = 30)
$targetSerial = '92f73a16'
$root = Split-Path -Parent $PSScriptRoot
$log = Join-Path $root 'logs\monitor.log'
New-Item -ItemType Directory -Force (Split-Path $log) | Out-Null
while ($true) {
    $time = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'
    $network = Test-Connection -ComputerName 'repo1.maven.org' -Count 1 -Quiet -ErrorAction SilentlyContinue
    $adb = 'D:\Leslie\Codex\tools\android-sdk\platform-tools\adb.exe'
    $device = if (Test-Path $adb) { (& $adb -s $targetSerial get-state 2>$null) -join '' } else { 'adb-unavailable' }
    Add-Content -LiteralPath $log -Value "$time | heartbeat | network=$network | device=$device | target=OnePlus/$targetSerial | action=continue-development-check"
    Start-Sleep -Seconds ($IntervalMinutes * 60)
}
