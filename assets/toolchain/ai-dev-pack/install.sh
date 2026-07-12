#!/usr/bin/env bash
set +e

MODE="${1:---install}"
PACK_DIR="${KF_TOOLCHAIN_PACK_DIR:-/workspace/.kf/toolchains/ai-dev-pack}"
TOOLCHAIN_DIR="${KF_TOOLCHAIN_DIR:-/workspace/.kf/toolchains}"
BIN_DIR="${KF_TOOLCHAIN_BIN_DIR:-/workspace/.kf/bin}"
LOG_PREFIX="toolchain"
NODE_VERSION="26.4.0"
UV_VERSION="0.11.25"
PNPM_VERSION="11.9.0"
PYTHON_VERSION="3.14.6"
PYTHON_BUILD_TAG="20260623"
ADB_VERSION="rootfs"
LIBATOMIC_VERSION="1.2.0"

PASS=0
WARN=0
FAIL=0

emit() {
  local level="$1"
  local key="$2"
  shift 2
  printf '%s\t%s\t%s\n' "$level" "$key" "$*"
  case "$level" in
    PASS) PASS=$((PASS + 1)) ;;
    WARN) WARN=$((WARN + 1)) ;;
    FAIL) FAIL=$((FAIL + 1)) ;;
  esac
}

has() {
  command -v "$1" >/dev/null 2>&1
}

ensure_dirs() {
  mkdir -p "$TOOLCHAIN_DIR" "$BIN_DIR" /root/.local/bin /root/.cache/pip /root/.cache/uv
cat > "$TOOLCHAIN_DIR/env.sh" <<'EOF'
export UV_LINK_MODE=copy
export TERM="${TERM:-xterm-256color}"
export COLORTERM="${COLORTERM:-truecolor}"
export FORCE_COLOR="${FORCE_COLOR:-3}"
export CLICOLOR_FORCE="${CLICOLOR_FORCE:-1}"
case ":$PATH:" in
  *":/workspace/.kf/bin:"*) ;;
  *) export PATH="/workspace/.kf/bin:$PATH" ;;
esac
case ":$PATH:" in
  *":/root/.local/bin:"*) ;;
  *) export PATH="/root/.local/bin:$PATH" ;;
esac
EOF
}

write_wrapper() {
  local name="$1"
  local body="$2"
  rm -f "$BIN_DIR/$name"
  printf '%s\n' "$body" > "$BIN_DIR/$name"
  chmod +x "$BIN_DIR/$name"
}

write_node_wrapper() {
  local name="$1"
  local target="$2"
  local lib_dir="$3"
  write_wrapper "$name" "#!/usr/bin/env sh
export LD_LIBRARY_PATH=\"$lib_dir\${LD_LIBRARY_PATH:+:\$LD_LIBRARY_PATH}\"
exec \"$target\" \"\$@\""
}

repair_wrappers() {
  if ! has fd && has fdfind; then
    write_wrapper fd '#!/usr/bin/env sh
exec fdfind "$@"'
  fi
  write_wrapper systemctl '#!/usr/bin/env sh
echo "KFShell runs Android/proot without systemd. Use KFShell runtime controls instead." >&2
exit 3'
  write_wrapper service '#!/usr/bin/env sh
echo "KFShell does not provide SysV/systemd service management. Use KFShell runtime controls instead." >&2
exit 3'
}

install_python() {
  local archive="$PACK_DIR/packages/cpython-$PYTHON_VERSION+$PYTHON_BUILD_TAG-aarch64-unknown-linux-gnu-install_only_stripped.tgz"
  local tar_flags="-xzf"
  if [ ! -f "$archive" ]; then
    archive="$PACK_DIR/packages/cpython-$PYTHON_VERSION+$PYTHON_BUILD_TAG-aarch64-unknown-linux-gnu-install_only_stripped.tar"
    tar_flags="-xf"
  fi
  local target="$TOOLCHAIN_DIR/python-$PYTHON_VERSION"
  if [ ! -x "$target/bin/python3.14" ]; then
    if [ ! -f "$archive" ]; then
      emit FAIL python-package "missing bundled package: $PACK_DIR/packages/cpython-$PYTHON_VERSION+$PYTHON_BUILD_TAG-aarch64-unknown-linux-gnu-install_only_stripped.tar or .tgz"
      return
    fi
    rm -rf "$target.tmp" "$target"
    mkdir -p "$target.tmp" "$target"
    tar "$tar_flags" "$archive" -C "$target.tmp"
    local tar_code=$?
    if [ "$tar_code" -ne 0 ]; then
      rm -rf "$target.tmp" "$target"
      emit FAIL python-install "extract failed with exitCode=$tar_code"
      return
    fi
    local pybin
    pybin="$(find "$target.tmp" -type f -name python3.14 | head -n 1)"
    if [ -z "$pybin" ]; then
      rm -rf "$target.tmp" "$target"
      emit FAIL python-install "python3.14 binary not found after extract"
      return
    fi
    local pydir
    pydir="$(cd "$(dirname "$pybin")/.." && pwd)"
    cp -a "$pydir"/. "$target"/
    rm -rf "$target.tmp"
  fi
  ln -sfn "$target/bin/python3.14" "$BIN_DIR/python3.14"
  ln -sfn "$target/bin/python3.14" "$BIN_DIR/python3"
  ln -sfn "$target/bin/python3.14" "$BIN_DIR/python"
  if [ -x "$target/bin/pip3" ]; then
    ln -sfn "$target/bin/pip3" "$BIN_DIR/pip3"
    ln -sfn "$target/bin/pip3" "$BIN_DIR/pip"
  fi
  emit PASS python-install "python $PYTHON_VERSION installed"
}

install_node() {
  local archive="$PACK_DIR/packages/node-v$NODE_VERSION-linux-arm64.tar.xz"
  local libatomic="$PACK_DIR/packages/libatomic.so.$LIBATOMIC_VERSION"
  local target="$TOOLCHAIN_DIR/node-v$NODE_VERSION"
  if [ ! -x "$target/bin/node" ]; then
    if [ ! -f "$archive" ]; then
      emit FAIL node-package "missing bundled package: $archive"
      return
    fi
    rm -rf "$target.tmp" "$target"
    mkdir -p "$target.tmp"
    tar -xJf "$archive" -C "$target.tmp" --strip-components=1
    local tar_code=$?
    if [ "$tar_code" -ne 0 ]; then
      rm -rf "$target.tmp"
      emit FAIL node-install "extract failed with exitCode=$tar_code"
      return
    fi
    mv "$target.tmp" "$target"
  fi
  if [ ! -f "$libatomic" ]; then
    emit FAIL node-libatomic "missing bundled package: $libatomic"
    return
  fi
  mkdir -p "$target/lib"
  cp "$libatomic" "$target/lib/libatomic.so.$LIBATOMIC_VERSION"
  local cp_code=$?
  if [ "$cp_code" -ne 0 ]; then
    emit FAIL node-libatomic "copy failed with exitCode=$cp_code"
    return
  fi
  ln -sfn "libatomic.so.$LIBATOMIC_VERSION" "$target/lib/libatomic.so.1"
  write_node_wrapper node "$target/bin/node" "$target/lib"
  write_node_wrapper npm "$target/bin/npm" "$target/lib"
  write_node_wrapper npx "$target/bin/npx" "$target/lib"
  emit PASS node-install "node $NODE_VERSION installed"
}

install_uv() {
  local archive="$PACK_DIR/packages/uv-aarch64-unknown-linux-gnu.tgz"
  [ -f "$archive" ] || archive="$PACK_DIR/packages/uv-aarch64-unknown-linux-gnu.tar"
  local target="$TOOLCHAIN_DIR/uv-$UV_VERSION"
  if [ ! -x "$target/uv" ]; then
    if [ ! -f "$archive" ]; then
      emit FAIL uv-package "missing bundled package: $PACK_DIR/packages/uv-aarch64-unknown-linux-gnu.tar or .tgz"
      return
    fi
    rm -rf "$target.tmp" "$target"
    mkdir -p "$target.tmp"
    tar -xf "$archive" -C "$target.tmp"
    local tar_code=$?
    if [ "$tar_code" -ne 0 ]; then
      rm -rf "$target.tmp"
      emit FAIL uv-install "extract failed with exitCode=$tar_code"
      return
    fi
    local uv_bin
    uv_bin="$(find "$target.tmp" -type f -name uv | head -n 1)"
    local uvx_bin
    uvx_bin="$(find "$target.tmp" -type f -name uvx | head -n 1)"
    if [ -z "$uv_bin" ]; then
      rm -rf "$target.tmp"
      emit FAIL uv-install "uv binary not found after extract"
      return
    fi
    mkdir -p "$target"
    cp "$uv_bin" "$target/uv"
    chmod +x "$target/uv"
    if [ -n "$uvx_bin" ]; then
      cp "$uvx_bin" "$target/uvx"
      chmod +x "$target/uvx"
    fi
    rm -rf "$target.tmp"
  fi
  ln -sfn "$target/uv" "$BIN_DIR/uv"
  [ -x "$target/uvx" ] && ln -sfn "$target/uvx" "$BIN_DIR/uvx"
  emit PASS uv-install "uv $UV_VERSION installed"
}

install_pnpm() {
  local archive="$PACK_DIR/packages/pnpm-$PNPM_VERSION.tgz"
  local target="$TOOLCHAIN_DIR/pnpm-$PNPM_VERSION"
  if ! has node; then
    emit FAIL pnpm-install "node command missing; install kite.nodejs first"
    return
  fi
  if [ ! -f "$target/package/bin/pnpm.cjs" ]; then
    if [ ! -f "$archive" ]; then
      emit FAIL pnpm-package "missing bundled package: $archive"
      return
    fi
    rm -rf "$target.tmp" "$target"
    mkdir -p "$target.tmp"
    tar -xzf "$archive" -C "$target.tmp"
    local tar_code=$?
    if [ "$tar_code" -ne 0 ]; then
      rm -rf "$target.tmp"
      emit FAIL pnpm-install "extract failed with exitCode=$tar_code"
      return
    fi
    mv "$target.tmp" "$target"
  fi
  write_wrapper pnpm "#!/usr/bin/env sh
exec node \"$target/package/bin/pnpm.cjs\" \"\$@\""
  write_wrapper pnpx "#!/usr/bin/env sh
exec node \"$target/package/bin/pnpx.cjs\" \"\$@\""
  emit PASS pnpm-install "pnpm $PNPM_VERSION installed"
}

rootfs_command_path() {
  local name="$1"
  local lookup="$name"
  if [ "$name" = "fd" ] && ! PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/system/bin" command -v fd >/dev/null 2>&1; then
    lookup="fdfind"
  fi
  PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/system/bin" command -v "$lookup" 2>/dev/null
}

install_rootfs_command() {
  local name="$1"
  local required="${2:-required}"
  local real
  real="$(rootfs_command_path "$name" || true)"
  if [ -z "$real" ] || [ ! -x "$real" ]; then
    if [ "$required" = "optional" ]; then
      emit WARN "$name-install" "optional rootfs command missing: $name"
    else
      emit FAIL "$name-install" "missing rootfs command: $name"
    fi
    return
  fi
  mkdir -p "$TOOLCHAIN_DIR/bin" "$BIN_DIR"
  if [ "$name" = "fd" ] && [ "$(basename "$real")" = "fdfind" ]; then
    rm -f "$TOOLCHAIN_DIR/bin/$name"
    printf '%s\n' "#!/usr/bin/env sh
exec \"$real\" \"\$@\"" > "$TOOLCHAIN_DIR/bin/$name"
    chmod +x "$TOOLCHAIN_DIR/bin/$name"
  else
    ln -sfn "$real" "$TOOLCHAIN_DIR/bin/$name"
  fi
  ln -sfn "$TOOLCHAIN_DIR/bin/$name" "$BIN_DIR/$name"
  emit PASS "$name-install" "$name linked from $real"
}

install_system_tools() {
  install_pnpm
  local required_commands="wget jq rg fd zip"
  local optional_commands="unzip zstd file tar gzip gunzip xz unxz bzip2 bunzip2 ps pgrep pkill pidof top free ip ss netstat ping dig nslookup host update-ca-certificates less tree rsync patch sed awk grep find xargs sort uniq head tail cut tr wc tee env which whoami id uname date sleep timeout kill sha256sum sha1sum md5sum base64 chmod chown chgrp ln readlink realpath mkdir rmdir rm cp mv touch du df stat"
  local command_name
  for command_name in $required_commands; do
    install_rootfs_command "$command_name"
  done
  for command_name in $optional_commands; do
    install_rootfs_command "$command_name" optional
  done
  repair_wrappers
}

version_line() {
  local name="$1"
  local command_name="$2"
  local source="$3"
  if has "$command_name"; then
    local path
    path="$(command -v "$command_name")"
    local probe_output
    if [ "$command_name" = "adb" ]; then
      probe_output="$(timeout -k 2s 5s "$command_name" version 2>&1)"
    else
      probe_output="$(timeout -k 2s 5s "$command_name" --version 2>&1)"
    fi
    local probe_code="$?"
    local version
    version="$(printf '%s\n' "$probe_output" | head -n 1)"
    if [ "$probe_code" -eq 0 ] && [ -n "$version" ]; then
      emit PASS "inventory:$name" "path=$path | version=$version | source=$source"
    else
      emit FAIL "inventory:$name" "path=$path | version=${version:-no output} | exitCode=$probe_code | source=$source"
    fi
  else
    emit WARN "inventory:$name" "missing | source=$source"
  fi
}

doctor() {
  export PATH="$BIN_DIR:/root/.local/bin:$PATH"
  export UV_LINK_MODE=copy
  echo "KFSHELL_AI_DEV_PACK_DOCTOR_BEGIN"
  echo "mode=$MODE"
  echo "pack_dir=$PACK_DIR"
  echo "toolchain_dir=$TOOLCHAIN_DIR"
  echo "bin_dir=$BIN_DIR"
  echo "UV_LINK_MODE=$UV_LINK_MODE"
  echo "PATH=$PATH"
  version_line node node "ai-dev-pack"
  version_line npm npm "ai-dev-pack"
  version_line npx npx "ai-dev-pack"
  version_line uv uv "ai-dev-pack"
  version_line uvx uvx "ai-dev-pack"
  version_line pnpm pnpm "ai-dev-pack"
  version_line python3 python3 "Ubuntu/rootfs"
  version_line pip3 pip3 "offline rootfs"
  version_line jq jq "offline rootfs"
  version_line fd fd "KFShell wrapper or fd-find"
  version_line rg rg "offline rootfs"
  version_line zstd zstd "offline rootfs"
  version_line zip zip "offline rootfs"
  version_line pkg-config pkg-config "offline rootfs"
  version_line tree tree "offline rootfs"
  version_line rsync rsync "offline rootfs"
  version_line supervisorctl supervisorctl "offline rootfs"
  echo "SUMMARY PASS=$PASS WARN=$WARN FAIL=$FAIL"
  echo "KFSHELL_AI_DEV_PACK_DOCTOR_END"
}

doctor_node() {
  export PATH="$BIN_DIR:/root/.local/bin:$PATH"
  echo "KFSHELL_NODE_RESOURCE_DOCTOR_BEGIN"
  echo "mode=$MODE"
  echo "pack_dir=$PACK_DIR"
  echo "toolchain_dir=$TOOLCHAIN_DIR"
  echo "bin_dir=$BIN_DIR"
  echo "PATH=$PATH"
  version_line node node "ai-dev-pack node resource"
  version_line npm npm "ai-dev-pack node resource"
  version_line npx npx "ai-dev-pack node resource"
  echo "SUMMARY PASS=$PASS WARN=$WARN FAIL=$FAIL"
  echo "KFSHELL_NODE_RESOURCE_DOCTOR_END"
}

doctor_uv() {
  export PATH="$BIN_DIR:/root/.local/bin:$PATH"
  echo "KFSHELL_UV_RESOURCE_DOCTOR_BEGIN"
  echo "mode=$MODE"
  echo "pack_dir=$PACK_DIR"
  echo "toolchain_dir=$TOOLCHAIN_DIR"
  echo "bin_dir=$BIN_DIR"
  echo "PATH=$PATH"
  version_line uv uv "ai-dev-pack uv resource"
  version_line uvx uvx "ai-dev-pack uv resource"
  echo "SUMMARY PASS=$PASS WARN=$WARN FAIL=$FAIL"
  echo "KFSHELL_UV_RESOURCE_DOCTOR_END"
}

main() {
  echo "KFSHELL_AI_DEV_PACK_BEGIN"
  echo "timestamp=$(date -u '+%Y-%m-%dT%H:%M:%SZ' 2>/dev/null)"
  ensure_dirs
  export PATH="$BIN_DIR:/root/.local/bin:$PATH"
  export UV_LINK_MODE=copy

  case "$MODE" in
    --doctor|doctor)
      doctor
      ;;
    --install-node|install-node|node)
      install_node
      doctor_node
      ;;
    --install-uv|install-uv|uv)
      install_uv
      doctor_uv
      ;;
    --install-python|install-python|python)
      install_python
      ;;
    --install-pnpm|install-pnpm|pnpm)
      install_pnpm
      ;;
    --install-git|install-git|git)
      install_rootfs_command git
      ;;
    --install-curl|install-curl|curl)
      install_rootfs_command curl
      ;;
    --install-system-tools|install-system-tools|system-tools)
      install_system_tools
      ;;
    *)
      install_python
      install_node
      install_uv
      install_system_tools
      ;;
  esac

  echo "SUMMARY PASS=$PASS WARN=$WARN FAIL=$FAIL"
  echo "KFSHELL_AI_DEV_PACK_END"
  [ "$FAIL" -eq 0 ] || exit 2
  exit 0
}

main "$@"
