param(
    [ValidateSet("Benchmark", "Compatibility", "Layered")]
    [string]$Mode = "Benchmark",
    [string]$Serial = "3f8bbaad",
    [string]$AdbPath = "D:\KF\Android\Sdk\platform-tools\adb.exe",
    [int]$TimeoutSeconds = 180,
    [string]$OutputPath = "local-artifacts\python-runtime-benchmark.log"
)

$ErrorActionPreference = "Stop"
$benchmarkRepoRoot = Split-Path -Parent $PSScriptRoot
$artifactPath = Join-Path $benchmarkRepoRoot $OutputPath
$action = switch ($Mode) {
    "Compatibility" { "com.kite.app.debug.PYTHON_RUNTIME_COMPATIBILITY" }
    "Layered" { "com.kite.app.debug.PYTHON_RUNTIME_LAYERED" }
    default { "com.kite.app.debug.PYTHON_RUNTIME_BENCHMARK" }
}
$completion = switch ($Mode) {
    "Compatibility" { "status=compatibility_complete" }
    "Layered" { "status=layered_complete" }
    default { "status=complete" }
}
$tag = "[KFShell]PythonRuntimeBenchmark"

if (-not (Test-Path -LiteralPath $AdbPath)) { throw "ADB 不存在：$AdbPath" }
if ($TimeoutSeconds -lt 10 -or $TimeoutSeconds -gt 900) { throw "TimeoutSeconds 必须在 10..900" }

& $AdbPath -s $Serial get-state | Out-Null
if ($LASTEXITCODE -ne 0) { throw "设备不可用：$Serial" }

# Android 不允许后台广播直接启动普通 Service；先把 Debug App 带到前台。
& $AdbPath -s $Serial shell monkey -p com.kite.app -c android.intent.category.LAUNCHER 1 | Out-Null
if ($LASTEXITCODE -ne 0) { throw "无法启动 com.kite.app" }
Start-Sleep -Seconds 2
& $AdbPath -s $Serial logcat -c
& $AdbPath -s $Serial shell am broadcast -a $action -p com.kite.app | Out-Null
if ($LASTEXITCODE -ne 0) { throw "无法触发 Python $Mode 矩阵" }

$deadline = [DateTimeOffset]::UtcNow.AddSeconds($TimeoutSeconds)
$lines = @()
do {
    Start-Sleep -Seconds 1
    $logcat = & $AdbPath -s $Serial logcat -d -v tag
    $lines = @($logcat | Select-String -SimpleMatch $tag | ForEach-Object { $_.Line })
    if ($lines | Select-String -SimpleMatch "status=trigger_rejected") {
        throw "Python $Mode 入口被 Android 拒绝：请确认 Debug App 位于前台"
    }
    if ($lines | Select-String -SimpleMatch $completion) { break }
} while ([DateTimeOffset]::UtcNow -lt $deadline)

if (-not ($lines | Select-String -SimpleMatch $completion)) {
    throw "Python $Mode 矩阵等待超时"
}
$fatal = @($logcat | Select-String -Pattern "FATAL EXCEPTION|ANR in com.kite.app")
if ($fatal.Count -gt 0) { throw "Python $Mode 矩阵发现 ANR/FATAL" }

$artifactDirectory = Split-Path -Parent $artifactPath
New-Item -ItemType Directory -Force -Path $artifactDirectory | Out-Null
$lines | Set-Content -LiteralPath $artifactPath -Encoding utf8
$lines
Write-Output "结果：$artifactPath"
