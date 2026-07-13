# Kite 主线稳定化进度

最后更新：2026-07-13

## 当前恢复指针

```text
方向：第二阶段业务架构迁移
状态：in_progress
当前任务：T011 终端边界与应用壳清理，正在按职责清单清除反向依赖和旧兼容路径
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
| 第二阶段架构迁移 | in_progress | T001-T007 已完成，下一步迁移运行管理与运行时面板 |

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

T006 状态：completed，提交 `f3df16b`。

下一步：提交 T006，进入 T007；先盘点 `CardRunActivity` 继承、terminal/report/web 显示面绑定、页面离开与任务停止的真实边界，再建立独立轻量 `RunSurfaceHost` 合同。

## T007 运行窗口与显示面

### 三问自检

目标是什么？按 `PLAYBOOK.md` 的 T007，让 `CardRunActivity` 成为不继承 `MainActivity` 的独立轻量外壳，由 `RunSurfaceHost` 组合 terminal、report、web 显示面；显示面只绑定指定 run instance，不拥有底层任务生命周期。

完成标准是什么？`CardRunActivity` 不再继承 `MainActivity`；terminal、report、web 各自拥有显示生命周期和局部更新入口；页面离开只解绑显示面，只有用户停止才停止任务；外部浏览器回跳、终端恢复和报告更新仍回到正确实例。

依赖是否满足？T006 已通过全量测试、架构护栏和 OnePlus 真机验收，并提交为 `f3df16b`；运行开始、步骤推进、停止与资源结算均已脱离页面所有权，T007 可以只迁移显示面和壳层职责。

首段只读审计：盘点 `CardRunActivity` 的继承所得能力、启动 Intent、`showCardRunSurface` 及 terminal/report/web 绑定入口，列出必须移入独立 host 的最小依赖和禁止带入的 MainActivity 业务职责，再建立合同与测试。

### 显示面所有权审计

```text
现状：CardRunActivity 只有一行继承声明，因此每个运行窗口都会执行 MainActivity 的完整启动链。
真正需要：recipe + instanceId、CardRunStore 快照、RunOrchestrator 的继续/停止命令、terminal/report/web/X11 的可见绑定。
不应继承：资源目录与搜索、首页编辑器、设置、DropZone、本地服务器、资源预热、首次权限向导、运行管理和完整浏览器自动化控制台。
状态拥有者：CardRunStore；显示面不得复制运行事实。
结构更新：surface/session/display 变化才重建结构；报告文本和 elapsed 等普通变化只局部绑定。
生命周期：attach/detach 只改变可见绑定；complete/stop 才能触发执行资源闭合。
外部回跳：按 recipeId + instanceId 回到同一目标，再从 Store 恢复，不依赖旧 Activity 字段。
```

T007 合同层结果：

- 新增纯 `RunSurfaceTarget`、`RunSurfaceContent`、`RunSurfaceUiState`、`RunSurfaceProjector` 与 `RunSurfaceController`；Feature 不引用 Activity、Fragment、View、Platform 或导航。
- Report、Terminal、Web、X11 与 InstallWizard 都投影为携带同一 instance 身份的结构化内容；`structureKey` 只包含实例与结构句柄，报告流文本变化不会要求重建页面。
- Controller 只接受当前 `recipeId + instanceId` 的状态更新，其他实例的迟到快照不能覆盖显示面。
- `detach()` 只清除显示绑定，不调用停止；只有显式 `stop()` 或 `completeCurrentStep()` 才提交动作 Gateway。
- 纯单测覆盖报告局部更新键稳定、终端会话绑定、跨实例迟到状态门禁，以及页面离开不停止任务；目标单测和架构检查通过。

下一步：提交合同层；随后建立 Android `RunSurfaceGateway` 和轻量 `CardRunActivity` 启动壳，先迁移 Report 显示面与返回/停止/继续，再迁移 Terminal、Web、X11 和安装向导。

T007 报告显示面样板结果：

- 新增 Android `RunSurfaceHost`，同一时刻只持有一份 `RunSurfaceBinding`；结构键变化才替换显示面，普通状态更新直接交给当前 binding。Host 的 `dispose()` 只清理 View 与回调，不调用停止或推进任务。
- 新增 `RunReportScreen` 与 `RunReportPresenter`。报告输出、状态徽标、耗时、失败解释、命令查看、复制和继续按钮均由 Feature 自己绑定，`MainActivity` 不再保存 TextView、ScrollView、节流时间戳或待绘制状态。
- Summary 与 Report 共用一套报告显示合同，不再维护外观相同但状态来源不同的第二套概览页。
- CardRunStore 更新仍先由 `RunSurfaceProjector` 校验同一实例，再局部追加输出或更新徽标；报告文本变化不会重建整个运行窗口，页面销毁只释放显示绑定。
- 删除 Activity 内旧报告绘制、局部绑定和计时循环约 700 行；架构债务快照降至 `lines=12336, functions=541, fields=109, inheritedActivities=1`。
- 报告投影测试覆盖结构键稳定、进程标记清洗、命令保留、Summary 共用合同和失败提示；目标单测、Debug Kotlin 编译、架构检查及运行车道静态检查通过。

报告显示面迁移状态：completed，待独立提交。

下一步：让 terminal 与 Web 各自实现 `RunSurfaceBinding`，把 Fragment/WebView 的 attach、update、dispose 从 MainActivity 移入显示面适配器；随后再让 `CardRunActivity` 脱离继承。

T007 终端显示面与返回语义结果：

- 新增 `RunTerminalSurfaceBinding`：根据同一实例的 `terminalSessionId` 挂载 `TerminalFragment.detailOnly`；session 未就绪时由绑定自己显示准备状态，销毁时只 detach Fragment，不调用 `TerminalSessionController`、Bridge 或 `RunOrchestrator.stop`。
- `MainActivity` 删除 CardRun 终端容器 ID、Fragment tag、session 参数解析和 `showCardRunTerminalFragment`，终端可见生命周期不再由 Activity 字段维护。
- 真机审计发现旧返回键把 `WaitingTerminal` 直接解释为“完成当前步骤”，关闭运行窗口还可能自动提交停止；现已改为返回只关闭任务窗口，顶部“继续”才完成步骤，明确“停止”才停止任务。
- 安装向导根页面离开不再暗中调用取消运行链；显式取消入口仍负责停止和清理，普通返回只释放显示面。
- OnePlus 8T `3f8bbaad`：OpenClaw 进入 `WaitingTerminal`，session 为 `embedded-space-main-1783877264318`；返回首页后状态、surface 和 session 完全不变，首页显示“已打开 1”。从编辑页“打开”恢复后仍绑定同一 session；点击“继续”后才变为 `Completed` 并清空 terminalSessionId。全链无 `AndroidRuntime`。
- 目标单测、Debug Kotlin 编译、Debug APK、架构检查和运行车道静态检查通过；债务快照为 `lines=12263, functions=538, fields=108, inheritedActivities=1`。

终端显示面迁移状态：completed，待独立提交。

下一步：迁移 Web 显示绑定和浏览器回跳可见恢复，让共享 WebView、认证等待页和外部浏览器打开都通过同一实例合同；完成后再抽出独立 CardRunActivity 壳。

T007 Web 显示面迁移结果：

- 新增 `RunWebSurfaceBinding`，每个 Web 运行显示面独立拥有 `WebView`、`KiteWebShell` 与自动化控制器；MainActivity 不再保存 CardRun WebView、认证等待页、外部浏览器提示页或空地址输入页。
- Web 普通页面、OAuth/CLI loopback 认证和仅外部浏览器地址继续复用同一 `BrowserHandoffPolicy` 与认证 Gateway；显示绑定只决定承载方式，不创建第二套认证会话或回调协议。
- `RunSurfaceHost.handleBack()` 将系统返回先交给当前显示面；WebView 有历史时只后退页面，没有历史时才退出运行窗口。销毁显示面只关闭自动化显示会话并销毁本地 WebView，不停止 CardRun 或底层任务。
- 正式产品入口验证不是直启 Activity：OnePlus 8T 通过本地服务 `/open-web` 创建临时网页实例，直接绑定 `https://example.com`；进入 IANA 页面后第一次返回恢复 Example Domain 且仍停留在同一 CardRunActivity，第二次返回才回主壳。
- 全链无 `AndroidRuntime` 崩溃；目标单测、Debug Kotlin 编译、架构检查和运行车道静态检查通过。债务快照为 `lines=12039, functions=534, fields=108, inheritedActivities=1`。

Web 显示面迁移状态：completed，待独立提交。

下一步：让 `CardRunActivity` 脱离 `MainActivity` 继承，建立只装配指定 instance、RunSurfaceHost 和运行控制动作的轻量 Android 壳；X11 与安装向导显示绑定随后按同一合同接入。

T007 X11 可见绑定结果：

- 新增 `RunX11SurfaceBinding`，由显示面自己创建 `LorieView`、展示 DISPLAY/socket 错误和释放 View；`MainActivity` 删除旧 `cardRunX11SurfaceBody`。
- X11 server 与进程仍由既有执行层拥有，绑定的 `dispose()` 只移除可见 View，不调用停止或重新分配 DISPLAY。
- 目标单测、Debug Kotlin 编译、架构检查和运行车道静态检查通过；债务快照为 `lines=12008, functions=532, fields=108, inheritedActivities=1`。

X11 可见绑定状态：completed，待独立提交。

下一步：抽取轻量运行壳的启动解析、浏览器 handoff Gateway 与运行窗口 chrome，然后切断 `CardRunActivity : MainActivity` 继承。

T007 Web 浮动栏刷新修复：

- WebView 所有权迁入 `RunWebSurfaceBinding` 后，旧浮动栏刷新仍调用 MainActivity 的共享 WebView；按钮可见但会刷新错误对象。
- `RunSurfaceBinding/RunSurfaceHost` 新增窄 `reload()` 显示能力，只有当前 Web 绑定响应；浮动栏不再越过 Host 访问具体 WebView。
- 全量目标单测、Debug APK、架构检查和运行车道检查通过。OnePlus 8T 正式 `/open-web` 入口展开浮动栏后，当前 WebView、刷新按钮和 `https://example.com` 地址栏同时存在，logcat 无崩溃。

Web 刷新逻辑修复状态：completed，待独立提交。

T007 轻量运行壳启动合同结果：

- 新增 `CardRunLaunchRequest/Target/Resolution` 与纯 `CardRunLaunchResolver`；它只解析 recipe/instance/autoStart/来源和安装计划，不启动任务、不写 Store、不创建 View。
- 解析顺序固定为配方目录、进程已登记配方、特殊配方；特殊工厂返回不同 recipeId 时明确拒绝，避免错误实例被静默绑定。
- 临时网页与资源获取向导配方集中到 `CardRunSpecialRecipes`，MainActivity 已删除两份重复构造代码，新旧壳将使用同一配方合同。
- 纯单测覆盖字段规范化、重复计划 ID 去重、已登记配方恢复、特殊配方身份门禁，以及临时 Web/安装向导合同；目标单测与 Debug Kotlin 编译通过。

轻量运行壳启动合同状态：completed，待独立提交。

下一步：建立共享浏览器 handoff 适配器和轻量运行 chrome；随后让独立 CardRunActivity 组合 resolver、CardRunStore、RunOrchestrator 与 RunSurfaceHost。

T007 共享浏览器 handoff 编排结果：

- 新增无 Android UI 依赖的 `BrowserHandoffCoordinator`：复用 pending、创建 session、写等待事实、准备 loopback、打开外部浏览器、失败回滚的副作用顺序由一处拥有。
- 新增 `AndroidBrowserHandoffGateway` 适配 SessionStore、LoopbackBridge、CardRunStore、诊断和外部浏览器；KiteAppGraph 提供 Activity 级工厂，MainActivity 删除原先约 90 行私有认证编排并改用同一协调器。
- 单测覆盖已有 session 不重复打开、新 session 严格先写等待状态、系统浏览器失败必定关闭/标失败，以及非 handoff 请求不触碰 Gateway；机器护栏禁止 session 创建与 loopback 准备回流 MainActivity。
- 浏览器目标单测、Debug APK、架构检查和运行车道检查通过。OnePlus 8T 用通用 OAuth + `127.0.0.1:1456/callback` 冒烟：系统浏览器成为前台，返回后仍回原 CardRunActivity，并显示“正在等待浏览器回调 / 重新打开 / 复制地址”，logcat 无崩溃。

共享浏览器 handoff 状态：completed，待独立提交。

下一步：实现轻量 CardRunActivity 的 Store 观察、Host 装配、continue/stop/back chrome 与各显示绑定，然后切断 MainActivity 继承并做真机回归。

T007 资源向导计划动作边界：

- `ResourceRunCoordinator.startNextPlannedInstall(parentInstanceId)` 成为安装向导“开始获取”的 Application 入口；页面不再需要读取资源目录、判断已安装项、编译配方或直接推进队列。
- `ResourceRunGateway` 只新增读取既有 pending plan 的合同，没有新增 Store；Android 适配器仍从 `KiteResourceInstallStore` 读取唯一计划事实。
- 单测覆盖跳过已安装项、推进计划、只启动下一待执行项和继承向导 parent instance；资源协调器目标测试与 Debug Kotlin 编译通过。

资源向导计划动作边界状态：completed，待独立提交。

T007 独立轻量运行壳结果：

- `CardRunActivity` 已直接继承 `AppCompatActivity`，不再启动 `MainActivity` 的首页、资源目录、设置、服务预热和首次向导链；当前架构护栏确认 `activitiesInheritingMainActivity=0`。
- 新壳只组合无副作用的 `CardRunLaunchResolver`、唯一运行事实 `CardRunStore`、进程级 `RunOrchestrator`、共享 `BrowserHandoffCoordinator` 与单一 `RunSurfaceHost`。迟到的其他实例状态不能替换当前显示面。
- Report、Terminal、Web、X11 与安装向导均通过独立 `RunSurfaceBinding` 接入；浏览器自动化更新和资源打开配方解析被收口为 Platform 适配器，安装向导通过 Shell 组合资源 Feature，不制造跨 Feature 依赖。
- 运行窗口控制条只提交“继续、停止、刷新、关闭”动作。系统返回和关闭窗口仅释放显示绑定；继续后才由编排器把 `WaitingTerminal` 写成 `Completed`，停止才进入停止协调器。
- 启动合同区分“允许创建新事实”和“必须恢复既有事实”：首次运行、临时网页和安装向导可以创建；通知或已有运行窗口的 `autoStart=false` 恢复若找不到实例，只显示“该运行已经结束”，不再伪造一个空白 `Starting` 实例。
- 机器护栏新增独立 Activity、Host 组合、Store 观察和显示生命周期入口检查，并禁止安装向导适配器回流到 `feature.runsurface`。
- 全量 `:app:testDebugUnitTest :app:assembleDebug`、架构检查和运行车道静态检查通过；债务快照为 `lines=11914, functions=532, fields=109, inheritedActivities=0`。
- OnePlus 8T `3f8bbaad`：正式 `/open-web` 可加载、页内后退后仍停留同一运行实例；通用 OAuth + loopback 会打开系统浏览器，返回后恢复同一等待页；OpenClaw 终端恢复同一 embedded session，关闭窗口不改变 `WaitingTerminal`，点击继续后 Store 为 `Completed`、session 清空且目标进程归零；进程重启后的缺失恢复请求未新增 Store 事实并显示确定错误；安装向导在不执行下载时可独立打开。全链无 `AndroidRuntime` 崩溃。

独立轻量运行壳状态：completed，待独立提交。

下一步：删除 `MainActivity` 内已因继承切断而不可达的旧 CardRun 壳层分支和迁移哨兵，保留首页发起运行窗口所需的窄入口；随后补齐 T007 最终回归并关闭任务。

T007 主壳旧实现删除与最终验收：

- `MainActivity` 已删除旧 CardRun terminal/report/web/X11/安装向导显示面、浮动控制条、路由注册和生命周期分支；运行入口只读取既有 `CardRunStore` 实例并启动独立 `CardRunActivity`。
- `AppNavigator` 不再把独立运行窗口当作主应用内部 Destination。历史保存值 `CardRun` 无法解析时安全回到控制台，主壳返回规则不再决定运行任务退出。
- 删除迁移哨兵和不可达兼容分支后，主壳从 `11914` 行、`532` 个成员函数、`109` 个私有字段降至 `9398` 行、`433` 个成员函数、`100` 个私有字段；运行事实引用从 `50` 降至 `35`，继承主壳的 Activity 保持为 `0`。
- 架构守卫明确禁止 `RunSurfaceHost`、各运行绑定、安装向导显示面和 `showCardRunSurface` 回流主壳；生命周期守卫直接检查 `CardRunActivity.onDestroy()` 只能解绑和释放显示资源，不能停止任务或清空事实。
- 全量 `:app:testDebugUnitTest :app:assembleDebug`、架构检查、运行车道静态检查和差异检查通过；代码封口提交为 `0c40b56`。
- OnePlus 8T `3f8bbaad` 正式产品链验证：OpenClaw 关闭窗口后仍保持同一 `instanceId=1782789184211`、`WaitingTerminal` 和 `terminalSessionId=embedded-space-main-1783886694976`；从运行管理展开“终端窗口”并点击“打开”后进入独立 `CardRunActivity`，仍绑定同一实例和会话。
- 同机资源首页点击 OpenCode“获取”会在独立 `CardRunActivity` 打开安装向导，未点击“开始获取”时不执行安装；显式点击 OpenClaw“停止”后首页立即变为“启动”，Store 确认为 `Stopped` 且终端绑定清空。全链无 `AndroidRuntime` 崩溃。

T007 状态：completed。关键壳层提交为 `57c0199`，旧主壳删除提交为 `0c40b56`。

下一步：进入 T008，先审计运行管理页、运行时状态面板、刷新入口和停止确认的真实所有权，再建立 `runtime-management` Feature 的状态与动作合同。

## T008 运行管理与运行时面板

### 三问自检

目标是什么？建立 `runtime-management` Feature，把卡片、终端、后台运行项和 PID 的归属投影、动作语义与可见页面移出 `MainActivity`；页面只消费 UiState 并提交刷新、打开、停止、结束和回收意图。

完成标准是什么？一个 UiState 可追踪卡片、终端、服务和 PID；停止动作先进入请求中/待确认，执行层确认后才成为已停止或失败；内存压力继续经过既有策略链；可见状态变化只更新受影响行，不靠整页重建和固定延迟轮询。

依赖是否满足？T006 已将运行开始和停止收口到进程级编排，T007 已让运行显示面脱离主壳。运行管理现在可以只聚合事实与提交动作，不再承担执行或显示面生命周期。

### 所有权与压力审计

- `showKiteProcessOverview()` 直接读取 `CardRunStore`、`TerminalSessionStore` 和 `TaskManagerStore`，同时完成归属推导、整页绘制、展开状态、Dialog 与动作执行；每次展开行也会 `clearRootForScreen()` 后重建整页。
- 停止和刷新依赖 `260/900/1800ms` 固定延迟再次调用整页入口，Store 信号不是主要同步方式。
- `stopRunManagementProcess()` 在执行进程结束前先把关联 CardRun 写成 `Stopped` 并清空绑定；终止失败无法恢复事实，结束子进程也可能错误停止整张卡片。
- `TaskManagerStore.prootOwnerStopId()` 同时要求同一个字符串以 `root-` 和 `card:/resource:/terminal:` 开头，拥有者级终止路径实际不可达。
- 运行状态弹层、首次权限门和运行管理页共用主壳字段，但它们的事实来源不同：准入/权限属于 runtime bootstrap，卡片/终端/PID 计数属于运行管理快照，不能继续混成一套页面本地状态。

T008 统一快照与投影合同结果：

- 新增 Application 层 `RuntimeManagementSnapshot`，把 CardRun、终端和进程作为三类结构化事实输入；不保存 View、导航或页面展开状态。
- 新增纯 `RuntimeManagementProjector` 与 Feature UiState。根运行实例形成唯一分组，子实例显示面折叠到父实例；进程按 owner、unit、terminal 和明确 PID 绑定评分后只归属一处。
- 根进程只有 owner-root 或明确绑定 PID 才能成立，不再用“列表第一项”猜主进程；主进程动作路由到 `StopRun`，子进程保持 `EndProcess`，避免结束子进程时把整项任务判停。
- 动作使用数据命令，不在 UiState 保存闭包；`Requested/AwaitingConfirmation/Failed` 只改变动作标签和可用性，不提前修改 CardRun 事实。
- 5 个纯单测覆盖完整归属、子实例折叠、待确认动作、Stopping 语义和系统/未归属分区；目标测试、Kotlin 编译、架构检查和差异检查通过。

下一步：实现 Android `RuntimeManagementGateway`，把三个现有 Store 映射到统一快照，并先修复拥有者级进程停止目标解析；随后接确认型动作协调器。

T008 Android Gateway 与停止目标修复结果：

- 新增 `RuntimeManagementGateway`，由进程级 `KiteAppGraph` 组合并经 `KFApplication` 提供给 Feature；公开的是稳定快照合同，Android 实现保持 internal。
- `AndroidRuntimeManagementGateway` 用一个 `combine` 合并 `CardRunStore.runs`、`TerminalSessionStore.snapshot`、`TaskManagerStore.snapshot` 与 `RuntimeHealthStore.snapshot`，统一生成卡片、终端、PID 和观测进程总数；刷新仍复用两个 Store 已有的单飞/限频机制。
- owner 类型、系统进程识别、进程用途和可执行能力在 Platform 映射一次；Feature 不再依赖中文 owner label、命令字符串或 `TaskManagerAction`。普通 PID 只有真实包含 `END_PROCESS` 能力时才开放结束动作。
- 修复 `TaskManagerStore` 的 owner-stop 目标解析：Task item 的 `id` 必须是 `root-*`，其 `runtimeOwnerId` 才允许为 `card:/resource:/terminal:`；旧实现错误地要求同一个字段同时满足两类前缀，导致 owner-stop 永远不可达。
- 6 个相关目标测试覆盖 owner root、普通子 PID、后台/未归属 root、Platform owner 映射和系统进程识别；Kotlin 编译、架构检查、运行车道检查和差异检查通过。

下一步：建立 `RuntimeManagementCoordinator`。刷新、停止运行、结束终端、结束 PID 与后台运行项动作统一由协调器提交；动作必须维持请求中/待确认/失败状态，禁止页面提前宣布已停止。

T008 确认型动作协调结果：

- 新增 Application 层 `RuntimeManagementCoordinator`，统一接受停止运行、结束终端、结束 PID、停止/重启后台运行项；动作按 `Requested -> AwaitingConfirmation -> confirmed/Failed` 推进。
- 协调器不写运行事实：停止 CardRun 仍进入 `RunOrchestrator`，终端进入 `TerminalSessionStore`，PID 与后台项进入 `TaskManagerStore`；它只用下一份统一快照确认 `Stopped`、终端不再 live、PID 消失或后台项状态成立。
- 默认确认期限为 15 秒。超时保留 `Failed` 和解释，拒绝不会静默恢复按钮；同一 mutation key 的重复提交幂等忽略，失败可显式清除后重试。
- 新增纯 `RuntimeManagementFeatureController`，把 UiState 数据动作映射为协调器 Command；打开运行显示面与查看日志只产生 Shell Effect，不越过模块直接导航或执行。
- 7 个新增测试覆盖运行停止确认、子 PID 不篡改 CardRun、PID 消失确认、超时失败、拒绝/重试、重复提交，以及打开显示面不提交执行动作；目标测试与 Kotlin 编译通过。

下一步：建立 `RuntimeManagementFragment/Screen`，消费 Gateway 与 Coordinator 的 StateFlow；展开、状态变化和动作反馈只更新对应卡片/行，删除 `showKiteProcessOverview()` 的固定延迟整页重建路径。

T008 运行管理页面所有权迁移结果：

- 新增 `RuntimeManagementFragment/Screen` 与窄 Result Contract；主壳只负责导航和按 `recipeId + instanceId + surface` 打开独立运行窗口，不再绘制运行管理内容。
- Screen 以结构签名管理卡片和进程绑定：事实结构不变时只更新现有标题、状态和按钮；只有卡片、显示面或进程拓扑变化时才重建列表。展开状态和滚动位置属于可见页面，不写入运行事实。
- Fragment 同时消费统一快照和确认事务流；操作完成依赖状态拥有者信号，超时只等待当前事务的最早 deadline，不再使用 `260/900/1800ms` 固定延迟整页刷新。
- 删除 `MainActivity` 中旧运行管理的展开字段、进程弹窗、进程归属推导、CardRun 显示面列表、提前写 `Stopped` 和延迟刷新链。主壳降至 `8306` 行、`385` 个成员函数、`95` 个私有字段。
- Screen/Result Contract 目标单测、Feature/Application 目标单测、Debug Kotlin 编译、架构检查和运行车道静态检查通过。

下一步：把运行时状态弹层、首次权限门和准入动作按事实边界迁出主壳；内存压力与回收继续委托既有 runtime policy，不并入页面动作。

T008 runtime-status 合同与平台 Gateway 结果：

- 新增 Android 无关的 `RuntimeBootstrapSnapshot/Gateway`，结构化表达权限、rootfs、基础部署与 readiness 探测，不把标题、Dialog 或页面字段放入 Application 层。
- `AndroidRuntimeBootstrapGateway` 合并既有 `BootstrapCoordinator`、`AssetExtractor.rootfsProgress` 和 `RuntimeBootstrapProgress`，并在 IO 调度器执行基础镜像、默认容器和内置资源 readiness 探测。
- 新增纯 `RuntimeStatusProjector`，统一决定状态标题、阻塞语义、进度、权限动作、失败重试和状态胶囊标签；首次授权只作为显式投影输入，不与 Android 权限请求耦合。
- `RuntimeStatusFeatureController` 合并 bootstrap 与 runtime-management 两份稳定 Gateway，运行数量不再由弹层自行读取三个 Store；重试只提交 Bootstrap Gateway，不提前显示成功。
- 10 个投影/Controller 目标测试和 Debug Kotlin 编译通过；静态守卫锁定 Application、Feature、Platform 依赖方向和 IO 探测边界。

下一步：建立 `RuntimeStatusChrome` 接管运行状态 Dialog、准入 Overlay 和局部 View binding；主壳只处理权限/设置页/进程页 Shell effect。

T008 runtime-status Chrome 与最终验收：

- `RuntimeStatusChrome` 接管状态胶囊、控制台内联提示、首次准入 Overlay 和运行状态 Dialog；Dialog/进度/数量 View 引用不再属于 `MainActivity`，同一事实变化只绑定现有控件。
- Main 只收 `ContinueFirstRunPermissionOnboarding`、权限请求、全部文件设置和打开进程页四类 Shell effect；主题变化会重建 Chrome token，页面切换会重新计算资源页的临时部署层抑制规则。
- 运行状态刷新同时校准 bootstrap 与 runtime-management Gateway；Android 运行 Gateway 首次创建即主动轻量刷新，修复弹层先显示 `终端 0`、第二次进入才变正确的问题。
- 真机发现并修复“顶部终端计数为 1、正文却为空”的投影缺口：未绑定 CardRun 的 live terminal 现在显示为独立终端行并提供确认型结束动作；后台服务、资源任务和终端进程保留明确分区。
- 主壳删除旧 readiness 线程、三个 Store 计数观察器、运行状态映射、Dialog/Overlay 绘制和 21 个 View/状态字段。架构棘轮更新为 `7368` 行、`361` 个成员函数、`74` 个私有字段、`34` 个运行事实引用。
- 全量 `:app:testDebugUnitTest :app:assembleDebug`、架构检查和运行车道静态检查通过。OnePlus 8T `3f8bbaad` 覆盖安装冷启动约 2.1-2.3 秒；状态弹层首次打开显示 `终端 1 / 进程 2`，进入运行管理可见独立终端和结束动作，系统返回恢复原首页，全链无 Kite `AndroidRuntime` 崩溃。

T008 状态：completed。关键提交为 `5c9fd04`、`5b47c19`、`50b1c47`。

下一步：进入 T009，先锁定普通 Web、系统浏览器认证、自动化显示和 CardRun Web 四条现有入口及认证回跳基线，再迁移显示职责，禁止改写已验证的通用 loopback 协议。

## T009 Web、浏览器与认证边界

### 三问自检

目标是什么？让普通工作台 Web 显示由独立 `web` Feature 拥有，让系统浏览器认证、loopback 回调和自动化运行事实继续通过 Application/Platform 边界工作；主壳只负责导航、Intent 路由和 Android Shell effect。

完成标准是什么？普通 Web、CardRun Web、系统认证与自动化会话各有明确所有者；OAuth/loopback 参数不被页面改写；首次安装、覆盖安装和进程重建后的回跳都定位原运行实例；销毁 WebView 只释放显示资源，不终止后台任务或认证会话。

依赖是否满足？T002 已固定导航与恢复合同，T007 已让 CardRun Web 独占显示实例并建立共享 `BrowserHandoffCoordinator`。当前可以迁移普通工作台，而不触碰已验证的 callback 协议。

### 入口与所有权审计

- CardRun Web 已由 `RunWebSurfaceBinding` 独立持有 `WebView/KiteWebShell/BrowserAutomationController`，不再依赖主壳共享 WebView。
- 系统认证副作用顺序已收口到 `BrowserHandoffCoordinator + AndroidBrowserHandoffGateway`；但 App redirect 的解析、投递和目标 Activity 恢复仍留在主壳。
- 普通工作台仍由 `MainActivity` 在 `onCreate()` 创建共享 WebView、Shell 与自动化 Controller，`showWorkbench()` 直接拼页面，Activity 销毁时手工释放整组显示资源。
- 自动化事件在主壳与独立运行壳各有一份 CardRun 状态投影。先让二者共用 `AndroidBrowserAutomationRunUpdater`，避免页面迁移时继续复制状态判断和报告格式。

T009 自动化运行事实收口结果：

- `MainActivity` 已改用与 `CardRunActivity` 相同的 `AndroidBrowserAutomationRunUpdater`；自动化事件只由这一 Platform 适配器写入 `CardRunStore`。
- 删除主壳内重复的状态转换、摘要与报告拼装，静态守卫禁止这些实现回流主壳。
- 此小段不改变浏览器模式、WebView 页面、认证参数、loopback 监听或回跳路由。

下一步：建立普通工作台 `WebWorkbenchFragment/Screen` 和窄 Result Contract，使 WebView、Shell、自动化 Controller、历史返回与显示销毁全部迁出主壳。

T009 普通 Web 工作台迁移结果：

- 新增 `WebWorkbenchFragment/Screen`。普通工作台独占自己的 `WebView`、`KiteWebShell`、网页历史和自动化 Controller；Fragment 销毁只关闭自动化显示会话并销毁 WebView。
- 主壳不再预创建共享 WebView，不再持有 `webShell/browserAutomationController`，也不再通过 Activity `onDestroy()` 清理 Feature 的显示资源。系统返回先由工作台处理网页历史，无历史时才进入 `AppNavigator`。
- 浏览器自动化动作只投递给 `BrowserAutomationControllerRegistry` 中仍存活的显示面；没有可见控制器时返回确定的 `display_not_available`，不再把其他页面的 session 错投给主壳共享 WebView。
- 浏览器模式为“自动浏览器”时，普通工作台现在会真实创建自动化 session；旧实现虽创建了 Controller，却始终以默认 `automationEnabled=false` 打开页面。
- `KFApplication` 通过窄依赖合同向 Feature 提供诊断、自动化 session store 和进程级 handoff；系统浏览器由 Platform launcher 打开，Feature 不创建或保存认证事实。
- 目标测试覆盖网页历史返回、无历史退出、自动化 session 创建/关闭和 Activity 销毁时的显示释放；Kotlin 编译与架构/运行车道守卫通过。主壳债务降至 `lines=7193, functions=354, fields=70, hosts=4, runtimeStateRefs=33`。

下一步：把 App redirect 的解析、state 匹配、目标运行实例投递和进程重建恢复收口为 Application 协调器，并补齐首次安装、覆盖安装、进程重建三类认证回跳合同测试。

T009 认证回跳协调结果：

- 新增无 Android UI 依赖的 `BrowserAuthRedirectCoordinator`，固定执行顺序为解析通用回跳、匹配持久化 session、解析目标实例、投影 CardRun 结果、标记 delivered/failed、记录诊断。
- 新增 `AndroidBrowserAuthRedirectGateway` 适配 `BrowserAuthSessionStore`、`CardRunStore`、loopback bridge 和诊断；MainActivity 只把 Intent URL 交给协调器，并按返回的 `recipeId + instanceId` 打开独立运行窗口。
- pending 过期、CLI loopback 已转发和过期运行事实的同步也进入同一协调器；`MainActivity` 与 `CardRunActivity` 恢复时调用同一 `reconcile()`，不再各自直接操作 session store。
- 回跳 URL 只经 `BrowserAuthRedirectParser` 解析，state/code/error 不由页面补写或改名；没有新增 Codex、Claude 或提供方专属分支。
- 纯单测覆盖非回跳、state 未匹配、成功交付、提供方失败、协调器重建后交付和恢复同步；持久化测试使用两个新的 `BrowserAuthSessionStore` 实例证明升级/进程重建后仍按原 state 匹配同一 session。
- 目标测试、Kotlin 编译与架构/运行车道守卫通过。主壳降至 `lines=6963, functions=350, fields=69, runtimeStateRefs=30`。

下一步：执行真机普通 Web、自动化 session、通用 App redirect 和 CLI loopback 回归；先用可控测试回跳验证首次安装/覆盖升级/进程强杀后的实例路由，再复核真实 Codex 登录桥不退化。

T009 回跳重建缺口与修复：

- OnePlus 8T 首轮覆盖升级测试中，持久化 session 能正确匹配 state，但临时网页配方只存在于旧进程内存；`CardRunStore` 按既有策略把未确认运行归档后，目标解析返回 `missing_target`。
- 修复不保留整条旧运行，也不为未知 session 造实例：Gateway 只有在同一 `instanceId` 仍有当前 CardRun，或持久化历史中存在完全匹配记录时，才创建一个无动作、无执行能力的最小 Web 投影配方，用于把回跳结果恢复成报告面。
- 恢复配方不包含 URL、token、code 或提供方信息，只保留 recipeId、显示名称和 `type=web`；静态守卫同时禁止 Codex、Claude、OpenAI、Google 等专属分支。
- 重新构建并覆盖升级后，同一测试链从 Pending session 经进程重建直接冷启动到原 `CardRunActivity`，session 为 Delivered；强杀应用进程后回跳同样冷启动到目标运行窗口。
- 全新卸载安装触发了 OnePlus 的系统安装安全确认和全部文件权限页；完成系统确认后，首次启动创建 session、回跳和目标窗口恢复成功。三条链路均无 `AndroidRuntime` 崩溃。

下一步：提交本次真机发现的恢复修复，随后跑 T009 全量单测、Debug 构建、架构守卫和最终真机 Web/认证回归，完成任务封口。

T009 最终验收：

- 全量 `:app:testDebugUnitTest :app:assembleDebug`、架构检查和运行车道静态检查通过；架构棘轮更新为 `lines=6963, functions=350, fields=69, hosts=4, runtimeStateRefs=30`。
- OnePlus 8T `3f8bbaad` 全新安装：完成系统安装确认和全部文件权限后，通用 App redirect 从系统浏览器回跳到原 CardRun，报告显示 `state=matched / code=present`，无崩溃。
- 同机覆盖安装与强杀进程：pending session 分别跨 APK 覆盖和 `am force-stop` 保存；回跳均冷启动并直接进入原 `CardRunActivity`，session 最终为 Delivered。
- 通用 CLI loopback 请求保留 `http://127.0.0.1:1457/callback`，session 类型为 CliLoopback、callback channel 为 Direct；原始回调地址没有被页面替换，真实 token/账号不参与测试。
- 可见截图确认回跳报告已经绑定目标运行实例；logcat 的 `AndroidRuntime` 崩溃筛选为空。

T009 状态：completed。关键提交为 `10008f6`、`9d272ee`、`94efb65`。

下一步：进入 T010，审计设置页、主题重建、首次权限 onboarding、系统设置返回和 rootfs 准备的真实所有权，先固定一次性步骤与可重入步骤的合同。

## T010 设置、主题与首次启动

### 三问自检

目标是什么？把设置显示状态、一次性首次权限引导和可重复的运行环境准备拆成三条职责明确的链；系统权限窗口和设置页只作为 Shell effect 执行，任何一步都不能靠 Activity 临时字段猜测进度。

完成标准是什么？设置由独立 Feature 投影；首次引导可跨 Activity/进程重建恢复；权限拒绝后保留可再次授权的运行状态；主题变化只重绑可见 UI；首次安装和已有用户升级均通过 OnePlus 8T 验收。

依赖是否满足？T008 已把权限/readiness 事实收口到 `RuntimeBootstrapGateway`，T009 已完成浏览器显示边界。T010 只迁移设置和首次引导，不改变 bootstrap、CardRun 或认证事实合同。

### 首次权限引导持久化结果

- 审计确认旧逻辑在引导刚开始时就写入 `first_run_permission_onboarding_done=true`；用户拒绝权限、停在系统设置或进程被回收后，下一次启动会误判已完成并永久跳过未走完步骤。
- 新增 Android 无关的 `FirstRunOnboardingCoordinator`，阶段只表示一次性动作是否已尝试；权限缺失和运行环境 readiness 仍由 `RuntimeBootstrapGateway` 保存事实，不复制进 onboarding Store。
- 每次权限请求或全部文件设置跳转前先同步持久化等待阶段。Activity 重建复用进程级协调器，不重复弹系统窗口；进程重建从持久化阶段继续，权限拒绝后由既有 runtime-status 动作提供稍后授权入口。
- `AndroidFirstRunOnboardingStore` 兼容正式版旧完成标记；已有用户升级不会重新弹首次引导，新安装只有所有一次性步骤走完后才写完成。
- 目标单测覆盖首次只请求一次、拒绝、系统设置返回、两种进程重建和旧标记迁移；Debug Kotlin 编译及 9 项目标测试通过。

下一步：建立设置 Feature 的 `State/Action/Effect` 合同，让设置项、主题选择和系统设置返回从主壳绘制函数迁出，同时保持当前页面视觉不变。

T010 设置 Feature 与主题边界结果：

- 新增 `SettingsGateway` 作为主题、浏览器模式、现场恢复和最近任务可见性的唯一持久化入口；通知开关与投放区是显式刷新得到的系统快照，不写成应用偏好事实。
- `AndroidSettingsGateway` 只在 `refresh()` 的 IO 段检查投放区；设置绘制和开关绑定不再执行文件准备、系统服务探测或 `SharedPreferences` 读取。
- 新增 `SettingsFragment/Screen` 与 `ThemeSettingsFragment/Screen`。设置页保持同一 View 结构并局部更新副标题和开关；主题页只在颜色身份变化时重绑自身，不再调用主壳函数整页重建。
- MainActivity 不再持有 `themeStore/appSettings`，不再绘制设置行、模式弹窗、颜色选项和主题预览；只处理返回、系统通知、投放区、最近任务可见性与主题 Shell effect。
- 主题变更只更新语义 token、终端主题、运行状态 Chrome、根背景和底部导航，不调用 Bootstrap、运行编排或后台服务。CardRun、终端进程与浏览器认证事实不受影响。
- 审计修复设置页投放区刷新成功后无条件 `showConsole()` 的旧错误：现在只校准当前可见设置页，用户不会被突然送回首页。
- 设置 Gateway、Projector、Controller、Result Contract、局部绑定和主题必要重绑目标测试通过；既有 Main 路由合同继续通过。主壳债务降至 `lines=6652, functions=341, fields=65`。

下一步：补充设置/首次引导架构守卫，执行全量单测与 Debug 构建，再在 OnePlus 8T 分别验证已有用户升级、主题切换、系统通知/文件设置返回和全新安装引导。

T010 最终验收：

- 全量 `:app:testDebugUnitTest :app:assembleDebug` 通过：422 项测试、0 失败、1 项既有跳过；架构与运行车道守卫通过。架构棘轮更新为 `lines=6652, functions=341, fields=65, hosts=4, runtimeStateRefs=30`。
- OnePlus 8T `3f8bbaad` 已有用户覆盖升级冷启动 1947ms；原设置值和投放区均即时投影，设置 -> 主题 -> 系统 back 回设置路径正确，未出现首次引导重放。
- 主题切换到蓝色时应用 PID 保持 `20386`，主题页与底部导航即时换色，偏好同步落盘；没有重启进程、Bootstrap、CardRun 或浏览器会话。
- 系统通知设置页返回后仍停在设置页并校准真实开关；投放区刷新完成后同样留在设置页，确认修复旧的无条件跳首页行为。
- 清空应用数据模拟首次安装：通知权限拒绝后系统事实保持 `granted=false`，一次性引导阶段持久化为 `Completed`，运行环境继续通过独立 runtime gate 准备。强杀重启冷启动 1563ms，不再重复首次授权窗口；环境准备完成后可进入设置，通知行明确显示“未开启”并保留稍后授权入口。
- 两轮真机均未发现 Kite `AndroidRuntime` 崩溃；首次准备和进程重建期间设置、引导与 runtime 状态没有互相覆盖。

T010 状态：completed。关键提交为 `3318411`、`76062af`。

下一步：进入 T011，先审计终端 Fragment、TerminalChromeHost、主壳快捷面板和终端会话恢复的真实边界，迁移前固定显示生命周期与扩展动作合同。

## T011 终端边界与应用壳清理

### 三问自检

目标是什么？让终端 Feature 只拥有列表、详情、TerminalView 和快捷动作显示；会话继续归 `TerminalRuntimeHost/TerminalSessionStore`，沉浸与返回只提交数据 effect，Main 与 CardRun 各自决定壳层表现。

完成标准是什么？终端不再识别或强转 Activity Host；主壳不保存终端详情模式和底栏引用；页面离开只 detach UI，不终止会话；所有剩余 Fragment 反向 Activity Host 都完成迁移或有明确台账。

依赖是否满足？T004 已固定导航返回，T007 已让 CardRun 独立，T008-T010 已迁移运行状态、Web 与设置。终端现有会话 Store、输入背压、快捷动作注册表和 View 刷新合并策略保持不变。

### 通用 Surface Effect 迁移结果

- 新增 Android 无关的 `SurfaceEffect`，只表达 `SetChromeMode(Standard/Immersive)` 与 `RequestBack`；终端 Result Contract 不包含 MainActivity、CardRunActivity 或页面回调对象。
- `TerminalFragment` 进入详情、回列表、详情返回和 View 销毁均发送数据 effect，不再强转 `TerminalChromeHost`。`TerminalChromeHost.kt` 已删除。
- MainActivity 按当前 Destination 解释 effect：详情只隐藏带标签的底部导航，离开终端后的迟到 effect 被忽略；CardRunActivity 始终保留独立运行控制 Chrome，并解释 back 为关闭/返回当前任务。
- 删除 Main 的 `terminalContainerId`、`terminalBottomNavigation`、`isTerminalDetailMode` 和两个 Host 实现；终端容器使用统一 Feature content id。死的 `openSessionFromExternal/openTerminalSession` 兼容入口一并移除。
- 会话 attach/detach、预输入、快捷动作注册表、输出 33ms 合并刷新和 5 秒会话校准均未改动；目标编译、Main 路由、快捷动作注册表和 Surface Contract 测试通过。

下一步：迁移最后一个明确的 Fragment 反向 Host `RecipeRawJsonFragment`，随后按职责清单审计 Main 中残留的旧绘制、死字段和兼容入口。

T011 原始 JSON 反向 Host 清理结果：

- 原根包 `RecipeRawJsonFragment` 及其 `RecipeProvider/RecipeRawJsonHost/UiKitProvider` 三套 Activity 接口已删除；MainActivity 现在不实现任何 Fragment Host 接口。
- 新 `feature/recipeeditor/RecipeRawJsonFragment` 通过 `RecipeFeatureDependenciesOwner` 获取统一 Gateway，在 lifecycle scope 中读取最新目录；不再同步调用 Main 的 loader 或复制配方事实。
- 主题颜色作为显示环境参数进入，Screen 独立渲染加载、JSON 和缺失状态；返回复用 `RecipeEditorResultContract.CloseRawJson`，由 Shell 的统一导航处理。
- Raw JSON 改用标准 Feature content 容器，不再替换 `rootHost`、隐藏整棵 Main root 或依赖 `pendingRawJsonRecipeId` 恢复；旧 route/exit/provider/UiKit 委托全部删除。
- 目标 Screen、Result、编辑器与 Main 路由测试通过；架构守卫升级为所有 Feature 源禁止 `activity as?` 强转，并锁定旧根包 Fragment 不得回流。

下一步：以“Main 只保留外壳、导航、系统回调和模块装配”为清单，扫描剩余字段和函数的调用者；优先删除已迁移模块的死兼容代码，再识别仍需迁出的活跃业务绘制。

T011 死兼容代码清理结果：

- 按私有成员调用图扫描 MainActivity，删除 45 个只有定义、没有调用者的旧函数；内容来自已迁移的首页卡片、资源页面、旧运行显示、权限反向映射和终端/浏览器兼容入口，没有删除任何活跃状态或执行路径。
- `:app:compileDebugKotlin` 通过，证明这些私有入口不存在隐藏源码调用；架构守卫通过，债务从 `6608 / 335 / 61 / 29` 降为 `6124 / 296 / 61 / 29`（行 / 函数 / 字段 / runtimeStates 引用）。
- 本段不新增 Store、刷新、轮询或显示面重建；终端会话仍归 `TerminalRuntimeHost/TerminalSessionStore`，页面销毁仍只解绑 UI。
- 首轮删除后继续按调用图清理级联失联的卡片视觉、图标摘要、URL 摘要与资源装饰类型；零调用私有函数扫描最终为 0，Kotlin 编译和架构守卫再次通过。MainActivity 当前为 `5802 / 276 / 61 / 29`（行 / 函数 / 字段 / runtimeStates 引用）。

下一步：把剩余活跃成员按 Shell 必需、系统适配、业务动作编排和业务显示四类归档；优先迁出仍由 Main 持有的业务临时状态与显示职责，再更新职责守卫。

T011 活跃业务显示迁移结果：

- 新增 `RunHistoryGateway` 与 `AndroidRunHistoryGateway`，运行历史 Feature 只读取既有 `CardRunStore` 历史事实；列表、详情、步骤报告、复制和内部返回均由 `RunHistoryFragment/Screen` 拥有。
- 资源“更多”和原始 JSON 迁入 `ResourceMoreFragment/Screen`、`ResourceRawJsonFragment/Screen`；资源图标、描述、首页卡片入口和获取日志不再由 Activity 绘制或维护位图缓存。
- MainActivity 只保留目标 ID 路由、返回策略、通用历史 Fragment 装配和创建首页卡片落点；旧历史格式化、SH 报告、资源补充页 View 工厂与级联缓存已删除。
- 修复初始历史定位被后台变更重复应用的问题：指定记录只在首次载入时定位，用户退回列表后刷新保持列表。
- 定向 Controller、Screen、Result Contract 与 Main 路由测试通过；架构和运行车道守卫均通过。MainActivity 当前为 `4949 / 237 / 56 / 28 / 29`（行 / 函数 / 字段 / 资源职责函数 / runtimeStates 引用）。

下一步：审计剩余资源动作临时状态和运行动作投影，把业务编排迁到 Application/Platform 边界；Shell 仅解释 Effect 和启动系统界面。

T011 资源旧引擎清理结果：

- 删除已经被 `ResourceFeatureGateway` 取代的 Activity UI 目录缓存刷新链，以及已经被 `ResourceRunCoordinator + CardRunActivity` 取代的 Main 下一安装项递归引擎。
- 静态守卫不再寻找旧 `resourceCatalogForUiRender` 字符串，改为验证 `ResourceFeatureController` 只通过 Gateway 加载目录，且不在投影路径引用文件、ManifestLoader 或 CardRunStore。
- Kotlin 编译、架构守卫和运行车道守卫通过；MainActivity 当前为 `4829 / 232 / 55`（行 / 函数 / 私有字段）。

下一步：在干净调用图上建立资源动作工作流合同，迁移获取计划、打开/停止、卸载、取消和首页卡片创建。

T011 资源动作工作流迁移结果：

- 新增 Android 无关的 `ResourceActionWorkflowCoordinator/ResourceActionGateway`；资源 Feature 的稳定意图统一经过一条工作流，MainActivity 只解释 `OpenRun/OpenInstallWizard/Message` Effect。
- `AndroidResourceActionGateway` 复用安装 Store、运行 Store、资源运行协调器和通用 RunOrchestrator，拥有获取计划、向导运行注册、打开/停止、卸载、取消清理和首页卡片写入；没有新增 Store、页面状态或刷新轮询。
- 删除 Main 中资源目录缓存、过期状态归一化、Store 观察器、打开运行签名、资源 DTO/Manifest 投影和向导临时字段。资源页面直接消费 Gateway 信号并局部 `ReconcileFacts`，不再依赖 Activity 标脏缓存。
- 修复计划依赖项触发“恢复向导/取消”时误用依赖项作为目标的问题；现在以 Store 的真实 `targetResourceId` 路由。取消清理超时不再无条件宣称清理成功。
- `CardRunSpecialRecipes` 从 Feature 迁至 Application 运行层，Platform 不再反向依赖 Feature。失真的 Activity 缓存反射测试和字符串守卫已改为工作流、Gateway、Feature 信号消费和依赖方向守卫。
- 定向资源工作流、资源 Controller、运行解析和 Main 路由测试通过；Kotlin 编译、架构守卫和运行车道守卫通过。MainActivity 当前为 `3186 / 147 / 44 / 10 / 19`（行 / 函数 / 私有字段 / 资源职责函数 / runtimeStates 引用），零调用私有函数为 0。

下一步：继续按 Shell 必需、系统适配、业务编排、业务显示四类审计剩余 147 个函数；优先迁出自动化测试入口、通用运行动作编排和仍由 Main 手绘的 Console 壳内容，再进行 T011 全量与真机封口。

T011 运行事实副本清理结果：

- 删除 MainActivity 的 `runtimeStates` 与 `activeRunInstanceIds`。启动、停止、编辑器删除、资源探针、浏览器 handoff、网页请求和 X11 桌面请求不再补写 Activity Map。
- 运行实例解析统一为显式实例、匹配 recipe 的可见焦点、`CardRunStore.currentForRecipe()` 和默认实例；`focusedRunInstanceId` 只表示 Shell 显示选择，不承担运行事实。
- 停止完成只清除匹配焦点；CardRunStore 继续独占运行状态、实例代次和持久化。没有新增扫描、轮询、整页刷新或第二 Store。
- 通用动作协调器、RunOrchestrator 与 Main 路由目标测试通过；Kotlin 编译、架构守卫和运行车道守卫通过。MainActivity 当前为 `3135 / 146 / 41 / 10 / 0`，其中 `runtimeStates` 引用已归零。

下一步：迁出 Main 中通用配方动作计划的副作用解释，使 Application 工作流负责实例解析和 RunOrchestrator 调用，Shell 只执行打开运行窗口、导航、运行时准入和离散消息 Effect。

T011 通用配方动作工作流结果：

- 新增 `RecipeActionWorkflowCoordinator/RecipeActionGateway` 与 `AndroidRecipeActionGateway`。首页和编辑器请求继续复用原 `KiteRecipeActionCoordinator` 计划，但实例解析、RunOrchestrator 启停、失败事实和诊断已迁出 Main。
- Main 只解释 `EnsureRuntime/FocusRun/OpenRun/CloseRunTask/ShowConsole/Message`；现有首页、编辑器、独立任务和停止后的页面落点保持原行为。
- 删除 Main 中 `KiteRecipeActionPlan` 解释、`executeRecipeActionRoute`、运行实例解析和失联的 `setRuntimeState/shouldIgnoreRuntimeStateAfterUserStop/stopRecipe` 兼容层。停止回调只清除匹配的 Shell 焦点；真正迟到回调保护继续由 RunOrchestrator 代次和 CardRunStore 停止写保护承担。
- 新工作流 5 类行为单测、原动作 Planner、RunOrchestrator、首页/编辑器 Controller 和 Main 路由回归通过；Kotlin 编译、架构守卫和运行车道守卫通过。MainActivity 当前为 `2949 / 140 / 40 / 10 / 0`，零调用私有函数为 0。

下一步：审计并迁出 Main 的自动化/本地服务器入站适配和 X11 桌面请求处理；保留 Shell Intent 分发，但将资源探针、网页请求、APK 安装和桌面运行事实交给 Platform Gateway。

T011 桌面入站适配迁移结果：

- 新增 `DesktopOpenCoordinator/DesktopOpenGateway` 与 `AndroidDesktopOpenGateway`。命令校验、配方解析、实例分配、X11 display/socket、CardRun 事实、原生 X11 启动和诊断全部迁出 Main。
- 临时桌面配方进入 Application `CardRunSpecialRecipes`；Main 只映射 `KiteDesktopOpenResponse`、打开需要的新运行窗口并向既有 `CardRunDesktopRouter` 投递成功请求。
- 保持失败可见性：新临时桌面若 X11 启动失败，Gateway 写入失败报告，Shell 仍打开对应 CardRun；指定已有实例的请求不创建第二个任务窗口。
- 桌面协调器、X11 分配、CardRun 解析和 Main 路由测试通过；Kotlin 编译、架构守卫和运行车道守卫通过。MainActivity 当前为 `2850 / 138 / 41 / 10 / 0`，零调用私有函数为 0。

下一步：迁出本地服务器的浏览器运行事实写入与 APK 路径解析；Shell 继续拥有 Web 显示导航和 Android 安装器启动 Effect。

T011 浏览器与 APK 入站适配结果：

- 新增 `BrowserOpenCoordinator/AndroidBrowserOpenGateway`。已有实例先走 `CardRunBrowserRouter`，指定 recipe/instance 只更新唯一 CardRun Web 事实，无目标时创建可恢复的临时 Web CardRun；Gateway 不持有 WebView 或 Activity。
- 新增 `InstallApkCoordinator/AndroidInstallApkGateway`。路径 trim、`file://`、`/exchange`、`/sdcard`、`/storage`、扩展名和文件存在性全部在 Platform 校验，Main 只对 accepted 结果启动系统安装器。
- 删除 Main 的临时浏览器配方/状态写入、APK 路径解析和相应事实分支；临时运行窗口启动失败仍回退现有工作台。
- 浏览器/APK 协调器、CardRun 浏览器路由、运行目标解析和 Main 路由测试通过；Kotlin 编译、架构守卫和运行车道守卫通过。MainActivity 当前为 `2780 / 135 / 43 / 10 / 0`，零调用私有函数为 0。

### 2026-07-13 T011 自动化运行入口收口

- 卡片停止自动化改为提交 `KiteRecipeActionSource.Automation`，与首页、编辑器和运行窗口共用 `RecipeActionWorkflowCoordinator`；Shell 不再直接停止 Orchestrator。
- 资源直接安装自动化进入 `ResourceActionWorkflowCoordinator.installDirect`；资源 owner 探针由独立 Application 协调器归一化身份，再由 Platform 适配器复用正式 `RunOrchestrator` 和 `CardRunSpecialRecipes`。
- 删除 MainActivity 的直接运行启停、资源探针配方、资源运行协调器字段、无写入的显示抑制集合及两个迁移后死方法。当前架构债为 `2578 / 127 / 41 / 10 / 0`（行 / 函数 / 私有字段 / 资源职责函数 / runtimeStates 引用）。
- 自动化 Intent 消费完成后同时清除 runtime、recipe、instance 和 resource 标识，避免 Activity/进程重建时把验收入口残留误路由成普通卡片运行。
- 定向工作流、RunOrchestrator 和 Main 路由单测通过；Kotlin 编译、架构守卫和运行车道守卫通过。Robolectric 仍只出现 Windows 临时目录清理告警，Gradle 结果为成功。
- 全量 `testDebugUnitTest + assembleDebug` 通过。Debug APK 为 241,594,117 bytes；覆盖安装到 OnePlus 8T 后冷启动 `TotalTime=1222ms`，首屏、进程和导航壳正常，无 FATAL/ANR。
- 真机资源 owner 探针通过新工作流启动，运行压力事实出现 `resource:kite.owner.telemetry.probe`、owner container=1、tracee=2；自动化停止复用正式配方动作后约 0.5 秒落为 Stopped，owner container/tracee 均归零，结果为“已停止，未发现进程残留”。T011 完成。

### 2026-07-13 T012 生命周期与状态拥有者审计

- 真机资源探针过程中发现 `KiteTaskContractInitializer` 的工具链回调反复创建 SQLite-backed `KiteResourceInstallStore`，logcat 连续报告 `SQLiteConnection object ... leaked`。改为复用 `KiteAppGraph.resourceInstallStore` 后，同样的冷启动、探针启停和回收路径不再出现数据库泄漏告警。
- 后台压力验收发现 `UI_HIDDEN=20` 被整数比较误判为高于 `RUNNING_CRITICAL=15`，占用冷却窗口并吞掉真实压力。现将 UI_HIDDEN 归为 `visibility_only`；目标单测、架构/运行车道守卫通过。
- OnePlus 8T 覆盖安装后冷启动 `1201ms`；切后台后依次注入 UI_HIDDEN、RUNNING_LOW、RUNNING_CRITICAL，日志分别为 visibility-only、PROCESS_SNAPSHOT、PROCESS_SNAPSHOT。热返回 `117ms`，前后 PID 均为 `10830`，无 SQLite 泄漏、FATAL 或 ANR。

下一步：审计 Main 的自动化测试 Intent 入口和剩余 Console/Shell 绘制职责；自动化业务动作改为调用现有工作流，测试入口本身保留为 Shell 系统回调。
