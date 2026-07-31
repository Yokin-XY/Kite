#!/system/bin/sh

set -eu

app_root="$(pwd)"
runtime_root="$app_root/files/runtime"
proot="$runtime_root/bin/proot-view-benchmark"
proot_lib="$runtime_root/lib"
proot_tmp="$runtime_root/tmp"
fixture_root="$proot_tmp/kf-proot-view-fixture"
base_root="$fixture_root/base"
upper_root="$fixture_root/upper"
whiteout_root="$fixture_root/whiteout"
control_path="$fixture_root/control"

if [ ! -x "$proot" ]; then
  echo "error\tcandidate_runtime_not_ready\t$proot"
  exit 3
fi

rm -rf "$fixture_root"
mkdir -p "$base_root/probe" "$base_root/system" "$base_root/apex" \
  "$base_root/vendor" "$base_root/dev" "$base_root/proc" \
  "$upper_root" "$whiteout_root"
printf 'base-value\n' > "$base_root/probe/value.txt"

{
  echo "schema=kf_proot_view_v1"
  echo "view_id=fixture"
  echo "base_root=$base_root"
  echo "upper_root=$upper_root"
  echo "whiteout_root=$whiteout_root"
  echo "mode=read_write"
} > "$control_path"

base_hash_before="$(sha256sum "$base_root/probe/value.txt" | awk '{print $1}')"

KF_PROOT_VIEW_CONTROL_PATH="$control_path" \
LD_LIBRARY_PATH="$proot_lib" \
PROOT_TMP_DIR="$proot_tmp" \
HOME=/root USER=root PATH=/system/bin \
"$proot" --link2symlink -0 \
  -r "$base_root" -w /probe \
  -b /system -b /apex -b /vendor -b /dev -b /proc \
  /system/bin/sh -c '
    set -eu
    test "$(cat /probe/value.txt)" = "base-value"
    printf "view-value\n" > /probe/value.txt
    test "$(cat /probe/value.txt)" = "view-value"
    printf "new-value\n" > /probe/new.txt
    test "$(cat /probe/new.txt)" = "new-value"
    rm /probe/value.txt
    test ! -e /probe/value.txt
  '

base_hash_after="$(sha256sum "$base_root/probe/value.txt" | awk '{print $1}')"
test "$base_hash_before" = "$base_hash_after"
test "$(cat "$base_root/probe/value.txt")" = "base-value"
test "$(cat "$upper_root/probe/new.txt")" = "new-value"
test -f "$whiteout_root/probe/value.txt"

printf 'meta\tbase_hash_before\t%s\n' "$base_hash_before"
printf 'meta\tbase_hash_after\t%s\n' "$base_hash_after"
printf 'meta\tupper_new_value\t%s\n' "$(cat "$upper_root/probe/new.txt")"
printf 'meta\twhiteout_value_path\t%s\n' "$whiteout_root/probe/value.txt"
printf 'result\tVIEW_FIXTURE_OK\n'
