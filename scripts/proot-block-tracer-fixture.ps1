param(
    [string]$CandidateBinary = 'D:\xm\KFshell\build\external\termux-proot-block-view\src\proot',
    [string]$DeviceSerial = '3f8bbaad',
    [switch]$FormalView
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path $PSScriptRoot -Parent
$source = Join-Path $repoRoot 'native\kf-block-view-probe\kf-block-proot-tracee.c'
$outputDir = Join-Path $repoRoot 'local-artifacts\proot-block-tracer'
$tracee = Join-Path $outputDir 'kf-block-proot-tracee'
$script = Join-Path $PSScriptRoot 'proot-block-tracer-fixture.sh'
$adb = 'D:\KF\Android\Sdk\platform-tools\adb.exe'
$clang = 'D:\KF\Android\Sdk\ndk\26.3.11579264\toolchains\llvm\prebuilt\windows-x86_64\bin\aarch64-linux-android24-clang.cmd'
$strip = 'D:\KF\Android\Sdk\ndk\26.3.11579264\toolchains\llvm\prebuilt\windows-x86_64\bin\llvm-strip.exe'
$packageName = 'com.kite.app'

New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
& $clang -O2 -Wall -Wextra -Werror -o $tracee $source
if ($LASTEXITCODE -ne 0) { throw '编译 PRoot 块级 tracee 失败。' }
& $strip $CandidateBinary
if ($LASTEXITCODE -ne 0) { throw '裁剪 PRoot 块级候选失败。' }

$remoteProot = '/data/local/tmp/proot-block-prototype'
$remoteTracee = '/data/local/tmp/kf-block-proot-tracee'
$remoteScript = '/data/local/tmp/proot-block-tracer-fixture.sh'
& $adb -s $DeviceSerial push $CandidateBinary $remoteProot | Out-Null
& $adb -s $DeviceSerial push $tracee $remoteTracee | Out-Null
& $adb -s $DeviceSerial push $script $remoteScript | Out-Null
if ($LASTEXITCODE -ne 0) { throw '推送 PRoot 块级 tracer 夹具失败。' }
& $adb -s $DeviceSerial shell run-as $packageName cp $remoteProot files/runtime/bin/proot-block-prototype
& $adb -s $DeviceSerial shell run-as $packageName cp $remoteTracee files/runtime/bin/kf-block-proot-tracee
& $adb -s $DeviceSerial shell run-as $packageName chmod 700 files/runtime/bin/proot-block-prototype files/runtime/bin/kf-block-proot-tracee
if ($LASTEXITCODE -ne 0) { throw '部署 PRoot 块级 tracer 夹具失败。' }

$remoteCommand = if ($FormalView) {
    "run-as $packageName env KF_BLOCK_FIXTURE_MODE=formal sh $remoteScript"
} else {
    "run-as $packageName sh $remoteScript"
}
$lines = @(& $adb -s $DeviceSerial shell $remoteCommand 2>&1)
$exitCode = $LASTEXITCODE
$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$suffix = if ($FormalView) { 'formal-view' } else { 'probe' }
$resultPath = Join-Path $outputDir "result-$suffix-$timestamp.tsv"
$lines | Set-Content -LiteralPath $resultPath -Encoding utf8
$lines
if ($exitCode -ne 0 -or -not ($lines -contains "result`tPROOT_BLOCK_TRACER_FIXTURE_OK")) {
    throw "PRoot 块级 tracer 夹具失败，退出码 $exitCode；输出：$resultPath"
}
Write-Output "RESULT_TSV=$resultPath"
