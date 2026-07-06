param(
    [string]$Serial = "3f8bbaad",
    [string]$StateDir = "",
    [string]$RunnerScript = "",
    [string]$SmokeWatchScript = "",
    [string]$ManualReadinessScript = "",
    [string]$CompletionAuditScript = "",
    [int]$SmokeIterations = 3,
    [int]$SmokeIntervalSeconds = 0,
    [int]$OpenWebP95ThresholdMs = 1500,
    [int]$ForegroundP95ThresholdMs = 5000,
    [switch]$SkipSmokeWatch,
    [switch]$RunCompletionAuditOnWaiting
)

$ErrorActionPreference = "Stop"

if ($SmokeIterations -lt 1) {
    throw "SmokeIterations must be at least 1."
}
if ($SmokeIntervalSeconds -lt 0) {
    throw "SmokeIntervalSeconds must be 0 or greater."
}

if ([string]::IsNullOrWhiteSpace($StateDir)) {
    $StateDir = Join-Path $env:LOCALAPPDATA "Kite\browser-login-continuation"
}
if ([string]::IsNullOrWhiteSpace($RunnerScript)) {
    $RunnerScript = Join-Path $PSScriptRoot "browser-login-continuation-runner.ps1"
}
if ([string]::IsNullOrWhiteSpace($SmokeWatchScript)) {
    $SmokeWatchScript = Join-Path $PSScriptRoot "browser-login-smoke-watch.ps1"
}
if ([string]::IsNullOrWhiteSpace($ManualReadinessScript)) {
    $ManualReadinessScript = Join-Path $PSScriptRoot "browser-login-manual-readiness.ps1"
}
if ([string]::IsNullOrWhiteSpace($CompletionAuditScript)) {
    $CompletionAuditScript = Join-Path $PSScriptRoot "browser-login-completion-audit.ps1"
}

foreach ($scriptPath in @($RunnerScript, $SmokeWatchScript, $ManualReadinessScript, $CompletionAuditScript)) {
    if (-not (Test-Path -LiteralPath $scriptPath)) {
        throw "Required script not found: $scriptPath"
    }
}

New-Item -ItemType Directory -Force -Path $StateDir | Out-Null

$statusPath = Join-Path $StateDir "browser-login-long-run-cycle.json"
$progressPath = Join-Path $StateDir "browser-login-long-run-cycle-progress.json"
$reportPath = Join-Path $StateDir "browser-login-long-run-cycle.md"
$runnerOutputPath = Join-Path $StateDir "long-run-runner-output.txt"
$smokeWatchOutputPath = Join-Path $StateDir "long-run-smoke-watch-output.txt"
$readinessOutputPath = Join-Path $StateDir "long-run-manual-readiness-output.txt"
$completionAuditOutputPath = Join-Path $StateDir "long-run-completion-audit-output.txt"
$smokeWatchStateDir = $StateDir
if ($SmokeIterations -lt 3) {
    $smokeWatchStateDir = Join-Path $StateDir "browser-login-long-run-cycle-smoke"
}
New-Item -ItemType Directory -Force -Path $smokeWatchStateDir | Out-Null
$smokeWatchJsonPath = Join-Path $smokeWatchStateDir "browser-login-smoke-watch.json"

function Read-JsonOrNull {
    param([string]$Path)

    if (-not (Test-Path -LiteralPath $Path)) {
        return $null
    }
    return Get-Content -Raw -LiteralPath $Path | ConvertFrom-Json
}

function To-StringArray {
    param([object]$Value)

    $items = New-Object System.Collections.Generic.List[string]
    foreach ($item in @($Value)) {
        if ($null -eq $item) {
            continue
        }
        $text = $item.ToString().Trim()
        if ([string]::IsNullOrWhiteSpace($text)) {
            continue
        }
        $items.Add($text)
    }
    return $items.ToArray()
}

function Save-Output {
    param(
        [string]$Path,
        [object[]]$Output
    )

    @($Output) | ForEach-Object { $_.ToString() } | Set-Content -LiteralPath $Path -Encoding UTF8
}

function Is-OnlyAccountFailures {
    param([string[]]$FailedItemIds)

    $nonAccount = @($FailedItemIds | Where-Object { $_ -ne "codex-account" -and $_ -ne "claude-account" })
    return $nonAccount.Count -eq 0
}

function Get-StateArray {
    param([object]$Value)

    $items = New-Object System.Collections.ArrayList
    foreach ($item in @(To-StringArray $Value)) {
        [void]$items.Add($item)
    }
    return ,$items
}

function Add-Line {
    param(
        [System.Collections.Generic.List[string]]$Lines,
        [string]$Text
    )

    $Lines.Add($Text)
}

function Write-LongRunProgress {
    param(
        [string]$Phase,
        [string]$Status = "running",
        [string]$Message = ""
    )

    $progress = [ordered]@{
        checkedAt = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ssK")
        startedAt = $startedAt
        serial = $Serial
        status = $Status
        phase = $Phase
        message = $Message
        stateDir = $StateDir
        smokeIterations = $SmokeIterations
        smokeIntervalSeconds = $SmokeIntervalSeconds
        smokeWatchStateDir = $smokeWatchStateDir
        runnerExit = $runnerExit
        smokeWatchExit = $smokeWatchExit
        manualReadinessExit = $manualReadinessExit
        completionAuditExit = $completionAuditExit
        progressPath = $progressPath
        statePath = $statusPath
        reportPath = $reportPath
        runnerOutputPath = $runnerOutputPath
        smokeWatchOutputPath = $smokeWatchOutputPath
        manualReadinessOutputPath = $readinessOutputPath
        completionAuditOutputPath = $completionAuditOutputPath
    }
    $progress | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $progressPath -Encoding UTF8
}

$startedAt = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ssK")
$runnerExit = $null
$smokeWatchExit = $null
$manualReadinessExit = $null
$completionAuditExit = $null
$exitCode = 1
$status = "needs_inspection"
$nextAction = "inspect_long_run_cycle_output"

Write-LongRunProgress -Phase "starting" -Message "Starting long-run cycle."
Write-LongRunProgress -Phase "runner_started" -Message "Checking account readiness through runner."
$runnerOutput = & $RunnerScript -Serial $Serial -StateDir $StateDir 2>&1
$runnerExit = $LASTEXITCODE
Save-Output -Path $runnerOutputPath -Output $runnerOutput
$runnerState = Read-JsonOrNull (Join-Path $StateDir "runner-status.json")
Write-LongRunProgress -Phase "runner_completed" -Message "Runner completed with exit code $runnerExit."

if ($runnerExit -eq 2) {
    if (-not $SkipSmokeWatch) {
        Write-LongRunProgress -Phase "smoke_watch_started" -Message "Running smoke watch while accounts are still waiting."
        $smokeWatchOutput = & $SmokeWatchScript `
            -Serial $Serial `
            -StateDir $smokeWatchStateDir `
            -Iterations $SmokeIterations `
            -IntervalSeconds $SmokeIntervalSeconds `
            -OpenWebP95ThresholdMs $OpenWebP95ThresholdMs `
            -ForegroundP95ThresholdMs $ForegroundP95ThresholdMs 2>&1
        $smokeWatchExit = $LASTEXITCODE
        Save-Output -Path $smokeWatchOutputPath -Output $smokeWatchOutput
        Write-LongRunProgress -Phase "smoke_watch_completed" -Message "Smoke watch completed with exit code $smokeWatchExit."
    }

    $smokeWatchState = Read-JsonOrNull $smokeWatchJsonPath
    if (-not $SkipSmokeWatch -and $smokeWatchExit -ne 0) {
        $status = "waiting_account_browser_watch_failed"
        $nextAction = "inspect_smoke_watch_before_account_authorization"
        $exitCode = 1
    } else {
        Write-LongRunProgress -Phase "manual_readiness_started" -Message "Refreshing manual account readiness."
        $readinessOutput = & $ManualReadinessScript -Serial $Serial -StateDir $StateDir -RefreshState 2>&1
        $manualReadinessExit = $LASTEXITCODE
        Save-Output -Path $readinessOutputPath -Output $readinessOutput
        $readinessState = Read-JsonOrNull (Join-Path $StateDir "manual-account-readiness.json")
        Write-LongRunProgress -Phase "manual_readiness_completed" -Message "Manual readiness completed with exit code $manualReadinessExit."
        $allowedReadiness = @("ready_for_manual_account", "partial_account_verified_continue_watch", "account_verified_run_completion_audit", "complete")
        $readinessStatus = if ($null -ne $readinessState) { $readinessState.status } else { "" }

        if ($manualReadinessExit -ne 0 -or -not ($allowedReadiness -contains $readinessStatus)) {
            $status = "waiting_account_readiness_failed"
            $nextAction = "inspect_manual_readiness_before_account_authorization"
            $exitCode = 1
        } elseif ($readinessStatus -eq "complete") {
            $status = "complete"
            $nextAction = "record_completion"
            $exitCode = 0
        } else {
            if ($RunCompletionAuditOnWaiting) {
                Write-LongRunProgress -Phase "completion_audit_started" -Message "Refreshing completion audit while accounts are still waiting."
                $completionAuditOutput = & $CompletionAuditScript -StateDir $StateDir -RefreshState 2>&1
                $completionAuditExit = $LASTEXITCODE
                Save-Output -Path $completionAuditOutputPath -Output $completionAuditOutput
                Write-LongRunProgress -Phase "completion_audit_completed" -Message "Completion audit completed with exit code $completionAuditExit."
            }
            $auditState = Read-JsonOrNull (Join-Path $StateDir "completion-audit.json")
            $auditFailed = if ($null -ne $auditState) { To-StringArray $auditState.failedItemIds } else { @() }
            if ($RunCompletionAuditOnWaiting -and ($null -eq $auditState -or -not (Is-OnlyAccountFailures $auditFailed))) {
                $status = "waiting_account_completion_audit_needs_inspection"
                $nextAction = "inspect_non_account_completion_audit_failure"
                $exitCode = 1
            } else {
                $status = "waiting_account_browser_stable"
                $nextAction = "continue_real_account_authorization_or_wait_for_ready_target"
                $exitCode = 2
            }
        }
    }
} elseif ($runnerExit -eq 0) {
    Write-LongRunProgress -Phase "completion_audit_started" -Message "Runner found ready targets; refreshing completion audit."
    $completionAuditOutput = & $CompletionAuditScript -StateDir $StateDir -RefreshState 2>&1
    $completionAuditExit = $LASTEXITCODE
    Save-Output -Path $completionAuditOutputPath -Output $completionAuditOutput
    Write-LongRunProgress -Phase "completion_audit_completed" -Message "Completion audit completed with exit code $completionAuditExit."
    $auditState = Read-JsonOrNull (Join-Path $StateDir "completion-audit.json")
    if ($null -ne $auditState -and $auditState.status -eq "complete") {
        $status = "complete"
        $nextAction = "record_completion"
        $exitCode = 0
    } else {
        $status = "post_auth_completion_audit_needs_inspection"
        $nextAction = "inspect_post_auth_or_missing_account_evidence"
        $exitCode = 1
    }
} else {
    $status = "runner_needs_inspection"
    $nextAction = "inspect_runner_environment_or_auth_status"
    $exitCode = 1
}

$endedAt = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ssK")
$runnerState = Read-JsonOrNull (Join-Path $StateDir "runner-status.json")
$smokeWatchState = Read-JsonOrNull $smokeWatchJsonPath
$readinessState = Read-JsonOrNull (Join-Path $StateDir "manual-account-readiness.json")
$auditState = Read-JsonOrNull (Join-Path $StateDir "completion-audit.json")
$readyTargetsForState = if ($null -ne $runnerState) { Get-StateArray $runnerState.readyTargets } else { [string[]]@() }
$waitingTargetsForState = if ($null -ne $runnerState) { Get-StateArray $runnerState.waitingTargets } else { [string[]]@() }
$verifiedTargetsForState = if ($null -ne $runnerState) { Get-StateArray $runnerState.verifiedTargets } else { [string[]]@() }
$errorTargetsForState = if ($null -ne $runnerState) { Get-StateArray $runnerState.errorTargets } else { [string[]]@() }
$smokeWatchHandlerPackagesForState = if ($null -ne $smokeWatchState) { Get-StateArray $smokeWatchState.handlerPackages } else { [string[]]@() }
$manualReadinessFailedForState = if ($null -ne $readinessState) { Get-StateArray $readinessState.failedItemIds } else { [string[]]@() }
$completionAuditFailedForState = if ($null -ne $auditState) { Get-StateArray $auditState.failedItemIds } else { [string[]]@() }

$state = [ordered]@{
    checkedAt = $endedAt
    startedAt = $startedAt
    endedAt = $endedAt
    serial = $Serial
    status = $status
    exitCode = $exitCode
    nextAction = $nextAction
    stateDir = $StateDir
    runnerExit = $runnerExit
    smokeWatchExit = $smokeWatchExit
    manualReadinessExit = $manualReadinessExit
    completionAuditExit = $completionAuditExit
    skipSmokeWatch = [bool]$SkipSmokeWatch
    runCompletionAuditOnWaiting = [bool]$RunCompletionAuditOnWaiting
    smokeIterations = $SmokeIterations
    smokeIntervalSeconds = $SmokeIntervalSeconds
    smokeWatchStateDir = $smokeWatchStateDir
    readyTargets = $readyTargetsForState
    waitingTargets = $waitingTargetsForState
    verifiedTargets = $verifiedTargetsForState
    errorTargets = $errorTargetsForState
    smokeWatchStatus = if ($null -ne $smokeWatchState) { $smokeWatchState.status } else { "" }
    smokeWatchFailureCount = if ($null -ne $smokeWatchState) { $smokeWatchState.failureCount } else { $null }
    smokeWatchOpenWebP95Ms = if ($null -ne $smokeWatchState) { $smokeWatchState.openWebP95Ms } else { $null }
    smokeWatchForegroundP95Ms = if ($null -ne $smokeWatchState) { $smokeWatchState.foregroundP95Ms } else { $null }
    smokeWatchHandlerPackages = $smokeWatchHandlerPackagesForState
    smokeWatchProviderSessionLeakRunCount = if ($null -ne $smokeWatchState) { $smokeWatchState.providerSessionLeakRunCount } else { $null }
    smokeWatchProviderPageBlockingErrorRunCount = if ($null -ne $smokeWatchState) { $smokeWatchState.providerPageBlockingErrorRunCount } else { $null }
    smokeWatchSecretLeakRunCount = if ($null -ne $smokeWatchState) { $smokeWatchState.secretLeakRunCount } else { $null }
    manualReadinessStatus = if ($null -ne $readinessState) { $readinessState.status } else { "" }
    manualReadinessFailedItemIds = $manualReadinessFailedForState
    completionAuditStatus = if ($null -ne $auditState) { $auditState.status } else { "" }
    completionAuditFailedItemIds = $completionAuditFailedForState
    runnerOutputPath = $runnerOutputPath
    smokeWatchOutputPath = $smokeWatchOutputPath
    manualReadinessOutputPath = $readinessOutputPath
    completionAuditOutputPath = $completionAuditOutputPath
    statePath = $statusPath
    reportPath = $reportPath
}
$state | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $statusPath -Encoding UTF8

$lines = New-Object System.Collections.Generic.List[string]
Add-Line $lines "# 浏览器登录 long-run cycle"
Add-Line $lines ""
Add-Line $lines "- 生成时间：$endedAt"
Add-Line $lines "- 状态：$status"
Add-Line $lines "- exitCode：$exitCode"
Add-Line $lines "- serial：$Serial"
Add-Line $lines "- nextAction：$nextAction"
Add-Line $lines ""
Add-Line $lines "## 阶段结果"
Add-Line $lines ""
Add-Line $lines "- runnerExit：$runnerExit"
Add-Line $lines "- smokeWatchExit：$smokeWatchExit"
Add-Line $lines "- manualReadinessExit：$manualReadinessExit"
Add-Line $lines "- completionAuditExit：$completionAuditExit"
Add-Line $lines "- smokeWatchStateDir：$smokeWatchStateDir"
Add-Line $lines ""
Add-Line $lines "## 摘要"
Add-Line $lines ""
Add-Line $lines "- readyTargets：$(($state.readyTargets) -join ', ')"
Add-Line $lines "- waitingTargets：$(($state.waitingTargets) -join ', ')"
Add-Line $lines "- verifiedTargets：$(($state.verifiedTargets) -join ', ')"
Add-Line $lines "- errorTargets：$(($state.errorTargets) -join ', ')"
Add-Line $lines "- smokeWatch：$($state.smokeWatchStatus)，iterations=$SmokeIterations，failureCount=$($state.smokeWatchFailureCount)，openWebP95Ms=$($state.smokeWatchOpenWebP95Ms)，foregroundP95Ms=$($state.smokeWatchForegroundP95Ms)，providerPageBlockingErrorRunCount=$($state.smokeWatchProviderPageBlockingErrorRunCount)"
Add-Line $lines "- manualReadiness：$($state.manualReadinessStatus)，failedItemIds=$(($state.manualReadinessFailedItemIds) -join ', ')"
Add-Line $lines "- completionAudit：$($state.completionAuditStatus)，failedItemIds=$(($state.completionAuditFailedItemIds) -join ', ')"
Add-Line $lines ""
Add-Line $lines "## 输出文件"
Add-Line $lines ""
Add-Line $lines "- runner：$runnerOutputPath"
Add-Line $lines "- smoke watch：$smokeWatchOutputPath"
Add-Line $lines "- manual readiness：$readinessOutputPath"
Add-Line $lines "- completion audit：$completionAuditOutputPath"
Add-Line $lines "- JSON：$statusPath"
Add-Line $lines ""
Add-Line $lines "## 边界"
Add-Line $lines ""
Add-Line $lines "- 本 cycle 不输入账号、不读取 token、不伪造 provider callback。"
Add-Line $lines "- `waiting_account_browser_stable` 只表示账号等待期间浏览器可控链路仍健康；真实完成仍以 Codex/Claude 官方状态命令和 completion audit 为准。"
$lines.ToArray() | Set-Content -LiteralPath $reportPath -Encoding UTF8
Write-LongRunProgress -Phase "finished" -Status $status -Message "Long-run cycle finished with status $status."

Write-Output "browser-login-long-run-cycle"
Write-Output "checkedAt=$endedAt"
Write-Output "status=$status"
Write-Output "exitCode=$exitCode"
Write-Output "runnerExit=$runnerExit"
Write-Output "smokeWatchExit=$smokeWatchExit"
Write-Output "manualReadinessExit=$manualReadinessExit"
Write-Output "completionAuditExit=$completionAuditExit"
Write-Output "nextAction=$nextAction"
Write-Output "json=$statusPath"
Write-Output "report=$reportPath"
exit $exitCode
