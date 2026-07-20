param(
    [string]$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
)

$ErrorActionPreference = 'Stop'

$mainPath = Join-Path $Root 'app/src/main/java/com/kite/app/MainActivity.kt'
$cardRunActivityPath = Join-Path $Root 'app/src/main/java/com/kite/app/CardRunActivity.kt'
$storePath = Join-Path $Root 'app/src/main/java/com/kite/app/resources/KiteResourceInstallStore.kt'
$bridgeClientPath = Join-Path $Root 'app/src/main/java/com/kite/app/bridge/KiteBridgeClient.kt'
$ownerStopOutputEvidencePath = Join-Path $Root 'app/src/main/java/com/kite/app/bridge/OwnerStopOutputEvidence.kt'
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
$runStepActionPolicyPath = Join-Path $Root 'app/src/main/java/com/kite/app/application/runs/RunStepActionPolicy.kt'
$runNotificationContractPath = Join-Path $Root 'app/src/main/java/com/kite/app/application/runs/RunNotificationContract.kt'
$recipeActionWorkflowPath = Join-Path $Root 'app/src/main/java/com/kite/app/application/runs/RecipeActionWorkflow.kt'
$runExecutionEffectBusPath = Join-Path $Root 'app/src/main/java/com/kite/app/application/runs/RunExecutionEffectBus.kt'
$runLifecycleEventHubPath = Join-Path $Root 'app/src/main/java/com/kite/app/application/runs/RunLifecycleEventHub.kt'
$stopCoordinatorPath = Join-Path $Root 'app/src/main/java/com/kite/app/application/runs/StopCoordinator.kt'
$androidRecipeExecutorPath = Join-Path $Root 'app/src/main/java/com/kite/app/platform/runs/AndroidRecipeExecutor.kt'
$androidRecipeActionGatewayPath = Join-Path $Root 'app/src/main/java/com/kite/app/platform/runs/AndroidRecipeActionGateway.kt'
$desktopOpenWorkflowPath = Join-Path $Root 'app/src/main/java/com/kite/app/application/runs/DesktopOpenWorkflow.kt'
$androidDesktopOpenGatewayPath = Join-Path $Root 'app/src/main/java/com/kite/app/platform/runs/AndroidDesktopOpenGateway.kt'
$runtimeOwnerProbeWorkflowPath = Join-Path $Root 'app/src/main/java/com/kite/app/application/runs/RuntimeOwnerProbeWorkflow.kt'
$androidRuntimeOwnerProbeGatewayPath = Join-Path $Root 'app/src/main/java/com/kite/app/platform/runs/AndroidRuntimeOwnerProbeGateway.kt'
$androidRunStateGatewayPath = Join-Path $Root 'app/src/main/java/com/kite/app/platform/runs/AndroidRunStateGateway.kt'
$androidRunNotificationCoordinatorPath = Join-Path $Root 'app/src/main/java/com/kite/app/platform/runs/AndroidRunNotificationCoordinator.kt'
$resourceRunCoordinatorPath = Join-Path $Root 'app/src/main/java/com/kite/app/application/resources/ResourceRunCoordinator.kt'
$resourceActionWorkflowPath = Join-Path $Root 'app/src/main/java/com/kite/app/application/resources/ResourceActionWorkflow.kt'
$androidResourceRecipeFactoryPath = Join-Path $Root 'app/src/main/java/com/kite/app/platform/resources/AndroidResourceRecipeFactory.kt'
$androidResourceRunGatewayPath = Join-Path $Root 'app/src/main/java/com/kite/app/platform/resources/AndroidResourceRunGateway.kt'
$androidResourceActionGatewayPath = Join-Path $Root 'app/src/main/java/com/kite/app/platform/resources/AndroidResourceActionGateway.kt'
$runSurfaceContractPath = Join-Path $Root 'app/src/main/java/com/kite/app/feature/runsurface/RunSurfaceContract.kt'
$runSurfaceControllerPath = Join-Path $Root 'app/src/main/java/com/kite/app/feature/runsurface/RunSurfaceController.kt'
$runSurfaceHostPath = Join-Path $Root 'app/src/main/java/com/kite/app/feature/runsurface/RunSurfaceHost.kt'
$runActivityChromePath = Join-Path $Root 'app/src/main/java/com/kite/app/feature/runsurface/RunActivityChrome.kt'
$runWindowOverviewScreenPath = Join-Path $Root 'app/src/main/java/com/kite/app/feature/runsurface/RunWindowOverviewScreen.kt'
$runReportScreenPath = Join-Path $Root 'app/src/main/java/com/kite/app/feature/runsurface/RunReportScreen.kt'
$runTerminalSurfaceBindingPath = Join-Path $Root 'app/src/main/java/com/kite/app/feature/runsurface/RunTerminalSurfaceBinding.kt'
$runWebSurfaceBindingPath = Join-Path $Root 'app/src/main/java/com/kite/app/feature/runsurface/RunWebSurfaceBinding.kt'
$runX11SurfaceBindingPath = Join-Path $Root 'app/src/main/java/com/kite/app/feature/runsurface/RunX11SurfaceBinding.kt'
$runInstallWizardSurfaceBindingPath = Join-Path $Root 'app/src/main/java/com/kite/app/shell/RunInstallWizardSurfaceBinding.kt'
$browserHandoffCoordinatorPath = Join-Path $Root 'app/src/main/java/com/kite/app/application/browser/BrowserHandoffCoordinator.kt'
$browserAuthRedirectCoordinatorPath = Join-Path $Root 'app/src/main/java/com/kite/app/application/browser/BrowserAuthRedirectCoordinator.kt'
$androidBrowserHandoffGatewayPath = Join-Path $Root 'app/src/main/java/com/kite/app/platform/browser/AndroidBrowserHandoffGateway.kt'
$androidBrowserAuthRedirectGatewayPath = Join-Path $Root 'app/src/main/java/com/kite/app/platform/browser/AndroidBrowserAuthRedirectGateway.kt'
$androidBrowserAutomationRunUpdaterPath = Join-Path $Root 'app/src/main/java/com/kite/app/platform/browser/AndroidBrowserAutomationRunUpdater.kt'
$browserOpenWorkflowPath = Join-Path $Root 'app/src/main/java/com/kite/app/application/browser/BrowserOpenWorkflow.kt'
$androidBrowserOpenGatewayPath = Join-Path $Root 'app/src/main/java/com/kite/app/platform/browser/AndroidBrowserOpenGateway.kt'
$installApkWorkflowPath = Join-Path $Root 'app/src/main/java/com/kite/app/application/packages/InstallApkWorkflow.kt'
$androidInstallApkGatewayPath = Join-Path $Root 'app/src/main/java/com/kite/app/platform/packages/AndroidInstallApkGateway.kt'
$webWorkbenchFragmentPath = Join-Path $Root 'app/src/main/java/com/kite/app/feature/web/WebWorkbenchFragment.kt'
$webWorkbenchScreenPath = Join-Path $Root 'app/src/main/java/com/kite/app/feature/web/WebWorkbenchScreen.kt'
$runtimeManagementCoordinatorPath = Join-Path $Root 'app/src/main/java/com/kite/app/application/runtimemanagement/RuntimeManagementCoordinator.kt'
$runtimeManagementGatewayPath = Join-Path $Root 'app/src/main/java/com/kite/app/application/runtimemanagement/RuntimeManagementGateway.kt'
$androidRuntimeManagementGatewayPath = Join-Path $Root 'app/src/main/java/com/kite/app/platform/runtimemanagement/AndroidRuntimeManagementGateway.kt'
$runtimeManagementControllerPath = Join-Path $Root 'app/src/main/java/com/kite/app/feature/runtimemanagement/RuntimeManagementFeatureController.kt'
$runtimeManagementProjectorPath = Join-Path $Root 'app/src/main/java/com/kite/app/feature/runtimemanagement/RuntimeManagementProjector.kt'
$instanceRuntimeTopologyPath = Join-Path $Root 'app/src/main/java/com/kite/app/application/runtimemanagement/InstanceRuntimeTopology.kt'
$runtimeManagementFragmentPath = Join-Path $Root 'app/src/main/java/com/kite/app/feature/runtimemanagement/RuntimeManagementFragment.kt'
$runtimeManagementScreenPath = Join-Path $Root 'app/src/main/java/com/kite/app/feature/runtimemanagement/RuntimeManagementScreen.kt'
$runtimeBootstrapSnapshotPath = Join-Path $Root 'app/src/main/java/com/kite/app/application/runtimebootstrap/RuntimeBootstrapSnapshot.kt'
$runtimeBootstrapGatewayPath = Join-Path $Root 'app/src/main/java/com/kite/app/application/runtimebootstrap/RuntimeBootstrapGateway.kt'
$androidRuntimeBootstrapGatewayPath = Join-Path $Root 'app/src/main/java/com/kite/app/platform/runtimebootstrap/AndroidRuntimeBootstrapGateway.kt'
$runtimeStatusProjectorPath = Join-Path $Root 'app/src/main/java/com/kite/app/feature/runtimebootstrap/RuntimeStatusProjector.kt'
$runtimeStatusControllerPath = Join-Path $Root 'app/src/main/java/com/kite/app/feature/runtimebootstrap/RuntimeStatusFeatureController.kt'
$runtimeStatusChromePath = Join-Path $Root 'app/src/main/java/com/kite/app/feature/runtimebootstrap/RuntimeStatusChrome.kt'
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
$cardRunActivity = Read-Utf8 $cardRunActivityPath
$store = Read-Utf8 $storePath
$bridgeClient = Read-Utf8 $bridgeClientPath
$ownerStopOutputEvidence = Read-Utf8 $ownerStopOutputEvidencePath
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
$runStepActionPolicy = Read-Utf8 $runStepActionPolicyPath
$runNotificationContract = Read-Utf8 $runNotificationContractPath
$recipeActionWorkflow = Read-Utf8 $recipeActionWorkflowPath
$runExecutionEffectBus = Read-Utf8 $runExecutionEffectBusPath
$runLifecycleEventHub = Read-Utf8 $runLifecycleEventHubPath
$stopCoordinator = Read-Utf8 $stopCoordinatorPath
$androidRecipeExecutor = Read-Utf8 $androidRecipeExecutorPath
$androidRecipeActionGateway = Read-Utf8 $androidRecipeActionGatewayPath
$desktopOpenWorkflow = Read-Utf8 $desktopOpenWorkflowPath
$androidDesktopOpenGateway = Read-Utf8 $androidDesktopOpenGatewayPath
$runtimeOwnerProbeWorkflow = Read-Utf8 $runtimeOwnerProbeWorkflowPath
$androidRuntimeOwnerProbeGateway = Read-Utf8 $androidRuntimeOwnerProbeGatewayPath
$androidRunStateGateway = Read-Utf8 $androidRunStateGatewayPath
$androidRunNotificationCoordinator = Read-Utf8 $androidRunNotificationCoordinatorPath
$resourceRunCoordinator = Read-Utf8 $resourceRunCoordinatorPath
$resourceActionWorkflow = Read-Utf8 $resourceActionWorkflowPath
$androidResourceRecipeFactory = Read-Utf8 $androidResourceRecipeFactoryPath
$androidResourceRunGateway = Read-Utf8 $androidResourceRunGatewayPath
$androidResourceActionGateway = Read-Utf8 $androidResourceActionGatewayPath
$runSurfaceContract = Read-Utf8 $runSurfaceContractPath
$runSurfaceController = Read-Utf8 $runSurfaceControllerPath
$runSurfaceHost = Read-Utf8 $runSurfaceHostPath
$runActivityChrome = Read-Utf8 $runActivityChromePath
$runWindowOverviewScreen = Read-Utf8 $runWindowOverviewScreenPath
$runReportScreen = Read-Utf8 $runReportScreenPath
$runTerminalSurfaceBinding = Read-Utf8 $runTerminalSurfaceBindingPath
$runWebSurfaceBinding = Read-Utf8 $runWebSurfaceBindingPath
$runX11SurfaceBinding = Read-Utf8 $runX11SurfaceBindingPath
$runInstallWizardSurfaceBinding = Read-Utf8 $runInstallWizardSurfaceBindingPath
$browserHandoffCoordinator = Read-Utf8 $browserHandoffCoordinatorPath
$browserAuthRedirectCoordinator = Read-Utf8 $browserAuthRedirectCoordinatorPath
$androidBrowserHandoffGateway = Read-Utf8 $androidBrowserHandoffGatewayPath
$androidBrowserAuthRedirectGateway = Read-Utf8 $androidBrowserAuthRedirectGatewayPath
$androidBrowserAutomationRunUpdater = Read-Utf8 $androidBrowserAutomationRunUpdaterPath
$browserOpenWorkflow = Read-Utf8 $browserOpenWorkflowPath
$androidBrowserOpenGateway = Read-Utf8 $androidBrowserOpenGatewayPath
$installApkWorkflow = Read-Utf8 $installApkWorkflowPath
$androidInstallApkGateway = Read-Utf8 $androidInstallApkGatewayPath
$webWorkbenchFragment = Read-Utf8 $webWorkbenchFragmentPath
$webWorkbenchScreen = Read-Utf8 $webWorkbenchScreenPath
$runtimeManagementCoordinator = Read-Utf8 $runtimeManagementCoordinatorPath
$runtimeManagementGateway = Read-Utf8 $runtimeManagementGatewayPath
$androidRuntimeManagementGateway = Read-Utf8 $androidRuntimeManagementGatewayPath
$runtimeManagementController = Read-Utf8 $runtimeManagementControllerPath
$runtimeManagementProjector = Read-Utf8 $runtimeManagementProjectorPath
$instanceRuntimeTopology = Read-Utf8 $instanceRuntimeTopologyPath
$runtimeManagementFragment = Read-Utf8 $runtimeManagementFragmentPath
$runtimeManagementScreen = Read-Utf8 $runtimeManagementScreenPath
$runtimeBootstrapSnapshot = Read-Utf8 $runtimeBootstrapSnapshotPath
$runtimeBootstrapGateway = Read-Utf8 $runtimeBootstrapGatewayPath
$androidRuntimeBootstrapGateway = Read-Utf8 $androidRuntimeBootstrapGatewayPath
$runtimeStatusProjector = Read-Utf8 $runtimeStatusProjectorPath
$runtimeStatusController = Read-Utf8 $runtimeStatusControllerPath
$runtimeStatusChrome = Read-Utf8 $runtimeStatusChromePath

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
$refreshDropZoneRecipes = Function-Body $main 'refreshDropZoneRecipes'
Assert-True ($androidResourceActionGateway -match 'override suspend fun createHomeCard' -and $androidResourceActionGateway -match 'recipeFeatureGateway\.invalidateCatalog\("resource_home_card_added"\)') 'Resource home-card writes must invalidate the shared recipe catalog snapshot.'
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
Assert-True ($runOrchestrator -match '(?s)fun completeStep\(command: RunStepCompletionCommand\).*createdAt != command\.expectedGeneration.*currentStepIndex != command\.expectedStepIndex.*step\.id != command\.expectedStepId.*executionFlights\.remove\(command\.instanceId\).*clearTerminalSession.*executor\.completeWaitingStep') 'Waiting-step completion must validate the exact generation and step identity before revoking the old execution flight and display binding.'
Assert-True ($runStepActionPolicy -match 'object RunStepActionPolicy' -and $runNotificationContract -match 'RunStepActionPolicy\.completionCommand') 'The notification and visible projection must share one step-completion policy.'
Assert-True ($stopCoordinator -match 'class StopCoordinator' -and $stopCoordinator -match 'cleanupPending' -and $stopCoordinator -match 'return StopResolution\(summary = "\u5df2\u5173\u95ed"' -and $stopCoordinator -notmatch 'StopResolution\.Restore') 'Stop coordinator must close logically in one direction and separate residual cleanup from the visible run state.'
Assert-True ($ownerStopOutputEvidence -match 'settledOwnerOutcomes' -and $ownerStopOutputEvidence -match 'OWNER_NOT_FOUND' -and $ownerStopOutputEvidence -match 'remainingProcessIds') 'Owner stop evidence must treat a missing target as settled while still rejecting explicit PID or PGID residue.'
$runApplicationLayer = $runExecutionContract + "`n" + $runOrchestrator + "`n" + $stopCoordinator
Assert-True ($runApplicationLayer -notmatch '(?m)^import\s+(android\.|androidx\.|com\.kite\.app\.(MainActivity|CardRunActivity|shell\.|bridge\.|platform\.))') 'Run application layer must remain independent from Android, concrete bridge/platform adapters, and page navigation.'
Assert-True ($runOrchestrator -notmatch 'Toast|startActivity|showConsole|showCardRunSurface|WebView|TerminalRuntimeHost|KiteBridgeClient') 'Run orchestrator must not perform page or execution-core work directly.'
Assert-True ($runExecutionEffectBus -match 'MutableSharedFlow<RunExecutionEffect>' -and $runExecutionEffectBus -notmatch 'replay\s*=') 'Run presentation effects must stay one-shot while durable recovery remains in CardRunStore.'
Assert-True ($runLifecycleEventHub -match 'CopyOnWriteArrayList<RunLifecycleSink>' -and $runLifecycleEventHub -notmatch 'MutableStateFlow|mutableMapOf') 'Run lifecycle events must notify after fact commits without becoming a second state store.'
Assert-True ($androidRecipeExecutor -match 'class AndroidRecipeExecutor' -and $androidRecipeExecutor -match 'when \(request\.step\.type\)' -and $androidRecipeExecutor -match 'KiteRecipe\.STEP_SHELL' -and $androidRecipeExecutor -match 'KiteRecipe\.STEP_TERMINAL' -and $androidRecipeExecutor -match 'KiteRecipe\.STEP_OPEN_WEB' -and $androidRecipeExecutor -match 'KiteRecipe\.STEP_X11' -and $androidRecipeExecutor -match 'KiteRecipe\.STEP_ANDROID_ACTION') 'Android recipe executor must dispatch every supported step through the shared execution port.'
Assert-True ($androidRecipeExecutor -match '!request\.hasBridgeProcessBinding\(\)' -and $androidRecipeExecutor -match 'val bridgeRunId = request\.bridgeRunId\(\)') 'Android stop execution must bypass Bridge for terminal-only sessions and use only the normalized Bridge run id.'
Assert-True ($androidRecipeExecutor -match 'val residueMarkerObserved = bridgeConfirmedStop' -and $androidRecipeExecutor -match 'residueMarkerObserved = residueMarkerObserved') 'Android stop execution must preserve explicit Bridge residue-audit evidence for StopCoordinator.'
Assert-True ($androidRecipeExecutor -notmatch '(?m)^import\s+(android\.app\.Activity|android\.view\.|android\.widget\.|com\.kite\.app\.(MainActivity|CardRunActivity|shell\.|feature\.))') 'Android recipe executor must remain independent from pages, View widgets, Shell, and Feature code.'
Assert-True ($androidRunStateGateway -match 'CardRunStore\.(registerRecipe|registeredRecipe|get|currentForRecipe|start|update)') 'Android run-state adapter must keep CardRunStore as the single run-fact owner.'
Assert-True ($resourceRunCoordinator -match 'class ResourceRunCoordinator' -and $resourceRunCoordinator -match 'RunLifecycleEventHub' -and $resourceRunCoordinator -match 'startNextPlannedInstall') 'Resource run coordination must own terminal settlement and dependency-plan continuation at process scope.'
Assert-True ($resourceRunCoordinator -notmatch '(?m)^import\s+(android\.|androidx\.|com\.kite\.app\.(MainActivity|CardRunActivity|shell\.|run\.CardRunStore))') 'Resource run coordinator must remain independent from Android pages, navigation, and concrete CardRunStore.'
Assert-True ($androidResourceRunGateway -match 'CardRunStore\.start' -and $androidResourceRunGateway -match 'installStore\.markInstalled' -and $androidResourceRunGateway -match 'installStore\.saveInstalledSnapshot') 'Android resource gateway must adapt CardRun and resource registry facts for the process coordinator.'
Assert-True ($androidResourceRecipeFactory -match 'KiteResourceInstallPlanCompiler\.compile' -and $androidResourceRecipeFactory -match 'KiteResourceInstallRecipes\.toRecipe') 'Resource run recipes must be compiled from manifests through the shared install compiler.'
Assert-True ($kiteAppGraph -match 'val resourceRunCoordinator: ResourceRunCoordinator by lazy' -and $kiteAppGraph -match 'lifecycleHub = runLifecycleEventHub') 'Resource run coordination and lifecycle events must be process composition-root dependencies.'
Assert-True ($terminalFragment -match '(?s)detailBackCallback.*showListPage\(\)') 'terminal detail back callback must return to the terminal list first.'
Assert-True ($terminalFragment -match '(?s)btnBackToSessions.*?sendSurfaceEffect\(SurfaceEffect\.RequestBack\)') 'terminal detail header must submit a generic surface back effect.'
Assert-True ($runSurfaceHost -match 'private var binding: RunSurfaceBinding' -and $runReportScreen -match 'override fun render\(state: RunSurfaceUiState\)' -and $runReportScreen -match 'private val outputText = TextView') 'report page must own its local output binding through RunSurfaceHost.'
Assert-True ($main -notmatch 'cardRunReportBinding|updateVisibleCardRunReport|renderVisibleCardRunReport') 'MainActivity must not retain the legacy report binding or render loop.'
Assert-True ($runTerminalSurfaceBinding -match 'TerminalFragment\.detailOnly' -and $runTerminalSurfaceBinding -match '\.detach\(fragment\)' -and $runTerminalSurfaceBinding -notmatch 'stop\(|RunOrchestrator|TerminalSessionController') 'terminal surface binding must own Fragment attach/detach without stopping the shell session.'
Assert-True ($runTerminalSurfaceBinding -match 'R\.id\.kite_run_terminal_container' -and $runTerminalSurfaceBinding -notmatch 'View\.generateViewId\(\)') 'terminal Fragment container id must remain stable across Activity restoration.'
Assert-True ($runTerminalSurfaceBinding -match 'commitNowAllowingStateLoss\(\)' -and $runTerminalSurfaceBinding -notmatch '\.commitAllowingStateLoss\(\)') 'terminal Fragment attach/detach must finish before its container can be removed.'
Assert-True ($cardRunActivity -match 'RunTerminalSurfaceBinding\.removeIncompatibleRestoredFragments\(supportFragmentManager\)') 'CardRunActivity must discard legacy dynamic-id terminal Fragments before view restoration.'
Assert-True ($main -notmatch 'showCardRunTerminalFragment|cardRunTerminalContainerId|CARD_RUN_TERMINAL_FRAGMENT_TAG') 'MainActivity must not retain the legacy CardRun terminal Fragment binding.'
Assert-True ($cardRunActivity -match '(?s)override fun handleOnBackPressed\(\)\s*\{\s*if \(chrome\?\.handleBack\(\) == true\) return\s*if \(surfaceHost\?\.handleBack\(\) == true\) return\s*closeTaskWindow\(\)\s*\}' -and $cardRunActivity -match '(?s)private fun closeTaskWindow\(\).*finishAndRemoveTask\(\)') 'CardRun back must close transient chrome, then delegate surface history, and only then detach the task window.'
Assert-True ($runWebSurfaceBinding -match 'WebView\(activity\)' -and $runWebSurfaceBinding -match 'KiteWebShell\(' -and $runWebSurfaceBinding -match 'override fun handleBack\(\)' -and $runWebSurfaceBinding -match 'override fun reload\(\)' -and $runWebSurfaceBinding -match 'current\.destroy\(\)') 'Web surface binding must own WebView creation, history/reload, and display disposal.'
Assert-True ($runSurfaceHost -match 'interface RunSurfaceToolbarOwner' -and $runSurfaceHost -match 'fun toggleSurfaceToolbar\(\)' -and $cardRunActivity -match 'onToggleSurfaceToolbar = \{ host\.toggleSurfaceToolbar\(\) \}') 'The instance handle must delegate only the current surface toolbar through RunSurfaceHost.'
Assert-True ($runWebSurfaceBinding -match 'private val toolbar = RunWebToolbar' -and $runWebSurfaceBinding -match 'override fun toggleSurfaceToolbar\(\)' -and $runWebSurfaceBinding -match 'override fun setSurfaceToolbarVisible\(visible: Boolean\)') 'The Web surface must own its navigation toolbar and toolbar visibility.'
Assert-True ($runWebSurfaceBinding -match 'requestedVisible' -and $runWebSurfaceBinding -match 'setDuration\(180L\)' -and $runWebSurfaceBinding -match 'scaleX\(0\.18f\)') 'The Web-owned toolbar must animate as a capsule while tracking requested visibility across overlapping taps.'
Assert-True ($runTerminalSurfaceBinding -notmatch 'toggleSurfaceToolbar|setSurfaceToolbarVisible' -and $terminalFragment -notmatch 'RunSurfaceToolbarOwner' -and $runReportScreen -notmatch 'toggleSurfaceToolbar|setSurfaceToolbarVisible') 'Terminal input and report core actions must not be treated as optional surface toolbars.'
Assert-True ($runActivityChrome -match 'onSingleTap = actions\.onToggleSurfaceToolbar' -and $runActivityChrome -match 'onDoubleTap = ::showOverview' -and $runActivityChrome -match 'getLongPressTimeout' -and $runActivityChrome -notmatch 'standardControls|webControls|autoOpenedKey') 'The instance chrome must keep only single-tap toolbar, double-tap overview, and long-press drag behavior.'
Assert-True ($runWindowOverviewScreen.Contains('private val onCloseInstance: () -> Unit') -and $runWindowOverviewScreen.Contains('private val onOpenWeb: () -> Unit') -and $runWindowOverviewScreen.Contains('private val onOpenTerminal: () -> Unit') -and $runWindowOverviewScreen -match 'showCreateBubble\(\)' -and $runWindowOverviewScreen -notmatch 'onComplete') 'The instance overview must keep instance close, animated Web/terminal creation, and back without hosting step completion.'
Assert-True ($runNotificationContract -match '(?s)data class CompleteStep\(\s*val command: RunStepCompletionCommand' -and $androidRunNotificationCoordinator -match '(?s)private fun completionPendingIntent\(command: RunStepCompletionCommand\).*EXTRA_GENERATION.*EXTRA_STEP_INDEX.*EXTRA_STEP_ID') 'Each next-step notification action must carry the exact instance generation, step index, and step id.'
Assert-True ($kiteAppGraph -match 'startGate = RunStartGate\(runNotificationCoordinator::startRejectionReason\)' -and $recipeActionWorkflow -match 'RecipeActionEffect\.RequireNotifications') 'Run creation must be gated before facts are written and return a permission effect instead of a half-started instance.'
Assert-True ($main -notmatch 'runSurfaceHost|RunWebSurfaceBinding') 'MainActivity must not regain CardRun Web display ownership.'
Assert-True ($main -notmatch 'showCardRunWebView|cardRunBrowserAuthWaitingBody|cardRunExternalBrowserBody|cardRunWebAddressInputBody') 'MainActivity must not retain the legacy CardRun Web display builders.'
Assert-True ($runX11SurfaceBinding -match 'KiteX11SurfaceServer\.surfaceView' -and $runX11SurfaceBinding -match 'override fun dispose\(\)' -and $runX11SurfaceBinding -notmatch 'RunOrchestrator|stop\(') 'X11 surface binding must own only the visible LorieView lifecycle.'
Assert-True ($main -notmatch 'cardRunX11SurfaceBody|x11TaskTitle') 'MainActivity must not retain the legacy CardRun X11 display builder.'
Assert-True ($desktopOpenWorkflow -match 'DesktopOpenResult' -and $desktopOpenWorkflow -notmatch 'CardRunStore|KiteX11Surface|Activity|View') 'Desktop-open application workflow must remain independent from X11 and UI implementations.'
Assert-True ($androidDesktopOpenGateway -match 'KiteX11SurfacePlan\.allocate' -and $androidDesktopOpenGateway -match 'KiteX11SurfaceServer\.ensureStarted' -and $androidDesktopOpenGateway -match 'CardRunStore\.update') 'Desktop-open platform adapter must prepare X11 and publish the run fact before returning.'
Assert-True ($main -match 'desktopOpenCoordinator\.open' -and $main -notmatch 'acceptDesktopOpenRequest|temporaryDesktopRecipe|KiteX11SurfacePlan|KiteX11SurfaceServer') 'MainActivity must only map desktop-open results to the existing run task and router.'
Assert-True ($browserOpenWorkflow -match 'data class OpenTemporaryRun' -and $browserOpenWorkflow -notmatch 'CardRunStore|WebView|Activity') 'Browser-open application workflow must remain independent from run stores and displays.'
Assert-True ($androidBrowserOpenGateway -match 'CardRunBrowserRouter\.dispatch' -and $androidBrowserOpenGateway -match 'CardRunStore\.update' -and $androidBrowserOpenGateway -notmatch 'startActivity|(?m)^import\s+android\.webkit\.') 'Browser-open platform adapter must route or publish one Web run fact without creating a display.'
Assert-True ($installApkWorkflow -match 'InstallApkResult' -and $installApkWorkflow -notmatch 'FileProvider|Intent|Activity') 'Install-APK application workflow must remain a path/result contract.'
Assert-True ($androidInstallApkGateway -match 'ExternalExchangeManager\.ensureExchangeDir' -and $androidInstallApkGateway -notmatch 'FileProvider|startActivity') 'Install-APK platform adapter must validate supported paths without opening Android UI.'
Assert-True ($main -match 'browserOpenCoordinator\.open' -and $main -match 'installApkCoordinator\.resolve' -and $main -notmatch 'openTemporaryBrowserRequest|updateBrowserRequestState|resolveInstallApkFile') 'MainActivity must only execute browser/install Shell effects.'
Assert-True ($main -match 'browserHandoffCoordinator\.launch' -and $main -notmatch 'browserAuthSessions\.createPending\(request, decision\)|browserLoopbackCallbackBridge\.prepare\(session\)') 'MainActivity must delegate browser handoff sequencing to BrowserHandoffCoordinator.'
Assert-True ($browserHandoffCoordinator -match '(?s)createPending\(request, decision\).*updateWaiting\(session, request\).*prepareCallback\(session\).*openExternal\(request\.url\)' -and $browserHandoffCoordinator -notmatch 'import android\.|import androidx\.') 'Browser handoff coordinator must preserve side-effect order without Android UI dependencies.'
Assert-True ($androidBrowserHandoffGateway -match 'CardRunStore\.update' -and $androidBrowserHandoffGateway -match 'loopbackBridge\.prepare' -and $androidBrowserHandoffGateway -match 'sessions\.markFailed') 'Android browser handoff gateway must own Store, loopback, and session adapters.'
Assert-True ($main -match 'browserAuthRedirectCoordinator\.handle\(rawUrl\)' -and $main -notmatch 'browserAuthSessions\.markReturned|deliverBrowserAuthRedirect|updateForwardedLoopbackBrowserAuthSession|updateExpiredBrowserAuthSession') 'MainActivity must delegate auth return sequencing and runtime synchronization to BrowserAuthRedirectCoordinator.'
Assert-True ($browserAuthRedirectCoordinator -match '(?s)matchReturned\(redirect\).*resolveTarget\(session\).*projectDelivery\(target, session, redirect, failed\).*markDelivered' -and $browserAuthRedirectCoordinator -notmatch 'import android\.|import androidx\.') 'Browser auth redirect coordinator must preserve delivery order without Android UI dependencies.'
Assert-True ($androidBrowserAuthRedirectGateway -match 'sessions\.markReturned' -and $androidBrowserAuthRedirectGateway -match 'CardRunStore\.update' -and $androidBrowserAuthRedirectGateway -match 'loopbackBridge\.stop') 'Android auth redirect gateway must own persisted session, CardRun, and callback adapters.'
Assert-True ($androidBrowserAuthRedirectGateway -match '(?s)val existing = CardRunStore\.get\(instanceId\).*CardRunStore::historyForRecipe.*firstOrNull \{ it\.instanceId == instanceId \}.*val persistedRecipeId = existing\?\.recipeId \?: history\?\.recipeId.*browserAuthRecoveryRecipe' -and $androidBrowserAuthRedirectGateway -notmatch 'Codex|Claude|OpenAI|Google') 'Process reconstruction may recover a projection recipe only from an existing run or matching persisted history and must remain provider-neutral.'
Assert-True ($cardRunActivity -match 'browserAutomationUpdater\.update\(event\)' -and $main -notmatch 'browserAutomationRunStatus|browserAutomationSummary|browserAutomationReport') 'Card-run automation events must delegate to the shared platform updater without a MainActivity copy.'
Assert-True ($androidBrowserAutomationRunUpdater -match 'CardRunStore\.update' -and $androidBrowserAutomationRunUpdater -notmatch 'MainActivity|CardRunActivity|android\.view|android\.widget') 'Browser automation run projection must stay in the shared platform adapter.'
Assert-True ($main -match 'WebWorkbenchFragment\.newInstance' -and $main -notmatch 'private lateinit var webView|private lateinit var webShell|handleWebViewBackSignal|releaseActivityDisplaySurfaces') 'MainActivity must route ordinary Web display to the web Feature without owning its WebView lifecycle.'
Assert-True ($webWorkbenchFragment -match 'onDestroyView' -and $webWorkbenchFragment -match 'screen\?\.dispose\(\)' -and $webWorkbenchFragment -match 'OnBackPressedCallback') 'Web workbench Fragment must own display disposal and browser-history back handling.'
Assert-True ($webWorkbenchScreen -match 'BrowserAutomationController\(' -and $webWorkbenchScreen -match 'KiteWebShell\(' -and $webWorkbenchScreen -match 'webView\.destroy\(\)') 'Web workbench Screen must own WebView, shell, automation display session, and disposal.'
Assert-True ($webWorkbenchScreen -notmatch 'BrowserAuthSessionStore|BrowserLoopbackCallbackBridge|CardRunStore|RunOrchestrator') 'Destroying an ordinary Web display must not own authentication sessions or background runs.'
Assert-True ($runInstallWizardSurfaceBinding -match 'override fun tick\(now: Long\): Boolean = surface\.tick\(now\)' -and $resourceInstallWizardScreen -match 'fun tick\(' -and $main -notmatch 'ResourceInstallWizardSurface|resourceInstallWizardSurface') 'install wizard must keep elapsed binding inside its feature screen.'
Assert-True ($main -match '(?s)private fun showConsole\b.*HomeFragment' -and $main -notmatch 'consoleCardBindings|consolePageBodyHost|private fun recipeGrid|updateVisibleConsoleCard') 'Home card views, bindings, and page state must remain owned by HomeFragment and HomeScreen.'
Assert-True ($resourceFeatureController -match 'gateway\.loadCatalog' -and $resourceFeatureController -notmatch 'File\.|KiteResourceManifestLoader|CardRunStore') 'UI resource rendering must consume the shared gateway snapshot without render-time probes.'
Assert-True ($main -match 'observeRuntimeStatus' -and $main -match 'RuntimeStatusFeatureController') 'runtime status chrome must observe the shared projected feature state.'
Assert-True ($main -match 'handleRuntimeAutomationIntent') 'Kite MainActivity must expose the runtime automation diagnostic entry on the real launcher path.'
Assert-True ($main -match 'RuntimeAutomationActions\.dumpDiagnostics\(applicationContext\)') 'Kite runtime diagnostics must reuse the shared RuntimeAutomationActions dump path.'
Assert-True ($main -match '(?s)private fun clearRuntimeAutomationExtras\b.*removeExtra\(EXTRA_AUTOMATION_RUNTIME_ID\).*removeExtra\(CardRunIntents\.EXTRA_RECIPE_ID\).*removeExtra\(CardRunIntents\.EXTRA_INSTANCE_ID\)') 'Consumed runtime automation intents must not retain ids that can be replayed as a product card route after process restore.'
Assert-True ($main -match 'RuntimeAutomationActions\.rotateProotTelemetry\(applicationContext\)') 'Kite runtime automation must expose telemetry rotation on the real launcher path.'
Assert-True ($main -match 'RuntimeAutomationActions\.prepareProotLiveTraceeProbe' -and $main -match 'RuntimeAutomationActions\.injectProotLiveTraceeProbe') 'Kite runtime automation must expose live-tracee probe actions on the real launcher path.'
Assert-True ($main -match 'private fun readProbeTargetLiveTracees' -and $main -match 'EXTRA_AUTOMATION_PROBE_TARGET_LIVE_TRACEES = "probe_target_live_tracees"') 'Kite live-tracee probe automation must accept an explicit target tracee count.'
Assert-True ($main -match 'ACTION_STOP_BACKGROUND_RUNTIME = "stop_background_runtime"' -and $main -match '(?s)private fun stopBackgroundRuntimeFromAutomation\b.*TaskManagerStore\.stopRuntime\(applicationContext, runtimeId\)') 'Kite runtime automation must stop background runtimes through the existing TaskManagerStore path.'
Assert-True ($main -match 'ACTION_RECLAIM_OWNER_RUNTIME = "reclaim_owner_runtime"' -and $main -match 'EXTRA_AUTOMATION_OWNER_ID = "owner_id"') 'Kite runtime automation must expose an explicit owner reclaim action and owner_id extra.'
Assert-True ($main -match '(?s)private fun reclaimOwnerRuntimeFromAutomation\b.*ownerId\.isAutomationRuntimeOwnerId\(\).*RuntimeReclaimer\.reclaimOwnerRuntime') 'Kite owner reclaim automation must validate card/resource/terminal owner ids before dispatching RuntimeReclaimer.'
Assert-True ($main -match '(?s)private fun String\.isAutomationRuntimeOwnerId\(\).*startsWith\("card:"\).*startsWith\("resource:"\).*startsWith\("terminal:"\)') 'Kite owner reclaim automation must stay bounded to card/resource/terminal owner ids.'
Assert-True ($runtimeAutomationActions -match '(?s)fun dumpDiagnostics\b.*RuntimeHealthStore\.refresh\(\s*context = appContext,\s*reason = "adb-dump-diagnostics"') 'ADB dump diagnostics must refresh RuntimeHealth before exporting owner/process facts.'
Assert-True ($main -match 'ACTION_STOP_CARD_RUN = "stop_card_run"') 'Kite runtime automation must expose a gated card stop action for real-path owner stop validation.'
Assert-True ($main -match '(?s)private fun stopCardRunFromAutomation\b.*submitRecipeAction\(.*KiteRecipeActionIntent\.Stop.*KiteRecipeActionSource\.Automation.*instanceId = state\.instanceId') 'Kite card stop automation must reuse the shared recipe action workflow.'
Assert-True ($main -match 'ACTION_START_RESOURCE_OWNER_PROBE = "start_resource_owner_probe"') 'Kite runtime automation must expose a gated resource owner probe for real-device validation.'
Assert-True ($main -match '(?s)private fun startResourceOwnerProbeFromAutomation\b.*runtimeOwnerProbeCoordinator\.start\(\s*RuntimeOwnerProbeRequest') 'resource owner probe automation must delegate to its application coordinator.'
Assert-True ($runtimeOwnerProbeWorkflow -match 'class RuntimeOwnerProbeCoordinator' -and $runtimeOwnerProbeWorkflow -match 'gateway\.start' -and $runtimeOwnerProbeWorkflow -notmatch '(?m)^import\s+(android\.|androidx\.|com\.kite\.app\.(MainActivity|CardRunActivity|shell\.|platform\.))') 'resource owner probe workflow must remain independent from Android pages and platform adapters.'
Assert-True ($androidRuntimeOwnerProbeGateway -match 'CardRunSpecialRecipes\.resourceOwnerProbe' -and $androidRuntimeOwnerProbeGateway -match 'orchestrator\.start' -and $androidRuntimeOwnerProbeGateway -match 'OWNER_KIND_RESOURCE') 'resource owner probe adapter must use the shared recipe shape and RunOrchestrator owner path.'
Assert-True ($main -notmatch 'runOrchestrator\.(start|stop)|fun\s+(startRecipeWithOrchestrator|stopRecipeWithOrchestrator|resourceOwnerProbeRecipe)\s*\(') 'MainActivity must not retain direct run orchestration or probe construction.'
Assert-True ($main -notmatch 'legacyStartRecipe|executeRecipeStep|runUbuntuStepWhenReady|handleSequenceShellResult') 'MainActivity must not retain a second recipe execution engine.'
Assert-True ($kiteAppGraph -match 'val runOrchestrator: RunOrchestrator by lazy' -and $kiteAppGraph -match 'AndroidRecipeExecutor\(appContext, bridgeClient, diagnostics\)') 'Run orchestration and execution adapter must be process composition-root dependencies.'
Assert-True ($recipeActionWorkflow -match 'planner\.plan' -and $recipeActionWorkflow -match 'gateway\.start' -and $recipeActionWorkflow -match 'gateway\.stop') 'Recipe actions must be planned and handed to one application workflow.'
Assert-True ($androidRecipeActionGateway -match 'orchestrator\.start' -and $androidRecipeActionGateway -match 'orchestrator\.stop' -and $androidRecipeActionGateway -notmatch '(?m)^import\s+(android\.app\.Activity|android\.view\.|android\.widget\.|androidx\.fragment\.|com\.kite\.app\.feature)') 'Recipe action runtime access must stay in the platform adapter without page ownership.'
Assert-True ($main -match 'recipeActionWorkflowCoordinator\.dispatch' -and $main -match 'applyRecipeActionEffects' -and $main -notmatch 'recipeActionCoordinator|executeRecipeActionRoute|KiteRecipeActionPlan') 'MainActivity must only interpret recipe action effects.'
Assert-True ($androidResourceActionGateway -match 'runCoordinator\.start' -and $main -match 'resourceActionWorkflowCoordinator\.installDirect' -and $main -notmatch 'fun\s+startResourceRun\s*\(|resourceRunCoordinator|ToolchainPackInstaller\.|resourceManifestLoader') 'Resource run intake must delegate preparation and execution to the process coordinator without page-owned execution.'
Assert-True ($main -notmatch 'resourceManifestRecipeSteps|resourceManifestActionCommand|legacyResourceInstallStep|legacyResourceUninstallStep|markResourceRunSuccess|markResourceInstallFailed') 'MainActivity must not compile resource recipes or settle resource registry facts.'
Assert-True ($runSurfaceContract -match 'sealed interface RunSurfaceContent' -and $runSurfaceContract -match 'val structureKey: String') 'Run surface feature must project explicit content and a stable structural binding key.'
Assert-True ($runSurfaceController -match 'recipe\.id != current\.recipeId \|\| state\.instanceId != current\.instanceId' -and $runSurfaceController -match '(?s)fun detach\(\).*target = null') 'Run surface controller must reject cross-instance state and detach without stopping runtime work.'
Assert-True ($runSurfaceController -notmatch 'Activity|Fragment|View|CardRunStore|TerminalRuntimeHost|WebView') 'Run surface controller must remain independent from Android surfaces and concrete runtime stores.'

$handleProgress = Member-Function-Body $androidRecipeExecutor 'handleShellProgress'
Assert-True ($handleProgress -match 'RecipeExecutionEvent\.Progress' -and $handleProgress -notmatch 'Activity|View|showCardRunSurface|showConsole|renderResourceInstallWizardFor') 'Shell progress must stay in the UI-agnostic execution adapter.'

$runtimeStatusObserver = Function-Body $main 'observeRuntimeStatus'
Assert-True ($runtimeStatusObserver -match 'runtimeStatusController\.state\.collect' -and $runtimeStatusObserver -notmatch 'CardRunStore|TerminalSessionStore|TaskManagerStore') 'runtime status chrome must observe one projected state instead of concrete stores.'
Assert-True ($runtimeStatusController -match 'management\.toStatusCounts\(\)' -and $runtimeStatusController -match 'runningCards' -and $runtimeStatusController -match 'runningTerminals' -and $runtimeStatusController -match 'runningProcesses') 'runtime status counts must derive from the shared runtime-management snapshot.'
$runtimeStatusPanelBind = Member-Function-Body $runtimeStatusChrome 'bindPanel'
Assert-True ($runtimeStatusPanelBind -match 'binding\.cardCount\.text' -and $runtimeStatusPanelBind -match 'binding\.terminalCount\.text' -and $runtimeStatusPanelBind -match 'binding\.processCount\.text') 'runtime status panel must bind count changes into existing value views.'
Assert-True ($runtimeStatusPanelBind -notmatch 'removeAllViews|CardRunStore|TerminalSessionStore|TaskManagerStore') 'runtime status count binding must remain local and store-free.'
$showRuntimeStatusPanel = Member-Function-Body $runtimeStatusChrome 'showPanel'
Assert-True ($showRuntimeStatusPanel -match 'onRefresh\(\)' -and $showRuntimeStatusPanel -match 'bindPanel' -and $showRuntimeStatusPanel -notmatch 'TaskManagerStore|TerminalSessionStore') 'opening runtime status panel must refresh through the feature callback and reuse existing bindings.'
Assert-True ($main -notmatch 'runtimePanelCardCountView|runtimePanelTerminalCountView|runtimePanelProcessCountView|runtimeGateOverlay|showUbuntuRuntimePanel|renderUbuntuRuntimePanelState') 'MainActivity must not retain runtime status Dialog or gate View ownership.'

$consoleRecipeAction = Function-Body $homeFeatureController 'requestPrimary'
$editorRecipeRun = Function-Body $recipeEditorController 'requestRun'
Assert-True ($consoleRecipeAction -match 'KiteRecipeActionRequest' -and $homeFeatureFragment -match 'HomeFeatureResultContract\.actionRequest' -and $main -match '(?s)registerHomeFeatureResults.*?submitRecipeAction') 'console recipe actions must submit through the shared action intake.'
Assert-True ($editorRecipeRun -match 'KiteRecipeActionRequest' -and $editorRecipeRun -match 'KiteRecipeActionSource\.Editor' -and $editorRecipeRun -notmatch '\bstartRecipe\s*\(') 'editor start must submit through the shared action intake instead of starting directly.'
Assert-True ($recipeEditorScreen -match 'KiteRecipeActionIntent\.Open' -and $recipeEditorScreen -match 'KiteRecipeActionIntent\.Stop' -and $recipeEditorScreen -match 'KiteRecipeActionIntent\.Start') 'editor open, stop and start controls must submit explicit shared action intents.'
Assert-True ($recipeEditorFragment -match 'RecipeEditorResultContract\.actionRequest' -and $recipeEditorFragment -notmatch '\bstopRecipe\s*\(' -and $recipeEditorFragment -notmatch '\bopenRecipeRunInstance\s*\(') 'editor action buttons must not bypass the shared action intake.'

$resourcePrimaryAction = Function-Body $resourceFeatureController 'requestPrimary'
$resourceSecondaryIntent = Function-Body $resourceFeatureController 'secondaryIntent'
$resourceSecondaryRequest = Function-Body $resourceFeatureController 'requestSecondary'
Assert-True ($resourceFeatureController -match 'KiteResourceActionCoordinator\.primaryIntent' -and $resourcePrimaryAction -match 'KiteResourceActionRequest' -and $resourcePrimaryAction -notmatch '\bhandleResource(?:Install|Uninstall|OpenStop|Cancel|Failed)') 'resource primary actions must resolve and submit through the shared action intake.'
Assert-True ($resourceSecondaryIntent -match 'KiteResourceActionIntent\.CancelInstall' -and $resourceSecondaryIntent -match 'KiteResourceActionIntent\.Stop' -and $resourceSecondaryIntent -match 'KiteResourceActionIntent\.Uninstall') 'resource detail secondary actions must resolve explicit shared intents.'
Assert-True ($resourceSecondaryRequest -match 'KiteResourceActionRequest' -and $resourceSecondaryRequest -match 'item\.secondaryIntent') 'resource detail secondary actions must submit through the shared action intake.'
Assert-True ($resourceSecondaryRequest -notmatch '\bhandleResource(?:Install|Uninstall|OpenStop|Cancel|Failed)') 'resource detail buttons must not bypass the shared action intake.'

Assert-True ($resourceInstallWizardPresentation -match 'KiteInstallPlanActionCoordinator\.plan' -and $resourceInstallWizardScreen -match 'onPlanAction\(intent\)') 'install wizard primary action must submit a coordinated plan intent.'
Assert-True ($resourceInstallWizardScreen -notmatch '\bstartNextResourceInstallFromPlan\s*\(' -and $resourceInstallWizardScreen -notmatch '\bshowResources\s*\(') 'install wizard button binding must not execute or navigate directly.'
Assert-True ($cardRunActivity -match 'resourceRunCoordinator\.startNextPlannedInstall\(target\.instanceId\)' -and $resourceRunCoordinator -match 'fun startNextPlannedInstall\(parentInstanceId: String\?\)') 'install-plan execution must delegate from the run shell to ResourceRunCoordinator.'
Assert-True ($cardRunActivity -match 'onCloseInstance = ::closeCurrentInstance' -and $cardRunActivity -match '(?s)override fun handleOnBackPressed\(\).*closeTaskWindow\(\)' -and $cardRunActivity -match '(?s)private fun closeCurrentInstance\(\).*pendingCloseGeneration = state\.createdAt.*runOrchestrator\.stop\(state\.instanceId\).*RunCommandResult\.Accepted -> Unit' -and $cardRunActivity -match '(?s)pendingCloseGeneration == state\.createdAt.*state\.status == CardRunStatus\.Stopped.*closeTaskWindow\(\)') 'CardRun back must only detach the task window while instance close waits for the selected generation to stop.'

$showRunManagement = Function-Body $main 'showKiteProcessOverview'
Assert-True ($showRunManagement -match 'RuntimeManagementFragment\.newInstance\(forceRefresh\)' -and $showRunManagement -match 'showFeatureFragment') 'run management destination must route to its owning feature fragment.'
Assert-True ($main -notmatch 'buildRunManagementGroups|runManagementPendingProcessStopIds|scheduleRunManagementLazyRefresh|markRunManagementProcessStopAsUserStop|showRunManagementProcessDialog') 'MainActivity must not retain the legacy run-management render or optimistic fact-writing path.'
Assert-True ($androidRuntimeManagementGateway -match 'CardRunStore\.runs' -and $androidRuntimeManagementGateway -match 'TerminalSessionStore\.snapshot' -and $androidRuntimeManagementGateway -match 'TaskManagerStore\.snapshot') 'runtime management gateway must compose one snapshot from the existing state owners.'
Assert-True ($runtimeManagementGateway -notmatch 'android\.view|Activity|Fragment') 'runtime management application gateway must stay independent from Android views.'
Assert-True ($runtimeManagementController -match 'RuntimeManagementProjector\.project' -and $runtimeManagementController -notmatch '(?m)^import\s+(android\.|androidx\.)') 'runtime management controller must project facts without owning Android views.'
Assert-True ($runtimeManagementFragment -match 'gateway\.snapshots\.collect' -and $runtimeManagementFragment -match 'coordinator\.commands\.collectLatest' -and $runtimeManagementFragment -match 'deadlineAt') 'runtime management fragment must react to owner signals and one confirmation deadline.'
Assert-True ($runtimeManagementFragment -notmatch '260L, 900L, 1800L|showKiteProcessOverview|TaskManagerStore|CardRunStore|TerminalSessionStore') 'runtime management fragment must not poll, rebuild the Activity page, or read concrete stores.'
$runtimeManagementRender = Member-Function-Body $runtimeManagementScreen 'render'
Assert-True ($runtimeManagementRender -match 'structureSignature' -and $runtimeManagementRender -match 'bindRun' -and $runtimeManagementRender -match 'bindProcess' -and $runtimeManagementRender -notmatch 'removeAllViews') 'runtime management steady-state rendering must bind existing rows without rebuilding the page.'
Assert-True ($runtimeManagementScreen -match 'runBindings' -and $runtimeManagementScreen -match 'terminalBindings' -and $runtimeManagementScreen -match 'processBindings' -and $runtimeManagementScreen -notmatch 'CardRunStore|TerminalSessionStore|TaskManagerStore|refreshResourceScreenIfVisible') 'runtime management screen must own local bindings while remaining store-free.'
Assert-True ($runtimeManagementProjector -match 'standaloneTerminals' -and $runtimeManagementProjector -match 'RuntimeManagementActionTarget\.EndTerminal') 'live terminals without CardRun bindings must remain traceable and endable.'
Assert-True ($runtimeManagementProjector -match 'BackgroundRuntime to "\u540e\u53f0\u670d\u52a1"' -and $runtimeManagementProjector -match 'Resource to "\u8d44\u6e90\u4efb\u52a1"' -and $runtimeManagementProjector -match 'Terminal to "\u7ec8\u7aef\u8fdb\u7a0b"') 'background runtime, resource, and terminal processes must keep explicit ownership sections.'
Assert-True ($runtimeManagementCoordinator -match 'Requested' -and $runtimeManagementCoordinator -match 'AwaitingConfirmation' -and $runtimeManagementCoordinator -match 'Failed' -and $runtimeManagementCoordinator -notmatch 'CardRunStore|TerminalSessionStore|TaskManagerStore') 'runtime management actions must use a confirmed transaction without writing owner facts directly.'
$runtimeManagementProcessDialog = Member-Function-Body $runtimeManagementScreen 'showProcessDialog'
Assert-True ($runtimeManagementProcessDialog -match 'process\.stopAction' -and $runtimeManagementProcessDialog -match 'onAction\(action\)') 'runtime management process details must submit the projected owner-aware action.'
Assert-True ($runtimeBootstrapSnapshot -notmatch '(?m)^import\s+(android\.|androidx\.)' -and $runtimeBootstrapGateway -notmatch '(?m)^import\s+(android\.|androidx\.)') 'runtime bootstrap application contracts must remain Android-independent.'
Assert-True ($runtimeStatusProjector -notmatch 'Activity|Fragment|View|Dialog|CardRunStore|TerminalSessionStore|TaskManagerStore|WorkSurfaceRuntimeBridge') 'runtime status projection must remain pure and store-free.'
Assert-True ($runtimeStatusController -match 'bootstrapGateway\.snapshots' -and $runtimeStatusController -match 'managementGateway\.snapshots' -and $runtimeStatusController -notmatch 'CardRunStore|TerminalSessionStore|TaskManagerStore|BootstrapCoordinator') 'runtime status controller must combine stable gateways rather than concrete stores.'
$runtimeStatusRefresh = Member-Function-Body $runtimeStatusController 'refresh'
Assert-True ($runtimeStatusRefresh -match 'bootstrapGateway\.refresh\(\)' -and $runtimeStatusRefresh -match 'managementGateway\.refresh\(force = true\)') 'runtime status refresh must calibrate bootstrap and management facts together.'
Assert-True ($androidRuntimeBootstrapGateway -match 'scope\.launch\(Dispatchers\.IO\)' -and $androidRuntimeBootstrapGateway -match 'WorkSurfaceRuntimeBridge\.isBaseImageReady' -and $androidRuntimeBootstrapGateway -match 'ToolchainPackInstaller\.bootstrapResourcesSettled') 'runtime readiness probes must run in the Android platform gateway off the UI thread.'
Assert-True ($androidRuntimeBootstrapGateway -match 'BootstrapCoordinator\.snapshot' -and $androidRuntimeBootstrapGateway -match 'AssetExtractor\.rootfsProgress' -and $androidRuntimeBootstrapGateway -match 'RuntimeBootstrapProgress\.snapshot') 'runtime bootstrap gateway must compose existing bootstrap fact owners.'
Assert-True ($androidRuntimeBootstrapGateway -match '(?s)BootstrapCoordinator\.snapshot.*?map \{ it\.stage \}.*?distinctUntilChanged\(\).*?filter\(::shouldRefreshReadiness\).*?probe\.value = probeReadiness\(\)' -and $androidRuntimeBootstrapGateway -match 'stage == BootstrapStage\.READY \|\| stage == BootstrapStage\.FAILED') 'runtime bootstrap completion must recheck readiness from the owner terminal signal without UI polling.'
Assert-True ($kiteAppGraph -match 'val runtimeBootstrapGateway: RuntimeBootstrapGateway by lazy' -and $kiteAppGraph -match 'AndroidRuntimeBootstrapGateway\(appContext\)') 'runtime bootstrap gateway must be a process composition-root dependency.'
$handleRunStopResolved = Function-Body $main 'handleRunStopResolvedEffect'
Assert-True ($handleRunStopResolved -match 'focusedRunInstanceId == state\.instanceId' -and $handleRunStopResolved -match 'focusedRunInstanceId = null') 'resolved stops must clear only the matching shell display focus.'
Assert-True ($main -notmatch 'fun\s+setRuntimeState\s*\(|shouldIgnoreRuntimeStateAfterUserStop|activeRunInstanceIds|runtimeStates') 'MainActivity must not retain a parallel runtime-state writer or stale-callback interpreter.'
Assert-True ($taskManagerStore -match '(?s)fun endProcess\(context: Context, pid: Int\).*ContainerProcessStore\.terminate\(context\.applicationContext, pid, force = true\)') 'task manager pid-only manual end-process must keep force termination fallback.'
Assert-True ($taskManagerStore -match 'fun endProcess\(context: Context, item: TaskManagerProcessItem' -and $taskManagerStore -match 'ProotOwnerProcessTerminator\.terminate\(appContext, ownerId\)' -and $taskManagerStore -match 'TaskManagerProcessStopTargetResolver::ownerId' -and $taskManagerStore -match 'internal object TaskManagerProcessStopTargetResolver' -and $taskManagerStore -match 'item\.id\.startsWith\("root-"\)' -and $taskManagerStore -match 'ownerId\.startsWith\("card:"\)' -and $taskManagerStore -match 'ownerId\.startsWith\("resource:"\)' -and $taskManagerStore -match 'ownerId\.startsWith\("terminal:"\)' -and $taskManagerStore -notmatch 'missedOwner') 'task manager manual end-process must reserve owner stop for root rows; concrete process rows must stay pid-only.'
Assert-True ($androidRuntimeManagementGateway -match 'TaskManagerStore\.endProcess\(appContext, item, pid\)') 'runtime management manual process stop must pass owner facts through the Android gateway.'
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
$terminalStep = Member-Function-Body $androidRecipeExecutor 'executeTerminal'
Assert-True ($main -notmatch 'openCardRunBlankTerminal|withTerminalOwner') 'MainActivity must not recreate the removed blank CardRun terminal path.'
Assert-True ($terminalStep -match 'RuntimeOwnerIdentity\.terminal\(' -and $terminalStep -match 'terminalOwner\.environment\(\)') 'terminal recipe steps must launch with the structured terminal owner environment.'
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
Assert-True ($taskManagerStore -match 'internal class TaskManagerSnapshotGrace' -and $taskManagerStore -match 'snapshotGrace = TaskManagerSnapshotGrace\(EMPTY_PROCESS_GRACE_MS\)' -and $taskManagerStore -match 'snapshotGrace\.accept\(filtered\)' -and $taskManagerStore -match 'scheduleEmptyExpiry\(decision\.emptyExpiryDelayMs') 'task manager snapshots must smooth transient empty collector gaps and still expire confirmed empty snapshots.'
Assert-True ($taskManagerStore -match 'private fun ProotLiveProcessEntry\.ownerSource' -and $taskManagerStore -match 'entriesByPid\[parentPid\]' -and $taskManagerStore -match 'val ownerEntry = ownerSource\(entriesByPid\)') 'task manager PRoot rows must inherit owner identity from parent tracees when child events are missing tags.'
Assert-True ($runtimeManagementProjector -match 'val topology = snapshot\.topology' -and $runtimeManagementProjector -notmatch 'assignProcesses\(|matchScore\(|expectedOwnerId\(') 'runtime management grouping must consume the explicit topology and must not restore score-based ownership guesses.'
Assert-True ($instanceRuntimeTopology -match 'run\.ownedRuntimeOwnerIds' -and $instanceRuntimeTopology -match 'run\.runtimeRootOwnerId' -and $instanceRuntimeTopology -match 'run\.runtimeOwnerId') 'instance topology must consume the persisted root and leaf owner identities.'
Assert-True ($instanceRuntimeTopology -match 'when \(candidates\.size\)' -and $instanceRuntimeTopology -match 'ambiguous \+= process\.id') 'instance topology must preserve ambiguous ownership instead of choosing an arbitrary run.'
Assert-True ($instanceRuntimeTopology -match 'process\.parentPid' -and $instanceRuntimeTopology -match 'assignments\[parent\.id\]') 'instance topology may inherit ownership only from a uniquely identified real parent process.'
Assert-True ($prootPoolPlan -match 'val ownerContainerCount: Int') 'PRoot pool plan must expose owner container count.'
Assert-True ($prootPoolPlan -match 'val ownerContainerTraceeCount: Int') 'PRoot pool plan must expose owner tracee count.'
Assert-True ($prootPoolPlan -match 'entry\.ownerKind == RuntimeRootOwnerKind\.CARD' -and $prootPoolPlan -match 'entry\.ownerKind == RuntimeRootOwnerKind\.RESOURCE') 'PRoot pool plan must derive owner container pressure from card/resource owner roots.'
Assert-True ($prootPoolPlan -match 'proot_pool_plan_owner_container_count' -and $prootPoolPlan -match 'proot_pool_plan_owner_container_tracee_count') 'PRoot pool env output must include owner container pressure.'
Assert-True ($runtimeMemoryLifecycleRuleTrigger -match 'prootRule\(snapshot, now\)\?\.let\(records::add\)' -and $runtimeMemoryLifecycleRuleTrigger -match 'records\.hasProotCapacityActuatorRequest\(\)') 'PRoot capacity actuator must still receive approved PRoot pool actions when generic lifecycle reclaim is disabled.'
Assert-True ($backgroundRuntimeRegistry -match 'PROOT_CAPACITY_WORKER_INITIAL_COUNT = 2') 'PRoot capacity registry must pre-register an inactive second worker for auto-bound scale-out.'
Assert-True ($prootOwnerTerminator -match 'object ProotOwnerProcessTerminator') 'owner stop must have a dedicated PRoot owner terminator.'
Assert-True ($prootOwnerTerminator -match 'ProotTelemetryStore\.refreshBlocking') 'owner stop must use blocking telemetry refresh for residue checks.'
Assert-True ($prootOwnerTerminator -match 'WorkSurfaceRuntimeBridge\.buildShellExecConfig' -and $prootOwnerTerminator -match 'buildUbuntuSignalPayload' -and $prootOwnerTerminator -match 'OWNER_STOP_DEADLINE_MS = 10_000L') 'owner stop must run the bounded TERM and KILL transaction inside Ubuntu.'
Assert-True ($prootOwnerTerminator -match 'kf_owner_pids' -and $prootOwnerTerminator -match 'kf_owner_pgids' -and $prootOwnerTerminator -match 'kf_signal_pgids' -and $prootOwnerTerminator -match 'kill -.*-- "-.*kf_pgid' -and $prootOwnerTerminator -match 'kill -.*kf_pid') 'owner stop must signal both Ubuntu tracee pids and their process groups.'
Assert-True ($prootOwnerTerminator -match 'ProotTelemetryStore\.retireOwnerTracees' -and $prootOwnerTerminator -match 'confirmAndRetire' -and $prootOwnerTerminator -match 'probeUbuntuLiveTargets') 'owner stop must tombstone stale telemetry only after direct Ubuntu pid and process-group probes are silent.'
Assert-True ($prootOwnerTerminator -notmatch 'Os\.kill' -and $prootOwnerTerminator -notmatch 'sendSignal\(-') 'owner stop must not manage owner processes through Android platform signals.'
Assert-True ($prootOwnerTerminator -match '__kite_owner_stop_owner' -and $prootOwnerTerminator -match '__kite_stop_remaining') 'owner stop must report owner and final remaining tracees.'
Assert-True ($runtimeReclaimer -match 'fun reclaimOwnerRuntime' -and $runtimeReclaimer -match 'ProotOwnerProcessTerminator\.terminate') 'RuntimeReclaimer explicit owner reclaim must terminate through the PRoot owner terminator.'
Assert-True ($runtimeReclaimer -match 'ownerProcessTerminateRequestCount' -and $runtimeReclaimer -match 'explicit_owner_process_terminate') 'RuntimeReclaimer must record explicit owner process terminate requests.'
Assert-True ($runtimeReclaimer -match '(?s)private fun String\.isExplicitOwnerReclaimId\(\).*startsWith\("card:"\).*startsWith\("resource:"\).*startsWith\("terminal:"\)') 'RuntimeReclaimer owner reclaim must reject non card/resource/terminal owner ids.'
Assert-True ($runtimeReclaimer -match 'RuntimeRootOwnerKind\.CARD,\s*\r?\n\s*RuntimeRootOwnerKind\.RESOURCE,\s*\r?\n\s*RuntimeRootOwnerKind\.TERMINAL -> false') 'RuntimeReclaimer automatic candidate selection must not auto-reclaim interactive owner roots.'
Assert-True ($bridgeClient -match 'ProotOwnerProcessTerminator\.terminate') 'bridge stop must invoke the PRoot owner terminator.'
Assert-True ($bridgeClient -match 'private fun stopOwnerProcesses') 'bridge stop must collect owner stop output centrally.'
Assert-True ($terminalSessionController -match 'private fun stopTerminalOwnerProcesses') 'terminal stop must have a dedicated owner stop hook.'
Assert-True ($terminalSessionController -match 'ProotOwnerProcessTerminator\.terminateTerminalSession\(appContext, cleanSessionId\)') 'terminal stop must invoke the session-aware PRoot owner terminator.'
Assert-True ($prootOwnerTerminator -match 'RuntimeOwnerIdentity\.terminalSessionId\(it\) == cleanSessionId') 'terminal stop must resolve every structured leaf owner belonging to the terminal session.'
Assert-True ($terminalSessionController -match 'attachingSessionIds' -and $terminalSessionController -match 'waitForSessionAttach') 'terminal session attach must be idempotent so a terminal owner cannot be split across duplicate PRoot sessions.'
Assert-True ($terminalSessionController -notmatch 'retireOwnerTracees' -and $prootOwnerTerminator -match 'confirmAndRetire') 'terminal stop must not tombstone telemetry outside the directly probed owner terminator.'
Assert-True ($bridgeClient -match 'OwnerStopOutputEvidence\.isConfirmed' -and $ownerStopOutputEvidence -match '__kite_stop_remaining_pgid:' -and $ownerStopOutputEvidence -match '__kite_owner_stop_outcome:') 'bridge residue parsing must reject pid, process-group, and unconfirmed owner evidence through one parser.'
Assert-True ($androidRecipeExecutor -match '__kite_stop_remaining_pgid:' -and $androidRecipeExecutor -match 'ownerOutcomeUnconfirmed' -and $androidRecipeExecutor -match 'remainingProcessIds = remaining') 'Execution adapter residue parsing must preserve pid, process-group, and owner outcome evidence.'
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

Assert-True ($androidRecipeActionGateway -match '(?s)override fun resolveState\b.*CardRunStore::get' -and $androidRecipeActionGateway -match 'orchestrator\.stop\(state\.instanceId\)') 'Shared recipe stop must resolve the latest CardRunStore state and submit its instance through RunOrchestrator.'
Assert-True ($main -notmatch 'fun\s+(stopRecipeByCardInstanceId|stopRecipeWithOrchestrator)\s*\(') 'MainActivity must not retain a second stop intake.'
Assert-True ($main -match 'is RunExecutionEffect\.StopResolved -> handleRunStopResolvedEffect\(effect\)') 'Stop resolution must return to the visible shell through the shared execution Effect contract.'
Assert-True ($stopCoordinator -match 'RecipeStopRequest' -and $stopCoordinator -match 'terminalSessionId == null' -and $stopCoordinator -match 'hasBridgeProcessBinding') 'StopCoordinator must distinguish terminal-only state from retained process bindings.'
Assert-True ($androidRecipeExecutor -match 'bridgeClient\.stopProcessBinding' -and $androidRecipeExecutor -match 'cardInstanceId = request\.instanceId') 'Execution adapter must stop retained process bindings with card ownership identity.'
Assert-True ($runOrchestrator -match '(?s)private fun stoppedMutation.*clearRunBinding = true.*clearTerminalSession = true.*clearNextActionUrl = true' -and $cardRunStore -match 'runtimeRootOwnerId = if \(clearRunBinding\) null') 'Logical stop must clear root and leaf ownership, terminal, process, and URL bindings instead of restoring stale facts.'
Assert-True ($bridgeClient -match 'scheduleResidualReap' -and $prootOwnerTerminator -match 'ownersBeingReaped' -and $prootOwnerTerminator -match 'RESIDUAL_REAP_DELAYS_MS') 'Unsettled owner cleanup must continue in a deduplicated old-generation background reaper.'
Assert-True ($runtimeManagementCoordinator -match 'staleDescendantRemains' -and $runtimeManagementCoordinator -notmatch 'process\.ownerId in target\.ownerIds') 'Runtime management confirmation must wait for old-generation run facts but must not let residual processes block a new generation.'
Assert-True ($androidRecipeExecutor -match 'manualKillObserved = bridgeConfirmedStop' -and $androidRecipeExecutor -match 'MANUAL_STOP_KILLED_REGEX\.containsMatchIn\(observation\)' -and $stopCoordinator -match 'result\.manualKillObserved') 'Manual Killed output must be interpreted by the execution and stop contracts instead of page callbacks.'
Assert-True ($runOrchestrator -match 'current\.createdAt != previousState\.createdAt \|\| current\.status != CardRunStatus\.Stopping') 'Late stop callbacks must be rejected against the current run generation and state.'
Assert-True ($main -notmatch 'legacyStopRecipeByCardInstanceId|handleStopResultV2|bridgeFailureArrivedAfterManualStop|stopRemainingProcesses') 'MainActivity must not retain a second stop result interpreter.'

$cardRunRestoreDrop = Function-Body $cardRunStore 'CardRunState.shouldDropCurrentAfterProcessRestore'
Assert-True ($cardRunRestoreDrop -match 'status\.endsHistoryEntry') 'restored ended card-run states should stay out of the current run list.'
Assert-True ($cardRunRestoreDrop -match 'hasRunBinding') 'restored run bindings should stay out of the current run list.'

$runtimeManagementCard = Member-Function-Body $runtimeManagementScreen 'createRunCard'
Assert-True ($runtimeManagementCard -match 'setOnClickListener \{ toggleRun\(run\.instanceId\) \}') 'run management row card should expand from the whole card click.'
Assert-True ($runtimeManagementCard -match 'LinearLayout\.LayoutParams\(dp\(34\), dp\(34\)\)') 'run management card icon should stay compact like install wizard rows.'
$runtimeManagementDetails = Member-Function-Body $runtimeManagementScreen 'rebuildRunDetails'
Assert-True ($runtimeManagementDetails -match 'run\.surfaces\.forEach' -and $runtimeManagementDetails -match 'run\.mainProcess' -and $runtimeManagementDetails -match 'run\.childProcesses') 'expanded run management card must render projected surfaces and process rows.'
Assert-True ($runtimeManagementProjector -match 'CardRunSurface\.Report' -and $runtimeManagementProjector -match 'CardRunSurface\.Terminal' -and $runtimeManagementProjector -match 'CardRunSurface\.Web') 'runtime management projection must preserve report, terminal, and web surfaces.'
Assert-True ($runtimeManagementProjector -match 'private fun CardRunState\.hasReportSurface') 'runtime management projection must surface SH reports from existing run facts.'
Assert-True ($runtimeManagementScreen -notmatch 'runManagementOwnershipRows|runManagementExitSummary|commandLine') 'runtime management screen must not expose duplicated ownership debug rows or raw command lines.'

$showManage = Function-Body $main 'showResourceManage'
Assert-True ($showManage -match 'showFeatureFragment' -and $showManage -match 'ResourceManageFragment') 'showResourceManage must route to the owning resource feature.'
Assert-True ($main -notmatch 'ResourceManageHost|renderResourceManageInto|requestResourceManageRefresh') 'resource management must not delegate rendering or refresh back to MainActivity.'
Assert-True ($resourceManageFragment -match 'ResourceFeatureFragment' -and $resourceManageFragment -match 'observeResourceState') 'resource management must observe the shared resource feature state.'
Assert-True ($resourceManageScreen -notmatch 'resourceCatalog\(|planSnapshot\(|registrySnapshot\(|KiteResourceInstallStore') 'resource management screen must only project supplied UI state.'

Assert-True ($resourceInstallWizardPresentation -match 'KiteResourceInstallStepUiProjector\.project') 'install wizard rows must consume the shared step projection.'
Assert-True ($resourceInstallWizardScreen -match 'row\.projection\.statusLabel' -and $resourceInstallWizardScreen -notmatch 'statusLabel\s*=\s*when') 'install wizard rows must render the shared projection without a parallel status-label decision tree.'
Assert-True ($resourceInstallWizardScreen -notmatch 'CardRunStore|KiteResourceInstallStore|MainActivity') 'install wizard screen must not read runtime stores or the shell directly.'
Assert-True ($main -notmatch 'ResourceInstallWizardBinding|ResourceInstallWizardUiState|requestVisibleResourceInstallWizardRefresh|resourceInstallWizardContent') 'MainActivity must not retain the legacy install-wizard render and refresh chain.'
Assert-True ($resourceFeatureFragment -match 'gateway\.changes\.collect' -and $resourceFeatureFragment -match 'ResourceFeatureAction\.ReconcileFacts') 'resource feature must reconcile owner signals directly instead of relying on an Activity cache.'
Assert-True ($resourceFeatureController -match 'gateway\.registrySnapshot' -and $resourceFeatureController -match 'KiteResourceRuntimeFactsProjector\.project') 'resource feature projection must consume shared runtime facts from its gateway.'
Assert-True ($resourceActionWorkflow -match 'interface\s+ResourceActionGateway' -and $resourceActionWorkflow -notmatch 'android\.|androidx\.|CardRunStore|KiteResourceInstallStore') 'resource action workflow must remain an Android-free application contract.'
Assert-True ($androidResourceActionGateway -match 'installWizardEffect' -and $androidResourceActionGateway -match 'CardRunStore\.update' -and $androidResourceActionGateway -notmatch 'MainActivity|android\.view|android\.widget') 'resource action adapter must own install-wizard run facts without depending on a page.'
Assert-True ($main -match 'resourceActionWorkflowCoordinator\.dispatch' -and $main -match 'ResourceActionEffect\.OpenInstallWizard' -and $main -notmatch 'resourceCatalogDirty|resourceCatalog\(|resourceRuntimeFactsFromStore|observeResourceInstallSignals|showResourceInstallWizard') 'MainActivity must only dispatch resource intents and interpret route effects.'
Assert-True ($homeFeatureViewSupport -match 'item\.projection\.primaryAction' -and $homeFeatureViewSupport -match 'localizedBadge\(item\)' -and $homeFeatureViewSupport -match 'item\.projection\.(problem|live)') 'Home card action and localized badge must consume the shared projected UI state.'
Assert-True ($runtimeManagementProjector -match 'KiteCardRunUiProjector\.project' -and $runtimeManagementScreen -match 'statusColors\(run\.statusTone\)') 'run management status colors must consume shared projected card-run UI state.'
$cardRunOnDestroy = Member-Function-Body $cardRunActivity 'onDestroy'
Assert-True ($webWorkbenchScreen -match 'automationController\.closeActiveSession\(\)' -and $webWorkbenchScreen -match 'webView\.destroy\(\)') 'Workbench display disposal must close only its automation display session and WebView.'
Assert-True ($cardRunOnDestroy -match 'surfaceController\.detach\(\)' -and $cardRunOnDestroy -match 'surfaceHost\?\.dispose\(\)') 'CardRunActivity destroy must detach and dispose only its visible surface.'
Assert-True ($cardRunOnDestroy -notmatch 'stopCurrentRun|stopRecipe|CardRunStore\.(stop|remove)|clearPlan|markFailed') 'CardRunActivity destroy must not treat a closed run surface as a stopped or failed task.'
$terminalDestroyView = Member-Function-Body $terminalFragment 'onDestroyView'
Assert-True ($terminalDestroyView -match 'TerminalRuntimeHost\.detachUi\(this\)') 'Terminal view destroy must detach the UI from the process-level session host.'
Assert-True ($terminalDestroyView -notmatch 'TerminalRuntimeHost\.release|TerminalRuntimeHost\.endSession|stopCurrentSession') 'Terminal view destroy must not stop or release the terminal session.'
Assert-True ($runtimeReclaimer -match 'RuntimeRootOwnerKind\.CARD,\s*RuntimeRootOwnerKind\.RESOURCE,\s*RuntimeRootOwnerKind\.TERMINAL\s*->\s*false') 'Cards, resources, and terminal owners must remain excluded from generic automatic reclaim.'
$lowMemoryResponse = Member-Function-Body $runtimePressureResponder 'onLowMemory'
$pressureHandler = Function-Body $runtimePressureResponder 'handlePressure'
Assert-True ($lowMemoryResponse -match 'RuntimeLifecycleSignalStore\.onLowMemory\(\)' -and $lowMemoryResponse -notmatch '\bonTrimMemory\s*\(') 'Low-memory handling must preserve its own lifecycle fact instead of rewriting it as a regular trim event.'
Assert-True ($pressureHandler -match 'RuntimeFrameCoordinator\.refreshTaskManager|RuntimeFrameCoordinator\.refreshProcessSnapshot') 'Memory pressure must enter the existing runtime snapshot refresh chain.'
Assert-True ($pressureHandler -notmatch 'destroyProcess|terminateForRuntimeReclaimer|stopRecipe|endSession') 'Memory callbacks must not directly terminate runs, processes, or terminal sessions.'
Assert-True ($runtimePressureResponder -match 'level == ComponentCallbacks2\.TRIM_MEMORY_UI_HIDDEN' -and $runtimePressureResponder -match 'reason = "visibility_only"') 'UI_HIDDEN is a visibility signal and must not consume the memory-pressure cooldown window.'
$renderTerminalPanel = Function-Body $terminalFragment 'renderTerminalPanelPage'
$buildTerminalPanel = Function-Body $terminalFragment 'buildTerminalPanelPage'
Assert-True ($renderTerminalPanel -match 'TerminalPanelActionRegistry\.snapshot\(\)') 'Terminal panel pages must come from the action registry.'
Assert-True ($buildTerminalPanel -match 'action\.execute\(terminalPanelActionHost, anchor\)') 'Terminal panel tiles must execute registered actions through the terminal host contract.'
Assert-True (
    $terminalPanelActionRegistry -match 'handler\.execute\(host, anchor\)\s*host\.applyComposerEffect\(composerEffect\)'
) 'Terminal action execution must apply the registered handler and its typed composer effect through the same host contract.'
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
