# rootfs symlink 权限污染与恢复（调试配方）

## 现象

App uid（`run-as com.kite.app`）下 proot 车道 execve 全部失败：

```
proot error: execve("/usr/bin/ls"): Operation not permitted
```

宿主车道不受影响（loader 直跑、`--library-path` 显式路径，不依赖 symlink 解析）。

## 根因

Android 文件系统黑盒行为（机理未深挖，实测结论）：

- **root 进程创建/触碰过的 symlink，App uid 不可读**（`ls`/`readlink`/`open` 全部 Permission denied）。
- 触碰方式包括 `chown -R`：即使属主改成 App uid，被 root chown 碰过的 symlink 照样坏。
- rootfs 全树 symlink（约 2800+ 个）被污染后，proot 翻译 PT_INTERP（`/lib/ld-linux-aarch64.so.1` → …）失败，一切动态程序 execve 报错。
- 容器内 root 会话（apt/ldconfig/chown 等）是主要污染源。

## 判定

```bash
adb shell "/system/bin/run-as com.kite.app ls -la \
  /data/user/0/com.kite.app/files/runtime/containers/ubuntu-main/rootfs/lib/ld-linux-aarch64.so.1"
# Permission denied = 污染；正常应显示 symlink 箭头
```

## 恢复（镜像重建法）

干净源是资源镜像 `runtime/images/ubuntu-noble/`（安装时从未被 root 碰过）。
**必须由 App uid 重建**（root 建的照样坏）：

```bash
cat > /data/local/tmp/restore-links.sh <<'EOS'
#!/system/bin/sh
IMG=/data/user/0/com.kite.app/files/runtime/containers/../images/ubuntu-noble
RFS=/data/user/0/com.kite.app/files/runtime/containers/ubuntu-main/rootfs
cd "$IMG" || exit 1
find . -type l | while read -r l; do
  t=$(readlink "$l")
  mkdir -p "$RFS/$(dirname "$l")" 2>/dev/null
  ln -sfn "$t" "$RFS/$l" 2>/dev/null || echo "FAIL $l"
done
EOS
adb shell "/system/bin/run-as com.kite.app sh /data/local/tmp/restore-links.sh"
```

说明：

- 只恢复镜像内的 symlink（约 1100 个核心链接：`/bin`、`/lib`、`/sbin`、`ld-linux`、alternatives 等）。
- 镜像之外的 symlink（历史上 apt 装的 gcc/binutils 工具链等约 1700 个）不恢复——那些本来就是容器 root 会话的污染产物，不在产品路径上。
- `HostNodeRuntimePreparer.repairRootfsSonameSymlinks` 会在车道准备时按 ldconfig 语义自愈两个 lib 目录的 soname 链接；全树级恢复用本配方。

## 验证

```bash
# App uid + App 真实 proot 参数跑三个入口
adb shell "/system/bin/run-as com.kite.app env \
  LD_LIBRARY_PATH=/data/user/0/com.kite.app/files/runtime/lib \
  PROOT_TMP_DIR=/data/user/0/com.kite.app/files/runtime/tmp \
  /data/user/0/com.kite.app/files/runtime/bin/proot --link2symlink -0 \
  -r /data/user/0/com.kite.app/files/runtime/containers/ubuntu-main/rootfs \
  -w /workspace -b /dev -b /proc -b /sys \
  -b /data/user/0/com.kite.app/files/runtime/shared/ubuntu-main:/workspace \
  /workspace/.kf/bin/opencode --version"
```

预期：`1.18.31`（或当前版本号）。同类验证 `node --version`、`openclaw --version`、`python3 --version`。

## 规则（AGENTS.md 已定档）

容器内 root 会话装包/改文件后，必须重跑车道准备并抽查 symlink 可读性；
禁止在 rootfs 内直接用 root 做 `chown -R` 类全树操作。
