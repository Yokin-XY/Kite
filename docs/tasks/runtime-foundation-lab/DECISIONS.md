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
