param(
    [string]$Serial = "3f8bbaad",
    [string]$StatusScript = "",
    [switch]$WriteState,
    [string]$StateDir = ""
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($StatusScript)) {
    $StatusScript = Join-Path $PSScriptRoot "browser-login-auth-status.ps1"
}

if (-not (Test-Path -LiteralPath $StatusScript)) {
    throw "Status script not found: $StatusScript"
}

function Get-StatusSection {
    param(
        [string[]]$Lines,
        [string]$Name
    )

    $marker = "---$Name---"
    $start = [Array]::IndexOf($Lines, $marker)
    if ($start -lt 0) {
        return ""
    }

    $section = New-Object System.Collections.Generic.List[string]
    for ($i = $start + 1; $i -lt $Lines.Count; $i++) {
        if ($Lines[$i] -match '^---.+---$') {
            break
        }
        $section.Add($Lines[$i])
    }

    return (($section.ToArray()) -join [Environment]::NewLine).Trim()
}

function Get-CodexGateState {
    param([string]$LoginStatus)

    if ([string]::IsNullOrWhiteSpace($LoginStatus)) {
        return "unknown"
    }
    if ($LoginStatus -match '(?i)not logged in') {
        return "account_required"
    }
    if ($LoginStatus -match '(?i)(command not found|no such file|not found|error)') {
        return "environment_error"
    }

    return "logged_in_candidate"
}

function Get-ClaudeGateState {
    param([string]$AuthStatus)

    if ([string]::IsNullOrWhiteSpace($AuthStatus)) {
        return "unknown"
    }
    if ($AuthStatus -match '"loggedIn"\s*:\s*true') {
        return "logged_in"
    }
    if ($AuthStatus -match '"loggedIn"\s*:\s*false') {
        return "account_required"
    }
    if ($AuthStatus -match '(?i)(command not found|no such file|not found|error)') {
        return "environment_error"
    }

    return "unknown"
}

$checkedAt = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ssK")
$rawLines = & $StatusScript -Serial $Serial 2>&1 | ForEach-Object { $_.ToString() }
$rawText = $rawLines -join [Environment]::NewLine

$codexVersion = Get-StatusSection -Lines $rawLines -Name "codex-version"
$codexLoginStatus = Get-StatusSection -Lines $rawLines -Name "codex-login-status"
$claudeVersion = Get-StatusSection -Lines $rawLines -Name "claude-version"
$claudeAuthStatus = Get-StatusSection -Lines $rawLines -Name "claude-auth-status"

$codexGateState = Get-CodexGateState -LoginStatus $codexLoginStatus
$claudeGateState = Get-ClaudeGateState -AuthStatus $claudeAuthStatus

$readyTargets = New-Object System.Collections.Generic.List[string]
$waitingTargets = New-Object System.Collections.Generic.List[string]
$errorTargets = New-Object System.Collections.Generic.List[string]

if ($codexGateState -eq "logged_in_candidate") {
    $readyTargets.Add("codex")
} elseif ($codexGateState -eq "account_required") {
    $waitingTargets.Add("codex")
} else {
    $errorTargets.Add("codex")
}

if ($claudeGateState -eq "logged_in") {
    $readyTargets.Add("claude")
} elseif ($claudeGateState -eq "account_required") {
    $waitingTargets.Add("claude")
} else {
    $errorTargets.Add("claude")
}

$readyTargetNames = $readyTargets.ToArray()
$waitingTargetNames = $waitingTargets.ToArray()
$errorTargetNames = $errorTargets.ToArray()
$readyForPostAuthVerification = $readyTargetNames.Count -gt 0

if ($readyForPostAuthVerification -and $waitingTargetNames.Count -gt 0) {
    $nextAction = "verify_ready_targets_and_continue_waiting_for_accounts"
} elseif ($readyForPostAuthVerification -and $errorTargetNames.Count -gt 0) {
    $nextAction = "verify_ready_targets_and_inspect_errors"
} elseif ($readyForPostAuthVerification) {
    $nextAction = "ready_for_post_auth_cli_verification"
} elseif ($waitingTargetNames.Count -gt 0 -and $errorTargetNames.Count -eq 0) {
    $nextAction = "wait_for_real_account_authorization"
} else {
    $nextAction = "inspect_environment_or_status_output"
}

$state = [ordered]@{
    checkedAt = $checkedAt
    serial = $Serial
    codexVersion = $codexVersion
    codexGateState = $codexGateState
    codexLoginStatus = $codexLoginStatus
    claudeVersion = $claudeVersion
    claudeGateState = $claudeGateState
    claudeAuthStatus = $claudeAuthStatus
    readyTargets = @($readyTargetNames)
    waitingTargets = @($waitingTargetNames)
    errorTargets = @($errorTargetNames)
    readyForPostAuthVerification = $readyForPostAuthVerification
    nextAction = $nextAction
}

Write-Output "browser-login-continuation-gate"
Write-Output "checkedAt=$checkedAt"
Write-Output "serial=$Serial"
Write-Output "codex=$codexGateState"
Write-Output "claude=$claudeGateState"
Write-Output "readyTargets=$($readyTargetNames -join ',')"
Write-Output "waitingTargets=$($waitingTargetNames -join ',')"
Write-Output "errorTargets=$($errorTargetNames -join ',')"
Write-Output "readyForPostAuthVerification=$readyForPostAuthVerification"
Write-Output "nextAction=$nextAction"

if ($WriteState) {
    if ([string]::IsNullOrWhiteSpace($StateDir)) {
        $StateDir = Join-Path $env:LOCALAPPDATA "Kite\browser-login-continuation"
    }
    New-Item -ItemType Directory -Force -Path $StateDir | Out-Null
    $jsonPath = Join-Path $StateDir "last-status.json"
    $rawPath = Join-Path $StateDir "last-status-raw.txt"
    $state | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $jsonPath -Encoding UTF8
    $rawText | Set-Content -LiteralPath $rawPath -Encoding UTF8
    Write-Output "stateJson=$jsonPath"
    Write-Output "stateRaw=$rawPath"
}

if ($readyForPostAuthVerification) {
    exit 0
}

if ($waitingTargetNames.Count -gt 0 -and $errorTargetNames.Count -eq 0) {
    exit 2
}

exit 1
