# 后台运行强进程身份与长期 owner 桥接

## 目的与边界

本文固定 `BackgroundRuntimeRegistry` 进入长期 PRoot owner lease 前的生产事实、缺口和迁移顺序。

只覆盖 `BackgroundRuntimeMode.PROCESS` 且实际运行通道为 PRoot 的后台运行项。以下对象不在本阶段顺带迁移：

- `SERVICE` 模式的一次性服务命令；
- 终端会话；
- Agent/ACP 进程；
- Host Node/Host Python 后台进程；
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

- `HostProcessRecord` 增加 nullable start ticks；`HostProcessSnapshot` 携带 nullable boot ID。
- 抽出可测试的 `/proc stat` 第 22 字段解析。
- `BackgroundRuntimeRecord` 增加 boot ID/start ticks，JSON 向后兼容。
- Registry 增加原子 identity 更新与清理不变量。
- 不修改 attach、kill、恢复或长期 lease。

### RF830：停止与恢复桥

- 创建成功后从同轮 snapshot 捕获强身份；捕获失败仍允许现有运行，但长期 lease 保持 no-go。
- 应用重启只按 boot+PID+startTicks 精确 attach；PID 复用、boot 变化和未知观察进入 review。
- expected stop 在信号前持久化；只有观察到同一代次退出才确认 STOPPED 和释放容量。
- statusCommand/健康端点继续证明服务健康，不再证明 owner 进程身份。

### RF840：后台 PRoot 类别门

- 只消费 `lastLaunchLane=proot_shell` 且强身份 ready 的 PROCESS 记录。
- 同一 runtimeId 映射唯一 `LongLivedProotOwnerIdentity`；Host 快速通道不占 PRoot lease。
- 生产接入前必须在 OnePlus 8T 覆盖应用重启、PID 复用反例、设备 reboot identity 失效、停止竞态、进程外死亡和重复 start。

## 禁止方案

- 不把 command token、statusCommand、端口健康或 PID-only 当强身份。
- 不因 statusCommand 成功而保留无法验证的旧 PID。
- 不在发送停止信号后立即无条件写 STOPPED。
- 不让长期 owner 复用短任务 `use {}` lease 或 `SHARED_WRITE` 锁。
- 不把终端/Agent 随后台桥接一起迁移。
- 不用 runtime kind、OpenClaw 名称或资源 ID 决定是否走长期调度。
