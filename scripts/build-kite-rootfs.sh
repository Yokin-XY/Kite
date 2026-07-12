#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK_DIR="${KITE_ROOTFS_WORK_DIR:-/tmp/kite-rootfs-build}"
CACHE_DIR="${KITE_ROOTFS_CACHE_DIR:-$WORK_DIR/cache}"
ROOTFS_DIR="$WORK_DIR/rootfs"
OUT_FILE="$REPO_ROOT/app/src/main/assets/rootfs/ubuntu-base-24.04-arm64.tgz"

CODENAME="${KITE_ROOTFS_CODENAME:-noble}"
if [ -n "${KITE_UBUNTU_PORTS_MIRROR:-}" ]; then
  MIRROR_CANDIDATES=("$KITE_UBUNTU_PORTS_MIRROR")
else
  MIRROR_CANDIDATES=(
    "https://mirrors.aliyun.com/ubuntu-ports"
    "https://mirrors.ustc.edu.cn/ubuntu-ports"
    "https://mirrors.tuna.tsinghua.edu.cn/ubuntu-ports"
    "https://mirror.nju.edu.cn/ubuntu-ports"
    "http://ports.ubuntu.com/ubuntu-ports"
  )
fi
MIRROR="${MIRROR_CANDIDATES[0]}"
HOST_PROXY="${KITE_APT_PROXY:-${http_proxy:-${HTTP_PROXY:-}}}"
APT_PROXY=""
APT_PACKAGES=(
  bash ca-certificates coreutils findutils sed grep tar xz-utils unzip zip zstd file
  curl wget git jq ripgrep fd-find procps iproute2 net-tools dnsutils
  adb fastboot
)
SUDO=()

need() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "missing host tool: $1" >&2
    exit 127
  }
}

init_privilege() {
  if [ "$(id -u)" -eq 0 ]; then
    SUDO=()
    return
  fi
  need sudo
  sudo -n -v || {
    echo "sudo password required; rerun with an active sudo session or WSL root" >&2
    exit 1
  }
  while true; do sudo -n true; sleep 30; kill -0 "$$" || exit; done 2>/dev/null &
}

as_root() {
  "${SUDO[@]}" "$@"
}

as_root_without_host_proxy() {
  "${SUDO[@]}" env -u http_proxy -u https_proxy -u HTTP_PROXY -u HTTPS_PROXY "$@"
}

as_root_with_optional_proxy() {
  if [ -n "$APT_PROXY" ]; then
    "${SUDO[@]}" env http_proxy="$APT_PROXY" https_proxy="$APT_PROXY" "$@"
  else
    as_root_without_host_proxy "$@"
  fi
}

download() {
  local url="$1"
  local file="$2"
  mkdir -p "$(dirname "$file")"
  if [ -s "$file" ]; then
    return
  fi
  curl -fL --retry 3 --retry-delay 2 "$url" -o "$file"
}

select_network() {
  local candidate test_url
  for candidate in "${MIRROR_CANDIDATES[@]}"; do
    test_url="$candidate/dists/$CODENAME/Release"
    if [ -n "$HOST_PROXY" ] &&
      timeout 10 env http_proxy="$HOST_PROXY" https_proxy="$HOST_PROXY" \
        curl -fsSL --connect-timeout 5 "$test_url" -o /dev/null; then
      MIRROR="$candidate"
      APT_PROXY="$HOST_PROXY"
      echo "using apt mirror with proxy: $MIRROR"
      return
    fi
    if timeout 10 env -u http_proxy -u https_proxy -u HTTP_PROXY -u HTTPS_PROXY \
      curl -fsSL --connect-timeout 5 "$test_url" -o /dev/null; then
      MIRROR="$candidate"
      APT_PROXY=""
      echo "using apt mirror direct: $MIRROR"
      return
    fi
  done
  echo "Ubuntu mirrors unreachable through proxy and direct network" >&2
  exit 1
}

bind_mounts() {
  as_root mount -t proc proc "$ROOTFS_DIR/proc"
  as_root mount --rbind /sys "$ROOTFS_DIR/sys"
  as_root mount --make-rslave "$ROOTFS_DIR/sys"
  as_root mount --rbind /dev "$ROOTFS_DIR/dev"
  as_root mount --make-rslave "$ROOTFS_DIR/dev"
}

unbind_mounts() {
  as_root umount -R "$ROOTFS_DIR/dev" 2>/dev/null || true
  as_root umount -R "$ROOTFS_DIR/sys" 2>/dev/null || true
  as_root umount -R "$ROOTFS_DIR/proc" 2>/dev/null || true
}

write_manifest() {
  as_root tee "$ROOTFS_DIR/etc/kite-rootfs-release" >/dev/null <<EOF
kite_rootfs_version=24.04-kite-offline-20260627
ubuntu_codename=$CODENAME
apt_packages=${APT_PACKAGES[*]}
network_required_at_phone_boot=false
EOF
}

package_rootfs() {
  as_root rm -f "$OUT_FILE.tmp"
  as_root tar --numeric-owner --xattrs --acls -C "$ROOTFS_DIR" -czf "$OUT_FILE.tmp" .
  as_root chown "$(id -u):$(id -g)" "$OUT_FILE.tmp"
  mv "$OUT_FILE.tmp" "$OUT_FILE"
}

main() {
  need curl
  need debootstrap
  need qemu-aarch64-static
  init_privilege
  select_network
  trap unbind_mounts EXIT

  rm -rf "$ROOTFS_DIR"
  mkdir -p "$WORK_DIR" "$CACHE_DIR"
  as_root_with_optional_proxy debootstrap --arch=arm64 --foreign --variant=minbase \
    --components=main,restricted,universe,multiverse "$CODENAME" "$ROOTFS_DIR" "$MIRROR"
  as_root cp "$(command -v qemu-aarch64-static)" "$ROOTFS_DIR/usr/bin/"
  as_root chroot "$ROOTFS_DIR" /debootstrap/debootstrap --second-stage
  as_root tee "$ROOTFS_DIR/etc/apt/sources.list" >/dev/null <<EOF
deb $MIRROR $CODENAME main restricted universe multiverse
deb $MIRROR $CODENAME-updates main restricted universe multiverse
deb $MIRROR $CODENAME-backports main restricted universe multiverse
deb $MIRROR $CODENAME-security main restricted universe multiverse
EOF
  if [ -n "$APT_PROXY" ]; then
    as_root tee "$ROOTFS_DIR/etc/apt/apt.conf.d/99kite-proxy" >/dev/null <<EOF
Acquire::http::Proxy "$APT_PROXY";
Acquire::https::Proxy "$APT_PROXY";
EOF
  fi
  bind_mounts
  as_root_without_host_proxy chroot "$ROOTFS_DIR" apt-get -o Acquire::Retries=3 update
  as_root_without_host_proxy chroot "$ROOTFS_DIR" env DEBIAN_FRONTEND=noninteractive apt-get -o Acquire::Retries=3 install -y --no-install-recommends "${APT_PACKAGES[@]}"
  write_manifest
  as_root chroot "$ROOTFS_DIR" apt-get clean
  as_root rm -f "$ROOTFS_DIR/etc/apt/apt.conf.d/99kite-proxy"
  as_root rm -rf "$ROOTFS_DIR/var/lib/apt/lists/"* "$ROOTFS_DIR/tmp/"* "$ROOTFS_DIR/var/tmp/"*
  as_root rm -f "$ROOTFS_DIR/usr/bin/qemu-aarch64-static"
  unbind_mounts
  package_rootfs
  ls -lh "$OUT_FILE"
}

main "$@"
