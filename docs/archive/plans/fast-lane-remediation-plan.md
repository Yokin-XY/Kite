# 快速通道整改方案（讨论稿）

> **已归档**（2026-09-21）· 快速通道整改计划。P1/P3 与 HostPython 已落地（924f1f53/f02ab6a7/2f82ed63/e7fb01fc），P2 一期完成（b3a90a1b）；剩余默认反转与收编由现行主计划 docs/plans/ubuntu-fast-lane-plan.md 接管。

> 2026-09-19 · 状态：P1 核心已落地（`924f1f53`）· 依据：两份只读审计
> - 运行通道决策审计：`local-artifacts/runtime-lane-decision-audit.md`
> - 功能面通道审计：本文附录

## P1 落地记录（2026-09-19，真机 OnePlus 8T 实证）

- `HostNodeRuntimeProvider` 删除 FILESYSTEM_VIEW 一票否决；node/node-shebang 命令的容器语义路径参数统一翻译。
- pi 清单 requirements 去除 full_linux。
- 真机实测：pi 桥宿主直跑（patched loader，无 proot 包裹，glibc 资产自动补齐）；boot→ModelRuntime.create **39ms**（原 proot 车道温启动约 11 秒、冷启动 85~231 秒）；建会话链 19ms；prompt 回复 4.7s（纯网络+模型）。
- 工具执行验证：bash 子进程经预载层自动回落 proot，cwd 语义保持 /workspace 视图（宿主物理路径自动双向映射）。
- 待办：P1 剩余项（会话管理命令去 FULL_LINUX、supervisorctl 原生化、版本探测治理）与 P2/P3 未动。

## 一、用户判断的验证结果

> "每次别人使用的时候，它（快速通道）都没有被当作默认选项或者必选项，总是被绕开"

**方向对，但要修正精度**——不是"全部被绕开"，而是**绕开集中在四个结构点**：

| 判断 | 实情 |
|---|---|
| 快速通道总被绕开 | **决策默认方向确实是 proot**：Host Provider 是严格白名单（requirements 必须为空+argv 可解析+双 guarantee+ABI 证据），**任何一票否决就静默回退 proot**；proot 则除 ANDROID_NATIVE 外无条件兜底 |
| 下载也走 proot | **静态下载已原生**（HttpURLConnection+SHA256+Rust 解包）；**动态"最新版"制品下载仍走 proot curl** |
| 查询也走 proot | **结构化查询已原生**（清单/元数据/目录全 Android NIO）；**命令式兜底确实起独立 proot**（无元数据资源的版本检查每次冷启一个 proot 跑 `--version`）；**supervisorctl 快照在 warm proot 池跑**（本质只是对 127.0.0.1:19001 的 HTTP 查询） |

## 二、四个结构点（问题清单）

### 1. FULL_LINUX 成了万能否决牌
- 仅 2 份清单显式声明（openclaw、pi），但 **7+ 处代码硬编码**：会话管理命令、recipe shell 全家、端口型后台依赖（bindPort/health 一刀切）、一次性命令、交互终端
- 语义混杂：**"我要读写容器路径的配置文件"≠"我需要完整 Linux 根"**——前者用路径翻译（env 覆写）就能上 Host
- FILESYSTEM_VIEW 和 FULL_LINUX 在 Host Provider 里被同样拒绝（`HostNodeTerminalLaunch.kt:30-42`），中间档名存实亡

### 2. 只有 3 条入口接入 lane 决策（Planner）
- Agent 主进程 argv、后台结构化命令、recipe 非空命令——**其余入口在构造点直接钦定 proot**
- 最典型：tools tab 交互终端根本不经过 Planner（`/bin/bash --login`+FULL_LINUX+INTERACTIVE_PTY 直接构造），用户手敲 node/python 全在 proot 里跑

### 3. Host 就绪依赖安装期产物，缺件即静默回退
- glibc 补丁资产（loader/libc/launcher/compat）换代或缺失 → `unsupported` → 回退，**无补齐、无可见性**
- 8T 清空重装后 Host 通道从未生效过（`.kf/system/glibc-runtime/host/` 不存在），但没有任何面板能看到这件事

### 4. 版本探测的浪费
- official_command 源的"最新版本"探针是 `echo '<常量>'`——**读 APK 内已知字符串也拉一次 proot**
- installed 版本探测：无结构化元数据 → 独立 proot 进程跑命令（非 warm 池）

## 三、方案（三层，不写单程序特例）

### A. 决策反转 + 合同收紧（核心）
1. **requirements 语义拆分**：
   - `FULL_LINUX` 收窄为"真需要 Linux 根/特权"（systemd、Linux 专属 syscall、/proc 特定内容）
   - `FILESYSTEM_VIEW` 激活为中间档："需要容器文件系统视图"→ **由 adapter 做路径翻译**（`PI_CODING_AGENT_DIR` 式 env 覆写）后走 Host
   - 新增声明需在清单里记录理由（评审可查）
2. **默认方向反转**：node/python 可解析 → **默认 Host**；声明"必须容器"才回落
3. **回退不静默**：lane+reason 进诊断面板（已有 `lastLaunchLane` 事实，补消费方）

### B. 入口收编（消灭构造点钦定）
按收益排序（功能面审计结论）：
1. **supervisorctl 快照原生化**：换成对 19001 的原生 HTTP 查询（loopback 探针同款模式）——纯 HTTP 调用没必要起 proot
2. **会话管理命令去 FULL_LINUX**（opencode 会话列表/删除每次起 proot——只是读 JSONL 文件）
3. **版本探测治理**：echo 常量探针消灭；命令式兜底优先补结构化元数据（安装期写好，查询期零进程）
4. **交互终端进 Planner**：手敲 node/python 走 Host（bash 仍 proot）
5. **动态 latest 制品下载**：URL 模板静态+有签名的场景并入原生下载
6. git 维持 proot（RF1230 已判 no-go，有子进程语义缺口实锤——不动）

### C. 观测先行（防再犯）
- 工程验收面板：Host 尝试率/回退率/回退原因 top
- 架构文档更新：runtime-provider-routing.md 补"新增 FULL_LINUX 需要理由"

## 四、分期建议

| 期 | 内容 | 预期收益 |
|---|---|---|
| P1 | pi 清单去 full_linux + 桥 env 双模式；会话管理命令去 FULL_LINUX；supervisorctl 原生化 | pi 会话启动 85s→秒级；后台探测减负 |
| P2 | requirements 语义拆分 + 默认反转 + 终端收编 | 结构性根治"绕开" |
| P3 | lane 诊断面板 + 回退率指标 | 可见性，防回潮 |

## 附录：功能面通道现状总表

| 功能 | 现走通道 | 判定 |
|---|---|---|
| 静态资源下载+解包 | 原生（HttpURLConnection/Rust JNI） | ✅ |
| 清单/目录/Skill 扫描/结构化元数据 | 原生（Android NIO） | ✅ |
| loopback 健康探测 | 宿主 Java Socket | ✅ |
| 远端版本号发现/商店/模型预设 | 原生 HTTP | ✅ |
| node-shebang ACP Agent（codex/claude/opencode/kimi/gemini 等） | Planner 路由（可上 Host） | ✅ 通道在 |
| rootfs 提取 | APK 资产 Rust 解包 | ✅ |
| recipe shell（安装/验证/npm/pip/git） | proot | 合理（真需要 Linux） |
| **openclaw/pi 会话** | proot（full_linux 声明） | ❌ 待改 |
| **命令式版本探测兜底** | 独立 proot | ❌ 待改 |
| **supervisorctl 快照** | warm proot 池 | ❌ 待改（可原生 HTTP） |
| **会话管理命令** | proot（硬编码） | ❌ 待改 |
| **交互终端** | proot（构造点钦定） | ⚠️ P2 |
| **动态 latest 制品下载** | proot curl | ⚠️ 低收益 |
| git | proot（RF1230 no-go） | 保持 |

## 全局加速（HostPython 车道）真机预检结论 2026-09-20

预检方式：root shell 下 patched-loader 直跑 rootfs python3.12（与宿主 node 车道同构：
`ld-linux-aarch64.so.1 --library-path <rootfs>/usr/lib/aarch64-linux-gnu:...`），逐层验证。

### 已实证（OnePlus 8T）

1. rootfs `/usr/bin/python3.12`（3.12.3）宿主直跑正常，stdlib 自动归位（sys.path 指向 rootfs 树）。
2. hermes venv（26 个 C 扩展，cpython-312 ABI）宿主 import 全绿：pydantic_core 2.46.4、
   yaml(_yaml.so)、anydoc(.so)、rich 等；acp_adapter 入口 import 仅 0.08s（proot 冷启 ~10s）。
3. `acp.stdio_streams`（connect_read_pipe/connect_write_pipe）宿主直跑读写回环正常。
4. hermes-acp 完整 initialize 回包（agentInfo hermes-agent 0.21.3）+ 插件发现 58 个 + SQLite 可用。

### ABI 决策

venv 是 rootfs python3.12 生态（ABI cpython-312）；独立资源 kite.python 是 3.14.6，ABI 不匹配。
正解：HostPython 车道按 node 车道同构方式直跑 rootfs python3.12（venv 语义 = rootfs python +
site-packages），而不是迁移 hermes 到 3.14 重建 venv。

### 已知环境差异点（车道实现必须处理）

1. venv 是 editable 安装：`__editable___hermes_agent_*_finder.py` 硬编码容器路径
   `/workspace/.kf/software/...`，宿主下失效。解法：PYTHONPATH 前置源码树根目录（等效覆盖
   顶层模块映射）+ site-packages。
2. hermes lazy_deps：initialize 期间会 pip 子进程懒安装（如 boto3），宿主下不可行且阻塞回包。
   解法：`security.allow_lazy_installs: false`（config.yaml），FeatureUnavailable 优雅降级，
   核心 provider 不受影响。该配置的注入方式（安装 ensure vs 清单声明）实现时定。
3. asyncio stdio 要求管道/字符设备：`< file` 重定向不行（真实 ACP 客户端是 ProcessBuilder 管道，
   不受影响；仅预检脚本陷阱，双层 cat 会秒 EOF 让进程优雅退出、回包来不及写——排查时勿被误导）。

### 实现清单（下一步）

- HostPythonCommandResolver：支持 rootfs 系统布局（/usr/bin/python3.12 + /usr/lib/python3.12 +
  libpython3.12.so.1.0 于 /usr/lib/aarch64-linux-gnu），现合同只认 .kf/software 受管布局。
- hermes 清单：argv 改 `["python3.12", "/workspace/.kf/.../venv/bin/hermes-acp"]` +
  runtimeGuarantees(no_child_process, verified_native_imports) + evidence pythonAbi=
  cpython-312-aarch64-linux-gnu + environment PYTHONPATH（源码树+site-packages，容器路径由
  mapEnvironment 映射）。
- 真机验收：hermes 会话 ≤4s + 遥测 host_python 车道实锤 + P3 面板命中率提升。
