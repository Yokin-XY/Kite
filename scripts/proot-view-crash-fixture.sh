#!/system/bin/sh

set -eu

size_mib="${1:-512}"
app_root="$(pwd)"
runtime_root="$app_root/files/runtime"
proot="$runtime_root/bin/proot-view-benchmark"
proot_lib="$runtime_root/lib"
proot_tmp="$runtime_root/tmp"
fixture_root="$proot_tmp/kf-proot-view-crash"
base_root="$fixture_root/base"
upper_root="$fixture_root/upper"
whiteout_root="$fixture_root/whiteout"
control_path="$fixture_root/control"
large_file="$base_root/probe/large.bin"
internal_tmp="$upper_root/.kite-proot-view/tmp"

case "$size_mib" in
  ''|*[!0-9]*) echo "error\tinvalid_size_mib\t$size_mib"; exit 2 ;;
esac
if [ "$size_mib" -lt 64 ] || [ ! -x "$proot" ]; then
  echo "error\tfixture_not_ready\t$size_mib\t$proot"
  exit 3
fi

rm -rf "$fixture_root"
mkdir -p "$base_root/probe" "$base_root/system" "$base_root/apex" \
  "$base_root/vendor" "$base_root/dev" "$base_root/proc" \
  "$upper_root" "$whiteout_root"
truncate -s "$((size_mib * 1024 * 1024))" "$large_file"
printf 'base-sentinel\n' > "$base_root/probe/sentinel.txt"

{
  echo "schema=kf_proot_view_v1"
  echo "view_id=crash-fixture"
  echo "base_root=$base_root"
  echo "upper_root=$upper_root"
  echo "whiteout_root=$whiteout_root"
  echo "mode=read_write"
} > "$control_path"

base_hash_before="$(sha256sum "$large_file" | awk '{print $1}')"
sentinel_hash_before="$(sha256sum "$base_root/probe/sentinel.txt" | awk '{print $1}')"
printf 'meta\tsize_mib\t%s\n' "$size_mib"
printf 'meta\tbase_hash_before\t%s\n' "$base_hash_before"

iteration=0
for delay in 0.02 0.10 0.40; do
  iteration=$((iteration + 1))
  rm -rf "$upper_root" "$whiteout_root"
  mkdir -p "$upper_root" "$whiteout_root"

  KF_PROOT_VIEW_CONTROL_PATH="$control_path" \
  LD_LIBRARY_PATH="$proot_lib" \
  PROOT_TMP_DIR="$proot_tmp" \
  HOME=/root USER=root PATH=/system/bin \
  "$proot" --kill-on-exit --link2symlink -0 \
    -r "$base_root" -w /probe \
    -b /system -b /apex -b /vendor -b /dev -b /proc \
    /system/bin/sh -c 'printf X > /probe/large.bin' \
    > "$fixture_root/run-$iteration.out" 2>&1 &
  proot_pid=$!
  sleep "$delay"
  kill -9 "$proot_pid" 2>/dev/null || true
  wait "$proot_pid" 2>/dev/null || true

  base_hash_after_kill="$(sha256sum "$large_file" | awk '{print $1}')"
  sentinel_hash_after_kill="$(sha256sum "$base_root/probe/sentinel.txt" | awk '{print $1}')"
  test "$base_hash_before" = "$base_hash_after_kill"
  test "$sentinel_hash_before" = "$sentinel_hash_after_kill"

  temp_before=0
  if [ -d "$internal_tmp" ]; then
    temp_before="$(find "$internal_tmp" -mindepth 1 -type f | wc -l)"
  fi

  visible_state="$({
    KF_PROOT_VIEW_CONTROL_PATH="$control_path" \
    LD_LIBRARY_PATH="$proot_lib" \
    PROOT_TMP_DIR="$proot_tmp" \
    HOME=/root USER=root PATH=/system/bin \
    "$proot" --kill-on-exit --link2symlink -0 \
      -r "$base_root" -w /probe \
      -b /system -b /apex -b /vendor -b /dev -b /proc \
      /system/bin/sh -c '
        test "$(cat /probe/sentinel.txt)" = "base-sentinel"
        if ls -a / | grep -q "^\.kite-proot-view$"; then exit 41; fi
        stat -c %s /probe/large.bin
      '
  } 2> "$fixture_root/reopen-$iteration.err")"
  temp_after="$(find "$internal_tmp" -mindepth 1 -type f | wc -l)"
  test "$temp_after" = "0"

  printf 'metric\tkill_reopen\t%s\t%s\t%s\t%s\n' \
    "$iteration" "$delay" "$temp_before" "$visible_state"
done

base_hash_after="$(sha256sum "$large_file" | awk '{print $1}')"
test "$base_hash_before" = "$base_hash_after"
printf 'meta\tbase_hash_after\t%s\n' "$base_hash_after"
printf 'result\tVIEW_CRASH_FIXTURE_OK\n'
