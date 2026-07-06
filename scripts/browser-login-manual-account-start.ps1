param(
    [string]$Serial = "3f8bbaad",
    [string[]]$Targets = @("codex", "claude"),
    [string]$StateDir = "",
    [string]$AdbPath = "adb",
    [string]$SmokeTestScript = "",
    [string]$ManualReadinessScript = "",
    [string]$AccountWatchScript = "",
    [int]$LaunchDelaySeconds = 2,
    [int]$WatchTimeoutMinutes = 60,
    [int]$WatchPollSeconds = 30,
    [int]$WatchMaxAttempts = 0,
    [switch]$SkipSmoke,
    [switch]$SkipReadiness,
    [switch]$StartWatch,
    [switch]$RunCompletionAuditOnVerified,
    [switch]$PlanOnly
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($StateDir)) {
    $StateDir = Join-Path $env:LOCALAPPDATA "Kite\browser-login-continuation"
}
if ([string]::IsNullOrWhiteSpace($SmokeTestScript)) {
    $SmokeTestScript = Join-Path $PSScriptRoot "browser-login-smoke-test.ps1"
}
if ([string]::IsNullOrWhiteSpace($ManualReadinessScript)) {
    $ManualReadinessScript = Join-Path $PSScriptRoot "browser-login-manual-readiness.ps1"
}
if ([string]::IsNullOrWhiteSpace($AccountWatchScript)) {
    $AccountWatchScript = Join-Path $PSScriptRoot "browser-login-account-watch.ps1"
}

if (-not $SkipSmoke -and -not (Test-Path -LiteralPath $SmokeTestScript)) {
    throw "Smoke test script not found: $SmokeTestScript"
}
if (-not $SkipReadiness -and -not (Test-Path -LiteralPath $ManualReadinessScript)) {
    throw "Manual readiness script not found: $ManualReadinessScript"
}
if ($StartWatch -and -not (Test-Path -LiteralPath $AccountWatchScript)) {
    throw "Account watch script not found: $AccountWatchScript"
}

New-Item -ItemType Directory -Force -Path $StateDir | Out-Null

$statePath = Join-Path $StateDir "manual-account-start-status.json"
$reportPath = Join-Path $StateDir "manual-account-start-report.md"
$manualReadinessJsonPath = Join-Path $StateDir "manual-account-readiness.json"
$accountWatchJsonPath = Join-Path $StateDir "account-watch-status.json"

$resourceByTarget = @{
    codex = "kite.codex.cli"
    claude = "kite.claude.code"
}
$activity = "com.kite.app/com.kite.app.MainActivity"
$runtimeActionExtra = "runtime_action"
$resourceIdExtra = "com.kite.app.extra.RESOURCE_INSTALL_TARGET_ID"

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
        if (-not $resourceByTarget.ContainsKey($name)) {
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

function Write-StartState {
    param(
        [string]$Status,
        [string]$NextAction,
        [int]$ExitCode,
        [object]$SmokeExit,
        [object]$ReadinessExit,
        [string]$ReadinessStatus,
        [object]$WatchExit,
        [string[]]$TargetNames,
        [string[]]$LaunchedTargets,
        [object[]]$LaunchResults
    )

    $checkedAt = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ssK")
    $targetNamesClean = ConvertTo-StringArray $TargetNames
    $launchedTargetsClean = ConvertTo-StringArray $LaunchedTargets
    $state = [ordered]@{
        checkedAt = $checkedAt
        serial = $Serial
        status = $Status
        exitCode = $ExitCode
        nextAction = $NextAction
        planOnly = [bool]$PlanOnly
        startWatch = [bool]$StartWatch
        skipSmoke = [bool]$SkipSmoke
        skipReadiness = [bool]$SkipReadiness
        smokeExit = $SmokeExit
        readinessExit = $ReadinessExit
        readinessStatus = $ReadinessStatus
        watchExit = $WatchExit
        watchMaxAttempts = $WatchMaxAttempts
        targets = @($targetNamesClean)
        launchedTargets = @($launchedTargetsClean)
        launchResults = @($LaunchResults)
        manualReadinessJsonPath = $manualReadinessJsonPath
        accountWatchJsonPath = $accountWatchJsonPath
        statePath = $statePath
        reportPath = $reportPath
    }
    $state | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $statePath -Encoding UTF8

    $lines = New-Object System.Collections.Generic.List[string]
    $lines.Add("# 浏览器登录人工账号启动")
    $lines.Add("")
    $lines.Add("- 生成时间：$checkedAt")
    $lines.Add("- 设备：$Serial")
    $lines.Add("- 状态：$Status")
    $lines.Add("- 下一步：$NextAction")
    $lines.Add("- targets：$(Format-Items $targetNamesClean)")
    $lines.Add("- launchedTargets：$(Format-Items $launchedTargetsClean)")
    $lines.Add("- smokeExit：$SmokeExit")
    $lines.Add("- readinessExit：$ReadinessExit")
    $lines.Add("- readinessStatus：$ReadinessStatus")
    $lines.Add("- watchExit：$WatchExit")
    $lines.Add("- watchMaxAttempts：$WatchMaxAttempts")
    $lines.Add("- manualReadinessJson：$manualReadinessJsonPath")
    $lines.Add("- accountWatchJson：$accountWatchJsonPath")
    $lines.Add("")
    $lines.Add("## 边界")
    $lines.Add("")
    $lines.Add("- 本脚本只启动真实资源登录入口并可选接 watch，不输入账号、不读取 token、不伪造 callback。")
    $lines.Add("- 登录事实仍以 Codex/Claude 官方状态命令、post-auth 状态和 completion audit 为准。")
    $lines.Add("- 如果 provider 显示验证码、MFA、设备确认或 paste code，请由用户在手机上完成。")
    $lines.ToArray() | Set-Content -LiteralPath $reportPath -Encoding UTF8

    Write-Output "stateJson=$statePath"
    Write-Output "report=$reportPath"
}

function Start-ResourceOpen {
    param(
        [string]$TargetName,
        [string]$ResourceId
    )

    $args = @(
        "-s", $Serial,
        "shell", "am", "start",
        "-n", $activity,
        "--es", $runtimeActionExtra, "start_resource_open",
        "--es", $resourceIdExtra, $ResourceId
    )
    $output = @(& $AdbPath @args 2>&1 | ForEach-Object { $_.ToString() })
    $exit = $LASTEXITCODE
    return [ordered]@{
        target = $TargetName
        resourceId = $ResourceId
        exitCode = $exit
        ok = ($exit -eq 0)
        command = "$AdbPath $($args -join ' ')"
        output = ($output -join "`n")
    }
}

$targetNames = Normalize-Targets $Targets
$launchedTargets = @()
$launchResults = New-Object System.Collections.Generic.List[object]
$smokeExit = $null
$readinessExit = $null
$readinessStatus = ""
$watchExit = $null
$acceptedReadinessStatuses = @(
    "ready_for_manual_account",
    "partial_account_verified_continue_watch",
    "account_verified_run_completion_audit"
)

Write-Output "browser-login-manual-account-start"
Write-Output "serial=$Serial"
Write-Output "targets=$($targetNames -join ',')"
Write-Output "stateDir=$StateDir"

if ($PlanOnly) {
    Write-StartState `
        -Status "planned" `
        -NextAction "run_without_plan_only_to_start_resource_login" `
        -ExitCode 0 `
        -SmokeExit $null `
        -ReadinessExit $null `
        -ReadinessStatus "" `
        -WatchExit $null `
        -TargetNames $targetNames `
        -LaunchedTargets @() `
        -LaunchResults @()
    Write-Output "nextAction=run_without_plan_only_to_start_resource_login"
    exit 0
}

if (-not $SkipSmoke) {
    & $SmokeTestScript -Serial $Serial -StateDir $StateDir | Out-Null
    $smokeExit = $LASTEXITCODE
    Write-Output "smokeExit=$smokeExit"
    if ($smokeExit -ne 0) {
        Write-StartState `
            -Status "smoke_failed" `
            -NextAction "inspect_browser_login_smoke_output" `
            -ExitCode 1 `
            -SmokeExit $smokeExit `
            -ReadinessExit $readinessExit `
            -ReadinessStatus $readinessStatus `
            -WatchExit $watchExit `
            -TargetNames $targetNames `
            -LaunchedTargets @() `
            -LaunchResults @()
        Write-Output "nextAction=inspect_browser_login_smoke_output"
        exit 1
    }
}

if (-not $SkipReadiness) {
    & $ManualReadinessScript -Serial $Serial -StateDir $StateDir -RefreshState -RunCompletionAudit | Out-Null
    $readinessExit = $LASTEXITCODE
    $readinessState = Read-JsonOrNull $manualReadinessJsonPath
    if ($null -ne $readinessState -and $null -ne $readinessState.PSObject.Properties["status"]) {
        $readinessStatus = $readinessState.status.ToString()
    }
    Write-Output "readinessExit=$readinessExit"
    Write-Output "readinessStatus=$readinessStatus"
    if ($readinessExit -ne 0 -or $acceptedReadinessStatuses -notcontains $readinessStatus) {
        Write-StartState `
            -Status "manual_readiness_failed" `
            -NextAction "inspect_manual_account_readiness" `
            -ExitCode 1 `
            -SmokeExit $smokeExit `
            -ReadinessExit $readinessExit `
            -ReadinessStatus $readinessStatus `
            -WatchExit $watchExit `
            -TargetNames $targetNames `
            -LaunchedTargets @() `
            -LaunchResults @()
        Write-Output "nextAction=inspect_manual_account_readiness"
        exit 1
    }
}

foreach ($target in $targetNames) {
    $resourceId = $resourceByTarget[$target]
    $result = Start-ResourceOpen -TargetName $target -ResourceId $resourceId
    $launchResults.Add($result)
    Write-Output "launchTarget=$target"
    Write-Output "launchResourceId=$resourceId"
    Write-Output "launchExit=$($result.exitCode)"
    if ($result.ok) {
        $launchedTargets += $target
    }
    if (-not $result.ok) {
        Write-StartState `
            -Status "launch_failed" `
            -NextAction "inspect_adb_resource_open_output" `
            -ExitCode 1 `
            -SmokeExit $smokeExit `
            -ReadinessExit $readinessExit `
            -ReadinessStatus $readinessStatus `
            -WatchExit $watchExit `
            -TargetNames $targetNames `
            -LaunchedTargets $launchedTargets `
            -LaunchResults $launchResults.ToArray()
        Write-Output "nextAction=inspect_adb_resource_open_output"
        exit 1
    }
    if ($LaunchDelaySeconds -gt 0) {
        Start-Sleep -Seconds $LaunchDelaySeconds
    }
}

if ($StartWatch) {
    $watchParams = @{
        Serial = $Serial
        Targets = $targetNames
        StateDir = $StateDir
        TimeoutMinutes = $WatchTimeoutMinutes
        PollSeconds = $WatchPollSeconds
    }
    if ($WatchMaxAttempts -gt 0) {
        $watchParams.MaxAttempts = $WatchMaxAttempts
    }
    if ($RunCompletionAuditOnVerified) {
        $watchParams.RunCompletionAuditOnVerified = $true
    }
    & $AccountWatchScript @watchParams
    $watchExit = $LASTEXITCODE
    Write-Output "watchExit=$watchExit"
    $status = if ($watchExit -eq 0) {
        "watch_verified"
    } elseif ($watchExit -eq 2) {
        "watch_waiting_for_real_account_authorization"
    } else {
        "watch_needs_inspection"
    }
    $nextAction = if ($watchExit -eq 0) {
        "run_completion_audit_or_record_verified_accounts"
    } elseif ($watchExit -eq 2) {
        "continue_account_authorization_in_browser"
    } else {
        "inspect_account_watch_output"
    }
    $exitCode = if ($watchExit -eq 0) { 0 } elseif ($watchExit -eq 2) { 2 } else { 1 }
    Write-StartState `
        -Status $status `
        -NextAction $nextAction `
        -ExitCode $exitCode `
        -SmokeExit $smokeExit `
        -ReadinessExit $readinessExit `
        -ReadinessStatus $readinessStatus `
        -WatchExit $watchExit `
        -TargetNames $targetNames `
        -LaunchedTargets $launchedTargets `
        -LaunchResults $launchResults.ToArray()
    Write-Output "nextAction=$nextAction"
    exit $exitCode
}

Write-StartState `
    -Status "launched" `
    -NextAction "complete_account_authorization_then_run_account_watch" `
    -ExitCode 0 `
    -SmokeExit $smokeExit `
    -ReadinessExit $readinessExit `
    -ReadinessStatus $readinessStatus `
    -WatchExit $watchExit `
    -TargetNames $targetNames `
    -LaunchedTargets $launchedTargets `
    -LaunchResults $launchResults.ToArray()
Write-Output "nextAction=complete_account_authorization_then_run_account_watch"
exit 0
