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
$cardRunPath = Join-Path $Root 'app/src/main/java/com/kite/app/CardRunActivity.kt'
$runSurfaceHostPath = Join-Path $Root 'app/src/main/java/com/kite/app/feature/runsurface/RunSurfaceHost.kt'
$runtimeManagementSnapshotPath = Join-Path $Root 'app/src/main/java/com/kite/app/application/runtimemanagement/RuntimeManagementSnapshot.kt'
$runtimeManagementContractPath = Join-Path $Root 'app/src/main/java/com/kite/app/feature/runtimemanagement/RuntimeManagementFeatureContract.kt'
$runtimeManagementProjectorPath = Join-Path $Root 'app/src/main/java/com/kite/app/feature/runtimemanagement/RuntimeManagementProjector.kt'
$installSurfaceBindingPath = Join-Path $Root 'app/src/main/java/com/kite/app/shell/RunInstallWizardSurfaceBinding.kt'
$legacyFeatureInstallBindingPath = Join-Path $Root 'app/src/main/java/com/kite/app/feature/runsurface/RunInstallWizardSurfaceBinding.kt'
$screenRouterPath = Join-Path $Root 'app/src/main/java/com/kite/app/shell/AppNavigator.kt'
$sourceRoots = @(
    (Join-Path $Root 'app/src/main/java/com/kite/app'),
    (Join-Path $Root 'app/src/main/kotlin/com/kite/app')
)

Assert-Architecture (Test-Path $baselinePath) 'Architecture baseline is missing.'
Assert-Architecture (Test-Path $mainPath) 'MainActivity source is missing.'
Assert-Architecture (Test-Path $cardRunPath) 'CardRunActivity source is missing.'
Assert-Architecture (Test-Path $runSurfaceHostPath) 'RunSurfaceHost source is missing.'
Assert-Architecture (Test-Path $runtimeManagementSnapshotPath) 'Runtime-management snapshot contract is missing.'
Assert-Architecture (Test-Path $runtimeManagementContractPath) 'Runtime-management feature contract is missing.'
Assert-Architecture (Test-Path $runtimeManagementProjectorPath) 'Runtime-management projector is missing.'
Assert-Architecture (Test-Path $installSurfaceBindingPath) 'Run install-wizard shell adapter is missing.'
Assert-Architecture (-not (Test-Path $legacyFeatureInstallBindingPath)) 'Run install-wizard adapter must not live inside the run-surface feature.'
Assert-Architecture (Test-Path $screenRouterPath) 'AppNavigator source is missing.'

if ($failures.Count -eq 0) {
    $baseline = Get-Content $baselinePath -Raw -Encoding UTF8 | ConvertFrom-Json
    $main = [System.IO.File]::ReadAllText($mainPath, [System.Text.Encoding]::UTF8)
    $cardRun = [System.IO.File]::ReadAllText($cardRunPath, [System.Text.Encoding]::UTF8)
    $runSurfaceHost = [System.IO.File]::ReadAllText($runSurfaceHostPath, [System.Text.Encoding]::UTF8)
    $runtimeManagementSnapshot = [System.IO.File]::ReadAllText($runtimeManagementSnapshotPath, [System.Text.Encoding]::UTF8)
    $runtimeManagementContract = [System.IO.File]::ReadAllText($runtimeManagementContractPath, [System.Text.Encoding]::UTF8)
    $runtimeManagementProjector = [System.IO.File]::ReadAllText($runtimeManagementProjectorPath, [System.Text.Encoding]::UTF8)
    $screenRouter = [System.IO.File]::ReadAllText($screenRouterPath, [System.Text.Encoding]::UTF8)
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
        $cardRun -match 'class\s+CardRunActivity\s*:\s*AppCompatActivity\(\),\s*TerminalChromeHost'
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
        $main -match 'private fun openCardRunTask\(' -and
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
        $runtimeManagementContract -match 'sealed interface RuntimeManagementActionTarget' -and
        $runtimeManagementContract -match 'AwaitingConfirmation' -and
        $runtimeManagementContract -notmatch '\(\)\s*->\s*Unit'
    ) 'Runtime-management UI actions must be data commands with explicit confirmation state, not View callbacks.'
    Assert-Architecture (
        $runtimeManagementProjector -match 'assignProcesses\(' -and
        $runtimeManagementProjector -match 'RuntimeManagementMutation' -and
        $runtimeManagementProjector -notmatch 'TaskManagerStore|TerminalSessionStore|CardRunStore|android\.|androidx\.|Context|View'
    ) 'Runtime-management projection must stay pure and must not read stores or Android UI directly.'

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
                Assert-Architecture ($source -notmatch '(?s)activity\s+as\?\s+[A-Za-z0-9_.]*Host') "Feature source '$label' delegates ownership back to an Activity Host."
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
