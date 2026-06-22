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
Assert-True ($main -notmatch 'startTerminalAuthorizationLinkWatcher|readTerminalAuthorizationUrlFromTranscript|extractTerminalAuthorizationUrl|TERMINAL_AUTH_LINK') 'terminal web open must use browser proxy events, not transcript URL parsing.'
Assert-True ($main -match 'updateVisibleCardRunReport') 'report page must have local output binding.'
Assert-True ($main -match 'updateVisibleResourceInstallWizardElapsed') 'install wizard must have local elapsed binding.'
Assert-True ($main -match 'updateVisibleConsoleCard') 'console card runtime changes should have local binding.'
Assert-True ($main -match 'resourceCatalogForUiRender') 'UI resource render should use cached catalog helper.'
Assert-True ($main -match 'observeRuntimePanelSummarySignals') 'runtime panel summary counts must observe existing store snapshots.'

$handleProgress = Function-Body $main 'handleShellProgress'
Assert-True ($handleProgress -notmatch 'showCardRunSurface|showConsole|renderResourceInstallWizardFor') 'handleShellProgress must not redraw whole surfaces.'

$runtimePanelObserver = Function-Body $main 'observeRuntimePanelSummarySignals'
Assert-True ($runtimePanelObserver -match 'TerminalSessionStore\.snapshot\.collect') 'runtime panel terminal count must reuse TerminalSessionStore snapshot.'
Assert-True ($runtimePanelObserver -match 'TaskManagerStore\.snapshot\.collect') 'runtime panel process count must reuse TaskManagerStore snapshot.'
Assert-True ($runtimePanelObserver -notmatch 'showCardRunSurface|showConsole|showKiteProcessOverview|refreshResourceScreenIfVisible') 'runtime panel count observer must not trigger whole-surface refreshes.'

$runtimePanelSummary = Function-Body $main 'runtimePanelSummary'
Assert-True ($runtimePanelSummary -match 'CardRunStore\.runs\.value') 'runtime panel card count must reuse CardRunStore.'
Assert-True ($runtimePanelSummary -match 'TerminalSessionStore\.snapshot\.value\.liveSessions\.size') 'runtime panel terminal count must reuse live terminal snapshot.'
Assert-True ($runtimePanelSummary -match 'TaskManagerStore\.snapshot\.value\.processes\.size') 'runtime panel process count must reuse task-manager snapshot.'

$runtimePanelCounts = Function-Body $main 'renderRuntimePanelCounts'
Assert-True ($runtimePanelCounts -match 'runtimePanelCardCountView\?\.text') 'runtime panel counts should update existing value views.'
Assert-True ($runtimePanelCounts -notmatch 'showCardRunSurface|showConsole|showKiteProcessOverview|RuntimeHealthStore\.refresh|TaskManagerStore\.refresh|TerminalSessionStore\.refresh') 'runtime panel count rendering must stay a cheap text bind.'

$showRuntimePanel = Function-Body $main 'showUbuntuRuntimePanel'
Assert-True ($showRuntimePanel -match 'requestRuntimePanelSummaryRefresh') 'opening runtime panel should request throttled summary refresh.'

$showRunManagement = Function-Body $main 'showKiteProcessOverview'
Assert-True ($showRunManagement -match 'runManagementHeader') 'run management page should use the run-management header.'
Assert-True ($showRunManagement -notmatch 'kiteProcessSummaryBlock') 'run management page must not render the old three-count summary card.'
Assert-True ($showRunManagement -match 'buildRunManagementGroups') 'run management page should render grouped card runtime rows.'

$runManagementGroups = Function-Body $main 'buildRunManagementGroups'
Assert-True ($runManagementGroups -match 'CardRunStore\.runs\.value') 'run management groups must reuse CardRunStore.'
Assert-True ($runManagementGroups -match 'TerminalSessionStore\.snapshot\.value\.sessions') 'run management groups must reuse TerminalSessionStore.'
Assert-True ($runManagementGroups -match 'TaskManagerStore\.snapshot\.value\.processes') 'run management groups must reuse TaskManagerStore.'

$runManagementCard = Function-Body $main 'runManagementCard'
Assert-True ($runManagementCard -match 'setOnClickListener \{ toggleRunManagementCard') 'run management row card should expand from the whole card click.'
Assert-True ($runManagementCard -match 'LinearLayout\.LayoutParams\(dp\(34\), dp\(34\)\)') 'run management card icon should stay compact like install wizard rows.'
Assert-True ($runManagementCard -notmatch '停止卡片|runManagementActionButton|runManagementSummary|processCount') 'run management card should not expose heavy counts or action buttons in the collapsed row.'

$runManagementDetails = Function-Body $main 'runManagementDetails'
Assert-True ($runManagementDetails -match 'runManagementSurfaceItems') 'expanded run management card should derive SH/terminal/web rows from existing card-run surfaces.'
Assert-True ($runManagementDetails -match 'CardRunSurface\.Report') 'expanded run management card should surface SH report when available.'
Assert-True ($runManagementDetails -match 'CardRunSurface\.Terminal') 'expanded run management card should surface terminal rows when available.'
Assert-True ($runManagementDetails -match 'CardRunSurface\.Web') 'expanded run management card should surface web rows when available.'

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
