# PRoot 活跃运行时开销归因

## 目标与非目标

本矩阵回答一个狭窄问题：Kite 当前活跃 PRoot 二进制及其正式生命周期遥测，相对 APK 内库存 PRoot 增加了多少通用执行成本。

它不运行 OpenClaw、Node 或 Python，不把上层解释器与网络时间归因给 PRoot，也不切换正式 runtime。库存二进制只在 Debug 进程内作为同输入对照。

## 固定资产身份

| 变体 | 资产 | SHA-256 | 大小 | loader | 用途 |
| --- | --- | --- | ---: | --- | --- |
| active | `proot-kf-lifecycle-arm64` | `0A465CE2F5E3DCD80F801EF500478E4932248806EDC86CE5C9B0918D60C604BC` | 356864 | embedded | 当前正式 v23，具备生命周期、保护与 View 能力 |
| stock | `proot-arm64` | `125DFF2415AE1DCB8B1AE97C51357DE73EF11F28268B86CD50A0F13AA1C3EA91` | 214416 | external | APK 已有无遥测库存候选，仅供 Debug A/B |
| historical | `proot-termux-baseline-arm64` | `AAB80BBBB38345A6CF30D5173B1D9E5FB506B72FCFB48B089DB0DA62088B51C4` | 258472 | embedded | 已因 `execve ENOSYS` quarantine，不进入成功/性能对照 |

active 与 stock 的来源代次、体积和 loader 模式不同，因此二者差值只能称为“当前打包运行时总差异”，不能直接归因于某一 patch。active 无遥测与 active 正式遥测使用同一个二进制，二者差值才用于隔离生命周期事实采集成本。

## 参数等价合同

每次执行先由 `WorkSurfaceRuntimeBridge.buildArgvExecConfig` 生成当前正式 PRoot 的唯一 argv/env/cwd/bind/network 计划。Debug 对照只能进行以下变换：

- active 正式遥测：保持正式 mode 和全部执行环境，只把 telemetry/active-registry 输出重定向到本轮独享的 Debug 私有 sink，结束后清理，避免历史文件大小和轮转污染 A/B；
- active 无遥测：保持同一 executable 和其他环境，只移除 `KF_PROOT_TELEMETRY_MODE`、`KF_PROOT_TELEMETRY_PATH` 与 `KF_PROOT_ACTIVE_REGISTRY_ROOT`；
- stock：只把 argv[0] 换成应用私有目录内的固定 stock 副本，移除上述遥测字段，并按 descriptor 为 external loader 增加同一正式 `PROOT_LOADER/PROOT_LOADER_32`。

禁止重建 rootfs/bind/network 参数，禁止更改 `activeRuntimeId`，禁止触碰安装态 `bin/proot`。三车道共享同一 rootfs、workspace、命令、输入与结果校验。

## 固定负载

矩阵使用 1/4/8 并发、三轮和轮换顺序，覆盖：

1. `startup`：`/bin/true`，观察 wrapper 固定成本；
2. `shell`：最小 `/bin/sh` 与 Linux 身份校验；
3. `metadata`：遍历固定 512 个小文件，观察路径/元数据成本；
4. `small_write`：在固定 Debug 工作区创建、核验并删除 128 个小文件；
5. `child_fanout`：同一 shell 顺序创建 16 个 `/bin/true` child。

入口不接受 ADB 自定义命令、路径、并发或轮数。结果必须同时校验 exit、固定 token、文件数量/内容；失败样本不进入延迟统计。

## 判断门

- 任一变体语义不同、残留进程、超时或 ANR/FATAL：停止性能外推；
- 小于 15ms 的绝对差不作为可行动热点；
- active 正式遥测相对 active 无遥测或 stock，只有在至少两个通用负载的 4/8 并发中同时满足 P50 退化至少 15%、绝对至少 15ms，并在至少两轮同向时，才进入热点定位；
- active 与 stock 差异若存在，但 active 无/有遥测接近，只能说明二进制/loader/来源总体差异，不能据此删除生命周期能力；
- 候选优化不得关闭强身份、停止确认、保护语义或默认无 View 路径的失败关闭。

## 发布边界

RF1410/1420 只新增 Debug 基准和文档。只有 RF1430 找到可归因、可复算且不牺牲语义的通用热点，才允许修改 PRoot 源/资产；否则 RF1440 以“当前 Kite PRoot 增量不是主要瓶颈”收口。

## RF1420 真机结论

OnePlus 8T 的两套最终矩阵均使用每套独享的 telemetry/registry sink。每套 45 组全部成功、零残留，sink 为 1,527,550 bytes、零轮转，无匹配 ANR/FATAL。

- startup、shell 与 metadata 三类没有达到 15ms 行动阈值；
- small-write 的 active 有/无遥测在 4/8 并发都稳定慢于 stock 89～115ms，而 active 两变体彼此接近，热点属于 active 二进制的默认文件操作路径；
- child-fanout 在 4 并发下，active telemetry 两套均比 active no-telemetry 多 98～109ms，但 8 并发接近，说明 lifecycle 写入存在特定竞争窗口，尚不能概括为线性并发成本；
- stock 与 active 来源/loader 不同，RF1430 仍须在 PRoot 源码/patch 中定位通用 fast-disable 或事件写入路径，不能直接回退 stock。

RF1430 因两个通用负载跨两套矩阵达到相对与绝对门槛而打开。可改边界仅为默认无 View、无保护事务的低成本跳过，以及不损失事件、强身份和退出确认的 telemetry 实现；产品功能不作为性能开关。
