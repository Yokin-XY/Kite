param(
    [Parameter(Mandatory = $true)]
    [string]$CandidateBinary,
    [ValidateRange(64, 4096)]
    [int]$SizeMiB = 512,
    [string]$DeviceSerial = '3f8bbaad'
)

$ErrorActionPreference = 'Stop'

$adb = 'D:\KF\Android\Sdk\platform-tools\adb.exe'
$packageName = 'com.kite.app'
$remoteScript = '/data/local/tmp/kite-proot-view-crash-fixture.sh'
$remoteCandidate = '/data/local/tmp/kite-proot-view-candidate'
$privateCandidate = 'files/runtime/bin/proot-view-benchmark'
$localScript = Join-Path $PSScriptRoot 'proot-view-crash-fixture.sh'
$repoRoot = Split-Path $PSScriptRoot -Parent

if (-not (Test-Path -LiteralPath $CandidateBinary)) {
    throw "候选 PRoot 不存在：$CandidateBinary"
}

& $adb -s $DeviceSerial push $localScript $remoteScript | Out-Null
if ($LASTEXITCODE -ne 0) { throw '推送崩溃夹具脚本失败。' }
& $adb -s $DeviceSerial push $CandidateBinary $remoteCandidate | Out-Null
if ($LASTEXITCODE -ne 0) { throw '推送候选 PRoot 失败。' }
& $adb -s $DeviceSerial shell chmod 755 $remoteScript
if ($LASTEXITCODE -ne 0) { throw '设置崩溃夹具权限失败。' }
& $adb -s $DeviceSerial shell run-as $packageName cp $remoteCandidate $privateCandidate
if ($LASTEXITCODE -ne 0) { throw '复制候选 PRoot 到应用私有目录失败。' }
& $adb -s $DeviceSerial shell run-as $packageName chmod 700 $privateCandidate
if ($LASTEXITCODE -ne 0) { throw '设置候选 PRoot 权限失败。' }

$lines = @(& $adb -s $DeviceSerial shell run-as $packageName sh $remoteScript $SizeMiB 2>&1)
$exitCode = $LASTEXITCODE
$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$outputDirectory = Join-Path $repoRoot "local-artifacts\proot-view-crash-fixture\$timestamp-$($SizeMiB)mib"
New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
$outputPath = Join-Path $outputDirectory 'result.tsv'
$lines | Set-Content -LiteralPath $outputPath -Encoding utf8
$lines

if ($exitCode -ne 0) {
    throw "崩溃夹具失败，退出码 $exitCode；输出：$outputPath"
}
if (-not ($lines -contains "result`tVIEW_CRASH_FIXTURE_OK")) {
    throw "崩溃夹具缺少成功标记；输出：$outputPath"
}

Write-Output "RESULT_TSV=$outputPath"
