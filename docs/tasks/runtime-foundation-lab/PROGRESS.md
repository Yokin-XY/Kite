# Kite 混合运行底座进度

## 状态总览

| 任务 | 状态 | 当前结论 |
| --- | --- | --- |
| RF000 | 已完成 | 快速通道、Android 原生能力与 PRoot 兼容底座三车道父任务门全部通过 |
| RF100 | 已完成 | 统一请求、Provider 结果和失败关闭通过全量回归 |
| RF110 | 已完成 | 总架构、三份 Provider 文档、Node 风险索引与性能证据已固化 |
| RF120 | 已完成 | 三个 Node 入口已迁移到统一结构化请求，行为等价 |
| RF130 | 已完成 | Ready/Unsupported/Blocked 已接入三条正式 Node 入口 |
| RF200 | 已完成 | Node 与 Python 通用快速通道均已按分层门收口 |
| RF210 | 已完成 | Node 显式实现标准 Provider，既有行为等价 |
| RF220 | 已完成 | HN-001～HN-011 已完成证据和开放门映射 |
| RF230 | 已完成 | 纯 Python go；subprocess/venv child/第三方扩展保持 PRoot |
| RF240 | 已完成 | 通用 glibc 资产与纯 Python 结构化 Provider 已通过真机门 |
| RF250 | 已完成 | 子进程保持 PRoot；扩展按精确 ABI 与不可变代次开放 |
| RF300 | 已完成 | 下载、文件、安全 ZIP 与 Android 系统能力目录完成分层验收 |
| RF310 | 已完成 | 原生下载、Recipe/Run、资源预取及对照压力门通过 |
| RF320 | 已完成 | 文件 Provider、Recipe/Run、权限边界和真机门通过 |
| RF330 | 已完成 | 安全 ZIP 正确性通过，但真机慢于 PRoot，不进入资源快速车道 |
| RF340 | 已完成 | 真实能力目录、薄适配、失败关闭和父任务门通过 |
| RF400 | 已完成 | PRoot Provider、准入、温热短任务与可调性能档位完成闭环 |
| RF500 | 已完成 | 实际控制面、冷启动接力、遥测和第二生产样板均已闭环 |
| RF510 | 已完成 | actual coordinator 状态已进入 RuntimeHealth，读取不创建 pool |
| RF520 | 已完成 | 策略文件与 host 内存先接力，RuntimeHealth 到达后完全接管 |
| RF530 | 已完成 | route/result/lane 与 queue/execute/total 已进入低基数正式遥测 |
| RF540 | 已完成 | 版本化固定 helper 已进入 SERVICE/SHARED_WRITE 有界 Runner |
| RF550 | 已完成 | 1341 项全量回归、强制构建和 OnePlus 8T 联合门通过 |
| RF600 | 已完成 | owner lease 合同/模拟器闭环；生产长期入口仍需类别桥接门 |
| RF610 | 已完成 | 长期 owner 状态机保持进程身份、停止意图与容量直到确认释放 |
| RF620 | 已完成 | 容量、压力、维护屏障、去重和跨 lane 公平性纯模拟通过 |
| RF630 | 已完成 | 恢复去重、PID 代次、孤儿容量与停止优先合同通过 |
| RF640 | 已完成 | 固定规划态 schema 通过隐私、基数与无副作用护栏 |
| RF650 | 已完成 | 1374 项全量回归通过；后台身份桥接准备 go、生产迁移 no-go |
| RF700 | 已完成 | 自适应规划合同闭环；现有内存收缩保留，失败率调档/自动升档 no-go |
| RF710 | 已完成 | overlay schema 失败关闭，tracee guard 与正式 1/2/4 档位彻底分离 |
| RF720 | 已完成 | actual 差量窗口失败关闭；高压/失败只降一级，无可信 thermal 永不升档 |
| RF730 | 已完成 | 三窗升档、两窗失败、紧急降档、冷却与重启 rebase 纯状态机通过 |
| RF740 | 已完成 | planned/actual 固定投影、合同复核和敏感字段护栏通过 |
| RF750 | 已完成 | 1404 项全量回归与强制构建通过；RF700 无生产装配，不覆盖安装 |
| RF800 | 已完成 | 后台长期 owner 强身份、停止确认与生产 no-go 边界已闭环 |
| RF810 | 已完成 | 定位 PID/token/停止窗口，确认持久强身份还需 boot ID + start ticks |
| RF820 | 已完成 | boot+PID+start ticks 观察、JSON、PID 清理不变量和原子写入口通过 |
| RF830 | 已完成 | 先落停止意图，精确身份恢复，退出确认后才清记录和容量 |
| RF831 | 已完成 | 纯合同只允许精确代次 attach/发信号，所有决策均为零进程创建 |
| RF832 | 已完成 | 窄读新进程身份；重启恢复强校验；健康命令不再保留失效 PID |
| RF833 | 已完成 | 停止意图优先；逐信号重验代次；确认退出前不写终态、不释放容量 |
| RF834 | 已完成 | 1429 项全量门通过；真机创建、外死、重复启动与 PRoot owner 树停止已闭环 |
| RF840 | 已完成 | 生产接入 no-go：强身份已过门，统一容量/STARTING 持久化/actual 观测仍缺失 |
| RF850 | 已完成 | 1431 项全量门、强制 Debug、OnePlus 8T 和范围审查全部通过 |
| RF910 | 已完成 | 同锁 lane 事实与统一只读容量合同完成，仍标记未生产 |
| RF920 | 已完成 | 同一后台记录原子持久化 PRoot route + STARTING，尚未接生产 |
| RF930 | 已完成 | 后台 PRoot PROCESS 已桥接同一 actual controller 与强身份停止链 |
| RF940 | 已完成 | 长期 actual 与短长统一总量已进入正式 RuntimeHealth |
| RF950 | 已完成 | 1460 项全量门与 OnePlus 8T 故障矩阵通过，后台通用 PRoot PROCESS 生产门已打开 |
| RF1000 | 已完成 | 长期 owner 保留短任务余量，actual 健康与父任务门通过 |
| RF1010 | 已完成 | 固定 1/2/4 档长期上限 1/1/3，低功耗不伪造第二容量 |
| RF1020 | 已完成 | actual controller 已限制 managed owner，并允许短任务绕过其等待项 |
| RF1030 | 已完成 | actual v2 健康与 OnePlus 8T 六项固定矩阵通过 |
| RF1040 | 已完成 | 1464 项全量回归、强制构建、连续三轮真机矩阵与范围审查通过 |
| RF1100 | 已完成 | 两轮真机矩阵判生产 no-go，正式入口保持不变 |
| RF1110 | 已完成 | 真实创建点和 READY 边界已审计，生产入口未修改 |
| RF1120 | 已完成 | 两整轮 28×3 固定矩阵零失败、零残留，READY 收窄稳定变慢 |
| RF1130 | 已完成 | 无稳定收益，否决生产启动窗口 |
| RF1140 | 未触发 | RF1130 no-go；未新增生产队列、状态或入口接线 |
| RF1200 | 已完成 | direct Host Git no-go；Debug 证据与全量门闭环，生产链零改动 |
| RF1210 | 已完成 | 正式依赖覆盖与安全边界审计完成，Git 优先于 curl/uv |
| RF1220 | 已完成 | 两套真机矩阵：本地 builtin 明显受益，所有外部子进程类别均有缺口 |
| RF1230 | 已完成 | direct Host Git no-go；argv/预扫描/事后回退都不能保证仓库语义 |
| RF1240 | 已完成 | 1464 项全量门、强制构建、双轮真机和生产范围审查通过 |
| RF1300 | 已完成 | unrestricted relay no-go；窄合同结果可行但当前实现不满足生产 fork/lifecycle 门 |
| RF1310 | 已完成 | 入口/观察语义与同步错误、hidden symbol 风险已固化 |
| RF1320 | 已完成 | direct exec/spawn 正常语义与并发通过；漏拦和同步错误边界已证实 |
| RF1330 | 已完成 | Git 实测 child 满足窄合同；Python 仅显式 direct exec/spawn 子集满足，不能整体放行 |
| RF1340 | 已完成 | preload relay 生产 no-go；Debug 证据保留，正式链零改动 |
| RF1400 | 已完成 | Kite 补丁/loader/NDK 候选均 no-go；正式 v23 保持不变 |
| RF1410 | 已完成 | 固定三资产身份、loader 等价、五类通用负载与双阈值 |
| RF1420 | 已完成 | 两套隔离 sink 矩阵打开 small-write 与 c4 lifecycle 热点门 |
| RF1430 | 已完成 | 高并发差异始于六个 Kite patch 之前；lifecycle 三候选无收益 |
| RF1431 | 已完成 | 九轮矩阵排除默认扩展、loader、共享日志和 registry 竞争 |
| RF1432 | 已完成 | 完整 v23 字节复现；固定补丁消融、unbundled 与 NDK 28 候选 |
| RF1433 | 已完成 | lifecycle 语义一致但无收益；三套补丁消融矩阵零失败、零残留 |
| RF1440 | 已完成 | 不更新正式 PRoot；全量门、真机与生产范围审查通过 |
| RF1500 | 已完成 | 债务总账、原生证明、真实打开链和父任务门均已完成 |
| RF1510 | 已完成 | 三车道明确不支持、兼容路线与未来候选已统一编号 |
| RF1520 | 已完成 | 打开/获取共享能力；收益成立，无执行位假阳性已进入 RF1530 |
| RF1530 | 已完成 | 完整肯定证明直达原生；三轮 p50 降低 88.6%～90.2% |
| RF1540 | 已完成 | OpenClaw 打开 native=1/fallback=0；Full 1473 项零失败 |
| RF1550 | 已完成 | 保留全量覆盖；Quick 默认测试数减少 82.7%，本机构建统一排队 |
| RF1551 | 已完成 | 276 类测试是覆盖累积，不是重复注册；共享 daemon/机器资源会碰撞 |
| RF1552 | 已完成 | Quick/Stage/Full 实跑零失败，默认测试数最高减少 82.7% |
| RF1553 | 已完成 | 独立工作进程持锁；外层中断后仍阻止 Gradle 重叠 |
| RF1554 | 已完成 | Full、Debug 构建、互斥探针和范围审查全部通过 |
| RF1600 | 已完成 | 默认 npm 元数据原生读取通过真实双车道链与 Full 1487 项父门 |
| RF1610 | 已完成 | 5 个正式资源共享默认元数据探针；两类高风险调用面已排除 |
| RF1620 | 已完成 | 三轮 13 类零差异；单请求和 5 请求批次收益门均通过 |
| RF1630 | 已完成 | 生产 Provider、唯一回退、Stage 与 OnePlus 固定矩阵均通过 |
| RF1640 | 已完成 | 真实动作链 native/proot 各一次；Full 1487 项零失败，production go |
| RF1700 | 已完成 | 批量版本检查进程前预检与 3/1 调度 production go |
| RF1710 | 已完成 | 正式批量入口、三个候选与预设发布门已固定 |
| RF1720 | 已完成 | 固定矩阵三轮零差异，最小收益 58.4%，候选 p95 380ms，判 go |
| RF1730 | 已完成 | 进程前预检、生产 3/1 调度、顺序与分层测试均通过 |
| RF1740 | 已完成 | OnePlus 真实双车道批量链与 Full 1497 tests 通过 |
| RF1800 | 已完成 | 首次内置 6 资源生产依赖调度放行；最小减少 67.7%/42,749ms，Full 1503 tests |
| RF1810 | 已完成 | 44,411ms 真实串行基线与 6 资源依赖图已审计，发布门已冻结 |
| RF1820 | 已完成 | 真实内置包三轮零差异，最小减少 68.9%/38,320ms，判 go |
| RF1830 | 已完成 | 生产 6 资源依赖调度已接入；真包三轮最小减少 67.7%/42,749ms |
| RF1840 | 已完成 | 真实生产复用/登记链和父门唯一 Full 通过，production go |
| RF1900 | 已完成 | 默认 Ubuntu 容器冷进程复用 production go；三轮至少减少 69.1%/1060ms，Full 1514 tests |
| RF1910 | 已完成 | 第一名冷启动默认容器复用；工具链热复用与健康合并均因收益不足 no-go |
| RF1920 | 已完成 | 三轮冷进程减少 89.7%～90.1%/至少 1851ms；p95 214ms，判 go |
| RF1930 | 已完成 | 版本化物理收据＋动态网络复算；三轮减少 69.1%～69.7%/至少 1060ms |
| RF1940 | 已完成 | 真实 App 冷启动命中、FATAL/ANR=0、唯一 Full 1514 tests，production go |
| RF2000 | 进行中 | 重新审计下一通用候选；未固定门前不写生产补丁 |
| RF2010 | 进行中 | 真实调用面、兼容债务和停止信号审计 |

## RF1800 开机与三问自检

- 目标是什么？只缩短首次准备 Kite 底层环境时 6 个正式内置资源的整批等待：按显式依赖图最多并行 2 项；不改变安装脚本、资源语义、登记事实或页面。
- 完成后拿什么证明？固定 6 任务依赖/失败/顺序矩阵；OnePlus APK 内置真实包三轮零差异；每轮至少缩短 20% 且至少 5 秒、候选 p95 不高于 36 秒；真实生产 6 资源链；Targeted/Quick/Stage、父门唯一 Full 与 FATAL/ANR。
- 依赖是否满足？RF1700 已完成真实双车道批量链和 Full 1497 tests；RF1550 的分层测试和 Gradle 锁可继续使用；分支 `codex/runtime-foundation-lab` 从 `d837714b87f1c032362e2e2650e93d96c39a15b6` 干净开始。
- 红线：不解析 shell，不按资源/包/命令身份选择槽位；不改变普通资源安装/更新/取消事务、Store、UI 或 PRoot View；AI 会话全范围冻结；只用 OnePlus 8T `3f8bbaad`，不触碰魅族、main、其他工作树、远端、版本或发布。

## RF1810 候选与发布门审计

- 第一名“内置底层资源依赖调度”覆盖 Node.js、Python、uv、Git、curl、系统工具 6 个正式资源。当前 `runPreparePackLocked()` 用 `mapIndexed` 串行执行；OnePlus 已有成功状态从 `1785472682584` 到 `1785472726995`，整批 44,411ms。正式关系中只有系统工具依赖 Node.js，其余可在副作用前由显式图判定 Ready，且写入独立资源根。
- 第二名“获取前资源清单强制重载”虽覆盖所有资源获取，但当前只有 17 份、68,450 字节正式清单，加载器已有完整进程缓存，剩余一次重载大概率只有细节收益，暂不建生产父任务。第三名“连续 PRoot 步骤复用”在正式资源安装中已由 `KiteResourceInstallPlanCompiler` 合并为单个事务 shell；剩余主要是用户自建多步卡片，达不到本轮正式资源覆盖门。
- RF1820 的门已在生产改动前固定：固定 6 任务三轮零差异、输入顺序、依赖阻断、失败隔离、无残留、最大活动数 `<=2`；OnePlus 真实包每轮至少缩短 20% 且至少 5,000ms，候选 p95 不高于 36,000ms。任一门失败即 no-go。
- 本轮 primary lane 为 Orchestrator：启动准备入口只提交一组结构化任务和依赖，调度器决定释放窗口，既有安装函数执行原 Ubuntu/PRoot 脚本；`KiteResourceInstallStore` 继续拥有每资源事实，页面只显示既有进度。RF1810 只修改三件套，没有生产补丁、测试、构建或设备状态变更。

## RF1820 固定依赖矩阵与真实包基线

- 新增入口无关的 `DependencyBatchScheduler` 候选合同，但没有接入生产 `ToolchainPackInstaller`。它只读取任务键和依赖集合；重复键、缺失依赖、自依赖或依赖环在创建线程和执行首个任务前 Blocked。正常图固定最多 2 槽，失败只阻断自己的后继，异常不取消独立任务，结果按输入顺序返回。
- 固定 JVM 合同 4 tests 全部通过；Stage 共 57 suites、266 tests、零失败。Debug 构建 60 tasks 成功。通用调度器源码没有资源 ID、包名、命令名或页面特判。
- Debug-only 真机矩阵固定使用 APK `ai-dev-pack` 的 Node 30.94MiB、Python 28.85MiB、uv 22.94MiB、pnpm 4.39MiB 包，以及 Git/curl rootfs 动作；6 个任务、1 条正式依赖、3 轮、2 槽和阈值均不可由 ADB 覆盖。每个串行/候选使用独立临时软件根和 bin 根，既有正式安装目录未修改。
- OnePlus 三轮串行为 `57,809/68,948/55,630ms`，候选为 `13,704/20,412/17,310ms`，分别减少 `76.3%/70.4%/68.9%`，绝对减少 `44,105/48,536/38,320ms`；候选 p50 17,310ms、p95 20,412ms，明显超过预设每轮 20%＋5,000ms、p95 36,000ms 门。
- 三轮 `differences=0`、输入顺序稳定、6 项全部成功、最大活动数 2、依赖数 1、进程清理和 `fixturesCleanedOnExit=true`；隔离目录最终不存在，当前 Kite 进程 FATAL/ANR 为 0。RF1820 判 go，允许 RF1830 最小生产接入，但不允许修改脚本、资源登记、普通资源安装、UI 或 AI 会话。

## RF1830 最小生产调度样板

- `ToolchainPackInstaller.runPreparePackLocked()` 已由完全串行改为委托通用 `DependencyBatchScheduler`。生产任务清单显式声明系统工具依赖 Node，其余 5 项无前置；调度器仍只消费键、依赖和工作闭包，不读取资源名、包名、命令或页面身份。
- 每项继续调用原 `BootstrapInstallRunner`、原安装脚本参数和原资源根；执行异常或依赖阻断的任务通过原 `ToolchainResourcePort` 登记失败，不新增 Store。结果继续按输入顺序生成失败列表、timeout 和最终摘要，`RuntimeBootstrapProgress` 仍是唯一启动进度拥有者。
- Debug 固定矩阵已读取生产调度合同并标记 `providerSource=production_scheduler`。OnePlus 三轮串行 `63,182/64,940/62,200ms`，生产调度 `20,433/21,008/18,303ms`，减少 `67.7%/67.7%/70.6%`，绝对至少减少 `42,749ms`；`differences=0`、6 项全成功、1 条依赖、最大活动数 2、性能/正确性/清理门全部通过。
- Targeted 16 tests、Quick 57 suites/266 tests、Stage 58 suites/278 tests 与 Debug 构建均零失败；固定临时目录不存在、矩阵服务已停止、Kite 进程存活，FATAL/ANR 为 0。补丁没有触及 UI、普通资源事务、PRoot View 或 AI 会话，RF1830 完成并进入 RF1840。

## RF1840 go/no-go 与父任务门

- OnePlus 真实生产证明直接调用 `prepareAiEnvForBootstrap()`：正式合同为 6 个资源、1 条依赖，运行前后 `bootstrapResourcesSettled=true`，结果为 `phase=succeeded`、`exitCode=0`、`timedOut=false`、`SUMMARY resources=6 failed=0`，生产链耗时 2,301ms。设备已有工具链完整，因此按原文件校验复用并登记；没有清空应用数据或正式资源目录来伪造首次安装。
- 首次真实包安装的性能和最大活动数继续由隔离但使用同 APK 包/生产调度合同的三轮矩阵证明：候选 18,303～21,008ms，最小减少 67.7% 和 42,749ms，零差异、最大活动数 2、夹具和进程无残留。
- RF1800 唯一 Full 为 289 suites、1503 tests、0 failures、0 errors、3 个既有平台跳过；当前 Kite 进程存活，Debug 证明服务已退出，FATAL/ANR 为 0。正式架构只记录首次内置工具链的局部依赖调度，不把结论外推到任意资源安装或任意 shell。
- RF1800 判 production go。用户可感知收益是新装或需要补齐底层工具链时，6 份资源不再无条件排队；已完整安装的日常启动只走约 2.3 秒真实复用/登记链，不宣称每次启动都减少 40 秒。

## RF1900 开机与三问自检

- 目标是什么？只缩短新进程第一次取得普通默认 Ubuntu 容器时重复的完整准备；复用现有普通启动已经信任的结构化运行时/容器身份，未知事实继续完整准备。
- 完成后拿什么证明？固定 Ready/Unsupported/Blocked 反例、候选零写入、OnePlus 三轮冷进程同一容器身份、每轮至少 50%＋700ms、候选 p95 不高于 500ms、端到端至少 20%＋500ms、Targeted/Quick/Stage、父门唯一 Full 与 FATAL/ANR。
- 依赖是否满足？RF1800 已以 Full 1503 tests 完成；当前分支从 `10a3821f076c17f61ee980177f760ae6a066780f` 干净开始；现有 `RuntimeLaunchPreparationIdentity`、普通候选和每进程 single-flight 可作为真实合同，不新增事实源。
- 红线：不改变首次安装/修复/重置/Canary/显式 View/环境/封存保护/动态网络/命令语义；不触碰 AI 会话、UI、PRoot View、魅族、main/其他工作树、远端、版本或发布。

## RF1910 候选与发布门审计

- 第一名“默认 Ubuntu 容器冷启动复用证明”覆盖启动协调、后台核心运行项、终端/卡片/资源在新进程中的第一次默认容器取得。OnePlus 冷进程真实日志：`ensureBaseImageReady(default-container)=919ms`，`ensureContainerFilesystem(ubuntu-main)=787ms`；容器阶段中工作区支持 309ms、系统组件 181ms，随后普通启动仍有 79ms 系统组件校准。当前普通 PRoot 启动候选已检查运行时文件/描述符、基础镜像、默认容器记录、容器 rootfs 标记/必需文件和工作区目录，但 `ensureDefaultContainer()` 因进程内身份为空仍重做整轮。
- 第二名“已安装内置工具链快速复用”在当前正式安装状态下三轮仅 82/25/47ms；RF1840 的 2,301ms 是一次生产证明，不足以外推稳定日常成本，判 no-go。第三名“运行状态校准波次合并”的 Supervisord 固定探针为 59ms，且 `ProotTelemetryStore`、`SupervisordServiceHealthStore`、后台状态刷新都已有单飞/限频，判 no-go。
- RF1920 门已在生产改动前冻结：固定结构化反例和候选零写入；OnePlus 三轮冷进程同一容器身份，候选每轮至少减少 50%＋700ms、p95 不高于 500ms，端到端至少减少 20%＋500ms。失败即 no-go。
- 本轮状态拥有者仍为现有容器注册表和 `RuntimeLaunchPreparationCache`；Provider 不创建第二 Store。页面不参与、不扫描、不整刷。RF1910 只写三件套，没有生产补丁。

## RF1920 固定冷启动准备矩阵与基线

- 新增纯判断 `DefaultContainerColdReuseProvider`：普通默认请求只有在当前 APK 的 PRoot 选择与已安装描述符一致，且基础镜像、默认容器记录、rootfs 标记/必需文件、工作区与 `RuntimeLaunchPreparationIdentity` 全部完整时才 Ready；显式 View/环境、描述符变化和任一物理事实缺失均 Unsupported/Blocked，并在副作用前回到原完整准备。
- Debug 固定入口只允许 ADB 触发，不接受路径、容器、版本、阈值或轮数参数；候选判断前后对正式证明文件取样。覆盖 Ready、显式 View/环境、运行时变化、基础镜像/容器/rootfs/工作区缺失和非法身份的固定测试全部通过。
- OnePlus 覆盖安装后的校准轮已直接 Ready：候选 208ms、原完整准备 2130ms，减少 1922ms/90.2%。随后三轮正式独立冷进程为：候选 214/205/210ms，对照 2078/2067/2061ms，分别减少 1864/1862/1851ms、89.7%/90.1%/89.8%；候选 p50 210ms、p95 214ms，三轮均 `noSideEffects=true`、`sameIdentity=true`。
- 预设门要求每轮至少 50%＋700ms、候选 p95 不高于 500ms、端到端至少 20%＋500ms，实测全部通过。定向测试与 Debug 构建通过，Stage 为 61 suites/285 tests、零失败/零错误；真机 FATAL/ANR 为 0。RF1920 判 go，进入 RF1930；尚未允许粗暴跳过目录权限、主机组、时区、包管理器目录或工作区组件等可变自修复。

## RF1930 最小生产复用样板

- 第一版生产接入只跳过基础镜像与注册表重写，保留整个容器自修复；三轮完整准备 1881～1943ms，生产 1335～1361ms，只减少 523～582ms、27.8%～30.0%，没有通过 RF1920 预设的 50%＋700ms，未放行也未降低门槛。
- 真实阶段日志定位到重复成本：包管理目录、主机组和时区在外层与 bootstrap 重复；`WorkspaceBuildSupport.ensure` 在容器层与系统组件层重复；每个目录权限还启动一次 `/system/bin/chmod`。生产代码只保留一轮等价修复，并改用 Android `Os.chmod`，不改变目录模式或失败语义。
- 为关闭跨进程自修复语义，新增版本化物理收据：绑定 APK `versionCode/lastUpdateTime`、PRoot/loader/描述符物理戳、容器身份、Host 时区、rootfs 关键文件/目录权限、工作区静态组件和必要动态路径；普通冷进程只有收据复算完全一致才 Ready，动态网络仍每次重建。APK 重装、时区或任何证明变化都会整条回完整准备并在成功后重写收据。
- 固定真机反例把收据时区改为不可匹配值，得到 `fallbackTriggered=true/repaired=true/correctnessGate=true`；未由 ADB 传入路径、容器、阈值或样例。最终 OnePlus 三轮配对：完整准备 1550/1535/1546ms，正式快路 479/475/469ms，减少 1071/1060/1077ms、69.1%/69.1%/69.7%；快路 p95 479ms，三轮 `sameIdentity=true/baseUnchanged=true`。
- 定向测试、Quick 58 suites/268 tests、Stage 62 suites/291 tests（1 个既有平台跳过）与 Debug 构建通过。补丁只改运行时准备、工作区物理证明和 Debug 入口；没有页面、Store、PRoot 命令、View、AI 会话或其他工作树改动。RF1930 完成，进入 RF1940 唯一 Full 与父门。

## RF1940 go/no-go 与父任务门

- OnePlus 强停后从正式 `StartupGuardActivity` 冷启动，系统确认 `LaunchState=COLD`、进入 `MainActivity`、`TotalTime=1404ms`；同一真实进程的正式后台准备日志命中 `默认容器冷进程复用` 与 `mutableRepair=receipt_verified`，不是 Debug 入口直接调用。
- 固定收据失配反例为 `fallbackTriggered=true/repaired=true`；最终三轮正式/完整配对仍为 469～479ms 对 1535～1550ms，每轮至少减少 69.1%/1060ms，身份一致、基础镜像不变。当前 Kite 进程存活，FATAL/ANR=0。
- RF1900 唯一 Full 为 291 suites、1514 tests、0 failure、0 error、3 个既有平台跳过。Robolectric 在构建成功后报告一个 Windows 临时目录未立即删除；该目录位于系统 Temp，不在工作树、设备或 Git 中，测试退出码仍为 0，未重跑第二次 Full。
- RF1900 判 production go。用户可感知收益是新进程第一次取得已经完整修复过的默认 Ubuntu 容器时，约 1.5 秒的重复准备降到约 0.47 秒；首次安装、APK 更新、收据失配和所有未知事实仍付完整修复，不把结论外推到首屏全部耗时或任意 PRoot 命令。

## RF2000 开机与三问自检

- 目标是什么？RF1900 结束后重新审计真实生产调用面，只开启仍能跨多个正式流程、结构化失败关闭并带来用户可感知收益的下一父任务。
- 完成后拿什么证明？最多三个候选的正式调用方/频率/固定成本，第一名固定反例、端到端收益门与停止信号；无候选达门则连续记录 no-go，不写生产补丁。
- 依赖是否满足？RF1900 已以唯一 Full 1514 tests 和 OnePlus 真实冷启动完成；当前分支从 `f1d7c402` 后继续，AI 会话、UI、PRoot View、魅族、main/其他工作树、远端、版本与发布仍冻结。

## RF1700 开机与三问自检

- 目标是什么？依据 PLAYBOOK 的 RF1700，只缩短资源管理页批量检查已获取资源更新的整批等待：结构化原生/远端任务最多 3 个并发，显式命令/PRoot 最多 1 个；不改变版本语义、结果顺序、Store 或页面。
- 完成后拿什么证明？固定五请求三轮零差异；顺序、检查中先写、失败隔离、取消和并发上限断言；p50 每轮至少降低 40%、p95 不高于 550ms；OnePlus 真实至少 2 目标批量链；Targeted/Quick/Stage、父门唯一 Full 和 FATAL/ANR。
- 依赖是否满足？RF1600 已通过生产 Provider、真实 native/proot 各一次和 Full 1487 tests；RF1550 已提供测试分层与跨 worktree Gradle 锁；分支 `codex/runtime-foundation-lab` 在 `46c1f890149da7d267cc6aa53496656c934f091b` 干净开始。
- 红线：AI 会话协议、消息模型、流式输出、渲染、会话恢复、附件和样式全部冻结；不迁移显式版本命令、不解析 shell、不改安装/更新事务、单项检查、Store、UI 或 PRoot View；只用 OnePlus 8T `3f8bbaad`，不触碰魅族、main、其他工作树、远端、版本或发布。

## RF1710 候选与发布门审计

- 第一名“批量版本检查分车道有界并发”覆盖正式资源管理页唯一批量入口及所有符合更新条件的已安装资源。当前先批量写入 `markUpdateChecking()`，随后 `targets.map` 完全串行；5 个默认 npm 正式资源的本地版本已原生化、远端请求相互独立，串行累计等待属于真实用户链。
- 第二名“显式 `--version` 结构化迁移”虽然覆盖 4 份显式探针，但 CLI 版本可能不等于包元数据版本，且显式声明本身可能是业务覆盖，当前不能安全替换。第三名“Ubuntu `/run`/locale 小探针原生化”频率较高，但缺少 rootfs 权限、mount、owner 和启动时序闭包，均保留兼容路线。
- RF1720 的门在生产改动前固定：五请求三轮零差异；输出顺序不变；原生/远端并发 `<=3`、兼容并发 `<=1`；检查中状态先写、失败隔离、取消无残留；批次 p50 每轮至少降低 40%、候选 p95 不高于 550ms。门失败即 no-go。
- 本轮 primary lane 为 Orchestrator：Action Intake 只提交批量意图，`KiteResourceInstallStore` 仍是状态拥有者，`ResourceVersionCoordinator` 仍产出版本事实；禁止把并发选择放到页面、Store 或 Execution Core。AI 会话不参与调用链并被明确冻结。

## RF1720 固定调度矩阵与基线

- 新增 Debug-only 固定五请求矩阵：3 个 `STRUCTURED_NATIVE_REMOTE`、2 个 `PROOT_COMPATIBILITY`，其中固定包含成功、可更新、失败、不支持和本地领先结果；ADB 只能触发，不能传样例、延迟、轮数、槽位或阈值。
- OnePlus 8T 三轮串行分别为 913/911/910ms，候选分别为 380/370/372ms，减少 58.4%/59.4%/59.1%；候选 p50 372ms、p95 380ms，超过每轮至少 40% 且 p95 不高于 550ms 的预设门。
- 三轮结果差异为 0，输出顺序不变；结构化原生/远端最大活动数为 3，兼容最大活动数为 1；全部检查中先于首个探针，固定失败不抹掉其他结果，取消后活动数归零。
- Targeted 4 tests、Quick 55 suites/261 tests、Stage 56 suites/264 tests 均零失败；Debug 构建、OnePlus 安装和固定广播通过，当前进程日志 `FATAL EXCEPTION`/`ANR in com.kite.app` 为 0。生产 `AndroidResourceActionGateway` 仍是串行，RF1720 只证明候选可进入 RF1730。
- `kfshell-toolchain` 发现本机 `references/toolchain.md` 的默认魅族记录已落后于项目规范；实时确认 OnePlus `3f8bbaad` 在线后已把忽略跟踪的本机参考纠正为默认 OnePlus，未查询、启动或安装魅族。

## RF1730 最小生产调度样板

- 新增通用 `ResourceVersionBatchScheduler`：只接收调用方预先确定的 `ResourceVersionBatchLane`，固定结构化原生/远端 3 槽、PRoot 兼容 1 槽；分类在任何任务执行前全部完成，结果由 `awaitAll()` 按输入顺序返回，调度器不认识资源 ID、包名、命令或页面。
- 为避免“结构化声明存在但本地事实临时不足”误入 3 槽并并发启动 PRoot，`ResourceVersionCoordinator.prepareBatchCheck()` 会先调用 Android 受控 JSON Provider：Ready 缓存已安装版本并只在 3 槽读取远端，Unsupported 整项进入 1 槽再走既有兼容路径，Blocked 直接返回失败且不启动命令。全部预检发生在所有 `markUpdateChecking()` 之后、首个业务进程之前。
- `AndroidResourceActionGateway` 只把原串行 `targets.map` 替换为“批量预检＋生产调度”，既有 `applyUpdateCheckResult()`、汇总文案、输入顺序和 Store 所有权不变；`KiteAppGraph` 只记录总数、两类数量和实际最大活动数，不记录资源身份。
- RF1720 Debug 候选已收缩为对生产调度器的薄委托。OnePlus 生产调度器固定矩阵三轮串行 909/910/910ms、候选 377/372/371ms，减少 58.5%/59.1%/59.2%，p95 377ms，零差异且 `providerSource=production_scheduler`。
- Targeted 5 suites/21 tests、Quick 55 suites/261 tests、Stage 59 suites/281 tests 均零失败；Debug 构建和 OnePlus 安装/广播通过，当前日志 FATAL/ANR 为 0。未运行 Full，留给 RF1700 父门。
- 生产范围只涉及版本批量 Orchestrator、版本事实预检 Provider、AppGraph 低基数观测及对应 Debug/测试；AI 会话、页面、Store、安装/更新事务、单项入口、PRoot View 和其他设备均未改。

## RF1740 真实批量链与父任务门

- 新增 Debug-only 真实链触发器，ADB 只能触发，不能传资源、车道或数量。它从 `KiteResourceInstallStore.registrySnapshot()` 的正式已安装事实与 `KiteResourceSourcePlanFactory.versionCheckPlan()` 中，按通用合同选择一个声明结构化原生/远端和一个兼容目标，再调用生产 `ResourceActionWorkflowCoordinator.checkUpdates()`。
- OnePlus 当前共有 2 个符合条件的正式已安装目标，选择数 2、去重后 2；生产摘要为 `structuredNativeRemote=1`、`prootCompatibility=1`、两类最大活动数均 1。兼容目标记录 `proot_fallback/structured_metadata_absent`，批量完成只返回既有单个汇总 Effect，`checkingRemaining=0`。
- 全部目标的 `markUpdateChecking()` 在预检和调度前执行；生产结果继续按输入顺序由既有 `applyUpdateCheckResult()` 写回 Store。页面、按钮、刷新、汇总文案和单项检查入口没有修改，因此没有引入整页刷新、并行页面状态或新 Store。
- 最终 Targeted 合同、Quick 56 suites/262 tests、Stage 60 suites/282 tests、Debug 构建/安装和 OnePlus 真实链均通过，日志 FATAL/ANR 为 0。RF1700 父门唯一 Full 为 288 suites、1497 tests、零失败、3 个既有平台跳过，production go。
- 本父任务只缩短多个独立更新检查的累计等待；不承诺远端网络本身、单项检查或应用启动缩短 58%～59%。AI 会话协议、消息模型、流式输出、会话恢复、附件与样式始终冻结，未进入审计或改动文件。

## RF1600 开机与三问自检

- 目标是什么？只优化默认 npm 来源的已安装版本元数据读取：把可由受控 JSON 文件完整表达的请求放到 Android 原生只读 Provider；任何自定义命令、未知文件事实或非共同语义在首个业务进程前保留现有 PRoot。
- 完成后拿什么证明？不可由 ADB 改写当前样例的固定矩阵；三轮零差异和预设 p50/p95/批次收益门；至少两份正式清单合同、真实已安装资源唯一车道证据；Targeted/Quick/Stage、父门 Full、Debug 构建和生产范围审查。
- 依赖是否满足？RF1500 已以 Full 1473 tests 和真实 OpenClaw `native=1/fallback=0` 收口，RF1550 已提供分层测试和跨 worktree Gradle 串行入口；冻结锚点 `8c046238b3c59094becc8f46df9857169a733649`、分支 `codex/runtime-foundation-lab`、工作树干净。
- 红线：不重做 Node/Python/RF1500，不解析 shell，不迁移显式版本命令、远端版本请求、安装事务或取消清理；不新增 Store、不改 UI/PRoot View；只用 OnePlus 8T `3f8bbaad`，不触碰魅族、main、其他工作树、远端、版本或发布。

## RF1610 候选与发布门审计

- 正式调用面共有 7 个 npm 来源资源；其中 5 个没有自定义 `versionProbe`，统一由 `defaultInstalledVersionProbe()` 生成 `package.json.version` 读取，并在单项/批量“检查更新”中经 `AndroidResourceVersionGateway` 逐资源启动静默 PRoot + Node。这是多个正式资源复用的真实高频链，不是 Debug demo。
- 第一名且唯一过初筛候选是“受管 JSON 元数据字符串字段读取”：输入可显式表达为容器路径、最大字节和字段；Android 已有 `/workspace` 物理映射、NOFOLLOW 文件事实和 JSON 能力；准备阶段可在无进程、无副作用前给出 Ready/Unsupported/Blocked。
- 4 个显式 `命令 --version` 探针未进入候选，因为调用前不能完整证明 Linux ELF、Node 包闭包、子进程、环境和输出语义；取消安装清理未进入候选，因为当前命令同时修改活动安装根、停止软件、调用包管理器并含资源分支，正确性风险高且不是高频链。
- RF1620 的固定门已经写入 PLAYBOOK：三轮零差异；原生 p50 每轮至少降低 70%、p95 每轮不高于 30ms；固定 5 请求批次 p50 至少降低 60%。门失败即 no-go，不写生产样板。
- 本叶只修改 `PLAYBOOK/PROGRESS/DECISIONS`，没有改生产 Gateway、Provider、资源清单、测试、构建配置或设备状态。下一恢复指针为 RF1620。

## RF1620 固定矩阵与 go 结论

- 新增 Debug-only `MANAGED_PACKAGE_VERSION_BENCHMARK`，ADB 只能触发，不能传入资源、包、路径、字段、轮数或阈值。夹具固定覆盖 5 个正向包元数据、缺失、符号链接、超限、坏 JSON、缺字段、非字符串、旧自定义命令和路径逃逸共 13 类。
- 三轮最终均为 `differences=0`、`correctnessGate=true`、`fixturesCleanedOnExit=true`。5 个普通/作用域/预发布/构建元数据样本走 `native/fallback=0`；符号链接、超限和格式未知走既有 PRoot；路径逃逸为 `blocked/fallback=0`。
- 单请求三轮 PRoot p50 为 `506.769/506.872/405.967ms`，原生 p50 为 `2.423/2.407/3.078ms`，分别降低 `99.5%/99.5%/99.2%`；原生 p95 为 `6.775/6.238/6.088ms`，均低于预设 30ms。
- 固定 5 请求批次三轮 PRoot p50 为 `2795.022/2673.266/2752.334ms`，原生为 `12.850/9.683/9.112ms`，分别降低 `99.5%/99.6%/99.7%`，高于预设 60% 门。
- 首次真机夹具把预发布和构建元数据组合在同一字符串，超出 Kite 当前单后缀版本合同；已拆成两个合法固定样本，没有修改解析器。随后安全阻断样例曾因比较器把原因文本与空基线文本做全对象比较产生一个假差异；修正为校验 `blocked/fallback=0`，没有改变 Provider、样例结果或阈值。
- 最终 Stage 为 53 suites、261 tests、0 failure、0 error、1 skipped；跳过项仅为 Windows 宿主无符号链接权限，OnePlus 固定 symlink 样例已真实通过。Debug 构建 60 tasks 成功；APK `247509540` bytes、SHA-256 `5A8CC74B9E5E97BCDB0C21F0B82221660D7C8F730EA9E54CA41713ED000B082C`，只覆盖安装到 `3f8bbaad`，未进入 Git。
- OnePlus 当前 Kite 进程无 FATAL/ANR，`files/runtime/shared/ubuntu-main/.kf/cache/rf1620-managed-package-version` 不存在。本叶没有修改生产 Gateway、Provider、资源清单、Store 或 UI；RF1620 判 go，下一恢复指针为 RF1630。

## RF1630 最小生产样板

- 新增入口无关的 `AndroidNativeStructuredJsonStringProvider`：请求只含授权根、容器路径、最大字节和顶层字符串字段；普通文件和严格 UTF-8 JSON 字符串字段形成 Ready，非法授权/路径合同 Blocked，缺失、符号链接、超限、坏 JSON、缺字段和非字符串保持 Unsupported。Provider 不读取资源 ID、包名、命令或页面，也不创建进程或写状态。
- `KiteResourceVersionProbeSpec` 只新增可选内部结构化元数据合同；`defaultInstalledVersionProbe()` 为默认 npm 探针同时保留原 `node -p` 命令回退并附加合同。正式资产扫描确认 5 份无显式 `versionProbe` 的 npm 资源共享该路径；显式命令探针结构化事实为空，行为不变。
- `AndroidResourceVersionGateway` 只在已安装版本入口读取结构化合同：Ready 直接返回，Unsupported 在首个业务进程前只执行一次原 PRoot 命令，Blocked 失败关闭且不回退；远端版本和显式命令路径未迁移。组合根只提供 `/workspace` 现有物理映射和低基数 route/reason 日志，没有新增 Store、UI 扫描或页面刷新。
- 最终 Stage 为 62 suites、315 tests、0 failure、0 error、1 skipped；跳过仍仅为 Windows 宿主无符号链接权限。固定网关测试证明 Ready `0` 次 recipe、Unsupported `1` 次、Blocked `0` 次、旧命令 `1` 次；静态护栏证明已安装版本选择不读取 `probe.command`，生产 Provider 不含资源/包/页面标识。
- 最终 Debug 构建 60 tasks 成功；APK `247525656` bytes、SHA-256 `16C7FF592D8695C5F916CB199896CD86356B632AD414540CB7F178D382F201C3`，仅安装到 OnePlus 8T `3f8bbaad`。生产 Provider 固定矩阵为 13 类、`differences=0`、`correctnessGate=true`；单请求 PRoot/native p50 为 `208.897/3.121ms`，降低 `98.5%`，原生 p95 `7.414ms`；五请求批次 p50 为 `1352.117/8.938ms`，降低 `99.3%`，`providerSource=production` 且夹具清理为真；同一日志核对无 FATAL/ANR。
- 曾有两次验收采集器假失败：第一次 `logcat -T` 的含空格时间被 PowerShell 拆参，第二次按历史行数截取新日志受环形缓冲变化影响；两次都没有改变生产代码、样例或阈值。第二次矩阵真实完成行已按专用标签和时间戳复核，不能把采集器超时解释成性能结果。RF1630 判 go，下一恢复指针为 RF1640。

## RF1640 真实链与父任务 go

- 新增 Debug-only `RESOURCE_VERSION_PRODUCTION_ROUTE_PROOF`，ADB 只能触发，不能提交资源 ID、路径或车道。入口从真实 `KiteResourceInstallStore.registrySnapshot()` 读取已安装登记，经正式 `KiteResourceSourcePlanFactory.versionCheckPlan()` 按结构化合同有/无各选第一项，并调用真实 `ResourceActionWorkflowCoordinator.dispatch(CheckUpdate)`；没有固定资源、包或命令白名单。
- OnePlus 8T 的真实登记中有 2 个满足完整版本计划的已安装资源。正式动作链按时间顺序记录一次 `route=android_native reason=structured_json_string_ready` 和一次 `route=proot_fallback reason=structured_metadata_absent`，两次均返回 1 个正式 Effect；证明入口最终为 `nativeChecks=1 fallbackChecks=1 adbOverrides=false`。原生开始后没有 PRoot 重放，旧显式探针只执行兼容车道。
- RF1640 Stage 为 63 suites、320 tests、零失败；独立 Quick 为 54 suites、260 tests、零失败。Debug 构建 60 tasks 成功；APK `247525656` bytes、SHA-256 `4540743460E420C26A071F6448AEE53B2E1D997F2A4E8E960561FB056C438092`，只覆盖安装到 OnePlus 8T `3f8bbaad`。
- RF1600 父门唯一一次 Full 为 284 suites、1487 tests、0 failure、0 error、3 skipped，墙钟 `154.883s`。跳过项是 Windows 宿主符号链接权限、容器符号链接 fd wrapper 与真机 PRoot journal 夹具，均有既有平台/真机门，不是本轮失败。OnePlus 当前进程 PID `24429` 的 FATAL/ANR 计数为 0。
- 生产范围审查确认没有修改正式资源清单、远端版本实现、安装/更新/取消事务、Store、UI、PRoot View、Node/Python/RF1500、版本或发布；没有操作魅族、main/其他工作树或远端。RF1600 判 production go，后续必须重新从兼容债务总账立新父任务，不得外推为任意版本命令或整个应用启动优化。

## RF1500 开机与三问自检

- 目标是什么？依据 PLAYBOOK 的 RF1500：先保存三车道全部明确不支持/未放行边界，再只研究多资源共用的高频结构化只读验证，不重新优化 Node/Python，不处理任意 shell。
- 完成后拿什么证明？稳定编号的债务总账；正式调用面和文件身份合同；固定正确性/性能矩阵；若达到门槛，再给唯一生产接线、目标回归、Debug 构建和 OnePlus 8T 资源打开/获取证据。
- 依赖是否满足？RF1440 已完成，工作树从 `57758e27` 干净开始；已有 `ManagedCommandVerificationBasis`、PRoot 探针和正向证据协调器可复用，不需要新增状态 Store。
- 红线：RF1510 只记载不修复；未知事实继续 PRoot；不迁移安装脚本中的 shell 校验，不修改资源卡，不按应用/资源/命令名特判。

## RF1510 验收

- 新增 `docs/architecture/runtime-compatibility-backlog.md`，以 FAST/NATIVE/PROOT 稳定编号保存三车道共 17 项明确不支持或尚未生产化边界。
- 每项分别记录已确认原因、当前兼容路线和未来候选；用户提出的“第二个全 PRoot Python”只作为后续组合方向记录，没有改变当前 Python、资源命令或 Provider。
- 总路由文档加入唯一总账入口；本叶没有修改代码、资源清单、运行资产、构建配置或设备状态，Markdown 差异检查通过。

## RF1550 开机与三问自检

- 目标是什么？依据 PLAYBOOK 的 RF1550，在不删除完整测试资产的前提下压缩每个叶子的默认执行集，并协调同机多个 Kite worktree 的 Gradle 重任务。
- 完成后拿什么证明？276 类静态清单、Quick/Stage/Full 实跑摘要、Quick 相对 1465 项/343 秒历史全量的比例、命名互斥锁双进程证据、最终 Full 与 Debug 构建。
- 依赖是否满足？RF1510 已完成；RF1520 三个 Debug 草稿文件已保存在 `stash@{0}: rf1520-managed-command-proof-draft`，当前工作树只允许 RF1550 文件变化。主线正在另一 worktree 运行 Agent 定向测试，本阶段不停止或干扰该进程。
- 红线：不按测试数量删覆盖，不把 Quick 冒充发布全量，不共享 worktree `build/`，不执行 `gradlew --stop` 杀死其他任务，不默认抢占任何 ADB 设备。

## RF1551 审计结果

- 当前 `app/src/test` 为 276 个 Kotlin 测试文件、约 1.85 MiB；文件名没有重复类组。RF1440 的 276 suites/1465 tests 是覆盖资产累积，不是同一测试被 Gradle 重复注册。
- 其中 Robolectric 测试类 103 个、读取源码/资产的静态合同类约 50 个、协程测试类约 22 个。按 `Contract/Protocol/Routing/Policy/Schema/Guard` 稳定后缀可选出 48 类，占总类数 17.4%，适合作为 Quick 候选。
- 各 worktree 的仓库 `.gradle`、模块 `build/` 和测试结果目录天然分开；共享的是用户级 Gradle 缓存/daemon、全局 Kotlin daemon 和设备/机器资源。检查时主线 wrapper、主线测试 worker、本支线单次 Gradle daemon 与长期 Kotlin daemon 同时存在，证明需要跨 worktree 协调而不是删除 build 目录。

## RF1552～RF1553 验收

- `run-kite-tests.ps1` 固定 Quick/Stage/Full 三档。Quick 由六类稳定职责后缀自动选取；Stage 未声明或声明不存在的测试模式会在 Gradle 前失败；Full 不接受自定义过滤。
- Quick：49 suites、254 tests、0 failure、0 error、0 skipped，墙钟 84.572 秒；相对本轮 Full 测试数减少 82.7%、墙钟减少 57.9%。
- Stage（Quick + `com.kite.app.platform.resources.*`）：55 suites、288 tests、零失败，墙钟 74.529 秒。它命中前一轮编译缓存，不能据此宣称 Stage 固有快于 Quick。
- Full：277 suites、1467 tests、0 failure、0 error、2 skipped，墙钟 201.151 秒；完整语义未改变。相对 RF1440 强制 `--rerun-tasks` 的约 343 秒减少 41.4%。
- 命名锁双进程探针中，第一进程持锁 3 秒，第二进程 `waitMs=2803` 后获得；1 秒超时探针 exit 1 并明确报告。所有临时输出位于忽略的 `local-artifacts/`。
- 后续故障注入暴露旧版包装器的窄窗口：外层调用器被终止后，已启动的 Gradle 子进程仍可能继续，而父进程持有的 mutex 已释放。现改由独立工作进程持锁；强制终止协调器后工作进程仍存活，第二探针 `waitMs=4964`、总墙钟 6885ms，确认实际任务结束前不会重叠。
- 加固后资源 Stage（Quick + `com.kite.app.platform.resources.*`）为 56 suites、289 tests、0 failure、0 error，墙钟 80.835 秒；同时验证新增 RF1520 Debug 合同能够正常编译，未再出现测试结果目录争写。
- 测试输出仍有既有 Robolectric SQLite CloseGuard 警告，但 JUnit 为零失败；它属于测试资源释放债务，不在本阶段以静默屏蔽处理。

## RF1554 父任务门

- `KiteTestProfileScriptContractTest` 两项合同进入 Quick，固定三档、25% 类数上限、Stage 范围校验、命名锁、禁止 `gradlew --stop` 和禁止删除缓存。
- 统一包装器完成 Debug 构建：60 个任务中 5 个执行、55 个 up-to-date，`BUILD SUCCESSFUL in 26s`。APK 为 241900484 bytes，SHA-256 `AD05EFA67345ABAD6AF4F2EE4D2D61A7FDFE7E2C27D17D536B04CC7A3AE9EA3C`；构建物未进入 Git。
- 范围审查：没有删除或排除任何测试源码；CI 的独立全量入口保持原样；没有改设备状态、资源卡、Provider、运行资产或生产业务代码。`references/toolchain.md` 已在本地更新为包装器入口，但该本机事实文件按仓库规则不进入 Git。
- RF1550 完成。下一步恢复 `rf1520-managed-command-proof-draft`，继续高频结构化只读验证；后续叶子使用 Stage，RF1500 父门才再次使用 Full。

## RF1520 正式调用面与收益门审计

- `AndroidResourceActionGateway.open()` 与 `install()`/`buildInstallPlan()` 均在用户动作的后台 preflight 调用同一 `reconcileInstalledResources()`；首次正向核对仍由 `AndroidResourceInstalledStateProbe` 创建静默 PRoot Recipe，现有原生文件身份只用于成功证据缓存。
- 固定 Debug 矩阵不接收 ADB 自定义命令、路径、资源 ID、轮数或环境参数，覆盖可执行、缺失、断链和无执行位四类事实，并在退出后按 `NOFOLLOW_LINKS` 核对夹具清理。
- OnePlus 8T 首轮九次对照：PRoot p50/p95 为 `106/107ms`，Android 文件证明为 `8966/22569us`，p50 减少约 91.5%；shell 零失败、无 ANR/FATAL。
- 当前 Android 身份将无执行位普通文件误判为存在，形成确定假阳性，因此本叶没有修改正式 Probe、Provider、资源或路由；结论仅为共同调用面与收益门成立。
- `docs/architecture/managed-command-native-proof.md` 已在生产改动前固定 RF1530 三轮确认门：零差异、原生 p95 不高于 30ms、p50 同时至少减少 50ms/50%，未知事实继续 PRoot。
- 下一恢复指针为 RF1530，只修通用文件可执行证明和失败关闭，不扩大到安装脚本、动态 PATH、View、别名、函数、版本或实际命令执行。

## RF1530 原生受管命令证明候选

- `ManagedCommandHostFileStamp` 新增明确的可执行事实；路径解析继续限制在普通容器 PATH 和受控宿主根，最终目标必须是普通文件并通过 Kite 真实 UID 的 `Files.isExecutable()`。
- 新增显式 `ResourceManagedCommandNativeProof`，只有完整身份和默认环境可构造；协调器不新增 Store，完整证明直接复用既有正向证据，混合请求仅把不完整资源交给原 PRoot Probe。
- 目标 Stage 覆盖 58 suites、303 tests，0 failure、0 error；包括完整证明零 PRoot、非执行/非默认拒绝、混合请求只探测 fallback、错配证明失败关闭以及既有 Warm Runner 身份回归。
- 第一版真机正确性通过，但原生 p95 `32.316ms` 超过预设 30ms，未放宽阈值。随后用一次 `readAttributes(NOFOLLOW_LINKS)` 代替每候选多次 stat，并重新开始三轮确认。
- OnePlus 8T 三轮 PRoot p50 为 `105/106/105ms`，原生 p50 为 `10.683/10.394/11.930ms`、p95 为 `14.210/15.194/28.269ms`，p50 减少 `89.8%/90.2%/88.6%`；三轮零差异、零 shell failure、零残留、无 ANR/FATAL。
- RF1530 完成。下一恢复指针为 RF1540：只做真实打开/获取链、Full、Debug 与范围审查；不再扩展能力边界。

## RF1540 go/no-go 与父任务门

- OnePlus 8T 覆盖安装候选 Debug 包后，从真实资源目录点击 OpenClaw“打开”；观测为 `resolved total=1, native=1, cached=0, fallback=0`，同一日志窗口没有 `resource-installed-state-probe`，页面进入 OpenClaw Agent 显示面并显示“可以开始新会话”。
- “获取”的 `buildInstallPlan()` 与“打开”复用同一 `reconcileInstalledResources()`。本轮用生产代码审查和自动测试覆盖获取侧，没有为了制造真机证据点击未获取资源、改变用户安装状态。
- 第一轮 Full 暴露观测日志直接依赖 Android `Log`，8 项协调器 JVM 测试因此失败；改为协调器接收纯函数观测出口、Android 组装层注入 Logger 后，目标测试通过。
- 最终 Full：279 suites、1473 tests、0 failure、0 error、2 skipped，JUnit 195.478 秒、墙钟 245.308 秒；真实打开窗口无新增 ANR/FATAL。
- 最终 Debug 构建成功，APK 为 248913164 bytes，SHA-256 `AF9936C58C2507340DA6B588EC1F523C2C5CE3C98B34609EC1A93BE157DC2EC2`；覆盖安装后再次实测仍为 `native=1/fallback=0` 并进入 OpenClaw 显示面，构建物未进入 Git。
- 生产范围审查：没有资源、命令或应用白名单；没有新 Store；动态 PATH、View、安装脚本内部校验、版本执行、别名和函数继续 PRoot。RF1500 以 go 收口，下一候选必须从兼容债务总账重新立项。

## RF1431 验收

- 新增固定 `PROOT_ACTIVE_RUNTIME_HOTSPOT` Debug 入口，不接受 ADB 参数；正式 PRoot、runtime descriptor、Provider、资源与 lane 均未改变。
- small-write 在 OnePlus 8T 九轮矩阵中，active no-telemetry 相对 stock 的 wall 中位数为 `232/140ms`（并发 4）与 `303/199ms`（并发 8）。关闭 `kf_procfs`、关闭 `mountinfo`、同时关闭以及强制 external loader 均未缩小差值，因此四项均不是主因。
- child-fanout 并发 4 的 wall 中位数为 active telemetry `214ms`、log-only `213ms`、每进程独立 log `215ms`、no-telemetry `123ms`、stock `118ms`。registry 与共享追加竞争被排除，热点落在每事件同步采集、JSON 格式化和写入总成本。
- child-fanout 并发 8 的五组均为 `231～235ms`，说明设备已进入吞吐平台，不能靠继续放大并发获得收益。
- 22 组、九轮全部语义成功、零残留。首次在应用完全后台触发时被 Android 拒绝普通 Service 并产生一次 Debug 进程 FATAL；探针现已捕获并报告 `requiresForeground=true`，有效矩阵在前台冷启动后重跑。
- 源码边界确认：正式 descriptor 指向 Termux PRoot `d30b988` 与 v23 block-view patch；现有 KFShell 候选工作树含用户脏改，RF1432 只能在 `local-artifacts/` 隔离重建。

## RF1432 开机与三问自检

- 目标是什么？重建可复现的 v23 Debug 候选，并在不减少 lifecycle 事件、强身份、退出事实和 registry 的前提下减少每事件同步开销。
- 完成后拿什么证明？源与 patch 身份、独立二进制、事件/registry 逐项对照、RF1431 性能复算；正式资产在 RF1440 前保持零差异。
- 依赖是否满足？RF1431 已排除扩展、loader 和文件竞争伪因；`d30b988`、正式 patch 与 NDK 构建链均可读取，但必须避开 KFShell 脏工作树。

## RF1432～RF1433 可复现源码、候选与消融结果

- `scripts/build-proot-runtime-ablation.ps1` 从 `d30b98846cfdf0923bea26956922a2acf9ef23ae` 归档源码，严格按 lifecycle、procfs、transaction、protection、view、block-view 顺序应用正式 patch，并把所有产物限制在 `local-artifacts/`。脚本实跑得到 356864-byte、SHA-256 `0A465CE2F5E3DCD80F801EF500478E4932248806EDC86CE5C9B0918D60C604BC`，与正式 v23 逐字节一致。
- lifecycle 候选依次验证了单缓冲区单次 write、持久 `O_APPEND` fd，以及 registry 活跃计数延迟目录扫描。前两项在 4/8 并发均与 active 持平；第三项 110 个 session、5404 个事件的 schema、事件签名和总字节完全一致，但 child-fanout 为 `219/221ms`（4 并发）与 `238/239ms`（8 并发），仍无收益。profile 显示 registry 约 `249µs/event`，说明成本来自每事件原子快照总路径，不是单次 open、JSON 编码或尾部目录扫描。
- 固定 `PROOT_PATCH_ABLATION_BENCHMARK` 不接受外部参数，逐层比较无 patch、lifecycle、procfs、transaction、protection、view、完整 v23、编译时 unbundled loader、NDK 28 和 stock。三套九轮 OnePlus 8T 矩阵全部结果正确、零残留。
- 4 并发受设备升频/调度影响出现两簇样本，但各同源层中位数均处于 `132～160ms`，stock 为 `136～139ms`，不存在补丁台阶。8 并发更稳定：无 patch 到完整 v23 均为 `299～317ms`，stock 为 `204～209ms`；unbundled 为 `305/314ms`，NDK 28 为 `314ms`。因此六个 Kite patch、embedded loader 与 NDK 26 不是该差异的根因。
- stock 只标识为来源未知的 PRoot 5.1.0 资产，缺少 lifecycle、active registry、保护和 View 能力，不能因基准更快而升级为正式 runtime。剩余差异属于 `d30b988` Termux 源码/库存未知构建代次之间的共同边界；在没有同源、同能力且完整语义的候选前，不做不可回退的二进制替换。

## RF1440 父任务门

- go/no-go：no-go。正式 `proot-kf-lifecycle-arm64`、`proot-runtime.json`、六个 patch、Provider、资源清单和运行 lane 均保持不变；Git 只保留 Debug 诊断入口、可复现构建脚本与长期结论。
- OnePlus 8T 最终消融 20 组、九轮全部成功、零残留；logcat 无匹配 ANR/FATAL。后台直接触发 Service 的既有 Debug 限制仍以 `requiresForeground=true` 显式报告，不进入生产路径。
- 强制全量单测汇总为 276 个 suite、1465 tests、0 failure、0 error、2 skipped。强制 Debug 构建通过；APK 241563604 bytes，SHA-256 `81FF9937CC2333F564ABB1ABA892AD05EA5D55FCB4991B2B439A9AA5CEEC5986`。最终 logcat 无匹配 ANR/FATAL，设备无残留 benchmark PRoot 进程。

## RF110 开机与三问自检

- 目标是什么？按 `PLAYBOOK.md` 建立三车道总架构、Provider 文档、Node 风险索引和任务验收合同。
- 完成标准是什么？文档互链、已验证与待验证事实分离、首个特例和回退门明确，不冒充 Python/原生能力已实现。
- 依赖是否满足？满足。当前分支从干净 `main@8223ba0` 建立；原主工作树的 `AGENTS.md` 和 Agent 模型库改动未带入。

## RF510 开机与三问自检

- 目标是什么？按 `PLAYBOOK.md` 把实际 coordinator/admission/warm pool 状态投影到正式 RuntimeHealth，明确区别现有规划推演结果。
- 完成标准是什么？字段来自同一实际状态源，读取不创建 PRoot，不记录命令/路径/用户输入，并有单测与 Debug 构建证据。
- 依赖是否满足？满足。RF440 已完成；分支工作树干净，实际快照已存在且当前仅由 Debug 探针消费。

## RF520 开机与三问自检

- 目标是什么？按 `PLAYBOOK.md` 让第一份健康快照前的 admission 使用已有 host 可用内存压力事实，关闭默认均衡档的假性单并发。
- 完成标准是什么？正常内存为 2，缺信号/高压/临界仍为 1；正式 RuntimeHealth 到达后覆盖 bootstrap，真机能读到来源和有效上限。
- 依赖是否满足？满足。RF510 已建立 actual 正式投影；现有 `RuntimePressureGuard` 已能从 `/proc/meminfo` 计算 hostAvailableLevel，无需新增传感器或状态源。

## RF530 开机与三问自检

- 目标是什么？从同一个 `BoundedProotTaskExecutor` 聚合低基数 route/result/lane 与 queue/execute/total 时延，补足仅凭 admission 总量无法判断真实收益和失败位置的问题。
- 完成后拿什么证明？单测覆盖每条 route、结果分类、时延边界和并发更新；RuntimeHealth 只输出枚举与数字，读取不执行任务、不包含 argv/cwd/env/output/owner。
- 依赖是否满足？满足。RF510 已提供 actual 正式投影；RF520 已确保首个任务前也有明确策略来源。现有执行结果已含 route 与 job duration，下一步只补统一计时和聚合，不改变任务结果拥有者。

## RF540 开机与三问自检

- 目标是什么？把高频、代码自有的 Supervisord update/status/日志尾部采集从每次独立 PRoot 迁入有界 Runner，同时保持原解析与错误边界。
- 完成后拿什么证明？helper 内容和版本固定、无外部命令参数；调用方只传结构化 helper argv，声明 `SERVICE/SHARED_WRITE`、稳定 owner 和有界 timeout/output；单测与真机冷温结果语义一致且无残留任务。
- 依赖是否满足？满足。RF530 已能观察 route/result/时延；现有 `WorkspaceBuildSupport` 已拥有受管 helper 写入位置，`SupervisordServiceHealthStore` 的 shell 内容完全由代码持有，可先固化为 helper 而不接收用户 payload。

## RF550 开机与三问自检

- 目标是什么？联合证明 RF510～RF540 没有破坏既有 PRoot/资源/Agent/运行状态合同，并给 RF600 长生命周期 lease 明确 go/no-go，而不是顺手迁移长期进程。
- 完成后拿什么证明？相关回归、强制全量单测、强制 Debug 构建、OnePlus 8T 冷启动/温复用/压力收缩/空闲行为/服务健康链与 ANR/FATAL 检查；Git 工作树干净、每个叶子提交可独立回退。
- 依赖是否满足？满足。RF510～RF540 均已独立实现和验证；Node/Python 历史矩阵仍冻结，本门只验证本阶段新增合同和跨模块回归。

## RF650 开机与三问自检

- 目标是什么？联合审查 RF610～RF640 是否真正构成长生命周期 owner 的合同闭环，并明确后台服务、终端、Agent 的生产迁移边界。
- 完成后拿什么证明？强制联合测试、Debug 构建、静态生产引用与敏感字段检查、提交边界复核；形成后台服务样板 go/no-go 和下一任务入口。本门不重跑冻结的 Node/Python 性能矩阵，也不伪造真机长期进程证据。
- 依赖是否满足？满足。四个叶子任务均已独立测试构建并提交前三项；RF640 等待本叶提交后进入父门，所有实现仍为无生产装配的合同/模拟器。

## RF720 开机与三问自检

- 目标是什么？把 RF510/RF530 的实际 coordinator 与有界任务遥测、可信内存压力、前后台和 thermal 可用性归一成一次评估输入，明确何时只能保持/降级、何时连候选升档都不能给。
- 完成后拿什么证明？未知、陈旧、零样本和冲突信号失败关闭；HIGH/CRITICAL 或显著失败给降级建议但不强杀任务；没有可信 thermal 时永远不产生升档建议；纯函数不改 coordinator。
- 依赖是否满足？满足。RF710 已证明 tracee overlay 只能当 guard，正式 1/2/4 来自单一 tunings；RF530 已有固定 lane/route/result 与 queue/execute/total 聚合，可作为实际任务证据。

## RF720 验收

- 新增 `RuntimeProotAdaptiveSignalGate`，只接收 RF510 actual tuning、RF530 两次累计遥测读数、RF710 tracee guard 与显式 thermal evidence；没有新 Store、线程、计时器或生产装配。
- 累计遥测必须满足唯一 key、非负值、每阶段桶数等于 entry count、前后计数单调；差量窗口固定为 30 秒至 10 分钟且最多陈旧 2 分钟，P95 由差量桶计算。
- 正式候选只允许内置 1/2/4 档。HIGH/CRITICAL 内存、可信 hot 或至少 5 样本且失败率达到 10% 时建议降一级；最低档只保持，不强杀已运行任务。
- 自动升档候选要求：正式 RuntimeHealth source、NORMAL 内存、前台、20 个以上差量样本、失败率低、P95 不超过 1 秒、RF710 guard ready、60 秒内可信 thermal normal。当前仓库没有可靠 thermal source，因此真实生产环境仍然不可能自动升档。
- 目标回归覆盖 `RuntimeProotAdaptiveSignalGateTest`、`RuntimeProotCalibrationAlignmentTest`、`BoundedProotTaskTelemetryTest`：3 个 suite、21 tests、0 failure、0 error、0 skipped；Debug 编译随测试成功。
- 下一恢复指针进入 RF730；它只能消费 RF720 的窗口 action，建立连续确认/冷却/回滚模拟器，不能写正式策略文件。

## RF730 验收

- 新增 `RuntimeProotAdaptiveHysteresis` 纯状态机，actual 档位始终由调用方传入；状态只保存连续窗口数、待应用相邻目标、冷却期限和相邻 rollback target，不成为第二个正式策略源。
- 默认三次连续健康窗口才发出一次升一级建议；HOLD 清空连续证据，待应用升档遇到坏窗口可取消。正式应用由 actual 后续变化确认，状态机本身不写档位。
- 升档确认后进入 10 分钟冷却。内存 HIGH/CRITICAL 或可信 thermal hot 仍可立即建议降一级；失败率坏窗口使用两个连续窗口预算，健康/HOLD 会打断预算。
- 恢复时 schema 损坏、actual 与 checkpoint 不同、pending 已被外部应用，均只 reset/rebase 并启动冷却；不会根据旧 streak 或 pending 再跳一级。所有输入窗口还要再次验证 scope、actual 和相邻目标。
- 目标回归覆盖 RF720/RF730：2 个 suite、18 tests、0 failure、0 error、0 skipped；Debug 编译随测试成功。
- 下一恢复指针进入 RF740，只投影固定低基数 planned/actual 差异，不能把 recommendation 冒充已生效策略。

## RF740 验收

- 新增 `RuntimeProotAdaptivePlanningProjector`，只消费调用方提供的 actual tuning、校准、thermal、窗口和迟滞结果；没有回读 coordinator/collector、文件扫描、Store、线程或计时器。
- 固定输出 actual reference 与 planned 两个明确 scope。actual 仍是 `mirror_of_proot_actual_scheduler`，建议始终是 `planned_not_production`，并输出 `changes_coordinator=false` 与 `recommendation_is_not_actual_policy`。
- actual、窗口和迟滞档位必须一致；窗口/建议只能相邻移动，计数、失败率、state schema、rollback target 和 pending target 都再次校验。任何矛盾只输出 `CONTRACT_MISMATCH`，target 回到 actual。
- 健康文本只含固定枚举、布尔和数字；测试明确拒绝 owner/lease/PID/process start/argv/cwd/command/session/resource 身份字段。
- RF740 目标 suite 5 tests、0 failure、0 error、0 skipped；第一次编译发现投影对象缺少机器可断言的 `changesCoordinator` 字段，补齐后原命令通过。
- 下一恢复指针进入 RF750，执行 RF700 联合回归与构建，分别给自动降档、自动升档生产结论；无可靠 thermal 时不得把升档改成 go。

## RF750 验收

- 强制 `:app:testDebugUnitTest --rerun-tasks` 第二次完整执行成功；JUnit XML 汇总 262 个 suite、1404 tests、0 failure、0 error、2 skipped。第一次重跑被前一条超时中断的 Gradle 进程锁住 `terminal-view-local` classes.jar，正常 `gradlew --stop` 后原命令通过，不计为代码失败。
- 强制 `:app:assembleDebug --rerun-tasks` 成功。本地 APK 241169520 bytes，SHA-256 `BF532BEFDE98FBC18F5446E23E57090622407CE7A585912A1D5482A75026CA53`；构建物未进入 Git。
- 静态引用确认 RF710～RF740 新类型只在四个自适应实现文件及目标测试内互相引用，没有接入 `RuntimeHealthStore`、正式 coordinator、页面、资源或后台调度；本阶段没有用户可见/生产行为变化，因此未覆盖安装 OnePlus 8T。
- 生产结论分开：现有 `ProotJobAdmissionController` 已按 HIGH/CRITICAL 压力把 effective max 收缩为 1，这条实际安全降档继续 go；按任务失败率修改档位缺少“失败由并发造成”的因果证据，no-go；自动升档缺少可信 thermal source，no-go。
- RF700 完成。下一阶段 RF800 只沿 RF650 已允许的后台强身份桥接推进；不借机迁移终端/Agent，不重跑冻结的 Node/Python 性能矩阵。

## RF810 开机与三问自检

- 目标是什么？把后台 runtime 从 PID-only 恢复推进到可证明的 `(hostPid, processStartTicks)` 身份前，完整核清记录、真实创建/attach/stop、持久化和观察链。
- 完成后拿什么证明？生产引用图、字段来源、旧记录兼容、停止确认和重复实例风险逐项落到代码位置；本叶只审计与定合同，不改生产行为。
- 依赖是否满足？满足。RF650 已明确后台是唯一可继续准备的长期 owner 类别，RF750 已完成且没有把自适应预研接入生产；终端和 Agent 继续 no-go。

## RF810 验收

- 正式架构审计写入 `docs/architecture/background-runtime-strong-identity.md`；生产链为 single-flight → ProcessBuilder → handle/PID → Registry，应用重启后则由 persisted PID + container-like + command token/statusCommand 弱探测恢复。
- `HostProcessInspector` 已解析 `/proc/<pid>/stat`，但只取 PGID、SID、CPU ticks，未取字段 22 starttime；`HostProcessRecord` 没有进程代次，`HostProcessSnapshot` 没有 boot identity。
- 发现跨设备 reboot 边界：`(pid,startTicks)` 只在同一 boot 内唯一。后台记录跨 reboot 持久化，所以 RF820 必须保存 `(bootId,pid,startTicks)`；boot 相同后才可向 RF610 提供 PID+代次。
- 停止链当前先用内存 `stoppingRuntimeIds`，终止并无条件写 STOPPED 后才持久化 expected stop；终止结果不控制 STOPPED。RF830 必须改为先持久化意图、再精确校验代次、观察退出后确认并释放 lease。
- RF810 只修改正式架构/任务文档，没有代码和生产行为变化，无需构建或真机。下一恢复指针进入 RF820；SERVICE、Host lane、终端和 Agent 均不进入本桥接。

## RF820 验收

- `HostProcessRecord` 增加 nullable `processStartTicks`，`HostProcessSnapshot` 增加 nullable boot ID；同一 `/proc` 读取链按 stat 字段 22 解析 starttime。comm 含右括号时从最后一个右括号后定位，避免错误拆字段。
- `HostProcessIdentityObservation` 强制规范 boot UUID、正 PID 和正 start ticks；snapshot 只从 `appProcess` 生成。`ps -A` fallback 没有 start ticks，因此自然保持 identity unavailable。
- `BackgroundRuntimeRecord` 在尾部追加可空 `processBootId/processStartTicks`，不破坏既有位置参数；JSON 缺字段向后兼容。派生身份要求三字段完整有效，坏/部分数据失败关闭。
- `updateStatus` 通过统一 `withProcessPid` 保证同 PID 保留、换 PID/清 PID 同步清身份；内置定义刷新与 upsertDefinition 保留同一活动身份；新增 Registry 原子写入口只接受活动记录的精确 PID。
- 目标回归 4 个 suite、18 tests、0 failure、0 error、0 skipped；首次新 JSON 测试因本地 JVM `org.json` stub 失败，改用项目既有 Robolectric 方式后原范围通过。Debug 构建成功。
- RF820 尚未由生产 start/attach/stop 调用新身份入口，行为保持不变。下一恢复指针 RF830，先建立纯恢复/停止决策和退出确认，再接 Host。

## RF831 验收

- 新增纯 `BackgroundRuntimeProcessIdentityPolicy`，恢复与停止共用一份 boot/PID/start ticks 比较结果；不读取 `/proc`、Registry 或命令，不 attach、不发信号、不创建进程。
- 只有 `EXACT_GENERATION` 可恢复既有实例或向目标 PID 发信号；同 PID 新代次、boot 变化、旧记录缺身份和 `ps -A` 观察缺代次均失败关闭。
- PID 已不存在时可确认没有可恢复实例；PID 复用或 boot 变化时，显式停止只能确认原代次已退出，不向当前 PID 发信号。旧记录或观察身份不完整时保持 review，不伪造退出确认。
- 目标 suite 8 tests 全部通过；本叶没有生产接线和用户可见行为，因此不构建/安装真机。下一恢复指针为 RF832。

## RF832 验收

- `HostProcessInspector.readAppProcessIdentity` 在创建后只读目标 `/proc/<pid>`、当前应用 UID、stat start ticks 与 boot ID，不为了捕获单个身份扫描整棵 `/proc`；任一字段不可得就不生成部分身份。
- `startProcessRuntime` 在 `RUNNING/pid` 发布后通过 Registry 原子入口写身份；现有本地 handle 复用路径也会补采集。快速退出仍由既有 lazy monitor 顺序收敛。
- 应用重启后的 `resolveRuntimeHostPid` 先用 RF831 比较 persisted/observed 强身份，只有 `ATTACH_EXACT_PROCESS` 才继续通过 container/owner token 门；旧 PID、boot 变化、PID 复用与 fallback 观察均不 attach。
- statusCommand 仍可说明服务响应，但没有精确 external PID 时不再保留 `record.pid`，因此不会把服务健康冒充为 owner 进程身份。
- 目标 4 suites、18 tests、0 failure、0 error、0 skipped；Debug 构建通过。RF832 尚未改变停止顺序，下一恢复指针 RF833。

## RF833 验收

- `stopProcessRuntime` 先写 manual/expected stop，再读取最新记录并决定操作；本地 handle 直接操作所拥有的 `Process`，重启后的 detached PID 必须同时满足强身份与 owner token，且 TERM/KILL 每一步前再次窄读代次。
- 终止器返回未退出、旧记录缺身份或观察身份不可用时，记录保持活动并显示“等待退出确认/身份待确认”；不写 STOPPED、不清身份、不释放准入容量，也不启动替代进程。
- handle 退出、强身份 guard 证明原代次消失或 snapshot 证明原 boot/代次已不存在后，统一入口才写 STOPPED、清身份、更新健康并释放容量。普通 stale reconciler 遇到强身份记录只委托身份感知 Host，不能抢先释放。
- 活动状态刷新在同 PID 下保留已持久化停止意图；显式 expected stop 优先于 core 自动恢复。新一轮 STARTING/换 PID 仍清旧意图与身份，不把 review 变成平行状态源。
- 目标 6 suites、23 tests、0 failure、0 error、0 skipped；Debug 构建通过。终端和 Agent 无改动。
- 已记录内核边界：detached 路径没有 pidfd，虽在每次信号前重验 start ticks，最终 `/proc` 复核到 `kill(pid)` 之间仍有极小 TOCTOU；RF840 必须据设备能力给严格 go 或 best-effort/no-go。

## RF834 验收

- 联合回归前的 OnePlus 8T 正式链证明 OpenClaw Host Node 后台进程持久化的 boot ID 与 `/proc/sys/kernel/random/boot_id` 一致，PID 18963 的 start ticks 与 `/proc/18963/stat` 第 22 字段精确一致；应用进程被外部结束后旧代次返回 `PROCESS_NOT_FOUND`，只创建一个替代实例。
- 显式停止 OpenClaw 后，记录进入 `STOPPED_EXPECTED`，PID/boot/start ticks 同步清空；随后重复启动同一 runtimeId 保持同一 PID 19613 和同一代次 55454791，没有第二实例。
- 首轮 PRoot worker 反例发现只销毁 wrapper 根 PID 会残留 `proot -> bash -> sleep` 三层 owner 树，并因子进程继承日志管道阻塞 monitor。修复改为按实际 `proot_shell` 车道先调用通用 `ProotOwnerProcessTerminator`；不读取资源 ID、应用名或 worker kind。
- 首次修复 APK 上 worker2 从 PID 20502、start ticks 55482454 启动并健康；停止结果为 `CONFIRMED/owner_stably_silent_after_identity_probe`，诊断 `trackedBefore=3 remaining=0 orphan=0 zombie=0`，记录确认 STOPPED 后才清身份与容量。
- 强制全量回归为 268 个 suite、1429 tests、0 failure、0 error、2 skipped；最终强制 Debug 构建通过。本地 APK 为 241,218,672 bytes，SHA-256 `FBC4AC8B1F49E75F3D6BD58788086C45A5D7F7D446DF40BC78CC36803FD60B79`，构建物未进入 Git。
- 最终 APK 覆盖安装后再次走生产 start/stop：worker2 以 PID 21399、start ticks 55557872、`proot_shell` 健康启动；停止再次得到 `CONFIRMED` 与 `trackedBefore=3 remaining=0 orphan=0 zombie=0`，记录强身份清空并进入 `STOPPED_EXPECTED`。本轮无匹配 FATAL/ANR。Node/Python 冻结性能矩阵未运行。

## RF840 生产类别门

- 只读引用图确认 RF610～RF640 的 owner transition、admission simulator、recovery planner 和 planning health 只在该组模型内互相引用；没有生产调用方，scope 仍为 `planned_not_production`。
- 短任务实际准入只由 `WarmProotExecutionCoordinator` 内的 `ProotJobAdmissionController` 计数。把模拟器另行实例化为长期 controller 会形成两个容量事实源，总 PRoot 运行数可超过同一 1/2/4 档位，违反父任务的“不崩溃前提下榨干性能”。
- 后台记录已能持久化 actual lane 和 boot/PID/start ticks，但没有进程创建前的 lease generation/phase；从 RUNNING 反推会漏算 STARTING，从 `lastLaunchLane` 预判又会把上一次实际车道当本次选择。
- 正式健康面只有短任务 `proot_actual_*`，长期 `proot_long_planned_*` 仍明确未生产。当前若直接输出 actual，会把未受统一仲裁的后台数冒充生产容量事实。
- 结论为 no-go，RF840 不修改生产准入。解阻工作拆为 RF910～RF950：统一容量快照、同记录 provisional lease、生命周期桥、actual schema、真机故障矩阵；终端和 Agent 继续不迁移。
- 新增 RF840 边界合同，禁止后台 Host 或短任务 coordinator 偷接 planned simulator、禁止在缺 lease 字段时冒充生产接入；长期状态机、容量、恢复、投影和边界共 5 suites、35 tests 全部通过。因无生产代码变化，本叶不重复构建或真机安装。

## RF850 / RF800 父任务验收

- 最终强制全量回归为 269 个 suite、1431 tests、0 failure、0 error、2 skipped；强制 Debug 构建成功。
- 最终本地 APK 为 241,218,672 bytes，SHA-256 `FBC4AC8B1F49E75F3D6BD58788086C45A5D7F7D446DF40BC78CC36803FD60B79`；该构建物已在 RF834 生产 stop/start 复验同一生产代码，构建物未进入 Git。
- RF800 从 `ab716c4` 之后共 7 个提交、20 个文件，只修改后台 runtime/强身份、对应测试和本任务正式文档；Agent、终端与 runsurface 没有净变化。
- OnePlus 8T 已证明 Host Node 强身份、外死单实例恢复、重复启动、显式停止，以及 PRoot owner 三层树完整收敛；没有把设备 reboot 或无 pidfd 的 detached 路径包装成已验证的内核原子保证。
- 后台强身份桥接完成，长期 lease 生产接入因统一容量事实缺失明确 no-go；下一恢复点为 RF910，先建立短任务与长期 owner 的统一容量快照，不修改后台生产启动。
- 全阶段没有重跑 Node/Python 冻结性能矩阵，没有迁移终端或 Agent，没有推送远端。

## RF910 统一容量快照与不变量

- `ProotJobAdmissionSnapshot` 现在从 controller 同一把锁内的 active/pending 集合生成逐 lane 计数；总数与 lane sum 必须一致，统一层不扫描任务或猜测 lane。
- 新增纯 `UnifiedProotCapacityProjection`，把短任务 actual snapshot 与调用方提供的长期 lease 记录合并。长期 ADMITTED、STARTING、RUNNING、STOPPING 和 ORPHAN_REVIEW 全部持有容量，REQUESTED 只排队，RELEASED 历史不参与冲突。
- 压力收缩造成既有 holder 超过新上限时输出 OVERCOMMITTED、剩余容量归零，但不产生驱逐动作；独占维护与其他活动任务并存、同 owner 多个未释放代次或进程身份冲突均输出 CONTRACT_MISMATCH。
- 快照固定为 `unified_contract_not_production`，只含低基数枚举、布尔和计数；没有 ownerId、leaseId、PID、命令、路径、Store、Context 或 ProcessBuilder，也没有后台生产引用。
- 首轮编译错误来自测试错误消息 nullable，修正后执行；第二轮两个失败是测试把 RUNNING 误算 queued、把聚合 identity count 误判为身份泄漏，修正断言并增加 RELEASED 同 owner 旧代次反例。最终目标回归 21 项、0 failure、0 error、0 skipped；强制 Debug 产物刷新后再以非强制构建取得 exitCode=0，下一恢复点 RF920。

## RF920 后台 provisional lease 持久化

- `BackgroundRuntimeRecord` 新增 `longLivedProotLeaseGeneration/Phase/UpdatedAt` 三个最小字段；phase 以内部枚举名持久化，避免把内部状态机扩大成公共模型类型。owner 继续使用同一记录 id，强身份继续使用既有 PID/boot/start ticks。
- 新增纯检查点策略和 Registry 原子 begin/transition：实际 controller 未来给出 generation 后，可以在进程创建前一次写入 `lastLaunchLane=proot_shell` 与 `STARTING`；后续转换必须命中 expected generation/phase 且时间不倒退。
- 三字段全缺失的旧 JSON 仍为 absent；部分字段、未知 phase、非 PROCESS 活跃 lease、Host/PRoot 路由冲突均保留为 malformed 并拒绝覆盖，不能因解析失败制造假空闲。RELEASED 代次保留，只有更高 generation 能再次 begin。
- 内置定义和资源定义刷新显式保留三个字段；`BackgroundRuntimeHost` 仍没有 begin/transition 调用，规划模拟器仍未装配，RF920 不改变生产启动、停止或健康行为。
- 第一轮联合目标回归 16 项中 2 项失败，原因是新 JSON 测试遗漏 Robolectric runner，并非产品逻辑失败；补齐测试环境后 16 项全通过，新增检查点 suite 5 项再次单独通过。下一恢复点 RF930。

## RF930 启动、恢复与停止桥

- `WarmProotExecutionCoordinator` 现在让有界短任务和后台长期 owner 共用同一个 `ProotJobAdmissionController`；新增的 owner registry 只持有 actual 容量句柄，不复制后台命令、状态、PID 或强身份。
- 通用后台 PROCESS 只有在本次实际路由为 `proot_shell` 时才准入。actual lease 与同记录 `STARTING` 检查点均发生在唯一 `ProcessBuilder.start()` 之前；Host Node 不占 PRoot 容量，代码不读取资源 ID、应用名或命令名做类别特判。
- 控制面恢复会导入持久化 holder。缩档后的既有 holder 可以形成 overcommitted，但不被驱逐；损坏检查点、恢复冲突和未知代次通过同一 actual controller 阻止新准入，不能制造假空闲。
- 创建成功后只有取得 boot/PID/start ticks 才把 lease 转为 RUNNING；创建前失败转 RELEASED。代码复核发现“进程创建后、强身份落盘前快速退出”会滞留 STARTING，现已改为 ORPHAN_REVIEW 并增加反例测试，仍需 owner 收敛证据才能释放。
- 显式停止先转 STOPPING，再由 `ProotOwnerProcessTerminator` 收敛完整 owner 树；只有 settled 且强身份终态成立才转 RELEASED 并关闭 actual lease。普通 stale reconciler 遇到未释放长期 lease 时交给身份感知 Host，不提前清记录。
- 目标回归先覆盖 6 suites、35 tests，补漏后再强制执行 4 个核心 suites、30 tests；两轮均为 0 failure、0 error、0 skipped。补漏后强制 Debug 构建通过，本地 APK 241,284,208 bytes，SHA-256 `5C4929F6F2647D8C575CA391238FCFDC24A5E902FB18D58A50DAE26BA80B4247`，已覆盖安装到 OnePlus 8T，构建物未进入 Git。
- OnePlus 8T 首轮通用受控 PRoot PROCESS 以 PID 24042、boot identity 和 start ticks 55954167 进入 generation 1/RUNNING；重复 start 的 PID、generation、start ticks 和进程数均不变。补漏后的最终 APK 再次从 RELEASED 启动为 PID 24741、generation 2、start ticks 56000069/RUNNING，并显式停止为 STOPPED/RELEASED；两轮均得到 `CONFIRMED/owner_stably_silent_after_identity_probe` 与 `trackedBefore=2 remaining=0 orphan=0 zombie=0`，强身份清空且无匹配 FATAL/ANR。
- RF930 完成，下一恢复点 RF940。Node/Python 冻结性能矩阵未运行，终端和 Agent 未迁移。

## RF940 actual 健康与迁移门

- `ProotJobAdmissionSnapshot` 在 controller 同一把锁内区分 `MANAGED_OWNER` 活动/排队数，避免读取 managed registry 与 admission 两份快照后再相减产生瞬时矛盾；`restoreActive` 也只接受 managed owner，因此恢复累计的含义稳定。
- 现有 `proot_actual_active_jobs/queued_jobs` 恢复为只表示有界短任务。新增 `proot_long_actual_*` 表示长期 owner actual，`proot_unified_actual_*` 表示短、长、总量、有效上限、剩余容量与 READY/FULL/OVERCOMMITTED/CONTRACT_MISMATCH。
- actual 投影只消费不可变 admission snapshot；字段只含固定 schema/source/scope、枚举和数字，不输出 ownerId、leaseId、PID、代次、命令、路径、资源、Agent 或 session。`proot_long_planned_*` 不改名、不接入该投影。
- 首轮目标回归 31 项中 1 项失败，原因是隐私断言把旧合法聚合字段 `warm_session_total` 的单词 session 整体禁止；修正为禁止真实动态身份值后，同一 5 suites、31 tests 全部通过，0 failure、0 error、0 skipped。
- 强制 Debug 构建通过。本地 APK 241,284,208 bytes，SHA-256 `2DDD5D2ED577F35F022806BD2AE32B28EA57498332CE093AA972B5660E54DCAA`，已覆盖安装 OnePlus 8T，构建物未进入 Git。
- 真机空闲输出：短任务 0、长期 0、统一总量 0、有效上限 2、剩余 2。受控 PRoot PROCESS generation 3/RUNNING 时：短任务仍为 0、长期 1、统一总量 1、剩余 1；停止为 RELEASED 并经过既有 10 秒健康面写入节流后，长期/总量回 0、剩余回 2。Crash buffer 为空。
- RF940 完成，下一恢复点 RF950。没有迁移终端、Agent、资源或命令分类，也没有重复 Node/Python 性能矩阵。

## RF950 真机故障矩阵与生产开关

- 新增单一 `BackgroundManagedProotProductionGate`，在 actual 准入和唯一 `ProcessBuilder.start()` 前检查；开关只表达后台通用 PRoot PROCESS 类别，不读取资源、命令、应用、Agent 或 runtime id。正式健康面输出固定 schema/state/reason。
- Debug 固定矩阵不接收命令、路径、档位或并发参数。OnePlus 8T 实际通过 6 个 case：PID 复用与 boot 改变均 review 且零进程创建；低功耗/均衡/高性能分别封顶 1/2/4；均衡档短任务 1 + 长期 owner 1 共享总容量 2；高性能档在 HIGH 压力下收缩到 1，已有 2 个 holder 保留为 OVERCOMMITTED，新准入被拒绝。
- 应用 force-stop 后原 PRoot 树消失；重启恢复 generation 1 为 ORPHAN_REVIEW、长期 actual=1，重复 START 不创建第二进程或第二 lease。定位并修复终态 STOPPED 记录因幂等短路无法显式释放孤儿 lease 的通用缺口；修复后显式 STOP 转为 RELEASED、actual=0。
- 正常恢复再次启动 generation 2，PID 28832 与子进程 28835 形成两层 owner 树；停止诊断为 `trackedBefore=2 remaining=0 orphan=0 zombie=0`，记录清 PID/强身份并转 RELEASED。同 UID 精确外死 generation 4 后转 ORPHAN_REVIEW、actual 保持 1，显式 STOP 后同样 RELEASED/actual=0。普通 shell UID 的无权限 kill 明确未计入证据。
- 强制全量命令首轮外层等待超时后仍在后台完成；误并发的第二轮只因争用 `processDebugResources/R.jar` 失败，不计为代码失败。最终 JUnit XML 汇总 275 suites、1460 tests、0 failure、0 error、2 skipped；随后独立 Debug 构建 exitCode=0。最终 APK 248,084,172 bytes，SHA-256 `DF5D147AE0BC9BFDE8929C9669823E46268CD657F17B944E72EAAF8FBD8B0D40`，已覆盖安装 OnePlus 8T，构建物未进入 Git。
- 最终 APK 上固定矩阵 6/6 通过，日志无匹配 FATAL/ANR。RF900 完成；Node/Python 冻结性能矩阵未重跑，终端与 Agent 未迁移。

## RF1010 饥饿反例与余量合同

- 审计 actual controller 后确认：优先级只选择当前可推进的 waiter，不能从长期 holder 手中产生空位。均衡档两个 managed owner 或高性能档四个 managed owner 可以无限期占满总量，后到交互任务只能超时。
- 新合同只限制新 `MANAGED_OWNER`：低功耗 1/1、均衡 1/2、高性能 3/4，给非长期任务保留至多一个可用位置；低功耗只有一个物理名额，明确不承诺并发。恢复和压力缩档不驱逐 holder，共享写屏障保持原语义。
- 下一恢复点 RF1020：在同一 controller/同一锁内实现上限、blocked reason 与 waiter 绕行，并先用并发反例证明，不接新类别。

## RF1020 actual 仲裁与公平队列

- `ProotJobAdmissionController` 在同一锁内按 `MANAGED_OWNER` 计算长期活动数。总量大于 1 时，新长期 owner 上限为 `globalMax-1`；总量为 1 时上限仍为 1。达到上限固定返回 `admission_managed_owner_headroom_timeout`。
- waiter 选择保留共享写队首屏障；普通 managed owner 达上限时不再阻塞后面的可运行短任务。没有建立第二个 controller、队列或状态源，恢复导入仍可 overcommit 且不驱逐 holder。
- 新增三个反例：均衡档第二长期 owner 先排队、后到 INTERACTIVE 仍准入；高性能三个长期 owner 后第四个被拒、短任务占第 4 位；低功耗一个长期 owner 后短任务仍按全局容量拒绝，不伪造第二名额。
- 强制目标 suite `ProotJobAdmissionControllerTest` 共 21 tests，0 failure、0 error、0 skipped。下一恢复点 RF1030，更新 actual 低基数健康与固定真机矩阵。

## RF1030 actual 健康与固定矩阵

- `ProotJobAdmissionSnapshot` 从唯一 admission 同锁发布 `managedOwnerAdmissionMax`；正式 actual v2 健康据此输出长期上限、剩余长期名额、短任务余量容量和余量保护状态。字段仍只含固定 schema、枚举、布尔和数字，不含 owner、PID、命令、路径或业务身份。
- Debug 固定矩阵已改成余量合同：低功耗实际 1/1；均衡长期 1 + 真实短任务 1；高性能长期 3 + 真实短任务 1；额外长期 owner 分别按全局容量或余量上限拒绝。
- 并发反例在一个长期 owner 持有均衡档时先排入第二个长期 owner，再让后到的真实 `/bin/sleep 1` 短任务绕行；观测到 short=1、long=1、queued-long=1，总量仍为 2，排队长期 owner 最终以 `admission_managed_owner_headroom_timeout` 关闭。
- 压力收缩从高性能的三个长期 holder 收缩到全局 1，实际状态为 OVERCOMMITTED、既有 holder 不驱逐、新准入按全局容量拒绝。固定矩阵 6/6 通过。
- 目标回归 4 suites、34 tests，0 failure、0 error、0 skipped；强制 Debug 构建成功。APK 241,317,216 bytes，SHA-256 `A3217ABCDAA17B97402A110A2E603EE78E427EFCCE338F3941384205DED872AE`，已覆盖安装 OnePlus 8T，构建物未进入 Git。
- 真机生命周期探针在均衡档观测 `max=2 long_max=1 headroom=1 protected=true`；显式停止后 `long=0 total=0 protected=false`。正式 health 文件发布 `managed_proot_owner_v2` 与 `shared_proot_capacity_v2`，未见匹配 FATAL/ANR。
- RF1030 完成，下一恢复点 RF1040：只做全量回归、范围审查和下一类别 go/no-go，不重跑 Node/Python 性能矩阵。

## RF1040 父任务门

- 第一轮强制全量回归在 1464 tests 中发现 `ResourceRunCoordinatorTest` 一项 `ConcurrentModificationException`。栈只进入测试 `FakeResourceRunGateway`：后台结算写普通 `ArrayList`，测试线程同时遍历；生产 Gateway 不在异常栈。测试夹具改为并发集合、原子代次和可见字段后，目标类 12/12 通过。
- 修复后两轮完整强制回归均通过；最终 JUnit XML 为 275 suites、1464 tests、0 failure、0 error、2 skipped。强制 Debug 构建成功，APK 241,317,216 bytes，最终 SHA-256 `67BB8165338D15956F0D9DBCDDD8E836327B57A3BF5C2B579C0005A16D93DA28`，已覆盖安装 OnePlus 8T，构建物未进入 Git。
- 最终真机第一次矩阵揭示 RuntimeHealth 可在固定矩阵中途重新接管 policy，使同一 case 的快照和拒绝来自不同档位。固定 Debug 矩阵现通过 coordinator policy 更新屏障短暂串行化；屏障不选择策略，退出后等待中的正式更新继续接管。修复后含冷启动的连续三轮均 6/6 通过，无匹配 FATAL/ANR。
- 从 RF1000 起点 `e0d2125` 到父任务门的净路径只涉及 PRoot admission/actual health/warm coordinator、Debug 探针、目标测试和文档；Node、Python、终端、Agent 均无净修改，冻结性能矩阵没有重跑。
- 类别复核结论：终端和 Agent 直接复用 `MANAGED_OWNER` 为 no-go。二者可长期空闲，若按会话存活永久占用 1/2/4 槽，会把吞吐保护变成会话数量限制；下一阶段只值得研究入口无关的“进程启动窗口协调”，不得借机接管会话全生命周期。
- RF1000 完成。

## RF1110 启动窗口审计启动

- 代码中已有统一 `ProotLaunchPlan`，能表达 `INTERACTIVE/EXEC/BOOTSTRAP` lane 与 purpose，但 `ContainerLaunchConfig`/`ContainerExecConfig` 目前只携带物理 argv/env，计划事实没有进入进程创建边界。
- 实际创建点并不唯一：Termux `TerminalSession` 内部创建 PTY 进程，Agent 由 `AgentProcessFactory` 创建双向 stdio 进程，后台与有界 exec 直接调用 `ProcessBuilder.start()`；因此不能把某个 helper 包起来就宣称全局启动协调。
- `ProcessBuilder.start()` 只证明 Android wrapper 已创建，不证明 PRoot 已完成 rootfs 翻译或业务可用。终端现有激活探针、Agent 协议连接、exec 首字节/退出和后台强身份/健康分别是不同就绪边界。
- 下一步先固定两阶段 launch lease 合同和 Debug 首字节矩阵；RF1110 不修改生产入口。
- 进一步逐入口复核：终端现有 `isRunning + pid` 仍不足以证明 shell 可交互；Agent 的 ACP initialize 是强 READY；后台已有长期 lease 且必须避免双重排队；有界 exec 已由完整任务 admission 覆盖；bootstrap 有固定 token 但属于准备事务；Bridge/ADB 仍存在直接创建旁路。
- `ProotLaunchPlan` 已有 lane/purpose，但两类物理 config 丢失该 metadata。若矩阵 go，正确接线顺序是先透传结构化 launch metadata，再由实际创建者持有两阶段 lease；禁止从 argv 或资源/Agent 身份反推。
- RF1110 完成，进入 RF1120 固定矩阵。矩阵先证明“只包围 start()”与“包围到 READY”的差异，不接生产入口。

## RF1120 Debug 固定并发启动矩阵

- 新增 Debug-only 无参数 service。命令固定为结构化 `/bin/sh -c`：先输出 `KF_LAUNCH_READY`，再继续存活 250ms；ADB 不能传入命令、路径、并发、窗口或业务身份。每个批次在创建前准备独立 config，统一起跑，记录 queue/start-return/READY/exit，并强制清理超时进程。
- 单次 suite 固定 28 个 case：并发 1/2/4/8；无协调、start-return 释放和 READY 释放；窗口 1/2/4；每 case 三轮。OnePlus 8T 连续执行两套完整 suite，共 168 个批次，全部零失败、零残留，suite 均 28/28 通过。
- 第一套 8 并发：无协调 ready P95/wall=66/369ms；start-return 窗口 1/2/4 为 84/66/65ms、wall 388/370/366ms；READY 窗口 1/2/4 为 160/95/78ms、wall 463/396/378ms。
- 第二套 8 并发：无协调 ready P95/wall=70/353ms；start-return 窗口 1/2/4 为 63/58/58ms、wall 365/359/360ms；READY 窗口 1/2/4 为 194/107/77ms、wall 494/403/365ms。
- READY 收窄在两套中均单调增加排队和尾延迟，窗口 1 最差。start-return 偶有 P95 改善，但第一套只能追平、第二套 batch wall 仍无改善，不能形成稳定收益。
- 强制 Debug 构建通过。APK 241,350,068 bytes，SHA-256 `86CE1D1306C44C809EB7C2A3FA2B984DA5B33BA4E4014AB382D60EA83FF644AB`，已覆盖安装 OnePlus 8T；无匹配 FATAL/ANR，构建物未进入 Git。
- RF1120 完成，RF1130 按预设发布门给出 go/no-go，不因代码已存在而放宽标准。

## RF1130 go/no-go 与 RF1100 收口

- 结论：生产 no-go。READY 窗口在两套 4/8 并发矩阵中均增加 queue P95 和 ready P95，窗口越窄越差；start-return 在第二套 8 并发有 70→58ms 的偶发改善，但第一套是 66→65/66ms，batch wall 均无稳定下降，达不到预设门。
- 单 PRoot 的固定 shell READY 处于几十毫秒级，远低于真实重型应用的多秒到几十秒。说明通用 wrapper/rootfs 进入并不是那类单实例慢启动的主要量级；在外层加启动锁不能加快一个进程，只会影响多个进程的重叠方式。
- RF1140 不触发。没有给 config 增字段，没有迁移终端/Agent/后台/Bridge，没有新增生产 semaphore、lease、Store、健康字段或定时器。Debug 矩阵保留，用于 PRoot/runtime/rootfs 前提改变后复算。
- 下一优化方向必须回到通用依赖内部的高频文件/解释器/子进程成本，继续遵守“只给通用依赖开快速通道，不给使用端应用特化”的三车道原则。
- RF1100 完成。

## RF1210 通用依赖候选审计

- 读取 17 份正式资源清单的 `relations.base`：`kite.git` 被 10 个上层资源依赖，`kite.nodejs` 9、`kite.curl` 4、`kite.python` 1、`kite.uv` 1。Node/Python 已完成，不能因进入新阶段而重验。
- curl 的高价值静态 HTTPS 下载已由 RF310 原生 Provider 覆盖；任意 curl 的网络时延占主导，当前增量收益低。uv 只有一个直接上层且核心语义是 Python 下载、venv 和子进程，正落在 RF250 保持 PRoot 的边界。Git 同时具有最高未覆盖 reach 和本地小文件敏感性，进入首位。
- 设备受管 Git 链为 `/workspace/.kf/bin/git -> /workspace/.kf/software/kite.git/bin/git -> /usr/bin/git`，最终身份属于当前 rootfs，不另带一份应用专用 Git。实验只复用 `GlibcHostRuntimePreparer` 的入口无关 launcher/loader/libc/compat 资产。
- Git 本地 builtin 与远程/helper 不能一起宣称兼容。首轮矩阵分别覆盖本地 version/init/status/add/commit/log/diff/rev-parse；hooks、pager、external diff/filter、credential/ssh/remote helper 与 submodule 必须独立失败关闭或证明。
- RF1210 完成，进入 RF1220 Debug 矩阵；不修改资源卡、shim、Planner 或正式入口。

## RF1220 Host Git 兼容与性能矩阵

- Debug 固定入口不接收 ADB 参数，精确解析 `/workspace/.kf/bin/git` 到当前 rootfs `/usr/bin/git`，只复用既有通用 glibc Host 资产；每轮在受控目录新建 Host/PRoot 两份 1000 文件仓库，结束后清理。
- 连续两套 OnePlus 8T 矩阵中，`init/add/commit/rev-parse/status/log/diff` 的 HEAD、clean status、diff 与 index 语义全部一致。第一套 Host/PRoot 顺序 P50=104/105ms，第二套为 107/407ms。
- 8 并发 Host batch wall 两套为 128/132ms，PRoot 为 702/559ms；Host P95 为 124/122ms，PRoot 为 622/511ms。核心本地路径存在稳定并发收益，PRoot 抖动明显。
- shell alias Host exit 127；hook exit 1 且 marker 文件虽创建但内容为空；external diff exit 128；remote helper exit 128；submodule exit 127。对应 PRoot 全部 exit 0 且 marker 正确。
- clean filter 是关键反例：Host `git add` 返回 0，但 filter marker 错误且 index 内容与 PRoot 不同，证明仅以返回码选择/验收会静默破坏仓库语义。
- Debug 构建和覆盖安装通过，未发现匹配 FATAL。RF1220 完成；生产资源卡、Git shim、Planner、运行状态与正式入口均未修改。

## RF1230 direct Host Git go/no-go

- 结论：生产 no-go。结构化 argv 仍无法证明同一仓库的 local config、`.gitattributes`、hooks、remote helper 或 submodule 不会创建 child；完整复刻 Git 的解析与优先级才可能预判，既形成平行实现，也有检查后被修改的竞态。
- 不能采用运行后失败再回 PRoot。Git 是有副作用工具，`add/commit/filter` 可在错误暴露前改变 index、对象库或工作树；RF1220 更证明 exit 0 也可能写入错误内容，再跑一遍 PRoot 会制造第二次执行而不是回退。
- 不采用 builtin/subcommand 白名单。相同 `status/diff/add` 会因 repository config 和 attributes 进入不同能力边界，命令名不是稳定合同。
- 重新核对正式 manifest：10 个 `relations.base` 说明上层需要 Git 可用，但静态安装 Recipe 中没有十条 `clone/fetch` 热路径；除 Git 卡自检外，Git 主要由上层程序动态调用，而 Host Node 的任意 child 已按兼容合同进入 PRoot。仅为直连终端 Git 开 Provider 不能兑现原先按 reach 推定的收益面。
- `HostGitBenchmarkReceiver` 作为 Debug-only 可复算证据保留。若要继续利用本地 builtin 收益，下一研究问题必须提升为入口无关的 glibc child relay：Host 父进程保留，所有 external child 在 exec 边界无损进入 PRoot；未证明前不接生产。

## RF1240 Host Git 父任务门

- 强制全量 `:app:testDebugUnitTest --rerun-tasks` 通过：275 suites、1464 tests、0 failures、0 errors、2 skipped。
- 强制 `:app:assembleDebug --rerun-tasks` 通过。APK 241,399,304 bytes，SHA-256 `F00F997ACD89B8F5DC84A8C04377500851A8B1B08258B2C9A44911AC5377728D`；构建物未进入 Git。
- `a31a35d3..HEAD` 范围只有 Debug manifest、Debug benchmark 与架构/任务文档；`app/src/main`、正式资源、shim、Planner、lane/Store 均无净改动。
- OnePlus 8T 已覆盖安装并连续运行两套 11 case 矩阵，无匹配 FATAL/ANR。RF1200 以 no-go 完成，不因性能收益绕过兼容门。
- 下一阶段进入 RF1300；它研究通用 child relay，而不是重新验证 Node/Python 或给 Git 增加特判。

## RF1310 child relay 入口与语义审计

- 正式 glibc launcher 的 `--preload` 只作用于当前目标；正式 compat 库不拦 exec/spawn。Node child bridge 已有路径/cwd/env/PRoot 前缀逻辑，但属于 JS API 层，不能冒充任意 glibc 覆盖。
- 通用 relay 至少面对 execve/execvp/execvpe/fexecve、posix_spawn/p、system/popen、fork/vfork 后 exec 与 shebang；只覆盖导出 `execve` 不能证明 glibc hidden 调用也命中。
- argv/env/cwd 之外，fd/file actions、pgroup、signal mask/default、wait exit、取消与 child PID 都是兼容合同。额外 wrapper 不能形成第二业务进程或遗留 tracee。
- 最大先验风险是同步错误：原生 exec/spawn 可直接返回 ENOENT/EACCES，而先启动 PRoot wrapper 会把它变成稍后的 exit 127。无法在不复制容器解析规则的条件下保持时，必须 no-go 或把 Provider 限制为调用方明确接受异步 child 的结构化合同。
- RF1310 只新增架构合同，生产 launcher/compat/Provider/资源/lane 均不变。RF1320 使用独立 Debug 资产验证实际入口，不覆盖冻结资产。

## RF1320 Debug-only child relay 固定矩阵

- 新增独立 relay/probe C 源码和可复现 WSL 交叉编译脚本；产物只生成到 `local-artifacts/glibc-child-relay-rf1320`，经 ADB 放入应用私有 debug 目录，没有打包或进入 Git。正式 launcher 与 compat 库未修改。
- execve/v/vp/vpe、补齐的 execl/lp/le、posix_spawn/p 与 fork 后 exec 均记录真实命中；argv/env/cwd、stdio、exit 37、signal 15、workspace shebang、spawn file actions 与独立 PRoot 完全一致。
- system/popen/fexecve 没有命中。改用 Android 不具备的 `git --version` 后，system exit 127、popen 无输出，而 PRoot 均成功；fexecve 也出现同 exit 但输出不同，证明便携命令的偶然成功不能算覆盖。
- missing exec/spawn、EACCES、坏 shebang 四类均改变同步错误：direct/PRoot parent 能得到 errno/return，Host+relay 先成功创建 PRoot wrapper，再以 exit 1 和 stderr 报错。unrestricted relay 因此 no-go。
- 1/4/8 并发各三轮均零失败。Host parent + relay batch wall 中位数为 106/117/137ms，PRoot parent 为 124/161/209ms；P95 为 109/116/126ms 对 110/109/124ms。收益来自父进程留在 Host，不是把 PRoot child 本身加速。
- RF1330 只复算窄的 `direct exec/spawn + async child failure accepted` 候选合同；不按工具名选择，任一真实 system/popen/fexecve 或同步 errno 依赖都失败关闭。

## RF1330 Git/Python 通用反例复算

- relay 增加受控根环境值与 `PATH` 分段映射，仍只消费统一 PRoot 前缀与环境文件；没有读取 Git/Python 名称、资源 ID、subcommand 或脚本文本。Debug 原生库 SHA-256 为 `37037E8E4EFB08CA62B908BCF5A3364EDEEC5BD734AAECACB71002ABDADCC378`，只部署到 OnePlus 8T 应用私有调试目录。
- Host Git + relay 复算 12 个固定 case：本地 HEAD/status/diff/index 保持一致，shell alias、hook、external diff、clean filter、remote helper 和 submodule 全部与独立 PRoot 对照一致；8 次 child 全部真实命中 `execve`。8 并发 Host batch wall 145ms，PRoot 566ms，Host P95 126ms，PRoot 512ms。
- Host Python + relay 中，Python subprocess、`/bin/uname` subprocess、`os.execve`、venv 创建及 venv child 均与独立 PRoot 一致，实际命中 `execv:3, execve:2`。`os.system("git --version")` 未命中 relay，Host 失败而 PRoot 成功，证实 Python 不能按解释器整体放行。
- `venv --with-pip` 在 Host+relay 与独立 PRoot 均因同一 `ensurepip` 路径失败，不作为 relay 回归或成功证据。矩阵结束后无 relay/proot/python 残留，logcat 无匹配 ANR/FATAL。
- 结论是“显式 direct exec/spawn 调用合同可行”，不是“Git/Python 已通用兼容”。RF1340 只有在请求能在进程创建前声明该保证、且不依赖同步 errno 时，才可讨论生产接线；未知调用保持整条 PRoot。

## RF1340 go/no-go 与父任务门

- 现有 `runtimeGuarantees` 能承载肯定式 direct exec/spawn 与异步失败保证，无需新增平行配置；但 Debug relay 在 exec 路径读取控制文件并使用 `calloc/strdup/realloc/free`。对多线程父进程 fork 后的 child，这些操作没有 async-signal-safe 证明，固定单线程矩阵不能替代该发布门。
- Debug 基准显式创建并在完成后删除每轮 prefix/env 控制目录。正式 `RuntimeExecutionProvider` 在生成 `ContainerLaunchConfig` 时尚无 run identity 或进程终态清理所有权；直接复用会产生跨运行覆盖、泄漏或竞态风险。
- `system/popen/fexecve` 漏拦和同步 errno 变化虽然能由调用方保证失败关闭，但不能抵消上述实现级阻断。把 relay 二进制打进正式资产、扩展 guarantee enum 或修改 Host Python 均被否决。
- RF1300 最终结论：实验原理与窄语义合同成立，当前 preload 实现生产 no-go。正式 launcher、compat、Host Python、资源、Planner、lane/Store 零改动；Debug 原型与文档保留为后续重新设计 fork-safe relay 的起点。
- 父任务门完成 275 个 suite、1464 项全量单测，0 failure、0 error、2 skipped；强制 Debug 构建通过，APK 241,481,432 bytes，SHA-256 `9C844C210D26CA63D88F9E76CAD774C00BC744CB595BCD3C7DC3183EC999799F`。`3f0c1577..HEAD` 对 `app/src/main`、正式 `assets` 与 `app/build.gradle` 零净改动。OnePlus 8T 沿用 RF1320/RF1330 当轮固定矩阵，无残留 relay/proot/python，logcat 无匹配 ANR/FATAL。

## RF1400 开机与三问自检

- 目标是什么？直接测量当前活跃 Kite PRoot 相对 APK 内库存 PRoot 的增量成本，区分 wrapper 固定开销、生命周期遥测和通用 Linux 负载，不再用 OpenClaw 或解释器总耗时猜原因。
- 完成后拿什么证明？同一 rootfs/workspace/argv/env 的 1/4/8 固定交替矩阵，覆盖启动、shell、元数据、文件 I/O 与 child，分别比较 active 无遥测、active 正式遥测和 stock；验证结果、残留与 ANR/FATAL。
- 依赖是否满足？满足。两个资产均已随 APK 打包且身份固定；termux historical baseline 已标记 `execve ENOSYS`，不纳入成功对照；本阶段只新增 Debug 入口，不切换正式 runtime。

## RF1410 活跃/库存 PRoot 对照合同

- 当前正式 v23 active 为 356864-byte embedded-loader 二进制，SHA-256 `0A465CE2F5E3DCD80F801EF500478E4932248806EDC86CE5C9B0918D60C604BC`；stock 为 214416-byte external-loader 二进制，SHA-256 `125DFF2415AE1DCB8B1AE97C51357DE73EF11F28268B86CD50A0F13AA1C3EA91`。
- historical Termux baseline 的 SHA-256 为 `AAB80BBBB38345A6CF30D5173B1D9E5FB506B72FCFB48B089DB0DA62088B51C4`，descriptor 已固定 `quarantined_after_execve_enosys`，不进入成功性能对照。
- Debug A/B 必须先取得同一正式 `buildArgvExecConfig`，active 无遥测只移除三个 lifecycle 环境键；stock 只替换 argv[0] 并补正式 external loader 环境。不得重建 bind/rootfs/network，也不得切换安装态 runtime。
- 固定负载为 startup、shell、512 小文件 metadata、128 小文件 write 与 16 child fanout，1/4/8 并发、三轮、顺序轮换。至少两个 4/8 并发负载同时达到 P50 退化 15% 且 15ms、两轮同向，才算可行动热点。
- 首两套试跑全部 45 组语义通过、零残留，但 active telemetry 使用正式累计 sink，第二套 child-fanout 出现与首套矛盾的轮转型抖动。该数据只用于发现夹具污染，不进入性能结论；RF1420 已改为每套独享 Debug telemetry/registry sink，并记录最终 bytes/rotations。

## RF1420 Debug-only 固定 A/B 矩阵

- Debug receiver/service 不接收 extras，stock 只复制到 `files/runtime/debug/proot-overhead-rf1420`，安装态 `bin/proot`、descriptor 与 `activeRuntimeId` 未改变。目标合同测试、Debug 构建和 OnePlus 8T 覆盖安装通过。
- 两套最终隔离矩阵各 45 组、585 个 wrapper 样本，全部结果校验成功、0 failure、0 residual；每套独享 telemetry sink 1,527,550 bytes、0 rotation，结束后清理。logcat 无匹配 ANR/FATAL。
- startup、shell、metadata 在 active telemetry、active no-telemetry 与 stock 之间均未达到 15ms 行动阈值，说明基础 wrapper、最小 shell 与 512 文件只读遍历不是当前 Kite 增量热点。
- small-write 的 active/no-telemetry/stock 在 4 并发两套 wall median 为 `242/247/152ms`、`241/248/137ms`，8 并发为 `316/317/228ms`、`317/315/200ms`。有无 telemetry 接近，而 active 相对 stock 稳定多 89～115ms；进入 active 二进制默认文件 syscall hook 定位。
- child-fanout 在 4 并发两套 active telemetry/no-telemetry/stock 为 `222/124/122ms`、`226/117/112ms`，telemetry 增量 98～109ms；8 并发三者接近。进入 lifecycle 事件并发写入/锁/registry 定位，但不能外推为所有并发档稳定退化。
- RF1430 只允许优化默认无 View、无保护事务时的 fast-disable 分支，以及不丢事件/身份/退出事实的 telemetry 写入路径；不得关闭强身份、保护或 View 能力。

## RF710 开机与三问自检

- 目标是什么？复用而不是重写仓库已有设备校准 dry-run，找出历史 tracee/overlay 模型与 RF400 正式 1/2/4 admission/pool 档位之间可证明的映射和冲突。
- 完成后拿什么证明？真实引用图、目标测试和对齐合同明确：校准结果只能产生候选建议，不能直接改 coordinator；未知 thermal、旧 schema 或缺失实测上界失败关闭。
- 依赖是否满足？满足。RF650 已确认长期 owner 预研不接生产，不会与校准并行改同一状态；RF510～RF540 已提供 actual policySource、压力和有界任务遥测可作为后续可信输入。

## RF640 开机与三问自检

- 目标是什么？把 RF620/RF630 的纯规划结果投影为有界、低基数、明确标注 `planned_not_production` 的诊断字段，同时不污染 RF510 的 actual PRoot 调度事实。
- 完成后拿什么证明？字段只含 phase/kind/lane/action/process-match 的固定枚举计数和容量数字；不含 ownerId、leaseId、PID、启动代次、路径、命令、Agent/session 身份；读取不扫描、不恢复、不改模拟器。
- 依赖是否满足？满足。RF620 已有稳定容量快照，RF630 已有一 owner 一 decision 的恢复计划；二者均为调用方提供的不可变值，不需要创建第二个状态源。

## RF630 开机与三问自检

- 目标是什么？在不创建第二进程的前提下，固定应用重启后如何恢复 owner lease、如何核对 PID 启动代次、如何处理丢失进程与主动停止竞态。
- 完成后拿什么证明？恢复批次对同 owner 去重；仅精确进程代次可重连；未知/死亡进程进入 orphan review 并保持容量直到确认；恢复时的停止意图优先于运行重连；测试不读取真实 `/proc`、不接生产 Registry。
- 依赖是否满足？满足。RF610 已定义可恢复的 lease phase 与进程身份，RF620 已确保 recovered lease 未来可继续参与同一容量模型而不复制 owner。

## RF620 开机与三问自检

- 目标是什么？以 RF610 状态机为唯一 lease 记录，模拟长期 owner 的容量、压力准入、同 owner 去重、独占维护屏障与 lane 优先级/FIFO 公平性。
- 完成后拿什么证明？纯 JVM 测试证明既有 owner 不因压力变化被强杀、重复请求不多占容量、维护请求最终获得屏障、满 lane 不阻塞其他可运行 lane；不创建进程、不持久化、不接生产 Store。
- 依赖是否满足？满足。RF610 已把长期 owner 的容量寿命、进程代次、停止意图与显式释放固定为纯状态机；短任务 `SHARED_WRITE` 没有被错误复用于常驻进程。

## RF610 开机与三问自检

- 目标是什么？定义长期 runtime owner 与准入 lease 的同寿命状态机，明确 acquire、attach、running、stopping、released、orphan reconciliation，避免把短任务 `use {}` lease 直接套给服务/终端/Agent。
- 完成后拿什么证明？纯模型和模拟器覆盖唯一 owner、重复 attach、启动失败、停止、进程外死亡、恢复、压力只影响新准入、同 owner 重连；本阶段不创建真实进程、不接生产 Store。
- 依赖是否满足？满足。RF500 已提供 actual admission/telemetry，但其 lease 仍以调用栈为寿命；现有 `CardRunStore`、`BackgroundRuntimeRegistry` 和 Agent binding 可作为未来 owner 事实源，本任务只定义桥接合同，不复制状态。

## 倒序日志

### 2026-08-01 RF710 既有设备校准与正式 1/2/4 对齐

- 真实链路为：packaged P0 Python 通过 automation 的 PRoot 命令写 `/workspace/.kf/proot-device-calibration.json`；`RuntimeHealthStore` 读取后只传给 `RuntimeProotPoolPlanDryRun` 与 `RuntimeProotDeviceCalibrationDryRun`。实际 `WarmProotExecutionCoordinator`、admission 与 pool 不读取 overlay，只消费 profile/workload/pressure 和唯一 `ProotPerformanceTunings`。
- 修正 overlay loader：必须同时满足 `schema=proot_device_calibration_v0` 与显式 `valid=true` 才标记有效；缺 schema/valid 的任意 JSON 不再默认有效，并保留 `appliedAtMs` 供后续陈旧性判断。dry-run 输出真实 overlay schema。
- 新增 `RuntimeProotCalibrationAlignment`：只接受当前 v4 方法、应用时间、实测上界和自洽 tracee 证据；通过后也仅为 `READY_TRACE_GUARD_ONLY`，`directProfileSelectionAllowed` 永远 false。旧 overlay 的 profileLimits 即使写成 64/96/128 也被忽略。
- dry-run 展示的 low/balanced/high 改为从 `ProotPerformanceTunings` 派生的 1/2/4，不能再被 tracee 数覆盖。目标 2 个 suite、10 项测试零失败，Debug 构建成功。
- 首轮临时文件测试暴露的是本地 JVM Android `org.json` stub 的 `RuntimeException`，并未执行真实 loader 规则；没有降低断言，而是把 loader 的 schema/valid 门抽为纯函数，让产品代码与 JVM 测试消费同一判断后复验通过。

### 2026-08-01 RF650 RF600 父任务门

- 强制全量单测在工具 180 秒等待窗后继续完成并完整落盘：258 个 suite、1374 tests、0 failure、0 error、2 skipped；随后同一测试任务取得 exit 0。强制 Debug 构建 exit 0。
- APK 为 241087600 bytes，SHA-256 `6EAA1D36A662271346D388F1E5CA6B95EA6436946A9FA34F29F0E65231D3B8C8`；构建物未进入 Git。RF600 没有用户可见或生产运行改动，因此本门不安装真机、不伪造长期 owner 设备证据，也不重复 Node/Python 性能矩阵。
- 相对 RF550，生产源码只新增 RF610～RF640 四个无装配的合同/模拟器文件；`LongLivedProot*` 的 main 引用也只存在于这四个文件。没有修改 `CardRunStore`、`BackgroundRuntimeRegistry/Host`、终端、Agent、资源清单或 RuntimeHealth actual 字段。
- 后台服务生产迁移 **no-go**：`BackgroundRuntimeRecord` 目前只持久化 PID，没有启动代次；`HostProcessRecord` 也未投影 start ticks，外部恢复主要依赖 PID、容器命令/token 和 status probe，尚不能满足 RF630 精确代次与跨 owner 单进程合同。允许下一步做身份桥接准备，但在真实 owner 持久化、停止确认和恢复真机门前不接 lease。
- 终端与 Agent 继续 no-go：它们各自具有交互/会话/协议生命周期，RF600 没有提供类别级唯一进程、重连和停止证据。RF700 转向既有设备校准体系与正式 1/2/4 策略的对齐，不创建第二套校准器。

### 2026-08-01 RF640 长期 owner 规划态健康投影

- 新增独立 `proot_long_planned_*` 固定 schema，只消费 RF620 admission snapshot 与 RF630 recovery plan 的不可变值；scope 强制为 `planned_not_production`，没有写入或复用 RF510 的 `proot_actual_*` 字段。
- 字段只含压力、容量、维护屏障，以及固定 phase/kind/lane/action/process-match 的计数。恢复不可用时仍输出相同枚举键和零值，owner 从 1 增到 8 时 schema 数量不增长。
- 敏感值护栏以 ownerId、leaseId、PID、启动代次、路径、命令和 Agent/session 标识作为反例，投影结果均不包含；两次读取结果一致，模拟器快照和记录不变。
- RF610～RF640 强制联合为 4 个 suite、33 项测试零失败；随后 Locale.ROOT 键稳定性微调的投影 suite 复验通过，Debug 构建成功。RF650 只做联合父门和生产引用审计。

### 2026-08-01 RF630 重启恢复与孤儿协调

- 新增纯 `LongLivedProotRecoveryPlanner`，输入持久化 lease、调用方观察到的存活进程代次和停止意图；自身不读 `/proc`、不创建/停止进程、不连接生产 Registry，计划固定 `processStartsRequested=0`。
- 恢复只接受 `(hostPid, processStartTicks)` 精确匹配；同 PID 新启动代次标记 `PID_REUSED` 并进入 orphan review，原进程身份不被覆盖。未发现进程同样进入/保持 orphan review并继续占容量，直到 owner 明确确认死亡。
- 同 owner 只保留最高 generation；完全重复折叠，同代次冲突固定排序后标记 `DUPLICATE_CONFLICT_REVIEW`，不猜测哪份记录正确。跨 owner 若声称同一进程代次，两边都进入 `PROCESS_IDENTITY_CONFLICT_REVIEW`，禁止双重绑定。未启动的 `ADMITTED` 安全释放，未持久化身份的 `STARTING` 保持 review，不再启动第二进程。
- 恢复前收到停止请求时先写入停止意图；即使随后精确重连也恢复为 `STOPPING`。RF610 新增 orphan 内停止转换，避免恢复竞态把用户停止覆盖成运行。
- RF610～RF630 共 3 个 suite、29 项测试零失败，Debug 构建成功。首次测试编译只发现测试 helper 的可空诊断文本类型错误，修正后同范围通过，没有绕过恢复行为断言。

### 2026-08-01 RF620 长期 owner 容量与公平性模拟器

- 新增纯 `LongLivedProotAdmissionSimulator`：同一 RF610 lease record 同时承载排队、准入和生命周期状态；没有生产单例、Store、线程、进程或 PRoot 调用。
- 全局容量、lane 容量、优先级/FIFO 和压力收缩均为确定性事件。高压只阻断新的非必要 owner，既有 holder 不被强杀；必要 owner 可绕过压力门但仍受实际容量约束。
- `EXCLUSIVE_MAINTENANCE` 以队列 sequence 建立屏障：屏障前任务可排空，屏障后不再新增共享 holder，维护完成后继续排队；被压力阻断的非必要维护不会堵住必要任务。它没有借用短任务 `SHARED_WRITE` 永久持锁。
- 重复 owner 返回同一 lease 且不增加容量；若 lane/posture/必要性声明变化则显式 `SPEC_CONFLICT`，不能静默替换。全局事件时间单调，陈旧输入失败关闭。
- RF610+RF620 共 2 个 suite、19 项测试零失败，Debug 构建成功；RF630 继续只模拟恢复与孤儿协调，不读取真实 `/proc`、不接生产 Registry。

### 2026-08-01 RF610 长期 owner lease 状态机

- 新增纯 `LongLivedProotOwnerLeaseTransitions`，把 owner kind、lane、文件系统姿态、lease phase 与 `(hostPid, processStartTicks)` 进程代次分离；没有集合、Store、线程、进程或生产装配。
- 容量从 `ADMITTED` 持续到 `STOPPING/ORPHAN_REVIEW`，只有启动前取消/失败、停止确认或死亡确认才释放。相同进程 attach 幂等，不同身份拒绝；失联前为 `STOPPING` 时，重新确认存活仍恢复停止意图，不会误转回运行。
- 长期文件系统姿态使用 `SHARED_RUNTIME/ISOLATED_RUNTIME/EXCLUSIVE_MAINTENANCE`，没有复用短任务调用期 `SHARED_WRITE` 锁；状态转换时间单调，陈旧事件不能倒推状态。
- 目标 2 个 suite、23 项测试零失败，Debug 构建成功。RF620 继续只做容量/公平性纯模拟，不接 `CardRunStore`、后台 Registry、终端、Agent 或任何真实进程。

### 2026-08-01 RF510 实际调度状态正式投影

- 正式 RuntimeHealth 新增 `proot_actual_*` 组，明确 `scope=actual_not_planned`；字段即时来自 `WarmProotExecutionCoordinator` 的 policy、admission 与 pool，不复用规划态 `prootPoolPlan` 冒充实际执行。
- 投影覆盖 profile、pressure、foreground、配置/有效上限、warm 上限/空闲时间、active/queued、session active/idle/stale 和最大空闲年龄；不包含 argv、cwd、owner、环境或输出。
- 未创建 pool 时 `tuningSnapshot()` 返回零 session，诊断读取不会触发 holder、资产检查或 PRoot 进程创建。
- 3 个相关 suite、13 项测试通过，Debug 构建成功；下一步 RF520 只修第一份 RuntimeHealth 前的 bootstrap policy，不改变正式快照接管规则。

### 2026-08-01 RF520 冷启动策略接力

- 冷启动在首个有界任务前复用现有 reclaimer/resident/workload 策略文件与 `/proc/meminfo`；正常内存按真实档位启动，信号缺失、高压或临界压力继续保持单并发。
- bootstrap 与正式 RuntimeHealth 共用单一 `policyState` 和 admission；正式快照无条件接管，迟到的 bootstrap 结果不能反向覆盖。实际健康面和 Debug 探针新增低基数 `policySource`。
- 4 个相关 suite、17 项测试零失败，Debug 构建成功。OnePlus 8T 覆盖安装后强停冷进程，首个固定探针为 `policySource=bootstrap_policy_files_host_memory pressure=normal configuredMax=2 effectiveMax=2`，成功走 `warm_runner`；无 ANR/FATAL。
- 下一步 RF530 只增加低基数执行遥测，不保存任务 payload，也不扩大有界 Runner 的任务范围。

### 2026-08-01 RF530 有界执行结果遥测

- `WarmProotPoolExecution` 统一给出 queue/execute/total：queue 覆盖 admission 与 Runner 槽等待，execute 覆盖业务 job 或独立回退；route 继续区分 warm、独立回退、拒绝、STARTED 后失败和 fallback 失败。
- `BoundedProotTaskTelemetry` 只按 lane/route/result 固定枚举聚合，时延使用 8 个固定桶及 sum/max；不保存 job、owner、argv、cwd、env 或输出，正式 RuntimeHealth 读取只复制内存快照。
- 4 个相关 suite、18 项测试零失败，4000 次并发完成样本无丢计数，Debug 构建成功。OnePlus 8T 冷进程固定探针记录 `telemetrySamples=2 telemetryRouteCount=2 queueMaxMs=39 executeMaxMs=1434 totalMaxMs=1473`，无 ANR/FATAL。
- 下一步 RF540 迁移第二个高频内部样板；不因遥测已经存在而扩大任意 shell、终端、Agent 或长期任务范围。

### 2026-08-01 RF540 Supervisord 健康采集有界 Runner 样板

- 新增 `/workspace/.kf/system/bin/kf-supervisord-health-snapshot`：稳定路径、内容版本 1、拒绝任何参数，固定执行 `update/status`、日志 marker 和每文件 8 行尾部；高频刷新只校准该 helper，不重跑整套 Workspace ensure。
- `SupervisordServiceHealthStore` 改为单一结构化 helper argv，声明稳定 owner、`SERVICE/SHARED_WRITE`、5 秒准入等待、10 秒运行和每流 256 KiB 上限；STARTED 前沿用统一独立回退，STARTED 后不重放。截断、超时、拒绝和 exit code 继续映射为原健康失败边界。
- 5 个相关 suite、23 项测试最终零失败、1 项环境跳过；首次强制轮只有既有并发池测试在编译高负载下 1 秒 latch 抖动，原范围无代码改动重跑通过。Debug 构建成功。
- OnePlus 8T 当前 Supervisord 运行记录本身为 ERROR、启动命令 exit 127；没有安装包或伪造健康。固定 helper 冷/温两次都走 `warm_runner`，真实保持 exit 127，total 1490ms→68ms，service sample 1→2，无 fallback/reject、无 helper 残留、无 ANR/FATAL。
- 35 秒后的共享 `kf-runner` 并非泄漏：随后固定采样显示其执行前 idle age 仅 1195ms，证明被现有容器进程采样复用；RF550 继续核对整体空闲与共享任务边界。

### 2026-08-01 RF550 RF500 父任务门

- 强制全量单测复算为 254 个 suite、1341 tests、0 failure、0 error、2 skipped；强制 Debug 构建成功。APK 241005680 bytes，SHA-256 `8F7DBC4073014D6CD188BE6061831D4CA64B6FB01AC4CD5EA1E396B77BEF7D7F`。
- RF510～RF540 相对 RF440 父门只改动实际策略/池/遥测、Supervisord helper、相关 Debug 探针、测试与任务文档；没有修改 Node/Python Provider、终端、Agent、资源清单或 View 正式链。`git diff --check` 通过。
- OnePlus 8T 覆盖安装强制构建 APK：冷进程固定采样为 `bootstrap_policy_files_host_memory/NORMAL/configured=2/effective=2`，成功 warm runner；随后两个 Supervisord helper 样本复用同一 Runner，121ms/115ms，真实保持 runtime ERROR/exit 127，无 fallback/reject、无 helper 残留、无 ANR/FATAL。
- RF440 已有压力收缩、1/2/4 档、空闲回收与 8 并发 no-go 证据，本门不重复性能矩阵。RF600 仅 go 合同/模拟器；生产迁移、终端、Agent 和长期服务全部 no-go，直到 owner 同寿命、恢复与停止合同分别通过。

### 2026-08-01 RF500 启动

- RF000 完成后按用户授权继续自主推进，不重跑冻结的 Node/Python 矩阵，不推送远端；每个叶子任务继续独立 Git 提交。
- 代码审计确认下一批真实缺口：实际 coordinator 状态没有进入正式健康面；冷启动首份健康快照前有效并发固定为 1；Supervisord 健康采集每次新建独立 PRoot 且不能直接把复杂 shell 伪装成 argv。
- RF500 先做控制面和第二个高频有界生产样板。普通终端、任意 Recipe shell、Agent、detached 和长期服务保持原路径；RF600 只在 RF500 父任务门后进入合同研究。

### 2026-08-01 RF440c / RF440 / RF400 / RF000 父任务验收

- 三档、CUSTOM、压力收缩、前后台 lane、动态 trim、空闲超时和活动任务保护由 3 个 suite、26 项测试覆盖，零失败、零错误、零跳过；本轮没有重复 Node/Python 矩阵。
- 复用 RF430c 同一 OnePlus 8T 的固定 32 MiB 对照：1/2/4/8 并发温热墙钟相对独立降低 64.8%/52.8%/59.3%/48.9%，吞吐提高 182.7%/112.6%/146.2%/95.3%，全部零失败；8 并发温热 P95 130 ms 高于独立 114 ms，因此生产继续保留 1/2/4，8 只用于 debug 校准。
- 最终构建覆盖安装后，正式有界进程表调用冷建池 344 ms、同会话温热复用 61 ms；超过均衡档 30 秒空闲线后，设备进程表已无 runner，下一次探针 `beforeSessions=0` 并在 143 ms 内正常重建。输出均为 `warm_runner`、4 条可解析记录、691 bytes、零截断。
- 温热点位应用 `TOTAL PSS/RSS` 为 114,887/224,428 KiB，runner 独立进程 RSS 4,632 KiB；回收后点位应用 `TOTAL PSS/RSS` 为 105,926/214,676 KiB。它是点位资源证据，不冒充多轮统计分布。
- 当前设备策略为均衡档，配置上限 2、温热上限 2、空闲 30 秒；压力尚为 `unknown` 时有效准入安全收缩为 1。无残留 benchmark/`ps` 子任务，无新增 ANR/FATAL。
- 最终 Debug APK 247,104,068 bytes，SHA-256 `00ED0500DA74A1325F9FCDC060499921D15D994CBEE25AFF98E3223FBBE9961F`，构建物未进入 Git。
- RF000 完成：快速通道、Android 原生能力、Ubuntu/PRoot 兼容底座共享统一请求/选择/失败关闭合同；普通终端、任意 shell、Agent 和长期服务继续独立 PRoot，View 仍只保留显式更新/危险文件保护边界。

### 2026-08-01 RF440b 动态策略与可观测性

- `WarmProotExecutionCoordinator` 继续只从现有 `RuntimeHealthSnapshot` 接收 active profile、lane、压力与前后台事实；策略变化更新同一 admission，并对同一 warm pool 执行动态 trim，页面和 Ubuntu 都没有直接控制入口。
- 压力升高或退到后台只限制后续准入：回归证明已经取得 lease 的活动任务不会被强杀；超出新上限的活动 Runner 标记为 stale，待 owner 正常释放后关闭，空闲超额 Runner 可立即回收。
- 新增即时 `TuningSnapshot`，从当前 policy、admission 与 pool 投影档位、配置/有效上限、温热上限、空闲时间、active/queued 及 Runner 活动/空闲/stale 数；没有新增持久化 Store 或复制运行事实。
- 3 个相关 suite、25 项测试通过，覆盖内置三档、CUSTOM、压力收缩、前后台 lane、动态 trim 与活动任务保护；下一步 RF440c 只做本阶段 Debug/真机父任务门，不重复 Node/Python 性能矩阵。

### 2026-08-01 RF440a 单一性能档参数源

- 新增 `ProotPerformanceTunings` 作为有界 PRoot 准入与温热池的共同参数源；准入器不再自己维护 1/2/4，Runner 池也不再维护另一份重复映射。
- 内置档保持低负载 1/2 秒、均衡 2/30 秒、高性能 4/120 秒；RF430 的 8 并发仅属于 debug `globalMaxOverride` 校准，不进入生产档位。
- 修复 CUSTOM 漂移：原先准入从 lane 推导 1～4，但温热池固定为 2；现在二者从同一 lane 最大并发推导并共同封顶 4，空闲回收仍保持 30 秒。
- 3 个相关 suite、23 项测试通过，零失败、零错误、零跳过；下一步 RF440b 增加同源动态状态快照并证明压力变化只影响后续准入/空闲回收。

### 2026-08-01 RF430c / RF430 父任务验收

- RF430a～b 的联合目标回归覆盖 7 个 suite、43 项测试，零失败、零错误、零跳过；既有 pool 测试证明 STARTED 前独立回退与同 lease，controller/protocol 测试覆盖 STARTED 后不重放、timeout、取消、runner 崩溃和身份失效。本阶段没有重复 Node/Python 矩阵。
- OnePlus 8T 固定 32 MiB 摘要对照全部零失败：1/2/4/8 并发的独立/温热墙钟分别为 193/68、229/108、275/112、268/137 ms，温热降低 64.8%、52.8%、59.3%、48.9%；吞吐提高 182.7%、112.6%、146.2%、95.3%。
- 8 并发温热 P95 为 130 ms，高于独立 P95 114 ms；它提升总吞吐但损伤单任务尾延迟，不能据此把生产高性能档从 4 盲目提高到 8。1/2/4 档继续进入 RF440 校准。
- 基准首次运行发现固定 32 MiB `/tmp` 样本未清理；已删除真机精确文件并在 debug 基准 `finally` 中固定清理，不重复整套矩阵。
- 最终 Debug 构建成功；APK 247,090,528 bytes，SHA-256 `57A0AF4F325ACDF49D83F8942FE8813F3786405D5DBA150A1E1487AB0FB485ED`，已覆盖安装到 OnePlus 8T，构建物未进入 Git。无残留基准文件，无新增 ANR/FATAL。
- RF430 完成。收益只适用于已准入的有界结构化短任务；普通终端、任意 shell、Agent、detached 和长期服务继续独立 PRoot。下一步 RF440 校准三档，不扩大任务类别。

### 2026-08-01 RF430b 首个新增生产调用方

- `ContainerProcessStore` 仅把容器进程表结构化 `ps` 查询迁入 `BoundedProotTaskExecutor`：每次生成唯一 jobId，owner 为 `system:container-process-store`，lane/access 为 `PROBE/READ_ONLY`，等待 1 秒、运行 12 秒、每流最多 1 MiB。
- `kill -0`、TERM/KILL、任意 shell、Agent、终端、detached 和长期服务保持原路径；没有按 `/usr/bin/ps` 名称在公共 Planner 中分流，迁移决定属于代码 owner 的显式调用合同。
- 3 个直接相关 suite、10 项测试通过，零失败、零错误、零跳过；Debug 构建与 OnePlus 8T 覆盖安装成功。
- 固定真机探针首轮（含建池）345 ms，第二轮温热复用 101 ms；两轮均为 `warm_runner`，解析 4 条记录、691 bytes、零截断，正式 Store 来源 `host_proc+container_ps`，可见进程 2 条。
- 查询子进程结束后无残留，均衡档空闲 Runner 按 30 秒策略回收，日志无新增 ANR/FATAL。该时间只证明此调用方的复用链可用，独立 PRoot 对照和百分比留到 RF430c。

### 2026-08-01 RF430a 通用有界短任务执行器

- 新增 `BoundedProotTaskExecutor`：输入只有代码 owner 提交的结构化 argv、cwd、env、lane、读写属性、wait/runtime timeout 和双流上限；交互 lane、shell 文本形态、超过 120 秒或超过 1 MiB/流的请求在准入前失败关闭。
- warm runner 与独立 PRoot 回退由执行器生成同一 job/admission 合同并持有同一 lease；独立路径并发排水 stdout/stderr，记录截断字节，timeout 后回收进程。只有 warm job 尚未 STARTED 才由既有 pool 调用该回退。
- 固定资源采样已迁移到通用执行器，删除原来私有的 `ProcessBuilder`、双 reader 和 timeout 复制实现；任务 owner、共享写、压力必要性和结果分类不变。
- 与本阶段直接相关的 6 个 suite、38 项测试通过，零失败、零错误、零跳过；下一步 RF430b 只迁移容器进程表的结构化只读 `ps` 查询，信号与其他 PRoot 类别不迁移。

### 2026-08-01 RF420c / RF420 父任务验收

- 新增同优先级 FIFO 与关闭唤醒回归；关闭 controller 会令排队项返回 `admission_closed`，活动 lease 仍保持到业务 owner 主动释放，证明关闭不是强杀入口。
- 5 个相关 suite、35 项测试全部通过，覆盖身份合同、重复 job、排队取消、交互优先、跨 lane 推进、共享写屏障、压力收缩、timeout、关闭、runner 协议/池/控制器及固定资源采样计划。
- Debug 构建成功；APK 247,077,712 bytes，SHA-256 `BDDA6F5872DFD81D46D5BC1681F6850A9F084BF520A850B4D4B7352F7FA1C796`，构建物未进入 Git。
- 本阶段没有改变普通终端、任意 shell、Agent、长期服务或 detached 的执行路径，因此没有为无用户可见变化重复真机 UI 和 Node/Python 性能矩阵。
- RF420 完成。RF430 只接入由代码 owner 声明的有界非交互结构化 argv：有限 timeout、有限 stdio、明确副作用属性和取消出口，且 STARTED 后禁止独立 PRoot 重放；用户 shell 与产品/资源名称不能成为路由条件。

### 2026-08-01 RF420b 队列生命周期闭环

- controller 在排队前检查活动和等待集合；同一 `jobId` 的第二份请求立即以 `admission_job_id_conflict` 失败，原 lease 释放或等待项取消后该身份可安全复用。
- 新增 `cancelQueued(jobId)` 只移除尚未准入的等待项并唤醒其线程，返回 `admission_cancelled`；活动 job 返回 false，必须继续由声明的 owner/timeout 回收，准入器不杀进程。
- snapshot 新增累计 `cancelledCount`，与现有 active、queued、admitted 和 timedOut 分开；取消不会消耗容量或计为超时。
- 准入器与 warm pool 两个 suite、18 项测试通过，零失败、零错误、零跳过；下一步 RF420c 补同优先级 FIFO、关闭唤醒和固定调用方回退合同，并完成 Debug 门。

### 2026-08-01 RF420a 完整任务身份合同

- `ProotJobAdmissionRequest` 现在强制声明 owner、取消模式和结果模式；它与既有 jobId、lane、读写属性、压力必要性和等待上限共同构成完整准入身份，缺失 owner 在排队前失败关闭。
- 唯一生产调用方——固定资源采样任务——声明 `system:runtime-process-resource-sampler`、`PROBE`、共享写、timeout/owner 回收和有界 stdio；debug 基准与测试夹具也显式声明，不留默认值掩盖遗漏。
- 架构矩阵明确普通终端、任意 Recipe shell、Agent、长期服务和 detached shell 尚未进入准入；它们继续独立 PRoot，不因 RF420a 被限流，也不能被宣称为统一调度。
- 首次编译发现 debug 准入基准仍使用旧请求，补齐合同后原命令重跑成功。3 个相关 suite、18 项测试通过，零失败、零错误、零跳过。
- 下一步 RF420b 只补队列身份冲突、排队取消和统计闭环；控制器仍不杀死已经开始的业务任务。

### 2026-08-01 RF410c / RF410 父任务验收

- RF410b 已完成的 10 个相关 suite、52 项回归继续覆盖 Provider/Planner、普通终端、Agent、后台、资源 shell、停止派发和显式 View 传递；RF410c 仅新增 debug 真机探针，没有重复执行这些未受影响的测试，也没有重跑 Node 性能矩阵。
- Debug 编译与构建通过；APK 为 247,072,180 bytes，SHA-256 `DCEB615400A353BBFA2000E2DF95A07FF1BC5EDF1C050F73F7EB7F4E910DA721`，构建物未进入 Git。
- OnePlus 8T 固定探针通过：直接 shell 104 ms、复杂 shell/ELF 103 ms、结构化 argv 103 ms；正式 `RunOrchestrator -> AndroidRecipeExecutor -> PRoot Provider -> 唯一进程` 为 985 ms，最终车道 `proot_shell`，原因 `shell_command_requires_proot`。
- 当前 Ubuntu rootfs 没有 `cc`；探针确认缺失状态未被 Provider 改造掩盖，不能宣称编译器执行成功。显式 View 只验证逻辑计划和 ID 传递，不创建伪 View 或全局叠层。
- 旧 600 秒 owner 探针属于瞬态运行，不写入持久化 CardRun；误用持久化文件观察后已通过停止 debug 应用清理。同一轮固定短探针结束后无残留 PRoot/睡眠进程，日志无新增 ANR/FATAL。
- RF410 完成；下一恢复指针为 RF420，把现有准入控制器接入逐类声明，不改变 PRoot 物理构造器、View 和状态拥有者。

### 2026-08-01 RF410b 正式入口等价适配

- `ManagedRuntimeLaunchPlan.Fallback` 已替换为带 `ProotCompatibilityPlan` 的明确 `Proot` 结果；快速 Provider 的 `Unsupported` 只有在允许启动前换道时才生成最终 PRoot 计划，`Blocked` 与禁用回退继续失败关闭。
- `WorkSurfaceRuntimeBridge` 只负责把逻辑计划交给原有 shell/argv/终端物理构造器；工作目录、附加环境、login/non-login shell 与显式 View ID 均从同一计划传递，没有复制 rootfs、bind、网络或遥测规则。
- 普通/Recipe 终端、资源 attached/detached shell、Agent 主进程、Agent 配置与会话管理、后台主进程/one-shot、supervisord 健康入口均消费标准计划；架构护栏禁止这些正式入口再直接调用旧 PRoot 构造器。
- PRoot 终端即使已有显式配置，Recipe 命令仍按真实 `proot_shell` 车道在终端打开后发送；Host Node/Python 配置内命令不会再发送第二次。
- Agent 的 Host Ready 不构造 PRoot；Proot 计划只物化一份 `ContainerExecConfig`；后台进程仍只有原来的唯一 `ProcessBuilder` 创建点，运行 lane/reason 继续由原 Registry 持久化。
- 10 个相关 suite、52 项测试全部通过，覆盖 Provider/Planner、入口护栏、终端、Agent、后台、detached 接受条件和停止派发；下一步 RF410c 做 Debug 与 OnePlus 8T 等价真机门，不重跑 Node 性能矩阵。

### 2026-08-01 RF410a PRoot 兼容 Provider 合同

- 新增纯逻辑 `ProotCompatibilityRuntimeProvider`；它消费统一 `RuntimeExecutionRequest`，只保留 payload、工作目录、环境、PTY 与显式 View 事实，不创建进程或持有运行状态。
- PRoot 作为最终兼容 Provider 对复杂 shell、结构化 argv、完整 Linux、子进程、未验证扩展和 View 请求返回 `Ready`；选择原因由上游显式传入，不解析命令名或资源 ID。
- Android 原生能力与 `ANDROID_NATIVE` 要求返回 `Blocked`，不能被错误包装成 PRoot 回退后静默改变能力语义。
- 物理 PRoot argv、rootfs、bind、网络、DNS、遥测和环境生成仍只属于既有 `KFContainerManager`；本阶段没有新增第二套构造器。
- Provider 与统一请求共 7 项目标测试通过；下一步 RF410b 让 Managed Planner 和正式入口消费该逻辑计划。

### 2026-08-01 RF340c / RF340 / RF300 父任务验收

- 目录唯一性、调用形态、权限门、结果拥有者、外部交接、禁止自动回退和缺失 Keystore 不虚构均有机器断言；目录查询是无 Android `Context`、无 I/O、无状态写入的纯 Kotlin 查询。
- 原生 Recipe 的未知能力在创建线程和取得运行所有权前失败关闭；APK Android action 继续调用原路径校验和系统 Intent，只额外把稳定能力 ID 写入同一 Run。
- 11 个相关 suite、59 项测试全部通过，零失败、零错误、零跳过；覆盖下载、文件、ZIP、Recipe/Run、停止所有权、资源编译、APK 交接与权限快照。
- Debug 构建成功；本地 APK 242,278,108 bytes，SHA-256 `8C15355364CA673E8411EDBB88FABA106C2B4EEF4BCFB47EA3E5DFC500640B35`，构建物未进入 Git。
- 本阶段没有重跑 Node 性能矩阵，也没有为目录验收在真机上主动弹出系统安装器；RF310～RF330 已有 OnePlus 8T 原生执行证据继续作为 RF300 真机基础。
- RF300 完成；下一恢复指针为 RF410，把现有 PRoot 等价收敛成统一 Planner 的兼容 Provider，不改变 rootfs、交互、View 或 owner 合同。

### 2026-07-31 RF340b 薄适配与运行证据

- 现有 `install_apk` Android action 仍复用原来的路径校验、`FileProvider` 与系统安装器 Intent；执行前只通过 `CapabilityCatalog` 将旧 action 映射为稳定的 `android.apk.open_installer`，没有复制安装逻辑。
- 同一 `CardRun` 写入 `android_native` 与稳定能力 ID，同时保持 `Running` 和“已打开安装器”文案；这只表示外部系统交接，不把用户确认后的安装结果冒充为应用内成功。
- `native_capability` Recipe 现在先检查目录条目及调用形态；未登记能力或把 Android action 塞进原生 Recipe 都会在建立线程和运行所有权前失败关闭，不隐式落回 shell/PRoot。
- 运行进度和结果仍由既有 `RunStateMutation` / `CardRunStore` 持有，目录不保存运行状态，也没有新增执行器或平行 Store。
- `CapabilityCatalogRoutingTest`、`AndroidNativeCapabilityRecipeRuntimeTest` 和 `AndroidRecipeExecutorTest` 共 24 项测试通过，零失败、零错误、零跳过；下一步 RF340c 只做目录合同、Debug 构建和必要入口回归，不重跑 Node 性能矩阵。

### 2026-07-31 RF340a 真实能力与所有者目录

- 在既有 `CapabilityCatalog` 内扩展可路由条目，没有新建平行目录；登记下载、文件、ZIP、APK 系统安装器交接、默认网络对齐和运行时权限快照。
- 每项固定 `NATIVE_RECIPE`、`ANDROID_ACTION`、`LIFECYCLE_SERVICE` 或 `QUERY_ONLY` 调用形态，以及权限门、结果拥有者、完成语义和自动回退边界。
- APK 真实入口是受控路径校验后打开系统安装器，不是静默 `PackageInstaller`；目录将其标记为 `EXTERNAL_HANDOFF`，不宣称安装成功。
- 默认网络继续由 `AndroidDefaultNetworkAlignment` 持续校准，权限快照继续由 `RuntimeBootstrapGateway` 持有；目录查询不读取或复制这些状态。
- 当前源码未发现正式 Android Keystore Provider，因此不登记 `android.keystore.*`，不把架构设想冒充已实现能力。
- 目录、文件与归档共 15 项目标测试全部通过；下一步 RF340b 让既有 Android action 和原生分发器只消费目录事实，不复制执行逻辑。

### 2026-07-31 RF330c / RF330 父任务验收

- 资源审计确认当前只有 OpenCode 声明 `tar.gz`，且下载路径依赖运行时架构变量；没有静态 ZIP、缓存目标且不要求 Linux 元数据的安全迁移点，因此资源清单零修改。
- OnePlus 8T 固定压力包为 16 MiB、128 个普通文件，原生与 PRoot 交替各 3 轮；最终安全双遍顺序实现为 2026/2491/2014 ms，PRoot 暂存解包后原子移动为 669/573/724 ms，p50 2026 ms 对 669 ms，原生慢 202.8%。
- 先后验证了中央目录随机访问、移除逐文件 `fsync`、中央目录安全预检＋顺序数据流三种实现；三种真机结果都明显慢于 PRoot，继续微调没有确定性收益依据。RF330 因此是“安全能力可用、性能路由 no-go”。
- 正式 `RunOrchestrator` 在同一 `CardRun` 解包 128 项、16 MiB，用时 2442 ms；唯一车道为 `android_native`，无 `runId`、终端或进程显示面。
- 真机 zip-slip 以 `native_archive_path_invalid` 失败关闭，取消后暂存与目标均清理；单测另覆盖符号链接、重复条目、膨胀比、条目/文件/总量/深度、空间不足和原子发布失败。
- 六个相关 suite、41 项测试全部通过，Debug 构建和覆盖安装通过；探针目录已清理，无 ANR/FATAL。本地 APK 242,275,344 bytes，SHA-256 `C123A38E4FCB0D201B65F1F2966EED97CE22D7BFFC3A0A74F81DA70051E93454`，构建物未进入 Git。
- RF330 完成；安全 ZIP 仅供显式受控调用，不自动迁移资源。下一恢复指针为 RF340。Node 性能矩阵未运行。

### 2026-07-31 RF330b Recipe/Run 与取消

- 原生分发顺序扩展为下载→文件→安全归档；只有前一 Provider 明确 Unsupported 才继续，Blocked 和执行失败不会投向下一 Provider 或 PRoot。
- `archive.extract_safe` 的进度、完成、失败和取消继续写入同一 `CardRun` 的 `android_native` 车道与报告显示面；不创建 `runId`、终端会话或进程 owner。
- 归档执行复用文件能力的取消信号；停止入口等待同一个 instance/generation/step 释放，阻塞测试证明取消后不会残留目标目录。
- 四个目标 suite、27 项测试全部通过，包含真实 ZIP Recipe、中央目录安全测试、无终端绑定、阻塞停止以及既有文件/Recipe executor 回归。
- 下一步 RF330c 审计资源 ZIP 合同并完成真机恶意样本、压力和 PRoot 对照；tar/tar.gz 保持原行为。

### 2026-07-31 RF330a ZIP 安全 Provider

- 新增 `archive.extract_safe`，第一版只接受 `format=zip`，并要求调用方显式声明压缩包大小、条目数、总输出、单文件、深度和膨胀比上限。
- 只提取普通文件和目录；tar/tar.gz、符号链接、硬链接、设备节点以及 owner、mode、xattr 保真全部保持 PRoot，不把 rootfs 解包器的完整 Linux 语义误当资源安全解包。
- 路径拒绝绝对路径、Windows 盘符、反斜杠、空段、`.`、`..`、超深/超长和重复条目；目标只能位于显式可创建根。
- 第一版流式 ZIP API 的反例测试发现它拿不到中央目录 Unix 类型，会把符号链接误当普通文件；实现改为先读取中央目录元数据，再逐条打开内容流，修复通用识别机制。
- 所有内容先进入目标同级唯一暂存目录，文件关闭后才原子发布整个目录；平台不支持原子移动、取消或任一安全门失败时删除暂存，不暴露运行中半成品。第一版不承诺断电级持久化。
- 归档与文件 Provider 共 10 项目标测试全部通过；下一步 RF330b 接入现有原生 Recipe/Run。

### 2026-07-31 RF320c / RF320 父任务验收

- 逐项审计资源清单和安装编译器：现有 `mv`、`rm`、`install` 要么修改活动安装根，要么同时维护命令链接，均处在资源级锁、单份备份、验证和回滚事务内；没有可安全独立迁移的缓存文件步骤，因此本阶段不改资源清单。
- 增加固定 Debug 真机门，不接受外部路径、文件大小、动作或轮数；16 MiB 文件在 OnePlus 8T 上交替执行原生与 PRoot 各 3 轮，原生为 109/56/40 ms，PRoot 为 1284/355/151 ms，p50 从 355 ms 降到 56 ms，当前测点降低 84.2%。
- 对照双方都完成同一文件复制和落盘同步；该数据只代表当前设备、文件大小与缓存状态，不外推为所有文件操作的统一收益。
- 正式 `RunOrchestrator` 在同一 `CardRun` 顺序执行 copy→move→delete，共 172 ms；唯一车道为 `android_native`，无 `runId`、终端或进程显示面，源文件未改变，复制和移动目标最终删除。
- 真机证明工作区删除在 Provider 阶段以 `native_file_delete_not_authorized` 失败关闭；探针缓存已清理，无 ANR/FATAL。
- 五个相关 suite 的 34 项测试全部通过，Debug 构建、覆盖安装和固定真机探针通过。本地 APK 242,270,872 bytes，SHA-256 `9AFFFCDDBFCA01E79AEC87FBACA938E80BFE117491A56927E8143A5A8661A92C`，构建物未进入 Git。
- RF320 完成；下一恢复指针为 RF330 安全归档能力。Node 性能矩阵未运行。

### 2026-07-31 RF320b Recipe/Run 与取消

- 现有 `AndroidNativeCapabilityRecipeRuntime` 在下载 Provider 返回 Unsupported 后继续选择文件 Provider；下载参数受阻时不会误投文件能力，未知能力也不会回退执行 shell。
- 生产文件上下文把 `/workspace` 限定为读、创建和替换，把 `REMOVE` 只授予更具体的 `/workspace/.kf/cache`，从同一通用机制阻止工作区任意移动源和删除。
- 文件复制进度、完成、失败和取消继续写入同一 `CardRun` 的 `android_native` 车道和报告显示面；不创建终端、`runId`、`terminalSessionId` 或进程 owner。
- 停止入口同时取消下载与文件信号，并等待原生执行线程释放同一个 instance/generation/step 所有权；不存在 Host 已开始后再补跑 PRoot 的路径。
- 三个目标 suite、20 项测试全部通过，覆盖文件复制正式 Run、无终端绑定、阻塞操作停止确认以及既有下载和 Recipe executor 回归。
- 下一步 RF320c 只审计可迁移的缓存操作并做真机门；活动安装根仍归既有资源事务。

### 2026-07-31 RF320a 封闭文件 Provider

- 新增 `filesystem.copy_file_atomic`、`filesystem.move_file_atomic` 与 `filesystem.delete_file` 三个固定能力；只接受结构化路径、尺寸上限和替换策略，不解析 shell。
- 文件根分别声明读、创建、替换和删除权限；更具体的根优先，因此工作区可读写不等于可删除，缓存等受控根才能授权移动源和删除。
- 路径逐段拒绝 `.`、`..`、空段和既有符号链接；第一版只操作普通单文件，不递归删除目录，不接收活动资源安装根的隐式授权。
- 复制先写同目录临时文件、同步后原子发布；移动要求平台原子移动，`ATOMIC_MOVE` 不受支持时失败关闭，不退化成可能暴露半状态的移动。
- 取消测试发现打开输出流时提前删除临时文件在部分文件系统会失败；实现改为先退出流再清理。目标测试 5 项全部通过，覆盖权限拒绝、取消、尺寸门、原子移动失败和正式目标保留。
- 下一步 RF320b 只把这些计划接到已有 `native_capability` 与同一 `CardRun`，不迁移资源活动安装根，也不重接旧 View 事务层。

### 2026-07-31 RF310c2 / RF310 父任务验收

- OnePlus 8T 使用同一 RFC 固定样本交替执行原生与 PRoot 各 3 轮：原生为 937/534/497 ms，PRoot 为 1359/654/630 ms；原生 p50 为 534 ms，较 PRoot 的 654 ms 降低 18.3%。该结果只证明当前设备与固定下载链，不外推全部网络负载。
- 真机故障重试使用不可连接的本机 HTTPS 端点，2 次尝试后返回 `native_download_io_failure`，目标和临时文件均清理；确定性单测另覆盖首轮响应中途断流、次轮从空文件重试，禁止把残片拼入成功结果。
- 真机通过 Android 正式 Provider 流式下载 2,020,420 字节，耗时 1,156 ms，摘要 `82ef2947a26edd0acfa4326db3d91790c5ef5ce1b433c9c484770e2e1e286cfd`，原子发布并完成清理；单测另覆盖 32 MiB 流式数据与空间不足时目标不落地。
- Debug 基准服务在应用处于后台时被 Android 正确拒绝启动并记录 `BackgroundServiceStartNotAllowedException`；将应用带到前台后才执行，不以绕过系统后台限制的方式取证。
- RF310 的成功、摘要失败、取消、网络中断、有限重试、尺寸/空间门、流式压力、资源事务和 PRoot 对照均已闭环；动态 URL、多镜像及无尺寸上限输入按合同继续留在 PRoot。
- 本阶段没有重跑 Node 性能矩阵，没有安装外部 Agent；下一恢复指针为 RF320 受控文件操作。

### 2026-07-31 RF310c1 静态资源获取迁移

- 资源安装步骤新增 `maxBytes` 安全上限；只有位于动作开头、单一静态 HTTPS URL、目标为 `$install_root` 安全相对路径且尺寸上限大于零的下载，才编译为 `native_capability`。
- 原生下载先原子发布到 `/workspace/.kf/cache/resources/<id>/native-downloads`；随后既有 PRoot shell 才获取 `<install_root>.kite-update-lock`，在原事务内移动缓存、执行安装、验证、提交或回滚。下载阶段不会改活动安装根。
- Kimi、Hermes 与 Antigravity 的固定官方安装脚本已声明 16 MiB 上限并进入原生车道；OpenCode 的 GitHub Release URL 依赖架构选择和 shell 变量，继续使用 PRoot 下载，未做资源 ID 特判。
- 发现并修复通用运行事实缺口：普通 shell 准备阶段此前不会覆盖上一阶段的运行车道；现在统一写入 `proot_shell / shell_command_requires_proot`。
- 资源与原生能力定向回归、Debug 构建通过；32 MiB 流式负载和声明尺寸大于可用空间的失败关闭已加入单测。
- OnePlus 8T 固定正式链探针在同一 `CardRun` 观察到 `android_native -> proot_shell`，18,504 字节样本完成摘要验证与资源事务，总链 6,186 ms；缓存、安装根、备份和锁均清理，无 ANR/FATAL。
- RF310c 尚未完成：固定 PRoot 对照、真实网络中断/重试与更大真机负载归 RF310c2；本阶段没有运行 Node 性能矩阵，也没有安装外部 Agent。

### 2026-07-31 RF310b 验收

- 新增显式 `native_capability` Recipe 步骤，能力由 `action`、字符串 `params` 表达；不解析 shell 文本，也不读取资源 ID 或应用名决定车道。
- `AndroidRecipeExecutor` 将该步骤交给原生能力运行端；请求使用 `ANDROID_NATIVE` 要求并关闭隐式回退，当前首个能力接入 `network.download_sha256` Provider。
- 进度按 1 MiB 或 500 ms 节流，通过原有运行事件写入同一 `CardRun` 的 `android_native` 车道、原因、报告和结果；不创建 `runId`、终端会话或虚假进程 owner。
- 停止链即使没有进程绑定也会进入原生执行端，主动关闭阻塞连接，并在临时文件清理完成后确认停止；重启同样先完成这一停止合同。
- 定向回归覆盖 6 个 suite、44 项测试，0 failure、0 error、0 skipped；首次失败仅因新增 JSON 测试漏用项目既有 Robolectric 运行器，修正后原命令通过。
- 本阶段没有重跑 Node 性能矩阵，也没有迁移任何资源卡。下一恢复指针为 RF310c：资源获取显式编译为原生能力，并保留更新锁、备份和安装状态拥有者。

### 2026-07-31 RF310a 验收

- 新增标准 `ANDROID_NATIVE` Provider `network.download_sha256`，只接受结构化 HTTPS URL、受控容器目标、最大字节数、超时、有限重试、替换策略和可选 SHA-256。
- 执行器使用 Android `HttpURLConnection`，继承应用 UID 的 VPN、私有 DNS 与证书策略；流式写入同目录唯一临时文件，`fd.sync()` 后再原子移动。
- 第一版明确不发 Range：每次重试先清空本次临时文件；意外 206、HTTPS 跳转到 HTTP、未知参数和目录逃逸均失败关闭。
- 6 项目标单测覆盖成功、摘要不匹配、有限重试、取消、尺寸上限、重定向降级和意外部分响应，零失败。
- OnePlus 8T 固定真机探针从 RFC Editor 下载 18,504 字节，SHA-256 `714d11bfcbc001f98cd8a92291a19e3f670c2236ad02771092e0eea826acd13a`，原子发布成功；错误摘要保留旧目标，取消后目标与临时文件均清理，无 ANR/FATAL。
- RF310 还未完成：RF310b 必须接入同一 Recipe/Run，RF310c 才迁移资源获取并做 PRoot 对照与大文件压力；当前资源安装仍保持既有 PRoot 行为。

### 2026-07-31 RF254 / RF250 父任务验收

- 固定 CPython 3.14 ARM64 扩展源码以及 0.0.1、0.0.2 两代 wheel 元数据；编译二进制和 wheel 只作为本地证据，不进入 Git。
- 生产 `HostPythonRuntimeProvider` 现在要求 `verified_native_imports` 同时携带规范化 `pythonAbi`；ABI 缺失或不匹配均在创建业务进程前回到 PRoot，未知证据键失败关闭。
- 资源 Agent、后台依赖、自定义 Agent 和后台持久化记录都透传同一份保证证据；旧记录缺省为空，继续安全回退。
- OnePlus 8T 真机证明同一 `cpython-314-aarch64-linux-gnu` 扩展在 Host 与 PRoot 均能直接导入；0.0.1、0.0.2 分别安装到不可变代次目录后，选择 0.0.2 的 Host 导入通过。
- 原地 `pip --target --upgrade` 被反例否决：它会保留旧 `.dist-info`，导致代码已升级而元数据仍可能返回 0.0.1；正式边界固定为新代次安装、验证后切换，不做目录内覆盖。
- 伪造 `cpython-315-aarch64-linux-gnu` 时 Provider 返回 `python_native_imports_abi_mismatch`，且 Host 业务进程创建数为 0；没有包名、资源 ID 或应用名白名单。
- 强制全量回归为 240 个 suite、1267 tests、0 failure、0 error、2 skipped；强制 Debug 构建成功。最终本地 APK 为 240628388 bytes，SHA-256 `9B08814F770917BA728CFD10BF66F99C407434C95A09908B9114864F929664A4`，构建物未进入 Git。
- 本阶段没有重跑 Node 性能矩阵；RF200 完成，下一恢复指针进入 RF310 原生下载与 SHA-256。

### 2026-07-31 RF252 验收

- `runtimeGuarantees` 使用固定 wire enum 接入资源 Agent、资源后台依赖、自定义 Agent 和 `BackgroundRuntimeRecord`；未知值拒绝，缺省空集合向后兼容。
- Agent 登记、JSON 持久化、后台记录和两条 Planner 请求均透传同一事实，没有从环境变量、命令名或资源 ID 旁路推断。
- 7 个目标 suite、43 项测试零失败；新增未知自定义保证拒绝测试单独通过。
- Debug 构建与一加 8T 覆盖安装成功；真机固定清单夹具经正式 Manifest Loader 解码保证，再由生产 `HostPythonRuntimeProvider` 启动，`production_manifest_provider` 退出 0。
- 旧 Node 清单未添加无关字段，Node Provider 不读取保证；本阶段没有重复 Node 性能矩阵。
- 下一恢复指针进入 RF254；未验证的第三方扩展仍整条 PRoot。

### 2026-07-31 RF251 / RF253 验收

- Layered 真机矩阵没有重跑 RF230 性能点；新增 Linux 身份、shell 文件视图、`execve` 和带 pip venv 四个分层探针。
- 发现 Host `/bin/echo` 可表面成功但实际命中 Android `/bin`；改用 GNU/Linux 身份和 Ubuntu 文件视图断言后，Host 三类外部执行失败、PRoot 通过。
- 统一请求新增 `NO_CHILD_PROCESS` 与 `VERIFIED_NATIVE_IMPORTS` 肯定式保证；Python 缺少任一保证即在资产准备和业务进程创建前回退 PRoot。
- 受管入口只接受裸 Python 命令或稳定 `.kf/bin` 链接；每次计划重新解析当前解释器目标，活动 venv 回退，并映射 Python 专用环境路径。
- `venv(with_pip=True)` 当前 Host 因子解释器不可执行失败，PRoot 因 `ensurepip` 非零失败；两车道均未开放，后续优先验证受管 `uv`，不伪装成已解决。
- 定向测试、Debug 构建、覆盖安装与 Layered 真机模式通过；无 ANR/FATAL。下一恢复指针为 RF252，将肯定式保证接入生产声明，旧记录继续安全回退。

### 2026-07-31 RF240 验收

- 将启动器、兼容库和 rootfs 身份副本准备提取为入口无关的 `GlibcHostRuntimePreparer`；Node 既有嵌入资产未改变，因此没有重复 Node 性能矩阵。
- 新增 `HostPythonRuntimeProvider` 与 `ManagedRuntimeLaunchPlanner`，只接收受管结构化 Python argv；终端、Agent、后台入口继续只创建一条实际运行通道并写回真实 lane/reason。
- subprocess、完整 Linux、PTY、View、未验证原生扩展、`-m pip` 与 `-m venv` 均在 Host 进程创建前回退 PRoot；身份损坏继续失败关闭。
- 目标回归覆盖 7 个 suite、37 项测试，0 failure、0 error、0 skipped；Debug 构建和一加 8T 覆盖安装成功。
- 真机直接执行生产 Provider 成功；完整通用资产矩阵 20 组对照、40 个通道测点零失败，无新增 ANR/FATAL。六个发布门测点 p50 降低 45.0%～85.3%。
- 兼容边界与 RF230 一致：stdlib/内置扩展/pip 入口/纯 wheel/venv 创建通过，Python subprocess 与 venv 子解释器保持 PRoot。
- 强制全量回归为 240 个 suite、1261 tests、0 failure、0 error、2 skipped；强制 Debug 构建成功。最终 APK 240595564 bytes，SHA-256 `FC9CED0CF82F286653F3F730EBB4815F73DCB4DE164DF7E84569626858136C21`，已覆盖安装到一加 8T 并复验生产 Provider。
- 下一恢复指针进入 RF250，只研究 Python 能力分层，不重做 Node 或 RF230 基线。

### 2026-07-31 RF230 验收

- OnePlus 8T 上复用同一份 Python 3.14.6，完成 5 类负载、1/4/8/16 并发、Host/PRoot 各 3 轮；20 组对照、40 个通道测点零失败。
- Host p50 相对 PRoot 降低 37.1%～86.6%；独立进程启动、import、小文件、CPU 和 I/O 均未出现反向退化。
- stdlib、内置 C 扩展、pip 入口、纯 Python wheel 安装和 venv 创建两条车道均通过。
- Host 的 Python subprocess 与 venv 子解释器失败，PRoot 通过；第三方 C 扩展未外推为兼容，三者固定为启动前 PRoot 能力门。
- Debug 基准入口只接受固定 Benchmark/Compatibility 动作；后台启动被系统拒绝时记录 trigger_rejected，不再让应用崩溃。
- RF230 给 RF240 的 go 只覆盖纯 Python 结构化 argv；下一恢复指针进入 RF240，不重复 Node 历史矩阵。

### 2026-07-31 RF220 验收

- 将 Node 债务扩展为 HN-001～HN-011；npm/plugin 生命周期与原生构建固定为 HN-010，运行中代次租约与升级回收固定为 HN-011。
- 为每个 HN 编号映射自动测试、历史性能证据或明确未关闭状态；HN-007、HN-010、HN-011 不冒充已解决。
- Node 的性能证据仍按“前提变化才重跑”，本任务没有再次运行真机矩阵。
- RF220 完成；后续 Node 真实问题按 HN 编号增量处理，当前恢复指针进入 RF230 Python go/no-go。

### 2026-07-31 RF210 验收

- 新增 `RuntimeExecutionProvider<C, T>` 标准接口，不强迫不同 Provider 共享不合适的 Android/container 准备上下文。
- `HostNodeRuntimeProvider` 显式实现标准接口，Provider kind、公共请求和统一结果合同均已接通。
- `HostNodeLaunchPlanner` 继续拥有 Node 子进程 PRoot 合同；Provider 仍不创建进程、不写 Store、不感知页面。
- 定向回归覆盖 10 个测试套件、59 项测试，0 failure、0 error、0 skipped；未运行旧性能矩阵。
- RF210 完成，下一任务 RF220 只审计和固化未关闭债务，不重复修复已经关闭的 HN 项。

### 2026-07-31 RF100 父任务验收

- 按工具链合同执行 `:app:testDebugUnitTest --rerun-tasks`：238 个 suite、1250 tests、0 failure、0 error、2 skipped。
- 执行 `:app:assembleDebug --rerun-tasks`：60 个任务全部执行，`BUILD SUCCESSFUL`。
- 本地 APK 为 `app/build/outputs/apk/debug/app-debug.apk`，240431413 bytes，SHA-256 `81794AE06AFBEAD62ABBEF463B50663108F612AC3B337D7990E6678A28BC67F9`；构建目录由 `.gitignore` 排除，未安装、未提交、未上传。
- 新实验工作树缺少本地工具链参考，已从历史和实时 JDK/SDK/ADB/设备事实恢复 `references/toolchain.md`；该路径按仓库边界保持本地忽略。
- RF100 完成，恢复指针进入 RF210。Node 后续只做标准 Provider 适配与等价护栏，不重跑既有性能矩阵。

### 2026-07-31 RF130 验收

- 新增统一 `RuntimeProviderDecision` 与 Provider 种类，明确 Ready、Unsupported、Blocked 三种结果。
- `HostNodeRuntimeProvider` 保持既有能力判断，但只把 Unsupported 交给 PRoot；禁用回退或 Blocked 时直接停止。
- 终端、Managed Agent 和后台入口均处理 Blocked；Agent 直接断言 Blocked 时 PRoot 构建次数为 0。
- 定向回归覆盖 8 个测试套件、46 项测试，0 failure、0 error、0 skipped；`git diff --check` 通过。
- 下一步先执行 RF100 全量单测与 Debug 构建，不能用定向测试直接宣布地基完成。

### 2026-07-31 RF120 验收

- 新增 `RuntimeExecutionRequest`，严格区分结构化 argv、兼容命令文本和原生能力；加入完整 Linux、Android 原生、PTY 与 View 要求。
- 终端、Managed Agent 和后台运行均通过同一请求进入既有 `HostNodeLaunchPlanner`，没有改变 Node 原生兼容实现。
- 第一次编译发现 Planner 内残留旧 workingDirectory 变量，已改为请求字段后原命令重跑。
- 定向回归覆盖 7 个测试套件、43 项测试，0 failure、0 error、0 skipped。
- 补充 Node 对原生能力和完整 Linux 要求的前置拒绝护栏；只需重跑目标测试，不触发旧性能矩阵。

### 2026-07-31 RF110 验收

- 新增混合运行总架构、通用依赖快速通道、Android/NDK 原生能力和 PRoot 兼容 Provider 文档。
- 恢复 Node HN-001～HN-009 风险索引与 2026-07-31 性能矩阵，明确 RF210 不重复全量历史试验。
- 本地 Markdown 链接检查全部通过；`git diff --check` 通过。
- 复核现有代码中的 `/proc/self/fd/99`、robust、clone3、rseq、结构化 Node 计划、PRoot 准入和运行车道字段，文档没有把 Python 或新增原生能力写成已实现。
- RF110 完成，下一任务 RF120。

### 2026-07-31 RF110 启动

- 创建独立工作树 `D:\xm\Kite-runtime-foundation-lab` 和分支 `codex/runtime-foundation-lab`。
- 只读确认现有 Node 入口：`HostNodeRuntimeProvider`、`HostNodeLaunchPlanner`、终端、Agent 和后台结构化启动。
- 只读确认现有 PRoot 能力：`ProotJobAdmissionController`、`WarmProotRunnerPool`、STARTED 前回退边界和三档调优。
- 只读确认运行事实：`RunStateMutation`、`CardRunStore` 已持有 `runtimeLane` 与 `runtimeFallbackReason`。
- 旧 Node 文档位于本地集成历史，尚未进入当前 main；本任务将恢复正式风险索引，不重复历史性能工作。

## 待验证

- RF340b 将 APK Android action 与原生分发器接入目录，保持原执行器和状态拥有者。
- RF340c 做目录、禁止回退和无副作用查询回归；不为缺失 Keystore 实现补假入口。
- RF330 若未来更换归档引擎或出现单大文件 ZIP 样本，可按新证据重开性能门；当前多文件资源自动路由保持 PRoot。
- 动态 URL、多镜像和无尺寸上限下载不属于 RF310 已开放范围；将来只有形成可验证的结构化合同后才单独立项。
