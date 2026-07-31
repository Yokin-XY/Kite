#!/usr/bin/env bash

# 工具版本探测的统一边界。调用方可以覆盖秒数，但必须保持在有限范围内，
# 避免慢设备被过早误判，也避免异常命令无限占住安装事务。
kf_probe_timeout_seconds() {
  local requested="${KF_TOOLCHAIN_PROBE_TIMEOUT_SECONDS:-30}"
  case "$requested" in
    ''|*[!0-9]*)
      printf '%s' 30
      return
      ;;
  esac
  if [ "$requested" -lt 1 ] || [ "$requested" -gt 300 ]; then
    printf '%s' 30
  else
    printf '%s' "$requested"
  fi
}

kf_probe_command() {
  local command_name="$1"
  shift
  local timeout_seconds
  timeout_seconds="$(kf_probe_timeout_seconds)"

  if KF_PROBE_OUTPUT="$(timeout -k 2s "${timeout_seconds}s" "$command_name" "$@" 2>&1)"; then
    KF_PROBE_EXIT_CODE=0
  else
    KF_PROBE_EXIT_CODE="$?"
  fi
  KF_PROBE_TIMEOUT_SECONDS="$timeout_seconds"
  case "$KF_PROBE_EXIT_CODE" in
    124|137)
      KF_PROBE_REASON="timeout(${timeout_seconds}s)"
      ;;
    0)
      KF_PROBE_REASON="completed"
      ;;
    *)
      KF_PROBE_REASON="exit($KF_PROBE_EXIT_CODE)"
      ;;
  esac
}
