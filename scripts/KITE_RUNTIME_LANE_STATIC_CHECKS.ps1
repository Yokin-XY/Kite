param(
    [string]$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
)

$ErrorActionPreference = 'Stop'

$mainPath = Join-Path $Root 'app/src/main/java/com/kite/app/MainActivity.kt'
$storePath = Join-Path $Root 'app/src/main/java/com/kite/app/resources/KiteResourceInstallStore.kt'
$bridgeClientPath = Join-Path $Root 'app/src/main/java/com/kite/app/bridge/KiteBridgeClient.kt'
$browserProxyPath = Join-Path $Root 'app/src/main/java/com/kite/app/bridge/KiteBrowserProxy.kt'
$localServerPath = Join-Path $Root 'app/src/main/java/com/kite/app/bridge/KiteLocalServer.kt'
$cardRunModelsPath = Join-Path $Root 'app/src/main/java/com/kite/app/run/CardRunModels.kt'
$cardRunStorePath = Join-Path $Root 'app/src/main/java/com/kite/app/run/CardRunStore.kt'
$prootTelemetryStorePath = Join-Path $Root 'app/src/main/kotlin/com/kftest/app/foundation/runtime/ProotTelemetryStore.kt'
$prootOwnerTerminatorPath = Join-Path $Root 'app/src/main/kotlin/com/kftest/app/foundation/runtime/ProotOwnerProcessTerminator.kt'
$runtimeHealthStorePath = Join-Path $Root 'app/src/main/kotlin/com/kftest/app/foundation/runtime/RuntimeHealthStore.kt'
$runtimeWorkloadRegistryPath = Join-Path $Root 'app/src/main/kotlin/com/kftest/app/foundation/runtime/RuntimeWorkloadRegistry.kt'
$taskManagerStorePath = Join-Path $Root 'app/src/main/kotlin/com/kftest/app/foundation/runtime/TaskManagerStore.kt'
$taskManagerFragmentPath = Join-Path $Root 'app/src/main/kotlin/com/kftest/app/ui/tasks/TaskManagerFragment.kt'
$prootPoolPlanPath = Join-Path $Root 'app/src/main/kotlin/com/kftest/app/foundation/runtime/RuntimeProotPoolPlanDryRun.kt'
$terminalSessionControllerPath = Join-Path $Root 'app/src/main/kotlin/com/kftest/app/foundation/terminal/TerminalSessionController.kt'

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

function Read-Utf8 {
    param([string]$Path)
    return [System.IO.File]::ReadAllText($Path, [System.Text.Encoding]::UTF8)
}

function Function-Body {
    param(
        [string]$Source,
        [string]$Name
    )
    $pattern = "(?s)private fun $([regex]::Escape($Name))\b.*?(?=\n    private fun |\n    private data class |\n    private enum class |\n    companion object|\n    override fun |\z)"
    return [regex]::Match($Source, $pattern).Value
}

$main = Read-Utf8 $mainPath
$store = Read-Utf8 $storePath
$bridgeClient = Read-Utf8 $bridgeClientPath
$browserProxy = Read-Utf8 $browserProxyPath
$localServer = Read-Utf8 $localServerPath
$cardRunModels = Read-Utf8 $cardRunModelsPath
$cardRunStore = Read-Utf8 $cardRunStorePath
$prootTelemetryStore = Read-Utf8 $prootTelemetryStorePath
$prootOwnerTerminator = Read-Utf8 $prootOwnerTerminatorPath
$runtimeHealthStore = Read-Utf8 $runtimeHealthStorePath
$runtimeWorkloadRegistry = Read-Utf8 $runtimeWorkloadRegistryPath
$taskManagerStore = Read-Utf8 $taskManagerStorePath
$taskManagerFragment = Read-Utf8 $taskManagerFragmentPath
$prootPoolPlan = Read-Utf8 $prootPoolPlanPath
$terminalSessionController = Read-Utf8 $terminalSessionControllerPath

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

Assert-True ($cardRunStore -match '(?s)fun initialize\b.*?shouldDropCurrentAfterProcessRestore') 'CardRunStore must not restore stale current runs after process restart.'
Assert-True ($cardRunModels -match 'val cardInstanceId: String get\(\) = instanceId') 'CardRunState must expose cardInstanceId as the card ownership alias.'
Assert-True ($cardRunStore -match '\.put\("cardInstanceId", cardInstanceId\)') 'CardRunStore must persist cardInstanceId beside instanceId.'
Assert-True ($cardRunStore -match 'optString\("cardInstanceId"\)') 'CardRunStore must restore cardInstanceId for ownership-compatible snapshots.'
Assert-True ($browserProxy -match 'KITE_CARD_INSTANCE_ID') 'Browser proxy environment must export KITE_CARD_INSTANCE_ID.'
Assert-True ($browserProxy -match 'cardInstanceId=') 'Browser proxy requests must carry cardInstanceId.'
Assert-True ($localServer -match 'cardInstanceId') 'Local server must accept cardInstanceId on open-web requests.'
Assert-True ($bridgeClient -match 'KITE_CARD_INSTANCE_ID') 'direct shell launch must resolve the cardInstanceId from the run environment.'
Assert-True ($bridgeClient -match 'private fun directRuntimeEnv') 'direct shell launch must build one owner-aware runtime env per run.'
Assert-True ($bridgeClient -match '"KF_RUNTIME_ID" to ownerId') 'direct shell launch must inject KF_RUNTIME_ID for PRoot ownership telemetry.'
Assert-True ($bridgeClient -match '"KF_UNIT_ID" to unitId') 'direct shell launch must inject KF_UNIT_ID for PRoot ownership telemetry.'
Assert-True ($bridgeClient -match 'runtimeOwnerId\(recipe, cardInstanceId\)') 'direct shell launch must derive owner id from recipe and card instance.'
Assert-True ($bridgeClient -match 'KiteResourceInstallRecipes\.RUNTIME_SOURCE') 'resource shell launches must get resource ownership ids.'
Assert-True ($bridgeClient -match 'val runEnv = directRuntimeEnv\(recipe, runId, extraEnv\)') 'direct recipe execution must compute identity env once per run.'
Assert-True ($bridgeClient -match 'executeDirectShellStep\(context, recipe, runId, requestId, step, runEnv, onProgress\)') 'all direct shell steps must receive the owner-aware runtime env.'
Assert-True ($main -match 'private fun Map<String, String>\.withTerminalOwner') 'terminal card runs must have one helper for PRoot owner env injection.'
Assert-True ($main.Contains('"KF_RUNTIME_ID" to "terminal:$terminalId"') -and $main.Contains('"KF_UNIT_ID" to "card:$unitId"')) 'terminal launch env must inject terminal owner id and card unit id.'
$blankTerminal = Function-Body $main 'openCardRunBlankTerminal'
$terminalStep = Function-Body $main 'executeTerminalRecipeStep'
Assert-True ($blankTerminal -match 'withTerminalOwner\(record\.id, instanceId\)') 'blank card terminals must launch with terminal owner env.'
Assert-True ($terminalStep -match 'withTerminalOwner\(record\.id, instanceId\)') 'terminal recipe steps must launch with terminal owner env.'
Assert-True ($prootTelemetryStore -match 'data class ProotOwnerProcessGroup') 'PRoot telemetry must expose owner process groups.'
Assert-True ($prootTelemetryStore -match 'data class ProotOwnerProcessIndex') 'PRoot telemetry must expose an owner process index.'
Assert-True ($prootTelemetryStore -match 'val ownerProcessIndex: ProotOwnerProcessIndex') 'PRoot telemetry snapshots must carry the owner process index.'
Assert-True ($prootTelemetryStore -match 'private fun buildOwnerProcessIndex') 'PRoot telemetry must build the owner index from the live process table.'
Assert-True ($prootTelemetryStore -match 'filter \{ it\.state == ProotLiveProcessState\.RUNNING && it\.kfRuntimeId\.isNotBlank\(\) \}') 'owner process index must be derived from live tracees with KF owner ids.'
Assert-True ($prootTelemetryStore -match 'groupBy \{ it\.kfRuntimeId \}') 'owner process index must group tracees by KF runtime owner id.'
Assert-True ($prootTelemetryStore -match 'kfRuntimeId = event\.kfRuntimeId\.ifBlank \{ existing\?\.kfRuntimeId \?: parent\?\.kfRuntimeId\.orEmpty\(\) \}') 'fork/clone/vfork events must inherit KF owner id from existing or parent tracees.'
Assert-True ($prootTelemetryStore -match 'kfUnitId = event\.kfUnitId\.ifBlank \{ existing\?\.kfUnitId \?: parent\?\.kfUnitId\.orEmpty\(\) \}') 'fork/clone/vfork events must inherit KF unit id from existing or parent tracees.'
Assert-True ($prootTelemetryStore -match 'fun refreshBlocking') 'PRoot telemetry must expose a blocking refresh for stop residue checks.'
Assert-True ($runtimeHealthStore -match 'CARD\("\u5361\u7247"\)' -and $runtimeHealthStore -match 'RESOURCE\("\u8d44\u6e90"\)') 'RuntimeHealth must model card/resource owner roots explicitly.'
Assert-True ($runtimeHealthStore -match 'private fun buildProotOwnerRoots') 'RuntimeHealth must build owner roots from PRoot owner index.'
Assert-True ($runtimeHealthStore -match 'prootTelemetry\.ownerProcessIndex\.groups') 'RuntimeHealth owner roots must consume the owner process index.'
Assert-True ($runtimeHealthStore -match 'existingTerminalOwnerIds' -and $runtimeHealthStore -match 'excludedOwnerIds: Set<String> = emptySet\(\)') 'RuntimeHealth must skip PRoot terminal owner roots already represented by terminal sessions.'
Assert-True ($runtimeHealthStore -match 'attributedRootPids = attributedPids \+ ownerTraceePids') 'owner tracees must be excluded from unattributed root generation.'
Assert-True ($runtimeWorkloadRegistry -match 'proot_owner_index:\$\{ownerKind\.name\.lowercase\(\)\}') 'workload registry must classify card/resource owner roots from the PRoot owner index.'
Assert-True ($taskManagerStore -match '\u5361\u7247\u5bb9\u5668' -and $taskManagerStore -match '\u8d44\u6e90\u5bb9\u5668') 'task manager must surface card/resource roots as owner containers.'
Assert-True ($taskManagerStore -match 'val runtimeOwnerId: String\? = null') 'task manager process items must carry the raw runtime owner id.'
Assert-True ($taskManagerStore -match 'runtimeOwnerId = ownerId') 'task manager root items must expose the RuntimeHealth owner id.'
Assert-True ($taskManagerFragment -match 'item\.runtimeOwnerId\.orEmpty\(\)') 'task manager render signatures must include runtime owner id.'
$runManagementGroups = Function-Body $main 'buildRunManagementGroups'
Assert-True ($runManagementGroups -match 'runtimeOwnerIdForRunManagement\(\)' -and $runManagementGroups -match 'belongsToRun\(run, boundPids, ownerId\)') 'run management groups must match CardRun rows by owner id, not only pid bindings.'
$belongsToRun = Function-Body $main 'TaskManagerProcessItem.belongsToRun'
Assert-True ($belongsToRun -match 'runtimeOwnerId == ownerId') 'CardRun process grouping must consume the TaskManager owner fact source.'
Assert-True ($main -match 'private fun RecipeRuntimeState\.runtimeOwnerIdForRunManagement') 'CardRun run management must derive the expected PRoot owner id.'
Assert-True ($prootPoolPlan -match 'val ownerContainerCount: Int') 'PRoot pool plan must expose owner container count.'
Assert-True ($prootPoolPlan -match 'val ownerContainerTraceeCount: Int') 'PRoot pool plan must expose owner tracee count.'
Assert-True ($prootPoolPlan -match 'entry\.ownerKind == RuntimeRootOwnerKind\.CARD' -and $prootPoolPlan -match 'entry\.ownerKind == RuntimeRootOwnerKind\.RESOURCE') 'PRoot pool plan must derive owner container pressure from card/resource owner roots.'
Assert-True ($prootPoolPlan -match 'proot_pool_plan_owner_container_count' -and $prootPoolPlan -match 'proot_pool_plan_owner_container_tracee_count') 'PRoot pool env output must include owner container pressure.'
Assert-True ($prootOwnerTerminator -match 'object ProotOwnerProcessTerminator') 'owner stop must have a dedicated PRoot owner terminator.'
Assert-True ($prootOwnerTerminator -match 'ProotTelemetryStore\.refreshBlocking') 'owner stop must use blocking telemetry refresh for residue checks.'
Assert-True ($prootOwnerTerminator -match 'KFJni\.sendSignal\(target, signal\)') 'owner stop must signal PRoot tracee process groups/pids directly.'
Assert-True ($prootOwnerTerminator -match '__kite_owner_stop_owner' -and $prootOwnerTerminator -match '__kite_stop_remaining') 'owner stop must report owner and final remaining tracees.'
Assert-True ($bridgeClient -match 'ProotOwnerProcessTerminator\.terminate') 'bridge stop must invoke the PRoot owner terminator.'
Assert-True ($bridgeClient -match 'private fun stopOwnerProcesses') 'bridge stop must collect owner stop output centrally.'
Assert-True ($terminalSessionController -match 'private fun stopTerminalOwnerProcesses') 'terminal stop must have a dedicated owner stop hook.'
Assert-True ($terminalSessionController -match 'ProotOwnerProcessTerminator\.terminate\(appContext, ownerId\)') 'terminal stop must invoke the PRoot owner terminator.'
Assert-True ($terminalSessionController.Contains('"terminal:$it"')) 'terminal stop must derive the owner id from the terminal session id.'
Assert-True ($bridgeClient -match 'lastOrNull\(\).*substringAfter' -or $bridgeClient -match '(?s)filter \{ it\.startsWith\("__kite_stop_remaining:"\) \}.*lastOrNull\(\)') 'bridge residue parsing must use the final stop remaining marker.'
Assert-True ($main -match '(?s)private fun stopRemainingProcesses\(result: BridgeResult\).*lastOrNull\(\)') 'UI stop residue parsing must use the final stop remaining marker.'
Assert-True ($bridgeClient -match 'cardRunPidFilePath') 'detached shell launch must create a pidfile through the pidfile helper.'
Assert-True ($bridgeClient.Contains('card-runs/${safeId(cardInstanceId)}')) 'detached shell pidfiles must be grouped by cardInstanceId.'
Assert-True ($bridgeClient.Contains('${safeId(runId)}.pid')) 'detached shell pidfiles must be keyed by runId.'
Assert-True ($bridgeClient -match '__kite_pid_file') 'detached shell launch must report the pidfile path in launch output.'
Assert-True ($bridgeClient -notmatch '/run/kite/cards') 'phase one must not introduce the phase two /run/kite/cards directory scheme.'
Assert-True ($bridgeClient -match 'private fun cleanCardRunPidFile') 'stop success must have a minimal pidfile cleanup helper.'
Assert-True ($bridgeClient -match '__kite_pid_file_cleaned') 'pidfile cleanup must report the cleaned pidfile path.'
Assert-True ($bridgeClient -match 'rm -f --') 'pidfile cleanup must delete only the resolved pidfile path.'
Assert-True ($bridgeClient -match 'pidFilePath = execution\.pidFilePath') 'direct run bindings must retain the launch pidfile path.'
Assert-True ($bridgeClient.Contains('if (stoppedOk)') -and $bridgeClient.Contains('cleanCardRunPidFile(context, binding.pidFilePath)')) 'pidfile cleanup must run only after process stop succeeds.'
Assert-True ($main -match 'private fun shouldStopRunBeforeDelete') 'delete recipe flow must have an active-run stop guard.'
$deleteConfirm = Function-Body $main 'showDeleteRecipeConfirmSheet'
Assert-True ($deleteConfirm -match 'shouldStopRunBeforeDelete' -and $deleteConfirm -match 'stopRecipeByCardInstanceId\(recipe, activeDeleteState\.cardInstanceId, activeDeleteState\)') 'delete recipe flow must stop an active card run before deleting the recipe.'
Assert-True ($deleteConfirm -match 'return@setOnClickListener') 'delete recipe flow must not delete active recipes in the same click after requesting stop.'
Assert-True ($cardRunStore -match 'fun removeClosedRunStatesForRecipes') 'card deletion cleanup must use a closed-run-only CardRunStore entry.'
Assert-True ($cardRunStore -match 'activeInstanceIds' -and $cardRunStore -match 'status\.endsHistoryEntry\(\)' -and $cardRunStore -match 'entry\.isClosed\(\)') 'closed-run cleanup must preserve active instances while removing ended run state/history.'
Assert-True ($main -match 'removeClosedRunStatesForRecipes\(listOf\(recipe\.id\)\)') 'delete recipe flow must clean only closed CardRun state for the deleted card.'
Assert-True ($main -match 'cleanCardRunPidDirs\(removedCardInstanceIds\)') 'delete recipe flow must request pid directory cleanup for removed cardInstanceIds.'
Assert-True ($bridgeClient -match 'fun cleanCardRunPidDirs') 'bridge client must expose card pid directory cleanup.'
Assert-True ($bridgeClient -match 'private fun cleanCardRunPidDirPayload') 'pid directory cleanup must derive its payload from a cardInstanceId.'
Assert-True ($bridgeClient.Contains('rm -rf -- ${shellQuote(dir)}')) 'pid directory cleanup must delete only the derived card-run pid directory.'
Assert-True ($bridgeClient -match '__kite_pid_dir_cleaned') 'pid directory cleanup must report the cleaned directory path.'
Assert-True ($cardRunStore -match 'PROCESS_RESTORE_ABORTED_MESSAGE') 'process-restore abnormal exits must use one explicit CardRunStore message.'
Assert-True ($cardRunStore -match 'status = CardRunStatus\.Failed' -and $cardRunStore -match 'lastError = lastError \?: PROCESS_RESTORE_ABORTED_MESSAGE') 'process-restore unfinished runs must be classified as abnormal, not normal stopped.'
Assert-True ($cardRunStore -match 'normalizedHistoryAfterProcessRestore' -and $cardRunStore -match 'error = error\.ifBlank \{ PROCESS_RESTORE_ABORTED_MESSAGE \}') 'process-restore history must preserve an abnormal-exit error.'

$stopRecipe = Function-Body $main 'stopRecipe'
$stopRecipeByCardInstanceId = Function-Body $main 'stopRecipeByCardInstanceId'
$handleStopResultV2 = Function-Body $main 'handleStopResultV2'
Assert-True ($stopRecipe -match 'stopRecipeByCardInstanceId\(recipe, previousState\.cardInstanceId, previousState\)') 'stopRecipe must delegate to the cardInstanceId stop entry.'
Assert-True ($stopRecipeByCardInstanceId -match 'CardRunStore\.get\(cardInstanceId\)') 'stop(cardInstanceId) must resolve the latest CardRunStore state.'
Assert-True ($stopRecipeByCardInstanceId -match 'activeRunInstanceIds\[recipe\.id\] = previousState\.instanceId') 'stop(cardInstanceId) must bind runtime writes to the resolved card instance.'
Assert-True ($main -match 'private fun RecipeRuntimeState\.hasProcessBindingForStop\(\)') 'stop flow must distinguish terminal-only state from process bindings.'
Assert-True ($stopRecipeByCardInstanceId -match 'hasProcessBindingForStop\(\)') 'terminal stop must check for retained process bindings.'
Assert-True ($stopRecipeByCardInstanceId -match 'stop_terminal_and_process_request_sent') 'terminal plus process stop must record the combined stop request.'
Assert-True ($stopRecipeByCardInstanceId -match 'bridgeClient\.stopProcessBinding') 'terminal plus process stop must ask the bridge to stop the retained process binding.'
Assert-True ($handleStopResultV2 -match 'instanceId = previousState\.instanceId') 'stop result handling must write back to the stopped card instance.'
Assert-True ($handleStopResultV2 -match 'stopRemaining\.isEmpty\(\).*KiteRunReport\.STATUS_STOPPED') 'stop success must require an empty process-residue check before Stopped.'
Assert-True ($handleStopResultV2 -match '\u5df2\u505c\u6b62\uff0c\u672a\u53d1\u73b0\u8fdb\u7a0b\u6b8b\u7559') 'stop success must write an explicit no-residue result.'
Assert-True ($handleStopResultV2 -match '\u505c\u6b62\u540e\u4ecd\u6709\u8fdb\u7a0b\u6b8b\u7559') 'stop failure must keep residue visible in CardRunStore.'
Assert-True ($main -match 'private fun stopRemainingProcesses\(result: BridgeResult\)') 'stop result handling must parse remaining process observations.'
Assert-True ($main -match '__kite_stop_remaining:') 'stop residue parsing must use the bridge remaining marker.'
Assert-True ($main -match 'cardInstanceId = previousState\.cardInstanceId') 'card stop calls must pass cardInstanceId to the bridge for pidfile cleanup.'

$cardRunRestoreDrop = Function-Body $cardRunStore 'CardRunState.shouldDropCurrentAfterProcessRestore'
Assert-True ($cardRunRestoreDrop -match 'status\.endsHistoryEntry') 'restored ended card-run states should stay out of the current run list.'
Assert-True ($cardRunRestoreDrop -match 'hasRunBinding') 'restored run bindings should stay out of the current run list.'

$runManagementCard = Function-Body $main 'runManagementCard'
Assert-True ($runManagementCard -match 'setOnClickListener \{ toggleRunManagementCard') 'run management row card should expand from the whole card click.'
Assert-True ($runManagementCard -match 'LinearLayout\.LayoutParams\(dp\(34\), dp\(34\)\)') 'run management card icon should stay compact like install wizard rows.'
Assert-True ($runManagementCard -notmatch '\u505c\u6b62\u5361\u7247|runManagementActionButton|runManagementSummary|processCount') 'run management card should not expose heavy counts or action buttons in the collapsed row.'

$runManagementDetails = Function-Body $main 'runManagementDetails'
Assert-True ($runManagementDetails -match 'runManagementOwnershipRows') 'expanded run management card must show CardRun ownership rows.'
Assert-True ($runManagementDetails -match 'runManagementSurfaceItems') 'expanded run management card should derive SH/terminal/web rows from existing card-run surfaces.'
Assert-True ($runManagementDetails -match 'CardRunSurface\.Report') 'expanded run management card should surface SH report when available.'
Assert-True ($runManagementDetails -match 'CardRunSurface\.Terminal') 'expanded run management card should surface terminal rows when available.'
Assert-True ($runManagementDetails -match 'CardRunSurface\.Web') 'expanded run management card should surface web rows when available.'

$runManagementOwnershipRows = Function-Body $main 'runManagementOwnershipRows'
Assert-True ($runManagementOwnershipRows -match 'cardInstanceId') 'run management ownership rows must expose cardInstanceId.'
Assert-True ($runManagementOwnershipRows -match 'runId' -and $runManagementOwnershipRows -match 'rootPid' -and $runManagementOwnershipRows -match 'pgid') 'run management ownership rows must expose runId/rootPid/pgid.'
Assert-True ($runManagementOwnershipRows -match 'terminal' -and $runManagementOwnershipRows -match 'web') 'run management ownership rows must expose terminal/web bindings.'
Assert-True ($runManagementOwnershipRows -match '\u9000\u51fa\u5224\u65ad' -and $runManagementOwnershipRows -match 'runManagementExitSummary\(run\)') 'run management ownership rows must expose the exit classification.'

$runManagementExitSummary = Function-Body $main 'runManagementExitSummary'
Assert-True ($runManagementExitSummary -match '\u505c\u6b62\u540e\u4ecd\u6709\u8fdb\u7a0b\u6b8b\u7559' -and $runManagementExitSummary -match '\u6b8b\u7559') 'run management exit summary must identify stop residue.'
Assert-True ($runManagementExitSummary -match 'RecipeRunStatus\.Stopped' -and $runManagementExitSummary -match '\u6b63\u5e38\u505c\u6b62') 'run management exit summary must identify normal stopped runs.'
Assert-True ($runManagementExitSummary -match 'RecipeRunStatus\.Failed' -and $runManagementExitSummary -match '\u5d29\u6e83/\u5f02\u5e38') 'run management exit summary must identify failed runs as abnormal exits.'
Assert-True ($runManagementExitSummary -match '\u672a\u7ed3\u675f') 'run management exit summary must identify still-open runs.'

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
