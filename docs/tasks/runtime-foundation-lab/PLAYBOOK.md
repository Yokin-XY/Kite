# Kite 混合运行底座任务单

## 当前恢复指针

- 根任务：`RF000`
- 当前阶段：`RF1600` 进行中
- 当前任务：`RF1640` go/no-go 与父任务门
- 基线：`main@8223ba02d2a75b5df86e3fb15914c6a30e8b3da2`
- 冻结锚点：`8c046238b3c59094becc8f46df9857169a733649`
- 分支：`codex/runtime-foundation-lab`

## 根目标

建立一套不依赖资源 ID、应用名或单个样例的混合运行底座，使结构化执行请求在创建进程前选择：

1. 通用依赖快速通道；
2. Android/NDK 原生能力；
3. Ubuntu/PRoot 兼容底座。

选择结果必须只创建一个业务进程或一次原生能力调用，继续由现有运行状态拥有者记录实际通道和回退原因。

## 固定红线

- 不为 OpenClaw、Git、Python 包名或资源 ID 写路由特判。
- 不把 shell 字符串伪装成结构化 argv。
- Host 或原生执行开始后，不自动再运行一份 PRoot 业务任务。
- 不新增平行运行 Store；卡片运行继续归 `CardRunStore`，后台运行继续归 `BackgroundRuntimeRegistry`。
- 页面不选择运行通道、不扫描运行资产、不复制运行事实。
- PRoot View 只用于显式更新保护或危险文件操作，不回到普通全局运行链。
- 旧 Node 性能矩阵不重复执行；只有代码、Node/rootfs 代次或证据前提变化时才重跑受影响测点。

## 总分任务树

### RF100 [P0 地基] 通用路由地基

父任务验收：三个 Provider 使用同一请求、能力、选择、回退和结果合同；现有 Node/PRoot 行为等价；运行事实仍写回原状态拥有者。

状态：已完成。强制全量单测与 Debug 构建已通过；三入口等价，下一阶段不得重新发明公共请求或状态源。

#### RF110 三车道架构与验收合同

- 问题证据：现有 `HostNodeLaunchPlanner` 只表达 Host Node/PRoot 二选一；三车道总架构、原生 Provider 和 Python 候选尚无正式文档。
- 解法：建立总架构及三份 Provider 文档，恢复 Node 风险索引和性能证据，固定任务树、路由不变量和验证分层。
- 验收标准：
  - [x] 总架构和三份 Provider 文档可互相链接；
  - [x] Node 已验证事实与待验证事项分离；
  - [x] 每条车道均列出首个特例、回退边界和完成门；
  - [x] 文档不宣称 Python 或新增原生 Provider 已实现。
- 依赖：无。

#### RF120 统一 Execution Request 与能力声明

- 问题证据：Node 请求、Recipe 请求、Agent 启动和后台启动仍使用不同形状，入口容易复制选择逻辑。
- 解法：建立入口无关的结构化请求、环境/ABI/解释器要求、允许车道和副作用阶段合同；先适配现有 Node 与 PRoot。
- 验收标准：
  - [x] 普通 argv 与显式 shell 不能混淆；
  - [x] 请求不包含资源 ID 或应用名称路由字段；
  - [x] Node/PRoot 选择器可由既有入口调用；
  - [x] 现有 Node 行为无变化。
- 依赖：RF110。

#### RF130 Provider 选择、失败关闭与证据

- 问题证据：`runtimeLane`/`runtimeFallbackReason` 已存在，但选择原因尚未统一为可扩展 Provider 结果。
- 解法：统一 Ready/Unsupported/Blocked 结果，在进程创建前完成选择，明确可回退阶段和实际车道证据。
- 验收标准：
  - [x] Host Ready 不构造 PRoot；
  - [x] Host 不满足能力时只构造一次 PRoot；
  - [x] STARTED 后错误不自动重放；
  - [x] 卡片和后台状态保留实际车道与稳定原因。
- 依赖：RF120。

### RF200 [P1 快速通道] 通用依赖快速通道

父任务验收：依赖运行时按解释器、ABI、环境与能力选择宿主执行；不安全或不可表达时在启动前回到 PRoot。

#### RF210 现有 Node 标准 Provider 化

- 问题证据：Node 已验证并投入使用，但类型和入口仍以 Node 专名直接耦合选择器。
- 解法：只做等价适配，把现有 `HostNodeRuntimeProvider` 接入统一合同，不重写兼容层、不重复历史性能试验。
- 验收标准：
  - [x] 既有 Node 单测继续通过；
  - [x] npm/npx/pnpm/openclaw 的受管 shebang 路由保持；
  - [x] 现有回退原因和唯一进程语义保持；
  - [x] 只有触及证据前提时才重跑对应真机矩阵。
- 依赖：RF130。

#### RF220 Node 长期债务门

- 问题证据：HN-007、npm/plugin、运行中旧代次回收等尚有明确边界。
- 解法：保留编号风险索引，按真实样本或版本变化增量关闭，不因其他 Provider 开发而重验全部 Node。
- 验收标准：
  - [x] 每个新增风险映射到 HN 编号或新增编号；
  - [x] 不兼容项在启动前回退；
  - [x] 证据变化才触发专项回归。
- 依赖：RF210。

#### RF230 Python 可行性与性能基线

- 问题证据：Python 是候选通用依赖，但尚未证明 Android 应用域下的解释器、路径、stdlib、subprocess、wheel 与 C 扩展边界。
- 解法：先做只读/实验性矩阵，与相同 Python 的独立 PRoot 路径对照。
- 验收标准：
  - [x] 覆盖独立进程启动、import、小文件、CPU、I/O、1/4/8/16 并发；
  - [x] 覆盖 subprocess、venv、pip、纯 Python wheel、代表性内置 C 扩展；
  - [x] 形成 go/no-go 结论，不以单个脚本成功代替兼容结论。
- 依赖：RF210。

#### RF240 纯 Python 结构化命令快速通道

- 问题证据：若 RF230 证明有稳定收益，最小安全范围应先排除 shell 展开和 C 扩展。
- 解法：只接受结构化 Python argv、可表达 cwd/env/stdio 和满足身份门的资产；不满足时启动前回退 PRoot。
- 验收标准：
  - [x] 无应用名特判；
  - [x] Host/PRoot 同输入语义对照通过；
  - [x] 唯一进程、取消、退出码和输出完整；
  - [x] 真机收益达到 RF230 预先固定的发布门。
- 依赖：RF230 的 go 结论。

#### RF250 Python subprocess、venv、pip 与扩展分层

- 问题证据：Python 的外部命令、环境隔离和原生扩展不能随纯脚本能力自动宣称兼容。
- 解法：按 subprocess、venv/pip、纯 wheel、C 扩展分别建立能力门；无法安全表达的任务整条回 PRoot。
- 验收标准：每一层都有独立兼容矩阵、回退证明和升级失效测试。
- 状态：已完成。子进程与 venv 保持 PRoot；第三方扩展只按精确 CPython ABI 证据开放，不按包名放行。
- 依赖：RF240。

##### RF251 肯定式 Host 安全门

- [x] 空能力声明不再被当成无子进程证明；
- [x] 受管 Python 身份不接受 `.kf` 下的 venv/任意路径；
- [x] 解释器升级后每次重新解析当前目标，不复用旧版本；
- [x] `PYTHONPATH`/`PYTHONSTARTUP` 映射，活动 `VIRTUAL_ENV` 回退。

##### RF252 保证字段的生产声明与透传

- [x] Agent 与后台结构化启动可以声明固定枚举保证；
- [x] 清单、自定义登记和持久化均拒绝未知值；
- [x] 旧记录缺省为空并保持 PRoot，不改变 Node；
- [x] 通用 Debug 清单夹具从声明走到真实 `host_python`，不是资源 ID 特判。

##### RF253 subprocess、shell、exec 与 venv/pip 矩阵

- [x] 独立 Layered 真机模式覆盖 Linux 身份、shell 视图、`execve` 与带 pip venv；
- [x] Host/PRoot 结果按语义判定，不以进程退出 0 冒充等价；
- [x] `venv(with_pip=True)` 两车道均失败，未强行开放。

##### RF254 第三方 C 扩展与包生命周期

- [x] 固定可复现的纯 wheel 与代表性 ARM64 扩展源码、两代 wheel 元数据；
- [x] 直接导入、不可变代次安装/升级、解释器 ABI 换代和失效路径分别验证；
- [x] 未验证扩展保持 PRoot，不建立包名白名单。

### RF300 [P2 原生能力] Android/NDK 原生 Provider

父任务验收：适合 Android 的通用能力无需创建 PRoot，同时保留权限、生命周期、取消和结果合同。

#### RF310 下载与 SHA-256 校验

- 问题证据：下载和摘要是跨资源通用能力，Android 已有网络与加密 API，使用 PRoot 只增加进程和路径成本。
- 解法：以结构化 URL、目标句柄、校验值、超时和取消表达原生任务，不接收任意 shell。
- 验收标准：覆盖成功、断点/重试边界、校验失败、取消、临时文件清理、VPN/私有 DNS 继承和大文件压力。
- 依赖：RF130。

##### RF310a 封闭 Provider 与流式执行器

- [x] 只接受 HTTPS、受控目标根、最大字节数和封闭参数；
- [x] 同目录临时文件、流式 SHA-256、验证后原子发布；
- [x] 摘要失败保留旧目标，取消/失败清理临时文件；
- [x] 重试从零开始，未知 Range/206 和 HTTPS 降级失败关闭；
- [x] OnePlus 8T 使用 Android 应用网络栈完成固定 HTTPS 真机探针。

##### RF310b Recipe/Run 正式接线

- [x] 新增结构化原生能力步骤，不从 `curl` 或资源 ID 推断；
- [x] 同一 `CardRun` 写入 `android_native` 车道、进度、结果与取消事实；
- [x] 页面只消费运行状态，不创建终端或整页刷新。

##### RF310c 资源获取迁移与压力门

- [x] 静态 HTTPS、单一 URL、受控目标且声明尺寸上限的前置下载显式编译为原生能力；
- [x] 动态 URL、多镜像或无尺寸上限下载保持 PRoot；只有新增结构化合同后才另行开放；
- [x] 固定 PRoot 对照、网络中断/重试、空间不足和大文件压力；
- [x] 原生下载只落资源缓存，更新锁、备份、验证和安装状态拥有者保持不变。

#### RF320 文件操作

- 问题证据：复制、移动和受控删除可由 Android 文件 API 完成，但必须服从现有文件保护与状态所有权。
- 解法：声明式文件能力接入现有保护协调器，不绕过更新锁和备份边界。
- 验收标准：路径约束、原子替换、失败恢复、权限错误和取消均可验证。
- 依赖：RF310。

##### RF320a 封闭文件 Provider

- [x] 复制、移动和删除分别使用固定能力 ID 与结构化参数，不接收任意 shell；
- [x] 根目录显式声明读、创建、替换和删除权限，更具体的受控根优先；
- [x] 不跟随符号链接、不递归删除，路径越界和未授权删除失败关闭；
- [x] 复制使用同目录临时文件和原子发布，移动不对非原子实现静默降级。

##### RF320b Recipe/Run 与取消

- [x] 三项能力接入现有 `native_capability` 步骤和同一 `CardRun`；
- [x] 流式复制可取消并清理临时文件，移动/删除在提交前响应取消；
- [x] 不创建终端、进程 owner 或第二份运行状态。

##### RF320c 资源事务边界

- [x] 完成资源步骤审计；当前没有脱离活动安装事务的安全迁移点，不强行迁移；
- [x] 活动安装根继续由资源级锁、单份备份、验证和回滚事务持有；
- [x] 不重新接入已退出正式链的 View/`ResourceTransactionCoordinator`。

#### RF330 安全归档能力

- 问题证据：解包有路径穿越、链接、权限和大文件风险，不能因“原生更快”直接替换 Linux 工具。
- 解法：只支持明确格式与安全策略；需要完整 Linux tar 语义时保留 PRoot。
- 验收标准：格式矩阵、zip-slip、符号链接、权限、空间不足和回滚测试通过。
- 依赖：RF320。

##### RF330a ZIP 安全 Provider

- [x] 第一版只开放普通文件和目录 ZIP；tar/tar.gz 与 Linux 权限/链接语义保持 PRoot；
- [x] 显式限制压缩包大小、条目数、总输出、单文件、深度和膨胀比；
- [x] 拒绝绝对路径、`..`、反斜杠、重复条目、符号链接和特殊文件；
- [x] 只向同级暂存目录解包，成功后原子发布，失败或取消不暴露半成品。

##### RF330b Recipe/Run 与取消

- [x] 接入现有 `native_capability` 与同一 `CardRun`；
- [x] 进度、失败、取消和清理沿用原生运行链，不创建终端或进程。

##### RF330c 资源与真机门

- [x] 完成资源审计；当前没有静态 ZIP 安全迁移点，且真机性能 no-go，不强行迁移；
- [x] tar/tar.gz、动态路径、链接或权限保真继续 PRoot；
- [x] 真机覆盖 ZIP 压力、恶意条目、空间不足、取消和 PRoot 对照。

#### RF340 Android 系统能力目录

- 问题证据：APK 安装、网络事实、权限与系统服务已有 Android 实现，但尚未统一声明为可路由能力。
- 解法：建立能力目录，复用现有 PackageInstaller、网络、Keystore 等实现，不复制业务状态。
- 验收标准：每项能力明确权限门、可用性、结果和禁止回退行为。
- 依赖：RF310。

##### RF340a 真实能力与所有者目录

- [x] 只登记已有生产入口，不把名称相似或计划中的能力写成可用；
- [x] 每项固定调用形态、权限门、结果拥有者、完成语义和回退边界；
- [x] APK 安装明确为外部安装器交接，网络对齐与权限快照保持原拥有者；
- [x] 未发现正式 Keystore Provider 时明确排除，不伪造条目。

##### RF340b 薄适配与运行证据

- [x] 既有 Android action 通过目录映射到稳定能力 ID，不复制执行逻辑；
- [x] 原生 Recipe 只使用目录已有项，未知项失败关闭；
- [x] 运行事实继续写入原状态拥有者。

##### RF340c 回归与父任务门

- [x] 目录唯一性、权限门、交接语义和禁止回退有机器测试；
- [x] Debug 构建通过；目录是无 Android 上下文的纯查询，本阶段没有为验收触发系统安装器副作用。

### RF400 [P3 兼容底座] Ubuntu/PRoot Provider

父任务验收：任意需要完整 Linux 语义的任务保持兼容；优化只针对经准入的任务类型，不改变交互、信号和副作用语义。

#### RF410 现有 PRoot 标准 Provider 化

- 问题证据：PRoot 已是正式兼容底座，但仍散落在 shell、终端、Agent 和后台入口。
- 解法：先做等价适配，使它成为统一 Planner 的最终兼容 Provider，不改变当前 rootfs、网络、bind 和 owner 合同。
- 验收标准：普通终端、复杂 shell、Linux ELF、编译器和显式 View 路径保持原行为。
- 依赖：RF130。

##### RF410a 兼容 Provider 合同

- [x] 建立只表达结构化请求、工作目录、环境、PTY 与 View 事实的 PRoot 逻辑计划；
- [x] PRoot 作为最终 Provider 返回 `Ready`，Android 原生能力不得伪装成 PRoot 回退；
- [x] 不复制 `KFContainerManager` 的 rootfs、网络、bind、遥测和物理 argv 规则。

##### RF410b 正式入口等价适配

- [x] Managed Planner 的 `Unsupported` 在启动前生成唯一 PRoot 计划，`Blocked` 和禁用回退仍失败关闭；
- [x] 普通终端、资源 shell、Agent 与后台入口消费同一逻辑计划，继续只创建一条业务进程；
- [x] 显式 View、运行 owner、结果出口和停止链保持原拥有者。

##### RF410c 回归与父任务门

- [x] 普通终端、复杂 shell、Linux ELF、编译器现状、Agent、后台与显式 View 合同通过；
- [x] Debug 构建与必要真机链通过，未重跑已冻结的 Node 性能矩阵。

#### RF420 负载分类与准入

- 问题证据：现有 `ProotJobAdmissionController` 已支持 lane 和压力收缩，但不能据此宣称所有 PRoot 已统一调度。
- 解法：调用方逐个声明 jobId、lane、读写属性、owner、取消和结果出口。
- 验收标准：交互优先、写屏障、公平性、压力收缩和关闭行为通过测试。
- 依赖：RF410。

##### RF420a 完整任务身份合同

- [x] 准入请求强制声明 jobId、owner、lane、读写属性、取消出口和结果出口；
- [x] 固定资源采样任务及 debug 基准使用完整合同，缺失 owner 在准入前失败关闭；
- [x] 普通终端、任意 shell、Agent 和长期服务仍明确标为未迁移，不冒充统一调度。

##### RF420b 队列生命周期闭环

- [x] 同一活动或排队 jobId 不能重复取得准入；
- [x] 排队任务可按稳定 jobId 取消，释放等待和写屏障且不杀死已开始任务；
- [x] snapshot 可区分活动、排队、取消和超时，不接管业务结果。

##### RF420c 策略与父任务门

- [x] 交互优先、同优先级 FIFO、共享写防饥饿、压力收缩和关闭行为通过回归；
- [x] 固定维护任务的 warm/独立回退继续共用同一 lease，Debug 构建通过；
- [x] 形成下一阶段可接入的有界短任务门，不按命令名或资源 ID 路由。

#### RF430 有界短任务温热 Runner

- 问题证据：现有温热 Runner 仅正式接入固定维护任务；任意用户 shell 和长期服务不满足复用前提。
- 解法：只扩展到无交互、边界明确、STARTED 后禁止重放的通用任务类别；Git 只能作为测量样本，不能成为路由条件。
- 验收标准：stdio、取消、timeout、进程树回收、身份失效、STARTED 边界和独立 PRoot 对照通过。
- 依赖：RF420。

##### RF430a 通用有界短任务执行器

- [x] 只接收代码 owner 声明的结构化 argv，限制 wait、runtime 和双流输出，不接受 shell 文本或交互 lane；
- [x] warm 与独立 PRoot 共享同一 admission lease，独立路径复用同一 argv/cwd/env/timeout/output 合同；
- [x] 固定资源采样迁移到通用执行器，删除其私有独立进程实现。

##### RF430b 首个新增生产调用方

- [x] 仅迁移容器进程表只读查询；kill、任意 shell、Agent、终端和长期服务保持原路径；
- [x] 查询声明稳定 owner、唯一 jobId、`PROBE/READ_ONLY`、有界 stdio 和 timeout；
- [x] 真机验证 warm、结果解析与无残留子进程；独立回退对照留在 RF430c，不从命令名外推其他任务。

##### RF430c 父任务门

- [x] STARTED 前可回退、STARTED 后不重放、timeout/取消/崩溃/身份失效合同通过；
- [x] Debug 构建和目标真机对照通过，收益与 no-go 边界分别记录。

#### RF440 可调性能档位

- 问题证据：占满核心不等于吞吐最优，固定并发会在不同手机和压力状态下失真。
- 解法：保留低负载、均衡、高性能三档，以实测吞吐曲线和压力门决定准入，不强杀已启动任务。
- 验收标准：多设备或至少目标设备的吞吐、P95、RSS、ANR、失败率和回收行为可复算。
- 依赖：RF430。

##### RF440a 单一性能档参数源

- [x] 准入上限、温热 Runner 上限和空闲回收由同一档位策略生成；
- [x] 内置低负载/均衡/高性能保持 1/2/4 与 2/30/120 秒；
- [x] CUSTOM 从 lane 上限推导同一受限值，生产上限不超过 4，debug 校准覆盖与生产策略分离。

##### RF440b 动态策略与可观测性

- [x] active profile、前后台和压力变化只收缩后续准入，并回收超额空闲 Runner；
- [x] 暴露当前档位、配置/有效上限、温热上限、空闲时间和 active/queued，不新增平行状态；
- [x] 生产策略变化与现有 RuntimeHealth 快照同源，不能由页面或 Ubuntu 直接控制进程池。

##### RF440c RF400 父任务门

- [x] 三档、CUSTOM、压力收缩、前后台 lane、动态 trim 和不强杀活动任务回归通过；
- [x] OnePlus 8T 复算吞吐、P95、内存、失败率和空闲回收，形成保留/调整结论；
- [x] RF400 完成，普通 PRoot 兼容路径与 View 边界不变。

### RF500 [P0 生产扩展] PRoot 实际控制面与第二生产样板

父任务验收：不扩大任意 shell、终端、Agent 和长期服务准入范围的前提下，让正式健康面能看到真实 admission/warm 状态，关闭冷启动假性单并发，并把一个高频代码自有任务迁入有界 Runner。

#### RF510 实际调度状态正式投影

- 问题证据：`WarmProotExecutionCoordinator.TuningSnapshot` 只被 Debug 探针读取；`RuntimeHealthStore` 的 `prootPoolPlan` 是规划/推演结果，不等于实际 admission、queue 和 warm session。
- 解法：从现有 coordinator 即时投影实际档位、配置/有效上限、压力、前后台、active/queued、warm active/idle/stale 和空闲年龄到正式 RuntimeHealth 文本，不新增 Store，不记录 argv、路径或用户输入。
- 验收标准：
  - [x] 正式 RuntimeHealth 输出明确区分 `planned` 与 `actual`；
  - [x] actual 字段只来自 coordinator 当前 policy、admission 和 pool；
  - [x] 未创建 pool 时可安全输出零会话，不因诊断读取创建 PRoot；
  - [x] 单测证明字段、来源和无敏感 payload，Debug 构建通过。
- 依赖：RF440。

#### RF520 冷启动策略接力

- 问题证据：coordinator 在第一份 `RuntimeHealthSnapshot` 到达前使用 `pressure=UNKNOWN`，均衡/高性能档有效上限均为 1；OnePlus 8T 冷探针已复现 `configuredMax=2 effectiveMax=1`。
- 解法：冷启动只复用现有 host MemAvailable 压力判定和默认 workload profile；内存信号可靠时按真实压力给出 1/2 起步值，信号缺失或高压仍保持 1，首份健康快照到达后由正式策略完全接管。
- 验收标准：
  - [x] host 可用内存正常时，默认均衡冷启动不再假性单并发；
  - [x] host 信号缺失、高压和临界压力仍保守为 1；
  - [x] 正式快照可覆盖 bootstrap policy，不存在第二控制源；
  - [x] OnePlus 8T 冷进程探针显示来源、配置上限和有效上限。
- 依赖：RF510。

#### RF530 有界执行结果遥测

- 问题证据：现有 snapshot 只有 admitted/timedOut/cancelled 和队列数量，无法区分 warm、独立回退、拒绝、STARTED 后失败及实际等待/执行耗时。
- 解法：为 `BoundedProotTaskExecutor` 增加固定低基数聚合，按 route/result/lane 记录累计次数与有界时延桶；只保留数字和枚举，不保存 argv、cwd、env、输出或 owner 原文，并投影到 RF510 的同一健康面。
- 验收标准：
  - [x] warm、独立回退、准入拒绝、STARTED 后失败和 fallback 失败可区分；
  - [x] 记录 queue/execute/total 的计数与有界时延，不引入高基数标签；
  - [x] 并发更新不丢计数，清零仅限测试；
  - [x] RuntimeHealth 读取不触发执行或扫描。
- 依赖：RF510。

#### RF540 Supervisord 健康采集有界 Runner 样板

- 问题证据：`SupervisordServiceHealthStore` 的健康刷新是高频内部任务，但当前用复杂 shell 每次新建独立 PRoot；它同时包含 `supervisorctl update/status` 和固定日志尾部，不能把 shell 文本伪装成结构化 argv。
- 解法：由 Android 控制面生成固定版本的容器 helper，调用方只执行结构化 helper argv；声明稳定 owner、`SERVICE/SHARED_WRITE`、timeout、输出和结果合同，STARTED 前可独立回退，STARTED 后不重放。
- 验收标准：
  - [x] helper 内容、路径和版本由代码拥有，不接受外部命令参数；
  - [x] 既有 update/status、日志 marker、退出码和解析语义保持；
  - [x] 调用方不再直接 `ProcessBuilder` PRoot，失败仍返回原 `CommandResult` 边界；
  - [x] 单测、Debug 构建和 OnePlus 8T 冷/温对照通过，无残留任务。
- 依赖：RF530。

#### RF550 RF500 父任务门

- [x] RF510～RF540 联合回归通过，实际/规划状态边界清楚；
- [x] 冷启动、温热复用、空闲回收、压力收缩和服务健康链真机通过；
- [x] 形成下一阶段长生命周期 owner lease 的 go/no-go，不直接迁移终端或 Agent。

### RF600 [P1 预研] 长生命周期 owner lease

父任务方向：让长期服务的准入 lease 与真实 runtime owner 同寿命，先建立合同与模拟器，再决定是否迁移一个后台服务；终端和 Agent 必须各自通过独立生命周期门。

#### RF610 长短任务合同与状态机

- 问题证据：短任务 lease 随调用栈释放；长期 owner 在调用返回后仍运行，还会经历重连、停止、进程外死亡和应用恢复。短任务 `SHARED_WRITE` 若直接套给服务，还会让常驻进程永久占住互斥写锁。
- 解法：建立不创建进程的纯状态机，分离 owner kind、lane、文件系统姿态、进程身份和 lease phase；所有非法/重复转换显式返回结果，不抛出隐藏控制流。
- 验收标准：
  - [x] request→admitted→starting→running→stopping→released 正常链完整；
  - [x] 相同进程 attach 幂等，不同进程身份拒绝；
  - [x] 启动前失败可释放，运行后丢失先进入 orphan review 并继续占容量；
  - [x] 只有停止确认或死亡确认释放容量，不接生产 Store、不创建进程。
- 依赖：RF550。

#### RF620 容量、互斥与公平性模拟器

- 解法：以 lease state machine 为唯一记录，模拟容量、压力只约束新准入、exclusive maintenance 屏障、同 owner 去重和优先级/FIFO 排队。
- 验收标准：
  - [x] 既有 owner 不因压力被强杀，高压只阻断新的非必要 owner；
  - [x] 重复 owner 不多占容量，也不能静默替换既有 spec；
  - [x] exclusive maintenance 建立有界屏障且不饥饿，被压力阻断时不堵必要任务；
  - [x] lane 满时不阻塞其他可运行 lane，同优先级保持 FIFO。
- 依赖：RF610。

#### RF630 重启恢复与 orphan reconciliation

- 解法：模拟应用重启后的记录恢复、同进程重连、PID 复用防护、无进程进入 orphan review、死亡确认释放和 owner 主动停止竞争。
- 验收标准：
  - [x] 恢复不创建第二进程，每个 owner 只保留一个最高代次决定；
  - [x] PID 相同但启动代次不同视为复用并进入 orphan review，不重连；
  - [x] 未观察到进程时继续占容量，只有死亡确认释放；
  - [x] 恢复与停止竞态由停止意图优先确定化，冲突记录显式进入 review。
- 依赖：RF620。

#### RF640 长期 lease 规划态可观测性

- 解法：只投影纯模拟器的低基数 phase/kind/lane/容量数字，明确 `planned_not_production`；不写入 RF510 的 actual 字段。
- 验收标准：
  - [x] 固定 `proot_long_planned_*` schema 与 `planned_not_production` scope；
  - [x] 只输出 phase/kind/lane/action/process-match 枚举计数和容量数字；
  - [x] 不含 ownerId、leaseId、PID/代次、路径、命令或 Agent/session 身份；
  - [x] owner 数增长不扩张字段集合，读取不可变快照无副作用。
- 依赖：RF630。

#### RF650 RF600 父任务门

- [x] 合同、容量、恢复、停止和可观测性模拟器联合回归通过；
- [x] 后台服务只允许进入身份桥接准备，当前生产迁移 no-go；
- [x] 终端和 Agent 继续保持 no-go，除非分别新增生命周期证据。

### RF700 [P2 预研] 设备自适应校准

父任务方向：在 1/2/4 固定安全档上研究可回滚的设备级校准，只消费可信内存、前后台和失败率信号；热状态没有可靠来源前不伪造自动升档。

状态：已完成。规划合同闭环；内存压力收缩已由现有 actual admission 生产实现，失败率调档与自动升档保持 no-go。

#### RF710 既有校准合同与实际策略对齐审计

- 问题证据：仓库已有 observe-only 的 `RuntimeProotDeviceCalibrationDryRun` 与 overlay，但 RF400 的正式 admission/pool 已统一到 `ProotPerformanceTunings` 1/2/4；不能另写第二套校准器或把历史 tracee 上限直接当并发上限。
- 验收标准：
  - [x] overlay/health/automation 与 actual coordinator 的真实读写路径已核清；
  - [x] loader 对 schema 与 declared valid 失败关闭，缺字段不再默认有效；
  - [x] tracee 校准只作为安全 guard，不直接选择 1/2/4 或消费旧 profileLimits；
  - [x] 显示档位从唯一 `ProotPerformanceTunings` 派生，不改变生产 coordinator。
- 依赖：RF650。

#### RF720 可信信号归一与升降级门

- 解法：只消费实际 coordinator 失败率/时延、可信内存压力和前后台；无热信号时最多维持或降级，禁止自动升档。
- 验收标准：
  - [x] 累计遥测按单调计数器生成带时间边界的差量窗口，重复 key、计数回退和桶总数矛盾失败关闭；
  - [x] 非正式 policy source、未知/陈旧信号、零样本和少样本只保持，不产生升档；
  - [x] HIGH/CRITICAL 内存、可信 thermal hot 或显著失败率只建议在 1/2/4 中降一级，不强杀运行任务；
  - [x] 可信 thermal normal、前台、tracee guard、足量成功样本和 P95 门全部通过时，也只产出 promotion window candidate；
  - [x] 实现是 `planned_not_production` 纯函数，`changesCoordinator=false`，不读取页面、不修改正式 coordinator。
- 依赖：RF710。

#### RF730 候选档、迟滞与回滚模拟器

- 解法：候选只在 1/2/4 中移动一级；连续窗口确认、冷却期、回滚阈值与失败预算统一为纯状态机。
- 验收标准：
  - [x] 三个连续健康窗口才给一次升一级建议，HOLD 会清空 streak，未应用建议不重复发出；
  - [x] 应用升档确认后进入冷却并保留相邻 rollback target，冷却只阻止升档、不阻止安全降档；
  - [x] HIGH/CRITICAL 或可信过热绕过冷却立即建议降一级，失败率需连续两个坏窗口耗尽预算；
  - [x] 重启恢复、actual 外部变化、损坏状态与不相邻窗口全部 rebase/reset，不据旧状态跳级；
  - [x] 纯状态机标记 `planned_not_production`，不写策略文件、不修改 coordinator。
- 依赖：RF720。

#### RF740 规划建议与 actual 边界

- 解法：输出独立 planned 建议与证据计数；实际档位仍只来自 RF510 coordinator，应用动作另立生产门。
- 验收标准：
  - [x] actual 只作为 RF510 不可变快照引用，planned 固定标记 `planned_not_production`；
  - [x] actual、窗口、迟滞 state/target 不一致或非相邻时投影失败关闭为 `CONTRACT_MISMATCH`；
  - [x] 固定 schema 只输出枚举、布尔和数字，不含 owner、PID、路径、命令、session 或资源身份；
  - [x] 投影只消费调用方参数，不回读 coordinator、collector、文件或页面，不产生应用动作。
- 依赖：RF730。

#### RF750 RF700 父任务门

- [x] 对齐、信号、迟滞、回滚和观测合同联合回归通过；
- [x] 内存压力自动收缩沿用现有 actual admission；失败率调档因缺少并发因果证据保持 no-go；
- [x] 没有可信 thermal 信号时自动升档保持 no-go；RF700 新代码全部无生产装配。

### RF800 [P1 生产准备] 后台长期 owner 强身份桥接

父任务方向：沿 RF650 的唯一 go 方向，只为 `BackgroundRuntimeRegistry` 补齐 PID 启动代次、停止确认和恢复证据，使后台服务未来可接长期 owner lease；不顺带迁移终端或 Agent，不创建第二实例。

#### RF810 后台运行身份与停止链审计

- 解法：核清 `BackgroundRuntimeRecord`、`HostProcessRecord`、真实进程创建/attach/stop、JSON 恢复和 `/proc/<pid>/stat` 读取边界，形成最小字段与迁移顺序。
- 验收标准：
  - [x] 创建、handle、PID 发布、应用重启、外部 attach、停止、协调和恢复生产链已逐项核清；
  - [x] PID-only、command token/statusCommand 弱归属和无条件 STOPPED 缺口分别定位；
  - [x] 确认 `/proc stat` 已读取但缺第 22 字段，且跨设备重启还必须增加 boot identity；
  - [x] 固定 RF820～RF840 最小字段、写入不变量、停止顺序和类别边界；本叶未修改生产行为。
- 依赖：RF650、RF750。

#### RF820 强进程身份值对象与持久化

- 解法：宿主同轮 `/proc` 观察生成 `(bootId, hostPid, processStartTicks)`；后台 JSON 保存 boot/代次，只有同 boot 后才转换为 RF610 的 PID+代次身份。旧记录缺失时保持 review/no-attach。
- 验收标准：
  - [x] `/proc stat` 字段 22 在 comm 含右括号时仍正确解析，Host snapshot 只为应用 UID 进程生成同 boot 强身份；
  - [x] boot ID 严格规范化；`ps -A` fallback、缺 start ticks、缺/坏 boot ID 都不能生成身份；
  - [x] 后台 JSON 向后兼容保存 boot/start ticks，旧/部分/坏字段的派生身份失败关闭；
  - [x] PID 不变保留身份，PID 改变/清空同步清理；定义刷新保留同一运行事实；
  - [x] Registry 只在活动记录 PID 与观察 PID 精确相等时原子写身份，本叶不改变 attach/kill/恢复。
- 依赖：RF810。

#### RF830 停止确认与单实例恢复桥

- 解法：先恢复 owner，再按强身份 attach；停止意图优先，未找到或不确定时不启动替代进程、不释放容量。
- 子任务：
  - [x] `RF831` 纯决策合同：只有 boot+PID+start ticks 精确一致才允许 attach/发信号，决策本身永不创建进程；
  - [x] `RF832` 创建后采集强身份，应用重启时用同一合同精确探测；
  - [x] `RF833` expected stop 先落盘，确认原代次退出后才写 STOPPED 和释放容量；
  - [x] `RF834` 启动/停止竞态、重启、外死、PID 复用、重复 attach 的回归与 OnePlus 8T 证据；PRoot 停止同时收敛完整 owner 树。
- 验收标准：四个子任务全部通过；终端/Agent 无净变化。
- 依赖：RF820。

#### RF840 后台类别生产试接

- 解法：仅当 RF830 的真实证据完整时，把后台服务接入 RF610～RF640 长期 lease；否则形成明确 no-go 报告。
- 状态：已完成，结论为 no-go。强身份、单实例和停止证据通过；统一容量、STARTING lease 持久化、actual 观测与 detached/reboot 证据未闭环，因此未修改生产准入。
- 验收标准：唯一 owner/进程、容量寿命、压力只影响新准入、停止确认、恢复和 actual 观测均通过真机门；任一缺失即保持 no-go。
- 依赖：RF830。

#### RF850 RF800 父任务门

- [x] 强身份、停止和恢复联合回归通过；
- [x] 后台服务生产接入形成 no-go 与 OnePlus 8T 证据；
- [x] 终端和 Agent 保持 no-go，Node/Python 冻结矩阵未重跑。

### RF900 [P1 后续] 短任务与长期 owner 统一容量仲裁

父任务方向：解开 RF840 的唯一生产阻断，让短任务 lease 和后台 PRoot owner lease 共享同一 1/2/4 实际容量、压力与维护屏障；`BackgroundRuntimeRegistry` 继续拥有后台身份和运行状态，不迁移终端或 Agent。

#### RF910 统一容量快照与不变量

- 状态：已完成，只读合同未接生产。
- [x] 短任务 controller 在同一锁内输出 active/queued 逐 lane 计数；
- [x] 长期 `ADMITTED/STARTING/RUNNING/STOPPING/ORPHAN_REVIEW` 与短任务合并计数，REQUESTED 只排队，RELEASED 不占容量；
- [x] 压力缩档只产生 overcommitted 并禁止新准入，不驱逐既有 holder；独占维护和重复 owner/进程身份失败关闭；
- [x] 快照只有枚举、布尔和计数，不含 owner、PID、命令、路径或输出；不读 Store、不创建进程、不接后台生产启动。

#### RF920 后台 provisional lease 持久化

- 状态：已完成，持久化原语未接生产。
- [x] 在同一 `BackgroundRuntimeRecord` 保存 generation、phase-name 和更新时间，不新增平行 Store；
- [x] 路由与 `STARTING` 检查点可在同一次 Registry 保存中写入，写入时 PID/boot/start ticks 仍为空；
- [x] 旧 JSON 表示无 lease，部分字段、未知 phase、时间/代次倒退和 Host/PRoot 路由冲突失败关闭；
- [x] 内置定义与资源定义刷新保留检查点，后台 Host 在 RF930 前无 begin/transition 调用。

#### RF930 启动、恢复与停止桥

- 状态：已完成，后台通用 PRoot PROCESS 已接入与短任务相同的 actual controller。
- [x] 实际准入与 `proot_shell + STARTING` 检查点发生在唯一 `ProcessBuilder.start()` 之前；
- [x] 创建成功后附着 boot/PID/start ticks 再把 lease 转为 RUNNING；启动失败释放，快速退出或外死进入 ORPHAN_REVIEW；
- [x] 重复 start 复用同 owner/generation，不形成第二进程或第二容量；重启恢复既有 holder，缩档只阻断新准入；
- [x] 停止先写 STOPPING，只有 owner 树 settled 且强身份终态成立才写 RELEASED 并释放 actual lease。

#### RF940 actual 健康与迁移门

- 状态：已完成，正式健康面直接读取同一 admission 的同锁快照。
- [x] 独立 `proot_long_actual_*` 输出长期 owner 活动、排队、恢复累计和合同阻断计数；
- [x] `proot_unified_actual_*` 输出短任务、长期 owner、短长总量、状态与剩余容量；
- [x] 旧 `proot_actual_active_jobs/queued_jobs` 保持短任务语义，planned 字段继续保留原名和未生效声明；
- [x] 字段固定低基数，不含 owner、lease、PID、命令、路径、资源、Agent 或 session 身份，读取不创建 pool/进程。

#### RF950 真机故障矩阵与生产开关

- 状态：已完成，后台通用 PRoot PROCESS 类别生产门已打开。
- [x] OnePlus 8T 固定矩阵覆盖 1/2/4 容量边界、短长任务竞争、压力收缩和 PID/boot 反例；
- [x] 应用重启、同 UID 外死、重复启动、孤儿显式停止和 owner 树停止均保留唯一 generation/进程与容量；
- [x] 修复终态记录仍持有未释放 lease 时被停止入口提前跳过的问题；
- [x] 生产开关只识别后台通用 PRoot PROCESS 类别，不识别资源、命令、应用或 runtime id，并进入低基数健康面。

### RF1000 [P1 调度扩展] 长期 owner 的短任务保底余量

父任务方向：解决长期 owner 占满 actual 1/2/4 后，高优先级短任务仍永久等待的问题；不增加总容量、不抢占进程、不迁移终端或 Agent。

状态：已完成。actual 仲裁、v2 健康、真机矩阵和全量父任务门全部通过。

#### RF1010 饥饿反例与余量合同

- 状态：已完成，架构合同见 [PRoot 长期 owner 与短任务余量合同](../../architecture/proot-short-task-headroom.md)。
- [x] 区分队列优先级与不可抢占 holder 占满总量；
- [x] 固定低功耗 1→长期 1、均衡 2→长期 1、高性能 4→长期 3；
- [x] 恢复和压力缩档只阻断新准入，不驱逐既有 holder；
- [x] 规则只识别 MANAGED_OWNER 生命周期类别，不识别业务身份。

#### RF1020 actual 仲裁与公平队列

- 状态：已完成。
- [x] 在同一 `ProotJobAdmissionController` 内限制新 managed owner；
- [x] managed owner 达上限时跳过该等待项，让可运行短任务继续推进；
- [x] 保留共享写队首屏障、全局上限、lane 上限、压力和关闭语义；
- [x] 用并发反例证明均衡/高性能余量和低功耗物理边界。

#### RF1030 actual 健康与固定矩阵

- 状态：已完成。
- [x] 输出长期上限、长期占用和余量保护的固定低基数字段；
- [x] Debug 固定矩阵覆盖 1/2/4、后到短任务、恢复超额与压力收缩；
- [x] OnePlus 8T 验证不增加业务进程、不产生 FATAL/ANR。

#### RF1040 父任务门

- 状态：已完成。
- [x] 强制全量单测与 Debug 构建；
- [x] 复核 Node/Python 无性能矩阵重跑，终端/Agent 无净迁移；
- [x] 独立提交并给出下一类别是否值得迁移的 go/no-go。

### RF1100 [P1 启动扩展] PRoot 进程启动窗口协调

父任务方向：只协调多个 PRoot 业务进程从排队到“已有真实就绪证据”的短暂启动窗口；就绪后立即释放，不按终端或 Agent 会话存活期占用容量。是否生产接入完全由 OnePlus 8T 并发冷启动矩阵决定。

状态：已完成，生产 no-go。Debug 矩阵保留，正式入口不接启动窗口。

#### RF1110 真实入口与就绪边界审计

- 状态：已完成，合同见 [PRoot 进程启动窗口协调](../../architecture/proot-launch-window-coordination.md)。
- [x] 枚举终端、Agent、资源/exec、后台、bootstrap 与内部维护的实际进程创建点；
- [x] 区分 `ProcessBuilder.start()` 返回、首字节、协议握手、PTY 激活和业务健康，不能统一伪造 READY；
- [x] 固定启动 lease、超时释放、取消、优先级和唯一进程合同；
- [x] 形成入口适配矩阵，不修改生产入口。

#### RF1120 Debug 固定并发启动矩阵

- 状态：已完成。
- [x] 使用固定结构化命令，对比不协调、只包围 `start()`、包围到首个 READY 三种模式；
- [x] 覆盖 1/2/4/8 同时请求及窗口 1/2/4，输出 batch wall、ready P50/P95、失败率与残留；
- [x] 矩阵不接收外部命令、路径、并发或业务身份，构建物不进入 Git。

#### RF1130 go/no-go 与生产合同

- 状态：已完成，结论 no-go。
- [x] 相同成功率下没有得到跨两套矩阵稳定的 ready P95 或失败率改善；
- [x] READY 收窄稳定增加队列和 tail，start-return 只出现不稳定调度差异；
- [x] 不新增窗口宽度、正式健康字段或生产状态。

#### RF1140 条件生产接线与父任务门

- 状态：未触发；RF1130 no-go，因此不实施生产接线。
- [x] 没有修改 `ContainerLaunchConfig`/`ContainerExecConfig`、终端、Agent、后台或 Bridge 生产入口；
- [x] 没有新增生产队列、lease、健康字段或调度状态；
- [x] RF1100 以 Debug 证据与 no-go 结论关闭。

### RF1200 [P1 快速通道扩展] Git 通用依赖可行性

父任务方向：Git 是正式资源中覆盖面仅次于 Node 的通用依赖，且本地仓库操作对小文件和 stat/open 敏感。先验证 rootfs Git 经通用 glibc Host 资产直接运行是否正确且有稳定收益；不为任何上层 Agent 或资源写特判。

#### RF1210 候选排序与安全边界

- 状态：已完成，见 [Host Git 快速通道](../../architecture/host-git-fast-path.md)。
- [x] 按正式资源关系统计依赖覆盖面，不按印象选候选；
- [x] Git 10、curl 4、uv 1，Node/Python 已完成且不重复；
- [x] 固定本地 builtin、hooks/helpers、remote、pager、filter、submodule 的分层边界；
- [x] 不修改 Git 资源卡、命令 shim 或生产 Planner。

#### RF1220 Host Git 兼容与性能矩阵

- 状态：已完成。
- [x] 使用 `GlibcHostRuntimePreparer` 的通用资产，精确解析受管 Git 身份和动态库；
- [x] 覆盖 init/status/add/commit/log/diff/rev-parse 与 1/4/8 并发；
- [x] hooks、external diff/filter、remote helper 与 submodule 形成固定 PRoot 对照；
- [x] 不只比较 exit code，同时核验 HEAD、index 内容与 marker，捕获 filter 静默语义损坏；
- [x] OnePlus 8T 连续两套矩阵均完成，Host 本地 builtin 有显著并发收益，任意子进程语义不兼容。

#### RF1230 go/no-go 与条件 Provider

- 状态：已完成，direct Host Git 生产 no-go。
- [x] argv 不能证明 local config、attributes、hooks、helper 或 submodule 不触发子进程；
- [x] 运行后失败回退会面对已修改 index/工作树，不能提供唯一执行；
- [x] subcommand 白名单和仓库预扫描均不能形成稳定通用合同；
- [x] 未实现 `HostGitRuntimeProvider`，未修改 Git shim、资源卡或统一 Planner；
- [x] 后续候选只允许研究入口无关的通用 child relay，不把 Git 特判塞进执行链。

#### RF1240 父任务门

- 状态：已完成，RF1200 以 no-go 收口。
- [x] Debug 证据保留，生产入口、资源、shim、Planner 和状态均无净改动；
- [x] 1464 项全量单测、强制 Debug 构建通过；
- [x] 两套 OnePlus 8T 矩阵完成，无匹配 FATAL/ANR；
- [x] RF1200 不进入生产，Debug 基准只作为前提变化后的可复算证据。

### RF1300 [P0 快速通道底座] 通用 glibc child relay 可行性

父任务方向：研究 Host glibc 父进程在调用 `execve/execvp/posix_spawn` 等外部 child 时，能否不解释 Git/Python/应用语义，统一把 child 原样交给既有 PRoot 兼容执行前缀。若成立，它可以补齐多个通用依赖的子进程缺口；若 fd、信号、路径或唯一执行无法保持，则实验 no-go。

#### RF1310 child relay 入口与语义审计

- 状态：已完成，见 [通用 glibc Child Relay](../../architecture/glibc-child-relay.md)；
- [x] 复读通用 glibc launcher/compat、Node JS child bridge 与 PRoot argv/env 构造；
- [x] 枚举 exec/spawn、PATH、shebang、cwd/env、fd、信号、退出与递归边界；
- [x] 固定同步 ENOENT/EACCES 与 glibc hidden symbol 两个先验高风险点；
- [x] 不改生产 compat 资产、不发布正式配置、不接 Git/Python/资源入口。

#### RF1320 Debug-only 最小 relay 探针

- 状态：已完成；unrestricted relay no-go，direct exec/spawn 候选可进入复算。
- [x] 独立 C 源码与 ADB 部署资产不覆盖正式 `libkite-glibc-compat.so`；
- [x] 对照 direct Host、Host+relay、独立 PRoot；
- [x] 覆盖 argv/env/cwd、stdio、exit/signal、PATH/shebang、file actions 与 1/4/8 并发；
- [x] 证明 system/popen/fexecve 漏拦和同步错误变化，不把局部成功冒充全兼容；
- [x] 本地产物只进入 `local-artifacts` 和设备私有调试目录，不进入 APK/Git。

#### RF1330 Git/Python 通用反例复算

- 状态：已完成；窄合同对显式 direct exec/spawn 调用方可行，但不能按 Git/Python 整体放行；
- [x] 同一 relay 复算 RF1220 hook/filter/helper/submodule 和 RF250 Python subprocess；
- [x] Git 外部 child 全部经 `execve` 命中；状态、marker、index 与独立 PRoot 一致；
- [x] Python subprocess、`os.execve`、venv child 通过，`os.system` 因未命中 relay 保持失败关闭；
- [x] 不按工具名、资源 ID、subcommand 或脚本内容选择，只有调用方显式声明窄语义合同才可进入下一门。

#### RF1340 go/no-go 与父任务门

- 状态：已完成，当前 preload relay 生产 no-go；
- [x] 兼容、唯一执行和收益同时成立才讨论生产合同；
- [x] fork-child 安全与控制文件生命周期未关闭，正式 Provider/compat/资源/lane 保持不变；
- [x] 全量回归、强制构建、OnePlus 8T 固定矩阵和生产范围审查后独立提交。

### RF1400 [P0 PRoot 核心] 活跃运行时开销归因

父任务方向：不再通过上层应用启动时间猜测 PRoot。用 APK 已打包的活跃 `proot-kf-lifecycle-arm64` 与库存 `proot-arm64` 在同一 rootfs、workspace、argv、环境和设备上做固定 A/B，分离基础 wrapper、生命周期遥测和通用小文件/子进程负载的成本。历史 quarantined baseline 只作身份材料，不作为成功对照。

#### RF1410 活跃/库存 PRoot 对照合同

- 状态：已完成，见 [PRoot 活跃运行时开销归因](../../architecture/proot-active-runtime-overhead.md)；
- [x] 审计 active/stock/historical 三个资产的来源、loader、身份和正式选择链；
- [x] 固定启动、shell、元数据遍历、文件读写和子进程负载，不重复 Node/Python 已冻结矩阵；
- [x] stock 只允许复制到 Debug 私有目录，绝不改变正式 `activeRuntimeId` 或安装态 `bin/proot`；
- [x] 固定相对/绝对双阈值，避免对微秒级噪声做生产补丁。

#### RF1420 Debug-only 固定 A/B 矩阵

- 状态：已完成；两套独享 sink 矩阵均为 45 组零失败、零残留；
- [x] 固定 1/4/8 并发、交替顺序、三轮、超时和结果校验；
- [x] active 正式遥测、同 active 无遥测与 stock external-loader 使用同一正式计划；
- [x] 记录 wall samples、P50/P95、失败、残留、sink bytes/rotation 与 ANR/FATAL；
- [x] ADB 入口不接受命令、路径、并发、轮数或 runtime 参数。

#### RF1430 热点归因与候选补丁边界

- [x] active 相对 stock 的高并发 small-write 差异已定位到正式补丁之前的共同源码/构建代次边界；
- [x] 未为某个应用特判，未关闭强身份、停止确认或文件保护；
- [x] 三个 lifecycle 候选均未达到收益门，正式资产保持不变。

##### RF1431 固定热点拆分矩阵

- [x] small-write 分别关闭 `kf_procfs`、`mountinfo`、两者，并对照 embedded/external loader；
- [x] child-fanout 分离共享日志、无 registry、每进程日志和无 telemetry；
- [x] OnePlus 8T 九轮矩阵确认默认扩展和 loader 不是 small-write 主因；
- [x] lifecycle 增量属于每事件同步处理，不属于共享文件或 registry 竞争。

##### RF1432 可复现源码与生命周期候选

- [x] 从 descriptor 的 `d30b988` 基线和六个正式 patch 在忽略目录重建，完整产物与 v23 正式资产逐字节同 SHA；
- [x] 增加可复现构建脚本和固定补丁消融入口，不修改脏 KFShell 工作树；
- [x] 单次编码/写入、持久 fd、registry 活跃计数三个候选均只部署到 Debug 私有路径并判定 no-go。

##### RF1433 候选正确性与收益门

- [x] lifecycle 候选 110 个 session 的事件 schema、事件数、父子身份和退出事实签名一致；
- [x] 九轮消融矩阵覆盖六层正式 patch、编译时 unbundled loader 与 NDK 28；
- [x] 各候选均未形成稳定收益，按门槛 no-go，不更新正式资产。

#### RF1440 go/no-go 与父任务门

- [x] 正式 PRoot 保持 v23：Kite 六层补丁、embedded loader 与 NDK 26/28 均不是已观测高并发差异的可行动根因；
- [x] 完成全量回归、强制构建、OnePlus 8T 固定矩阵和生产范围审查后独立提交。

### RF1500 [P1 通用能力] 高频结构化只读验证

父任务目标：不重做 Node/Python，不解析任意 shell，也不为资源或应用特判；先固化所有明确不支持和未放行边界，再审计多个正式调用方都会触发的只读 PRoot 小任务。首个候选是受管命令存在性校验：只有默认环境、无 View、固定 PATH、外部文件身份和完整运行时身份都可由 Android 物理文件事实精确表达时，才允许跳过 PRoot `command -v`；任何未知事实继续使用现有 PRoot 探针。

#### RF1510 兼容性债务总账

- 问题证据：Node、Python、Git、child relay、原生 ZIP 和 PRoot 调度的 no-go 分散在多份性能矩阵和 ADR，后续容易把“尚未放行”误解为“已经支持”或重复排查；
- 解法：建立稳定编号的兼容性债务总账，分别记录当前事实、失败原因、现行兼容路线和未来候选方案；本叶只写文档，不改变 Provider、资源、二进制或路由；
- 验收标准：覆盖快速通道、原生能力和 PRoot 三车道；明确区分“不支持”“当前不生产化”“可回退 PRoot”；每项都有原证据回指；独立提交；
- 依赖：RF1440 已完成。

#### RF1520 正式调用面与收益门审计

- 问题证据：资源打开和获取计划会用一次 PRoot `command -v` 核对登记命令，现有 Android 侧已经能生成运行时身份、PATH 文件身份和有界正向缓存，但首次动作仍创建 PRoot；
- 解法：审计所有调用方、PATH/符号链接/可执行位/View/环境边界和缓存失效事实，建立固定 Host/PRoot 对照与错误矩阵；不先修改生产探针；
- 验收标准：至少两个正式动作共享同一能力；固定请求不接受 ADB 自定义命令或路径；正确性覆盖存在、缺失、断链、非执行文件、身份变化和未知环境；性能门预先固定；
- 依赖：RF1510。
- [x] 打开与获取两个正式动作共享同一核对能力，安装脚本内任意 shell 校验不在范围；
- [x] 固定存在、缺失、断链和无执行位四类事实；首轮 p50 从 106ms 降至 8.966ms，但发现无执行位假阳性；
- [x] RF1530 的三轮正确性与性能确认门已在生产改动前固定。

#### RF1530 原生受管命令证明候选

- 问题证据：`managedCommandVerificationBasis` 当前只用于复用“曾经 PRoot 成功”的正向证据，没有直接成为 Provider 结果；
- 解法：只在完整肯定式文件证明存在时返回原生 Ready，未知或不完整时使用现有 PRoot 探针；不得把页面、资源 ID 或命令白名单写入判断；
- 验收标准：结果与 PRoot `command -v` 在共同合同内一致；不创建第二状态源；首次资源动作只执行一次证明；任一未知事实失败关闭到 PRoot；
- 依赖：RF1520 达到通用性和收益门。
- [x] 文件身份加入可执行事实，缺失、断链、无执行位和非默认环境都不能形成原生证明；
- [x] 完整 `NativeProof` 复用既有协调器，混合请求只把不完整部分交给 PRoot；证明错配失败关闭；
- [x] 首次 p95 超门后不放宽阈值；减少重复 stat 后重新三轮，正确性和固定性能门全部通过。

#### RF1540 go/no-go 与父任务门

- 有稳定收益、共同语义一致且正式链无额外状态时才接入；否则保留既有 PRoot 探针；
- 完成目标回归、Debug 构建、OnePlus 8T 资源打开/获取链和生产范围审查后独立提交；
- 本父任务不迁移安装脚本内部的任意 `command -v`，不修改资源清单，不扩大到版本执行、shell 函数、别名或任意 PATH。
- [x] OnePlus 8T 真实 OpenClaw“打开”命中 `native=1/fallback=0` 并进入 Agent 显示面；
- [x] “获取”与“打开”继续复用同一核对函数，未为验收改变未安装资源状态；
- [x] Full 279 suites、1473 tests 零失败，Debug 构建与生产范围审查通过；
- [x] RF1500 以生产 go 收口，未知事实仍失败关闭到 PRoot。

### RF1550 [P0 工程门] 测试分层与本机构建协调

父任务目标：保留完整回归资产，但不再让每个叶子任务都执行 1400+ 全量测试；建立日常快测、阶段回归、发布全量三层入口，并让同机多个 Kite worktree 的 Gradle 重任务经过同一协调锁。各 worktree 的源码、`.gradle`、`build/` 和测试报告继续物理隔离；设备仍按明确 serial 分工。

#### RF1551 测试资产与并发碰撞审计

- 问题证据：当前 `app/src/test` 有 276 个测试类、约 1.85 MiB 源码；RF1440 全量为 1465 tests，强制执行约 343 秒。历史每个阶段都追加合同测试并重复运行全量；同时多个 worktree 共用用户级 Gradle/Kotlin daemon 与依赖缓存，已有 classes.jar 锁等待和并行进程争用记录；
- 解法：统计测试类形态、稳定命名族、现有执行命令、worktree 构建目录与当前 Java/Gradle 进程，区分“覆盖累积”和“重复注册”；
- 验收标准：给出可复算的测试类/命名族数量；确认哪些目录已隔离、哪些 daemon/cache/设备需要协调；不删除测试；
- 依赖：RF1510 已完成。RF1520 草稿独立 stash，避免混入。

#### RF1552 三层测试入口

- 问题证据：当前只有目标 `--tests` 与全量 `testDebugUnitTest` 两个极端，长期任务缺少统一日常门；
- 解法：新增单一脚本入口：Quick 固定执行架构/合同/协议/路由/策略/Schema/Guard 命名族，Stage 在 Quick 基础上合并调用方给出的模块测试模式，Full 执行全部测试；默认不强制 `--rerun-tasks`；
- 验收标准：Quick/Stage/Full 参数失败关闭；Stage 未提供测试范围时拒绝；每次输出 suite/test/failure/error/skipped/耗时摘要；Quick 测试类不超过全量 25%；Full 保持原 `testDebugUnitTest` 语义；
- 依赖：RF1551。

#### RF1553 跨 worktree Gradle 构建协调

- 问题证据：worktree 自身 `.gradle` 和 `build/` 已隔离，但同机进程仍共享 `C:\Users\19437\.gradle`、Gradle daemon、Kotlin daemon、CPU 和内存；`--no-daemon` 仍会创建单次构建 daemon；
- 解法：新增通用本地 Gradle 包装器，以独立工作进程持有 Windows 命名互斥锁并串行化 Kite 重任务，自动附加 `--no-daemon --console=plain`；外层调用器中断不能让实际 Gradle 逃逸到锁外，不复制依赖缓存，不杀其他 worktree 的 daemon；
- 验收标准：同一时刻只允许一个工作进程进入 Gradle；等待、超时和遗弃锁均明确报告；主动终止外层协调器后，工作进程仍持锁到任务结束；任意 Gradle 参数原样透传；CI 保持原入口；工具链文档统一要求本地长期任务使用包装器；
- 依赖：RF1551。

#### RF1554 压缩与隔离父任务门

- [x] Quick、Stage 和 Full 入口分别实跑；Quick 数量与耗时相对 Full 形成明确比例；
- [x] 启动两个只读锁探针证明互斥，没有并行启动真实双 Gradle 重任务；
- [x] 故障注入终止外层协调器，第二工作进程仍等待第一工作进程完成后才获锁；
- [x] 脚本合同、全量测试与 Debug 构建通过；RF1520 从 stash 恢复后使用新测试入口。

### RF1600 [P1 通用能力] 受管包元数据版本探针

父任务目标：优化资源“检查更新”中默认 npm 已安装版本探针，不执行 `node -p require(...).version`，也不从任意版本命令反推能力。只有调用方显式提交受控容器路径、有限 JSON 大小和字段合同，且 Android 能在无进程、无副作用的准备阶段完整证明普通文件、路径边界和字符串字段时，才由原生 Provider 返回版本原文；自定义命令、符号链接、超限、格式未知或其他不完整事实在首个业务进程前整条保留现有 PRoot 探针。

#### RF1610 候选与发布门审计

- 问题证据：正式资源目录有 7 个 npm 来源，其中 5 个没有自定义 `versionProbe`，共同由 `KiteResourceSourcePlanFactory.defaultInstalledVersionProbe()` 生成绝对 `package.json` 路径，并由 `AndroidResourceVersionGateway` 为每个资源启动一次静默 PRoot + Node；批量检查更新按资源顺序重复该固定成本；
- 候选排序：唯一进入候选的是“受管 JSON 元数据字符串字段读取”。4 个显式 `命令 --version` 探针因可执行文件、Linux ELF、子进程和环境闭包未知被排除；取消安装清理因含活动安装根、软件停止、包管理和资源分支被排除；
- 解法：先固定入口无关的元数据请求、正确性反例、性能阈值和真实链门，不修改生产 Gateway、清单、Provider 或 Store；
- 验收标准：至少两个正式资源复用同一结构化合同；请求不含应用名、命令名白名单或路由资源 ID；所有门在基线前固定；独立文档提交；
- 依赖：RF1500、RF1550 已完成，冻结锚点工作树干净。
- [x] 5 个正式资源共享默认包元数据版本探针；
- [x] Android 已有 `/workspace` 到物理工作区的受控映射和只读文件事实；
- [x] 自定义命令和事务清理未进入候选；
- [x] RF1620～RF1640 发布门已在 Debug/生产改动前固定。

#### RF1620 固定 Debug/测试矩阵与基线

- 固定入口不接受 ADB 传入资源 ID、包名、路径、字段、轮数或阈值；夹具由 Debug 代码在受控目录内部建立并在退出后核对清理；
- 正向合同覆盖普通包、作用域包和预发布/构建版本；反例覆盖缺失文件、路径逃逸、符号链接、超限、坏 JSON、缺字段、非字符串字段及旧自定义命令；
- 正确性门：三轮矩阵最终结果、版本原文和预期车道零差异；不安全请求必须 `Blocked` 且不创建 PRoot，未知/不完整事实必须在首个进程前 `Unsupported` 并由既有 PRoot 路径完成；
- 性能门：OnePlus 8T 三轮中，原生正向样本 p50 每轮相对现有 PRoot + Node 至少降低 70%，原生 p95 每轮不高于 30ms；固定 5 请求批次 p50 至少降低 60%；
- 若任一正确性或收益门失败，RF1600 立即 no-go，只保留可复算 Debug 证据，不写生产样板。
- [x] 三轮 13 类固定矩阵均为零差异，路径逃逸 `Blocked/fallback=0`，未知或不完整事实保持 PRoot；
- [x] 单请求三轮 p50 降低 99.2%～99.5%，原生 p95 为 6.088～6.775ms；
- [x] 固定 5 请求批次三轮 p50 降低 99.5%～99.7%；
- [x] Stage、Debug 构建、OnePlus 8T、夹具清理和 FATAL/ANR 检查通过，允许进入 RF1630。

#### RF1630 最小生产样板

- 只有 RF1620 全部门通过才开始；Provider 只消费结构化容器路径、最大字节数和 JSON 字段，不读取资源 ID、来源包名、页面或任意 shell；
- Ready 只返回普通受控文件中的字符串字段原文；路径逃逸等无效请求 `Blocked`，符号链接、超限、格式/字段未知等不完整事实 `Unsupported`；选择在 PRoot/Node 创建前完成；
- 默认 npm 来源可生成结构化元数据探针；显式 `versionProbe`、非 npm 来源和旧协议继续现有 PRoot 命令；Host/原生开始后不自动重放 PRoot；
- 不新增 Store；版本事实仍由 `ResourceVersionCoordinator` 产出，安装状态仍由原 Store 持有，页面不选择 lane、不扫描文件、不整页刷新；
- 叶子验证使用 Targeted、Quick、Stage，不运行 Full。
- [x] `AndroidNativeStructuredJsonStringProvider` 只消费授权根、容器路径、最大字节和顶层字符串字段，不读取资源、包、命令或页面标识；
- [x] 默认 npm 来源在保留原命令回退的同时生成结构化元数据合同，5 份正式资源复用；显式探针保持无结构化事实；
- [x] Gateway 单测固定 Ready 零 PRoot、Unsupported 单次 PRoot、Blocked 零 PRoot、旧命令单次 PRoot；选择不解析 `probe.command`；
- [x] 最终 Stage 为 62 suites、315 tests、零失败；OnePlus 固定矩阵 13 类零差异，生产 Provider 单请求 p50 降低 98.5%、批次降低 99.3%，允许进入 RF1640。

#### RF1640 go/no-go 与父任务门

- [ ] 至少两份正式资源清单生成同一结构化元数据合同，且没有资源/包/命令白名单；
- [ ] OnePlus 8T 至少一个真实已安装资源检查更新命中 `native=1/fallback=0`，旧自定义探针证明 `native=0/fallback=1` 且回退发生在首个进程前；
- [ ] 固定反例、三轮性能、Targeted、Quick、Stage、Debug 构建、ANR/FATAL 和生产范围审查全部通过；
- [ ] RF1600 父门执行一次 Full，零失败后才可判定生产 go；否则回退生产样板并以 no-go 收口；
- [ ] 不触碰 Node/Python/RF1500 历史矩阵、远端版本请求、安装事务、取消清理、PRoot View、魅族设备、main/其他工作树、远端、版本或发布。

## 每个叶子任务的固定闭环

1. 复读任务目标、验收和依赖；
2. 只修改所属 Provider 或共同合同；
3. 运行目标单测与静态护栏；
4. 运行必要的基准或真机链路；
5. 回写 `PROGRESS.md` 与必要 ADR；
6. 独立 Git 提交，不混入用户或其他支线文件；
7. 通过父任务门后才进入下一层。
