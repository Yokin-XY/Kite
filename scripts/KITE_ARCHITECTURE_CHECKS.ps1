param(
    [string]$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
)

$ErrorActionPreference = 'Stop'
$failures = New-Object System.Collections.Generic.List[string]

function Assert-Architecture {
    param(
        [bool]$Condition,
        [string]$Message
    )
    if (-not $Condition) {
        $script:failures.Add($Message)
    }
}

function Count-Matches {
    param(
        [string]$Source,
        [string]$Pattern
    )
    return [regex]::Matches($Source, $Pattern).Count
}

function Imported-Types {
    param([string]$Source)
    return @(
        [regex]::Matches($Source, '(?m)^\s*import\s+([^\s;]+)') |
            ForEach-Object { $_.Groups[1].Value }
    )
}

$baselinePath = Join-Path $Root 'docs/stabilization/architecture-baseline.json'
$mainPath = Join-Path $Root 'app/src/main/java/com/kite/app/MainActivity.kt'
$taskContractInitializerPath = Join-Path $Root 'app/src/main/java/com/kite/app/KiteTaskContractInitializer.kt'
$cardRunPath = Join-Path $Root 'app/src/main/java/com/kite/app/CardRunActivity.kt'
$runSurfaceHostPath = Join-Path $Root 'app/src/main/java/com/kite/app/feature/runsurface/RunSurfaceHost.kt'
$runtimeManagementSnapshotPath = Join-Path $Root 'app/src/main/java/com/kite/app/application/runtimemanagement/RuntimeManagementSnapshot.kt'
$runtimeManagementGatewayPath = Join-Path $Root 'app/src/main/java/com/kite/app/application/runtimemanagement/RuntimeManagementGateway.kt'
$runtimeManagementCoordinatorPath = Join-Path $Root 'app/src/main/java/com/kite/app/application/runtimemanagement/RuntimeManagementCoordinator.kt'
$runtimeManagementContractPath = Join-Path $Root 'app/src/main/java/com/kite/app/feature/runtimemanagement/RuntimeManagementFeatureContract.kt'
$runtimeManagementControllerPath = Join-Path $Root 'app/src/main/java/com/kite/app/feature/runtimemanagement/RuntimeManagementFeatureController.kt'
$runtimeManagementProjectorPath = Join-Path $Root 'app/src/main/java/com/kite/app/feature/runtimemanagement/RuntimeManagementProjector.kt'
$androidRuntimeManagementGatewayPath = Join-Path $Root 'app/src/main/java/com/kite/app/platform/runtimemanagement/AndroidRuntimeManagementGateway.kt'
$installSurfaceBindingPath = Join-Path $Root 'app/src/main/java/com/kite/app/shell/RunInstallWizardSurfaceBinding.kt'
$legacyFeatureInstallBindingPath = Join-Path $Root 'app/src/main/java/com/kite/app/feature/runsurface/RunInstallWizardSurfaceBinding.kt'
$screenRouterPath = Join-Path $Root 'app/src/main/java/com/kite/app/shell/AppNavigator.kt'
$settingsGatewayPath = Join-Path $Root 'app/src/main/java/com/kite/app/application/settings/SettingsGateway.kt'
$settingsControllerPath = Join-Path $Root 'app/src/main/java/com/kite/app/feature/settings/SettingsFeatureController.kt'
$settingsFragmentPath = Join-Path $Root 'app/src/main/java/com/kite/app/feature/settings/SettingsFragment.kt'
$androidSettingsGatewayPath = Join-Path $Root 'app/src/main/java/com/kite/app/platform/settings/AndroidSettingsGateway.kt'
$onboardingCoordinatorPath = Join-Path $Root 'app/src/main/java/com/kite/app/application/onboarding/FirstRunOnboardingCoordinator.kt'
$surfaceEffectPath = Join-Path $Root 'app/src/main/java/com/kite/app/application/surface/SurfaceEffect.kt'
$terminalSurfaceContractPath = Join-Path $Root 'app/src/main/java/com/kite/app/feature/terminal/TerminalSurfaceResultContract.kt'
$terminalFragmentPath = Join-Path $Root 'app/src/main/kotlin/com/kite/app/ui/terminal/TerminalFragment.kt'
$legacyTerminalChromeHostPath = Join-Path $Root 'app/src/main/kotlin/com/kite/app/ui/terminal/TerminalChromeHost.kt'
$recipeRawJsonFragmentPath = Join-Path $Root 'app/src/main/java/com/kite/app/feature/recipeeditor/RecipeRawJsonFragment.kt'
$legacyRecipeRawJsonFragmentPath = Join-Path $Root 'app/src/main/java/com/kite/app/RecipeRawJsonFragment.kt'
$runHistoryGatewayPath = Join-Path $Root 'app/src/main/java/com/kite/app/application/runs/RunHistoryGateway.kt'
$androidRunHistoryGatewayPath = Join-Path $Root 'app/src/main/java/com/kite/app/platform/runs/AndroidRunHistoryGateway.kt'
$runHistoryFragmentPath = Join-Path $Root 'app/src/main/java/com/kite/app/feature/runhistory/RunHistoryFragment.kt'
$runHistoryScreenPath = Join-Path $Root 'app/src/main/java/com/kite/app/feature/runhistory/RunHistoryScreen.kt'
$resourceMoreFragmentPath = Join-Path $Root 'app/src/main/java/com/kite/app/feature/resources/ResourceMoreFragment.kt'
$resourceRawJsonFragmentPath = Join-Path $Root 'app/src/main/java/com/kite/app/feature/resources/ResourceRawJsonFragment.kt'
$resourceActionWorkflowPath = Join-Path $Root 'app/src/main/java/com/kite/app/application/resources/ResourceActionWorkflow.kt'
$androidResourceActionGatewayPath = Join-Path $Root 'app/src/main/java/com/kite/app/platform/resources/AndroidResourceActionGateway.kt'
$cardRunSpecialRecipesPath = Join-Path $Root 'app/src/main/java/com/kite/app/application/runs/CardRunSpecialRecipes.kt'
$legacyCardRunSpecialRecipesPath = Join-Path $Root 'app/src/main/java/com/kite/app/feature/runsurface/CardRunSpecialRecipes.kt'
$recipeActionWorkflowPath = Join-Path $Root 'app/src/main/java/com/kite/app/application/runs/RecipeActionWorkflow.kt'
$androidRecipeActionGatewayPath = Join-Path $Root 'app/src/main/java/com/kite/app/platform/runs/AndroidRecipeActionGateway.kt'
$desktopOpenWorkflowPath = Join-Path $Root 'app/src/main/java/com/kite/app/application/runs/DesktopOpenWorkflow.kt'
$androidDesktopOpenGatewayPath = Join-Path $Root 'app/src/main/java/com/kite/app/platform/runs/AndroidDesktopOpenGateway.kt'
$runtimeOwnerProbeWorkflowPath = Join-Path $Root 'app/src/main/java/com/kite/app/application/runs/RuntimeOwnerProbeWorkflow.kt'
$androidRuntimeOwnerProbeGatewayPath = Join-Path $Root 'app/src/main/java/com/kite/app/platform/runs/AndroidRuntimeOwnerProbeGateway.kt'
$browserOpenWorkflowPath = Join-Path $Root 'app/src/main/java/com/kite/app/application/browser/BrowserOpenWorkflow.kt'
$androidBrowserOpenGatewayPath = Join-Path $Root 'app/src/main/java/com/kite/app/platform/browser/AndroidBrowserOpenGateway.kt'
$installApkWorkflowPath = Join-Path $Root 'app/src/main/java/com/kite/app/application/packages/InstallApkWorkflow.kt'
$androidInstallApkGatewayPath = Join-Path $Root 'app/src/main/java/com/kite/app/platform/packages/AndroidInstallApkGateway.kt'
$sourceRoots = @(
    (Join-Path $Root 'app/src/main/java/com/kite/app'),
    (Join-Path $Root 'app/src/main/kotlin/com/kite/app')
)

Assert-Architecture (Test-Path $baselinePath) 'Architecture baseline is missing.'
Assert-Architecture (Test-Path $mainPath) 'MainActivity source is missing.'
Assert-Architecture (Test-Path $taskContractInitializerPath) 'Task contract initializer is missing.'
Assert-Architecture (Test-Path $cardRunPath) 'CardRunActivity source is missing.'
Assert-Architecture (Test-Path $runSurfaceHostPath) 'RunSurfaceHost source is missing.'
Assert-Architecture (Test-Path $runtimeManagementSnapshotPath) 'Runtime-management snapshot contract is missing.'
Assert-Architecture (Test-Path $runtimeManagementGatewayPath) 'Runtime-management gateway contract is missing.'
Assert-Architecture (Test-Path $runtimeManagementCoordinatorPath) 'Runtime-management coordinator is missing.'
Assert-Architecture (Test-Path $runtimeManagementContractPath) 'Runtime-management feature contract is missing.'
Assert-Architecture (Test-Path $runtimeManagementControllerPath) 'Runtime-management feature controller is missing.'
Assert-Architecture (Test-Path $runtimeManagementProjectorPath) 'Runtime-management projector is missing.'
Assert-Architecture (Test-Path $androidRuntimeManagementGatewayPath) 'Android runtime-management gateway is missing.'
Assert-Architecture (Test-Path $installSurfaceBindingPath) 'Run install-wizard shell adapter is missing.'
Assert-Architecture (-not (Test-Path $legacyFeatureInstallBindingPath)) 'Run install-wizard adapter must not live inside the run-surface feature.'
Assert-Architecture (Test-Path $screenRouterPath) 'AppNavigator source is missing.'
Assert-Architecture (Test-Path $settingsGatewayPath) 'Settings gateway contract is missing.'
Assert-Architecture (Test-Path $settingsControllerPath) 'Settings feature controller is missing.'
Assert-Architecture (Test-Path $settingsFragmentPath) 'Settings feature fragment is missing.'
Assert-Architecture (Test-Path $androidSettingsGatewayPath) 'Android settings gateway is missing.'
Assert-Architecture (Test-Path $onboardingCoordinatorPath) 'First-run onboarding coordinator is missing.'
Assert-Architecture (Test-Path $surfaceEffectPath) 'Generic surface effect contract is missing.'
Assert-Architecture (Test-Path $terminalSurfaceContractPath) 'Terminal surface result contract is missing.'
Assert-Architecture (Test-Path $terminalFragmentPath) 'Terminal fragment is missing.'
Assert-Architecture (-not (Test-Path $legacyTerminalChromeHostPath)) 'TerminalChromeHost must not return.'
Assert-Architecture (Test-Path $recipeRawJsonFragmentPath) 'Recipe raw-JSON feature fragment is missing.'
Assert-Architecture (-not (Test-Path $legacyRecipeRawJsonFragmentPath)) 'Legacy Activity-hosted raw-JSON fragment must not return.'
Assert-Architecture (Test-Path $runHistoryGatewayPath) 'Run-history application gateway is missing.'
Assert-Architecture (Test-Path $androidRunHistoryGatewayPath) 'Android run-history adapter is missing.'
Assert-Architecture (Test-Path $runHistoryFragmentPath) 'Run-history feature fragment is missing.'
Assert-Architecture (Test-Path $runHistoryScreenPath) 'Run-history feature screen is missing.'
Assert-Architecture (Test-Path $resourceMoreFragmentPath) 'Resource-more feature fragment is missing.'
Assert-Architecture (Test-Path $resourceRawJsonFragmentPath) 'Resource raw-JSON feature fragment is missing.'
Assert-Architecture (Test-Path $resourceActionWorkflowPath) 'Resource action application workflow is missing.'
Assert-Architecture (Test-Path $androidResourceActionGatewayPath) 'Android resource action adapter is missing.'
Assert-Architecture (Test-Path $cardRunSpecialRecipesPath) 'Application special-run recipe factory is missing.'
Assert-Architecture (-not (Test-Path $legacyCardRunSpecialRecipesPath)) 'Special-run recipe factory must not live inside a Feature.'
Assert-Architecture (Test-Path $recipeActionWorkflowPath) 'Recipe action application workflow is missing.'
Assert-Architecture (Test-Path $androidRecipeActionGatewayPath) 'Android recipe action adapter is missing.'
Assert-Architecture (Test-Path $desktopOpenWorkflowPath) 'Desktop-open application workflow is missing.'
Assert-Architecture (Test-Path $androidDesktopOpenGatewayPath) 'Android desktop-open adapter is missing.'
Assert-Architecture (Test-Path $runtimeOwnerProbeWorkflowPath) 'Runtime owner-probe application workflow is missing.'
Assert-Architecture (Test-Path $androidRuntimeOwnerProbeGatewayPath) 'Android runtime owner-probe adapter is missing.'
Assert-Architecture (Test-Path $browserOpenWorkflowPath) 'Browser-open application workflow is missing.'
Assert-Architecture (Test-Path $androidBrowserOpenGatewayPath) 'Android browser-open adapter is missing.'
Assert-Architecture (Test-Path $installApkWorkflowPath) 'Install-APK application workflow is missing.'
Assert-Architecture (Test-Path $androidInstallApkGatewayPath) 'Android install-APK adapter is missing.'

if ($failures.Count -eq 0) {
    $baseline = Get-Content $baselinePath -Raw -Encoding UTF8 | ConvertFrom-Json
    $main = [System.IO.File]::ReadAllText($mainPath, [System.Text.Encoding]::UTF8)
    $taskContractInitializer = [System.IO.File]::ReadAllText($taskContractInitializerPath, [System.Text.Encoding]::UTF8)
    $cardRun = [System.IO.File]::ReadAllText($cardRunPath, [System.Text.Encoding]::UTF8)
    $runSurfaceHost = [System.IO.File]::ReadAllText($runSurfaceHostPath, [System.Text.Encoding]::UTF8)
    $runtimeManagementSnapshot = [System.IO.File]::ReadAllText($runtimeManagementSnapshotPath, [System.Text.Encoding]::UTF8)
    $runtimeManagementGateway = [System.IO.File]::ReadAllText($runtimeManagementGatewayPath, [System.Text.Encoding]::UTF8)
    $runtimeManagementCoordinator = [System.IO.File]::ReadAllText($runtimeManagementCoordinatorPath, [System.Text.Encoding]::UTF8)
    $runtimeManagementContract = [System.IO.File]::ReadAllText($runtimeManagementContractPath, [System.Text.Encoding]::UTF8)
    $runtimeManagementController = [System.IO.File]::ReadAllText($runtimeManagementControllerPath, [System.Text.Encoding]::UTF8)
    $runtimeManagementProjector = [System.IO.File]::ReadAllText($runtimeManagementProjectorPath, [System.Text.Encoding]::UTF8)
    $androidRuntimeManagementGateway = [System.IO.File]::ReadAllText($androidRuntimeManagementGatewayPath, [System.Text.Encoding]::UTF8)
    $screenRouter = [System.IO.File]::ReadAllText($screenRouterPath, [System.Text.Encoding]::UTF8)
    $settingsGateway = [System.IO.File]::ReadAllText($settingsGatewayPath, [System.Text.Encoding]::UTF8)
    $settingsController = [System.IO.File]::ReadAllText($settingsControllerPath, [System.Text.Encoding]::UTF8)
    $settingsFragment = [System.IO.File]::ReadAllText($settingsFragmentPath, [System.Text.Encoding]::UTF8)
    $androidSettingsGateway = [System.IO.File]::ReadAllText($androidSettingsGatewayPath, [System.Text.Encoding]::UTF8)
    $onboardingCoordinator = [System.IO.File]::ReadAllText($onboardingCoordinatorPath, [System.Text.Encoding]::UTF8)
    $surfaceEffect = [System.IO.File]::ReadAllText($surfaceEffectPath, [System.Text.Encoding]::UTF8)
    $terminalSurfaceContract = [System.IO.File]::ReadAllText($terminalSurfaceContractPath, [System.Text.Encoding]::UTF8)
    $terminalFragment = [System.IO.File]::ReadAllText($terminalFragmentPath, [System.Text.Encoding]::UTF8)
    $recipeRawJsonFragment = [System.IO.File]::ReadAllText($recipeRawJsonFragmentPath, [System.Text.Encoding]::UTF8)
    $runHistoryGateway = [System.IO.File]::ReadAllText($runHistoryGatewayPath, [System.Text.Encoding]::UTF8)
    $androidRunHistoryGateway = [System.IO.File]::ReadAllText($androidRunHistoryGatewayPath, [System.Text.Encoding]::UTF8)
    $runHistoryFragment = [System.IO.File]::ReadAllText($runHistoryFragmentPath, [System.Text.Encoding]::UTF8)
    $runHistoryScreen = [System.IO.File]::ReadAllText($runHistoryScreenPath, [System.Text.Encoding]::UTF8)
    $resourceMoreFragment = [System.IO.File]::ReadAllText($resourceMoreFragmentPath, [System.Text.Encoding]::UTF8)
    $resourceRawJsonFragment = [System.IO.File]::ReadAllText($resourceRawJsonFragmentPath, [System.Text.Encoding]::UTF8)
    $resourceActionWorkflow = [System.IO.File]::ReadAllText($resourceActionWorkflowPath, [System.Text.Encoding]::UTF8)
    $androidResourceActionGateway = [System.IO.File]::ReadAllText($androidResourceActionGatewayPath, [System.Text.Encoding]::UTF8)
    $cardRunSpecialRecipes = [System.IO.File]::ReadAllText($cardRunSpecialRecipesPath, [System.Text.Encoding]::UTF8)
    $recipeActionWorkflow = [System.IO.File]::ReadAllText($recipeActionWorkflowPath, [System.Text.Encoding]::UTF8)
    $androidRecipeActionGateway = [System.IO.File]::ReadAllText($androidRecipeActionGatewayPath, [System.Text.Encoding]::UTF8)
    $desktopOpenWorkflow = [System.IO.File]::ReadAllText($desktopOpenWorkflowPath, [System.Text.Encoding]::UTF8)
    $androidDesktopOpenGateway = [System.IO.File]::ReadAllText($androidDesktopOpenGatewayPath, [System.Text.Encoding]::UTF8)
    $runtimeOwnerProbeWorkflow = [System.IO.File]::ReadAllText($runtimeOwnerProbeWorkflowPath, [System.Text.Encoding]::UTF8)
    $androidRuntimeOwnerProbeGateway = [System.IO.File]::ReadAllText($androidRuntimeOwnerProbeGatewayPath, [System.Text.Encoding]::UTF8)
    $browserOpenWorkflow = [System.IO.File]::ReadAllText($browserOpenWorkflowPath, [System.Text.Encoding]::UTF8)
    $androidBrowserOpenGateway = [System.IO.File]::ReadAllText($androidBrowserOpenGatewayPath, [System.Text.Encoding]::UTF8)
    $installApkWorkflow = [System.IO.File]::ReadAllText($installApkWorkflowPath, [System.Text.Encoding]::UTF8)
    $androidInstallApkGateway = [System.IO.File]::ReadAllText($androidInstallApkGatewayPath, [System.Text.Encoding]::UTF8)
    $allSourceFiles = @(
        $sourceRoots |
            Where-Object { Test-Path $_ } |
            ForEach-Object { Get-ChildItem $_ -Recurse -File -Include '*.kt', '*.java' }
    )

    $mainHeader = [regex]::Match(
        $main,
        'open class MainActivity\s*:\s*AppCompatActivity\(\),(?<body>[\s\S]*?)\{'
    ).Groups['body'].Value
    $hostInterfaces = @(
        $mainHeader -split ',' |
            ForEach-Object { $_.Trim() } |
            Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
    ).Count

    $metrics = [ordered]@{
        physicalLines = [System.IO.File]::ReadAllLines($mainPath).Count
        memberFunctions = Count-Matches $main '(?m)^\s{4}(?:override\s+|private\s+|internal\s+|protected\s+|public\s+)?fun\s+[A-Za-z_][A-Za-z0-9_]*\s*\('
        privateFields = Count-Matches $main '(?m)^\s{4}private\s+(?:(?:lateinit|const)\s+)?(?:var|val)\s+[A-Za-z_][A-Za-z0-9_]*'
        hostInterfaces = $hostInterfaces
        resourceRenderDelegates = Count-Matches $main '(?m)^\s{4}override\s+fun\s+renderResource(?:s|Search|Manage|Detail)Into\s*\('
        resourceFunctions = Count-Matches $main '(?m)^\s{4}(?:override\s+|private\s+|internal\s+)?fun\s+(?:showResource|refreshResource|renderResource|buildResource|bindResource|handleResource|startResource|stopResource|openResource)[A-Za-z0-9_]*\s*\('
        runtimeStatesReferences = Count-Matches $main '\bruntimeStates\b'
        screenRouterMainActivityScreenReferences = Count-Matches $screenRouter 'MainActivity\.Screen'
        activitiesInheritingMainActivity = @(
            $allSourceFiles |
                Where-Object {
                    [System.IO.File]::ReadAllText($_.FullName) -match 'class\s+[A-Za-z0-9_]+Activity\s*:\s*MainActivity'
                }
        ).Count
    }

    foreach ($name in @(
        'physicalLines',
        'memberFunctions',
        'privateFields',
        'hostInterfaces',
        'resourceRenderDelegates',
        'resourceFunctions',
        'runtimeStatesReferences'
    )) {
        $limit = [long]$baseline.mainActivity.$name
        Assert-Architecture ([long]$metrics[$name] -le $limit) "MainActivity debt '$name' increased: $($metrics[$name]) > $limit."
    }
    foreach ($name in @('screenRouterMainActivityScreenReferences', 'activitiesInheritingMainActivity')) {
        $limit = [long]$baseline.legacyBoundaryDebt.$name
        Assert-Architecture ([long]$metrics[$name] -le $limit) "Legacy boundary debt '$name' increased: $($metrics[$name]) > $limit."
    }

    Assert-Architecture (
        $cardRun -match 'class\s+CardRunActivity\s*:\s*AppCompatActivity\(\)'
    ) 'CardRunActivity must remain an independent AppCompatActivity shell.'
    Assert-Architecture (
        $cardRun -notmatch 'class\s+CardRunActivity\s*:\s*MainActivity'
    ) 'CardRunActivity must not inherit MainActivity.'
    Assert-Architecture (
        $cardRun -match 'RunSurfaceHost\s*\(' -and
        $cardRun -match 'CardRunLaunchResolver\s*\(' -and
        $cardRun -match 'CardRunStore\.runs\.collect'
    ) 'CardRunActivity must compose launch resolution, shared run facts, and RunSurfaceHost.'
    Assert-Architecture (
        $main -notmatch 'isLegacyCardRunShell|RunSurfaceHost|RunWebSurfaceBinding|RunTerminalSurfaceBinding|RunX11SurfaceBinding|ResourceInstallWizardSurface'
    ) 'MainActivity must not retain the legacy CardRun or install-wizard display shell.'
    Assert-Architecture (
        $main -match 'CardRunIntents\.launchIntent\(' -and
        $main -notmatch 'showCardRunSurface'
    ) 'MainActivity must launch the independent run task instead of rendering a card-run surface.'
    Assert-Architecture (
        $screenRouter -notmatch 'AppDestination\.CardRun|DestinationKind\.RunSurface|NavigationBackAction\.CardRunTask'
    ) 'Main application navigation must not own the independent CardRun task.'
    Assert-Architecture (
        $runSurfaceHost -match 'fun\s+render\s*\(' -and
        $runSurfaceHost -match 'fun\s+reconcile\s*\(' -and
        $runSurfaceHost -match 'fun\s+dispose\s*\('
    ) 'RunSurfaceHost must own render, reconcile, and display disposal entry points.'
    Assert-Architecture (
        $runtimeManagementSnapshot -match 'val\s+runs:\s*List<CardRunState>' -and
        $runtimeManagementSnapshot -match 'val\s+terminals:\s*List<RuntimeManagedTerminal>' -and
        $runtimeManagementSnapshot -match 'val\s+processes:\s*List<RuntimeManagedProcess>'
    ) 'Runtime management must consume one structured run, terminal, and process snapshot.'
    Assert-Architecture (
        $runtimeManagementGateway -match 'val\s+snapshots:\s*StateFlow<RuntimeManagementSnapshot>' -and
        $runtimeManagementGateway -match 'fun\s+refresh\(force:\s*Boolean'
    ) 'Runtime-management Feature must receive snapshots and refresh through one Application gateway.'
    Assert-Architecture (
        $androidRuntimeManagementGateway -match 'combine\(\s*CardRunStore\.runs,\s*TerminalSessionStore\.snapshot,\s*TaskManagerStore\.snapshot,\s*RuntimeHealthStore\.snapshot' -and
        $androidRuntimeManagementGateway -match 'TaskManagerAction\.END_PROCESS in availableActions'
    ) 'Android runtime-management gateway must combine the existing fact owners and preserve process action capabilities.'
    Assert-Architecture (
        $runtimeManagementCoordinator -match 'Requested' -and
        $runtimeManagementCoordinator -match 'AwaitingConfirmation' -and
        $runtimeManagementCoordinator -match 'Failed' -and
        $runtimeManagementCoordinator -match 'fun\s+reconcile\(snapshot:\s*RuntimeManagementSnapshot' -and
        $runtimeManagementCoordinator -notmatch 'CardRunStore|TaskManagerStore|TerminalSessionStore|android\.|androidx\.|Context|View'
    ) 'Runtime-management actions must wait for shared-fact confirmation in a UI-agnostic coordinator.'
    Assert-Architecture (
        $runtimeManagementController -match 'RuntimeManagementFeatureEffect\.OpenSurface' -and
        $runtimeManagementController -match 'RuntimeManagementCommand\.EndProcess' -and
        $runtimeManagementController -notmatch 'CardRunStore|TaskManagerStore|TerminalSessionStore|android\.|androidx\.|\bContext\b|\bView\b'
    ) 'Runtime-management Feature controller must map data actions to coordinator commands or shell effects.'
    Assert-Architecture (
        $runtimeManagementContract -match 'sealed interface RuntimeManagementActionTarget' -and
        $runtimeManagementContract -match 'AwaitingConfirmation' -and
        $runtimeManagementContract -notmatch '\(\)\s*->\s*Unit'
    ) 'Runtime-management UI actions must be data commands with explicit confirmation state, not View callbacks.'
    Assert-Architecture (
        $runtimeManagementProjector -match 'assignProcesses\(' -and
        $runtimeManagementProjector -match 'RuntimeManagementMutation' -and
        $runtimeManagementProjector -notmatch 'TaskManagerStore|TerminalSessionStore|CardRunStore|android\.|androidx\.|Context|View'
    ) 'Runtime-management projection must stay pure and must not read stores or Android UI directly.'
    Assert-Architecture (
        $main -match 'showFeatureFragment\(SettingsFragment\(\),\s*TAG_SETTINGS_FRAGMENT\)' -and
        $main -match 'showFeatureFragment\(ThemeSettingsFragment\(\),\s*TAG_THEME_SETTINGS_FRAGMENT\)' -and
        $main -notmatch 'getSharedPreferences\("kite_(?:theme|app_settings)' -and
        $main -notmatch 'fun\s+(?:settingsRow|settingsSwitchRow|colorPresetRow|themePreviewCard)\s*\('
    ) 'MainActivity must route to the settings feature instead of owning settings persistence or drawing.'
    Assert-Architecture (
        $settingsGateway -match 'val\s+snapshots:\s*StateFlow<SettingsSnapshot>' -and
        $settingsGateway -match 'suspend\s+fun\s+refresh\(\):\s*SettingsSnapshot' -and
        $settingsGateway -notmatch 'android\.|androidx\.'
    ) 'Settings application contract must expose one Android-free snapshot owner.'
    Assert-Architecture (
        $settingsController -match 'SettingsCommand\.SetThemeColor' -and
        $settingsController -match 'SettingsFeatureEffect\.RecentTaskVisibilityChanged' -and
        $settingsController -notmatch 'SharedPreferences|NotificationManager|KiteDropZoneManager|android\.|androidx\.'
    ) 'Settings feature controller must submit data commands and shell effects without platform access.'
    Assert-Architecture (
        $settingsFragment -match 'SettingsFeatureResultContract\.send' -and
        $settingsFragment -notmatch 'getSharedPreferences|NotificationManager|KiteDropZoneManager|MainActivity'
    ) 'Settings fragment must project state and return effects without delegating ownership to MainActivity.'
    Assert-Architecture (
        $androidSettingsGateway -match 'withContext\(Dispatchers\.IO\)' -and
        $androidSettingsGateway -match 'SettingsCommand\.SetBrowserRuntimeMode' -and
        $androidSettingsGateway -notmatch 'android\.view\.|android\.widget\.|Activity'
    ) 'Android settings gateway must keep system probes off the UI thread and must not own views.'
    Assert-Architecture (
        $onboardingCoordinator -match 'AwaitingRuntimePermissionResult' -and
        $onboardingCoordinator -match 'AwaitingAllFilesReturn' -and
        $onboardingCoordinator -match 'store\.writePhase\(FirstRunOnboardingPhase\.' -and
        $onboardingCoordinator -notmatch '(?m)^\s*import\s+(?:android\.|androidx\.|.*RuntimeBootstrapGateway|.*SharedPreferences)'
    ) 'First-run onboarding must persist action phases without copying Android or runtime-readiness facts.'
    Assert-Architecture (
        $surfaceEffect -match 'enum\s+class\s+SurfaceChromeMode' -and
        $surfaceEffect -match 'sealed\s+interface\s+SurfaceEffect' -and
        $surfaceEffect -notmatch 'android\.|androidx\.'
    ) 'Surface display effects must remain Android-free data contracts.'
    Assert-Architecture (
        $terminalSurfaceContract -match 'SurfaceEffect\.SetChromeMode' -and
        $terminalSurfaceContract -match 'SurfaceEffect\.RequestBack' -and
        $terminalSurfaceContract -notmatch '(?m)^\s*import\s+.*(?:MainActivity|CardRunActivity|TerminalChromeHost)'
    ) 'Terminal surface contract must emit generic chrome/back effects without naming a shell.'
    Assert-Architecture (
        $terminalFragment -match 'TerminalSurfaceResultContract\.send' -and
        $terminalFragment -notmatch 'TerminalChromeHost|activity\s+as\?|MainActivity|CardRunActivity|setTerminalDetailMode'
    ) 'Terminal Fragment must not cast back to an Activity host.'
    Assert-Architecture (
        $main -notmatch 'TerminalChromeHost|terminalBottomNavigation|isTerminalDetailMode|terminalContainerId|openTerminalSession' -and
        $cardRun -notmatch 'TerminalChromeHost|openTerminalSession'
    ) 'Application shells must interpret terminal surface effects without terminal-specific host interfaces or fields.'
    Assert-Architecture (
        $recipeRawJsonFragment -match 'RecipeFeatureDependenciesOwner' -and
        $recipeRawJsonFragment -match 'RecipeEditorResultContract\.send' -and
        $recipeRawJsonFragment -notmatch 'activity\s+as\?|MainActivity|RecipeProvider|RecipeRawJsonHost|UiKitProvider'
    ) 'Recipe raw-JSON fragment must load through the feature gateway and return through a result contract.'
    Assert-Architecture (
        $runHistoryGateway -match 'interface\s+RunHistoryGateway' -and
        $runHistoryGateway -notmatch 'android\.|androidx\.|CardRunStore'
    ) 'Run-history application contract must remain Android-free and independent from the concrete store.'
    Assert-Architecture (
        $androidRunHistoryGateway -match 'CardRunStore\.historyForRecipe' -and
        $androidRunHistoryGateway -notmatch 'android\.view|android\.widget|MainActivity|CardRunActivity'
    ) 'Run-history store access must stay in the platform adapter without page dependencies.'
    Assert-Architecture (
        $runHistoryFragment -match 'RunHistoryDependenciesOwner' -and
        $runHistoryFragment -match 'RunHistoryResultContract\.sendBack' -and
        $runHistoryFragment -notmatch 'CardRunStore|MainActivity|activity\s+as\?'
    ) 'Run-history Feature must use the application gateway and return data effects to the shell.'
    Assert-Architecture (
        $runHistoryScreen -notmatch 'CardRunStore|MainActivity|Fragment' -and
        $main -notmatch 'fun\s+(?:recentRunHistoryPanel|runHistoryDetailHeader|readonlyShellReportCard|runHistoryStepRow|showRunHistoryDetail)\s*\('
    ) 'Run-history rendering must stay in its Feature instead of MainActivity.'
    Assert-Architecture (
        $resourceMoreFragment -match 'ResourceMoreScreen' -and
        $resourceMoreFragment -notmatch 'CardRunStore|MainActivity' -and
        $resourceRawJsonFragment -match 'ResourceRawJsonScreen' -and
        $resourceRawJsonFragment -notmatch 'KiteResourceManifestLoader|MainActivity'
    ) 'Resource supplemental pages must own their views and consume existing gateways.'
    Assert-Architecture (
        $main -match 'ResourceMoreFragment\.newInstance' -and
        $main -match 'ResourceRawJsonFragment\.newInstance' -and
        $main -notmatch 'fun\s+(?:resourceMoreHeader|resourceInstallHistoryPanel|resourceRawJsonForUi)\s*\('
    ) 'MainActivity must route resource supplemental pages without drawing their content.'
    Assert-Architecture (
        $resourceActionWorkflow -match 'interface\s+ResourceActionGateway' -and
        $resourceActionWorkflow -notmatch 'android\.|androidx\.|CardRunStore|KiteResourceInstallStore'
    ) 'Resource action workflow must remain an Android-free application contract.'
    Assert-Architecture (
        $androidResourceActionGateway -match 'class\s+AndroidResourceActionGateway' -and
        $androidResourceActionGateway -match 'installWizardEffect' -and
        $androidResourceActionGateway -notmatch 'MainActivity|CardRunActivity|android\.view|android\.widget|com\.kite\.app\.feature'
    ) 'Resource action adapter must own Store orchestration without depending on UI or a Feature.'
    Assert-Architecture (
        $cardRunSpecialRecipes -match 'object\s+CardRunSpecialRecipes' -and
        $cardRunSpecialRecipes -notmatch 'android\.|androidx\.|com\.kite\.app\.feature|com\.kite\.app\.platform'
    ) 'Special-run recipes must remain reusable application data factories.'
    Assert-Architecture (
        $main -match 'resourceActionWorkflowCoordinator\.dispatch' -and
        $main -match 'ResourceActionEffect\.OpenInstallWizard' -and
        $main -notmatch 'resourceCatalogDirty|resourceCatalog\(|resourceRuntimeFactsFromStore|observeResourceInstallSignals|showResourceInstallWizard'
    ) 'MainActivity must dispatch resource intent and interpret route effects without owning resource facts.'
    Assert-Architecture (
        $recipeActionWorkflow -match 'interface\s+RecipeActionGateway' -and
        $recipeActionWorkflow -match 'planner\.plan' -and
        $recipeActionWorkflow -notmatch 'android\.|androidx\.|CardRunStore|MainActivity|CardRunActivity'
    ) 'Recipe action workflow must remain an Android-free application contract.'
    Assert-Architecture (
        $androidRecipeActionGateway -match 'orchestrator\.start' -and
        $androidRecipeActionGateway -match 'orchestrator\.stop' -and
        $androidRecipeActionGateway -notmatch 'MainActivity|CardRunActivity|android\.view|android\.widget|com\.kite\.app\.feature'
    ) 'Recipe action adapter must own Store and orchestrator access without UI dependencies.'
    Assert-Architecture (
        $main -match 'recipeActionWorkflowCoordinator\.dispatch' -and
        $main -match 'RecipeActionEffect\.OpenRun' -and
        $main -notmatch 'recipeActionCoordinator|executeRecipeActionRoute|KiteRecipeActionPlan|runOrchestrator\.(start|stop)'
    ) 'MainActivity must interpret recipe action effects instead of planning or executing recipe actions.'
    Assert-Architecture (
        $runtimeOwnerProbeWorkflow -match 'class\s+RuntimeOwnerProbeCoordinator' -and
        $runtimeOwnerProbeWorkflow -notmatch 'android\.|androidx\.|CardRunStore|MainActivity|CardRunActivity|com\.kite\.app\.platform'
    ) 'Runtime owner-probe workflow must remain an Android-free application contract.'
    Assert-Architecture (
        $androidRuntimeOwnerProbeGateway -match 'CardRunSpecialRecipes\.resourceOwnerProbe' -and
        $androidRuntimeOwnerProbeGateway -match 'orchestrator\.start' -and
        $androidRuntimeOwnerProbeGateway -notmatch 'MainActivity|CardRunActivity|android\.view|android\.widget|com\.kite\.app\.feature'
    ) 'Runtime owner-probe adapter must own run orchestration without UI dependencies.'
    Assert-Architecture (
        $main -match 'runtimeOwnerProbeCoordinator\.start' -and
        $main -notmatch 'resourceOwnerProbeRecipe|OWNER_KIND_RESOURCE|resourceRunCoordinator'
    ) 'MainActivity must only submit runtime owner-probe requests.'
    Assert-Architecture (
        $taskContractInitializer -match 'KiteAppGraph\.from\(context\.applicationContext\)\.resourceInstallStore' -and
        $taskContractInitializer -notmatch 'KiteResourceInstallStore\(context\.applicationContext\)'
    ) 'Toolchain callbacks must reuse the process resource store instead of creating SQLite-backed stores per event.'
    Assert-Architecture (
        $desktopOpenWorkflow -match 'class\s+DesktopOpenCoordinator' -and
        $desktopOpenWorkflow -notmatch 'android\.|androidx\.|CardRunStore|KiteX11Surface|MainActivity|CardRunActivity'
    ) 'Desktop-open workflow must remain an Android-free request/result contract.'
    Assert-Architecture (
        $androidDesktopOpenGateway -match 'KiteX11SurfaceServer\.ensureStarted' -and
        $androidDesktopOpenGateway -match 'CardRunStore\.update' -and
        $androidDesktopOpenGateway -notmatch 'MainActivity|CardRunActivity|android\.view|android\.widget|com\.kite\.app\.feature'
    ) 'Desktop-open adapter must own X11 and run facts without UI dependencies.'
    Assert-Architecture (
        $main -match 'desktopOpenCoordinator\.open' -and
        $main -notmatch 'acceptDesktopOpenRequest|temporaryDesktopRecipe|KiteX11SurfacePlan|KiteX11SurfaceServer'
    ) 'MainActivity must map desktop results to Shell effects without owning X11 preparation.'
    Assert-Architecture (
        $browserOpenWorkflow -match 'class\s+BrowserOpenCoordinator' -and
        $browserOpenWorkflow -notmatch 'android\.|androidx\.|CardRunStore|WebView|MainActivity|CardRunActivity'
    ) 'Browser-open workflow must remain an Android-free request/result contract.'
    Assert-Architecture (
        $androidBrowserOpenGateway -match 'CardRunBrowserRouter\.dispatch' -and
        $androidBrowserOpenGateway -match 'CardRunStore\.update' -and
        $androidBrowserOpenGateway -notmatch 'startActivity|(?m)^import\s+(android\.webkit\.|android\.view\.|android\.widget\.|android\.app\.Activity|com\.kite\.app\.(MainActivity|CardRunActivity|feature))'
    ) 'Browser-open adapter must own routing and run facts without display dependencies.'
    Assert-Architecture (
        $installApkWorkflow -match 'class\s+InstallApkCoordinator' -and
        $installApkWorkflow -notmatch 'android\.|androidx\.|FileProvider|Intent|MainActivity'
    ) 'Install-APK workflow must remain an Android-free path contract.'
    Assert-Architecture (
        $androidInstallApkGateway -match 'ExternalExchangeManager\.ensureExchangeDir' -and
        $androidInstallApkGateway -notmatch 'FileProvider|startActivity|(?m)^import\s+(android\.app\.Activity|com\.kite\.app\.feature)'
    ) 'Install-APK adapter must resolve files without launching Android UI.'
    Assert-Architecture (
        $main -match 'browserOpenCoordinator\.open' -and
        $main -match 'installApkCoordinator\.resolve' -and
        $main -notmatch 'openTemporaryBrowserRequest|updateBrowserRequestState|resolveInstallApkFile'
    ) 'MainActivity must only open the routed Web task or Android installer.'

    foreach ($file in $allSourceFiles) {
        $source = [System.IO.File]::ReadAllText($file.FullName, [System.Text.Encoding]::UTF8)
        $packageMatch = [regex]::Match($source, '(?m)^\s*package\s+(com\.kite\.app\.(?<layer>shell|feature|application|domain|platform)(?:\.[A-Za-z0-9_]+)*)')
        if (-not $packageMatch.Success) {
            continue
        }
        $packageName = $packageMatch.Groups[1].Value
        $layer = $packageMatch.Groups['layer'].Value
        $imports = Imported-Types $source
        $label = $file.FullName.Substring($Root.Length + 1)

        switch ($layer) {
            'domain' {
                $forbidden = @($imports | Where-Object {
                    $_ -match '^(android\.|androidx\.|com\.kite\.app\.(shell|feature|application|platform)(\.|$))'
                })
                Assert-Architecture ($forbidden.Count -eq 0) "Domain source '$label' has forbidden imports: $($forbidden -join ', ')."
            }
            'application' {
                $forbidden = @($imports | Where-Object {
                    $_ -match '^(android\.|androidx\.|com\.kite\.app\.(shell|feature|platform)(\.|$))'
                })
                Assert-Architecture ($forbidden.Count -eq 0) "Application source '$label' has forbidden imports: $($forbidden -join ', ')."
            }
            'platform' {
                $forbidden = @($imports | Where-Object {
                    $_ -match '^com\.kite\.app\.(shell|feature)(\.|$)' -or
                    $_ -match '^(android\.app\.Activity|android\.view\.|android\.widget\.|androidx\.activity\.|androidx\.appcompat\.app\.|androidx\.fragment\.)'
                })
                Assert-Architecture ($forbidden.Count -eq 0) "Platform source '$label' has forbidden UI imports: $($forbidden -join ', ')."
            }
            'feature' {
                $forbidden = @($imports | Where-Object {
                    $_ -match '^com\.kite\.app\.(shell|platform)(\.|$)' -or
                    $_ -in @('com.kite.app.MainActivity', 'com.kite.app.CardRunActivity')
                })
                Assert-Architecture ($forbidden.Count -eq 0) "Feature source '$label' depends on shell/platform implementation: $($forbidden -join ', ')."

                $ownerMatch = [regex]::Match($packageName, '^com\.kite\.app\.feature\.(?<owner>[A-Za-z0-9_]+)')
                if ($ownerMatch.Success) {
                    $owner = $ownerMatch.Groups['owner'].Value
                    $crossFeature = @($imports | Where-Object {
                        $importMatch = [regex]::Match($_, '^com\.kite\.app\.feature\.(?<target>[A-Za-z0-9_]+)')
                        $importMatch.Success -and $importMatch.Groups['target'].Value -ne $owner
                    })
                    Assert-Architecture ($crossFeature.Count -eq 0) "Feature source '$label' imports another feature directly: $($crossFeature -join ', ')."
                }
                Assert-Architecture ($source -notmatch '\bactivity\s+as\?') "Feature source '$label' casts back to an Activity."
                Assert-Architecture ($source -notmatch 'fun\s+render[A-Za-z0-9_]*Into\s*\([^)]*ViewGroup') "Feature source '$label' exposes an Activity-style render-into callback."
            }
        }
    }

    Write-Host (
        'Architecture debt: lines={0}, functions={1}, fields={2}, hosts={3}, resourceDelegates={4}, resourceFunctions={5}, screenRefs={6}, inheritedActivities={7}, runtimeStateRefs={8}' -f
        $metrics.physicalLines,
        $metrics.memberFunctions,
        $metrics.privateFields,
        $metrics.hostInterfaces,
        $metrics.resourceRenderDelegates,
        $metrics.resourceFunctions,
        $metrics.screenRouterMainActivityScreenReferences,
        $metrics.activitiesInheritingMainActivity,
        $metrics.runtimeStatesReferences
    )
}

if ($failures.Count -gt 0) {
    Write-Host 'Kite architecture checks failed:' -ForegroundColor Red
    foreach ($failure in $failures) {
        Write-Host " - $failure" -ForegroundColor Red
    }
    exit 1
}

Write-Host 'Kite architecture checks passed.' -ForegroundColor Green
