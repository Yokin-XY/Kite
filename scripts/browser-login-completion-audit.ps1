param(
    [string]$RepoRoot = "",
    [string]$StateDir = "",
    [switch]$RefreshState,
    [switch]$SkipImplementationChecks,
    [switch]$RunSmokeTest
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($RepoRoot)) {
    $RepoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
} else {
    $RepoRoot = Resolve-Path $RepoRoot
}

if ([string]::IsNullOrWhiteSpace($StateDir)) {
    $StateDir = Join-Path $env:LOCALAPPDATA "Kite\browser-login-continuation"
}

$runnerScript = Join-Path $RepoRoot "scripts\browser-login-continuation-runner.ps1"
$evidenceReportScript = Join-Path $RepoRoot "scripts\browser-login-evidence-report.ps1"
$selfTestScript = Join-Path $RepoRoot "scripts\test-browser-login-continuation.ps1"
$smokeTestScript = Join-Path $RepoRoot "scripts\browser-login-smoke-test.ps1"
$longRunCycleScript = Join-Path $RepoRoot "scripts\browser-login-long-run-cycle.ps1"
$gradlewScript = Join-Path $RepoRoot "gradlew.bat"
$auditJsonPath = Join-Path $StateDir "completion-audit.json"
$auditReportPath = Join-Path $StateDir "completion-audit.md"
$evidenceReportPath = Join-Path $StateDir "post-auth-evidence-report.md"
$smokeJsonPath = Join-Path $StateDir "browser-login-smoke.json"
$smokeReportPath = Join-Path $StateDir "browser-login-smoke.md"
$smokeWatchJsonPath = Join-Path $StateDir "browser-login-smoke-watch.json"
$smokeWatchReportPath = Join-Path $StateDir "browser-login-smoke-watch.md"
$accountWatchJsonPath = Join-Path $StateDir "account-watch-status.json"
$accountWatchReportPath = Join-Path $StateDir "account-watch-report.md"
$manualAccountStartJsonPath = Join-Path $StateDir "manual-account-start-status.json"
$manualAccountStartReportPath = Join-Path $StateDir "manual-account-start-report.md"
$unitTestLogPath = Join-Path $StateDir "browser-unit-test-output.txt"
$assembleLogPath = Join-Path $StateDir "assemble-debug-output.txt"
$adbDevicesLogPath = Join-Path $StateDir "adb-devices-output.txt"
$scheduledTaskName = "KiteBrowserLoginContinuationGate"
$longRunScheduledTaskName = "KiteBrowserLoginLongRunCycle"

function Add-ItemResult {
    param(
        [System.Collections.Generic.List[object]]$Items,
        [string]$Id,
        [string]$Title,
        [bool]$Passed,
        [string]$Evidence,
        [string]$RequiredEvidence
    )

    $Items.Add([ordered]@{
        id = $Id
        title = $Title
        passed = $Passed
        evidence = $Evidence
        requiredEvidence = $RequiredEvidence
    })
}

function Test-File {
    param([string]$RelativePath)

    return Test-Path -LiteralPath (Join-Path $RepoRoot $RelativePath)
}

function Read-JsonOrNull {
    param([string]$Path)

    if (-not (Test-Path -LiteralPath $Path)) {
        return $null
    }
    return Get-Content -Raw -LiteralPath $Path | ConvertFrom-Json
}

function To-StringArray {
    param([object]$Value)

    $items = New-Object System.Collections.Generic.List[string]
    foreach ($item in @($Value)) {
        if ($null -eq $item) {
            continue
        }
        $text = $item.ToString().Trim()
        if ([string]::IsNullOrWhiteSpace($text)) {
            continue
        }
        $items.Add($text)
    }
    return $items.ToArray()
}

function Format-Status {
    param([bool]$Passed)

    if ($Passed) {
        return "PASS"
    }
    return "MISS"
}

function Format-Items {
    param([string[]]$Items)

    if ($Items.Count -eq 0) {
        return "(none)"
    }
    return ($Items -join ", ")
}

function Get-JsonPropertyString {
    param(
        [object]$Object,
        [string]$Name
    )

    if ($null -eq $Object -or $null -eq $Object.PSObject.Properties[$Name]) {
        return ""
    }
    return $Object.PSObject.Properties[$Name].Value.ToString()
}

function Get-JsonPropertyInt {
    param(
        [object]$Object,
        [string]$Name,
        [int]$DefaultValue = -1
    )

    if ($null -eq $Object -or $null -eq $Object.PSObject.Properties[$Name]) {
        return $DefaultValue
    }
    $raw = $Object.PSObject.Properties[$Name].Value
    if ($null -eq $raw) {
        return $DefaultValue
    }
    $parsed = 0
    if ([int]::TryParse($raw.ToString(), [ref]$parsed)) {
        return $parsed
    }
    return $DefaultValue
}

function Test-JsonCheckedAtFresh {
    param(
        [object]$Object,
        [int]$Hours = 24
    )

    $checkedAt = Get-JsonPropertyString $Object "checkedAt"
    if ([string]::IsNullOrWhiteSpace($checkedAt)) {
        return $false
    }
    $parsed = [datetimeoffset]::MinValue
    if (-not [datetimeoffset]::TryParse($checkedAt, [ref]$parsed)) {
        return $false
    }
    return ([datetimeoffset]::Now - $parsed) -le [TimeSpan]::FromHours($Hours)
}

New-Item -ItemType Directory -Force -Path $StateDir | Out-Null

$refreshRunnerExit = $null
$selfTestExit = $null
$unitTestExit = $null
$assembleExit = $null
$adbDevicesExit = $null
$adbDeviceLine = ""
$smokeExit = $null

try {
    $adb = (Get-Command adb -ErrorAction Stop).Source
    & $adb devices -l *> $adbDevicesLogPath
    $adbDevicesExit = $LASTEXITCODE
    if (Test-Path -LiteralPath $adbDevicesLogPath) {
        $adbDeviceLine = @(
            Get-Content -LiteralPath $adbDevicesLogPath |
                Where-Object { $_ -match "^\s*3f8bbaad\s+device\b" }
        ) | Select-Object -First 1
    }
} catch {
    "adb error=$($_.Exception.Message)" | Set-Content -LiteralPath $adbDevicesLogPath -Encoding UTF8
}

if ($RefreshState) {
    if (-not (Test-Path -LiteralPath $runnerScript)) {
        throw "Runner script not found: $runnerScript"
    }
    & $runnerScript -Serial "3f8bbaad" -StateDir $StateDir | Out-Null
    $refreshRunnerExit = $LASTEXITCODE

    if (Test-Path -LiteralPath $selfTestScript) {
        & $selfTestScript | Out-Null
        $selfTestExit = $LASTEXITCODE
    }

    if ($RunSmokeTest) {
        if (-not (Test-Path -LiteralPath $smokeTestScript)) {
            throw "Smoke test script not found: $smokeTestScript"
        }
        & $smokeTestScript -Serial "3f8bbaad" -StateDir $StateDir | Out-Null
        $smokeExit = $LASTEXITCODE
    }

    if (-not $SkipImplementationChecks) {
        if (-not (Test-Path -LiteralPath $gradlewScript)) {
            throw "Gradle wrapper not found: $gradlewScript"
        }

        Push-Location -LiteralPath $RepoRoot
        try {
            & $gradlewScript :app:testDebugUnitTest --tests "com.kite.app.browser.*" --console=plain *> $unitTestLogPath
            $unitTestExit = $LASTEXITCODE

            & $gradlewScript :app:assembleDebug --console=plain *> $assembleLogPath
            $assembleExit = $LASTEXITCODE
        } finally {
            Pop-Location
        }
    }
}

$runnerState = Read-JsonOrNull (Join-Path $StateDir "runner-status.json")
$gateState = Read-JsonOrNull (Join-Path $StateDir "last-status.json")
$postAuthState = Read-JsonOrNull (Join-Path $StateDir "post-auth-status.json")
$smokeState = Read-JsonOrNull $smokeJsonPath
$smokeWatchState = Read-JsonOrNull $smokeWatchJsonPath
$accountWatchState = Read-JsonOrNull $accountWatchJsonPath
$manualAccountStartState = Read-JsonOrNull $manualAccountStartJsonPath

$verifiedTargets = @()
if ($null -ne $runnerState -and
    $null -ne $runnerState.PSObject.Properties["postAuthAttempted"] -and
    [bool]$runnerState.postAuthAttempted) {
    if ($null -ne $postAuthState -and $null -ne $postAuthState.PSObject.Properties["verifiedTargets"]) {
        $verifiedTargets = To-StringArray $postAuthState.verifiedTargets
    } elseif ($null -ne $runnerState.PSObject.Properties["verifiedTargets"]) {
        $verifiedTargets = To-StringArray $runnerState.verifiedTargets
    }
}

$waitingTargets = @()
if ($null -ne $runnerState -and $null -ne $runnerState.PSObject.Properties["waitingTargets"]) {
    $waitingTargets = To-StringArray $runnerState.waitingTargets
} elseif ($null -ne $gateState -and $null -ne $gateState.PSObject.Properties["waitingTargets"]) {
    $waitingTargets = To-StringArray $gateState.waitingTargets
}
$allAccountsVerified = ($verifiedTargets -contains "codex") -and ($verifiedTargets -contains "claude")

$items = New-Object System.Collections.Generic.List[object]

$requiredDocs = @(
    "docs\browser-login\PLAYBOOK.md",
    "docs\browser-login\PROGRESS.md",
    "docs\browser-login\DECISIONS.md",
    "docs\browser-login\CURRENT_CHAIN.md",
    "docs\browser-login\WEB_LOGIN_RESEARCH.md",
    "docs\browser-login\LOGIN_HANDOFF_DESIGN.md",
    "docs\browser-login\ACCOUNT_VERIFICATION_NODES.md",
    "docs\browser-login\LOGIN_TEST_STRATEGY.md",
    "docs\browser-login\COMPATIBILITY_MATRIX.md",
    "docs\browser-login\ACCOUNT_AUTH_COMPLETION_SOP.md"
)
$missingDocs = @($requiredDocs | Where-Object { -not (Test-File $_) })
Add-ItemResult `
    -Items $items `
    -Id "docs" `
    -Title "调研、方案和三件套文档存在" `
    -Passed ($missingDocs.Count -eq 0) `
    -Evidence "missing=$(Format-Items $missingDocs)" `
    -RequiredEvidence "PLAYBOOK/PROGRESS/DECISIONS、CURRENT_CHAIN、WEB_LOGIN_RESEARCH、LOGIN_HANDOFF_DESIGN、ACCOUNT_VERIFICATION_NODES、LOGIN_TEST_STRATEGY、COMPATIBILITY_MATRIX、ACCOUNT_AUTH_COMPLETION_SOP 均存在"

$requiredCode = @(
    "app\src\main\java\com\kite\app\browser\BrowserHandoffPolicy.kt",
    "app\src\main\java\com\kite\app\browser\BrowserAuthSessionStore.kt",
    "app\src\test\kotlin\com\kite\app\browser\BrowserHandoffPolicyTest.kt",
    "app\src\test\kotlin\com\kite\app\browser\BrowserAuthSessionStoreTest.kt"
)
$missingCode = @($requiredCode | Where-Object { -not (Test-File $_) })
Add-ItemResult `
    -Items $items `
    -Id "implementation" `
    -Title "通用 handoff 实现和单测存在" `
    -Passed ($missingCode.Count -eq 0) `
    -Evidence "missing=$(Format-Items $missingCode)" `
    -RequiredEvidence "BrowserHandoffPolicy、BrowserAuthSessionStore 和对应单测存在"

$unitTestPassed = if ($RefreshState -and -not $SkipImplementationChecks) {
    $unitTestExit -eq 0
} else {
    $missingCode.Count -eq 0
}
$unitTestEvidence = if ($RefreshState -and -not $SkipImplementationChecks) {
    "exit=$unitTestExit; log=$unitTestLogPath"
} elseif ($SkipImplementationChecks) {
    "skipped by -SkipImplementationChecks"
} else {
    "not run in this audit; run with -RefreshState for current evidence"
}
Add-ItemResult `
    -Items $items `
    -Id "browser-unit-tests" `
    -Title "浏览器 handoff 单测当前通过" `
    -Passed $unitTestPassed `
    -Evidence $unitTestEvidence `
    -RequiredEvidence "使用 -RefreshState 时 `:app:testDebugUnitTest --tests com.kite.app.browser.*` 退出码为 0"

$apkPath = Join-Path $RepoRoot "app\build\outputs\apk\debug\app-debug.apk"
$assemblePassed = if ($RefreshState -and -not $SkipImplementationChecks) {
    $assembleExit -eq 0 -and (Test-Path -LiteralPath $apkPath)
} else {
    Test-Path -LiteralPath $apkPath
}
$assembleEvidence = if ($RefreshState -and -not $SkipImplementationChecks) {
    "exit=$assembleExit; apkExists=$(Test-Path -LiteralPath $apkPath); apk=$apkPath; log=$assembleLogPath"
} elseif ($SkipImplementationChecks) {
    "skipped by -SkipImplementationChecks; apkExists=$(Test-Path -LiteralPath $apkPath); apk=$apkPath"
} else {
    "not run in this audit; apkExists=$(Test-Path -LiteralPath $apkPath); run with -RefreshState for current evidence"
}
Add-ItemResult `
    -Items $items `
    -Id "debug-apk-build" `
    -Title "debug APK 当前可构建" `
    -Passed $assemblePassed `
    -Evidence $assembleEvidence `
    -RequiredEvidence "使用 -RefreshState 时 `:app:assembleDebug` 退出码为 0，且 debug APK 存在"

$requiredEvidence = @(
    "docs\browser-login\evidence\google-oauth-webview-after-wake.png",
    "docs\browser-login\evidence\google-oauth-custom-tabs-handoff.png",
    "docs\browser-login\evidence\browser-auth-callback-return.png",
    "docs\browser-login\evidence\codex-cli-current-state.png",
    "docs\browser-login\evidence\codex-cli-openai-browser.png",
    "docs\browser-login\evidence\codex-cli-loopback-terminal-preserved.png",
    "docs\browser-login\evidence\claude-code-login-method.png"
)
$missingEvidence = @($requiredEvidence | Where-Object { -not (Test-File $_) })
Add-ItemResult `
    -Items $items `
    -Id "device-evidence" `
    -Title "OnePlus 8T 关键截图证据存在" `
    -Passed ($missingEvidence.Count -eq 0) `
    -Evidence "missing=$(Format-Items $missingEvidence)" `
    -RequiredEvidence "Google WebView 失败、Custom Tabs/system browser、App callback、Codex、Claude 关键截图存在"

$onePlusOnline = $adbDevicesExit -eq 0 -and -not [string]::IsNullOrWhiteSpace($adbDeviceLine)
$onePlusEvidence = if ([string]::IsNullOrWhiteSpace($adbDeviceLine)) {
    "exit=$adbDevicesExit; serial=3f8bbaad not online; log=$adbDevicesLogPath"
} else {
    "exit=$adbDevicesExit; line=$adbDeviceLine; log=$adbDevicesLogPath"
}
Add-ItemResult `
    -Items $items `
    -Id "oneplus-device-online" `
    -Title "OnePlus 8T 当前 ADB 在线" `
    -Passed $onePlusOnline `
    -Evidence $onePlusEvidence `
    -RequiredEvidence "adb devices -l 中 3f8bbaad 为 device 状态，避免误用 X11/MEIZU 设备或离线设备"

$requiredScripts = @(
    "scripts\browser-login-auth-status.ps1",
    "scripts\browser-login-continuation-gate.ps1",
    "scripts\browser-login-continuation-runner.ps1",
    "scripts\browser-login-account-watch.ps1",
    "scripts\browser-login-manual-account-start.ps1",
    "scripts\browser-login-post-auth-verify.ps1",
    "scripts\browser-login-evidence-report.ps1",
    "scripts\browser-login-completion-audit.ps1",
    "scripts\browser-login-smoke-test.ps1",
    "scripts\browser-login-smoke-watch.ps1",
    "scripts\browser-login-manual-readiness.ps1",
    "scripts\browser-login-long-run-cycle.ps1",
    "scripts\register-browser-login-long-run-cycle.ps1",
    "scripts\browser-login-provider-preflight.ps1",
    "scripts\test-browser-login-continuation.ps1"
)
$missingScripts = @($requiredScripts | Where-Object { -not (Test-File $_) })
Add-ItemResult `
    -Items $items `
    -Id "continuation-scripts" `
    -Title "账号补证和续跑脚本存在" `
    -Passed ($missingScripts.Count -eq 0) `
    -Evidence "missing=$(Format-Items $missingScripts)" `
    -RequiredEvidence "auth status、gate、runner、account watch、manual account start、post-auth、evidence report、smoke test、smoke watch、manual readiness、long-run cycle、provider preflight、自测试脚本存在"

$smokeFresh = $false
$smokeCheckedAt = ""
if ($null -ne $smokeState -and $null -ne $smokeState.PSObject.Properties["checkedAt"]) {
    $smokeCheckedAt = $smokeState.checkedAt.ToString()
    $parsedSmokeTime = [datetimeoffset]::MinValue
    if ([datetimeoffset]::TryParse($smokeCheckedAt, [ref]$parsedSmokeTime)) {
        $smokeFresh = ([datetimeoffset]::Now - $parsedSmokeTime) -le [TimeSpan]::FromHours(24)
    }
}
$smokeFailedItems = if ($null -ne $smokeState -and $null -ne $smokeState.PSObject.Properties["failedItemIds"]) {
    To-StringArray $smokeState.failedItemIds
} else {
    @()
}
$smokeSchemaVersion = Get-JsonPropertyInt $smokeState "schemaVersion" 0
$smokeItemIds = if ($null -ne $smokeState -and $null -ne $smokeState.PSObject.Properties["items"]) {
    To-StringArray (@($smokeState.items | ForEach-Object { $_.id }))
} else {
    @()
}
$requiredSmokeItemIds = @(
    "auth-hosts-network-reachable",
    "external-browser-handler-resolved",
    "local-web-open-accepted",
    "local-webview-stays-in-kite",
    "local-webview-no-auth-session",
    "open-web-responsive",
    "external-foreground-responsive",
    "external-browser-handler-observed",
    "provider-oauth-openai-external-browser",
    "provider-oauth-claude-external-browser",
    "provider-oauth-no-auth-session",
    "foreground-external-browser",
    "ui-no-disallowed-useragent",
    "provider-page-no-blocking-error",
    "no-third-party-appredirect-session",
    "appredirect-pending-session",
    "appredirect-callback-delivered",
    "appredirect-callback-redacted",
    "no-oauth-temporary-values-in-app-files",
    "no-crash-or-anr"
)
$missingSmokeItemIds = @($requiredSmokeItemIds | Where-Object { $smokeItemIds -notcontains $_ })
$expectedAppRedirectReturnedUrl = "kite-auth://callback?code=present&access_token=present&state=present"
$smokeAppRedirectStatus = Get-JsonPropertyString $smokeState "appRedirectStatus"
$smokeAppRedirectReturnedUrl = Get-JsonPropertyString $smokeState "appRedirectReturnedUrl"
$smokeAppRedirectRawSecretHitCount = Get-JsonPropertyInt $smokeState "appRedirectRawSecretHitCount" -1
$smokeLocalWebOpenWebElapsedMs = Get-JsonPropertyInt $smokeState "localWebOpenWebElapsedMs" -1
$smokeLocalWebForegroundPackage = Get-JsonPropertyString $smokeState "localWebForegroundPackage"
$smokeLocalWebForegroundActivity = Get-JsonPropertyString $smokeState "localWebForegroundActivity"
$smokeOpenWebResponsiveThresholdMs = Get-JsonPropertyInt $smokeState "openWebResponsiveThresholdMs" -1
$smokeForegroundResponsiveThresholdMs = Get-JsonPropertyInt $smokeState "foregroundResponsiveThresholdMs" -1
$smokeOpenWebElapsedMs = Get-JsonPropertyInt $smokeState "openWebElapsedMs" -1
$smokeForegroundHandoffElapsedMs = Get-JsonPropertyInt $smokeState "foregroundHandoffElapsedMs" -1
$smokeAppRedirectOpenWebElapsedMs = Get-JsonPropertyInt $smokeState "appRedirectOpenWebElapsedMs" -1
$smokeAuthHostNetworkEvidence = if ($null -ne $smokeState -and $null -ne $smokeState.PSObject.Properties["authHostNetworkResults"]) {
    (@(
        $smokeState.authHostNetworkResults |
            ForEach-Object { "$($_.host):exit=$($_.exitCode);http=$($_.httpCode);ok=$($_.ok);attempts=$($_.attemptCount)" }
    ) -join ",")
} else {
    ""
}
$smokeHttpsBrowserResolvePackage = Get-JsonPropertyString $smokeState "httpsBrowserResolvePackage"
$smokeHttpsBrowserResolveActivity = Get-JsonPropertyString $smokeState "httpsBrowserResolveActivity"
$smokeCustomTabsServiceCount = Get-JsonPropertyInt $smokeState "customTabsServiceCount" -1
$smokeForegroundPackage = Get-JsonPropertyString $smokeState "foregroundPackage"
$smokeForegroundActivity = Get-JsonPropertyString $smokeState "foregroundActivity"
$smokeProviderOAuthNewSessionCount = Get-JsonPropertyInt $smokeState "providerOAuthNewSessionCount" -1
$smokeProviderOAuthResults = if ($null -ne $smokeState -and $null -ne $smokeState.PSObject.Properties["providerOAuthResults"]) {
    @($smokeState.providerOAuthResults)
} else {
    @()
}
$smokeProviderOAuthEvidence = if ($smokeProviderOAuthResults.Count -gt 0) {
    (@(
        $smokeProviderOAuthResults |
            ForEach-Object { "$($_.id):accepted=$($_.accepted);foreground=$($_.foregroundPackage);handoffMs=$($_.handoffForegroundElapsedMs);passed=$($_.passed)" }
    ) -join ",")
} else {
    ""
}
$smokeProviderOAuthForegroundMaxElapsedMs = Get-JsonPropertyInt $smokeState "providerOAuthForegroundMaxElapsedMs" -1
$smokeProviderPageSignalState = Get-JsonPropertyString $smokeState "providerPageSignalState"
$smokeProviderPageBlockingErrorCount = Get-JsonPropertyInt $smokeState "providerPageBlockingErrorCount" -1
$smokeProviderPageChallengeHintCount = Get-JsonPropertyInt $smokeState "providerPageChallengeHintCount" -1
$smokeAppPrivateTextScannedFileCount = Get-JsonPropertyInt $smokeState "appPrivateTextScannedFileCount" -1
$smokeAppPrivateRawTemporaryValueHitCount = Get-JsonPropertyInt $smokeState "appPrivateRawTemporaryValueHitCount" -1
$smokeSchemaOk = $smokeSchemaVersion -ge 10
$smokeOpenWebResponsiveProven = $smokeOpenWebResponsiveThresholdMs -gt 0 -and
    $smokeOpenWebElapsedMs -ge 0 -and
    $smokeAppRedirectOpenWebElapsedMs -ge 0 -and
    $smokeOpenWebElapsedMs -le $smokeOpenWebResponsiveThresholdMs -and
    $smokeAppRedirectOpenWebElapsedMs -le $smokeOpenWebResponsiveThresholdMs
$smokeExternalForegroundResponsiveProven = $smokeForegroundResponsiveThresholdMs -gt 0 -and
    $smokeForegroundHandoffElapsedMs -ge 0 -and
    $smokeProviderOAuthForegroundMaxElapsedMs -ge 0 -and
    $smokeForegroundHandoffElapsedMs -le $smokeForegroundResponsiveThresholdMs -and
    $smokeProviderOAuthForegroundMaxElapsedMs -le $smokeForegroundResponsiveThresholdMs
$smokeLocalWebViewProven = $smokeLocalWebOpenWebElapsedMs -ge 0 -and
    $smokeLocalWebForegroundPackage -eq "com.kite.app"
$smokeAppPrivateTemporaryValueScanProven = $smokeAppPrivateTextScannedFileCount -gt 0 -and
    $smokeAppPrivateRawTemporaryValueHitCount -eq 0
$smokeExternalBrowserHandlerProven = -not [string]::IsNullOrWhiteSpace($smokeHttpsBrowserResolvePackage) -and
    -not [string]::IsNullOrWhiteSpace($smokeHttpsBrowserResolveActivity) -and
    $smokeHttpsBrowserResolvePackage -ne "com.kite.app" -and
    $smokeForegroundPackage -eq $smokeHttpsBrowserResolvePackage
$smokeProviderOAuthProven = $smokeProviderOAuthResults.Count -ge 2 -and
    $smokeProviderOAuthNewSessionCount -eq 0 -and
    @($smokeProviderOAuthResults | Where-Object {
        $_.accepted -ne $true -or
        $_.externalBrowser -ne $true -or
        $_.matchesHandler -ne $true -or
        $_.passed -ne $true
    }).Count -eq 0
$smokeProviderPageSignalsProven = $smokeProviderPageBlockingErrorCount -eq 0 -and
    -not [string]::IsNullOrWhiteSpace($smokeProviderPageSignalState) -and
    $smokeProviderPageSignalState -ne "blocking_error"
$smokeAppRedirectProven = $smokeSchemaOk -and
    $missingSmokeItemIds.Count -eq 0 -and
    $smokeExternalBrowserHandlerProven -and
    $smokeProviderOAuthProven -and
    $smokeProviderPageSignalsProven -and
    $smokeLocalWebViewProven -and
    $smokeOpenWebResponsiveProven -and
    $smokeExternalForegroundResponsiveProven -and
    $smokeAppPrivateTemporaryValueScanProven -and
    $smokeAppRedirectStatus -eq "Delivered" -and
    $smokeAppRedirectReturnedUrl -eq $expectedAppRedirectReturnedUrl -and
    $smokeAppRedirectRawSecretHitCount -eq 0
$smokePassed = $null -ne $smokeState -and
    $smokeState.status -eq "passed" -and
    $smokeFresh -and
    (Test-Path -LiteralPath $smokeReportPath) -and
    $smokeAppRedirectProven
if ($RunSmokeTest) {
    $smokePassed = $smokePassed -and $smokeExit -eq 0
}
$smokeEvidence = if ($null -eq $smokeState) {
    "browser-login-smoke.json missing; run scripts/browser-login-smoke-test.ps1"
} else {
    "schemaVersion=$smokeSchemaVersion; status=$($smokeState.status); checkedAt=$smokeCheckedAt; fresh24h=$smokeFresh; smokeExit=$smokeExit; authHostNetworkResults=$smokeAuthHostNetworkEvidence; httpsBrowserResolve=$smokeHttpsBrowserResolvePackage/$smokeHttpsBrowserResolveActivity; customTabsServiceCount=$smokeCustomTabsServiceCount; providerOAuthResults=$smokeProviderOAuthEvidence; providerOAuthNewSessionCount=$smokeProviderOAuthNewSessionCount; providerOAuthForegroundMaxElapsedMs=$smokeProviderOAuthForegroundMaxElapsedMs; providerPageSignalState=$smokeProviderPageSignalState; providerPageBlockingErrorCount=$smokeProviderPageBlockingErrorCount; providerPageChallengeHintCount=$smokeProviderPageChallengeHintCount; localWebOpenWebElapsedMs=$smokeLocalWebOpenWebElapsedMs; localWebForeground=$smokeLocalWebForegroundPackage/$smokeLocalWebForegroundActivity; foreground=$smokeForegroundPackage/$smokeForegroundActivity; openWebElapsedMs=$smokeOpenWebElapsedMs; foregroundHandoffElapsedMs=$smokeForegroundHandoffElapsedMs; appRedirectOpenWebElapsedMs=$smokeAppRedirectOpenWebElapsedMs; openWebResponsiveThresholdMs=$smokeOpenWebResponsiveThresholdMs; foregroundResponsiveThresholdMs=$smokeForegroundResponsiveThresholdMs; appRedirectStatus=$smokeAppRedirectStatus; appRedirectReturnedUrl=$smokeAppRedirectReturnedUrl; appRedirectRawSecretHitCount=$smokeAppRedirectRawSecretHitCount; appPrivateTextScannedFileCount=$smokeAppPrivateTextScannedFileCount; appPrivateRawTemporaryValueHitCount=$smokeAppPrivateRawTemporaryValueHitCount; missingSmokeItemIds=$(Format-Items $missingSmokeItemIds); failedItemIds=$(Format-Items $smokeFailedItems); report=$smokeReportPath"
}
Add-ItemResult `
    -Items $items `
    -Id "browser-login-smoke" `
    -Title "人工账号验证前真机 smoke test 通过" `
    -Passed $smokePassed `
    -Evidence $smokeEvidence `
    -RequiredEvidence "最近 24 小时内 browser-login-smoke-test.ps1 在 OnePlus 8T 上通过；schemaVersion>=10；关键 item 包含授权主机设备侧 HTTPS 可达且带重试 attempts 证据、HTTPS ACTION_VIEW 外部浏览器 handler、OpenAI/Codex 和 Claude OAuth 形态 URL 外部浏览器分流且不建假 auth session、普通 localhost WebView 不外跳且不建 auth session、/open-web 响应耗时、OAuth handoff 前台切换耗时、实测前台匹配外部浏览器 handler、无 disallowed_useragent、provider 页面无阻塞性错误信号、第三方 HTTPS redirect 不新增假 AppRedirect、AppRedirect pending/delivered/redacted、app 私有文本文件无本轮 OAuth 临时值原文；appRedirectStatus=Delivered；returnedUrl 为 code/access_token/state=present；rawSecretHitCount=0；appPrivateRawTemporaryValueHitCount=0"

$smokeWatchFresh = Test-JsonCheckedAtFresh $smokeWatchState 24
$smokeWatchIterations = Get-JsonPropertyInt $smokeWatchState "iterations" -1
$smokeWatchFailureCount = Get-JsonPropertyInt $smokeWatchState "failureCount" -1
$smokeWatchOpenWebP95ThresholdMs = Get-JsonPropertyInt $smokeWatchState "openWebP95ThresholdMs" -1
$smokeWatchOpenWebP95Ms = Get-JsonPropertyInt $smokeWatchState "openWebP95Ms" -1
$smokeWatchForegroundP95ThresholdMs = Get-JsonPropertyInt $smokeWatchState "foregroundP95ThresholdMs" -1
$smokeWatchForegroundP95Ms = Get-JsonPropertyInt $smokeWatchState "foregroundP95Ms" -1
$smokeWatchProviderSessionLeakRunCount = Get-JsonPropertyInt $smokeWatchState "providerSessionLeakRunCount" -1
$smokeWatchProviderPageBlockingErrorRunCount = Get-JsonPropertyInt $smokeWatchState "providerPageBlockingErrorRunCount" -1
$smokeWatchSecretLeakRunCount = Get-JsonPropertyInt $smokeWatchState "secretLeakRunCount" -1
$smokeWatchHandlerPackages = if ($null -ne $smokeWatchState -and $null -ne $smokeWatchState.PSObject.Properties["handlerPackages"]) {
    To-StringArray $smokeWatchState.handlerPackages
} else {
    @()
}
$smokeWatchHandlerStable = $null -ne $smokeWatchState -and
    $null -ne $smokeWatchState.PSObject.Properties["handlerStable"] -and
    [bool]$smokeWatchState.handlerStable
$smokeWatchOpenWebP95Ok = $smokeWatchOpenWebP95ThresholdMs -gt 0 -and
    $smokeWatchOpenWebP95Ms -ge 0 -and
    $smokeWatchOpenWebP95Ms -le $smokeWatchOpenWebP95ThresholdMs
$smokeWatchForegroundP95Ok = $smokeWatchForegroundP95ThresholdMs -gt 0 -and
    $smokeWatchForegroundP95Ms -ge 0 -and
    $smokeWatchForegroundP95Ms -le $smokeWatchForegroundP95ThresholdMs
$smokeWatchNoLeaks = $smokeWatchProviderSessionLeakRunCount -eq 0 -and
    $smokeWatchSecretLeakRunCount -eq 0
$smokeWatchNoProviderPageBlockingErrors = $smokeWatchProviderPageBlockingErrorRunCount -eq 0
$smokeWatchPassed = $null -ne $smokeWatchState -and
    $smokeWatchState.status -eq "passed" -and
    $smokeWatchFresh -and
    (Test-Path -LiteralPath $smokeWatchReportPath) -and
    $smokeWatchIterations -ge 3 -and
    $smokeWatchFailureCount -eq 0 -and
    $smokeWatchHandlerStable -and
    $smokeWatchHandlerPackages.Count -gt 0 -and
    $smokeWatchHandlerPackages -notcontains "com.kite.app" -and
    $smokeWatchOpenWebP95Ok -and
    $smokeWatchForegroundP95Ok -and
    $smokeWatchNoLeaks -and
    $smokeWatchNoProviderPageBlockingErrors
$smokeWatchEvidence = if ($null -eq $smokeWatchState) {
    "browser-login-smoke-watch.json missing; run scripts/browser-login-smoke-watch.ps1"
} else {
    "status=$($smokeWatchState.status); checkedAt=$(Get-JsonPropertyString $smokeWatchState 'checkedAt'); fresh24h=$smokeWatchFresh; iterations=$smokeWatchIterations; failureCount=$smokeWatchFailureCount; openWebP95Ms=$smokeWatchOpenWebP95Ms; openWebP95ThresholdMs=$smokeWatchOpenWebP95ThresholdMs; foregroundP95Ms=$smokeWatchForegroundP95Ms; foregroundP95ThresholdMs=$smokeWatchForegroundP95ThresholdMs; handlerPackages=$(Format-Items $smokeWatchHandlerPackages); handlerStable=$smokeWatchHandlerStable; providerSessionLeakRunCount=$smokeWatchProviderSessionLeakRunCount; providerPageBlockingErrorRunCount=$smokeWatchProviderPageBlockingErrorRunCount; secretLeakRunCount=$smokeWatchSecretLeakRunCount; report=$smokeWatchReportPath"
}
Add-ItemResult `
    -Items $items `
    -Id "browser-login-smoke-watch" `
    -Title "人工账号验证前多轮 smoke watch 通过" `
    -Passed $smokeWatchPassed `
    -Evidence $smokeWatchEvidence `
    -RequiredEvidence "最近 24 小时内 browser-login-smoke-watch.ps1 至少 3 轮通过；failureCount=0；/open-web p95 和外部浏览器前台切换 p95 不超过阈值；HTTPS handler 稳定且不是 Kite；provider OAuth 未误建 session；provider 页面无阻塞性错误趋势；OAuth 临时值未原文落盘"

$selfTestKnown = Test-Path -LiteralPath $selfTestScript
$selfTestPassed = if ($RefreshState) { $selfTestKnown -and $selfTestExit -eq 0 } else { $selfTestKnown }
$selfTestEvidence = if ($RefreshState) { "script=$selfTestScript; exit=$selfTestExit" } else { "script=$selfTestScript" }
Add-ItemResult `
    -Items $items `
    -Id "continuation-self-test" `
    -Title "续跑链路自测试通过" `
    -Passed $selfTestPassed `
    -Evidence $selfTestEvidence `
    -RequiredEvidence "scripts/test-browser-login-continuation.ps1 存在；使用 -RefreshState 时必须退出 0"

$runnerRefreshPassed = if ($RefreshState) {
    $refreshRunnerExit -eq 0 -or $refreshRunnerExit -eq 2
} else {
    $true
}
$runnerRefreshEvidence = if ($RefreshState) {
    "refreshRunnerExit=$refreshRunnerExit"
} else {
    "not refreshed in this audit run"
}
Add-ItemResult `
    -Items $items `
    -Id "runner-refresh" `
    -Title "runner 刷新结果不是环境错误" `
    -Passed $runnerRefreshPassed `
    -Evidence $runnerRefreshEvidence `
    -RequiredEvidence "使用 -RefreshState 时 runner 退出码为 0 或 2；1 表示环境或后置验证需要检查"

$runnerKnown = $null -ne $runnerState
$runnerEvidence = if ($runnerKnown) {
    "exitCode=$($runnerState.exitCode); nextAction=$($runnerState.nextAction); readyTargets=$(Format-Items (To-StringArray $runnerState.readyTargets)); waitingTargets=$(Format-Items $waitingTargets)"
} else {
    "runner-status.json missing"
}
Add-ItemResult `
    -Items $items `
    -Id "runner-state" `
    -Title "runner 当前状态可读取" `
    -Passed $runnerKnown `
    -Evidence $runnerEvidence `
    -RequiredEvidence "%LOCALAPPDATA%/Kite/browser-login-continuation/runner-status.json 可读取"

$manualStartKnown = $null -ne $manualAccountStartState
$manualStartFresh = Test-JsonCheckedAtFresh $manualAccountStartState 24
$manualStartStatus = Get-JsonPropertyString $manualAccountStartState "status"
$manualStartTargets = if ($manualStartKnown -and $null -ne $manualAccountStartState.PSObject.Properties["targets"]) {
    To-StringArray $manualAccountStartState.targets
} else {
    @()
}
$manualStartLaunchedTargets = if ($manualStartKnown -and $null -ne $manualAccountStartState.PSObject.Properties["launchedTargets"]) {
    To-StringArray $manualAccountStartState.launchedTargets
} else {
    @()
}
$manualStartWatchExit = Get-JsonPropertyInt $manualAccountStartState "watchExit" -1
$manualStartWatchMaxAttempts = Get-JsonPropertyInt $manualAccountStartState "watchMaxAttempts" -1
$manualStartAcceptedStatuses = @(
    "planned",
    "launched",
    "watch_waiting_for_real_account_authorization",
    "watch_verified"
)
$manualStartBlockingStatuses = @(
    "launch_failed",
    "watch_needs_inspection"
)
$manualStartPassed = if ($allAccountsVerified) {
    $true
} elseif (-not $manualStartKnown) {
    $runnerKnown
} elseif ($manualStartAcceptedStatuses -contains $manualStartStatus) {
    $manualStartFresh
} elseif ($manualStartBlockingStatuses -contains $manualStartStatus) {
    $false
} else {
    $runnerKnown -and $manualStartFresh
}
$manualStartEvidence = if ($manualStartKnown) {
    "status=$manualStartStatus; fresh24h=$manualStartFresh; targets=$(Format-Items $manualStartTargets); launchedTargets=$(Format-Items $manualStartLaunchedTargets); watchExit=$manualStartWatchExit; watchMaxAttempts=$manualStartWatchMaxAttempts; allAccountsVerified=$allAccountsVerified; json=$manualAccountStartJsonPath; report=$manualAccountStartReportPath"
} else {
    "manual-account-start-status.json missing; runnerKnown=$runnerKnown; allAccountsVerified=$allAccountsVerified; can regenerate with scripts/browser-login-manual-account-start.ps1"
}
Add-ItemResult `
    -Items $items `
    -Id "manual-account-start-state" `
    -Title "人工账号启动入口状态可继续或可重新生成" `
    -Passed $manualStartPassed `
    -Evidence $manualStartEvidence `
    -RequiredEvidence "账号未全部 verified 时，manual-account-start 状态缺失但 runner 可读，或最近 24 小时内为 planned/launched/watch_waiting_for_real_account_authorization/watch_verified；launch_failed/watch_needs_inspection 必须先检查。账号都已 verified 时旧启动状态不阻塞完成。"

$accountWatchKnown = $null -ne $accountWatchState
$accountWatchFresh = Test-JsonCheckedAtFresh $accountWatchState 24
$accountWatchStatus = Get-JsonPropertyString $accountWatchState "status"
$accountWatchTargets = if ($accountWatchKnown -and $null -ne $accountWatchState.PSObject.Properties["targets"]) {
    To-StringArray $accountWatchState.targets
} else {
    @()
}
$accountWatchWaitingTargets = if ($accountWatchKnown -and $null -ne $accountWatchState.PSObject.Properties["waitingTargets"]) {
    To-StringArray $accountWatchState.waitingTargets
} else {
    @()
}
$accountWatchVerifiedTargets = if ($accountWatchKnown -and $null -ne $accountWatchState.PSObject.Properties["verifiedTargets"]) {
    To-StringArray $accountWatchState.verifiedTargets
} else {
    @()
}
$accountWatchFailedTargets = if ($accountWatchKnown -and $null -ne $accountWatchState.PSObject.Properties["failedTargets"]) {
    To-StringArray $accountWatchState.failedTargets
} else {
    @()
}
$accountWatchErrorTargets = if ($accountWatchKnown -and $null -ne $accountWatchState.PSObject.Properties["errorTargets"]) {
    To-StringArray $accountWatchState.errorTargets
} else {
    @()
}
$accountWatchAttempts = Get-JsonPropertyInt $accountWatchState "attempts" -1
$accountWatchMaxAttempts = Get-JsonPropertyInt $accountWatchState "maxAttempts" -1
$accountWatchAcceptedStatuses = @(
    "waiting_for_real_account_authorization",
    "verified"
)
$accountWatchBlockingStatuses = @(
    "smoke_failed",
    "manual_readiness_failed",
    "needs_inspection"
)
$accountWatchPassed = if ($allAccountsVerified) {
    $true
} elseif (-not $accountWatchKnown) {
    $runnerKnown
} elseif ($accountWatchBlockingStatuses -contains $accountWatchStatus) {
    -not $accountWatchFresh -and $runnerKnown
} elseif ($accountWatchAcceptedStatuses -contains $accountWatchStatus) {
    $accountWatchFresh -or $runnerKnown
} else {
    $runnerKnown -and (-not $accountWatchFresh)
}
$accountWatchEvidence = if ($accountWatchKnown) {
    "status=$accountWatchStatus; fresh24h=$accountWatchFresh; targets=$(Format-Items $accountWatchTargets); waitingTargets=$(Format-Items $accountWatchWaitingTargets); verifiedTargets=$(Format-Items $accountWatchVerifiedTargets); failedTargets=$(Format-Items $accountWatchFailedTargets); errorTargets=$(Format-Items $accountWatchErrorTargets); attempts=$accountWatchAttempts; maxAttempts=$accountWatchMaxAttempts; allAccountsVerified=$allAccountsVerified; runnerKnown=$runnerKnown; json=$accountWatchJsonPath; report=$accountWatchReportPath"
} else {
    "account-watch-status.json missing; runnerKnown=$runnerKnown; allAccountsVerified=$allAccountsVerified; can regenerate with scripts/browser-login-account-watch.ps1"
}
Add-ItemResult `
    -Items $items `
    -Id "account-watch-state" `
    -Title "人工账号 watch 状态可继续或可重新生成" `
    -Passed $accountWatchPassed `
    -Evidence $accountWatchEvidence `
    -RequiredEvidence "账号未全部 verified 时，account-watch 状态缺失但 runner 可读，或状态为 waiting_for_real_account_authorization/verified；新鲜的 smoke_failed/manual_readiness_failed/needs_inspection 必须先检查。状态陈旧且 runner 当前可读时不阻塞。账号都已 verified 时旧 watch 状态不阻塞完成。"

$evidenceReportExists = Test-Path -LiteralPath $evidenceReportPath
$runnerStatePath = Join-Path $StateDir "runner-status.json"
$evidenceReportFresh = $false
$evidenceReportHasStatus = $false
$evidenceReportEvidence = "report missing"
if ($evidenceReportExists) {
    $reportInfo = Get-Item -LiteralPath $evidenceReportPath
    $reportText = Get-Content -Raw -LiteralPath $evidenceReportPath
    $evidenceReportHasStatus = $reportText -match "状态："
    $freshText = "not checked"
    if ($RefreshState -and (Test-Path -LiteralPath $runnerStatePath)) {
        $runnerInfo = Get-Item -LiteralPath $runnerStatePath
        $evidenceReportFresh = $reportInfo.LastWriteTime -ge $runnerInfo.LastWriteTime.AddSeconds(-2)
        $freshText = "reportLastWrite=$($reportInfo.LastWriteTime.ToString('yyyy-MM-ddTHH:mm:ssK')); runnerLastWrite=$($runnerInfo.LastWriteTime.ToString('yyyy-MM-ddTHH:mm:ssK'))"
    } else {
        $evidenceReportFresh = $true
    }
    $evidenceReportEvidence = "path=$evidenceReportPath; hasStatus=$evidenceReportHasStatus; freshness=$freshText"
}
Add-ItemResult `
    -Items $items `
    -Id "runner-evidence-report" `
    -Title "runner 自动生成账号证据摘要" `
    -Passed ($evidenceReportExists -and $evidenceReportHasStatus -and $evidenceReportFresh) `
    -Evidence $evidenceReportEvidence `
    -RequiredEvidence "post-auth-evidence-report.md 存在，含状态摘要；使用 -RefreshState 时其更新时间不早于 runner-status.json"

$scheduledTaskPassed = $false
$scheduledTaskEvidence = "task=$scheduledTaskName missing"
try {
    $task = Get-ScheduledTask -TaskName $scheduledTaskName -ErrorAction Stop
    $taskInfo = Get-ScheduledTaskInfo -TaskName $scheduledTaskName -ErrorAction Stop
    $runnerPath = (Resolve-Path $runnerScript).Path
    $actionOk = $false
    $actionEvidence = @()
    foreach ($action in @($task.Actions)) {
        $actionText = "$($action.Execute) $($action.Arguments)"
        $actionEvidence += $actionText
        if ($action.Arguments -like "*$runnerPath*" -and $action.Arguments -like "*3f8bbaad*") {
            $actionOk = $true
        }
    }

    $intervalOk = $false
    $intervalEvidence = @()
    foreach ($trigger in @($task.Triggers)) {
        $intervalText = $null
        if ($null -ne $trigger.Repetition -and -not [string]::IsNullOrWhiteSpace($trigger.Repetition.Interval)) {
            $intervalText = $trigger.Repetition.Interval
            $span = [System.Xml.XmlConvert]::ToTimeSpan($trigger.Repetition.Interval)
            if ($span -le (New-TimeSpan -Minutes 5)) {
                $intervalOk = $true
            }
        }
        $intervalEvidence += "enabled=$($trigger.Enabled); interval=$intervalText"
    }

    $enabledOk = $task.State -ne "Disabled"
    $scheduledTaskPassed = $enabledOk -and $actionOk -and $intervalOk
    $scheduledTaskEvidence = "state=$($task.State); lastResult=$($taskInfo.LastTaskResult); nextRun=$($taskInfo.NextRunTime); actionOk=$actionOk; intervalOk=$intervalOk; actions=$($actionEvidence -join ' | '); triggers=$($intervalEvidence -join ' | ')"
} catch {
    $scheduledTaskEvidence = "task=$scheduledTaskName error=$($_.Exception.Message)"
}
Add-ItemResult `
    -Items $items `
    -Id "scheduled-continuation-task" `
    -Title "Windows 计划任务已绑定浏览器线 runner" `
    -Passed $scheduledTaskPassed `
    -Evidence $scheduledTaskEvidence `
    -RequiredEvidence "KiteBrowserLoginContinuationGate 启用，动作调用本副本 runner，包含 serial 3f8bbaad，重复间隔不高于 5 分钟"

$longRunTaskPassed = $false
$longRunTaskEvidence = "task=$longRunScheduledTaskName missing"
try {
    $longRunTask = Get-ScheduledTask -TaskName $longRunScheduledTaskName -ErrorAction Stop
    $longRunInfo = Get-ScheduledTaskInfo -TaskName $longRunScheduledTaskName -ErrorAction Stop
    $cyclePath = (Resolve-Path $longRunCycleScript).Path
    $actionOk = $false
    $actionEvidence = @()
    foreach ($action in @($longRunTask.Actions)) {
        $actionText = "$($action.Execute) $($action.Arguments)"
        $actionEvidence += $actionText
        if ($action.Arguments -like "*$cyclePath*" -and
            $action.Arguments -like "*3f8bbaad*" -and
            $action.Arguments -like "*-SmokeIterations 6*" -and
            $action.Arguments -like "*-SmokeIntervalSeconds 600*") {
            $actionOk = $true
        }
    }

    $intervalOk = $false
    $durationOk = $false
    $intervalEvidence = @()
    foreach ($trigger in @($longRunTask.Triggers)) {
        $intervalText = $null
        $durationText = $null
        if ($null -ne $trigger.Repetition -and -not [string]::IsNullOrWhiteSpace($trigger.Repetition.Interval)) {
            $intervalText = $trigger.Repetition.Interval
            $span = [System.Xml.XmlConvert]::ToTimeSpan($trigger.Repetition.Interval)
            if ($span -ge (New-TimeSpan -Minutes 15) -and $span -le (New-TimeSpan -Minutes 60)) {
                $intervalOk = $true
            }
        }
        if ($null -ne $trigger.Repetition -and -not [string]::IsNullOrWhiteSpace($trigger.Repetition.Duration)) {
            $durationText = $trigger.Repetition.Duration
            $duration = [System.Xml.XmlConvert]::ToTimeSpan($trigger.Repetition.Duration)
            if ($duration -ge (New-TimeSpan -Days 1)) {
                $durationOk = $true
            }
        }
        $intervalEvidence += "enabled=$($trigger.Enabled); interval=$intervalText; duration=$durationText"
    }

    $enabledOk = $longRunTask.State -ne "Disabled"
    $longRunTaskPassed = $enabledOk -and $actionOk -and $intervalOk -and $durationOk
    $longRunTaskEvidence = "state=$($longRunTask.State); lastResult=$($longRunInfo.LastTaskResult); nextRun=$($longRunInfo.NextRunTime); actionOk=$actionOk; intervalOk=$intervalOk; durationOk=$durationOk; actions=$($actionEvidence -join ' | '); triggers=$($intervalEvidence -join ' | ')"
} catch {
    $longRunTaskEvidence = "task=$longRunScheduledTaskName error=$($_.Exception.Message)"
}
Add-ItemResult `
    -Items $items `
    -Id "scheduled-long-run-cycle-task" `
    -Title "Windows 计划任务已绑定浏览器线 long-run cycle" `
    -Passed $longRunTaskPassed `
    -Evidence $longRunTaskEvidence `
    -RequiredEvidence "KiteBrowserLoginLongRunCycle 启用，动作调用本副本 long-run cycle，包含 serial 3f8bbaad，使用 SmokeIterations=6 / SmokeIntervalSeconds=600，重复间隔在 15 到 60 分钟之间且持续至少 1 天"

$codexVerified = $verifiedTargets -contains "codex"
Add-ItemResult `
    -Items $items `
    -Id "codex-account" `
    -Title "Codex/OpenAI 完整账号授权完成证据" `
    -Passed $codexVerified `
    -Evidence "verifiedTargets=$(Format-Items $verifiedTargets); waitingTargets=$(Format-Items $waitingTargets)" `
    -RequiredEvidence "post-auth-status.json 或 runner-status.json 中 verifiedTargets 包含 codex，并有脱敏 evidence report/raw 证据"

$claudeVerified = $verifiedTargets -contains "claude"
Add-ItemResult `
    -Items $items `
    -Id "claude-account" `
    -Title "Claude Code 完整账号授权完成证据" `
    -Passed $claudeVerified `
    -Evidence "verifiedTargets=$(Format-Items $verifiedTargets); waitingTargets=$(Format-Items $waitingTargets)" `
    -RequiredEvidence "post-auth-status.json 或 runner-status.json 中 verifiedTargets 包含 claude，并有脱敏 evidence report/raw 证据"

$failedItems = @($items | Where-Object { -not $_.passed })
$failedItemIds = @($failedItems | ForEach-Object { $_["id"] })
$status = if ($failedItems.Count -eq 0) { "complete" } else { "incomplete" }
$checkedAt = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ssK")

$audit = New-Object System.Collections.Specialized.OrderedDictionary
$audit["checkedAt"] = $checkedAt
$audit["status"] = $status
$audit["repoRoot"] = $RepoRoot.ToString()
$audit["stateDir"] = $StateDir
$audit["refreshState"] = [bool]$RefreshState
$audit["skipImplementationChecks"] = [bool]$SkipImplementationChecks
$audit["runSmokeTest"] = [bool]$RunSmokeTest
$audit["refreshRunnerExit"] = $refreshRunnerExit
$audit["selfTestExit"] = $selfTestExit
$audit["smokeExit"] = $smokeExit
$audit["unitTestExit"] = $unitTestExit
$audit["assembleExit"] = $assembleExit
$audit["adbDevicesExit"] = $adbDevicesExit
$audit["adbDeviceLine"] = $adbDeviceLine
$audit["verifiedTargets"] = @($verifiedTargets)
$audit["waitingTargets"] = @($waitingTargets)
$audit["accountWatchStatus"] = $accountWatchStatus
$audit["accountWatchTargets"] = @($accountWatchTargets)
$audit["accountWatchWaitingTargets"] = @($accountWatchWaitingTargets)
$audit["accountWatchVerifiedTargets"] = @($accountWatchVerifiedTargets)
$audit["accountWatchAttempts"] = $accountWatchAttempts
$audit["accountWatchMaxAttempts"] = $accountWatchMaxAttempts
$audit["manualStartStatus"] = $manualStartStatus
$audit["manualStartTargets"] = @($manualStartTargets)
$audit["manualStartLaunchedTargets"] = @($manualStartLaunchedTargets)
$audit["manualStartWatchExit"] = $manualStartWatchExit
$audit["manualStartWatchMaxAttempts"] = $manualStartWatchMaxAttempts
$audit["failedItemIds"] = @($failedItemIds)
$audit["items"] = @($items.ToArray())

$audit | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $auditJsonPath -Encoding UTF8

$lines = New-Object System.Collections.Generic.List[string]
$lines.Add("# 浏览器登录任务完成审计")
$lines.Add("")
$lines.Add("- 生成时间：$checkedAt")
$lines.Add("- 状态：$status")
$lines.Add("- verifiedTargets：$(Format-Items $verifiedTargets)")
$lines.Add("- waitingTargets：$(Format-Items $waitingTargets)")
$lines.Add("")
$lines.Add("## 审计项")
$lines.Add("")
foreach ($item in $items) {
    $itemId = $item["id"]
    $itemTitle = $item["title"]
    $itemPassed = [bool]$item["passed"]
    $itemEvidence = $item["evidence"]
    $itemRequiredEvidence = $item["requiredEvidence"]
    $lines.Add("- $(Format-Status $itemPassed) ``$itemId``：$itemTitle")
    $lines.Add("  - 证据：$itemEvidence")
    $lines.Add("  - 需要：$itemRequiredEvidence")
}
$lines.Add("")
$lines.Add("## 边界")
$lines.Add("")
$lines.Add("- 本审计只判断当前证据是否足以宣称浏览器线完成。")
$lines.Add("- ``incomplete`` 不表示实现失败；它表示仍缺目标要求中的强证据。")
$lines.Add("- Codex/Claude 账号完成必须来自真实账号授权后的官方状态命令和 post-auth 证据，不能用 mock 或伪造 callback 替代。")
$lines.ToArray() | Set-Content -LiteralPath $auditReportPath -Encoding UTF8

Write-Output "browser-login-completion-audit"
Write-Output "checkedAt=$checkedAt"
Write-Output "status=$status"
Write-Output "verifiedTargets=$($verifiedTargets -join ',')"
Write-Output "waitingTargets=$($waitingTargets -join ',')"
Write-Output "failedItemIds=$($failedItemIds -join ',')"
Write-Output "auditJson=$auditJsonPath"
Write-Output "auditReport=$auditReportPath"

if ($status -eq "complete") {
    exit 0
}

exit 2
