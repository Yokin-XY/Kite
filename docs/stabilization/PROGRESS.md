# Kite 主线稳定化进度

最后更新：2026-07-12

## 当前恢复指针

```text
方向：第二阶段业务架构迁移
状态：in_progress
当前任务：T006 运行编排与执行引擎，已完成最终收口与真机验收
代码分支：main
代码策略：单会话连续推进 D1-D5，Git 单主线，阶段性本地提交
```

## 方向总览

| 方向 | 状态 | 当前结论 |
| --- | --- | --- |
| P0 公共行为安全网 | done | 静态检查、动作路由测试、全量单测和 Debug 构建通过 |
| D1 导航与返回 | done | Destination、返回优先级、恢复策略和主要真机路径均已验收 |
| D2 动作编排 | done | 卡片、资源、向导和明确实例动作均经过统一入口 |
| D3 状态投影 | done | 资源事实、向导步骤和 CardRun 显示语义已统一，后台更新不再越权导航 |
| D4 生命周期和资源预算 | completed | 生命周期合同、压力链、构建和真机验证完成 |
| D5 功能模块与扩展点 | completed | 终端扩展点、进程管理单入口和真机验收完成 |
| 第二阶段架构迁移 | planned | 采用模块化单体与单向状态流，按 T001-T012 顺序迁移真实职责 |

## P0 公共安全网记录

目标：让后续每个结构调整都有可信的自动回归基础，而不是依赖源码字符串或人工记忆。

完成标准：修复验证误报、补共享动作路由测试，并通过全量单测和 Debug 构建。

依赖检查：正式版本 `v0.0.1` 和本地 `main@cc70520` 已存在；P0 无前置方向任务。

已知证据：

- `MainActivity.kt` 约 2.1 万行，导航、动作、状态投影和生命周期仍高度集中。
- `KITE_RUNTIME_LANE_STATIC_CHECKS.ps1` 当前报告 11 项失败。
- 核对代码后，资源安装 Store 的失败项实际都已调用 `emitSignal`。
- `stopRecipe` 已委托 `stopRecipeByCardInstanceId`，失败来自脚本要求旧的精确调用文本。

## P0 完成记录

实现：

- 新增稳定化三件套，固定会话分支、代码单主线和阶段提交边界。
- 静态检查新增成员函数体提取，资源信号检查不再锁死调用参数排版。
- 停止委托检查改为验证入口和 `cardInstanceId`，允许合法增加调用参数。
- 新增 5 条 `KiteActionRouter` 合同测试，覆盖标准停止兜底、显式停止动作、
  命名动作归一化、缺失动作和空动作。

提交：

- `e8c6b7e [S1] establish stabilization handoff baseline`
- `174321b [S1] make runtime lane checks semantic`
- `bcff4b8 [S1] cover shared action routing contracts`

验证：

- `scripts/KITE_RUNTIME_LANE_STATIC_CHECKS.ps1`：通过。
- 动作路由、资源安装信号、资源投影、MainActivity 路由目标测试：通过。
- `:app:testDebugUnitTest`：`BUILD SUCCESSFUL`。
- `:app:assembleDebug`：`BUILD SUCCESSFUL`。
- 本阶段没有用户可见行为改动，因此未安装真机；S2 恢复 OnePlus 8T 真机验收。

## D1 当前动作

1. 审计所有 `currentScreen = Screen.*`、`show*()`、顶部返回、系统 back 和恢复入口。
2. 建立导航目标、父子关系和返回策略矩阵。
3. 先写纯合同与测试，再让现有入口渐进接入。
4. 不改页面视觉，不改动作执行、资源 Store、浏览器认证和运行生命周期。
5. 每形成一个可构建、可回退的小段立即本地提交。

## D1 执行记录

### 当前节点三问

- 目标是什么：让 `MainActivity` 的系统 back、顶部返回、Screen 登记和状态恢复统一经过 D1 导航合同。
- 完成标准是什么：父页面、上下文、系统、CardRun 和恢复边界有自动化证据；全量单测、Debug 构建和 OnePlus 8T 验收通过。
- 依赖是否满足：P0 公共安全网已完成，`Destination + BackPolicy + RestorePolicy` 合同及纯测试已提交，依赖满足。

边界：不改页面视觉，不修改资源或运行状态事实，不调整浏览器认证协议，不改变任务生命周期。

### 导航合同与矩阵

- 17 个 `currentScreen` 写入点已盘点，共覆盖 16 个 Screen。
- 返回行为归为系统、父页面、上下文、CardRun 任务四种合同。
- Web 历史和终端详情定义为 Activity 合同前的显示面优先消费者。
- `ScreenRouter` 现在拥有完整 Destination、BackPolicy 和 RestorePolicy 表。
- 新增 7 条纯合同测试，目标完整性、父子关系、上下文、恢复和委托全部通过。

下一步：把 MainActivity 的系统 back、顶部返回、Screen 登记和状态恢复接入同一合同。

### Activity 与显示面返回入口接入

- `MainActivity` 已改用生命周期感知的 `OnBackPressedDispatcher`，统一解析 Web、CardRun、上下文、父页面和系统返回。
- 17 个 Screen 写入点全部经过 `enterScreen`，顶部返回和底部主导航不再各自维护返回目标。
- Recipe 原始 JSON、资源更多/原始 JSON、历史详情和编辑草稿保留上下文返回动作。
- 终端详情顶部返回改为提交统一 back 请求，由 Fragment 自己的回调优先消费。
- 状态恢复由 `RestorePolicy` 驱动，保留原有白名单和缺少参数时不恢复的边界。
- `MainActivityScreenRoutingTest` 新增资源搜索、资源详情、进程管理和恢复边界覆盖。
- 静态检查禁止直接写 `currentScreen = Screen.*` 和恢复旧 `onBackPressed()` 分支。

验证：

- `ScreenRouterContractTest + MainActivityScreenRoutingTest`：`BUILD SUCCESSFUL`。
- `scripts/KITE_RUNTIME_LANE_STATIC_CHECKS.ps1`：通过。
- `git diff --check`：通过，仅有既存换行符提示。

下一步：补 Web 历史和终端详情优先消费的自动化证据，再做全量构建与 OnePlus 8T 验收。

### 显示面优先消费证据

- 新增 WebView back 跟踪测试：工作台存在网页历史时只调用 `goBack()`，Screen 保持 Workbench。
- 终端详情回调与顶部按钮加入静态合同护栏，分别锁定“详情回列表”和“提交统一 dispatcher”。
- 曾尝试用 Robolectric 挂载完整 `TerminalFragment`；断言通过，但会启动真实终端运行时并留下临时文件占用，因此撤销该重型测试，改由 OnePlus 8T 完成行为验收。

验证：

- `MainActivityScreenRoutingTest`：`BUILD SUCCESSFUL`。
- `scripts/KITE_RUNTIME_LANE_STATIC_CHECKS.ps1`：通过。

下一步：运行全量单测和 Debug 构建，随后在 OnePlus 8T 验收返回矩阵。

### D1 最终验收

提交：

- `2656f82 [D1] align stabilization work by direction`
- `99fdc6d [D1] define navigation destination contracts`
- `3731c02 [D1] unify activity and surface back dispatch`
- `60e85a0 [D1] verify surface back priority`

自动化证据：

- `scripts/KITE_RUNTIME_LANE_STATIC_CHECKS.ps1`：通过。
- `:app:testDebugUnitTest`：`BUILD SUCCESSFUL`，28 秒。
- `:app:assembleDebug`：`BUILD SUCCESSFUL`，16 秒。
- 源码复核：所有 Screen 写入均经过 `enterScreen`；页面顶部返回均提交统一 back 请求，编辑弹窗的关闭动作除外。

OnePlus 8T `3f8bbaad` 证据：

- APK 覆盖安装成功；MainActivity 冷启动成功，进程持续存活。
- 设置 -> 主题：系统 back 回设置；设置顶部返回回首页。
- 资源 -> 搜索：系统 back 在键盘关闭后回资源页；顶部返回直接回资源页。
- 首页 -> 运行管理：系统 back 回首页。
- 终端列表 -> 新终端详情：第一次系统 back 回终端列表，第二次回首页。
- 最终 logcat 未匹配到崩溃、ANR 或输入超时。

D1 结论：导航合同和必要接入已完成；后续 D2-D5 只能消费 `ScreenRouter`、`enterScreen` 和统一 back 请求，不得恢复页面自行维护返回目标。

## D2 当前节点三问

- 目标是什么：统一卡片与资源动作的接收和编排入口，消除首页、编辑页、运行面之间的行为分叉。
- 完成标准是什么：引用 `PLAYBOOK.md` 的 D2 五项验收，不以新增类或减少行数代替行为证据。
- 依赖是否满足：P0 与 D1 已完成；动作路由已有基础合同测试，依赖满足。

动作通道分诊：

- 主要通道：Action Intake；下游保留 Run Instance、Orchestrator、Runtime Prep 和 Surface Binding。
- 状态拥有者：卡片运行事实仍归 `CardRunStore`，资源安装事实仍归 `KiteResourceInstallStore` 与资源登记记录。
- 禁止边界：动作入口不得直接执行 PRoot/终端重活，不复制运行事实，不用页面刷新掩盖动作分叉。

下一步：列出所有用户动作入口和最终委托，先以卡片首页/编辑页作为第一条完整迁移链。

### D2 卡片动作第一条迁移链

- 新增 `KiteRecipeActionRequest`，明确 Primary、Start、Open、Stop 意图和 ConsoleCard、Editor、RunSurface 来源。
- 新增纯 `KiteRecipeActionCoordinator`，只生成 Ignored、RuntimeRequired、OpenRun、LaunchTask、Stop、Execute 计划。
- 首页卡片和编辑页启动、打开、停止全部改为调用 `submitRecipeAction`。
- `startRecipe`、`stopRecipe`、CardRunActivity、运行环境准备和 `CardRunStore` 所有权保持不变。
- 已有运行绑定时，编辑页 Start 归一化为 OpenRun，避免重复创建运行实例。
- 保留编辑页原行为：启动中仍允许显式打开或停止，只有重复 Primary/Start 被拦截。

验证：

- `KiteRecipeActionCoordinatorTest`、`KiteActionRouterTest`、`MainActivityScreenRoutingTest`：`BUILD SUCCESSFUL`。
- `scripts/KITE_RUNTIME_LANE_STATIC_CHECKS.ps1`：通过。

下一步：提交本节点，审计并迁移资源获取、打开、停止、卸载和重试入口。

### D2 资源动作入口

- 新增 `KiteResourceActionRequest`，统一 Install、ReopenInstall、Open、Stop、Uninstall、CancelInstall、CancelFailedInstall 等意图。
- 资源卡片主按钮由 `KiteResourceActionCoordinator` 把过渡期投影标签归一化为意图。
- 资源详情副按钮、失败取消、向导重试和卸载后续接全部改为 `submitResourceAction`。
- 安装、卸载、打开、停止的既有执行函数保持为动作入口下游，不搬动 Store 或执行核心。
- 源码扫描确认，页面与续接逻辑不再直接调用这些处理器，直接调用只存在于统一分发函数内。

验证：

- `KiteResourceActionCoordinatorTest`、`KiteRecipeActionCoordinatorTest`、`MainActivityScreenRoutingTest`：`BUILD SUCCESSFUL`。

下一步：收口安装向导的开始获取、完成和异常处理动作。

### D2 安装计划与实例动作

- 新增 `KiteInstallPlanActionCoordinator`，把向导主按钮归一化为禁用、StartNext 或 Finish 计划。
- 向导按钮绑定不再直接启动下一项、清理上下文或导航；全部交给 `submitInstallPlanAction`。
- `KiteRecipeActionRequest` 增加可选 `instanceId`，统一入口先绑定目标实例，再分发 Open/Stop。
- 运行窗口关闭、CardRun 关闭返回和进程管理停止均提交明确实例，不再直接调用 `stopRecipe`。

验证：

- 三组动作协调器定向测试：`BUILD SUCCESSFUL`。
- 静态检查锁定向导按钮无直接执行/导航，以及运行窗口和进程管理停止携带目标实例。

下一步：运行 D2 全量单测和构建，真机验证首页卡片、编辑页、资源页和运行管理动作入口。

### D2 最终验收

提交：

- `f05f8c6 [D2] continue stabilization in one session`
- `480df61 [D2] unify recipe action intake`
- `86c90e8 [D2] unify resource action intake`
- `af6b4f9 [D2] coordinate install plan and instance actions`

证据：

- `KiteRecipeActionCoordinatorTest`、`KiteResourceActionCoordinatorTest`、`KiteInstallPlanActionCoordinatorTest` 和既有动作路由测试通过。
- `scripts/KITE_RUNTIME_LANE_STATIC_CHECKS.ps1`：通过。
- `:app:testDebugUnitTest :app:assembleDebug`：`BUILD SUCCESSFUL`，44 秒。
- OnePlus 8T：首页单次启动只创建一个 CardRunActivity；关闭运行窗口停止同一实例并回首页，首页显示一个已停止实例。
- 最终 logcat 未发现崩溃、ANR 或输入超时。

D2 结论：页面只提交动作意图，协调器只生成轻量计划；运行事实与重活继续归 Store、编排器和执行核心。

## D3 当前节点三问

- 目标是什么：统一首页、资源、安装向导和进程管理的状态投影与局部更新。
- 完成标准是什么：引用 `PLAYBOOK.md` 的 D3 五项验收，必须有状态一致性和可见局部更新证据。
- 依赖是否满足：P0、D1、D2 已完成；动作入口现在可稳定发出状态变化，依赖满足。

下一步：先以资源卡片与安装向导为样板，核对 Store 事实、`KiteResourceUiProjector` 和各页面本地判断的重复部分。

### D3 安装向导步骤投影样板

- 发现资源卡片已使用 `KiteResourceUiProjector`，安装向导步骤仍在 Activity 内平行解释相同安装事实。
- 新增 `KiteResourceInstallStepUiProjector`，统一卸载、失败、计划运行、完成、阻塞和等待的优先级。
- 投影器输出状态文字、语义色调、失败和卸载标记；Activity 只把语义色调映射到主题 token。
- 安装向导行不再维护本地 `statusLabel when`，Store 与计划事实仍由原拥有者提供。

验证：

- `KiteResourceInstallStepUiProjectorTest` 与 `KiteResourceUiProjectorTest`：`BUILD SUCCESSFUL`。
- 静态护栏禁止安装向导恢复平行状态文字决策树。

下一步：把资源详情、资源首页和安装向导的可见绑定统一消费相同 Resource UiState，并审计整页刷新残留。

### D3 后台资源状态不得越权导航

- 审计发现 `settleVisibleResourceMutation` 在非资源页面会调用 `showResources()`，后台安装或卸载完成可能强制改变用户当前页面。
- 现在只有可见资源页面执行局部校准；设置、终端、编辑页等其他页面只失效缓存并标记 `resourceCatalogDirty`。
- 新增 Robolectric 回归：设置页收到后台资源完成后仍停留设置页，同时资源缓存变脏。
- 静态护栏禁止该入口重新调用 `showResources()`。

验证：

- `MainActivityScreenRoutingTest` 与步骤投影测试：`BUILD SUCCESSFUL`。
- `scripts/KITE_RUNTIME_LANE_STATIC_CHECKS.ps1`：通过。

下一步：让剩余资源完成/失败回调携带资源 ID，只 patch 对应可见卡片、详情或向导行。

### D3 资源运行事实统一

- 完整资源目录与可见卡片增量 patch 原先分别计算计划失败、计划忙碌、登记状态和 Node 本地基线。
- 新增 `KiteResourceRuntimeFactsProjector`，统一生成 installed、preparing、installing、uninstalling、failed 和 extraBusy。
- 全量目录与增量绑定已共同消费该投影器，Node 本地基线作为明确输入，不再拥有平行计划判断。
- 删除 Activity 内两套重复的计划状态判断。

验证：

- `KiteResourceRuntimeFactsProjectorTest` 覆盖待执行忙碌、依赖步骤失败传导和本地基线合并。
- 资源投影、向导步骤投影和 MainActivity 路由测试：`BUILD SUCCESSFUL`。

下一步：审计首页卡片与进程管理的运行状态投影，消除页面各自判断 Starting/Running/Stopping 的分叉。

### D3 卡片运行状态投影

- 新增 `KiteCardRunUiProjector`，统一 badge、语义色调、主动作、按钮文字、可用性、live 和 problem。
- 首页主按钮与状态徽标改为消费共享投影。
- 进程管理状态颜色改为消费同一语义色调，失败不再与停止中共用警告色。
- 执行状态机和 `CardRunStore` 写入路径未改动。

验证：

- `KiteCardRunUiProjectorTest` 覆盖运行/停止、失败重试和运行环境阻塞。
- 卡片、资源事实和 MainActivity 路由定向测试：`BUILD SUCCESSFUL`。

下一步：D3 全量单测与构建，OnePlus 8T 验证首页、资源页、向导和运行管理状态一致性。

### D3 最终验收

提交：

- `51f263a [D3] centralize install step projection`
- `7e4b56c [D3] keep background resource updates local`
- `d474063 [D3] share resource runtime facts`
- `693b620 [D3] share card run presentation`

证据：

- 资源运行事实、资源卡片、向导步骤、首页卡片与运行管理均由纯投影器解释状态。
- 非资源页面收到后台资源完成只记脏，不再强制导航。
- `:app:testDebugUnitTest :app:assembleDebug`：`BUILD SUCCESSFUL`，36 秒。
- OnePlus 8T：首页显示 OpenClaw“启动”；资源页显示确定的“获取/打开”；运行管理为运行卡片 0、进程 0。
- logcat 未发现崩溃、ANR 或输入超时。

## D4 当前节点三问

- 目标是什么：明确页面、显示面、运行实例和底层进程的生命周期边界，并统一资源预算。
- 完成标准是什么：引用 `PLAYBOOK.md` 的 D4 五项验收，不以简单释放所有对象作为优化。
- 依赖是否满足：D1-D3 已完成，导航、动作和状态事实边界已稳定，依赖满足。

下一步：从 `onStop/onDestroy/onTrimMemory`、终端 attach/detach、WebView 和 RuntimeReclaimer 开始建立生命周期矩阵。

### D4 Activity 显示面释放

- 新增生命周期矩阵，明确页面切换、Activity/Fragment 销毁、用户停止和内存压力的不同合同。
- `onDestroy` 新增 `releaseActivityDisplaySurfaces`：关闭 Activity 级浏览器自动化 session，detach 并 destroy WebView。
- 该释放路径不调用 `stopRecipe`、`CardRunStore`、`TerminalRuntimeHost.release` 或资源计划清理。
- 资源向导销毁函数改名为 `releaseResourceInstallWizardSurfaceIfActivityDestroyed`，明确只释放向导显示实例，不停止安装子任务。
- 终端 Fragment 继续使用 `TerminalRuntimeHost.detachUi`，不结束终端 session。

验证：

- MainActivity 生命周期测试确认销毁后显示面释放标记成立。
- 静态护栏锁定 WebView 释放和后台运行事实保留边界。

下一步：审计 `RuntimePressureResponder` 的 trim/low-memory 路径，确保刷新快照后真正进入现有策略与回收链，且不回收用户锁定运行。

### D4 系统内存压力交接

- `RuntimePressureResponder` 只把 Android 内存事件转换为现有运行快照刷新，不直接终止任务。
- 普通 trim 与 `onLowMemory` 分别记录真实事件，低内存状态不再被紧接着的普通 trim 覆盖。
- 同级或更低压力在 2.5 秒窗口内合并；更高级压力可以越过冷却，避免关键刷新被较早的低级事件吞掉。
- 刷新后的快照继续经 `RuntimeMemoryLifecycleRuleTrigger`、`RuntimeLifecycleStrategyActivator` 和 `RuntimeReclaimer`；用户锁定、前台、系统核心、匹配歧义和租约未到期仍由既有准入合同阻断。

验证：

- `RuntimePressureResponderTest` 覆盖同级冷却、压力升级和前台 Activity 存在时的低内存事实保留。
- 目标单测通过。
- 静态护栏确认终端 View 销毁只 detach、卡片/资源/终端 owner 不进入通用自动回收、内存回调不直接停止任务。

下一步：执行 D4 全量单测、Debug 构建和 OnePlus 8T 压力验证；通过后关闭 D4 并自动进入 D5。

### D4 验收关闭

验证：

- `:app:testDebugUnitTest` 与 `:app:assembleDebug` 均 `BUILD SUCCESSFUL`。
- `KITE_RUNTIME_LANE_STATIC_CHECKS.ps1` 通过。
- OnePlus 8T 冷启动 1818ms；在 0.7 秒内连续注入 `RUNNING_LOW`、`RUNNING_CRITICAL`，两级事件均进入快照刷新链。
- 切到后台注入 `COMPLETE` 后进入任务管理刷新，应用 PID 保持 `28770`；返回前台热恢复 296ms。
- 真机首页结构正常，logcat 未发现崩溃、ANR 或输入超时。

D4 状态：completed。

## D5 当前节点三问

- 目标是什么：让功能模块拥有页面、状态投影和动作入口，`MainActivity` 逐步收口为应用外壳与导航；合并进程管理重复入口，并把终端快捷面板改为可注册动作。
- 完成标准是什么：引用 `PLAYBOOK.md` 的 D5 范围，以职责所有权真实转移、扩展点可测试和 MainActivity 不再新增业务编排为准。
- 依赖是否满足：D1-D4 已完成，导航、动作、状态和生命周期合同稳定，依赖满足。

下一步：先审计现有 Fragment 的反向委托和终端快捷面板硬编码，选择最小完整迁移链建立模块接口。

### D5 终端快捷面板扩展点

- 新增 `TerminalPanelActionRegistry`，默认两页八个动作由注册表拥有。
- 注册表支持按页面追加、替换和移除动作，稳定保留页面及动作顺序。
- `TerminalFragment` 只渲染注册表快照并提供发送输入、调整字体、粘贴和主题能力。
- 方向键仍由终端显示模块拥有；动作清单不再硬编码在 Fragment。

验证：

- `TerminalPanelActionRegistryTest` 覆盖默认合同和同 ID 替换。
- 目标单测通过。

下一步：真机确认面板外观和八个默认动作入口未回归，随后处理进程管理双实现。

### D5 进程管理单入口

- 确认用户实际入口为 `showKiteProcessOverview()` 的“运行管理”，事实源为 `TaskManagerStore`、`CardRunStore` 和 `TerminalSessionStore`。
- 删除无路由、无 Manifest、无调用方的旧 `TaskManagerFragment` 及其两份专用布局。
- 删除只供旧页面使用的字符串和颜色；当前运行管理的视觉与行为不变。
- 静态护栏不再要求维护失联页面，只校验当前运行管理按 owner、unit 和 PID 使用 `TaskManagerStore`。

下一步：构建验证资源清理完整，再选择资源模块的一条反向 Activity 渲染链转移真实所有权。

### D5 验收关闭

验证：

- 终端功能链由 `TerminalFragment` 拥有页面和显示状态，由 `TerminalPanelActionRegistry` 拥有动作定义与顺序，不反向委托 Activity 渲染。
- 进程管理只保留 `showKiteProcessOverview()` 用户入口，统一消费 `TaskManagerStore`；失联 Fragment 和 607 行专用代码/资源已删除。
- `:app:testDebugUnitTest :app:assembleDebug` 与静态车道检查通过。
- OnePlus 8T 第一页显示 Ctrl+C、Ctrl+L、字体和方向键；第二页显示 Esc、Tab、粘贴和当前主题。
- OnePlus 8T 运行状态显示终端 1、进程 2；运行管理显示同一份 2 个进程事实。
- logcat 未发现崩溃、ANR 或输入超时。

D5 状态：completed。

## 五方向完成后的优化节点

- 目标：量化启动耗时、APK 体积和 `MainActivity` 剩余职责，不用大重写破坏已验收行为。
- 完成标准：先形成可复查基线，再只处理收益明确、风险可控的热点。
- 当前依赖：D1-D5 均已完成，具备稳定行为基线。

下一步：分析 Debug APK 组成、启动主线程阶段耗时和 MainActivity 体量，选择第一个可验证优化。

### APK 体积基线与压缩资产修正

- 优化前 Debug APK 为 246,809,486 bytes；assets 压缩后占 196,377,276 bytes。
- Android 打包工具会把 `.tar.gz` 自动展开为 APK 内 `.tar` 再重新压缩，三个大归档比源 gzip 流合计多约 11MB。
- rootfs、Python 和 uv 归档改用 `.tgz` 保留 gzip 流；内容、离线安装语义和资源卡依赖不变。
- exchange rootfs 同时支持新 `.tgz` 和旧 `.tar.gz/.tar`，避免破坏已有投放方式。
- 新增源归档 gzip 合同测试和 APK 产物体积审计脚本。

下一步：重建 APK，核对归档条目、体积收益、首次 rootfs 读取合同和 OnePlus 8T 升级启动。

验证：

- 优化后 Debug APK 为 241,594,058 bytes，较基线减少 5,215,428 bytes。
- APK 内压缩 assets 为 183,990,598 bytes，较基线减少 12,386,678 bytes。
- 产物只包含三个 `.tgz` 条目，不包含对应的展开 `.tar`；体积审计上限为 243,269,632 bytes。
- 源归档 gzip 合同测试、全量单测、Debug 构建、运行车道静态检查和 APK 审计均通过。
- OnePlus 8T 覆盖安装成功，冷启动 1784ms，进程正常；未发现崩溃、ANR、输入超时或资产读取异常。

下一步：继续量化启动阶段和剩余体积大项；rootfs、离线工具链和 X11 原生库的去留涉及发行能力边界，不在没有迁移通道时直接删除。

### 本地服务单连接崩溃边界

- OnePlus 启动诊断中发现真实 `uncaught_exception`：浏览器/客户端提前断开后，`KiteLocalServer.writeBytes` 抛出 `SocketException: Broken pipe`。
- 原实现的每客户端线程没有异常边界，单连接写失败会进入全局未捕获处理器并杀死整个应用进程。
- 每个客户端请求现在独立捕获：`IOException` 记为客户端断开，其他错误记为单请求失败；服务主循环和应用进程继续运行。
- 新增分类单测和静态护栏，禁止 `handleClient` 再次裸奔在线程入口。

下一步：复现客户端提前断开，确认服务仍可接受后续 `/status` 请求且进程 PID 不变。

验证：

- OnePlus 8T 通过 ADB 转发连续 8 次发送请求后 TCP RST 断开。
- 测试前后应用 PID 均为 `32264`，随后 `/status` 返回 `{"ok":true,"app":"Kite","version":"0.3","server":"running"}`。
- 目标单测、Debug 构建和运行车道静态检查通过；未出现 `FATAL EXCEPTION`、ANR 或新的未捕获 `Broken pipe`。

下一步：恢复启动耗时优化，优先减少不必要的主线程同步持久化和重复页面重建。

### 启动阶段持久化降阻塞

- OnePlus 当前时间线从 `application.process_created` 到 `main.first_frame_ready` 为 1578ms。
- 普通 `markStage` 每次都在启动线程调用 `SharedPreferences.commit()`；Application 与 MainActivity 首帧前会重复同步刷盘二十余次。
- 普通阶段改为 `apply()`：进程内状态立即可读，磁盘异步落盘。
- `beginAttempt`、`markReady` 和失败记录继续同步 `commit()`，保证跨进程诊断边界不变。
- 增加即时可见性测试和静态护栏。

下一步：全量测试并在 OnePlus 8T 连续冷启动采样，比较首帧时间线与 ActivityManager TotalTime。

验证：

- OnePlus 8T 三次冷启动 `TotalTime`：1659、1659、1571ms，平均 1629.7ms。
- 三次进程创建到 `main.first_frame_ready`：1453、1574、1412ms，平均 1479.7ms。
- 相对改动前同机 1734ms / 1578ms 基线，两个口径均约减少 100ms。
- 启动诊断单测、Debug 构建和静态护栏通过；无崩溃、ANR 或输入超时。

下一步：审计 `onResume` 对 Console/Settings 的整页重建，只在显示面缺失时重建，已存在页面改为局部校准。

### Console 回前台复用

- `onCreate` 已完成 Console 首次渲染，旧 `onResume` 随后无条件再次 `showConsole()`，重复加载配方、清空绑定和重建整页。
- 新增 `resumeConsoleSurface`：显示面仍挂载、投放区状态相同且配方结构未变时，保留页面和滚动现场，只校准运行卡片。
- 页面缺失、投放区变化或配方变化时仍回到完整 `showConsole()`，不牺牲正确性。
- 设置页继续完整校准系统权限，不纳入本次复用。

下一步：运行 Console 生命周期测试并再次采样 OnePlus 8T 冷启动及后台返回。

验证：

- Console 生命周期测试确认 pause/resume 前后复用同一个 `consolePageBodyHost`。
- OnePlus 8T 三次冷启动 `TotalTime`：1722、1489、1452ms，平均 1554.3ms。
- 三次内部首帧：1537、1330、1512ms，平均 1459.7ms。
- 热返回 221ms，前后 PID 均为 `4773`，稳定帧截图确认首页完整且现场保留。
- 相对最初 1734ms 单次基线，当前三次平均减少约 180ms；无崩溃、ANR 或输入超时。

下一步：执行最终全量测试、构建、静态检查和 APK 审计，并区分 APK 分发体积与安装后 rootfs/工具链占用。

### 工具链共享缓存

- OnePlus 当前内部数据约 3,078,095KB；其中 `.kf/cache/resources` 约 1,040,587KB。
- 根因是每个 bundled 资源都把同一份约 91MB `ai-dev-pack` 复制到自己的缓存目录。
- `localPackPath` 改为统一 `/workspace/.kf/cache/shared/ai-dev-pack`；资源自己的缓存由独立 `resourceCachePath` 表达，卸载不会误删共享包。
- 安装器使用 pending 目录发布共享包，清单版本和声明文件完整时直接复用。
- 共享包就绪后清理旧资源目录中的重复 `ai-dev-pack`，新用户只保留一份，旧用户在下一次 bundled 资源操作时迁移。

下一步：目标单测、构建后在 OnePlus 8T 触发一个快速 bundled 资源操作，核对共享路径和实际回收空间。

首次真机结果：

- `.kf/cache` 从约 1,040,587KB 降到 226,551KB，旧 `resources/*/ai-dev-pack` 目录归零，已回收约 795MB。
- 共享包可用，Node 安装脚本 `SUMMARY PASS=67 WARN=8 FAIL=0`，Node 二进制存在，应用无崩溃。
- 发现升级遗留的展开 `.tar` 仍在内部 pack，与新 `.tgz` 同时复制，额外占约 134MB。
- 补充按 manifest 清理未声明 package 文件；共享包完整性检查同时拒绝多余文件，确保升级用户也收敛到一份精确内容。

下一步：再次执行 Node staging，确认共享缓存约 91MB 且 `.tar` 遗留消失。

第二次真机结果：

- `.kf/cache` 为 89,439KB，共享 `ai-dev-pack` 为 89,407KB；内部 runtime pack 同为 89,407KB。
- 共享 packages 只保留 manifest 声明的五个文件，旧 `.tar` 与所有资源私有 pack 均消失。
- 应用内部数据从约 3,078,095KB 降到 2,061,377KB，合计回收约 993MB。
- Node 安装仍为 `SUMMARY PASS=67 WARN=8 FAIL=0`，应用 PID 存活且无崩溃。
- 内部 pack 增加完整性复用；需要更新时先写 pending 目录、校验完整后再替换，避免每次资源操作重复复制 89MB。

下一步：验证第二次 staging 复用耗时，再完成最终全量验收和提交。

最终验证：

- 第三次 Node 资源动作中，内部包和共享包的 Node 归档 mtime 分别保持 `1783835299`、`1783835292`，确认完整包直接复用，没有重复复制约 89MB。
- 持久日志记录 Node、Python、uv、Git、curl 和工具环境均使用 `/workspace/.kf/cache/shared/ai-dev-pack`，`reclaimedLegacyBytes=0`。
- 第三次工具链结果仍为 `SUMMARY PASS=67 WARN=8 FAIL=0`；应用 PID 保持 `11940`，未发生崩溃或 ANR。
- `.kf/cache` 保持 89,439KB，内部包与共享包均为 89,407KB，没有重新生成资源私有副本。
- 全量 Debug 单测、Debug APK 构建、运行车道静态检查、`git diff --check` 和 APK 体积审计全部通过。
- Debug APK 为 241,594,058 bytes，压缩 assets 为 183,990,598 bytes，满足 243,269,632 bytes 预算。

共享工具链缓存状态：completed。

## 第二阶段架构迁移计划

计划结论：采用模块化单体、单向状态流和平台适配器。先在现有 `:app` 内完成真实职责迁移，
包级依赖稳定后再判断是否值得拆 Gradle 模块。

当前结构证据：

- `MainActivity` 仍约两万行，并实现资源、原始 JSON 和终端等七组宿主接口。
- 四个资源 Fragment 仍将真实渲染反向委托给 Activity。
- `ScreenRouter` 依赖 `MainActivity.Screen`，`CardRunActivity` 继承完整 `MainActivity`。
- 资源相关渲染、动作、安装和刷新仍有至少 64 个入口位于 Activity。

执行计划已经写入 `PLAYBOOK.md` 的 T001-T012；架构选择和逻辑修复顺序记录为
ADR-S012、ADR-S013。

下一步：只启动 T001，先建立六条业务链目的矩阵、依赖护栏和 MainActivity 迁移台账，
不直接开始资源页面搬迁。

### T001 三问

- 目标是什么：建立第二阶段真实架构基线、六条业务目的矩阵和依赖护栏；不迁移页面。
- 完成标准是什么：对应 `PLAYBOOK.md` T001 四项验收，必须有自动检查、全量测试和构建证据。
- 依赖是否满足：D1-D5 均已完成并提交；第二阶段方案提交为 `416e2d9`，依赖满足。

已完成盘点：

- `MainActivity` 物理行数 21,144，成员函数 854，私有字段 171，实现 8 个 Host/Provider 接口。
- 资源反向渲染委托 4 个，Activity 内资源职责函数 64 个。
- `ScreenRouter` 引用 `MainActivity.Screen` 46 次；1 个 Activity 继承完整 `MainActivity`。
- `runtimeStates` 在 Activity 内仍有 64 处引用，是首页/运行投影迁移需要消除的平行缓存债务。
- 六条业务链目的、事实源、动作、生命周期、失败语义和当前债务已写入 `ARCHITECTURE_BASELINE.md`。

下一步：实现目标包依赖规则和历史债务防回涨脚本，并接入现有静态检查入口。

T001 验证：

- `ARCHITECTURE_BASELINE.md` 已覆盖首页、资源、运行、终端、Web、设置六条业务链的目的、事实、动作、生命周期、失败语义和当前债务。
- `architecture-baseline.json` 保存九项机器债务基线；`KITE_ARCHITECTURE_CHECKS.ps1` 要求这些值只能下降。
- 新目标包依赖规则已覆盖 Shell、Feature、Application、Domain、Platform；Feature 跨模块直连和反向 Activity Host 委托被禁止。
- 反向探针让 Domain 引用 `android.view.View` 后，检查按预期失败；删除探针后恢复通过。
- 架构检查已接入 `KITE_RUNTIME_LANE_STATIC_CHECKS.ps1`。
- 全量 Debug 单测和 Debug 构建通过；运行车道与架构检查通过；APK 体积审计保持 241,594,058 bytes。

T001 状态：completed。

下一步：提交 T001，执行 T002 三问，只迁移应用壳合同和组合根。

### T002 三问

- 目标是什么：让导航、系统 Intent 分类和长期依赖装配脱离具体 `MainActivity` 类型，保持现有业务和视觉不变。
- 完成标准是什么：对应 `PLAYBOOK.md` T002 四项；导航类型独立、Intent 先分类、长期依赖由组合根创建，并通过真机返回与回跳冒烟。
- 依赖是否满足：T001 已完成并提交为 `c3b636f`，依赖满足。

当前证据：

- `ScreenRouter` 对 `MainActivity.Screen` 有 46 次引用，导航合同无法脱离具体 Activity 编译。
- `MainActivity.onCreate` 直接创建 diagnostics、配方/投放区加载器、bridge、浏览器会话、资源 Store 等长期对象。
- `onCreate` 与 `onNewIntent` 分别按认证、自动化、运行窗口顺序手写同一套分发优先级。

执行顺序：先迁移 `AppDestination`/`AppNavigator` 并跑导航回归；再建立 Intent 分类和组合根，避免同时改变三条入口。

T002 结果：

- `AppDestination`、返回与恢复策略迁入 `shell/AppNavigator`，导航合同对 `MainActivity.Screen` 的引用由 46 降到 0。
- `AppIntentRouter` 统一首次启动和 `onNewIntent` 的优先级：官方认证回跳 -> 自动化动作 -> CardRun。
- `KiteAppGraph` 使用 application context 统一提供 diagnostics、bridge、认证/自动化会话、资源 Store 和 ManifestLoader；页面可变的 RecipeLoader、DropZoneManager 仍按使用方创建。
- 架构护栏曾阻止在 `MainActivity` 新增三个测试/分发函数，最终改为 Router 外部分发，成员函数保持 854，没有通过放宽基线绕过。
- 导航、Intent 和组合根目标测试、全量 Debug 单测、Debug 构建、静态检查与 APK 体积审计通过。
- OnePlus 8T 冷启动 1769ms；自动化与认证 Intent 均复用 PID `16748`，认证回跳命中认证分支，没有误进 CardRun。
- OnePlus 8T 设置页进入与系统返回回到首页通过，未发现崩溃或 ANR。

T002 状态：completed。

下一步：提交 T002 收口与机器基线，再执行 T003 三问，先建立资源 Feature 状态合同，不直接搬 UI。

### T003 三问

- 目标是什么：建立资源目录、详情、搜索、管理和安装向导共用的状态、动作、Effect 与依赖合同，不迁移视图。
- 完成标准是什么：对应 `PLAYBOOK.md` T003 四项；事实继续来自现有 Store/Projector，Controller 不持有 View 或导航，并覆盖完整资源状态转换。
- 依赖是否满足：T002 已完成，导航、Intent 与组合根收口提交为 `5c439ff`、`d9bbee0`、`6cd5323`，依赖满足。

当前事实：

- `KiteResourceInstallStore` 已提供 registry snapshot、plan snapshot 和轻量 signals。
- `KiteResourceRuntimeFactsProjector` 已能合并 registry 与计划事实，`KiteResourceUiProjector` 已统一主/次动作标签。
- `KiteResourceActionCoordinator` 已把显示标签映射为稳定动作意图，但页面仍自行决定何时调用和如何执行。
- `ResourceItem` 仍是 Activity 私有显示模型；T003 只建立可供各页面连接的状态合同，完整内容模型在 T004 迁移。

下一步：新增 `feature.resources` 合同和纯 Controller，用 fake gateway 验证准备、安装、失败、运行、停止、卸载和取消语义。

T003 结果：

- 新增统一 `ResourceFeatureUiState`、`ResourceFeatureAction`、`ResourceFeatureEffect` 和 `ResourceFeatureGateway`。
- 每个资源 UiState 同时携带确定 phase、现有 UI projection、主动作意图、次动作意图和 registry 诊断摘要。
- 安装向导步骤继续复用 `KiteResourceInstallStepUiProjector`；目录/详情状态继续复用 RuntimeFacts 与 UiProjector。
- Controller 只读取 Gateway 快照并返回 ActionRequest，不调用 `markInstalling`、`markFailed`、`beginPlan` 等事实写入口。
- Mutex 保证刷新、事实校准和动作请求串行，避免旧目录结果覆盖新事实。
- 测试覆盖未获取、准备、安装、等待终端、运行、停止中、卸载、安装失败、卸载失败、取消、目录失败和事实校准。
- 全量 Debug 单测、Debug 构建、运行车道/架构检查和 APK 体积审计通过；MainActivity 机器债务没有增长。

T003 状态：completed。

下一步：提交 T003，进入 T004；按目录、搜索、详情、管理、安装向导顺序迁移视图所有权。

### T004 三问

- 目标是什么：让资源目录、搜索、详情、管理和安装向导真正拥有自己的视图、绑定和局部更新，删除四个 Activity 渲染 Host。
- 完成标准是什么：对应 `PLAYBOOK.md` T004 五项；每个页面单独迁移、测试、真机验证，滚动、返回、安装状态和失败重试不回归。
- 依赖是否满足：T003 已完成并提交为 `9eda767`，统一资源状态合同可供页面接入，依赖满足。

迁移策略：

- Shell 在 Fragment 外托管全局底栏；资源 Feature 只渲染页面内容并通过 Effect 请求导航/动作。
- Gateway 合同下沉到 Application，Android/manifest/Store 适配器放到 Platform；Fragment 不导入 AppGraph、MainActivity 或 Platform 实现。
- 先完成资源目录与搜索，证明数据、主题、导航和状态更新链；再迁详情、管理和安装向导。
- 旧 Activity 方法在对应页面真机验收后立即删除，不保留两套活跃渲染。

T004 基础接线：

- `ResourceFeatureGateway` 从 Feature 实现细节上移为 Application 合同，Android manifest、安装 Store、运行 Store 和 Node 工作区探测由 Platform 适配器组合。
- `KFApplication` 只暴露 Application 合同，Fragment 无需导入 `KiteAppGraph` 或 Platform 实现。
- 安装 Store 与 `CardRunStore` 的变化合并为 `ResourceFeatureChange`；页面收到事件后只调用 Controller 重投影已有目录，不重新读取 manifest、不轮询，也不把事实复制进页面。
- `ResourceFeatureControllerTest`、Debug Kotlin 编译和架构依赖检查通过；MainActivity 债务指标未增长。

下一步：迁移资源目录 Fragment 的视图、绑定、滚动与分类状态，接入轻量变化流并删除 `ResourcesHost`。

T004 资源目录结果：

- `ResourcesFragment` 已迁入 `feature.resources`，直接拥有 `ResourceCatalogScreen`；分类、滚动位置、结构签名、渐进分批绑定和局部按钮绑定不再位于 Activity。
- 目录点击通过 Fragment Result 发出搜索、管理、详情和动作 Effect；Shell 只提供 Feature 容器、全局底栏和 Effect 落点，不再把 View 反向渲染进 Fragment。
- 安装、运行和工具链事实通过 Gateway 变化流进入 Controller；普通事实变化复用原行和按钮，只有目录结构或分类变化才重建分节。
- 删除 `ResourcesHost`、旧目录页缓存、请求序号、整页刷新、滚动导航动画、海报/分类/货架 View 工厂及失联模型，共从 `MainActivity` 清理约 670 行净代码。
- Robolectric 验证同一目录结构下“获取 -> 打开”复用原按钮，动作受理立即显示“准备中”；全量 284 项 Debug 单测通过，Debug APK 和运行车道静态检查通过。
- OnePlus 8T `3f8bbaad` 冷启动为 1773ms；资源目录、海报、分类、资源状态、搜索入口和 Shell 底栏均可见，搜索 Effect 与返回链可达；清空启动日志后二次进入未出现掉帧、崩溃或 ANR。
- 架构债务从 `21133 / 854 / 171 / 8 / 4 / 64` 降为 `20457 / 829 / 159 / 7 / 3 / 55`（行 / 函数 / 字段 / Host / 资源渲染委托 / 资源函数）。

资源目录状态：completed，待独立提交。

下一步：迁移资源搜索页，复用同一 Controller、展示投影、图标仓库和行绑定，删除 `ResourceSearchHost` 及 Activity 搜索请求/渲染状态。

T004 资源搜索结果：

- 新增 `ResourceFeatureFragment`，统一资源页面的 Controller、Gateway 变化流、刷新和主/次动作 Effect 生命周期；具体页面仍独立拥有 View 与页面状态。
- `ResourceSearchFragment` 与 `ResourceSearchScreen` 直接拥有查询、输入法、滚动位置、过滤结果、渐进绑定和动作承诺状态。
- 搜索只过滤 Controller 已加载的内存目录；安装/运行事实变化只重绑当前结果按钮，不再启动 Activity 搜索线程或重读目录。
- Shell 继续拥有 Feature 容器，但搜索 Destination 不挂载全局底栏，避免软键盘弹出后挤占结果空间；顶部返回立即回到资源目录。
- 删除 `ResourceSearchHost`、Activity 搜索字段、请求序号、搜索算法、空态、列表分批渲染与刷新分支。
- Robolectric 验证查询过滤、清空恢复、原按钮局部重绑和返回 Effect；OnePlus 8T 实测 `Codex` 过滤得到 Codex CLI/VS Code，输入法与顶部返回正常，未见掉帧、崩溃或 ANR。
- 架构债务进一步降为 `20144 / 817 / 155 / 6 / 2 / 52`（行 / 函数 / 字段 / Host / 资源渲染委托 / 资源函数）。

资源搜索状态：completed，待独立提交。

下一步：迁移资源详情页；把详情内容、媒体、操作区、推荐与局部状态绑定移入 Feature，并删除 `ResourceDetailHost`。

T004 资源详情结果：

- `ResourceDetailFragment` 与 `ResourceDetailScreen` 直接拥有详情结构、媒体、推荐、来源、执行预览、依赖要求、滚动位置和动作绑定。
- manifest 描述变化才按静态签名重建内容；安装、运行、失败和次动作变化只重绑原按钮与状态行，不再由 Activity 开线程、轮询目录或重建详情页。
- 返回、更多、原始 JSON、推荐资源和主次动作都通过 Feature Result 上交 Shell；页面不直接调用 Activity 导航或执行函数。
- 删除 `ResourceDetailHost`、Activity 详情请求序号、页面缓存、媒体缓存、渲染绑定及全部详情 View 工厂，`MainActivity` 净减少 857 行。
- Robolectric 验证“获取 -> 打开”复用原按钮、停止动作立即进入“停止中”，以及返回、更多、原始 JSON 事件完整上交。
- 全量 Debug 单测、Debug APK 构建、架构检查与运行车道静态检查通过；旧静态检查已改为验证 Controller 中的稳定次动作意图和共享动作请求。
- OnePlus 8T `3f8bbaad` 覆盖安装与 1714ms 冷启动通过；Codex CLI 详情、更多操作返回详情、详情返回目录均正常，截图无重叠，日志未见崩溃、ANR 或掉帧告警。
- 架构债务进一步降为 `19287 / 775 / 148 / 5 / 1 / 42`（行 / 函数 / 字段 / Host / 资源渲染委托 / 资源函数）。

资源详情状态：completed，待独立提交。

下一步：迁移资源管理页；让队列、已安装列表和局部状态绑定归 Feature，删除最后一个资源渲染 Host。

T004 资源管理结果：

- `ResourceManageFragment` 与 `ResourceManageScreen` 直接拥有执行队列、已获取列表、滚动位置和动作承诺状态；首次加载明确显示校准中，不再把未加载误报为空队列。
- 队列结构不变时复用原卡片并只更新进度、状态和色调；已获取集合不变时复用原行与按钮，安装、运行和停止事实由 Gateway 变化流直接重投影。
- 打开安装向导和上滑取消计划新增为计划级 Fragment Result；Shell 继续执行既有向导与取消逻辑，页面不读取 Store、不操作 Activity。
- 删除 `ResourceManageHost`、管理页刷新线程、Payload/Binding、Activity 资源行绑定、250ms 收敛检查和旧刷新目标测试；资源反向渲染委托降为 0。
- 真机发现并修复“资源管理 -> 详情 -> 返回”错误落到资源目录：`ResourceDetail` 改用上下文返回，缺失上下文时仍安全回到资源目录。
- Robolectric 覆盖加载态、队列局部更新、已获取行复用、动作即时承诺、打开/取消计划 Result 和管理详情返回；全量 Debug 单测、Debug APK、架构与运行车道检查通过。
- OnePlus 8T `3f8bbaad` 上资源管理、已获取列表、详情往返和底栏正常；清空启动日志后的页面链未见崩溃、ANR 或掉帧告警。
- 独立冷启动为 1605ms-1667ms，但系统重复记录一次 `Skipped 72 frames` 和约 1152ms Activity 主线程消息；该证据登记到 T012 启动性能基线，不在管理 Feature 尚未创建时归因给本页面。
- 架构债务进一步降为 `18645 / 752 / 142 / 4 / 0 / 38`（行 / 函数 / 字段 / Host / 资源渲染委托 / 资源函数）。

资源管理状态：completed，待独立提交。

下一步：迁移资源安装向导；保留既有执行核心和 CardRun 生命周期，只转移向导 UiState、步骤绑定和计划动作所有权。

T004 资源安装向导结果：

- `ResourceInstallWizardScreen` 与 `ResourceInstallWizardSurface` 已拥有向导标题、进度、计划按钮、步骤行、动态时长和局部重绑；CardRun 只挂载 Feature Surface，不再渲染向导 View。
- Application Gateway 新增中立的安装/卸载运行快照，Controller 将明确 `instanceId`、operation、status、surface 和时间投影到同一资源 UiState；报告、终端和网页打开请求不再重新猜测“最新运行”。
- 删除 Activity 内 `ResourceInstallWizardBinding`、行 Binding、UiState、请求序号、post/thread 刷新链和全部向导 View 工厂；安装执行、计划推进、失败卸载和 CardRun 生命周期仍由原 Shell/Store 拥有。
- 点击“开始获取”立即锁定为“准备中”；Store 与 CardRun 后续只重绑标题、按钮、步骤和时长，不重建 CardRun 页面。
- 真机发现资源首页在后台错过安装完成信号后仍保留“准备中”；`ResourceFeatureFragment` 现于每次进入 `STARTED` 时重新校准事实，非重放信号只用于实时加速，不再承担恢复正确性的责任。
- Robolectric 覆盖计划动作即时承诺、原步骤 View 复用、失败卸载、完成队列保留和指定实例的报告/终端打开；全量 Debug 单测、Debug APK、架构与运行车道检查通过。
- OnePlus 8T `3f8bbaad` 实测 OpenCode“获取 -> 准备中 -> 向导 -> 获取中计时 -> 完成 -> 报告 -> 返回向导 -> 完成 -> 首页打开”全链；第二轮完成返回后 250ms 即显示“打开”，未再出现旧“准备中”，日志无崩溃、ANR 或掉帧告警。
- 测试结束后已卸载 OpenCode，设备恢复测试前状态；覆盖安装后的独立冷启动为 1660ms。
- 架构债务进一步降为 `17820 / 733 / 141 / 4 / 0 / 36`（行 / 函数 / 字段 / Host / 资源渲染委托 / 资源函数）。

T004 状态：completed。目录、搜索、详情、管理和安装向导均已迁移并分段提交。

下一步：进入 T005；先建立首页卡片与配方编辑目的矩阵，盘点 Loader/Store、草稿、分组、图标、步骤编辑和运行动作的真实所有权。

### T005 三问

- 目标是什么：把首页卡片投影与配方编辑拆成两个 Feature；首页只组合配方、分组和运行事实并提交动作，编辑器只拥有草稿、校验和配置写入。
- 完成标准是什么：对应 `PLAYBOOK.md` T005 四项；首页运行变化局部更新，编辑草稿可恢复且未保存返回可解释，两处启动产生同一动作计划，旧 Activity 字段与渲染链删除。
- 依赖是否满足：T002 动作合同与 T004 资源页面迁移均已完成；`KiteRecipeActionCoordinator`、`CardRunStore`、`KiteRecipeLoader` 与分组 Store 的真实边界已经核对，依赖满足。

真实所有权矩阵：

- 配方文件事实归 `KiteRecipeLoader`，卡片分组归 `KiteCardGroupStore`，运行事实归 `CardRunStore`。
- 首页只读取三类事实，复用 `KiteCardRunUiProjector`，并提交 `KiteRecipeActionRequest`；不得执行配方步骤或写运行事实。
- 编辑器后续通过同一 Gateway 保存/删除配方与创建分组；不得直接创建、停止或选择运行实例。
- Shell 只落地导航、文件选择、动作执行和系统能力 Effect，不保存 Feature 草稿或复制卡片运行状态。

T005 合同层结果：

- 新增进程级 `RecipeFeatureGateway`，统一 Loader、分组 Store 与运行快照的读取边界，并将配置变化和运行变化暴露为轻量信号。
- `KiteAppGraph` 现在只创建一份 `KiteRecipeLoader` 与 `KiteCardGroupStore`；首页和编辑器不再各自形成事实缓存。
- 新增纯 `HomeFeatureController`，统一目录、分组、运行投影、Ubuntu 环境阻塞和首页主动作 Effect，不引用 Android View、导航或执行器。
- 定向测试覆盖统一状态、纯网页卡片不被 Ubuntu 阻塞、稳定动作请求、运行校准不重读目录和目录失败保留旧卡片。
- 机器护栏锁定首页 Controller 的依赖边界，以及 Loader/分组 Store 的进程唯一性。
- 全量 300 项 Debug 单测（1 项既有跳过）、Debug APK 构建与运行车道静态检查通过；`MainActivity` 债务基线未增长。

下一步：提交 T005 合同层；随后迁移首页卡片、分组、滚动与运行局部绑定，删除 Activity 内对应状态和渲染链。

T005 首页迁移结果：

- `HomeFragment` 与 `HomeScreen` 已拥有全部/已打开/已停止/自定义分组、滚动、下拉导入、卡片结构、运行状态、计时与即时动作承诺。
- 首页只消费 `RecipeFeatureGateway` 与 `HomeFeatureController`；卡片主动作通过 Fragment Result 把标准 `KiteRecipeActionRequest` 交给 Shell，页面不执行步骤、不创建或停止运行实例。
- 配方目录/分组变化才重建分页或网格；`CardRunStore` 变化经 Gateway 重投影后只重绑当前可见卡片的徽章、步骤、按钮和计时。
- 运行环境状态变化只更新 Shell 的状态胶囊、提示条和 Home 的阻塞投影，不再调用 `showConsole()` 重建首页。
- 删除 Activity 内首页分页、网格、卡片 Binding、状态徽章、计时器、运行刷新节流和旧数据类，机器债务从 `17820 / 733 / 141` 降为 `17277 / 696 / 139`（行 / 函数 / 字段）。
- Robolectric 覆盖原按钮复用、动作即时锁定、自定义分组、外部导入和回前台复用同一 Home Fragment/View；全量 Debug 单测与 Debug APK 构建通过。
- OnePlus 8T `3f8bbaad` 冷启动 1525ms；分页空态、卡片编辑往返、后台热恢复 139ms 均正常。
- 真机动作链确认“启动 -> CardRun -> 首页停止/运行计时 -> 停止 -> 首页启动/上次刚刚”自动同步；logcat 无崩溃、ANR 或掉帧。

首页迁移状态：completed，待独立提交。

下一步：迁移配方编辑器；先建立草稿状态与校验合同，再按表单、图标、步骤、分组、保存/删除和未保存返回顺序转移所有权。

T005 编辑器合同层结果：

- 新增 `RecipeEditorDraft` 与 `RecipeEditorStepDraft`，名称、描述、图标、分组、启动选项、快捷方式请求和有序步骤只有一份草稿事实。
- `RecipeEditorController` 统一初始化、脏状态、模板、增删改序步骤、校验、分组创建、保存、删除、草稿持久化和运行校准，不持有 View、Context、导航或具体 Store。
- 保存与删除只经过 `RecipeFeatureGateway`；旧 `recipe_draft` 与保存时间键由 Platform 适配器兼容读取，Activity 不需要再拥有草稿存储。
- 编辑页启动请求与首页启动请求现在产生相同动作计划；独立运行页策略只由卡片 `launch.openInstance` 决定，不再由页面来源暗中改变。
- 测试覆盖已有/新建初始化、缺名与缺步骤字段校验、输入归一化、分组保存、草稿 JSON 往返以及首页/编辑器动作计划一致性。

下一步：提交编辑器合同层；随后让 `RecipeEditorFragment/Screen` 接管真实表单、图标选择、步骤弹窗、分组、未保存返回与保存/删除 Effect。

T005 配方编辑器迁移结果：

- `RecipeEditorFragment` 与 `RecipeEditorScreen` 已拥有名称、说明、图标、分组、模板、有序步骤、启动选项、快捷方式请求、运行按钮、未保存返回和页面恢复；Activity 不再读取输入框或保存表单字段。
- 系统相册仍通过 Activity Result API 打开，但选择结果、拖动/双指裁剪、512px PNG、头像集和当前图标都在编辑 Feature 与 `RecipeFeatureGateway` 内闭环；旧 `onActivityResult` 和 Activity 裁剪状态已删除。
- 保存、删除、原始 JSON、运行历史、快捷方式和运行请求通过 `RecipeEditorResultContract` 上交 Shell。首页与编辑器启动继续产生同一 `KiteRecipeActionRequest`，页面不直接执行或停止配方。
- 删除恢复旧安全语义：Gateway 先检查真实 `CardRunState`；活跃实例只产生标准停止请求，不在同一次点击删除；停止后才删除配置，只清理已闭合运行事实，并由 Shell 清理对应 PID 目录。
- 真机首次发现“保存后首页仍是旧卡片”：原因是首页 detached 时错过无重放目录信号。Gateway 现先更新进程目录快照再发一条可重放变更；首页重新挂载后即时消费快照，不依赖二次点击、延时刷新或轮询。
- 删除旧表单、更多页、草稿模型、图标选择、步骤弹窗、未保存弹窗与相关字段后，`MainActivity` 从 `17277 / 696 / 139` 降至 `15055 / 610 / 111`（行 / 函数 / 字段），旧编辑器符号为 0。
- Robolectric 新增首次新建空标识、未运行历史语义、步骤移动，以及活跃删除先停、闭合删除清理实例测试；全量 315 项 Debug 单测（1 项既有跳过）、Debug APK、架构与运行车道检查通过。
- OnePlus 8T `3f8bbaad` 覆盖新建、字段校验、步骤编辑、保存后首页即时出现、再次编辑即时改名、JSON/历史往返保留草稿、强杀恢复、放弃修改、删除后首页即时回落和头像选择面；测试卡已从共享目录清理，日志无崩溃或 ANR。冷启动为 1530ms-1668ms。

T005 状态：completed。首页与配方编辑已经成为两个独立 Feature，旧 Activity 所有权已删除。

下一步：进入 T006；先盘点开始、步骤推进、停止和结果回写的真实执行入口，再建立与 View 无关的运行编排合同。

## T006 运行编排与执行引擎

### 三问自检

目标是什么？按 `PLAYBOOK.md` 的 T006，将配方开始、步骤分派、继续、停止和结果解释从页面所有权迁到与 Android View 无关的运行编排层；`CardRunStore` 继续是运行事实唯一拥有者。

完成标准是什么？shell、terminal、Web、X11 与 Android action 通过统一执行接口；开始、继续、停止、取消、失败及残留确认不依赖页面可见性；同一 `instanceId` 不产生重复执行链；执行层不引用 Activity、Fragment、View、Toast 或导航。

依赖是否满足？T005 已完成并提交为 `d0f8bfb`；首页和编辑器只提交动作计划，不再直接执行步骤，T006 可以开始。

### 动作运行车道审计

```text
现象：运行事实已经集中，但执行链仍由 MainActivity 维持，页面销毁或不可见会影响终端续跑和结果处理。
用户动作：开始、继续、完成当前步骤、停止、取消。
参与模块：KiteRecipeActionCoordinator、MainActivity、CardRunStore、KiteBridgeClient、TerminalRuntimeHost、Web/X11 适配器。
主车道：Orchestrator（步骤顺序与结果推进）。
次车道：Run Instance、Runtime Prep、Runtime Surface、Execution Core。
状态拥有者：CardRunStore。
必须保留的伙伴机制：编排器写运行事实，执行适配器回传结构化事件，Shell 只消费 Effect 并绑定可见显示面。
断裂点：startRecipe、executeRecipeStep、pendingTerminalFlow、stopRecipeByCardInstanceId、handleStopResultV2 和各步骤结果解释仍在 MainActivity。
禁止层：不让 View 直接执行 PRoot/终端，不把运行事实复制到 Feature，不让执行核心调用页面导航或 Toast。
所需证据：纯编排单测、架构静态护栏、全量单测/构建，以及 OnePlus 8T 开始/继续/停止真机链。
```

首个迁移段：先建立 `RunOrchestrator`、`RecipeExecutor` 与 `StopCoordinator` 的无 Android 合同，用测试锁定步骤分派、实例幂等、终端等待恢复、停止策略和迟到结果门禁；随后再逐段替换 Activity 入口。

T006 合同层结果：

- 新增 `RunStateGateway`、`RecipeExecutor`、结构化 `RecipeExecutionEvent` 和停止结果合同；Application 层不引用 Android、View、Shell、Bridge 或具体 Platform。
- `RunOrchestrator` 以 `instanceId + createdAt` 锁定执行代次，同一实例同一步骤只保留一个 flight；迟到回调、停止后回调和旧代次回调不能覆盖当前事实。
- `StopCoordinator` 将本地网页闭合、终端/进程停止、确认成功、人工 kill、超时和残留进程解释收成纯规则；只有确认无残留才落 `Stopped`。
- `AndroidRunStateGateway` 只适配现有 `CardRunStore`，没有建立第二份运行状态。
- 新增 6 项纯单测，覆盖五类步骤统一分派、重复启动、跨编排器终端续跑、停止后迟到结果、残留进程和本地网页闭合；全量单测、Debug 构建及运行车道静态检查通过。

下一步：把进程级编排器装配到 `KiteAppGraph`，提取真实步骤执行适配器；先迁移开始和步骤推进，再迁移停止，不在同一改动中切换全部入口。

T006 开始与步骤推进迁移结果：

- `KiteAppGraph` 进程级装配唯一 `RunOrchestrator`、`AndroidRunStateGateway`、`AndroidRecipeExecutor` 与一次性 Effect 总线；Activity 只接收动作计划、绑定可见显示面，不再决定普通配方步骤如何执行。
- shell、terminal、Web、X11 和 Android action 已通过同一 `RecipeExecutor` 端口分派；执行结果只以携带 `instanceId + createdAt + stepIndex` 的结构化事件回写，跨编排器恢复与迟到结果均经过当前事实校验。
- 普通首页/编辑器配方已切到新开始链；资源安装运行暂留兼容入口，原因是资源登记成功、失败回滚和卸载续接仍由旧资源生命周期回调拥有，不能在事实回写尚未迁移时硬切。
- Terminal 步骤先创建、暂存并打开进程级会话，再投递命令；显示面是否已挂载不再决定命令是否执行。等待用户确认期间执行 flight 保留，确认完成后无论是否还有下一步都结束终端会话。
- Web 可见步骤将 URL 先写入 `CardRunStore` 再发送一次性打开 Effect；页面错过 Effect 时仍可从运行事实恢复，不把导航事件当作持久事实。
- Robolectric 覆盖可见 Web 等待与 Effect、静默 Web 自动完成、未知步骤结构化失败；编排测试补齐旧等待回调不得覆盖新步骤。全量 Debug 单测、Debug APK、架构与运行车道检查通过。
- OnePlus 8T `3f8bbaad` 冷启动 1581ms；OpenClaw 首次启动的终端命令只出现一次，状态进入 `WaitingTerminal`。点击“继续”后摘要立即变为“已完成”，对应 `proot` 与 `bash --login` 子进程均从进程表消失，logcat 无 `AndroidRuntime` 崩溃。

开始与步骤推进迁移状态：completed，待独立提交。

下一步：迁移普通配方停止、取消、失败和残留进程确认；让 `StopCoordinator` 成为唯一停止结果解释器，再迁移资源安装运行的事实回写。

T006 停止与取消迁移结果：

- 普通卡片、资源打开实例和自动化停止入口已统一提交到进程级 `RunOrchestrator.stop(instanceId)`；Activity 只负责即时页面反馈和任务窗口关闭，不再直接调用 Bridge、终端或写停止事实。
- 资源安装/卸载运行仍明确留在 `legacyStopRecipeByCardInstanceId`，直到资源登记、失败回滚和卸载续接的事实回写迁走；兼容边界由机器护栏锁定，不能扩散到普通运行。
- `StopResolved` 一次性 Effect 只负责把成功/失败提示交给当前前台 Shell；`Stopped/Running/lastError` 等可恢复事实仍只写 `CardRunStore`。
- 等待步骤完成改为先撤销旧 execution flight、清除终端/Web 显示绑定，再关闭执行资源；终端结束事件即使在关闭过程中迟到，也不能重复分派下一步或让页面按旧 session 重建 shell。
- 停止合同区分终端会话 ID 与 Bridge 运行 ID：当 `runId == terminalSessionId` 且没有 PID/进程组/系统会话时，只结束终端，不向 Bridge 误发 `stop-run`。
- Bridge 强杀可能因目标进程被杀而返回非零，但只要同一次停止响应提供明确的 `__kite_stop_remaining:` 空审计，`StopCoordinator` 仍确认停止；非空 PID 列表始终恢复原状态并显示残留错误。
- 单测新增完成竞态、终端会话 ID 归类、无绑定启动取消、强杀非零但空残留确认和 Stop Effect 覆盖；全量 Debug 单测、Debug APK、架构与运行车道检查通过。
- OnePlus 8T `3f8bbaad` 终端停止验证：目标 session `shell-space-main-1783865366865` 的 PID `8138` 退出、残留 0，Kite 宿主 PID 保持不变，首页恢复“启动”。
- OnePlus 临时 Bridge 卡验证：`sleep 300` 的 PRoot/bash/sleep 链 `11017/11020/11024` 全部退出，Kite PID `10377` 保持不变；Store 最终为 `Stopped`，所有 run/PID/PGID/SID 绑定清空，摘要为“已停止，未发现进程残留”。临时卡已从共享目录删除，首页恢复仅 OpenClaw 一张卡。

停止与取消迁移状态：completed，待独立提交。

下一步：迁移资源安装运行的成功、失败、回滚、取消和登记回写，让资源运行也进入同一编排器；完成后删除 Activity 内旧步骤执行与停止链。

T006 资源运行生命周期迁移结果：

- 新增进程内 `RunLifecycleEventHub`；每次 `RunOrchestrator` 完成 `CardRunStore` 写入后才通知订阅者。Hub 不保存状态、不重放页面事件，也不读取 Store，因此不是第二份运行事实。
- `ResourceRunCoordinator` 进程级拥有资源准备、有限运行启动、成功/失败/取消结算、安装计划推进和卸载续接；它只依赖 Gateway、RunOrchestrator 与事实事件，不引用 Android、Activity、View、导航或具体 CardRunStore。
- `AndroidResourceRunGateway` 适配资源登记、已安装快照、本地包准备和 CardRun 状态；`AndroidResourceRecipeFactory` 直接从 manifest actions 编译安装/卸载配方，并保留既有 bundled/legacy 兼容命令。
- 资源入口不再启动 Activity 线程执行 `ToolchainPackInstaller` 或调用 `startRecipe`；页面提交 `ResourceRunLaunchRequest` 后只决定是否打开运行窗口或留在安装向导。
- 有限资源配方的最终状态由运行编排器确定为 `Completed`，并清除已退出命令留下的 run/PID/PGID/SID 绑定；注册机结算不再由 Activity 的 shell 回调猜测。
- 安装完成先登记资源和保存清单快照，再推进 plan；有下一依赖时由进程协调器直接编译并启动，页面不可见不会中断队列。失败、Bridge 不可用和用户停止分别写入确定失败/取消原因并阻断当前安装步骤。
- 纯测试覆盖安装登记与下一资源自动启动、失败阻断、卸载后重新获取、重复终态幂等，以及 OpenCode/Node.js 清单配方编译；全量 Debug 单测与 Debug APK 通过。
- OnePlus 8T `3f8bbaad` 实测 OpenCode：点击开始后立即把 Kite 退到后台，66MB 获取仍自行完成；回前台资源首页直接显示“打开”，`/workspace/.kf/bin/opencode` 链接存在，安装 CardRun 为 `Completed`。
- 同机卸载约 2 秒完成，详情按钮立即恢复“获取”，命令链接消失，卸载 CardRun 为 `Completed`；全链 logcat 无 `AndroidRuntime` 崩溃。测试结束设备恢复为 OpenCode 未获取状态。

资源运行生命周期迁移状态：completed，待独立提交。

下一步：删除 Activity 中已无产品入口的 legacyStartRecipe、executeRecipeStep、旧 shell/terminal/X11/Android 分派、旧资源成功失败结算和 legacy stop；随后收紧机器护栏并做 T006 最终真机回归。

T006 最终收口结果：

- `MainActivity` 已删除 `legacyStartRecipe`、`executeRecipeStep`、旧 shell/terminal/X11/Android 分派、旧 Bridge 结果解释、`legacyStopRecipeByCardInstanceId`、旧资源成功失败结算和终端页面观察器；开始、继续、停止、取消与资源结算不再存在第二套页面执行引擎。
- 资源安装/卸载配方只由 `AndroidResourceRecipeFactory` 从 manifest 编译；Activity 仅向 `ResourceRunCoordinator` 请求配方并提交运行，不再复制清单命令、legacy fallback 或资源登记逻辑。
- `KiteActionRoute` 删除无法由当前路由生成的 `OpenWeb/NativeAction` 直达旁路；网页和 Android action 与 shell、terminal、X11 一样，只能作为配方步骤进入 `RunOrchestrator`。
- 配方终端改用不写入普通终端列表的 embedded session。终止 embedded 会话时禁止唤醒普通终端 fallback；定向命令在 holder 尚未挂接时从 staged embedded record 恢复并沿已有 attach/wait 队列投递，首条命令不再丢失。
- 机器护栏已从检查旧 Activity 字符串升级为检查 `RunOrchestrator`、`StopCoordinator`、`AndroidRecipeExecutor`、`ResourceRunCoordinator` 与 embedded terminal 合同，并明确禁止 legacy 执行/停止解释器回流。
- 全量 `:app:testDebugUnitTest :app:assembleDebug` 通过；架构与运行车道静态检查通过。债务快照为 `lines=13012, functions=569, fields=112, hosts=4, resourceDelegates=0, resourceFunctions=36, screenRefs=0, inheritedActivities=1, runtimeStateRefs=51`，相对资源迁移节点减少 2232 行、49 个函数和 2 个字段。
- OnePlus 8T `3f8bbaad` 冷启动正常。OpenClaw 终端首条命令只执行一次；点击“继续”后 CardRun 为 `Completed`、目标 `proot/bash` 归零、Kite 宿主 PID 保持存活。产品停止入口得到 `Stopped`，摘要为“终端已发送中断并关闭”，绑定清空且无进程残留。
- 同机 OpenCode 在 Kite 退到后台后仍完成获取，回前台卡片为“已获取”，命令链接存在；卸载 CardRun 为 `Completed`，详情立即恢复“获取”，命令链接消失。全链 logcat 无 `AndroidRuntime`。

T006 状态：completed，待本节点独立提交。

下一步：提交 T006，进入 T007；先盘点 `CardRunActivity` 继承、terminal/report/web 显示面绑定、页面离开与任务停止的真实边界，再建立独立轻量 `RunSurfaceHost` 合同。
