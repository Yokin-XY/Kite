param(
    [string]$StateDir = "",
    [string]$OutputPath = "",
    [switch]$RequireVerified
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($StateDir)) {
    $StateDir = Join-Path $env:LOCALAPPDATA "Kite\browser-login-continuation"
}
if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    $OutputPath = Join-Path $StateDir "post-auth-evidence-report.md"
}

$gateStatePath = Join-Path $StateDir "last-status.json"
$runnerStatePath = Join-Path $StateDir "runner-status.json"
$postAuthStatePath = Join-Path $StateDir "post-auth-status.json"
$postAuthRawPath = Join-Path $StateDir "post-auth-raw.txt"

function Read-JsonOrNull {
    param([string]$Path)

    if (-not (Test-Path -LiteralPath $Path)) {
        return $null
    }
    return Get-Content -Raw -LiteralPath $Path | ConvertFrom-Json
}

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
    $safe = $safe -replace '(?i)(Bearer\s+)[A-Za-z0-9._~+/=-]+', '$1<bearer-token>'
    return $safe
}

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

function Get-ValueOrEmpty {
    param(
        [object]$Object,
        [string]$Name
    )

    if ($null -eq $Object) {
        return ""
    }
    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property -or $null -eq $property.Value) {
        return ""
    }
    return (ConvertTo-SafeStatusText $property.Value.ToString())
}

function Get-ArrayValue {
    param(
        [object]$Primary,
        [object]$Fallback,
        [string]$Name
    )

    $primaryValue = $null
    if ($null -ne $Primary -and $null -ne $Primary.PSObject.Properties[$Name]) {
        $primaryValue = $Primary.PSObject.Properties[$Name].Value
    }
    $primaryItems = ConvertTo-StringArray $primaryValue
    if ($primaryItems.Count -gt 0) {
        return $primaryItems
    }

    $fallbackValue = $null
    if ($null -ne $Fallback -and $null -ne $Fallback.PSObject.Properties[$Name]) {
        $fallbackValue = $Fallback.PSObject.Properties[$Name].Value
    }
    return ConvertTo-StringArray $fallbackValue
}

function Get-RawHighlights {
    param([string]$Path)

    $highlights = New-Object System.Collections.Generic.List[string]
    if (-not (Test-Path -LiteralPath $Path)) {
        return $highlights.ToArray()
    }

    $rawLines = Get-Content -LiteralPath $Path
    foreach ($line in $rawLines) {
        $safeLine = ConvertTo-SafeStatusText $line
        if ($safeLine -match '^---.+---$' -or
            $safeLine -match '(?i)(version_exit|status_exit|doctor_exit)=' -or
            $safeLine -match '(?i)(codex-cli|Claude Code|loggedIn|Logged in|Not logged in|authMethod|apiProvider)') {
            $highlights.Add($safeLine)
        }

        if ($highlights.Count -ge 80) {
            break
        }
    }
    return $highlights.ToArray()
}

$gateState = Read-JsonOrNull $gateStatePath
$runnerState = Read-JsonOrNull $runnerStatePath
$postAuthAttempted = $true
if ($null -ne $runnerState -and $null -ne $runnerState.PSObject.Properties["postAuthAttempted"]) {
    $postAuthAttempted = [bool]$runnerState.postAuthAttempted
}

$postAuthState = $null
if ($postAuthAttempted) {
    $postAuthState = Read-JsonOrNull $postAuthStatePath
}

$checkedAt = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ssK")
$serial = Get-ValueOrEmpty $runnerState "serial"
if ([string]::IsNullOrWhiteSpace($serial)) {
    $serial = Get-ValueOrEmpty $gateState "serial"
}

$readyTargets = Get-ArrayValue -Primary $runnerState -Fallback $gateState -Name "readyTargets"
$waitingTargets = Get-ArrayValue -Primary $runnerState -Fallback $gateState -Name "waitingTargets"
$errorTargets = Get-ArrayValue -Primary $runnerState -Fallback $gateState -Name "errorTargets"
$verifiedTargets = Get-ArrayValue -Primary $postAuthState -Fallback $runnerState -Name "verifiedTargets"
$failedTargets = Get-ArrayValue -Primary $postAuthState -Fallback $runnerState -Name "failedTargets"
$selectedTargets = ConvertTo-StringArray $(if ($null -ne $postAuthState -and $null -ne $postAuthState.PSObject.Properties["selectedTargets"]) { $postAuthState.selectedTargets } else { @() })

$runnerExit = Get-ValueOrEmpty $runnerState "exitCode"
$gateExit = Get-ValueOrEmpty $runnerState "gateExit"
if ([string]::IsNullOrWhiteSpace($gateExit)) {
    $gateExit = Get-ValueOrEmpty $postAuthState "gateExit"
}
$postAuthExit = Get-ValueOrEmpty $runnerState "postAuthExit"
$nextAction = Get-ValueOrEmpty $postAuthState "nextAction"
if ([string]::IsNullOrWhiteSpace($nextAction)) {
    $nextAction = Get-ValueOrEmpty $runnerState "nextAction"
}
if ([string]::IsNullOrWhiteSpace($nextAction)) {
    $nextAction = Get-ValueOrEmpty $gateState "nextAction"
}

$rawHighlights = @()
if ($postAuthAttempted) {
    $rawHighlights = Get-RawHighlights $postAuthRawPath
}

$status = "waiting_for_real_account_authorization"
if ($verifiedTargets.Count -gt 0 -and $failedTargets.Count -eq 0) {
    $status = "post_auth_verified"
} elseif ($verifiedTargets.Count -gt 0) {
    $status = "post_auth_partially_verified"
} elseif ($failedTargets.Count -gt 0) {
    $status = "post_auth_failed"
} elseif ($errorTargets.Count -gt 0) {
    $status = "inspect_environment_or_status_output"
}

$readyTargetsText = Format-Items $readyTargets
$waitingTargetsText = Format-Items $waitingTargets
$errorTargetsText = Format-Items $errorTargets
$selectedTargetsText = Format-Items $selectedTargets
$verifiedTargetsText = Format-Items $verifiedTargets
$failedTargetsText = Format-Items $failedTargets

$codexVersionText = Get-ValueOrEmpty $gateState "codexVersion"
$codexGateStateText = Get-ValueOrEmpty $gateState "codexGateState"
$codexLoginStatusText = Get-ValueOrEmpty $gateState "codexLoginStatus"
$claudeVersionText = Get-ValueOrEmpty $gateState "claudeVersion"
$claudeGateStateText = Get-ValueOrEmpty $gateState "claudeGateState"
$claudeAuthStatusText = Get-ValueOrEmpty $gateState "claudeAuthStatus"

$lines = New-Object System.Collections.Generic.List[string]
$lines.Add("# 浏览器登录账号补证摘要")
$lines.Add("")
$lines.Add("- 生成时间：$checkedAt")
$lines.Add("- 设备：$serial")
$lines.Add("- 状态：$status")
$lines.Add("- 下一步：$nextAction")
$lines.Add("")
$lines.Add("## 退出码")
$lines.Add("")
$lines.Add("- runnerExit：$runnerExit")
$lines.Add("- gateExit：$gateExit")
$lines.Add("- postAuthExit：$postAuthExit")
$lines.Add("")
$lines.Add("## 目标")
$lines.Add("")
$lines.Add("- readyTargets：$readyTargetsText")
$lines.Add("- waitingTargets：$waitingTargetsText")
$lines.Add("- errorTargets：$errorTargetsText")
$lines.Add("- selectedTargets：$selectedTargetsText")
$lines.Add("- verifiedTargets：$verifiedTargetsText")
$lines.Add("- failedTargets：$failedTargetsText")
$lines.Add("")
$lines.Add("## gate 摘要")
$lines.Add("")
$lines.Add("- codexVersion：$codexVersionText")
$lines.Add("- codexGateState：$codexGateStateText")
$lines.Add("- codexLoginStatus：$codexLoginStatusText")
$lines.Add("- claudeVersion：$claudeVersionText")
$lines.Add("- claudeGateState：$claudeGateStateText")
$lines.Add("- claudeAuthStatus：$claudeAuthStatusText")
$lines.Add("")
$lines.Add("## post-auth raw 摘要")
$lines.Add("")
if ($rawHighlights.Count -eq 0) {
    $lines.Add("(none)")
} else {
    foreach ($line in $rawHighlights) {
        $lines.Add("- ``$($line)``")
    }
}
$lines.Add("")
$lines.Add("## 边界")
$lines.Add("")
$lines.Add("- 本报告只整理已脱敏的状态文件和 raw 输出。")
$lines.Add("- 本报告不是登录事实来源；登录事实仍以 CLI 官方状态命令和 post-auth 验证结果为准。")
$lines.Add("- 不保存账号邮箱、token、API key 或 callback code 原文。")

$outputDirectory = Split-Path -Parent $OutputPath
if (-not [string]::IsNullOrWhiteSpace($outputDirectory)) {
    New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
}
$lines.ToArray() | Set-Content -LiteralPath $OutputPath -Encoding UTF8

Write-Output "browser-login-evidence-report"
Write-Output "checkedAt=$checkedAt"
Write-Output "stateDir=$StateDir"
Write-Output "report=$OutputPath"
Write-Output "status=$status"
Write-Output "verifiedTargets=$($verifiedTargets -join ',')"
Write-Output "failedTargets=$($failedTargets -join ',')"
Write-Output "nextAction=$nextAction"

if ($failedTargets.Count -gt 0 -or $errorTargets.Count -gt 0) {
    exit 1
}
if ($verifiedTargets.Count -gt 0) {
    exit 0
}
if ($RequireVerified) {
    exit 2
}
exit 2
