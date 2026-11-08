param(
    [switch]$Uninstall,
    [ValidateRange(5, 3600)]
    [int]$Interval = 60
)

$ErrorActionPreference = "Stop"
$TaskName = "TeachTrackAgentMailboxWatch"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$Script = Join-Path $Root "scripts\agent_mailbox.py"

if ($Uninstall) {
    $Existing = Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
    if ($Existing) {
        Stop-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
        Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false
    }
    Write-Output "已卸载 $TaskName"
    exit 0
}

$Python = (Get-Command python -ErrorAction Stop).Source
$Pythonw = Join-Path (Split-Path $Python) "pythonw.exe"
if (-not (Test-Path $Pythonw)) {
    throw "找不到 pythonw.exe：$Pythonw"
}

& $Python $Script --repo $Root watch --once
if ($LASTEXITCODE -ne 0) {
    throw "信箱自检失败，未安装后台任务"
}

$UserId = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
$Action = New-ScheduledTaskAction -Execute $Pythonw -Argument "`"$Script`" --repo `"$Root`" watch --interval $Interval" -WorkingDirectory $Root
$Trigger = New-ScheduledTaskTrigger -AtLogOn -User $UserId
$Settings = New-ScheduledTaskSettingsSet -Hidden -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable -RestartCount 3 -RestartInterval (New-TimeSpan -Minutes 1)
$Principal = New-ScheduledTaskPrincipal -UserId $UserId -LogonType Interactive -RunLevel Limited
Stop-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
Register-ScheduledTask -TaskName $TaskName -Action $Action -Trigger $Trigger -Settings $Settings -Principal $Principal -Description "TeachTrack agent mailbox silent background sync" -Force | Out-Null
Start-ScheduledTask -TaskName $TaskName
Start-Sleep -Seconds 3
$Task = Get-ScheduledTask -TaskName $TaskName
$Info = Get-ScheduledTaskInfo -TaskName $TaskName
[pscustomobject]@{
    TaskName = $TaskName
    State = [string]$Task.State
    LastTaskResult = $Info.LastTaskResult
    IntervalSeconds = $Interval
    Pythonw = $Pythonw
    Log = (Join-Path $Root ".agent-mailbox-watch.log")
} | ConvertTo-Json -Compress
