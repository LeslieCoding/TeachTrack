$ErrorActionPreference = "Stop"
$backend = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$root = (Resolve-Path (Join-Path $backend "..")).Path
$runtime = Join-Path $backend "data\runtime"
$statusFile = Join-Path $runtime "chatgpt-share-sync.json"
$seedFile = Join-Path $backend "data\imports\chatgpt-share-seed.json"
$summaryFile = Join-Path $backend "data\imports\chatgpt-share-summary.json"
$envFile = Join-Path $backend ".env"
New-Item -ItemType Directory -Force -Path $runtime | Out-Null

$started = Get-Date
$proxy = if ($env:TEACHTRACK_SHARE_PROXY) { $env:TEACHTRACK_SHARE_PROXY } else { "http://127.0.0.1:10808" }
$previousProxy = $env:HTTPS_PROXY
try {
    $health = 0
    try {
        $health = (Invoke-WebRequest -UseBasicParsing -TimeoutSec 5 "http://127.0.0.1:8080/actuator/health").StatusCode
    } catch {
        $health = 0
    }
    if ($health -ne 200) {
        Start-ScheduledTask -TaskName "TeachTrackBackend"
        for ($i = 0; $i -lt 60; $i++) {
            Start-Sleep -Seconds 1
            try {
                $health = (Invoke-WebRequest -UseBasicParsing -TimeoutSec 3 "http://127.0.0.1:8080/actuator/health").StatusCode
            } catch {
                $health = 0
            }
            if ($health -eq 200) { break }
        }
    }
    if ($health -ne 200) { throw "TeachTrack backend is not healthy" }

    Push-Location $root
    try {
        $env:HTTPS_PROXY = $proxy
        python scripts/sync_chatgpt_share.py --out $seedFile --summary $summaryFile | Out-Null
        if ($LASTEXITCODE -ne 0) {
            $env:HTTPS_PROXY = ""
            python scripts/sync_chatgpt_share.py --out $seedFile --summary $summaryFile | Out-Null
        }
        if ($LASTEXITCODE -ne 0) { throw "ChatGPT share sync failed" }
    } finally {
        Pop-Location
    }

    $token = Get-Content $envFile | ForEach-Object {
        if ($_ -match '^TEACHTRACK_BOOTSTRAP_TOKEN=(.*)$') { $Matches[1].Trim() }
    } | Select-Object -First 1
    if ([string]::IsNullOrWhiteSpace($token)) { throw "TEACHTRACK_BOOTSTRAP_TOKEN missing" }

    $headers = @{ Authorization = "Bearer $token" }
    $body = @{ path = $seedFile; format = "SEED_V1"; dryRun = $false } | ConvertTo-Json
    $result = Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:8080/api/v1/admin/migrations" `
        -Headers $headers -ContentType "application/json" -Body $body -TimeoutSec 180
    $summary = Get-Content $summaryFile -Raw -Encoding UTF8 | ConvertFrom-Json
    $payload = [ordered]@{
        ok = $true
        startedAt = $started.ToString("o")
        finishedAt = (Get-Date).ToString("o")
        shareId = $summary.shareId
        feedbackRecords = $summary.feedbackRecords
        studentsWithFeedback = $summary.studentsWithFeedback
        chatgptCourses = $summary.chatgptCourses
        examAnalysis = $summary.examAnalysis
        import = $result
    }
    $payload | ConvertTo-Json -Depth 8 | Set-Content $statusFile -Encoding UTF8
    Write-Host "ChatGPT share synced: $($result.students) students, $($result.courses) courses"
} catch {
    [ordered]@{
        ok = $false
        startedAt = $started.ToString("o")
        finishedAt = (Get-Date).ToString("o")
        error = $_.Exception.Message
    } | ConvertTo-Json | Set-Content $statusFile -Encoding UTF8
    throw
} finally {
    $env:HTTPS_PROXY = $previousProxy
}
