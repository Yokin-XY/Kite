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
$prootTelemetryStorePath = Join-Path $Root 'app/src/main/kotlin/com/kite/app/foundation/runtime/ProotTelemetryStore.kt'
$prootOwnerTerminatorPath = Join-Path $Root 'app/src/main/kotlin/com/kite/app/foundation/runtime/ProotOwnerProcessTerminator.kt'
$runtimeHealthStorePath = Join-Path $Root 'app/src/main/kotlin/com/kite/app/foundation/runtime/RuntimeHealthStore.kt'
$runtimeReclaimerPath = Join-Path $Root 'app/src/main/kotlin/com/kite/app/foundation/runtime/RuntimeReclaimer.kt'
$runtimeWorkloadRegistryPath = Join-Path $Root 'app/src/main/kotlin/com/kite/app/foundation/runtime/RuntimeWorkloadRegistry.kt'
$runtimeAutomationActionsPath = Join-Path $Root 'app/src/main/kotlin/com/kite/app/foundation/runtime/RuntimeAutomationActions.kt'
$runtimeMemoryLifecycleRuleTriggerPath = Join-Path $Root 'app/src/main/kotlin/com/kite/app/foundation/runtime/RuntimeMemoryLifecycleRuleTrigger.kt'
$runtimePressureResponderPath = Join-Path $Root 'app/src/main/kotlin/com/kite/app/foundation/runtime/RuntimePressureResponder.kt'
$backgroundRuntimeRegistryPath = Join-Path $Root 'app/src/main/kotlin/com/kite/app/foundation/service/BackgroundRuntimeRegistry.kt'
$taskManagerStorePath = Join-Path $Root 'app/src/main/kotlin/com/kite/app/foundation/runtime/TaskManagerStore.kt'
$containerProcessStorePath = Join-Path $Root 'app/src/main/kotlin/com/kite/app/foundation/runtime/ContainerProcessStore.kt'
$prootPoolPlanPath = Join-Path $Root 'app/src/main/kotlin/com/kite/app/foundation/runtime/RuntimeProotPoolPlanDryRun.kt'
$terminalSessionControllerPath = Join-Path $Root 'app/src/main/kotlin/com/kite/app/foundation/terminal/TerminalSessionController.kt'
$terminalFragmentPath = Join-Path $Root 'app/src/main/kotlin/com/kite/app/ui/terminal/TerminalFragment.kt'
$terminalPanelActionRegistryPath = Join-Path $Root 'app/src/main/kotlin/com/kite/app/ui/terminal/TerminalPanelActionRegistry.kt'
$startupTraceStorePath = Join-Path $Root 'app/src/main/kotlin/com/kite/app/foundation/bootstrap/StartupTraceStore.kt'
$toolchainPackInstallerPath = Join-Path $Root 'app/src/main/kotlin/com/kite/app/foundation/toolchain/ToolchainPackInstaller.kt'
$resourceInstallRecipesPath = Join-Path $Root 'app/src/main/java/com/kite/app/resources/KiteResourceInstallRecipes.kt'
$appNavigatorPath = Join-Path $Root 'app/src/main/java/com/kite/app/shell/AppNavigator.kt'
$appIntentRouterPath = Join-Path $Root 'app/src/main/java/com/kite/app/shell/AppIntentRouter.kt'
$kiteAppGraphPath = Join-Path $Root 'app/src/main/java/com/kite/app/shell/KiteAppGraph.kt'
$resourceFeatureContractPath = Join-Path $Root 'app/src/main/java/com/kite/app/feature/resources/ResourceFeatureContract.kt'
$resourceFeatureControllerPath = Join-Path $Root 'app/src/main/java/com/kite/app/feature/resources/ResourceFeatureController.kt'
$resourceFeatureFragmentPath = Join-Path $Root 'app/src/main/java/com/kite/app/feature/resources/ResourceFeatureFragment.kt'
$resourceManageFragmentPath = Join-Path $Root 'app/src/main/java/com/kite/app/feature/resources/ResourceManageFragment.kt'
$resourceManageScreenPath = Join-Path $Root 'app/src/main/java/com/kite/app/feature/resources/ResourceManageScreen.kt'
$resourceInstallWizardPresentationPath = Join-Path $Root 'app/src/main/java/com/kite/app/feature/resources/ResourceInstallWizardPresentation.kt'
$resourceInstallWizardScreenPath = Join-Path $Root 'app/src/main/java/com/kite/app/feature/resources/ResourceInstallWizardScreen.kt'
$recipeFeatureGatewayPath = Join-Path $Root 'app/src/main/java/com/kite/app/application/recipes/RecipeFeatureGateway.kt'
$homeFeatureContractPath = Join-Path $Root 'app/src/main/java/com/kite/app/feature/home/HomeFeatureContract.kt'
$homeFeatureControllerPath = Join-Path $Root 'app/src/main/java/com/kite/app/feature/home/HomeFeatureController.kt'
$homeFeatureFragmentPath = Join-Path $Root 'app/src/main/java/com/kite/app/feature/home/HomeFragment.kt'
$homeScreenPath = Join-Path $Root 'app/src/main/java/com/kite/app/feature/home/HomeScreen.kt'
$homeFeatureViewSupportPath = Join-Path $Root 'app/src/main/java/com/kite/app/feature/home/HomeFeatureViewSupport.kt'
$recipeEditorContractPath = Join-Path $Root 'app/src/main/java/com/kite/app/feature/recipeeditor/RecipeEditorContract.kt'
$recipeEditorControllerPath = Join-Path $Root 'app/src/main/java/com/kite/app/feature/recipeeditor/RecipeEditorController.kt'
$recipeEditorFragmentPath = Join-Path $Root 'app/src/main/java/com/kite/app/feature/recipeeditor/RecipeEditorFragment.kt'
$recipeEditorScreenPath = Join-Path $Root 'app/src/main/java/com/kite/app/feature/recipeeditor/RecipeEditorScreen.kt'
$androidRecipeFeatureGatewayPath = Join-Path $Root 'app/src/main/java/com/kite/app/platform/recipes/AndroidRecipeFeatureGateway.kt'
$runExecutionContractPath = Join-Path $Root 'app/src/main/java/com/kite/app/application/runs/RunExecutionContract.kt'
$runOrchestratorPath = Join-Path $Root 'app/src/main/java/com/kite/app/application/runs/RunOrchestrator.kt'
$runExecutionEffectBusPath = Join-Path $Root 'app/src/main/java/com/kite/app/application/runs/RunExecutionEffectBus.kt'
$runLifecycleEventHubPath = Join-Path $Root 'app/src/main/java/com/kite/app/application/runs/RunLifecycleEventHub.kt'
$stopCoordinatorPath = Join-Path $Root 'app/src/main/java/com/kite/app/application/runs/StopCoordinator.kt'
$androidRecipeExecutorPath = Join-Path $Root 'app/src/main/java/com/kite/app/platform/runs/AndroidRecipeExecutor.kt'
$androidRunStateGatewayPath = Join-Path $Root 'app/src/main/java/com/kite/app/platform/runs/AndroidRunStateGateway.kt'
$resourceRunCoordinatorPath = Join-Path $Root 'app/src/main/java/com/kite/app/application/resources/ResourceRunCoordinator.kt'
$androidResourceRecipeFactoryPath = Join-Path $Root 'app/src/main/java/com/kite/app/platform/resources/AndroidResourceRecipeFactory.kt'
$androidResourceRunGatewayPath = Join-Path $Root 'app/src/main/java/com/kite/app/platform/resources/AndroidResourceRunGateway.kt'
$runSurfaceContractPath = Join-Path $Root 'app/src/main/java/com/kite/app/feature/runsurface/RunSurfaceContract.kt'
$runSurfaceControllerPath = Join-Path $Root 'app/src/main/java/com/kite/app/feature/runsurface/RunSurfaceController.kt'
$runSurfaceHostPath = Join-Path $Root 'app/src/main/java/com/kite/app/feature/runsurface/RunSurfaceHost.kt'
$runReportScreenPath = Join-Path $Root 'app/src/main/java/com/kite/app/feature/runsurface/RunReportScreen.kt'
$runTerminalSurfaceBindingPath = Join-Path $Root 'app/src/main/java/com/kite/app/feature/runsurface/RunTerminalSurfaceBinding.kt'
# T11 拆分后的 model 文件(Store 检查需合并 Models)
$prootTelemetryModelsPath = Join-Path $Root 'app/src/main/kotlin/com/kite/app/foundation/runtime/ProotTelemetryModels.kt'
$runtimeHealthModelsPath = Join-Path $Root 'app/src/main/kotlin/com/kite/app/foundation/runtime/RuntimeHealthModels.kt'

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
    $pattern = "(?s)private (?:suspend )?fun $([regex]::Escape($Name))\b.*?(?=\n    private (?:suspend )?fun |\n    private data class |\n    private enum class |\n    companion object|\n    override fun |\z)"
    return [regex]::Match($Source, $pattern).Value
}

function Member-Function-Body {
    param(
        [string]$Source,
        [string]$Name
    )
    $visibility = '(?:(?:private|internal|protected|public)\s+)?'
    $override = '(?:override\s+)?'
    $memberPrefix = '^    ' + $visibility + $override + 'fun\s+'
    $pattern = '(?ms)' +
        $memberPrefix + [regex]::Escape($Name) + '\b.*?' +
        '(?=' + $memberPrefix + '|^    (?:private\s+)?(?:data class|enum class|companion object)\b|\z)'
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
# T11 拆分后 model 定义移到 ProotTelemetryModels.kt。用 Get-Content -Raw 读取
# (该文件经 sed 提取,ReadAllText 对其返回空,Get-Content -Raw 可靠)。
$prootTelemetryModelsPath = Join-Path $Root 'app/src/main/kotlin/com/kite/app/foundation/runtime/ProotTelemetryModels.kt'
$prootTelemetryModels = Get-Content -Path $prootTelemetryModelsPath -Raw -Encoding UTF8
# 在读取点立即合并(避免后续作用域诡异行为)
$prootTelemetryAll = $prootTelemetryStore + "`n" + $prootTelemetryModels
$prootOwnerTerminator = Read-Utf8 $prootOwnerTerminatorPath
$runtimeHealthStore = Read-Utf8 $runtimeHealthStorePath
# T11 拆分后 model 定义移到 RuntimeHealthModels.kt,合并读取
$runtimeHealthModels = Read-Utf8 $runtimeHealthModelsPath
$runtimeHealthStore = $runtimeHealthStore + "`n" + $runtimeHealthModels
$runtimeReclaimer = Read-Utf8 $runtimeReclaimerPath
$runtimeWorkloadRegistry = Read-Utf8 $runtimeWorkloadRegistryPath
$runtimeAutomationActions = Read-Utf8 $runtimeAutomationActionsPath
$runtimeMemoryLifecycleRuleTrigger = Read-Utf8 $runtimeMemoryLifecycleRuleTriggerPath
$runtimePressureResponder = Read-Utf8 $runtimePressureResponderPath
$backgroundRuntimeRegistry = Read-Utf8 $backgroundRuntimeRegistryPath
$taskManagerStore = Read-Utf8 $taskManagerStorePath
$containerProcessStore = Read-Utf8 $containerProcessStorePath
$prootPoolPlan = Read-Utf8 $prootPoolPlanPath
$terminalSessionController = Read-Utf8 $terminalSessionControllerPath
$terminalFragment = Read-Utf8 $terminalFragmentPath
$terminalPanelActionRegistry = Read-Utf8 $terminalPanelActionRegistryPath
$startupTraceStore = Read-Utf8 $startupTraceStorePath
$toolchainPackInstaller = Read-Utf8 $toolchainPackInstallerPath
$resourceInstallRecipes = Read-Utf8 $resourceInstallRecipesPath
$appNavigator = Read-Utf8 $appNavigatorPath
$appIntentRouter = Read-Utf8 $appIntentRouterPath
$kiteAppGraph = Read-Utf8 $kiteAppGraphPath
$resourceFeatureContract = Read-Utf8 $resourceFeatureContractPath
$resourceFeatureController = Read-Utf8 $resourceFeatureControllerPath
$resourceFeatureFragment = Read-Utf8 $resourceFeatureFragmentPath
$resourceManageFragment = Read-Utf8 $resourceManageFragmentPath
$resourceManageScreen = Read-Utf8 $resourceManageScreenPath
$resourceInstallWizardPresentation = Read-Utf8 $resourceInstallWizardPresentationPath
$resourceInstallWizardScreen = Read-Utf8 $resourceInstallWizardScreenPath
$recipeFeatureGateway = Read-Utf8 $recipeFeatureGatewayPath
$homeFeatureContract = Read-Utf8 $homeFeatureContractPath
$homeFeatureController = Read-Utf8 $homeFeatureControllerPath
$homeFeatureFragment = Read-Utf8 $homeFeatureFragmentPath
$homeScreen = Read-Utf8 $homeScreenPath
$homeFeatureViewSupport = Read-Utf8 $homeFeatureViewSupportPath
$recipeEditorContract = Read-Utf8 $recipeEditorContractPath
$recipeEditorController = Read-Utf8 $recipeEditorControllerPath
$recipeEditorFragment = Read-Utf8 $recipeEditorFragmentPath
$recipeEditorScreen = Read-Utf8 $recipeEditorScreenPath
$androidRecipeFeatureGateway = Read-Utf8 $androidRecipeFeatureGatewayPath
$runExecutionContract = Read-Utf8 $runExecutionContractPath
$runOrchestrator = Read-Utf8 $runOrchestratorPath
$runExecutionEffectBus = Read-Utf8 $runExecutionEffectBusPath
$runLifecycleEventHub = Read-Utf8 $runLifecycleEventHubPath
$stopCoordinator = Read-Utf8 $stopCoordinatorPath
$androidRecipeExecutor = Read-Utf8 $androidRecipeExecutorPath
$androidRunStateGateway = Read-Utf8 $androidRunStateGatewayPath
$resourceRunCoordinator = Read-Utf8 $resourceRunCoordinatorPath
$androidResourceRecipeFactory = Read-Utf8 $androidResourceRecipeFactoryPath
$androidResourceRunGateway = Read-Utf8 $androidResourceRunGatewayPath
$runSurfaceContract = Read-Utf8 $runSurfaceContractPath
$runSurfaceController = Read-Utf8 $runSurfaceControllerPath
$runSurfaceHost = Read-Utf8 $runSurfaceHostPath
$runReportScreen = Read-Utf8 $runReportScreenPath
$runTerminalSurfaceBinding = Read-Utf8 $runTerminalSurfaceBindingPath

Assert-True ($main -notmatch 'maybeRenderShellProgress') 'shell progress must not route through maybeRenderShellProgress.'
Assert-True ($main -notmatch 'SHELL_PROGRESS_RENDER_INTERVAL_MS') 'shell progress render throttle must not imply whole-surface redraw.'
Assert-True ($main -notmatch 'lastShellProgressRenderAt') 'shell progress render timestamp should stay removed.'
Assert-True ($main -notmatch 'File\(entry\.transcriptPath\)\.readText\(\)') 'terminal auth transcript must not be fully read on UI path.'
Assert-True ($main -notmatch 'startTerminalAuthorizationLinkWatcher|readTerminalAuthorizationUrlFromTranscript|extractTerminalAuthorizationUrl|TERMINAL_AUTH_LINK') 'terminal web open must use browser proxy events, not transcript URL parsing.'
Assert-True ($main -notmatch 'currentScreen\s*=\s*AppDestination\.') 'destination changes must go through enterScreen instead of writing currentScreen directly.'
Assert-True ($main -notmatch 'override fun onBackPressed\s*\(') 'MainActivity back handling must stay on OnBackPressedDispatcher.'
Assert-True ($main -match 'onBackPressedDispatcher\.addCallback\(this, navigationBackCallback\)') 'MainActivity must register the shared navigation back callback.'
Assert-True ($main -match '(?s)private fun handleAppNavigationBack\b.*appNavigator\.resolveBack') 'MainActivity back handling must resolve through AppNavigator.'
Assert-True ($appNavigator -notmatch 'MainActivity' -and $appNavigator -match 'enum class AppDestination') 'Navigation contracts must remain independent from concrete Activity types.'
$mainOnCreate = Member-Function-Body $main 'onCreate'
$mainOnNewIntent = Member-Function-Body $main 'onNewIntent'
Assert-True ($mainOnCreate -match 'AppIntentRouter\.dispatch' -and $mainOnNewIntent -match 'AppIntentRouter\.dispatch') 'Initial and reused Activity intents must share AppIntentRouter dispatch.'
Assert-True ($mainOnCreate -match 'KiteAppGraph\.from\(applicationContext\)') 'MainActivity must obtain long-lived dependencies from the process composition root.'
Assert-True ($mainOnCreate -notmatch 'KiteDiagnostics\(' -and $mainOnCreate -notmatch 'BrowserAuthSessionStore\(' -and $mainOnCreate -notmatch 'KiteResourceInstallStore\(') 'MainActivity must not recreate process dependencies directly.'
Assert-True ($appIntentRouter -match 'BrowserAuthRedirectParser\.parse' -and $appIntentRouter -match 'EXTRA_RUNTIME_ACTION' -and $appIntentRouter -match 'CardRunIntents\.EXTRA_RECIPE_ID') 'AppIntentRouter must preserve browser, automation, then card-run classification.'
Assert-True ($kiteAppGraph -match 'context\.applicationContext' -and $kiteAppGraph -notmatch 'android\.app\.Activity|android\.view\.View') 'KiteAppGraph must retain only application context and non-View dependencies.'
Assert-True ($resourceFeatureContract -match 'ResourceFeatureUiState' -and $resourceFeatureContract -match 'ResourceFeatureAction' -and $resourceFeatureContract -match 'ResourceFeatureEffect' -and $resourceFeatureContract -match 'ResourceFeatureGateway') 'Resource feature must expose one shared state, action, effect, and dependency contract.'
Assert-True ($resourceFeatureController -match 'KiteResourceRuntimeFactsProjector\.project' -and $resourceFeatureController -match 'KiteResourceUiProjector\.project' -and $resourceFeatureController -match 'KiteResourceInstallStepUiProjector\.project') 'Resource feature state must reuse existing fact and UI projectors.'
Assert-True ($resourceFeatureController -match 'KiteResourceActionCoordinator\.primaryIntent' -and $resourceFeatureController -match 'KiteResourceActionRequest') 'Resource feature actions must reuse the stable resource action contract.'
Assert-True ($resourceFeatureController -notmatch '(?m)^import\s+(android\.|androidx\.|com\.kite\.app\.(MainActivity|CardRunActivity|shell\.))') 'Resource feature controller must remain independent from Android views and navigation.'
Assert-True ($resourceFeatureController -notmatch '\.(markInstalling|markPreparing|markUninstalling|markInstalled|markFailed|beginPlan|clearPlan)\s*\(') 'Resource feature controller must not write installation facts owned by KiteResourceInstallStore.'
Assert-True ($resourceFeatureFragment -match '(?s)repeatOnLifecycle\(Lifecycle\.State\.STARTED\).*ResourceFeatureAction\.ReconcileFacts') 'resource features must reconcile owner facts whenever their view returns to the foreground.'
Assert-True ($recipeFeatureGateway -match 'interface RecipeFeatureGateway' -and $recipeFeatureGateway -match 'loadRecipes' -and $recipeFeatureGateway -match 'runSnapshot') 'Home and recipe editor must share one application recipe gateway contract.'
Assert-True ($homeFeatureContract -match 'HomeFeatureUiState' -and $homeFeatureContract -match 'HomeFeatureAction' -and $homeFeatureContract -match 'HomeFeatureEffect') 'Home feature must expose one state, action, and effect contract.'
Assert-True ($homeFeatureController -match 'KiteCardRunUiProjector\.project' -and $homeFeatureController -match 'KiteRecipeActionRequest') 'Home feature must reuse the shared run projector and recipe action request.'
Assert-True ($homeFeatureController -notmatch '(?m)^import\s+(android\.|androidx\.|com\.kite\.app\.(MainActivity|CardRunActivity|shell\.|run\.CardRunStore))') 'Home feature controller must remain independent from Android views, navigation, and concrete run stores.'
Assert-True ($homeFeatureController -notmatch '\.(startRecipe|stopRecipe|executeRecipe|saveUserRecipe|deleteRecipe)\s*\(') 'Home feature controller must not execute recipes or mutate recipe facts.'
Assert-True ($homeFeatureFragment -match '(?s)repeatOnLifecycle\(Lifecycle\.State\.STARTED\).*HomeFeatureAction\.ReconcileRuns') 'Home feature must reconcile run-owner facts whenever it returns to the foreground.'
Assert-True ($homeFeatureFragment -match 'change\.catalogInvalidated' -and $homeFeatureFragment -match 'HomeFeatureAction\.Refresh') 'Home feature must reconcile catalog facts when a recipe mutation is replayed.'
Assert-True ($androidRecipeFeatureGateway -match 'MutableSharedFlow<RecipeFeatureChange>\(\s*replay = 1' -and $androidRecipeFeatureGateway -match '@Volatile\s+private var cachedRecipes') 'Recipe mutations must be replayable and backed by one process catalog snapshot.'
Assert-True ($androidRecipeFeatureGateway -match '(?s)cachedRecipes = catalog\s+mutationChanges\.tryEmit' -and $androidRecipeFeatureGateway -match '(?s)cachedRecipes = withContext.*removeClosedRunStatesForRecipes|(?s)removeClosedRunStatesForRecipes.*cachedRecipes = withContext') 'Recipe save and delete must update the catalog snapshot before publishing mutation signals.'
$addResourceHomeCard = Function-Body $main 'addResourceHomeCard'
$refreshDropZoneRecipes = Function-Body $main 'refreshDropZoneRecipes'
Assert-True ($addResourceHomeCard -match 'recipeFeatureGateway\.invalidateCatalog\("resource_home_card_added"\)') 'Resource home-card writes must invalidate the shared recipe catalog snapshot.'
Assert-True ($refreshDropZoneRecipes -match 'recipeFeatureGateway\.refreshExternalRecipes\(\)' -and $refreshDropZoneRecipes -notmatch 'dropZoneManager\.scanAndImport\(\)') 'Shell drop-zone refresh must use the shared recipe gateway instead of bypassing its catalog state.'
Assert-True ($homeScreen -match 'structureSignature' -and $homeScreen -match 'factory\.bind' -and $homeScreen -match 'fun acknowledge\(') 'Home screen must separate structural rebuilds from local run-state binding and immediate action acknowledgement.'
Assert-True ($homeScreen -notmatch 'CardRunStore|KiteRecipeLoader|MainActivity') 'Home screen must only project supplied state and must not read stores, files, or the shell.'
Assert-True ($recipeEditorContract -match 'RecipeEditorDraft' -and $recipeEditorContract -match 'RecipeEditorStepDraft' -and $recipeEditorContract -match 'validationErrors') 'Recipe editor contract must own its draft, ordered steps, and validation result.'
Assert-True ($recipeEditorController -match 'gateway\.saveRecipe' -and $recipeEditorController -match 'gateway\.deleteRecipe' -and $recipeEditorController -match 'KiteRecipeActionRequest') 'Recipe editor controller must save configuration through the recipe gateway and submit runs through the shared action contract.'
Assert-True ($recipeEditorController -notmatch '(?m)^import\s+(android\.|androidx\.|com\.kite\.app\.(MainActivity|CardRunActivity|shell\.|run\.CardRunStore))') 'Recipe editor controller must remain independent from Android views, navigation, and concrete run stores.'
Assert-True ($recipeEditorController -notmatch '\.(startRecipe|stopRecipe|executeRecipe|saveUserRecipe|deleteUserRecipe)\s*\(') 'Recipe editor controller must not bypass gateway or execute recipe steps.'
Assert-True ($kiteAppGraph -match 'val recipeLoader: KiteRecipeLoader by lazy' -and $kiteAppGraph -match 'val cardGroupStore: KiteCardGroupStore by lazy' -and $kiteAppGraph -match 'val recipeFeatureGateway: RecipeFeatureGateway by lazy') 'Recipe loader, group store, and gateway must be process composition-root dependencies.'
Assert-True ($kiteAppGraph -match 'fun createRecipeLoader\(\): KiteRecipeLoader = recipeLoader' -and $main -notmatch 'KiteCardGroupStore\(applicationContext\)') 'Shell callers must reuse process-owned recipe and group facts instead of constructing parallel stores.'
Assert-True ($runExecutionContract -match 'interface RunStateGateway' -and $runExecutionContract -match 'interface RecipeExecutor' -and $runExecutionContract -match 'RecipeExecutionEvent') 'Run application contract must expose one state owner port, one executor port, and structured execution events.'
Assert-True ($runExecutionContract -match '(?s)fun bridgeRunId\(\).*terminalSessionId\.isNullOrBlank\(\).*it != terminalSessionId' -and $runExecutionContract -match 'fun hasBridgeProcessBinding\(\)') 'A terminal-owned runId must not be classified as a Bridge process binding.'
Assert-True ($runOrchestrator -match 'class RunOrchestrator' -and $runOrchestrator -match 'executionFlights' -and $runOrchestrator -match 'validStateFor') 'Run orchestrator must enforce one execution flight per instance generation and reject stale events.'
Assert-True ($runOrchestrator -match '(?s)fun completeCurrentStep\b.*executionFlights\.remove\(instanceId\).*clearTerminalSession.*executor\.completeWaitingStep') 'Waiting-step completion must revoke the old execution flight and display binding before closing the runtime resource.'
Assert-True ($stopCoordinator -match 'class StopCoordinator' -and $stopCoordinator -match 'remainingProcessIds' -and $stopCoordinator -match 'StopResolution') 'Stop coordinator must resolve confirmed stop and process residue before writing final state.'
Assert-True ($stopCoordinator -match 'result\.residueMarkerObserved' -and $stopCoordinator -match '\u5df2\u505c\u6b62\uff0c\u672a\u53d1\u73b0\u8fdb\u7a0b\u6b8b\u7559') 'An explicit empty Bridge residue audit must confirm stopped state even when force-kill returns a nonzero execution result.'
$runApplicationLayer = $runExecutionContract + "`n" + $runOrchestrator + "`n" + $stopCoordinator
Assert-True ($runApplicationLayer -notmatch '(?m)^import\s+(android\.|androidx\.|com\.kite\.app\.(MainActivity|CardRunActivity|shell\.|bridge\.|platform\.))') 'Run application layer must remain independent from Android, concrete bridge/platform adapters, and page navigation.'
Assert-True ($runOrchestrator -notmatch 'Toast|startActivity|showConsole|showCardRunSurface|WebView|TerminalRuntimeHost|KiteBridgeClient') 'Run orchestrator must not perform page or execution-core work directly.'
Assert-True ($runExecutionEffectBus -match 'MutableSharedFlow<RunExecutionEffect>' -and $runExecutionEffectBus -notmatch 'replay\s*=') 'Run presentation effects must stay one-shot while durable recovery remains in CardRunStore.'
Assert-True ($runLifecycleEventHub -match 'CopyOnWriteArrayList<RunLifecycleSink>' -and $runLifecycleEventHub -notmatch 'MutableStateFlow|mutableMapOf') 'Run lifecycle events must notify after fact commits without becoming a second state store.'
Assert-True ($androidRecipeExecutor -match 'class AndroidRecipeExecutor' -and $androidRecipeExecutor -match 'when \(request\.step\.type\)' -and $androidRecipeExecutor -match 'KiteRecipe\.STEP_SHELL' -and $androidRecipeExecutor -match 'KiteRecipe\.STEP_TERMINAL' -and $androidRecipeExecutor -match 'KiteRecipe\.STEP_OPEN_WEB' -and $androidRecipeExecutor -match 'KiteRecipe\.STEP_X11' -and $androidRecipeExecutor -match 'KiteRecipe\.STEP_ANDROID_ACTION') 'Android recipe executor must dispatch every supported step through the shared execution port.'
Assert-True ($androidRecipeExecutor -match '!request\.hasBridgeProcessBinding\(\)' -and $androidRecipeExecutor -match 'val bridgeRunId = request\.bridgeRunId\(\)') 'Android stop execution must bypass Bridge for terminal-only sessions and use only the normalized Bridge run id.'
Assert-True ($androidRecipeExecutor -match 'residueMarkerObserved = observationLines\.any' -and $androidRecipeExecutor -match 'residueMarkerObserved = residueMarkerObserved') 'Android stop execution must preserve explicit Bridge residue-audit evidence for StopCoordinator.'
Assert-True ($androidRecipeExecutor -notmatch '(?m)^import\s+(android\.app\.Activity|android\.view\.|android\.widget\.|com\.kite\.app\.(MainActivity|CardRunActivity|shell\.|feature\.))') 'Android recipe executor must remain independent from pages, View widgets, Shell, and Feature code.'
Assert-True ($androidRunStateGateway -match 'CardRunStore\.(registerRecipe|registeredRecipe|get|currentForRecipe|start|update)') 'Android run-state adapter must keep CardRunStore as the single run-fact owner.'
Assert-True ($resourceRunCoordinator -match 'class ResourceRunCoordinator' -and $resourceRunCoordinator -match 'RunLifecycleEventHub' -and $resourceRunCoordinator -match 'startNextPlannedInstall') 'Resource run coordination must own terminal settlement and dependency-plan continuation at process scope.'
Assert-True ($resourceRunCoordinator -notmatch '(?m)^import\s+(android\.|androidx\.|com\.kite\.app\.(MainActivity|CardRunActivity|shell\.|run\.CardRunStore))') 'Resource run coordinator must remain independent from Android pages, navigation, and concrete CardRunStore.'
Assert-True ($androidResourceRunGateway -match 'CardRunStore\.start' -and $androidResourceRunGateway -match 'installStore\.markInstalled' -and $androidResourceRunGateway -match 'installStore\.saveInstalledSnapshot') 'Android resource gateway must adapt CardRun and resource registry facts for the process coordinator.'
Assert-True ($androidResourceRecipeFactory -match 'KiteResourceInstallPlanCompiler\.compile' -and $androidResourceRecipeFactory -match 'KiteResourceInstallRecipes\.toRecipe') 'Resource run recipes must be compiled from manifests through the shared install compiler.'
Assert-True ($kiteAppGraph -match 'val resourceRunCoordinator: ResourceRunCoordinator by lazy' -and $kiteAppGraph -match 'lifecycleHub = runLifecycleEventHub') 'Resource run coordination and lifecycle events must be process composition-root dependencies.'
Assert-True ($terminalFragment -match '(?s)detailBackCallback.*showListPage\(\)') 'terminal detail back callback must return to the terminal list first.'
Assert-True ($terminalFragment -match '(?s)btnBackToSessions.*?onBackPressedDispatcher\.onBackPressed\(\)') 'terminal detail header must submit through the shared back dispatcher.'
Assert-True ($runSurfaceHost -match 'private var binding: RunSurfaceBinding' -and $runReportScreen -match 'override fun render\(state: RunSurfaceUiState\)' -and $runReportScreen -match 'private val outputText = TextView') 'report page must own its local output binding through RunSurfaceHost.'
Assert-True ($main -notmatch 'cardRunReportBinding|updateVisibleCardRunReport|renderVisibleCardRunReport') 'MainActivity must not retain the legacy report binding or render loop.'
Assert-True ($runTerminalSurfaceBinding -match 'TerminalFragment\.detailOnly' -and $runTerminalSurfaceBinding -match '\.detach\(fragment\)' -and $runTerminalSurfaceBinding -notmatch 'stop\(|RunOrchestrator|TerminalSessionController') 'terminal surface binding must own Fragment attach/detach without stopping the shell session.'
Assert-True ($main -notmatch 'showCardRunTerminalFragment|cardRunTerminalContainerId|CARD_RUN_TERMINAL_FRAGMENT_TAG') 'MainActivity must not retain the legacy CardRun terminal Fragment binding.'
Assert-True ($main -match '(?s)private fun handleCardRunBackSignal\(\)\s*\{\s*closeCardRunTask\(\)\s*\}' -and $main -match '(?s)private fun closeCardRunTask\b.*?finishAndRemoveTask\(\).*?(?=\n    private fun )') 'CardRun back must detach the task window instead of completing or stopping the run.'
Assert-True ($main -match 'resourceInstallWizardSurface\?\.tick' -and $resourceInstallWizardScreen -match 'fun tick\(') 'install wizard must keep elapsed binding inside its feature screen.'
Assert-True ($main -match '(?s)private fun showConsole\b.*HomeFragment' -and $main -notmatch 'consoleCardBindings|consolePageBodyHost|private fun recipeGrid|updateVisibleConsoleCard') 'Home card views, bindings, and page state must remain owned by HomeFragment and HomeScreen.'
Assert-True ($main -match 'resourceCatalogForUiRender') 'UI resource render should use cached catalog helper.'
Assert-True ($main -match 'observeRuntimePanelSummarySignals') 'runtime panel summary counts must observe existing store snapshots.'
Assert-True ($main -match 'handleRuntimeAutomationIntent') 'Kite MainActivity must expose the runtime automation diagnostic entry on the real launcher path.'
Assert-True ($main -match 'RuntimeAutomationActions\.dumpDiagnostics\(applicationContext\)') 'Kite runtime diagnostics must reuse the shared RuntimeAutomationActions dump path.'
Assert-True ($main -match 'RuntimeAutomationActions\.rotateProotTelemetry\(applicationContext\)') 'Kite runtime automation must expose telemetry rotation on the real launcher path.'
Assert-True ($main -match 'RuntimeAutomationActions\.prepareProotLiveTraceeProbe' -and $main -match 'RuntimeAutomationActions\.injectProotLiveTraceeProbe') 'Kite runtime automation must expose live-tracee probe actions on the real launcher path.'
Assert-True ($main -match 'private fun readProbeTargetLiveTracees' -and $main -match 'EXTRA_AUTOMATION_PROBE_TARGET_LIVE_TRACEES = "probe_target_live_tracees"') 'Kite live-tracee probe automation must accept an explicit target tracee count.'
Assert-True ($main -match 'ACTION_STOP_BACKGROUND_RUNTIME = "stop_background_runtime"' -and $main -match '(?s)private fun stopBackgroundRuntimeFromAutomation\b.*TaskManagerStore\.stopRuntime\(applicationContext, runtimeId\)') 'Kite runtime automation must stop background runtimes through the existing TaskManagerStore path.'
Assert-True ($main -match 'ACTION_RECLAIM_OWNER_RUNTIME = "reclaim_owner_runtime"' -and $main -match 'EXTRA_AUTOMATION_OWNER_ID = "owner_id"') 'Kite runtime automation must expose an explicit owner reclaim action and owner_id extra.'
Assert-True ($main -match '(?s)private fun reclaimOwnerRuntimeFromAutomation\b.*ownerId\.isAutomationRuntimeOwnerId\(\).*RuntimeReclaimer\.reclaimOwnerRuntime') 'Kite owner reclaim automation must validate card/resource/terminal owner ids before dispatching RuntimeReclaimer.'
Assert-True ($main -match '(?s)private fun String\.isAutomationRuntimeOwnerId\(\).*startsWith\("card:"\).*startsWith\("resource:"\).*startsWith\("terminal:"\)') 'Kite owner reclaim automation must stay bounded to card/resource/terminal owner ids.'
Assert-True ($runtimeAutomationActions -match '(?s)fun dumpDiagnostics\b.*RuntimeHealthStore\.refresh\(\s*context = appContext,\s*reason = "adb-dump-diagnostics"') 'ADB dump diagnostics must refresh RuntimeHealth before exporting owner/process facts.'
Assert-True ($main -match 'ACTION_STOP_CARD_RUN = "stop_card_run"') 'Kite runtime automation must expose a gated card stop action for real-path owner stop validation.'
Assert-True ($main -match '(?s)private fun stopCardRunFromAutomation\b.*stopRecipeByCardInstanceId\(recipe, state\.cardInstanceId, state\)') 'Kite card stop automation must reuse the product card-instance stop path.'
Assert-True ($main -match 'ACTION_START_RESOURCE_OWNER_PROBE = "start_resource_owner_probe"') 'Kite runtime automation must expose a gated resource owner probe for real-device validation.'
Assert-True ($main -match '(?s)private fun startResourceOwnerProbeFromAutomation\b.*ownerKind = RecipeRuntimeState\.OWNER_KIND_RESOURCE.*startRecipe\(\s*recipe = recipe,\s*previousState = state') 'resource owner probe must create a resource CardRun state before using the product start path.'
Assert-True ($main -match '(?s)private fun resourceOwnerProbeRecipe\b.*KiteResourceInstallRecipes\.toRecipe\(\s*KiteResourceInstallSpec') 'resource owner probe must use the existing resource install recipe shape.'

$startRecipe = Function-Body $main 'startRecipe'
$startRecipeWithOrchestrator = Function-Body $main 'startRecipeWithOrchestrator'
$startResourceRun = Function-Body $main 'startResourceRun'
Assert-True ($startRecipe -match 'startRecipeWithOrchestrator' -and $startRecipe -notmatch 'legacyStartRecipe|recipeUsesProcessRunOrchestrator') 'All recipe start intake must submit through the process orchestrator.'
Assert-True ($startRecipeWithOrchestrator -match 'runOrchestrator\.start' -and $startRecipeWithOrchestrator -match 'ownerKind = previousState\.ownerKind' -and $startRecipeWithOrchestrator -match 'stepId = previousState\.stepId') 'RunOrchestrator start must preserve the existing CardRun owner identity.'
Assert-True ($main -notmatch 'legacyStartRecipe|executeRecipeStep|runUbuntuStepWhenReady|handleSequenceShellResult') 'MainActivity must not retain a second recipe execution engine.'
Assert-True ($kiteAppGraph -match 'val runOrchestrator: RunOrchestrator by lazy' -and $kiteAppGraph -match 'AndroidRecipeExecutor\(appContext, bridgeClient, diagnostics\)') 'Run orchestration and execution adapter must be process composition-root dependencies.'
Assert-True ($startResourceRun -match 'resourceRunCoordinator\.start' -and $startResourceRun -notmatch 'thread\(|startRecipe\(|ToolchainPackInstaller\.|setRuntimeState\(') 'Resource run intake must delegate preparation and execution to the process coordinator without page-owned execution.'
Assert-True ($main -notmatch 'resourceManifestRecipeSteps|resourceManifestActionCommand|legacyResourceInstallStep|legacyResourceUninstallStep|markResourceRunSuccess|markResourceInstallFailed') 'MainActivity must not compile resource recipes or settle resource registry facts.'
Assert-True ($runSurfaceContract -match 'sealed interface RunSurfaceContent' -and $runSurfaceContract -match 'val structureKey: String') 'Run surface feature must project explicit content and a stable structural binding key.'
Assert-True ($runSurfaceController -match 'recipe\.id != current\.recipeId \|\| state\.instanceId != current\.instanceId' -and $runSurfaceController -match '(?s)fun detach\(\).*target = null') 'Run surface controller must reject cross-instance state and detach without stopping runtime work.'
Assert-True ($runSurfaceController -notmatch 'Activity|Fragment|View|CardRunStore|TerminalRuntimeHost|WebView') 'Run surface controller must remain independent from Android surfaces and concrete runtime stores.'

$handleProgress = Member-Function-Body $androidRecipeExecutor 'handleShellProgress'
Assert-True ($handleProgress -match 'RecipeExecutionEvent\.Progress' -and $handleProgress -notmatch 'Activity|View|showCardRunSurface|showConsole|renderResourceInstallWizardFor') 'Shell progress must stay in the UI-agnostic execution adapter.'

$runtimePanelObserver = Function-Body $main 'observeRuntimePanelSummarySignals'
Assert-True ($runtimePanelObserver -match 'TerminalSessionStore\.snapshot\.collect') 'runtime panel terminal count must reuse TerminalSessionStore snapshot.'
Assert-True ($runtimePanelObserver -match 'TaskManagerStore\.snapshot\.collect') 'runtime panel process count must reuse TaskManagerStore snapshot.'
Assert-True ($runtimePanelObserver -notmatch 'showCardRunSurface|showConsole|showKiteProcessOverview|refreshResourceScreenIfVisible') 'runtime panel count observer must not trigger whole-surface refreshes.'

$runtimePanelSummary = Function-Body $main 'runtimePanelSummary'
Assert-True ($runtimePanelSummary -match 'CardRunStore\.runs\.value') 'runtime panel card count must reuse CardRunStore.'
Assert-True ($runtimePanelSummary -match 'TerminalSessionStore\.snapshot\.value\.liveSessions\.size') 'runtime panel terminal count must reuse live terminal snapshot.'
Assert-True ($runtimePanelSummary -match 'taskSnapshot\.processes\.size') 'runtime panel process count must use the task-manager snapshot passed to the render frame.'

$runtimePanelCounts = Function-Body $main 'renderRuntimePanelCounts'
Assert-True ($runtimePanelCounts -match 'runtimePanelCardCountView\?\.text') 'runtime panel counts should update existing value views.'
Assert-True ($runtimePanelCounts -notmatch 'showCardRunSurface|showConsole|showKiteProcessOverview|RuntimeHealthStore\.refresh|TaskManagerStore\.refresh|TerminalSessionStore\.refresh') 'runtime panel count rendering must stay a cheap text bind.'

$showRuntimePanel = Function-Body $main 'showUbuntuRuntimePanel'
Assert-True ($showRuntimePanel -match 'requestRuntimePanelSummaryRefresh') 'opening runtime panel should request throttled summary refresh.'

$consoleRecipeAction = Function-Body $main 'handleRecipeActionWithRouter'
$editorRecipeRun = Function-Body $recipeEditorController 'requestRun'
Assert-True ($consoleRecipeAction -match 'submitRecipeAction') 'console recipe actions must submit through the shared action intake.'
Assert-True ($editorRecipeRun -match 'KiteRecipeActionRequest' -and $editorRecipeRun -match 'KiteRecipeActionSource\.Editor' -and $editorRecipeRun -notmatch '\bstartRecipe\s*\(') 'editor start must submit through the shared action intake instead of starting directly.'
Assert-True ($recipeEditorScreen -match 'KiteRecipeActionIntent\.Open' -and $recipeEditorScreen -match 'KiteRecipeActionIntent\.Stop' -and $recipeEditorScreen -match 'KiteRecipeActionIntent\.Start') 'editor open, stop and start controls must submit explicit shared action intents.'
Assert-True ($recipeEditorFragment -match 'RecipeEditorResultContract\.actionRequest' -and $recipeEditorFragment -notmatch '\bstopRecipe\s*\(' -and $recipeEditorFragment -notmatch '\bopenRecipeRunInstance\s*\(') 'editor action buttons must not bypass the shared action intake.'

$resourcePrimaryAction = Function-Body $main 'handleResourceAction'
$resourceSecondaryIntent = Function-Body $resourceFeatureController 'secondaryIntent'
$resourceSecondaryRequest = Function-Body $resourceFeatureController 'requestSecondary'
Assert-True ($resourcePrimaryAction -match 'KiteResourceActionCoordinator\.primaryIntent' -and $resourcePrimaryAction -match 'submitResourceAction') 'resource primary actions must resolve and submit through the shared action intake.'
Assert-True ($resourceSecondaryIntent -match 'KiteResourceActionIntent\.CancelInstall' -and $resourceSecondaryIntent -match 'KiteResourceActionIntent\.Stop' -and $resourceSecondaryIntent -match 'KiteResourceActionIntent\.Uninstall') 'resource detail secondary actions must resolve explicit shared intents.'
Assert-True ($resourceSecondaryRequest -match 'KiteResourceActionRequest' -and $resourceSecondaryRequest -match 'item\.secondaryIntent') 'resource detail secondary actions must submit through the shared action intake.'
Assert-True ($resourceSecondaryRequest -notmatch '\bhandleResource(?:Install|Uninstall|OpenStop|Cancel|Failed)') 'resource detail buttons must not bypass the shared action intake.'

$installPlanAction = Function-Body $main 'submitInstallPlanAction'
$runManagementStop = Function-Body $main 'stopRunManagementCard'
$cardRunWindowClose = Function-Body $main 'closeCardRunWindowInstance'
Assert-True ($resourceInstallWizardPresentation -match 'KiteInstallPlanActionCoordinator\.plan' -and $resourceInstallWizardScreen -match 'onPlanAction\(intent\)') 'install wizard primary action must submit a coordinated plan intent.'
Assert-True ($resourceInstallWizardScreen -notmatch '\bstartNextResourceInstallFromPlan\s*\(' -and $resourceInstallWizardScreen -notmatch '\bshowResources\s*\(') 'install wizard button binding must not execute or navigate directly.'
Assert-True ($installPlanAction -match 'KiteInstallPlanActionIntent\.StartNext' -and $installPlanAction -match 'startNextResourceInstallFromPlan') 'shell must remain the install-plan execution owner.'
Assert-True ($runManagementStop -match 'submitRecipeAction' -and $runManagementStop -match 'instanceId = group\.run\.instanceId') 'run management stop must submit the selected card instance.'
Assert-True ($cardRunWindowClose -match 'submitRecipeAction' -and $cardRunWindowClose -match 'instanceId = latestRootState\.instanceId') 'card run window close must submit the selected root instance.'

$showRunManagement = Function-Body $main 'showKiteProcessOverview'
Assert-True ($showRunManagement -match 'runManagementHeader') 'run management page should use the run-management header.'
Assert-True ($showRunManagement -notmatch 'kiteProcessSummaryBlock') 'run management page must not render the old three-count summary card.'
Assert-True ($showRunManagement -match 'buildRunManagementGroups') 'run management page should render grouped card runtime rows.'
Assert-True ($showRunManagement -match 'runManagementOtherProcessSections') 'run management page should render non-card process sections.'
Assert-True ($showRunManagement -match 'val taskSnapshot = TaskManagerStore\.snapshot\.value' -and $showRunManagement -match 'buildRunManagementGroups\(\s*runs = runs,\s*terminalItems = terminalItems,\s*processItems = taskSnapshot\.processes' -and $showRunManagement -match 'runManagementOtherProcessSections\(groups, taskSnapshot\.processes\)') 'run management page must classify one render frame from one task-manager snapshot.'

$runManagementGroups = Function-Body $main 'buildRunManagementGroups'
Assert-True ($runManagementGroups -match 'CardRunStore\.runs\.value') 'run management groups must reuse CardRunStore.'
Assert-True ($runManagementGroups -match 'TerminalSessionStore\.snapshot\.value\.sessions') 'run management groups must reuse TerminalSessionStore.'
Assert-True ($runManagementGroups -match 'TaskManagerStore\.snapshot\.value\.processes') 'run management groups must reuse TaskManagerStore.'

$runManagementOtherProcessSections = Function-Body $main 'runManagementOtherProcessSections'
Assert-True ($runManagementOtherProcessSections -match 'cardProcessIds' -and $runManagementOtherProcessSections -match 'processItems: List<TaskManagerProcessItem> = TaskManagerStore\.snapshot\.value\.processes') 'run management non-card process sections must reuse the render-frame process list and keep TaskManagerStore as a default source.'
Assert-True ($runManagementOtherProcessSections -notmatch '"\u7ec8\u7aef"' -and $runManagementOtherProcessSections -match '"\u7cfb\u7edf"' -and $runManagementOtherProcessSections -match '"\u5176\u4ed6"') 'run management non-card processes must be grouped as system and other, without a separate terminal bucket.'
Assert-True ($main -match 'private fun TaskManagerProcessItem\.isRunManagementSystemProcess' -and $main -match 'supervisord' -and $main -match '/runtime/bin/proot' -and $main -match '/workspace/\.kf/system/') 'run management must classify Kite/container skeleton processes as system rows.'

$runManagementProcessDialog = Function-Body $main 'showRunManagementProcessDialog'
Assert-True ($runManagementProcessDialog -match 'runManagementProcessDialogText' -and $runManagementProcessDialog -match 'showRunManagementProcessConfirmDialog') 'run management process click must show details before the confirm step.'
$runManagementProcessDialogText = Function-Body $main 'runManagementProcessDialogText'
Assert-True ($runManagementProcessDialogText -match '\u540d\u79f0:' -and $runManagementProcessDialogText -match '\u7528\u9014:' -and $runManagementProcessDialogText -match '\u5b8c\u6574\u547d\u4ee4:') 'run management process dialog must show readable name, purpose, and full command.'
Assert-True ($runManagementProcessDialogText -notmatch '\u6765\u6e90:' -and $runManagementProcessDialogText -notmatch '\u5355\u5143:') 'run management process dialog must not expose raw source/unit debug labels as primary facts.'
Assert-True ($main -match 'runManagementProcessName' -and $main -match 'PRoot \u5bb9\u5668\u5165\u53e3' -and $main -match 'Kite \u547d\u4ee4\u542f\u52a8\u5668' -and $main -match '\u672a\u5173\u8054\u5361\u7247') 'run management process rows must use readable process names and ownership labels.'
$runManagementProcessConfirmDialog = Function-Body $main 'showRunManagementProcessConfirmDialog'
Assert-True ($runManagementProcessConfirmDialog -match 'stopRunManagementProcess\(process, pid\)') 'run management process confirm must dispatch the process stop only after second confirmation.'
$runManagementHeader = Function-Body $main 'runManagementHeader'
Assert-True ($runManagementHeader -match 'scheduleRunManagementLazyRefresh\(force = true\)' -and $runManagementHeader -notmatch '\u7ec8\u7aef \$\{summary\.runningTerminals\}') 'run management refresh button must use lazy refresh and avoid terminal as a top-level count.'
$stopRunManagementProcess = Function-Body $main 'stopRunManagementProcess'
Assert-True ($stopRunManagementProcess -match 'runManagementPendingProcessStopIds\.add\(pid\)' -and $stopRunManagementProcess -match 'scheduleRunManagementLazyRefresh\(force = true\)') 'run management process stop must show local pending feedback and schedule lazy refresh passes.'
Assert-True ($stopRunManagementProcess -match 'markRunManagementProcessStopAsUserStop\(process, pid\)') 'run management manual process stop must mark card-owned processes as user-stopped before killed shell output can report failure.'
Assert-True ($main -match 'private fun markRunManagementProcessStopAsUserStop' -and $main -match 'RecipeRunStatus\.Stopped' -and $main -match 'clearRunBinding = true') 'manual card process stop must become a stopped card state, not a failed startup.'
Assert-True ($main -match 'private fun runManagementRunForProcess' -and $main -match 'runtimeUnitId' -and $main -match 'runtimeOwnerId' -and $main -match 'boundProcessIds\(\)') 'manual process stop must resolve the owning CardRun from TaskManager ownership facts.'
$setRuntimeState = Function-Body $main 'setRuntimeState'
Assert-True ($setRuntimeState -match 'shouldIgnoreRuntimeStateAfterUserStop' -and $main -match 'runtime_state_ignored_after_user_stop') 'CardRun state updates must ignore stale runtime callbacks after a user stop.'
Assert-True ($main -match 'current\.status != RecipeRunStatus\.Stopping && current\.status != RecipeRunStatus\.Stopped' -and $main -match 'status == RecipeRunStatus\.BridgeUnavailable') 'stale callback guard must protect Stopping/Stopped instances from late runtime result writes.'
Assert-True ($setRuntimeState -match 'clearActiveRunInstance\(recipe, state\.instanceId\)' -and $main -match 'private fun clearActiveRunInstance') 'stopped card runs must be removed from the active instance index.'
Assert-True ($main -match 'CardRunStore\.currentForRecipe\(recipe\.id\)\?\.instanceId') 'stale callback guard must still find stopped instances after the active index is cleared.'
Assert-True ($main -match 'private fun scheduleRunManagementLazyRefresh' -and $main -match '260L, 900L, 1800L') 'run management lazy refresh must use a short bounded refresh window, not a realtime polling loop.'
Assert-True ($taskManagerStore -match '(?s)fun endProcess\(context: Context, pid: Int\).*ContainerProcessStore\.terminate\(context\.applicationContext, pid, force = true\)') 'task manager pid-only manual end-process must keep force termination fallback.'
Assert-True ($taskManagerStore -match 'fun endProcess\(context: Context, item: TaskManagerProcessItem' -and $taskManagerStore -match 'ProotOwnerProcessTerminator\.terminate\(appContext, ownerId\)' -and $taskManagerStore -match 'private fun TaskManagerProcessItem\.prootOwnerStopId' -and $taskManagerStore -match 'id\.startsWith\("root-"\)' -and $taskManagerStore -notmatch 'missedOwner') 'task manager manual end-process must reserve owner stop for root rows; concrete process rows must stay pid-only.'
Assert-True ($stopRunManagementProcess -match 'TaskManagerStore\.endProcess\(applicationContext, process, pid\)') 'run management manual process stop must pass owner facts to TaskManagerStore.'
Assert-True ($containerProcessStore -match '(?s)else if \(force\).*killUbuntuProcessPid\(' -and $containerProcessStore -notmatch '(?s)else if \(force\).*HostProcessTerminator\.killHostProcess') 'manual force termination must stay inside Ubuntu pid kill and never fall back to host PID kill.'

Assert-True ($cardRunStore -match '(?s)fun initialize\b.*?shouldDropCurrentAfterProcessRestore') 'CardRunStore must not restore stale current runs after process restart.'
Assert-True ($cardRunModels -match 'val cardInstanceId: String get\(\) = instanceId') 'CardRunState must expose cardInstanceId as the card ownership alias.'
Assert-True ($cardRunStore -match '\.put\("cardInstanceId", cardInstanceId\)') 'CardRunStore must persist cardInstanceId beside instanceId.'
Assert-True ($cardRunStore -match 'optString\("cardInstanceId"\)') 'CardRunStore must restore cardInstanceId for ownership-compatible snapshots.'
Assert-True ($browserProxy -match 'KITE_CARD_INSTANCE_ID') 'Browser proxy environment must export KITE_CARD_INSTANCE_ID.'
Assert-True ($browserProxy -match 'cardInstanceId=') 'Browser proxy requests must carry cardInstanceId.'
Assert-True ($localServer -match 'cardInstanceId') 'Local server must accept cardInstanceId on open-web requests.'
Assert-True ($localServer -match 'runCatching \{ handleClient\(client\) \}' -and $localServer -match 'localServerClientFailureEvent\(error\)') 'Each local-server client must have an exception boundary so one disconnected socket cannot crash the app process.'
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
$terminalStep = Member-Function-Body $androidRecipeExecutor 'executeTerminal'
Assert-True ($blankTerminal -match 'withTerminalOwner\(record\.id, instanceId\)') 'blank card terminals must launch with terminal owner env.'
Assert-True ($terminalStep.Contains('"KF_RUNTIME_ID" to "terminal:${record.id}"') -and $terminalStep.Contains('"KF_UNIT_ID" to "card:${request.instanceId}"')) 'terminal recipe steps must launch with terminal owner env.'
Assert-True ($terminalStep -match 'createEmbeddedShellSession' -and $terminalStep -notmatch 'createShellSession') 'recipe terminal steps must use non-persistent embedded sessions.'
Assert-True ($terminalSessionController -match 'TerminalSessionEndPolicy\.shouldSelectManagedFallback' -and $terminalSessionController -match 'embeddedSessionRecords\.remove\(targetSessionId\)') 'ending an embedded terminal must not wake an unrelated managed fallback session.'
Assert-True ($terminalSessionController -match '(?s)resolveWritableSessionHolder.*embeddedSessionRecords\[resolvedTargetId\].*managed = false') 'targeted terminal input must resolve a staged embedded session while attach is still in flight.'
# T11:model 定义已移到 ProotTelemetryModels.kt。用 Select-String 直接扫文件(规避变量合并的诡异行为)。
# T11:model 定义已移到 ProotTelemetryModels.kt。$prootTelemetryAll 已在读取点合并(store+models)。
Assert-True ($prootTelemetryAll -match 'data class ProotOwnerProcessGroup') 'PRoot telemetry must expose owner process groups.'
Assert-True ($prootTelemetryAll -match 'data class ProotOwnerProcessIndex') 'PRoot telemetry must expose an owner process index.'
Assert-True ($prootTelemetryAll -match 'val ownerProcessIndex: ProotOwnerProcessIndex') 'PRoot telemetry snapshots must carry the owner process index.'
Assert-True ($prootTelemetryStore -match 'private fun buildOwnerProcessIndex') 'PRoot telemetry must build the owner index from the live process table.'
Assert-True ($prootTelemetryStore -match 'filter \{ it\.state == ProotLiveProcessState\.RUNNING && it\.kfRuntimeId\.isNotBlank\(\) \}') 'owner process index must be derived from live tracees with KF owner ids.'
Assert-True ($prootTelemetryStore -match 'groupBy \{ it\.kfRuntimeId \}') 'owner process index must group tracees by KF runtime owner id.'
Assert-True ($prootTelemetryStore -match 'kfRuntimeId = event\.kfRuntimeId\.ifBlank \{ existing\?\.kfRuntimeId \?: parent\?\.kfRuntimeId\.orEmpty\(\) \}') 'fork/clone/vfork events must inherit KF owner id from existing or parent tracees.'
Assert-True ($prootTelemetryStore -match 'kfUnitId = event\.kfUnitId\.ifBlank \{ existing\?\.kfUnitId \?: parent\?\.kfUnitId\.orEmpty\(\) \}') 'fork/clone/vfork events must inherit KF unit id from existing or parent tracees.'
Assert-True ($prootTelemetryStore -match 'fun refreshBlocking') 'PRoot telemetry must expose a blocking refresh for stop residue checks.'
Assert-True ($prootTelemetryStore -match 'ROTATED_TELEMETRY_SEGMENTS' -and $prootTelemetryStore -match 'readRotationBaselineIfNeeded') 'PRoot telemetry must rebuild a bounded owner baseline from rotated JSONL segments.'
Assert-True ($prootTelemetryStore -match 'telemetryBaselineFiles' -and $prootTelemetryStore -match 'ROTATED_TELEMETRY_SEGMENTS downTo 1') 'rotated telemetry baseline must read older segments before newer segments.'
Assert-True ($prootTelemetryStore -match 'lastOffsetBytes = currentLength') 'rotated telemetry baseline must advance the active file offset to avoid replaying the same tail every refresh.'
Assert-True ($prootTelemetryStore -match 'belongsToRotationBaseline') 'rotated telemetry baseline must have a bounded time window instead of replaying arbitrary history.'
Assert-True ($prootTelemetryStore -match 'fun retireOwnerTracees') 'PRoot telemetry store must support owner tombstones after confirmed control-plane stop.'
Assert-True ($prootTelemetryStore -match 'RETIRE_PROOT_OWNER_TRACEES' -and $prootTelemetryStore -match 'kite_owner_retire_') 'owner tombstones must be written as explicit same-source telemetry events.'
Assert-True ($prootTelemetryAll -match 'ProotTelemetryEventType\.TraceeExited\.name' -and $prootTelemetryAll -match 'android_control_plane_owner_tombstone_same_telemetry_source_no_proc_scan') 'owner tombstones must retire running tracees without falling back to /proc scans.'
Assert-True ($runtimeHealthStore -match 'CARD\("\u5361\u7247"\)' -and $runtimeHealthStore -match 'RESOURCE\("\u8d44\u6e90"\)') 'RuntimeHealth must model card/resource owner roots explicitly.'
Assert-True ($runtimeHealthStore -match 'private fun buildProotOwnerRoots') 'RuntimeHealth must build owner roots from PRoot owner index.'
Assert-True ($runtimeHealthStore -match 'prootTelemetry\.ownerProcessIndex\.groups') 'RuntimeHealth owner roots must consume the owner process index.'
Assert-True ($runtimeHealthStore -match 'PRoot \u5bb9\u5668\u5165\u53e3' -and $runtimeHealthStore -match 'RuntimeProcessUnitTier\.PROOT_CORE') 'RuntimeHealth must classify PRoot entry processes as named system support units.'
Assert-True ($runtimeHealthStore -match 'existingTerminalOwnerIds' -and $runtimeHealthStore -match 'excludedOwnerIds: Set<String> = emptySet\(\)') 'RuntimeHealth must skip PRoot terminal owner roots already represented by terminal sessions.'
Assert-True ($runtimeHealthStore -match 'val attributedOwnerIds = ownerRoots\s*\r?\n\s*\.mapNotNull \{ it\.ownerId \}\s*\r?\n\s*\.toSet\(\)' -and $runtimeHealthStore -match '\.filter \{ group -> group\.ownerId in attributedOwnerIds \|\| group\.ownerId in existingTerminalOwnerIds \}') 'RuntimeHealth must keep hidden terminal owner tracees attributed while not hiding unknown owner prefixes.'
Assert-True ($runtimeHealthStore -match 'attributedRootPids = attributedPids \+ ownerTraceePids') 'owner tracees must be excluded from unattributed root generation.'
Assert-True ($runtimeHealthStore -match 'runtime_reclaimer_owner_reclaim_mode=explicit_owner_id_only' -and $runtimeHealthStore -match 'runtime_reclaimer_owner_process_terminate_request_count') 'RuntimeHealth env output must declare explicit-only owner reclaim and expose owner terminate count.'
Assert-True ($runtimeWorkloadRegistry -match 'proot_owner_index:\$\{ownerKind\.name\.lowercase\(\)\}') 'workload registry must classify card/resource owner roots from the PRoot owner index.'
Assert-True ($taskManagerStore -match '\u5361\u7247\u5bb9\u5668' -and $taskManagerStore -match '\u8d44\u6e90\u5bb9\u5668') 'task manager must surface card/resource roots as owner containers.'
Assert-True ($taskManagerStore -match 'val runtimeOwnerId: String\? = null') 'task manager process items must carry the raw runtime owner id.'
Assert-True ($taskManagerStore -match 'val runtimeUnitId: String\? = null') 'task manager process items must carry the raw runtime unit id.'
Assert-True ($taskManagerStore -match 'runtimeOwnerId = ownerId') 'task manager root items must expose the RuntimeHealth owner id.'
Assert-True ($taskManagerStore -match 'runtimeUnitId = processUnitId' -and $taskManagerStore -match 'runtimeUnitId = runtimeUnitId') 'task manager rows must preserve RuntimeHealth and PRoot unit ids.'
Assert-True ($taskManagerStore -match 'private fun ProotLiveProcessEntry\.terminalOwnerSessionId' -and $taskManagerStore -match 'linkedTerminalSessionId = terminalSessionId') 'task manager PRoot process rows must preserve terminal owner session ids.'
Assert-True ($taskManagerStore -match 'private fun ProotLiveProcessEntry\.runtimeOwnerKindLabel' -and $taskManagerStore -match 'runtimeOwnerKindLabel = ownerEntry\.runtimeOwnerKindLabel\(\)') 'task manager PRoot process rows must expose owner kind labels.'
Assert-True ($taskManagerStore -match 'private fun stabilizeSnapshot' -and $taskManagerStore -match 'EMPTY_PROCESS_GRACE_MS') 'task manager snapshots must smooth transient empty collector gaps.'
Assert-True ($taskManagerStore -match 'private fun ProotLiveProcessEntry\.ownerSource' -and $taskManagerStore -match 'entriesByPid\[parentPid\]' -and $taskManagerStore -match 'val ownerEntry = ownerSource\(entriesByPid\)') 'task manager PRoot rows must inherit owner identity from parent tracees when child events are missing tags.'
$runManagementGroups = Function-Body $main 'buildRunManagementGroups'
Assert-True ($runManagementGroups -match 'runtimeOwnerIdForRunManagement\(\)' -and $runManagementGroups -match 'runtimeUnitIdForRunManagement\(\)' -and $runManagementGroups -match 'belongsToRun\(run, boundPids, ownerId, unitId\)') 'run management groups must match CardRun rows by owner and unit id, not only pid bindings.'
$belongsToRun = Function-Body $main 'TaskManagerProcessItem.belongsToRun'
Assert-True ($belongsToRun -match 'runtimeOwnerId == ownerId') 'CardRun process grouping must consume the TaskManager owner fact source.'
Assert-True ($belongsToRun -match 'runtimeUnitId == unitId') 'CardRun process grouping must consume the TaskManager unit fact source.'
Assert-True ($main -match 'private fun RecipeRuntimeState\.runtimeOwnerIdForRunManagement') 'CardRun run management must derive the expected PRoot owner id.'
Assert-True ($main -match 'private fun RecipeRuntimeState\.runtimeUnitIdForRunManagement') 'CardRun run management must derive the expected PRoot unit id.'
Assert-True ($prootPoolPlan -match 'val ownerContainerCount: Int') 'PRoot pool plan must expose owner container count.'
Assert-True ($prootPoolPlan -match 'val ownerContainerTraceeCount: Int') 'PRoot pool plan must expose owner tracee count.'
Assert-True ($prootPoolPlan -match 'entry\.ownerKind == RuntimeRootOwnerKind\.CARD' -and $prootPoolPlan -match 'entry\.ownerKind == RuntimeRootOwnerKind\.RESOURCE') 'PRoot pool plan must derive owner container pressure from card/resource owner roots.'
Assert-True ($prootPoolPlan -match 'proot_pool_plan_owner_container_count' -and $prootPoolPlan -match 'proot_pool_plan_owner_container_tracee_count') 'PRoot pool env output must include owner container pressure.'
Assert-True ($runtimeMemoryLifecycleRuleTrigger -match 'prootRule\(snapshot, now\)\?\.let\(records::add\)' -and $runtimeMemoryLifecycleRuleTrigger -match 'records\.hasProotCapacityActuatorRequest\(\)') 'PRoot capacity actuator must still receive approved PRoot pool actions when generic lifecycle reclaim is disabled.'
Assert-True ($backgroundRuntimeRegistry -match 'PROOT_CAPACITY_WORKER_INITIAL_COUNT = 2') 'PRoot capacity registry must pre-register an inactive second worker for auto-bound scale-out.'
Assert-True ($prootOwnerTerminator -match 'object ProotOwnerProcessTerminator') 'owner stop must have a dedicated PRoot owner terminator.'
Assert-True ($prootOwnerTerminator -match 'ProotTelemetryStore\.refreshBlocking') 'owner stop must use blocking telemetry refresh for residue checks.'
Assert-True ($prootOwnerTerminator -match 'WorkSurfaceRuntimeBridge\.buildShellExecConfig' -and $prootOwnerTerminator -match 'buildUbuntuOwnerKillPayload' -and $prootOwnerTerminator -match 'OWNER_STOP_DEADLINE_MS = 10_000L') 'owner stop must run the bounded KO transaction inside Ubuntu.'
Assert-True ($prootOwnerTerminator -match 'kf_collect_tree' -and $prootOwnerTerminator -match 'kf_kill_pids' -and $prootOwnerTerminator -match 'kill -KILL' -and $prootOwnerTerminator -notmatch 'kf_kill_groups' -and $prootOwnerTerminator -notmatch 'kill -KILL -- "-') 'owner stop must kill the Ubuntu tracee pid tree, not broad process groups.'
Assert-True ($prootOwnerTerminator -match 'ProotTelemetryStore\.retireOwnerTracees' -and $prootOwnerTerminator -match 'probeUbuntuLiveTracees') 'owner stop must tombstone stale telemetry only after Ubuntu /proc checks show tracee pids are gone.'
Assert-True ($prootOwnerTerminator -notmatch 'Os\.kill' -and $prootOwnerTerminator -notmatch 'sendSignal\(-') 'owner stop must not manage owner processes through Android platform signals.'
Assert-True ($prootOwnerTerminator -match '__kite_owner_stop_owner' -and $prootOwnerTerminator -match '__kite_stop_remaining') 'owner stop must report owner and final remaining tracees.'
Assert-True ($runtimeReclaimer -match 'fun reclaimOwnerRuntime' -and $runtimeReclaimer -match 'ProotOwnerProcessTerminator\.terminate') 'RuntimeReclaimer explicit owner reclaim must terminate through the PRoot owner terminator.'
Assert-True ($runtimeReclaimer -match 'ownerProcessTerminateRequestCount' -and $runtimeReclaimer -match 'explicit_owner_process_terminate') 'RuntimeReclaimer must record explicit owner process terminate requests.'
Assert-True ($runtimeReclaimer -match '(?s)private fun String\.isExplicitOwnerReclaimId\(\).*startsWith\("card:"\).*startsWith\("resource:"\).*startsWith\("terminal:"\)') 'RuntimeReclaimer owner reclaim must reject non card/resource/terminal owner ids.'
Assert-True ($runtimeReclaimer -match 'RuntimeRootOwnerKind\.CARD,\s*\r?\n\s*RuntimeRootOwnerKind\.RESOURCE,\s*\r?\n\s*RuntimeRootOwnerKind\.TERMINAL -> false') 'RuntimeReclaimer automatic candidate selection must not auto-reclaim interactive owner roots.'
Assert-True ($bridgeClient -match 'ProotOwnerProcessTerminator\.terminate') 'bridge stop must invoke the PRoot owner terminator.'
Assert-True ($bridgeClient -match 'private fun stopOwnerProcesses') 'bridge stop must collect owner stop output centrally.'
Assert-True ($terminalSessionController -match 'private fun stopTerminalOwnerProcesses') 'terminal stop must have a dedicated owner stop hook.'
Assert-True ($terminalSessionController -match 'ProotOwnerProcessTerminator\.terminate\(appContext, ownerId\)') 'terminal stop must invoke the PRoot owner terminator.'
Assert-True ($terminalSessionController.Contains('"terminal:$it"')) 'terminal stop must derive the owner id from the terminal session id.'
Assert-True ($terminalSessionController -match 'attachingSessionIds' -and $terminalSessionController -match 'waitForSessionAttach') 'terminal session attach must be idempotent so a terminal owner cannot be split across duplicate PRoot sessions.'
Assert-True ($terminalSessionController -match 'retireStoppedTerminalOwnerTracees' -and $terminalSessionController -match 'terminal-host-process-exited' -and $terminalSessionController -match 'outcome\.exited') 'terminal stop must tombstone owner tracees only after host process exit is confirmed.'
Assert-True ($bridgeClient -match 'lastOrNull\(\).*substringAfter' -or $bridgeClient -match '(?s)filter \{ it\.startsWith\("__kite_stop_remaining:"\) \}.*lastOrNull\(\)') 'bridge residue parsing must use the final stop remaining marker.'
Assert-True ($androidRecipeExecutor -match '(?s)filter \{ it\.startsWith\("__kite_stop_remaining:"\) \}.*lastOrNull\(\)') 'Execution adapter residue parsing must use the final stop remaining marker.'
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
Assert-True ($androidRecipeFeatureGateway -match 'private fun mustStopBeforeDelete' -and $androidRecipeFeatureGateway -match 'RecipeDeleteResult\.RequiresStop') 'delete recipe flow must have an active-run stop guard.'
$deleteRecipe = Function-Body $recipeEditorController 'delete'
Assert-True ($deleteRecipe -match 'RecipeEditorEffect\.DeleteRequiresStop' -and $deleteRecipe -match 'KiteRecipeActionIntent\.Stop' -and $deleteRecipe -match 'instanceId = result\.run\.instanceId') 'delete recipe flow must stop an active card run before deleting the recipe.'
Assert-True ($deleteRecipe -match 'when \(result\)' -and $deleteRecipe -match 'RecipeDeleteResult\.Deleted') 'delete recipe flow must not delete active recipes in the same action after requesting stop.'
Assert-True ($cardRunStore -match 'fun removeClosedRunStatesForRecipes') 'card deletion cleanup must use a closed-run-only CardRunStore entry.'
Assert-True ($cardRunStore -match 'activeInstanceIds' -and $cardRunStore -match 'status\.endsHistoryEntry\(\)' -and $cardRunStore -match 'entry\.isClosed\(\)') 'closed-run cleanup must preserve active instances while removing ended run state/history.'
Assert-True ($androidRecipeFeatureGateway -match 'removeClosedRunStatesForRecipes\(listOf\(recipeId\)\)') 'delete recipe flow must clean only closed CardRun state for the deleted card.'
Assert-True ($main -match 'cleanCardRunPidDirs\(request\.removedCardInstanceIds\)') 'delete recipe flow must request pid directory cleanup for removed cardInstanceIds.'
Assert-True ($bridgeClient -match 'fun cleanCardRunPidDirs') 'bridge client must expose card pid directory cleanup.'
Assert-True ($bridgeClient -match 'private fun cleanCardRunPidDirPayload') 'pid directory cleanup must derive its payload from a cardInstanceId.'
Assert-True ($bridgeClient.Contains('rm -rf -- ${shellQuote(dir)}')) 'pid directory cleanup must delete only the derived card-run pid directory.'
Assert-True ($bridgeClient -match '__kite_pid_dir_cleaned') 'pid directory cleanup must report the cleaned directory path.'
Assert-True ($cardRunStore -match 'PROCESS_RESTORE_ABORTED_MESSAGE') 'process-restore abnormal exits must use one explicit CardRunStore message.'
Assert-True ($cardRunStore -match 'status = CardRunStatus\.Failed' -and $cardRunStore -match 'lastError = lastError \?: PROCESS_RESTORE_ABORTED_MESSAGE') 'process-restore unfinished runs must be classified as abnormal, not normal stopped.'
Assert-True ($cardRunStore -match 'normalizedHistoryAfterProcessRestore' -and $cardRunStore -match 'error = error\.ifBlank \{ PROCESS_RESTORE_ABORTED_MESSAGE \}') 'process-restore history must preserve an abnormal-exit error.'
Assert-True ($cardRunStore -match 'shouldIgnoreStoppedRuntimeWrite' -and $cardRunStore -match 'this\.status != CardRunStatus\.Stopped' -and $cardRunStore -match 'CardRunStatus\.Running') 'CardRunStore must reject stale runtime writes that try to revive a stopped card.'

$stopRecipe = Function-Body $main 'stopRecipe'
$stopRecipeByCardInstanceId = Function-Body $main 'stopRecipeByCardInstanceId'
$stopRecipeWithOrchestrator = Function-Body $main 'stopRecipeWithOrchestrator'
Assert-True (
    $stopRecipe -match 'stopRecipeByCardInstanceId\s*\(' -and
    $stopRecipe -match 'previousState\.cardInstanceId'
) 'stopRecipe must delegate to the cardInstanceId stop entry.'
Assert-True ($stopRecipeByCardInstanceId -match 'CardRunStore\.get\(cardInstanceId\)') 'stop(cardInstanceId) must resolve the latest CardRunStore state.'
Assert-True ($stopRecipeByCardInstanceId -match 'stopRecipeWithOrchestrator' -and $stopRecipeByCardInstanceId -notmatch 'legacyStopRecipeByCardInstanceId|recipeUsesProcessRunOrchestrator') 'All stop intake must submit through the process orchestrator.'
Assert-True ($stopRecipeWithOrchestrator -match 'activeRunInstanceIds\[recipe\.id\] = previousState\.instanceId' -and $stopRecipeWithOrchestrator -match 'runOrchestrator\.stop\(previousState\.instanceId\)') 'Migrated stop must bind to the resolved CardRun instance and submit through RunOrchestrator.'
Assert-True ($stopRecipeWithOrchestrator -notmatch 'bridgeClient\.|TerminalRuntimeHost\.|setRuntimeState\(') 'Migrated stop intake must not bypass the orchestrator to mutate facts or execution resources.'
Assert-True ($main -match 'is RunExecutionEffect\.StopResolved -> handleRunStopResolvedEffect\(effect\)') 'Stop resolution must return to the visible shell through the shared execution Effect contract.'
Assert-True ($stopCoordinator -match 'RecipeStopRequest' -and $stopCoordinator -match 'terminalSessionId == null' -and $stopCoordinator -match 'hasBridgeProcessBinding') 'StopCoordinator must distinguish terminal-only state from retained process bindings.'
Assert-True ($androidRecipeExecutor -match 'bridgeClient\.stopProcessBinding' -and $androidRecipeExecutor -match 'cardInstanceId = request\.instanceId') 'Execution adapter must stop retained process bindings with card ownership identity.'
Assert-True ($stopCoordinator -match 'remaining\.isNotEmpty\(\)' -and $stopCoordinator -match '\u505c\u6b62\u540e\u4ecd\u6709\u8fdb\u7a0b\u6b8b\u7559') 'Stop failure must keep process residue visible in the run fact.'
Assert-True ($stopCoordinator -match 'residueMarkerObserved' -and $stopCoordinator -match '\u5df2\u505c\u6b62\uff0c\u672a\u53d1\u73b0\u8fdb\u7a0b\u6b8b\u7559') 'Stop success must require an explicit empty-residue result when the marker is present.'
Assert-True ($androidRecipeExecutor -match 'manualKillObserved = MANUAL_STOP_KILLED_REGEX' -and $stopCoordinator -match 'result\.manualKillObserved') 'Manual Killed output must be interpreted by the execution and stop contracts instead of page callbacks.'
Assert-True ($runOrchestrator -match 'current\.createdAt != previousState\.createdAt \|\| current\.status != CardRunStatus\.Stopping') 'Late stop callbacks must be rejected against the current run generation and state.'
Assert-True ($main -notmatch 'legacyStopRecipeByCardInstanceId|handleStopResultV2|bridgeFailureArrivedAfterManualStop|stopRemainingProcesses') 'MainActivity must not retain a second stop result interpreter.'

$cardRunRestoreDrop = Function-Body $cardRunStore 'CardRunState.shouldDropCurrentAfterProcessRestore'
Assert-True ($cardRunRestoreDrop -match 'status\.endsHistoryEntry') 'restored ended card-run states should stay out of the current run list.'
Assert-True ($cardRunRestoreDrop -match 'hasRunBinding') 'restored run bindings should stay out of the current run list.'

$runManagementCard = Function-Body $main 'runManagementCard'
Assert-True ($runManagementCard -match 'setOnClickListener \{ toggleRunManagementCard') 'run management row card should expand from the whole card click.'
Assert-True ($runManagementCard -match 'LinearLayout\.LayoutParams\(dp\(34\), dp\(34\)\)') 'run management card icon should stay compact like install wizard rows.'
Assert-True ($runManagementCard -notmatch '\u505c\u6b62\u5361\u7247|runManagementActionButton|runManagementSummary|processCount') 'run management card should not expose heavy counts or action buttons in the collapsed row.'

$runManagementDetails = Function-Body $main 'runManagementDetails'
Assert-True ($runManagementDetails -notmatch 'runManagementOwnershipRows|runManagementExitSummary') 'expanded run management card must not show duplicated internal ownership debug rows.'
Assert-True ($runManagementDetails -match 'runManagementSurfaceItems') 'expanded run management card should derive SH/terminal/web rows from existing card-run surfaces.'
Assert-True ($runManagementDetails -match 'CardRunSurface\.Report') 'expanded run management card should surface SH report when available.'
Assert-True ($runManagementDetails -match 'CardRunSurface\.Terminal') 'expanded run management card should surface terminal rows when available.'
Assert-True ($runManagementDetails -match 'CardRunSurface\.Web') 'expanded run management card should surface web rows when available.'
Assert-True ($main -notmatch 'private fun runManagementOwnershipRows|private fun runManagementExitSummary') 'run management should not keep dead helpers for removed debug ownership rows.'
Assert-True ($main -match 'private fun RunManagementGroup\.runManagementProcessPreview' -and $main -match '\u4e3b\u8fdb\u7a0b PID' -and $main -match '\u5b50\u8fdb\u7a0b') 'run management process preview should summarize PID/count instead of repeating the command line.'

$showManage = Function-Body $main 'showResourceManage'
Assert-True ($showManage -match 'showResourceFeatureFragment' -and $showManage -match 'ResourceManageFragment') 'showResourceManage must route to the owning resource feature.'
Assert-True ($main -notmatch 'ResourceManageHost|renderResourceManageInto|requestResourceManageRefresh') 'resource management must not delegate rendering or refresh back to MainActivity.'
Assert-True ($resourceManageFragment -match 'ResourceFeatureFragment' -and $resourceManageFragment -match 'observeResourceState') 'resource management must observe the shared resource feature state.'
Assert-True ($resourceManageScreen -notmatch 'resourceCatalog\(|planSnapshot\(|registrySnapshot\(|KiteResourceInstallStore') 'resource management screen must only project supplied UI state.'

Assert-True ($resourceInstallWizardPresentation -match 'KiteResourceInstallStepUiProjector\.project') 'install wizard rows must consume the shared step projection.'
Assert-True ($resourceInstallWizardScreen -match 'row\.projection\.statusLabel' -and $resourceInstallWizardScreen -notmatch 'statusLabel\s*=\s*when') 'install wizard rows must render the shared projection without a parallel status-label decision tree.'
Assert-True ($resourceInstallWizardScreen -notmatch 'CardRunStore|KiteResourceInstallStore|MainActivity') 'install wizard screen must not read runtime stores or the shell directly.'
Assert-True ($main -notmatch 'ResourceInstallWizardBinding|ResourceInstallWizardUiState|requestVisibleResourceInstallWizardRefresh|resourceInstallWizardContent') 'MainActivity must not retain the legacy install-wizard render and refresh chain.'
$settleResourceMutation = Function-Body $main 'settleVisibleResourceMutation'
Assert-True ($settleResourceMutation -notmatch '\bshowResources\s*\(') 'background resource mutations must not navigate users away from the current screen.'
Assert-True ($settleResourceMutation -match 'resourceCatalogDirty\s*=\s*true') 'background resource mutations must mark hidden resource surfaces dirty.'
$resourceIncrementalFacts = Function-Body $main 'resourceRuntimeFactsFromStore'
$resourceCatalogFacts = Function-Body $main 'resourceCatalog'
Assert-True ($resourceIncrementalFacts -match 'KiteResourceRuntimeFactsProjector\.project') 'incremental resource binding must consume shared runtime facts.'
Assert-True ($resourceCatalogFacts -match 'KiteResourceRuntimeFactsProjector\.project') 'full resource catalog must consume shared runtime facts.'
$runManagementStatusColors = Function-Body $main 'runManagementStatusColors'
Assert-True ($homeFeatureViewSupport -match 'item\.projection\.primaryAction' -and $homeFeatureViewSupport -match 'item\.projection\.badgeLabel') 'Home card action and badge must consume the shared projected UI state.'
Assert-True ($runManagementStatusColors -match 'KiteCardRunUiProjector\.project') 'run management status colors must consume shared card-run UI state.'
$releaseActivitySurfaces = Function-Body $main 'releaseActivityDisplaySurfaces'
$releaseInstallWizardSurface = Function-Body $main 'releaseResourceInstallWizardSurfaceIfActivityDestroyed'
Assert-True ($releaseActivitySurfaces -match 'webView\.destroy\(\)' -and $releaseActivitySurfaces -match 'browserAutomationController\.closeActiveSession\(\)') 'Activity destroy must release its WebView and automation display session.'
Assert-True ($releaseActivitySurfaces -notmatch 'stopRecipe|CardRunStore|TerminalRuntimeHost\.release|clearPlan') 'display-surface release must not stop runs, terminals, or install plans.'
Assert-True ($releaseInstallWizardSurface -notmatch 'stopResourceInstallRunsForCancel|clearPlan|markFailed') 'Activity destroy must not treat the install wizard surface as a stopped or failed install task.'
$terminalDestroyView = Member-Function-Body $terminalFragment 'onDestroyView'
Assert-True ($terminalDestroyView -match 'TerminalRuntimeHost\.detachUi\(this\)') 'Terminal view destroy must detach the UI from the process-level session host.'
Assert-True ($terminalDestroyView -notmatch 'TerminalRuntimeHost\.release|TerminalRuntimeHost\.endSession|stopCurrentSession') 'Terminal view destroy must not stop or release the terminal session.'
Assert-True ($runtimeReclaimer -match 'RuntimeRootOwnerKind\.CARD,\s*RuntimeRootOwnerKind\.RESOURCE,\s*RuntimeRootOwnerKind\.TERMINAL\s*->\s*false') 'Cards, resources, and terminal owners must remain excluded from generic automatic reclaim.'
$lowMemoryResponse = Member-Function-Body $runtimePressureResponder 'onLowMemory'
$pressureHandler = Function-Body $runtimePressureResponder 'handlePressure'
Assert-True ($lowMemoryResponse -match 'RuntimeLifecycleSignalStore\.onLowMemory\(\)' -and $lowMemoryResponse -notmatch '\bonTrimMemory\s*\(') 'Low-memory handling must preserve its own lifecycle fact instead of rewriting it as a regular trim event.'
Assert-True ($pressureHandler -match 'RuntimeFrameCoordinator\.refreshTaskManager|RuntimeFrameCoordinator\.refreshProcessSnapshot') 'Memory pressure must enter the existing runtime snapshot refresh chain.'
Assert-True ($pressureHandler -notmatch 'destroyProcess|terminateForRuntimeReclaimer|stopRecipe|endSession') 'Memory callbacks must not directly terminate runs, processes, or terminal sessions.'
$renderTerminalPanel = Function-Body $terminalFragment 'renderTerminalPanelPage'
$buildTerminalPanel = Function-Body $terminalFragment 'buildTerminalPanelPage'
Assert-True ($renderTerminalPanel -match 'TerminalPanelActionRegistry\.snapshot\(\)') 'Terminal panel pages must come from the action registry.'
Assert-True ($buildTerminalPanel -match 'action\.handler\.execute\(terminalPanelActionHost, anchor\)') 'Terminal panel tiles must execute registered actions through the terminal host contract.'
Assert-True ($terminalPanelActionRegistry -match 'fun register\(' -and $terminalPanelActionRegistry -match 'fun unregister\(') 'Terminal panel registry must expose stable extension operations.'
$startupMarkStage = Member-Function-Body $startupTraceStore 'markStage'
$startupMarkReady = Member-Function-Body $startupTraceStore 'markReady'
Assert-True ($startupMarkStage -match '\.apply\(\)' -and $startupMarkStage -notmatch '\.commit\(\)') 'Routine startup stage tracing must not synchronously flush SharedPreferences on the startup thread.'
Assert-True ($startupMarkReady -match '\.commit\(\)') 'First-frame ready must remain synchronously durable across process restarts.'
$mainOnResume = Member-Function-Body $main 'onResume'
$resumeConsoleSurface = Function-Body $main 'resumeConsoleSurface'
Assert-True ($mainOnResume -match 'AppDestination\.Console -> resumeConsoleSurface\(\)') 'Activity resume must calibrate an existing Console surface instead of rebuilding it unconditionally.'
Assert-True ($resumeConsoleSurface -match 'findFragmentByTag\(TAG_HOME_FRAGMENT\)' -and $resumeConsoleSurface -match 'refreshConsoleRuntimeChrome\(\)' -and $resumeConsoleSurface -notmatch 'loadAllRecipes|prepareDropZone') 'Console resume must reuse HomeFragment and reconcile local chrome without synchronous catalog scans.'
Assert-True ($resourceInstallRecipes -match 'WORKSPACE_SHARED_CACHE_ROOT' -and $resourceInstallRecipes -match '\$WORKSPACE_SHARED_CACHE_ROOT/\$packId') 'Bundled resources must resolve one shared toolchain-pack cache.'
Assert-True ($resourceInstallRecipes -match 'fun resourceCachePath' -and $resourceInstallRecipes -notmatch 'localPackPath\([^\)]*\)\.substringBeforeLast') 'Resource-private cache cleanup must not be derived from the shared pack path.'
Assert-True ($toolchainPackInstaller -match 'mirrorPackIntoSharedResourceCache' -and $toolchainPackInstaller -match 'cleanupLegacyResourcePackCopies') 'Toolchain staging must publish one shared pack and migrate legacy per-resource copies.'
$extractRuntimePack = Function-Body $toolchainPackInstaller 'extractRuntimePack'
Assert-True ($extractRuntimePack -match 'bundledPackDirectoryIsComplete' -and $extractRuntimePack -match '\$PACK_ID\.pending') 'Bundled pack extraction must reuse a complete pack and publish replacements through a pending directory.'

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
    $signalMethod = Member-Function-Body $store $reason
    Assert-True (-not [string]::IsNullOrWhiteSpace($signalMethod)) "install store must define $reason."
    Assert-True ($signalMethod -match '\bemitSignal\s*\(') "install store must emit signal for $reason."
}

if ($failures.Count -gt 0) {
    Write-Host 'Kite runtime lane static checks failed:' -ForegroundColor Red
    foreach ($failure in $failures) {
        Write-Host " - $failure" -ForegroundColor Red
    }
    exit 1
}

& (Join-Path $PSScriptRoot 'KITE_ARCHITECTURE_CHECKS.ps1') -Root $Root
Write-Host 'Kite runtime lane static checks passed.' -ForegroundColor Green
