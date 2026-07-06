param(
    [switch]$KeepTemp
)

$ErrorActionPreference = "Stop"

$runnerScript = Join-Path $PSScriptRoot "browser-login-continuation-runner.ps1"
$evidenceReportScript = Join-Path $PSScriptRoot "browser-login-evidence-report.ps1"
$accountWatchScript = Join-Path $PSScriptRoot "browser-login-account-watch.ps1"
$smokeWatchScript = Join-Path $PSScriptRoot "browser-login-smoke-watch.ps1"
$manualReadinessScript = Join-Path $PSScriptRoot "browser-login-manual-readiness.ps1"
$manualAccountStartScript = Join-Path $PSScriptRoot "browser-login-manual-account-start.ps1"
$longRunCycleScript = Join-Path $PSScriptRoot "browser-login-long-run-cycle.ps1"
$longRunCycleRegisterScript = Join-Path $PSScriptRoot "register-browser-login-long-run-cycle.ps1"
$providerPreflightScript = Join-Path $PSScriptRoot "browser-login-provider-preflight.ps1"

if (-not (Test-Path -LiteralPath $runnerScript)) {
    throw "Runner script not found: $runnerScript"
}
if (-not (Test-Path -LiteralPath $evidenceReportScript)) {
    throw "Evidence report script not found: $evidenceReportScript"
}
if (-not (Test-Path -LiteralPath $accountWatchScript)) {
    throw "Account watch script not found: $accountWatchScript"
}
if (-not (Test-Path -LiteralPath $smokeWatchScript)) {
    throw "Smoke watch script not found: $smokeWatchScript"
}
if (-not (Test-Path -LiteralPath $manualReadinessScript)) {
    throw "Manual readiness script not found: $manualReadinessScript"
}
if (-not (Test-Path -LiteralPath $manualAccountStartScript)) {
    throw "Manual account start script not found: $manualAccountStartScript"
}
if (-not (Test-Path -LiteralPath $longRunCycleScript)) {
    throw "Long-run cycle script not found: $longRunCycleScript"
}
if (-not (Test-Path -LiteralPath $longRunCycleRegisterScript)) {
    throw "Long-run cycle register script not found: $longRunCycleRegisterScript"
}
if (-not (Test-Path -LiteralPath $providerPreflightScript)) {
    throw "Provider preflight script not found: $providerPreflightScript"
}

$failures = New-Object System.Collections.Generic.List[string]

function Add-Failure {
    param([string]$Message)

    $script:failures.Add($Message)
    Write-Output "FAIL $Message"
}

function Assert-Equal {
    param(
        [object]$Actual,
        [object]$Expected,
        [string]$Message
    )

    if ($Actual -ne $Expected) {
        Add-Failure "$Message actual=[$Actual] expected=[$Expected]"
    } else {
        Write-Output "PASS $Message"
    }
}

function Assert-True {
    param(
        [bool]$Condition,
        [string]$Message
    )

    if (-not $Condition) {
        Add-Failure $Message
    } else {
        Write-Output "PASS $Message"
    }
}

function Read-Json {
    param([string]$Path)

    return Get-Content -Raw -LiteralPath $Path | ConvertFrom-Json
}

function Write-MockGate {
    param(
        [string]$Path,
        [int]$ExitCode,
        [string[]]$ReadyTargets,
        [string[]]$WaitingTargets,
        [string[]]$ErrorTargets,
        [string]$NextAction
    )

    $readyLiteral = "@(" + (($ReadyTargets | ForEach-Object { "'$_'" }) -join ",") + ")"
    $waitingLiteral = "@(" + (($WaitingTargets | ForEach-Object { "'$_'" }) -join ",") + ")"
    $errorLiteral = "@(" + (($ErrorTargets | ForEach-Object { "'$_'" }) -join ",") + ")"

    @"
param([string]`$Serial, [switch]`$WriteState, [string]`$StateDir)
New-Item -ItemType Directory -Force -Path `$StateDir | Out-Null
@{
  checkedAt = 'test'
  serial = `$Serial
  readyTargets = $readyLiteral
  waitingTargets = $waitingLiteral
  errorTargets = $errorLiteral
  nextAction = '$NextAction'
} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path `$StateDir 'last-status.json') -Encoding UTF8
Write-Output 'mockGate=$NextAction'
exit $ExitCode
"@ | Set-Content -LiteralPath $Path -Encoding UTF8
}

function Write-MockPostAuth {
    param(
        [string]$Path,
        [int]$ExitCode,
        [string[]]$VerifiedTargets,
        [string[]]$FailedTargets,
        [string]$NextAction
    )

    $verifiedLiteral = "@(" + (($VerifiedTargets | ForEach-Object { "'$_'" }) -join ",") + ")"
    $failedLiteral = "@(" + (($FailedTargets | ForEach-Object { "'$_'" }) -join ",") + ")"

    @"
param([string]`$Serial, [switch]`$UseExistingGateState, [switch]`$WriteState, [string]`$StateDir, [switch]`$PlanOnly)
New-Item -ItemType Directory -Force -Path `$StateDir | Out-Null
@{
  checkedAt = 'test'
  serial = `$Serial
  readyTargets = @('codex')
  selectedTargets = @('codex')
  verifiedTargets = $verifiedLiteral
  failedTargets = $failedLiteral
  nextAction = '$NextAction'
  useExistingGateState = [bool]`$UseExistingGateState
  writeState = [bool]`$WriteState
  planOnly = [bool]`$PlanOnly
} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path `$StateDir 'post-auth-status.json') -Encoding UTF8
@'
---codex-post-auth-version---
codex-cli 0.142.4
version_exit=0
---codex-post-auth-login-status---
Logged in as user@example.com
status_exit=0
---codex-post-auth-doctor-json---
{"api_key":"sk-SECRETSECRETSECRET","auth":"ok"}
doctor_exit=0
'@ | Set-Content -LiteralPath (Join-Path `$StateDir 'post-auth-raw.txt') -Encoding UTF8
Write-Output 'mockPostAuth=$NextAction'
exit $ExitCode
"@ | Set-Content -LiteralPath $Path -Encoding UTF8
}

function Write-MockWatchRunner {
    param(
        [string]$Path,
        [int]$VerifyAfterAttempt,
        [string[]]$WaitingTargets = @("codex")
    )

    $waitingLiteral = "@(" + (($WaitingTargets | ForEach-Object { "'$_'" }) -join ",") + ")"
    @"
param([string]`$Serial, [string]`$StateDir)
New-Item -ItemType Directory -Force -Path `$StateDir | Out-Null
`$counterPath = Join-Path `$StateDir 'mock-watch-runner-count.txt'
`$count = 0
if (Test-Path -LiteralPath `$counterPath) {
  `$count = [int](Get-Content -Raw -LiteralPath `$counterPath)
}
`$count += 1
Set-Content -LiteralPath `$counterPath -Value `$count -Encoding UTF8
if (`$count -ge $VerifyAfterAttempt) {
  `$exitCode = 0
  `$nextAction = 'record_post_auth_completion_evidence'
  `$verifiedTargets = @('codex')
  `$waitingTargets = @()
  `$postAuthAttempted = `$true
} else {
  `$exitCode = 2
  `$nextAction = 'wait_for_real_account_authorization'
  `$verifiedTargets = @()
  `$waitingTargets = $waitingLiteral
  `$postAuthAttempted = `$false
}
@{
  checkedAt = 'test'
  serial = `$Serial
  exitCode = `$exitCode
  gateExit = `$exitCode
  postAuthExit = if (`$postAuthAttempted) { 0 } else { `$null }
  postAuthAttempted = `$postAuthAttempted
  nextAction = `$nextAction
  readyTargets = @()
  waitingTargets = `$waitingTargets
  errorTargets = @()
  verifiedTargets = `$verifiedTargets
  failedTargets = @()
} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path `$StateDir 'runner-status.json') -Encoding UTF8
'mock evidence report' | Set-Content -LiteralPath (Join-Path `$StateDir 'post-auth-evidence-report.md') -Encoding UTF8
Write-Output "mockWatchRunnerAttempt=`$count"
exit `$exitCode
"@ | Set-Content -LiteralPath $Path -Encoding UTF8
}

function Write-MockManualReadiness {
    param(
        [string]$Path,
        [int]$ExitCode,
        [string]$Status,
        [switch]$RequireSmokeFirst
    )

    $requireSmokeFirstLiteral = if ($RequireSmokeFirst) { '$true' } else { '$false' }
    @"
param([string]`$Serial, [string]`$StateDir, [switch]`$RefreshState, [switch]`$RunCompletionAudit)
New-Item -ItemType Directory -Force -Path `$StateDir | Out-Null
`$actualStatus = '$Status'
`$actualExitCode = $ExitCode
if ($requireSmokeFirstLiteral -and -not (Test-Path -LiteralPath (Join-Path `$StateDir 'mock-smoke-count.txt'))) {
  `$actualStatus = 'not_ready'
  `$actualExitCode = 1
}
`$failedItemIds = @()
if (`$actualStatus -eq 'not_ready') {
  `$failedItemIds = @('t2-smoke')
}
@{
  checkedAt = 'test'
  serial = `$Serial
  status = `$actualStatus
  failedItemIds = `$failedItemIds
  waitingTargets = @('codex')
  verifiedTargets = @()
  nextAction = 'mock_next_action'
  refreshState = [bool]`$RefreshState
  runCompletionAudit = [bool]`$RunCompletionAudit
} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path `$StateDir 'manual-account-readiness.json') -Encoding UTF8
'mock readiness report' | Set-Content -LiteralPath (Join-Path `$StateDir 'manual-account-readiness.md') -Encoding UTF8
Write-Output "mockManualReadiness=`$actualStatus"
exit `$actualExitCode
"@ | Set-Content -LiteralPath $Path -Encoding UTF8
}

function Write-MockSmoke {
    param(
        [string]$Path
    )

    @"
param([string]`$Serial, [string]`$StateDir, [switch]`$LeaveBrowserOpen)
New-Item -ItemType Directory -Force -Path `$StateDir | Out-Null
`$counterPath = Join-Path `$StateDir 'mock-smoke-count.txt'
`$count = 0
if (Test-Path -LiteralPath `$counterPath) {
  `$count = [int](Get-Content -Raw -LiteralPath `$counterPath)
}
`$count += 1
Set-Content -LiteralPath `$counterPath -Value `$count -Encoding UTF8
@{
  schemaVersion = 10
  checkedAt = 'test'
  status = 'passed'
  serial = `$Serial
  failedItemIds = @()
  openWebElapsedMs = 20 + `$count
  appRedirectOpenWebElapsedMs = 30 + `$count
  localWebOpenWebElapsedMs = 40 + `$count
  foregroundHandoffElapsedMs = 300 + `$count
  providerOAuthForegroundMaxElapsedMs = 500 + `$count
  providerPageSignalState = 'external_page_no_blocking_error'
  providerPageBlockingErrorCount = 0
  providerPageBlockingErrorMatches = @()
  providerPageChallengeHintCount = 0
  providerPageChallengeHintMatches = @()
  providerOAuthNewSessionCount = 0
  appPrivateRawTemporaryValueHitCount = 0
  httpsBrowserResolvePackage = 'com.heytap.browser'
  httpsBrowserResolveActivity = 'com.android.browser.RealBrowserActivity'
  foregroundPackage = 'com.heytap.browser'
  foregroundActivity = 'com.android.browser.BrowserActivity'
  customTabsServiceCount = 0
  items = @()
} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path `$StateDir 'browser-login-smoke.json') -Encoding UTF8
Write-Output "mockSmoke=`$count"
exit 0
"@ | Set-Content -LiteralPath $Path -Encoding UTF8
}

function Write-MockSmokeWatch {
    param(
        [string]$Path,
        [int]$ExitCode = 0,
        [string]$Status = "passed"
    )

    @"
param(
  [string]`$Serial,
  [string]`$StateDir,
  [int]`$Iterations,
  [int]`$IntervalSeconds,
  [int]`$OpenWebP95ThresholdMs,
  [int]`$ForegroundP95ThresholdMs
)
New-Item -ItemType Directory -Force -Path `$StateDir | Out-Null
@{
  checkedAt = 'test'
  status = '$Status'
  serial = `$Serial
  iterations = `$Iterations
  intervalSeconds = `$IntervalSeconds
  failureCount = 0
  openWebP95Ms = 42
  openWebP95ThresholdMs = `$OpenWebP95ThresholdMs
  foregroundP95Ms = 777
  foregroundP95ThresholdMs = `$ForegroundP95ThresholdMs
  handlerPackages = @('com.heytap.browser')
  handlerStable = `$true
  providerSessionLeakRunCount = 0
  providerPageBlockingErrorRunCount = 0
  secretLeakRunCount = 0
  nextAction = 'ready_for_manual_account_validation_or_continue_long_run'
} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path `$StateDir 'browser-login-smoke-watch.json') -Encoding UTF8
'mock smoke watch' | Set-Content -LiteralPath (Join-Path `$StateDir 'browser-login-smoke-watch.md') -Encoding UTF8
Write-Output 'mockSmokeWatch=$Status'
exit $ExitCode
"@ | Set-Content -LiteralPath $Path -Encoding UTF8
}

function Write-MockAdbStart {
    param(
        [string]$Path
    )

    @"
param([Parameter(ValueFromRemainingArguments=`$true)][string[]]`$Args)
`$stateDir = `$env:KITE_BROWSER_LOGIN_TEST_STATE_DIR
if ([string]::IsNullOrWhiteSpace(`$stateDir)) {
  throw 'KITE_BROWSER_LOGIN_TEST_STATE_DIR is required'
}
New-Item -ItemType Directory -Force -Path `$stateDir | Out-Null
Add-Content -LiteralPath (Join-Path `$stateDir 'mock-adb-args.txt') -Value (`$Args -join ' ') -Encoding UTF8
Write-Output 'Starting: Intent { mock }'
exit 0
"@ | Set-Content -LiteralPath $Path -Encoding UTF8
}

function Write-MockAccountWatch {
    param(
        [string]$Path,
        [int]$ExitCode,
        [string]$Status
    )

    @"
param([string]`$Serial, [string[]]`$Targets, [string]`$StateDir, [int]`$TimeoutMinutes, [int]`$PollSeconds, [int]`$MaxAttempts, [switch]`$RunCompletionAuditOnVerified)
New-Item -ItemType Directory -Force -Path `$StateDir | Out-Null
@{
  checkedAt = 'test'
  serial = `$Serial
  status = '$Status'
  exitCode = $ExitCode
  maxAttempts = `$MaxAttempts
  targets = `$Targets
  waitingTargets = `$Targets
  verifiedTargets = @()
  runCompletionAuditOnVerified = [bool]`$RunCompletionAuditOnVerified
} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path `$StateDir 'account-watch-status.json') -Encoding UTF8
'mock account watch report' | Set-Content -LiteralPath (Join-Path `$StateDir 'account-watch-report.md') -Encoding UTF8
Write-Output 'mockAccountWatch=$Status'
exit $ExitCode
"@ | Set-Content -LiteralPath $Path -Encoding UTF8
}

function Write-MockCompletionAudit {
    param(
        [string]$Path,
        [int]$ExitCode,
        [string]$Status
    )

    @"
param([string]`$StateDir, [switch]`$RefreshState)
New-Item -ItemType Directory -Force -Path `$StateDir | Out-Null
@{
  checkedAt = 'test'
  status = '$Status'
  refreshState = [bool]`$RefreshState
  failedItemIds = if ('$Status' -eq 'complete') { @() } else { @('claude-account') }
} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path `$StateDir 'completion-audit.json') -Encoding UTF8
'mock completion audit' | Set-Content -LiteralPath (Join-Path `$StateDir 'completion-audit.md') -Encoding UTF8
Write-Output 'mockCompletionAudit=$Status'
exit $ExitCode
"@ | Set-Content -LiteralPath $Path -Encoding UTF8
}

function Write-MockProviderPreflightState {
    param(
        [string]$StateDir,
        [switch]$ForegroundInKite
    )

    New-Item -ItemType Directory -Force -Path $StateDir | Out-Null
    $freshNow = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ssK")
    $foregroundPackage = if ($ForegroundInKite) { "com.kite.app" } else { "com.heytap.browser" }
    $foregroundExternalPassed = -not [bool]$ForegroundInKite

    $smokeItems = @(
        @{ id = "external-browser-handler-resolved"; passed = $true },
        @{ id = "foreground-external-browser"; passed = $foregroundExternalPassed },
        @{ id = "ui-no-disallowed-useragent"; passed = $true },
        @{ id = "provider-page-no-blocking-error"; passed = $true },
        @{ id = "provider-oauth-no-auth-session"; passed = $true },
        @{ id = "no-third-party-appredirect-session"; passed = $true },
        @{ id = "appredirect-pending-session"; passed = $true },
        @{ id = "appredirect-callback-delivered"; passed = $true },
        @{ id = "appredirect-callback-redacted"; passed = $true },
        @{ id = "no-oauth-temporary-values-in-app-files"; passed = $true }
    )

    @{
        schemaVersion = 10
        checkedAt = $freshNow
        status = "passed"
        serial = "3f8bbaad"
        failedItemIds = @()
        authHostNetworkResults = @(
            @{ host = "accounts.google.com"; exitCode = 0; httpCode = 200; ok = $true; attemptCount = 1 },
            @{ host = "auth.openai.com"; exitCode = 0; httpCode = 403; ok = $true; attemptCount = 1 },
            @{ host = "claude.ai"; exitCode = 0; httpCode = 403; ok = $true; attemptCount = 1 }
        )
        httpsBrowserResolvePackage = "com.heytap.browser"
        httpsBrowserResolveActivity = "com.android.browser.RealBrowserActivity"
        customTabsServiceCount = 0
        foregroundPackage = $foregroundPackage
        foregroundActivity = "com.android.browser.BrowserActivity"
        providerOAuthResults = @(
            @{ id = "openai"; accepted = $true; externalBrowser = $true; matchesHandler = $true; passed = $true; handoffForegroundElapsedMs = 800 },
            @{ id = "claude"; accepted = $true; externalBrowser = $true; matchesHandler = $true; passed = $true; handoffForegroundElapsedMs = 900 }
        )
        providerOAuthNewSessionCount = 0
        providerOAuthForegroundMaxElapsedMs = 900
        foregroundResponsiveThresholdMs = 5000
        openWebElapsedMs = 20
        appRedirectOpenWebElapsedMs = 30
        localWebOpenWebElapsedMs = 40
        openWebResponsiveThresholdMs = 1500
        appRedirectStatus = "Delivered"
        appRedirectSessionId = "mock-session"
        appRedirectReturnedUrl = "kite-auth://callback?code=present&state=present"
        appRedirectRawSecretHitCount = 0
        appPrivateRawTemporaryValueHitCount = 0
        providerPageSignalState = "external_page_no_blocking_error"
        providerPageBlockingErrorCount = 0
        providerPageBlockingErrorMatches = @()
        providerPageChallengeHintCount = 1
        providerPageChallengeHintMatches = @("Sign in")
        items = $smokeItems
    } | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $StateDir "browser-login-smoke.json") -Encoding UTF8

    @{
        checkedAt = $freshNow
        status = "passed"
        iterations = 3
        failureCount = 0
        handlerStable = $true
        openWebP95Ms = 50
        openWebP95ThresholdMs = 1500
        foregroundP95Ms = 900
        foregroundP95ThresholdMs = 5000
        providerSessionLeakRunCount = 0
        providerPageBlockingErrorRunCount = 0
        secretLeakRunCount = 0
    } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $StateDir "browser-login-smoke-watch.json") -Encoding UTF8

    @{
        checkedAt = $freshNow
        status = "ready_for_manual_account"
        failedItemIds = @()
        waitingTargets = @("codex", "claude")
        verifiedTargets = @()
    } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $StateDir "manual-account-readiness.json") -Encoding UTF8

    @{
        checkedAt = $freshNow
        status = "incomplete"
        failedItemIds = @("codex-account", "claude-account")
    } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $StateDir "completion-audit.json") -Encoding UTF8

    @{
        checkedAt = $freshNow
        exitCode = 2
        nextAction = "wait_for_real_account_authorization"
        readyTargets = @()
        waitingTargets = @("codex", "claude")
        verifiedTargets = @()
        errorTargets = @()
    } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $StateDir "runner-status.json") -Encoding UTF8

    @{
        checkedAt = $freshNow
        status = "watch_waiting_for_real_account_authorization"
        targets = @("codex", "claude")
        launchedTargets = @("codex", "claude")
    } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $StateDir "manual-account-start-status.json") -Encoding UTF8

    @{
        checkedAt = $freshNow
        status = "waiting_for_real_account_authorization"
        targets = @("codex", "claude")
        waitingTargets = @("codex", "claude")
        verifiedTargets = @()
    } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $StateDir "account-watch-status.json") -Encoding UTF8
}

$tempRoot = Join-Path $env:TEMP ("kite-browser-login-continuation-test-" + [guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Path $tempRoot | Out-Null
Write-Output "tempRoot=$tempRoot"

try {
    $waitDir = Join-Path $tempRoot "wait"
    New-Item -ItemType Directory -Path $waitDir | Out-Null
    $waitGate = Join-Path $tempRoot "mock-gate-wait.ps1"
    $postAuthShouldNotRun = Join-Path $tempRoot "mock-post-auth-should-not-run.ps1"
    Write-MockGate `
        -Path $waitGate `
        -ExitCode 2 `
        -ReadyTargets @() `
        -WaitingTargets @("codex", "claude") `
        -ErrorTargets @() `
        -NextAction "wait_for_real_account_authorization"
    Write-MockPostAuth `
        -Path $postAuthShouldNotRun `
        -ExitCode 0 `
        -VerifiedTargets @("codex") `
        -FailedTargets @() `
        -NextAction "unexpected_post_auth"

    & $runnerScript -Serial "3f8bbaad" -GateScript $waitGate -PostAuthScript $postAuthShouldNotRun -StateDir $waitDir | Out-Null
    $waitRunnerExit = $LASTEXITCODE
    $waitRunnerState = Read-Json (Join-Path $waitDir "runner-status.json")
    Assert-Equal $waitRunnerExit 2 "wait runner exits 2"
    Assert-Equal ([bool]$waitRunnerState.postAuthAttempted) $false "wait runner does not attempt post-auth"
    Assert-True (-not (Test-Path -LiteralPath (Join-Path $waitDir "post-auth-status.json"))) "wait runner does not create post-auth status"
    $autoWaitReport = Join-Path $waitDir "post-auth-evidence-report.md"
    Assert-True (Test-Path -LiteralPath $autoWaitReport) "wait runner writes evidence report"
    $autoWaitReportText = Get-Content -Raw -LiteralPath $autoWaitReport
    Assert-True ($autoWaitReportText -match "waiting_for_real_account_authorization") "wait runner evidence report keeps waiting status"

    $waitReport = Join-Path $waitDir "report.md"
    & $evidenceReportScript -StateDir $waitDir -OutputPath $waitReport | Out-Null
    $waitReportExit = $LASTEXITCODE
    $waitReportText = Get-Content -Raw -LiteralPath $waitReport
    Assert-Equal $waitReportExit 2 "wait evidence report exits 2"
    Assert-True ($waitReportText -match "waiting_for_real_account_authorization") "wait evidence report keeps waiting status"
    Assert-True ($waitReportText -notmatch "failedTargets：codex") "wait evidence report has no stale failed target"

    $readyDir = Join-Path $tempRoot "ready"
    New-Item -ItemType Directory -Path $readyDir | Out-Null
    $readyGate = Join-Path $tempRoot "mock-gate-ready.ps1"
    $verifiedPostAuth = Join-Path $tempRoot "mock-post-auth-verified.ps1"
    Write-MockGate `
        -Path $readyGate `
        -ExitCode 0 `
        -ReadyTargets @("codex") `
        -WaitingTargets @("claude") `
        -ErrorTargets @() `
        -NextAction "verify_ready_targets_and_continue_waiting_for_accounts"
    Write-MockPostAuth `
        -Path $verifiedPostAuth `
        -ExitCode 0 `
        -VerifiedTargets @("codex") `
        -FailedTargets @() `
        -NextAction "record_post_auth_completion_evidence"

    & $runnerScript -Serial "3f8bbaad" -GateScript $readyGate -PostAuthScript $verifiedPostAuth -StateDir $readyDir | Out-Null
    $readyRunnerExit = $LASTEXITCODE
    $readyRunnerState = Read-Json (Join-Path $readyDir "runner-status.json")
    Assert-Equal $readyRunnerExit 0 "ready runner exits 0"
    Assert-Equal ([bool]$readyRunnerState.postAuthAttempted) $true "ready runner attempts post-auth"
    Assert-True (@($readyRunnerState.verifiedTargets) -contains "codex") "ready runner records verified codex"
    $autoReadyReport = Join-Path $readyDir "post-auth-evidence-report.md"
    Assert-True (Test-Path -LiteralPath $autoReadyReport) "ready runner writes evidence report"
    $autoReadyReportText = Get-Content -Raw -LiteralPath $autoReadyReport
    Assert-True ($autoReadyReportText -match "post_auth_verified") "ready runner evidence report marks verified"
    Assert-True ($autoReadyReportText -match "Logged in as <account>") "ready runner evidence report redacts account"
    Assert-True ($autoReadyReportText -notmatch "sk-SECRET") "ready runner evidence report omits api key"

    $readyReport = Join-Path $readyDir "report.md"
    & $evidenceReportScript -StateDir $readyDir -OutputPath $readyReport -RequireVerified | Out-Null
    $readyReportExit = $LASTEXITCODE
    $readyReportText = Get-Content -Raw -LiteralPath $readyReport
    Assert-Equal $readyReportExit 0 "ready evidence report exits 0"
    Assert-True ($readyReportText -match "post_auth_verified") "ready evidence report marks verified"
    Assert-True ($readyReportText -match "Logged in as <account>") "ready evidence report redacts account"
    Assert-True ($readyReportText -notmatch "user@example\.com") "ready evidence report omits raw email"
    Assert-True ($readyReportText -notmatch "sk-SECRET") "ready evidence report omits api key"

    $staleDir = Join-Path $tempRoot "stale"
    New-Item -ItemType Directory -Path $staleDir | Out-Null
    @{
        checkedAt = "test"
        serial = "3f8bbaad"
        readyTargets = @()
        waitingTargets = @("codex", "claude")
        errorTargets = @()
        nextAction = "wait_for_real_account_authorization"
    } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $staleDir "last-status.json") -Encoding UTF8
    @{
        checkedAt = "test"
        serial = "3f8bbaad"
        exitCode = 2
        gateExit = 2
        postAuthExit = $null
        postAuthAttempted = $false
        nextAction = "wait_for_real_account_authorization"
        readyTargets = @()
        waitingTargets = @("codex", "claude")
        errorTargets = @()
        verifiedTargets = @()
        failedTargets = @()
    } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $staleDir "runner-status.json") -Encoding UTF8
    @{
        checkedAt = "old"
        serial = "3f8bbaad"
        readyTargets = @("codex")
        selectedTargets = @("codex")
        verifiedTargets = @()
        failedTargets = @("codex")
        nextAction = "inspect_post_auth_probe_output"
    } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $staleDir "post-auth-status.json") -Encoding UTF8

    $staleReport = Join-Path $staleDir "report.md"
    & $evidenceReportScript -StateDir $staleDir -OutputPath $staleReport | Out-Null
    $staleReportExit = $LASTEXITCODE
    $staleReportText = Get-Content -Raw -LiteralPath $staleReport
    Assert-Equal $staleReportExit 2 "stale evidence report exits 2"
    Assert-True ($staleReportText -match "waiting_for_real_account_authorization") "stale evidence report keeps current waiting status"
    Assert-True ($staleReportText -notmatch "failedTargets：codex") "stale evidence report ignores stale post-auth failed target"

    $watchDir = Join-Path $tempRoot "watch"
    New-Item -ItemType Directory -Path $watchDir | Out-Null
    $watchRunner = Join-Path $tempRoot "mock-watch-runner.ps1"
    Write-MockWatchRunner -Path $watchRunner -VerifyAfterAttempt 2

    & $accountWatchScript -Serial "3f8bbaad" -RunnerScript $watchRunner -StateDir $watchDir -Targets "codex" -PollSeconds 0 -MaxAttempts 2 | Out-Null
    $watchExit = $LASTEXITCODE
    $watchState = Read-Json (Join-Path $watchDir "account-watch-status.json")
    $watchReportPath = Join-Path $watchDir "account-watch-report.md"
    Assert-Equal $watchExit 0 "account watch exits 0 after verified target"
    Assert-Equal $watchState.status "verified" "account watch records verified status"
    Assert-Equal $watchState.attempts 2 "account watch records attempts"
    Assert-True (@($watchState.verifiedTargets) -contains "codex") "account watch records verified codex"
    Assert-True (Test-Path -LiteralPath $watchReportPath) "account watch writes report"

    $watchAuditDir = Join-Path $tempRoot "watch-audit"
    New-Item -ItemType Directory -Path $watchAuditDir | Out-Null
    $watchAuditRunner = Join-Path $tempRoot "mock-watch-runner-audit.ps1"
    $watchAuditCompletion = Join-Path $tempRoot "mock-completion-audit.ps1"
    Write-MockWatchRunner -Path $watchAuditRunner -VerifyAfterAttempt 1
    Write-MockCompletionAudit -Path $watchAuditCompletion -ExitCode 2 -Status "incomplete"

    & $accountWatchScript -Serial "3f8bbaad" -RunnerScript $watchAuditRunner -CompletionAuditScript $watchAuditCompletion -RunCompletionAuditOnVerified -StateDir $watchAuditDir -Targets "codex" -PollSeconds 0 -MaxAttempts 1 | Out-Null
    $watchAuditExit = $LASTEXITCODE
    $watchAuditState = Read-Json (Join-Path $watchAuditDir "account-watch-status.json")
    Assert-Equal $watchAuditExit 0 "account watch exits 0 when verified audit is incomplete for other targets"
    Assert-Equal $watchAuditState.status "verified" "account watch with audit records verified status"
    Assert-Equal $watchAuditState.completionAuditExit 2 "account watch records completion audit exit"
    Assert-Equal $watchAuditState.completionAuditStatus "incomplete" "account watch records completion audit status"
    Assert-Equal $watchAuditState.nextAction "continue_waiting_or_complete_remaining_accounts" "account watch records audit next action"

    $watchWaitDir = Join-Path $tempRoot "watch-wait"
    New-Item -ItemType Directory -Path $watchWaitDir | Out-Null
    $watchWaitRunner = Join-Path $tempRoot "mock-watch-runner-wait.ps1"
    Write-MockWatchRunner -Path $watchWaitRunner -VerifyAfterAttempt 99

    & $accountWatchScript -Serial "3f8bbaad" -RunnerScript $watchWaitRunner -StateDir $watchWaitDir -Targets "codex" -PollSeconds 0 -MaxAttempts 1 | Out-Null
    $watchWaitExit = $LASTEXITCODE
    $watchWaitState = Read-Json (Join-Path $watchWaitDir "account-watch-status.json")
    Assert-Equal $watchWaitExit 2 "account watch exits 2 while waiting"
    Assert-Equal $watchWaitState.status "waiting_for_real_account_authorization" "account watch records waiting status"
    Assert-True (@($watchWaitState.waitingTargets) -contains "codex") "account watch records waiting codex"
    Assert-Equal (@($watchWaitState.readyTargets | Where-Object { $null -eq $_ }).Count) 0 "account watch omits null ready targets"
    Assert-Equal (@($watchWaitState.verifiedTargets | Where-Object { $null -eq $_ }).Count) 0 "account watch omits null verified targets"

    $watchTargetFilterDir = Join-Path $tempRoot "watch-target-filter"
    New-Item -ItemType Directory -Path $watchTargetFilterDir | Out-Null
    $watchTargetFilterRunner = Join-Path $tempRoot "mock-watch-runner-target-filter.ps1"
    Write-MockWatchRunner -Path $watchTargetFilterRunner -VerifyAfterAttempt 99 -WaitingTargets @("codex", "claude")

    & $accountWatchScript -Serial "3f8bbaad" -RunnerScript $watchTargetFilterRunner -StateDir $watchTargetFilterDir -Targets "claude" -PollSeconds 0 -MaxAttempts 1 | Out-Null
    $watchTargetFilterExit = $LASTEXITCODE
    $watchTargetFilterState = Read-Json (Join-Path $watchTargetFilterDir "account-watch-status.json")
    Assert-Equal $watchTargetFilterExit 2 "account watch target filter exits 2 while selected target waits"
    Assert-True (@($watchTargetFilterState.waitingTargets) -contains "claude") "account watch target filter keeps selected waiting target"
    Assert-True (-not (@($watchTargetFilterState.waitingTargets) -contains "codex")) "account watch target filter omits unselected waiting target"

    $watchReadinessDir = Join-Path $tempRoot "watch-readiness"
    New-Item -ItemType Directory -Path $watchReadinessDir | Out-Null
    $watchReadinessRunner = Join-Path $tempRoot "mock-watch-runner-readiness.ps1"
    $watchReadinessScript = Join-Path $tempRoot "mock-manual-readiness.ps1"
    Write-MockWatchRunner -Path $watchReadinessRunner -VerifyAfterAttempt 99
    Write-MockManualReadiness -Path $watchReadinessScript -ExitCode 0 -Status "ready_for_manual_account"

    & $accountWatchScript -Serial "3f8bbaad" -RunnerScript $watchReadinessRunner -ManualReadinessScript $watchReadinessScript -RunReadinessFirst -StateDir $watchReadinessDir -Targets "codex" -PollSeconds 0 -MaxAttempts 1 | Out-Null
    $watchReadinessExit = $LASTEXITCODE
    $watchReadinessState = Read-Json (Join-Path $watchReadinessDir "account-watch-status.json")
    Assert-Equal $watchReadinessExit 2 "account watch readiness preflight permits waiting poll"
    Assert-Equal $watchReadinessState.status "waiting_for_real_account_authorization" "account watch records waiting after readiness pass"
    Assert-Equal $watchReadinessState.readinessExit 0 "account watch records readiness exit"
    Assert-Equal $watchReadinessState.readinessStatus "ready_for_manual_account" "account watch records readiness status"
    Assert-True (Test-Path -LiteralPath (Join-Path $watchReadinessDir "mock-watch-runner-count.txt")) "account watch runs runner after readiness pass"

    $watchSmokeReadinessDir = Join-Path $tempRoot "watch-smoke-readiness-order"
    New-Item -ItemType Directory -Path $watchSmokeReadinessDir | Out-Null
    $watchSmokeReadinessRunner = Join-Path $tempRoot "mock-watch-runner-smoke-readiness.ps1"
    $watchSmokeReadinessScript = Join-Path $tempRoot "mock-manual-readiness-requires-smoke.ps1"
    $watchSmokeReadinessSmoke = Join-Path $tempRoot "mock-smoke-before-readiness.ps1"
    Write-MockWatchRunner -Path $watchSmokeReadinessRunner -VerifyAfterAttempt 99
    Write-MockManualReadiness -Path $watchSmokeReadinessScript -ExitCode 0 -Status "ready_for_manual_account" -RequireSmokeFirst
    Write-MockSmoke -Path $watchSmokeReadinessSmoke

    & $accountWatchScript -Serial "3f8bbaad" -RunnerScript $watchSmokeReadinessRunner -ManualReadinessScript $watchSmokeReadinessScript -SmokeTestScript $watchSmokeReadinessSmoke -RunSmokeFirst -RunReadinessFirst -StateDir $watchSmokeReadinessDir -Targets "codex" -PollSeconds 0 -MaxAttempts 1 | Out-Null
    $watchSmokeReadinessExit = $LASTEXITCODE
    $watchSmokeReadinessState = Read-Json (Join-Path $watchSmokeReadinessDir "account-watch-status.json")
    Assert-Equal $watchSmokeReadinessExit 2 "account watch smoke-first readiness order permits waiting poll"
    Assert-Equal $watchSmokeReadinessState.status "waiting_for_real_account_authorization" "account watch records waiting after smoke-first readiness"
    Assert-Equal $watchSmokeReadinessState.smokeExit 0 "account watch records smoke exit before readiness"
    Assert-Equal $watchSmokeReadinessState.readinessExit 0 "account watch records readiness exit after smoke refresh"
    Assert-Equal $watchSmokeReadinessState.readinessStatus "ready_for_manual_account" "account watch readiness sees refreshed smoke evidence"
    Assert-True (Test-Path -LiteralPath (Join-Path $watchSmokeReadinessDir "mock-smoke-count.txt")) "account watch runs smoke before readiness when both switches are set"

    $watchReadinessFailDir = Join-Path $tempRoot "watch-readiness-fail"
    New-Item -ItemType Directory -Path $watchReadinessFailDir | Out-Null
    $watchReadinessFailRunner = Join-Path $tempRoot "mock-watch-runner-readiness-fail.ps1"
    $watchReadinessFailScript = Join-Path $tempRoot "mock-manual-readiness-fail.ps1"
    Write-MockWatchRunner -Path $watchReadinessFailRunner -VerifyAfterAttempt 1
    Write-MockManualReadiness -Path $watchReadinessFailScript -ExitCode 1 -Status "not_ready"

    & $accountWatchScript -Serial "3f8bbaad" -RunnerScript $watchReadinessFailRunner -ManualReadinessScript $watchReadinessFailScript -RunReadinessFirst -StateDir $watchReadinessFailDir -Targets "codex" -PollSeconds 0 -MaxAttempts 1 | Out-Null
    $watchReadinessFailExit = $LASTEXITCODE
    $watchReadinessFailState = Read-Json (Join-Path $watchReadinessFailDir "account-watch-status.json")
    Assert-Equal $watchReadinessFailExit 1 "account watch exits 1 when readiness fails"
    Assert-Equal $watchReadinessFailState.status "manual_readiness_failed" "account watch records readiness failure"
    Assert-Equal $watchReadinessFailState.nextAction "inspect_manual_account_readiness" "account watch points to readiness inspection"
    Assert-Equal $watchReadinessFailState.readinessExit 1 "account watch records failed readiness exit"
    Assert-Equal $watchReadinessFailState.readinessStatus "not_ready" "account watch records failed readiness status"
    Assert-True (-not (Test-Path -LiteralPath (Join-Path $watchReadinessFailDir "mock-watch-runner-count.txt"))) "account watch skips runner when readiness fails"

    $manualStartPlanDir = Join-Path $tempRoot "manual-start-plan"
    New-Item -ItemType Directory -Path $manualStartPlanDir | Out-Null
    & $manualAccountStartScript -Serial "3f8bbaad" -Targets "codex","claude" -StateDir $manualStartPlanDir -PlanOnly | Out-Null
    $manualStartPlanExit = $LASTEXITCODE
    $manualStartPlanState = Read-Json (Join-Path $manualStartPlanDir "manual-account-start-status.json")
    Assert-Equal $manualStartPlanExit 0 "manual account start plan exits 0"
    Assert-Equal $manualStartPlanState.status "planned" "manual account start records planned status"
    Assert-Equal @($manualStartPlanState.launchedTargets).Count 0 "manual account start plan does not launch resources"

    $manualStartDir = Join-Path $tempRoot "manual-start"
    New-Item -ItemType Directory -Path $manualStartDir | Out-Null
    $mockManualStartAdb = Join-Path $tempRoot "mock-adb-start.ps1"
    $mockManualStartSmoke = Join-Path $tempRoot "mock-manual-start-smoke.ps1"
    $mockManualStartReadiness = Join-Path $tempRoot "mock-manual-start-readiness.ps1"
    Write-MockAdbStart -Path $mockManualStartAdb
    Write-MockSmoke -Path $mockManualStartSmoke
    Write-MockManualReadiness -Path $mockManualStartReadiness -ExitCode 0 -Status "ready_for_manual_account"
    $env:KITE_BROWSER_LOGIN_TEST_STATE_DIR = $manualStartDir
    try {
        & $manualAccountStartScript -Serial "3f8bbaad" -Targets "codex","claude" -StateDir $manualStartDir -AdbPath $mockManualStartAdb -SmokeTestScript $mockManualStartSmoke -ManualReadinessScript $mockManualStartReadiness -LaunchDelaySeconds 0 | Out-Null
        $manualStartExit = $LASTEXITCODE
    } finally {
        Remove-Item Env:\KITE_BROWSER_LOGIN_TEST_STATE_DIR -ErrorAction SilentlyContinue
    }
    $manualStartState = Read-Json (Join-Path $manualStartDir "manual-account-start-status.json")
    $manualStartAdbArgs = Get-Content -LiteralPath (Join-Path $manualStartDir "mock-adb-args.txt")
    Assert-Equal $manualStartExit 0 "manual account start exits 0 after launching resources"
    Assert-Equal $manualStartState.status "launched" "manual account start records launched status"
    Assert-Equal $manualStartState.smokeExit 0 "manual account start records smoke exit"
    Assert-Equal $manualStartState.readinessExit 0 "manual account start records readiness exit"
    Assert-Equal $manualStartState.readinessStatus "ready_for_manual_account" "manual account start records readiness status"
    Assert-True (@($manualStartState.launchedTargets) -contains "codex") "manual account start launches codex"
    Assert-True (@($manualStartState.launchedTargets) -contains "claude") "manual account start launches claude"
    Assert-True (($manualStartAdbArgs -join "`n") -match "kite.codex.cli") "manual account start adb command targets Codex resource"
    Assert-True (($manualStartAdbArgs -join "`n") -match "kite.claude.code") "manual account start adb command targets Claude resource"

    $manualStartFailDir = Join-Path $tempRoot "manual-start-readiness-fail"
    New-Item -ItemType Directory -Path $manualStartFailDir | Out-Null
    $mockManualStartFailAdb = Join-Path $tempRoot "mock-adb-start-readiness-fail.ps1"
    $mockManualStartFailSmoke = Join-Path $tempRoot "mock-manual-start-smoke-failcase.ps1"
    $mockManualStartFailReadiness = Join-Path $tempRoot "mock-manual-start-readiness-fail.ps1"
    Write-MockAdbStart -Path $mockManualStartFailAdb
    Write-MockSmoke -Path $mockManualStartFailSmoke
    Write-MockManualReadiness -Path $mockManualStartFailReadiness -ExitCode 1 -Status "not_ready"
    $env:KITE_BROWSER_LOGIN_TEST_STATE_DIR = $manualStartFailDir
    try {
        & $manualAccountStartScript -Serial "3f8bbaad" -Targets "codex" -StateDir $manualStartFailDir -AdbPath $mockManualStartFailAdb -SmokeTestScript $mockManualStartFailSmoke -ManualReadinessScript $mockManualStartFailReadiness -LaunchDelaySeconds 0 | Out-Null
        $manualStartFailExit = $LASTEXITCODE
    } finally {
        Remove-Item Env:\KITE_BROWSER_LOGIN_TEST_STATE_DIR -ErrorAction SilentlyContinue
    }
    $manualStartFailState = Read-Json (Join-Path $manualStartFailDir "manual-account-start-status.json")
    Assert-Equal $manualStartFailExit 1 "manual account start exits 1 when readiness fails"
    Assert-Equal $manualStartFailState.status "manual_readiness_failed" "manual account start records readiness failure"
    Assert-True (-not (Test-Path -LiteralPath (Join-Path $manualStartFailDir "mock-adb-args.txt"))) "manual account start skips adb launch when readiness fails"

    $manualStartWatchDir = Join-Path $tempRoot "manual-start-watch"
    New-Item -ItemType Directory -Path $manualStartWatchDir | Out-Null
    $mockManualStartWatchAdb = Join-Path $tempRoot "mock-adb-start-watch.ps1"
    $mockManualStartWatchSmoke = Join-Path $tempRoot "mock-manual-start-smoke-watch.ps1"
    $mockManualStartWatchReadiness = Join-Path $tempRoot "mock-manual-start-readiness-watch.ps1"
    $mockManualStartWatch = Join-Path $tempRoot "mock-account-watch-from-start.ps1"
    Write-MockAdbStart -Path $mockManualStartWatchAdb
    Write-MockSmoke -Path $mockManualStartWatchSmoke
    Write-MockManualReadiness -Path $mockManualStartWatchReadiness -ExitCode 0 -Status "ready_for_manual_account"
    Write-MockAccountWatch -Path $mockManualStartWatch -ExitCode 2 -Status "waiting_for_real_account_authorization"
    $env:KITE_BROWSER_LOGIN_TEST_STATE_DIR = $manualStartWatchDir
    try {
        & $manualAccountStartScript -Serial "3f8bbaad" -Targets "codex" -StateDir $manualStartWatchDir -AdbPath $mockManualStartWatchAdb -SmokeTestScript $mockManualStartWatchSmoke -ManualReadinessScript $mockManualStartWatchReadiness -AccountWatchScript $mockManualStartWatch -LaunchDelaySeconds 0 -StartWatch -WatchMaxAttempts 1 | Out-Null
        $manualStartWatchExit = $LASTEXITCODE
    } finally {
        Remove-Item Env:\KITE_BROWSER_LOGIN_TEST_STATE_DIR -ErrorAction SilentlyContinue
    }
    $manualStartWatchState = Read-Json (Join-Path $manualStartWatchDir "manual-account-start-status.json")
    $manualStartWatchAccountState = Read-Json (Join-Path $manualStartWatchDir "account-watch-status.json")
    Assert-Equal $manualStartWatchExit 2 "manual account start propagates waiting watch exit"
    Assert-Equal $manualStartWatchState.status "watch_waiting_for_real_account_authorization" "manual account start records watch waiting status"
    Assert-Equal $manualStartWatchState.watchExit 2 "manual account start records watch exit"
    Assert-Equal $manualStartWatchState.watchMaxAttempts 1 "manual account start records watch max attempts"
    Assert-Equal $manualStartWatchAccountState.maxAttempts 1 "manual account start forwards bounded watch max attempts"
    Assert-True (Test-Path -LiteralPath (Join-Path $manualStartWatchDir "account-watch-status.json")) "manual account start writes account watch status when StartWatch is used"

    $smokeWatchDir = Join-Path $tempRoot "smoke-watch"
    New-Item -ItemType Directory -Path $smokeWatchDir | Out-Null
    $mockSmoke = Join-Path $tempRoot "mock-smoke.ps1"
    Write-MockSmoke -Path $mockSmoke

    & $smokeWatchScript -Serial "3f8bbaad" -SmokeTestScript $mockSmoke -StateDir $smokeWatchDir -Iterations 2 -IntervalSeconds 0 | Out-Null
    $smokeWatchExit = $LASTEXITCODE
    $smokeWatchState = Read-Json (Join-Path $smokeWatchDir "browser-login-smoke-watch.json")
    $smokeWatchReport = Join-Path $smokeWatchDir "browser-login-smoke-watch.md"
    Assert-Equal $smokeWatchExit 0 "smoke watch exits 0 for stable smoke"
    Assert-Equal $smokeWatchState.status "passed" "smoke watch records passed status"
    Assert-Equal $smokeWatchState.iterations 2 "smoke watch records iteration count"
    Assert-Equal $smokeWatchState.failureCount 0 "smoke watch records zero failures"
    Assert-True ($smokeWatchState.handlerStable -eq $true) "smoke watch records stable handler"
    Assert-True (Test-Path -LiteralPath $smokeWatchReport) "smoke watch writes report"
    Assert-True (Test-Path -LiteralPath (Join-Path $smokeWatchDir "browser-login-smoke-watch-progress.json")) "smoke watch writes progress"
    $smokeWatchProgress = Read-Json (Join-Path $smokeWatchDir "browser-login-smoke-watch-progress.json")
    Assert-Equal $smokeWatchProgress.status "passed" "smoke watch progress records final status"
    Assert-Equal $smokeWatchProgress.completedIterations 2 "smoke watch progress records completed iterations"
    Assert-Equal $smokeWatchProgress.remainingIterations 0 "smoke watch progress records no remaining iterations"

    $longRunDir = Join-Path $tempRoot "long-run-cycle"
    New-Item -ItemType Directory -Path $longRunDir | Out-Null
    $mockLongRunRunner = Join-Path $tempRoot "mock-long-run-runner.ps1"
    $mockLongRunSmokeWatch = Join-Path $tempRoot "mock-long-run-smoke-watch.ps1"
    $mockLongRunReadiness = Join-Path $tempRoot "mock-long-run-readiness.ps1"
    $mockLongRunAudit = Join-Path $tempRoot "mock-long-run-audit.ps1"
    Write-MockWatchRunner -Path $mockLongRunRunner -VerifyAfterAttempt 99 -WaitingTargets @("codex", "claude")
    Write-MockSmokeWatch -Path $mockLongRunSmokeWatch -ExitCode 0 -Status "passed"
    Write-MockManualReadiness -Path $mockLongRunReadiness -ExitCode 0 -Status "ready_for_manual_account"
    Write-MockCompletionAudit -Path $mockLongRunAudit -ExitCode 2 -Status "incomplete"

    & $longRunCycleScript -Serial "3f8bbaad" -StateDir $longRunDir -RunnerScript $mockLongRunRunner -SmokeWatchScript $mockLongRunSmokeWatch -ManualReadinessScript $mockLongRunReadiness -CompletionAuditScript $mockLongRunAudit -SmokeIterations 2 -SmokeIntervalSeconds 0 | Out-Null
    $longRunExit = $LASTEXITCODE
    $longRunState = Read-Json (Join-Path $longRunDir "browser-login-long-run-cycle.json")
    $longRunReport = Join-Path $longRunDir "browser-login-long-run-cycle.md"
    Assert-Equal $longRunExit 2 "long-run cycle exits 2 while accounts wait"
    Assert-Equal $longRunState.status "waiting_account_browser_stable" "long-run cycle records browser-stable waiting status"
    Assert-Equal $longRunState.runnerExit 2 "long-run cycle records runner waiting exit"
    Assert-Equal $longRunState.smokeWatchExit 0 "long-run cycle records smoke watch success"
    Assert-Equal $longRunState.manualReadinessExit 0 "long-run cycle records readiness success"
    Assert-True (@($longRunState.waitingTargets) -contains "claude") "long-run cycle preserves waiting targets"
    Assert-True (Test-Path -LiteralPath $longRunReport) "long-run cycle writes report"
    Assert-True (Test-Path -LiteralPath (Join-Path $longRunDir "browser-login-long-run-cycle-progress.json")) "long-run cycle writes progress"
    $longRunProgress = Read-Json (Join-Path $longRunDir "browser-login-long-run-cycle-progress.json")
    Assert-Equal $longRunProgress.phase "finished" "long-run cycle progress records finished phase"
    Assert-Equal $longRunProgress.status "waiting_account_browser_stable" "long-run cycle progress records final status"
    Assert-Equal $longRunProgress.smokeWatchExit 0 "long-run cycle progress records smoke watch exit"

    $readinessDir = Join-Path $tempRoot "manual-readiness"
    New-Item -ItemType Directory -Path $readinessDir | Out-Null
    $freshNow = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ssK")
    @{
        schemaVersion = 10
        checkedAt = $freshNow
        status = "passed"
        failedItemIds = @()
    } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $readinessDir "browser-login-smoke.json") -Encoding UTF8
    @{
        checkedAt = $freshNow
        status = "passed"
        iterations = 3
        failureCount = 0
        openWebP95Ms = 42
        foregroundP95Ms = 1200
        providerPageBlockingErrorRunCount = 0
    } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $readinessDir "browser-login-smoke-watch.json") -Encoding UTF8
    @{
        checkedAt = $freshNow
        serial = "3f8bbaad"
        exitCode = 2
        readyTargets = @()
        waitingTargets = @("codex", "claude")
        verifiedTargets = @()
        errorTargets = @()
        nextAction = "wait_for_real_account_authorization"
    } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $readinessDir "runner-status.json") -Encoding UTF8
    @{
        checkedAt = $freshNow
        status = "incomplete"
        failedItemIds = @("codex-account", "claude-account")
    } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $readinessDir "completion-audit.json") -Encoding UTF8
    @{
        checkedAt = $freshNow
        serial = "3f8bbaad"
        status = "watch_waiting_for_real_account_authorization"
        exitCode = 2
        targets = @("claude")
        launchedTargets = @("claude")
        watchExit = 2
        watchMaxAttempts = 1
    } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $readinessDir "manual-account-start-status.json") -Encoding UTF8

    & $manualReadinessScript -StateDir $readinessDir | Out-Null
    $readinessExit = $LASTEXITCODE
    $readinessState = Read-Json (Join-Path $readinessDir "manual-account-readiness.json")
    $readinessReport = Join-Path $readinessDir "manual-account-readiness.md"
    $manualStartReadinessItem = @($readinessState.items | Where-Object { $_.id -eq "t4-manual-account-start-known" })[0]
    Assert-Equal $readinessExit 0 "manual readiness exits 0 when only account gaps remain"
    Assert-Equal $readinessState.status "ready_for_manual_account" "manual readiness records ready status"
    Assert-True (@($readinessState.waitingTargets) -contains "codex") "manual readiness records waiting codex"
    Assert-Equal $readinessState.manualStartStatus "watch_waiting_for_real_account_authorization" "manual readiness records manual account start status"
    Assert-True (@($readinessState.manualStartLaunchedTargets) -contains "claude") "manual readiness records manual account start launched target"
    Assert-True ($manualStartReadinessItem.passed -eq $true) "manual readiness accepts waiting manual account start status"
    Assert-True ($readinessState.nextAction -match "browser-login-manual-account-start") "manual readiness points to manual account start"
    Assert-True ($readinessState.nextAction -match "StartWatch") "manual readiness keeps account start connected to watch"
    Assert-True (Test-Path -LiteralPath $readinessReport) "manual readiness writes report"

    $readinessBadDir = Join-Path $tempRoot "manual-readiness-bad"
    Copy-Item -LiteralPath $readinessDir -Destination $readinessBadDir -Recurse
    @{
        schemaVersion = 10
        checkedAt = $freshNow
        status = "failed"
        failedItemIds = @("external-foreground-responsive")
    } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $readinessBadDir "browser-login-smoke.json") -Encoding UTF8

    & $manualReadinessScript -StateDir $readinessBadDir | Out-Null
    $readinessBadExit = $LASTEXITCODE
    $readinessBadState = Read-Json (Join-Path $readinessBadDir "manual-account-readiness.json")
    Assert-Equal $readinessBadExit 1 "manual readiness exits 1 when smoke fails"
    Assert-Equal $readinessBadState.status "not_ready" "manual readiness records not ready status"
    Assert-True (@($readinessBadState.failedItemIds) -contains "t2-smoke") "manual readiness records smoke failure"

    $readinessStaleAuditDir = Join-Path $tempRoot "manual-readiness-stale-audit"
    Copy-Item -LiteralPath $readinessDir -Destination $readinessStaleAuditDir -Recurse
    $staleAuditTime = (Get-Date).AddDays(-2).ToString("yyyy-MM-ddTHH:mm:ssK")
    @{
        checkedAt = $staleAuditTime
        status = "incomplete"
        failedItemIds = @("codex-account", "claude-account")
    } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $readinessStaleAuditDir "completion-audit.json") -Encoding UTF8

    & $manualReadinessScript -StateDir $readinessStaleAuditDir | Out-Null
    $readinessStaleAuditExit = $LASTEXITCODE
    $readinessStaleAuditState = Read-Json (Join-Path $readinessStaleAuditDir "manual-account-readiness.json")
    Assert-Equal $readinessStaleAuditExit 1 "manual readiness exits 1 when completion audit is stale"
    Assert-Equal $readinessStaleAuditState.status "not_ready" "manual readiness records not ready for stale audit"
    Assert-True (@($readinessStaleAuditState.failedItemIds) -contains "t5-completion-audit-shape") "manual readiness records stale audit failure"

    $readinessLaunchFailDir = Join-Path $tempRoot "manual-readiness-launch-fail"
    Copy-Item -LiteralPath $readinessDir -Destination $readinessLaunchFailDir -Recurse
    @{
        checkedAt = $freshNow
        serial = "3f8bbaad"
        status = "launch_failed"
        exitCode = 1
        targets = @("codex")
        launchedTargets = @()
        watchExit = $null
        watchMaxAttempts = 0
    } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $readinessLaunchFailDir "manual-account-start-status.json") -Encoding UTF8

    & $manualReadinessScript -StateDir $readinessLaunchFailDir | Out-Null
    $readinessLaunchFailExit = $LASTEXITCODE
    $readinessLaunchFailState = Read-Json (Join-Path $readinessLaunchFailDir "manual-account-readiness.json")
    Assert-Equal $readinessLaunchFailExit 1 "manual readiness exits 1 when manual account start launch failed"
    Assert-Equal $readinessLaunchFailState.status "not_ready" "manual readiness records not ready for manual start launch failure"
    Assert-True (@($readinessLaunchFailState.failedItemIds) -contains "t4-manual-account-start-known") "manual readiness records manual start launch failure"

    $providerPreflightDir = Join-Path $tempRoot "provider-preflight-ready"
    Write-MockProviderPreflightState -StateDir $providerPreflightDir
    & $providerPreflightScript -StateDir $providerPreflightDir | Out-Null
    $providerPreflightExit = $LASTEXITCODE
    $providerPreflightState = Read-Json (Join-Path $providerPreflightDir "provider-auth-preflight.json")
    $providerPreflightReport = Join-Path $providerPreflightDir "provider-auth-preflight.md"
    Assert-Equal $providerPreflightExit 2 "provider preflight exits 2 when only accounts wait"
    Assert-Equal $providerPreflightState.status "ready_for_manual_provider_auth" "provider preflight records manual provider auth readiness"
    Assert-Equal @($providerPreflightState.blockingFailureIds).Count 0 "provider preflight has no blocking failures when only accounts wait"
    Assert-True (@($providerPreflightState.waitingTargets) -contains "codex") "provider preflight preserves waiting codex"
    Assert-True (Test-Path -LiteralPath $providerPreflightReport) "provider preflight writes report"

    $providerPreflightBadDir = Join-Path $tempRoot "provider-preflight-bad-browser"
    Write-MockProviderPreflightState -StateDir $providerPreflightBadDir -ForegroundInKite
    & $providerPreflightScript -StateDir $providerPreflightBadDir | Out-Null
    $providerPreflightBadExit = $LASTEXITCODE
    $providerPreflightBadState = Read-Json (Join-Path $providerPreflightBadDir "provider-auth-preflight.json")
    Assert-Equal $providerPreflightBadExit 1 "provider preflight exits 1 when OAuth remains in Kite"
    Assert-Equal $providerPreflightBadState.status "not_ready" "provider preflight records not ready for embedded browser risk"
    Assert-True (@($providerPreflightBadState.blockingFailureIds) -contains "external-user-agent") "provider preflight records external user-agent failure"
    Assert-True (@($providerPreflightBadState.failedBuckets) -contains "browser_environment") "provider preflight classifies embedded browser risk"

    if ($failures.Count -gt 0) {
        Write-Output "browser-login-continuation-self-test failed=$($failures.Count)"
        exit 1
    }

    Write-Output "browser-login-continuation-self-test passed"
    exit 0
} finally {
    if ($KeepTemp) {
        Write-Output "keptTemp=$tempRoot"
    } elseif (Test-Path -LiteralPath $tempRoot) {
        Remove-Item -LiteralPath $tempRoot -Recurse -Force
    }
}
