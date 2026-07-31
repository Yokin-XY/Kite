#!/bin/sh
# 普通 Ubuntu View 离线验证夹具；只依赖不可变 Base 自带的 shell 与基础命令。

set -eu

LAB_DIR=/root/.kite-view-lab
STATE_FILE=$LAB_DIR/state.json
TARGET=$LAB_DIR/deterministic.bin
REPORT_PATH=${KF_VIEW_LAB_REPORT:-/tmp/kite-view-lab-report.json}

previous_count=0
previous_run_at=0
if [ -f "$STATE_FILE" ]; then
  parsed_count=$(sed -n 's/.*"runCount":[[:space:]]*\([0-9][0-9]*\).*/\1/p' "$STATE_FILE" | head -n 1)
  parsed_run_at=$(sed -n 's/.*"lastRunAt":[[:space:]]*\([0-9][0-9]*\).*/\1/p' "$STATE_FILE" | head -n 1)
  previous_count=${parsed_count:-0}
  previous_run_at=${parsed_run_at:-0}
fi

mkdir -p "$LAB_DIR/crud"
rm -f "$LAB_DIR/crud/a.txt" "$LAB_DIR/crud/b.txt"
printf 'alpha' > "$LAB_DIR/crud/a.txt"
mv "$LAB_DIR/crud/a.txt" "$LAB_DIR/crud/b.txt"
rm "$LAB_DIR/crud/b.txt"
printf 'beta' > "$LAB_DIR/crud/b.txt"
[ "$(cat "$LAB_DIR/crud/b.txt")" = 'beta' ]

dd if=/dev/zero of="$TARGET" bs=1048576 count=1 status=none
head -c 65536 /dev/zero | tr '\000' '\001' | dd of="$TARGET" bs=65536 count=1 conv=notrunc status=none
head -c 65536 /dev/zero | tr '\000' '\005' | dd of="$TARGET" bs=65536 seek=8 count=1 conv=notrunc status=none
sync "$TARGET" 2>/dev/null || true
sha_line=$(sha256sum "$TARGET")
file_sha256=${sha_line%% *}

run_count=$((previous_count + 1))
now=$(date +%s)
at_unix_ms="${now}000"
state_tmp="${STATE_FILE}.tmp.$$"
printf '{"runCount":%s,"lastRunAt":%s,"previousRunAt":%s,"lastSummary":"run#%s sha256=%.12s crud=true"}\n' \
  "$run_count" "$now" "$previous_run_at" "$run_count" "$file_sha256" > "$state_tmp"
mv "$state_tmp" "$STATE_FILE"

python_available=false
node_available=false
command -v python3 >/dev/null 2>&1 && python_available=true
(command -v node >/dev/null 2>&1 || command -v nodejs >/dev/null 2>&1) && node_available=true

report=$(printf '{"schema":"kite_view_lab_report_v1","success":true,"runCount":%s,"previousRunAt":%s,"fileSha256":"%s","crudOk":true,"environmentId":"%s","viewId":"%s","pythonAvailable":%s,"nodeAvailable":%s,"workingDirectory":"%s","labDirExists":true,"exitCode":0,"atUnixMs":%s}' \
  "$run_count" \
  "$previous_run_at" \
  "$file_sha256" \
  "${KF_PROOT_ENVIRONMENT_ID:-}" \
  "${KF_PROOT_VIEW_ID:-}" \
  "$python_available" \
  "$node_available" \
  "$(pwd)" \
  "$at_unix_ms")

mkdir -p "$(dirname "$REPORT_PATH")"
report_tmp="${REPORT_PATH}.tmp.$$"
printf '%s\n' "$report" > "$report_tmp"
mv "$report_tmp" "$REPORT_PATH"
printf '%s\n' "$report"
