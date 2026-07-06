param(
    [string]$RepoRoot = "",
    [string]$StateDir = "",
    [string]$Serial = "3f8bbaad",
    [switch]$RefreshState,
    [switch]$RunSmokeWatch,
    [int]$SmokeIterations = 3,
    [int]$SmokeIntervalSeconds = 0,
    [int]$AuditFreshHours = 24,
    [switch]$RunCompletionAudit
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($RepoRoot)) {
    $RepoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
} else {
    $RepoRoot = Resolve-Path $RepoRoot
}

if ([string]::IsNullOrWhiteSpace($StateDir)) {
    $StateDir = Join-Path $env:LOCALAPPDATA "Kite\browser-login-continuation"
}

$runnerScript = Join-Path $RepoRoot "scripts\browser-login-continuation-runner.ps1"
$smokeWatchScript = Join-Path $RepoRoot "scripts\browser-login-smoke-watch.ps1"
$completionAuditScript = Join-Path $RepoRoot "scripts\browser-login-completion-audit.ps1"
$readinessJsonPath = Join-Path $StateDir "manual-account-readiness.json"
$readinessReportPath = Join-Path $StateDir "manual-account-readiness.md"

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

function Format-Items {
    param([string[]]$Items)

    if ($Items.Count -eq 0) {
        return "(none)"
    }
    return ($Items -join ", ")
}

function Format-Status {
    param([bool]$Passed)

    if ($Passed) {
        return "PASS"
    }
    return "MISS"
}

function Get-JsonPropertyString {
    param(
        [object]$Object,
        [string]$Name
    )

    if ($null -eq $Object -or $null -eq $Object.PSObject.Properties[$Name]) {
        return ""
    }
    return $Object.PSObject.Properties[$Name].Value.ToString()
}

function Get-JsonPropertyInt {
    param(
        [object]$Object,
        [string]$Name,
        [int]$DefaultValue = -1
    )

    if ($null -eq $Object -or $null -eq $Object.PSObject.Properties[$Name]) {
        return $DefaultValue
    }
    $raw = $Object.PSObject.Properties[$Name].Value
    if ($null -eq $raw) {
        return $DefaultValue
    }
    $parsed = 0
    if ([int]::TryParse($raw.ToString(), [ref]$parsed)) {
        return $parsed
    }
    return $DefaultValue
}

function Test-Fresh {
    param(
        [object]$Object,
        [int]$Hours = 24
    )

    $checkedAt = Get-JsonPropertyString $Object "checkedAt"
    if ([string]::IsNullOrWhiteSpace($checkedAt)) {
        return $false
    }
    $parsed = [datetimeoffset]::MinValue
    if (-not [datetimeoffset]::TryParse($checkedAt, [ref]$parsed)) {
        return $false
    }
    return ([datetimeoffset]::Now - $parsed) -le [TimeSpan]::FromHours($Hours)
}

function Add-ReadinessItem {
    param(
        [System.Collections.Generic.List[object]]$Items,
        [string]$Id,
        [string]$Title,
        [bool]$Passed,
        [string]$Evidence,
        [string]$NextAction
    )

    $Items.Add([ordered]@{
        id = $Id
        title = $Title
        passed = $Passed
        evidence = $Evidence
        nextAction = $NextAction
    })
}

New-Item -ItemType Directory -Force -Path $StateDir | Out-Null

$runnerExit = $null
$smokeWatchExit = $null
$completionAuditExit = $null

if ($RefreshState) {
    if (-not (Test-Path -LiteralPath $runnerScript)) {
        throw "Runner script not found: $runnerScript"
    }
    & $runnerScript -Serial $Serial -StateDir $StateDir | Out-Null
    $runnerExit = $LASTEXITCODE
}

if ($RunSmokeWatch) {
    if (-not (Test-Path -LiteralPath $smokeWatchScript)) {
        throw "Smoke watch script not found: $smokeWatchScript"
    }
    & $smokeWatchScript -Serial $Serial -StateDir $StateDir -Iterations $SmokeIterations -IntervalSeconds $SmokeIntervalSeconds | Out-Null
    $smokeWatchExit = $LASTEXITCODE
}

if ($RunCompletionAudit) {
    if (-not (Test-Path -LiteralPath $completionAuditScript)) {
        throw "Completion audit script not found: $completionAuditScript"
    }
    & $completionAuditScript -RepoRoot $RepoRoot -StateDir $StateDir -RefreshState | Out-Null
    $completionAuditExit = $LASTEXITCODE
}

$smokeState = Read-JsonOrNull (Join-Path $StateDir "browser-login-smoke.json")
$smokeWatchState = Read-JsonOrNull (Join-Path $StateDir "browser-login-smoke-watch.json")
$runnerState = Read-JsonOrNull (Join-Path $StateDir "runner-status.json")
$completionAuditState = Read-JsonOrNull (Join-Path $StateDir "completion-audit.json")
$accountWatchState = Read-JsonOrNull (Join-Path $StateDir "account-watch-status.json")
$manualAccountStartState = Read-JsonOrNull (Join-Path $StateDir "manual-account-start-status.json")

$items = New-Object System.Collections.Generic.List[object]

$requiredDocs = @(
    "docs\browser-login\PLAYBOOK.md",
    "docs\browser-login\PROGRESS.md",
    "docs\browser-login\DECISIONS.md",
    "docs\browser-login\LOGIN_TEST_STRATEGY.md",
    "docs\browser-login\COMPATIBILITY_MATRIX.md"
)
$missingDocs = @($requiredDocs | Where-Object { -not (Test-Path -LiteralPath (Join-Path $RepoRoot $_)) })
Add-ReadinessItem `
    -Items $items `
    -Id "t0-docs" `
    -Title "T0 官方合规和测试策略文档存在" `
    -Passed ($missingDocs.Count -eq 0) `
    -Evidence "missing=$(Format-Items $missingDocs)" `
    -NextAction "补齐 docs/browser-login 三件套、LOGIN_TEST_STRATEGY 和 COMPATIBILITY_MATRIX"

$requiredScripts = @(
    "scripts\browser-login-smoke-test.ps1",
    "scripts\browser-login-smoke-watch.ps1",
    "scripts\browser-login-account-watch.ps1",
    "scripts\browser-login-manual-account-start.ps1",
    "scripts\browser-login-completion-audit.ps1",
    "scripts\browser-login-long-run-cycle.ps1",
    "scripts\register-browser-login-long-run-cycle.ps1",
    "scripts\test-browser-login-continuation.ps1"
)
$missingScripts = @($requiredScripts | Where-Object { -not (Test-Path -LiteralPath (Join-Path $RepoRoot $_)) })
Add-ReadinessItem `
    -Items $items `
    -Id "t1-scripts" `
    -Title "T1-T5 自动化脚本存在" `
    -Passed ($missingScripts.Count -eq 0) `
    -Evidence "missing=$(Format-Items $missingScripts)" `
    -NextAction "补齐 smoke、watch、account watch、completion audit、long-run cycle 和续跑自测试脚本"

$smokeFresh = Test-Fresh $smokeState 24
$smokeSchemaVersion = Get-JsonPropertyInt $smokeState "schemaVersion" 0
$smokeFailedItems = if ($null -ne $smokeState -and $null -ne $smokeState.PSObject.Properties["failedItemIds"]) {
    To-StringArray $smokeState.failedItemIds
} else {
    @()
}
$smokeOk = $null -ne $smokeState -and
    $smokeState.status -eq "passed" -and
    $smokeFresh -and
    $smokeSchemaVersion -ge 9 -and
    $smokeFailedItems.Count -eq 0
Add-ReadinessItem `
    -Items $items `
    -Id "t2-smoke" `
    -Title "T2 最近真机 smoke test 通过" `
    -Passed $smokeOk `
    -Evidence "status=$(Get-JsonPropertyString $smokeState 'status'); schemaVersion=$smokeSchemaVersion; fresh24h=$smokeFresh; failedItemIds=$(Format-Items $smokeFailedItems)" `
    -NextAction ".\scripts\browser-login-smoke-test.ps1 -Serial $Serial"

$smokeWatchFresh = Test-Fresh $smokeWatchState 24
$smokeWatchFailureCount = Get-JsonPropertyInt $smokeWatchState "failureCount" -1
$smokeWatchOk = $null -ne $smokeWatchState -and
    $smokeWatchState.status -eq "passed" -and
    $smokeWatchFresh -and
    $smokeWatchFailureCount -eq 0
Add-ReadinessItem `
    -Items $items `
    -Id "t3-smoke-watch" `
    -Title "T3 最近多轮 smoke watch 通过" `
    -Passed $smokeWatchOk `
    -Evidence "status=$(Get-JsonPropertyString $smokeWatchState 'status'); iterations=$(Get-JsonPropertyInt $smokeWatchState 'iterations' -1); failureCount=$smokeWatchFailureCount; fresh24h=$smokeWatchFresh; openWebP95Ms=$(Get-JsonPropertyInt $smokeWatchState 'openWebP95Ms' -1); foregroundP95Ms=$(Get-JsonPropertyInt $smokeWatchState 'foregroundP95Ms' -1)" `
    -NextAction ".\scripts\browser-login-smoke-watch.ps1 -Serial $Serial -Iterations 3 -IntervalSeconds 0"

$runnerKnown = $null -ne $runnerState
$readyTargets = if ($runnerKnown -and $null -ne $runnerState.PSObject.Properties["readyTargets"]) {
    To-StringArray $runnerState.readyTargets
} else {
    @()
}
$waitingTargets = if ($runnerKnown -and $null -ne $runnerState.PSObject.Properties["waitingTargets"]) {
    To-StringArray $runnerState.waitingTargets
} else {
    @()
}
$verifiedTargets = if ($runnerKnown -and $null -ne $runnerState.PSObject.Properties["verifiedTargets"]) {
    To-StringArray $runnerState.verifiedTargets
} else {
    @()
}
$errorTargets = if ($runnerKnown -and $null -ne $runnerState.PSObject.Properties["errorTargets"]) {
    To-StringArray $runnerState.errorTargets
} else {
    @()
}
$runnerExitCode = Get-JsonPropertyInt $runnerState "exitCode" -1
$runnerOk = $runnerKnown -and $errorTargets.Count -eq 0 -and ($runnerExitCode -eq 0 -or $runnerExitCode -eq 2)
Add-ReadinessItem `
    -Items $items `
    -Id "t4-runner-state" `
    -Title "T4 runner 当前状态可读且无环境错误" `
    -Passed $runnerOk `
    -Evidence "exitCode=$runnerExitCode; readyTargets=$(Format-Items $readyTargets); waitingTargets=$(Format-Items $waitingTargets); verifiedTargets=$(Format-Items $verifiedTargets); errorTargets=$(Format-Items $errorTargets); runnerExit=$runnerExit" `
    -NextAction ".\scripts\browser-login-continuation-runner.ps1 -Serial $Serial"

$auditKnown = $null -ne $completionAuditState
$auditFresh = Test-Fresh $completionAuditState $AuditFreshHours
$auditFailedItems = if ($auditKnown -and $null -ne $completionAuditState.PSObject.Properties["failedItemIds"]) {
    To-StringArray $completionAuditState.failedItemIds
} else {
    @()
}
$allowedAccountGaps = @("codex-account", "claude-account")
$unexpectedAuditFailures = @($auditFailedItems | Where-Object { $allowedAccountGaps -notcontains $_ })
$auditOnlyAccountGaps = $auditKnown -and
    $auditFresh -and
    ($completionAuditState.status -eq "complete" -or $unexpectedAuditFailures.Count -eq 0)
Add-ReadinessItem `
    -Items $items `
    -Id "t5-completion-audit-shape" `
    -Title "T5 完成审计新鲜且没有账号以外的新缺口" `
    -Passed $auditOnlyAccountGaps `
    -Evidence "status=$(Get-JsonPropertyString $completionAuditState 'status'); checkedAt=$(Get-JsonPropertyString $completionAuditState 'checkedAt'); fresh${AuditFreshHours}h=$auditFresh; failedItemIds=$(Format-Items $auditFailedItems); unexpected=$(Format-Items $unexpectedAuditFailures); completionAuditExit=$completionAuditExit" `
    -NextAction ".\scripts\browser-login-completion-audit.ps1 -RefreshState"

$accountWatchReady = $false
$accountWatchEvidence = "account-watch-status.json missing"
if ($null -ne $accountWatchState) {
    $accountWatchStatus = Get-JsonPropertyString $accountWatchState "status"
    $accountWatchReady = $accountWatchStatus -eq "waiting_for_real_account_authorization" -or
        $accountWatchStatus -eq "verified"
    $accountWatchEvidence = "status=$accountWatchStatus; targets=$(Format-Items (To-StringArray $accountWatchState.targets)); waitingTargets=$(Format-Items (To-StringArray $accountWatchState.waitingTargets)); verifiedTargets=$(Format-Items (To-StringArray $accountWatchState.verifiedTargets))"
}
Add-ReadinessItem `
    -Items $items `
    -Id "t4-account-watch-known" `
    -Title "T4 account watch 状态存在或可重新生成" `
    -Passed ($accountWatchReady -or $runnerOk) `
    -Evidence $accountWatchEvidence `
    -NextAction ".\scripts\browser-login-account-watch.ps1 -Serial $Serial -Targets codex,claude -RunSmokeFirst -RunReadinessFirst -RunCompletionAuditOnVerified -TimeoutMinutes 60 -PollSeconds 30"

$manualStartStatus = ""
$manualStartTargets = @()
$manualStartLaunchedTargets = @()
$manualStartWatchExit = -1
$manualStartWatchMaxAttempts = -1
$manualStartReady = $runnerOk
$manualStartEvidence = "manual-account-start-status.json missing; can regenerate with manual-account-start"
if ($null -ne $manualAccountStartState) {
    $manualStartStatus = Get-JsonPropertyString $manualAccountStartState "status"
    $manualStartTargets = To-StringArray $manualAccountStartState.targets
    $manualStartLaunchedTargets = To-StringArray $manualAccountStartState.launchedTargets
    $manualStartWatchExit = Get-JsonPropertyInt $manualAccountStartState "watchExit" -1
    $manualStartWatchMaxAttempts = Get-JsonPropertyInt $manualAccountStartState "watchMaxAttempts" -1
    $manualStartAcceptedStatuses = @(
        "planned",
        "launched",
        "watch_waiting_for_real_account_authorization",
        "watch_verified"
    )
    $manualStartNeedsInspectionStatuses = @(
        "launch_failed",
        "watch_needs_inspection"
    )
    if ($manualStartAcceptedStatuses -contains $manualStartStatus) {
        $manualStartReady = $true
    } elseif ($manualStartNeedsInspectionStatuses -contains $manualStartStatus) {
        $manualStartReady = $false
    }
    $manualStartEvidence = "status=$manualStartStatus; targets=$(Format-Items $manualStartTargets); launchedTargets=$(Format-Items $manualStartLaunchedTargets); watchExit=$manualStartWatchExit; watchMaxAttempts=$manualStartWatchMaxAttempts"
}
Add-ReadinessItem `
    -Items $items `
    -Id "t4-manual-account-start-known" `
    -Title "T4 人工账号启动入口状态存在或可重新生成" `
    -Passed $manualStartReady `
    -Evidence $manualStartEvidence `
    -NextAction ".\scripts\browser-login-manual-account-start.ps1 -Serial $Serial -Targets codex,claude -StartWatch -RunCompletionAuditOnVerified"

$failedItems = @($items | Where-Object { -not $_.passed })
$failedItemIds = @($failedItems | ForEach-Object { $_["id"] })
$manualStartBlocking = $failedItemIds -contains "t4-manual-account-start-known"
$allVerified = ($verifiedTargets -contains "codex") -and ($verifiedTargets -contains "claude")
$anyVerified = $verifiedTargets.Count -gt 0
$allPreflight = $failedItemIds.Count -eq 0
$onlyAccountGaps = $auditOnlyAccountGaps -and
    (($auditFailedItems -contains "codex-account") -or ($auditFailedItems -contains "claude-account"))

$status = "not_ready"
$nextAction = "fix_failed_readiness_items"
if ($auditKnown -and $auditFresh -and $completionAuditState.status -eq "complete" -and $allVerified) {
    $status = "complete"
    $nextAction = "no_action"
} elseif ($allPreflight -and $allVerified) {
    $status = "account_verified_run_completion_audit"
    $nextAction = ".\scripts\browser-login-completion-audit.ps1 -RefreshState"
} elseif ($allPreflight -and $anyVerified) {
    $status = "partial_account_verified_continue_watch"
    $nextAction = ".\scripts\browser-login-account-watch.ps1 -Serial $Serial -Targets codex,claude -RunSmokeFirst -RunReadinessFirst -RunCompletionAuditOnVerified -TimeoutMinutes 60 -PollSeconds 30"
} elseif ($allPreflight -or ($smokeOk -and $smokeWatchOk -and $runnerOk -and $onlyAccountGaps -and -not $manualStartBlocking)) {
    $status = "ready_for_manual_account"
    $nextAction = ".\scripts\browser-login-manual-account-start.ps1 -Serial $Serial -Targets codex,claude -StartWatch -RunCompletionAuditOnVerified"
}

$checkedAt = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ssK")
$readiness = [ordered]@{
    checkedAt = $checkedAt
    status = $status
    repoRoot = $RepoRoot.ToString()
    stateDir = $StateDir
    serial = $Serial
    refreshState = [bool]$RefreshState
    runSmokeWatch = [bool]$RunSmokeWatch
    runCompletionAudit = [bool]$RunCompletionAudit
    auditFreshHours = $AuditFreshHours
    auditFresh = $auditFresh
    runnerExit = $runnerExit
    smokeWatchExit = $smokeWatchExit
    completionAuditExit = $completionAuditExit
    readyTargets = @($readyTargets)
    waitingTargets = @($waitingTargets)
    verifiedTargets = @($verifiedTargets)
    manualStartStatus = $manualStartStatus
    manualStartTargets = @($manualStartTargets)
    manualStartLaunchedTargets = @($manualStartLaunchedTargets)
    manualStartWatchExit = $manualStartWatchExit
    manualStartWatchMaxAttempts = $manualStartWatchMaxAttempts
    failedItemIds = @($failedItemIds)
    auditFailedItemIds = @($auditFailedItems)
    nextAction = $nextAction
    items = @($items.ToArray())
}

$readiness | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $readinessJsonPath -Encoding UTF8

$lines = New-Object System.Collections.Generic.List[string]
$lines.Add("# 浏览器登录人工账号验证准备度")
$lines.Add("")
$lines.Add("- 生成时间：$checkedAt")
$lines.Add("- 状态：$status")
$lines.Add("- 设备：$Serial")
$lines.Add("- verifiedTargets：$(Format-Items $verifiedTargets)")
$lines.Add("- waitingTargets：$(Format-Items $waitingTargets)")
$lines.Add("- manualStartStatus：$manualStartStatus")
$lines.Add("- manualStartLaunchedTargets：$(Format-Items $manualStartLaunchedTargets)")
$lines.Add("- 下一步：$nextAction")
$lines.Add("")
$lines.Add("## 检查项")
$lines.Add("")
foreach ($item in $items) {
    $lines.Add("- $(Format-Status ([bool]$item["passed"])) ``$($item["id"])``：$($item["title"])")
    $lines.Add("  - 证据：$($item["evidence"])")
    $lines.Add("  - 下一步：$($item["nextAction"])")
}
$lines.Add("")
$lines.Add("## 边界")
$lines.Add("")
$lines.Add("- ``ready_for_manual_account`` 只表示 Kite 可控链路已准备好进入真人账号挑战，不表示 Google/OpenAI/Claude 账号已经通过。")
$lines.Add("- 本脚本不输入账号、不读取 token、不伪造 provider callback；真实完成仍以官方状态命令和 completion audit 为准。")
$lines.ToArray() | Set-Content -LiteralPath $readinessReportPath -Encoding UTF8

Write-Output "browser-login-manual-readiness"
Write-Output "checkedAt=$checkedAt"
Write-Output "status=$status"
Write-Output "verifiedTargets=$($verifiedTargets -join ',')"
Write-Output "waitingTargets=$($waitingTargets -join ',')"
Write-Output "failedItemIds=$($failedItemIds -join ',')"
Write-Output "nextAction=$nextAction"
Write-Output "json=$readinessJsonPath"
Write-Output "report=$readinessReportPath"

if ($status -eq "not_ready") {
    exit 1
}
exit 0
