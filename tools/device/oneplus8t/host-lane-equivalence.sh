#!/system/bin/sh
# 宿主车道等价性测试（宿主侧执行，App uid）
# 形态照抄 App 真实 bridge：loader --library-path ... --preload compat node 脚本
H=/data/user/0/com.kite.app/files/runtime/shared/ubuntu-main/.kf/system/node-runtime/host
NB=/data/user/0/com.kite.app/files/runtime/shared/ubuntu-main/.kf/software/kite.nodejs/node-v26.4.0/bin/node
RF=/data/user/0/com.kite.app/files/runtime/containers/ubuntu-main/rootfs
LP=/data/user/0/com.kite.app/files/runtime/shared/ubuntu-main/.kf/system/node-runtime/host/glibc:/data/user/0/com.kite.app/files/runtime/containers/ubuntu-main/rootfs/usr/lib/aarch64-linux-gnu:/data/user/0/com.kite.app/files/runtime/containers/ubuntu-main/rootfs/lib/aarch64-linux-gnu:/data/user/0/com.kite.app/files/runtime/shared/ubuntu-main/.kf/software/kite.nodejs/node-v26.4.0/lib
/system/bin/run-as com.kite.app env \
  HOME=/data/user/0/com.kite.app/files/runtime/containers/ubuntu-main/rootfs/root \
  "PATH=/workspace/.kf/bin:/root/.local/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin" \
  HTTP_PROXY=http://127.0.0.1:38087 \
  HTTPS_PROXY=http://127.0.0.1:38087 \
  http_proxy=http://127.0.0.1:38087 \
  https_proxy=http://127.0.0.1:38087 \
  NO_PROXY=localhost,127.0.0.1,::1 \
  no_proxy=localhost,127.0.0.1,::1 \
  TMPDIR=$H/tmp \
  "NODE_OPTIONS=--no-warnings --require=$H/kite-node-host-runtime.cjs" \
  KITE_NODE_HOST_LANE=direct_glibc_v1 \
  KITE_NODE_HOST_LAUNCHER=$H/kite-node-host \
  KITE_NODE_HOST_LOADER=$H/glibc/ld-linux-aarch64.so.1 \
  "KITE_NODE_HOST_LIBRARY_PATH=$LP" \
  KITE_NODE_HOST_COMPAT_LIBRARY=$H/glibc/libkite-node-glibc-compat.so \
  KITE_NODE_HOST_BINARY=$NB \
  KITE_NODE_HOST_WORKSPACE=/data/user/0/com.kite.app/files/runtime/shared/ubuntu-main \
  KITE_NODE_HOST_CONTROL=/data/user/0/com.kite.app/files/runtime/shared/ubuntu-main/.kf \
  KITE_NODE_HOST_ROOTFS=$RF \
  KITE_GLIBC_HOST_LOADER=$H/glibc/ld-linux-aarch64.so.1 \
  "KITE_GLIBC_HOST_LIBRARY_PATH=$LP" \
  KITE_GLIBC_HOST_GUEST_TMP=$H/tmp \
  KITE_NODE_HOST_GUEST_TMP=$H/tmp \
  KITE_NODE_HOST_RESOLV_CONF=/data/user/0/com.kite.app/files/runtime/tmp/resolv.conf \
  "$H/glibc/ld-linux-aarch64.so.1" --library-path "$LP" --preload "$H/glibc/libkite-node-glibc-compat.so" \
  "$NB" "$@"
