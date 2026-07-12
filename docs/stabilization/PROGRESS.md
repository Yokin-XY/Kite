# Kite 主线稳定化进度

最后更新：2026-07-12

## 当前恢复指针

```text
方向：D4 生命周期和资源预算收口
状态：in_progress
当前任务：审计页面、运行实例、显示面和底层进程的释放与回收边界
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
| D4 生命周期和资源预算 | in_progress | 当前审计销毁、租约、内存压力和回收边界 |
| D5 功能模块与扩展点 | pending | D4 验收后自动进入 |

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
