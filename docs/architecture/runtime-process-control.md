# PRoot 进程事实与控制协议

## 目标

Kite 的卡片不是 Linux namespace 或 Docker 容器，而是一个稳定的生命周期 owner：由该卡片命令产生的进程都继承 owner。运行管理、单进程结束、工作负载结束和卡片停止必须消费同一份 PRoot 事实，不能分别扫描和猜测。

本协议优先保证两件事：

1. 不把仍在运行的卡片进程丢进“未归属”。
2. 不因 PID 复用、共享进程组或记录不完整而结束其他卡片或 Kite 本身。

## 数据链

```text
PRoot fork/clone/vfork/exec/exit/signal
  -> v2 JSONL 增量事件（审计与低成本更新）
  -> 每个 PRoot 会话的 active registry（当前事实与缺口恢复）
  -> ProotTelemetryStore（唯一 Android 活跃事实）
  -> TaskManagerStore / CardRunStore（管理投影与卡片事务）
  -> 运行管理一级页、卡片/全部/未归属二级页
```

事件负责快，活动注册表负责全。Android 不用周期性 `ps` 或全系统 `/proc` 扫描维持事实；只有冷启动、事件序号缺口、日志截断或停止确认需要读取 PRoot 已知会话的注册表。

## 稳定身份

每个 v2 进程引用同时包含：

- `telemetrySessionId`：一次 PRoot 会话；
- `lifecycleSeq`：会话内单调生命周期编号；
- `hostPid` / `guestPid`：执行和展示句柄；
- `startTimeTicks`：Linux `/proc/<pid>/stat` 的进程启动时钟；
- `parentLifecycleSeq`：不会因 PID 复用混淆的父关系；
- `eventSeq`：检测重复、乱序和缺口。

破坏性动作只接受强身份引用。发送信号前，`ProotProcessVerifier` 定向读取目标 `/proc/<hostPid>/stat`，核对 `startTimeTicks`。不匹配表示 PID 已被复用，必须拒绝执行；不可读取表示证据不足，也必须失败关闭。

旧 v1 事件继续兼容显示，但没有强身份的行只读，不能进入精确结束路径。

### 三层身份

运行管理明确区分三层身份：

- `ownerId`：卡片、资源、终端等外层生命周期与整体回收边界；
- `workloadScopeId`：一次 Linux 作业/应用工作负载的自动分组和整组动作边界；
- `lifecycleId`：单个进程代次的精确控制身份。

`workloadScopeId` 不由资源清单或应用适配表填写。`ProotWorkloadScopeProjector` 在每个 PRoot 会话中，以
会话根为启动入口，找到首个脱离根 PGID/SID 的 Linux 作业，并把对应进程组长的强生命周期作为作用域根；
子孙继承最先出现的作用域，不会因 `exec` 改名或后续再建进程组而换组。同一 pipeline 的兄弟进程通过组长
生命周期归一，不同 PRoot 会话则始终隔离。没有作业变化的派生树退化为会话根作用域。
当会话根已经派生出独立作业时，根作用域被标记为启动基础并进入“运行基础”，不会额外生成一组无意义的
`bash`；该判断同样只看拓扑和作用域，不匹配 shell 名称。

名称、命令行和可执行文件只决定显示文字。两个 `python` 可以属于两个作用域；同一作用域内的
`python`、`node` 和 helper 也可以显示在同一个应用组。

## 活动注册表

每个 PRoot 会话维护独立目录：

- `meta.json` 表达会话和发布序号；
- 每个活跃 tracee 一份原子替换的 JSON；
- 最后一个 tracee 退出时，先把会话目录原子改名为隐藏退役目录，再删除内容。

Android 全量恢复可以读取所有可见会话；停止某个 owner 或工作负载时只调用 `readSessions(sessionIds)` 读取目标已登记的会话。无关历史会话损坏或正在更新不能阻塞当前停止，目标会话目录消失表示稳定空集，隐藏退役目录不会重新导入旧事实。

## 控制语义

### 单进程

按强身份定向核验，先发 `SIGTERM`，宽限后仍为同一生命周期才发 `SIGKILL`，最后再次核验。PID 消失或已换代都不会继续发送信号。

### 工作负载

“全部”、卡片详情和“未归属”统一按 `workloadScopeId` 分组；未归属只表示没有 owner，不表示没有工作负载。
整组结束只提交作用域身份，后台从最新 `ProotWorkloadScopeIndex` 重新解析成员，使用
`parentLifecycleSeq`/PPID 子进程优先结束。每个成员仍逐一通过强身份核验；停止期间有限重读同一作用域，
接住新派生子进程。目标会话的活动注册表用同一作用域协议重投影当前成员；缺少强身份或目标注册表不完整
的组不进入破坏性动作，但全局无关旧会话为 partial 不影响目标会话的精确控制。

### 卡片 / owner

停止开始后 owner 绑定继续保留。每轮合并：

- `ProotTelemetryStore` 当前 owner 成员；
- 该 owner 已知会话的活动注册表成员；
- 停止期间新产生且继承同一 owner 的成员。

所有目标逐一核验并直接通过 JNI `kill(pid, signal)` 处理。只有连续两轮出现“目标注册表为空、所有已知身份均不再活跃”，才确认 owner 已停止并清除可见绑定。超时、D 状态、注册表不稳定或 `/proc` 不可读都保留绑定并报告未确认，后台只能按相同精确范围重试。

### 前台状态收敛

停止事务不得借用“启动失败”表达确认尚未完成：

- 用户提交停止后立即进入 `Stopping`；
- 执行层明确报告仍有进程或无法核验时进入 `CleanupPending`（“停止待确认”），保留 owner 和运行绑定；前端经过某个时长不得自行写入该状态；
- 终止层逐个发出 owner 已退出事实，`CardRunStore` 只在该代实例的所有 owner 都已确认时收敛为 `Stopped` 并清除绑定；
- `Failed` 仅保留给真实的启动或执行失败，不再承载停止超时。

停止请求必须携带该代实例的完整 owner 集合：已登记叶子 owner、当前 owner 与根 owner。状态层不得等待一个从未交给控制层处理的根 owner。控制层到达执行窗口末尾时，活动注册表只负责发现待核验目标，最终结论只认强身份定向核验：目标已经为空则确认停止；只有仍观测到具体进程时才报告残留，不能把注册表旧条目或“没有完成额外等待轮次”解释成超时失败。Bridge 等待窗口结束只触发一次相同 owner 范围的定向核验，不产生用户可见的“超时”终态，也不重复提交停止命令。

运行管理页的可见性以当前进程与存活终端事实为准：已经没有进程的旧绑定不再生成“0 个进程”空卡片；非运行状态如果仍然有真实残留，则继续展示并提供精确结束入口。应用进程重启后不恢复旧代 `CleanupPending` 为当前卡片，只保留关闭的历史记录。

## 明确禁止的兜底

- 不按进程名结束；
- 不用全系统 `/proc` 或 `ps` 发现 owner；
- 不把“信号发送成功”当作进程已经退出；
- 不因 TTL 删除没有退出证据的活跃记录；
- 不在 owner 精确停止失败后扩大到 Android PGID。

最后一条尤其重要：PRoot 启动的多个卡片以及 Kite 可能共享 Android 进程组。PGID 只参与识别 PRoot 会话内
的 Linux 作业边界，并与会话、组长强生命周期共同生成作用域；发送信号时仍逐个核验成员，绝不执行
`kill(-pgid)`。没有独占证据时，PGID 不是卡片隔离边界。历史上的进程组兜底会同时结束 A、B 和 Kite，
已经从正常路径和 Bridge 二次兜底中删除。

## 性能模型

- 正常事件：按会话/生命周期键做增量更新；
- 普通页面：只消费不可变投影，不扫描文件或进程；
- 进入详情/执行动作：仅核验可见或目标 PID；
- 工作负载结束：只重读该作用域在 PRoot 活动注册表中的已登记成员；
- owner 停止：只读取目标 owner 的已知会话目录，目标数量为该 owner 的进程数；
- 完整注册表恢复：只在冷启动或明确缺口时执行，并按 PRoot 已登记项工作。

因此成本随目标 owner 的进程数增长，不随手机全系统进程数增长，也不会为每个 PID 常驻独立计时器。

## 未来 cgroup 接入

cgroup 是可选的更强执行边界，不改变本协议的上层语义。接入时应保持：

1. owner 仍是产品生命周期身份，`workloadScopeId` 仍是应用/作业身份；
2. PRoot 创建进程时把成员加入作用域对应 cgroup，并保留 owner 到作用域的关系；
3. 运行管理仍从统一事实快照投影，不从 cgroup 另建页面状态；
4. `CgroupBackend` 可以替代作用域/owner 成员枚举和批量发送，但执行前后仍校验身份对应关系并确认空集；
5. 不支持 cgroup 的设备继续使用当前强身份定向后端。

这样未来增强隔离能力时，只替换底层成员来源和执行器，不修改卡片、应用树、页面和停止事务合同。

## 当前实现入口

- PRoot 协议补丁：`assets/proot/patches/kf-proot-lifecycle-telemetry-v2.patch`
- 运行时描述：`assets/proot/proot-runtime.json`
- 活动注册表读取：`ProotActiveRegistry.kt`
- 身份与 `/proc` 核验：`ProotProcessEvidence.kt`
- 单进程/进程树执行：`ProotProcessControlBackend.kt`
- 工作负载身份与结束：`ProotWorkloadScopeProjector.kt`、`ProotWorkloadScopeTerminator.kt`
- owner 停止：`ProotOwnerProcessTerminator.kt`
- 统一事实：`ProotTelemetryStore.kt`
- 管理投影：`TaskManagerStore.kt` 与 `RuntimeManagementProjector.kt`

## v12 验证基线

- PRoot 上游基线：`d30b98846cfdf0923bea26956922a2acf9ef23ae`；生命周期 v2 与 procfs 补丁均通过严格应用检查和 Android arm64 重放构建。
- 打包二进制：251000 字节，SHA-256 `DFEB842ADB5C2FB41991110AE67A79299CA874F8E22A338F171371C617717C88`；真机安装后的哈希一致。
- OnePlus 8T A/B 隔离：停止 A 后，A 的根/子进程和活动会话均退出；B 的根/子进程与活动会话继续存在；Kite 应用 PID 不变。随后停止 B 也只结束 B。
- UI 卡片停止：受控卡片的根进程和子进程均退出，Kite 应用继续存活；一级范围、卡片二级树、菜单与确认层无裁切或崩溃。

这些证据是当前 v12 的回归基线。后续替换 PRoot、引入 cgroup 或调整停止后端时，必须重复 A/B 隔离验证，不能只以单卡片“似乎停止”作为验收。
