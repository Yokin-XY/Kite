#!/system/bin/sh

set -eu

app_root="$(pwd)"
runtime_root="$app_root/files/runtime"
proot="$runtime_root/bin/proot-view-benchmark"
proot_lib="$runtime_root/lib"
proot_tmp="$runtime_root/tmp"
rootfs="$runtime_root/containers/ubuntu-main/rootfs"
workspace="$runtime_root/shared/ubuntu-main"
# Upper and Whiteout must be siblings of the immutable Base, never descendants
# of it.  Keeping them outside runtime also matches the Android ProotViewStore
# layout used by the product path.
fixture_root="$app_root/files/proot-view-fixtures/kf-proot-view-managed-install"
software_root="$workspace/.kf/software"
scope_root="$software_root"
resource_root="$software_root/kite.view.fixture"
fresh_root="$software_root/kite.view.fixture.fresh"
target="$resource_root/bin/tool"
source="$workspace/kite-view-managed-install-source"
upper="$fixture_root/upper"
whiteout="$fixture_root/whiteout"
control="$fixture_root/control"

cleanup() {
  rm -rf "$fixture_root" "$resource_root" "$fresh_root"
  rm -f "$source"
}
trap cleanup EXIT

run_view() {
  script="$1"
  KF_PROOT_VIEW_CONTROL_PATH="$control" \
  LD_LIBRARY_PATH="$proot_lib" PROOT_TMP_DIR="$proot_tmp" \
  HOME=/root USER=root \
  PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin \
  "$proot" --link2symlink -0 -r "$rootfs" -w /workspace \
    -b /system -b /apex -b /vendor -b /dev -b /proc \
    -b "$workspace:/workspace" \
    /bin/bash -lc "$script"
}

cleanup
mkdir -p "$(dirname "$target")" "$upper" "$whiteout"
printf 'old-version\n' > "$target"
printf 'new-version\n' > "$source"
base_hash_before="$(sha256sum "$target" | awk '{print $1}')"

{
  echo 'schema=kf_proot_view_v1'
  echo 'view_id=managed-install-fixture'
  echo "base_root=$runtime_root"
  echo "upper_root=$upper"
  echo "whiteout_root=$whiteout"
  echo "scope_root=$scope_root"
  echo 'mode=read_write'
} > "$control"

run_view '
  set -eu
  test "$(cat /workspace/.kf/software/kite.view.fixture/bin/tool)" = old-version
  mkdir -p /workspace/.kf/software/kite.view.fixture/bin
  install -m 0755 /workspace/kite-view-managed-install-source \
    /workspace/.kf/software/kite.view.fixture/bin/tool
  test "$(cat /workspace/.kf/software/kite.view.fixture/bin/tool)" = new-version
  mkdir -p /workspace/.kf/software/kite.view.fixture.fresh/bin/nested
  printf "fresh-install\n" > \
    /workspace/.kf/software/kite.view.fixture.fresh/bin/nested/state
  test "$(cat /workspace/.kf/software/kite.view.fixture.fresh/bin/nested/state)" = fresh-install
'

base_hash_after="$(sha256sum "$target" | awk '{print $1}')"
test "$base_hash_before" = "$base_hash_after"
test "$(cat "$target")" = old-version
test "$(cat "$upper/shared/ubuntu-main/.kf/software/kite.view.fixture/bin/tool")" = new-version
test "$(cat "$upper/shared/ubuntu-main/.kf/software/kite.view.fixture.fresh/bin/nested/state")" = fresh-install
test ! -e "$fresh_root"

printf 'meta\tbase_hash\t%s\n' "$base_hash_before"
printf 'meta\tupper_target\t%s\n' "$upper/shared/ubuntu-main/.kf/software/kite.view.fixture/bin/tool"
printf 'result\tMANAGED_INSTALL_FIXTURE_OK\n'
