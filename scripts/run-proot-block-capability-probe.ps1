param(
    [string]$DeviceSerial = '3f8bbaad'
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path $PSScriptRoot -Parent
$source = Join-Path $repoRoot 'native\kf-block-view-probe\kf-block-view-probe.c'
$outputDir = Join-Path $repoRoot 'local-artifacts\proot-block-capability'
$binary = Join-Path $outputDir 'kf-block-view-probe'
$mmapSource = Join-Path $repoRoot 'native\kf-block-view-probe\kf-block-mmap-prototype.c'
$mmapBinary = Join-Path $outputDir 'kf-block-mmap-prototype'
$adb = 'D:\KF\Android\Sdk\platform-tools\adb.exe'
$clang = 'D:\KF\Android\Sdk\ndk\26.3.11579264\toolchains\llvm\prebuilt\windows-x86_64\bin\aarch64-linux-android24-clang.cmd'
$packageName = 'com.kite.app'
$remoteStaging = '/data/local/tmp/kf-block-view-probe'
$privateBinary = 'files/runtime/bin/kf-block-view-probe'
$privateMmapBinary = 'files/runtime/bin/kf-block-mmap-prototype'
$privateWork = 'files/kf-block-view-capability-work'

New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
& $clang -O2 -Wall -Wextra -Werror -o $binary $source
if ($LASTEXITCODE -ne 0) { throw '编译块级能力探针失败。' }
& $clang -O2 -Wall -Wextra -Werror -o $mmapBinary $mmapSource
if ($LASTEXITCODE -ne 0) { throw '编译块级 mmap 原型失败。' }

& $adb -s $DeviceSerial push $binary $remoteStaging | Out-Null
if ($LASTEXITCODE -ne 0) { throw '推送块级能力探针失败。' }
& $adb -s $DeviceSerial shell run-as $packageName cp $remoteStaging $privateBinary
if ($LASTEXITCODE -ne 0) { throw '复制块级能力探针到应用私有目录失败。' }
& $adb -s $DeviceSerial shell run-as $packageName chmod 700 $privateBinary
if ($LASTEXITCODE -ne 0) { throw '设置块级能力探针权限失败。' }
& $adb -s $DeviceSerial push $mmapBinary "$remoteStaging-mmap" | Out-Null
if ($LASTEXITCODE -ne 0) { throw '推送块级 mmap 原型失败。' }
& $adb -s $DeviceSerial shell run-as $packageName cp "$remoteStaging-mmap" $privateMmapBinary
if ($LASTEXITCODE -ne 0) { throw '复制块级 mmap 原型到应用私有目录失败。' }
& $adb -s $DeviceSerial shell run-as $packageName chmod 700 $privateMmapBinary
if ($LASTEXITCODE -ne 0) { throw '设置块级 mmap 原型权限失败。' }

$lines = @(& $adb -s $DeviceSerial shell "run-as $packageName $privateBinary $privateWork" 2>&1)
$exitCode = $LASTEXITCODE
$mmapLines = @(& $adb -s $DeviceSerial shell "run-as $packageName $privateMmapBinary $privateWork-mmap" 2>&1)
$mmapExitCode = $LASTEXITCODE
$lines += $mmapLines
$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$resultPath = Join-Path $outputDir "result-$timestamp.tsv"
$lines | Set-Content -LiteralPath $resultPath -Encoding utf8
$lines

if ($exitCode -ne 0) {
    throw "块级能力探针失败，退出码 $exitCode；输出：$resultPath"
}
if ($mmapExitCode -ne 0 -or -not ($lines -contains "result`tBLOCK_MMAP_PROTOTYPE_OK")) {
    throw "块级 mmap 原型失败，退出码 $mmapExitCode；输出：$resultPath"
}
Write-Output "RESULT_TSV=$resultPath"
