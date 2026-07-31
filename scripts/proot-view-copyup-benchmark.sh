#!/system/bin/sh

set -u

size_mib="${1:-128}"
iterations="${2:-3}"

case "$size_mib" in
  ''|*[!0-9]*) echo "error\tinvalid_size_mib\t$size_mib"; exit 2 ;;
esac
case "$iterations" in
  ''|*[!0-9]*) echo "error\tinvalid_iterations\t$iterations"; exit 2 ;;
esac
if [ "$size_mib" -lt 1 ] || [ "$iterations" -lt 1 ]; then
  echo "error\tinvalid_range\t$size_mib\t$iterations"
  exit 2
fi

app_root="$(pwd)"
runtime_root="$app_root/files/runtime"
proot="$runtime_root/bin/proot-view-benchmark"
proot_lib="$runtime_root/lib"
proot_tmp="$runtime_root/tmp"
probe_root="$proot_tmp/kf-proot-view-copyup"
base_root="$probe_root/base"
upper_root="$probe_root/upper"
whiteout_root="$probe_root/whiteout"
control_path="$probe_root/control"
large_file="$base_root/probe/large.bin"
marker="$probe_root/.prepared-$size_mib"
error_file="$probe_root/last-error"

if [ ! -x "$proot" ]; then
  echo "error\tcandidate_runtime_not_ready\t$proot"
  exit 3
fi

now_ns() {
  date +%s%N
}

thermal_max() {
  max=0
  for path in /sys/class/thermal/thermal_zone*/temp; do
    [ -r "$path" ] || continue
    value="$(cat "$path" 2>/dev/null || echo 0)"
    case "$value" in
      ''|*[!0-9-]*) value=0 ;;
    esac
    [ "$value" -gt "$max" ] && max="$value"
  done
  echo "$max"
}

if [ ! -f "$marker" ]; then
  rm -rf "$base_root" "$upper_root" "$whiteout_root"
  mkdir -p "$base_root/probe" "$base_root/system" "$base_root/apex" \
    "$base_root/vendor" "$base_root/dev" "$base_root/proc" \
    "$upper_root" "$whiteout_root"
  dd if=/dev/zero of="$large_file" bs=1048576 count="$size_mib" 2>/dev/null
  rm -f "$probe_root"/.prepared-*
  touch "$marker"
fi

{
  echo "schema=kf_proot_view_v1"
  echo "view_id=copyup"
  echo "base_root=$base_root"
  echo "upper_root=$upper_root"
  echo "whiteout_root=$whiteout_root"
  echo "mode=read_write"
} > "$control_path"

base_hash_before="$(sha256sum "$large_file" | awk '{print $1}')"
seek_blocks=$((size_mib * 128))

printf 'meta\tsize_mib\t%s\n' "$size_mib"
printf 'meta\titerations\t%s\n' "$iterations"
printf 'meta\tthermal_start\t%s\n' "$(thermal_max)"

index=1
while [ "$index" -le "$iterations" ]; do
  rm -rf "$upper_root" "$whiteout_root"
  mkdir -p "$upper_root" "$whiteout_root"
  start="$(now_ns)"
  KF_PROOT_VIEW_CONTROL_PATH="$control_path" \
  LD_LIBRARY_PATH="$proot_lib" \
  PROOT_TMP_DIR="$proot_tmp" \
  HOME=/root USER=root PATH=/system/bin \
  "$proot" --link2symlink -0 \
    -r "$base_root" -w /probe \
    -b /system -b /apex -b /vendor -b /dev -b /proc \
    /system/bin/dd if=/dev/zero of=/probe/large.bin bs=4096 count=1 \
      seek="$seek_blocks" conv=notrunc > /dev/null 2> "$error_file" &
  proot_pid=$!
  max_rss_kb=0
  last_read_bytes=0
  last_write_bytes=0
  while kill -0 "$proot_pid" 2>/dev/null; do
    if [ -r "/proc/$proot_pid/status" ]; then
      rss_kb="$(awk '/^VmRSS:/ {print $2}' "/proc/$proot_pid/status")"
      case "$rss_kb" in
        ''|*[!0-9]*) rss_kb=0 ;;
      esac
      [ "$rss_kb" -gt "$max_rss_kb" ] && max_rss_kb="$rss_kb"
    fi
    if [ -r "/proc/$proot_pid/io" ]; then
      last_read_bytes="$(awk '/^read_bytes:/ {print $2}' "/proc/$proot_pid/io")"
      last_write_bytes="$(awk '/^write_bytes:/ {print $2}' "/proc/$proot_pid/io")"
    fi
    sleep 0.05
  done
  wait "$proot_pid"
  rc=$?
  end="$(now_ns)"
  duration="$(awk -v start="$start" -v end="$end" 'BEGIN { printf "%.0f", end - start }')"
  upper_size=0
  [ -f "$upper_root/probe/large.bin" ] && \
    upper_size="$(stat -c %s "$upper_root/probe/large.bin")"
  printf 'metric\tcopyup\t%s\t%s\t%s\t%s\t%s\t%s\n' \
    "$index" "$duration" "$rc" "$max_rss_kb" "$last_read_bytes" "$last_write_bytes"
  printf 'metric_meta\tcopyup\t%s\tupper_size\t%s\n' "$index" "$upper_size"
  if [ "$rc" -ne 0 ]; then
    diagnostic="$(tr '\n' ' ' < "$error_file" | cut -c 1-500)"
    printf 'diagnostic\tcopyup\t%s\t%s\n' "$index" "$diagnostic"
    exit "$rc"
  fi
  expected_size=$((size_mib * 1048576))
  if [ "$upper_size" -ne "$expected_size" ]; then
    echo "error\tupper_size_mismatch\t$upper_size\t$expected_size"
    exit 4
  fi
  index=$((index + 1))
done

base_hash_after="$(sha256sum "$large_file" | awk '{print $1}')"
printf 'meta\tthermal_end\t%s\n' "$(thermal_max)"
printf 'meta\tbase_hash_before\t%s\n' "$base_hash_before"
printf 'meta\tbase_hash_after\t%s\n' "$base_hash_after"
if [ "$base_hash_before" != "$base_hash_after" ]; then
  echo "error\tbase_hash_changed"
  exit 5
fi
printf 'result\tCOPYUP_BENCHMARK_OK\n'
