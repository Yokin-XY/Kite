# Kite 主线稳定化决策记录

## ADR-S001 会话分支，代码单主线

状态：accepted

决策：不同 Codex 会话按阶段分工，但代码不建立并行 Git 开发分支。每个阶段在本地
`main` 上形成一个或多个边界清晰的提交，下一阶段从最新提交继续。

理由：当前多个阶段都会触及 `MainActivity`、状态 Store 和显示面。并行代码分支会引入
过期假设和大量冲突；会话分支已经足以保留不同任务视角。

影响：阶段执行者可以直接修改代码，但必须按独立成果提交，并回写交接状态。

## ADR-S002 母板是方向事实源，测试是行为事实源

状态：accepted

决策：`PLAYBOOK.md` 固定方向、边界和验收；具体行为是否成立由测试、构建、静态检查和
真机证据证明。不得用文档声明替代运行证据。

理由：旧重构记录出现过“任务标记完成，但职责仍委托回 MainActivity”的情况，也出现了
静态脚本因源码写法变化而误报的情况。

影响：后续验收关注状态所有权、动作入口和页面行为，不以类名、文件数或精确源码排版为准。

## ADR-S003 第一阶段不改变产品行为

状态：accepted

决策：S1 只修验证基础并补回归测试，不迁移页面、不改导航结果、不改资源安装和浏览器协议。

理由：必须先建立可靠安全网，再进入跨页面行为调整。

## ADR-S004 静态检查验证语义，不锁死源码排版

状态：accepted

决策：静态检查可以确认目标成员函数存在、调用了指定入口或包含关键状态字段，但不得要求
完整调用文本、固定参数数量或固定命名参数排版。可通过单元测试覆盖的状态行为优先写测试。

理由：`stopRecipe` 增加 `navigateToConsole` 参数、资源 Store 改用命名参数后，旧检查产生了
11 项误报，而真实委托和信号均存在。

影响：后续源码结构调整不会因为无关排版变化破坏验证；真正丢失委托或信号仍会失败。

## ADR-S005 会话按五个大方向分工

状态：superseded by ADR-S007

决策：五个会话方向分别是导航与返回、动作编排、状态投影、生命周期和资源预算、功能
模块与扩展点。本会话只领取 D1 导航与返回规则统一。

理由：五个大方向是职责所有权划分；六条建议执行顺序是母任务的全局排期，两者不能混成
同一套任务编号。

影响：`108910a` 之前的成果改记为五个方向共享的 P0 安全网。本会话不再承担动作、状态、
生命周期或最终模块化任务，只修改导航合同及其必要接入点。

## ADR-S006 页面提交返回请求，导航合同决定目标

状态：accepted

决策：页面顶部返回、系统 back 和显示面内部返回统一提交到 `OnBackPressedDispatcher`。
`ScreenRouter` 拥有 Destination、父级、上下文返回和恢复策略；Web 历史和终端详情作为更具体的
显示面消费者优先处理，CardRun 保留原有任务返回合同。

理由：同一页面的顶部按钮与系统 back 曾分别写死目标，页面恢复又维护另一份映射，导致返回行为
随入口分叉。统一请求入口后，页面不再决定“应该回哪里”，但现有渲染和运行状态所有权保持不变。

影响：后续迁移动作、状态和模块时必须通过 `enterScreen` 登记目标，通过统一 dispatcher 请求返回；
不得直接写 `currentScreen`、恢复旧 `onBackPressed()` 分支，或用导航重建无关终端/Web/报告显示面。

## ADR-S007 单会话按五个方向连续推进

状态：accepted

决策：取消五个会话分别领取方向的安排。当前会话在本地 `main` 上按 D1、D2、D3、D4、D5 顺序连续推进；每个独立成果仍单独提交和验证，完成一个方向后自动进入下一个方向。

理由：会话分类本身增加了调度和交接成本，用户更需要同一执行者保持完整主线并持续推进。

影响：`PLAYBOOK.md` 和 `PROGRESS.md` 是跨上下文恢复入口；不创建 Git 开发分支，不等待其他会话领取，不把五个方向压成一个不可回退的大提交。

## ADR-S008 页面提交意图，协调器生成计划

状态：accepted

决策：卡片、资源和安装向导页面只提交稳定动作意图；纯协调器根据共享状态生成轻量计划，`MainActivity` 的统一入口再委托现有 Store、编排器和执行核心。停止或打开具体运行时必须携带目标 `instanceId`。

理由：首页、编辑页、资源详情和运行管理曾分别拼接启动、打开、停止、卸载和重试流程，容易重复创建实例或让同一动作表现不同。

影响：后续 D3-D5 不得把执行流程重新放回按钮回调；协调器不写运行事实、不阻塞重活，`CardRunStore` 和 `KiteResourceInstallStore` 所有权不变。

## ADR-S009 内存回调只触发策略刷新

状态：accepted

决策：Android `onTrimMemory` 和 `onLowMemory` 只记录生命周期事实并请求现有运行快照刷新，不新增直接杀进程路径。同级或更低压力允许在冷却窗口内合并，但升级压力必须立即进入刷新链。

理由：系统可能在很短时间内连续提升内存压力。固定冷却会吞掉更严重事件，而 `onLowMemory` 复用普通 trim 入口还会覆盖真实低内存信号。真正是否回收必须继续由租约、归属、前台状态和用户锁定规则共同决定。

影响：后续显示资源释放可以响应内存压力，但任务或进程回收不得绕过 `RuntimeMemoryLifecycleRuleTrigger`、`RuntimeLifecycleStrategyActivator` 和 `RuntimeReclaimer` 的既有合同。

## ADR-S010 扩展点归模块，失联页面直接退役

状态：accepted

决策：终端快捷动作由 `TerminalPanelActionRegistry` 管理，终端 Fragment 只提供能力并渲染注册快照。没有路由、Manifest 或调用方的旧 `TaskManagerFragment` 直接删除，不再与当前运行管理双线维护。

理由：模块化以职责所有权和真实入口为准，不以文件数量为准。继续保留失联页面只会让状态、动作和静态检查同时维护两套行为。

影响：新增终端快捷动作通过注册表扩展；进程管理统一消费 `TaskManagerStore`，不得恢复平行页面或独立进程事实。

## ADR-S011 离线工具链包按版本全局共享

状态：accepted

决策：资源安装所需的 `ai-dev-pack` 在工作区只保留一份共享缓存。资源私有缓存继续使用独立路径，资源卸载不得由共享包路径反推并删除公共内容。内部包和工作区共享包都以 manifest 完整性校验决定复用或更新，更新时通过 pending 目录校验后原子替换。

理由：所有 bundled 资源使用同一份约 89MB 离线工具链，但旧实现为每个资源复制一份，使 OnePlus 应用数据额外增长约 1GB。共享缓存不改变官方安装脚本和资源依赖语义，只消除重复载荷。

影响：旧资源私有副本在下一次 bundled 资源操作时迁移清理；manifest 版本、安装脚本、声明文件缺失或出现未声明 package 文件时必须重建，完整包则直接复用。

## ADR-S012 采用模块化单体与单向状态流

状态：accepted

决策：Kite 第二阶段采用模块化单体。应用壳负责 Android 生命周期、导航、系统 Intent 和依赖装配；首页、资源、运行、终端、Web、设置分别拥有 Screen、UiState、Action 和 Controller；现有 Store 继续拥有事实；PRoot、bridge、browser、file、toolchain 作为平台适配层。一次性导航和系统窗口通过 Effect 上交应用壳。

理由：Kite 的核心业务不是普通信息页面，而是“卡片或资源动作 -> 运行实例 -> terminal/report/web 显示面 -> 后台生命周期”。按页面机械拆文件不能解决状态和执行耦合；一次切换 Compose、Hilt、Navigation Component 或多 Gradle 模块又会同时改变太多变量。先在单 APK 内落实职责和依赖方向，风险更可控。

影响：后续迁移以职责所有权、状态来源和依赖方向为验收核心，文件大小只作为健康指标。Feature 不得直接操作其他 Feature 页面，Platform 不得引用 Activity/View，ViewModel/Controller 不得复制安装和运行事实。只有包级边界稳定、循环依赖消失后，才评估 Gradle 模块化。

## ADR-S013 逻辑修复先于职责搬迁并独立提交

状态：accepted

决策：每个模块迁移前先建立目的验收矩阵。发现点击反馈、状态确认、失败恢复、返回目标或生命周期不符合业务目的时，先做独立行为修复提交；行为通过后再做职责迁移提交。

理由：把逻辑修复混进大规模移动会导致回归时无法判断是原行为错误、搬迁错误还是新架构错误，也容易为了让新结构通过而保留错误语义。

影响：每个 T 任务至少包含基线、行为修复、职责迁移、清理和验收边界；没有发现逻辑问题时可以省略修复提交，但不得省略目的矩阵和迁移前后对照。

## ADR-S014 资源页面区分结构内容与动态事实

状态：accepted

决策：资源页面的 manifest 描述、分节、媒体和推荐属于结构内容；安装、运行、失败、动作可用性和计划步骤属于动态事实。结构签名不变时只重绑受影响控件，动态事实不得触发页面级目录读取、媒体缓存线程或整页重建。

理由：资源详情旧实现同时维护 Activity 页面缓存、请求序号、媒体缓存和 View binding，安装信号到来后仍可能开线程重新读取目录；这既复制事实，又让普通状态变化拥有整页重建能力。Controller 与 Gateway 已提供同一事实投影，不需要页面再建第二套同步链。

影响：目录、搜索、详情、管理和安装向导应共享这一规则。页面保存的只能是查询、分类、滚动等显示状态；安装和运行事实继续由现有 Store 写入，并经 Gateway 信号驱动局部重绑。

## ADR-S015 资源详情返回保留来源上下文

状态：accepted

决策：`ResourceDetail` 使用上下文返回策略。由资源管理等子页面进入详情时，Shell 登记来源页面的恢复动作；没有来源上下文时仍回到资源目录这一安全父级。

理由：固定把详情父级写成资源目录，会让“资源管理 -> 详情 -> 返回”丢失用户正在处理的管理上下文。页面自身不应写死目标，但导航合同必须允许 Shell 保留真实来源。

影响：详情 Fragment 仍只提交 `Back`。来源恢复由 `AppNavigator` 与 Shell 负责；后续其他入口需要保留上下文时应登记返回动作，不得在详情页按钮里直接调用目标页面。

## ADR-S016 前台恢复必须从事实拥有者校准

状态：accepted

决策：Feature 的实时变化流负责前台期间的低延迟更新；每次页面生命周期重新进入 `STARTED` 时，Controller 必须从既有 Store/服务重新投影当前事实。非重放信号不得作为页面恢复正确性的唯一依据。

理由：资源首页进入 CardRun 后处于 `STOPPED`，安装完成信号可能在此期间发生且不会重放。仅依赖实时信号会让页面恢复后继续显示离开前的乐观“准备中”，即使 Store 已确认安装成功。

影响：返回前台先保留已有结构，再局部校准动态控件；不得通过整页重建、轮询或复制一份长期状态解决漏信号。该规则后续适用于首页、运行管理、终端、报告和 Web 显示面。

## ADR-S017 配方目录与分组事实必须进程唯一

状态：accepted

决策：`KiteRecipeLoader`、`KiteCardGroupStore` 和组合两者的 `RecipeFeatureGateway` 由 `KiteAppGraph` 作为进程级依赖创建。首页与配方编辑器只能通过 Application 合同消费，不得自行构造平行 Loader/Store。

理由：首页结构、编辑保存和卡片动作都依赖同一份配方及分组事实。页面各自构造 Loader/Store 会形成独立缓存与变化时序，使编辑完成后首页仍显示旧结构，或运行状态校准误触发目录重载。

影响：配置结构变化通过 Gateway 事件通知需要重载的 Feature；`CardRunStore` 变化只触发运行投影校准。页面不得为同步方便复制一份长期目录事实，Shell 也不得绕过 Gateway 创建配方存储对象。

## ADR-S018 首页结构与运行投影分离

状态：accepted

决策：首页的配方 ID、名称、图标、步骤和分组构成结构签名；只有这些结构事实变化时才重建分页或卡片网格。运行状态、按钮、徽章、步骤进度和计时属于动态投影，只能重绑已有卡片。

理由：卡片运行信号频繁，若与目录结构使用同一刷新路径，会让首页滚动、分页和点击反馈随着后台任务反复重建。反过来，配置保存或分组变化确实会改变卡片集合，必须允许受控结构更新。

影响：`HomeScreen` 持有结构签名与卡片 Binding，`HomeFeatureController` 复用 `KiteCardRunUiProjector`；Shell 不再观察 `CardRunStore` 后直接更新首页 View，也不得用 `showConsole()` 处理普通运行变化。

## ADR-S019 配方编辑只允许一份草稿事实

状态：accepted

决策：配方编辑过程中的名称、描述、图标、分组、启动选项、快捷方式请求和步骤顺序统一存入 `RecipeEditorDraft`。输入框和弹窗只提交 Action，不再各自长期保存平行字段；保存时由草稿一次性生成 `NewRecipeInput`。

理由：旧 Activity 同时维护输入框、十余个可变字段、`formSteps`、`recipeMoreDraft` 和持久化 JSON，返回更多配置或图片选择后容易由较旧的一份状态覆盖新输入。单一草稿可以让未保存判断、进程恢复和校验使用同一事实。

影响：编辑器 Controller 拥有草稿与校验，Gateway 拥有配置写入和不透明草稿字符串持久化；Shell 只处理页面导航、系统图片选择、桌面快捷方式和运行 Effect，不得读取输入框拼装保存请求。

## ADR-S020 配方目录变更先更新快照再发布可重放信号

状态：accepted

决策：`RecipeFeatureGateway` 保存或删除配方时，必须先更新进程内目录快照，再发布一条可重放的目录变更信号。首页等 Feature 重新进入前台后消费同一快照；外部投放区刷新会使快照失效并触发下一次真实目录校准。

理由：首页进入编辑器后处于 detached，保存信号可能发生在首页停止收集期间。无重放瞬时信号会导致配置已经写入共享目录，而首页仍投影旧集合。给页面增加延时刷新、轮询或本地复制目录都会再次形成平行事实。

影响：实时信号负责低延迟，缓存快照负责同一进程内恢复，冷启动仍从 Loader 加载真实目录。资源模板等仍需直接写共享目录的入口必须调用 Gateway 失效合同；状态拥有者必须在发信号前完成事实更新，页面不得用二次点击或整页定时刷新补偿漏信号。

## ADR-S021 运行编排以实例代次和结构化事件为边界

状态：accepted

决策：`RunOrchestrator` 只通过 `RunStateGateway` 读写运行事实，只通过 `RecipeExecutor` 分派所有步骤和停止请求。每次执行以 `instanceId + createdAt` 作为实例代次；同一代次同一步骤只允许一个在途执行，回调必须携带代次和步骤索引，迟到或旧代次事件一律丢弃。

理由：旧链同时依靠 Activity 字段、当前页面、`runId` 和步骤索引判断回调归属。页面重建、手工继续或停止后，旧回调仍可能把状态写回运行中；终端续跑还依赖 `pendingTerminalFlow` 页面字段，无法从 `CardRunStore` 独立恢复。

影响：`CardRunStore` 继续是运行事实唯一拥有者，执行适配器只返回 `Progress/Completed/AwaitingUser/Failed` 等结构化事件。页面不参与步骤推进；等待步骤的下一步由当前运行事实推导。Bridge、终端、Web、X11 和 Android 能力只能位于 Platform 适配器，Application 编排层不得引用 Android/View/Shell。

## ADR-S022 等待步骤完成必须同时闭合执行资源

状态：accepted

决策：用户对 terminal、Web 等等待步骤提交“继续/完成”时，编排器先校验当前实例代次和步骤，再要求执行适配器闭合该步骤占用的会话或进程，最后推进运行事实。即使它是最后一步，也不能只把 `CardRunState` 标记为完成而保留执行资源。

理由：旧链把“继续”理解为页面状态推进，最后一步没有下一步时不会结束终端会话，导致摘要显示完成而 `proot/bash` 仍存活。页面事实与真实资源生命周期因此分叉，后续启动、内存回收和进程管理都会得到错误答案。

影响：显示面离开仍不等于停止任务；但用户明确完成等待步骤时，执行资源必须同步闭合。后续 Web、X11 与 Android 等待能力也遵守同一规则，不能按页面或步骤类型增加单点特判。

## ADR-S023 停止确认以绑定身份和残留审计为准

状态：accepted

决策：停止请求必须先区分终端会话身份与 Bridge 进程身份。`runId == terminalSessionId` 且没有 PID、进程组或系统会话时，只结束终端会话，不调用 Bridge。进入 Bridge 的停止请求，最终以同一响应中的残留进程审计为准：存在 PID 则不得宣布停止；明确出现空 `__kite_stop_remaining:` 时，即使强杀命令本身返回非零，也确认停止并清除运行绑定。

理由：终端步骤历史上把会话 ID 同时写进 `runId`。把它误当 Bridge 运行 ID 会向错误目标发送 `stop-run`，甚至终止 Kite 宿主。另一方面，强杀成功常以信号退出产生非零结果，仅看 `ok/status` 会把已经清零的进程恢复成“运行中”。身份归一化与残留审计分别解决“杀错对象”和“结果解释错误”。

影响：`RecipeStopRequest` 提供唯一的 `bridgeRunId/hasBridgeProcessBinding` 规则，Application 与 Platform 不得各自猜测。`StopCoordinator` 是停止结果唯一解释器；页面、Bridge 回调和资源卡不得绕过它直接写 `Stopped`，也不得因退出码非零而忽略明确的残留证据。

## ADR-S024 资源结算属于进程级运行生命周期

状态：accepted

决策：资源安装和卸载继续使用 `KiteResourceInstallStore` 作为资源事实拥有者，但何时登记成功、失败、取消以及何时推进依赖计划，由进程级 `ResourceRunCoordinator` 在收到已提交的 CardRun 生命周期事件后决定。页面只提交资源运行请求和选择显示位置，不参与命令执行或结算。

理由：旧链在 Activity 的 shell 回调末尾调用 `markResourceRunSuccess/markResourceInstallFailed`。页面停止收集、运行窗口销毁或 Activity 被重建时，命令可以完成而注册机与队列不更新。把结算放入进程协调器后，执行、CardRun 事实和资源登记形成一条不依赖页面可见性的纵向业务链。

影响：运行事实仍先写 `CardRunStore`，资源协调器只消费提交后的事件并写资源 Store；`RunLifecycleEventHub` 不得缓存或复制状态。manifest 到有限配方的编译属于 Platform Gateway，安装计划推进属于 Application Coordinator。Activity、Fragment 和资源 Screen 不得再调用 Bridge、ToolchainPackInstaller 或自行宣布资源完成。

## ADR-S025 T006 完成后只保留一套运行编排引擎

状态：accepted

决策：所有可执行配方统一进入进程级 `RunOrchestrator` 与 `AndroidRecipeExecutor`。资源运行额外由 `ResourceRunCoordinator` 结算，但不拥有第二套步骤执行器。T006 验收通过后直接删除 Activity 内 legacy 开始、步骤分派、Bridge 结果解释、停止解释和资源结算代码，不保留永远返回 true 的迁移开关或隐藏兼容分支。

理由：旧代码即使已经没有产品入口，仍会复制状态规则、资源命令和停止语义，机器检查也会继续保护错误的旧实现。后续维护者可能误接回 legacy 分支，重新造成页面可见性决定执行、迟到回调覆盖事实和同一实例双执行链。

影响：`MainActivity` 只接收动作、提交编排请求和绑定可见 Effect。网页与 Android action 不再保留直达旁路；shell、terminal、Web、X11 和 Android action 都经过同一执行合同。静态护栏必须检查新所有者并禁止旧方法名回流。

## ADR-S026 配方终端必须使用独立内嵌会话生命周期

状态：accepted

决策：配方 terminal 步骤使用 `createEmbeddedShellSession`，不写入普通终端会话列表。结束 active embedded session 时清空当前绑定并终止目标，不选择或启动任何 managed terminal fallback；命令投递在 holder 挂接尚未完成时必须从 staged embedded record 恢复，并复用幂等 attach/wait 与输入队列。

理由：把卡片终端作为普通会话持久化会留下 `REGISTERED` 记录。用户完成卡片步骤时，控制器关闭目标后会把历史记录当 fallback 重新启动，造成页面已完成而另一个 `proot/bash` 仍存活。直接改为 embedded 后，若定向写入只查持久化列表，又会在启动稍慢时丢失首条命令。

影响：卡片终端的事实仍由 `CardRunStore.terminalSessionId` 持有，普通终端列表不再出现卡片执行残影。页面离开不结束会话；只有用户完成步骤、停止运行或执行终态才闭合 embedded 资源。真机验收必须同时检查 CardRun 状态、命令只执行一次、目标进程归零和 Kite 宿主存活。

## ADR-S027 运行显示面由单一 Host 管理可见绑定

状态：accepted

决策：每个运行窗口只创建一个 `RunSurfaceHost`。Host 根据 `RunSurfaceUiState.structureKey` 持有并替换唯一 `RunSurfaceBinding`；Report、Terminal、Web、X11 和安装向导分别拥有自己的显示绑定与局部更新入口。Activity 负责 Android 壳层和平台适配装配，不得保存显示面内部控件或复制运行事实。

理由：旧实现把报告 TextView、终端 Fragment、共享 WebView、X11 View 和安装向导字段全部放在 `MainActivity`，导致普通状态变化、页面切换与底层任务生命周期互相影响。只拆文件而继续让 Activity 维护这些字段，仍然不会形成真正的职责边界。

影响：`CardRunStore` 继续是运行事实唯一拥有者，`RunSurfaceProjector` 负责事实到显示状态的转换。Host 的 attach、render、tick、dispose 都只影响可见绑定；用户显式继续或停止才通过 Action Gateway 进入 `RunOrchestrator`。迁移按 Report 样板逐面进行，静态护栏禁止旧报告绑定回流。

## ADR-S028 运行窗口返回只解除可见绑定

状态：accepted

决策：系统返回、运行窗口关闭和 Activity 销毁只离开当前显示面，不自动完成等待步骤、不停止运行实例，也不取消资源安装计划。完成步骤、停止实例和取消计划必须分别来自可见的“继续”“停止”“取消”用户动作。

理由：返回属于导航意图，不是运行控制意图。旧实现会在终端等待时把返回解释为完成，并在关闭窗口时根据运行绑定自动停止；用户只是离开页面，后台 shell 或安装队列却被改变，既违反常规交互，也让显示生命周期拥有了执行生命周期。

影响：Terminal Fragment detach、Report Screen dispose、WebView unbind 和 CardRunActivity finish 都不得写运行事实。首页或运行记录重新打开时必须从 `CardRunStore` 恢复同一 instance 与 surface；显式运行控制继续进入 `RunOrchestrator`，不得再按页面类型增加关闭即停止的特判。

## ADR-S029 Web 运行显示面独占可见浏览器实例

状态：accepted

决策：每个 Web 类型的运行窗口由 `RunWebSurfaceBinding` 独立创建并销毁自己的 `WebView`、`KiteWebShell` 与自动化显示会话。普通网页加载、OAuth/CLI loopback 和仅外部浏览器地址都先经过全局唯一的 `BrowserHandoffPolicy`；显示绑定只调用既有认证和外部浏览器 Gateway，不保存认证事实、不重写回调协议。

理由：MainActivity 的共享 WebView 同时承担工作台和运行窗口时，一个页面的导航、返回、销毁或自动化 session 会影响另一个页面。把 WebView 交给运行显示面后，页面历史与可见资源有明确所有者，同时认证桥仍由既有进程能力负责，不会因拆 UI 形成第二套 token 或 loopback 状态。

影响：WebView 历史返回优先于运行窗口退出；显示面 dispose 可以停止加载和销毁该 WebView，但不得停止 CardRun。外部浏览器回跳仍按 `recipeId + instanceId` 更新 `CardRunStore` 并恢复对应显示面，MainActivity 不得重新引入 CardRun 专用 Web 构建方法或共享 WebView 绑定。

## ADR-S030 X11 可见绑定不得拥有 X11 进程

状态：accepted

决策：`RunX11SurfaceBinding` 只根据运行事实中的 DISPLAY/socket 创建并持有可见 `LorieView`。X11 server 启动、命令执行、停止和残留确认继续属于执行层；显示绑定销毁只能移除 View。

理由：X11 画面离开前台并不表示用户要求停止桌面程序。若 View 的 detach/destroy 顺带结束 server 或运行实例，系统返回、旋转和 Activity 重建都会改变后台事实，重新造成显示生命周期与执行生命周期耦合。

影响：轻量 `CardRunActivity` 可以安全替换 X11 可见面而不影响运行。沉浸式系统栏属于 Activity 壳层效果，DISPLAY 分配与进程清理由运行编排器和 X11 Platform 适配器负责。

## ADR-S031 运行窗口启动解析不得包含执行副作用

状态：accepted

决策：`CardRunLaunchResolver` 只把外部输入解析为确定的 recipe、instance、autoStart 和可选安装向导上下文。目录读取和特殊配方由注入的提供者完成；解析阶段不得调用 RunOrchestrator、写 CardRunStore、启动 Activity 或创建显示面。

理由：旧 `handleCardRunLaunchIntent` 同时解析 Intent、加载目录、创建特殊配方、启动实例、注册路由和绘制页面。任何恢复或重复 Intent 都可能在“还没确认目标”时产生执行副作用，且独立 Activity 只能复制整条私有链。先固定无副作用的解析边界，才能让启动幂等、错误可解释并对壳层做纯测试。

影响：解析成功后由 Activity 壳显式登记 recipe，再根据 target 的 autoStart 决定是否提交 `RunOrchestrator.start`。解析失败只能展示错误并退出，不得用默认 recipe 或当前页面状态猜测目标。

## ADR-S032 系统浏览器 handoff 只有一条副作用序列

状态：accepted

决策：所有运行壳发起系统浏览器认证时统一调用 `BrowserHandoffCoordinator`。固定顺序为复用 pending、创建 session、写入目标实例等待事实、准备 callback channel、打开外部浏览器；外部浏览器打开失败必须停止 callback channel 并把同一 session 标记失败。

理由：认证桥的正确性不取决于页面长相，而取决于 session、state、loopback 和目标 run instance 的顺序一致。若 MainActivity 与 CardRunActivity 分别复制编排，任何一边漏写等待事实、漏停端口或重复打开都会重现“浏览器拿到返回但软件没接住”的问题。

影响：Application 协调器不依赖 Android UI；SessionStore、LoopbackBridge、CardRunStore、Custom Tabs 和诊断由 Platform Gateway 适配。页面只消费返回结果与目标事实，不直接调用 `createPending` 或 `prepare`。

## ADR-S033 运行窗口是独立轻量应用壳

状态：accepted

决策：`CardRunActivity` 直接继承 `AppCompatActivity`，只装配一个目标实例的启动解析、运行事实观察、显示面 Host、运行控制动作和必要平台适配。它不得继承 `MainActivity`，不得初始化首页、资源目录、设置、首次向导或主壳服务，也不得复制底层任务状态。

理由：继承完整主壳会让每个运行窗口同时创建两套不相关能力，使启动耗时、内存、导航和生命周期彼此污染；即使把显示代码拆成文件，只要 Activity 仍继承并持有主壳状态，职责所有权就没有真正转移。独立壳让显示生命周期可以重建或关闭，而进程级运行事实和任务继续存在。

影响：所有运行窗口必须从 `CardRunStore` 恢复指定 `recipeId + instanceId`，通过 `RunSurfaceHost` 组合显示绑定，通过 `RunOrchestrator` 提交继续和停止。`autoStart=false` 的恢复请求找不到既有事实时必须明确拒绝，不能新建空白运行；只有首次启动、临时网页和安装向导允许创建。系统返回只结束当前 Activity task；外部浏览器回跳、终端恢复和报告更新按实例事实重新投影。机器护栏永久要求 `activitiesInheritingMainActivity=0`。

## ADR-S034 独立运行任务不属于主应用导航栈

状态：accepted

决策：`AppNavigator` 只管理主应用内部 Destination，不声明 CardRun 或运行显示面类型。首页、运行管理、资源向导、浏览器请求和桌面请求需要显示运行实例时，只能携带明确 `recipeId + instanceId` 启动独立 `CardRunActivity`；`MainActivity` 不渲染运行显示面，也不处理其返回和关闭策略。

理由：运行窗口已经是独立 Android task，若主导航仍保留 CardRun Destination，返回键、恢复状态和页面历史会形成第二套所有权；旧显示代码也容易借这个入口重新接回主壳。运行管理可以展示同一 Store 的摘要，但“查看运行”必须打开事实所指向的独立任务，而不是复制显示状态。

影响：历史保存值 `CardRun` 视为未知 Destination 并回到控制台；关闭独立任务后由 Android 恢复原主壳位置。静态护栏同时禁止 `AppDestination.CardRun`、`DestinationKind.RunSurface`、`NavigationBackAction.CardRunTask` 和 `showCardRunSurface` 回流。
