#!/usr/bin/env bash
set +e

MODE="${1:---install}"
PACK_DIR="${KF_TOOLCHAIN_PACK_DIR:-/workspace/.kf/toolchains/ai-dev-pack}"
TOOLCHAIN_DIR="${KF_TOOLCHAIN_DIR:-/workspace/.kf/toolchains}"
BIN_DIR="${KF_TOOLCHAIN_BIN_DIR:-/workspace/.kf/bin}"
LOG_PREFIX="toolchain"
NODE_VERSION="24.15.0"
UV_VERSION="0.11.1"
PNPM_VERSION="10.33.2"
ADB_VERSION="managed-apt"

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

version_probe() {
  local seconds="$1"
  shift
  local output
  output="$(timeout -k 2s "${seconds}s" "$@" 2>&1 | head -n 1 || true)"
  if [ -n "$output" ]; then
    printf '%s' "$output"
  else
    printf '%s' "version probe timed out or produced no output"
  fi
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

repair_wrappers() {
  if ! has fd && has fdfind; then
    write_wrapper fd '#!/usr/bin/env sh
exec fdfind "$@"'
  fi
  write_wrapper systemctl '#!/usr/bin/env sh
echo "KFShell runs Android/proot without systemd. Use supervisorctl or KFShell runtime controls instead." >&2
exit 3'
  write_wrapper service '#!/usr/bin/env sh
echo "KFShell does not provide SysV/systemd service management. Use supervisorctl or KFShell runtime controls instead." >&2
exit 3'
}

install_apt_baseline() {
  if ! has apt-get; then
    emit WARN apt "apt-get missing; skip Ubuntu package completion"
    return
  fi
  local packages="python3-pip python3-venv pkg-config bzip2 less gnupg tree rsync zstd zip fd-find jq ripgrep adb fastboot"
  emit PASS apt "requested packages: $packages"
  if has dpkg-query; then
    local missing=""
    local pkg
    for pkg in $packages; do
      if ! dpkg-query -W -f='${Status}' "$pkg" 2>/dev/null | grep -q "install ok installed"; then
        missing="$missing $pkg"
      fi
    done
    if [ -z "$missing" ]; then
      emit PASS apt-install "Ubuntu package baseline already satisfied"
      return
    fi
    packages="${missing# }"
    emit WARN apt-missing "installing missing Ubuntu packages:$packages"
  fi
  apt-get update
  local update_code=$?
  if [ "$update_code" -ne 0 ]; then
    emit WARN apt-update "apt-get update failed with exitCode=$update_code; offline bundled tools will still be installed"
    return
  fi
  DEBIAN_FRONTEND=noninteractive apt-get install -y $packages
  local install_code=$?
  if [ "$install_code" -eq 0 ]; then
    emit PASS apt-install "Ubuntu package baseline installed or already present"
  else
    emit WARN apt-install "apt-get install failed with exitCode=$install_code"
  fi
}

install_node() {
  local archive="$PACK_DIR/packages/node-v$NODE_VERSION-linux-arm64.tar.xz"
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
  ln -sfn "$target/bin/node" "$BIN_DIR/node"
  ln -sfn "$target/bin/npm" "$BIN_DIR/npm"
  ln -sfn "$target/bin/npx" "$BIN_DIR/npx"
  emit PASS node-install "$(version_probe 10 "$BIN_DIR/node" --version)"
}

install_uv() {
  local archive="$PACK_DIR/packages/uv-aarch64-unknown-linux-gnu.tar.gz"
  [ -f "$archive" ] || archive="$PACK_DIR/packages/uv-aarch64-unknown-linux-gnu.tar"
  local target="$TOOLCHAIN_DIR/uv-$UV_VERSION"
  if [ ! -x "$target/uv" ]; then
    if [ ! -f "$archive" ]; then
      emit FAIL uv-package "missing bundled package: $PACK_DIR/packages/uv-aarch64-unknown-linux-gnu.tar[.gz]"
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
  emit PASS uv-install "$(version_probe 10 "$BIN_DIR/uv" --version)"
}

install_pnpm() {
  local archive="$PACK_DIR/packages/pnpm-$PNPM_VERSION.tgz"
  local target="$TOOLCHAIN_DIR/pnpm-$PNPM_VERSION"
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
  emit PASS pnpm-install "$(version_probe 10 "$BIN_DIR/pnpm" --version)"
}

is_kfshell_adb_wrapper() {
  local file="$1"
  [ -f "$file" ] || return 1
  grep -q 'ensure_host_self_connected' "$file" 2>/dev/null && grep -q 'REAL_ADB=' "$file" 2>/dev/null
}

restore_system_adb_if_wrapped() {
  local target=""
  if [ -e /usr/bin/adb ]; then
    target="$(readlink -f /usr/bin/adb 2>/dev/null || printf '%s' /usr/bin/adb)"
  fi

  if is_kfshell_adb_wrapper /usr/bin/adb || { [ -n "$target" ] && is_kfshell_adb_wrapper "$target"; }; then
    emit WARN adb-repair "system adb was overwritten by a KFShell wrapper; reinstalling Ubuntu adb package"
    if ! has apt-get; then
      emit WARN adb-repair "apt-get missing; cannot restore system adb automatically"
      return
    fi
    DEBIAN_FRONTEND=noninteractive apt-get install --reinstall -y adb
    local reinstall_code=$?
    if [ "$reinstall_code" -eq 0 ]; then
      emit PASS adb-repair "Ubuntu adb package restored"
    else
      emit WARN adb-repair "adb package reinstall failed with exitCode=$reinstall_code"
    fi
  fi
}

install_adb() {
  local adb_path=""
  local fastboot_path=""

  restore_system_adb_if_wrapped

  if [ -x /usr/bin/adb ]; then
    adb_path="/usr/bin/adb"
  elif PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/system/bin" command -v adb >/dev/null 2>&1; then
    adb_path="$(PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/system/bin" command -v adb)"
  fi

  if [ -x /usr/bin/fastboot ]; then
    fastboot_path="/usr/bin/fastboot"
  elif PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/system/bin" command -v fastboot >/dev/null 2>&1; then
    fastboot_path="$(PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/system/bin" command -v fastboot)"
  fi

  if [ -z "$adb_path" ] || [ ! -x "$adb_path" ]; then
    emit FAIL adb-install "adb binary missing after apt baseline"
    return
  fi

  local adb_target
  adb_target="$(readlink -f "$adb_path" 2>/dev/null || printf '%s' "$adb_path")"
  if is_kfshell_adb_wrapper "$adb_path" || is_kfshell_adb_wrapper "$adb_target"; then
    emit FAIL adb-install "system adb is still a KFShell wrapper after repair; refusing to create recursive wrapper"
    return
  fi

write_wrapper adb "#!/usr/bin/env sh
REAL_ADB=\"$adb_path\"

resolve_host_self_port() {
  port=\"\"
  case \"\${KF_ADB_HOST_SELF_PORT:-}\" in
    ''|0|*[!0-9]*) ;;
    *)
      printf '%s' \"\$KF_ADB_HOST_SELF_PORT\"
      return 0
      ;;
  esac
  if [ -x /system/bin/getprop ]; then
    port=\"\$(/system/bin/getprop service.adb.tls.port 2>/dev/null | tr -d '\r')\"
  elif command -v getprop >/dev/null 2>&1; then
    port=\"\$(getprop service.adb.tls.port 2>/dev/null | tr -d '\r')\"
  fi
  case \"\$port\" in
    ''|0|*[!0-9]*) return 1 ;;
  esac
  printf '%s' \"\$port\"
}

resolve_host_self_serial() {
  printf '%s' \"\${KF_ADB_HOST_SELF_SERIAL:-kf-host-self}\"
}

should_skip_autoconnect() {
  expect_value=0
  subcommand=\"\"
  for arg in \"\$@\"; do
    if [ \"\$expect_value\" -eq 1 ]; then
      expect_value=0
      continue
    fi
    case \"\$arg\" in
      --)
        break
        ;;
      -s|-t|-H|-P|-L)
        expect_value=1
        ;;
      -a|-d|-e)
        ;;
      -*)
        ;;
      *)
        subcommand=\"\$arg\"
        break
        ;;
    esac
  done
  case \"\$subcommand\" in
    ''|help|version|start-server|kill-server|server|pair|connect|disconnect|keygen|mdns)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

first_subcommand() {
  expect_value=0
  for arg in \"\$@\"; do
    if [ \"\$expect_value\" -eq 1 ]; then
      expect_value=0
      continue
    fi
    case \"\$arg\" in
      --)
        break
        ;;
      -s|-t|-H|-P|-L)
        expect_value=1
        ;;
      -a|-d|-e)
        ;;
      -*)
        ;;
      *)
        printf '%s' \"\$arg\"
        return 0
        ;;
    esac
  done
  return 1
}

selected_serial() {
  expect_serial=0
  for arg in \"\$@\"; do
    if [ \"\$expect_serial\" -eq 1 ]; then
      printf '%s' \"\$arg\"
      return 0
    fi
    case \"\$arg\" in
      -s)
        expect_serial=1
        ;;
      -s*)
        printf '%s' \"\${arg#-s}\"
        return 0
        ;;
    esac
  done
  return 1
}

is_host_self_target() {
  case \"\$1\" in
    \"\${KF_ADB_HOST_SELF_SERIAL:-kf-host-self}\"|kf-host-self|host-self-adb)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

print_devices_with_host_self() {
  echo \"List of devices attached\"
  printf '%s\tdevice\n' \"\${KF_ADB_HOST_SELF_SERIAL:-kf-host-self}\"
  if [ \"\${KF_ADB_INCLUDE_STANDARD_DEVICES:-1}\" = \"1\" ]; then
    timeout -k 2s \"\${KF_ADB_STANDARD_SCAN_TIMEOUT_SEC:-3}s\" \"\$REAL_ADB\" \"\$@\" 2>&1 \\
      | awk 'NR == 1 && /^List of devices attached/ { next } NF > 0 { print }'
  fi
}

run_adb_with_watchdog() {
  seconds=\"\$1\"
  shift
  tmp=\"\$(mktemp \"\${TMPDIR:-/tmp}/kf-adb.XXXXXX\")\" || tmp=\"/tmp/kf-adb.\$\$\"
  timed_out=\"\${tmp}.timeout\"
  \"\$REAL_ADB\" \"\$@\" >\"\$tmp\" 2>&1 &
  pid=\"\$!\"
  (
    sleep \"\$seconds\"
    if kill -0 \"\$pid\" >/dev/null 2>&1; then
      : >\"\$timed_out\"
      kill \"\$pid\" >/dev/null 2>&1 || true
      sleep 2
      kill -9 \"\$pid\" >/dev/null 2>&1 || true
      if command -v pkill >/dev/null 2>&1; then
        pkill -x adb >/dev/null 2>&1 || true
      fi
    fi
  ) &
  watchdog=\"\$!\"
  wait \"\$pid\"
  status=\"\$?\"
  kill \"\$watchdog\" >/dev/null 2>&1 || true
  cat \"\$tmp\" 2>/dev/null || true
  if [ -f \"\$timed_out\" ]; then
    rm -f \"\$tmp\" \"\$timed_out\"
    echo \"error: kfshell adb watchdog timed out: adb \$*\"
    echo \"hint: adb client is installed, but adb server startup is blocked or hanging in this container session.\"
    exit 124
  fi
  rm -f \"\$tmp\" \"\$timed_out\"
  return \"\$status\"
}

ensure_host_self_connected() {
  [ \"\${KF_ADB_HOST_SELF_AUTOCONNECT:-1}\" = \"0\" ] && return 0
  should_skip_autoconnect \"\$@\" && return 0
  serial=\"\$(resolve_host_self_serial || true)\"
  [ -n \"\$serial\" ] || return 0
  case \"\$serial\" in
    kf-host-self|host-self-adb)
      return 0
      ;;
  esac
  timeout -k 2s 5s \"\$REAL_ADB\" connect \"\$serial\" >/dev/null 2>&1 || true
}

ensure_host_self_connected \"\$@\"
subcommand=\"\$(first_subcommand \"\$@\" || true)\"
target=\"\$(selected_serial \"\$@\" || true)\"
case \"\$subcommand\" in
  devices)
    print_devices_with_host_self \"\$@\"
    exit 0
    ;;
esac
if [ -n \"\$target\" ] && is_host_self_target \"\$target\"; then
  exec kf-adb-bridge adb \"\$@\"
fi
exec \"\$REAL_ADB\" \"\$@\"
"
  if [ -n "$fastboot_path" ] && [ -x "$fastboot_path" ]; then
    ln -sfn "$fastboot_path" "$BIN_DIR/fastboot"
  else
    rm -f "$BIN_DIR/fastboot"
  fi

  adb_probe="$(version_probe 5 "$BIN_DIR/adb" version)"
  if [ -n "$adb_probe" ]; then
    emit PASS adb-install "$adb_probe"
  else
    emit WARN adb-install "managed adb installed, but version probe timed out or produced no output"
  fi
}

version_line() {
  local name="$1"
  local command_name="$2"
  local source="$3"
  if has "$command_name"; then
    local path
    path="$(command -v "$command_name")"
    local version
    if [ "$command_name" = "adb" ]; then
      version="$(version_probe 5 "$command_name" version)"
    else
      version="$(version_probe 5 "$command_name" --version)"
    fi
    emit PASS "inventory:$name" "path=$path | version=$version | source=$source"
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
  version_line pip3 pip3 "Ubuntu apt"
  version_line jq jq "Ubuntu apt"
  version_line fd fd "KFShell wrapper or fd-find"
  version_line rg rg "Ubuntu apt"
  version_line zstd zstd "Ubuntu apt"
  version_line zip zip "Ubuntu apt"
  version_line pkg-config pkg-config "Ubuntu apt"
  version_line tree tree "Ubuntu apt"
  version_line rsync rsync "Ubuntu apt"
  version_line supervisorctl supervisorctl "Ubuntu apt"
  version_line adb adb "ai-dev-pack managed adb"
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

main() {
  echo "KFSHELL_AI_DEV_PACK_BEGIN"
  echo "timestamp=$(date -u '+%Y-%m-%dT%H:%M:%SZ' 2>/dev/null)"
  ensure_dirs
  export PATH="$BIN_DIR:/root/.local/bin:$PATH"
  export UV_LINK_MODE=copy
  repair_wrappers

  case "$MODE" in
    --doctor|doctor)
      doctor
      ;;
    --install-node|install-node|node)
      install_node
      doctor_node
      ;;
    *)
      install_apt_baseline
      install_node
      install_uv
      install_pnpm
      install_adb
      repair_wrappers
      doctor
      ;;
  esac

  echo "KFSHELL_AI_DEV_PACK_END"
  [ "$FAIL" -eq 0 ] || exit 2
  exit 0
}

main "$@"
