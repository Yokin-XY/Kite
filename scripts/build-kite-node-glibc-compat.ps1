param(
    [string]$WslDistribution = "Ubuntu-24.04"
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$source = Join-Path $repoRoot "native\kite-node-host\kite-node-glibc-compat.c"
$syscallSource = Join-Path $repoRoot "native\kite-node-host\kite-node-glibc-syscall-arm64.S"
$outputDir = Join-Path $repoRoot "assets\node-runtime"
$output = Join-Path $outputDir "libkite-node-glibc-compat.so"

New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
function Convert-ToWslPath([string]$WindowsPath) {
    $fullPath = [System.IO.Path]::GetFullPath($WindowsPath)
    $drive = $fullPath.Substring(0, 1).ToLowerInvariant()
    $tail = $fullPath.Substring(2).Replace('\', '/')
    return "/mnt/$drive$tail"
}
$sourceWsl = Convert-ToWslPath $source
$syscallSourceWsl = Convert-ToWslPath $syscallSource
$outputWsl = Convert-ToWslPath $output

& wsl.exe -d $WslDistribution -- aarch64-linux-gnu-gcc `
    -O2 -fPIC -shared -Wall -Wextra -Werror `
    "-Wl,-z,relro,-z,now,-z,noexecstack" `
    -o $outputWsl $sourceWsl $syscallSourceWsl -ldl
if ($LASTEXITCODE -ne 0) {
    throw "Failed to build Kite glibc compatibility library"
}
& wsl.exe -d $WslDistribution -- aarch64-linux-gnu-strip $outputWsl
if ($LASTEXITCODE -ne 0) {
    throw "Failed to strip Kite glibc compatibility library"
}

Write-Output $output
