#!/system/bin/sh

set -eu

app_root="$(pwd)"
runtime_root="$app_root/files/runtime"
proot="$runtime_root/bin/proot-view-benchmark"
proot_lib="$runtime_root/lib"
proot_tmp="$runtime_root/tmp"
fixture_root="$proot_tmp/kf-proot-view-scoped-bind"
rootfs="$fixture_root/rootfs"
managed_base="$fixture_root/managed-base"
upper="$fixture_root/upper"
whiteout="$fixture_root/whiteout"
control="$fixture_root/control"
managed_mount="/workspace/.kf/software/kite.opencode"

run_plain() {
  script="$1"
  LD_LIBRARY_PATH="$proot_lib" PROOT_TMP_DIR="$proot_tmp" \
  HOME=/root USER=root PATH=/system/bin \
  "$proot" --link2symlink -0 -r "$rootfs" -w "$managed_mount" \
    -b /system -b /apex -b /vendor -b /dev -b /proc \
    -b "$managed_base:$managed_mount" \
    /system/bin/sh -c "$script"
}

run_view() {
  script="$1"
  KF_PROOT_VIEW_CONTROL_PATH="$control" \
  LD_LIBRARY_PATH="$proot_lib" PROOT_TMP_DIR="$proot_tmp" \
  HOME=/root USER=root PATH=/system/bin \
  "$proot" --link2symlink -0 -r "$rootfs" -w "$managed_mount" \
    -b /system -b /apex -b /vendor -b /dev -b /proc \
    -b "$managed_base:$managed_mount" \
    /system/bin/sh -c "$script"
}

rm -rf "$fixture_root"
mkdir -p "$rootfs" "$managed_base/bin" "$managed_base/state" "$upper" "$whiteout"
printf '1.0.0\n' > "$managed_base/bin/version"
printf 'keep-base\n' > "$managed_base/state/preserved"
base_hash_before="$(find "$managed_base" -type f -exec sha256sum {} \; | sort | sha256sum | awk '{print $1}')"

{
  echo 'schema=kf_proot_view_v1'
  echo 'view_id=scoped-resource-update'
  echo "base_root=$managed_base"
  echo "upper_root=$upper"
  echo "whiteout_root=$whiteout"
  echo 'mode=read_write'
} > "$control"

run_view "
  set -eu
  test \"\$(cat '$managed_mount/bin/version')\" = 1.0.0
  printf '2.0.0\\n' > '$managed_mount/bin/version'
  printf 'created-in-update\\n' > '$managed_mount/bin/new-tool'
  rm '$managed_mount/state/preserved'
  test \"\$(cat '$managed_mount/bin/version')\" = 2.0.0
  test -f '$managed_mount/bin/new-tool'
  test ! -e '$managed_mount/state/preserved'
  test \"\$(cat /system/build.prop 2>/dev/null | head -n 1 >/dev/null; printf ok)\" = ok
"

base_hash_after="$(find "$managed_base" -type f -exec sha256sum {} \; | sort | sha256sum | awk '{print $1}')"
test "$base_hash_before" = "$base_hash_after"
test "$(cat "$managed_base/bin/version")" = 1.0.0
test ! -e "$managed_base/bin/new-tool"
test -f "$managed_base/state/preserved"
test "$(cat "$upper/bin/version")" = 2.0.0
test "$(cat "$upper/bin/new-tool")" = created-in-update
test -f "$whiteout/state/preserved"

run_plain "
  set -eu
  test \"\$(cat '$managed_mount/bin/version')\" = 1.0.0
  test ! -e '$managed_mount/bin/new-tool'
  test -f '$managed_mount/state/preserved'
"

run_view "
  set -eu
  test \"\$(cat '$managed_mount/bin/version')\" = 2.0.0
  test -f '$managed_mount/bin/new-tool'
  test ! -e '$managed_mount/state/preserved'
"

printf 'meta\tscope\t%s\n' "$managed_mount"
printf 'meta\tbase_hash\t%s\n' "$base_hash_before"
printf 'result\tSCOPED_BIND_FIXTURE_OK\n'
