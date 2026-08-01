[CmdletBinding()]
param(
    [ValidateSet('Quick', 'Stage', 'Full')]
    [string]$Profile = 'Quick',
    [string[]]$Tests = @(),
    [switch]$Rerun,
    [ValidateRange(1, 7200)]
    [int]$LockTimeoutSeconds = 1800
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repositoryRoot = Split-Path $PSScriptRoot -Parent
$invokeGradle = Join-Path $PSScriptRoot 'invoke-kite-gradle.ps1'
$quickPatterns = @(
    '*ContractTest',
    '*ProtocolTest',
    '*RoutingTest',
    '*PolicyTest',
    '*SchemaTest',
    '*GuardTest'
)
$testSourceRoot = Join-Path $repositoryRoot 'app\src\test'
$allTestSources = @(Get-ChildItem -LiteralPath $testSourceRoot -Filter '*Test.kt' -Recurse)
$quickTestSources = @($allTestSources | Where-Object {
    $className = $_.BaseName
    $matchesQuickPattern = $false
    foreach ($pattern in $quickPatterns) {
        if ($className -like $pattern) {
            $matchesQuickPattern = $true
            break
        }
    }
    $matchesQuickPattern
})
if ($allTestSources.Count -eq 0 -or $quickTestSources.Count -eq 0) {
    throw '测试源码或 Quick 命名族为空，拒绝运行不明确的测试范围。'
}
if ($quickTestSources.Count * 4 -gt $allTestSources.Count) {
    throw "Quick 测试类超过全量 25%：$($quickTestSources.Count)/$($allTestSources.Count)"
}

if ($Profile -eq 'Stage' -and $Tests.Count -eq 0) {
    throw 'Stage 必须通过 -Tests 提供至少一个受影响模块或测试类模式。'
}
if ($Profile -ne 'Stage' -and $Tests.Count -gt 0) {
    throw '-Tests 只允许与 Stage 一起使用，避免调用方误以为 Quick/Full 覆盖了自定义范围。'
}
if ($Profile -eq 'Stage') {
    $qualifiedTestClasses = @($allTestSources | ForEach-Object {
        $relativePath = $_.FullName.Substring($testSourceRoot.Length + 1)
        $relativePath.Replace('\', '.').Replace('/', '.').Replace('.kt', '').Replace('kotlin.', '')
    })
    foreach ($testPattern in $Tests) {
        if (!($qualifiedTestClasses | Where-Object { $_ -like $testPattern })) {
            throw "Stage 测试模式没有匹配源码类：$testPattern"
        }
    }
}

$gradleArguments = [Collections.Generic.List[string]]::new()
$gradleArguments.Add(':app:testDebugUnitTest')
$patterns = @(switch ($Profile) {
    'Quick' { $quickPatterns }
    'Stage' { @($quickPatterns + $Tests) | Select-Object -Unique }
    'Full' { @() }
})
foreach ($pattern in $patterns) {
    $gradleArguments.Add('--tests')
    $gradleArguments.Add($pattern)
}
if ($Rerun) {
    $gradleArguments.Add('--rerun-tasks')
}

$wall = [Diagnostics.Stopwatch]::StartNew()
$gradleArgumentArray = $gradleArguments.ToArray()
& $invokeGradle `
    -GradleArguments $gradleArgumentArray `
    -LockTimeoutSeconds $LockTimeoutSeconds
$gradleExitCode = $LASTEXITCODE
$wall.Stop()
if ($gradleExitCode -ne 0) {
    throw "Kite $Profile 测试失败，Gradle exitCode=$gradleExitCode"
}

$resultRoot = Join-Path $repositoryRoot 'app\build\test-results\testDebugUnitTest'
$reports = @(Get-ChildItem -LiteralPath $resultRoot -Filter 'TEST-*.xml' -Recurse)
if ($reports.Count -eq 0) {
    throw "Kite $Profile 测试没有生成 JUnit XML：$resultRoot"
}
$suites = foreach ($report in $reports) {
    [xml]$document = Get-Content -LiteralPath $report.FullName -Raw
    $document.testsuite
}
$summary = [pscustomobject]@{
    profile = $Profile.ToLowerInvariant()
    suites = $reports.Count
    tests = [int](($suites | Measure-Object -Property tests -Sum).Sum)
    failures = [int](($suites | Measure-Object -Property failures -Sum).Sum)
    errors = [int](($suites | Measure-Object -Property errors -Sum).Sum)
    skipped = [int](($suites | Measure-Object -Property skipped -Sum).Sum)
    junitSeconds = [math]::Round([double](($suites | Measure-Object -Property time -Sum).Sum), 3)
    wallSeconds = [math]::Round($wall.Elapsed.TotalSeconds, 3)
    filters = if ($patterns.Count -eq 0) { 'all' } else { $patterns -join ',' }
    quickSourceClasses = $quickTestSources.Count
    totalSourceClasses = $allTestSources.Count
}
$summary | Format-List
if ($summary.failures -ne 0 -or $summary.errors -ne 0) {
    throw "Kite $Profile JUnit 摘要包含失败：failures=$($summary.failures), errors=$($summary.errors)"
}
