param(
    [string]$Serial = "3f8bbaad",
    [string]$AdbPath = "D:\KF\Android\Sdk\platform-tools\adb.exe",
    [int]$TimeoutSeconds = 60,
    [string]$OutputPath = "local-artifacts\native-download-capability-probe.log"
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$artifactPath = Join-Path $repoRoot $OutputPath
$action = "com.kite.app.debug.NATIVE_DOWNLOAD_CAPABILITY_PROBE"
$tag = "[KFShell]NativeDownload"

if (-not (Test-Path -LiteralPath $AdbPath)) { throw "ADB 不存在：$AdbPath" }
if ($TimeoutSeconds -lt 10 -or $TimeoutSeconds -gt 300) { throw "TimeoutSeconds 必须在 10..300" }

& $AdbPath -s $Serial get-state | Out-Null
if ($LASTEXITCODE -ne 0) { throw "设备不可用：$Serial" }

& $AdbPath -s $Serial shell monkey -p com.kite.app -c android.intent.category.LAUNCHER 1 | Out-Null
if ($LASTEXITCODE -ne 0) { throw "无法启动 com.kite.app" }
Start-Sleep -Seconds 2
& $AdbPath -s $Serial logcat -c
& $AdbPath -s $Serial shell am broadcast -a $action -p com.kite.app | Out-Null
if ($LASTEXITCODE -ne 0) { throw "无法触发原生下载探针" }

$deadline = [DateTimeOffset]::UtcNow.AddSeconds($TimeoutSeconds)
$lines = @()
do {
    Start-Sleep -Seconds 1
    $logcat = & $AdbPath -s $Serial logcat -d -v tag
    $lines = @($logcat | Select-String -SimpleMatch $tag | ForEach-Object { $_.Line })
    if ($lines | Select-String -SimpleMatch "status=failed") {
        throw "原生下载探针失败：$($lines -join '; ')"
    }
    if ($lines | Select-String -SimpleMatch "status=complete") { break }
} while ([DateTimeOffset]::UtcNow -lt $deadline)

if (-not ($lines | Select-String -SimpleMatch "status=complete")) {
    throw "原生下载探针等待超时"
}
$fatal = @($logcat | Select-String -Pattern "FATAL EXCEPTION|ANR in com.kite.app")
if ($fatal.Count -gt 0) { throw "原生下载探针发现 ANR/FATAL" }

$artifactDirectory = Split-Path -Parent $artifactPath
New-Item -ItemType Directory -Force -Path $artifactDirectory | Out-Null
$lines | Set-Content -LiteralPath $artifactPath -Encoding utf8
$lines
Write-Output "结果：$artifactPath"
