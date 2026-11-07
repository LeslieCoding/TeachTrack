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

$tasks = @(
    @{ Name = "TeachTrackBackend"; Script = "run-local.ps1"; Description = "TeachTrack local Java API and PostgreSQL" },
    @{ Name = "TeachTrackTunnel"; Script = "tunnel-quick.ps1"; Description = "TeachTrack free HTTPS Quick Tunnel" }
)
foreach ($task in $tasks) {
    $script = Join-Path $backend ("ops\" + $task.Script)
    $action = New-ScheduledTaskAction -Execute "powershell.exe" -Argument "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `\"$script`\"" -WorkingDirectory $backend
    Register-ScheduledTask -TaskName $task.Name -Action $action -Trigger $trigger -Principal $principal -Settings $settings -Description $task.Description -Force | Out-Null
}

$startup = [Environment]::GetFolderPath("Startup")
foreach ($name in @("TeachTrackBackend.lnk", "TeachTrackTunnel.lnk")) {
    $path = Join-Path $startup $name
    if (Test-Path $path) { Remove-Item $path -Force }
}

Get-ScheduledTask -TaskName "TeachTrackBackend", "TeachTrackTunnel" |
    Select-Object TaskName, State, @{ Name = "Hidden"; Expression = { $_.Settings.Hidden } }
