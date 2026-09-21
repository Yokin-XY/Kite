# 宿主车道 Ubuntu 语义通用化（GUEST_TMP 提权 + spawn 路由去特判）

> 状态：实施中 · 2026-09-22 立项 · 依据 AGENTS.md《运行车道策略》

## 背景

Claude Code 桥（claude-agent-acp）经 `38f710b3`（env 剥壳）默认上宿主车道后，
其 CLI 子进程（glibc 动态 ELF）在 spawn 时因预载层路由的**目录特判**（只认
`toolchains/native-tools`）落入 proot 兜底，撞上 proot 假 root 的 uid 错位与
`/tmp` 残留检查，报 `Internal error`。同类缺口是系统性的：`/tmp`、`/var/run`、
`/dev/shm` 等硬编码路径族在宿主车道均无通用翻译。

事实链（全部通道取证，铁证）：

1. claude 本体 `claude.exe` 是 **glibc 动态 ELF**（`PT_INTERP=ld-linux-aarch64.so.1`，
   依赖 libc/librt/libpthread）——C 兼容层可拦截。
2. 预载层 `routeFile` 四类路由：loader 形态 / node 命令 / **native-tools 目录内** /
   其他落 proot。目录限制即特判。
3. GUEST_TMP 双层重写（C 兼容层符号拦截 + Node fs 钩子）仅在 `minefieldLane`
   （openclaw 声明 `openat2_degrade`）注入——特权而非能力。
4. App uid 手动驱动（纯 proot、fresh tmp）一切正常——**车道语义缺口，不是程序 bug**。

## 方案（短期：GUEST_TMP 提权 + spawn 路由通用化）

### 改动 1：GUEST_TMP 无条件注入宿主车道

`HostNodeTerminalLaunch.buildConfig`：`KITE_NODE_HOST_GUEST_TMP` 移出
`minefieldLane` 条件，宿主车道全部注入。Node fs 层 `/tmp` 重写对一切 node 程序
生效；未注入时零行为（预载层契约不变）。

`KITE_GLIBC_HOST_LOADER/LIBRARY_PATH/GUEST_TMP`（C 兼容层定位三件套）同样移出
条件：动态 glibc ELF 走兼容层的前提。`minefieldLane` 判定保留但仅剩历史语义，
后续随等价性测试覆盖后移除。

### 改动 2：spawn 路由按 ELF 特征去目录特判

预载层 `resolveNativeToolInvocation`：目录限制（`isInside(native-tools)`）放开为
rootfs 内任意动态 glibc ELF 判定（现有 PT_INTERP 读取逻辑已具备）：
- 静态 ELF：直接执行（现行为）
- 动态 glibc ELF：loader --library-path --preload compat（现 native-tool 行为）

风险与对策：兼容层 preload 到任意 glibc 程序——雷族（rseq/getrandom/syscall
直发）已在雷补丁批次拔除；等价性测试为强制关卡。

### 改动 3：车道等价性测试

同一组操作两车道各跑比对：
- `/tmp` 读写与目录自建
- `/var/run` 锁文件创建
- glibc 动态 ELF 起动（claude --version）
- node 程序 fs 访问
由 PC 经 ssh 通道驱动（`tools/device/oneplus8t/`）。

## 中期方向（另立项）

路径翻译下沉到兼容层 syscall 级（open/stat 族 rootfs 前缀重写），新 Agent
上车道零适配。本文档不覆盖。


## 实施记录（2026-09-22）

### 已落地

1. GUEST_TMP/兼容层三件套移出 minefieldLane（HostNodeTerminalLaunch，无条件注入）。
2. 预载层 spawn 路由去目录特判：rootfs/shared 任意运行时根内 ELF 按 PT_INTERP
   分流（静态直跑 / 动态走兼容层）；`.kf/bin` symlink 场（容器绝对路径目标）改为
   映射后逐层解析。
3. 等价性测试固化：`tools/device/oneplus8t/host-lane-equivalence.sh`
   （run-as + loader 形态，形态照抄 App bridge）。

### 端到端验证（通道驱动，App uid 宿主车道）

- initialize ✓ session/new ✓ session/prompt "hi" → **end_turn 7.8s**（智谱真实回复）。
- spawn claude --version 经兼容层车道返回 2.1.278。
- 验证期网络经 ssh -R 反向隧道代理（127.0.0.1:19999→PC CONNECT 代理）。

### 过程中发现的三个同族缺口（宿主车道 Ubuntu 语义缺口清单 +3）

1. **rootfs 内 symlink 的"创建者决定可读性"**（最终根因）：App uid 打开
   "非本 uid 进程创建"的 symlink 被拒（open/lstat/readlink/unlink 全拒，
   chown 无效、SELinux 标签相同）；同目录 App uid 自建的 symlink 可读。
   容器内 root 会话（apt install 触发 ldconfig 重建 soname symlink）即造出
   不可读 symlink，宿主车道 LP 搜索撞上即 EACCES；容器车道靠 ld.so.cache
   （realpath 直指真文件）绕开故无感。
   - 修复：`HostNodeRuntimePreparer.repairRootfsSonameSymlinks`——车道准备期
     以 App uid 按 ldconfig 语义（NAME.so.V → 同前缀最高版本真文件）重建
     缺失 soname 链接；坏 inode 由 root 删除后本机制负责重建。
   - 规则（记入运维常识）：**容器内 root 会话装包后，宿主车道需重跑车道准备**
     （或直接避免在 root 会话里动 lib 目录）。
2. **HOME 路径**：宿主车道程序读 $HOME/.claude/settings.json 时 HOME 为容器
   路径（/root），宿主上不存在。验证时以 HOME=rootfs/root 通过；**车道级
   HOME 映射未落地**（buildConfig 注入宿主映射 HOME，属下一步）。
3. **DNS/resolv.conf**：宿主无 /etc/resolv.conf（/etc→/system/etc 只读），
   claude（glibc 动态）报 EAI_AGAIN。App 代理（HTTPS_PROXY 38087）可绕
   （DNS 在代理端解析），但代理服务随 App 生命周期；ssh -R 隧道代理可用。
   待办：compat 层 getaddrinfo 拦截（读 KITE_GLIBC_HOST_RESOLV_CONF）。

### 事故与教训（本次自查）

- 容器内 apt install 会 ldconfig 重建 rootfs/etc/ld.so.cache（容器路径条目，
  宿主车道解析打坏）+ **重建 lib soname symlink（root 建的，App uid 不可读）**
  ——容器内装包后需重跑车道准备并验证宿主车道库解析。
- su 下 chown -R rootfs 会破坏 /run/sshd 属主（sshd 拒启）——rootfs 属主
  修改要排除 /run。
- 容器内进程写 /dev 直通宿主：apt 装包曾把宿主 /dev/null 替换成普通文件，
  修复需 mknod + **restorecon**（漏 restorecon 标签错会令全部 App 反复
  "failed to attach" 循环死）——修复设备文件务必 restorecon。

### 最终验收（App 内，2026-09-22 02:06）

宿主车道（fast agent -> host_node）Claude Code 会话页发送 "hi" →
glm-5.3-flash 9 秒真实回复，状态"准备就绪"，无 Internal error。

## 验收

1. 等价性测试两车道全绿。
2. Claude Code App 内发消息端到端成功（用户验收视觉，通道取 stderr 证据）。
3. openclaw 车道回归（雷区不复活）。
