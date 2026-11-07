$ErrorActionPreference = "Stop"
$backend = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$cloudflared = if ($env:CLOUDFLARED) { $env:CLOUDFLARED } else { "D:\SoftWare\cloudflared\cloudflared.exe" }
$port = if ($env:PORT) { $env:PORT } else { "8080" }
$runtime = Join-Path $backend "data\runtime"
$outLog = Join-Path $runtime "tunnel.out.log"
$errLog = Join-Path $runtime "tunnel.err.log"
$urlFile = Join-Path $runtime "server-url.txt"

if (-not (Test-Path $cloudflared)) { throw "cloudflared not found: $cloudflared" }
New-Item -ItemType Directory -Force -Path $runtime | Out-Null
Set-Content -Path $outLog -Value "" -Encoding UTF8
Set-Content -Path $errLog -Value "" -Encoding UTF8

$process = Start-Process -FilePath $cloudflared `
    -ArgumentList @("tunnel", "--url", "http://127.0.0.1:$port", "--no-autoupdate") `
    -RedirectStandardOutput $outLog -RedirectStandardError $errLog `
    -PassThru -WindowStyle Hidden

$url = ""
for ($i = 0; $i -lt 60; $i++) {
    Start-Sleep -Seconds 1
    if ($process.HasExited) { break }
    $match = Select-String -Path $outLog, $errLog -Pattern "https://[a-zA-Z0-9.-]+trycloudflare\.com" -AllMatches -ErrorAction SilentlyContinue
    if ($match) { $url = $match.Matches[0].Value; break }
}

if (-not $url) {
    if (-not $process.HasExited) { Stop-Process -Id $process.Id }
    throw "Tunnel did not start; see $errLog"
}

Set-Content -Path $urlFile -Value $url -Encoding ASCII
Write-Host "TeachTrack HTTPS URL: $url"
Write-Host "The Quick Tunnel URL changes after a tunnel restart."
Wait-Process -Id $process.Id
