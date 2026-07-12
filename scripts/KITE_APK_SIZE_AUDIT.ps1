param(
    [string]$Apk = (Join-Path $PSScriptRoot '..\app\build\outputs\apk\debug\app-debug.apk'),
    [long]$MaxBytes = 243269632
)

$ErrorActionPreference = 'Stop'
$resolvedApk = (Resolve-Path $Apk).Path
$apkFile = Get-Item $resolvedApk
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($resolvedApk)

try {
    $entries = @($zip.Entries | ForEach-Object { $_.FullName })
    $expected = @(
        'assets/rootfs/ubuntu-base-24.04-arm64.tgz',
        'assets/toolchain/ai-dev-pack/packages/cpython-3.14.6+20260623-aarch64-unknown-linux-gnu-install_only_stripped.tgz',
        'assets/toolchain/ai-dev-pack/packages/uv-aarch64-unknown-linux-gnu.tgz'
    )
    $forbidden = @(
        'assets/rootfs/ubuntu-base-24.04-arm64.tar',
        'assets/toolchain/ai-dev-pack/packages/cpython-3.14.6+20260623-aarch64-unknown-linux-gnu-install_only_stripped.tar',
        'assets/toolchain/ai-dev-pack/packages/uv-aarch64-unknown-linux-gnu.tar'
    )

    foreach ($entry in $expected) {
        if ($entry -notin $entries) { throw "APK missing compressed asset: $entry" }
    }
    foreach ($entry in $forbidden) {
        if ($entry -in $entries) { throw "APK contains expanded tar asset: $entry" }
    }
    if ($apkFile.Length -gt $MaxBytes) {
        throw "APK exceeds size budget: $($apkFile.Length) > $MaxBytes bytes"
    }

    $assetCompressedBytes = ($zip.Entries | Where-Object FullName -Like 'assets/*' |
        Measure-Object CompressedLength -Sum).Sum
    Write-Host "Kite APK size audit passed: apk=$($apkFile.Length) assetsCompressed=$assetCompressedBytes max=$MaxBytes"
} finally {
    $zip.Dispose()
}
