# 混合运行路由总架构

## 目标

Kite 同时保留三种执行能力，但不让资源卡、页面或最终应用自己选择运行方式：

```text
结构化执行请求
→ Runtime Planner
→ 精确匹配一个 Provider
   ├─ Android/NDK 原生能力
   ├─ 通用依赖快速通道
   └─ Ubuntu/PRoot 兼容底座
→ 创建唯一进程或执行一次原生能力
→ 原状态拥有者记录实际通道、结果和回退原因
```

这三种能力是同一运行底座的三条车道，不是三个平行产品。快速通道负责确定性收益，原生能力负责 Android 已能直接兑现的
通用操作，PRoot 负责完整 Linux 兼容性。

## 当前基线与目标差距

当前已经具备：

- `HostNodeRuntimeProvider` 与 `HostNodeLaunchPlanner`，可在创建进程前选择 Host Node 或 PRoot；
- 终端、Agent、后台运行的结构化 Node 入口；
- `runtimeLane`、`runtimeFallbackReason` 在 `CardRunStore` 与后台运行记录中的事实保存；
- `ProotJobAdmissionController`、温热 Runner 协议和固定维护任务的第一条生产接线；
- PackageInstaller、网络、文件保护、Keystore 等分散的 Android 原生实现。

当前缺口不是重新实现 Node 或 PRoot，而是缺少入口无关的请求与 Provider 合同，原生能力也尚未作为统一运行选择的一等参与者。

## 术语

- **Execution Request**：入口提交的结构化意图。它表达 executable/argv 或明确的原生能力、cwd、env、stdio、语义要求和
  运行身份，不用普通 shell 字符串代替 argv。
- **Provider**：判断自己能否完整兑现请求，并生成执行计划的能力提供者。
- **Runtime Planner**：在任何业务进程创建前比较 Provider 结果并选择唯一计划。
- **运行车道事实**：实际选择的 Provider 和稳定原因。卡片运行写入 `CardRunStore`，后台运行写入
  `BackgroundRuntimeRegistry`。
- **调度 lane**：PRoot 内部的 `INTERACTIVE/SERVICE/BUILD/PROBE` 等准入类别。它与 Provider 选择是两个维度，不能混用。

## 统一请求必须表达的事实

正式类型由 RF120 落地，但合同至少需要表达：

1. 请求与 owner 身份；
2. 结构化 executable/argv，或枚举化原生能力及参数；
3. cwd、env、stdio 和交互要求；
4. 所需平台语义，例如完整 Linux、Android API、解释器、ABI、网络和文件能力；
5. 是否允许启动前回退；
6. 副作用阶段：尚未创建、已创建、已开始执行；
7. 取消、超时、结果和进程树归属。

请求不得包含“OpenClaw 走 Host”“Git 走 PRoot”之类的产品结论。资源只声明依赖和真实执行需求。

## Provider 选择

Provider 返回三类结果：

- `Ready`：能力完整，给出唯一执行计划和实际通道证据；
- `Unsupported`：当前 Provider 无法完整表达，可在尚未创建进程时继续比较后续 Provider；
- `Blocked`：请求本身不安全、身份不一致或合同损坏，必须失败关闭，不能用 PRoot 掩盖错误。

选择原则：

1. 明确的 Android 能力请求优先由原生 Provider 处理；
2. 结构化解释器请求由相应通用运行时 Provider 判断；
3. 要求完整 Linux 语义、复杂 shell、Linux ELF 或不满足快速门时进入 PRoot；
4. 一旦原生调用或业务进程开始，不再自动执行第二条车道；
5. 回退只转换能力不满足，不吞掉已开始任务的真实失败。

## 与 Action Runtime 固定框架的关系

```text
Action Intake
→ Run Instance
→ Orchestrator
→ Runtime Prep + Runtime Planner
→ 唯一进程/能力调用
→ Surface Binding
→ Runtime Surface
```

- Action Intake 不执行命令；
- Orchestrator 决定业务步骤，不实现解释器兼容；
- Runtime Prep 准备 Space、容器和资产，并调用 Planner；
- Provider 不拥有页面、卡片和 Store；
- Surface 只绑定同一个 run 的终端、报告、Web 或 Agent 句柄。

## 状态与可观测性

Provider 选择结果至少投影：

- 实际 Provider/车道；
- 稳定选择或回退原因；
- 运行时代次与关键资产身份；
- 创建失败、执行失败和取消的阶段区别。

页面只能读取投影。不得根据命令名、文件是否存在或默认文案反推实际车道，也不得新增第二份 Provider 状态 Store。

## View 边界

PRoot View 与三车道正交。普通 Node、Python、原生能力、终端和 PRoot 都不默认套全局 View。只有更新或危险文件操作的显式
调用方可临时使用 View/保护事务；显式 View 请求需要完整 Linux 视图时直接进入 PRoot，不进入宿主快速通道。

## 验收层级

每个 Provider 都按四层验收：

1. 合同测试：请求、能力判断、Ready/Unsupported/Blocked 和唯一计划；
2. 等价测试：快速/原生路径与兼容路径在共同语义范围内结果一致；
3. 性能测试：预先固定负载、轮数、p50/p95、失败率和资源压力；
4. 真实链路：资源动作到真实可交互或可消费结果，验证取消、停止、升级和回退证据。

构建成功、出现 CLI 横幅或单个样例运行不能替代真实链路验收。

## 实施顺序

1. 用现有 Node 与 PRoot 收口统一请求和 Planner，不改变当前行为；
2. 把 Node 变成首个标准快速 Provider，并只做等价回归；
3. 对 Python 做 go/no-go 基线，通过后才实现纯 Python 最小通道；
4. 以下载＋SHA-256 建立首个原生 Provider 样板；
5. 将 PRoot 归一为最终兼容 Provider，再逐类扩大温热 Runner 准入；
6. 每个叶子任务独立验证和提交，父任务通过后才扩大覆盖面。

分车道细则见：[通用依赖快速通道](managed-runtime-fast-path.md)、[Android/NDK 原生能力](native-capability-provider.md)和
[Ubuntu/PRoot 兼容 Provider](proot-compatibility-provider.md)。

