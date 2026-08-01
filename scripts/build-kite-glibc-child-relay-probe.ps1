param(
    [string]$WslDistribution = "Ubuntu-24.04"
)

$ErrorActionPreference = "Stop"
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$sourceRoot = Join-Path $repositoryRoot "native\kite-glibc-child-relay"
$outputRoot = Join-Path $repositoryRoot "local-artifacts\glibc-child-relay-rf1320"
$relaySource = Join-Path $sourceRoot "kite-glibc-child-relay.c"
$probeSource = Join-Path $sourceRoot "kite-glibc-child-probe.c"
$relayOutput = Join-Path $outputRoot "libkite-glibc-child-relay.so"
$probeOutput = Join-Path $outputRoot "kite-glibc-child-probe"

New-Item -ItemType Directory -Path $outputRoot -Force | Out-Null

function Convert-ToWslPath([string]$WindowsPath) {
    $fullPath = [System.IO.Path]::GetFullPath($WindowsPath)
    $drive = $fullPath.Substring(0, 1).ToLowerInvariant()
    $tail = $fullPath.Substring(2).Replace('\', '/')
    return "/mnt/$drive$tail"
}

$relaySourceWsl = Convert-ToWslPath $relaySource
$probeSourceWsl = Convert-ToWslPath $probeSource
$relayOutputWsl = Convert-ToWslPath $relayOutput
$probeOutputWsl = Convert-ToWslPath $probeOutput

& wsl.exe -d $WslDistribution -- aarch64-linux-gnu-gcc `
    -O2 -fPIC -shared -Wall -Wextra -Werror `
    "-Wl,-z,relro,-z,now,-z,noexecstack" `
    -o $relayOutputWsl $relaySourceWsl -ldl
if ($LASTEXITCODE -ne 0) { throw "Failed to build RF1320 child relay" }

& wsl.exe -d $WslDistribution -- aarch64-linux-gnu-gcc `
    -O2 -fPIE -pie -Wall -Wextra -Werror `
    "-Wl,-z,relro,-z,now,-z,noexecstack" `
    -o $probeOutputWsl $probeSourceWsl
if ($LASTEXITCODE -ne 0) { throw "Failed to build RF1320 child probe" }

& wsl.exe -d $WslDistribution -- aarch64-linux-gnu-strip $relayOutputWsl $probeOutputWsl
if ($LASTEXITCODE -ne 0) { throw "Failed to strip RF1320 assets" }

Get-Item -LiteralPath $relayOutput, $probeOutput | Select-Object FullName, Length, LastWriteTime
