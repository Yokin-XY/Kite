param(
    [string]$Serial = "3f8bbaad",
    [string]$AdbPath = "D:\KF\Android\Sdk\platform-tools\adb.exe",
    [int]$Rounds = 3,
    [string]$OutputPath = "local-artifacts\host-node-pressure-benchmark.jsonl",
    [string]$CaseWorkload = "",
    [string]$CaseLane = "",
    [int]$CaseLoad = 0
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$source = Join-Path $PSScriptRoot "host-node-pressure-benchmark.cjs"
$runnerSource = Join-Path $PSScriptRoot "host-node-env-runner.sh"
$artifactPath = Join-Path $repoRoot $OutputPath
$remoteStage = "/data/local/tmp/kite-node-pressure-benchmark.cjs"
$remoteRunnerStage = "/data/local/tmp/kite-node-env-runner.sh"
$sharedRoot = "/data/user/0/com.kite.app/files/runtime/shared/ubuntu-main"
$remoteScript = "$sharedRoot/host-node-pressure-benchmark.cjs"
$remoteRunner = "$sharedRoot/.kf/system/node-runtime/host/kite-node-env-runner.sh"
$managedCommand = "$sharedRoot/.kf/bin/kite-node-pressure-benchmark"
$launcher = "$sharedRoot/.kf/system/node-runtime/host/kite-node-host"

if (-not (Test-Path -LiteralPath $AdbPath)) { throw "ADB 不存在：$AdbPath" }
if (-not (Test-Path -LiteralPath $source)) { throw "基准脚本不存在：$source" }
if (-not (Test-Path -LiteralPath $runnerSource)) { throw "环境运行器不存在：$runnerSource" }
if ($Rounds -lt 1 -or $Rounds -gt 10) { throw "Rounds 必须在 1..10" }

& $AdbPath -s $Serial get-state | Out-Null
if ($LASTEXITCODE -ne 0) { throw "设备不可用：$Serial" }

$gatewayLine = & $AdbPath -s $Serial shell "ps -A -o PID,NAME,ARGS | grep openclaw-gateway | grep -v grep"
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace(($gatewayLine -join ""))) {
    throw "需要先启动 OpenClaw Gateway，基准会复用其已验证的 Host Node 环境合同。"
}
$gatewayPid = (($gatewayLine -join "`n").Trim() -split '\s+')[0]
if ($gatewayPid -notmatch '^\d+$') { throw "无法解析 Gateway PID：$gatewayLine" }

& $AdbPath -s $Serial push $source $remoteStage | Out-Null
if ($LASTEXITCODE -ne 0) { throw "推送基准脚本失败" }
& $AdbPath -s $Serial push $runnerSource $remoteRunnerStage | Out-Null
if ($LASTEXITCODE -ne 0) { throw "推送环境运行器失败" }
& $AdbPath -s $Serial shell run-as com.kite.app cp $remoteStage $remoteScript
if ($LASTEXITCODE -ne 0) { throw "复制基准脚本到工作区失败" }
& $AdbPath -s $Serial shell run-as com.kite.app cp $remoteScript $managedCommand
if ($LASTEXITCODE -ne 0) { throw "创建受管 Node 基准命令失败" }
& $AdbPath -s $Serial shell run-as com.kite.app cp $remoteRunnerStage $remoteRunner
if ($LASTEXITCODE -ne 0) { throw "复制环境运行器失败" }
& $AdbPath -s $Serial shell run-as com.kite.app chmod 700 $remoteScript $managedCommand $remoteRunner
if ($LASTEXITCODE -ne 0) { throw "设置基准脚本权限失败" }

$artifactDirectory = Split-Path -Parent $artifactPath
New-Item -ItemType Directory -Force -Path $artifactDirectory | Out-Null
try {
    $benchmarkArguments = if ($CaseWorkload) {
        if ($CaseLane -notin @("host_node", "proot") -or $CaseLoad -lt 1) {
            throw "CaseWorkload 需要有效的 CaseLane 和 CaseLoad"
        }
        @("--case", $CaseWorkload, $CaseLane, "$CaseLoad", "$Rounds")
    } else {
        @("--matrix", "--rounds", "$Rounds")
    }
    & $AdbPath -s $Serial exec-out run-as com.kite.app $remoteRunner $gatewayPid $launcher $remoteScript @benchmarkArguments |
        Set-Content -LiteralPath $artifactPath -Encoding utf8
    if ($LASTEXITCODE -ne 0) { throw "真机 Host/PRoot 压力矩阵失败" }
} finally {
    & $AdbPath -s $Serial shell run-as com.kite.app rm -f $remoteScript $managedCommand $remoteRunner | Out-Null
    & $AdbPath -s $Serial shell rm -f $remoteStage $remoteRunnerStage | Out-Null
}

$finalLine = Get-Content -LiteralPath $artifactPath | Select-Object -Last 1
if (-not $finalLine.TrimStart().StartsWith("{")) {
    throw "压力矩阵输出不是 JSON：$finalLine"
}
$final = $finalLine | ConvertFrom-Json
if (-not $final.ok) { throw "压力矩阵存在失败：$artifactPath" }
$displayResults = if ($final.results) { $final.results } else { @($final.result) }
$displayResults | Format-Table kind, lane, load, rounds, p50Ms, p95Ms, failures -AutoSize
Write-Output "结果：$artifactPath"
