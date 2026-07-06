param(
    [string]$Serial = "3f8bbaad",
    [string[]]$Targets = @("codex", "claude"),
    [string]$RunnerScript = "",
    [string]$SmokeTestScript = "",
    [string]$ManualReadinessScript = "",
    [string]$CompletionAuditScript = "",
    [string]$StateDir = "",
    [int]$TimeoutMinutes = 60,
    [int]$PollSeconds = 30,
    [int]$MaxAttempts = 0,
    [switch]$RunReadinessFirst,
    [switch]$RunSmokeFirst,
    [switch]$RunCompletionAuditOnVerified
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($StateDir)) {
    $StateDir = Join-Path $env:LOCALAPPDATA "Kite\browser-login-continuation"
}
if ([string]::IsNullOrWhiteSpace($RunnerScript)) {
    $RunnerScript = Join-Path $PSScriptRoot "browser-login-continuation-runner.ps1"
}
if ([string]::IsNullOrWhiteSpace($SmokeTestScript)) {
    $SmokeTestScript = Join-Path $PSScriptRoot "browser-login-smoke-test.ps1"
}
if ([string]::IsNullOrWhiteSpace($ManualReadinessScript)) {
    $ManualReadinessScript = Join-Path $PSScriptRoot "browser-login-manual-readiness.ps1"
}
if ([string]::IsNullOrWhiteSpace($CompletionAuditScript)) {
    $CompletionAuditScript = Join-Path $PSScriptRoot "browser-login-completion-audit.ps1"
}
if (-not (Test-Path -LiteralPath $RunnerScript)) {
    throw "Runner script not found: $RunnerScript"
}
if ($RunSmokeFirst -and -not (Test-Path -LiteralPath $SmokeTestScript)) {
    throw "Smoke test script not found: $SmokeTestScript"
}
if ($RunReadinessFirst -and -not (Test-Path -LiteralPath $ManualReadinessScript)) {
    throw "Manual readiness script not found: $ManualReadinessScript"
}
if ($RunCompletionAuditOnVerified -and -not (Test-Path -LiteralPath $CompletionAuditScript)) {
    throw "Completion audit script not found: $CompletionAuditScript"
}

New-Item -ItemType Directory -Force -Path $StateDir | Out-Null

$watchStatePath = Join-Path $StateDir "account-watch-status.json"
$watchReportPath = Join-Path $StateDir "account-watch-report.md"
$runnerStatePath = Join-Path $StateDir "runner-status.json"
$evidenceReportPath = Join-Path $StateDir "post-auth-evidence-report.md"
$manualReadinessJsonPath = Join-Path $StateDir "manual-account-readiness.json"
$manualReadinessReportPath = Join-Path $StateDir "manual-account-readiness.md"
$completionAuditJsonPath = Join-Path $StateDir "completion-audit.json"
$completionAuditReportPath = Join-Path $StateDir "completion-audit.md"

function ConvertTo-StringArray {
    param([object]$Value)

    $items = New-Object System.Collections.Generic.List[string]
    if ($null -eq $Value) {
        return $items.ToArray()
    }

    foreach ($item in @($Value)) {
        if ($null -eq $item) {
            continue
        }
        $text = $item.ToString().Trim().ToLowerInvariant()
        if ([string]::IsNullOrWhiteSpace($text)) {
            continue
        }
        $items.Add($text)
    }
    return $items.ToArray()
}

function Normalize-Targets {
    param([string[]]$Items)

    $normalized = New-Object System.Collections.Generic.List[string]
    foreach ($item in $Items) {
        $name = $item.ToString().Trim().ToLowerInvariant()
        if ([string]::IsNullOrWhiteSpace($name)) {
            continue
        }
        if ($name -ne "codex" -and $name -ne "claude") {
            throw "Unsupported target: $item"
        }
        if (-not $normalized.Contains($name)) {
            $normalized.Add($name)
        }
    }

    if ($normalized.Count -eq 0) {
        $normalized.Add("codex")
        $normalized.Add("claude")
    }
    return $normalized.ToArray()
}

function Read-JsonOrNull {
    param([string]$Path)

    if (-not (Test-Path -LiteralPath $Path)) {
        return $null
    }
    return Get-Content -Raw -LiteralPath $Path | ConvertFrom-Json
}

function Format-Items {
    param([string[]]$Items)

    if ($Items.Count -eq 0) {
        return "(none)"
    }
    return ($Items -join ", ")
}

function Test-AllTargetsVerified {
    param(
        [string[]]$TargetNames,
        [string[]]$VerifiedNames
    )

    return @($TargetNames | Where-Object { $VerifiedNames -notcontains $_ }).Count -eq 0
}

function Get-Intersection {
    param(
        [string[]]$Left,
        [string[]]$Right
    )

    return @($Left | Where-Object { $Right -contains $_ })
}

function Write-WatchState {
    param(
        [string]$Status,
        [string]$NextAction,
        [int]$ExitCode,
        [int]$Attempts,
        [object]$RunnerExit,
        [object]$ReadinessExit,
        [string]$ReadinessStatus,
        [object]$SmokeExit,
        [string[]]$TargetNames,
        [string[]]$ReadyTargets,
        [string[]]$WaitingTargets,
        [string[]]$VerifiedTargets,
        [string[]]$FailedTargets,
        [string[]]$ErrorTargets,
        [object]$CompletionAuditExit,
        [string]$CompletionAuditStatus,
        [object[]]$History
    )

    $checkedAt = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ssK")
    $targetNamesClean = ConvertTo-StringArray $TargetNames
    $readyTargetsClean = ConvertTo-StringArray $ReadyTargets
    $waitingTargetsClean = ConvertTo-StringArray $WaitingTargets
    $verifiedTargetsClean = ConvertTo-StringArray $VerifiedTargets
    $failedTargetsClean = ConvertTo-StringArray $FailedTargets
    $errorTargetsClean = ConvertTo-StringArray $ErrorTargets
    $state = [ordered]@{
        checkedAt = $checkedAt
        serial = $Serial
        status = $Status
        exitCode = $ExitCode
        attempts = $Attempts
        maxAttempts = $MaxAttempts
        timeoutMinutes = $TimeoutMinutes
        pollSeconds = $PollSeconds
        nextAction = $NextAction
        runnerExit = $RunnerExit
        readinessExit = $ReadinessExit
        readinessStatus = $ReadinessStatus
        smokeExit = $SmokeExit
        targets = @($targetNamesClean)
        readyTargets = @($readyTargetsClean)
        waitingTargets = @($waitingTargetsClean)
        verifiedTargets = @($verifiedTargetsClean)
        failedTargets = @($failedTargetsClean)
        errorTargets = @($errorTargetsClean)
        runnerStatePath = $runnerStatePath
        evidenceReportPath = $evidenceReportPath
        manualReadinessJsonPath = $manualReadinessJsonPath
        manualReadinessReportPath = $manualReadinessReportPath
        completionAuditExit = $CompletionAuditExit
        completionAuditStatus = $CompletionAuditStatus
        completionAuditJsonPath = $completionAuditJsonPath
        completionAuditReportPath = $completionAuditReportPath
        history = @($History)
    }
    $state | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $watchStatePath -Encoding UTF8

    $lines = New-Object System.Collections.Generic.List[string]
    $lines.Add("# 浏览器登录人工账号 watch")
    $lines.Add("")
    $lines.Add("- 生成时间：$checkedAt")
    $lines.Add("- 设备：$Serial")
    $lines.Add("- 状态：$Status")
    $lines.Add("- 下一步：$NextAction")
    $lines.Add("- 尝试次数：$Attempts")
    $lines.Add("- runnerExit：$RunnerExit")
    $lines.Add("- readinessExit：$ReadinessExit")
    $lines.Add("- readinessStatus：$ReadinessStatus")
    $lines.Add("- smokeExit：$SmokeExit")
    $lines.Add("- targets：$(Format-Items $targetNamesClean)")
    $lines.Add("- readyTargets：$(Format-Items $readyTargetsClean)")
    $lines.Add("- waitingTargets：$(Format-Items $waitingTargetsClean)")
    $lines.Add("- verifiedTargets：$(Format-Items $verifiedTargetsClean)")
    $lines.Add("- failedTargets：$(Format-Items $failedTargetsClean)")
    $lines.Add("- errorTargets：$(Format-Items $errorTargetsClean)")
    $lines.Add("- completionAuditExit：$CompletionAuditExit")
    $lines.Add("- completionAuditStatus：$CompletionAuditStatus")
    $lines.Add("- runnerState：$runnerStatePath")
    $lines.Add("- evidenceReport：$evidenceReportPath")
    $lines.Add("- manualReadinessJson：$manualReadinessJsonPath")
    $lines.Add("- manualReadinessReport：$manualReadinessReportPath")
    $lines.Add("- completionAuditJson：$completionAuditJsonPath")
    $lines.Add("- completionAuditReport：$completionAuditReportPath")
    $lines.Add("")
    $lines.Add("## 边界")
    $lines.Add("")
    $lines.Add("- 本脚本只轮询现有 gate/runner/post-auth 链路，不输入账号，不读取 token，不伪造 callback。")
    $lines.Add("- 登录事实仍以 CLI 官方状态命令、post-auth 状态和 completion audit 为准。")
    $lines.ToArray() | Set-Content -LiteralPath $watchReportPath -Encoding UTF8

    Write-Output "stateJson=$watchStatePath"
    Write-Output "report=$watchReportPath"
}

function Invoke-CompletionAuditIfRequested {
    param([string[]]$TargetNames)

    if (-not $RunCompletionAuditOnVerified) {
        return [ordered]@{
            exitCode = $null
            status = ""
            nextAction = "run_completion_audit"
        }
    }

    & $CompletionAuditScript -StateDir $StateDir -RefreshState | Out-Null
    $auditExit = $LASTEXITCODE
    $auditStatus = ""
    $auditState = Read-JsonOrNull $completionAuditJsonPath
    if ($null -ne $auditState -and $null -ne $auditState.PSObject.Properties["status"]) {
        $auditStatus = $auditState.status.ToString()
    }

    $nextAction = if ($auditExit -eq 0 -and $auditStatus -eq "complete") {
        "completion_audit_complete"
    } elseif ($auditExit -eq 2 -or $auditStatus -eq "incomplete") {
        "continue_waiting_or_complete_remaining_accounts"
    } else {
        "inspect_completion_audit_output"
    }

    return [ordered]@{
        exitCode = $auditExit
        status = $auditStatus
        nextAction = $nextAction
    }
}

$targetNames = Normalize-Targets $Targets
$startedAt = Get-Date
$deadline = $startedAt.AddMinutes($TimeoutMinutes)
$attempts = 0
$lastRunnerExit = $null
$smokeExit = $null
$readinessExit = $null
$readinessStatus = ""
$acceptedReadinessStatuses = @(
    "ready_for_manual_account",
    "partial_account_verified_continue_watch",
    "account_verified_run_completion_audit"
)
$history = New-Object System.Collections.Generic.List[object]

Write-Output "browser-login-account-watch"
Write-Output "startedAt=$($startedAt.ToString("yyyy-MM-ddTHH:mm:ssK"))"
Write-Output "serial=$Serial"
Write-Output "targets=$($targetNames -join ',')"
Write-Output "stateDir=$StateDir"

if ($RunSmokeFirst) {
    & $SmokeTestScript -Serial $Serial -StateDir $StateDir | Out-Null
    $smokeExit = $LASTEXITCODE
    Write-Output "smokeExit=$smokeExit"
    if ($smokeExit -ne 0) {
        Write-WatchState `
            -Status "smoke_failed" `
            -NextAction "inspect_browser_login_smoke_output" `
            -ExitCode 1 `
            -Attempts 0 `
            -RunnerExit $null `
            -ReadinessExit $readinessExit `
            -ReadinessStatus $readinessStatus `
            -SmokeExit $smokeExit `
            -TargetNames $targetNames `
            -ReadyTargets @() `
            -WaitingTargets @($targetNames) `
            -VerifiedTargets @() `
            -FailedTargets @() `
            -ErrorTargets @() `
            -CompletionAuditExit $null `
            -CompletionAuditStatus "" `
            -History @()
        Write-Output "nextAction=inspect_browser_login_smoke_output"
        exit 1
    }
}

if ($RunReadinessFirst) {
    & $ManualReadinessScript -Serial $Serial -StateDir $StateDir -RefreshState -RunCompletionAudit | Out-Null
    $readinessExit = $LASTEXITCODE
    $readinessState = Read-JsonOrNull $manualReadinessJsonPath
    if ($null -ne $readinessState -and $null -ne $readinessState.PSObject.Properties["status"]) {
        $readinessStatus = $readinessState.status.ToString()
    }
    Write-Output "readinessExit=$readinessExit"
    Write-Output "readinessStatus=$readinessStatus"
    if ($readinessExit -ne 0 -or $acceptedReadinessStatuses -notcontains $readinessStatus) {
        Write-WatchState `
            -Status "manual_readiness_failed" `
            -NextAction "inspect_manual_account_readiness" `
            -ExitCode 1 `
            -Attempts 0 `
            -RunnerExit $null `
            -ReadinessExit $readinessExit `
            -ReadinessStatus $readinessStatus `
            -SmokeExit $smokeExit `
            -TargetNames $targetNames `
            -ReadyTargets @() `
            -WaitingTargets @($targetNames) `
            -VerifiedTargets @() `
            -FailedTargets @() `
            -ErrorTargets @() `
            -CompletionAuditExit $null `
            -CompletionAuditStatus "" `
            -History @()
        Write-Output "nextAction=inspect_manual_account_readiness"
        exit 1
    }
}

while ($true) {
    $attempts += 1
    Write-Output "attempt=$attempts"

    & $RunnerScript -Serial $Serial -StateDir $StateDir | Out-Null
    $lastRunnerExit = $LASTEXITCODE
    $runnerState = Read-JsonOrNull $runnerStatePath

    $readyTargets = @()
    $waitingTargets = @()
    $verifiedTargets = @()
    $failedTargets = @()
    $errorTargets = @()
    $nextAction = "inspect_runner_state"

    if ($null -ne $runnerState) {
        $readyTargets = ConvertTo-StringArray $runnerState.readyTargets
        $waitingTargets = ConvertTo-StringArray $runnerState.waitingTargets
        $verifiedTargets = ConvertTo-StringArray $runnerState.verifiedTargets
        $failedTargets = ConvertTo-StringArray $runnerState.failedTargets
        $errorTargets = ConvertTo-StringArray $runnerState.errorTargets
        if ($null -ne $runnerState.PSObject.Properties["nextAction"]) {
            $nextAction = $runnerState.nextAction.ToString()
        }
    }
    $readyTargets = Get-Intersection -Left $targetNames -Right $readyTargets
    $waitingTargets = Get-Intersection -Left $targetNames -Right $waitingTargets
    $verifiedTargets = Get-Intersection -Left $targetNames -Right $verifiedTargets
    $failedTargets = Get-Intersection -Left $targetNames -Right $failedTargets
    $errorTargets = Get-Intersection -Left $targetNames -Right $errorTargets

    $history.Add([ordered]@{
        attempt = $attempts
        checkedAt = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ssK")
        runnerExit = $lastRunnerExit
        readyTargets = @($readyTargets)
        waitingTargets = @($waitingTargets)
        verifiedTargets = @($verifiedTargets)
        failedTargets = @($failedTargets)
        errorTargets = @($errorTargets)
        nextAction = $nextAction
    })

    Write-Output "runnerExit=$lastRunnerExit"
    Write-Output "verifiedTargets=$($verifiedTargets -join ',')"
    Write-Output "waitingTargets=$($waitingTargets -join ',')"
    Write-Output "failedTargets=$($failedTargets -join ',')"
    Write-Output "errorTargets=$($errorTargets -join ',')"
    Write-Output "nextAction=$nextAction"

    if (Test-AllTargetsVerified -TargetNames $targetNames -VerifiedNames $verifiedTargets) {
        $completionAudit = Invoke-CompletionAuditIfRequested -TargetNames $targetNames
        Write-WatchState `
            -Status "verified" `
            -NextAction $completionAudit.nextAction `
            -ExitCode 0 `
            -Attempts $attempts `
            -RunnerExit $lastRunnerExit `
            -ReadinessExit $readinessExit `
            -ReadinessStatus $readinessStatus `
            -SmokeExit $smokeExit `
            -TargetNames $targetNames `
            -ReadyTargets $readyTargets `
            -WaitingTargets $waitingTargets `
            -VerifiedTargets $verifiedTargets `
            -FailedTargets $failedTargets `
            -ErrorTargets $errorTargets `
            -CompletionAuditExit $completionAudit.exitCode `
            -CompletionAuditStatus $completionAudit.status `
            -History $history.ToArray()
        Write-Output "completionAuditExit=$($completionAudit.exitCode)"
        Write-Output "completionAuditStatus=$($completionAudit.status)"
        Write-Output "nextAction=$($completionAudit.nextAction)"
        if ($RunCompletionAuditOnVerified -and $completionAudit.exitCode -eq 1) {
            exit 1
        }
        exit 0
    }

    $targetFailures = Get-Intersection -Left $targetNames -Right $failedTargets
    $targetErrors = Get-Intersection -Left $targetNames -Right $errorTargets
    if ($targetFailures.Count -gt 0 -or $targetErrors.Count -gt 0) {
        Write-WatchState `
            -Status "needs_inspection" `
            -NextAction "inspect_post_auth_or_environment_output" `
            -ExitCode 1 `
            -Attempts $attempts `
            -RunnerExit $lastRunnerExit `
            -ReadinessExit $readinessExit `
            -ReadinessStatus $readinessStatus `
            -SmokeExit $smokeExit `
            -TargetNames $targetNames `
            -ReadyTargets $readyTargets `
            -WaitingTargets $waitingTargets `
            -VerifiedTargets $verifiedTargets `
            -FailedTargets $failedTargets `
            -ErrorTargets $errorTargets `
            -CompletionAuditExit $null `
            -CompletionAuditStatus "" `
            -History $history.ToArray()
        Write-Output "nextAction=inspect_post_auth_or_environment_output"
        exit 1
    }

    $attemptLimitReached = $MaxAttempts -gt 0 -and $attempts -ge $MaxAttempts
    $timeoutReached = (Get-Date) -ge $deadline
    if ($attemptLimitReached -or $timeoutReached) {
        Write-WatchState `
            -Status "waiting_for_real_account_authorization" `
            -NextAction "continue_waiting_or_complete_account_authorization" `
            -ExitCode 2 `
            -Attempts $attempts `
            -RunnerExit $lastRunnerExit `
            -ReadinessExit $readinessExit `
            -ReadinessStatus $readinessStatus `
            -SmokeExit $smokeExit `
            -TargetNames $targetNames `
            -ReadyTargets $readyTargets `
            -WaitingTargets $waitingTargets `
            -VerifiedTargets $verifiedTargets `
            -FailedTargets $failedTargets `
            -ErrorTargets $errorTargets `
            -CompletionAuditExit $null `
            -CompletionAuditStatus "" `
            -History $history.ToArray()
        Write-Output "nextAction=continue_waiting_or_complete_account_authorization"
        exit 2
    }

    if ($PollSeconds -gt 0) {
        Start-Sleep -Seconds $PollSeconds
    }
}
