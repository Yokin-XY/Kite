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

## RF1431 热点拆分结论

固定 Debug 热点矩阵只使用当前 active 和 stock 资产，继续复用正式 argv/env/rootfs/bind。small-write 增加四个 active 对照：关闭 `kf_procfs`、关闭 `mountinfo`、同时关闭、强制 external loader；child-fanout 增加无 registry 的共享 telemetry 与每个 PRoot 独立 telemetry 文件。最终判断使用九轮 wall samples，不以三轮中的调度偶发值决定源码改动。

OnePlus 8T 九轮结果：

| 负载 | 并发 | active no telemetry | stock | 关键对照 |
| --- | ---: | ---: | ---: | --- |
| small-write | 4 | 232ms | 140ms | no-procfs 230、no-mountinfo 243、minimal 242、external-loader 238ms |
| small-write | 8 | 303ms | 199ms | no-procfs 315、no-mountinfo 309、minimal 302、external-loader 320ms |
| child-fanout | 4 | 123ms | 118ms | telemetry 214、log-only 213、log-sharded 215ms |
| child-fanout | 8 | 231ms | 235ms | telemetry/log-only/log-sharded 为 232/235/232ms |

结论边界：

- 默认 `kf_procfs`/`mountinfo` 扩展分派与 loader 模式不是 small-write 的稳定主因，不能通过默认关闭功能取得收益；
- active no-telemetry 的 small-write 差值仍属于 v23 总体 patch/build 差异，只有重建同源候选后才能继续二分；
- child-fanout 的 4 并发增量不由 active registry 或共享日志争用主导；独立日志仍保留约 90ms，因此热点是 lifecycle 每事件同步采集、格式化与落盘总路径；
- 8 并发已经落入设备吞吐平台，任何只在 8 并发好看的方案都不能算优化；
- RF1432 只能优化事件实现，不减少事件、不关闭 telemetry、不弱化强身份与退出确认。候选必须先在 Debug 私有路径与正式资产并行，不能直接覆盖 runtime descriptor。

## RF1432 可复现源码与 lifecycle 候选

正式 v23 已从 `d30b98846cfdf0923bea26956922a2acf9ef23ae` 和六个仓库 patch 在隔离目录重建。固定 NDK 26.3、版本字符串和构建参数后，重建产物为 356864 bytes，SHA-256 `0A465CE2F5E3DCD80F801EF500478E4932248806EDC86CE5C9B0918D60C604BC`，与 APK 正式资产逐字节一致。仓库通过 `scripts/build-proot-runtime-ablation.ps1` 保留这一复现合同；脚本只向 `local-artifacts/` 输出，不生成 Git 构建物。

三个无损 lifecycle 候选依次尝试：

1. 用有界栈缓冲区一次编码并一次 `write`；
2. 复用单个 `O_APPEND` fd，并在轮转或失败时安全重开；
3. 记录每个 Tracee 的 registry 活跃事实，只在计数归零时确认并退休 session。

候选三与正式 active 对照 110 个 session、5404 个事件，事件 schema、每 session 连续序号、事件类型签名和总字节一致。profile 把每事件成本分为 identity 1µs、scope 1µs、open 0µs、编码/写入 19µs、registry 249µs；但候选 wall 未改善：child-fanout 4 并发为 active/candidate `219/221ms`，8 并发为 `238/239ms`。这证明 registry 的总原子快照路径昂贵，但单独减少日志 open、编码调用或空 session 扫描并不能降低当前批次时间。三个候选均 no-go，不生成正式 patch。

## RF1433 正式补丁消融

固定 Debug 入口按顺序构建并比较以下同源层级：

| 变体 | 含义 | SHA-256 |
| --- | --- | --- |
| `patch_00_base` | `d30b988` 加 Android 头文件构建兼容，不含 Kite 功能 patch | `F8BD91DE...E251` |
| `patch_01_lifecycle` | 加 lifecycle | `9435B333...E6CF` |
| `patch_02_procfs` | 再加 procfs | `DFEB842A...7C88` |
| `patch_03_transaction` | 再加 transaction | `E52501DA...6D5A` |
| `patch_04_protection` | 再加 protection | `DC57AE34...8B28` |
| `patch_05_view` | 再加 View v1 | `7B1B4C5C...4247` |
| active | 再加 block View v2，即正式 v23 | `0A465CE2...4BC` |
| `patch_06_unbundled` | 完整 v23，编译时 external loader | `205C06FA...A1A` |
| `patch_07_ndk28` | 完整 v23，改用 NDK 28.2 | `57778BB2...769B` |

三套 OnePlus 8T 九轮矩阵均结果正确、零残留。4 并发样本受升频和调度影响存在约 130ms/240ms 两簇，但各补丁层中位数均为 `132～160ms`，stock 为 `136～139ms`，没有层级台阶。8 并发结果稳定：所有 `d30b988` 同源层为 `299～317ms`，stock 为 `204～209ms`；unbundled 为 `305/314ms`，NDK 28 为 `314ms`。

所以当前 high-concurrency small-write 差异在第一个 Kite patch 之前已经存在，也不由 embedded loader 或 NDK 26 引起。库存资产报告 PRoot 5.1.0，但源码和构建来源未知，且不具备正式 lifecycle、active registry、保护与 View 语义，不能作为生产替代。RF1400 最终 no-go：正式 v23 保持不变，后续只有拿到同源、同能力的新 PRoot 基线并通过完整语义矩阵时才允许重开。
