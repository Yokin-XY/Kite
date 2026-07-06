param(
    [string]$Serial = "3f8bbaad",
    [int]$Minutes = 60,
    [int]$Days = 7,
    [string]$TaskName = "KiteBrowserLoginContinuationGate"
)

$ErrorActionPreference = "Stop"

if ($Minutes -lt 5) {
    throw "Minutes must be at least 5 to avoid excessive ADB/PRoot polling."
}

$runnerScript = Join-Path $PSScriptRoot "browser-login-continuation-runner.ps1"
if (-not (Test-Path -LiteralPath $runnerScript)) {
    throw "Runner script not found: $runnerScript"
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

$argument = "-NoProfile -ExecutionPolicy Bypass -File `"$runnerScript`" -Serial `"$Serial`" -StateDir `"$stateDir`""
$action = New-ScheduledTaskAction -Execute $powershell -Argument $argument
$trigger = New-ScheduledTaskTrigger `
    -Once `
    -At (Get-Date).AddMinutes(1) `
    -RepetitionInterval (New-TimeSpan -Minutes $Minutes) `
    -RepetitionDuration (New-TimeSpan -Days $Days)
$settings = New-ScheduledTaskSettingsSet `
    -MultipleInstances IgnoreNew `
    -StartWhenAvailable `
    -ExecutionTimeLimit (New-TimeSpan -Minutes 10)

Register-ScheduledTask `
    -TaskName $TaskName `
    -Action $action `
    -Trigger $trigger `
    -Settings $settings `
    -Description "Kite browser-login continuation runner. Exit 0 means post-auth evidence is ready; exit 2 means real account authorization is still required." `
    -Force | Out-Null

Write-Output "registeredTask=$TaskName"
Write-Output "intervalMinutes=$Minutes"
Write-Output "durationDays=$Days"
Write-Output "serial=$Serial"
Write-Output "powershell=$powershell"
Write-Output "runner=$runnerScript"
Write-Output "stateDir=$stateDir"
