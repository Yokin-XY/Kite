param(
    [string]$Distro = "Ubuntu-24.04",
    [string]$Source = "native/kf-runner/kf-runner.c",
    [string]$Output = "assets/system/kf-runner-arm64",
    [string]$Version = "0.1.0"
)

$ErrorActionPreference = "Stop"

function ConvertTo-WslPath {
    param([string]$Path)
    $resolved = Resolve-Path $Path
    $fullPath = $resolved.Path
    if ($fullPath -match '^([A-Za-z]):\\(.*)$') {
        $drive = $Matches[1].ToLowerInvariant()
        $rest = $Matches[2] -replace '\\', '/'
        return "/mnt/$drive/$rest"
    }
    throw "Cannot convert path to WSL path: $fullPath"
}

function QuoteFor-Bash {
    param([string]$Value)
    return "'" + ($Value -replace "'", "'\''") + "'"
}

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$wslRepo = ConvertTo-WslPath $repoRoot.Path
$wslSource = $Source -replace '\\', '/'
$wslOutput = $Output -replace '\\', '/'
$quotedRepo = QuoteFor-Bash $wslRepo
$quotedSource = QuoteFor-Bash $wslSource
$quotedOutput = QuoteFor-Bash $wslOutput

$bash = @"
set -euo pipefail
cd $quotedRepo
if ! command -v aarch64-linux-gnu-gcc >/dev/null 2>&1; then
  echo "missing aarch64-linux-gnu-gcc" >&2
  echo "install in WSL: sudo apt-get install -y gcc-aarch64-linux-gnu libc6-dev-arm64-cross" >&2
  exit 127
fi
mkdir -p "`$(dirname $quotedOutput)"
aarch64-linux-gnu-gcc -O2 -pipe -static -s -Wall -Wextra \
  -DKF_RUNNER_VERSION=\"$Version\" \
  -o $quotedOutput $quotedSource
file $quotedOutput
readelf -h $quotedOutput | grep -E "Class|Type|Machine"
"@

Write-Host "Building kf-runner with WSL distro: $Distro"
$bash | wsl -d $Distro -- bash -s
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}
