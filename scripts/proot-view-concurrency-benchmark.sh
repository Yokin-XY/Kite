#!/system/bin/sh

set -u

variant="${1:-baseline}"
levels="${2:-1,4,8,16}"

case "$variant" in
  baseline|candidate-disabled|view-empty|view-populated) ;;
  *) echo "error\tunsupported_variant\t$variant"; exit 2 ;;
esac

app_root="$(pwd)"
runtime_root="$app_root/files/runtime"
official_proot="$runtime_root/bin/proot"
candidate_proot="$runtime_root/bin/proot-view-benchmark"
proot_lib="$runtime_root/lib"
proot_tmp="$runtime_root/tmp"
container_root="$runtime_root/containers/ubuntu-main/rootfs"
workspace_root="$runtime_root/shared/ubuntu-main"
probe_root="$proot_tmp/kf-proot-view-concurrency"
upper_root="$probe_root/upper"
whiteout_root="$probe_root/whiteout"
control_path="$probe_root/control"
container_path="/workspace/.kf/system/bin:/workspace/.kf/bin:/root/.local/bin:/workspace/npm-global/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/system/bin"

for candidate in "$workspace_root"/.kf/toolchains/node-v*/bin "$workspace_root"/node-v*/bin; do
  [ -x "$candidate/node" ] || continue
  relative="${candidate#"$workspace_root"/}"
  container_path="/workspace/$relative:$container_path"
done

if [ "$variant" = "baseline" ]; then
  proot="$official_proot"
else
  proot="$candidate_proot"
fi
if [ ! -x "$proot" ] || [ ! -d "$container_root" ]; then
  echo "error\truntime_not_ready\t$proot\t$container_root"
  exit 3
fi

rm -rf "$probe_root"
mkdir -p "$upper_root" "$whiteout_root"
if [ "$variant" = "view-populated" ]; then
  printf 'marker\n' > "$upper_root/.kf-benchmark-marker"
  printf 'marker\n' > "$whiteout_root/.kf-benchmark-marker"
fi
{
  echo "schema=kf_proot_view_v1"
  echo "view_id=concurrency"
  echo "base_root=$container_root"
  echo "upper_root=$upper_root"
  echo "whiteout_root=$whiteout_root"
  echo "mode=read_write"
} > "$control_path"

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

launch_worker() {
  worker="$1"
  if [ "$variant" = "view-empty" ] || [ "$variant" = "view-populated" ]; then
    export KF_PROOT_VIEW_CONTROL_PATH="$control_path"
  else
    unset KF_PROOT_VIEW_CONTROL_PATH
  fi
  LD_LIBRARY_PATH="$proot_lib" \
  PROOT_TMP_DIR="$proot_tmp" \
  HOME=/root USER=root LANG=C.UTF-8 PATH="$container_path" \
  "$proot" --link2symlink -0 \
    -r "$container_root" -w /workspace \
    -b /dev -b /proc -b /sys -b "$workspace_root:/workspace" \
    /bin/bash -lc \
      'find /usr -xdev -type f -print 2>/dev/null | head -n 1000 | xargs -r stat >/dev/null' \
    > /dev/null 2> "$probe_root/worker-$worker.err" &
}

printf 'meta\tvariant\t%s\n' "$variant"
printf 'meta\truntime_sha256\t%s\n' "$(sha256sum "$proot" | awk '{print $1}')"
printf 'meta\tthermal_start\t%s\n' "$(thermal_max)"

old_ifs="$IFS"
IFS=','
for level in $levels; do
  IFS="$old_ifs"
  case "$level" in
    ''|*[!0-9]*) echo "error\tinvalid_level\t$level"; exit 2 ;;
  esac
  start="$(now_ns)"
  pids=""
  worker=1
  while [ "$worker" -le "$level" ]; do
    launch_worker "$worker"
    pid=$!
    pids="$pids $pid"
    worker=$((worker + 1))
  done

  max_total_rss_kb=0
  while :; do
    alive=0
    total_rss_kb=0
    for pid in $pids; do
      if kill -0 "$pid" 2>/dev/null; then
        alive=1
        rss_kb="$(awk '/^VmRSS:/ {print $2}' "/proc/$pid/status" 2>/dev/null)"
        case "$rss_kb" in
          ''|*[!0-9]*) rss_kb=0 ;;
        esac
        total_rss_kb=$((total_rss_kb + rss_kb))
      fi
    done
    [ "$total_rss_kb" -gt "$max_total_rss_kb" ] && \
      max_total_rss_kb="$total_rss_kb"
    [ "$alive" -eq 0 ] && break
    sleep 0.05
  done

  failures=0
  worker=1
  for pid in $pids; do
    wait "$pid"
    rc=$?
    if [ "$rc" -ne 0 ]; then
      failures=$((failures + 1))
      diagnostic="$(tr '\n' ' ' < "$probe_root/worker-$worker.err" | cut -c 1-300)"
      printf 'diagnostic\t%s\t%s\t%s\n' "$level" "$worker" "$diagnostic"
    fi
    worker=$((worker + 1))
  done
  end="$(now_ns)"
  duration="$(awk -v start="$start" -v end="$end" 'BEGIN { printf "%.0f", end - start }')"
  printf 'metric\tconcurrency\t%s\t%s\t%s\t%s\n' \
    "$level" "$duration" "$failures" "$max_total_rss_kb"
  if [ "$failures" -ne 0 ]; then
    exit 4
  fi
  IFS=','
done
IFS="$old_ifs"

printf 'meta\tthermal_end\t%s\n' "$(thermal_max)"
printf 'result\tCONCURRENCY_BENCHMARK_OK\n'
