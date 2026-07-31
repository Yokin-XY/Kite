#!/bin/sh
# Kite PRoot 环境隔离离线夹具；只依赖 Ubuntu Base 自带的 POSIX shell 与基础命令。

set -eu

ROOT_FILE=/root/.kite-environment-lab/private.txt
WORKSPACE_FILE=/workspace/.kite-environment-lab/private.txt
EXCHANGE_FILE=/exchange/.kite-environment-lab/shared.txt

json_escape() {
  printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'
}

json_file_value() {
  if [ -f "$1" ]; then
    printf '"%s"' "$(json_escape "$(cat "$1")")"
  else
    printf 'null'
  fi
}

write_text() {
  path=$1
  value=$2
  mkdir -p "$(dirname "$path")"
  tmp="${path}.tmp.$$"
  printf '%s' "$value" > "$tmp"
  mv "$tmp" "$path"
  sync "$path" 2>/dev/null || true
}

action=${1:-}
case "$action" in
  read)
    ;;
  write)
    [ "$#" -ge 2 ] || {
      echo 'write requires PRIVATE_MARKER' >&2
      exit 2
    }
    write_text "$ROOT_FILE" "$2"
    write_text "$WORKSPACE_FILE" "$2"
    if [ "$#" -ge 3 ] && [ "$3" != '-' ]; then
      write_text "$EXCHANGE_FILE" "$3"
    fi
    ;;
  *)
    echo 'usage: kite_environment_lab.sh read | write PRIVATE_MARKER [SHARED_MARKER]' >&2
    exit 2
    ;;
esac

environment_id=$(json_escape "${KF_PROOT_ENVIRONMENT_ID:-}")
view_id=$(json_escape "${KF_PROOT_VIEW_ID:-}")
at_unix_ms="$(date +%s)000"

printf '{"schema":"kite_environment_lab_report_v1","action":"%s","environmentId":"%s","viewId":"%s","rootValue":%s,"workspaceValue":%s,"exchangeValue":%s,"atUnixMs":%s}\n' \
  "$action" \
  "$environment_id" \
  "$view_id" \
  "$(json_file_value "$ROOT_FILE")" \
  "$(json_file_value "$WORKSPACE_FILE")" \
  "$(json_file_value "$EXCHANGE_FILE")" \
  "$at_unix_ms"
