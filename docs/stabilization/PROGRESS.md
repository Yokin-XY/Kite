# Kite 主线稳定化进度

最后更新：2026-07-12

## 当前恢复指针

```text
方向：D2 动作编排统一
状态：in_progress
当前任务：审计首页、编辑页、运行面和资源页的动作入口及分叉
代码分支：main
代码策略：单会话连续推进 D1-D5，Git 单主线，阶段性本地提交
```

## 方向总览

| 方向 | 状态 | 当前结论 |
| --- | --- | --- |
| P0 公共行为安全网 | done | 静态检查、动作路由测试、全量单测和 Debug 构建通过 |
| D1 导航与返回 | done | Destination、返回优先级、恢复策略和主要真机路径均已验收 |
| D2 动作编排 | in_progress | 当前审计动作入口、命令语义和重复执行边界 |
| D3 状态投影 | pending | D2 验收后自动进入 |
| D4 生命周期和资源预算 | pending | D3 验收后自动进入 |
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
