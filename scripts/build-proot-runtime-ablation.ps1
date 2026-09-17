[CmdletBinding()]
param(
    [ValidateSet('base', 'lifecycle', 'procfs', 'transaction', 'protection', 'view', 'block-view')]
    [string]$Stage = 'block-view',
    [ValidateSet('26.3.11579264', '28.2.13676358')]
    [string]$NdkVersion = '26.3.11579264',
    [switch]$UnbundleLoader,
    [string]$SourceRepository = 'D:\xm\KFshell\build\external\termux-proot',
    [string]$SourceCommit = 'd30b98846cfdf0923bea26956922a2acf9ef23ae',
    [string]$BuildVersion = 'v5.1.107.76-dirty',
    [string]$OutputDirectory
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repositoryRoot = Split-Path $PSScriptRoot -Parent
$localArtifacts = Join-Path $repositoryRoot 'local-artifacts'
$stageOrder = @('lifecycle', 'procfs', 'transaction', 'protection', 'view', 'block-view')
$patches = @(
    'assets/proot/patches/kf-proot-lifecycle-telemetry-v2.patch',
    'assets/proot/patches/kf-proot-procfs-projection-v1.patch',
    'assets/proot/patches/kf-proot-resource-transaction-v1.patch',
    'assets/proot/patches/kf-proot-file-protection-v2.patch',
    'assets/proot/patches/kf-proot-view-v1.patch',
    'assets/proot/patches/kf-proot-block-view-v2.patch'
)

if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $suffix = if ($UnbundleLoader) { '-unbundled' } else { '' }
    $OutputDirectory = Join-Path $localArtifacts "proot-ablation-$Stage-ndk$NdkVersion$suffix"
}
$output = [IO.Path]::GetFullPath($OutputDirectory)
$allowedRoot = [IO.Path]::GetFullPath($localArtifacts) + [IO.Path]::DirectorySeparatorChar
if (!$output.StartsWith($allowedRoot, [StringComparison]::OrdinalIgnoreCase)) {
    throw "输出目录必须位于 local-artifacts：$output"
}
if (Test-Path -LiteralPath $output) {
    throw "输出目录已存在；为避免覆盖实验证据，请换一个目录：$output"
}
if (!(Test-Path -LiteralPath (Join-Path $SourceRepository '.git'))) {
    throw "PRoot 源仓库不存在：$SourceRepository"
}
& git -C $SourceRepository cat-file -e "$SourceCommit^{commit}"
if ($LASTEXITCODE -ne 0) {
    throw "PRoot 源提交不存在：$SourceCommit"
}

New-Item -ItemType Directory -Path $output | Out-Null
$archive = Join-Path $output 'source.tar'
& git -C $SourceRepository archive --format=tar --output=$archive $SourceCommit
if ($LASTEXITCODE -ne 0) {
    throw '创建源码归档失败'
}
& tar -xf $archive -C $output
if ($LASTEXITCODE -ne 0) {
    throw '展开源码归档失败'
}

$relativeOutput = [IO.Path]::GetRelativePath($repositoryRoot, $output).Replace('\', '/')
if ($Stage -eq 'base') {
    # 上游基线缺少当前 NDK 要求的 ashmem 头文件兼容；只复用正式 lifecycle patch 中该文件的构建兼容 hunk。
    & git -C $repositoryRoot apply `
        "--directory=$relativeOutput" `
        '--include=src/extension/ashmem_memfd/ashmem_memfd.c' `
        (Join-Path $repositoryRoot $patches[0])
    if ($LASTEXITCODE -ne 0) {
        throw '应用 Android ashmem 构建兼容 hunk 失败'
    }
} else {
    $lastPatch = [Array]::IndexOf($stageOrder, $Stage)
    for ($index = 0; $index -le $lastPatch; $index++) {
        & git -C $repositoryRoot apply `
            "--directory=$relativeOutput" `
            (Join-Path $repositoryRoot $patches[$index])
        if ($LASTEXITCODE -ne 0) {
            throw "应用补丁失败：$($patches[$index])"
        }
    }
}

$ndkRoot = "D:\KF\Android\Sdk\ndk\$NdkVersion"
$make = Join-Path $ndkRoot 'prebuilt\windows-x86_64\bin\make.exe'
$llvmBin = Join-Path $ndkRoot 'toolchains\llvm\prebuilt\windows-x86_64\bin'
$support = 'D:\xm\KFshell\build\external\termux-proot-verify\android-build-support'
foreach ($required in @(
    $make,
    (Join-Path $llvmBin 'aarch64-linux-android24-clang.cmd'),
    (Join-Path $llvmBin 'llvm-strip.exe'),
    (Join-Path $support 'include\talloc.h'),
    (Join-Path $support 'lib\libtalloc.so')
)) {
    if (!(Test-Path -LiteralPath $required)) {
        throw "构建依赖不存在：$required"
    }
}

$toolBin = Join-Path $output 'build-tools'
New-Item -ItemType Directory -Path $toolBin | Out-Null
Copy-Item (Join-Path $llvmBin 'llvm-readelf.exe') (Join-Path $toolBin 'readelf.exe')
$env:PATH = "$toolBin;D:\KF\git\Git\usr\bin;D:\KF\git\Git\bin;$llvmBin;" + $env:PATH

$sourceDir = Join-Path $output 'src'
$sourceUnix = $sourceDir.Replace('\', '/')
$llvmUnix = $llvmBin.Replace('\', '/')
$supportUnix = $support.Replace('\', '/')
$makeArgs = @(
    '-C', $sourceDir,
    'V=0',
    "CC=$llvmUnix/aarch64-linux-android24-clang.cmd",
    "STRIP=$llvmUnix/llvm-strip.exe",
    "OBJCOPY=$llvmUnix/llvm-objcopy.exe",
    "OBJDUMP=$llvmUnix/llvm-objdump.exe",
    "CPPFLAGS=-D_FILE_OFFSET_BITS=64 -D_GNU_SOURCE -I. -I$sourceUnix -I$supportUnix/include",
    "LDFLAGS=-L$supportUnix/lib -ltalloc -Wl,-z,noexecstack"
)
if ($UnbundleLoader) {
    $makeArgs += 'PROOT_UNBUNDLE_LOADER=/data/data/com.termux/files/usr/libexec/proot'
}

& $make @makeArgs build.h
if ($LASTEXITCODE -ne 0) {
    throw '生成 build.h 失败'
}
$buildHeader = Join-Path $sourceDir 'build.h'
$headerText = [IO.File]::ReadAllText($buildHeader)
$headerText = [Text.RegularExpressions.Regex]::Replace(
    $headerText,
    '#define VERSION "[^"]*"',
    "#define VERSION `"$BuildVersion`""
)
[IO.File]::WriteAllText($buildHeader, $headerText, [Text.UTF8Encoding]::new($false))

& $make @makeArgs -o build.h proot
if ($LASTEXITCODE -ne 0) {
    throw '构建 PRoot 失败'
}
$binary = Join-Path $sourceDir 'proot'
& (Join-Path $llvmBin 'llvm-strip.exe') $binary
if ($LASTEXITCODE -ne 0) {
    throw '剥离 PRoot 符号失败'
}

$hash = (Get-FileHash $binary -Algorithm SHA256).Hash
$size = (Get-Item $binary).Length
$formalHash = '9A599F91A089EF05AB774AC5272745A813285C791F62CFA72824BBDBABBF88F0'
if ($Stage -eq 'block-view' -and $NdkVersion -eq '26.3.11579264' -and !$UnbundleLoader -and $hash -ne $formalHash) {
    throw "正式链复现失败：期望 $formalHash，实际 $hash"
}

[pscustomobject]@{
    stage = $Stage
    ndkVersion = $NdkVersion
    unbundleLoader = [bool]$UnbundleLoader
    sourceCommit = $SourceCommit
    patchCount = if ($Stage -eq 'base') { 0 } else { [Array]::IndexOf($stageOrder, $Stage) + 1 }
    binary = $binary
    size = $size
    sha256 = $hash
}
