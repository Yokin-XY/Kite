# Ubuntu/PRoot 兼容 Provider

## 定位

PRoot 是 Kite 的完整 Linux 兼容底座和最终兼容 Provider。复杂 shell、Linux ELF、编译器、完整文件系统视图、未验证运行时和
无法由原生能力完整表达的请求都进入 PRoot。

Provider 化不是替换或重写现有 PRoot，而是让终端、资源、Agent 和后台入口通过同一个 Planner 获得唯一 PRoot 计划，避免
各入口复制回退和状态逻辑。

## 当前正式能力

- 普通终端、资源运行和后台任务直接创建原生 PRoot 实例；
- 全局 View 已退出普通路径，显式更新保护除外；
- `ProotJobAdmissionController` 已支持任务 lane、优先级、共享写屏障和压力收缩；
- `kf-runner --server` 与 `WarmProotRunnerPool` 已具备 stdio、退出、信号、取消、timeout 和身份失效；
- 正式温热池接线目前只覆盖固定维护任务，不能宣称任意用户 shell 已进入统一调度。

## 准入覆盖矩阵

准入只覆盖已经能声明完整生命周期的任务，不能根据“它也是 PRoot”就自动接管：

| 任务类别 | 当前准入状态 | 原因 |
| --- | --- | --- |
| 固定资源采样 | 已接入 | 有稳定 owner、结构化 argv、共享写属性、20 秒 timeout 和有界 stdio 结果 |
| 普通交互终端 | 未接入 | 生命周期由终端会话持有，任意命令的读写属性不可预判 |
| Recipe 任意 shell | 未接入 | shell 可包含未知副作用，不能猜成只读或安全重放 |
| Agent 与长期服务 | 未接入 | 需要让 lease 与受管 owner 同寿命，不能只包住进程创建瞬间 |
| detached shell | 未接入 | 启动返回不等于业务进程结束，短 lease 会制造虚假容量 |

准入任务必须同时声明 `jobId`、`ownerId`、lane、读写属性、取消模式和结果模式。控制器只决定何时开始；任务开始后的停止、
结果和进程树仍由声明的 owner 负责。未迁移类别继续走独立 PRoot 兼容路径，不因本表而被限流或改变行为。

## 作为最终回退的规则

PRoot 可以承接 `Unsupported`，但不能掩盖 `Blocked`：

- 快速 Provider 缺少解释器能力、需要 Linux ELF 或复杂 shell时，可以在进程创建前选择 PRoot；
- 资产身份损坏、请求非法、路径越界或安全合同失败时必须失败关闭；
- Host 或原生任务已经开始后，不自动重放 PRoot；
- PRoot 计划继续继承现有 bind、网络、DNS、遥测、owner、运行代次和停止合同。

## 独立 PRoot 与温热 Runner

独立 PRoot 是默认兼容路径。温热 Runner 只服务满足全部条件的任务：

1. 结构化 argv，无交互终端；
2. 稳定 jobId、owner、lane、取消和结果出口；
3. 容器、rootfs、workspace、runner 文件和协议代次完全匹配；
4. STARTED 之前失败可以在同一准入租约内回退独立 PRoot；
5. STARTED 之后失败禁止自动重放；
6. 写属性明确，共享写任务服从队列屏障。

Git、文件扫描或摘要只能作为性能样本。是否进入温热池由任务合同和副作用阶段决定，不能按命令名写死。

## 可调性能档位

现有策略基线：

| 档位 | 全局上限 | 空闲回收 | 目标 |
| --- | ---: | ---: | --- |
| 低负载 | 1 | 2 秒 | 长时间挂载 |
| 均衡 | 2 | 30 秒 | 默认使用 |
| 高性能 | 4 | 120 秒 | 用户主动选择 |

并发上限必须由目标设备的吞吐、P95、RSS、low-memory kill、ANR 和失败率确定。CPU 满载只说明当前任务 CPU 饱和，不证明
继续增加 PRoot 会缩短单任务时间。压力升高只限制后续准入，不强杀已经开始的任务。

## View 边界

```text
普通 Linux 任务 → PRoot Provider → 物理 rootfs
更新或危险文件操作 → 显式保护事务 → PRoot + 临时 View → 验证后提交或回滚
```

历史 View activation 不得影响普通运行。资源页面、终端和后台服务不能因为看见 View 文件而自行切换路径。

## 第一阶段实施

1. RF410：把现有独立 PRoot 计划适配为最终 Provider，不改变执行语义；
2. RF420：调用方按任务类别逐个补齐准入身份；
3. RF430：扩展一个无交互、边界明确、可证明不重复副作用的短任务类别；
4. RF440：在真机曲线基础上校准低负载、均衡和高性能档位。

## 发布门

- 普通终端、复杂 shell、Linux ELF、编译器和长期服务行为不退化；
- Host/原生不满足能力时只创建一份 PRoot；
- 温热 Runner 的 STARTED、取消、timeout、崩溃和身份失效可证明；
- 每个新增调用方都有 owner、结果、取消和副作用边界；
- 不以空 worker 存活、CPU 占用或单次命令更快冒充正式吞吐收益。
