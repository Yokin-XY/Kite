param(
    [string]$Serial = "3f8bbaad",
    [string[]]$Targets = @(),
    [string]$GateScript = "",
    [string]$StateDir = "",
    [switch]$UseExistingGateState,
    [switch]$PlanOnly,
    [switch]$WriteState
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($GateScript)) {
    $GateScript = Join-Path $PSScriptRoot "browser-login-continuation-gate.ps1"
}
if (-not (Test-Path -LiteralPath $GateScript)) {
    throw "Gate script not found: $GateScript"
}
if ([string]::IsNullOrWhiteSpace($StateDir)) {
    $StateDir = Join-Path $env:LOCALAPPDATA "Kite\browser-login-continuation"
}

$gateStatePath = Join-Path $StateDir "last-status.json"
$postAuthStatePath = Join-Path $StateDir "post-auth-status.json"
$postAuthRawPath = Join-Path $StateDir "post-auth-raw.txt"

function ConvertTo-SafeStatusText {
    param([string]$Value)

    if ($null -eq $Value) {
        return ""
    }

    $safe = $Value
    $safe = $safe -replace '[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}', '<account>'
    $safe = $safe -replace '(?i)("?(?:access[_-]?token|refresh[_-]?token|id[_-]?token|api[_-]?key|secret|authorization|code)"?\s*[:=]\s*)"?[^",\s}]+', '$1<redacted>'
    $safe = $safe -replace '(?i)(sk-[A-Za-z0-9_-]{16,})', '<api-key>'
    $safe = $safe -replace '(?i)(sess-[A-Za-z0-9_-]{16,})', '<session>'
    return $safe
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
    return $normalized.ToArray()
}

New-Item -ItemType Directory -Force -Path $StateDir | Out-Null

if (-not $UseExistingGateState) {
    & $GateScript -Serial $Serial -WriteState -StateDir $StateDir | Out-Null
    $gateExit = $LASTEXITCODE
} else {
    $gateExit = $null
}

if (-not (Test-Path -LiteralPath $gateStatePath)) {
    throw "Gate state not found: $gateStatePath"
}

$gateState = Get-Content -Raw -LiteralPath $gateStatePath | ConvertFrom-Json
$readyTargets = Normalize-Targets @($gateState.readyTargets)
$selectedTargets = Normalize-Targets $Targets
if ($selectedTargets.Count -eq 0) {
    $selectedTargets = $readyTargets
}

$checkedAt = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ssK")

Write-Output "browser-login-post-auth-verify"
Write-Output "checkedAt=$checkedAt"
Write-Output "serial=$Serial"
Write-Output "gateExit=$gateExit"
Write-Output "readyTargets=$($readyTargets -join ',')"
Write-Output "selectedTargets=$($selectedTargets -join ',')"

if ($selectedTargets.Count -eq 0) {
    $state = [ordered]@{
        checkedAt = $checkedAt
        serial = $Serial
        gateExit = $gateExit
        readyTargets = @($readyTargets)
        selectedTargets = @()
        verifiedTargets = @()
        failedTargets = @()
        skippedTargets = @("codex", "claude")
        nextAction = "wait_for_real_account_authorization"
    }
    if ($WriteState) {
        $state | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $postAuthStatePath -Encoding UTF8
        Write-Output "stateJson=$postAuthStatePath"
    }
    Write-Output "nextAction=wait_for_real_account_authorization"
    exit 2
}

if ($PlanOnly) {
    $state = [ordered]@{
        checkedAt = $checkedAt
        serial = $Serial
        gateExit = $gateExit
        readyTargets = @($readyTargets)
        selectedTargets = @($selectedTargets)
        verifiedTargets = @()
        failedTargets = @()
        skippedTargets = @()
        nextAction = "run_post_auth_probe"
        planOnly = $true
    }
    if ($WriteState) {
        $state | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $postAuthStatePath -Encoding UTF8
        Write-Output "stateJson=$postAuthStatePath"
    }
    Write-Output "nextAction=run_post_auth_probe"
    exit 0
}

$proot = "/data/user/0/com.kite.app/files/runtime/bin/proot"
$lib = "/data/user/0/com.kite.app/files/runtime/lib"
$tmp = "/data/user/0/com.kite.app/files/runtime/tmp"
$rootfs = "/data/user/0/com.kite.app/files/runtime/containers/ubuntu-main/rootfs"
$workspace = "/data/user/0/com.kite.app/files/runtime/shared/ubuntu-main"
$resolv = "/data/user/0/com.kite.app/files/runtime/tmp/resolv.conf"

$innerParts = New-Object System.Collections.Generic.List[string]
$innerParts.Add("export PATH=/workspace/.kf/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin")

if ($selectedTargets -contains "codex") {
    $innerParts.Add("echo ---codex-post-auth-version---")
    $innerParts.Add("HOME=/root codex --version 2>&1; echo version_exit=`$?")
    $innerParts.Add("echo ---codex-post-auth-login-status---")
    $innerParts.Add("HOME=/root codex login status 2>&1; echo status_exit=`$?")
    $innerParts.Add("echo ---codex-post-auth-doctor-json---")
    $innerParts.Add("HOME=/root codex doctor --json 2>&1; echo doctor_exit=`$?")
}

if ($selectedTargets -contains "claude") {
    $innerParts.Add("echo ---claude-post-auth-version---")
    $innerParts.Add("HOME=/root claude --version 2>&1; echo version_exit=`$?")
    $innerParts.Add("echo ---claude-post-auth-status-json---")
    $innerParts.Add("HOME=/root claude auth status --json 2>&1; echo status_exit=`$?")
}

$inner = ($innerParts.ToArray() -join "; ")
$remote = "run-as com.kite.app sh -c 'PROOT_TMP_DIR=$tmp LD_LIBRARY_PATH=$lib $proot --link2symlink -0 -r $rootfs -w /workspace -b /dev:/dev -b /proc:/proc -b /sys:/sys -b ${workspace}:/workspace -b ${resolv}:/etc/resolv.conf /bin/bash -lc `"$inner`"'"

$rawLines = & adb -s $Serial shell $remote 2>&1 | ForEach-Object {
    ConvertTo-SafeStatusText $_.ToString()
}
$rawText = $rawLines -join [Environment]::NewLine

$verified = New-Object System.Collections.Generic.List[string]
$failed = New-Object System.Collections.Generic.List[string]

if ($selectedTargets -contains "codex") {
    $codexStatus = Get-StatusSection -Lines $rawLines -Name "codex-post-auth-login-status"
    $codexDoctor = Get-StatusSection -Lines $rawLines -Name "codex-post-auth-doctor-json"
    if ($codexStatus -notmatch '(?i)not logged in' -and
        $codexStatus -notmatch '(?i)(command not found|no such file|not found|error)' -and
        $codexDoctor -match 'doctor_exit=0') {
        $verified.Add("codex")
    } else {
        $failed.Add("codex")
    }
}

if ($selectedTargets -contains "claude") {
    $claudeStatus = Get-StatusSection -Lines $rawLines -Name "claude-post-auth-status-json"
    if ($claudeStatus -match '"loggedIn"\s*:\s*true' -and
        $claudeStatus -match 'status_exit=0') {
        $verified.Add("claude")
    } else {
        $failed.Add("claude")
    }
}

$verifiedTargets = $verified.ToArray()
$failedTargets = $failed.ToArray()

if ($verifiedTargets.Count -gt 0 -and $failedTargets.Count -eq 0) {
    $nextAction = "record_post_auth_completion_evidence"
} elseif ($verifiedTargets.Count -gt 0) {
    $nextAction = "record_partial_evidence_and_inspect_failed_targets"
} else {
    $nextAction = "inspect_post_auth_probe_output"
}

Write-Output "verifiedTargets=$($verifiedTargets -join ',')"
Write-Output "failedTargets=$($failedTargets -join ',')"
Write-Output "nextAction=$nextAction"

if ($WriteState) {
    $state = [ordered]@{
        checkedAt = $checkedAt
        serial = $Serial
        gateExit = $gateExit
        readyTargets = @($readyTargets)
        selectedTargets = @($selectedTargets)
        verifiedTargets = @($verifiedTargets)
        failedTargets = @($failedTargets)
        nextAction = $nextAction
    }
    $state | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $postAuthStatePath -Encoding UTF8
    $rawText | Set-Content -LiteralPath $postAuthRawPath -Encoding UTF8
    Write-Output "stateJson=$postAuthStatePath"
    Write-Output "stateRaw=$postAuthRawPath"
}

if ($verifiedTargets.Count -gt 0 -and $failedTargets.Count -eq 0) {
    exit 0
}

exit 1
