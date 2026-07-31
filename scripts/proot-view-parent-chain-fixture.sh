#!/system/bin/sh

set -eu

app_root="$(pwd)"
runtime_root="$app_root/files/runtime"
proot="$runtime_root/bin/proot-view-benchmark"
proot_lib="$runtime_root/lib"
proot_tmp="$runtime_root/tmp"
fixture_root="$proot_tmp/kf-proot-view-parent-chain"
base="$fixture_root/base"
p1_upper="$fixture_root/p1-upper"
p1_whiteout="$fixture_root/p1-whiteout"
p2_upper="$fixture_root/p2-upper"
p2_whiteout="$fixture_root/p2-whiteout"
c1_upper="$fixture_root/c1-upper"
c1_whiteout="$fixture_root/c1-whiteout"
c2_upper="$fixture_root/c2-upper"
c2_whiteout="$fixture_root/c2-whiteout"
persistent_workspace="$fixture_root/persistent-workspace"
persistent_exchange="$fixture_root/persistent-exchange"

run_view() {
  control="$1"
  script="$2"
  KF_PROOT_VIEW_CONTROL_PATH="$control" \
  LD_LIBRARY_PATH="$proot_lib" PROOT_TMP_DIR="$proot_tmp" \
  HOME=/root USER=root PATH=/system/bin \
  "$proot" --link2symlink -0 -r "$base" -w /probe \
    -b /system -b /apex -b /vendor -b /dev -b /proc \
    -b "$persistent_workspace:/workspace" \
    -b "$persistent_exchange:/exchange" \
    /system/bin/sh -c "$script"
}

rm -rf "$fixture_root"
mkdir -p "$base/probe/dir" "$base/system" "$base/apex" "$base/vendor" \
  "$base/dev" "$base/proc" "$p1_upper/probe/dir" "$p1_whiteout/probe" \
  "$p2_upper/probe/dir" "$p2_whiteout/probe" "$c1_upper" "$c1_whiteout" \
  "$c2_upper" "$c2_whiteout" "$persistent_workspace" "$persistent_exchange"
printf 'base\n' > "$base/probe/common.txt"
printf 'base-only\n' > "$base/probe/base-only.txt"
printf 'base-hidden\n' > "$base/probe/base-hidden.txt"
printf 'base-entry\n' > "$base/probe/dir/base.txt"
printf 'base-collide\n' > "$base/probe/dir/collide.txt"
printf 'parent-one\n' > "$p1_upper/probe/common.txt"
printf 'p1-only\n' > "$p1_upper/probe/p1-only.txt"
printf 'p1-hidden\n' > "$p1_upper/probe/p1-hidden.txt"
printf 'p1-entry\n' > "$p1_upper/probe/dir/p1.txt"
printf 'p1-collide\n' > "$p1_upper/probe/dir/collide.txt"
: > "$p1_whiteout/probe/base-hidden.txt"
printf 'parent-two\n' > "$p2_upper/probe/common.txt"
printf 'p2-only\n' > "$p2_upper/probe/p2-only.txt"
printf 'p2-entry\n' > "$p2_upper/probe/dir/p2.txt"
printf 'p2-collide\n' > "$p2_upper/probe/dir/collide.txt"
: > "$p2_whiteout/probe/p1-hidden.txt"

parent_hash_before="$(sha256sum "$p1_upper/probe/p1-only.txt" | awk '{print $1}')"
base_hash_before="$(sha256sum "$base/probe/base-only.txt" | awk '{print $1}')"

control1="$fixture_root/control-1"
{
  echo 'schema=kf_proot_view_v1'
  echo 'view_id=child-one'
  echo "base_root=$base"
  echo "upper_root=$c1_upper"
  echo "whiteout_root=$c1_whiteout"
  echo "parent_upper_root=$p2_upper"
  echo "parent_whiteout_root=$p2_whiteout"
  echo "parent_upper_root=$p1_upper"
  echo "parent_whiteout_root=$p1_whiteout"
  echo 'mode=read_write'
} > "$control1"

run_view "$control1" '
  set -eu
  printf "stage\tchild-one-read\n"
  test "$(cat /probe/common.txt)" = parent-two
  test "$(cat /probe/p1-only.txt)" = p1-only
  test "$(cat /probe/p2-only.txt)" = p2-only
  test "$(cat /probe/base-only.txt)" = base-only
  test ! -e /probe/base-hidden.txt
  test ! -e /probe/p1-hidden.txt
  names="$(ls /probe/dir | sort | tr "\n" " ")"
  test "$names" = "base.txt collide.txt p1.txt p2.txt "
  test "$(cat /probe/dir/collide.txt)" = p2-collide
  printf "stage\tchild-one-write\n"
  printf "child-one\n" > /probe/p1-only.txt
  rm /probe/base-only.txt
  printf "new-one\n" > /probe/new-one.txt
  printf "workspace-one\n" > /workspace/persist.txt
  printf "exchange-one\n" > /exchange/persist.txt
  mv /probe/dir /probe/dir-renamed
  printf "stage\tchild-one-verify\n"
  test "$(cat /probe/dir-renamed/base.txt)" = base-entry
  test "$(cat /workspace/persist.txt)" = workspace-one
  test "$(cat /exchange/persist.txt)" = exchange-one
  printf "workspace-two\n" > /workspace/persist.txt
  test "$(cat /probe/dir-renamed/p1.txt)" = p1-entry
  test "$(cat /probe/dir-renamed/p2.txt)" = p2-entry
  test "$(cat /probe/dir-renamed/collide.txt)" = p2-collide
'

test "$(sha256sum "$p1_upper/probe/p1-only.txt" | awk '{print $1}')" = "$parent_hash_before"
test "$(sha256sum "$base/probe/base-only.txt" | awk '{print $1}')" = "$base_hash_before"
test -f "$c1_whiteout/probe/base-only.txt"

control2="$fixture_root/control-2"
{
  echo 'schema=kf_proot_view_v1'
  echo 'view_id=child-two'
  echo "base_root=$base"
  echo "upper_root=$c2_upper"
  echo "whiteout_root=$c2_whiteout"
  echo "parent_upper_root=$c1_upper"
  echo "parent_whiteout_root=$c1_whiteout"
  echo "parent_upper_root=$p2_upper"
  echo "parent_whiteout_root=$p2_whiteout"
  echo "parent_upper_root=$p1_upper"
  echo "parent_whiteout_root=$p1_whiteout"
  echo 'mode=read_write'
} > "$control2"

run_view "$control2" '
  set -eu
  printf "stage\tchild-two-read\n"
  test "$(cat /probe/p1-only.txt)" = child-one
  test "$(cat /probe/new-one.txt)" = new-one
  test ! -e /probe/base-only.txt
  test ! -e /probe/dir
  test "$(cat /probe/dir-renamed/base.txt)" = base-entry
  printf "stage\tchild-two-write\n"
  printf "child-two\n" > /probe/common.txt
  rm /probe/new-one.txt
  test "$(cat /probe/common.txt)" = child-two
'

test "$(cat "$p2_upper/probe/common.txt")" = parent-two
test "$(cat "$c1_upper/probe/new-one.txt")" = new-one
test -f "$c2_whiteout/probe/new-one.txt"
test "$(cat "$persistent_workspace/persist.txt")" = workspace-two
test "$(cat "$persistent_exchange/persist.txt")" = exchange-one
printf 'meta\tparent_layers\t3\n'
printf 'meta\tpersistent_binds\tworkspace,exchange\n'
printf 'meta\tbase_hash\t%s\n' "$base_hash_before"
printf 'meta\tparent_hash\t%s\n' "$parent_hash_before"
printf 'result\tPARENT_CHAIN_FIXTURE_OK\n'
