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

RF920 必须在 `BackgroundRuntimeRecord` 内持久化 provisional lease generation/phase；RF930 才能让实际仲裁器在路由选定为 `proot_shell` 后、进程创建前原子占位。届时 production controller 必须直接生成统一 actual 快照，不能把本只读投影改名后冒充实际准入。
