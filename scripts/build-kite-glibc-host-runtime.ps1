param(
    [string]$NdkRoot = "D:\KF\Android\Sdk\ndk\26.3.11579264",
    [string]$WslDistribution = "Ubuntu-24.04"
)

$ErrorActionPreference = "Stop"
$runtimeRepoRoot = Split-Path -Parent $PSScriptRoot
$nativeRoot = Join-Path $runtimeRepoRoot "native\kite-glibc-host"
$launcherSource = Join-Path $nativeRoot "kite-glibc-host-launcher.c"
$compatSource = Join-Path $nativeRoot "kite-glibc-compat.c"
$syscallSource = Join-Path $nativeRoot "kite-glibc-syscall-arm64.S"
$outputDir = Join-Path $runtimeRepoRoot "assets\glibc-runtime"
$launcherOutput = Join-Path $outputDir "kite-glibc-host-launcher-arm64"
$compatOutput = Join-Path $outputDir "libkite-glibc-compat.so"
$compiler = Join-Path $NdkRoot "toolchains\llvm\prebuilt\windows-x86_64\bin\aarch64-linux-android28-clang.cmd"
$strip = Join-Path $NdkRoot "toolchains\llvm\prebuilt\windows-x86_64\bin\llvm-strip.exe"

if (-not (Test-Path -LiteralPath $compiler)) { throw "Android NDK compiler not found: $compiler" }
New-Item -ItemType Directory -Path $outputDir -Force | Out-Null

& $compiler -O2 -fPIE -pie -Wall -Wextra -Werror "-Wl,-z,noexecstack" -o $launcherOutput $launcherSource
if ($LASTEXITCODE -ne 0) { throw "Failed to build Kite glibc host launcher" }
& $strip $launcherOutput
if ($LASTEXITCODE -ne 0) { throw "Failed to strip Kite glibc host launcher" }

function Convert-ToWslPath([string]$WindowsPath) {
    $fullPath = [System.IO.Path]::GetFullPath($WindowsPath)
    $drive = $fullPath.Substring(0, 1).ToLowerInvariant()
    $tail = $fullPath.Substring(2).Replace('\', '/')
    return "/mnt/$drive$tail"
}

$compatSourceWsl = Convert-ToWslPath $compatSource
$syscallSourceWsl = Convert-ToWslPath $syscallSource
$compatOutputWsl = Convert-ToWslPath $compatOutput
& wsl.exe -d $WslDistribution -- aarch64-linux-gnu-gcc `
    -O2 -fPIC -shared -Wall -Wextra -Werror `
    "-Wl,-z,relro,-z,now,-z,noexecstack" `
    -o $compatOutputWsl $compatSourceWsl $syscallSourceWsl -ldl
if ($LASTEXITCODE -ne 0) { throw "Failed to build Kite glibc compatibility library" }
& wsl.exe -d $WslDistribution -- aarch64-linux-gnu-strip $compatOutputWsl
if ($LASTEXITCODE -ne 0) { throw "Failed to strip Kite glibc compatibility library" }

Write-Output $launcherOutput
Write-Output $compatOutput
