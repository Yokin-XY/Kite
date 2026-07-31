# Kite 混合运行底座任务单

## 当前恢复指针

- 根任务：`RF000`
- 当前阶段：`RF700` 设备自适应校准预研
- 当前任务：`RF710` 既有校准合同与实际 1/2/4 策略对齐审计
- 基线：`main@8223ba02d2a75b5df86e3fb15914c6a30e8b3da2`
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

#### RF710 既有校准合同与实际策略对齐审计

- 问题证据：仓库已有 observe-only 的 `RuntimeProotDeviceCalibrationDryRun` 与 overlay，但 RF400 的正式 admission/pool 已统一到 `ProotPerformanceTunings` 1/2/4；不能另写第二套校准器或把历史 tracee 上限直接当并发上限。
- 验收标准：列清 overlay/health/automation 的真实读写路径；证明哪些字段只描述 tracee 模型、哪些能映射 1/2/4；建立失败关闭的候选输入合同，不改变生产档位。
- 依赖：RF650。

#### RF720 可信信号归一与升降级门

- 解法：只消费实际 coordinator 失败率/时延、可信内存压力和前后台；无热信号时最多维持或降级，禁止自动升档。
- 验收标准：未知/陈旧/矛盾信号失败关闭；单次好样本不升档；高压或失败率立即产生降级建议但不强杀运行任务。
- 依赖：RF710。

#### RF730 候选档、迟滞与回滚模拟器

- 解法：候选只在 1/2/4 中移动一级；连续窗口确认、冷却期、回滚阈值与失败预算统一为纯状态机。
- 验收标准：抖动不反复切档，坏样本可回滚，应用重启恢复不跳级；不写正式策略文件。
- 依赖：RF720。

#### RF740 规划建议与 actual 边界

- 解法：输出独立 planned 建议与证据计数；实际档位仍只来自 RF510 coordinator，应用动作另立生产门。
- 验收标准：读取无副作用、低基数、无设备/任务身份；planned 不冒充 actual。
- 依赖：RF730。

#### RF750 RF700 父任务门

- [ ] 对齐、信号、迟滞、回滚和观测合同联合回归通过；
- [ ] 给出自动降级与自动升档分别的生产 go/no-go；
- [ ] 没有可信 thermal 信号时自动升档保持 no-go。

## 每个叶子任务的固定闭环

1. 复读任务目标、验收和依赖；
2. 只修改所属 Provider 或共同合同；
3. 运行目标单测与静态护栏；
4. 运行必要的基准或真机链路；
5. 回写 `PROGRESS.md` 与必要 ADR；
6. 独立 Git 提交，不混入用户或其他支线文件；
7. 通过父任务门后才进入下一层。
