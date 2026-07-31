#!/system/bin/sh
set -eu

source_pid="${1:-}"
shift || true
if [ -z "$source_pid" ] || [ ! -r "/proc/$source_pid/environ" ]; then
  echo "host-node-env-runner: source environment unavailable" >&2
  exit 2
fi
if [ "$#" -eq 0 ]; then
  echo "host-node-env-runner: command missing" >&2
  exit 2
fi

runner_dir="$(dirname "$0")"
env_file="$runner_dir/.kite-node-env-$source_pid-$$"
trap 'rm -f "$env_file"' EXIT HUP INT TERM
xargs -0 -n1 < "/proc/$source_pid/environ" > "$env_file"
while IFS= read -r entry; do
  export "$entry"
done < "$env_file"

exec "$@"
