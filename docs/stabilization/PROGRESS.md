# Kite 主线稳定化进度

最后更新：2026-07-12

## 当前恢复指针

```text
方向：第二阶段业务架构迁移
状态：in_progress
当前任务：T004 资源页面所有权迁移，等待 T003 提交后启动
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
