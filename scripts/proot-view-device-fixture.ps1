param(
    [Parameter(Mandatory = $true)]
    [string]$CandidateBinary,
    [string]$DeviceSerial = '3f8bbaad'
)

$ErrorActionPreference = 'Stop'

$adb = 'D:\KF\Android\Sdk\platform-tools\adb.exe'
$packageName = 'com.kite.app'
$remoteScript = '/data/local/tmp/kite-proot-view-fixture.sh'
$remoteCandidate = '/data/local/tmp/kite-proot-view-candidate'
$privateCandidate = 'files/runtime/bin/proot-view-benchmark'
$localScript = Join-Path $PSScriptRoot 'proot-view-device-fixture.sh'
$repoRoot = Split-Path $PSScriptRoot -Parent

if (-not (Test-Path -LiteralPath $CandidateBinary)) {
    throw "候选 PRoot 不存在：$CandidateBinary"
}
if (-not (Test-Path -LiteralPath $localScript)) {
    throw "设备夹具脚本不存在：$localScript"
}

$deviceLine = & $adb -s $DeviceSerial get-state 2>&1
if ($LASTEXITCODE -ne 0 -or ($deviceLine -join '').Trim() -ne 'device') {
    throw "目标设备不可用：$DeviceSerial"
}

& $adb -s $DeviceSerial push $localScript $remoteScript | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw '推送 View 夹具脚本失败。'
}
& $adb -s $DeviceSerial shell chmod 755 $remoteScript
if ($LASTEXITCODE -ne 0) {
    throw '设置 View 夹具脚本权限失败。'
}
& $adb -s $DeviceSerial push $CandidateBinary $remoteCandidate | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw '推送候选 PRoot 失败。'
}
& $adb -s $DeviceSerial shell run-as $packageName cp $remoteCandidate $privateCandidate
if ($LASTEXITCODE -ne 0) {
    throw '复制候选 PRoot 到应用私有目录失败。'
}
& $adb -s $DeviceSerial shell run-as $packageName chmod 700 $privateCandidate
if ($LASTEXITCODE -ne 0) {
    throw '设置候选 PRoot 权限失败。'
}

$lines = @(& $adb -s $DeviceSerial shell "run-as $packageName sh $remoteScript" 2>&1)
$exitCode = $LASTEXITCODE
$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$outputDir = Join-Path $repoRoot "local-artifacts\proot-view-fixture\$timestamp"
New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
$rawPath = Join-Path $outputDir 'result.tsv'
$lines | Set-Content -LiteralPath $rawPath -Encoding utf8
$lines

if ($exitCode -ne 0) {
    throw "设备 View 夹具失败，退出码 $exitCode；输出：$rawPath"
}
if (-not ($lines -contains "result`tVIEW_FIXTURE_OK")) {
    throw "设备 View 夹具缺少成功标记；输出：$rawPath"
}

Write-Output "RESULT_TSV=$rawPath"
