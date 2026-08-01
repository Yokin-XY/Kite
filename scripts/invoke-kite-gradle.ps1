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
if (!(Test-Path -LiteralPath $settingsFile) -or
    !(Select-String -LiteralPath $settingsFile -SimpleMatch 'rootProject.name = "Kite"' -Quiet)) {
    throw "当前目录不是 Kite 工作树：$repositoryRoot"
}
if (!(Test-Path -LiteralPath $wrapper)) {
    throw "Gradle Wrapper 不存在：$wrapper"
}
if ($ProbeHoldSeconds -eq 0 -and $GradleArguments.Count -eq 0) {
    throw '必须提供 GradleArguments，或显式使用 ProbeHoldSeconds 验证互斥锁。'
}

$mutexName = 'Local\KiteGradleBuildV1'
$mutex = [Threading.Mutex]::new($false, $mutexName)
$acquired = $false
$abandoned = $false
$exitCode = 1
$wait = [Diagnostics.Stopwatch]::StartNew()
try {
    try {
        $acquired = $mutex.WaitOne([TimeSpan]::FromSeconds($LockTimeoutSeconds))
    } catch [Threading.AbandonedMutexException] {
        $acquired = $true
        $abandoned = $true
    }
    $wait.Stop()
    if (!$acquired) {
        throw "等待 Kite Gradle 构建锁超时：${LockTimeoutSeconds}s"
    }
    Write-Output (
        "KITE_GRADLE_LOCK status=acquired name=$mutexName pid=$PID " +
        "waitMs=$($wait.ElapsedMilliseconds) abandoned=$($abandoned.ToString().ToLowerInvariant()) " +
        "worktree=$repositoryRoot"
    )

    if ($ProbeHoldSeconds -gt 0) {
        Start-Sleep -Seconds $ProbeHoldSeconds
        $exitCode = 0
    } else {
        $arguments = [Collections.Generic.List[string]]::new()
        $GradleArguments | ForEach-Object { $arguments.Add($_) }
        if (!$arguments.Contains('--no-daemon')) {
            $arguments.Add('--no-daemon')
        }
        if (!$arguments.Contains('--console=plain')) {
            $arguments.Add('--console=plain')
        }
        & $wrapper @arguments
        $exitCode = $LASTEXITCODE
    }
} finally {
    if ($acquired) {
        $mutex.ReleaseMutex()
        Write-Output "KITE_GRADLE_LOCK status=released name=$mutexName pid=$PID"
    }
    $mutex.Dispose()
}

exit $exitCode
