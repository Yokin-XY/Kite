#!/system/bin/sh

set -eu

app_root="$(pwd)"
runtime_root="$app_root/files/runtime"
proot="$runtime_root/bin/proot-view-benchmark"
proot_lib="$runtime_root/lib"
proot_tmp="$runtime_root/tmp"
fixture_root="$proot_tmp/kf-proot-view-concurrency"
base="$fixture_root/base"
upper="$fixture_root/upper"
whiteout="$fixture_root/whiteout"
control="$fixture_root/control"

run_view() {
  script="$1"
  KF_PROOT_VIEW_CONTROL_PATH="$control" \
  LD_LIBRARY_PATH="$proot_lib" PROOT_TMP_DIR="$proot_tmp" \
  HOME=/root USER=root PATH=/system/bin \
  "$proot" --link2symlink -0 -r "$base" -w /probe \
    -b /system -b /apex -b /vendor -b /dev -b /proc \
    /system/bin/sh -c "$script"
}

rm -rf "$fixture_root"
mkdir -p "$base/probe/dir" "$base/system" "$base/apex" "$base/vendor" \
  "$base/dev" "$base/proc" "$upper" "$whiteout"
printf 'base-stable\n' > "$base/probe/stable.txt"
printf 'delete-me\n' > "$base/probe/delete-me.txt"
printf 'rename-me\n' > "$base/probe/rename-me.txt"
base_hash_before="$(find "$base/probe" -type f -print0 | sort -z | \
  xargs -0 sha256sum | sha256sum | awk '{print $1}')"

{
  echo 'schema=kf_proot_view_v1'
  echo 'view_id=concurrency-fixture'
  echo "base_root=$base"
  echo "upper_root=$upper"
  echo "whiteout_root=$whiteout"
  echo 'mode=read_write'
} > "$control"

# 读进程先启动并建立旧索引；写进程随后改变命名空间。读进程不重启，
# 必须依靠共享代次发现新文件、删除标记和重命名。
run_view '
  set -eu
  printf "ready\n" > /probe/reader-ready
  deadline=300
  while [ ! -f /probe/new-from-writer.txt ] && [ "$deadline" -gt 0 ]; do
    deadline=$((deadline - 1))
    sleep 0.01
  done
  test "$deadline" -gt 0
  test "$(cat /probe/new-from-writer.txt)" = writer-value
  deadline=300
  while [ -e /probe/delete-me.txt ] && [ "$deadline" -gt 0 ]; do
    deadline=$((deadline - 1))
    sleep 0.01
  done
  test "$deadline" -gt 0
  deadline=300
  while [ ! -f /probe/renamed.txt ] && [ "$deadline" -gt 0 ]; do
    deadline=$((deadline - 1))
    sleep 0.01
  done
  test "$deadline" -gt 0
  test "$(cat /probe/renamed.txt)" = rename-me
  printf "observed\n" > /probe/reader-observed
' &
reader_pid=$!

deadline=300
while [ ! -f "$upper/probe/reader-ready" ] && [ "$deadline" -gt 0 ]; do
  deadline=$((deadline - 1))
  sleep 0.01
done
test "$deadline" -gt 0

run_view '
  set -eu
  printf "writer-value\n" > /probe/new-from-writer.txt
  rm /probe/delete-me.txt
  mv /probe/rename-me.txt /probe/renamed.txt
'
wait "$reader_pid"
test -f "$upper/probe/reader-observed"

# 多个独立 PRoot 树同时创建路径，所有命名空间事务必须串行完成且不能丢项。
writer_pids=""
i=1
while [ "$i" -le 8 ]; do
  run_view "set -eu; mkdir /probe/concurrent-$i; printf 'writer-$i\\n' > /probe/concurrent-$i/value.txt" &
  writer_pids="$writer_pids $!"
  i=$((i + 1))
done
for writer_pid in $writer_pids; do
  wait "$writer_pid"
done

run_view '
  set -eu
  i=1
  while [ "$i" -le 8 ]; do
    test "$(cat /probe/concurrent-$i/value.txt)" = "writer-$i"
    i=$((i + 1))
  done
  test ! -e /probe/delete-me.txt
  test "$(cat /probe/new-from-writer.txt)" = writer-value
  test "$(cat /probe/renamed.txt)" = rename-me
'

# 模拟写进程在代次为奇数时崩溃。新 PRoot 必须接管未完成代次、重建索引，
# 然后继续提供完整视图。
printf '\001\000\000\000\000\000\000\000' > \
  "$upper/.kite-proot-view/generation"
run_view '
  set -eu
  test "$(cat /probe/new-from-writer.txt)" = writer-value
  test ! -e /probe/delete-me.txt
  test "$(cat /probe/concurrent-8/value.txt)" = writer-8
'
generation_value="$(od -An -tu8 "$upper/.kite-proot-view/generation" | tr -d ' ')"
test $((generation_value % 2)) -eq 0

base_hash_after="$(find "$base/probe" -type f -print0 | sort -z | \
  xargs -0 sha256sum | sha256sum | awk '{print $1}')"
test "$base_hash_before" = "$base_hash_after"
test "$(wc -c < "$upper/.kite-proot-view/generation")" -eq 8

printf 'meta\tindependent_writers\t8\n'
printf 'meta\trecovered_generation\t%s\n' "$generation_value"
printf 'meta\tbase_hash\t%s\n' "$base_hash_after"
printf 'result\tVIEW_CONCURRENCY_FIXTURE_OK\n'
