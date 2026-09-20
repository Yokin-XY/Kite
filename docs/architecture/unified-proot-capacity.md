# PRoot 短任务与长期 owner 统一容量合同

## 目的

本文固定 RF910 的只读容量合同，为后续把后台 PRoot owner 接入实际 1/2/4 调度做准备。它解决的是“同一时刻到底已经占了多少 PRoot 容量”，不负责启动、停止、持久化或恢复。

当前有两类事实：

- 短任务实际状态来自 `ProotJobAdmissionController`，它已经控制 warm runner 与独立 fallback 的完整执行寿命；
- 长期 owner 的 phase、generation、lane、文件系统姿态和强进程身份由 RF610～RF640 状态机表达，但尚未接入生产。

RF910 不把两个 controller 并排运行。它先让短任务 controller 在同一把锁内输出 active/queued 的逐 lane 计数，再用纯 `UnifiedProotCapacityProjection` 合并未来调用方提供的长期 lease 记录。

## 统一计数

长期记录只有以下 phase 占容量：

```text
ADMITTED
STARTING
RUNNING
STOPPING
ORPHAN_REVIEW
```

`REQUESTED` 只进入队列计数，`RELEASED` 不进入 active 或 queued。这样进程尚未创建的 STARTING 窗口不会被误认为空闲；外死但尚未确认的 ORPHAN_REVIEW 也不会提前释放。

统一快照只输出：

- effective global max；
- 短任务、长期 owner 与合计的 active/queued 数；
- 合计逐 lane 数；
- 剩余容量；
- shared write、exclusive maintenance 与合同冲突的低基数事实。

快照不包含 ownerId、leaseId、PID、启动代次、命令、路径、环境或输出。

## 状态语义

- `READY`：合同有效且仍有空位；这里只表示数学上可继续评估，不执行准入。
- `FULL`：活动总量等于当前有效上限。
- `OVERCOMMITTED`：压力或前后台策略收缩后，既有 holder 数高于新上限；不驱逐 holder，只禁止新准入。
- `EXCLUSIVE_MAINTENANCE_ACTIVE`：唯一活动 holder 是长期独占维护。
- `CONTRACT_MISMATCH`：逐 lane 和总数不一致、同 owner 有多个未释放代次、多个 owner 指向同一进程代次，或独占维护与其他活动任务并存；失败关闭。

## 当前边界

`UnifiedProotCapacitySnapshot.scope` 固定为 `unified_contract_not_production`。投影不读取 Store、RuntimeHealth、`/proc` 或页面，不创建进程，也不修改 `WarmProotExecutionCoordinator`。因此 RF910 通过不代表后台长期 lease 已接入生产。

RF920 已在 `BackgroundRuntimeRecord` 内加入 provisional lease 的 generation、phase-name 和更新时间，并提供原子的 `proot_shell + STARTING` 持久化原语。字段仍属于同一后台记录，没有新增 Store；定义刷新也必须保留它们。旧 JSON 三字段全缺失表示没有 lease，部分字段、未知 phase、非 PROCESS 活跃 lease 或 Host/PRoot 路由冲突均标记为损坏并失败关闭。

RF930 已完成生产桥接。`WarmProotExecutionCoordinator` 内唯一的 `ProotJobAdmissionController` 同时持有短任务 lease 和后台长期 owner lease；长期句柄表不保存命令、状态或进程身份。实际路由为 `proot_shell` 的后台 PROCESS 必须先获得 actual 准入并持久化 STARTING，随后才允许创建唯一进程。Host Node 不进入该容量。

恢复时，持久化 holder 直接导入同一 controller；若当前档位已缩小，可呈现 overcommitted，但不会驱逐既有任务。损坏或冲突检查点会阻断新准入。创建后取得强身份才进入 RUNNING；快速退出和外死进入 ORPHAN_REVIEW。STOPPING 只有在 PRoot owner 树 settled 且强身份终态成立后才转 RELEASED 并关闭 actual lease。

RF940 已把 actual 健康接到该唯一 controller 的同锁快照。`proot_actual_active_jobs/queued_jobs` 继续表示有界短任务；`proot_long_actual_*` 表示 managed owner 活动、排队、恢复累计和合同阻断；`proot_unified_actual_*` 表示短、长、总量、状态与剩余容量。它们不扫描后台记录，也不读取 managed owner 身份表。

`proot_long_planned_*` 和 RF910 的 `unified_contract_not_production` 纯投影继续保留原名，不能冒充 actual。

RF950 已关闭后台通用 PRoot PROCESS 的生产门。OnePlus 8T 固定矩阵证明 1/2/4、短长竞争、压力收缩、PID/boot 反例、应用重启、外死、重复启动和 owner 树停止；类别门在 actual 准入与唯一进程创建前检查，并通过 `proot_long_actual_production_gate_*` 发布固定低基数状态。记录已进入 STOPPED/ERROR 但仍持有 ORPHAN_REVIEW 等未释放 lease 时，显式停止不得按普通终态跳过，仍须取得 owner settled 与强身份终态后才能 RELEASED。

该生产结论只覆盖后台实际 `proot_shell` 的通用 PROCESS。Host Node 不占 PRoot 容量；终端和 Agent 仍未迁移，也不能因为共享某些进程工具便自动继承此门。

## 长期 owner 保底余量（原 proot-short-task-headroom.md 并入，2026-09-21）

统一容量关闭了总量超售，但不可抢占的长期 holder 若占满总容量，后到的交互短任务即使优先级最高也只能等待到超时。这不是队列排序错误，不能靠提高优先级、轮询或另建 controller 解决，也不能为短任务静默杀死后台 owner。因此只对 `MANAGED_OWNER` 长期 holder 增加一条通用上限：

```text
effectiveGlobalMax <= 1: managedOwnerMax = 1
effectiveGlobalMax >= 2: managedOwnerMax = effectiveGlobalMax - 1
```

| 档位 | 总容量 | 长期 owner 上限 | 给非长期任务留下的最小余量 |
| --- | ---: | ---: | ---: |
| 低功耗 | 1 | 1 | 0 |
| 均衡 | 2 | 1 | 1 |
| 高性能 | 4 | 3 | 1 |

低功耗只有一个物理名额，无法同时运行长期 owner 与短任务；本合同不伪造第二容量、不抢占长期进程，需要短任务并发时选均衡或高性能档。

调度语义要点：上限只看 `cancellationMode=MANAGED_OWNER`，不识别资源、命令或 owner id；长期 owner 达上限后其等待项不能挡住后面的可运行短任务，共享写任务保留队首屏障防写饥饿；压力缩档与控制面恢复不驱逐既有 holder，恢复后超额仅标记 overcommitted 并拒绝新长期准入。

## 健康与验证

正式健康面只发布固定数字与枚举（`longAdmissionMax`、`longAdmissionRemaining`、`shortHeadroomCapacity`、`shortHeadroomProtected` 及 `proot_unified_actual_*` 系列），不输出 owner、PID、命令、路径或等待项身份。

生产门验证矩阵（OnePlus 8T 固定矩阵，RF1030/RF950 已过）：均衡档一长期 owner 后第二长期排队但交互短任务仍可准入；高性能档三长期 owner 后仍有一个短任务位置；低功耗档保持总量 1；压力 4→1 收缩不驱逐三个既有长期 holder；恢复导入的超额 holder 不被释放或覆盖，显式停止后余量自然恢复；PID/boot 反例、应用重启、外死、重复启动与 owner 树停止均按失败关闭验证。

该结论只覆盖后台实际 `proot_shell` 的通用 PROCESS；Host Node 不占 PRoot 容量；终端和 Agent 仍未迁移，也不能因共享进程工具自动继承此门。
