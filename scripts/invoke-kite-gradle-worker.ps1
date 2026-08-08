[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$PayloadBase64
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$payloadJson = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($PayloadBase64))
$payload = $payloadJson | ConvertFrom-Json
$repositoryRoot = [IO.Path]::GetFullPath([string]$payload.repositoryRoot)
$settingsFile = Join-Path $repositoryRoot 'settings.gradle'
$wrapper = Join-Path $repositoryRoot 'gradlew.bat'
$argumentContract = Join-Path $PSScriptRoot 'kite-gradle-argument-contract.ps1'
$gradleArguments = @($payload.gradleArguments | ForEach-Object { [string]$_ })
$lockTimeoutSeconds = [int]$payload.lockTimeoutSeconds
$probeHoldSeconds = [int]$payload.probeHoldSeconds

if (!(Test-Path -LiteralPath $settingsFile) -or
    !(Select-String -LiteralPath $settingsFile -SimpleMatch 'rootProject.name = "Kite"' -Quiet)) {
    throw "工作进程拒绝非 Kite 工作树：$repositoryRoot"
}
if (!(Test-Path -LiteralPath $wrapper)) {
    throw "Gradle Wrapper 不存在：$wrapper"
}
if (!(Test-Path -LiteralPath $argumentContract)) {
    throw "Kite Gradle 参数合同不存在：$argumentContract"
}
. $argumentContract
$resolvedGradleArguments = Resolve-KiteGradleArguments -GradleArguments $gradleArguments
if ($lockTimeoutSeconds -lt 1 -or $lockTimeoutSeconds -gt 7200) {
    throw "非法构建锁超时：$lockTimeoutSeconds"
}
if ($probeHoldSeconds -lt 0 -or $probeHoldSeconds -gt 120) {
    throw "非法构建锁探针时长：$probeHoldSeconds"
}

$mutexName = 'Local\KiteGradleBuildV1'
$mutex = [Threading.Mutex]::new($false, $mutexName)
$acquired = $false
$abandoned = $false
$exitCode = 1
$wait = [Diagnostics.Stopwatch]::StartNew()
try {
    try {
        $acquired = $mutex.WaitOne([TimeSpan]::FromSeconds($lockTimeoutSeconds))
    } catch [Threading.AbandonedMutexException] {
        $acquired = $true
        $abandoned = $true
    }
    $wait.Stop()
    if (!$acquired) {
        throw "等待 Kite Gradle 构建锁超时：${lockTimeoutSeconds}s"
    }
    Write-Output (
        "KITE_GRADLE_LOCK status=acquired name=$mutexName workerPid=$PID " +
        "waitMs=$($wait.ElapsedMilliseconds) abandoned=$($abandoned.ToString().ToLowerInvariant()) " +
        "worktree=$repositoryRoot"
    )

    if ($probeHoldSeconds -gt 0) {
        Start-Sleep -Seconds $probeHoldSeconds
        $exitCode = 0
    } else {
        & $wrapper @resolvedGradleArguments
        $exitCode = $LASTEXITCODE
    }
} finally {
    if ($acquired) {
        $mutex.ReleaseMutex()
        Write-Output "KITE_GRADLE_LOCK status=released name=$mutexName workerPid=$PID"
    }
    $mutex.Dispose()
}

exit $exitCode
