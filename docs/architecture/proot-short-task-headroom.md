# PRoot 长期 owner 与短任务余量合同

## 问题

RF950 让长期后台 PRoot owner 与有界短任务共用同一个 actual controller，关闭了总量超售，但优先级只在“仍有容量”或“已有任务释放”时生效。若均衡档的两个名额都由长期 owner 占用，后来到达的交互短任务即使优先级最高，也会一直等待到超时。

这不是队列排序错误，而是不可抢占的长期 holder 已经占满总容量。不能通过提高交互优先级、轮询或另建 controller 解决，也不能为了短任务静默杀死后台 owner。

## 保底规则

只对 `MANAGED_OWNER` 长期 holder 增加一条通用上限：

```text
effectiveGlobalMax <= 1: managedOwnerMax = 1
effectiveGlobalMax >= 2: managedOwnerMax = effectiveGlobalMax - 1
```

因此：

| 档位 | 总容量 | 新长期 owner 上限 | 给非长期任务留下的最小余量 |
| --- | ---: | ---: | ---: |
| 低功耗 | 1 | 1 | 0 |
| 均衡 | 2 | 1 | 1 |
| 高性能 | 4 | 3 | 1 |

低功耗只有一个物理名额，无法同时运行长期 owner 与短任务。本合同不伪造第二容量、不抢占长期进程；需要短任务并发时应选择均衡或高性能档。

## 调度语义

- 上限只看 `cancellationMode=MANAGED_OWNER`，不识别资源、应用、命令、runtime id 或 owner id。
- 非长期的 INTERACTIVE、SERVICE、BUILD、PROBE 仍受原 lane 上限、全局上限、压力和共享写屏障约束。
- 长期 owner 达到上限后，其等待项不能挡住后面的可运行短任务；共享写任务仍保留既有队首屏障，避免写饥饿。
- 压力缩档和控制面恢复不驱逐既有 holder。恢复后长期数高于新上限时标记 overcommitted，并拒绝新的长期 owner；已有任务只能由其真实 owner 生命周期停止。
- “余量”不是预启动 warm runner，也不是额外线程；只是限制不可抢占长期 holder 对现有总容量的占用。

## 健康与验证

正式健康面只增加固定数字与枚举：长期 owner 上限、已占数量、余量是否被长期 owner 保护。不得输出 owner、PID、命令、路径或等待项身份。

生产门至少验证：

1. 均衡档一个长期 owner 后，第二个长期 owner 排队，但后到的交互短任务仍可准入；
2. 高性能档三个长期 owner 后仍有一个短任务位置；
3. 低功耗档保持总量 1，不宣传不存在的并发；
4. 压力从 4 收缩到 1 时不驱逐三个既有长期 holder，新长期和新短任务均按总量失败关闭；
5. 恢复导入的超额 holder 不被释放或覆盖，显式停止后余量自然恢复。

该合同只扩展 RF950 的后台长期 owner 与现有短任务，不迁移终端或 Agent。

## RF1020 实施状态

actual controller 已在同一锁内落实长期上限与 waiter 绕行。阻断原因固定为 `admission_managed_owner_headroom_timeout`；恢复入口继续绕过新准入限制，以便如实恢复并呈现 overcommitted，而不是驱逐或遗忘既有 owner。正式健康字段和 OnePlus 8T 固定矩阵由 RF1030 补齐。
