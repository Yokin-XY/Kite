param(
    [string]$Serial = "3f8bbaad",
    [string]$GateScript = "",
    [string]$PostAuthScript = "",
    [string]$EvidenceReportScript = "",
    [string]$StateDir = "",
    [switch]$PostAuthPlanOnly,
    [switch]$SkipEvidenceReport
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($GateScript)) {
    $GateScript = Join-Path $PSScriptRoot "browser-login-continuation-gate.ps1"
}
if ([string]::IsNullOrWhiteSpace($PostAuthScript)) {
    $PostAuthScript = Join-Path $PSScriptRoot "browser-login-post-auth-verify.ps1"
}
if ([string]::IsNullOrWhiteSpace($EvidenceReportScript)) {
    $EvidenceReportScript = Join-Path $PSScriptRoot "browser-login-evidence-report.ps1"
}
if ([string]::IsNullOrWhiteSpace($StateDir)) {
    $StateDir = Join-Path $env:LOCALAPPDATA "Kite\browser-login-continuation"
}

if (-not (Test-Path -LiteralPath $GateScript)) {
    throw "Gate script not found: $GateScript"
}
if (-not (Test-Path -LiteralPath $PostAuthScript)) {
    throw "Post-auth script not found: $PostAuthScript"
}
if (-not $SkipEvidenceReport -and -not (Test-Path -LiteralPath $EvidenceReportScript)) {
    throw "Evidence report script not found: $EvidenceReportScript"
}

New-Item -ItemType Directory -Force -Path $StateDir | Out-Null
$runnerStatePath = Join-Path $StateDir "runner-status.json"

function Invoke-EvidenceReport {
    if ($SkipEvidenceReport) {
        return 0
    }

    & $EvidenceReportScript -StateDir $StateDir | Out-Null
    $reportExit = $LASTEXITCODE
    if ($reportExit -eq 0 -or $reportExit -eq 2) {
        return $reportExit
    }

    return $reportExit
}

function Write-RunnerState {
    param(
        [int]$ExitCode,
        [string]$NextAction,
        [object]$GateExit = $null,
        [object]$PostAuthExit = $null,
        [bool]$PostAuthAttempted = $false
    )

    $gateStatePath = Join-Path $StateDir "last-status.json"
    $postAuthStatePath = Join-Path $StateDir "post-auth-status.json"
    $gateState = $null
    $postAuthState = $null

    if (Test-Path -LiteralPath $gateStatePath) {
        $gateState = Get-Content -Raw -LiteralPath $gateStatePath | ConvertFrom-Json
    }
    if ($PostAuthAttempted -and (Test-Path -LiteralPath $postAuthStatePath)) {
        $postAuthState = Get-Content -Raw -LiteralPath $postAuthStatePath | ConvertFrom-Json
    }

    $readyTargets = @()
    $waitingTargets = @()
    $errorTargets = @()
    $verifiedTargets = @()
    $failedTargets = @()

    if ($null -ne $gateState) {
        $readyTargets = @($gateState.readyTargets)
        $waitingTargets = @($gateState.waitingTargets)
        $errorTargets = @($gateState.errorTargets)
    }
    if ($null -ne $postAuthState) {
        $verifiedTargets = @($postAuthState.verifiedTargets)
        $failedTargets = @($postAuthState.failedTargets)
    }

    $state = [ordered]@{
        checkedAt = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ssK")
        serial = $Serial
        exitCode = $ExitCode
        gateExit = $GateExit
        postAuthExit = $PostAuthExit
        postAuthAttempted = $PostAuthAttempted
        nextAction = $NextAction
        readyTargets = $readyTargets
        waitingTargets = $waitingTargets
        errorTargets = $errorTargets
        verifiedTargets = $verifiedTargets
        failedTargets = $failedTargets
        gateStatePath = $gateStatePath
        postAuthStatePath = $postAuthStatePath
    }

    $state | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $runnerStatePath -Encoding UTF8
    Write-Output "runnerState=$runnerStatePath"
}

$checkedAt = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ssK")
Write-Output "browser-login-continuation-runner"
Write-Output "checkedAt=$checkedAt"
Write-Output "serial=$Serial"
Write-Output "stateDir=$StateDir"

& $GateScript -Serial $Serial -WriteState -StateDir $StateDir
$gateExit = $LASTEXITCODE
Write-Output "gateExit=$gateExit"

if ($gateExit -eq 2) {
    $nextAction = "wait_for_real_account_authorization"
    Write-Output "nextAction=$nextAction"
    Write-RunnerState -ExitCode 2 -NextAction $nextAction -GateExit $gateExit -PostAuthAttempted $false
    $reportExit = Invoke-EvidenceReport
    Write-Output "evidenceReportExit=$reportExit"
    exit 2
}

if ($gateExit -ne 0) {
    $nextAction = "inspect_environment_or_status_output"
    Write-Output "nextAction=$nextAction"
    Write-RunnerState -ExitCode 1 -NextAction $nextAction -GateExit $gateExit -PostAuthAttempted $false
    $reportExit = Invoke-EvidenceReport
    Write-Output "evidenceReportExit=$reportExit"
    if ($reportExit -ne 0 -and $reportExit -ne 2) {
        exit 1
    }
    exit 1
}

$postAuthArgs = @{
    Serial = $Serial
    UseExistingGateState = $true
    WriteState = $true
    StateDir = $StateDir
}
if ($PostAuthPlanOnly) {
    $postAuthArgs.PlanOnly = $true
}

& $PostAuthScript @postAuthArgs
$postAuthExit = $LASTEXITCODE
Write-Output "postAuthExit=$postAuthExit"

if ($postAuthExit -eq 0) {
    $nextAction = "record_post_auth_completion_evidence"
    Write-Output "nextAction=$nextAction"
    Write-RunnerState -ExitCode 0 -NextAction $nextAction -GateExit $gateExit -PostAuthExit $postAuthExit -PostAuthAttempted $true
    $reportExit = Invoke-EvidenceReport
    Write-Output "evidenceReportExit=$reportExit"
    if ($reportExit -ne 0) {
        exit 1
    }
    exit 0
}

if ($postAuthExit -eq 2) {
    $nextAction = "wait_for_real_account_authorization"
    Write-Output "nextAction=$nextAction"
    Write-RunnerState -ExitCode 2 -NextAction $nextAction -GateExit $gateExit -PostAuthExit $postAuthExit -PostAuthAttempted $true
    $reportExit = Invoke-EvidenceReport
    Write-Output "evidenceReportExit=$reportExit"
    exit 2
}

$nextAction = "inspect_post_auth_probe_output"
Write-Output "nextAction=$nextAction"
Write-RunnerState -ExitCode 1 -NextAction $nextAction -GateExit $gateExit -PostAuthExit $postAuthExit -PostAuthAttempted $true
$reportExit = Invoke-EvidenceReport
Write-Output "evidenceReportExit=$reportExit"
if ($reportExit -ne 0 -and $reportExit -ne 2) {
    exit 1
}
exit 1
