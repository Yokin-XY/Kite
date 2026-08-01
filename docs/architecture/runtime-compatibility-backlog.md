# 混合运行底座兼容性债务总账

## 用途

本总账只保存已经验证的“不支持”“尚未生产化”和“必须走兼容车道”边界，避免后续遗忘原因或重复把单一样例当成完整支持。它不是修复计划，也不改变当前路由。默认兼容原则始终是：快速/原生 Provider 不能完整证明请求时，在任何业务进程创建前整条选择 Ubuntu/PRoot；已经开始执行后不自动重放第二份任务。

## 通用依赖快速通道

| 编号 | 当前未放行能力 | 已确认原因 | 当前兼容路线 | 未来候选，不代表承诺 |
| --- | --- | --- | --- | --- |
| FAST-NODE-01 | 任意 npm lifecycle 与原生构建 | lifecycle 可启动 shell、编译器和 Linux ELF，尚无完整发布矩阵 | 创建 Node 前整条 PRoot | 对纯 JS、shell lifecycle、node-gyp 分层验证 |
| FAST-NODE-02 | 任意 Node 原生 addon/直接 syscall | addon 可绕过受管 child 路由，Android seccomp 与 Ubuntu 语义未全覆盖 | 未验证 addon 请求走 PRoot | 按 ABI、导入闭包和 syscall 矩阵肯定式开放 |
| FAST-NODE-03 | 运行中 Node 代次的自动替换/卸载 | 旧进程仍持有 loader、libc、Node 和包资产 | 当前唯一 `kite.nodejs` 保留，不允许活动依赖被替换 | 不可变新代次加活动租约和延迟回收 |
| FAST-PY-01 | Python `subprocess`、`os.system`、`execve` 的完整 Linux 子进程语义 | Host 可命中 Android `/bin`，或无法直接启动 Linux ELF；失败发生在 Python 已启动后 | 调用方不能证明无子进程时整条 PRoot | 独立全 PRoot Python，或未来 fork-safe 通用 child relay |
| FAST-PY-02 | venv 子解释器和 `venv(with_pip=True)` | venv 文件可创建，但子解释器/ensurepip 生命周期不能由当前 Host 合同完整兑现 | 需要 venv 生命周期的请求走 PRoot；当前失败能力不伪装成功 | 双 Python 产品策略、受管 uv 生命周期或可验证 venv Provider |
| FAST-PY-03 | 任意第三方 C 扩展、manylinux wheel、`dlopen` 和直接 syscall | 当前只证明精确 CPython ABI 与固定导入闭包，不能外推 | 缺少精确 ABI/代次证据时走 PRoot | 按 ABI、wheel 资产、导入闭包和不可变代次逐类开放 |
| FAST-PY-04 | 网络 pip、编译工具链和任意包升级 | 安装脚本和编译器可能创建外部进程；原地 target 升级会残留旧元数据 | 完整安装生命周期走 PRoot | 新代次安装、验证、切换与旧租约回收 |
| FAST-GIT-01 | direct Host Git | hooks、filters、pager、remote helper、submodule 等外部 child 无法在启动前完整预判 | Git 继续 PRoot | 只有通用 child relay 完整保持语义后重开 |
| FAST-RELAY-01 | unrestricted glibc child relay | missing/EACCES/坏 shebang 的同步错误会变成 wrapper 的异步退出；fork/lifecycle 也未闭环 | 不打入正式 compat 资产 | 重新设计 fork-safe relay，并保持 fd、信号、errno、退出和唯一执行 |

证据回指：[宿主 Node 快速运行时](host-node-runtime.md)、[宿主 Python 性能与兼容矩阵](host-python-performance-matrix.md)、任务 `RF1200` 与 `RF1300`。

## Android 原生能力

| 编号 | 当前未放行能力 | 已确认原因 | 当前兼容路线 | 未来候选，不代表承诺 |
| --- | --- | --- | --- | --- |
| NATIVE-ARCHIVE-01 | 将资源 ZIP 默认迁到 Android 安全解包 | 正确性通过，但 OnePlus 8T 固定矩阵比 PRoot 慢约 202.8% | 资源安装继续现有 PRoot/事务链；原生 ZIP 只供显式受控调用 | 更换实现或平台能力后重跑同一固定矩阵 |
| NATIVE-ARCHIVE-02 | tar、tar.gz、xz 等任意归档原生化 | 尚无统一安全解析、权限/链接语义和性能证据 | 继续 PRoot 工具链 | 仅在多个正式调用方和固定安全合同出现后立项 |
| NATIVE-SHELL-01 | 把任意 shell 文本编译为 Android 原生动作 | shell 展开、管道、重定向、环境和副作用无法靠轻量解析等价复制 | 任意 shell 继续 PRoot | 只增加枚举化、结构化的单项原生能力 |

证据回指：[Android/NDK 原生能力](native-capability-provider.md)与任务 `RF300`。

## Ubuntu/PRoot 兼容与调度

| 编号 | 当前未放行能力 | 已确认原因 | 当前兼容路线 | 未来候选，不代表承诺 |
| --- | --- | --- | --- | --- |
| PROOT-SCHED-01 | 终端/Agent 按整个会话占用长期 managed-owner 槽 | 会话可长期空闲，会把吞吐保护错误变成会话数量限制 | 保留现有终端/Agent 生命周期；不占后台长期槽 | 只协调真实进程启动窗口或设计独立交互容量模型 |
| PROOT-SCHED-02 | 自动性能升档 | 当前没有可信 thermal 信号，失败率也不能证明由并发造成 | 内存高压可降档；升档由固定用户档位/校准结果决定 | 接入可信温度与因果窗口后重开 |
| PROOT-SCHED-03 | 通用 PRoot 启动窗口协调 | 两套真机矩阵没有稳定降低 batch wall/P95，部分等待反而增加 | 正式入口保持现状 | 运行时或设备代次改变后按原矩阵复验 |
| PROOT-RUNTIME-01 | 用库存 PRoot 替换正式 v23 | 库存来源未知，缺少 lifecycle、registry、保护与 View；约 100ms 差异不由六层 Kite patch、loader 或 NDK 版本造成 | 正式 v23 保持不变 | 获得同源、同能力、可复现候选后先过语义矩阵 |
| PROOT-RUNTIME-02 | lifecycle registry 局部减费 | 单写、持久 fd、活跃计数三个候选保持事件语义但没有端到端收益 | 保留完整生命周期与强身份 | 若重构原子快照协议，必须同时覆盖恢复、gap、异常中断和旧 reader |

证据回指：[统一 PRoot 容量](unified-proot-capacity.md)、[启动窗口协调](proot-launch-window-coordination.md)、[活跃运行时开销](proot-active-runtime-overhead.md)与任务 `RF400`～`RF1440`。

## 产品层后续组合方向

这些方向只用于防止遗忘，不在 RF1500 实施：

1. 保留唯一受管 Python 的双车道路由：能提交肯定式保证的请求走 Host，其余在进程创建前走 PRoot。
2. 若用户需要完全独立的 Linux Python，可提供第二个明确标识的 PRoot Python 环境；它不能覆盖或替换快速 Python 的资产和命令所有权。
3. 包管理和扩展安装采用“新代次安装 → 验证 → 切换 → 活动租约退出后回收”，不允许原地覆盖正在使用的依赖。
4. 任何新快速能力先按依赖/ABI/语义选择，不按 OpenClaw、资源 ID、页面或应用名称选择。
