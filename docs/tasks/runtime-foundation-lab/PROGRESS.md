# Kite 混合运行底座进度

## 状态总览

| 任务 | 状态 | 当前结论 |
| --- | --- | --- |
| RF000 | 已完成 | 快速通道、Android 原生能力与 PRoot 兼容底座三车道父任务门全部通过 |
| RF100 | 已完成 | 统一请求、Provider 结果和失败关闭通过全量回归 |
| RF110 | 已完成 | 总架构、三份 Provider 文档、Node 风险索引与性能证据已固化 |
| RF120 | 已完成 | 三个 Node 入口已迁移到统一结构化请求，行为等价 |
| RF130 | 已完成 | Ready/Unsupported/Blocked 已接入三条正式 Node 入口 |
| RF200 | 已完成 | Node 与 Python 通用快速通道均已按分层门收口 |
| RF210 | 已完成 | Node 显式实现标准 Provider，既有行为等价 |
| RF220 | 已完成 | HN-001～HN-011 已完成证据和开放门映射 |
| RF230 | 已完成 | 纯 Python go；subprocess/venv child/第三方扩展保持 PRoot |
| RF240 | 已完成 | 通用 glibc 资产与纯 Python 结构化 Provider 已通过真机门 |
| RF250 | 已完成 | 子进程保持 PRoot；扩展按精确 ABI 与不可变代次开放 |
| RF300 | 已完成 | 下载、文件、安全 ZIP 与 Android 系统能力目录完成分层验收 |
| RF310 | 已完成 | 原生下载、Recipe/Run、资源预取及对照压力门通过 |
| RF320 | 已完成 | 文件 Provider、Recipe/Run、权限边界和真机门通过 |
| RF330 | 已完成 | 安全 ZIP 正确性通过，但真机慢于 PRoot，不进入资源快速车道 |
| RF340 | 已完成 | 真实能力目录、薄适配、失败关闭和父任务门通过 |
| RF400 | 已完成 | PRoot Provider、准入、温热短任务与可调性能档位完成闭环 |
| RF500 | 已完成 | 实际控制面、冷启动接力、遥测和第二生产样板均已闭环 |
| RF510 | 已完成 | actual coordinator 状态已进入 RuntimeHealth，读取不创建 pool |
| RF520 | 已完成 | 策略文件与 host 内存先接力，RuntimeHealth 到达后完全接管 |
| RF530 | 已完成 | route/result/lane 与 queue/execute/total 已进入低基数正式遥测 |
| RF540 | 已完成 | 版本化固定 helper 已进入 SERVICE/SHARED_WRITE 有界 Runner |
| RF550 | 已完成 | 1341 项全量回归、强制构建和 OnePlus 8T 联合门通过 |
| RF600 | 已完成 | owner lease 合同/模拟器闭环；生产长期入口仍需类别桥接门 |
| RF610 | 已完成 | 长期 owner 状态机保持进程身份、停止意图与容量直到确认释放 |
| RF620 | 已完成 | 容量、压力、维护屏障、去重和跨 lane 公平性纯模拟通过 |
| RF630 | 已完成 | 恢复去重、PID 代次、孤儿容量与停止优先合同通过 |
| RF640 | 已完成 | 固定规划态 schema 通过隐私、基数与无副作用护栏 |
| RF650 | 已完成 | 1374 项全量回归通过；后台身份桥接准备 go、生产迁移 no-go |
| RF700 | 进行中 | 在既有校准 dry-run 上对齐正式 1/2/4 策略，不另建平行体系 |
| RF710 | 已完成 | overlay schema 失败关闭，tracee guard 与正式 1/2/4 档位彻底分离 |
| RF720 | 已完成 | actual 差量窗口失败关闭；高压/失败只降一级，无可信 thermal 永不升档 |
| RF730 | 进行中 | 建立连续窗口、冷却、回滚和重启恢复纯状态机 |

## RF110 开机与三问自检

- 目标是什么？按 `PLAYBOOK.md` 建立三车道总架构、Provider 文档、Node 风险索引和任务验收合同。
- 完成标准是什么？文档互链、已验证与待验证事实分离、首个特例和回退门明确，不冒充 Python/原生能力已实现。
- 依赖是否满足？满足。当前分支从干净 `main@8223ba0` 建立；原主工作树的 `AGENTS.md` 和 Agent 模型库改动未带入。

## RF510 开机与三问自检

- 目标是什么？按 `PLAYBOOK.md` 把实际 coordinator/admission/warm pool 状态投影到正式 RuntimeHealth，明确区别现有规划推演结果。
- 完成标准是什么？字段来自同一实际状态源，读取不创建 PRoot，不记录命令/路径/用户输入，并有单测与 Debug 构建证据。
- 依赖是否满足？满足。RF440 已完成；分支工作树干净，实际快照已存在且当前仅由 Debug 探针消费。

## RF520 开机与三问自检

- 目标是什么？按 `PLAYBOOK.md` 让第一份健康快照前的 admission 使用已有 host 可用内存压力事实，关闭默认均衡档的假性单并发。
- 完成标准是什么？正常内存为 2，缺信号/高压/临界仍为 1；正式 RuntimeHealth 到达后覆盖 bootstrap，真机能读到来源和有效上限。
- 依赖是否满足？满足。RF510 已建立 actual 正式投影；现有 `RuntimePressureGuard` 已能从 `/proc/meminfo` 计算 hostAvailableLevel，无需新增传感器或状态源。

## RF530 开机与三问自检

- 目标是什么？从同一个 `BoundedProotTaskExecutor` 聚合低基数 route/result/lane 与 queue/execute/total 时延，补足仅凭 admission 总量无法判断真实收益和失败位置的问题。
- 完成后拿什么证明？单测覆盖每条 route、结果分类、时延边界和并发更新；RuntimeHealth 只输出枚举与数字，读取不执行任务、不包含 argv/cwd/env/output/owner。
- 依赖是否满足？满足。RF510 已提供 actual 正式投影；RF520 已确保首个任务前也有明确策略来源。现有执行结果已含 route 与 job duration，下一步只补统一计时和聚合，不改变任务结果拥有者。

## RF540 开机与三问自检

- 目标是什么？把高频、代码自有的 Supervisord update/status/日志尾部采集从每次独立 PRoot 迁入有界 Runner，同时保持原解析与错误边界。
- 完成后拿什么证明？helper 内容和版本固定、无外部命令参数；调用方只传结构化 helper argv，声明 `SERVICE/SHARED_WRITE`、稳定 owner 和有界 timeout/output；单测与真机冷温结果语义一致且无残留任务。
- 依赖是否满足？满足。RF530 已能观察 route/result/时延；现有 `WorkspaceBuildSupport` 已拥有受管 helper 写入位置，`SupervisordServiceHealthStore` 的 shell 内容完全由代码持有，可先固化为 helper 而不接收用户 payload。

## RF550 开机与三问自检

- 目标是什么？联合证明 RF510～RF540 没有破坏既有 PRoot/资源/Agent/运行状态合同，并给 RF600 长生命周期 lease 明确 go/no-go，而不是顺手迁移长期进程。
- 完成后拿什么证明？相关回归、强制全量单测、强制 Debug 构建、OnePlus 8T 冷启动/温复用/压力收缩/空闲行为/服务健康链与 ANR/FATAL 检查；Git 工作树干净、每个叶子提交可独立回退。
- 依赖是否满足？满足。RF510～RF540 均已独立实现和验证；Node/Python 历史矩阵仍冻结，本门只验证本阶段新增合同和跨模块回归。

## RF650 开机与三问自检

- 目标是什么？联合审查 RF610～RF640 是否真正构成长生命周期 owner 的合同闭环，并明确后台服务、终端、Agent 的生产迁移边界。
- 完成后拿什么证明？强制联合测试、Debug 构建、静态生产引用与敏感字段检查、提交边界复核；形成后台服务样板 go/no-go 和下一任务入口。本门不重跑冻结的 Node/Python 性能矩阵，也不伪造真机长期进程证据。
- 依赖是否满足？满足。四个叶子任务均已独立测试构建并提交前三项；RF640 等待本叶提交后进入父门，所有实现仍为无生产装配的合同/模拟器。

## RF720 开机与三问自检

- 目标是什么？把 RF510/RF530 的实际 coordinator 与有界任务遥测、可信内存压力、前后台和 thermal 可用性归一成一次评估输入，明确何时只能保持/降级、何时连候选升档都不能给。
- 完成后拿什么证明？未知、陈旧、零样本和冲突信号失败关闭；HIGH/CRITICAL 或显著失败给降级建议但不强杀任务；没有可信 thermal 时永远不产生升档建议；纯函数不改 coordinator。
- 依赖是否满足？满足。RF710 已证明 tracee overlay 只能当 guard，正式 1/2/4 来自单一 tunings；RF530 已有固定 lane/route/result 与 queue/execute/total 聚合，可作为实际任务证据。

## RF720 验收

- 新增 `RuntimeProotAdaptiveSignalGate`，只接收 RF510 actual tuning、RF530 两次累计遥测读数、RF710 tracee guard 与显式 thermal evidence；没有新 Store、线程、计时器或生产装配。
- 累计遥测必须满足唯一 key、非负值、每阶段桶数等于 entry count、前后计数单调；差量窗口固定为 30 秒至 10 分钟且最多陈旧 2 分钟，P95 由差量桶计算。
- 正式候选只允许内置 1/2/4 档。HIGH/CRITICAL 内存、可信 hot 或至少 5 样本且失败率达到 10% 时建议降一级；最低档只保持，不强杀已运行任务。
- 自动升档候选要求：正式 RuntimeHealth source、NORMAL 内存、前台、20 个以上差量样本、失败率低、P95 不超过 1 秒、RF710 guard ready、60 秒内可信 thermal normal。当前仓库没有可靠 thermal source，因此真实生产环境仍然不可能自动升档。
- 目标回归覆盖 `RuntimeProotAdaptiveSignalGateTest`、`RuntimeProotCalibrationAlignmentTest`、`BoundedProotTaskTelemetryTest`：3 个 suite、21 tests、0 failure、0 error、0 skipped；Debug 编译随测试成功。
- 下一恢复指针进入 RF730；它只能消费 RF720 的窗口 action，建立连续确认/冷却/回滚模拟器，不能写正式策略文件。

## RF710 开机与三问自检

- 目标是什么？复用而不是重写仓库已有设备校准 dry-run，找出历史 tracee/overlay 模型与 RF400 正式 1/2/4 admission/pool 档位之间可证明的映射和冲突。
- 完成后拿什么证明？真实引用图、目标测试和对齐合同明确：校准结果只能产生候选建议，不能直接改 coordinator；未知 thermal、旧 schema 或缺失实测上界失败关闭。
- 依赖是否满足？满足。RF650 已确认长期 owner 预研不接生产，不会与校准并行改同一状态；RF510～RF540 已提供 actual policySource、压力和有界任务遥测可作为后续可信输入。

## RF640 开机与三问自检

- 目标是什么？把 RF620/RF630 的纯规划结果投影为有界、低基数、明确标注 `planned_not_production` 的诊断字段，同时不污染 RF510 的 actual PRoot 调度事实。
- 完成后拿什么证明？字段只含 phase/kind/lane/action/process-match 的固定枚举计数和容量数字；不含 ownerId、leaseId、PID、启动代次、路径、命令、Agent/session 身份；读取不扫描、不恢复、不改模拟器。
- 依赖是否满足？满足。RF620 已有稳定容量快照，RF630 已有一 owner 一 decision 的恢复计划；二者均为调用方提供的不可变值，不需要创建第二个状态源。

## RF630 开机与三问自检

- 目标是什么？在不创建第二进程的前提下，固定应用重启后如何恢复 owner lease、如何核对 PID 启动代次、如何处理丢失进程与主动停止竞态。
- 完成后拿什么证明？恢复批次对同 owner 去重；仅精确进程代次可重连；未知/死亡进程进入 orphan review 并保持容量直到确认；恢复时的停止意图优先于运行重连；测试不读取真实 `/proc`、不接生产 Registry。
- 依赖是否满足？满足。RF610 已定义可恢复的 lease phase 与进程身份，RF620 已确保 recovered lease 未来可继续参与同一容量模型而不复制 owner。

## RF620 开机与三问自检

- 目标是什么？以 RF610 状态机为唯一 lease 记录，模拟长期 owner 的容量、压力准入、同 owner 去重、独占维护屏障与 lane 优先级/FIFO 公平性。
- 完成后拿什么证明？纯 JVM 测试证明既有 owner 不因压力变化被强杀、重复请求不多占容量、维护请求最终获得屏障、满 lane 不阻塞其他可运行 lane；不创建进程、不持久化、不接生产 Store。
- 依赖是否满足？满足。RF610 已把长期 owner 的容量寿命、进程代次、停止意图与显式释放固定为纯状态机；短任务 `SHARED_WRITE` 没有被错误复用于常驻进程。

## RF610 开机与三问自检

- 目标是什么？定义长期 runtime owner 与准入 lease 的同寿命状态机，明确 acquire、attach、running、stopping、released、orphan reconciliation，避免把短任务 `use {}` lease 直接套给服务/终端/Agent。
- 完成后拿什么证明？纯模型和模拟器覆盖唯一 owner、重复 attach、启动失败、停止、进程外死亡、恢复、压力只影响新准入、同 owner 重连；本阶段不创建真实进程、不接生产 Store。
- 依赖是否满足？满足。RF500 已提供 actual admission/telemetry，但其 lease 仍以调用栈为寿命；现有 `CardRunStore`、`BackgroundRuntimeRegistry` 和 Agent binding 可作为未来 owner 事实源，本任务只定义桥接合同，不复制状态。

## 倒序日志

### 2026-08-01 RF710 既有设备校准与正式 1/2/4 对齐

- 真实链路为：packaged P0 Python 通过 automation 的 PRoot 命令写 `/workspace/.kf/proot-device-calibration.json`；`RuntimeHealthStore` 读取后只传给 `RuntimeProotPoolPlanDryRun` 与 `RuntimeProotDeviceCalibrationDryRun`。实际 `WarmProotExecutionCoordinator`、admission 与 pool 不读取 overlay，只消费 profile/workload/pressure 和唯一 `ProotPerformanceTunings`。
- 修正 overlay loader：必须同时满足 `schema=proot_device_calibration_v0` 与显式 `valid=true` 才标记有效；缺 schema/valid 的任意 JSON 不再默认有效，并保留 `appliedAtMs` 供后续陈旧性判断。dry-run 输出真实 overlay schema。
- 新增 `RuntimeProotCalibrationAlignment`：只接受当前 v4 方法、应用时间、实测上界和自洽 tracee 证据；通过后也仅为 `READY_TRACE_GUARD_ONLY`，`directProfileSelectionAllowed` 永远 false。旧 overlay 的 profileLimits 即使写成 64/96/128 也被忽略。
- dry-run 展示的 low/balanced/high 改为从 `ProotPerformanceTunings` 派生的 1/2/4，不能再被 tracee 数覆盖。目标 2 个 suite、10 项测试零失败，Debug 构建成功。
- 首轮临时文件测试暴露的是本地 JVM Android `org.json` stub 的 `RuntimeException`，并未执行真实 loader 规则；没有降低断言，而是把 loader 的 schema/valid 门抽为纯函数，让产品代码与 JVM 测试消费同一判断后复验通过。

### 2026-08-01 RF650 RF600 父任务门

- 强制全量单测在工具 180 秒等待窗后继续完成并完整落盘：258 个 suite、1374 tests、0 failure、0 error、2 skipped；随后同一测试任务取得 exit 0。强制 Debug 构建 exit 0。
- APK 为 241087600 bytes，SHA-256 `6EAA1D36A662271346D388F1E5CA6B95EA6436946A9FA34F29F0E65231D3B8C8`；构建物未进入 Git。RF600 没有用户可见或生产运行改动，因此本门不安装真机、不伪造长期 owner 设备证据，也不重复 Node/Python 性能矩阵。
- 相对 RF550，生产源码只新增 RF610～RF640 四个无装配的合同/模拟器文件；`LongLivedProot*` 的 main 引用也只存在于这四个文件。没有修改 `CardRunStore`、`BackgroundRuntimeRegistry/Host`、终端、Agent、资源清单或 RuntimeHealth actual 字段。
- 后台服务生产迁移 **no-go**：`BackgroundRuntimeRecord` 目前只持久化 PID，没有启动代次；`HostProcessRecord` 也未投影 start ticks，外部恢复主要依赖 PID、容器命令/token 和 status probe，尚不能满足 RF630 精确代次与跨 owner 单进程合同。允许下一步做身份桥接准备，但在真实 owner 持久化、停止确认和恢复真机门前不接 lease。
- 终端与 Agent 继续 no-go：它们各自具有交互/会话/协议生命周期，RF600 没有提供类别级唯一进程、重连和停止证据。RF700 转向既有设备校准体系与正式 1/2/4 策略的对齐，不创建第二套校准器。

### 2026-08-01 RF640 长期 owner 规划态健康投影

- 新增独立 `proot_long_planned_*` 固定 schema，只消费 RF620 admission snapshot 与 RF630 recovery plan 的不可变值；scope 强制为 `planned_not_production`，没有写入或复用 RF510 的 `proot_actual_*` 字段。
- 字段只含压力、容量、维护屏障，以及固定 phase/kind/lane/action/process-match 的计数。恢复不可用时仍输出相同枚举键和零值，owner 从 1 增到 8 时 schema 数量不增长。
- 敏感值护栏以 ownerId、leaseId、PID、启动代次、路径、命令和 Agent/session 标识作为反例，投影结果均不包含；两次读取结果一致，模拟器快照和记录不变。
- RF610～RF640 强制联合为 4 个 suite、33 项测试零失败；随后 Locale.ROOT 键稳定性微调的投影 suite 复验通过，Debug 构建成功。RF650 只做联合父门和生产引用审计。

### 2026-08-01 RF630 重启恢复与孤儿协调

- 新增纯 `LongLivedProotRecoveryPlanner`，输入持久化 lease、调用方观察到的存活进程代次和停止意图；自身不读 `/proc`、不创建/停止进程、不连接生产 Registry，计划固定 `processStartsRequested=0`。
- 恢复只接受 `(hostPid, processStartTicks)` 精确匹配；同 PID 新启动代次标记 `PID_REUSED` 并进入 orphan review，原进程身份不被覆盖。未发现进程同样进入/保持 orphan review并继续占容量，直到 owner 明确确认死亡。
- 同 owner 只保留最高 generation；完全重复折叠，同代次冲突固定排序后标记 `DUPLICATE_CONFLICT_REVIEW`，不猜测哪份记录正确。跨 owner 若声称同一进程代次，两边都进入 `PROCESS_IDENTITY_CONFLICT_REVIEW`，禁止双重绑定。未启动的 `ADMITTED` 安全释放，未持久化身份的 `STARTING` 保持 review，不再启动第二进程。
- 恢复前收到停止请求时先写入停止意图；即使随后精确重连也恢复为 `STOPPING`。RF610 新增 orphan 内停止转换，避免恢复竞态把用户停止覆盖成运行。
- RF610～RF630 共 3 个 suite、29 项测试零失败，Debug 构建成功。首次测试编译只发现测试 helper 的可空诊断文本类型错误，修正后同范围通过，没有绕过恢复行为断言。

### 2026-08-01 RF620 长期 owner 容量与公平性模拟器

- 新增纯 `LongLivedProotAdmissionSimulator`：同一 RF610 lease record 同时承载排队、准入和生命周期状态；没有生产单例、Store、线程、进程或 PRoot 调用。
- 全局容量、lane 容量、优先级/FIFO 和压力收缩均为确定性事件。高压只阻断新的非必要 owner，既有 holder 不被强杀；必要 owner 可绕过压力门但仍受实际容量约束。
- `EXCLUSIVE_MAINTENANCE` 以队列 sequence 建立屏障：屏障前任务可排空，屏障后不再新增共享 holder，维护完成后继续排队；被压力阻断的非必要维护不会堵住必要任务。它没有借用短任务 `SHARED_WRITE` 永久持锁。
- 重复 owner 返回同一 lease 且不增加容量；若 lane/posture/必要性声明变化则显式 `SPEC_CONFLICT`，不能静默替换。全局事件时间单调，陈旧输入失败关闭。
- RF610+RF620 共 2 个 suite、19 项测试零失败，Debug 构建成功；RF630 继续只模拟恢复与孤儿协调，不读取真实 `/proc`、不接生产 Registry。

### 2026-08-01 RF610 长期 owner lease 状态机

- 新增纯 `LongLivedProotOwnerLeaseTransitions`，把 owner kind、lane、文件系统姿态、lease phase 与 `(hostPid, processStartTicks)` 进程代次分离；没有集合、Store、线程、进程或生产装配。
- 容量从 `ADMITTED` 持续到 `STOPPING/ORPHAN_REVIEW`，只有启动前取消/失败、停止确认或死亡确认才释放。相同进程 attach 幂等，不同身份拒绝；失联前为 `STOPPING` 时，重新确认存活仍恢复停止意图，不会误转回运行。
- 长期文件系统姿态使用 `SHARED_RUNTIME/ISOLATED_RUNTIME/EXCLUSIVE_MAINTENANCE`，没有复用短任务调用期 `SHARED_WRITE` 锁；状态转换时间单调，陈旧事件不能倒推状态。
- 目标 2 个 suite、23 项测试零失败，Debug 构建成功。RF620 继续只做容量/公平性纯模拟，不接 `CardRunStore`、后台 Registry、终端、Agent 或任何真实进程。

### 2026-08-01 RF510 实际调度状态正式投影

- 正式 RuntimeHealth 新增 `proot_actual_*` 组，明确 `scope=actual_not_planned`；字段即时来自 `WarmProotExecutionCoordinator` 的 policy、admission 与 pool，不复用规划态 `prootPoolPlan` 冒充实际执行。
- 投影覆盖 profile、pressure、foreground、配置/有效上限、warm 上限/空闲时间、active/queued、session active/idle/stale 和最大空闲年龄；不包含 argv、cwd、owner、环境或输出。
- 未创建 pool 时 `tuningSnapshot()` 返回零 session，诊断读取不会触发 holder、资产检查或 PRoot 进程创建。
- 3 个相关 suite、13 项测试通过，Debug 构建成功；下一步 RF520 只修第一份 RuntimeHealth 前的 bootstrap policy，不改变正式快照接管规则。

### 2026-08-01 RF520 冷启动策略接力

- 冷启动在首个有界任务前复用现有 reclaimer/resident/workload 策略文件与 `/proc/meminfo`；正常内存按真实档位启动，信号缺失、高压或临界压力继续保持单并发。
- bootstrap 与正式 RuntimeHealth 共用单一 `policyState` 和 admission；正式快照无条件接管，迟到的 bootstrap 结果不能反向覆盖。实际健康面和 Debug 探针新增低基数 `policySource`。
- 4 个相关 suite、17 项测试零失败，Debug 构建成功。OnePlus 8T 覆盖安装后强停冷进程，首个固定探针为 `policySource=bootstrap_policy_files_host_memory pressure=normal configuredMax=2 effectiveMax=2`，成功走 `warm_runner`；无 ANR/FATAL。
- 下一步 RF530 只增加低基数执行遥测，不保存任务 payload，也不扩大有界 Runner 的任务范围。

### 2026-08-01 RF530 有界执行结果遥测

- `WarmProotPoolExecution` 统一给出 queue/execute/total：queue 覆盖 admission 与 Runner 槽等待，execute 覆盖业务 job 或独立回退；route 继续区分 warm、独立回退、拒绝、STARTED 后失败和 fallback 失败。
- `BoundedProotTaskTelemetry` 只按 lane/route/result 固定枚举聚合，时延使用 8 个固定桶及 sum/max；不保存 job、owner、argv、cwd、env 或输出，正式 RuntimeHealth 读取只复制内存快照。
- 4 个相关 suite、18 项测试零失败，4000 次并发完成样本无丢计数，Debug 构建成功。OnePlus 8T 冷进程固定探针记录 `telemetrySamples=2 telemetryRouteCount=2 queueMaxMs=39 executeMaxMs=1434 totalMaxMs=1473`，无 ANR/FATAL。
- 下一步 RF540 迁移第二个高频内部样板；不因遥测已经存在而扩大任意 shell、终端、Agent 或长期任务范围。

### 2026-08-01 RF540 Supervisord 健康采集有界 Runner 样板

- 新增 `/workspace/.kf/system/bin/kf-supervisord-health-snapshot`：稳定路径、内容版本 1、拒绝任何参数，固定执行 `update/status`、日志 marker 和每文件 8 行尾部；高频刷新只校准该 helper，不重跑整套 Workspace ensure。
- `SupervisordServiceHealthStore` 改为单一结构化 helper argv，声明稳定 owner、`SERVICE/SHARED_WRITE`、5 秒准入等待、10 秒运行和每流 256 KiB 上限；STARTED 前沿用统一独立回退，STARTED 后不重放。截断、超时、拒绝和 exit code 继续映射为原健康失败边界。
- 5 个相关 suite、23 项测试最终零失败、1 项环境跳过；首次强制轮只有既有并发池测试在编译高负载下 1 秒 latch 抖动，原范围无代码改动重跑通过。Debug 构建成功。
- OnePlus 8T 当前 Supervisord 运行记录本身为 ERROR、启动命令 exit 127；没有安装包或伪造健康。固定 helper 冷/温两次都走 `warm_runner`，真实保持 exit 127，total 1490ms→68ms，service sample 1→2，无 fallback/reject、无 helper 残留、无 ANR/FATAL。
- 35 秒后的共享 `kf-runner` 并非泄漏：随后固定采样显示其执行前 idle age 仅 1195ms，证明被现有容器进程采样复用；RF550 继续核对整体空闲与共享任务边界。

### 2026-08-01 RF550 RF500 父任务门

- 强制全量单测复算为 254 个 suite、1341 tests、0 failure、0 error、2 skipped；强制 Debug 构建成功。APK 241005680 bytes，SHA-256 `8F7DBC4073014D6CD188BE6061831D4CA64B6FB01AC4CD5EA1E396B77BEF7D7F`。
- RF510～RF540 相对 RF440 父门只改动实际策略/池/遥测、Supervisord helper、相关 Debug 探针、测试与任务文档；没有修改 Node/Python Provider、终端、Agent、资源清单或 View 正式链。`git diff --check` 通过。
- OnePlus 8T 覆盖安装强制构建 APK：冷进程固定采样为 `bootstrap_policy_files_host_memory/NORMAL/configured=2/effective=2`，成功 warm runner；随后两个 Supervisord helper 样本复用同一 Runner，121ms/115ms，真实保持 runtime ERROR/exit 127，无 fallback/reject、无 helper 残留、无 ANR/FATAL。
- RF440 已有压力收缩、1/2/4 档、空闲回收与 8 并发 no-go 证据，本门不重复性能矩阵。RF600 仅 go 合同/模拟器；生产迁移、终端、Agent 和长期服务全部 no-go，直到 owner 同寿命、恢复与停止合同分别通过。

### 2026-08-01 RF500 启动

- RF000 完成后按用户授权继续自主推进，不重跑冻结的 Node/Python 矩阵，不推送远端；每个叶子任务继续独立 Git 提交。
- 代码审计确认下一批真实缺口：实际 coordinator 状态没有进入正式健康面；冷启动首份健康快照前有效并发固定为 1；Supervisord 健康采集每次新建独立 PRoot 且不能直接把复杂 shell 伪装成 argv。
- RF500 先做控制面和第二个高频有界生产样板。普通终端、任意 Recipe shell、Agent、detached 和长期服务保持原路径；RF600 只在 RF500 父任务门后进入合同研究。

### 2026-08-01 RF440c / RF440 / RF400 / RF000 父任务验收

- 三档、CUSTOM、压力收缩、前后台 lane、动态 trim、空闲超时和活动任务保护由 3 个 suite、26 项测试覆盖，零失败、零错误、零跳过；本轮没有重复 Node/Python 矩阵。
- 复用 RF430c 同一 OnePlus 8T 的固定 32 MiB 对照：1/2/4/8 并发温热墙钟相对独立降低 64.8%/52.8%/59.3%/48.9%，吞吐提高 182.7%/112.6%/146.2%/95.3%，全部零失败；8 并发温热 P95 130 ms 高于独立 114 ms，因此生产继续保留 1/2/4，8 只用于 debug 校准。
- 最终构建覆盖安装后，正式有界进程表调用冷建池 344 ms、同会话温热复用 61 ms；超过均衡档 30 秒空闲线后，设备进程表已无 runner，下一次探针 `beforeSessions=0` 并在 143 ms 内正常重建。输出均为 `warm_runner`、4 条可解析记录、691 bytes、零截断。
- 温热点位应用 `TOTAL PSS/RSS` 为 114,887/224,428 KiB，runner 独立进程 RSS 4,632 KiB；回收后点位应用 `TOTAL PSS/RSS` 为 105,926/214,676 KiB。它是点位资源证据，不冒充多轮统计分布。
- 当前设备策略为均衡档，配置上限 2、温热上限 2、空闲 30 秒；压力尚为 `unknown` 时有效准入安全收缩为 1。无残留 benchmark/`ps` 子任务，无新增 ANR/FATAL。
- 最终 Debug APK 247,104,068 bytes，SHA-256 `00ED0500DA74A1325F9FCDC060499921D15D994CBEE25AFF98E3223FBBE9961F`，构建物未进入 Git。
- RF000 完成：快速通道、Android 原生能力、Ubuntu/PRoot 兼容底座共享统一请求/选择/失败关闭合同；普通终端、任意 shell、Agent 和长期服务继续独立 PRoot，View 仍只保留显式更新/危险文件保护边界。

### 2026-08-01 RF440b 动态策略与可观测性

- `WarmProotExecutionCoordinator` 继续只从现有 `RuntimeHealthSnapshot` 接收 active profile、lane、压力与前后台事实；策略变化更新同一 admission，并对同一 warm pool 执行动态 trim，页面和 Ubuntu 都没有直接控制入口。
- 压力升高或退到后台只限制后续准入：回归证明已经取得 lease 的活动任务不会被强杀；超出新上限的活动 Runner 标记为 stale，待 owner 正常释放后关闭，空闲超额 Runner 可立即回收。
- 新增即时 `TuningSnapshot`，从当前 policy、admission 与 pool 投影档位、配置/有效上限、温热上限、空闲时间、active/queued 及 Runner 活动/空闲/stale 数；没有新增持久化 Store 或复制运行事实。
- 3 个相关 suite、25 项测试通过，覆盖内置三档、CUSTOM、压力收缩、前后台 lane、动态 trim 与活动任务保护；下一步 RF440c 只做本阶段 Debug/真机父任务门，不重复 Node/Python 性能矩阵。

### 2026-08-01 RF440a 单一性能档参数源

- 新增 `ProotPerformanceTunings` 作为有界 PRoot 准入与温热池的共同参数源；准入器不再自己维护 1/2/4，Runner 池也不再维护另一份重复映射。
- 内置档保持低负载 1/2 秒、均衡 2/30 秒、高性能 4/120 秒；RF430 的 8 并发仅属于 debug `globalMaxOverride` 校准，不进入生产档位。
- 修复 CUSTOM 漂移：原先准入从 lane 推导 1～4，但温热池固定为 2；现在二者从同一 lane 最大并发推导并共同封顶 4，空闲回收仍保持 30 秒。
- 3 个相关 suite、23 项测试通过，零失败、零错误、零跳过；下一步 RF440b 增加同源动态状态快照并证明压力变化只影响后续准入/空闲回收。

### 2026-08-01 RF430c / RF430 父任务验收

- RF430a～b 的联合目标回归覆盖 7 个 suite、43 项测试，零失败、零错误、零跳过；既有 pool 测试证明 STARTED 前独立回退与同 lease，controller/protocol 测试覆盖 STARTED 后不重放、timeout、取消、runner 崩溃和身份失效。本阶段没有重复 Node/Python 矩阵。
- OnePlus 8T 固定 32 MiB 摘要对照全部零失败：1/2/4/8 并发的独立/温热墙钟分别为 193/68、229/108、275/112、268/137 ms，温热降低 64.8%、52.8%、59.3%、48.9%；吞吐提高 182.7%、112.6%、146.2%、95.3%。
- 8 并发温热 P95 为 130 ms，高于独立 P95 114 ms；它提升总吞吐但损伤单任务尾延迟，不能据此把生产高性能档从 4 盲目提高到 8。1/2/4 档继续进入 RF440 校准。
- 基准首次运行发现固定 32 MiB `/tmp` 样本未清理；已删除真机精确文件并在 debug 基准 `finally` 中固定清理，不重复整套矩阵。
- 最终 Debug 构建成功；APK 247,090,528 bytes，SHA-256 `57A0AF4F325ACDF49D83F8942FE8813F3786405D5DBA150A1E1487AB0FB485ED`，已覆盖安装到 OnePlus 8T，构建物未进入 Git。无残留基准文件，无新增 ANR/FATAL。
- RF430 完成。收益只适用于已准入的有界结构化短任务；普通终端、任意 shell、Agent、detached 和长期服务继续独立 PRoot。下一步 RF440 校准三档，不扩大任务类别。

### 2026-08-01 RF430b 首个新增生产调用方

- `ContainerProcessStore` 仅把容器进程表结构化 `ps` 查询迁入 `BoundedProotTaskExecutor`：每次生成唯一 jobId，owner 为 `system:container-process-store`，lane/access 为 `PROBE/READ_ONLY`，等待 1 秒、运行 12 秒、每流最多 1 MiB。
- `kill -0`、TERM/KILL、任意 shell、Agent、终端、detached 和长期服务保持原路径；没有按 `/usr/bin/ps` 名称在公共 Planner 中分流，迁移决定属于代码 owner 的显式调用合同。
- 3 个直接相关 suite、10 项测试通过，零失败、零错误、零跳过；Debug 构建与 OnePlus 8T 覆盖安装成功。
- 固定真机探针首轮（含建池）345 ms，第二轮温热复用 101 ms；两轮均为 `warm_runner`，解析 4 条记录、691 bytes、零截断，正式 Store 来源 `host_proc+container_ps`，可见进程 2 条。
- 查询子进程结束后无残留，均衡档空闲 Runner 按 30 秒策略回收，日志无新增 ANR/FATAL。该时间只证明此调用方的复用链可用，独立 PRoot 对照和百分比留到 RF430c。

### 2026-08-01 RF430a 通用有界短任务执行器

- 新增 `BoundedProotTaskExecutor`：输入只有代码 owner 提交的结构化 argv、cwd、env、lane、读写属性、wait/runtime timeout 和双流上限；交互 lane、shell 文本形态、超过 120 秒或超过 1 MiB/流的请求在准入前失败关闭。
- warm runner 与独立 PRoot 回退由执行器生成同一 job/admission 合同并持有同一 lease；独立路径并发排水 stdout/stderr，记录截断字节，timeout 后回收进程。只有 warm job 尚未 STARTED 才由既有 pool 调用该回退。
- 固定资源采样已迁移到通用执行器，删除原来私有的 `ProcessBuilder`、双 reader 和 timeout 复制实现；任务 owner、共享写、压力必要性和结果分类不变。
- 与本阶段直接相关的 6 个 suite、38 项测试通过，零失败、零错误、零跳过；下一步 RF430b 只迁移容器进程表的结构化只读 `ps` 查询，信号与其他 PRoot 类别不迁移。

### 2026-08-01 RF420c / RF420 父任务验收

- 新增同优先级 FIFO 与关闭唤醒回归；关闭 controller 会令排队项返回 `admission_closed`，活动 lease 仍保持到业务 owner 主动释放，证明关闭不是强杀入口。
- 5 个相关 suite、35 项测试全部通过，覆盖身份合同、重复 job、排队取消、交互优先、跨 lane 推进、共享写屏障、压力收缩、timeout、关闭、runner 协议/池/控制器及固定资源采样计划。
- Debug 构建成功；APK 247,077,712 bytes，SHA-256 `BDDA6F5872DFD81D46D5BC1681F6850A9F084BF520A850B4D4B7352F7FA1C796`，构建物未进入 Git。
- 本阶段没有改变普通终端、任意 shell、Agent、长期服务或 detached 的执行路径，因此没有为无用户可见变化重复真机 UI 和 Node/Python 性能矩阵。
- RF420 完成。RF430 只接入由代码 owner 声明的有界非交互结构化 argv：有限 timeout、有限 stdio、明确副作用属性和取消出口，且 STARTED 后禁止独立 PRoot 重放；用户 shell 与产品/资源名称不能成为路由条件。

### 2026-08-01 RF420b 队列生命周期闭环

- controller 在排队前检查活动和等待集合；同一 `jobId` 的第二份请求立即以 `admission_job_id_conflict` 失败，原 lease 释放或等待项取消后该身份可安全复用。
- 新增 `cancelQueued(jobId)` 只移除尚未准入的等待项并唤醒其线程，返回 `admission_cancelled`；活动 job 返回 false，必须继续由声明的 owner/timeout 回收，准入器不杀进程。
- snapshot 新增累计 `cancelledCount`，与现有 active、queued、admitted 和 timedOut 分开；取消不会消耗容量或计为超时。
- 准入器与 warm pool 两个 suite、18 项测试通过，零失败、零错误、零跳过；下一步 RF420c 补同优先级 FIFO、关闭唤醒和固定调用方回退合同，并完成 Debug 门。

### 2026-08-01 RF420a 完整任务身份合同

- `ProotJobAdmissionRequest` 现在强制声明 owner、取消模式和结果模式；它与既有 jobId、lane、读写属性、压力必要性和等待上限共同构成完整准入身份，缺失 owner 在排队前失败关闭。
- 唯一生产调用方——固定资源采样任务——声明 `system:runtime-process-resource-sampler`、`PROBE`、共享写、timeout/owner 回收和有界 stdio；debug 基准与测试夹具也显式声明，不留默认值掩盖遗漏。
- 架构矩阵明确普通终端、任意 Recipe shell、Agent、长期服务和 detached shell 尚未进入准入；它们继续独立 PRoot，不因 RF420a 被限流，也不能被宣称为统一调度。
- 首次编译发现 debug 准入基准仍使用旧请求，补齐合同后原命令重跑成功。3 个相关 suite、18 项测试通过，零失败、零错误、零跳过。
- 下一步 RF420b 只补队列身份冲突、排队取消和统计闭环；控制器仍不杀死已经开始的业务任务。

### 2026-08-01 RF410c / RF410 父任务验收

- RF410b 已完成的 10 个相关 suite、52 项回归继续覆盖 Provider/Planner、普通终端、Agent、后台、资源 shell、停止派发和显式 View 传递；RF410c 仅新增 debug 真机探针，没有重复执行这些未受影响的测试，也没有重跑 Node 性能矩阵。
- Debug 编译与构建通过；APK 为 247,072,180 bytes，SHA-256 `DCEB615400A353BBFA2000E2DF95A07FF1BC5EDF1C050F73F7EB7F4E910DA721`，构建物未进入 Git。
- OnePlus 8T 固定探针通过：直接 shell 104 ms、复杂 shell/ELF 103 ms、结构化 argv 103 ms；正式 `RunOrchestrator -> AndroidRecipeExecutor -> PRoot Provider -> 唯一进程` 为 985 ms，最终车道 `proot_shell`，原因 `shell_command_requires_proot`。
- 当前 Ubuntu rootfs 没有 `cc`；探针确认缺失状态未被 Provider 改造掩盖，不能宣称编译器执行成功。显式 View 只验证逻辑计划和 ID 传递，不创建伪 View 或全局叠层。
- 旧 600 秒 owner 探针属于瞬态运行，不写入持久化 CardRun；误用持久化文件观察后已通过停止 debug 应用清理。同一轮固定短探针结束后无残留 PRoot/睡眠进程，日志无新增 ANR/FATAL。
- RF410 完成；下一恢复指针为 RF420，把现有准入控制器接入逐类声明，不改变 PRoot 物理构造器、View 和状态拥有者。

### 2026-08-01 RF410b 正式入口等价适配

- `ManagedRuntimeLaunchPlan.Fallback` 已替换为带 `ProotCompatibilityPlan` 的明确 `Proot` 结果；快速 Provider 的 `Unsupported` 只有在允许启动前换道时才生成最终 PRoot 计划，`Blocked` 与禁用回退继续失败关闭。
- `WorkSurfaceRuntimeBridge` 只负责把逻辑计划交给原有 shell/argv/终端物理构造器；工作目录、附加环境、login/non-login shell 与显式 View ID 均从同一计划传递，没有复制 rootfs、bind、网络或遥测规则。
- 普通/Recipe 终端、资源 attached/detached shell、Agent 主进程、Agent 配置与会话管理、后台主进程/one-shot、supervisord 健康入口均消费标准计划；架构护栏禁止这些正式入口再直接调用旧 PRoot 构造器。
- PRoot 终端即使已有显式配置，Recipe 命令仍按真实 `proot_shell` 车道在终端打开后发送；Host Node/Python 配置内命令不会再发送第二次。
- Agent 的 Host Ready 不构造 PRoot；Proot 计划只物化一份 `ContainerExecConfig`；后台进程仍只有原来的唯一 `ProcessBuilder` 创建点，运行 lane/reason 继续由原 Registry 持久化。
- 10 个相关 suite、52 项测试全部通过，覆盖 Provider/Planner、入口护栏、终端、Agent、后台、detached 接受条件和停止派发；下一步 RF410c 做 Debug 与 OnePlus 8T 等价真机门，不重跑 Node 性能矩阵。

### 2026-08-01 RF410a PRoot 兼容 Provider 合同

- 新增纯逻辑 `ProotCompatibilityRuntimeProvider`；它消费统一 `RuntimeExecutionRequest`，只保留 payload、工作目录、环境、PTY 与显式 View 事实，不创建进程或持有运行状态。
- PRoot 作为最终兼容 Provider 对复杂 shell、结构化 argv、完整 Linux、子进程、未验证扩展和 View 请求返回 `Ready`；选择原因由上游显式传入，不解析命令名或资源 ID。
- Android 原生能力与 `ANDROID_NATIVE` 要求返回 `Blocked`，不能被错误包装成 PRoot 回退后静默改变能力语义。
- 物理 PRoot argv、rootfs、bind、网络、DNS、遥测和环境生成仍只属于既有 `KFContainerManager`；本阶段没有新增第二套构造器。
- Provider 与统一请求共 7 项目标测试通过；下一步 RF410b 让 Managed Planner 和正式入口消费该逻辑计划。

### 2026-08-01 RF340c / RF340 / RF300 父任务验收

- 目录唯一性、调用形态、权限门、结果拥有者、外部交接、禁止自动回退和缺失 Keystore 不虚构均有机器断言；目录查询是无 Android `Context`、无 I/O、无状态写入的纯 Kotlin 查询。
- 原生 Recipe 的未知能力在创建线程和取得运行所有权前失败关闭；APK Android action 继续调用原路径校验和系统 Intent，只额外把稳定能力 ID 写入同一 Run。
- 11 个相关 suite、59 项测试全部通过，零失败、零错误、零跳过；覆盖下载、文件、ZIP、Recipe/Run、停止所有权、资源编译、APK 交接与权限快照。
- Debug 构建成功；本地 APK 242,278,108 bytes，SHA-256 `8C15355364CA673E8411EDBB88FABA106C2B4EEF4BCFB47EA3E5DFC500640B35`，构建物未进入 Git。
- 本阶段没有重跑 Node 性能矩阵，也没有为目录验收在真机上主动弹出系统安装器；RF310～RF330 已有 OnePlus 8T 原生执行证据继续作为 RF300 真机基础。
- RF300 完成；下一恢复指针为 RF410，把现有 PRoot 等价收敛成统一 Planner 的兼容 Provider，不改变 rootfs、交互、View 或 owner 合同。

### 2026-07-31 RF340b 薄适配与运行证据

- 现有 `install_apk` Android action 仍复用原来的路径校验、`FileProvider` 与系统安装器 Intent；执行前只通过 `CapabilityCatalog` 将旧 action 映射为稳定的 `android.apk.open_installer`，没有复制安装逻辑。
- 同一 `CardRun` 写入 `android_native` 与稳定能力 ID，同时保持 `Running` 和“已打开安装器”文案；这只表示外部系统交接，不把用户确认后的安装结果冒充为应用内成功。
- `native_capability` Recipe 现在先检查目录条目及调用形态；未登记能力或把 Android action 塞进原生 Recipe 都会在建立线程和运行所有权前失败关闭，不隐式落回 shell/PRoot。
- 运行进度和结果仍由既有 `RunStateMutation` / `CardRunStore` 持有，目录不保存运行状态，也没有新增执行器或平行 Store。
- `CapabilityCatalogRoutingTest`、`AndroidNativeCapabilityRecipeRuntimeTest` 和 `AndroidRecipeExecutorTest` 共 24 项测试通过，零失败、零错误、零跳过；下一步 RF340c 只做目录合同、Debug 构建和必要入口回归，不重跑 Node 性能矩阵。

### 2026-07-31 RF340a 真实能力与所有者目录

- 在既有 `CapabilityCatalog` 内扩展可路由条目，没有新建平行目录；登记下载、文件、ZIP、APK 系统安装器交接、默认网络对齐和运行时权限快照。
- 每项固定 `NATIVE_RECIPE`、`ANDROID_ACTION`、`LIFECYCLE_SERVICE` 或 `QUERY_ONLY` 调用形态，以及权限门、结果拥有者、完成语义和自动回退边界。
- APK 真实入口是受控路径校验后打开系统安装器，不是静默 `PackageInstaller`；目录将其标记为 `EXTERNAL_HANDOFF`，不宣称安装成功。
- 默认网络继续由 `AndroidDefaultNetworkAlignment` 持续校准，权限快照继续由 `RuntimeBootstrapGateway` 持有；目录查询不读取或复制这些状态。
- 当前源码未发现正式 Android Keystore Provider，因此不登记 `android.keystore.*`，不把架构设想冒充已实现能力。
- 目录、文件与归档共 15 项目标测试全部通过；下一步 RF340b 让既有 Android action 和原生分发器只消费目录事实，不复制执行逻辑。

### 2026-07-31 RF330c / RF330 父任务验收

- 资源审计确认当前只有 OpenCode 声明 `tar.gz`，且下载路径依赖运行时架构变量；没有静态 ZIP、缓存目标且不要求 Linux 元数据的安全迁移点，因此资源清单零修改。
- OnePlus 8T 固定压力包为 16 MiB、128 个普通文件，原生与 PRoot 交替各 3 轮；最终安全双遍顺序实现为 2026/2491/2014 ms，PRoot 暂存解包后原子移动为 669/573/724 ms，p50 2026 ms 对 669 ms，原生慢 202.8%。
- 先后验证了中央目录随机访问、移除逐文件 `fsync`、中央目录安全预检＋顺序数据流三种实现；三种真机结果都明显慢于 PRoot，继续微调没有确定性收益依据。RF330 因此是“安全能力可用、性能路由 no-go”。
- 正式 `RunOrchestrator` 在同一 `CardRun` 解包 128 项、16 MiB，用时 2442 ms；唯一车道为 `android_native`，无 `runId`、终端或进程显示面。
- 真机 zip-slip 以 `native_archive_path_invalid` 失败关闭，取消后暂存与目标均清理；单测另覆盖符号链接、重复条目、膨胀比、条目/文件/总量/深度、空间不足和原子发布失败。
- 六个相关 suite、41 项测试全部通过，Debug 构建和覆盖安装通过；探针目录已清理，无 ANR/FATAL。本地 APK 242,275,344 bytes，SHA-256 `C123A38E4FCB0D201B65F1F2966EED97CE22D7BFFC3A0A74F81DA70051E93454`，构建物未进入 Git。
- RF330 完成；安全 ZIP 仅供显式受控调用，不自动迁移资源。下一恢复指针为 RF340。Node 性能矩阵未运行。

### 2026-07-31 RF330b Recipe/Run 与取消

- 原生分发顺序扩展为下载→文件→安全归档；只有前一 Provider 明确 Unsupported 才继续，Blocked 和执行失败不会投向下一 Provider 或 PRoot。
- `archive.extract_safe` 的进度、完成、失败和取消继续写入同一 `CardRun` 的 `android_native` 车道与报告显示面；不创建 `runId`、终端会话或进程 owner。
- 归档执行复用文件能力的取消信号；停止入口等待同一个 instance/generation/step 释放，阻塞测试证明取消后不会残留目标目录。
- 四个目标 suite、27 项测试全部通过，包含真实 ZIP Recipe、中央目录安全测试、无终端绑定、阻塞停止以及既有文件/Recipe executor 回归。
- 下一步 RF330c 审计资源 ZIP 合同并完成真机恶意样本、压力和 PRoot 对照；tar/tar.gz 保持原行为。

### 2026-07-31 RF330a ZIP 安全 Provider

- 新增 `archive.extract_safe`，第一版只接受 `format=zip`，并要求调用方显式声明压缩包大小、条目数、总输出、单文件、深度和膨胀比上限。
- 只提取普通文件和目录；tar/tar.gz、符号链接、硬链接、设备节点以及 owner、mode、xattr 保真全部保持 PRoot，不把 rootfs 解包器的完整 Linux 语义误当资源安全解包。
- 路径拒绝绝对路径、Windows 盘符、反斜杠、空段、`.`、`..`、超深/超长和重复条目；目标只能位于显式可创建根。
- 第一版流式 ZIP API 的反例测试发现它拿不到中央目录 Unix 类型，会把符号链接误当普通文件；实现改为先读取中央目录元数据，再逐条打开内容流，修复通用识别机制。
- 所有内容先进入目标同级唯一暂存目录，文件关闭后才原子发布整个目录；平台不支持原子移动、取消或任一安全门失败时删除暂存，不暴露运行中半成品。第一版不承诺断电级持久化。
- 归档与文件 Provider 共 10 项目标测试全部通过；下一步 RF330b 接入现有原生 Recipe/Run。

### 2026-07-31 RF320c / RF320 父任务验收

- 逐项审计资源清单和安装编译器：现有 `mv`、`rm`、`install` 要么修改活动安装根，要么同时维护命令链接，均处在资源级锁、单份备份、验证和回滚事务内；没有可安全独立迁移的缓存文件步骤，因此本阶段不改资源清单。
- 增加固定 Debug 真机门，不接受外部路径、文件大小、动作或轮数；16 MiB 文件在 OnePlus 8T 上交替执行原生与 PRoot 各 3 轮，原生为 109/56/40 ms，PRoot 为 1284/355/151 ms，p50 从 355 ms 降到 56 ms，当前测点降低 84.2%。
- 对照双方都完成同一文件复制和落盘同步；该数据只代表当前设备、文件大小与缓存状态，不外推为所有文件操作的统一收益。
- 正式 `RunOrchestrator` 在同一 `CardRun` 顺序执行 copy→move→delete，共 172 ms；唯一车道为 `android_native`，无 `runId`、终端或进程显示面，源文件未改变，复制和移动目标最终删除。
- 真机证明工作区删除在 Provider 阶段以 `native_file_delete_not_authorized` 失败关闭；探针缓存已清理，无 ANR/FATAL。
- 五个相关 suite 的 34 项测试全部通过，Debug 构建、覆盖安装和固定真机探针通过。本地 APK 242,270,872 bytes，SHA-256 `9AFFFCDDBFCA01E79AEC87FBACA938E80BFE117491A56927E8143A5A8661A92C`，构建物未进入 Git。
- RF320 完成；下一恢复指针为 RF330 安全归档能力。Node 性能矩阵未运行。

### 2026-07-31 RF320b Recipe/Run 与取消

- 现有 `AndroidNativeCapabilityRecipeRuntime` 在下载 Provider 返回 Unsupported 后继续选择文件 Provider；下载参数受阻时不会误投文件能力，未知能力也不会回退执行 shell。
- 生产文件上下文把 `/workspace` 限定为读、创建和替换，把 `REMOVE` 只授予更具体的 `/workspace/.kf/cache`，从同一通用机制阻止工作区任意移动源和删除。
- 文件复制进度、完成、失败和取消继续写入同一 `CardRun` 的 `android_native` 车道和报告显示面；不创建终端、`runId`、`terminalSessionId` 或进程 owner。
- 停止入口同时取消下载与文件信号，并等待原生执行线程释放同一个 instance/generation/step 所有权；不存在 Host 已开始后再补跑 PRoot 的路径。
- 三个目标 suite、20 项测试全部通过，覆盖文件复制正式 Run、无终端绑定、阻塞操作停止确认以及既有下载和 Recipe executor 回归。
- 下一步 RF320c 只审计可迁移的缓存操作并做真机门；活动安装根仍归既有资源事务。

### 2026-07-31 RF320a 封闭文件 Provider

- 新增 `filesystem.copy_file_atomic`、`filesystem.move_file_atomic` 与 `filesystem.delete_file` 三个固定能力；只接受结构化路径、尺寸上限和替换策略，不解析 shell。
- 文件根分别声明读、创建、替换和删除权限；更具体的根优先，因此工作区可读写不等于可删除，缓存等受控根才能授权移动源和删除。
- 路径逐段拒绝 `.`、`..`、空段和既有符号链接；第一版只操作普通单文件，不递归删除目录，不接收活动资源安装根的隐式授权。
- 复制先写同目录临时文件、同步后原子发布；移动要求平台原子移动，`ATOMIC_MOVE` 不受支持时失败关闭，不退化成可能暴露半状态的移动。
- 取消测试发现打开输出流时提前删除临时文件在部分文件系统会失败；实现改为先退出流再清理。目标测试 5 项全部通过，覆盖权限拒绝、取消、尺寸门、原子移动失败和正式目标保留。
- 下一步 RF320b 只把这些计划接到已有 `native_capability` 与同一 `CardRun`，不迁移资源活动安装根，也不重接旧 View 事务层。

### 2026-07-31 RF310c2 / RF310 父任务验收

- OnePlus 8T 使用同一 RFC 固定样本交替执行原生与 PRoot 各 3 轮：原生为 937/534/497 ms，PRoot 为 1359/654/630 ms；原生 p50 为 534 ms，较 PRoot 的 654 ms 降低 18.3%。该结果只证明当前设备与固定下载链，不外推全部网络负载。
- 真机故障重试使用不可连接的本机 HTTPS 端点，2 次尝试后返回 `native_download_io_failure`，目标和临时文件均清理；确定性单测另覆盖首轮响应中途断流、次轮从空文件重试，禁止把残片拼入成功结果。
- 真机通过 Android 正式 Provider 流式下载 2,020,420 字节，耗时 1,156 ms，摘要 `82ef2947a26edd0acfa4326db3d91790c5ef5ce1b433c9c484770e2e1e286cfd`，原子发布并完成清理；单测另覆盖 32 MiB 流式数据与空间不足时目标不落地。
- Debug 基准服务在应用处于后台时被 Android 正确拒绝启动并记录 `BackgroundServiceStartNotAllowedException`；将应用带到前台后才执行，不以绕过系统后台限制的方式取证。
- RF310 的成功、摘要失败、取消、网络中断、有限重试、尺寸/空间门、流式压力、资源事务和 PRoot 对照均已闭环；动态 URL、多镜像及无尺寸上限输入按合同继续留在 PRoot。
- 本阶段没有重跑 Node 性能矩阵，没有安装外部 Agent；下一恢复指针为 RF320 受控文件操作。

### 2026-07-31 RF310c1 静态资源获取迁移

- 资源安装步骤新增 `maxBytes` 安全上限；只有位于动作开头、单一静态 HTTPS URL、目标为 `$install_root` 安全相对路径且尺寸上限大于零的下载，才编译为 `native_capability`。
- 原生下载先原子发布到 `/workspace/.kf/cache/resources/<id>/native-downloads`；随后既有 PRoot shell 才获取 `<install_root>.kite-update-lock`，在原事务内移动缓存、执行安装、验证、提交或回滚。下载阶段不会改活动安装根。
- Kimi、Hermes 与 Antigravity 的固定官方安装脚本已声明 16 MiB 上限并进入原生车道；OpenCode 的 GitHub Release URL 依赖架构选择和 shell 变量，继续使用 PRoot 下载，未做资源 ID 特判。
- 发现并修复通用运行事实缺口：普通 shell 准备阶段此前不会覆盖上一阶段的运行车道；现在统一写入 `proot_shell / shell_command_requires_proot`。
- 资源与原生能力定向回归、Debug 构建通过；32 MiB 流式负载和声明尺寸大于可用空间的失败关闭已加入单测。
- OnePlus 8T 固定正式链探针在同一 `CardRun` 观察到 `android_native -> proot_shell`，18,504 字节样本完成摘要验证与资源事务，总链 6,186 ms；缓存、安装根、备份和锁均清理，无 ANR/FATAL。
- RF310c 尚未完成：固定 PRoot 对照、真实网络中断/重试与更大真机负载归 RF310c2；本阶段没有运行 Node 性能矩阵，也没有安装外部 Agent。

### 2026-07-31 RF310b 验收

- 新增显式 `native_capability` Recipe 步骤，能力由 `action`、字符串 `params` 表达；不解析 shell 文本，也不读取资源 ID 或应用名决定车道。
- `AndroidRecipeExecutor` 将该步骤交给原生能力运行端；请求使用 `ANDROID_NATIVE` 要求并关闭隐式回退，当前首个能力接入 `network.download_sha256` Provider。
- 进度按 1 MiB 或 500 ms 节流，通过原有运行事件写入同一 `CardRun` 的 `android_native` 车道、原因、报告和结果；不创建 `runId`、终端会话或虚假进程 owner。
- 停止链即使没有进程绑定也会进入原生执行端，主动关闭阻塞连接，并在临时文件清理完成后确认停止；重启同样先完成这一停止合同。
- 定向回归覆盖 6 个 suite、44 项测试，0 failure、0 error、0 skipped；首次失败仅因新增 JSON 测试漏用项目既有 Robolectric 运行器，修正后原命令通过。
- 本阶段没有重跑 Node 性能矩阵，也没有迁移任何资源卡。下一恢复指针为 RF310c：资源获取显式编译为原生能力，并保留更新锁、备份和安装状态拥有者。

### 2026-07-31 RF310a 验收

- 新增标准 `ANDROID_NATIVE` Provider `network.download_sha256`，只接受结构化 HTTPS URL、受控容器目标、最大字节数、超时、有限重试、替换策略和可选 SHA-256。
- 执行器使用 Android `HttpURLConnection`，继承应用 UID 的 VPN、私有 DNS 与证书策略；流式写入同目录唯一临时文件，`fd.sync()` 后再原子移动。
- 第一版明确不发 Range：每次重试先清空本次临时文件；意外 206、HTTPS 跳转到 HTTP、未知参数和目录逃逸均失败关闭。
- 6 项目标单测覆盖成功、摘要不匹配、有限重试、取消、尺寸上限、重定向降级和意外部分响应，零失败。
- OnePlus 8T 固定真机探针从 RFC Editor 下载 18,504 字节，SHA-256 `714d11bfcbc001f98cd8a92291a19e3f670c2236ad02771092e0eea826acd13a`，原子发布成功；错误摘要保留旧目标，取消后目标与临时文件均清理，无 ANR/FATAL。
- RF310 还未完成：RF310b 必须接入同一 Recipe/Run，RF310c 才迁移资源获取并做 PRoot 对照与大文件压力；当前资源安装仍保持既有 PRoot 行为。

### 2026-07-31 RF254 / RF250 父任务验收

- 固定 CPython 3.14 ARM64 扩展源码以及 0.0.1、0.0.2 两代 wheel 元数据；编译二进制和 wheel 只作为本地证据，不进入 Git。
- 生产 `HostPythonRuntimeProvider` 现在要求 `verified_native_imports` 同时携带规范化 `pythonAbi`；ABI 缺失或不匹配均在创建业务进程前回到 PRoot，未知证据键失败关闭。
- 资源 Agent、后台依赖、自定义 Agent 和后台持久化记录都透传同一份保证证据；旧记录缺省为空，继续安全回退。
- OnePlus 8T 真机证明同一 `cpython-314-aarch64-linux-gnu` 扩展在 Host 与 PRoot 均能直接导入；0.0.1、0.0.2 分别安装到不可变代次目录后，选择 0.0.2 的 Host 导入通过。
- 原地 `pip --target --upgrade` 被反例否决：它会保留旧 `.dist-info`，导致代码已升级而元数据仍可能返回 0.0.1；正式边界固定为新代次安装、验证后切换，不做目录内覆盖。
- 伪造 `cpython-315-aarch64-linux-gnu` 时 Provider 返回 `python_native_imports_abi_mismatch`，且 Host 业务进程创建数为 0；没有包名、资源 ID 或应用名白名单。
- 强制全量回归为 240 个 suite、1267 tests、0 failure、0 error、2 skipped；强制 Debug 构建成功。最终本地 APK 为 240628388 bytes，SHA-256 `9B08814F770917BA728CFD10BF66F99C407434C95A09908B9114864F929664A4`，构建物未进入 Git。
- 本阶段没有重跑 Node 性能矩阵；RF200 完成，下一恢复指针进入 RF310 原生下载与 SHA-256。

### 2026-07-31 RF252 验收

- `runtimeGuarantees` 使用固定 wire enum 接入资源 Agent、资源后台依赖、自定义 Agent 和 `BackgroundRuntimeRecord`；未知值拒绝，缺省空集合向后兼容。
- Agent 登记、JSON 持久化、后台记录和两条 Planner 请求均透传同一事实，没有从环境变量、命令名或资源 ID 旁路推断。
- 7 个目标 suite、43 项测试零失败；新增未知自定义保证拒绝测试单独通过。
- Debug 构建与一加 8T 覆盖安装成功；真机固定清单夹具经正式 Manifest Loader 解码保证，再由生产 `HostPythonRuntimeProvider` 启动，`production_manifest_provider` 退出 0。
- 旧 Node 清单未添加无关字段，Node Provider 不读取保证；本阶段没有重复 Node 性能矩阵。
- 下一恢复指针进入 RF254；未验证的第三方扩展仍整条 PRoot。

### 2026-07-31 RF251 / RF253 验收

- Layered 真机矩阵没有重跑 RF230 性能点；新增 Linux 身份、shell 文件视图、`execve` 和带 pip venv 四个分层探针。
- 发现 Host `/bin/echo` 可表面成功但实际命中 Android `/bin`；改用 GNU/Linux 身份和 Ubuntu 文件视图断言后，Host 三类外部执行失败、PRoot 通过。
- 统一请求新增 `NO_CHILD_PROCESS` 与 `VERIFIED_NATIVE_IMPORTS` 肯定式保证；Python 缺少任一保证即在资产准备和业务进程创建前回退 PRoot。
- 受管入口只接受裸 Python 命令或稳定 `.kf/bin` 链接；每次计划重新解析当前解释器目标，活动 venv 回退，并映射 Python 专用环境路径。
- `venv(with_pip=True)` 当前 Host 因子解释器不可执行失败，PRoot 因 `ensurepip` 非零失败；两车道均未开放，后续优先验证受管 `uv`，不伪装成已解决。
- 定向测试、Debug 构建、覆盖安装与 Layered 真机模式通过；无 ANR/FATAL。下一恢复指针为 RF252，将肯定式保证接入生产声明，旧记录继续安全回退。

### 2026-07-31 RF240 验收

- 将启动器、兼容库和 rootfs 身份副本准备提取为入口无关的 `GlibcHostRuntimePreparer`；Node 既有嵌入资产未改变，因此没有重复 Node 性能矩阵。
- 新增 `HostPythonRuntimeProvider` 与 `ManagedRuntimeLaunchPlanner`，只接收受管结构化 Python argv；终端、Agent、后台入口继续只创建一条实际运行通道并写回真实 lane/reason。
- subprocess、完整 Linux、PTY、View、未验证原生扩展、`-m pip` 与 `-m venv` 均在 Host 进程创建前回退 PRoot；身份损坏继续失败关闭。
- 目标回归覆盖 7 个 suite、37 项测试，0 failure、0 error、0 skipped；Debug 构建和一加 8T 覆盖安装成功。
- 真机直接执行生产 Provider 成功；完整通用资产矩阵 20 组对照、40 个通道测点零失败，无新增 ANR/FATAL。六个发布门测点 p50 降低 45.0%～85.3%。
- 兼容边界与 RF230 一致：stdlib/内置扩展/pip 入口/纯 wheel/venv 创建通过，Python subprocess 与 venv 子解释器保持 PRoot。
- 强制全量回归为 240 个 suite、1261 tests、0 failure、0 error、2 skipped；强制 Debug 构建成功。最终 APK 240595564 bytes，SHA-256 `FC9CED0CF82F286653F3F730EBB4815F73DCB4DE164DF7E84569626858136C21`，已覆盖安装到一加 8T 并复验生产 Provider。
- 下一恢复指针进入 RF250，只研究 Python 能力分层，不重做 Node 或 RF230 基线。

### 2026-07-31 RF230 验收

- OnePlus 8T 上复用同一份 Python 3.14.6，完成 5 类负载、1/4/8/16 并发、Host/PRoot 各 3 轮；20 组对照、40 个通道测点零失败。
- Host p50 相对 PRoot 降低 37.1%～86.6%；独立进程启动、import、小文件、CPU 和 I/O 均未出现反向退化。
- stdlib、内置 C 扩展、pip 入口、纯 Python wheel 安装和 venv 创建两条车道均通过。
- Host 的 Python subprocess 与 venv 子解释器失败，PRoot 通过；第三方 C 扩展未外推为兼容，三者固定为启动前 PRoot 能力门。
- Debug 基准入口只接受固定 Benchmark/Compatibility 动作；后台启动被系统拒绝时记录 trigger_rejected，不再让应用崩溃。
- RF230 给 RF240 的 go 只覆盖纯 Python 结构化 argv；下一恢复指针进入 RF240，不重复 Node 历史矩阵。

### 2026-07-31 RF220 验收

- 将 Node 债务扩展为 HN-001～HN-011；npm/plugin 生命周期与原生构建固定为 HN-010，运行中代次租约与升级回收固定为 HN-011。
- 为每个 HN 编号映射自动测试、历史性能证据或明确未关闭状态；HN-007、HN-010、HN-011 不冒充已解决。
- Node 的性能证据仍按“前提变化才重跑”，本任务没有再次运行真机矩阵。
- RF220 完成；后续 Node 真实问题按 HN 编号增量处理，当前恢复指针进入 RF230 Python go/no-go。

### 2026-07-31 RF210 验收

- 新增 `RuntimeExecutionProvider<C, T>` 标准接口，不强迫不同 Provider 共享不合适的 Android/container 准备上下文。
- `HostNodeRuntimeProvider` 显式实现标准接口，Provider kind、公共请求和统一结果合同均已接通。
- `HostNodeLaunchPlanner` 继续拥有 Node 子进程 PRoot 合同；Provider 仍不创建进程、不写 Store、不感知页面。
- 定向回归覆盖 10 个测试套件、59 项测试，0 failure、0 error、0 skipped；未运行旧性能矩阵。
- RF210 完成，下一任务 RF220 只审计和固化未关闭债务，不重复修复已经关闭的 HN 项。

### 2026-07-31 RF100 父任务验收

- 按工具链合同执行 `:app:testDebugUnitTest --rerun-tasks`：238 个 suite、1250 tests、0 failure、0 error、2 skipped。
- 执行 `:app:assembleDebug --rerun-tasks`：60 个任务全部执行，`BUILD SUCCESSFUL`。
- 本地 APK 为 `app/build/outputs/apk/debug/app-debug.apk`，240431413 bytes，SHA-256 `81794AE06AFBEAD62ABBEF463B50663108F612AC3B337D7990E6678A28BC67F9`；构建目录由 `.gitignore` 排除，未安装、未提交、未上传。
- 新实验工作树缺少本地工具链参考，已从历史和实时 JDK/SDK/ADB/设备事实恢复 `references/toolchain.md`；该路径按仓库边界保持本地忽略。
- RF100 完成，恢复指针进入 RF210。Node 后续只做标准 Provider 适配与等价护栏，不重跑既有性能矩阵。

### 2026-07-31 RF130 验收

- 新增统一 `RuntimeProviderDecision` 与 Provider 种类，明确 Ready、Unsupported、Blocked 三种结果。
- `HostNodeRuntimeProvider` 保持既有能力判断，但只把 Unsupported 交给 PRoot；禁用回退或 Blocked 时直接停止。
- 终端、Managed Agent 和后台入口均处理 Blocked；Agent 直接断言 Blocked 时 PRoot 构建次数为 0。
- 定向回归覆盖 8 个测试套件、46 项测试，0 failure、0 error、0 skipped；`git diff --check` 通过。
- 下一步先执行 RF100 全量单测与 Debug 构建，不能用定向测试直接宣布地基完成。

### 2026-07-31 RF120 验收

- 新增 `RuntimeExecutionRequest`，严格区分结构化 argv、兼容命令文本和原生能力；加入完整 Linux、Android 原生、PTY 与 View 要求。
- 终端、Managed Agent 和后台运行均通过同一请求进入既有 `HostNodeLaunchPlanner`，没有改变 Node 原生兼容实现。
- 第一次编译发现 Planner 内残留旧 workingDirectory 变量，已改为请求字段后原命令重跑。
- 定向回归覆盖 7 个测试套件、43 项测试，0 failure、0 error、0 skipped。
- 补充 Node 对原生能力和完整 Linux 要求的前置拒绝护栏；只需重跑目标测试，不触发旧性能矩阵。

### 2026-07-31 RF110 验收

- 新增混合运行总架构、通用依赖快速通道、Android/NDK 原生能力和 PRoot 兼容 Provider 文档。
- 恢复 Node HN-001～HN-009 风险索引与 2026-07-31 性能矩阵，明确 RF210 不重复全量历史试验。
- 本地 Markdown 链接检查全部通过；`git diff --check` 通过。
- 复核现有代码中的 `/proc/self/fd/99`、robust、clone3、rseq、结构化 Node 计划、PRoot 准入和运行车道字段，文档没有把 Python 或新增原生能力写成已实现。
- RF110 完成，下一任务 RF120。

### 2026-07-31 RF110 启动

- 创建独立工作树 `D:\xm\Kite-runtime-foundation-lab` 和分支 `codex/runtime-foundation-lab`。
- 只读确认现有 Node 入口：`HostNodeRuntimeProvider`、`HostNodeLaunchPlanner`、终端、Agent 和后台结构化启动。
- 只读确认现有 PRoot 能力：`ProotJobAdmissionController`、`WarmProotRunnerPool`、STARTED 前回退边界和三档调优。
- 只读确认运行事实：`RunStateMutation`、`CardRunStore` 已持有 `runtimeLane` 与 `runtimeFallbackReason`。
- 旧 Node 文档位于本地集成历史，尚未进入当前 main；本任务将恢复正式风险索引，不重复历史性能工作。

## 待验证

- RF340b 将 APK Android action 与原生分发器接入目录，保持原执行器和状态拥有者。
- RF340c 做目录、禁止回退和无副作用查询回归；不为缺失 Keystore 实现补假入口。
- RF330 若未来更换归档引擎或出现单大文件 ZIP 样本，可按新证据重开性能门；当前多文件资源自动路由保持 PRoot。
- 动态 URL、多镜像和无尺寸上限下载不属于 RF310 已开放范围；将来只有形成可验证的结构化合同后才单独立项。
