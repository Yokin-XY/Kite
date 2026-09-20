#!/bin/bash
# 交叉编译宿主 syscall tracer（动态链接版）。
#
# 静态 glibc 版在 untrusted_app 域活不过 glibc 静态初始化：Android 应用
# seccomp 过滤器对 set_robust_list(99)、getrandom(278) 等 glibc 静态 init
# 依赖的系统调用直接 KILL(SIGSYS)，且多层 filter 取最严动作，无法在运行
# 时绕过。动态链接版与 node 本体走同一条 glibc loader 启动路径（该路径
# 已在应用域长期验证可行），通过 patchelf 把 interpreter/rpath 固定到
# 随 App 部署的 glibc 目录，不依赖系统 libc。
set -e
cd /work
aarch64-linux-gnu-gcc -O2 -Wall -Wextra -Werror \
  -o /tmp/kite-syscall-tracer \
  native/kite-glibc-host/kite-syscall-tracer.c -ldl
HOST_GLIBC=/data/user/0/com.kite.app/files/runtime/shared/ubuntu-main/.kf/system/node-runtime/host/glibc
patchelf --set-interpreter "$HOST_GLIBC/ld-linux-aarch64.so.1" \
  --set-rpath "$HOST_GLIBC" /tmp/kite-syscall-tracer
cp /tmp/kite-syscall-tracer assets/node-runtime/kite-syscall-tracer-arm64
echo "BUILD-OK dynamic tracer"
