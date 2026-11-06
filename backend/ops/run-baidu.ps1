$ErrorActionPreference = "Stop"
$backend = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$root = (Resolve-Path (Join-Path $backend "..")).Path
$envFile = Join-Path $backend ".env"

if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -eq "" -or $line.StartsWith("#")) { return }
        $parts = $line.Split("=", 2)
        if ($parts.Length -ne 2) { return }
        [Environment]::SetEnvironmentVariable($parts[0].Trim(), $parts[1].Trim(), "Process")
    }
}

$env:TEACHTRACK_STORAGE_PROVIDER = "baidu-pan"
if (-not $env:TEACHTRACK_BYPY) { $env:TEACHTRACK_BYPY = "bypy" }
if (-not $env:TEACHTRACK_BAIDU_ROOT) { $env:TEACHTRACK_BAIDU_ROOT = "teachtrack" }
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = "D:\SoftWare\jdk-21" }

Push-Location $backend
try {
    & (Join-Path $root "gradlew.bat") -p $backend bootRun --args="--spring.profiles.active=baidu"
} finally {
    Pop-Location
}
