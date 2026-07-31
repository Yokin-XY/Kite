#!/system/bin/sh

set -eu

app_root="$(pwd)"
runtime_root="$app_root/files/runtime"
proot="$runtime_root/bin/proot-view-benchmark"
proot_lib="$runtime_root/lib"
proot_tmp="$runtime_root/tmp"
fixture_root="$proot_tmp/kf-proot-view-domain-layout"
base="$fixture_root/base"
rootfs="$base/rootfs"
workspace="$base/shared/default"
managed_software="$workspace/.kf/software"
managed_bin="$workspace/.kf/bin"
upper="$fixture_root/upper"
whiteout="$fixture_root/whiteout"
control="$fixture_root/control"

run_plain() {
  script="$1"
  LD_LIBRARY_PATH="$proot_lib" PROOT_TMP_DIR="$proot_tmp" \
  HOME=/root USER=root PATH=/system/bin \
  "$proot" --link2symlink -0 -r "$rootfs" -w /workspace \
    -b /system -b /apex -b /vendor -b /dev -b /proc \
    -b "$workspace:/workspace" \
    /system/bin/sh -c "$script"
}

run_view() {
  script="$1"
  KF_PROOT_VIEW_CONTROL_PATH="$control" \
  LD_LIBRARY_PATH="$proot_lib" PROOT_TMP_DIR="$proot_tmp" \
  HOME=/root USER=root PATH=/system/bin \
  "$proot" --link2symlink -0 -r "$rootfs" -w /workspace \
    -b /system -b /apex -b /vendor -b /dev -b /proc \
    -b "$workspace:/workspace" \
    /system/bin/sh -c "$script"
}

rm -rf "$fixture_root"
mkdir -p "$rootfs/probe" "$managed_software/kite.opencode/bin" \
  "$managed_bin" "$upper" "$whiteout"
printf 'rootfs-v1\n' > "$rootfs/probe/version"
printf 'opencode-v1\n' > "$managed_software/kite.opencode/bin/version"
printf 'wrapper-v1\n' > "$managed_bin/opencode"
printf 'workspace-v1\n' > "$workspace/user.txt"
protected_hash_before="$(find "$rootfs" "$managed_software" "$managed_bin" -type f -exec sha256sum {} \; | sort | sha256sum | awk '{print $1}')"

{
  echo 'schema=kf_proot_view_v1'
  echo 'view_id=container-domain'
  echo "base_root=$base"
  echo "upper_root=$upper"
  echo "whiteout_root=$whiteout"
  echo "scope_root=$rootfs"
  echo "scope_root=$managed_software"
  echo "scope_root=$managed_bin"
  echo 'mode=read_write'
} > "$control"

run_view '
  set -eu
  test "$(cat /probe/version)" = rootfs-v1
  test "$(cat /workspace/.kf/software/kite.opencode/bin/version)" = opencode-v1
  test "$(cat /workspace/.kf/bin/opencode)" = wrapper-v1
  test "$(cat /workspace/user.txt)" = workspace-v1
  printf "rootfs-v2\n" > /probe/version
  printf "opencode-v2\n" > /workspace/.kf/software/kite.opencode/bin/version
  printf "wrapper-v2\n" > /workspace/.kf/bin/opencode
  printf "workspace-v2\n" > /workspace/user.txt
'

protected_hash_after="$(find "$rootfs" "$managed_software" "$managed_bin" -type f -exec sha256sum {} \; | sort | sha256sum | awk '{print $1}')"
test "$protected_hash_before" = "$protected_hash_after"
test "$(cat "$rootfs/probe/version")" = rootfs-v1
test "$(cat "$managed_software/kite.opencode/bin/version")" = opencode-v1
test "$(cat "$managed_bin/opencode")" = wrapper-v1
test "$(cat "$workspace/user.txt")" = workspace-v2
test "$(cat "$upper/rootfs/probe/version")" = rootfs-v2
test "$(cat "$upper/shared/default/.kf/software/kite.opencode/bin/version")" = opencode-v2
test "$(cat "$upper/shared/default/.kf/bin/opencode")" = wrapper-v2

run_plain '
  set -eu
  test "$(cat /probe/version)" = rootfs-v1
  test "$(cat /workspace/.kf/software/kite.opencode/bin/version)" = opencode-v1
  test "$(cat /workspace/.kf/bin/opencode)" = wrapper-v1
  test "$(cat /workspace/user.txt)" = workspace-v2
'

run_view '
  set -eu
  test "$(cat /probe/version)" = rootfs-v2
  test "$(cat /workspace/.kf/software/kite.opencode/bin/version)" = opencode-v2
  test "$(cat /workspace/.kf/bin/opencode)" = wrapper-v2
  test "$(cat /workspace/user.txt)" = workspace-v2
'

printf 'meta\tbase_domain\t%s\n' "$base"
printf 'meta\tpersistent_workspace\t%s\n' "$workspace"
printf 'meta\tprotected_hash\t%s\n' "$protected_hash_before"
printf 'meta\tscope_roots\trootfs,software,bin\n'
printf 'result\tDOMAIN_LAYOUT_FIXTURE_OK\n'
