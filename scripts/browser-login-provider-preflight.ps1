param(
    [string]$RepoRoot = "",
    [string]$StateDir = "",
    [string]$Serial = "3f8bbaad",
    [int]$FreshHours = 24,
    [switch]$RefreshRunner,
    [switch]$RunSmokeTest,
    [switch]$RunSmokeWatch,
    [int]$SmokeIterations = 3,
    [int]$SmokeIntervalSeconds = 0,
    [switch]$RefreshReadiness,
    [switch]$RunCompletionAudit
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

New-Item -ItemType Directory -Force -Path $StateDir | Out-Null

$runnerScript = Join-Path $RepoRoot "scripts\browser-login-continuation-runner.ps1"
$smokeTestScript = Join-Path $RepoRoot "scripts\browser-login-smoke-test.ps1"
$smokeWatchScript = Join-Path $RepoRoot "scripts\browser-login-smoke-watch.ps1"
$manualReadinessScript = Join-Path $RepoRoot "scripts\browser-login-manual-readiness.ps1"
$completionAuditScript = Join-Path $RepoRoot "scripts\browser-login-completion-audit.ps1"

$preflightJsonPath = Join-Path $StateDir "provider-auth-preflight.json"
$preflightReportPath = Join-Path $StateDir "provider-auth-preflight.md"

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

function Get-JsonPropertyString {
    param(
        [object]$Object,
        [string]$Name
    )

    $value = Get-JsonPropertyValue $Object $Name
    if ($null -eq $value) {
        return ""
    }
    return $value.ToString()
}

function Get-JsonPropertyValue {
    param(
        [object]$Object,
        [string]$Name
    )

    if ($null -eq $Object -or $null -eq $Object.PSObject.Properties[$Name]) {
        return $null
    }
    return $Object.PSObject.Properties[$Name].Value
}

function Format-Timestamp {
    param([object]$Value)

    if ($null -eq $Value) {
        return $null
    }

    if ($Value -is [datetime]) {
        return $Value.ToString("yyyy-MM-ddTHH:mm:ssK")
    }

    $text = $Value.ToString().Trim()
    if ([string]::IsNullOrWhiteSpace($text)) {
        return $null
    }

    try {
        $parsed = [datetimeoffset]::Parse($text, [Globalization.CultureInfo]::InvariantCulture)
        return $parsed.ToString("yyyy-MM-ddTHH:mm:ssK")
    } catch {
        return $text
    }
}

function Get-JsonPropertyInt {
    param(
        [object]$Object,
        [string]$Name,
        [int]$DefaultValue = -1
    )

    $text = Get-JsonPropertyString $Object $Name
    if ([string]::IsNullOrWhiteSpace($text)) {
        return $DefaultValue
    }
    $parsed = 0
    if ([int]::TryParse($text, [ref]$parsed)) {
        return $parsed
    }
    return $DefaultValue
}

function Get-JsonPropertyDouble {
    param(
        [object]$Object,
        [string]$Name,
        [double]$DefaultValue = -1
    )

    $text = Get-JsonPropertyString $Object $Name
    if ([string]::IsNullOrWhiteSpace($text)) {
        return $DefaultValue
    }
    $parsed = 0.0
    if ([double]::TryParse($text, [ref]$parsed)) {
        return $parsed
    }
    return $DefaultValue
}

function Test-Fresh {
    param(
        [object]$Object,
        [int]$Hours
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

function Get-ItemById {
    param(
        [object]$State,
        [string]$Id
    )

    if ($null -eq $State -or $null -eq $State.PSObject.Properties["items"]) {
        return $null
    }
    foreach ($item in @($State.items)) {
        if ($null -ne $item -and (Get-JsonPropertyString $item "id") -eq $Id) {
            return $item
        }
    }
    return $null
}

function Test-ItemPassed {
    param(
        [object]$State,
        [string]$Id
    )

    $item = Get-ItemById $State $Id
    if ($null -eq $item -or $null -eq $item.PSObject.Properties["passed"]) {
        return $false
    }
    return [bool]$item.passed
}

function Format-Items {
    param([string[]]$Items)

    if ($Items.Count -eq 0) {
        return "(none)"
    }
    return ($Items -join ", ")
}

function Format-MarkdownCell {
    param([string]$Text)

    if ([string]::IsNullOrWhiteSpace($Text)) {
        return ""
    }
    return (($Text -replace "\r?\n", " ") -replace "\|", "/")
}

function Add-Check {
    param(
        [System.Collections.Generic.List[object]]$Checks,
        [System.Collections.Generic.List[string]]$BlockingFailures,
        [System.Collections.Generic.List[string]]$FailedBuckets,
        [string]$Id,
        [string]$Title,
        [string]$Bucket,
        [bool]$Passed,
        [string]$Evidence,
        [string]$RequiredEvidence,
        [bool]$Blocking = $true
    )

    $Checks.Add([ordered]@{
        id = $Id
        title = $Title
        bucket = $Bucket
        passed = $Passed
        blocking = $Blocking
        evidence = $Evidence
        requiredEvidence = $RequiredEvidence
    })

    if ($Blocking -and -not $Passed) {
        $BlockingFailures.Add($Id)
        if ($FailedBuckets -notcontains $Bucket) {
            $FailedBuckets.Add($Bucket)
        }
    }
}

$runnerExit = $null
$smokeExit = $null
$smokeWatchExit = $null
$readinessExit = $null
$auditExit = $null

if ($RefreshRunner) {
    if (-not (Test-Path -LiteralPath $runnerScript)) {
        throw "Runner script not found: $runnerScript"
    }
    & $runnerScript -Serial $Serial -StateDir $StateDir | Out-Null
    $runnerExit = $LASTEXITCODE
}

if ($RunSmokeTest) {
    if (-not (Test-Path -LiteralPath $smokeTestScript)) {
        throw "Smoke test script not found: $smokeTestScript"
    }
    & $smokeTestScript -Serial $Serial -StateDir $StateDir | Out-Null
    $smokeExit = $LASTEXITCODE
}

if ($RunSmokeWatch) {
    if (-not (Test-Path -LiteralPath $smokeWatchScript)) {
        throw "Smoke watch script not found: $smokeWatchScript"
    }
    & $smokeWatchScript -Serial $Serial -StateDir $StateDir -Iterations $SmokeIterations -IntervalSeconds $SmokeIntervalSeconds | Out-Null
    $smokeWatchExit = $LASTEXITCODE
}

if ($RefreshReadiness) {
    if (-not (Test-Path -LiteralPath $manualReadinessScript)) {
        throw "Manual readiness script not found: $manualReadinessScript"
    }
    & $manualReadinessScript -RepoRoot $RepoRoot -StateDir $StateDir -Serial $Serial -RefreshState | Out-Null
    $readinessExit = $LASTEXITCODE
}

if ($RunCompletionAudit) {
    if (-not (Test-Path -LiteralPath $completionAuditScript)) {
        throw "Completion audit script not found: $completionAuditScript"
    }
    & $completionAuditScript -RepoRoot $RepoRoot -StateDir $StateDir -RefreshState | Out-Null
    $auditExit = $LASTEXITCODE
}

$smokeState = Read-JsonOrNull (Join-Path $StateDir "browser-login-smoke.json")
$smokeWatchState = Read-JsonOrNull (Join-Path $StateDir "browser-login-smoke-watch.json")
$runnerState = Read-JsonOrNull (Join-Path $StateDir "runner-status.json")
$gateState = Read-JsonOrNull (Join-Path $StateDir "last-status.json")
$manualReadinessState = Read-JsonOrNull (Join-Path $StateDir "manual-account-readiness.json")
$completionAuditState = Read-JsonOrNull (Join-Path $StateDir "completion-audit.json")
$manualAccountStartState = Read-JsonOrNull (Join-Path $StateDir "manual-account-start-status.json")
$accountWatchState = Read-JsonOrNull (Join-Path $StateDir "account-watch-status.json")

$checks = New-Object System.Collections.Generic.List[object]
$blockingFailures = New-Object System.Collections.Generic.List[string]
$failedBuckets = New-Object System.Collections.Generic.List[string]
$officialSources = New-Object System.Collections.Generic.List[object]

$officialSources.Add([ordered]@{
    id = "google-oauth-policies"
    url = "https://developers.google.com/identity/protocols/oauth2/policies"
    summary = "按平台创建匹配 OAuth client；redirect/origin 必须符合安全和所有权要求；不要把 WebView 当成正式 OAuth 承载面。"
})
$officialSources.Add([ordered]@{
    id = "google-webview-remediation"
    url = "https://support.google.com/faqs/answer/12284343"
    summary = "Google 建议把 WebView OAuth 替换为 Chrome Custom Tabs。"
})
$officialSources.Add([ordered]@{
    id = "google-installed-apps"
    url = "https://developers.google.com/identity/protocols/oauth2/native-app"
    summary = "安装式应用应打开系统浏览器并用本地或受控 redirect 接收授权响应。"
})
$officialSources.Add([ordered]@{
    id = "chrome-custom-tabs-warmup"
    url = "https://developer.chrome.com/docs/android/custom-tabs/guide-warmup-prefetch"
    summary = "Custom Tabs warmup 和 mayLaunchUrl 可降低外部认证页体感延迟；能力应先探测。"
})
$officialSources.Add([ordered]@{
    id = "openai-codex-auth"
    url = "https://developers.openai.com/codex/auth"
    summary = "Codex CLI 浏览器登录遇到 headless 或 localhost callback 问题时，优先 device code，或用官方 fallback。"
})
$officialSources.Add([ordered]@{
    id = "anthropic-claude-code-auth"
    url = "https://docs.anthropic.com/en/docs/claude-code/iam"
    summary = "Claude Code 首次启动打开浏览器；callback 到不了本地时，把浏览器显示的 code 粘回终端是官方 fallback。"
})

$smokeKnown = $null -ne $smokeState
$smokeFresh = Test-Fresh $smokeState $FreshHours
$smokeSchema = Get-JsonPropertyInt $smokeState "schemaVersion"
$smokeStatus = Get-JsonPropertyString $smokeState "status"
$smokeFailedItems = if ($smokeKnown -and $null -ne $smokeState.PSObject.Properties["failedItemIds"]) {
    To-StringArray $smokeState.failedItemIds
} else {
    @()
}

$authHostResults = @()
if ($smokeKnown -and $null -ne $smokeState.PSObject.Properties["authHostNetworkResults"]) {
    $authHostResults = @($smokeState.authHostNetworkResults)
}
$authHostsOk = $authHostResults.Count -ge 3
$authHostEvidenceParts = New-Object System.Collections.Generic.List[string]
foreach ($hostResult in $authHostResults) {
    $hostName = Get-JsonPropertyString $hostResult "host"
    $ok = $false
    if ($null -ne $hostResult.PSObject.Properties["ok"]) {
        $ok = [bool]$hostResult.ok
    }
    $attemptCount = Get-JsonPropertyInt $hostResult "attemptCount" 0
    if (-not $ok -or $attemptCount -lt 1) {
        $authHostsOk = $false
    }
    $authHostEvidenceParts.Add("$($hostName):ok=$ok;attempts=$attemptCount;http=$(Get-JsonPropertyInt $hostResult 'httpCode' 0)")
}

$httpsHandlerPackage = Get-JsonPropertyString $smokeState "httpsBrowserResolvePackage"
$customTabsServiceCount = Get-JsonPropertyInt $smokeState "customTabsServiceCount" 0
$foregroundPackage = Get-JsonPropertyString $smokeState "foregroundPackage"
$providerResults = @()
if ($smokeKnown -and $null -ne $smokeState.PSObject.Properties["providerOAuthResults"]) {
    $providerResults = @($smokeState.providerOAuthResults)
}
$providerResultsOk = $providerResults.Count -ge 2
$providerEvidenceParts = New-Object System.Collections.Generic.List[string]
foreach ($result in $providerResults) {
    $id = Get-JsonPropertyString $result "id"
    $accepted = $false
    $external = $false
    $matchesHandler = $false
    $passed = $false
    if ($null -ne $result.PSObject.Properties["accepted"]) { $accepted = [bool]$result.accepted }
    if ($null -ne $result.PSObject.Properties["externalBrowser"]) { $external = [bool]$result.externalBrowser }
    if ($null -ne $result.PSObject.Properties["matchesHandler"]) { $matchesHandler = [bool]$result.matchesHandler }
    if ($null -ne $result.PSObject.Properties["passed"]) { $passed = [bool]$result.passed }
    if (-not ($accepted -and $external -and $matchesHandler -and $passed)) {
        $providerResultsOk = $false
    }
    $providerEvidenceParts.Add("$($id):accepted=$accepted;external=$external;matchesHandler=$matchesHandler;handoffMs=$(Get-JsonPropertyInt $result 'handoffForegroundElapsedMs' 0)")
}

$providerOAuthNewSessionCount = Get-JsonPropertyInt $smokeState "providerOAuthNewSessionCount"
$providerPageSignalState = Get-JsonPropertyString $smokeState "providerPageSignalState"
$providerPageBlockingErrorCount = Get-JsonPropertyInt $smokeState "providerPageBlockingErrorCount"
$providerPageChallengeHintCount = Get-JsonPropertyInt $smokeState "providerPageChallengeHintCount"
$providerForegroundMax = Get-JsonPropertyDouble $smokeState "providerOAuthForegroundMaxElapsedMs"
$foregroundThreshold = Get-JsonPropertyInt $smokeState "foregroundResponsiveThresholdMs" 5000
$openWebElapsed = Get-JsonPropertyInt $smokeState "openWebElapsedMs"
$appRedirectOpenWebElapsed = Get-JsonPropertyInt $smokeState "appRedirectOpenWebElapsedMs"
$localWebOpenWebElapsed = Get-JsonPropertyInt $smokeState "localWebOpenWebElapsedMs"
$openWebThreshold = Get-JsonPropertyInt $smokeState "openWebResponsiveThresholdMs" 1500

Add-Check `
    -Checks $checks `
    -BlockingFailures $blockingFailures `
    -FailedBuckets $failedBuckets `
    -Id "smoke-current-schema" `
    -Title "真机 smoke 证据新鲜且格式足够" `
    -Bucket "browser_environment" `
    -Passed ($smokeKnown -and $smokeFresh -and $smokeStatus -eq "passed" -and $smokeSchema -ge 10 -and $smokeFailedItems.Count -eq 0) `
    -Evidence "known=$smokeKnown; fresh${FreshHours}h=$smokeFresh; status=$smokeStatus; schemaVersion=$smokeSchema; failedItemIds=$(Format-Items $smokeFailedItems); smokeExit=$smokeExit" `
    -RequiredEvidence "最近 $FreshHours 小时内 browser-login-smoke-test.ps1 通过，schemaVersion>=10，且 failedItemIds 为空"

Add-Check `
    -Checks $checks `
    -BlockingFailures $blockingFailures `
    -FailedBuckets $failedBuckets `
    -Id "auth-hosts-network" `
    -Title "授权主机设备侧网络可达" `
    -Bucket "browser_environment" `
    -Passed ($smokeKnown -and $authHostsOk) `
    -Evidence ($authHostEvidenceParts.ToArray() -join "; ") `
    -RequiredEvidence "OnePlus 8T 能通过 HTTPS 到达 accounts.google.com、auth.openai.com、claude.ai；允许 2xx-4xx 作为网络/TLS 可达，不代表账号成功"

Add-Check `
    -Checks $checks `
    -BlockingFailures $blockingFailures `
    -FailedBuckets $failedBuckets `
    -Id "external-user-agent" `
    -Title "OAuth 授权页离开 Kite WebView" `
    -Bucket "browser_environment" `
    -Passed ($smokeKnown -and
        (Test-ItemPassed $smokeState "external-browser-handler-resolved") -and
        (Test-ItemPassed $smokeState "foreground-external-browser") -and
        (Test-ItemPassed $smokeState "ui-no-disallowed-useragent") -and
        -not [string]::IsNullOrWhiteSpace($httpsHandlerPackage) -and
        $httpsHandlerPackage -ne "com.kite.app" -and
        $foregroundPackage -ne "com.kite.app") `
    -Evidence "handler=$httpsHandlerPackage; foreground=$foregroundPackage; customTabsServiceCount=$customTabsServiceCount; disallowedUserAgentItem=$(Test-ItemPassed $smokeState 'ui-no-disallowed-useragent')" `
    -RequiredEvidence "HTTPS 授权 URL 解析到 com.kite.app 之外的浏览器；Google OAuth 前台离开 Kite；UI dump 无 disallowed_useragent"

Add-Check `
    -Checks $checks `
    -BlockingFailures $blockingFailures `
    -FailedBuckets $failedBuckets `
    -Id "provider-page-no-blocking-error" `
    -Title "外部 provider 页面没有阻塞性错误信号" `
    -Bucket "browser_environment" `
    -Passed ($smokeKnown -and
        (Test-ItemPassed $smokeState "provider-page-no-blocking-error") -and
        $providerPageBlockingErrorCount -eq 0 -and
        -not [string]::IsNullOrWhiteSpace($providerPageSignalState) -and
        $providerPageSignalState -ne "blocking_error") `
    -Evidence "state=$providerPageSignalState; blockingErrorCount=$providerPageBlockingErrorCount; challengeHintCount=$providerPageChallengeHintCount" `
    -RequiredEvidence "外部浏览器 UI dump 没有 disallowed_useragent、redirect_uri_mismatch、invalid_client、unsupported_browser、Error 400/403 等阻塞性错误；账号挑战提示只作为人工账号节点"

Add-Check `
    -Checks $checks `
    -BlockingFailures $blockingFailures `
    -FailedBuckets $failedBuckets `
    -Id "multi-provider-oauth-shape" `
    -Title "OpenAI / Claude OAuth 形态同样外部打开" `
    -Bucket "browser_environment" `
    -Passed ($smokeKnown -and $providerResultsOk -and $providerOAuthNewSessionCount -eq 0 -and (Test-ItemPassed $smokeState "provider-oauth-no-auth-session")) `
    -Evidence "providerResults=$($providerEvidenceParts.ToArray() -join '; '); providerOAuthNewSessionCount=$providerOAuthNewSessionCount" `
    -RequiredEvidence "OpenAI/Codex 与 Claude 相关 OAuth 形态 URL 都进入外部浏览器，且不新增假 AppRedirect/CliLoopback session"

Add-Check `
    -Checks $checks `
    -BlockingFailures $blockingFailures `
    -FailedBuckets $failedBuckets `
    -Id "redirect-type-boundary" `
    -Title "redirect 类型边界正确" `
    -Bucket "provider_configuration" `
    -Passed ($smokeKnown -and
        (Test-ItemPassed $smokeState "no-third-party-appredirect-session") -and
        (Test-ItemPassed $smokeState "appredirect-pending-session") -and
        (Test-ItemPassed $smokeState "appredirect-callback-delivered")) `
    -Evidence "thirdPartyNoSession=$(Test-ItemPassed $smokeState 'no-third-party-appredirect-session'); appRedirectStatus=$(Get-JsonPropertyString $smokeState 'appRedirectStatus'); appRedirectSessionId=$(Get-JsonPropertyString $smokeState 'appRedirectSessionId')" `
    -RequiredEvidence "第三方 HTTPS redirect 只证明外部浏览器，不制造回 Kite 的假 session；kite-auth://callback 能 pending 并交付"

Add-Check `
    -Checks $checks `
    -BlockingFailures $blockingFailures `
    -FailedBuckets $failedBuckets `
    -Id "sensitive-boundary" `
    -Title "授权临时值和 callback 参数不原文落盘" `
    -Bucket "sensitive_boundary" `
    -Passed ($smokeKnown -and
        (Test-ItemPassed $smokeState "appredirect-callback-redacted") -and
        (Test-ItemPassed $smokeState "no-oauth-temporary-values-in-app-files") -and
        (Get-JsonPropertyInt $smokeState "appRedirectRawSecretHitCount") -eq 0 -and
        (Get-JsonPropertyInt $smokeState "appPrivateRawTemporaryValueHitCount") -eq 0) `
    -Evidence "returnedUrl=$(Get-JsonPropertyString $smokeState 'appRedirectReturnedUrl'); rawSecretHitCount=$(Get-JsonPropertyInt $smokeState 'appRedirectRawSecretHitCount'); appPrivateRawTemporaryValueHitCount=$(Get-JsonPropertyInt $smokeState 'appPrivateRawTemporaryValueHitCount')" `
    -RequiredEvidence "callback 只保存 code/access_token/state=present；app 私有 files/shared_prefs 无本轮 OAuth state/code/token 原文"

Add-Check `
    -Checks $checks `
    -BlockingFailures $blockingFailures `
    -FailedBuckets $failedBuckets `
    -Id "handoff-performance" `
    -Title "本地接收与外部浏览器前台切换足够快" `
    -Bucket "performance" `
    -Passed ($smokeKnown -and
        $openWebElapsed -ge 0 -and
        $appRedirectOpenWebElapsed -ge 0 -and
        $localWebOpenWebElapsed -ge 0 -and
        $openWebElapsed -le $openWebThreshold -and
        $appRedirectOpenWebElapsed -le $openWebThreshold -and
        $localWebOpenWebElapsed -le $openWebThreshold -and
        $providerForegroundMax -ge 0 -and
        $providerForegroundMax -le $foregroundThreshold) `
    -Evidence "googleOpenWebMs=$openWebElapsed; appRedirectOpenWebMs=$appRedirectOpenWebElapsed; localWebOpenWebMs=$localWebOpenWebElapsed; openWebThresholdMs=$openWebThreshold; providerForegroundMaxMs=$providerForegroundMax; foregroundThresholdMs=$foregroundThreshold" `
    -RequiredEvidence "/open-web 接收不超过阈值，Google/OpenAI/Claude OAuth 形态切到外部浏览器前台不超过阈值"

$smokeWatchKnown = $null -ne $smokeWatchState
$smokeWatchFresh = Test-Fresh $smokeWatchState $FreshHours
$smokeWatchStatus = Get-JsonPropertyString $smokeWatchState "status"
$smokeWatchIterations = Get-JsonPropertyInt $smokeWatchState "iterations"
$smokeWatchFailureCount = Get-JsonPropertyInt $smokeWatchState "failureCount"
$smokeWatchOpenWebP95 = Get-JsonPropertyInt $smokeWatchState "openWebP95Ms"
$smokeWatchOpenWebThreshold = Get-JsonPropertyInt $smokeWatchState "openWebP95ThresholdMs" 1500
$smokeWatchForegroundP95 = Get-JsonPropertyInt $smokeWatchState "foregroundP95Ms"
$smokeWatchForegroundThreshold = Get-JsonPropertyInt $smokeWatchState "foregroundP95ThresholdMs" 5000
$smokeWatchProviderPageBlockingErrorRunCount = Get-JsonPropertyInt $smokeWatchState "providerPageBlockingErrorRunCount"
$smokeWatchHandlerStable = $false
if ($smokeWatchKnown -and $null -ne $smokeWatchState.PSObject.Properties["handlerStable"]) {
    $smokeWatchHandlerStable = [bool]$smokeWatchState.handlerStable
}

Add-Check `
    -Checks $checks `
    -BlockingFailures $blockingFailures `
    -FailedBuckets $failedBuckets `
    -Id "long-run-stability" `
    -Title "多轮 smoke watch 趋势稳定" `
    -Bucket "performance" `
    -Passed ($smokeWatchKnown -and $smokeWatchFresh -and $smokeWatchStatus -eq "passed" -and $smokeWatchIterations -ge 3 -and $smokeWatchFailureCount -eq 0 -and $smokeWatchHandlerStable -and $smokeWatchOpenWebP95 -le $smokeWatchOpenWebThreshold -and $smokeWatchForegroundP95 -le $smokeWatchForegroundThreshold -and (Get-JsonPropertyInt $smokeWatchState "providerSessionLeakRunCount") -eq 0 -and $smokeWatchProviderPageBlockingErrorRunCount -eq 0 -and (Get-JsonPropertyInt $smokeWatchState "secretLeakRunCount") -eq 0) `
    -Evidence "known=$smokeWatchKnown; fresh${FreshHours}h=$smokeWatchFresh; status=$smokeWatchStatus; iterations=$smokeWatchIterations; failureCount=$smokeWatchFailureCount; handlerStable=$smokeWatchHandlerStable; openWebP95Ms=$smokeWatchOpenWebP95/$smokeWatchOpenWebThreshold; foregroundP95Ms=$smokeWatchForegroundP95/$smokeWatchForegroundThreshold; providerPageBlockingErrorRunCount=$smokeWatchProviderPageBlockingErrorRunCount; smokeWatchExit=$smokeWatchExit" `
    -RequiredEvidence "最近 $FreshHours 小时内至少 3 轮 smoke watch 通过，handler 稳定，p95 不超阈值，无假 session、provider 页面阻塞错误或 secret 泄漏"

$manualReadinessKnown = $null -ne $manualReadinessState
$manualReadinessFresh = Test-Fresh $manualReadinessState $FreshHours
$manualReadinessStatus = Get-JsonPropertyString $manualReadinessState "status"
$acceptedReadiness = @("ready_for_manual_account", "partial_account_verified_continue_watch", "account_verified_run_completion_audit", "complete")
$manualReadinessFailedItems = if ($manualReadinessKnown -and $null -ne $manualReadinessState.PSObject.Properties["failedItemIds"]) {
    To-StringArray $manualReadinessState.failedItemIds
} else {
    @()
}

Add-Check `
    -Checks $checks `
    -BlockingFailures $blockingFailures `
    -FailedBuckets $failedBuckets `
    -Id "manual-readiness-window" `
    -Title "人工账号验证准备度窗口可进入" `
    -Bucket "account_challenge" `
    -Passed ($manualReadinessKnown -and $manualReadinessFresh -and $acceptedReadiness -contains $manualReadinessStatus -and $manualReadinessFailedItems.Count -eq 0) `
    -Evidence "known=$manualReadinessKnown; fresh${FreshHours}h=$manualReadinessFresh; status=$manualReadinessStatus; failedItemIds=$(Format-Items $manualReadinessFailedItems); readinessExit=$readinessExit" `
    -RequiredEvidence "manual readiness 为 ready_for_manual_account / partial / account_verified / complete，且没有失败项"

$manualStartKnown = $null -ne $manualAccountStartState
$manualStartStatus = Get-JsonPropertyString $manualAccountStartState "status"
$manualStartLaunched = if ($manualStartKnown -and $null -ne $manualAccountStartState.PSObject.Properties["launchedTargets"]) {
    To-StringArray $manualAccountStartState.launchedTargets
} else {
    @()
}
$accountWatchKnown = $null -ne $accountWatchState
$accountWatchStatus = Get-JsonPropertyString $accountWatchState "status"

Add-Check `
    -Checks $checks `
    -BlockingFailures $blockingFailures `
    -FailedBuckets $failedBuckets `
    -Id "cli-login-entry-known" `
    -Title "CLI 登录入口和 fallback 证据已知" `
    -Bucket "cli_callback_or_fallback" `
    -Passed ($manualStartKnown -and
        $manualStartStatus -in @("planned", "launched", "watch_waiting_for_real_account_authorization", "watch_verified") -and
        ($manualStartLaunched -contains "codex") -and
        ($manualStartLaunched -contains "claude") -and
        $accountWatchKnown -and
        $accountWatchStatus -in @("waiting_for_real_account_authorization", "verified")) `
    -Evidence "manualStartStatus=$manualStartStatus; launchedTargets=$(Format-Items $manualStartLaunched); accountWatchStatus=$accountWatchStatus" `
    -RequiredEvidence "Codex/Claude 真实 CLI 登录入口已拉起或可继续；watch 等待真实账号授权或已 verified；该项不读取 token"

$runnerKnown = $null -ne $runnerState
$runnerExitCode = Get-JsonPropertyInt $runnerState "exitCode"
$runnerNextAction = Get-JsonPropertyString $runnerState "nextAction"
$runnerReadyTargets = if ($runnerKnown -and $null -ne $runnerState.PSObject.Properties["readyTargets"]) {
    To-StringArray $runnerState.readyTargets
} else {
    @()
}
$runnerWaitingTargets = if ($runnerKnown -and $null -ne $runnerState.PSObject.Properties["waitingTargets"]) {
    To-StringArray $runnerState.waitingTargets
} else {
    @()
}
$runnerErrorTargets = if ($runnerKnown -and $null -ne $runnerState.PSObject.Properties["errorTargets"]) {
    To-StringArray $runnerState.errorTargets
} else {
    @()
}
$runnerVerifiedTargets = if ($runnerKnown -and $null -ne $runnerState.PSObject.Properties["verifiedTargets"]) {
    To-StringArray $runnerState.verifiedTargets
} else {
    @()
}

Add-Check `
    -Checks $checks `
    -BlockingFailures $blockingFailures `
    -FailedBuckets $failedBuckets `
    -Id "runner-account-boundary" `
    -Title "账号状态被归为等待或 ready，没有环境错误" `
    -Bucket "post_auth" `
    -Passed ($runnerKnown -and $runnerExitCode -in @(0, 2) -and $runnerErrorTargets.Count -eq 0) `
    -Evidence "exitCode=$runnerExitCode; nextAction=$runnerNextAction; readyTargets=$(Format-Items $runnerReadyTargets); waitingTargets=$(Format-Items $runnerWaitingTargets); verifiedTargets=$(Format-Items $runnerVerifiedTargets); errorTargets=$(Format-Items $runnerErrorTargets); runnerExit=$runnerExit" `
    -RequiredEvidence "runner-status.json 可读；退出码为 0 或 2；errorTargets 为空。等待账号是允许状态，不是浏览器环境失败。"

$auditKnown = $null -ne $completionAuditState
$auditFresh = Test-Fresh $completionAuditState $FreshHours
$auditStatus = Get-JsonPropertyString $completionAuditState "status"
$auditFailedItems = if ($auditKnown -and $null -ne $completionAuditState.PSObject.Properties["failedItemIds"]) {
    To-StringArray $completionAuditState.failedItemIds
} else {
    @()
}
$expectedAccountFailures = @("codex-account", "claude-account")
$unexpectedAuditFailures = @($auditFailedItems | Where-Object { $expectedAccountFailures -notcontains $_ })
$auditAccountOnly = $auditKnown -and $auditFresh -and ($auditStatus -eq "complete" -or $unexpectedAuditFailures.Count -eq 0)

Add-Check `
    -Checks $checks `
    -BlockingFailures $blockingFailures `
    -FailedBuckets $failedBuckets `
    -Id "completion-audit-account-only" `
    -Title "最终审计没有账号以外的失败项" `
    -Bucket "post_auth" `
    -Passed $auditAccountOnly `
    -Evidence "known=$auditKnown; fresh${FreshHours}h=$auditFresh; status=$auditStatus; failedItemIds=$(Format-Items $auditFailedItems); unexpected=$(Format-Items $unexpectedAuditFailures); auditExit=$auditExit" `
    -RequiredEvidence "completion audit 为 complete，或 incomplete 但失败项只剩 codex-account / claude-account"

$status = "not_ready"
$nextAction = "inspect_provider_auth_preflight_failures"
$allVerified = ($runnerVerifiedTargets -contains "codex") -and ($runnerVerifiedTargets -contains "claude")
if ($blockingFailures.Count -eq 0) {
    if ($auditStatus -eq "complete" -or $allVerified) {
        $status = "account_verified_run_completion_audit"
        $nextAction = ".\scripts\browser-login-completion-audit.ps1 -RefreshState"
    } elseif ($runnerReadyTargets.Count -gt 0) {
        $status = "account_ready_run_post_auth"
        $nextAction = ".\scripts\browser-login-continuation-runner.ps1 -Serial $Serial"
    } else {
        $status = "ready_for_manual_provider_auth"
        $nextAction = ".\scripts\browser-login-manual-account-start.ps1 -Serial $Serial -Targets codex,claude -StartWatch -RunCompletionAuditOnVerified"
    }
}

$semanticExitCode = 0
if ($status -eq "not_ready") {
    $semanticExitCode = 1
} elseif ($status -eq "ready_for_manual_provider_auth" -or $status -eq "account_ready_run_post_auth") {
    $semanticExitCode = 2
}

$state = [ordered]@{
    checkedAt = (Get-Date).ToString("o")
    serial = $Serial
    repoRoot = $RepoRoot.ToString()
    stateDir = $StateDir
    status = $status
    exitCode = $semanticExitCode
    nextAction = $nextAction
    freshHours = $FreshHours
    runnerExit = $runnerExit
    smokeExit = $smokeExit
    smokeWatchExit = $smokeWatchExit
    readinessExit = $readinessExit
    completionAuditExit = $auditExit
    blockingFailureIds = @($blockingFailures.ToArray())
    failedBuckets = @($failedBuckets.ToArray())
    readyTargets = @($runnerReadyTargets)
    waitingTargets = @($runnerWaitingTargets)
    verifiedTargets = @($runnerVerifiedTargets)
    errorTargets = @($runnerErrorTargets)
    manualReadinessStatus = $manualReadinessStatus
    completionAuditStatus = $auditStatus
    completionAuditFailedItemIds = @($auditFailedItems)
    smokeStatus = $smokeStatus
    smokeCheckedAt = Format-Timestamp (Get-JsonPropertyValue $smokeState "checkedAt")
    providerPageSignalState = $providerPageSignalState
    providerPageBlockingErrorCount = $providerPageBlockingErrorCount
    providerPageChallengeHintCount = $providerPageChallengeHintCount
    smokeWatchStatus = $smokeWatchStatus
    smokeWatchCheckedAt = Format-Timestamp (Get-JsonPropertyValue $smokeWatchState "checkedAt")
    officialSources = @($officialSources.ToArray())
    checks = @($checks.ToArray())
    statePath = $preflightJsonPath
    reportPath = $preflightReportPath
}

$state | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $preflightJsonPath -Encoding UTF8

$lines = New-Object System.Collections.Generic.List[string]
$lines.Add("# Provider Auth Preflight")
$lines.Add("")
$lines.Add("- checkedAt：$($state.checkedAt)")
$lines.Add("- status：$status")
$lines.Add("- exitCode：$semanticExitCode")
$lines.Add("- nextAction：$nextAction")
$lines.Add("- blockingFailureIds：$(Format-Items $state.blockingFailureIds)")
$lines.Add("- failedBuckets：$(Format-Items $state.failedBuckets)")
$lines.Add("- waitingTargets：$(Format-Items $state.waitingTargets)")
$lines.Add("- readyTargets：$(Format-Items $state.readyTargets)")
$lines.Add("- verifiedTargets：$(Format-Items $state.verifiedTargets)")
$lines.Add("")
$lines.Add("## Checks")
$lines.Add("")
$lines.Add("| id | status | bucket | evidence |")
$lines.Add("| --- | --- | --- | --- |")
foreach ($check in $checks) {
    $checkStatus = if ([bool]$check.passed) { "PASS" } else { "MISS" }
    $lines.Add("| $($check.id) | $checkStatus | $($check.bucket) | $(Format-MarkdownCell $check.evidence) |")
}
$lines.Add("")
$lines.Add("## Official Boundary")
$lines.Add("")
$lines.Add("这些来源只用于确认合规方向，不作为账号已登录证据。账号完成仍必须由 Codex / Claude 官方状态命令和 completion audit 证明。")
$lines.Add("")
foreach ($source in $officialSources) {
    $lines.Add("- $($source.id)：$($source.url)")
}
$lines.Add("")
$lines.Add("## Output Files")
$lines.Add("")
$lines.Add("- json：$preflightJsonPath")
$lines.Add("- report：$preflightReportPath")

$lines | Set-Content -LiteralPath $preflightReportPath -Encoding UTF8

Write-Output "checkedAt=$($state.checkedAt)"
Write-Output "status=$status"
Write-Output "exitCode=$semanticExitCode"
Write-Output "blockingFailureIds=$(Format-Items $state.blockingFailureIds)"
Write-Output "failedBuckets=$(Format-Items $state.failedBuckets)"
Write-Output "waitingTargets=$(Format-Items $state.waitingTargets)"
Write-Output "readyTargets=$(Format-Items $state.readyTargets)"
Write-Output "verifiedTargets=$(Format-Items $state.verifiedTargets)"
Write-Output "nextAction=$nextAction"

exit $semanticExitCode
