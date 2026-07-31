#!/system/bin/sh

set -u

variant="${1:-baseline}"
iterations="${2:-15}"
suite="${3:-daily}"

case "$variant" in
  baseline|candidate-disabled|view-empty|view-populated|view-parent-8|view-scoped) ;;
  *) echo "error\tunsupported_variant\t$variant"; exit 2 ;;
esac

case "$suite" in
  smoke) tree_count=200; large_mb=16 ;;
  daily) tree_count=5000; large_mb=128 ;;
  extreme) tree_count=100000; large_mb=1024 ;;
  *) echo "error\tunsupported_suite\t$suite"; exit 2 ;;
esac

case "$iterations" in
  ''|*[!0-9]*) echo "error\tinvalid_iterations\t$iterations"; exit 2 ;;
esac

if [ "$iterations" -lt 1 ]; then
  echo "error\tinvalid_iterations\t$iterations"
  exit 2
fi

app_root="$(pwd)"
runtime_root="$app_root/files/runtime"
proot="$runtime_root/bin/proot"
candidate_proot="$runtime_root/bin/proot-view-benchmark"
proot_lib="$runtime_root/lib"
proot_tmp="$runtime_root/tmp"
container_root="$runtime_root/containers/ubuntu-main/rootfs"
workspace_root="$runtime_root/shared/ubuntu-main"
container_path="/workspace/.kf/system/bin:/workspace/.kf/bin:/root/.local/bin:/workspace/npm-global/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/system/bin"
for candidate in "$workspace_root"/.kf/toolchains/node-v*/bin "$workspace_root"/node-v*/bin; do
  [ -x "$candidate/node" ] || continue
  relative="${candidate#"$workspace_root"/}"
  container_path="/workspace/$relative:$container_path"
done
probe_root="$proot_tmp/kf-proot-view-benchmark"
synthetic_base="$probe_root/base"
container_upper="$probe_root/views/container/upper"
container_whiteout="$probe_root/views/container/whiteout"
synthetic_upper="$probe_root/views/synthetic/upper"
synthetic_whiteout="$probe_root/views/synthetic/whiteout"
container_control="$probe_root/container-control"
synthetic_control="$probe_root/synthetic-control"

if [ ! -x "$proot" ] || [ ! -d "$container_root" ]; then
  echo "error\truntime_not_ready\t$proot\t$container_root"
  exit 3
fi

if [ "$variant" != "baseline" ]; then
  if [ ! -x "$candidate_proot" ]; then
    echo "error\tcandidate_runtime_not_ready\t$candidate_proot"
    exit 3
  fi
  proot="$candidate_proot"
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

write_control() {
  target="$1"
  view_id="$2"
  base_root="$3"
  upper_root="$4"
  whiteout_root="$5"
  parent_count="${6:-0}"
  mkdir -p "$upper_root" "$whiteout_root"
  temp="$target.tmp.$$"
  {
    echo "schema=kf_proot_view_v1"
    echo "view_id=$view_id"
    echo "base_root=$base_root"
    echo "upper_root=$upper_root"
    echo "whiteout_root=$whiteout_root"
    if [ "$variant" = "view-scoped" ]; then
      echo "scope_root=$base_root"
    fi
    parent=1
    while [ "$parent" -le "$parent_count" ]; do
      parent_root="$(dirname "$upper_root")/parents/$parent"
      mkdir -p "$parent_root/upper" "$parent_root/whiteout"
      echo "parent_upper_root=$parent_root/upper"
      echo "parent_whiteout_root=$parent_root/whiteout"
      parent=$((parent + 1))
    done
    echo "mode=read_write"
  } > "$temp"
  mv "$temp" "$target"
}

prepare_view_storage() {
  rm -rf "$container_upper" "$container_whiteout" \
    "$synthetic_upper" "$synthetic_whiteout"
  mkdir -p "$container_upper" "$container_whiteout" \
    "$synthetic_upper" "$synthetic_whiteout"
  if [ "$variant" = "view-populated" ]; then
    printf 'marker\n' > "$container_upper/.kf-benchmark-marker"
    printf 'marker\n' > "$container_whiteout/.kf-benchmark-marker"
    printf 'marker\n' > "$synthetic_upper/.kf-benchmark-marker"
    printf 'marker\n' > "$synthetic_whiteout/.kf-benchmark-marker"
  fi
}

prepare_synthetic_base() {
  marker="$synthetic_base/.prepared-$tree_count-$large_mb"
  if [ -f "$marker" ]; then
    return
  fi

  rm -rf "$synthetic_base"
  mkdir -p "$synthetic_base/probe/tree" \
    "$synthetic_base/system" "$synthetic_base/apex" "$synthetic_base/vendor" \
    "$synthetic_base/dev" "$synthetic_base/proc"
  echo "base-value" > "$synthetic_base/probe/value.txt"

  unset KF_PROOT_VIEW_CONTROL_PATH
  LD_LIBRARY_PATH="$proot_lib" \
  PROOT_TMP_DIR="$proot_tmp" \
  HOME=/root USER=root LANG=C.UTF-8 PATH="$container_path" \
  "$runtime_root/bin/proot" --link2symlink -0 \
    -r "$container_root" -w /workspace \
    -b /dev -b /proc -b /sys \
    -b "$workspace_root:/workspace" \
    -b "$synthetic_base:/mnt/kf-benchmark" \
    /workspace/.kf/bin/python3 -c '
import os
import sys

root = "/mnt/kf-benchmark/probe"
count = int(sys.argv[1])
for bucket in range(100):
    os.makedirs(os.path.join(root, "tree", str(bucket)), exist_ok=True)
for index in range(1, count + 1):
    path = os.path.join(root, "tree", str(index % 100), f"file-{index}")
    with open(path, "wb") as stream:
        stream.write(b"fixture\n")
' "$tree_count"
  if [ "$?" -ne 0 ]; then
    echo "error\tsynthetic_tree_prepare_failed\t$tree_count"
    exit 4
  fi

  dd if=/dev/zero of="$synthetic_base/probe/large.bin" bs=1048576 count="$large_mb" 2>/dev/null
  touch "$marker"
}

view_env() {
  control="$1"
  if [ "$variant" = "baseline" ] || [ "$variant" = "candidate-disabled" ]; then
    unset KF_PROOT_VIEW_CONTROL_PATH
  else
    export KF_PROOT_VIEW_CONTROL_PATH="$control"
  fi
}

run_container() {
  view_env "$container_control"
  LD_LIBRARY_PATH="$proot_lib" \
  PROOT_TMP_DIR="$proot_tmp" \
  HOME=/root USER=root LANG=C.UTF-8 \
  PATH="$container_path" \
  "$proot" --link2symlink -0 \
    -r "$container_root" \
    -w /workspace \
    -b /dev -b /proc -b /sys \
    -b "$workspace_root:/workspace" \
    "$@"
}

run_synthetic() {
  view_env "$synthetic_control"
  LD_LIBRARY_PATH="$proot_lib" \
  PROOT_TMP_DIR="$proot_tmp" \
  HOME=/root USER=root PATH=/system/bin \
  "$proot" --link2symlink -0 \
    -r "$synthetic_base" \
    -w /probe \
    -b /system -b /apex -b /vendor -b /dev -b /proc \
    "$@"
}

emit_metric() {
  name="$1"
  count="$2"
  runner="$3"
  shift 3
  index=1
  diagnostic_emitted=0
  while [ "$index" -le "$count" ]; do
    start="$(now_ns)"
    error_file="$probe_root/last-error-$name"
    "$runner" "$@" >/dev/null 2>"$error_file"
    rc=$?
    end="$(now_ns)"
    duration="$(awk -v start="$start" -v end="$end" 'BEGIN { printf "%.0f", end - start }')"
    printf 'metric\t%s\t%s\t%s\t%s\t%s\n' "$variant" "$name" "$index" "$duration" "$rc"
    if [ "$rc" -ne 0 ] && [ "$diagnostic_emitted" -eq 0 ]; then
      diagnostic="$(tr '\n' ' ' < "$error_file" | cut -c 1-500)"
      printf 'diagnostic\t%s\t%s\t%s\n' "$variant" "$name" "$diagnostic"
      diagnostic_emitted=1
    fi
    index=$((index + 1))
  done
}

prepare_synthetic_base
prepare_view_storage
parent_count=0
[ "$variant" = "view-parent-8" ] && parent_count=8
write_control "$container_control" "benchmark-container" "$container_root" "$container_upper" "$container_whiteout" "$parent_count"
write_control "$synthetic_control" "benchmark-synthetic" "$synthetic_base" "$synthetic_upper" "$synthetic_whiteout" "$parent_count"

runtime_hash="$(sha256sum "$proot" | awk '{print $1}')"
printf 'meta\tvariant\t%s\n' "$variant"
printf 'meta\tsuite\t%s\n' "$suite"
printf 'meta\titerations\t%s\n' "$iterations"
printf 'meta\ttree_count\t%s\n' "$tree_count"
printf 'meta\tlarge_mb\t%s\n' "$large_mb"
printf 'meta\tparent_layer_count\t%s\n' "$parent_count"
printf 'meta\truntime_sha256\t%s\n' "$runtime_hash"
printf 'meta\tthermal_start\t%s\n' "$(thermal_max)"
printf 'meta\tview_storage_kb_start\t%s\n' "$(du -sk "$probe_root/views" 2>/dev/null | awk '{print $1}')"

run_container /bin/true >/dev/null 2>&1
run_synthetic /system/bin/true >/dev/null 2>&1

emit_metric "container_true" "$iterations" run_container /bin/true
emit_metric "container_bash_noop" "$iterations" run_container /bin/bash -lc true

short_iterations="$iterations"
[ "$short_iterations" -gt 5 ] && short_iterations=5

if run_container /bin/bash -lc 'command -v python3 >/dev/null 2>&1' >/dev/null 2>&1; then
  emit_metric "container_python_noop" "$short_iterations" run_container /bin/bash -lc 'python3 -c pass'
fi
if run_container /bin/bash -lc 'command -v node >/dev/null 2>&1' >/dev/null 2>&1; then
  emit_metric "container_node_version" "$short_iterations" run_container /bin/bash -lc 'node --version'
fi
if run_container /bin/bash -lc 'command -v opencode >/dev/null 2>&1' >/dev/null 2>&1; then
  emit_metric "container_opencode_version" "$short_iterations" run_container /bin/bash -lc 'opencode --version'
fi

emit_metric "container_usr_stat_1000" "$short_iterations" run_container /bin/bash -lc \
  'find /usr -xdev -type f -print 2>/dev/null | head -n 1000 | xargs -r stat >/dev/null'
emit_metric "synthetic_cat" "$iterations" run_synthetic /system/bin/cat /probe/value.txt
emit_metric "synthetic_find" "$short_iterations" run_synthetic /system/bin/sh -c \
  'find /probe/tree -type f -print | wc -l'
emit_metric "synthetic_large_read" "$short_iterations" run_synthetic /system/bin/dd \
  if=/probe/large.bin of=/dev/null bs=1048576

printf 'meta\tthermal_end\t%s\n' "$(thermal_max)"
printf 'meta\tview_storage_kb_end\t%s\n' "$(du -sk "$probe_root/views" 2>/dev/null | awk '{print $1}')"

exit 0
