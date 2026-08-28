param(
    [string]$Serial = "181QGEYH222B9",
    [string]$AdbPath = "D:\KF\Android\Sdk\platform-tools\adb.exe",
    [string]$PackageName = "com.kite.app",
    [string]$EnvironmentId = "ubuntu-main",
    [string]$AgentCommand = "/workspace/.kf/bin/hermes",
    [int]$TimeoutSeconds = 20,
    [bool]$DisableLazyInstalls = $true
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $AdbPath)) { throw "ADB 不存在：$AdbPath" }
if ($TimeoutSeconds -lt 3 -or $TimeoutSeconds -gt 120) { throw "TimeoutSeconds 必须在 3..120" }
if ($EnvironmentId -notmatch '^[0-9A-Za-z._-]+$') { throw "EnvironmentId 不安全：$EnvironmentId" }
if ($PackageName -notmatch '^[0-9A-Za-z._]+$') { throw "PackageName 不安全：$PackageName" }

& $AdbPath -s $Serial get-state | Out-Null
if ($LASTEXITCODE -ne 0) { throw "设备不可用：$Serial" }

$contractPath = "files/runtime/shared/$EnvironmentId/.kf/proot-launch-contract.json"
$contractText = (& $AdbPath -s $Serial exec-out run-as $PackageName cat $contractPath) -join "`n"
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($contractText)) {
    throw "无法读取 PRoot 启动合同：$contractPath"
}
$contract = $contractText | ConvertFrom-Json -Depth 30
if ($contract.authority -ne "android_control_plane" -or $contract.kind -ne "CONTAINER") {
    throw "PRoot 启动合同不属于 Android 容器控制面"
}

$adbArguments = [System.Collections.Generic.List[string]]::new()
@("-s", $Serial, "shell", "run-as", $PackageName, "/system/bin/env") |
    ForEach-Object { $adbArguments.Add($_) }
$prootLibDir = [string]$contract.runtime.proot.installed.libDir
if ([string]::IsNullOrWhiteSpace($prootLibDir)) {
    $prootLibDir = ([string]$contract.executablePath) -replace '/bin/proot$', '/lib'
}
$adbArguments.Add("LD_LIBRARY_PATH=$prootLibDir")
$adbArguments.Add("PROOT_TMP_DIR=$($contract.runtime.tmpDirPath)")
$adbArguments.Add([string]$contract.executablePath)
$contract.flags | ForEach-Object { $adbArguments.Add([string]$_) }
@("-r", [string]$contract.rootfsPath, "-w", [string]$contract.workingDirectory) |
    ForEach-Object { $adbArguments.Add($_) }
foreach ($mount in $contract.bindMounts) {
    $adbArguments.Add("-b")
    $source = [string]$mount.sourcePath
    $target = [string]$mount.targetPath
    $adbArguments.Add($(if ($source -eq $target) { $source } else { "${source}:${target}" }))
}
if ($contract.network.includeNetworkModeFlag -and $null -ne $contract.network.prootFlag) {
    $adbArguments.Add([string]$contract.network.prootFlag)
}

$adbArguments.Add("/usr/bin/env")
@(
    "HOME=/root",
    "USER=root",
    "LANG=C.UTF-8",
    "TERM=dumb",
    "FORCE_COLOR=0",
    "PATH=/workspace/.kf/bin:/root/.local/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
) | ForEach-Object { $adbArguments.Add($_) }
if ($DisableLazyInstalls) { $adbArguments.Add("HERMES_DISABLE_LAZY_INSTALLS=1") }
@($AgentCommand, "acp") | ForEach-Object { $adbArguments.Add($_) }

$startInfo = [System.Diagnostics.ProcessStartInfo]::new()
$startInfo.FileName = $AdbPath
$startInfo.UseShellExecute = $false
$startInfo.CreateNoWindow = $true
$startInfo.RedirectStandardInput = $true
$startInfo.RedirectStandardOutput = $true
$startInfo.RedirectStandardError = $true
$adbArguments | ForEach-Object { [void]$startInfo.ArgumentList.Add($_) }

$process = [System.Diagnostics.Process]::new()
$process.StartInfo = $startInfo
$stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
$started = $false
$succeeded = $false
try {
    $started = $process.Start()
    if (-not $started) { throw "无法启动 ACP 直连探针" }
    $stderrTask = $process.StandardError.ReadToEndAsync()
    $responseTask = $process.StandardOutput.ReadLineAsync()
    $initialize = [ordered]@{
        jsonrpc = "2.0"
        id = 1
        method = "initialize"
        params = [ordered]@{
            protocolVersion = 1
            clientCapabilities = [ordered]@{
                fs = [ordered]@{ readTextFile = $true; writeTextFile = $true }
                terminal = $true
            }
            clientInfo = [ordered]@{
                name = "kite-device-acp-probe"
                title = "Kite device ACP probe"
                version = "1"
            }
        }
    } | ConvertTo-Json -Compress -Depth 10
    $process.StandardInput.WriteLine($initialize)
    $process.StandardInput.Flush()

    while (-not $responseTask.IsCompleted -and -not $process.HasExited -and $stopwatch.Elapsed.TotalSeconds -lt $TimeoutSeconds) {
        Start-Sleep -Milliseconds 50
    }
    if (-not $responseTask.IsCompleted) {
        throw "ACP initialize 在 $TimeoutSeconds 秒内没有返回"
    }
    $responseLine = $responseTask.GetAwaiter().GetResult()
    if ([string]::IsNullOrWhiteSpace($responseLine)) { throw "ACP 进程未返回 initialize 响应" }
    $response = $responseLine | ConvertFrom-Json -Depth 20
    if ($response.id -ne 1 -or $null -ne $response.error -or $null -eq $response.result) {
        throw "ACP initialize 响应无效：$responseLine"
    }
    Write-Output "ACP initialize 成功：$([math]::Round($stopwatch.Elapsed.TotalSeconds, 2)) 秒"
    Write-Output $responseLine
    $succeeded = $true
}
finally {
    if ($started -and -not $process.HasExited) {
        $process.Kill($true)
        $process.WaitForExit(5000) | Out-Null
    }
    if ($null -ne $stderrTask) {
        $stderrText = $stderrTask.GetAwaiter().GetResult().Trim()
        if ($stderrText -and -not $succeeded) {
            $stderrTail = ($stderrText -split "`r?`n" | Select-Object -Last 30) -join "`n"
            Write-Warning "ACP stderr（末尾 30 行）：`n$stderrTail"
        } elseif ($stderrText) {
            Write-Verbose $stderrText
        }
    }
    $process.Dispose()
}
