[CmdletBinding()]
param(
    [Parameter(Mandatory = $false)]
    [string[]]$GradleArguments = @(),
    [ValidateRange(1, 7200)]
    [int]$LockTimeoutSeconds = 1800,
    [ValidateRange(0, 120)]
    [int]$ProbeHoldSeconds = 0
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repositoryRoot = Split-Path $PSScriptRoot -Parent
$settingsFile = Join-Path $repositoryRoot 'settings.gradle'
$wrapper = Join-Path $repositoryRoot 'gradlew.bat'
$worker = Join-Path $PSScriptRoot 'invoke-kite-gradle-worker.ps1'
if (!(Test-Path -LiteralPath $settingsFile) -or
    !(Select-String -LiteralPath $settingsFile -SimpleMatch 'rootProject.name = "Kite"' -Quiet)) {
    throw "当前目录不是 Kite 工作树：$repositoryRoot"
}
if (!(Test-Path -LiteralPath $wrapper)) {
    throw "Gradle Wrapper 不存在：$wrapper"
}
if (!(Test-Path -LiteralPath $worker)) {
    throw "Kite Gradle 工作进程脚本不存在：$worker"
}
if ($ProbeHoldSeconds -eq 0 -and $GradleArguments.Count -eq 0) {
    throw '必须提供 GradleArguments，或显式使用 ProbeHoldSeconds 验证互斥锁。'
}

# 锁由独立工作进程持有。即使本协调器被调用方中断，实际 Gradle 结束前也不会提前放锁。
$payload = @{
    repositoryRoot = $repositoryRoot
    gradleArguments = @($GradleArguments)
    lockTimeoutSeconds = $LockTimeoutSeconds
    probeHoldSeconds = $ProbeHoldSeconds
} | ConvertTo-Json -Compress
$payloadBase64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($payload))
$powerShellPath = (Get-Process -Id $PID).Path
$workerProcess = Start-Process `
    -FilePath $powerShellPath `
    -ArgumentList @(
        '-NoLogo',
        '-NoProfile',
        '-NonInteractive',
        '-ExecutionPolicy', 'Bypass',
        '-File', $worker,
        '-PayloadBase64', $payloadBase64
    ) `
    -NoNewWindow `
    -Wait `
    -PassThru

exit $workerProcess.ExitCode
