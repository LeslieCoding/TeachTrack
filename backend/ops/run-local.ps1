$ErrorActionPreference = "Stop"
$backend = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$envFile = Join-Path $backend ".env"
$pgBin = if ($env:PG_BIN) { $env:PG_BIN } else { "D:\SoftWare\pgsql-16\bin" }
$pgData = if ($env:PG_DATA) { $env:PG_DATA } else { "D:\SoftWare\pgdata-teachtrack" }

if (-not (Test-Path $envFile)) {
    throw "backend/.env not found. Copy .env.example and set the local server values."
}

Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -eq "" -or $line.StartsWith("#")) { return }
    $parts = $line.Split("=", 2)
    if ($parts.Length -ne 2) { return }
    [Environment]::SetEnvironmentVariable($parts[0].Trim(), $parts[1].Trim(), "Process")
}

$env:JAVA_HOME = if ($env:TEACHTRACK_JAVA_HOME) { $env:TEACHTRACK_JAVA_HOME } else { "D:\SoftWare\jdk-21" }
$pgCtl = Join-Path $pgBin "pg_ctl.exe"
if (-not (Test-Path $pgCtl)) { throw "pg_ctl not found: $pgCtl" }
if (-not (Test-Path $pgData)) { throw "PostgreSQL data directory not found: $pgData" }

& $pgCtl status -D $pgData | Out-Null
if ($LASTEXITCODE -ne 0) {
    New-Item -ItemType Directory -Force -Path (Join-Path $backend "data") | Out-Null
    & $pgCtl start -w -D $pgData -l (Join-Path $backend "data\postgresql.log") -o "-p 55432 -c listen_addresses=127.0.0.1"
    if ($LASTEXITCODE -ne 0) { throw "PostgreSQL failed to start" }
}

$jar = Get-ChildItem -Path (Join-Path $backend "build\libs") -Filter "teachtrack-backend-*.jar" -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $jar) { throw "Backend jar not found. Run: ./gradlew.bat -p backend bootJar" }
$java = Join-Path $env:JAVA_HOME "bin\java.exe"
if (-not (Test-Path $java)) { throw "Java 21 not found: $java" }

Push-Location $backend
try {
    & $java -jar $jar.FullName "--server.address=127.0.0.1" "--logging.file.name=$backend\data\backend.log"
    if ($LASTEXITCODE -ne 0) { throw "Backend exited with code $LASTEXITCODE" }
} finally {
    Pop-Location
}
