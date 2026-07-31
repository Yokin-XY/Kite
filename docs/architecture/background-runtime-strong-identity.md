# 后台运行强进程身份与长期 owner 桥接

## 目的与边界

本文固定 `BackgroundRuntimeRegistry` 进入长期 PRoot owner lease 前的生产事实、缺口和迁移顺序。

强身份采集、恢复和停止安全规则覆盖 `BackgroundRuntimeMode.PROCESS` 后台运行项；RF840 的长期 owner lease 生产试接只覆盖实际运行通道为 PRoot 的记录。以下对象不在 RF840 顺带迁移：

- `SERVICE` 模式的一次性服务命令；
- 终端会话；
- Agent/ACP 进程；
- Host Node/Host Python 后台进程的长期 PRoot lease；
- 普通未登记 Ubuntu 进程。

长期 lease 不能用命令名、资源 ID 或 runtime kind 特判选择。只有真实运行结果为 PRoot，且已取得可恢复强身份的后台 owner，才可能进入后续类别门。

## 当前生产链

### 创建与发布

1. `BackgroundRuntimeHost.startRuntimeInternal` 使用 `BackgroundRuntimeStartSingleFlight` 合并同一 runtimeId 的并发启动。
2. `startProcessRuntime` 在启动前把记录写为 `STARTING/pid=null`，再由 `ManagedRuntimeLaunchPlanner` 选择 Host 或 PRoot。
3. `ProcessBuilder.start()` 返回后，`RuntimeHandle` 只保存 `Process`、日志和两个 Job。
4. `Process.safePid()` 得到 PID，`BackgroundRuntimeRegistry.updateStatus` 直接持久化 `RUNNING/pid`。
5. monitor 等待 wrapper 退出；若 PID 与命令 token 仍被认为存活，可把记录继续保持为 `RUNNING`，否则写入终态并触发现有恢复策略。

已有正确边界：同进程内 handle 存活时不会重复拉起，入口有 single-flight，实际 Host/PRoot 通道已经写入 `lastLaunchLane/lastLaunchReason`。

### 应用重启与外部 attach

1. `ensureInitialized` 调用 `reconcilePersistedStates`，保留持久化的活动状态和 PID。
2. `refreshProcessRuntimeStatus` 在本地 handle 不存在时调用外部探测。
3. `resolveRuntimeHostPid` 只取持久化 PID；`matchesRuntimeHostProcess` 检查它仍是当前应用 UID 下的 container-like 进程，并用 command/cmdline token 做弱匹配。
4. statusCommand 成功也能把记录判为 `RUNNING`；如果没有解析到 host PID，会继续保留旧 PID。

这里没有进程启动代次。Android/Linux 复用 PID 后，同 PID、相似命令即可被误 attach；token 为空时甚至会接受任意 container-like 进程。statusCommand 只能证明“某种服务响应”，不能证明它就是该 owner 的同一宿主根进程。

### 停止与确认

1. `stopProcessRuntime` 先把 runtimeId 写入仅内存的 `stoppingRuntimeIds`。
2. 它取本地 handle PID，或用上述弱匹配恢复 PID，再调用 `HostProcessTerminator`。
3. 终止结果会写日志，但随后无条件写 `STOPPED/pid=null`。
4. `markExpectedStop` 在进程终止与 STOPPED 写入之后才持久化；应用若在这段窗口死亡，停止意图会丢失。
5. `RuntimeStateReconciler` 对未观察到的活动记录会写 STOPPED、释放当前内存准入预算，并按停止协调结果决定是否自动恢复。

因此当前链不能证明“被停止的是同一进程代次”，也不能证明 STOPPED 时进程已经退出。该语义足以支撑现有 best-effort 后台管理，但不足以持有长期 lease 容量。

## Host 观察能力现状

`HostProcessInspector` 已直接读取 `/proc/<pid>/stat`，并正确处理 comm 中括号后字段；当前只投影：

- process group id；
- session id；
- user + system CPU ticks。

Linux `/proc/<pid>/stat` 的第 22 字段 `starttime` 尚未进入 `HostProcessRecord`。`HostProcessSnapshot` 也没有读取 `/proc/sys/kernel/random/boot_id`。

仅 `(pid, starttime)` 只在同一次系统启动内唯一。后台 JSON 会跨设备重启保留，所以持久化边界必须同时记录 boot identity；设备 boot 不一致时，旧 PID/代次一律不得 attach。长期 lease 内部仍可在同一 boot 已确认后使用 RF610 的 `(hostPid, processStartTicks)`。

`ps -A` fallback 无法提供 starttime。命中 fallback 只能继续做现有弱诊断，不能生成强身份或进入长期 lease。

## 最小生产模型

### 宿主观察值

新增通用、不可伪造的观察结果：

```text
HostProcessIdentityObservation
  bootId
  hostPid
  processStartTicks
```

来源必须是 Android 进程内同一轮 `/proc` 快照：boot ID 读取成功，目标 PID 属于应用 UID，`stat` starttime 有效，并继续通过现有 container-like 与 owner token 校验。命令 token 只能作为归属附加门，不能替代启动代次。

### 后台持久化值

`BackgroundRuntimeRecord` 保留兼容字段 `pid`，追加：

```text
processBootId: String?
processStartTicks: Long?
```

不另建 Store。旧 JSON 缺字段时读取为 null，状态可以继续展示和走现有探测，但必须标记为 identity unavailable，禁止长期 lease attach。

身份只能通过原子 registry API 写入；定义 upsert 和内置项刷新必须保留它，PID 改变或清空时必须同时清空 bootId/startTicks，不能让新 PID 携带旧代次。

### 停止意图

不新增平行停止状态。继续使用 `lastStopReconciliation*`，但顺序必须调整为：

```text
持久化 expected stop
-> 重新读取并精确校验 bootId + PID + startTicks
-> 发送信号
-> 观察同一代次退出
-> 写 STOPPED、清身份、释放长期 lease
```

身份未知、已变化或观察失败时，不向该 PID 发送信号，不写已确认 STOPPED，不启动替代进程；记录进入 orphan/review，由现有停止协调面显示原因。

## 分阶段迁移

### RF820：值对象与持久化

- [x] `HostProcessRecord` 增加 nullable start ticks；`HostProcessSnapshot` 携带 nullable boot ID。
- [x] 抽出可测试的 `/proc stat` 第 22 字段解析。
- [x] `BackgroundRuntimeRecord` 增加 boot ID/start ticks，JSON 向后兼容。
- [x] Registry 增加原子 identity 更新与清理不变量。
- [x] 不修改 attach、kill、恢复或长期 lease。

### RF830：停止与恢复桥

- [x] 创建成功后窄读目标 `/proc/<pid>` 与 boot ID 捕获强身份；该 PID 必须属于当前应用 UID。捕获失败仍允许本地 handle 运行，但长期 lease 保持 no-go。
- [x] 应用重启只按 boot+PID+startTicks 精确 attach，并追加 container/owner token 归属门；PID 复用、boot 变化和未知观察进入 review。
- [x] statusCommand/健康端点只证明服务响应；未得到精确外部 PID 时必须清理旧 PID，不能恢复 owner 身份。
- [x] expected stop 在信号前持久化；本地 handle 直接停止其拥有的 `Process`，重启后的外部 PID 仅在强身份和 owner token 共同命中后进入受 guard 的终止器。
- [x] STOPPED、身份清理和容量释放只在 handle 已退出、强身份终止器确认原代次消失，或观察证明原 boot/代次已不存在后发生；身份不可得时保持 review。

创建链中的 `Process` handle 本身是归属证据，因此用目标 PID 的应用 UID + boot/start ticks 捕获；应用重启后没有 handle，必须在强身份之外继续通过原有 container/owner token 门。两条链都不使用 statusCommand 生成或补全进程身份。

### PRoot owner 树停止

PRoot 通道返回的本地 `Process` 只是 owner 树的宿主根。它退出后，内部 shell、任务子进程或继承 stdout/stderr 管道的子进程仍可能存活；此时只 `destroy()` 根进程既不能证明业务已经停止，也会令 monitor 卡在日志 reader 收尾，形成“PID 已消失但记录一直 RUNNING”的半状态。

因此实际车道为 `proot_shell` 时，停止必须先调用既有 `ProotOwnerProcessTerminator`，以 runtimeId 作为通用 owner identity 收敛完整树；只有 owner 终止结果 `settled` 且本地 wrapper 已退出，才允许确认 STOPPED。owner 仍有残余、身份遥测不足或终止器异常时，不补杀单个 wrapper，不释放容量，继续保持 pending/review。Host 通道仍直接使用其本地 handle 或受强身份保护的 detached PID 路径。此规则只看实际车道与 owner，不识别资源 ID、命令名或 runtime kind。

### 外部 PID 信号的内核边界

当前 JNI 终止能力最终仍是 `kill(pid, signal)`，没有持有 `pidfd`。RF833 已在 TERM、等待轮询和 KILL 前窄读 boot/PID/start ticks；一旦代次变化就把原进程视为已退出，绝不向当前 PID 继续发信号。这关闭了 TERM 等待期间 PID 被复用后再误发 KILL 的主要窗口。

但“最后一次 `/proc` 复核”与实际 `kill` 系统调用之间仍存在无法由用户态 PID 比较彻底消除的微小 TOCTOU。RF840 若要求内核级严格身份持有，必须在设备内核支持时引入 pidfd 条件能力；不支持 pidfd 的设备只能把重启后 detached PID 停止标记为受强复核的 best-effort，不能宣传为内核原子保证。本地 `Process` handle 路径不受该限制。

### RF840：后台 PRoot 类别门

RF840 的生产接入结论为 **no-go**，不是强身份链失败，而是统一容量事实尚未闭环：

- `LongLivedProotAdmissionSimulator`、恢复规划器和 `proot_long_planned_*` 仍只有相互引用，没有任何生产调用方；它们明确标记 `planned_not_production`。
- 短任务实际容量由 `WarmProotExecutionCoordinator` 内的 `ProotJobAdmissionController` 持有；若另建长期 controller，两边不会互相计数，实际同时运行数可以超过同一 1/2/4 档位。
- `BackgroundRuntimeRegistry` 目前只持有运行状态、实际车道和强进程身份，没有“已准入但尚未创建 PID”的长期 lease generation/phase。只从 RUNNING 记录反推会漏掉 STARTING 容量，应用重启也无法区分旧准入和新请求。
- 正式健康面只有短任务 `proot_actual_*`；长期字段仍是规划 schema。此时改名或投影后台 RUNNING 数会把未受统一准入控制的进程冒充 actual lease。
- OnePlus 8T 已覆盖进程外死亡、重复 start、强身份失效和完整 owner 树停止，但应用进程被系统结束时 PRoot 子树也随 UID 退出，尚未形成“控制面重启而 owner 存活”的真实 reattach 样本；设备 reboot 也不应拿运行中的用户设备强行取证。detached PID 路径另受无 pidfd 的内核边界限制。

因此 RF840 不修改生产准入。下一阶段必须先把短任务和长期 owner 放入同一实际容量仲裁器；后台记录仍是 owner/身份事实源，仲裁器只持有容量序列和 lease，不复制命令、状态或进程身份。生产试接仍只消费 `lastLaunchLane=proot_shell` 且强身份 ready 的 PROCESS 记录；Host 快速通道不占 PRoot lease。停止释放 lease 前必须同时取得 PRoot owner 树 `settled` 和强身份终态，只退出 wrapper 不算停止完成。

## 禁止方案

- 不把 command token、statusCommand、端口健康或 PID-only 当强身份。
- 不因 statusCommand 成功而保留无法验证的旧 PID。
- 不在发送停止信号后立即无条件写 STOPPED。
- 不让长期 owner 复用短任务 `use {}` lease 或 `SHARED_WRITE` 锁。
- 不把终端/Agent 随后台桥接一起迁移。
- 不用 runtime kind、OpenClaw 名称或资源 ID 决定是否走长期调度。
