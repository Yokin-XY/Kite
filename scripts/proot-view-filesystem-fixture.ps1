param(
    [Parameter(Mandatory = $true)]
    [string]$CandidateBinary,
    [string]$DeviceSerial = '3f8bbaad',
    [switch]$AllowFailure
)

$ErrorActionPreference = 'Stop'

$adb = 'D:\KF\Android\Sdk\platform-tools\adb.exe'
$packageName = 'com.kite.app'
$remoteScript = '/data/local/tmp/kite-proot-view-filesystem-fixture.sh'
$remoteCandidate = '/data/local/tmp/kite-proot-view-candidate'
$remoteHelper = '/data/local/tmp/kf-view-fixture-helper'
$remoteHelper32 = '/data/local/tmp/kf-view-fixture-helper32'
$privateCandidate = 'files/runtime/bin/proot-view-benchmark'
$privateHelper = 'files/runtime/bin/kf-view-fixture'
$privateHelper32 = 'files/runtime/bin/kf-view-fixture32'
$localScript = Join-Path $PSScriptRoot 'proot-view-filesystem-fixture.sh'
$repoRoot = Split-Path $PSScriptRoot -Parent

if (-not (Test-Path -LiteralPath $CandidateBinary)) {
    throw "候选 PRoot 不存在：$CandidateBinary"
}

$helper = & (Join-Path $PSScriptRoot 'build-proot-view-fixture-helper.ps1')
if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $helper)) {
    throw '构建语义夹具 helper 失败。'
}
$helper32 = & (Join-Path $PSScriptRoot 'build-proot-view-fixture-helper.ps1') -Architecture arm32
if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $helper32)) {
    throw '构建 32 位 ABI 语义夹具 helper 失败。'
}

& $adb -s $DeviceSerial push $localScript $remoteScript | Out-Null
if ($LASTEXITCODE -ne 0) { throw '推送文件系统夹具脚本失败。' }
& $adb -s $DeviceSerial push $CandidateBinary $remoteCandidate | Out-Null
if ($LASTEXITCODE -ne 0) { throw '推送候选 PRoot 失败。' }
& $adb -s $DeviceSerial push $helper $remoteHelper | Out-Null
if ($LASTEXITCODE -ne 0) { throw '推送文件系统夹具 helper 失败。' }
& $adb -s $DeviceSerial push $helper32 $remoteHelper32 | Out-Null
if ($LASTEXITCODE -ne 0) { throw '推送 32 位 ABI 语义夹具 helper 失败。' }
& $adb -s $DeviceSerial shell chmod 755 $remoteScript $remoteHelper $remoteHelper32
if ($LASTEXITCODE -ne 0) { throw '设置远端夹具权限失败。' }
& $adb -s $DeviceSerial shell run-as $packageName cp $remoteCandidate $privateCandidate
if ($LASTEXITCODE -ne 0) { throw '复制候选 PRoot 到应用私有目录失败。' }
& $adb -s $DeviceSerial shell run-as $packageName cp $remoteHelper $privateHelper
if ($LASTEXITCODE -ne 0) { throw '复制文件系统夹具 helper 到应用私有目录失败。' }
& $adb -s $DeviceSerial shell run-as $packageName cp $remoteHelper32 $privateHelper32
if ($LASTEXITCODE -ne 0) { throw '复制 32 位 ABI 语义夹具 helper 到应用私有目录失败。' }
& $adb -s $DeviceSerial shell run-as $packageName chmod 700 $privateCandidate $privateHelper $privateHelper32
if ($LASTEXITCODE -ne 0) { throw '设置候选 PRoot 权限失败。' }

$lines = @(& $adb -s $DeviceSerial shell "run-as $packageName sh $remoteScript" 2>&1)
$exitCode = $LASTEXITCODE
$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$outputDirectory = Join-Path $repoRoot "local-artifacts\proot-view-filesystem-fixture\$timestamp"
New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
$outputPath = Join-Path $outputDirectory 'result.tsv'
$lines | Set-Content -LiteralPath $outputPath -Encoding utf8
$lines

if ($exitCode -ne 0 -and -not $AllowFailure) {
    throw "文件系统夹具失败，退出码 $exitCode；输出：$outputPath"
}
if ($exitCode -eq 0 -and -not ($lines -contains "result`tVIEW_FILESYSTEM_FIXTURE_OK")) {
    throw "文件系统夹具缺少成功标记；输出：$outputPath"
}

Write-Output "EXIT_CODE=$exitCode"
Write-Output "RESULT_TSV=$outputPath"
