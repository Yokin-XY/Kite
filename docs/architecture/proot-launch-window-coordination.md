# PRoot 进程启动窗口协调

## 要解决的问题

多个 PRoot 进程同时冷启动时，会并发读取 rootfs 大量小文件、建立 bind/loader 状态并创建 tracee。现有 1/2/4 admission 只覆盖已经迁移的有界任务和后台长期 owner；普通终端、Agent 与若干兼容 exec 仍可同时启动。

本阶段不限制会话数量，也不把所有 PRoot 进程长期计入一个 CPU 槽。它只研究一个较短的启动窗口：

```text
结构化启动意图
-> 等待 launch lease
-> 创建唯一业务进程
-> 等待该入口已有的 READY 证据
-> 释放 launch lease
-> 会话或任务按原生命周期继续
```

## 为什么不能只包围 ProcessBuilder.start

`ProcessBuilder.start()` 返回仅代表 Android 侧 wrapper 已创建。PRoot 可能仍在翻译路径、加载 ELF、进入 rootfs 或启动解释器。若此时立刻释放，多个重型启动仍会在真正昂贵的阶段重叠，协调器只增加锁而没有收益。

READY 必须来自各入口已有事实：

| 入口 | 可接受的候选 READY | 不可接受的替代 |
| --- | --- | --- |
| 普通 exec | 首字节、明确 ready token 或进程已终结 | `start()` 返回 |
| 终端 | PTY 已创建且激活探针确认 shell 可交互 | 页面已打开 |
| Agent | stdio 进程存在且协议初始化/连接成功 | Agent 卡片已显示 |
| 后台服务 | 现有强身份加业务健康或入口明示 ready | 仅持久化 STARTING |
| bootstrap | 原有校验步骤完成 | 文件复制开始 |

当前代码审计进一步得到：

| 生产路径 | 实际创建边界 | 当前可复用事实 | RF1110 结论 |
| --- | --- | --- | --- |
| 普通终端与 Recipe 终端 | `TerminalSessionController` 构造 Termux `TerminalSession` | `isRunning + pid` 激活探针 | 现有事实只证明进程存活，不足以证明 shell 已接受输入；生产接线前需要显式无污染 sentinel 或等价 PTY ready 证据 |
| Managed Agent | `JavaAgentProcessFactory` 的 `ProcessBuilder.start()` | ACP `initialize` 成功 | 强 READY 候选，但 lease 必须跨越 `processFactory` 到 provider connect，不能由进程工厂自行猜测 |
| 后台 PROCESS | `BackgroundRuntimeHost` 直接 `ProcessBuilder.start()` | 强身份、业务健康、owner 树 | 已有长期 actual lease；启动窗口若叠加必须避免双重排队，并只在业务健康或明确失败处释放 |
| 有界 exec/维护 | `BoundedProotTaskExecutor` 独立 fallback 或 warm runner | 首字节、退出、timeout | 现有 admission 已覆盖完整任务，另加启动门可能重复限制；只进入对照矩阵，不先生产接线 |
| runtime smoke/bootstrap | `KFContainerManager` 直接创建固定校验进程 | `KITE_RUNTIME_READY`/退出校验 | READY 强，但属于准备事务，不能与普通业务进程共用失败处理 |
| Bridge/ADB automation | 各自直接 `ProcessBuilder.start()` | 输出、退出、timeout | 入口仍有旁路，若最终 go 必须通过统一物理启动适配器收口，不能逐命令加锁 |
| 信号、chmod、Host shell | Android 工具进程 | Android 进程退出 | 不是 PRoot 业务启动，明确排除 |

审计同时确认：`ProotLaunchPlan` 已有 `INTERACTIVE/EXEC/BOOTSTRAP` 与 purpose，但 `ContainerLaunchConfig` 和 `ContainerExecConfig` 没有携带这些计划事实。生产接线若 go，应先让配置保留结构化 launch metadata，再把 lease 交给实际创建者；不能从最终 argv 反向解析 lane。

通用协调器只拥有 lease 和超时，不解释协议、终端内容或业务健康。调用方在已有事实发生时释放；超时只释放启动 lease并报告，不擅自杀进程或创建回退副本。

## 两阶段合同

- 准入输入只包含稳定 request id、`ProotLaunchLane`、超时和取消句柄；不包含资源 ID、应用名、命令文本或 Agent ID。
- lease 在任何进程创建前取得，同一 request id 不能重复取得。
- `start()` 抛错时立即释放；进程创建成功后由调用方在真实 READY、明确失败或超时处释放。
- READY 后不再占用 launch lease；PTY、ACP session、后台 owner 和业务停止仍归原状态拥有者。
- 超时不得自动重放已 STARTED 进程，不得静默再创建一份 PRoot。
- lane 只决定等待顺序；共享写、长期运行和实际 CPU 压力仍由既有机制管理。

## 验证顺序

1. 先用固定 Debug 命令对比无协调、只包围 `start()`、包围到首个 READY；
2. 覆盖 1/2/4/8 同时请求，分别尝试窗口 1/2/4；
3. 记录每个请求的 wait、start-return、ready、exit 和总时间，统计 batch wall、ready P50/P95、失败率与残留进程；
4. 只有真机结果证明 READY 窗口有稳定收益，才设计生产适配；若只包围 `start()` 无收益，该模式直接淘汰；
5. 终端、Agent 和后台分别过自己的 READY/取消/重连门，不能因 Debug 固定命令成功而一起迁移。

## 当前边界

RF1110 只建立审计和实验合同。生产入口、会话数量、现有 admission、Node/Python 快速通道与 PRoot View 均不改变。

## RF1120 实测结果

OnePlus 8T 连续两套固定矩阵均 28/28 case 通过、零失败、零残留。8 并发无协调的 ready P95 为 66/70ms；READY 窗口 4、2、1 分别为 78/77ms、95/107ms、160/194ms，窗口越窄越慢。start-return 窗口在第二套偶有 P95 降低，但第一套只追平，且两套 batch wall 均无稳定改善。

该结果只证明多个独立 PRoot 的底层进入阶段；没有重跑 Node/Python 性能矩阵，也不代表特定应用。按预先固定的“稳定 P95/失败率改善且 batch wall 不明显退化”门，当前证据不支持生产启动协调。

## RF1130 最终决定

生产 no-go。RF1140 不执行，`ContainerLaunchConfig`/`ContainerExecConfig` 和所有正式创建入口保持原样，不增加 semaphore、lease、Store、健康字段或页面状态。Debug 矩阵保留为前提变化后的复算工具。

这也明确了性能归因边界：外层 PRoot wrapper 的固定 READY 是几十毫秒量级，不能解释单个重型解释器应用的多秒到几十秒启动；后续应审计通用依赖内部的文件加载、解释器、动态库和子进程成本，而不是给外层启动加锁。
