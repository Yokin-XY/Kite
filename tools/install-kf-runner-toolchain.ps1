param(
    [string]$Distro = "Ubuntu-24.04",
    [string]$Proxy = "http://127.0.0.1:7897"
)

$ErrorActionPreference = "Stop"

$bash = @"
set -euo pipefail
proxy="$Proxy"
apt-get \
  -o Acquire::http::Proxy="`$proxy" \
  -o Acquire::https::Proxy="`$proxy" \
  -o Acquire::Retries=1 \
  -o Acquire::http::Timeout=30 \
  update
DEBIAN_FRONTEND=noninteractive apt-get \
  -o Acquire::http::Proxy="`$proxy" \
  -o Acquire::https::Proxy="`$proxy" \
  -o Acquire::Retries=1 \
  -o Acquire::http::Timeout=30 \
  install -y gcc-aarch64-linux-gnu libc6-dev-arm64-cross
aarch64-linux-gnu-gcc --version | head -1
"@

Write-Host "Installing kf-runner WSL toolchain in distro: $Distro"
Write-Host "Using proxy: $Proxy"
$bash | wsl -d $Distro -u root -- bash -s
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}
