#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=../assets/toolchain/ai-dev-pack/lib/command-probe.sh
source "$ROOT_DIR/assets/toolchain/ai-dev-pack/lib/command-probe.sh"

KF_TOOLCHAIN_PROBE_TIMEOUT_SECONDS=3
export KF_TOOLCHAIN_PROBE_TIMEOUT_SECONDS
kf_probe_command bash -c 'sleep 2; printf slow-ok'
test "$KF_PROBE_EXIT_CODE" = 0
test "$KF_PROBE_OUTPUT" = slow-ok
test "$KF_PROBE_REASON" = completed

KF_TOOLCHAIN_PROBE_TIMEOUT_SECONDS=1
export KF_TOOLCHAIN_PROBE_TIMEOUT_SECONDS
kf_probe_command bash -c 'sleep 3; printf too-late'
test "$KF_PROBE_EXIT_CODE" = 124
test "$KF_PROBE_REASON" = 'timeout(1s)'

KF_TOOLCHAIN_PROBE_TIMEOUT_SECONDS=invalid
export KF_TOOLCHAIN_PROBE_TIMEOUT_SECONDS
test "$(kf_probe_timeout_seconds)" = 30

printf 'ai-dev-pack command probe fixture PASS\n'
