# Kite 宿主 Node 快速运行时

## 定位

Kite 宿主 Node 快速运行时是一条通用依赖能力，不是 OpenClaw 或其他最终应用的特殊发行版。它只优化满足严格合同的 Node
主进程；Ubuntu/PRoot 继续作为完整 Linux 兼容底座。

```text
资源卡、终端、Agent 或后台动作
→ Runtime Prep 识别结构化 Node 请求
→ Android/Bionic 启动器
→ 当前 Ubuntu glibc 的身份绑定兼容副本
→ kite.nodejs 提供的 Node
```

Node 创建子进程时，受管 Node 或最终 Node shebang 继续走宿主；shell、Git、Python、编译器和其他 Linux ELF 使用 Android
生成的完整 PRoot argv/env。运行时不代理每次文件 syscall，也不识别资源 ID、包名或应用名称。

## 事实来源

- loader 和 glibc 来自当前 Ubuntu rootfs 的身份绑定私有副本；
- Node 来自唯一 `kite.nodejs` 资源及其真实 ARM64 ELF；
- `/workspace` 使用当前 Space 的物理工作区，`HOME` 指向容器物理 root；
- PRoot 子进程合同由 `WorkSurfaceRuntimeBridge.buildArgvExecConfig` 生成；
- PTY、输入输出、停止、退出码、CardRun 和后台生命周期继续归既有模块。

## 启动与回退门

宿主进程创建前必须同时满足：

1. 普通 default 工作区，不是显式 View 或工程环境；
2. 容器使用 HOST 网络，Android 默认网络提供可用 DNS；
3. 命令是无需 shell 展开的单一 Node，或最终 shebang 为 Node 的受管命令；
4. loader、Node、启动器和 patched libc 都是身份可验证的 AArch64 ELF；
5. Android 能生成完整 PRoot 子进程前缀，并以严格尾标记证明 argv 没有截错。

任一能力不满足时，在进程创建前使用原 PRoot 路径。宿主进程已经开始后不自动再运行一份 PRoot，避免双实例、双写和端口
竞争。显式 View 请求不进入宿主通道。

## DNS 与 Android seccomp

Android 应用域没有 Ubuntu `/etc/resolv.conf`。Kite 在身份绑定 libc 副本中把该路径等长替换为 `/proc/self/fd/99`，由启动器
打开 Android 当前默认网络生成的共享运行时 resolv.conf 后执行 glibc loader，不硬编码公共 DNS。PRoot、Node 和通用 glibc
宿主通道必须引用同一文件；默认网络变化时原地改写文件而不替换 inode，确保已经打开 fd 的长驻进程也能看到 VPN 切换后的 DNS。

Android seccomp 拒绝部分普通发行版允许的新 syscall。兼容副本只转换已有标准回退语义：

- robust mutex 的公开 pthread API 对不支持能力返回 `ENOTSUP`；
- `clone3` 返回 `ENOSYS`，由 glibc 回退传统 `clone`；
- `io_uring_setup` 返回 `ENOSYS`，由 Node/libuv 回退线程池；
- rseq 通过 glibc 官方 tunable 关闭。

指令标记、源资产或 ABI 不匹配时，必须在进程创建前拒绝快速通道。

## 已知债务索引

以下编号是长期排障入口。不能因为某个 CLI 能显示横幅就宣布兼容问题全部消失。

| 编号 | 类型 | 当前事实 | 剩余风险与收口条件 |
| --- | --- | --- | --- |
| HN-001 | 正确性 | robust mutex 不再伪装成功；公开 API 返回 `ENOTSUP` | 绕过 pthread API 的 addon 归 HN-007；真实样本出现后补直接 syscall 矩阵 |
| HN-002 | 正确性 | ARM64 汇编跳板只对已知受阻 syscall 返回 `ENOSYS`，其余尾跳真实 glibc `syscall` | ABI、导出合同和真机 Host Node 继续作为护栏 |
| HN-003 | 正确性 | loader/libc 副本绑定源 SHA-256，只修改 ELF64 ARM64 可执行 `PT_LOAD` 段并要求精确命中 | rootfs/glibc 变化时快速通道失败关闭，必须重跑身份和段边界夹具 |
| HN-004 | 性能 | 既有小文件与 Worker 矩阵中 Host p50 均优于 PRoot | 更换 Node/rootfs/兼容层代次时重跑，不把单机最优并发写死 |
| HN-005 | 性能 | 受管 Node 子级保留宿主可减少固定成本，但 100 个独立 Node 进程仍昂贵 | 不用共享 Node VM 改变隔离；非 Node 短任务只能进入独立准入的 PRoot Runner |
| HN-006 | 路由 | `.kf/bin` 可解析受管链接、最终 Node shebang 和无副作用 wrapper | npm/npx/pnpm/openclaw 留在 Host；shell、Git、Python、编译器和 Linux ELF 继续 PRoot |
| HN-007 | 兼容性 | Host Node 看见 Android 物理 workspace/rootfs；原生 addon 可绕过 JS 子进程代理 | 建立路径、系统探测、直接 `execve/system` 和代表性 addon 矩阵；不能安全表达时启动前回退 |
| HN-008 | 覆盖面 | 资源终端、Agent 和后台结构化入口已共用 Host Node 计划器 | 新入口必须提交结构化请求，禁止在入口复制 Host/PRoot 选择 |
| HN-009 | 可观测性 | CardRun 与后台 Registry 保存实际车道和原因 | 新入口必须通过状态拥有者写入，页面不能探测或复制运行事实 |
| HN-010 | 包管理兼容 | 受管 npm/npx/pnpm CLI 可留在 Host，但真实 lifecycle script 和原生构建尚未形成发布矩阵 | 覆盖纯 JS 插件、shell lifecycle、node-gyp/编译器、失败清理和启动前整条 PRoot 回退 |
| HN-011 | 版本生命周期 | `kite.nodejs` 是唯一运行时来源，但活跃进程的旧代次租约和延迟回收仍未完成正式验收 | 升级发布新代次；旧租约退出前不回收或替换其 loader/libc/Node 资产；有反向依赖时禁止卸载 |

## 证据映射

| 编号 | 当前证据 | 状态 |
| --- | --- | --- |
| HN-001～HN-002 | `HostNodeNativeCompatibilityContractTest` 与 ARM64/C 源合同 | 自动护栏 |
| HN-003 | `HostNodeRuntimePreparerTest` 的身份、段边界、精确命中和畸形 ELF 拒绝 | 自动护栏 |
| HN-004～HN-005 | `host-node-pressure-benchmark` 既有真机矩阵；受管 Node 子进程脚本合同 | 历史性能证据有效，前提变化才重跑 |
| HN-006 | `HostNodeRuntimeTest` 与 `test-kite-node-host-runtime.mjs` 的 shebang、wrapper、spawn/exec/fork/信号 | 自动护栏加真机脚本入口 |
| HN-007 | 当前只有失败关闭和 PRoot 回退边界，尚无代表性 addon 矩阵 | 未关闭，等待真实样本矩阵 |
| HN-008 | `HostNodeLaunchPlannerContractTest`，终端、Agent、后台入口回归 | 自动护栏 |
| HN-009 | `CardRunRuntimeLaneContractTest` 与 `BackgroundRuntimeStructuredLaunchContractTest` | 自动护栏 |
| HN-010 | 受管 CLI 路由已覆盖，真实插件/lifecycle/native build 未覆盖 | 未关闭 |
| HN-011 | 单一资源来源已存在，活跃代次租约与升级回收未完成 | 未关闭 |

## 尚未关闭的专项门

- HN-010 的真实 npm/plugin 生命周期脚本与原生构建回退矩阵；
- HN-007 的代表性原生 addon 与直接 syscall；
- HN-011 的运行中旧 Node 代次租约和升级后延迟回收；
- Node/rootfs/兼容层升级后的针对性回归；
- 真实模型首个 token 属于应用链路验收，不由 `--version` 或横幅替代。

这些门按变化和真实样本增量执行，不重新运行已经有效的完整历史矩阵。

## 后续归一

Node 将作为[通用依赖快速通道](managed-runtime-fast-path.md)的首个标准 Provider 接入
[混合运行路由](runtime-provider-routing.md)。RF210 只做合同适配和等价性护栏，不重写现有兼容层。
