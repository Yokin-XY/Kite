# 性能加速二期计划

> 一期已完成：Node.js/Python/uv/pnpm 通过 glibc Host 原生执行（3-10x 加速）。
> 二期目标：把 Agent 最高频调用的工具也从 PRoot 加速到原生层。

## 核心原理

```
当前架构（已加速的部分标 ✓）：

  Claude Code / Codex (Node.js)     ← ✓ glibc Host 原生执行
    ├── spawn('rg', ...)             ← ✗ 走 PRoot（apt 版），每次搜索慢 3-10x
    ├── spawn('fd', ...)             ← ✗ 走 PRoot
    ├── spawn('jq', ...)             ← ✗ 走 PRoot
    ├── spawn('git', ...)            ← ✗ 走 PRoot
    └── spawn('node', ...)           ← ✓ 继承原生层

关键洞察：原生 Node.js spawn 子进程时，子进程也在 Android 内核上运行。
只要子进程的二进制能在原生层执行（aarch64 linux-gnu），就自动绕过 PRoot。
```

## 方向一：预构建原生工具（短期，收益最大）

把 Agent 最常用的工具换成预构建的 aarch64 linux-gnu 二进制，
放入 toolchain，被原生 Node spawn 时自动走原生层。

### 按优先级排序

| 优先级 | 工具 | 原因 | 体积 | 版本 | 链接方式 |
|--------|------|------|------|------|----------|
| P0 | **rg** (ripgrep) | Agent 搜代码最高频，每次对话多次调用 | 5 MB | 14.1.1 | glibc 动态 |
| P0 | **fd** | Agent 找文件，仅次于 rg | 3 MB | 10.2.0 | glibc 动态 |
| P1 | **jq** | JSON 解析，API 交互必用 | 2 MB | 1.7.1 | 静态 |
| P2 | **git** | commit/diff/push，依赖多 | - | - | 需要评估 |
| P3 | **zstd/zip** | 压缩解压 | - | - | 已有原生归档替代 |

### 实现步骤

1. 下载官方预构建 aarch64 linux-gnu 二进制
2. 放入 `assets/toolchain/ai-dev-pack/packages/`
3. 修改 `install.sh`：
   - 新增 `install_native_search_tools()` 函数
   - rg/fd/jq 优先用预构建版（不再从 rootfs 链接）
   - 安装到 `/workspace/.kf/toolchains/native-tools/`
4. 更新 `kite.tool.env` 清单
5. APK 体积增加约 10 MB，换取 Agent 核心操作 3-10x 提速

### 验证方法

在 OnePlus 8T 上：
1. 安装 Claude Code
2. 在终端跑 `which rg` → 应指向 `native-tools/rg`
3. 跑 `time rg "pattern" /workspace/` → 对比 PRoot 版速度
4. 让 Claude Code 做一次代码搜索 → 观察速度提升

## 方向二：Android 原生能力直通（中期，更深度）

用 Rust/C 写 Android/Bionic 原生工具，通过已有的桥接层接入 Ubuntu。
比方向一更快（Bionic 直调内核，无 glibc 开销），但每个工具需要单独开发。

| 能力 | 实现方式 | 工作量 | 依赖方向一 |
|------|----------|--------|-----------|
| 原生 rg | 用 Rust crate `grep-searcher` 编译 Bionic 版 | 2-3 天 | 否（可独立） |
| 原生 fd | 用 Rust `walkdir` + `ignore` 编译 Bionic 版 | 1-2 天 | 否 |
| 原生 git | 桥接 libgit2 或调用系统 git | 5+ 天 | 否 |
| 原生 HTTP | 用 OkHttp/Rust reqwest 替代 curl | 2 天 | 部分替代 |

### 与方向一的关系

- 方向一是"用现成的预构建二进制"——立即可做，收益立竿见影
- 方向二是"自己编译 Bionic 原生版"——更极致，但需要开发
- 建议先做方向一验证效果，效果好再做方向二进一步优化

## 方向三：ZCode 加速（远期）

ZCode 目前是 deb 包安装，在 PRoot 里跑，未走 glibc Host。
如果有需要，可以：
1. 提取 ZCode 的核心二进制
2. 配置 glibc Host wrapper
3. 但 ZCode 更新频繁且是闭源的，维护成本高

## 不加速的（维持现状）

| 工具 | 原因 |
|------|------|
| bash/sh/coreutils | 就是 Ubuntu 环境本身，不可也不需要加速 |
| apt/dpkg | Ubuntu 包管理器，只在 PRoot 内部用 |
| curl/wget | 已被 Android 原生下载能力替代（大部分场景） |
| zip/unzip | 已被 Android 原生归档能力替代 |

## 变更日志

| 日期 | 变更 |
|------|------|
| 2026-09-17 | 计划创建；P0（rg/fd/jq）已准备好二进制和 install.sh 修改 |
| 2026-09-17 | 真机验证发现 P0 缺失关键环节：preload 的 `routeFile()` 把所有非 Node 子进程路由回 PRoot，原生工具从未绕过 PRoot。已补上原生工具车道（`resolveNativeToolInvocation`：基于 native-tools 目录 + ELF 头识别，动态 ELF 走 patched loader 车道、静态 ELF 直接 exec）。OnePlus 8T 实测：rg 同搜索任务 Host 车道中位 15.3ms vs PRoot 31ms（约 2x）。另发现 toolchain pack 缓存基于文件存在性，v19→v20 的重提取未发生，需版本化（下项优先） |
