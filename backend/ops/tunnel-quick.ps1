$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$cloudflared = if ($env:CLOUDFLARED) { $env:CLOUDFLARED } else { "D:\SoftWare\cloudflared.exe" }
$port = if ($env:PORT) { $env:PORT } else { "8080" }
$log = Join-Path $root "backend\cloudflared-quick.log"
$outLog = Join-Path $root "backend\cloudflared-quick.out.log"
$errLog = Join-Path $root "backend\cloudflared-quick.err.log"

if (-not (Test-Path $cloudflared)) { throw "cloudflared not found: $cloudflared" }
Set-Content -Path $outLog -Value "" -Encoding UTF8
Set-Content -Path $errLog -Value "" -Encoding UTF8

$process = Start-Process -FilePath $cloudflared `
    -ArgumentList @("tunnel", "--url", "http://127.0.0.1:$port", "--no-autoupdate") `
    -RedirectStandardOutput $outLog -RedirectStandardError $errLog `
    -PassThru -WindowStyle Hidden

$url = ""
for ($i = 0; $i -lt 30; $i++) {
    Start-Sleep -Seconds 1
    $match = Select-String -Path $outLog, $errLog -Pattern "https://[a-zA-Z0-9.-]+trycloudflare\.com" -AllMatches -ErrorAction SilentlyContinue
    if ($match) { $url = $match.Matches[0].Value; break }
}

if (-not $url) { throw "tunnel did not start; see $log" }

Write-Host "temporary HTTPS URL: $url"
Write-Host "backend: http://127.0.0.1:$port"
Write-Host "pid: $($process.Id)"
Write-Host "stop: Stop-Process -Id $($process.Id)"
Write-Host "note: URL changes on every restart; do not use for permanent production"
