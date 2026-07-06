param(
    [string]$Serial = "3f8bbaad",
    [string]$StateDir = "",
    [string]$ContinuationTaskName = "KiteBrowserLoginContinuationGate",
    [string]$LongRunTaskName = "KiteBrowserLoginLongRunCycle"
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($StateDir)) {
    $StateDir = Join-Path $env:LOCALAPPDATA "Kite\browser-login-continuation"
}

New-Item -ItemType Directory -Force -Path $StateDir | Out-Null

$summaryPath = Join-Path $StateDir "browser-login-status-summary.json"
$reportPath = Join-Path $StateDir "browser-login-status-summary.md"

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

function Format-Timestamp {
    param([object]$Value)

    if ($null -eq $Value) {
        return $null
    }

    if ($Value -is [datetime]) {
        return $Value.ToString("yyyy-MM-ddTHH:mm:ssK")
    }

    $text = $Value.ToString().Trim()
    if ([string]::IsNullOrWhiteSpace($text)) {
        return $null
    }

    try {
        $parsed = [datetimeoffset]::Parse($text, [Globalization.CultureInfo]::InvariantCulture)
        return $parsed.ToString("yyyy-MM-ddTHH:mm:ssK")
    } catch {
        return $text
    }
}

function Convert-ToDateTimeOffsetOrNull {
    param([object]$Value)

    if ($null -eq $Value) {
        return $null
    }

    if ($Value -is [datetimeoffset]) {
        return $Value
    }

    if ($Value -is [datetime]) {
        return [datetimeoffset]::new($Value)
    }

    $text = $Value.ToString().Trim()
    if ([string]::IsNullOrWhiteSpace($text)) {
        return $null
    }

    try {
        return [datetimeoffset]::Parse($text, [Globalization.CultureInfo]::InvariantCulture)
    } catch {
        return $null
    }
}

function Get-ActionIntArgument {
    param(
        [object[]]$Actions,
        [string]$Name,
        [int]$DefaultValue = 0
    )

    $escaped = [regex]::Escape($Name)
    $pattern = "(?i)(?:^|\s)-$escaped\s+([0-9]+)"
    foreach ($action in @($Actions)) {
        if ($null -eq $action) {
            continue
        }
        $match = [regex]::Match($action.ToString(), $pattern)
        if ($match.Success) {
            return [int]$match.Groups[1].Value
        }
    }
    return $DefaultValue
}

function Get-TaskSnapshot {
    param([string]$TaskName)

    try {
        $task = Get-ScheduledTask -TaskName $TaskName -ErrorAction Stop
        $info = Get-ScheduledTaskInfo -TaskName $TaskName -ErrorAction Stop
        return [ordered]@{
            found = $true
            taskName = $TaskName
            state = $task.State.ToString()
            lastRunTime = Format-Timestamp $info.LastRunTime
            lastTaskResult = $info.LastTaskResult
            nextRunTime = Format-Timestamp $info.NextRunTime
            actions = @($task.Actions | ForEach-Object { ($_.Execute + " " + $_.Arguments).Trim() })
            repetitionInterval = @($task.Triggers | ForEach-Object { $_.Repetition.Interval })
            repetitionDuration = @($task.Triggers | ForEach-Object { $_.Repetition.Duration })
        }
    } catch {
        return [ordered]@{
            found = $false
            taskName = $TaskName
            error = $_.Exception.Message
        }
    }
}

function Get-FileTimestamp {
    param([string]$Path)

    if (-not (Test-Path -LiteralPath $Path)) {
        return $null
    }
    return (Get-Item -LiteralPath $Path).LastWriteTime.ToString("yyyy-MM-ddTHH:mm:ssK")
}

function Add-Line {
    param(
        [System.Collections.Generic.List[string]]$Lines,
        [string]$Text
    )

    $Lines.Add($Text)
}

$runnerPath = Join-Path $StateDir "runner-status.json"
$longRunPath = Join-Path $StateDir "browser-login-long-run-cycle.json"
$longRunProgressPath = Join-Path $StateDir "browser-login-long-run-cycle-progress.json"
$smokePath = Join-Path $StateDir "browser-login-smoke.json"
$smokeWatchPath = Join-Path $StateDir "browser-login-smoke-watch.json"
$smokeWatchProgressPath = Join-Path $StateDir "browser-login-smoke-watch-progress.json"
$manualReadinessPath = Join-Path $StateDir "manual-account-readiness.json"
$completionAuditPath = Join-Path $StateDir "completion-audit.json"
$providerPreflightPath = Join-Path $StateDir "provider-auth-preflight.json"

$runner = Read-JsonOrNull $runnerPath
$longRun = Read-JsonOrNull $longRunPath
$longRunProgress = Read-JsonOrNull $longRunProgressPath
$smoke = Read-JsonOrNull $smokePath
$smokeWatch = Read-JsonOrNull $smokeWatchPath
$smokeWatchProgress = Read-JsonOrNull $smokeWatchProgressPath
$manualReadiness = Read-JsonOrNull $manualReadinessPath
$completionAudit = Read-JsonOrNull $completionAuditPath
$providerPreflight = Read-JsonOrNull $providerPreflightPath

$continuationTask = Get-TaskSnapshot $ContinuationTaskName
$longRunTask = Get-TaskSnapshot $LongRunTaskName

$readyTargets = if ($null -ne $runner) { To-StringArray $runner.readyTargets } else { @() }
$waitingTargets = if ($null -ne $runner) { To-StringArray $runner.waitingTargets } else { @() }
$verifiedTargets = if ($null -ne $runner) { To-StringArray $runner.verifiedTargets } else { @() }
$errorTargets = if ($null -ne $runner) { To-StringArray $runner.errorTargets } else { @() }
$auditFailed = if ($null -ne $completionAudit) { To-StringArray $completionAudit.failedItemIds } else { @() }
$nonAccountAuditFailures = @($auditFailed | Where-Object { $_ -ne "codex-account" -and $_ -ne "claude-account" })

$longRunTaskRunning = [bool]($longRunTask.found -and $longRunTask.state -eq "Running")
$progressKnown = $null -ne $longRunProgress
$scheduledLongRunSmokeIterations = if ($longRunTask.found) { Get-ActionIntArgument -Actions $longRunTask.actions -Name "SmokeIterations" -DefaultValue 0 } else { 0 }
$scheduledLongRunSmokeIntervalSeconds = if ($longRunTask.found) { Get-ActionIntArgument -Actions $longRunTask.actions -Name "SmokeIntervalSeconds" -DefaultValue 0 } else { 0 }
$longRunTaskStartedAt = if ($longRunTaskRunning) { Convert-ToDateTimeOffsetOrNull $longRunTask.lastRunTime } else { $null }
$latestSmokeCheckedAt = if ($null -ne $smoke) { Convert-ToDateTimeOffsetOrNull $smoke.checkedAt } else { $null }
$now = [datetimeoffset]::Now
$longRunElapsedSeconds = $null
$longRunElapsedMinutes = $null
if ($null -ne $longRunTaskStartedAt) {
    $elapsed = $now - $longRunTaskStartedAt
    $longRunElapsedSeconds = [int][Math]::Max(0, [Math]::Round($elapsed.TotalSeconds))
    $longRunElapsedMinutes = [int][Math]::Max(0, [Math]::Floor($elapsed.TotalMinutes))
}
$latestSmokeAgeSeconds = $null
$latestSmokeAgeMinutes = $null
if ($null -ne $latestSmokeCheckedAt) {
    $smokeAge = $now - $latestSmokeCheckedAt
    $latestSmokeAgeSeconds = [int][Math]::Max(0, [Math]::Round($smokeAge.TotalSeconds))
    $latestSmokeAgeMinutes = [int][Math]::Max(0, [Math]::Floor($smokeAge.TotalMinutes))
}
$latestSmokeAfterCurrentRunStart = $null
$latestSmokeSecondsAfterCurrentRunStart = $null
if ($null -ne $latestSmokeCheckedAt -and $null -ne $longRunTaskStartedAt) {
    $latestSmokeAfterCurrentRunStart = [bool]($latestSmokeCheckedAt -ge $longRunTaskStartedAt)
    $smokeFromStart = $latestSmokeCheckedAt - $longRunTaskStartedAt
    $latestSmokeSecondsAfterCurrentRunStart = [int][Math]::Round($smokeFromStart.TotalSeconds)
}

$longRunProgressCheckedAt = if ($null -ne $longRunProgress) { Convert-ToDateTimeOffsetOrNull $longRunProgress.checkedAt } else { $null }
$longRunProgressAgeSeconds = $null
$longRunProgressAgeMinutes = $null
if ($null -ne $longRunProgressCheckedAt) {
    $longRunProgressAge = $now - $longRunProgressCheckedAt
    $longRunProgressAgeSeconds = [int][Math]::Max(0, [Math]::Round($longRunProgressAge.TotalSeconds))
    $longRunProgressAgeMinutes = [int][Math]::Max(0, [Math]::Floor($longRunProgressAge.TotalMinutes))
}

$smokeWatchProgressCheckedAt = if ($null -ne $smokeWatchProgress) { Convert-ToDateTimeOffsetOrNull $smokeWatchProgress.checkedAt } else { $null }
$smokeWatchProgressAgeSeconds = $null
$smokeWatchProgressAgeMinutes = $null
if ($null -ne $smokeWatchProgressCheckedAt) {
    $smokeWatchProgressAge = $now - $smokeWatchProgressCheckedAt
    $smokeWatchProgressAgeSeconds = [int][Math]::Max(0, [Math]::Round($smokeWatchProgressAge.TotalSeconds))
    $smokeWatchProgressAgeMinutes = [int][Math]::Max(0, [Math]::Floor($smokeWatchProgressAge.TotalMinutes))
}

$smokeWatchProgressIntervalSeconds = $scheduledLongRunSmokeIntervalSeconds
if ($null -ne $smokeWatchProgress -and $null -ne $smokeWatchProgress.PSObject.Properties["intervalSeconds"]) {
    $parsedInterval = 0
    if ([int]::TryParse($smokeWatchProgress.intervalSeconds.ToString(), [ref]$parsedInterval)) {
        $smokeWatchProgressIntervalSeconds = $parsedInterval
    }
}
$smokeWatchProgressNextExpectedAt = $null
$smokeWatchProgressNextSecondsRemaining = $null
$smokeWatchProgressNextMinutesRemaining = $null
$smokeWatchProgressNextExpectedGraceSeconds = 300
$smokeWatchProgressNextExpectedOverdue = $false
if ($null -ne $smokeWatchProgressCheckedAt -and
    $smokeWatchProgressIntervalSeconds -gt 0 -and
    $null -ne $smokeWatchProgress -and
    $smokeWatchProgress.status -eq "running" -and
    $smokeWatchProgress.remainingIterations -gt 0) {
    $smokeWatchProgressNextExpectedAt = $smokeWatchProgressCheckedAt.AddSeconds($smokeWatchProgressIntervalSeconds)
    $nextRemaining = $smokeWatchProgressNextExpectedAt - $now
    $smokeWatchProgressNextSecondsRemaining = [int][Math]::Max(0, [Math]::Round($nextRemaining.TotalSeconds))
    $smokeWatchProgressNextMinutesRemaining = [int][Math]::Max(0, [Math]::Ceiling($nextRemaining.TotalMinutes))
    $smokeWatchProgressNextExpectedOverdue = [bool]($now -gt $smokeWatchProgressNextExpectedAt.AddSeconds($smokeWatchProgressNextExpectedGraceSeconds))
}

$longRunNoProgressGraceSeconds = 0
if ($scheduledLongRunSmokeIterations -gt 0) {
    $longRunNoProgressGraceSeconds = ([Math]::Max(0, $scheduledLongRunSmokeIterations - 1) * $scheduledLongRunSmokeIntervalSeconds) + 1800
}
$longRunNoProgressOverdueAt = $null
$longRunNoProgressSecondsRemaining = $null
$longRunNoProgressMinutesRemaining = $null
if ($null -ne $longRunTaskStartedAt -and $longRunNoProgressGraceSeconds -gt 0) {
    $longRunNoProgressOverdueAt = $longRunTaskStartedAt.AddSeconds($longRunNoProgressGraceSeconds)
    $remaining = $longRunNoProgressOverdueAt - $now
    $longRunNoProgressSecondsRemaining = [int][Math]::Max(0, [Math]::Round($remaining.TotalSeconds))
    $longRunNoProgressMinutesRemaining = [int][Math]::Max(0, [Math]::Ceiling($remaining.TotalMinutes))
}
$longRunNoProgressOverdue = [bool](
    $longRunTaskRunning -and
    -not $progressKnown -and
    $longRunNoProgressGraceSeconds -gt 0 -and
    $null -ne $longRunElapsedSeconds -and
    $longRunElapsedSeconds -gt $longRunNoProgressGraceSeconds
)
$longRunObservation = "unknown"
if ($longRunTaskRunning -and $progressKnown) {
    $longRunObservation = "running_with_progress"
} elseif ($longRunNoProgressOverdue) {
    $longRunObservation = "running_without_progress_overdue"
} elseif ($longRunTaskRunning -and -not $progressKnown -and $latestSmokeAfterCurrentRunStart -eq $true) {
    $longRunObservation = "running_without_progress_latest_smoke_after_current_run_start"
} elseif ($longRunTaskRunning -and -not $progressKnown) {
    $longRunObservation = "running_without_progress_from_pre_progress_script"
} elseif (-not $longRunTaskRunning -and $null -ne $longRun) {
    $longRunObservation = "last_summary_available"
}

$status = "needs_inspection"
$nextAction = "inspect_status_summary"
$exitCode = 1

if (-not $continuationTask.found -or -not $longRunTask.found) {
    $status = "scheduled_task_missing"
    $nextAction = "re_register_browser_login_scheduled_tasks"
} elseif ($errorTargets.Count -gt 0) {
    $status = "runner_error_targets_present"
    $nextAction = "inspect_runner_status"
} elseif ($nonAccountAuditFailures.Count -gt 0) {
    $status = "completion_audit_non_account_failure"
    $nextAction = "inspect_completion_audit"
} elseif ($readyTargets.Count -gt 0) {
    $status = "ready_for_post_auth_verification"
    $nextAction = ".\scripts\browser-login-post-auth-verify.ps1 -Serial $Serial -WriteState"
    $exitCode = 0
} elseif ($verifiedTargets.Count -gt 0 -and $waitingTargets.Count -gt 0) {
    $status = "partial_account_verified_continue_watch"
    $nextAction = ".\scripts\browser-login-account-watch.ps1 -Serial $Serial -Targets $($waitingTargets -join ',') -RunReadinessFirst"
    $exitCode = 0
} elseif ($longRunNoProgressOverdue) {
    $status = "long_run_running_without_progress_overdue"
    $nextAction = "inspect_long_run_cycle_before_starting_parallel_smoke"
} elseif ($waitingTargets.Count -gt 0 -or $longRunTaskRunning) {
    $status = if ($longRunTaskRunning) { "long_run_running_waiting_for_real_account_authorization" } else { "waiting_for_real_account_authorization" }
    $nextAction = "continue_or_complete_real_account_authorization"
    $exitCode = 0
} elseif ($null -ne $completionAudit -and $completionAudit.status -eq "complete") {
    $status = "complete"
    $nextAction = "record_completion"
    $exitCode = 0
}

$checkedAt = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ssK")
$summary = [ordered]@{
    checkedAt = $checkedAt
    serial = $Serial
    status = $status
    exitCode = $exitCode
    nextAction = $nextAction
    stateDir = $StateDir
    continuationTask = $continuationTask
    longRunTask = $longRunTask
    longRunObservation = $longRunObservation
    longRunRuntime = [ordered]@{
        running = $longRunTaskRunning
        currentRunStartedAt = if ($null -ne $longRunTaskStartedAt) { $longRunTaskStartedAt.ToString("yyyy-MM-ddTHH:mm:ssK") } else { $null }
        currentRunElapsedSeconds = $longRunElapsedSeconds
        currentRunElapsedMinutes = $longRunElapsedMinutes
        scheduledSmokeIterations = $scheduledLongRunSmokeIterations
        scheduledSmokeIntervalSeconds = $scheduledLongRunSmokeIntervalSeconds
        noProgressGraceSeconds = $longRunNoProgressGraceSeconds
        noProgressOverdueAt = if ($null -ne $longRunNoProgressOverdueAt) { $longRunNoProgressOverdueAt.ToString("yyyy-MM-ddTHH:mm:ssK") } else { $null }
        noProgressSecondsRemaining = $longRunNoProgressSecondsRemaining
        noProgressMinutesRemaining = $longRunNoProgressMinutesRemaining
        noProgressOverdue = $longRunNoProgressOverdue
        latestSmokeCheckedAt = if ($null -ne $latestSmokeCheckedAt) { $latestSmokeCheckedAt.ToString("yyyy-MM-ddTHH:mm:ssK") } else { $null }
        latestSmokeAgeSeconds = $latestSmokeAgeSeconds
        latestSmokeAgeMinutes = $latestSmokeAgeMinutes
        latestSmokeAfterCurrentRunStart = $latestSmokeAfterCurrentRunStart
        latestSmokeSecondsAfterCurrentRunStart = $latestSmokeSecondsAfterCurrentRunStart
    }
    readyTargets = @($readyTargets)
    waitingTargets = @($waitingTargets)
    verifiedTargets = @($verifiedTargets)
    errorTargets = @($errorTargets)
    runner = if ($null -ne $runner) {
        [ordered]@{
            checkedAt = Format-Timestamp $runner.checkedAt
            exitCode = $runner.exitCode
            nextAction = $runner.nextAction
            path = $runnerPath
            lastWriteTime = Get-FileTimestamp $runnerPath
        }
    } else { $null }
    longRun = if ($null -ne $longRun) {
        [ordered]@{
            checkedAt = Format-Timestamp $longRun.checkedAt
            status = $longRun.status
            exitCode = $longRun.exitCode
            smokeIterations = $longRun.smokeIterations
            smokeIntervalSeconds = $longRun.smokeIntervalSeconds
            smokeWatchStatus = $longRun.smokeWatchStatus
            smokeWatchFailureCount = $longRun.smokeWatchFailureCount
            smokeWatchOpenWebP95Ms = $longRun.smokeWatchOpenWebP95Ms
            smokeWatchForegroundP95Ms = $longRun.smokeWatchForegroundP95Ms
            path = $longRunPath
            lastWriteTime = Get-FileTimestamp $longRunPath
        }
    } else { $null }
    longRunProgress = if ($null -ne $longRunProgress) {
        [ordered]@{
            checkedAt = Format-Timestamp $longRunProgress.checkedAt
            startedAt = Format-Timestamp $longRunProgress.startedAt
            status = $longRunProgress.status
            phase = $longRunProgress.phase
            message = $longRunProgress.message
            smokeIterations = $longRunProgress.smokeIterations
            smokeIntervalSeconds = $longRunProgress.smokeIntervalSeconds
            runnerExit = $longRunProgress.runnerExit
            smokeWatchExit = $longRunProgress.smokeWatchExit
            manualReadinessExit = $longRunProgress.manualReadinessExit
            completionAuditExit = $longRunProgress.completionAuditExit
            ageSeconds = $longRunProgressAgeSeconds
            ageMinutes = $longRunProgressAgeMinutes
            path = $longRunProgressPath
            lastWriteTime = Get-FileTimestamp $longRunProgressPath
        }
    } else { $null }
    smoke = if ($null -ne $smoke) {
        [ordered]@{
            checkedAt = Format-Timestamp $smoke.checkedAt
            status = $smoke.status
            schemaVersion = $smoke.schemaVersion
            openWebElapsedMs = $smoke.openWebElapsedMs
            foregroundHandoffElapsedMs = $smoke.foregroundHandoffElapsedMs
            providerPageSignalState = $smoke.providerPageSignalState
            providerPageBlockingErrorCount = $smoke.providerPageBlockingErrorCount
            providerPageChallengeHintCount = $smoke.providerPageChallengeHintCount
            appRedirectStatus = $smoke.appRedirectStatus
            appRedirectRawSecretHitCount = $smoke.appRedirectRawSecretHitCount
            appPrivateRawTemporaryValueHitCount = $smoke.appPrivateRawTemporaryValueHitCount
            path = $smokePath
            lastWriteTime = Get-FileTimestamp $smokePath
        }
    } else { $null }
    smokeWatch = if ($null -ne $smokeWatch) {
        [ordered]@{
            checkedAt = Format-Timestamp $smokeWatch.checkedAt
            status = $smokeWatch.status
            iterations = $smokeWatch.iterations
            failureCount = $smokeWatch.failureCount
            openWebP95Ms = $smokeWatch.openWebP95Ms
            foregroundP95Ms = $smokeWatch.foregroundP95Ms
            providerPageBlockingErrorRunCount = $smokeWatch.providerPageBlockingErrorRunCount
            path = $smokeWatchPath
            lastWriteTime = Get-FileTimestamp $smokeWatchPath
        }
    } else { $null }
    smokeWatchProgress = if ($null -ne $smokeWatchProgress) {
        [ordered]@{
            checkedAt = Format-Timestamp $smokeWatchProgress.checkedAt
            status = $smokeWatchProgress.status
            iterations = $smokeWatchProgress.iterations
            intervalSeconds = $smokeWatchProgressIntervalSeconds
            completedIterations = $smokeWatchProgress.completedIterations
            remainingIterations = $smokeWatchProgress.remainingIterations
            failureCount = $smokeWatchProgress.failureCount
            lastIteration = $smokeWatchProgress.lastIteration
            lastStatus = $smokeWatchProgress.lastStatus
            lastExitCode = $smokeWatchProgress.lastExitCode
            nextAction = $smokeWatchProgress.nextAction
            ageSeconds = $smokeWatchProgressAgeSeconds
            ageMinutes = $smokeWatchProgressAgeMinutes
            nextExpectedAt = if ($null -ne $smokeWatchProgressNextExpectedAt) { $smokeWatchProgressNextExpectedAt.ToString("yyyy-MM-ddTHH:mm:ssK") } else { $null }
            nextSecondsRemaining = $smokeWatchProgressNextSecondsRemaining
            nextMinutesRemaining = $smokeWatchProgressNextMinutesRemaining
            nextExpectedGraceSeconds = $smokeWatchProgressNextExpectedGraceSeconds
            nextExpectedOverdue = $smokeWatchProgressNextExpectedOverdue
            path = $smokeWatchProgressPath
            lastWriteTime = Get-FileTimestamp $smokeWatchProgressPath
        }
    } else { $null }
    manualReadiness = if ($null -ne $manualReadiness) {
        [ordered]@{
            checkedAt = Format-Timestamp $manualReadiness.checkedAt
            status = $manualReadiness.status
            failedItemIds = @(To-StringArray $manualReadiness.failedItemIds)
            path = $manualReadinessPath
            lastWriteTime = Get-FileTimestamp $manualReadinessPath
        }
    } else { $null }
    completionAudit = if ($null -ne $completionAudit) {
        [ordered]@{
            checkedAt = Format-Timestamp $completionAudit.checkedAt
            status = $completionAudit.status
            failedItemIds = @($auditFailed)
            nonAccountFailedItemIds = @($nonAccountAuditFailures)
            path = $completionAuditPath
            lastWriteTime = Get-FileTimestamp $completionAuditPath
        }
    } else { $null }
    providerPreflight = if ($null -ne $providerPreflight) {
        [ordered]@{
            checkedAt = Format-Timestamp $providerPreflight.checkedAt
            status = $providerPreflight.status
            exitCode = $providerPreflight.exitCode
            blockingFailureIds = @(To-StringArray $providerPreflight.blockingFailureIds)
            failedBuckets = @(To-StringArray $providerPreflight.failedBuckets)
            path = $providerPreflightPath
            lastWriteTime = Get-FileTimestamp $providerPreflightPath
        }
    } else { $null }
    summaryPath = $summaryPath
    reportPath = $reportPath
}

$summary | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $summaryPath -Encoding UTF8

$lines = New-Object System.Collections.Generic.List[string]
Add-Line $lines "# 浏览器登录当前状态汇总"
Add-Line $lines ""
Add-Line $lines "- 生成时间：$checkedAt"
Add-Line $lines "- 状态：$status"
Add-Line $lines "- exitCode：$exitCode"
Add-Line $lines "- nextAction：$nextAction"
Add-Line $lines "- serial：$Serial"
Add-Line $lines "- longRunObservation：$longRunObservation"
Add-Line $lines "- longRunRuntime：running=$($summary.longRunRuntime.running)，elapsedMinutes=$($summary.longRunRuntime.currentRunElapsedMinutes)，scheduledSmokeIterations=$($summary.longRunRuntime.scheduledSmokeIterations)，scheduledSmokeIntervalSeconds=$($summary.longRunRuntime.scheduledSmokeIntervalSeconds)，noProgressOverdue=$($summary.longRunRuntime.noProgressOverdue)，noProgressOverdueAt=$($summary.longRunRuntime.noProgressOverdueAt)，noProgressMinutesRemaining=$($summary.longRunRuntime.noProgressMinutesRemaining)，latestSmokeAgeMinutes=$($summary.longRunRuntime.latestSmokeAgeMinutes)，latestSmokeAfterCurrentRunStart=$($summary.longRunRuntime.latestSmokeAfterCurrentRunStart)"
Add-Line $lines ""
Add-Line $lines "## 计划任务"
Add-Line $lines ""
Add-Line $lines "- $ContinuationTaskName：found=$($continuationTask.found)，state=$($continuationTask.state)，lastResult=$($continuationTask.lastTaskResult)，nextRun=$($continuationTask.nextRunTime)"
Add-Line $lines "- $LongRunTaskName：found=$($longRunTask.found)，state=$($longRunTask.state)，lastResult=$($longRunTask.lastTaskResult)，nextRun=$($longRunTask.nextRunTime)"
Add-Line $lines ""
Add-Line $lines "## 账号状态"
Add-Line $lines ""
Add-Line $lines "- readyTargets：$($readyTargets -join ', ')"
Add-Line $lines "- waitingTargets：$($waitingTargets -join ', ')"
Add-Line $lines "- verifiedTargets：$($verifiedTargets -join ', ')"
Add-Line $lines "- errorTargets：$($errorTargets -join ', ')"
Add-Line $lines ""
Add-Line $lines "## 关键文件"
Add-Line $lines ""
Add-Line $lines "- runner：$($summary.runner.checkedAt)，exit=$($summary.runner.exitCode)"
Add-Line $lines "- long-run：$($summary.longRun.checkedAt)，status=$($summary.longRun.status)"
Add-Line $lines "- long-run progress：$($summary.longRunProgress.checkedAt)，phase=$($summary.longRunProgress.phase)，status=$($summary.longRunProgress.status)，ageMinutes=$($summary.longRunProgress.ageMinutes)，runnerExit=$($summary.longRunProgress.runnerExit)，smokeWatchExit=$($summary.longRunProgress.smokeWatchExit)"
Add-Line $lines "- smoke：$($summary.smoke.checkedAt)，status=$($summary.smoke.status)，schema=$($summary.smoke.schemaVersion)，providerBlocking=$($summary.smoke.providerPageBlockingErrorCount)，secretHits=$($summary.smoke.appPrivateRawTemporaryValueHitCount)"
Add-Line $lines "- smoke watch：$($summary.smokeWatch.checkedAt)，status=$($summary.smokeWatch.status)，iterations=$($summary.smokeWatch.iterations)，failureCount=$($summary.smokeWatch.failureCount)"
Add-Line $lines "- smoke watch progress：$($summary.smokeWatchProgress.checkedAt)，status=$($summary.smokeWatchProgress.status)，completed=$($summary.smokeWatchProgress.completedIterations)，remaining=$($summary.smokeWatchProgress.remainingIterations)，last=$($summary.smokeWatchProgress.lastStatus)，nextExpectedAt=$($summary.smokeWatchProgress.nextExpectedAt)，nextMinutesRemaining=$($summary.smokeWatchProgress.nextMinutesRemaining)，nextExpectedOverdue=$($summary.smokeWatchProgress.nextExpectedOverdue)"
Add-Line $lines "- manual readiness：$($summary.manualReadiness.checkedAt)，status=$($summary.manualReadiness.status)"
Add-Line $lines "- completion audit：$($summary.completionAudit.checkedAt)，status=$($summary.completionAudit.status)，failedItemIds=$($auditFailed -join ', ')"
Add-Line $lines "- provider preflight：$($summary.providerPreflight.checkedAt)，status=$($summary.providerPreflight.status)，exit=$($summary.providerPreflight.exitCode)"
Add-Line $lines ""
Add-Line $lines "## 边界"
Add-Line $lines ""
Add-Line $lines "- 本脚本只读取计划任务和状态文件，并写出汇总；不启动真机测试、不输入账号、不读取 token、不伪造 provider callback。"
Add-Line $lines '- `running_without_progress_from_pre_progress_script` 表示当前长跑进程可能在 progress JSON 加入前启动；应结合计划任务 state 和最新 smoke 文件判断。'
Add-Line $lines '- `running_without_progress_latest_smoke_after_current_run_start` 表示当前长跑没有 progress JSON，但最新 smoke 文件晚于当前长跑启动时间；这只能说明长跑窗口内出现过新 smoke 样本，不能单独证明该样本一定由当前长跑写入。'
Add-Line $lines '- `running_without_progress_overdue` 是只读超时提示：长跑运行中、没有 progress JSON，且已超过计划参数估算的宽限时间；它不会停止或重启计划任务。'
Add-Line $lines "- 账号真实完成仍以 runner/post-auth 官方状态命令和 completion audit 为准。"
$lines.ToArray() | Set-Content -LiteralPath $reportPath -Encoding UTF8

Write-Output "browser-login-status-summary"
Write-Output "checkedAt=$checkedAt"
Write-Output "status=$status"
Write-Output "exitCode=$exitCode"
Write-Output "longRunObservation=$longRunObservation"
Write-Output "longRunElapsedMinutes=$longRunElapsedMinutes"
Write-Output "longRunNoProgressOverdue=$longRunNoProgressOverdue"
Write-Output "longRunNoProgressOverdueAt=$($summary.longRunRuntime.noProgressOverdueAt)"
Write-Output "longRunNoProgressMinutesRemaining=$longRunNoProgressMinutesRemaining"
Write-Output "latestSmokeAgeMinutes=$latestSmokeAgeMinutes"
Write-Output "latestSmokeAfterLongRunStart=$latestSmokeAfterCurrentRunStart"
if ($null -ne $longRunProgress) {
    Write-Output "longRunProgressPhase=$($summary.longRunProgress.phase)"
    Write-Output "longRunProgressStatus=$($summary.longRunProgress.status)"
    Write-Output "longRunProgressAgeMinutes=$($summary.longRunProgress.ageMinutes)"
}
if ($null -ne $smokeWatchProgress) {
    Write-Output "smokeWatchProgressStatus=$($summary.smokeWatchProgress.status)"
    Write-Output "smokeWatchProgressCompleted=$($summary.smokeWatchProgress.completedIterations)"
    Write-Output "smokeWatchProgressRemaining=$($summary.smokeWatchProgress.remainingIterations)"
    Write-Output "smokeWatchProgressNextExpectedAt=$($summary.smokeWatchProgress.nextExpectedAt)"
    Write-Output "smokeWatchProgressNextMinutesRemaining=$($summary.smokeWatchProgress.nextMinutesRemaining)"
    Write-Output "smokeWatchProgressNextExpectedOverdue=$($summary.smokeWatchProgress.nextExpectedOverdue)"
}
Write-Output "readyTargets=$($readyTargets -join ',')"
Write-Output "waitingTargets=$($waitingTargets -join ',')"
Write-Output "verifiedTargets=$($verifiedTargets -join ',')"
Write-Output "errorTargets=$($errorTargets -join ',')"
if ($null -ne $smoke) {
    Write-Output "smokeCheckedAt=$($summary.smoke.checkedAt)"
    Write-Output "smokeStatus=$($summary.smoke.status)"
    Write-Output "smokeProviderBlockingErrorCount=$($summary.smoke.providerPageBlockingErrorCount)"
}
if ($null -ne $providerPreflight) {
    Write-Output "providerPreflightStatus=$($summary.providerPreflight.status)"
    Write-Output "providerPreflightExitCode=$($summary.providerPreflight.exitCode)"
}
Write-Output "json=$summaryPath"
Write-Output "report=$reportPath"
exit $exitCode
