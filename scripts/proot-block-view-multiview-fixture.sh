#!/system/bin/sh

set -eu

app_root="$(pwd)"
runtime_root="$app_root/files/runtime"
proot="$runtime_root/bin/proot-view-benchmark"
proot_lib="$runtime_root/lib"
proot_tmp="$runtime_root/tmp"
fixture_root="$proot_tmp/kf-proot-block-multiview"
base_root="$fixture_root/base"
payload="$base_root/payload.bin"

rm -rf "$fixture_root"
mkdir -p "$base_root/system" "$base_root/apex" "$base_root/vendor" \
  "$base_root/dev" "$base_root/proc"
truncate -s 1073741824 "$payload"
printf '\125' | dd of="$payload" bs=1 seek=1073741823 conv=notrunc 2>/dev/null
base_hash_before="$(sha256sum "$payload" | awk '{print $1}')"

hashes=""
total_upper_kib=0
view=1
while [ "$view" -le 4 ]; do
  view_root="$fixture_root/view-$view"
  upper="$view_root/upper"
  whiteout="$view_root/whiteout"
  control="$view_root/control"
  mkdir -p "$upper" "$whiteout"
  {
    echo "schema=kf_proot_view_v1"
    echo "view_id=multiview-$view"
    echo "base_root=$base_root"
    echo "upper_root=$upper"
    echo "whiteout_root=$whiteout"
    echo "mode=read_write"
  } > "$control"

  offset=$((view * 8))
  view_hash="$(
    KF_PROOT_VIEW_CONTROL_PATH="$control" \
    LD_LIBRARY_PATH="$proot_lib" PROOT_TMP_DIR="$proot_tmp" \
    HOME=/root USER=root PATH=/system/bin \
    "$proot" --link2symlink -0 -r "$base_root" -w / \
      -b /system -b /apex -b /vendor -b /dev -b /proc \
      /system/bin/sh -c \
      "dd if=/dev/urandom of=/payload.bin bs=1048576 count=4 seek=$offset conv=notrunc 2>/dev/null; sync; dd if=/payload.bin bs=1048576 skip=$offset count=4 2>/dev/null | sha256sum | cut -d ' ' -f 1"
  )"
  reopen_hash="$(
    KF_PROOT_VIEW_CONTROL_PATH="$control" \
    LD_LIBRARY_PATH="$proot_lib" PROOT_TMP_DIR="$proot_tmp" \
    HOME=/root USER=root PATH=/system/bin \
    "$proot" --link2symlink -0 -r "$base_root" -w / \
      -b /system -b /apex -b /vendor -b /dev -b /proc \
      /system/bin/sh -c \
      "dd if=/payload.bin bs=1048576 skip=$offset count=4 2>/dev/null | sha256sum | cut -d ' ' -f 1"
  )"
  test -n "$view_hash"
  test "$view_hash" = "$reopen_hash"
  case " $hashes " in
    *" $view_hash "*) echo "duplicate view hash: $view_hash" >&2; exit 31 ;;
  esac
  hashes="$hashes $view_hash"
  upper_kib="$(du -sk "$upper" | awk '{print $1}')"
  total_upper_kib=$((total_upper_kib + upper_kib))
  printf 'view\t%s\thash\t%s\tupper_kib\t%s\n' "$view" "$view_hash" "$upper_kib"
  view=$((view + 1))
done

base_hash_after="$(sha256sum "$payload" | awk '{print $1}')"
test "$base_hash_before" = "$base_hash_after"
test "$total_upper_kib" -le 65536
printf 'meta\tbase_logical_bytes\t%s\n' "$(stat -c %s "$payload")"
printf 'meta\tbase_hash_before\t%s\n' "$base_hash_before"
printf 'meta\tbase_hash_after\t%s\n' "$base_hash_after"
printf 'meta\ttotal_upper_kib\t%s\n' "$total_upper_kib"
printf 'result\tBLOCK_VIEW_MULTIVIEW_OK\n'
