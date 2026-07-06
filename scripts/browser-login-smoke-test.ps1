param(
    [string]$Serial = "3f8bbaad",
    [string]$StateDir = "",
    [int]$HostPort = 18791,
    [int]$DevicePort = 8791,
    [int]$OpenWebResponsiveThresholdMs = 1500,
    [int]$ForegroundResponsiveThresholdMs = 5000,
    [int]$AuthHostProbeAttempts = 2,
    [int]$AuthHostProbeRetryDelaySeconds = 2,
    [switch]$LeaveBrowserOpen
)

$ErrorActionPreference = "Stop"

if ($AuthHostProbeAttempts -lt 1) {
    throw "AuthHostProbeAttempts must be at least 1"
}
if ($AuthHostProbeRetryDelaySeconds -lt 0) {
    throw "AuthHostProbeRetryDelaySeconds must be 0 or greater"
}

if ([string]::IsNullOrWhiteSpace($StateDir)) {
    $StateDir = Join-Path $env:LOCALAPPDATA "Kite\browser-login-continuation"
}

New-Item -ItemType Directory -Force -Path $StateDir | Out-Null

$jsonPath = Join-Path $StateDir "browser-login-smoke.json"
$reportPath = Join-Path $StateDir "browser-login-smoke.md"
$screenshotPath = Join-Path $StateDir "browser-login-smoke.png"
$uiDumpPath = Join-Path $StateDir "browser-login-smoke-ui.xml"
$remoteScreenshotPath = "/sdcard/Download/kite-browser-login-smoke.png"
$remoteUiDumpPath = "/sdcard/Download/kite-browser-login-smoke-ui.xml"

$items = New-Object System.Collections.Generic.List[object]

function Add-SmokeItem {
    param(
        [string]$Id,
        [string]$Title,
        [bool]$Passed,
        [string]$Evidence,
        [string]$RequiredEvidence
    )

    $script:items.Add([ordered]@{
        id = $Id
        title = $Title
        passed = $Passed
        evidence = $Evidence
        requiredEvidence = $RequiredEvidence
    })
}

function Invoke-Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)

    $output = & adb @Arguments 2>&1
    return [ordered]@{
        exitCode = $LASTEXITCODE
        output = ($output | ForEach-Object { $_.ToString() }) -join "`n"
    }
}

function Limit-Text {
    param(
        [string]$Text,
        [int]$MaxLength = 1000
    )

    if ([string]::IsNullOrEmpty($Text) -or $Text.Length -le $MaxLength) {
        return $Text
    }
    return $Text.Substring(0, $MaxLength)
}

function Get-ForegroundWindow {
    $result = Invoke-Adb @("-s", $Serial, "shell", "dumpsys window")
    $text = $result.output
    $line = @(
        $text -split "`n" |
            Where-Object { $_ -match "mCurrentFocus=.*\/|mFocusedApp=.*\/|topResumedActivity=.*\/" }
    ) | Select-Object -First 1
    $package = ""
    $activity = ""
    if ($line -match "\s([A-Za-z0-9_.]+)\/([A-Za-z0-9_.$]+)") {
        $package = $Matches[1]
        $activity = $Matches[2]
    }
    return [ordered]@{
        exitCode = $result.exitCode
        line = $line.ToString().Trim()
        package = $package
        activity = $activity
    }
}

function Wait-LocalServer {
    param([int]$Seconds = 20)

    $deadline = (Get-Date).AddSeconds($Seconds)
    $lastError = ""
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-RestMethod -Method Get -Uri "http://127.0.0.1:$HostPort/status" -TimeoutSec 3
            if ($response.ok -eq $true -and $response.server -eq "running") {
                return [ordered]@{
                    ok = $true
                    evidence = "app=$($response.app); version=$($response.version); server=$($response.server)"
                }
            }
        } catch {
            $lastError = $_.Exception.Message
        }
        Start-Sleep -Milliseconds 700
    }
    return [ordered]@{
        ok = $false
        evidence = "timeout waiting for /status; lastError=$lastError"
    }
}

function Read-BrowserAuthSessions {
    $prefs = Read-BrowserAuthSessionPrefs
    return @($prefs.sessions)
}

function Read-BrowserAuthSessionPrefs {
    $result = Invoke-Adb @("-s", $Serial, "exec-out", "run-as", "com.kite.app", "cat", "shared_prefs/kite_browser_auth_sessions.xml")
    if ($result.exitCode -ne 0 -or [string]::IsNullOrWhiteSpace($result.output)) {
        return [ordered]@{
            exitCode = $result.exitCode
            rawXml = $result.output
            decodedJson = ""
            sessions = @()
        }
    }
    $match = [regex]::Match($result.output, '<string name="sessions_v1">(.*?)</string>', [System.Text.RegularExpressions.RegexOptions]::Singleline)
    if (-not $match.Success) {
        return [ordered]@{
            exitCode = $result.exitCode
            rawXml = $result.output
            decodedJson = ""
            sessions = @()
        }
    }
    $json = [System.Net.WebUtility]::HtmlDecode($match.Groups[1].Value)
    if ([string]::IsNullOrWhiteSpace($json)) {
        return [ordered]@{
            exitCode = $result.exitCode
            rawXml = $result.output
            decodedJson = ""
            sessions = @()
        }
    }
    return [ordered]@{
        exitCode = $result.exitCode
        rawXml = $result.output
        decodedJson = $json
        sessions = @($json | ConvertFrom-Json)
    }
}

function Find-AppPrivateRawValueHits {
    param([string[]]$Needles)

    $safeNeedles = @(
        $Needles |
            Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
    )
    $listResult = Invoke-Adb @("-s", $Serial, "shell", "run-as", "com.kite.app", "find", "files", "shared_prefs", "-maxdepth", "5", "-type", "f")
    $paths = @()
    if ($listResult.exitCode -eq 0 -and -not [string]::IsNullOrWhiteSpace($listResult.output)) {
        $paths = @(
            $listResult.output -split "`n" |
                ForEach-Object { $_.Trim() } |
                Where-Object { $_ -match "\.(xml|json|jsonl|log|txt|properties)$" }
        )
    }

    $hitPaths = New-Object System.Collections.Generic.List[string]
    $grepErrors = New-Object System.Collections.Generic.List[string]
    if ($safeNeedles.Count -gt 0 -and $paths.Count -gt 0) {
        $batchSize = 24
        for ($index = 0; $index -lt $paths.Count; $index += $batchSize) {
            $last = [Math]::Min($index + $batchSize - 1, $paths.Count - 1)
            $batch = @($paths[$index..$last])
            $grepArgs = @("-s", $Serial, "shell", "run-as", "com.kite.app", "grep", "-I", "-l", "-F")
            foreach ($needle in $safeNeedles) {
                $grepArgs += @("-e", $needle)
            }
            $grepArgs += $batch
            $grepResult = Invoke-Adb @grepArgs
            if ($grepResult.exitCode -eq 0 -and -not [string]::IsNullOrWhiteSpace($grepResult.output)) {
                foreach ($line in @($grepResult.output -split "`n")) {
                    $path = $line.Trim()
                    if (-not [string]::IsNullOrWhiteSpace($path)) {
                        $hitPaths.Add($path)
                    }
                }
            } elseif ($grepResult.exitCode -ne 1) {
                $grepErrors.Add("exit=$($grepResult.exitCode); batchStart=$index; fileCount=$($batch.Count)")
            }
        }
    }

    $uniqueHitPaths = @($hitPaths.ToArray() | Sort-Object -Unique)
    return [ordered]@{
        listExitCode = $listResult.exitCode
        scannedFileCount = $paths.Count
        hitCount = $uniqueHitPaths.Count
        hitPaths = @($uniqueHitPaths)
        grepErrorCount = $grepErrors.Count
        grepErrors = @($grepErrors.ToArray())
    }
}

function Test-DeviceHttpsReachability {
    param([string[]]$Hosts)

    $results = New-Object System.Collections.Generic.List[object]
    foreach ($hostName in $Hosts) {
        $url = "https://$hostName/"
        $attempts = New-Object System.Collections.Generic.List[object]
        $bestResult = $null
        for ($attempt = 1; $attempt -le $AuthHostProbeAttempts; $attempt++) {
            $result = Invoke-Adb @(
                "-s", $Serial,
                "shell",
                "curl", "-L",
                "--connect-timeout", "5",
                "--max-time", "12",
                "-o", "/dev/null",
                "-sS",
                "-w", "%{http_code}",
                $url
            )
            $httpCode = 0
            $matches = [regex]::Matches($result.output, "\b(\d{3})\b")
            if ($matches.Count -gt 0) {
                $httpCode = [int]$matches[$matches.Count - 1].Groups[1].Value
            }
            $ok = $result.exitCode -eq 0 -and $httpCode -ge 200 -and $httpCode -lt 500
            $attemptResult = [pscustomobject][ordered]@{
                attempt = $attempt
                exitCode = $result.exitCode
                httpCode = $httpCode
                ok = $ok
                output = Limit-Text ($result.output.Trim()) 300
            }
            $attempts.Add($attemptResult)
            if ($null -eq $bestResult -or $ok) {
                $bestResult = $attemptResult
            }
            if ($ok) {
                break
            }
            if ($attempt -lt $AuthHostProbeAttempts -and $AuthHostProbeRetryDelaySeconds -gt 0) {
                Start-Sleep -Seconds $AuthHostProbeRetryDelaySeconds
            }
        }
        $results.Add([pscustomobject][ordered]@{
            host = $hostName
            exitCode = $bestResult.exitCode
            httpCode = $bestResult.httpCode
            ok = $bestResult.ok
            attemptCount = $attempts.Count
            attempts = @($attempts.ToArray())
            output = $bestResult.output
        })
    }
    return @($results.ToArray())
}

function Get-DefaultHttpsBrowserHandler {
    $result = Invoke-Adb @(
        "-s", $Serial,
        "shell",
        "cmd", "package", "resolve-activity",
        "-a", "android.intent.action.VIEW",
        "-d", "https://accounts.google.com/"
    )

    $package = ""
    $activity = ""
    $exported = ""
    $packageMatch = [regex]::Match($result.output, "(?m)^\s*packageName=([A-Za-z0-9_.]+)\s*$")
    $activityMatch = [regex]::Match($result.output, "(?m)^\s*name=([A-Za-z0-9_.$]+)\s*$")
    $exportedMatch = [regex]::Match($result.output, "\bexported=(true|false)\b")
    if ($packageMatch.Success) {
        $package = $packageMatch.Groups[1].Value
    }
    if ($activityMatch.Success) {
        $activity = $activityMatch.Groups[1].Value
    }
    if ($exportedMatch.Success) {
        $exported = $exportedMatch.Groups[1].Value
    }

    return [ordered]@{
        exitCode = $result.exitCode
        package = $package
        activity = $activity
        exported = $exported
        output = Limit-Text ($result.output.Trim()) 800
    }
}

function Get-CustomTabsServices {
    $result = Invoke-Adb @(
        "-s", $Serial,
        "shell",
        "cmd", "package", "query-services",
        "-a", "android.support.customtabs.action.CustomTabsService"
    )

    $packageMatches = [regex]::Matches($result.output, "(?m)^\s*packageName=([A-Za-z0-9_.]+)\s*$")
    $packages = @(
        $packageMatches |
            ForEach-Object { $_.Groups[1].Value } |
            Sort-Object -Unique
    )
    $serviceInfoCount = [regex]::Matches($result.output, "(?m)^\s*ServiceInfo:").Count
    $serviceCount = [Math]::Max($serviceInfoCount, $packages.Count)
    if ($result.output -match "No services found") {
        $serviceCount = 0
        $packages = @()
    }

    return [ordered]@{
        exitCode = $result.exitCode
        serviceCount = $serviceCount
        packages = @($packages)
        output = Limit-Text ($result.output.Trim()) 800
    }
}

function Find-NewBrowserAuthSession {
    param(
        [object[]]$Sessions,
        [string[]]$KnownIds,
        [string]$Kind,
        [string]$RedirectUri
    )

    return @(
        $Sessions |
            Where-Object {
                $KnownIds -notcontains $_.sessionId -and
                $_.kind -eq $Kind -and
                $_.redirectUri -eq $RedirectUri
            } |
            Sort-Object -Property createdAt -Descending
    ) | Select-Object -First 1
}

function Wait-NewBrowserAuthSession {
    param(
        [string[]]$KnownIds,
        [string]$Kind,
        [string]$RedirectUri,
        [int]$Seconds = 15
    )

    $deadline = (Get-Date).AddSeconds($Seconds)
    $lastPrefs = Read-BrowserAuthSessionPrefs
    while ((Get-Date) -lt $deadline) {
        $session = Find-NewBrowserAuthSession -Sessions @($lastPrefs.sessions) -KnownIds $KnownIds -Kind $Kind -RedirectUri $RedirectUri
        if ($null -ne $session) {
            return [ordered]@{
                session = $session
                prefs = $lastPrefs
            }
        }
        Start-Sleep -Milliseconds 700
        $lastPrefs = Read-BrowserAuthSessionPrefs
    }
    return [ordered]@{
        session = $null
        prefs = $lastPrefs
    }
}

function Wait-BrowserAuthSessionStatus {
    param(
        [string]$SessionId,
        [string[]]$Statuses,
        [int]$Seconds = 15
    )

    $deadline = (Get-Date).AddSeconds($Seconds)
    $lastPrefs = Read-BrowserAuthSessionPrefs
    while ((Get-Date) -lt $deadline) {
        $session = @($lastPrefs.sessions | Where-Object { $_.sessionId -eq $SessionId }) | Select-Object -First 1
        if ($null -ne $session -and $Statuses -contains $session.status) {
            return [ordered]@{
                session = $session
                prefs = $lastPrefs
            }
        }
        Start-Sleep -Milliseconds 700
        $lastPrefs = Read-BrowserAuthSessionPrefs
    }
    $lastSession = @($lastPrefs.sessions | Where-Object { $_.sessionId -eq $SessionId }) | Select-Object -First 1
    return [ordered]@{
        session = $lastSession
        prefs = $lastPrefs
    }
}

function Format-Status {
    param([bool]$Passed)

    if ($Passed) {
        return "PASS"
    }
    return "MISS"
}

function Get-UniqueRegexMatches {
    param(
        [string]$Text,
        [string]$Pattern
    )

    if ([string]::IsNullOrWhiteSpace($Text)) {
        return @()
    }
    return @(
        [regex]::Matches(
            $Text,
            $Pattern,
            [System.Text.RegularExpressions.RegexOptions]::IgnoreCase
        ) |
            ForEach-Object { $_.Value } |
            Sort-Object -Unique
    )
}

function Get-ProviderPageSignals {
    param([string]$UiText)

    $blockingPattern = "disallowed_useragent|redirect_uri_mismatch|invalid_client|invalid_request|unauthorized_client|unsupported_response_type|unsupported_grant_type|unsupported_browser|access_denied|Error 400|Error 403|错误\s*400|错误\s*403|禁止访问|不符合\s*Google|不符合相关政策|应用被阻止|This app is blocked"
    $challengePattern = "Sign in|登录|Email or phone|电子邮件|手机号|使用您的 Google 帐号|Continue|继续|Create account|创建帐号|Paste code|login code|验证码|MFA|Two-Step|两步验证"
    $blockingMatches = Get-UniqueRegexMatches -Text $UiText -Pattern $blockingPattern
    $challengeMatches = Get-UniqueRegexMatches -Text $UiText -Pattern $challengePattern
    $state = if ($blockingMatches.Count -gt 0) {
        "blocking_error"
    } elseif ($challengeMatches.Count -gt 0) {
        "challenge_or_login_visible"
    } else {
        "external_page_no_blocking_error"
    }

    return [ordered]@{
        state = $state
        blockingErrorCount = $blockingMatches.Count
        blockingErrorMatches = @($blockingMatches)
        challengeHintCount = $challengeMatches.Count
        challengeHintMatches = @($challengeMatches)
    }
}

function Redacted-GoogleOAuthUrl {
    return "https://accounts.google.com/o/oauth2/v2/auth?response_type=present&client_id=present&redirect_uri=https&scope=present&state=present&prompt=present"
}

function Redacted-KiteAppOAuthUrl {
    return "https://accounts.google.com/o/oauth2/v2/auth?response_type=present&client_id=present&redirect_uri=kite_app&scope=present&state=present&prompt=present"
}

function Redacted-KiteCallbackUrl {
    return "kite-auth://callback?code=present&access_token=present&state=present"
}

function Local-WebViewSmokeUrl {
    return "http://127.0.0.1:$DevicePort/status"
}

function UrlEncode {
    param([string]$Value)

    return [System.Uri]::EscapeDataString($Value)
}

function Invoke-OpenWeb {
    param(
        [string]$Url,
        [string]$Source = "browser_proxy"
    )

    $elapsed = [System.Diagnostics.Stopwatch]::StartNew()
    $response = $null
    $errorText = ""
    try {
        $body = @{
            url = $Url
            source = $Source
        } | ConvertTo-Json -Depth 4
        $response = Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:$HostPort/open-web" -Body $body -ContentType "application/json; charset=utf-8" -TimeoutSec 10
    } catch {
        $errorText = $_.Exception.Message
    }
    $elapsed.Stop()

    $accepted = $null -ne $response -and $response.ok -eq $true -and $response.accepted -eq $true
    return [ordered]@{
        accepted = $accepted
        elapsedMs = $elapsed.ElapsedMilliseconds
        response = $response
        error = $errorText
    }
}

function Wait-ExternalForeground {
    param(
        [string]$ExpectedPackage = "",
        [int]$Seconds = 12
    )

    $elapsed = [System.Diagnostics.Stopwatch]::StartNew()
    $deadline = (Get-Date).AddSeconds($Seconds)
    $lastForeground = Get-ForegroundWindow
    while ((Get-Date) -lt $deadline) {
        $lastForeground = Get-ForegroundWindow
        $isExternal = -not [string]::IsNullOrWhiteSpace($lastForeground.package) -and
            $lastForeground.package -ne "com.kite.app"
        $matchesExpected = [string]::IsNullOrWhiteSpace($ExpectedPackage) -or
            $lastForeground.package -eq $ExpectedPackage
        if ($isExternal -and $matchesExpected) {
            $elapsed.Stop()
            $lastForeground["elapsedMs"] = $elapsed.ElapsedMilliseconds
            return $lastForeground
        }
        Start-Sleep -Milliseconds 700
    }
    $elapsed.Stop()
    $lastForeground["elapsedMs"] = $elapsed.ElapsedMilliseconds
    return $lastForeground
}

function Invoke-ProviderOAuthHandoffCheck {
    param(
        [string]$Id,
        [string]$Title,
        [string]$Url,
        [string]$RedactedUrl,
        [string]$ExpectedPackage
    )

    Invoke-Adb @("-s", $Serial, "shell", "am", "start", "-n", "com.kite.app/com.kite.app.MainActivity") | Out-Null
    Start-Sleep -Milliseconds 800
    $beforeForeground = Get-ForegroundWindow
    $openResult = Invoke-OpenWeb -Url $Url -Source "browser_proxy"
    $foregroundResult = if ($openResult.accepted) {
        Wait-ExternalForeground -ExpectedPackage $ExpectedPackage -Seconds 12
    } else {
        Get-ForegroundWindow
    }
    $foregroundWaitMs = if ($null -ne $foregroundResult.elapsedMs) { [int64]$foregroundResult.elapsedMs } else { -1 }
    $handoffForegroundElapsedMs = if ($openResult.accepted -and $foregroundWaitMs -ge 0) {
        [int64]$openResult.elapsedMs + $foregroundWaitMs
    } else {
        -1
    }
    $isExternal = -not [string]::IsNullOrWhiteSpace($foregroundResult.package) -and
        $foregroundResult.package -ne "com.kite.app"
    $matchesHandler = [string]::IsNullOrWhiteSpace($ExpectedPackage) -or
        $foregroundResult.package -eq $ExpectedPackage
    $passed = $openResult.accepted -and $isExternal -and $matchesHandler

    Add-SmokeItem `
        -Id "provider-oauth-$Id-external-browser" `
        -Title "$Title OAuth 形态 URL 进入外部浏览器" `
        -Passed $passed `
        -Evidence "accepted=$($openResult.accepted); openWebElapsedMs=$($openResult.elapsedMs); foregroundWaitMs=$foregroundWaitMs; handoffForegroundElapsedMs=$handoffForegroundElapsedMs; before=$($beforeForeground.package)/$($beforeForeground.activity); foreground=$($foregroundResult.package)/$($foregroundResult.activity); expectedPackage=$ExpectedPackage; url=$RedactedUrl; error=$($openResult.error)" `
        -RequiredEvidence "$Title 相关 OAuth 形态 URL 经 /open-web 接收后，从 Kite 前台切到外部浏览器 handler；不输入账号，不证明 provider 授权成功"

    return [pscustomobject][ordered]@{
        id = $Id
        title = $Title
        accepted = [bool]$openResult.accepted
        elapsedMs = $openResult.elapsedMs
        foregroundWaitMs = $foregroundWaitMs
        handoffForegroundElapsedMs = $handoffForegroundElapsedMs
        foregroundPackage = $foregroundResult.package
        foregroundActivity = $foregroundResult.activity
        expectedPackage = $ExpectedPackage
        externalBrowser = $isExternal
        matchesHandler = $matchesHandler
        passed = $passed
        redactedUrl = $RedactedUrl
    }
}

$smokeSchemaVersion = 10
$checkedAt = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ssK")
$testState = "kite-smoke-" + (Get-Date -Format "yyyyMMddHHmmssfff")
$localWebUrl = Local-WebViewSmokeUrl
$googleUrl = "https://accounts.google.com/o/oauth2/v2/auth?response_type=code&client_id=407408718192.apps.googleusercontent.com&redirect_uri=https%3A%2F%2Fdevelopers.google.com%2Foauthplayground&scope=openid%20email&state=$testState&prompt=consent"
$appRedirectState = "kite-app-smoke-" + (Get-Date -Format "yyyyMMddHHmmssfff")
$openAiOAuthState = "kite-openai-smoke-" + (Get-Date -Format "yyyyMMddHHmmssfff")
$claudeOAuthState = "kite-claude-smoke-" + (Get-Date -Format "yyyyMMddHHmmssfff")
$appRedirectSecretCode = "smoke-code-" + [Guid]::NewGuid().ToString("N")
$appRedirectSecretToken = "smoke-token-" + [Guid]::NewGuid().ToString("N")
$appRedirectUrl = "https://accounts.google.com/o/oauth2/v2/auth?response_type=code&client_id=kite-smoke-client.apps.googleusercontent.com&redirect_uri=kite-auth%3A%2F%2Fcallback&scope=openid%20email&state=$(UrlEncode $appRedirectState)&prompt=consent"
$openAiOAuthUrl = "https://auth.openai.com/oauth/authorize?response_type=code&client_id=kite-smoke-openai&redirect_uri=https%3A%2F%2Fexample.com%2Fcallback&scope=openid%20email&state=$(UrlEncode $openAiOAuthState)"
$claudeOAuthUrl = "https://claude.ai/login?response_type=code&client_id=kite-smoke-claude&redirect_uri=https%3A%2F%2Fexample.com%2Fcallback&scope=openid&state=$(UrlEncode $claudeOAuthState)"
$openAiOAuthUrlRedacted = "https://auth.openai.com/oauth/authorize?response_type=present&client_id=present&redirect_uri=https&scope=present&state=present"
$claudeOAuthUrlRedacted = "https://claude.ai/login?response_type=present&client_id=present&redirect_uri=https&scope=present&state=present"

$deviceList = (& adb devices -l 2>&1 | ForEach-Object { $_.ToString() }) -join "`n"
$deviceLine = @(
    $deviceList -split "`n" |
        Where-Object { $_ -match "^\s*$([regex]::Escape($Serial))\s+device\b" }
) | Select-Object -First 1
Add-SmokeItem `
    -Id "device-online" `
    -Title "OnePlus 8T 在线" `
    -Passed (-not [string]::IsNullOrWhiteSpace($deviceLine)) `
    -Evidence $deviceLine `
    -RequiredEvidence "adb devices -l 中 $Serial 为 device"

$forwardResult = Invoke-Adb @("-s", $Serial, "forward", "tcp:$HostPort", "tcp:$DevicePort")
Add-SmokeItem `
    -Id "adb-forward" `
    -Title "浏览器线端口转发已恢复" `
    -Passed ($forwardResult.exitCode -eq 0) `
    -Evidence "exit=$($forwardResult.exitCode); tcp:$HostPort -> tcp:$DevicePort" `
    -RequiredEvidence "adb -s $Serial forward tcp:$HostPort tcp:$DevicePort 退出 0"

$startResult = Invoke-Adb @("-s", $Serial, "shell", "am", "start", "-n", "com.kite.app/com.kite.app.MainActivity")
Add-SmokeItem `
    -Id "kite-started" `
    -Title "Kite 已启动到前台" `
    -Passed ($startResult.exitCode -eq 0) `
    -Evidence "exit=$($startResult.exitCode); output=$($startResult.output.Trim())" `
    -RequiredEvidence "am start com.kite.app/.MainActivity 退出 0"

$server = Wait-LocalServer -Seconds 25
Add-SmokeItem `
    -Id "local-server" `
    -Title "Kite 本地 server 可用" `
    -Passed ([bool]$server.ok) `
    -Evidence $server.evidence `
    -RequiredEvidence "http://127.0.0.1:$HostPort/status 返回 ok=true 且 server=running"

$authHostNetworkResults = Test-DeviceHttpsReachability -Hosts @(
    "accounts.google.com",
    "auth.openai.com",
    "claude.ai"
)
$failedAuthHostNetworkResults = @($authHostNetworkResults | Where-Object { -not $_.ok })
$authHostNetworkEvidence = (@(
    $authHostNetworkResults |
        ForEach-Object { "$($_.host):exit=$($_.exitCode);http=$($_.httpCode);ok=$($_.ok);attempts=$($_.attemptCount)" }
) -join "; ")
Add-SmokeItem `
    -Id "auth-hosts-network-reachable" `
    -Title "账号授权主机设备侧 HTTPS 可达" `
    -Passed ($failedAuthHostNetworkResults.Count -eq 0 -and $authHostNetworkResults.Count -eq 3) `
    -Evidence $authHostNetworkEvidence `
    -RequiredEvidence "OnePlus 8T 设备侧 curl 能通过 HTTPS 到达 accounts.google.com、auth.openai.com、claude.ai；每个 host 允许少量重试并记录 attempts；HTTP 2xx-4xx 均视为 DNS/TLS/网络路径可达，不代表账号授权成功"

$httpsBrowserHandler = Get-DefaultHttpsBrowserHandler
$customTabsServices = Get-CustomTabsServices
$httpsBrowserHandlerOk = $httpsBrowserHandler.exitCode -eq 0 -and
    -not [string]::IsNullOrWhiteSpace($httpsBrowserHandler.package) -and
    -not [string]::IsNullOrWhiteSpace($httpsBrowserHandler.activity) -and
    $httpsBrowserHandler.package -ne "com.kite.app"
$customTabsPackagesText = (@($customTabsServices.packages) -join ",")
Add-SmokeItem `
    -Id "external-browser-handler-resolved" `
    -Title "HTTPS 授权 URL 可解析到外部浏览器" `
    -Passed $httpsBrowserHandlerOk `
    -Evidence "resolveExit=$($httpsBrowserHandler.exitCode); package=$($httpsBrowserHandler.package); activity=$($httpsBrowserHandler.activity); exported=$($httpsBrowserHandler.exported); customTabsServiceCount=$($customTabsServices.serviceCount); customTabsPackages=$customTabsPackagesText" `
    -RequiredEvidence "cmd package resolve-activity 能把 https://accounts.google.com/ 解析到 com.kite.app 之外的浏览器 Activity；Custom Tabs service 数量只作能力诊断，数量为 0 时允许走 ACTION_VIEW 系统浏览器 fallback"

$beforeSessions = @(Read-BrowserAuthSessions)
$beforeSessionIds = @($beforeSessions | ForEach-Object { $_.sessionId })

$localBeforeSessions = @(Read-BrowserAuthSessions)
$localBeforeSessionIds = @($localBeforeSessions | ForEach-Object { $_.sessionId })
$localOpenElapsed = [System.Diagnostics.Stopwatch]::StartNew()
$localOpenResponse = $null
$localOpenError = ""
try {
    $body = @{
        url = $localWebUrl
        source = "browser_proxy"
    } | ConvertTo-Json -Depth 4
    $localOpenResponse = Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:$HostPort/open-web" -Body $body -ContentType "application/json; charset=utf-8" -TimeoutSec 10
} catch {
    $localOpenError = $_.Exception.Message
}
$localOpenElapsed.Stop()
$localOpenAccepted = $null -ne $localOpenResponse -and $localOpenResponse.ok -eq $true -and $localOpenResponse.accepted -eq $true
Add-SmokeItem `
    -Id "local-web-open-accepted" `
    -Title "普通 localhost Web UI 已被 /open-web 接收" `
    -Passed $localOpenAccepted `
    -Evidence "accepted=$localOpenAccepted; elapsedMs=$($localOpenElapsed.ElapsedMilliseconds); url=$localWebUrl; error=$localOpenError" `
    -RequiredEvidence "/open-web 返回 ok=true、accepted=true；这是普通 localhost 页面，不是 OAuth 授权请求"

$localForeground = $null
if ($localOpenAccepted) {
    Start-Sleep -Seconds 2
    $deadline = (Get-Date).AddSeconds(10)
    while ((Get-Date) -lt $deadline) {
        $localForeground = Get-ForegroundWindow
        if ($localForeground.package -eq "com.kite.app") {
            break
        }
        Start-Sleep -Milliseconds 700
    }
}
if ($null -eq $localForeground) {
    $localForeground = Get-ForegroundWindow
}
$localStayedInKite = $localForeground.package -eq "com.kite.app"
Add-SmokeItem `
    -Id "local-webview-stays-in-kite" `
    -Title "普通 localhost Web UI 留在 Kite WebView" `
    -Passed $localStayedInKite `
    -Evidence "package=$($localForeground.package); activity=$($localForeground.activity); line=$($localForeground.line)" `
    -RequiredEvidence "普通 http://127.0.0.1 页面前台仍为 com.kite.app，不能被 OAuth handoff 分流到系统浏览器"

$localAfterSessions = @(Read-BrowserAuthSessions)
$newLocalSessions = @(
    $localAfterSessions |
        Where-Object { $localBeforeSessionIds -notcontains $_.sessionId }
)
Add-SmokeItem `
    -Id "local-webview-no-auth-session" `
    -Title "普通 localhost Web UI 不创建 browser auth session" `
    -Passed ($newLocalSessions.Count -eq 0) `
    -Evidence "beforeCount=$($localBeforeSessions.Count); afterCount=$($localAfterSessions.Count); newSessionIds=$((@($newLocalSessions | ForEach-Object { $_.sessionId }) -join ','))" `
    -RequiredEvidence "普通 localhost 页面不新增 AppRedirect、CliLoopback 或 ExternalOnly browser auth session"

$openElapsed = [System.Diagnostics.Stopwatch]::StartNew()
$openResponse = $null
$openError = ""
try {
    $body = @{
        url = $googleUrl
        source = "browser_proxy"
    } | ConvertTo-Json -Depth 4
    $openResponse = Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:$HostPort/open-web" -Body $body -ContentType "application/json; charset=utf-8" -TimeoutSec 10
} catch {
    $openError = $_.Exception.Message
}
$openElapsed.Stop()
$openAccepted = $null -ne $openResponse -and $openResponse.ok -eq $true -and $openResponse.accepted -eq $true
Add-SmokeItem `
    -Id "google-open-web-accepted" `
    -Title "Google OAuth URL 已被 /open-web 接收" `
    -Passed $openAccepted `
    -Evidence "accepted=$openAccepted; elapsedMs=$($openElapsed.ElapsedMilliseconds); url=$(Redacted-GoogleOAuthUrl); error=$openError" `
    -RequiredEvidence "/open-web 返回 ok=true、accepted=true，且不保存原始 state 到报告"

$foreground = $null
$foregroundWaitMs = -1
if ($openAccepted) {
    $foreground = Wait-ExternalForeground -ExpectedPackage $httpsBrowserHandler.package -Seconds 15
    if ($null -ne $foreground.elapsedMs) {
        $foregroundWaitMs = [int64]$foreground.elapsedMs
    }
}
if ($null -eq $foreground) {
    $foreground = Get-ForegroundWindow
}
$foregroundHandoffElapsedMs = if ($openAccepted -and $foregroundWaitMs -ge 0) {
    [int64]$openElapsed.ElapsedMilliseconds + $foregroundWaitMs
} else {
    -1
}
$externalBrowser = -not [string]::IsNullOrWhiteSpace($foreground.package) -and $foreground.package -ne "com.kite.app"
Add-SmokeItem `
    -Id "foreground-external-browser" `
    -Title "授权页离开 Kite WebView" `
    -Passed $externalBrowser `
    -Evidence "package=$($foreground.package); activity=$($foreground.activity); foregroundWaitMs=$foregroundWaitMs; handoffForegroundElapsedMs=$foregroundHandoffElapsedMs; line=$($foreground.line)" `
    -RequiredEvidence "前台 Activity 不是 com.kite.app，证明 Google OAuth 不再由 Kite WebView 承载"

$observedResolvedHandler = $externalBrowser -and
    $httpsBrowserHandlerOk -and
    $foreground.package -eq $httpsBrowserHandler.package
Add-SmokeItem `
    -Id "external-browser-handler-observed" `
    -Title "实测 handoff 前台匹配外部浏览器 handler" `
    -Passed $observedResolvedHandler `
    -Evidence "resolvedPackage=$($httpsBrowserHandler.package); resolvedActivity=$($httpsBrowserHandler.activity); foregroundPackage=$($foreground.package); foregroundActivity=$($foreground.activity); customTabsServiceCount=$($customTabsServices.serviceCount)" `
    -RequiredEvidence "Google OAuth handoff 后前台包名离开 Kite，并与 HTTPS ACTION_VIEW 默认浏览器 handler 包名一致；若 Custom Tabs service 不可用，该项证明系统浏览器 fallback 实际生效"

if ($externalBrowser) {
    Start-Sleep -Seconds 5
}

$screenshotResult = Invoke-Adb @("-s", $Serial, "shell", "screencap", "-p", $remoteScreenshotPath)
$pullScreenshotResult = Invoke-Adb @("-s", $Serial, "pull", $remoteScreenshotPath, $screenshotPath)
$screenshotOk = $screenshotResult.exitCode -eq 0 -and $pullScreenshotResult.exitCode -eq 0 -and (Test-Path -LiteralPath $screenshotPath)
Add-SmokeItem `
    -Id "screenshot" `
    -Title "授权页截图已保存" `
    -Passed $screenshotOk `
    -Evidence "screencapExit=$($screenshotResult.exitCode); pullExit=$($pullScreenshotResult.exitCode); path=$screenshotPath" `
    -RequiredEvidence "保存当前前台页面截图，供人工复查账号挑战或错误页面"

$uiDumpResult = Invoke-Adb @("-s", $Serial, "shell", "uiautomator", "dump", $remoteUiDumpPath)
$pullUiDumpResult = Invoke-Adb @("-s", $Serial, "pull", $remoteUiDumpPath, $uiDumpPath)
$uiText = if (Test-Path -LiteralPath $uiDumpPath) { Get-Content -Raw -LiteralPath $uiDumpPath } else { "" }
$hasDisallowedUserAgent = $uiText -match "disallowed_useragent|不符合 Google|禁止访问|不符合相关政策"
$uiDumpOk = $uiDumpResult.exitCode -eq 0 -and $pullUiDumpResult.exitCode -eq 0 -and -not $hasDisallowedUserAgent
Add-SmokeItem `
    -Id "ui-no-disallowed-useragent" `
    -Title "UI dump 未出现 WebView 禁止访问错误" `
    -Passed $uiDumpOk `
    -Evidence "dumpExit=$($uiDumpResult.exitCode); pullExit=$($pullUiDumpResult.exitCode); hasDisallowedUserAgent=$hasDisallowedUserAgent; path=$uiDumpPath" `
    -RequiredEvidence "uiautomator dump 可读取，且不包含 disallowed_useragent 或 Google WebView 禁止访问文案"

$providerPageSignals = Get-ProviderPageSignals -UiText $uiText
$providerPageSignalOk = $uiDumpResult.exitCode -eq 0 -and
    $pullUiDumpResult.exitCode -eq 0 -and
    $externalBrowser -and
    $providerPageSignals.blockingErrorCount -eq 0
Add-SmokeItem `
    -Id "provider-page-no-blocking-error" `
    -Title "外部 provider 页面未出现阻塞性错误信号" `
    -Passed $providerPageSignalOk `
    -Evidence "state=$($providerPageSignals.state); blockingErrorCount=$($providerPageSignals.blockingErrorCount); blockingErrorMatches=$((@($providerPageSignals.blockingErrorMatches) -join ',')); challengeHintCount=$($providerPageSignals.challengeHintCount); dumpExit=$($uiDumpResult.exitCode); pullExit=$($pullUiDumpResult.exitCode); path=$uiDumpPath" `
    -RequiredEvidence "外部浏览器页面 UI dump 可读，且没有 disallowed_useragent、redirect_uri_mismatch、invalid_client、unsupported_browser、Error 400/403 等阻塞性错误信号；登录页/验证码/MFA/paste code 只作为账号挑战信号，不代表账号完成"

$afterSessions = @(Read-BrowserAuthSessions)
$newThirdPartySessions = @(
    $afterSessions |
        Where-Object {
            $beforeSessionIds -notcontains $_.sessionId -and
            $_.redirectUri -like "https://developers.google.com/oauthplayground*"
        }
)
Add-SmokeItem `
    -Id "no-third-party-appredirect-session" `
    -Title "第三方 HTTPS redirect 不创建假 AppRedirect session" `
    -Passed ($newThirdPartySessions.Count -eq 0) `
    -Evidence "beforeCount=$($beforeSessions.Count); afterCount=$($afterSessions.Count); newThirdPartySessionIds=$((@($newThirdPartySessions | ForEach-Object { $_.sessionId }) -join ','))" `
    -RequiredEvidence "本次 Google OAuthPlayground redirect 不新增 redirectUri=https://developers.google.com/oauthplayground 的 browser auth session"

$providerBeforeSessions = @(Read-BrowserAuthSessions)
$providerBeforeSessionIds = @($providerBeforeSessions | ForEach-Object { $_.sessionId })
$providerOAuthResults = @(
    Invoke-ProviderOAuthHandoffCheck `
        -Id "openai" `
        -Title "OpenAI/Codex" `
        -Url $openAiOAuthUrl `
        -RedactedUrl $openAiOAuthUrlRedacted `
        -ExpectedPackage $httpsBrowserHandler.package
    Invoke-ProviderOAuthHandoffCheck `
        -Id "claude" `
        -Title "Claude" `
        -Url $claudeOAuthUrl `
        -RedactedUrl $claudeOAuthUrlRedacted `
        -ExpectedPackage $httpsBrowserHandler.package
)
$providerAfterSessions = @(Read-BrowserAuthSessions)
$newProviderSessions = @(
    $providerAfterSessions |
        Where-Object { $providerBeforeSessionIds -notcontains $_.sessionId }
)
$providerOAuthAllPassed = @($providerOAuthResults | Where-Object { -not $_.passed }).Count -eq 0 -and
    $providerOAuthResults.Count -eq 2
Add-SmokeItem `
    -Id "provider-oauth-no-auth-session" `
    -Title "OpenAI/Claude OAuth 形态 URL 不创建假 browser auth session" `
    -Passed ($newProviderSessions.Count -eq 0 -and $providerOAuthAllPassed) `
    -Evidence "beforeCount=$($providerBeforeSessions.Count); afterCount=$($providerAfterSessions.Count); newSessionIds=$((@($newProviderSessions | ForEach-Object { $_.sessionId }) -join ',')); providerResults=$((@($providerOAuthResults | ForEach-Object { "$($_.id):accepted=$($_.accepted);foreground=$($_.foregroundPackage);passed=$($_.passed)" }) -join '; '))" `
    -RequiredEvidence "OpenAI/Codex 与 Claude 相关 OAuth 形态 URL 只外部打开，不新增 AppRedirect、CliLoopback 或其他 browser auth session；该检查不输入账号、不伪造 callback"

$providerOAuthForegroundElapsedValues = @(
    $providerOAuthResults |
        ForEach-Object { [int64]$_.handoffForegroundElapsedMs } |
        Where-Object { $_ -ge 0 }
)
$providerOAuthForegroundMaxElapsedMs = if ($providerOAuthForegroundElapsedValues.Count -gt 0) {
    @($providerOAuthForegroundElapsedValues | Measure-Object -Maximum).Maximum
} else {
    -1
}
$providerOAuthForegroundResponsive = $providerOAuthForegroundElapsedValues.Count -eq 2 -and
    @($providerOAuthForegroundElapsedValues | Where-Object { $_ -gt $ForegroundResponsiveThresholdMs }).Count -eq 0
$externalForegroundResponsive = $externalBrowser -and
    $foregroundHandoffElapsedMs -ge 0 -and
    $foregroundHandoffElapsedMs -le $ForegroundResponsiveThresholdMs -and
    $providerOAuthAllPassed -and
    $providerOAuthForegroundResponsive
Add-SmokeItem `
    -Id "external-foreground-responsive" `
    -Title "OAuth handoff 前台切换足够快" `
    -Passed $externalForegroundResponsive `
    -Evidence "googleHandoffForegroundElapsedMs=$foregroundHandoffElapsedMs; providerMaxHandoffForegroundElapsedMs=$providerOAuthForegroundMaxElapsedMs; thresholdMs=$ForegroundResponsiveThresholdMs; providerResults=$((@($providerOAuthResults | ForEach-Object { "$($_.id):handoffForegroundElapsedMs=$($_.handoffForegroundElapsedMs);foreground=$($_.foregroundPackage);passed=$($_.passed)" }) -join '; '))" `
    -RequiredEvidence "Google、OpenAI/Codex、Claude 相关 OAuth 形态 URL 从 /open-web 请求到外部浏览器前台的总耗时不高于阈值；该阈值不覆盖 provider 页面加载、验证码/MFA 或账号风控"

$appRedirectBeforePrefs = Read-BrowserAuthSessionPrefs
$appRedirectBeforeIds = @($appRedirectBeforePrefs.sessions | ForEach-Object { $_.sessionId })
$appOpenElapsed = [System.Diagnostics.Stopwatch]::StartNew()
$appOpenResponse = $null
$appOpenError = ""
try {
    $body = @{
        url = $appRedirectUrl
        source = "browser_proxy"
    } | ConvertTo-Json -Depth 4
    $appOpenResponse = Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:$HostPort/open-web" -Body $body -ContentType "application/json; charset=utf-8" -TimeoutSec 10
} catch {
    $appOpenError = $_.Exception.Message
}
$appOpenElapsed.Stop()
$appOpenAccepted = $null -ne $appOpenResponse -and $appOpenResponse.ok -eq $true -and $appOpenResponse.accepted -eq $true
Add-SmokeItem `
    -Id "appredirect-open-web-accepted" `
    -Title "Kite App redirect OAuth URL 已被 /open-web 接收" `
    -Passed $appOpenAccepted `
    -Evidence "accepted=$appOpenAccepted; elapsedMs=$($appOpenElapsed.ElapsedMilliseconds); url=$(Redacted-KiteAppOAuthUrl); error=$appOpenError" `
    -RequiredEvidence "/open-web 返回 ok=true、accepted=true，且报告只保存 redirect_uri=kite_app 摘要"

$openWebResponsive = $openAccepted -and
    $appOpenAccepted -and
    $openElapsed.ElapsedMilliseconds -le $OpenWebResponsiveThresholdMs -and
    $appOpenElapsed.ElapsedMilliseconds -le $OpenWebResponsiveThresholdMs
Add-SmokeItem `
    -Id "open-web-responsive" `
    -Title "本地 /open-web handoff 接收足够快" `
    -Passed $openWebResponsive `
    -Evidence "googleElapsedMs=$($openElapsed.ElapsedMilliseconds); appRedirectElapsedMs=$($appOpenElapsed.ElapsedMilliseconds); thresholdMs=$OpenWebResponsiveThresholdMs" `
    -RequiredEvidence "Google OAuth 和 Kite App redirect 两次 /open-web 接收耗时都不高于阈值；该阈值只覆盖本地 handoff，不覆盖 provider 页面加载"

$appPendingResult = [ordered]@{ session = $null; prefs = $appRedirectBeforePrefs }
if ($appOpenAccepted) {
    $appPendingResult = Wait-NewBrowserAuthSession `
        -KnownIds $appRedirectBeforeIds `
        -Kind "AppRedirect" `
        -RedirectUri "kite-auth://callback" `
        -Seconds 20
}
$appPendingSession = $appPendingResult.session
$appPendingOk = $null -ne $appPendingSession -and $appPendingSession.status -eq "Pending"
$appPendingSessionId = if ($appPendingOk) { $appPendingSession.sessionId } else { "" }
Add-SmokeItem `
    -Id "appredirect-pending-session" `
    -Title "Kite 可接收 redirect 创建 AppRedirect pending session" `
    -Passed $appPendingOk `
    -Evidence "sessionId=$appPendingSessionId; status=$($appPendingSession.status); redirectUri=$($appPendingSession.redirectUri); kind=$($appPendingSession.kind); beforeCount=$($appRedirectBeforeIds.Count); afterCount=$($appPendingResult.prefs.sessions.Count)" `
    -RequiredEvidence "本次唯一 state 的 OAuth URL 创建 kind=AppRedirect、redirectUri=kite-auth://callback、status=Pending 的新 session"

$callbackResult = [ordered]@{ exitCode = -1; output = "pending session missing" }
if ($appPendingOk) {
    $callbackUrl = "kite-auth://callback?state=$(UrlEncode $appRedirectState)&code=$(UrlEncode $appRedirectSecretCode)&access_token=$(UrlEncode $appRedirectSecretToken)"
    $callbackResult = Invoke-Adb @("-s", $Serial, "shell", "am start -a android.intent.action.VIEW -d '$callbackUrl'")
}
$callbackStarted = $callbackResult.exitCode -eq 0
Add-SmokeItem `
    -Id "appredirect-callback-intent" `
    -Title "同 state App redirect callback 已触发" `
    -Passed $callbackStarted `
    -Evidence "exit=$($callbackResult.exitCode); callback=$(Redacted-KiteCallbackUrl)" `
    -RequiredEvidence "adb am start ACTION_VIEW kite-auth://callback 退出 0；报告不保存原始 code/token/state"

$appDeliveredResult = [ordered]@{ session = $appPendingSession; prefs = $appPendingResult.prefs }
if ($callbackStarted -and $appPendingOk) {
    # Returned is an intermediate state; wait until the runtime delivery either succeeds or fails.
    $appDeliveredResult = Wait-BrowserAuthSessionStatus `
        -SessionId $appPendingSessionId `
        -Statuses @("Delivered", "Failed") `
        -Seconds 20
}
$appDeliveredSession = $appDeliveredResult.session
$appDeliveredOk = $null -ne $appDeliveredSession -and $appDeliveredSession.status -eq "Delivered"
Add-SmokeItem `
    -Id "appredirect-callback-delivered" `
    -Title "App redirect callback 回到正确运行实例并交付" `
    -Passed $appDeliveredOk `
    -Evidence "sessionId=$appPendingSessionId; status=$($appDeliveredSession.status); returnedUrl=$($appDeliveredSession.returnedUrl); failureReason=$($appDeliveredSession.failureReason)" `
    -RequiredEvidence "同一 session 从 Pending 进入 Delivered；证明回跳入口、state 匹配和运行实例交付链路可用"

$expectedRedactedCallback = Redacted-KiteCallbackUrl
$returnedUrl = if ($null -ne $appDeliveredSession) { $appDeliveredSession.returnedUrl } else { "" }
$returnedUrlRedacted = $returnedUrl -eq $expectedRedactedCallback
$prefsText = "$($appDeliveredResult.prefs.rawXml)`n$($appDeliveredResult.prefs.decodedJson)"
$secretHits = @(
    $appRedirectSecretCode,
    $appRedirectSecretToken,
    $appRedirectState
) | Where-Object {
    -not [string]::IsNullOrWhiteSpace($_) -and $prefsText.Contains($_)
}
$noSecretPrefs = @($secretHits).Count -eq 0
Add-SmokeItem `
    -Id "appredirect-callback-redacted" `
    -Title "App redirect callback 参数已脱敏落盘" `
    -Passed ($returnedUrlRedacted -and $noSecretPrefs) `
    -Evidence "returnedUrl=$returnedUrl; rawSecretHitCount=$(@($secretHits).Count); expected=$expectedRedactedCallback" `
    -RequiredEvidence "returnedUrl 只含 code/access_token/state=present，shared_prefs 原文不含本次假 code/token/state"

$appPrivateSecretScan = Find-AppPrivateRawValueHits -Needles @(
    $testState,
    $openAiOAuthState,
    $claudeOAuthState,
    $appRedirectState,
    $appRedirectSecretCode,
    $appRedirectSecretToken
)
$appPrivateSecretScanOk = $appPrivateSecretScan.listExitCode -eq 0 -and
    $appPrivateSecretScan.grepErrorCount -eq 0 -and
    $appPrivateSecretScan.hitCount -eq 0 -and
    $appPrivateSecretScan.scannedFileCount -gt 0
Add-SmokeItem `
    -Id "no-oauth-temporary-values-in-app-files" `
    -Title "OAuth 临时值不进入 app 私有文本文件" `
    -Passed $appPrivateSecretScanOk `
    -Evidence "scannedFiles=$($appPrivateSecretScan.scannedFileCount); rawTemporaryValueHitCount=$($appPrivateSecretScan.hitCount); hitPaths=$((@($appPrivateSecretScan.hitPaths) -join ',')); grepErrorCount=$($appPrivateSecretScan.grepErrorCount); listExit=$($appPrivateSecretScan.listExitCode)" `
    -RequiredEvidence "本轮生成的 Google state、OpenAI state、Claude state、AppRedirect state、假 code 和假 token 不以原文出现在 app 私有 files/shared_prefs 的文本类状态或诊断文件中"

$logcatPattern = "FATAL EXCEPTION|AndroidRuntime.*FATAL|Application Not Responding|ANR in com.kite.app|Input dispatching timed out"
$logcatResult = Invoke-Adb @("-s", $Serial, "shell", "logcat -d -t 1200 | grep -E '$logcatPattern' || true")
$crashText = $logcatResult.output.Trim()
$noCrash = [string]::IsNullOrWhiteSpace($crashText)
Add-SmokeItem `
    -Id "no-crash-or-anr" `
    -Title "未发现崩溃或 ANR 关键日志" `
    -Passed $noCrash `
    -Evidence $(if ($noCrash) { "no matching logcat lines" } else { Limit-Text $crashText 1000 }) `
    -RequiredEvidence "logcat 最近 1200 行无 FATAL EXCEPTION/AndroidRuntime FATAL/ANR/Input timeout"

if (-not $LeaveBrowserOpen) {
    Invoke-Adb @("-s", $Serial, "shell", "am", "start", "-n", "com.kite.app/com.kite.app.MainActivity") | Out-Null
}

$failedItems = @($items | Where-Object { -not $_.passed })
$status = if ($failedItems.Count -eq 0) { "passed" } else { "failed" }
$failedItemIds = @($failedItems | ForEach-Object { $_["id"] })

$summary = [ordered]@{
    schemaVersion = $smokeSchemaVersion
    checkedAt = $checkedAt
    status = $status
    serial = $Serial
    stateDir = $StateDir
    hostPort = $HostPort
    devicePort = $DevicePort
    openWebResponsiveThresholdMs = $OpenWebResponsiveThresholdMs
    foregroundResponsiveThresholdMs = $ForegroundResponsiveThresholdMs
    authHostProbeAttempts = $AuthHostProbeAttempts
    authHostProbeRetryDelaySeconds = $AuthHostProbeRetryDelaySeconds
    authHostNetworkResults = @($authHostNetworkResults)
    httpsBrowserResolvePackage = $httpsBrowserHandler.package
    httpsBrowserResolveActivity = $httpsBrowserHandler.activity
    httpsBrowserResolveExported = $httpsBrowserHandler.exported
    httpsBrowserResolveExitCode = $httpsBrowserHandler.exitCode
    customTabsServiceCount = $customTabsServices.serviceCount
    customTabsServicePackages = @($customTabsServices.packages)
    providerOAuthResults = @($providerOAuthResults)
    providerOAuthNewSessionCount = $newProviderSessions.Count
    providerOAuthForegroundMaxElapsedMs = $providerOAuthForegroundMaxElapsedMs
    providerPageSignalState = $providerPageSignals.state
    providerPageBlockingErrorCount = $providerPageSignals.blockingErrorCount
    providerPageBlockingErrorMatches = @($providerPageSignals.blockingErrorMatches)
    providerPageChallengeHintCount = $providerPageSignals.challengeHintCount
    providerPageChallengeHintMatches = @($providerPageSignals.challengeHintMatches)
    localWebUrl = $localWebUrl
    localWebOpenWebElapsedMs = $localOpenElapsed.ElapsedMilliseconds
    localWebForegroundPackage = $localForeground.package
    localWebForegroundActivity = $localForeground.activity
    url = Redacted-GoogleOAuthUrl
    appRedirectUrl = Redacted-KiteAppOAuthUrl
    appRedirectCallback = Redacted-KiteCallbackUrl
    openWebElapsedMs = $openElapsed.ElapsedMilliseconds
    foregroundWaitElapsedMs = $foregroundWaitMs
    foregroundHandoffElapsedMs = $foregroundHandoffElapsedMs
    appRedirectOpenWebElapsedMs = $appOpenElapsed.ElapsedMilliseconds
    appRedirectSessionId = $appPendingSessionId
    appRedirectStatus = if ($null -ne $appDeliveredSession) { $appDeliveredSession.status } else { "" }
    appRedirectReturnedUrl = $returnedUrl
    appRedirectRawSecretHitCount = @($secretHits).Count
    appPrivateTextScannedFileCount = $appPrivateSecretScan.scannedFileCount
    appPrivateRawTemporaryValueHitCount = $appPrivateSecretScan.hitCount
    appPrivateRawTemporaryValueHitPaths = @($appPrivateSecretScan.hitPaths)
    foregroundPackage = $foreground.package
    foregroundActivity = $foreground.activity
    screenshotPath = $screenshotPath
    uiDumpPath = $uiDumpPath
    failedItemIds = @($failedItemIds)
    items = @($items.ToArray())
}
$summary | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $jsonPath -Encoding UTF8

$lines = New-Object System.Collections.Generic.List[string]
$lines.Add("# 浏览器登录 smoke test")
$lines.Add("")
$lines.Add("- schemaVersion：$smokeSchemaVersion")
$lines.Add("- 生成时间：$checkedAt")
$lines.Add("- 状态：$status")
$lines.Add("- serial：$Serial")
$lines.Add("- openWebResponsiveThresholdMs：$OpenWebResponsiveThresholdMs")
$lines.Add("- foregroundResponsiveThresholdMs：$ForegroundResponsiveThresholdMs")
$lines.Add("- authHostProbeAttempts：$AuthHostProbeAttempts")
$lines.Add("- authHostProbeRetryDelaySeconds：$AuthHostProbeRetryDelaySeconds")
$lines.Add("- authHostNetworkResults：$authHostNetworkEvidence")
$lines.Add("- httpsBrowserResolve：$($httpsBrowserHandler.package)/$($httpsBrowserHandler.activity); exported=$($httpsBrowserHandler.exported); exit=$($httpsBrowserHandler.exitCode)")
$lines.Add("- customTabsServiceCount：$($customTabsServices.serviceCount)")
$lines.Add("- customTabsServicePackages：$customTabsPackagesText")
$lines.Add("- providerOAuthResults：$((@($providerOAuthResults | ForEach-Object { "$($_.id):accepted=$($_.accepted);foreground=$($_.foregroundPackage);handoffForegroundElapsedMs=$($_.handoffForegroundElapsedMs);passed=$($_.passed)" }) -join '; '))")
$lines.Add("- providerOAuthNewSessionCount：$($newProviderSessions.Count)")
$lines.Add("- providerOAuthForegroundMaxElapsedMs：$providerOAuthForegroundMaxElapsedMs")
$lines.Add("- providerPageSignalState：$($providerPageSignals.state)")
$lines.Add("- providerPageBlockingErrorCount：$($providerPageSignals.blockingErrorCount)")
$lines.Add("- providerPageChallengeHintCount：$($providerPageSignals.challengeHintCount)")
$lines.Add("- localWebUrl：$localWebUrl")
$lines.Add("- localWebOpenWebElapsedMs：$($localOpenElapsed.ElapsedMilliseconds)")
$lines.Add("- localWebForeground：$($localForeground.package)/$($localForeground.activity)")
$lines.Add("- URL：$(Redacted-GoogleOAuthUrl)")
$lines.Add("- App redirect URL：$(Redacted-KiteAppOAuthUrl)")
$lines.Add("- App redirect callback：$(Redacted-KiteCallbackUrl)")
$lines.Add("- openWebElapsedMs：$($openElapsed.ElapsedMilliseconds)")
$lines.Add("- foregroundWaitElapsedMs：$foregroundWaitMs")
$lines.Add("- foregroundHandoffElapsedMs：$foregroundHandoffElapsedMs")
$lines.Add("- appRedirectOpenWebElapsedMs：$($appOpenElapsed.ElapsedMilliseconds)")
$lines.Add("- appRedirectStatus：$(if ($null -ne $appDeliveredSession) { $appDeliveredSession.status } else { '' })")
$lines.Add("- appRedirectReturnedUrl：$returnedUrl")
$lines.Add("- appPrivateTextScannedFileCount：$($appPrivateSecretScan.scannedFileCount)")
$lines.Add("- appPrivateRawTemporaryValueHitCount：$($appPrivateSecretScan.hitCount)")
$lines.Add("- 前台：$($foreground.package)/$($foreground.activity)")
$lines.Add("- 截图：$screenshotPath")
$lines.Add("- UI dump：$uiDumpPath")
$lines.Add("")
$lines.Add("## 检查项")
$lines.Add("")
foreach ($item in $items) {
    $lines.Add("- $(Format-Status ([bool]$item["passed"])) ``$($item["id"])``：$($item["title"])")
    $lines.Add("  - 证据：$($item["evidence"])")
    $lines.Add("  - 需要：$($item["requiredEvidence"])")
}
$lines.Add("")
$lines.Add("## 边界")
$lines.Add("")
$lines.Add("- 本脚本不输入账号、不绕过验证码/MFA/风控，不证明 N4/N5 账号完成。")
$lines.Add("- 本脚本证明的是 C2/C3/G3/G4 前置：OnePlus 8T 设备侧能到达主要授权主机，HTTPS 授权 URL 可解析到外部浏览器 handler，Kite 能把 Google/OpenAI/Claude 相关 OAuth 形态 URL 交给外部浏览器，外部 provider 页面没有明显阻塞性错误信号，第三方 HTTPS redirect 不建假 session，Kite 可接收 redirect 能按 state 回跳，并且本轮 OAuth 临时值不以原文进入 app 私有文本状态/诊断文件。")
$lines.Add("- Custom Tabs service 数量是能力诊断，不是单独的成功条件；当前路线允许在无 Custom Tabs service 时通过系统 ACTION_VIEW 浏览器 fallback 完成外部 user-agent handoff。")
$lines.Add("- App redirect callback 使用本机生成的假 code/token，只测试回跳机制和敏感字段边界，不代表 provider 已签发真实授权码。")
$lines.ToArray() | Set-Content -LiteralPath $reportPath -Encoding UTF8

Write-Output "browser-login-smoke-test"
Write-Output "checkedAt=$checkedAt"
Write-Output "status=$status"
Write-Output "foreground=$($foreground.package)/$($foreground.activity)"
Write-Output "failedItemIds=$($failedItemIds -join ',')"
Write-Output "json=$jsonPath"
Write-Output "report=$reportPath"

if ($status -eq "passed") {
    exit 0
}

exit 1
