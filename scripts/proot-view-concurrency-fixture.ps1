param(
    [Parameter(Mandatory = $true)]
    [string]$CandidateBinary,
    [string]$DeviceSerial = '3f8bbaad'
)

$ErrorActionPreference = 'Stop'
$adb = 'D:\KF\Android\Sdk\platform-tools\adb.exe'
$packageName = 'com.kite.app'
$remoteScript = '/data/local/tmp/kite-proot-view-concurrency-fixture.sh'
$remoteCandidate = '/data/local/tmp/kite-proot-view-candidate'
$privateCandidate = 'files/runtime/bin/proot-view-benchmark'
$localScript = Join-Path $PSScriptRoot 'proot-view-concurrency-fixture.sh'
$repoRoot = Split-Path $PSScriptRoot -Parent

if (-not (Test-Path -LiteralPath $CandidateBinary)) {
    throw "候选 PRoot 不存在：$CandidateBinary"
}

& $adb -s $DeviceSerial push $localScript $remoteScript | Out-Null
if ($LASTEXITCODE -ne 0) { throw '推送并发 View 夹具失败。' }
& $adb -s $DeviceSerial shell chmod 755 $remoteScript
if ($LASTEXITCODE -ne 0) { throw '设置并发 View 夹具权限失败。' }
& $adb -s $DeviceSerial push $CandidateBinary $remoteCandidate | Out-Null
if ($LASTEXITCODE -ne 0) { throw '推送候选 PRoot 失败。' }
& $adb -s $DeviceSerial shell run-as $packageName cp $remoteCandidate $privateCandidate
if ($LASTEXITCODE -ne 0) { throw '复制候选 PRoot 失败。' }
& $adb -s $DeviceSerial shell run-as $packageName chmod 700 $privateCandidate
if ($LASTEXITCODE -ne 0) { throw '设置候选 PRoot 权限失败。' }

$lines = @(& $adb -s $DeviceSerial shell "run-as $packageName sh $remoteScript" 2>&1)
$exitCode = $LASTEXITCODE
$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$outputDirectory = Join-Path $repoRoot "local-artifacts\proot-view-concurrency\$timestamp"
New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
$resultPath = Join-Path $outputDirectory 'result.tsv'
$lines | Set-Content -LiteralPath $resultPath -Encoding utf8
$lines

if ($exitCode -ne 0) {
    throw "并发 View 夹具失败，退出码 $exitCode；输出：$resultPath"
}
if (-not ($lines -contains "result`tVIEW_CONCURRENCY_FIXTURE_OK")) {
    throw "并发 View 夹具缺少成功标记；输出：$resultPath"
}
Write-Output "RESULT_TSV=$resultPath"
