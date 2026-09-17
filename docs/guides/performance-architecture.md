# Kite 性能优化架构：glibc Host 原生执行系统

> 本文档系统性记录 Kite 的性能加速层——哪些工具已加速、走什么机制、哪些还没来得及。
> 任何涉及运行时分发、安装位置、外部化的决策必须先对照本文档。

## 一、问题背景

Kite 的 Agent（Claude Code、Codex 等）都是 Linux/glibc 程序，正常跑在 PRoot Ubuntu 里。
PRoot 通过 ptrace 翻译每个系统调用，性能损耗 **3-10 倍**：

```
PRoot 方式：  App → PRoot ptrace 翻译 → Ubuntu 内核接口 → Node.js → 你的代码
原生方式：    App → Android 内核（本身是 Linux）→ Node.js → 你的代码
```

用户为此建了一套 glibc Host 原生执行系统，让关键运行时绕过 PRoot 直接跑在 Android 上。

## 二、架构总览

```
┌─────────────────────────────────────────────────────┐
│                    Android App                       │
│  ┌───────────────┐  ┌─────────────────────────┐    │
│  │   UI / Kotlin  │  │  GlibcHostRuntimePreparer │    │
│  │   KiteBridge   │  │  (准备启动器+补丁加载器)  │    │
│  └───────┬───────┘  └───────────┬─────────────┘    │
│          │                        │                  │
│          ▼                        ▼                  │
│  ┌───────────────┐  ┌─────────────────────────┐    │
│  │  kf-runner     │  │  kite-glibc-host-launcher│    │
│  │  (进程管理)     │  │  (Bionic链接,~6KB)      │    │
│  └───────────────┘  └───────────┬─────────────┘    │
│                                   │                  │
│          ┌────────────────────────┘                  │
│          ▼                                           │
│  ┌──────────────────────────────────────────┐       │
│  │  Ubuntu glibc Loader（打了补丁）           │       │
│  │  + libkite-glibc-compat.so（兼容层）      │       │
│  │  + Node.js / Python 二进制               │       │
│  │  → 直接在 Android 内核上运行              │       │
│  └──────────────────────────────────────────┘       │
│                        ↑ 桥接                        │
│  ┌──────────────────────────────────────────┐       │
│  │  PRoot Ubuntu（文件系统+Shell 环境）       │       │
│  │  Agent 在这里看到 node/python 命令         │       │
│  │  但实际执行在 Android 原生层              │       │
│  └──────────────────────────────────────────┘       │
└─────────────────────────────────────────────────────┘
```

## 三、已加速的运行时

### 3.1 Node.js（✅ 已加速）

| 项目 | 值 |
|------|-----|
| 版本 | v26.4.0 |
| 来源 | 官方 linux-arm64 tar.xz，APK 内置 |
| 执行方式 | **Android 原生**（glibc host launcher） |
| 启动器 | `kite-glibc-host-launcher-arm64` |
| 兼容层 | `libkite-glibc-compat.so` |
| 补丁 | glibc loader 和 libc 打了 syscall 补丁（`set_robust_list`/`clone3`/resolver 路径） |
| wrapper | `/workspace/.kf/bin/node` → 设置 `LD_LIBRARY_PATH` → 直接 exec |
| 性能提升 | **~3-10x**（绕过 PRoot ptrace） |
| 影响范围 | 所有 npm 系 Agent（Claude Code、Codex、OpenCode、OpenClaw、Gemini CLI 等） |

关键代码：
- `GlibcHostRuntimePreparer.kt` — 准备补丁后的 loader/libc/兼容层
- `HostNodeRuntime.kt` / `HostNodeRuntimePreparer.kt` — Node 专用布局和补丁
- `HostNodeTerminalLaunch.kt` — 终端启动时使用原生 Node
- `native/kite-glibc-host/kite-glibc-host-launcher.c` — C 启动器源码
- `native/kite-node-host/kite-node-host-launcher.c` — Node 专用启动器
- `assets/glibc-runtime/` — APK 内置的启动器 + 兼容层二进制

### 3.2 Python（✅ 已加速）

| 项目 | 值 |
|------|-----|
| 版本 | CPython 3.14.6 |
| 来源 | python-build-standalone aarch64 install-only，APK 内置 |
| 执行方式 | **Android 原生**（glibc host launcher） |
| wrapper | `/workspace/.kf/bin/python3` → 直接 exec |
| 性能提升 | **~3-10x** |
| 影响范围 | 所有 Python 系工具（Hermes、uv 管理的包） |

关键代码：
- `HostPythonRuntime.kt` — Python 专用布局和 RuntimeProvider

### 3.3 uv（✅ 已加速）

| 项目 | 值 |
|------|-----|
| 版本 | 0.11.25 |
| 来源 | astral.sh 官方 aarch64 GNU 二进制，APK 内置 |
| 执行方式 | **Android 原生**（glibc 兼容层加载） |
| wrapper | `/workspace/.kf/bin/uv` |
| 性能提升 | **~3-5x** |
| 影响范围 | Python 包安装/管理 |

### 3.4 pnpm（✅ 已加速——通过 Node 间接加速）

| 项目 | 值 |
|------|-----|
| 版本 | 11.9.0 |
| 来源 | npm registry tgz，APK 内置 |
| 执行方式 | 通过原生 Node.js 执行（`exec node pnpm.cjs`） |
| wrapper | `/workspace/.kf/bin/pnpm` |
| 性能提升 | 随 Node 加速 |

## 四、Android 原生能力（不走 PRoot 的操作）

除了运行时加速，还有一组 **Android 原生能力**——直接在 Android 层执行、不进 PRoot：

| 能力 | 文件 | 用途 |
|------|------|------|
| **原生下载** | `AndroidNativeDownloadCapability.kt` | 大文件下载走 Android 网络栈（不用 PRoot 里的 curl/wget） |
| **原生文件操作** | `AndroidNativeFileCapability.kt` | 文件读写/移动直接走 Android FS（不用 PRoot shell） |
| **原生归档** | `AndroidNativeArchiveCapability.kt` | tar/zip 解压走 Rust 库 `kite-archive-rs`（不用 PRoot tar） |
| **原生 JSON** | `AndroidNativeStructuredJsonStringProvider.kt` | 结构化 JSON 处理 |
| **ADB 自桥接** | `HostSelfAdbBridgeWorker.kt` | App 自己作为 ADB 服务端 |
| **HTTP 代理** | `AndroidRuntimeHttpProxy.kt` | 运行时 HTTP 代理（Agent 网络请求） |

## 五、进程管理基础设施

| 组件 | 位置 | 用途 |
|------|------|------|
| **kf-runner** | `native/kf-runner/` | 高性能进程执行器（C 实现，帧协议，替代 shell fork/exec） |
| **glibc child relay** | `native/kite-glibc-child-relay/` | 管理原生 Node/Python 的子进程 |
| **HostProcessTerminator** | foundation/runtime | 原生进程终止 |
| **HostProcessSnapshot** | foundation/runtime | 原生进程快照 |
| **WarmProotRunnerPool** | foundation/runtime | PRoot 预热池（减少冷启动） |

## 六、未加速的（仍在 PRoot 里跑）

| 工具 | 说明 | 加速可行性 |
|------|------|-----------|
| **git** | Ubuntu apt 安装，在 PRoot 里 | 可加速（git 有静态编译版） |
| **curl/wget** | Ubuntu 自带 | 已被 Android 原生下载能力替代（大部分场景） |
| **jq/rg/fd/zip/unzip** | apt 安装 | 部分已被原生归档能力替代 |
| **apt 包管理器** | PRoot 内部 | 不可加速（Ubuntu 专属） |
| **bash/sh** | PRoot 内部 | 不可加速（shell 本身就是 Ubuntu 环境） |
| **Hermes Agent** | Python 程序 | 随 Python 加速（uv sync 安装的包在原生 Python 里跑） |
| **codex-relay** | Python 包（uv tool install） | 随 Python/uv 加速 |
| **ZCode** | deb 包安装 | **未加速**（dpkg 解包的 binary 直接在 PRoot 里跑） |
| **Codex 兼容协议桥** | pypi 包 | 随 Python/uv 加速 |

## 七、对外部化方案的约束

基于以上盘点，外部化方案必须遵守：

### 可以外部化（不影响性能）

| 资产 | 方案 |
|------|------|
| **Ubuntu rootfs**（93 MB） | 首次下载后仍作为 glibc loader/libc 的来源；PRoot 环境本身不加速 |
| **系统工具**（git、curl 等） | `apt install` 安装在 PRoot 里即可（它们不是性能瓶颈） |

### 不能外部化（会破坏加速）

| 资产 | 原因 | 正确做法 |
|------|------|----------|
| **glibc host launcher**（6 KB） | APK 内置，Android/Bionic 编译 | **保留在 APK** |
| **glibc compat 库**（67 KB） | APK 内置 | **保留在 APK** |
| **Node.js 二进制**（31 MB） | 必须是官方 linux-arm64 版（走 glibc host） | 可以下载，但**不走 apt**——从我们的镜像下载官方 linux-arm64 包 |
| **Python 二进制**（29 MB） | 必须是 python-build-standalone aarch64 版 | 同上 |
| **uv 二进制**（23 MB） | 官方 aarch64 GNU 版 | 同上 |

### 下载策略

外部化 Node.js/Python/uv 时：
- 下载的是**与当前 bundled 版本完全相同的二进制**（官方构建，非 apt 编译版）
- 下载后安装到 `/workspace/.kf/toolchains/`（不是 PRoot 的 `/usr/bin/`）
- wrapper 仍然走 glibc host launcher（维持原生执行）
- **不使用 `apt install`**（apt 版本是为 Ubuntu 环境编译的，可能不兼容 glibc host 补丁）

## 八、变更日志

| 日期 | 变更 |
|------|------|
| 2026-07-31 | 提取通用 glibc Host 原生资产（RF240a） |
| 2026-08-01 | 建立通用 glibc child relay（RF1310-1340） |
| 2026-08-03 | 修复直接执行代理驻留（aaa8e4ac） |
| 2026-09-16 | 本文档创建；确认 Node.js/Python/uv/pnpm 已加速，ZCode 未加速 |
