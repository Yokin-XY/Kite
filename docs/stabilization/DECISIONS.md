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

## ADR-S035 运行管理动作必须等待事实确认

状态：accepted

决策：运行管理的停止运行、结束终端、结束 PID 和停止后台运行项均作为确认事务执行。协调器先发布 `Requested`，状态拥有者接受后发布 `AwaitingConfirmation`；只有统一运行快照证明目标已停止或消失，事务才结束。拒绝、异常或超时必须保留 `Failed` 与可重试语义。

理由：旧页面在异步终止前直接把 CardRun 写成 `Stopped`，再用固定延迟刷新等待进程列表追上。终止失败、快照稍慢或用户结束的是子进程时，页面事实都会与真实进程分叉。页面也无法凭一次按钮回调证明 Ubuntu 内 PID、终端 owner 或整条 CardRun 已闭合。

影响：页面不得调用 `setRuntimeState(...Stopped)` 宣布进程结束，也不得以 `260/900/1800ms` 整页轮询作为主同步机制。CardRun 继续由 `RunOrchestrator` 写状态，终端和 PID 继续由各自 Store 处理；`RuntimeManagementCoordinator` 只保存短期动作事务并消费统一快照，不成为第四个运行事实源。

## ADR-S036 运行管理页面按事实拓扑维护局部绑定

状态：accepted

决策：`RuntimeManagementScreen` 只把卡片、显示面和进程的 key 集合作为结构签名。签名不变时复用已有 View binding 并更新可见字段；签名变化时才重建列表。卡片展开和滚动位置是显示状态，由 Fragment/Screen 在重建时恢复，不写入 Store，也不触发 Activity 重新导航。

理由：旧页面把展开、刷新和事实校准都实现为再次调用 `showKiteProcessOverview()`，每次清空根容器并重新读取多个 Store。这样即使只有一个 PID 状态改变，也会造成滚动丢失、整页抖动和按钮反馈延迟。局部绑定能够让状态拥有者信号直接落到对应行，同时保持事实与显示状态边界清晰。

影响：运行管理 Screen 不得依赖 `CardRunStore`、`TerminalSessionStore` 或 `TaskManagerStore`，Fragment 不得加入固定间隔轮询。统一 Gateway 负责合并事实，Projector 决定 UiState，Coordinator 只维护短期动作事务。主壳只接收返回和打开独立运行窗口的 Effect，不保留运行管理字段、Dialog 或绘制函数。

## ADR-S037 运行准备状态与运行列表事实分离后再组合

状态：accepted

决策：权限、rootfs、Bootstrap 与 readiness 属于 `RuntimeBootstrapGateway`；卡片、终端和 PID 属于 `RuntimeManagementGateway`。`RuntimeStatusFeatureController` 只在显示层之前组合两份稳定快照，生成运行状态弹层需要的一份 UiState。首次授权是一次性 onboarding 输入，不写入任一运行事实源。

理由：旧弹层直接读取三个运行 Store，同时把权限检查、文件探测和 Bootstrap 进度存入 Activity 字段。这样弹层刷新会做重型探测，权限恢复会重建运行数量，且首次授权的临时阶段可能覆盖真实部署失败。两类事实分别拥有后，页面仍可显示统一状态，但不会制造一个包办所有运行事实的新 Store。

影响：readiness 探测必须在 Platform Gateway 的 IO 调度器执行；Projector 和 Controller 不得引用 Android View、具体 Store 或 Foundation 单例。重试通过 Gateway 请求 Bootstrap，权限和系统设置页通过 Shell effect 执行。T010 可以迁移 onboarding 流程，但不能改变 runtime-status 的事实合同。

## ADR-S038 运行状态 Chrome 拥有可见绑定但不拥有运行事实

状态：accepted

决策：状态胶囊、控制台内联提示、准入 Overlay 与运行状态 Dialog 统一由 Activity 级 `RuntimeStatusChrome` 持有。Chrome 只消费 `RuntimeStatusUiState` 并回传一个主动作；它可以随主题或 Activity 生命周期销毁重建，但不得停止任务、修改权限事实、探测文件系统或读取 Store。

理由：这四个表面显示的是同一份状态，旧实现却把二十余个 View 字段和多套绑定函数放在主壳。把它们各自拆成页面会复制状态，把它们继续留在 Activity 又无法建立所有权。单一 Chrome owner 既能局部更新，又能把 Android Dialog/Window 生命周期限制在可见层。

影响：Main 只处理权限、系统设置和导航 Effect，并在 Destination 变化时传入是否抑制临时 Chrome；资源页抑制只影响显示，不改变 bootstrap。状态弹层刷新必须同时校准 bootstrap 与运行管理 Gateway。主题变化先 dispose 旧 Chrome 再按当前 UiState 重建，Activity 销毁只释放可见绑定。

## ADR-S039 普通 Web 工作台拥有显示生命周期，认证桥保持进程级

状态：accepted

决策：普通工作台由 `WebWorkbenchFragment/Screen` 独占 `WebView`、`KiteWebShell`、网页历史和自动化显示会话。系统浏览器认证仍统一进入进程级 `BrowserHandoffCoordinator`；Feature 只提交原始 request 与既有 policy decision，不读取或写入认证 session、loopback 端口和 CardRun 事实。

理由：主壳共享 WebView 会让 Activity 冷启动提前创建浏览器资源，并把工作台销毁、自动化 session 与整个应用生命周期绑定。另一方面，认证正确性依赖进程级 session 与 callback channel，不能随 Fragment 重建而消失。把显示和认证拆开后，页面可以独立释放，已发起登录仍能由应用入口接收并恢复目标运行实例。

影响：主壳不得重新持有 `webView/webShell/browserAutomationController`，普通工作台返回优先消费网页历史。工作台销毁可以关闭自己的自动化显示 session，但不得停止 CardRun、后台进程或 `BrowserAuthSessionStore` 会话。自动化 API 只能控制 Registry 中仍存活的 Web 显示面，没有显示面时返回可解释失败，禁止回退到无关页面的共享控制器。

## ADR-S040 认证回跳由进程级协调器完成确定性交付

状态：accepted

决策：所有 `kite-auth://callback` 回跳统一进入 `BrowserAuthRedirectCoordinator`。协调器按持久化 state 匹配 session，解析 `recipeId + instanceId` 目标，先把结果投影到唯一 `CardRunStore`，再把 session 标记为 delivered 或 failed；只有投影成功才允许 Shell 打开目标运行窗口。

理由：旧实现把解析、session 状态、运行事实和 Activity 跳转写在主壳同一方法中，进程重建后容易依赖尚未恢复的页面字段。认证 session 本来已持久化，CardRun 也有进程级事实，因此回跳交付不应依赖发起页面仍存在。固定副作用顺序还能避免“浏览器已返回、session 已消费，但运行实例没有收到”的半完成状态。

影响：MainActivity 只解释协调器结果并执行目标 Activity 跳转，不得直接调用 `markReturned/markDelivered/markFailed`。CLI loopback 的 callback 转发和过期同步也通过同一协调器校准。协议解析保持通用，禁止按 Codex、Claude、Google 等提供方增加页面特判或改写 code/state/error。

## ADR-S041 首次引导只持久化步骤，不复制权限与运行事实

状态：accepted

决策：首次权限引导由进程级 `FirstRunOnboardingCoordinator` 管理，并在发出权限请求或系统设置跳转前持久化等待阶段。协调器只记录一次性步骤的执行位置；当前缺失权限继续由 Android 权限事实提供，rootfs、Bootstrap 和 readiness 继续由 `RuntimeBootstrapGateway` 提供。完成标记只能在一次性步骤结束后写入。

理由：旧实现用四个 Activity 布尔字段追踪请求，同时在第一步开始前就宣布引导完成。进程回收会丢失字段却保留错误完成标记，导致新启动无法区分“已完成”“被拒绝”和“停在系统设置”。若把权限状态一并持久化，又会产生第二份容易过期的事实。

影响：Activity 只把当前权限快照交给协调器，并执行 `RequestRuntimePermissions` 或 `OpenAllFilesSettings` effect。Activity 重建不得重复系统窗口，进程重建按等待阶段确定恢复点；拒绝不会伪装成授权成功，后续授权继续走 runtime-status 的同一权限事实和动作。旧 `first_run_permission_onboarding_done` 仅作为已有用户兼容输入。

## ADR-S042 设置事实由 Gateway 持有，主题变化只重绑显示环境

状态：accepted

决策：主题颜色、浏览器模式、现场恢复和最近任务可见性统一由 `SettingsGateway` 读写并发布快照；通知授权和投放区可用性作为 Platform 刷新得到的系统事实附加投影。`SettingsFragment/Screen` 拥有设置与主题页面，MainActivity 只执行导航、权限窗口、系统设置页、最近任务和全局主题环境 effect。

理由：旧设置页在 Activity 中同时读取两份 SharedPreferences、查询通知、准备投放区、重建页面和修改 Chrome。主题每点一次颜色都会再次导航并重画整页，投放区刷新甚至会把设置页无条件切回首页。页面、持久化和系统副作用混合后，任何系统返回都只能靠重新调用 `showSettings()` 校准。

影响：设置页面不得直接读 SharedPreferences、探测文件或调用 Activity Host；普通快照变化只局部绑定开关和副标题。主题变化允许重绑当前主题页、RuntimeStatusChrome、底部导航和终端颜色，但不得触发 Bootstrap、重启 CardRun、销毁 Web 显示或重新启动服务。投放区完成只刷新当前可见页面，不改变导航目的地。

## ADR-S043 终端沉浸与返回是 Surface Effect，不是 Activity Host API

状态：accepted

决策：终端列表/详情切换只发送通用 `SurfaceEffect`：标准壳、沉浸壳或请求返回。MainActivity 与 CardRunActivity 各自解释同一 effect；终端 Fragment 不得识别宿主类型、持有底部导航或调用 `TerminalChromeHost`。终端 View 销毁仍只 detach UI，不结束 `TerminalRuntimeHost` 会话。

理由：同一 TerminalFragment 同时用于主应用终端页和独立 CardRun。旧 Host 接口迫使两个 Activity 实现终端专属方法，Main 还需要三个字段保存 Feature 的详情状态；CardRun 的实现则故意忽略沉浸请求。数据 effect 能保留两种壳的不同策略，同时把终端状态留在 Feature 内。

影响：详情返回通过 Shell back 解释，Main 只在当前 Destination 为终端时更改底栏，防止销毁或迟到结果影响其他页面；CardRun 始终保留继续、停止和关闭控制。不得恢复 `TerminalChromeHost`、`activity as?` 终端强转或 `openTerminalSession` 兼容入口。

## ADR-S044 Feature 数据依赖来自 Application Owner，禁止反向强转 Activity

状态：accepted

决策：Feature Fragment 需要目录、Store 或协调器时，通过 Application 暴露的窄 DependenciesOwner 获取；需要返回、导航或系统动作时，通过 Result/Effect 上交。任何 Feature 源都不得用 `activity as?` 获取数据、UiKit 或回调 Host。显示环境可作为参数传入，事实必须从既有 Gateway 读取。

理由：旧 Raw JSON Fragment 虽已拆成文件，却同时强转三套 MainActivity 接口，数据、主题和返回仍由 God Activity 提供；还要隐藏整个 rootHost 才能显示。这只是文件拆分，不是职责转移。统一 Gateway 和 Result 后，页面可独立测试，Shell 也无需保存 Feature 临时状态。

影响：旧根包 `RecipeRawJsonFragment`、`pendingRawJsonRecipeId`、root 隐藏路由和三套 Host 接口删除。架构守卫对全部 Feature 包禁止 Activity 强转；后续模块不能以新 Host 接口恢复同类反向依赖。

## ADR-S045 运行历史和资源补充页是 Feature，不是 Shell 手绘页面

状态：accepted

决策：运行历史通过 Android 无关的 `RunHistoryGateway` 读取既有 `CardRunStore` 快照，由 `RunHistoryFragment/Screen` 独占列表、详情、历史 SH 报告和内部返回状态。资源“更多”和原始 JSON 分别由 `ResourceMoreFragment/Screen`、`ResourceRawJsonFragment/Screen` 拥有；Shell 只路由目标 ID、解释返回和落地创建首页卡片等一次性动作。

理由：这些页面虽然入口来自编辑器和资源详情，但 MainActivity 仍保存了约千行历史格式化、报告复制、资源图标缓存和 View 构建。页面状态变化只能重建根容器，资源日志还直接读取具体 Store。这属于活跃业务显示职责留在应用壳，不是单纯死代码。

影响：Feature 不直接读取 `CardRunStore` 或 `KiteResourceManifestLoader`；Platform 适配器只暴露只读历史快照。指定日志的初始定位只消费一次，用户退回历史列表后，后台 Store 更新不得再次强制打开原详情。MainActivity 不得恢复历史、资源更多或资源 JSON 的 View 工厂。

## ADR-S046 资源动作由 Application 工作流收口，Shell 只解释路由 Effect

状态：accepted

决策：资源获取、恢复向导、打开、停止、卸载、取消和创建首页卡片统一提交给 `ResourceActionWorkflowCoordinator`。`AndroidResourceActionGateway` 复用既有 `KiteResourceInstallStore`、`CardRunStore`、`ResourceRunCoordinator` 与 `RunOrchestrator` 写入事实，并返回打开运行窗口、打开安装向导或离散消息三类 Effect；MainActivity 只启动目标 Activity 或显示结果。

理由：旧 MainActivity 同时保存资源目录缓存、过期状态猜测、打开运行签名、安装计划临时字段和向导 CardRun 注册。资源 Feature 已经直接订阅 Store/Gateway 信号后，这套 Activity 缓存既没有显示消费者，又会制造第二份事实和失真的静态测试。向导运行事实如果仍由 Shell 注册，也只是把页面拆出去了，业务所有权并没有迁移。

影响：资源状态变化由 Feature 直接消费 `ResourceFeatureGateway.changes` 并局部校准；MainActivity 不得恢复 `resourceCatalog`、`resourceCatalogDirty`、资源 Store 观察器或 `showResourceInstallWizard`。向导 Effect 必须携带已注册的 `recipeId + instanceId + targetResourceId + planResourceIds`。计划成员触发恢复或取消时，以 Store 的真实 `targetResourceId` 为目标，不得把依赖项误当作整条计划目标。`CardRunSpecialRecipes` 属于 Application 数据工厂，不再放在 Feature 包中。

## ADR-S047 MainActivity 不保存运行事实副本，只保存可见焦点

状态：accepted

决策：`CardRunStore` 是运行实例、状态和当前实例的唯一事实源。MainActivity 删除按 recipe 保存的 `runtimeStates` 与 `activeRunInstanceIds`；需要解析动作目标时按“显式 instanceId、当前聚焦且 recipe 匹配的实例、Store 当前实例、recipe 默认实例”选择。Shell 只允许保存 `focusedRunInstanceId` 这种可丢失的显示焦点。

理由：两个 Activity Map 在启动、停止、浏览器回跳、桌面请求和后台 Effect 中分别补写，任何漏写都会让页面动作落到过期实例。它们既没有独立持久化语义，也没有比 Store 更多的真实信息，只是第二事实源。显示焦点可以随 Activity 销毁而丢失，运行事实不能。

影响：运行停止只清除匹配的 Shell 焦点，不删除或复制 Store 事实。编辑器删除、投放区刷新、浏览器回跳和桌面请求不得再维护 Activity 运行索引。架构守卫锁定 Main 中 `runtimeStates/activeRunInstanceIds` 为零；多实例选择继续由显式实例和 Store 当前代次决定。

## ADR-S048 配方动作计划与运行副作用属于 Application 工作流

状态：accepted

决策：首页和编辑器提交的 `KiteRecipeActionRequest` 统一进入 `RecipeActionWorkflowCoordinator`。已有 `KiteRecipeActionCoordinator` 继续只决定轻量计划；`AndroidRecipeActionGateway` 负责解析唯一 CardRun 事实、调用 `RunOrchestrator.start/stop`、记录诊断和落失败状态。MainActivity 只解释准备运行时、聚焦、打开/关闭独立运行窗口、回首页和离散消息 Effect。

理由：旧 Main 虽复用了纯 Planner 和 RunOrchestrator，却仍自己解释每一种 Plan、选择实例、写失败事实和决定页面落点。首页与编辑器共享同一请求合同后，副作用继续留在 Activity 会让新入口再次复制分支。工作流收口既不改变执行引擎，也让动作合同可以纯单测。

影响：MainActivity 不得出现 `KiteRecipeActionPlan`、`executeRecipeActionRoute` 或具体 Planner；Platform Gateway 不得依赖 Activity、View 或 Feature。现有首页启动时回 Console、编辑器启动时打开独立运行窗口、显式独立任务自动启动和停止后回 Console 的行为保持。迟到执行回调由 RunOrchestrator 代次与 CardRunStore 停止写保护处理，不再保留 Activity 级 stale callback 解释器。

## ADR-S049 本地桌面请求由 Platform 准备 X11，Shell 只打开运行窗口

状态：accepted

决策：本地服务器的桌面请求先进入 Android 无关的 `DesktopOpenCoordinator` 做输入归一化，再由 `AndroidDesktopOpenGateway` 解析配方、分配 X11 display/socket、写入 CardRun 事实并启动既有 `KiteX11SurfaceServer`。MainActivity 只把结果映射回本地服务器响应、投递既有 `CardRunDesktopRouter`，并在需要时打开独立运行 Activity。

理由：桌面请求的业务和运行事实原先全部写在 Main，包括临时配方、实例分配、X11 资源选择、失败投影和诊断；它并不是 Activity 显示职责。迁移只改变所有权，不改变 X11 内核、显示控件、命令协议或现有运行窗口。

影响：X11 启动失败时仍保留失败 CardRun 并为新临时请求打开报告窗口；已有 instance 请求不重复新建窗口。MainActivity 不得恢复 `acceptDesktopOpenRequest`、`temporaryDesktopRecipe`、`KiteX11SurfacePlan` 或 `KiteX11SurfaceServer` 调用。Platform Gateway 不得创建 View 或依赖 Activity/Feature。

## ADR-S050 本地浏览器与 APK 入站分离事实处理和系统显示

状态：accepted

决策：本地服务器浏览器请求经 `BrowserOpenCoordinator + AndroidBrowserOpenGateway` 路由；Gateway 优先投递既有 CardRun 浏览器显示面，否则写入指定实例或创建临时 Web CardRun。APK 请求经 `InstallApkCoordinator + AndroidInstallApkGateway` 归一化、限制支持路径并检查文件。MainActivity 只打开临时 CardRun Activity 或 Android 系统安装器。

理由：浏览器运行事实、临时配方和 APK 文件解析原本与 Activity 跳转混在三个 Main 方法中。前者属于运行路由/Store，后者属于 Platform 文件边界；只有启动可见窗口和系统 Intent 才是 Shell 职责。分离后不会为修路径或状态同步重进 Main，也不会把 WebView/安装器副作用下沉。

影响：既有 `CardRunBrowserRouter` 优先级、指定实例 URL 更新、临时网页失败时回退工作台、`/exchange` 与 `/sdcard`/`/storage` 支持范围和响应错误码保持。Gateway 禁止创建 WebView、Activity 或安装 Intent；Main 不得恢复 `updateBrowserRequestState`、`openTemporaryBrowserRequest` 或 `resolveInstallApkFile`。

## ADR-S051 自动化入口复用正式 Application 工作流

状态：accepted

决策：ADB 自动化的卡片停止、资源直接安装和资源 owner 探针不得在 `MainActivity` 中保留第二套运行编排。停止复用 `RecipeActionWorkflowCoordinator`，资源安装复用 `ResourceActionWorkflowCoordinator`，owner 探针通过 `RuntimeOwnerProbeCoordinator + AndroidRuntimeOwnerProbeGateway` 进入正式 `RunOrchestrator`。探针配方由 Application 层的 `CardRunSpecialRecipes` 统一创建。

理由：自动化入口虽然只用于验收，但它实际会创建和停止正式运行实例。若 Shell 自行调用 Orchestrator、构造配方或写 Store，真机验证通过的将是旁路而不是产品合同，并且迁移完成后仍会迫使应用壳持有执行职责。

影响：`MainActivity` 不再持有 `RunOrchestrator`、`ResourceRunCoordinator`，也不再定义 `startRecipeWithOrchestrator`、`stopRecipeWithOrchestrator` 或资源探针配方。自动化与用户点击共享同一状态拥有者、执行入口和迟到回调保护；静态守卫必须验证该合同，不得恢复只为测试存在的 Shell 特判。
