#!/system/bin/sh

set -eu

app_root="$(pwd)"
runtime_root="$app_root/files/runtime"
proot="$runtime_root/bin/proot-block-prototype"
tracee_source="$runtime_root/bin/kf-block-proot-tracee"
proot_lib="$runtime_root/lib"
proot_tmp="$runtime_root/tmp"
fixture_root="$proot_tmp/kf-block-tracer-fixture"
rootfs="$fixture_root/rootfs"
base="$rootfs/probe/base.bin"
delta="$fixture_root/delta.bin"
meta="$fixture_root/delta.meta"
upper="$fixture_root/upper"
whiteout="$fixture_root/whiteout"
control="$fixture_root/control"
mode="${KF_BLOCK_FIXTURE_MODE:-probe}"

rm -rf "$fixture_root"
mkdir -p "$rootfs/probe" "$rootfs/system" "$rootfs/apex" \
  "$rootfs/vendor" "$rootfs/dev" "$rootfs/proc" "$upper" "$whiteout"
cp "$tracee_source" "$rootfs/probe/tracee"
chmod 700 "$rootfs/probe/tracee"
truncate -s 1073741824 "$base"
printf '\021' | dd of="$base" bs=1 seek=1048576 conv=notrunc 2>/dev/null
printf '\125' | dd of="$base" bs=1 seek=681574400 conv=notrunc 2>/dev/null

if [ "$mode" = formal ]; then
  {
    echo "schema=kf_proot_view_v1"
    echo "view_id=block-tracer-formal"
    echo "base_root=$rootfs"
    echo "upper_root=$upper"
    echo "whiteout_root=$whiteout"
    echo "mode=read_write"
  } > "$control"
  delta="$upper/probe/base.bin"
  meta="$upper/.kite-proot-view/blocks/probe/base.bin.meta"
fi

run_tracee() {
  action="$1"
  if [ "$mode" = formal ]; then
    KF_PROOT_VIEW_CONTROL_PATH="$control" \
    LD_LIBRARY_PATH="$proot_lib" PROOT_TMP_DIR="$proot_tmp" \
    HOME=/root USER=root PATH=/system/bin \
    "$proot" --link2symlink -0 \
      -r "$rootfs" -w /probe \
      -b /system -b /apex -b /vendor -b /dev -b /proc \
      /probe/tracee /probe/base.bin "$action"
    return
  fi
  KF_PROOT_BLOCK_PROBE_BASE="$base" \
  KF_PROOT_BLOCK_PROBE_DELTA="$delta" \
  KF_PROOT_BLOCK_PROBE_META="$meta" \
  LD_LIBRARY_PATH="$proot_lib" \
  PROOT_TMP_DIR="$proot_tmp" \
  HOME=/root USER=root PATH=/system/bin \
  "$proot" --link2symlink -0 \
    -r "$rootfs" -w /probe \
    -b /system -b /apex -b /vendor -b /dev -b /proc \
    /probe/tracee /probe/base.bin "$action"
}

base_hash_before="$(sha256sum "$base" | cut -d ' ' -f 1)"
started="$(date +%s%N)"
set +e
prepare_output="$(run_tracee prepare-crash)"
prepare_status="$?"
set -e
printf '%s\n' "$prepare_output"
printf '%s\n' "$prepare_output" | grep -q '^result[[:space:]]PROOT_BLOCK_PREPARED$'
# The tracee deliberately dies after persisting the marker.  PRoot's aggregate
# exit code can reflect a previously reaped child, so recovery is proved by the
# marker plus the fresh PRoot invocation below.
run_tracee verify
ended="$(date +%s%N)"
base_hash_after="$(sha256sum "$base" | cut -d ' ' -f 1)"

base_byte="$(od -An -tu1 -N1 -j 536870912 "$base" | tr -d ' ')"
delta_first="$(od -An -tu1 -N1 -j 536870912 "$delta" | tr -d ' ')"
delta_last="$(od -An -tu1 -N1 -j 541065215 "$delta" | tr -d ' ')"
delta_pwrite="$(od -An -tu1 -N1 -j 104857723 "$delta" | tr -d ' ')"
base_preserved_tail="$(od -An -tu1 -N1 -j 681574400 "$base" | tr -d ' ')"
delta_blocks="$(stat -c %b "$delta")"
delta_allocated="$((delta_blocks * 512))"

printf 'fixture\tbase_logical_bytes\t%s\n' "$(stat -c %s "$base")"
printf 'fixture\tdelta_logical_bytes\t%s\n' "$(stat -c %s "$delta")"
printf 'fixture\tdelta_allocated_bytes\t%s\n' "$delta_allocated"
printf 'fixture\telapsed_ns\t%s\n' "$((ended - started))"
printf 'fixture\tprepare_status\t%s\n' "$prepare_status"
printf 'fixture\tbase_hash_before\t%s\n' "$base_hash_before"
printf 'fixture\tbase_hash_after\t%s\n' "$base_hash_after"
printf 'fixture\tbase_byte\t%s\n' "$base_byte"
printf 'fixture\tdelta_first_byte\t%s\n' "$delta_first"
printf 'fixture\tdelta_last_byte\t%s\n' "$delta_last"
printf 'fixture\tdelta_pwrite_byte\t%s\n' "$delta_pwrite"
printf 'fixture\tbase_preserved_tail\t%s\n' "$base_preserved_tail"
printf 'fixture\tmode\t%s\n' "$mode"

test "$base_byte" = "0"
test "$base_hash_before" = "$base_hash_after"
test "$delta_first" = "66"
test "$delta_last" = "66"
test "$delta_pwrite" = "49"
test "$base_preserved_tail" = "85"
test "$delta_allocated" -le 16777216
printf 'result\tPROOT_BLOCK_TRACER_FIXTURE_OK\n'
