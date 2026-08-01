# Kite 混合运行底座决策

## ADR-RF-001 三种 Provider 共用一套路由地基

- 状态：已接受
- 日期：2026-07-31
- 决定：原生能力、通用依赖快速通道和 Ubuntu/PRoot 不是三套独立入口；统一由结构化请求和 Runtime Planner 在执行前选择。
- 原因：避免资源、页面、终端、Agent 和后台服务分别复制路由条件与状态。
- 影响：通道选择依据只能是解释器、ABI、环境、能力和语义要求，不能是应用名或资源 ID。

## ADR-RF-002 Node 是既有基线，不重新实验

- 状态：已接受
- 日期：2026-07-31
- 决定：现有 Node 快速通道作为第一个标准 Provider 样板；RF210 只做合同适配和等价性护栏。
- 原因：Node 的正确性、性能和真机链路已经完成阶段验证，重复执行全部矩阵没有新增价值。
- 触发重验：Node/rootfs/兼容层代次变化、路由行为变化、旧证据前提失效或出现新的真实兼容样本。

## ADR-RF-003 Python 是首个新增快速通道 Provider

- 状态：已接受，RF240 已按分层 go/no-go 接入
- 日期：2026-07-31
- 决定：RF240 只实现纯 Python 结构化命令；subprocess 与 venv 子解释器在启动前回 PRoot，pip/wheel 生命周期和第三方 C 扩展继续由 RF250 分层开放。
- 原因：Python 通用价值高，但轻量与重型负载、纯代码与原生扩展的兼容边界差异很大，不能一次性宣称支持。
- 证据：RF230 基线 Host p50 降低 37.1%～86.6%；RF240 通用资产生产复验的 40 个通道测点零失败，固定发布门降低 45.0%～85.3%。生产 Provider 真机直接执行成功，兼容探针仍证明 Host 子进程边界失败而 PRoot 成功。

## ADR-RF-004 原生 Provider 首个样板为下载与摘要

- 状态：已接受
- 日期：2026-07-31
- 决定：优先用 Android 网络和加密 API 表达下载、SHA-256、取消和结果，不先用解包作为原生样板。
- 原因：能力通用、边界可结构化、容易与 PRoot 对照；解包涉及链接、权限和路径穿越，适合作为后续独立能力。

## ADR-RF-005 PRoot 优化按任务类别准入

- 状态：已接受
- 日期：2026-07-31
- 决定：PRoot 始终是完整 Linux 兼容底座；温热 Runner 只扩展到声明完整、无交互且 STARTED 后不得重放的任务类别。
- 原因：预启动复用可以减少固定成本，但不能改变副作用、信号、终端和长期服务语义。

## ADR-RF-006 文档与发布边界

- 状态：已接受
- 日期：2026-07-31
- 决定：实验分支本地跟踪正式架构和任务文档；是否推送文档或合并进 GitHub main 仍是独立发布决定。
- 原因：用户此前要求 GitHub 发布只包含代码，本任务不能擅自改变外部发布边界。

## ADR-RF-007 Provider 请求不拥有运行实例

- 状态：已接受
- 日期：2026-07-31
- 决定：`RuntimeExecutionRequest` 只表达选择和执行所需事实；instance、run、owner 和显示面身份继续由 Orchestrator、`CardRunStore` 或后台 Registry 持有。
- 原因：Provider 需要可复用于终端、Agent 和后台运行，但不能因此复制状态、接管生命周期或产生新的事实源。

## ADR-RF-008 Python Host 必须使用肯定式安全保证

- 状态：已接受，RF251 已落地请求与 Provider 门
- 日期：2026-07-31
- 决定：Python 请求只有同时声明 `NO_CHILD_PROCESS` 与 `VERIFIED_NATIVE_IMPORTS` 才能进入 Host；空声明表示未知并回退 PRoot。
- 原因：Host 的 Linux 绝对路径可能命中 Android 同名程序并表面成功，无法依靠退出码或运行后回退识别语义漂移。
- 影响：RF252 已通过稳定 wire enum 将保证从资源 Agent、后台依赖、自定义登记和持久化记录透传到统一请求；不得从应用名、脚本名、包名或资源 ID 推断保证。

## ADR-RF-009 Python 原生导入绑定 ABI，包升级使用不可变代次

- 状态：已接受，RF254 已落地
- 日期：2026-07-31
- 决定：声明 `verified_native_imports` 的 Python 请求还必须携带精确 `pythonAbi` 证据；第三方包安装到新代次目录，验证后由调用方选择新目录，不在当前目录执行原地覆盖。
- 原因：扩展后缀和解释器 ABI 必须一致；真机反例证明 `pip --target --upgrade` 会留下旧 `.dist-info`，产生代码与元数据版本分裂。
- 影响：缺少或不匹配 ABI 的请求在启动前回 PRoot，未知证据字段失败关闭；不建立包名白名单，旧代次可在无运行租约后回收。

## ADR-RF-010 原生下载第一版不做推测式续传

- 状态：已接受，RF310a 已落地
- 日期：2026-07-31
- 决定：`network.download_sha256` 每次尝试都从新的同目录临时文件开始，不发送 Range；只有将来能同时验证临时文件身份、服务端 Range 能力和 206 区间时才另开续传合同。
- 原因：把普通 200 响应追加到旧文件会静默损坏内容；当前优先保证摘要、原子发布、取消和失败清理的确定性。
- 影响：意外 206 失败关闭；重试会重新下载，但不污染正式目标。Android 网络连接不设置公共 DNS、不绕过系统 VPN 或证书验证。

## ADR-RF-011 资源先原生预取，活动安装根仍由 PRoot 事务修改

- 状态：已接受，RF310 已完成
- 日期：2026-07-31
- 决定：可静态验证的资源下载先进入资源缓存；下载成功后，既有 PRoot 安装脚本再获取资源锁，并在原备份/验证/提交/回滚事务内把缓存移入安装根。
- 原因：跨 Recipe 步骤长期持锁会扩大失败恢复面；下载本身不需要修改活动安装根，放到锁前还能缩短资源写锁占用时间。
- 安全门：仅提升单一静态 HTTPS URL、安全相对目标和显式 `maxBytes` 的前置下载。动态 URL、多镜像、无尺寸上限或依赖前置 shell 变量的下载保持 PRoot，不猜测参数。
- 影响：同一 `CardRun` 会按步骤记录 `android_native -> proot_shell`；运行通道变化不复制资源安装状态，也不改变更新锁和备份所有者。
- 验收：OnePlus 8T 固定样本原生下载 p50 较 PRoot 降低 18.3%；中途断流从空临时文件重试，失败、空间不足、摘要不符和取消均不污染正式目标。该性能数据不外推为所有下载承诺。

## ADR-RF-012 原生文件权限按根声明，活动安装根不隐式授权

- 状态：已接受，RF320a 已落地
- 日期：2026-07-31
- 决定：复制、移动和删除分别作为结构化能力；每个容器根显式声明读、创建、替换和删除权限，更具体根优先。移动只接受原子实现，删除只处理授权根内的普通单文件。
- 原因：仅限制在 `/workspace` 仍会让任意 Recipe 获得过宽的破坏能力；按根分权可以开放缓存加速，同时阻止删除用户工作区或绕过资源事务。
- 影响：RF320b 的生产上下文必须把 `REMOVE` 限制在缓存等临时根。活动资源安装根不因路径位于工作区而获得删除或移动授权，RF320c 不得重新接入旧 View 事务协调器。
- 验收：OnePlus 8T 固定 16 MiB 对照的原生复制 p50 为 56 ms，PRoot 为 355 ms；同一 Run 的 copy→move→delete 完成且无终端。资源审计未找到脱离活动安装事务的安全迁移点，因此没有为当前清单强行增加原生步骤。

## ADR-RF-013 安全归档第一版只开放普通 ZIP

- 状态：已接受，RF330 已完成；性能路由 no-go
- 日期：2026-07-31
- 决定：原生归档第一版只接受普通文件和目录 ZIP，并通过中央目录识别 Unix 类型；tar/tar.gz、链接、特殊文件和 Linux 元数据保真继续 PRoot。
- 原因：ZIP 的封闭子集可以完整约束路径、数量、尺寸和膨胀比；把完整 Linux 归档语义一起原生化会扩大路径逃逸、链接和权限漂移风险。
- 影响：资源不能仅凭扩展名进入原生车道。OnePlus 8T 固定多文件负载中原生 p50 为 2026 ms，PRoot 为 669 ms，因此自动资源编译器不生成原生归档步骤；OpenCode 当前 tar.gz 合同保持不变。显式安全调用仍可使用该能力。

## ADR-RF-014 系统能力目录只描述真实入口与所有者

- 状态：已接受，RF340a 已落地
- 日期：2026-07-31
- 决定：复用既有 `CapabilityCatalog` 登记调用形态、权限门、结果拥有者、完成语义和回退边界；目录本身不执行能力或复制状态。
- 原因：网络对齐、权限快照和安装器交接已有不同拥有者，强行统一成第二套运行状态会制造冲突；名称存在也不能证明能力实现。
- 影响：APK 只宣称外部安装器交接，不宣称安装完成；当前没有 Keystore 条目。后续新增能力必须先有真实生产入口和结果合同，再进入目录。

## ADR-RF-015 能力目录是执行前白名单，不是第二个执行层

- 状态：已接受，RF340b 已落地
- 日期：2026-07-31
- 决定：`native_capability` 必须先命中 `NATIVE_RECIPE` 目录项；既有 Android action 只借目录取得稳定能力 ID，随后仍调用原执行入口。
- 原因：目录若复制执行逻辑或运行状态，会与现有 Provider、系统 Intent 和 `CardRunStore` 形成双事实源；目录若只用于展示，则未知能力仍可能绕过准入。
- 影响：未知或调用形态不符的能力在取得运行所有权前失败关闭；APK 系统交接把 lane/能力 ID 写回同一 Run，但不创建新的进程、终端、Store 或安装完成事实。

## ADR-RF-016 PRoot Provider 只拥有逻辑计划

- 状态：已接受，RF410 已完成
- 日期：2026-08-01
- 决定：最终 PRoot Provider 归一 payload、工作目录、环境、PTY、View 与选择原因；物理启动配置继续由既有 `KFContainerManager` 在执行入口生成。
- 原因：把 PRoot argv、bind、网络和遥测复制进新 Provider 会形成第二套兼容实现；只返回字符串 `Fallback` 又不足以约束各入口使用同一计划。
- 影响：正式终端、资源 shell、Agent 与后台入口把逻辑计划交给现有物理构造器，不能重写 rootfs 或 View 规则；Android 原生能力在此边界失败关闭。Host `Unsupported` 变成明确 `Proot` 计划，`Blocked` 不得换道。

## ADR-RF-017 PRoot 准入按完整任务合同逐类开放

- 状态：已接受，RF420 已完成
- 日期：2026-08-01
- 决定：进入 `ProotJobAdmissionController` 的调用方必须显式声明 job、owner、lane、读写属性、取消出口和结果出口；没有完整生命周期证据的 PRoot 类别保持独立兼容路径。
- 原因：只包住进程创建不能代表长期服务的真实占用，按命令字符串猜只读/写属性也会破坏副作用和公平性；“所有 PRoot 都统一调度”目前不是事实。
- 影响：当前正式准入仅覆盖固定资源采样。普通终端、任意 shell、Agent、detached 与长期服务以后逐类迁移；控制器只管开始顺序，不接管业务结果或强杀已开始任务。

## ADR-RF-018 温热 Runner 只接收代码所有的有界 argv 任务

- 状态：已接受，RF430 已完成
- 日期：2026-08-01
- 决定：通用短任务执行器只接受结构化 argv 和完整准入身份，并为等待、运行、stdout、stderr 设置硬上限；shell 文本、交互会话和无边界长期进程不进入该入口。
- 原因：结构化 argv 只能解决输入边界，不能自动证明副作用；因此仍由代码 owner 声明读写属性，pool 只允许 STARTED 前回退，不能把“温热进程可用”误写成任意命令可重放。
- 影响：固定资源采样先复用该入口；首个新增类别限定为内部容器进程表只读查询。Git 只可用于性能样本，产品名、资源 ID 和 executable 名称都不是生产路由条件。

## ADR-RF-019 PRoot 生产性能档封顶四并保持单一参数源

- 状态：已接受，RF440 已完成
- 日期：2026-08-01
- 决定：低负载、均衡、高性能的准入/温热上限固定为 1/2/4，空闲回收为 2/30/120 秒；CUSTOM 从 lane 配置推导，但生产同样封顶 4。准入与 pool 只能消费 `ProotPerformanceTunings`。
- 原因：OnePlus 8T 的 8 并发虽提高总吞吐，但温热 P95 从 114 ms 上升到 130 ms；继续扩大默认并发不能保证用户感知更快。重复映射还会令 CUSTOM 准入和实际池容量漂移。
- 影响：8 只保留 debug 校准覆盖。压力变化可继续收缩有效上限，但不改变配置档，也不强杀已开始任务；当前继续采用 1/2/4，均衡档空闲 30 秒后回收，后续若要提高生产上限，必须重新提供目标设备 P95、内存、失败率和 ANR 证据。

## ADR-RF-020 规划态与实际 PRoot 调度态分开投影

- 状态：已接受，RF510 已完成
- 日期：2026-08-01
- 决定：`RuntimeProotPoolPlanDryRun` 继续表达规划/建议；实际 admission、queue 和 warm session 只从 `WarmProotExecutionCoordinator` 即时投影为 `proot_actual_*`，不建立第二份 Store。
- 原因：规划出来的 slots、压力建议和容量动作不证明真实任务已准入或 Runner 已创建；把二者混在同一字段会导致性能诊断和自动策略读取错误事实。
- 影响：正式健康输出可以同时比较 planned 与 actual。actual 只允许低基数枚举和数字，不记录任务身份、命令、路径、环境或输出，诊断读取也不能创建 PRoot。

## ADR-RF-021 冷启动只做可被正式健康面覆盖的策略接力

- 状态：已接受，RF520 已完成
- 日期：2026-08-01
- 决定：第一份 RuntimeHealth 到达前，从现有策略文件和 host `MemAvailable` 解析一次 bootstrap policy；正常内存使用所选档位，信号缺失或高压保持单并发。正式快照无条件接管，迟到的 bootstrap 不得覆盖它。
- 原因：默认 `UNKNOWN` 虽安全，却把正常设备的均衡和高性能档都错误压成 1；另建 bootstrap Store 或独立控制器又会制造双事实源与竞态。
- 影响：coordinator 仍是唯一实际策略持有者，admission 仍是唯一准入原语。策略来源显式投影为 `initial_conservative`、`bootstrap_policy_files_host_memory` 或 `runtime_health`；bootstrap 不创建 PRoot，不改变普通终端、Agent、任意 shell 和长期服务路径。

## ADR-RF-022 有界任务遥测只聚合固定执行事实

- 状态：已接受，RF530 已完成
- 日期：2026-08-01
- 决定：有界任务按 lane/route/result 固定枚举聚合完成次数，并记录 queue/execute/total 的固定时延桶、sum 和 max；只在 `BoundedProotTaskExecutor` 完成一次实际尝试后写入。
- 原因：admission 的 admitted/timedOut 总量无法说明任务最终走 warm 还是 fallback，也无法区分慢在排队或执行；记录 job、owner 或命令又会造成高基数、隐私和健康输出膨胀。
- 影响：RuntimeHealth 可直接读取内存快照且不触发任务或扫描。生产 collector 没有 reset 入口、键空间由 enum 上限固定；普通 PRoot 路径不自动纳入，也不能拿遥测存在当作准入证据。

## ADR-RF-023 复杂内部采集先固化 helper 再进入结构化 Runner

- 状态：已接受，RF540 已完成
- 日期：2026-08-01
- 决定：`supervisorctl update/status` 与固定日志尾部由 Android 生成的无参数、版本化 helper 持有；调用方只执行唯一 helper argv，并因 `update` 明确声明 `SERVICE/SHARED_WRITE`。
- 原因：把多行 shell 直接放进 `argv` 只是伪结构化；删除 `update` 又会改变 dropped-in 配置发现语义。代码自有 helper 能保留 Linux 组合语义，同时封闭外部命令、参数和路径输入。
- 影响：Supervisord 健康刷新进入统一 admission、warm/fallback 和 RF530 遥测；输出有硬上限，截断失败关闭。当前 OnePlus 环境缺失 Supervisord 仍真实返回 exit 127，不属于迁移失败，也不能由本阶段偷偷安装依赖。

## ADR-RF-024 RF600 只开放 owner lease 合同与模拟器

- 状态：已接受，RF550 已完成
- 日期：2026-08-01
- 决定：RF600 可以研究并实现不创建进程的长期 owner lease 状态机、模拟器和观测合同；禁止直接把短任务 admission lease 套到后台服务、终端或 Agent，也禁止在合同阶段接入生产 Store。
- 原因：短任务 lease 由调用栈 `use {}` 释放，长期进程却可能在调用返回后继续运行、被重连、外部死亡或跨进程恢复；直接复用会提前释放容量或形成永不释放的假占用。
- 影响：未来迁移必须逐类别证明唯一 owner、真实进程寿命、停止确认、崩溃回收、恢复和重复 attach。RF600 的完成不等于任何长期入口已受统一 admission 管理。

## ADR-RF-025 长期 owner 不复用短任务写锁与调用栈 lease

- 状态：已接受，RF610 已完成
- 日期：2026-08-01
- 决定：长期 owner 使用独立的 owner lease phase 和文件系统姿态；容量从准入持续到停止或死亡确认。进程身份同时包含 host PID 与启动代次，孤儿协调保留失联前的运行或停止意图。
- 原因：常驻进程若持有短任务 `SHARED_WRITE`，会永久阻塞其他写任务；若随启动调用返回释放 lease，又会让真实长期占用从调度面消失。仅按 PID 恢复还会误认系统复用后的新进程。
- 影响：RF610 只提供纯状态转换，不接生产 Store 或进程。未来调度器必须把 `EXCLUSIVE_MAINTENANCE` 实现为准入屏障，而不是让所有共享长期 owner 永久占据同一调用期互斥锁；停止中的 owner 失联后重新确认存活仍保持停止意图。

## ADR-RF-026 长期维护使用队列屏障而非永久共享写锁

- 状态：已接受，RF620 已完成
- 日期：2026-08-01
- 决定：长期共享 owner 只占容量和 lane，不持有短任务写锁；`EXCLUSIVE_MAINTENANCE` 入队后阻止其 sequence 之后的新共享准入，等待屏障前 holder 排空后独占运行。被当前压力策略阻断的维护不建立屏障。
- 原因：长期共享进程若持有调用期写锁，普通任务会永久饥饿；若维护没有屏障，持续到来的高优先级共享 owner 又会让维护永远无法获得空窗。压力已阻断的维护若仍建屏障，还会反向堵住救援所需的必要任务。
- 影响：压力变化只影响后续准入，不撤销既有 lease。重复 owner 只能复用完全相同的 spec，不能借重连修改 lane、文件系统姿态或必要性；生产接入仍需 RF630 恢复合同和独立类别迁移门。

## ADR-RF-027 恢复只认精确进程代次且停止意图优先

- 状态：已接受，RF630 已完成
- 日期：2026-08-01
- 决定：长期 owner 恢复用 `(hostPid, processStartTicks)` 作为最小进程身份；只在二者精确匹配时重连。同 PID 不同代次、未发现进程或身份不完整都不得启动替代进程。恢复开始前已有停止意图时，最终状态必须保持停止或孤儿停止审查。
- 原因：Android/Linux 会复用 PID，仅按数字重连可能把无关新进程绑定到旧 owner；把“未发现”直接当死亡释放容量，又会在观察窗口或控制面重启时产生第二实例。恢复与用户停止并发时，运行优先会违背明确的停止请求。
- 影响：未找到的已附着 owner 继续占容量直到死亡确认；`STARTING` 且未持久化身份的记录保持 review。持久化批次按 owner/generation 去重，同代次冲突不自动选择生产语义；多个 owner 声称同一进程代次时全部进入冲突审查，禁止双重绑定。该规划器不读取 `/proc`，未来每类生产 owner 必须提供可信观察与停止确认。

## ADR-RF-028 长期 owner 规划态使用独立固定健康 schema

- 状态：已接受，RF640 已完成
- 日期：2026-08-01
- 决定：RF600 的诊断字段统一使用 `proot_long_planned_*` 前缀和 `planned_not_production` scope，只从调用方传入的不可变 admission snapshot/recovery plan 计算固定枚举计数，不接入正式 RuntimeHealth。
- 原因：把模拟结果写入 `proot_actual_*` 会让尚未接入生产的长期 owner 冒充真实调度事实；按 owner 动态生成字段又会泄漏身份并造成无界基数。投影时回读模拟器或扫描进程也会让诊断读取产生副作用。
- 影响：ownerId、leaseId、PID/启动代次、路径、命令和 Agent/session 身份永不进入该 schema。未来生产迁移后，actual 字段必须来自唯一生产状态拥有者并另立迁移门，不能把 planned 字段简单改名。

## ADR-RF-029 后台长期 owner 生产迁移等待强进程身份桥接

- 状态：已接受，RF650 已完成
- 日期：2026-08-01
- 决定：RF600 合同与模拟器完成，但后台服务、终端、Agent 均不接入生产 long-lived admission。后台服务只允许继续做强身份桥接准备；必须先把 PID 与 `/proc/<pid>/stat` 启动代次绑定到真实 runtime owner，并证明停止确认和应用重启恢复。
- 原因：当前 `BackgroundRuntimeRecord` 只持久化 PID，`HostProcessRecord` 不含 start ticks，命令/token 匹配不能排除 PID 复用和跨 owner 误认。此时接 lease 会在控制面重启后出现提前释放、永久占用或第二实例风险。
- 影响：RF600 的 `LongLivedProot*` 代码保持无生产引用。下一次后台试迁必须先有 owner→强身份→停止确认的单向适配和真机证据；终端与 Agent 另立类别门，不得借后台桥接顺带迁移。

## ADR-RF-030 RF700 复用既有校准体系并对齐正式档位

- 状态：已接受，RF650 已完成
- 日期：2026-08-01
- 决定：RF700 不新建平行设备校准器；先审计既有 `RuntimeProotDeviceCalibrationDryRun`、overlay、RuntimeHealth 与 automation 路径，再把可信结果映射为 RF400 的 1/2/4 候选建议。生产 coordinator 仍是唯一实际档位拥有者。
- 原因：仓库已有较完整的 tracee/内存校准模型，但历史 profile limit 与当前 `ProotPerformanceTunings` 不完全一致。直接新增实现会形成第二套事实源，直接套用又可能把 tracee 容量错当任务并发。
- 影响：未知 thermal、旧 schema、缺实测上界或信号冲突时失败关闭；在 RF750 前只允许 planned 建议，不改正式策略文件、不自动升档。

## ADR-RF-031 tracee 校准只作安全 guard 不直接选择任务并发档

- 状态：已接受，RF710 已完成
- 日期：2026-08-01
- 决定：P0 overlay 只有 schema、显式 valid、当前 calibration method、applied time、实测上界和 tracee 数关系全部可信时，才能作为后续自适应的设备安全 guard；即使通过，也不能直接选择 LOW/DEFAULT/HIGH。正式档位数字始终从 `ProotPerformanceTunings` 派生为 1/2/4。
- 原因：P0 测量的是单 PRoot 内标准 worker 的 tracee 吞吐峰值，不是有界任务在多个 warm runner 间的并发收益。旧 profileLimits 是已废弃分段，脚本当前也明确不再生成；把 tracee 数当任务并发会跨越没有证据的模型边界。
- 影响：overlay loader 缺 schema/valid 时失败关闭；历史 profileLimits 只保留兼容读取，不进入新对齐结果和档位显示。RF720 的升降级必须使用实际任务遥测、内存和前后台证据，thermal 不可信时不得升档。

## ADR-RF-032 自适应只消费可验证差量窗口且升档证据更严格

- 状态：已接受，RF720 已完成
- 日期：2026-08-01
- 决定：自适应评估只接受正式 RuntimeHealth policy source、RF530 累计遥测的单调差量窗口、内存/前后台、RF710 guard 与显式 thermal evidence。高压、可信过热或显著失败可以建议降一级；升档必须同时满足可信 normal thermal、足量低失败样本、P95、前台与 guard，且只标记一个健康窗口。
- 原因：累计计数不能直接代表当前窗口，单个好样本也不能证明更高并发稳定；另一方面，高内存压力是无需等待任务样本即可收缩后续准入的安全事实。当前代码没有可靠 thermal 来源，若把 unavailable 当 normal 会让预研合同伪造生产能力。
- 影响：RF720 永不修改 coordinator，所有结果都标记 `planned_not_production` 和 `changesCoordinator=false`。未知、陈旧、计数回退、桶矛盾、CUSTOM 档或非正式 source 均失败关闭；RF730 只能在这些窗口结果之上增加迟滞，不能降低本门要求。

## ADR-RF-033 自适应迟滞不拥有 actual 且安全降档不受冷却阻断

- 状态：已接受，RF730 已完成
- 日期：2026-08-01
- 决定：迟滞状态只保存窗口 streak、待应用相邻目标、冷却和 rollback target；每次推进都由调用方提供 actual 1/2/4。升档需三个连续 RF720 健康窗口，失败率降档需两个连续坏窗口，内存/thermal 紧急压力可直接建议降一级并绕过升档冷却。
- 原因：把候选档写进独立 Store 会复制 coordinator 事实；让单个好样本升档会抖动，而让冷却压住高压降档又会把性能稳定性置于设备存活之前。重启时旧 pending/streak 也不能证明当前 actual 已按同一路径变化。
- 影响：建议发出后等待外部 actual 确认，不重复发出；actual 变化、状态损坏或输入不相邻时 reset/rebase 并重新冷却。RF740 只能投影这份规划状态和 RF510 actual 的差异，正式应用仍为 no-go。

## ADR-RF-034 planned 只能镜像 actual 引用且必须显式声明未生效

- 状态：已接受，RF740 已完成
- 日期：2026-08-01
- 决定：自适应诊断同时输出 RF510 actual reference 和 RF730 planned suggestion，但二者使用不同 scope；planned 固定声明 `changesCoordinator=false` 和 recommendation 非 actual policy。投影只接收不可变参数，不主动读取任何正式状态源。
- 原因：若建议字段与 actual 共用命名或在投影时回读 coordinator，会让观察面看起来像策略已经切换，并可能因读取创建 pool 或产生时序不一致。动态 owner/进程字段还会扩大基数并泄漏运行身份。
- 影响：合同矛盾时 relation 固定为 `CONTRACT_MISMATCH`，planned target 回到 actual 且 recommendation 清空。该 schema 尚未接入正式 RuntimeHealth；RF750 只能在证明信号来源与应用事务后另给生产结论，不能用“能输出文本”代替生产接线。

## ADR-RF-035 RF700 不新增自动调档生产状态

- 状态：已接受，RF750 已完成
- 日期：2026-08-01
- 决定：RF700 以规划合同结束，不接生产 Store、定时器或 coordinator override。HIGH/CRITICAL 内存压力继续由现有 admission 的 effective max 自动收缩；失败率调档与自动升档均保持 no-go。
- 原因：内存压力与收缩准入存在直接安全关系，且生产机制已经生效；任务失败可能来自命令、依赖、网络或环境，尚不能归因于并发。仓库也没有可靠 thermal source，无法满足升档门。再建自适应 override 会复制正式档位事实并可能形成抖动。
- 影响：RF710～RF740 代码只作为纯评估/模拟/投影合同保留，不接 `RuntimeHealthStore`。下一优化阶段转向 RF650 已证明的后台强身份桥接，避免为了“继续优化”重复实现已有内存收缩。

## ADR-RF-036 后台持久强身份包含 boot identity 且停止意图先落盘

- 状态：已接受，RF810 已完成
- 日期：2026-08-01
- 决定：后台记录的持久强身份采用 `(bootId, hostPid, processStartTicks)`；只有 boot 一致后才向 RF610 长期 lease 提供 PID+代次。停止必须先持久化 expected stop，再校验同一强身份、发信号并观察退出，最后确认 STOPPED 和释放容量。
- 原因：PID 会复用，start ticks 也会在设备 reboot 后重新从 boot 起点计数；仅靠 command token/statusCommand 只能证明类似进程或服务存在。当前停止链无论 terminate outcome 都写 STOPPED，且 expected stop 落盘晚于信号，无法承担长期容量释放语义。
- 影响：RF820 先补通用 Host 观察与 Registry 持久化，不改变 attach/kill；RF830 才允许调整停止和恢复。旧 JSON、`ps -A` fallback 或任一身份字段缺失都只可继续现有诊断，不得进入长期 lease。

## ADR-RF-037 强身份由同轮 Host snapshot 生成且随 PID 原子失效

- 状态：已接受，RF820 已完成
- 日期：2026-08-01
- 决定：`HostProcessSnapshot` 只有在 boot UUID、应用 UID 进程和 stat starttime 同时可用时才生成 `HostProcessIdentityObservation`。后台记录追加 boot/start ticks；PID 改变或清空时同步清身份，Registry 只对活动且 PID 精确匹配的记录原子写入。
- 原因：分别读取或只校验 PID 会产生 TOCTOU/复用窗口；让旧 start ticks 跟随新 PID 会制造看似完整的假身份。`ps -A` fallback 和旧 JSON 都缺少足够证据，不能为了覆盖率填默认值。
- 影响：现有 PID/token/statusCommand 链仍按原行为运行，但不能借 RF820 宣称已强恢复。RF830 必须用观察值与持久值精确比较，并把停止意图和退出确认顺序修正后，才可考虑长期 lease。

## ADR-RF-038 后台恢复和停止必须共用同一强身份判定

- 状态：已接受，RF831 已完成
- 日期：2026-08-01
- 决定：恢复 attach 与停止 signal 共用 `BackgroundRuntimeProcessIdentityPolicy`。只有 boot、PID 和 start ticks 精确一致才可操作现存进程；纯决策固定 `processStartsRequested=0`。同 PID 新代次或不同 boot 只能证明原代次不再存在，绝不向当前 PID 发信号；任一身份字段不可得则进入 review。
- 原因：若恢复和停止各自解释 PID，可能出现恢复拒绝新代次、停止却仍按弱 token 杀死它的矛盾。把 statusCommand、端口或 command token 当身份也无法排除 PID 复用。
- 影响：RF832/833 必须消费该合同，不能在 Host 内重新复制比较分支。RF831 本身没有生产装配；旧 PID 记录仍需 RF832 明确兼容显示和 no-attach 行为。

## ADR-RF-039 本地 handle 与重启恢复使用不同归属证据但同一进程身份

- 状态：已接受，RF832 已完成
- 日期：2026-08-01
- 决定：`ProcessBuilder.start()` 返回的本地 handle 是创建归属证据，身份捕获只需目标 PID 属于应用 UID并具备 boot/start ticks；应用重启后 handle 消失，外部 attach 必须同时满足 RF831 精确身份和既有 container/owner token 门。两者都不得由 statusCommand 或健康端点补全 PID。
- 原因：创建后为取一个 start ticks 扫描整棵 `/proc` 会给高频启动增加无谓成本；重启后只看命令 token 又无法排除 PID 复用。健康成功代表服务可响应，不代表持久 owner PID 是同一进程代次。
- 影响：新建/复用本地 handle 会原子补强身份；旧记录仍可显示服务健康，但没有强身份就不 attach 且 PID 会被清理。RF833 可据此安全决定是否发停止信号，不能回退到弱 PID 补偿。

## ADR-RF-040 显式停止意图优先且终态必须由退出证据确认

- 状态：已接受，RF833 已完成
- 日期：2026-08-01
- 决定：后台 PROCESS 的显式停止先写现有 `lastStopReconciliation*`，再操作本地 handle 或强身份命中的 detached PID；只有原代次确认退出才写 STOPPED、清身份和释放容量。活动刷新必须保留同 PID 的 expected stop，且 expected stop 优先于 core 自动恢复。
- 原因：先发信号再落盘会在应用死亡时丢失意图；只看 terminate 返回日志后无条件写 STOPPED 会释放仍在运行的 owner 容量；core 恢复若压过显式停止则会造成“停止后又拉起”。
- 影响：身份不完整和观察不可用显示 review 而不是伪终态；普通 stale reconciler 不再处置带强身份的后台记录。detached 信号仍受无 pidfd 的最终 TOCTOU 限制，RF840 不能把用户态重验描述成内核原子身份持有。

## ADR-RF-041 PRoot 后台停止是 owner 树事务

- 状态：已接受，RF834 已完成
- 日期：2026-08-01
- 决定：后台 PROCESS 的实际车道为 `proot_shell` 时，必须先由既有 `ProotOwnerProcessTerminator` 按 runtimeId 收敛完整 owner 树；只有 owner 结果 settled 且本地 wrapper 已退出才确认 STOPPED。终止失败时保留 expected stop 和活动身份，不补杀单个 wrapper。
- 原因：真机反例证明 wrapper 根 PID 退出后，PRoot 内的 shell 与 sleep 子进程仍会存活，并可因继承日志管道阻塞 monitor；根 PID 消失不能代表业务和 owner 容量已经退出。
- 影响：Host 通道继续使用本地 handle 或强身份 detached 终止路径；PRoot 路径只按实际车道和通用 owner identity 选择，不识别资源 ID、命令名或 runtime kind。RF840 释放长期 lease 必须消费同一 owner 树 settled 证据。

## ADR-RF-042 后台长期 lease 不在双容量事实源下生产接入

- 状态：已接受，RF840 已完成
- 日期：2026-08-01
- 决定：RF840 保持生产 no-go。不能把 `LongLivedProotAdmissionSimulator` 直接实例化后接到后台，也不能仅从 RUNNING 记录反推长期容量；必须先让短任务和长期 owner 共用一个实际容量仲裁器，并在 `BackgroundRuntimeRecord` 内持久化进程创建前的 provisional lease generation/phase。
- 原因：当前短任务 actual controller 和长期 planned simulator 彼此不计数，直接并行会超售 1/2/4 档；后台现有状态也不能表达“已占容量、尚无 PID”的 STARTING 窗口。两者都会在并发启动或控制面重启时产生假空闲、重复准入或第二实例。
- 影响：RF834 的强身份与 owner 树停止继续作为必要地基，但不等于容量生产接入完成。解阻任务固定为 RF910～RF950；实际健康字段必须来自统一仲裁器，后台身份仍由 `BackgroundRuntimeRegistry` 唯一持有，终端和 Agent 不借机迁移。

## ADR-RF-043 统一容量先固定无副作用快照再改生产仲裁

- 状态：已接受，RF910 已完成
- 日期：2026-08-01
- 决定：先从现有短任务 controller 同锁投影逐 lane actual 计数，再以纯函数合并长期 lease phase；RF910 不实例化第二个 controller、不接后台、不读取 Store 或进程。统一快照 scope 固定为 `unified_contract_not_production`。
- 原因：直接改生产准入会同时跨越短任务公平队列、长期 provisional lease 持久化和重启恢复三个风险面，难以证明总容量没有超售。先固定计数和失败关闭不变量，可以让 RF920/930 复用同一事实，而不会把规划结果冒充 actual。
- 影响：ADMITTED 到 ORPHAN_REVIEW 均持有容量；REQUESTED 只排队，RELEASED 忽略。压力缩档可出现 OVERCOMMITTED，但不得强杀既有任务；重复 owner/进程身份、lane sum 不一致和独占冲突均阻止新准入。RF920 仍必须在 `BackgroundRuntimeRecord` 内补 provisional generation/phase。

## ADR-RF-044 长期 PRoot 检查点归属后台记录并失败关闭

- 状态：已接受，RF920 已完成
- 日期：2026-08-01
- 决定：长期 PRoot 的 generation、phase-name 和更新时间直接持久化在同一 `BackgroundRuntimeRecord`；Registry 提供原子 `proot_shell + STARTING` begin 和带 expected generation/phase 的转换。内部强类型策略解释字符串 phase，不把内部 lease 枚举扩成公共模型 API。
- 原因：进程创建前没有 PID，若只从 RUNNING/强身份反推容量，控制面重启会把 STARTING 窗口误判为空闲；另建 Store 又会复制 owner、路由和身份事实。把最小检查点写回同一记录，能让将来恢复先看占位、再看进程。
- 影响：旧 JSON 三字段全缺失保持兼容；任意部分字段、未知 phase、路由冲突、旧 generation 或时间倒退都拒绝覆盖。RELEASED 历史保留以保证 generation 单调。RF920 不授权生产调用；RF930 必须由实际统一仲裁结果驱动这些原语，不能由页面、资源 ID、命令名或规划模拟器自行写入。

## ADR-RF-045 后台长期 owner 与短任务共用同一 actual controller

- 状态：已接受，RF930 已完成
- 日期：2026-08-01
- 决定：后台通用 PRoot PROCESS 通过 `WarmProotExecutionCoordinator` 持有与有界短任务相同的 `ProotJobAdmissionController` lease。长期句柄 registry 只保存 owner、generation 和关闭句柄；运行状态、路由、命令和强身份继续只属于 `BackgroundRuntimeRegistry`。实际准入和 STARTING 检查点必须先于唯一进程创建；恢复导入既有 holder，停止只有 owner 树与强身份共同确认后才释放。
- 原因：另建长期 controller 会让 1/2/4 档超售；只从 RUNNING 反推会漏掉 STARTING；进程根 PID 消失也不能证明 PRoot 子树已经退出。共享 actual controller、同记录检查点和既有 owner 终止器分别关闭这三个窗口。
- 影响：压力收缩只阻止新准入，不驱逐恢复 holder。损坏/冲突检查点会在 actual controller 上建立低基数合同阻断。进程创建后在强身份前快速退出进入 ORPHAN_REVIEW，不以 STARTING 或普通 ERROR 冒充已释放。RF940 可以投影实际长期计数和短长总量，但不得复用 `proot_long_planned_*` 名称或迁移终端、Agent。

## ADR-RF-046 actual 健康必须来自 admission 同锁分类而非记录反推

- 状态：已接受，RF940 已完成
- 日期：2026-08-01
- 决定：唯一 admission snapshot 在持锁期间按 `MANAGED_OWNER` 分类长期活动/排队 holder；正式健康面据此输出独立 `proot_long_actual_*` 和 `proot_unified_actual_*`。旧 `proot_actual_active_jobs/queued_jobs` 继续只代表有界短任务，规划态继续使用 `proot_long_planned_*`。
- 原因：分别读取长期句柄表和 admission 会在 acquire/release 窗口出现瞬时错配；扫描 `BackgroundRuntimeRecord` 又会把未实际准入的记录冒充容量。请求取消模式已经是 controller 内的通用生命周期声明，可以在同一原子快照中稳定分类。
- 影响：读取健康面不创建 pool、进程或 Store。合同阻断、计数矛盾和缩档超售分别输出 CONTRACT_MISMATCH/OVERCOMMITTED，不隐藏既有 holder。RF950 只需验证故障矩阵和最终开关边界，不再新增第三份容量事实。

## ADR-RF-047 后台通用 PRoot PROCESS 通过固定矩阵后打开生产门

- 状态：已接受，RF950 已完成
- 日期：2026-08-01
- 决定：后台实际路由为 `proot_shell` 的通用 PROCESS 在进程创建前必须通过单一类别开关、同一 actual admission 与同记录 STARTING 检查点。生产门不识别产品、资源、命令、runtime id 或 Agent。1/2/4、短长竞争、压力收缩、PID/boot 反例、重启、外死、重复启动和 owner 树停止全部通过后，开关固定为 ENABLED，并在正式健康面发布稳定原因 `rf950_matrix_passed`。
- 原因：仅凭目标单测无法证明 Android 进程重启、应用 UID 进程树和真实 RuntimeHealth 接力下仍不超售；反过来，为调试矩阵在生产 controller 上加永久测试锁也会污染正式机制。固定 Debug 服务只等待启动期策略接力稳定，随后使用同一个 controller 做无参数矩阵，生产代码只保留类别门。
- 影响：STOPPED/ERROR 只在没有未释放长期 lease 时才可幂等跳过停止；ORPHAN_REVIEW 即使记录已是终态，也必须继续走 owner settled、强身份终态和 RELEASED。后台通用 PRoot PROCESS 由此正式进入统一容量；终端和 Agent 仍未迁移，未来类别扩展必须另过自己的生命周期与真机门。

## ADR-RF-048 长期 owner 不得占满可并发档位的全部容量

- 状态：已接受，RF1010 已完成
- 日期：2026-08-01
- 决定：effective global max 大于 1 时，新 `MANAGED_OWNER` 最多占 `globalMax-1`；global max 为 1 时仍允许一个长期 owner，不伪造并发。该限制在唯一 admission 的同一把锁内判断，只影响新准入，不驱逐恢复或正在运行的 holder。
- 原因：lane priority 只能排序 waiter，无法抢占已经无限期持有容量的后台进程。让长期 owner 占满 2/4 会使后到交互短任务永久超时；另建短任务 controller 又会重新引入 RF840 已禁止的总量超售。
- 影响：达到长期上限的 waiter 必须让可运行短任务绕行，但共享写任务继续充当队首屏障。低功耗档无法同时满足长期运行和短任务并发，产品只能通过切换均衡/高性能取得余量。健康面后续只输出上限与计数，不暴露业务身份；终端和 Agent 不自动继承该类别。

## ADR-RF-049 短任务余量必须由 actual 同锁事实发布并用真实命令过门

- 状态：已接受，RF1030 已完成
- 日期：2026-08-01
- 决定：长期上限必须由 `ProotJobAdmissionController` 的同锁 snapshot 发布，正式 health 只投影长期上限、剩余长期名额、短任务余量和保护状态。发布 schema 升为 `managed_proot_owner_v2` 与 `shared_proot_capacity_v2`。生产门必须让真实有界短命令占用保留位，并验证排在前面的长期 waiter 不阻塞它。
- 原因：从档位配置或后台记录反推上限，会与实际 policy/压力/恢复 holder 产生时序差异；只构造纯内存 lease 又不能证明 Warm PRoot 执行链真正拿到保留位。低基数同锁事实和真机真实命令分别关闭观测与执行两个证据缺口。
- 影响：低功耗仍明确没有短任务并发余量；均衡/高性能只保护一个非长期位置而不预启动进程。压力缩档可如实显示 overcommitted，不释放既有 holder。类别边界仍仅为 `MANAGED_OWNER`，终端与 Agent 是否迁移必须另立生命周期门。

## ADR-RF-050 终端与 Agent 不按会话存活期占用 managed owner 容量

- 状态：已接受，RF1040 已完成
- 日期：2026-08-01
- 决定：普通终端和 Agent 不能直接标成 `MANAGED_OWNER` 并在整个会话存活期占用 RF1000 的长期名额。二者保持现有生产入口；如继续统一，下一阶段只能先研究跨入口的 PRoot 进程启动窗口协调，启动完成即释放，不接管 PTY、ACP session、重连或业务停止语义。
- 原因：后台 PROCESS 以持续运行和 owner 树停止为容量生命周期；终端/Agent 则可能长时间空闲且用户可同时保留多个会话。把两者当同一 holder 会让均衡档只能保留一个会话、高性能档只能三个，并不能代表 CPU 或 I/O 实际压力。
- 影响：RF1000 的余量收益只对已过门的后台通用 PRoot PROCESS 生效。下一阶段必须先用并发冷启动、P95、失败率和唯一进程证据证明启动协调是否有价值；没有收益则维持现状，不能为了统一而增加排队。

## ADR-RF-051 启动窗口使用调用方 READY 释放而不解释业务协议

- 状态：已接受，RF1110 已完成
- 日期：2026-08-01
- 决定：通用 launch lease 只负责进程创建前准入、等待、取消和超时；实际创建者必须在其已有 READY、明确失败或超时边界释放。`ProcessBuilder.start()` 返回不是通用 READY。协调器不得读取 PTY 内容、ACP 消息、健康端点或命令输出来替调用方判断业务状态。
- 原因：终端、Agent、后台、exec 与 bootstrap 的可用性证据不同；集中解析会把协议和业务特判塞进底层。另一方面，只包围 `start()` 可能在 PRoot 真正加载 rootfs 前就释放，无法改变昂贵阶段的重叠。
- 影响：RF1120 必须同时测无协调、start-return 释放和首个固定 READY 释放。未来若 go，`ProotLaunchPlan` 的 lane/purpose 要正向透传到物理配置；终端仍需补强 READY，Agent 可用 ACP initialize，既有完整任务 admission 不重复叠加。

## ADR-RF-052 PRoot 启动窗口不进入生产

- 状态：已接受，RF1130 已完成
- 日期：2026-08-01
- 决定：不实现 RF1140，不在终端、Agent、后台、Bridge 或通用 config 中接入 launch semaphore/lease。RF1120 Debug 固定矩阵作为可复算诊断保留，但不发布正式健康字段，也不形成第二个生产 admission。
- 原因：两套 OnePlus 8T 矩阵中，READY 窗口收窄稳定恶化 tail 与 batch wall；start-return 只在一套出现偶发 P95 改善，另一套基本追平，失败率本来就是 0，无法证明确定收益。单固定 PRoot READY 只有几十毫秒，也不在真实重型应用几十秒启动的同一量级。
- 影响：不能以“协调器代码容易写”为由生产化。下一阶段只研究通用依赖内部可证明的高频路径成本；任何快速通道仍以依赖 ABI/能力为条件，不识别使用端应用。

## ADR-RF-053 下一个快速通道候选按正式依赖覆盖与能力风险选择 Git

- 状态：已接受，RF1210 已完成
- 日期：2026-08-01
- 决定：在 Node/Python 已完成后，下一候选选择受管 Git，先做 Debug-only Host glibc 兼容与性能矩阵。curl 和 uv 暂不进入；不修改资源卡、Git shim、Planner 或生产入口。
- 原因：正式清单中 Git 被 10 个资源依赖，curl 4、uv 1；Git 本地操作又对小文件路径访问敏感。curl 的静态 HTTPS 高价值范围已有 Android 原生 Provider，uv 的核心语义落在 Python 子进程/venv PRoot 边界。覆盖面必须与可验证能力同时考虑，不能只按工具知名度选择。
- 影响：RF1220 必须把本地 builtin 与 hooks/pager/filter/remote/helper/submodule 分层，不得因 `git --version` 成功就开放。只有同版本同仓库语义一致且收益稳定时，RF1230 才能讨论 Provider；否则 no-go。

## ADR-RF-054 Host Git 必须用输出和仓库状态验收，不能只看进程返回码

- 状态：已接受，RF1220 已完成
- 日期：2026-08-01
- 决定：Host Git 矩阵同时核验 HEAD、status/diff 输出、index 内容和外部脚本 marker；PRoot 作为同二进制、同输入的独立控制组。Host 启动成功或 exit 0 均不构成能力兼容证明。
- 原因：真机 clean filter 反例中，Host `git add` 返回 0，但 `/usr/bin` 子命令没有正确执行，marker 内容错误且写入 index 的内容与 PRoot 不同。alias、hook、external diff、remote helper 和 submodule 也分别证明 Git 会从 builtin 边界进入 shell/helper 子进程。
- 影响：RF1230 不能用运行后失败回退，因为 Git 可能已经修改 index/工作树且错误可能静默；只能在任何 Git 进程创建前证明完整能力边界，否则整条选择 PRoot。任何依赖 subcommand 白名单、仓库配置猜测或忽略 stderr 的方案均为 no-go。

## ADR-RF-055 direct Host Git 不进入生产

- 状态：已接受，RF1230 已完成
- 日期：2026-08-01
- 决定：不实现 `HostGitRuntimeProvider`，不修改 Git shim、资源清单、统一 Planner 或运行 lane。Debug 矩阵保留；本地 builtin 的性能收益不抵消任意 child 和仓库配置语义无法在进程创建前证明的问题。
- 原因：同一 argv 是否触发 hook/filter/helper 取决于仓库和配置，subcommand 白名单不正确，预扫描不完备且有竞态，运行后回退又可能重复副作用。正式资源的十条 Git relation 也不是十条 Git 安装热路径，不能把依赖覆盖数直接当成可兑现的启动收益。
- 影响：所有未显式证明的 Git 继续整条走 PRoot。下一可研究的通用机制是 exec 边界 child relay，让 Host glibc 父进程的外部 child 无损进入 PRoot；它必须独立证明 argv/env/cwd/fd/signal/exit 与唯一进程语义，不能作为 Git 特判偷偷接入。

## ADR-RF-056 child relay 必须作为通用执行边界独立立项

- 状态：已接受，RF1240 已完成
- 日期：2026-08-01
- 决定：RF1200 no-go 后不继续扩大 Git resolver；另立 RF1300，只研究 Host glibc 父进程的通用 exec/spawn child 能否无业务语义地交给既有 PRoot 兼容前缀。Debug 原型必须使用独立资产，不覆盖已冻结的正式 compat 库。
- 原因：Git、Python 和其他 Linux 依赖的共同缺口不是父 ELF 无法启动，而是 child 重新进入 Android 文件系统/ABI 语义。按每个工具补白名单会重复造机制；若能在通用 exec 边界保留 argv/env/cwd/fd/signal/exit，才有跨依赖复用价值。
- 影响：RF1310 先审计所有入口和不可变语义；RF1320 未过门前不修改生产 launcher、compat、Provider、资源或 lane。任何漏拦、双执行、递归套 PRoot、fd/信号变化或静默降级直接 no-go。

## ADR-RF-057 child relay 的正确性包含同步创建错误与进程观察语义

- 状态：已接受，RF1310 已完成
- 日期：2026-08-01
- 决定：child relay 不以“最终命令输出相同”为充分条件。同步 ENOENT/EACCES、posix_spawn 返回值、fd/file actions、pgroup/signal、wait exit、取消和唯一 child 都属于发布合同；Debug 探针还必须证明每个 exec/spawn 家族入口实际被拦截。
- 原因：把目标改写为 PRoot wrapper 后，wrapper 创建成功可能把原本同步的 exec/spawn 错误变成异步 exit；glibc 内部 hidden symbol 也可能绕过 LD_PRELOAD interpose。二者都会让表面成功的 demo 在真实调用方中改变控制流。
- 影响：RF1320 必须先做入口命中与错误矩阵，再做 Git/Python 复算。若需要复制容器解析器、忽略同步差异或接受漏拦，RF1300 直接 no-go，不进入正式 compat 库。

## ADR-RF-058 unrestricted relay no-go，窄 direct exec/spawn 合同进入复算

- 状态：已接受，RF1320 已完成
- 日期：2026-08-01
- 决定：任意 glibc 父进程的 unrestricted child relay 不进入生产；`system/popen/fexecve` 漏拦与同步错误变化是明确阻断。RF1330 只验证入口无关的窄合同：调用方只使用已覆盖 direct exec/spawn 家族，并明确接受容器目标错误在 child 启动后以异步 exit 表达。
- 原因：常规 exec/spawn 的 argv/env/cwd/fd/exit/signal 和 1/4/8 并发已证明可行，完全放弃会丢掉可复用价值；但把漏拦或 errno 差异藏起来又会制造假兼容。用显式能力要求分层，才能在进程创建前选择而不靠工具白名单。
- 影响：RF1330 必须观察 Git/Python 的实际命中入口与最终状态，不因名称放行。Debug 资产继续独立部署；正式 compat、launcher、Provider、资源、lane 仍不变。若上层无法声明该合同，保持整条 PRoot。

## ADR-RF-059 child relay 只能由调用语义保证开放

- 状态：已接受，RF1330 已完成
- 日期：2026-08-01
- 决定：不增加 Git/Python 工具白名单，也不因一次真机矩阵直接开放整个解释器。只有调用方在进程创建前肯定声明“只使用已覆盖 direct exec/spawn 家族”与“接受容器目标的异步 child failure”时，候选 Provider 才能使用 relay；空声明、未知调用、`system/popen/fexecve` 或依赖同步 errno 的请求继续整条 PRoot。
- 原因：Git 固定矩阵的全部外部机制均通过 `execve`，relay 后状态、文件副作用与 PRoot 一致且保留明显并发收益；同一个 Python 父进程却同时存在可捕获的 subprocess/execve 和绕过 relay 的 `os.system`。工具身份因此不能证明运行语义，只有调用方合同能在首个业务进程前安全分流。
- 影响：RF1340 若接生产，必须新增通用、肯定式、失败关闭的执行保证，不解析工具名或脚本；relay 仍复用唯一 PRoot builder，不生成第二条状态链。无法把保证贯穿 Recipe/Run 请求时，RF1300 以生产 no-go 收口。

## ADR-RF-060 当前 preload child relay 不进入生产

- 状态：已接受，RF1340 已完成
- 日期：2026-08-01
- 决定：保留 Debug relay、真机矩阵与窄调用合同，但不把 relay 打入正式 glibc 资产，不扩展正式 guarantee enum，不修改 Host Python/Git/资源/Planner。RF1300 以“原理可行、当前实现生产 no-go”收口。
- 原因：exec interposer 当前依赖文件 I/O 与动态分配，无法证明多线程父进程 fork 后 child 的 async-signal-safe；每运行 prefix/env 控制文件也没有进入现有 run 生命周期所有权。两项均可能造成死锁、跨运行污染或长期残留，不能用单线程成功样本掩盖。
- 影响：未来重开必须先给出 fork-safe 的预计算/无分配执行路径和由现有 run owner 管理的配置生命周期，再复用 RF1320/RF1330 矩阵。当前未知子进程仍整条 PRoot，已冻结 Node 专用 child bridge 不受影响。

## ADR-RF-061 下一阶段直接归因活跃 PRoot 增量成本

- 状态：已接受，RF1400 进行中
- 日期：2026-08-01
- 决定：下一性能父任务不再新增应用特例，直接用 APK 打包的 active/stock PRoot 做同输入 A/B，并把 active 无遥测与正式遥测分开。Debug 对照不得改变 `activeRuntimeId` 或生产迁移状态。
- 原因：当前上层启动耗时混合了解释器、文件加载、网络和应用初始化；只有同 rootfs/argv 的 PRoot 二进制对照才能回答 Kite 生命周期/View 增量是否构成底层瓶颈。历史 termux baseline 已因 `execve ENOSYS` 隔离，不能作为正常性能对照。
- 影响：RF1410 先固定参数等价与通用负载，RF1420 再上 OnePlus 8T。仅当 active 相对 stock 稳定退化且能归因到可关闭热点时，RF1430 才允许修改 PRoot 资产；强身份、停止确认和保护语义不得作为性能开关被移除。

## ADR-RF-062 PRoot A/B 分离总运行时差异与遥测增量

- 状态：已接受，RF1410 已完成
- 日期：2026-08-01
- 决定：RF1420 同时测 active 正式遥测、同 active 无遥测和 stock 无遥测。active/stock 差值只代表当前打包运行时总体差异；只有同 active 二进制的有/无遥测差值可归因于生命周期采集。
- 原因：active 与 stock 的来源代次、体积和 embedded/external loader 均不同，二元 A/B 无法把差值归因到 View、telemetry 或某个补丁。增加同二进制 telemetry toggle 才能避免错误删除关键能力。
- 影响：基准复用正式 argv/env/bind/network，只允许固定变换；historical baseline 继续 quarantine。低于 15ms 的差异不触发生产工作，至少两个通用负载跨 4/8 并发达到相对与绝对双阈值后，RF1430 才打开。
