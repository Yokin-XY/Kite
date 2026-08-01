[CmdletBinding()]
param(
    [ValidateSet("smoke", "agent", "runtime", "resource", "ui", "full")]
    [string]$Lane = "smoke",

    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$GradleArguments = @()
)

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$gradle = Join-Path $projectRoot "gradlew.bat"

if (-not (Test-Path -LiteralPath $gradle -PathType Leaf)) {
    throw "未找到 Gradle Wrapper：$gradle"
}

$lanePatterns = @{
    smoke = @(
        "com.kite.app.agent.auth.*",
        "com.kite.app.agent.contract.KiteAgentContractTest",
        "com.kite.app.agent.process.AgentProcessChannelTest",
        "com.kite.app.agent.registration.AgentRegistryAssemblerTest",
        "com.kite.app.application.runs.RunOrchestratorTest",
        "com.kite.app.feature.runsurface.AgentSurfaceNavigationPolicyTest",
        "com.kite.app.platform.runs.AndroidAgentOfficialAccountCommandRunnerTest",
        "com.kite.app.resources.KiteResourceManifestProtocolTest",
        "com.kite.app.run.CardRunAgentBindingTest",
        "com.kite.app.run.CardRunRuntimeLaneContractTest"
    )
    agent = @(
        "com.kite.app.agent.*",
        "com.kite.app.feature.runsurface.Agent*",
        "com.kite.app.platform.runs.AndroidAgent*",
        "com.kite.app.resources.KiteResourceManifestProtocolTest",
        "com.kite.app.run.CardRunAgentBindingTest"
    )
    runtime = @(
        "com.kite.app.application.runs.*",
        "com.kite.app.foundation.runtime.*",
        "com.kite.app.platform.runs.*",
        "com.kite.app.run.*"
    )
    resource = @(
        "com.kite.app.application.resources.*",
        "com.kite.app.feature.resources.*",
        "com.kite.app.platform.resources.*",
        "com.kite.app.resources.*"
    )
    ui = @(
        "com.kite.app.MainActivityScreenRoutingTest",
        "com.kite.app.feature.*",
        "com.kite.app.shell.*",
        "com.kite.app.ui.*"
    )
    full = @()
}

$arguments = @(
    ":app:testDebugUnitTest",
    "--console=plain"
)

foreach ($pattern in $lanePatterns[$Lane]) {
    $arguments += "--tests"
    $arguments += $pattern
}

$arguments += $GradleArguments

Write-Host "Kite 测试车道：$Lane"
if ($lanePatterns[$Lane].Count -eq 0) {
    Write-Host "范围：完整 Debug 单元测试"
} else {
    Write-Host "范围：$($lanePatterns[$Lane].Count) 个职责模式"
}

Push-Location $projectRoot
try {
    & $gradle @arguments
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
} finally {
    Pop-Location
}
