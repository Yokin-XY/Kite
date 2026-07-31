param(
    [string]$OutputPath = '',
    [ValidateSet('arm64', 'arm32')]
    [string]$Architecture = 'arm64'
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path $PSScriptRoot -Parent
$source = Join-Path $repoRoot 'native\kf-view-fixture\kf-view-fixture.c'
$clangName = if ($Architecture -eq 'arm32') {
    'armv7a-linux-androideabi24-clang.cmd'
} else {
    'aarch64-linux-android24-clang.cmd'
}
$clang = Join-Path 'D:\KF\Android\Sdk\ndk\26.3.11579264\toolchains\llvm\prebuilt\windows-x86_64\bin' $clangName

if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    $outputDirectory = Join-Path $repoRoot 'local-artifacts\proot-view-tools'
    $fileName = if ($Architecture -eq 'arm32') { 'kf-view-fixture32' } else { 'kf-view-fixture' }
    $OutputPath = Join-Path $outputDirectory $fileName
} else {
    $outputDirectory = Split-Path $OutputPath -Parent
}

if (-not (Test-Path -LiteralPath $clang)) {
    throw "Android NDK clang 不存在：$clang"
}
if (-not (Test-Path -LiteralPath $source)) {
    throw "语义夹具源码不存在：$source"
}
New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null

& $clang -D_FILE_OFFSET_BITS=64 -O2 -Wall -Wextra -Werror -fPIE -pie -o $OutputPath $source
if ($LASTEXITCODE -ne 0) {
    throw '构建 PRoot View 语义夹具失败。'
}

Write-Output $OutputPath
