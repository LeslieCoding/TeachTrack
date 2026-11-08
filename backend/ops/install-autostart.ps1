$ErrorActionPreference = "Stop"
$backend = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$sid = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value
$principal = New-ScheduledTaskPrincipal -UserId $sid -LogonType Interactive -RunLevel Limited
$settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -RestartCount 3 -RestartInterval (New-TimeSpan -Minutes 1) -ExecutionTimeLimit ([TimeSpan]::Zero)
$settings.Hidden = $true
$settings.DisallowStartIfOnBatteries = $false
$settings.StopIfGoingOnBatteries = $false
$settings.MultipleInstances = "IgnoreNew"
$trigger = New-ScheduledTaskTrigger -AtLogOn -User $sid

$launcher = Join-Path $backend "ops\run-hidden.vbs"
if (-not (Test-Path $launcher)) { throw "Hidden launcher not found: $launcher" }
$wscript = Join-Path $env:SystemRoot "System32\wscript.exe"
$action = New-ScheduledTaskAction -Execute $wscript -Argument "`"$launcher`" backend" -WorkingDirectory $backend
Register-ScheduledTask -TaskName "TeachTrackBackend" -Action $action -Trigger $trigger -Principal $principal -Settings $settings -Description "TeachTrack local Java API and PostgreSQL" -Force | Out-Null

$cloudflared = "D:\SoftWare\cloudflared\cloudflared.exe"
$config = "D:\SoftWare\cloudflared\config.yml"
if (-not (Test-Path $cloudflared)) { throw "cloudflared not found: $cloudflared" }
if (-not (Test-Path $config)) { throw "Cloudflare config not found: $config" }
$tunnelAction = New-ScheduledTaskAction -Execute $wscript -Argument "`"$launcher`" tunnel" -WorkingDirectory $backend
Register-ScheduledTask -TaskName "TeachTrackTunnel" -Action $tunnelAction -Trigger $trigger -Principal $principal -Settings $settings -Description "TeachTrack fixed Cloudflare tunnel" -Force | Out-Null

$startup = [Environment]::GetFolderPath("Startup")
foreach ($name in @("TeachTrackBackend.lnk", "TeachTrackTunnel.lnk")) {
    $shortcut = Join-Path $startup $name
    if (Test-Path $shortcut) { Remove-Item $shortcut -Force }
}

$syncScript = Join-Path $backend "ops\sync-chatgpt-share.ps1"
$syncAction = New-ScheduledTaskAction -Execute "powershell.exe" `
    -Argument ('-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "{0}"' -f $syncScript) `
    -WorkingDirectory $backend
$syncTrigger = New-ScheduledTaskTrigger -Daily -At 06:17
$syncSettings = New-ScheduledTaskSettingsSet -StartWhenAvailable -RestartCount 3 `
    -RestartInterval (New-TimeSpan -Minutes 5) -ExecutionTimeLimit (New-TimeSpan -Hours 1)
$syncSettings.Hidden = $true
$syncSettings.DisallowStartIfOnBatteries = $false
$syncSettings.StopIfGoingOnBatteries = $false
Register-ScheduledTask -TaskName "TeachTrackChatGPTSync" -Action $syncAction -Trigger $syncTrigger `
    -Principal $principal -Settings $syncSettings `
    -Description "Sync the shared ChatGPT teaching feedback conversation into TeachTrack" -Force | Out-Null

Get-ScheduledTask -TaskName "TeachTrackBackend", "TeachTrackTunnel", "TeachTrackChatGPTSync" |
    Select-Object TaskName, State, @{ Name = "Hidden"; Expression = { $_.Settings.Hidden } }
