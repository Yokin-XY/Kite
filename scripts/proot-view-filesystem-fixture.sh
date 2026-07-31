#!/system/bin/sh

set -eu

app_root="$(pwd)"
runtime_root="$app_root/files/runtime"
proot="$runtime_root/bin/proot-view-benchmark"
proot_lib="$runtime_root/lib"
proot_tmp="$runtime_root/tmp"
fixture_root="$proot_tmp/kf-proot-view-filesystem"
base_root="$fixture_root/base"
upper_root="$fixture_root/upper"
whiteout_root="$fixture_root/whiteout"
control_path="$fixture_root/control"
helper_host="$runtime_root/bin/kf-view-fixture"
helper32_host="$runtime_root/bin/kf-view-fixture32"

if [ ! -x "$proot" ] || [ ! -x "$helper_host" ] || [ ! -x "$helper32_host" ]; then
  echo "error\truntime_not_ready\t$proot\t$helper_host\t$helper32_host"
  exit 3
fi

tree_hash() {
  root="$1"
  (
    cd "$root"
    find . -print | sort | while IFS= read -r path; do
      if [ -L "$path" ]; then
        printf 'l\t%s\t%s\n' "$path" "$(readlink "$path")"
      elif [ -f "$path" ]; then
        printf 'f\t%s\t%s\t%s\n' "$path" "$(stat -c %a "$path")" \
          "$(sha256sum "$path" | awk '{print $1}')"
      elif [ -d "$path" ]; then
        printf 'd\t%s\t%s\n' "$path" "$(stat -c %a "$path")"
      fi
    done
  ) | sha256sum | awk '{print $1}'
}

rm -rf "$fixture_root"
mkdir -p "$base_root/probe/tree" "$base_root/probe/rename-source" \
  "$base_root/probe/remove-empty" "$base_root/probe/remove-nonempty" \
  "$base_root/probe/remove-tree/nested" \
  "$base_root/system" "$base_root/apex" "$base_root/vendor" \
  "$base_root/dev" "$base_root/proc" "$upper_root" "$whiteout_root"
printf 'lower-value\n' > "$base_root/probe/lower.txt"
printf 'abcdef\n' > "$base_root/probe/truncate.txt"
printf 'metadata\n' > "$base_root/probe/metadata.txt"
printf '0123456789\n' > "$base_root/probe/mmap.bin"
printf 'base\n' > "$base_root/probe/mmap-grow.bin"
printf 'xattr\n' > "$base_root/probe/xattr.txt"
printf 'fd-xattr\n' > "$base_root/probe/fd-xattr.txt"
printf 'locks\n' > "$base_root/probe/locks.txt"
printf 'openat2-base\n' > "$base_root/probe/openat2.txt"
printf 'creat-base\n' > "$base_root/probe/creat.txt"
printf 'guard-base\n' > "$base_root/probe/guard.txt"
printf 'sparse-growth-base\n' > "$base_root/probe/sparse-growth.bin"
printf 'vector-base\n' > "$base_root/probe/vector-io.bin"
printf 'abi32-base\n' > "$base_root/probe/abi32.bin"
printf 'abi32-vector-base\n' > "$base_root/probe/abi32-vector.bin"
dd if=/dev/zero of="$base_root/probe/hardlink.bin" bs=65536 count=2 2>/dev/null
printf 'second-block\n' | dd of="$base_root/probe/hardlink.bin" \
  bs=1 seek=65536 conv=notrunc 2>/dev/null
dd if=/dev/zero of="$base_root/probe/mmap-lifecycle.bin" \
  bs=65536 count=2 2>/dev/null
dd if=/dev/zero of="$base_root/probe/mmap-truncate.bin" \
  bs=65536 count=2 2>/dev/null
dd if=/dev/zero of="$base_root/probe/fd-rename.bin" bs=65536 count=1 2>/dev/null
dd if=/dev/zero of="$base_root/probe/fd-overwrite-source.bin" \
  bs=65536 count=1 2>/dev/null
dd if=/dev/zero of="$base_root/probe/fd-overwrite-destination.bin" \
  bs=65536 count=1 2>/dev/null
dd if=/dev/zero of="$base_root/probe/fd-unlink.bin" bs=65536 count=1 2>/dev/null
printf '#!/system/bin/sh\nprintf "base-exec\\n"\n' > "$base_root/probe/run.sh"
chmod 0755 "$base_root/probe/run.sh"
cp "$helper_host" "$base_root/probe/native-exec"
chmod 0755 "$base_root/probe/native-exec"
printf 'lower-only\n' > "$base_root/probe/tree/lower-only.txt"
printf 'base-override\n' > "$base_root/probe/tree/override.txt"
printf 'delete-me\n' > "$base_root/probe/tree/deleted.txt"
printf 'nested\n' > "$base_root/probe/rename-source/nested.txt"
printf 'keep\n' > "$base_root/probe/remove-nonempty/keep.txt"
printf 'top\n' > "$base_root/probe/remove-tree/top.txt"
printf 'nested\n' > "$base_root/probe/remove-tree/nested/value.txt"
chmod 0644 "$base_root/probe/metadata.txt" \
  "$base_root/probe/remove-nonempty/keep.txt"
"$helper_host" setxattr "$base_root/probe/xattr.txt" user.keep keep
"$helper_host" setxattr "$base_root/probe/xattr.txt" user.change base
"$helper_host" setxattr "$base_root/probe/fd-xattr.txt" user.fd base

{
  echo "schema=kf_proot_view_v1"
  echo "view_id=filesystem-fixture"
  echo "base_root=$base_root"
  echo "upper_root=$upper_root"
  echo "whiteout_root=$whiteout_root"
  echo "mode=read_write"
} > "$control_path"

base_hash_before="$(tree_hash "$base_root/probe")"

KF_PROOT_VIEW_CONTROL_PATH="$control_path" \
LD_LIBRARY_PATH="$proot_lib" \
PROOT_TMP_DIR="$proot_tmp" \
HOME=/root USER=root PATH=/system/bin \
"$proot" --link2symlink -0 \
  -r "$base_root" -w /probe \
  -b /system -b /apex -b /vendor -b /dev -b /proc \
  -b "$helper_host:/kf-view-fixture" \
  -b "$helper32_host:/kf-view-fixture32" \
  /system/bin/sh -c '
    set -eu
    printf "stage\tread-copyup\n"
    test "$(cat /probe/lower.txt)" = "lower-value"
    printf "view-lower\n" > /probe/lower.txt
    printf "new-value\n" > /probe/new.txt
    rm /probe/tree/deleted.txt

    printf "stage\tdirectory-merge\n"
    printf "view-override\n" > /probe/tree/override.txt
    printf "upper-only\n" > /probe/tree/upper-only.txt
    listing="$(ls -1 /probe/tree | sort | tr "\n" ",")"
    test "$listing" = "lower-only.txt,override.txt,upper-only.txt,"
    rewind_listing="$(/kf-view-fixture dir-rewind /probe/tree | sort | tr "\n" ",")"
    test "$rewind_listing" = "lower-only.txt,override.txt,upper-only.txt,"

    printf "stage\tdirectory-create-remove\n"
    mkdir /probe/new-dir
    printf "inside\n" > /probe/new-dir/inside.txt
    rmdir /probe/remove-empty
    test ! -e /probe/remove-empty
    if rmdir /probe/remove-nonempty 2>/dev/null; then
      echo "nonempty rmdir unexpectedly succeeded" >&2
      exit 20
    fi
    printf "stage\tdirectory-recursive-remove-recreate\n"
    rm -rf /probe/remove-tree
    test ! -e /probe/remove-tree
    mkdir -p /probe/remove-tree/recreated
    printf "new-tree\n" > /probe/remove-tree/recreated/value.txt
    test "$(cat /probe/remove-tree/recreated/value.txt)" = "new-tree"

    printf "stage\trename\n"
    mv /probe/truncate.txt /probe/renamed-file.txt
    test ! -e /probe/truncate.txt
    test "$(cat /probe/remove-tree/recreated/value.txt)" = "new-tree"
    test "$(cat /probe/renamed-file.txt)" = "abcdef"
    mv /probe/rename-source /probe/renamed-dir
    test ! -e /probe/rename-source
    test "$(cat /probe/renamed-dir/nested.txt)" = "nested"

    printf "stage\tfd-path-identity\n"
    /kf-view-fixture fd-rename-lifecycle /probe/fd-rename.bin \
      /probe/fd-renamed.bin
    /kf-view-fixture fd-rename-overwrite-lifecycle \
      /probe/fd-overwrite-source.bin /probe/fd-overwrite-destination.bin
    /kf-view-fixture fd-unlink-lifecycle /probe/fd-unlink.bin

    printf "stage\tlinks\n"
    ln -s lower.txt /probe/symbolic.txt
    test "$(readlink /probe/symbolic.txt)" = "lower.txt"
    test "$(cat /probe/symbolic.txt)" = "view-lower"
    if ln /probe/hardlink.bin /probe/hard.txt 2>/dev/null; then
      cmp /probe/hardlink.bin /probe/hard.txt
      printf "hardlink-write\n" | dd of=/probe/hard.txt \
        bs=1 seek=65536 conv=notrunc 2>/dev/null
      cmp /probe/hardlink.bin /probe/hard.txt
      printf "capability\tlink2symlink_alias\tcoherent\n"
    else
      test ! -e /probe/hard.txt
      printf "capability\tlink2symlink_alias\tblocked\n"
    fi

    printf "stage\tmetadata-truncate-mmap\n"
    chmod 0600 /probe/metadata.txt
    test "$(stat -c %a /probe/metadata.txt)" = "600"
    truncate -s 3 /probe/renamed-file.txt
    test "$(cat /probe/renamed-file.txt)" = "abc"
    /kf-view-fixture mmap-write /probe/mmap.bin 3 VIEW
    test "$(cat /probe/mmap.bin)" = "012VIEW789"
    printf "stage\tmmap-grow\n"
    /kf-view-fixture mmap-grow /probe/mmap-grow.bin
    test "$(stat -c %s /probe/mmap-grow.bin)" = "131072"
    printf "stage\tmmap-lifecycle\n"
    /kf-view-fixture mmap-lifecycle /probe/mmap-lifecycle.bin
    printf "stage\tmmap-truncate-lifecycle\n"
    /kf-view-fixture mmap-truncate-lifecycle /probe/mmap-truncate.bin
    test "$(dd if=/probe/mmap-truncate.bin bs=1 skip=98304 count=7 2>/dev/null)" = "REGROWN"

    printf "stage\topen-variants\n"
    /kf-view-fixture openat2-write /probe/openat2.txt openat2-view
    test "$(cat /probe/openat2.txt)" = "openat2-view"
    /kf-view-fixture creat-write /probe/creat.txt creat-view
    test "$(cat /probe/creat.txt)" = "creat-view"

    printf "stage\tfail-closed\n"
    /kf-view-fixture destructive-guards /probe/guard.txt /system/bin/sh
    test "$(cat /probe/guard.txt)" = "guard-base"
    printf "stage\tclose-range-lifecycle\n"
    /kf-view-fixture close-range-lifecycle /probe/guard.txt
    /kf-view-fixture lseek-data-hole /probe/guard.txt

    printf "stage\tsparse-metadata-growth\n"
    truncate -s 3221225472 /probe/sparse-growth.bin
    printf "GROW-3G" | dd of=/probe/sparse-growth.bin \
      bs=1 seek=3221225465 conv=notrunc 2>/dev/null
    test "$(stat -c %s /probe/sparse-growth.bin)" = "3221225472"
    test "$(dd if=/probe/sparse-growth.bin bs=1 skip=3221225465 \
      count=7 2>/dev/null)" = "GROW-3G"

    printf "stage\tvector-io-statx\n"
    /kf-view-fixture vector-io-lifecycle /probe/vector-io.bin
    /kf-view-fixture statx-size /probe/vector-io.bin 4294967431

    printf "stage\tabi32-large-file\n"
    /kf-view-fixture32 abi32-lifecycle /probe/abi32.bin
    /kf-view-fixture32 statx-size /probe/abi32.bin 5368717312
    /kf-view-fixture32 vector-io-lifecycle /probe/abi32-vector.bin
    /kf-view-fixture32 statx-size /probe/abi32-vector.bin 4294967431

    printf "stage\texec-view\n"
    printf "#!/system/bin/sh\nprintf \"view-exec\\\\n\"\n" > /probe/run.sh
    chmod 0755 /probe/run.sh
    printf "stage\texec-script\n"
    test "$(/probe/run.sh)" = "view-exec"
    printf "stage\texec-native-base\n"
    test "$(/probe/native-exec print-native-marker)" = "BASE-NATIVE-MARKER"
    /kf-view-fixture replace-marker /probe/native-exec \
      BASE-NATIVE-MARKER VIEW-NATIVE-MARKER
    printf "stage\texec-native-view\n"
    test "$(/probe/native-exec print-native-marker)" = "VIEW-NATIVE-MARKER"

    printf "stage\tlocks\n"
    /kf-view-fixture lock-test /probe/locks.txt

    printf "stage\txattr-socket\n"
    /kf-view-fixture setxattr /probe/xattr.txt user.change view
    test "$(/kf-view-fixture getxattr /probe/xattr.txt user.change)" = "view"
    test "$(/kf-view-fixture getxattr /probe/xattr.txt user.keep)" = "keep"
    /kf-view-fixture unix-bind /probe/view.sock
    test "$(stat -c %F /probe/view.sock)" = "socket"

    printf "stage\tfd-metadata-safety\n"
    if /kf-view-fixture fd-chmod /probe/remove-nonempty/keep.txt 0600; then
      test "$(stat -c %a /probe/remove-nonempty/keep.txt)" = "600"
      printf "capability\tfd_lower_metadata\tredirected\n"
    else
      test "$(stat -c %a /probe/remove-nonempty/keep.txt)" = "644"
      printf "capability\tfd_lower_metadata\tblocked\n"
    fi
    if /kf-view-fixture fd-setxattr /probe/fd-xattr.txt user.fd changed; then
      test "$(/kf-view-fixture getxattr /probe/fd-xattr.txt user.fd)" = "changed"
      printf "capability\tfd_lower_xattr\tredirected\n"
    else
      test "$(/kf-view-fixture getxattr /probe/fd-xattr.txt user.fd)" = "base"
      printf "capability\tfd_lower_xattr\tblocked\n"
    fi
  '

printf 'stage\treopen-view\n'
KF_PROOT_VIEW_CONTROL_PATH="$control_path" \
LD_LIBRARY_PATH="$proot_lib" \
PROOT_TMP_DIR="$proot_tmp" \
HOME=/root USER=root PATH=/system/bin \
"$proot" --link2symlink -0 \
  -r "$base_root" -w /probe \
  -b /system -b /apex -b /vendor -b /dev -b /proc \
  -b "$helper_host:/kf-view-fixture" \
  -b "$helper32_host:/kf-view-fixture32" \
  /system/bin/sh -c '
    set -eu
    test "$(cat /probe/lower.txt)" = "view-lower"
    test "$(cat /probe/new.txt)" = "new-value"
    test ! -e /probe/tree/deleted.txt
    listing="$(ls -1 /probe/tree | sort | tr "\n" ",")"
    test "$listing" = "lower-only.txt,override.txt,upper-only.txt,"
    test ! -e /probe/truncate.txt
    test "$(cat /probe/renamed-file.txt)" = "abc"
    test ! -e /probe/rename-source
    test "$(cat /probe/renamed-dir/nested.txt)" = "nested"
    test ! -e /probe/fd-rename.bin
    test "$(dd if=/probe/fd-renamed.bin bs=1 count=10 2>/dev/null)" = "FD-RENAMED"
    test "$(dd if=/probe/fd-renamed.bin bs=1 skip=32 count=12 2>/dev/null)" = \
      "PATH-RENAMED"
    test ! -e /probe/fd-overwrite-source.bin
    test "$(dd if=/probe/fd-overwrite-destination.bin bs=1 count=10 \
      2>/dev/null)" = "NEW-SOURCE"
    test "$(dd if=/probe/fd-unlink.bin bs=1 count=8 2>/dev/null)" = "NEW-PATH"
    test "$(readlink /probe/symbolic.txt)" = "lower.txt"
    if [ -e /probe/hard.txt ]; then
      cmp /probe/hardlink.bin /probe/hard.txt
      printf "capability\tlink2symlink_alias_reopen\tcoherent\n"
    fi
    test "$(cat /probe/mmap.bin)" = "012VIEW789"
    test "$(stat -c %s /probe/mmap-grow.bin)" = "131072"
    test "$(dd if=/probe/mmap-truncate.bin bs=1 skip=98304 count=7 2>/dev/null)" = "REGROWN"
    test "$(cat /probe/openat2.txt)" = "openat2-view"
    test "$(cat /probe/creat.txt)" = "creat-view"
    test "$(dd if=/probe/guard.txt bs=1 count=11 2>/dev/null)" = "CLOSE-RANGE"
    test "$(stat -c %s /probe/sparse-growth.bin)" = "3221225472"
    test "$(dd if=/probe/sparse-growth.bin bs=1 skip=3221225465 \
      count=7 2>/dev/null)" = "GROW-3G"
    /kf-view-fixture statx-size /probe/vector-io.bin 4294967431
    /kf-view-fixture32 abi32-verify /probe/abi32.bin
    /kf-view-fixture32 statx-size /probe/abi32.bin 5368717312
    /kf-view-fixture32 statx-size /probe/abi32-vector.bin 4294967431
    test "$(dd if=/probe/vector-io.bin bs=1 skip=4294967419 count=6 \
      2>/dev/null)" = "VECTOR"
    test "$(dd if=/probe/vector-io.bin bs=1 skip=100 count=3 2>/dev/null)" = \
      "POS"
    test "$(dd if=/probe/vector-io.bin bs=1 skip=4294967425 count=6 \
      2>/dev/null)" = "APPEND"
    test "$(dd if=/probe/abi32-vector.bin bs=1 skip=4294967419 count=6 \
      2>/dev/null)" = "VECTOR"
    test "$(dd if=/probe/abi32-vector.bin bs=1 skip=100 count=3 \
      2>/dev/null)" = "POS"
    test "$(dd if=/probe/abi32-vector.bin bs=1 skip=4294967425 count=6 \
      2>/dev/null)" = "APPEND"
    test "$(/probe/run.sh)" = "view-exec"
    test "$(/probe/native-exec print-native-marker)" = "VIEW-NATIVE-MARKER"
    test "$(/kf-view-fixture getxattr /probe/xattr.txt user.change)" = "view"
    test "$(stat -c %F /probe/view.sock)" = "socket"
  '

base_hash_after="$(tree_hash "$base_root/probe")"
test "$base_hash_before" = "$base_hash_after"
test "$(cat "$base_root/probe/lower.txt")" = "lower-value"
test "$(cat "$base_root/probe/truncate.txt")" = "abcdef"
test "$(cat "$base_root/probe/mmap.bin")" = "0123456789"
test "$(cat "$base_root/probe/openat2.txt")" = "openat2-base"
test "$(cat "$base_root/probe/creat.txt")" = "creat-base"
test "$(cat "$base_root/probe/guard.txt")" = "guard-base"
test "$(cat "$base_root/probe/sparse-growth.bin")" = "sparse-growth-base"
test "$(cat "$base_root/probe/vector-io.bin")" = "vector-base"
test "$(cat "$base_root/probe/abi32.bin")" = "abi32-base"
test "$(cat "$base_root/probe/abi32-vector.bin")" = "abi32-vector-base"
test "$("$base_root/probe/native-exec" print-native-marker)" = \
  "BASE-NATIVE-MARKER"
test "$("$helper_host" getxattr "$base_root/probe/xattr.txt" user.change)" = "base"
test "$("$helper_host" getxattr "$base_root/probe/fd-xattr.txt" user.fd)" = "base"
test ! -e "$base_root/probe/view.sock"
test -d "$base_root/probe/rename-source"
test ! -e "$base_root/probe/renamed-dir"

printf 'meta\tbase_hash_before\t%s\n' "$base_hash_before"
printf 'meta\tbase_hash_after\t%s\n' "$base_hash_after"
printf 'meta\tupper_entries\t%s\n' "$(find "$upper_root" -mindepth 1 | wc -l)"
printf 'meta\twhiteout_entries\t%s\n' "$(find "$whiteout_root" -type f | wc -l)"
upper_physical_kib="$(du -sk "$upper_root" | awk '{print $1}')"
test "$upper_physical_kib" -lt 65536
printf 'meta\tupper_physical_kib\t%s\n' "$upper_physical_kib"
printf 'result\tVIEW_FILESYSTEM_FIXTURE_OK\n'
