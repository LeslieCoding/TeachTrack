# Cloud profile launcher (PowerShell).
# Reads backend/.env into the process environment, then starts Spring Boot
# with the cloud profile. It never prints secret values.

$ErrorActionPreference = "Stop"
$backend = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$root = (Resolve-Path (Join-Path $backend "..")).Path
$envFile = Join-Path $backend ".env"

if (-not (Test-Path $envFile)) {
    throw "backend/.env not found. Copy .env.example to .env and fill the cloud connection values."
}

Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -eq "" -or $line.StartsWith("#")) { return }
    $parts = $line.Split("=", 2)
    if ($parts.Length -ne 2) { return }
    $name = $parts[0].Trim()
    $value = $parts[1].Trim()
    [Environment]::SetEnvironmentVariable($name, $value, "Process")
}

if (-not $env:JAVA_HOME) {
    $env:JAVA_HOME = "D:\SoftWare\jdk-21"
}

Write-Host "Starting TeachTrack backend with cloud profile..."
Push-Location $backend
try {
    & (Join-Path $root "gradlew.bat") -p $backend bootRun --args="--spring.profiles.active=cloud"
} finally {
    Pop-Location
}
