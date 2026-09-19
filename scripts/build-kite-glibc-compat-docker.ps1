# 交叉编译宿主 glibc 兼容层（Docker 版，替代 WSL 不可用时的 build-kite-node-glibc-compat.ps1）。
# 产出写入 assets/glibc-runtime/libkite-glibc-compat.so（随 APK 资产分发，
# 由 GlibcHostRuntimePreparer 落盘到 .kf/system/glibc-runtime/host/glibc/）。
param(
    [string]$Image = "ubuntu:24.04"
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$source = Join-Path $repoRoot "native\kite-glibc-host\kite-glibc-compat.c"
$syscallSource = Join-Path $repoRoot "native\kite-glibc-host\kite-glibc-syscall-arm64.S"
$output = Join-Path $repoRoot "assets\glibc-runtime\libkite-glibc-compat.so"

if (-not (Test-Path $source)) { throw "missing $source" }
if (-not (Test-Path $syscallSource)) { throw "missing $syscallSource" }

$repoPosix = "/" + (($repoRoot -replace '\\', '/') -replace '^([A-Za-z]):', { param($m) $m.Groups[1].Value.ToLower() })

& docker run --rm -v "${repoPosix}:/work" -w /work $Image bash -c @("
apt-get update -qq >/dev/null 2>&1
apt-get install -y -qq gcc-aarch64-linux-gnu binutils-aarch64-linux-gnu >/dev/null 2>&1
aarch64-linux-gnu-gcc -O2 -fPIC -shared -Wall -Wextra -Werror ``
  -Wl,-z,relro,-z,now,-z,noexecstack ``
  -o /tmp/libkite-glibc-compat.so native/kite-glibc-host/kite-glibc-compat.c native/kite-glibc-host/kite-glibc-syscall-arm64.S -ldl
aarch64-linux-gnu-strip /tmp/libkite-glibc-compat.so
cp /tmp/libkite-glibc-compat.so assets/glibc-runtime/libkite-glibc-compat.so
" -join "`n")
if ($LASTEXITCODE -ne 0) {
    throw "Failed to build Kite glibc compatibility library in Docker"
}

Write-Output $output
