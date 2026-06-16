param(
    [string]$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
)

$ErrorActionPreference = 'Stop'

$mainPath = Join-Path $Root 'app/src/main/java/com/kite/app/MainActivity.kt'
$storePath = Join-Path $Root 'app/src/main/java/com/kite/app/resources/KiteResourceInstallStore.kt'

$main = Get-Content -LiteralPath $mainPath -Raw
$store = Get-Content -LiteralPath $storePath -Raw
$failures = New-Object System.Collections.Generic.List[string]

function Assert-True {
    param(
        [bool]$Condition,
        [string]$Message
    )
    if (-not $Condition) {
        $script:failures.Add($Message)
    }
}

function Function-Body {
    param(
        [string]$Source,
        [string]$Name
    )
    $pattern = "(?s)private fun $([regex]::Escape($Name))\b.*?(?=\n    private fun |\n    private data class |\n    private enum class |\n    companion object|\n    override fun |\z)"
    return [regex]::Match($Source, $pattern).Value
}

Assert-True ($main -notmatch 'maybeRenderShellProgress') 'shell progress must not route through maybeRenderShellProgress.'
Assert-True ($main -notmatch 'SHELL_PROGRESS_RENDER_INTERVAL_MS') 'shell progress render throttle must not imply whole-surface redraw.'
Assert-True ($main -notmatch 'lastShellProgressRenderAt') 'shell progress render timestamp should stay removed.'
Assert-True ($main -notmatch 'File\(entry\.transcriptPath\)\.readText\(\)') 'terminal auth transcript must not be fully read on UI path.'
Assert-True ($main -match 'readTerminalAuthorizationUrlFromTranscript') 'terminal auth transcript reader should stay isolated.'
Assert-True ($main -match 'updateVisibleCardRunReport') 'report page must have local output binding.'
Assert-True ($main -match 'updateVisibleResourceInstallWizardElapsed') 'install wizard must have local elapsed binding.'
Assert-True ($main -match 'updateVisibleConsoleCard') 'console card runtime changes should have local binding.'
Assert-True ($main -match 'resourceCatalogForUiRender') 'UI resource render should use cached catalog helper.'

$handleProgress = Function-Body $main 'handleShellProgress'
Assert-True ($handleProgress -notmatch 'showCardRunSurface|showConsole|renderResourceInstallWizardFor') 'handleShellProgress must not redraw whole surfaces.'

$showManage = Function-Body $main 'showResourceManage'
Assert-True ($showManage -notmatch 'resourceCatalog\(forceRefresh = true\)|planSnapshot\(\)|registrySnapshot\(') 'showResourceManage must not synchronously build catalog or DB snapshots.'
Assert-True ($showManage -match 'requestResourceManageRefresh') 'showResourceManage should request a background payload.'

$consoleRefresh = Function-Body $main 'maybeRefreshConsoleAfterRuntimeState'
Assert-True ($consoleRefresh -match 'updateVisibleConsoleCard') 'console runtime refresh should update a visible card first.'

Assert-True ($store -match 'data class KiteResourceInstallSignal') 'install store must expose KiteResourceInstallSignal.'
Assert-True ($store -match 'val signals: StateFlow<KiteResourceInstallSignal>') 'install store must expose signals StateFlow.'
foreach ($reason in @(
    'markInstalling',
    'markUninstalling',
    'markInstalled',
    'markFailed',
    'clear',
    'beginPlan',
    'markPlanStepRunning',
    'advancePlanAfter',
    'failPlanAt',
    'clearPlan'
)) {
    Assert-True ($store -match "emitSignal\(`"$reason") "install store must emit signal for $reason."
}

if ($failures.Count -gt 0) {
    Write-Host 'Kite runtime lane static checks failed:' -ForegroundColor Red
    foreach ($failure in $failures) {
        Write-Host " - $failure" -ForegroundColor Red
    }
    exit 1
}

Write-Host 'Kite runtime lane static checks passed.' -ForegroundColor Green
