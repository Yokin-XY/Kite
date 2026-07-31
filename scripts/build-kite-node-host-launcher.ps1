param(
    [string]$NdkRoot = "D:\KF\Android\Sdk\ndk\26.3.11579264"
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$source = Join-Path $repoRoot "native\kite-node-host\kite-node-host-launcher.c"
$outputDir = Join-Path $repoRoot "assets\node-runtime"
$output = Join-Path $outputDir "kite-node-host-launcher-arm64"
$compiler = Join-Path $NdkRoot "toolchains\llvm\prebuilt\windows-x86_64\bin\aarch64-linux-android28-clang.cmd"
$strip = Join-Path $NdkRoot "toolchains\llvm\prebuilt\windows-x86_64\bin\llvm-strip.exe"

if (-not (Test-Path -LiteralPath $compiler)) {
    throw "Android NDK compiler not found: $compiler"
}

New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
& $compiler -O2 -fPIE -pie -Wall -Wextra -Werror "-Wl,-z,noexecstack" -o $output $source
if ($LASTEXITCODE -ne 0) {
    throw "Failed to build Kite host Node launcher"
}
& $strip $output
if ($LASTEXITCODE -ne 0) {
    throw "Failed to strip Kite host Node launcher"
}

Write-Output $output
