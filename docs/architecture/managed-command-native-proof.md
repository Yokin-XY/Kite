# 受管命令原生证明边界

## 要解决的重复成本

资源卡的“打开”和“获取”是两个独立正式动作，但都会先核对已登记资源的 `managedCommands`：

- `open()` 在不存在可复用运行实例时调用 `reconcileInstalledResources()`；
- `install()` 的 `buildInstallPlan()` 在计算依赖前调用同一方法。

当前首次核对由 `AndroidResourceInstalledStateProbe` 创建一条静默 PRoot Recipe，并批量执行 `command -v`。已有的 `managedCommandVerificationBasis()` 只负责给成功结果建立运行时、PATH 文件和安装记录的正向缓存身份；它还不是探测结果，首次动作仍进入 PRoot。

因此候选不是资源或命令特判，而是一项结构化只读能力：在 Android 已经掌握完整物理事实时，直接证明普通默认 PRoot PATH 中的受管外部命令是否存在；事实不完整时继续执行原有 PRoot 探针。

## 允许原生表达的共同合同

只有同时满足以下条件，RF1530 才能返回原生证明：

1. 使用普通默认容器，不带显式环境或 View；
2. PATH 来自当前普通启动准备身份，不接受调用方自定义 PATH；
3. 命令是 PATH 中的外部文件，不推断 shell 函数、别名、builtin 或版本输出；
4. 符号链接全链可解析，目标仍位于容器可见宿主根内；
5. 最终目标是普通文件，并具有至少一个执行位；
6. 运行时身份、容器身份、安装记录和命令文件身份均完整；
7. 只给出本次肯定式事实，不缓存缺失或错误结论。

动态 shell PATH、安装脚本内部的任意 `command -v`、命令实际执行、版本检查、显式环境/View 和不完整身份均不属于该能力，继续走 PRoot。

## RF1520 固定反例与结果

Debug 基准不接收 ADB 命令、路径、轮数、资源 ID 或环境参数，只创建四个固定临时事实并在退出前核对清理：

| 固定事实 | PRoot `command -v` | 当前 Android 文件身份 | RF1530 期望 |
| --- | --- | --- | --- |
| 普通可执行文件 | 存在 | 存在 | 存在 |
| 缺失文件 | 缺失 | 缺失 | 缺失或回退 |
| 断链符号链接 | 缺失 | 缺失 | 缺失或回退 |
| 普通文件但无执行位 | 缺失 | **存在，假阳性** | 缺失或回退 |

2026-08-01 OnePlus 8T 首轮九次测量：

- PRoot：p50 `106ms`，p95 `107ms`，零探针失败；
- Android 文件证明：p50 `8966us`，p95 `22569us`；
- p50 减少约 `91.5%`，但无执行位文件形成一个确定的假阳性；
- 无 ANR/FATAL，正式生产代码和路由未改变。

结论是“共同调用面和收益成立，当前正确性不成立”。RF1520 允许进入 RF1530 修复通用文件证明，但不能据此跳过 PRoot。

## RF1530 预先固定的生产候选门

修复后使用同一固定矩阵连续执行三轮，必须同时满足：

- 每轮原生与 PRoot 对四类事实完全一致，假阳性、假阴性和 shell failure 均为零；
- 每轮原生 p95 不高于 `30ms`；
- 每轮 p50 相对 PRoot 至少减少 `50ms` 且至少减少 `50%`；
- 临时事实全部清理，无 ANR/FATAL；
- 未知或不完整身份的自动测试证明仍调用原 PRoot 探针；
- 正式接入不新增 Store，不改变安装登记或页面状态拥有者。

这里固定的是 RF1530 的确认门；RF1520 首轮数字只作为候选发现证据，不冒充预注册的生产验收。

## RF1530 实施与确认

正式实现只扩展既有事实链：

- `ManagedCommandHostFileStamp` 明确携带 `executable`；
- 宿主文件解析用 `readAttributes(NOFOLLOW_LINKS)` 一次读取类型、mtime 和长度，并按 Kite 真实 UID 调用 `Files.isExecutable()`；
- `ResourceManagedCommandNativeProof` 只包装完整默认环境的肯定式身份；
- 既有 `ResourceManagedCommandEvidenceCoordinator` 直接接受该证明，混合请求只把不能证明的资源交给原 PRoot Probe；
- 证明与请求错配、非默认环境、文件缺失、断链或无执行位均失败关闭，不新增 Store。

首个候选包在 OnePlus 8T 上正确性已通过，但原生 p95 为 `32.316ms`，超过预设 `30ms` 门，因此没有放宽阈值。随后把每个文件原先分散的 `exists/isFile/mtime/length` 查询合并为一次 `readAttributes`，重新构建、安装并从第一轮开始计数：

| 确认轮次 | PRoot p50 | 原生 p50 | 原生 p95 | p50 减少 |
| --- | ---: | ---: | ---: | ---: |
| 1 | 105ms | 10.683ms | 14.210ms | 89.8% |
| 2 | 106ms | 10.394ms | 15.194ms | 90.2% |
| 3 | 105ms | 11.930ms | 28.269ms | 88.6% |

三轮的可执行、缺失、断链和无执行位结果均与 PRoot 完全一致；假阳性、假阴性、shell failure、残留、ANR 和 FATAL 均为零。RF1530 的候选门通过，RF1540 仍需验证真实资源打开/获取链和父任务 Full 门。

## RF1540 正式链确认

最终接入保留原有 `reconcileInstalledResources()` 作为唯一入口，并只增加低基数计数观测。协调器本身不依赖 Android 日志，正式 Android 组装层注入日志出口，因此纯 JVM 合同测试和生产观测互不污染。

2026-08-01 在 OnePlus 8T 上覆盖安装最终候选包，从资源目录人工触发已获取的 OpenClaw“打开”：

- 日志为 `resolved total=1, native=1, cached=0, fallback=0`；
- 同一时间窗没有 `resource-installed-state-probe`，即没有创建 PRoot `command -v` 探针；
- 页面进入 OpenClaw Agent 显示面并显示“可以开始新会话”；
- 没有新增 ANR 或 FATAL。

资源“获取”的 `buildInstallPlan()` 与“打开”调用同一 `reconcileInstalledResources()`，由生产代码审查和 JVM 回归共同覆盖。本轮没有为了验收点击一个未获取资源并改变用户安装状态，因此不把它描述成第二次真机安装证据。

父任务 Full 为 279 suites、1473 tests、0 failure、0 error、2 skipped；最终结论为 go。该能力只减少受管命令首次正向核对的约 90ms PRoot 固定成本，不等价于把整个 OpenClaw 启动时间缩短 90%。
