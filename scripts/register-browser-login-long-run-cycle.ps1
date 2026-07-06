param(
    [string]$Serial = "3f8bbaad",
    [int]$Minutes = 60,
    [int]$Days = 1,
    [int]$SmokeIterations = 6,
    [int]$SmokeIntervalSeconds = 600,
    [string]$TaskName = "KiteBrowserLoginLongRunCycle",
    [switch]$RunCompletionAuditOnWaiting
)

$ErrorActionPreference = "Stop"

if ($Minutes -lt 15) {
    throw "Minutes must be at least 15 to avoid excessive ADB/browser smoke polling."
}
if ($SmokeIterations -lt 1) {
    throw "SmokeIterations must be at least 1."
}
if ($SmokeIntervalSeconds -lt 0) {
    throw "SmokeIntervalSeconds must be 0 or greater."
}

$cycleScript = Join-Path $PSScriptRoot "browser-login-long-run-cycle.ps1"
if (-not (Test-Path -LiteralPath $cycleScript)) {
    throw "Long-run cycle script not found: $cycleScript"
}

$stateDir = Join-Path $env:LOCALAPPDATA "Kite\browser-login-continuation"
$currentShell = (Get-Process -Id $PID).Path
$pwsh = (Get-Command pwsh.exe -ErrorAction SilentlyContinue).Source
$windowsPowerShell = (Get-Command powershell.exe -ErrorAction SilentlyContinue).Source

if (-not [string]::IsNullOrWhiteSpace($currentShell) -and
    [System.IO.Path]::GetFileName($currentShell).Equals("pwsh.exe", [System.StringComparison]::OrdinalIgnoreCase)) {
    $powershell = $currentShell
} elseif (-not [string]::IsNullOrWhiteSpace($pwsh)) {
    $powershell = $pwsh
} elseif (-not [string]::IsNullOrWhiteSpace($windowsPowerShell)) {
    $powershell = $windowsPowerShell
} else {
    throw "No PowerShell executable found for scheduled task action."
}

$argument = "-NoProfile -ExecutionPolicy Bypass -File `"$cycleScript`" -Serial `"$Serial`" -StateDir `"$stateDir`" -SmokeIterations $SmokeIterations -SmokeIntervalSeconds $SmokeIntervalSeconds"
if ($RunCompletionAuditOnWaiting) {
    $argument = "$argument -RunCompletionAuditOnWaiting"
}

$action = New-ScheduledTaskAction -Execute $powershell -Argument $argument
$trigger = New-ScheduledTaskTrigger `
    -Once `
    -At (Get-Date).AddMinutes(1) `
    -RepetitionInterval (New-TimeSpan -Minutes $Minutes) `
    -RepetitionDuration (New-TimeSpan -Days $Days)
$settings = New-ScheduledTaskSettingsSet `
    -MultipleInstances IgnoreNew `
    -StartWhenAvailable `
    -ExecutionTimeLimit (New-TimeSpan -Hours 2)

Register-ScheduledTask `
    -TaskName $TaskName `
    -Action $action `
    -Trigger $trigger `
    -Settings $settings `
    -Description "Kite browser-login long-run cycle. Exit 2 means account authorization is still required but browser smoke stayed healthy; exit 0 means completion evidence is ready." `
    -Force | Out-Null

Write-Output "registeredTask=$TaskName"
Write-Output "intervalMinutes=$Minutes"
Write-Output "durationDays=$Days"
Write-Output "serial=$Serial"
Write-Output "smokeIterations=$SmokeIterations"
Write-Output "smokeIntervalSeconds=$SmokeIntervalSeconds"
Write-Output "runCompletionAuditOnWaiting=$([bool]$RunCompletionAuditOnWaiting)"
Write-Output "powershell=$powershell"
Write-Output "cycle=$cycleScript"
Write-Output "stateDir=$stateDir"
