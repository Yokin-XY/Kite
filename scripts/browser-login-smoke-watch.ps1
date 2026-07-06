param(
    [string]$Serial = "3f8bbaad",
    [string]$StateDir = "",
    [string]$SmokeTestScript = "",
    [int]$Iterations = 3,
    [int]$IntervalSeconds = 60,
    [int]$OpenWebP95ThresholdMs = 1500,
    [int]$ForegroundP95ThresholdMs = 5000,
    [int]$MaxFailureCount = 0,
    [switch]$LeaveBrowserOpen
)

$ErrorActionPreference = "Stop"

if ($Iterations -lt 1) {
    throw "Iterations must be at least 1"
}
if ($IntervalSeconds -lt 0) {
    throw "IntervalSeconds must be 0 or greater"
}

if ([string]::IsNullOrWhiteSpace($StateDir)) {
    $StateDir = Join-Path $env:LOCALAPPDATA "Kite\browser-login-continuation"
}
if ([string]::IsNullOrWhiteSpace($SmokeTestScript)) {
    $SmokeTestScript = Join-Path $PSScriptRoot "browser-login-smoke-test.ps1"
}

$SmokeTestScript = (Resolve-Path $SmokeTestScript).Path

New-Item -ItemType Directory -Force -Path $StateDir | Out-Null
$watchDir = Join-Path $StateDir "browser-login-smoke-watch"
New-Item -ItemType Directory -Force -Path $watchDir | Out-Null

$statusPath = Join-Path $StateDir "browser-login-smoke-watch.json"
$progressPath = Join-Path $StateDir "browser-login-smoke-watch-progress.json"
$reportPath = Join-Path $StateDir "browser-login-smoke-watch.md"
$smokeJsonPath = Join-Path $StateDir "browser-login-smoke.json"

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
    $parsed = 0
    if ([int]::TryParse($raw.ToString(), [ref]$parsed)) {
        return $parsed
    }
    return $DefaultValue
}

function Get-Percentile {
    param(
        [object[]]$Values,
        [double]$Percentile = 95
    )

    $numeric = @(
        $Values |
            Where-Object { $null -ne $_ } |
            ForEach-Object { [int64]$_ } |
            Where-Object { $_ -ge 0 } |
            Sort-Object
    )
    if ($numeric.Count -eq 0) {
        return -1
    }
    $rank = [int][Math]::Ceiling(($Percentile / 100.0) * $numeric.Count) - 1
    $rank = [Math]::Max(0, [Math]::Min($rank, $numeric.Count - 1))
    return [int64]$numeric[$rank]
}

function Format-Status {
    param([bool]$Passed)

    if ($Passed) {
        return "PASS"
    }
    return "MISS"
}

function Format-Items {
    param([string[]]$Items)

    if ($Items.Count -eq 0) {
        return "(none)"
    }
    return ($Items -join ", ")
}

function Format-RunLine {
    param([object]$Run)

    $failed = Format-Items (To-StringArray $Run.failedItemIds)
    return "| $($Run.iteration) | $($Run.status) | $($Run.exitCode) | $($Run.openWebElapsedMs) | $($Run.appRedirectOpenWebElapsedMs) | $($Run.foregroundHandoffElapsedMs) | $($Run.providerOAuthForegroundMaxElapsedMs) | $($Run.providerPageSignalState) | $($Run.providerPageBlockingErrorCount) | $($Run.httpsBrowserResolvePackage) | $failed |"
}

function Write-WatchProgress {
    param(
        [string]$Status,
        [int]$CompletedIterations,
        [object]$LastRecord = $null,
        [string]$NextAction = "continue_smoke_watch"
    )

    $recordsSnapshot = if ($null -ne $script:runRecords) {
        @($script:runRecords.ToArray())
    } else {
        @()
    }
    $failedSoFar = @($recordsSnapshot | Where-Object { -not [bool]$_["passed"] })
    $lastIteration = $null
    $lastStatus = ""
    $lastExitCode = $null
    if ($null -ne $LastRecord) {
        $lastIteration = $LastRecord["iteration"]
        $lastStatus = $LastRecord["status"]
        $lastExitCode = $LastRecord["exitCode"]
    }

    $progress = [ordered]@{
        checkedAt = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ssK")
        status = $Status
        serial = $Serial
        stateDir = $StateDir
        iterations = $Iterations
        intervalSeconds = $IntervalSeconds
        completedIterations = $CompletedIterations
        remainingIterations = [Math]::Max(0, $Iterations - $CompletedIterations)
        failureCount = $failedSoFar.Count
        lastIteration = $lastIteration
        lastStatus = $lastStatus
        lastExitCode = $lastExitCode
        nextAction = $NextAction
        progressPath = $progressPath
        summaryPath = $statusPath
        reportPath = $reportPath
    }
    $progress | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $progressPath -Encoding UTF8
}

$runRecords = New-Object System.Collections.Generic.List[object]
Write-WatchProgress -Status "running" -CompletedIterations 0 -NextAction "start_smoke_watch"

for ($iteration = 1; $iteration -le $Iterations; $iteration++) {
    $startedAt = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ssK")
    if ($LeaveBrowserOpen) {
        & $SmokeTestScript -Serial $Serial -StateDir $StateDir -LeaveBrowserOpen | Out-Null
    } else {
        & $SmokeTestScript -Serial $Serial -StateDir $StateDir | Out-Null
    }
    $smokeExit = $LASTEXITCODE
    $endedAt = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ssK")

    $smoke = Read-JsonOrNull $smokeJsonPath
    $smokeStatus = Get-JsonPropertyString $smoke "status"
    $failedItemIds = if ($null -ne $smoke -and $null -ne $smoke.PSObject.Properties["failedItemIds"]) {
        To-StringArray $smoke.failedItemIds
    } else {
        @("smoke-json-missing")
    }
    $runPassed = $smokeExit -eq 0 -and $smokeStatus -eq "passed"
    $iterationCopyPath = Join-Path $watchDir ("smoke-iteration-{0:000}.json" -f $iteration)
    if (Test-Path -LiteralPath $smokeJsonPath) {
        Copy-Item -LiteralPath $smokeJsonPath -Destination $iterationCopyPath -Force
    }

    $record = [ordered]@{
        iteration = $iteration
        startedAt = $startedAt
        endedAt = $endedAt
        exitCode = $smokeExit
        status = $smokeStatus
        passed = $runPassed
        schemaVersion = Get-JsonPropertyInt $smoke "schemaVersion" 0
        failedItemIds = @($failedItemIds)
        openWebElapsedMs = Get-JsonPropertyInt $smoke "openWebElapsedMs" -1
        appRedirectOpenWebElapsedMs = Get-JsonPropertyInt $smoke "appRedirectOpenWebElapsedMs" -1
        localWebOpenWebElapsedMs = Get-JsonPropertyInt $smoke "localWebOpenWebElapsedMs" -1
        foregroundHandoffElapsedMs = Get-JsonPropertyInt $smoke "foregroundHandoffElapsedMs" -1
        providerOAuthForegroundMaxElapsedMs = Get-JsonPropertyInt $smoke "providerOAuthForegroundMaxElapsedMs" -1
        providerOAuthNewSessionCount = Get-JsonPropertyInt $smoke "providerOAuthNewSessionCount" -1
        providerPageSignalState = Get-JsonPropertyString $smoke "providerPageSignalState"
        providerPageBlockingErrorCount = Get-JsonPropertyInt $smoke "providerPageBlockingErrorCount" -1
        providerPageChallengeHintCount = Get-JsonPropertyInt $smoke "providerPageChallengeHintCount" -1
        appPrivateRawTemporaryValueHitCount = Get-JsonPropertyInt $smoke "appPrivateRawTemporaryValueHitCount" -1
        httpsBrowserResolvePackage = Get-JsonPropertyString $smoke "httpsBrowserResolvePackage"
        httpsBrowserResolveActivity = Get-JsonPropertyString $smoke "httpsBrowserResolveActivity"
        foregroundPackage = Get-JsonPropertyString $smoke "foregroundPackage"
        foregroundActivity = Get-JsonPropertyString $smoke "foregroundActivity"
        customTabsServiceCount = Get-JsonPropertyInt $smoke "customTabsServiceCount" -1
        smokeJsonCopy = $iterationCopyPath
    }
    $runRecords.Add($record)
    $iterationNextAction = if ($iteration -lt $Iterations) { "sleep_before_next_smoke_iteration" } else { "summarize_smoke_watch" }
    Write-WatchProgress -Status "running" -CompletedIterations $iteration -LastRecord $record -NextAction $iterationNextAction

    if ($iteration -lt $Iterations -and $IntervalSeconds -gt 0) {
        Start-Sleep -Seconds $IntervalSeconds
    }
}

$records = @($runRecords.ToArray())
$failedRecords = @($records | Where-Object { -not [bool]$_["passed"] })
$openWebElapsedValues = @(
    $records | ForEach-Object {
        $_["openWebElapsedMs"]
        $_["appRedirectOpenWebElapsedMs"]
        $_["localWebOpenWebElapsedMs"]
    }
)
$foregroundElapsedValues = @(
    $records | ForEach-Object {
        $_["foregroundHandoffElapsedMs"]
        $_["providerOAuthForegroundMaxElapsedMs"]
    }
)
$openWebP95Ms = Get-Percentile $openWebElapsedValues 95
$foregroundP95Ms = Get-Percentile $foregroundElapsedValues 95
$handlerPackages = @(
    $records |
        ForEach-Object { $_["httpsBrowserResolvePackage"] } |
        Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
        Sort-Object -Unique
)
$handlerStable = $handlerPackages.Count -eq 1 -and $handlerPackages[0] -ne "com.kite.app"
$providerSessionLeakRuns = @(
    $records |
        Where-Object { [int]$_["providerOAuthNewSessionCount"] -ne 0 }
)
$secretLeakRuns = @(
    $records |
        Where-Object { [int]$_["appPrivateRawTemporaryValueHitCount"] -ne 0 }
)
$providerPageBlockingErrorRuns = @(
    $records |
        Where-Object {
            [int]$_["providerPageBlockingErrorCount"] -ne 0 -or
            $_["providerPageSignalState"] -eq "blocking_error"
        }
)
$openWebP95Ok = $openWebP95Ms -ge 0 -and $openWebP95Ms -le $OpenWebP95ThresholdMs
$foregroundP95Ok = $foregroundP95Ms -ge 0 -and $foregroundP95Ms -le $ForegroundP95ThresholdMs
$failureCountOk = $failedRecords.Count -le $MaxFailureCount
$noLeaks = $providerSessionLeakRuns.Count -eq 0 -and $secretLeakRuns.Count -eq 0
$noProviderPageBlockingErrors = $providerPageBlockingErrorRuns.Count -eq 0
$status = if ($failureCountOk -and $openWebP95Ok -and $foregroundP95Ok -and $handlerStable -and $noLeaks -and $noProviderPageBlockingErrors) {
    "passed"
} else {
    "failed"
}
$checkedAt = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ssK")
$nextAction = if ($status -eq "passed") {
    "ready_for_manual_account_validation_or_continue_long_run"
} else {
    "inspect_failed_smoke_iteration_or_device_browser_state"
}

$summary = [ordered]@{
    checkedAt = $checkedAt
    status = $status
    serial = $Serial
    stateDir = $StateDir
    smokeTestScript = $SmokeTestScript
    iterations = $Iterations
    intervalSeconds = $IntervalSeconds
    maxFailureCount = $MaxFailureCount
    failureCount = $failedRecords.Count
    openWebP95ThresholdMs = $OpenWebP95ThresholdMs
    openWebP95Ms = $openWebP95Ms
    foregroundP95ThresholdMs = $ForegroundP95ThresholdMs
    foregroundP95Ms = $foregroundP95Ms
    handlerPackages = @($handlerPackages)
    handlerStable = $handlerStable
    providerSessionLeakRunCount = $providerSessionLeakRuns.Count
    providerPageBlockingErrorRunCount = $providerPageBlockingErrorRuns.Count
    secretLeakRunCount = $secretLeakRuns.Count
    nextAction = $nextAction
    runs = @($records)
}
$summary | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $statusPath -Encoding UTF8
Write-WatchProgress -Status $status -CompletedIterations $Iterations -LastRecord $records[-1] -NextAction $nextAction

$lines = New-Object System.Collections.Generic.List[string]
$lines.Add("# 浏览器登录 smoke watch")
$lines.Add("")
$lines.Add("- 生成时间：$checkedAt")
$lines.Add("- 状态：$status")
$lines.Add("- serial：$Serial")
$lines.Add("- iterations：$Iterations")
$lines.Add("- intervalSeconds：$IntervalSeconds")
$lines.Add("- failureCount：$($failedRecords.Count)，maxFailureCount：$MaxFailureCount")
$lines.Add("- openWebP95Ms：$openWebP95Ms，threshold：$OpenWebP95ThresholdMs")
$lines.Add("- foregroundP95Ms：$foregroundP95Ms，threshold：$ForegroundP95ThresholdMs")
$lines.Add("- handlerPackages：$(Format-Items (To-StringArray $handlerPackages))，stable：$handlerStable")
$lines.Add("- providerSessionLeakRunCount：$($providerSessionLeakRuns.Count)")
$lines.Add("- providerPageBlockingErrorRunCount：$($providerPageBlockingErrorRuns.Count)")
$lines.Add("- secretLeakRunCount：$($secretLeakRuns.Count)")
$lines.Add("- nextAction：$nextAction")
$lines.Add("")
$lines.Add("## 趋势检查")
$lines.Add("")
$lines.Add("- $(Format-Status $failureCountOk) ``failure-count``：失败次数不超过阈值。")
$lines.Add("- $(Format-Status $openWebP95Ok) ``open-web-p95``：本地 /open-web 与普通 localhost handoff p95 不超过阈值。")
$lines.Add("- $(Format-Status $foregroundP95Ok) ``foreground-p95``：OAuth handoff 到外部浏览器前台 p95 不超过阈值。")
$lines.Add("- $(Format-Status $handlerStable) ``handler-stable``：HTTPS 默认浏览器 handler 稳定且不是 Kite。")
$lines.Add("- $(Format-Status $noLeaks) ``no-session-or-secret-leak``：多站点 OAuth smoke 未新增假 session，本轮临时值未原文落盘。")
$lines.Add("- $(Format-Status $noProviderPageBlockingErrors) ``no-provider-page-blocking-error``：外部 provider 页面没有阻塞性错误信号。")
$lines.Add("")
$lines.Add("## 迭代明细")
$lines.Add("")
$lines.Add("| # | status | exit | openWebMs | appRedirectMs | googleForegroundMs | providerMaxForegroundMs | pageSignal | blockingErrors | handler | failedItemIds |")
$lines.Add("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |")
foreach ($run in $records) {
    $lines.Add((Format-RunLine $run))
}
$lines.Add("")
$lines.Add("## 边界")
$lines.Add("")
$lines.Add("- 本脚本不输入账号、不读取 token、不伪造 provider callback。")
$lines.Add("- 本脚本证明的是多轮无账号 smoke 的稳定性、性能趋势和环境一致性；真实 Codex/Claude 账号完成仍必须由 account watch、runner/post-auth 和完成审计确认。")
$lines.Add("- 如果该脚本失败，先检查失败迭代的 smoke JSON、设备默认浏览器、网络可达性、前台切换耗时、session 计数和 app 私有文件临时值扫描。")
$lines.ToArray() | Set-Content -LiteralPath $reportPath -Encoding UTF8

Write-Output "browser-login-smoke-watch"
Write-Output "checkedAt=$checkedAt"
Write-Output "status=$status"
Write-Output "iterations=$Iterations"
Write-Output "failureCount=$($failedRecords.Count)"
Write-Output "openWebP95Ms=$openWebP95Ms"
Write-Output "foregroundP95Ms=$foregroundP95Ms"
Write-Output "handlerPackages=$($handlerPackages -join ',')"
Write-Output "providerPageBlockingErrorRunCount=$($providerPageBlockingErrorRuns.Count)"
Write-Output "json=$statusPath"
Write-Output "report=$reportPath"

if ($status -eq "passed") {
    exit 0
}

exit 1
